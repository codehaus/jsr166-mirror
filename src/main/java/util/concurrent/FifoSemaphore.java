package java.util.concurrent;

/**
 * A semaphore that guarantees that threads invoking
 * any of the {@link #acquire() acquire} methods 
 * are allocated permits in the order in 
 * which their invocation of those methods was processed (first-in-first-out).
 *
 * <p>This class introduces a fairness guarantee that can be useful
 * in some contexts.
 * However, it needs to be realized that the order in which invocations are
 * processed can be different from the order in which an application perceives
 * those invocations to be processed. Effectively, when no permit is available
 * each thread is stored in a FIFO queue. As permits are released, they
 * are allocated to the thread at the head of the queue, and so the order
 * in which threads enter the queue, is the order in which they come out.  
 * This order need not have any relationship to the order in which requests 
 * were made, nor the order in which requests actually return to the caller as 
 * these depend on the underlying thread scheduling, which is not guaranteed 
 * to be predictable or fair.
 *
 * <p>As permits are allocated in-order, this class also provides convenience
 * methods to {@link #acquire(long) acquire} and 
 * {@link #release(long) release} multiple permits at a time. 
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/05/14 21:30:46 $
 * @editor $Author: tim $
 *
 */
public class FifoSemaphore extends Semaphore {

    /**
     * Construct a <tt>FiFoSemaphore</tt> with the given number of
     * permits.
     * @param permits the initial number of permits available
     */
    public FifoSemaphore(long permits) { 
        super(permits); 
    }

    /**
     * Acquires a permit from this semaphore, blocking until one is
     * available, or the thread is {@link Thread#interrupt interrupted}.
     *
     * <p>This is equivalent to invoking {@link #acquire(long) acquire(1)}.
     *
     * @throws InterruptedException if the current thread is interrupted
     *
     * @see Thread#interrupt
     */
    public void acquire() throws InterruptedException {
        acquire(1);
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
     *
     * @see Thread#interrupt
     */
    public void acquire(long permits) throws InterruptedException {}

    /**
     * Acquires a permit from this semaphore, blocking until one is
     * available.
     *
     * <p>This is equivalent to invoking 
     * {@link #acquireUninterruptibly(long) acquireUninterruptibly(1)}.
     *
     */
    public void acquireUninterruptibly() {
        acquireUninterruptibly(1);
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
     *
     */
    public void acquireUninterruptibly(long permits) {}

    /**
     * Acquires a permit from this semaphore, only if one is available at 
     * the time of invocation.
     *
     * <p>This is equivalent to invoking 
     * {@link #tryAcquire(long) tryAcquire(1)}.
     *
     * @return <tt>true</tt> if a permit was acquired and <tt>false</tt>
     * otherwise.
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
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
     */
    public boolean tryAcquire(long permits) {
        return false;
    }

    /**
     * Acquires a permit from this semaphore, if one becomes available 
     * within the given waiting time and the
     * current thread has not been {@link Thread#interrupt interrupted}.
     *
     * <p>This is equivalent to invoking 
     * {@link #tryAcquire(long, long, TimeUnit) 
     * tryAcquire(1, timeout, granularity)}.
     *
     * @param timeout the maximum time to wait for a permit
     * @param granularity the time unit of the <tt>timeout</tt> argument.
     * @return <tt>true</tt> if a permit was acquired and <tt>false</tt>
     * if the waiting time elapsed before a permit was acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     *
     * @see Thread#interrupt
     *
     */
    public boolean tryAcquire(long timeout, TimeUnit granularity)
        throws InterruptedException {
        return tryAcquire(1, timeout, granularity);
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
     *
     * @see Thread#interrupt
     *
     */
    public boolean tryAcquire(long permits, long timeout, TimeUnit granularity)
        throws InterruptedException {
        return false;
    }



    /**
     * Releases a permit, returning it to the semaphore.
     * <p>This is equivalent to invoking {@link #release(long) release(1)}.
     */
    public void release() {
        release(1);
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
     */
    public void release(long permits) {}

}





