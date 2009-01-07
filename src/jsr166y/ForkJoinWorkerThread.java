/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import sun.misc.Unsafe;
import java.lang.reflect.*;

/**
 * A thread managed by a {@link ForkJoinPool}.  This class is
 * subclassable solely for the sake of adding functionality -- there
 * are no overridable methods dealing with scheduling or
 * execution. However, you can override initialization and termination
 * cleanup methods surrounding the main task processing loop.  If you
 * do create such a subclass, you will also need to supply a custom
 * ForkJoinWorkerThreadFactory to use it in a ForkJoinPool.
 * 
 * <p>This class also provides methods for generating per-thread
 * random numbers, with the same properties as {@link
 * java.util.Random} but with each generator isolated from those of
 * other threads.
 */
public class ForkJoinWorkerThread extends Thread {
    /*
     * Algorithm overview:
     *
     * 1. Work-Stealing: Work-stealing queues are special forms of
     * Deques that support only three of the four possible
     * end-operations -- push, pop, and deq (aka steal), and only do
     * so under the constraints that push and pop are called only from
     * the owning thread, while deq may be called from other threads.
     * (If you are unfamiliar with them, you probably want to read
     * Herlihy and Shavit's book "The Art of Multiprocessor
     * programming", chapter 16 describing these in more detail before
     * proceeding.)  The main work-stealing queue design is roughly
     * similar to "Dynamic Circular Work-Stealing Deque" by David
     * Chase and Yossi Lev, SPAA 2005
     * (http://research.sun.com/scalable/pubs/index.html).  The main
     * difference ultimately stems from gc requirements that we null
     * out taken slots as soon as we can, to maintain as small a
     * footprint as possible even in programs generating huge numbers
     * of tasks. To accomplish this, we shift the CAS arbitrating pop
     * vs deq (steal) from being on the indices ("base" and "sp") to
     * the slots themselves (mainly via method "casSlotNull()"). So,
     * both a successful pop and deq mainly entail CAS'ing a nonnull
     * slot to null.  Because we rely on CASes of references, we do
     * not need tag bits on base or sp.  They are simple ints as used
     * in any circular array-based queue (see for example ArrayDeque).
     * Updates to the indices must still be ordered in a way that
     * guarantees that (sp - base) > 0 means the queue is empty, but
     * otherwise may err on the side of possibly making the queue
     * appear nonempty when a push, pop, or deq have not fully
     * committed. Note that this means that the deq operation,
     * considered individually, is not wait-free. One thief cannot
     * successfully continue until another in-progress one (or, if
     * previously empty, a push) completes.  However, in the
     * aggregate, we ensure at least probablistic non-blockingness. If
     * an attempted steal fails, a thief always chooses a different
     * random victim target to try next. So, in order for one thief to
     * progress, it suffices for any in-progress deq or new push on
     * any empty queue to complete. One reason this works well here is
     * that apparently-nonempty often means soon-to-be-stealable,
     * which gives threads a chance to activate if necessary before
     * stealing (see below).
     *
     * Efficient implementation of this approach currently relies on
     * an uncomfortable amount of "Unsafe" mechanics. To maintain
     * correct orderings, reads and writes of variable base require
     * volatile ordering.  Variable sp does not require volatile write
     * but needs cheaper store-ordering on writes.  Because they are
     * protected by volatile base reads, reads of the queue array and
     * its slots do not need volatile load semantics, but writes (in
     * push) require store order and CASes (in pop and deq) require
     * (volatile) CAS semantics. Since these combinations aren't
     * supported using ordinary volatiles, the only way to accomplish
     * these effciently is to use direct Unsafe calls. (Using external
     * AtomicIntegers and AtomicReferenceArrays for the indices and
     * array is significantly slower because of memory locality and
     * indirection effects.) Further, performance on most platforms is
     * very sensitive to placement and sizing of the (resizable) queue
     * array.  Even though these queues don't usually become all that
     * big, the initial size must be large enough to counteract cache
     * contention effects across multiple queues (especially in the
     * presence of GC cardmarking). Also, to improve thread-locality,
     * queues are currently initialized immediately after the thread
     * gets the initial signal to start processing tasks.  However,
     * all queue-related methods except pushTask are written in a way
     * that allows them to instead be lazily allocated and/or disposed
     * of when empty. All together, these low-level implementation
     * choices produce as much as a factor of 4 performance
     * improvement compared to naive implementations, and enable the
     * processing of billions of tasks per second, sometimes at the
     * expense of ugliness.
     *
     * 2. Run control: The primary run control is based on a global
     * counter (activeCount) held by the pool. It uses an algorithm
     * similar to that in Herlihy and Shavit section 17.6 to cause
     * threads to eventually block when all threads declare they are
     * inactive. (See variable "scans".)  For this to work, threads
     * must be declared active when executing tasks, and before
     * stealing a task. They must be inactive before blocking on the
     * Pool Barrier (awaiting a new submission or other Pool
     * event). In between, there is some free play which we take
     * advantage of to avoid contention and rapid flickering of the
     * global activeCount: If inactive, we activate only if a victim
     * queue appears to be nonempty (see above).  Similarly, a thread
     * tries to inactivate only after a full scan of other threads.
     * The net effect is that contention on activeCount is rarely a
     * measurable performance issue. (There are also a few other cases
     * where we scan for work rather than retry/block upon
     * contention.)
     *
     * 3. Selection control. We maintain policy of always choosing to
     * run local tasks rather than stealing, and always trying to
     * steal tasks before trying to run a new submission. All steals
     * are currently performed in randomly-chosen deq-order. It may be
     * worthwhile to bias these with locality / anti-locality
     * information, but doing this well probably requires more
     * lower-level information from JVMs than currently provided.
     */

