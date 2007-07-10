/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

/**
 * The host and external interface for ForkJoinTasks. A ForkJoinPool
 * manages a group of specialized worker threads that perform
 * ForkJoinTasks. It also provides the entry point for tasks submitted
 * from non-ForkJoinTasks, as well as other management and monitoring
 * operations.
 *
 * <p> Class ForkJoinPool does not implement the ExecutorService
 * interface because it only executes ForkJoinTasks, not arbitrary
 * Runnables. However, for the sake of uniformity, it supports
 * ExecutorService lifecycle control methods (such as shutdown).
 */
public class ForkJoinPool {

    /*
     * This is an overhauled version of the framework described in "A
     * Java Fork/Join Framework" by Doug Lea, in, Proceedings, ACM
     * JavaGrande Conference, June 2000
     * (http://gee.cs.oswego.edu/dl/papers/fj.pdf). It retains most of
     * the basic structure, but includes a number of algorithmic
     * improvements, along with integration with other
     * java.util.concurrent components.
     */

    /**
     * Generator for assigning sequence numbers as thread names.
     */
    static final AtomicInteger poolNumberGenerator = new AtomicInteger();

    /**
     * Array holding all worker threads in the pool. Acts basically as
     * a CopyOnWriteArrayList, but hand-crafted to allow in-place
     * replacements and to maintain thread to index mappings. All uses
     * of this array should first assign as local, and must screen out
     * nulls.
     */
    volatile ForkJoinWorkerThread[] workers;

    /**
     * External submission queue. "Submissions" are tasks scheduled
     * via submit or invoke, not internally generated within
     * ForkJoinTasks.
     */
    final SubmissionQueue submissions;

    /**
     * Main lock protecting access to threads and run state. You might
     * think that having a single lock and condition here wouldn't
     * work so well. But serializing the starting and stopping of
     * worker threads (its main purpose) helps enough in controlling
     * startup effects, contention vs dynamic compilation, etc to be
     * better than alternatives.
     */
    final ReentrantLock lock;

    /**
     * Condition for awaitTermination. Triggered when
     * runningWorkers reaches zero.
     */
    final Condition termination;

    /**
     * Condition triggered when new submission is available so workers
     * should awaken if blocked.
     */
    final Condition workAvailable;

    /**
     * Condition serving same role as workAvailable, but used only for
     * timed waits when work is known to be available but repeated
     * steal attempts fail.  Separating these minimizes unproductive
     * wakeups of one vs the other kinds of waits.
     */
    final Condition idleSleep;

    /**
     * Tracks whether pool is running, shutdown, etc. Modified
     * only under lock, but volatile to allow concurrent reads.
     */
    volatile int runState;

    // Values for runState
    static final int RUNNING    = 0;
    static final int SHUTDOWN   = 1;
    static final int STOP       = 2;
    static final int TERMINATED = 3;

    /**
     * The number of submissions that are currently executing in the pool.
     * This does not include those submissions that are still in job queue
     * waiting to be taken by workers.
     */
    int runningSubmissions;

    /**
     * Maximum number of active submissions allowed to run;
     */
    int maxRunningSubmissions;

    /**
     * Number of workers that are (probably) executing tasks.
     * Incremented when a worker gets a task to run, and decremented
     * when worker has no tasks and cannot find any to steal from
     * other workers.
     */
    volatile int activeWorkers;

    /**
     * Updater to CAS activeWorkers
     */
    static final AtomicIntegerFieldUpdater<ForkJoinPool> activeWorkersUpdater =
        AtomicIntegerFieldUpdater.newUpdater(ForkJoinPool.class, "activeWorkers");
    

    /** 
     * The number of workers that have not yet terminated 
     */
    int runningWorkers;

    /**
     * Number of workers that are (probably) sleeping.  Incremented
     * upon each sleep, but not decremented or cleared until explicit
     * wakeup.  So the actual number of sleepers is at most this
     * value, which is checked against work condition count (or zeroed
     * on signalAll) to correct value during wakeups.
     */
    volatile int sleepers;

    /**
     * The uncaught exception handler used when any worker
     * abrupty terminates
     */
    volatile Thread.UncaughtExceptionHandler ueh;

    /**
     * True if dying workers should be replaced.
     */
    boolean continueOnError;

