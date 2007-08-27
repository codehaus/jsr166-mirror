/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */


// MapReduce, specialized for ints

import jsr166y.forkjoin.*;
import static jsr166y.forkjoin.Ops.*;
import java.util.*;
import java.util.concurrent.*;

public class IntMapReduceDemo {
    static final int NCPU = Runtime.getRuntime().availableProcessors();
    /**
     * Sequential version, for performance comparison
     */
    static int seqMapReduce(int[] array, 
                            MapperFromIntToInt mapper,
                            IntReducer reducer,
                            int base) {
        int n = array.length;
        int x = base;
        for (int i = 0; i < n; ++i) 
            x = reducer.combine(x, mapper.map(array[i]));
        return x;
    }

    // sample functions
    static final class NextRand implements MapperFromIntToInt {
        public int map(int seed) {
            seed ^= seed >>> 3; 
            seed ^= (seed << 10);
            return seed;
        }
    }
    
    static final NextRand nextRand = new NextRand();

    static final class Accum implements IntReducer {
        public int combine(int x, int y) {
            for (int i = 0; i < (1 << 10); ++i) {
                x = 36969 * (x & 65535) + (x >> 16);
                y =  y * 134775813 + 1;
            }
            return x + y;
        }
    }

    static final Accum accum = new Accum();

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    public static void main(String[] args) throws Exception {
        int n = 1 << 21;
        int[] array = new int[n];
        ForkJoinPool fjp = new ForkJoinPool(1);
        ParallelIntArray pa = new ParallelIntArray(fjp, array);
        
        final int zero = 0;
        long last, now;
        double elapsed;
        last = System.nanoTime();
        int sum = 0;
        for (int k = 0; k < 3; ++k) {
            pa.randomFill();
            sum += seqMapReduce(array, nextRand, accum, zero);
            now = System.nanoTime();
            elapsed = (double)(now - last) / NPS;
            last = now;
            System.out.printf("seq:    %7.3f\n", elapsed);
        }

        for (int i = 1; i <= NCPU; i <<= 1) {
            fjp.setPoolSize(i);
            pa.randomFill();
            last = System.nanoTime();
            for (int k = 0; k < 3; ++k) {
                sum += pa.withMapping(nextRand).reduce(accum, zero);
                now = System.nanoTime();
                elapsed = (double)(now - last) / NPS;
                last = now;
                System.out.printf("ps %2d:  %7.3f\n", i, elapsed);
            }
        }
        fjp.shutdown();
        if (sum == 0) System.out.print(" ");
    }
}
