/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

import java.util.*;
import java.math.*;

/**
 * A micro-benchmark with key types and operation mixes roughly
 * corresponding to some real programs.
 * 
 * The main results are a table of approximate nanoseconds per
 * element-operation (averaged across get, put etc) for each type,
 * across a range of map sizes. It also includes category "Mixed"
 * that includes elements of multiple types including those with
 * identical hash codes.
 *
 * The program includes a bunch of microbenchmarking safeguards that
 * might underestimate typical performance. For example, by using many
 * different key types and exercising them in warmups it disables most
 * dynamic type specialization.  Some test classes, like Float and
 * BigDecimal are included not because they are commonly used as keys,
 * but because they can be problematic for some map implementations.
 * 
 * By default, it creates and inserts in order dense numerical keys
 * and searches for keys in scrambled order. Use "r" as second arg to
 * instead use random numerical values, and "s" as third arg to search
 * in insertion order.
 */
public class MapMicroBenchmark {
    static Class mapClass;
    static boolean randomSearches = true;
    static boolean randomKeys = false;

    // Nanoseconds per run
    static final long NANOS_PER_JOB = 6L * 1000L*1000L*1000L;
    static final long NANOS_PER_WARMUP = 100L*1000L*1000L;

    // map operations per item per iteration -- change if job.work changed
    static final int OPS_PER_ITER = 11;
    static final int MIN_ITERS_PER_TEST = 3;
    static final int MAX_ITERS_PER_TEST = 1000000; // avoid runaway

    // sizes are at halfway points for HashMap default resizes
    static final int firstSize = 9;
    static final int sizeStep = 4; // each size 4X last
    static final int nsizes = 9;
    static final int[] sizes = new int[nsizes];

    public static void main(String[] args) throws Throwable {
        if (args.length == 0) {
            System.out.println("Usage: java MapMicroBenchmark className [r|s]keys [r|s]searches");
            return;
        }
            
        mapClass = Class.forName(args[0]);

        if (args.length > 1) {
            if (args[1].startsWith("s"))
                randomKeys = false;
            else if (args[1].startsWith("r"))
                randomKeys = true;
        }
        if (args.length > 2) {
            if (args[2].startsWith("s"))
                randomSearches = false;
            else if (args[2].startsWith("r"))
                randomSearches = true;
        }

        System.out.print("Class " + mapClass.getName());
        if (randomKeys)
            System.out.print(" random keys");
        else
            System.out.print(" sequential keys");
        if (randomSearches)
            System.out.print(" randomized searches");
        else
            System.out.print(" sequential searches");

        System.out.println();

        int n = firstSize;
        for (int i = 0; i < nsizes - 1; ++i) {
            sizes[i] = n;
            n *= sizeStep;
        }
        sizes[nsizes - 1] = n;

        int njobs = 9;

        Object[] os = new Object[n];
        Object[] ss = new Object[n];
        Object[] is = new Object[n];
        Object[] ls = new Object[n];
        Object[] fs = new Object[n];
        Object[] ds = new Object[n];
        Object[] bs = new Object[n];
        Object[] es = new Object[n];
        Object[] ms = new Object[n];

        for (int i = 0; i < n; i++) {
            os[i] = new Object();
        }

        // To guarantee uniqueness, use xorshift for "random" versions
        int rnd = 3122688;
        for (int i = 0; i < n; i++) {
            rnd = xorshift(rnd);
            int j = randomKeys? rnd : i;
            ss[i] = String.valueOf(j);
        }

        for (int i = 0; i < n; i++) {
            rnd = xorshift(rnd);
            int j = randomKeys? rnd : i;
            is[i] = Integer.valueOf(j);
        }

        for (int i = 0; i < n; i++) {
            rnd = xorshift(rnd);
            int j = randomKeys? rnd : i;
            ls[i] = Long.valueOf((long)j);
        }

        for (int i = 0; i < n; i++) {
            //            rnd = xorshift(rnd);
            //            int j = randomKeys? rnd : i;
            fs[i] = Float.valueOf((float)i); // can't use random for float
        }

        for (int i = 0; i < n; i++) {
            rnd = xorshift(rnd);
            int j = randomKeys? rnd : i;
            ds[i] = Double.valueOf((double)j);
        }

        for (int i = 0; i < n; i++) {
            rnd = xorshift(rnd);
            int j = randomKeys? rnd : i;
            bs[i] = BigInteger.valueOf(j);
        }

        for (int i = 0; i < n; i++) {
            rnd = xorshift(rnd);
            int j = randomKeys? rnd : i;
            es[i] = BigDecimal.valueOf(j);
        }

        Job[] jobs = new Job[njobs];
        jobs[0] = new Job("Object    ", os, Object.class);
        jobs[1] = new Job("String    ", ss, String.class);
        jobs[2] = new Job("Integer   ", is, Integer.class);
        jobs[3] = new Job("Long      ", ls, Long.class);
        jobs[4] = new Job("Float     ", fs, Float.class);
        jobs[5] = new Job("Double    ", ds, Double.class);
        jobs[6] = new Job("BigInteger", bs, BigInteger.class);
        jobs[7] = new Job("BigDecimal", es, BigDecimal.class);

        for (int i = 0; i < n; i +=2) {
            rnd = xorshift(rnd);
            int j = (rnd & 7); // change if njobs changes
            ms[i] = jobs[j].items[i];
            j = (j + 1) & 7;
            ms[i+1] = jobs[j].items[i];
        }

        jobs[8] = new Job("Mixed     ", ms, Object.class);

        warmup1(jobs[8]);
        warmup2(jobs);
        warmup1(jobs[8]);
        warmup3(jobs);
        warmup1(jobs[8]);
        Thread.sleep(500);
	time(jobs);
    }