    /**
     * Pool number, just for assigning names
     */
    final int poolNumber;

    /**
     * The number of times to scan for tasks before
     * yileding/sleeping (and thereafter, between sleeps).  Must
     * be a power of two minus 1. Using short sleeps during times
     * when tasks should be available but aren't makes these
     * threads cope better with lags due to GC, dynamic
     * compilation, queue resizing, and multitasking.
     */
    static final int SCANS_PER_SLEEP = (1 << 10) - 1;

    /**
     * The amount of time to sleep per empty scan. Sleep durations
     * increase only arithmetically, as a compromise between
     * responsiveness and good citizenship.  The value here
     * arranges that first sleep is approximately the smallest
     * value worth context switching out for on typical platforms.
     */
    static final long SLEEP_NANOS_PER_SCAN = (1 << 16);

    /**
     * Returns true if a given idleCount value indicates that its
     * corresponding worker should enter a sleep on failure to get a
     * new task.
     */
    static boolean shouldSleep(int scans) {
        return (scans & SCANS_PER_SLEEP) == SCANS_PER_SLEEP;
    }

    /**
     * Return a useful name for a worker thread
     */
    private String workerName(int i) {
        return "ForkJoinPool-" + poolNumber + "-worker-" + i;
    }

    /**
     * Creates a ForkJoinPool with a pool size equal to the number of
     * processors available on the system.
     */
    public ForkJoinPool() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a ForkJoinPool with the indicated number of Worker
     * threads. You can also add and remove threads while the pool is
     * running, it is generally more efficient and leads to more
     * predictable performance to initialize the pool with a
     * sufficient number of threads to support the desired concurrency
     * level and leave this value fixed.
     * @param poolSize the number of worker threads
     * @throws IllegalArgumentException if poolSize less than or
     * equal to zero
     */
    public ForkJoinPool(int poolSize) {
        if (poolSize <= 0) 
            throw new IllegalArgumentException();
        poolNumber = poolNumberGenerator.incrementAndGet();
        maxRunningSubmissions = poolSize;
        lock = new ReentrantLock();
        workAvailable = lock.newCondition();
        idleSleep = lock.newCondition();
        termination = lock.newCondition();
        submissions = new SubmissionQueue();
        ForkJoinWorkerThread[] ws = new ForkJoinWorkerThread[poolSize];
        for (int i = 0; i < ws.length; ++i)
            ws[i] = new ForkJoinWorkerThread(this, i, workerName(i));
        workers = ws;
        startWorkers(ws);
    }

