/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.atomic.*;
import java.util.Date;

/**
 * A reentrant mutual exclusion lock.  
 * <p><tt>ReentrantLock</tt> defines a stand-alone {@link Lock} class with 
 * the same basic behavior and semantics as the implicit
 * monitor lock accessed by the use of <tt>synchronized</tt> methods and
 * statements, but without the forced block-structured locking and unlocking
 * that occurs with <tt>synchronized</tt> methods and
 * statements. 
 *
 * <p>The order in which blocked threads are granted the lock is not
 * specified.  
 *
 * <p>If you want a non-reentrant mutual exclusion lock then it is a simple
 * matter to use a reentrant lock in a non-reentrant way by ensuring that
 * the lock is not held by the current thread prior to locking.
 * See {@link #getHoldCount} for a way to check this.
 *
 *
 * <p><code>ReentrantLock</code> instances are intended to be used primarily 
 * in before/after constructions such as:
 *
 * <pre>
 * class X {
 *   ReentrantLock lock;
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
 * <p>This class supports the interruption of lock acquisition and provides a 
 * {@link #newCondition Condition} implementation that supports the 
 * interruption of thread suspension.
 *
 * <p>Except where noted, passing a <tt>null</tt> value for any parameter 
 * will result in a {@link NullPointerException} being thrown.
 *
 * <h3>Implementation Notes</h3>
 * <p> To-BE_DONE
 *
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/05/27 18:14:40 $
 * @editor $Author: dl $
 * @author Doug Lea
 * 
 * @fixme (1) We need a non-nested example to motivate this
 * @fixme (2) Describe performance aspects of interruptible locking for the RI
 **/
public class ReentrantLock implements Lock, java.io.Serializable {
    /*
      This is a fastpath/slowpath algorithm.

      * The basic algorithm looks like this, ignoring reentrance,
        cancellation, timeouts, error checking etc:
        Lock:
          if (!fair && casOwner(null, currentThread)) // fastpath
            return;
          node = create and enq a wait node;
          for (;;) {
            if (node is first on queue) {
              if (casOwner(null, currentThread)) {
                 deq(node);
                 return;
               }
            }
            park(currentThread);
          }

        Unlock:
          owner = null;      // volatile assignment
          h = first node on queue;
          if (h != null) unpark(h's successor's thread);

      * The fast path uses one atomic CAS operation, plus one
        StoreLoad barrier per lock/unlock pair.  The "owner" field is
        handled as a simple spinlock.  To lock, the owner field is set
        to current thread using conditional atomic update.  To unlock,
        the owner field is set to null, checking if anyone needs
        waking up, if so doing so.  Recursive locks/unlocks instead
        increment/decrement recursion field.

      * By default, contended locks use a kind of "greedy" /
        "renouncement" / barging / convoy-avoidance strategy: When a
        lock is released, a waiting thread is signalled so that it can
        (re)contend for the lock. It might lose and thus need to
        rewait. This strategy has much higher throughput than
        "directed handoff" because it reduces blocking of running
        threads, but poorer fairness.  The wait queue is FIFO, but
        newly entering threads can barge ahead and grab lock before
        woken waiters, so acquires are not strictly FIFO, and transfer
        is not deterministically fair. It is probablistically fair
        though. Earlier queued threads are allowed to recontend before
        later queued threads, and each recontention has an unbiased
        chance to succeed.

      * The base class also sets up support for FairReentrantLock
        subclass, that differs only in that barging is disabled when
        there is contention, so locks proceed FIFO. There can be
        some races in detecting contention, but it is still FIFO from
        a definable (although complicated to describe) single point,
        so qualifies as a FIFO lock.

      * The wait queue is a variant of a "CLH" (Craig, Landin, and
        Hagersten) lock. CLH locks are normally used for spinlocks.
        We instead use them for blocking locks, but use the same basic
        tactic of holding the information about whether a thread is
        released (i.e, eligible to contend for ownership lock) in the
        predecessor of each node.  A node waits until its predecessor
        says it is released. It is signalled when its predecessor
        releases the lock. Each node of the queue otherwise serves as
        a specific-notification-style monitor holding a single waiting
        thread.

        To enqueue into a CLH lock, you atomically splice it in as new
        tail. To dequeue, you just set the head field.  
        
             +------+  prev +-----+       +-----+
        head |      | <---- |     | <---- |     |  tail
             +------+       +-----+       +-----+

        The great thing about CLH Locks is that insertion into a CLH
        queue requires only a single atomic operation on "tail", so
        there is a simple atomic point of demarcation from unqueued to
        queued. Similarly, dequeing involves only updating the
        "head". However, it takes a bit more work for nodes to
        determine who their successors are, in part to deal with
        possible cancellation due to timeouts and interrupts.

        The "prev" links (not used in original CLH locks), are mainly
        needed to handle cancellation. If a node is cancelled, its
        successor must be relinked to a non-cancelled predecessor. For
        explanation in the case of spin locks, see the papers by Scott
        & Scherer at
        http://www.cs.rochester.edu/u/scott/synchronization/
        
        Being first in the queue does not mean that you have the lock,
        only that you may contend for it (by CAS'ing owner field).  So
        the currently released contender thread may need to rewait.

        We also use "next" links to implement blocking mechanics.  The
        thread id for each node is kept in its node, so a predecessor
        signals the next node to wake up by traversing next link to
        determine which thread it is.  Determination of successor must
        avoid races with newly queued nodes to set the "next" fields
        of their predecessors.  This is solved by checking backwards
        from the atomically updated "tail" when a node's successor
        appears to be null.

        Cancellation introduces some conservatism to the basic
        algorithm of these.  Since we must poll for cancellation of
        other nodes, we can miss noticing whether a cancelled node is
        ahead or behind us. This is dealt with by always unparking
        successors upon cancellation, and not letting them park again
        (by saturating release counts) until they stabilize on a new
        predecessor.

      * Threads waiting on Conditions use the same kind of nodes, but
        only need to link them in simple (non-concurrent) linked
        queues because they are only accessed when lock is held.  To
        wait, a thread makes a node inserted into a condition queue.
        Upon signal, the node is transferred to the lock queue.
        Special values of count fields are used to mark which queue a
        node is on.

      * All suspension and resumption of threads uses the JSR166
        native park/unpark API. These are safe versions of
        suspend/resume (plus timeout support) that internally avoid
        the intrinsic race problems with suspend/resume: Park suspends
        if not preceded by an unpark. Unpark resumes if waiting, else
        causes next park not to suspend. While safe and efficient,
        these are not general-purpose public operations because we do
        not want code outside this package to randomly call these
        methods -- parks and unparks should be matched up. (It is OK
        to have more unparks than unparks, but it causes threads to
        spuriously wake up. So minimizing excessive unparks is a
        performance concern.)
    */

