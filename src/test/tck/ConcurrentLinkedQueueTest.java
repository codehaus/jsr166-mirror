/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class ConcurrentLinkedQueueTest extends TestCase {
    private static final int N = 10;
    private static final long SHORT_DELAY_MS = 100; 
    private static final long MEDIUM_DELAY_MS = 1000;
    private static final long LONG_DELAY_MS = 10000; 

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(ConcurrentLinkedQueueTest.class);
    }

    /**
     * Create a queue of given size containing consecutive
     * Integers 0 ... n.
     */
    private ConcurrentLinkedQueue fullQueue(int n) {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        assertTrue(q.isEmpty());
	for(int i = 0; i < n; ++i)
	    assertTrue(q.offer(new Integer(i)));
        assertFalse(q.isEmpty());
	assertEquals(n, q.size());
        return q;
    }
 
    public void testConstructor1(){
        assertEquals(0, new ConcurrentLinkedQueue().size());
    }

    public void testConstructor3() {
        try {
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue((Collection)null);
            fail("Cannot make from null collection");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor4(){
        try {
            Integer[] ints = new Integer[N];
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor5(){
        try {
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N-1; ++i)
                ints[i] = new Integer(i);
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor6(){
        try {
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue(Arrays.asList(ints));
            for (int i = 0; i < N; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testEmpty() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        assertTrue(q.isEmpty());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.add(new Integer(2));
        q.remove();
        q.remove();
        assertTrue(q.isEmpty());
    }

    public void testSize() {
        ConcurrentLinkedQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(N-i, q.size());
            q.remove();
        }
        for (int i = 0; i < N; ++i) {
            assertEquals(i, q.size());
            q.add(new Integer(i));
        }
    }

    public void testOfferNull(){
	try {
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
            q.offer(null);
            fail("should throw NPE");
        } catch (NullPointerException success) { }   
    }

    public void testOffer() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        assertTrue(q.offer(new Integer(0)));
        assertTrue(q.offer(new Integer(1)));
    }

    public void testAdd(){
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        for (int i = 0; i < N; ++i) {
            assertEquals(i, q.size());
            assertTrue(q.add(new Integer(i)));
        }
    }

    public void testAddAll1(){
        try {
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
            q.addAll(null);
            fail("Cannot add null collection");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll2(){
        try {
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
            Integer[] ints = new Integer[N];
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll3(){
        try {
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
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
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
            assertFalse(q.addAll(Arrays.asList(empty)));
            assertTrue(q.addAll(Arrays.asList(ints)));
            for (int i = 0; i < N; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testPoll(){
        ConcurrentLinkedQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, ((Integer)q.poll()).intValue());
        }
	assertNull(q.poll());
    }

    public void testPeek(){
        ConcurrentLinkedQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, ((Integer)q.peek()).intValue());
            q.poll();
            assertTrue(q.peek() == null ||
                       i != ((Integer)q.peek()).intValue());
        }
	assertNull(q.peek());
    }

    public void testElement(){
        ConcurrentLinkedQueue q = fullQueue(N);
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
        ConcurrentLinkedQueue q = fullQueue(N);
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
        ConcurrentLinkedQueue q = fullQueue(N);
        for (int i = 1; i < N; i+=2) {
            assertTrue(q.remove(new Integer(i)));
        }
        for (int i = 0; i < N; i+=2) {
            assertTrue(q.remove(new Integer(i)));
            assertFalse(q.remove(new Integer(i+1)));
        }
        assert(q.isEmpty());
    }
	
    public void testContains(){
        ConcurrentLinkedQueue q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.poll();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    public void testClear(){
        ConcurrentLinkedQueue q = fullQueue(N);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    public void testContainsAll(){
        ConcurrentLinkedQueue q = fullQueue(N);
        ConcurrentLinkedQueue p = new ConcurrentLinkedQueue();
        for (int i = 0; i < N; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    public void testRetainAll(){
        ConcurrentLinkedQueue q = fullQueue(N);
        ConcurrentLinkedQueue p = fullQueue(N);
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
            ConcurrentLinkedQueue q = fullQueue(N);
            ConcurrentLinkedQueue p = fullQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(N-i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer I = (Integer)(p.remove());
                assertFalse(q.contains(I));
            }
        }
    }

    public void testToArray(){
        ConcurrentLinkedQueue q = fullQueue(N);
	Object[] o = q.toArray();
        Arrays.sort(o);
	for(int i = 0; i < o.length; i++)
	    assertEquals(o[i], q.poll());
    }

    public void testToArray2(){
        ConcurrentLinkedQueue q = fullQueue(N);
	Integer[] ints = new Integer[N];
	ints = (Integer[])q.toArray(ints);
        Arrays.sort(ints);
        for(int i = 0; i < ints.length; i++)
            assertEquals(ints[i], q.poll());
    }
    
    public void testIterator(){
        ConcurrentLinkedQueue q = fullQueue(N);
        int i = 0;
	Iterator it = q.iterator();
        while(it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(i, N);
    }

    public void testIteratorOrdering() {

        final ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();

        q.add(new Integer(1));
        q.add(new Integer(2));
        q.add(new Integer(3));

        int k = 0;
        for (Iterator it = q.iterator(); it.hasNext();) {
            int i = ((Integer)(it.next())).intValue();
            assertEquals("items should come out in order", ++k, i);
        }

        assertEquals("should go through 3 elements", 3, k);
    }

    public void testWeaklyConsistentIteration () {

        final ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();

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

    public void testIteratorRemove () {

        final ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();

        q.add(new Integer(1));
        q.add(new Integer(2));
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
        ConcurrentLinkedQueue q = fullQueue(N);
        String s = q.toString();
        for (int i = 0; i < N; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        

}
