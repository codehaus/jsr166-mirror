/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
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
 * <tt>mutex</tt>. There are no garanteed fairness properties. The
 * associated {@link Condition} implementation is also similar
 * except that, unlike POSIX versions, it requires that the lock be
 * held when invoking {@link Condition#signal} and {@link
 * Condition#signalAll}.
 *
 * <p> Serialization of this class behaves in the same way as
 * built-in locks: a deserialized lock is in the unlocked state,
 * regardless of its state when serialized.
 *
 * @since 1.5
 * @author Doug Lea
 * 
 */
public class Mutex implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572514699L;
    /** Sync mechanics use AbstractQueuedSynchronizer inplementation */
    private final Sync sync = new Sync();

    private final static class Sync extends AbstractQueuedSynchronizer {
        /*
         * Implement using 0 for unlocked, 1 for locked.
         */

        public final int acquireExclusiveState(boolean isQueued, 
                                               int acquires, 
                                               Thread current) {
            return (getState().compareAndSet(0, 1)) ? 0 : -1;
        }
        
        public final boolean releaseExclusiveState(int releases) {
            getState().set(0);
            return true;
        }
        
        public final void checkConditionAccess(Thread thread, boolean waiting) {
            if (getState().get() == 0) throw new IllegalMonitorStateException();
        }
        
        
        public final int acquireSharedState(boolean isQueued, int acquires, 
                                            Thread current) {
            throw new UnsupportedOperationException();
        }
        
        
        public final boolean releaseSharedState(int releases) {
            throw new UnsupportedOperationException();
        }
        
        ConditionObject newCondition() { return new ConditionObject(); }
        
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

    }

    /**
     * Creates an instance of <tt>Mutex</tt>.
     */
    public Mutex() { 
    }

    /**
     * Acquires the lock. 
     *
     * <p>If the lock is already held then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * the lock has been acquired.
     */
    public void lock() {
        if (!sync.getState().compareAndSet(0, 1)) 
            sync.acquireExclusiveUninterruptibly(1);
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
        sync.acquireExclusiveInterruptibly(1);
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
        return sync.getState().compareAndSet(0, 1);
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
        return sync.acquireExclusiveTimed(1, unit.toNanos(timeout));
    }

    /**
     * Releases this lock.  Has no effect if already unlocked.
     */
    public void unlock() {
        sync.releaseExclusive(1);
    }

    /**
     * Returns a {@link Condition} instance for use with this 
     * {@link Lock} instance.     
     * Condition implementation. Methods may be invoked only when the
     * lock associated with this Condition is held by current thread.
     * If the lock is not held, attempts to access any condition
     * methods result in {@link IllegalMonitorStateException}.
     * However, this check does not strictly guarantee that the lock
     * is held by the calling thread. The effects of condition methods
     * accessed by threads that do not hold the associated lock are
     * undefined.
     *
     * @return the Condition object
     */
    public AbstractQueuedSynchronizer.ConditionObject newCondition() {
        return sync.newCondition();
    }

    /**
     * Queries if this lock is held. This method is designed for use
     * in monitoring of the system state, not for synchronization
     * control.
     * @return <tt>true</tt> if this lock is held and <tt>false</tt>
     * otherwise.
     */
    public boolean isLocked() {
        return sync.getState().get() != 0;
    }

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations may occur at any time, a <tt>true</tt>
     * return does not guarantee that any other thread will ever
     * acquire.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @return true if there may be other threads waiting to acquire
     * the lock.
     */
    public final boolean hasWaiters() { 
        return sync.hasWaiters();
    }


    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring of the system state, not for synchronization
     * control.
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

}
