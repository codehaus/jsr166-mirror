/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class IntegerMax {
    static final int SIZE = 1000000;
    static final AtomicInteger checksum = new AtomicInteger();
    static boolean print;
    static boolean allClasses = false; // true if also test dumb/default classes
    static final BinaryOperator<Integer> MAX =
        (Integer x, Integer y) -> x >= y ? x : y;

    public static void main(String[] args) throws Exception {
        if (args.length > 0)
            allClasses = true;
        print = false;
        System.out.println("warmup...");
        allTests(10000, 100);
        System.out.println("...");
        print = true;
        int step = 10;
        //        int step = 100;
        for (int reps = 0; reps < 2; ++reps) {
            int trials = SIZE;
            for (int size = 1; size <= SIZE; size *= step) {
                allTests(size, trials);
                trials /= (step / 2);
            }
        }
    }

    static String sep() { return print ? "\n" : " "; }

    static void allTests(int size, int trials) throws Exception {
        System.out.println("---------------------------------------------");
        System.out.println("size: " + size + " trials: " + trials);
        Integer[] keys = new Integer[size];
        for (int i = 0; i < size; ++i)
            keys[i] = Integer.valueOf(i);
        Integer[] vals = Arrays.copyOf(keys, size);
        shuffle(keys);
        shuffle(vals);
        List<Integer> klist = Arrays.asList(keys);
        List<Integer> vlist = Arrays.asList(vals);
        int kmax = size - 1;
        int vmax = size - 1;

        isptest(klist, kmax, size, trials);
        System.out.print("Arrays.asList.keys" + sep());
        isptest(vlist, vmax, size, trials);
        System.out.print("Arrays.asList.values" + sep());

        ctest(new ArrayList<Integer>(), klist, kmax, size, trials);
        ctest(new ArrayList<Integer>(), vlist, vmax, size, trials);
        ctest(new Vector<Integer>(), klist, kmax, size, trials);
        ctest(new Vector<Integer>(), vlist, vmax, size, trials);
        ctest(new ArrayDeque<Integer>(), klist, kmax, size, trials);
        ctest(new ArrayDeque<Integer>(), vlist, vmax, size, trials);
        ctest(new CopyOnWriteArrayList<Integer>(), klist, kmax, size, trials);
        ctest(new CopyOnWriteArrayList<Integer>(), vlist, vmax, size, trials);
        ctest(new PriorityQueue<Integer>(), klist, kmax, size, trials);
        ctest(new PriorityQueue<Integer>(), vlist, vmax, size, trials);

        ctest(new HashSet<Integer>(), klist, kmax, size, trials);
        ctest(new HashSet<Integer>(), vlist, vmax, size, trials);
        ctest(ConcurrentHashMap.<Integer>newKeySet(), klist, kmax, size, trials);
        ctest(ConcurrentHashMap.<Integer>newKeySet(), vlist, vmax, size, trials);
        ctest(new TreeSet<Integer>(), klist, kmax, size, trials);
        ctest(new TreeSet<Integer>(), vlist, vmax, size, trials);
        ctest(new ConcurrentSkipListSet<Integer>(), klist, kmax, size, trials);
        ctest(new ConcurrentSkipListSet<Integer>(), vlist, vmax, size, trials);

        mtest(new HashMap<Integer,Integer>(), keys, vals, kmax, vmax, size, trials);
        mtest(new IdentityHashMap<Integer,Integer>(), keys, vals, kmax, vmax, size, trials);
        mtest(new WeakHashMap<Integer,Integer>(), keys, vals, kmax, vmax, size, trials);
        mtest(new ConcurrentHashMap<Integer,Integer>(), keys, vals, kmax, vmax, size, trials);

        mtest(new TreeMap<Integer,Integer>(), keys, vals, kmax, vmax, size, trials);
        mtest(new ConcurrentSkipListMap<Integer,Integer>(), keys, vals, kmax, vmax, size, trials);

        if (allClasses) {
            mtest(new Hashtable<Integer,Integer>(), keys, vals, kmax, vmax, size, trials);
            mtest(new LinkedHashMap<Integer,Integer>(), keys, vals, kmax, vmax, size, trials);
            ctest(new LinkedHashSet<Integer>(), klist, kmax, size, trials);
            ctest(new LinkedHashSet<Integer>(), vlist, vmax, size, trials);
            ctest(new LinkedList<Integer>(), klist, kmax, size, trials);
            ctest(new LinkedList<Integer>(), vlist, vmax, size, trials);
            //            catest(new LinkedList<Integer>(), klist, kmax, size, trials);
            //            catest(new LinkedList<Integer>(), vlist, vmax, size, trials);
            ctest(new ConcurrentLinkedQueue<Integer>(), klist, kmax, size, trials);
            ctest(new ConcurrentLinkedQueue<Integer>(), vlist, vmax, size, trials);
            ctest(new ConcurrentLinkedDeque<Integer>(), klist, kmax, size, trials);
            ctest(new ConcurrentLinkedDeque<Integer>(), vlist, vmax, size, trials);
            ctest(new LinkedBlockingQueue<Integer>(SIZE), klist, kmax, size, trials);
            ctest(new LinkedBlockingQueue<Integer>(SIZE), vlist, vmax, size, trials);
            ctest(new LinkedBlockingDeque<Integer>(SIZE), klist, kmax, size, trials);
            ctest(new LinkedBlockingDeque<Integer>(SIZE), vlist, vmax, size, trials);
            ctest(new LinkedTransferQueue<Integer>(), klist, kmax, size, trials);
            ctest(new LinkedTransferQueue<Integer>(), vlist, vmax, size, trials);
            ctest(new ArrayBlockingQueue<Integer>(SIZE), klist, kmax, size, trials);
            ctest(new ArrayBlockingQueue<Integer>(SIZE), vlist, vmax, size, trials);
            ctest(new PriorityBlockingQueue<Integer>(SIZE), klist, kmax, size, trials);
            ctest(new PriorityBlockingQueue<Integer>(SIZE), vlist, vmax, size, trials);
        }

        if (checksum.get() != 0) throw new Error("bad computation");
    }

    static void ctest(Collection<Integer> c, List<Integer> klist, int kmax, int size, int trials)
        throws Exception {
        String cn = c.getClass().getName();
        if (cn.startsWith("java.util.concurrent."))
            cn = cn.substring(21);
        else if (cn.startsWith("java.util."))
            cn = cn.substring(10);
        c.addAll(klist);
        isptest(c, kmax, size, trials);
        System.out.print(cn + sep());
    }

    static void catest(Collection<Integer> c, List<Integer> klist, int kmax, int size, int trials)
        throws Exception {
        String cn = c.getClass().getName();
        if (cn.startsWith("java.util.concurrent."))
            cn = cn.substring(21);
        else if (cn.startsWith("java.util."))
            cn = cn.substring(10);
        cn = cn + ".toArrayList";
        c.addAll(klist);
        ArrayList<Integer> ac = new ArrayList<Integer>(c);
        isptest(ac, kmax, size, trials);
        System.out.print(cn + sep());
    }

    static void mtest(Map<Integer,Integer> m, Integer[] keys, Integer[] vals, int kmax, int vmax, int size, int trials) throws Exception {
        String cn = m.getClass().getName();
        if (cn.startsWith("java.util.concurrent."))
            cn = cn.substring(21);
        else if (cn.startsWith("java.util."))
            cn = cn.substring(10);
        for (int i = 0; i < size; ++i)
            m.put(keys[i], vals[i]);
        isptest(m.keySet(), kmax, size, trials);
        System.out.print(cn + ".keys" + sep());
        isptest(m.values(), vmax, size, trials);
        System.out.print(cn + ".vals" + sep());
    }

    static void isptest(Collection<Integer> c, int max, int size, int trials) throws Exception {
        long ti = itest(c, max, trials);
        long ts = stest(c, max, trials);
        long tp = ptest(c, max, trials);
        if (checksum.get() != 0) throw new Error("bad computation");
        if (print) {
            long scale = (long)size * trials;
            double di = ((double)ti) / scale;
            double ds = ((double)ts) / scale;
            double dp = ((double)tp) / scale;
            System.out.printf("n:%7d ", size);
            System.out.printf("i:%8.2f ", di);
            System.out.printf("s:%8.2f ", ds);
            System.out.printf("p:%8.2f   ", dp);
        }
    }

    static long itest(Collection<Integer> c, int max, int trials) throws Exception {
        if (c == null) throw new Error();
        Thread.sleep(250);
        long tlast = System.nanoTime();
        for (int i = 0; i < trials; ++i) {
            Integer pmax = Integer.valueOf(Integer.MIN_VALUE - checksum.get());
            for (Integer x : c)
                pmax = MAX.apply(pmax, x);
            checksum.getAndAdd(max - pmax);
        }
        return System.nanoTime() - tlast;
    }

    static long stest(Collection<Integer> c, int max, int trials) throws Exception {
        if (c == null) throw new Error();
        Thread.sleep(250);
        long tlast = System.nanoTime();
        for (int i = 0; i < trials; ++i) {
            int pmax = c.stream().reduce
                (Integer.valueOf(Integer.MIN_VALUE - checksum.get()), MAX);
            checksum.getAndAdd(max - pmax);
        }
        return System.nanoTime() - tlast;
    }

    static long ptest(Collection<Integer> c, int max, int trials) throws Exception {
        if (c == null) throw new Error();
        Thread.sleep(250);
        long tlast = System.nanoTime();
        for (int i = 0; i < trials; ++i) {
            int pmax = c.parallelStream().reduce
                (Integer.valueOf(Integer.MIN_VALUE - checksum.get()), MAX);
            checksum.getAndAdd(max - pmax);
        }
        return System.nanoTime() - tlast;
    }

    // misc

    static final long NPS = (1000L * 1000 * 1000);
    static double elapsedTime(long startTime) {
        return (double)(System.nanoTime() - startTime) / NPS;
    }

    static void shuffle(Object[] a) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = a.length; i > 1; i--) {
            Object t = a[i-1];
            int r = rng.nextInt(i);
            a[i-1] = a[r];
            a[r] = t;
        }
    }

}
