/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.Date;
import java.lang.reflect.*;
import sun.misc.*;

/**
 * A reentrant mutual exclusion {@link Lock} with the same basic
 * behavior and semantics as the implicit monitor lock accessed by the
 * use of <tt>synchronized</tt> methods and statements, but without
 * the forced block-structured locking and unlocking that occurs with
 * <tt>synchronized</tt> methods and statements.
 *
 * <p> The constructor for this class accepts an optional
 * <em>fairness</em> parameter.  When set <tt>true</tt>, under
 * contention, locks favor granting access to the longest-waiting
 * thread.  Otherwise this lock does not guarantee any particular
 * access order.  Programs using fair locks accessed by many threads
 * may display lower overall throughput (i.e., are slower; often much
 * slower) than those using default locks, but have but smaller
 * variances in times to obtain locks and guarantee lack of
 * starvation. Note however, that fairness of locks does not in any
 * way guarantee fairness of thread scheduling. Thus, it is very
 * possible that one of many threads using a fair lock will obtain it
 * multiple times in succession while other active threads are not
 * running and not currently holding the lock.
 *
 * <p>A <tt>ReentrantLock</tt> may be used in a non-reentrant way by
 * checking that the lock is not already held by the current thread 
 * prior to locking - see {@link #isHeldByCurrentThread}.
 *
 * <p><tt>ReentrantLock</tt> instances are intended to be used primarily 
 * in before/after constructions such as:
 *
 * <pre>
 * class X {
 *   private final ReentrantLock lock = new ReentrantLock();
 *   // ...
 *
 *   public void m() { 
 *     lock.lock();  // block until condition holds
 *     try {
 *       // ... method body
 *     } finally {
 *       lock.unlock()
 *     }
 *   }
 * }
 * </pre>
 *
 * <p> It is recommended practice to <em>always</em> immediately
 * follow a call to <tt>lock</tt> with a <tt>try</tt> block.
 *
 * <p>Except where noted, passing a <tt>null</tt> value for any parameter 
 * will result in a {@link NullPointerException} being thrown.
 *
 * <h3>Implementation Notes</h3>
 * <p>This implementation supports the interruption of lock acquisition and 
 * provides a 
 * {@link #newCondition Condition} implementation that supports the 
 * interruption of thread suspension.
 * It also favors interruption over normal method return.
 *
 *
 * @since 1.5
 * @author Doug Lea
 * 
 */
public class ReentrantLock implements Lock, java.io.Serializable {
    /*
     *  General description and notes.
     *
     *  The basic fastpath/slowpath algorithm looks like this, ignoring 
     *  reentrance, cancellation, timeouts, error checking etc:
     *    Lock:
     *      if (!fair && casOwner(null, currentThread)) // fastpath
     *        return;
     *      node = create and enq a wait node;
     *      for (;;) {
     *        if (node is first on queue) {
     *          if (casOwner(null, currentThread)) {
     *             deq(node);
     *             return;
     *           }
     *        }
     *        park(currentThread);
     *      }
     *
     *    Unlock:
     *      owner = null;      // volatile assignment
     *      h = first node on queue;
     *      if (h != null) unpark(h's successor's thread);
     *
     *  * The fast path uses one atomic CAS operation, plus one
     *    StoreLoad barrier (i.e., volatile-write-barrier) per
     *    lock/unlock pair.  The "owner" field is handled as a simple
     *    spinlock.  To lock, the owner field is set to current thread
     *    using conditional atomic update.  To unlock, the owner field
     *    is set to null, checking if anyone needs waking up, if so
     *    doing so.  Recursive locks/unlocks instead increment/decrement
     *    recursion field.
     *
     *  * By default, contended locks use a kind of "greedy" /
     *    "renouncement" / barging / convoy-avoidance strategy:
     *    When a lock is released, a waiting thread is signalled
     *    so that it can (re)contend for the lock. It might lose
     *    and thus need to rewait. This strategy has much higher
     *    throughput than "directed handoff" because it reduces
     *    blocking of running threads, but poorer fairness.  The
     *    wait queue is FIFO, but newly entering threads can barge
     *    ahead and grab lock before woken waiters, so acquires
     *    are not strictly FIFO, and transfer is not
     *    deterministically fair. It is probablistically fair in
     *    the sense that earlier queued threads are allowed to
     *    recontend before later queued threads, and each
     *    recontention has an unbiased chance to succeed against
     *    any incoming barging threads.
     *
     *  * The "fair" variant differs only in that barging is disabled
     *    when there is contention, so locks proceed FIFO. There can be
     *    some races in detecting contention, but it is still FIFO from
     *    a definable (although complicated to describe) single point,
     *    so qualifies as a FIFO lock.
     *
     *  * While this lock never "spins" in the usual sense, it checks for
     *    ownership multiple times (four in the most common case of a
     *    call from <tt>lock</tt>) interspersed with other computations
     *    before the first call to <tt>park</tt>.  This gives most of
     *    the benefits of spins when locks are only briefly held without
     *    most of the liabilities when they aren't.
     *
     *  * The wait queue is a variant of a "CLH" (Craig, Landin, and
     *    Hagersten) lock. CLH locks are normally used for spinlocks.
     *    We instead use them for blocking locks, but use the same
     *    basic tactic of holding some of the control information
     *    about a thread in the predecessor of its node.  A "status"
     *    field in each node keeps track of whether a thread is/should
     *    block.  A node is signalled when its predecessor releases
     *    the lock. Each node of the queue otherwise serves as a
     *    specific-notification-style monitor holding a single waiting
     *    thread. The status field does NOT control whether threads
     *    are granted locks though.  A thread may try to acquire
     *    ownership if it is first in the queue. But being first does
     *    not guarantee the lock; it only give the right to contend
     *    for it (by CAS'ing owner field).  So the currently released
     *    contender thread may need to rewait.
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
     *    effort if the lock is never contended. Instead, the node
     *    is constructed and head and tail pointers are set upon first
     *    contention.
     *
     *  * Threads waiting on Conditions use extended versions
     *    of nodes with an additional link. Conditions only need to
     *    link nodes in simple (non-concurrent) linked queues because
     *    they are only accessed when lock is held.  Upon await, a
     *    node is inserted into a condition queue.  Upon signal, the
     *    node is transferred to the lock queue.  A special value of
     *    status field is used to mark which queue a node is on.
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


    /*
     * Values for node status:
     *   PARKED:     Successor of this node is (or will soon be) blocked 
     *               in park and hasn't been unparked.
     *   CANCELLED:  Node is cancelled due to timeout or interrupt
     *               Nodes never leave this state. In particular,
     *               a thread with cancelled node never again blocks.
     *   CONDITION:  Node is currently on a condition queue
     *               It will not be used as a lock queue node until
     *               transferred.
     *   0:          Not any of the above
     *
     * The values are arranged numerically to simplify use.
     * Non-negative values mean that a node doesn't need to be
     * unparked to progress. So, some code doesn't need to check for
     * particular values, just for sign.
     */

