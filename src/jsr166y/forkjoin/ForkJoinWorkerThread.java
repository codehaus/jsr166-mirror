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
 * accessing basic scheduling and execution mechanics for the
 * <em>current</em> ForkJoinWorkerThread. These methods may be invoked
 * only from within other ForkJoinTask computations. Attempts to
 * invoke in other contexts result in exceptions or errors including
 * ClassCastException.  These methods enable construction of
 * special-purpose task classes, as well as specialized idioms
 * occasionally useful in ForkJoinTask processing.
 *
 * <p>The form of supported methods reflects the fact that worker
 * threads may access and process tasks obtained in any of three
 * ways. In preference order: <em>Local</em> tasks are processed in
 * LIFO (newest first) order. <em>Stolen</em> tasks are obtained from
 * other threads in FIFO (oldest first) order. <em>Submissions</em>
 * form a FIFO queue common to the entire pool. The preference order
 * among local and stolen tasks cannot be changed. However, methods
 * exist to independently process submissions.
 *
 * <p> This class is subclassable solely for the sake of adding
 * functionality -- there are no overridable methods dealing with
 * fork/join execution (although overridable method
 * <tt>initializeUponStart</tt> does allow additional initialization
 * once the thread begins running.) If you do create such a subclass,
 * you will also need to supply a custom ForkJoinWorkerThreadFactory
 * to and pool using them.
 *
 */
