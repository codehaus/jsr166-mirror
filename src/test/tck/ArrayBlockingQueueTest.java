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

public class ArrayBlockingQueueTest extends TestCase {

    private static int N = 10;
    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(ArrayBlockingQueueTest.class);
    }

    /**
     * Create a queue of given size containing consecutive
     * Integers 0 ... n.
     */
    private ArrayBlockingQueue fullQueue(int n) {
        ArrayBlockingQueue q = new ArrayBlockingQueue(n);
        assertTrue(q.isEmpty());
	for(int i = 0; i < n; i++)
	    assertTrue(q.offer(new Integer(i)));
        assertFalse(q.isEmpty());
        assertEquals(0, q.remainingCapacity());
	assertEquals(n, q.size());
        return q;
    }
 
    public void testConstructor1(){
        assertEquals(N, new ArrayBlockingQueue(N).remainingCapacity());
    }

    public void testConstructor2(){
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(0);
            fail("Cannot make zero-sized");
        }
        catch (IllegalArgumentException success) {}
    }

    public void testConstructor3(){

        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(1, true, null);
            fail("Cannot make from null collection");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor4(){
        try {
            Integer[] ints = new Integer[N];
            ArrayBlockingQueue q = new ArrayBlockingQueue(N, false, Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor5(){
        try {
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N-1; ++i)
                ints[i] = new Integer(i);
            ArrayBlockingQueue q = new ArrayBlockingQueue(N, false, Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor6(){
        try {
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            ArrayBlockingQueue q = new ArrayBlockingQueue(1, false, Arrays.asList(ints));
            fail("Cannot make with insufficient capacity");
        }
        catch (IllegalArgumentException success) {}
    }

    public void testConstructor7(){
        try {
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            ArrayBlockingQueue q = new ArrayBlockingQueue(N, true, Arrays.asList(ints));
            for (int i = 0; i < N; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testEmptyFull() {
        ArrayBlockingQueue q = new ArrayBlockingQueue(2);
        assertTrue(q.isEmpty());
        assertEquals("should have room for 2", 2, q.remainingCapacity());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.add(new Integer(2));
        assertFalse(q.isEmpty());
        assertEquals("queue should be full", 0, q.remainingCapacity());
        assertFalse("offer should be rejected", q.offer(new Integer(3)));
    }

    public void testRemainingCapacity(){
        ArrayBlockingQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, q.remainingCapacity());
            assertEquals(N-i, q.size());
            q.remove();
        }
        for (int i = 0; i < N; ++i) {
            assertEquals(N-i, q.remainingCapacity());
            assertEquals(i, q.size());
            q.add(new Integer(i));
        }
    }

    public void testOfferNull(){
	try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(1);
            q.offer(null);
            fail("should throw NPE");
        } catch (NullPointerException success) { }   
    }

    public void testOffer(){
        ArrayBlockingQueue q = new ArrayBlockingQueue(1);
        assertTrue(q.offer(new Integer(0)));
        assertFalse(q.offer(new Integer(1)));
    }

    public void testAdd(){
	try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(N);
            for (int i = 0; i < N; ++i) {
                assertTrue(q.add(new Integer(i)));
            }
            assertEquals(0, q.remainingCapacity());
            q.add(new Integer(N));
        } catch (IllegalStateException success){
	}   
    }

    public void testAddAll1(){
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(1);
            q.addAll(null);
            fail("Cannot add null collection");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll2(){
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(N);
            Integer[] ints = new Integer[N];
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll3(){
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(N);
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N-1; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll4(){
        try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(1);
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            fail("Cannot add with insufficient capacity");
        }
        catch (IllegalStateException success) {}
    }
    public void testAddAll5(){
        try {
            Integer[] empty = new Integer[0];
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            ArrayBlockingQueue q = new ArrayBlockingQueue(N);
            assertFalse(q.addAll(Arrays.asList(empty)));
            assertTrue(q.addAll(Arrays.asList(ints)));
            for (int i = 0; i < N; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

     public void testPutNull() {
	try {
            ArrayBlockingQueue q = new ArrayBlockingQueue(N);
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
             ArrayBlockingQueue q = new ArrayBlockingQueue(N);
             for (int i = 0; i < N; ++i) {
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
                        ArrayBlockingQueue q = new ArrayBlockingQueue(N);
                        for (int i = 0; i < N; ++i) {
                            q.put(new Integer(i));
                            ++added;
                        }
                        q.put(new Integer(N));
                        fail("put should block");
                    } catch (InterruptedException ie){
                        assertEquals(added, N);
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
        final ArrayBlockingQueue q = new ArrayBlockingQueue(2);
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
			fail("Should block");
                    } catch (InterruptedException e){
                        assertTrue(added >= 2);
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
        final ArrayBlockingQueue q = new ArrayBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try {
                        q.put(new Object());
                        q.put(new Object());
                        assertFalse(q.offer(new Object(), SHORT_DELAY_MS/2, TimeUnit.MILLISECONDS));
                        q.offer(new Object(), LONG_DELAY_MS, TimeUnit.MILLISECONDS);
			fail("Should block");
                    } catch (InterruptedException success){}
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

    public void testTake(){
	try {
            ArrayBlockingQueue q = fullQueue(N);
            for (int i = 0; i < N; ++i) {
                assertEquals(i, ((Integer)q.take()).intValue());
            }
        } catch (InterruptedException e){
	    fail("Unexpected exception");
	}   
    }

    public void testTakeFromEmpty() {
        final ArrayBlockingQueue q = new ArrayBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try {
                        q.take();
			fail("Should block");
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
                        ArrayBlockingQueue q = fullQueue(N);
                        for (int i = 0; i < N; ++i) {
                            assertEquals(i, ((Integer)q.take()).intValue());
                        }
                        q.take();
                        fail("take should block");
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
        ArrayBlockingQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, ((Integer)q.poll()).intValue());
        }
	assertNull(q.poll());
    }

    public void testTimedPoll0() {
        try {
            ArrayBlockingQueue q = fullQueue(N);
            for (int i = 0; i < N; ++i) {
                assertEquals(i, ((Integer)q.poll(0, TimeUnit.MILLISECONDS)).intValue());
            }
            assertNull(q.poll(0, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e){
	    fail("Unexpected exception");
	}   
    }

    public void testTimedPoll() {
        try {
            ArrayBlockingQueue q = fullQueue(N);
            for (int i = 0; i < N; ++i) {
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
                        ArrayBlockingQueue q = fullQueue(N);
                        for (int i = 0; i < N; ++i) {
                            assertEquals(i, ((Integer)q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS)).intValue());
                        }
                        assertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
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
        final ArrayBlockingQueue q = new ArrayBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try {
                        assertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        q.poll(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
                        q.poll(LONG_DELAY_MS, TimeUnit.MILLISECONDS);
			fail("Should block");
                    } catch (InterruptedException success) { }                
                }
            });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS * 2);
            assertTrue(q.offer(new Integer(0), SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            t.interrupt();
            t.join();
        } catch (Exception e){
            fail("Unexpected exception");
        }
    }  


    public void testPeek(){
        ArrayBlockingQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, ((Integer)q.peek()).intValue());
            q.poll();
            assertTrue(q.peek() == null ||
                       i != ((Integer)q.peek()).intValue());
        }
	assertNull(q.peek());
    }

    public void testElement(){
        ArrayBlockingQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
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
        ArrayBlockingQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, ((Integer)q.remove()).intValue());
        }
        try {
            q.remove();
            fail("remove should throw");
        } catch (NoSuchElementException success){
	}   
    }

    public void testRemoveElement(){
        ArrayBlockingQueue q = fullQueue(N);
        for (int i = 1; i < N; i+=2) {
            assertTrue(q.remove(new Integer(i)));
        }
        for (int i = 0; i < N; i+=2) {
            assertTrue(q.remove(new Integer(i)));
            assertFalse(q.remove(new Integer(i+1)));
        }
        assertTrue(q.isEmpty());
    }
	
    public void testContains(){
        ArrayBlockingQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.poll();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    public void testClear(){
        ArrayBlockingQueue q = fullQueue(N);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(N, q.remainingCapacity());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    public void testContainsAll(){
        ArrayBlockingQueue q = fullQueue(N);
        ArrayBlockingQueue p = new ArrayBlockingQueue(N);
        for (int i = 0; i < N; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    public void testRetainAll(){
        ArrayBlockingQueue q = fullQueue(N);
        ArrayBlockingQueue p = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            boolean changed = q.retainAll(p);
            if (i == 0)
                assertFalse(changed);
            else
                assertTrue(changed);

            assertTrue(q.containsAll(p));
            assertEquals(N-i, q.size());
            p.remove();
        }
    }

    public void testRemoveAll(){
        for (int i = 1; i < N; ++i) {
            ArrayBlockingQueue q = fullQueue(N);
            ArrayBlockingQueue p = fullQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(N-i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer I = (Integer)(p.remove());
                assertFalse(q.contains(I));
            }
        }
    }


    public void testToArray(){
        ArrayBlockingQueue q = fullQueue(N);
	Object[] o = q.toArray();
	try {
	for(int i = 0; i < o.length; i++)
	    assertEquals(o[i], q.take());
	} catch (InterruptedException e){
	    fail("Unexpected exception");
	}    
    }

    public void testToArray2(){
        ArrayBlockingQueue q = fullQueue(N);
	Integer[] ints = new Integer[N];
	ints = (Integer[])q.toArray(ints);
	try {
	    for(int i = 0; i < ints.length; i++)
		assertEquals(ints[i], q.take());
	} catch (InterruptedException e){
	    fail("Unexpected exception");
	}    
    }
    
    public void testIterator(){
        ArrayBlockingQueue q = fullQueue(N);
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

        final ArrayBlockingQueue q = new ArrayBlockingQueue(3);

        q.add(new Integer(1));
        q.add(new Integer(2));
        q.add(new Integer(3));

        assertEquals("queue should be full", 0, q.remainingCapacity());

        int k = 0;
        for (Iterator it = q.iterator(); it.hasNext();) {
            int i = ((Integer)(it.next())).intValue();
            assertEquals("items should come out in order", ++k, i);
        }

        assertEquals("should go through 3 elements", 3, k);
    }

    public void testWeaklyConsistentIteration () {

        final ArrayBlockingQueue q = new ArrayBlockingQueue(3);

        q.add(new Integer(1));
        q.add(new Integer(2));
        q.add(new Integer(3));

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
        ArrayBlockingQueue q = fullQueue(N);
        String s = q.toString();
        for (int i = 0; i < N; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        


    public void testOfferInExecutor() {

        final ArrayBlockingQueue q = new ArrayBlockingQueue(2);

        q.add(new Integer(1));
        q.add(new Integer(2));

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new Runnable() {
            public void run() {
                assertFalse("offer should be rejected", q.offer(new Integer(3)));
                try {
                    assertTrue("offer should be accepted", q.offer(new Integer(3), MEDIUM_DELAY_MS * 2, TimeUnit.MILLISECONDS));
                    assertEquals(0, q.remainingCapacity());
                }
                catch (InterruptedException e) {
                    fail("should not be interrupted");
                }
            }
        });

        executor.execute(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(MEDIUM_DELAY_MS);
                    assertEquals("first item in queue should be 1", new Integer(1), q.take());
                }
                catch (InterruptedException e) {
                    fail("should not be interrupted");
                }
            }
        });
        
        executor.shutdown();

    }

    public void testPollInExecutor() {

        final ArrayBlockingQueue q = new ArrayBlockingQueue(2);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new Runnable() {
            public void run() {
                assertNull("poll should fail", q.poll());
                try {
                    assertTrue(null != q.poll(MEDIUM_DELAY_MS * 2, TimeUnit.MILLISECONDS));
                    assertTrue(q.isEmpty());
                }
                catch (InterruptedException e) {
                    fail("should not be interrupted");
                }
            }
        });

        executor.execute(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(MEDIUM_DELAY_MS);
                    q.put(new Integer(1));
                }
                catch (InterruptedException e) {
                    fail("should not be interrupted");
                }
            }
        });
        
        executor.shutdown();

    }

    public void testSerialization() {
        ArrayBlockingQueue q = fullQueue(N);

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(q);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            ArrayBlockingQueue r = (ArrayBlockingQueue)in.readObject();
            assertEquals(q.size(), r.size());
            while (!q.isEmpty()) 
                assertEquals(q.remove(), r.remove());
        } catch(Exception e){
            fail("unexpected exception");
        }
    }


}