    static void runWork(Job[] jobs, int minIters, int maxIters, long timeLimit) throws Throwable {
        for (int k = 0; k <  nsizes; ++k) {
            int len = sizes[k];
            for (int i = 0; i < jobs.length; i++) {
                Thread.sleep(50);
                jobs[i].nanos[k] = jobs[i].work(len, minIters, maxIters, timeLimit);
                System.out.print(".");
            }
        }
        System.out.println();
    }

    // First warmup -- run only mixed job to discourage type specialization
    static void warmup1(Job job) throws Throwable {
        for (int k = 0; k <  nsizes; ++k)
            job.work(sizes[k], 1, 1, 0);
    }

    // Second, run each once
    static void warmup2(Job[] jobs) throws Throwable {
        System.out.print("warm up");
        runWork(jobs, 1, 1, 0);
        long ck = jobs[0].checkSum;
        for (int i = 1; i < jobs.length - 1; i++) {
            if (jobs[i].checkSum != ck)
                throw new Error("CheckSum");
        }
    }

    // Third: short timed runs
    static void warmup3(Job[] jobs) throws Throwable {
        System.out.print("warm up");
        runWork(jobs, 1, MAX_ITERS_PER_TEST, NANOS_PER_WARMUP);
    }

    static void time(Job[] jobs) throws Throwable {
        System.out.print("running");
        runWork(jobs, MIN_ITERS_PER_TEST, MAX_ITERS_PER_TEST, NANOS_PER_JOB);

        System.out.print("Type/Size:");
        for (int k = 0; k < nsizes; ++k)
            System.out.printf("%7d", sizes[k]);
        System.out.println();

        long[] aves = new long[nsizes];
        int njobs = jobs.length;

	for (int i = 0; i < njobs; i++) {
            System.out.print(jobs[i].name);
            for (int k = 0; k < nsizes; ++k) {
                long nanos = jobs[i].nanos[k];
                System.out.printf("%7d", nanos);
                aves[k] += nanos;
            }
            System.out.println();
        }

        System.out.println();
        System.out.print("average   ");
        for (int k = 0; k < nsizes; ++k)
            System.out.printf("%7d", (aves[k] / njobs));
        System.out.println("\n");
    }


    static final class Job {
	final String name;
        final Class elementClass;
        long[] nanos = new long[nsizes];
        final Object[] items;
        Object[] searches;
        volatile long checkSum;
        volatile int lastSum;
        Job(String name, Object[] items, Class elementClass) {
            this.name = name;
            this.items = items;
            this.elementClass = elementClass;
            if (randomSearches) {
                scramble(items);
                this.searches = new Object[items.length];
                System.arraycopy(items, 0, searches, 0, items.length);
                scramble(searches);
            }
            else
                this.searches = items;
        }