    /**
     * Start all workers in the given array.
     */
    private void startWorkers(ForkJoinWorkerThread[] ws) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (int i = 0; i < ws.length; ++i) {
                ForkJoinWorkerThread t = ws[i];
                if (t != null) {
                    t.start();
                    ++runningWorkers;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds a new worker thread to the pool. This method may be used
     * to increase the amount of parallelism available to tasks.
     * @return the index of the added worker thread.
     * @throws IllegalStateException if pool is terminating or
     * terminated
     */
    public int addWorker() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (runState >= STOP)
                throw new IllegalStateException();
            ForkJoinWorkerThread[] ws = workers;
            int len = ws.length;
            ForkJoinWorkerThread[] nws = new ForkJoinWorkerThread[len+1];
            System.arraycopy(ws, 0, nws, 0, len);
            ForkJoinWorkerThread w =
                new ForkJoinWorkerThread(this, len, workerName(len));
            if (ueh != null)
                w.setUncaughtExceptionHandler(ueh);
            nws[len] = w;
            new ForkJoinWorkerThread(this, len, workerName(len));
            workers = nws;
            w.start();
            ++runningWorkers;
            return len;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a worker thread from the pool. The worker will
     * exit the next time it is idle. This method may be used
     * to decrease the amount of parallelism available to tasks.
     * @return the index of the removed worker thread.
     * @throws IllegalStateException if pool is terminating or
     * terminated or the poolSize would become zero.
     */
    public int removeWorker() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            int newlen = ws.length - 1;
            if (newlen <= 0 || runState >= STOP)
                throw new IllegalStateException();
            ForkJoinWorkerThread w = ws[newlen];
            ForkJoinWorkerThread[] nws = new ForkJoinWorkerThread[newlen];
            System.arraycopy(ws, 0, nws, 0, newlen);
            workers = nws;
            if (w != null) 
                w.shouldStop();
            return newlen;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Signal blocked/sleeping workers
     */
    final void signalWork() {
        if (sleepers != 0) {
            sleepers = 0;
            idleSleep.signalAll();
        }
        workAvailable.signalAll();
    }

    /**
     * Performs the given task; returning its result upon completion
     * @param task the task
     * @return the task's result
     * @throws NullPointerException if task is null
     */
    public <T> T invoke(ForkJoinTask<T> task) {
        if (task == null)
            throw new NullPointerException();
        Submission<T> job = new Submission<T>(task, this);
        addSubmission(job);
        return job.awaitInvoke();
    }

    /**
     * Arranges for (asynchronous) execution of the given task.
     * @param task the task
     * @return a Future that can be used to get the task's results.
     * @throws NullPointerException if task is null
     */
    public <T> Future<T> submit(ForkJoinTask<T> task) {
        if (task == null)
            throw new NullPointerException();
        Submission<T> job = new Submission<T>(task, this);
        addSubmission(job);
        return job;
    }

    /**
     * Returns the policy for whether to continue execution when a
     * worker thread aborts due to Error. If false, the pool shuts
     * down upon error. The default is false.  If true, dying workers
     * are replaced with fresh threads with empty work queues.
     * @return true if pool should continue when one thread aborts
     */
    public boolean getContinueOnErrorPolicy() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return continueOnError;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the policy for whether to continue execution
     * when a worker thread aborts due to Error. If false,
     * the pool shots down upon error.
     * @param shouldContinue true if the pool should continue
     */
    public void setContinueOnErrorPolicy(boolean shouldContinue) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            continueOnError = shouldContinue;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the handler for internal worker threads that terminate due
     * to uncaught Errors or other unchecked Throwables encountered
     * while executing tasks. Since such errors are not in generable
     * recoverable, they are not managed by ForkJoinTasks themselves,
     * but instead cause threads to die, invoking the handler, and
     * then acting in accord with the given error policy. Because
     * aborted threads can cause tasks to never properly be joined, it
     * may be in some cases be preferable to install a handler to
     * terminate the program or perform other program-wide recovery.
     * Unless set, the current default or ThreadGroup handler is used
     * as handler.
     * @param h the new handler
     * @return the old handler, or null if none
     */
    public Thread.UncaughtExceptionHandler
        setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler h) {

        final ReentrantLock lock = this.lock;
        Thread.UncaughtExceptionHandler old = null;
        lock.lock();
        try {
            old = ueh;
            ueh = h;
            ForkJoinWorkerThread[] ws = workers;
            for (int i = 0; i < ws.length; ++i) {
                ForkJoinWorkerThread w = ws[i];
                if (w != null)
                    w.setUncaughtExceptionHandler(h);
            }
        } finally {
            lock.unlock();
        }
        return old;
    }

    /**
     * Returns the number of worker threads in this pool.  If
     * <tt>removeWorker</tt> has been called but the associated
     * removed workers have not reached an idle state, there may
     * transiently be more workers executing than indicated by this
     * method.
     *
     * @return the number of worker threads in this pool
     */
    public int getPoolSize() {
        return workers.length;
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     */
    public void shutdown() {
        // todo security checks??
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (runState < SHUTDOWN) {
                if (runningSubmissions > 0 || !submissions.isEmpty())
                    runState = SHUTDOWN;
                else
                    terminate();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Attempts to stop all actively executing tasks, and halts the
     * processing of waiting tasks.
     */
    public void shutdownNow() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            terminate();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns <tt>true</tt> if this pool has been shut down.
     *
     * @return <tt>true</tt> if this pool has been shut down
     */
    public boolean isShutdown() {
        return runState >= SHUTDOWN;
    }

    /**
     * Returns <tt>true</tt> if all tasks have completed following shut down.
     * Note that <tt>isTerminated</tt> is never <tt>true</tt> unless
     * either <tt>shutdown</tt> or <tt>shutdownNow</tt> was called first.
     *
     * @return <tt>true</tt> if all tasks have completed following shut down
     */
    public boolean isTerminated() {
        return runState == TERMINATED;
    }

    /**
     * Returns <tt>true</tt> if termination has commenced but has
     * not yet completed.
     *
     * @return <tt>true</tt> if in the process of terminating
     */
    public boolean isTerminating() {
        return runState == STOP;
    }

    /**
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is
     * interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return <tt>true</tt> if this executor terminated and
     *         <tt>false</tt> if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (;;) {
                if (runState == TERMINATED)
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            lock.unlock();
        }
    }

    final void terminate() { // called only under lock
        if (runState < STOP) {
            runState = STOP;
            cancelQueuedSubmissions();
            ForkJoinWorkerThread[] ws = workers;
            stopAllWorkers(ws);
            doCancelQueuedWorkerTasks(ws);
            interruptAllWorkers(ws);
            signalWork();
        }
    }

    final void cancelQueuedSubmissions() {
        Submission<?> task;
        while ((task = submissions.poll()) != null)
            task.cancel(false);
    }

    /**
     * Cancels all tasks that are currently held in any worker
     * thread's local queues. This method may be useful for bulk
     * cancellation of a set of tasks that may terminate when any one
     * of them finds a solution. However, this applies only if you are
     * sure that no other tasks have been submitted to, or are active
     * in, the pool. See {@link
     * ForkJoinPool#setMaximumActiveSubmissionCount}.
     */
    public void cancelQueuedWorkerTasks() {
        doCancelQueuedWorkerTasks(workers);
    }
    
    final void doCancelQueuedWorkerTasks(ForkJoinWorkerThread[] ws) {
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                t.cancelTasks();
        }
    }

    final void interruptAllWorkers(ForkJoinWorkerThread[] ws) {
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                t.interrupt();
        }
    }

    final void stopAllWorkers(ForkJoinWorkerThread[] ws) {
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                t.shouldStop();
        }
    }

    /**
     * Returns the total number of tasks stolen from one thread's work
     * queue by another. This value is only an approximation,
     * obtained by iterating across all threads in the pool, but may
     * be useful for monitoring and tuning fork/join programs.
     * @return the number of steals.
     */
    public long getStealCount() {
        long sum = 0;
        ForkJoinWorkerThread[] ws = workers;
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                sum += t.stealCount;
        }
        return sum;
    }

