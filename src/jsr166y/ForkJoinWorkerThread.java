/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y;

import java.util.concurrent.*;

import java.util.Random;
import java.util.Collection;
import java.util.concurrent.locks.LockSupport;

/**
 * A thread managed by a {@link ForkJoinPool}.  This class is
 * subclassable solely for the sake of adding functionality -- there
 * are no overridable methods dealing with scheduling or execution.
 * However, you can override initialization and termination methods
 * surrounding the main task processing loop.  If you do create such a
 * subclass, you will also need to supply a custom {@link
 * ForkJoinPool.ForkJoinWorkerThreadFactory} to use it in a {@code
 * ForkJoinPool}.
 *
 * @since 1.7
 * @author Doug Lea
 */
public class ForkJoinWorkerThread extends Thread {
    /*
     * Overview:
     *
     * ForkJoinWorkerThreads are managed by ForkJoinPools and perform
     * ForkJoinTasks. This class includes bookkeeping in support of
     * worker activation, suspension, and lifecycle control described
     * in more detail in the internal documentation of class
     * ForkJoinPool. And as described further below, this class also
     * includes special-cased support for some ForkJoinTask
     * methods. But the main mechanics involve work-stealing:
     *
     * Work-stealing queues are special forms of Deques that support
     * only three of the four possible end-operations -- push, pop,
     * and deq (aka steal), under the further constraints that push
     * and pop are called only from the owning thread, while deq may
     * be called from other threads.  (If you are unfamiliar with
     * them, you probably want to read Herlihy and Shavit's book "The
     * Art of Multiprocessor programming", chapter 16 describing these
     * in more detail before proceeding.)  The main work-stealing
     * queue design is roughly similar to those in the papers "Dynamic
     * Circular Work-Stealing Deque" by Chase and Lev, SPAA 2005
     * (http://research.sun.com/scalable/pubs/index.html) and
     * "Idempotent work stealing" by Michael, Saraswat, and Vechev,
     * PPoPP 2009 (http://portal.acm.org/citation.cfm?id=1504186).
     * The main differences ultimately stem from gc requirements that
     * we null out taken slots as soon as we can, to maintain as small
     * a footprint as possible even in programs generating huge
     * numbers of tasks. To accomplish this, we shift the CAS
     * arbitrating pop vs deq (steal) from being on the indices
     * ("base" and "sp") to the slots themselves (mainly via method
     * "casSlotNull()"). So, both a successful pop and deq mainly
     * entail a CAS of a slot from non-null to null.  Because we rely
     * on CASes of references, we do not need tag bits on base or sp.
     * They are simple ints as used in any circular array-based queue
     * (see for example ArrayDeque).  Updates to the indices must
     * still be ordered in a way that guarantees that sp == base means
     * the queue is empty, but otherwise may err on the side of
     * possibly making the queue appear nonempty when a push, pop, or
     * deq have not fully committed. Note that this means that the deq
     * operation, considered individually, is not wait-free. One thief
     * cannot successfully continue until another in-progress one (or,
     * if previously empty, a push) completes.  However, in the
     * aggregate, we ensure at least probabilistic non-blockingness.
     * If an attempted steal fails, a thief always chooses a different
     * random victim target to try next. So, in order for one thief to
     * progress, it suffices for any in-progress deq or new push on
     * any empty queue to complete. One reason this works well here is
     * that apparently-nonempty often means soon-to-be-stealable,
     * which gives threads a chance to set activation status if
     * necessary before stealing.
     *
     * This approach also enables support for "async mode" where local
     * task processing is in FIFO, not LIFO order; simply by using a
     * version of deq rather than pop when locallyFifo is true (as set
     * by the ForkJoinPool).  This allows use in message-passing
     * frameworks in which tasks are never joined.
     *
     * When a worker would otherwise be blocked waiting to join a
     * task, it first tries a form of linear helping: Each worker
     * records (in field currentSteal) the most recent task it stole
     * from some other worker. Plus, it records (in field currentJoin)
     * the task it is currently actively joining. Method joinTask uses
     * these markers to try to find a worker to help (i.e., steal back
     * a task from and execute it) that could hasten completion of the
     * actively joined task. In essence, the joiner executes a task
     * that would be on its own local deque had the to-be-joined task
     * not been stolen. This may be seen as a conservative variant of
     * the approach in Wagner & Calder "Leapfrogging: a portable
     * technique for implementing efficient futures" SIGPLAN Notices,
     * 1993 (http://portal.acm.org/citation.cfm?id=155354). It differs
     * in that: (1) We only maintain dependency links across workers
     * upon steals, rather than maintain per-task bookkeeping.  This
     * may require a linear scan of workers array to locate stealers,
     * but usually doesn't because stealers leave hints (that may
     * become stale/wrong) of where to locate the kathem. This
     * isolates cost to when it is needed, rather than adding to
     * per-task overhead.  (2) It is "shallow", ignoring nesting and
     * potentially cyclic mutual steals.  (3) It is intentionally
     * racy: field currentJoin is updated only while actively joining,
     * which means that we could miss links in the chain during
     * long-lived tasks, GC stalls etc.  (4) We bound the number of
     * attempts to find work (see MAX_HELP_DEPTH) and fall back to
     * suspending the worker and if necessary replacing it with a
     * spare (see ForkJoinPool.tryAwaitJoin).
     *
     * Efficient implementation of these algorithms currently relies
     * on an uncomfortable amount of "Unsafe" mechanics. To maintain
     * correct orderings, reads and writes of variable base require
     * volatile ordering.  Variable sp does not require volatile
     * writes but still needs store-ordering, which we accomplish by
     * pre-incrementing sp before filling the slot with an ordered
     * store.  (Pre-incrementing also enables backouts used in
     * joinTask.)  Because they are protected by volatile base reads,
     * reads of the queue array and its slots by other threads do not
     * need volatile load semantics, but writes (in push) require
     * store order and CASes (in pop and deq) require (volatile) CAS
     * semantics.  (Michael, Saraswat, and Vechev's algorithm has
     * similar properties, but without support for nulling slots.)
     * Since these combinations aren't supported using ordinary
     * volatiles, the only way to accomplish these efficiently is to
     * use direct Unsafe calls. (Using external AtomicIntegers and
     * AtomicReferenceArrays for the indices and array is
     * significantly slower because of memory locality and indirection
     * effects.)
     *
     * Further, performance on most platforms is very sensitive to
     * placement and sizing of the (resizable) queue array.  Even
     * though these queues don't usually become all that big, the
     * initial size must be large enough to counteract cache
     * contention effects across multiple queues (especially in the
     * presence of GC cardmarking). Also, to improve thread-locality,
     * queues are initialized after starting.  All together, these
     * low-level implementation choices produce as much as a factor of
     * 4 performance improvement compared to naive implementations,
     * and enable the processing of billions of tasks per second,
     * sometimes at the expense of ugliness.
     */

