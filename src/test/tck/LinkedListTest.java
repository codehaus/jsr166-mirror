/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class LinkedListTest extends TestCase {

    private static final int N = 10;
    private static final long SHORT_DELAY_MS = 100; 
    private static final long MEDIUM_DELAY_MS = 1000;
    private static final long LONG_DELAY_MS = 10000; 

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(LinkedListTest.class);
    }

    /**
     * Create a queue of given size containing consecutive
     * Integers 0 ... n.
     */
    private LinkedList fullQueue(int n) {
        LinkedList q = new LinkedList();
        assertTrue(q.isEmpty());
	for(int i = 0; i < n; ++i)
	    assertTrue(q.offer(new Integer(i)));
        assertFalse(q.isEmpty());
	assertEquals(n, q.size());
        return q;
    }
 
    public void testConstructor1(){
        assertEquals(0, new LinkedList().size());
    }

    public void testConstructor3() {
        try {
            LinkedList q = new LinkedList((Collection)null);
            fail("Cannot make from null collection");
        }
        catch (NullPointerException success) {}
    }

    public void testConstructor6(){
        try {
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            LinkedList q = new LinkedList(Arrays.asList(ints));
            for (int i = 0; i < N; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testEmpty() {
        LinkedList q = new LinkedList();
        assertTrue(q.isEmpty());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.add(new Integer(2));
        q.remove();
        q.remove();
        assertTrue(q.isEmpty());
    }

    public void testSize() {
        LinkedList q = fullQueue(N);
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
            LinkedList q = new LinkedList();
            q.offer(null);
        } catch (NullPointerException ie) { 
            fail("should not throw NPE");
        }   
    }

    public void testOffer() {
        LinkedList q = new LinkedList();
        assertTrue(q.offer(new Integer(0)));
        assertTrue(q.offer(new Integer(1)));
    }

    public void testAdd(){
        LinkedList q = new LinkedList();
        for (int i = 0; i < N; ++i) {
            assertEquals(i, q.size());
            assertTrue(q.add(new Integer(i)));
        }
    }

    public void testAddAll1(){
        try {
            LinkedList q = new LinkedList();
            q.addAll(null);
            fail("Cannot add null collection");
        }
        catch (NullPointerException success) {}
    }

    public void testAddAll5(){
        try {
            Integer[] empty = new Integer[0];
            Integer[] ints = new Integer[N];
            for (int i = 0; i < N; ++i)
                ints[i] = new Integer(i);
            LinkedList q = new LinkedList();
            assertFalse(q.addAll(Arrays.asList(empty)));
            assertTrue(q.addAll(Arrays.asList(ints)));
            for (int i = 0; i < N; ++i)
                assertEquals(ints[i], q.poll());
        }
        finally {}
    }

    public void testPoll(){
        LinkedList q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, ((Integer)q.poll()).intValue());
        }
	assertNull(q.poll());
    }

    public void testPeek(){
        LinkedList q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertEquals(i, ((Integer)q.peek()).intValue());
            q.poll();
            assertTrue(q.peek() == null ||
                       i != ((Integer)q.peek()).intValue());
        }
	assertNull(q.peek());
    }

    public void testElement(){
        LinkedList q = fullQueue(N);
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
        LinkedList q = fullQueue(N);
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
        LinkedList q = fullQueue(N);
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
        LinkedList q = fullQueue(N);
        for (int i = 0; i < N; ++i) {
            assertTrue(q.contains(new Integer(i)));
            q.poll();
            assertFalse(q.contains(new Integer(i)));
        }
    }

    public void testClear(){
        LinkedList q = fullQueue(N);
        q.clear();
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
        q.add(new Integer(1));
        assertFalse(q.isEmpty());
        q.clear();
        assertTrue(q.isEmpty());
    }

    public void testContainsAll(){
        LinkedList q = fullQueue(N);
        LinkedList p = new LinkedList();
        for (int i = 0; i < N; ++i) {
            assertTrue(q.containsAll(p));
            assertFalse(p.containsAll(q));
            p.add(new Integer(i));
        }
        assertTrue(p.containsAll(q));
    }

    public void testRetainAll(){
        LinkedList q = fullQueue(N);
        LinkedList p = fullQueue(N);
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
            LinkedList q = fullQueue(N);
            LinkedList p = fullQueue(i);
            assertTrue(q.removeAll(p));
            assertEquals(N-i, q.size());
            for (int j = 0; j < i; ++j) {
                Integer I = (Integer)(p.remove());
                assertFalse(q.contains(I));
            }
        }
    }

    public void testToArray(){
        LinkedList q = fullQueue(N);
	Object[] o = q.toArray();
        Arrays.sort(o);
	for(int i = 0; i < o.length; i++)
	    assertEquals(o[i], q.poll());
    }

    public void testToArray2(){
        LinkedList q = fullQueue(N);
	Integer[] ints = new Integer[N];
	ints = (Integer[])q.toArray(ints);
        Arrays.sort(ints);
        for(int i = 0; i < ints.length; i++)
            assertEquals(ints[i], q.poll());
    }
    
    public void testIterator(){
        LinkedList q = fullQueue(N);
        int i = 0;
	Iterator it = q.iterator();
        while(it.hasNext()) {
            assertTrue(q.contains(it.next()));
            ++i;
        }
        assertEquals(i, N);
    }

    public void testIteratorOrdering() {

        final LinkedList q = new LinkedList();

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

    public void testIteratorRemove () {
        final LinkedList q = new LinkedList();

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
        LinkedList q = fullQueue(N);
        String s = q.toString();
        for (int i = 0; i < N; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        

    public void testAddFirst(){
        LinkedList q = fullQueue(3);
	q.addFirst(new Integer(4));
	assertEquals(new Integer(4),q.get(0));
    }	

    public void testAddLast(){
        LinkedList q = fullQueue(3);
	q.addLast(new Integer(3));
	assertEquals(new Integer(3),q.get(3));
    }
    
    public void testGetFirst() {
        LinkedList q = fullQueue(3);
        assertEquals(new Integer(0),q.getFirst());
    }	
    
    public void testGetLast() {
        LinkedList q = fullQueue(3);
        assertEquals(new Integer(2),q.getLast());
    }
    
    public void testIndexOf(){
        LinkedList q = fullQueue(3);
	assertEquals(0,q.indexOf(new Integer(0)));
	assertEquals(1,q.indexOf(new Integer(1)));
	assertEquals(2,q.indexOf(new Integer(2)));
        assertEquals(-1, q.indexOf("not there")); 
    }

    public void testLastIndexOf(){
        LinkedList q = fullQueue(3);
        q.add(new Integer(2));
	assertEquals(3,q.lastIndexOf(new Integer(2)));
        assertEquals(-1, q.lastIndexOf("not there"));
    }
    
    public void testSet(){
        LinkedList q = fullQueue(3);
	q.set(0,(new Integer(1)));
	assertFalse(q.contains(new Integer(0)));
	assertEquals(new Integer(1), q.get(0));
    }
    

    public void testGetFirst_NoSuchElementException(){
	try {
	    LinkedList l = new LinkedList();
	    l.getFirst();
	    fail("First Element"); 
	}
	catch(NoSuchElementException success) {}
    }
    
    public void testRemoveFirst() {
	try {
	    LinkedList l = new LinkedList();
	    l.removeFirst();
	    fail("R: First Element"); 
	}
	catch(NoSuchElementException success) {}
    }
    
    public void testRemoveLast() {
	try {
	    LinkedList l = new LinkedList();
	    l.removeLast();
	    fail("R: Last Element");  
	}
	catch(NoSuchElementException success) {}
    }
    
    public void testGetLast_NoSuchElementException(){
	try {
	    LinkedList l = new LinkedList();
	    l.getLast();
	    fail("Last Element");  
	}
	catch(NoSuchElementException success) {}
    }


    public void testAddAll_NullPointerException(){
	try {
	    LinkedList l = new LinkedList(); 
	    l.addAll((Collection)null);
	    fail("Add All Failed");
	}
	catch(NullPointerException success){}
    }
    
    
    public void testAddAll1_OutOfBounds() {
	try {
	    LinkedList l = new LinkedList(); 
	    l.addAll(4,new LinkedList());
	    fail("boolean addAll(int, Collection) should throw IndexOutOfBoundsException");
	}
	catch(IndexOutOfBoundsException  success) {}
    }
    
    
    public void testAddAll2_IndexOutOfBoundsException() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    LinkedList m = new LinkedList();
	    m.add(new Object());
	    l.addAll(4,m);
	    fail("Add All Failed " + l.size());
	}catch(IndexOutOfBoundsException  success) {}
    }

    public void testAddAll4_BadIndex() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    LinkedList m = new LinkedList();
	    m.add(new Object());
	    l.addAll(-1,m);
	    fail("Add All Failed " + l.size());
	}catch(IndexOutOfBoundsException  success){}
    }

    public void testget1() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    l.get(-1);
	    fail("get Failed - l.get(-1)");
	}catch(IndexOutOfBoundsException  success) {}	
    }

    public void testget2() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    l.get(5);
	    fail("get Failed - l.get(5) l.size(): " + l.size());
	}catch(IndexOutOfBoundsException  success){}	
    }

    public void testset1() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    l.set(-1,new Object());
	    fail("set failed - l.set(-1,...)" + l.size());
	}catch(IndexOutOfBoundsException  success){}	
    }

    public void testset2() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    l.set(5,new Object());
	    fail("set failed = l.set(5,..) l.size():" + l.size());
	}catch(IndexOutOfBoundsException  success){}	
    }

    public void testadd1() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    l.add(-1,new Object());
	    fail("Add Failed - l.add(-1) l.size(): " + l.size());
	}catch(IndexOutOfBoundsException  success){}	
    }

    public void add2(){
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    l.add(5,new Object());
	    fail("Add Failed  l.add(f,...)");
	}catch(IndexOutOfBoundsException  success) {}	
    }

    public void testremove(){
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    l.remove(-1);
	    fail("Remove Failed l.remove(-1); l.size():" + l.size());
	}catch(IndexOutOfBoundsException  success){}
    }
    
    public void testremove1(){
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    l.remove(5);
	    fail("Remove Failed l.remove(5); l.size():" + l.size());
	}catch(IndexOutOfBoundsException  success){}
    }

     
    public void testremove2(){
        try{
	    LinkedList l = new LinkedList();
            l.remove();
            fail("LinkedList - Object remove() should throw a NoSuchElementException");
	}catch(NoSuchElementException e){}	
    }

    public void testlistIt1() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    l.listIterator(5);
	    fail("l.listIterator(5) l.size():" + l.size());
	}catch(IndexOutOfBoundsException  success){}
    }
    
    public void testlistIt2() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    l.listIterator(-1);
	    fail("l.listIterator(-1) l.size():" + l.size());
	}catch(IndexOutOfBoundsException  success){}
    }
    
    public void testlistIt3() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    ListIterator a = l.listIterator(0);
	    l.removeFirst();
	    a.next();
	    fail("l.listIterator(-1) l.size():" + l.size());
	}catch(ConcurrentModificationException success){}
    }

    public void testToArray_BadArg() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Object());
	    Object o[] = l.toArray(null);
	    fail("l.toArray(null) did not throw an exception");
	}catch(NullPointerException success){}
    }

    public void testToArray1_BadArg() {
	try {
	    LinkedList l = new LinkedList();
	    l.add(new Integer(5));
	    Object o[] = l.toArray(new String[10] );
	    fail("l.toArray(String[] f) did not throw an exception, an Integer was added");
	}catch(ArrayStoreException  success){}
    }

}
