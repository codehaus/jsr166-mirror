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
 * <h3>Implementation Notes</h3>
 *
 * <p>A reentrant write lock intrinsically defines an owner and can
 * only be released by the thread that acquired it.  In contrast, in
 * this implementation, the read lock has no concept of ownership, and
 * there is no requirement that the thread releasing a read lock is
 * the same as the one that acquired it.  However, this property is
 * not guaranteed to hold in future implementations of this class.
 *
 * @since 1.5
 * @author Doug Lea
 *
 */
public class ReentrantReadWriteLock extends AbstractReentrantLock implements ReadWriteLock, java.io.Serializable  {

    private static final long serialVersionUID = -6992448646407690164L;

    /** 
     * Read/write hold status is kept in a separate AtomicInteger. The
     * low bit (1) is set when there is a writer. The rest of the word
     * holds the number of readers, so the value increments/decrements
     * by 2 per lock/unlock.
     */
    private final AtomicInteger count = new AtomicInteger(0);
    /** Current writing thread */
    private transient Thread owner;
    /** Inner class providing readlock */
    private final Lock readerLock = new ReadLock();
    /** Inner class providing writelock */
    private final Lock writerLock = new WriteLock();

    /**
     * Creates a new <tt>ReentrantReadWriteLock</tt> with
     * default ordering properties.
     */
    public ReentrantReadWriteLock() {
        super(false);
    }

    /**
     * Creates a new <tt>ReentrantReadWriteLock</tt> with
     * the given fairness policy.
     *
     * @param fair true if this lock should use a fair ordering policy
     */
    public ReentrantReadWriteLock(boolean fair) {
        super(fair);
    }

    public Lock writeLock() { 
        return writerLock; 
    }

    public Lock readLock() { 
        return readerLock; 
    }

    /**
     * Return true if count indicates lock is held in write mode
     * @param count a lock status count
     * @return true if count indicates lock is held in write mode
     */
    private static boolean isWriting(int count) { return (count & 1) != 0; }

    // Implementations of abstract methods

    boolean tryAcquire(int mode, Thread current) {
        final AtomicInteger count = this.count;
        for (;;) {
            int c = count.get();
            if (isReader(mode)) {
                if (isWriting(c) && current != owner)
                    return false;
                if (count.compareAndSet(c, c+2))
                    return true;
            }
            else {
                if (c != 0) {
                    if (current != owner)
                        return false;
                    ++recursions;
                    return true;
                }
                else if (count.compareAndSet(c, 1)) {
                    owner = current;
                    return true;
                }
            }
        }
    }

    boolean tryReentrantAcquire(int mode, Thread current) {
        if (!isReader(mode)) {
            int c = count.get();
            if (isWriting(c) && current == owner) {
                ++recursions;
                return true;
            }
        }
        return (!fair || head == tail) && tryAcquire(mode, current);
    }

    boolean tryRelease(int mode) {
        final AtomicInteger count = this.count;
        if (!isReader(mode)) {
            owner = null;
            for (;;) {
                int c = count.get();
                if (count.compareAndSet(c, c-1))
                    return true;
            }
        }
        else {
            for (;;) {
                int c = count.get();
                int nextc = c-2;
                if (count.compareAndSet(c, nextc)) 
                    return nextc <= 1;
            }
        }
    }

    /**
     * Returns the thread that currently owns the write lock, or
     * <tt>null</tt> if not owned. Note that the owner may be
     * momentarily <tt>null</tt> even if there are threads trying to
     * acquire the lock but have not yet done so.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive lock monitoring facilities.
     * @return the owner, or <tt>null</tt> if not owned.
     */
    protected Thread getOwner() {
        return (isWriting(count.get()))? owner : null;
    }

    void checkOwner(Thread thread) {
        if (!isWriting(count.get()) || owner != thread) 
            throw new IllegalMonitorStateException();
    }

    void checkOwnerForWait(Thread thread) {
        if (count.get() != 1 || owner != thread) 
            throw new IllegalMonitorStateException();
    }

    /**
     * The Reader lock
     */
    private class ReadLock implements Lock, java.io.Serializable  {
        private static final long serialVersionUID = -5992448646407690164L;

        public void lock() {
            // fast path
            if (!fair || head == tail) {
                final AtomicInteger count = ReentrantReadWriteLock.this.count;
                int c = count.get();
                if ((c & 1) == 0 && count.compareAndSet(c, c+2))
                    return;
            }
            doLock(READER | UNINTERRUPTED, 0);
        }

        public void lockInterruptibly() throws InterruptedException {
            if (doLock(READER | INTERRUPT, 0) == INTERRUPT)
                throw new InterruptedException();
        }