    /**
     * Generator for initial random seeds for random victim
     * selection. This is used only to create initial seeds. Random
     * steals use a cheaper xorshift generator per steal attempt. We
     * expect only rare contention on seedGenerator, so just use a
     * plain Random.
     */
    private static final Random seedGenerator = new Random();

    /**
     * The timeout value for suspending spares. Spare workers that
     * remain unsignalled for more than this time may be trimmed
     * (killed and removed from pool).  Since our goal is to avoid
     * long-term thread buildup, the exact value of timeout does not
     * matter too much so long as it avoids most false-alarm timeouts
     * under GC stalls or momentarily high system load.
     */
    private static final long SPARE_KEEPALIVE_NANOS =
        5L * 1000L * 1000L * 1000L; // 5 secs

    /**
     * The maximum stolen->joining link depth allowed in helpJoinTask.
     * Depths for legitimate chains are unbounded, but we use a fixed
     * constant to avoid (otherwise unchecked) cycles and bound
     * staleness of traversal parameters at the expense of sometimes
     * blocking when we could be helping.
     */
    private static final int MAX_HELP_DEPTH = 8;

    /**
     * Capacity of work-stealing queue array upon initialization.
     * Must be a power of two. Initial size must be at least 4, but is
     * padded to minimize cache effects.
     */
    private static final int INITIAL_QUEUE_CAPACITY = 1 << 13;

    /**
     * Maximum work-stealing queue array size.  Must be less than or
     * equal to 1 << 28 to ensure lack of index wraparound. (This
     * is less than usual bounds, because we need leftshift by 3
     * to be in int range).
     */
    private static final int MAXIMUM_QUEUE_CAPACITY = 1 << 28;

    /**
     * The pool this thread works in. Accessed directly by ForkJoinTask.
     */
    final ForkJoinPool pool;

    /**
     * The work-stealing queue array. Size must be a power of two.
     * Initialized in onStart, to improve memory locality.
     */
    private ForkJoinTask<?>[] queue;

    /**
     * Index (mod queue.length) of least valid queue slot, which is
     * always the next position to steal from if nonempty.
     */
    private volatile int base;

    /**
     * Index (mod queue.length) of next queue slot to push to or pop
     * from. It is written only by owner thread, and accessed by other
     * threads only after reading (volatile) base.  Both sp and base
     * are allowed to wrap around on overflow, but (sp - base) still
     * estimates size.
     */
    private int sp;

