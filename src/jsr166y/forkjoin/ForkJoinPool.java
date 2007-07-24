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
import jsr166y.*;


/**
 * The host and external interface for ForkJoinTasks. A ForkJoinPool
 * manages a group of specialized ForkJoinWorkerThreads that perform
 * ForkJoinTasks. It also provides the entry point for tasks submitted
 * from non-ForkJoinTasks, as well as other management and monitoring
 * operations.  Normally a single ForkJoinPool is used for a large
 * number of submitted tasks. Otherwise, use would not always outweigh
 * the construction overhead of creating a large set of threads.
 *
 * <p> Class ForkJoinPool does not implement the ExecutorService
 * interface because it only executes ForkJoinTasks, not arbitrary
 * Runnables. However, for the sake of uniformity, it supports
 * analogous lifecycle control methods such as shutdown.
 *
 * <p>A ForkJoinPool may be constructed with any number of worker
 * threads, and worker threads may be added and removed (one-by-one)
 * dynamically. However, as a general rule, using a pool size of the
 * number of processors on a given system, as arranged by the default
 * constructor) will result in the best performance.
 *
 * <p>In addition to execution and lifecycle control methods, this
 * class provides methods (for example <tt>getStealCount</tt>) that
 * are intended to aid in developing, tuning, and monitoring fork/join
 * applications.
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

    private static final DefaultForkJoinWorkerThreadFactory
        defaultForkJoinWorkerThreadFactory =
        new DefaultForkJoinWorkerThreadFactory();


    /**
     * Permission required for callers of methods that may start or
     * kill.threads.
     */
    private static final RuntimePermission modifyThreadPermission =
        new RuntimePermission("modifyThread");

    /**
     * If there is a security manager, makes sure caller has
     * permission to modify threads.
     */
    private static void checkPermission() {
        SecurityManager security = System.getSecurityManager();
        if (security != null)
            security.checkPermission(modifyThreadPermission);
    }

    /**
     * Generator for assigning sequence numbers as thread names.
     */
    private static final AtomicInteger poolNumberGenerator =
        new AtomicInteger();

    /**
     * Creation factory for worker threads.
     */
    private final ForkJoinWorkerThreadFactory factory;

    /**
     * Array holding all worker threads in the pool. Acts similarly to
     * a CopyOnWriteArrayList -- updates are protected by workerLock.
     * But it additionally allows in-place nulling out or replacements
     * of slots upon termination.  All uses of this array should first
     * assign as local, and must screen out nulls.
     */
    private volatile ForkJoinWorkerThread[] workers;

    /**
     * Lock protecting access to workers.
     */
    private final ReentrantLock workerLock;

    /**
     * Condition for awaitTermination.
     */
    private final Condition termination;

    /**
     * The number of workers that have started but not yet terminated
     * Accessed only under workerLock.
     */
    private int runningWorkers;

    /**
     * Queue of external submissions.
     */
    private final ConcurrentLinkedQueue<Submission<?>> submissionQueue;

    /**
     * Lifecycle control.
     */
    private final RunState runState;

    /**
     * Pool wide synchronization control. Workers are enabled to look
     * for work when the barrier's count is incremented. If they fail
     * to find some, they may wait for next count. Sysnchronization
     * events occur only in enough contexts to maintain overall
     * liveness:
     *
     *   1. Changes to worker array
     *   2. Changes to runState
     *   3. Submission of a new task
     *   4. A worker pushing a task on an empty per-worker queue
     *   5. A worker completing a stolen or cancelled task
     *
     * So, signals and waits occur relatively rarely in normal
     * fork/join processing, which minimizes contention on this global
     * synchronizer. Even so, the EventBarrier is designed to minimize
     * blockages by signallers (that have better things to do).
     */
    private final EventBarrier eventBarrier;

    /**
     * The number of submissions that are running in pool.
     */
    private final AtomicInteger runningSubmissions;

    /**
     * Number of workers that are (probably) executing tasks.
     * Atomically incremented when a worker gets a task to run, and
     * decremented when worker has no tasks and cannot find any.
     */
    private final AtomicInteger activeWorkerCounter;

    /**
     * The uncaught exception handler used when any worker
     * abrupty terminates
     */
    private Thread.UncaughtExceptionHandler ueh;

    /**
     * Pool number, just for assigning useful names to worker threads
     */
    private final int poolNumber;

    /**
     * Creates a ForkJoinPool with a pool size equal to the number of
     * processors available on the system and using the default
     * ForkJoinWorkerThreadFactory,
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
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
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
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
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     */
    public ForkJoinPool(ForkJoinWorkerThreadFactory factory) {
        this(Runtime.getRuntime().availableProcessors(), factory);
    }

    /**
     * Creates a ForkJoinPool with the indicated number of worker
     * threads and the given factory.
     *
     * <p> You can also add and remove threads while the pool is
     * running. But it is generally more efficient and leads to more
     * predictable performance to initialize the pool with a
     * sufficient number of threads to support the desired concurrency
     * level and leave this value fixed.
     *
     * @param poolSize the number of worker threads
     * @param factory the factory for creating new threads
     * @throws IllegalArgumentException if poolSize less than or
     * equal to zero
     * @throws NullPointerException if factory is null
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     */
    public ForkJoinPool(int poolSize, ForkJoinWorkerThreadFactory factory) {
        checkPermission();
        if (poolSize <= 0)
            throw new IllegalArgumentException();
        if (factory == null)
            throw new NullPointerException();
        this.factory = factory;
        this.poolNumber = poolNumberGenerator.incrementAndGet();
        this.workers = new ForkJoinWorkerThread[poolSize];
        this.submissionQueue = new ConcurrentLinkedQueue<Submission<?>>();
        this.eventBarrier = new EventBarrier();
        this.activeWorkerCounter = new AtomicInteger();
        this.runningSubmissions = new AtomicInteger();
        this.runState = new RunState();
        this.workerLock = new ReentrantLock();
        this.termination = workerLock.newCondition();
        createAndStartAll();
    }

    /**
     * Create new worker using factory. Call only while holding workerLock
     * @param index the index to assign worker
     */
    private ForkJoinWorkerThread createWorker(int index) {
        ForkJoinWorkerThread w = factory.newThread(this);
        w.setDaemon(true);
        w.setWorkerPoolIndex(index);
        w.setName("ForkJoinPool-" + poolNumber + "-worker-" + index);
        Thread.UncaughtExceptionHandler h = ueh;
        if (h != null)
            w.setUncaughtExceptionHandler(h);
        return w;
    }

    /**
     * Start the given worker, if nonnull. Call only while holding
     * workerLock.
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
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            for (int i = 0; i < ws.length; ++i)
                ws[i] = createWorker(i);
            for (int i = 0; i < ws.length; ++i)
                startWorker(ws[i]);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Enqueue an externally submitted task
     */
    private void addSubmission(Submission<?> job) {
        if (runState.isAtLeastShutdown() || !submissionQueue.offer(job))
            throw new RejectedExecutionException();
        eventBarrier.signal();
    }

    /**
     * Performs the given task; returning its result upon completion
     * @param task the task
     * @return the task's result
     * @throws NullPointerException if task is null
     * @throws RejectedExecutionException if pool is shut down
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
     * @throws RejectedExecutionException if pool is shut down
     */
    public <T> Future<T> submit(ForkJoinTask<T> task) {
        if (task == null)
            throw new NullPointerException();
        Submission<T> job = new Submission<T>(task, this);
        addSubmission(job);
        return job;
    }

    /**
     * Returns the number of worker threads in this pool.  This method
     * returns the current targetted pool size, and does not
     * necessarily reflect transient changes as threads are added,
     * removed, or abruptly terminate.
     *
     * @return the number of worker threads in this pool
     */
    public int getPoolSize() {
        return workers.length;
    }

    /**
     * Sets the handler for internal worker threads that terminate due
     * to unrecoverable errors encountered while executing tasks.
     * Unless set, the current default or ThreadGroup handler is used
     * as handler.
     *
     * @param h the new handler
     * @return the old handler, or null if none
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     */
    public Thread.UncaughtExceptionHandler
        setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler h) {
        checkPermission();
        final ReentrantLock lock = this.workerLock;
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
     * Returns the handler for internal worker threads that terminate
     * due to unrecoverable errors encountered while executing tasks.
     * @return the handler, or null if none
     */
    public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler h;
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            h = ueh;
        } finally {
            lock.unlock();
        }
        return h;
    }

    /**
     * Adds a new worker thread to the pool. This method may be used
     * to increase the amount of parallelism available to tasks.
     * @throws IllegalStateException if pool is terminating or
     * terminated
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     */
    public void addWorker() {
        doAddWorker(null, false);
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
     * terminated
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     */
    public <T> Future<T> submitUsingAddedWorker(ForkJoinTask<T> task,
                                                boolean exitOnCompletion) {
        if (task == null)
            throw new NullPointerException();
        Submission<T> job = new Submission<T>(task, this);
        ForkJoinWorkerThread w = doAddWorker(job, exitOnCompletion);
        return job;
    }

    /**
     * Shared code for addWorker and variants
     */
    private ForkJoinWorkerThread doAddWorker(Submission<?> job,
                                             boolean exitOnCompletion) {
        checkPermission();
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            if (runState.isAtLeastStopping())
                throw new IllegalStateException();
            ForkJoinWorkerThread[] ws = workers;
            int len = ws.length;
            int newlen = len + 1;
            ForkJoinWorkerThread[] nws = new ForkJoinWorkerThread[newlen];
            System.arraycopy(ws, 0, nws, 0, len);
            ForkJoinWorkerThread w = createWorker(len);
            if (job != null) {
                w.setFirstTask(job, exitOnCompletion);
                runningSubmissions.incrementAndGet();
            }
            nws[len] = w;
            workers = nws;
            startWorker(w);
            eventBarrier.signal();
            return w;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a worker thread from the pool. The worker will
     * exit the next time it is idle. This method may be used
     * to decrease the amount of parallelism available to tasks.
     * @throws IllegalStateException if pool is terminating or
     * terminated or the poolSize would become zero.
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     */
    public void removeWorker() {
        checkPermission();
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            if (runState.isAtLeastStopping())
                throw new IllegalStateException();
            ForkJoinWorkerThread[] ws = workers;
            int newlen = ws.length - 1;
            if (newlen <= 0)
                throw new IllegalStateException();
            ForkJoinWorkerThread w = ws[newlen];
            ForkJoinWorkerThread[] nws = new ForkJoinWorkerThread[newlen];
            System.arraycopy(ws, 0, nws, 0, newlen);
            workers = nws;
            if (w != null) {
                RunState rs = w.getRunState();
                rs.transitionToShutdown();
            }
            eventBarrier.signal();
        } finally {
            lock.unlock();
        }
    }

    // lifecycle control

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     * Tasks that are in the process of being submitted concurrently
     * during the course of this method may or may not be rejected.
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     */
    public void shutdown() {
        checkPermission();
        runState.transitionToShutdown();
        tryTerminateOnShutdown();
    }

    /**
     * Attempts to stop all actively executing tasks, and cancels all
     * waiting tasks.  Tasks that are in the process of being
     * submitted or executed concurrently during the course of this
     * method may or may not be rejected.
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     */
    public void shutdownNow() {
        checkPermission();
        terminate();
    }

    /**
     * Returns <tt>true</tt> if this pool has been shut down.
     *
     * @return <tt>true</tt> if this pool has been shut down
     */
    public boolean isShutdown() {
        return runState.isAtLeastShutdown();
    }

    /**
     * Returns <tt>true</tt> if all tasks have completed following shut down.
     *
     * @return <tt>true</tt> if all tasks have completed following shut down
     */
    public boolean isTerminated() {
        return runState.isTerminated();
    }

    /**
     * Returns <tt>true</tt> if termination has commenced but has
     * not yet completed.
     *
     * @return <tt>true</tt> if in the process of terminating
     */
    public boolean isTerminating() {
        return runState.isStopping();
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
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            for (;;) {
                if (runState.isTerminated())
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
     * Initiate termination.
     */
    private void terminate() {
        if (runState.transitionToStopping()) {
            stopAllWorkers();
            cancelQueuedSubmissions();
            cancelQueuedWorkerTasks();
            interruptUnterminatedWorkers();
        }
    }

    /**
     * Check for termination in shutdown state
     */
    private void tryTerminateOnShutdown() {
        if (runState.isAtLeastShutdown() &&
            activeWorkerCounter.get() == 0 &&
            runningSubmissions.get() == 0 &&
            submissionQueue.isEmpty())
            terminate();
    }

    /**
     * Clear out and cancel submissions
     */
    private void cancelQueuedSubmissions() {
        Submission<?> task;
        while ((task = submissionQueue.poll()) != null)
            task.cancel(false);
    }

    /**
     * Clean out worker queues.
     */
    private void cancelQueuedWorkerTasks() {
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            for (int i = 0; i < ws.length; ++i) {
                ForkJoinWorkerThread t = ws[i];
                if (t != null)
                    t.cancelTasks();
            }
            eventBarrier.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set each worker's status to stopping. Requires lock to avoid
     * conflicts with add/remove
     */
    private void stopAllWorkers() {
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            for (int i = 0; i < ws.length; ++i) {
                ForkJoinWorkerThread t = ws[i];
                if (t != null) {
                    RunState rs = t.getRunState();
                    rs.transitionToStopping();
                }
            }
            eventBarrier.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Interrupt all unterminated workers.  This is not required for
     * sake of internal control, but may help unstick user code during
     * shutdown.
     */
    private void interruptUnterminatedWorkers() {
        ForkJoinWorkerThread[] ws = workers;
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null) {
                RunState rs = t.getRunState();
                if (!rs.isTerminated()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    }
                }
            }
        }
    }

    // Status queries

    /**
     * Returns true if all worker threads are currently idle. An idle
     * worker is one that cannot obtain a task to execute because none
     * are available to steal from other threads, and there are no
     * pending submissions to the pool. This method is conservative:
     * It might not return true immediately upon idleness of all
     * threads, but will eventually become true if threads remain
     * inactive.
     * @return true if all threads are currently idle
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
     * queue by another. This value is only an approximation, obtained
     * by iterating across all threads in the pool, but may be useful
     * for monitoring and tuning fork/join programs: In general, steal
     * counts should be high enough to keep threads busy, but low
     * enough to avoid overhead and contention across threads.
     * @return the number of steals.
     */
    public long getStealCount() {
        long sum = 0;
        ForkJoinWorkerThread[] ws = workers;
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                sum += t.getWorkerStealCount();
        }
        return sum;
    }

    /**
     * Returns the total number of times that worker threads have
     * globally synchronized (and possibly suspended) because there
     * are no tasks available to execute.  This value is only an
     * approximation, obtained by iterating across all threads in the
     * pool. The reported value means little in isolation, but can be
     * useful for monitoring and tuning fork/join programs: Smaller
     * values indicate less stalling of threads while waiting for each
     * other, which generally corresponds to better throughput.
     * @return the number of synchronizations
     */
    public long getSyncCount() {
        long sum = 0;
        ForkJoinWorkerThread[] ws = workers;
        for (int i = 0; i < ws.length; ++i) {
            ForkJoinWorkerThread t = ws[i];
            if (t != null)
                sum += t.getWorkerSyncCount();
        }
        return sum;
    }

    /**
     * Returns the total number of tasks currently held in queues by
     * worker threads (but not including tasks submitted to the pool
     * that have not begun executing). This value is only an
     * approximation, obtained by iterating across all threads in the
     * pool. This method may be useful for tuning task granularities.
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
     * execution. Note that this method may be relatively slow
     * because it may require a traveral of elements to compute size.
     * You should prefer <tt>hasQueuedSubmissions</tt> when it 
     * suffices.
     * @return the number of tasks.
     */
    public int getQueuedSubmissionCount() {
        return submissionQueue.size();
    }

    /**
     * Returns true if there are any tasks submitted to this pool
     * that have not yet begun executing.
     * @return <tt>true</tt> if there are any queued submissions.
     */
    public boolean hasQueuedSubmissions() {
        return !submissionQueue.isEmpty();
    }

    /**
     * Returns the number of tasks that have been submitted (via
     * <tt>submit</tt> or <tt>invoke</tt>) and are currently executing
     * in the pool.
     * @return the number of tasks.
     */
    public int getActiveSubmissionCount() {
        return runningSubmissions.get();
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
     * Return counter (cached by workers)
     */
    final EventBarrier getEventBarrier() {
        return eventBarrier;
    }

    /**
     * Completion callback from externally submitted job.
     */
    final void submissionCompleted() {
        if (runningSubmissions.decrementAndGet() == 0)
            tryTerminateOnShutdown();
    }

    /**
     * Return a submission, or null if none
     */
    final Submission<?> pollSubmission() {
        Submission<?> task = submissionQueue.poll();
        if (task != null)
            runningSubmissions.incrementAndGet();
        return task;
    }

    /**
     * Callback from terminating worker.
     * @param w the worker
     * @param ex the exception causing abrupt termination, or null if
     * completed normally
     */
    final void workerTerminated(ForkJoinWorkerThread w) {
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            ForkJoinWorkerThread[] ws = workers;
            int idx = w.getWorkerPoolIndex();
            if (idx >= 0 && idx < ws.length)
                ws[idx] = null;
            if (--runningWorkers == 0) {
                terminate();
                runState.transitionToTerminated();
                termination.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * A variant of a cyclic barrier that is advanced upon
     * signals. Uses a custom Michael & Scott linked queue for wait
     * nodes to reduce blocking and speed up signalling compared to
     * lock-based strategies. Threads waiting for next event are kept
     * in FIFO order. Signals increment count and then release all
     * with incoming values less than count. To maintain ordering
     * across generations, threads cannot wait if there are still
     * others waiting for their incoming counts, but instead help wake
     * them up.
     */
    static final class EventBarrier {
        /*
         * Wait queue nodes. Opportunistically subclasses
         * AtomicReference to represent next-fields.
         */
        static final class QNode extends AtomicReference<QNode> {
            Thread thread;
            final long incomingCount;
            QNode(Thread t, long c) {
                thread = t;
                incomingCount = c;
            }
        }

        private final AtomicLong counter;
        private final AtomicReference<QNode> head;
        private final AtomicReference<QNode> tail;

        EventBarrier() {
            counter = new AtomicLong();
            QNode dummy = new QNode(null, 0);
            tail = new AtomicReference<QNode>(dummy);
            head = new AtomicReference<QNode>(dummy);
        }

        /**
         * Advance counter, waking up all waiting for change
         * to new value.
         */
        final void signal() {
            long count = counter.incrementAndGet();
            final AtomicReference<QNode> head = this.head;
            final AtomicReference<QNode> tail = this.tail;
            for (;;) {
                QNode h = head.get();
                QNode t = tail.get();
                QNode first = h.get();
                if (h == head.get()) {
                    if (first == null || count <= first.incomingCount)
                        return;
                    else if (h == t)
                        tail.compareAndSet(t, first);
                    else if (head.compareAndSet(h, first))
                        LockSupport.unpark(first.thread);
                }
            }
        }

        /**
         * Possibly block until counter is advanced from given value.
         *
         * @param count incoming count
         * @return count upon invocation of this method.
         */
        final long await(long count) {
            final AtomicLong ctr = counter;
            long current = ctr.get();
            if (count == current && !releaseOneWaiter(count)) {
                final AtomicReference<QNode> tail = this.tail;
                QNode s = new QNode(Thread.currentThread(), count);
                while (count == ctr.get()) {
                    QNode t = tail.get();
                    QNode last = t.get();
                    if (last != null)
                        tail.compareAndSet(t, last);
                    else if (t.compareAndSet(null, s)) {
                        tail.compareAndSet(t, s);
                        if (count == ctr.get())
                            LockSupport.park();     // can only park once
                        s.thread = null;            // avoid useless unparks
                        break;
                    }
                }
            }
            return current;
        }

        /**
         * Dequeue and signal at most one node with incomingCount less
         * than given count. This helps ensure progress when threads
         * cannot wait yet because previous wakeups have stalled, but
         * stopping with at most one per call avoids further wait
         * queue contention.
         * @return true if a node released
         */
        private boolean releaseOneWaiter(long count) {
            final AtomicReference<QNode> head = this.head;
            final AtomicReference<QNode> tail = this.tail;
            for (;;) {
                QNode h = head.get();
                QNode t = tail.get();
                QNode first = h.get();
                if (h == head.get()) {
                    if (first == null || count <= first.incomingCount)
                        return false;
                    else if (h == t)
                        tail.compareAndSet(t, first);
                    else if (head.compareAndSet(h, first)) {
                        LockSupport.unpark(first.thread);
                        return true;
                    }
                }
            }
        }
    }

    /**
     * Maintains lifecycle control for pool (also workers)
     */
    static final class RunState extends AtomicInteger {
        static final int RUNNING    = 0;
        static final int SHUTDOWN   = 1;
        static final int STOPPING   = 2;
        static final int TERMINATED = 4;

        boolean isRunning()              { return get() == RUNNING; }
        boolean isShutdown()             { return get() == SHUTDOWN; }
        boolean isStopping()             { return get() == STOPPING; }
        boolean isTerminated()           { return get() == TERMINATED; }
        boolean isAtLeastShutdown()      { return get() >= SHUTDOWN; }
        boolean isAtLeastStopping()      { return get() >= STOPPING; }
        boolean transitionToShutdown()   { return transitionTo(SHUTDOWN); }
        boolean transitionToStopping()   { return transitionTo(STOPPING); }
        boolean transitionToTerminated() { return transitionTo(TERMINATED); }

        /**
         * Transition to at least the given state. Return true if not
         * already at least given state.
         */
        boolean transitionTo(int state) {
            for (;;) {
                int s = get();
                if (s >= state)
                    return false;
                if (compareAndSet(s, state))
                    return true;
            }
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
     * worker threads. The submissionCompleted method invoked when
     * compute() completes performs the associated bookkeeping.
     */
    static final class Submission<T> extends RecursiveTask<T> implements Future<T> {
        private final ForkJoinTask<T> task;
        private final ForkJoinPool pool;
        private final ReentrantLock lock;
        private final Condition ready;
        Submission(ForkJoinTask<T> t, ForkJoinPool p) {
            lock = new ReentrantLock();
            ready = lock.newCondition();
            pool = p;
            task = t;
            t.setStolen(); // external tasks always treated as stolen
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

        /**
         * ForkJoinTask version of cancel
         */
        public void cancel() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                task.cancel();
                // avoid recursive call to cancel
                finishExceptionally(new CancellationException());
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
            T res = t.getResult();
            Throwable ex = t.getException();
            if (ex != null) {
                if (ex instanceof CancellationException)
                    throw (CancellationException)ex;
                else
                    throw new ExecutionException(ex);
            }
            return res;
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
                return futureResult(t);
            } finally {
                lock.unlock();
            }
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
                T res = t.getResult();
                Throwable ex = t.getException();
                if (ex != null)
                    ForkJoinTask.rethrowException(ex);
                return res;
            } finally {
                lock.unlock();
            }
        }
    }
}
