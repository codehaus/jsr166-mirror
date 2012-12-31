/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.function.Supplier;
import java.util.function.Block;
import java.util.function.BiBlock;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * A {@link Future} that may be explicitly completed (setting its
 * value and status), and may include dependent functions and actions
 * that trigger upon its completion.
 *
 * <p>Similar methods are available for Functions, Blocks, and
 * Runnables, depending on whether actions require arguments and/or
 * produce results.  Functions and actions supplied for dependent
 * completions (mainly using methods with prefix {@code then}) may be
 * performed by the thread that completes the current
 * CompletableFuture, or by any other caller of these methods.  There
 * are no guarantees about the order of processing completions unless
 * constrained by these methods.
 *
 * <p>When two or more threads attempt to {@link #complete} or {@link
 * #completeExceptionally} a CompletableFuture, only one of them
 * succeeds.
 *
 * <p>Upon exceptional completion, or when a completion entails
 * computation of a function or action, and it terminates abruptly
 * with an (unchecked) exception or error, then further completions
 * act as {@code completeExceptionally} with a {@link
 * CompletionException} holding that exception as its cause.  If a
 * CompletableFuture completes exceptionally, and is not followed by a
 * {@link #exceptionally} or {@link #handle} completion, then all of
 * its dependents (and their dependents) also complete exceptionally
 * with CompletionExceptions holding the ultimate cause.  In case of a
 * CompletionException, methods {@link #get()} and {@link #get(long,
 * TimeUnit)} throw a {@link ExecutionException} with the same cause
 * as would be held in the corresponding CompletionException. However,
 * in these cases, methods {@link #join()} and {@link #getNow} throw
 * the CompletionException, which simplifies usage especially within
 * other completion functions.
 *
 * <p>CompletableFutures themselves do not execute asynchronously.
 * However, the {@code async} methods provide commonly useful ways to
 * commence asynchronous processing, using either a given {@link
 * Executor} or by default the {@link ForkJoinPool#commonPool()}, of a
 * function or action that will result in the completion of a new
 * CompletableFuture.
 *
 * @author Doug Lea
 * @since 1.8
 */
public class CompletableFuture<T> implements Future<T> {
    /*
     * Overview:
     *
     * 1. Non-nullness of field result (set via CAS) indicates
     * done. An AltResult is used to box null as a result, as well as
     * to hold exceptions.  Using a single field makes completion fast
     * and simple to detect and trigger, at the expense of a lot of
     * encoding and decoding that infiltrates many methods. One minor
     * simplification relies on the (static) NIL (to box null results)
     * being the only AltResult with a null exception field, so we
     * don't usually need explicit comparisons with NIL.
     *
     * 2. Waiters are held in a Treiber stack similar to the one used
     * in FutureTask, Phaser, and SynchronousQueue. See their
     * internal documentation for details.
     *
     * 3. Completions are also kept in a list/stack, and pulled off
     * and run when completion is triggered. (We could in fact use the
     * same stack as for waiters, but would give up the potential
     * parallelism obtained because woken waiters help release/run
     * others (see method postComplete).  Because post-processing may
     * race with direct calls, completions extend AtomicInteger so
     * callers can claim the action via compareAndSet(0, 1).  The
     * Completion.run methods are all written a boringly similar
     * uniform way (that sometimes includes unnecessary-looking
     * checks, kept to maintain uniformity). There are enough
     * dimensions upon which they differ that factoring to use common
     * code isn't worthwhile.
     *
     * 4. The exported then/and/or methods do support a bit of
     * factoring (see thenFunction, andBlock, etc). They must cope
     * with the intrinsic races surrounding addition of a dependent
     * action versus performing the action directly because the task
     * is already complete.  For example, a CF may not be complete
     * upon entry, so a dependent completion is added, but by the time
     * it is added, the target CF is complete, so must be directly
     * executed. This is all done while avoiding unnecessary object
     * construction in safe-bypass cases.
     */

    // preliminary class definitions

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
     * basically the same way as WaitNodes. (We separate nodes from
     * the Completions themselves mainly because for the And and Or
     * methods, the same Completion object resides in two lists.)
     */
    static final class CompletionNode {
        final Completion completion;
        volatile CompletionNode next;
        CompletionNode(Completion completion) { this.completion = completion; }
    }


    // Fields

    volatile Object result;    // Either the result or boxed AltResult
    volatile WaitNode waiters; // Treiber stack of threads blocked on get()
    volatile CompletionNode completions; // list (Treiber stack) of completions

    // Basic utilities for triggering and processing completions
    // (The Completion and Async classes and internal methods that
    // use them are defined after the public methods.)

    /**
     * Removes and signals all waiting threads and runs all completions.
     */
    final void postComplete() {
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

    /**
     * Triggers completion with the encoding of the given arguments:
     * if the exception is non-null, encodes it as a wrapped
     * CompletionException unless it is one already.  Otherwise uses
     * the given result, boxed as NIL if null.
     */
    final void internalComplete(Object v, Throwable ex) {
        if (result == null)
            UNSAFE.compareAndSwapObject
                (this, RESULT, null,
                 (ex == null) ? (v == null) ? NIL : v :
                 new AltResult((ex instanceof CompletionException) ? ex :
                               new CompletionException(ex)));
        postComplete(); // help out even if not triggered
    }

    /**
     * If triggered, help release and/or process completions
     */
    final void helpPostComplete() {
        if (result != null)
            postComplete();
    }

    // public methods

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
     * to complete the returned CompletableFuture
     * @return the CompletableFuture
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        if (supplier == null) throw new NullPointerException();
        CompletableFuture<U> f = new CompletableFuture<U>();
        ForkJoinPool.commonPool().
            execute((ForkJoinTask<?>)new AsyncSupplier<U>(supplier, f));
        return f;
    }

    /**
     * Asynchronously executes using the given executor, a task that
     * completes the returned CompletableFuture with the result of the
     * given Supplier.
     *
     * @param supplier a function returning the value to be used
     * to complete the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the CompletableFuture
     */
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier,
                                                       Executor executor) {
        if (executor == null || supplier == null)
            throw new NullPointerException();
        CompletableFuture<U> f = new CompletableFuture<U>();
        executor.execute(new AsyncSupplier<U>(supplier, f));
        return f;
    }

    /**
     * Asynchronously executes in the {@link
     * ForkJoinPool#commonPool()} a task that runs the given action,
     * and then completes the returned CompletableFuture.
     *
     * @param runnable the action to run before completing the
     * returned CompletableFuture
     * @return the CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        if (runnable == null) throw new NullPointerException();
        CompletableFuture<Void> f = new CompletableFuture<Void>();
        ForkJoinPool.commonPool().
            execute((ForkJoinTask<?>)new AsyncRunnable(runnable, f));
        return f;
    }

    /**
     * Asynchronously executes using the given executor, a task that
     * runs the given action, and then completes the returned
     * CompletableFuture.
     *
     * @param runnable the action to run before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable,
                                                   Executor executor) {
        if (executor == null || runnable == null)
            throw new NullPointerException();
        CompletableFuture<Void> f = new CompletableFuture<Void>();
        executor.execute(new AsyncRunnable(runnable, f));
        return f;
    }

    /**
     * Returns {@code true} if completed in any fashion: normally,
     * exceptionally, or via cancellation.
     *
     * @return {@code true} if completed
     */
    public boolean isDone() {
        return result != null;
    }

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an
     * exception
     * @throws InterruptedException if the current thread was interrupted
     * while waiting
     */
    @SuppressWarnings("unchecked") public T get() throws InterruptedException, ExecutionException {
        Object r; Throwable ex, cause;
        if ((r = result) == null && (r = waitingGet(true)) == null)
            throw new InterruptedException();
        if (r instanceof AltResult) {
            if ((ex = ((AltResult)r).ex) != null) {
                if (ex instanceof CancellationException)
                    throw (CancellationException)ex;
                if ((ex instanceof CompletionException) &&
                    (cause = ex.getCause()) != null)
                    ex = cause;
                throw new ExecutionException(ex);
            }
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
    @SuppressWarnings("unchecked") public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        Object r; Throwable ex, cause;
        long nanos = unit.toNanos(timeout);
        if (Thread.interrupted())
            throw new InterruptedException();
        if ((r = result) == null)
            r = timedAwaitDone(nanos);
        if (r instanceof AltResult) {
            if ((ex = ((AltResult)r).ex) != null) {
                if (ex instanceof CancellationException)
                    throw (CancellationException)ex;
                if ((ex instanceof CompletionException) &&
                    (cause = ex.getCause()) != null)
                    ex = cause;
                throw new ExecutionException(ex);
            }
            return null;
        }
        return (T)r;
    }

    /**
     * Returns the result value when complete, or throws an
     * (unchecked) exception if completed exceptionally. To better
     * conform with the use of common functional forms, if a
     * computation involved in the completion of this
     * CompletableFuture threw an exception, this method throws an
     * (unchecked) {@link CompletionException} with the underlying
     * exception as its cause.
     *
     * @return the result value
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException if a completion computation threw
     * an exception
     */
    @SuppressWarnings("unchecked") public T join() {
        Object r; Throwable ex;
        if ((r = result) == null)
            r = waitingGet(false);
        if (r instanceof AltResult) {
            if ((ex = ((AltResult)r).ex) != null) {
                if (ex instanceof CancellationException)
                    throw (CancellationException)ex;
                if (ex instanceof CompletionException)
                    throw (CompletionException)ex;
                throw new CompletionException(ex);
            }
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
     * @throws CancellationException if the computation was cancelled
     * @throws CompletionException if a completion computation threw
     * an exception
     */
    @SuppressWarnings("unchecked") public T getNow(T valueIfAbsent) {
        Object r; Throwable ex;
        if ((r = result) == null)
            return valueIfAbsent;
        if (r instanceof AltResult) {
            if ((ex = ((AltResult)r).ex) != null) {
                if (ex instanceof CancellationException)
                    throw (CancellationException)ex;
                if (ex instanceof CompletionException)
                    throw (CompletionException)ex;
                throw new CompletionException(ex);
            }
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
     * to transition to a completed state, else false
     */
    public boolean complete(T value) {
        boolean triggered =
            result == null &&
            UNSAFE.compareAndSwapObject(this, RESULT, null,
                                        value == null ? NIL : value);
        postComplete();
        return triggered;
    }

    /**
     * If not already completed, causes invocations of {@link #get()}
     * and related methods to throw the given exception.
     *
     * @param ex the exception
     * @return true if this invocation caused this CompletableFuture
     * to transition to a completed state, else false
     */
    public boolean completeExceptionally(Throwable ex) {
        if (ex == null) throw new NullPointerException();
        boolean triggered =
            result == null &&
            UNSAFE.compareAndSwapObject(this, RESULT, null, new AltResult(ex));
        postComplete();
        return triggered;
    }

    /**
     * Creates and returns a CompletableFuture that is completed with
     * the result of the given function of this CompletableFuture.
     * If this CompletableFuture completes exceptionally,
     * then the returned CompletableFuture also does so,
     * with a CompletionException holding this exception as
     * its cause.
     *
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> thenApply(Function<? super T,? extends U> fn) {
        return thenFunction(fn, null);
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the {@link ForkJoinPool#commonPool()} with the
     * result of the given function of this CompletableFuture.  If
     * this CompletableFuture completes exceptionally, then the
     * returned CompletableFuture also does so, with a
     * CompletionException holding this exception as its cause.
     *
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T,? extends U> fn) {
        return thenFunction(fn, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the given executor with the result of the given
     * function of this CompletableFuture.  If this CompletableFuture
     * completes exceptionally, then the returned CompletableFuture
     * also does so, with a CompletionException holding this exception as
     * its cause.
     *
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T,? extends U> fn,
                                                   Executor executor) {
        if (executor == null) throw new NullPointerException();
        return thenFunction(fn, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed after
     * performing the given action with this CompletableFuture's
     * result when it completes.  If this CompletableFuture
     * completes exceptionally, then the returned CompletableFuture
     * also does so, with a CompletionException holding this exception as
     * its cause.
     *
     * @param block the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> thenAccept(Block<? super T> block) {
        return thenBlock(block, null);
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the {@link ForkJoinPool#commonPool()} with this
     * CompletableFuture's result when it completes.  If this
     * CompletableFuture completes exceptionally, then the returned
     * CompletableFuture also does so, with a CompletionException holding
     * this exception as its cause.
     *
     * @param block the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> thenAcceptAsync(Block<? super T> block) {
        return thenBlock(block, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the given executor with this
     * CompletableFuture's result when it completes.  If this
     * CompletableFuture completes exceptionally, then the returned
     * CompletableFuture also does so, with a CompletionException holding
     * this exception as its cause.
     *
     * @param block the action to perform before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> thenAcceptAsync(Block<? super T> block,
                                                   Executor executor) {
        if (executor == null) throw new NullPointerException();
        return thenBlock(block, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed after
     * performing the given action when this CompletableFuture
     * completes.  If this CompletableFuture completes exceptionally,
     * then the returned CompletableFuture also does so, with a
     * CompletionException holding this exception as its cause.
     *
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> thenRun(Runnable action) {
        return thenRunnable(action, null);
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the {@link ForkJoinPool#commonPool()} after
     * performing the given action when this CompletableFuture
     * completes.  If this CompletableFuture completes exceptionally,
     * then the returned CompletableFuture also does so, with a
     * CompletionException holding this exception as its cause.
     *
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> thenRunAsync(Runnable action) {
        return thenRunnable(action, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the given executor after performing the given
     * action when this CompletableFuture completes.  If this
     * CompletableFuture completes exceptionally, then the returned
     * CompletableFuture also does so, with a CompletionException holding
     * this exception as its cause.
     *
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> thenRunAsync(Runnable action,
                                                Executor executor) {
        if (executor == null) throw new NullPointerException();
        return thenRunnable(action, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed with
     * the result of the given function of this and the other given
     * CompletableFuture's results when both complete.  If this or
     * the other CompletableFuture complete exceptionally, then the
     * returned CompletableFuture also does so, with a
     * CompletionException holding the exception as its cause.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U,V> CompletableFuture<V> thenCombine(CompletableFuture<? extends U> other,
                                                  BiFunction<? super T,? super U,? extends V> fn) {
        return andFunction(other, fn, null);
    }

    /**
     * Creates and returns a CompletableFuture that is asynchronously
     * completed using the {@link ForkJoinPool#commonPool()} with
     * the result of the given function of this and the other given
     * CompletableFuture's results when both complete.  If this or
     * the other CompletableFuture complete exceptionally, then the
     * returned CompletableFuture also does so, with a
     * CompletionException holding the exception as its cause.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U,V> CompletableFuture<V> thenCombineAsync(CompletableFuture<? extends U> other,
                                                       BiFunction<? super T,? super U,? extends V> fn) {
        return andFunction(other, fn, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is
     * asynchronously completed using the given executor with the
     * result of the given function of this and the other given
     * CompletableFuture's results when both complete.  If this or
     * the other CompletableFuture complete exceptionally, then the
     * returned CompletableFuture also does so, with a
     * CompletionException holding the exception as its cause.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */

    public <U,V> CompletableFuture<V> thenCombineAsync(CompletableFuture<? extends U> other,
                                                       BiFunction<? super T,? super U,? extends V> fn,
                                                       Executor executor) {
        if (executor == null) throw new NullPointerException();
        return andFunction(other, fn, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed with
     * the results of this and the other given CompletableFuture if
     * both complete.  If this and/or the other CompletableFuture
     * complete exceptionally, then the returned CompletableFuture
     * also does so, with a CompletionException holding one of these
     * exceptions as its cause.
     *
     * @param other the other CompletableFuture
     * @param block the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<Void> thenAcceptBoth(CompletableFuture<? extends U> other,
                                                      BiBlock<? super T, ? super U> block) {
        return andBlock(other, block, null);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the {@link ForkJoinPool#commonPool()} with
     * the results of this and the other given CompletableFuture when
     * both complete.  If this and/or the other CompletableFuture
     * complete exceptionally, then the returned CompletableFuture
     * also does so, with a CompletionException holding one of these
     * exceptions as its cause.
     *
     * @param other the other CompletableFuture
     * @param block the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletableFuture<? extends U> other,
                                                           BiBlock<? super T, ? super U> block) {
        return andBlock(other, block, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the given executor with the results of
     * this and the other given CompletableFuture when both complete.
     * If this and/or the other CompletableFuture complete
     * exceptionally, then the returned CompletableFuture also does
     * so, with a CompletionException holding one of these exceptions as
     * its cause.
     *
     * @param other the other CompletableFuture
     * @param block the action to perform before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<Void> thenAcceptBothAsync(CompletableFuture<? extends U> other,
                                                           BiBlock<? super T, ? super U> block,
                                                           Executor executor) {
        if (executor == null) throw new NullPointerException();
        return andBlock(other, block, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * when this and the other given CompletableFuture both
     * complete.  If this and/or the other CompletableFuture complete
     * exceptionally, then the returned CompletableFuture also does
     * so, with a CompletionException holding one of these exceptions as
     * its cause.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> runAfterBoth(CompletableFuture<?> other,
                                                Runnable action) {
        return andRunnable(other, action, null);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the {@link ForkJoinPool#commonPool()}
     * when this and the other given CompletableFuture both
     * complete.  If this and/or the other CompletableFuture complete
     * exceptionally, then the returned CompletableFuture also does
     * so, with a CompletionException holding one of these exceptions as
     * its cause.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> runAfterBothAsync(CompletableFuture<?> other,
                                                     Runnable action) {
        return andRunnable(other, action, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the given executor
     * when this and the other given CompletableFuture both
     * complete.  If this and/or the other CompletableFuture complete
     * exceptionally, then the returned CompletableFuture also does
     * so, with a CompletionException holding one of these exceptions as
     * its cause.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> runAfterBothAsync(CompletableFuture<?> other,
                                                     Runnable action,
                                                     Executor executor) {
        if (executor == null) throw new NullPointerException();
        return andRunnable(other, action, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed with
     * the result of the given function of either this or the other
     * given CompletableFuture's results when either complete.  If
     * this and/or the other CompletableFuture complete exceptionally,
     * then the returned CompletableFuture may also do so, with a
     * CompletionException holding one of these exceptions as its cause.
     * No guarantees are made about which result or exception is used
     * in the returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> applyToEither(CompletableFuture<? extends T> other,
                                                      Function<? super T, U> fn) {
        return orFunction(other, fn, null);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the {@link ForkJoinPool#commonPool()} with
     * the result of the given function of either this or the other
     * given CompletableFuture's results when either complete.  If
     * this and/or the other CompletableFuture complete exceptionally,
     * then the returned CompletableFuture may also do so, with a
     * CompletionException holding one of these exceptions as its cause.
     * No guarantees are made about which result or exception is used
     * in the returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> applyToEitherAsync(CompletableFuture<? extends T> other,
                                                           Function<? super T, U> fn) {
        return orFunction(other, fn, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the given executor with the result of the
     * given function of either this or the other given
     * CompletableFuture's results when either complete.  If this
     * and/or the other CompletableFuture complete exceptionally, then
     * the returned CompletableFuture may also do so, with a
     * CompletionException holding one of these exceptions as its cause.
     * No guarantees are made about which result or exception is used
     * in the returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param fn the function to use to compute the value of
     * the returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public <U> CompletableFuture<U> applyToEitherAsync(CompletableFuture<? extends T> other,
                                                       Function<? super T, U> fn,
                                                       Executor executor) {
        if (executor == null) throw new NullPointerException();
        return orFunction(other, fn, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed after
     * performing the given action with the result of either this or the
     * other given CompletableFuture's result, when either complete.
     * If this and/or the other CompletableFuture complete
     * exceptionally, then the returned CompletableFuture may also do
     * so, with a CompletionException holding one of these exceptions as
     * its cause.  No guarantees are made about which exception is
     * used in the returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param block the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> acceptEither(CompletableFuture<? extends T> other,
                                                    Block<? super T> block) {
        return orBlock(other, block, null);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the {@link ForkJoinPool#commonPool()},
     * performing the given action with the result of either this or
     * the other given CompletableFuture's result, when either
     * complete.  If this and/or the other CompletableFuture complete
     * exceptionally, then the returned CompletableFuture may also do
     * so, with a CompletionException holding one of these exceptions as
     * its cause.  No guarantees are made about which exception is
     * used in the returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param block the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> acceptEitherAsync(CompletableFuture<? extends T> other,
                                                     Block<? super T> block) {
        return orBlock(other, block, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the given executor,
     * performing the given action with the result of either this or
     * the other given CompletableFuture's result, when either
     * complete.  If this and/or the other CompletableFuture complete
     * exceptionally, then the returned CompletableFuture may also do
     * so, with a CompletionException holding one of these exceptions as
     * its cause.  No guarantees are made about which exception is
     * used in the returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param block the action to perform before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> acceptEitherAsync(CompletableFuture<? extends T> other,
                                                     Block<? super T> block,
                                                     Executor executor) {
        if (executor == null) throw new NullPointerException();
        return orBlock(other, block, executor);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * after this or the other given CompletableFuture complete.  If
     * this and/or the other CompletableFuture complete exceptionally,
     * then the returned CompletableFuture may also do so, with a
     * CompletionException holding one of these exceptions as its cause.
     * No guarantees are made about which exception is used in the
     * returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> runAfterEither(CompletableFuture<?> other,
                                                  Runnable action) {
        return orRunnable(other, action, null);
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the {@link ForkJoinPool#commonPool()}
     * after this or the other given CompletableFuture complete.  If
     * this and/or the other CompletableFuture complete exceptionally,
     * then the returned CompletableFuture may also do so, with a
     * CompletionException holding one of these exceptions as its cause.
     * No guarantees are made about which exception is used in the
     * returned CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> runAfterEitherAsync(CompletableFuture<?> other,
                                                       Runnable action) {
        return orRunnable(other, action, ForkJoinPool.commonPool());
    }

    /**
     * Creates and returns a CompletableFuture that is completed
     * asynchronously using the given executor after this or the other
     * given CompletableFuture complete.  If this and/or the other
     * CompletableFuture complete exceptionally, then the returned
     * CompletableFuture may also do so, with a CompletionException
     * holding one of these exceptions as its cause.  No guarantees are
     * made about which exception is used in the returned
     * CompletableFuture.
     *
     * @param other the other CompletableFuture
     * @param action the action to perform before completing the
     * returned CompletableFuture
     * @param executor the executor to use for asynchronous execution
     * @return the new CompletableFuture
     */
    public CompletableFuture<Void> runAfterEitherAsync(CompletableFuture<?> other,
                                                       Runnable action,
                                                       Executor executor) {
        if (executor == null) throw new NullPointerException();
        return orRunnable(other, action, executor);
    }

    /**
     * Returns a CompletableFuture (or an equivalent one) produced by
     * the given function of the result of this CompletableFuture when
     * completed.  If this CompletableFuture completes exceptionally,
     * then the returned CompletableFuture also does so, with a
     * CompletionException holding this exception as its cause.
     *
     * @param fn the function returning a new CompletableFuture.
     * @return the CompletableFuture, that {@code isDone()} upon
     * return if completed by the given function, or an exception
     * occurs.
     */
    @SuppressWarnings("unchecked") public <U> CompletableFuture<U> thenCompose(Function<? super T,
                                                CompletableFuture<U>> fn) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<U> dst = null;
        ThenCompose<T,U> d = null;
        Object r;
        if ((r = result) == null) {
            dst = new CompletableFuture<U>();
            CompletionNode p = new CompletionNode
                (d = new ThenCompose<T,U>(this, fn, dst));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject
                    (this, COMPLETIONS, p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                t = (T) r;
            }
            if (ex == null) {
                try {
                    dst = fn.apply(t);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (dst == null) {
                dst = new CompletableFuture<U>();
                if (ex == null)
                    ex = new NullPointerException();
            }
            if (ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        dst.helpPostComplete();
        return dst;
    }

    /**
     * Creates and returns a CompletableFuture that is completed with
     * the result of the given function of the exception triggering
     * this CompletableFuture's completion when it completes
     * exceptionally; Otherwise, if this CompletableFuture completes
     * normally, then the returned CompletableFuture also completes
     * normally with the same value.
     *
     * @param fn the function to use to compute the value of the
     * returned CompletableFuture if this CompletableFuture completed
     * exceptionally
     * @return the new CompletableFuture
     */
    @SuppressWarnings("unchecked") public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<T> dst = new CompletableFuture<T>();
        ExceptionAction<T> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p =
                new CompletionNode(d = new ExceptionAction<T>(this, fn, dst));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject(this, COMPLETIONS,
                                                p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t = null; Throwable ex, dx = null;
            if (r instanceof AltResult) {
                if ((ex = ((AltResult)r).ex) != null)  {
                    try {
                        t = fn.apply(ex);
                    } catch (Throwable rex) {
                        dx = rex;
                    }
                }
            }
            else
                t = (T) r;
            dst.internalComplete(t, dx);
        }
        helpPostComplete();
        return dst;
    }

    /**
     * Creates and returns a CompletableFuture that is completed with
     * the result of the given function of the result and exception of
     * this CompletableFuture's completion when it completes.  The
     * given function is invoked with the result (or {@code null} if
     * none) and the exception (or {@code null} if none) of this
     * CompletableFuture when complete.
     *
     * @param fn the function to use to compute the value of the
     * returned CompletableFuture

     * @return the new CompletableFuture
     */
    @SuppressWarnings("unchecked") public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<U> dst = new CompletableFuture<U>();
        ThenHandle<T,U> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p =
                new CompletionNode(d = new ThenHandle<T,U>(this, fn, dst));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject(this, COMPLETIONS,
                                                p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                t = (T) r;
            }
            U u = null; Throwable dx = null;
            try {
                u = fn.apply(t, ex);
            } catch (Throwable rex) {
                dx = rex;
            }
            dst.internalComplete(u, dx);
        }
        helpPostComplete();
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
     * Forcibly sets or resets the value subsequently returned by
     * method get() and related methods, whether or not already
     * completed. This method is designed for use only in error
     * recovery actions, and even in such situations may result in
     * ongoing dependent completions using established versus
     * overwritten values.
     *
     * @param value the completion value
     */
    public void obtrudeValue(T value) {
        result = (value == null) ? NIL : value;
        postComplete();
    }

    /* ------------- waiting for completions -------------- */

    /**
     * Heuristic spin value for waitingGet() before blocking on
     * multiprocessors
     */
    static final int WAITING_GET_SPINS = 256;

    /**
     * Returns raw result after waiting, or null if interruptible and
     * interrupted.
     */
    private Object waitingGet(boolean interruptible) {
        WaitNode q = null;
        boolean queued = false,  interrupted = false;
        int h = 0, spins = 0;
        for (Object r;;) {
            if ((r = result) != null) {
                Throwable ex;
                if (q != null)  // suppress unpark
                    q.thread = null;
                postComplete(); // help release others
                if (interrupted)
                    Thread.currentThread().interrupt();
                return r;
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
            else if (Thread.interrupted()) {
                if (interruptible) {
                    removeWaiter(q);
                    return null;
                }
                interrupted = true;
            }
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
        final Runnable fn;
        final CompletableFuture<Void> dst;
        AsyncRunnable(Runnable fn, CompletableFuture<Void> dst) {
            this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<Void> d; Throwable ex;
            if ((d = this.dst) != null) {
                try {
                    fn.run();
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                }
                d.internalComplete(null, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncSupplier<U> extends Async {
        final Supplier<U> fn;
        final CompletableFuture<U> dst;
        AsyncSupplier(Supplier<U> fn, CompletableFuture<U> dst) {
            this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<U> d; U u; Throwable ex;
            if ((d = this.dst) != null) {
                try {
                    u = fn.get();
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                    u = null;
                }
                d.internalComplete(u, ex);
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
            CompletableFuture<U> d; U u; Throwable ex;
            if ((d = this.dst) != null) {
                try {
                    u = fn.apply(arg);
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                    u = null;
                }
                d.internalComplete(u, ex);
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
            CompletableFuture<V> d; V v; Throwable ex;
            if ((d = this.dst) != null) {
                try {
                    v = fn.apply(arg1, arg2);
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                    v = null;
                }
                d.internalComplete(v, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncBlock<T> extends Async {
        Block<? super T> fn;
        T arg;
        final CompletableFuture<Void> dst;
        AsyncBlock(T arg, Block<? super T> fn,
                   CompletableFuture<Void> dst) {
            this.arg = arg; this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<Void> d; Throwable ex;
            if ((d = this.dst) != null) {
                try {
                    fn.accept(arg);
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                }
                d.internalComplete(null, ex);
            }
            return true;
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AsyncBiBlock<T,U> extends Async {
        final BiBlock<? super T,? super U> fn;
        final T arg1;
        final U arg2;
        final CompletableFuture<Void> dst;
        AsyncBiBlock(T arg1, U arg2,
                     BiBlock<? super T,? super U> fn,
                     CompletableFuture<Void> dst) {
            this.arg1 = arg1; this.arg2 = arg2; this.fn = fn; this.dst = dst;
        }
        public final boolean exec() {
            CompletableFuture<Void> d; Throwable ex;
            if ((d = this.dst) != null) {
                try {
                    fn.accept(arg1, arg2);
                    ex = null;
                } catch (Throwable rex) {
                    ex = rex;
                }
                d.internalComplete(null, ex);
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
        @SuppressWarnings("unchecked") public final void run() {
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
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    t = (T) r;
                }
                Executor e = executor;
                U u = null;
                if (ex == null) {
                    try {
                        if (e != null)
                            e.execute(new AsyncFunction<T,U>(t, fn, dst));
                        else
                            u = fn.apply(t);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(u, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ThenBlock<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final Block<? super T> fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        ThenBlock(CompletableFuture<? extends T> src,
                     final Block<? super T> fn,
                     final CompletableFuture<Void> dst, Executor executor) {
            this.src = src; this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        @SuppressWarnings("unchecked") public final void run() {
            CompletableFuture<? extends T> a;
            Block<? super T> fn;
            CompletableFuture<Void> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    t = (T) r;
                }
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            e.execute(new AsyncBlock<T>(t, fn, dst));
                        else
                            fn.accept(t);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
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
        @SuppressWarnings("unchecked") public final void run() {
            CompletableFuture<? extends T> a;
            Runnable fn;
            CompletableFuture<Void> dst;
            Object r; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult)
                    ex = ((AltResult)r).ex;
                else
                    ex = null;
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            e.execute(new AsyncRunnable(fn, dst));
                        else
                            fn.run();
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
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
        @SuppressWarnings("unchecked") public final void run() {
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
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    t = (T) r;
                }
                if (ex != null)
                    u = null;
                else if (s instanceof AltResult) {
                    ex = ((AltResult)s).ex;
                    u = null;
                }
                else
                    u = (U) s;
                Executor e = executor;
                V v = null;
                if (ex == null) {
                    try {
                        if (e != null)
                            e.execute(new AsyncBiFunction<T,U,V>(t, u, fn, dst));
                        else
                            v = fn.apply(t, u);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(v, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class AndBlock<T,U> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<? extends U> snd;
        final BiBlock<? super T,? super U> fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        AndBlock(CompletableFuture<? extends T> src,
                 CompletableFuture<? extends U> snd,
                 BiBlock<? super T,? super U> fn,
                 CompletableFuture<Void> dst, Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        @SuppressWarnings("unchecked") public final void run() {
            Object r, s; T t; U u; Throwable ex;
            CompletableFuture<? extends T> a;
            CompletableFuture<? extends U> b;
            BiBlock<? super T,? super U> fn;
            CompletableFuture<Void> dst;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                (b = this.snd) != null &&
                (s = b.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    t = (T) r;
                }
                if (ex != null)
                    u = null;
                else if (s instanceof AltResult) {
                    ex = ((AltResult)s).ex;
                    u = null;
                }
                else
                    u = (U) s;
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            e.execute(new AsyncBiBlock<T,U>(t, u, fn, dst));
                        else
                            fn.accept(t, u);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
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
        @SuppressWarnings("unchecked") public final void run() {
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
                if (r instanceof AltResult)
                    ex = ((AltResult)r).ex;
                else
                    ex = null;
                if (ex == null && (s instanceof AltResult))
                    ex = ((AltResult)s).ex;
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            e.execute(new AsyncRunnable(fn, dst));
                        else
                            fn.run();
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
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
        @SuppressWarnings("unchecked") public final void run() {
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
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    t = (T) r;
                }
                Executor e = executor;
                U u = null;
                if (ex == null) {
                    try {
                        if (e != null)
                            e.execute(new AsyncFunction<T,U>(t, fn, dst));
                        else
                            u = fn.apply(t);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(u, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class OrBlock<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<? extends T> snd;
        final Block<? super T> fn;
        final CompletableFuture<Void> dst;
        final Executor executor;
        OrBlock(CompletableFuture<? extends T> src,
                CompletableFuture<? extends T> snd,
                Block<? super T> fn,
                CompletableFuture<Void> dst, Executor executor) {
            this.src = src; this.snd = snd;
            this.fn = fn; this.dst = dst;
            this.executor = executor;
        }
        @SuppressWarnings("unchecked") public final void run() {
            Object r; T t; Throwable ex;
            CompletableFuture<? extends T> a;
            CompletableFuture<? extends T> b;
            Block<? super T> fn;
            CompletableFuture<Void> dst;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (((a = this.src) != null && (r = a.result) != null) ||
                 ((b = this.snd) != null && (r = b.result) != null)) &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    t = (T) r;
                }
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            e.execute(new AsyncBlock<T>(t, fn, dst));
                        else
                            fn.accept(t);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
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
        @SuppressWarnings("unchecked") public final void run() {
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
                if (r instanceof AltResult)
                    ex = ((AltResult)r).ex;
                else
                    ex = null;
                Executor e = executor;
                if (ex == null) {
                    try {
                        if (e != null)
                            e.execute(new AsyncRunnable(fn, dst));
                        else
                            fn.run();
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (e == null || ex != null)
                    dst.internalComplete(null, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ExceptionAction<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final Function<? super Throwable, ? extends T> fn;
        final CompletableFuture<T> dst;
        ExceptionAction(CompletableFuture<? extends T> src,
                        Function<? super Throwable, ? extends T> fn,
                        CompletableFuture<T> dst) {
            this.src = src; this.fn = fn; this.dst = dst;
        }
        @SuppressWarnings("unchecked") public final void run() {
            CompletableFuture<? extends T> a;
            Function<? super Throwable, ? extends T> fn;
            CompletableFuture<T> dst;
            Object r; T t = null; Throwable ex, dx = null;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if ((r instanceof AltResult) &&
                    (ex = ((AltResult)r).ex) != null)  {
                    try {
                        t = fn.apply(ex);
                    } catch (Throwable rex) {
                        dx = rex;
                    }
                }
                else
                    t = (T) r;
                dst.internalComplete(t, dx);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ThenCopy<T> extends Completion {
        final CompletableFuture<? extends T> src;
        final CompletableFuture<T> dst;
        ThenCopy(CompletableFuture<? extends T> src,
                 CompletableFuture<T> dst) {
            this.src = src; this.dst = dst;
        }
        @SuppressWarnings("unchecked") public final void run() {
            CompletableFuture<? extends T> a;
            CompletableFuture<T> dst;
            Object r; Object t; Throwable ex;
            if ((dst = this.dst) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    t = r;
                }
                dst.internalComplete(t, ex);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ThenHandle<T,U> extends Completion {
        final CompletableFuture<? extends T> src;
        final BiFunction<? super T, Throwable, ? extends U> fn;
        final CompletableFuture<U> dst;
        ThenHandle(CompletableFuture<? extends T> src,
                   BiFunction<? super T, Throwable, ? extends U> fn,
                   final CompletableFuture<U> dst) {
            this.src = src; this.fn = fn; this.dst = dst;
        }
        @SuppressWarnings("unchecked") public final void run() {
            CompletableFuture<? extends T> a;
            BiFunction<? super T, Throwable, ? extends U> fn;
            CompletableFuture<U> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    t = (T) r;
                }
                U u = null; Throwable dx = null;
                try {
                    u = fn.apply(t, ex);
                } catch (Throwable rex) {
                    dx = rex;
                }
                dst.internalComplete(u, dx);
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    static final class ThenCompose<T,U> extends Completion {
        final CompletableFuture<? extends T> src;
        final Function<? super T, CompletableFuture<U>> fn;
        final CompletableFuture<U> dst;
        ThenCompose(CompletableFuture<? extends T> src,
                    Function<? super T, CompletableFuture<U>> fn,
                    final CompletableFuture<U> dst) {
            this.src = src; this.fn = fn; this.dst = dst;
        }
        @SuppressWarnings("unchecked") public final void run() {
            CompletableFuture<? extends T> a;
            Function<? super T, CompletableFuture<U>> fn;
            CompletableFuture<U> dst;
            Object r; T t; Throwable ex;
            if ((dst = this.dst) != null &&
                (fn = this.fn) != null &&
                (a = this.src) != null &&
                (r = a.result) != null &&
                compareAndSet(0, 1)) {
                if (r instanceof AltResult) {
                    ex = ((AltResult)r).ex;
                    t = null;
                }
                else {
                    ex = null;
                    t = (T) r;
                }
                CompletableFuture<U> c = null;
                U u = null;
                boolean complete = false;
                if (ex == null) {
                    try {
                        c = fn.apply(t);
                    } catch (Throwable rex) {
                        ex = rex;
                    }
                }
                if (ex != null || c == null) {
                    if (ex == null)
                        ex = new NullPointerException();
                }
                else {
                    ThenCopy<U> d = null;
                    Object s;
                    if ((s = c.result) == null) {
                        CompletionNode p = new CompletionNode
                            (d = new ThenCopy<U>(c, dst));
                        while ((s = c.result) == null) {
                            if (UNSAFE.compareAndSwapObject
                                (c, COMPLETIONS, p.next = c.completions, p))
                                break;
                        }
                    }
                    if (s != null && (d == null || d.compareAndSet(0, 1))) {
                        complete = true;
                        if (s instanceof AltResult) {
                            ex = ((AltResult)s).ex;  // no rewrap
                            u = null;
                        }
                        else
                            u = (U) s;
                    }
                }
                if (complete || ex != null)
                    dst.internalComplete(u, ex);
                if (c != null)
                    c.helpPostComplete();
            }
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /* ------------- then/and/or implementations -------------- */

    @SuppressWarnings("unchecked") private <U> CompletableFuture<U> thenFunction
        (Function<? super T,? extends U> fn,
         Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<U> dst = new CompletableFuture<U>();
        ThenFunction<T,U> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p = new CompletionNode
                (d = new ThenFunction<T,U>(this, fn, dst, e));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject
                    (this, COMPLETIONS, p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                t = (T) r;
            }
            U u = null;
            if (ex == null) {
                try {
                    if (e != null)
                        e.execute(new AsyncFunction<T,U>(t, fn, dst));
                    else
                        u = fn.apply(t);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(u, ex);
        }
        helpPostComplete();
        return dst;
    }

    @SuppressWarnings("unchecked") private CompletableFuture<Void> thenBlock
        (Block<? super T> fn,
         Executor e) {
        if (fn == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        ThenBlock<T> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p = new CompletionNode
                (d = new ThenBlock<T>(this, fn, dst, e));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject
                    (this, COMPLETIONS, p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                t = (T) r;
            }
            if (ex == null) {
                try {
                    if (e != null)
                        e.execute(new AsyncBlock<T>(t, fn, dst));
                    else
                        fn.accept(t);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        return dst;
    }

    @SuppressWarnings("unchecked") private CompletableFuture<Void> thenRunnable
        (Runnable action,
         Executor e) {
        if (action == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        ThenRunnable<T> d = null;
        Object r;
        if ((r = result) == null) {
            CompletionNode p = new CompletionNode
                (d = new ThenRunnable<T>(this, action, dst, e));
            while ((r = result) == null) {
                if (UNSAFE.compareAndSwapObject
                    (this, COMPLETIONS, p.next = completions, p))
                    break;
            }
        }
        if (r != null && (d == null || d.compareAndSet(0, 1))) {
            Throwable ex;
            if (r instanceof AltResult)
                ex = ((AltResult)r).ex;
            else
                ex = null;
            if (ex == null) {
                try {
                    if (e != null)
                        e.execute(new AsyncRunnable(action, dst));
                    else
                        action.run();
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        return dst;
    }

    @SuppressWarnings("unchecked") private <U,V> CompletableFuture<V> andFunction
        (CompletableFuture<? extends U> other,
         BiFunction<? super T,? super U,? extends V> fn,
         Executor e) {
        if (other == null || fn == null) throw new NullPointerException();
        CompletableFuture<V> dst = new CompletableFuture<V>();
        AndFunction<T,U,V> d = null;
        Object r, s = null;
        if ((r = result) == null || (s = other.result) == null) {
            d = new AndFunction<T,U,V>(this, other, fn, dst, e);
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
            T t; U u; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                t = (T) r;
            }
            if (ex != null)
                u = null;
            else if (s instanceof AltResult) {
                ex = ((AltResult)s).ex;
                u = null;
            }
            else
                u = (U) s;
            V v = null;
            if (ex == null) {
                try {
                    if (e != null)
                        e.execute(new AsyncBiFunction<T,U,V>(t, u, fn, dst));
                    else
                        v = fn.apply(t, u);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(v, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    @SuppressWarnings("unchecked") private <U> CompletableFuture<Void> andBlock
        (CompletableFuture<? extends U> other,
         BiBlock<? super T,? super U> fn,
         Executor e) {
        if (other == null || fn == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        AndBlock<T,U> d = null;
        Object r, s = null;
        if ((r = result) == null || (s = other.result) == null) {
            d = new AndBlock<T,U>(this, other, fn, dst, e);
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
            T t; U u; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                t = (T) r;
            }
            if (ex != null)
                u = null;
            else if (s instanceof AltResult) {
                ex = ((AltResult)s).ex;
                u = null;
            }
            else
                u = (U) s;
            if (ex == null) {
                try {
                    if (e != null)
                        e.execute(new AsyncBiBlock<T,U>(t, u, fn, dst));
                    else
                        fn.accept(t, u);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    @SuppressWarnings("unchecked") private CompletableFuture<Void> andRunnable
        (CompletableFuture<?> other,
         Runnable action,
         Executor e) {
        if (other == null || action == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        AndRunnable<T> d = null;
        Object r, s = null;
        if ((r = result) == null || (s = other.result) == null) {
            d = new AndRunnable<T>(this, other, action, dst, e);
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
            Throwable ex;
            if (r instanceof AltResult)
                ex = ((AltResult)r).ex;
            else
                ex = null;
            if (ex == null && (s instanceof AltResult))
                ex = ((AltResult)s).ex;
            if (ex == null) {
                try {
                    if (e != null)
                        e.execute(new AsyncRunnable(action, dst));
                    else
                        action.run();
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    @SuppressWarnings("unchecked") private <U> CompletableFuture<U> orFunction
        (CompletableFuture<? extends T> other,
         Function<? super T, U> fn,
         Executor e) {
        if (other == null || fn == null) throw new NullPointerException();
        CompletableFuture<U> dst = new CompletableFuture<U>();
        OrFunction<T,U> d = null;
        Object r;
        if ((r = result) == null && (r = other.result) == null) {
            d = new OrFunction<T,U>(this, other, fn, dst, e);
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
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                t = (T) r;
            }
            U u = null;
            if (ex == null) {
                try {
                    if (e != null)
                        e.execute(new AsyncFunction<T,U>(t, fn, dst));
                    else
                        u = fn.apply(t);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(u, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    @SuppressWarnings("unchecked") private CompletableFuture<Void> orBlock
        (CompletableFuture<? extends T> other,
         Block<? super T> fn,
         Executor e) {
        if (other == null || fn == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        OrBlock<T> d = null;
        Object r;
        if ((r = result) == null && (r = other.result) == null) {
            d = new OrBlock<T>(this, other, fn, dst, e);
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
            T t; Throwable ex;
            if (r instanceof AltResult) {
                ex = ((AltResult)r).ex;
                t = null;
            }
            else {
                ex = null;
                t = (T) r;
            }
            if (ex == null) {
                try {
                    if (e != null)
                        e.execute(new AsyncBlock<T>(t, fn, dst));
                    else
                        fn.accept(t);
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
    }

    @SuppressWarnings("unchecked") private CompletableFuture<Void> orRunnable
        (CompletableFuture<?> other,
         Runnable action,
         Executor e) {
        if (other == null || action == null) throw new NullPointerException();
        CompletableFuture<Void> dst = new CompletableFuture<Void>();
        OrRunnable<T> d = null;
        Object r;
        if ((r = result) == null && (r = other.result) == null) {
            d = new OrRunnable<T>(this, other, action, dst, e);
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
            Throwable ex;
            if (r instanceof AltResult)
                ex = ((AltResult)r).ex;
            else
                ex = null;
            if (ex == null) {
                try {
                    if (e != null)
                        e.execute(new AsyncRunnable(action, dst));
                    else
                        action.run();
                } catch (Throwable rex) {
                    ex = rex;
                }
            }
            if (e == null || ex != null)
                dst.internalComplete(null, ex);
        }
        helpPostComplete();
        other.helpPostComplete();
        return dst;
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
