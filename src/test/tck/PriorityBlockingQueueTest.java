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

public class PriorityBlockingQueueTest extends TestCase {

    private static final int N = 10;
    private static final long SHORT_DELAY_MS = 100; 
    private static final long MEDIUM_DELAY_MS = 1000;
    private static final long LONG_DELAY_MS = 10000; 
    private static final int NOCAP = Integer.MAX_VALUE;

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(PriorityBlockingQueueTest.class);
    }

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
    private PriorityBlockingQueue fullQueue(int n) {
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
 
    public void testConstructor1(){
        assertEquals(NOCAP, new PriorityBlockingQueue(N).remainingCapacity());
    }

    public void testConstructor2(){
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(0);
            fail("Cannot make zero-sized");
        }
        catch (IllegalArgumentException success) {}
    }

    public void testConstructor3(){

        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(null);
            fail("Cannot make from null collection");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor4(){
        try {
            Integer[] ints = new Integer[N];
            PriorityBlockingQueue q = new PriorityBlockingQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor5(){
        try {
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N-1; ++i)
                ints[i] = new Integer(i);
            PriorityBlockingQueue q = new PriorityBlockingQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor6(){
        try {
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            PriorityBlockingQueue q = new PriorityBlockingQueue(Arrays.asList(ints));
            for (int i = 0; i < N; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testConstructor7(){
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(N, new MyReverseComparator());
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            for (int i = N-1; i >= 0; --i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

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

    public void testRemainingCapacity(){
        PriorityBlockingQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(NOCAP, q.remainingCapacity());
            assertEquals(N-i, q.size());
            q.remove();
        }
        for (int i = 0; i < N; ++i) {
            assertEquals(NOCAP, q.remainingCapacity());
            assertEquals(i, q.size());
            q.add(new Integer(i));
        }
    }

    public void testOfferNull(){
	try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(1);
            q.offer(null);
            fail("should throw NPE");
        } catch (NullPointerException success) { }   
    }

    public void testOffer() {
        PriorityBlockingQueue q = new PriorityBlockingQueue(1);
        assertTrue(q.offer(new Integer(0)));
        assertTrue(q.offer(new Integer(1)));
    }

    public void testOfferNonComparable() {
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(1);
            q.offer(new Object());
            q.offer(new Object());
            q.offer(new Object());
            fail("should throw CCE");
        }
        catch(ClassCastException success) {}
    }

    public void testAdd(){
        PriorityBlockingQueue q = new PriorityBlockingQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, q.size());
            assertTrue(q.add(new Integer(i)));
        }
    }

    public void testAddAll1(){
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(1);
            q.addAll(null);
            fail("Cannot add null collection");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll2(){
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(N);
            Integer[] ints = new Integer[N];
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll3(){
        try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(N);
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N-1; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testAddAll5(){
        try {
            Integer[] empty = new Integer[0];
            Integer[] ints = new Integer[N];
            for (int i = N-1; i >= 0; --i)
                ints[i] = new Integer(i);
            PriorityBlockingQueue q = new PriorityBlockingQueue(N);
            assertFalse(q.addAll(Arrays.asList(empty)));
            assertTrue(q.addAll(Arrays.asList(ints)));
            for (int i = 0; i < N; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

     public void testPutNull() {
	try {
            PriorityBlockingQueue q = new PriorityBlockingQueue(N);
            q.put(null);
            fail("put should throw NPE");
        } 
        catch (NullPointerException success){
	}   
     }

     public void testPut() {
         try {
             PriorityBlockingQueue q = new PriorityBlockingQueue(N);
             for (int i = 0; i < N; ++i) {
                 Integer I = new Integer(i);
                 q.put(I);
                 assertTrue(q.contains(I));
             }
             assertEquals(N, q.size());
         }
         finally {
        }
    }

    public void testPutWithTake() {
        final PriorityBlockingQueue q = new PriorityBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run(){
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
                        assertTrue(added == 4);
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
            fail("Unexpected exception");
        }
    }

    public void testTimedOffer() {
        final PriorityBlockingQueue q = new PriorityBlockingQueue(2);
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try {
                        q.put(new Integer(0));
                        q.put(new Integer(0));
                        assertTrue(q.offer(new Integer(0), SHORT_DELAY_MS/2, TimeUnit.MILLISECONDS));
                        assertTrue(q.offer(new Integer(0), LONG_DELAY_MS, TimeUnit.MILLISECONDS));
                    } finally { }
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
            PriorityBlockingQueue q = fullQueue(N);
            for (int i = 0; i < N; ++i) {
                assertEquals(i, ((Integer)q.take()).intValue());
            }
        } catch (InterruptedException e){
	    fail("Unexpected exception");
	}   
    }

    public void testTakeFromEmpty() {
        final PriorityBlockingQueue q = new PriorityBlockingQueue(2);
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
                        PriorityBlockingQueue q = fullQueue(N);
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
        PriorityBlockingQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, ((Integer)q.poll()).intValue());
        }
	assertNull(q.poll());
    }

    public void testTimedPoll0() {
        try {
            PriorityBlockingQueue q = fullQueue(N);
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
            PriorityBlockingQueue q = fullQueue(N);
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
                        PriorityBlockingQueue q = fullQueue(N);
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
        final PriorityBlockingQueue q = new PriorityBlockingQueue(2);
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
        PriorityBlockingQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, ((Integer)q.peek()).intValue());
            q.poll();
            assertTrue(q.peek() == null ||
                       i != ((Integer)q.peek()).intValue());
        }
	assertNull(q.peek());
    }

    public void testElement(){
        PriorityBlockingQueue q = fullQueue(N);
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
        PriorityBlockingQueue q = fullQueue(N);
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
        PriorityBlockingQueue q = fullQueue(N);
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
        PriorityBlockingQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.poll();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    public void testClear(){
        PriorityBlockingQueue q = fullQueue(N);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(NOCAP, q.remainingCapacity());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    public void testContainsAll(){
        PriorityBlockingQueue q = fullQueue(N);
        PriorityBlockingQueue p = new PriorityBlockingQueue(N);
        for (int i = 0; i < N; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    public void testRetainAll(){
        PriorityBlockingQueue q = fullQueue(N);
        PriorityBlockingQueue p = fullQueue(N);
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
            PriorityBlockingQueue q = fullQueue(N);
            PriorityBlockingQueue p = fullQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(N-i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer I = (Integer)(p.remove());
                assertFalse(q.contains(I));
            }
        }
    }

    public void testToArray(){
        PriorityBlockingQueue q = fullQueue(N);
	Object[] o = q.toArray();
        Arrays.sort(o);
	try {
	for(int i = 0; i < o.length; i++)
	    assertEquals(o[i], q.take());
	} catch (InterruptedException e){
	    fail("Unexpected exception");
	}    
    }

    public void testToArray2(){
        PriorityBlockingQueue q = fullQueue(N);
	Integer[] ints = new Integer[N];
	ints = (Integer[])q.toArray(ints);
        Arrays.sort(ints);
	try {
	    for(int i = 0; i < ints.length; i++)
		assertEquals(ints[i], q.take());
	} catch (InterruptedException e){
	    fail("Unexpected exception");
	}    
    }
    
    public void testIterator(){
        PriorityBlockingQueue q = fullQueue(N);
        int i = 0;
	Iterator it = q.iterator();
        while(it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(i, N);
    }

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


    public void testToString(){
        PriorityBlockingQueue q = fullQueue(N);
        String s = q.toString();
        for (int i = 0; i < N; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        

    public void testPollInExecutor() {

        final PriorityBlockingQueue q = new PriorityBlockingQueue(2);

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
        PriorityBlockingQueue q = fullQueue(N);
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
            fail("unexpected exception");
        }
    }

}
