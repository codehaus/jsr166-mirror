/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.ArrayList;
import java.util.stream.Stream;

/**
 * Interrelated interfaces and static methods for establishing
 * flow-controlled components in which {@link Publisher Publishers}
 * produce items consumed by one or more {@link Subscriber
 * Subscribers}, each managed by a {@link Subscription
 * Subscription}. The use of flow control helps address common
 * resource issues in "push" based asynchronous systems.
 *
 * <p>These interfaces correspond to the <a
 * href="http://www.reactive-streams.org/"> reactive-streams</a>
 * specification. (<b>Preliminary release note:</b> This spec is
 * not yet finalized, so minor details could change.)
 *
 * <p><b>Preliminary release note:</b> This class may later include
 * methods supporting periodic events and/or async IO.
 *
 * @author Doug Lea
 * @since 1.9
 */
public final class Flow {

    private Flow() {} // uninstantiable

    /**
     * A producer of items (and related control messages) received by
     * Subscribers.  Each current {@link Subscriber} receives the same
     * items (via method onNext) in the same order, unless drops or
     * errors are encountered. If a Publisher encounters an error that
     * does not allow further items to be issued to a Subscriber, that
     * Subscriber receives onError, and then receives no further
     * messages.  Otherwise, if it is known that no further items will
     * be produced, each Subscriber receives onComplete.  Publishers
     * may vary in policy about whether drops (failures to issue an
     * item because of resource limitations) are treated as errors.
     * Publishers may also vary about whether Subscribers receive
     * items that were produced or available before they subscribed.
     *
     * @param <T> the published item type
     */
    @FunctionalInterface
    public static interface Publisher<T> {
        /**
         * Adds the given Subscriber if possible.  If already
         * subscribed, or the attempt to subscribe fails due to policy
         * violations or errors, the Subscriber's onError method is
         * invoked with an IllegalStateException.  Otherwise, upon
         * success, the Subscriber's onSubscribe method is invoked
         * with a new Subscription.  Subscribers may enable receiving
         * items by invoking the request method of this Subscription,
         * and may unsubscribe by invoking its cancel method.
         *
         * @param subscriber the subscriber
         * @throws NullPointerException if subscriber is null
         */
        public void subscribe(Subscriber<? super T> subscriber);
    }

    /**
     * A receiver of messages.  The methods in this interface must be
     * invoked sequentially by each Subscription, so are not required
     * to be thread-safe unless subscribing to multiple publishers.
     *
     * <p>Items (invocations of {@link #onNext}) are not issued unless
     * requested, but multiple items may be requested.  Many
     * Subscriber implementations can arrange this in the style of the
     * following example, where a request size of 1 single-steps, and
     * larger sizes (for example 64) usually allow for more efficient
     * overlapped processing.  (See also {@link #consume(long, Publisher,
     * Consumer)} that automates a common case.)
     *
     * <pre> {@code
     * class SampleSubscriber<T> implements Subscriber<T> {
     *   final Consumer<? super T> consumer;
     *   Subscription subscription;
     *   final long requestSize;
     *   long count;
     *   SampleSubscriber(long requestSize, Consumer<? super T> consumer) {
     *     this.requestSize = requestSize;
     *     this.consumer = consumer;
     *   }
     *   public void onSubscribe(Subscription subscription) {
     *     count = requestSize / 2; // re-request when half consumed
     *     (this.subscription = subscription).request(requestSize);
     *   }
     *   public void onNext(T item) {
     *     if (--count <= 0)
     *       subscription.request(count = requestSize);
     *     consumer.accept(item);
     *   }
     *   public void onError(Throwable ex) { ex.printStackTrace(); }
     *   public void onComplete() {}
     * }}</pre>
     *
     * @param <T> the subscribed item type
     */
    public static interface Subscriber<T> {
        /**
         * Method invoked prior to invoking any other Subscriber
         * methods for the given Subscription. If this method throws
         * an exception, resulting behavior is not guaranteed, but may
         * cause the Subscription to be cancelled.
         *
         * <p>Typically, implementations of this method invoke the
         * subscription's request method to enable receiving items.
         *
         * @param subscription a new subscription
         */
        public void onSubscribe(Subscription subscription);

