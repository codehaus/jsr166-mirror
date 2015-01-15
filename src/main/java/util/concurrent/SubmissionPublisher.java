/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.util.concurrent.locks.LockSupport;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.List;
import java.util.ArrayList;

/**
 * A {@link Flow.Publisher} that asynchronously issues submitted items
 * to current subscribers until it is closed.  Each current subscriber
 * receives newly submitted items in the same order unless drops or
 * exceptions are encountered.  Using a SubmissionPublisher allows
 * item generators to act as Publishers, although without integrated
 * flow control.  Instead they rely on drop handling and/or blocking.
 * This class may also serve as a base for subclasses that generate
 * items, and use the methods in this class to publish them.
 *
 * <p>A SubmissionPublisher uses the Executor supplied in its
 * constructor for delivery to subscribers. The best choice of
 * Executor depends on expected usage. If the generator(s) of
 * submitted items run in separate threads, and the number of
 * subscribers can be estimated, consider using a {@link
 * Executors#newFixedThreadPool}. Otherwise consider using a
 * work-stealing pool (including {@link ForkJoinPool#commonPool}).
 *
 * <p>Buffering allows producers and consumers to transiently operate
 * at different rates.  Each subscriber uses an independent buffer.
 * Buffers are created upon first use with a given initial capacity,
 * and are resized as needed up to the maximum.  (Capacity arguments
 * may be rounded up to powers of two.)  Invocations of {@code
 * subscription.request} do not directly result in buffer expansion,
 * but risk saturation if unfulfilled requests exceed the maximum
 * capacity.  Choices of buffer parameters rely on expected rates,
 * resources, and usages, that usually benefit from empirical testing.
 * As first guesses, consider initial 8 and maximum 1024.
 *
 * <p>Publication methods support different policies about what to do
 * when buffers are saturated. Method {@link #submit} blocks until
 * resources are available. This is simplest (and often appropriate
 * for relay stages; see {@link #newTransformProcessor}), but least
 * responsive. The {@code offer} methods may either immediately, or
 * with bounded timeout, drop items, but provide an opportunity to
 * interpose a handler and then retry.  While the handler is invoked,
 * other calls to methods in this class by other threads are blocked.
 * Unless recovery is assured, options are usually limited to logging
 * the error and/or issuing an onError signal to the subscriber.
 *
 * <p>If any Subscriber method throws an exception, its subscription
 * is cancelled.  If the supplied Executor throws
 * RejectedExecutionException (or any other RuntimeException or Error)
 * when attempting to execute a task, or a drop handler throws an
 * exception when processing a dropped item, then the exception is
 * rethrown. In these cases, some but not all subscribers may have
 * received items. It is usually good practice to {@link
 * #closeExceptionally} in these cases.
 *
 * @param <T> the published item type
 * @author Doug Lea
 * @since 1.9
 */
