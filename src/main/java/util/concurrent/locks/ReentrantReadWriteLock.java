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
 * </ul>
 *
 * <p><b>Sample usage</b>. Here is a code sketch showing how to exploit
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
 * <h3>Implementation Notes</h3>
 *
 * <p>In this implementation the write lock internally defines an
 * owner and can only be released by the thread that acquired it.  In
 * contrast, the read lock has no concept of ownership.  Consequently,
 * while not usually a good practice, it is possible to acquire a read
 * lock in one thread, and release it in another.
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
        super();
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
    boolean isWriting(int count) { return (count & 1) != 0; }

    // Implementations of abstract methods


    boolean tryAcquire(int mode, Thread current) {
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

    boolean tryInitialAcquire(int mode, Thread current) {
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

    protected Thread getOwner() {
        return (isWriting(count.get()))? owner : null;
    }


    /**
     * The Reader lock
     */
    private class ReadLock implements Lock, java.io.Serializable  {
        private static final long serialVersionUID = -5992448646407690164L;

        public void lock() {
            // fast path
            if (!fair || head == tail) {
                int c = count.get();
                if ((c & 1) == 0 && count.compareAndSet(c, c+2))
                    return;
            }
            doLock(Thread.currentThread(), READER | UNINTERRUPTED, 0);
        }

        public void lockInterruptibly() throws InterruptedException {
            if (doLock(Thread.currentThread(), READER | INTERRUPT, 0) == INTERRUPT)
                throw new InterruptedException();
        }

        public  boolean tryLock() {
            return tryAcquire(READER, Thread.currentThread());
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();
            int stat = doLock(Thread.currentThread(), READER | INTERRUPT | TIMEOUT, 
                              unit.toNanos(timeout));
            if (stat == INTERRUPT)
                throw new InterruptedException();
            return (stat == UNINTERRUPTED);
        }

        public  void unlock() {
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
            // fast path
            if (!fair || head == tail) {
                int c = count.get();
                if (c == 0 && count.compareAndSet(0, 1)) {
                    owner = Thread.currentThread();
                    return;
                }
            }
            doLock(Thread.currentThread(), WRITER | UNINTERRUPTED, 0);
        }

        public void lockInterruptibly() throws InterruptedException {
            if (doLock(Thread.currentThread(), WRITER | INTERRUPT, 0) == INTERRUPT)
                throw new InterruptedException();
        }

        public boolean tryLock( ) {
            return tryAcquire(WRITER, Thread.currentThread());
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();
            int stat = doLock(Thread.currentThread(), WRITER | INTERRUPT | TIMEOUT, 
                              unit.toNanos(timeout));
            if (stat == INTERRUPT)
                throw new InterruptedException();
            return (stat == UNINTERRUPTED);
        }
        
        public void unlock() {
            Thread current = Thread.currentThread();
            int c = count.get();
            if (!isWriting(c) || current != owner) 
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

        public AbstractReentrantLock.ConditionObject newCondition() { 
            return new AbstractReentrantLock.ConditionObject(ReentrantReadWriteLock.this);
        }

    }
    
    // Instrumentation methods

    /**
     * Queries the number of read locks held for this lock. This method is
     * designed for use in monitoring of the system state, 
     * not for synchronization control.
     * @return the number of read locks held.
     */
    public int getReadLocks() {
        return count.get() >>> 1;
    }

    /**
     * Queries if the write lock is held by any thread. This method is
     * designed for use in monitoring of the system state, 
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
        return (isWriting(count.get()) && owner == Thread.currentThread())?
            recursions + 1 :  0;
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
    protected Thread getWriter() {
        return (isWriting(count.get()))? owner : null;
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

}

