/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMap8Test extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(ConcurrentHashMap8Test.class);
    }

    /**
     * Returns a new map from Integers 1-5 to Strings "A"-"E".
     */
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
     * computeIfAbsent adds when the given key is not present
     */
    public void testComputeIfAbsent() {
        ConcurrentHashMap map = map5();
        map.computeIfAbsent(six, (x) -> "Z");
        assertTrue(map.containsKey(six));
    }

    /**
     * computeIfAbsent does not replace  if the key is already present
     */
    public void testComputeIfAbsent2() {
        ConcurrentHashMap map = map5();
        assertEquals("A", map.computeIfAbsent(one, (x) -> "Z"));
    }

    /**
     * computeIfAbsent does not add if function returns null
     */
    public void testComputeIfAbsent3() {
        ConcurrentHashMap map = map5();
        map.computeIfAbsent(six, (x) -> null);
        assertFalse(map.containsKey(six));
    }
    
    /**
     * computeIfPresent does not replace  if the key is already present
     */
    public void testComputeIfPresent() {
        ConcurrentHashMap map = map5();
        map.computeIfPresent(six, (x, y) -> "Z");
        assertFalse(map.containsKey(six));
    }

    /**
     * computeIfPresent adds when the given key is not present
     */
    public void testComputeIfPresent2() {
        ConcurrentHashMap map = map5();
        assertEquals("Z", map.computeIfPresent(one, (x, y) -> "Z"));
    }

    /**
     * compute does not replace  if the function returns null
     */
    public void testCompute() {
        ConcurrentHashMap map = map5();
        map.compute(six, (x, y) -> null);
        assertFalse(map.containsKey(six));
    }

    /**
     * compute adds when the given key is not present
     */
    public void testCompute2() {
        ConcurrentHashMap map = map5();
        assertEquals("Z", map.compute(six, (x, y) -> "Z"));
    }

    /**
     * compute replaces when the given key is present
     */
    public void testCompute3() {
        ConcurrentHashMap map = map5();
        assertEquals("Z", map.compute(one, (x, y) -> "Z"));
    }

    /**
     * compute removes when the given key is present and function returns null
     */
    public void testCompute4() {
        ConcurrentHashMap map = map5();
        map.compute(one, (x, y) -> null);
        assertFalse(map.containsKey(one));
    }

    /**
     * merge adds when the given key is not present
     */
    public void testMerge1() {
        ConcurrentHashMap map = map5();
        assertEquals("Y", map.merge(six, "Y", (x, y) -> "Z"));
    }

    /**
     * merge replaces when the given key is present
     */
    public void testMerge2() {
        ConcurrentHashMap map = map5();
        assertEquals("Z", map.merge(one, "Y", (x, y) -> "Z"));
    }

    /**
     * merge removes when the given key is present and function returns null
     */
    public void testMerge3() {
        ConcurrentHashMap map = map5();
        map.merge(one, "Y", (x, y) -> null);
        assertFalse(map.containsKey(one));
    }



}
