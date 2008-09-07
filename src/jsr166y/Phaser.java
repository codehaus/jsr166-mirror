/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import sun.misc.Unsafe;
import java.lang.reflect.*;

/**
 * A reusable synchronization barrier, similar in functionality to a
 * {@link java.util.concurrent.CyclicBarrier} and {@link
 * java.util.concurrent.CountDownLatch} but supporting more flexible
 * usage.
 *
 * <ul>
 *
 * <li> The number of parties synchronizing on a phaser may vary over
 * time.  A task may register to be a party at any time, and may
 * deregister upon arriving at the barrier.  As is the case with most
 * basic synchronization constructs, registration and deregistration
 * affect only internal counts; they do not establish any further
 * internal bookkeeping, so tasks cannot query whether they are
 * registered. (However, you can introduce such bookkeeping in by
 * subclassing this class.)
 *
 * <li> Each generation has an associated phase value, starting at
 * zero, and advancing when all parties reach the barrier (wrapping
 * around to zero after reaching <tt>Integer.MAX_VALUE</tt>).
 *
 * <li> Like a CyclicBarrier, a Phaser may be repeatedly awaited.
 * Method <tt>arriveAndAwaitAdvance</tt> has effect analogous to
 * <tt>CyclicBarrier.await</tt>.  However, Phasers separate two
 * aspects of coordination, that may also be invoked independently:
 *
 * <ul>
 *
 *   <li> Arriving at a barrier. Methods <tt>arrive</tt> and
 *       <tt>arriveAndDeregister</tt> do not block, but return
 *       the phase value current upon entry to the method.
 *
 *   <li> Awaiting others. Method <tt>awaitAdvance</tt> requires an
 *       argument indicating the entry phase, and returns when the
 *       barrier advances to a new phase.
 * </ul>
 *
 *
 * <li> Barrier actions, performed by the task triggering a phase
 * advance while others may be waiting, are arranged by overriding
 * method <tt>onAdvance</tt>, that also controls termination.
 * Overriding this method may be used to similar but more flexible
 * effect as providing a barrier action to a CyclicBarrier.
 *
 * <li> Phasers may enter a <em>termination</em> state in which all
 * await actions immediately return, indicating (via a negative phase
 * value) that execution is complete.  Termination is triggered by
 * executing the overridable <tt>onAdvance</tt> method that is invoked
 * each time the barrier is about to be tripped. When a Phaser is
 * controlling an action with a fixed number of iterations, it is
 * often convenient to override this method to cause termination when
 * the current phase number reaches a threshold. Method
 * <tt>forceTermination</tt> is also available to abruptly release
 * waiting threads and allow them to terminate.
 *
 * <li> Phasers may be tiered to reduce contention. Phasers with large
 * numbers of parties that would otherwise experience heavy
 * synchronization contention costs may instead be arranged in trees.
 * This will typically greatly increase throughput even though it
 * incurs somewhat greater per-operation overhead.
 *
 * <li> By default, <tt>awaitAdvance</tt> continues to wait even if
 * the waiting thread is interrupted. And unlike the case in
 * CyclicBarriers, exceptions encountered while tasks wait
 * interruptibly or with timeout do not change the state of the
 * barrier. If necessary, you can perform any associated recovery
 * within handlers of those exceptions, often after invoking
 * <tt>forceTermination</tt>.
 *
 * </ul>
 *
 * <p><b>Sample usages:</b>
 *
 * <p>A Phaser may be used instead of a <tt>CountdownLatch</tt> to control
 * a one-shot action serving a variable number of parties. The typical
 * idiom is for the method setting this up to first register, then
 * start the actions, then deregister, as in:
 *
 * <pre>
 *  void runTasks(List&lt;Runnable&gt; list) {
 *    final Phaser phaser = new Phaser(1); // "1" to register self
 *    for (Runnable r : list) {
 *      phaser.register();
 *      new Thread() {
 *        public void run() {
 *          phaser.arriveAndAwaitAdvance(); // await all creation
 *          r.run();
 *          phaser.arriveAndDeregister();   // signal completion
 *        }
 *      }.start();
 *   }
 *   phaser.arrive(); // allow threads to start
 *   int p = phaser.arriveAndDeregister(); // deregister self
 *   otherActions(); // do other things while tasks execute
 *   phaser.awaitAdvance(p); // wait for all tasks to arrive
 * }
 * </pre>
 *
 * <p>One way to cause a set of threads to repeatedly perform actions
 * for a given number of iterations is to override <tt>onAdvance</tt>:
 *
 * <pre>
 *  void startTasks(List&lt;Runnable&gt; list, final int iterations) {
 *    final Phaser phaser = new Phaser() {
 *       public boolean onAdvance(int phase, int registeredParties) {
 *         return phase &gt;= iterations || registeredParties == 0;
 *       }
 *    };
 *    phaser.register();
 *    for (Runnable r : list) {
 *      phaser.register();
 *      new Thread() {
 *        public void run() {
 *           do {
 *             r.run();
 *             phaser.arriveAndAwaitAdvance();
 *           } while(!phaser.isTerminated();
 *        }
 *      }.start();
 *   }
 *   phaser.arriveAndDeregister(); // deregister self, don't wait
 * }
 * </pre>
 *
 * <p> To create a set of tasks using a tree of Phasers,
 * you could use code of the following form, assuming a
 * Task class with a constructor accepting a Phaser that
 * it registers for upon construction:
 * <pre>
 *  void build(Task[] actions, int lo, int hi, Phaser b) {
 *    int step = (hi - lo) / TASKS_PER_PHASER;
 *    if (step &gt; 1) {
 *       int i = lo;
 *       while (i &lt; hi) {
 *         int r = Math.min(i + step, hi);
 *         build(actions, i, r, new Phaser(b));
 *         i = r;
 *       }
 *    }
 *    else {
 *      for (int i = lo; i &lt; hi; ++i)
 *        actions[i] = new Task(b);
 *        // assumes new Task(b) performs b.register()
 *    }
 *  }
 *  // .. initially called, for n tasks via
 *  build(new Task[n], 0, n, new Phaser());
 * </pre>
 *
 * The best value of <tt>TASKS_PER_PHASER</tt> depends mainly on
 * expected barrier synchronization rates. A value as low as four may
 * be appropriate for extremely small per-barrier task bodies (thus
 * high rates), or up to hundreds for extremely large ones.
 *
 * </pre>
 *
 * <p><b>Implementation notes</b>: This implementation restricts the
 * maximum number of parties to 65535. Attempts to register additional
 * parties result in IllegalStateExceptions. However, you can and
 * should create tiered phasers to accommodate arbitrarily large sets
 * of participants.
 */
