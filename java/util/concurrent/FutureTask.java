package java.util.concurrent;

/**
 * A FutureTask is a runnable, cancellable Future.
 **/
public class FutureTask<V> implements Runnable, Cancellable, Future {

    private V result;
    private Throwable exception;
    private boolean ready;
    private Thread runner;
    private final Callable<V> callable;
    private boolean cancelled;

    /**
     * Construct a FutureTask that will upon running, execute
     * the given function.
     **/
    public FutureTask(Callable<V> callable) {
        this.callable = callable;
    }


    /**
     * Construct a FutureTask that will upon running, execute
     * the given runnable, and arrange that <tt>get</tt> will
     * return the given result on successful completion.
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
     * Return true if the underlying task has completed,
     * either normally or via cancellation,
     **/
    public synchronized boolean isDone() {
        return ready;
    }

    /**
     * Wait if necessary for object to exist, then get it
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
     * Wait if necessary for at most the given time for object to exist,
     * then get it.
     * @param time the maximum time to wait
     * @param granularity the time unit of the time argument
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws TimeOutException if the wait timed out
     * @throws CancellationException if task producing this value was cancelled before completion.
     * @throws ExecutionException if the underlying computation
     * threw an exception.
     **/
    public synchronized V get(long time, Clock granularity)
        throws InterruptedException, ExecutionException {

        if (!ready) {
            long startTime = granularity.currentTime();
            long waitTime = time;
            for (;;) {
                granularity.timedWait(this, waitTime);
                if (ready)
                    break;
                else {
                    waitTime = granularity.currentTime() - startTime;
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
     * Set the value of this Future to the given value.
     **/
    protected synchronized void set(V v) {
        ready = true;
        result = v;
        runner = null;
        notifyAll();
    }

    /**
     * Cause get to throw an ExecutionException wrapping the
     * given exception.
     **/
    protected synchronized void setException(Throwable t) {
        ready = true;
        exception = t;
        runner = null;
        notifyAll();
    }

    /**
     * Execute the callable if not already cancelled or running, and set the
     * value or exception with its results.
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

    public void run() {
        doRun();
    }

}
