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
            x ^= x << 6; 
            x ^= x >>> 21; 
            x ^= (x << 7);
            y ^= y << 6; 
            y ^= y >>> 21; 
            y ^= (y << 7);
            return x + y;
        }
    }

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    public static void main(String[] args) throws Exception {
        int n = 1 << 18;
        int reps = 1 << 8;
        Rand[] array = new Rand[n];
        for (int i = 0; i < n; ++i) 
            array[i] = new Rand(i+1);
        ForkJoinPool fjp = new ForkJoinPool(1);
        ParallelArray<Rand> pa = new ParallelArray<Rand>(fjp, array);
        final Ops.Mapper<Rand, Integer> getNext = new GetNext();
        final Ops.Reducer<Integer> accum = new Accum();
        final Integer zero = 0;
        long last, now;
        double elapsed;
        int sum = 0;
        for (int j = 0; j < 2; ++j) {
            last = System.nanoTime();
            for (int k = 0; k < reps; ++k) {
                sum += seqMapReduce(array, getNext, accum, zero);
            }
            now = System.nanoTime();
            elapsed = (double)(now - last) / NPS;
            last = now;
            System.out.printf("sequential:    %7.3f\n", elapsed);
            for (int i = 2; i <= NCPU; i <<= 1) {
                fjp.setPoolSize(i);
                last = System.nanoTime();
                for (int k = 0; k < reps; ++k) {
                    sum += pa.withMapping(getNext).reduce(accum, zero);
                }
                now = System.nanoTime();
                elapsed = (double)(now - last) / NPS;
                last = now;
                System.out.printf("poolSize %3d:  %7.3f\n", i, elapsed);
            }
            for (int i = NCPU; i >= 1; i >>>= 1) {
                fjp.setPoolSize(i);
                last = System.nanoTime();
                for (int k = 0; k < reps; ++k) {
                    sum += pa.withMapping(getNext).reduce(accum, zero);
                }
                now = System.nanoTime();
                elapsed = (double)(now - last) / NPS;
                last = now;
                System.out.printf("poolSize %3d:  %7.3f\n", i, elapsed);
            }
        }
        fjp.shutdownNow();
        fjp.awaitTermination(1, TimeUnit.SECONDS);
        Thread.sleep(100);
        if (sum == 0) System.out.print(" ");
    }

    /**
     * Xorshift Random algorithm.
     */
    public static final class Rand {
        private int seed;
        Rand(int s) {
            seed = s;
        }
        public int next() {
            int x = seed;
            x ^= x << 13; 
            x ^= x >>> 7; 
            x ^= (x << 17);
            seed = x;
            return x;
        }
    }

}
