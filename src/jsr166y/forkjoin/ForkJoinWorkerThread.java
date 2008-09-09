/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import jsr166y.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import sun.misc.Unsafe;
import java.lang.reflect.*;

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
 * <p> This class also includes utility methods for accessing and
 * manipulating submissions to the pool, in support of extensions that
 * provide more extensive error recovery and/or alternate forms of
 * execution.
 *
 * <p> This class is subclassable solely for the sake of adding
 * functionality -- there are no overridable methods dealing with
 * scheduling or execution. However, you can override initialization
 * and termination cleanup methods surrounding the main task
 * processing loop.  If you do create such a subclass, you will also
 * need to supply a custom ForkJoinWorkerThreadFactory to use it in a
 * ForkJoinPool.
 */
public class ForkJoinWorkerThread extends Thread {
    /**
     * Generator for per-thread randomVictimSeeds
     */
    static final Random randomSeedGenerator = new Random();

    /**
     * Exported random numbers
     */
    final JURandom juRandom;

    /**
     * Run state of this worker, set only by pool (except for termination)
     */
    final RunState runState;

    /**
     * The pool this thread works in.
     */
    final ForkJoinPool pool;

    /**
     * Pool-wide sync barrier, cached from pool upon construction
     */
    final PoolBarrier poolBarrier;

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
     * To avoid garbage retention, slots for popped and stolen tasks
     * are nulled out. This is done just by direct nulling in the case
     * of pop, but must use a CAS (in tryStealTask()) for steals to
     * guard against wrongly nulling a slot reused after index
     * wraparound.
     *
     * The length of the queue array must always be a power of
     * two. Even though these queues don't usually become all that
     * big, the initial size must be large enough to counteract cache
     * contention effects across multiple queues.  Currently, they are
     * initialized upon construction.  However, all queue-related
     * methods except pushTask are written in a way that allows them
     * to instead be lazily allocated and/or disposed of when empty.
     *
     * Bear in mind while reading queue support code that we expect to
     * be able to process billions of tasks per second, sometimes at
     * the expense of odd-looking code.
     */
    ForkJoinTask<?>[] queue;

    private static final int INITIAL_CAPACITY = 1 << 13;
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * Index (mod queue.length-1) of next queue slot to push to or pop
     * from. Almost always written via urdered store, which is weaker
     * than volatile write but unfortunately requires direct use of
     * Unsafe.
     */
    volatile long sp;

    /**
     * Index (mod queue.length) of least valid queue slot, which is
     * always the next position to steal from if nonempty.  Updated
     * only via CAS. To keep this field nearby sp field rather than a
     * separate AtomicLong, we need to use Unsafe.
     */
    volatile long base;

    /**
     * Number of steals, just for monitoring purposes,
     */
    volatile long fullStealCount;

    /**
     * The last event event count waited for
     */
    long eventCount;

    /**
     * Seed for random number generator for choosing steal victims
     */
    int randomVictimSeed;

    /**
     * Number of steals, transferred to fullStealCount when idle
     */
    int stealCount;

    /**
     * Seed for juRandom. Kept with worker fields to minimize
     * cacheline sharing
     */
    long juRandomSeed;

    /**
     * Index of this worker in pool array. Set once by pool before running.
     */
    int poolIndex;

    /**
     * Activity status.  Must be true when executing tasks, and BEFORE
     * stealing a task.  Set false after failed scans to help control
     * spins.
     */
    boolean isActive;

    /**
     * The number of consecutive empty calls to getStolenTask since
     * last successful one;
     */
    int emptyScans;

    /**
     * The maximum number of consecutive unsuccessful calls to
     * getStolenTask before possibly deactivating. Must be at least 2.
     * Using a slightly larger value reduces useless blocking more
     * than enough to outweigh useless spinning.
     */
    private static final int SCANS_PER_DEACTIVATE = 4;

