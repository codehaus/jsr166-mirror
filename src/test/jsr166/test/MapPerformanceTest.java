package jsr166.test;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import junit.framework.TestCase;


public class MapPerformanceTest extends TestCase {

    static final int absentSize = 1 << 17;
    static final int absentMask = absentSize - 1;
    static Object[] absent = new Object[absentSize];

    static final Object MISSING = new Object();

    static TestTimer timer = new TestTimer();

    public void testHashMap () {
        try {
            doTest("java.util.HashMap", 20, 100, false);
        }
        catch (Exception e) {
            fail("should not throw exception");
        }
    }

    public void testWeakHashMap () {
        try {
            doTest("java.util.WeakHashMap", 20, 100, false);
        }
        catch (Exception e) {
            e.printStackTrace(System.err);
            fail("should not throw exception");
        }
    }

    public void testConcurrentHashMap () {
        try {
            doTest("java.util.concurrent.ConcurrentHashMap", 20, 1000, false);
        }
        catch (Exception e) {
            fail("should not throw exception");
        }
    }

    private void doTest (String mapClassName, int numTests, int size, boolean doSerializeTest)
                         throws Exception {
        Class mapClass;
        try {
            mapClass = Class.forName(mapClassName);
        } catch(ClassNotFoundException e) {
            throw new RuntimeException("Class " + mapClassName + " not found.");
        }


        System.out.println("Testing " + mapClass.getName() + " size: " + size);

        for (int i = 0; i < absentSize; ++i) absent[i] = new Object();

        Object[] key = new Object[size];
        for (int i = 0; i < size; ++i) key[i] = new Object();

        forceMem(size * 8);

        for (int rep = 0; rep < numTests; ++rep) {
            runTest(newMap(mapClass), key);
        }

        TestTimer.printStats();


        if (doSerializeTest)
            stest(newMap(mapClass), size);
    }

    static Map<Object, Object> newMap(Class cl) {
        try {
            Map<Object, Object> m = (Map<Object, Object>)cl.newInstance();
            return m;
        } catch(Exception e) {
            throw new RuntimeException("Can't instantiate " + cl + ": " + e);
        }
    }


    static void runTest(Map<Object, Object> s, Object[] key) {
        shuffle(key);
        int size = key.length;
        long startTime = System.currentTimeMillis();
        test(s, key);
        long time = System.currentTimeMillis() - startTime;
    }

    static void forceMem(int n) {
        // force enough memory
        Long[] junk = new Long[n];
        for (int i = 0; i < junk.length; ++i) junk[i] = new Long(i);
        int sum = 0;
        for (int i = 0; i < junk.length; ++i)
            sum += (int)(junk[i].longValue() + i);
        if (sum == 0) System.out.println("Useless number = " + sum);
        junk = null;
        //        System.gc();
    }


    static void t1(String nm, int n, Map<Object, Object> s, Object[] key, int expect) {
        int sum = 0;
        int iters = 4;
        timer.start(nm, n * iters);
        for (int j = 0; j < iters; ++j) {
            for (int i = 0; i < n; i++) {
                if (s.get(key[i]) != null) ++sum;
            }
        }
        timer.finish();
        assertEquals("sum == expect * iters", expect * iters, sum);
    }

    static void t2(String nm, int n, Map<Object, Object> s, Object[] key, int expect) {
        int sum = 0;
        timer.start(nm, n);
        for (int i = 0; i < n; i++) {
            if (s.remove(key[i]) != null) ++sum;
        }
        timer.finish();
        assertEquals("sum == expect", expect, sum);
    }

    static void t3(String nm, int n, Map<Object, Object> s, Object[] key, int expect) {
        int sum = 0;
        timer.start(nm, n);
        for (int i = 0; i < n; i++) {
            if (s.put(key[i], absent[i & absentMask]) == null) ++sum;
        }
        timer.finish();
        assertEquals("sum == expect", expect, sum);
    }

    static void t4(String nm, int n, Map<Object, Object> s, Object[] key, int expect) {
        int sum = 0;
        timer.start(nm, n);
        for (int i = 0; i < n; i++) {
            if (s.containsKey(key[i])) ++sum;
        }
        timer.finish();
        assertEquals("sum == expect", expect, sum);
    }

    static void t5(String nm, int n, Map<Object, Object> s, Object[] key, int expect) {
        int sum = 0;
        timer.start(nm, n/2);
        for (int i = n-2; i >= 0; i-=2) {
            if (s.remove(key[i]) != null) ++sum;
        }
        timer.finish();
        assertEquals("sum == expect", expect, sum);
    }

