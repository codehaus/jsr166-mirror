/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class CopyOnWriteArrayListTest extends TestCase{
    
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(CopyOnWriteArrayListTest.class);
    }

    static CopyOnWriteArrayList fullArray(int n){
	CopyOnWriteArrayList a = new CopyOnWriteArrayList();
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
	CopyOnWriteArrayList full = fullArray(3);
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
    public void testAddAllAbsent(){
	CopyOnWriteArrayList full = fullArray(3);
	Vector v = new Vector();
	v.add(new Integer(3));
	v.add(new Integer(4));
	v.add(new Integer(1)); // will not add this element
	full.addAllAbsent(v);
	assertEquals(5, full.size());
    }

    /**
     *  Test to verify addIfAbsent will not add the element if it already exists in the list
     */
    public void testAddIfAbsent(){
	CopyOnWriteArrayList full = fullArray(3);
	full.addIfAbsent(new Integer(1));
	assertEquals(3, full.size());
    }

    /**
     *  test to verify addIfAbsent correctly adds the element when it does not exist in the list
     */
    public void testAddIfAbsent2(){
	CopyOnWriteArrayList full = fullArray(3);
        full.addIfAbsent(new Integer(3));
        assertTrue(full.contains(new Integer(3)));
    }

    /**
     *  Test to verify clear correctly removes all elements from the list
     */
    public void testClear(){
	CopyOnWriteArrayList full = fullArray(3);
	full.clear();
	assertEquals(0, full.size());
    }

    /**
     *  Test to verify contains returns the correct values 
     */
    public void testContains(){
	CopyOnWriteArrayList full = fullArray(3);
	assertTrue(full.contains(new Integer(1)));
	assertFalse(full.contains(new Integer(5)));
    }

    public void testAddIndex() {
	CopyOnWriteArrayList full = fullArray(3);
        full.add(0, new Integer(-1));
        assertEquals(4, full.size());
        assertEquals(new Integer(-1), full.get(0));
        assertEquals(new Integer(0), full.get(1));

        full.add(2, new Integer(-2));
        assertEquals(5, full.size());
        assertEquals(new Integer(-2), full.get(2));
        assertEquals(new Integer(2), full.get(4));
    }

    public void testEquals() {
	CopyOnWriteArrayList a = fullArray(3);
	CopyOnWriteArrayList b = fullArray(3);
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
	CopyOnWriteArrayList full = fullArray(3);
	Vector v = new Vector();
	v.add(new Integer(1));
	v.add(new Integer(2));
	assertTrue(full.containsAll(v));
	v.add(new Integer(6));
	assertFalse(full.containsAll(v));
    }

    /**
     *  Test to verify get returns the correct value for the given index
     */
    public void testGet(){
	CopyOnWriteArrayList full = fullArray(3);
	assertEquals(0, ((Integer)full.get(0)).intValue());
    }

    /**
     *  Test to verify indexOf gives the correct index for the given object
     */
    public void testIndexOf(){
	CopyOnWriteArrayList full = fullArray(3);
	assertEquals(1, full.indexOf(new Integer(1)));
	assertEquals(-1, full.indexOf("puppies"));
    }

    /**
     *  Test to verify indexOf gives the correct index based on the given index
     *  at which to start searching
     */
    public void testIndexOf2(){
	CopyOnWriteArrayList full = fullArray(3);
	assertEquals(1, full.indexOf(new Integer(1), 0));
	assertEquals(-1, full.indexOf(new Integer(1), 2));
    }

    /**
     *  Test to verify isEmpty returns the correct values
     */
    public void testIsEmpty(){
	CopyOnWriteArrayList empty = new CopyOnWriteArrayList();
	CopyOnWriteArrayList full = fullArray(3);
	assertTrue(empty.isEmpty());
	assertFalse(full.isEmpty());
    }

    /**
     *  Test to verify iterator() returns an iterator containing the elements of the list 
     */
    public void testIterator(){
	CopyOnWriteArrayList full = fullArray(3);
	Iterator i = full.iterator();
	int j;
	for(j = 0; i.hasNext(); j++)
	    assertEquals(j, ((Integer)i.next()).intValue());
	assertEquals(3, j);
    }

    public void testIteratorRemove () {
	CopyOnWriteArrayList full = fullArray(3);
        Iterator it = full.iterator();
        it.next();
        try {
            it.remove();
            fail("should throw");
        }
        catch (UnsupportedOperationException success) {}
    }

    public void testToString(){
	CopyOnWriteArrayList full = fullArray(3);
        String s = full.toString();
        for (int i = 0; i < 3; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        

    /**
     *  Test to verify lastIndexOf returns the correct index for the given object
     */
    public void testLastIndexOf1(){
	CopyOnWriteArrayList full = fullArray(3);
	full.add(new Integer(1));
	full.add(new Integer(3));
	assertEquals(3, full.lastIndexOf(new Integer(1)));
	assertEquals(-1, full.lastIndexOf(new Integer(6)));
    }

    /**
     *  Test to verify lastIndexOf returns the correct index from the given starting point
     */
    public void testlastIndexOf2(){
	CopyOnWriteArrayList full = fullArray(3);
	full.add(new Integer(1));
	full.add(new Integer(3));
	assertEquals(3, full.lastIndexOf(new Integer(1), 4));
	assertEquals(-1, full.lastIndexOf(new Integer(3), 3));
    }

    /**
     *  Identical to testIterator, except ListInterator has more functionality
     */
    public void testListIterator1(){
	CopyOnWriteArrayList full = fullArray(3);
	ListIterator i = full.listIterator();
	int j;
	for(j = 0; i.hasNext(); j++)
	    assertEquals(j, ((Integer)i.next()).intValue());
	assertEquals(3, j);
    }

    /**
     *  Identical to testIterator and testListIterator1, but only returns those elements
     *  after the given index
     */
    public void testListIterator2(){
	CopyOnWriteArrayList full = fullArray(3);
	ListIterator i = full.listIterator(1);
	int j;
	for(j = 0; i.hasNext(); j++)
	    assertEquals(j+1, ((Integer)i.next()).intValue());
	assertEquals(2, j);
    }

    /**
     *  Test to verify remove correctly removes and returns the object at the given index
     */
    public void testRemove(){
	CopyOnWriteArrayList full = fullArray(3);
	assertEquals(new Integer(2), full.remove(2));
	assertEquals(2, full.size());
    }

    /**
     *  Test to verify removeAll correctly removes all elements from the given collection
     */
    public void testRemoveAll(){
	CopyOnWriteArrayList full = fullArray(3);
	Vector v = new Vector();
	v.add(new Integer(1));
	v.add(new Integer(2));
	full.removeAll(v);
	assertEquals(1, full.size());
    }

    /**
     *  Test to verify set correctly changes the element at the given index
     */
    public void testSet(){
	CopyOnWriteArrayList full = fullArray(3);
	assertEquals(new Integer(2), full.set(2, new Integer(4)));
	assertEquals(4, ((Integer)full.get(2)).intValue());
    }

    /**
     *  Test to verify size returns the correct values
     */
    public void testSize(){
	CopyOnWriteArrayList empty = new CopyOnWriteArrayList();
	CopyOnWriteArrayList full = fullArray(3);
	assertEquals(3, full.size());
	assertEquals(0, empty.size());
    }

    /**
     *  Test to verify toArray returns an Object array containing all elements from the list
     */
    public void testToArray(){
	CopyOnWriteArrayList full = fullArray(3);
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
	CopyOnWriteArrayList full = fullArray(3);
	Integer[] i = new Integer[3];
	i = (Integer[])full.toArray(i);
	assertEquals(3, i.length);
	assertEquals(0, i[0].intValue());
	assertEquals(1, i[1].intValue());
	assertEquals(2, i[2].intValue());
    }


    public void testSubList() {
	CopyOnWriteArrayList a = fullArray(10);
        assertTrue(a.subList(1,1).isEmpty());
	for(int j = 0; j < 9; ++j) {
	    for(int i = j ; i < 10; ++i) {
		List b = a.subList(j,i);
		for(int k = j; k < i; ++k) {
		    assertEquals(new Integer(k), b.get(k-j));
		}
	    }
	}

        Integer m1 = new Integer(-1);
	List s = a.subList(2, 5);
        assertEquals(s.size(), 3);
        s.set(2, new Integer(m1));
        assertEquals(a.get(4), m1);
	s.clear();
        assertEquals(a.size(), 7);
    }

    // Exception tests

    /**
     *  Test to verify toArray throws an ArrayStoreException when the given array
     *  can not store the objects inside the list
     */
    public void testToArray_ArrayStoreException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.add("zfasdfsdf");
            c.add("asdadasd");
            c.toArray(new Long[5]);
	    fail("Object[] toArray(Object[]) should throw ArrayStoreException");
        }catch(ArrayStoreException e){}
    }

    /**
     *  Test to verify get throws an IndexOutOfBoundsException on a negative index
     */
    public void testGet1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.get(-1);
            fail("Object get(int) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }
    
    /**
     *  Test to verify get throws an IndexOutOfBoundsException on a too high index
     */
    public void testGet2_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.add("asdasd");
            c.add("asdad");
            c.get(100);
            fail("Object get(int) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *  Test to verify set throws an IndexOutOfBoundsException on a negative index
     */
    public void testSet1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.set(-1,"qwerty");
            fail("Object get(int, Object) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }
    
    /**
     *  Test to verify set throws an IndexOutOfBoundsException on a too high index
     */
    public void testSet2(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.add("asdasd");
            c.add("asdad");
            c.set(100, "qwerty");
            fail("Object set(int, Object) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *  Test to verify add throws an IndexOutOfBoundsException on a negative index
     */
    public void testAdd1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.add(-1,"qwerty");
            fail("void add(int, Object) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }
    
    /**
     *  Test to verify add throws an IndexOutOfBoundsException on a too high index
     */
    public void testAdd2_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.add("asdasd");
            c.add("asdasdasd");
            c.add(100, "qwerty");
            fail("void add(int, Object) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *  Test to verify remove throws an IndexOutOfBoundsException on a negative index
     */
    public void testRemove1_IndexOutOfBounds(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.remove(-1);
            fail("Object remove(int) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *  Test to verify remove throws an IndexOutOfBoundsException on a too high index
     */
    public void testRemove2_IndexOutOfBounds(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.add("asdasd");
            c.add("adasdasd");
            c.remove(100);
            fail("Object remove(int) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }
    
    /**
     *  Test to verify addAll throws an IndexOutOfBoundsException on a negative index
     */
    public void testAddAll1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.addAll(-1,new LinkedList());
            fail("boolean add(int, Collection) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }
    
    /**
     *  Test to verify addAll throws an IndexOutOfBoundsException on a too high index
     */
    public void testAddAll2_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.add("asdasd");
            c.add("asdasdasd");
            c.addAll(100, new LinkedList());
            fail("boolean addAll(int, Collection) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *  Test to verify listIterator throws an IndexOutOfBoundsException on a negative index
     */
    public void testListIterator1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.listIterator(-1);
            fail("ListIterator listIterator(int) should throw IndexOutOfBounds exceptione");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *  Test to verify listIterator throws an IndexOutOfBoundsException on a too high index
     */
    public void testListIterator2_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.add("adasd");
            c.add("asdasdas");
            c.listIterator(100);
            fail("ListIterator listIterator(int) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *  Test to verify subList throws an IndexOutOfBoundsException on a negative index
     */
    public void testSubList1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.subList(-1,100);

            fail("List subList(int, int) should throw IndexOutofBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *  Test to verify subList throws an IndexOutOfBoundsException on a too high index
     */
    public void testSubList2_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.add("asdasd");
            c.subList(1,100);
            fail("List subList(int, int) should throw IndexOutofBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *  Test to verify subList throws IndexOutOfBoundsException when the second index
     *  is lower then the first 
     */
    public void testSubList3_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.subList(3,1);

            fail("List subList(int, int) should throw IndexOutofBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }
    
}
