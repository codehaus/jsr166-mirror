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

public class LinkedBlockingQueueTest extends JSR166TestCase {

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(LinkedBlockingQueueTest.class);
    }


    /**
     * Create a queue of given size containing consecutive
     * Integers 0 ... n.
     */
    private LinkedBlockingQueue populatedQueue(int n) {
        LinkedBlockingQueue q = new LinkedBlockingQueue(n);
        assertTrue(q.isEmpty());
	for(int i = 0; i < n; i++)
	    assertTrue(q.offer(new Integer(i)));
        assertFalse(q.isEmpty());
        assertEquals(0, q.remainingCapacity());
	assertEquals(n, q.size());
        return q;
    }
 
    public void testConstructor1(){
        assertEquals(SIZE, new LinkedBlockingQueue(SIZE).remainingCapacity());
    }

    public void testConstructor2(){
        try {
            LinkedBlockingQueue q = new LinkedBlockingQueue(0);
            fail("Cannot make zero-sized");
        }
        catch (IllegalArgumentException success) {}
    }

    public void testConstructor3(){

        try {
            LinkedBlockingQueue q = new LinkedBlockingQueue(null);
            fail("Cannot make from null collection");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor4(){
        try {
            Integer[] ints = new Integer[SIZE];
            LinkedBlockingQueue q = new LinkedBlockingQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor5(){
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE-1; ++i)
                ints[i] = new Integer(i);
            LinkedBlockingQueue q = new LinkedBlockingQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor6(){
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i)
                ints[i] = new Integer(i);
            LinkedBlockingQueue q = new LinkedBlockingQueue(Arrays.asList(ints));
            for (int i = 0; i < SIZE; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testEmptyFull() {
        LinkedBlockingQueue q = new LinkedBlockingQueue(2);
        assertTrue(q.isEmpty());
        assertEquals("should have room for 2", 2, q.remainingCapacity());
        q.add(one);
        assertFalse(q.isEmpty());
        q.add(two);
        assertFalse(q.isEmpty());
        assertEquals("queue should be full", 0, q.remainingCapacity());
        assertFalse("offer should be rejected", q.offer(three));
    }

    public void testRemainingCapacity(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.remainingCapacity());
            assertEquals(SIZE-i, q.size());
            q.remove();
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(SIZE-i, q.remainingCapacity());
            assertEquals(i, q.size());
            q.add(new Integer(i));
        }
    }

    public void testOfferNull(){
	try {
            LinkedBlockingQueue q = new LinkedBlockingQueue(1);
            q.offer(null);
            fail("should throw NPE");
        } catch (NullPointerException success) { }   
    }

    public void testOffer(){
        LinkedBlockingQueue q = new LinkedBlockingQueue(1);
        assertTrue(q.offer(zero));
        assertFalse(q.offer(one));
    }

    public void testAdd(){
	try {
            LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertTrue(q.add(new Integer(i)));
            }
            assertEquals(0, q.remainingCapacity());
            q.add(new Integer(SIZE));
        } catch (IllegalStateException success){
	}   
    }

    public void testAddAll1(){
        try {
            LinkedBlockingQueue q = new LinkedBlockingQueue(1);
            q.addAll(null);
            fail("Cannot add null collection");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll2(){
        try {
            LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
            Integer[] ints = new Integer[SIZE];
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll3(){
        try {
            LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE-1; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll4(){
        try {
            LinkedBlockingQueue q = new LinkedBlockingQueue(1);
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            fail("Cannot add with insufficient capacity");
        }
        catch (IllegalStateException success) {}
    }
    public void testAddAll5(){
        try {
            Integer[] empty = new Integer[0];
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i)
                ints[i] = new Integer(i);
            LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
            assertFalse(q.addAll(Arrays.asList(empty)));
            assertTrue(q.addAll(Arrays.asList(ints)));
            for (int i = 0; i < SIZE; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

     public void testPutNull() {
	try {
            LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
            q.put(null);
            fail("put should throw NPE");
        } 
        catch (NullPointerException success){
	}   
        catch (InterruptedException ie) {
	    fail("Unexpected exception");
        }
     }

     public void testPut() {
         try {
             LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
             for (int i = 0; i < SIZE; ++i) {
                 Integer I = new Integer(i);
                 q.put(I);
                 assertTrue(q.contains(I));
             }
             assertEquals(0, q.remainingCapacity());
         }
        catch (InterruptedException ie) {
	    fail("Unexpected exception");
        }
    }

    public void testBlockingPut(){
        Thread t = new Thread(new Runnable() {
                public void run() {
                    int added = 0;
                    try {
                        LinkedBlockingQueue q = new LinkedBlockingQueue(SIZE);
                        for (int i = 0; i < SIZE; ++i) {
                            q.put(new Integer(i));
                            ++added;
                        }
                        q.put(new Integer(SIZE));
                        threadFail("put should block");
                    } catch (InterruptedException ie){
                        threadAssertEquals(added, SIZE);
                    }   
                }});
        t.start();
        try { 
           Thread.sleep(SHORT_DELAY_MS); 
           t.interrupt();
           t.join();
        }
        catch (InterruptedException ie) {
	    fail("Unexpected exception");
        }
    }

    public void testPutWithTake() {
        final LinkedBlockingQueue q = new LinkedBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run(){
                    int added = 0;
                    try {
                        q.put(new Object());
                        ++added;
                        q.put(new Object());
                        ++added;
                        q.put(new Object());
                        ++added;
                        q.put(new Object());
                        ++added;
			threadFail("Should block");
                    } catch (InterruptedException e){
                        threadAssertTrue(added >= 2);
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
            fail("Unexpected exception");
        }
    }

    public void testTimedOffer() {
        final LinkedBlockingQueue q = new LinkedBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try {
                        q.put(new Object());
                        q.put(new Object());
                        threadAssertFalse(q.offer(new Object(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        q.offer(new Object(), LONG_DELAY_MS, TimeUnit.MILLISECONDS);
			threadFail("Should block");
                    } catch (InterruptedException success){}
                }
            });
        
        try {
            t.start();
            Thread.sleep(SMALL_DELAY_MS);
            t.interrupt();
            t.join();
        } catch (Exception e){
            fail("Unexpected exception");
        }
    }

    public void testTake(){
	try {
            LinkedBlockingQueue q = populatedQueue(SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(i, ((Integer)q.take()).intValue());
            }
        } catch (InterruptedException e){
	    fail("Unexpected exception");
	}   
    }

    public void testTakeFromEmpty() {
        final LinkedBlockingQueue q = new LinkedBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try {
                        q.take();
			threadFail("Should block");
                    } catch (InterruptedException success){ }                
                }
            });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch (Exception e){
            fail("Unexpected exception");
        }
    }

    public void testBlockingTake(){
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        LinkedBlockingQueue q = populatedQueue(SIZE);
                        for (int i = 0; i < SIZE; ++i) {
                            assertEquals(i, ((Integer)q.take()).intValue());
                        }
                        q.take();
                        threadFail("take should block");
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
	    fail("Unexpected exception");
        }
    }


    public void testPoll(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.poll()).intValue());
        }
	assertNull(q.poll());
    }

    public void testTimedPoll0() {
        try {
            LinkedBlockingQueue q = populatedQueue(SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(i, ((Integer)q.poll(0, TimeUnit.MILLISECONDS)).intValue());
            }
            assertNull(q.poll(0, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e){
	    fail("Unexpected exception");
	}   
    }

    public void testTimedPoll() {
        try {
            LinkedBlockingQueue q = populatedQueue(SIZE);
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(i, ((Integer)q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS)).intValue());
            }
            assertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e){
	    fail("Unexpected exception");
	}   
    }

    public void testInterruptedTimedPoll(){
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        LinkedBlockingQueue q = populatedQueue(SIZE);
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
	    fail("Unexpected exception");
        }
    }

    public void testTimedPollWithOffer(){
        final LinkedBlockingQueue q = new LinkedBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try {
                        threadAssertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        q.poll(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
                        q.poll(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
			threadFail("Should block");
                    } catch (InterruptedException success) { }                
                }
            });
        try {
            t.start();
            Thread.sleep(SMALL_DELAY_MS);
            assertTrue(q.offer(zero, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            t.interrupt();
            t.join();
        } catch (Exception e){
            fail("Unexpected exception");
        }
    }  


    public void testPeek(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.peek()).intValue());
            q.poll();
            assertTrue(q.peek() == null ||
                       i != ((Integer)q.peek()).intValue());
        }
	assertNull(q.peek());
    }

    public void testElement(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.element()).intValue());
            q.poll();
        }
        try {
            q.element();
            fail("no such element");
        }
        catch (NoSuchElementException success) {}
    }

    public void testRemove(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.remove()).intValue());
        }
        try {
            q.remove();
            fail("remove should throw");
        } catch (NoSuchElementException success){
	}   
    }

    public void testRemoveElement(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 1; i < SIZE; i+=2) {
            assertTrue(q.remove(new Integer(i)));
        }
        for (int i = 0; i < SIZE; i+=2) {
            assertTrue(q.remove(new Integer(i)));
            assertFalse(q.remove(new Integer(i+1)));
        }
        assertTrue(q.isEmpty());
    }
	
    public void testContains(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.poll();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    public void testClear(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(SIZE, q.remainingCapacity());
        q.add(one);
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    public void testContainsAll(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        LinkedBlockingQueue p = new LinkedBlockingQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    public void testRetainAll(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        LinkedBlockingQueue p = populatedQueue(SIZE);
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

    public void testRemoveAll(){
        for (int i = 1; i < SIZE; ++i) {
            LinkedBlockingQueue q = populatedQueue(SIZE);
            LinkedBlockingQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE-i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer I = (Integer)(p.remove());
                assertFalse(q.contains(I));
            }
        }
    }


    public void testToArray(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
	Object[] o = q.toArray();
	try {
	for(int i = 0; i < o.length; i++)
	    assertEquals(o[i], q.take());
	} catch (InterruptedException e){
	    fail("Unexpected exception");
	}    
    }

    public void testToArray2(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
	Integer[] ints = new Integer[SIZE];
	ints = (Integer[])q.toArray(ints);
	try {
	    for(int i = 0; i < ints.length; i++)
		assertEquals(ints[i], q.take());
	} catch (InterruptedException e){
	    fail("Unexpected exception");
	}    
    }
    
    public void testIterator(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
	Iterator it = q.iterator();
	try {
	    while(it.hasNext()){
		assertEquals(it.next(), q.take());
	    }
	} catch (InterruptedException e){
	    fail("Unexpected exception");
	}    
    }

    public void testIteratorOrdering() {

        final LinkedBlockingQueue q = new LinkedBlockingQueue(3);

        q.add(one);
        q.add(two);
        q.add(three);

        assertEquals("queue should be full", 0, q.remainingCapacity());

        int k = 0;
        for (Iterator it = q.iterator(); it.hasNext();) {
            int i = ((Integer)(it.next())).intValue();
            assertEquals("items should come out in order", ++k, i);
        }

        assertEquals("should go through 3 elements", 3, k);
    }

    public void testWeaklyConsistentIteration () {

        final LinkedBlockingQueue q = new LinkedBlockingQueue(3);

        q.add(one);
        q.add(two);
        q.add(three);

        try {
            for (Iterator it = q.iterator(); it.hasNext();) {
                q.remove();
                it.next();
            }
        }
        catch (ConcurrentModificationException e) {
            fail("weakly consistent iterator; should not get CME");
        }

        assertEquals("queue should be empty again", 0, q.size());
    }


    public void testToString(){
        LinkedBlockingQueue q = populatedQueue(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        


    public void testOfferInExecutor() {

        final LinkedBlockingQueue q = new LinkedBlockingQueue(2);

        q.add(one);
        q.add(two);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new Runnable() {
            public void run() {
                threadAssertFalse(q.offer(three));
                try {
                    threadAssertTrue(q.offer(three, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS));
                    threadAssertEquals(0, q.remainingCapacity());
                }
                catch (InterruptedException e) {
                    threadFail("should not be interrupted");
                }
            }
        });

        executor.execute(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(SMALL_DELAY_MS);
                    threadAssertEquals(one, q.take());
                }
                catch (InterruptedException e) {
                    threadFail("should not be interrupted");
                }
            }
        });
        
        joinPool(executor);

    }

    public void testPollInExecutor() {

        final LinkedBlockingQueue q = new LinkedBlockingQueue(2);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new Runnable() {
            public void run() {
                threadAssertNull(q.poll());
                try {
                    threadAssertTrue(null != q.poll(MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS));
                    threadAssertTrue(q.isEmpty());
                }
                catch (InterruptedException e) {
                    threadFail("should not be interrupted");
                }
            }
        });

        executor.execute(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(SMALL_DELAY_MS);
                    q.put(one);
                }
                catch (InterruptedException e) {
                    threadFail("should not be interrupted");
                }
            }
        });
        
        joinPool(executor);
    }

    public void testSerialization() {
        LinkedBlockingQueue q = populatedQueue(SIZE);

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(q);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            LinkedBlockingQueue r = (LinkedBlockingQueue)in.readObject();
            assertEquals(q.size(), r.size());
            while (!q.isEmpty()) 
                assertEquals(q.remove(), r.remove());
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }

}
