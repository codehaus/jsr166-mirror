/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent.locks;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

/**
 * An implementation of {@link ReadWriteLock} supporting similar
 * semantics to {@link ReentrantLock}.
 * <p>This class has the following properties:
 *
 * <ul>
 * <li><b>Acquisition order</b>
 *
 * <p> This class does not impose a reader or writer preference
 * ordering for lock access.  However, it does support an optional
 * <em>fairness</em> policy.  When constructed as fair, threads
 * contend for entry using an approximately arrival-order policy. When
 * the write lock is released either the longest-waiting single writer
 * will be assigned the write lock, or if there is a reader waiting
 * longer than any writer, the set of readers will be assigned the
 * read lock.  When constructed as non-fair, the order of entry to the
 * lock need not be in arrival order.  In either case, if readers are
 * active and a writer enters the lock then no subsequent readers will
 * be granted the read lock until after that writer has acquired and
 * released the write lock.
 * 
 * <li><b>Reentrancy</b>
 * <p>This lock allows both readers and writers to reacquire read or
 * write locks in the style of a {@link ReentrantLock}. Readers are not
 * allowed until all write locks held by the writing thread have been
 * released.  
 * <p>Additionally, a writer can acquire the read lock - but not vice-versa.
 * Among other applications, reentrancy can be useful when
 * write locks are held during calls or callbacks to methods that
 * perform reads under read locks. 
 * If a reader tries to acquire the write lock it will never succeed.
 * 
 * <li><b>Lock downgrading</b>
 * <p>Reentrancy also allows downgrading from the write lock to a read lock,
 * by acquiring the write lock, then the read lock and then releasing the
 * write lock. However, upgrading from a read lock to the write lock, is
 * <b>not</b> possible.
 *
 * <li><b>Interruption of lock acquisition</b>
 * <p>The read lock and write lock both support interruption during lock
 * acquisition.
 *
 * <li><b>{@link Condition} support</b>
 * <p>The write lock provides a {@link Condition} implementation that
 * behaves in the same way, with respect to the write lock, as the 
 * {@link Condition} implementation provided by
 * {@link ReentrantLock#newCondition} does for {@link ReentrantLock}.
 * This {@link Condition} can, of course, only be used with the write lock.
 * <p>The read lock does not support a {@link Condition} and
 * <tt>readLock().newCondition()</tt> throws 
 * <tt>UnsupportedOperationException</tt>.
 *
 * <li><b>Instrumentation</b>
 * <P> This class supports methods to determine whether locks
 * are held or contended. These methods are designed for monitoring
 * system state, not for synchronization control.
 * </ul>
 *
 * <p><b>Sample usages</b>. Here is a code sketch showing how to exploit
 * reentrancy to perform lock downgrading after updating a cache (exception
 * handling is elided for simplicity):
 * <pre>
 * class CachedData {
 *   Object data;
 *   volatile boolean cacheValid;
 *   ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *
 *   void processCachedData() {
 *     rwl.readLock().lock();
 *     if (!cacheValid) {
 *        // upgrade lock manually
 *        rwl.readLock().unlock();   // must unlock first to obtain writelock
 *        rwl.writeLock().lock();
 *        if (!cacheValid) { // recheck
 *          data = ...
 *          cacheValid = true;
 *        }
 *        // downgrade lock
 *        rwl.readLock().lock();  // reacquire read without giving up write lock
 *        rwl.writeLock().unlock(); // unlock write, still hold read
 *     }
 *
 *     use(data);
 *     rwl.readLock().unlock();
 *   }
 * }
 * </pre>
 *
 * ReentrantReadWriteLocks can be used to improve concurrency in some
 * uses of some kinds of Collections. This is typically worthwhile
 * only when the collections are expected to be large, accessed by
 * more reader threads than writer threads, and entail operations with
 * overhead that outweighs synchronization overhead. For example, here
 * is a class using a TreeMap that is expected to be large and 
 * concurrently accessed.
 *
 * <pre>
 * class RWDictionary {
 *    private final Map&lt;String, Data&gt;  m = new TreeMap&lt;String, Data&gt;();
 *    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
 *    private final Lock r = rwl.readLock();
 *    private final Lock w = rwl.writeLock();
 *
 *    public Data get(String key) {
 *        r.lock(); try { return m.get(key); } finally { r.unlock(); }
 *    }
 *    public String[] allKeys() {
 *        r.lock(); try { return m.keySet().toArray(); } finally { r.unlock(); }
 *    }
 *    public Data put(String key, Data value) {
 *        w.lock(); try { return m.put(key, value); } finally { w.unlock(); }
 *    }
 *    public void clear() {
 *        w.lock(); try { m.clear(); } finally { w.unlock(); }
 *    }
 * }
 * </pre>
 * 
 *
 * <p> Serialization of this class behaves in the same way as built-in
 * locks: a deserialized lock is in the unlocked state, regardless of
 * its state when serialized.
 *
 * <h3>Implementation Notes</h3>
 *
 * <p>A reentrant write lock intrinsically defines an owner and can
 * only be released by the thread that acquired it.  In contrast, in
 * this implementation, the read lock has no concept of ownership, and
 * there is no requirement that the thread releasing a read lock is
 * the same as the one that acquired it.  However, this property is
 * not guaranteed to hold in future implementations of this class.
 *
 * <p> This lock supports a maximum of 655536 recursive write locks
 * and 65536 read locks. Attempts to exceed this limit result in
 * {@link Error} throws from locking methods.
 *
 * @since 1.5
 * @author Doug Lea
 *
 */