    /*
      Note that all fields are transient and defined in a way that
      deserialized locks are in initial unlocked state.
    */

    /**
     * Creates an instance of <tt>ReentrantLock</tt>.
     */
    public ReentrantLock() { }

    /** 
     * Current owner of lock, or null iff the lock is free.  Acquired
     * only using CAS.
     */
    private transient volatile Thread owner;

    /** Number of recursive acquires. Note: total holds = recursions+1 */
    private transient int recursions;

    /** Head of the wait queue, initialized to dummy node */
    private transient volatile WaitNode head = new WaitNode(null);

    /** Tail of the wait queue, initialized  to be same node as head */
    private transient volatile WaitNode tail = head;

    // Atomics support

    private final static AtomicReferenceFieldUpdater<ReentrantLock, Thread>  ownerUpdater = new AtomicReferenceFieldUpdater<ReentrantLock, Thread>(new ReentrantLock[0], new Thread[0], "owner");
    private final static AtomicReferenceFieldUpdater<ReentrantLock, WaitNode> tailUpdater = new AtomicReferenceFieldUpdater<ReentrantLock, WaitNode>(new ReentrantLock[0], new WaitNode[0], "tail");
    private final static AtomicReferenceFieldUpdater<ReentrantLock, WaitNode>  headUpdater = new AtomicReferenceFieldUpdater<ReentrantLock, WaitNode>(new ReentrantLock[0], new WaitNode[0], "head");
    private final static AtomicIntegerFieldUpdater<WaitNode> countUpdater = 
        new AtomicIntegerFieldUpdater<WaitNode>(new WaitNode[0], "count");

    private boolean acquireOwner(Thread current) {
        return ownerUpdater.compareAndSet(this, null, current);
    }