    /**
     * Number of empty helping probes before pausing.  Must be a power
     * of two. Currently pauses are implemented only as yield, but may
     * someday incorporate advisory blocking.
     */
    private static final int HELPING_PROBES_PER_PAUSE = (1 << 10);


    // Methods called only by pool

    final void setWorkerPoolIndex(int i) {
        poolIndex = i;
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

    // misc utilities

    /**
     * Computes next value for random victim probe. Scans don't
     * require a very high quality generator, but also not a crummy
     * one. This is cheap and works well.
     */
    static final int xorShift(int r) {
        r ^= r << 1;
        r ^= r >>> 3;
        return r ^ (r << 10);
    }


    final void ensureActive() {
        if (!isActive) {
            isActive = true;
            pool.incrementActiveCount();
        }
    }

    final void ensureInactive() {
        if (isActive) {
            isActive = false;
            pool.decrementActiveCount();
        }
    }

    /**
     * transfer local steal count to volatile field pool can read
     */
    final void transferSteals() {
        int sc = stealCount;
        if (sc != 0) {
            stealCount = 0;
            if (sc < 0)
                sc = Integer.MAX_VALUE; // wraparound
            fullStealCount += sc;
        }
    }

    /**
     * Returns queue length minus #inactive workers.
     */
    final int estimatedSurplusTaskCount() {
        return (int)(sp - base) - pool.getIdleThreadCount();
    }

    /**
     * Resizes queue
     */
    final void growQueue() {
        ForkJoinTask<?>[] oldQ = queue;
        int oldSize = oldQ.length;
        int newSize = oldSize << 1;
        if (newSize > MAXIMUM_CAPACITY)
            throw new RejectedExecutionException("Queue capacity exceeded");
        ForkJoinTask<?>[] newQ = new ForkJoinTask<?>[newSize];
        int oldMask = oldSize - 1;
        int newMask = newSize - 1;
        long s = sp;
        for (long i = base; i < s; ++i)
            newQ[((int)i) & newMask] = oldQ[((int)i) & oldMask];
        _unsafe.putOrderedLong(this, spOffset, s); // store fence
        queue = newQ;
    }

    // Construction and lifecycle methods

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null;
     */
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        this.pool = pool;
        this.poolBarrier = pool.poolBarrier; // cached
        this.juRandomSeed = randomSeedGenerator.nextLong();
        int rseed = randomSeedGenerator.nextInt();
        this.randomVictimSeed = (rseed == 0)? 1 : rseed; // must be nonzero
        this.juRandom = new JURandom();
        this.runState = new RunState();
        this.queue = new ForkJoinTask<?>[INITIAL_CAPACITY];
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
            ensureInactive();
            transferSteals();
            cancelTasks();
            runState.transitionToTerminated();
        } finally {
            pool.workerTerminated(this, exception);
        }
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
            while (runState.isRunning())
                topLevelStep();
            clearLocalTasks();
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            onTermination(exception);
        }
    }

    /**
     * Scans for a task and if one exists, runs it (along with any
     * tasks it in turn pushed).  Checks first for submissions if none
     * currently running, else prefers stolen tasks to new
     * submissions. If none found, possibly deactivates or blocks.
     */
    final void topLevelStep() {
        ForkJoinTask<?> t;
        if ((t = getSubmission(true)) != null ||
            (t = getStolenTask()) != null ||
            (t = getSubmission(false)) != null) {
            do {
                t.exec();
            } while ((t = popTask()) != null);
            emptyScans = 0;
        }
        else {
            long c = poolBarrier.getCount();
            if (emptyScans < SCANS_PER_DEACTIVATE) {
                ++emptyScans;
                eventCount = c;
            }
            else if (!isActive)
                eventCount = poolBarrier.sync(eventCount);
            else if (eventCount == c && pool.tryDecrementActiveCount()) {
                isActive = false;
                transferSteals();
            }
            else
                eventCount = c;
        }
    }

    /**
     * Signal idle threads waiting on poolBarrier.sync.  To avoid
     * needless contention on barrier, this suppresses signals if
     * there are no idle threads. The precheck works because threads
     * must scan one more time after declaring they are they are
     * inactive (see topLevelStep). The loop is also stopped if other
     * threads have already stolen all tasks.
     */
    final void signalAvailability() {
        while (pool.getIdleThreadCount() > 0) {
            if (poolBarrier.trySignal() || sp <= base)
                break;
        }
    }

    // Main work-stealing queue methods

    /**
     * Pushes a task. Also wakes up other workers if queue was
     * previously empty.  Called only by current thread.
     * @param x the task
     */
    final void pushTask(ForkJoinTask<?> x) {
        ForkJoinTask<?>[] q = queue;
        long mask = q.length - 1;
        long s = sp;
        q[(int)(s & mask)] = x;
        _unsafe.putOrderedLong(this, spOffset, s + 1); // store fence
        if ((s -= base) >= mask - 1)
            growQueue();
        else if (s <= 0)
            signalAvailability();
    }

    /**
     * Same as pushTask except doesn't signal and returns old queue
     * length. Mainly used for repushes.
     */
    final long unsignalledPushTask(ForkJoinTask<?> x) {
        ForkJoinTask<?>[] q = queue;
        long mask = q.length - 1;
        long s = sp;
        q[(int)(s & mask)] = x;
        _unsafe.putOrderedLong(this, spOffset, s + 1); // store fence
        if ((s -= base) >= mask - 1)
            growQueue();
        return s;
    }

    /**
     * Returns a popped task, or null if empty.  Called only by
     * current thread.
     */
    final ForkJoinTask<?> popTask() {
        ForkJoinTask<?> x;
        ForkJoinTask<?>[] q = queue;
        long s = sp - 1;
        int idx;
        long b;
        if (q == null || s < base ||
            (x = q[idx = ((int)s) & (q.length-1)]) == null ||
            ((sp = s) <= (b = base) && !arbitratePop(s, b)))
            return null;
        q[idx] = null;
        return x;
    }

    /**
     * Arbitrate an apparently tied or losing attempt to pop item, in
     * either case reconciling sp and base. This is offloaded from ppp
     * to improve performance for typical case where this is not
     * called
     * @return true if popped
     */
    final boolean arbitratePop(long s, long b) {
        boolean w = s < b? false :
            _unsafe.compareAndSwapLong(this, baseOffset, b, ++b);
        _unsafe.putOrderedLong(this, spOffset, b);
        return w;
    }

    /**
     * Specialized version of popTask to pop only if
     * topmost element is the given task.
     * @param task the task to match, null OK (but never matched)
     */
    final boolean popIfNext(ForkJoinTask<?> task) {
        ForkJoinTask<?>[] q = queue;
        long s = sp - 1;
        int idx;
        long b;
        if (task == null || q == null ||
            q[idx = ((int)s) & (q.length-1)] != task ||
            ((sp = s) <= (b = base) && !arbitratePop(s, b)))
            return false;
        q[idx] = null;
        return true;
    }

    /**
     * Returns next task to pop. Called only by current thread.
     */
    final ForkJoinTask<?> peekTask() {
        ForkJoinTask<?>[] q = queue;
        long s = sp - 1;
        return (q == null || s <= base)? null : q[((int)s) & (q.length-1)];
    }

    /**
     * Tries to take a task from the base of the queue.  Returns null
     * upon contention.
     * @return a task, or null if none
     */
    final ForkJoinTask<?> tryStealTask() {
        long b;
        ForkJoinTask<?>[] q;
        ForkJoinTask<?> t;
        int k;
        if ((b = base) >= sp ||
            (q = queue) == null ||
            (t = q[k = ((int)b) & (q.length-1)]) == null ||
            !_unsafe.compareAndSwapLong(this, baseOffset, b, b+1))
            return null;
        // CAS slot to null
        _unsafe.compareAndSwapObject(q, (k<<arrayShift)+arrayBase, t, null);
        return t;
    }

    /**
     * Tries to steal a task from a worker. Starts at a random index
     * of workers array, and probes workers until finding one with
     * non-empty queue or finding that all are empty.  It uses
     * Marsaglia xorshift to generate first n-1 probes. If these are
     * empty, it resorts to n circular traversals (which is necessary
     * to accurately set idle status by caller).
     *
     * This method must be both fast and quiet -- avoiding as much as
     * possible memory accesses that could disrupt cache sharing etc
     * other than those needed to check for and take tasks. This
     * accounts for, among other things, updating random seed in place
     * without storing it until exit.
     *
     * @return a task, or null if none
     */
    final ForkJoinTask<?> getStolenTask() {
        int r = randomVictimSeed;            // extract once to keep scan quiet
        final ForkJoinWorkerThread[] ws = pool.workers;
        final int mask = ws.length - 1;      // must be power of 2 minus 1
        if (mask > 0) {                      // skip for pool size 1
            int probes = -mask;              // use random index while negative
            int idx = r;
            do {
                r = xorShift(r);             // update random seed
                ForkJoinWorkerThread v = ws[mask & idx];
                if (v != null && v.base < v.sp) {
                    if (isActive) {
                        ForkJoinTask<?> t = v.tryStealTask();
                        if (t != null) {
                            randomVictimSeed = r;
                            ++stealCount;
                            return t;
                        }
                    }
                    else if (pool.tryIncrementActiveCount())
                        isActive = true;     // activate and retry
                    else
                        idx = r;
                    probes = -mask;          // restart on contention
                    continue;
                }
                idx = (probes >= 0)? (idx + 1) : r;
            } while (probes++ <= mask);
        }
        return null;
    }

    /**
     * Tries to get a submission, first activating if necessary.
     * @param initial try only if no other tasks apparently running
     * @return a task, or null if none
     */
    final ForkJoinTask<?> getSubmission(boolean initial) {
        while ((!initial ||
                pool.getActiveSubmissionCount() == 0 ||
                pool.getActiveThreadCount() == 0) &&
               pool.mayHaveQueuedSubmissions()) {
            ForkJoinTask<?> t;
            if ((isActive ||
                 (isActive = pool.tryIncrementActiveCount())) &&
                (t = pool.pollSubmission()) != null)
                return t;
        }
        return null;
    }

    // Cleanup support

    /**
     * Run or cancel all local tasks on exit from main.  Exceptions
     * will cause aborts that will eventually trigger cancelTasks.
     */
    final void clearLocalTasks() {
        while (sp > base) {
            ForkJoinTask<?> t = popTask();
            if (t != null) {
                if (runState.isAtLeastStopping())
                    t.setCancelled();
                else
                    t.exec();
            }
        }
    }

    /*
     * Remove (via steal) and cancel all tasks in queue.  Can be
     * called from any thread.
     */
    final void cancelTasks() {
        while (sp > base) {
            ForkJoinTask<?> t = tryStealTask();
            if (t != null) // avoid exceptions due to cancel()
                t.setCancelled();
        }
    }

    // Package-local support for core ForkJoinTask methods

    /**
     * Try to steal tasks while waiting for join.  Similar to
     * getStolenTask except must check completion and run state.
     * @return a task, or null if joinMe is completed
     */
    final ForkJoinTask<?> scanWhileJoining(ForkJoinTask<?> joinMe) {
        ForkJoinWorkerThread[] ws = pool.workers;
        int mask = ws.length - 1;
        int r = randomVictimSeed;
        int idx = r;
        int probes = 0;
        for (;;) {
            ForkJoinTask<?> t;
            r = xorShift(r);
            if (mask <= 0 || runState.isAtLeastStopping()) {
                joinMe.cancel();
                break;
            }
            ForkJoinWorkerThread v = ws[mask & idx];
            if (joinMe.status < 0)
                break;
            if (v != null && (t = v.tryStealTask()) != null){
                randomVictimSeed = r;
                ++stealCount;
                return t;
            }
            if ((++probes & (HELPING_PROBES_PER_PAUSE - 1)) == 0)
                Thread.yield();
            idx = probes <= mask? r : (idx + 1); // n-1 random then circular
        }
        return null;
    }

    /**
     * Implements ForkJoinTask.quietlyJoin
     */
    final void helpJoinTask(ForkJoinTask<?> joinMe) {
        ForkJoinTask<?> t;
        while (joinMe.status >= 0 &&
               ((t = popTask()) != null ||
                (t = scanWhileJoining(joinMe)) != null))
            t.exec();
    }

    /**
     * Implements RecursiveAction.forkJoin
     */
    final void doForkJoin(RecursiveAction t1, RecursiveAction t2) {
        if (t1 == null || t2 == null)
            throw new NullPointerException();
        pushTask(t2);
        if (!t1.exec() || !popIfNext(t2) || !t2.exec()) {
            Throwable ex = t1.getException();
            if (ex != null)
                t2.cancel();
            helpJoinTask(t2);
            if (ex != null || (ex = t2.getException()) != null)
                ForkJoinTask.rethrowException(ex);
        }
    }

    /**
     * Pop or steal a task
     */
    final ForkJoinTask<?> getLocalOrStolenTask() {
        ForkJoinTask<?> t = popTask();
        return t != null? t : getStolenTask();
    }

    /**
     * Timeout version of helpJoin needed for Submission class
     * Returns false if timed out before complated
     */
    final boolean doTimedJoinTask(ForkJoinTask<?> joinMe, long nanos) {
        long startTime = System.nanoTime();
        int spins = 0;
        for (;;) {
            ForkJoinTask<?> t = popTask();
            if (joinMe.isDone())
                return true;
            else if ((t = getLocalOrStolenTask())!= null)
                t.exec();
            else if (runState.isAtLeastStopping())
                return false;
            else if (nanos - (System.nanoTime() - startTime) <= 0)
                return false;
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
        ForkJoinWorkerThread w =
            (ForkJoinWorkerThread)(Thread.currentThread());
        long n = w.sp - w.base;
        return n < 0? 0 : (int)n;
    }

    /**
     * Returns true if there are no local tasks waiting to be run by
     * the current worker thread.
     * @return true if there are no local tasks waiting to be run by
     * the current worker thread.
     */
    public static boolean hasEmptyLocalQueue() {
        ForkJoinWorkerThread w =
            (ForkJoinWorkerThread)(Thread.currentThread());
        return w.sp <= w.base;
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
     * Removes and returns, without executing, the next task queued
     * for execution in the current worker thread's local queue or if
     * none, a task stolen from another worker, if one is available.
     * A null return does not necessarily imply that all tasks are
     * completed, only that there are currently none available.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollTask() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).
            getLocalOrStolenTask();
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
     *
     * @return true if a task was run; a false return indicates
     * that no task was available.
     */
    public static boolean executeTask() {
        return ((ForkJoinWorkerThread)(Thread.currentThread()))
            .doExecuteTask();
    }

    final boolean doExecuteTask() {
        ForkJoinTask<?> t = getLocalOrStolenTask();
        if (t == null)
            return false;
        t.exec();
        return true;
    }

    /**
     * Executes tasks (but not new submissions) until the pool
     * isQuiescent.
     */
    public static void helpQuiesce() {
        ((ForkJoinWorkerThread)(Thread.currentThread())).doHelpQuiesce();
    }

    final void doHelpQuiesce() {
        for (;;) {
            ForkJoinTask<?> t = popTask();
            if (t != null || (t = getStolenTask()) != null) {
                ensureActive();
                t.exec();
            }
            else {
                ensureInactive();
                if (pool.getActiveThreadCount() == 0) {
                    ensureActive(); // reactivate on exit
                    break;
                }
            }
        }
    }

    /**
     * Returns an estimate of how many more locally queued tasks there
     * are than idle worker threads that might steal them.  This value
     * may be useful for heuristic decisions about whether to fork
     * other tasks. In many usages of ForkJoinTasks, at steady state,
     * each worker should aim to maintain a small constant number (for
     * example, 3) stealable tasks, plus more if there are idle
     * workers.
     *
     * <p><b>Sample Usage.</b> Here is a variant version of
     * <tt>compute</tt> for the {@link BinaryAsyncAction} Fib example
     * using getEstimatedSurplusTaskCount to dynamically determine
     * sequential threshold:
     *
     * <pre>
     *   protected void compute() {
     *     Fib f = this;
     *     while (f.n &gt; 1 &amp;&amp;
     *       ForkJoinWorkerThread.getEstimatedSurplusTaskCount() &lt;= 3) {
     *        Fib left = new Fib(f.n - 1);
     *        Fib right = new Fib(f.n - 2);
     *        f.linkSubtasks(left, right);
     *        right.fork(); // fork right
     *        f = left;     // loop on left
     *     }
     *     f.result = sequentiallyComputeFibinacci(f.n);
     *     f.finish();
     *   }
     * }
     * </pre>
     *
     * @return the number of tasks, which is negative if there are
     * fewer tasks than idle workers
     */
    public static int getEstimatedSurplusTaskCount() {
        return ((ForkJoinWorkerThread)(Thread.currentThread()))
            .estimatedSurplusTaskCount();
    }

    /**
     * Removes and returns, without executing, the given task from the
     * queue hosting current execution only if it would be the next
     * task executed by the current worker.  Among other usages, this
     * method may be used to bypass task execution during
     * cancellation.
     *
     * <p><b>Sample Usage,</b> This method may help counterbalance
     * effects of dynamic task thresholding. If using a threshold that
     * typically generates too many tasks, then this method may be
     * used to more cheaply execute excess ones. Here is a dynamically
     * tuned version of the {@link RecursiveAction} Applyer example:
     *
     * <pre>
     * class Applyer extends RecursiveAction {
     *   final double[] array;
     *   final int lo, hi, seqSize;
     *   double result;
     *   Applyer next; // keeps track of right-hand-side tasks
     *   Applyer(double[] array, int lo, int hi, int seqSize, Applyer next) {
     *     this.array = array; this.lo = lo; this.hi = hi;
     *     this.seqSize = seqSize; this.next = next;
     *   }
     *
     *   double atLeaf(int l, int r) {
     *     double sum = 0;
     *     for (int i = l; i &lt; h; ++i) // perform leftmost base step
     *       sum += array[i] * array[i];
     *     return sum;
     *   }
     *
     *   protected void compute() {
     *     int l = lo;
     *     int h = hi;
     *     Applyer right = null;
     *     while (h - l &gt; 1 &amp;&amp;
     *        ForkJoinWorkerThread.getEstimatedSurplusTaskCount() &lt;= 3) {
     *        int mid = (l + h) &gt;&gt;&gt; 1;
     *        right = new Applyer(array, mid, h, seqSize, right);
     *        right.fork();
     *        h = mid;
     *     }
     *     double sum = atLeaf(l, h);
     *     while (right != null &amp;&amp; // direct compute unstolen tasks
     *        ForkJoinWorkerThread.removeIfNextLocalTask(right)) {
     *          sum += right.atLeaf(r.lo, r.hi);
     *          right = right.next;
     *      }
     *     while (right != null) {  // join remaining right-hand sides
     *       right.join();
     *       sum += right.result;
     *       right = right.next;
     *     }
     *     result = sum;
     *   }
     * }
     * </pre>
     *
     * @param task the task
     * @return true if removed
     */
    public static boolean removeIfNextLocalTask(ForkJoinTask<?> task) {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).popIfNext(task);
    }

    /**
     * Declares that there are stealable tasks. This may (or may not)
     * improve ramp-up time when called after forking large numbers of
     * tasks that might not in turn naturally propagate by forking
     * other tasks. Use in other contexts is unlikely to benefit
     * performance.
     */
    public static void advertiseWork() {
        Thread t = Thread.currentThread();
        if (t instanceof ForkJoinWorkerThread)
            ((ForkJoinWorkerThread)t).poolBarrier.signal();
    }

    /**
     * Forks the given task, but does not advertise its existence to
     * idle threads. While this task may be stolen anyway, this method
     * may be useful as a heuristic to improve locality.
     * @param task the task
     * @throws NullPointerException if task is null
     */
    public static void quietlyFork(ForkJoinTask<?> task) {
        if (task == null)
            throw new NullPointerException();
        ((ForkJoinWorkerThread)(Thread.currentThread())).
            unsignalledPushTask(task);
    }

    // Support for alternate handling of submissions

    /**
     * Removes and returns the next unexecuted submission to the given
     * pool, if one is available. To access a submission from the
     * current worker's pool, use <tt>pollSubmission(getPool())</tt>.
     * This method may be useful for draining tasks during exception
     * recovery and for re-assigning work in systems with multiple
     * pools.
     * @param pool the pool
     * @return the next submission, or null if none
     */
    public static Future<?> pollSubmission(ForkJoinPool pool) {
        return pool.pollSubmission();
    }

    /**
     * If the given argument represents a submission to a ForkJoinPool
     * (normally, one returned by <tt>pollSubmission</tt>), returns
     * the actual task submitted to the pool.  This method may be
     * useful for alternate handling of drained submissions..
     * @param submission the submission
     * @return the underlying task
     * @throws IllegalArgumentException if the given future does
     * not represent a submission to a pool
     */
    public static <V> ForkJoinTask<V> getSubmittedTask(Future<V> submission) {
        try {
            return ((Submission)submission).getSubmittedTask();
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * If the argument represents a submission to a ForkJoinPool
     * (normally, one returned by <tt>pollSubmission</tt>), causes it
     * to be ready with the given value returned upon invocation of
     * its <tt>get()</tt> method. This method may be useful for
     * alternate handling of drained submissions..
     * @param submission the submission
     * @param value the result to be returned by the submission
     * @throws IllegalArgumentException if the given future does
     * not represent a submission to a pool
     */
    public static <V> void forceCompletion(Future<V> submission, V value) {
        try {
            ((Submission)submission).finishTask(value);
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException();
        }
    }

    /**
     * If the argument represents a submission to a ForkJoinPool
     * (normally, one returned by <tt>pollSubmission</tt>), causes it
     * to be ready with the given exception thrown on invocation of
     * its <tt>get()</tt> method.This method may be useful for
     * alternate handling of drained submissions..
     * @param submission the submission
     * @param exception the exception to be thrown on access 
     * @throws IllegalArgumentException if the exception is
     * not a RuntimeException or Error
     * @throws IllegalArgumentException if the given future does
     * not represent a submission to a pool
     */
    public static <V> void forceCompletionExceptionally(Future<V> submission, 
                                                        Throwable exception) {
        if (!(exception instanceof RuntimeException) && 
            !(exception instanceof Error))
            throw new IllegalArgumentException();
        try {
            ((Submission)submission).finishTaskExceptionally(exception);
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException();
        }
    }


    // per-worker exported random numbers

    /**
     * A workalike for java.util.Random, but specialized
     * for exporting to users of worker threads.
     */
    final class JURandom { // non-static, use worker seed
        // Guarantee same constants as java.util.Random
        final static long Multiplier = 0x5DEECE66DL;
        final static long Addend = 0xBL;
        final static long Mask = (1L << 48) - 1;

        int next(int bits) {
            long next = (juRandomSeed * Multiplier + Addend) & Mask;
            juRandomSeed = next;
            return (int)(next >>> (48 - bits));
        }

        int nextInt() {
            return next(32);
        }

        int nextInt(int n) {
            if (n <= 0)
                throw new IllegalArgumentException("n must be positive");
            int bits = next(31);
            if ((n & -n) == n)
                return (int)((n * (long)bits) >> 31);

            for (;;) {
                int val = bits % n;
                if (bits - val + (n-1) >= 0)
                    return val;
                bits = next(31);
            }
        }

        long nextLong() {
            return ((long)(next(32)) << 32) + next(32);
        }

        long nextLong(long n) {
            if (n <= 0)
                throw new IllegalArgumentException("n must be positive");
            long offset = 0;
            while (n >= Integer.MAX_VALUE) { // randomly pick half range
                int bits = next(2); // 2nd bit for odd vs even split
                long half = n >>> 1;
                long nextn = ((bits & 2) == 0)? half : n - half;
                if ((bits & 1) == 0)
                    offset += n - nextn;
                n = nextn;
            }
            return offset + nextInt((int)n);
        }

        double nextDouble() {
            return (((long)(next(26)) << 27) + next(27))
                / (double)(1L << 53);
        }
    }

    /**
     * Returns a random integer using a per-worker random
     * number generator with the same properties as
     * {@link java.util.Random#nextInt}
     * @return the next pseudorandom, uniformly distributed {@code int}
     *         value from this worker's random number generator's sequence
     */
    public static int nextRandomInt() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).
            juRandom.nextInt();
    }

    /**
     * Returns a random integer using a per-worker random
     * number generator with the same properties as
     * {@link java.util.Random#nextInt(int)}
     * @param n the bound on the random number to be returned.  Must be
     *        positive.
     * @return the next pseudorandom, uniformly distributed {@code int}
     *         value between {@code 0} (inclusive) and {@code n} (exclusive)
     *         from this worker's random number generator's sequence
     * @throws IllegalArgumentException if n is not positive
     */
    public static int nextRandomInt(int n) {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).
            juRandom.nextInt(n);
    }

    /**
     * Returns a random long using a per-worker random
     * number generator with the same properties as
     * {@link java.util.Random#nextLong}
     * @return the next pseudorandom, uniformly distributed {@code long}
     *         value from this worker's random number generator's sequence
     */
    public static long nextRandomLong() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).
            juRandom.nextLong();
    }

    /**
     * Returns a random integer using a per-worker random
     * number generator with the same properties as
     * {@link java.util.Random#nextInt(int)}
     * @param n the bound on the random number to be returned.  Must be
     *        positive.
     * @return the next pseudorandom, uniformly distributed {@code int}
     *         value between {@code 0} (inclusive) and {@code n} (exclusive)
     *         from this worker's random number generator's sequence
     * @throws IllegalArgumentException if n is not positive
     */
    public static long nextRandomLong(long n) {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).
            juRandom.nextLong(n);
    }

    /**
     * Returns a random double using a per-worker random
     * number generator with the same properties as
     * {@link java.util.Random#nextDouble}
     * @return the next pseudorandom, uniformly distributed {@code double}
     *         value between {@code 0.0} and {@code 1.0} from this
     *         worker's random number generator's sequence
     */
    public static double nextRandomDouble() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).
            juRandom.nextDouble();
    }

    // Temporary Unsafe mechanics for preliminary release

    static final Unsafe _unsafe;
    static final long baseOffset;
    static final long spOffset;
    static final long arrayBase;
    static final int arrayShift;
    static {
        try {
            if (ForkJoinWorkerThread.class.getClassLoader() != null) {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                _unsafe = (Unsafe)f.get(null);
            }
            else
                _unsafe = Unsafe.getUnsafe();
            baseOffset = _unsafe.objectFieldOffset
                (ForkJoinWorkerThread.class.getDeclaredField("base"));
            spOffset = _unsafe.objectFieldOffset
                (ForkJoinWorkerThread.class.getDeclaredField("sp"));
            arrayBase = _unsafe.arrayBaseOffset(ForkJoinTask[].class);
            int s = _unsafe.arrayIndexScale(ForkJoinTask[].class);
            if ((s & (s-1)) != 0)
                throw new Error("data type scale not a power of two");
            arrayShift = 31 - Integer.numberOfLeadingZeros(s);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize intrinsics", e);
        }
    }

}
