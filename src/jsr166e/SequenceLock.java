/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package jsr166e;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.AbstractQueuedLongSynchronizer;
import java.util.Collection;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

/**
 * A reentrant mutual exclusion {@link Lock} in which each lock
 * acquisition or release advances a sequence number.  When the
 * sequence number (accessible using {@link #getSequence()}) is odd,
 * the lock is held. When it is even (i.e., ({@code lock.getSequence()
 * & 1L) == 0L}), the lock is released. Method {@link
 * #awaitAvailability} can be used to await availability of the lock,
 * returning its current sequence number. Sequence numbers are of type
 * {@code long} to ensure that they will not wrap around until
 * hundreds of years of use under current processor rates.  A
 * SequenceLock can be created with a specified number of
 * spins. Attempts to lock or await release retry at least the given
 * number of times before blocking. If not specified, a default,
 * possibly platform-specific, value is used.
 *
 * <p>Except for the lack of support for specified fairness policies,
 * or {link Condition} objects, a SequenceLock can be used in the same
 * way as {@link ReentrantLock}, and has a nearly identical
 * API. SequenceLocks may be preferable in contexts in which multiple
 * threads invoke read-only methods much more frequently than fully
 * locked methods.
 * 
 * <p> Methods {@code awaitAvailability} and {@code getSequence} can
 * be used together to define (partially) optimistic read-only methods
 * that are usually more efficient than ReadWriteLocks when they
 * apply.  These read-only methods typically read multiple field
 * values into local variables when the lock is not held, retrying if
 * the sequence number changed while doing so.  Alternatively, because
 * {@code awaitAvailability} accommodates reentrancy, a method can
 * retry a bounded number of times before switching to locking mode.
 * While conceptually straightforward, expressing these ideas can be
 * verbose. For example:
 *
 * <pre> {@code
 * class Point {
 *     private float x, y;
 *     private final SequenceLock sl = new SequenceLock();
 *
 *     void move(float deltaX, float deltaY) { // an excluively locked method
 *        sl.lock();
 *        try {
 *            x += deltaX;
 *            y += deltaY;
 *        } finally {
 *          sl.unlock();
 *      }
 *  }
 *
 *  float distanceFromOriginV1() { // A read-only method
 *      float currentX, currentY;
 *      long seq;
 *      do {
 *          seq = sl.awaitAvailability();
 *          currentX = x;
 *          currentY = y;
 *      } while (sl.getSequence() != seq); // retry if sequence changed
 *      return (float)Math.sqrt(currentX * currentX + currentY * currentY);
 *  }
 *
 *  float distanceFromOriginV2() { // Uses bounded retries before locking
 *      float currentX, currentY;
 *      long seq;
 *      int retries = RETRIES_BEFORE_LOCKING; // for example 8
 *      try {
 *        do {
 *           if (--retries < 0)
 *              sl.lock();
 *           seq = sl.awaitAvailability();
 *           currentX = x;
 *           currentY = y;
 *        } while (sl.getSequence() != seq);
 *      } finally {
 *        if (retries < 0)
 *           sl.unlock();
 *      }
 *      return (float)Math.sqrt(currentX * currentX + currentY * currentY);
 *  }
 *}}</pre>
 *
 * @since 1.8
 * @author Doug Lea
 */
