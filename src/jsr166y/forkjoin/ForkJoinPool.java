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
 * manages a group of specialized ForkJoinWorkerThreads that perform
 * ForkJoinTasks. It also provides the entry point for tasks submitted
 * from non-ForkJoinTasks, as well as other management and monitoring
 * operations.
 *
 * <p> Class ForkJoinPool does not implement the ExecutorService
 * interface because it only executes ForkJoinTasks, not arbitrary
 * Runnables. However, for the sake of uniformity, it supports
 * ExecutorService lifecycle control methods (such as shutdown).
 *
 * <p>A ForkJoinPool may be constructed with any number of worker
 * threads, and worker threads may be added and removed (one-by-one)
 * dynamically. However, as a general rule, using a pool size of the
 * number of processors on a given system, as arranged by the default
 * constructor) will result in the best performance.
 *
 * <p> Normally a single ForkJoinPool should be used for a large
 * number of ForkJoinTasks. (Otherwise, use would not always outweigh
 * construction overhead.) However, if a computation encounters an
 * untrapped error, you may need special handling to recover.  Methods
 * <tt>setContinueOnErrorPolicy</tt>,
 * <tt>setUncaughtExceptionHandler</tt>, and
 * <tt>getAbortedWorkerCount</tt> may assist in such
 * efforts. Alternatively, it is often simpler to replace the pool
 * with a fresh one.
 *
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
     * Factory for creating new ForkJoinWorkerThreads.  A
     * ForkJoinWorkerThreadFactory must be defined and used for
     * ForkJoinWorkerThread subclasses that extend base functionality
     * or initialize threads with different contexts.
     */
    public static interface ForkJoinWorkerThreadFactory {
        /**
         * Returns a new worker thread operating in the given pool.
         * 
         * @param pool the pool this thread works in
         * @throws NullPointerException if pool is null;
         */
        public ForkJoinWorkerThread newThread(ForkJoinPool pool);
    }

    /**
     * The default ForkJoinWorkerThreadFactory, used unless overridden
     * in ForkJoinPool constructors.
     */
    public static class  DefaultForkJoinWorkerThreadFactory 
        implements ForkJoinWorkerThreadFactory {
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            return new ForkJoinWorkerThread(pool);
        }
    }

    static final DefaultForkJoinWorkerThreadFactory 
        defaultForkJoinWorkerThreadFactory = 
        new DefaultForkJoinWorkerThreadFactory();


    /**
     * Generator for assigning sequence numbers as thread names.
     */
    private static final AtomicInteger poolNumberGenerator = new AtomicInteger();

    /**
     * Creation factory for worker threads.
     */
    private final ForkJoinWorkerThreadFactory factory;

    /**
     * Array holding all worker threads in the pool. Acts similarly to
     * a CopyOnWriteArrayList, but hand-crafted to allow in-place
     * replacements and to maintain thread to index mappings. All uses
     * of this array should first assign as local, and must screen out
     * nulls.
     */
    private volatile ForkJoinWorkerThread[] workers;

    /**
     * External submission queue. "Submissions" are tasks scheduled
     * via submit or invoke, not internally generated within
     * ForkJoinTasks.
     */
    private final SubmissionQueue submissions;

    /**
     * Main lock protecting access to threads and run state. You might
     * think that having a single lock and condition here wouldn't
     * work so well. But serializing the starting and stopping of
     * worker threads (its main purpose) helps enough in controlling
     * startup effects, contention vs dynamic compilation, etc to be
     * better than alternatives.
     */
    private final ReentrantLock lock;

    /**
     * Condition for awaitTermination. Triggered when
     * runningWorkers reaches zero.
     */
    private final Condition termination;

    /**
     * Condition triggered when new submission is available so workers
     * should awaken if blocked.
     */
    private final Condition workAvailable;

    /**
     * Queue (actually a simple Treiber stack) of threads sleeping
     * because they acannot find work even though there are active
     * jobs running. Used internally by workers, not by pool.
     */
    private final AtomicReference<ForkJoinWorkerThread> idleSleepQueue;

    /**
     * Tracks whether pool is running, shutdown, etc. Modified
     * only under lock, but volatile to allow concurrent reads.
     */
    private volatile int runState;

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
    private int runningSubmissions;

    /**
     * Maximum number of active submissions allowed to run;
     */
    private int maxRunningSubmissions;

    /**
     * Number of workers that are (probably) executing tasks.
     * Atomically incremented when a worker gets a task to run, and
     * decremented when worker has no tasks and cannot find any to
     * steal from other workers.
     */
    private volatile int activeWorkers;

    /**
     * Updater to CAS activeWorkers
     */
    private static final 
        AtomicIntegerFieldUpdater<ForkJoinPool> activeWorkersUpdater =
        AtomicIntegerFieldUpdater.newUpdater(ForkJoinPool.class, "activeWorkers");
    

    /** 
     * The number of workers that have not yet terminated 
     */
    private int runningWorkers;

    /**
     * The uncaught exception handler used when any worker
     * abrupty terminates
     */
    private volatile Thread.UncaughtExceptionHandler ueh;

    /**
     * True if dying workers should be replaced.
     */
    private boolean continueOnError;

    /**
     * Pool number, just for assigning useful names to worker threads
     */
    private final int poolNumber;

    /**
     * The number of workers that terminated abnormally
     */
    private volatile int workerAborts;

    /**
     * Creates a ForkJoinPool with a pool size equal to the number of
     * processors available on the system and using the default
     * ForkJoinWorkerThreadFactory,
     */
    public ForkJoinPool() {
        this(Runtime.getRuntime().availableProcessors(),
             defaultForkJoinWorkerThreadFactory);
    }

    /**
     * Creates a ForkJoinPool with the indicated number of Worker
     * threads, and using the default ForkJoinWorkerThreadFactory,
     * @param poolSize the number of worker threads
     * @throws IllegalArgumentException if poolSize less than or
     * equal to zero
     */
    public ForkJoinPool(int poolSize) {
        this(poolSize, defaultForkJoinWorkerThreadFactory);
    }

    /**
     * Creates a ForkJoinPool with a pool size equal to the number of
     * processors available on the system and using the given
     * ForkJoinWorkerThreadFactory,
     * @param factory the factory for creating new threads
     * @throws NullPointerException if factory is null
     */
    public ForkJoinPool(ForkJoinWorkerThreadFactory factory) {
        this(Runtime.getRuntime().availableProcessors(), factory);
    }

    /**
     * Creates a ForkJoinPool with the indicated number of worker
     * threads and the given factory.
     *
     * <p> You can also add and remove threads while the pool is
     * running, it is generally more efficient and leads to more
     * predictable performance to initialize the pool with a
     * sufficient number of threads to support the desired concurrency
     * level and leave this value fixed.
     *
     * @param poolSize the number of worker threads
     * @param factory the factory for creating new threads
     * @throws IllegalArgumentException if poolSize less than or
     * equal to zero
     * @throws NullPointerException if factory is null
     */
    public ForkJoinPool(int poolSize, ForkJoinWorkerThreadFactory factory) {
        if (poolSize <= 0) 
            throw new IllegalArgumentException();
        if (factory == null)
            throw new NullPointerException();
        this.factory = factory;
        this.poolNumber = poolNumberGenerator.incrementAndGet();
        this.maxRunningSubmissions = Integer.MAX_VALUE;
        this.lock = new ReentrantLock();
        this.termination = lock.newCondition();
        this.workAvailable = lock.newCondition();
        this.idleSleepQueue = new AtomicReference<ForkJoinWorkerThread>();
        this.submissions = new SubmissionQueue();
        ForkJoinWorkerThread[] ws = new ForkJoinWorkerThread[poolSize];
        for (int i = 0; i < ws.length; ++i)
            ws[i] = createWorker(i);
        this.workers = ws;
        startWorkers(ws);
    }

    /**
     * Create new worker using factory
     * @param index the index to assign worker
     */
    private ForkJoinWorkerThread createWorker(int index) {
        ForkJoinWorkerThread w = factory.newThread(this);
        w.setDaemon(true);
        w.setIndex(index);
        String name = "ForkJoinPool-" + poolNumber + "-worker-" + index;
        w.setName(name);
        if (ueh != null)
            w.setUncaughtExceptionHandler(ueh);
        workerActive();
        return w;
    }

    /**
     * Start the given worker, if nonnull. Call only while holding
     * main lock.
     * @param w the worker
     */
    private void startWorker(ForkJoinWorkerThread w) {
        if (w != null) {
            w.start();
            ++runningWorkers;
        }
    }

    /**
     * Start all workers in the given array.
     */
    private void startWorkers(ForkJoinWorkerThread[] ws) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (int i = 0; i < ws.length; ++i) 
                startWorker(ws[i]);
        } finally {
            lock.unlock();
        }
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
            ForkJoinWorkerThread w = createWorker(len);
            nws[len] = w;
            workers = nws;
            startWorker(w);
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

    /**
     * Initiate termination. Call only while holding main lock.
     */
    private void terminate() {
        if (runState < STOP) {
            runState = STOP;
            cancelQueuedSubmissions();
            ForkJoinWorkerThread[] ws = workers;
            stopAllWorkers(ws);
            doCancelQueuedWorkerTasks(ws);
            interruptAllWorkers(ws);
            workAvailable.signalAll();
        }
    }

    // Helpers for termination and cancellation

    private void cancelQueuedSubmissions() {
        Submission<?> task;
        while ((task = submissions.poll()) != null)
            task.cancel(false);
    }

    private void doCancelQueuedWorkerTasks(ForkJoinWorkerThread[] ws) {
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                t.cancelTasks();
        }
    }

    private void interruptAllWorkers(ForkJoinWorkerThread[] ws) {
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                t.interrupt();
        }
    }

    private void stopAllWorkers(ForkJoinWorkerThread[] ws) {
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                t.shouldStop();
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

    /**
     * Returns the number of threads that are not currently idle
     * waiting for tasks.
     * @return the number of active threads.
     */
    public int getActiveThreadCount() {
        return activeWorkers;
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
                sum += t.getSteals();
        }
        return sum;
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
     * to concurrently execute. By default, the value is essentially
     * unbounded (Integer.MAX_VALUE).
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
     * that submissions execute one at a time, which may be required
     * for sensible use of methods such as <tt>isQuiescent</tt>.
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
     * Returns the number of worker threads that have 
     * terminated exceptionally due to untrapped errors.
     *
     * @return then number of aborted workers.
     */
    public int getAbortedWorkerCount() {
        return workerAborts;
    }

    /**
     * Returns the factory used for constructing new workers
     *
     * @return the factory used for constructing new workers
     */
    public ForkJoinWorkerThreadFactory getFactory() {
        return factory;
    }

    // Methods callable from workers, along with other misc support

    /**
     * Callback when worker is created or becomes active
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

    /**
     * Return current workers array
     */
    final ForkJoinWorkerThread[] getWorkers() {
        return workers;
    }

    /**
     * Return idle sleep queue
     */
    final AtomicReference<ForkJoinWorkerThread> getIdleSleepQueue() {
        return idleSleepQueue;
    }

    /**
     * Enqueue an externally submitted task
     */
    private void addSubmission(Submission<?> job) {
        final ReentrantLock lock = this.lock;
        boolean ok;
        lock.lock();
        try {
            if (ok = (runState == RUNNING)) {
                submissions.add(job);
                workAvailable.signalAll();
            }
        } finally {
            lock.unlock();
        }
        if (!ok)
            throw new RejectedExecutionException();
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
     * Returns a job to run, or null if none available. Blocks if
     * there are no available submissions. 
     */
    final ForkJoinTask<?> getSubmission() {
        ForkJoinTask<?> task = null;
        final ReentrantLock lock = this.lock;
        if (lock.tryLock()) {
            try {
                if (runState < STOP) {
                    if (runningSubmissions < maxRunningSubmissions)
                        task = submissions.poll();
                    if (task == null && runningSubmissions == 0) {
                        workAvailable.await();
                        if (runState < STOP && 
                            runningSubmissions < maxRunningSubmissions)
                            task = submissions.poll();
                    }
                    if (task != null) {
                        ++runningSubmissions;
                        workAvailable.signalAll();
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
     * Returns a job to run, or null if none available without blocking.
     */
    final ForkJoinTask<?> pollSubmission() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (runState < STOP && 
                runningSubmissions < maxRunningSubmissions) {
                ForkJoinTask<?> task = submissions.poll();
                if (task != null) {
                    ++runningSubmissions;
                    workAvailable.signalAll();
                    return task;
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }


    /**
     * Callback from terminating worker.
     * @param w the worker
     * @param ex the exception causing abrupt termination, or null if
     * completed normally
     */
    final void workerTerminated(ForkJoinWorkerThread w, Throwable ex) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            boolean replaced = false;
            int rw = --runningWorkers;
            if (ex != null) {
                ++workerAborts;
                if (!continueOnError)
                    terminate();
                else if (runState < STOP) {
                    int idx = w.getIndex();
                    ForkJoinWorkerThread[] ws = workers;
                    if (idx < ws.length) { 
                        ForkJoinWorkerThread replacement = createWorker(idx);
                        ws[idx] = replacement;
                        startWorker(replacement);
                        replaced = true;
                    }
                }
            }
            if (!replaced && rw <= 0) {
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
                throw (Error)ex;
            else
                throw new Error(ex);
        }
    }

    /**
     * A SubmissionQueue is a simple array-based circular queue.
     * Basically a stripped-down ArrayDeque
     */
    static final class SubmissionQueue {
        static final int INITIAL_SUBMISSION_QUEUE_CAPACITY = 64;
        private Submission<?>[] elements = (Submission<?>[]) 
            new Submission[INITIAL_SUBMISSION_QUEUE_CAPACITY];
        private int head;
        private int tail;

        public int size() {
            return (tail - head) & (elements.length - 1);
        }

        public boolean isEmpty() {
            return head == tail;
        }

        public void add(Submission<?> e) {
            elements[tail] = e;
            if ( (tail = (tail + 1) & (elements.length - 1)) == head)
                doubleCapacity();
        }

        public Submission<?> poll() {
            int h = head;
            Submission<?> result = elements[h];
            if (result != null) {
                elements[h] = null;
                head = (h + 1) & (elements.length - 1);
            }
            return result;
        }

        private void doubleCapacity() {
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
        private final ForkJoinTask<T> task;
        private final ForkJoinPool pool;
        private final ReentrantLock lock;
        private final Condition ready;
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

        private void completed() {
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
        static <T> T futureResult(ForkJoinTask<T> t) 
            throws ExecutionException {
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
        public T awaitInvoke() {
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

}