public class ReentrantReadWriteLock implements ReadWriteLock, java.io.Serializable  {
    private static final long serialVersionUID = -6992448646407690164L;
    /** Inner class providing readlock */
    private final ReentrantReadWriteLock.ReadLock readerLock = new ReadLock();
    /** Inner class providing writelock */
    private final ReentrantReadWriteLock.WriteLock writerLock = new WriteLock();
    private final Sync sync;

    /**
     * Creates a new <tt>ReentrantReadWriteLock</tt> with
     * default ordering properties.
     */
    public ReentrantReadWriteLock() {
        sync = new Sync();
    }

    /**
     * Creates a new <tt>ReentrantReadWriteLock</tt> with
     * the given fairness policy.
     *
     * @param fair true if this lock should use a fair ordering policy
     */
    public ReentrantReadWriteLock(boolean fair) {
        sync = new Sync(fair);
    }

    public ReentrantReadWriteLock.WriteLock writeLock() { 
        return writerLock; 
    }

    public ReentrantReadWriteLock.ReadLock readLock() { 
        return readerLock; 
    }

    /**
     * The lock returned by method {@link ReentrantReadWriteLock#readLock}.
     */
    public class ReadLock implements Lock, java.io.Serializable  {
        private static final long serialVersionUID = -5992448646407690164L;

        /** Constructor for use by subclasses */
        protected ReadLock() {}

        /**
         * Acquires the shared lock. 
         *
         * <p>Acquires the lock if it is not held exclusively by
         * another thread and returns immediately.
         *
         * <p>If the lock is held exclusively by another thread then
         * the current thread becomes disabled for thread scheduling
         * purposes and lies dormant until the lock has been acquired.
         */
        public void lock() { 
            sync.lockShared();
        }

        /**
         * Acquires the shared lock unless the current thread is 
         * {@link Thread#interrupt interrupted}.
         *
         * <p>Acquires the shared lock if it is not held exclusively
         * by another thread and returns immediately.
         *
         * <p>If the lock is held by another thread then the
         * current thread becomes disabled for thread scheduling 
         * purposes and lies dormant until one of two things happens:
         *
         * <ul>
         *
         * <li>The lock is acquired by the current thread; or
         *
         * <li>Some other thread {@link Thread#interrupt interrupts}
         * the current thread.
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
         *
         * then {@link InterruptedException} is thrown and the current
         * thread's interrupted status is cleared.
         *
         * <p>In this implementation, as this method is an explicit
         * interruption point, preference is given to responding to
         * the interrupt over normal or reentrant acquisition of the
         * lock.
         *
         * @throws InterruptedException if the current thread is interrupted
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         * Acquires the shared lock only if it is not held exclusively by
         * another thread at the time of invocation.
         *
         * <p>Acquires the lock if it is not held exclusively by
         * another thread and returns immediately with the value
         * <tt>true</tt>. Even when this lock has been set to use a
         * fair ordering policy, a call to <tt>tryLock()</tt>
         * <em>will</em> immediately acquire the lock if it is
         * available, whether or not other threads are currently
         * waiting for the lock.  This &quot;barging&quot; behavior
         * can be useful in certain circumstances, even though it
         * breaks fairness. If you want to honor the fairness setting
         * for this lock, then use {@link #tryLock(long, TimeUnit)
         * tryLock(0, TimeUnit.SECONDS) } which is almost equivalent
         * (it also detects interruption).
         *
         * <p>If the lock is held exclusively by another thread then
         * this method will return immediately with the value
         * <tt>false</tt>.
         *
         * @return <tt>true</tt> if the lock was acquired.
         */
        public  boolean tryLock() {
            return sync.fastAcquireShared(false) ||
                sync.acquireSharedState(true, 1, Thread.currentThread()) >= 0;
        }