    static final int CANCELLED =  1;
    static final int PARKED    = -1;
    static final int CONDITION = -2;

    /**
     * Node class for threads waiting for locks. 
     */
    static class LockNode {
        /**
         * Status field mainly used for indicating whether successor
         * node needs to be signalled, Initialized to 0 for normal lock
         * nodes, and CONDITION for condition nodes.  Takes on only
         * the values listed above, and modified only using CAS,
         * except for transitions to CANCELLED, which are
         * unconditionally, "finally" assigned.
         */
        transient volatile int status;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking status. Assigned during enqueing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of a thread getting the lock. A
         * cancelled thread never gets the lock, and a thread only
         * cancels itself, not any other node.
         */
        transient volatile LockNode prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon lock release. Assigned once during enquing,
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
        transient volatile LockNode next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.  Note that this need
         * not be declared volatile since it is always accessed after
         * traversing volatile links, and written before writing
         * links.
         */
        transient Thread thread;

        LockNode(Thread t) { 
            thread = t; 
        }
    }

    /**
     * Serialization ID. Note that all fields are defined in a way so
     * that deserialized locks are in initial unlocked state, and
     * there is no explicit serialization code.
     */
    private static final long serialVersionUID = 7373984872572414699L;

    /** 
     * Current owner of lock, or null iff the lock is free.  Acquired
     * only using CAS.
     */
    private transient volatile Thread owner;

    /** Number of recursive acquires. Note: total holds = recursions+1 */
    private transient int recursions;

    /** true if barging disabled */
    private final boolean fair;

    /** 
     * Head of the wait queue, lazily initialized.  Except for
     * initialisation, it is modified only by a thread upon acquiring
     * the lock. If head exists, it's node status is guaanteed not to
     * be CANCELLED.
     */
    private transient volatile LockNode head; 

    /** 
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile LockNode tail; 

    // Atomics support

    private static final OwnerUpdater ownerUpdater = new OwnerUpdater();

    private static final 
        AtomicReferenceFieldUpdater<ReentrantLock, LockNode>  
        tailUpdater = 
        AtomicReferenceFieldUpdater.<ReentrantLock, LockNode>newUpdater 
        (ReentrantLock.class, LockNode.class, "tail");
    private static final 
        AtomicReferenceFieldUpdater<ReentrantLock, LockNode>   
        headUpdater = 
        AtomicReferenceFieldUpdater.<ReentrantLock, LockNode>newUpdater 
        (ReentrantLock.class,  LockNode.class, "head");
    private static final 
        AtomicIntegerFieldUpdater<LockNode>  
        statusUpdater = 
        AtomicIntegerFieldUpdater.newUpdater 
        (LockNode.class, "status");

    /**
     * Creates an instance of <tt>ReentrantLock</tt>.
     * This is equivalent to using <tt>ReentrantLock(false)</tt>.
     */
    public ReentrantLock() { 
        fair = false;
    }

