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
 * help out processing tasks. We use a ReentrantLock condition
 * to arrange this.
 *
 * (2) Regular Futures encase RuntimeExceptions within
 * ExecutionExeptions, while internal tasks just throw them
 * directly, so these must be trapped and wrapped.
 *
 * (3) External submissions are tracked for the sake of managing
 * worker threads. The pool submissionStarting and submissionCompleted
 * methods perform the associated bookkeeping.
 */
final class Submission<T> extends RecursiveTask<T> implements Future<T> {
    private final ForkJoinTask<T> task;
    private final ForkJoinPool pool;
    private final ReentrantLock lock;
    private final Condition ready;
    private boolean started;

    Submission(ForkJoinTask<T> t, ForkJoinPool p) {
        t.setStolen(); // All submitted tasks treated as stolen
        task = t;
        pool = p;
        lock = new ReentrantLock();
        ready = lock.newCondition();
    }

    protected T compute() {
        try {
            if (tryStart())
                return task.invoke();
            else {
                setDoneExceptionally(new CancellationException());
                return null; // will be trapped on get
            }
        } finally {
            complete();
        }
    }

    private boolean tryStart() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return !started && (started = pool.submissionStarting());
        } finally {
            lock.unlock();
        }
    }

    private void complete() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (started)
                pool.submissionCompleted();
            ready.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * ForkJoinTask version of cancel
     */
    public void cancel() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            task.cancel();
            // avoid recursive call to cancel
            setDoneExceptionally(new CancellationException());
            if (started)
                pool.submissionCompleted();
            ready.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Future version of cancel
     */
    public boolean cancel(boolean ignore) {
        this.cancel();
        return isCancelled();
    }

    /**
     * Return result or throw exception using Future conventions
     */
    static <T> T futureResult(ForkJoinTask<T> t)
        throws ExecutionException {
        Throwable ex = t.getException();
        if (ex != null) {
            if (ex instanceof CancellationException)
                throw (CancellationException)ex;
            else
                throw new ExecutionException(ex);
        }
        return t.getResult();
    }

    public T get() throws InterruptedException, ExecutionException {
        final ForkJoinTask<T> t = this.task;
        if (!t.isDone()) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                while (!t.isDone())
                    ready.await();
            } finally {
                lock.unlock();
            }
        }
        return futureResult(t);
    }

    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        final ForkJoinTask<T> t = this.task;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            long nanos = unit.toNanos(timeout);
            while (!t.isDone()) {
                if (nanos <= 0)
                    throw new TimeoutException();
                else
                    nanos = ready.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
        }
        return futureResult(t);
    }

    /**
     * Interrupt-less get for ForkJoinPool.invoke
     */
    public T awaitInvoke() {
        final ForkJoinTask<T> t = this.task;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            while (!t.isDone())
                ready.awaitUninterruptibly();
        } finally {
            lock.unlock();
        }
        Throwable ex = t.getException();
        if (ex != null)
            ForkJoinTask.rethrowException(ex);
        return t.getResult();
    }
}


