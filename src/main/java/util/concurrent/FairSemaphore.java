/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.locks.*;

/**
 * A semaphore guaranteeing that threads invoking any of the {@link
 * #acquire() acquire} methods are allocated permits in the order in
 * which their invocation of those methods was processed
 * (first-in-first-out; FIFO). Note that FIFO ordering necessarily
 * applies to specific internal points of execution within these
 * methods.  So, it is possible for one thread to invoke
 * <tt>acquire</tt> before another, but reach the ordering point after
 * the other, and similarly upon return from the method.
 *
 * <p>Because permits are allocated in-order, this class also provides
 * convenience methods to {@link #acquire(long) acquire} and {@link
 * #release(long) release} multiple permits at a time. 
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/07/11 13:12:06 $
 * @editor $Author: dl $
 * @author Doug Lea
 *
 */
public class FairSemaphore extends Semaphore {

    /*
     * Basic algorithm is a variant of the one described
     * in CPJ book 2nd edition, section 3.7.3
     */

    /**
     * Nodes of the wait queue. Opportunistically subclasses ReentrantLock.
     */
    private static class Node extends ReentrantLock {
        /** The condition to wait on */
        Condition done = newCondition();
        /** True if interrupted before released */
        boolean cancelled;
        /** Number of permits remaining until can release */
        long permitsNeeded;
        /** Next node of queue */
        Node next;

        Node(long n) { permitsNeeded = n; }
    }

    /** Head of the wait queue */
    private Node head;

    /** Pointer to last node of the wait queue */
    private Node last;

    /** Add a new node to wait queue with given number of permits needed */
    private Node enq(long n) { 
        Node p = new Node(n);
        if (last == null) 
            last = head = p;
        else 
            last = last.next = p;
        return p;
    }

    /** Remove first node from wait queue */
    private void removeFirst() {
        Node p = head;
        assert p != null;
        if (p != null && (head = p.next) == null) 
            last = null;
    }

    /**
     * Construct a <tt>FairSemaphore</tt> with the given number of
     * permits.
     * @param permits the initial number of permits available
     */
    public FairSemaphore(long permits) { 
        super(permits, new ReentrantLock(true)); 
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
     * semaphore and the current thread happens to be chosen as the
     * thread to receive the permit; or
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
        acquire(1L);
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
     * semaphore and the current thread happens to be chosen as the
     * thread to receive the permit.
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
        acquireUninterruptibly(1L);
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
        return tryAcquire(1L);
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
     * semaphore and the current thread happens to be chosen as the
     * thread to receive the permit; or
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
     * The given waiting time is a best-effort lower bound. If the time is
     * less than or equal to zero, the method will not wait at all.
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
        return tryAcquire(1L, timeout, unit);
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
        release(1L);
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
     * Any available permits are assigned to the next waiting thread as if
     * they had been made available by a call to {@link #release()}.
     *
     * @param permits the number of permits to acquire
     *
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if permits less than zero.
     *
     * @see Thread#interrupt
     */
    public void acquire(long permits) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        Node node = null;
        lock.lockInterruptibly();
        try {
            long remaining = count - permits;
            if (remaining >= 0) {
                count = remaining;
                return;
            }
            count = 0;
            node = enq(-remaining);
        }
        finally {
            lock.unlock();
        }

        node.lock();
        try {
            while (node.permitsNeeded > 0) 
                node.done.await();
        }
        catch(InterruptedException ie) {
            if (node.permitsNeeded > 0) {
                node.cancelled = true;
                release(permits - node.permitsNeeded);
                throw ie;
            }
            else { // ignore interrupt
                Thread.currentThread().interrupt();
            }
        }
        finally {
            node.unlock();
        }
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
    public void acquireUninterruptibly(long permits) {
        if (permits < 0) throw new IllegalArgumentException();
        Node node = null;
        lock.lock();
        try {
            long remaining = count - permits;
            if (remaining >= 0) {
                count = remaining;
                return;
            }
            count = 0;
            node = enq(-remaining);
        }
        finally {
            lock.unlock();
        }

        node.lock();
        try {
            while (node.permitsNeeded > 0) 
                node.done.awaitUninterruptibly();
        }
        finally {
            node.unlock();
        }
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
    public boolean tryAcquire(long permits) {
        if (permits < 0) throw new IllegalArgumentException();
        lock.lock();
        try {
            long remaining = count - permits;
            if (remaining >= 0) {
                count = remaining;
                return true;
            }
            return false;
        }
        finally {
            lock.unlock();
        }
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
     * Any available permits are assigned to the next waiting thread as if
     * they had been made available by a call to {@link #release()}.
     *
     * <p>If the specified waiting time elapses then the value <tt>false</tt>
     * is returned.
     * The given waiting time is a best-effort lower bound. If the time is
     * less than or equal to zero, the method will not wait at all.
     * Any available permits are assigned to the next waiting thread as if
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
    public boolean tryAcquire(long permits, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        Node node = null;
        long nanos = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            long remaining = count - permits;
            if (remaining >= 0) {
                count = remaining;
                return true;
            }
            if (nanos <= 0)
                return false;
            count = 0;
            node = enq(-remaining);
        }
        finally {
            lock.unlock();
        }
        
        node.lock();
        try {
            while (node.permitsNeeded > 0) {
                nanos = node.done.awaitNanos(nanos);
                if (nanos <= 0) {
                    release(permits - node.permitsNeeded);
                    return false;
                }
            }
            return true;
        }
        catch(InterruptedException ie) {
            if (node.permitsNeeded > 0) {
                node.cancelled = true;
                release(permits - node.permitsNeeded);
                throw ie;
            }
            else { // ignore interrupt
                Thread.currentThread().interrupt();
                return true;
            }
        }
        finally {
            node.unlock();
        }
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
    public void release(long permits) {
        if (permits < 0) throw new IllegalArgumentException();
        if (permits == 0)
            return;
        // Even when using fair locks, releases should try to barge in.
        if (!lock.tryLock())
            lock.lock();
        try {
            long p = permits;
            while (p > 0) {
                Node node = head;
                if (node == null) {
                    count += p;
                    p = 0;
                }
                else {
                    node.lock();
                    try {
                        if (node.cancelled)
                            removeFirst();
                        else if (node.permitsNeeded > p) {
                            node.permitsNeeded -= p;
                            p = 0;
                        }
                        else {
                            p -= node.permitsNeeded;
                            node.permitsNeeded = 0;
                            node.done.signal();
                            removeFirst();
                        }
                    }
                    finally {
                        node.unlock();
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
    }
}
