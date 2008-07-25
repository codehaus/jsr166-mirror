/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y;
import jsr166y.forkjoin.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;

/**
 * A reusable synchronization barrier, similar in functionality to a
 * {@link java.util.concurrent.CyclicBarrier}, but supporting more
 * flexible usage.
 *
 * <ul>
 *
 * <li> The number of parties synchronizing on the barrier may vary
 * over time.  A task may register to be a party in a barrier at any
 * time, and may deregister upon arriving at the barrier.  As is the
 * case with most basic synchronization constructs, registration
 * and deregistration affect only internal counts; they do not
 * establish any further internal bookkeeping, so tasks cannot query
 * whether they are registered.
 *
 * <li> Each generation has an associated phase value, starting at
 * zero, and advancing when all parties reach the barrier (wrapping
 * around to zero after reaching <tt>Integer.MAX_VALUE</tt>).
 * 
 * <li> Like a CyclicBarrier, a Phaser may be repeatedly awaited.
 * Method <tt>arriveAndAwaitAdvance</tt> has effect analogous to
 * <tt>CyclicBarrier.await</tt>.  However, Phasers separate two
 * aspects of coordination, that may be invoked independently:
 *
 * <ul>
 *
 *   <li> Arriving at a barrier. Methods <tt>arrive</tt> and
 *       <tt>arriveAndDeregister</tt> do not block, but return 
 *       the phase value on entry to the method.
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
 *
 * <li> Phasers may enter a <em>termination</em> state in which all
 * await actions immediately return, indicating (via a negative phase
 * value) that execution is complete.  Termination is triggered by
 * executing the overridable <tt>onAdvance</tt> method that is invoked
 * each time the barrier is tripped. When a Phaser is controlling an
 * action with a fixed number of iterations, it is often convenient to
 * override this method to cause termination when the current phase
 * number reaches a threshold.  Method <tt>forceTermination</tt> is
 * also available to assist recovery actions upon failure.
 *
 * <li> Unlike most synchronizers, a Phaser may also be used with 
 * ForkJoinTasks (as well as plain threads).
 * 
 * <li> By default, <tt>awaitAdvance</tt> continues to wait even if
 * the current thread is interrupted. And unlike the case in
 * CyclicBarriers, exceptions encountered while tasks wait
 * interruptibly or with timeout do not change the state of the
 * barrier. If necessary, you can perform any associated recovery
 * within handlers of those exceptions.
 *
 * </ul>
 *
 * <p><b>Sample usage:</b> 
 *
 * <p>[todo: non-FJ example]
 *
 * <p> A Phaser may be used to support a style of programming in
 * which a task waits for others to complete, without otherwise
 * needing to keep track of which tasks it is waiting for. This is
 * similar to the "sync" construct in Cilk and "clocks" in X10.
 * Special constructions based on such barriers are available using
 * the <tt>LinkedAsyncAction</tt> and <tt>CyclicAction</tt> classes,
 * but they can be useful in other contexts as well.  For a simple
 * (but not very useful) example, here is a variant of Fibonacci:
 *
 * <pre>
 * class BarrierFibonacci extends RecursiveAction {
 *   int argument, result;
 *   final Phaser parentBarrier;
 *   BarrierFibonacci(int n, Phaser parentBarrier) {
 *     this.argument = n;
 *     this.parentBarrier = parentBarrier;
 *     parentBarrier.register();
 *   }
 *   protected void compute() {
 *     int n = argument;
 *     if (n &lt;= 1)
 *        result = n;
 *     else {
 *        Phaser childBarrier = new Phaser(1);
 *        BarrierFibonacci f1 = new BarrierFibonacci(n - 1, childBarrier);
 *        BarrierFibonacci f2 = new BarrierFibonacci(n - 2, childBarrier);
 *        f1.fork();
 *        f2.fork();
 *        childBarrier.arriveAndAwait();
 *        result = f1.result + f2.result;
 *     }
 *     parentBarrier.arriveAndDeregister();
 *   }
 * }
 * </pre>
 *
 * <p><b>Implementation notes</b>: This implementation restricts the
 * maximum number of parties to 65535. Attempts to register
 * additional parties result in IllegalStateExceptions.  
 */
