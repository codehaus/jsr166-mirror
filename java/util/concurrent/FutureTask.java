package java.util.concurrent;

/**
 * A FutureTask represents the results of a cancellable, asynchronous
 * computation, providing methods to start and cancel the computation,
 * query to see if the computation is complete, and retrieve the
 * result of the computation.  The result can only be retrieved when
 * the computation has completed; the get() method will block if the
 * computation has not yet completed.  Once the computation is
 * completed, the result cannot be changed, nor can the computation be
 * restarted or cancelled.
 *
 * <p>Because FutureTask implements Runnable, a FutureTask can be
 * submitted to an Executor for current or deferred execution.
 *
 * <p>FutureTask can be used to wrap a Callable or Runnable so that it
 * can scheduled for execution in a thread or an Executor, cancel
 * computation before the computation completes, and wait for or
 * retrieve the results.  If the computation threw an exception, the
 * exception is propagated to any thread that attempts to retrieve the
 * result.
 * @see Callable
 * @see Future
 * @see Cancellable
 * @see Executor
 **/
public class FutureTask<V> implements Runnable, Cancellable, Future {

    private V result;
    private Throwable exception;
    private boolean ready;
    private Thread runner;
    private final Callable<V> callable;
    private boolean cancelled;

    /**
     * Construct a FutureTask that will upon running, execute the
     * given Callable.  
     **/
    public FutureTask(Callable<V> callable) {
        this.callable = callable;
    }


    /**
     * Construct a FutureTask that will upon running, execute the
     * given runnable, and arrange that <tt>get</tt> will return the
     * given result on successful completion.
     * @param  runnable the runnable task.
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider just using
     * <tt>Boolean.TRUE</tt>.
     **/
    public FutureTask(final Runnable runnable, final V result) {
        callable = new Callable<V>() {
            public V call() {
                runnable.run();
                return result;
            }
        };
    }

    /**
     * Return true if the underlying task has completed, either
     * normally or via cancellation.  
     **/
    public synchronized boolean isDone() {
        return ready;
    }

    /**
     * Wait if necessary for the computation to complete, and then retrieve
     * its result.  
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws CancellationException if task producing this value was cancelled before completion.
     * @throws ExecutionException if the underlying computation
     * threw an exception.
     **/
    public synchronized V get() throws InterruptedException, ExecutionException {
        while (!ready)
            wait();
        if (exception != null)
            throw new ExecutionException(exception);
        else
            return result;
    }

    /**
     * Wait if necessary for at most the given time for the computation to
     * complete, and then retrieve the result
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws TimeOutException if the wait timed out
     * @throws CancellationException if task producing this value was cancelled before completion.
     * @throws ExecutionException if the underlying computation
     * threw an exception.
     **/
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
        if (exception != null)
            throw new ExecutionException(exception);
        else
            return result;
    }


    /**
     * Cancel computation of this future if it has not already completed.
     * If the computation has not started when cancel() is called, the 
     * computation will be cancelled.  If it has already started, then 
     * whether or not the computation is cancelled depends on the value of
     * the interruptIfRunning argument.  
     * @param interruptIfRunning true if the execution of the run method
     * computing this value should be interrupted. Otherwise,
     * in-progress executions are allowed to complete.
     * @return true unless the task has already completed or already
     * been cancelled.  
     **/
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (ready || cancelled)
            return false;
        if (mayInterruptIfRunning &&
        runner != null &&
        runner != Thread.currentThread())
            runner.interrupt();
        return cancelled = true;
    }

    /**
     * Return true if this task has been cancelled before completion.
     **/
    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /**
     * Set the value of this Future to the given value.  This method
     * should only be called once; once it is called, the computation
     * is assumed to have completed.  
     * @@@brian We should guard against changing the value by throwing
     * an exception if the value has already been set!
     **/
    protected synchronized void set(V v) {
        ready = true;
        result = v;
        runner = null;
        notifyAll();
    }

    /**
     * Indicate that the computation has failed.  After setException
     * is called, the computation is assumed to be completed, and any
     * attempt to retrieve the result will throw an ExecutionException
     * wrapping the exception provided here.
     **/
    protected synchronized void setException(Throwable t) {
        ready = true;
        exception = t;
        runner = null;
        notifyAll();
    }

    /**
     * Execute the callable if not already cancelled or running, and
     * set the value or exception with its results.
     **/
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

    /** Start the computation */
    public void run() {
        doRun();
    }

}
