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
 * A thread that executes ForkJoinTasks. ForkJoinWorkerThreads are
 * internally managed by ForkJoinPools in support of ForkJoinTasks.
 * This class additionally provides public <tt>static</tt> methods
 * accessingsome basic scheduling and execution mechanics for the
 * <em>current</em> ForkJoinWorkerThread. These methods may be invoked
 * only from within other ForkJoinTask computations. Attempts to
 * invoke in other contexts result in exceptions or errors including
 * ClassCastException.  These methods enable construction of
 * special-purpose task classes, as well as specialized idioms
 * occasionally useful in ForkJoinTask processing.
 *
 * <p>The form of supported static methods reflects the fact that
 * worker threads may access and process tasks obtained in any of
 * three ways. In preference order: <em>Local</em> tasks are processed
 * in LIFO (newest first) order. <em>Stolen</em> tasks are obtained
 * from other threads in FIFO (oldest first) order, only if there are
 * no local tasks to run.  <em>Submissions</em> form a FIFO queue
 * common to the entire pool, and are started only if no other
 * work is available.
 *
 * <p> This class is subclassable solely for the sake of adding
 * functionality -- there are no overridable methods dealing with
 * fork/join execution. However, you can override initialization and
 * termination cleanup methods surrounding the main task processing
 * loop.  If you do create such a subclass, you will also need to
 * supply a custom ForkJoinWorkerThreadFactory to use it.
 *
 */
public class ForkJoinWorkerThread extends Thread {
    /**
     * The pool this thread works in.
     */
    private final ForkJoinPool pool;

    /**
     * Each ForkJoinWorkerThread thread maintains a work-stealing
     * queue represented via the resizable "queue" array plus base and
     * sp indices.  Work-stealing queues are special forms of Deques
     * that support only three of the four possible end-operations --
     * push, pop, and steal (aka dequeue), and only do so under the
     * constraints that push and pop are called only from the owning
     * thread, while steal is called from other threads.  The
     * work-stealing queue here uses a variant of the algorithm
     * described in "Dynamic Circular Work-Stealing Deque" by David
     * Chase and Yossi Lev, SPAA 2005. For an explanation, read the
     * paper.  http://research.sun.com/scalable/pubs/index.html The
     * main differences here stem from ensuring that deq slots
     * referencing popped and stolen tasks are cleared, as well as
     * spin/sleep scontrol. (Also. method and variable names differ.)
     *
     * The length of the queue array must always be a power of
     * two. Even though these queues don't usually become all that
     * big, the initial size must be large enough to counteract cache
     * contention effects across multiple queues.  Currently, they are
     * initialized upon start of main run loop, to increase the
     * likelihood they will be allocated near other per-thread
     * objects.  However, all queue-related methods are written so
     * that they could alternatively be lazily allocated (see
     * growQueue) and/or disposed of when empty.
     */
    private ForkJoinTask<?>[] queue;

    private static final int INITIAL_CAPACITY = 1 << 13;
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * Index (mod queue.length) of least valid queue slot, which
     * is always the next position to steal from if nonempty.
     */
    private volatile long base;

    /**
     * Index (mod queue.length-1) of next queue slot to push to
     * or pop from
     */
    private volatile long sp;

    /**
     * Updater to allow CAS of base index. Even though this slows down
     * CASes of base compared to standalone AtomicLong, CASes are
     * relatively infrequent, so the better locality of having sp and
     * base close to each other normally outweighs this.
     */
    private static final 
        AtomicLongFieldUpdater<ForkJoinWorkerThread> baseUpdater =
        AtomicLongFieldUpdater.newUpdater(ForkJoinWorkerThread.class, "base");

    /**
     * FOR JAVA 7 VERSION ONLY. (relies on Java 7 array updater class).
     * Updater to CAS queue array slot to null upon steal.
     * (CAS is not needed to clear on pop.)
     */
    // static final AtomicReferenceArrayUpdater<ForkJoinTask<?>> 
    // slotUpdater = new AtomicReferenceArrayUpdater<ForkJoinTask<?>>();

    /**
     * NEEDED FOR JAVA 5/6 VERSION ONLY.
     * Index (mod queue.length) of the next queue slot to null
     * out. Queue slots stolen by other threads cannot be safely
     * nulled out by them. Instead, they are cleared by owning
     * thread whenever queue is empty.
     */
    private long nextSlotToClean;

    /**
     * Seed for random number generator in randomIndex
     */
    private long randomSeed;