    private boolean casTail(WaitNode cmp, WaitNode val) {
        return tailUpdater.compareAndSet(this, cmp, val);
    }

    private boolean casHead(WaitNode cmp, WaitNode val) {
        return headUpdater.compareAndSet(this, cmp, val);
    }

    // casCount non-private because also accessed by Conditions
    final boolean casCount(WaitNode node, int cmp, int val) {
        return countUpdater.compareAndSet(node, cmp, val);
    }

    /**
     * Node class for threads waiting for locks.
     */
    static final class WaitNode {
        /**
         * Count controlling whether successor node is allowed to try
         * to obtain ownership. Acts as a saturating (in both
         * directions) counting semaphore: Upon each wait, the count
         * is reduced to zero if positive, else reduced to negative,
         * in which case the thread will park. The count is
         * incremented on each unlock that would enable successor
         * thread to obtain lock (succeeding if there is no
         * contention). The special value of CANCELLED is used to mean
         * that the count cannot be either incremented or decremented.
         * The special value of ON_CONDITION_QUEUE is used when nodes
         * are on conditions queues instead of lock queue, and the
         * special value TRANSFERRING is used while signals are in
         * progress.
         */
        volatile int count;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking release count. Assigned once during enqueing,
         * and nulled out (for sake of GC) only upon dequeuing.  Upon
         * cancellation, we do NOT adjust this field, but simply
         * traverse through prev's until we hit a non-cancelled node.
         * A valid predecessor will always exist because the head node
         * is never cancelled.
         */
        volatile WaitNode prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon lock release. Assigned once during enquing.
         * Upon cancellation, we do NOT adjust this field, but simply
         * traverse through next's until we hit a non-cancelled node,
         * (or null if at end of queue).  The enqueue operation does
         * not assign next field of a predecessor until after
         * attachment, so seeing a null next field not necessarily
         * mean that node is at end of queue. However, if a next field
         * appears to be null, we can scan prev's from the tail to
         * double-check.
         */
        volatile WaitNode next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.  Note that this need
         * not be declared volatile since it is always accessed after
         * traversing volatile links, and written before writing
         * links.
         */
        Thread thread;

        WaitNode(Thread t) { thread = t; }
    }

    /**
     * Special value for release counts indicating that node is cancelled.
     * Must be a large positive number.
     */
    static private final int CANCELLED = Integer.MAX_VALUE;

    /**
     * Special value for node counts indicating that node is on a
     * condition queue. Must be large negative number.
     */
    static private final int ON_CONDITION_QUEUE = Integer.MIN_VALUE;

    /**
     * Special value for node counts indicating that node is in
     * process of transfer. Must be negative and greater than
     * ON_CONDITION_QUEUE.
     */
    static private final int TRANSFERRING = ON_CONDITION_QUEUE+1;


    /**
     * Utility to throw an exception if lock not held by given thread.
     */
    final void checkOwner(Thread current) {
        if (current != owner)
            throw new IllegalMonitorStateException();
    }

    /**
     * Return whther lock wait queue is empty
     */
    final boolean queueEmpty() {
        WaitNode h = head; // force order of the volatile reads
        return h == tail; 
    }

    /**
     * Insert node into queue. Return predecessor.
     */
    private WaitNode enq(WaitNode node) {
        for (;;) { 
            WaitNode p = tail;
            node.prev = p;        // prev must be valid before/upon CAS
            if (casTail(p, node)) {
                p.next = node;    // Note: next field assignment lags CAS
                return p;
            }
        }
    }

    /**
     * Return true if it is OK to take fast path to lock.
     * Overridden in FairReentrantLock.
     */
    boolean canBarge() {
        return true;
    }

    /*
     * policy/status values for main lock routine. A bit ugly but
     * worth it to factor the common lock code across the different
     * policies for suppressing or throwing IE and indicating
     * timeouts.
     */

    /** 
     * As param, suppress interrupts.
     * As return, lock was successfully obtained.
     */
    static final int NO_INTERRUPT = 0;

    /** 
     * As param, allow interrupts.
     * As return, lock was not obtained because thread was interrupted.
     */
    static final int INTERRUPT = -1;

    
    /** 
     * As param, use timed waits. 
     * As return, lock was not obtained because timeout was exceeded.
     */
    static final int TIMEOUT = -2;

