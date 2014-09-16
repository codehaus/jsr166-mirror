/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

// A CountedCompleter version of Fibonacci

import java.util.concurrent.*;

public abstract class CCFib extends CountedCompleter {
    static int sequentialThreshold;
    int number;
    int rnumber;

    public CCFib(CountedCompleter parent, int n) {
        super(parent, 1);
        this.number = n;
    }

    public final void compute() {
        CountedCompleter p;
        CCFib f = this;
        int n = number;
        while (n > sequentialThreshold) {
            new RCCFib(f, n - 2).fork();
            f = new LCCFib(f, --n);
        }
        f.number = n <= 1? n : seqFib(n);
        f.onCompletion(f);
        if ((p = f.getCompleter()) != null)
            p.tryComplete();
        else 
            f.quietlyComplete(); 
    }

    static final class LCCFib extends CCFib {
        public LCCFib(CountedCompleter parent, int n) {
            super(parent, n);
        }
        public final void onCompletion(CountedCompleter caller) {
            CCFib p = (CCFib)getCompleter();
            int n = number + rnumber;
            if (p != null)
                p.number = n;
            else
                number = n;
        }
    }
        
    static final class RCCFib extends CCFib {
        public RCCFib(CountedCompleter parent, int n) {
            super(parent, n);
        }
        public final void onCompletion(CountedCompleter caller) {
            CCFib p = (CCFib)getCompleter();
            int n = number + rnumber;
            if (p != null)
                p.rnumber = n;
            else
                number = n;
        }
    }
    
    static long lastStealCount;

    public static void main(String[] args) throws Exception {
        int num = 45;
        sequentialThreshold = 5;
        try {
            if (args.length > 0)
                num = Integer.parseInt(args[0]);
            if (args.length > 1)
                sequentialThreshold = Integer.parseInt(args[1]);
        }
        catch (Exception e) {
            System.out.println("Usage: java CCFib <number> <threshold>]");
            return;
        }

        ForkJoinPool g = ForkJoinPool.commonPool();
        for (int reps = 0; reps < 2; ++reps) {
            lastStealCount = g.getStealCount();
            for (int i = 0; i < 20; ++i) {
                test(g, num);
            }
            System.out.println(g);
        }
    }

    /** for time conversion */
    static final long NPS = (1000L * 1000 * 1000);

    static void test(ForkJoinPool g, int num) throws Exception {
        int ps = g.getParallelism();
        long start = System.nanoTime();
        CCFib f = new LCCFib(null, num);
        f.invoke();
        long time = System.nanoTime() - start;
        double secs = ((double)time) / NPS;
        long number = f.number;
        System.out.print("CCFib " + num + " = " + number);
        System.out.printf("\tTime: %9.3f", secs);
        long sc = g.getStealCount();
        long ns = sc - lastStealCount;
        lastStealCount = sc;
        System.out.printf(" Steals/t: %5d", ns/ps);
        System.out.printf(" Workers: %8d", g.getPoolSize());
        System.out.println();
    }

    // Sequential version for arguments less than threshold
    static final int seqFib(int n) { // unroll left only
        int r = 1;
        do {
            int m = n - 2;
            r += m <= 1 ? m : seqFib(m);
        } while (--n > 1);
        return r;
    }

}


