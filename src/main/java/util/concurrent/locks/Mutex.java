/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.locks;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * A non-reentrant mutual exclusion {@link Lock}. This class may be
 * preferable to {@link ReentrantLock} in a narrow range of
 * circumstances, including those in which it is not possible or
 * desirable for a thread that holds a lock to re-acquire it (which
 * will always block forever with a <tt>Mutex</tt>, but will succeed
 * with a <tt>ReentrantLock</tt>); and those where it is necessary for
 * a different thread than the one that locked a Lock to later unlock
 * it (which is not possible with <tt>ReentrantLock</tt>).
 *
 * <p> This class has semantics similar to those for POSIX (pthreads)
 * <tt>mutex</tt>. The associated {@link ConditionObject}
 * implementation is also similar except that, unlike POSIX versions,
 * it requires that the lock be held when invoking {@link
 * Condition#signal} and {@link Condition#signalAll}.
 *
 * * <p> Serialization of this class behaves in the same way as
 * built-in locks: a deserialized lock is in the unlocked state,
 * regardless of its state when serialized.
 *
 * @since 1.5
 * @author Doug Lea
 * 
 */
public class Mutex extends AbstractQueuedSynchronizer implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572514699L;

    /**
     * Creates an instance of <tt>Mutex</tt>.
     */
    public Mutex() { 
    }

    // implement abstract methods

    protected final int acquireExclusiveState(boolean isQueued, int acquires, 
                                              Thread current) {
        return (getState().compareAndSet(0, 1)) ? 0 : -1;
    }

    protected final boolean releaseExclusiveState(int releases) {
        getState().set(0);
        return true;
    }

    protected final void checkConditionAccess(Thread thread, boolean waiting) {
        if (getState().get() == 0) throw new IllegalMonitorStateException();
    }

    protected int acquireSharedState(boolean isQueued, int acquires, 
                                     Thread current) {
        throw new UnsupportedOperationException();
    }

    protected boolean releaseSharedState(int releases) {
        throw new UnsupportedOperationException();
    }


    /**
     * Acquires the lock. 
     *
     * <p>If the lock is already held then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * the lock has been acquired.
     */
    public void lock() {
        if (!getState().compareAndSet(0, 1)) 
            acquireExclusiveUninterruptibly(1);
    }

    /**
     * Acquires the lock unless the current thread is 
     * {@link Thread#interrupt interrupted}.
     *
     * <p>If the lock is already held by another thread then the
     * current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of two things happens:
     *
     * <ul>
     *
     * <li>The lock is acquired by the current thread; or
     *
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread.
     *
     * </ul>
     *
     * <p>If the lock is acquired by the current thread then the lock hold 
     * count is set to one.
     *
     * <p>If the current thread:
     *
     * <ul>
     *
     * <li>has its interrupted status set on entry to this method; or 
     *
     * <li>is {@link Thread#interrupt interrupted} while acquiring 
     * the lock,
     *
     * </ul>
     *
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. 
     *
     * <p>In this implementation, as this method is an explicit interruption 
     * point, preference is 
     * given to responding to the interrupt over normal or reentrant 
     * acquisition of the lock.
     *
     * @throws InterruptedException if the current thread is interrupted
     */
    public void lockInterruptibly() throws InterruptedException { 
        acquireExclusiveInterruptibly(1);
    }

    /**
     * Acquires the lock only if it is not held at the time
     * of invocation.
     *
     * <p>Acquires the lock if it is not already held and returns with
     * the value <tt>true</tt>.  If the lock is held by another thread
     * then this method will return immediately with the value
     * <tt>false</tt>.
     *
     * @return <tt>true</tt> if the lock was free and was acquired;
     * and <tt>false</tt> otherwise.
     */
    public boolean tryLock() {
        return getState().compareAndSet(0, 1);
    }

    /**
     * Acquires the lock if it is not held within the given waiting
     * time and the current thread has not been {@link
     * Thread#interrupt interrupted}.
     *
     * <p>Acquires the lock if it is not already held and returns
     * immediately with the value <tt>true</tt> If the lock is already
     * held then the current thread becomes disabled for thread
     * scheduling purposes and lies dormant until one of three things
     * happens:
     *
     * <ul>
     *
     * <li>The lock is acquired by the current thread; or
     *
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     *
     * <li>The specified waiting time elapses
     *
     * </ul>
     *
     * <p>If the current thread:
     *
     * <ul>
     *
     * <li>has its interrupted status set on entry to this method; or 
     *
     * <li>is {@link Thread#interrupt interrupted} while acquiring
     * the lock,
     *
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's 
     * interrupted status is cleared. 
     *
     * <p>If the specified waiting time elapses then the value
     * <tt>false</tt> is returned.  If the time is less than or equal
     * to zero, the method will not wait at all.
     *
     * <p>In this implementation, as this method is an explicit
     * interruption point, preference is given to responding to the
     * interrupt over normal or reentrant acquisition of the lock, and
     * over reporting the elapse of the waiting time.
     *
     * @param timeout the time to wait for the lock
     * @param unit the time unit of the timeout argument
     *
     * @return <tt>true</tt> if the lock was free and was acquired by
     * the current thread; and <tt>false</tt> if the waiting time
     * elapsed before the lock could be acquired.
     *
     * @throws InterruptedException if the current thread is interrupted
     * @throws NullPointerException if unit is null
     *
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return acquireExclusiveTimed(1, unit.toNanos(timeout));
    }

    /**
     * Releases this lock.  Has no effect if already unlocked.
     */
    public void unlock() {
        releaseExclusive(1);
    }

    /**
     * Returns a {@link Condition} instance for use with this 
     * {@link Lock} instance.
     * @return the Condition object
     */
    public ConditionObject newCondition() {
        return new ConditionObject();
    }

    /**
     * Queries if this lock is held. This method is designed for use
     * in monitoring of the system state, not for synchronization
     * control.
     * @return <tt>true</tt> if this lock is held and <tt>false</tt>
     * otherwise.
     */
    public boolean isLocked() {
        return getState().get() != 0;
    }

    /**
     * Reconstitute this lock instance from a stream (that is,
     * deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        getState().set(0); // reset to unlocked state
    }

    /**
     * Condition implementation. Methods may be invoked only when the
     * lock associated with this Condition is held by current thread.
     * If the lock is not held, attempts to access any condition
     * methods result in {@link IllegalMonitorStateException}.
     * However, this check does not strictly guarantee that the lock
     * is held by the calling thread. The effects of condition methods
     * accessed by threads that do not hold the associated lock are
     * undefined.
     *
     */
    public class ConditionObject extends AbstractQueuedSynchronizer.LockCondition {
        /** Constructor for use by subclasses */
        protected ConditionObject() {}
    }

}