        /**
         * Method invoked with a Subscription's next item.  If this
         * method throws an exception, resulting behavior is not
         * guaranteed, but may cause the Subscription to be cancelled.
         *
         * @param item the item
         */
        public void onNext(T item);

        /**
         * Method invoked upon an unrecoverable error encountered by a
         * Publisher or Subscription, after which no other Subscriber
         * methods are invoked by the Subscription.  If this method
         * itself throws an exception, resulting behavior is
         * undefined.
         *
         * @param throwable the exception
         */
        public void onError(Throwable throwable);

        /**
         * Method invoked when it is known that no additional onNext
         * invocations will occur for a Subscription that is not
         * already terminated by error, after which no other
         * Subscriber methods are invoked by the Subscription.  If
         * this method itself throws an exception, resulting behavior
         * is undefined.
         */
        public void onComplete();
    }

    /**
     * Message control linking Publishers and Subscribers.
     * Subscribers receive items (via onNext) only when requested, and
     * may cancel at any time. The methods in this interface are
     * intended to be invoked only by their Subscribers.
     */
    public static interface Subscription {
        /**
         * Adds the given number {@code n} of items to the current
         * unfulfilled demand for this subscription.  If {@code n} is
         * negative, the Subscriber will receive an onError signal
         * with an IllegalArgumentException argument. Otherwise, the
         * Subscriber will receive up to {@code n} additional onNext
         * invocations (or fewer if terminated).
         *
         * @param n the increment of demand; a value of {@code
         * Long.MAX_VALUE} may be considered as effectively unbounded
         */
        public void request(long n);

        /**
         * Causes the Subscriber to (eventually) stop receiving onNext
         * messages
         */
        public void cancel();
    }

    /**
     * A component that acts as both a Subscriber and Publisher.
     *
     * @param <T> the subscribed item type
     * @param <R> the published item type
     */
    public static interface Processor<T, R> extends Subscriber<T>, Publisher<R> {
    }

    // Support for static methods

    static final long DEFAULT_REQUEST_SIZE = 64L;

    abstract static class CompletableSubscriber<T,U> implements Subscriber<T>,
                                                                Consumer<T> {
        final CompletableFuture<U> status;
        Subscription subscription;
        final long requestSize;
        long count;
        CompletableSubscriber(long requestSize,
                              CompletableFuture<U> status) {
            this.status = status;
            this.requestSize = requestSize;
            this.count = requestSize >>> 1;
        }
        public final void onSubscribe(Subscription subscription) {
            (this.subscription = subscription).request(requestSize);
            status.exceptionally(ex -> { subscription.cancel(); return null;});
        }
        public final void onError(Throwable ex) {
            if (ex == null)
                ex = new IllegalStateException("null onError argument");
            status.completeExceptionally(ex);
        }
        public void onNext(T item) {
            Subscription s = subscription;
            if (s == null)
                status.completeExceptionally(
                    new IllegalStateException("onNext without subscription"));
            else {
                try {
                    if (--count <= 0)
                        s.request(count = requestSize);
                    accept(item);
                } catch (Throwable ex) {
                    status.completeExceptionally(ex);
                }
            }
        }
    }

    static final class ConsumeSubscriber<T> extends CompletableSubscriber<T,Void> {
        final Consumer<? super T> consumer;
        ConsumeSubscriber(long requestSize,
                          CompletableFuture<Void> status,
                          Consumer<? super T> consumer) {
            super(requestSize, status);
            this.consumer = consumer;
        }
        public void accept(T item) { consumer.accept(item); }
        public void onComplete() { status.complete(null); }
    }

