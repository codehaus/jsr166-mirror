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
 * Provides shared data structures, utility methods, base {@link
 * Condition} implementations, and instrumentation methods for the
 * reentrant lock classes defined in this package.  This class is not
 * designed to be directly subclassed outside of this
 * package. However, subclasses {@link ReentrantLock} and {@link
 * ReentrantReadWriteLock} may in turn be usefully extended.
 *
 * @since 1.5
 * @author Doug Lea
 * 
 */
public abstract class AbstractReentrantLock implements java.io.Serializable {
    /*
     *  General description and notes.
     *
     *  The basic idea, ignoring all sorts of things
     *  (reentrance, modes, cancellation, timeouts, error checking etc) is:
     *    Lock:
     *      if (atomically set lock status) // fastpath
     *        return;
     *      node = create and enq a wait node;
     *      for (;;) {
     *        if (node is first on queue) {
     *          if (atomically set lock status) 
     *             deq(node);
     *             return;
     *           }
     *        }
     *        park(currentThread);
     *      }
     *
     *    Unlock:
     *      atomically release lockStatus;
     *      h = first node on queue;
     *      if (h != null) unpark(h's successor's thread);
     *
     *  * The particular atomic actions needed in the Reentrant
     *    vs ReentrantReadWrite subclasses differ, but both
     *    follow the same basic logic. This base class contains
     *    the code that doesn't need to vary with respect to
     *    these. 
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
     *    deterministically fair. It is probabilistically fair in
     *    the sense that earlier queued threads are allowed to
     *    recontend before later queued threads, and each
     *    recontention has an unbiased chance to succeed against
     *    any incoming barging threads.
     *
     *  * Even non-fair locks don't do a bare atomic CAS in the
     *    fast path (except in tryLock). Instead, if the wait queue
     *    appears to be non-empty, they use a test-and-test-and-set
     *    approach,  which avoids most failed CASes.
     *
     *  * The "fair" variant differs only in that barging is disabled
     *    when there is contention, so locks proceed FIFO. There can be
     *    some races in detecting contention, but it is still FIFO from
     *    a definable (although complicated to describe) single point,
     *    so qualifies as a FIFO lock.
     *
     *  * While this lock never "spins" in the usual sense, it 
     *    perfroms multiple test-and-test-and sets (four in the most
     *    common case of a call from <tt>lock</tt>) interspersed with
     *    other computations before the first call to <tt>park</tt>.
     *    This gives most of the benefits of spins when locks are only
     *    briefly held without most of the liabilities when they
     *    aren't.
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
     *    lock if it is first in the queue. But being first does
     *    not guarantee the lock; it only gives the right to contend
     *    for it.  So the currently released
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
     *  * Threads waiting on Conditions use the same nodes, but
     *    use an additional link. Conditions only need to link nodes
     *    in simple (non-concurrent) linked queues because they are
     *    only accessed when lock is held.  Upon await, a node is
     *    inserted into a condition queue.  Upon signal, the node is
     *    transferred to the lock queue.  A special value of status
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

    /**
     * Serialization ID. Note that all fields are defined in a way so
     * that deserialized locks are in initial unlocked state, and
     * there is no explicit serialization code.
     */
    private static final long serialVersionUID = 7373984872572414691L;

    /** Node status value to indicate thread has cancelled */
    static final int CANCELLED =  1;
    /** Node status value to indicate thread needs unparking */
    static final int SIGNAL    = -1;
    /** Node status value to indicate thread is waiting on condition */
    static final int CONDITION = -2;

    /**
     * Node class for threads waiting for locks or conditions.  Rather
     * than using special node subtypes for r/w locks and conditions,
     * fields are declared that are only used for these purposes,
     * and ignored when not needed.
     */
    static final class Node {
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
        volatile int status;

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
        volatile Node prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon lock release. Assigned once during enqueuing,
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
         * Type of lock, used to distinguish readers from writers
         * in read-write locks
         */
        final int mode;
        
        /** 
         * Link to next node waiting on condition.  Because condition
         * queues are accessed only when locks are already held, we
         * just need a simple linked queue to hold nodes while they
         * are waiting on conditions. They are then transferred to the
         * lock queue to re-acquire locks.
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
            this.mode = 0;
        }

        Node(Thread thread, int mode) { 
            this.thread = thread; 
            this.mode = mode;
        }

        Node(Thread thread, int mode, int status) { 
            this.thread = thread; 
            this.mode = mode;
            this.status = status;
        }
    }

    /** true if barging disabled */
    private final boolean fair;