    /**
     * Number of steals, just for monitoring purposes.
     */
    private int stealCount;

    /**
     * Number of sleeps, just for monitoring purposes.
     */
    private int sleepCount;

    /**
     * Index of this worker in pool array. Set once by pool before running.
     */
    private int index;

    /**
     * Number of scans since last successfully getting a task.  Always
     * zero when busy executing tasks.  Incremented after a failed
     * scan in getTask(), to help control sleeps and maintain active
     * worker count.
     */
    private int idleScans;

    /**
     * Pool-wide count of active workers, cached from pool
     */
    private final AtomicInteger activeWorkerCounter;

    /**
     * Run state of this worker
     */
    private final AtomicInteger workerRunState;

    private static final int WORKER_SHUTDOWN   =  1;
    private static final int WORKER_STOP       =  2;
    private static final int WORKER_TERMINATED =  4;

    /**
     * Pool-wide idle sleep semahore. This reference is cached from
     * pool. See below for explanation.
     */
    private final ForkJoinPool.SleepControl sleepCtl;


    /**
     * Optional first task to run before main loop, set by pool
     */
    private ForkJoinTask<?> firstTask;


    /**
     * True if exit after first task completes, as set by pool
     */
    boolean exitOnFirstTaskCompletion;

    /**
     * Padding to avoid cache-line sharing across workers
     */
    //     int p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, pa, pb, pc, pd, pe, pf;

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null;
     */
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        if (pool == null)
            throw new NullPointerException();
        this.pool = pool;
        this.workerRunState = new AtomicInteger();
        this.sleepCtl = pool.getSleepControl();
        this.activeWorkerCounter = pool.getActiveWorkerCounter();
        activeWorkerCounter.incrementAndGet();
    }

    /**
     * Initializes internal state after construction but before
     * processing any tasks. If you override this method, you must
     * invoke super.onStart() at the beginning of the method.
     * Initialization requires care: Most fields must have legal
     * default values, to ensure that attempted accesses from other
     * threads work correctly even before this thread starts
     * processing tasks.
     */
    protected void onStart() {
        randomSeed = (((long)index << 24) ^ System.nanoTime());
        if (randomSeed == 0)
            randomSeed = index + 1;
        if (queue == null) 
            growQueue(null);
    }

    /**
     * This method is required to be public, but should never be
     * called explicitly. It executes the main run loop to execute
     * ForkJoinTasks.
     */
    public void run() {
        Throwable exception = null;
        try {
            onStart();
            if (firstTask != null) {
                firstTask.exec();
                firstTask = null;
                if (exitOnFirstTaskCompletion)
                    return;
            }
            mainLoop();
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            onTermination(exception);
        }
    }

    /**
     * Process tasks until terminated
     */
    private void mainLoop() {
        while ((workerRunState.get() & (WORKER_SHUTDOWN|WORKER_STOP)) == 0) {
            ForkJoinTask<?> task = getTask(true);
            if (task != null) 
                task.exec();
        }
    }

    /**
     * Perform cleanup associated with termination of this worker
     * thread.  If you override this method, you must invoke
     * super.onTermination at the end of the overridden method.
     * 
     * @param exception the exception causing this thread to abort, or
     * null if completed normally due to pool shutdown.
     */
    protected void onTermination(Throwable exception) {
        try {
            cancelTasks();
        } finally {
            if (idleScans++ == 0)
                activeWorkerCounter.decrementAndGet();
            setWorkerStateBits(WORKER_TERMINATED);
            pool.workerTerminated(this, exception);
        }
    }

    // misc internal utilities

    /**
     * Set given bits in workerRunState, return previous state
     */
    private int setWorkerStateBits(int bits) {
        AtomicInteger wrs = workerRunState;
        for (;;) {
            int rs = wrs.get();
            int next = rs | bits;
            if (rs == next || wrs.compareAndSet(rs, next))
                return rs;
        }
    }

    /**
     * Clear given bits in workerRunState, return previous state
     * (Currently unused.)
     */
    private int clearWorkerStateBits(int bits) {
        AtomicInteger wrs = workerRunState;
        for (;;) {
            int rs = wrs.get();
            int next = rs & ~bits;
            if (rs == next || wrs.compareAndSet(rs, next))
                return rs;
        }
    }

    /**
     * Returns a random index for choosing thread to steal from.
     * This doesn't need to be a high quality generator.  So it
     * isn't -- it uses a simple Marsaglia xorshift.
     */
    private int randomIndex(int n) {
        long rand = randomSeed;
        rand ^= rand << 13;
        rand ^= rand >>> 7;
        rand ^= rand << 17;
        randomSeed = rand;
        if ((n & (n-1)) == 0) // avoid "%"
            return (int)(rand & (n-1));
        else
            return ((int)(rand & 0x7fffffff)) % n;
    }


    // Simple accessors callable from pool and/or public static methods.

    final void setIndex(int i) { // called only from pool on initialization
        index = i;
    }

    final void setFirstTask(ForkJoinTask<?> task, 
                            boolean exitOnCompletion) { 
        firstTask = task;
        exitOnFirstTaskCompletion = exitOnCompletion;
    }

    final int getIndex() {
        return index;
    }

    final int getSteals() {
        return stealCount;
    }

    final int getSleeps() {
        return sleepCount;
    }

    final void setShutdown() {
        setWorkerStateBits(WORKER_SHUTDOWN);
    }

    final void setStop() {
        setWorkerStateBits(WORKER_STOP);
    }

    final boolean queueIsEmpty() {
        return base >= sp;
    }

    final int getQueueSize() {
        long n = sp - base;
        return n < 0? 0 : (int)n; // suppress momentarily negative values
    }

    /**
     * Reports true unless this thread has unsuccessfully tried to
     * obtain a task to run.
     */
    final boolean isActive() {
        return !queueIsEmpty() || idleScans == 0;
    }

    /*
     * Tuning constants controlling spin/yield/sleep.  These values
     * empirically work well on typical platforms.  The SCANS_PER_X
     * values must be powers of two minus one since they are used as
     * masks. Also, SCANS_PER_YIELD < SCANS_PER_SLEEP < MAX_SCANS.
     * See below for explanation.
     */
    private static final int  SCANS_PER_YIELD       = (1 <<  5) - 1;
    private static final int  SCANS_PER_SLEEP       = (1 << 10) - 1;
    private static final int  MAX_SCANS             = (1 << 16) - 1;
    private static final long SLEEP_NANOS_PER_SCAN  = (1 << 14);

    /**
     * Spin/yield/sleep control triggered when threads are unable to
     * find work in getTask. This is controlled via idleScans, that
     * keeps track of failed getTask attempts ("scans"). There are
     * three options:
     * 
     * * Spin: do nothing and allow worker thread to call
     *   getTask again. Because getTask etc are designed to be quiet
     *   and fast (usually no locks etc) while looking for work,
     *   spins can and should greatly outnumber other options.
     *
     * * Yield: Call Thread.yield and then spin more.
     *   The SCANS_PER_YIELD value is a heuristic compromise balancing
     *   facts that yield may or may not actually have any effect on
     *   some VMs, but yielding too frequently may have detrimental
     *   effects (like hitting internal VM locks) on other VMs.
     * 
     * * Sleep, via a form of signallable timed wait. 
     *   Because even this light form of sleeping is relatively
     *   expensive compared to spinning, SCANS_PER_SLEEP is relatively
     *   high, and sleep durations start (SLEEP_NANOS_PER_SCAN) with a
     *   value that is approximately the smallest duration worth
     *   context switching out for on typical platforms.  These
     *   durations increase only arithmetically, as a compromise
     *   between responsiveness and good citizenship.  Times are
     *   bounded by MAX_SCANS (the maximum allowed idleScans value)
     *   times the base time.
     * 
     * Using sleeps in addition to spins and yields makes this
     * framework cope much better with lags due to GC, dynamic
     * compilation, queue resizing, multitasking, etc.  But it is a
     * bit delicate.
     *   
     * Sleeping and wakeups are managed by a capped semaphore
     * scheme. Threads sleep on a semaphore that is released when some
     * other worker pushes a task onto an empty queue or successfully
     * steals. This propagates signals across sets of sleeping workers
     * when work becomes available. (Note that in steady state,
     * sleepCtl.releaseOne is usually almost a no-op since the number
     * of permits will be maxed out.)
     * 
     * When a worker that is in the midst of joining a task sleeps,
     * (i.e., a call other than from mainLoop), typically others that
     * are in turn waiting on it will also start stalling. So, in this
     * case, upon being signalled, we produce enough signals to wake
     * up all other workers.  Some may further stall if this thread
     * takes a long time to produce work, but we at least avoid full
     * convoying.
     *
     * Note that if this scheme were used only for main loop, then we
     * wouldn't even need timeouts -- the permit granting scheme would
     * wake up each in turn when there is work available. However,
     * even here, timeouts work better since they have the effect of
     * pre-signalling workers, avoiding context switch, which happens
     * often enough to outweigh timeout overhead. (And in any case,
     * timeouts are needed anyway for the other case.)
     *
     * @param inMainLoop true if called from main loop
     */
    private void maybeSleep(boolean inMainLoop) {
        int scans = idleScans;
        if ((scans & SCANS_PER_SLEEP) == SCANS_PER_SLEEP) {
            long nanos = scans * SLEEP_NANOS_PER_SCAN;
            int res = sleepCtl.acquire(nanos);
            if (res != 0)
                ++sleepCount;
            if (res >= 0 && !inMainLoop) // avoid pile-ups
                sleepCtl.releaseAll();
            // Trace sleeps...
            //            if (res != 0)
            //                System.out.print(inMainLoop? "*" : "#");
        }
        else if ((scans & SCANS_PER_YIELD) == SCANS_PER_YIELD)
            Thread.yield();
    }

    // Main work-stealing queue methods

    /**
     * Pushes a task. Also wakes up a sleeping worker if queue was
     * previously empty.  Called only by current thread.
     * @param x the task
     */
    final void pushTask(ForkJoinTask<?> x) {
        ForkJoinTask<?>[] q = queue;
        if (q != null) {
            int mask = q.length - 1;
            long s = sp;
            int len = (int)(s - base);
            if (len < mask) {
                q[((int)s) & mask] = x;
                sp = s + 1;
                if (len <= 0) 
                    sleepCtl.releaseOne();
                return;
            }
        }
        growQueue(x);
    }

    /*
     * Resizes and/or initializes queue, also pushing on given
     * task if non-null;
     * @param x the task
     */
    private void growQueue(ForkJoinTask<?> x) {
        int oldSize = 0;
        int newSize = 0;
        ForkJoinTask<?>[] oldQ = queue;
        if (oldQ != null) {
            oldSize = oldQ.length;
            newSize = oldSize << 1;
        }
        if (newSize < INITIAL_CAPACITY)
            newSize = INITIAL_CAPACITY;
        if (newSize > MAXIMUM_CAPACITY)
            throw new Error("Queue capacity exceeded");
        ForkJoinTask<?>[] newQ = new ForkJoinTask<?>[newSize];
        int newMask = newSize - 1;
        long s = sp;
        if (oldQ != null) {
            int oldMask = oldSize - 1;
            for (long i = nextSlotToClean = base; i < s; ++i)
                newQ[((int)i) & newMask] = oldQ[((int)i) & oldMask];
        }
        sp = s; // need volatile write here just to force ordering
        if (x == null) {
            queue = newQ;
            sp = s; // need second barrier even if no push
        }
        else {
            newQ[((int)s) & newMask] = x;
            queue = newQ;
            sp = s + 1;
        }
    }

    /**
     * Returns next task to pop. Called only by current thread.
     */
    private ForkJoinTask<?> peekTask() {
        ForkJoinTask<?>[] q = queue;
        long s = sp - 1;
        if (q == null || s <= base)
            return null;
        else
            return q[((int)s) & (q.length-1)];
    }

    /**
     * Returns a popped task, or null if empty.  Called only by
     * current thread.
     */
    private ForkJoinTask<?> popTask() {
        ForkJoinTask<?> x = null;
        ForkJoinTask<?>[] q = queue;
        if (q != null) {
            long s = sp - 1;
            int mask = q.length - 1;
            int idx = ((int)s) & mask;
            x = q[idx];
            sp = s;
            q[idx] = null; 
            long b = base;
            if (s <= b) {
                if (s < b ||   // note ++b side effect
                    !baseUpdater.compareAndSet(this, b, ++b))
                    x = null;  // lost race vs steal
                sp = b;
                // CLEANUP LOOP NEEDED FOR JAVA 5/6 VERSION ONLY:
                while (nextSlotToClean < b)
                    q[((int)(nextSlotToClean++)) & mask] = null;
            }
        }
        return x;
    }

    /**
     * Specialized version of popTask to pop only if
     * topmost element is the given task.
     * @param task the task to match
     */
    private boolean popIfNext(ForkJoinTask<?> task) {
        boolean popped = false;
        ForkJoinTask<?>[] q = queue;
        if (q != null) {
            long s = sp - 1;
            int idx = ((int)s) & (q.length-1);
            if (task != null && q[idx] == task) { // can't pop null
                sp = s;
                q[idx] = null;
                long b = base;
                if (s <= b) {
                    popped = (s == b &&
                              baseUpdater.compareAndSet(this, b, ++b));
                    sp = b;
                }
                else
                    popped = true;
            }
        }
        return popped;
    }

    /**
     * Tries to take a task from the base of the queue.  Always
     * called by other non-owning threads. Fails upon contention.
     *
     * Currently used only for cancellation -- getTask embeds
     * a variant with contention control for other cases.
     * @return a task, or null if none
     */
    private ForkJoinTask<?> tryStealTask() {
        ForkJoinTask<?>[] q;
        long b = base;
        if (b < sp && (q = queue) != null) {
            int slot = ((int)b) & (q.length-1);
            ForkJoinTask<?> x = q[slot];
            if (x != null &&
                baseUpdater.compareAndSet(this, b, b+1)) {
                // slotUpdater.compareAndSet(q, slot, t, null);
                return x;
            }
        }
        return null;
    }

    /**
     * Tries to get a task to run
     * @param inMainLoop true if should check submission
     * queue in addition to local or stolen tasks.
     * @return a task or null if none
     */
    private ForkJoinTask<?> getTask(boolean inMainLoop) {
        int scans = idleScans;
        // Try a local task
        if (scans == 0) { // no use checking on repeated scans
            ForkJoinTask<?> t = popTask();
            if (t != null)
                return t;
        }

        // Try to steal a task
        ForkJoinWorkerThread[] ws = pool.getWorkers();
        final int n = ws.length;
        int idx = randomIndex(n);
        int remaining = n;
        while (remaining > 0) {
            ForkJoinWorkerThread v = ws[idx];
            if (v != null && v != this) {
                long b = v.base;
                ForkJoinTask<?>[] q;
                if (b < v.sp && (q = v.queue) != null) {
                    // must set active even if fail to get task
                    if (scans > 0) {
                        idleScans = scans = 0;
                        activeWorkerCounter.incrementAndGet();
                    }
                    int slot = ((int)b) & (q.length-1);
                    ForkJoinTask<?> t = q[slot];
                    if (t != null && 
                        baseUpdater.compareAndSet(v, b, b+1)) {
                        // slotUpdater.compareAndSet(q, slot, t, null);
                        sleepCtl.releaseOne();
                        ++stealCount;
                        t.setStolen();
                        return t;
                    }
                    idx = index;   // restart at own index
                    remaining = n; // rescan upon contention
                }
            }
            if (++idx >= n)
                idx = 0;
            --remaining;
        }

        if (++scans > MAX_SCANS)
            scans = MAX_SCANS;
        idleScans = scans;
        if (scans == 1)
            activeWorkerCounter.decrementAndGet();
        
        // Try to get a submission
        if (scans >= SCANS_PER_SLEEP || inMainLoop) {
            ForkJoinTask<?> t = pool.getSubmission(inMainLoop);
            if (t != null) {
                idleScans = 0;
                activeWorkerCounter.incrementAndGet();
                return t;
            }
        }

        if (!inMainLoop &&
            (workerRunState.get() & WORKER_STOP) != 0) // killed
            throw new CancellationException();

        maybeSleep(inMainLoop);
        return null;
    }

    /*
     * Remove (via steal) and cancel all tasks in queue.  Can be
     * called from any thread.
     */
    final void cancelTasks() {
        while (!queueIsEmpty()) {
            ForkJoinTask<?> t = tryStealTask();
            if (t != null)
                t.cancel();
        }
    }

    // Public methods on current thread
    
    /**
     * Returns the pool hosting the current task execution.
     * @return the pool
     */
    public static ForkJoinPool getPool() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).pool;
    }

    /**
     * Returns the index number of the current worker in its pool.
     * The return value is in the range
     * <tt>0...getPool().getPoolSize()-1</tt>.  This method may be
     * useful for applications that track status or collect results
     * per-worker rather than per-task.
     * @return the index number.
     */
    public static int getPoolIndex() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).index;
    }

    /**
     * Returns true if the current worker thread cannot obtain
     * a task to execute. This method is conservative: It might
     * not return true immediately upon idleness, but will eventually
     * return true if this thread cannot obtain work.
     * @return true if idle
     */
    public static boolean isIdle() {
        return !(((ForkJoinWorkerThread)(Thread.currentThread())).isActive());
    }

    /**
     * Returns the number of tasks waiting to be run by the thread
     * currently performing task execution. This value may be useful
     * for heuristic decisions about whether to fork other tasks.
     * @return the number of tasks
     */
    public static int getLocalQueueSize() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).getQueueSize();
    }

    /**
     * Returns the number of tasks the current worker thread has stolen
     * from other workers.
     * @return the number of stolen tasks
     */
    public static int getStealCount() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).getSteals();
    }

    /**
     * Returns the number of times the current worker thread has slept
     * waiting for work to be produced by other workers.
     * @return the number of sleeps
     */
    public static int getSleepCount() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).getSleeps();
    }

    /**
     * Adds the given task as the next task scheduled for execution in
     * the current worker's local queue.
     * @param task the task
     * @throws NullPointerException if task is null
     */
    public static void addLocalTask(ForkJoinTask<?> task) {
        if (task == null)
            throw new NullPointerException();
        ((ForkJoinWorkerThread)(Thread.currentThread())).pushTask(task);
    }

    /**
     * Returns, but does not remove or execute, the next task locally
     * queued for execution by the current worker. There is no
     * guarantee that this task will be the next one actually executed
     * or returned from <tt>pollTask</tt>.
     * @return the next task or null if none
     */
    public static ForkJoinTask<?> peekLocalTask() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).peekTask();
    }

    /**
     * Removes and returns, without executing, the next task queued
     * for execution in the current worker's local queue.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollLocalTask() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).popTask();
    }

    /**
     * Removes and returns, without executing, the given task from the
     * queue hosting current execution only if it would be the next
     * task executed by the current worker.  Among other usages, this
     * method may be used to bypass task execution during
     * cancellation.
     * @param task the task
     * @return true if removed
     */
    public static boolean removeIfNextLocalTask(ForkJoinTask<?> task) {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).popIfNext(task);
    }

    /**
     * Removes and returns, without executing, the next task available
     * for execution by the current worker, which may be either
     * locally queued task, or one stolen from another worker.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollTask() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).getTask(false);
    }

    /**
     * Execute the next task locally queued by the current worker, if
     * one is available.
     * @return true if a task was run; a false return indicates
     * that no task was available.
     */
    public static boolean executeLocalTask() {
        ForkJoinTask<?> t = 
            ((ForkJoinWorkerThread)(Thread.currentThread())).popTask();
        if (t == null)
            return false;
        else {
            t.exec();
            return true;
        }
    }

    /**
     * Helps this program complete by processing a local or stolen
     * task, if one is available.  This method may be useful when
     * several tasks are forked, and only one of them must be joined,
     * as in:
     *
     * <pre>
     *   while (!t1.isDone() &amp;&amp; !t2.isDone()) 
     *     ForkJoinWorkerThread.executeTask();
     * </pre> 
     * Similarly, you can help process tasks until a computation
     * completes via 
     * <pre>
     *   while(ForkJoinWorkerThread.executeTask() || 
     *         !ForkJoinWorkerThread.getPool().isQuiescent()) 
     *      ;
     * </pre>
     *
     * @return true if a task was run; a false return indicates
     * that no task was available.
     */
    public static boolean executeTask() {
        ForkJoinTask<?> t = 
            ((ForkJoinWorkerThread)(Thread.currentThread())).getTask(false);
        if (t == null)
            return false;
        else {
            t.exec();
            return true;
        }
    }

    // Package-local bypasses for core ForkJoinTask methods

    /**
     * Implements ForkJoinTask.join
     */
    final <T> T doJoinTask(ForkJoinTask<T> joinMe) {
        for (;;) {
            RuntimeException ex = joinMe.exception;
            if (ex != null)
                throw ex;
            if (joinMe.status < 0)
                return joinMe.getResult();
            ForkJoinTask<?> t = getTask(false);
            if (t != null)
                t.exec();
        }
    }

    /**
     * Implements ForkJoinTask.quietlyJoin
     */
    final RuntimeException doQuietlyJoinTask(ForkJoinTask<?> joinMe) {
        for (;;) {
            RuntimeException ex = joinMe.exception;
            if (ex != null)
                return ex;
            if (joinMe.status < 0)
                return null;
            ForkJoinTask<?> t = getTask(false);
            if (t != null)
                t.exec();
        }
    }


    /**
     * Implements RecursiveAction.coInvoke
     */
    final void doCoInvoke(RecursiveAction t1, RecursiveAction t2) {
        pushTask(t2);
        RuntimeException ex = t1.exec();
        boolean popped = popIfNext(t2);
        if (ex != null ||
            ((ex = !popped? doQuietlyJoinTask(t2) : t2.exec()) != null)) {
            t2.cancel();
            throw ex;
        }
    }


}