        public  boolean tryLock() {
            return tryAcquire(READER, Thread.currentThread());
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();
            int stat = doLock(READER | INTERRUPT | TIMEOUT, 
                              unit.toNanos(timeout));
            if (stat == INTERRUPT)
                throw new InterruptedException();
            return (stat == UNINTERRUPTED);
        }

        public  void unlock() {
            final AtomicInteger count = ReentrantReadWriteLock.this.count;
            for (;;) {
                int c = count.get();
                int nextc = c-2;
                if (nextc < 0)
                    throw new IllegalMonitorStateException();
                if (count.compareAndSet(c, nextc)) {
                    if (nextc <= 1) {
                        Node h = head;
                        if (h != null  && h.status < 0)
                            unparkSuccessor(h);
                    }
                    return;
                }
            }
        }

        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * The writer lock
     */
    private class WriteLock implements Lock, java.io.Serializable  {
        private static final long serialVersionUID = -4992448646407690164L;

        public void lock() {
            if (!fair || head == tail) { // fast path
                if (count.compareAndSet(0, 1)) {
                    owner = Thread.currentThread();
                    return;
                }
            }
            doLock(WRITER | UNINTERRUPTED, 0);
        }

        public void lockInterruptibly() throws InterruptedException {
            if (doLock(WRITER | INTERRUPT, 0) == INTERRUPT)
                throw new InterruptedException();
        }

        public boolean tryLock( ) {
            return tryAcquire(WRITER, Thread.currentThread());
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();
            int stat = doLock(WRITER | INTERRUPT | TIMEOUT, unit.toNanos(timeout));
            if (stat == INTERRUPT)
                throw new InterruptedException();
            return (stat == UNINTERRUPTED);
        }
        
        public void unlock() {
            final AtomicInteger count = ReentrantReadWriteLock.this.count;
            int c = count.get();
            if (!isWriting(c) || owner != Thread.currentThread())
                throw new IllegalMonitorStateException();
            else if (recursions > 0) 
                --recursions;
            else {
                owner = null;
                while (!count.compareAndSet(c, c-1))
                    c = count.get();
                Node h = head;
                if (h != null  && h.status < 0)
                    unparkSuccessor(h);
            }
        }

        public WriterConditionObject newCondition() { 
            return new WriterConditionObject(ReentrantReadWriteLock.this);
        }

    }
    
    // Instrumentation methods

    /**
     * Queries the number of read locks held for this lock. This method is
     * designed for use in monitoring  system state, 
     * not for synchronization control.
     * @return the number of read locks held.
     */
    public int getReadLockCount() {
        return count.get() >>> 1;
    }

    /**
     * Queries if the write lock is held by any thread. This method is
     * designed for use in monitoring  system state, 
     * not for synchronization control.
     * @return <tt>true</tt> if any thread holds write lock and 
     * <tt>false</tt> otherwise.
     */
    public boolean isWriteLocked() {
        return isWriting(count.get());
    }

    /**
     * Queries if the write lock is held by the current thread. 
     * @return <tt>true</tt> if current thread holds this lock and 
     * <tt>false</tt> otherwise.
     */
    public boolean isWriteLockedByCurrentThread() {
        return isWriting(count.get()) && owner == Thread.currentThread();
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
        return (isWriteLockedByCurrentThread())? recursions + 1 :  0;
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
        return getQueuedThreadsWithMode(WRITER);
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
        return getQueuedThreadsWithMode(READER);
    }

    // Serialization support

    /**
     * Throw away the object created with readObject, and replace it
     * @return the lock
     */
    private Object readResolve() throws java.io.ObjectStreamException {
        return new ReentrantReadWriteLock(fair);
    }
    /**
     * Reconstitute this lock instance from a stream (that is,
     * deserialize it).
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        // reset to unlocked state
        count.set(0);
    }

    /**
     * Condition implementation for use with reentrant write locks.
     * Instances of this class can be constructed only using method
     * {@link Lock#newCondition}.
     * 
     * <p>This class supports the same basic semantics and styles of
     * usage as the {@link Object} monitor methods.  Methods may be
     * invoked only when holding the lock associated with this
     * Condition. Failure to comply results in {@link
     * IllegalMonitorStateException}. Additionally, this exception is
     * thrown by waiting methods if the thread holding the write lock
     * also holds any read locks.
     *
     */
    public static class WriterConditionObject extends AbstractReentrantLock.AbstractConditionObject {
        protected WriterConditionObject(ReentrantReadWriteLock lock) { super(lock); }
    }

}