    /** 
     * Lock hold status is kept in a separate AtomicInteger.  It is
     * logically divided into two shorts: The lower one representing
     * the exclusive (write) lock hold count, and the upper the shared
     * hold count.
     */
    private final AtomicInteger count = new AtomicInteger();

    // shared vs write count extraction constants and functions

    static final int SHARED_SHIFT = 16;
    static final int SHARED_UNIT = (1 << SHARED_SHIFT);
    static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

    /**
     * Return true if count indicates lock is held in exclusive mode
     * @param c a lock status count
     * @return true if count indicates lock is held in exclusive mode
     */
    static final boolean isExclusive(int c) { return (c & EXCLUSIVE_MASK) != 0; }

    /**
     * Return the number of shared holds represented in count
     */
    static final int sharedCount(int c)  { return c >>> SHARED_SHIFT; }

    /**
     * Return the number of exclusive holds represented in count
     */
    static final int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }

    /** Return current shared count */
    final int getSharedCount() { return sharedCount(count.get()); }
    /** Return current exclusive count */
    final int getExclusiveCount() { return exclusiveCount(count.get()) ; }

    /** Current (exclusive) owner thread */
    private transient Thread owner;

    /** 
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only by a thread upon acquiring
     * the lock. If head exists, its node status is guaranteed not to
     * be CANCELLED.
     */
    private transient volatile Node head; 

    /** 
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile Node tail; 

    // Atomics support

    private static final 
        AtomicReferenceFieldUpdater<AbstractReentrantLock, Node> tailUpdater = 
        AtomicReferenceFieldUpdater.<AbstractReentrantLock, Node>newUpdater 
        (AbstractReentrantLock.class, Node.class, "tail");
    private static final 
        AtomicReferenceFieldUpdater<AbstractReentrantLock, Node> headUpdater = 
        AtomicReferenceFieldUpdater.<AbstractReentrantLock, Node>newUpdater 
        (AbstractReentrantLock.class,  Node.class, "head");
    static final 
        AtomicIntegerFieldUpdater<Node> statusUpdater = 
        AtomicIntegerFieldUpdater.newUpdater 
        (Node.class, "status");

    /**
     * Creates an instance of <tt>AbstractReentrantLock</tt> with 
     * non-fair fairness policy.
     */
    protected AbstractReentrantLock() { 
        fair = false;
    }

    /**
     * Creates an instance of <tt>AbstractReentrantLock</tt> with the
     * given fairness policy.
     */
    protected AbstractReentrantLock(boolean fair) { 
        this.fair = fair;
    }

    /*
     * Mode words are used to handle all of the combinations of r/w
     * interrupt, timeout, etc for lock methods.  These are OR'ed
     * together as appropriate for arguments, status fields, and
     * results.
     */

    /** As arg or node field, lock in exclusive mode */
    private static final int EXCLUSIVE     =  0;
    /** As arg or node field, lock in shared mode */
    private static final int SHARED        =  1;
    /** As arg, don't interrupt; as result, was not interrupted */
    private static final int UNINTERRUPTED =  0;
    /** As arg, allow interrupt; as result, was interrupted */ 
    private static final int INTERRUPT     =  2;
    /** As arg, allow timeouts; as result, did time out */
    private static final int TIMEOUT       =  4;
    /** As arg, reset interrupt status upon return */
    private static final int REINTERRUPT   =  8;
    /** As arg, don't acquire unowned lock unless unfair or queue is empty */
    private static final int CHECK_FAIRNESS = 16;

    /**
     * Return true if mode denotes a shared-lock
     * @param mode mode
     * @return true if shared mode
     */
    static final boolean isSharedMode(int mode) { return (mode & SHARED) != 0; }