    /**
     * The index of most recent stealer, used as a hint to avoid
     * traversal in method helpJoinTask. This is only a hint because a
     * worker might have had multiple steals and this only holds one
     * of them (usually the most current). Declared non-volatile,
     * relying on other prevailing sync to keep reasonably current.
     */
    private int stealHint;

    /**
     * Run state of this worker. In addition to the usual run levels,
     * tracks if this worker is suspended as a spare, and if it was
     * killed (trimmed) while suspended. However, "active" status is
     * maintained separately.
     */
    private volatile int runState;

    private static final int TERMINATING = 0x01;
    private static final int TERMINATED  = 0x02;
    private static final int SUSPENDED   = 0x04; // inactive spare
    private static final int TRIMMED     = 0x08; // killed while suspended

    /**
     * Number of LockSupport.park calls to block this thread for
     * suspension or event waits. Used for internal instrumention;
     * currently not exported but included because volatile write upon
     * park also provides a workaround for a JVM bug.
     */
    volatile int parkCount;

    /**
     * Number of steals, transferred and reset in pool callbacks pool
     * when idle Accessed directly by pool.
     */
    int stealCount;

    /**
     * Seed for random number generator for choosing steal victims.
     * Uses Marsaglia xorshift. Must be initialized as nonzero.
     */
    private int seed;


    /**
     * Activity status. When true, this worker is considered active.
     * Accessed directly by pool.  Must be false upon construction.
     */
    boolean active;

    /**
     * True if use local fifo, not default lifo, for local polling.
     * Shadows value from ForkJoinPool, which resets it if changed
     * pool-wide.
     */
    private final boolean locallyFifo;

    /**
     * Index of this worker in pool array. Set once by pool before
     * running, and accessed directly by pool to locate this worker in
     * its workers array.
     */
    int poolIndex;

    /**
     * The last pool event waited for. Accessed only by pool in
     * callback methods invoked within this thread.
     */
    int lastEventCount;

    /**
     * Encoded index and event count of next event waiter. Used only
     * by ForkJoinPool for managing event waiters.
     */
    volatile long nextWaiter;

    /**
     * The task currently being joined, set only when actively trying
     * to helpStealer. Written only by current thread, but read by
     * others.
     */
    private volatile ForkJoinTask<?> currentJoin;

    /**
     * The task most recently stolen from another worker (or
     * submission queue).  Not volatile because always read/written in
     * presence of related volatiles in those cases where it matters.
     */
    private ForkJoinTask<?> currentSteal;

