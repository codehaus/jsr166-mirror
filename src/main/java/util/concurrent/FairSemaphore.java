/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

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
 * @revised $Date: 2003/06/24 14:34:48 $
 * @editor $Author: dl $
 * @author Doug Lea
 *
 */
public class FairSemaphore extends Semaphore {

    /*
     * This differs from Semaphore only in that it uses a
     * FairReentrantLock.  Because the FairReentrantLock guarantees
     * FIFO queuing, we don't need to do anything fancy to prevent
     * overtaking etc.  for the multiple-permit methods. The only
     * minor downside is that multi-permit acquires will sometimes
     * repeatedly wake up finding that they must re-wait. A custom
     * solution could minimize this, at the expense of being slower
     * and more complex for the typical case.
     */

    /**
     * Construct a <tt>FiFoSemaphore</tt> with the given number of
     * permits.
     * @param permits the initial number of permits available
     */
    public FairSemaphore(long permits) { 
        super(permits, new FairReentrantLock()); 
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

        lock.lockInterruptibly();
        try {
            while (count - permits < 0) 
                available.await();
            count -= permits;
        }
        catch (InterruptedException ie) {
            available.signal();
            throw ie;
        }
        finally {
            lock.unlock();
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

        lock.lock();
        try {
            while (count - permits < 0) 
                available.awaitUninterruptibly();
            count -= permits;
        }
        finally {
            lock.unlock();
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
            if (count - permits >= 0) {
                count -= permits;
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
     * @param granularity the time unit of the <tt>timeout</tt> argument.
     * @return <tt>true</tt> if all permits were acquired and <tt>false</tt>
     * if the waiting time elapsed before all permits were acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalArgumentException if permits less than zero.
     *
     * @see Thread#interrupt
     *
     */
    public boolean tryAcquire(long permits, long timeout, TimeUnit granularity)
        throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        lock.lockInterruptibly();
        long nanos = granularity.toNanos(timeout);
        try {
            for (;;) {
                if (count - permits >= 0) {
                    count -= permits;
                    return true;
                }
                if (nanos <= 0)
                    return false;
                nanos = available.awaitNanos(nanos);
            }
        }
        catch (InterruptedException ie) {
            available.signal();
            throw ie;
        }
        finally {
            lock.unlock();
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
        lock.lock();
        try {
            count += permits;
            for (int i = 0; i < permits; ++i)
                available.signal();
        }
        finally {
            lock.unlock();
        }
    }
}