public class Phaser {
    /*
     * This class implements an extension of X10 "clocks".  Thanks to
     * Vijay Saraswat for the idea of applying it to ForkJoinTasks,
     * and to Vivek Sarkar for enhancements to extend functionality.
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
     * packed into a single AtomicLong. Termination uses the sign bit
     * of 32 bit representation of phase, so phase is set to -1 on
     * termination.
     */
    private final AtomicLong state;

    /**
     * Head of Treiber stack for waiting nonFJ threads.
     */
    private final AtomicReference<QNode> head = new AtomicReference<QNode>();

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

    private static IllegalStateException badBounds(int parties, int unarrived) {
        return new IllegalStateException("Attempt to set " + unarrived +
                                         " unarrived of " + parties + " parties");
    }

    /**
     * Creates a new Phaser without any initially registered parties,
     * and initial phase number 0.
     */
    public Phaser() {
        state = new AtomicLong(stateFor(0, 0, 0));
    }

    /**
     * Creates a new Phaser with the given numbers of registered
     * unarrived parties and initial phase number 0.
     * @param parties the number of parties required to trip barrier.
     * @throws IllegalArgumentException if parties less than zero
     * or greater than the maximum number of parties supported.
     */
    public Phaser(int parties) {
        if (parties < 0 || parties > ushortMask)
            throw new IllegalArgumentException("Illegal number of parties");
        state = new AtomicLong(stateFor(0, parties, parties));
    }

    /**
     * Adds a new unarrived party to this phaser.
     * @return the current barrier phase number upon registration
     * @throws IllegalStateException if attempting to register more
     * than the maximum supported number of parties.
     */
    public int register() { // increment both parties and unarrived
        final AtomicLong state = this.state;
        for (;;) {
            long s = state.get();
            int phase = phaseOf(s);
            int parties = partiesOf(s) + 1;
            int unarrived = unarrivedOf(s) + 1;
            if (parties > ushortMask || unarrived > ushortMask)
                throw badBounds(parties, unarrived);
            if (state.compareAndSet(s, stateFor(phase, parties, unarrived)))
                return phase;
        }
    }

    /**
     * Arrives at the barrier, but does not wait for others.  (You can
     * in turn wait for others via {@link #awaitAdvance}).
     *
     * @return the current barrier phase number upon entry to
     * this method, or a negative value if terminated;
     * @throws IllegalStateException if the number of unarrived
     * parties would become negative.
     */
    public int arrive() { // decrement unarrived. If zero, trip
        final AtomicLong state = this.state;
        for (;;) {
            long s = state.get();
            int phase = phaseOf(s);
            int parties = partiesOf(s);
            int unarrived = unarrivedOf(s) - 1;
            if (unarrived < 0)
                throw badBounds(parties, unarrived);
            if (unarrived == 0 && phase >= 0) {
                trip(phase, parties);
                return phase;
            }
            if (state.compareAndSet(s, stateFor(phase, parties, unarrived)))
                return phase;
        }
    }

    /**
     * Arrives at the barrier, and deregisters from it, without
     * waiting for others.
     *
     * @return the current barrier phase number upon entry to
     * this method, or a negative value if terminated;
     * @throws IllegalStateException if the number of registered or
     * unarrived parties would become negative.
     */
    public int arriveAndDeregister() { // Same as arrive, plus decrement parties
        final AtomicLong state = this.state;
        for (;;) {
            long s = state.get();
            int phase = phaseOf(s);
            int parties = partiesOf(s) - 1;
            int unarrived = unarrivedOf(s) - 1;
            if (parties < 0 || unarrived < 0)
                throw badBounds(parties, unarrived);
            if (unarrived == 0 && phase >= 0) {
                trip(phase, parties);
                return phase;
            }
            if (state.compareAndSet(s, stateFor(phase, parties, unarrived)))
                return phase;
        }
    }

