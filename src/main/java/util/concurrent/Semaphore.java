/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

/**
 * A counting semaphore.  Conceptually, a semaphore maintains a set of
 * permits.  Each {@link #acquire} blocks if necessary until a permit is
 * available, and then takes it.  Each {@link #release} adds a permit,
 * potentially releasing a blocking acquirer.
 * However, no actual permit objects are used; the <tt>Semaphore</tt> just
 * keeps a count of the number available and acts accordingly.
 *
 * <p>Semaphores are often used to restrict the number of threads than can
 * access some (physical or logical) resource. For example, here is
 * a class that uses a semaphore to control access to a pool of items:
 * <pre>
 * class Pool {
 *   private static final MAX_AVAILABLE = 100;
 *   private final Semaphore available = new Semaphore(MAX_AVAILABLE, true);
 *
 *   public Object getItem() throws InterruptedException {
 *     available.acquire();
 *     return getNextAvailableItem();
 *   }
 *
 *   public void putItem(Object x) {
 *     if (markAsUnused(x))
 *       available.release();
 *   }
 *
 *   // Not a particularly efficient data structure; just for demo
 *
 *   protected Object[] items = ... whatever kinds of items being managed
 *   protected boolean[] used = new boolean[MAX_AVAILABLE];
 *
 *   protected synchronized Object getNextAvailableItem() {
 *     for (int i = 0; i < MAX_AVAILABLE; ++i) {
 *       if (!used[i]) {
 *          used[i] = true;
 *          return items[i];
 *       }
 *     }
 *     return null; // not reached
 *   }
 *
 *   protected synchronized boolean markAsUnused(Object item) {
 *     for (int i = 0; i < MAX_AVAILABLE; ++i) {
 *       if (item == items[i]) {
 *          if (used[i]) {
 *            used[i] = false;
 *            return true;
 *          } else
 *            return false;
 *       }
 *     }
 *     return false;
 *   }
 *
 * }
 * </pre>
 *
 * <p>Before obtaining an item each thread must acquire a permit from
 * the semaphore, guaranteeing that an item is available for use. When
 * the thread has finished with the item it is returned back to the
 * pool and a permit is returned to the semaphore, allowing another
 * thread to acquire that item.  Note that no synchronization lock is
 * held when {@link #acquire} is called as that would prevent an item
 * from being returned to the pool.  The semaphore encapsulates the
 * synchronization needed to restrict access to the pool, separately
 * from any synchronization needed to maintain the consistency of the
 * pool itself.
 *
 * <p>A semaphore initialized to one, and which is used such that it
 * only has at most one permit available, can serve as a mutual
 * exclusion lock.  This is more commonly known as a <em>binary
 * semaphore</em>, because it only has two states: one permit
 * available, or zero permits available.  When used in this way, the
 * binary semaphore has the property (unlike many {@link Lock}
 * implementations), that the &quot;lock&quot; can be released by a
 * thread other than the owner (as semaphores have no notion of
 * ownership).  This can be useful in some specialized contexts, such
 * as deadlock recovery.
 *
 * <p> The constructor for this class accepts a <em>fairness</em>
 * parameter. When set false, this class makes no guarantees about the
 * order in which threads acquire permits. In particular, <em>barging</em> is
 * permitted, that is, a thread invoking {@link #acquire} can be
 * allocated a permit ahead of a thread that has been waiting.  When
 * fairness is set true, the semaphore guarantees that threads
 * invoking any of the {@link #acquire() acquire} methods are
 * allocated permits in the order in which their invocation of those
 * methods was processed (first-in-first-out; FIFO). Note that FIFO
 * ordering necessarily applies to specific internal points of
 * execution within these methods.  So, it is possible for one thread
 * to invoke <tt>acquire</tt> before another, but reach the ordering
 * point after the other, and similarly upon return from the method.
 *
 * <p>Generally, semaphores used to control resource access should be
 * initialized as fair, to ensure that no thread is starved out from
 * accessing a resource. When using semaphores for other kinds of
 * synchronization control, the throughput advantages of non-fair
 * ordering often outweigh fairness considerations.
 *
 * <p>This class also provides convenience methods to {@link
 * #acquire(int) acquire} and {@link #release(int) release} multiple
 * permits at a time.  Beware of the increased risk of indefinite
 * postponement when these methods are used without fairness set true.
 *
 * @since 1.5
 * @author Doug Lea
 *
 */

