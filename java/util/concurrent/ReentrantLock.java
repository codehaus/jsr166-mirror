package java.util.concurrent;

/**
 * A reentrant mutual exclusion lock.  
 * <p><tt>ReentrantLock</tt> defines a stand-alone {@link Lock} class with 
 * the same basic behavior and semantics as the implicit
 * monitor lock accessed by the use of <tt>synchronized</tt> methods and
 * statements, but without the forced block-structured locking and unlocking
 * that occurs with <tt>synchronized</tt> methods and
 * statements. 
 * In a good implementation the performance characteristics of using a
 * <tt>ReentrantLock</tt> instance should be about the same as using 
 * monitors directly.
 *
 * <p><em>Only</em> use this class when you want normal locking semantics but
 * need to use the lock in a non-nested fashion.
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
 * {@link #newCondition Condition} implemenatation that supports the 
 * interruption of thread suspension.
 *
 * <p>Except where noted, passing a <tt>null</tt> value for any parameter 
 * will result in a {@link NullPointerException} being thrown.
 *
 * <h3>Implentation Notes</h3>
 * <p> To-BE_DONE
 *
 *
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/01/09 17:56:51 $
 * @editor $Author: dl $
 * 
 * @fixme (1) We need a non-nested example to motivate this
 * @fixme (2) Describe performance aspects of interruptible locking for the RI
 **/
public class ReentrantLock implements Lock {

    /**
     * Creates an instance of <tt>ReentrantLock</tt>
     */
    public ReentrantLock() {}

    /**
     * Acquirea the lock. 
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
    public void lock() {}

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
     * <h3>Implentation Notes</h3>
     * <p> To-BE_DONE
     *
     * @throws InterruptedException if the current thread is interrupted
     */
    public void lockInterruptibly() throws InterruptedException { }

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
     * <li>is {@link Thread#interrupt interrupted} while waiting to acquire 
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
     * <h3>Implentation Notes</h3>
     * <p> To-BE_DONE
     *
     * @return <tt>true</tt> if the lock was free and was acquired by the
     * current thread, or the lock was already held by the current thread; and
     * <tt>false</tt> if the waiting time elapsed before the lock could be 
     * acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     */
    public boolean tryLock(long timeout, TimeUnit granularity) throws InterruptedException {
        return false;
    }

    /**
     * Attempts to release this lock.
     * <p>If the current thread is the holder of this lock then the hold count
     * is decremented. If the hold count is now zero then the lock is released.
     * <p>If the current thread is not the holder of this lock then
     * {@link IllegalMonitorStateException} is thrown.
     * @throws IllegalMonitorStateExeception if the current thread does not
     * hold this lock.
     */
    public void unlock() {}

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
        return 0;
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
        return false;
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
        return null;
    }

}