    /**
     * Arrives at the barrier and awaits others. Unlike other arrival
     * methods, this method returns the arrival index of the
     * caller. The caller tripping the barrier returns zero, the
     * previous caller 1, and so on.
     * @return the arrival index
     * @throws IllegalStateException if the number of unarrived
     * parties would become negative.
     */
    public int arriveAndAwaitAdvance() {
        final AtomicLong state = this.state;
        for (;;) {
            long s = state.get();
            int phase = phaseOf(s);
            int parties = partiesOf(s);
            int unarrived = unarrivedOf(s) - 1;
            if (unarrived < 0)
                throw badBounds(parties, unarrived);
            if (unarrived == 0 && phase >= 0) {
                trip(phase, parties);
                return 0;
            }
            if (state.compareAndSet(s, stateFor(phase, parties, unarrived))) {
                awaitAdvance(phase);
                return unarrived;
            }
        }
    }

    /**
     * Awaits the phase of the barrier to advance from the given
     * value, or returns immediately if this barrier is terminated.
     * @param phase the phase on entry to this method
     * @return the phase on exit from this method
     */
    public int awaitAdvance(int phase) {
        if (phase < 0)
            return phase;
        Thread current = Thread.currentThread();
        if (current instanceof ForkJoinWorkerThread) 
            return helpingWait(phase);
        if (untimedWait(current, phase, false))
            current.interrupt();
        return phaseOf(state.get());
    }

    /**
     * Awaits the phase of the barrier to advance from the given
     * value, or returns immediately if this barrier is terminated, or
     * throws InterruptedException if interrupted while waiting.
     * @param phase the phase on entry to this method
     * @return the phase on exit from this method
     * @throws InterruptedException if thread interrupted while waiting
     */
    public int awaitAdvanceInterruptibly(int phase) throws InterruptedException {
        if (phase < 0)
            return phase;
        Thread current = Thread.currentThread();
        if (current instanceof ForkJoinWorkerThread) 
            return helpingWait(phase);
        else if (Thread.interrupted() || untimedWait(current, phase, true))
            throw new InterruptedException();
        else
            return phaseOf(state.get());
    }

    /**
     * Awaits the phase of the barrier to advance from the given value
     * or the given timeout elapses, or returns immediately if this
     * barrier is terminated.
     * @param phase the phase on entry to this method
     * @return the phase on exit from this method
     * @throws InterruptedException if thread interrupted while waiting
     * @throws TimeoutException if timed out while waiting
     */
    public int awaitAdvanceInterruptibly(int phase, long timeout, TimeUnit unit) 
        throws InterruptedException, TimeoutException {
        if (phase < 0)
            return phase;
        long nanos = unit.toNanos(timeout);
        Thread current = Thread.currentThread();
        if (current instanceof ForkJoinWorkerThread) 
            return timedHelpingWait(phase, nanos);
        timedWait(current, phase, nanos);
        return phaseOf(state.get());
    }

    /**
     * Forces this barrier to enter termination state. Counts of
     * arrived and registered parties are unaffected. This method may
     * be useful for coordinating recovery after one or more tasks
     * encounter unexpected exceptions.
     */
    public void forceTermination() {
        final AtomicLong state = this.state;
        for (;;) {
            long s = state.get();
            int phase = phaseOf(s);
            int parties = partiesOf(s);
            int unarrived = unarrivedOf(s);
            if (phase < 0 ||
                state.compareAndSet(s, stateFor(-1, parties, unarrived))) {
                if (head.get() != null)
                    releaseWaiters(-1);
                return;
            }
        }
    }

