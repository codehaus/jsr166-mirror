/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;
import java.util.*;

class CCBoxedLongSort {
    static final long NPS = (1000L * 1000 * 1000);

    static final int INSERTION_SORT_THRESHOLD = 8;
    //    static final int THRESHOLD = 64;
    static int THRESHOLD;

    public static void main(String[] args) throws Exception {
        int procs = 0;
        int n = 1 << 22;
        int reps = 30;
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
            pool.invoke(new RandomRepacker(null, numbers, a, 0, n, n));
            long last = System.nanoTime();
            quickSort(a, 0, n-1);
            //            java.util.Arrays.sort(a);
            long now = System.nanoTime();
            double total = (double)(now - start) / NPS;
            double elapsed = (double)(now - last) / NPS;
            System.out.printf("Arrays.sort   time:  %7.3f total %9.3f\n",
                              elapsed, total);
            pool.invoke(new OrderChecker(null, a, 0, n, n));
        }
    }

    static void parTest(Long[] a, Long[] numbers, ForkJoinPool pool, int reps) {
        int n = numbers.length;
        Long[] w = new Long[n];
        System.out.printf("Sorting %d longs, %d replications\n", n, reps);
        long start = System.nanoTime();
        for (int i = 0; i < reps; ++i) {
            pool.invoke(new RandomRepacker(null, numbers, a, 0, n, n));
            long last = System.nanoTime();
            pool.invoke(new Sorter(null, a, w, 0, n));
            long now = System.nanoTime();
            double total = (double)(now - start) / NPS;
            double elapsed = (double)(now - last) / NPS;
            System.out.printf("Parallel sort time:  %7.3f total %9.3f\n",
                              elapsed, total);
            pool.invoke(new OrderChecker(null, a, 0, n, n));
        }
    }

    /*
     * Merge sort alternates placing elements in the given array vs
     * the workspace array. To make sure the final elements are in the
     * given array, we descend in double steps.  So we need some
     * little tasks to serve as the place holders for triggering the
     * merges and re-merges. These don't need to keep track of the
     * arrays, and are never themselves forked, so are mostly empty.
     */
    static final class Subsorter extends CountedCompleter<Void> {
        Subsorter(CountedCompleter<?> p) { super(p); }
        public final void compute() { }
    }

    static final class Comerger extends CountedCompleter<Void> {
        final Merger merger;
        Comerger(Merger merger) {
            super(null, 1);
            this.merger = merger;
        }
        public final void compute() { }
        public final void onCompletion(CountedCompleter<?> t) {
            merger.compute();
        }
    }

    static final class Sorter extends CountedCompleter<Void> {
        final Long[] a;
        final Long[] w;
        final int origin;
        final int size;
        Sorter(CountedCompleter<?> par, Long[] a, Long[] w, int origin, int n) {
            super(par);
            this.a = a; this.w = w; this.origin = origin; this.size = n;
        }

        public final void compute() {
            Long[] a = this.a;
            Long[] w = this.w;
            int l = this.origin;
            int n = this.size;
            CountedCompleter<?> s = this;
            int thr = THRESHOLD;
            while (n > thr) {
                int h = n >>> 1;
                int q = n >>> 2;
                int u = h + q;
                int lq = l + q, lh = l + h, lu = l + u;
                int nh = n - h, nu = n - u, hq = h - q;
                Comerger fc =
                    new Comerger(new Merger(s, w, a, l, h, lh, nh, l));
                Comerger rc =
                    new Comerger(new Merger(fc, a, w, lh, q, lu, nu, lh));
                Comerger lc =
                    new Comerger(new Merger(fc, a, w, l, q, lq, hq, l));
                Sorter su = new Sorter(rc, a, w, lu, nu);
                Sorter sh = new Sorter(rc, a, w, lh, q);
                Sorter sq = new Sorter(lc, a, w, lq, hq);
                su.fork();
                sh.fork();
                sq.fork();
                s = new Subsorter(lc);
                n = q;
            }
            //            Arrays.sort(a, l, l+n);
            quickSort(a, l, l+n-1);
            s.tryComplete();
        }
    }

    static final class Merger extends CountedCompleter<Void> {
        final Long[] a; final Long[] w;
        final int lo; final int ln; final int ro; final int rn; final int wo;
        Merger(CountedCompleter<?> par,
               Long[] a, Long[] w, int lo, int ln, int ro, int rn, int wo) {
            super(par);
            this.a = a;    this.w = w;
            this.lo = lo;  this.ln = ln;
            this.ro = ro;  this.rn = rn;
            this.wo = wo;
        }

        /**
         * Merge left and right by splitting left in half,
         * and finding index of right closest to split point.
         */
        public final void compute() {
            int ln = this.ln;
            int rn = this.rn;
            int l = this.lo;
            int r = this.ro;
            int k = this.wo;
            Long[] a = this.a;
            Long[] w = this.w;
            int thr = THRESHOLD;
            while (ln > thr && rn > 4) {
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
                addToPendingCount(1);
                new Merger(this, a, w, lm, ln-lh, r+rh, rn-rh, k+lh+rh).fork();
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
            tryComplete();
        }
    }

    static void checkSorted(Long[] a) {
        int n = a.length;
        long x = a[0].longValue(), y;
        for (int i = 0; i < n - 1; i++) {
            if (x > (y = a[i+1].longValue()))
                throw new Error("Unsorted at " + i + ": " + x + " / " + y);
            x = y;
        }
    }

    static final class RandomRepacker extends CountedCompleter<Void> {
        final Long[] src;
        final Long[] dst;
        final int lo, hi, size;
        RandomRepacker(CountedCompleter<?> par, Long[] src, Long[] dst,
                       int lo, int hi, int size) {
            super(par);
            this.src = src; this.dst = dst;
            this.lo = lo; this.hi = hi; this.size = size;
        }

        public final void compute() {
            Long[] s = src;
            Long[] d = dst;
            int l = lo, h = hi, n = size;
            while (h - l > THRESHOLD) {
                int m = (l + h) >>> 1;
                addToPendingCount(1);
                new RandomRepacker(this, s, d, m, h, n).fork();
                h = m;
            }
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int i = l; i < h; ++i)
                d[i] = s[rng.nextInt(n)];
            tryComplete();
        }
    }

    static final class OrderChecker extends CountedCompleter<Void> {
        final Long[] array;
        final int lo, hi, size;
        OrderChecker(CountedCompleter<?> par, Long[] a, int lo, int hi, int size) {
            super(par);
            this.array = a;
            this.lo = lo; this.hi = hi; this.size = size;
        }

        public final void compute() {
            Long[] a = this.array;
            int l = lo, h = hi, n = size;
            while (h - l > THRESHOLD) {
                int m = (l + h) >>> 1;
                addToPendingCount(1);
                new OrderChecker(this, a, m, h, n).fork();
                h = m;
            }
            int bound = h < n ? h : n - 1;
            int i = l;
            long x = a[i].longValue(), y;
            while (i < bound) {
                if (x > (y = a[++i].longValue()))
                    throw new Error("Unsorted " + x + " / " + y);
                x = y;
            }
            tryComplete();
        }
    }

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
