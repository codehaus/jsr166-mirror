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
 * AbstractReentrantLock provides shared data structures and utility
 * methods for the reentrant lock classes in this package. It also
 * provides a common public {@link Condition} implementation, as well
 * as instrumentation methods. This class is not designed to be
 * subclassed outside of this package.
 *
 * @since 1.5
 * @author Doug Lea
 * 
 */
public abstract class AbstractReentrantLock {
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
     *    approach, checking the owner field before trying to CAS it,
     *    which avoids most failed CASes.
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

    /** 
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only by a thread upon acquiring
     * the lock. If head exists, its node status is guaranteed not to
     * be CANCELLED.
     */
    transient volatile Node head; 

    /** 
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    transient volatile Node tail; 

    /** Number of recursive acquires. Note: total holds = recursions+1 */
    transient int recursions;

    /** true if barging disabled */
    final boolean fair;

    // Atomics support

    static final 
        AtomicReferenceFieldUpdater<AbstractReentrantLock, Node> tailUpdater = 
        AtomicReferenceFieldUpdater.<AbstractReentrantLock, Node>newUpdater 
        (AbstractReentrantLock.class, Node.class, "tail");
    static final 
        AtomicReferenceFieldUpdater<AbstractReentrantLock, Node> headUpdater = 
        AtomicReferenceFieldUpdater.<AbstractReentrantLock, Node>newUpdater 
        (AbstractReentrantLock.class,  Node.class, "head");
    static final 
        AtomicIntegerFieldUpdater<Node> statusUpdater = 
        AtomicIntegerFieldUpdater.newUpdater 
        (Node.class, "status");

    /**
     * Creates an instance of <tt>AbstractReentrantLock</tt>.
     * This is equivalent to using <tt>AbstractReentrantLock(false)</tt>.
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

    /** As arg or node field, lock in write mode */
    static final int WRITER        =  0;
    /** As arg or node field, lock in read mode */
    static final int READER        =  1;
    /** As arg, don't interrupt; as result, was not interrupted */
    static final int UNINTERRUPTED =  0;
    /** As arg, allow interrupt; as result, was interrupted */ 
    static final int INTERRUPT     =  2;
    /** As arg, allow timeouts; as result, did time out */
    static final int TIMEOUT       =  4;
    /** As arg, reset interrupt status upon return */
    static final int REINTERRUPT   =  8;

    /**
     * Return true if mode denotes a read-lock
     * @param mode mode
     * @return true if read mode
     */
    boolean isReader(int mode) { return (mode & READER) != 0; }

    // abstract methods to acquire/release status

    /**
     * Try to atomically set lock status
     * @param mode lock mode
     * @param current current thread
     * @return true if successful
     */
    abstract boolean tryAcquire(int mode, Thread current);

    
    /**
     * Atomically release lock status
     * @param mode lock mode
     * @return true if lock is now free
     */
    abstract boolean tryRelease(int mode);

    /**
     * Try to recursively acquire or barge (if not fair) lock.
     * @param mode lock mode
     * @param current current thread
     * @return true if successful
     */
    abstract boolean tryInitialAcquire(int mode, Thread current);


    // Queuing utilities

