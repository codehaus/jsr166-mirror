/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class CopyOnWriteArraySetTest extends TestCase{
    
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(CopyOnWriteArraySetTest.class);
    }

    static CopyOnWriteArraySet fullSet(int n){
	CopyOnWriteArraySet a = new CopyOnWriteArraySet();
        assertTrue(a.isEmpty());
        for (int i = 0; i < n; ++i) 
            a.add(new Integer(i));
        assertFalse(a.isEmpty());
        assertEquals(n, a.size());
        return a;
    }

    /**
     *  Test to verify addAll correctly adds each element from the given collection
     */
    public void testAddAll(){
	CopyOnWriteArraySet full = fullSet(3);
	Vector v = new Vector();
	v.add(new Integer(3));
	v.add(new Integer(4));
	v.add(new Integer(5));
	full.addAll(v);
	assertEquals(6, full.size());
    }

    /**
     *  Test to verify addAllAbsent adds each element from the given collection that did not
     *  already exist in the List
     */
    public void testAddAll2(){
	CopyOnWriteArraySet full = fullSet(3);
	Vector v = new Vector();
	v.add(new Integer(3));
	v.add(new Integer(4));
	v.add(new Integer(1)); // will not add this element
	full.addAll(v);
	assertEquals(5, full.size());
    }

    /**
     *  Test to verify addIfAbsent will not add the element if it already exists in the list
     */
    public void testAdd2(){
	CopyOnWriteArraySet full = fullSet(3);
	full.add(new Integer(1));
	assertEquals(3, full.size());
    }

    /**
     *  test to verify addIfAbsent correctly adds the element when it does not exist in the list
     */
    public void testAdd3(){
	CopyOnWriteArraySet full = fullSet(3);
        full.add(new Integer(3));
        assertTrue(full.contains(new Integer(3)));
    }

    /**
     *  Test to verify clear correctly removes all elements from the list
     */
    public void testClear(){
	CopyOnWriteArraySet full = fullSet(3);
	full.clear();
	assertEquals(0, full.size());
    }

    /**
     *  Test to verify contains returns the correct values 
     */
    public void testContains(){
	CopyOnWriteArraySet full = fullSet(3);
	assertTrue(full.contains(new Integer(1)));
	assertFalse(full.contains(new Integer(5)));
    }

    public void testEquals() {
	CopyOnWriteArraySet a = fullSet(3);
	CopyOnWriteArraySet b = fullSet(3);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
        a.add(new Integer(-1));
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
        b.add(new Integer(-1));
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    
    /**
     *  Test to verify containsAll returns the correct values
     */
    public void testContainsAll(){
	CopyOnWriteArraySet full = fullSet(3);
	Vector v = new Vector();
	v.add(new Integer(1));
	v.add(new Integer(2));
	assertTrue(full.containsAll(v));
	v.add(new Integer(6));
	assertFalse(full.containsAll(v));
    }

    /**
     *  Test to verify isEmpty returns the correct values
     */
    public void testIsEmpty(){
	CopyOnWriteArraySet empty = new CopyOnWriteArraySet();
	CopyOnWriteArraySet full = fullSet(3);
	assertTrue(empty.isEmpty());
	assertFalse(full.isEmpty());
    }

    /**
     *  Test to verify iterator() returns an iterator containing the elements of the list 
     */
    public void testIterator(){
	CopyOnWriteArraySet full = fullSet(3);
	Iterator i = full.iterator();
	int j;
	for(j = 0; i.hasNext(); j++)
	    assertEquals(j, ((Integer)i.next()).intValue());
	assertEquals(3, j);
    }

    public void testIteratorRemove () {
	CopyOnWriteArraySet full = fullSet(3);
        Iterator it = full.iterator();
        it.next();
        try {
            it.remove();
            fail("should throw");
        }
        catch (UnsupportedOperationException success) {}
    }

    public void testToString(){
	CopyOnWriteArraySet full = fullSet(3);
        String s = full.toString();
        for (int i = 0; i < 3; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        


    /**
     *  Test to verify removeAll correctly removes all elements from the given collection
     */
    public void testRemoveAll(){
	CopyOnWriteArraySet full = fullSet(3);
	Vector v = new Vector();
	v.add(new Integer(1));
	v.add(new Integer(2));
	full.removeAll(v);
	assertEquals(1, full.size());
    }


    public void testRemove(){
	CopyOnWriteArraySet full = fullSet(3);
	full.remove(new Integer(1));
        assertFalse(full.contains(new Integer(1)));
	assertEquals(2, full.size());
    }

    /**
     *  Test to verify size returns the correct values
     */
    public void testSize(){
	CopyOnWriteArraySet empty = new CopyOnWriteArraySet();
	CopyOnWriteArraySet full = fullSet(3);
	assertEquals(3, full.size());
	assertEquals(0, empty.size());
    }

    /**
     *  Test to verify toArray returns an Object array containing all elements from the list
     */
    public void testToArray(){
	CopyOnWriteArraySet full = fullSet(3);
	Object[] o = full.toArray();
	assertEquals(3, o.length);
	assertEquals(0, ((Integer)o[0]).intValue());
	assertEquals(1, ((Integer)o[1]).intValue());
	assertEquals(2, ((Integer)o[2]).intValue());
    }

    /**
     *  test to verify toArray returns an Integer array containing all elements from the list
     */
    public void testToArray2(){
	CopyOnWriteArraySet full = fullSet(3);
	Integer[] i = new Integer[3];
	i = (Integer[])full.toArray(i);
	assertEquals(3, i.length);
	assertEquals(0, i[0].intValue());
	assertEquals(1, i[1].intValue());
	assertEquals(2, i[2].intValue());
    }



    // Exception tests

    /**
     *  Test to verify toArray throws an ArrayStoreException when the given array
     *  can not store the objects inside the list
     */
    public void testToArray_ArrayStoreException(){
        try{
            CopyOnWriteArraySet c = new CopyOnWriteArraySet();
            c.add("zfasdfsdf");
            c.add("asdadasd");
            c.toArray(new Long[5]);
	    fail("Object[] toArray(Object[]) should throw ArrayStoreException");
        }catch(ArrayStoreException e){}
    }

}
