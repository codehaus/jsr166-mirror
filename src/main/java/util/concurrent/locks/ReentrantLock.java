/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import java.lang.reflect.*;
import sun.misc.*;

/**
 * A reentrant mutual exclusion {@link Lock} with the same basic
 * behavior and semantics as the implicit monitor lock accessed using
 * <tt>synchronized</tt> methods and statements, but with extended
 * capabilities.
 *
 * <p> A <tt>ReentrantLock</tt> is <em>owned</em> by the thread last
 * successfully locking, but not yet unlocking it. A thread invoking
 * <tt>lock</tt> will return, successfully acquiring the lock, when
 * the lock is not owned by another thread. The method will return
 * immediately if the current thread already owns the lock. This can
 * be checked using methods {@link #isHeldByCurrentThread}, and {@link
 * #getHoldCount}.  A <tt>ReentrantLock</tt> may be used in a
 * non-reentrant way by checking that the lock is not already held by
 * the current thread prior to locking.
 *
 * <p> The constructor for this class accepts an optional
 * <em>fairness</em> parameter.  When set <tt>true</tt>, under
 * contention, locks favor granting access to the longest-waiting
 * thread.  Otherwise this lock does not guarantee any particular
 * access order.  Programs using fair locks accessed by many threads
 * may display lower overall throughput (i.e., are slower; often much
 * slower) than those using the default setting, but have smaller
 * variances in times to obtain locks and guarantee lack of
 * starvation. Note however, that fairness of locks does not guarantee
 * fairness of thread scheduling. Thus, one of many threads using a
 * fair lock may obtain it multiple times in succession while other
 * active threads are not progressing and not currently holding the
 * lock.
 *
 * <p> It is recommended practice to <em>always</em> immediately
 * follow a call to <tt>lock</tt> with a <tt>try</tt> block, most
 * typically in a before/after construction such as:
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
 * <p>In addition to implementing the {@link Lock} interface, this
 * class defines methods <tt>isLocked</tt> and
 * <tt>getLockQueueLength</tt>, as well as some associated
 * <tt>protected</tt> access methods that may be useful for
 * instrumentation and monitoring.
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
     *  The basic idea, ignoring all sorts of things
     *  (reentrance, cancellation, timeouts, error checking etc) is:
     *    Lock:
     *      if (casOwner(null, currentThread)) // fastpath
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
     *  * Even the non-fair version doesn't do a bare CAS in the
     *    fast path (except in tryLock). Instead, if the wait queue
     *    appears to be non-empty, it uses a test-and-test-and-set
     *    approach, checking the owner field before trying to CAS it,
     *    which avoids most failed CASes.
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

    /** Node status value to indicate thread has cancelled */
    static final int CANCELLED =  1;
    /** Node status value to indicate thread needs unparking */
    static final int SIGNAL    = -1;
    /** Node status value to indicate thread is waiting on condition */
    static final int CONDITION = -2;

    /**
     * Node class for threads waiting for locks. 
     */
    static class LockNode {
        /**
         * Status field, taking on only the values:
         *   SIGNAL:     The successor of this node is (or will soon be) 
         *               blocked (via park), so the current node must 
         *               unpark its successor when it releases lock or 
         *               cancels.
         *   CANCELLED:  Node is cancelled due to timeout or interrupt
         *               Nodes never leave this state. In particular,
         *               a thread with cancelled node never again blocks.
         *   CONDITION:  Node is currently on a condition queue
         *               It will not be used as a lock queue node until
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
         * The field is initialized to 0 for normal lock nodes, and
         * CONDITION for condition nodes.  It is modified only using
         * CAS, except for transitions to CANCELLED, which are
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

    /** Number of recursive acquires. Note: total holds = recursions+1 */
    private transient int recursions;

    /** true if barging disabled */
    private final boolean fair;


    // Atomics support

    /*
     * For now, we use a special version of an AtomicReferenceFieldUpdater
     * (defined below) to simplify and slightly speed up CAS'ing owner field. 
     */
    private static final OwnerUpdater ownerUpdater = new OwnerUpdater();

    private static final 
        AtomicReferenceFieldUpdater<ReentrantLock, LockNode> tailUpdater = 
        AtomicReferenceFieldUpdater.<ReentrantLock, LockNode>newUpdater 
        (ReentrantLock.class, LockNode.class, "tail");
    private static final 
        AtomicReferenceFieldUpdater<ReentrantLock, LockNode> headUpdater = 
        AtomicReferenceFieldUpdater.<ReentrantLock, LockNode>newUpdater 
        (ReentrantLock.class,  LockNode.class, "head");
    private static final 
        AtomicIntegerFieldUpdater<LockNode> statusUpdater = 
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
     * Insert node into queue, initializing head and tail if necessary.
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
     * Main locking code for untimed locks that do not make it through
     * initial fast path. (The timed version is handled directly in
     * tryLock(timeout). It differs enough in detail to separate out.)
     * @param current current thread
     * @param node its wait-node, if it already exists; else null in
     * which case it is created,
     * @param interrupts interrupt control: -1 for abort on interrupt,
     * 0 for continue on interrupt, 1 for continue and thread was
     * already interrupted so needs re-interrupt on exit.
     * @return true if lock acquired (can be false only if interruptible)
     */
    private boolean waitForLock(Thread current, 
                                LockNode node, 
                                int interrupts) {
        /*
         * If node isn't null, it was already created during a
         * Condition await and then transfered from a condition queue.
         * Otherwise, first check for recursive hold and barging acquire,
         * and then if still need to wait, create node.
         */
        if (node == null) {
            Thread o = owner;
            if (o == current) { 
                ++recursions;
                return true;
            }
            if (o == null && !fair && ownerUpdater.acquire(this, current))
                return true;
            enq(node = new LockNode(current));
        }

        LockNode p = node.prev;         // our predecessor

        /*
         * Repeatedly try to get ownership if first in queue; block
         * (park) on failure.  If we are the first thread in queue, we
         * must try to get the lock, and we must not try to get lock
         * if we are not first. We can park only if we are sure that
         * some other thread holds lock and will signal us.
         *
         * Along the way, make sure that the predecessor hasn't been
         * cancelled. If it has, relink to its predecessor.
         *
         * When about to park, first try to set status enabling
         * lock-holder to signal, and then recheck one final time
         * before actually blocking. This also has effect of retrying
         * failed status CAS due to contention. 
         *
         * On success, set head, and null out the fields we are sure
         * will be unused in the future, for sake of GC.
         */ 

        for (;;) {
            if (p == head && owner == null &&
                ownerUpdater.acquire(this, current)) {
                p.next = null; 
                node.thread = null;
                node.prev = null; 
                head = node;
                if (interrupts > 0) // Re-interrupt on normal exit
                    current.interrupt();
                return true;
            }
            int status = p.status;
            if (status == 0) 
                statusUpdater.compareAndSet(p, 0, SIGNAL);
            else if (status == CANCELLED) 
                node.prev = p = p.prev;
            else { 
                assert (status == SIGNAL);
                LockSupport.park();
                if (Thread.interrupted()) {
                    if (interrupts < 0)  {  
                        cancelWaitForLock(node);
                        return false;
                    }
                    interrupts = 1; // set to re-interrupt on exit
                }
            }
        }
    }

    /**
     * Wake up node's successor, if one exists.
     * @param node the node
     */
    private void unparkSuccessor(LockNode node) {
        /*
         * Reset status before unparking. This improves performance
         * when called from unlock to release next thread: A given
         * head-node can be in effect across multiple unlocking owners
         * that acquired by barging. When they do so, and later
         * unlock, the successor that lost a previous race and
         * re-parked must be re-unparked. But otherwise, we'd like to
         * minimize unnecessary calls to unpark, which may be
         * relatively expensive. We don't bother to loop on failed CAS
         * here though, since the reset is just for performance.  Note
         * that the CAS will fail when this method is called from
         * cancelWaitForLock since status will be set to CANCELLED.
         * This doesn't occur frequently enough to bother avoiding.
         */
        statusUpdater.compareAndSet(node, SIGNAL, 0);

        /*
         * Successor is normally just the next node.  But if cancelled
         * or apparently null, traverse backwards from tail to find
         * the actual non-cancelled successor.
         */
        LockNode s = node.next;
        if ((s != null && s.status != CANCELLED) ||
            (s = findSuccessorFromTail(node)) != null)
            LockSupport.unpark(s.thread);
    }


    /**
     * Find the successor of a node, working backwards from the tail
     * @param node the node
     * @return successor, or null if there isn't one. 
     */
    private LockNode findSuccessorFromTail(LockNode node) {
        LockNode s = tail;
        if (s == null || s == node)
            return null;
        LockNode p = s.prev;
        for (;;) {
            if (p == null || p == node)
                return s;
            if (p.status != CANCELLED) 
                s = p; 
            p = p.prev;
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
            if (h != null && h.status < 0)
                unparkSuccessor(h);
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
        if (head != tail || !ownerUpdater.acquire(this, current))
            waitForLock(current, null, 0);
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
        // Fail if interrupted or if fastpath and slowpath both fail
        if (Thread.interrupted() ||
            ((head != tail || !ownerUpdater.acquire(this, current)) &&
             !waitForLock(current, null, -1)))
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
     * This &quot;barging&quot; behavior can be useful in certain 
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
     * @param timeout the time to wait for the lock
     * @param unit the time unit of the timeout argument
     *
     * @return <tt>true</tt> if the lock was free and was acquired by the
     * current thread, or the lock was already held by the current thread; and
     * <tt>false</tt> if the waiting time elapsed before the lock could be 
     * acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     * @throws NullPointerException if unit is null
     *
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null)
            throw new NullPointerException();
        if (Thread.interrupted())
            throw new InterruptedException();

        //  This is a variant of lockInterruptibly + waitForLock with
        //  interspersed timeout checks

        Thread current = Thread.currentThread();
        // Check fast path
        if (head == tail && ownerUpdater.acquire(this, current))
            return true;

        Thread o = owner;
        // Check for recursive hold
        if (o == current) { 
            ++recursions;
            return true;
        }

        // Retry fastpath if barging allowed
        if (o == null && !fair && ownerUpdater.acquire(this, current))
            return true;

        if (timeout <= 0L) 
            return false;
        
        long nanos = unit.toNanos(timeout);
        long lastTime = System.nanoTime();
        LockNode node = new LockNode(current);
        enq(node);
        LockNode p = node.prev;

        for (;;) {
            if (p == head && owner == null &&
                ownerUpdater.acquire(this, current)) {
                p.next = null; 
                node.thread = null;
                node.prev = null; 
                head = node;
                return true;
            }
            if (nanos <= 0L) {     
                cancelWaitForLock(node);
                return false;
            }

            int status = p.status;
            if (status == 0) 
                statusUpdater.compareAndSet(p, 0, SIGNAL);
            else if (status == CANCELLED) 
                node.prev = p = p.prev;
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
    }

    /**
     * Returns a {@link Condition} instance for use with this 
     * {@link Lock} instance.
     * @return the Condition object
     */
    public ConditionObject newCondition() {
        return new ConditionObject(this);
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
     * Returns an estimate of the number of threads waiting to acquire
     * this lock. The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring of the system state, not for synchronization
     * control.
     * @return the estimated number of threads waiting for this lock
     */
    public int getQueueLength() {
        int n = 0;
        for (LockNode p = tail; p != null && p != head; p = p.prev)
            ++n;
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire this lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (LockNode p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns the thread that currently owns the lock, or
     * <tt>null</tt> if not owned. Note that the owner may be
     * momentarily <tt>null</tt> even if there are threads trying to
     * acquire the lock but have not yet done so.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the owner, or <tt>null</tt> if not owned.
     */
    protected Thread getOwner() {
        return owner;
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
     * Re-acquire lock after a wait, resetting recursion count
     * and propagating interrupt status for reset control. 
     * @param current the waiting thread
     * @param node its node
     * @param recs number of recursive holds on lock before entering wait
     * @param interrupts interrupt control upon acquiring lock
     */
    final void relockAfterWait(Thread current, 
                               LockNode node, 
                               int recs,
                               int interrupts) {
        waitForLock(current, node, 0);
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
        if (c == CANCELLED || !statusUpdater.compareAndSet(p, c, SIGNAL))
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
        waitForLock(current, node, 0);
        recursions = recs;
        return isCancelled;
    }

    /**
     * Condition implementation for use with <tt>ReentrantLock</tt>.
     * Instances of this class can be constructed only using method
     * {@link ReentrantLock#newCondition}.
     * 
     * <p>This class supports the same basic semantics and styles of
     * usage as the {@link Object} monitor methods.  Methods may be
     * invoked only when holding the <tt>ReentrantLock</tt> associated
     * with this Condition. Failure to comply results in {@link
     * IllegalMonitorStateException}.
     *
     * <p>In addition to implementing the {@link Condition} interface,
     * this class defines methods <tt>hasWaiters</tt> and
     * <tt>getWaitQueueLength</tt>, as well as some associated
     * <tt>protected</tt> access methods, that may be useful for
     * instrumentation and monitoring.
     */

    public static class ConditionObject implements Condition, java.io.Serializable {

        private static final long serialVersionUID = 1173984872572414699L;

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

        /**
         * Constructor for use by subclasses to create a
         * ConditionObject associated with given lock.  (All other
         * construction should use the {@link
         * ReentrantLock#newCondition} method.)
         * @param lock the lock for this condition
         * @throws NullPointerException if lock null
         */
        protected ConditionObject(ReentrantLock lock) {
            if (lock == null)
                throw new NullPointerException();
            this.lock = lock;
        }

        // Internal methods

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
            lastWaiter = w;
            return w;
        }

        /**
         * Remove and transfer nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
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
         * Remove and transfer all nodes.
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
         * with this Condition is not held by the current thread
         **/
        public void signal() {
            lock.checkOwner(Thread.currentThread());
            ConditionNode w = firstWaiter;
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
         * with this Condition is not held by the current thread
         */
        public void signalAll() {
            lock.checkOwner(Thread.currentThread());
            ConditionNode w = firstWaiter;
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
         * three things happens: 
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
         * <li>A &quot;<em>spurious wakeup</em>&quot; occurs
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
         * with this Condition is not held by the current thread
         */
        public void awaitUninterruptibly() {
            Thread current = Thread.currentThread();
            lock.checkOwner(current);
            ConditionNode w = addConditionWaiter(current);
            int recs = lock.unlockForWait();
            int interrupted = 0;
            while (!lock.isOnLockQueue(w)) {
                LockSupport.park();
                if (Thread.interrupted()) 
                    interrupted = 1;
            }
            lock.relockAfterWait(current, w, recs, interrupted);
        }

        /**
         * Causes the current thread to wait until it is signalled or
         * {@link Thread#interrupt interrupted}.
         *
         * <p>The lock associated with this <tt>Condition</tt> is
         * atomically released and the current thread becomes disabled
         * for thread scheduling purposes and lies dormant until
         * <em>one</em> of four things happens:
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
         * <li>A &quot;<em>spurious wakeup</em>&quot; occurs
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
         * interrupted status is cleared.  This implementation favors
         * responding to an interrupt over normal method return in
         * response to a signal.
         *
         * @throws InterruptedException if the current thread is
         * interrupted
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         **/
        public void await() throws InterruptedException {
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
                    lock.relockAfterWait(current, w, recs, 0);
                    checkInterruptAfterRelock();
                    return;
                }
                LockSupport.park();
            }
        }

        /**
         * Causes the current thread to wait until it is signalled or
         * interrupted, or the specified waiting time elapses.
         *
         * <p>The lock associated with this condition is atomically
         * released and the current thread becomes disabled for thread
         * scheduling purposes and lies dormant until <em>one</em> of
         * five things happens:
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
         * <li>The specified waiting time elapses; or
         *
         * <li>A &quot;<em>spurious wakeup</em>&quot; occurs.
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
         * interrupted status is cleared.  This implementation favors
         * responding to an interrupt over normal method return in
         * response to a signal or timeout.
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
         * with this Condition is not held by the current thread
         */
        public long awaitNanos(long nanosTimeout) throws InterruptedException {
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
                if (nanosTimeout <= 0L) {
                    cancelAndRelock(current, w, recs);
                    return nanosTimeout;
                }
                if (lock.isOnLockQueue(w)) {
                    lock.relockAfterWait(current, w, recs, 0);
                    checkInterruptAfterRelock();
                    // We could have sat in lock queue a while, so
                    // recompute time left on way out.
                    return nanosTimeout - (System.nanoTime() - lastTime);
                }
                LockSupport.parkNanos(nanosTimeout);
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }
        }


        /**
         * Causes the current thread to wait until it is signalled or
         * interrupted, or the specified deadline elapses.
         *
         * <p>The lock associated with this condition is atomically
         * released and the current thread becomes disabled for thread
         * scheduling purposes and lies dormant until <em>one</em> of
         * five things happens:
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
         * <li>The specified deadline elapses; or
         *
         * <li>A &quot;<em>spurious wakeup</em>&quot; occurs.
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
         * interrupted status is cleared.  This implementation favors
         * responding to an interrupt over normal method return in
         * response to a signal or timeout.
         *
         * @param deadline the absolute time to wait until
         * @return <tt>false</tt> if the deadline has
         * elapsed upon return, else <tt>true</tt>.
         *
         * @throws InterruptedException if the current thread is interrupted
         * @throws IllegalMonitorStateException if the lock associated
         * with this Condition is not held by the current thread
         * @throws NullPointerException if deadline is null
         */
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            if (deadline == null)
                throw new NullPointerException();
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
                    lock.relockAfterWait(current, w, recs, 0);
                    checkInterruptAfterRelock();
                    return true;
                }
                LockSupport.parkUntil(abstime);
            }
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
         * with this Condition is not held by the current thread
         * @throws NullPointerException if unit is null
         */
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();
            return awaitNanos(unit.toNanos(time)) > 0L;
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
         * with this Condition is not held by the current thread
         */ 
        public boolean hasWaiters() {
            lock.checkOwner(Thread.currentThread());
            for (ConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.status == CONDITION)
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
         * with this Condition is not held by the current thread
         */ 
        public int getWaitQueueLength() {
            lock.checkOwner(Thread.currentThread());
            int n = 0;
            for (ConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.status == CONDITION)
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
         * with this Condition is not held by the current thread
         */
        protected Collection<Thread> getWaitingThreads() {
            lock.checkOwner(Thread.currentThread());
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (ConditionNode w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.status == CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
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