    /**
     * Main locking code, parameterized across different policies.
     * @param current current thread
     * @param node its wait-node, it is alread exists; else null in
     * which case it is created,
     * @param policy -- NO_INTERRUPT, INTERRUPT, or TIMEOUT
     * thread interrupted
     * @param nanos time to wait, or zero if untimed
     * @return NO_INTERRUPT on success, else INTERRUPT, or TIMEOUT
     */
    final int doLock(Thread current, 
                     WaitNode node, 
                     int policy,
                     long nanos) {

        int status = NO_INTERRUPT;

        /*
         * Bypass queueing if a recursive lock
         */
        if (owner == current) {
            ++recursions;
            return status;
        }


        long lastTime = 0; // for adjusting timeouts, below

        /*
         * p is our predecessor node, that holds release count giving
         * permission to try to obtain lock if we are first in queue.
         */
        WaitNode p;

        /*
         * Create and enqueue node if not already created. Nodes
         * transferred from condition queues will already be created
         * and queued.
         */
        if (node == null) {
            node = new WaitNode(current);
            p = enq(node);
        }
        else 
            p = node.prev;

        /*
         * Repeatedly try to get ownership if first in queue, else
         * block.
         */
        for (;;) {
            /*
             * If we are the first thread in queue, try to get the
             * lock.  (We must not try to get lock if we are not
             * first.) Note that if we become first after p == head
             * check, all is well -- we can be sure an unlocking
             * thread will signal us.  
             */

            if (p == head && acquireOwner(current)) {
                /*
                 * status can be INTERRUPT here if policy is
                 * NO_INTERRUPT but thread was interrupted while
                 * waiting, in which case we must reset status upon
                 * return.
                 */
                if (status == INTERRUPT) 
                    current.interrupt();

                p.next = null;       // clear for GC and to suppress signals
                node.thread = null;  
                node.prev = null;    
                head = node;
                return NO_INTERRUPT;
            }

            int count = p.count;
            
            /*
             * If our predecessor was cancelled, use its predecessor.
             * There will always be a non-cancelled one somewhere
             * because head node is never cancelled, so at worst we
             * will hit it. (Note that because head is never
             * cancelled, we can perform this check after trying to
             * acquire ownership).
             */
            if (count == CANCELLED) {
                node.prev = p = p.prev;
            }

            /*
             * Wait if we are not not first in queue, or if we are
             * first, we have tried to acquire owner and failed since
             * either entry or last release.  (Note that count can
             * already be less than zero if we spuriously returned
             * from a previous park or got new a predecessor due to
             * cancellation.)
             *
             * We also don't wait if atomic decrement of release count
             * fails. We just continue main loop on failure to
             * atomically update release count because interference
             * causing failure is almost surely due to someone
             * releasing us anyway.
             *
             * Each wait consumes all available releases. Normally
             * there is only one anyway because release doesn't bother
             * incrementing if already positive.
             *
             */
            else if (casCount(p, count, (count > 0)? 0 : -1) && count <= 0) {

                // Update and check timeout value
                if (nanos > 0) { 
                    long now = TimeUnit.nanoTime();
                    if (lastTime != 0) {
                        nanos -= now - lastTime;
                        if (nanos <= 0) 
                            status = TIMEOUT;
                    }
                    lastTime = now;
                }
                
                if (status != TIMEOUT)
                    JSR166Support.park(false, nanos);
                
                if (Thread.interrupted()) 
                    status = INTERRUPT;

                if (status != NO_INTERRUPT && policy != NO_INTERRUPT) {
                    node.thread = null;      // disable signals
                    countUpdater.set(node, CANCELLED);  // don't need CAS here
                    signalSuccessor(node);   
                    return status;
                }
            }
        }
    }

