/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.Date;


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
 * thread.  Otherwise a policy is used that does not guarantee any
 * particular access order.  Programs using fair locks may display
 * lower overall throughput (i.e., are slower; often much slower) than
 * those using default locks, but have but smaller variances in times
 * to obtain locks and guarantee lack of starvation.
 *
 * <p>If you want a non-reentrant mutual exclusion lock then it is a simple
 * matter to use a reentrant lock in a non-reentrant way by ensuring that
 * the lock is not held by the current thread prior to locking.
 * See {@link #getHoldCount} for a way to check this.
 *
 *
 * <p><tt>ReentrantLock</tt> instances are intended to be used primarily
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
 * @spec JSR-166
 * @revised $Date: 2003/08/05 04:03:38 $
 * @editor $Author: tim $
 * @author Doug Lea
 *
 **/
public class ReentrantLock implements Lock, java.io.Serializable {
    /*
      The basic fastpath/slowpath algorithm looks like this, ignoring
      reentrance, cancellation, timeouts, error checking etc:
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
        StoreLoad barrier (i.e., volatile-write-barrier) per
        lock/unlock pair.  The "owner" field is handled as a simple
        spinlock.  To lock, the owner field is set to current thread
        using conditional atomic update.  To unlock, the owner field
        is set to null, checking if anyone needs waking up, if so
        doing so.  Recursive locks/unlocks instead increment/decrement
        recursion field.

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

      * The "fair" variant differs only in that barging is disabled
        when there is contention, so locks proceed FIFO. There can be
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
        algorithms.  Since we must poll for cancellation of other
        nodes, we can miss noticing whether a cancelled node is ahead
        or behind us. This is dealt with by always unparking
        successors upon cancellation, and not letting them park again
        (by saturating release counts) until they stabilize on a new
        predecessor.

      * Threads waiting on Conditions use the same kind of nodes, but
        only need to link them in simple (non-concurrent) linked
        queues because they are only accessed when lock is held.  To
        wait, a thread makes a node inserted into a condition queue.
        Upon signal, the node is transferred to the lock queue.
        Special values of releaseStatus fields are used to mark which
        queue a node is on.

      * All suspension and resumption of threads uses the internal
        JSR166 native park/unpark API. These are safe versions of
        suspend/resume (plus timeout support) that avoid the intrinsic
        race problems with suspend/resume: Park suspends if not
        preceded by an unpark. Unpark resumes if waiting, else causes
        next park not to suspend. While safe and efficient, these are
        not general-purpose public operations because we cannot allow
        code outside this package to randomly call these methods --
        parks and unparks should be matched up. (It is OK to have more
        unparks than unparks, but it causes threads to spuriously wake
        up. So minimizing excessive unparks is a performance concern.)

    */

    /**
     * Node class for threads waiting for locks.
     */
    static class ReentrantLockQueueNode {
        /**
         * Controls whether successor node is allowed to try to obtain
         * ownership. Acts as a saturating (in both directions) counting
         * semaphore: Upon each wait, the releaseStatus is reduced to zero
         * if positive, else reduced to negative, in which case the thread
         * will park. The releaseStatus is incremented on each unlock that
         * would enable successor thread to obtain lock (succeeding if
         * there is no contention). The special value of CANCELLED is used
         * to mean that the releaseStatus cannot be either incremented or
         * decremented.  The special value of ON_CONDITION_QUEUE is used
         * when nodes are on conditions queues instead of lock queue.
         */
        transient volatile int releaseStatus;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking releaseStatus. Assigned once during enqueing,
         * and nulled out (for sake of GC) only upon dequeuing.  Upon
         * cancellation, we do NOT adjust this field, but simply
         * traverse through prev's until we hit a non-cancelled node.
         * A valid predecessor will always exist because the head node
         * is never cancelled.
         */
        transient volatile ReentrantLockQueueNode prev;

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
        transient volatile ReentrantLockQueueNode next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.  Note that this need
         * not be declared volatile since it is always accessed after
         * traversing volatile links, and written before writing
         * links.
         */
        transient Thread thread;


        /**
         * Link to next node waiting on condition.
         * These are only necessary for nodes created in Conditions
         */
        transient ReentrantLockQueueNode nextWaiter;

        /**
         * TEMPORARY field for use only by emulated version of park/unpark.
         * Remove when emulation package is phased out.
         */
        transient int parkSemaphore;

        ReentrantLockQueueNode() { }
        ReentrantLockQueueNode(Thread t) {
            thread = t;
        }
        ReentrantLockQueueNode(Thread t, int s) {
            thread = t;
            releaseStatus = s;
        }
    }

    /*
      Note that all fields are defined in a way so that deserialized
      locks are in initial unlocked state.
    */

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
        if (fair) {// avoid races to detecting first contention
            ReentrantLockQueueNode h = new ReentrantLockQueueNode();
            head = h;
            tail = h;
        }
    }

    /**
     * Current owner of lock, or null iff the lock is free.  Acquired
     * only using CAS.
     */
    private transient volatile Thread owner;

    /** Number of recursive acquires. Note: total holds = recursions+1 */
    private transient int recursions;

    /** true if barging disabled */
    private final boolean fair;

    /** Head of the wait queue, lazily initialized. */
    private transient volatile ReentrantLockQueueNode head;

    /** Tail of the wait queue, lazily initialized.  */
    private transient volatile ReentrantLockQueueNode tail;

    // Atomics support

    private final static
        AtomicReferenceFieldUpdater<ReentrantLock, Thread>
        ownerUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (ReentrantLock.class, Thread.class, "owner");
    private final static
        AtomicReferenceFieldUpdater<ReentrantLock, ReentrantLockQueueNode>
        tailUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (ReentrantLock.class, ReentrantLockQueueNode.class, "tail");
    private final static
        AtomicReferenceFieldUpdater<ReentrantLock, ReentrantLockQueueNode>
        headUpdater =
        AtomicReferenceFieldUpdater.newUpdater
        (ReentrantLock.class,  ReentrantLockQueueNode.class, "head");
    private final static
        AtomicIntegerFieldUpdater<ReentrantLockQueueNode>
        releaseStatusUpdater =
        AtomicIntegerFieldUpdater.newUpdater
        (ReentrantLockQueueNode.class, "releaseStatus");

    private boolean acquireOwner(Thread current) {
        return ownerUpdater.compareAndSet(this, null, current);
    }

    private boolean casTail(ReentrantLockQueueNode cmp, ReentrantLockQueueNode val) {
        return tailUpdater.compareAndSet(this, cmp, val);
    }

    private boolean casHead(ReentrantLockQueueNode cmp, ReentrantLockQueueNode val) {
        return headUpdater.compareAndSet(this, cmp, val);
    }

    private boolean casReleaseStatus(ReentrantLockQueueNode node, int cmp, int val) {
        return releaseStatusUpdater.compareAndSet(node, cmp, val);
    }

    /**
     * Special value for releaseStatus indicating that node is cancelled.
     * Must be a large positive number.
     */
    private static final int CANCELLED = Integer.MAX_VALUE;

    /**
     * Special value for node releaseStatus indicating that node is on a
     * condition queue. Must be large negative number.
     */
    private static final int ON_CONDITION_QUEUE = Integer.MIN_VALUE;

    /**
     * Return whether lock wait queue is empty
     * @return true if no threads are waiting for lock
     */
    private boolean queueEmpty() {
        ReentrantLockQueueNode h = head; // force order of the volatile reads
        return h == tail;
    }

    /**
     * Throw IllegalMonitorStateException if t not owner
     */
    final void checkOwner(Thread t) {
        if (owner != t)
            throw new IllegalMonitorStateException();
    }

    /**
     * Insert node into queue. Return predecessor.
     * @param node the node to insert
     * @return node's predecessor
     */
    private ReentrantLockQueueNode enq(ReentrantLockQueueNode node) {
        // ensure initialization
        ReentrantLockQueueNode t;
        while ( (t = tail) == null) {
            ReentrantLockQueueNode h = new ReentrantLockQueueNode();
            if (casHead(null, h)) {
                tail = h;
                break;
            }
        }

        ReentrantLockQueueNode p;
        do {
            node.prev = p = tail; // prev must be valid before/upon CAS
        } while (!casTail(p, node));
        p.next = node;      // Note: next field assignment lags CAS
        return p;
    }

    /**
     * Main locking code, parameterized across different policies.
     * @param current current thread
     * @param node its wait-node, if it already exists; else null in
     * which case it is created,
     * @param interruptible - true if can abort for interrupt or timeout
     * @param nanos time to wait, or zero if untimed
     * @return true if lock acquired (can be false only if interruptible)
     */
    private boolean waitForLock(Thread current,
                              ReentrantLockQueueNode node,
                              boolean interruptible,
                              long nanos) {
        /*
         * Check for recursive lock
         */
        if (owner == current) {
            ++recursions;
            return true;
        }

        /*
         * p is our predecessor node, that holds releaseStatus giving
         * permission to try to obtain lock if we are first in queue.
         */
        ReentrantLockQueueNode p;

        /*
         * Create and enqueue node if not already created. Nodes
         * transferred from condition queues will already be created
         * and queued.
         */
        if (node == null) {
            node = new ReentrantLockQueueNode(current);
            // Last chance to retry fast path before queuing
            if ((!fair || queueEmpty()) && acquireOwner(current))
                return true;
            p = enq(node);
        }
        else
            p = node.prev;

        boolean wasInterrupted = false;
        long startTime = 0;

        /*
         * Repeatedly try to get ownership if first in queue; block on
         * failure.  If we are the first thread in queue, we must try
         * to get the lock, and we must not try to get lock if we are
         * not first.  If we become first after p == head check, all
         * is well -- we can be sure an unlocking thread will signal
         * us.
         */
        for (;;) {
            if (p == head && acquireOwner(current)) {
                // Set head and unlink after successfully getting owenership
                head = node;
                p.next = null;
                node.thread = null;
                node.prev = null;
                if (wasInterrupted)            // Re-interrupt on normal exit
                    current.interrupt();
                return true;
            }

            int releaseStatus = p.releaseStatus;

            /*
             * If our predecessor was cancelled, use its predecessor.
             * There will always be a non-cancelled one somewhere
             * because head node is never cancelled, so at worst we
             * will hit it.
             */

            if (releaseStatus == CANCELLED) {
                node.prev = p = p.prev;
            }

            /*
             * If released, retry. (The retry is unlikely to
             * succeed, but we must anyway in case the release
             * occurred since the above failed ownership CAS.)
             */
            else if (releaseStatus > 0) {
                casReleaseStatus(p, releaseStatus, 0);
            }


            /*
             * Wait if we are not not first in queue, or if we are
             * first, we have tried to acquire owner and failed since
             * either entry or last release.  Note that releaseStatus
             * can already be less than zero if we spuriously returned
             * from a previous park or got new a predecessor due to
             * cancellation.
             *
             * We also don't wait if atomic decrement of releaseStatus
             * fails. We just continue main loop on failure to
             * atomically update releaseStatus because interference
             * causing failure is almost surely due to someone
             * releasing us anyway. Similarly, we don't wait if
             * it looks like we can get ownership lock after the CAS.
             *
             * Each wait consumes all available releases. Normally
             * there is only one anyway because unlock doesn't bother
             * incrementing if already positive.
             *
             */
            else if (casReleaseStatus(p, releaseStatus, -1)) {
                long timeLeft = 0;
                if (nanos > 0) {
                    long now = System.nanoTime();
                    if (startTime == 0) {
                        startTime = now;
                        timeLeft = nanos;
                    }
                    else {
                        timeLeft = nanos - (now - startTime);
                        if (timeLeft <= 0)
                            timeLeft = -1;
                    }
                }
                else if (!interruptible && Thread.interrupted())
                    wasInterrupted = true;

                // last chance to avoid blocking
                if (owner != null || p != head) {
                    if (timeLeft == 0)
                        LockSupport.park(node);
                    else if (timeLeft > 0)
                        LockSupport.parkNanos(node, timeLeft);

                    if (timeLeft < 0 ||
                        (interruptible && current.isInterrupted())) {
                        node.thread = null;      // disable signals
                        node.releaseStatus = CANCELLED;
                        signalSuccessor(node);
                        return false;
                    }
                }
            }
        }

    }

    /**
     * Signal succeessor of node, if one exists
     *
     * @param node the node
     */
    private void signalSuccessor(ReentrantLockQueueNode node) {
        /*
         * Find successor -- normally just node.next.
         * But if its is cancelled, traverse through next's.
         */
        ReentrantLockQueueNode s = node.next;
        while (s != null && s.releaseStatus == CANCELLED) {
            node = s;
            s = s.next;
        }

        /*
         * If successor appears to be null, check to see if a newly
         * queued node is successor by starting at tail and working
         * backwards.
         */
        if (s == null) {
            s = tail;
            for (;;) {
                /*
                 * If s == node, there is no successor.  And s's
                 * predecessor is null if we are lagging so far behind
                 * the actions of other nodes/threads that an
                 * intervening head.prev was nulled by some
                 * non-cancelled successor of node. In which case,
                 * there's no live successor.
                 */
                if (s == node || s == null)
                    return;
                ReentrantLockQueueNode sp = s.prev;
                if (sp == node)
                    break;
                s = sp;
            }
        }

        Thread thr = s.thread;
        if (thr != null && thr != owner) // don't bother to signal if has lock
            LockSupport.unpark(node, thr);
    }

    /**
     * Release and signal if necessary the first waiting thread, if
     * one exists.
     */
    private void releaseFirst() {
        ReentrantLockQueueNode h = head;
        if (h != null) {
            int c;
            while ((c = h.releaseStatus) <= 0 && owner == null) {
                if (casReleaseStatus(h, c, c+1)) {
                    if (c < 0)
                        signalSuccessor(h);
                    break;
                }
                // Retry if CAS fails
            }
        }
    }


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
        checkOwner(Thread.currentThread());
        if (recursions > 0)
            --recursions;
        else {
            owner = null;
            releaseFirst();
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
        if ((fair && !queueEmpty()) || !acquireOwner(current))
            waitForLock(current, null, false, 0);
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
     * <li> The lock is acquired by the current thread; or
     * <li> Some other thread {@link Thread#interrupt interrupts} the current
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
        if (Thread.interrupted())
            throw new InterruptedException();
        if ((!fair || queueEmpty()) && acquireOwner(current))
            return;
        if (waitForLock(current, null, true, 0))
            return;
        Thread.interrupted(); // clear interrupt status on failure
        throw new InterruptedException();
    }

    /**
     * Acquires the lock only if it is not held by another thread at the time
     * of invocation.
     * <p>Acquires the lock if it is not held by another thread and
     * returns immediately with the value <tt>true</tt>, setting the
     * lock hold count to one. Even when locking has been set to use a
     * fair ordering policy, a call to <tt>tryLock</tt> will
     * immediately acquire the lock if it is available, whether or not
     * other threads are currently waiting for the lock.
     * <p> If the current thread
     * already holds this lock then the hold count is incremented by one and
     * the method returns <tt>true</tt>.
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
     * Acquires the lock if it is not held by another thread within the given
     * waiting time and the current thread has not been
     * {@link Thread#interrupt interrupted}.
     * <p>Acquires the lock if it is not held by another thread and returns
     * immediately with the value <tt>true</tt>, setting the lock hold count
     * to one.
     * <p> If the current thread
     * already holds this lock then the hold count is incremented by one and
     * the method returns <tt>true</tt>.
     * <p>If the lock is held by another thread then the
     * current thread becomes disabled for thread scheduling
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
     * <li>is {@link Thread#interrupt interrupted} while acquiring
     * the lock,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * The given waiting time is a best-effort lower bound. If the time is
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
        if ((!fair || queueEmpty()) && acquireOwner(current))
            return true;
        if (owner == current) { // check recursions before timeout
            ++recursions;
            return true;
        }
        if (timeout <= 0)
            return false;
        if (waitForLock(current, null, true, unit.toNanos(timeout)))
            return true;
        if (Thread.interrupted())
            throw new InterruptedException();
        return false;  // timed out
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
     * Queries if this lock is held by any thread. This method is
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
     * @return the Condition object
     */
    public Condition newCondition() {
        return new ReentrantLockConditionObject();
    }

    // Helper methods for Conditions

    /**
     * Variant of unlock used by condition wait.
     * Fully unlocks, setting recursions to zero.
     * @return current recursion count.
     */
    final int unlockForWait() {
        int recs = recursions;
        recursions = 0;
        owner = null;
        releaseFirst();
        return recs;
    }

    /**
     * Re-acquire lock after a wait, resetting recursion count.
     */
    final void relockAfterWait(Thread current,
                               ReentrantLockQueueNode node,
                               int recs) {
        waitForLock(current, node, false, 0);
        recursions = recs;
    }

    /**
     * Return true if a node, always one that was initially placed on
     * a condition queue, is now on the lock queue.
     */
    final boolean isOnLockQueue(ReentrantLockQueueNode w) {
        if (w.releaseStatus == ON_CONDITION_QUEUE || w.prev == null)
            return false;
        if (w.next != null) // for sure on queue if has a successor
            return true;
        /*
         * w.prev can be non-null, but not yet on lock queue because
         * the CAS to place it on queue can fail. So we have
         * to traverse from tail to make sure it actually made it.
         * It will always be near the tail in calls to this method,
         * so we won't need to traverse much.
         */
        for (ReentrantLockQueueNode t = tail; t != null; t = t.prev)
            if (t == w)
                return true;
        return false;
    }

    /**
     * Transfer a node from a condition queue onto lock queue.
     * Return true if successful.
     */
    final boolean transferForSignal(ReentrantLockQueueNode node) {
        /*
         * If cannot change status, then the node has already been
         * cancelled.
         */
        if (!casReleaseStatus(node, ON_CONDITION_QUEUE, 0))
            return false;

        /*
         *  Splice onto queue.
         */
        ReentrantLockQueueNode p = enq(node);

        /*
         * Try to set releaseStatus of predecessor negative to
         * indicate that thread is (probably) waiting. If cancelled or
         * already negative or attempt to set releaseStatus fails,
         * wake up to resynch.
         */

        int c = p.releaseStatus;
        if (c == CANCELLED || c < 0 || !casReleaseStatus(p, c, -1)) {
            c = p.releaseStatus;
            if (c <= 0)
                casReleaseStatus(p, c, 1);
            Thread thr = node.thread;
            if (thr != null)
                LockSupport.unpark(node, thr);
        }

        return true;
    }


    /**
     * Re-acquire lock after a cancelled wait.
     */
    final void relockAfterCancelledWait(Thread current,
                                        ReentrantLockQueueNode node,
                                       int recs) {
        /*
         * Try to place node on lock queue.  If we lost race to a
         * signal(), then we can't proceed until it succeeds in
         * placing us on lock queue.  So just spin.
         */
        if (casReleaseStatus(node, ON_CONDITION_QUEUE, 0))
            enq(node);
        else {
            while (!isOnLockQueue(node))
                Thread.yield();
        }
        waitForLock(current, node, false, 0);
        recursions = recs;
    }

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
        private transient ReentrantLockQueueNode firstWaiter;

        /**
         * Last node of condition queue.
         */
        private transient ReentrantLockQueueNode lastWaiter;

        /**
         * Add new waiter:  Bookkeeping plus linked queue insertion.
         */
        private ReentrantLockQueueNode addWaiter(Thread current) {
            if (current != owner) throw new IllegalMonitorStateException();
            ReentrantLockQueueNode w = new ReentrantLockQueueNode(current, ON_CONDITION_QUEUE);
            if (lastWaiter == null)
                firstWaiter = lastWaiter = w;
            else {
                ReentrantLockQueueNode t = lastWaiter;
                lastWaiter = w;
                t.nextWaiter = w;
            }
            return w;
        }

        /**
         * Main code for signal.  Dequeue and transfer nodes until hit
         * non-cancelled one or null. Split out from signal to
         * encourage compilers to inline the case of no waiters.
         * @param first the first node on condition queue
         */
        private void doSignal(ReentrantLockQueueNode first) {
            do {
                if ( (firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;
                first.nextWaiter = null;
                if (transferForSignal(first))
                    return;
                first = firstWaiter;
            } while (first != null);
        }

        public void signal() {
            checkOwner(Thread.currentThread());
            ReentrantLockQueueNode w = firstWaiter;
            if (w != null)
                doSignal(w);
        }

        public void signalAll() {
            checkOwner(Thread.currentThread());
            // Pull off list all at once and traverse.
            ReentrantLockQueueNode w = firstWaiter;
            if (w != null) {
                lastWaiter = firstWaiter  = null;
                do {
                    ReentrantLockQueueNode n = w.nextWaiter;
                    w.nextWaiter = null;
                    transferForSignal(w);
                    w = n;
                } while (w != null);
            }
        }

        /*
         * Various flavors of wait. Each almost the same, but
         * annoyingly different.
         */

        public void await() throws InterruptedException {
            Thread current = Thread.currentThread();
            checkOwner(current);
            ReentrantLockQueueNode w = addWaiter(current);
            int recs = unlockForWait();

            for (;;) {
                if (Thread.interrupted()) {
                    relockAfterCancelledWait(current, w, recs);
                    throw new InterruptedException();
                }
                if (isOnLockQueue(w)) {
                    relockAfterWait(current, w, recs);
                    if (Thread.interrupted())
                        throw new InterruptedException();
                    return;
                }
                LockSupport.park(w);
            }
        }

        public void awaitUninterruptibly() {
            Thread current = Thread.currentThread();
            checkOwner(current);
            ReentrantLockQueueNode w = addWaiter(current);
            int recs = unlockForWait();

            boolean wasInterrupted = false;
            while (!isOnLockQueue(w)) {
                LockSupport.park(w);
                if (Thread.interrupted())
                    wasInterrupted = true;
            }

            relockAfterWait(current, w, recs);
            // avoid re-interrupts on exit
            if (wasInterrupted && !current.isInterrupted())
                current.interrupt();
        }


        public long awaitNanos(long nanos) throws InterruptedException {
            Thread current = Thread.currentThread();
            checkOwner(current);
            ReentrantLockQueueNode w = addWaiter(current);
            int recs = unlockForWait();

            if (nanos <= 0) nanos = 1; // park arg must be positive
            long timeLeft = nanos;
            long startTime = System.nanoTime();

            for (;;) {
                if (Thread.interrupted()) {
                    relockAfterCancelledWait(current, w, recs);
                    throw new InterruptedException();
                }
                if ((timeLeft = nanos - (System.nanoTime()-startTime)) <= 0) {
                    relockAfterCancelledWait(current, w, recs);
                    return timeLeft;
                }
                if (isOnLockQueue(w)) {
                    relockAfterWait(current, w, recs);
                    if (Thread.interrupted())
                        throw new InterruptedException();
                    return nanos - (System.nanoTime() - startTime);
                }
                LockSupport.parkNanos(w, timeLeft);
            }
        }

        public boolean awaitUntil(Date deadline) throws InterruptedException {
            Thread current = Thread.currentThread();
            checkOwner(current);
            ReentrantLockQueueNode w = addWaiter(current);
            int recs = unlockForWait();
            long abstime = deadline.getTime();

            for (;;) {
                if (Thread.interrupted()) {
                    relockAfterCancelledWait(current, w, recs);
                    throw new InterruptedException();
                }
                if (System.currentTimeMillis() > abstime) {
                    relockAfterCancelledWait(current, w, recs);
                    return false;
                }
                if (isOnLockQueue(w)) {
                    relockAfterWait(current, w, recs);
                    if (Thread.interrupted())
                        throw new InterruptedException();
                    return true;
                }
                LockSupport.parkUntil(w, abstime);
            }
        }

        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            return awaitNanos(unit.toNanos(time)) > 0;
        }

    }

}

