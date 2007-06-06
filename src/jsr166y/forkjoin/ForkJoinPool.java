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
 * from non-ForkJoinTasks.
 *
 * <p> Class ForkJoinPool does not implement the ExecutorService
 * interface because it only executes ForkJoinTasks, not arbitrary
 * Runnables. However, for the sake of uniformity, it supports all
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
     * Main lock protecting access to threads and run state. You might
     * think that having a single lock and condition here wouldn't
     * work so well. But serializing the starting and stopping of
     * worker threads (its main purpose) helps enough in controlling
     * startup effects, contention vs dynamic compilation, etc to be
     * better than alternatives.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Condition triggered when new work is available so workers
     * should awaken if blocked. Also used for timed waits when work
     * is known to be available but repeated steal attempts fail
     */
    private final Condition work = lock.newCondition();


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
     * The pool of threads. Currently, all threads are created
     * upon construction. However, all usages of workers array
     * are prepared to see null entries, allowing alternative
     * schemes in which workers are added more lazily.
     */
    private final Worker[] workers;

    /**
     * External submission queue.  "Jobs" are tasks submitted to the
     * pool, not internally generated.
     */
    private final JobQueue jobs = new JobQueue();

    /**
     * The number of jobs that are currently executing in the pool.
     * This does not include those jobs that are still in job queue
     * waiting to be taken by workers.
     */
    int activeJobs = 0;

    /** The number of workers that have not yet terminated */
    private int runningWorkers = 0;

    /**
     * Condition for awaitTermination. Triggered when
     * activeWorkers reaches zero.
     */
    private final Condition termination = lock.newCondition();

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
     * Maximum number of active jobs allowed to run;
     */
    private int maxActive;

    /**
     * Creates a ForkJoinPool with a pool size equal to the number of
     * processors available on the system.
     */
    public ForkJoinPool() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a ForkJoinPool with the indicated number
     * of Worker threads.
     */
    public ForkJoinPool(int poolSize) {
        if (poolSize <= 0) throw new IllegalArgumentException();
        maxActive = poolSize;
        workers = new Worker[poolSize];
        lock.lock();
        try {
            for (int i = 0; i < poolSize; ++i) {
                Worker r = new Worker(this, i);
                workers[i] = r;
                r.start();
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
     */
    public <T> T invoke(ForkJoinTask<T> task) {
        Job<T> job = new Job<T>(task, this);
        addJob(job);
        return job.awaitInvoke();
    }

    /**
     * Arranges for (asynchronous) execution of the given task.
     * @param task the task
     * @return a Future that can be used to get the task's results.
     */
    public <T> Future<T> submit(ForkJoinTask<T> task) {
        Job<T> job = new Job<T>(task, this);
        addJob(job);
        return job;
    }

    /**
     * Returns the policy for whether to continue execution when a
     * worker thread aborts due to Error. If false, the pool shots
     * down upon error. The default is false.  If true, dying workers
     * are replaced with fresh threads with empty work queues.
     * @return true if pool should continue when one thread aborts
     */
    public boolean getContinueOnErrorPolicy() {
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
     * is may be in some cases be preferable to install a handler to
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
            for (int i = 0; i < workers.length; ++i) {
                Worker w = workers[i];
                if (w != null)
                    w.setUncaughtExceptionHandler(h);
            }
        } finally {
            lock.unlock();
        }
        return old;
    }

    /**
     * Returns the number of worker threads in this pool.
     *
     * @return the number of worker threads in this pool
     */
    public int getPoolSize() {
        return workers.length;
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
                if (activeJobs > 0 || !jobs.isEmpty())
                    runState = SHUTDOWN;
                else
                    tryTerminate();
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
            tryTerminate();
        } finally {
            lock.unlock();
        }
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

    private void tryTerminate() { // called only under lock
        if (runState < STOP) {
            runState = STOP;
            ForkJoinTask<?> task;
            while ((task = jobs.poll()) != null)
                task.cancel();
            for (int i = 0; i < workers.length; ++i) {
                Worker t = workers[i];
                if (t != null) {
                    t.cancelTasks();
                    t.interrupt();
                }
            }
            work.signalAll();
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
        for (int i = 0; i < workers.length; ++i) {
            Worker t = workers[i];
            if (t != null)
                sum += t.stealCount;
        }
        return sum;
    }

    /**
     * Returns the number of threads that are not currently idle
     * waiting for tasks. This value is only an approximation,
     * obtained by iterating across all threads in the pool.
     * @return the number of active threads.
     */
    public int getActiveThreadCount() {
        int count = 0;
        for (int i = 0; i < workers.length; ++i) {
            Worker t = workers[i];
            if (t != null && t.isActive())
                ++count;
        }
        return count;
    }


    /**
     * Returns true if all threads are currently idle.
     * @return true is all threads are currently idle
     */
    public boolean isQuiescent() {
        for (int i = 0; i < workers.length; ++i) {
            Worker t = workers[i];
            if (t != null && t.isActive())
                return false;
        }
        return true;
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
        for (int i = 0; i < workers.length; ++i) {
            Worker t = workers[i];
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
            return jobs.size();
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
            return activeJobs;
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
            return maxActive;
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
            maxActive = max;
        } finally {
            lock.unlock();
        }
    }


    /**
     * Adapter class to allow submitted jobs to act as Futures.  This
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
     * (3) External jobs are tracked for the sake of managing worker
     * threads -- when there are no jobs, workers are blocked, but
     * otherwise they scan looking for work. The jobCompleted method
     * invoked when compute() completes performs the associated
     * bookkeeping.
     */
    static final class Job<T> extends RecursiveTask<T> implements Future<T> {
        final ForkJoinTask<T> task;
        final ForkJoinPool pool;
        final ReentrantLock lock;
        final Condition ready;
        Job(ForkJoinTask<T> t, ForkJoinPool p) {
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
            final ReentrantLock lock = this.lock;
            lock.lock();
            ready.signalAll();
            lock.unlock();
            pool.jobCompleted();
        }

        public boolean cancel(boolean ignore) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                task.cancel();
                return isCancelled();
            } finally {
                lock.unlock();
            }
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
     * A JobQueue is a simple array-based circular queue.
     * Basically a stripped-down variant of ArrayDeque
     */
    static final class JobQueue {
        static final int INITIAL_JOBQUEUE_CAPACITY = 64;
        Job<?>[] elements = (Job<?>[]) new Job[INITIAL_JOBQUEUE_CAPACITY];
        int head;
        int tail;

        int size() {
            return (tail - head) & (elements.length - 1);
        }

        boolean isEmpty() {
            return head == tail;
        }

        void add(Job<?> e) {
            elements[tail] = e;
            if ( (tail = (tail + 1) & (elements.length - 1)) == head)
                doubleCapacity();
        }

        Job<?> poll() {
            int h = head;
            Job<?> result = elements[h];
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
                throw new IllegalStateException("Job queue capacity exceeded");
            Job<?>[] a = (Job<?>[]) new Job[newCapacity];
            System.arraycopy(elements, p, a, 0, r);
            System.arraycopy(elements, 0, a, r, p);
            elements = a;
            head = 0;
            tail = n;
        }
    }


    // Internal methods that may be invoked by workers

    final boolean isStopped() {
        return runState >= STOP;
    }

    /**
     * Return the workers array; needed for random-steal by Workers.
     */
    final Worker[] getWorkers() {
        return workers;
    }

    /**
     * Completion callback from externally submitted job.
     */
    final void jobCompleted() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (--activeJobs <= 0 && runState == SHUTDOWN && jobs.isEmpty())
                tryTerminate();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Enqueue an externally submitted task
     */
    private void addJob(Job<?> job) {
        final ReentrantLock lock = this.lock;
        boolean ok;
        lock.lock();
        try {
            if (ok = (runState == RUNNING)) {
                jobs.add(job);
                work.signalAll();
            }
        } finally {
            lock.unlock();
        }
        if (!ok)
            throw new RejectedExecutionException();
    }

    /**
     * Returns a job to run, or null if none available.
     * @param w calling worker
     */
    final ForkJoinTask<?> getJob(Worker w) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            ForkJoinTask<?> task = null;
            if (activeJobs < maxActive) {
                task = jobs.poll();
                if (task == null && activeJobs == 0) {
                    w.setIdle(); // needed for quiescence detection
                    work.await();
                    if (activeJobs < maxActive)
                        task = jobs.poll();
                }
            }
            if (task != null) {
                ++activeJobs;
                work.signalAll();
            }
            return task;
        } catch (InterruptedException ie) {
            return null; // ignore/swallow interrupt
        } finally {
            lock.unlock();
        }
    }


    /**
     * Termination callback from dying worker.
     */
    final void workerTerminated(Worker r, int index) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (!continueOnError)
                tryTerminate();

            if (runState >= STOP) {
                if (--runningWorkers <= 0) {
                    runState = TERMINATED;
                    termination.signalAll();
                }
            }

            else if (continueOnError &&
                     index >= 0 && index < workers.length &&
                     workers[index] == r) {
                Worker replacement = new Worker(this, index);
                if (ueh != null)
                    replacement.setUncaughtExceptionHandler(ueh);
                workers[index] = replacement;
                replacement.start();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Workers repeatedly get tasks and run them.
     *
     * Each Worker thread maintains a work-stealing queue.
     * Work-stealing queues are special forms of Deques that support
     * only three of the four possible end-operations -- push, pop,
     * and steal (aka dequeue), and only do so under the constraints
     * that push and pop are called only from the owning thread, while
     * steal is called from other threads.  The work-stealing queue
     * here uses a variant of the algorithm described in "Dynamic
     * Circular Work-Stealing Deque" by David Chase and Yossi Lev,
     * SPAA 2005. For an explanation, read the paper.
     * http://research.sun.com/scalable/pubs/index.html The main
     * differences here stem from ensuring that deq slots referencing
     * popped and stolen tasks are cleared. (Also. method and variable
     * names differ.)
     *
     * The Worker class also includes support for the main
     * functionality of ForkJoinTask methods.  It is more efficient to
     * implement them here rather than requiring ForkJoinTasks to
     * repeatedly relay to worker methods via class-cast of
     * currentThread.
     */
    static final class Worker extends Thread {
        /**
         * The pool. This class could alternatively be an inner class,
         * but the reference is left explicit to make it easier to see
         * and check which calls are local to threads vs global to
         * pool.
         */
        final ForkJoinPool pool;

        /**
         * The array used as work-stealing queue. Length must always
         * be a power of two. Even though these queues rarely become
         * all that big, the initial size must be large enough to
         * counteract cache contention effects across multiple queues.
         * Currently, they are initialized to an empirically
         * reasonable size when a Worker gets its first task, which
         * improves chances of being allocated far away from
         * task arrays used by other threads. However, all
         * queue-related methods are written so that they could
         * alternatively be lazily allocated (see growAndPushTask)
         * and/or disposed of when empty.
         */
        private ForkJoinTask<?>[] tasks;

        private static final int INITIAL_CAPACITY = 1 << 13;
        private static final int MAXIMUM_CAPACITY = 1 << 30;

        /**
         * Index (mod tasks.length) of least valid slot, which
         * is always the next position to steal from if nonempty.
         */
        private volatile long base;

        /**
         * Index (mod tasks.length-1) of next position to push to
         * or pop from
         */
        private volatile long sp;

        /**
         * Updater to allow CAS of base index. Even though this slows
         * down CASes of base, they are relatively infrequent,
         * so the better locality of having sp and base close
         * to each other normally outweighs this.
         */
        static final AtomicLongFieldUpdater<Worker> baseUpdater =
            AtomicLongFieldUpdater.newUpdater(Worker.class, "base");

        /**
         * Index (mod tasks.length) of the next slot to null
         * out. Tasks slots stolen by other threads cannot be safely
         * nulled out by them. Instead, they are cleared by owning
         * thread whenever queue is empty.
         */
        private long nextSlotToClean;

        /**
         * Index of this worker in pool array. Needed for replacement
         * upon uncaught exceptions.
         */
        final int index;

        /**
         * Seed for random number generator in randomIndex
         */
        private int randomSeed;

        /**
         * Number of steals
         */
        int stealCount;

        /**
         * Number of scans since last successfully getting a task.
         * Always zero when busy executing tasks. Incremented after a
         * failed scan in getTask().  When maybeSleeping(scanCtl) is
         * true, this worker sleeps for a while.
         */
        private volatile int scanCtl;

        /**
         * Updater for scanCtl to control wakeups
         */
        static final AtomicIntegerFieldUpdater<Worker> scanCtlUpdater =
            AtomicIntegerFieldUpdater.newUpdater(Worker.class, "scanCtl");

        /**
         * Padding to avoid cache-line sharing across workers
         */
        int p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, pa, pb, pc, pd, pe, pf;

        /**
         * The number of times to scan for tasks before
         * yileding/sleeping (and thereafter, between sleeps).  Must
         * be a power of two minus 1. Using short sleeps during times
         * when tasks should be available but aren't makes these
         * threads cope better with lags due to GC, dynamic
         * compilation, queue resizing, and multitasking.
         */
        private static final int SCANS_PER_SLEEP = (1 << 6) - 1;

        /**
         * The amount of time to sleep per empty scan. Sleep durations
         * increase only arithmetically, as a compromise between
         * responsiveness and good citizenship.  The value here
         * arranges that first sleep is approximately the smallest
         * value worth context switching out for on typical platforms.
         */
        private static final long SLEEP_NANOS_PER_SCAN = (1 << 16);

        /**
         * Returns true if a given scanCtl value indicates that its
         * corresponding worker might be sleeping (or should enter a
         * sleep).
         */
        private static boolean maybeSleeping(int scans) {
            return (scans & SCANS_PER_SLEEP) == SCANS_PER_SLEEP;
        }

        /**
         * Returns next value for scan count, avoiding negative values.
         */
        private static int nextScanCount(int scans) {
            int s = scans + 1;
            return s < 0? 1 : s;
        }

        /**
         * Creates new Worker.
         */
        Worker(ForkJoinPool pool, int index) {
            this.pool = pool;
            this.index = index;
            this.randomSeed =  index ^ (int)System.nanoTime();
            setDaemon(true);
        }

        /**
         * Main run loop
         */
        public void run() {
            try {
                if (tasks == null) // lazily initialize array
                    tasks = new ForkJoinTask<?>[INITIAL_CAPACITY];
                while (!pool.isStopped()) {
                    ForkJoinTask<?> task = popTask();
                    if (task == null)
                        task = getTask(true);
                    if (task != null)
                        task.exec();
                }
            } finally {
                pool.workerTerminated(this, index);
            }
        }

        // Misc status and accessor methods, callable from ForkJoinTasks

        ForkJoinPool getPool() {
            return pool;
        }

        int getPoolSize() {
            return pool.getPoolSize();
        }

        int getQueueSize() {
            long n = sp - base;
            return n < 0? 0 : (int)n; // suppress momentarily negative values
        }

        boolean queueIsEmpty() {
            return base >= sp;
        }

        boolean isActive() {
            return base < sp || scanCtl == 0;
        }

        void setIdle() {
            if (scanCtl == 0)
                scanCtl = 1;
        }

        /**
         * Pushes a task. Called only by current thread.
         * @param x the task
         */
        void pushTask(ForkJoinTask<?> x) {
            ForkJoinTask<?>[] array = tasks;
            if (array != null) {
                int mask = array.length - 1;
                long s = sp;
                if (s - base < mask) {
                    array[((int)s) & mask] = x;
                    sp = s + 1;
                    return;
                }
            }
            growAndPushTask(x);
        }

        /*
         * Handles resizing and reinitialization cases for pushTask
         * @param x the task
         */
        private void growAndPushTask(ForkJoinTask<?> x) {
            int oldSize = 0;
            int newSize = 0;
            ForkJoinTask<?>[] oldArray = tasks;
            if (oldArray != null) {
                oldSize = oldArray.length;
                newSize = oldSize << 1;
            }
            if (newSize < INITIAL_CAPACITY)
                newSize = INITIAL_CAPACITY;
            if (newSize > MAXIMUM_CAPACITY)
                throw new Error("Work queue capacity exceeded");
            ForkJoinTask<?>[] newArray = new ForkJoinTask<?>[newSize];
            int newMask = newSize - 1;
            long s = sp;
            newArray[((int)s) & newMask] = x;
            if (oldArray != null) {
                int oldMask = oldSize - 1;
                for (long i = nextSlotToClean = base; i < s; ++i) {
                    newArray[((int)i) & newMask] =
                        oldArray[((int)i) & oldMask];
                }
            }
            sp = s; // need volatile write here just to force ordering
            tasks = newArray;
            sp = s + 1;
        }

        /**
         * Returns a popped task, or null if empty.  Called only by
         * current thread.
         */
        private ForkJoinTask<?> popTask() {
            ForkJoinTask<?> x = null;
            ForkJoinTask<?>[] array = tasks;
            if (array != null) {
                long s = sp - 1;
                int idx = ((int)s) & (array.length-1);
                x = array[idx];
                if (x != null) {
                    array[idx] = null; // always OK to clear slot here
                    sp = s;
                    long b = base;
                    if (s <= b) {
                        if (s < b || // note b++ side effect
                            !baseUpdater.compareAndSet(this, b++, b))
                            x = null;  // lost race vs steal
                        sp = b;
                    }
                }
            }
            return x;
        }

        /**
         * Specialized version of popTask to pop only if
         * topmost element is the given task.
         * @param task the task to match
         */
        private boolean popIfEq(ForkJoinTask<?> task) {
            boolean popped = false;
            long s = sp - 1;
            ForkJoinTask<?>[] array = tasks;
            if (array != null) {
                int idx = ((int)s) & (array.length-1);
                if (array[idx] == task) {
                    array[idx] = null;
                    sp = s;
                    long b = base;
                    if (s <= b) {
                        popped = (s == b &&
                                  baseUpdater.compareAndSet(this, b++, b));
                        sp = b;
                    }
                    else
                        popped = true;
                }
            }
            return popped;
        }

        /**
         * Takes a task from the base of the queue.  Always called by
         * other non-owning threads. Retries upon contention.
         *
         * Currently used only for cancellation -- getTask embeds a
         * variant with better contention control for other uses..
         * @return a task, or null if none
         */
        private ForkJoinTask<?> stealTask() {
            for (;;) {
                long b = base;
                if (b >= sp)
                    return null;
                ForkJoinTask<?>[] array = tasks;
                if (array == null)
                    return null;
                ForkJoinTask<?> x = array[((int)b) & (array.length-1)];
                if (x != null &&
                    baseUpdater.compareAndSet(this, b, b+1))
                    return x;
            }
        }

        /**
         * Steal a task or get one from submission queue.
         * @param inMainLoop true if should try submission queue
         * @return a task or null if none
         */
        private ForkJoinTask<?> getTask(boolean inMainLoop) {
            clearStolenSlots(); // first, clean up

            /*
             * Scan through all workers starting at random index. If
             * any attempted steal fails due to apparent contention,
             * rescan all workers starting at a new random
             * index. While traversing, the first failed attempted
             * steal for which the attempted victim is apparently
             * sleeping is recorded. If a task is found in some other
             * queue, and that queue appears to have more tasks, that
             * sleeper is woken up.  This propagates wakeups across
             * workers when new work becomes available to a set of
             * otherwise idle threads (Workers sleep here only if
             * there is not enough parallelism to keep them busy. They
             * block on input queue when there are no jobs.)
             */
            Worker sleeper = null;
            Worker[] workers = pool.getWorkers();
            int n = workers.length;
            int idx = -1;          // force randomIndex call below
            int remaining = n;     // number of workers to be scanned
            while (remaining-- > 0) {
                if (idx < 0)
                    idx = randomIndex(n);
                else if (++idx >= n)
                    idx = 0;
                Worker v = workers[idx];
                if (v != null && v != this) {
                    long b = v.base;
                    ForkJoinTask<?>[] array;
                    if (b < v.sp && (array = v.tasks) != null) {
                        int k = ((int)b) & (array.length-1);
                        ForkJoinTask<?> task = array[k];
                        if (task != null &&
                            baseUpdater.compareAndSet(v, b, b+1)) {
                            ++stealCount;
                            scanCtl = 0;
                            if (sleeper != null && v.base < v.sp)
                                sleeper.wakeup();
                            return task;
                        }
                        idx = -1;
                        remaining = n; // apparent contention
                    }
                    else if (sleeper == null &&
                             maybeSleeping(v.scanCtl))
                        sleeper = v;
                }
            }

            /*
             * If in main loop, check submission queue
             */
            if (inMainLoop) {
                ForkJoinTask<?> job = pool.getJob(this);
                if (job != null) {
                    scanCtl = 0;
                    if (sleeper != null)
                        sleeper.wakeup();
                    return job;
                }
            }

            /*
             * Adjust scanCtl count and possibly sleep or yield
             * after failing to find work
             */
            int scans = nextScanCount(scanCtl);
            if (maybeSleeping(scans)) {
                int next = nextScanCount(scans);
                if (inMainLoop) {
                    scanCtl = scans;
                    LockSupport.parkNanos(scans * SLEEP_NANOS_PER_SCAN);
                    scanCtl = next; // OK to unconditionally set here
                }
                else { // skip over sleep state
                    scanCtl = next;
                    Thread.yield();
                }
            }
            else
                scanCtl = scans;

            return null;
        }


        /**
         * Wake up if sleeping waiting for work
         */
        private void wakeup() {
            int scans = scanCtl;
            if (maybeSleeping(scans) &&
                scanCtlUpdater.compareAndSet(this, scans,
                                             nextScanCount(scans)))
                LockSupport.unpark(this);
        }

        /**
         * Returns a random index for choosing thread to steal from.
         * This doesn't need to be a high quality generator.  So it
         * isn't -- it uses a simple Marsaglia xorshift.
         */
        private int randomIndex(int n) {
            int rand = randomSeed;
            rand ^= rand << 13;
            rand ^= rand >>> 17;
            randomSeed = rand ^= rand << 5;
            if ((n & (n-1)) == 0) // avoid "%"
                return rand & (n-1);
            else
                return (rand & 0x7fffffff) % n;
        }

        /**
         * Clear any lingering refs to stolen tasks. Procedes only if
         * queue is empty.
         */
        private void clearStolenSlots() {
            long b = base;
            if (b == sp) {
                long i = nextSlotToClean;
                if (i < b) {
                    nextSlotToClean = b;
                    ForkJoinTask<?>[] array = tasks;
                    if (array != null) {
                        // Due to wraparounds, we might clear some
                        // slots multiple times. But this is not
                        // common enough to bother dealing with.
                        int mask = array.length - 1;
                        do {
                            array[((int)(i)) & mask] = null;
                        } while (++i < b);
                    }
                }
            }
        }

        /*
         * Remove (via steal) and cancel all tasks in queue.
         * Called from pool only during shutdown.
         */
        void cancelTasks() {
            while (!queueIsEmpty()) {
                ForkJoinTask<?> t = stealTask();
                if (t != null)
                    t.cancel();
            }
        }

        /*
         * Support for main functionality of ForkJoinTask methods.
         */

        /**
         * Implements ForkJoinTask.help
         */
        boolean help() {
            ForkJoinTask<?> t = popTask();
            if (t == null && (t = getTask(false)) == null)
                return false;
            t.exec();
            return true;
        }

        /**
         * Implements ForkJoinTask.helpUntilQuiescent
         */
        void helpUntilQuiescent() {
            for (;;) {
                ForkJoinTask<?> t = popTask();
                if (t == null)
                    t = getTask(false);
                if (t != null)
                    t.exec();
                else if (pool.isQuiescent())
                    return;
            }
        }

        /**
         * Execute tasks until joinMe isDone. Assumes caller
         * pre-checks that joinMe is not done before invoking
         * this method.
         */
        RuntimeException helpWhileJoining(ForkJoinTask<?> joinMe) {
            for (;;) {
                ForkJoinTask<?> t = popTask();
                if (t != null || (t = getTask(false)) != null)
                    t.exec();
                if (joinMe.isDone())
                    return joinMe.getException();
            }
        }

        /**
         * Version of join for void actions
         */
        Void joinAction(ForkJoinTask<Void> joinMe) {
            for (;;) {
                RuntimeException ex = joinMe.exception;
                if (ex != null)
                    throw ex;
                if (joinMe.status > 0)
                    return null;
                ForkJoinTask<?> t = popTask();
                if (t != null || (t = getTask(false)) != null)
                    t.exec();
            }
        }

        /**
         * version of join for result-bearing actions
         */
        <T> T joinTask(RecursiveTask<T> joinMe) {
            for (;;) {
                RuntimeException ex = joinMe.exception;
                if (ex != null)
                    throw ex;
                if (joinMe.status > 0)
                    return joinMe.getResult();
                ForkJoinTask<?> t = popTask();
                if (t != null || (t = getTask(false)) != null)
                    t.exec();
            }
        }

        /**
         * Implements TaskBarrier.awaitCycleAdvance
         * @return current barrier phase
         */
        final int helpUntilBarrierAdvance(TaskBarrier b, int phase) {
            for (;;) {
                int p = b.getCycle();
                if (p != phase || p < 0)
                    return p;
                ForkJoinTask<?> t = popTask();
                if (t == null)
                    t = getTask(false);
                if (t != null) {
                    p = b.getCycle();
                    if (p != phase) { // if barrier advanced
                        pushTask(t);  // push task and exit
                        return p;
                    }
                    else
                        t.exec();
                }
            }
        }

        /**
         * Implements ForkJoinTask.coInvoke.
         */
        void coInvokeTasks(RecursiveAction t1, RecursiveAction t2) {
            if (t1 == null || t2 == null)
                throw new NullPointerException();
            pushTask(t2);
            RuntimeException ex = t1.exec();
            boolean popped = popIfEq(t2);
            if (ex != null ||
                ((ex = !popped? helpWhileJoining(t2) : t2.exec()) != null)) {
                t2.cancel();
                throw ex;
            }
        }

        /**
         * Implements ForkJoinTask.coInvoke.
         */
        void coInvokeTasks(RecursiveAction[] tasks) {
            int last = tasks.length - 1;
            RuntimeException ex = null;
            for (int i = last; i >= 0; --i) {
                RecursiveAction t = tasks[i];
                if (t == null) {
                    if (ex == null)
                        ex = new NullPointerException();
                }
                else if (ex != null)
                    t.cancel();
                else if (i != 0)
                    pushTask(t);
                else
                    ex = t.exec();
            }
            for (int i = 1; i <= last; ++i) {
                RecursiveAction t = tasks[i];
                if (t != null) {
                    boolean popped = popIfEq(t);
                    if (ex != null)
                        t.cancel();
                    else if (!popped)
                        ex = helpWhileJoining(t);
                    else
                        ex = t.exec();
                }
            }
            if (ex != null)
                throw ex;
        }

        /**
         * Implements ForkJoinTask.coInvoke.
         */
        void coInvokeTasks(List<? extends RecursiveAction> tasks) {
            int last = tasks.size() - 1;
            RuntimeException ex = null;
            for (int i = last; i >= 0; --i) {
                RecursiveAction t = tasks.get(i);
                if (t == null) {
                    if (ex == null)
                        ex = new NullPointerException();
                }
                else if (i != 0)
                    pushTask(t);
                else if (ex != null)
                    t.cancel();
                else
                    ex = t.exec();
            }
            for (int i = 1; i <= last; ++i) {
                RecursiveAction t = tasks.get(i);
                if (t != null) {
                    boolean popped = popIfEq(t);
                    if (ex != null)
                        t.cancel();
                    else if (!popped)
                        ex = helpWhileJoining(t);
                    else
                        ex = t.exec();
                }
            }
            if (ex != null)
                throw ex;
        }

    }
}