    /**
     * Wake up node's successor, if one exists
     */
    private void signalSuccessor(WaitNode node) {
        /*
         * Find successor -- normally just node.next.
         */
        WaitNode s = node.next;

        /*
         * if s is cancelled, traverse through next's. 
         */

        while (s != null && s.count == CANCELLED) {
            node = s;
            s = s.next;
        }
            
        /*
         * If successor appears to be null, check to see if a newly
         * queued node is successor by starting at tail and working
         * backwards. If so, help out the enqueing thread by setting
         * next field. We don't expect this loop to trigger often, 
         * and hardly ever to iterate.
         */
        
        if (s == null) {
            WaitNode t = tail;
            for (;;) {
                /* 
                 * If t == node, there is no successor.  
                 */
                if (t == node) 
                    return;

                WaitNode tp = t.prev;

                /* 
                 * t's predecessor is null if we are lagging so far
                 * behind the actions of other nodes/threads that an
                 * intervening head.prev was nulled by some
                 * non-cancelled successor of node. In which case,
                 * there's no live successor.
                 */

                if (tp == null)
                    return;

                /* 
                 * If we find successor, we can do the assignment to
                 * next (don't even need CAS) on behalf of enqueuing
                 * thread. At worst we will stall now and lag behind
                 * both the setting and the later clearing of next
                 * field. But if so, we will reattach an internal link
                 * in soon-to-be unreachable set of nodes, so no harm
                 * done.
                 */
                
                if (tp == node) {
                    node.next = s = t;
                    break;
                }

                t = tp; 

                /*
                 * Before iterating, check to see if link has
                 * appeared.
                 */
                WaitNode n = node.next;
                if (n != null) {
                    s = n;
                    break;
                }
            }
        }

        Thread thr = s.thread;
        if (thr != null && thr != owner) // don't bother signalling if has lock
            JSR166Support.unpark(thr);
    }


    /**
     * Increment release count and signal next thread in queue if one
     * exists and is waiting. Called only by unlock. This code is split
     * out from unlock to encourage inlining of non-contended cases.
     * @param h (nonnull) current head of lock queue (reread upon CAS
     * failure).
     */
    private void releaseFirst(WaitNode h) {
        for (;;) {
            int c = h.count;
            if (c > 0)         // Don't need signal if already positive
                return;
            if (owner != null) // Don't bother if some thread got lock
                return;
            if (casCount(h, c, (c < 0)? 0 : 1)) { // saturate count at 1
                if (c < 0) 
                    signalSuccessor(h);
                return;
            }
            h = head;
            if (h == tail)    // No successor
                return;
        }
    }

    /**
     * Attempts to release this lock.  <p>If the current thread is the
     * holder of this lock then the hold count is decremented. If the
     * hold count is now zero then the lock is released.  <p>If the
     * current thread is not the holder of this lock then {@link
     * IllegalMonitorStateException} is thrown.
     * @throws IllegalMonitorStateException if the current thread does not
     * hold this lock.
     */
    public void unlock() {
        checkOwner(Thread.currentThread());

        if (recursions > 0) {
            --recursions;
            return;
        }

        ownerUpdater.set(this, null);
        WaitNode h = head;
        if (h != tail) 
            releaseFirst(h);
    }

    /**
     * Acquire the lock. 
     * <p>Acquires the lock if it is not held be another thread and returns 
     * immediately, setting the lock hold count to one.
     * <p>If the current thread
     * already holds the lock then the hold count is incremented by one and
     * the method returns immediately.
     * <p>If the lock is held by another thread then the
     * the current thread thread becomes disabled for thread scheduling 
     * purposes and lies dormant until the lock has been acquired
     * at which time the lock hold count is set to one. 
     */
    public void lock() {
        Thread current = Thread.currentThread();
        if (canBarge() && acquireOwner(current)) 
            return;
        int stat = doLock(current, null, NO_INTERRUPT, 0);
        //        assert stat == NO_INTERRUPT;
    }

    /**
     * Acquires the lock unless the current thread is 
     * {@link Thread#interrupt interrupted}.
     * <p>Acquires the lock if it is not held by another thread and returns 
     * immediately, setting the lock hold count to one.
     * <p>If the current thread already holds this lock then the hold count 
     * is incremented by one and the method returns immediately.
     * <p>If the lock is held by another thread then the
     * the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of two things happens:
     * <ul>
     * <li> The lock is acquired by the current thread; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     * <p>If the lock is acquired by the current thread then the lock hold 
     * count is set to one.
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or 
     * <li>is {@link Thread#interrupt interrupted} while waiting to acquire 
     * the lock,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. As this method is an explicit
     * interruption point, preference is given to responding to the interrupt
     * over reentrant acquisition of the lock.
     *
     * <h3>Implementation Notes</h3>
     * <p> To-BE_DONE
     *
     * @throws InterruptedException if the current thread is interrupted
     */
    public void lockInterruptibly() throws InterruptedException { 
        Thread current = Thread.currentThread();
        if (Thread.interrupted()) 
            throw new InterruptedException();
        if (canBarge() && acquireOwner(current)) 
            return;
        if (doLock(current, null, INTERRUPT, 0) == INTERRUPT)
            throw new InterruptedException();
    }

