/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
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
     * Special value for "runState" indicating task is cancelled
     */
    private static final Object CANCELLED = new Object();

    /**
     * Special value for "runState" indicating task is completed
     */
    private static final Object DONE = new Object();

    /** 
     * Holds the run-state, taking on values:
     *   null              = not yet started,
     *   [some thread ref] = running,
     *   DONE              = completed normally,
     *   CANCELLED         = cancelled (may or may not have ever run).
     */
    private volatile Object runState;

    /** The underlying callable */
    private final Callable<V> callable;
    /** The result to return from get() */
    private V result;
    /** The exception to throw from get() */
    private Throwable exception;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition accessible = lock.newCondition();

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
    }

    /**
     * Constructs a <tt>FutureTask</tt> that will upon running, execute the
     * given <tt>Runnable</tt>, and arrange that <tt>get</tt> will return the
     * given result on successful completion.
     *
     * @param  runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * <tt>Future&lt;?&gt; f = new FutureTask&lt;Object&gt;(runnable, null)</tt>
     * @throws NullPointerException if runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
    }

    public boolean isCancelled() {
        return runState == CANCELLED;
    }
    
    public boolean isDone() {
        Object r = runState;
        return r == DONE || r == CANCELLED;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        final ReentrantLock lock = this.lock;
        Thread interruptThread = null;
        lock.lock();
        try {
            Object r = runState;
            if (r == DONE || r == CANCELLED)
                return false;
            if (mayInterruptIfRunning && r != null && r instanceof Thread)
                interruptThread = (Thread)r;
            accessible.signalAll();
            runState = CANCELLED;
        }
        finally{
            lock.unlock();
        }
        if (interruptThread != null)
            interruptThread.interrupt();
        done();
        return true;
    }
    
    /**
     * Waits if necessary for execution to complete, and then
     * retrieves its result.
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
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (;;) {
                Object r = runState;
                if (r == CANCELLED)
                    throw new CancellationException();
                if (r == DONE) {
                    if (exception != null)
                        throw new ExecutionException(exception);
                    else
                        return result;
                }
                accessible.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits if necessary for at most the given time for execution to
     * complete, and then retrieves its result, if available.
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
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (;;) {
                Object r = runState;
                if (r == CANCELLED)
                    throw new CancellationException();
                if (r == DONE) {
                    if (exception != null)
                        throw new ExecutionException(exception);
                    else
                        return result;
                }
                if (nanos <= 0)
                    throw new TimeoutException();
                nanos = accessible.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
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
     * Sets the result of this Future to the given value unless
     * this future has been cancelled
     * @param v the value
     */ 
    protected void set(V v) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (runState == CANCELLED) 
                return;
            result = v;
            accessible.signalAll();
            runState = DONE;
        } finally {
            lock.unlock();
        }
        done();
    }

    /**
     * Causes this future to report an <tt>ExecutionException</tt>
     * with the given throwable as its cause, unless this Future has
     * been cancelled.
     * @param t the cause of failure.
     */ 
    protected void setException(Throwable t) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (runState == CANCELLED) 
                return;
            exception = t;
            accessible.signalAll();
            runState = DONE;
        } finally {
            lock.unlock();
        }
        done();
    }
    
    /**
     * Attempts to set the state of this task to Running, succeeding
     * only if the state is currently NOT Done, Running, or Cancelled.
     * @return true if successful
     */ 
   private boolean setRunning() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (runState != null)
                return false;
            runState = Thread.currentThread();
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Resets the run state of this task to its initial state unless
     * it has been cancelled. (Note that a cancelled task cannot be
     * reset.)
     * @return true if successful
     */
    private boolean reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (runState == CANCELLED) 
                return false;
            runState = null;
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Sets this Future to the result of computation unless
     * it has been cancelled.
     */
    public void run() {
        if (setRunning()) {
            try {
                set(callable.call());
            } catch(Throwable ex) {
                setException(ex);
            }
        }
    }

    /**
     * Executes the computation and then resets this Future to initial
     * state; failing to do so if the computation encounters an
     * exception or is cancelled.  This is designed for use with tasks
     * that intrinsically execute more than once.
     * @return true if successfully run and reset
     */
    protected boolean runAndReset() {
        if (setRunning()) {
            try {
                // don't bother to set result; it can't be accessed
                callable.call();
                return reset();
            } catch(Throwable ex) {
                setException(ex);
            } 
        }
        return false;
    }

}

