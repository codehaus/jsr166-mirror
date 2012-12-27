/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.function.Block;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;


/**
 * A {@link Future} that may be explicitly completed (setting its
 * value and status), and may include dependent functions and actions
 * that trigger upon its completion.
 *
 * <p> Similar methods are available for function-based usages in
 * which dependent stages typically propagate values, as well as
 * result-less action-based usages, that are normally associated with
 * {@code CompletableFuture<Void>} Futures.  Functions and actions
 * supplied for dependent completions using {@code then}, {@code
 * andThen}, {@code orThen}, and {@code exceptionally} may be
 * performed by the thread that completes the current
 * CompletableFuture, or by any other caller of these methods.  There
 * are no guarantees about the order of processing completions unless
 * constrained by method {@code then} and related methods.
 *
 * <p> When two or more threads attempt to {@link #complete} or {@link
 * #completeExceptionally} a CompletableFuture, only one of them will
 * succeed. When completion entails computation of a function or
 * action, it is executed <em>after</em> establishing precedence. If
 * this function terminates abruptly with an exception, then method
 * {@code complete} acts as {@code completeExceptionally} with that
 * exception.
 *
 * <p>CompletableFutures themselves do not execute asynchronously.
 * However, the {@code async} methods provide commonly useful ways to
 * to commence asynchronous processing, using either a given {@link
 * Executor} or by default the {@link ForkJoinPool#commonPool()}, of a
 * function or action that will result in the completion of a new
 * CompletableFuture.
 *
 * @author Doug Lea
 * @since 1.8
 */
public class CompletableFuture<T> implements Future<T> {
    /*
     * Quick overview (more to come):
     *
     * 1. Non-nullness of field result indicates done. An AltResult is
     * used to box null as a result, as well as to hold exceptions.
     *
     * 2. Waiters are held in a Treiber stack similar to the one used
     * in FutureTask
     *
     * 3. Completions are also kept in a list/stack, and pulled off
     * and run when completion is triggered.
     */

    static final class AltResult {
        final Throwable ex; // null only for NIL
        AltResult(Throwable ex) { this.ex = ex; }
    }

