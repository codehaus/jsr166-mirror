/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

public class PriorityBlockingQueueTest extends JSR166TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(PriorityBlockingQueueTest.class);
    }

    private static final int NOCAP = Integer.MAX_VALUE;

    /** Sample Comparator */
    static class MyReverseComparator implements Comparator { 
        public int compare(Object x, Object y) {
            int i = ((Integer)x).intValue();
            int j = ((Integer)y).intValue();
            if (i < j) return 1;
            if (i > j) return -1;
            return 0;
        }
    }


    /**
     * Create a queue of given size containing consecutive
     * Integers 0 ... n.
     */
    private PriorityBlockingQueue populatedQueue(int n) {
        PriorityBlockingQueue q = new PriorityBlockingQueue(n);
        assertTrue(q.isEmpty());
	for(int i = n-1; i >= 0; i-=2)
	    assertTrue(q.offer(new Integer(i)));
	for(int i = (n & 1); i < n; i+=2)
	    assertTrue(q.offer(new Integer(i)));
        assertFalse(q.isEmpty());
        assertEquals(NOCAP, q.remainingCapacity());
	assertEquals(n, q.size());
        return q;
    }
 
    /**
     *
     */
    public void testConstructor1() {
        assertEquals(NOCAP, new PriorityBlockingQueue(SIZE).remainingCapacity());
    }

    /**
     *
     */
    public void testConstructor2() {
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(0);
            shouldThrow();
        }
        catch (IllegalArgumentException success) {}
    }

    /**
     *
     */
    public void testConstructor3() {

        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(null);
            shouldThrow();
        }
        catch (NullPointerException success) {}
    }

    /**
     *
     */
    public void testConstructor4() {
        try {
            Integer[] ints = new Integer[SIZE];
            PriorityBlockingQueue q = new PriorityBlockingQueue(Arrays.asList(ints));
            shouldThrow();
        }
        catch (NullPointerException success) {}
    }

    /**
     *
     */
    public void testConstructor5() {
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE-1; ++i)
                ints[i] = new Integer(i);
            PriorityBlockingQueue q = new PriorityBlockingQueue(Arrays.asList(ints));
            shouldThrow();
        }
        catch (NullPointerException success) {}
    }

    /**
     *
     */
    public void testConstructor6() {
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i)
                ints[i] = new Integer(i);
            PriorityBlockingQueue q = new PriorityBlockingQueue(Arrays.asList(ints));
            for (int i = 0; i < SIZE; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    /**
     *
     */
    public void testConstructor7() {
        try {
            MyReverseComparator cmp = new MyReverseComparator();
            PriorityBlockingQueue q = new PriorityBlockingQueue(SIZE, cmp);
            assertEquals(cmp, q.comparator());
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            for (int i = SIZE-1; i >= 0; --i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    /**
     *
     */
    public void testEmpty() {
        PriorityBlockingQueue q = new PriorityBlockingQueue(2);
        assertTrue(q.isEmpty());
        assertEquals(NOCAP, q.remainingCapacity());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.add(new Integer(2));
        q.remove();
        q.remove();
        assertTrue(q.isEmpty());
    }

    /**
     *
     */
    public void testRemainingCapacity() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(NOCAP, q.remainingCapacity());
            assertEquals(SIZE-i, q.size());
            q.remove();
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(NOCAP, q.remainingCapacity());
            assertEquals(i, q.size());
            q.add(new Integer(i));
        }
    }

    /**
     *
     */
    public void testOfferNull() {
	try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(1);
            q.offer(null);
            shouldThrow();
        } catch (NullPointerException success) { }   
    }

    /**
     *
     */
    public void testOffer() {
        PriorityBlockingQueue q = new PriorityBlockingQueue(1);
        assertTrue(q.offer(new Integer(0)));
        assertTrue(q.offer(new Integer(1)));
    }

    /**
     *
     */
    public void testOfferNonComparable() {
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(1);
            q.offer(new Object());
            q.offer(new Object());
            q.offer(new Object());
            shouldThrow();
        }
        catch(ClassCastException success) {}
    }

    /**
     *
     */
    public void testAdd() {
        PriorityBlockingQueue q = new PriorityBlockingQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.size());
            assertTrue(q.add(new Integer(i)));
        }
    }

    /**
     *
     */
    public void testAddAll1() {
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(1);
            q.addAll(null);
            shouldThrow();
        }
        catch (NullPointerException success) {}
    }
    /**
     *
     */
    public void testAddAll2() {
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(SIZE);
            Integer[] ints = new Integer[SIZE];
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        }
        catch (NullPointerException success) {}
    }
    /**
     *
     */
    public void testAddAll3() {
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(SIZE);
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE-1; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            shouldThrow();
        }
        catch (NullPointerException success) {}
    }

    /**
     *
     */
    public void testAddAll5() {
        try {
            Integer[] empty = new Integer[0];
            Integer[] ints = new Integer[SIZE];
            for (int i = SIZE-1; i >= 0; --i)
                ints[i] = new Integer(i);
            PriorityBlockingQueue q = new PriorityBlockingQueue(SIZE);
            assertFalse(q.addAll(Arrays.asList(empty)));
            assertTrue(q.addAll(Arrays.asList(ints)));
            for (int i = 0; i < SIZE; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    /**
     *
     */
     public void testPutNull() {
	try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(SIZE);
            q.put(null);
            shouldThrow();
        } 
        catch (NullPointerException success){
	}   
     }

    /**
     *
     */
     public void testPut() {
         try {
             PriorityBlockingQueue q = new PriorityBlockingQueue(SIZE);
             for (int i = 0; i < SIZE; ++i) {
                 Integer I = new Integer(i);
                 q.put(I);
                 assertTrue(q.contains(I));
             }
             assertEquals(SIZE, q.size());
         }
         finally {
        }
    }

    /**
     *
     */
    public void testPutWithTake() {
        final PriorityBlockingQueue q = new PriorityBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run() {
                    int added = 0;
                    try {
                        q.put(new Integer(0));
                        ++added;
                        q.put(new Integer(0));
                        ++added;
                        q.put(new Integer(0));
                        ++added;
                        q.put(new Integer(0));
                        ++added;
                        threadAssertTrue(added == 4);
                    } finally {
                    }
                }
            });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            q.take();
            t.interrupt();
            t.join();
        } catch (Exception e){
            unexpectedException();
        }
    }

    /**
     *
     */
    public void testTimedOffer() {
        final PriorityBlockingQueue q = new PriorityBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        q.put(new Integer(0));
                        q.put(new Integer(0));
                        threadAssertTrue(q.offer(new Integer(0), SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        threadAssertTrue(q.offer(new Integer(0), LONG_DELAY_MS, TimeUnit.MILLISECONDS));
                    } finally { }
                }
            });
        
        try {
            t.start();
            Thread.sleep(SMALL_DELAY_MS);
            t.interrupt();
            t.join();
        } catch (Exception e){
            unexpectedException();
        }
    }

    /**
     *
     */
    public void testTake() {
	try {
            PriorityBlockingQueue q = populatedQueue(SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(i, ((Integer)q.take()).intValue());
            }
        } catch (InterruptedException e){
	    unexpectedException();
	}   
    }

    /**
     *
     */
    public void testTakeFromEmpty() {
        final PriorityBlockingQueue q = new PriorityBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        q.take();
			threadShouldThrow();
                    } catch (InterruptedException success){ }                
                }
            });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch (Exception e){
            unexpectedException();
        }
    }

    /**
     *
     */
    public void testBlockingTake() {
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        PriorityBlockingQueue q = populatedQueue(SIZE);
                        for (int i = 0; i < SIZE; ++i) {
                            threadAssertEquals(i, ((Integer)q.take()).intValue());
                        }
                        q.take();
                        threadShouldThrow();
                    } catch (InterruptedException success){
                    }   
                }});
        t.start();
        try { 
           Thread.sleep(SHORT_DELAY_MS); 
           t.interrupt();
           t.join();
        }
        catch (InterruptedException ie) {
	    unexpectedException();
        }
    }


    /**
     *
     */
    public void testPoll() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.poll()).intValue());
        }
	assertNull(q.poll());
    }

    /**
     *
     */
    public void testTimedPoll0() {
        try {
            PriorityBlockingQueue q = populatedQueue(SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(i, ((Integer)q.poll(0, TimeUnit.MILLISECONDS)).intValue());
            }
            assertNull(q.poll(0, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e){
	    unexpectedException();
	}   
    }

    /**
     *
     */
    public void testTimedPoll() {
        try {
            PriorityBlockingQueue q = populatedQueue(SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(i, ((Integer)q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS)).intValue());
            }
            assertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e){
	    unexpectedException();
	}   
    }

    /**
     *
     */
    public void testInterruptedTimedPoll() {
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        PriorityBlockingQueue q = populatedQueue(SIZE);
                        for (int i = 0; i < SIZE; ++i) {
                            threadAssertEquals(i, ((Integer)q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS)).intValue());
                        }
                        threadAssertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                    } catch (InterruptedException success){
                    }   
                }});
        t.start();
        try { 
           Thread.sleep(SHORT_DELAY_MS); 
           t.interrupt();
           t.join();
        }
        catch (InterruptedException ie) {
	    unexpectedException();
        }
    }

    /**
     *
     */
    public void testTimedPollWithOffer() {
        final PriorityBlockingQueue q = new PriorityBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        threadAssertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        q.poll(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
                        q.poll(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
			threadShouldThrow();
                    } catch (InterruptedException success) { }                
                }
            });
        try {
            t.start();
            Thread.sleep(SMALL_DELAY_MS);
            assertTrue(q.offer(new Integer(0), SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            t.interrupt();
            t.join();
        } catch (Exception e){
            unexpectedException();
        }
    }  


    /**
     *
     */
    public void testPeek() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.peek()).intValue());
            q.poll();
            assertTrue(q.peek() == null ||
                       i != ((Integer)q.peek()).intValue());
        }
	assertNull(q.peek());
    }

    /**
     *
     */
    public void testElement() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.element()).intValue());
            q.poll();
        }
        try {
            q.element();
            shouldThrow();
        }
        catch (NoSuchElementException success) {}
    }

    /**
     *
     */
    public void testRemove() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.remove()).intValue());
        }
        try {
            q.remove();
            shouldThrow();
        } catch (NoSuchElementException success){
	}   
    }

    /**
     *
     */
    public void testRemoveElement() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        for (int i = 1; i < SIZE; i+=2) {
            assertTrue(q.remove(new Integer(i)));
        }
        for (int i = 0; i < SIZE; i+=2) {
            assertTrue(q.remove(new Integer(i)));
            assertFalse(q.remove(new Integer(i+1)));
        }
        assertTrue(q.isEmpty());
    }
	
    /**
     *
     */
    public void testContains() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.poll();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    /**
     *
     */
    public void testClear() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(NOCAP, q.remainingCapacity());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    /**
     *
     */
    public void testContainsAll() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        PriorityBlockingQueue p = new PriorityBlockingQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    /**
     *
     */
    public void testRetainAll() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        PriorityBlockingQueue p = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            boolean changed = q.retainAll(p);
            if (i == 0)
                assertFalse(changed);
            else
                assertTrue(changed);

            assertTrue(q.containsAll(p));
            assertEquals(SIZE-i, q.size());
            p.remove();
        }
    }

    /**
     *
     */
    public void testRemoveAll() {
        for (int i = 1; i < SIZE; ++i) {
            PriorityBlockingQueue q = populatedQueue(SIZE);
            PriorityBlockingQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE-i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer I = (Integer)(p.remove());
                assertFalse(q.contains(I));
            }
        }
    }

    /**
     *
     */
    public void testToArray() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
	Object[] o = q.toArray();
        Arrays.sort(o);
	try {
	for(int i = 0; i < o.length; i++)
	    assertEquals(o[i], q.take());
	} catch (InterruptedException e){
	    unexpectedException();
	}    
    }

    /**
     *
     */
    public void testToArray2() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
	Integer[] ints = new Integer[SIZE];
	ints = (Integer[])q.toArray(ints);
        Arrays.sort(ints);
	try {
	    for(int i = 0; i < ints.length; i++)
		assertEquals(ints[i], q.take());
	} catch (InterruptedException e){
	    unexpectedException();
	}    
    }
    
    /**
     *
     */
    public void testIterator() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        int i = 0;
	Iterator it = q.iterator();
        while(it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(i, SIZE);
    }

    /**
     *
     */
    public void testIteratorRemove () {
        final PriorityBlockingQueue q = new PriorityBlockingQueue(3);
        q.add(new Integer(2));
        q.add(new Integer(1));
        q.add(new Integer(3));

        Iterator it = q.iterator();
        it.next();
        it.remove();

        it = q.iterator();
        assertEquals(it.next(), new Integer(2));
        assertEquals(it.next(), new Integer(3));
        assertFalse(it.hasNext());
    }


    /**
     *
     */
    public void testToString() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        

    /**
     *
     */
    public void testPollInExecutor() {

        final PriorityBlockingQueue q = new PriorityBlockingQueue(2);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new Runnable() {
            public void run() {
                threadAssertNull(q.poll());
                try {
                    threadAssertTrue(null != q.poll(MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS));
                    threadAssertTrue(q.isEmpty());
                }
                catch (InterruptedException e) {
                    threadUnexpectedException();
                }
            }
        });

        executor.execute(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(SMALL_DELAY_MS);
                    q.put(new Integer(1));
                }
                catch (InterruptedException e) {
                    threadUnexpectedException();
                }
            }
        });
        
        joinPool(executor);

    }

    /**
     *
     */
    public void testSerialization() {
        PriorityBlockingQueue q = populatedQueue(SIZE);
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(q);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            PriorityBlockingQueue r = (PriorityBlockingQueue)in.readObject();
            assertEquals(q.size(), r.size());
            while (!q.isEmpty()) 
                assertEquals(q.remove(), r.remove());
        } catch(Exception e){
            unexpectedException();
        }
    }

}
