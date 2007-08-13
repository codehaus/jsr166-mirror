/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * A thread that is internally managed by a ForkJoinPool to execute
 * ForkJoinTasks. This class additionally provides public
 * <tt>static</tt> methods accessing some basic scheduling and
 * execution mechanics for the <em>current</em>
 * ForkJoinWorkerThread. These methods may be invoked only from within
 * other ForkJoinTask computations. Attempts to invoke in other
 * contexts result in exceptions or errors including
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
 * scheduling or execution. However, you can override initialization
 * and termination cleanup methods surrounding the main task
 * processing loop.  If you do create such a subclass, you will also
 * need to supply a custom ForkJoinWorkerThreadFactory to use it in a
 * ForkJoinPool.
 *
 */
public class ForkJoinWorkerThread extends Thread {
    /**
     * The pool this thread works in.
     */
    final ForkJoinPool pool;

    /**
     * Pool-wide sync barrier, cached from pool upon construction
     */
    private final PoolBarrier poolBarrier;

    /**
     * Pool-wide count of active workers, cached from pool upon
     * construction
     */
    private final AtomicInteger activeWorkerCounter;

    /**
     * Run state of this worker, set only by pool (except for termination)
     */
    private final RunState runState;

    /**
     * Optional first task to run before main loop, set by pool
     */
    private ForkJoinTask<?> firstTask;

    /**
     * Each thread's work-stealing queue is represented via the
     * resizable "queue" array plus base and sp indices.
     * Work-stealing queues are special forms of Deques that support
     * only three of the four possible end-operations -- push, pop,
     * and steal (aka dequeue), and only do so under the constraints
     * that push and pop are called only from the owning thread, while
     * steal may be called from other threads.  The work-stealing
     * queue here uses a variant of the algorithm described in
     * "Dynamic Circular Work-Stealing Deque" by David Chase and Yossi
     * Lev, SPAA 2005. For an explanation, read the paper.
     * (http://research.sun.com/scalable/pubs/index.html). The main
     * differences here stem from ensuring that queue slots
     * referencing popped and stolen tasks are cleared, as well as
     * spin/wait control. (Also. method and variable names differ.)
     *
     * The length of the queue array must always be a power of
     * two. Even though these queues don't usually become all that
     * big, the initial size must be large enough to counteract cache
     * contention effects across multiple queues.  Currently, they are
     * initialized upon construction.  However, all queue-related
     * methods except pushTask are written in a way that allows them
     * to instead be lazily allocated and/or disposed of when empty.
     */
    private ForkJoinTask<?>[] queue;

    private static final int INITIAL_CAPACITY = 1 << 13;
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * Index (mod queue.length-1) of next queue slot to push to
     * or pop from
     */
    private volatile long sp;

    /**
     * Index (mod queue.length) of least valid queue slot, which
     * is always the next position to steal from if nonempty.
     */
    private volatile long base;

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
    //   slotUpdater = new AtomicReferenceArrayUpdater<ForkJoinTask<?>>();

    /**
     * NEEDED FOR JAVA 5/6 VERSION ONLY.
     * Index (mod queue.length) of the next queue slot to null
     * out. Queue slots stolen by other threads cannot be safely
     * nulled out by them. Instead, they are cleared by owning
     * thread whenever queue is empty. Note that this requires
     * stealers to check for null before trying to CAS.
     */
    private long nextSlotToClean;

    /**
     * Number of steals, just for monitoring purposes, 
     */
    private volatile long fullStealCount;

    /**
     * The last event count waited for
     */
    private long eventCount;

    /**
     * Seed for random number generator for choosing steal victims
     */
    private long randomSeed;

    /**
     * Number of steals, transferred to fullStealCount at syncs
     */
    private int stealCount; 

    /**
     * Index of this worker in pool array. Set once by pool before running.
     */
    private int poolIndex;

    /**
     * Number of scans (calls to getTask()) since last successfully
     * getting a task.  Always zero (== "active" status) when busy
     * executing tasks.  Incremented after a failed scan to help
     * control spins and maintain active worker count. On transition
     * from or to sero, the pool's activeWorkerCounter must be
     * adjusted.  Must be zero when executing tasks, and BEFORE
     * stealing a submission.  To avoid continual flickering and
     * contention, this is done only if worker queues appear to be
     * non-empty.
     */
    private int scans;

    /**
     * True if exit after first task completes, as set by pool
     */
    boolean exitOnFirstTaskCompletion;

