/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Other contributors include John Vint
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import junit.framework.Test;
import junit.framework.TestSuite;

public class LinkedTransferQueueTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(LinkedTransferQueueTest.class);
    }

    /**
     * Constructor builds new queue with size being zero and empty
     * being true
     */
    public void testConstructor1() {
        assertEquals(0, new LinkedTransferQueue().size());
        assertTrue(new LinkedTransferQueue().isEmpty());
    }

    /**
     * Initializing constructor with null collection throws
     * NullPointerException
     */
    public void testConstructor2() {
        try {
            new LinkedTransferQueue(null);
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * Initializing from Collection of null elements throws
     * NullPointerException
     */
    public void testConstructor3() {
        try {
            Integer[] ints = new Integer[SIZE];
            new LinkedTransferQueue(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * Initializing constructor with a collection containing some null elements
     * throws NullPointerException
     */
    public void testConstructor4() {
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE - 1; ++i) {
                ints[i] = i;
            }
            new LinkedTransferQueue(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * Queue contains all elements of the collection it is initialized by
     */
    public void testConstructor5() {
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i) {
                ints[i] = i;
            }
            LinkedTransferQueue q
                = new LinkedTransferQueue(Arrays.asList(ints));
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(ints[i], q.poll());
            }
        } finally {
        }
    }

    /**
     * Remaining capacity never decrease nor increase on add or remove
     */
    public void testRemainingCapacity() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
            assertEquals(SIZE - i, q.size());
            q.remove();
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
            assertEquals(i, q.size());
            q.add(i);
        }
    }

    /**
     * offer(null) throws NullPointerException
     */
    public void testOfferNull() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            q.offer(null);
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * add(null) throws NullPointerException
     */
    public void testAddNull() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            q.add(null);
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * addAll(null) throws NullPointerException
     */
    public void testAddAll1() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            q.addAll(null);
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * addAll(this) throws IllegalArgumentException
     */
    public void testAddAllSelf() {
        try {
            LinkedTransferQueue q = populatedQueue(SIZE);
            q.addAll(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {
        }
    }

    /**
     * addAll of a collection with null elements throws NullPointerException
     */
    public void testAddAll2() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            Integer[] ints = new Integer[SIZE];
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * addAll of a collection with any null elements throws
     * NullPointerException after possibly adding some elements
     */
    public void testAddAll3() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE - 1; ++i) {
                ints[i] = i;
            }
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * Queue contains all elements, in traversal order, of successful addAll
     */
    public void testAddAll5() {
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE; ++i) {
            ints[i] = i;
        }
        LinkedTransferQueue q = new LinkedTransferQueue();
        assertFalse(q.addAll(Arrays.asList(empty)));
        assertTrue(q.addAll(Arrays.asList(ints)));
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(ints[i], q.poll());
        }
    }

    /**
     * put(null) throws NullPointerException
     */
    public void testPutNull() throws InterruptedException {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            q.put(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * all elements successfully put are contained
     */
    public void testPut() {
        LinkedTransferQueue<Integer> q = new LinkedTransferQueue<Integer>();
        for (int i = 0; i < SIZE; ++i) {
            q.put(i);
            assertTrue(q.contains(i));
        }
        assertEquals(q.size(), SIZE);
    }

    /**
     * take retrieves elements in FIFO order
     */
    public void testTake() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.take());
        }
    }

    /**
     * take blocks interruptibly when empty
     */
    public void testTakeFromEmpty() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        Thread t = new Thread(new CheckedInterruptedRunnable() {
            void realRun() throws InterruptedException {
                q.take();
            }});
        t.start();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        t.join();
    }

    /**
     * Take removes existing elements until empty, then blocks interruptibly
     */
    public void testBlockingTake() throws InterruptedException {
        Thread t = new Thread(new CheckedInterruptedRunnable() {
            void realRun() throws InterruptedException {
                LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
                for (int i = 0; i < SIZE; ++i) {
                    threadAssertEquals(i, (int) q.take());
                }
                q.take();
            }});
        t.start();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        t.join();
    }

    /**
     * poll succeeds unless empty
     */
    public void testPoll() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.poll());
        }
        assertNull(q.poll());
    }

    /**
     * timed pool with zero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll0() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.poll(0, MILLISECONDS));
        }
        assertNull(q.poll(0, MILLISECONDS));
    }

    /**
     * timed pool with nonzero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.poll(SHORT_DELAY_MS, MILLISECONDS));
        }
        assertNull(q.poll(SHORT_DELAY_MS, MILLISECONDS));
    }

    /**
     * Interrupted timed poll throws InterruptedException instead of
     * returning timeout status
     */
    public void testInterruptedTimedPoll() throws InterruptedException {
        Thread t = new Thread(new CheckedInterruptedRunnable() {
            void realRun() throws InterruptedException {
                LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
                for (int i = 0; i < SIZE; ++i) {
                    threadAssertEquals(i, (int) q.poll(SHORT_DELAY_MS,
                                                       MILLISECONDS));
                }
                q.poll(SHORT_DELAY_MS, MILLISECONDS);
            }});
        t.start();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        t.join();
    }

    /**
     * timed poll before a delayed offer fails; after offer succeeds;
     * on interruption throws
     */
    public void testTimedPollWithOffer() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        Thread t = new Thread(new CheckedInterruptedRunnable() {
            void realRun() throws InterruptedException {
                threadAssertNull(q.poll(SHORT_DELAY_MS, MILLISECONDS));
                q.poll(LONG_DELAY_MS, MILLISECONDS);
                q.poll(LONG_DELAY_MS, MILLISECONDS);
            }});
        t.start();
        Thread.sleep(SMALL_DELAY_MS);
        assertTrue(q.offer(zero, SHORT_DELAY_MS, MILLISECONDS));
        t.interrupt();
        t.join();
    }

    /**
     * peek returns next element, or null if empty
     */
    public void testPeek() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.peek());
            assertEquals(i, (int) q.poll());
            assertTrue(q.peek() == null ||
                       i != (int) q.peek());
        }
        assertNull(q.peek());
    }

    /**
     * element returns next element, or throws NoSuchElementException if empty
     */
    public void testElement() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.element());
            assertEquals(i, (int) q.poll());
        }
        try {
            q.element();
            shouldThrow();
        } catch (NoSuchElementException success) {
        }
    }

    /**
     * remove removes next element, or throws NoSuchElementException if empty
     */
    public void testRemove() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, (int) q.remove());
        }
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success) {
        }
    }

    /**
     * remove(x) removes x and returns true if present
     */
    public void testRemoveElement() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        for (int i = 1; i < SIZE; i += 2) {
            assertTrue(q.remove(i));
        }
        for (int i = 0; i < SIZE; i += 2) {
            assertTrue(q.remove(i));
            assertFalse(q.remove(i + 1));
        }
        assertTrue(q.isEmpty());
    }

    /**
     * An add following remove(x) succeeds
     */
    public void testRemoveElementAndAdd() throws InterruptedException {
        LinkedTransferQueue q = new LinkedTransferQueue();
        assertTrue(q.add(one));
        assertTrue(q.add(two));
        assertTrue(q.remove(one));
        assertTrue(q.remove(two));
        assertTrue(q.add(three));
        assertTrue(q.take() != null);
    }

    /**
     * contains(x) reports true when elements added but not yet removed
     */
    public void testContains() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(i));
            assertEquals(i, (int) q.poll());
            assertFalse(q.contains(i));
        }
    }

    /**
     * clear removes all elements
     */
    public void testClear() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        q.add(one);
        assertFalse(q.isEmpty());
        assertTrue(q.contains(one));
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     * containsAll(c) is true when c contains a subset of elements
     */
    public void testContainsAll() {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        LinkedTransferQueue<Integer> p = new LinkedTransferQueue<Integer>();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(i);
        }
        assertTrue(p.containsAll(q));
    }

    /**
     * retainAll(c) retains only those elements of c and reports true
     * if changed
     */
    public void testRetainAll() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        LinkedTransferQueue p = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            boolean changed = q.retainAll(p);
            if (i == 0) {
                assertFalse(changed);
            } else {
                assertTrue(changed);
            }
            assertTrue(q.containsAll(p));
            assertEquals(SIZE - i, q.size());
            p.remove();
        }
    }

    /**
     * removeAll(c) removes only those elements of c and reports true
     * if changed
     */
    public void testRemoveAll() {
        for (int i = 1; i < SIZE; ++i) {
            LinkedTransferQueue q = populatedQueue(SIZE);
            LinkedTransferQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE - i, q.size());
            for (int j = 0; j < i; ++j) {
                assertFalse(q.contains(p.remove()));
            }
        }
    }

    /**
     * toArray contains all elements
     */
    public void testToArray() throws InterruptedException {
        LinkedTransferQueue q = populatedQueue(SIZE);
        Object[] o = q.toArray();
        for (int i = 0; i < o.length; i++) {
            assertEquals(o[i], q.take());
        }
    }

    /**
     * toArray(a) contains all elements
     */
    public void testToArray2() throws InterruptedException {
        LinkedTransferQueue<Integer> q = populatedQueue(SIZE);
        Integer[] ints = new Integer[SIZE];
        ints = q.toArray(ints);
        for (int i = 0; i < ints.length; i++) {
            assertEquals(ints[i], q.take());
        }
    }

    /**
     * toArray(null) throws NullPointerException
     */
    public void testToArray_BadArg() {
        try {
            LinkedTransferQueue q = populatedQueue(SIZE);
            Object o[] = q.toArray(null);
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * toArray with incompatible array type throws CCE
     */
    public void testToArray1_BadArg() {
        try {
            LinkedTransferQueue q = populatedQueue(SIZE);
            Object o[] = q.toArray(new String[10]);
            shouldThrow();
        } catch (ArrayStoreException success) {
        }
    }

    /**
     * iterator iterates through all elements
     */
    public void testIterator() throws InterruptedException {
        LinkedTransferQueue q = populatedQueue(SIZE);
        Iterator it = q.iterator();
        while (it.hasNext()) {
            assertEquals(it.next(), q.take());
        }
    }

    /**
     * iterator.remove removes current element
     */
    public void testIteratorRemove() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        q.add(two);
        q.add(one);
        q.add(three);

        Iterator it = q.iterator();
        it.next();
        it.remove();

        it = q.iterator();
        assertEquals(it.next(), one);
        assertEquals(it.next(), three);
        assertFalse(it.hasNext());
    }

    /**
     * iterator ordering is FIFO
     */
    public void testIteratorOrdering() {
        final LinkedTransferQueue<Integer> q
            = new LinkedTransferQueue<Integer>();
        assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        q.add(one);
        q.add(two);
        q.add(three);
        assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        int k = 0;
        for (Integer n : q) {
            assertEquals(++k, (int) n);
        }
        assertEquals(3, k);
    }

    /**
     * Modifications do not cause iterators to fail
     */
    public void testWeaklyConsistentIteration() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        q.add(one);
        q.add(two);
        q.add(three);
        for (Iterator it = q.iterator(); it.hasNext();) {
            q.remove();
            it.next();
        }
        assertEquals(0, q.size());
    }

    /**
     * toString contains toStrings of elements
     */
    public void testToString() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }

    /**
     * offer transfers elements across Executor tasks
     */
    public void testOfferInExecutor() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        q.add(one);
        q.add(two);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new CheckedRunnable() {
            void realRun() {
                threadAssertTrue(q.offer(three, MEDIUM_DELAY_MS,
                                         MILLISECONDS));
            }});

        executor.execute(new CheckedRunnable() {
            void realRun() throws InterruptedException {
                Thread.sleep(SMALL_DELAY_MS);
                threadAssertEquals(one, q.take());
            }});

        joinPool(executor);
    }

    /**
     * poll retrieves elements across Executor threads
     */
    public void testPollInExecutor() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new CheckedRunnable() {
            void realRun() throws InterruptedException {
                threadAssertNull(q.poll());
                threadAssertTrue(null != q.poll(MEDIUM_DELAY_MS,
                                                MILLISECONDS));
                threadAssertTrue(q.isEmpty());
            }});

        executor.execute(new CheckedRunnable() {
            void realRun() throws InterruptedException {
                Thread.sleep(SMALL_DELAY_MS);
                q.put(one);
            }});

        joinPool(executor);
    }

    /**
     * A deserialized serialized queue has same elements in same order
     */
    public void testSerialization() throws Exception {
        LinkedTransferQueue q = populatedQueue(SIZE);

        ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
        ObjectOutputStream out
            = new ObjectOutputStream(new BufferedOutputStream(bout));
        out.writeObject(q);
        out.close();

        ByteArrayInputStream bin
            = new ByteArrayInputStream(bout.toByteArray());
        ObjectInputStream in
            = new ObjectInputStream(new BufferedInputStream(bin));
        LinkedTransferQueue r = (LinkedTransferQueue) in.readObject();

        assertEquals(q.size(), r.size());
        while (!q.isEmpty()) {
            assertEquals(q.remove(), r.remove());
        }
    }

    /**
     * drainTo(null) throws NullPointerException
     */
    public void testDrainToNull() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        try {
            q.drainTo(null);
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * drainTo(this) throws IllegalArgumentException
     */
    public void testDrainToSelf() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        try {
            q.drainTo(q);
            shouldThrow();
        } catch (IllegalArgumentException success) {
        }
    }

    /**
     * drainTo(c) empties queue into another collection c
     */
    public void testDrainTo() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertEquals(q.size(), 0);
        assertEquals(l.size(), SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(l.get(i), i);
        }
        q.add(zero);
        q.add(one);
        assertFalse(q.isEmpty());
        assertTrue(q.contains(zero));
        assertTrue(q.contains(one));
        l.clear();
        q.drainTo(l);
        assertEquals(q.size(), 0);
        assertEquals(l.size(), 2);
        for (int i = 0; i < 2; ++i) {
            assertEquals(l.get(i), i);
        }
    }

    /**
     * drainTo empties full queue, unblocking a waiting put.
     */
    public void testDrainToWithActivePut() throws InterruptedException {
        final LinkedTransferQueue q = populatedQueue(SIZE);
        Thread t = new Thread(new CheckedRunnable() {
            void realRun() {
                q.put(SIZE + 1);
            }});
        t.start();
        ArrayList l = new ArrayList();
        q.drainTo(l);
        assertTrue(l.size() >= SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(l.get(i), i);
        }
        t.join();
        assertTrue(q.size() + l.size() >= SIZE);
    }

    /**
     * drainTo(null, n) throws NullPointerException
     */
    public void testDrainToNullN() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        try {
            q.drainTo(null, 0);
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * drainTo(this, n) throws IllegalArgumentException
     */
    public void testDrainToSelfN() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        try {
            q.drainTo(q, 0);
            shouldThrow();
        } catch (IllegalArgumentException success) {
        }
    }

    /**
     * drainTo(c, n) empties first max {n, size} elements of queue into c
     */
    public void testDrainToN() {
        LinkedTransferQueue q = new LinkedTransferQueue();
        for (int i = 0; i < SIZE + 2; ++i) {
            for (int j = 0; j < SIZE; j++) {
                assertTrue(q.offer(j));
            }
            ArrayList l = new ArrayList();
            q.drainTo(l, i);
            int k = (i < SIZE) ? i : SIZE;
            assertEquals(l.size(), k);
            assertEquals(q.size(), SIZE - k);
            for (int j = 0; j < k; ++j) {
                assertEquals(l.get(j), j);
            }
            while (q.poll() != null)
                ;
        }
    }

    /**
     * poll and take decrement the waiting consumer count
     */
    public void testWaitingConsumer() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        final ConsumerObserver waiting = new ConsumerObserver();
        new Thread(new CheckedRunnable() {
            void realRun() {
                threadAssertTrue(q.hasWaitingConsumer());
                waiting.setWaitingConsumer(q.getWaitingConsumerCount());
                threadAssertTrue(q.offer(new Object()));
            }}).start();
        assertTrue(q.poll(LONG_DELAY_MS, MILLISECONDS) != null);
        assertTrue(q.getWaitingConsumerCount()
                   < waiting.getWaitingConsumers());
    }

    /**
     * transfer(null) throws NullPointerException
     */
    public void testTransfer1() throws InterruptedException {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            q.transfer(null);
            shouldThrow();
        } catch (NullPointerException ex) {
        }
    }

    /**
     * transfer waits until a poll occurs. The transfered element
     * is returned by this associated poll.
     */
    public void testTransfer2() throws InterruptedException {
        final LinkedTransferQueue<Integer> q
            = new LinkedTransferQueue<Integer>();

        new Thread(new CheckedRunnable() {
            void realRun() throws InterruptedException {
                q.transfer(SIZE);
                threadAssertTrue(q.isEmpty());
            }}).start();

        Thread.sleep(SHORT_DELAY_MS);
        assertEquals(1, q.size());
        assertEquals(SIZE, (int) q.poll());
        assertTrue(q.isEmpty());
    }

    /**
     * transfer waits until a poll occurs, and then transfers in fifo order
     */
    public void testTransfer3() throws InterruptedException {
        final LinkedTransferQueue<Integer> q
            = new LinkedTransferQueue<Integer>();

        Thread first = new Thread(new CheckedRunnable() {
            void realRun() throws InterruptedException {
                Integer i = SIZE + 1;
                q.transfer(i);
                threadAssertTrue(!q.contains(i));
                threadAssertEquals(1, q.size());
            }});
        first.start();

        Thread interruptedThread = new Thread(new CheckedInterruptedRunnable() {
            void realRun() throws InterruptedException {
                while (q.size() == 0)
                    Thread.yield();
                q.transfer(SIZE);
            }});
        interruptedThread.start();

        while (q.size() < 2)
            Thread.yield();
        assertEquals(2, q.size());
        assertEquals(SIZE + 1, (int) q.poll());
        first.join();
        assertEquals(1, q.size());
        interruptedThread.interrupt();
        interruptedThread.join();
        assertEquals(0, q.size());
        assertTrue(q.isEmpty());
    }

    /**
     * transfer waits until a poll occurs, at which point the polling
     * thread returns the element
     */
    public void testTransfer4() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        new Thread(new CheckedRunnable() {
            void realRun() throws InterruptedException {
                q.transfer(four);
                threadAssertFalse(q.contains(four));
                threadAssertEquals(three, q.poll());
            }}).start();
        Thread.sleep(MEDIUM_DELAY_MS);
        assertTrue(q.offer(three));
        assertEquals(four, q.poll());
    }

    /**
     * transfer waits until a take occurs. The transfered element
     * is returned by this associated take.
     */
    public void testTransfer5() throws InterruptedException {
        final LinkedTransferQueue<Integer> q
            = new LinkedTransferQueue<Integer>();

        new Thread(new CheckedRunnable() {
            void realRun() throws InterruptedException {
                q.transfer(SIZE);
                threadAssertTrue(q.isEmpty());
            }}).start();

        Thread.sleep(SHORT_DELAY_MS);
        assertEquals(SIZE, (int) q.take());
        assertTrue(q.isEmpty());
    }

    /**
     * tryTransfer(null) throws NullPointerException
     */
    public void testTryTransfer1() {
        try {
            final LinkedTransferQueue q = new LinkedTransferQueue();
            q.tryTransfer(null);
            shouldThrow();
        } catch (NullPointerException ex) {
        }
    }

    /**
     * tryTransfer returns false and does not enqueue if there are no
     * consumers waiting to poll or take.
     */
    public void testTryTransfer2() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertFalse(q.tryTransfer(new Object()));
        assertFalse(q.hasWaitingConsumer());
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
    }

    /**
     * If there is a consumer waiting in timed poll, tryTransfer
     * returns true while successfully transfering object.
     */
    public void testTryTransfer3() throws InterruptedException {
        final Object hotPotato = new Object();
        final LinkedTransferQueue q = new LinkedTransferQueue();
        new Thread(new CheckedRunnable() {
            void realRun() {
                while (! q.hasWaitingConsumer())
                    Thread.yield();
                threadAssertTrue(q.hasWaitingConsumer());
                threadAssertTrue(q.isEmpty());
                threadAssertTrue(q.size() == 0);
                threadAssertTrue(q.tryTransfer(hotPotato));
            }}).start();
        assertTrue(q.poll(LONG_DELAY_MS, MILLISECONDS) == hotPotato);
        assertTrue(q.isEmpty());
    }

    /**
     * If there is a consumer waiting in take, tryTransfer returns
     * true while successfully transfering object.
     */
    public void testTryTransfer4() throws InterruptedException {
        final Object hotPotato = new Object();
        final LinkedTransferQueue q = new LinkedTransferQueue();
        new Thread(new CheckedRunnable() {
            void realRun() {
                while (! q.hasWaitingConsumer())
                    Thread.yield();
                threadAssertTrue(q.hasWaitingConsumer());
                threadAssertTrue(q.isEmpty());
                threadAssertTrue(q.size() == 0);
                threadAssertTrue(q.tryTransfer(hotPotato));
            }}).start();
        assertTrue(q.take() == hotPotato);
        assertTrue(q.isEmpty());
    }

    /**
     * tryTransfer waits the amount given if interrupted, and
     * throws interrupted exception
     */
    public void testTryTransfer5() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        Thread toInterrupt = new Thread(new CheckedInterruptedRunnable() {
            void realRun() throws InterruptedException {
                q.tryTransfer(new Object(), LONG_DELAY_MS, MILLISECONDS);
            }});
        toInterrupt.start();
        Thread.sleep(SMALL_DELAY_MS);
        toInterrupt.interrupt();
    }

    /**
     * tryTransfer gives up after the timeout and return false
     */
    public void testTryTransfer6() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        new Thread(new CheckedRunnable() {
            void realRun() throws InterruptedException {
                threadAssertFalse
                    (q.tryTransfer(new Object(),
                                   SHORT_DELAY_MS, MILLISECONDS));
            }}).start();
        Thread.sleep(LONG_DELAY_MS);
        assertTrue(q.isEmpty());
    }

    /**
     * tryTransfer waits for any elements previously in to be removed
     * before transfering to a poll or take
     */
    public void testTryTransfer7() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertTrue(q.offer(four));
        new Thread(new CheckedRunnable() {
            void realRun() throws InterruptedException {
                threadAssertTrue(q.tryTransfer(five,
                                               LONG_DELAY_MS, MILLISECONDS));
                threadAssertTrue(q.isEmpty());
            }}).start();
        Thread.sleep(SHORT_DELAY_MS);
        assertEquals(2, q.size());
        assertEquals(four, q.poll());
        assertEquals(five, q.poll());
        assertTrue(q.isEmpty());
    }

    /**
     * tryTransfer attempts to enqueue into the q and fails returning
     * false not enqueueing and the successive poll is null
     */
    public void testTryTransfer8() throws InterruptedException {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        assertTrue(q.offer(four));
        assertEquals(1, q.size());
        assertFalse(q.tryTransfer(five, SHORT_DELAY_MS, MILLISECONDS));
        assertEquals(1, q.size());
        assertEquals(four, q.poll());
        threadAssertTrue(q.isEmpty());
        assertNull(q.poll());
    }

    private LinkedTransferQueue<Integer> populatedQueue(int n) {
        LinkedTransferQueue<Integer> q = new LinkedTransferQueue<Integer>();
        assertTrue(q.isEmpty());
        for (int i = 0; i < n; i++) {
            assertEquals(i, q.size());
            assertTrue(q.offer(i));
            assertEquals(Integer.MAX_VALUE, q.remainingCapacity());
        }
        assertFalse(q.isEmpty());
        return q;
    }

    private static class ConsumerObserver {

        private int waitingConsumers;

        private ConsumerObserver() {
        }

        private void setWaitingConsumer(int i) {
            this.waitingConsumers = i;
        }

        private int getWaitingConsumers() {
            return waitingConsumers;
        }
    }
}