        /**
         * Acquires the shared lock if it is not held exclusively by
         * another thread within the given waiting time and the
         * current thread has not been {@link Thread#interrupt
         * interrupted}.
         *
         * <p>Acquires the lock if it is not held exclusively by
         * another thread and returns immediately with the value
         * <tt>true</tt>. If this lock has been set to use a fair
         * ordering policy then an available lock <em>will not</em> be
         * acquired if any other threads are waiting for the
         * lock. This is in contrast to the {@link #tryLock()}
         * method. If you want a timed <tt>tryLock</tt> that does
         * permit barging on a fair lock then combine the timed and
         * un-timed forms together:
         *
         * <pre>if (lock.tryLock() || lock.tryLock(timeout, unit) ) { ... }
         * </pre>
         *
         * <p>If the lock is held exclusively by another thread then the
         * current thread becomes disabled for thread scheduling 
         * purposes and lies dormant until one of three things happens:
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
         * <p>If the lock is acquired then the value <tt>true</tt> is
         * returned.
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
         * </ul> then {@link InterruptedException} is thrown and the
         * current thread's interrupted status is cleared.
         *
         * <p>If the specified waiting time elapses then the value
         * <tt>false</tt> is returned.  If the time is less than or
         * equal to zero, the method will not wait at all.
         *
         * <p>In this implementation, as this method is an explicit
         * interruption point, preference is given to responding to
         * the interrupt over normal or reentrant acquisition of the
         * lock, and over reporting the elapse of the waiting time.
         *
         * @param timeout the time to wait for the lock
         * @param unit the time unit of the timeout argument
         *
         * @return <tt>true</tt> if the lock was acquired.
         *
         * @throws InterruptedException if the current thread is interrupted
         * @throws NullPointerException if unit is null
         *
         */
        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return sync.acquireSharedTimed(1, unit.toNanos(timeout));
        }

        /**
         * Attempts to release this lock.  
         *
         * <p> If the number of readers is now zero then the lock
         * is made available for other lock attempts.
         */
        public  void unlock() {
            sync.releaseShared(1);
        }

        /**
         * Throws UnsupportedOperationException because ReadLocks
         * do not support conditions.
         * @throws UnsupportedOperationException always
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * The lock returned by method {@link ReentrantReadWriteLock#writeLock}.
     */
    public class WriteLock implements Lock, java.io.Serializable  {
        private static final long serialVersionUID = -4992448646407690164L;

        /** Constructor for use by subclasses */
        protected WriteLock() {}

        /**
         * Acquire the lock. 
         *
         * <p>Acquires the lock if it is not held by another thread
         * and returns immediately, setting the lock hold count to
         * one.
         *
         * <p>If the current thread already holds the lock then the
         * hold count is incremented by one and the method returns
         * immediately.
         *
         * <p>If the lock is held by another thread then the current
         * thread becomes disabled for thread scheduling purposes and
         * lies dormant until the lock has been acquired, at which
         * time the lock hold count is set to one.
         */
        public void lock() {
            sync.lockExclusive();
        }

