/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

import java.util.*;
import java.util.concurrent.*;

public final class DynamicLeftSpineFib extends RecursiveAction {

    // Performance-tuning constant:
    static long lastStealCount;

    public static void main(String[] args) throws Exception {
        int procs = 0;
        int num = 43;
        try {
            if (args.length > 0)
                procs = Integer.parseInt(args[0]);
            if (args.length > 1)
                num = Integer.parseInt(args[1]);
        }
        catch (Exception e) {
            System.out.println("Usage: java DynamicLeftSpineFib <threads> <number> [<sequntialThreshold>]");
            return;
        }


        for (int reps = 0; reps < 2; ++reps) {
            ForkJoinPool g = procs == 0? new ForkJoinPool() :
                new ForkJoinPool(procs);
            lastStealCount = g.getStealCount();
            for (int i = 0; i < 10; ++i) {
                test(g, num);
            }
            System.out.println(g);
            g.shutdown();
        }
    }

    static void test(ForkJoinPool g, int num) throws Exception {
        int ps = g.getParallelism();
        long start = System.currentTimeMillis();
        DynamicLeftSpineFib f = new DynamicLeftSpineFib(num, null);
        g.invoke(f);
        long time = System.currentTimeMillis() - start;
        double secs = ((double)time) / 1000.0;
        long result = f.getAnswer();
        System.out.print("DLSFib " + num + " = " + result);
        System.out.printf("\tTime: %7.3f", secs);
        long sc = g.getStealCount();
        long ns = sc - lastStealCount;
        lastStealCount = sc;
        System.out.printf(" Steals/t: %5d", ns/ps);
        System.out.printf(" Workers: %8d", g.getPoolSize());
        System.out.println();
    }


    // Initialized with argument; replaced with result
    int number;
    DynamicLeftSpineFib next;

    DynamicLeftSpineFib(int n, DynamicLeftSpineFib nxt) {
        number = n; next = nxt;
    }

    int getAnswer() {
        return number;
    }

    public final void compute() {
        int n = number;
        if (n > 1) {
            int r = 0;
            DynamicLeftSpineFib rt = null;
            while (getSurplusQueuedTaskCount() <= 3) {
                int m = n - 2;
                n -= 1;
                if (m <= 1) {
                    r += m;
                    if (n > 1) {
                        r += n - 2;
                        n -= 1;
                    }
                    break;
                }
                (rt = new DynamicLeftSpineFib(m, rt)).fork();
            }
            r += seqFib(n);
            while (rt != null) {
                if (rt.tryUnfork()) rt.compute(); else rt.helpJoin();
                r += rt.number;
                rt = rt.next;
            }
            number = r;
        }
    }

    // Sequential version for arguments less than threshold
    static int seqFib(int n) {
        if (n <= 1)
            return n;
        else
            return seqFib(n-1) + seqFib(n-2);
    }

}