    /**
     * Try to set lock status
     * @param mode lock mode
     * @param current current thread
     * @return true if successful
     */
    private boolean tryAcquire(int mode, Thread current) {
        final AtomicInteger count = this.count;
        boolean nobarge = (mode & CHECK_FAIRNESS) != 0 && fair;
        for (;;) {
            int c = count.get();
            int w = exclusiveCount(c);
            int r = sharedCount(c);
            if (isSharedMode(mode)) {
                if (w != 0 && current != owner)
                    return false;
                if (nobarge && head != tail)
                    return false;
                int nextc = c + SHARED_UNIT;
                if (nextc < c)
                    throw new Error("Maximum lock count exceeded");
                if (count.compareAndSet(c, nextc)) 
                    return true;
            }
            else {
                if (r != 0)
                    return false;
                if (w != 0) {
                    if (current != owner)
                        return false;
                    if (w + 1 >= SHARED_UNIT)
                        throw new Error("Maximum lock count exceeded");
                    if (count.compareAndSet(c, c + 1)) 
                        return true;
                }
                else {
                    if (nobarge && head != tail)
                        return false;
                    if (count.compareAndSet(c, c + 1)) {
                        owner = current;
                        return true;
                    }
                }
            }
            // Recheck count if lost any of the above CAS's
        }
    }
    
    // Queuing utilities

    /**
     * Initialize queue. Called on first contended lock attempt
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
         * when called from unlock to release next thread: A given
         * head-node can be in effect across multiple unlockers
         * that acquired by barging. When they do so, and later
         * unlock, the successor that lost a previous race and
         * re-parked must be re-unparked. But otherwise, we'd like to
         * minimize unnecessary calls to unpark, which may be
         * relatively expensive. We don't bother to loop on failed CAS
         * here though, since the reset is just for performance.  Note
         * that the CAS will fail when this method is called from
         * cancellation code since status will be set to CANCELLED.
         * This doesn't occur frequently enough to bother avoiding.
         */
        statusUpdater.compareAndSet(node, SIGNAL, 0);

