/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Base class for cancellable actions running in the Executor
 * framework. In addition to serving as a standalone class, this
 * provides <tt>protected</tt> functionality that may be useful when
 * creating customized task classes.
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
     */
    public CancellableTask(Runnable r) {
        this.runnable = r;
    }

    /**
     * Creates a new CancellableTask without a runnable action, which
     * must be set using <tt>setRunnable</tt> before use.  This is
     * intended for use in subclasses that must complete superclass
     * construction beofre establishing the runnable action.
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
     */
    protected Runnable getRunnable() {
        return runnable;
    }

    /**
     * Set the Runnable forming the basis of this task.
     * @param r the runnable action
     */
    protected void setRunnable(Runnable r) {
        runnable = r;
    }

    /**
     * Set the state of this task to Cancelled
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

    public void run() {
        if (setRunning()) {
            try {
                runnable.run();
            }
            finally {
                setDone();
            }
        }
    }

    /**
     * Implementation of Future methods under the control of a current
     * CancellableTask. This is split into an inner class to permit
     * Future support to be mixed-in with other flavors of tasks.
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

        public boolean isDone() {
            return CancellableTask.this.isDone();
        }

        public void run() {
            try {
                set(callable.call());
            }
            catch(Throwable ex) {
                setException(ex);
            }
        }

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
            }
            finally {
                lock.unlock();
            }
        }

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
            }
            finally {
                lock.unlock();
            }
        }

        protected void set(V v) {
            lock.lock();
            try {
                result = v;
                setDone();
                accessible.signalAll();
            }
            finally {
                lock.unlock();
            }
        }

        protected void setException(Throwable t) {
            lock.lock();
            try {
                exception = t;
                setDone();
                accessible.signalAll();
            }
            finally {
                lock.unlock();
            }
        }
    }
   
}