public class SequenceLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;

    static final class Sync extends AbstractQueuedLongSynchronizer {
        /**
         * The number of times to spin in lock() and awaitAvailability().
         */
        final int spins;

        /**
         * The number of reentrant holds on this lock. Uses a long for
         * compatibility with other AbstractQueuedLongSynchronizer
         * operations.
         */
        long holds;

        Sync(int spins) { this.spins = spins; }

        // overrides of AQLS methods

        public final boolean isHeldExclusively() {
            return (getState() & 1L) != 0L &&
                getExclusiveOwnerThread() == Thread.currentThread();
        }
        
      public final boolean tryAcquire(long acquires) {
            Thread current = Thread.currentThread();
            long c = getState();
            if ((c & 1L) == 0L) {
                if (compareAndSetState(c, c + 1L)) {
                    holds = acquires;
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            else if (current == getExclusiveOwnerThread()) {
                holds += acquires;
                return true;
            }
            return false;
        }
        
        public final boolean tryRelease(long releases) {
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();
            if ((holds -= releases) == 0L) {
                setExclusiveOwnerThread(null);
                setState(getState() + 1L);
                return true;
            }
            return false;
        }

        public final long tryAcquireShared(long unused) {
            return ((getState() & 1L) == 0L || 
                    getExclusiveOwnerThread() == Thread.currentThread())? 
                1L : -1L; // must return long
        }

        public final boolean tryReleaseShared(long unused) {
            return true;
        }

        public final Condition newCondition() { 
            throw new UnsupportedOperationException();
        }

        // Other methods in support of SequenceLock

        final long getSequence() {
            return getState();
        }
        
        final void lock() {
            int k = spins;
            while (!tryAcquire(1)) {
                if (k == 0) {
                    acquire(1);
                    break;
                }
                --k;
            }
        }
        
        final long awaitAvailability() {
            long s;
            int k = spins;
            while (((s = getState()) & 1L) != 0L &&
                   getExclusiveOwnerThread() != Thread.currentThread()) {
                if (k > 0)
                    --k;
                else {
                    acquireShared(1);
                    releaseShared(1);
                }
            }
            return s;
        }

        final boolean isLocked() {
            return (getState() & 1L) != 0L;
        }

        final Thread getOwner() {
            return (getState() & 1L) == 0L ? null : getExclusiveOwnerThread();
        }

        final long getHoldCount() {
            return isHeldExclusively()? holds : 0;
        }

        private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
            s.defaultReadObject();
            holds = 0L;
            setState(0L); // reset to unlocked state
        }
    }

    private final Sync sync;

    /** 
     * The default spin value for constructor. Future versions of this
     * class might choose platform-specific values.  Currently, except
     * on uniprocessors, it is set to a small value that ovecomes near
     * misses between releases and acquires.
     */
    static final int DEFAULT_SPINS = 
        Runtime.getRuntime().availableProcessors() > 1 ? 64 : 0;

    /**
     * Creates an instance of {@code SequenceLock} with the default
     * number of retry attempts to lock or await release before
     * blocking.
     */
    public SequenceLock() { sync = new Sync(DEFAULT_SPINS); }

    /**
     * Creates an instance of {@code SequenceLock} that
     * will retry attempts to lock or await release 
     * at least the given number times before blocking.
     */
    public SequenceLock(int spins) { sync = new Sync(spins); }

    /**
     * Returns the current sequence number of this lock.  The sequence
     * number is advanced upon each lock or unlock action. When this
     * value is odd, the lock is held; when even, it is released.
     *
     * @return the current sequence number
     */
    public long getSequence() { return sync.getSequence(); }

    /**
     * Returns the current sequence number when the lock is, or
     * becomes, available. A lock is available if it is either
     * released, or is held by the current thread.  If the lock is not
     * available, the current thread becomes disabled for thread
     * scheduling purposes and lies dormant until the lock has been
     * released by some other thread.
     *
     * @return the current sequence number
     */
    public long awaitAvailability() { return sync.awaitAvailability(); }
    
    /**
     * Acquires the lock.
     *
     * <p>Acquires the lock if it is not held by another thread and returns
     * immediately, setting the lock hold count to one.
     *
     * <p>If the current thread already holds the lock then the hold
     * count is incremented by one and the method returns immediately.
     *
     * <p>If the lock is held by another thread then the
     * current thread becomes disabled for thread scheduling
     * purposes and lies dormant until the lock has been acquired,
     * at which time the lock hold count is set to one.
     */
    public void lock() { sync.lock(); }

    /**
     * Acquires the lock unless the current thread is
     * {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires the lock if it is not held by another thread and returns
     * immediately, setting the lock hold count to one.
     *
     * <p>If the current thread already holds this lock then the hold count
     * is incremented by one and the method returns immediately.
     *
     * <p>If the lock is held by another thread then the
     * current thread becomes disabled for thread scheduling
     * purposes and lies dormant until one of two things happens:
     *
     * <ul>
     *
     * <li>The lock is acquired by the current thread; or
     *
     * <li>Some other thread {@linkplain Thread#interrupt interrupts} the
     * current thread.
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
     * <li>is {@linkplain Thread#interrupt interrupted} while acquiring
     * the lock,
     *
     * </ul>
     *
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>In this implementation, as this method is an explicit
     * interruption point, preference is given to responding to the
     * interrupt over normal or reentrant acquisition of the lock.
     *
     * @throws InterruptedException if the current thread is interrupted
     */
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    /**
     * Acquires the lock only if it is not held by another thread at the time
     * of invocation.
     *
     * <p>Acquires the lock if it is not held by another thread and
     * returns immediately with the value {@code true}, setting the
     * lock hold count to one. 
     *
     * <p> If the current thread already holds this lock then the hold
     * count is incremented by one and the method returns {@code true}.
     *
     * <p>If the lock is held by another thread then this method will return
     * immediately with the value {@code false}.
     *
     * @return {@code true} if the lock was free and was acquired by the
     *         current thread, or the lock was already held by the current
     *         thread; and {@code false} otherwise
     */
    public boolean tryLock() { return sync.tryAcquire(1); }

    /**
     * Acquires the lock if it is not held by another thread within the given
     * waiting time and the current thread has not been
     * {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires the lock if it is not held by another thread and returns
     * immediately with the value {@code true}, setting the lock hold count
     * to one. If this lock has been set to use a fair ordering policy then
     * an available lock <em>will not</em> be acquired if any other threads
     * are waiting for the lock. This is in contrast to the {@link #tryLock()}
     * method. If you want a timed {@code tryLock} that does permit barging on
     * a fair lock then combine the timed and un-timed forms together:
     *
     *  <pre> {@code
     * if (lock.tryLock() ||
     *     lock.tryLock(timeout, unit)) {
     *   ...
     * }}</pre>
     *
     * <p>If the current thread
     * already holds this lock then the hold count is incremented by one and
     * the method returns {@code true}.
     *
     * <p>If the lock is held by another thread then the
     * current thread becomes disabled for thread scheduling
     * purposes and lies dormant until one of three things happens:
     *
     * <ul>
     *
     * <li>The lock is acquired by the current thread; or
     *
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     *
     * <li>The specified waiting time elapses
     *
     * </ul>
     *
     * <p>If the lock is acquired then the value {@code true} is returned and
     * the lock hold count is set to one.
     *
     * <p>If the current thread:
     *
     * <ul>
     *
     * <li>has its interrupted status set on entry to this method; or
     *
     * <li>is {@linkplain Thread#interrupt interrupted} while
     * acquiring the lock,
     *
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * <p>In this implementation, as this method is an explicit
     * interruption point, preference is given to responding to the
     * interrupt over normal or reentrant acquisition of the lock, and
     * over reporting the elapse of the waiting time.
     *
     * @param timeout the time to wait for the lock
     * @param unit the time unit of the timeout argument
     * @return {@code true} if the lock was free and was acquired by the
     *         current thread, or the lock was already held by the current
     *         thread; and {@code false} if the waiting time elapsed before
     *         the lock could be acquired
     * @throws InterruptedException if the current thread is interrupted
     * @throws NullPointerException if the time unit is null
     *
     */
    public boolean tryLock(long timeout, TimeUnit unit)
        throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    /**
     * Attempts to release this lock.
     *
     * <p>If the current thread is the holder of this lock then the hold
     * count is decremented.  If the hold count is now zero then the lock
     * is released.  If the current thread is not the holder of this
     * lock then {@link IllegalMonitorStateException} is thrown.
     *
     * @throws IllegalMonitorStateException if the current thread does not
     *         hold this lock
     */
    public void unlock()              { sync.release(1); }

    /**
     * Throws UnsupportedOperationException. SequenceLocks
     * do not support Condition objects.
     *
     * @throws UnsupportedOperationException
     */
    public Condition newCondition()   { 
        throw new UnsupportedOperationException();
    }

    /**
     * Queries the number of holds on this lock by the current thread.
     *
     * <p>A thread has a hold on a lock for each lock action that is not
     * matched by an unlock action.
     *
     * <p>The hold count information is typically only used for testing and
     * debugging purposes. 
     *
     * @return the number of holds on this lock by the current thread,
     *         or zero if this lock is not held by the current thread
     */
    public long getHoldCount() { return sync.getHoldCount();  }

    /**
     * Queries if this lock is held by the current thread.
     *
     * @return {@code true} if current thread holds this lock and
     *         {@code false} otherwise
     */
    public boolean isHeldByCurrentThread() { return sync.isHeldExclusively(); }

    /**
     * Queries if this lock is held by any thread. This method is
     * designed for use in monitoring of the system state,
     * not for synchronization control.
     *
     * @return {@code true} if any thread holds this lock and
     *         {@code false} otherwise
     */
    public boolean isLocked() { return sync.isLocked(); }

    /**
     * Returns the thread that currently owns this lock, or
     * {@code null} if not owned. When this method is called by a
     * thread that is not the owner, the return value reflects a
     * best-effort approximation of current lock status. For example,
     * the owner may be momentarily {@code null} even if there are
     * threads trying to acquire the lock but have not yet done so.
     * This method is designed to facilitate construction of
     * subclasses that provide more extensive lock monitoring
     * facilities.
     *
     * @return the owner, or {@code null} if not owned
     */
    protected Thread getOwner() { return sync.getOwner(); }

    /**
     * Queries whether any threads are waiting to acquire this lock. Note that
     * because cancellations may occur at any time, a {@code true}
     * return does not guarantee that any other thread will ever
     * acquire this lock.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @return {@code true} if there may be other threads waiting to
     *         acquire the lock
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Queries whether the given thread is waiting to acquire this
     * lock. Note that because cancellations may occur at any time, a
     * {@code true} return does not guarantee that this thread
     * will ever acquire this lock.  This method is designed primarily for use
     * in monitoring of the system state.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is queued waiting for this lock
     * @throws NullPointerException if the thread is null
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire this lock.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring of the system state, not for synchronization
     * control.
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire this lock.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * Returns a string identifying this lock, as well as its lock state.
     * The state, in brackets, includes either the String {@code "Unlocked"}
     * or the String {@code "Locked by"} followed by the
     * {@linkplain Thread#getName name} of the owning thread.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ?
                                   "[Unlocked]" :
                                   "[Locked by thread " + o.getName() + "]");
    }

}
            
