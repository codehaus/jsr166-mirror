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

public class PriorityQueueTest extends JSR166TestCase {

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(PriorityQueueTest.class);
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
    private PriorityQueue populatedQueue(int n) {
        PriorityQueue q = new PriorityQueue(n);
        assertTrue(q.isEmpty());
	for(int i = n-1; i >= 0; i-=2)
	    assertTrue(q.offer(new Integer(i)));
	for(int i = (n & 1); i < n; i+=2)
	    assertTrue(q.offer(new Integer(i)));
        assertFalse(q.isEmpty());
	assertEquals(n, q.size());
        return q;
    }
 
    public void testConstructor1(){
        assertEquals(0, new PriorityQueue(SIZE).size());
    }

    public void testConstructor2(){
        try {
            PriorityQueue q = new PriorityQueue(0);
            fail("Cannot make zero-sized");
        }
        catch (IllegalArgumentException success) {}
    }

    public void testConstructor3() {

        try {
            PriorityQueue q = new PriorityQueue((Collection)null);
            fail("Cannot make from null collection");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor4(){
        try {
            Integer[] ints = new Integer[SIZE];
            PriorityQueue q = new PriorityQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor5(){
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE-1; ++i)
                ints[i] = new Integer(i);
            PriorityQueue q = new PriorityQueue(Arrays.asList(ints));
            fail("Cannot make with null elements");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor6(){
        try {
            Integer[] ints = new Integer[SIZE];
            for (int i = 0; i < SIZE; ++i)
                ints[i] = new Integer(i);
            PriorityQueue q = new PriorityQueue(Arrays.asList(ints));
            for (int i = 0; i < SIZE; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testConstructor7(){
        try {
            MyReverseComparator cmp = new MyReverseComparator();
            PriorityQueue q = new PriorityQueue(SIZE, cmp);
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

    public void testEmpty() {
        PriorityQueue q = new PriorityQueue(2);
        assertTrue(q.isEmpty());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.add(new Integer(2));
        q.remove();
        q.remove();
        assertTrue(q.isEmpty());
    }

    public void testSize() {
        PriorityQueue q = populatedQueue(SIZE);
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
            PriorityQueue q = new PriorityQueue(1);
            q.offer(null);
            fail("should throw NPE");
        } catch (NullPointerException success) { }   
    }

    public void testOffer() {
        PriorityQueue q = new PriorityQueue(1);
        assertTrue(q.offer(new Integer(0)));
        assertTrue(q.offer(new Integer(1)));
    }

    public void testOfferNonComparable() {
        try {
            PriorityQueue q = new PriorityQueue(1);
            q.offer(new Object());
            q.offer(new Object());
            q.offer(new Object());
            fail("should throw CCE");
        }
        catch(ClassCastException success) {}
    }

    public void testAdd(){
        PriorityQueue q = new PriorityQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, q.size());
            assertTrue(q.add(new Integer(i)));
        }
    }

    public void testAddAll1(){
        try {
            PriorityQueue q = new PriorityQueue(1);
            q.addAll(null);
            fail("Cannot add null collection");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll2(){
        try {
            PriorityQueue q = new PriorityQueue(SIZE);
            Integer[] ints = new Integer[SIZE];
            q.addAll(Arrays.asList(ints));
            fail("Cannot add null elements");
        }
        catch (NullPointerException success) {}
    }
    public void testAddAll3(){
        try {
            PriorityQueue q = new PriorityQueue(SIZE);
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
                ints[i] = new Integer(SIZE-1-i);
            PriorityQueue q = new PriorityQueue(SIZE);
            assertFalse(q.addAll(Arrays.asList(empty)));
            assertTrue(q.addAll(Arrays.asList(ints)));
            for (int i = 0; i < SIZE; ++i)
                assertEquals(new Integer(i), q.poll());
        }
        finally {}
    }

    public void testPoll(){
        PriorityQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.poll()).intValue());
        }
	assertNull(q.poll());
    }

    public void testPeek(){
        PriorityQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, ((Integer)q.peek()).intValue());
            q.poll();
            assertTrue(q.peek() == null ||
                       i != ((Integer)q.peek()).intValue());
        }
	assertNull(q.peek());
    }

    public void testElement(){
        PriorityQueue q = populatedQueue(SIZE);
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
        PriorityQueue q = populatedQueue(SIZE);
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
        PriorityQueue q = populatedQueue(SIZE);
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
        PriorityQueue q = populatedQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.poll();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    public void testClear(){
        PriorityQueue q = populatedQueue(SIZE);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    public void testContainsAll(){
        PriorityQueue q = populatedQueue(SIZE);
        PriorityQueue p = new PriorityQueue(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    public void testRetainAll(){
        PriorityQueue q = populatedQueue(SIZE);
        PriorityQueue p = populatedQueue(SIZE);
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
            PriorityQueue q = populatedQueue(SIZE);
            PriorityQueue p = populatedQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(SIZE-i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer I = (Integer)(p.remove());
                assertFalse(q.contains(I));
            }
        }
    }

    public void testToArray(){
        PriorityQueue q = populatedQueue(SIZE);
	Object[] o = q.toArray();
        Arrays.sort(o);
	for(int i = 0; i < o.length; i++)
	    assertEquals(o[i], q.poll());
    }

    public void testToArray2(){
        PriorityQueue q = populatedQueue(SIZE);
	Integer[] ints = new Integer[SIZE];
	ints = (Integer[])q.toArray(ints);
        Arrays.sort(ints);
        for(int i = 0; i < ints.length; i++)
            assertEquals(ints[i], q.poll());
    }
    
    public void testIterator(){
        PriorityQueue q = populatedQueue(SIZE);
        int i = 0;
	Iterator it = q.iterator();
        while(it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(i, SIZE);
    }

    public void testIteratorRemove () {

        final PriorityQueue q = new PriorityQueue(3);

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
        PriorityQueue q = populatedQueue(SIZE);
        String s = q.toString();
        for (int i = 0; i < SIZE; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        

    public void testSerialization() {
        PriorityQueue q = populatedQueue(SIZE);
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(q);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            PriorityQueue r = (PriorityQueue)in.readObject();
            assertEquals(q.size(), r.size());
            while (!q.isEmpty()) 
                assertEquals(q.remove(), r.remove());
        } catch(Exception e){
            fail("unexpected exception");
        }
    }
}