public class Semaphore implements java.io.Serializable {
    /*
     * The underlying algorithm here is a simplified adaptation of
     * that used for ReentrantLock. See the internal documentation of
     * ReentrantLock for detailed explanation.
     */

    private static final long serialVersionUID = -3222578661600680210L;

    /** Node status value to indicate thread has cancelled */
    private static final int CANCELLED =  1;
    /** Node status value to indicate successor needs unparking */
    private static final int SIGNAL    = -1;
    /** Node class for waiting threads */
    private static class Node {
        volatile int status;
        volatile Node prev;
        volatile Node next;
        Thread thread;
        Node(Thread t) { thread = t; }
    }

    /** Number of available permits held in a separate AtomicInteger */
    private final AtomicInteger perms;
    /**  Head of the wait queue, lazily initialized.  */
    private transient volatile Node head;
    /**  Tail of the wait queue, lazily initialized.  */
    private transient volatile Node tail;
    /** true if barging disabled */
    private final boolean fair;

    // Atomic update support

    private static final 
        AtomicReferenceFieldUpdater<Semaphore, Node> tailUpdater = 
        AtomicReferenceFieldUpdater.<Semaphore, Node>newUpdater 
        (Semaphore.class, Node.class, "tail");
    private static final 
        AtomicReferenceFieldUpdater<Semaphore, Node> headUpdater = 
        AtomicReferenceFieldUpdater.<Semaphore, Node>newUpdater 
        (Semaphore.class,  Node.class, "head");
    private static final 
        AtomicIntegerFieldUpdater<Node> statusUpdater = 
        AtomicIntegerFieldUpdater.<Node>newUpdater 
        (Node.class, "status");


    /**
     * Insert node into queue, initializing head and tail if necessary.
     * @param node the node to insert
     */
    private void enq(Node node) {
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
     * Unblock the successor of node
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        statusUpdater.compareAndSet(node, SIGNAL, 0);
        Node s = node.next;
        if (s == null || s.status == CANCELLED) {
            s = tail;
            if (s != null && s != node) {
                Node p = s.prev;
                while (p != null && p != node) {
                    if (p.status != CANCELLED) 
                        s = p; 
                    p = p.prev;
                }
            }
        }
        if (s != null && s != node)
            LockSupport.unpark(s.thread);
    }


    /**
     * Internal version of tryAcquire returning number of remaining
     * permits, which is nonnegative only if the acquire succeeded.
     * @param permits requested number of permits
     * @return remaining number of permits
     */
    private int doTryAcquire(int permits) {
        for (;;) {
            int available = perms.get();
            int remaining = available - permits;
            if (remaining < 0 ||
                perms.compareAndSet(available, remaining))
                return remaining;
        }
    }

    /**
     * Main code for untimed acquires. 
     * @param permits number of permits requested
     * @param interrupts interrupt control: -1 for abort on interrupt,
     * 0 for continue on interrupt
     * @return true if lock acquired (can be false only if interruptible)
     */
    private boolean doAcquire(int permits, int interrupts) {
        // Fast path bypasses queue
        if ((!fair || head == tail) && doTryAcquire(permits) >= 0) 
            return true;
        Thread current = Thread.currentThread();
        Node node = new Node(current);
        // Retry fast path before enqueuing
        if (!fair && doTryAcquire(permits) >= 0) 
            return true;
        enq(node);

        for (;;) {
            Node p = node.prev; 
            if (p == head) {
                int remaining = doTryAcquire(permits);
                if (remaining >= 0) {
                    p.next = null; 
                    node.thread = null;
                    node.prev = null; 
                    head = node;
                    // if still some permits left, wake up successor
                    if (remaining > 0 && node.status < 0) 
                        unparkSuccessor(node);
                    if (interrupts > 0) // Re-interrupt on normal exit
                        current.interrupt();
                    return true;
                }
            }
            int status = p.status;
            if (status == 0) 
                statusUpdater.compareAndSet(p, 0, SIGNAL);
            else if (status == CANCELLED) 
                node.prev = p.prev;
            else { 
                assert (status == SIGNAL);
                LockSupport.park();
                if (Thread.interrupted()) {
                    if (interrupts < 0)  {  
                        node.thread = null;      
                        node.status = CANCELLED;
                        unparkSuccessor(node);
                        return false;
                    }
                    interrupts = 1; // set to re-interrupt on exit
                }
            }
        }
    }

