/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

import jsr166y.forkjoin.*;
import java.util.*;
import java.util.concurrent.*;

public class MapReduceDemo {
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Sequential version, for performance comparison
     */
    static <T, U> U seqMapReduce(T[] array,
                                 Ops.Mapper<T, U> mapper,
                                 Ops.Reducer<U> reducer,
                                 U base) {
        int n = array.length;
        U x = base;
        for (int i = 0; i < n; ++i) 
            x = reducer.combine(x, mapper.map(array[i]));
        return x;
    }



    // sample functions
    static final class GetNext implements Ops.Mapper<Rand, Integer> {
        public Integer map(Rand x) { 
            return x.next();
        }
    }

    static final class Accum implements Ops.Reducer<Integer> {
        public Integer combine(Integer a, Integer b) {
            int x = a;
            int y = b;
            for (int i = 0; i < (1 << 10); ++i) {
                x = 36969 * (x & 65535) + (x >> 16);
                y =  y * 134775813 + 1;
            }
            return x + y;
        }
    }

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    public static void main(String[] args) throws Exception {
        int n = 1 << 21;
        Rand[] array = new Rand[n];
        for (int i = 0; i < n; ++i) 
            array[i] = new Rand(i);
        final Ops.Mapper<Rand, Integer> getNext = new GetNext();
        final Ops.Reducer<Integer> accum = new Accum();
        final Integer zero = 0;
        long last, now;
        double elapsed;
        last = System.nanoTime();
        int sum = 0;
        for (int k = 0; k < 2; ++k) {
            sum += seqMapReduce(array, getNext, accum, zero);
            now = System.nanoTime();
            elapsed = (double)(now - last) / NPS;
            last = now;
            System.out.printf("seq:    %7.3f\n", elapsed);
        }
        Thread.sleep(100);
        ForkJoinPool fjp = new ForkJoinPool(1);
        ParallelArray<Rand> pa = new ParallelArray<Rand>(fjp, array);
        for (int i = 1; i <= NCPU; i <<= 1) {
            fjp.setPoolSize(i);
            last = System.nanoTime();
            for (int k = 0; k < 2; ++k) {
                sum += pa.withMapping(getNext).reduce(accum, zero);
                now = System.nanoTime();
                elapsed = (double)(now - last) / NPS;
                last = now;
                System.out.printf("ps %2d:  %7.3f\n", i, elapsed);
            }
        }
        fjp.shutdownNow();
        fjp.awaitTermination(1, TimeUnit.SECONDS);
        Thread.sleep(100);
        if (sum == 0) System.out.print(" ");
    }

    /**
     * Unsynchronized version of java.util.Random algorithm.
     */
    public static final class Rand {
        private final static long multiplier = 0x5DEECE66DL;
        private final static long addend = 0xBL;
        private final static long mask = (1L << 48) - 1;
        private long seed;

        Rand(long s) {
            seed = s;
        }
        public int next() {
            long nextseed = (seed * multiplier + addend) & mask;
            seed = nextseed;
            return ((int)(nextseed >>> 17)) & 0x7FFFFFFF;
        }
    }

}