public class Phaser {
    /*
     * This class implements an extension of X10 "clocks".  Thanks to
     * Vijay Saraswat for the idea, and to Vivek Sarkar for
     * enhancements to extend functionality.
     */

    /**
     * Barrier state representation. Conceptually, a barrier contains
     * four values:
     *
     * * parties -- the number of parties to wait (16 bits)
     * * unarrived -- the number of parties yet to hit barrier (16 bits)
     * * phase -- the generation of the barrier (31 bits)
     * * terminated -- set if barrier is terminated (1 bit)
     *
     * However, to efficiently maintain atomicity, these values are
     * packed into a single (atomic) long. Termination uses the sign
     * bit of 32 bit representation of phase, so phase is set to -1 on
     * termination. Good performace relies on keeping state decoding
     * and encoding simple, and keeping race windows short.
     *
     * Note: there are some cheats in arrive() that rely on unarrived
     * being lowest 16 bits.
     */
    private volatile long state;

    private static final int ushortBits = 16;
    private static final int ushortMask =  (1 << ushortBits) - 1;
    private static final int phaseMask = 0x7fffffff;

    private static int unarrivedOf(long s) {
        return (int)(s & ushortMask);
    }

    private static int partiesOf(long s) {
        return (int)(s & (ushortMask << 16)) >>> 16;
    }