    /**
     * Resets the barrier with the given numbers of registered unarrived
     * parties and phase number 0. This method allows repeated reuse
     * of this barrier, but only if it is somehow known not to be in
     * use for other purposes.
     * @param parties the number of parties required to trip barrier.
     * @throws IllegalArgumentException if parties less than zero
     * or greater than the maximum number of parties supported.
     */
    public void reset(int parties) {
        if (parties < 0 || parties > ushortMask)
            throw new IllegalArgumentException("Illegal number of parties");
        state.set(stateFor(0, parties, parties));
        if (head.get() != null)
            releaseWaiters(0);
    }

    /**
     * Returns the current phase number. The maximum phase number is
     * <tt>Integer.MAX_VALUE</tt>, after which it restarts at
     * zero. Upon termination, the phase number is negative.
     * @return the phase number, or a negative value if terminated
     */
    public int getPhase() {
        return phaseOf(state.get());
    }

    /**
     * Returns the number of parties registered at this barrier.
     * @return the number of parties
     */
    public int getRegisteredParties() {
        return partiesOf(state.get());
    }

    /**
     * Returns the number of parties that have arrived at the current
     * phase of this barrier.
     * @return the number of arrived parties
     */
    public int getArrivedParties() {
        return arrivedOf(state.get());
    }

    /**
     * Returns the number of registered parties that have not yet
     * arrived at the current phase of this barrier.
     * @return the number of unarrived parties
     */
    public int getUnarrivedParties() {
        return unarrivedOf(state.get());
    }

