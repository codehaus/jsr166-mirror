/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.io.*;
import java.util.*;

public class ReentrantReadWriteLockTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(ReentrantReadWriteLockTest.class);
    }

    /**
     * A runnable calling lockInterruptibly
     */
    class InterruptibleLockRunnable extends CheckedRunnable {
        final ReentrantReadWriteLock lock;
        InterruptibleLockRunnable(ReentrantReadWriteLock l) { lock = l; }
        public void realRun() throws InterruptedException {
            lock.writeLock().lockInterruptibly();
        }
    }

    /**
     * A runnable calling lockInterruptibly that expects to be
     * interrupted
     */
    class InterruptedLockRunnable extends CheckedInterruptedRunnable {
        final ReentrantReadWriteLock lock;
        InterruptedLockRunnable(ReentrantReadWriteLock l) { lock = l; }
        public void realRun() throws InterruptedException {
            lock.writeLock().lockInterruptibly();
        }
    }

    /**
     * Subclass to expose protected methods
     */
    static class PublicReentrantReadWriteLock extends ReentrantReadWriteLock {
        PublicReentrantReadWriteLock() { super(); }
        PublicReentrantReadWriteLock(boolean fair) { super(fair); }
        public Thread getOwner() {
            return super.getOwner();
        }
        public Collection<Thread> getQueuedThreads() {
            return super.getQueuedThreads();
        }
        public Collection<Thread> getWaitingThreads(Condition c) {
            return super.getWaitingThreads(c);
        }
    }

    /**
     * Releases write lock, checking that it had a hold count of 1.
     */
    void releaseWriteLock(PublicReentrantReadWriteLock lock) {
        ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        assertWriteLockedBy(lock, Thread.currentThread());
        assertEquals(1, lock.getWriteHoldCount());
        writeLock.unlock();
        assertNotWriteLocked(lock);
    }

    /**
     * Spin-waits until lock.hasQueuedThread(t) becomes true.
     */
    void waitForQueuedThread(PublicReentrantReadWriteLock lock, Thread t) {
        long startTime = System.nanoTime();
        while (!lock.hasQueuedThread(t)) {
            if (millisElapsedSince(startTime) > LONG_DELAY_MS)
                throw new AssertionError("timed out");
            Thread.yield();
        }
        assertTrue(t.isAlive());
        assertTrue(lock.getOwner() != t);
    }

    /**
     * Checks that lock is not write-locked.
     */
    void assertNotWriteLocked(PublicReentrantReadWriteLock lock) {
        assertFalse(lock.isWriteLocked());
        assertFalse(lock.isWriteLockedByCurrentThread());
        assertFalse(lock.writeLock().isHeldByCurrentThread());
        assertNull(lock.getOwner());
        assertEquals(0, lock.getWriteHoldCount());
    }

    /**
     * Checks that lock is write-locked by the given thread.
     */
    void assertWriteLockedBy(PublicReentrantReadWriteLock lock, Thread t) {
        assertTrue(lock.isWriteLocked());
        assertSame(t, lock.getOwner());
        assertEquals(t == Thread.currentThread(),
                     lock.isWriteLockedByCurrentThread());
        assertEquals(t == Thread.currentThread(),
                     lock.writeLock().isHeldByCurrentThread());
        assertEquals(t == Thread.currentThread(),
                     lock.getWriteHoldCount() > 0);
        assertEquals(0, lock.getReadLockCount());
    }

    /**
     * Checks that condition c has no waiters.
     */
    void assertHasNoWaiters(PublicReentrantReadWriteLock lock, Condition c) {
        assertHasWaiters(lock, c, new Thread[] {});
    }

    /**
     * Checks that condition c has exactly the given waiter threads.
     */
    void assertHasWaiters(PublicReentrantReadWriteLock lock, Condition c,
                          Thread... threads) {
        lock.writeLock().lock();
        assertEquals(threads.length > 0, lock.hasWaiters(c));
        assertEquals(threads.length, lock.getWaitQueueLength(c));
        assertEquals(threads.length == 0, lock.getWaitingThreads(c).isEmpty());
        assertEquals(threads.length, lock.getWaitingThreads(c).size());
        assertEquals(new HashSet<Thread>(lock.getWaitingThreads(c)),
                     new HashSet<Thread>(Arrays.asList(threads)));
        lock.writeLock().unlock();
    }

    /**
     * Constructor sets given fairness, and is in unlocked state
     */
    public void testConstructor() {
        PublicReentrantReadWriteLock lock;

        lock = new PublicReentrantReadWriteLock();
        assertFalse(lock.isFair());
        assertNotWriteLocked(lock);
        assertEquals(0, lock.getReadLockCount());

        lock = new PublicReentrantReadWriteLock(true);
        assertTrue(lock.isFair());
        assertNotWriteLocked(lock);
        assertEquals(0, lock.getReadLockCount());

        lock = new PublicReentrantReadWriteLock(false);
        assertFalse(lock.isFair());
        assertNotWriteLocked(lock);
        assertEquals(0, lock.getReadLockCount());
    }

    /**
     * write-locking and read-locking an unlocked lock succeed
     */
    public void testLock() {
        PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        assertNotWriteLocked(lock);
        lock.writeLock().lock();
        assertWriteLockedBy(lock, Thread.currentThread());
        lock.writeLock().unlock();
        assertNotWriteLocked(lock);
        assertEquals(0, lock.getReadLockCount());
        lock.readLock().lock();
        assertNotWriteLocked(lock);
        assertEquals(1, lock.getReadLockCount());
        lock.readLock().unlock();
        assertNotWriteLocked(lock);
        assertEquals(0, lock.getReadLockCount());
    }

    /**
     * locking an unlocked fair lock succeeds
     */
    public void testFairLock() {
        PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock(true);
        assertNotWriteLocked(lock);
        lock.writeLock().lock();
        assertWriteLockedBy(lock, Thread.currentThread());
        lock.writeLock().unlock();
        assertNotWriteLocked(lock);
        assertEquals(0, lock.getReadLockCount());
        lock.readLock().lock();
        assertNotWriteLocked(lock);
        assertEquals(1, lock.getReadLockCount());
        lock.readLock().unlock();
        assertNotWriteLocked(lock);
        assertEquals(0, lock.getReadLockCount());
    }

    /**
     * getWriteHoldCount returns number of recursive holds
     */
    public void testGetWriteHoldCount() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        for (int i = 1; i <= SIZE; i++) {
            lock.writeLock().lock();
            assertEquals(i,lock.getWriteHoldCount());
        }
        for (int i = SIZE; i > 0; i--) {
            lock.writeLock().unlock();
            assertEquals(i-1,lock.getWriteHoldCount());
        }
    }

    /**
     * WriteLock.getHoldCount returns number of recursive holds
     */
    public void testGetHoldCount() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        for (int i = 1; i <= SIZE; i++) {
            lock.writeLock().lock();
            assertEquals(i,lock.writeLock().getHoldCount());
        }
        for (int i = SIZE; i > 0; i--) {
            lock.writeLock().unlock();
            assertEquals(i-1,lock.writeLock().getHoldCount());
        }
    }

    /**
     * getReadHoldCount returns number of recursive holds
     */
    public void testGetReadHoldCount() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        for (int i = 1; i <= SIZE; i++) {
            lock.readLock().lock();
            assertEquals(i,lock.getReadHoldCount());
        }
        for (int i = SIZE; i > 0; i--) {
            lock.readLock().unlock();
            assertEquals(i-1,lock.getReadHoldCount());
        }
    }

    /**
     * write-unlocking an unlocked lock throws IllegalMonitorStateException
     */
    public void testWriteUnlock_IllegalMonitorStateException() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        try {
            lock.writeLock().unlock();
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * read-unlocking an unlocked lock throws IllegalMonitorStateException
     */
    public void testReadUnlock_IllegalMonitorStateException() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        try {
            lock.readLock().unlock();
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * write-lockInterruptibly is interruptible
     */
    public void testWriteLockInterruptibly_Interrupted() throws Exception {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lockInterruptibly();
            }});

        waitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    /**
     * timed write-tryLock is interruptible
     */
    public void testWriteTryLock_Interrupted() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().tryLock(2 * LONG_DELAY_MS, MILLISECONDS);
            }});

        waitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    /**
     * read-lockInterruptibly is interruptible
     */
    public void testReadLockInterruptibly_Interrupted() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.readLock().lockInterruptibly();
            }});

        waitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    /**
     * timed read-tryLock is interruptible
     */
    public void testReadTryLock_Interrupted() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.readLock().tryLock(2 * LONG_DELAY_MS, MILLISECONDS);
            }});

        waitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    /**
     * write-tryLock fails if locked
     */
    public void testWriteTryLockWhenLocked() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertFalse(lock.writeLock().tryLock());
            }});

        awaitTermination(t);
        releaseWriteLock(lock);
    }

    /**
     * read-tryLock fails if locked
     */
    public void testReadTryLockWhenLocked() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertFalse(lock.readLock().tryLock());
            }});

        awaitTermination(t);
        releaseWriteLock(lock);
    }

    /**
     * Multiple threads can hold a read lock when not write-locked
     */
    public void testMultipleReadLocks() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        lock.readLock().lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertTrue(lock.readLock().tryLock());
                lock.readLock().unlock();
            }});

        awaitTermination(t);
        lock.readLock().unlock();
    }

    /**
     * A writelock succeeds only after a reading thread unlocks
     */
    public void testWriteAfterReadLock() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.readLock().lock();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertEquals(1, lock.getReadLockCount());
                lock.writeLock().lock();
                assertEquals(0, lock.getReadLockCount());
                lock.writeLock().unlock();
            }});
        waitForQueuedThread(lock, t);
        assertNotWriteLocked(lock);
        assertEquals(1, lock.getReadLockCount());
        lock.readLock().unlock();
        assertEquals(0, lock.getReadLockCount());
        awaitTermination(t);
        assertNotWriteLocked(lock);
    }

    /**
     * A writelock succeeds only after reading threads unlock
     */
    public void testWriteAfterMultipleReadLocks() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.readLock().lock();
        lock.readLock().lock();
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.readLock().lock();
                assertEquals(3, lock.getReadLockCount());
                lock.readLock().unlock();
            }});
        awaitTermination(t1);

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertEquals(2, lock.getReadLockCount());
                lock.writeLock().lock();
                assertEquals(0, lock.getReadLockCount());
                lock.writeLock().unlock();
            }});
        waitForQueuedThread(lock, t2);
        assertNotWriteLocked(lock);
        assertEquals(2, lock.getReadLockCount());
        lock.readLock().unlock();
        lock.readLock().unlock();
        assertEquals(0, lock.getReadLockCount());
        awaitTermination(t2);
        assertNotWriteLocked(lock);
    }

    /**
     * A thread that tries to acquire a fair read lock (non-reentrantly)
     * will block if there is a waiting writer thread.
     */
    public void testReaderWriterReaderFairFifo() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock(true);
        final AtomicBoolean t1GotLock = new AtomicBoolean(false);

        lock.readLock().lock();
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertEquals(1, lock.getReadLockCount());
                lock.writeLock().lock();
                assertEquals(0, lock.getReadLockCount());
                t1GotLock.set(true);
                lock.writeLock().unlock();
            }});
        waitForQueuedThread(lock, t1);

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertEquals(1, lock.getReadLockCount());
                lock.readLock().lock();
                assertEquals(1, lock.getReadLockCount());
                assertTrue(t1GotLock.get());
                lock.readLock().unlock();
            }});
        waitForQueuedThread(lock, t2);
        assertTrue(t1.isAlive());
        assertNotWriteLocked(lock);
        assertEquals(1, lock.getReadLockCount());
        lock.readLock().unlock();
        awaitTermination(t1);
        awaitTermination(t2);
        assertNotWriteLocked(lock);
    }

    /**
     * Readlocks succeed only after a writing thread unlocks
     */
    public void testReadAfterWriteLock() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.readLock().lock();
                lock.readLock().unlock();
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.readLock().lock();
                lock.readLock().unlock();
            }});

        waitForQueuedThread(lock, t1);
        waitForQueuedThread(lock, t2);
        releaseWriteLock(lock);
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * Read trylock succeeds if write locked by current thread
     */
    public void testReadHoldingWriteLock() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        lock.writeLock().lock();
        assertTrue(lock.readLock().tryLock());
        lock.readLock().unlock();
        lock.writeLock().unlock();
    }

    /**
     * Read lock succeeds if write locked by current thread even if
     * other threads are waiting for readlock
     */
    public void testReadHoldingWriteLock2() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lock();
        lock.readLock().lock();
        lock.readLock().unlock();

        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.readLock().lock();
                lock.readLock().unlock();
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.readLock().lock();
                lock.readLock().unlock();
            }});

        waitForQueuedThread(lock, t1);
        waitForQueuedThread(lock, t2);
        assertWriteLockedBy(lock, Thread.currentThread());
        lock.readLock().lock();
        lock.readLock().unlock();
        releaseWriteLock(lock);
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * Read lock succeeds if write locked by current thread even if
     * other threads are waiting for writelock
     */
    public void testReadHoldingWriteLock3() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lock();
        lock.readLock().lock();
        lock.readLock().unlock();

        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});

        waitForQueuedThread(lock, t1);
        waitForQueuedThread(lock, t2);
        assertWriteLockedBy(lock, Thread.currentThread());
        lock.readLock().lock();
        lock.readLock().unlock();
        releaseWriteLock(lock);
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * Write lock succeeds if write locked by current thread even if
     * other threads are waiting for writelock
     */
    public void testWriteHoldingWriteLock4() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lock();
        lock.writeLock().lock();
        lock.writeLock().unlock();

        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});

        waitForQueuedThread(lock, t1);
        waitForQueuedThread(lock, t2);
        assertWriteLockedBy(lock, Thread.currentThread());
        assertEquals(1, lock.getWriteHoldCount());
        lock.writeLock().lock();
        assertWriteLockedBy(lock, Thread.currentThread());
        assertEquals(2, lock.getWriteHoldCount());
        lock.writeLock().unlock();
        releaseWriteLock(lock);
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * Fair Read trylock succeeds if write locked by current thread
     */
    public void testReadHoldingWriteLockFair() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
        lock.writeLock().lock();
        assertTrue(lock.readLock().tryLock());
        lock.readLock().unlock();
        lock.writeLock().unlock();
    }

    /**
     * Fair Read lock succeeds if write locked by current thread even if
     * other threads are waiting for readlock
     */
    public void testReadHoldingWriteLockFair2() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock(true);
        lock.writeLock().lock();
        lock.readLock().lock();
        lock.readLock().unlock();

        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.readLock().lock();
                lock.readLock().unlock();
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.readLock().lock();
                lock.readLock().unlock();
            }});

        waitForQueuedThread(lock, t1);
        waitForQueuedThread(lock, t2);
        assertWriteLockedBy(lock, Thread.currentThread());
        lock.readLock().lock();
        lock.readLock().unlock();
        releaseWriteLock(lock);
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * Fair Read lock succeeds if write locked by current thread even if
     * other threads are waiting for writelock
     */
    public void testReadHoldingWriteLockFair3() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock(true);
        lock.writeLock().lock();
        lock.readLock().lock();
        lock.readLock().unlock();

        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});

        waitForQueuedThread(lock, t1);
        waitForQueuedThread(lock, t2);
        assertWriteLockedBy(lock, Thread.currentThread());
        lock.readLock().lock();
        lock.readLock().unlock();
        releaseWriteLock(lock);
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * Fair Write lock succeeds if write locked by current thread even if
     * other threads are waiting for writelock
     */
    public void testWriteHoldingWriteLockFair4() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock(true);
        lock.writeLock().lock();
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.writeLock().lock();
                lock.writeLock().unlock();
            }});

        waitForQueuedThread(lock, t1);
        waitForQueuedThread(lock, t2);
        assertWriteLockedBy(lock, Thread.currentThread());
        assertEquals(1, lock.getWriteHoldCount());
        lock.writeLock().lock();
        assertEquals(2, lock.getWriteHoldCount());
        lock.writeLock().unlock();
        lock.writeLock().lock();
        lock.writeLock().unlock();
        releaseWriteLock(lock);
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * Read tryLock succeeds if readlocked but not writelocked
     */
    public void testTryLockWhenReadLocked() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        lock.readLock().lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertTrue(lock.readLock().tryLock());
                lock.readLock().unlock();
            }});

        awaitTermination(t);
        lock.readLock().unlock();
    }

    /**
     * write tryLock fails when readlocked
     */
    public void testWriteTryLockWhenReadLocked() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        lock.readLock().lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertFalse(lock.writeLock().tryLock());
            }});

        awaitTermination(t);
        lock.readLock().unlock();
    }

    /**
     * Fair Read tryLock succeeds if readlocked but not writelocked
     */
    public void testTryLockWhenReadLockedFair() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
        lock.readLock().lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertTrue(lock.readLock().tryLock());
                lock.readLock().unlock();
            }});

        awaitTermination(t);
        lock.readLock().unlock();
    }

    /**
     * Fair write tryLock fails when readlocked
     */
    public void testWriteTryLockWhenReadLockedFair() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
        lock.readLock().lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertFalse(lock.writeLock().tryLock());
            }});

        awaitTermination(t);
        lock.readLock().unlock();
    }

    /**
     * write timed tryLock times out if locked
     */
    public void testWriteTryLock_Timeout() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                long timeoutMillis = 10;
                assertFalse(lock.writeLock().tryLock(timeoutMillis, MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
            }});

        awaitTermination(t);
        assertTrue(lock.writeLock().isHeldByCurrentThread());
        lock.writeLock().unlock();
    }

    /**
     * read timed tryLock times out if write-locked
     */
    public void testReadTryLock_Timeout() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        lock.writeLock().lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                long timeoutMillis = 10;
                assertFalse(lock.readLock().tryLock(timeoutMillis, MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
            }});

        awaitTermination(t);
        assertTrue(lock.writeLock().isHeldByCurrentThread());
        lock.writeLock().unlock();
    }

    /**
     * write lockInterruptibly succeeds if lock free else is interruptible
     */
    public void testWriteLockInterruptibly() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lockInterruptibly();
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lockInterruptibly();
            }});

        waitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    /**
     * read lockInterruptibly succeeds if lock free else is interruptible
     */
    public void testReadLockInterruptibly() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        lock.writeLock().lockInterruptibly();
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.readLock().lockInterruptibly();
            }});

        waitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseWriteLock(lock);
    }

    /**
     * Calling await without holding lock throws IllegalMonitorStateException
     */
    public void testAwait_IllegalMonitor() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        try {
            c.await();
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
        try {
            c.await(LONG_DELAY_MS, MILLISECONDS);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
        try {
            c.awaitNanos(100);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
        try {
            c.awaitUninterruptibly();
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * Calling signal without holding lock throws IllegalMonitorStateException
     */
    public void testSignal_IllegalMonitor() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        try {
            c.signal();
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * Calling signalAll without holding lock throws IllegalMonitorStateException
     */
    public void testSignalAll_IllegalMonitor() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        try {
            c.signalAll();
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * awaitNanos without a signal times out
     */
    public void testAwaitNanos_Timeout() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        lock.writeLock().lock();
        long startTime = System.nanoTime();
        long timeoutMillis = 10;
        long timeoutNanos = MILLISECONDS.toNanos(timeoutMillis);
        long nanosRemaining = c.awaitNanos(timeoutNanos);
        assertTrue(nanosRemaining <= 0);
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
        lock.writeLock().unlock();
    }

    /**
     * timed await without a signal times out
     */
    public void testAwait_Timeout() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        lock.writeLock().lock();
        long startTime = System.nanoTime();
        long timeoutMillis = 10;
        assertFalse(c.await(timeoutMillis, MILLISECONDS));
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
        lock.writeLock().unlock();
    }

    /**
     * awaitUntil without a signal times out
     */
    public void testAwaitUntil_Timeout() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        lock.writeLock().lock();
        long startTime = System.nanoTime();
        long timeoutMillis = 10;
        java.util.Date d = new java.util.Date();
        assertFalse(c.awaitUntil(new java.util.Date(d.getTime() + timeoutMillis)));
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
        lock.writeLock().unlock();
    }

    /**
     * await returns when signalled
     */
    public void testAwait() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lock();
                locked.countDown();
                c.await();
                lock.writeLock().unlock();
            }});

        locked.await();
        lock.writeLock().lock();
        assertHasWaiters(lock, c, t);
        c.signal();
        assertHasNoWaiters(lock, c);
        assertTrue(t.isAlive());
        lock.writeLock().unlock();
        awaitTermination(t);
    }

    /**
     * awaitUninterruptibly doesn't abort on interrupt
     */
    public void testAwaitUninterruptibly() throws InterruptedException {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.writeLock().lock();
                locked.countDown();
                c.awaitUninterruptibly();
                assertTrue(Thread.interrupted());
                lock.writeLock().unlock();
            }});

        locked.await();
        lock.writeLock().lock();
        lock.writeLock().unlock();
        t.interrupt();
        long timeoutMillis = 10;
        assertThreadJoinTimesOut(t, timeoutMillis);
        lock.writeLock().lock();
        c.signal();
        lock.writeLock().unlock();
        awaitTermination(t);
    }

    /**
     * await is interruptible
     */
    public void testAwait_Interrupt() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lock();
                assertWriteLockedBy(lock, Thread.currentThread());
                assertHasNoWaiters(lock, c);
                locked.countDown();
                try {
                    c.await();
                } finally {
                    assertWriteLockedBy(lock, Thread.currentThread());
                    assertHasNoWaiters(lock, c);
                    lock.writeLock().unlock();
                    assertFalse(Thread.interrupted());
                }
            }});

        locked.await();
        assertHasWaiters(lock, c, t);
        t.interrupt();
        awaitTermination(t);
        assertNotWriteLocked(lock);
    }

    /**
     * awaitNanos is interruptible
     */
    public void testAwaitNanos_Interrupt() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lock();
                assertWriteLockedBy(lock, Thread.currentThread());
                assertHasNoWaiters(lock, c);
                locked.countDown();
                try {
                    c.awaitNanos(MILLISECONDS.toNanos(2 * LONG_DELAY_MS));
                } finally {
                    assertWriteLockedBy(lock, Thread.currentThread());
                    assertHasNoWaiters(lock, c);
                    lock.writeLock().unlock();
                    assertFalse(Thread.interrupted());
                }
            }});

        locked.await();
        assertHasWaiters(lock, c, t);
        t.interrupt();
        awaitTermination(t);
        assertNotWriteLocked(lock);
    }

    /**
     * awaitUntil is interruptible
     */
    public void testAwaitUntil_Interrupt() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lock();
                assertWriteLockedBy(lock, Thread.currentThread());
                assertHasNoWaiters(lock, c);
                locked.countDown();
                java.util.Date d = new java.util.Date();
                try {
                    c.awaitUntil(new java.util.Date(d.getTime() + 2 * LONG_DELAY_MS));
                } finally {
                    assertWriteLockedBy(lock, Thread.currentThread());
                    assertHasNoWaiters(lock, c);
                    lock.writeLock().unlock();
                    assertFalse(Thread.interrupted());
                }
            }});

        locked.await();
        assertHasWaiters(lock, c, t);
        t.interrupt();
        awaitTermination(t);
        assertNotWriteLocked(lock);
    }

    /**
     * signalAll wakes up all threads
     */
    public void testSignalAll() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked = new CountDownLatch(2);
        final Lock writeLock = lock.writeLock();
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                writeLock.lock();
                locked.countDown();
                c.await();
                writeLock.unlock();
            }});

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                writeLock.lock();
                locked.countDown();
                c.await();
                writeLock.unlock();
            }});

        locked.await();
        writeLock.lock();
        assertHasWaiters(lock, c, t1, t2);
        c.signalAll();
        assertHasNoWaiters(lock, c);
        writeLock.unlock();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * signal wakes up waiting threads in FIFO order.
     */
    public void testSignalWakesFifo() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked1 = new CountDownLatch(1);
        final CountDownLatch locked2 = new CountDownLatch(1);
        final Lock writeLock = lock.writeLock();
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                writeLock.lock();
                locked1.countDown();
                c.await();
                writeLock.unlock();
            }});

        locked1.await();

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                writeLock.lock();
                locked2.countDown();
                c.await();
                writeLock.unlock();
            }});

        locked2.await();

        writeLock.lock();
        assertHasWaiters(lock, c, t1, t2);
        assertFalse(lock.hasQueuedThreads());
        c.signal();
        assertHasWaiters(lock, c, t2);
        assertTrue(lock.hasQueuedThread(t1));
        assertFalse(lock.hasQueuedThread(t2));
        c.signal();
        assertHasNoWaiters(lock, c);
        assertTrue(lock.hasQueuedThread(t1));
        assertTrue(lock.hasQueuedThread(t2));
        writeLock.unlock();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * await after multiple reentrant locking preserves lock count
     */
    public void testAwaitLockCount() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked = new CountDownLatch(2);
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lock();
                assertWriteLockedBy(lock, Thread.currentThread());
                assertEquals(1, lock.writeLock().getHoldCount());
                locked.countDown();
                c.await();
                assertWriteLockedBy(lock, Thread.currentThread());
                assertEquals(1, lock.writeLock().getHoldCount());
                lock.writeLock().unlock();
            }});

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lock();
                lock.writeLock().lock();
                assertWriteLockedBy(lock, Thread.currentThread());
                assertEquals(2, lock.writeLock().getHoldCount());
                locked.countDown();
                c.await();
                assertWriteLockedBy(lock, Thread.currentThread());
                assertEquals(2, lock.writeLock().getHoldCount());
                lock.writeLock().unlock();
                lock.writeLock().unlock();
            }});

        locked.await();
        lock.writeLock().lock();
        assertHasWaiters(lock, c, t1, t2);
        c.signalAll();
        assertHasNoWaiters(lock, c);
        lock.writeLock().unlock();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * A serialized lock deserializes as unlocked
     */
    public void testSerialization() throws Exception {
        ReentrantReadWriteLock l = new ReentrantReadWriteLock();
        l.readLock().lock();
        l.readLock().unlock();

        ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
        ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
        out.writeObject(l);
        out.close();

        ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
        ReentrantReadWriteLock r = (ReentrantReadWriteLock) in.readObject();
        r.readLock().lock();
        r.readLock().unlock();
    }

    /**
     * hasQueuedThreads reports whether there are waiting threads
     */
    public void testHasQueuedThreads() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertFalse(lock.hasQueuedThreads());
        lock.writeLock().lock();
        assertFalse(lock.hasQueuedThreads());
        t1.start();
        waitForQueuedThread(lock, t1);
        assertTrue(lock.hasQueuedThreads());
        t2.start();
        waitForQueuedThread(lock, t2);
        assertTrue(lock.hasQueuedThreads());
        t1.interrupt();
        awaitTermination(t1);
        assertTrue(lock.hasQueuedThreads());
        lock.writeLock().unlock();
        awaitTermination(t2);
        assertFalse(lock.hasQueuedThreads());
    }

    /**
     * hasQueuedThread(null) throws NPE
     */
    public void testHasQueuedThreadNPE() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        try {
            lock.hasQueuedThread(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * hasQueuedThread reports whether a thread is queued.
     */
    public void testHasQueuedThread() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertFalse(lock.hasQueuedThread(t1));
        assertFalse(lock.hasQueuedThread(t2));
        lock.writeLock().lock();
        t1.start();
        waitForQueuedThread(lock, t1);
        assertTrue(lock.hasQueuedThread(t1));
        assertFalse(lock.hasQueuedThread(t2));
        t2.start();
        waitForQueuedThread(lock, t2);
        assertTrue(lock.hasQueuedThread(t1));
        assertTrue(lock.hasQueuedThread(t2));
        t1.interrupt();
        awaitTermination(t1);
        assertFalse(lock.hasQueuedThread(t1));
        assertTrue(lock.hasQueuedThread(t2));
        lock.writeLock().unlock();
        awaitTermination(t2);
        assertFalse(lock.hasQueuedThread(t1));
        assertFalse(lock.hasQueuedThread(t2));
    }

    /**
     * getQueueLength reports number of waiting threads
     */
    public void testGetQueueLength() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertEquals(0, lock.getQueueLength());
        lock.writeLock().lock();
        t1.start();
        waitForQueuedThread(lock, t1);
        assertEquals(1, lock.getQueueLength());
        t2.start();
        waitForQueuedThread(lock, t2);
        assertEquals(2, lock.getQueueLength());
        t1.interrupt();
        awaitTermination(t1);
        assertEquals(1, lock.getQueueLength());
        lock.writeLock().unlock();
        awaitTermination(t2);
        assertEquals(0, lock.getQueueLength());
    }

    /**
     * getQueuedThreads includes waiting threads
     */
    public void testGetQueuedThreads() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertTrue(lock.getQueuedThreads().isEmpty());
        lock.writeLock().lock();
        assertTrue(lock.getQueuedThreads().isEmpty());
        t1.start();
        waitForQueuedThread(lock, t1);
        assertEquals(1, lock.getQueuedThreads().size());
        assertTrue(lock.getQueuedThreads().contains(t1));
        t2.start();
        waitForQueuedThread(lock, t2);
        assertEquals(2, lock.getQueuedThreads().size());
        assertTrue(lock.getQueuedThreads().contains(t1));
        assertTrue(lock.getQueuedThreads().contains(t2));
        t1.interrupt();
        awaitTermination(t1);
        assertFalse(lock.getQueuedThreads().contains(t1));
        assertTrue(lock.getQueuedThreads().contains(t2));
        assertEquals(1, lock.getQueuedThreads().size());
        lock.writeLock().unlock();
        awaitTermination(t2);
        assertTrue(lock.getQueuedThreads().isEmpty());
    }

    /**
     * hasWaiters throws NPE if null
     */
    public void testHasWaitersNPE() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        try {
            lock.hasWaiters(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * getWaitQueueLength throws NPE if null
     */
    public void testGetWaitQueueLengthNPE() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        try {
            lock.getWaitQueueLength(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * getWaitingThreads throws NPE if null
     */
    public void testGetWaitingThreadsNPE() {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        try {
            lock.getWaitingThreads(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * hasWaiters throws IllegalArgumentException if not owned
     */
    public void testHasWaitersIAE() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final ReentrantReadWriteLock lock2 = new ReentrantReadWriteLock();
        try {
            lock2.hasWaiters(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * hasWaiters throws IllegalMonitorStateException if not locked
     */
    public void testHasWaitersIMSE() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        try {
            lock.hasWaiters(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * getWaitQueueLength throws IllegalArgumentException if not owned
     */
    public void testGetWaitQueueLengthIAE() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final ReentrantReadWriteLock lock2 = new ReentrantReadWriteLock();
        try {
            lock2.getWaitQueueLength(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * getWaitQueueLength throws IllegalMonitorStateException if not locked
     */
    public void testGetWaitQueueLengthIMSE() {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        try {
            lock.getWaitQueueLength(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * getWaitingThreads throws IllegalArgumentException if not owned
     */
    public void testGetWaitingThreadsIAE() {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final PublicReentrantReadWriteLock lock2 = new PublicReentrantReadWriteLock();
        try {
            lock2.getWaitingThreads(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * getWaitingThreads throws IllegalMonitorStateException if not locked
     */
    public void testGetWaitingThreadsIMSE() {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        try {
            lock.getWaitingThreads(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * hasWaiters returns true when a thread is waiting, else false
     */
    public void testHasWaiters() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lock();
                assertHasNoWaiters(lock, c);
                assertFalse(lock.hasWaiters(c));
                locked.countDown();
                c.await();
                assertHasNoWaiters(lock, c);
                assertFalse(lock.hasWaiters(c));
                lock.writeLock().unlock();
            }});

        locked.await();
        lock.writeLock().lock();
        assertHasWaiters(lock, c, t);
        assertTrue(lock.hasWaiters(c));
        c.signal();
        assertHasNoWaiters(lock, c);
        assertFalse(lock.hasWaiters(c));
        lock.writeLock().unlock();
        awaitTermination(t);
        assertHasNoWaiters(lock, c);
    }

    /**
     * getWaitQueueLength returns number of waiting threads
     */
    public void testGetWaitQueueLength() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lock();
                assertEquals(0, lock.getWaitQueueLength(c));
                locked.countDown();
                c.await();
                lock.writeLock().unlock();
            }});

        locked.await();
        lock.writeLock().lock();
        assertHasWaiters(lock, c, t);
        assertEquals(1, lock.getWaitQueueLength(c));
        c.signal();
        assertHasNoWaiters(lock, c);
        assertEquals(0, lock.getWaitQueueLength(c));
        lock.writeLock().unlock();
        awaitTermination(t);
    }

    /**
     * getWaitingThreads returns only and all waiting threads
     */
    public void testGetWaitingThreads() throws InterruptedException {
        final PublicReentrantReadWriteLock lock = new PublicReentrantReadWriteLock();
        final Condition c = lock.writeLock().newCondition();
        final CountDownLatch locked1 = new CountDownLatch(1);
        final CountDownLatch locked2 = new CountDownLatch(1);
        Thread t1 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lock();
                assertTrue(lock.getWaitingThreads(c).isEmpty());
                locked1.countDown();
                c.await();
                lock.writeLock().unlock();
            }});

        Thread t2 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.writeLock().lock();
                assertFalse(lock.getWaitingThreads(c).isEmpty());
                locked2.countDown();
                c.await();
                lock.writeLock().unlock();
            }});

        lock.writeLock().lock();
        assertTrue(lock.getWaitingThreads(c).isEmpty());
        lock.writeLock().unlock();

        t1.start();
        locked1.await();
        t2.start();
        locked2.await();

        lock.writeLock().lock();
        assertTrue(lock.hasWaiters(c));
        assertTrue(lock.getWaitingThreads(c).contains(t1));
        assertTrue(lock.getWaitingThreads(c).contains(t2));
        assertEquals(2, lock.getWaitingThreads(c).size());
        c.signalAll();
        assertHasNoWaiters(lock, c);
        lock.writeLock().unlock();

        awaitTermination(t1);
        awaitTermination(t2);

        assertHasNoWaiters(lock, c);
    }

    /**
     * toString indicates current lock state
     */
    public void testToString() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        String us = lock.toString();
        assertTrue(us.indexOf("Write locks = 0") >= 0);
        assertTrue(us.indexOf("Read locks = 0") >= 0);
        lock.writeLock().lock();
        String ws = lock.toString();
        assertTrue(ws.indexOf("Write locks = 1") >= 0);
        assertTrue(ws.indexOf("Read locks = 0") >= 0);
        lock.writeLock().unlock();
        lock.readLock().lock();
        lock.readLock().lock();
        String rs = lock.toString();
        assertTrue(rs.indexOf("Write locks = 0") >= 0);
        assertTrue(rs.indexOf("Read locks = 2") >= 0);
    }

    /**
     * readLock.toString indicates current lock state
     */
    public void testReadLockToString() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        String us = lock.readLock().toString();
        assertTrue(us.indexOf("Read locks = 0") >= 0);
        lock.readLock().lock();
        lock.readLock().lock();
        String rs = lock.readLock().toString();
        assertTrue(rs.indexOf("Read locks = 2") >= 0);
    }

    /**
     * writeLock.toString indicates current lock state
     */
    public void testWriteLockToString() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        String us = lock.writeLock().toString();
        assertTrue(us.indexOf("Unlocked") >= 0);
        lock.writeLock().lock();
        String ls = lock.writeLock().toString();
        assertTrue(ls.indexOf("Locked") >= 0);
    }

}