    private static int phaseOf(long s) {
        return (int)(s >>> 32);
    }

    private static int arrivedOf(long s) {
        return partiesOf(s) - unarrivedOf(s);
    }

    private static long stateFor(int phase, int parties, int unarrived) {
        return (((long)phase) << 32) | ((parties << 16) | unarrived);
    }

    private static long trippedStateFor(int phase, int parties) {
        return (((long)phase) << 32) | ((parties << 16) | parties);
    }

    private static IllegalStateException badBounds(int parties, int unarrived) {
        return new IllegalStateException
            ("Attempt to set " + unarrived +
             " unarrived of " + parties + " parties");
    }

    /**
     * The parent of this phaser, or null if none
     */
    private final Phaser parent;

    /**
     * The root of Phaser tree. Equals this if not in a tree.  Used to
     * support faster state push-down.
     */
    private final Phaser root;

    // Wait queues

    /**
     * Heads of Treiber stacks waiting for nonFJ threads. To eliminate
     * contention while releasing some threads while adding others, we
     * use two of them, alternating across even and odd phases.
     */
    private final AtomicReference<QNode> evenQ = new AtomicReference<QNode>();
    private final AtomicReference<QNode> oddQ  = new AtomicReference<QNode>();

    private AtomicReference<QNode> queueFor(int phase) {
        return (phase & 1) == 0? evenQ : oddQ;
    }

    /**
     * Returns current state, first resolving lagged propagation from
     * root if necessary.
     */
    private long getReconciledState() {
        return parent == null? state : reconcileState();
    }

    /**
     * Recursively resolves state.
     */
    private long reconcileState() {
        Phaser p = parent;
        long s = state;
        if (p != null) {
            while (unarrivedOf(s) == 0 && phaseOf(s) != phaseOf(root.state)) {
                long parentState = p.getReconciledState();
                int parentPhase = phaseOf(parentState);
                int phase = phaseOf(s = state);
                if (phase != parentPhase) {
                    long next = trippedStateFor(parentPhase, partiesOf(s));
                    if (casState(s, next)) {
                        releaseWaiters(phase);
                        s = next;
                    }
                }
            }
        }
        return s;
    }

    /**
     * Creates a new Phaser without any initially registered parties,
     * initial phase number 0, and no parent.
     */
    public Phaser() {
        this(null);
    }

    /**
     * Creates a new Phaser with the given numbers of registered
     * unarrived parties, initial phase number 0, and no parent.
     * @param parties the number of parties required to trip barrier.
     * @throws IllegalArgumentException if parties less than zero
     * or greater than the maximum number of parties supported.
     */
    public Phaser(int parties) {
        this(null, parties);
    }

    /**
     * Creates a new Phaser with the given parent, without any
     * initially registered parties. If parent is non-null this phaser
     * is registered with the parent and its initial phase number is
     * the same as that of parent phaser.
     * @param parent the parent phaser.
     */
    public Phaser(Phaser parent) {
        int phase = 0;
        this.parent = parent;
        if (parent != null) {
            this.root = parent.root;
            phase = parent.register();
        }
        else
            this.root = this;
        this.state = trippedStateFor(phase, 0);
    }

    /**
     * Creates a new Phaser with the given parent and numbers of
     * registered unarrived parties. If parent is non-null this phaser
     * is registered with the parent and its initial phase number is
     * the same as that of parent phaser.
     * @param parent the parent phaser.
     * @param parties the number of parties required to trip barrier.
     * @throws IllegalArgumentException if parties less than zero
     * or greater than the maximum number of parties supported.
     */
    public Phaser(Phaser parent, int parties) {
        if (parties < 0 || parties > ushortMask)
            throw new IllegalArgumentException("Illegal number of parties");
        int phase = 0;
        this.parent = parent;
        if (parent != null) {
            this.root = parent.root;
            phase = parent.register();
        }
        else
            this.root = this;
        this.state = trippedStateFor(phase, parties);
    }

