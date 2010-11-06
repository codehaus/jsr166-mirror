/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * A reusable synchronization barrier, similar in functionality to
 * {@link java.util.concurrent.CyclicBarrier CyclicBarrier} and
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * but supporting more flexible usage.
 *
 * <p> <b>Registration.</b> Unlike the case for other barriers, the
 * number of parties <em>registered</em> to synchronize on a phaser
 * may vary over time.  Tasks may be registered at any time (using
 * methods {@link #register}, {@link #bulkRegister}, or forms of
 * constructors establishing initial numbers of parties), and
 * optionally deregistered upon any arrival (using {@link
 * #arriveAndDeregister}).  As is the case with most basic
 * synchronization constructs, registration and deregistration affect
 * only internal counts; they do not establish any further internal
 * bookkeeping, so tasks cannot query whether they are registered.
 * (However, you can introduce such bookkeeping by subclassing this
 * class.)
 *
 * <p> <b>Synchronization.</b> Like a {@code CyclicBarrier}, a {@code
 * Phaser} may be repeatedly awaited.  Method {@link
 * #arriveAndAwaitAdvance} has effect analogous to {@link
 * java.util.concurrent.CyclicBarrier#await CyclicBarrier.await}. Each
 * generation of a {@code Phaser} has an associated phase number. The
 * phase number starts at zero, and advances when all parties arrive
 * at the barrier, wrapping around to zero after reaching {@code
 * Integer.MAX_VALUE}. The use of phase numbers enables independent
 * control of actions upon arrival at a barrier and upon awaiting
 * others, via two kinds of methods that may be invoked by any
 * registered party:
 *
 * <ul>
 *
 *   <li> <b>Arrival.</b> Methods {@link #arrive} and
 *       {@link #arriveAndDeregister} record arrival at a
 *       barrier. These methods do not block, but return an associated
 *       <em>arrival phase number</em>; that is, the phase number of
 *       the barrier to which the arrival applied. When the final
 *       party for a given phase arrives, an optional barrier action
 *       is performed and the phase advances.  Barrier actions,
 *       performed by the party triggering a phase advance, are
 *       arranged by overriding method {@link #onAdvance(int, int)},
 *       which also controls termination. Overriding this method is
 *       similar to, but more flexible than, providing a barrier
 *       action to a {@code CyclicBarrier}.
 *
 *   <li> <b>Waiting.</b> Method {@link #awaitAdvance} requires an
 *       argument indicating an arrival phase number, and returns when
 *       the barrier advances to (or is already at) a different phase.
 *       Unlike similar constructions using {@code CyclicBarrier},
 *       method {@code awaitAdvance} continues to wait even if the
 *       waiting thread is interrupted. Interruptible and timeout
 *       versions are also available, but exceptions encountered while
 *       tasks wait interruptibly or with timeout do not change the
 *       state of the barrier. If necessary, you can perform any
 *       associated recovery within handlers of those exceptions,
 *       often after invoking {@code forceTermination}.  Phasers may
 *       also be used by tasks executing in a {@link ForkJoinPool},
 *       which will ensure sufficient parallelism to execute tasks
 *       when others are blocked waiting for a phase to advance.
 *
 * </ul>
 *
 * <p> <b>Termination.</b> A {@code Phaser} may enter a
 * <em>termination</em> state in which all synchronization methods
 * immediately return without updating phaser state or waiting for
 * advance, and indicating (via a negative phase value) that execution
 * is complete.  Termination is triggered when an invocation of {@code
 * onAdvance} returns {@code true}.  As illustrated below, when
 * phasers control actions with a fixed number of iterations, it is
 * often convenient to override this method to cause termination when
 * the current phase number reaches a threshold. Method {@link
 * #forceTermination} is also available to abruptly release waiting
 * threads and allow them to terminate.
 *
 * <p> <b>Tiering.</b> Phasers may be <em>tiered</em> (i.e., arranged
 * in tree structures) to reduce contention. Phasers with large
 * numbers of parties that would otherwise experience heavy
 * synchronization contention costs may instead be set up so that
 * groups of sub-phasers share a common parent.  This may greatly
 * increase throughput even though it incurs greater per-operation
 * overhead.
 *
 * <p><b>Monitoring.</b> While synchronization methods may be invoked
 * only by registered parties, the current state of a phaser may be
 * monitored by any caller.  At any given moment there are {@link
 * #getRegisteredParties} parties in total, of which {@link
 * #getArrivedParties} have arrived at the current phase ({@link
 * #getPhase}).  When the remaining ({@link #getUnarrivedParties})
 * parties arrive, the phase advances.  The values returned by these
 * methods may reflect transient states and so are not in general
 * useful for synchronization control.  Method {@link #toString}
 * returns snapshots of these state queries in a form convenient for
 * informal monitoring.
 *
 * <p><b>Sample usages:</b>
 *
 * <p>A {@code Phaser} may be used instead of a {@code CountDownLatch}
 * to control a one-shot action serving a variable number of parties.
 * The typical idiom is for the method setting this up to first
 * register, then start the actions, then deregister, as in:
 *
 *  <pre> {@code
 * void runTasks(List<Runnable> tasks) {
 *   final Phaser phaser = new Phaser(1); // "1" to register self
 *   // create and start threads
 *   for (Runnable task : tasks) {
 *     phaser.register();
 *     new Thread() {
 *       public void run() {
 *         phaser.arriveAndAwaitAdvance(); // await all creation
 *         task.run();
 *       }
 *     }.start();
 *   }
 *
 *   // allow threads to start and deregister self
 *   phaser.arriveAndDeregister();
 * }}</pre>
 *
 * <p>One way to cause a set of threads to repeatedly perform actions
 * for a given number of iterations is to override {@code onAdvance}:
 *
 *  <pre> {@code
 * void startTasks(List<Runnable> tasks, final int iterations) {
 *   final Phaser phaser = new Phaser() {
 *     protected boolean onAdvance(int phase, int registeredParties) {
 *       return phase >= iterations || registeredParties == 0;
 *     }
 *   };
 *   phaser.register();
 *   for (final Runnable task : tasks) {
 *     phaser.register();
 *     new Thread() {
 *       public void run() {
 *         do {
 *           task.run();
 *           phaser.arriveAndAwaitAdvance();
 *         } while (!phaser.isTerminated());
 *       }
 *     }.start();
 *   }
 *   phaser.arriveAndDeregister(); // deregister self, don't wait
 * }}</pre>
 *
 * If the main task must later await termination, it
 * may re-register and then execute a similar loop:
 *  <pre> {@code
 *   // ...
 *   phaser.register();
 *   while (!phaser.isTerminated())
 *     phaser.arriveAndAwaitAdvance();}</pre>
 *
 * <p>Related constructions may be used to await particular phase numbers
 * in contexts where you are sure that the phase will never wrap around
 * {@code Integer.MAX_VALUE}. For example:
 *
 *  <pre> {@code
 * void awaitPhase(Phaser phaser, int phase) {
 *   int p = phaser.register(); // assumes caller not already registered
 *   while (p < phase) {
 *     if (phaser.isTerminated())
 *       // ... deal with unexpected termination
 *     else
 *       p = phaser.arriveAndAwaitAdvance();
 *   }
 *   phaser.arriveAndDeregister();
 * }}</pre>
 *
 *
 * <p>To create a set of tasks using a tree of phasers,
 * you could use code of the following form, assuming a
 * Task class with a constructor accepting a phaser that
 * it registers with upon construction:
 *
 *  <pre> {@code
 * void build(Task[] actions, int lo, int hi, Phaser ph) {
 *   if (hi - lo > TASKS_PER_PHASER) {
 *     for (int i = lo; i < hi; i += TASKS_PER_PHASER) {
 *       int j = Math.min(i + TASKS_PER_PHASER, hi);
 *       build(actions, i, j, new Phaser(ph));
 *     }
 *   } else {
 *     for (int i = lo; i < hi; ++i)
 *       actions[i] = new Task(ph);
 *       // assumes new Task(ph) performs ph.register()
 *   }
 * }
 * // .. initially called, for n tasks via
 * build(new Task[n], 0, n, new Phaser());}</pre>
 *
 * The best value of {@code TASKS_PER_PHASER} depends mainly on
 * expected barrier synchronization rates. A value as low as four may
 * be appropriate for extremely small per-barrier task bodies (thus
 * high rates), or up to hundreds for extremely large ones.
 *
 * <p><b>Implementation notes</b>: This implementation restricts the
 * maximum number of parties to 65535. Attempts to register additional
 * parties result in {@code IllegalStateException}. However, you can and
 * should create tiered phasers to accommodate arbitrarily large sets
 * of participants.
 *
 * @since 1.7
 * @author Doug Lea
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
     * termination. Good performance relies on keeping state decoding
     * and encoding simple, and keeping race windows short.
     *
     * Note: there are some cheats in arrive() that rely on unarrived
     * count being lowest 16 bits.
     */
    private volatile long state;

    private static final int ushortMask = 0xffff;
    private static final int phaseMask  = 0x7fffffff;

    private static int unarrivedOf(long s) {
        return (int) (s & ushortMask);
    }

    private static int partiesOf(long s) {
        return ((int) s) >>> 16;
    }

    private static int phaseOf(long s) {
        return (int) (s >>> 32);
    }

    private static int arrivedOf(long s) {
        return partiesOf(s) - unarrivedOf(s);
    }

    private static long stateFor(int phase, int parties, int unarrived) {
        return ((((long) phase) << 32) | (((long) parties) << 16) |
                (long) unarrived);
    }

    private static long trippedStateFor(int phase, int parties) {
        long lp = (long) parties;
        return (((long) phase) << 32) | (lp << 16) | lp;
    }

    /**
     * Returns message string for bad bounds exceptions.
     */
    private static String badBounds(int parties, int unarrived) {
        return ("Attempt to set " + unarrived +
                " unarrived of " + parties + " parties");
    }

    /**
     * The parent of this phaser, or null if none
     */
    private final Phaser parent;

    /**
     * The root of phaser tree. Equals this if not in a tree.  Used to
     * support faster state push-down.
     */
    private final Phaser root;

    // Wait queues

    /**
     * Heads of Treiber stacks for waiting threads. To eliminate
     * contention when releasing some threads while adding others, we
     * use two of them, alternating across even and odd phases.
     * Subphasers share queues with root to speed up releases.
     */
    private final AtomicReference<QNode> evenQ;
    private final AtomicReference<QNode> oddQ;

    private AtomicReference<QNode> queueFor(int phase) {
        return ((phase & 1) == 0) ? evenQ : oddQ;
    }

    /**
     * Returns current state, first resolving lagged propagation from
     * root if necessary.
     */
    private long getReconciledState() {
        return (parent == null) ? state : reconcileState();
    }

    /**
     * Recursively resolves state.
     */
    private long reconcileState() {
        Phaser par = parent;
        long s = state;
        if (par != null) {
            int phase, rootPhase;
            while ((phase = phaseOf(s)) >= 0 &&
                   (rootPhase = phaseOf(root.state)) != phase &&
                   (rootPhase < 0 || unarrivedOf(s) == 0)) {
                int parentPhase = phaseOf(par.getReconciledState());
                if (parentPhase != phase) {
                    long next = trippedStateFor(parentPhase, partiesOf(s));
                    if (state == s)
                        UNSAFE.compareAndSwapLong(this, stateOffset, s, next);
                }
                s = state;
            }
        }
        return s;
    }

    /**
     * Creates a new phaser without any initially registered parties,
     * initial phase number 0, and no parent. Any thread using this
     * phaser will need to first register for it.
     */
    public Phaser() {
        this(null, 0);
    }

    /**
     * Creates a new phaser with the given number of registered
     * unarrived parties, initial phase number 0, and no parent.
     *
     * @param parties the number of parties required to trip barrier
     * @throws IllegalArgumentException if parties less than zero
     * or greater than the maximum number of parties supported
     */
    public Phaser(int parties) {
        this(null, parties);
    }

    /**
     * Creates a new phaser with the given parent, without any
     * initially registered parties. If parent is non-null this phaser
     * is registered with the parent and its initial phase number is
     * the same as that of parent phaser.
     *
     * @param parent the parent phaser
     */
    public Phaser(Phaser parent) {
        this(parent, 0);
    }

    /**
     * Creates a new phaser with the given parent and number of
     * registered unarrived parties. If parent is non-null, this phaser
     * is registered with the parent and its initial phase number is
     * the same as that of parent phaser.
     *
     * @param parent the parent phaser
     * @param parties the number of parties required to trip barrier
     * @throws IllegalArgumentException if parties less than zero
     * or greater than the maximum number of parties supported
     */
    public Phaser(Phaser parent, int parties) {
        if (parties < 0 || parties > ushortMask)
            throw new IllegalArgumentException("Illegal number of parties");
        int phase;
        this.parent = parent;
        if (parent != null) {
            Phaser r = parent.root;
            this.root = r;
            this.evenQ = r.evenQ;
            this.oddQ = r.oddQ;
            phase = parent.register();
        }
        else {
            this.root = this;
            this.evenQ = new AtomicReference<QNode>();
            this.oddQ = new AtomicReference<QNode>();
            phase = 0;
        }
        this.state = trippedStateFor(phase, parties);
    }

    /**
     * Adds a new unarrived party to this phaser.
     * If an ongoing invocation of {@link #onAdvance} is in progress,
     * this method may wait until its completion before registering.
     *
     * @return the arrival phase number to which this registration applied
     * @throws IllegalStateException if attempting to register more
     * than the maximum supported number of parties
     */
    public int register() {
        return doRegister(1);
    }

    /**
     * Adds the given number of new unarrived parties to this phaser.
     * If an ongoing invocation of {@link #onAdvance} is in progress,
     * this method may wait until its completion before registering.
     *
     * @param parties the number of additional parties required to trip barrier
     * @return the arrival phase number to which this registration applied
     * @throws IllegalStateException if attempting to register more
     * than the maximum supported number of parties
     * @throws IllegalArgumentException if {@code parties < 0}
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
        Phaser par = parent;
        long s;
        int phase;
        while ((phase = phaseOf(s = par==null? state:reconcileState())) >= 0) {
            int p = partiesOf(s);
            int u = unarrivedOf(s);
            int unarrived = u + registrations;
            int parties = p + registrations;
            if (u == 0 && p != 0)  // if tripped, wait for advance
                untimedWait(phase);
            else if (parties > ushortMask)
                throw new IllegalStateException(badBounds(parties, unarrived));
            else if (par == null || phaseOf(root.state) == phase) {
                long next = stateFor(phase, parties, unarrived);
                if (UNSAFE.compareAndSwapLong(this, stateOffset, s, next))
                    break;
            }
        }
        return phase;
    }

    /**
     * Arrives at the barrier, but does not wait for others.  (You can
     * in turn wait for others via {@link #awaitAdvance}).  It is an
     * unenforced usage error for an unregistered party to invoke this
     * method.
     *
     * @return the arrival phase number, or a negative value if terminated
     * @throws IllegalStateException if not terminated and the number
     * of unarrived parties would become negative
     */
    public int arrive() {
        Phaser par = parent;
        long s;
        int phase;
        while ((phase = phaseOf(s = par==null? state:reconcileState())) >= 0) {
            int parties = partiesOf(s);
            int unarrived = unarrivedOf(s) - 1;
            if (unarrived > 0) {                // Not the last arrival
                if (UNSAFE.compareAndSwapLong(this, stateOffset, s, s - 1))
                    break;                      // s-1 adds one arrival
            }
            else if (unarrived < 0)
                throw new IllegalStateException(badBounds(parties, unarrived));
            else if (par == null) {             // directly trip
                long next = trippedStateFor(onAdvance(phase, parties) ? -1 :
                                            ((phase + 1) & phaseMask),
                                            parties);
                if (UNSAFE.compareAndSwapLong(this, stateOffset, s, next)) {
                    releaseWaiters(phase);
                    break;
                }
            }
            else if (phaseOf(root.state) == phase &&
                     UNSAFE.compareAndSwapLong(this, stateOffset, s, s - 1)) {
                par.arrive();                   // cascade to parent
                reconcileState();
                break;
            }
        }
        return phase;
    }

    /**
     * Arrives at the barrier and deregisters from it without waiting
     * for others. Deregistration reduces the number of parties
     * required to trip the barrier in future phases.  If this phaser
     * has a parent, and deregistration causes this phaser to have
     * zero parties, this phaser also arrives at and is deregistered
     * from its parent.  It is an unenforced usage error for an
     * unregistered party to invoke this method.
     *
     * @return the arrival phase number, or a negative value if terminated
     * @throws IllegalStateException if not terminated and the number
     * of registered or unarrived parties would become negative
     */
    public int arriveAndDeregister() {
        // similar to arrive, but too different to merge
        Phaser par = parent;
        long s;
        int phase;
        while ((phase = phaseOf(s = par==null? state:reconcileState())) >= 0) {
            int parties = partiesOf(s) - 1;
            int unarrived = unarrivedOf(s) - 1;
            if (unarrived > 0) {
                long next = stateFor(phase, parties, unarrived);
                if (UNSAFE.compareAndSwapLong(this, stateOffset, s, next))
                    break;
            }
            else if (unarrived < 0)
                throw new IllegalStateException(badBounds(parties, unarrived));
            else if (par == null) {
                long next = trippedStateFor(onAdvance(phase, parties)? -1:
                                            (phase + 1) & phaseMask,
                                            parties);
                if (UNSAFE.compareAndSwapLong(this, stateOffset, s, next)) {
                    releaseWaiters(phase);
                    break;
                }
            }
            else if (phaseOf(root.state) == phase) {
                long next = stateFor(phase, parties, 0);
                if (UNSAFE.compareAndSwapLong(this, stateOffset, s, next)) {
                    if (parties == 0)
                        par.arriveAndDeregister();
                    else
                        par.arrive();
                    reconcileState();
                    break;
                }
            }
        }
        return phase;
    }

    /**
     * Arrives at the barrier and awaits others. Equivalent in effect
     * to {@code awaitAdvance(arrive())}.  If you need to await with
     * interruption or timeout, you can arrange this with an analogous
     * construction using one of the other forms of the {@code
     * awaitAdvance} method.  If instead you need to deregister upon
     * arrival, use {@link #arriveAndDeregister}. It is an unenforced
     * usage error for an unregistered party to invoke this method.
     *
     * @return the arrival phase number, or a negative number if terminated
     * @throws IllegalStateException if not terminated and the number
     * of unarrived parties would become negative
     */
    public int arriveAndAwaitAdvance() {
        return awaitAdvance(arrive());
    }

    /**
     * Awaits the phase of the barrier to advance from the given phase
     * value, returning immediately if the current phase of the
     * barrier is not equal to the given phase value or this barrier
     * is terminated.
     *
     * @param phase an arrival phase number, or negative value if
     * terminated; this argument is normally the value returned by a
     * previous call to {@code arrive} or its variants
     * @return the next arrival phase number, or a negative value
     * if terminated or argument is negative
     */
    public int awaitAdvance(int phase) {
        if (phase < 0)
            return phase;
        int p = getPhase();
        if (p != phase)
            return p;
        return untimedWait(phase);
    }

    /**
     * Awaits the phase of the barrier to advance from the given phase
     * value, throwing {@code InterruptedException} if interrupted
     * while waiting, or returning immediately if the current phase of
     * the barrier is not equal to the given phase value or this
     * barrier is terminated.
     *
     * @param phase an arrival phase number, or negative value if
     * terminated; this argument is normally the value returned by a
     * previous call to {@code arrive} or its variants
     * @return the next arrival phase number, or a negative value
     * if terminated or argument is negative
     * @throws InterruptedException if thread interrupted while waiting
     */
    public int awaitAdvanceInterruptibly(int phase)
        throws InterruptedException {
        if (phase < 0)
            return phase;
        int p = getPhase();
        if (p != phase)
            return p;
        return interruptibleWait(phase);
    }

    /**
     * Awaits the phase of the barrier to advance from the given phase
     * value or the given timeout to elapse, throwing {@code
     * InterruptedException} if interrupted while waiting, or
     * returning immediately if the current phase of the barrier is
     * not equal to the given phase value or this barrier is
     * terminated.
     *
     * @param phase an arrival phase number, or negative value if
     * terminated; this argument is normally the value returned by a
     * previous call to {@code arrive} or its variants
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the next arrival phase number, or a negative value
     * if terminated or argument is negative
     * @throws InterruptedException if thread interrupted while waiting
     * @throws TimeoutException if timed out while waiting
     */
    public int awaitAdvanceInterruptibly(int phase,
                                         long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException {
        long nanos = unit.toNanos(timeout);
        if (phase < 0)
            return phase;
        int p = getPhase();
        if (p != phase)
            return p;
        return timedWait(phase, nanos);
    }

    /**
     * Forces this barrier to enter termination state. Counts of
     * arrived and registered parties are unaffected. If this phaser
     * has a parent, it too is terminated. This method may be useful
     * for coordinating recovery after one or more tasks encounter
     * unexpected exceptions.
     */
    public void forceTermination() {
        Phaser r = root;    // force at root then reconcile
        long s;
        while (phaseOf(s = r.state) >= 0)
            UNSAFE.compareAndSwapLong(r, stateOffset, s,
                                      stateFor(-1, partiesOf(s),
                                               unarrivedOf(s)));
        reconcileState();
        releaseWaiters(0);  // ensure wakeups on both queues
        releaseWaiters(1);
    }

    /**
     * Returns the current phase number. The maximum phase number is
     * {@code Integer.MAX_VALUE}, after which it restarts at
     * zero. Upon termination, the phase number is negative.
     *
     * @return the phase number, or a negative value if terminated
     */
    public final int getPhase() {
        return phaseOf(getReconciledState());
    }

    /**
     * Returns the number of parties registered at this barrier.
     *
     * @return the number of parties
     */
    public int getRegisteredParties() {
        return partiesOf(getReconciledState());
    }

    /**
     * Returns the number of registered parties that have arrived at
     * the current phase of this barrier.
     *
     * @return the number of arrived parties
     */
    public int getArrivedParties() {
        return arrivedOf(getReconciledState());
    }

    /**
     * Returns the number of registered parties that have not yet
     * arrived at the current phase of this barrier.
     *
     * @return the number of unarrived parties
     */
    public int getUnarrivedParties() {
        return unarrivedOf(getReconciledState());
    }

    /**
     * Returns the parent of this phaser, or {@code null} if none.
     *
     * @return the parent of this phaser, or {@code null} if none
     */
    public Phaser getParent() {
        return parent;
    }

    /**
     * Returns the root ancestor of this phaser, which is the same as
     * this phaser if it has no parent.
     *
     * @return the root ancestor of this phaser
     */
    public Phaser getRoot() {
        return root;
    }

    /**
     * Returns {@code true} if this barrier has been terminated.
     *
     * @return {@code true} if this barrier has been terminated
     */
    public boolean isTerminated() {
        return getPhase() < 0;
    }

    /**
     * Overridable method to perform an action upon impending phase
     * advance, and to control termination. This method is invoked
     * upon arrival of the party tripping the barrier (when all other
     * waiting parties are dormant).  If this method returns {@code
     * true}, then, rather than advance the phase number, this barrier
     * will be set to a final termination state, and subsequent calls
     * to {@link #isTerminated} will return true. Any (unchecked)
     * Exception or Error thrown by an invocation of this method is
     * propagated to the party attempting to trip the barrier, in
     * which case no advance occurs.
     *
     * <p>The arguments to this method provide the state of the phaser
     * prevailing for the current transition.  The results and effects
     * of invoking phase-related methods (including {@code getPhase}
     * as well as arrival, registration, and waiting methods) from
     * within {@code onAdvance} are unspecified and should not be
     * relied on. Similarly, while it is possible to override this
     * method to produce side-effects visible to participating tasks,
     * it is in general safe to do so only in designs in which all
     * parties register before any arrive, and all {@link
     * #awaitAdvance} at each phase.
     *
     * <p>The default version returns {@code true} when the number of
     * registered parties is zero. Normally, overrides that arrange
     * termination for other reasons should also preserve this
     * property.
     *
     * @param phase the phase number on entering the barrier
     * @param registeredParties the current number of registered parties
     * @return {@code true} if this barrier should terminate
     */
    protected boolean onAdvance(int phase, int registeredParties) {
        return registeredParties <= 0;
    }

    /**
     * Returns a string identifying this phaser, as well as its
     * state.  The state, in brackets, includes the String {@code
     * "phase = "} followed by the phase number, {@code "parties = "}
     * followed by the number of registered parties, and {@code
     * "arrived = "} followed by the number of arrived parties.
     *
     * @return a string identifying this barrier, as well as its state
     */
    public String toString() {
        long s = getReconciledState();
        return super.toString() +
            "[phase = " + phaseOf(s) +
            " parties = " + partiesOf(s) +
            " arrived = " + arrivedOf(s) + "]";
    }

    // methods for waiting

    /**
     * Wait nodes for Treiber stack representing wait queue
     */
    static final class QNode implements ForkJoinPool.ManagedBlocker {
        final Phaser phaser;
        final int phase;
        final long startTime;
        final long nanos;
        final boolean timed;
        final boolean interruptible;
        volatile boolean wasInterrupted = false;
        volatile Thread thread; // nulled to cancel wait
        QNode next;

        QNode(Phaser phaser, int phase, boolean interruptible,
              boolean timed, long startTime, long nanos) {
            this.phaser = phaser;
            this.phase = phase;
            this.timed = timed;
            this.interruptible = interruptible;
            this.startTime = startTime;
            this.nanos = nanos;
            thread = Thread.currentThread();
        }

        public boolean isReleasable() {
            return (thread == null ||
                    phaser.getPhase() != phase ||
                    (interruptible && wasInterrupted) ||
                    (timed && (nanos - (System.nanoTime() - startTime)) <= 0));
        }

        public boolean block() {
            if (Thread.interrupted()) {
                wasInterrupted = true;
                if (interruptible)
                    return true;
            }
            if (!timed)
                LockSupport.park(this);
            else {
                long waitTime = nanos - (System.nanoTime() - startTime);
                if (waitTime <= 0)
                    return true;
                LockSupport.parkNanos(this, waitTime);
            }
            return isReleasable();
        }

        void signal() {
            Thread t = thread;
            if (t != null) {
                thread = null;
                LockSupport.unpark(t);
            }
        }

        boolean doWait() {
            if (thread != null) {
                try {
                    ForkJoinPool.managedBlock(this);
                } catch (InterruptedException ie) {
                    wasInterrupted = true; // can't currently happen
                }
            }
            return wasInterrupted;
        }
    }

    /**
     * Removes and signals waiting threads from wait queue.
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
     * Tries to enqueue given node in the appropriate wait queue.
     *
     * @return true if successful
     */
    private boolean tryEnqueue(QNode node) {
        AtomicReference<QNode> head = queueFor(node.phase);
        return head.compareAndSet(node.next = head.get(), node);
    }

    /**
     * The number of times to spin before blocking waiting for advance.
     */
    static final int MAX_SPINS =
        Runtime.getRuntime().availableProcessors() == 1 ? 0 : 1 << 8;

    /**
     * Enqueues node and waits unless aborted or signalled.
     *
     * @return current phase
     */
    private int untimedWait(int phase) {
        QNode node = null;
        boolean queued = false;
        boolean interrupted = false;
        int spins = MAX_SPINS;
        int p;
        while ((p = getPhase()) == phase) {
            if (Thread.interrupted())
                interrupted = true;
            else if (spins > 0) {
                if (--spins == 0)
                    Thread.yield();
            }
            else if (node == null)
                node = new QNode(this, phase, false, false, 0, 0);
            else if (!queued)
                queued = tryEnqueue(node);
            else if (node.doWait())
                interrupted = true;
        }
        if (node != null)
            node.thread = null;
        releaseWaiters(phase);
        if (interrupted)
            Thread.currentThread().interrupt();
        return p;
    }

    /**
     * Interruptible version
     * @return current phase
     */
    private int interruptibleWait(int phase) throws InterruptedException {
        QNode node = null;
        boolean queued = false;
        boolean interrupted = false;
        int spins = MAX_SPINS;
        int p;
        while ((p = getPhase()) == phase && !interrupted) {
            if (Thread.interrupted())
                interrupted = true;
            else if (spins > 0) {
                if (--spins == 0)
                    Thread.yield();
            }
            else if (node == null)
                node = new QNode(this, phase, true, false, 0, 0);
            else if (!queued)
                queued = tryEnqueue(node);
            else if (node.doWait())
                interrupted = true;
        }
        if (node != null)
            node.thread = null;
        if (p != phase || (p = getPhase()) != phase)
            releaseWaiters(phase);
        if (interrupted)
            throw new InterruptedException();
        return p;
    }

    /**
     * Timeout version.
     * @return current phase
     */
    private int timedWait(int phase, long nanos)
        throws InterruptedException, TimeoutException {
        long startTime = System.nanoTime();
        QNode node = null;
        boolean queued = false;
        boolean interrupted = false;
        int spins = MAX_SPINS;
        int p;
        while ((p = getPhase()) == phase && !interrupted) {
            if (Thread.interrupted())
                interrupted = true;
            else if (nanos - (System.nanoTime() - startTime) <= 0)
                break;
            else if (spins > 0) {
                if (--spins == 0)
                    Thread.yield();
            }
            else if (node == null)
                node = new QNode(this, phase, true, true, startTime, nanos);
            else if (!queued)
                queued = tryEnqueue(node);
            else if (node.doWait())
                interrupted = true;
        }
        if (node != null)
            node.thread = null;
        if (p != phase || (p = getPhase()) != phase)
            releaseWaiters(phase);
        if (interrupted)
            throw new InterruptedException();
        if (p == phase)
            throw new TimeoutException();
        return p;
    }

    // Unsafe mechanics

    private static final sun.misc.Unsafe UNSAFE = sun.misc.Unsafe.getUnsafe();
    private static final long stateOffset =
        objectFieldOffset("state", Phaser.class);

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
}
