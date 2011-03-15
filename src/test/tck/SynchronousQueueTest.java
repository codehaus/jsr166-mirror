/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.io.*;

public class SynchronousQueueTest extends JSR166TestCase {

    public static class Fair extends BlockingQueueTest {
        protected BlockingQueue emptyCollection() {
            return new SynchronousQueue(true);
        }
    }

    public static class NonFair extends BlockingQueueTest {
        protected BlockingQueue emptyCollection() {
            return new SynchronousQueue(false);
        }
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return newTestSuite(SynchronousQueueTest.class,
                            new Fair().testSuite(),
                            new NonFair().testSuite());
    }

    /**
     * Any SynchronousQueue is both empty and full
     */
    public void testEmptyFull(SynchronousQueue q) {
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(0, q.remainingCapacity());
        assertFalse(q.offer(zero));
    }

    /**
     * A non-fair SynchronousQueue is both empty and full
     */
    public void testEmptyFull() {
        testEmptyFull(new SynchronousQueue());
    }

    /**
     * A fair SynchronousQueue is both empty and full
     */
    public void testFairEmptyFull() {
        testEmptyFull(new SynchronousQueue(true));
    }

    /**
     * offer(null) throws NPE
     */
    public void testOfferNull() {
        try {
            SynchronousQueue q = new SynchronousQueue();
            q.offer(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * add(null) throws NPE
     */
    public void testAddNull() {
        try {
            SynchronousQueue q = new SynchronousQueue();
            q.add(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * offer fails if no active taker
     */
    public void testOffer() {
        SynchronousQueue q = new SynchronousQueue();
        assertFalse(q.offer(one));
    }

    /**
     * add throws ISE if no active taker
     */
    public void testAdd() {
        try {
            SynchronousQueue q = new SynchronousQueue();
            assertEquals(0, q.remainingCapacity());
            q.add(one);
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * addAll(null) throws NPE
     */
    public void testAddAll1() {
        try {
            SynchronousQueue q = new SynchronousQueue();
            q.addAll(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addAll(this) throws IAE
     */
    public void testAddAllSelf() {
        try {
            SynchronousQueue q = new SynchronousQueue();
            q.addAll(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * addAll of a collection with null elements throws NPE
     */
    public void testAddAll2() {
        try {
            SynchronousQueue q = new SynchronousQueue();
            Integer[] ints = new Integer[1];
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * addAll throws ISE if no active taker
     */
    public void testAddAll4() {
        try {
            SynchronousQueue q = new SynchronousQueue();
            Integer[] ints = new Integer[1];
            for (int i = 0; i < 1; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * put(null) throws NPE
     */
    public void testPutNull() throws InterruptedException {
        try {
            SynchronousQueue q = new SynchronousQueue();
            q.put(null);
            shouldThrow();
        } catch (NullPointerException success) {}
     }

    /**
     * put blocks interruptibly if no active taker
     */
    public void testBlockingPut() throws InterruptedException {
        Thread t = new Thread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                SynchronousQueue q = new SynchronousQueue();
                q.put(zero);
            }});

        t.start();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        t.join();
    }

    /**
     * put blocks waiting for take
     */
    public void testPutWithTake() throws InterruptedException {
        final SynchronousQueue q = new SynchronousQueue();
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                int added = 0;
                try {
                    while (true) {
                        q.put(added);
                        ++added;
                    }
                } catch (InterruptedException success) {
                    assertEquals(1, added);
                }
            }});

        t.start();
        Thread.sleep(SHORT_DELAY_MS);
        assertEquals(0, q.take());
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        t.join();
    }

    /**
     * timed offer times out if elements not taken
     */
    public void testTimedOffer(final SynchronousQueue q)
            throws InterruptedException {
        final CountDownLatch pleaseInterrupt = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long t0 = System.nanoTime();
                assertFalse(q.offer(new Object(), SHORT_DELAY_MS, MILLISECONDS));
                assertTrue(millisElapsedSince(t0) >= SHORT_DELAY_MS);
                pleaseInterrupt.countDown();
                t0 = System.nanoTime();
                try {
                    q.offer(new Object(), LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertTrue(millisElapsedSince(t0) < MEDIUM_DELAY_MS);
            }});

        assertTrue(pleaseInterrupt.await(MEDIUM_DELAY_MS, MILLISECONDS));
        t.interrupt();
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * timed offer times out if elements not taken
     */
    public void testTimedOffer() throws InterruptedException {
        testTimedOffer(new SynchronousQueue());
    }

    /**
     * timed offer times out if elements not taken
     */
    public void testFairTimedOffer() throws InterruptedException {
        testTimedOffer(new SynchronousQueue(true));
    }

    /**
     * put blocks interruptibly if no active taker
     */
    public void testFairBlockingPut() throws InterruptedException {
        Thread t = new Thread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                SynchronousQueue q = new SynchronousQueue(true);
                q.put(zero);
            }});

        t.start();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        t.join();
    }

    /**
     * put blocks waiting for take
     */
    public void testFairPutWithTake() throws InterruptedException {
        final SynchronousQueue q = new SynchronousQueue(true);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                int added = 0;
                try {
                    while (true) {
                        q.put(added);
                        ++added;
                    }
                } catch (InterruptedException success) {
                    assertEquals(1, added);
                }
            }});

        t.start();
        Thread.sleep(SHORT_DELAY_MS);
        assertEquals(0, q.take());
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        t.join();
    }

    /**
     * take blocks interruptibly when empty
     */
    public void testFairTakeFromEmpty() throws InterruptedException {
        final SynchronousQueue q = new SynchronousQueue(true);
        Thread t = new Thread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                q.take();
            }});

        t.start();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        t.join();
    }

    /**
     * poll return null if no active putter
     */
    public void testPoll() {
        SynchronousQueue q = new SynchronousQueue();
        assertNull(q.poll());
    }

    /**
     * timed poll with zero timeout times out if no active putter
     */
    public void testTimedPoll0() throws InterruptedException {
        SynchronousQueue q = new SynchronousQueue();
        assertNull(q.poll(0, MILLISECONDS));
    }

    /**
     * timed poll with nonzero timeout times out if no active putter
     */
    public void testTimedPoll() throws InterruptedException {
        SynchronousQueue q = new SynchronousQueue();
        long t0 = System.nanoTime();
        assertNull(q.poll(SHORT_DELAY_MS, MILLISECONDS));
        assertTrue(millisElapsedSince(t0) >= SHORT_DELAY_MS);
    }

    /**
     * Interrupted timed poll throws InterruptedException instead of
     * returning timeout status
     */
    public void testInterruptedTimedPoll(final SynchronousQueue q)
            throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long t0 = System.nanoTime();
                threadStarted.countDown();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertTrue(millisElapsedSince(t0) >= SHORT_DELAY_MS);
                assertTrue(millisElapsedSince(t0) < MEDIUM_DELAY_MS);
            }});

        threadStarted.await();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * Interrupted timed poll throws InterruptedException instead of
     * returning timeout status
     */
    public void testInterruptedTimedPoll() throws InterruptedException {
        testInterruptedTimedPoll(new SynchronousQueue());
    }

    /**
     * Interrupted timed poll throws InterruptedException instead of
     * returning timeout status
     */
    public void testFairInterruptedTimedPoll() throws InterruptedException {
        testInterruptedTimedPoll(new SynchronousQueue(true));
    }

    /**
     * timed poll before a delayed offer times out, returning null;
     * after offer succeeds; on interruption throws
     */
    public void testFairTimedPollWithOffer() throws InterruptedException {
        final SynchronousQueue q = new SynchronousQueue(true);
        final CountDownLatch pleaseOffer = new CountDownLatch(1);
        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                long t0 = System.nanoTime();
                assertNull(q.poll(SHORT_DELAY_MS, MILLISECONDS));
                assertTrue(millisElapsedSince(t0) >= SHORT_DELAY_MS);

                pleaseOffer.countDown();
                t0 = System.nanoTime();
                assertSame(zero, q.poll(LONG_DELAY_MS, MILLISECONDS));
                assertTrue(millisElapsedSince(t0) < MEDIUM_DELAY_MS);

                t0 = System.nanoTime();
                try {
                    q.poll(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (InterruptedException success) {}
                assertTrue(millisElapsedSince(t0) < MEDIUM_DELAY_MS);
            }});

        assertTrue(pleaseOffer.await(MEDIUM_DELAY_MS, MILLISECONDS));
        long t0 = System.nanoTime();
        assertTrue(q.offer(zero, LONG_DELAY_MS, MILLISECONDS));
        assertTrue(millisElapsedSince(t0) < MEDIUM_DELAY_MS);

        t.interrupt();
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * peek() returns null if no active putter
     */
    public void testPeek() {
        SynchronousQueue q = new SynchronousQueue();
        assertNull(q.peek());
    }

    /**
     * element() throws NSEE if no active putter
     */
    public void testElement() {
        SynchronousQueue q = new SynchronousQueue();
        try {
            q.element();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * remove() throws NSEE if no active putter
     */
    public void testRemove() {
        SynchronousQueue q = new SynchronousQueue();
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * remove(x) returns false
     */
    public void testRemoveElement() {
        SynchronousQueue q = new SynchronousQueue();
        assertFalse(q.remove(zero));
        assertTrue(q.isEmpty());
    }

    /**
     * contains returns false
     */
    public void testContains() {
        SynchronousQueue q = new SynchronousQueue();
        assertFalse(q.contains(zero));
    }

    /**
     * clear ensures isEmpty
     */
    public void testClear() {
        SynchronousQueue q = new SynchronousQueue();
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll returns false unless empty
     */
    public void testContainsAll() {
        SynchronousQueue q = new SynchronousQueue();
        Integer[] empty = new Integer[0];
        assertTrue(q.containsAll(Arrays.asList(empty)));
        Integer[] ints = new Integer[1]; ints[0] = zero;
        assertFalse(q.containsAll(Arrays.asList(ints)));
    }

    /**
     * retainAll returns false
     */
    public void testRetainAll() {
        SynchronousQueue q = new SynchronousQueue();
        Integer[] empty = new Integer[0];
        assertFalse(q.retainAll(Arrays.asList(empty)));
        Integer[] ints = new Integer[1]; ints[0] = zero;
        assertFalse(q.retainAll(Arrays.asList(ints)));
    }

    /**
     * removeAll returns false
     */
    public void testRemoveAll() {
        SynchronousQueue q = new SynchronousQueue();
        Integer[] empty = new Integer[0];
        assertFalse(q.removeAll(Arrays.asList(empty)));
        Integer[] ints = new Integer[1]; ints[0] = zero;
        assertFalse(q.containsAll(Arrays.asList(ints)));
    }


    /**
     * toArray is empty
     */
    public void testToArray() {
        SynchronousQueue q = new SynchronousQueue();
        Object[] o = q.toArray();
        assertEquals(o.length, 0);
    }

    /**
     * toArray(a) is nulled at position 0
     */
    public void testToArray2() {
        SynchronousQueue q = new SynchronousQueue();
        Integer[] ints = new Integer[1];
        assertNull(ints[0]);
    }

    /**
     * toArray(null) throws NPE
     */
    public void testToArray_BadArg() {
        SynchronousQueue q = new SynchronousQueue();
        try {
            Object o[] = q.toArray(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }


    /**
     * iterator does not traverse any elements
     */
    public void testIterator() {
        SynchronousQueue q = new SynchronousQueue();
        Iterator it = q.iterator();
        assertFalse(it.hasNext());
        try {
            Object x = it.next();
            shouldThrow();
        } catch (NoSuchElementException success) {}
    }

    /**
     * iterator remove throws ISE
     */
    public void testIteratorRemove() {
        SynchronousQueue q = new SynchronousQueue();
        Iterator it = q.iterator();
        try {
            it.remove();
            shouldThrow();
        } catch (IllegalStateException success) {}
    }

    /**
     * toString returns a non-null string
     */
    public void testToString() {
        SynchronousQueue q = new SynchronousQueue();
        String s = q.toString();
        assertNotNull(s);
    }


    /**
     * offer transfers elements across Executor tasks
     */
    public void testOfferInExecutor() {
        final SynchronousQueue q = new SynchronousQueue();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertFalse(q.offer(one));
                assertTrue(q.offer(one, MEDIUM_DELAY_MS, MILLISECONDS));
                assertEquals(0, q.remainingCapacity());
            }});

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Thread.sleep(SMALL_DELAY_MS);
                assertSame(one, q.take());
            }});

        joinPool(executor);
    }

    /**
     * poll retrieves elements across Executor threads
     */
    public void testPollInExecutor() {
        final SynchronousQueue q = new SynchronousQueue();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                assertNull(q.poll());
                assertSame(one, q.poll(MEDIUM_DELAY_MS, MILLISECONDS));
                assertTrue(q.isEmpty());
            }});

        executor.execute(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Thread.sleep(SHORT_DELAY_MS);
                q.put(one);
            }});

        joinPool(executor);
    }

    /**
     * a deserialized serialized queue is usable
     */
    public void testSerialization() throws Exception {
        SynchronousQueue q = new SynchronousQueue();
        ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
        ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
        out.writeObject(q);
        out.close();

        ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
        SynchronousQueue r = (SynchronousQueue)in.readObject();
        assertEquals(q.size(), r.size());
        while (!q.isEmpty())
            assertEquals(q.remove(), r.remove());
    }

    /**
     * drainTo(null) throws NPE
     */
    public void testDrainToNull() {
        SynchronousQueue q = new SynchronousQueue();
        try {
            q.drainTo(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * drainTo(this) throws IAE
     */
    public void testDrainToSelf() {
        SynchronousQueue q = new SynchronousQueue();
        try {
            q.drainTo(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * drainTo(c) of empty queue doesn't transfer elements
     */
    public void testDrainTo() {
        SynchronousQueue q = new SynchronousQueue();
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertEquals(q.size(), 0);
        assertEquals(l.size(), 0);
    }

    /**
     * drainTo empties queue, unblocking a waiting put.
     */
    public void testDrainToWithActivePut() throws InterruptedException {
        final SynchronousQueue q = new SynchronousQueue();
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(new Integer(1));
            }});

        t.start();
        ArrayList l = new ArrayList();
        Thread.sleep(SHORT_DELAY_MS);
        q.drainTo(l);
        assertTrue(l.size() <= 1);
        if (l.size() > 0)
            assertEquals(l.get(0), new Integer(1));
        t.join();
        assertTrue(l.size() <= 1);
    }

    /**
     * drainTo(null, n) throws NPE
     */
    public void testDrainToNullN() {
        SynchronousQueue q = new SynchronousQueue();
        try {
            q.drainTo(null, 0);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * drainTo(this, n) throws IAE
     */
    public void testDrainToSelfN() {
        SynchronousQueue q = new SynchronousQueue();
        try {
            q.drainTo(q, 0);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * drainTo(c, n) empties up to n elements of queue into c
     */
    public void testDrainToN() throws InterruptedException {
        final SynchronousQueue q = new SynchronousQueue();
        Thread t1 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(one);
            }});

        Thread t2 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                q.put(two);
            }});

        t1.start();
        t2.start();
        ArrayList l = new ArrayList();
        Thread.sleep(SHORT_DELAY_MS);
        q.drainTo(l, 1);
        assertEquals(1, l.size());
        q.drainTo(l, 1);
        assertEquals(2, l.size());
        assertTrue(l.contains(one));
        assertTrue(l.contains(two));
        t1.join();
        t2.join();
    }

}