    static void t6(String nm, int n, Map<Object, Object> s, Object[] k1, Object[] k2) {
        int sum = 0;
        timer.start(nm, n * 2);
        for (int i = 0; i < n; i++) {
            if (s.get(k1[i]) != null) ++sum;
            if (s.get(k2[i & absentMask]) != null) ++sum;
        }
        timer.finish();
        assertEquals("sum == n", n, sum);
    }

    static void t7(String nm, int n, Map<Object, Object> s, Object[] k1, Object[] k2) {
        int sum = 0;
        timer.start(nm, n * 2);
        for (int i = 0; i < n; i++) {
            if (s.containsKey(k1[i])) ++sum;
            if (s.containsKey(k2[i & absentMask])) ++sum;
        }
        timer.finish();
        assertEquals("sum == n", n, sum);
    }

    static void t8(String nm, int n, Map<Object, Object> s, Object[] key, int expect) {
        int sum = 0;
        timer.start(nm, n);
        for (int i = 0; i < n; i++) {
            if (s.get(key[i]) != null) ++sum;
        }
        timer.finish();
        assertEquals("sum == expect", expect, sum);
    }


    static void t9(Map s) {
        int sum = 0;
        int iters = 20;
        timer.start("ContainsValue (/n)     ", iters * s.size());
        int step = absentSize / iters;
        for (int i = 0; i < absentSize; i += step)
            if (s.containsValue(absent[i])) ++sum;
        timer.finish();
        assertFalse("sum should not be zero", sum == 0);
    }


    static void ktest(Map<Object, Object> s, int size, Object[] key) {
        timer.start("ContainsKey            ", size);
        Set ks = s.keySet();
        int sum = 0;
        for (int i = 0; i < size; i++) {
            if (ks.contains(key[i])) ++sum;
        }
        timer.finish();
        assertEquals("sum == size", size, sum);
    }


    static void ittest1(Map<Object, Object> s, int size) {
        int sum = 0;
        timer.start("Iter Key               ", size);
        for (Iterator it = s.keySet().iterator(); it.hasNext(); ) {
            if(it.next() != MISSING)
                ++sum;
        }
        timer.finish();
        assertEquals("sum == size", size, sum);
    }

    static void ittest2(Map<Object, Object> s, int size) {
        int sum = 0;
        timer.start("Iter Value             ", size);
        for (Iterator it = s.values().iterator(); it.hasNext(); ) {
            if(it.next() != MISSING)
                ++sum;
        }
        timer.finish();
        assertEquals("sum == size", size, sum);
    }
    static void ittest3(Map<Object, Object> s, int size) {
        int sum = 0;
        timer.start("Iter Entry             ", size);
        for (Iterator it = s.entrySet().iterator(); it.hasNext(); ) {
            if(it.next() != MISSING)
                ++sum;
        }
        timer.finish();
        assertEquals("sum == size", size, sum);
    }

    static void ittest4(Map<Object, Object> s, int size, int pos) {
        IdentityHashMap<Object, Object> seen = new IdentityHashMap<Object, Object>(size);
        assertEquals("s.size() == size", size, s.size());
        int sum = 0;
        timer.start("Iter XEntry            ", size);
        Iterator it = s.entrySet().iterator();
        Object k = null;
        Object v = null;
        for (int i = 0; i < size-pos; ++i) {
            Map.Entry x = (Map.Entry)(it.next());
            k = x.getKey();
            v = x.getValue();
            seen.put(k, k);
            if (x != MISSING)
                ++sum;
        }
        assertTrue("s should contain k", s.containsKey(k));
        it.remove();
        assertFalse("s should not contain k", s.containsKey(k));
        while (it.hasNext()) {
            Map.Entry x = (Map.Entry)(it.next());
            Object k2 = x.getKey();
            seen.put(k2, k2);
            if (x != MISSING)
                ++sum;
        }

        assertEquals("s.size() == size-1", size-1, s.size());
        s.put(k, v);
        assertEquals("seen.size() == size", size, seen.size());
        timer.finish();
        assertEquals("sum == size", size, sum);
        assertEquals("s.size() == size", size, s.size());
    }


    static void ittest(Map<Object, Object> s, int size) {
        ittest1(s, size);
        ittest2(s, size);
        ittest3(s, size);
        //        for (int i = 0; i < size-1; ++i)
        //            ittest4(s, size, i);
    }

