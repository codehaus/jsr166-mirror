package java.util.concurrent;

import java.util.Set;
import java.util.HashSet;
import java.util.ConcurrentModificationException;

import junit.framework.TestCase;

/**
 * Tests the PriorityBlockingQueue implementation.
 */
public class PriorityBlockingQueueTest extends TestCase {

    public void testOrdering () {

        final PriorityBlockingQueue<Integer> q = new PriorityBlockingQueue<Integer>(3);

        q.add(1);
        q.add(2);
        q.add(3);

        int k = 0;
        for (Integer i : q) {
            assertEquals("items should come out in order", ++k, i);
        }

        assertEquals("should go through 3 elements", 3, k);
    }

    public void testFastFail () {

        final PriorityBlockingQueue<Integer> q = new PriorityBlockingQueue<Integer>(3);

        q.add(1);
        q.add(2);
        q.add(3);

        try {
            for (Integer i : q) {
                q.remove();
            }
            fail("fast-fail iteration; should get CME");
        }
        catch (ConcurrentModificationException e) {
        }
    }

    public void testOffer () {

        final PriorityBlockingQueue<Integer> q = new PriorityBlockingQueue<Integer>(2);

        q.add(1);
        q.add(2);

        Executor executor = Executors.newFixedThreadPool(2);

        executor.execute(new Runnable() {
            public void run() {
                //assertFalse("offer should be rejected", q.offer(3));
                try {
                    assertTrue("offer should be accepted", q.offer(3, 1000, TimeUnit.MILLISECONDS));
                }
                catch (IllegalMonitorStateException e) {
                    e.printStackTrace(System.err);
                    fail("illegal monitor state");
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