    /**
     * Adds a new unarrived party to this phaser.
     * @return the current barrier phase number upon registration
     * @throws IllegalStateException if attempting to register more
     * than the maximum supported number of parties.
     */
    public int register() {
        return doRegister(1);
    }

    /**
     * Adds the given number of new unarrived parties to this phaser.
     * @param parties the number of parties required to trip barrier.
     * @return the current barrier phase number upon registration
     * @throws IllegalStateException if attempting to register more
     * than the maximum supported number of parties.
     */
    public int bulkRegister(int parties) {
        if (parties < 0)
            throw new IllegalArgumentException();
        if (parties == 0)
            return getPhase();
        return doRegister(parties);
    }

    /**
     * Shared code for register, bulkRegister
     */
    private int doRegister(int registrations) {
        int phase;
        for (;;) {
            long s = getReconciledState();
            phase = phaseOf(s);
            int unarrived = unarrivedOf(s) + registrations;
            int parties = partiesOf(s) + registrations;
            if (phase < 0)
                break;
            if (parties > ushortMask || unarrived > ushortMask)
                throw badBounds(parties, unarrived);
            if (phase == phaseOf(root.state) &&
                casState(s, stateFor(phase, parties, unarrived)))
                break;
        }
        return phase;
    }

    /**
     * Arrives at the barrier, but does not wait for others.  (You can
     * in turn wait for others via {@link #awaitAdvance}).
     *
     * @return the barrier phase number upon entry to this method, or a
     * negative value if terminated;
     * @throws IllegalStateException if not terminated and the number
     * of unarrived parties would become negative.
     */
    public int arrive() {
        int phase;
        for (;;) {
            long s = state;
            phase = phaseOf(s);
            int parties = partiesOf(s);
            int unarrived = unarrivedOf(s) - 1;
            if (unarrived > 0) {        // Not the last arrival
                if (casState(s, s - 1)) // s-1 adds one arrival
                    break;
            }
            else if (unarrived == 0) {  // the last arrival
                Phaser par = parent;
                if (par == null) {      // directly trip
                    if (casState
                        (s,
                         trippedStateFor(onAdvance(phase, parties)? -1 :
                                         ((phase + 1) & phaseMask), parties))) {
                        releaseWaiters(phase);
                        break;
                    }
                }
                else {                  // cascade to parent
                    if (casState(s, s - 1)) { // zeroes unarrived
                        par.arrive();
                        reconcileState();
                        break;
                    }
                }
            }
            else if (phase < 0) // Don't throw exception if terminated
                break;
            else if (phase != phaseOf(root.state)) // or if unreconciled
                reconcileState();
            else
                throw badBounds(parties, unarrived);
        }
        return phase;
    }

    /**
     * Arrives at the barrier, and deregisters from it, without
     * waiting for others. Deregistration reduces number of parties
     * required to trip the barrier in future phases.  If this phaser
     * has a parent, and deregistration causes this phaser to have
     * zero parties, this phaser is also deregistered from its parent.
     *
     * @return the current barrier phase number upon entry to
     * this method, or a negative value if terminated;
     * @throws IllegalStateException if not terminated and the number
     * of registered or unarrived parties would become negative.
     */
    public int arriveAndDeregister() {
        // similar code to arrive, but too different to merge
        Phaser par = parent;
        int phase;
        for (;;) {
            long s = state;
            phase = phaseOf(s);
            int parties = partiesOf(s) - 1;
            int unarrived = unarrivedOf(s) - 1;
            if (parties >= 0) {
                if (unarrived > 0 || (unarrived == 0 && par != null)) {
                    if (casState
                        (s,
                         stateFor(phase, parties, unarrived))) {
                        if (unarrived == 0) {
                            par.arriveAndDeregister();
                            reconcileState();
                        }
                        break;
                    }
                    continue;
                }
                if (unarrived == 0) {
                    if (casState
                        (s,
                         trippedStateFor(onAdvance(phase, parties)? -1 :
                                         ((phase + 1) & phaseMask), parties))) {
                        releaseWaiters(phase);
                        break;
                    }
                    continue;
                }
                if (phase < 0)
                    break;
                if (par != null && phase != phaseOf(root.state)) {
                    reconcileState();
                    continue;
                }
            }
            throw badBounds(parties, unarrived);
        }
        return phase;
    }