        /**
         * Acquires the lock unless the current thread is {@link
         * Thread#interrupt interrupted}.
         *
         * <p>Acquires the lock if it is not held by another thread
         * and returns immediately, setting the lock hold count to
         * one.
         *
         * <p>If the current thread already holds this lock then the
         * hold count is incremented by one and the method returns
         * immediately.
         *
         * <p>If the lock is held by another thread then the current
         * thread becomes disabled for thread scheduling purposes and
         * lies dormant until one of two things happens:
         *
         * <ul>
         *
         * <li>The lock is acquired by the current thread; or
         *
         * <li>Some other thread {@link Thread#interrupt interrupts}
         * the current thread.
         *
         * </ul>
         *
         * <p>If the lock is acquired by the current thread then the
         * lock hold count is set to one.
         *
         * <p>If the current thread:
         *
         * <ul>
         *
         * <li>has its interrupted status set on entry to this method;
         * or
         *
         * <li>is {@link Thread#interrupt interrupted} while acquiring
         * the lock,
         *
         * </ul>
         *
         * then {@link InterruptedException} is thrown and the current
         * thread's interrupted status is cleared.
         *
         * <p>In this implementation, as this method is an explicit
         * interruption point, preference is given to responding to
         * the interrupt over normal or reentrant acquisition of the
         * lock.
         *
         * @throws InterruptedException if the current thread is interrupted
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireExclusiveInterruptibly(1);
        }

        /**
         * Acquires the lock only if it is not held by another thread
         * at the time of invocation.
         *
         * <p>Acquires the lock if it is not held by another thread
         * and returns immediately with the value <tt>true</tt>,
         * setting the lock hold count to one. Even when this lock has
         * been set to use a fair ordering policy, a call to
         * <tt>tryLock()</tt> <em>will</em> immediately acquire the
         * lock if it is available, whether or not other threads are
         * currently waiting for the lock.  This &quot;barging&quot;
         * behavior can be useful in certain circumstances, even
         * though it breaks fairness. If you want to honor the
         * fairness setting for this lock, then use {@link
         * #tryLock(long, TimeUnit) tryLock(0, TimeUnit.SECONDS) }
         * which is almost equivalent (it also detects interruption).
         *
         * <p> If the current thread already holds this lock then the
         * hold count is incremented by one and the method returns
         * <tt>true</tt>.
         *
         * <p>If the lock is held by another thread then this method
         * will return immediately with the value <tt>false</tt>.
         *
         * @return <tt>true</tt> if the lock was free and was acquired by the
         * current thread, or the lock was already held by the current thread; and
         * <tt>false</tt> otherwise.
         */
        public boolean tryLock( ) {
            return sync.fastAcquireExclusive(false) ||
                sync.acquireExclusiveState(true, 1, Thread.currentThread()) >= 0;
        }

        /**
         * Acquires the lock if it is not held by another thread
         * within the given waiting time and the current thread has
         * not been {@link Thread#interrupt interrupted}.
         *
         * <p>Acquires the lock if it is not held by another thread
         * and returns immediately with the value <tt>true</tt>,
         * setting the lock hold count to one. If this lock has been
         * set to use a fair ordering policy then an available lock
         * <em>will not</em> be acquired if any other threads are
         * waiting for the lock. This is in contrast to the {@link
         * #tryLock()} method. If you want a timed <tt>tryLock</tt>
         * that does permit barging on a fair lock then combine the
         * timed and un-timed forms together:
         *
         * <pre>if (lock.tryLock() || lock.tryLock(timeout, unit) ) { ... }
         * </pre>
         *
         * <p>If the current thread already holds this lock then the
         * hold count is incremented by one and the method returns
         * <tt>true</tt>.
         *
         * <p>If the lock is held by another thread then the current
         * thread becomes disabled for thread scheduling purposes and
         * lies dormant until one of three things happens:
         *
         * <ul>
         *
         * <li>The lock is acquired by the current thread; or
         *
         * <li>Some other thread {@link Thread#interrupt interrupts}
         * the current thread; or
         *
         * <li>The specified waiting time elapses
         *
         * </ul>
         *
         * <p>If the lock is acquired then the value <tt>true</tt> is
         * returned and the lock hold count is set to one.
         *
         * <p>If the current thread:
         *
         * <ul>
         *
         * <li>has its interrupted status set on entry to this method;
         * or
         *
         * <li>is {@link Thread#interrupt interrupted} while acquiring
         * the lock,
         *
         * </ul> 
         *
         * then {@link InterruptedException} is thrown and the current
         * thread's interrupted status is cleared.
         *
         * <p>If the specified waiting time elapses then the value
         * <tt>false</tt> is returned.  If the time is less than or
         * equal to zero, the method will not wait at all.
         *
         * <p>In this implementation, as this method is an explicit
         * interruption point, preference is given to responding to
         * the interrupt over normal or reentrant acquisition of the
         * lock, and over reporting the elapse of the waiting time.
         *
         * @param timeout the time to wait for the lock
         * @param unit the time unit of the timeout argument
         *
         * @return <tt>true</tt> if the lock was free and was acquired
         * by the current thread, or the lock was already held by the
         * current thread; and <tt>false</tt> if the waiting time
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
         * Attempts to release this lock.  
         *
         * <p>If the current thread is the holder of this lock then
         * the hold count is decremented. If the hold count is now
         * zero then the lock is released.  If the current thread is
         * not the holder of this lock then {@link
         * IllegalMonitorStateException} is thrown.
         * @throws IllegalMonitorStateException if the current thread does not
         * hold this lock.
         */
        public void unlock() {
            sync.releaseExclusive(1);
        }

