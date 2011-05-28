/*
 * Written by Doug Lea and Martin Buchholz with assistance from members
 * of JCP JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Contains tests generally applicable to BlockingQueue implementations.
 */
public abstract class BlockingQueueTest extends JSR166TestCase {
    /*
     * This is the start of an attempt to refactor the tests for the
     * various related implementations of related interfaces without
     * too much duplicated code.  junit does not really support such
     * testing.  Here subclasses of TestCase not only contain tests,
     * but also configuration information that describes the
     * implementation class, most importantly how to instantiate
     * instances.
     */

    /** Like suite(), but non-static */
    public Test testSuite() {
        // TODO: filter the returned tests using the configuration
        // information provided by the subclass via protected methods.
        return new TestSuite(this.getClass());
    }

    /** Returns an empty instance of the implementation class. */
    protected abstract BlockingQueue emptyCollection();

    /**
     * timed poll before a delayed offer times out; after offer succeeds;
     * on interruption throws
     */
    public void testTimedPollWithOffer() throws InterruptedException {
        final BlockingQueue q = emptyCollection();
        final CheckedBarrier barrier = new CheckedBarrier(2);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long startTime = System.nanoTime();
                assertNull(q.poll(timeoutMillis(), MILLISECONDS));
                assertTrue(millisElapsedSince(startTime) >= timeoutMillis());

                barrier.await();

                assertSame(zero, q.poll(LONG_DELAY_MS, MILLISECONDS));

                Thread.currentThread().interrupt();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());

                barrier.await();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        barrier.await();
        long startTime = System.nanoTime();
        assertTrue(q.offer(zero, LONG_DELAY_MS, MILLISECONDS));
        assertTrue(millisElapsedSince(startTime) < LONG_DELAY_MS);

        barrier.await();
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * take() blocks interruptibly when empty
     */
    public void testTakeFromEmptyBlocksInterruptibly() {
        final BlockingQueue q = emptyCollection();
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                threadStarted.countDown();
                try {
                    q.take();
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(threadStarted);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * take() throws InterruptedException immediately if interrupted
     * before waiting
     */
    public void testTakeFromEmptyAfterInterrupt() {
        final BlockingQueue q = emptyCollection();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                Thread.currentThread().interrupt();
                try {
                    q.take();
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        awaitTermination(t);
    }

    /**
     * timed poll() blocks interruptibly when empty
     */
    public void testTimedPollFromEmptyBlocksInterruptibly() {
        final BlockingQueue q = emptyCollection();
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                threadStarted.countDown();
                try {
                    q.poll(2 * LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        await(threadStarted);
        assertThreadStaysAlive(t);
        t.interrupt();
        awaitTermination(t);
    }

    /**
     * timed poll() throws InterruptedException immediately if
     * interrupted before waiting
     */
    public void testTimedPollFromEmptyAfterInterrupt() {
        final BlockingQueue q = emptyCollection();
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                Thread.currentThread().interrupt();
                try {
                    q.poll(2 * LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertFalse(Thread.interrupted());
            }});

        awaitTermination(t);
    }

    /** For debugging. */
    public void XXXXtestFails() {
        fail(emptyCollection().getClass().toString());
    }

}