    /**
     * Arrives at the barrier and awaits others. Equivalent in effect
     * to <tt>awaitAdvance(arrive())</tt>.  If you instead need to
     * await with interruption of timeout, and/or deregister upon
     * arrival, you can arrange them using analogous constructions.
     * @return the phase on entry to this method
     * @throws IllegalStateException if not terminated and the number
     * of unarrived parties would become negative.
     */
    public int arriveAndAwaitAdvance() {
        return awaitAdvance(arrive());
    }

    /**
     * Awaits the phase of the barrier to advance from the given
     * value, or returns immediately if argument is negative or this
     * barrier is terminated.
     * @param phase the phase on entry to this method
     * @return the phase on exit from this method
     */
    public int awaitAdvance(int phase) {
        if (phase < 0)
            return phase;
        long s = getReconciledState();
        int p = phaseOf(s);
        if (p != phase)
            return p;
        if (unarrivedOf(s) == 0)
            parent.awaitAdvance(phase);
        // Fall here even if parent waited, to reconcile and help release
        return untimedWait(phase);
    }

    /**
     * Awaits the phase of the barrier to advance from the given
     * value, or returns immediately if argumet is negative or this
     * barrier is terminated, or throws InterruptedException if
     * interrupted while waiting.
     * @param phase the phase on entry to this method
     * @return the phase on exit from this method
     * @throws InterruptedException if thread interrupted while waiting
     */
    public int awaitAdvanceInterruptibly(int phase) throws InterruptedException {
        if (phase < 0)
            return phase;
        long s = getReconciledState();
        int p = phaseOf(s);
        if (p != phase)
            return p;
        if (unarrivedOf(s) != 0)
            parent.awaitAdvanceInterruptibly(phase);
        return interruptibleWait(phase);
    }

