/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.concurrent.locks.LockSupport;

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
public class FutureTask<V> implements RunnableFuture<V> {
    /*
     * Revision notes: This differs from previous versions of this
     * class that relied on AbstractQueuedSynchronizer, mainly to
     * avoid surprising users about retaining interrupt status during
     * cancellation races. Sync control in the current design relies
     * on a "state" field updated via CAS to track completion, along
     * with a simple Treiber stack to hold waiting threads.
     *
     * Style note: As usual, we bypass overhead of using
     * AtomicXFieldUpdaters and instead directly use Unsafe intrinsics.
     */

    /**
     * The run state of this task, initially 0.  The run state
     * transitions to NORMAL, EXCEPTIONAL, or CANCELLED (only) in
     * method setCompletion. During setCompletion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (while interrupting the runner). State values
     * are ordered and set to powers of two to simplify checks.
     */
    private volatile int state;
    private static final int COMPLETING   = 0x01;
    private static final int INTERRUPTING = 0x02;
    private static final int NORMAL       = 0x04;
    private static final int EXCEPTIONAL  = 0x08;
    private static final int CANCELLED    = 0x10;

    /** The result to return or exception to throw from get() */
    private Object outcome; // non-volatile, protected by state reads/writes
    /** The thread running the callable; CASed during run() */
    private volatile Thread runner;
    /** The underlying callable */
    private final Callable<V> callable;
    /** Treiber stack of waiting threads */
    private volatile WaitNode waiters;

    /**
     * Sets completion status, unless already completed.  If
     * necessary, we first set state to COMPLETING or INTERRUPTING to
     * establish precedence. This intentionally stalls (just via
     * yields) in (uncommon) cases of concurrent calls during
     * cancellation until state is set, to avoid surprising users
     * during cancellation races.
     *
     * @param x the outcome
     * @param mode the completion state value
     * @return true if this call caused transtion from 0 to completed
     */
    private boolean setCompletion(Object x, int mode) {
        Thread r = runner;
        if (r == Thread.currentThread()) // null out runner on completion
            UNSAFE.putObject(this, runnerOffset, r = null); // nonvolatile OK
        int next = ((mode == INTERRUPTING) ? // set up transient states
                    (r != null) ? INTERRUPTING : CANCELLED :
                    (x != null) ? COMPLETING : mode);
        for (int s;;) {
            if ((s = state) == 0) {
                if (UNSAFE.compareAndSwapInt(this, stateOffset, 0, next)) {
                    if (next == INTERRUPTING) {
                        Thread t = runner; // recheck
                        if (t != null)
                            t.interrupt();
                        state = CANCELLED;
                    }
                    else if (next == COMPLETING) {
                        outcome = x;
                        state = mode;
                    }
                    if (waiters != null)
                        releaseAll();
                    done();
                    return true;
                }
            }
            else if (s == INTERRUPTING)
                Thread.yield(); // wait out cancellation
            else
                return false;
        }
    }

    /**
     * Returns result or throws exception for completed task
     * @param s completed state value
     */
    private V report(int s) throws ExecutionException {
        Object x = outcome;
        if (s == NORMAL)
            return (V)x;
        if ((s & (CANCELLED | INTERRUPTING)) != 0)
            throw new CancellationException();
        throw new ExecutionException((Throwable)x);
    }

    /**
     * Creates a <tt>FutureTask</tt> that will, upon running, execute the
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
     * Creates a <tt>FutureTask</tt> that will, upon running, execute the
     * given <tt>Runnable</tt>, and arrange that <tt>get</tt> will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
    }

    public boolean isCancelled() {
        return (state & (CANCELLED | INTERRUPTING)) != 0;
    }

    public boolean isDone() {
        return state != 0;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return state == 0 &&
            setCompletion(null, mayInterruptIfRunning ?
                          INTERRUPTING : CANCELLED);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get() throws InterruptedException, ExecutionException {
        int s;
        return report((s = state) > COMPLETING ? s : awaitDone(false, 0L));
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        int s;
        long nanos = unit.toNanos(timeout);
        if ((s = state) <= COMPLETING &&
            (s = awaitDone(true, nanos)) <= COMPLETING)
            throw new TimeoutException();
        return report(s);
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
     * this future has already been set or has been cancelled.
     * This method is invoked internally by the <tt>run</tt> method
     * upon successful completion of the computation.
     * @param v the value
     */
    protected void set(V v) {
        setCompletion(v, NORMAL);
    }

    /**
     * Causes this future to report an <tt>ExecutionException</tt>
     * with the given throwable as its cause, unless this Future has
     * already been set or has been cancelled.
     * This method is invoked internally by the <tt>run</tt> method
     * upon failure of the computation.
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        setCompletion(t, EXCEPTIONAL);
    }

    public void run() {
        Thread r = Thread.currentThread();
        if (state == 0 &&
            UNSAFE.compareAndSwapObject(this, runnerOffset, null, r)) {
            V result;
            try {
                result = callable.call();
            } catch (Throwable ex) {
                setException(ex);
                return;
            }
            set(result);
        }
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this Future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     * @return true if successfully run and reset
     */
    protected boolean runAndReset() {
        Thread r = Thread.currentThread();
        if (state != 0 ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset, null, r))
            return false;
        try {
            callable.call(); // don't set result
        } catch (Throwable ex) {
            setException(ex);
            return false;
        }
        runner = null;
        for (;;) {
            int s = state;
            if (s == 0)
                return true;
            if (s != INTERRUPTING)
                return false;
            Thread.yield(); // wait out racing cancellation
        }
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack. See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        WaitNode next;
    }

    /**
     * Removes and signals all waiting threads
     */
    private void releaseAll() {
        WaitNode q;
        while ((q = waiters) != null) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        return;
                    q.next = null; // unlink to help gc
                    q = next;
                }
            }
        }
    }

    /**
     * Awaits completion or aborts on interrupt of timeout
     * @param timed true if use timed waits
     * @param nanos time to wait if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
        long last = timed? System.nanoTime() : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (int s;;) {
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }
            else if ((s = state) > COMPLETING) {
                if (q != null)
                    q.thread = null;
                return s;
            }
            else if (q == null)
                q = new WaitNode();
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q.next = waiters, q);
            else if (q.thread == null)
                q.thread = Thread.currentThread();
            else if (timed) {
                long now = System.nanoTime();
                if ((nanos -= (now - last)) <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                last = now;
                LockSupport.parkNanos(this, nanos);
            }
            else
                LockSupport.park(this);
        }
    }

    /**
     * Try to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage. Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers or concurrent calls to removeWaiter.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            WaitNode pred = null;
            WaitNode q = waiters;
            while (q != null) {
                WaitNode next = node.next;
                if (q != node) {
                    pred = q;
                    q = next;
                }
                else if (pred != null) {
                    pred.next = next;
                    break;
                }
                else if (UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q, next))
                    break;
                else { // restart on CAS failure
                    pred = null;
                    q = waiters;
                }
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
