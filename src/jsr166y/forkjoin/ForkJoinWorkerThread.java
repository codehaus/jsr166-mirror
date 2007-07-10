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
 * A thread that executes ForkJoinTasks. ForkJoinWorkerThreads are
 * managed by ForkJoinPools in support of ForkJoinTasks.  However,
 * this class also provides public <tt>static</tt> methods accessing
 * basic scheduling and execution mechanics for the current
 * ForkJoinWorkerThread. These enable construction of special-purpose
 * task classes. These methods may be invoked only from within other
 * ForkJoinTask computations. Attempts to invoke in other contexts
 * result in exceptions or errors including ClassCastException.
 *
 * <p>Worker threads may access and process tasks obtained in any of
 * three ways. In preference order: <em>Local</em> tasks are processed
 * in LIFO (newest first) order. <em>Stolen</em> tasks are obtained
 * from other threads in FIFO (oldest first) order.
 * <em>Submissions</em> form a FIFO queue common to the entire
 * pool. The preference order among local and stolen tasks cannot be
 * changed. However, methods exist to preferentially process
 * submissions
 */
public class ForkJoinWorkerThread extends Thread {
    /*
     * ForkJoinWorkerThreads repeatedly get tasks and run them.
     *
     * Each ForkJoinWorkerThread thread maintains a work-stealing queue.
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
     * The ForkJoinWorkerThread class also includes support for the main
     * functionality of ForkJoinTask methods.  It is more efficient to
     * implement them here rather than requiring ForkJoinTasks to
     * repeatedly relay to worker methods via class-cast of
     * currentThread.
     */

    /**
     * The pool this thread works in.
     */
    final ForkJoinPool pool;

    /**
     * The array used as work-stealing queue. Length must always
     * be a power of two. Even though these queues rarely become
     * all that big, the initial size must be large enough to
     * counteract cache contention effects across multiple queues.
     * Currently, they are initialized either on construction or
     * in main loop.  However, all queue-related methods are
     * written so that they could alternatively be lazily
     * allocated (see growAndPushTask) and/or disposed of when
     * empty.
     */
    ForkJoinTask<?>[] queue;

    static final int INITIAL_CAPACITY = 1 << 13;
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * Index (mod queue.length) of least valid slot, which
     * is always the next position to steal from if nonempty.
     */
    volatile long base;

    /**
     * Index (mod queue.length-1) of next position to push to
     * or pop from
     */
    volatile long sp;

    /**
     * Updater to allow CAS of base index. Even though this slows
     * down CASes of base, they are relatively infrequent,
     * so the better locality of having sp and base close
     * to each other normally outweighs this.
     */
    static final AtomicLongFieldUpdater<ForkJoinWorkerThread> baseUpdater =
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
    long nextSlotToClean;

    /**
     * Seed for random number generator in randomIndex
     */
    long randomSeed;

    /**
     * Index of this worker in pool array. Needed for replacement
     * upon uncaught exceptions.
     */
    final int index;

    /**
     * Number of steals, just for monitoring purposes.
     */
    int stealCount;

    /**
     * Number of scans since last successfully getting a task.
     * Always zero when busy executing tasks. 
     * Incremented after a failed scan in getNextTask(),
     * to help control sleeps inside Pool.takeSubmission
     */
    int idleCount;

    volatile boolean shouldStop;

    /**
     * Padding to avoid cache-line sharing across workers
     */
    int p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, pa, pb, pc, pd, pe, pf;

    /**
     * Creates new ForkJoinWorkerThread.
     */
    ForkJoinWorkerThread(ForkJoinPool pool, int index, String name) {
        super(name);
        this.pool = pool;
        this.index = index;
        setDaemon(true);
        pool.workerActive();
    }

    /**
     * This method is required to be public, but should never be
     * called explicitly. It executes the main run loop.
     */
    public void run() {
        Throwable exception = null;
        try {
            // Finish initialization
            randomSeed = (((long)index << 24) ^ System.nanoTime());
            if (randomSeed == 0)
                randomSeed = index + 1;
            if (queue == null) 
                growQueue(null);

            while (!shouldStop) {
                ForkJoinTask<?> task = getNextTask();
                if (task == null)
                    task = pool.takeSubmission(this);
                if (task != null)
                    task.exec();
            }
        } catch (Throwable ex) {
            exception = ex;
        } finally {
            pool.workerTerminated(this, exception);
        }
    }