public class ForkJoinWorkerThread extends Thread {
    /*
     * ForkJoinWorkerThreads repeatedly get tasks and run them.
     *
     * Each ForkJoinWorkerThread thread maintains a work-stealing
     * queue.  Work-stealing queues are special forms of Deques that
     * support only three of the four possible end-operations -- push,
     * pop, and steal (aka dequeue), and only do so under the
     * constraints that push and pop are called only from the owning
     * thread, while steal is called from other threads.  The
     * work-stealing queue here uses a variant of the algorithm
     * described in "Dynamic Circular Work-Stealing Deque" by David
     * Chase and Yossi Lev, SPAA 2005. For an explanation, read the
     * paper.  http://research.sun.com/scalable/pubs/index.html The
     * main differences here stem from ensuring that deq slots
     * referencing popped and stolen tasks are cleared. (Also. method
     * and variable names differ.)
     *
     * Nearly all methods of this class are either final or static.
     * This might impede extensibility, but avoids exposing internal
     * details that might preclude improvements over time.
     */

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null;
     */
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        if (pool == null)
            throw new NullPointerException();
        this.pool = pool;
    }

    /**
     * The pool this thread works in.
     */
    private final ForkJoinPool pool;

    /**
     * The array used as work-stealing queue. Length must always be a
     * power of two. Even though these queues don't usually become all
     * that big, the initial size must be large enough to counteract
     * cache contention effects across multiple queues.  Currently,
     * they are initialized upon start of main run loop, to increase
     * the likelihood they will be allocated near other per-thread
     * objects.  However, all queue-related methods are written so
     * that they could alternatively be lazily allocated (see
     * growAndPushTask) and/or disposed of when empty.
     */
    private ForkJoinTask<?>[] queue;

    private static final int INITIAL_CAPACITY = 1 << 13;
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * Index (mod queue.length) of least valid slot, which
     * is always the next position to steal from if nonempty.
     */
    private volatile long base;

    /**
     * Index (mod queue.length-1) of next position to push to
     * or pop from
     */
    private volatile long sp;

    /**
     * Updater to allow CAS of base index. Even though this slows
     * down CASes of base, they are relatively infrequent,
     * so the better locality of having sp and base close
     * to each other normally outweighs this.
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
     * Index (mod queue.length) of the next slot to null
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
     * Index of this worker in pool array. Set once by pool before running.
     */
    private int index;

    /**
     * Number of steals, just for monitoring purposes.
     */
    private int stealCount;

    /**
     * Number of scans since last successfully getting a task.
     * Always zero when busy executing tasks. 
     * Incremented after a failed scan in getTask(),
     * to help control sleeps inside Pool.getSubmission
     */
    private int idleCount;

    /**
     * Flag set by pool when this thread should terminate.
     */
    private volatile boolean shouldStop;


    /**
     * status set to IDLE_SLEEPING when about to sleep, which is unset
     * upon wakeup or signal. Or'ed with ON_SLEEP_QUEUE bit to prevent
     * adding multiple times on pool's idleSleepQueue.
     */
    private volatile int sleepStatus;

    private static final int IDLE_SLEEPING  = 1;
    private static final int ON_SLEEP_QUEUE = 2;

    /**
     * Updater to CAS sleepStatus
     */
    private static final 
        AtomicIntegerFieldUpdater<ForkJoinWorkerThread> sleepStatusUpdater =
        AtomicIntegerFieldUpdater.newUpdater(ForkJoinWorkerThread.class, "sleepStatus");


    /**
     * Link needed for idleSleepQueue 
     */
    private volatile ForkJoinWorkerThread nextSleeper;


    /**
     * Padding to avoid cache-line sharing across workers
     */
    int p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, pa, pb, pc, pd, pe, pf;

    /**
     * Initializes internal state after construction but before
     * processing any tasks. If you override this method, you must
     * invoke super.initializeUponStart() at the beginning of the
     * method.  Initialization requires care: Most fields must have
     * legal default values, to ensure that attempted sccesses from
     * other threads work correctly even before this thread starts
     * processing tasks.
     */
    protected void initializeUponStart() {
        randomSeed = (((long)index << 24) ^ System.nanoTime());
        if (randomSeed == 0)
            randomSeed = index + 1;
        if (queue == null) 
            growQueue(null);
    }

    /**
     * This method is required to be public, but should never be
     * called explicitly. It executes the main run loop.
     */
    public final void run() {
        Throwable exception = null;
        try {
            initializeUponStart();
            while (!shouldStop) {
                ForkJoinTask<?> task = getTask();
                if (task == null) 
                    task = doGetSubmission();
                if (task == null) 
                    maybeSleep();
                else
                    task.exec();
            }
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            terminate(exception);
        }
    }

    /**
     * Cleanup upon termination
     * @param exception the exception causing abort, or null if
     * completed normally.
     */
    private void terminate(Throwable exception) {
        cancelTasks();
        advanceIdleCount();
        pool.workerTerminated(this, exception);
    }


    // Simple accessors callable from pool and/or public static methods.

    final void setIndex(int i) { // called only from pool
        index = i;
    }

    final int getIndex() {
        return index;
    }

    final int getSteals() {
        return stealCount;
    }

    final void shouldStop() {
        shouldStop = true;
    }

    final boolean isStopped() {
        return shouldStop;
    }

    final boolean queueIsEmpty() {
        return base >= sp;
    }

    final int getQueueSize() {
        long n = sp - base;
        return n < 0? 0 : (int)n; // suppress momentarily negative values
    }

    // Active/Idle state maintenance

    /**
     * The maximum scan value, needed to bound sleep times and avoid
     * wrapping negative. Must be power of two minus one.
     */
    private static final int MAX_SCANS = (1 << 16) - 1;

    /**
     * Number of idle scans between calls to Thread.yield.  Must
     * be power of two minus one. Value is a heuristic compromise
     * across facts that yield may or may not actually have any
     * effect, and yielding too frequently may have detrimental
     * effect.
     */
    private static final int SCANS_PER_YIELD = (1 << 7) - 1;

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
     * Reports true unless this thread has unsuccessfully tried to
     * obtain a task to run.
     */
    final boolean isActive() {
        return idleCount == 0;
    }

    /**
     * Sets status to represent that this worker is active running
     * a task
     */
    private void clearIdleCount() {
        if (idleCount != 0) {
            idleCount = 0;
            pool.workerActive();
        }
    }

    /**
     * Increments idle count upon unsuccessful scan.
     */
    private void advanceIdleCount() {
        int next = idleCount + 1;
        if (next >= MAX_SCANS)
            next = MAX_SCANS;
        idleCount = next;
        if (next == 1)
            pool.workerIdle();
        else if ((next & SCANS_PER_YIELD) == 0)
            Thread.yield(); 
    }

    /**
     * Possibly sleeps (via parkNanos) upon failure to get work
     */
    private void maybeSleep() {
        int c = idleCount;
        if ((c & SCANS_PER_SLEEP) != SCANS_PER_SLEEP)
            return;

        long nanos = c * SLEEP_NANOS_PER_SCAN;
        int s = sleepStatus;
        if (!sleepStatusUpdater.compareAndSet
            (this, s, IDLE_SLEEPING | ON_SLEEP_QUEUE))
            return;
        
        if ((s & ON_SLEEP_QUEUE) == 0) {
            AtomicReference<ForkJoinWorkerThread> idleSleepQueue =
                pool.getIdleSleepQueue();
            while ((sleepStatus & ON_SLEEP_QUEUE) != 0) {
                ForkJoinWorkerThread h = idleSleepQueue.get();
                nextSleeper = h;
                if (idleSleepQueue.compareAndSet(h, this)) 
                    break;
            }
        }

        if ((sleepStatus & IDLE_SLEEPING) != 0) {
            LockSupport.parkNanos(nanos);
            for (;;) {
                int ss = sleepStatus;
                int nexts = ss & ~IDLE_SLEEPING;
                if (ss == nexts ||
                    sleepStatusUpdater.compareAndSet(this, ss, nexts))
                    break;
            }
        }
    }

    /**
     * Wakes up threads sleeping waiting for work. 
     */
    private void wakeupSleepers() {
        AtomicReference<ForkJoinWorkerThread> idleSleepQueue =
            pool.getIdleSleepQueue();
        ForkJoinWorkerThread h;
        while ((h = idleSleepQueue.get()) != null) {
            if (idleSleepQueue.compareAndSet(h, h.nextSleeper)) {
                for (;;) {
                    int s = h.sleepStatus;
                    if (sleepStatusUpdater.compareAndSet(h, s, 0)) {
                        if ((s & IDLE_SLEEPING) != 0)
                            LockSupport.unpark(h);
                        break;
                    }
                }
            }
        }
    }

    // Wrappers to update idleCounts when starting new submissions

    private ForkJoinTask<?> doGetSubmission() {
        ForkJoinTask<?> t = pool.getSubmission();
        if (t != null) 
            clearIdleCount();
        return t;
    }

    private ForkJoinTask<?> doPollSubmission() {
        ForkJoinTask<?> t = pool.pollSubmission();
        if (t != null) 
            clearIdleCount();
        return t;
    }

    // Main work-stealing queue methods

    /**
     * Pushes a task. Called only by current thread.
     * @param x the task
     */
    private void pushTask(ForkJoinTask<?> x) {
        if (x == null)
            throw new NullPointerException();
        ForkJoinTask<?>[] q = queue;
        if (q != null) {
            int mask = q.length - 1;
            long s = sp;
            if ((int)(s - base) < mask) {
                q[((int)s) & mask] = x;
                sp = s + 1;
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
     * Tries to pop local task, returning if present. Otherwise
     * scans through all workers starting at random index trying
     * to steal a task. If any attempted steal fails due to
     * apparent contention, rescans all workers.
     * @return a task or null if none
     */
    private ForkJoinTask<?> getTask() {
        if (idleCount == 0) { // no use checking on repeated scans
            ForkJoinTask<?> popped = popTask();
            if (popped != null)
                return popped;
        }
        ForkJoinWorkerThread[] ws = pool.getWorkers();
        final int n = ws.length;
        int idx = randomIndex(n);
        int remaining = n;
        while (remaining-- > 0) {
            ForkJoinWorkerThread v = ws[idx];
            if (++idx >= n)
                idx = 0;
            if (v != null && v != this) {
                long b = v.base;
                ForkJoinTask<?>[] q;
                if (b < v.sp && (q = v.queue) != null) {
                    clearIdleCount(); // must clear even if fail CAS
                    int slot = ((int)b) & (q.length-1);
                    ForkJoinTask<?> t = q[slot];
                    if (t != null) {
                        long nextb = b + 1;
                        if (baseUpdater.compareAndSet(v, b, nextb)) {
                            // slotUpdater.compareAndSet(q, slot, t, null);
                            if (nextb < v.sp)
                                wakeupSleepers();
                            ++stealCount;
                            t.setStolen();
                            return t;
                        }
                    }
                    remaining = n; // rescan upon contention
                    idx = index;   // restart at own index
                }
            }
        }
        advanceIdleCount();
        return null;
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
     * Adds the given task as the next task scheduled for execution in
     * the current worker's local queue.
     * @param task the task
     * @throws NullPointerException if task is null
     */
    public static void addLocalTask(ForkJoinTask<?> task) {
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
        return ((ForkJoinWorkerThread)(Thread.currentThread())).getTask();
    }

    /**
     * Removes and returns, without executing, the next pool
     * submission available to the current worker.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollSubmission() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).doPollSubmission();
    }

    /**
     * Removes and returns, without executing, the next task queued
     * for execution by the current worker, if one exists, or a pool
     * submission, if one exists.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollTaskOrSubmission() {
        ForkJoinWorkerThread w = 
            (ForkJoinWorkerThread)(Thread.currentThread());
        ForkJoinTask<?> t = w.getTask();
        if (t == null) 
            t = w.doPollSubmission();
        return t;
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
     * Executes a local or stolen task, if one is available.  This
     * method may be useful when several tasks are forked, and only
     * one of them must be joined, as in:
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
            ((ForkJoinWorkerThread)(Thread.currentThread())).getTask();
        if (t == null)
            return false;
        else {
            t.exec();
            return true;
        }
    }

    /**
     * Commences execution of a pool submission, if one exists.
     * @return true if a task was run; a false return indicates that
     * no pool submission was available.
     */
    public static boolean executeSubmission() {
        ForkJoinTask<?> t = pollSubmission();
        if (t == null)
            return false;
        else {
            t.exec();
            return true;
        }
    }

    /**
     * Helps this program complete by processing a ready task, if one
     * is available, or if not available, commencing execution of a
     * pool submission, if one exists.
     * @return true if a task was run; a false return indicates that
     * no task or pool submission was available.
     */
    public static boolean executeTaskOrSubmission() {
        ForkJoinTask<?> t = pollTaskOrSubmission();
        if (t == null)
            return false;
        else {
            t.exec();
            return true;
        }
    }

}
