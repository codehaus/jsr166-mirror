/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Base class for {@link Cancellable} {@link java.lang.Runnable} 
 * actions within the {@link Executor} framework. In addition to
 * serving as a standalone class, this provides <tt>protected</tt>
 * functionality that may be useful when creating customized task
 * classes.
 * @since 1.5
 * @author Doug Lea
 */

public class CancellableTask implements Cancellable, Runnable {
    /** 
     * Holds the run-state, taking on values:
     *   null              = not yet started,
     *   [some thread ref] = running,
     *   DONE              = completed normally,
     *   CANCELLED         = cancelled (may or may not have ever run).
     * Transitions use atomic updates.
     */
    private volatile Object runner;

    /**
     * Special value for "runner" indicating task is completed
     */
    private static final Object DONE = new Object();

    /**
     * Special value for "runner" indicating task is cancelled
     */
    private static final Object CANCELLED = new Object();

    private static AtomicReferenceFieldUpdater<CancellableTask, Object> 
        runnerUpdater = 
        AtomicReferenceFieldUpdater.newUpdater
        (CancellableTask.class, Object.class, "runner");

    /**
     * The runnable underlying this task
     */
    private volatile Runnable runnable;

    /**
     * Creates a new CancellableTask which invokes the given
     * <tt>Runnable</tt> when executed.
     * @param r the runnable action
     * @throws NullPointerException if runnable is null
     */
    public CancellableTask(Runnable r) {
        if (r == null)
            throw new NullPointerException();
        this.runnable = r;
    }

    /**
     * Creates a new CancellableTask without a runnable action, which
     * must be set using <tt>setRunnable</tt> before use.  This is
     * intended for use in subclasses that must complete superclass
     * construction before establishing the runnable action.
     */
    protected CancellableTask() {
    }


    public boolean cancel(boolean mayInterruptIfRunning) {
        Object r = runner;
        if (r == DONE || r == CANCELLED)
            return false;

        if (mayInterruptIfRunning &&
            r != null &&
            r instanceof Thread &&
            runnerUpdater.compareAndSet(this, r, CANCELLED)) 

            ((Thread)r).interrupt();
        else 
            runnerUpdater.set(this, CANCELLED);
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
     * Return the Runnable forming the basis of this task.
     * @return the runnable action
     * @see #setRunnable
     */
    protected Runnable getRunnable() {
        return runnable;
    }

    /**
     * Set the Runnable forming the basis of this task.
     * @param r the runnable action
     * @see #getRunnable
     */
    protected void setRunnable(Runnable r) {
        runnable = r;
    }

    /**
     * Set the state of this task to Cancelled.
     */
    protected void setCancelled() {
        runnerUpdater.set(this, CANCELLED);
    }
    
    /**
     * Set the state of this task to Done, unless already
     * in a Cancelled state, in which Cancelled status is preserved.
     *
     */
    protected void setDone() {
        for (;;) {
            Object r = runner;
            if (r == DONE || r == CANCELLED) 
                return;
            if (runnerUpdater.compareAndSet(this, r, DONE))
                return;
        }
    }

    /**
     * Attempt to set the state of this task to Running, succeeding
     * only if the state is currently NOT Done, Running, or Cancelled.
     * @return true if successful
     */ 
    protected boolean setRunning() {
        return runnerUpdater.compareAndSet(this, null, Thread.currentThread());
    }

    /**
     * Runs the runnable if not cancelled, maintaining run status.
     */
    public void run() {
        if (setRunning()) {
            try {
                runnable.run();
            } finally {
                setDone();
            }
        }
    }

    /**
     * Reset the run state of this task to its initial state unless
     * it has been cancelled. (Note that a cancelled task cannot be
     * reset.)
     * @return true if successful
     */
    protected boolean reset() {
        for (;;) {
            Object r = runner;
            if (r == CANCELLED) 
                return false;
            if (runnerUpdater.compareAndSet(this, r, null))
                return true;
        }
    }

    /**
     * Implementation of Future methods under the control of a current
     * <tt>CancellableTask</tt>, which it relies on for methods
     * <tt>isDone</tt>, <tt>isCancelled</tt> and <tt>cancel</tt>. This
     * class is split into an inner class to permit Future support to
     * be mixed-in with other flavors of tasks.  Normally, such a
     * class will delegate <tt>Future</tt> <tt>get</tt> methods to the
     * <tt>InnerCancellableFuture</tt>, and internally arrange that
     * <tt>set</tt> methods be invoked when computations are ready.
     *
     * <p><b>Sample Usage</b>. Here are fragments of an example subclass.
     * <pre>
     *  class MyFutureTask&lt;V&gt; extends CancellableTask implements Future&lt;V&gt; {
     *                
     *    MyFutureTask(Callable&lt;V&gt; callable) {
     *      setRunnable(new InnerCancellableFuture&lt;V&gt;(callable));
     *    }
     *
     *    public V get() throws InterruptedException, ExecutionException {
     *      return ((InnerCancellableFuture&lt;V&gt;)getRunnable()).get();
     *    }
     *    // (And similarly for timeout version.)
     *
     *    void action() { // whatever action causes execution
     *      try {
     *        ((InnerCancellableFuture&lt;V&gt;)getRunnable()).set(compute());
     *      } catch (Exception ex) {
     *        ((InnerCancellableFuture&lt;V&gt;)getRunnable()).setException(ex);
     *      }
     *   }
     * }
     *</pre>
     */
    protected class InnerCancellableFuture<V> implements Future<V>, Runnable {
        private final Callable<V> callable;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition accessible = lock.newCondition();
        private V result;
        private Throwable exception;

        /**
         * Create an InnerCancellableFuture that will execute the
         * given callable.
         * @param callable the function to execute
         */
        protected InnerCancellableFuture(Callable<V> callable) {
            this.callable = callable;
        }
        
        public boolean cancel(boolean mayInterruptIfRunning) {
            return CancellableTask.this.cancel(mayInterruptIfRunning);
        }
        
        public boolean isCancelled() {
            return CancellableTask.this.isCancelled();
        }


        public boolean isDone() {
            return CancellableTask.this.isDone();
        }


        /**
         * Sets this Future to the results of <tt>callable.call</tt>
         */
        public void run() {
            try {
                set(callable.call());
            } catch(Throwable ex) {
                setException(ex);
            }
        }

        /**
         * Waits if necessary for the call to <tt>callable.call</tt> to
         * complete, and then retrieves its result.
         *
         * @return computed result
         * @throws CancellationException here???
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
    }
   
}
