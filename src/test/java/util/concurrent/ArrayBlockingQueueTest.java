package java.util.concurrent;

import java.util.Set;
import java.util.HashSet;
import java.util.ConcurrentModificationException;

import junit.framework.TestCase;

/**
 * Tests the ArrayBlockingQueue implementation.
 */
public class ArrayBlockingQueueTest extends TestCase {

    public void testCapacity () {

        ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<Integer>(2);

        assertEquals("should have room for 2", 2, q.remainingCapacity());

        q.add(1);
        q.add(2);

        assertEquals("queue should be full", 0, q.remainingCapacity());

        assertFalse("offer should be rejected", q.offer(3));
    }

    public void testOrdering () {

        final ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<Integer>(3);

        q.add(1);
        q.add(2);
        q.add(3);

        assertEquals("queue should be full", 0, q.remainingCapacity());

        int k = 0;
        for (Integer i : q) {
            assertEquals("items should come out in order", ++k, i);
        }

        assertEquals("should go through 3 elements", 3, k);

        try {
            for (Integer i : q) {
                q.remove();
            }
            fail("should get CME");
        }
        catch (ConcurrentModificationException e) {
        }
    }

    public void testOffer () {

        final ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<Integer>(2);

        q.add(1);
        q.add(2);

        Executor executor = Executors.newFixedThreadPool(2);

        executor.execute(new Runnable() {
            public void run() {
                assertFalse("offer should be rejected", q.offer(3));
                try {
                    assertTrue("offer should be accepted", q.offer(3, 1000, TimeUnit.MILLISECONDS));
                }
                catch (IllegalMonitorStateException e) {
                    e.printStackTrace(System.err);
                    fail("illegal monitor state");
                }
                catch (InterruptedException e) {
                    fail("should not be interrupted");
                }
            }
        });

        executor.execute(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(2000);
                    assertEquals("first item in queue should be 1", 1, q.take());
                }
                catch (InterruptedException e) {
                    fail("should not be interrupted");
                }
            }
        });
    }
}