    /**
     * Returns the number of threads that are not currently idle
     * waiting for tasks.
     * @return the number of active threads.
     */
    public int getActiveThreadCount() {
        return activeWorkers;
    }

    /**
     * Returns the total number of tasks currently held in queues by
     * worker threads (but not including tasks submitted to the pool
     * that have not begun executing). This value is only an
     * approximation, obtained by iterating across all threads in the
     * pool.
     * @return the number of tasks.
     */
    public long getTotalPerThreadQueueSize() {
        long count = 0;
        ForkJoinWorkerThread[] ws = workers;
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                count += t.getQueueSize();
        }
        return count;
    }

    /**
     * Returns the number of tasks that have been submitted (via
     * <tt>submit</tt> or <tt>invoke</tt>) but not have not yet begun
     * execution.
     * @return the number of tasks.
     */
    public int getQueuedSubmissionCount() {
        lock.lock();
        try {
            return submissions.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of tasks that have been submitted (via
     * <tt>submit</tt> or <tt>invoke</tt>) and are currently executing
     * in the pool.
     * @return the number of tasks.
     */
    public int getActiveSubmissionCount() {
        lock.lock();
        try {
            return runningSubmissions;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the maximum number of submitted tasks that are allowed
     * to concurrently execute. By default, the value is equal to
     * the pool size.
     * @return the maximum number
     */
    public int getMaximumActiveSubmissionCount() {
        lock.lock();
        try {
            return maxRunningSubmissions;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the maximum number of submitted tasks that are allowed to
     * concurrently execute. Restricting this value may lessen
     * interference among tasks. Restricting the value to one ensures
     * that submissions execute sequentially.
     * @param max the maximum
     * @throws IllegalArgumentException if max is not positive
     */
    public void setMaximumActiveSubmissionCount(int max) {
        if (max <= 0)
            throw new IllegalArgumentException("max must be positive");
        lock.lock();
        try {
            maxRunningSubmissions = max;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns true if all worker threads are currently idle. An idle
     * worker is one that cannot obtain a task to execute.  This
     * method is conservative: It might not return true immediately
     * upon idleness of all threads, but will eventually become true
     * if no threads become active.
     * @return true is all threads are currently idle
     */
    public boolean isQuiescent() {
        return activeWorkers == 0;
    }

    // Internal methods that may be invoked by workers

    /**
     * Callback when worker becomes active
     */
    final void workerActive() {
        activeWorkersUpdater.incrementAndGet(this);
    }

    /**
     * Callback when worker becomes inactive
     */
    final void workerIdle() {
        activeWorkersUpdater.decrementAndGet(this);
    }

    final ForkJoinWorkerThread[] getWorkers() {
        return workers;
    }

    /**
     * Completion callback from externally submitted job.
     */
    final void submissionCompleted() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (--runningSubmissions <= 0) {
                if (runState >= SHUTDOWN && submissions.isEmpty())
                    terminate();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Enqueue an externally submitted task
     */
    final void addSubmission(Submission<?> job) {
        final ReentrantLock lock = this.lock;
        boolean ok;
        lock.lock();
        try {
            if (ok = (runState == RUNNING)) {
                submissions.add(job);
                signalWork();
            }
        } finally {
            lock.unlock();
        }
        if (!ok)
            throw new RejectedExecutionException();
    }

    /**!
     * Returns a job to run, or null if none available. Blocks if
     * there are no available submissions. Also sets caller to sleep if
     * repeatedly called when there are active submissions but no available
     * work, which is generally caused by insufficient task
     * parallelism for the given pool size, or transiently due to GC
     * etc.
     * @param w calling worker
     */
    final ForkJoinTask<?> takeSubmission(ForkJoinWorkerThread w) {
        ForkJoinTask<?> task = null;
        final ReentrantLock lock = this.lock;
        if (lock.tryLock()) { // skip on lock contention
            try {
                if (runState < STOP) {
                    if (runningSubmissions < maxRunningSubmissions)
                        task = submissions.poll();
                    if (task == null) {
                        boolean blocked;
                        if (runningSubmissions == 0) {
                            blocked = true;
                            workAvailable.await();
                        }
                        else {
                            blocked = false;
                            int s = w.idleCount;
                            if (shouldSleep(s)) {
                                if (++sleepers > runningWorkers) {
                                    // periodically reset
                                    sleepers = 0;
                                    idleSleep.signalAll();
                                }
                                else {
                                    blocked = true;
                                    long nanos = s * SLEEP_NANOS_PER_SCAN;
                                    idleSleep.awaitNanos(nanos);
                                }
                            }
                        }
                        if (blocked && runState < STOP && 
                            runningSubmissions < maxRunningSubmissions)
                            task = submissions.poll();
                    }
                    if (task != null) {
                        ++runningSubmissions;
                        w.clearIdleCount();
                        signalWork();
                    }
                }
            } catch (InterruptedException ie) {
                // ignore
            } finally {
                lock.unlock();
            }
        }
        return task;
    }

    /**
     * Wakes up some thread that is sleeping waiting for work.  Called
     * from ForkJoinWorkerThreads upon successful steals. This
     * propagates wakeups across workers when new work becomes
     * available to a set of otherwise idle threads.
     * @param w the worker that may have some tasks to steal from
     */
    final void alertSleeper(ForkJoinWorkerThread w) {
        int c = sleepers;
        if (c > 0 && !w.queueIsEmpty()) {
            final ReentrantLock lock = this.lock;
            if (lock.tryLock()) { // skip if contended
                try {
                    idleSleep.signal();
                    if (--c <= 0 || !lock.hasWaiters(idleSleep))
                        c = 0;
                    sleepers = c;
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Termination callback from dying worker.
     * @param w the worker
     * @param ex the exception causing abrupt termination, or null if
     * completed normally
     */
    final void workerTerminated(ForkJoinWorkerThread w, Throwable ex) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            w.cancelTasks();
            w.advanceIdleCount();
            boolean replaced = false;
            if (ex != null) {
                if (!continueOnError)
                    terminate();
                else if (runState < STOP) {
                    int idx = w.getIndex();
                    ForkJoinWorkerThread[] ws = workers;
                    if (idx < ws.length) { 
                        ForkJoinWorkerThread replacement = 
                            new ForkJoinWorkerThread(this, idx, w.getName());
                        if (ueh != null)
                            replacement.setUncaughtExceptionHandler(ueh);
                        ws[idx] = replacement;
                        replacement.start();
                        replaced = true;
                    }
                }
            }
            if (!replaced && --runningWorkers <= 0) {
                if (runState < STOP)
                    terminate();
                runState = TERMINATED;
                termination.signalAll();
            }
        } finally {
            lock.unlock();
        }
        if (ex != null) {
            if (ex instanceof RuntimeException)
                throw (RuntimeException)ex;
            else if (ex instanceof Error)
                throw (RuntimeException)ex;
            else
                throw new Error(ex);
        }
    }

    /**
     * Adapter class to allow submissions to act as Futures.  This
     * entails three kinds of adaptation:
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
     * worker threads -- when there are no submissions, workers are
     * blocked, but otherwise they scan looking for work. The
     * submissionCompleted method invoked when compute() completes
     * performs the associated bookkeeping.
     */
    static final class Submission<T> extends RecursiveTask<T> implements Future<T> {
        final ForkJoinTask<T> task;
        final ForkJoinPool pool;
        final ReentrantLock lock;
        final Condition ready;
        Submission(ForkJoinTask<T> t, ForkJoinPool p) {
            pool = p;
            task = t;
            lock = new ReentrantLock();
            ready = lock.newCondition();
        }

        protected T compute() {
            try {
                return task.invoke();
            } finally {
                completed();
            }
        }

        void completed() {
            pool.submissionCompleted();
            final ReentrantLock lock = this.lock;
            lock.lock();
            ready.signalAll();
            lock.unlock();
        }

        public boolean cancel(boolean ignore) {
            boolean ret = false;
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                task.cancel();
                // avoid recursive call to cancel
                finishExceptionally(new CancellationException());
                ret = isCancelled();
                pool.submissionCompleted();
                ready.signalAll();
            } finally {
                lock.unlock();
            }
            return ret;
        }

        /**
         * Return result or throw exception using Future conventions
         */
        static <T> T futureResult(ForkJoinTask<T> t) throws ExecutionException {
            RuntimeException ex = t.getException();
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
            if (!t.isDone()) {
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
            }
            return futureResult(t);
        }

        /**
         * Interrupt-less get for ForkJoinPool.invoke
         */
        T awaitInvoke() {
            final ForkJoinTask<T> t = this.task;
            if (!t.isDone()) {
                final ReentrantLock lock = this.lock;
                lock.lock();
                try {
                    while (!t.isDone())
                        ready.awaitUninterruptibly();
                } finally {
                    lock.unlock();
                }
            }
            return t.getResult();
        }
    }


    /**
     * A SubmissionQueue is a simple array-based circular queue.
     * Basically a stripped-down ArrayDeque
     */
    static final class SubmissionQueue {
        static final int INITIAL_SUBMISSION_QUEUE_CAPACITY = 64;
        Submission<?>[] elements = (Submission<?>[]) 
            new Submission[INITIAL_SUBMISSION_QUEUE_CAPACITY];
        int head;
        int tail;

        int size() {
            return (tail - head) & (elements.length - 1);
        }

        boolean isEmpty() {
            return head == tail;
        }

        void add(Submission<?> e) {
            elements[tail] = e;
            if ( (tail = (tail + 1) & (elements.length - 1)) == head)
                doubleCapacity();
        }

        Submission<?> poll() {
            int h = head;
            Submission<?> result = elements[h];
            if (result != null) {
                elements[h] = null;
                head = (h + 1) & (elements.length - 1);
            }
            return result;
        }

        void doubleCapacity() {
            int p = head;
            int n = elements.length;
            int r = n - p;
            int newCapacity = n << 1;
            if (newCapacity < 0)
                throw new IllegalStateException("Queue capacity exceeded");
            Submission<?>[] a = (Submission<?>[]) new Submission[newCapacity];
            System.arraycopy(elements, p, a, 0, r);
            System.arraycopy(elements, 0, a, r, p);
            elements = a;
            head = 0;
            tail = n;
        }
    }

}
