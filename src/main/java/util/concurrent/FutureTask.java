/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * A cancellable asynchronous computation.
 *
 * <p>Provides methods to start and cancel the computation, query to see if
 * the computation is complete, and retrieve the result of the computation.
 * The result can only be retrieved when the computation has completed;
 * the <tt>get</tt> method will block if the computation has not yet completed.
 * Once the computation is completed, the result cannot be changed, nor can the
 * computation be restarted or cancelled.
 *
 * <p>Because <tt>FutureTask</tt> implements <tt>Runnable</tt>, a
 * <tt>FutureTask</tt> can be submitted to an {@link Executor} for 
 * current or deferred execution.
 *
 * <p>A <tt>FutureTask</tt> can be used to wrap a <tt>Callable</tt> or 
 * <tt>Runnable</tt> object so that it can be scheduled for execution in a 
 * thread or an <tt>Executor</tt>, cancel
 * computation before the computation completes, and wait for or
 * retrieve the results.  If the computation threw an exception, the
 * exception is propagated to any thread that attempts to retrieve the
 * result.
 *
 * @see Executor
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/05/27 18:14:40 $
 * @editor $Author: dl $
 */
public class FutureTask<V> implements Cancellable, Future<V>, Runnable {
    private V result;
    private Throwable exception;
    private boolean ready;
    private Thread runner;
    private final Callable<V> callable;
    private boolean cancelled;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition accessible = lock.newCondition();

    /**
     * Constructs a <tt>FutureTask</tt> that will upon running, execute the
     * given <tt>Callable</tt>.
     *
     * @param  callable the callable task
     */
    public FutureTask(Callable<V> callable) {
        this.callable = callable;
    }

    /**
     * Constructs a <tt>FutureTask</tt> that will upon running, execute the
     * given <tt>Runnable</tt>, and arrange that <tt>get</tt> will return the
     * given result on successful completion.
     *
     * @param  runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider just using
     * <tt>Boolean.TRUE</tt>.
     */
    public FutureTask(final Runnable runnable, final V result) {
        callable = new Callable<V>() {
            public V call() {
                runnable.run();
                return result;
            }
        };
    }

    /* Runnable implementation. */

    /** Starts the computation. */
    public void run() {
        doRun();
    }

    /**
     * Executes the callable if not already cancelled or running, and
     * sets the value or exception with its results.
     */
    protected void doRun() {
        try {
            lock.lock();
            try {
                if (ready || runner != null)
                    return;
                runner = Thread.currentThread();
            }
            finally {
                lock.unlock();
            }
            set(callable.call());
        }
        catch(Throwable ex) {
            setException(ex);
        }
    }

    /* Future implementation. INHERIT this javadoc from interface??? Note CancellationException. */

    /**
     * Waits if necessary for the computation to complete, and then retrieves
     * its result.
     *
     * @return the computed result
     * @throws CancellationException if task producing this value was
     * cancelled before completion
     * @throws ExecutionException if the underlying computation threw an exception
     * @throws InterruptedException if current thread was interrupted while waiting
     */
    public V get() throws InterruptedException, ExecutionException {
        lock.lock();
        try {
            while (!ready)
                accessible.await();
            if (cancelled)
                throw new CancellationException();
            else if (exception != null)
                throw new ExecutionException(exception);
            else
                return result;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Waits if necessary for at most the given time for the computation to
     * complete, and then retrieves its result.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return value of this task
     * @throws CancellationException if task producing this value was cancelled before completion
     * @throws ExecutionException if the underlying computation
     * threw an exception.
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws TimeoutException if the wait timed out
     */
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {

        lock.lock();
        try {
            if (!ready) {
                long nanos = unit.toNanos(timeout);
                do {
                    if (nanos <= 0)
                        throw new TimeoutException();
                    nanos = accessible.awaitNanos(nanos);
                } while (!ready);
            }
            if (cancelled)
                throw new CancellationException();
            else if (exception != null)
                throw new ExecutionException(exception);
            else
                return result;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Sets the value of this task to the given value.  This method
     * should only be called once; once it is called, the computation
     * is assumed to have completed.
     *
     * @param v the value
     *
     * @fixme Need to clarify "should" in "should only be called once".
     */
    protected void set(V v) {
        lock.lock();
        try {
            ready = true;
            result = v;
            runner = null;
            accessible.signalAll();
        }
        finally {
            lock.unlock();
        }
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
        lock.lock();
        try {
            ready = true;
            exception = t;
            runner = null;
            accessible.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    /* Cancellable implementation. */

    public boolean cancel(boolean mayInterruptIfRunning) {
        lock.lock();
        try {
            if (ready || cancelled)
                return false;
            if (mayInterruptIfRunning &&
                runner != null && runner != Thread.currentThread())
                runner.interrupt();
            return cancelled = ready = true;
        }
        finally {
            lock.unlock();
        }
    }

    public boolean isCancelled() {
        lock.lock();
        try {
            return cancelled;
        }
        finally {
            lock.unlock();
        }
    }

    public boolean isDone() {
        lock.lock();
        try {
            return ready;
        }
        finally {
            lock.unlock();
        }
    }
}










