/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import java.lang.reflect.*;
import sun.misc.*;

/**
 * A reentrant mutual exclusion {@link Lock} with the same basic
 * behavior and semantics as the implicit monitor lock accessed using
 * <tt>synchronized</tt> methods and statements, but with extended
 * capabilities.
 *
 * <p> A <tt>ReentrantLock</tt> is <em>owned</em> by the thread last
 * successfully locking, but not yet unlocking it. A thread invoking
 * <tt>lock</tt> will return, successfully acquiring the lock, when
 * the lock is not owned by another thread. The method will return
 * immediately if the current thread already owns the lock. This can
 * be checked using methods {@link #isHeldByCurrentThread}, and {@link
 * #getHoldCount}.  A <tt>ReentrantLock</tt> may be used in a
 * non-reentrant way by checking that the lock is not already held by
 * the current thread prior to locking.
 *
 * <p> The constructor for this class accepts an optional
 * <em>fairness</em> parameter.  When set <tt>true</tt>, under
 * contention, locks favor granting access to the longest-waiting
 * thread.  Otherwise this lock does not guarantee any particular
 * access order.  Programs using fair locks accessed by many threads
 * may display lower overall throughput (i.e., are slower; often much
 * slower) than those using the default setting, but have smaller
 * variances in times to obtain locks and guarantee lack of
 * starvation. Note however, that fairness of locks does not guarantee
 * fairness of thread scheduling. Thus, one of many threads using a
 * fair lock may obtain it multiple times in succession while other
 * active threads are not progressing and not currently holding the
 * lock.
 *
 * <p> It is recommended practice to <em>always</em> immediately
 * follow a call to <tt>lock</tt> with a <tt>try</tt> block, most
 * typically in a before/after construction such as:
 *
 * <pre>
 * class X {
 *   private final ReentrantLock lock = new ReentrantLock();
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
 * <p>In addition to implementing the {@link Lock} interface, this
 * class defines methods <tt>isLocked</tt> and
 * <tt>getLockQueueLength</tt>, as well as some associated
 * <tt>protected</tt> access methods that may be useful for
 * instrumentation and monitoring.  This implementation also provides
 * a {@link #newCondition Condition} implementation that supports the
 * interruption of thread suspension.
 *
 * @since 1.5
 * @author Doug Lea
 * 
 */
public class ReentrantLock extends AbstractReentrantLock implements Lock, java.io.Serializable {
    /*
     * See the internal documentation for AbstractReentrantLock for
     * description of approach and algorithm.
     */

    /**
     * Serialization ID. Note that all fields are defined in a way so
     * that deserialized locks are in initial unlocked state, and
     * there is no explicit serialization code.
     */
    private static final long serialVersionUID = 7373984872572414699L;

    /** 
     * Current owner of lock, or null iff the lock is free.  Acquired
     * only using CAS.  The fast path to acquire lock uses one atomic
     * CAS operation, plus one StoreLoad barrier (i.e.,
     * volatile-write-barrier) per lock/unlock pair.  The "owner"
     * field is handled as a simple spinlock.  To lock, the owner
     * field is set to current thread using conditional atomic update.
     * To unlock, the owner field is set to null, checking if anyone
     * needs waking up, if so doing so.  Recursive locks/unlocks
     * instead increment/decrement recursion field.
     */
    private transient volatile Thread owner;

    /*
     * For now, we use a special version of an AtomicReferenceFieldUpdater
     * (defined below) to simplify and slightly speed up CAS'ing owner field. 
     */
    private static final OwnerUpdater ownerUpdater = new OwnerUpdater();

    /**
     * Creates an instance of <tt>ReentrantLock</tt>.
     * This is equivalent to using <tt>ReentrantLock(false)</tt>.
     */
    public ReentrantLock() { 
        super();
    }

    /**
     * Creates an instance of <tt>ReentrantLock</tt> with the
     * given fairness policy.
     */
    public ReentrantLock(boolean fair) { 
        super(fair);
    }

    // Implementations of abstract methods

    boolean tryAcquire(int mode, Thread current) {
        return owner == null && ownerUpdater.acquire(this, current);
    }

    boolean tryRelease(int mode) {
        owner = null;
        return true;
    }

    protected Thread getOwner() {
        return owner;
    }

    boolean tryInitialAcquire(int mode, Thread current) {
        Thread o = owner;
        if (o == null && !fair && ownerUpdater.acquire(this, current))
            return true;
        if (o == current) { 
            ++recursions;
            return true;
        }
        return false;
    }

    // Public Lock methods

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
        if (Thread.currentThread() != owner)
            throw new IllegalMonitorStateException();
        else if (recursions > 0) 
            --recursions;
        else {
            owner = null;
            Node h = head;
            if (h != null && h.status < 0)
                unparkSuccessor(h);
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
        if (head != tail || !ownerUpdater.acquire(this, current))
            doLock(current, UNINTERRUPTED, 0L);
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
     * <li>The lock is acquired by the current thread; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
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
        if (doLock(Thread.currentThread(), INTERRUPT, 0) == INTERRUPT)
            throw new InterruptedException();
    }

