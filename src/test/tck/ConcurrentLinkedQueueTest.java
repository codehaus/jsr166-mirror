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

public class ConcurrentLinkedQueueTest extends JSR166TestCase {

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
    private ConcurrentLinkedQueue populatedQueue(int n) {
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
            Integer[] ints = new Integer[SIZE];
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor5(){
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE-1; ++i)
                ints[i] = new Integer(i);
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor6(){
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i)
                ints[i] = new Integer(i);
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue(Arrays.asList(ints));
            for (int i = 0; i < SIZE; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testEmpty() {
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        assertTrue(q.isEmpty());
        q.add(one);
        assertFalse(q.isEmpty());
        q.add(two);
        q.remove();
        q.remove();
        assertTrue(q.isEmpty());
    }

    public void testSize() {
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(SIZE-i, q.size());
            q.remove();
        }
        for (int i = 0; i < SIZE; ++i) {
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
        assertTrue(q.offer(zero));
        assertTrue(q.offer(one));
    }

    public void testAdd(){
        ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        for (int i = 0; i < SIZE; ++i) {
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
            Integer[] ints = new Integer[SIZE];
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll3(){
        try {
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE-1; ++i)
                ints[i] = new Integer(i);
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testAddAll5(){
        try {
            Integer[] empty = new Integer[0];
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i)
                ints[i] = new Integer(i);
            ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
            assertFalse(q.addAll(Arrays.asList(empty)));
            assertTrue(q.addAll(Arrays.asList(ints)));
            for (int i = 0; i < SIZE; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testPoll(){
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.poll()).intValue());
        }
	assertNull(q.poll());
    }

    public void testPeek(){
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.peek()).intValue());
            q.poll();
            assertTrue(q.peek() == null ||
                       i != ((Integer)q.peek()).intValue());
        }
	assertNull(q.peek());
    }

    public void testElement(){
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
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
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
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
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
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
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.poll();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    public void testClear(){
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        q.add(one);
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    public void testContainsAll(){
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
        ConcurrentLinkedQueue p = new ConcurrentLinkedQueue();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    public void testRetainAll(){
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
        ConcurrentLinkedQueue p = populatedQueue(SIZE);
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
            ConcurrentLinkedQueue q = populatedQueue(SIZE);
            ConcurrentLinkedQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE-i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer I = (Integer)(p.remove());
                assertFalse(q.contains(I));
            }
        }
    }

    public void testToArray(){
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
	Object[] o = q.toArray();
        Arrays.sort(o);
	for(int i = 0; i < o.length; i++)
	    assertEquals(o[i], q.poll());
    }

    public void testToArray2(){
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
	Integer[] ints = new Integer[SIZE];
	ints = (Integer[])q.toArray(ints);
        Arrays.sort(ints);
        for(int i = 0; i < ints.length; i++)
            assertEquals(ints[i], q.poll());
    }
    
    public void testIterator(){
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
        int i = 0;
	Iterator it = q.iterator();
        while(it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(i, SIZE);
    }

    public void testIteratorOrdering() {
        final ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        q.add(one);
        q.add(two);
        q.add(three);

        int k = 0;
        for (Iterator it = q.iterator(); it.hasNext();) {
            int i = ((Integer)(it.next())).intValue();
            assertEquals("items should come out in order", ++k, i);
        }

        assertEquals("should go through 3 elements", 3, k);
    }

    public void testWeaklyConsistentIteration () {
        final ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
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

    public void testIteratorRemove () {
        final ConcurrentLinkedQueue q = new ConcurrentLinkedQueue();
        q.add(one);
        q.add(two);
        q.add(three);
        Iterator it = q.iterator();
        it.next();
        it.remove();
        it = q.iterator();
        assertEquals(it.next(), two);
        assertEquals(it.next(), three);
        assertFalse(it.hasNext());
    }


    public void testToString(){
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        

    public void testSerialization() {
        ConcurrentLinkedQueue q = populatedQueue(SIZE);
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(q);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            ConcurrentLinkedQueue r = (ConcurrentLinkedQueue)in.readObject();
            assertEquals(q.size(), r.size());
            while (!q.isEmpty()) 
                assertEquals(q.remove(), r.remove());
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }

}
