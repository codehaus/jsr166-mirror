/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.Enumeration;
import java.io.*;

public class ConcurrentHashMapTest extends TestCase{
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(ConcurrentHashMapTest.class);
    }

    static final Integer one = new Integer(1);
    static final Integer two = new Integer(2);
    static final Integer three = new Integer(3);
    static final Integer four = new Integer(4);
    static final Integer five = new Integer(5);

    private static ConcurrentHashMap map5() {   
	ConcurrentHashMap map = new ConcurrentHashMap(5);
        assertTrue(map.isEmpty());
	map.put(one, "A");
	map.put(two, "B");
	map.put(three, "C");
	map.put(four, "D");
	map.put(five, "E");
        assertFalse(map.isEmpty());
        assertEquals(5, map.size());
	return map;
    }

    /**
     *  Test to verify clear correctly removes all key-element pairs from the map
     */
    public void testClear(){
        ConcurrentHashMap map = map5();
	map.clear();
	assertEquals(map.size(), 0);
    }

    /**
     *  Test to verify contains gives the appropriate value
     */
    public void testContains(){
        ConcurrentHashMap map = map5();
	assertTrue(map.contains("A"));
        assertFalse(map.contains("Z"));
    }
    
    /**
     *  Test to verify containsKey gives the appropriate value
     */
    public void testContainsKey(){
        ConcurrentHashMap map = map5();
	assertTrue(map.containsKey(one));
        assertFalse(map.containsKey(new Integer(100)));
    }

    /**
     *  Identical to normal contains
     */
    public void testContainsValue(){
        ConcurrentHashMap map = map5();
	assertTrue(map.contains("A"));
        assertFalse(map.contains("Z"));
    }

    /**
     *  tes to verify enumeration returns an enumeration containing the correct elements
     */
    public void testEnumeration(){
        ConcurrentHashMap map = map5();
	Enumeration e = map.elements();
	int count = 0;
	while(e.hasMoreElements()){
	    count++;
	    e.nextElement();
	}
	assertEquals(5, count);
    }

    /**
     *  Test to verify get returns the correct element at the given index
     */
    public void testGet(){
        ConcurrentHashMap map = map5();
	assertEquals("A", (String)map.get(one));
    }

    /**
     *  Test to verify get on a nonexistant key returns null
     */
    public void testGet2(){
        ConcurrentHashMap empty = new ConcurrentHashMap();
        assertNull(empty.get("anything"));
    }

    /**
     *  Simple test to verify isEmpty returns the correct value
     */
    public void testIsEmpty(){
        ConcurrentHashMap empty = new ConcurrentHashMap();
        ConcurrentHashMap map = map5();
	assertTrue(empty.isEmpty());
        assertFalse(map.isEmpty());
    }

    /**
     *  Test to verify keys returns an enumeration containing all the keys from the map
     */
    public void testKeys(){
        ConcurrentHashMap map = map5();
	Enumeration e = map.keys();
	int count = 0;
	while(e.hasMoreElements()){
	    count++;
	    e.nextElement();
	}
	assertEquals(5, count);
    }

    /**
     *  Test to verify keySet returns a Set containing all the keys
     */
    public void testKeySet(){
        ConcurrentHashMap map = map5();
	Set s = map.keySet();
	assertEquals(5, s.size());
	assertTrue(s.contains(one));
	assertTrue(s.contains(two));
	assertTrue(s.contains(three));
	assertTrue(s.contains(four));
	assertTrue(s.contains(five));
    }

    public void testValues(){
        ConcurrentHashMap map = map5();
	Collection s = map.values();
	assertEquals(5, s.size());
	assertTrue(s.contains("A"));
	assertTrue(s.contains("B"));
	assertTrue(s.contains("C"));
	assertTrue(s.contains("D"));
	assertTrue(s.contains("E"));
    }

    public void testEntrySet(){
        ConcurrentHashMap map = map5();
	Set s = map.entrySet();
	assertEquals(5, s.size());
        Iterator it = s.iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            assertTrue( 
                       (e.getKey().equals(one) && e.getValue().equals("A")) ||
                       (e.getKey().equals(two) && e.getValue().equals("B")) ||
                       (e.getKey().equals(three) && e.getValue().equals("C")) ||
                       (e.getKey().equals(four) && e.getValue().equals("D")) ||
                       (e.getKey().equals(five) && e.getValue().equals("E")));
        }
    }

    /**
     *  Test to verify putAll correctly adds all key-value pairs from the given map
     */
    public void testPutAll(){
        ConcurrentHashMap empty = new ConcurrentHashMap();
        ConcurrentHashMap map = map5();
	empty.putAll(map);
	assertEquals(5, empty.size());
	assertTrue(empty.containsKey(one));
	assertTrue(empty.containsKey(two));
	assertTrue(empty.containsKey(three));
	assertTrue(empty.containsKey(four));
	assertTrue(empty.containsKey(five));
    }

    /**
     *  Test to verify putIfAbsent works when the given key is not present
     */
    public void testPutIfAbsent(){
        ConcurrentHashMap map = map5();
	map.putIfAbsent(new Integer(6), "Z");
        assertTrue(map.containsKey(new Integer(6)));
    }

    /**
     *  Test to verify putIfAbsent does not add the pair if the key is already present
     */
    public void testPutIfAbsent2(){
        ConcurrentHashMap map = map5();
        assertEquals("A", map.putIfAbsent(one, "Z"));
    }

    /**
     *  Test to verify remove removes the correct key-value pair from the map
     */
    public void testRemove(){
        ConcurrentHashMap map = map5();
	map.remove(five);
	assertEquals(4, map.size());
	assertFalse(map.containsKey(five));
    }

    public void testRemove2(){
        ConcurrentHashMap map = map5();
	map.remove(five, "E");
	assertEquals(4, map.size());
	assertFalse(map.containsKey(five));
	map.remove(four, "A");
	assertEquals(4, map.size());
	assertTrue(map.containsKey(four));

    }

    /**
     *  Simple test to verify size returns the correct values
     */
    public void testSize(){
        ConcurrentHashMap map = map5();
        ConcurrentHashMap empty = new ConcurrentHashMap();
	assertEquals(0, empty.size());
	assertEquals(5, map.size());
    }

    public void testToString(){
        ConcurrentHashMap map = map5();
        String s = map.toString();
        for (int i = 1; i <= 5; ++i) {
            assertTrue(s.indexOf(String.valueOf(i)) >= 0);
        }
    }        

    // Exception tests
    
    public void testConstructor1(){
        try{
            new ConcurrentHashMap(-1,0,1);
            fail("ConcurrentHashMap(int, float, int) should throw Illegal Argument Exception");
        }catch(IllegalArgumentException e){}
    }

    public void testConstructor2(){
        try{
            new ConcurrentHashMap(1,0,-1);
            fail("ConcurrentHashMap(int, float, int) should throw Illegal Argument Exception");
        }catch(IllegalArgumentException e){}
    }

    public void testConstructor3(){
        try{
            new ConcurrentHashMap(-1);
            fail("ConcurrentHashMap(int) should throw Illegal Argument Exception");
        }catch(IllegalArgumentException e){}
    }

    public void testGet_NullPointerException(){
        try{
            ConcurrentHashMap c = new ConcurrentHashMap(5);
            c.get(null);
            fail("ConcurrentHashMap - Object get(Object) should throw Null Pointer exception");
        }catch(NullPointerException e){}
    }

    public void testContainsKey_NullPointerException(){
        try{
            ConcurrentHashMap c = new ConcurrentHashMap(5);
            c.containsKey(null);
            fail("ConcurrenthashMap - boolean containsKey(Object) should throw Null Pointer exception");
        }catch(NullPointerException e){}
    }

    public void testContainsValue_NullPointerException(){
        try{
            ConcurrentHashMap c = new ConcurrentHashMap(5);
            c.containsValue(null);
            fail("ConcurrentHashMap - boolean containsValue(Object) should throw Null Pointer exception");
        }catch(NullPointerException e){}
    }

    public void testContains_NullPointerException(){
        try{
            ConcurrentHashMap c = new ConcurrentHashMap(5);
            c.contains(null);
            fail("ConcurrentHashMap - boolean contains(Object) should throw Null Pointer exception");
        }catch(NullPointerException e){}
    }

    public void testPut1_NullPointerException(){
        try{
            ConcurrentHashMap c = new ConcurrentHashMap(5);
            c.put(null, "whatever");
            fail("ConcurrentHashMap - Object put(Object, Object) should throw Null Pointer exception");
        }catch(NullPointerException e){}
    }

    public void testPut2_NullPointerException(){
        try{
            ConcurrentHashMap c = new ConcurrentHashMap(5);
            c.put("whatever", null);
            fail("ConcurrentHashMap - Object put(Object, Object) should throw Null Pointer exception");
        }catch(NullPointerException e){}
    }

    public void testPutIfAbsent1_NullPointerException(){
        try{
            ConcurrentHashMap c = new ConcurrentHashMap(5);
            c.putIfAbsent(null, "whatever");
            fail("ConcurrentHashMap - Object putIfAbsent(Object, Object) should throw Null Pointer exception");
        }catch(NullPointerException e){}
    }

    public void testPutIfAbsent2_NullPointerException(){
        try{
            ConcurrentHashMap c = new ConcurrentHashMap(5);
            c.putIfAbsent("whatever", null);
            fail("COncurrentHashMap - Object putIfAbsent(Object, Object) should throw Null Pointer exception");
        }catch(NullPointerException e){}
    }


    public void testRemove1_NullPointerException(){
        try{
            ConcurrentHashMap c = new ConcurrentHashMap(5);
            c.put("sadsdf", "asdads");
            c.remove(null);
            fail("ConcurrentHashMap - Object remove(Object) should throw Null pointer exceptione");
        }catch(NullPointerException e){}
    }

    public void testRemove2_NullPointerException(){
        try{
            ConcurrentHashMap c = new ConcurrentHashMap(5);
            c.put("sadsdf", "asdads");
            c.remove(null, "whatever");
            fail("ConcurrentHashMap - Object remove(Object, Object) should throw Null pointer exceptione");
        }catch(NullPointerException e){}
    }

    public void testSerialization() {
        ConcurrentHashMap q = map5();

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(q);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            ConcurrentHashMap r = (ConcurrentHashMap)in.readObject();
            assertEquals(q.size(), r.size());
            assertTrue(q.equals(r));
            assertTrue(r.equals(q));
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }

    
}