    /**
     * Creates and subscribes a Subscriber that consumes all items
     * from the given publisher using the given Consumer function, and
     * using the given requestSize for buffering. Returns a
     * CompletableFuture that is completed normally when the publisher
     * signals onComplete, or completed exceptionally upon any error,
     * including an exception thrown by the Consumer (in which case
     * the subscription is cancelled if not already terminated).
     *
     * @param requestSize the request size for subscriptions
     * @param publisher the publisher
     * @param consumer the function applied to each onNext item
     * @return a CompletableFuture that is completed normally
     * when the publisher signals onComplete, and exceptionally
     * upon any error.
     * @throws NullPointerException if publisher or consumer are null
     * @throws IllegalArgumentException if requestSize not positive
     * @param <T> the published item type
     */
    public static <T> CompletableFuture<Void> consume(
        long requestSize, Publisher<T> publisher, Consumer<? super T> consumer) {
        if (requestSize <= 0L)
            throw new IllegalArgumentException("requestSize must be positive");
        if (publisher == null || consumer == null)
            throw new NullPointerException();
        CompletableFuture<Void> status = new CompletableFuture<>();
        publisher.subscribe(new ConsumeSubscriber<T>(
                                requestSize, status, consumer));
        return status;
    }

    /**
     * Equivalent to {@link #consume(long, Publisher, Consumer)}
     * with a request size of 64.
     *
     * @param publisher the publisher
     * @param consumer the function applied to each onNext item
     * @return a CompletableFuture that is completed normally
     * when the publisher signals onComplete, and exceptionally
     * upon any error.
     * @throws NullPointerException if publisher or consumer are null
     * @param <T> the published item type
     */
    public static <T> CompletableFuture<Void> consume(
        Publisher<T> publisher, Consumer<? super T> consumer) {
        return consume(DEFAULT_REQUEST_SIZE, publisher, consumer);
    }

    /**
     * Temporary implementation for Stream, collecting all items
     * and then applying stream operation.
     */
    static final class StreamSubscriber<T,R> extends CompletableSubscriber<T,R> {
        final Function<? super Stream<T>, ? extends R> fn;
        final ArrayList<T> items;
        StreamSubscriber(long requestSize,
                         CompletableFuture<R> status,
                         Function<? super Stream<T>, ? extends R> fn) {
            super(requestSize, status);
            this.fn = fn;
            this.items = new ArrayList<T>();
        }
        public void accept(T item) { items.add(item); }
        public void onComplete() { status.complete(fn.apply(items.stream())); }
    }

    /**
     * Creates and subscribes a Subscriber that applies the given
     * stream operation to items, and uses the given requestSize for
     * buffering. Returns a CompletableFuture that is completed
     * normally with the result of this function when the publisher
     * signals onComplete, or is completed exceptionally upon any
     * error.
     *
     * <p><b>Preliminary release note:</b> Currently, this method
     * collects all items before executing the stream
     * computation. Improvements are pending Stream integration.
     *
     * @param requestSize the request size for subscriptions
     * @param publisher the publisher
     * @param streamFunction the operation on elements
     * @return a CompletableFuture that is completed normally with the
     * result of the given function as result when the publisher signals
     * onComplete, and exceptionally upon any error.
     * @throws NullPointerException if publisher or function are null
     * @throws IllegalArgumentException if requestSize not positive
     * @param <T> the published item type
     * @param <R> the result type of the stream function
     */
    public static <T,R> CompletableFuture<R> stream(
        long requestSize, Publisher<T> publisher,
        Function<? super Stream<T>, ? extends R> streamFunction) {
        if (requestSize <= 0L)
            throw new IllegalArgumentException("requestSize must be positive");
        if (publisher == null || streamFunction == null)
            throw new NullPointerException();
        CompletableFuture<R> status = new CompletableFuture<>();
        publisher.subscribe(new StreamSubscriber<T,R>(
                                requestSize, status, streamFunction));
        return status;
    }

    /**
     * Equivalent to {@link #stream(long, Publisher, Function)}
     * with a request size of 64.
     *
     * @param publisher the publisher
     * @param streamFunction the operation on elements
     * @return a CompletableFuture that is completed normally with the
     * result of the given function as result when the publisher signals
     * onComplete, and exceptionally upon any error.
     * @throws NullPointerException if publisher or function are null
     * @param <T> the published item type
     * @param <R> the result type of the stream function
     */
    public static <T,R> CompletableFuture<R> stream(
        Publisher<T> publisher,
        Function<? super Stream<T>,? extends R> streamFunction) {
        return stream(DEFAULT_REQUEST_SIZE, publisher, streamFunction);
    }


}
