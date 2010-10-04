/*
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.*;

public class LockSupportTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(LockSupportTest.class);
    }

    /**
     * park is released by subsequent unpark
     */
    public void testParkBeforeUnpark() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                threadStarted.countDown();
                LockSupport.park();
            }});

        t.start();
        threadStarted.await();
        Thread.sleep(SHORT_DELAY_MS);
        LockSupport.unpark(t);
        joinWith(t);
    }

    /**
     * parkUntil is released by subsequent unpark
     */
    public void testParkUntilBeforeUnpark() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                long d = new Date().getTime() + LONG_DELAY_MS;
                long nanos = LONG_DELAY_MS * 1000L * 1000L;
                long t0 = System.nanoTime();
                threadStarted.countDown();
                LockSupport.parkUntil(d);
                assertTrue(System.nanoTime() - t0 < nanos);
            }});

        t.start();
        threadStarted.await();
        Thread.sleep(SHORT_DELAY_MS);
        LockSupport.unpark(t);
        joinWith(t);
    }

    /**
     * parkNanos is released by subsequent unpark
     */
    public void testParkNanosBeforeUnpark() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                long nanos = LONG_DELAY_MS * 1000L * 1000L;
                long t0 = System.nanoTime();
                threadStarted.countDown();
                LockSupport.parkNanos(nanos);
                assertTrue(System.nanoTime() - t0 < nanos);
            }});

        t.start();
        threadStarted.await();
        Thread.sleep(SHORT_DELAY_MS);
        LockSupport.unpark(t);
        joinWith(t);
    }

    /**
     * park is released by preceding unpark
     */
    public void testParkAfterUnpark() throws Exception {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final AtomicBoolean unparked = new AtomicBoolean(false);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                while (!unparked.get())
                    Thread.yield();
                LockSupport.park();
            }});

        t.start();
        threadStarted.await();
        LockSupport.unpark(t);
        unparked.set(true);
        joinWith(t);
    }

    /**
     * parkUntil is released by preceding unpark
     */
    public void testParkUntilAfterUnpark() throws Exception {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final AtomicBoolean unparked = new AtomicBoolean(false);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                while (!unparked.get())
                    Thread.yield();
                long d = new Date().getTime() + LONG_DELAY_MS;
                long nanos = LONG_DELAY_MS * 1000L * 1000L;
                long t0 = System.nanoTime();
                LockSupport.parkUntil(d);
                assertTrue(System.nanoTime() - t0 < nanos);
            }});

        t.start();
        threadStarted.await();
        LockSupport.unpark(t);
        unparked.set(true);
        joinWith(t);
    }

    /**
     * parkNanos is released by preceding unpark
     */
    public void testParkNanosAfterUnpark() throws Exception {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final AtomicBoolean unparked = new AtomicBoolean(false);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                while (!unparked.get())
                    Thread.yield();
                long nanos = LONG_DELAY_MS * 1000L * 1000L;
                long t0 = System.nanoTime();
                LockSupport.parkNanos(nanos);
                assertTrue(System.nanoTime() - t0 < nanos);
            }});

        t.start();
        threadStarted.await();
        LockSupport.unpark(t);
        unparked.set(true);
        joinWith(t);
    }

    /**
     * park is released by subsequent interrupt
     */
    public void testParkBeforeInterrupt() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                assertFalse(Thread.currentThread().isInterrupted());
                threadStarted.countDown();
                LockSupport.park();
                assertTrue(Thread.currentThread().isInterrupted());
            }});

        t.start();
        threadStarted.await();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        joinWith(t);
    }

    /**
     * parkUntil is released by subsequent interrupt
     */
    public void testParkUntilBeforeInterrupt() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                long d = new Date().getTime() + LONG_DELAY_MS;
                long nanos = LONG_DELAY_MS * 1000L * 1000L;
                long t0 = System.nanoTime();
                threadStarted.countDown();
                LockSupport.parkUntil(d);
                assertTrue(System.nanoTime() - t0 < nanos);
            }});

        t.start();
        threadStarted.await();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        joinWith(t);
    }

    /**
     * parkNanos is released by subsequent interrupt
     */
    public void testParkNanosBeforeInterrupt() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                long nanos = LONG_DELAY_MS * 1000L * 1000L;
                long t0 = System.nanoTime();
                threadStarted.countDown();
                LockSupport.parkNanos(nanos);
                assertTrue(System.nanoTime() - t0 < nanos);
            }});

        t.start();
        threadStarted.await();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        joinWith(t);
    }

    /**
     * park is released by preceding interrupt
     */
    public void testParkAfterInterrupt() throws Exception {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final AtomicBoolean unparked = new AtomicBoolean(false);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                while (!unparked.get())
                    Thread.yield();
                assertTrue(Thread.currentThread().isInterrupted());
                LockSupport.park();
                assertTrue(Thread.currentThread().isInterrupted());
            }});

        t.start();
        threadStarted.await();
        t.interrupt();
        unparked.set(true);
        joinWith(t);
    }

    /**
     * parkUntil is released by preceding interrupt
     */
    public void testParkUntilAfterInterrupt() throws Exception {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final AtomicBoolean unparked = new AtomicBoolean(false);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                while (!unparked.get())
                    Thread.yield();
                long d = new Date().getTime() + LONG_DELAY_MS;
                long nanos = LONG_DELAY_MS * 1000L * 1000L;
                long t0 = System.nanoTime();
                LockSupport.parkUntil(d);
                assertTrue(System.nanoTime() - t0 < nanos);
            }});

        t.start();
        threadStarted.await();
        t.interrupt();
        unparked.set(true);
        joinWith(t);
    }

    /**
     * parkNanos is released by preceding interrupt
     */
    public void testParkNanosAfterInterrupt() throws Exception {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final AtomicBoolean unparked = new AtomicBoolean(false);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                while (!unparked.get())
                    Thread.yield();
                long nanos = LONG_DELAY_MS * 1000L * 1000L;
                long t0 = System.nanoTime();
                LockSupport.parkNanos(nanos);
                assertTrue(System.nanoTime() - t0 < nanos);
            }});

        t.start();
        threadStarted.await();
        t.interrupt();
        unparked.set(true);
        joinWith(t);
    }

    /**
     * parkNanos times out if not unparked
     */
    public void testParkNanosTimesOut() throws InterruptedException {
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                final long timeoutNanos = SHORT_DELAY_MS * 1000L * 1000L;
                long t0 = System.nanoTime();
                LockSupport.parkNanos(timeoutNanos);
                assertTrue(System.nanoTime() - t0 >= timeoutNanos);
            }});

        t.start();
        joinWith(t);
    }


    /**
     * parkUntil times out if not unparked
     */
    public void testParkUntilTimesOut() throws InterruptedException {
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                long d = new Date().getTime() + SHORT_DELAY_MS;
                // beware of rounding
                long timeoutNanos = (SHORT_DELAY_MS - 1) * 1000L * 1000L;
                long t0 = System.nanoTime();
                LockSupport.parkUntil(d);
                assertTrue(System.nanoTime() - t0 >= timeoutNanos);
            }});

        t.start();
        joinWith(t);
    }

    /**
     * parkUntil(0) returns immediately
     * Requires hotspot fix for:
     * 6763959 java.util.concurrent.locks.LockSupport.parkUntil(0) blocks forever
     */
    public void XXXtestParkUntil0Returns() throws InterruptedException {
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                LockSupport.parkUntil(0L);
            }});

        t.start();
        joinWith(t);
    }

    private void joinWith(Thread t) throws InterruptedException {
        t.join(MEDIUM_DELAY_MS);
        if (t.isAlive()) {
            fail("Test timed out");
            t.interrupt();
        }
    }
}