    /**
     * Capacity of work-stealing queue array upon initialization.
     * Must be a power of two. Initial size must be at least 2, but is
     * padded to minimize cache effects.
     */
    private static final int INITIAL_QUEUE_CAPACITY = 1 << 13;

    /**
     * Maximum work-stealing queue array size.  Must be less than or
     * equal to 1 << 30 to ensure lack of index wraparound.
     */
    private static final int MAXIMUM_QUEUE_CAPACITY = 1 << 30;

    /**
     * Generator of seeds for per-thread random numbers.
     */
    private static final Random randomSeedGenerator = new Random();

    /**
     * The work-stealing queue array. Size must be a power of two.
     */
    private ForkJoinTask<?>[] queue;

    /**
     * Index (mod queue.length) of next queue slot to push to or pop
     * from. It is written only by owner thread, via ordered store.
     * Both sp and base are allowed to wrap around on overflow, but
     * (sp - base) still estimates size.
     */
    private volatile int sp;

    /**
     * Index (mod queue.length) of least valid queue slot, which is
     * always the next position to steal from if nonempty.
     */
    private volatile int base;

    /**
     * The pool this thread works in.
     */
    final ForkJoinPool pool;

    /**
     * Index of this worker in pool array. Set once by pool before
     * running, and accessed directly by pool during cleanup etc
     */
    int poolIndex;

    /**
     * Run state of this worker. Supports simple versions of the usual
     * shutdown/shutdownNow control.
     */
    private volatile int runState;

    // Runstate values. Order matters
    private static final int RUNNING     = 0;
    private static final int SHUTDOWN    = 1;
    private static final int TERMINATING = 2;
    private static final int TERMINATED  = 3;

    /**
     * Activity status. When true, this worker is considered active.
     * Must be false upon construction. It must be true when executing
     * tasks, and BEFORE stealing a task. It must be false before
     * blocking on the Pool Barrier.
     */
    private boolean active;

    /**
     * Number of steals, transferred to pool when idle
     */
    private int stealCount;

    /**
     * Seed for random number generator for choosing steal victims
     */
    private int randomVictimSeed;

    /**
     * Seed for embedded Jurandom
     */
    private long juRandomSeed;