    static void entest1(Hashtable ht, int size) {
        int sum = 0;

        timer.start("Iter Enumeration Key   ", size);
        for (Enumeration en = ht.keys(); en.hasMoreElements(); ) {
            if (en.nextElement() != MISSING)
                ++sum;
        }
        timer.finish();
        assertEquals("sum == size", size, sum);
    }

    static void entest2(Hashtable ht, int size) {
        int sum = 0;
        timer.start("Iter Enumeration Value ", size);
        for (Enumeration en = ht.elements(); en.hasMoreElements(); ) {
            if (en.nextElement() != MISSING)
                ++sum;
        }
        timer.finish();
        assertEquals("sum == size", size, sum);
    }


    static void entest3(Hashtable ht, int size) {
        int sum = 0;

        timer.start("Iterf Enumeration Key  ", size);
        Enumeration en = ht.keys();
        for (int i = 0; i < size; ++i) {
            if (en.nextElement() != MISSING)
                ++sum;
        }
        timer.finish();
        assertEquals("sum == size", size, sum);
    }

    static void entest4(Hashtable ht, int size) {
        int sum = 0;
        timer.start("Iterf Enumeration Value", size);
        Enumeration en = ht.elements();
        for (int i = 0; i < size; ++i) {
            if (en.nextElement() != MISSING)
                ++sum;
        }
        timer.finish();
        assertEquals("sum == size", size, sum);
    }

    static void entest(Map<Object, Object> s, int size) {
        if (s instanceof Hashtable) {
            Hashtable ht = (Hashtable)s;
            //            entest3(ht, size);
            //            entest4(ht, size);
            entest1(ht, size);
            entest2(ht, size);
            entest1(ht, size);
            entest2(ht, size);
            entest1(ht, size);
            entest2(ht, size);
        }
    }

    static void rtest(Map<Object, Object> s, int size) {
        timer.start("Remove (iterator)      ", size);
        for (Iterator it = s.keySet().iterator(); it.hasNext(); ) {
            it.next();
            it.remove();
        }
        timer.finish();
    }

    static void rvtest(Map<Object, Object> s, int size) {
        timer.start("Remove (iterator)      ", size);
        for (Iterator it = s.values().iterator(); it.hasNext(); ) {
            it.next();
            it.remove();
        }
        timer.finish();
    }


    static void dtest(Map<Object, Object> s, int size, Object[] key) {
        timer.start("Put (putAll)           ", size * 2);
        Map<Object, Object> s2 = null;
        try {
            s2 = (Map<Object, Object>) (s.getClass().newInstance());
            s2.putAll(s);
        }
        catch (Exception e) { e.printStackTrace(); return; }
        timer.finish();

        timer.start("Iter Equals            ", size * 2);
        boolean eqt = s2.equals(s) && s.equals(s2);
        assertTrue("eqt", eqt);
        timer.finish();

        timer.start("Iter HashCode          ", size * 2);
        int shc = s.hashCode();
        int s2hc = s2.hashCode();
        assertEquals("shc == s2hc", shc, s2hc);
        timer.finish();

        timer.start("Put (present)          ", size);
        s2.putAll(s);
        timer.finish();

        timer.start("Iter EntrySet contains ", size * 2);
        Set es2 = s2.entrySet();
        int sum = 0;
        for (Iterator i1 = s.entrySet().iterator(); i1.hasNext(); ) {
            Object entry = i1.next();
            if (es2.contains(entry)) ++sum;
        }
        timer.finish();
        assertEquals("sum == size", size, sum);

        t6("Get                    ", size, s2, key, absent);

        Object hold = s2.get(key[size-1]);
        s2.put(key[size-1], absent[0]);
        timer.start("Iter Equals            ", size * 2);
        eqt = s2.equals(s) && s.equals(s2);
        assertFalse("eqt should be false", eqt);
        timer.finish();

        timer.start("Iter HashCode          ", size * 2);
        int s1h = s.hashCode();
        int s2h = s2.hashCode();
        assertFalse("s1h should not be equal to s2h", s1h == s2h);
        timer.finish();

        s2.put(key[size-1], hold);
        timer.start("Remove (iterator)      ", size * 2);
        Iterator s2i = s2.entrySet().iterator();
        Set es = s.entrySet();
        while (s2i.hasNext())
            es.remove(s2i.next());
        timer.finish();

        assertTrue("s.isEmpty()", s.isEmpty());

        timer.start("Clear                  ", size);
        s2.clear();
        timer.finish();
        assertTrue("s2.isEmpty() && s.isEmpty()", s2.isEmpty() && s.isEmpty());
    }