    /**
     * Returns true if this barrier has been terminated.
     * @return true if this barrier has been terminated
     */
    public boolean isTerminated() {
        return phaseOf(state.get()) < 0;
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
     * @param phase the phase number on entering the barrier
     * @param registeredParties the current number of registered
     * parties.
     * @return true if this barrier should terminate
     */
    protected boolean onAdvance(int phase, int registeredParties) {
        return registeredParties <= 0;
    }

    /**
     * Returns a string identifying this barrier, as well as its
     * state.  The state, in brackets, includes the String {@code
     * "phase ="} followed by the phase number, {@code "parties ="}
     * followed by the number of registered parties, and {@code
     * "arrived ="} followed by the number of arrived parties
     *
     * @return a string identifying this barrier, as well as its state
     */
    public String toString() {
        long s = state.get();
        return super.toString() + "[phase = " + phaseOf(s) + " parties = " + partiesOf(s) + " arrived = " + arrivedOf(s) + "]";
    }

    // methods for tripping and waiting

    /**
     * Advance the current phase (or terminate)
     */
    private void trip(int phase, int parties) {
        int next = onAdvance(phase, parties)? -1 : ((phase + 1) & phaseMask);
        state.set(stateFor(next, parties, parties));
        if (head.get() != null)
            releaseWaiters(next);
    }

    private int helpingWait(int phase) {
        final AtomicLong state = this.state;
        int p;
        while ((p = phaseOf(state.get())) == phase) {
            ForkJoinTask<?> t = ForkJoinWorkerThread.pollTask();
            if (t != null) {
                if ((p = phaseOf(state.get())) == phase)
                    t.exec();
                else {   // push task and exit if barrier advanced
                    t.fork();
                    break;
                }
            }
        }
        return p;
    }

    private int timedHelpingWait(int phase, long nanos) throws TimeoutException {
        final AtomicLong state = this.state;
        long lastTime = System.nanoTime();
        int p;
        while ((p = phaseOf(state.get())) == phase) {
            long now = System.nanoTime();
            nanos -= now - lastTime;
            lastTime = now;
            if (nanos <= 0) {
                if ((p = phaseOf(state.get())) == phase)
                    throw new TimeoutException();
                else
                    break;
            }
            ForkJoinTask<?> t = ForkJoinWorkerThread.pollTask();
            if (t != null) {
                if ((p = phaseOf(state.get())) == phase)
                    t.exec();
                else {   // push task and exit if barrier advanced
                    t.fork();
                    break;
                }
            }
        }
        return p;
    }

    /**
     * Wait nodes for Treiber stack representing wait queue for non-FJ
     * tasks. The waiting scheme is an adaptation of the one used in
     * forkjoin.PoolBarrier.
     */
    static final class QNode {
        QNode next;
        volatile Thread thread; // nulled to cancel wait
        final int phase;
        QNode(Thread t, int c) {
            thread = t;
            phase = c;
        }
    }

    private void releaseWaiters(int currentPhase) {
        final AtomicReference<QNode> head = this.head;
        QNode p;
        while ((p = head.get()) != null && p.phase != currentPhase) {
            if (head.compareAndSet(p, null)) {
                do {
                    Thread t = p.thread;
                    if (t != null) {
                        p.thread = null;
                        LockSupport.unpark(t);
                    }
                } while ((p = p.next) != null);
            }
        }
    }

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
     * Enqueues node and waits unless aborted or signalled.
     */
    private boolean untimedWait(Thread thread, int currentPhase, 
                               boolean abortOnInterrupt) {
        final AtomicReference<QNode> head = this.head;
        final AtomicLong state = this.state;
        boolean wasInterrupted = false;
        QNode node = null;
        boolean queued = false;
        int spins = maxUntimedSpins;
        while (phaseOf(state.get()) == currentPhase) {
            QNode h;
            if (node != null && queued) {
                if (node.thread != null) {
                    LockSupport.park();
                    if (Thread.interrupted()) {
                        wasInterrupted = true;
                        if (abortOnInterrupt) 
                            break;
                    }
                }
            }
            else if ((h = head.get()) != null && h.phase != currentPhase) {
                if (phaseOf(state.get()) == currentPhase) { // must recheck
                    if (head.compareAndSet(h, h.next)) {
                        Thread t = h.thread; // help clear out old waiters
                        if (t != null) {
                            h.thread = null;
                            LockSupport.unpark(t);
                        }
                    }
                }
                else
                    break;
            }
            else if (node != null)
                queued = head.compareAndSet(node.next = h, node);
            else if (spins <= 0)
                node = new QNode(thread, currentPhase);
            else
                --spins;
        }
        if (node != null)
            node.thread = null;
        return wasInterrupted;
    }

    /**
     * Messier timeout version
     */
    private void timedWait(Thread thread, int currentPhase, long nanos) 
        throws InterruptedException, TimeoutException {
        final AtomicReference<QNode> head = this.head;
        final AtomicLong state = this.state;
        long lastTime = System.nanoTime();
        QNode node = null;
        boolean queued = false;
        int spins = maxTimedSpins;
        while (phaseOf(state.get()) == currentPhase) {
            QNode h;
            long now = System.nanoTime();
            nanos -= now - lastTime;
            lastTime = now;
            if (nanos <= 0) {
                if (node != null)
                    node.thread = null;
                if (phaseOf(state.get()) == currentPhase)
                    throw new TimeoutException();
                else
                    break;
            }
            else if (node != null && queued) {
                if (node.thread != null &&
                    nanos > spinForTimeoutThreshold) {
                    //                LockSupport.parkNanos(this, nanos);
                    LockSupport.parkNanos(nanos);
                    if (Thread.interrupted()) {
                        node.thread = null;
                        throw new InterruptedException();
                    }
                }
            }
            else if ((h = head.get()) != null && h.phase != currentPhase) {
                if (phaseOf(state.get()) == currentPhase) { // must recheck
                    if (head.compareAndSet(h, h.next)) {
                        Thread t = h.thread; // help clear out old waiters
                        if (t != null) {
                            h.thread = null;
                            LockSupport.unpark(t);
                        }
                    }
                }
                else
                    break;
            }
            else if (node != null)
                queued = head.compareAndSet(node.next = h, node);
            else if (spins <= 0)
                node = new QNode(thread, currentPhase);
            else
                --spins;
        }
        if (node != null)
            node.thread = null;
    }

}

