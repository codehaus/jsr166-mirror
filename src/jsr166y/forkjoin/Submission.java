/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * Adapter class to allow tasks submitted to a pool to act as Futures.
 * This entails three kinds of adaptation:
 *
 * (1) Unlike internal fork/join processing, get() must block, not
 * help out processing tasks. We use a simpler variant of the
 * mechanics used in FutureTask
 *
 * (2) Regular Futures encase RuntimeExceptions within
 * ExecutionExeptions, while internal tasks just throw them directly,
 * so these must be trapped and wrapped.
 *
 * (3) External submissions are tracked for the sake of managing
 * worker threads. The pool submissionStarting and submissionCompleted
 * methods perform the associated bookkeeping.
 */
final class Submission<T> extends RecursiveTask<T> implements Future<T> {
    
    // status values for sync. We need to keep track of RUNNING status
    // just to make sure callbacks to pool are balanced.
    static final int INITIAL = 0;
    static final int RUNNING = 1;
    static final int DONE    = 2;

    /**
     * Stripped-down variant of FutureTask.sync
     */
    static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 4982264981922014374L;

        public int tryAcquireShared(int acquires) {
            return getState() == DONE? 1 : -1;
        }

        public boolean tryReleaseShared(int releases) { return true; }

        public boolean isDone() { return getState() == DONE; }

        public boolean transitionToRunning() {
            return compareAndSetState(INITIAL, RUNNING);
        }

        /** Set status to DONE; return old state */
        public int transitionToDone() {
            for (;;) {
                int c = getState();
                if (c == DONE || compareAndSetState(c, DONE)) {
                    releaseShared(0);
                    return c;
                }
            }
        }

    }

    private final ForkJoinTask<T> task;
    private final ForkJoinPool pool;
    private final Sync sync;

    Submission(ForkJoinTask<T> t, ForkJoinPool p) {
        t.setStolen(); // All submitted tasks treated as stolen
        task = t;
        pool = p;
        sync = new Sync();
    }
    
    protected T compute() {
        try {
            T result = null;
            if (sync.transitionToRunning()) {
                pool.submissionStarting();
                result = task.invoke();
            } // else was cancelled, so result doesn't matter
            return result; 
        } finally {
            if (sync.transitionToDone() == RUNNING)
                pool.submissionCompleted();
        }
    }
   

    /**
     * ForkJoinTask version of cancel
     */
    public void cancel() {
        try {
            // Don't bother trying to cancel if already done
            if (getException() == null && !sync.isDone()) {
                // avoid recursive call to cancel
                setDoneExceptionally(new CancellationException());
                task.cancel();
            }
        } finally {
            if (sync.transitionToDone() == RUNNING)
                pool.submissionCompleted();
        }
    }

    /**
     * Future version of cancel
     */
    public boolean cancel(boolean ignore) {
        this.cancel();
        return isCancelled();
    }

    public T get() throws InterruptedException, ExecutionException {
        sync.acquireSharedInterruptibly(1);
        return task.reportAsFutureResult();
    }

    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        if (!sync.tryAcquireSharedNanos(1, unit.toNanos(timeout)))
            throw new TimeoutException();
        return task.reportAsFutureResult();
    }

    /**
     * Interrupt-less get for ForkJoinPool.invoke
     */
    public T awaitInvoke() {
        sync.acquireShared(1);
        return task.reportAsForkJoinResult();
    }
}