    /**
     * Main code for timed acquires. Same as doAcquire but with
     * interspersed time checks.
     * @param permits number of permits requested
     * @param nanos timeout in nanosecs
     * @return true if lock acquired 
     */
    private boolean doTimedAcquire(int permits, long nanos) throws InterruptedException {
        if ((!fair || head == tail) && doTryAcquire(permits) >= 0)
            return true;
        Thread current = Thread.currentThread();
        long lastTime = System.nanoTime();
        Node node = new Node(current);
        // Retry fast path before enqueuing
        if (!fair && doTryAcquire(permits) >= 0) 
            return true;
        enq(node);

        for (;;) {
            Node p = node.prev;
            if (p == head) {
                int remaining =  doTryAcquire(permits);
                if (remaining >= 0) {
                    p.next = null; 
                    node.thread = null;
                    node.prev = null; 
                    head = node;
                    if (remaining > 0 && node.status < 0) 
                        unparkSuccessor(node);
                    return true;
                }
            }
            if (nanos <= 0L) {     
                node.thread = null;      
                node.status = CANCELLED;
                unparkSuccessor(node);
                return false;
            }

            int status = p.status;
            if (status == 0) 
                statusUpdater.compareAndSet(p, 0, SIGNAL);
            else if (status == CANCELLED) 
                node.prev = p.prev;
            else {                      
                LockSupport.parkNanos(nanos);
                if (Thread.interrupted()) {
                    node.thread = null;      
                    node.status = CANCELLED;
                    unparkSuccessor(node);
                    throw new InterruptedException();
                }
                long now = System.nanoTime();
                nanos -= now - lastTime;
                lastTime = now;
            }
        }
    }

    /**
     * Internal version of release
     */
    private void doRelease(int permits) {
        for (;;) {
            int p = perms.get();
            if (perms.compareAndSet(p, p + permits)) {
                Node h = head;
                if (h != null  && h.status < 0)
                    unparkSuccessor(h);
                return;
            }
        }
    }

    /**
     * Construct a <tt>Semaphore</tt> with the given number of
     * permits and the given fairness setting.
     * @param permits the initial number of permits available. This
     * value may be negative, in which case releases must
     * occur before any acquires will be granted.
     * @param fair true if this semaphore will guarantee first-in
     * first-out granting of permits under contention, else false.
     */
    public Semaphore(int permits, boolean fair) { 
        this.fair = fair;
        perms = new AtomicInteger(permits);
    }

    /**
     * Acquires a permit from this semaphore, blocking until one is
     * available, or the thread is {@link Thread#interrupt interrupted}.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * reducing the number of available permits by one.
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting
     * for a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @throws InterruptedException if the current thread is interrupted
     *
     * @see Thread#interrupt
     */
    public void acquire() throws InterruptedException {
        if (Thread.interrupted() || !doAcquire(1, -1))
            throw new InterruptedException();
    }

    /**
     * Acquires a permit from this semaphore, blocking until one is
     * available.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * reducing the number of available permits by one.
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * some other thread invokes the {@link #release} method for this
     * semaphore and the current thread is next to be assigned a permit.
     *
     * <p>If the current thread
     * is {@link Thread#interrupt interrupted} while waiting
     * for a permit then it will continue to wait, but the time at which
     * the thread is assigned a permit may change compared to the time it
     * would have received the permit had no interruption occurred. When the
     * thread does return from this method its interrupt status will be set.
     *
     */
    public void acquireUninterruptibly() {
        doAcquire(1, 0);
    }

