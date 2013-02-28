/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class IntegerSum {
    static final int SIZE = 1000000;
    static final AtomicInteger checksum = new AtomicInteger();
    static boolean print;
    static boolean allClasses = false; // true if also test dumb/default classes

    static final BinaryOperator<Integer> SUM = (Integer x, Integer y) -> x + y;

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

    static String sep() { return print? "\n" : " "; }

    static void allTests(int size, int trials) throws Exception {
        System.out.println("---------------------------------------------");
        System.out.println("size: " + size + " trials: " + trials);
        Integer[] keys = new Integer[size];
        int ksum = 0;
        for (int i = 0; i < size; ++i) {
            ksum += i;
            keys[i] = Integer.valueOf(i);
        }
        int vsum = ksum;
        Integer[] vals = Arrays.copyOf(keys, size);
        shuffle(vals);
        List<Integer> klist = Arrays.asList(keys);
        List<Integer> vlist = Arrays.asList(vals);

        isptest(klist, ksum, size, trials);
        System.out.print("Arrays.asList.keys" + sep());
        isptest(vlist, vsum, size, trials);
        System.out.print("Arrays.asList.values" + sep());

        ctest(new ArrayList<Integer>(), klist, ksum, size, trials);
        ctest(new ArrayList<Integer>(), vlist, vsum, size, trials);
        ctest(new Vector<Integer>(), klist, ksum, size, trials);
        ctest(new Vector<Integer>(), vlist, vsum, size, trials);
        ctest(new ArrayDeque<Integer>(), klist, ksum, size, trials);
        ctest(new ArrayDeque<Integer>(), vlist, vsum, size, trials);
        ctest(new CopyOnWriteArrayList<Integer>(), klist, ksum, size, trials);
        ctest(new CopyOnWriteArrayList<Integer>(), vlist, vsum, size, trials);
        ctest(new PriorityQueue<Integer>(), klist, ksum, size, trials);
        ctest(new PriorityQueue<Integer>(), vlist, vsum, size, trials);
        ctest(new HashSet<Integer>(), klist, ksum, size, trials);
        ctest(new HashSet<Integer>(), vlist, vsum, size, trials);
        ctest(ConcurrentHashMap.<Integer>newKeySet(), klist, ksum, size, trials);
        ctest(ConcurrentHashMap.<Integer>newKeySet(), vlist, vsum, size, trials);
        ctest(new TreeSet<Integer>(), klist, ksum, size, trials);
        ctest(new TreeSet<Integer>(), vlist, vsum, size, trials);
        ctest(ConcurrentSkipListMap.<Integer>newKeySet(), klist, ksum, size, trials);
        ctest(ConcurrentSkipListMap.<Integer>newKeySet(), vlist, vsum, size, trials);

        mtest(new HashMap<Integer,Integer>(), keys, vals, ksum, vsum, size, trials);
        mtest(new IdentityHashMap<Integer,Integer>(), keys, vals, ksum, vsum, size, trials);
        mtest(new WeakHashMap<Integer,Integer>(), keys, vals, ksum, vsum, size, trials);
        mtest(new ConcurrentHashMap<Integer,Integer>(), keys, vals, ksum, vsum, size, trials);

        mtest(new TreeMap<Integer,Integer>(), keys, vals, ksum, vsum, size, trials);
        mtest(new ConcurrentSkipListMap<Integer,Integer>(), keys, vals, ksum, vsum, size, trials);

        if (allClasses) {
            mtest(new Hashtable<Integer,Integer>(), keys, vals, ksum, vsum, size, trials);
            mtest(new LinkedHashMap<Integer,Integer>(), keys, vals, ksum, vsum, size, trials);
            ctest(new LinkedHashSet<Integer>(), klist, ksum, size, trials);
            ctest(new LinkedHashSet<Integer>(), vlist, vsum, size, trials);
            ctest(new LinkedList<Integer>(), klist, ksum, size, trials);
            ctest(new LinkedList<Integer>(), vlist, vsum, size, trials);
            ctest(new ConcurrentLinkedQueue<Integer>(), klist, ksum, size, trials);
            ctest(new ConcurrentLinkedQueue<Integer>(), vlist, vsum, size, trials);
            ctest(new ConcurrentLinkedDeque<Integer>(), klist, ksum, size, trials);
            ctest(new ConcurrentLinkedDeque<Integer>(), vlist, vsum, size, trials);
            ctest(new LinkedBlockingQueue<Integer>(SIZE), klist, ksum, size, trials);
            ctest(new LinkedBlockingQueue<Integer>(SIZE), vlist, vsum, size, trials);
            ctest(new LinkedBlockingDeque<Integer>(SIZE), klist, ksum, size, trials);
            ctest(new LinkedBlockingDeque<Integer>(SIZE), vlist, vsum, size, trials);
            ctest(new LinkedTransferQueue<Integer>(), klist, ksum, size, trials);
            ctest(new LinkedTransferQueue<Integer>(), vlist, vsum, size, trials);
            ctest(new ArrayBlockingQueue<Integer>(SIZE), klist, ksum, size, trials);
            ctest(new ArrayBlockingQueue<Integer>(SIZE), vlist, vsum, size, trials);
            ctest(new PriorityBlockingQueue<Integer>(SIZE), klist, ksum, size, trials);
            ctest(new PriorityBlockingQueue<Integer>(SIZE), vlist, vsum, size, trials);
        }

        if (checksum.get() != 0)
            throw new Error("bad computation");
    }

    static void ctest(Collection<Integer> c, List<Integer> klist, int ksum, int size, int trials) 
        throws Exception {
        String cn = c.getClass().getName();
        if (cn.startsWith("java.util.concurrent."))
            cn = cn.substring(21);
        else if (cn.startsWith("java.util."))
            cn = cn.substring(10);
        c.addAll(klist);
        isptest(c, ksum, size, trials);
        System.out.print(cn + sep());
    }

    static void mtest(Map<Integer,Integer> m, Integer[] keys, Integer[] vals, int ksum, int vsum, int size, int trials) throws Exception {
        String cn = m.getClass().getName();
        if (cn.startsWith("java.util.concurrent."))
            cn = cn.substring(21);
        else if (cn.startsWith("java.util."))
            cn = cn.substring(10);
        for (int i = 0; i < size; ++i)
            m.put(keys[i], vals[i]);
        isptest(m.keySet(), ksum, size, trials);
        System.out.print(cn + ".keys" + sep());
        isptest(m.values(), vsum, size, trials);
        System.out.print(cn + ".vals" + sep());
    }

    static void isptest(Collection<Integer> c, int sum, int size, int trials) throws Exception {
        long ti = itest(c, sum, trials);
        long ts = stest(c, sum, trials);
        long tp = ptest(c, sum, trials);
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

    static long itest(Collection<Integer> c, int sum, int trials) throws Exception {
        if (c == null) throw new Error();
        Thread.sleep(250);
        long tlast =  System.nanoTime();
        for (int i = 0; i < trials; ++i) {
            Integer psum = Integer.valueOf(checksum.get());
            for (Integer x : c) 
                psum = SUM.apply(psum, x);
            checksum.getAndAdd(sum - psum);
        }
        return System.nanoTime() - tlast;
    }

    static long stest(Collection<Integer> c, int sum, int trials) throws Exception {
        if (c == null) throw new Error();
        Thread.sleep(250);
        long tlast =  System.nanoTime();
        for (int i = 0; i < trials; ++i) {
            int psum = c.stream().reduce
                (Integer.valueOf(checksum.get()), SUM);
            checksum.getAndAdd(sum - psum);
        }
        return System.nanoTime() - tlast;
    }

    static long ptest(Collection<Integer> c, int sum, int trials) throws Exception {
        if (c == null) throw new Error();
        Thread.sleep(250);
        long tlast =  System.nanoTime();
        for (int i = 0; i < trials; ++i) {
            int psum = c.parallelStream().reduce
                (Integer.valueOf(checksum.get()), SUM);
            checksum.getAndAdd(sum - psum);
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