    /**
     * The last barrier event waited for
     */
    private long eventCount;

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null
     */
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        if (pool == null) throw new NullPointerException();
        this.pool = pool;
        // remaining initialization deferred to onStart
    }

    // public access methods 

    /**
     * Returns the pool hosting the current task execution.
     * @return the pool
     */
    public static ForkJoinPool getPool() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).pool;
    }

    /**
     * Returns the index number of the current worker thread in its
     * pool.  The returned value ranges from zero to the maximum
     * number of threads (minus one) that have ever been created in
     * the pool.  This method may be useful for applications that
     * track status or collect results on a per-worker basis.
     * @return the index number.
     */
    public static int getPoolIndex() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).poolIndex;
    }

    //  Access methods used by Pool

    /**
     * Get and clear steal count for accumulation by pool.  Called
     * only when known to be idle (in pool.sync and termination).
     */
    final int getAndClearStealCount() {
        int sc = stealCount;
        stealCount = 0;
        return sc;
    }

    /**
     * Returns estimate of the number of tasks in the queue, without
     * correcting for transient negative values
     */
    final int getRawQueueSize() {
        return sp - base;
    }

    // Intrinsics-based support for queue operations.
    // Currently these three (setSp, setSlot, casSlotNull) are
    // usually manually inlined to improve performance

    /**
     * Sets sp in store-order.
     */
    private void setSp(int s) {
        _unsafe.putOrderedInt(this, spOffset, s);
    }

    /**
     * Add in store-order the given task at given slot of q to
     * null. Caller must ensure q is nonnull and index is in range.
     */
    private static void setSlot(ForkJoinTask<?>[] q, int i,
                                ForkJoinTask<?> t){
        _unsafe.putOrderedObject(q, (i << qShift) + qBase, t);
    }

    /**
     * CAS given slot of q to null. Caller must ensure q is nonnull
     * and index is in range.
     */
    private static boolean casSlotNull(ForkJoinTask<?>[] q, int i,
                                       ForkJoinTask<?> t) {
        return _unsafe.compareAndSwapObject(q, (i << qShift) + qBase, t, null);
    }

    // Main queue methods

    /**
     * Pushes a task. Called only by current thread.
     * @param t the task. Caller must ensure nonnull
     */
    final void pushTask(ForkJoinTask<?> t) {
        ForkJoinTask<?>[] q = queue;
        int mask = q.length - 1;
        int s = sp;
        _unsafe.putOrderedObject(q, ((s & mask) << qShift) + qBase, t);
        _unsafe.putOrderedInt(this, spOffset, ++s);
        if ((s -= base) == 1)
            pool.signalNonEmptyWorkerQueue();
        else if (s >= mask)
            growQueue();
    }

    /**
     * Tries to take a task from the base of the queue, failing if
     * either empty or contended.
     * @return a task, or null if none or contended.
     */
    private ForkJoinTask<?> deqTask() {
        ForkJoinTask<?>[] q;
        ForkJoinTask<?> t;
        int i;
        int b;
        if (sp != (b = base) &&
            (q = queue) != null && // must read q after b
            (t = q[i = (q.length - 1) & b]) != null &&
            _unsafe.compareAndSwapObject(q, (i << qShift) + qBase, t, null)) {
            base = b + 1;
            return t;
        }
        return null;
    }

    /**
     * Returns a popped task, or null if empty.  Called only by
     * current thread.
     */
    final ForkJoinTask<?> popTask() {
        ForkJoinTask<?> t;
        int i;
        ForkJoinTask<?>[] q = queue;
        int mask = q.length - 1;
        int s = sp;
        if (s != base &&
            (t = q[i = (s - 1) & mask]) != null &&
            _unsafe.compareAndSwapObject(q, (i << qShift) + qBase, t, null)) {
            _unsafe.putOrderedInt(this, spOffset, s - 1);
            return t;
        }
        return null;
    }

    /**
     * Specialized version of popTask to pop only if
     * topmost element is the given task. Called only
     * by current thread.
     * @param t the task. Caller must ensure nonnull
     */
    final boolean unpushTask(ForkJoinTask<?> t) {
        ForkJoinTask<?>[] q = queue;
        int mask = q.length - 1;
        int s = sp - 1;
        if (_unsafe.compareAndSwapObject(q, ((s & mask) << qShift) + qBase,
                                         t, null)) {
            _unsafe.putOrderedInt(this, spOffset, s);
            return true;
        }
        return false;
    }

    /**
     * Returns next task to pop.
     */
    final ForkJoinTask<?> peekTask() {
        ForkJoinTask<?>[] q = queue;
        return q == null? null : q[(sp - 1) & (q.length - 1)];
    }

    /**
     * Doubles queue array size. Transfers elements by emulating
     * steals (deqs) from old array and placing, oldest first, into
     * new array.
     */
    private void growQueue() {
        ForkJoinTask<?>[] oldQ = queue;
        int oldSize = oldQ.length;
        int newSize = oldSize << 1;
        if (newSize > MAXIMUM_QUEUE_CAPACITY)
            throw new RejectedExecutionException("Queue capacity exceeded");
        ForkJoinTask<?>[] newQ = queue = new ForkJoinTask<?>[newSize];

        int b = base;
        int bf = b + oldSize;
        int oldMask = oldSize - 1;
        int newMask = newSize - 1;
        do {
            int oldIndex = b & oldMask;
            ForkJoinTask<?> t = oldQ[oldIndex];
            if (t != null && !casSlotNull(oldQ, oldIndex, t))
                t = null;
            setSlot(newQ, b & newMask, t);
        } while (++b != bf);
        pool.signalIdleWorkers(false);
    }

    // Runstate management

    final boolean isShutdown()    { return runState >= SHUTDOWN;  }
    final boolean isTerminating() { return runState >= TERMINATING;  }
    final boolean isTerminated()  { return runState == TERMINATED; }
    final boolean shutdown()      { return transitionRunStateTo(SHUTDOWN); }
    final boolean shutdownNow()   { return transitionRunStateTo(TERMINATING); }

    /**
     * Transition to at least the given state. Return true if not
     * already at least given state.
     */
    private boolean transitionRunStateTo(int state) {
        for (;;) {
            int s = runState;
            if (s >= state)
                return false;
            if (_unsafe.compareAndSwapInt(this, runStateOffset, s, state))
                return true;
        }
    }

    /**
     * Ensure status is active and if necessary adjust pool active count
     */
    final void activate() {
        if (!active) {
            active = true;
            pool.incrementActiveCount();
        }
    }

    /**
     * Ensure status is inactive and if necessary adjust pool active count
     */
    final void inactivate() {
        if (active) {
            active = false;
            pool.decrementActiveCount();
        }
    }

    // Lifecycle methods

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
        juRandomSeed = randomSeedGenerator.nextLong();
        do;while((randomVictimSeed = nextRandomInt()) == 0); // must be nonzero
        if (queue == null)
            queue = new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY];

        // Heuristically allow one initial thread to warm up; others wait
        if (poolIndex < pool.getParallelism() - 1) {
            eventCount = pool.sync(this, 0);
            activate();
        }
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
            clearLocalTasks();
            inactivate();
            cancelTasks();
        } finally {
            terminate(exception);
        }
    }

    /**
     * Notify pool of termination and, if exception is nonnull,
     * rethrow it to trigger this thread's uncaughtExceptionHandler
     */
    private void terminate(Throwable exception) {
        transitionRunStateTo(TERMINATED);
        try {
            pool.workerTerminated(this);
        } finally {
            if (exception != null)
                ForkJoinTask.rethrowException(exception);
        }
    }

    /**
     * Run local tasks on exit from main.
     */
    private void clearLocalTasks() {
        while (base != sp && !pool.isTerminating()) {
            ForkJoinTask<?> t = popTask();
            if (t != null) {
                activate(); // ensure active status
                t.quietlyExec();
            }
        }
    }

    /**
     * Removes and cancels all tasks in queue.  Can be called from any
     * thread.
     */
    final void cancelTasks() {
        while (base != sp) {
            ForkJoinTask<?> t = deqTask();
            if (t != null)
                t.cancelIgnoreExceptions();
        }
    }

    /**
     * This method is required to be public, but should never be
     * called explicitly. It performs the main run loop to execute
     * ForkJoinTasks.
     */
    public void run() {
        Throwable exception = null;
        try {
            onStart();
            while (!isShutdown())
                step();
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            onTermination(exception);
        }
    }

    /**
     * Main top-level action.
     */
    private void step() {
        ForkJoinTask<?> t = sp != base? popTask() : null;
        if (t != null || (t = scan(null, true)) != null) {
            activate();
            t.quietlyExec();
        }
        else {
            inactivate();
            eventCount = pool.sync(this, eventCount);
        }
    }

    // scanning for and stealing tasks

    /**
     * Computes next value for random victim probe. Scans don't
     * require a very high quality generator, but also not a crummy
     * one. Marsaglia xor-shift is cheap and works well.
     *
     * This is currently unused, and manually inlined
     */
    private static int xorShift(int r) {
        r ^= r << 1;
        r ^= r >>> 3;
        r ^= r << 10;
        return r;
    }

    /**
     * Tries to steal a task from another worker and/or, if enabled,
     * submission queue. Starts at a random index of workers array,
     * and probes workers until finding one with non-empty queue or
     * finding that all are empty.  It randomly selects the first n-1
     * probes. If these are empty, it resorts to full circular
     * traversal, which is necessary to accurately set active status
     * by caller. Also restarts if pool barrier has tripped since last
     * scan, which forces refresh of workers array, in case barrier
     * was associated with resize.
     *
     * This method must be both fast and quiet -- usually avoiding
     * memory accesses that could disrupt cache sharing etc other than
     * those needed to check for and take tasks. This accounts for,
     * among other things, updating random seed in place without
     * storing it until exit. (Note that we only need to store it if
     * we found a task; otherwise it doesn't matter if we start at the
     * same place next time.)
     *
     * @param joinMe if non null; exit early if done
     * @param checkSubmissions true if OK to take submissions
     * @return a task, or null if none found
     */
    private ForkJoinTask<?> scan(ForkJoinTask<?> joinMe,
                                 boolean checkSubmissions) {
        ForkJoinPool p = pool;
        if (p == null)                    // Never null, but avoids
            return null;                  //   implicit nullchecks below
        int r = randomVictimSeed;         // extract once to keep scan quiet
        restart:                          // outer loop refreshes ws array
        while (joinMe == null || joinMe.status >= 0) {
            int mask;
            ForkJoinWorkerThread[] ws = p.workers;
            if (ws != null && (mask = ws.length - 1) > 0) {
                int probes = -mask;       // use random index while negative
                int idx = r;
                for (;;) {
                    ForkJoinWorkerThread v;
                    // inlined xorshift to update seed
                    r ^= r << 1;  r ^= r >>> 3; r ^= r << 10;
                    if ((v = ws[mask & idx]) != null && v.sp != v.base) {
                        ForkJoinTask<?> t;
                        activate();
                        if ((joinMe == null || joinMe.status >= 0) &&
                            (t = v.deqTask()) != null) {
                            randomVictimSeed = r;
                            ++stealCount;
                            return t;
                        }
                        continue restart; // restart on contention
                    }
                    if ((probes >> 1) <= mask) // n-1 random then circular
                        idx = (probes++ < 0)? r : (idx + 1);
                    else
                        break;
                }
            }
            if (checkSubmissions && p.hasQueuedSubmissions()) {
                activate();
                ForkJoinTask<?> t = p.pollSubmission();
                if (t != null)
                    return t;
            }
            else {
                long ec = eventCount;     // restart on pool event
                if ((eventCount = p.getEventCount()) == ec)
                    break;
            }
        }
        return null;
    }

    /**
     * Callback from pool.sync to rescan before blocking.  If a
     * task is found, it is pushed so it can be executed upon return.
     * @return true if found and pushed a task
     */
    final boolean prescan() {
        ForkJoinTask<?> t = scan(null, true);
        if (t != null) {
            pushTask(t);
            return true;
        }
        else {
            inactivate();
            return false;
        }
    }

    // Support for ForkJoinTask methods

    /**
     * Implements ForkJoinTask.helpJoin
     */
    final int helpJoinTask(ForkJoinTask<?> joinMe) {
        ForkJoinTask<?> t = null;
        int s;
        while ((s = joinMe.status) >= 0) {
            if (t == null) {
                if ((t = scan(joinMe, false)) == null)  // block if no work
                    return joinMe.awaitDone(this, false);
                // else recheck status before exec
            }
            else {
                t.quietlyExec();
                t = null;
            }
        }
        if (t != null) // unsteal
            pushTask(t);
        return s;
    }

    /**
     * Pops or steals a task
     * @return task, or null if none available
     */
    final ForkJoinTask<?> getLocalOrStolenTask() {
        ForkJoinTask<?> t = popTask();
        return t != null? t : scan(null, false);
    }

    /**
     * Runs tasks until pool isQuiescent
     */
    final void helpQuiescePool() {
        for (;;) {
            ForkJoinTask<?> t = getLocalOrStolenTask();
            if (t != null) {
                activate();
                t.quietlyExec();
            }
            else {
                inactivate();
                if (pool.isQuiescent()) {
                    activate(); // re-activate on exit
                    break;
                }
            }
        }
    }

    /**
     * Returns an estimate of the number of tasks in the queue.
     */
    final int getQueueSize() {
        int b = base;
        int n = sp - b;
        return n <= 0? 0 : n; // suppress momentarily negative values
    }

    /**
     * Returns an estimate of the number of tasks, offset by a
     * function of number of idle workers.
     */
    final int getEstimatedSurplusTaskCount() {
        return (sp - base) - (pool.getIdleThreadCount() >>> 1);
    }

    // Per-worker exported random numbers

    // Same constants as java.util.Random
    final static long JURandomMultiplier = 0x5DEECE66DL;
    final static long JURandomAddend = 0xBL;
    final static long JURandomMask = (1L << 48) - 1;

    private final int nextJURandom(int bits) {
        long next = (juRandomSeed * JURandomMultiplier + JURandomAddend) &
            JURandomMask;
        juRandomSeed = next;
        return (int)(next >>> (48 - bits));
    }

    private final int nextJURandomInt(int n) {
        if (n <= 0)
            throw new IllegalArgumentException("n must be positive");
        int bits = nextJURandom(31);
        if ((n & -n) == n)
            return (int)((n * (long)bits) >> 31);

        for (;;) {
            int val = bits % n;
            if (bits - val + (n-1) >= 0)
                return val;
            bits = nextJURandom(31);
        }
    }

    private final long nextJURandomLong() {
        return ((long)(nextJURandom(32)) << 32) + nextJURandom(32);
    }

    private final long nextJURandomLong(long n) {
        if (n <= 0)
            throw new IllegalArgumentException("n must be positive");
        long offset = 0;
        while (n >= Integer.MAX_VALUE) { // randomly pick half range
            int bits = nextJURandom(2); // 2nd bit for odd vs even split
            long half = n >>> 1;
            long nextn = ((bits & 2) == 0)? half : n - half;
            if ((bits & 1) == 0)
                offset += n - nextn;
            n = nextn;
        }
        return offset + nextJURandomInt((int)n);
    }

    private final double nextJURandomDouble() {
        return (((long)(nextJURandom(26)) << 27) + nextJURandom(27))
            / (double)(1L << 53);
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
            nextJURandom(32);
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
            nextJURandomInt(n);
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
            nextJURandomLong();
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
            nextJURandomLong(n);
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
            nextJURandomDouble();
    }

    // Temporary Unsafe mechanics for preliminary release

    static final Unsafe _unsafe;
    static final long baseOffset;
    static final long spOffset;
    static final long qBase;
    static final int qShift;
    static final long runStateOffset;
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
            runStateOffset = _unsafe.objectFieldOffset
                (ForkJoinWorkerThread.class.getDeclaredField("runState"));
            qBase = _unsafe.arrayBaseOffset(ForkJoinTask[].class);
            int s = _unsafe.arrayIndexScale(ForkJoinTask[].class);
            if ((s & (s-1)) != 0)
                throw new Error("data type scale not a power of two");
            qShift = 31 - Integer.numberOfLeadingZeros(s);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize intrinsics", e);
        }
    }
}