    //  Active/Idle status maintenance

    /**
     * The maximum scan value, needed to bound sleep times and avoid
     * wrapping negative.
     */
    static final int MAX_SCANS = (1 << 16) - 1;

    /**
     * Number of idle scans between calls to Thread.yield.  Must
     * be power of two minus one. Value is a heuristic compromise
     * across facts that yield may or may not actually have any
     * effect, and yielding too frequently may have detrimental
     * effect.
     */
    static final int SCANS_PER_YIELD = (1 << 7) - 1;

    /**
     * Sets status to represent that this worker is active running
     * a task
     */
    final void clearIdleCount() {
        if (idleCount != 0) {
            idleCount = 0;
            pool.workerActive();
        }
    }

    /**
     * Increments idle count upon unsuccessful scan.
     */
    final void advanceIdleCount() {
        int next = idleCount + 1;
        if (next >= MAX_SCANS)
            next = MAX_SCANS;
        idleCount = next;
        if (next == 1)
            pool.workerIdle();
        else if ((next & SCANS_PER_YIELD) == 0)
            Thread.yield(); 
    }

    final boolean isActive() {
        return idleCount == 0;
    }

    // Misc status and accessor methods, callable from ForkJoinTasks

    final ForkJoinPool doGetPool() {
        return pool;
    }

    final int getIndex() {
        return index;
    }

    final void shouldStop() {
        shouldStop = true;
    }

    final boolean isStopped() {
        return shouldStop;
    }

    boolean queueIsEmpty() {
        return base >= sp;
    }

    int getQueueSize() {
        long n = sp - base;
        return n < 0? 0 : (int)n; // suppress momentarily negative values
    }

    // Main work-stealing queue methods

