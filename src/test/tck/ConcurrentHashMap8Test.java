/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
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

    static final int SIZE = 10000;
    static ConcurrentHashMap<Long, Long> longMap;
    
    static ConcurrentHashMap<Long, Long> longMap() {
        if (longMap == null) {
            longMap = new ConcurrentHashMap<Long, Long>(SIZE);
            for (int i = 0; i < SIZE; ++i)
                longMap.put(Long.valueOf(i), Long.valueOf(2 *i));
        }
        return longMap;
    }

    /**
     * forEachKeySequentially, forEachValueSequentially,
     * forEachEntrySequentially, forEachSequentially,
     * forEachKeyInParallel, forEachValueInParallel,
     * forEachEntryInParallel, forEachInParallel traverse all keys,
     * values, entries, or mappings accordingly
     */
    public void testForEach() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachKeySequentially((Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), SIZE * (SIZE - 1) / 2);
        adder.reset();
        m.forEachValueSequentially((Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), SIZE * (SIZE - 1));
        adder.reset();
        m.forEachSequentially((Long x, Long y) -> adder.add(x.longValue() + y.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        m.forEachEntrySequentially((Map.Entry<Long,Long> e) -> adder.add(e.getKey().longValue() + e.getValue().longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        m.forEachKeyInParallel((Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), SIZE * (SIZE - 1) / 2);
        adder.reset();
        m.forEachValueInParallel((Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), SIZE * (SIZE - 1));
        adder.reset();
        m.forEachInParallel((Long x, Long y) -> adder.add(x.longValue() + y.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        m.forEachEntryInParallel((Map.Entry<Long,Long> e) -> adder.add(e.getKey().longValue() + e.getValue().longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * Mapped forEachKeySequentially, forEachValueSequentially,
     * forEachEntrySequentially, forEachSequentially,
     * forEachKeyInParallel, forEachValueInParallel,
     * forEachEntryInParallel, forEachInParallel traverse the given
     * transformations of all keys, values, entries, or mappings
     * accordingly
     */
    public void testMappedForEach() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        m.forEachKeySequentially((Long x) -> Long.valueOf(4 * x.longValue()),
                                 (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 4 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        m.forEachValueSequentially((Long x) -> Long.valueOf(4 * x.longValue()),
                                   (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 4 * SIZE * (SIZE - 1));
        adder.reset();
        m.forEachSequentially((Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()),
                              (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        m.forEachEntrySequentially((Map.Entry<Long,Long> e) -> Long.valueOf(e.getKey().longValue() + e.getValue().longValue()),
                                   (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        m.forEachKeyInParallel((Long x) -> Long.valueOf(4 * x.longValue()),
                               (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 4 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        m.forEachValueInParallel((Long x) -> Long.valueOf(4 * x.longValue()),
                                 (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 4 * SIZE * (SIZE - 1));
        adder.reset();
        m.forEachInParallel((Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()),
                            (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        m.forEachEntryInParallel((Map.Entry<Long,Long> e) -> Long.valueOf(e.getKey().longValue() + e.getValue().longValue()),
                                 (Long x) -> adder.add(x.longValue()));
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
    }

    /**
     * reduceKeysSequentially, reduceValuesSequentially,
     * reduceSequentially, reduceEntriesSequentially,
     * reduceKeysInParallel, reduceValuesInParallel, reduceInParallel,
     * and reduceEntriesInParallel, accumulate across all keys,
     * values, entries, or mappings accordingly
     */
    public void testReduce() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.reduceKeysSequentially((Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)SIZE * (SIZE - 1) / 2);
        r = m.reduceValuesSequentially((Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)SIZE * (SIZE - 1));
        r = m.reduceSequentially((Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()),
                                 (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        
        assertEquals((long)r, (long)3 * SIZE * (SIZE - 1) / 2);
        r = m.reduceEntriesSequentially((Map.Entry<Long,Long> e) -> Long.valueOf(e.getKey().longValue() + e.getValue().longValue()),
                                        (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)3 * SIZE * (SIZE - 1) / 2);

        r = m.reduceKeysInParallel((Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)SIZE * (SIZE - 1) / 2);
        r = m.reduceValuesInParallel((Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)SIZE * (SIZE - 1));
        r = m.reduceInParallel((Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()),
                               (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)3 * SIZE * (SIZE - 1) / 2);
        r = m.reduceEntriesInParallel((Map.Entry<Long,Long> e) -> Long.valueOf(e.getKey().longValue() + e.getValue().longValue()),
                                        (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)3 * SIZE * (SIZE - 1) / 2);
    }

    /*
     * Mapped reduceKeysSequentially, reduceKeysToIntSequentially,
     * reduceKeysToLongSequentially, reduceKeysToDoubleSequentially,
     * reduceValuesSequentially, reduceValuesToLongSequentially,
     * reduceValuesToDoubleSequentially, reduceKeysInParallel,
     * reduceKeysToLongInParallel, reduceKeysToIntInParallel,
     * reduceKeysToDoubleInParallel, reduceValuesInParallel,
     * reduceValuesToLongInParallel, reduceValuesToIntInParallel,
     * reduceValuesToDoubleInParallel accumulate mapped keys, values,
     * entries, or mappings accordingly
     */
    public void testMapReduce() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r; long lr; int ir; double dr;
        r = m.reduceKeysSequentially((Long x) -> Long.valueOf(4 * x.longValue()),
                                     (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)4 * SIZE * (SIZE - 1) / 2);
        lr = m.reduceKeysToLongSequentially((Long x) -> x.longValue(), 0L, Long::sum);
        assertEquals(lr, (long)SIZE * (SIZE - 1) / 2);

        ir = m.reduceKeysToIntSequentially((Long x) -> x.intValue(), 0, Integer::sum);
        assertEquals(ir, (int)SIZE * (SIZE - 1) / 2);
        dr = m.reduceKeysToDoubleSequentially((Long x) -> x.doubleValue(), 0.0, Double::sum);
        assertEquals(dr, (double)SIZE * (SIZE - 1) / 2);

        r = m.reduceValuesSequentially((Long x) -> Long.valueOf(4 * x.longValue()),
                                       (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)4 * SIZE * (SIZE - 1));
        lr = m.reduceValuesToLongSequentially((Long x) -> x.longValue(), 0L, Long::sum);
        assertEquals(lr, (long)SIZE * (SIZE - 1));
        ir = m.reduceValuesToIntSequentially((Long x) -> x.intValue(), 0, Integer::sum);
        assertEquals(ir, (int)SIZE * (SIZE - 1));

        dr = m.reduceValuesToDoubleSequentially((Long x) -> x.doubleValue(), 0.0, Double::sum);
        assertEquals(dr, (double)SIZE * (SIZE - 1));

        r = m.reduceKeysInParallel((Long x) -> Long.valueOf(4 * x.longValue()),
                                   (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)4 * SIZE * (SIZE - 1) / 2);
        lr = m.reduceKeysToLongInParallel((Long x) -> x.longValue(), 0L, Long::sum);
        assertEquals(lr, (long)SIZE * (SIZE - 1) / 2);
        ir = m.reduceKeysToIntInParallel((Long x) -> x.intValue(), 0, Integer::sum);
        assertEquals(ir, (int)SIZE * (SIZE - 1) / 2);
        dr = m.reduceKeysToDoubleInParallel((Long x) -> x.doubleValue(), 0.0, Double::sum);
        assertEquals(dr, (double)SIZE * (SIZE - 1) / 2);

        r = m.reduceValuesInParallel((Long x) -> Long.valueOf(4 * x.longValue()),
                                     (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()));
        assertEquals((long)r, (long)4 * SIZE * (SIZE - 1));
        lr = m.reduceValuesToLongInParallel((Long x) -> x.longValue(), 0L, Long::sum);
        assertEquals(lr, (long)SIZE * (SIZE - 1));
        ir = m.reduceValuesToIntInParallel((Long x) -> x.intValue(), 0, Integer::sum);
        assertEquals(ir, (int)SIZE * (SIZE - 1));
        dr = m.reduceValuesToDoubleInParallel((Long x) -> x.doubleValue(), 0.0, Double::sum);
        assertEquals(dr, (double)SIZE * (SIZE - 1));
    }

    /**
     * searchKeysSequentially, searchValuesSequentially,
     * searchSequentially, searchEntriesSequentially,
     * searchKeysInParallel, searchValuesInParallel, searchInParallel,
     * searchEntriesInParallel all return a non-null result of search
     * function, or null if none, across keys, values, entries, or
     * mappings accordingly
     */
    public void testSearch() {
        ConcurrentHashMap<Long, Long> m = longMap();
        Long r;
        r = m.searchKeysSequentially((Long x) -> x.longValue() == (long)(SIZE/2)? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchValuesSequentially((Long x) -> x.longValue() == (long)(SIZE/2)? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchSequentially((Long x, Long y) -> x.longValue() == (long)(SIZE/2)? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchEntriesSequentially((Map.Entry<Long,Long> e) -> e.getKey().longValue() == (long)(SIZE/2)? e.getKey() : null);
        assertEquals((long)r, (long)(SIZE/2));

        r = m.searchKeysSequentially((Long x) -> x.longValue() < 0L? x : null);
        assertNull(r);
        r = m.searchValuesSequentially((Long x) -> x.longValue() < 0L? x : null);
        assertNull(r);
        r = m.searchSequentially((Long x, Long y) -> x.longValue() < 0L? x : null);
        assertNull(r);
        r = m.searchEntriesSequentially((Map.Entry<Long,Long> e) -> e.getKey().longValue() < 0L? e.getKey() : null);
        assertNull(r);

        r = m.searchKeysInParallel((Long x) -> x.longValue() == (long)(SIZE/2)? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchValuesInParallel((Long x) -> x.longValue() == (long)(SIZE/2)? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchInParallel((Long x, Long y) -> x.longValue() == (long)(SIZE/2)? x : null);
        assertEquals((long)r, (long)(SIZE/2));
        r = m.searchEntriesInParallel((Map.Entry<Long,Long> e) -> e.getKey().longValue() == (long)(SIZE/2)? e.getKey() : null);
        assertEquals((long)r, (long)(SIZE/2));

        r = m.searchKeysInParallel((Long x) -> x.longValue() < 0L? x : null);
        assertNull(r);
        r = m.searchValuesInParallel((Long x) -> x.longValue() < 0L? x : null);
        assertNull(r);
        r = m.searchInParallel((Long x, Long y) -> x.longValue() < 0L? x : null);
        assertNull(r);
        r = m.searchEntriesInParallel((Map.Entry<Long,Long> e) -> e.getKey().longValue() < 0L? e.getKey() : null);
        assertNull(r);
    }

    /**
     * Invoking task versions of bulk methods has same effect as
     * parallel methods
     */
    public void testForkJoinTasks() {
        LongAdder adder = new LongAdder();
        ConcurrentHashMap<Long, Long> m = longMap();
        ConcurrentHashMap.ForkJoinTasks.forEachKey
            (m, (Long x) -> adder.add(x.longValue())).invoke();
        assertEquals(adder.sum(), SIZE * (SIZE - 1) / 2);
        adder.reset();
        ConcurrentHashMap.ForkJoinTasks.forEachValue
            (m, (Long x) -> adder.add(x.longValue())).invoke();
        assertEquals(adder.sum(), SIZE * (SIZE - 1));
        adder.reset();
        ConcurrentHashMap.ForkJoinTasks.forEach
            (m, (Long x, Long y) -> adder.add(x.longValue() + y.longValue())).invoke();
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        ConcurrentHashMap.ForkJoinTasks.forEachEntry
            (m, 
             (Map.Entry<Long,Long> e) -> adder.add(e.getKey().longValue() + e.getValue().longValue())).invoke();
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        ConcurrentHashMap.ForkJoinTasks.forEachKey
            (m, (Long x) -> Long.valueOf(4 * x.longValue()),
             (Long x) -> adder.add(x.longValue())).invoke();
        assertEquals(adder.sum(), 4 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        ConcurrentHashMap.ForkJoinTasks.forEachValue
            (m, (Long x) -> Long.valueOf(4 * x.longValue()),
             (Long x) -> adder.add(x.longValue())).invoke();
        assertEquals(adder.sum(), 4 * SIZE * (SIZE - 1));
        adder.reset();
        ConcurrentHashMap.ForkJoinTasks.forEach
            (m, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()),
             (Long x) -> adder.add(x.longValue())).invoke();
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
        adder.reset();
        ConcurrentHashMap.ForkJoinTasks.forEachEntry
            (m, (Map.Entry<Long,Long> e) -> Long.valueOf(e.getKey().longValue() + e.getValue().longValue()),
             (Long x) -> adder.add(x.longValue())).invoke();
        assertEquals(adder.sum(), 3 * SIZE * (SIZE - 1) / 2);
        adder.reset();

        Long r; long lr; int ir; double dr;
        r = ConcurrentHashMap.ForkJoinTasks.reduceKeys
            (m, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue())).invoke();
        assertEquals((long)r, (long)SIZE * (SIZE - 1) / 2);
        r = ConcurrentHashMap.ForkJoinTasks.reduceValues
            (m, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue())).invoke();
        assertEquals((long)r, (long)SIZE * (SIZE - 1));
        r = ConcurrentHashMap.ForkJoinTasks.reduce
            (m, (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue()),
             (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue())).invoke();
        assertEquals((long)r, (long)3 * SIZE * (SIZE - 1) / 2);
        r = ConcurrentHashMap.ForkJoinTasks.reduceEntries
            (m, (Map.Entry<Long,Long> e) -> Long.valueOf(e.getKey().longValue() + e.getValue().longValue()),
             (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue())).invoke();
        assertEquals((long)r, (long)3 * SIZE * (SIZE - 1) / 2);
        r = ConcurrentHashMap.ForkJoinTasks.reduceKeys
            (m, (Long x) -> Long.valueOf(4 * x.longValue()),
             (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue())).invoke();
        assertEquals((long)r, (long)4 * SIZE * (SIZE - 1) / 2);
        lr = ConcurrentHashMap.ForkJoinTasks.reduceKeysToLong
            (m, (Long x) -> x.longValue(), 0L, Long::sum).invoke();
        assertEquals(lr, (long)SIZE * (SIZE - 1) / 2);
        ir = ConcurrentHashMap.ForkJoinTasks.reduceKeysToInt
            (m, (Long x) -> x.intValue(), 0, Integer::sum).invoke();
        assertEquals(ir, (int)SIZE * (SIZE - 1) / 2);
        dr = ConcurrentHashMap.ForkJoinTasks.reduceKeysToDouble
            (m, (Long x) -> x.doubleValue(), 0.0, Double::sum).invoke();
        assertEquals(dr, (double)SIZE * (SIZE - 1) / 2);
        r = ConcurrentHashMap.ForkJoinTasks.reduceValues
            (m, (Long x) -> Long.valueOf(4 * x.longValue()),
             (Long x, Long y) -> Long.valueOf(x.longValue() + y.longValue())).invoke();
        assertEquals((long)r, (long)4 * SIZE * (SIZE - 1));
        lr = ConcurrentHashMap.ForkJoinTasks.reduceValuesToLong
            (m, (Long x) -> x.longValue(), 0L, Long::sum).invoke();
        assertEquals(lr, (long)SIZE * (SIZE - 1));
        ir = ConcurrentHashMap.ForkJoinTasks.reduceValuesToInt
            (m, (Long x) -> x.intValue(), 0, Integer::sum).invoke();
        assertEquals(ir, (int)SIZE * (SIZE - 1));
        dr = ConcurrentHashMap.ForkJoinTasks.reduceValuesToDouble
            (m, (Long x) -> x.doubleValue(), 0.0, Double::sum).invoke();
        assertEquals(dr, (double)SIZE * (SIZE - 1));
        r = ConcurrentHashMap.ForkJoinTasks.searchKeys
            (m, (Long x) -> x.longValue() == (long)(SIZE/2)? x : null).invoke();
        assertEquals((long)r, (long)(SIZE/2));
        r = ConcurrentHashMap.ForkJoinTasks.searchValues
            (m, (Long x) -> x.longValue() == (long)(SIZE/2)? x : null).invoke();
        assertEquals((long)r, (long)(SIZE/2));
        r = ConcurrentHashMap.ForkJoinTasks.search
            (m, (Long x, Long y) -> x.longValue() == (long)(SIZE/2)? x : null).invoke();
        assertEquals((long)r, (long)(SIZE/2));
        r = ConcurrentHashMap.ForkJoinTasks.searchEntries
            (m, (Map.Entry<Long,Long> e) -> e.getKey().longValue() == (long)(SIZE/2)? e.getKey() : null).invoke();
        assertEquals((long)r, (long)(SIZE/2));
    }            
}
