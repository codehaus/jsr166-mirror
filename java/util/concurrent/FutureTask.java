/*
 * @(#)FutureTask.java
 */

package java.util.concurrent;

/**
 * A cancellable asynchronous computation.
 * Provides methods to start and cancel the computation, query to see if
 * the computation is complete, and retrieve the result of the computation.
 * The result can only be retrieved when the computation has completed;
 * the <tt>get</tt> method will block if the computation has not yet completed.
 * Once the computation is completed, the result cannot be changed, nor can the
 * computation be restarted or cancelled.
 *
 * <p>Because <tt>FutureTask</tt> implements <tt>Runnable</tt>, a
 * <tt>FutureTask</tt> can be submitted to an Executor for current or deferred
 * execution.
 *
 * <p>FutureTask can be used to wrap a Callable or Runnable so that it
 * can scheduled for execution in a thread or an Executor, cancel
 * computation before the computation completes, and wait for or
 * retrieve the results.  If the computation threw an exception, the
 * exception is propagated to any thread that attempts to retrieve the
 * result.
 *
 * @see Executor
 * @since 1.5
 * @spec JSR-166
 */
public class FutureTask<V> implements Cancellable, Future<V>, Runnable {

    private V result;
    private Throwable exception;
    private boolean ready;
    private Thread runner;
    private final Callable<V> callable;
    private boolean cancelled;

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
     * given runnable, and arrange that <tt>get</tt> will return the
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
            synchronized(this) {
                if (ready || runner != null)
                    return;
                runner = Thread.currentThread();
            }
            set(callable.call());
        }
        catch(Throwable ex) {
            setException(ex);
        }
    }

    /* Future implementation. INHERIT this javadoc from interface??? Note CancellationException. */

    /**
     * Waits if necessary for the computation to complete, and then retrieve
     * its result.
     *
     * @return value of this task
     * @throws CancellationException if task producing this value was
     * cancelled before completion
     * @throws ExecutionException if the underlying computation threw an exception
     * @throws InterruptedException if current thread was interrupted while waiting
     */
    public synchronized V get() throws InterruptedException, ExecutionException {
        while (!ready)
            wait();
        if (cancelled)
            throw new CancellationException();
        else if (exception != null)
            throw new ExecutionException(exception);
        else
            return result;
    }

    /**
     * Waits if necessary for at most the given time for the computation to
     * complete, and then retrieves the result.
     *
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument
     * @return value of this task
     * @throws CancellationException if task producing this value was cancelled before completion
     * @throws ExecutionException if the underlying computation
     * threw an exception.
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws TimeOutException if the wait timed out
     */
    public synchronized V get(long timeout, TimeUnit granularity)
        throws InterruptedException, ExecutionException {

        if (!ready) {
            long startTime = granularity.now();
            long waitTime = timeout;
            for (;;) {
                granularity.timedWait(this, waitTime);
                if (ready)
                    break;
                else {
                    waitTime = granularity.elapsed(startTime);
                    if (waitTime <= 0)
                        throw new TimeoutException();
                }
            }
        }
        if (cancelled)
            throw new CancellationException();
        else if (exception != null)
            throw new ExecutionException(exception);
        else
            return result;
    }

    /**
     * Sets the value of this task to the given value.  This method
     * should only be called once; once it is called, the computation
     * is assumed to have completed.
     * @@@brian We should guard against changing the value by throwing
     * an exception if the value has already been set!
     * @@joe We should allow set after task has been cancelled.  I think it's
     * OK to allow multiple sets by default.
     *
     * @param v the value
     */
    protected synchronized void set(V v) {
        ready = true;
        result = v;
        runner = null;
        notifyAll();
    }

    /**
     * Indicates that the computation has failed.  After this method
     * is called, the computation is assumed to be completed, and any
     * attempt to retrieve the result will throw an <tt>ExecutionException</tt>
     * wrapping the exception provided here.
     *
     * @param t the throwable
     */
    protected synchronized void setException(Throwable t) {
        ready = true;
        exception = t;
        runner = null;
        notifyAll();
    }

    /* Cancellable implementation. */

    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (ready || cancelled)
            return false;
        if (mayInterruptIfRunning &&
            runner != null && runner != Thread.currentThread())
            runner.interrupt();
        return cancelled = true;
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public synchronized boolean isDone() {
        return ready;
    }
}
