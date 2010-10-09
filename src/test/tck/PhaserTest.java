/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Other contributors include John Vint
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import junit.framework.Test;
import junit.framework.TestSuite;

public class PhaserTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(PhaserTest.class);
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
        assertEquals(0, phaser.getUnarrivedParties());
        phaser.register();
        assertEquals(1, phaser.getUnarrivedParties());
        assertEquals(0, phaser.getArrivedParties());
    }

    /**
     * Registering more than 65536 parties causes IllegalStateException
     */
    public void testRegister2() {
        Phaser phaser = new Phaser(0);
        int expectedUnnarivedParties = (1 << 16) - 1;
        for (int i = 0; i < expectedUnnarivedParties; i++) {
            phaser.register();
            assertEquals(i + 1, phaser.getUnarrivedParties());
        }
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
        phaser.arrive();
        assertEquals(1, phaser.register());
    }

    /**
     * register causes the next arrive to not increment the phase rather retain
     * the phase number
     */
    public void testRegister4() {
        Phaser phaser = new Phaser(1);
        phaser.arrive();
        int expectedPhase = phaser.register();
        phaser.arrive();
        assertEquals(expectedPhase, phaser.getPhase());
    }

    public void testRegister5() {
        Phaser phaser = new Phaser();
        phaser.register();
        assertEquals(1, phaser.getUnarrivedParties());
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
        phaser.bulkRegister(20);
        assertEquals(20, phaser.getUnarrivedParties());
    }

    /**
     * Registering with a number of parties greater than or equal to 1<<16
     * throws IllegalStateException.
     */
    public void testBulkRegister3() {
        try {
            new Phaser().bulkRegister(1 << 16);
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
     * Arrive() on a registered phaser increments phase.
     */
    public void testArrive1() {
        Phaser phaser = new Phaser(1);
        phaser.arrive();
        assertEquals(1, phaser.getPhase());
    }

    /**
     * arriveAndDeregister does not wait for others to arrive at barrier
     */
    public void testArrive2() throws InterruptedException {
        final Phaser phaser = new Phaser(1);
        phaser.register();
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 10; i++)
            phaser.register();
            threads.add(newStartedThread(new CheckedRunnable() {
                public void realRun() throws InterruptedException {
                    Thread.sleep(SMALL_DELAY_MS);
                    phaser.arriveAndDeregister();
                }}));

        phaser.arrive();
        assertTrue(threads.get(0).isAlive());
        assertFalse(phaser.isTerminated());
        for (Thread thread : threads)
            thread.join();
    }

    /**
     * arrive() returns a negative number if the Phaser is terminated
     */
    public void testArrive3() {
        Phaser phaser = new Phaser(1);
        phaser.forceTermination();
        assertTrue(phaser.arrive() < 0);
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
     * arriveAndDeregister deregisters reduces the number of arrived parties
     */
    public void testArriveAndDergeister2() {
        final Phaser phaser = new Phaser(1);
        phaser.register();
        phaser.arrive();
        int p = phaser.getArrivedParties();
        assertEquals(1, p);
        phaser.arriveAndDeregister();
        assertTrue(phaser.getArrivedParties() < p);
    }

    /**
     * arriveAndDeregister arrives to the barrier on a phaser with a parent and
     * when a deregistration occurs and causes the phaser to have zero parties
     * its parent will be deregistered as well
     */
    public void testArriveAndDeregsiter3() {
        Phaser parent = new Phaser();
        Phaser root = new Phaser(parent);
        root.register();
        assertTrue(parent.getUnarrivedParties() > 0);
        assertTrue(root.getUnarrivedParties() > 0);
        root.arriveAndDeregister();
        assertEquals(0, parent.getUnarrivedParties());
        assertEquals(0, root.getUnarrivedParties());
        assertTrue(root.isTerminated() && parent.isTerminated());
    }

    /**
     * arriveAndDeregister deregisters one party from its parent when
     * the number of parties of root is zero after deregistration
     */
    public void testArriveAndDeregsiter4() {
        Phaser parent = new Phaser();
        Phaser root = new Phaser(parent);
        parent.register();
        root.register();
        int parentParties = parent.getUnarrivedParties();
        root.arriveAndDeregister();
        assertEquals(parentParties - 1, parent.getUnarrivedParties());
    }

    /**
     * arriveAndDeregister deregisters one party from its parent when
     * the number of parties of root is nonzero after deregistration.
     */
    public void testArriveAndDeregister5() {
        Phaser parent = new Phaser();
        Phaser child = new Phaser(parent);
        Phaser root = new Phaser(child);
        assertTrue(parent.getUnarrivedParties() > 0);
        assertTrue(child.getUnarrivedParties() > 0);
        root.register();
        root.arriveAndDeregister();
        assertEquals(0, parent.getUnarrivedParties());
        assertEquals(0, child.getUnarrivedParties());
        assertTrue(root.isTerminated());
    }

    /**
     * arriveAndDeregister returns the phase in which it leaves the
     * phaser in after deregistration
     */
    public void testArriveAndDeregister6() throws InterruptedException {
        final Phaser phaser = new Phaser(2);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                sleepTillInterrupted(SHORT_DELAY_MS);
                phaser.arrive();
            }});
        phaser.arriveAndAwaitAdvance();
        int phase = phaser.arriveAndDeregister();
        assertEquals(phase, phaser.getPhase());
        t.join();
    }

    /**
     * awaitAdvance succeeds upon advance
     */
    public void testAwaitAdvance1() {
        final Phaser phaser = new Phaser(1);
        phaser.awaitAdvance(phaser.arrive());
    }

    /**
     * awaitAdvance with a negative parameter will return without affecting the
     * phaser
     */
    public void testAwaitAdvance2() {
        Phaser phaser = new Phaser();
        phaser.awaitAdvance(-1);
    }

    /**
     * awaitAdvance while waiting does not abort on interrupt.
     */
    public void testAwaitAdvance3() throws InterruptedException {
        final Phaser phaser = new Phaser();
        phaser.register();

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                phaser.register();
                sleepTillInterrupted(LONG_DELAY_MS);
                phaser.awaitAdvance(phaser.arrive());
            }});
        Thread.sleep(SMALL_DELAY_MS);
        t.interrupt();
        Thread.sleep(SMALL_DELAY_MS);
        phaser.arrive();
        assertFalse(t.isInterrupted());
        t.join();
    }

    /**
     * awaitAdvance atomically waits for all parties within the same phase to
     * complete before continuing
     */
    public void testAwaitAdvance4() throws InterruptedException {
        final Phaser phaser = new Phaser(4);
        final AtomicInteger phaseCount = new AtomicInteger(0);
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 4; i++) {
            threads.add(newStartedThread(new CheckedRunnable() {
                public void realRun() {
                    int phase = phaser.arrive();
                    phaseCount.incrementAndGet();
                    sleepTillInterrupted(SMALL_DELAY_MS);
                    phaser.awaitAdvance(phase);
                    assertEquals(phaseCount.get(), 4);
                }}));
        }
        for (Thread thread : threads)
            thread.join();
    }

    /**
     * awaitAdvance returns the current phase
     */
    public void testAwaitAdvance5() throws InterruptedException {
        final Phaser phaser = new Phaser(1);
        int phase = phaser.awaitAdvance(phaser.arrive());
        assertEquals(phase, phaser.getPhase());
        phaser.register();
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 8; i++) {
            threads.add(newStartedThread(new CheckedRunnable() {
                public void realRun() {
                    sleepTillInterrupted(SHORT_DELAY_MS);
                    phaser.arrive();
                }}));
            phase = phaser.awaitAdvance(phaser.arrive());
            assertEquals(phase, phaser.getPhase());
        }
        for (Thread thread : threads)
            thread.join();
    }

    /**
     * awaitAdvance returns when the phaser is externally terminated
     */
    public void testAwaitAdvance6() throws InterruptedException {
        final Phaser phaser = new Phaser(3);
        /*
         * Start new thread. This thread waits a small amount of time
         * and waits for the other two parties to arrive.  The party
         * in the main thread arrives quickly so at best this thread
         * waits for the second thread's party to arrive
         */
        Thread t1 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                sleepTillInterrupted(SMALL_DELAY_MS);
                int phase = phaser.awaitAdvance(phaser.arrive());
                /*
                 * This point is reached when force termination is called in which phase = -1
                 */
                assertTrue(phase < 0);
                assertTrue(phaser.isTerminated());
            }});
        /*
         * This thread will cause the first thread run to wait, in doing so
         * the main thread will force termination in which the first thread
         * should exit peacefully as this one
         */
        Thread t2 = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                sleepTillInterrupted(MEDIUM_DELAY_MS);
                int p1 = phaser.arrive();
                int phase = phaser.awaitAdvance(p1);
                assertTrue(phase < 0);
                assertTrue(phaser.isTerminated());
            }});

        phaser.arrive();
        phaser.forceTermination();
        t1.join();
        t2.join();
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
        Thread th = newStartedThread(new CheckedRunnable() {
            public void realRun() {
                phaser.arriveAndAwaitAdvance();
            }});

        Thread.sleep(SMALL_DELAY_MS);
        th.interrupt();
        Thread.sleep(SMALL_DELAY_MS);
        phaser.arrive();
        assertFalse(th.isInterrupted());
        th.join();
    }

    /**
     * arriveAndAwaitAdvance waits for all threads to arrive, the
     * number of arrived parties is the same number that is accounted
     * for when the main thread awaitsAdvance
     */
    public void testArriveAndAwaitAdvance3() throws InterruptedException {
        final Phaser phaser = new Phaser(1);
        final List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < 3; i++) {
            threads.add(newStartedThread(new CheckedRunnable() {
                    public void realRun() throws InterruptedException {
                        phaser.register();
                        phaser.arriveAndAwaitAdvance();
                    }}));
        }
        Thread.sleep(MEDIUM_DELAY_MS);
        assertEquals(phaser.getArrivedParties(), 3);
        phaser.arriveAndAwaitAdvance();
        for (Thread thread : threads)
            thread.join();
    }

}