    // Transient state indicators, used as argument, not field values.

    /**
     * Signifies that the current thread is not currently executing
     * any task. Used only from mainLoop.
     */
    private static final int IDLING   = 0;

    /**
     * Signifies that the current thread is helping while waiting for
     * another task to be joined.
     */
    private static final int JOINING  = 1;

    /**
     * Signifies that the current thread is willing to run a task, but
     * cannot block if none are available.
     */
    private static final int POLLING  = 2;

    /**
     * Threshold for checking with PoolBarrier and possibly blocking
     * when idling threads cannot find work. This value must be at
     * least 3 for scan control logic to work.
     * 
     * Scans for work (see getTask()) are nearly read-only, and likely
     * to be much faster than context switches that may occur within
     * syncs. So there's no reason to make this value especially
     * small. On the other hand, useless scans waste CPU time, and
     * syncs may help wake up other threads that may allow further
     * progress. So the value should not be especially large either.
     */
    private static final int IDLING_SCANS_PER_SYNC = 16; 
    
    /**
     * Threshold for checking with PoolBarrier and possibly blocking
     * when joining threads cannot find work. This value must be at
     * least IDLING_SCANS_PER_SYNC.
     *
     * Unlike idling threads, joining threads "know" that they will be
     * able to continue at some point. However, we still force them to
     * sometimes sync to reduce wasted CPU time in the face of
     * programmer errors as well as to cope better when some worker
     * threads are making very slow progress.
     */
    private static final int JOINING_SCANS_PER_SYNC = 256; 

    /**
     * Generator for per-thread randomSeeds
     */
    private static final Random randomSeedGenerator = new Random();

    // Methods called only by pool

    final void setWorkerPoolIndex(int i) { 
        poolIndex = i;
    }

    final void setFirstTask(ForkJoinTask<?> t, boolean exitOnCompletion) { 
        firstTask = t;
        exitOnFirstTaskCompletion = exitOnCompletion;
    }

    final int getWorkerPoolIndex() {
        return poolIndex;
    }

    final long getWorkerStealCount() {
        return fullStealCount;
    }

    final RunState getRunState() {
        return runState;
    }

    final int getQueueSize() {
        long n = sp - base;
        return n < 0? 0 : (int)n; // suppress momentarily negative values
    }

    /**
     * Reports true if this thread has no tasks and has unsuccessfully
     * tried to obtain a task to run.
     */
    final boolean workerIsIdle() {
        return  base >= sp && scans != 0; // ensure volatile read first
    }