    static final AltResult NIL = new AltResult(null);

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;
    }

    /**
     * Simple linked list nodes to record completions, used in
     * basically the same way as WaitNodes
     */
    static final class CompletionNode {
        final Completion completion;
        volatile CompletionNode next;
        CompletionNode(Completion completion) { this.completion = completion; }
    }


    volatile Object result;    // either the result or boxed AltResult
    volatile WaitNode waiters; // Treiber stack of threads blocked on get()
    volatile CompletionNode completions; // list (Treiber stack) of completions

    /**
     * Creates a new incomplete CompletableFuture.
     */
    public CompletableFuture() {
    }

    /**
     * Asynchronously executes in the {@link
     * ForkJoinPool#commonPool()}, a task that completes the returned
     * CompletableFuture with the result of the given Supplier.
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletableFuture.
     * @return the CompletableFuture.
     */
    public static <U> CompletableFuture<U> async(Supplier<U> supplier) {
        if (supplier == null) throw new NullPointerException();
        CompletableFuture<U> f = new CompletableFuture<U>();
        ForkJoinPool.commonPool().
            execute((ForkJoinTask<?>)new AsyncSupplier(supplier, f));
        return f;
    }

    /**
     * Asynchronously executes using the given executor, a task that
     * completes the returned CompletableFuture with the result of the
     * given Supplier.
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletableFuture.
     * @param executor the executor to use for asynchronous execution
     * @return the CompletableFuture.
     */
    public static <U> CompletableFuture<U> async(Supplier<U> supplier,
                                                 Executor executor) {
        if (executor == null || supplier == null)
            throw new NullPointerException();
        CompletableFuture<U> f = new CompletableFuture<U>();
        executor.execute(new AsyncSupplier(supplier, f));
        return f;
    }

    /**
     * Asynchronously executes in the {@link
     * ForkJoinPool#commonPool()} a task that runs the given action,
     * and then completes the returned CompletableFuture
     *
     * @param runnable the action to run before completing the
     * returned CompletableFuture.
     * @return the CompletableFuture.
     */
    public static CompletableFuture<Void> async(Runnable runnable) {
        if (runnable == null) throw new NullPointerException();
        CompletableFuture<Void> f = new CompletableFuture<Void>();
        ForkJoinPool.commonPool().
            execute((ForkJoinTask<?>)new AsyncRunnable(runnable, f));
        return f;
    }

    /**
     * Asynchronously executes using the given executor, a task that
     * runs the given action, and then completes the returned
     * CompletableFuture
     *
     * @param runnable the action to run before completing the
     * returned CompletableFuture.
     * @param executor the executor to use for asynchronous execution
     * @return the CompletableFuture.
     */
    public static CompletableFuture<Void> async(Runnable runnable,
                                                Executor executor) {
        if (executor == null || runnable == null)
            throw new NullPointerException();
        CompletableFuture<Void> f = new CompletableFuture<Void>();
        executor.execute(new AsyncRunnable(runnable, f));
        return f;
    }

    /**
     * Returns {@code true} if completed in any fashion: normally,
     * exceptionally, or cancellation.
     *
     * @return {@code true} if completed
     */
    public boolean isDone() {
        return result != null;
    }

    /**
     * Returns the result value when complete, or throws an
     * (unchecked) exception if completed exceptionally. To better
     * conform with the use of common functional forms, this method
     * transforms any checked exception possible with {@link
     * Future#get} into an (unchecked) {@link RuntimeException} with
     * the underlying exception as its cause.  (The checked exception
     * convention is available using the timed form of get.)
     *
     * @return the result value
     */
    public T get() {
        Object r; Throwable ex;
        if ((r = result) == null)
            return waitingGet();
        if (r instanceof AltResult) {
            if ((ex = ((AltResult)r).ex) != null)
                rethrow(ex);
            return null;
        }
        return (T)r;
    }

    /**
     * Returns the result value (or throws any encountered exception)
     * if completed, else returns the given valueIfAbsent.
     *
     * @param valueIfAbsent the value to return if not completed
     * @return the result value, if completed, else the given valueIfAbsent
     */
    public T getNow(T valueIfAbsent) {
        Object r; Throwable ex;
        if ((r = result) == null)
            return valueIfAbsent;
        if (r instanceof AltResult) {
            if ((ex = ((AltResult)r).ex) != null)
                rethrow(ex);
            return null;
        }
        return (T)r;
    }

    /**
     * Waits if necessary for at most the given time for completion,
     * and then retrieves its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an
     * exception
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     * @throws TimeoutException if the wait timed out
     */
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        Object r; Throwable ex;
        long nanos = unit.toNanos(timeout);
        if (Thread.interrupted())
            throw new InterruptedException();
        if ((r = result) == null)
            r = timedAwaitDone(nanos);
        if (r instanceof AltResult) {
            if ((ex = ((AltResult)r).ex) != null)
                throw new ExecutionException(ex);
            return null;
        }
        return (T)r;
    }

    /**
     * If not already completed, sets the value returned by {@link
     * #get()} and related methods to the given value.
     *
     * @param value the result value
     * @return true if this invocation caused this CompletableFuture
     * to transition to a completed state, else false.
     */
    public boolean complete(T value) {
        if (result == null &&
            UNSAFE.compareAndSwapObject(this, RESULT, null,
                                        (value == null) ? NIL : value)) {
            postComplete();
            return true;
        }
        return false;
    }

    /**
     * If not already completed, causes invocations of {@link #get()}
     * and related methods to throw the given exception.
     *
     * @param ex the exception
     * @return true if this invocation caused this CompletableFuture
     * to transition to a completed state, else false.
     */
    public boolean completeExceptionally(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        if (result == null) {
            Object r = new AltResult(ex);
            if (UNSAFE.compareAndSwapObject(this, RESULT, null, r)) {
                postComplete();
                return true;
            }
        }
        return false;
    }

    /**
     * Creates and returns a CompletableFuture that is completed with
     * the result of the given function of this CompletableFuture.
     * If this CompletableFuture completes exceptionally,
     * then the returned CompletableFuture also does so,
     * with a RuntimeException having this exception as
     * its cause.
     *
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> then(Function<? super T,? extends U> fn) {
        return thenFunction(fn, null);
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the {@link ForkJoinPool#commonPool()} with the
     * result of the given function of this CompletableFuture.  If
     * this CompletableFuture completes exceptionally, then the
     * returned CompletableFuture also does so, with a
     * RuntimeException having this exception as its cause.
     *
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> thenAsync(Function<? super T,? extends U> fn) {
        return thenFunction(fn, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the given executor with the result of the given
     * function of this CompletableFuture.  If this CompletableFuture
     * completes exceptionally, then the returned CompletableFuture
     * also does so, with a RuntimeException having this exception as
     * its cause.
     *
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> thenAsync(Function<? super T,? extends U> fn,
                                              Executor executor) {
        if (executor == null) throw new NullPointerException();
        return thenFunction(fn, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed after
     * performing the given action if/when this CompletableFuture
     * completes.  If this CompletableFuture completes exceptionally,
     * then the returned CompletableFuture also does so, with a
     * RuntimeException having this exception as its cause.
     *
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> then(Runnable action) {
        return thenRunnable(action, null);
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the {@link ForkJoinPool#commonPool()} after
     * performing the given action if/when this CompletableFuture
     * completes.  If this CompletableFuture completes exceptionally,
     * then the returned CompletableFuture also does so, with a
     * RuntimeException having this exception as its cause.
     *
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> thenAsync(Runnable action) {
        return thenRunnable(action, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the given executor after performing the given
     * action if/when this CompletableFuture completes.  If this
     * CompletableFuture completes exceptionally, then the returned
     * CompletableFuture also does so, with a RuntimeException having
     * this exception as its cause.
     *
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> thenAsync(Runnable action, Executor executor) {
        if (executor == null) throw new NullPointerException();
        return thenRunnable(action, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed with
     * the result of the given function of this and the other given
     * CompletableFuture's results if/when both complete.  If this or
     * the other CompletableFuture complete exceptionally, then the
     * returned CompletableFuture also does so, with a
     * RuntimeException having the exception as its cause.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U,V> CompletableFuture<V> andThen(CompletableFuture<? extends U> other,
                                              BiFunction<? super T,? super U,? extends V> fn) {
        return andFunction(other, fn, null);
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the {@link ForkJoinPool#commonPool()} with
     * the result of the given function of this and the other given
     * CompletableFuture's results if/when both complete.  If this or
     * the other CompletableFuture complete exceptionally, then the
     * returned CompletableFuture also does so, with a
     * RuntimeException having the exception as its cause.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U,V> CompletableFuture<V> andThenAsync(CompletableFuture<? extends U> other,
                                                   BiFunction<? super T,? super U,? extends V> fn) {
        return andFunction(other, fn, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is
     * asynchronously completed using the given executor with the
     * result of the given function of this and the other given
     * CompletableFuture's results if/when both complete.  If this or
     * the other CompletableFuture complete exceptionally, then the
     * returned CompletableFuture also does so, with a
     * RuntimeException having the exception as its cause.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */

    public <U,V> CompletableFuture<V> andThenAsync(CompletableFuture<? extends U> other,
                                                   BiFunction<? super T,? super U,? extends V> fn,
                                                   Executor executor) {
        if (executor == null) throw new NullPointerException();
        return andFunction(other, fn, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * if/when this and the other given CompletableFuture both
     * complete.  If this and/or the other CompletableFuture complete
     * exceptionally, then the returned CompletableFuture also does
     * so, with a RuntimeException having the one of the exceptions as
     * its cause.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> andThen(CompletableFuture<?> other,
                                           Runnable action) {
        return andRunnable(other, action, null);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the {@link ForkJoinPool#commonPool()}
     * if/when this and the other given CompletableFuture both
     * complete.  If this and/or the other CompletableFuture complete
     * exceptionally, then the returned CompletableFuture also does
     * so, with a RuntimeException having the one of the exceptions as
     * its cause.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> andThenAsync(CompletableFuture<?> other,
                                                Runnable action) {
        return andRunnable(other, action, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the given executor
     * if/when this and the other given CompletableFuture both
     * complete.  If this and/or the other CompletableFuture complete
     * exceptionally, then the returned CompletableFuture also does
     * so, with a RuntimeException having the one of the exceptions as
     * its cause.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> andThenAsync(CompletableFuture<?> other,
                                                Runnable action,
                                                Executor executor) {
        if (executor == null) throw new NullPointerException();
        return andRunnable(other, action, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed with
     * the result of the given function of either this or the other
     * given CompletableFuture's results if/when either complete.  If
     * this and/or the other CompletableFuture complete exceptionally,
     * then the returned CompletableFuture may also do so, with a
     * RuntimeException having one of these exceptions as its cause.
     * No guarantees are made about which result or exception is used
     * in the returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> orThen(CompletableFuture<? extends T> other,
                                           Function<? super T, U> fn) {
        return orFunction(other, fn, null);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the {@link ForkJoinPool#commonPool()} with
     * the result of the given function of either this or the other
     * given CompletableFuture's results if/when either complete.  If
     * this and/or the other CompletableFuture complete exceptionally,
     * then the returned CompletableFuture may also do so, with a
     * RuntimeException having one of these exceptions as its cause.
     * No guarantees are made about which result or exception is used
     * in the returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> orThenAsync(CompletableFuture<? extends T> other,
                                                Function<? super T, U> fn) {
        return orFunction(other, fn, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the given executor with the result of the
     * given function of either this or the other given
     * CompletableFuture's results if/when either complete.  If this
     * and/or the other CompletableFuture complete exceptionally, then
     * the returned CompletableFuture may also do so, with a
     * RuntimeException having one of these exceptions as its cause.
     * No guarantees are made about which result or exception is used
     * in the returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> orThen(CompletableFuture<? extends T> other,
                                           Function<? super T, U> fn,
                                           Executor executor) {
        if (executor == null) throw new NullPointerException();
        return orFunction(other, fn, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * after this or the other given CompletableFuture complete.  If
     * this and/or the other CompletableFuture complete exceptionally,
     * then the returned CompletableFuture may also do so, with a
     * RuntimeException having one of these exceptions as its cause.
     * No guarantees are made about which exception is used in the
     * returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> orThen(CompletableFuture<?> other,
                                          Runnable action) {
        return orRunnable(other, action, null);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the {@link ForkJoinPool#commonPool()}
     * after this or the other given CompletableFuture complete.  If
     * this and/or the other CompletableFuture complete exceptionally,
     * then the returned CompletableFuture may also do so, with a
     * RuntimeException having one of these exceptions as its cause.
     * No guarantees are made about which exception is used in the
     * returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> orThenAsync(CompletableFuture<?> other,
                                               Runnable action) {
        return orRunnable(other, action, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the given executor after this or the other
     * given CompletableFuture complete.  If this and/or the other
     * CompletableFuture complete exceptionally, then the returned
     * CompletableFuture may also do so, with a RuntimeException
     * having one of these exceptions as its cause.  No guarantees are
     * made about which exception is used in the returned
     * CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> orThenAsync(CompletableFuture<?> other,
                                               Runnable action,
                                               Executor executor) {
        if (executor == null) throw new NullPointerException();
        return orRunnable(other, action, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed after
     * performing the given action with the exception triggering this
     * CompletableFuture's completion if/when it completes
     * exceptionally.
     *
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> exceptionally(Block<Throwable> action) {
        if (action == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        ExceptionAction<T> d = null;
        Object r; Throwable ex;
        if ((r = result) == null) {
            CompletionNode p =
                new CompletionNode(d = new ExceptionAction<T>(this, action, dst));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject(this, COMPLETIONS,
                                                p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1)) &&
            (r instanceof AltResult) && (ex = ((AltResult)r).ex) != null)  {
            try {
                action.accept(ex);
                dst.complete(null);
            } catch (Throwable rex) {
                dst.completeExceptionally(rex);
            }
        }
        if (r != null)
            postComplete();
        return dst;
    }

    /**
     * Attempts to complete this CompletableFuture with
     * a {@link CancellationException}.
     *
     * @param mayInterruptIfRunning this value has no effect in this
     * implementation because interrupts are not used to control
     * processing.
     *
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        Object r;
        while ((r = result) == null) {
            r = new AltResult(new CancellationException());
            if (UNSAFE.compareAndSwapObject(this, RESULT, null, r)) {
                postComplete();
                return true;
            }
        }
        return ((r instanceof AltResult) &&
                (((AltResult)r).ex instanceof CancellationException));
    }

    /**
     * Returns {@code true} if this CompletableFuture was cancelled
     * before it completed normally.
     *
     * @return {@code true} if this CompletableFuture was cancelled
     * before it completed normally
     */
    public boolean isCancelled() {
        Object r;
        return ((r = result) != null &&
                (r instanceof AltResult) &&
                (((AltResult)r).ex instanceof CancellationException));
    }

    /**
     * Whether or not already completed, sets the value subsequently
     * returned by method get() and related methods to the given
     * value. This method is designed for use in error recovery
     * actions, and is very unlikely to be useful otherwise.
     *
     * @param value the completion value
     */
    public void force(T value) {
        result = (value == null) ? NIL : value;
        postComplete();
    }

    /**
     * Removes and signals all waiting threads and runs all completions
     */
    private void postComplete() {
        WaitNode q; Thread t;
        while ((q = waiters) != null) {
            if (UNSAFE.compareAndSwapObject(this, WAITERS, q, q.next) &&
                (t = q.thread) != null) {
                q.thread = null;
                LockSupport.unpark(t);
            }
        }

        CompletionNode h; Completion c;
        while ((h = completions) != null) {
            if (UNSAFE.compareAndSwapObject(this, COMPLETIONS, h, h.next) &&
                (c = h.completion) != null)
                c.run();
        }
    }

    /* ------------- waiting for completions -------------- */

    /**
     * Heuristic spin value for waitingGet() before blocking on
     * multiprocessors
     */
    static final int WAITING_GET_SPINS = 256;

    /**
     * Returns result after waiting.
     */
    private T waitingGet() {
        WaitNode q = null;
        boolean queued = false,  interrupted = false;
        int h = 0, spins = 0;
        for (Object r;;) {
            if ((r = result) != null) {
                if (q != null)  // suppress unpark
                    q.thread = null;
                postComplete(); // help release others
                if (interrupted)
                    Thread.currentThread().interrupt();
                if (r instanceof AltResult) {
                    if (r != NIL)
                        rethrow(((AltResult)r).ex);
                    return null;
                }
                return (T)r;
            }
            else if (h == 0) {
                h = ThreadLocalRandom.current().nextInt();
                if (Runtime.getRuntime().availableProcessors() > 1)
                    spins = WAITING_GET_SPINS;
            }
            else if (spins > 0) {
                h ^= h << 1;  // xorshift
                h ^= h >>> 3;
                if ((h ^= h << 10) >= 0)
                    --spins;
            }
            else if (q == null)
                q = new WaitNode();
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, WAITERS,
                                                     q.next = waiters, q);
            else if (Thread.interrupted())
                interrupted = true;
            else if (q.thread == null)
                q.thread = Thread.currentThread();
            else
                LockSupport.park(this);
        }
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * @param nanos time to wait
     * @return raw result
     */
    private Object timedAwaitDone(long nanos)
        throws InterruptedException, TimeoutException {
        final long deadline = System.nanoTime() + nanos;
        WaitNode q = null;
        boolean queued = false;
        for (Object r;;) {
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }
            else if ((r = result) != null) {
                if (q != null)
                    q.thread = null;
                postComplete();
                return r;
            }
            else if (q == null)
                q = new WaitNode();
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, WAITERS,
                                                     q.next = waiters, q);
            else if ((nanos = deadline - System.nanoTime()) <= 0L) {
                removeWaiter(q);
                throw new TimeoutException();
            }
            else if (q.thread == null)
                q.thread = Thread.currentThread();
            else
                LockSupport.parkNanos(this, nanos);
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            for (;;) {          // restart on removeWaiter race
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        pred = q;
                    else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) // check for race
                            continue retry;
                    }
                    else if (!UNSAFE.compareAndSwapObject(this, WAITERS, q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    /* ------------- Async tasks -------------- */

    /** Base class can act as either FJ or plain Runnable */
    static abstract class Async extends ForkJoinTask<Void> implements Runnable {
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) { }
        public final void run() { exec(); }
    }

    static final class AsyncRunnable extends Async {
        final Runnable runnable;
        final CompletableFuture<Void> dst;
        AsyncRunnable(Runnable runnable, CompletableFuture<Void> dst) {
            this.runnable = runnable; this.dst = dst;
        }
        public final boolean exec() {
            Runnable fn;
            CompletableFuture<Void> d;
            if ((fn = this.runnable) == null || (d = this.dst) == null)
                throw new NullPointerException();
            try {
                fn.run();
                d.complete(null);
            } catch (Throwable ex) {
                d.completeExceptionally(ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncSupplier<U> extends Async {
        final Supplier<U> supplier;
        final CompletableFuture<U> dst;
        AsyncSupplier(Supplier<U> supplier, CompletableFuture<U> dst) {
            this.supplier = supplier; this.dst = dst;
        }
        public final boolean exec() {
            Supplier<U> fn;
            CompletableFuture<U> d;
            if ((fn = this.supplier) == null || (d = this.dst) == null)
                throw new NullPointerException();
            try {
                d.complete(fn.get());
            } catch (Throwable ex) {
                d.completeExceptionally(ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncFunction<T,U> extends Async {
        Function<? super T,? extends U> fn;
        T arg;
        final CompletableFuture<U> dst;
        AsyncFunction(T arg, Function<? super T,? extends U> fn,
                      CompletableFuture<U> dst) {
            this.arg = arg; this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            Function<? super T,? extends U> fn;
            CompletableFuture<U> d;
            if ((fn = this.fn) == null || (d = this.dst) == null)
                throw new NullPointerException();
            try {
                d.complete(fn.apply(arg));
            } catch (Throwable ex) {
                d.completeExceptionally(ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncBiFunction<T,U,V> extends Async {
        final BiFunction<? super T,? super U,? extends V> fn;
        final T arg1;
        final U arg2;
        final CompletableFuture<V> dst;
        AsyncBiFunction(T arg1, U arg2,
                        BiFunction<? super T,? super U,? extends V> fn,
                        CompletableFuture<V> dst) {
            this.arg1 = arg1; this.arg2 = arg2; this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            BiFunction<? super T,? super U,? extends V> fn;
            CompletableFuture<V> d;
            if ((fn = this.fn) == null || (d = this.dst) == null)
                throw new NullPointerException();
            try {
                d.complete(fn.apply(arg1, arg2));
            } catch (Throwable ex) {
                d.completeExceptionally(ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /* ------------- Completions -------------- */

    // Opportunistically subclass AtomicInteger to use compareAndSet to claim.
    static abstract class Completion extends AtomicInteger implements Runnable {
    }

    static final class ThenFunction<T,U> extends Completion {
        final CompletableFuture<? extends T> src;
        final Function<? super T,? extends U> fn;
        final CompletableFuture<U> dst;
        final Executor executor;
        ThenFunction(CompletableFuture<? extends T> src,
                     final Function<? super T,? extends U> fn,
                     final CompletableFuture<U> dst, Executor executor) {
            this.src = src; this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public void run() {
            CompletableFuture<? extends T> a;
            Function<? super T,? extends U> fn;
            CompletableFuture<U> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    if ((ex = ((AltResult)r).ex) != null) {
                        dst.completeExceptionally(new RuntimeException(ex));
                        return;
                    }
                    t = null;
                }
                else
                    t = (T) r;
                try {
                    if (executor != null)
                        executor.execute(new AsyncFunction(t, fn, dst));
                    else
                        dst.complete(fn.apply(t));
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
        }
    }

    static final class ThenRunnable<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final Runnable fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        ThenRunnable(CompletableFuture<? extends T> src,
                     Runnable fn,
                     CompletableFuture<Void> dst,
                     Executor executor) {
            this.src = src; this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public void run() {
            CompletableFuture<? extends T> a;
            Runnable fn;
            CompletableFuture<Void> dst;
            Object r; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    if ((ex = ((AltResult)r).ex) != null) {
                        dst.completeExceptionally(new RuntimeException(ex));
                        return;
                    }
                }
                try {
                    if (executor != null)
                        executor.execute(new AsyncRunnable(fn, dst));
                    else {
                        fn.run();
                        dst.complete(null);
                    }
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
        }
    }

    static final class AndFunction<T,U,V> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<? extends U> snd;
        final BiFunction<? super T,? super U,? extends V> fn;
        final CompletableFuture<V> dst;
        final Executor executor;
        AndFunction(CompletableFuture<? extends T> src,
                    CompletableFuture<? extends U> snd,
                    BiFunction<? super T,? super U,? extends V> fn,
                    CompletableFuture<V> dst, Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public void run() {
            Object r, s; T t; U u; Throwable ex;
            CompletableFuture<? extends T> a;
            CompletableFuture<? extends U> b;
            BiFunction<? super T,? super U,? extends V> fn;
            CompletableFuture<V> dst;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                (b = this.snd) != null &&
                (s = b.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    if ((ex = ((AltResult)r).ex) != null) {
                        dst.completeExceptionally(new RuntimeException(ex));
                        return;
                    }
                    t = null;
                }
                else
                    t = (T) r;
                if (s instanceof AltResult) {
                    if ((ex = ((AltResult)s).ex) != null) {
                        dst.completeExceptionally(new RuntimeException(ex));
                        return;
                    }
                    u = null;
                }
                else
                    u = (U) s;
                try {
                    if (executor != null)
                        executor.execute(new AsyncBiFunction<T,U,V>(t, u, fn, dst));
                    else
                        dst.complete(fn.apply(t, u));
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
        }
    }

    static final class AndRunnable<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<?> snd;
        final Runnable fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        AndRunnable(CompletableFuture<? extends T> src,
                    CompletableFuture<?> snd,
                    Runnable fn,
                    CompletableFuture<Void> dst, Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public void run() {
            Object r, s; Throwable ex;
            final CompletableFuture<? extends T> a;
            final CompletableFuture<?> b;
            final Runnable fn;
            final CompletableFuture<Void> dst;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                (b = this.snd) != null &&
                (s = b.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    if ((ex = ((AltResult)r).ex) != null) {
                        dst.completeExceptionally(new RuntimeException(ex));
                        return;
                    }
                }
                if (s instanceof AltResult) {
                    if ((ex = ((AltResult)s).ex) != null) {
                        dst.completeExceptionally(new RuntimeException(ex));
                        return;
                    }
                }
                try {
                    if (executor != null)
                        executor.execute(new AsyncRunnable(fn, dst));
                    else {
                        fn.run();
                        dst.complete(null);
                    }
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
        }
    }

    static final class OrFunction<T,U> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<? extends T> snd;
        final Function<? super T,? extends U> fn;
        final CompletableFuture<U> dst;
        final Executor executor;
        OrFunction(CompletableFuture<? extends T> src,
                   CompletableFuture<? extends T> snd,
                   Function<? super T,? extends U> fn,
                   CompletableFuture<U> dst, Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public void run() {
            Object r; T t; Throwable ex;
            CompletableFuture<? extends T> a;
            CompletableFuture<? extends T> b;
            Function<? super T,? extends U> fn;
            CompletableFuture<U> dst;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (((a = this.src) != null && (r = a.result) != null) ||
                 ((b = this.snd) != null && (r = b.result) != null)) &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    if ((ex = ((AltResult)r).ex) != null) {
                        dst.completeExceptionally(new RuntimeException(ex));
                        return;
                    }
                    t = null;
                }
                else
                    t = (T) r;
                try {
                    if (executor != null)
                        executor.execute(new AsyncFunction(t, fn, dst));
                    else
                        dst.complete(fn.apply(t));
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
        }
    }

    static final class OrRunnable<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<?> snd;
        final Runnable fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        OrRunnable(CompletableFuture<? extends T> src,
                   CompletableFuture<?> snd,
                   Runnable fn,
                   CompletableFuture<Void> dst, Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        public void run() {
            Object r; Throwable ex;
            CompletableFuture<? extends T> a;
            final CompletableFuture<?> b;
            final Runnable fn;
            final CompletableFuture<Void> dst;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (((a = this.src) != null && (r = a.result) != null) ||
                 ((b = this.snd) != null && (r = b.result) != null)) &&
                compareAndSet(0, 1)) {
                if ((r instanceof AltResult) &&
                    (ex = ((AltResult)r).ex) != null) {
                    dst.completeExceptionally(new RuntimeException(ex));
                }
                else {
                    try {
                        if (executor != null)
                            executor.execute(new AsyncRunnable(fn, dst));
                        else {
                            fn.run();
                            dst.complete(null);
                        }
                    } catch (Throwable rex) {
                        dst.completeExceptionally(rex);
                    }
                }
            }
        }
    }

    static final class ExceptionAction<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final Block<? super Throwable> fn;
        final CompletableFuture<Void> dst;
        ExceptionAction(CompletableFuture<? extends T> src,
                        Block<? super Throwable> fn,
                        CompletableFuture<Void> dst) {
            this.src = src; this.fn = fn; this.dst = dst;
        }
        public void run() {
            CompletableFuture<? extends T> a;
            Block<? super Throwable> fn;
            CompletableFuture<Void> dst;
            Object r; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if ((r instanceof AltResult) &&
                    (ex = ((AltResult)r).ex) != null)  {
                    try {
                        fn.accept(ex);
                        dst.complete(null);
                    } catch (Throwable rex) {
                        dst.completeExceptionally(rex);
                    }
                }
            }
        }
    }

    /* ------------- then/and/or implementations -------------- */

    private <U> CompletableFuture<U> thenFunction(Function<? super T,? extends U> fn,
                                                  Executor executor) {

        if (fn == null) throw new NullPointerException();
        CompletableFuture<U> dst = new CompletableFuture<U>();
        ThenFunction<T,U> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p = new CompletionNode
                (d = new ThenFunction<T,U>(this, fn, dst, executor));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject
                    (this, COMPLETIONS, p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex = null;
            if (r instanceof AltResult) {
                if ((ex = ((AltResult)r).ex) != null)
                    dst.completeExceptionally(new RuntimeException(ex));
                t = null;
            }
            else
                t = (T) r;
            if (ex == null) {
                try {
                    if (executor != null)
                        executor.execute(new AsyncFunction(t, fn, dst));
                    else
                        dst.complete(fn.apply(t));
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
            postComplete();
        }
        return dst;
    }

    private CompletableFuture<Void> thenRunnable(Runnable action,
                                                 Executor executor) {
        if (action == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        ThenRunnable<T> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p = new CompletionNode
                (d = new ThenRunnable<T>(this, action, dst, executor));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject
                    (this, COMPLETIONS, p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            Throwable ex = null;
            if (r instanceof AltResult) {
                if ((ex = ((AltResult)r).ex) != null)
                    dst.completeExceptionally(new RuntimeException(ex));
            }
            if (ex == null) {
                try {
                    if (executor != null)
                        executor.execute(new AsyncRunnable(action, dst));
                    else {
                        action.run();
                        dst.complete(null);
                    }
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
            postComplete();
        }
        return dst;
    }

    private <U,V> CompletableFuture<V> andFunction(CompletableFuture<? extends U> other,
                                                   BiFunction<? super T,? super U,? extends V> fn,
                                                   Executor executor) {
        if (other == null || fn == null) throw new NullPointerException();
        CompletableFuture<V> dst = new CompletableFuture<V>();
        AndFunction<T,U,V> d = null;
        Object r, s = null;
        if ((r = result) == null || (s = other.result) == null) {
            d = new AndFunction<T,U,V>(this, other, fn, dst, executor);
            CompletionNode q = null, p = new CompletionNode(d);
            while ((r == null && (r = result) == null) ||
                   (s == null && (s = other.result) == null)) {
                if (q != null) {
                    if (s != null ||
                        UNSAFE.compareAndSwapObject
                        (other, COMPLETIONS, q.next = other.completions, q))
                        break;
                }
                else if (r != null ||
                         UNSAFE.compareAndSwapObject
                         (this, COMPLETIONS, p.next = completions, p)) {
                    if (s != null)
                        break;
                    q = new CompletionNode(d);
                }
            }
        }
        if (r != null && s != null && (d == null || d.compareAndSet(0, 1))) {
            T t; U u; Throwable ex = null;
            if (r instanceof AltResult) {
                if ((ex = ((AltResult)r).ex) != null)
                    dst.completeExceptionally(new RuntimeException(ex));
                t = null;
            }
            else
                t = (T) r;
            if (ex != null)
                u = null;
            else if (s instanceof AltResult) {
                if ((ex = ((AltResult)s).ex) != null)
                    dst.completeExceptionally(new RuntimeException(ex));
                u = null;
            }
            else
                u = (U) s;
            if (ex == null) {
                try {
                    if (executor != null)
                        executor.execute(new AsyncBiFunction<T,U,V>(t, u, fn, dst));
                    else
                        dst.complete(fn.apply(t, u));
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
        }
        if (r != null)
            postComplete();
        if (s != null)
            other.postComplete();
        return dst;
    }

    private CompletableFuture<Void> andRunnable(CompletableFuture<?> other,
                                                Runnable action,
                                                Executor executor) {
        if (other == null || action == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        AndRunnable<T> d = null;
        Object r, s = null;
        if ((r = result) == null || (s = other.result) == null) {
            d = new AndRunnable<T>(this, other, action, dst, executor);
            CompletionNode q = null, p = new CompletionNode(d);
            while ((r == null && (r = result) == null) ||
                   (s == null && (s = other.result) == null)) {
                if (q != null) {
                    if (s != null ||
                        UNSAFE.compareAndSwapObject
                        (other, COMPLETIONS, q.next = other.completions, q))
                        break;
                }
                else if (r != null ||
                         UNSAFE.compareAndSwapObject
                         (this, COMPLETIONS, p.next = completions, p)) {
                    if (s != null)
                        break;
                    q = new CompletionNode(d);
                }
            }
        }
        if (r != null && s != null && (d == null || d.compareAndSet(0, 1))) {
            Throwable ex = null;
            if ((r instanceof AltResult) &&
                (ex = ((AltResult)r).ex) != null)
                dst.completeExceptionally(new RuntimeException(ex));
            else if ((s instanceof AltResult) &&
                     (ex = ((AltResult)s).ex) != null)
                dst.completeExceptionally(new RuntimeException(ex));
            else {
                try {
                    if (executor != null)
                        executor.execute(new AsyncRunnable(action, dst));
                    else {
                        action.run();
                        dst.complete(null);
                    }
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
        }
        if (r != null)
            postComplete();
        if (s != null)
            other.postComplete();
        return dst;
    }

    private <U> CompletableFuture<U> orFunction(CompletableFuture<? extends T> other,
                                                Function<? super T, U> fn,
                                                Executor executor) {
        if (other == null || fn == null) throw new NullPointerException();
        CompletableFuture<U> dst = new CompletableFuture<U>();
        OrFunction<T,U> d = null;
        Object r;
        if ((r = result) == null && (r = other.result) == null) {
            d = new OrFunction<T,U>(this, other, fn, dst, executor);
            CompletionNode q = null, p = new CompletionNode(d);
            while ((r = result) == null && (r = other.result) == null) {
                if (q != null) {
                    if (UNSAFE.compareAndSwapObject
                        (other, COMPLETIONS, q.next = other.completions, q))
                        break;
                }
                else if (UNSAFE.compareAndSwapObject
                         (this, COMPLETIONS, p.next = completions, p))
                    q = new CompletionNode(d);
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex = null;
            if (r instanceof AltResult) {
                if ((ex = ((AltResult)r).ex) != null)
                    dst.completeExceptionally(new RuntimeException(ex));
                t = null;
            }
            else
                t = (T) r;
            if (ex == null) {
                try {
                    if (executor != null)
                        executor.execute(new AsyncFunction(t, fn, dst));
                    else
                        dst.complete(fn.apply(t));
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
        }
        if (r != null) {
            if (result != null)
                postComplete();
            if (other.result != null)
                other.postComplete();
        }
        return dst;
    }

    private CompletableFuture<Void> orRunnable(CompletableFuture<?> other,
                                               Runnable action,
                                               Executor executor) {
        if (other == null || action == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        OrRunnable<T> d = null;
        Object r;
        if ((r = result) == null && (r = other.result) == null) {
            d = new OrRunnable<T>(this, other, action, dst, executor);
            CompletionNode q = null, p = new CompletionNode(d);
            while ((r = result) == null && (r = other.result) == null) {
                if (q != null) {
                    if (UNSAFE.compareAndSwapObject
                        (other, COMPLETIONS, q.next = other.completions, q))
                        break;
                }
                else if (UNSAFE.compareAndSwapObject
                         (this, COMPLETIONS, p.next = completions, p))
                    q = new CompletionNode(d);
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            Throwable ex = null;
            if ((r instanceof AltResult) &&
                (ex = ((AltResult)r).ex) != null)
                dst.completeExceptionally(new RuntimeException(ex));
            else {
                try {
                    if (executor != null)
                        executor.execute(new AsyncRunnable(action, dst));
                    else {
                        action.run();
                        dst.complete(null);
                    }
                } catch (Throwable rex) {
                    dst.completeExceptionally(rex);
                }
            }
        }
        if (r != null) {
            if (result != null)
                postComplete();
            if (other.result != null)
                other.postComplete();
        }
        return dst;
    }

    /* ------------- misc -------------- */

    /**
     * A version of "sneaky throw" to relay exceptions
     */
    static void rethrow(final Throwable ex) {
        if (ex != null) {
            if (ex instanceof Error)
                throw (Error)ex;
            if (ex instanceof RuntimeException)
                throw (RuntimeException)ex;
            throw uncheckedThrowable(ex, RuntimeException.class);
        }
    }

    /**
     * The sneaky part of sneaky throw, relying on generics
     * limitations to evade compiler complaints about rethrowing
     * unchecked exceptions
     */
    @SuppressWarnings("unchecked") static <T extends Throwable>
        T uncheckedThrowable(final Throwable t, final Class<T> c) {
        return (T)t; // rely on vacuous cast
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long RESULT;
    private static final long WAITERS;
    private static final long COMPLETIONS;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = CompletableFuture.class;
            RESULT = UNSAFE.objectFieldOffset
                (k.getDeclaredField("result"));
            WAITERS = UNSAFE.objectFieldOffset
                (k.getDeclaredField("waiters"));
            COMPLETIONS = UNSAFE.objectFieldOffset
                (k.getDeclaredField("completions"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
