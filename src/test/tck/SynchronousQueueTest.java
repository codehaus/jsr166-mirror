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

public class SynchronousQueueTest extends TestCase {

    private final static int N = 1;
    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(SynchronousQueueTest.class);
    }

    public void testEmptyFull() {
        SynchronousQueue q = new SynchronousQueue();
        assertTrue(q.isEmpty());
	assertEquals(0, q.size());
        assertEquals(0, q.remainingCapacity());
        assertFalse(q.offer(new Integer(3)));
    }

    public void testOfferNull(){
	try {
            SynchronousQueue q = new SynchronousQueue();
            q.offer(null);
            fail("should throw NPE");
        } catch (NullPointerException success) { }   
    }

    public void testOffer(){
        SynchronousQueue q = new SynchronousQueue();
        assertFalse(q.offer(new Integer(1)));
    }

    public void testAdd(){
	try {
            SynchronousQueue q = new SynchronousQueue();
            assertEquals(0, q.remainingCapacity());
            q.add(new Integer(0));
        } catch (IllegalStateException success){
	}   
    }

    public void testAddAll1(){
        try {
            SynchronousQueue q = new SynchronousQueue();
            q.addAll(null);
            fail("Cannot add null collection");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll2(){
        try {
            SynchronousQueue q = new SynchronousQueue();
            Integer[] ints = new Integer[N];
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll4(){
        try {
            SynchronousQueue q = new SynchronousQueue();
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            fail("Cannot add with insufficient capacity");
        }
        catch (IllegalStateException success) {}
    }

    public void testPutNull() {
	try {
            SynchronousQueue q = new SynchronousQueue();
            q.put(null);
            fail("put should throw NPE");
        } 
        catch (NullPointerException success){
	}   
        catch (InterruptedException ie) {
	    fail("Unexpected exception");
        }
     }

    public void testBlockingPut(){
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        SynchronousQueue q = new SynchronousQueue();
                        q.put(new Integer(0));
                        fail("put should block");
                    } catch (InterruptedException ie){
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
        final SynchronousQueue q = new SynchronousQueue();
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
                        assertTrue(added >= 1);
                    }
                }
            });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            q.take();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch (Exception e){
            fail("Unexpected exception");
        }
    }

    public void testTimedOffer() {
        final SynchronousQueue q = new SynchronousQueue();
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try {

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


    public void testTakeFromEmpty() {
        final SynchronousQueue q = new SynchronousQueue();
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

    public void testPoll(){
        SynchronousQueue q = new SynchronousQueue();
	assertNull(q.poll());
    }

    public void testTimedPoll0() {
        try {
            SynchronousQueue q = new SynchronousQueue();
            assertNull(q.poll(0, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e){
	    fail("Unexpected exception");
	}   
    }

    public void testTimedPoll() {
        try {
            SynchronousQueue q = new SynchronousQueue();
            assertNull(q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e){
	    fail("Unexpected exception");
	}   
    }

    public void testInterruptedTimedPoll(){
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        SynchronousQueue q = new SynchronousQueue();
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
        final SynchronousQueue q = new SynchronousQueue();
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
        SynchronousQueue q = new SynchronousQueue();
	assertNull(q.peek());
    }

    public void testElement(){
        SynchronousQueue q = new SynchronousQueue();
        try {
            q.element();
            fail("no such element");
        }
        catch (NoSuchElementException success) {}
    }

    public void testRemove(){
        SynchronousQueue q = new SynchronousQueue();
        try {
            q.remove();
            fail("remove should throw");
        } catch (NoSuchElementException success){
	}   
    }

    public void testRemoveElement(){
        SynchronousQueue q = new SynchronousQueue();
        for (int i = 1; i < N; i+=2) {
            assertFalse(q.remove(new Integer(i)));
        }
        assertTrue(q.isEmpty());
    }
	
    public void testContains(){
        SynchronousQueue q = new SynchronousQueue();
        for (int i = 0; i < N; ++i) {
            assertFalse(q.contains(new Integer(i)));
        }
    }

    public void testClear(){
        SynchronousQueue q = new SynchronousQueue();
        q.clear();
        assertTrue(q.isEmpty());
    }

    public void testContainsAll(){
        SynchronousQueue q = new SynchronousQueue();
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[1]; ints[0] = new Integer(0);
        //        assertTrue(q.containsAll(Arrays.asList(empty)));
        assertFalse(q.containsAll(Arrays.asList(ints)));
    }

    public void testRetainAll(){
        SynchronousQueue q = new SynchronousQueue();
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[1]; ints[0] = new Integer(0);
        q.retainAll(Arrays.asList(ints));
        //        assertTrue(q.containsAll(Arrays.asList(empty)));
        assertFalse(q.containsAll(Arrays.asList(ints)));
    }

    public void testRemoveAll(){
        SynchronousQueue q = new SynchronousQueue();
        Integer[] empty = new Integer[0];
        Integer[] ints = new Integer[1]; ints[0] = new Integer(0);
        q.removeAll(Arrays.asList(ints));
        //        assertTrue(q.containsAll(Arrays.asList(empty)));
        assertFalse(q.containsAll(Arrays.asList(ints)));
    }


    public void testToArray(){
        SynchronousQueue q = new SynchronousQueue();
	Object[] o = q.toArray();
        assertEquals(o.length, 0);
    }

    public void testToArray2(){
        SynchronousQueue q = new SynchronousQueue();
	Integer[] ints = new Integer[1];
        assertNull(ints[0]);
    }
    
    public void testIterator(){
        SynchronousQueue q = new SynchronousQueue();
	Iterator it = q.iterator();
        assertFalse(it.hasNext());
        try {
            Object x = it.next();
            fail("should throw");
        }
        catch (NoSuchElementException success) {}
    }

    public void testIteratorRemove(){
        SynchronousQueue q = new SynchronousQueue();
	Iterator it = q.iterator();
        try {
            it.remove();
            fail("should throw");
        }
        catch (IllegalStateException success) {}
    }

    public void testToString(){
        SynchronousQueue q = new SynchronousQueue();
        String s = q.toString();
        assertTrue(s != null);
    }        


    public void testOfferInExecutor() {
        final SynchronousQueue q = new SynchronousQueue();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        final Integer one = new Integer(1);

        executor.execute(new Runnable() {
            public void run() {
                assertFalse(q.offer(one));
                try {
                    assertTrue(q.offer(one, MEDIUM_DELAY_MS * 2, TimeUnit.MILLISECONDS));
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
                    assertEquals(one, q.take());
                }
                catch (InterruptedException e) {
                    fail("should not be interrupted");
                }
            }
        });
        
        executor.shutdown();

    }

    public void testPollInExecutor() {

        final SynchronousQueue q = new SynchronousQueue();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.execute(new Runnable() {
            public void run() {
                assertNull(q.poll());
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
        SynchronousQueue q = new SynchronousQueue();
        try {
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
        } catch(Exception e){
            fail("unexpected exception");
        }
    }

}