        public long work(int len, int minIters, int maxIters, long timeLimit) {
            Map m;
            try {
                m = (Map)mapClass.newInstance();
            } catch(Exception e) {
                throw new RuntimeException("Can't instantiate " + mapClass + ": " + e);
            }
            Object[] ins = items;
            Object[] keys = searches;

            if (ins.length < len || keys.length < len)
                throw new Error(name);
            int half = len / 2;
            int quarter = half / 2;
            int sum = lastSum;
            long startTime = System.nanoTime();
            long elapsed;
            int j = 0;
            for (;;) {
                for (int i = 0; i < half; ++i) {
                    Object x = ins[i];
                    if (m.put(x, x) == null)
                        ++sum;
                }
                checkSum += sum ^ (sum << 1); // help avoid loop merging
                sum += len - half;
                for (int i = 0; i < len; ++i) {
                    Object x = keys[i];
                    Object v = m.get(x); 
                    if (elementClass.isInstance(v)) // touch v
                        ++sum;
                }
                checkSum += sum ^ (sum << 2);
                for (int i = half; i < len; ++i) {
                    Object x = ins[i];
                    if (m.put(x, x) == null)
                        ++sum;
                }
                checkSum += sum ^ (sum << 3);
                for (Object e : m.keySet()) {
                    if (elementClass.isInstance(e)) 
                        ++sum;
                }
                checkSum += sum ^ (sum << 4);
                for (Object e : m.values()) {
                    if (elementClass.isInstance(e)) 
                        ++sum;
                }
                checkSum += sum ^ (sum << 5);
                for (int i = len - 1; i >= 0; --i) {
                    Object x = keys[i];
                    Object v = m.get(x);
                    if (elementClass.isInstance(v))
                        ++sum;
                }
                checkSum += sum ^ (sum << 6);
                for (int i = 0; i < len; ++i) {
                    Object x = ins[i];
                    Object v = m.get(x);
                    if (elementClass.isInstance(v))
                        ++sum;
                }
                checkSum += sum ^ (sum << 7);
                for (int i = 0; i < len; ++i) {
                    Object x = keys[i];
                    Object v = ins[i];
                    if (m.put(x, v) == x)
                        ++sum;
                }
                checkSum += sum ^ (sum << 8);
                for (int i = 0; i < len; ++i) {
                    Object x = keys[i];
                    Object v = ins[i];
                    if (v == m.get(x))
                        ++sum;
                }
                checkSum += sum ^ (sum << 9);
                for (int i = len - 1; i >= 0; --i) {
                    Object x = ins[i];
                    Object v = m.get(x);
                    if (elementClass.isInstance(v))
                        ++sum;
                }
                checkSum += sum ^ (sum << 10);
                for (int i = len - 1; i >= 0; --i) {
                    Object x = keys[i];
                    Object v = ins[i];
                    if (v == m.get(x))
                        ++sum;
                }
                checkSum += sum ^ (sum << 11);
                for (int i = 0; i < quarter; ++i) {
                    Object x = keys[i];
                    if (m.remove(x) != null)
                        ++sum;
                }
                m.clear();
                sum += len - quarter;
                checkSum += sum ^ (sum << 12);

                elapsed = System.nanoTime() - startTime;
                ++j;
                if (j >= minIters &&
                    (j >= maxIters || elapsed >= timeLimit))
                    break;
            }
            long ops = ((long)j) * len * OPS_PER_ITER;
            if (sum != lastSum + (int)ops)
                throw new Error(name);
            lastSum = sum;
            return elapsed / ops;
        }

    }

    static final int xorshift(int seed) { 
        seed ^= seed << 1; 
        seed ^= seed >>> 3; 
        seed ^= seed << 10;
        return seed;
    }


    static final Random rng = new Random(3122688);

    // Shuffle the subarrays for each size. This doesn't fully
    // randomize, but the remaining partial locality is arguably a bit
    // more realistic
    static void scramble(Object[] a) {
        for (int k = 0; k < sizes.length; ++k) {
            int origin = k == 0? 0 : sizes[k-1];
            for (int i = sizes[k]; i > origin + 1; i--) {
                Object t = a[i-1];
                int r = rng.nextInt(i - origin) + origin;
                a[i-1] = a[r];
                a[r] = t;
            }
        }
    }            

    // plain array shuffle
    static void shuffle(Object[] a, int size) {
        for (int i= size; i>1; i--) {
            Object t = a[i-1];
            int r = rng.nextInt(i);
            a[i-1] = a[r];
            a[r] = t;
        }
    }



}

