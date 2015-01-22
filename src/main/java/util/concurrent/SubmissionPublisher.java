/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link Flow.Publisher} that asynchronously issues submitted
 * (non-null) items to current subscribers until it is closed.  Each
 * current subscriber receives newly submitted items in the same order
 * unless drops or exceptions are encountered.  Using a
 * SubmissionPublisher allows item generators to act as Publishers
 * relying on drop handling and/or blocking for flow control.
 *
 * <p>A SubmissionPublisher uses the {@link Executor} supplied in its
 * constructor for delivery to subscribers. The best choice of
 * Executor depends on expected usage. If the generator(s) of
 * submitted items run in separate threads, and the number of
 * subscribers can be estimated, consider using a {@link
 * Executors#newFixedThreadPool}. Otherwise consider using a
 * work-stealing pool (including {@link ForkJoinPool#commonPool}).
 *
 * <p>Buffering allows producers and consumers to transiently operate
 * at different rates.  Each subscriber uses an independent buffer.
 * Buffers are created upon first use and expanded as needed up to the
 * given maximum. (the enforced capacity may be rounded up to the
 * nearest power of two and/or bounded by the largest value supported
 * by this implementation.)  Invocations of {@link
 * Flow.Subscription#request} do not directly result in buffer
 * expansion, but risk saturation if unfilled requests exceed the
 * maximum capacity.  Choices of buffer parameters rely on expected
 * rates, resources, and usages, that usually benefit from empirical
 * testing.  As a first guess, consider a value of 64.
 *
 * <p>Publication methods support different policies about what to do
 * when buffers are saturated. Method {@link #submit} blocks until
 * resources are available. This is simplest, but least
 * responsive. The {@code offer} methods may drop items (either
 * immediately or with bounded timeout), but provide an opportunity to
 * interpose a handler and then retry.
 *
 * <p>If any Subscriber method throws an exception, its subscription
 * is cancelled.  If the supplied Executor throws
 * RejectedExecutionException (or any other RuntimeException or Error)
 * when attempting to execute a task, or a drop handler throws an
 * exception when processing a dropped item, then the exception is
 * rethrown. In these cases, some but not all subscribers may have
 * received items. It is usually good practice to {@link
 * #closeExceptionally closeExceptionally} in these cases.
 *
 * <p>This class may also serve as a convenient base for subclasses
 * that generate items, and use the methods in this class to publish
 * them.  For example here is a class that periodically publishes the
 * items generated from a supplier. (In practice you might add methods
 * to independently start and stop generation, to share schedulers
 * among publishers, and so on, or instead use a SubmissionPublisher
 * as a component rather than a superclass.)
 *
 * <pre> {@code
 * class PeriodicPublisher<T> extends SubmissionPublisher<T> {
 *   final ScheduledFuture<?> periodicTask;
 *   final ScheduledExecutorService scheduler;
 *   PeriodicPublisher(Executor executor, int maxBufferCapacity,
 *                     Supplier<? extends T> supplier,
 *                     long period, TimeUnit unit) {
 *     super(executor, maxBufferCapacity);
 *     scheduler = new ScheduledThreadPoolExecutor(1);
 *     periodicTask = scheduler.scheduleAtFixedRate(
 *       () -> submit(supplier.get()), 0, period, unit);
 *   }
 *   public void close() {
 *     periodicTask.cancel(false);
 *     scheduler.shutdown();
 *     super.close();
 *   }
 * }}</pre>
 *
 * <p>Here is an example of a {@link Flow.Processor} implementation.
 * It uses single-step requests to its publisher for simplicity of
 * illustration. A more adaptive version could monitor flow using the
 * lag estimate returned from {@code submit} and/or other utility
 * methods.
 *
 * <pre> {@code
 * class TransformProcessor<S,T> extends SubmissionPublisher<T>
 *   implements Flow.Processor<S,T> {
 *   final Function<? super S, ? extends T> function;
 *   Flow.Subscription subscription;
 *   TransformProcessor(Executor executor, int maxBufferCapacity,
 *                      Function<? super S, ? extends T> function) {
 *     super(executor, maxBufferCapacity);
 *     this.function = function;
 *   }
 *   public void onSubscribe(Flow.Subscription subscription) {
 *     (this.subscription = subscription).request(1);
 *   }
 *   public void onNext(S item) {
 *     subscription.request(1);
 *     submit(function.apply(item));
 *   }
 *   public void onError(Throwable ex) { closeExceptionally(ex); }
 *   public void onComplete() { close(); }
 * }}</pre>
 *
 * @param <T> the published item type
 * @author Doug Lea
 * @since 1.9
 */
public class SubmissionPublisher<T> implements Flow.Publisher<T>,
                                               AutoCloseable {
    /*
     * Most mechanics are handled by BufferedSubscription. This class
     * mainly tracks subscribers and ensures sequentiality, by using
     * built-in synchronization locks across public methods. (Using
     * built-in locks works well in the most typical case in which
     * only one thread submits items).
     */

    // Ensuring that all arrays have power of two length

    static final int MAXIMUM_BUFFER_CAPACITY  = 1 << 30;
    static final int roundCapacity(int cap) { // to nearest power of 2
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n <= 0) ? 2 : // at least 2
            (n >= MAXIMUM_BUFFER_CAPACITY) ? MAXIMUM_BUFFER_CAPACITY : n + 1;
    }

    /**
     * Clients (BufferedSubscriptions) are maintained in a linked list
     * (via their "next" fields). This works well for publish loops.
     * It requires O(n) traversal to check for duplicate subscribers,
     * but we expect that subscribing is much less common than
     * publishing. Unsubscribing occurs only during traversal loops,
     * when BufferedSubscription methods or status checks return
     * negative values signifying that they have been disabled.
     */
    BufferedSubscription<T> clients;

    /** Run status, updated only within locks */
    volatile boolean closed;

    // Parameters for constructing BufferedSubscriptions
    final Executor executor;
    final int maxBufferCapacity;

    /**
     * Creates a new SubmissionPublisher using the given Executor for
     * async delivery to subscribers, and with the given maximum
     * buffer size for each subscriber. In the absence of other
     * constraints, consider using {@code ForkJoinPool.commonPool(),
     * 64}.
     *
     * @param executor the executor to use for async delivery,
     * supporting creation of at least one independent thread
     * @param maxBufferCapacity the maximum capacity for each
     * subscriber's buffer (the enforced capacity may be rounded up to
     * the nearest power of two and/or bounded by the largest value
     * supported by this implementation; method {@link #getMaxBufferCapacity}
     * returns the actual value)
     * @throws NullPointerException if executor is null
     * @throws IllegalArgumentException if maxBufferCapacity not
     * positive
     */
    public SubmissionPublisher(Executor executor, int maxBufferCapacity) {
        if (executor == null)
            throw new NullPointerException();
        if (maxBufferCapacity <= 0)
            throw new IllegalArgumentException("capacity must be positive");
        this.executor = executor;
        this.maxBufferCapacity = roundCapacity(maxBufferCapacity);
    }

    /**
     * Adds the given Subscriber unless already subscribed.  If
     * already subscribed, the Subscriber's onError method is invoked
     * with an IllegalStateException.  Otherwise, upon success, the
     * Subscriber's onSubscribe method is invoked with a new
     * Subscription. If onSubscribe throws an exception, the
     * subscription is cancelled. Otherwise, if this
     * SubmissionPublisher is closed, the subscriber's onComplete
     * method is then invoked.  Subscribers may enable receiving items
     * by invoking the {@code request} method of the new Subscription,
     * and may unsubscribe by invoking its cancel method.
     *
     * @param subscriber the subscriber
     * @throws NullPointerException if subscriber is null
     */
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        if (subscriber == null) throw new NullPointerException();
        BufferedSubscription<T> subscription =
            new BufferedSubscription<T>(subscriber, executor, maxBufferCapacity);
        boolean present = false, complete;
        synchronized (this) {
            complete = closed;
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; ; b = next) {
                if (b == null) {
                    if (pred == null)
                        clients = subscription;
                    else
                        pred.next = subscription;
                    subscription.onSubscribe();
                    break;
                }
                next = b.next;
                if (b.isDisabled()) { // remove
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else if (subscriber.equals(b.subscriber)) {
                    present = true;
                    break;
                }
                pred = b;
            }
        }
        if (present)
            subscriber.onError(new IllegalStateException("Already subscribed"));
        else if (complete)
            subscription.onComplete();
    }

    /**
     * Publishes the given item to each current subscriber by
     * asynchronously invoking its onNext method, blocking
     * uninterruptibly while resources for any subscriber are
     * unavailable. This method returns an estimate of the maximum lag
     * (number of items submitted but not yet consumed) among all
     * current subscribers. This value is at least one (accounting for
     * this submitted item) if there are any subscribers, else zero.
     *
     * <p>If the Executor for this publisher throws a
     * RejectedExecutionException (or any other RuntimeException or
     * Error) when attempting to asynchronously notify subscribers,
     * then this exception is rethrown.
     *
     * @param item the (non-null) item to publish
     * @return the estimated maximum lag among subscribers
     * @throws IllegalStateException if closed
     * @throws NullPointerException if item is null
     * @throws RejectedExecutionException if thrown by Executor
     */
    public int submit(T item) {
        if (item == null) throw new NullPointerException();
        int lag = 0;
        synchronized (this) {
            if (closed)
                throw new IllegalStateException("Closed");
            /*
             * To reduce head-of-line blocking, try offer() on each,
             * place saturated ones in retries list, and later wait
             * them out.
             */
            BufferedSubscription<T> b = clients, retries = null,
                rtail = null, pred = null, next;
            for ( ; b != null; b = next) {
                int stat;
                next = b.next;
                if ((stat = b.offer(item)) < 0) {
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else {
                    if (stat == 0) {
                        if (rtail == null)
                            retries = b;
                        else
                            rtail.nextRetry = b;
                        rtail = b;
                        stat = maxBufferCapacity;
                    }
                    if (stat > lag)
                        lag = stat;
                    pred = b;
                }
            }
            if (retries != null)
                retrySubmit(retries, item);
        }
        return lag;
    }

    /**
     * Calls submit on each subscription on retry list.
     */
    private void retrySubmit(BufferedSubscription<T> retries, T item) {
        for (BufferedSubscription<T> r = retries; r != null;) {
            BufferedSubscription<T> nextRetry = r.nextRetry;
            r.nextRetry = null;
            r.submit(item);
            r = nextRetry;
        }
    }

    /**
     * Publishes the given item, if possible, to each current
     * subscriber by asynchronously invoking its onNext method. The
     * item may be dropped by one or more subscribers if resource
     * limits are exceeded, in which case the given handler (if
     * non-null) is invoked, and if it returns true, retried once.
     * Other calls to methods in this class by other threads are
     * blocked while the handler is invoked.  Unless recovery is
     * assured, options are usually limited to logging the error
     * and/or issuing an onError signal to the subscriber.
     *
     * <p>This method returns a status indicator: If negative, it
     * represents the (negative) number of drops (failed attempts to
     * issue the item to a subscriber). Otherwise it is an estimate of
     * the maximum lag (number of items submitted but not yet
     * consumed) among all current subscribers. This value is at least
     * one (accounting for this submitted item) if there are any
     * subscribers, else zero.
     *
     * <p>If the Executor for this publisher throws a
     * RejectedExecutionException (or any other RuntimeException or
     * Error) when attempting to asynchronously notify subscribers, or
     * the drop handler throws an exception when processing a dropped
     * item, then this exception is rethrown.
     *
     * @param item the (non-null) item to publish
     * @param onDrop if non-null, the handler invoked upon a drop to a
     * subscriber, with arguments of the subscriber and item; if it
     * returns true, an offer is re-attempted (once)
     * @return if negative, the (negative) number of drops; otherwise
     * an estimate of maximum lag
     * @throws IllegalStateException if closed
     * @throws NullPointerException if item is null
     * @throws RejectedExecutionException if thrown by Executor
     */
    public int offer(T item,
                     BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
        if (item == null) throw new NullPointerException();
        int ret = 0;
        synchronized (this) {
            if (closed)
                throw new IllegalStateException("Closed");
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                int stat;
                next = b.next;
                if ((stat = b.offer(item)) == 0 &&
                    onDrop != null &&
                    onDrop.test(b.subscriber, item))
                    stat = b.offer(item);
                if (stat < 0) {
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else {
                    pred = b;
                    if (stat == 0)
                        ret = (ret >= 0) ? -1 : ret - 1;
                    else if (ret >= 0 && stat > ret)
                        ret = stat;
                }
            }
            return ret;
        }
    }

    /**
     * Publishes the given item, if possible, to each current
     * subscriber by asynchronously invoking its onNext method,
     * blocking while resources for any subscription are unavailable,
     * up to the specified timeout or the caller thread is
     * interrupted, at which point the given handler (if non-null) is
     * invoked, and if it returns true, retried once. (The drop
     * handler may distinguish timeouts from interrupts by checking
     * whether the current thread is interrupted.) Other calls to
     * methods in this class by other threads are blocked while the
     * handler is invoked.  Unless recovery is assured, options are
     * usually limited to logging the error and/or issuing an onError
     * signal to the subscriber.
     *
     * <p>This method returns a status indicator: If negative, it
     * represents the (negative) number of drops (failed attempts to
     * issue the item to a subscriber). Otherwise it is an estimate of
     * the maximum lag (number of items submitted but not yet
     * consumed) among all current subscribers. This value is at least
     * one (accounting for this submitted item) if there are any
     * subscribers, else zero.
     *
     * <p>If the Executor for this publisher throws a
     * RejectedExecutionException (or any other RuntimeException or
     * Error) when attempting to asynchronously notify subscribers, or
     * the drop handler throws an exception when processing a dropped
     * item, then this exception is rethrown.
     *
     * @param item the (non-null) item to publish
     * @param timeout how long to wait for resources for any subscriber
     * before giving up, in units of {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     * {@code timeout} parameter
     * @param onDrop if non-null, the handler invoked upon a drop to a
     * subscriber, with arguments of the subscriber and item; if it
     * returns true, an offer is re-attempted (once)
     * @return if negative, the (negative) number of drops; otherwise
     * an estimate of maximum lag
     * @throws IllegalStateException if closed
     * @throws NullPointerException if item is null
     * @throws RejectedExecutionException if thrown by Executor
     */
    public int offer(T item, long timeout, TimeUnit unit,
                     BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
        if (item == null) throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        int ret = 0;
        synchronized (this) {
            if (closed)
                throw new IllegalStateException("Closed");
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                int stat;
                next = b.next;
                if ((stat = b.offerNanos(item, nanos)) == 0 &&
                    onDrop != null && onDrop.test(b.subscriber, item))
                    stat = b.offer(item);
                if (stat < 0) {
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else {
                    pred = b;
                    if (stat == 0)
                        ret = (ret >= 0) ? -1 : ret - 1;
                    else if (ret >= 0 && stat > ret)
                        ret = stat;
                }
            }
        }
        return ret;
    }

    /**
     * Unless already closed, issues onComplete signals to current
     * subscribers, and disallows subsequent attempts to publish.
     */
    public void close() {
        if (!closed) {
            BufferedSubscription<T> b, next;
            synchronized (this) {
                b = clients;
                clients = null;
                closed = true;
            }
            while (b != null) {
                next = b.next;
                b.onComplete();
                b = next;
            }
        }
    }

    /**
     * Unless already closed, issues onError signals to current
     * subscribers with the given error, and disallows subsequent
     * attempts to publish.
     *
     * @param error the onError argument sent to subscribers
     * @throws NullPointerException if error is null
     */
    public void closeExceptionally(Throwable error) {
        if (error == null)
            throw new NullPointerException();
        if (!closed) {
            BufferedSubscription<T> b, next;
            synchronized (this) {
                b = clients;
                clients = null;
                closed = true;
            }
            while (b != null) {
                next = b.next;
                b.onError(error);
                b = next;
            }
        }
    }

    /**
     * Returns true if this publisher is not accepting submissions.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns true if this publisher has any subscribers.
     *
     * @return true if this publisher has any subscribers
     */
    public boolean hasSubscribers() {
        boolean nonEmpty = false;
        if (!closed) {
            synchronized (this) {
                BufferedSubscription<T> pred = null, next;
                for (BufferedSubscription<T> b = clients; b != null; b = next) {
                    next = b.next;
                    if (b.isDisabled()) {
                        if (pred == null)
                            clients = next;
                        else
                            pred.next = next;
                    }
                    else {
                        nonEmpty = true;
                        break;
                    }
                }
            }
        }
        return nonEmpty;
    }

    /**
     * Returns the number of current subscribers.
     *
     * @return the number of current subscribers
     */
    public int getNumberOfSubscribers() {
        int count = 0;
        if (!closed) {
            synchronized (this) {
                BufferedSubscription<T> pred = null, next;
                for (BufferedSubscription<T> b = clients; b != null; b = next) {
                    next = b.next;
                    if (b.isDisabled()) {
                        if (pred == null)
                            clients = next;
                        else
                            pred.next = next;
                    }
                    else {
                        pred = b;
                        ++count;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Returns the Executor used for asynchronous delivery.
     *
     * @return the Executor used for asynchronous delivery
     */
    public Executor getExecutor() {
        return executor;
    }

    /**
     * Returns the maximum per-subscriber buffer capacity.
     *
     * @return the maximum per-subscriber buffer capacity
     */
    public int getMaxBufferCapacity() {
        return maxBufferCapacity;
    }

    /**
     * Returns a list of current subscribers.
     *
     * @return list of current subscribers
     */
    public List<Flow.Subscriber<? super T>> getSubscribers() {
        ArrayList<Flow.Subscriber<? super T>> subs = new ArrayList<>();
        synchronized (this) {
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                next = b.next;
                if (b.isDisabled()) {
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else
                    subs.add(b.subscriber);
            }
        }
        return subs;
    }

    /**
     * Returns true if the given Subscriber is currently subscribed.
     *
     * @param subscriber the subscriber
     * @return true if currently subscribed
     * @throws NullPointerException if subscriber is null
     */
    public boolean isSubscribed(Flow.Subscriber<? super T> subscriber) {
        if (subscriber == null) throw new NullPointerException();
        if (!closed) {
            synchronized (this) {
                BufferedSubscription<T> pred = null, next;
                for (BufferedSubscription<T> b = clients; b != null; b = next) {
                    next = b.next;
                    if (b.isDisabled()) {
                        if (pred == null)
                            clients = next;
                        else
                            pred.next = next;
                    }
                    else if (subscriber.equals(b.subscriber))
                        return true;
                    else
                        pred = b;
                }
            }
        }
        return false;
    }

    /**
     * Returns an estimate of the minimum number of items requested
     * (via {@link Flow.Subscription#request}) but not yet produced,
     * among all current subscribers.
     *
     * @return the estimate, or zero if no subscribers
     */
    public long estimateMinimumDemand() {
        long min = Long.MAX_VALUE;
        boolean nonEmpty = false;
        synchronized (this) {
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                int n; long d;
                next = b.next;
                if ((n = b.estimateLag()) < 0) {
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else {
                    if ((d = b.demand - n) < min)
                        min = d;
                    nonEmpty = true;
                }
            }
        }
        return nonEmpty ? min : 0;
    }

    /**
     * Returns an estimate of the maximum number of items produced but
     * not yet consumed among all current subscribers.
     *
     * @return the estimate
     */
    public int estimateMaximumLag() {
        int max = 0;
        synchronized (this) {
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                int n;
                next = b.next;
                if ((n = b.estimateLag()) < 0) {
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else if (n > max)
                    max = n;
            }
        }
        return max;
    }

    /**
     * A bounded (ring) buffer with integrated control to start a
     * consumer task whenever items are available.  The buffer
     * algorithm is similar to one used inside ForkJoinPool,
     * specialized for the case of at most one concurrent producer and
     * consumer, and power of two buffer sizes. This allows methods to
     * operate without locks even while supporting resizing, blocking,
     * task-triggering, and garbage-free buffers (nulling out elements
     * when consumed), although supporting these does impose a bit of
     * overhead compared to plain fixed-size ring buffers.
     *
     * The publisher guarantees a single producer via its lock.  We
     * ensure in this class that there is at most one consumer.  The
     * request and cancel methods must be fully thread-safe but are
     * coded to exploit the most common case in which they are only
     * called by consumers (usually within onNext).
     *
     * This class also serves as its own consumer task, consuming as
     * many items/signals as possible before terminating, at which
     * point it is re-executed when needed. (The dual Runnable and
     * ForkJoinTask declaration saves overhead when executed by
     * ForkJoinPools, without impacting other kinds of Executors.)
     * Execution control is managed using the ACTIVE ctl bit. We
     * ensure that a task is active when consumable items (and
     * usually, SUBSCRIBE, ERROR or COMPLETE signals) are present and
     * there is demand (unfilled requests).  This is complicated on
     * the creation side by the possibility of exceptions when trying
     * to execute tasks. These eventually force DISABLED state, but
     * sometimes not directly. On the task side, termination (clearing
     * ACTIVE) may race with producers or request() calls, so in some
     * cases requires a re-check, re-activating if possible.
     *
     * The ctl field also manages run state. When DISABLED, no further
     * updates are possible. Disabling may be preceded by setting
     * ERROR or COMPLETE (or both -- ERROR has precedence), in which
     * case the associated Subscriber methods are invoked, possibly
     * synchronously if there is no active consumer task (including
     * cases where execute() failed). The cancel() method is supported
     * by treating as ERROR but suppressing onError signal.
     *
     * Support for blocking also exploits the fact that there is only
     * one possible waiter. ManagedBlocker-compatible control fields
     * are placed in this class itself rather than in wait-nodes.
     * Blocking control relies on the "waiter" field. Producers set
     * the field before trying to block, but must then recheck (via
     * offer) before parking. Signalling then just unparks and clears
     * waiter field. If the producer and consumer are both in the same
     * ForkJoinPool, the producer attempts to help run consumer tasks
     * that it forked before blocking.
     *
     * This class uses @Contended and heuristic field declaration
     * ordering to reduce memory contention on BufferedSubscription
     * itself, but it does not currently attempt to avoid memory
     * contention (especially including card-marks) among buffer
     * elements, that can significantly slow down some usages.
     * Addressing this may require allocating substantially more space
     * than users expect.
     */
    @SuppressWarnings("serial")
    @sun.misc.Contended
    static final class BufferedSubscription<T> extends ForkJoinTask<Void>
        implements Runnable, Flow.Subscription, ForkJoinPool.ManagedBlocker {
        // Order-sensitive field declarations
        long timeout;                     // > 0 if timed wait
        volatile long demand;             // # unfilled requests
        int maxCapacity;                  // reduced on OOME
        int putStat;                      // offer result for ManagedBlocker
        volatile int ctl;                 // atomic run state flags
        volatile int head;                // next position to take
        volatile int tail;                // next position to put
        volatile Object[] array;          // buffer: null if disabled
        Flow.Subscriber<? super T> subscriber; // null if disabled
        Executor executor;                // null if disabled
        volatile Throwable pendingError;  // holds until onError issued
        volatile Thread waiter;           // blocked producer thread
        T putItem;                        // for offer within ManagedBlocker
        BufferedSubscription<T> next;     // used only by publisher
        BufferedSubscription<T> nextRetry;// used only by publisher

        // ctl values
        static final int ACTIVE    = 0x01; // consumer task active
        static final int DISABLED  = 0x02; // final state
        static final int ERROR     = 0x04; // signal onError then disable
        static final int SUBSCRIBE = 0x08; // signal onSubscribe
        static final int COMPLETE  = 0x10; // signal onComplete when done

        static final long INTERRUPTED = -1L; // timeout vs interrupt sentinel

        /**
         * Initial/Minimum buffer capacity. Must be a power of two, at least 2.
         */
        static final int MINCAP = 8;

        BufferedSubscription(Flow.Subscriber<? super T> subscriber,
                             Executor executor, int maxBufferCapacity) {
            this.subscriber = subscriber;
            this.executor = executor;
            this.maxCapacity = maxBufferCapacity;
        }

        final boolean isDisabled() {
            return ctl == DISABLED;
        }

        /**
         * Returns estimated number of buffered items, or -1 if
         * disabled
         */
        final int estimateLag() {
            int n;
            return (ctl == DISABLED) ? -1 : ((n = tail - head) > 0) ? n : 0;
        }

        /**
         * Tries to add item and start consumer task if necessary.
         * @return -1 if disabled, 0 if dropped, else estimated lag
         */
        final int offer(T item) {
            Object[] a = array;
            int t = tail, stat = t + 1 - head, n, i;
            if (a != null && (n = a.length) >= stat && (i = t & (n - 1)) >= 0) {
                a[i] = item;
                U.putOrderedInt(this, TAIL, t + 1);
            }
            else if ((stat = growAndAdd(a, item)) <= 0)
                return stat;
            for (int c;;) { // possibly start task
                Executor e;
                if (((c = ctl) & ACTIVE) != 0)
                    break;
                else if (c == DISABLED || (e = executor) == null)
                    return -1;
                else if (demand == 0L || tail == head)
                    break;
                else if (U.compareAndSwapInt(this, CTL, c, c | ACTIVE)) {
                    try {
                        e.execute(this);
                        break;
                    } catch (RuntimeException | Error ex) { // back out
                        do {} while ((c = ctl) >= 0 &&
                                     (c & ACTIVE) != 0 &&
                                     !U.compareAndSwapInt(this, CTL, c,
                                                          c & ~ACTIVE));
                        throw ex;
                    }
                }
            }
            return stat;
        }

        /**
         * Tries to create or expand buffer, then adds item if possible
         */
        final int growAndAdd(Object[] oldArray, T item) {
            int oldLen, newLen;
            if (oldArray == null) {
                oldLen = 0;
                newLen = (maxCapacity >= MINCAP ? MINCAP :
                          maxCapacity >= 2 ? maxCapacity : 2);
            }
            else if ((oldLen = oldArray.length) >= maxCapacity ||
                     (newLen = oldLen << 1) <= 0)
                return 0;                         // cannot grow
            if (ctl == DISABLED)
                return -1;
            Object[] newArray;
            try {
                newArray = new Object[newLen];
            } catch (Throwable ex) {              // try to cope with OOME
                if (oldLen > 0)
                    maxCapacity = oldLen;        // avoid continuous failure
                return 0;
            }
            array = newArray;
            int t = tail, oldMask = oldLen - 1, newMask = newLen - 1;
            if (oldArray != null && oldMask >= 0 && newMask >= oldMask) {
                for (int j = head; j != t; ++j) { // races with consumer
                    Object x;
                    int i = j & oldMask;
                    if ((x = oldArray[i]) != null &&
                        U.compareAndSwapObject(oldArray,
                                               (((long)i) << ASHIFT) + ABASE,
                                               x, null))
                        newArray[j & newMask] = x;
                }
            }
            newArray[t & newMask] = item;
            tail = t + 1;
            return oldLen + 1;
        }

        /**
         * Spins/helps/blocks while offer returns 0
         */
        final int submit(T item) {
            int stat;
            if ((stat = offer(item)) == 0) {
                Thread thread = Thread.currentThread();
                if ((thread instanceof ForkJoinWorkerThread) &&
                    ((ForkJoinWorkerThread)thread).getPool() == executor) {
                    for (ForkJoinTask<?> t;;) { // try helping
                        if ((t = ForkJoinTask.peekNextLocalTask()) == null ||
                            !(t instanceof BufferedSubscription))
                            break;
                        if (t.tryUnfork())
                            ((BufferedSubscription<?>)t).exec();
                        if ((stat = offer(item)) != 0)
                            break;
                    }
                }
                if (stat == 0) {
                    putItem = item;
                    timeout = 0L;
                    try {
                        ForkJoinPool.managedBlock(this);
                    } catch (InterruptedException ie) {
                        timeout = INTERRUPTED;
                    }
                    stat = putStat;
                    if (timeout < 0L)
                        Thread.currentThread().interrupt();
                }
            }
            return stat;
        }

        /**
         * Timeout version of offer
         */
        final int offerNanos(T item, long nanos) {
            int stat;
            if ((stat = offer(item)) == 0 && nanos > 0L) {
                Thread thread = Thread.currentThread();
                if ((thread instanceof ForkJoinWorkerThread) &&
                    ((ForkJoinWorkerThread)thread).getPool() == executor) {
                    long deadline = System.nanoTime() + nanos;
                    for (ForkJoinTask<?> t;;) { // similar to above
                        if ((t = ForkJoinTask.peekNextLocalTask()) == null ||
                            !(t instanceof BufferedSubscription))
                            break;
                        if (t.tryUnfork())
                            ((BufferedSubscription<?>)t).exec();
                        if ((stat = offer(item)) != 0 ||
                            (nanos = deadline - System.nanoTime()) <= 0L)
                            break;
                    }
                }
                if (stat == 0 && (timeout = nanos) > 0L) {
                    putItem = item;
                    try {
                        ForkJoinPool.managedBlock(this);
                    } catch (InterruptedException ie) {
                        timeout = INTERRUPTED;
                    }
                    stat = putStat;
                    if (timeout < 0L)
                        Thread.currentThread().interrupt();
                }
            }
            return stat;
        }

        /**
         * Nulls out most fields, mainly to avoid garbage retention
         * until publisher unsubscribes, but also to help cleanly stop
         * upon error by nulling required components.
         */
        final void detach() {
            pendingError = null;
            subscriber = null;
            executor = null;
            array = null;
            Thread w = waiter;
            if (w != null) {
                waiter = null;
                LockSupport.unpark(w); // force wakeup
            }
        }

        /**
         * Issues error signal, asynchronously if a task is running,
         * else synchronously
         */
        final void onError(Throwable ex) {
            for (int c;;) {
                if ((c = ctl) == DISABLED)
                    break;
                else if ((c & ACTIVE) != 0) {
                    pendingError = ex;
                    if (U.compareAndSwapInt(this, CTL, c, c | ERROR))
                        break; // cause consumer task to exit
                }
                else if (U.compareAndSwapInt(this, CTL, c, DISABLED)) {
                    Flow.Subscriber<? super T> s = subscriber;
                    if (s != null && ex != null) {
                        try {
                            s.onError(ex);
                        } catch (Throwable ignore) {
                        }
                    }
                    detach();
                    break;
                }
            }
        }

        /**
         * Tries to start consumer task upon a signal or request;
         * disables on failure.
         */
        final void startOrDisable() {
            Executor e; // skip if already disabled
            if ((e = executor) != null) {
                try {
                    e.execute(this);
                } catch (Throwable ex) { // back out and force signal
                    for (int c;;) {
                        if ((c = ctl) == DISABLED || (c & ACTIVE) == 0)
                            break;
                        if (U.compareAndSwapInt(this, CTL, c, c & ~ACTIVE)) {
                            onError(ex);
                            break;
                        }
                    }
                }
            }
        }

        final void onComplete() {
            for (int c;;) {
                if ((c = ctl) == DISABLED)
                    break;
                if (U.compareAndSwapInt(this, CTL, c, c | (ACTIVE | COMPLETE))) {
                    if ((c & ACTIVE) == 0)
                        startOrDisable();
                    break;
                }
            }
        }

        final void onSubscribe() {
            for (int c;;) {
                if ((c = ctl) == DISABLED)
                    break;
                if (U.compareAndSwapInt(this, CTL, c, c | (ACTIVE | SUBSCRIBE))) {
                    if ((c & ACTIVE) == 0)
                        startOrDisable();
                    break;
                }
            }
        }

        /**
         * Causes consumer task to exit if active (without reporting
         * onError unless there is already a pending error), and
         * disables.
         */
        public void cancel() {
            for (int c;;) {
                if ((c = ctl) == DISABLED)
                    break;
                else if ((c & ACTIVE) != 0) {
                    if (U.compareAndSwapInt(this, CTL, c, c | ERROR))
                        break;
                }
                else if (U.compareAndSwapInt(this, CTL, c, DISABLED)) {
                    detach();
                    break;
                }
            }
        }

        /**
         * Adds to demand and possibly starts task.
         */
        public void request(long n) {
            if (n > 0L) {
                for (;;) {
                    long prev = demand, d;
                    if ((d = prev + n) < prev) // saturate
                        d = Long.MAX_VALUE;
                    if (U.compareAndSwapLong(this, DEMAND, prev, d)) {
                        for (int c, h;;) {
                            if (((c = ctl) & (ACTIVE | DISABLED)) != 0 ||
                                demand == 0L)
                                break;
                            if ((h = head) != tail) {
                                if (U.compareAndSwapInt(this, CTL, c,
                                                        c | ACTIVE)) {
                                    startOrDisable();
                                    break;
                                }
                            }
                            else if (head == h && tail == h)
                                break;
                        }
                        break;
                    }
                }
            }
            else if (n < 0L)
                onError(new IllegalArgumentException(
                            "negative subscription request"));
        }

        public final boolean isReleasable() { // for ManagedBlocker
            T item = putItem;
            if (item != null) {
                if ((putStat = offer(item)) == 0)
                    return false;
                putItem = null;
            }
            return true;
        }

        public final boolean block() { // for ManagedBlocker
            T item = putItem;
            if (item != null) {
                putItem = null;
                long nanos = timeout;
                long deadline = (nanos > 0L) ? System.nanoTime() + nanos : 0L;
                while ((putStat = offer(item)) == 0) {
                    if (Thread.interrupted()) {
                        timeout = INTERRUPTED;
                        if (nanos > 0L)
                            break;
                    }
                    else if (nanos > 0L &&
                             (nanos = deadline - System.nanoTime()) <= 0L)
                        break;
                    else if (waiter == null)
                        waiter = Thread.currentThread();
                    else {
                        if (nanos > 0L)
                            LockSupport.parkNanos(this, nanos);
                        else
                            LockSupport.park(this);
                        waiter = null;
                    }
                }
            }
            waiter = null;
            return true;
        }

        /**
         * Consumer task loop; supports resubmission when used as
         * ForkJoinTask.
         */
        public final boolean exec() {
            Flow.Subscriber<? super T> s;
            if ((s = subscriber) != null) { // else disabled
                for (;;) {
                    long d = demand; // read volatile fields in acceptable order
                    int c = ctl;
                    int h = head;
                    int t = tail;
                    Object[] a = array;
                    int i, n; Object x; Thread w;
                    if ((c & (ERROR | SUBSCRIBE | DISABLED)) != 0) {
                        if ((c & ERROR) != 0) {
                            Throwable ex = pendingError;
                            ctl = DISABLED; // no need for CAS
                            if (ex != null) {
                                try {
                                    s.onError(ex);
                                } catch (Throwable ignore) {
                                }
                            }
                        }
                        else if ((c & SUBSCRIBE) != 0) {
                            if (U.compareAndSwapInt(this, CTL, c,
                                                    c & ~SUBSCRIBE)) {
                                try {
                                    s.onSubscribe(this);
                                } catch (Throwable ex) {
                                    ctl = DISABLED;   // disable on throw
                                }
                            }
                        }
                        else {
                            detach();
                            break;
                        }
                    }
                    else if (h == t) {                     // empty
                        if (h == tail &&
                            U.compareAndSwapInt(this, CTL, c, c &= ~ACTIVE)) {
                            if (h != tail || c != (c = ctl)) { // recheck
                                if ((c & (ACTIVE | DISABLED)) != 0 ||
                                    !U.compareAndSwapInt(this, CTL, c,
                                                         c | ACTIVE))
                                    break;
                            }
                            else if ((c & COMPLETE) != 0) {
                                ctl = DISABLED;
                                try {
                                    s.onComplete();
                                } catch (Throwable ignore) {
                                }
                            }
                            else
                                break;
                        }
                    }
                    else if (a == null || (n = a.length) == 0 ||
                             (x = a[i = h & (n - 1)]) == null)
                        ;                                  // stale; retry
                    else if (d == 0L) {                    // can't take
                        if (demand == 0L &&
                            U.compareAndSwapInt(this, CTL, c, c &= ~ACTIVE) &&
                            ((demand == 0L && c == (c = ctl)) || // recheck
                             (c & (ACTIVE | DISABLED)) != 0 ||
                             !U.compareAndSwapInt(this, CTL, c, c | ACTIVE)))
                            break;
                    }
                    else if (U.compareAndSwapObject(
                                 a, (((long)i) << ASHIFT) + ABASE, x, null)) {
                        U.putOrderedInt(this, HEAD, h + 1);
                        while (!U.compareAndSwapLong(this, DEMAND, d, d - 1L))
                            d = demand;                     // almost never fails
                        if ((w = waiter) != null) {
                            waiter = null;
                            LockSupport.unpark(w);          // release producer
                        }
                        try {
                            @SuppressWarnings("unchecked") T y = (T) x;
                            s.onNext(y);
                        } catch (Throwable ex) {            // disable on throw
                            ctl = DISABLED;
                        }
                    }
                }
            }
            return false; // resubmittable; never joined
        }

        // Runnable and FJ support
        public final void run() { exec(); }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) {}

        // Unsafe mechanics
        private static final sun.misc.Unsafe U = sun.misc.Unsafe.getUnsafe();
        private static final long CTL;
        private static final long TAIL;
        private static final long HEAD;
        private static final long DEMAND;
        private static final int  ABASE;
        private static final int  ASHIFT;

        static {
            try {
                CTL = U.objectFieldOffset
                    (BufferedSubscription.class.getDeclaredField("ctl"));
                TAIL = U.objectFieldOffset
                    (BufferedSubscription.class.getDeclaredField("tail"));
                HEAD = U.objectFieldOffset
                    (BufferedSubscription.class.getDeclaredField("head"));
                DEMAND = U.objectFieldOffset
                    (BufferedSubscription.class.getDeclaredField("demand"));

                ABASE = U.arrayBaseOffset(Object[].class);
                int scale = U.arrayIndexScale(Object[].class);
                if ((scale & (scale - 1)) != 0)
                    throw new Error("data type scale not a power of two");
                ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
            } catch (ReflectiveOperationException e) {
                throw new Error(e);
            }
        }
    }
}