    /**
     * Creates a ForkJoinWorkerThread operating in the given pool.
     *
     * @param pool the pool this thread works in
     * @throws NullPointerException if pool is null
     */
    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        this.pool = pool;
        this.locallyFifo = pool.locallyFifo;
        // To avoid exposing construction details to subclasses,
        // remaining initialization is in start() and onStart()
    }

    /**
     * Performs additional initialization and starts this thread
     */
    final void start(int poolIndex, UncaughtExceptionHandler ueh) {
        this.poolIndex = poolIndex;
        if (ueh != null)
            setUncaughtExceptionHandler(ueh);
        setDaemon(true);
        start();
    }

    // Public/protected methods

    /**
     * Returns the pool hosting this thread.
     *
     * @return the pool
     */
    public ForkJoinPool getPool() {
        return pool;
    }

    /**
     * Returns the index number of this thread in its pool.  The
     * returned value ranges from zero to the maximum number of
     * threads (minus one) that have ever been created in the pool.
     * This method may be useful for applications that track status or
     * collect results per-worker rather than per-task.
     *
     * @return the index number
     */
    public int getPoolIndex() {
        return poolIndex;
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
        int rs = seedGenerator.nextInt();
        seed = rs == 0? 1 : rs; // seed must be nonzero

        // Allocate name string and arrays in this thread
        String pid = Integer.toString(pool.getPoolNumber());
        String wid = Integer.toString(poolIndex);
        setName("ForkJoinPool-" + pid + "-worker-" + wid);

        queue = new ForkJoinTask<?>[INITIAL_QUEUE_CAPACITY];
    }

    /**
     * Performs cleanup associated with termination of this worker
     * thread.  If you override this method, you must invoke
     * {@code super.onTermination} at the end of the overridden method.
     *
     * @param exception the exception causing this thread to abort due
     * to an unrecoverable error, or {@code null} if completed normally
     */
    protected void onTermination(Throwable exception) {
        try {
            cancelTasks();
            setTerminated();
            pool.workerTerminated(this);
        } catch (Throwable ex) {        // Shouldn't ever happen
            if (exception == null)      // but if so, at least rethrown
                exception = ex;
        } finally {
            if (exception != null)
                UNSAFE.throwException(exception);
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
            mainLoop();
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            onTermination(exception);
        }
    }

    // helpers for run()

    /**
     * Find and execute tasks and check status while running
     */
    private void mainLoop() {
        int emptyScans = 0; // consecutive times failed to find work
        ForkJoinPool p = pool;
        for (;;) {
            p.preStep(this, emptyScans);
            if (runState != 0)
                return;
            ForkJoinTask<?> t; // try to get and run stolen or submitted task
            if ((t = scan()) != null || (t = pollSubmission()) != null) {
                t.tryExec();
                if (base != sp)
                    runLocalTasks();
                currentSteal = null;
                emptyScans = 0;
            }
            else
                ++emptyScans;
        }
    }

    /**
     * Runs local tasks until queue is empty or shut down.  Call only
     * while active.
     */
    private void runLocalTasks() {
        while (runState == 0) {
            ForkJoinTask<?> t = locallyFifo? locallyDeqTask() : popTask();
            if (t != null)
                t.tryExec();
            else if (base == sp)
                break;
        }
    }

    /**
     * If a submission exists, try to activate and take it
     *
     * @return a task, if available
     */
    private ForkJoinTask<?> pollSubmission() {
        ForkJoinPool p = pool;
        while (p.hasQueuedSubmissions()) {
            if (active || (active = p.tryIncrementActiveCount())) {
                ForkJoinTask<?> t = p.pollSubmission();
                if (t != null) {
                    currentSteal = t;
                    return t;
                }
                return scan(); // if missed, rescan
            }
        }
        return null;
    }

    /*
     * Intrinsics-based atomic writes for queue slots. These are
     * basically the same as methods in AtomicObjectArray, but
     * specialized for (1) ForkJoinTask elements (2) requirement that
     * nullness and bounds checks have already been performed by
     * callers and (3) effective offsets are known not to overflow
     * from int to long (because of MAXIMUM_QUEUE_CAPACITY). We don't
     * need corresponding version for reads: plain array reads are OK
     * because they protected by other volatile reads and are
     * confirmed by CASes.
     *
     * Most uses don't actually call these methods, but instead contain
     * inlined forms that enable more predictable optimization.  We
     * don't define the version of write used in pushTask at all, but
     * instead inline there a store-fenced array slot write.
     */

    /**
     * CASes slot i of array q from t to null. Caller must ensure q is
     * non-null and index is in range.
     */
    private static final boolean casSlotNull(ForkJoinTask<?>[] q, int i,
                                             ForkJoinTask<?> t) {
        return UNSAFE.compareAndSwapObject(q, (i << qShift) + qBase, t, null);
    }

    /**
     * Performs a volatile write of the given task at given slot of
     * array q.  Caller must ensure q is non-null and index is in
     * range. This method is used only during resets and backouts.
     */
    private static final void writeSlot(ForkJoinTask<?>[] q, int i,
                                              ForkJoinTask<?> t) {
        UNSAFE.putObjectVolatile(q, (i << qShift) + qBase, t);
    }

    // queue methods

    /**
     * Pushes a task. Call only from this thread.
     *
     * @param t the task. Caller must ensure non-null.
     */
    final void pushTask(ForkJoinTask<?> t) {
        ForkJoinTask<?>[] q = queue;
        int mask = q.length - 1; // implicit assert q != null
        int s = sp++;            // ok to increment sp before slot write
        UNSAFE.putOrderedObject(q, ((s & mask) << qShift) + qBase, t);
        if ((s -= base) == 0)
            pool.signalWork();   // was empty
        else if (s == mask)
            growQueue();         // is full
    }

    /**
     * Tries to take a task from the base of the queue, failing if
     * empty or contended. Note: Specializations of this code appear
     * in locallyDeqTask and elsewhere.
     *
     * @return a task, or null if none or contended
     */
    final ForkJoinTask<?> deqTask() {
        ForkJoinTask<?> t;
        ForkJoinTask<?>[] q;
        int b, i;
        if ((b = base) != sp &&
            (q = queue) != null && // must read q after b
            (t = q[i = (q.length - 1) & b]) != null && base == b &&
            UNSAFE.compareAndSwapObject(q, (i << qShift) + qBase, t, null)) {
            base = b + 1;
            return t;
        }
        return null;
    }

    /**
     * Tries to take a task from the base of own queue. Assumes active
     * status.  Called only by current thread.
     *
     * @return a task, or null if none
     */
    final ForkJoinTask<?> locallyDeqTask() {
        ForkJoinTask<?>[] q = queue;
        if (q != null) {
            ForkJoinTask<?> t;
            int b, i;
            while (sp != (b = base)) {
                if ((t = q[i = (q.length - 1) & b]) != null && base == b &&
                    UNSAFE.compareAndSwapObject(q, (i << qShift) + qBase,
                                                t, null)) {
                    base = b + 1;
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * Returns a popped task, or null if empty. Assumes active status.
     * Called only by current thread.
     */
    final ForkJoinTask<?> popTask() {
        int s;
        ForkJoinTask<?>[] q;
        if (base != (s = sp) && (q = queue) != null) {
            int i = (q.length - 1) & --s;
            ForkJoinTask<?> t = q[i];
            if (t != null && UNSAFE.compareAndSwapObject
                (q, (i << qShift) + qBase, t, null)) {
                sp = s;
                return t;
            }
        }
        return null;
    }

    /**
     * Specialized version of popTask to pop only if topmost element
     * is the given task. Called only by current thread while
     * active.
     *
     * @param t the task. Caller must ensure non-null.
     */
    final boolean unpushTask(ForkJoinTask<?> t) {
        int s;
        ForkJoinTask<?>[] q;
        if (base != (s = sp) && (q = queue) != null &&
            UNSAFE.compareAndSwapObject
            (q, (((q.length - 1) & --s) << qShift) + qBase, t, null)) {
            sp = s;
            return true;
        }
        return false;
    }

    /**
     * Returns next task or null if empty or contended
     */
    final ForkJoinTask<?> peekTask() {
        ForkJoinTask<?>[] q = queue;
        if (q == null)
            return null;
        int mask = q.length - 1;
        int i = locallyFifo ? base : (sp - 1);
        return q[i & mask];
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
            writeSlot(newQ, b & newMask, t);
        } while (++b != bf);
        pool.signalWork();
    }

    /**
     * Computes next value for random victim probe in scan().  Scans
     * don't require a very high quality generator, but also not a
     * crummy one.  Marsaglia xor-shift is cheap and works well enough.
     * Note: This is manually inlined in scan()
     */
    private static final int xorShift(int r) {
        r ^= r << 13;
        r ^= r >>> 17;
        return r ^ (r << 5);
    }

    /**
     * Tries to steal a task from another worker. Starts at a random
     * index of workers array, and probes workers until finding one
     * with non-empty queue or finding that all are empty.  It
     * randomly selects the first n probes. If these are empty, it
     * resorts to a circular sweep, which is necessary to accurately
     * set active status. (The circular sweep uses steps of
     * approximately half the array size plus 1, to avoid bias
     * stemming from leftmost packing of the array in ForkJoinPool.)
     *
     * This method must be both fast and quiet -- usually avoiding
     * memory accesses that could disrupt cache sharing etc other than
     * those needed to check for and take tasks (or to activate if not
     * already active). This accounts for, among other things,
     * updating random seed in place without storing it until exit.
     *
     * @return a task, or null if none found
     */
    private ForkJoinTask<?> scan() {
        ForkJoinPool p = pool;
        ForkJoinWorkerThread[] ws;        // worker array
        int n;                            // upper bound of #workers
        if ((ws = p.workers) != null && (n = ws.length) > 1) {
            boolean canSteal = active;    // shadow active status
            int r = seed;                 // extract seed once
            int mask = n - 1;
            int j = -n;                   // loop counter
            int k = r;                    // worker index, random if j < 0
            for (;;) {
                ForkJoinWorkerThread v = ws[k & mask];
                r ^= r << 13; r ^= r >>> 17; r ^= r << 5; // inline xorshift
                if (v != null && v.base != v.sp) {
                    if (canSteal ||       // ensure active status
                        (canSteal = active = p.tryIncrementActiveCount())) {
                        int b = v.base;   // inline specialized deqTask
                        ForkJoinTask<?>[] q;
                        if (b != v.sp && (q = v.queue) != null) {
                            ForkJoinTask<?> t;
                            int i = (q.length - 1) & b;
                            long u = (i << qShift) + qBase; // raw offset
                            if ((t = q[i]) != null && v.base == b &&
                                UNSAFE.compareAndSwapObject(q, u, t, null)) {
                                currentSteal = t;
                                v.stealHint = poolIndex;
                                v.base = b + 1;
                                seed = r;
                                ++stealCount;
                                return t;
                            }
                        }
                    }
                    j = -n;
                    k = r;                // restart on contention
                }
                else if (++j <= 0)
                    k = r;
                else if (j <= n)
                    k += (n >>> 1) | 1;
                else
                    break;
            }
        }
        return null;
    }

    // Run State management

    // status check methods used mainly by ForkJoinPool
    final boolean isTerminating() { return (runState & TERMINATING) != 0; }
    final boolean isTerminated()  { return (runState & TERMINATED) != 0; }
    final boolean isSuspended()   { return (runState & SUSPENDED) != 0; }
    final boolean isTrimmed()     { return (runState & TRIMMED) != 0; }

    /**
     * Sets state to TERMINATING, also resuming if suspended.
     */
    final void shutdown() {
        for (;;) {
            int s = runState;
            if ((s & SUSPENDED) != 0) { // kill and wakeup if suspended
                if (UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                             (s & ~SUSPENDED) |
                                             (TRIMMED|TERMINATING))) {
                    LockSupport.unpark(this);
                    break;
                }
            }
            else if (UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                              s | TERMINATING))
                break;
        }
    }

    /**
     * Sets state to TERMINATED. Called only by this thread.
     */
    private void setTerminated() {
        int s;
        do {} while (!UNSAFE.compareAndSwapInt(this, runStateOffset,
                                               s = runState,
                                               s | (TERMINATING|TERMINATED)));
    }

    /**
     * Instrumented version of park used by ForkJoinPool.awaitEvent
     */
    final void doPark() {
        ++parkCount;
        LockSupport.park(this);
    }

    /**
     * If suspended, tries to set status to unsuspended and unparks.
     *
     * @return true if successful
     */
    final boolean tryResumeSpare() {
        int s = runState;
        if ((s & SUSPENDED) != 0 &&
            UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                     s & ~SUSPENDED)) {
            LockSupport.unpark(this);
            return true;
        }
        return false;
    }

    /**
     * Sets suspended status and blocks as spare until resumed,
     * shutdown, or timed out.
     *
     * @return false if trimmed
     */
    final boolean suspendAsSpare() {
        for (;;) {               // set suspended unless terminating
            int s = runState;
            if ((s & TERMINATING) != 0) { // must kill
                if (UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                             s | (TRIMMED | TERMINATING)))
                    return false;
            }
            else if (UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                              s | SUSPENDED))
                break;
        }
        boolean timed;
        long nanos;
        long startTime;
        if (poolIndex < pool.parallelism) {
            timed = false;
            nanos = 0L;
            startTime = 0L;
        }
        else {
            timed = true;
            nanos = SPARE_KEEPALIVE_NANOS;
            startTime = System.nanoTime();
        }
        pool.accumulateStealCount(this);
        lastEventCount = 0;      // reset upon resume
        interrupted();           // clear/ignore interrupts
        while ((runState & SUSPENDED) != 0) {
            ++parkCount;
            if (!timed)
                LockSupport.park(this);
            else if ((nanos -= (System.nanoTime() - startTime)) > 0)
                LockSupport.parkNanos(this, nanos);
            else { // try to trim on timeout
                int s = runState;
                if (UNSAFE.compareAndSwapInt(this, runStateOffset, s,
                                             (s & ~SUSPENDED) |
                                             (TRIMMED|TERMINATING)))
                    return false;
            }
        }
        return true;
    }

    // Misc support methods for ForkJoinPool

    /**
     * Returns an estimate of the number of tasks in the queue.  Also
     * used by ForkJoinTask.
     */
    final int getQueueSize() {
        return -base + sp;
    }

    /**
     * Removes and cancels all tasks in queue.  Can be called from any
     * thread.
     */
    final void cancelTasks() {
        ForkJoinTask<?> cj = currentJoin; // try to kill live tasks
        if (cj != null) {
            currentJoin = null;
            cj.cancelIgnoringExceptions();
        }
        ForkJoinTask<?> cs = currentSteal;
        if (cs != null) {
            currentSteal = null;
            cs.cancelIgnoringExceptions();
        }
        while (base != sp) {
            ForkJoinTask<?> t = deqTask();
            if (t != null)
                t.cancelIgnoringExceptions();
        }
    }

    /**
     * Drains tasks to given collection c.
     *
     * @return the number of tasks drained
     */
    final int drainTasksTo(Collection<? super ForkJoinTask<?>> c) {
        int n = 0;
        while (base != sp) {
            ForkJoinTask<?> t = deqTask();
            if (t != null) {
                c.add(t);
                ++n;
            }
        }
        return n;
    }

    // Support methods for ForkJoinTask

    /**
     * Gets and removes a local task.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> pollLocalTask() {
        while (sp != base) {
            if (active || (active = pool.tryIncrementActiveCount()))
                return locallyFifo? locallyDeqTask() : popTask();
        }
        return null;
    }

    /**
     * Gets and removes a local or stolen task.
     *
     * @return a task, if available
     */
    final ForkJoinTask<?> pollTask() {
        ForkJoinTask<?> t;
        return (t = pollLocalTask()) != null ? t : scan();
    }

    /**
     * Possibly runs some tasks and/or blocks, until task is done.
     * The main body is basically a big spinloop, alternating between
     * calls to helpJoinTask and pool.tryAwaitJoin with increased
     * patience parameters until either the task is done without
     * waiting, or we have, if necessary, created or resumed a
     * replacement for this thread while it blocks.
     *
     * @param joinMe the task to join
     * @return task status on exit
     */
    final int joinTask(ForkJoinTask<?> joinMe) {
        int stat;
        ForkJoinTask<?> prevJoin = currentJoin;
        currentJoin = joinMe;
        if ((stat = joinMe.status) >= 0 &&
            (sp == base || (stat = localHelpJoinTask(joinMe)) >= 0)) {
            ForkJoinPool p = pool;
            int helpRetries = 2;     // initial patience values
            int awaitRetries = -1;   // -1 is sentinel for replace-check only
            do {
                helpJoinTask(joinMe, helpRetries);
                if ((stat = joinMe.status) < 0)
                    break;
                boolean busy = p.tryAwaitJoin(joinMe, awaitRetries);
                if ((stat = joinMe.status) < 0)
                    break;
                if (awaitRetries == -1)
                    awaitRetries = 0;
                else if (busy)
                    ++awaitRetries;
                if (helpRetries < p.parallelism)
                    helpRetries <<= 1;
                Thread.yield(); // tame unbounded loop
            } while (joinMe.status >= 0);
        }
        currentJoin = prevJoin;
        return stat;
    }

    /**
     * Run tasks in local queue until given task is done.
     *
     * @param joinMe the task to join
     * @return task status on exit
     */
    private int localHelpJoinTask(ForkJoinTask<?> joinMe) {
        int stat, s;
        ForkJoinTask<?>[] q;
        while ((stat = joinMe.status) >= 0 &&
               base != (s = sp) && (q = queue) != null) {
            ForkJoinTask<?> t;
            int i = (q.length - 1) & --s;
            long u = (i << qShift) + qBase; // raw offset
            if ((t = q[i]) != null &&
                UNSAFE.compareAndSwapObject(q, u, t, null)) {
                /*
                 * This recheck (and similarly in helpJoinTask)
                 * handles cases where joinMe is independently
                 * cancelled or forced even though there is other work
                 * available. Back out of the pop by putting t back
                 * into slot before we commit by writing sp.
                 */
                if ((stat = joinMe.status) < 0) {
                    UNSAFE.putObjectVolatile(q, u, t);
                    break;
                }
                sp = s;
                t.tryExec();
            }
        }
        return stat;
    }

    /**
     * Tries to locate and help perform tasks for a stealer of the
     * given task, or in turn one of its stealers.  Traces
     * currentSteal->currentJoin links looking for a thread working on
     * a descendant of the given task and with a non-empty queue to
     * steal back and execute tasks from. Restarts search upon
     * encountering chains that are stale, unknown, or of length
     * greater than MAX_HELP_DEPTH links, to avoid unbounded cycles.
     *
     * The implementation is very branchy to cope with the restart
     * cases.  Returns void, not task status (which must be reread by
     * caller anyway) to slightly simplify control paths.
     *
     * @param joinMe the task to join
     */
    final void helpJoinTask(ForkJoinTask<?> joinMe, int retries) {
        ForkJoinWorkerThread[] ws = pool.workers;
        int n;
        if (ws == null || (n = ws.length) <= 1)
            return;                   // need at least 2 workers

        restart:while (joinMe.status >= 0 && --retries >= 0) {
            ForkJoinTask<?> task = joinMe;        // base of chain
            ForkJoinWorkerThread thread = this;   // thread with stolen task
            for (int depth = 0; depth < MAX_HELP_DEPTH; ++depth) {
                // Try to find v, the stealer of task, by first using hint
                ForkJoinWorkerThread v = ws[thread.stealHint & (n - 1)];
                if (v == null || v.currentSteal != task) {
                    for (int j = 0; ; ++j) {      // search array
                        if (task.status < 0 || j == n)
                            continue restart;     // stale or no stealer
                        if ((v = ws[j]) != null && v.currentSteal == task) {
                            thread.stealHint = j; // save for next time
                            break;
                        }
                    }
                }
                // Try to help v, using specialized form of deqTask
                int b;
                ForkJoinTask<?>[] q;
                while ((b = v.base) != v.sp && (q = v.queue) != null) {
                    int i = (q.length - 1) & b;
                    long u = (i << qShift) + qBase;
                    ForkJoinTask<?> t = q[i];
                    if (task.status < 0)          // stale
                        continue restart;
                    if (v.base == b) {            // recheck after reading t
                        if (t == null)            // producer stalled
                            continue restart;     // retry via restart
                        if (UNSAFE.compareAndSwapObject(q, u, t, null)) {
                            if (joinMe.status < 0) {
                                UNSAFE.putObjectVolatile(q, u, t);
                                return;           // back out on cancel
                            }
                            ForkJoinTask<?> prevSteal = currentSteal;
                            currentSteal = t;
                            v.stealHint = poolIndex;
                            v.base = b + 1;
                            t.tryExec();
                            currentSteal = prevSteal;
                        }
                    }
                    if (joinMe.status < 0)
                        return;
                }
                // Try to descend to find v's stealer
                ForkJoinTask<?> next = v.currentJoin;
                if (next == null || task.status < 0)
                    continue restart;             // no descendent or stale
                if (joinMe.status < 0)
                    return;
                task = next;
                thread = v;
            }
        }
    }

    /**
     * Returns an estimate of the number of tasks, offset by a
     * function of number of idle workers.
     *
     * This method provides a cheap heuristic guide for task
     * partitioning when programmers, frameworks, tools, or languages
     * have little or no idea about task granularity.  In essence by
     * offering this method, we ask users only about tradeoffs in
     * overhead vs expected throughput and its variance, rather than
     * how finely to partition tasks.
     *
     * In a steady state strict (tree-structured) computation, each
     * thread makes available for stealing enough tasks for other
     * threads to remain active. Inductively, if all threads play by
     * the same rules, each thread should make available only a
     * constant number of tasks.
     *
     * The minimum useful constant is just 1. But using a value of 1
     * would require immediate replenishment upon each steal to
     * maintain enough tasks, which is infeasible.  Further,
     * partitionings/granularities of offered tasks should minimize
     * steal rates, which in general means that threads nearer the top
     * of computation tree should generate more than those nearer the
     * bottom. In perfect steady state, each thread is at
     * approximately the same level of computation tree. However,
     * producing extra tasks amortizes the uncertainty of progress and
     * diffusion assumptions.
     *
     * So, users will want to use values larger, but not much larger
     * than 1 to both smooth over transient shortages and hedge
     * against uneven progress; as traded off against the cost of
     * extra task overhead. We leave the user to pick a threshold
     * value to compare with the results of this call to guide
     * decisions, but recommend values such as 3.
     *
     * When all threads are active, it is on average OK to estimate
     * surplus strictly locally. In steady-state, if one thread is
     * maintaining say 2 surplus tasks, then so are others. So we can
     * just use estimated queue length (although note that (sp - base)
     * can be an overestimate because of stealers lagging increments
     * of base).  However, this strategy alone leads to serious
     * mis-estimates in some non-steady-state conditions (ramp-up,
     * ramp-down, other stalls). We can detect many of these by
     * further considering the number of "idle" threads, that are
     * known to have zero queued tasks, so compensate by a factor of
     * (#idle/#active) threads.
     */
    final int getEstimatedSurplusTaskCount() {
        return sp - base - pool.idlePerActive();
    }

    /**
     * Runs tasks until {@code pool.isQuiescent()}.
     */
    final void helpQuiescePool() {
        for (;;) {
            ForkJoinTask<?> t = pollLocalTask();
            if (t != null || (t = scan()) != null) {
                t.tryExec();
                currentSteal = null;
            }
            else {
                ForkJoinPool p = pool;
                if (active) {
                    active = false; // inactivate
                    do {} while (!p.tryDecrementActiveCount());
                }
                if (p.isQuiescent()) {
                    active = true; // re-activate
                    do {} while (!p.tryIncrementActiveCount());
                    return;
                }
            }
        }
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE = getUnsafe();
    private static final long runStateOffset =
        objectFieldOffset("runState", ForkJoinWorkerThread.class);
    private static final long qBase =
        UNSAFE.arrayBaseOffset(ForkJoinTask[].class);
    private static final int qShift;

    static {
        int s = UNSAFE.arrayIndexScale(ForkJoinTask[].class);
        if ((s & (s-1)) != 0)
            throw new Error("data type scale not a power of two");
        qShift = 31 - Integer.numberOfLeadingZeros(s);
    }

    private static long objectFieldOffset(String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            // Convert Exception to corresponding Error
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }

    /**
     * Returns a sun.misc.Unsafe.  Suitable for use in a 3rd party package.
     * Replace with a simple call to Unsafe.getUnsafe when integrating
     * into a jdk.
     *
     * @return a sun.misc.Unsafe
     */
    private static sun.misc.Unsafe getUnsafe() {
        try {
            return sun.misc.Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                return java.security.AccessController.doPrivileged
                    (new java.security
                     .PrivilegedExceptionAction<sun.misc.Unsafe>() {
                        public sun.misc.Unsafe run() throws Exception {
                            java.lang.reflect.Field f = sun.misc
                                .Unsafe.class.getDeclaredField("theUnsafe");
                            f.setAccessible(true);
                            return (sun.misc.Unsafe) f.get(null);
                        }});
            } catch (java.security.PrivilegedActionException e) {
                throw new RuntimeException("Could not initialize intrinsics",
                                           e.getCause());
            }
        }
    }
}
