/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent.locks;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Provides a framework for creating blocking locks and related
 * synchronization aids that relying on First-in-first-out wait
 * queues.  This class is designed to be a suitable superclass for
 * most kinds of synchronizers that rely on a single atomic
 * <tt>int</tt> value to represent status. Subclasses must define the
 * methods that change this status.  Given these, the other methods in
 * this class carry out all queuing using an internal specialized FIFO
 * queue. Implementation classes can maintain other fields, but only
 * the {@link AtomicInteger} provided by {@link #getState} is tracked
 * with respect to synchronization mechanics.
 *
 * <p> Generally, subclasses of this class should be defined as
 * non-public internal helper classes used to implement
 * synchronization needed inside other classes.  This class does not
 * implement any synchronization interface.  Instead it defines
 * methods <tt>acquireExclusiveUninterruptibly</tt> and so on that can
 * be invoked as appropriate by concrete locks and related
 * synchronizers to implement their public methods. (Note that this
 * class does not directly provide untimed "trylock" forms, since the
 * state acquire methods can be used for these purposes.)  This class
 * also provides instrumentation and monitoring methods such as {@link
 * #hasWaiters}.
 *
 * <p> This class supports either or both <em>exclusive</em> and
 * <em>shared</em> modes. When acquired in exclusive mode, it cannot
 * be acquired by another thread. Shared modes may (but need not be)
 * held by multiple threads. This class does not "understand" these
 * differences except in the mechanical sense that when a shared mode
 * acquire succeeds, the next waiting thread (if one exists) must also
 * determine whether it can acquire as well. Implementations that
 * support only exclusive or only shared modes should define the
 * abstract methods for the unused mode to throw {@link
 * UnsupportedOperationException}.
 *
 * <p> This class defines a nested {@link Condition} class that can be
 * used with subclasses for which method <tt>releaseExclusive</tt>
 * invoked with the current state value fully releases the lock, and
 * <tt>acquireExclusive</tt>, given this saved state value, restores
 * the lock to its previous lock state. No method creates such a
 * condition, so if this constraint cannot be met, do not use it.
 * 
 * <p> Serialization of this class serializes only the atomic integer
 * maintaining state. Typical subclasses requiring seializability will
 * define a <tt>readObject</tt> method that restores this to a known
 * initial state upon deserialization.
 *
 * <p> To extend this class for use as a synchronization implementation,
 * implement the following five methods, throwing
 * {@link UnsupportedOperationException} for those that will not
 * be used:
 * <ul>
 * <li> {@link #acquireExclusiveState}
 * <li> {@link #releaseExclusiveState}
 * <li> {@link #acquireSharedState}
 * <li> {@link #releaseSharedState}
 * <li> {@link #checkConditionAccess}
 *</ul>
 *
 * <p>
 * <b>Extension Example.</b> Here is a fair mutual exclusion lock class:
 * <pre>
 * class FairMutex implements Lock {
 *    private static class Sync extends AbstractQueuedSynchronizer {
 *       // Uses 0 for unlocked, 1 for locked state
 *
 *       public int acquireExclusiveState(boolean isQueued, int acquires, 
 *                                        Thread ignore) {
 *           assert acquires == 1; // Does not use multiple acquires
 *           if ((isQueued || !hasWaiters()) &amp;&amp;
 *                getState().compareAndSet(0, 1))
 *               return 0;
 *           return -1;
 *       }
 *
 *       public boolean releaseExclusiveState(int releases) {
 *           getState().set(0);
 *           return true;
 *       }
 *       
 *       public int acquireSharedState(boolean isQueued, int acquires, 
 *                                        Thread current) {
 *           throw new UnsupportedOperationException();
 *       }
 *
 *       public boolean releaseSharedState(int releases) {
 *           throw new UnsupportedOperationException();
 *       }
 *
 *       public void checkConditionAccess(Thread thread, boolean waiting) {
 *           if (getState().get() == 0)
 *               throw new IllegalMonitorException();
 *       }
 *       Condition newCondition() { return new ConditionObject(); }
 *    }
 *    private final Sync sync = new Sync();
 *    public void lock() { sync.acquireExclusiveUninterruptibly(1); }
 *    public void unlock() { sync.releaseExclusive(1); }
 *    public boolean tryLock() { return sync.acquireExclusiveState(false, 1, null); }
 *    public Condition newCondition() { return sync.newCondition(); }
 *    // ... and so on for other lock methods
 * }
 * </pre>
 *
 * @since 1.5
 * @author Doug Lea
 * 
 */
public abstract class AbstractQueuedSynchronizer implements java.io.Serializable {
    /*
     *  General description and notes.
     *
     *  The basic idea, ignoring all sorts of things
     *  (modes, cancellation, timeouts, error checking etc) is:
     *    acquire:
     *      if (atomically set status) // fastpath
     *        return;
     *      node = create and enq a wait node;
     *      for (;;) {
     *        if (node is first on queue) {
     *          if (atomically set status) 
     *             deq(node);
     *             return;
     *           }
     *        }
     *        park(currentThread);
     *      }
     *
     *    release:
     *      atomically lockStatus;
     *      if (is now fully released) {
     *         h = first node on queue;
     *         if (h != null) unpark(h's successor's thread);
     *      }
     *
     *  * The particular atomic actions needed to atomically lock and
     *    unlock can vary in subclasses.
     *
     *  * By default, contended acquires use a kind of "greedy" /
     *    "renouncement" / barging / convoy-avoidance strategy:
     *    When a lock is released, a waiting thread is signalled
     *    so that it can (re)contend for the lock. It might lose
     *    and thus need to rewait. This strategy has much higher
     *    throughput than "directed handoff" because it reduces
     *    blocking of running threads, but poorer fairness.  The
     *    wait queue is FIFO, but newly entering threads can barge
     *    ahead and grab lock before woken waiters, so acquires
     *    are not strictly FIFO, and transfer is not
     *    deterministically fair. It is probabilistically fair in
     *    the sense that earlier queued threads are allowed to
     *    recontend before later queued threads, and each
     *    recontention has an unbiased chance to succeed against
     *    any incoming barging threads.
     *
     * * "Fair" variants differ only in that they can disable barging
     *    in the fast path if there is contention (see the "isQueued"
     *    argument to state methods). There can be races in detecting
     *    contention, but it is still FIFO from a definable (although
     *    complicated to describe) single point, so qualifies as a
     *    FIFO lock.
     *
     * * While acquires never "spin" in the usual sense, they perform
     *    multiple test-and-test-and sets interspersed with other
     *    computations before blocking.  This gives most of the
     *    benefits of spins when they are only briefly held without
     *    most of the liabilities when they aren't.
     *
     * * The wait queue is a variant of a "CLH" (Craig, Landin, and
     *    Hagersten) lock. CLH locks are normally used for spinlocks.
     *    We instead use them for blocking synchronizers, but use the
     *    same basic tactic of holding some of the control information
     *    about a thread in the predecessor of its node.  A "status"
     *    field in each node keeps track of whether a thread is/should
     *    block.  A node is signalled when its predecessor releases
     *    the lock. Each node of the queue otherwise serves as a
     *    specific-notification-style monitor holding a single waiting
     *    thread. The status field does NOT control whether threads
     *    are granted locks though.  A thread may try to acquire lock
     *    if it is first in the queue. But being first does not
     *    guarantee success; it only gives the right to contend.  So
     *    the currently released contender thread may need to rewait.
     *
     *    To enqueue into a CLH lock, you atomically splice it in as new
     *    tail. To dequeue, you just set the head field.  
     *    
     *         +------+  prev +-----+       +-----+
     *    head |      | <---- |     | <---- |     |  tail
     *         +------+       +-----+       +-----+
     *
     *    Insertion into a CLH queue requires only a single atomic
     *    operation on "tail", so there is a simple atomic point of
     *    demarcation from unqueued to queued. Similarly, dequeing
     *    involves only updating the "head". However, it takes a bit
     *    more work for nodes to determine who their successors are,
     *    in part to deal with possible cancellation due to timeouts
     *    and interrupts.
     *
     *    The "prev" links (not used in original CLH locks), are mainly
     *    needed to handle cancellation. If a node is cancelled, its
     *    successor is (normally) relinked to a non-cancelled
     *    predecessor. For explanation of similar mechanics in the case
     *    of spin locks, see the papers by Scott & Scherer at
     *    http://www.cs.rochester.edu/u/scott/synchronization/
     *    
     *    We also use "next" links to implement blocking mechanics.
     *    The thread id for each node is kept in its node, so a
     *    predecessor signals the next node to wake up by traversing
     *    next link to determine which thread it is.  Determination of
     *    successor must avoid races with newly queued nodes to set
     *    the "next" fields of their predecessors.  This is solved
     *    when necessary by checking backwards from the atomically
     *    updated "tail" when a node's successor appears to be null.
     *    (Or, said differently, the next-links are an optimization
     *    so that we don't usually need a backward scan.)
     *
     *    Cancellation introduces some conservatism to the basic
     *    algorithms.  Since we must poll for cancellation of other
     *    nodes, we can miss noticing whether a cancelled node is
     *    ahead or behind us. This is dealt with by always unparking
     *    successors upon cancellation, allowing them to stabilize on
     *    a new predecessor.
     *  
     *  * CLH queues need a dummy header node to get started. But
     *    we don't create them on construction, because it would be wasted
     *    effort if there never contention. Instead, the node
     *    is constructed and head and tail pointers are set upon first
     *    contention.
     *
     *  * Threads waiting on Conditions use the same nodes, but
     *    use an additional link. Conditions only need to link nodes
     *    in simple (non-concurrent) linked queues because they are
     *    only accessed when exclusively held.  Upon await, a node is
     *    inserted into a condition queue.  Upon signal, the node is
     *    transferred to the main queue.  A special value of status
     *    field is used to mark which queue a node is on.
     *
     *  * All suspension and resumption of threads uses the JSR166
     *    native park/unpark API. These are safe versions of
     *    suspend/resume (plus timeout support) that avoid the intrinsic
     *    race problems with suspend/resume: Park suspends if not
     *    preceded by an unpark. Unpark resumes if waiting, else causes
     *    next park not to suspend.
     *
     *  * Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     *    Scherer and Michael Scott, along with members of JSR-166
     *    expert group, for helpful ideas, discussions, and critiques.
     */

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Node class for threads waiting for synch or conditions.  
     */
    static final class Node {
        /** status value to indicate thread has cancelled */
        static final int CANCELLED =  1;
        /** status value to indicate thread needs unparking */
        static final int SIGNAL    = -1;
        /** status value to indicate thread is waiting on condition */
        static final int CONDITION = -2;

        /**
         * Status field, taking on only the values:
         *   SIGNAL:     The successor of this node is (or will soon be) 
         *               blocked (via park), so the current node must 
         *               unpark its successor when it releases or 
         *               cancels.
         *   CANCELLED:  Node is cancelled due to timeout or interrupt
         *               Nodes never leave this state. In particular,
         *               a thread with cancelled node never again blocks.
         *   CONDITION:  Node is currently on a condition queue
         *               It will not be used as a sync queue node until
         *               transferred. (Use of this value here
         *               has nothing to do with the other uses
         *               of the field, but simplifies mechanics.)
         *   0:          None of the above
         *
         * The values are arranged numerically to simplify use.
         * Non-negative values mean that a node doesn't need to
         * signal. So, some code doesn't need to check for particular
         * values, just for sign.
         *
         * The field is initialized to 0 for normal sync nodes, and
         * CONDITION for condition nodes.  It is modified only using
         * CAS, except for transitions to CANCELLED, which are
         * unconditionally, "finally" assigned.
         */
        volatile int status;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking status. Assigned during enqueing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of successful acquire. A
         * cancelled thread never succeeds in acquiring, and a thread only
         * cancels itself, not any other node.
         */
        volatile Node prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon release. Assigned once during enqueuing,
         * and nulled out (for sake of GC) when no longer needed.
         * Upon cancellation, we do NOT adjust this field, but simply
         * traverse through next's until we hit a non-cancelled node,
         * (or null if at end of queue).  The enq operation does not
         * assign next field of a predecessor until after attachment,
         * so seeing a null next field does not necessarily mean that
         * node is at end of queue. However, if a next field appears
         * to be null, we can scan prev's from the tail to
         * double-check.
         *
         */
        volatile Node next;

        /**
         * True if waiting in shared mode.  If so, successful acquires
         * cascade to wake up subsequent nodes.
         */
        final boolean shared;
        
        /** 
         * Link to next node waiting on condition.  Because condition
         * queues are accessed only when holding in exclusive mode, we
         * just need a simple linked queue to hold nodes while they
         * are waiting on conditions. They are then transferred to the
         * queue to re-acquire.
         */
        Node nextWaiter;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.  Note that this need
         * not be declared volatile since it is always accessed after
         * traversing volatile links, and written before writing
         * links.
         */
        Thread thread;

        Node(Thread thread) { 
            this.thread = thread; 
            this.shared = false;
        }

        Node(Thread thread, boolean shared) { 
            this.thread = thread; 
            this.shared = shared;
        }

        Node(Thread thread, boolean shared, int status) { 
            this.thread = thread; 
            this.shared = shared;
            this.status = status;
        }

        /**
         * Updater to provide CAS for status field
         */
        private static final 
            AtomicIntegerFieldUpdater<Node> statusUpdater = 
            AtomicIntegerFieldUpdater.<Node>newUpdater 
            (Node.class, "status");

        /**
         * CAS the status field
         * @param cmp expected value
         * @param val the new value
         * @return true if successful
         */
        final boolean compareAndSetStatus(int cmp, int val) {
            return statusUpdater.compareAndSet(this, cmp, val);
        }
    }

    /** 
     * Status is kept in a separate AtomicInteger.  
     */
    private final AtomicInteger state = new AtomicInteger();

    /** 
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only by a thread upon acquiring.
     * If head exists, its node status is guaranteed not to be
     * CANCELLED.
     */
    private transient volatile Node head; 

    /** 
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile Node tail; 

    /**
     * Updater to provide CAS for tail field
     */
    private static final 
        AtomicReferenceFieldUpdater<AbstractQueuedSynchronizer, Node> tailUpdater = 
        AtomicReferenceFieldUpdater.<AbstractQueuedSynchronizer, Node>newUpdater 
        (AbstractQueuedSynchronizer.class, Node.class, "tail");

    /**
     * Updater to provide CAS for head field
     */
    private static final 
        AtomicReferenceFieldUpdater<AbstractQueuedSynchronizer, Node> headUpdater = 
        AtomicReferenceFieldUpdater.<AbstractQueuedSynchronizer, Node>newUpdater 
        (AbstractQueuedSynchronizer.class,  Node.class, "head");

    /*
     * Mode words are used internally to handle combinations of
     * interruptiblity and timeout.  These are OR'ed together as
     * appropriate for arguments and results.
     */

    /** As arg, don't interrupt; as result, was not interrupted */
    private static final int UNINTERRUPTED =  0;
    /** As arg, allow interrupt; as result, was interrupted */ 
    private static final int INTERRUPT     =  2;
    /** As arg, allow timeouts; as result, did time out */
    private static final int TIMEOUT       =  4;
    /** As arg, reset interrupt status upon return */
    private static final int REINTERRUPT   =  8;

    /**
     * Constructor for use by subclasses
     */
    protected AbstractQueuedSynchronizer() { }

    /**
     * Returns the atomic integer maintaining synchronization status.
     * The returned {@link AtomicInteger} is to be used inside methods
     * that initialize, acquire and release state to effect these
     * state changes.
     * @return the state
     */
    public AtomicInteger getState() {
        return state;
    }

    /**
     * Try to set status for an attempt to acquire in exclusive mode.
     * Failures are presumed to be due to unavailability of the
     * synchronizer.
     * @param isQueued true if the thread has been queued, possibly
     * blocking before this call. If this argument is false,
     * then the thread has not yet been queued. This can be used
     * to help implement a fairness policy
     * @param acquires the number of acquires requested. This value
     * is always the one given in an <tt>acquire</tt> method,
     * or is the value saved on entry to a condition wait.
     * @param current current thread
     * @return negative on failure, zero on success. (These
     * unusual return value conventions match those needed for
     * shared modes.)
     * @throws IllegalMonitorStateException if acquiring would place
     * this synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if exclusive mode not supported
     */
    public abstract int acquireExclusiveState(boolean isQueued, 
                                                 int acquires, 
                                                 Thread current);

    /**
     * Set status to reflect a release in exclusive mode..
     * @param releases the number of releases. This value
     * is always the one given in a <tt>release</tt> method,
     * or the current value upon entry to a condition wait.
     * @return true if now in a fully released state, so that
     * any waiting threads may attempt to acquire.
     * @throws IllegalMonitorStateException if releasing would place
     * this synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if exclusive mode not supported
     */
    public abstract boolean releaseExclusiveState(int releases);

    /**
     * Try to set status for an attempt to acquire in shared mode.
     * Failures are presumed to be due to unavailability of the
     * synchronizer.
     * @param isQueued true if the thread has been queued, possibly
     * blocking before this call. If this argument is false,
     * then the thread has not yet been queued. This can be used
     * to help implement a fairness policy
     * @param acquires the number of acquires requested. This value
     * is always the one given in an <tt>acquire</tt> method.
     * @param current current thread
     * @return negative on failure, zero on exclusive success, and
     * positive if non-exclusively successful, in which case a
     * subsequent waiting thread must check availability.
     * @throws IllegalMonitorStateException if acquiring would place
     * this synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if shared mode not supported
     */
    public abstract int acquireSharedState(boolean isQueued, 
                                           int acquires, 
                                           Thread current);
    /**
     * Set status to reflect a release in shared mode.
     * @param releases the number of releases. This value
     * is always the one given in a <tt>release</tt> method.
     * @return true if now in a fully released state, so that
     * any waiting threads may attempt to acquire.
     * @throws IllegalMonitorStateException if releasing would place
     * this synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if shared mode not supported
     */
    public abstract boolean releaseSharedState(int releases);

    /**
     * Throw IllegalMonitorStateException if given thread should not
     * access a {@link Condition} method. This method is invoked upon
     * each call to a {@link ConditionObject} method.
     * @param thread the thread
     * @param waiting true if the access is for a condition-wait method,
     * and false if any other method (such as <tt>signal</tt>).
     * @throws IllegalMonitorStateException if cannot access
     * @throws UnsupportedOperationException if conditions not supported
     */
    public abstract void checkConditionAccess(Thread thread, 
                                              boolean waiting);

    /**
     * Acquire in exclusive mode, ignoring interrupts.
     * With an argument of 1, this can be used to 
     * implement method {@link Lock#lock}
     * @param acquires the number of times to acquire
     */
    public final void acquireExclusiveUninterruptibly(int acquires) {
        doAcquire(false, acquires, UNINTERRUPTED, 0L);
    }

    /**
     * Acquire in exclusive mode, aborting if interrupted.
     * With an argument of 1, this can be used to 
     * implement method {@link Lock#lockInterruptibly}
     * @param acquires the number of times to acquire
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireExclusiveInterruptibly(int acquires) throws InterruptedException {
       if (doAcquire(false, acquires, INTERRUPT, 0L) == INTERRUPT)
           throw new InterruptedException();
   }

    /**
     * Acquire in exclusive mode, aborting if interrupted or
     * the given timeout elapses.
     * With an argument of 1, this can be used to 
     * implement method {@link Lock#lockInterruptibly}
     * @param acquires the number of times to acquire
     * @param nanos the maximum number of nanoseconds to wait
     * @return true if acquired; false if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
   public boolean acquireExclusiveTimed(int acquires, long nanos) throws InterruptedException {
       int s = doAcquire(false, acquires, INTERRUPT | TIMEOUT, nanos);
       if (s == UNINTERRUPTED)
           return true;
       if (s != INTERRUPT)
           return false;
       throw new InterruptedException();
   }

    /**
     * Release in exclusive mode. With an argument of 1, this method
     * can be used to implement method {@link Lock#unlock}
     * @param releases the number of releases
     */
    public final void releaseExclusive(int releases) {
        if (releaseExclusiveState(releases)) {
            Node h = head;
            if (h != null && h.status < 0)
                unparkSuccessor(h);
        }
    }

    /**
     * Acquire in shared mode, ignoring interrupts.  With an argument
     * of 1, this can be used to implement method {@link Lock#lock}
     * @param acquires the number of times to acquire
     */
    public final void acquireSharedUninterruptibly(int acquires) {
        doAcquire(true, acquires, UNINTERRUPTED, 0L);
    }

    /**
     * Acquire in shared mode, aborting if interrupted.  With an
     * argument of 1, this can be used to implement method {@link
     * Lock#lockInterruptibly}
     * @param acquires the number of times to acquire
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int acquires) throws InterruptedException {
       if (doAcquire(true, acquires, INTERRUPT, 0L) == INTERRUPT)
           throw new InterruptedException();
   }

    /**
     * Acquire in shared mode, aborting if interrupted or the given
     * timeout elapses.  With an argument of 1, this can be used to
     * implement method {@link Lock#lockInterruptibly}
     * @param acquires the number of times to acquire
     * @param nanos the maximum number of nanoseconds to wait
     * @return true if acquired; false if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
   public boolean acquireSharedTimed(int acquires, long nanos) throws InterruptedException {
       int s = doAcquire(true, acquires, INTERRUPT | TIMEOUT, nanos);
       if (s == UNINTERRUPTED)
           return true;
       if (s != INTERRUPT)
           return false;
       throw new InterruptedException();
   }

    /**
     * Release in shared mode. With an argument of 1, this method can
     * be used to implement method {@link Lock#unlock}
     * @param releases the number of releases
     */
    public final void releaseShared(int releases) {
        if (releaseSharedState(releases)) {
            Node h = head;
            if (h != null && h.status < 0)
                unparkSuccessor(h);
        }
    }

    // Queuing utilities

    /**
     * Initialize queue. Called on first contention.
     */
    private void initializeQueue() {
        Node t = tail;
        if (t == null) {         
            Node h = new Node(null);
            while ((t = tail) == null) {     
                if (headUpdater.compareAndSet(this, null, h)) 
                    tail = h;
                else
                    Thread.yield();
            }
        }
    }

    /**
     * Insert node into queue, initializing head and tail if necessary.
     * @param node the node to insert
     */
    private void enq(Node node) {
        Node t = tail;
        if (t == null) {         // Must initialize first
            initializeQueue();
            t = tail;
        }
        for (;;) {
            node.prev = t;      // Prev field must be valid before/upon CAS
            if (tailUpdater.compareAndSet(this, t, node)) {
                t.next = node;  // Next field assignment lags CAS
                return;
            }
            t = tail;
        } 
    }

    /**
     * Wake up node's successor, if one exists.
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        /*
         * Reset status before unparking. This improves performance
         * when called from a releaase method to enable next thread: A
         * given head-node can be in effect across multiple releases
         * that acquired by barging. When they do so, and later
         * release, the successor that lost a previous race and
         * re-parked must be re-unparked. But otherwise, we'd like to
         * minimize unnecessary calls to unpark, which may be
         * relatively expensive. We don't bother to loop on failed CAS
         * here though, since the reset is just for performance.  Note
         * that the CAS will fail when this method is called from
         * cancellation code since status will be set to CANCELLED.
         * This doesn't occur frequently enough to bother avoiding.
         */
        node.compareAndSetStatus(Node.SIGNAL, 0);

        /*
         * Successor is normally just the next node.  But if cancelled
         * or apparently null, traverse backwards from tail to find
         * the actual non-cancelled successor.
         */
        Node s = node.next;
        if ((s != null && s.status != Node.CANCELLED) ||
            (s = findSuccessorFromTail(node)) != null)
            LockSupport.unpark(s.thread);
    }

    /**
     * Find the successor of a node, working backwards from the tail
     * @param node the node
     * @return successor, or null if there isn't one. 
     */
    private Node findSuccessorFromTail(Node node) {
        Node s = tail;
        if (s == null || s == node)
            return null;
        Node p = s.prev;
        for (;;) {
            if (p == null || p == node)
                return s;
            if (p.status != Node.CANCELLED) 
                s = p; 
            p = p.prev;
        }
    }

    /**
     * Cancel an ongoing attempt to acquire.
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        node.thread = null;   
        node.status = Node.CANCELLED;
        unparkSuccessor(node);
    }

    /**
     * Main code for all varieties of acquire.
     * @param shared if acquiring in shared mode
     * @param mode mode representing interrupt/timeout control
     * @param nanos timeout time
     * @return UNINTERRUPTED on success, INTERRUPT on interrupt,
     * TIMEOUT on timeout
     */
    private int doAcquire(boolean shared, int acquires, int mode, long nanos) {
        final Thread current = Thread.currentThread();

        if ((mode & INTERRUPT) != 0 && Thread.interrupted())
            return INTERRUPT;

        int precheck = (shared)?
            acquireSharedState(false, acquires, current):
            acquireExclusiveState(false, acquires, current);
        if (precheck >= 0)
            return UNINTERRUPTED;

        long lastTime = ((mode & TIMEOUT) == 0)? 0 : System.nanoTime();

        final Node node = new Node(current, shared);
        enq(node);

        /*
         * Repeatedly try to set status if first in queue; block
         * (park) on failure.  If we are the first thread in queue, we
         * must try to acquire, and we must not try if we are not
         * first. We can park only if we are sure that some other
         * thread will (eventually) release and signal us.  Along the
         * way, make sure that the predecessor hasn't been
         * cancelled. If it has, relink to its predecessor.  When
         * about to park, first try to set status enabling holder
         * to signal, and then recheck one final time before actually
         * blocking. This also has effect of retrying failed status
         * CAS due to contention.
         */ 

        for (;;) {
            Node p = node.prev; 
            if (p == head) {
                int acq;
                try {
                    acq = shared?
                        acquireSharedState(true, acquires, current):
                        acquireExclusiveState(true, acquires, current);
                } catch (RuntimeException ex) {
                    cancelAcquire(node);
                    throw ex;
                }
                if (acq >= 0) {
                    p.next = null; 
                    node.thread = null;
                    node.prev = null; 
                    head = node;
                    if (acq > 0 && node.status < 0) {
                        Node s = node.next; 
                        if (s == null || s.shared)
                            unparkSuccessor(node);
                    }
                    if ((mode & REINTERRUPT) != 0)
                        current.interrupt();
                    return UNINTERRUPTED;
                }
            }

            int status = p.status;
            if (status == 0) 
                p.compareAndSetStatus(0, Node.SIGNAL);
            else if (status == Node.CANCELLED) 
                node.prev = p.prev;
            else {
                if ((mode & TIMEOUT) != 0) {
                    if (nanos > 0) {
                        long now = System.nanoTime();
                        nanos -= now - lastTime;
                        lastTime = now;
                    }
                    if (nanos <= 0) {
                        cancelAcquire(node);
                        return TIMEOUT;
                    }
                    else
                        LockSupport.parkNanos(nanos);
                }
                else
                    LockSupport.park();
                
                if (Thread.interrupted()) {
                    if ((mode & INTERRUPT) != 0) {
                        cancelAcquire(node);
                        return INTERRUPT;
                    }
                    else
                        mode |= REINTERRUPT;
                }
            }
        }
    }

    /**
     * Re-acquire after a wait, resetting state.  Called only by
     * Condition methods after returning from condition-waits.
     * @param current the waiting thread
     * @param node its node
     * @param savedState the value of state before entering wait
     * @return true if interrupted while re-acquiring
     */
    boolean reacquireExclusive(Thread current, Node node, int savedState) {
        boolean interrupted = false;
        for (;;) {
            Node p = node.prev; 
            if (p == head && 
                acquireExclusiveState(true, savedState, current) >= 0) {
                p.next = null; 
                node.thread = null;
                node.prev = null; 
                head = node;
                return interrupted;
            }
            
            int status = p.status;
            if (status == 0) 
                p.compareAndSetStatus(0, Node.SIGNAL);
            else if (status == Node.CANCELLED) 
                node.prev = p.prev;
            else { 
                LockSupport.park();
                if (Thread.interrupted())
                    interrupted = true;
            }
        }
    }

    /**
     * Fully release. Called only by Condition methods on entry to await.
     * @return the state before unlocking
     */
    int fullyReleaseExclusive() {
        int c = state.get();
        releaseExclusive(c);
        return c;
    }

    // Instrumentation and monitoring methods

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations may occur at any time, a <tt>true</tt>
     * return does not guarantee that any other thread will ever
     * acquire.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @return true if there may be other threads waiting to acquire
     * the lock.
     */
    public final boolean hasWaiters() { 
        return head != tail; 
    }


    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring of the system state, not for synchronization
     * control.
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null && p != head; p = p.prev)
            ++n;
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     * @return the collection of threads
     */
    public Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire with given mode.
     * @param shared true if shared mode, else exclusive
     * @return the collection of threads
     */
    public Collection<Thread> getQueuedThreads(boolean shared) {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.shared == shared) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }


    /**
     * Return true if a node, always one that was initially placed on
     * a condition queue, is now waiting to reacquire on sync queue.
     * @param node the node
     * @return true if is reacquiring
     */
    final boolean isReacquiring(Node node) {
        if (node.status == Node.CONDITION || node.prev == null)
            return false;
        // If node has successor, it must be on queue
        if (node.next != null) 
            return true;
        /*
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         */
        Node t = tail; 
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    } 

    /**
     * Transfer a node from a condition queue onto sync queue. 
     * Return true if successful.
     * @param node the node
     * @return true if succesfully transferred (else the node was
     * cancelled before signal).
     */
    final boolean transferForSignal(Node node) {
        /*
         * If cannot change status, the node has been cancelled.
         */
        if (!node.compareAndSetStatus(Node.CONDITION, 0))
            return false;

        /*
         * Splice onto queue and try to set status of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set status fails, wake up to resynch (in which
         * case the status can be transiently/harmlessly wrong).
         */
        enq(node);
        Node p = node.prev;
        int c = p.status;
        if (c == Node.CANCELLED || 
            !p.compareAndSetStatus(c, Node.SIGNAL))
            LockSupport.unpark(node.thread);

        return true;
    }

    /**
     * Transfer node, if necessary, to sync queue after a cancelled
     * wait. Return true if thread was cancelled before being
     * signalled.
     * @param current the waiting thread
     * @param node its node
     * @return true if cancelled before the node was signalled.
     */
    final boolean transferAfterCancelledWait(Thread current, Node node) {
        if (node.compareAndSetStatus(Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        else {
            /*
             * If we lost out to a signal(), then we can't proceed
             * until it finishes its enq().  Cancelling during an
             * incomplete transfer is both rare and transient, so just
             * spin.
             */
            while (!isReacquiring(node)) 
                Thread.yield();
            return false;
        }
    }

    /**
     * Condition implementation.
     * Instances of this class can be constructed only by subclasses.
     *
     * <p>In addition to implementing the {@link Condition} interface,
     * this class defines public methods <tt>hasWaiters</tt> and
     * <tt>getWaitQueueLength</tt>, as well as <tt>protected
     * getWaitingThreads</tt> methods that may be useful for
     * instrumentation and monitoring.
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;

        /** First node of condition queue. */
        private transient Node firstWaiter;
        /** Last node of condition queue. */
        private transient Node lastWaiter;

        /**
         * Constructor for use by subclasses 
         */
        protected ConditionObject() { }

        // Internal methods

        /**
         * Add a new waiter to wait queue
         * @param current the thread that will be waiting
         * @return its new wait node
         */
        private Node addConditionWaiter(Thread current) {
            Node w = new Node(current, false, Node.CONDITION);
            Node t = lastWaiter;
            if (t == null) 
                firstWaiter = w;
            else 
                t.nextWaiter = w;
            lastWaiter = w;
            return w;
        }

        /**
         * Remove and transfer nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                if ( (firstWaiter = first.nextWaiter) == null) 
                    lastWaiter = null;
                first.nextWaiter = null;
            } while (!transferForSignal(first) &&
                     (first = firstWaiter) != null);
        }

        /**
         * Remove and transfer all nodes.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter  = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        // public methods

        /**
         * Wakes up one waiting thread.
         *
         * <p>If any threads are waiting on this condition then one is
         * selected for waking up.  This implementation always chooses
         * to wake up the longest-waiting thread whose wait has not
         * been interrupted or timed out.  That thread must then
         * re-acquire the lock before it returns. The order in which
         * it will do so is the same as that for threads initially
         * acquiring the lock, which is in the default case not
         * specified, but for <em>fair</em> locks favors those threads
         * that have been waiting the longest. Note that an awakened
         * thread can return, at the soonest, only after the current
         * thread releases the lock associated with this Condition.
         * 
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         **/
        public void signal() {
            checkConditionAccess(Thread.currentThread(), false);
            Node w = firstWaiter;
            if (w != null)
                doSignal(w);
        }
         
        /**
         * Wake up all waiting threads.
         *
         * <p>If any threads are waiting on this condition then they
         * are all woken up. Each thread must re-acquire the lock
         * before it returns.
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         */
        public void signalAll() {
            checkConditionAccess(Thread.currentThread(), false);
            Node w = firstWaiter;
            if (w != null) 
                doSignalAll(w);
        }

        /*
         * Various flavors of wait. Each almost the same, but
         * annoyingly different.
         */


        /**
         * Causes the current thread to wait until it is signalled.
         *
         * <p>The lock associated with this condition is atomically
         * released and the current thread becomes disabled for thread
         * scheduling purposes and lies dormant until <em>one</em> of
         * the following happens: 
         *
         * <ul>
         *
         * <li>Some other thread invokes the {@link #signal} method
         * for this <tt>Condition</tt> and the current thread 
         * has been waiting the longest of all waiting threads; or
         *
         * <li>Some other thread invokes the {@link #signalAll} method
         * for this <tt>Condition</tt>
         *
         * </ul>
         *
         * <p>In all cases, before this method can return the current
         * thread must re-acquire the lock associated with this
         * condition. When the thread returns it is
         * <em>guaranteed</em> to hold this lock.
         *
         * <p>If the current thread's interrupt status is set when it
         * enters this method, or it is {@link Thread#interrupt
         * interrupted} while waiting, it will continue to wait until
         * signalled. When it finally returns from this method its
         * <em>interrupted status</em> will still be set.
         * 
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         */
        public void awaitUninterruptibly() {
            Thread current = Thread.currentThread();
            checkConditionAccess(current, true);
            Node w = addConditionWaiter(current);
            int savedState = fullyReleaseExclusive();
            boolean interrupted = false;
            while (!isReacquiring(w)) {
                LockSupport.park();
                if (Thread.interrupted()) 
                    interrupted = true;
            }
            if (reacquireExclusive(current, w, savedState))
                interrupted = true;
            if (interrupted)
                current.interrupt();
        }

        /**
         * Causes the current thread to wait until it is signalled or
         * {@link Thread#interrupt interrupted}.
         *
         * <p>The lock associated with this <tt>Condition</tt> is
         * atomically released and the current thread becomes disabled
         * for thread scheduling purposes and lies dormant until
         * <em>one</em> of the following happens:
         *
         * <ul>
         *
         * <li>Some other thread invokes the {@link #signal} method
         * for this <tt>Condition</tt> and the current thread 
         * has been waiting the longest of all waiting threads; or
         *
         * <li>Some other thread invokes the {@link #signalAll} method
         * for this <tt>Condition</tt>; or
         *
         * <li>Some other thread {@link Thread#interrupt interrupts}
         * the current thread
         *
         * </ul>
         *
         * <p>In all cases, before this method can return the current
         * thread must re-acquire the lock associated with this
         * condition. When the thread returns it is
         * <em>guaranteed</em> to hold this lock.
         *
         * <p>If the current thread has its interrupted status set on
         * entry to this method or is {@link Thread#interrupt
         * interrupted} while waiting, then {@link
         * InterruptedException} is thrown and the current thread's
         * interrupted status is cleared.  
         *
         * @throws InterruptedException if the current thread is
         * interrupted
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         **/
        public void await() throws InterruptedException {
            Thread current = Thread.currentThread();
            checkConditionAccess(current, true);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int savedState = fullyReleaseExclusive();
            boolean throwIE = false;
            boolean interrupted = false;

            for (;;) {
                if (Thread.interrupted()) {
                    if (transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (isReacquiring(w)) 
                    break;
                LockSupport.park();
            }

            if (reacquireExclusive(current, w, savedState))
                interrupted = true;
            if (throwIE)
                throw new InterruptedException();
            if (interrupted)
                current.interrupt();
        }

        /**
         * Causes the current thread to wait until it is signalled or
         * interrupted, or the specified waiting time elapses.
         *
         * <p>The lock associated with this condition is atomically
         * released and the current thread becomes disabled for thread
         * scheduling purposes and lies dormant until <em>one</em> of
         * the following happens:
         *
         * <ul>
         *
         * <li>Some other thread invokes the {@link #signal} method
         * for this <tt>Condition</tt> and the current thread 
         * has been waiting the longest of all waiting threads; or
         *
         * <li>Some other thread invokes the {@link #signalAll} method
         * for this <tt>Condition</tt>; or
         *
         * <li>Some other thread {@link Thread#interrupt interrupts}
         * the current thread; or
         *
         * <li>The specified waiting time elapses
         *
         * </ul>
         *
         * <p>In all cases, before this method can return the current
         * thread must re-acquire the lock associated with this
         * condition. When the thread returns it is
         * <em>guaranteed</em> to hold this lock.
         *
         * <p>If the current thread has its interrupted status set on
         * entry to this method or is {@link Thread#interrupt
         * interrupted} while waiting, then {@link
         * InterruptedException} is thrown and the current thread's
         * interrupted status is cleared.  
         *
         * <p>The method returns an estimate of the number of nanoseconds
         * remaining to wait given the supplied <tt>nanosTimeout</tt>
         * value upon return, or a value less than or equal to zero if it
         * timed out. This value can be used to determine whether and how
         * long to re-wait in cases where the wait returns but an awaited
         * condition still does not hold. 
         *
         * @param nanosTimeout the maximum time to wait, in nanoseconds
         * @return A value less than or equal to zero if the wait has
         * timed out; otherwise an estimate, that
         * is strictly less than the <tt>nanosTimeout</tt> argument,
         * of the time still remaining when this method returned.
         *
         * @throws InterruptedException if the current thread is
         * interrupted.
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         */
        public long awaitNanos(long nanosTimeout) throws InterruptedException {
            Thread current = Thread.currentThread();
            checkConditionAccess(current, true);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int savedState = fullyReleaseExclusive();
            long lastTime = System.nanoTime();
            boolean throwIE = false;
            boolean interrupted = false;

            for (;;) {
                if (Thread.interrupted()) {
                    if (transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(current, w); 
                    break;
                }
                if (isReacquiring(w)) 
                    break;
                LockSupport.parkNanos(nanosTimeout);
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }

            if (reacquireExclusive(current, w, savedState))
                interrupted = true;
            if (throwIE)
                throw new InterruptedException();
            if (interrupted)
                current.interrupt();
            return nanosTimeout - (System.nanoTime() - lastTime);
        }


        /**
         * Causes the current thread to wait until it is signalled or
         * interrupted, or the specified deadline elapses.
         *
         * <p>The lock associated with this condition is atomically
         * released and the current thread becomes disabled for thread
         * scheduling purposes and lies dormant until <em>one</em> of
         * the following happens:
         *
         * <ul>
         *
         * <li>Some other thread invokes the {@link #signal} method
         * for this <tt>Condition</tt> and the current thread 
         * has been waiting the longest of all waiting threads; or
         *
         * <li>Some other thread invokes the {@link #signalAll} method
         * for this <tt>Condition</tt>; or
         *
         * <li>Some other thread {@link Thread#interrupt interrupts}
         * the current thread; or
         *
         * <li>The specified deadline elapses
         *
         * </ul>
         *
         * <p>In all cases, before this method can return the current
         * thread must re-acquire the lock associated with this
         * condition. When the thread returns it is
         * <em>guaranteed</em> to hold this lock.
         *
         * <p>If the current thread has its interrupted status set on
         * entry to this method or is {@link Thread#interrupt
         * interrupted} while waiting, then {@link
         * InterruptedException} is thrown and the current thread's
         * interrupted status is cleared.  
         *
         * @param deadline the absolute time to wait until
         * @return <tt>false</tt> if the deadline has
         * elapsed upon return, else <tt>true</tt>.
         *
         * @throws InterruptedException if the current thread is interrupted
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         * @throws NullPointerException if deadline is null
         */
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            if (deadline == null)
                throw new NullPointerException();
            Thread current = Thread.currentThread();
            checkConditionAccess(current, true);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int savedState = fullyReleaseExclusive();
            long abstime = deadline.getTime();
            boolean timedout = false;
            boolean throwIE = false;
            boolean interrupted = false;
            
            for (;;) {
                if (Thread.interrupted()) {
                    if (transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(current, w); 
                    break;
                }
                if (isReacquiring(w)) 
                    break;
                LockSupport.parkUntil(abstime);
            }

            if (reacquireExclusive(current, w, savedState))
                interrupted = true;
            if (throwIE)
                throw new InterruptedException();
            if (interrupted)
                current.interrupt();
            return !timedout;
        }
        
        /**
         * Causes the current thread to wait until it is signalled or
         * interrupted, or the specified waiting time elapses. This
         * method is behaviorally equivalent to:<br>
         *
         * <pre>
         *   awaitNanos(unit.toNanos(time)) &gt; 0
         * </pre>
         *
         * @param time the maximum time to wait
         * @param unit the time unit of the <tt>time</tt> argument.
         * @return <tt>false</tt> if the waiting time detectably
         * elapsed before return from the method, else <tt>true</tt>.
         * @throws InterruptedException if the current thread is
         * interrupted
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         * @throws NullPointerException if unit is null
         */
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();

            long nanosTimeout = unit.toNanos(time);
            Thread current = Thread.currentThread();
            checkConditionAccess(current, true);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int savedState = fullyReleaseExclusive();
            long lastTime = System.nanoTime();
            boolean timedout = false;
            boolean throwIE = false;
            boolean interrupted = false;

            for (;;) {
                if (Thread.interrupted()) {
                    if (transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(current, w); 
                    break;
                }
                if (isReacquiring(w)) 
                    break;
                LockSupport.parkNanos(nanosTimeout);
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }

            if (reacquireExclusive(current, w, savedState))
                interrupted = true;
            if (throwIE)
                throw new InterruptedException();
            if (interrupted)
                current.interrupt();
            return !timedout;
        }

        /**
         * Queries whether any threads are waiting on this
         * condition. Note that because timeouts and interrupts may
         * occur at any time, a <tt>true</tt> return does not
         * guarantee that a future <tt>signal</tt> will awaken any
         * threads.  This method is designed primarily for use in
         * monitoring of the system state.
         * @return <tt>true</tt> if there are any waiting threads.
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         */ 
        public boolean hasWaiters() {
            checkConditionAccess(Thread.currentThread(), false);
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.status == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition. Note that because timeouts and interrupts
         * may occur at any time, the estimate serves only as an upper
         * bound on the actual number of waiters.  This method is
         * designed for use in monitoring of the system state, not for
         * synchronization control.
         * @return the estimated number of waiting threads.
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         */ 
        public int getWaitQueueLength() {
            checkConditionAccess(Thread.currentThread(), false);
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.status == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.  Because the actual set of
         * threads may change dynamically while constructing this
         * result, the returned collection is only a best-effort
         * estimate. The elements of the returned collection are in no
         * particular order.  This method is designed to facilitate
         * construction of subclasses that provide more extensive
         * condition monitoring facilities.
         * @return the collection of threads
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held
         */
        protected Collection<Thread> getWaitingThreads() {
            checkConditionAccess(Thread.currentThread(), false);
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.status == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }
}
