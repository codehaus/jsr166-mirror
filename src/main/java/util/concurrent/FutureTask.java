/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.locks.*;


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
 * <p>In addition to serving as a standalone class, this class provides
 * <tt>protected</tt> functionality that may be useful when creating
 * customized task classes.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The result type returned by this FutureTask's <tt>get</tt> method
 */
public class FutureTask<V> implements Future<V>, Runnable {
    /**
     * Special value for "runner" indicating task is cancelled
     */
    private static final Object CANCELLED = new Object();

    /**
     * Special value for "runner" indicating task is completed
     */
    private static final Object DONE = new Object();

    /** 
     * Holds the run-state, taking on values:
     *   null              = not yet started,
     *   [some thread ref] = running,
     *   DONE              = completed normally,
     *   CANCELLED         = cancelled (may or may not have ever run).
     */
    private volatile Object runner;

    /*
     * For simplicity of use, we can use either a Runnable or a
     * Callable as basis for run method. So one or the other of these
     * will be null.
     */

    /** The runnable, if non-null, then callable is null */
    final Runnable runnable;
    /** The callable, if non-null, then runnable is null */
    final Callable<V> callable;

    /** The result to return from get() */
    private V result;
    /** The exception to throw from get() */
    private Throwable exception;

    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock.ConditionObject accessible = lock.newCondition();

    /**
     * Constructs a <tt>FutureTask</tt> that will upon running, execute the
     * given <tt>Callable</tt>.
     *
     * @param  callable the callable task
     * @throws NullPointerException if callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;
        this.runnable = null;
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
        if (runnable == null)
            throw new NullPointerException();
        this.runnable = runnable;
        this.result = result;
        this.callable = null;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        lock.lock();
        try {
            Object r = runner;
            if (r == DONE || r == CANCELLED)
                return false;
            if (mayInterruptIfRunning && r != null && r instanceof Thread)
                ((Thread)r).interrupt();
            runner = CANCELLED;
        }
        finally{
            lock.unlock();
        }
        done();
        return true;
    }
    
    public boolean isCancelled() {
        return runner == CANCELLED;
    }
    
    public boolean isDone() {
        Object r = runner;
        return r == DONE || r == CANCELLED;
    }

    /**
     * Waits if necessary for the call to <tt>callable.call</tt> to
     * complete, and then retrieves its result.
     *
     * @return computed result
     * @throws CancellationException if underlying computation was
     * cancelled
     * @throws ExecutionException if underlying computation threw an
     * exception
     * @throws InterruptedException if current thread was interrupted
     * while waiting
     */
    public V get() throws InterruptedException, ExecutionException {
        lock.lock();
        try {
            while (!isDone())
                accessible.await();
            if (isCancelled())
                throw new CancellationException();
            else if (exception != null)
                throw new ExecutionException(exception);
            else
                return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits if necessary for at most the given time for the call to
     * <tt>callable.call</tt> to complete, and then retrieves its
     * result.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return computed result
     * @throws CancellationException if underlying computation was
     * cancelled
     * @throws ExecutionException if underlying computation threw an
     * exception
     * @throws InterruptedException if current thread was interrupted
     * while waiting
     * @throws TimeoutException if the wait timed out
     */
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        lock.lock();
        try {
            if (!isDone()) {
                long nanos = unit.toNanos(timeout);
                do {
                    if (nanos <= 0)
                        throw new TimeoutException();
                    nanos = accessible.awaitNanos(nanos);
                } while (!isDone());
            }
            if (isCancelled())
                throw new CancellationException();
            else if (exception != null)
                throw new ExecutionException(exception);
            else
                return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the result of this Future to the given value.
     * @param v the value
     */ 
    protected void set(V v) {
        lock.lock();
        try {
            result = v;
            setDone();
            accessible.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Causes this future to report an <tt>ExecutionException</tt>
     * with the given throwable as its cause.
     * @param t the cause of failure.
     */ 
    protected void setException(Throwable t) {
        lock.lock();
        try {
            exception = t;
            setDone();
            accessible.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the state of this task to Cancelled.
     */
    protected void setCancelled() {
        lock.lock();
        try {
            runner = CANCELLED;
        }
        finally{
            lock.unlock();
        }
    }
    
    /**
     * Sets the state of this task to Done, unless already in a
     * Cancelled state, in which case Cancelled status is preserved.
     */
    protected void setDone() {
        lock.lock();
        try {
            Object r = runner;
            if (r == DONE || r == CANCELLED) 
                return;
            runner = DONE;
        }
        finally{
            lock.unlock();
        }
        done();
    }

    /**
     * Attempts to set the state of this task to Running, succeeding
     * only if the state is currently NOT Done, Running, or Cancelled.
     * @return true if successful
     */ 
    protected boolean setRunning() {
        lock.lock();
        try {
            if (runner != null)
                return false;
            runner = Thread.currentThread();
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Sets this Future to the results of computation
     */
    public void run() {
        if (setRunning()) {
            try {
                try {
                    if (runnable != null)
                        runnable.run();
                    else if (callable != null)
                        set(callable.call());
                } catch(Throwable ex) {
                    setException(ex);
                }
            } finally {
                setDone();
            }
        }
    }

    /**
     * Protected method invoked when this task transitions to state
     * <tt>isDone</tt> (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() { }

    /**
     * Resets the run state of this task to its initial state unless
     * it has been cancelled. (Note that a cancelled task cannot be
     * reset.)
     * @return true if successful
     */
    protected boolean reset() {
        lock.lock();
        try {
            if (runner == CANCELLED) 
                return false;
            runner = null;
            return true;
        }
        finally {
            lock.unlock();
        }
    }
}