    /**
     * Acquires the lock only if it is not held by another thread at the time
     * of invocation.
     * <p>Acquires the lock if it is not held by another thread and returns 
     * immediately with the value <tt>true</tt>, setting the lock hold count 
     * to one.
     * <p> If the current thread
     * already holds this lock then the hold count is incremented by one and
     * the method returns <tt>true</code>.
     * <p>If the lock is held by another thread then this method will return 
     * immediately with the value <tt>false</tt>.
     *
     * @return <tt>true</tt>if the lock was free and was acquired by the
     * current thread, or the lock was already held by the current thread; and
     * <tt>false</tt> otherwise.
     */
    public boolean tryLock() {
        Thread current = Thread.currentThread();
        if (acquireOwner(current))
            return true;
        if (owner == current) {
            ++recursions;
            return true;
        }
        return false;
    }

    /**
     *
     * Acquires the lock if it is not held by another thread  within the given 
     * waiting time and the current thread has not been interrupted. 
     * <p>Acquires the lock if it is not held by another thread and returns 
     * immediately with the value <tt>true</tt>, setting the lock hold count 
     * to one.
     * <p> If the current thread
     * already holds this lock then the hold count is incremented by one and
     * the method returns <tt>true</code>.
     * <p>If the lock is held by another thread then the
     * the current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li> The lock is acquired by the current thread; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li> The specified waiting time elapses
     * </ul>
     * <p>If the lock is acquired then the value <tt>true</tt> is returned and
     * the lock hold count is set to one.
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or 
     * <li>is {@link Thread#interrupt interrupted} before acquiring
     * the lock,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. As this method is an explicit
     * interruption point, preference is given to responding to the interrupt
     * over reentrant acquisition of the lock.
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * <p>The given waiting time is a best-effort lower bound. If the time is 
     * less than or equal to zero, the method will not wait at all.
     *
     * <h3>Implementation Notes</h3>
     * <p> To-BE_DONE
     *
     * @return <tt>true</tt> if the lock was free and was acquired by the
     * current thread, or the lock was already held by the current thread; and
     * <tt>false</tt> if the waiting time elapsed before the lock could be 
     * acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null)
            throw new NullPointerException();
        if (Thread.interrupted()) 
            throw new InterruptedException();
        Thread current = Thread.currentThread();
        if (canBarge() && acquireOwner(current))
            return true;
        if (owner == current) { 
            ++recursions;
            return true;
        }
        if (timeout <= 0) 
            return false;
        int stat = doLock(current, null, TIMEOUT, unit.toNanos(timeout));
        if (stat == NO_INTERRUPT)
            return true;
        if (stat == INTERRUPT)
            throw new InterruptedException();
        assert stat == TIMEOUT;
        return false; 
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
     * @return the number of holds on this lock by current thread,
     * or zero if this lock is not held by current thread.
     **/
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
     *
     * @return <tt>true</tt> if current thread holds this lock and 
     * <tt>false</tt> otherwise.
     **/
    public boolean isHeldByCurrentThread() {
        return (owner == Thread.currentThread());
    }


    /**
     * Queries if this lock is held by any thread. THis method is
     * designed for use in monitoring, not for synchronization control.
     * @return <tt>true</tt> if any thread holds this lock and 
     * <tt>false</tt> otherwise.
     **/
    public boolean isLocked() {
        return owner != null;
    }


    /**
     * Returns a {@link Condition} instance for use with this 
     * {@link Lock} instance.
     *
     * <p>The returned {@link Condition} instance has the same behavior and 
     * usage
     * restrictions with this lock as the {@link Object} monitor methods
     * ({@link Object#wait() wait}, {@link Object#notify notify}, and
     * {@link Object#notifyAll notifyAll}) have with the built-in monitor
     * lock:
     * <ul>
     * <li>If this lock is not held when any of the {@link Condition}
     * {@link Condition#await() waiting} or {@link Condition#signal signalling}
     * methods are called, then an {@link IllegalMonitorStateException} is
     * thrown.
     * <li>When the condition {@link Condition#await() waiting} methods are
     * called the lock is released and before they return the lock is
     * reacquired and the lock hold count restored to what it was when the
     * method was called.
     * <li>If a thread is {@link Thread#interrupt interrupted} while waiting
     * then the wait will terminate, an {@link InterruptedException} will be
     * thrown, and the thread's interrupted status will be cleared.
     * <li>The order in which waiting threads are signalled is not specified.
     * <li>The order in which threads returning from a wait, and threads trying
     * to acquire the lock, are granted the lock, is not specified.
     * </ul>
     */
    public Condition newCondition() {
        return new ReentrantLockConditionObject();
    }