        /*
         * Successor is normally just the next node.  But if cancelled
         * or apparently null, traverse backwards from tail to find
         * the actual non-cancelled successor.
         */
        Node s = node.next;
        if ((s != null && s.status != CANCELLED) ||
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
            if (p.status != CANCELLED) 
                s = p; 
            p = p.prev;
        }
    }

    /**
     * Main locking code.
     * @param mode mode representing r/w, interrupt, timeout control
     * @param nanos timeout time
     * @return UNINTERRUPTED on success, INTERRUPT on interrupt,
     * TIMEOUT on timeout
     */
    private int doLock(int mode, long nanos) {
        final Thread current = Thread.currentThread();

        if ((mode & INTERRUPT) != 0 && Thread.interrupted())
            return INTERRUPT;

        // Try initial barging or recursive acquire
        if (tryAcquire(mode | CHECK_FAIRNESS, current))
            return UNINTERRUPTED;

        long lastTime = ((mode & TIMEOUT) == 0)? 0 : System.nanoTime();

        final Node node = new Node(current, mode & SHARED);
        enq(node);

        /*
         * Repeatedly try to set lock status if first in queue; block
         * (park) on failure.  If we are the first thread in queue, we
         * must try to get the lock, and we must not try to get lock
         * if we are not first. We can park only if we are sure that
         * some other thread holds lock and will signal us.  Along the
         * way, make sure that the predecessor hasn't been
         * cancelled. If it has, relink to its predecessor.  When
         * about to park, first try to set status enabling lock-holder
         * to signal, and then recheck one final time before actually
         * blocking. This also has effect of retrying failed status
         * CAS due to contention.
         */ 

        for (;;) {
            Node p = node.prev; 
            if (p == head && tryAcquire(mode, current)) {
                p.next = null; 
                node.thread = null;
                node.prev = null; 
                head = node;
                if (isSharedMode(mode) && node.status < 0) {
                    Node s = node.next; // wake up other readers
                    if (s == null || isSharedMode(s.mode))
                        unparkSuccessor(node);
                }
                
                if ((mode & REINTERRUPT) != 0)
                    current.interrupt();
                return UNINTERRUPTED;
            }

            int status = p.status;
            if (status == 0) 
                statusUpdater.compareAndSet(p, 0, SIGNAL);
            else if (status == CANCELLED) 
                node.prev = p.prev;
            else {
                if ((mode & TIMEOUT) != 0) {
                    if (nanos > 0) {
                        long now = System.nanoTime();
                        nanos -= now - lastTime;
                        lastTime = now;
                    }
                    if (nanos <= 0) {
                        node.thread = null;   
                        node.status = CANCELLED;
                        unparkSuccessor(node);
                        return TIMEOUT;
                    }
                    else
                        LockSupport.parkNanos(nanos);
                }
                else
                    LockSupport.park();
                
                if (Thread.interrupted()) {
                    if ((mode & INTERRUPT) != 0)  {  
                        node.thread = null;   
                        node.status = CANCELLED;
                        unparkSuccessor(node);
                        return INTERRUPT;
                    }
                    else
                        mode |= REINTERRUPT;
                }
            }
        }
    }

    /**
     * Re-acquire lock after a wait, resetting lock count.
     * Assumes (does not check) that lock was, and will be, held in
     * exclusive-mode.
     * @param current the waiting thread
     * @param node its node
     * @param holds number of holds on lock before entering wait
     * @return true if interrupted while re-acquiring lock
     */
    boolean relock(Thread current, Node node, int holds) {
        boolean interrupted = false;
        for (;;) {
            Node p = node.prev; 
            if (p == head && tryAcquire(EXCLUSIVE, current)) {
                if (holds != 1)
                    count.set(holds);
                p.next = null; 
                node.thread = null;
                node.prev = null; 
                head = node;
                return interrupted;
            }
            
            int status = p.status;
            if (status == 0) 
                statusUpdater.compareAndSet(p, 0, SIGNAL);
            else if (status == CANCELLED) 
                node.prev = p.prev;
            else { 
                LockSupport.park();
                if (Thread.interrupted())
                    interrupted = true;
            }
        }
    }

    // Exportable versions of main lock/unlock methods

    /** Acquire shared lock */
    final void lockShared() {
        if (!fair || head == tail) {             // fast path
            final AtomicInteger count = this.count;
            int c = count.get();
            if (!isExclusive(c) && count.compareAndSet(c, c + SHARED_UNIT))
                return;
        }
        doLock(SHARED | UNINTERRUPTED, 0);
    }

    /** trylock for shared lock */
    final boolean tryLockShared() {
        final AtomicInteger count = this.count;
        int c = count.get();
        if (!isExclusive(c) && count.compareAndSet(c, c + SHARED_UNIT))
            return true;
        return tryAcquire(SHARED, Thread.currentThread());
    }

    /** Trylock for shared lock */
    final boolean tryLockShared(long timeout, TimeUnit unit) throws InterruptedException {
        int s = doLock(SHARED | INTERRUPT | TIMEOUT, unit.toNanos(timeout));
        if (s == UNINTERRUPTED)
            return true;
        if (s != INTERRUPT)
            return false;
        throw new InterruptedException();
    }

    /** Interruptibly lock shared lock */
    final void lockInterruptiblyShared() throws InterruptedException {
        if (doLock(SHARED | INTERRUPT, 0) == INTERRUPT)
            throw new InterruptedException();
    }


    /** Release shared lock */
    final void unlockShared() {
        final AtomicInteger count = this.count;
        for (;;) {
            int c = count.get();
            int nextc = c - SHARED_UNIT;
            if (nextc < 0)
                throw new IllegalMonitorStateException();
            if (count.compareAndSet(c, nextc)) {
                if (nextc == 0) {
                    Node h = head;
                    if (h != null && h.status < 0)
                        unparkSuccessor(h);
                }
                return;
            }
        }
    }

    /** Acquire exclusive (write) lock */
    final void lockExclusive() {
        if ((!fair || head == tail) && count.compareAndSet(0, 1)) 
            owner = Thread.currentThread();
        else
            doLock(UNINTERRUPTED, 0);
    }

    /** Trylock for exclusive (write) lock */
    final boolean tryLockExclusive() {
        if (count.compareAndSet(0, 1)) {
            owner = Thread.currentThread();
            return true;
        }
        return tryAcquire(0, Thread.currentThread());
    }

    /** Release exclusive (write) lock */
    final void unlockExclusive() {
        Node h;
        final AtomicInteger count = this.count;
        Thread current = Thread.currentThread();
        if (count.get() != 1 || current != owner) 
            slowUnlockExclusive(current);
        else {
            owner = null;
            count.set(0);
            if ((h = head) != null && h.status < 0)
                unparkSuccessor(h);
        }
    }

    /**
     * Handle uncommon cases for unlockExclusive
     * @param current current Thread
     */
    private void slowUnlockExclusive(Thread current) {
        Node h;
        int c = count.get();
        int w = exclusiveCount(c) - 1;
        if (w < 0 || owner != current)
            throw new IllegalMonitorStateException();
        if (w == 0) 
            owner = null;
        count.set(c - 1);
        if (w == 0 && (h = head) != null && h.status < 0)
            unparkSuccessor(h);
    }

    /** Trylock for exclusive (write) lock */
    final boolean tryLockExclusive(long timeout, TimeUnit unit) throws InterruptedException {
        int s = doLock(INTERRUPT | TIMEOUT, unit.toNanos(timeout));
        if (s == UNINTERRUPTED)
            return true;
        if (s != INTERRUPT)
            return false;
        throw new InterruptedException();
    }

    /** Interruptible lock exclusive (write) lock */
    final void lockInterruptiblyExclusive() throws InterruptedException { 
        if (doLock(INTERRUPT, 0) == INTERRUPT)
            throw new InterruptedException();
    }

    /**
     * Fully unlock the lock, setting lock holds to zero.  Assumes
     * (does not check) that lock is held in exclusive-mode.
     * @return current hold count.
     */
    final int fullyUnlock() {
        int holds = count.get();
        owner = null;
        count.set(0);
        Node h = head;
        if (h != null && h.status < 0)
            unparkSuccessor(h);
        return holds;
    }

    // Instrumentation and status

    /**
     * Return true if this lock has fairness set true.
     * @return true if this lock has fairness set true.
     */
    public boolean isFair() {
        return fair;
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
        for (Node p = tail; p != null && p != head; p = p.prev)
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
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire lock with given mode. 
     * @param shared true if shared mode, else exclusive
     * @return the collection of threads
     */
    Collection<Thread> getQueuedThreads(boolean shared) {
        int mode = shared? SHARED : EXCLUSIVE;
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.mode == mode) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns the thread that currently owns the exclusive lock, or
     * <tt>null</tt> if not owned. Note that the owner may be
     * momentarily <tt>null</tt> even if there are threads trying to
     * acquire the lock but have not yet done so.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the owner, or <tt>null</tt> if not owned.
     */
    protected Thread getOwner() {
        return (isExclusive(count.get()))? owner : null;
    }

    /**
     * Throw IllegalMonitorStateException if given thread is not owner
     * @param thread the thread
     * @throws IllegalMonitorStateException if thread not owner
     */
    final void checkOwner(Thread thread) {
        if (!isExclusive(count.get()) || owner != thread) 
            throw new IllegalMonitorStateException();
    }

    /**
     * Throw IllegalMonitorStateException if given thread cannot
     * wait on condition
     * @param thread the thread
     * @throws IllegalMonitorStateException if thread cannot wait
     */
    final void checkOwnerForWait(Thread thread) {
        // Waiters cannot hold shared locks
        if (count.get() != 1 || owner != thread) 
            throw new IllegalMonitorStateException();
    }

    /**
     * Return true if a node, always one that was initially placed on
     * a condition queue, is now on the lock queue.
     * @param node the node
     * @return true if on lock queue
     */
    final boolean isOnLockQueue(Node node) {
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
     * Transfer a node from a condition queue onto lock queue. 
     * Return true if successful.
     * @param node the node
     * @return true if succesfully transferred (else the node was
     * cancelled before signal).
     */
    final boolean transferForSignal(Node node) {
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
        Node p = node.prev;
        int c = p.status;
        if (c == CANCELLED || !statusUpdater.compareAndSet(p, c, SIGNAL))
            LockSupport.unpark(node.thread);

        return true;
    }

    /**
     * Transfer node, if necessary, to lock queue after a cancelled
     * wait. Return true if thread was cancelled before being
     * signalled.
     * @param current the waiting thread
     * @param node its node
     * @return true if cancelled before the node was signalled.
     */
    final boolean transferAfterCancelledWait(Thread current, Node node) {
        if (statusUpdater.compareAndSet(node, CONDITION, 0)) {
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
            while (!isOnLockQueue(node)) 
                Thread.yield();
            return false;
        }
    }

    // Serialization support

    /**
     * Reconstitute this lock instance from a stream (that is,
     * deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        count.set(0); // reset to unlocked state
    }

    /**
     * Condition implementation for use with <tt>AbstractReentrantLock</tt>.
     * Instances of this class can be constructed only by subclasses.
     *
     * <p>In addition to implementing the {@link Condition} interface,
     * this class defines methods <tt>hasWaiters</tt> and
     * <tt>getWaitQueueLength</tt>, as well as associated
     * <tt>protected</tt> access methods that may be useful for
     * instrumentation and monitoring.
     */
    protected static class AbstractConditionObject implements Condition, java.io.Serializable {

        private static final long serialVersionUID = 1173984872572414699L;

        /** The lock we are serving as a condition for. */
        private final AbstractReentrantLock lock;
        /** First node of condition queue. */
        private transient Node firstWaiter;
        /** Last node of condition queue. */
        private transient Node lastWaiter;

        /**
         * Constructor for use by subclasses to create a
         * AbstractConditionObject associated with given lock.  
         * @param lock the lock for this condition
         * @throws NullPointerException if lock null
         */
        protected AbstractConditionObject(AbstractReentrantLock lock) {
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
        private Node addConditionWaiter(Thread current) {
            Node w = new Node(current, 0, CONDITION);
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
            } while (!lock.transferForSignal(first) &&
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
                lock.transferForSignal(first);
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
         * with this Condition is not held by the current thread
         **/
        public void signal() {
            lock.checkOwner(Thread.currentThread());
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
         * with this Condition is not held by the current thread
         */
        public void signalAll() {
            lock.checkOwner(Thread.currentThread());
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
         * with this Condition is not held by the current thread
         */
        public void awaitUninterruptibly() {
            Thread current = Thread.currentThread();
            lock.checkOwnerForWait(current);
            Node w = addConditionWaiter(current);
            int holds = lock.fullyUnlock();
            boolean interrupted = false;
            while (!lock.isOnLockQueue(w)) {
                LockSupport.park();
                if (Thread.interrupted()) 
                    interrupted = true;
            }
            if (lock.relock(current, w, holds))
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
         * with this Condition is not held by the current thread
         **/
        public void await() throws InterruptedException {
            Thread current = Thread.currentThread();
            lock.checkOwnerForWait(current);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int holds = lock.fullyUnlock();
            boolean throwIE = false;
            boolean interrupted = false;

            for (;;) {
                if (Thread.interrupted()) {
                    if (lock.transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (lock.isOnLockQueue(w)) 
                    break;
                LockSupport.park();
            }

            if (lock.relock(current, w, holds))
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
         * with this Condition is not held by the current thread
         */
        public long awaitNanos(long nanosTimeout) throws InterruptedException {
            Thread current = Thread.currentThread();
            lock.checkOwnerForWait(current);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int holds = lock.fullyUnlock();
            long lastTime = System.nanoTime();
            boolean throwIE = false;
            boolean interrupted = false;

            for (;;) {
                if (Thread.interrupted()) {
                    if (lock.transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (nanosTimeout <= 0L) {
                    lock.transferAfterCancelledWait(current, w); 
                    break;
                }
                if (lock.isOnLockQueue(w)) 
                    break;
                LockSupport.parkNanos(nanosTimeout);
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }

            if (lock.relock(current, w, holds))
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
         * with this Condition is not held by the current thread
         * @throws NullPointerException if deadline is null
         */
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            if (deadline == null)
                throw new NullPointerException();
            Thread current = Thread.currentThread();
            lock.checkOwnerForWait(current);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int holds = lock.fullyUnlock();
            long abstime = deadline.getTime();
            boolean timedout = false;
            boolean throwIE = false;
            boolean interrupted = false;
            
            for (;;) {
                if (Thread.interrupted()) {
                    if (lock.transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (System.currentTimeMillis() > abstime) {
                    timedout = lock.transferAfterCancelledWait(current, w); 
                    break;
                }
                if (lock.isOnLockQueue(w)) 
                    break;
                LockSupport.parkUntil(abstime);
            }

            if (lock.relock(current, w, holds))
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
         * with this Condition is not held by the current thread
         * @throws NullPointerException if unit is null
         */
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();

            long nanosTimeout = unit.toNanos(time);
            Thread current = Thread.currentThread();
            lock.checkOwnerForWait(current);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int holds = lock.fullyUnlock();
            long lastTime = System.nanoTime();
            boolean timedout = false;
            boolean throwIE = false;
            boolean interrupted = false;

            for (;;) {
                if (Thread.interrupted()) {
                    if (lock.transferAfterCancelledWait(current, w))
                        throwIE = true;
                    else
                        interrupted = true;
                    break;
                }
                if (nanosTimeout <= 0L) {
                    timedout = lock.transferAfterCancelledWait(current, w); 
                    break;
                }
                if (lock.isOnLockQueue(w)) 
                    break;
                LockSupport.parkNanos(nanosTimeout);
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }

            if (lock.relock(current, w, holds))
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
         * with this Condition is not held by the current thread
         */ 
        public boolean hasWaiters() {
            lock.checkOwner(Thread.currentThread());
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
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
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
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
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.status == CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }
}