    /**
     * Insert node into queue, initializing head and tail if necessary.
     * @param node the node to insert
     */
    void enq(Node node) {
        Node t = tail;
        if (t == null) {         // Must initialize first
            Node h = new Node(null);
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
     * Wake up node's successor, if one exists.
     * @param node the node
     */
    void unparkSuccessor(Node node) {
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
    int doLock(Thread current, int mode, long nanos) {
        if ((mode & INTERRUPT) != 0 && Thread.interrupted())
            return INTERRUPT;

        if (tryInitialAcquire(mode, current))
            return UNINTERRUPTED;

        long lastTime = ((mode & TIMEOUT) == 0)? 0 : System.nanoTime();

        Node node = new Node(current, mode & READER);
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
                if (isReader(mode) && node.status < 0) {
                    Node s = node.next; // wake up other readers
                    if (s == null || isReader(s.mode))
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
     * Re-acquire lock after a wait, resetting recursion count.
     * Assumes (does not check) that lock was, and will be, held in
     * write-mode.
     * @param current the waiting thread
     * @param node its node
     * @param recs number of recursive holds on lock before entering wait
     * @return true if interrupted while re-acquiring lock
     */
    boolean relock(Thread current, Node node, int recs) {
        boolean interrupted = false;
        for (;;) {
            Node p = node.prev; 
            if (p == head && tryAcquire(WRITER, current)) {
                recursions = recs;
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

    // Note: unlock() is done in subclasses since the chacks vary
    // across modes/types


    /**
     * Fully unlock the lock, setting recursions to zero.  Assumes
     * (does not check) that lock is held in write-mode.
     * @return current recursion count.
     */
    int fullyUnlock() {
        int recs = recursions;
        recursions = 0;
        if (tryRelease(WRITER)) {
            Node h = head;
            if (h != null && h.status < 0)
                unparkSuccessor(h);
        }
        return recs;
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
     * @return the collection of threads
     */
    Collection<Thread> getQueuedThreadsWithMode(int mode) {
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
     * Returns the thread that currently owns the lock, or
     * <tt>null</tt> if not owned. Note that the owner may be
     * momentarily <tt>null</tt> even if there are threads trying to
     * acquire the lock but have not yet done so.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the owner, or <tt>null</tt> if not owned.
     */
    protected abstract Thread getOwner();

    /**
     * Return true if a node, always one that was initially placed on
     * a condition queue, is now on the lock queue.
     * @param node the node
     * @return true if on lock queue
     */
    boolean isOnLockQueue(Node node) {
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
    boolean transferForSignal(Node node) {
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
    boolean transferAfterCancelledWait(Thread current, Node node) {
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

    /**
     * Condition implementation for use with <tt>AbstractReentrantLock</tt>.
     * Instances of this class can be constructed only using method
     * {@link Lock#newCondition}.
     * 
     * <p>This class supports the same basic semantics and styles of
     * usage as the {@link Object} monitor methods.  Methods may be
     * invoked only when holding the <tt>AbstractReentrantLock</tt> associated
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

        /** The lock we are serving as a condition for. */
        private final AbstractReentrantLock lock;
        /** First node of condition queue. */
        private transient Node firstWaiter;
        /** Last node of condition queue. */
        private transient Node lastWaiter;

        /**
         * Constructor for use by subclasses to create a
         * ConditionObject associated with given lock.  
         * @param lock the lock for this condition
         * @throws NullPointerException if lock null
         */
        protected ConditionObject(AbstractReentrantLock lock) {
            if (lock == null)
                throw new NullPointerException();
            this.lock = lock;
        }

        // Internal methods

        /**
         * Throw IllegalMonitorStateException if given thread is not owner
         * @param thread the thread
         * @throws IllegalMonitorStateException if thread not owner
         */
        void checkOwner(Thread thread) {
            if (lock.getOwner() != thread) 
                throw new IllegalMonitorStateException();
        }


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
            checkOwner(Thread.currentThread());
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
            checkOwner(Thread.currentThread());
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
            checkOwner(current);
            Node w = addConditionWaiter(current);
            int recs = lock.fullyUnlock();
            boolean interrupted = false;
            while (!lock.isOnLockQueue(w)) {
                LockSupport.park();
                if (Thread.interrupted()) 
                    interrupted = true;
            }
            if (lock.relock(current, w, recs))
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
            checkOwner(current);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int recs = lock.fullyUnlock();
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

            if (lock.relock(current, w, recs))
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
            checkOwner(current);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int recs = lock.fullyUnlock();
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

            if (lock.relock(current, w, recs))
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
            checkOwner(current);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int recs = lock.fullyUnlock();
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

            if (lock.relock(current, w, recs))
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
            checkOwner(current);
            if (Thread.interrupted()) 
                throw new InterruptedException();

            Node w = addConditionWaiter(current);
            int recs = lock.fullyUnlock();
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

            if (lock.relock(current, w, recs))
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
            checkOwner(Thread.currentThread());
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
            checkOwner(Thread.currentThread());
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
            checkOwner(Thread.currentThread());
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