    /**
     * Pushes a task. Called only by current thread.
     * @param x the task
     */
    void pushTask(ForkJoinTask<?> x) {
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
     * Returns next task to pop
     */
    ForkJoinTask<?> doPeekNextLocalTask() {
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
    ForkJoinTask<?> popTask() {
        ForkJoinTask<?> x = null;
        ForkJoinTask<?>[] q = queue;
        if (q != null) {
            long s = sp - 1;
            int mask = q.length - 1;
            int idx = ((int)s) & mask;
            x = q[idx];
            if (x != null) {
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
        }
        return x;
    }

    /**
     * Specialized version of popTask to pop only if
     * topmost element is the given task.
     * @param task the task to match
     */
    boolean doRemoveIfNextLocalTask(ForkJoinTask<?> task) {
        boolean popped = false;
        ForkJoinTask<?>[] q = queue;
        if (q != null) {
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
     * Currently used only for cancellation -- getNextTask embeds
     * a variant with contention control for other cases.
     * @return a task, or null if none
     */
    ForkJoinTask<?> tryStealTask() {
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
    ForkJoinTask<?> getNextTask() {
        ForkJoinTask<?> popped = popTask();
        if (popped != null)
            return popped;
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
                    if (t == null || 
                        !baseUpdater.compareAndSet(v, b, b+1)) {
                        remaining = n; // rescan upon contention
                        idx = index;   // restart at own index
                    }
                    else {
                        // slotUpdater.compareAndSet(q, slot, t, null);
                        t.setStolen();
                        ++stealCount;
                        pool.alertSleeper(v);
                        return t;
                    }
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
     * Remove (via steal) and cancel all tasks in queue.
     * Called only from pool.
     */
    void cancelTasks() {
        while (!queueIsEmpty()) {
            ForkJoinTask<?> t = tryStealTask();
            if (t != null)
                t.cancel();
        }
    }

    /**
     * Return either a task, or if none, a pool submission
     */
    ForkJoinTask<?> getNextTaskOrSubmission() {
        ForkJoinTask<?> t = getNextTask();
        if (t == null)
            t = pool.pollSubmission(this);
        return t;
    }

    ForkJoinTask<?> doPollSubmission() {
        return pool.pollSubmission(this);
    }

    /*
     * Support for other ForkJoinTask methods.
     */

    /**
     * Implements ForkJoinTask.join
     */
    <T> T joinTask(ForkJoinTask<T> joinMe) {
        for (;;) {
            RuntimeException ex = joinMe.exception;
            if (ex != null)
                throw ex;
            if (joinMe.status < 0)
                return joinMe.getResult();
            ForkJoinTask<?> t = getNextTask();
            if (t != null)
                t.exec();
        }
    }

    /**
     * Same as join except returns status, not result
     */
    RuntimeException quietlyJoinTask(ForkJoinTask<?> joinMe) {
        for (;;) {
            RuntimeException ex = joinMe.exception;
            if (ex != null || joinMe.status < 0)
                return ex;
            ForkJoinTask<?> t = getNextTask();
            if (t != null)
                t.exec();
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
        boolean popped = doRemoveIfNextLocalTask(t2);
        if (ex != null ||
            ((ex = !popped? quietlyJoinTask(t2) : t2.exec()) != null)) {
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
                boolean popped = doRemoveIfNextLocalTask(t);
                if (ex != null)
                    t.cancel();
                else if (!popped)
                    ex = quietlyJoinTask(t);
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
                boolean popped = doRemoveIfNextLocalTask(t);
                if (ex != null)
                    t.cancel();
                else if (!popped)
                    ex = quietlyJoinTask(t);
                else
                    ex = t.exec();
            }
        }
        if (ex != null)
            throw ex;
    }

    // Public methods on current thread
    
    /**
     * Returns the pool hosting the current task execution.
     * @return the pool
     */
    public static ForkJoinPool getPool() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).doGetPool();
    }

    /**
     * Returns the index number of the current worker in its pool.
     * The return value is in the range
     * <tt>0...getPool().getPoolSize()-1</tt>.  This method may be
     * useful for applications that track status or collect results
     * per-worker rather than per-task.
     * @return the index number.
     */
    public static int getWorkerIndex() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).getIndex();
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
     * Adds the given task as the next task scheduled for execution in
     * this worker's local queue.
     * @param task the task
     * @throws NullPointerException if task is null
     */
    public static void addNextLocalTask(ForkJoinTask<?> task) {
        if (task == null)
            throw new NullPointerException();
        ((ForkJoinWorkerThread)(Thread.currentThread())).pushTask(task);
    }

    /**
     * Removes and returns, without executing, the given task from the
     * queue hosting current execution only if it would be the next
     * task executed by the current worker thread.  Among other
     * usages, this method may be used to bypass task execution during
     * cancellation.
     * @param task the task
     * @return true if removed
     * @throws NullPointerException if task is null
     */
    public static boolean removeIfNextLocalTask(ForkJoinTask<?> task) {
        if (task == null)
            throw new NullPointerException();
        return ((ForkJoinWorkerThread)(Thread.currentThread())).doRemoveIfNextLocalTask(task);
    }

    /**
     * Returns, but does not remove or execute, the next task locally
     * queued for execution. There is no guarantee that this task will
     * be the next one actually executed or returned from
     * <tt>pollNextTask</tt>.
     * @return the next task or null if none
     */
    public static ForkJoinTask<?> peekNextLocalTask() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).doPeekNextLocalTask();
    }

    /**
     * Removes and returns, without executing, the next task queued
     * for execution in this worker's local queue.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollNextLocalTask() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).popTask();
    }

    /**
     * Removes and returns, without executing, the next task queued
     * for execution, which may be either locally queued task, or one
     * stolen from another worker thread.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollNextTask() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).getNextTask();
    }

    /**
     * Removes and returns, without executing, the next pool submission.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollNextSubmission() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).doPollSubmission();
    }

    /**
     * Removes and returns, without executing, the next task queued
     * for execution, if one exists, or a pool submission, if one exsits.
     * @return the next task to execute, or null if none
     */
    public static ForkJoinTask<?> pollNextTaskOrSubmission() {
        return ((ForkJoinWorkerThread)(Thread.currentThread())).getNextTaskOrSubmission();
    }

    /**
     * Execute a locally queued task, if one is available.
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
            ((ForkJoinWorkerThread)(Thread.currentThread())).getNextTask();
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
        ForkJoinTask<?> t = 
            ((ForkJoinWorkerThread)(Thread.currentThread())).doPollSubmission();
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
        ForkJoinTask<?> t = 
            ((ForkJoinWorkerThread)(Thread.currentThread())).getNextTaskOrSubmission();
        if (t == null)
            return false;
        else {
            t.exec();
            return true;
        }
    }

}
