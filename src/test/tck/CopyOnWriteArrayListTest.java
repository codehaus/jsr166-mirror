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

public class CopyOnWriteArrayListTest extends JSR166TestCase{
    
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }

    public static Test suite() {
	return new TestSuite(CopyOnWriteArrayListTest.class);
    }

    static CopyOnWriteArrayList populatedArray(int n){
	CopyOnWriteArrayList a = new CopyOnWriteArrayList();
        assertTrue(a.isEmpty());
        for (int i = 0; i < n; ++i) 
            a.add(new Integer(i));
        assertFalse(a.isEmpty());
        assertEquals(n, a.size());
        return a;
    }


    public void testConstructor() {
	CopyOnWriteArrayList a = new CopyOnWriteArrayList();
        assertTrue(a.isEmpty());
    }

    public void testConstructor2() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE-1; ++i)
            ints[i] = new Integer(i);
	CopyOnWriteArrayList a = new CopyOnWriteArrayList(ints);
        for (int i = 0; i < SIZE; ++i) 
            assertEquals(ints[i], a.get(i));
    }

    public void testConstructor3() {
        Integer[] ints = new Integer[SIZE];
        for (int i = 0; i < SIZE-1; ++i)
            ints[i] = new Integer(i);
	CopyOnWriteArrayList a = new CopyOnWriteArrayList(Arrays.asList(ints));
        for (int i = 0; i < SIZE; ++i) 
            assertEquals(ints[i], a.get(i));
    }
        

    /**
     *   addAll correctly adds each element from the given collection
     */
    public void testAddAll(){
	CopyOnWriteArrayList full = populatedArray(3);
	Vector v = new Vector();
	v.add(three);
	v.add(four);
	v.add(five);
	full.addAll(v);
	assertEquals(6, full.size());
    }

    /**
     *   addAllAbsent adds each element from the given collection that did not
     *  already exist in the List
     */
    public void testAddAllAbsent(){
	CopyOnWriteArrayList full = populatedArray(3);
	Vector v = new Vector();
	v.add(three);
	v.add(four);
	v.add(one); // will not add this element
	full.addAllAbsent(v);
	assertEquals(5, full.size());
    }

    /**
     *   addIfAbsent will not add the element if it already exists in the list
     */
    public void testAddIfAbsent(){
	CopyOnWriteArrayList full = populatedArray(3);
	full.addIfAbsent(one);
	assertEquals(3, full.size());
    }

    /**
     *   addIfAbsent correctly adds the element when it does not exist in the list
     */
    public void testAddIfAbsent2(){
	CopyOnWriteArrayList full = populatedArray(3);
        full.addIfAbsent(three);
        assertTrue(full.contains(three));
    }

    /**
     *   clear correctly removes all elements from the list
     */
    public void testClear(){
	CopyOnWriteArrayList full = populatedArray(3);
	full.clear();
	assertEquals(0, full.size());
    }

    /**
     *   contains returns the correct values 
     */
    public void testContains(){
	CopyOnWriteArrayList full = populatedArray(3);
	assertTrue(full.contains(one));
	assertFalse(full.contains(five));
    }

    public void testAddIndex() {
	CopyOnWriteArrayList full = populatedArray(3);
        full.add(0, m1);
        assertEquals(4, full.size());
        assertEquals(m1, full.get(0));
        assertEquals(zero, full.get(1));

        full.add(2, m2);
        assertEquals(5, full.size());
        assertEquals(m2, full.get(2));
        assertEquals(two, full.get(4));
    }

    public void testEquals() {
	CopyOnWriteArrayList a = populatedArray(3);
	CopyOnWriteArrayList b = populatedArray(3);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
        a.add(m1);
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
        b.add(m1);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    
    /**
     *   containsAll returns the correct values
     */
    public void testContainsAll(){
	CopyOnWriteArrayList full = populatedArray(3);
	Vector v = new Vector();
	v.add(one);
	v.add(two);
	assertTrue(full.containsAll(v));
	v.add(six);
	assertFalse(full.containsAll(v));
    }

    /**
     *   get returns the correct value for the given index
     */
    public void testGet(){
	CopyOnWriteArrayList full = populatedArray(3);
	assertEquals(0, ((Integer)full.get(0)).intValue());
    }

    /**
     *   indexOf gives the correct index for the given object
     */
    public void testIndexOf(){
	CopyOnWriteArrayList full = populatedArray(3);
	assertEquals(1, full.indexOf(one));
	assertEquals(-1, full.indexOf("puppies"));
    }

    /**
     *   indexOf gives the correct index based on the given index
     *  at which to start searching
     */
    public void testIndexOf2(){
	CopyOnWriteArrayList full = populatedArray(3);
	assertEquals(1, full.indexOf(one, 0));
	assertEquals(-1, full.indexOf(one, 2));
    }

    /**
     *   isEmpty returns the correct values
     */
    public void testIsEmpty(){
	CopyOnWriteArrayList empty = new CopyOnWriteArrayList();
	CopyOnWriteArrayList full = populatedArray(3);
	assertTrue(empty.isEmpty());
	assertFalse(full.isEmpty());
    }

    /**
     *   iterator() returns an iterator containing the elements of the list 
     */
    public void testIterator(){
	CopyOnWriteArrayList full = populatedArray(3);
	Iterator i = full.iterator();
	int j;
	for(j = 0; i.hasNext(); j++)
	    assertEquals(j, ((Integer)i.next()).intValue());
	assertEquals(3, j);
    }

    public void testIteratorRemove () {
	CopyOnWriteArrayList full = populatedArray(3);
        Iterator it = full.iterator();
        it.next();
        try {
            it.remove();
            fail("should throw");
        }
        catch (UnsupportedOperationException success) {}
    }

    public void testToString(){
	CopyOnWriteArrayList full = populatedArray(3);
        String s = full.toString();
        for (int i = 0; i < 3; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        

    /**
     *   lastIndexOf returns the correct index for the given object
     */
    public void testLastIndexOf1(){
	CopyOnWriteArrayList full = populatedArray(3);
	full.add(one);
	full.add(three);
	assertEquals(3, full.lastIndexOf(one));
	assertEquals(-1, full.lastIndexOf(six));
    }

    /**
     *   lastIndexOf returns the correct index from the given starting point
     */
    public void testlastIndexOf2(){
	CopyOnWriteArrayList full = populatedArray(3);
	full.add(one);
	full.add(three);
	assertEquals(3, full.lastIndexOf(one, 4));
	assertEquals(-1, full.lastIndexOf(three, 3));
    }

    /**
     *  Identical to testIterator, except ListInterator has more functionality
     */
    public void testListIterator1(){
	CopyOnWriteArrayList full = populatedArray(3);
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
	CopyOnWriteArrayList full = populatedArray(3);
	ListIterator i = full.listIterator(1);
	int j;
	for(j = 0; i.hasNext(); j++)
	    assertEquals(j+1, ((Integer)i.next()).intValue());
	assertEquals(2, j);
    }

    /**
     *   remove correctly removes and returns the object at the given index
     */
    public void testRemove(){
	CopyOnWriteArrayList full = populatedArray(3);
	assertEquals(two, full.remove(2));
	assertEquals(2, full.size());
    }

    /**
     *   removeAll correctly removes all elements from the given collection
     */
    public void testRemoveAll(){
	CopyOnWriteArrayList full = populatedArray(3);
	Vector v = new Vector();
	v.add(one);
	v.add(two);
	full.removeAll(v);
	assertEquals(1, full.size());
    }

    /**
     *   set correctly changes the element at the given index
     */
    public void testSet(){
	CopyOnWriteArrayList full = populatedArray(3);
	assertEquals(two, full.set(2, four));
	assertEquals(4, ((Integer)full.get(2)).intValue());
    }

    /**
     *   size returns the correct values
     */
    public void testSize(){
	CopyOnWriteArrayList empty = new CopyOnWriteArrayList();
	CopyOnWriteArrayList full = populatedArray(3);
	assertEquals(3, full.size());
	assertEquals(0, empty.size());
    }

    /**
     *   toArray returns an Object array containing all elements from the list
     */
    public void testToArray(){
	CopyOnWriteArrayList full = populatedArray(3);
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
	CopyOnWriteArrayList full = populatedArray(3);
	Integer[] i = new Integer[3];
	i = (Integer[])full.toArray(i);
	assertEquals(3, i.length);
	assertEquals(0, i[0].intValue());
	assertEquals(1, i[1].intValue());
	assertEquals(2, i[2].intValue());
    }


    public void testSubList() {
	CopyOnWriteArrayList a = populatedArray(10);
        assertTrue(a.subList(1,1).isEmpty());
	for(int j = 0; j < 9; ++j) {
	    for(int i = j ; i < 10; ++i) {
		List b = a.subList(j,i);
		for(int k = j; k < i; ++k) {
		    assertEquals(new Integer(k), b.get(k-j));
		}
	    }
	}

	List s = a.subList(2, 5);
        assertEquals(s.size(), 3);
        s.set(2, m1);
        assertEquals(a.get(4), m1);
	s.clear();
        assertEquals(a.size(), 7);
    }

    // Exception tests

    /**
     *   toArray throws an ArrayStoreException when the given array
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
     *   get throws an IndexOutOfBoundsException on a negative index
     */
    public void testGet1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.get(-1);
            fail("Object get(int) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }
    
    /**
     *   get throws an IndexOutOfBoundsException on a too high index
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
     *   set throws an IndexOutOfBoundsException on a negative index
     */
    public void testSet1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.set(-1,"qwerty");
            fail("Object get(int, Object) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }
    
    /**
     *   set throws an IndexOutOfBoundsException on a too high index
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
     *   add throws an IndexOutOfBoundsException on a negative index
     */
    public void testAdd1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.add(-1,"qwerty");
            fail("void add(int, Object) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }
    
    /**
     *   add throws an IndexOutOfBoundsException on a too high index
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
     *   remove throws an IndexOutOfBoundsException on a negative index
     */
    public void testRemove1_IndexOutOfBounds(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.remove(-1);
            fail("Object remove(int) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *   remove throws an IndexOutOfBoundsException on a too high index
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
     *   addAll throws an IndexOutOfBoundsException on a negative index
     */
    public void testAddAll1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.addAll(-1,new LinkedList());
            fail("boolean add(int, Collection) should throw IndexOutOfBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }
    
    /**
     *   addAll throws an IndexOutOfBoundsException on a too high index
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
     *   listIterator throws an IndexOutOfBoundsException on a negative index
     */
    public void testListIterator1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.listIterator(-1);
            fail("ListIterator listIterator(int) should throw IndexOutOfBounds exceptione");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *   listIterator throws an IndexOutOfBoundsException on a too high index
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
     *   subList throws an IndexOutOfBoundsException on a negative index
     */
    public void testSubList1_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.subList(-1,100);

            fail("List subList(int, int) should throw IndexOutofBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    /**
     *   subList throws an IndexOutOfBoundsException on a too high index
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
     *   subList throws IndexOutOfBoundsException when the second index
     *  is lower then the first 
     */
    public void testSubList3_IndexOutOfBoundsException(){
        try{
            CopyOnWriteArrayList c = new CopyOnWriteArrayList();
            c.subList(3,1);

            fail("List subList(int, int) should throw IndexOutofBounds exception");
        }catch(IndexOutOfBoundsException e){}
    }

    public void testSerialization() {
        CopyOnWriteArrayList q = populatedArray(SIZE);

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(q);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            CopyOnWriteArrayList r = (CopyOnWriteArrayList)in.readObject();
            assertEquals(q.size(), r.size());
            assertTrue(q.equals(r));
            assertTrue(r.equals(q));
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }
    
}