        /**
         * Returns a {@link Condition} instance for use with this
         * {@link Lock} instance. This class supports
         * the same basic semantics and styles of usage as the {@link
         * Object} monitor methods.  Methods may be invoked only when
         * exclusively holding the write lock associated with this
         * Condition, and not holding any read locks. Failure to comply
         * results in {@link IllegalMonitorStateException}.
         * @return the Condition object
         */
        public AbstractQueuedSynchronizer.ConditionObject newCondition() { 
            return sync.newCondition();
        }
    }


    // Instrumentation and status

    /**
     * Return true if this lock has fairness set true.
     * @return true if this lock has fairness set true.
     */
    public final boolean isFair() {
        return sync.fair;
    }

    /**
     * Returns the thread that currently owns the exclusive lock, or
     * <tt>null</tt> if not owned. Note that the owner may be
     * momentarily <tt>null</tt> even if there are threads trying to
     * acquire the lock but have not yet done so.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the owner, or <tt>null</tt> if not owned.
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }
    
    // Instrumentation methods

    /**
     * Queries the number of read locks held for this lock. This method is
     * designed for use in monitoring  system state, 
     * not for synchronization control.
     * @return the number of read locks held.
     */
    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    /**
     * Queries if the write lock is held by any thread. This method is
     * designed for use in monitoring  system state, 
     * not for synchronization control.
     * @return <tt>true</tt> if any thread holds write lock and 
     * <tt>false</tt> otherwise.
     */
    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    /**
     * Queries if the write lock is held by the current thread. 
     * @return <tt>true</tt> if current thread holds this lock and 
     * <tt>false</tt> otherwise.
     */
    public boolean isWriteLockedByCurrentThread() {
        return sync.isWriteLockedByCurrentThread();
    }

    /**
     * Queries the number of reentrant write holds on this lock by the
     * current thread.  <p>A writer thread has a hold on a lock for
     * each lock action that is not matched by an unlock action.
     *
     * @return the number of holds on this lock by the current thread,
     * or zero if this lock is not held by the current thread.
     */
    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the write lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getQueuedWriterThreads();
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire the read lock.  Because the actual set of threads may
     * change dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the collection of threads
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        return getQueuedReaderThreads();
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

    private final static class Sync extends AbstractQueuedSynchronizer {
        /** true if barging disabled */
        final boolean fair;
        /** Current (exclusive) owner thread */
        private transient Thread owner;

        Sync() { this.fair = false; }
        Sync(boolean fair) { this.fair = fair; }

        /* 
         * Shared vs write count extraction constants and functions.  Lock
         * state is logically divided into two shorts: The lower one
         * representing the exclusive (writer) lock hold count, and the
         * upper the shared (reader) hold count.
         */

        static final int SHARED_SHIFT   = 16;
        static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /**
         * Return true if count indicates lock is held in exclusive mode
         * @param c a lock status count
         * @return true if count indicates lock is held in exclusive mode
         */
        boolean isExclusive(int c) { 
            return (c & EXCLUSIVE_MASK) != 0; 
        }

        /**
         * Return the number of shared holds represented in count
         */
        int sharedCount(int c)  { 
            return c >>> SHARED_SHIFT; 
        }

        /**
         * Return the number of exclusive holds represented in count
         */
        int exclusiveCount(int c) { 
            return c & EXCLUSIVE_MASK; 
        }

        /**
         * Fast path for write locks
         */
        boolean fastAcquireExclusive(boolean enforceFairness) {
            final AtomicInteger count = getState();
            if ((!enforceFairness || !hasWaiters()) && 
                count.compareAndSet(0, 1)) {
                owner = Thread.currentThread();
                return true;
            }
            return false;
        }