    // Helper methods for Conditions

    /**
     * Return true if a node, always one that was initially placed on
     * a condition queue, is off the condition queue (and thus,
     * normally is now on lock queue.)
     */
    boolean isOffConditionQueue(WaitNode w) {
        return w.count > TRANSFERRING;
    } 

    /**
     * Transfer a node from condition queue onto lock queue. 
     * Return true if successful (i.e., node not cancelled)
     */
    final boolean transferToLockQueue(WaitNode node) {
        /*
         * Atomically change status to TRANSFERRING to avoid races
         * with cancelling waiters. We use a special value that causes
         * any waiters spuriously waking up to re-park until the node
         * has been placed on lock queue.
         */
        if (!casCount(node, ON_CONDITION_QUEUE, TRANSFERRING))
            return false;

        /*
         *  Splice onto queue
         */
        WaitNode p = enq(node);

        /*
         * Establish normal lock-queue release count for node.  The
         * CAS can fail if node already was involved in a cancellation
         * on lock-queue, in which case we signal to be sure.
         */
        if (!casCount(node, TRANSFERRING, 0))
            signalSuccessor(node);

        /*
         * Ensure release count of predecessor is negative to indicate
         * that thread is (probably) waiting. If attempt to set count
         * fails or is pred is/becomes cancelled, wake up successor
         * (which will ordinarily be "node") to resynch.
         */

        for (;;) {
            int c = p.count;
            if (c < 0 || (c != CANCELLED && casCount(p, c, -1)))
                break;
            signalSuccessor(p);
            if (c == CANCELLED)
                break;
        }

        return true;
    }

    /**
     * Hook method used by ReentrantReadWriteLock. Called
     * before unlocking lock to enter wait.
     */
    void beforeWait() { }


    /**
     * Hook method used by ReentrantReadWriteLock. Called
     * after locking lock after exiting wait.
     */
    void afterWait() { }

    private class ReentrantLockConditionObject implements Condition, java.io.Serializable {
        /*
         * Because condition queues are accessed only when locks are
         * already held, we just need a simple linked queue to hold
         * nodes while they are waiting on conditions. They are then
         * transferred to the lock queue to re-acquire locks.
         */

        /**
         * First node of condition queue.
         */
        private transient WaitNode firstWaiter;

        /**
         * Last node of condition queue.
         */
        private transient WaitNode lastWaiter;

        /**
         * Basic linked queue insertion.
         */
        private WaitNode addWaiter(Thread current) {
            WaitNode w = new WaitNode(current);
            w.count = ON_CONDITION_QUEUE;
            if (lastWaiter == null) 
                firstWaiter = lastWaiter = w;
            else {
                WaitNode t = lastWaiter;
                lastWaiter = w;
                t.next = w;
            }
            return w;
        }

        /**
         * Main code for signal.  Dequeue and transfer nodes until hit
         * non-cancelled one or null. Split out from signal to
         * encourage compilers to inline the case of no waiters.
         */
        private void doSignal(WaitNode first) {
            do {
                if ( (firstWaiter = first.next) == null) 
                    lastWaiter = null;
                first.next = null;
                if (transferToLockQueue(first))
                    return;
                first = firstWaiter;
            } while (first != null);
        }
        
        public void signal() {
            checkOwner(Thread.currentThread());
            WaitNode w = firstWaiter;
            if (w != null)
                doSignal(w);
        }
            
        public void signalAll() {
            checkOwner(Thread.currentThread());
            // Pull off list all at once and traverse.
            WaitNode w = firstWaiter;
            if (w != null) {
                lastWaiter = firstWaiter  = null;
                do {
                    WaitNode n = w.next;
                    w.next = null;
                    transferToLockQueue(w);
                    w = n;
                } while (w != null);
            }
        }