    /**
     * Acquires a permit from this semaphore, only if one is available at the 
     * time of invocation.
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the value <tt>true</tt>,
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then this method will return
     * immediately with the value <tt>false</tt>.
     *
     * @return <tt>true</tt> if a permit was acquired and <tt>false</tt>
     * otherwise.
     */
    public boolean tryAcquire() {
        return doTryAcquire(1) >= 0;
    }

    /**
     * Acquires a permit from this semaphore, if one becomes available 
     * within the given waiting time and the
     * current thread has not been {@link Thread#interrupt interrupted}.
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the value <tt>true</tt>,
     * reducing the number of available permits by one.
     * <p>If no permit is available then
     * the current thread becomes disabled for thread scheduling
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     * <p>If a permit is acquired then the value <tt>true</tt> is returned.
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting to acquire
     * a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * If the time is less than or equal to zero, the method will not wait 
     * at all.
     *
     * @param timeout the maximum time to wait for a permit
     * @param unit the time unit of the <tt>timeout</tt> argument.
     * @return <tt>true</tt> if a permit was acquired and <tt>false</tt>
     * if the waiting time elapsed before a permit was acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     *
     * @see Thread#interrupt
     *
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) 
        throws InterruptedException {
        if (unit == null)
            throw new NullPointerException();
        if (Thread.interrupted())
            throw new InterruptedException();
        return doTimedAcquire(1, unit.toNanos(timeout));
    }

    /**
     * Releases a permit, returning it to the semaphore.
     * <p>Releases a permit, increasing the number of available permits
     * by one.
     * If any threads are blocking trying to acquire a permit, then one
     * is selected and given the permit that was just released.
     * That thread is re-enabled for thread scheduling purposes.
     * <p>There is no requirement that a thread that releases a permit must
     * have acquired that permit by calling {@link #acquire}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     */
    public void release() {
        doRelease(1);
    }
       
