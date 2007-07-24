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
    private final ForkJoinPool.EventBarrier eventBarrier;

    /**
     * Pool-wide count of active workers, cached from pool upon
     * construction
     */
    private final AtomicInteger activeWorkerCounter;

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
     * methods are written in a way that allows them to instead be
     * lazily allocated (see growQueue) and/or disposed of when empty.
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
    // slotUpdater = new AtomicReferenceArrayUpdater<ForkJoinTask<?>>();

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
     * Index of this worker in pool array. Set once by pool before running.
     */
    private int poolIndex;

    /**
     * Number of scans since last successfully getting a task.  Always
     * zero when busy executing tasks.  Incremented after a failed
     * scan in getTask(), to help control spins and maintain active
     * worker count. On transition from or to sero, the pool's
     * activeWorkerCounter must be adjusted (see activate
     * and inactivate). 
     */
    private int scans;

    /**
     * Ceiling for scans. Serves as threshold for blocking when
     * threads cannot find work. This value must be at least 2.  Scans
     * are usually fast and nearly read-only, so there's no reason to
     * make this value especially small.
     */
    private static final int MAX_SCANS = (1 << 8);

    /**
     * Number of steals, just for monitoring purposes, although its
     * volatile-ness is exploited in scanForTask.
     */
    private volatile long stealCount;

    /**
     * Number of calls to eventBarrier.await, just for monitoring
     * purposes.
     */
    private volatile long syncCount;

    /**
     * Seed for random number generator for choosing steal victims
     */
    private long randomSeed;

    /**
     * The last event count waited for
     */
    private long eventCount;

    /**
     * Run state of this worker, set only by pool (except for termination)
     */
    private final ForkJoinPool.RunState runState;

    /**
     * Optional first task to run before main loop, set by pool
     */
    private ForkJoinTask<?> firstTask;

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

    // misc internal utilities, most callable from pool.

    /**
     * Returns a random non-negative value for choosing thread to
     * steal from.  This doesn't need to be a high quality generator.
     * So it isn't -- it uses a simple Marsaglia xorshift.
     */
    private int nextRandom() {
        long rand = randomSeed;
        rand ^= rand << 13;
        rand ^= rand >>> 7;
        rand ^= rand << 17;
        randomSeed = rand;
        return ((int)(rand >>> 32)) & 0x7fffffff;
    }

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
        return stealCount;
    }

    final long getWorkerSyncCount() {
        return syncCount;
    }

    final ForkJoinPool.RunState getRunState() {
        return runState;
    }

    private boolean queueIsEmpty() {
        return base >= sp;
    }

    final int getQueueSize() {
        long n = sp - base;
        return n < 0? 0 : (int)n; // suppress momentarily negative values
    }

    /**
     * Ensure active status. Active status must be set when executing
     * tasks, and BEFORE stealing or taking submission.  To avoid
     * continual flickering, this is done only if worker or submission
     * queues appear to be non-empty.
     */
    private void activate() {
        if (scans != 0) {
            scans = 0;
            activeWorkerCounter.incrementAndGet();
        }
    }

    /**
     * Ensure inActive status, set when worker cannot find work, as
     * well as upon termination.
     * @return current scan value;
     */
    private int inactivate() {
        int s = ++scans;
        if (s == 1)
            activeWorkerCounter.decrementAndGet();
        return s;
    }

    /**
     * Reports true if this thread has no tasks and has unsuccessfully
     * tried to obtain a task to run.
     */
    final boolean workerIsIdle() {
        return  base >= sp && scans != 0;
    }

    /**
     * Execute task if nonnull. 
     */
    private static boolean execTask(ForkJoinTask<?> task) {
        if (task == null)
            return false;
        task.exec();
        return true;
    }

    /**
     * Executes optional first task
     * @return true if thread should exit
     */
    private boolean execFirstTask() {
        ForkJoinTask<?> t = firstTask;
        if (t == null)
            return false;
        firstTask = null;
        t.exec();
        return exitOnFirstTaskCompletion;
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
        this.eventBarrier = pool.getEventBarrier();
        this.runState = new ForkJoinPool.RunState();
        long seed = getId() - System.nanoTime();
        this.randomSeed = (seed == 0)? 1 : seed; // must be nonzero
        this.queue = new ForkJoinTask<?>[INITIAL_CAPACITY];
        activeWorkerCounter.incrementAndGet(); // initially active state
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
            if (execFirstTask())
                return;
            while (runState.isRunning())
                execTask(getTask(IDLING));
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
        cancelTasks();
        inactivate();
        runState.transitionToTerminated();
        pool.workerTerminated(this);
    }

    // Main work-stealing queue methods

    /**
     * Pushes a task. Also wakes up other workers if queue was
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
                    eventBarrier.signal();
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
    private void growQueue(ForkJoinTask<?> task) {
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
            throw new RejectedExecutionException("Queue capacity exceeded");
        // Might throw OutOfMemoryError, which will be trapped later
        ForkJoinTask<?>[] newQ = new ForkJoinTask<?>[newSize];
        int newMask = newSize - 1;
        long s = sp;
        if (oldQ != null) {
            int oldMask = oldSize - 1;
            for (long i = nextSlotToClean = base; i < s; ++i)
                newQ[((int)i) & newMask] = oldQ[((int)i) & oldMask];
        }
        sp = s; // need volatile write here just to force ordering
        newQ[((int)s) & newMask] = task;
        queue = newQ;
        sp = s + 1;
        eventBarrier.signal();
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
     * @param task the task to match, null OK (but never matched)
     */
    private boolean popIfNext(ForkJoinTask<?> task) {
        boolean popped = false;
        ForkJoinTask<?>[] q = queue;
        if (task != null  && q != null) { // always false for null task
            long s = sp - 1;
            int idx = ((int)s) & (q.length-1);
            if (q[idx] == task) {
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
     * Currently used only for cancellation -- scanForTask embeds
     * a variant with contention control for other cases.
     * @return a task, or null if none
     */
    private ForkJoinTask<?> tryStealTask() {
        long b; // (note read order in condition below)
        ForkJoinTask<?>[] q;
        if ((b = base) < sp && (q = queue) != null) {
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
     * Tries to steal a task from another worker.  Starting at a
     * random index of workers array, probes (circularly) consecutive
     * workers until finding one with non-empty queue. If attempt to
     * steal task from this worker fails due to contention, the loop
     * restarts but this time at the worker one past the caller's own
     * index which scatters accesses and thus reduces contention.
     * @return a task, or null if none
     */
    private ForkJoinTask<?> scanForTask() {
        ForkJoinWorkerThread[] ws = pool.getWorkers();
        int n = ws.length;
        int remaining = n - 1;
        if (remaining > 0) {            // skip for size 1 to simplify
            int rnd = nextRandom();     // avoid "%" on power of 2
            int idx = (n & remaining) == 0? (rnd & remaining) : (rnd % n);
            
            while (remaining-- >= 0) {  // (0-base to simplify restart)
                if (++idx >= n)
                    idx = 0;
                ForkJoinWorkerThread v = ws[idx];
                if (v != null && v != this) {
                    long b = v.base;
                    ForkJoinTask<?>[] q;
                    if (b < v.sp && (q = v.queue) != null) {
                        activate(); // required even if fail to get task
                        int slot = ((int)b) & (q.length-1);
                        ForkJoinTask<?> t = q[slot];
                        if (t == null ||     // contention
                            !baseUpdater.compareAndSet(v, b, b+1)) {
                            remaining = n;   // restart at own index
                            idx = poolIndex; //  to scatter contention
                        }
                        else {
                            // slotUpdater.compareAndSet(q, slot, t, null);
                            t.setStolen();
                            ++stealCount; // also serves as barrier 
                            return t;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Tries to get a task to run; possibly blocking on failure
     * @param context: IDLING, JOINING, POLLING
     * @return a task or null if none
     */
    private ForkJoinTask<?> getTask(int context) {
        ForkJoinTask<?> t;
        // Try local, then stolen, then submitted task
        if (scans == 0 && (t = popTask()) != null)
            return t;
        if ((t = scanForTask()) != null)
            return t;
        if (pool.hasQueuedSubmissions()) {
            activate(); // required even if another worker gets task
            if ((t = pool.pollSubmission()) != null)
                return t;
        }

        if (context == JOINING && runState.isAtLeastStopping()) 
            throw new CancellationException(); // abort join if killed

        if (inactivate() >= MAX_SCANS) {
            scans = 1; // set to spin on wakeup
            if (context != POLLING) {
                ++syncCount;
                eventCount = eventBarrier.await(eventCount);
            }
        }

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
        return execTask(((ForkJoinWorkerThread)(Thread.currentThread())).popTask());
    }

    /**
     * Removes and returns, without executing, the next task available
     * for execution by the current worker thread, which may be a
     * locally queued task, one stolen from another worker, or a pool
     * submission.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollTask() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).getTask(POLLING);
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
        return execTask(((ForkJoinWorkerThread)(Thread.currentThread())).getTask(POLLING));
    }

    // Package-local support for core ForkJoinTask methods

    /**
     * Called only from ForkJoinTask.setDone, upon completion
     * of stolen or cancelled task.
     */
    static void signalTaskCompletion() {
        ((ForkJoinWorkerThread)(Thread.currentThread())).eventBarrier.signal();
    }

    /**
     * Implements ForkJoinTask.join
     */
    final <T> T doJoinTask(ForkJoinTask<T> joinMe) {
        for (;;) {
            Throwable ex = joinMe.exception; // need only as barrier
            int s = joinMe.status;
            ex = joinMe.exception;
            if (ex != null)
                ForkJoinTask.rethrowException(ex);
            if (s < 0)
                return joinMe.getResult();
            ForkJoinTask<?> t = getTask(JOINING);
            if (t != null)
                t.exec();
        }
    }

    /**
     * Implements ForkJoinTask.quietlyJoin
     */
    final Throwable doQuietlyJoinTask(ForkJoinTask<?> joinMe) {
        for (;;) {
            Throwable ex = joinMe.exception; // need only as barrier
            int s = joinMe.status;
            ex = joinMe.exception;
            if (ex != null || s < 0)
                return ex;
            ForkJoinTask<?> t = getTask(JOINING);
            if (t != null)
                t.exec();
        }
    }

    /**
     * Implements RecursiveAction.coInvoke
     */
    final void doCoInvoke(RecursiveAction t1, RecursiveAction t2) {
        pushTask(t2);
        Throwable ex = t1.execKnownLocal();
        boolean stolen = !popIfNext(t2);
        if (ex != null ||
            ((ex = stolen? doQuietlyJoinTask(t2) : t2.execKnownLocal()) != null)) {
            t2.cancel();
            ForkJoinTask.rethrowException(ex);
        }
    }

}
