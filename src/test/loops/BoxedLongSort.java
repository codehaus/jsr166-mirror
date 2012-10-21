/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;
import java.util.*;

class BoxedLongSort {
    static final long NPS = (1000L * 1000 * 1000);

    static int THRESHOLD;
    //    static final int THRESHOLD = 64;

    public static void main(String[] args) throws Exception {
        int procs = 0;
        int n = 1 << 22;
        int reps = 20;
        int sreps = 2;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1)
                n = Integer.parseInt(args[1]);
            if (args.length > 2)
                reps = Integer.parseInt(args[1]);
        }
        catch (Exception e) {
            System.out.println("Usage: java BoxedLongSort threads n reps");
            return;
        }
        if (procs == 0)
            procs = Runtime.getRuntime().availableProcessors();

        THRESHOLD = ((n + 7) >>> 3) / procs;
        //        THRESHOLD = ((n + 15) >>> 4) / procs;
        //        THRESHOLD = ((n + 31) >>> 5) / procs;
        if (THRESHOLD < 64)
            THRESHOLD = 64;

        System.out.println("Threshold = " + THRESHOLD);

        Long[] numbers = new Long[n];
        for (int i = 0; i < n; ++i)
            numbers[i] = Long.valueOf(i);
        Long[] a = new Long[n];
        ForkJoinPool pool = new ForkJoinPool(procs);
        seqTest(a, numbers, pool, 1);
        System.out.println(pool);
        parTest(a, numbers, pool, reps);
        System.out.println(pool);
        seqTest(a, numbers, pool, 2);
        System.out.println(pool);
        pool.shutdown();
    }

    static void seqTest(Long[] a, Long[] numbers, ForkJoinPool pool, int reps) {
        int n = numbers.length;
        System.out.printf("Sorting %d longs, %d replications\n", n, reps);
        long start = System.nanoTime();
        for (int i = 0; i < reps; ++i) {
            pool.invoke(new RandomRepacker(numbers, a, 0, n, n));
            //            pool.invoke(new TaskChecker());
            long last = System.nanoTime();
            //            quickSort(a, 0, n-1);
            java.util.Arrays.sort(a);
            long now = System.nanoTime();
            double total = (double)(now - start) / NPS;
            double elapsed = (double)(now - last) / NPS;
            System.out.printf("Arrays.sort   time:  %7.3f total %9.3f\n",
                              elapsed, total);
            if (i == 0)
                checkSorted(a);
        }
    }

    static void parTest(Long[] a, Long[] numbers, ForkJoinPool pool, int reps) {
        int n = numbers.length;
        Long[] w = new Long[n];
        System.out.printf("Sorting %d longs, %d replications\n", n, reps);
        long start = System.nanoTime();
        for (int i = 0; i < reps; ++i) {
            //            Arrays.fill(w, 0, n, null);
            pool.invoke(new RandomRepacker(numbers, a, 0, n, n));
            //            pool.invoke(new TaskChecker());
            long last = System.nanoTime();
            pool.invoke(new Sorter(a, w, 0, n));
            long now = System.nanoTime();
            //            pool.invoke(new TaskChecker());
            double total = (double)(now - start) / NPS;
            double elapsed = (double)(now - last) / NPS;
            System.out.printf("Parallel sort time:  %7.3f total %9.3f\n",
                              elapsed, total);
            if (i == 0)
                checkSorted(a);
        }
    }

    static final class Sorter extends RecursiveAction {
        final Long[] a;
        final Long[] w;
        final int origin;
        final int n;
        Sorter(Long[] a, Long[] w, int origin, int n) {
            this.a = a; this.w = w; this.origin = origin; this.n = n;
        }

        public void compute() {
            int l = origin;
            if (n <= THRESHOLD) {
                //                Arrays.sort(a, l, l+n);
                quickSort(a, l, l+n-1);
            }
            else { // divide in quarters to ensure sorted array in a not w
                int h = n >>> 1;
                int q = n >>> 2;
                int u = h + q;
                SubSorter rs = new SubSorter
                    (new Sorter(a, w, l+h, q),
                     new Sorter(a, w, l+u, n-u),
                     new Merger(a, w, l+h, q, l+u, n-u, l+h, null));
                rs.fork();
                Sorter rl = new Sorter(a, w, l+q, h-q);
                rl.fork();
                (new Sorter(a, w, l,   q)).compute();
                rl.join();
                (new Merger(a, w, l,   q, l+q, h-q, l, null)).compute();
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
        final Long[] a; final Long[] w;
        final int lo; final int ln; final int ro; final int rn; final int wo;
        Merger next;
        Merger(Long[] a, Long[] w, int lo, int ln, int ro, int rn, int wo,
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
         */
        public final void compute() {
            Merger rights = null;
            Long[] a = this.a;
            Long[] w = this.w;
            int ln = this.ln;
            int rn = this.rn;
            int l = this.lo;
            int r = this.ro;
            int k = this.wo;
            while (ln > THRESHOLD && rn > 4) {
                int lh = ln >>> 1;
                int lm = l + lh;
                Long split = a[lm];
                long ls = split.longValue();
                int rl = 0;
                int rh = rn;
                while (rl < rh) {
                    int rm = (rl + rh) >>> 1;
                    if (ls <= a[r + rm].longValue())
                        rh = rm;
                    else
                        rl = rm + 1;
                }
                (rights = new Merger(a, w, lm, ln-lh, r+rh, rn-rh, k+lh+rh, rights)).fork();
                rn = rh;
                ln = lh;
            }

            int lFence = l + ln;
            int rFence = r + rn;
            for (Long t;;) {
                if (l < lFence) {
                    Long al = a[l], ar;
                    if (r >= rFence ||
                        al.longValue() <= (ar = a[r]).longValue()) {
                        ++l; t = al;
                    }
                    else {
                        ++r; t = ar;
                    }
                }
                else if (r < rFence)
                    t = a[r++];
                else
                    break;
                w[k++] = t;
            }

            //            merge(nleft, nright);
            if (rights != null)
                collectRights(rights);

        }

        final void merge(int nleft, int nright) {
            int l = lo;
            int lFence = lo + nleft;
            int r = ro;
            int rFence = ro + nright;
            int k = wo;
            while (l < lFence && r < rFence) {
                Long al = a[l];
                Long ar = a[r];
                Long t;
                if (al <= ar) { ++l; t = al; } else { ++r; t = ar; }
                w[k++] = t;
            }
            while (l < lFence)
                w[k++] = a[l++];
            while (r < rFence)
                w[k++] = a[r++];
        }

        static void collectRights(Merger rt) {
            while (rt != null) {
                Merger next = rt.next;
                rt.next = null;
                if (rt.tryUnfork()) rt.compute(); else rt.join();
                rt = next;
            }
        }

    }

    static void checkSorted(Long[] a) {
        int n = a.length;
        for (int i = 0; i < n - 1; i++) {
            if (a[i] > a[i+1]) {
                throw new Error("Unsorted at " + i + ": " +
                                a[i] + " / " + a[i+1]);
            }
        }
    }

    static void seqRandomFill(Long[] array, int lo, int hi) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = lo; i < hi; ++i)
            array[i] = rng.nextLong();
    }

    static final class RandomFiller extends RecursiveAction {
        final Long[] array;
        final int lo, hi;
        RandomFiller(Long[] array, int lo, int hi) {
            this.array = array; this.lo = lo; this.hi = hi;
        }
        public void compute() {
            if (hi - lo <= THRESHOLD) {
                Long[] a = array;
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                for (int i = lo; i < hi; ++i)
                    a[i] = rng.nextLong();
            }
            else {
                int mid = (lo + hi) >>> 1;
                RandomFiller r = new RandomFiller(array, mid, hi);
                r.fork();
                (new RandomFiller(array, lo, mid)).compute();
                r.join();
            }
        }
    }


    static final class RandomRepacker extends RecursiveAction {
        final Long[] src;
        final Long[] dst;
        final int lo, hi, size;
        RandomRepacker(Long[] src, Long[] dst,
                       int lo, int hi, int size) {
            this.src = src; this.dst = dst;
            this.lo = lo; this.hi = hi; this.size = size;
        }

        public final void compute() {
            Long[] s = src;
            Long[] d = dst;
            int l = lo, h = hi, n = size;
            if (h - l > THRESHOLD) {
                int m = (l + h) >>> 1;
                invokeAll(new RandomRepacker(s, d, l, m, n),
                          new RandomRepacker(s, d, m, h, n));
            }
            else {
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                for (int i = l; i < h; ++i)
                    d[i] = s[rng.nextInt(n)];
            }
        }
    }

    static final int INSERTION_SORT_THRESHOLD = 8;

    static void quickSort(Long[] a, int lo, int hi) {
        for (;;) {
            if (hi - lo <= INSERTION_SORT_THRESHOLD) {
                for (int i = lo + 1; i <= hi; i++) {
                    Long t = a[i];
                    long tv = t.longValue();
                    int j = i - 1;
                    while (j >= lo && tv < a[j].longValue()) {
                        a[j+1] = a[j];
                        --j;
                    }
                    a[j+1] = t;
                }
                return;
            }

            int mid = (lo + hi) >>> 1;
            if (a[lo].longValue() > a[mid].longValue()) {
                Long t = a[lo]; a[lo] = a[mid]; a[mid] = t;
            }
            if (a[mid].longValue() > a[hi].longValue()) {
                Long t = a[mid]; a[mid] = a[hi]; a[hi] = t;
                if (a[lo].longValue() > a[mid].longValue()) {
                    Long u = a[lo]; a[lo] = a[mid]; a[mid] = u;
                }
            }

            long pivot = a[mid].longValue();
            int left = lo+1;
            int right = hi-1;
            for (;;) {
                while (pivot < a[right].longValue())
                    --right;
                while (left < right && pivot >= a[left].longValue())
                    ++left;
                if (left < right) {
                    Long t = a[left]; a[left] = a[right]; a[right] = t;
                    --right;
                }
                else break;
            }

            if (left - lo <= hi - right) {
                quickSort(a, lo, left);
                lo = left + 1;
            }
            else {
                quickSort(a, right, hi);
                hi = left;
            }
        }
    }

}