        /**
         * Fast path for read locks
         */
        boolean fastAcquireShared(boolean enforceFairness) {
            if (!enforceFairness || !hasWaiters()) {
                final AtomicInteger count = getState();
                int c = count.get();
                if (!isExclusive(c) && 
                    count.compareAndSet(c, c + SHARED_UNIT))
                    return true;
            }
            return false;
        }



        public final int acquireExclusiveState(boolean isQueued, int acquires, 
                                               Thread current) {
            final AtomicInteger count = getState();
            boolean nobarge = !isQueued && fair;
            for (;;) {
                int c = count.get();
                int w = exclusiveCount(c);
                int r = sharedCount(c);
                if (r != 0)
                    return -1;
                if (w != 0) {
                    if (current != owner)
                        return -1;
                    if (w + acquires >= SHARED_UNIT)
                        throw new Error("Maximum lock count exceeded");
                    if (count.compareAndSet(c, c + acquires)) 
                        return 0;
                }
                else {
                    if (nobarge && hasWaiters())
                        return -1;
                    if (count.compareAndSet(c, c + acquires)) {
                        owner = current;
                        return 0;
                    }
                }
                // Recheck count if lost any of the above CAS's
            }
        }

        public final boolean releaseExclusiveState(int releases) {
            final AtomicInteger count = getState();
            Thread current = Thread.currentThread();
            int c = count.get();
            int w = exclusiveCount(c) - releases;
            if (w < 0 || owner != current)
                throw new IllegalMonitorStateException();
            if (w == 0) 
                owner = null;
            count.set(c - releases);
            return w == 0;
        }

        public final int acquireSharedState(boolean isQueued, int acquires, 
                                            Thread current) {
            final AtomicInteger count = getState();
            boolean nobarge = !isQueued && fair;
            for (;;) {
                int c = count.get();
                int w = exclusiveCount(c);
                int r = sharedCount(c);
                if (w != 0 && current != owner)
                    return -1;
                if (nobarge && !hasWaiters())
                    return -1;
                int nextc = c + acquires * SHARED_UNIT;
                if (nextc < c)
                    throw new Error("Maximum lock count exceeded");
                if (count.compareAndSet(c, nextc)) 
                    return 1;
            }
            // Recheck count if lost any of the above CAS's
        }

        public final boolean releaseSharedState(int releases) {
            final AtomicInteger count = getState();
            for (;;) {
                int c = count.get();
                int nextc = c - releases * SHARED_UNIT;
                if (nextc < 0)
                    throw new IllegalMonitorStateException();
                if (count.compareAndSet(c, nextc)) 
                    return nextc == 0;
            }
        }
    
        public final void checkConditionAccess(Thread thread, boolean waiting) {
            int c = getState().get();
            boolean ok = exclusiveCount(c) != 0;
            if (ok && waiting && sharedCount(c) != 0)
                ok = false;
            if (!ok || owner != thread) 
                throw new IllegalMonitorStateException();
        }

        void lockShared() { 
            if (!fastAcquireShared(fair))
                acquireSharedUninterruptibly(1);
        }

        void lockExclusive() {
            if (!fastAcquireExclusive(fair))
                acquireExclusiveUninterruptibly(1);
        }

        ConditionObject newCondition() { return new ConditionObject(); }

        Thread getOwner() {
            return (exclusiveCount(getState().get()) != 0)? owner : null;
        }
        
        int getReadLockCount() {
            return sharedCount(getState().get());
        }
        
        boolean isWriteLocked() {
            return exclusiveCount(getState().get()) != 0;
        }

        boolean isWriteLockedByCurrentThread() {
            return getOwner() == Thread.currentThread();
        }

        int getWriteHoldCount() {
            int c = exclusiveCount(getState().get());
            return (owner == Thread.currentThread())? c : 0;
        }

        Collection<Thread> getQueuedWriterThreads() {
            return getQueuedThreads(false);
        }

        Collection<Thread> getQueuedReaderThreads() {
            return getQueuedThreads(true);
        }


        /**
         * Reconstitute this lock instance from a stream
         * @param s the stream
         */
        private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            getState().set(0); // reset to unlocked state
        }
        
    }
}