public class SubmissionPublisher<T> implements Flow.Publisher<T>,
                                               AutoCloseable {
    /*
     * Most mechanics are handled by BufferedSubscription. This class
     * mainly ensures sequentiality by using built-in synchronization
     * locks across public methods. (Using built-in locks works well
     * in the most typical case in which only one thread submits
     * items).
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
        return (n < 0) ? 1 :
            (n >= MAXIMUM_BUFFER_CAPACITY) ? MAXIMUM_BUFFER_CAPACITY : n + 1;
    }

    /**
     * Clients (BufferedSubscriptions) are maintained in a linked list
     * (via their "next" fields). This works well for publish loops.
     * It requires O(n) traversal to check for duplicate subscribers,
     * but we expect that subscribing is much less common than
     * publishing. Unsubscribing occurs only during publish loops,
     * when BufferedSubscription methods return negative values
     * signifying that they have been disabled (cancelled or closed).
     */
    BufferedSubscription<T> clients;

    // Parameters for constructing BufferedSubscriptions
    final Executor executor;
    final int minBufferCapacity;
    final int maxBufferCapacity;

    /** Run status, updated only within locks */
    volatile boolean closed;

    /**
     * Creates a new SubmissionPublisher using the given Executor for
     * async delivery to subscribers, and with the given initial and
     * maximum buffer sizes for each subscriber. In the absence of
     * other constraints, consider using {@code
     * ForrkJoinPool.commonPool(), 8, 1024}.
     *
     * @param executor the executor to use for async delivery,
     * supporting creation of at least one independent thread
     * @param initialBufferCapacity the initial capacity for each
     * subscriber's buffer (the actual capacity may be rounded up to
     * the nearest power of two)
     * @param maxBufferCapacity the maximum capacity for each
     * subscriber's buffer (the actual capacity may be rounded up to
     * the nearest power of two)
     * @throws NullPointerException if executor is null
     * @throws IllegalArgumentException if initialBufferCapacity is
     * not positive or exceeds maxBufferCapacity, or maxBufferCapacity
     * exceeds {@code 1<<30} (about 1 billion), the maximum bound for
     * a power of two array size
     */
    public SubmissionPublisher(Executor executor,
                               int initialBufferCapacity,
                               int maxBufferCapacity) {
        if (executor == null)
            throw new NullPointerException();
        if (initialBufferCapacity <= 0 || maxBufferCapacity <= 0)
            throw new IllegalArgumentException("capacity must be positive");
        if (maxBufferCapacity > MAXIMUM_BUFFER_CAPACITY)
            throw new IllegalArgumentException("capacity exceeds limit");
        if (initialBufferCapacity > maxBufferCapacity)
            throw new IllegalArgumentException("initial cannot exceed max capacity");
        int minc = roundCapacity(initialBufferCapacity);
        int maxc = roundCapacity(maxBufferCapacity);
        this.executor = executor;
        this.minBufferCapacity = minc;
        this.maxBufferCapacity = maxc;
    }

    /**
     * Adds the given Subscriber unless already subscribed.  If
     * already subscribed, the Subscriber's onError method is invoked
     * with an IllegalStateException.  Otherwise, upon success, the
     * Subscriber's onSubscribe method is invoked with a new
     * Subscription (upon exception, the exception is rethrown and the
     * Subscriber remains unsubscribed). If this SubmissionPublisher
     * is closed, the subscriber's onComplete method is then invoked.
     * Subscribers may enable receiving items by invoking the request
     * method of the returned Subscription, and may unsubscribe by
     * invoking its cancel method.
     *
     * @param subscriber the subscriber
     * @throws NullPointerException if subscriber is null
     */
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        if (subscriber == null) throw new NullPointerException();
        BufferedSubscription<T> sub = new BufferedSubscription<T>(
            subscriber, executor, minBufferCapacity, maxBufferCapacity);
        boolean present = false, clsd;
        synchronized (this) {
            clsd = closed;
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                next = b.next;
                if (b.ctl < 0) { // disabled; remove
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else if (subscriber == b.subscriber) {
                    present = true;
                    break;
                }
                pred = b;
            }
            if (!present) {
                subscriber.onSubscribe(sub); // don't link on exception
                if (!clsd) {
                    if (pred == null)
                        clients = sub;
                    else
                        pred.next = sub;
                }
            }
        }
        if (clsd)
            subscriber.onComplete();
        else if (present)
            subscriber.onError(new IllegalStateException("Already subscribed"));
    }

    /**
     * Publishes the given item, if possible, to each current
     * subscriber by asynchronously invoking its onNext method. The
     * item may be dropped by one or more subscribers if resource
     * limits are exceeded, in which case the given handler (if
     * non-null) is invoked, and if it returns true, retried once.
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
     * @return the number of drops (failed attempts to to issue
     * the item to a subscriber)
     * @throws IllegalStateException if closed
     * @throws NullPointerException if item is null
     * @throws RejectedExecutionException if thrown by Executor
     */
    public int offer(T item,
                     BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
        if (item == null) throw new NullPointerException();
        int drops = 0;
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
                        ++drops;
                }
            }
            return drops;
        }
    }

    /**
     * Publishes the given item, if possible, to each current
     * subscriber by asynchronously invoking its onNext method,
     * blocking while resources for any subscription are unavailable,
     * up to the specified timeout or the caller thread is
     * interrupted, at which point the the given handler (if non-null)
     * is invoked, and if it returns true, retried once.
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
     * @return the number of drops (failed attempts to to issue
     * the item to a subscriber)
     * @throws IllegalStateException if closed
     * @throws NullPointerException if item is null
     * @throws RejectedExecutionException if thrown by Executor
     */
    public int offer(T item, long timeout, TimeUnit unit,
                     BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
        if (item == null) throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        int drops = 0;
        synchronized (this) {
            if (closed)
                throw new IllegalStateException("Closed");
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                int stat;
                next = b.next;
                if ((stat = b.offer(item)) == 0 &&
                    (stat = b.timedOffer(item, nanos)) == 0 &&
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
                        ++drops;
                }
            }
        }
        return drops;
    }

    /**
     * Publishes the given item to each current subscriber by
     * asynchronously invoking its onNext method, blocking
     * uninterruptibly while resources for any subscriber are
     * unavailable.
     *
     * <p>If the Executor for this publisher throws a
     * RejectedExecutionException (or any other RuntimeException or
     * Error) when attempting to asynchronously notify subscribers,
     * then this exception is rethrown.
     *
     * @param item the (non-null) item to publish
     * @throws IllegalStateException if closed
     * @throws NullPointerException if item is null
     * @throws RejectedExecutionException if thrown by Executor
     */
    public void submit(T item) {
        if (item == null) throw new NullPointerException();
        synchronized (this) {
            if (closed)
                throw new IllegalStateException("Closed");
            BufferedSubscription<T> pred = null, next;
            for (BufferedSubscription<T> b = clients; b != null; b = next) {
                int stat;
                next = b.next;
                if ((stat = b.offer(item)) == 0)
                    stat = b.submit(item);
                if (stat < 0) {
                    if (pred == null)
                        clients = next;
                    else
                        pred.next = next;
                }
                else
                    pred = b;
            }
        }
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
                b.close();
                b = next;
            }
        }
    }

    /**
     * Unless already closed, issues onError signals to current
     * subscribers with the given error, and disallows subsequent
     * attempts to publish.
     *
     * @param error the onError argument sent to suibscribers
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
                b.closeExceptionally(error);
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
     * Returns true if this publisher has any subscribers,
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
                    if (b.ctl < 0) {
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
     * Returns the Executor used for asynchronous delivery.
     *
     * @return the Executor used for asynchronous delivery
     */
    public Executor getExecutor() {
        return executor;
    }

    /*
     * Returns the initial per-subsciber buffer capacity.
     *
     * @return the initial per-subsciber buffer capacity
     */
    public int getInitialBufferCapacity() {
        return minBufferCapacity;
    }

    /*
     * Returns the maximum per-subsciber buffer capacity.
     *
     * @return the maximum per-subsciber buffer capacity
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
                if (b.ctl < 0) {
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
     */
    public boolean isSubscribed(Flow.Subscriber<? super T> subscriber) {
        if (!closed) {
            synchronized (this) {
                BufferedSubscription<T> pred = null, next;
                for (BufferedSubscription<T> b = clients; b != null; b = next) {
                    next = b.next;
                    if (b.ctl < 0) {
                        if (pred == null)
                            clients = next;
                        else
                            pred.next = next;
                    }
                    else if (subscriber == b.subscriber)
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * If the given subscription is managed by a SubmissionPublisher,
     * returns an estimate of the number of items produced but not yet
     * consumed; otherwise returns zero. This method is designed only
     * for monitoring purposes, not for control.
     *
     * @param subscription the subscription
     * @return the estimate, or zero if the subscription is of an
     * unknown type.
     */
    public static int estimateAvailable(Flow.Subscription subscription) {
        if (subscription instanceof BufferedSubscription)
            return ((BufferedSubscription)subscription).estimateAvailable();
        else
            return 0;
    }

    /**
     * Returns a new {@link Flow.Processor} that uses this
     * SubmissionPublisher to relay transformed items from its source
     * using method {@link submit}, and using the given request size
     * for requests to its source subscription. (Typically,
     * requestSizes should range from the initial and maximum buffer
     * capacity of this SubmissionPublisher.)
     *
     * @param <S> the subscribed item type
     * @param requestSize the request size for subscriptions
     * with the source
     * @param transform the transform function
     * @return the new Processor
     * @throws NullPointerException if transform is null
     * @throws IllegalArgumentException if requestSize not positive
     */
    public <S> Flow.Processor<S,T> newTransformProcessor(
        long requestSize,
        Function<? super S, ? extends T> transform) {
        if (requestSize <= 0L)
            throw new IllegalArgumentException("requestSize must be positive");
        if (transform == null)
            throw new NullPointerException();
        return new TransformProcessor<S,T>(requestSize, transform, this);
    }

    /**
     * A task for consuming buffer items and signals, created and
     * executed whenever they become available. A task consumes as
     * many items/signals as possible before terminating, at which
     * point another task is created when needed. The dual Runnable
     * and ForkJoinTask declaration saves overhead when executed by
     * ForkJoinPools, without impacting other kinds of Executors.
     */
    @SuppressWarnings("serial")
    static final class ConsumerTask<T> extends ForkJoinTask<Void>
        implements Runnable {
        final BufferedSubscription<T> s;
        ConsumerTask(BufferedSubscription<T> s) { this.s = s; }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) {}
        public final boolean exec() { s.consume(); return true; }
        public final void run() { s.consume(); }
    }

    /**
     * A bounded (ring) buffer with integrated control to start
     * consumer tasks whenever items are available.  The buffer
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
     * Control over creating ConsumerTasks is managed using the ACTIVE
     * ctl bit. We ensure that a task is active when consumable items
     * (and usually, ERROR or COMPLETE signals) are present and there
     * is demand (unfulfilled requests).  This is complicated on the
     * creation side by the possibility of exceptions when trying to
     * execute tasks. These eventually force DISABLED state, but
     * sometimes not directly. On the task side, termination (clearing
     * ACTIVE) may race with producers or request() calls, so in some
     * cases requires a re-check, re-activating if possible.
     *
     * The ctl field also manages run state. When DISABLED, no further
     * updates are possible (to simplify checks, DISABLED is defined
     * as a negative value). Disabling may be preceded by setting
     * ERROR or COMPLETE (or both -- ERROR has precedence), in which
     * case the associated Subscriber methods are invoked, possibly
     * synchronously if there is no active consumer task (including
     * cases where execute() failed).
     *
     * Support for blocking also exploits the fact that there is only
     * one possible waiter. ManagedBlocker-compatible control fields
     * are placed in this class itself rather than in wait-nodes.
     * Blocking control relies on the "waiter" field. Producers set
     * the field before trying to block, but must then recheck (via
     * offer) before parking. Signalling then just unparks and clears
     * waiter field.
     *
     * This class uses @Contended and heuristic field declaration
     * ordering to reduce memory contention on BufferedSubscription
     * itself, but it does not currently attempt to avoid memory
     * contention (especially including card-marks) among buffer
     * elements, that can significantly slow down some usages.
     * Addressing this may require allocating substantially more space
     * than users expect.
     */
    @sun.misc.Contended
    static final class BufferedSubscription<T> implements Flow.Subscription,
        ForkJoinPool.ManagedBlocker {
        // Order-sensitive field declarations
        long timeout;                     // > 0 if timed wait
        volatile long demand;             // # unfilled requests
        final int minCapacity;            // initial buffer size
        int maxCapacity;                  // reduced on OOME
        int putStat;                      // offer result for ManagedBlocker
        volatile int ctl;                 // atomic run state flags
        volatile int head;                // next position to take
        volatile int tail;                // next position to put
        boolean wasInterrupted;           // true if interrupted while waiting
        volatile Object[] array;          // buffer: null if disabled
        Flow.Subscriber<? super T> subscriber; // null if disabled
        Executor executor;                // null if disabled
        volatile Throwable pendingError;  // holds until onError issued
        volatile Thread waiter;           // blocked producer thread
        T putItem;                        // for offer within ManagedBlocker
        BufferedSubscription<T> next;     // used only by publisher

        // ctl values
        static final int ACTIVE   = 0x01;       // consumer task active
        static final int ERROR    = 0x02;       // signal pending error
        static final int COMPLETE = 0x04;       // signal completion when done
        static final int DISABLED = 0x80000000; // must be negative

        BufferedSubscription(Flow.Subscriber<? super T> subscriber,
                             Executor executor, int minBufferCapacity,
                             int maxBufferCapacity) {
            this.subscriber = subscriber;
            this.executor = executor;
            this.minCapacity = minBufferCapacity;
            this.maxCapacity = maxBufferCapacity;
        }

        /**
         * @return -1 if disabled, 0 if dropped, else 1
         */
        final int offer(T item) {
            Object[] a = array;
            int t = tail, h = head, mask;
            if (a == null || (mask = a.length - 1) < 0 || t - h >= mask)
                return growAndOffer(item);
            else {
                a[t & mask] = item;
                U.putOrderedInt(this, TAIL, t + 1);
                return (ctl & ACTIVE) != 0 ? 1 : startOnOffer();
            }
        }

        /**
         * Create or expand buffer if possible, then offer
         */
        final int growAndOffer(T item) {
            int oldLen, len;
            Object[] oldA = array, a = null;
            if (oldA != null)
                len = (oldLen = oldA.length) << 1;
            else if (ctl < 0)
                return -1;  // disabled
            else {
                oldLen = 0;
                len = minCapacity;
            }
            if (oldLen >= maxCapacity || len <= 0)
                return 0;
            try {
                a = new Object[len];
            } catch (Throwable ex) { // try to cope with OOME
                if (oldLen != 0)     // avoid continuous failure
                    maxCapacity = oldLen;
                return 0;
            }
            array = a;
            int oldMask = oldLen - 1, mask = len - 1, oldTail = tail, j = head;
            if (oldA != null && oldMask >= 0 && mask > oldMask) {
                for (Object x; j != oldTail; ++j) { // races with consumer
                    int i = j & oldMask;
                    if ((x = oldA[i]) != null &&
                        U.compareAndSwapObject(oldA,
                                               (((long)i) << ASHIFT) + ABASE,
                                               x, null))
                        a[j & mask] = x;
                }
            }
            a[j & mask] = item;
            U.putOrderedInt(this, TAIL, j + 1);
            return startOnOffer();
        }

        /**
         * Tries to start consumer task if items are availsble.
         * Backs out and relays exception if executor fails
         */
        final int startOnOffer() {
            for (;;) {
                int c; Executor e;
                if (((c = ctl) & ACTIVE) != 0 || demand == 0L || tail == head)
                    break;
                if (c < 0 || (e = executor) == null)
                    return -1;
                if (U.compareAndSwapInt(this, CTL, c, c | ACTIVE)) {
                    try {
                        e.execute(new ConsumerTask<T>(this));
                        break;
                    } catch (RuntimeException | Error ex) { // back out
                        for (;;) {
                            if ((c = ctl) < 0 || (c & ACTIVE) == 0 ||
                                U.compareAndSwapInt(this, CTL, c, c & ~ACTIVE))
                                throw ex;
                        }
                    }
                }
            }
            return 1;
        }

        /**
         * Tries to start consumer task upon a signal or request;
         * disables on failure
         */
        final void startOrDisable() {
            Executor e; // skip if already disabled
            if ((e = executor) != null) {
                try {
                    e.execute(new ConsumerTask<T>(this));
                } catch (Throwable ex) { // back out and force signal
                    for (int c;;) {
                        if ((c = ctl) < 0 || (c & ACTIVE) == 0)
                            break;
                        if (U.compareAndSwapInt(this, CTL, c, c & ~ACTIVE)) {
                            closeExceptionally(ex);
                            break;
                        }
                    }
                }
            }
        }

        /**
         * Nulls out most fields, mainly to avoid garbage retention
         * until publisher unsubscribes.
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
                closeExceptionally(new IllegalArgumentException(
                                       "negative subscription request"));
        }

        public void cancel() {
            for (int c;;) {
                if ((c = ctl) < 0)
                    break;
                else if ((c & ACTIVE) != 0) {
                    if (U.compareAndSwapInt(this, CTL, c, c | ERROR))
                        break; // cause consumer task to exit
                }
                else if (U.compareAndSwapInt(this, CTL, c, DISABLED)) {
                    detach();
                    break;
                }
            }
        }

        final void closeExceptionally(Throwable ex) {
            pendingError = ex;
            for (int c;;) {
                if ((c = ctl) < 0)
                    break;
                else if ((c & ACTIVE) != 0) {
                    if (U.compareAndSwapInt(this, CTL, c, c | ERROR))
                        break; // cause consumer task to exit
                }
                else if (U.compareAndSwapInt(this, CTL, c, DISABLED)) {
                    Flow.Subscriber<? super T> s = subscriber;
                    if (s != null) {
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

        final void close() {
            for (int c;;) {
                if ((c = ctl) < 0)
                    break;
                if (U.compareAndSwapInt(this, CTL, c, c | (ACTIVE | COMPLETE))) {
                    if ((c & ACTIVE) == 0)
                        startOrDisable();
                    break;
                }
            }
        }

        final int estimateAvailable() {
            int n;
            return (ctl >= 0 && (n = tail - head) > 0) ? n : 0;
        }

        // ManagedBlocker support

        public final boolean isReleasable() {
            T item = putItem;
            if (item != null) {
                if ((putStat = offer(item)) == 0)
                    return false;
                putItem = null;
            }
            return true;
        }

        public final boolean block() {
            T item = putItem;
            if (item != null) {
                putItem = null;
                long nanos = timeout;
                long deadline = (nanos > 0L) ? System.nanoTime() + nanos : 0L;
                while ((putStat = offer(item)) == 0) {
                    if (Thread.interrupted()) {
                        wasInterrupted = true;
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

        final int submit(T item) {
            putItem = item;
            timeout = 0L;
            wasInterrupted = false;
            try {
                ForkJoinPool.managedBlock(this);
            } catch (InterruptedException cantHappen) {
            }
            if (wasInterrupted)
                Thread.currentThread().interrupt();
            return putStat;
        }

        final int timedOffer(T item, long nanos) {
            if (nanos <= 0L)
                return 0;
            putItem = item;
            timeout = nanos;
            wasInterrupted = false;
            try {
                ForkJoinPool.managedBlock(this);
            } catch (InterruptedException cantHappen) {
            }
            if (wasInterrupted)
                Thread.currentThread().interrupt();
            return putStat;
        }

        /** Consume loop called only from ConsumerTask */
        final void consume() {
            Flow.Subscriber<? super T> s;
            if ((s = subscriber) != null) { // else disabled
                for (;;) {
                    long d = demand; // read volatile fields in acceptable order
                    int c = ctl;
                    int h = head;
                    int t = tail;
                    Object[] a = array;
                    int i, n; Object x; Thread w;
                    if (c < 0) {
                        detach();
                        break;
                    }
                    else if ((c & ERROR) != 0) {
                        Throwable ex = pendingError;
                        ctl = DISABLED; // no need for CAS
                        if (ex != null) {
                            try {
                                s.onError(ex);
                            } catch (Throwable ignore) {
                            }
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
                    else if (d == 0L) {                    // can't take
                        if (demand == 0L &&
                            U.compareAndSwapInt(this, CTL, c, c &= ~ACTIVE) &&
                            ((demand == 0L && c == (c = ctl)) || // recheck
                             (c & (ACTIVE | DISABLED)) != 0 ||
                             !U.compareAndSwapInt(this, CTL, c, c | ACTIVE)))
                            break;
                    }
                    else if (a != null &&
                             (n = a.length) > 0 &&
                             (x = a[i = h & (n - 1)]) != null &&
                             U.compareAndSwapObject(
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
                        } catch (Throwable ex) {
                            ctl = DISABLED;
                        }
                    }
                }
            }
        }

        // Unsafe setup
        private static final sun.misc.Unsafe U;
        private static final long CTL;
        private static final long TAIL;
        private static final long HEAD;
        private static final long DEMAND;
        private static final int  ABASE;
        private static final int  ASHIFT;

        static {
            try {
                U = sun.misc.Unsafe.getUnsafe();
                Class<?> k = BufferedSubscription.class;
                CTL = U.objectFieldOffset
                    (k.getDeclaredField("ctl"));
                TAIL = U.objectFieldOffset
                    (k.getDeclaredField("tail"));
                HEAD = U.objectFieldOffset
                    (k.getDeclaredField("head"));
                DEMAND = U.objectFieldOffset
                    (k.getDeclaredField("demand"));
                Class<?> ak = Object[].class;
                ABASE = U.arrayBaseOffset(ak);
                int scale = U.arrayIndexScale(ak);
                if ((scale & (scale - 1)) != 0)
                    throw new Error("data type scale not a power of two");
                ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    static final class TransformProcessor<U,R> implements Flow.Processor<U,R> {
        final Function<? super U, ? extends R> fn;
        final SubmissionPublisher<R> sink;
        Flow.Subscription subscription;
        final long requestSize;
        long count;
        TransformProcessor(long requestSize,
                           Function<? super U, ? extends R> fn,
                           SubmissionPublisher<R> sink) {
            this.fn = fn;
            this.sink = sink;
            this.requestSize = requestSize;
            this.count = requestSize >>> 1;
        }
        public void subscribe(Flow.Subscriber<? super R> subscriber) {
            sink.subscribe(subscriber);
        }
        public void onSubscribe(Flow.Subscription subscription) {
            (this.subscription = subscription).request(requestSize);
        }
        public void onNext(U item) {
            Flow.Subscription s = subscription;
            if (s == null)
                sink.closeExceptionally(
                    new IllegalStateException("onNext without subscription"));
            else {
                try {
                    if (--count <= 0)
                        s.request(count = requestSize);
                    sink.submit(fn.apply(item));
                } catch (Throwable ex) {
                    try {
                        s.cancel();
                    } finally {
                        sink.closeExceptionally(ex);
                    }
                }
            }
        }
        public void onError(Throwable ex) {
            sink.closeExceptionally(ex);
        }
        public void onComplete() {
            sink.close();
        }
    }
}