    static void stest(Map<Object, Object> s, int size) throws Exception {
        if (!(s instanceof Serializable))
            return;
        System.out.print("Serialize              : ");

        for (int i = 0; i < size; i++) {
            s.put(new Integer(i), Boolean.TRUE);
        }

        long startTime = System.currentTimeMillis();

        FileOutputStream fs = new FileOutputStream("MapCheck.dat");
        ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(fs));
        out.writeObject(s);
        out.close();

        FileInputStream is = new FileInputStream("MapCheck.dat");
        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(is));
        Map m = (Map)in.readObject();

        long endTime = System.currentTimeMillis();
        long time = endTime - startTime;

        System.out.print(time + "ms");

        if (s instanceof IdentityHashMap) return;
        assertEquals("s should equal m", m, s);
    }


    static void test(Map<Object, Object> s, Object[] key) {
        int size = key.length;

        t3("Put (absent)           ", size, s, key, size);
        t3("Put (present)          ", size, s, key, 0);
        t7("ContainsKey            ", size, s, key, absent);
        t4("ContainsKey            ", size, s, key, size);
        ktest(s, size, key);
        t4("ContainsKey            ", absentSize, s, absent, 0);
        t6("Get                    ", size, s, key, absent);
        t1("Get (present)          ", size, s, key, size);
        t1("Get (absent)           ", absentSize, s, absent, 0);
        t2("Remove (absent)        ", absentSize, s, absent, 0);
        t5("Remove (present)       ", size, s, key, size / 2);
        t3("Put (half present)     ", size, s, key, size / 2);

        ittest(s, size);
        entest(s, size);
        t9(s);
        rtest(s, size);

        t4("ContainsKey            ", size, s, key, 0);
        t2("Remove (absent)        ", size, s, key, 0);
        t3("Put (presized)         ", size, s, key, size);
        dtest(s, size, key);
    }

    static class TestTimer {
        private String name;
        private long numOps;
        private long startTime;
        private String cname;

        static final java.util.TreeMap<Object, Object> accum = new java.util.TreeMap<Object, Object>();

        static void printStats() {
            for (Iterator it = accum.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry e = (Map.Entry)(it.next());
                Stats stats = ((Stats)(e.getValue()));
                int n = stats.number;
                double t;
                if (n > 0)
                    t = stats.sum / n;
                else
                    t = stats.least;
                long nano = Math.round(1000000.0 * t);
                System.out.println(e.getKey() + ": " + nano);
            }
        }

        void start(String name, long numOps) {
            this.name = name;
            this.cname = classify();
            this.numOps = numOps;
            startTime = System.currentTimeMillis();
        }


        String classify() {
            if (name.startsWith("Get"))
                return "Get                    ";
            else if (name.startsWith("Put"))
                return "Put                    ";
            else if (name.startsWith("Remove"))
                return "Remove                 ";
            else if (name.startsWith("Iter"))
                return "Iter                   ";
            else
                return null;
        }

        void finish() {
            long endTime = System.currentTimeMillis();
            long time = endTime - startTime;
            double timePerOp = ((double)time)/numOps;

            Object st = accum.get(name);
            if (st == null)
                accum.put(name, new Stats(timePerOp));
            else {
                Stats stats = (Stats) st;
                stats.sum += timePerOp;
                stats.number++;
                if (timePerOp < stats.least) stats.least = timePerOp;
            }

            if (cname != null) {
                st = accum.get(cname);
                if (st == null)
                    accum.put(cname, new Stats(timePerOp));
                else {
                    Stats stats = (Stats) st;
                    stats.sum += timePerOp;
                    stats.number++;
                    if (timePerOp < stats.least) stats.least = timePerOp;
                }
            }

        }

    }

    static class Stats {
        double sum = 0;
        double least;
        int number = 0;
        Stats(double t) { least = t; }
    }

    static Random rng = new Random();

    static void shuffle(Object[] keys) {
        int size = keys.length;
        for (int i=size; i>1; i--) {
            int r = rng.nextInt(i);
            Object t = keys[i-1];
            keys[i-1] = keys[r];
            keys[r] = t;
        }
    }

}

