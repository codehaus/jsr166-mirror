/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class DelayQueueTest extends TestCase {

    private static final int N = 10;
    private static final long SHORT_DELAY_MS = 100; 
    private static final long MEDIUM_DELAY_MS = 1000;
    private static final long LONG_DELAY_MS = 10000; 
    private static final int NOCAP = Integer.MAX_VALUE;

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(DelayQueueTest.class);
    }

    // Most Q/BQ tests use Pseudodelays, where delays are all elapsed
    // (so, no blocking solely for delays) but are still ordered

    static class PDelay implements Delayed { 
        int pseudodelay;
        PDelay(int i) { pseudodelay = Integer.MIN_VALUE + i; }
        public int compareTo(Object y) {
            int i = pseudodelay;
            int j = ((PDelay)y).pseudodelay;
            if (i < j) return -1;
            if (i > j) return 1;
            return 0;
        }

        public int compareTo(PDelay y) {
            int i = pseudodelay;
            int j = ((PDelay)y).pseudodelay;
            if (i < j) return -1;
            if (i > j) return 1;
            return 0;
        }

        public boolean equals(Object other) {
            return ((PDelay)other).pseudodelay == pseudodelay;
        }
        public boolean equals(PDelay other) {
            return ((PDelay)other).pseudodelay == pseudodelay;
        }


        public long getDelay(TimeUnit ignore) {
            return pseudodelay;
        }
        public int intValue() {
            return pseudodelay;
        }

        public String toString() {
            return String.valueOf(pseudodelay);
        }
    }




    /**
     * Create a queue of given size containing consecutive
     * PDelays 0 ... n.
     */
    private DelayQueue fullQueue(int n) {
        DelayQueue q = new DelayQueue();
        assertTrue(q.isEmpty());
	for(int i = n-1; i >= 0; i-=2)
	    assertTrue(q.offer(new PDelay(i)));
	for(int i = (n & 1); i < n; i+=2)
	    assertTrue(q.offer(new PDelay(i)));
        assertFalse(q.isEmpty());
        assertEquals(NOCAP, q.remainingCapacity());
	assertEquals(n, q.size());
        return q;
    }
 
    public void testConstructor1(){
        assertEquals(NOCAP, new DelayQueue().remainingCapacity());
    }

    public void testConstructor3(){

        try {
            DelayQueue q = new DelayQueue(null);
            fail("Cannot make from null collection");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor4(){
        try {
            PDelay[] ints = new PDelay[N];
            DelayQueue q = new DelayQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor5(){
        try {
            PDelay[] ints = new PDelay[N];
            for (int i = 0; i < N-1; ++i)
                ints[i] = new PDelay(i);
            DelayQueue q = new DelayQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor6(){
        try {
            PDelay[] ints = new PDelay[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new PDelay(i);
            DelayQueue q = new DelayQueue(Arrays.asList(ints));
            for (int i = 0; i < N; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testEmpty() {
        DelayQueue q = new DelayQueue();
        assertTrue(q.isEmpty());
        assertEquals(NOCAP, q.remainingCapacity());
        q.add(new PDelay(1));
        assertFalse(q.isEmpty());
        q.add(new PDelay(2));
        q.remove();
        q.remove();
        assertTrue(q.isEmpty());
    }

    public void testRemainingCapacity(){
        DelayQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(NOCAP, q.remainingCapacity());
            assertEquals(N-i, q.size());
            q.remove();
        }
        for (int i = 0; i < N; ++i) {
            assertEquals(NOCAP, q.remainingCapacity());
            assertEquals(i, q.size());
            q.add(new PDelay(i));
        }
    }

    public void testOfferNull(){
	try {
            DelayQueue q = new DelayQueue();
            q.offer(null);
            fail("should throw NPE");
        } catch (NullPointerException success) { }   
    }

    public void testOffer() {
        DelayQueue q = new DelayQueue();
        assertTrue(q.offer(new PDelay(0)));
        assertTrue(q.offer(new PDelay(1)));
    }

    public void testAdd(){
        DelayQueue q = new DelayQueue();
        for (int i = 0; i < N; ++i) {
            assertEquals(i, q.size());
            assertTrue(q.add(new PDelay(i)));
        }
    }

    public void testAddAll1(){
        try {
            DelayQueue q = new DelayQueue();
            q.addAll(null);
            fail("Cannot add null collection");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll2(){
        try {
            DelayQueue q = new DelayQueue();
            PDelay[] ints = new PDelay[N];
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll3(){
        try {
            DelayQueue q = new DelayQueue();
            PDelay[] ints = new PDelay[N];
            for (int i = 0; i < N-1; ++i)
                ints[i] = new PDelay(i);
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testAddAll5(){
        try {
            PDelay[] empty = new PDelay[0];
            PDelay[] ints = new PDelay[N];
            for (int i = N-1; i >= 0; --i)
                ints[i] = new PDelay(i);
            DelayQueue q = new DelayQueue();
            assertFalse(q.addAll(Arrays.asList(empty)));
            assertTrue(q.addAll(Arrays.asList(ints)));
            for (int i = 0; i < N; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

     public void testPutNull() {
	try {
            DelayQueue q = new DelayQueue();
            q.put(null);
            fail("put should throw NPE");
        } 
        catch (NullPointerException success){
	}   
     }

     public void testPut() {
         try {
             DelayQueue q = new DelayQueue();
             for (int i = 0; i < N; ++i) {
                 PDelay I = new PDelay(i);
                 q.put(I);
                 assertTrue(q.contains(I));
             }
             assertEquals(N, q.size());
         }
         finally {
        }
    }

    public void testPutWithTake() {
        final DelayQueue q = new DelayQueue();
        Thread t = new Thread(new Runnable() {
                public void run(){
                    int added = 0;
                    try {
                        q.put(new PDelay(0));
                        ++added;
                        q.put(new PDelay(0));
                        ++added;
                        q.put(new PDelay(0));
                        ++added;
                        q.put(new PDelay(0));
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
        final DelayQueue q = new DelayQueue();
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try {
                        q.put(new PDelay(0));
                        q.put(new PDelay(0));
                        assertTrue(q.offer(new PDelay(0), SHORT_DELAY_MS/2, TimeUnit.MILLISECONDS));
                        assertTrue(q.offer(new PDelay(0), LONG_DELAY_MS, TimeUnit.MILLISECONDS));
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
            DelayQueue q = fullQueue(N);
            for (int i = 0; i < N; ++i) {
                assertEquals(new PDelay(i), ((PDelay)q.take()));
            }
        } catch (InterruptedException e){
	    fail("Unexpected exception");
	}   
    }

    public void testTakeFromEmpty() {
        final DelayQueue q = new DelayQueue();
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
                        DelayQueue q = fullQueue(N);
                        for (int i = 0; i < N; ++i) {
                            assertEquals(new PDelay(i), ((PDelay)q.take()));
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
        DelayQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(new PDelay(i), ((PDelay)q.poll()));
        }
	assertNull(q.poll());
    }

    public void testTimedPoll0() {
        try {
            DelayQueue q = fullQueue(N);
            for (int i = 0; i < N; ++i) {
                assertEquals(new PDelay(i), ((PDelay)q.poll(0, TimeUnit.MILLISECONDS)));
            }
            assertNull(q.poll(0, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e){
	    fail("Unexpected exception");
	}   
    }

    public void testTimedPoll() {
        try {
            DelayQueue q = fullQueue(N);
            for (int i = 0; i < N; ++i) {
                assertEquals(new PDelay(i), ((PDelay)q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS)));
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
                        DelayQueue q = fullQueue(N);
                        for (int i = 0; i < N; ++i) {
                            assertEquals(new PDelay(i), ((PDelay)q.poll(SHORT_DELAY_MS, TimeUnit.MILLISECONDS)));
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
        final DelayQueue q = new DelayQueue();
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
            assertTrue(q.offer(new PDelay(0), SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            t.interrupt();
            t.join();
        } catch (Exception e){
            fail("Unexpected exception");
        }
    }  


    public void testPeek(){
        DelayQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(new PDelay(i), ((PDelay)q.peek()));
            q.poll();
            assertTrue(q.peek() == null ||
                       i != ((PDelay)q.peek()).intValue());
        }
	assertNull(q.peek());
    }

    public void testElement(){
        DelayQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(new PDelay(i), ((PDelay)q.element()));
            q.poll();
        }
        try {
            q.element();
            fail("no such element");
        }
        catch (NoSuchElementException success) {}
    }

    public void testRemove(){
        DelayQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(new PDelay(i), ((PDelay)q.remove()));
        }
        try {
            q.remove();
            fail("remove should throw");
        } catch (NoSuchElementException success){
	}   
    }

    public void testRemoveElement(){
        DelayQueue q = fullQueue(N);
        for (int i = 1; i < N; i+=2) {
            assertTrue(q.remove(new PDelay(i)));
        }
        for (int i = 0; i < N; i+=2) {
            assertTrue(q.remove(new PDelay(i)));
            assertFalse(q.remove(new PDelay(i+1)));
        }
        assertTrue(q.isEmpty());
    }
	
    public void testContains(){
        DelayQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertTrue(q.contains(new PDelay(i)));
            q.poll();
            assertFalse(q.contains(new PDelay(i)));
        }
    }

    public void testClear(){
        DelayQueue q = fullQueue(N);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        assertEquals(NOCAP, q.remainingCapacity());
        q.add(new PDelay(1));
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    public void testContainsAll(){
        DelayQueue q = fullQueue(N);
        DelayQueue p = new DelayQueue();
        for (int i = 0; i < N; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new PDelay(i));
        }
        assertTrue(p.containsAll(q));
    }

    public void testRetainAll(){
        DelayQueue q = fullQueue(N);
        DelayQueue p = fullQueue(N);
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
            DelayQueue q = fullQueue(N);
            DelayQueue p = fullQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(N-i, q.size());
            for (int j = 0; j < i; ++j) {
                PDelay I = (PDelay)(p.remove());
                assertFalse(q.contains(I));
            }
        }
    }

    /*
    public void testEqualsAndHashCode(){
        DelayQueue q1 = fullQueue(N);
        DelayQueue q2 = fullQueue(N);
        assertTrue(q1.equals(q2));
        assertTrue(q2.equals(q1));
        assertEquals(q1.hashCode(), q2.hashCode());
        q1.remove();
        assertFalse(q1.equals(q2));
        assertFalse(q2.equals(q1));
        assertFalse(q1.hashCode() == q2.hashCode());
        q2.remove();
        assertTrue(q1.equals(q2));
        assertTrue(q2.equals(q1));
        assertEquals(q1.hashCode(), q2.hashCode());
    }
    */

    public void testToArray(){
        DelayQueue q = fullQueue(N);
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
        DelayQueue q = fullQueue(N);
	PDelay[] ints = new PDelay[N];
	ints = (PDelay[])q.toArray(ints);
        Arrays.sort(ints);
	try {
	    for(int i = 0; i < ints.length; i++)
		assertEquals(ints[i], q.take());
	} catch (InterruptedException e){
	    fail("Unexpected exception");
	}    
    }
    
    public void testIterator(){
        DelayQueue q = fullQueue(N);
        int i = 0;
	Iterator it = q.iterator();
        while(it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(i, N);
    }

    public void testIteratorRemove () {

        final DelayQueue q = new DelayQueue();

        q.add(new PDelay(2));
        q.add(new PDelay(1));
        q.add(new PDelay(3));

        Iterator it = q.iterator();
        it.next();
        it.remove();

        it = q.iterator();
        assertEquals(it.next(), new PDelay(2));
        assertEquals(it.next(), new PDelay(3));
        assertFalse(it.hasNext());
    }


    public void testToString(){
        DelayQueue q = fullQueue(N);
        String s = q.toString();
        for (int i = 0; i < N; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        

    public void testPollInExecutor() {

        final DelayQueue q = new DelayQueue();

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
                    q.put(new PDelay(1));
                }
                catch (InterruptedException e) {
                    fail("should not be interrupted");
                }
            }
        });
        
        executor.shutdown();

    }

    static class NanoDelay implements Delayed { 
        long trigger;
        NanoDelay(long i) { 
            trigger = System.nanoTime() + i;
        }
        public int compareTo(Object y) {
            long i = trigger;
            long j = ((NanoDelay)y).trigger;
            if (i < j) return -1;
            if (i > j) return 1;
            return 0;
        }

        public int compareTo(NanoDelay y) {
            long i = trigger;
            long j = ((NanoDelay)y).trigger;
            if (i < j) return -1;
            if (i > j) return 1;
            return 0;
        }

        public boolean equals(Object other) {
            return ((NanoDelay)other).trigger == trigger;
        }
        public boolean equals(NanoDelay other) {
            return ((NanoDelay)other).trigger == trigger;
        }

        public long getDelay(TimeUnit unit) {
            long n = trigger - System.nanoTime();
            return unit.convert(n, TimeUnit.NANOSECONDS);
        }

        public long getTriggerTime() {
            return trigger;
        }

        public String toString() {
            return String.valueOf(trigger);
        }
    }

    public void testDelay() {
        DelayQueue q = new DelayQueue();
        NanoDelay[] elements = new NanoDelay[N];
        for (int i = 0; i < N; ++i) {
            elements[i] = new NanoDelay(1000000000L + 1000000L * (N - i));
        }
        for (int i = 0; i < N; ++i) {
            q.add(elements[i]);
        }

        try {
            long last = 0;
            for (int i = 0; i < N; ++i) {
                NanoDelay e = (NanoDelay)(q.take());
                long tt = e.getTriggerTime();
                assertTrue(tt <= System.nanoTime());
                if (i != 0) 
                    assertTrue(tt >= last);
                last = tt;
            }
        }
        catch(InterruptedException ie) {
            fail("Unexpected Exception");
        }
    }

}
