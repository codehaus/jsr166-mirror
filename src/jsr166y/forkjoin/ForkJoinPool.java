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
     * Maximum supported value for all bounds used in this pool: pool
     * size, maximum submission queue size, and maximum active
     * submission bounds. The value of this constant is 65535.  This
     * implementation-defined value is subject to increase in future
     * releases.
     */
    public static final int CAPACITY = (1 << 16) - 1;
    
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
     * better than alternatives. See however, use of SubmissionState
     * to evade locking in many common cases.
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
     * Sleep control used internally by workers.
     */
    private final SleepControl sleepCtl;

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
     * Counter maintaining number submissions available to worker threads.
     */
    private final SubmissionState submissionState;

    /**
     * Number of workers that are (probably) executing tasks.
     * Atomically incremented when a worker gets a task to run, and
     * decremented when worker has no tasks and cannot find any to
     * steal from other workers.
     */
    private final AtomicInteger activeWorkerCounter;

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
    private volatile boolean continueOnError;

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
     * equal to zero or greater that capacity
     * @throws NullPointerException if factory is null
     */
    public ForkJoinPool(int poolSize, ForkJoinWorkerThreadFactory factory) {
        if (poolSize <= 0 || poolSize > CAPACITY) 
            throw new IllegalArgumentException();
        if (factory == null)
            throw new NullPointerException();
        this.factory = factory;
        ForkJoinWorkerThread[] ws = new ForkJoinWorkerThread[poolSize];
        this.workers = ws;
        this.sleepCtl = new SleepControl(poolSize);
        this.lock = new ReentrantLock();
        this.termination = lock.newCondition();
        this.workAvailable = lock.newCondition();
        this.activeWorkerCounter = new AtomicInteger();
        this.submissionState = new SubmissionState(CAPACITY);
        this.submissions = new SubmissionQueue();
        this.poolNumber = poolNumberGenerator.incrementAndGet();
        createAndStartAll();
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
     * Create and start all workers in workers array.
     */
    private void createAndStartAll() {
        ForkJoinWorkerThread[] ws = workers;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (int i = 0; i < ws.length; ++i)
                ws[i] = createWorker(i);
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
        return continueOnError;
    }

    /**
     * Sets the policy for whether to continue execution
     * when a worker thread aborts due to Error. If false,
     * the pool shots down upon error.
     * @param shouldContinue true if the pool should continue
     */
    public void setContinueOnErrorPolicy(boolean shouldContinue) {
        continueOnError = shouldContinue;
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
     * terminated or the pool size would exceed maximum capacity.
     */
    public int addWorker() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            int len = ws.length;
            int newlen = len + 1;
            if (newlen > CAPACITY || runState >= STOP)
                throw new IllegalStateException();
            ForkJoinWorkerThread[] nws = new ForkJoinWorkerThread[newlen];
            System.arraycopy(ws, 0, nws, 0, len);
            ForkJoinWorkerThread w = createWorker(len);
            nws[len] = w;
            workers = nws;
            sleepCtl.resetCap(newlen);
            startWorker(w);
            return len;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Arranges for (asynchronous) execution of the given task using a
     * fresh worker thread that is guaranteed not to be executing any
     * other task, and optionally exits upon its completion.
     * @param task the task
     * @param exitOnCompletion if true, the created worker will
     * terminate upon completion of this task.
     * @return a Future that can be used to get the task's results.
     * @throws NullPointerException if task is null
     * @throws IllegalStateException if pool is terminating or
     * terminated or the pool size would exceed maximum capacity.
     */
    public <T> Future<T> submitUsingAddedWorker(ForkJoinTask<T> task,
                                                boolean exitOnCompletion) {
        if (task == null)
            throw new NullPointerException();
        Submission<T> job = new Submission<T>(task, this);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            int len = ws.length;
            int newlen = len + 1;
            if (newlen > CAPACITY || runState >= STOP)
                throw new IllegalStateException();
            ForkJoinWorkerThread[] nws = new ForkJoinWorkerThread[newlen];
            System.arraycopy(ws, 0, nws, 0, len);
            ForkJoinWorkerThread w = createWorker(len);
            w.setFirstTask(job, exitOnCompletion);
            nws[len] = w;
            workers = nws;
            submissionState.incRunning();
            sleepCtl.resetCap(newlen);
            startWorker(w);
        } finally {
            lock.unlock();
        }
        return job;
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
            sleepCtl.resetCap(newlen);
            if (w != null) 
                w.setShutdown();
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
                if (submissionState.getRunningSubmissions() > 0 ||
                    !submissions.isEmpty())
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
        while ((task = submissions.poll()) != null) {
            submissionState.decQueueSize();
            task.cancel(false);
        }
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
                t.setStop();
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
        return activeWorkerCounter.get() == 0;
    }

    /**
     * Returns the number of threads that are not currently idle
     * waiting for tasks.
     * @return the number of active threads.
     */
    public int getActiveThreadCount() {
        return activeWorkerCounter.get();
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
     * Returns the total number of times that worker threads have
     * transiently slept waiting for other workers to produce tasks
     * for them to run. This value is only an approximation, obtained
     * by iterating across all threads in the pool, but may be useful
     * for monitoring and tuning fork/join programs.  High values
     * usually indicate that there is not enough parallelism for the
     * given pool size.
     * @return the number of sleeps.
     */
    public long getSleepCount() {
        long sum = 0;
        ForkJoinWorkerThread[] ws = workers;
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                sum += t.getSleeps();
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
        return submissionState.getQueueSize();
    }

    /**
     * Returns the number of tasks that have been submitted (via
     * <tt>submit</tt> or <tt>invoke</tt>) and are currently executing
     * in the pool.
     * @return the number of tasks.
     */
    public int getActiveSubmissionCount() {
        return submissionState.getRunningSubmissions();
    }

    /**
     * Returns the maximum number of submitted tasks that are allowed
     * to concurrently execute. By default, the value is the
     * capacity of the pool.
     * @return the maximum number
     */
    public int getMaximumActiveSubmissionCount() {
        return submissionState.getMaxRunningSubmissions();
    }

    /**
     * Sets the maximum number of submitted tasks that are allowed to
     * concurrently execute. Restricting this value may lessen
     * interference among tasks. Restricting the value to one ensures
     * that submissions execute one at a time, which may be required
     * for sensible use of methods such as <tt>isQuiescent</tt>.
     * @param max the maximum
     * @throws IllegalArgumentException if max is not positive
     * or if max is greater than capacity of pool
     */
    public void setMaximumActiveSubmissionCount(int max) {
        if (max <= 0 || max > CAPACITY)
            throw new IllegalArgumentException("max out of range");
        lock.lock();
        try {
            submissionState.setMaxRunning(max);
            workAvailable.signalAll();
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
     * Return current workers array
     */
    final ForkJoinWorkerThread[] getWorkers() {
        return workers;
    }

    /**
     * Return counter (cached by workers)
     */
    final AtomicInteger getActiveWorkerCounter() {
        return activeWorkerCounter;
    }

    /**
     * Return idle sleep semaphore (cached by workers)
     */
    final SleepControl getSleepControl() {
        return sleepCtl;
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
                submissionState.incQueueSize();
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
        if (submissionState.decRunningOnCompletion() &&
            runState >= SHUTDOWN) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                // must recheck state
                if (submissionState.getRunningSubmissions() == 0 &&
                    runState >= SHUTDOWN && submissions.isEmpty())
                    terminate();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Returns a job to run, or null if none available. May block if
     * there are no available submissions. 
     * @param canBlock true if caller should block if no jobs
     * are running and the queue is empty.
     */
    final ForkJoinTask<?> getSubmission(boolean canBlock) {
        /*
         * The unusual loop here eliminates need for locking in common
         * cases where we can tell from submission state that we can't
         * get a task, and don't need to block for one.
         */
        ForkJoinTask<?> task = null;
        final ReentrantLock lock = this.lock;
        boolean locked = false;
        try {
            for (;;) {
                long ss = submissionState.get();
                int qs = SubmissionState.queueSizeOf(ss);
                int max = SubmissionState.maxRunningOf(ss);
                int running = SubmissionState.runningOf(ss);
                if (running >= max || 
                    (!canBlock && qs == 0) ||
                    runState >= STOP)
                    break;
                if (!locked) {
                    locked = lock.tryLock();
                    continue;
                }
                if ((task = submissions.poll()) != null) {
                    submissionState.incRunningOnPoll();
                    workAvailable.signalAll();
                    break;
                }
                if (!canBlock || running > 0)
                    break;
                workAvailable.await();
            }
        } catch (InterruptedException ie) {
            // ignore/swallow
        } finally {
            if (locked) 
                lock.unlock();
        }
        return task;
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
            if (newCapacity >= CAPACITY)
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

    /**
     * Specialized capped form of a semaphore to control sleeps when
     * workers cannot get tasks to run. See ForkJoinWorkerThread for
     * explanation.
     */
    static final class SleepControl extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1192457210091910933L;

        /*
         * Top 16 bits are capacity, bottom 16 are available permits.
         * The three kinds of release are multiplexed via op
         * codes. Positive codes are the capacity reset args.
         */
        static int capOf(int s)           { return s >>> 16; }
        static int permitsOf(int s)       { return s & 0xffff; }
        static int stateFor(int c, int p) { return (c << 16) | p; }

        SleepControl(int cap)  { setState(stateFor(cap, cap));  }

        int getPermits()       { return permitsOf(getState());  }
        int getCap()           { return capOf(getState());  }
        
        static final int RELEASE_ONE =  0;
        static final int RELEASE_ALL = -1;

        void releaseOne()      { releaseShared(RELEASE_ONE); }
        void releaseAll()      { releaseShared(RELEASE_ALL); }
        void resetCap(int cap) { releaseShared(cap); }

        /**
         * Try to acquire. 
         * @return -1 on timeout, 0 on success with no wait, 1 if wait
         */
        int acquire(long nanos) {
            try {
                if (tryAcquireShared(0) >= 0)
                    return 0;
                else if (tryAcquireSharedNanos(0, nanos))
                    return 1;
                else
                    return -1;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // basically useless
                return -1;
            }
        }

        protected int tryAcquireShared(int ignore) {
            // Need fairness for sake of releaseAll
            Thread current = Thread.currentThread();
            for (;;) {
                Thread first = getFirstQueuedThread();
                if (first != null && first != current)
                    return -1;
                int s = getState();
                int c = capOf(s);
                int remaining = permitsOf(s) - 1;
                if (remaining < 0 ||
                    compareAndSetState(s, stateFor(c, remaining)))
                    return remaining;
            }
        }

        protected boolean tryReleaseShared(int op) {
            for (;;) {
                int s = getState();
                int c = capOf(s);
                int p = permitsOf(s);
                if (op <= 0 && p >= c) // can't add any more permits
                    return false;
                int next;
                if (op == RELEASE_ONE) 
                    next = stateFor(c, p+1);
                else if (op == RELEASE_ALL)
                    next = stateFor(c, c);
                else // reset cap
                    next = stateFor(op, op);
                
                if (compareAndSetState(s, next))
                    return true;
            }
        }
    }


    /**
     * Atomically maintains counts of running, max running, and
     * queued counts.
     */
    static final class SubmissionState extends AtomicLong {

        SubmissionState(int maxRunning) {
            set(stateFor(0, maxRunning, 0, 0));
        }

        static final int ushortBits = 16;
        static final int ushortMask  =  0xffff;
        static final int uintMask    =  (1 << ushortBits) - 1;

        static int runningOf(long s) { 
            return (int)(s & 0xffff); 
        }
        static int queueSizeOf(long s) {
            return (int)(s >>> 16) & 0xffff;
        }
        static int maxRunningOf(long s) {
            return (int)(s >>> 32) & 0xffff;
        }
        static int tagOf(long s) {
            return (int)(s >>> 48);
        }
        static long stateFor(int t, int m, int q, int r) {
            return ((long)t << 48) | ((long)m << 32) | (q << 16) | r;
        }

        int getRunningSubmissions() {
            return runningOf(get());
        }

        int getMaxRunningSubmissions() {
            return maxRunningOf(get());
        }

        int getQueueSize() {
            return queueSizeOf(get());
        }

        void setQueueSize(int queueSize) {
            for (;;) {
                long s = get();
                int r = runningOf(s);
                int m = maxRunningOf(s);
                int t = (tagOf(s) + 1) & 0xffff;
                if (compareAndSet(s, stateFor(t, m, queueSize, r)))
                    return;
            }
        }

        int incQueueSize() {
            for (;;) {
                long s = get();
                int r = runningOf(s);
                int m = maxRunningOf(s);
                int q = queueSizeOf(s) + 1;
                int t = (tagOf(s) + 1) & 0xffff;
                if (compareAndSet(s, stateFor(t, m, q, r)))
                    return q;
            }
        }

        int decQueueSize() {
            for (;;) {
                long s = get();
                int r = runningOf(s);
                int m = maxRunningOf(s);
                int q = queueSizeOf(s) - 1;
                int t = (tagOf(s) + 1) & 0xffff;
                if (compareAndSet(s, stateFor(t, m, q, r)))
                    return q;
            }
        }

        void setMaxRunning(int maxRunning) {
            for (;;) {
                long s = get();
                int r = runningOf(s);
                int q = queueSizeOf(s);
                int t = (tagOf(s) + 1) & 0xffff;
                if (compareAndSet(s, stateFor(t, maxRunning, q, r)))
                    return;
            }
        }

        int incRunningOnPoll() {
            for (;;) {
                long s = get();
                int nextr = runningOf(s) + 1;
                int m = maxRunningOf(s);
                int q = queueSizeOf(s) - 1;
                int t = (tagOf(s) + 1) & 0xffff;
                if (compareAndSet(s, stateFor(t, m, q, nextr)))
                    return nextr;
            }
        }

        int incRunning() {
            for (;;) {
                long s = get();
                int nextr = runningOf(s) + 1;
                int m = maxRunningOf(s);
                int q = queueSizeOf(s);
                int t = (tagOf(s) + 1) & 0xffff;
                if (compareAndSet(s, stateFor(t, m, q, nextr)))
                    return nextr;
            }
        }

        int decRunning() {
            for (;;) {
                long s = get();
                int nextr = runningOf(s) - 1;
                int m = maxRunningOf(s);
                int q = queueSizeOf(s);
                int t = (tagOf(s) + 1) & 0xffff;
                if (compareAndSet(s, stateFor(t, m, q, nextr)))
                    return nextr;
            }
        }

        boolean decRunningOnCompletion() {
            for (;;) {
                long s = get();
                int nextr = runningOf(s) - 1;
                int m = maxRunningOf(s);
                int q = queueSizeOf(s);
                int t = (tagOf(s) + 1) & 0xffff;
                if (compareAndSet(s, stateFor(t, m, q, nextr)))
                    return nextr == 0 && q == 0;
            }
        }

    }



}
