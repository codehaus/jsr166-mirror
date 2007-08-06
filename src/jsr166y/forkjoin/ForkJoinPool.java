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
 * from non-ForkJoinTasks, as well as management and monitoring
 * operations.  Normally a single ForkJoinPool is used for a large
 * number of submitted tasks. Otherwise, use would not always outweigh
 * the construction overhead of creating a large set of threads
 * and the associated startup bookkeeping.
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
 * class provides status check methods (for example
 * <tt>getStealCount</tt>) that are intended to aid in developing,
 * tuning, and monitoring fork/join applications.
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
     * kill threads.
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
     * The current targetted pool size. Updated only under worker lock
     * but volatile to allow concurrent reads.
     */
    private volatile int poolSize;

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
     * to find some, they may wait for next count. Synchronization
     * events occur only in enough contexts to maintain overall
     * liveness:
     *
     *   - Submission of a new task
     *   - Termination of pool or worker
     *   - A worker pushing a task on an empty per-worker queue
     *   - A worker completing a stolen or cancelled task
     *
     * So, signals and waits occur relatively rarely during normal
     * processing, which minimizes contention on this global
     * synchronizer. Even so, the EventBarrier is designed to minimize
     * blockages by threads that have better things to do.
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
     * Create new worker using factory.
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
        activeWorkerCounter.incrementAndGet(); // initially active state
        return w;
    }

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
        this.poolSize = poolSize;
        this.factory = factory;
        this.poolNumber = poolNumberGenerator.incrementAndGet();
        this.eventBarrier = new EventBarrier();
        this.submissionQueue = new ConcurrentLinkedQueue<Submission<?>>();
        this.activeWorkerCounter = new AtomicInteger();
        this.runningSubmissions = new AtomicInteger();
        this.runState = new RunState();
        this.workerLock = new ReentrantLock();
        this.termination = workerLock.newCondition();
        ForkJoinWorkerThread[] ws = new ForkJoinWorkerThread[poolSize];
        this.workers = ws;

        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            for (int i = 0; i < ws.length; ++i) 
                ws[i] = createWorker(i);
            for (int i = 0; i < ws.length; ++i) {
                ws[i].start();
                ++runningWorkers;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Performs the given task; returning its result upon completion
     * @param task the task
     * @return the task's result
     * @throws NullPointerException if task is null
     * @throws RejectedExecutionException if pool is shut down
     */
    public <T> T invoke(ForkJoinTask<T> task) {
        return doSubmit(task).awaitInvoke();
    }
    
    /**
     * Arranges for (asynchronous) execution of the given task.
     * @param task the task
     * @return a Future that can be used to get the task's results.
     * @throws NullPointerException if task is null
     * @throws RejectedExecutionException if pool is shut down
     */
    public <T> Future<T> submit(ForkJoinTask<T> task) {
        return doSubmit(task);
    }

    /**
     * Common code for invoke and submit
     */
    private <T> Submission<T> doSubmit(ForkJoinTask<T> task) {
        if (task == null)
            throw new NullPointerException();
        if (runState.isAtLeastShutdown())
            throw new RejectedExecutionException();
        Submission<T> job = new Submission<T>(task, this);
        submissionQueue.add(job);
        eventBarrier.poolSignal();
        return job;
    }

    /**
     * Returns the targetted number of worker threads in this pool.
     * This value does not necessarily reflect transient changes as
     * threads are added, removed, or abruptly terminate.
     *
     * @return the number of worker threads in this pool
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Returns the number of worker threads that have started but not
     * yet terminated.  This result returned by this method may differ
     * from <tt>getPoolSize</tt> when threads are added, removed, or
     * abruptly terminate.
     *
     * @return the number of worker threads
     */
    public int getRunningWorkerCount() {
        int r;
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            r = runningWorkers;
        } finally {
            lock.unlock();
        }
        return r;
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
        Thread.UncaughtExceptionHandler old = null;
        final ReentrantLock lock = this.workerLock;
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
        doAddWorker(job, exitOnCompletion);
        return job;
    }

    /**
     * Shared code for addWorker and variants
     */
    private void doAddWorker(Submission<?> job,
                             boolean exitOnCompletion) {
        checkPermission();
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            if (runState.isAtLeastStopping())
                throw new IllegalStateException();
            ForkJoinWorkerThread[] ws = workers;
            int len = ws.length;
            ForkJoinWorkerThread w = createWorker(len);
            if (job != null)
                w.setFirstTask(job, exitOnCompletion);
            ForkJoinWorkerThread[] nws = new ForkJoinWorkerThread[len+1];
            System.arraycopy(ws, 0, nws, 0, len);
            nws[len] = w;
            workers = nws;
            w.start();
            ++runningWorkers;
            ++poolSize;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Tries to remove a worker thread from the pool. The worker will
     * exit the next time it is idle. This method may be used
     * to decrease the amount of parallelism available to tasks.
     * @return true if a worker was removed, or false if the pool is
     * terminating or terminated or the pool size would become zero.
     * @throws SecurityException if a security manager exists and
     *         the caller is not permitted to modify threads
     *         because it does not hold {@link
     *         java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     */
    public boolean removeWorker() {
        boolean removed = false;
        checkPermission();
        final ReentrantLock lock = this.workerLock;
        lock.lock();
        try {
            if (!runState.isAtLeastStopping()) {
                // shutdown the rightmost running worker
                // (rightmost enables shrinkage in workerTerminated)
                ForkJoinWorkerThread[] ws = workers;
                int k = ws.length;
                while (--k > 0) { // don't kill ws[0]
                    ForkJoinWorkerThread w = ws[k];
                    if (w != null) {
                        RunState rs = w.getRunState();
                        if (rs.transitionToShutdown()) {
                            --poolSize;
                            removed = true;
                            break;
                        }
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return removed;
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
            if (!runState.isAtLeastStopping()) { // don't bother if stopping
                // Null slot, and if end slots now null, shrink
                int idx = w.getWorkerPoolIndex();
                ForkJoinWorkerThread[] ws = workers;
                int len = ws.length;
                if (idx >= 0 && idx < len && ws[idx] == w) {
                    ws[idx] = null;
                    int newlen = len;
                    while (newlen > 0 && ws[newlen-1] == null)
                        --newlen;
                    if (newlen < len) {
                        ForkJoinWorkerThread[] nws = 
                            new ForkJoinWorkerThread[newlen];
                        System.arraycopy(ws, 0, nws, 0, newlen);
                        workers = nws;
                        eventBarrier.signal();
                    }
                }
            }
            if (--runningWorkers == 0) {
                terminate(); // no-op if already stopping
                runState.transitionToTerminated();
                termination.signalAll();
            }
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
        } finally {
            lock.unlock();
        }
        eventBarrier.poolSignal();
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
    public final boolean isQuiescent() {
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
        return submissionQueue.peek() == null;
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

    // Methods callable from workers.

    /**
     * Return current workers array (not cached by workers)
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
     * Return a submission, or null if none
     */
    final Submission<?> pollSubmission() {
        return submissionQueue.poll();
    }

    /**
     * A variant of a cyclic barrier that is advanced upon explicit
     * signals representing event occurrences.
     */
    static final class EventBarrier {
        /**
         * Wait nodes for Treiber stack representing wait queue.
         */
        static final class EQNode {
            EQNode next;
            volatile ForkJoinWorkerThread thread; // nulled to cancel wait
            final long count;
            EQNode(ForkJoinWorkerThread t, long c) {
                thread = t;
                count = c;
            }
        }

        /**
         * Head of Treiber stack. Even though this variable is very
         * busy, it is not usually heavily contended because of
         * signal/wait/release policies.
         */
        final AtomicReference<EQNode> head = new AtomicReference<EQNode>();

        /**
         * The event count
         */
        final AtomicLong counter = new AtomicLong();

        /**
         * Returns the current event count
         */
        long getCount() {
            return counter.get();
        }

        /**
         * Waits until event count advances from count, or some
         * other thread arrives or is already waiting with a different
         * count.
         * @param count previous value returned by sync (or 0)
         * @return current event count
         */
        long sync(ForkJoinWorkerThread thread, long count) {
            long current = counter.get();
            if (current == count) 
                enqAndWait(thread, count);
            if (head.get() != null)
                releaseAll();
            return current;
        }

        /**
         * Ensures that event count on exit is greater than event
         * count on entry, and that at least one thread waiting for
         * count to change is signalled, (It will then in turn
         * propagate other wakeups.) This lessens stalls by signallers
         * when they want to be doing something more productive.
         * However, on contention to release, wakes up all threads to
         * help forestall further contention.  Note that the counter
         * is not necessarily incremented by caller.  If attempted CAS
         * fails, then some other thread already advanced from
         * incoming value;
         */
        void signal() {
            final AtomicLong counter = this.counter;
            long c = counter.get();
            counter.compareAndSet(c, c+1); 
            final AtomicReference<EQNode> head = this.head;
            EQNode h = head.get();
            if (h != null) {
                ForkJoinWorkerThread t;
                if (head.compareAndSet(h, h.next) &&
                    (t = h.thread) != null) {
                    h.thread = null;
                    LockSupport.unpark(t);
                }
                else if (head.get() != null)
                    releaseAll();
            }
        }

        /**
         * Version of signal called from pool. Forces increment and
         * releases all. It is OK if this is called by worker threads,
         * but it is heavier than necessary for them.
         */
        void poolSignal() {
            counter.incrementAndGet();
            releaseAll();
        }

        /**
         * Enqueues node and waits unless aborted or signalled.
         */
        private void enqAndWait(ForkJoinWorkerThread thread, long count) {
            EQNode node = new EQNode(thread, count);
            final AtomicReference<EQNode> head = this.head;
            final AtomicLong counter = this.counter;
            for (;;) {
                EQNode h = head.get();
                node.next = h;
                if ((h != null && h.count != count) || 
                    counter.get() != count)
                    break;
                if (head.compareAndSet(h, node)) {
                    while (!thread.isInterrupted() &&
                           node.thread != null &&
                           counter.get() == count)
                        LockSupport.park();
                    node.thread = null;
                    break;
                }
            }
        }

        /**
         * Release all waiting threads. Called on exit from sync, as
         * well as on contention in signal. Regardless of why sync'ing
         * threads exit, other waiting threads must also recheck for
         * tasks or completions before resync. Release by chopping off
         * entire list, and then signalling. This both lessens
         * contention and avoids unbounded enq/deq races.
         */
        private void releaseAll() {
            final AtomicReference<EQNode> head = this.head;
            EQNode p;
            while ( (p = head.get()) != null) {
                if (head.compareAndSet(p, null)) {
                    do {
                        ForkJoinWorkerThread t = p.thread;
                        if (t != null) {
                            p.thread = null;
                            LockSupport.unpark(t);
                        }
                    } while ((p = p.next) != null);
                    break;
                }
            }
        }
    }
                
    /**
     * Callback on starting execution of externally submitted job.
     * @return true if task can execute
     */
    final boolean submissionStarting() {
        if (runState.isAtLeastStopping())
            return false;
        runningSubmissions.incrementAndGet();
        return true;
    }

    /**
     * Completion callback from externally submitted job.
     */
    final void submissionCompleted() {
        if (runningSubmissions.decrementAndGet() == 0 &&
            runState.isAtLeastShutdown())
            tryTerminateOnShutdown();
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
     * worker threads. The submissionStarting and submissionCompleted
     * methods perform the associated bookkeeping.
     */
    static final class Submission<T> extends RecursiveTask<T> 
        implements Future<T> {

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
                    finishExceptionally(new CancellationException());
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
                if (started || !pool.submissionStarting())
                    return false;
                started = true;
                return true;
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
                finishExceptionally(new CancellationException());
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

    /**
     * Maintains lifecycle control for pool (also workers)
     */
    static final class RunState extends AtomicInteger {
        // Order among values matters
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

}
