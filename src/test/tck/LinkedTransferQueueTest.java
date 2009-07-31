
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
import junit.framework.Test;
import junit.framework.TestSuite;

public class LinkedTransferQueueTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(LinkedTransferQueueTest.class);
    }

    /*
     *Constructor builds new queue with size being zero and empty being true
     */
    public void testConstructor1() {
        assertEquals(0, new LinkedTransferQueue().size());
        assertTrue(new LinkedTransferQueue().isEmpty());
    }

    /*
     * Initizialing constructor with null collection throws NPE
     */
    public void testConstructor2() {
        try {
            new LinkedTransferQueue(null);
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * Initializing from Collection of null elements throws NPE
     */
    public void testConstructor3() {
        try {
            Integer[] ints = new Integer[SIZE];
            LinkedTransferQueue q = new LinkedTransferQueue(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }
    /*
     * Initializing constructor with a collection containing some null elements
     * throws NPE
     */

    public void testConstructor4() {
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE - 1; ++i) {
                ints[i] = new Integer(i);
            }
            LinkedTransferQueue q = new LinkedTransferQueue(Arrays.asList(ints));
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /*
     * Queue contains all elements of the collection it is initialized by
     */
    public void testConstructor5() {
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i) {
                ints[i] = new Integer(i);
            }
            LinkedTransferQueue q = new LinkedTransferQueue(Arrays.asList(ints));
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
        LinkedTransferQueue q = populatedQueue(SIZE);
        int remainingCapacity = q.remainingCapacity();
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(remainingCapacity, q.remainingCapacity());
            assertEquals(SIZE - i, q.size());
            q.remove();
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(remainingCapacity, q.remainingCapacity());
            assertEquals(i, q.size());
            q.add(new Integer(i));
        }
    }

    /**
     * offer(null) throws NPE
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
     * add(null) throws NPE
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
     * addAll(null) throws NPE
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
     * addAll(this) throws IAE
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
     * addAll of a collection with null elements throws NPE
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
     * addAll of a collection with any null elements throws NPE after
     * possibly adding some elements
     */
    public void testAddAll3() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE - 1; ++i) {
                ints[i] = new Integer(i);
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
        try {
            Integer[] empty = new Integer[0];
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i) {
                ints[i] = new Integer(i);
            }
            LinkedTransferQueue q = new LinkedTransferQueue();
            assertFalse(q.addAll(Arrays.asList(empty)));
            assertTrue(q.addAll(Arrays.asList(ints)));
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(ints[i], q.poll());
            }
        } finally {
        }
    }

    /**
     * put(null) throws NPE
     */
    public void testPutNull() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            q.put(null);
            shouldThrow();
        } catch (NullPointerException success) {
        } catch (Exception ie) {
            unexpectedException();
        }
    }

    /**
     * all elements successfully put are contained
     */
    public void testPut() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            for (int i = 0; i < SIZE; ++i) {
                Integer I = new Integer(i);
                q.put(I);
                assertTrue(q.contains(I));
            }
        } catch (Exception ie) {
            unexpectedException();
        }
    }

    /**
     * take retrieves elements in FIFO order
     */
    public void testTake() {
        try {
            LinkedTransferQueue q = populatedQueue(SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(i, ((Integer) q.take()).intValue());
            }
        } catch (InterruptedException e) {
            unexpectedException();
        }
    }

    /**
     * take blocks interruptibly when empty
     */
    public void testTakeFromEmpty() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        Thread t = new Thread(new Runnable() {

            public void run() {
                try {
                    q.take();
                    threadShouldThrow();
                } catch (InterruptedException success) {
                }
            }
        });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch (Exception e) {
            unexpectedException();
        }
    }
    /*
     * Take removes existing elements until empty, then blocks interruptibly
     */

    public void testBlockingTake() {
        Thread t = new Thread(new Runnable() {

            public void run() {
                try {
                    LinkedTransferQueue q = populatedQueue(SIZE);
                    for (int i = 0; i < SIZE; ++i) {
                        assertEquals(i, ((Integer) q.take()).intValue());
                    }
                    q.take();
                    threadShouldThrow();
                } catch (InterruptedException success) {
                }
            }
        });
        t.start();
        try {
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch (InterruptedException ie) {
            unexpectedException();
        }
    }

    /**
     * poll succeeds unless empty
     */
    public void testPoll() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer) q.poll()).intValue());
        }
        assertNull(q.poll());
    }

    /**
     * timed pool with zero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll0() {
        try {
            LinkedTransferQueue q = populatedQueue(SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(i, ((Integer) q.poll(0, TimeUnit.MILLISECONDS)).intValue());
            }
            assertNull(q.poll(0, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            unexpectedException();
        }
    }

    /**
     * timed pool with nonzero timeout succeeds when non-empty, else times out
     */
    public void testTimedPoll() {
        try {
            LinkedTransferQueue q = populatedQueue(SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(i, ((Integer) q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS)).intValue());
            }
            assertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            unexpectedException();
        }
    }

    /**
     * Interrupted timed poll throws InterruptedException instead of
     * returning timeout status
     */
    public void testInterruptedTimedPoll() {
        Thread t = new Thread(new Runnable() {

            public void run() {
                try {
                    LinkedTransferQueue q = populatedQueue(SIZE);
                    for (int i = 0; i < SIZE; ++i) {
                        threadAssertEquals(i, ((Integer) q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS)).intValue());
                    }
                    threadAssertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                } catch (InterruptedException success) {
                }
            }
        });
        t.start();
        try {
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch (InterruptedException ie) {
            unexpectedException();
        }
    }

    /**
     *  timed poll before a delayed offer fails; after offer succeeds;
     *  on interruption throws
     */
    public void testTimedPollWithOffer() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        Thread t = new Thread(new Runnable() {

            public void run() {
                try {
                    threadAssertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                    q.poll(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
                    q.poll(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
                    threadShouldThrow();
                } catch (InterruptedException success) {
                }
            }
        });
        try {
            t.start();
            Thread.sleep(SMALL_DELAY_MS);
            assertTrue(q.offer(zero, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            t.interrupt();
            t.join();
        } catch (Exception e) {
            unexpectedException();
        }
    }

    /**
     * peek returns next element, or null if empty
     */
    public void testPeek() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer) q.peek()).intValue());
            q.poll();
            assertTrue(q.peek() == null ||
                    i != ((Integer) q.peek()).intValue());
        }
        assertNull(q.peek());
    }

    /**
     * element returns next element, or throws NSEE if empty
     */
    public void testElement() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer) q.element()).intValue());
            q.poll();
        }
        try {
            q.element();
            shouldThrow();
        } catch (NoSuchElementException success) {
        }
    }

    /**
     * remove removes next element, or throws NSEE if empty
     */
    public void testRemove() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer) q.remove()).intValue());
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
            assertTrue(q.remove(new Integer(i)));
        }
        for (int i = 0; i < SIZE; i += 2) {
            assertTrue(q.remove(new Integer(i)));
            assertFalse(q.remove(new Integer(i + 1)));
        }
        assertTrue(q.isEmpty());
    }

    /**
     * An add following remove(x) succeeds
     */
    public void testRemoveElementAndAdd() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            assertTrue(q.add(new Integer(1)));
            assertTrue(q.add(new Integer(2)));
            assertTrue(q.remove(new Integer(1)));
            assertTrue(q.remove(new Integer(2)));
            assertTrue(q.add(new Integer(3)));
            assertTrue(q.take() != null);
        } catch (Exception e) {
            unexpectedException();
        }
    }

    /**
     * contains(x) reports true when elements added but not yet removed
     */
    public void testContains() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.poll();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    /**
     * clear removes all elements
     */
    public void testClear() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        int remainingCapacity = q.remainingCapacity();
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(remainingCapacity, q.remainingCapacity());
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
        LinkedTransferQueue q = populatedQueue(SIZE);
        LinkedTransferQueue p = new LinkedTransferQueue();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    /**
     * retainAll(c) retains only those elements of c and reports true if changed
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
     * removeAll(c) removes only those elements of c and reports true if changed
     */
    public void testRemoveAll() {
        for (int i = 1; i < SIZE; ++i) {
            LinkedTransferQueue q = populatedQueue(SIZE);
            LinkedTransferQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE - i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer I = (Integer) (p.remove());
                assertFalse(q.contains(I));
            }
        }
    }

    /**
     * toArray contains all elements
     */
    public void testToArray() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        Object[] o = q.toArray();
        try {
            for (int i = 0; i < o.length; i++) {
                assertEquals(o[i], q.take());
            }
        } catch (InterruptedException e) {
            unexpectedException();
        }
    }

    /**
     * toArray(a) contains all elements
     */
    public void testToArray2() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        Integer[] ints = new Integer[SIZE];
        ints = (Integer[]) q.toArray(ints);
        try {
            for (int i = 0; i < ints.length; i++) {
                assertEquals(ints[i], q.take());
            }
        } catch (InterruptedException e) {
            unexpectedException();
        }
    }

    /**
     * toArray(null) throws NPE
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
    public void testIterator() {
        LinkedTransferQueue q = populatedQueue(SIZE);
        Iterator it = q.iterator();
        try {
            while (it.hasNext()) {
                assertEquals(it.next(), q.take());
            }
        } catch (InterruptedException e) {
            unexpectedException();
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
        final LinkedTransferQueue q = new LinkedTransferQueue();
        int remainingCapacity = q.remainingCapacity();
        q.add(one);
        q.add(two);
        q.add(three);
        assertEquals(remainingCapacity, q.remainingCapacity());
        int k = 0;
        for (Iterator it = q.iterator(); it.hasNext();) {
            int i = ((Integer) (it.next())).intValue();
            assertEquals(++k, i);
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
        try {
            for (Iterator it = q.iterator(); it.hasNext();) {
                q.remove();
                it.next();
            }
        } catch (ConcurrentModificationException e) {
            unexpectedException();
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
        executor.execute(new Runnable() {

            public void run() {
                try {
                    threadAssertTrue(q.offer(three, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS));
                } catch (Exception e) {
                    threadUnexpectedException();
                }
            }
        });

        executor.execute(new Runnable() {

            public void run() {
                try {
                    Thread.sleep(SMALL_DELAY_MS);
                    threadAssertEquals(one, q.take());
                } catch (InterruptedException e) {
                    threadUnexpectedException();
                }
            }
        });

        joinPool(executor);
    }

    /**
     * poll retrieves elements across Executor threads
     */
    public void testPollInExecutor() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.execute(new Runnable() {

            public void run() {
                threadAssertNull(q.poll());
                try {
                    threadAssertTrue(null != q.poll(MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS));
                    threadAssertTrue(q.isEmpty());
                } catch (InterruptedException e) {
                    threadUnexpectedException();
                }
            }
        });

        executor.execute(new Runnable() {

            public void run() {
                try {
                    Thread.sleep(SMALL_DELAY_MS);
                    q.put(one);
                } catch (InterruptedException e) {
                    threadUnexpectedException();
                }
            }
        });

        joinPool(executor);
    }

    /**
     * A deserialized serialized queue has same elements in same order
     */
    public void testSerialization() {
        LinkedTransferQueue q = populatedQueue(SIZE);

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(q);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            LinkedTransferQueue r = (LinkedTransferQueue) in.readObject();

            assertEquals(q.size(), r.size());
            while (!q.isEmpty()) {
                assertEquals(q.remove(), r.remove());
            }
        } catch (Exception e) {
            unexpectedException();
        }
    }

    /**
     * drainTo(null) throws NPE
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
     * drainTo(this) throws IAE
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
            assertEquals(l.get(i), new Integer(i));
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
            assertEquals(l.get(i), new Integer(i));
        }
    }

    /**
     * drainTo empties full queue, unblocking a waiting put.
     */
    public void testDrainToWithActivePut() {
        final LinkedTransferQueue q = populatedQueue(SIZE);
        Thread t = new Thread(new Runnable() {

            public void run() {
                try {
                    q.put(new Integer(SIZE + 1));
                } catch (Exception ie) {
                    threadUnexpectedException();
                }
            }
        });
        try {
            t.start();
            ArrayList l = new ArrayList();
            q.drainTo(l);
            assertTrue(l.size() >= SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(l.get(i), new Integer(i));
            }
            t.join();
            assertTrue(q.size() + l.size() >= SIZE);
        } catch (Exception e) {
            unexpectedException();
        }
    }

    /**
     * drainTo(null, n) throws NPE
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
     * drainTo(this, n) throws IAE
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
                assertTrue(q.offer(new Integer(j)));
            }
            ArrayList l = new ArrayList();
            q.drainTo(l, i);
            int k = (i < SIZE) ? i : SIZE;
            assertEquals(l.size(), k);
            assertEquals(q.size(), SIZE - k);
            for (int j = 0; j < k; ++j) {
                assertEquals(l.get(j), new Integer(j));
            }
            while (q.poll() != null);
        }
    }

    /*
     * poll and take should decrement the waiting consumer count
     */
    public void testWaitingConsumer() {
        try {
            final LinkedTransferQueue q = new LinkedTransferQueue();
            final ConsumerObserver waiting = new ConsumerObserver();
            new Thread(new Runnable() {

                public void run() {
                    try {
                        threadAssertTrue(q.hasWaitingConsumer());
                        waiting.setWaitingConsumer(q.getWaitingConsumerCount());
                        threadAssertTrue(q.offer(new Object()));
                    } catch (Exception ex) {
                        threadUnexpectedException();
                    }

                }
            }).start();
            assertTrue(q.poll(LONG_DELAY_MS, TimeUnit.MILLISECONDS) != null);
            assertTrue(q.getWaitingConsumerCount() < waiting.getWaitingConsumers());
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }
    /*
     * Inserts null into transfer throws NPE
     */

    public void testTransfer1() {
        try {
            LinkedTransferQueue q = new LinkedTransferQueue();
            q.transfer(null);
            shouldThrow();
        } catch (NullPointerException ex) {
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }

    /*
     * transfer attempts to insert into the queue then wait until that 
     * object is removed via take or poll. 
     */
    public void testTransfer2() {
        final LinkedTransferQueue<Integer> q = new LinkedTransferQueue<Integer>();
        new Thread(new Runnable() {

            public void run() {
                try {
                    q.transfer(new Integer(SIZE));
                    threadAssertTrue(q.isEmpty());
                } catch (Exception ex) {
                    threadUnexpectedException();
                }
            }
        }).start();

        try {
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(1, q.size());
            q.poll();
            assertTrue(q.isEmpty());
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }
    /*
     * transfer will attempt to transfer in fifo order and continue waiting if 
     * the element being transfered is not polled or taken 
     */

    public void testTransfer3() {
        final LinkedTransferQueue<Integer> q = new LinkedTransferQueue<Integer>();
        new Thread(new Runnable() {
                public void run() {
                    try {
                        Integer i;
                        q.transfer((i = new Integer(SIZE + 1)));
                        threadAssertTrue(!q.contains(i));
                        threadAssertEquals(1, q.size());
                    } catch (Exception ex) {
                        threadUnexpectedException();
                    }
                }
            }).start();
        Thread interruptedThread = 
            new Thread(new Runnable() {
                    public void run() {
                        try {
                            q.transfer(new Integer(SIZE));
                            threadShouldThrow();
                        } catch (InterruptedException ex) {
                        }
                    }
                });
        interruptedThread.start();
        try {
            Thread.sleep(LONG_DELAY_MS);
            assertEquals(2, q.size());
            q.poll();
            Thread.sleep(LONG_DELAY_MS);
            interruptedThread.interrupt();
            assertEquals(1, q.size());
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }

    /**
     * transfer will wait as long as a poll or take occurs if one does occur
     * the waiting is finished and the thread that tries to poll/take 
     * wins in retrieving the element
     */
    public void testTransfer4() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        new Thread(new Runnable() {

            public void run() {
                try {
                    q.transfer(new Integer(four));
                    threadAssertFalse(q.contains(new Integer(four)));
                    threadAssertEquals(new Integer(three), q.poll());
                } catch (Exception ex) {
                    threadUnexpectedException();
                }
            }
        }).start();
        try {
            Thread.sleep(MEDIUM_DELAY_MS);
            assertTrue(q.offer(three));
            assertEquals(new Integer(four), q.poll());
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }
    /*
     * Insert null into trTransfer throws NPE
     */

    public void testTryTransfer1() {
        try {
            final LinkedTransferQueue q = new LinkedTransferQueue();
            q.tryTransfer(null);
            this.shouldThrow();
        } catch (NullPointerException ex) {
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }
    /*
     * tryTransfer returns false and does not enqueue if there are no consumers
     * waiting to poll or take.
     */

    public void testTryTransfer2() {
        try {
            final LinkedTransferQueue q = new LinkedTransferQueue();
            assertFalse(q.tryTransfer(new Object()));
            assertEquals(0, q.size());
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }
    /*
     * if there is a consumer waiting poll or take tryTransfer returns
     * true while enqueueing object
     */

    public void testTryTransfer3() {
        try {
            final LinkedTransferQueue q = new LinkedTransferQueue();
            new Thread(new Runnable() {

                public void run() {
                    try {
                        threadAssertTrue(q.hasWaitingConsumer());
                        threadAssertTrue(q.tryTransfer(new Object()));
                    } catch (Exception ex) {
                        threadUnexpectedException();
                    }

                }
            }).start();
            assertTrue(q.poll(LONG_DELAY_MS, TimeUnit.MILLISECONDS) != null);
            assertTrue(q.isEmpty());
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }

    /*
     * tryTransfer waits the amount given if interrupted, show an
     * interrupted exception
     */
    public void testTryTransfer4() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        Thread toInterrupt = new Thread(new Runnable() {

            public void run() {
                try {
                    q.tryTransfer(new Object(), LONG_DELAY_MS, TimeUnit.MILLISECONDS);
                    threadShouldThrow();
                } catch (InterruptedException ex) {
                }
            }
        });
        try {
            toInterrupt.start();
            Thread.sleep(SMALL_DELAY_MS);
            toInterrupt.interrupt();
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }

    /*
     * tryTransfer gives up after the timeout and return false
     */
    public void testTryTransfer5() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        try {
            new Thread(new Runnable() {

                public void run() {
                    try {
                        threadAssertFalse(q.tryTransfer(new Object(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                    } catch (InterruptedException ex) {
                        threadUnexpectedException();
                    }
                }
            }).start();
            Thread.sleep(LONG_DELAY_MS);
            assertTrue(q.isEmpty());
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }

    /*
     * tryTransfer waits for any elements previously in to be removed
     * before transfering to a poll or take
     */
    public void testTryTransfer6() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        q.offer(new Integer(four));
        new Thread(new Runnable() {

            public void run() {
                try {
                    threadAssertTrue(q.tryTransfer(new Integer(five), LONG_DELAY_MS, TimeUnit.MILLISECONDS));
                    threadAssertTrue(q.isEmpty());
                } catch (InterruptedException ex) {
                    threadUnexpectedException();
                }
            }
        }).start();
        try {
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(2, q.size());
            assertEquals(new Integer(four), q.poll());
            assertEquals(new Integer(five), q.poll());
            assertTrue(q.isEmpty());
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }

    /*
     * tryTransfer attempts to enqueue into the q and fails returning false not 
     * enqueueing and the successing poll is null
     */
    public void testTryTransfer7() {
        final LinkedTransferQueue q = new LinkedTransferQueue();
        q.offer(new Integer(four));
        new Thread(new Runnable() {

            public void run() {
                try {
                    threadAssertFalse(q.tryTransfer(new Integer(five), SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                    threadAssertTrue(q.isEmpty());
                } catch (InterruptedException ex) {
                    threadUnexpectedException();
                }
            }
        }).start();
        try {
            assertEquals(1, q.size());
            assertEquals(new Integer(four), q.poll());
            Thread.sleep(MEDIUM_DELAY_MS);
            assertNull(q.poll());
        } catch (Exception ex) {
            this.unexpectedException();
        }
    }

    private LinkedTransferQueue populatedQueue(
            int n) {
        LinkedTransferQueue q = new LinkedTransferQueue();
        assertTrue(q.isEmpty());
        int remainingCapacity = q.remainingCapacity();
        for (int i = 0; i <
                n; i++) {
            assertTrue(q.offer(i));
        }

        assertFalse(q.isEmpty());
        assertEquals(remainingCapacity, q.remainingCapacity());
        assertEquals(n, q.size());
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
