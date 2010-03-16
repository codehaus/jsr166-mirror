/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

import java.util.*;
import java.util.concurrent.*;

// Based very loosely on cilksort

class ScalarLongSort {
    static final long NPS = (1000L * 1000 * 1000); // time conversion

    static int THRESHOLD;
    static final boolean warmup = true;

    public static void main (String[] args) throws Exception {
        int n = 1 << 22;
        int sreps = 2;
        int reps = 20;
        long[] a = new long[n];

        if (warmup) {
            System.out.printf("Sorting %d longs, %d replications\n", n, 1);
            seqRandomFill(a, 0, n);
            long last = System.nanoTime();
            java.util.Arrays.sort(a);
            double elapsed = (double)(System.nanoTime() - last) / NPS;
            System.out.printf("Arrays.sort   time:  %7.3f\n", elapsed);
            checkSorted(a);
        }

        ForkJoinPool pool = new ForkJoinPool();
        // for now hardwire 8 * #CPUs leaf tasks
        THRESHOLD = 1 + ((n + 7) >>> 3) / pool.getParallelism();
        //        THRESHOLD = 1 + ((n + 15) >>> 4) / pool.getParallelism();
        //        THRESHOLD = 1 + ((n + 31) >>> 5) / pool.getParallelism();

        System.out.printf("Sorting %d longs, %d replications\n", n, reps);
        for (int i = 0; i < reps; ++i) {
            pool.invoke(new RandomFiller(a, 0, n));
            long last = System.nanoTime();
            pool.invoke(new Sorter(a, new long[n], 0, n));
            double elapsed = (double)(System.nanoTime() - last) / NPS;
            System.out.printf("Parallel sort time: %7.3f\n", elapsed);
            if (i == 0)
                checkSorted(a);
        }
        System.out.println(pool);

        System.out.printf("Sorting %d longs, %d replications\n", n, sreps);
        for (int i = 0; i < sreps; ++i) {
            pool.invoke(new RandomFiller(a, 0, n));
            long last = System.nanoTime();
            java.util.Arrays.sort(a);
            double elapsed = (double)(System.nanoTime() - last) / NPS;
            System.out.printf("Arrays.sort   time:  %7.3f\n", elapsed);
            if (i == 0)
                checkSorted(a);
        }
        System.out.println(pool);


        pool.shutdown();
    }

    static final class Sorter extends RecursiveAction {
        final long[] a; 
        final long[] w;
        final int origin; 
        final int n;
        Sorter(long[] a, long[] w, int origin, int n) {
            this.a = a; this.w = w; this.origin = origin; this.n = n;
        }

        public void compute()  {
            int l = origin;
            if (n <= THRESHOLD)
                Arrays.sort(a, l, l+n);
            else { // divide in quarters to ensure sorted array in a not w
                SubSorter rs;
                int h = n >>> 1;
                int q = n >>> 2;
                int u = h + q;
                (rs = new SubSorter
                 (new Sorter(a, w, l+h, q),
                  new Sorter(a, w, l+u, n-u),
                  new Merger(a, w, l+h, q, l+u, n-u, l+h, null))).fork();
                (new SubSorter
                 (new Sorter(a, w, l,   q),
                  new Sorter(a, w, l+q, h-q),
                  new Merger(a, w, l,   q, l+q, h-q, l, null))).compute();
                rs.join();
                new Merger(w, a, l, h, l+h, n-h, l, null).compute();
            }
        }
    }
    
    static final class SubSorter extends RecursiveAction {
        final Sorter left;
        final Sorter right;
        final Merger merger;
        SubSorter(Sorter left, Sorter right, Merger merger) {
            this.left = left; this.right = right; this.merger = merger;
        }
        public void compute() {
            right.fork();
            left.compute();
            right.join();
            merger.compute();
        }
    }

    static final class Merger extends RecursiveAction {
        final long[] a; final long[] w;
        final int lo; final int ln; final int ro; final int rn; final int wo;
        final Merger next;
        Merger(long[] a, long[] w, int lo, int ln, int ro, int rn, int wo,
               Merger next) {
            this.a = a;    this.w = w;
            this.lo = lo;  this.ln = ln;
            this.ro = ro;  this.rn = rn;
            this.wo = wo;
            this.next = next;
        }

        /**
         * Merge left and right by splitting left in half,
         * and finding index of right closest to split point.
         * Uses left-spine decomposition to generate all
         * merge tasks before bottomming out at base case.
         * 
         */
        public void compute() {
            Merger rights = null;
            int nleft = ln;
            int nright = rn;
            while (nleft > THRESHOLD) { //  && nright > (THRESHOLD >>> 3)) {
                int lh = nleft >>> 1;
                int splitIndex = lo + lh;
                long split = a[splitIndex];
                int rl = 0;
                int rh = nright;
                while (rl < rh) {
                    int mid = (rl + rh) >>> 1;
                    if (split <= a[ro + mid])
                        rh = mid;
                    else
                        rl = mid + 1;
                }
                (rights = new Merger(a, w, splitIndex, nleft-lh, ro+rh,
                                     nright-rh, wo+lh+rh, rights)).fork();
                nleft = lh;
                nright = rh;
            }

            // Base case -- merge left and right
            int l = lo;
            int lFence = lo + nleft;
            int r = ro;
            int rFence = ro + nright;
            int k = wo;
            while (l < lFence && r < rFence) {
                long al = a[l];
                long ar = a[r];
                long t;
                if (al <= ar) { ++l; t = al; } else { ++r; t = ar; }
                w[k++] = t;
            }
            while (l < lFence)
                w[k++] = a[l++];
            while (r < rFence)
                w[k++] = a[r++];
            
            while (rights != null) {
                rights.join();
                rights = rights.next;
            }
        }

    }

    static void checkSorted (long[] a)  {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            if (a[i] > a[i+1]) {
                throw new Error("Unsorted at " + i + ": " + 
                                a[i] + " / " + a[i+1]);
            }
        }
    }

    static void seqRandomFill(long[] array, int lo, int hi) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = lo; i < hi; ++i)
            array[i] = rng.nextLong();
    }

    static final class RandomFiller extends RecursiveAction {
        final long[] array;
        final int lo, hi;
        RandomFiller(long[] array, int lo, int hi) {
            this.array = array; this.lo = lo; this.hi = hi;
        }
        public void compute() {
            if (hi - lo <= THRESHOLD)
                seqRandomFill(array, lo, hi);
            else {
                int mid = (lo + hi) >>> 1;
                RandomFiller r = new RandomFiller(array, mid, hi);
                r.fork();
                (new RandomFiller(array, lo, mid)).compute();
                r.join();
            }
        }
    }


}