    /**
     * Awaits the phase of the barrier to advance from the given value
     * or the given timeout elapses, or returns immediately if
     * argument is negative or this barrier is terminated.
     * @param phase the phase on entry to this method
     * @return the phase on exit from this method
     * @throws InterruptedException if thread interrupted while waiting
     * @throws TimeoutException if timed out while waiting
     */
    public int awaitAdvanceInterruptibly(int phase, long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException {
        if (phase < 0)
            return phase;
        long s = getReconciledState();
        int p = phaseOf(s);
        if (p != phase)
            return p;
        if (unarrivedOf(s) == 0)
            parent.awaitAdvanceInterruptibly(phase, timeout, unit);
        return timedWait(phase, unit.toNanos(timeout));
    }

    /**
     * Forces this barrier to enter termination state. Counts of
     * arrived and registered parties are unaffected. If this phaser
     * has a parent, it too is terminated. This method may be useful
     * for coordinating recovery after one or more tasks encounter
     * unexpected exceptions.
     */
    public void forceTermination() {
        for (;;) {
            long s = getReconciledState();
            int phase = phaseOf(s);
            int parties = partiesOf(s);
            int unarrived = unarrivedOf(s);
            if (phase < 0 ||
                casState(s, stateFor(-1, parties, unarrived))) {
                releaseWaiters(0);
                releaseWaiters(1);
                if (parent != null)
                    parent.forceTermination();
                return;
            }
        }
    }

    /**
     * Returns the current phase number. The maximum phase number is
     * <tt>Integer.MAX_VALUE</tt>, after which it restarts at
     * zero. Upon termination, the phase number is negative.
     * @return the phase number, or a negative value if terminated
     */
    public final int getPhase() {
        return phaseOf(getReconciledState());
    }

    /**
     * Returns true if the current phase number equals the given phase.
     * @param phase the phase
     * @return true if the current phase number equals the given phase.
     */
    public final boolean hasPhase(int phase) {
        return phaseOf(getReconciledState()) == phase;
    }

    /**
     * Returns the number of parties registered at this barrier.
     * @return the number of parties
     */
    public int getRegisteredParties() {
        return partiesOf(state);
    }

    /**
     * Returns the number of parties that have arrived at the current
     * phase of this barrier.
     * @return the number of arrived parties
     */
    public int getArrivedParties() {
        return arrivedOf(state);
    }

    /**
     * Returns the number of registered parties that have not yet
     * arrived at the current phase of this barrier.
     * @return the number of unarrived parties
     */
    public int getUnarrivedParties() {
        return unarrivedOf(state);
    }

    /**
     * Returns the parent of this phaser, or null if none.
     * @return the parent of this phaser, or null if none.
     */
    public Phaser getParent() {
        return parent;
    }

    /**
     * Returns the root ancestor of this phaser, which is the same as
     * this phaser if it has no parent.
     * @return the root ancestor of this phaser.
     */
    public Phaser getRoot() {
        return root;
    }

    /**
     * Returns true if this barrier has been terminated.
     * @return true if this barrier has been terminated
     */
    public boolean isTerminated() {
        return getPhase() < 0;
    }

    /**
     * Overridable method to perform an action upon phase advance, and
     * to control termination. This method is invoked whenever the
     * barrier is tripped (and thus all other waiting parties are
     * dormant). If it returns true, then, rather than advance the
     * phase number, this barrier will be set to a final termination
     * state, and subsequent calls to <tt>isTerminated</tt> will
     * return true.
     *
     * <p> The default version returns true when the number of
     * registered parties is zero. Normally, overrides that arrange
     * termination for other reasons should also preserve this
     * property.
     *
     * <p> You may override this method to perform an action with side
     * effects visible to participating tasks, but it is in general
     * only sensible to do so in designs where all parties register
     * before any arrive, and all <tt>awaitAdvance</tt> at each phase.
     * Otherwise, you cannot ensure lack of interference. In
     * particular, this method may be invoked more than once per
     * transition if other parties successfully register while the
     * invocation of this method is in progress, thus postponing the
     * transition until those parties also arrive, re-triggering this
     * method.
     *
     * @param phase the phase number on entering the barrier
     * @param registeredParties the current number of registered
     * parties.
     * @return true if this barrier should terminate
     */
    protected boolean onAdvance(int phase, int registeredParties) {
        return registeredParties <= 0;
    }

    /**
     * Returns a string identifying this phaser, as well as its
     * state.  The state, in brackets, includes the String {@code
     * "phase ="} followed by the phase number, {@code "parties ="}
     * followed by the number of registered parties, and {@code
     * "arrived ="} followed by the number of arrived parties
     *
     * @return a string identifying this barrier, as well as its state
     */
    public String toString() {
        long s = getReconciledState();
        return super.toString() + "[phase = " + phaseOf(s) + " parties = " + partiesOf(s) + " arrived = " + arrivedOf(s) + "]";
    }

    // methods for waiting

    /** The number of CPUs, for spin control */
    static final int NCPUS = Runtime.getRuntime().availableProcessors();

    /**
     * The number of times to spin before blocking in timed waits.
     * The value is empirically derived.
     */
    static final int maxTimedSpins = (NCPUS < 2)? 0 : 32;

    /**
     * The number of times to spin before blocking in untimed waits.
     * This is greater than timed value because untimed waits spin
     * faster since they don't need to check times on each spin.
     */
    static final int maxUntimedSpins = maxTimedSpins * 32;

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices.
     */
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * Wait nodes for Treiber stack representing wait queue for non-FJ
     * tasks.
     */
    static final class QNode {
        QNode next;
        volatile Thread thread; // nulled to cancel wait
        QNode() {
            thread = Thread.currentThread();
        }
        void signal() {
            Thread t = thread;
            if (t != null) {
                thread = null;
                LockSupport.unpark(t);
            }
        }
    }

    /**
     * Removes and signals waiting threads from wait queue
     */
    private void releaseWaiters(int phase) {
        AtomicReference<QNode> head = queueFor(phase);
        QNode q;
        while ((q = head.get()) != null) {
            if (head.compareAndSet(q, q.next))
                q.signal();
        }
    }

    /**
     * Enqueues node and waits unless aborted or signalled.
     */
    private int untimedWait(int phase) {
        int spins = maxUntimedSpins;
        QNode node = null;
        boolean interrupted = false;
        boolean queued = false;
        int p;
        while ((p = getPhase()) == phase) {
            interrupted = Thread.interrupted();
            if (node != null) {
                if (!queued) {
                    AtomicReference<QNode> head = queueFor(phase);
                    queued = head.compareAndSet(node.next = head.get(), node);
                }
                else if (node.thread != null)
                    LockSupport.park(this);
            }
            else if (spins <= 0)
                node = new QNode();
            else
                --spins;
        }
        if (node != null)
            node.thread = null;
        if (interrupted)
            Thread.currentThread().interrupt();
        releaseWaiters(phase);
        return p;
    }

    /**
     * Messier interruptible version
     */
    private int interruptibleWait(int phase) throws InterruptedException {
        int spins = maxUntimedSpins;
        QNode node = null;
        boolean queued = false;
        boolean interrupted = false;
        int p;
        while ((p = getPhase()) == phase) {
            if (interrupted = Thread.interrupted())
                break;
            if (node != null) {
                if (!queued) {
                    AtomicReference<QNode> head = queueFor(phase);
                    queued = head.compareAndSet(node.next = head.get(), node);
                }
                else if (node.thread != null)
                    LockSupport.park(this);
            }
            else if (spins <= 0)
                node = new QNode();
            else
                --spins;
        }
        if (node != null)
            node.thread = null;
        if (interrupted)
            throw new InterruptedException();
        releaseWaiters(phase);
        return p;
    }

    /**
     * Even messier timeout version.
     */
    private int timedWait(int phase, long nanos)
        throws InterruptedException, TimeoutException {
        int p;
        if ((p = getPhase()) == phase) {
            long lastTime = System.nanoTime();
            int spins = maxTimedSpins;
            QNode node = null;
            boolean queued = false;
            boolean interrupted = false;
            while ((p = getPhase()) == phase) {
                if (interrupted = Thread.interrupted())
                    break;
                long now = System.nanoTime();
                if ((nanos -= now - lastTime) <= 0)
                    break;
                lastTime = now;
                if (node != null) {
                    if (!queued) {
                        AtomicReference<QNode> head = queueFor(phase);
                        queued = head.compareAndSet(node.next = head.get(), node);
                    }
                    else if (node.thread != null &&
                             nanos > spinForTimeoutThreshold) {
                        LockSupport.parkNanos(this, nanos);
                    }
                }
                else if (spins <= 0)
                    node = new QNode();
                else
                    --spins;
            }
            if (node != null)
                node.thread = null;
            if (interrupted)
                throw new InterruptedException();
            if (p == phase && (p = getPhase()) == phase)
                throw new TimeoutException();
        }
        releaseWaiters(phase);
        return p;
    }

    // Temporary Unsafe mechanics for preliminary release

    static final Unsafe _unsafe;
    static final long stateOffset;

    static {
        try {
            if (Phaser.class.getClassLoader() != null) {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                _unsafe = (Unsafe)f.get(null);
            }
            else
                _unsafe = Unsafe.getUnsafe();
            stateOffset = _unsafe.objectFieldOffset
                (Phaser.class.getDeclaredField("state"));
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize intrinsics", e);
        }
    }

    final boolean casState(long cmp, long val) {
        return _unsafe.compareAndSwapLong(this, stateOffset, cmp, val);
    }
}
