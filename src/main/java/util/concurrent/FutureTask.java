/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the <tt>get</tt>
 * method will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled.
 *
 * <p>A <tt>FutureTask</tt> can be used to wrap a {@link Callable} or
 * {@link java.lang.Runnable} object.  Because <tt>FutureTask</tt>
 * implements <tt>Runnable</tt>, a <tt>FutureTask</tt> can be
 * submitted to an {@link Executor} for execution.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class FutureTask<V> extends CancellableTask implements Future<V> {

    /**
     * Constructs a <tt>FutureTask</tt> that will upon running, execute the
     * given <tt>Callable</tt>.
     *
     * @param  callable the callable task
     * @throws NullPointerException if callable is null
     */
    public FutureTask(Callable<V> callable) {
        // must set after super ctor call to use inner class
        super();
        if (callable == null)
            throw new NullPointerException();
        setRunnable(new InnerCancellableFuture<V>(callable));
    }

    /**
     * Constructs a <tt>FutureTask</tt> that will upon running, execute the
     * given <tt>Runnable</tt>, and arrange that <tt>get</tt> will return the
     * given result on successful completion.
     *
     * @param  runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * <tt>Boolean.TRUE</tt>.
     * @throws NullPointerException if runnable is null
     */
    public FutureTask(final Runnable runnable, final V result) {
        super();
        if (runnable == null)
            throw new NullPointerException();
        setRunnable(new InnerCancellableFuture<V>
                    (new Callable<V>() {
                        public V call() {
                            runnable.run();
                            return result;
                        }
                    }));
    }

    /**
     * Waits if necessary for the computation to complete, and then retrieves
     * its result.
     *
     * @return the computed result
     * @throws CancellationException if task producing this value was
     * cancelled before completion
     * @throws ExecutionException if the underlying computation threw
     * an exception
     * @throws InterruptedException if current thread was interrupted
     * while waiting
     */
    public V get() throws InterruptedException, ExecutionException {
        return ((InnerCancellableFuture<V>)getRunnable()).get();
    }

    /**
     * Waits if necessary for at most the given time for the computation to
     * complete, and then retrieves its result.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return value of this task
     * @throws CancellationException if task producing this value was
     * cancelled before completion
     * @throws ExecutionException if the underlying computation threw
     * an exception.
     * @throws InterruptedException if current thread was interrupted
     * while waiting
     * @throws TimeoutException if the wait timed out
     */
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return ((InnerCancellableFuture<V>)getRunnable()).get(timeout, unit);
    }

    /**
     * Sets the value of this task to the given value.  After this
     * method is called, the computation is assumed to be completed --
     * threads already waiting for the result via <tt>get</tt> are
     * unblocked, and future attempts to retrieve the result will not
     * block. While not explicity disallowed, it is rarely a good idea
     * to invoke <tt>set</tt> more than once.
     *
     * @param v the value
     *
     */
    protected void set(V v) {
        ((InnerCancellableFuture<V>)getRunnable()).set(v);
    }

    /**
     * Indicates that the computation has failed.  After this method
     * is called, the computation is assumed to be completed, and any
     * attempt to retrieve the result will throw an <tt>ExecutionException</tt>
     * wrapping the exception provided here.
     *
     * @param t the throwable
     */
    protected void setException(Throwable t) {
        ((InnerCancellableFuture<V>)getRunnable()).setException(t);
    }

}