    // Construction and lifecycle methods

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null;
     */
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        if (pool == null)
            throw new NullPointerException();
        this.pool = pool;
        this.activeWorkerCounter = pool.getActiveWorkerCounter();
        this.poolBarrier = pool.getPoolBarrier();
        long seed = randomSeedGenerator.nextLong();
        this.randomSeed = (seed == 0)? 1 : seed; // must be nonzero
        this.runState = new RunState();
        this.queue = new ForkJoinTask<?>[INITIAL_CAPACITY];
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
            execFirstTask();
            while (runState.isRunning()) {
                ForkJoinTask<?> t = getTask();
                if (t == null)
                    onEmptyScan(IDLING);
                else
                    t.exec();
            }
            clearLocalTasks();
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            onTermination(exception);
        }
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
    }

    /**
     * Perform cleanup associated with termination of this worker
     * thread.  If you override this method, you must invoke
     * super.onTermination at the end of the overridden method.
     * 
     * @param exception the exception causing this thread to abort due
     * to an unrecoverable error, or null if completed normally.
     */
    protected void onTermination(Throwable exception) {
        try {
            if (scans == 0) {
                scans = 1;
                activeWorkerCounter.decrementAndGet();
            }
            cancelTasks();
            runState.transitionToTerminated();
        } finally {
            pool.workerTerminated(this, exception);
        }
    }

    /**
     * Primary method for getting a task to run.  Tries local, then
     * stolen, then submitted tasks. 
     * @return a task or null if none
     */
    private ForkJoinTask<?> getTask() {
        ForkJoinTask<?> popped, stolen;
        if ((popped = popTask()) != null)
            return popped;
        else if ((stolen = getStolenTask()) != null)
            return stolen;
        else
            return getSubmission();
    }

    /**
     * Actions to take on failed getTask, depending on calling
     * context.
     * @param context: IDLING, JOINING, or POLLING
     */
    private void onEmptyScan(int context) {
        int s = scans;
        if (s == 0) { // try to inactivate, but give up on contention
            AtomicInteger awc = activeWorkerCounter;
            int c = awc.get();
            if (awc.compareAndSet(c, c-1))
                scans = 1;
        }
        else if (runState.isAtLeastStopping()) { 
            // abort if joining; else return so caller can check state
            if (context == JOINING) 
                throw new CancellationException(); 
        }
        else if (s <= IDLING_SCANS_PER_SYNC ||
                 (s <= JOINING_SCANS_PER_SYNC && context == JOINING))
            scans = s + 1;
        else {
            scans = 1; // reset scans to 1 (not 0) on resumption
            
            // transfer steal counts so pool can read
            int sc = stealCount; 
            if (sc != 0) {
                stealCount = 0;
                if (sc < 0)
                    sc = Integer.MAX_VALUE; // wraparound
                fullStealCount += sc;
            }

            if (context == POLLING)
                Thread.yield();
            else
                eventCount = poolBarrier.sync(this, eventCount);
        }
    }

    // Main work-stealing queue methods

    /*
     * Resizes queue
     */
    private void growQueue() {
        ForkJoinTask<?>[] oldQ = queue;
        int oldSize = oldQ.length;
        int newSize = oldSize << 1;
        if (newSize > MAXIMUM_CAPACITY)
            throw new RejectedExecutionException("Queue capacity exceeded");
        ForkJoinTask<?>[] newQ = new ForkJoinTask<?>[newSize];
        int oldMask = oldSize - 1;
        int newMask = newSize - 1;
        long s = sp;
        for (long i = nextSlotToClean = base; i < s; ++i)
            newQ[((int)i) & newMask] = oldQ[((int)i) & oldMask];
        sp = s; // need volatile write here just to force ordering
        queue = newQ;
    }

    /**
     * Pushes a task. Also wakes up other workers if queue was
     * previously empty.  Called only by current thread.
     * @param x the task
     */
    final void pushTask(ForkJoinTask<?> x) {
        long s = sp;
        ForkJoinTask<?>[] q = queue;
        int mask = q.length - 1;
        q[((int)s) & mask] = x;
        sp = s + 1;
        if ((s -= base) >= mask - 1)
            growQueue();
        else if (s <= 0)
            poolBarrier.signal();
    }

    /**
     * Returns a popped task, or null if empty.  Called only by
     * current thread.
     */
    private ForkJoinTask<?> j7popTask() {
        ForkJoinTask<?> x = null;
        long s = sp - 1;
        ForkJoinTask<?>[] q = queue;
        if (q != null) {
            int idx = ((int)s) & (q.length-1);
            x = q[idx];
            sp = s;
            q[idx] = null; 
            long b = base;
            if (s <= b) {
                if (s < b ||   // note ++b side effect
                    !baseUpdater.compareAndSet(this, b, ++b))
                    x = null;  // lost race vs steal
                sp = b;
            }
        }
        return x;
    }

    /** 
     * NEEDED FOR JAVA 5/6 VERSION ONLY. Null out slots. Call only
     * when known to be empty.
     */
    private void cleanSlots() {
        long i = nextSlotToClean;
        long b = base;
        if (i < b) {
            nextSlotToClean = b;
            int mask;
            ForkJoinTask<?>[] q = queue;
            if (q != null && (mask = q.length-1) > 0) {
                do { q[((int)(i)) & mask] = null; } while (++i < b);
            }
        }
    }
        

    /**
     * NEEDED FOR JAVA 5/6 VERSION ONLY.  Pop or clear any lingering
     * refs to stolen tasks. 
     */
    private ForkJoinTask<?> popTask() {
        ForkJoinTask<?> x = null;
        long s = sp - 1;
        if (s < base) { // empty
            if (nextSlotToClean <= s)
                cleanSlots();
        }
        else {
            ForkJoinTask<?>[] q = queue;
            if (q != null) {
                int idx = ((int)s) & (q.length-1);
                x = q[idx];
                sp = s;
                q[idx] = null; 
                long b = base;
                if (s <= b) {
                    if (s < b ||   // note ++b side effect
                        !baseUpdater.compareAndSet(this, b, ++b))
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
     * @param task the task to match, null OK (but never matched)
     */
    private boolean popIfNext(ForkJoinTask<?> task) {
        if (task != null) {
            long s = sp - 1;
            ForkJoinTask<?>[] q = queue;
            if (q != null) { 
                int idx = ((int)s) & (q.length-1);
                if (q[idx] == task) {
                    sp = s;
                    q[idx] = null;
                    long b = base;
                    if (s > b)
                        return true;
                    if (s == b && baseUpdater.compareAndSet(this, b, ++b)) {
                        sp = b;
                        return true;
                    }
                    sp = b;
                }
            }
        }
        return false;
    }

    /**
     * Tries to take a task from the base of the queue.  Returns null
     * upon contention.
     * @return a task, or null if none
     */
    private ForkJoinTask<?> tryStealTask() {
        long b = base;
        if (b < sp) {
            ForkJoinTask<?>[] q = queue;
            if (q != null) {
                int k = ((int)b) & (q.length-1);
                ForkJoinTask<?> t = q[k];
                if (t != null &&
                    baseUpdater.compareAndSet(this, b, b+1)) {
                    // slotUpdater.compareAndSet(q, k, t, null);
                    t.setStolen();
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Tries to steal a task from a random worker.  
     * @return a task, or null if none
     */
    private ForkJoinTask<?> getStolenTask() {
        ForkJoinWorkerThread v;
        while ((v = randomVictim()) != null) {
            if (scans != 0) { // try to activate
                // If contention while trying to activate, some other
                // thread probably stole this anyway, so rescan
                AtomicInteger awc = activeWorkerCounter;
                int c = awc.get();
                if (!awc.compareAndSet(c, c+1))
                    continue;
                scans = 0;
            }
            ForkJoinTask<?> t = v.tryStealTask();
            if (t != null) {
                ++stealCount;
                return t;
            }
        }
        return null;
    }

    /**
     * Tries to find a worker with a non-empty queue.  Starts at a
     * random index of workers array, and probes workers until finding
     * one with non-empty queue or finding that all are empty.  This
     * doesn't require a very high quality generator, but also not a
     * crummy one.  It relies on a 64 bit Marsaglia xorshift, and uses
     * upper and lower 32 bit words to generate first two probes. If
     * these are both empty, it resorts to incremental circular
     * traversal.
     * @return a worker with (currently) non-empty queue, or null if none
     */
    private ForkJoinWorkerThread randomVictim() {
        int n;
        ForkJoinWorkerThread[] ws = pool.getWorkers();
        if (ws != null && (n = ws.length) > 0) { // skip during shutdown
            long r = randomSeed; // xorshift
            r ^= r << 13;
            r ^= r >>> 7;
            r ^= r << 17;
            randomSeed = r;
            int rlo = ((int)r) >>> 1; // strip sign
            int rhi = (((int)(r >>> 32))) >>> 1;
            int maxIndex = n - 1;
            boolean p2 = (n & maxIndex) == 0;  // avoid "%" on power of 2

            // first probe
            ForkJoinWorkerThread v = ws[p2? (rlo & maxIndex) : (rlo % n)];
            if (v != null && v.base < v.sp)
                return v;

            // second probe followed by linear scan
            int i = p2? (rhi & maxIndex) : (rhi % n);
            int remaining = n;
            do {
                if ((v = ws[i]) != null && v.base < v.sp)
                    return v;
                if (++i >= n)
                    i = 0;
            } while (remaining-- >= 0); // (overshoots to recheck origin)
        }
        return null;
    }

    // misc utilities

    /**
     * Tries to get a submission. Same basic logic as getStolenTask
     * @return a task, or null if none
     */
    private ForkJoinTask<?> getSubmission() {
        ForkJoinPool.SubmissionQueue sq = pool.getSubmissionQueue();
        while (sq.isApparentlyNonEmpty()) {
            if (scans != 0) {
                AtomicInteger awc = activeWorkerCounter;
                int c = awc.get();
                if (!awc.compareAndSet(c, c+1))
                    continue;
                scans = 0;
            }
            ForkJoinTask<?> t = sq.poll();
            if (t != null)
                return t;
        }
        return null;
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
     * Execute optional first task
     */
    private void execFirstTask() {
        ForkJoinTask<?> t = firstTask;
        if (t != null) {
            firstTask = null;
            t.exec();
            if (exitOnFirstTaskCompletion)
                runState.transitionToShutdown();
        }
    }

    /**
     * Run or cancel all local tasks on exit from main.  Exceptions
     * will cause aborts that will eventually trigger cancelTasks.
     */
    private void clearLocalTasks() {
        for (;;) {
            ForkJoinTask<?> t = popTask();
            if (t == null)
                break;
            if (runState.isAtLeastStopping())
                t.cancel();
            else
                t.exec();
        }
    }

    /*
     * Remove (via steal) and cancel all tasks in queue.  Can be
     * called from any thread.
     */
    final void cancelTasks() {
        while (base < sp) {
            ForkJoinTask<?> t = tryStealTask();
            if (t != null) // avoid exceptions due to cancel()
                t.setDoneExceptionally(new CancellationException());
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
     * Returns the index number of the current worker thread in its
     * pool.  The return value is in the range
     * <tt>0...getPool().getPoolSize()-1</tt>.  This method may be
     * useful for applications that track status or collect results
     * per-worker rather than per-task.
     * @return the index number.
     */
    public static int getPoolIndex() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).poolIndex;
    }

    /**
     * Returns the number of tasks waiting to be run by the current
     * worker thread. This value may be useful for heuristic decisions
     * about whether to fork other tasks.
     * @return the number of tasks
     */
    public static int getLocalQueueSize() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).getQueueSize();
    }

    /**
     * Returns, but does not remove or execute, the next task locally
     * queued for execution by the current worker thread. There is no
     * guarantee that this task will be the next one actually returned
     * or executed from other polling or execution methods.
     * @return the next task or null if none
     */
    public static ForkJoinTask<?> peekLocalTask() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).peekTask();
    }

    /**
     * Removes and returns, without executing, the next task queued
     * for execution in the current worker thread's local queue.
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
        t.exec();
        return true;
    }

    /**
     * Removes and returns, without executing, the next task available
     * for execution by the current worker thread, which may be a
     * locally queued task, one stolen from another worker, or a pool
     * submission.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollTask() {
        ForkJoinWorkerThread w = 
            (ForkJoinWorkerThread)(Thread.currentThread());
        ForkJoinTask<?> t = w.getTask();
        if (t == null)
            w.onEmptyScan(POLLING);
        return t;
    }

    /**
     * Helps this program complete by processing a local, stolen or
     * submitted task, if one is available.  This method may be useful
     * when several tasks are forked, and only one of them must be
     * joined, as in:
     * <pre>
     *   while (!t1.isDone() &amp;&amp; !t2.isDone()) 
     *     ForkJoinWorkerThread.executeTask();
     * </pre> 
     * Similarly, you can help process tasks until all computations
     * complete via 
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
        ForkJoinWorkerThread w = 
            (ForkJoinWorkerThread)(Thread.currentThread());
        ForkJoinTask<?> t = w.getTask();
        if (t == null) {
            w.onEmptyScan(POLLING);
            return false;
        }
        t.exec();
        return true;
    }

    // Package-local support for core ForkJoinTask methods

    /**
     * Called only from ForkJoinTask, upon completion
     * of stolen or cancelled task. 
     */
    static void signalTaskCompletion() {
        Thread t = Thread.currentThread();
        if (t instanceof ForkJoinWorkerThread)
            ((ForkJoinWorkerThread)t).poolBarrier.signal();
        // else external cancel -- OK not to signal
    }

    /**
     * Implements ForkJoinTask.join
     */
    final <T> T doJoinTask(ForkJoinTask<T> joinMe) {
        for (;;) {
            Throwable ex = joinMe.exception;
            if (ex != null)
                ForkJoinTask.rethrowException(ex);
            if (joinMe.status < 0)
                return joinMe.getResult();
            ForkJoinTask<?> t = getTask();
            if (t == null)
                onEmptyScan(JOINING);
            else
                t.exec();
        }
    }

    /**
     * Implements ForkJoinTask.quietlyJoin
     */
    final Throwable doQuietlyJoinTask(ForkJoinTask<?> joinMe) {
        for (;;) {
            Throwable ex = joinMe.exception;
            if (ex != null)
                return ex;
            if (joinMe.status < 0)
                return null;
            ForkJoinTask<?> t = getTask();
            if (t == null)
                onEmptyScan(JOINING);
            else
                t.exec();
        }
    }
    
    final void doCoInvoke(RecursiveAction t1, RecursiveAction t2) {
        int touch = t1.status + t2.status; // force null pointer check
        pushTask(t2);
        Throwable ex1 = t1.exec();
        Throwable ex2 = popIfNext(t2)? t2.exec() : doQuietlyJoinTask(t2);
        if (ex2 != null)
            ForkJoinTask.rethrowException(ex2);
        else if (ex1 != null)
            ForkJoinTask.rethrowException(ex1);
    }

}