    /**
     * Creates an instance of <tt>ReentrantLock</tt> with the
     * given fairness policy.
     */
    public ReentrantLock(boolean fair) { 
        this.fair = fair;
    }

    // Internal utilities

    /**
     * Insert node into queue. 
     * @param node the node to insert
     */
    private void enq(LockNode node) {
        LockNode t = tail;
        if (t == null) {         // Must initialize first
            LockNode h = new LockNode(null);
            while ((t = tail) == null) {     
                if (headUpdater.compareAndSet(this, null, h)) 
                    tail = h;
            }
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
     * Create and add a new node to queue, interspersed with checks to
     * make sure we really need to.
     * @return the node, or null if acquired lock so don't need to wait
     */
    private LockNode newLockWaiter(Thread current) {
        // First check for recursion ...
        Thread o = owner;
        if (o == current) { 
            ++recursions;
            return null;
        }

        // ... and retry fastpath (since cheap to here)
        if (o == null &&    
            (!fair || head == tail) &&
            ownerUpdater.acquire(this, current))
            return null;
        
        LockNode node = new LockNode(current);

        /*
         * If constructing the node triggered GC (or just some slow
         * construction path), enough time will have passed to make
         * fastpath recheck worthwhile before enqueuing.
         */
        o = owner;
        if (o == null &&    
            (!fair || head == tail) &&
            ownerUpdater.acquire(this, current))
            return null;

        enq(node);
        return node;
    }

    /**
     * Main locking code for untimed locks that do not make it through
     * fast path. (The timed version is handled directly in
     * tryLock(timeout). It differs enough in detail to separate out.)
     * @param current current thread
     * @param node its wait-node, if it already exists; else null in
     * which case it is created,
     * @param interruptible true if can abort if interrupted
     * @return true if lock acquired (can be false only if interruptible)
     */
    private boolean waitForLock(Thread current, 
                                LockNode node, 
                                boolean interruptible) {
        /*
         * If node isn't null, it was already created during a
         * Condition await, and then transfered from a condition
         * queue.  Otherwise, create node unless newLockWaiter
         * discovers we don't need to because this is a recursive hold
         * or fastpath retry succeeded.
         */
        if (node == null && (node = newLockWaiter(current)) == null)
            return true;

        boolean wasInterrupted = false;

        /*
         * p is our predecessor node, that holds status giving
         * permission to try to obtain lock if we are first in queue.
         */
        LockNode p = node.prev;

        /*
         * Repeatedly try to get ownership if first in queue; block
         * (park) on failure.  If we are the first thread in queue, we
         * must try to get the lock, and we must not try to get lock
         * if we are not first -- We can park only if we are sure that
         * some other thread holds lock and will signal us. 
         *
         * Along the way, make sure that the predecessor hasn't been
         * cancelled. When about to park, first set status enabling
         * lock-holder to signal, and then recheck one final time
         * before actually blocking (this also has effect of handling
         * failed status CAS).
         */
        while (p != head ||
               owner != null ||
               !ownerUpdater.acquire(this, current)) {
            int status = p.status;
            if (status == CANCELLED) 
                node.prev = p = p.prev;
            else if (status == 0) 
                statusUpdater.compareAndSet(p, 0, PARKED);
            else { 
                LockSupport.park();
                if (Thread.interrupted()) {
                    wasInterrupted = true; 
                    if (interruptible)  {  
                        cancelWaitForLock(node);
                        return false;
                    }
                }
            }
        }

        /*
         * Set head, and unlink fields we are sure will be unused in
         * the future for sake of GC after a successful acquire
         */ 
        head = node;
        p.next = null; 
        node.thread = null;
        node.prev = null; 
        if (wasInterrupted) // Re-interrupt on normal exit
            current.interrupt();
        return true;
    }

    /**
     * Unpark node's successor, if one exists.
     * @param node the node
     */
    private void unparkSuccessor(LockNode node) {
        /*
         * Successor is normally just node.next.  But if
         * it is cancelled, traverse through its next's.
         */
        LockNode s = node.next;
        while (s != null) {
            if (s.status == CANCELLED) 
                s = s.next;
            else {
                LockSupport.unpark(s.thread);
                break;
            }
        }
    }

    /**
     * Cancel a node waiting for lock because it was interrupted or timed out
     * @param node the node
     */
    private void cancelWaitForLock(LockNode node) {
        node.thread = null;                // disable signals
        node.status = CANCELLED;
        unparkSuccessor(node);
    }


    /**
     * Wake up the first node waiting for lock, if one exists.  Called
     * only from unlock, and broken out from it just to encourage
     * compilers to inline non-contended cases.
     * 
     * @param node the head node at point of call (non-null). This
     * is passed in as argument just to avoid having to reread it.
     */
    private void releaseFirst(LockNode h) {
        if (h.status < 0) { // Skip if no successor parked
            /*
             * Reset status before unparking.  A given head-node can
             * be "used" by multiple unlocking owners that acquired by
             * barging. When they do so, and later unlock, the
             * successor that lost a previous race and re-parked must
             * be re-signalled. But otherwise, we'd like to minimize
             * unnecessary calls to unpark, which may be relatively
             * expensive. We don't bother to loop on failed CAS here
             * though, since the reset is just an performance hint.
             */
            statusUpdater.compareAndSet(h, PARKED, 0);
            unparkSuccessor(h);
        }
    }

    // Public Lock methods

    /**
     * Attempts to release this lock.  
     * <p>If the current thread is the
     * holder of this lock then the hold count is decremented. If the
     * hold count is now zero then the lock is released.  If the
     * current thread is not the holder of this lock then {@link
     * IllegalMonitorStateException} is thrown.
     * @throws IllegalMonitorStateException if the current thread does not
     * hold this lock.
     */
    public void unlock() {
        if (Thread.currentThread() != owner)
            throw new IllegalMonitorStateException();
        else if (recursions > 0) 
            --recursions;
        else {
            owner = null;
            LockNode h = head;
            if (h != null) 
                releaseFirst(h);
        }
    }

    /**
     * Acquire the lock. 
     * <p>Acquires the lock if it is not held by another thread and returns 
     * immediately, setting the lock hold count to one.
     * <p>If the current thread
     * already holds the lock then the hold count is incremented by one and
     * the method returns immediately.
     * <p>If the lock is held by another thread then the
     * current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until the lock has been acquired,
     * at which time the lock hold count is set to one. 
     */
    public void lock() {
        Thread current = Thread.currentThread();
        if ((fair && head != tail) || 
            !ownerUpdater.acquire(this, current))
            waitForLock(current, null, false);
    }

    /**
     * Acquires the lock unless the current thread is 
     * {@link Thread#interrupt interrupted}.
     * <p>Acquires the lock if it is not held by another thread and returns 
     * immediately, setting the lock hold count to one.
     * <p>If the current thread already holds this lock then the hold count 
     * is incremented by one and the method returns immediately.
     * <p>If the lock is held by another thread then the
     * current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of two things happens:
     * <ul>
     * <li>The lock is acquired by the current thread; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     * <p>If the lock is acquired by the current thread then the lock hold 
     * count is set to one.
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or 
     * <li>is {@link Thread#interrupt interrupted} while acquiring 
     * the lock,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. 
     * <p>In this implementation, as this method is an explicit interruption 
     * point, preference is 
     * given to responding to the interrupt over normal or reentrant 
     * acquisition of the lock.
     *
     * @throws InterruptedException if the current thread is interrupted
     */
    public void lockInterruptibly() throws InterruptedException { 
        Thread current = Thread.currentThread();
        // Fail if interrupted or fastpath and slowpath both fail
        if (Thread.interrupted() ||
            (((fair && head != tail) || 
              !ownerUpdater.acquire(this, current)) &&
             !waitForLock(current, null, true)))
            throw new InterruptedException();
    }

    /**
     * Acquires the lock only if it is not held by another thread at the time
     * of invocation.
     * <p>Acquires the lock if it is not held by another thread and
     * returns immediately with the value <tt>true</tt>, setting the
     * lock hold count to one. Even when this lock has been set to use a
     * fair ordering policy, a call to <tt>tryLock()</tt> <em>will</em>
     * immediately acquire the lock if it is available, whether or not
     * other threads are currently waiting for the lock. 
     * This &quot;barging&quot; behaviour can be useful in certain 
     * circumstances, even though it breaks fairness. If you want to honor
     * the fairness setting for this lock, then use 
     * {@link #tryLock(long, TimeUnit) tryLock(0, TimeUnit.SECONDS) }
     * which is almost equivalent (it also detects interruption).
     * <p> If the current thread
     * already holds this lock then the hold count is incremented by one and
     * the method returns <tt>true</tt>.
     * <p>If the lock is held by another thread then this method will return 
     * immediately with the value <tt>false</tt>.  
     *
     * @return <tt>true</tt> if the lock was free and was acquired by the
     * current thread, or the lock was already held by the current thread; and
     * <tt>false</tt> otherwise.
     */
    public boolean tryLock() {
        Thread current = Thread.currentThread();
        if (ownerUpdater.acquire(this, current))
            return true;
        if (owner == current) {
            ++recursions;
            return true;
        }
        return false;
    }

    /**
     *
     * Acquires the lock if it is not held by another thread within the given 
     * waiting time and the current thread has not been 
     * {@link Thread#interrupt interrupted}.
     * <p>Acquires the lock if it is not held by another thread and returns 
     * immediately with the value <tt>true</tt>, setting the lock hold count 
     * to one. If this lock has been set to use a fair ordering policy then
     * an available lock <em>will not</em> be acquired if any other threads
     * are waiting for the lock. This is in contrast to the {@link #tryLock()}
     * method. If you want a timed <tt>tryLock</tt> that does permit barging on
     * a fair lock then combine the timed and un-timed forms together:
     * <pre>if (lock.tryLock() || lock.tryLock(timeout, unit) ) { ... }
     * </pre>
     * <p>If the current thread
     * already holds this lock then the hold count is incremented by one and
     * the method returns <tt>true</tt>.
     * <p>If the lock is held by another thread then the
     * current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li>The lock is acquired by the current thread; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses
     * </ul>
     * <p>If the lock is acquired then the value <tt>true</tt> is returned and
     * the lock hold count is set to one.
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or 
     * <li>is {@link Thread#interrupt interrupted} while acquiring
     * the lock,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. 
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * If the time is 
     * less than or equal to zero, the method will not wait at all.
     * <p>In this implementation, as this method is an explicit interruption 
     * point, preference is 
     * given to responding to the interrupt over normal or reentrant 
     * acquisition of the lock, and over reporting the elapse of the waiting
     * time.
     *
     *
     * @param timeout the time to wait for the lock
     * @param unit the time unit of the timeout argument
     *
     * @return <tt>true</tt> if the lock was free and was acquired by the
     * current thread, or the lock was already held by the current thread; and
     * <tt>false</tt> if the waiting time elapsed before the lock could be 
     * acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     *
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null)
            throw new NullPointerException();
        if (Thread.interrupted())
            throw new InterruptedException();

        Thread current = Thread.currentThread();
        if ((!fair || head == tail) && 
            ownerUpdater.acquire(this, current))
            return true;
        if (owner == current) { 
            ++recursions;
            return true;
        }
        if (timeout <= 0L)
            return false;

        // Specialized variant of waitForLock code.

        long nanos = unit.toNanos(timeout);
        long lastTime = System.nanoTime();
        LockNode node = newLockWaiter(current);
        if (node == null)
            return true;

        LockNode p = node.prev;
        while (p != head ||
               owner != null ||
               !ownerUpdater.acquire(this, current)) {
            if (nanos <= 0L) {     
                cancelWaitForLock(node);
                return false;
            }
            int status = p.status;
            if (status == CANCELLED) 
                node.prev = p = p.prev;
            else if (status == 0) 
                statusUpdater.compareAndSet(p, 0, PARKED);
            else {                      
                LockSupport.parkNanos(nanos);
                if (Thread.interrupted()) {
                    cancelWaitForLock(node);
                    throw new InterruptedException();
                }
                long now = System.nanoTime();
                nanos -= now - lastTime;
                lastTime = now;
            }
        }

        head = node;
        p.next = null; 
        node.thread = null;
        node.prev = null; 
        return true;
    }


    /**
     * Queries the number of holds on this lock by the current thread.
     * <p>A thread has a hold on a lock for each lock action that is not 
     * matched by an unlock action.
     * <p>The hold count information is typically only used for testing and
     * debugging purposes. For example, if a certain section of code should
     * not be entered with the lock already held then we can assert that
     * fact:
     * <pre>
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *
     *   public void m() { 
     *     assert lock.getHoldCount() == 0;
     *     lock.lock();
     *     try {
     *       // ... method body
     *     } finally {
     *       lock.unlock()
     *     }
     *   }
     * }
     * </pre>
     *
     * @return the number of holds on this lock by the current thread,
     * or zero if this lock is not held by the current thread.
     */
    public int getHoldCount() {
        return (owner == Thread.currentThread()) ?  recursions + 1 :  0;
    }

    /**
     * Queries if this lock is held by the current thread.
     * <p>Analogous to the {@link Thread#holdsLock} method for built-in
     * monitor locks, this method is typically used for debugging and
     * testing. For example, a method that should only be called while
     * a lock is held can assert that this is the case:
     * <pre>
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *
     *   public void m() { 
     *       assert lock.isHeldByCurrentThread();
     *       // ... method body
     *   }
     * }
     * </pre>
     * <p>It can also be used to ensure that a reentrant lock is used
     * in a non-reentrant manner, for example:
     * <pre>
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *
     *   public void m() { 
     *       assert !lock.isHeldByCurrentThread();
     *       lock.lock();
     *       try {
     *           // ... method body
     *       } finally {
     *           lock.unlock();
     *       }
     *   }
     * }
     * </pre>
     * @return <tt>true</tt> if current thread holds this lock and 
     * <tt>false</tt> otherwise.
     */
    public boolean isHeldByCurrentThread() {
        return owner == Thread.currentThread();
    }

    /**
     * Queries if this lock is held by any thread. This method is
     * designed for use in monitoring of the system state, 
     * not for synchronization control.
     * @return <tt>true</tt> if any thread holds this lock and 
     * <tt>false</tt> otherwise.
     */
    public boolean isLocked() {
        return owner != null;
    }

    /**
     * Returns a {@link Condition} instance for use with this 
     * {@link Lock} instance.
     *
     * <p>The returned {@link Condition} instance supports the same
     * usages as do the {@link Object} monitor methods ({@link
     * Object#wait() wait}, {@link Object#notify notify}, and {@link
     * Object#notifyAll notifyAll}) when used with the built-in
     * monitor lock.
     *
     * <ul>
     *
     * <li>If this lock is not held by the calling thread when any of
     * the {@link Condition} {@link Condition#await() waiting} or
     * {@link Condition#signal signalling} methods are called, then an
     * {@link IllegalMonitorStateException} is thrown.
     *
     * <li>When the condition {@link Condition#await() waiting}
     * methods are called the lock is released and, before they
     * return, the lock is reacquired and the lock hold count restored
     * to what it was when the method was called.
     *
     * <li>If a thread is {@link Thread#interrupt interrupted} while
     * waiting then the wait will terminate, an {@link
     * InterruptedException} will be thrown, and the thread's
     * interrupted status will be cleared.
     *
     * <li>The order in which waiting threads are signalled is not
     * specified.
     *
     * <li>The ordering of lock reacquisition for threads returning
     * from waiting methods is the same as for threads initially
     * acquiring the lock, which is in the default case not specified,
     * but for <em>fair</em> locks favors those threads that have been
     * waiting the longest.
     * 
     * </ul>
     * @return the Condition object
     */
    public Condition newCondition() {
        return new ReentrantLockCondition(this);
    }

    // Helper methods for Conditions

    /**
     * Throw IllegalMonitorStateException if given thread is not owner
     * @param thread the thread
     * @throws IllegalMonitorStateException if thread not owner
     */
    final void checkOwner(Thread thread) {
        if (owner != thread) 
            throw new IllegalMonitorStateException();
    }

    /**
     * Variant of unlock used by condition wait. 
     * Fully unlocks, setting recursions to zero.
     * @return current recursion count.
     */
    final int unlockForWait() {
        int recs = recursions;
        recursions = 0;
        unlock();
        return recs;
    }

    /**
     * Re-acquire lock after a wait, resetting recursion count.
     * @param current the waiting thread
     * @param node its node
     * @param recs number of recursive holds on lock before entering wait
     */
    final void relockAfterWait(Thread current, 
                               LockNode node, 
                               int recs) {
        waitForLock(current, node, false);
        recursions = recs;
    }

    /**
     * Return true if a node, always one that was initially placed on
     * a condition queue, is now on the lock queue.
     * @param node the node
     * @return true if on lock queue
     */
    final boolean isOnLockQueue(LockNode node) {
        if (node.status == CONDITION || node.prev == null)
            return false;
        // If node has successor, it must be on lock queue
        if (node.next != null) 
            return true;
        /*
         * node.prev can be non-null, but not yet on lock queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         */
        LockNode t = tail; 
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    } 

    /**
     * Transfer a node from a condition queue onto lock queue. 
     * Return true if successful.
     * @param node the node
     * @return true if succesfully transferred (else the node was
     * cancelled before signal).
     */
    final boolean transferForSignal(LockNode node) {
        /*
         * If cannot change status, the node has been cancelled.
         */
        if (!statusUpdater.compareAndSet(node, CONDITION, 0))
            return false;

        /*
         * Splice onto queue and try to set status of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set status fails, wake up to resynch (in which
         * case the status can be transiently/harmlessly wrong).
         */
        enq(node);
        LockNode p = node.prev;
        int c = p.status;
        if (c == CANCELLED || !statusUpdater.compareAndSet(p, c, PARKED))
            LockSupport.unpark(node.thread);

        return true;
    }

    /**
     * Re-acquire lock after a cancelled wait. Return true if thread
     * was cancelled before being signalled.
     * @param current the waiting thread
     * @param node its node
     * @param recs number of recursive holds on lock before entering wait
     * @return true if cancelled before the node was signalled.
     */
    final boolean relockAfterCancelledWait(Thread current, 
                                           LockNode node, 
                                           int recs) {
        boolean isCancelled = statusUpdater.compareAndSet(node, CONDITION, 0);
        if (isCancelled) // place on lock queue
            enq(node);
        else {
            /*
             * If we lost out to a signal(), then we can't proceed
             * until it finishes its enq().  Cancelling during an an
             * incomplete transfer is both rare and transient, so just
             * spin.
             */
            while (!isOnLockQueue(node)) 
                Thread.yield();
        }
        waitForLock(current, node, false);
        recursions = recs;
        return isCancelled;
    }

    /**
     * Condition objects for ReentrantLock. (This class could equally
     * well be defined as a non-static inner class, but static with
     * explicit link to the lock makes the interplay between lock
     * methods and condition methods clearer.)
     */

    static class ReentrantLockCondition implements Condition, java.io.Serializable {
        /**
         * Node class for conditions. Because condition queues are
         * accessed only when locks are already held, we just need a
         * simple linked queue to hold nodes while they are waiting on
         * conditions. They are then transferred to the lock queue to
         * re-acquire locks.
         */
        static class ConditionNode extends LockNode {
            /** Link to next node waiting on condition. */
            transient ConditionNode nextWaiter;
            
            ConditionNode(Thread t) { 
                super(t); 
                status = CONDITION;
            }
        }

        /** The lock we are serving as a condition for. */
        private final ReentrantLock lock;
        /** First node of condition queue. */
        private transient ConditionNode firstWaiter;
        /** Last node of condition queue. */
        private transient ConditionNode lastWaiter;

        ReentrantLockCondition(ReentrantLock lock) {
            this.lock = lock;
        }

        /**
         * Add a new waiter to wait queue
         * @param current the thread that will be waiting
         * @return its new wait node
         */
        private ConditionNode addConditionWaiter(Thread current) {
            ConditionNode w = new ConditionNode(current);
            ConditionNode t = lastWaiter;
            if (t == null) 
                firstWaiter = w;
            else 
                t.nextWaiter = w;
            return lastWaiter = w;
        }

        /**
         * Main code for signal.  Remove and transfer nodes until hit
         * non-cancelled one or null. Split out from signal in part to
         * encourage compilers to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(ConditionNode first) {
            do {
                if ( (firstWaiter = first.nextWaiter) == null) 
                    lastWaiter = null;
                first.nextWaiter = null;
            } while (!lock.transferForSignal(first) &&
                     (first = firstWaiter) != null);
        }

        /**
         * Main code for signalAll.  Remove and transfer all nodes.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(ConditionNode first) {
            lastWaiter = firstWaiter  = null;
            do {
                ConditionNode next = first.nextWaiter;
                first.nextWaiter = null;
                lock.transferForSignal(first);
                first = next;
            } while (first != null);
        }

        public final void signal() {
            lock.checkOwner(Thread.currentThread());
            ConditionNode w = firstWaiter;
            if (w != null)
                doSignal(w);
        }
         
        public final void signalAll() {
            lock.checkOwner(Thread.currentThread());
            ConditionNode w = firstWaiter;
            if (w != null) 
                doSignalAll(w);
        }

        /*
         * Cancellation support for await() etc. When a cancellation
         * (interrupt or timeout) and a notification (signal or
         * signalAll) occur at about the same time, the signaller
         * cannot tell for sure that it should not perform the queue
         * transfer when it actually should instead let the
         * cancellation continue. So the signaller will blindly try
         * the transfer. To compensate for this, a cancelling thread
         * must, according to the (draft) JSR-133 spec, ensure
         * notification of some other thread that was waiting when
         * signal() made this mistake (this kind of mistake is rare
         * but inevitable because of intrinsic races detecting
         * cancellation vs queue transfer). Since we give preference
         * to cancellation over normal returns, a cancelling thread
         * that has already been notified performs the equivalent of a
         * signal(). Because our condition queues are FIFO, the first
         * waiter (if one exists) may be one that had been waiting at
         * the point of the lost signal/cancel race (or not, in which
         * case the wakeup is spurious). But no other thread could
         * have have already been waiting if the first one wasn't, so
         * a signal((), as opposed to a signalAll(), suffices.
         *
         * This is done only when the cancelling thread has reacquired
         * the lock, in the following two methods dealing with the two
         * kinds of circumstances in which this race can occur: In
         * method cancelAndRelock, when a thread knows that it has
         * cancelled before reacquiring lock and in method
         * checkInterruptAfterRelock, when it notices that it has been
         * interrupted after reacquiring lock, but hadn't noticed
         * before relocking.
         */

        /**
         * Cancel a wait due to interrupt or timeout: reacquire lock
         * and handle possible cancellation race.
         * @param current the waiting thread
         * @param node its node
         * @param recs number of recursive holds on lock before entering wait
         */
        private void cancelAndRelock(Thread current, 
                                     ConditionNode node, 
                                     int recs) {
            if (!lock.relockAfterCancelledWait(current, node, recs)) {
                ConditionNode w = firstWaiter;
                if (w != null) 
                    doSignal(w);
            }
        }

        /**
         * Check for interruption upon return from a wait, throwing
         * InterruptedException and handling possible cancellation
         * race if interrupted.
         */
        private void checkInterruptAfterRelock() throws InterruptedException {
            if (Thread.interrupted()) {
                ConditionNode w = firstWaiter;
                if (w != null) 
                    doSignal(w);
                throw new InterruptedException();
            }
        }
            
        /*
         * Various flavors of wait. Each almost the same, but
         * annoyingly different.
         */

        public final void awaitUninterruptibly() {
            Thread current = Thread.currentThread();
            lock.checkOwner(current);
            ConditionNode w = addConditionWaiter(current);
            int recs = lock.unlockForWait();

            boolean wasInterrupted = false;
            while (!lock.isOnLockQueue(w)) {
                LockSupport.park();
                if (Thread.interrupted()) 
                    wasInterrupted = true;
            }

            lock.relockAfterWait(current, w, recs);
            /*
             * On exit, make sure interrupt status is accurate.  We
             * typically will not need to re-interrupt if
             * wasInterrupted because relockAfterWait may have done
             * this in the course of getting lock, So check first.
             */
            if (wasInterrupted && !current.isInterrupted()) 
                current.interrupt();
        }


        public final void await() throws InterruptedException {
            Thread current = Thread.currentThread();
            lock.checkOwner(current);
            ConditionNode w = addConditionWaiter(current);
            int recs = lock.unlockForWait();

            for (;;) {
                if (Thread.interrupted()) {
                    cancelAndRelock(current, w, recs);
                    throw new InterruptedException();
                }
                if (lock.isOnLockQueue(w)) {
                    lock.relockAfterWait(current, w, recs);
                    checkInterruptAfterRelock();
                    return;
                }
                LockSupport.park();
            }
        }

        public final long awaitNanos(long nanos) throws InterruptedException {
            Thread current = Thread.currentThread();
            lock.checkOwner(current);
            ConditionNode w = addConditionWaiter(current);
            int recs = lock.unlockForWait();
            long lastTime = System.nanoTime();

            for (;;) {
                if (Thread.interrupted()) {
                    cancelAndRelock(current, w, recs);
                    throw new InterruptedException();
                }
                if (nanos <= 0L) {
                    cancelAndRelock(current, w, recs);
                    return nanos;
                }
                if (lock.isOnLockQueue(w)) {
                    lock.relockAfterWait(current, w, recs);
                    checkInterruptAfterRelock();
                    // We could have sat in lock queue a while, so
                    // recompute time left on way out.
                    return nanos - (System.nanoTime() - lastTime);
                }
                LockSupport.parkNanos(nanos);
                long now = System.nanoTime();
                nanos -= now - lastTime;
                lastTime = now;
            }
        }

        public final boolean awaitUntil(Date deadline) throws InterruptedException {
            Thread current = Thread.currentThread();
            lock.checkOwner(current);
            ConditionNode w = addConditionWaiter(current);
            int recs = lock.unlockForWait();
            long abstime = deadline.getTime();
            
            for (;;) {
                if (Thread.interrupted()) {
                    cancelAndRelock(current, w, recs);
                    throw new InterruptedException();
                }
                if (System.currentTimeMillis() > abstime) {
                    cancelAndRelock(current, w, recs);
                    return false;
                }
                if (lock.isOnLockQueue(w)) {
                    lock.relockAfterWait(current, w, recs);
                    checkInterruptAfterRelock();
                    return true;
                }
                LockSupport.parkUntil(abstime);
            }
        }
        
        public final boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();
            return awaitNanos(unit.toNanos(timeout)) > 0L;
        }
    }


    /**
     * This class is a minor performance hack, that will hopefully
     * someday disappear. It specializes AtomicReferenceFieldUpdater
     * for ReentrantLock owner field without requiring dynamic
     * instanceof checks in method acquire.
     */
    private static class OwnerUpdater extends AtomicReferenceFieldUpdater<ReentrantLock,Thread> {

        private static final Unsafe unsafe =  Unsafe.getUnsafe();
        private final long offset;

        OwnerUpdater() {
            try {
                Field field = ReentrantLock.class.getDeclaredField("owner");
                offset = unsafe.objectFieldOffset(field);
            } catch(Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        public boolean compareAndSet(ReentrantLock obj, Thread expect, Thread update) {
            return unsafe.compareAndSwapObject(obj, offset, expect, update);
        }

        public boolean weakCompareAndSet(ReentrantLock obj, Thread expect, Thread update) {
            return unsafe.compareAndSwapObject(obj, offset, expect, update);
        }

        public void set(ReentrantLock obj, Thread newValue) {
            unsafe.putObjectVolatile(obj, offset, newValue);
        }

        public Thread get(ReentrantLock obj) {
            return (Thread)unsafe.getObjectVolatile(obj, offset);
        }

        final boolean acquire(ReentrantLock obj, Thread current) {
            return unsafe.compareAndSwapObject(obj, offset, null, current);
        }
    }
        
}

