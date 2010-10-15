/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Other contributors include John Vint
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import junit.framework.Test;
import junit.framework.TestSuite;

public class PhaserTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(PhaserTest.class);
    }

    /** Checks state of phaser. */
    protected void assertState(Phaser phaser,
                               int phase, int parties, int unarrived) {
        assertEquals(phase, phaser.getPhase());
        assertEquals(parties, phaser.getRegisteredParties());
        assertEquals(unarrived, phaser.getUnarrivedParties());
        assertEquals(parties - unarrived, phaser.getArrivedParties());
        assertTrue((phaser.getPhase() >= 0) ^ phaser.isTerminated());
    }

    /** Checks state of terminated phaser. */
    protected void assertTerminated(Phaser phaser, int parties, int unarrived) {
        assertTrue(phaser.isTerminated());
        assertTrue(phaser.getPhase() < 0);
        assertEquals(parties, phaser.getRegisteredParties());
        assertEquals(unarrived, phaser.getUnarrivedParties());
        assertEquals(parties - unarrived, phaser.getArrivedParties());
    }

    protected void assertTerminated(Phaser phaser) {
        assertTerminated(phaser, 0, 0);
    }

    /**
     * Empty constructor builds a new Phaser with no parent, no registered
     * parties and initial phase number of 0
     */
    public void testConstructor1() {
        Phaser phaser = new Phaser();
        assertNull(phaser.getParent());
        assertEquals(0, phaser.getArrivedParties());
        assertEquals(0, phaser.getPhase());
    }

    /**
     * A negative party number for the constructor throws illegal argument
     * exception
     */
    public void testConstructor2() {
        try {
            new Phaser(-1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * The parent being input into the constructor should equal the original
     * parent when being returned
     */
    public void testConstructor3() {
        Phaser parent = new Phaser();
        assertEquals(parent, new Phaser(parent).getParent());
    }

    /**
     * A negative party number for the constructor throws illegal argument
     * exception
     */
    public void testConstructor4() {
        try {
            new Phaser(new Phaser(), -1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * The parent being input into the parameter should equal the original
     * parent when being returned
     */
    public void testConstructor5() {
        Phaser parent = new Phaser();
        assertEquals(parent, new Phaser(parent, 0).getParent());
    }

    /**
     * register() will increment the number of unarrived parties by one and not
     * affect its arrived parties
     */
    public void testRegister1() {
        Phaser phaser = new Phaser();
        assertState(phaser, 0, 0, 0);
        assertEquals(0, phaser.register());
        assertState(phaser, 0, 1, 1);
    }

    /**
     * Registering more than 65536 parties causes IllegalStateException
     */
    public void testRegister2() {
        Phaser phaser = new Phaser(0);
        int maxParties = (1 << 16) - 1;
        assertState(phaser, 0, 0, 0);
        assertEquals(0, phaser.bulkRegister(maxParties - 10));
        assertState(phaser, 0, maxParties - 10, maxParties - 10);
        for (int i = 0; i < 10; i++) {
            assertState(phaser, 0, maxParties - 10 + i, maxParties - 10 + i);
            assertEquals(0, phaser.register());
        }
        assertState(phaser, 0, maxParties, maxParties);
        try {
            phaser.register();
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * register() correctly returns the current barrier phase number when
     * invoked
     */
    public void testRegister3() {
        Phaser phaser = new Phaser();
        assertEquals(0, phaser.register());
        assertEquals(0, phaser.arrive());
        assertEquals(1, phaser.register());
        assertState(phaser, 1, 2, 2);
    }

    /**
     * register causes the next arrive to not increment the phase rather retain
     * the phase number
     */
    public void testRegister4() {
        Phaser phaser = new Phaser(1);
        assertEquals(0, phaser.arrive());
        assertEquals(1, phaser.register());
        assertEquals(1, phaser.arrive());
        assertState(phaser, 1, 2, 1);
    }

    /**
     * Invoking bulkRegister with a negative parameter throws an
     * IllegalArgumentException
     */
    public void testBulkRegister1() {
        try {
            new Phaser().bulkRegister(-1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * bulkRegister should correctly record the number of unarrived parties with
     * the number of parties being registered
     */
    public void testBulkRegister2() {
        Phaser phaser = new Phaser();
        assertEquals(0, phaser.bulkRegister(20));
        assertState(phaser, 0, 20, 20);
    }

    /**
     * Registering with a number of parties greater than or equal to 1<<16
     * throws IllegalStateException.
     */
    public void testBulkRegister3() {
        assertEquals(0, new Phaser().bulkRegister((1 << 16) - 1));

        try {
            new Phaser().bulkRegister(1 << 16);
            shouldThrow();
        } catch (IllegalStateException success) {}

        try {
            new Phaser(2).bulkRegister((1 << 16) - 2);
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * the phase number increments correctly when tripping the barrier
     */
    public void testPhaseIncrement1() {
        for (int size = 1; size < nine; size++) {
            final Phaser phaser = new Phaser(size);
            for (int index = 0; index <= (1 << size); index++) {
                int phase = phaser.arrive();
                assertTrue(index % size == 0 ? (index / size) == phase : index - (phase * size) > 0);
            }
        }
    }

    /**
     * arrive() on a registered phaser increments phase.
     */
    public void testArrive1() {
        Phaser phaser = new Phaser(1);
        assertState(phaser, 0, 1, 1);
        assertEquals(0, phaser.arrive());
        assertState(phaser, 1, 1, 1);
    }

    /**
     * arriveAndDeregister does not wait for others to arrive at barrier
     */
    public void testArriveAndDeregister() throws InterruptedException {
        final Phaser phaser = new Phaser(1);
        for (int i = 0; i < 10; i++) {
            assertState(phaser, 0, 1, 1);
            assertEquals(0, phaser.register());
            assertState(phaser, 0, 2, 2);
            assertEquals(0, phaser.arriveAndDeregister());
            assertState(phaser, 0, 1, 1);
        }
        assertEquals(0, phaser.arriveAndDeregister());
        assertTerminated(phaser);
    }

    /**
     * arriveAndDeregister does not wait for others to arrive at barrier
     */
    public void testArrive2() throws InterruptedException {
        final Phaser phaser = new Phaser();
        assertEquals(0, phaser.register());
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 10; i++) {
            assertEquals(0, phaser.register());
            threads.add(newStartedThread(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    assertEquals(0, phaser.arriveAndDeregister());
                }}));
        }

        for (Thread thread : threads)
            awaitTermination(thread, LONG_DELAY_MS);
        assertState(phaser, 0, 1, 1);
        assertEquals(0, phaser.arrive());
        assertState(phaser, 1, 1, 1);
    }

    /**
     * arrive() returns a negative number if the Phaser is terminated
     */
    public void testArrive3() {
        Phaser phaser = new Phaser(1);
        phaser.forceTermination();
        assertTerminated(phaser, 1, 1);
        assertTrue(phaser.arrive() < 0);
        assertTrue(phaser.register() < 0);
        assertTrue(phaser.arriveAndDeregister() < 0);
        assertTrue(phaser.awaitAdvance(1) < 0);
        assertTrue(phaser.getPhase() < 0);
    }

    /**
     * arriveAndDeregister() throws IllegalStateException if number of
     * registered or unarrived parties would become negative
     */
    public void testArriveAndDeregister1() {
        try {
            Phaser phaser = new Phaser();
            phaser.arriveAndDeregister();
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * arriveAndDeregister reduces the number of arrived parties
     */
    public void testArriveAndDeregister2() {
        final Phaser phaser = new Phaser(1);
        assertEquals(0, phaser.register());
        assertEquals(0, phaser.arrive());
        assertState(phaser, 0, 2, 1);
        assertEquals(0, phaser.arriveAndDeregister());
        assertState(phaser, 1, 1, 1);
    }

    /**
     * arriveAndDeregister arrives at the barrier on a phaser with a parent and
     * when a deregistration occurs and causes the phaser to have zero parties
     * its parent will be deregistered as well
     */
    public void testArriveAndDeregister3() {
        Phaser parent = new Phaser();
        Phaser child = new Phaser(parent);
        assertState(child, 0, 0, 0);
        assertState(parent, 0, 1, 1);
        assertEquals(0, child.register());
        assertState(child, 0, 1, 1);
        assertState(parent, 0, 1, 1);
        assertEquals(0, child.arriveAndDeregister());
        assertTerminated(child);
        assertTerminated(parent);
    }

    /**
     * arriveAndDeregister deregisters one party from its parent when
     * the number of parties of child is zero after deregistration
     */
    public void testArriveAndDeregister4() {
        Phaser parent = new Phaser();
        Phaser child = new Phaser(parent);
        assertEquals(0, parent.register());
        assertEquals(0, child.register());
        assertState(child, 0, 1, 1);
        assertState(parent, 0, 2, 2);
        assertEquals(0, child.arriveAndDeregister());
        assertState(child, 0, 0, 0);
        assertState(parent, 0, 1, 1);
    }

    /**
     * arriveAndDeregister deregisters one party from its parent when
     * the number of parties of root is nonzero after deregistration.
     */
    public void testArriveAndDeregister5() {
        Phaser root = new Phaser();
        Phaser parent = new Phaser(root);
        Phaser child = new Phaser(parent);
        assertState(root, 0, 1, 1);
        assertState(parent, 0, 1, 1);
        assertState(child, 0, 0, 0);
        assertEquals(0, child.register());
        assertState(root, 0, 1, 1);
        assertState(parent, 0, 1, 1);
        assertState(child, 0, 1, 1);
        assertEquals(0, child.arriveAndDeregister());
        assertTerminated(child);
        assertTerminated(parent);
        assertTerminated(root);
    }

    /**
     * arriveAndDeregister returns the phase in which it leaves the
     * phaser in after deregistration
     */
    public void testArriveAndDeregister6() throws InterruptedException {
        final Phaser phaser = new Phaser(2);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                assertEquals(0, phaser.arrive());
            }});
        assertEquals(1, phaser.arriveAndAwaitAdvance());
        assertState(phaser, 1, 2, 2);
        assertEquals(1, phaser.arriveAndDeregister());
        assertState(phaser, 1, 1, 1);
        assertEquals(1, phaser.arriveAndDeregister());
        assertTerminated(phaser);
        awaitTermination(t, SHORT_DELAY_MS);
    }

    /**
     * awaitAdvance succeeds upon advance
     */
    public void testAwaitAdvance1() {
        final Phaser phaser = new Phaser(1);
        assertEquals(0, phaser.arrive());
        assertEquals(1, phaser.awaitAdvance(0));
    }

    /**
     * awaitAdvance with a negative parameter will return without affecting the
     * phaser
     */
    public void testAwaitAdvance2() {
        Phaser phaser = new Phaser();
        assertTrue(phaser.awaitAdvance(-1) < 0);
        assertState(phaser, 0, 0, 0);
    }

    /**
     * awaitAdvance continues waiting if interrupted
     */
    public void testAwaitAdvance3() throws InterruptedException {
        final Phaser phaser = new Phaser();
        assertEquals(0, phaser.register());
        final CountDownLatch threadStarted = new CountDownLatch(1);

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertEquals(0, phaser.register());
                threadStarted.countDown();
                assertEquals(1, phaser.awaitAdvance(phaser.arrive()));
                assertTrue(Thread.currentThread().isInterrupted());
            }});
        assertTrue(threadStarted.await(SMALL_DELAY_MS, MILLISECONDS));
        t.interrupt();
        assertEquals(0, phaser.arrive());
        awaitTermination(t, SMALL_DELAY_MS);
    }

    /**
     * awaitAdvance atomically waits for all parties within the same phase to
     * complete before continuing
     */
    public void testAwaitAdvance4() throws InterruptedException {
        final Phaser phaser = new Phaser(4);
        final AtomicInteger count = new AtomicInteger(0);
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 4; i++)
            threads.add(newStartedThread(new CheckedRunnable() {
                public void realRun() {
                    for (int k = 0; k < 3; k++) {
                        assertEquals(2*k+1, phaser.arriveAndAwaitAdvance());
                        count.incrementAndGet();
                        assertEquals(2*k+1, phaser.arrive());
                        assertEquals(2*k+2, phaser.awaitAdvance(2*k+1));
                        assertEquals(count.get(), 4*(k+1));
                    }}}));

        for (Thread thread : threads)
            awaitTermination(thread, MEDIUM_DELAY_MS);
    }

    /**
     * awaitAdvance returns the current phase
     */
    public void testAwaitAdvance5() throws InterruptedException {
        final Phaser phaser = new Phaser(1);
        assertEquals(1, phaser.awaitAdvance(phaser.arrive()));
        assertEquals(1, phaser.getPhase());
        assertEquals(1, phaser.register());
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 8; i++) {
            final CountDownLatch latch = new CountDownLatch(1);
            final boolean goesFirst = ((i & 1) == 0);
            threads.add(newStartedThread(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    if (goesFirst)
                        latch.countDown();
                    else
                        assertTrue(latch.await(SMALL_DELAY_MS, MILLISECONDS));
                    phaser.arrive();
                }}));
            if (goesFirst)
                assertTrue(latch.await(SMALL_DELAY_MS, MILLISECONDS));
            else
                latch.countDown();
            assertEquals(i + 2, phaser.awaitAdvance(phaser.arrive()));
            assertEquals(i + 2, phaser.getPhase());
        }
        for (Thread thread : threads)
            awaitTermination(thread, SMALL_DELAY_MS);
    }

    /**
     * awaitAdvance returns when the phaser is externally terminated
     */
    public void testAwaitAdvance6() throws InterruptedException {
        final Phaser phaser = new Phaser(3);
        final CountDownLatch threadsStarted = new CountDownLatch(2);
        final List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 2; i++) {
            Runnable r = new CheckedRunnable() {
                public void realRun() {
                    assertEquals(0, phaser.arrive());
                    threadsStarted.countDown();
                    assertTrue(phaser.awaitAdvance(0) < 0);
                    assertTrue(phaser.isTerminated());
                    assertTrue(phaser.getPhase() < 0);
                    assertEquals(3, phaser.getRegisteredParties());
                }};
            threads.add(newStartedThread(r));
        }
        threadsStarted.await();
        phaser.forceTermination();
        for (Thread thread : threads)
            awaitTermination(thread, SMALL_DELAY_MS);
        assertTrue(phaser.isTerminated());
        assertTrue(phaser.getPhase() < 0);
        assertEquals(3, phaser.getRegisteredParties());
    }

    /**
     * arriveAndAwaitAdvance throws IllegalStateException with no
     * unarrived parties
     */
    public void testArriveAndAwaitAdvance1() {
        try {
            Phaser phaser = new Phaser();
            phaser.arriveAndAwaitAdvance();
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * Interrupted arriveAndAwaitAdvance does not throw InterruptedException
     */
    public void testArriveAndAwaitAdvance2() throws InterruptedException {
        final Phaser phaser = new Phaser(2);
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final AtomicBoolean advanced = new AtomicBoolean(false);
        final AtomicBoolean checkedInterruptStatus = new AtomicBoolean(false);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                threadStarted.countDown();
                assertEquals(1, phaser.arriveAndAwaitAdvance());
                assertState(phaser, 1, 2, 2);
                advanced.set(true);
                assertTrue(Thread.currentThread().isInterrupted());
                while (!checkedInterruptStatus.get())
                    Thread.yield();
            }});

        assertTrue(threadStarted.await(SMALL_DELAY_MS, MILLISECONDS));
        t.interrupt();
        assertEquals(0, phaser.arrive());
        while (!advanced.get())
            Thread.yield();
        assertTrue(t.isInterrupted());
        checkedInterruptStatus.set(true);
        awaitTermination(t, SMALL_DELAY_MS);
    }

    /**
     * arriveAndAwaitAdvance waits for all threads to arrive, the
     * number of arrived parties is the same number that is accounted
     * for when the main thread awaitsAdvance
     */
    public void testArriveAndAwaitAdvance3() throws InterruptedException {
        final Phaser phaser = new Phaser(1);
        final int THREADS = 3;
        final CountDownLatch threadsStarted = new CountDownLatch(THREADS);
        final List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < THREADS; i++)
            threads.add(newStartedThread(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    assertEquals(0, phaser.register());
                    threadsStarted.countDown();
                    assertEquals(1, phaser.arriveAndAwaitAdvance());
                }}));

        assertTrue(threadsStarted.await(MEDIUM_DELAY_MS, MILLISECONDS));
        long t0 = System.nanoTime();
        while (phaser.getArrivedParties() < THREADS)
            Thread.yield();
        assertEquals(THREADS, phaser.getArrivedParties());
        assertTrue(NANOSECONDS.toMillis(System.nanoTime() - t0) < SMALL_DELAY_MS);
        for (Thread thread : threads)
            assertTrue(thread.isAlive());
        assertState(phaser, 0, THREADS + 1, 1);
        phaser.arriveAndAwaitAdvance();
        for (Thread thread : threads)
            awaitTermination(thread, SMALL_DELAY_MS);
        assertState(phaser, 1, THREADS + 1, THREADS + 1);
    }

}