    /**
     * Acquires the lock only if it is not held by another thread at the time
     * of invocation.
     * <p>Acquires the lock if it is not held by another thread and
     * returns immediately with the value <tt>true</tt>, setting the
     * lock hold count to one. Even when this lock has been set to use a
     * fair ordering policy, a call to <tt>tryLock()</tt> <em>will</em>
     * immediately acquire the lock if it is available, whether or not
     * other threads are currently waiting for the lock. 
     * This &quot;barging&quot; behavior can be useful in certain 
     * circumstances, even though it breaks fairness. If you want to honor
     * the fairness setting for this lock, then use 
     * {@link #tryLock(long, TimeUnit) tryLock(0, TimeUnit.SECONDS) }
     * which is almost equivalent (it also detects interruption).
     * <p> If the current thread
     * already holds this lock then the hold count is incremented by one and
     * the method returns <tt>true</tt>.
     * <p>If the lock is held by another thread then this method will return 
     * immediately with the value <tt>false</tt>.  
     *
     * @return <tt>true</tt> if the lock was free and was acquired by the
     * current thread, or the lock was already held by the current thread; and
     * <tt>false</tt> otherwise.
     */
    public boolean tryLock() {
        Thread current = Thread.currentThread();
        if (ownerUpdater.acquire(this, current))
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
     * to one. If this lock has been set to use a fair ordering policy then
     * an available lock <em>will not</em> be acquired if any other threads
     * are waiting for the lock. This is in contrast to the {@link #tryLock()}
     * method. If you want a timed <tt>tryLock</tt> that does permit barging on
     * a fair lock then combine the timed and un-timed forms together:
     * <pre>if (lock.tryLock() || lock.tryLock(timeout, unit) ) { ... }
     * </pre>
     * <p>If the current thread
     * already holds this lock then the hold count is incremented by one and
     * the method returns <tt>true</tt>.
     * <p>If the lock is held by another thread then the
     * current thread becomes disabled for thread scheduling 
     * purposes and lies dormant until one of three things happens:
     * <ul>
     * <li>The lock is acquired by the current thread; or
     * <li>Some other thread {@link Thread#interrupt interrupts} the current
     * thread; or
     * <li>The specified waiting time elapses
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
     * If the time is 
     * less than or equal to zero, the method will not wait at all.
     * <p>In this implementation, as this method is an explicit interruption 
     * point, preference is 
     * given to responding to the interrupt over normal or reentrant 
     * acquisition of the lock, and over reporting the elapse of the waiting
     * time.
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
     * @throws NullPointerException if unit is null
     *
     */
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null)
            throw new NullPointerException();
        int stat = doLock(Thread.currentThread(), 
                          INTERRUPT | TIMEOUT, 
                          unit.toNanos(timeout));
        if (stat == INTERRUPT)
            throw new InterruptedException();
        return (stat == UNINTERRUPTED);
    }

    /**
     * Returns a {@link Condition} instance for use with this 
     * {@link Lock} instance.
     * @return the Condition object
     */
    public ConditionObject newCondition() {
        return new ConditionObject(this);
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
     */
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
     * <p>It can also be used to ensure that a reentrant lock is used
     * in a non-reentrant manner, for example:
     * <pre>
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *
     *   public void m() { 
     *       assert !lock.isHeldByCurrentThread();
     *       lock.lock();
     *       try {
     *           // ... method body
     *       } finally {
     *           lock.unlock();
     *       }
     *   }
     * }
     * </pre>
     * @return <tt>true</tt> if current thread holds this lock and 
     * <tt>false</tt> otherwise.
     */
    public boolean isHeldByCurrentThread() {
        return owner == Thread.currentThread();
    }

    /**
     * Queries if this lock is held by any thread. This method is
     * designed for use in monitoring of the system state, 
     * not for synchronization control.
     * @return <tt>true</tt> if any thread holds this lock and 
     * <tt>false</tt> otherwise.
     */
    public boolean isLocked() {
        return owner != null;
    }


    // Helper methods for Conditions


    /**
     * This class is a minor performance hack, that will hopefully
     * someday disappear. It specializes AtomicReferenceFieldUpdater
     * for ReentrantLock owner field without requiring dynamic
     * instanceof checks in method acquire.
     */
    private static class OwnerUpdater extends AtomicReferenceFieldUpdater<ReentrantLock,Thread> {

        private static final Unsafe unsafe =  Unsafe.getUnsafe();
        private final long offset;

        OwnerUpdater() {
            try {
                Field field = ReentrantLock.class.getDeclaredField("owner");
                offset = unsafe.objectFieldOffset(field);
            } catch(Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        public boolean compareAndSet(ReentrantLock obj, Thread expect, Thread update) {
            return unsafe.compareAndSwapObject(obj, offset, expect, update);
        }

        public boolean weakCompareAndSet(ReentrantLock obj, Thread expect, Thread update) {
            return unsafe.compareAndSwapObject(obj, offset, expect, update);
        }

        public void set(ReentrantLock obj, Thread newValue) {
            unsafe.putObjectVolatile(obj, offset, newValue);
        }

        public Thread get(ReentrantLock obj) {
            return (Thread)unsafe.getObjectVolatile(obj, offset);
        }

        final boolean acquire(ReentrantLock obj, Thread current) {
            return unsafe.compareAndSwapObject(obj, offset, null, current);
        }
    }
        
}