    /**
     * Acquires the given number of permits from this semaphore, 
     * blocking until all are available, 
     * or the thread is {@link Thread#interrupt interrupted}.
     *
     * <p>Acquires the given number of permits, if they are available,
     * and returns immediately,
     * reducing the number of available permits by the given amount.
     *
     * <p>If insufficient permits are available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes one of the {@link #release() release} 
     * methods for this semaphore, the current thread is next to be assigned
     * permits and the number of available permits satisfies this request; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting
     * for a permit,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared. 
     * Any permits that were to be assigned to this thread are instead 
     * assigned to the next waiting thread(s), as if
     * they had been made available by a call to {@link #release()}.
     *
     * @param permits the number of permits to acquire
     *
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if permits less than zero.
     *
     * @see Thread#interrupt
     */
    public void acquire(int permits) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        if (Thread.interrupted() || !doAcquire(permits, -1))
            throw new InterruptedException();
    }

    /**
     * Acquires the given number of permits from this semaphore, 
     * blocking until all are available.
     *
     * <p>Acquires the given number of permits, if they are available,
     * and returns immediately,
     * reducing the number of available permits by the given amount.
     *
     * <p>If insufficient permits are available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * some other thread invokes one of the {@link #release() release} 
     * methods for this semaphore, the current thread is next to be assigned
     * permits and the number of available permits satisfies this request.
     *
     * <p>If the current thread
     * is {@link Thread#interrupt interrupted} while waiting
     * for permits then it will continue to wait and its position in the
     * queue is not affected. When the
     * thread does return from this method its interrupt status will be set.
     *
     * @param permits the number of permits to acquire
     * @throws IllegalArgumentException if permits less than zero.
     *
     */
    public void acquireUninterruptibly(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        doAcquire(permits, 0);
    }

    /**
     * Acquires the given number of permits from this semaphore, only if 
     * all are available at the  time of invocation.
     * <p>Acquires the given number of permits, if they are available, and 
     * returns immediately, with the value <tt>true</tt>,
     * reducing the number of available permits by the given amount.
     *
     * <p>If insufficient permits are available then this method will return
     * immediately with the value <tt>false</tt> and the number of available
     * permits is unchanged.
     *
     * @param permits the number of permits to acquire
     *
     * @return <tt>true</tt> if the permits were acquired and <tt>false</tt>
     * otherwise.
     * @throws IllegalArgumentException if permits less than zero.
     */
    public boolean tryAcquire(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        return doTryAcquire(permits) >= 0;
    }

    /**
     * Acquires the given number of permits from this semaphore, if all 
     * become available within the given waiting time and the
     * current thread has not been {@link Thread#interrupt interrupted}.
     * <p>Acquires the given number of permits, if they are available and 
     * returns immediately, with the value <tt>true</tt>,
     * reducing the number of available permits by the given amount.
     * <p>If insufficient permits are available then
     * the current thread becomes disabled for thread scheduling
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li>Some other thread invokes one of the {@link #release() release} 
     * methods for this semaphore, the current thread is next to be assigned
     * permits and the number of available permits satisfies this request; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     * <p>If the permits are acquired then the value <tt>true</tt> is returned.
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@link Thread#interrupt interrupted} while waiting to acquire
     * the permits,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * Any permits that were to be assigned to this thread, are instead 
     * assigned to the next waiting thread(s), as if
     * they had been made available by a call to {@link #release()}.
     *
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * If the time is
     * less than or equal to zero, the method will not wait at all.
     * Any permits that were to be assigned to this thread, are instead 
     * assigned to the next waiting thread(s), as if
     * they had been made available by a call to {@link #release()}.
     *
     * @param permits the number of permits to acquire
     * @param timeout the maximum time to wait for the permits
     * @param unit the time unit of the <tt>timeout</tt> argument.
     * @return <tt>true</tt> if all permits were acquired and <tt>false</tt>
     * if the waiting time elapsed before all permits were acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if permits less than zero.
     *
     * @see Thread#interrupt
     *
     */
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        if (unit == null)
            throw new NullPointerException();
        if (Thread.interrupted())
            throw new InterruptedException();
        return doTimedAcquire(permits, unit.toNanos(timeout));
    }


    /**
     * Releases the given number of permits, returning them to the semaphore.
     * <p>Releases the given number of permits, increasing the number of 
     * available permits by that amount.
     * If any threads are blocking trying to acquire permits, then the
     * one that has been waiting the longest
     * is selected and given the permits that were just released.
     * If the number of available permits satisfies that thread's request
     * then that thread is re-enabled for thread scheduling purposes; otherwise
     * the thread continues to wait. If there are still permits available
     * after the first thread's request has been satisfied, then those permits
     * are assigned to the next waiting thread. If it is satisfied then it is
     * re-enabled for thread scheduling purposes. This continues until there
     * are insufficient permits to satisfy the next waiting thread, or there
     * are no more waiting threads.
     *
     * <p>There is no requirement that a thread that releases a permit must
     * have acquired that permit by calling {@link Semaphore#acquire acquire}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     *
     * @param permits the number of permits to release
     * @throws IllegalArgumentException if permits less than zero.
     */
    public void release(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        doRelease(permits);
    }

    /**
     * Return the current number of permits available in this semaphore.
     * <p>This method is typically used for debugging and testing purposes.
     * @return the number of permits available in this semaphore.
     */
    public int availablePermits() {
        return perms.get();
    }

    /**
     * Shrink the number of available permits by the indicated
     * reduction. This method can be useful in subclasses that
     * use semaphores to track available resources that become
     * unavailable. This method differs from <tt>acquire</tt>
     * in that it does not block waiting for permits to become
     * available.
     * @param reduction the number of permits to remove
     * @throws IllegalArgumentException if reduction is negative
     */
    protected void reducePermits(int reduction) {
	if (reduction < 0) throw new IllegalArgumentException();
        perms.getAndAdd(-reduction);
    }

    /**
     * Return true if this semaphore has fairness set true.
     * @return true if this semaphore has fairness set true.
     */
    public boolean isFair() {
        return fair;
    }

    /**
     * Returns an estimate of the number of threads waiting to acquire
     * a permit. The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring of the system state, not for synchronization
     * control.
     * @return the estimated number of threads waiting for a permit
     */
    public int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null && p != head; p = p.prev)
            ++n;
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire a permit.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
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

}
