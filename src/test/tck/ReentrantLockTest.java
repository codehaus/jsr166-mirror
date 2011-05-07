/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.*;
import java.io.*;

public class ReentrantLockTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(ReentrantLockTest.class);
    }

    /**
     * A runnable calling lockInterruptibly
     */
    class InterruptibleLockRunnable extends CheckedRunnable {
        final ReentrantLock lock;
        InterruptibleLockRunnable(ReentrantLock l) { lock = l; }
        public void realRun() throws InterruptedException {
            lock.lockInterruptibly();
        }
    }

    /**
     * A runnable calling lockInterruptibly that expects to be
     * interrupted
     */
    class InterruptedLockRunnable extends CheckedInterruptedRunnable {
        final ReentrantLock lock;
        InterruptedLockRunnable(ReentrantLock l) { lock = l; }
        public void realRun() throws InterruptedException {
            lock.lockInterruptibly();
        }
    }

    /**
     * Subclass to expose protected methods
     */
    static class PublicReentrantLock extends ReentrantLock {
        PublicReentrantLock() { super(); }
        PublicReentrantLock(boolean fair) { super(fair); }
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
    void releaseLock(PublicReentrantLock lock) {
        assertLockedBy(lock, Thread.currentThread());
        lock.unlock();
        assertFalse(lock.isHeldByCurrentThread());
        assertNotLocked(lock);
    }

    /**
     * Spin-waits until lock.hasQueuedThread(t) becomes true.
     */
    void waitForQueuedThread(PublicReentrantLock lock, Thread t) {
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
     * Checks that lock is not locked.
     */
    void assertNotLocked(PublicReentrantLock lock) {
        assertFalse(lock.isLocked());
        assertFalse(lock.isHeldByCurrentThread());
        assertNull(lock.getOwner());
        assertEquals(0, lock.getHoldCount());
    }

    /**
     * Checks that lock is locked by the given thread.
     */
    void assertLockedBy(PublicReentrantLock lock, Thread t) {
        assertTrue(lock.isLocked());
        assertSame(t, lock.getOwner());
        assertEquals(t == Thread.currentThread(),
                     lock.isHeldByCurrentThread());
        assertEquals(t == Thread.currentThread(),
                     lock.getHoldCount() > 0);
    }

    /**
     * Checks that condition c has no waiters.
     */
    void assertHasNoWaiters(PublicReentrantLock lock, Condition c) {
        assertHasWaiters(lock, c, new Thread[] {});
    }

    /**
     * Checks that condition c has exactly the given waiter threads.
     */
    void assertHasWaiters(PublicReentrantLock lock, Condition c,
                          Thread... threads) {
        lock.lock();
        assertEquals(threads.length > 0, lock.hasWaiters(c));
        assertEquals(threads.length, lock.getWaitQueueLength(c));
        assertEquals(threads.length == 0, lock.getWaitingThreads(c).isEmpty());
        assertEquals(threads.length, lock.getWaitingThreads(c).size());
        assertEquals(new HashSet<Thread>(lock.getWaitingThreads(c)),
                     new HashSet<Thread>(Arrays.asList(threads)));
        lock.unlock();
    }

    /**
     * Constructor sets given fairness, and is in unlocked state
     */
    public void testConstructor() {
        PublicReentrantLock lock;

        lock = new PublicReentrantLock();
        assertFalse(lock.isFair());
        assertNotLocked(lock);

        lock = new PublicReentrantLock(true);
        assertTrue(lock.isFair());
        assertNotLocked(lock);

        lock = new PublicReentrantLock(false);
        assertFalse(lock.isFair());
        assertNotLocked(lock);
    }

    /**
     * locking an unlocked lock succeeds
     */
    public void testLock() {
        PublicReentrantLock lock = new PublicReentrantLock();
        lock.lock();
        assertLockedBy(lock, Thread.currentThread());
        releaseLock(lock);
    }

    /**
     * locking an unlocked fair lock succeeds
     */
    public void testFairLock() {
        PublicReentrantLock lock = new PublicReentrantLock(true);
        lock.lock();
        assertLockedBy(lock, Thread.currentThread());
        releaseLock(lock);
    }

    /**
     * Unlocking an unlocked lock throws IllegalMonitorStateException
     */
    public void testUnlock_IllegalMonitorStateException() {
        ReentrantLock lock = new ReentrantLock();
        try {
            lock.unlock();
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * tryLock on an unlocked lock succeeds
     */
    public void testTryLock() {
        PublicReentrantLock lock = new PublicReentrantLock();
        assertTrue(lock.tryLock());
        assertLockedBy(lock, Thread.currentThread());
        releaseLock(lock);
    }

    /**
     * hasQueuedThreads reports whether there are waiting threads
     */
    public void testHasQueuedThreads() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertFalse(lock.hasQueuedThreads());
        lock.lock();
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
        lock.unlock();
        awaitTermination(t2);
        assertFalse(lock.hasQueuedThreads());
    }

    /**
     * getQueueLength reports number of waiting threads
     */
    public void testGetQueueLength() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertEquals(0, lock.getQueueLength());
        lock.lock();
        t1.start();
        waitForQueuedThread(lock, t1);
        assertEquals(1, lock.getQueueLength());
        t2.start();
        waitForQueuedThread(lock, t2);
        assertEquals(2, lock.getQueueLength());
        t1.interrupt();
        awaitTermination(t1);
        assertEquals(1, lock.getQueueLength());
        lock.unlock();
        awaitTermination(t2);
        assertEquals(0, lock.getQueueLength());
    }

    /**
     * getQueueLength reports number of waiting threads
     */
    public void testGetQueueLength_fair() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock(true);
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertEquals(0, lock.getQueueLength());
        lock.lock();
        t1.start();
        waitForQueuedThread(lock, t1);
        assertEquals(1, lock.getQueueLength());
        t2.start();
        waitForQueuedThread(lock, t2);
        assertEquals(2, lock.getQueueLength());
        t1.interrupt();
        awaitTermination(t1);
        assertEquals(1, lock.getQueueLength());
        lock.unlock();
        awaitTermination(t2);
        assertEquals(0, lock.getQueueLength());
    }

    /**
     * hasQueuedThread(null) throws NPE
     */
    public void testHasQueuedThreadNPE() {
        final ReentrantLock lock = new ReentrantLock();
        try {
            lock.hasQueuedThread(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * hasQueuedThread reports whether a thread is queued.
     */
    public void testHasQueuedThread() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertFalse(lock.hasQueuedThread(t1));
        assertFalse(lock.hasQueuedThread(t2));
        lock.lock();
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
        lock.unlock();
        awaitTermination(t2);
        assertFalse(lock.hasQueuedThread(t1));
        assertFalse(lock.hasQueuedThread(t2));
    }

    /**
     * getQueuedThreads includes waiting threads
     */
    public void testGetQueuedThreads() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        assertTrue(lock.getQueuedThreads().isEmpty());
        lock.lock();
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
        lock.unlock();
        awaitTermination(t2);
        assertTrue(lock.getQueuedThreads().isEmpty());
    }

    /**
     * timed tryLock is interruptible.
     */
    public void testTryLock_Interrupted() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        lock.lock();
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.tryLock(2 * LONG_DELAY_MS, MILLISECONDS);
            }});

        waitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseLock(lock);
    }

    /**
     * tryLock on a locked lock fails
     */
    public void testTryLockWhenLocked() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        lock.lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertFalse(lock.tryLock());
            }});

        awaitTermination(t);
        releaseLock(lock);
    }

    /**
     * Timed tryLock on a locked lock times out
     */
    public void testTryLock_Timeout() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        lock.lock();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                long timeoutMillis = 10;
                assertFalse(lock.tryLock(timeoutMillis, MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
            }});

        awaitTermination(t);
        releaseLock(lock);
    }

    /**
     * getHoldCount returns number of recursive holds
     */
    public void testGetHoldCount() {
        ReentrantLock lock = new ReentrantLock();
        for (int i = 1; i <= SIZE; i++) {
            lock.lock();
            assertEquals(i, lock.getHoldCount());
        }
        for (int i = SIZE; i > 0; i--) {
            lock.unlock();
            assertEquals(i-1, lock.getHoldCount());
        }
    }

    /**
     * isLocked is true when locked and false when not
     */
    public void testIsLocked() throws Exception {
        final ReentrantLock lock = new ReentrantLock();
        assertFalse(lock.isLocked());
        lock.lock();
        assertTrue(lock.isLocked());
        lock.lock();
        assertTrue(lock.isLocked());
        lock.unlock();
        assertTrue(lock.isLocked());
        lock.unlock();
        assertFalse(lock.isLocked());
        final CyclicBarrier barrier = new CyclicBarrier(2);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                lock.lock();
                assertTrue(lock.isLocked());
                barrier.await();
                barrier.await();
                lock.unlock();
            }});

        barrier.await();
        assertTrue(lock.isLocked());
        barrier.await();
        awaitTermination(t);
        assertFalse(lock.isLocked());
    }

    /**
     * lockInterruptibly is interruptible.
     */
    public void testLockInterruptibly_Interrupted() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        lock.lock();
        Thread t = newStartedThread(new InterruptedLockRunnable(lock));
        waitForQueuedThread(lock, t);
        t.interrupt();
        awaitTermination(t);
        releaseLock(lock);
    }

    /**
     * lockInterruptibly succeeds when unlocked, else is interruptible
     */
    public void testLockInterruptibly_Interrupted2() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        lock.lockInterruptibly();
        Thread t = newStartedThread(new InterruptedLockRunnable(lock));
        waitForQueuedThread(lock, t);
        t.interrupt();
        assertTrue(lock.isLocked());
        assertTrue(lock.isHeldByCurrentThread());
        awaitTermination(t);
        releaseLock(lock);
    }

    /**
     * Calling await without holding lock throws IllegalMonitorStateException
     */
    public void testAwait_IllegalMonitor() throws InterruptedException {
        final ReentrantLock lock = new ReentrantLock();
        final Condition c = lock.newCondition();
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
        final ReentrantLock lock = new ReentrantLock();
        final Condition c = lock.newCondition();
        try {
            c.signal();
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * awaitNanos without a signal times out
     */
    public void testAwaitNanos_Timeout() throws InterruptedException {
        final ReentrantLock lock = new ReentrantLock();
        final Condition c = lock.newCondition();
        lock.lock();
        long startTime = System.nanoTime();
        long timeoutMillis = 10;
        long timeoutNanos = MILLISECONDS.toNanos(timeoutMillis);
        long nanosRemaining = c.awaitNanos(timeoutNanos);
        assertTrue(nanosRemaining <= 0);
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
        lock.unlock();
    }

    /**
     * timed await without a signal times out
     */
    public void testAwait_Timeout() throws InterruptedException {
        final ReentrantLock lock = new ReentrantLock();
        final Condition c = lock.newCondition();
        lock.lock();
        long startTime = System.nanoTime();
        long timeoutMillis = 10;
        assertFalse(c.await(timeoutMillis, MILLISECONDS));
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
        lock.unlock();
    }

    /**
     * awaitUntil without a signal times out
     */
    public void testAwaitUntil_Timeout() throws InterruptedException {
        final ReentrantLock lock = new ReentrantLock();
        final Condition c = lock.newCondition();
        lock.lock();
        long startTime = System.nanoTime();
        long timeoutMillis = 10;
        java.util.Date d = new java.util.Date();
        assertFalse(c.awaitUntil(new java.util.Date(d.getTime() + timeoutMillis)));
        assertTrue(millisElapsedSince(startTime) >= timeoutMillis);
        lock.unlock();
    }

    /**
     * await returns when signalled
     */
    public void testAwait() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                locked.countDown();
                c.await();
                lock.unlock();
            }});

        locked.await();
        lock.lock();
        assertHasWaiters(lock, c, t);
        c.signal();
        assertHasNoWaiters(lock, c);
        assertTrue(t.isAlive());
        lock.unlock();
        awaitTermination(t);
    }

    /**
     * hasWaiters throws NPE if null
     */
    public void testHasWaitersNPE() {
        final ReentrantLock lock = new ReentrantLock();
        try {
            lock.hasWaiters(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * getWaitQueueLength throws NPE if null
     */
    public void testGetWaitQueueLengthNPE() {
        final ReentrantLock lock = new ReentrantLock();
        try {
            lock.getWaitQueueLength(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * getWaitingThreads throws NPE if null
     */
    public void testGetWaitingThreadsNPE() {
        final PublicReentrantLock lock = new PublicReentrantLock();
        try {
            lock.getWaitingThreads(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * hasWaiters throws IllegalArgumentException if not owned
     */
    public void testHasWaitersIAE() {
        final ReentrantLock lock = new ReentrantLock();
        final Condition c = lock.newCondition();
        final ReentrantLock lock2 = new ReentrantLock();
        try {
            lock2.hasWaiters(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * hasWaiters throws IllegalMonitorStateException if not locked
     */
    public void testHasWaitersIMSE() {
        final ReentrantLock lock = new ReentrantLock();
        final Condition c = lock.newCondition();
        try {
            lock.hasWaiters(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * getWaitQueueLength throws IllegalArgumentException if not owned
     */
    public void testGetWaitQueueLengthIAE() {
        final ReentrantLock lock = new ReentrantLock();
        final Condition c = lock.newCondition();
        final ReentrantLock lock2 = new ReentrantLock();
        try {
            lock2.getWaitQueueLength(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * getWaitQueueLength throws IllegalMonitorStateException if not locked
     */
    public void testGetWaitQueueLengthIMSE() {
        final ReentrantLock lock = new ReentrantLock();
        final Condition c = lock.newCondition();
        try {
            lock.getWaitQueueLength(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * getWaitingThreads throws IllegalArgumentException if not owned
     */
    public void testGetWaitingThreadsIAE() {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        final PublicReentrantLock lock2 = new PublicReentrantLock();
        try {
            lock2.getWaitingThreads(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * getWaitingThreads throws IllegalMonitorStateException if not locked
     */
    public void testGetWaitingThreadsIMSE() {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        try {
            lock.getWaitingThreads(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {}
    }

    /**
     * hasWaiters returns true when a thread is waiting, else false
     */
    public void testHasWaiters() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertHasNoWaiters(lock, c);
                assertFalse(lock.hasWaiters(c));
                locked.countDown();
                c.await();
                assertHasNoWaiters(lock, c);
                assertFalse(lock.hasWaiters(c));
                lock.unlock();
            }});

        locked.await();
        lock.lock();
        assertHasWaiters(lock, c, t);
        assertTrue(lock.hasWaiters(c));
        c.signal();
        assertHasNoWaiters(lock, c);
        assertFalse(lock.hasWaiters(c));
        lock.unlock();
        awaitTermination(t);
        assertHasNoWaiters(lock, c);
    }

    /**
     * getWaitQueueLength returns number of waiting threads
     */
    public void testGetWaitQueueLength() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        final CountDownLatch locked1 = new CountDownLatch(1);
        final CountDownLatch locked2 = new CountDownLatch(1);
        Thread t1 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertFalse(lock.hasWaiters(c));
                assertEquals(0, lock.getWaitQueueLength(c));
                locked1.countDown();
                c.await();
                lock.unlock();
            }});

        Thread t2 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertTrue(lock.hasWaiters(c));
                assertEquals(1, lock.getWaitQueueLength(c));
                locked2.countDown();
                c.await();
                lock.unlock();
            }});

        lock.lock();
        assertEquals(0, lock.getWaitQueueLength(c));
        lock.unlock();

        t1.start();
        locked1.await();

        lock.lock();
        assertHasWaiters(lock, c, t1);
        assertEquals(1, lock.getWaitQueueLength(c));
        lock.unlock();

        t2.start();
        locked2.await();

        lock.lock();
        assertHasWaiters(lock, c, t1, t2);
        assertEquals(2, lock.getWaitQueueLength(c));
        c.signalAll();
        assertHasNoWaiters(lock, c);
        lock.unlock();

        awaitTermination(t1);
        awaitTermination(t2);

        assertHasNoWaiters(lock, c);
    }

    /**
     * getWaitingThreads returns only and all waiting threads
     */
    public void testGetWaitingThreads() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        final CountDownLatch locked1 = new CountDownLatch(1);
        final CountDownLatch locked2 = new CountDownLatch(1);
        Thread t1 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertTrue(lock.getWaitingThreads(c).isEmpty());
                locked1.countDown();
                c.await();
                lock.unlock();
            }});

        Thread t2 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertFalse(lock.getWaitingThreads(c).isEmpty());
                locked2.countDown();
                c.await();
                lock.unlock();
            }});

        lock.lock();
        assertTrue(lock.getWaitingThreads(c).isEmpty());
        lock.unlock();

        t1.start();
        locked1.await();

        lock.lock();
        assertHasWaiters(lock, c, t1);
        assertTrue(lock.getWaitingThreads(c).contains(t1));
        assertFalse(lock.getWaitingThreads(c).contains(t2));
        assertEquals(1, lock.getWaitingThreads(c).size());
        lock.unlock();

        t2.start();
        locked2.await();

        lock.lock();
        assertHasWaiters(lock, c, t1, t2);
        assertTrue(lock.getWaitingThreads(c).contains(t1));
        assertTrue(lock.getWaitingThreads(c).contains(t2));
        assertEquals(2, lock.getWaitingThreads(c).size());
        c.signalAll();
        assertHasNoWaiters(lock, c);
        lock.unlock();

        awaitTermination(t1);
        awaitTermination(t2);

        assertHasNoWaiters(lock, c);
    }

    /**
     * awaitUninterruptibly doesn't abort on interrupt
     */
    public void testAwaitUninterruptibly() throws InterruptedException {
        final ReentrantLock lock = new ReentrantLock();
        final Condition c = lock.newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                lock.lock();
                locked.countDown();
                c.awaitUninterruptibly();
                assertTrue(Thread.interrupted());
                lock.unlock();
            }});

        locked.await();
        lock.lock();
        lock.unlock();
        t.interrupt();
        long timeoutMillis = 10;
        assertThreadJoinTimesOut(t, timeoutMillis);
        lock.lock();
        c.signal();
        lock.unlock();
        awaitTermination(t);
    }

    /**
     * await is interruptible
     */
    public void testAwait_Interrupt() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertTrue(lock.isLocked());
                assertTrue(lock.isHeldByCurrentThread());
                assertHasNoWaiters(lock, c);
                locked.countDown();
                try {
                    c.await();
                } finally {
                    assertTrue(lock.isLocked());
                    assertTrue(lock.isHeldByCurrentThread());
                    assertHasNoWaiters(lock, c);
                    lock.unlock();
                    assertFalse(Thread.interrupted());
                }
            }});

        locked.await();
        assertHasWaiters(lock, c, t);
        t.interrupt();
        awaitTermination(t);
        assertFalse(lock.isLocked());
    }

    /**
     * awaitNanos is interruptible
     */
    public void testAwaitNanos_Interrupt() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertTrue(lock.isLocked());
                assertTrue(lock.isHeldByCurrentThread());
                assertHasNoWaiters(lock, c);
                locked.countDown();
                try {
                    c.awaitNanos(MILLISECONDS.toNanos(2 * LONG_DELAY_MS));
                } finally {
                    assertTrue(lock.isLocked());
                    assertTrue(lock.isHeldByCurrentThread());
                    assertHasNoWaiters(lock, c);
                    lock.unlock();
                    assertFalse(Thread.interrupted());
                }
            }});

        locked.await();
        assertHasWaiters(lock, c, t);
        t.interrupt();
        awaitTermination(t);
        assertFalse(lock.isLocked());
    }

    /**
     * awaitUntil is interruptible
     */
    public void testAwaitUntil_Interrupt() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        final CountDownLatch locked = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertTrue(lock.isLocked());
                assertTrue(lock.isHeldByCurrentThread());
                assertHasNoWaiters(lock, c);
                locked.countDown();
                java.util.Date d = new java.util.Date();
                try {
                    c.awaitUntil(new java.util.Date(d.getTime() + 2 * LONG_DELAY_MS));
                } finally {
                    assertTrue(lock.isLocked());
                    assertTrue(lock.isHeldByCurrentThread());
                    assertHasNoWaiters(lock, c);
                    lock.unlock();
                    assertFalse(Thread.interrupted());
                }
            }});

        locked.await();
        assertHasWaiters(lock, c, t);
        t.interrupt();
        awaitTermination(t);
        assertFalse(lock.isLocked());
    }

    /**
     * signalAll wakes up all threads
     */
    public void testSignalAll() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        final CountDownLatch locked = new CountDownLatch(2);
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                locked.countDown();
                c.await();
                lock.unlock();
            }});

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                locked.countDown();
                c.await();
                lock.unlock();
            }});

        locked.await();
        lock.lock();
        assertHasWaiters(lock, c, t1, t2);
        c.signalAll();
        assertHasNoWaiters(lock, c);
        lock.unlock();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * await after multiple reentrant locking preserves lock count
     */
    public void testAwaitLockCount() throws InterruptedException {
        final PublicReentrantLock lock = new PublicReentrantLock();
        final Condition c = lock.newCondition();
        final CountDownLatch locked = new CountDownLatch(2);
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                assertLockedBy(lock, Thread.currentThread());
                assertEquals(1, lock.getHoldCount());
                locked.countDown();
                c.await();
                assertLockedBy(lock, Thread.currentThread());
                assertEquals(1, lock.getHoldCount());
                lock.unlock();
            }});

        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                lock.lock();
                lock.lock();
                assertLockedBy(lock, Thread.currentThread());
                assertEquals(2, lock.getHoldCount());
                locked.countDown();
                c.await();
                assertLockedBy(lock, Thread.currentThread());
                assertEquals(2, lock.getHoldCount());
                lock.unlock();
                lock.unlock();
            }});

        locked.await();
        lock.lock();
        assertHasWaiters(lock, c, t1, t2);
        assertEquals(1, lock.getHoldCount());
        c.signalAll();
        assertHasNoWaiters(lock, c);
        lock.unlock();
        awaitTermination(t1);
        awaitTermination(t2);
    }

    /**
     * A serialized lock deserializes as unlocked
     */
    public void testSerialization() throws Exception {
        ReentrantLock l = new ReentrantLock();
        l.lock();
        l.unlock();

        ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
        ObjectOutputStream out =
            new ObjectOutputStream(new BufferedOutputStream(bout));
        out.writeObject(l);
        out.close();

        ByteArrayInputStream bin =
            new ByteArrayInputStream(bout.toByteArray());
        ObjectInputStream in =
            new ObjectInputStream(new BufferedInputStream(bin));
        ReentrantLock r = (ReentrantLock) in.readObject();
        r.lock();
        r.unlock();
    }

    /**
     * toString indicates current lock state
     */
    public void testToString() {
        ReentrantLock lock = new ReentrantLock();
        String us = lock.toString();
        assertTrue(us.indexOf("Unlocked") >= 0);
        lock.lock();
        String ls = lock.toString();
        assertTrue(ls.indexOf("Locked") >= 0);
    }
}