        /*
         * Various flavors of wait. Each almost the same, but
         * annoyingly different and no nice way to factor common code.
         */

        public void await() throws InterruptedException {
            Thread current = Thread.currentThread();
            checkOwner(current);

            WaitNode w = addWaiter(current);
            beforeWait();
            int recs = recursions;
            unlock();

            boolean wasInterrupted = false;
            
            while (!isOffConditionQueue(w)) {
                JSR166Support.park(false, 0);
                if (Thread.interrupted()) {
                    wasInterrupted = true;
                    if (casCount(w, ON_CONDITION_QUEUE, CANCELLED)) {
                        w.thread = null;
                        w = null;
                    }
                    break;
                }
            } 

            /*
             * If we exited above loop due to cancellation, then w is
             * null, and doLock will make a new lock node for
             * us. Otherwise, upon exit, our node is already in the
             * lock queue when doLock is called.
             */
            doLock(current, w, NO_INTERRUPT, 0);

            recursions = recs;
            afterWait();

            if (wasInterrupted) 
                throw new InterruptedException();
        }
        
        public void awaitUninterruptibly() {
            Thread current = Thread.currentThread();
            checkOwner(current);

            WaitNode w = addWaiter(current);
            beforeWait();
            int recs = recursions;
            unlock();

            boolean wasInterrupted = false;
            while (!isOffConditionQueue(w)) {
                JSR166Support.park(false, 0);
                if (Thread.interrupted()) 
                    wasInterrupted = true;
            }

            doLock(current, w, NO_INTERRUPT, 0);
            recursions = recs;
            afterWait();
            // avoid re-interrupts on exit
            if (wasInterrupted && !current.isInterrupted()) 
                current.interrupt();
        }


        public long awaitNanos(long nanos) throws InterruptedException {
            Thread current = Thread.currentThread();
            checkOwner(current);

            WaitNode w = addWaiter(current);
            beforeWait();
            int recs = recursions;
            unlock();

            if (nanos <= 0) nanos = 1; // park arg must be positive
            long timeLeft = nanos;
            long startTime = TimeUnit.nanoTime();
            boolean wasInterrupted = false;
            boolean cancelled = false;

            if (!isOffConditionQueue(w)) {
                for (;;) {
                    JSR166Support.park(false, timeLeft);
                    if (Thread.interrupted()) 
                        wasInterrupted = true;
                    else if (isOffConditionQueue(w)) 
                        break;
                    else
                        timeLeft = nanos - (TimeUnit.nanoTime() - startTime);

                    if (wasInterrupted || timeLeft <= 0) {
                        if (casCount(w, ON_CONDITION_QUEUE, CANCELLED)) {
                            w.thread = null;
                            w = null;
                        }
                        break;
                    }
                } 
            }

            doLock(current, w, NO_INTERRUPT, 0);
            recursions = recs;
            afterWait();

            if (wasInterrupted) 
                throw new InterruptedException();
            else if (timeLeft <= 0)
                return timeLeft;
            else
                return nanos - (TimeUnit.nanoTime() - startTime);
        }

        public boolean awaitUntil(Date deadline) throws InterruptedException {
            Thread current = Thread.currentThread();
            checkOwner(current);

            WaitNode w = addWaiter(current);
            beforeWait();
            int recs = recursions;
            unlock();

            boolean wasInterrupted = false;
            boolean cancelled = false;
            long abstime = deadline.getTime();

            if (!isOffConditionQueue(w)) {
                for (;;)  {
                    JSR166Support.park(true, abstime);
                    
                    boolean timedOut = false;
                    if (Thread.interrupted()) 
                        wasInterrupted = true;
                    else if (isOffConditionQueue(w)) 
                        break;
                    else if (System.currentTimeMillis() <= abstime)
                        timedOut = true;
                    
                    if (wasInterrupted || timedOut) {
                        if (casCount(w, ON_CONDITION_QUEUE, CANCELLED)) {
                            w.thread = null;
                            w = null;
                        }
                        break;
                    }
                } 
            }

            doLock(current, w, NO_INTERRUPT, 0);
            recursions = recs;
            afterWait();

            if (wasInterrupted) 
                throw new InterruptedException();
            return !cancelled;
        }

        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            return awaitNanos(unit.toNanos(time)) > 0;
        }

    }

}
