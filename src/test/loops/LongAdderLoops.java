/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

public class LongAdderLoops {
    public static void main(String[] args) {
        final int NCPU = Runtime.getRuntime().availableProcessors();
        int minThreads = 1;
        int maxThreads = 2 * NCPU;
        long iters = 300_000_000L;
        nextArg: for (String arg : args) {
            String[] fields = arg.split("=");
            if (fields.length == 2) {
                String prop = fields[0], val = fields[1];
                switch (prop) {
                case "threads":
                    minThreads = maxThreads = Integer.valueOf(val);
                    continue nextArg;
                case "minThreads":
                    minThreads = Integer.valueOf(val);
                    continue nextArg;
                case "maxThreads":
                    maxThreads = Integer.valueOf(val);
                    continue nextArg;
                case "iters":
                    iters = Long.valueOf(val);
                    continue nextArg;
                }
            }
            throw new Error("Usage: LongAdderLoops minThreads=n maxThreads=n threads=n iters=n");
        }

        final ExecutorService pool = Executors.newCachedThreadPool();
        for (int i = minThreads; i <= maxThreads; i += (i+1) >>> 1)
            adderTest(pool, i, iters);
        pool.shutdown();
    }

    static void adderTest(ExecutorService pool, int nthreads, long iters) {
        System.out.print("LongAdder  ");
        Phaser phaser = new Phaser(nthreads + 1);
        LongAdder a = new LongAdder();
        for (int i = 0; i < nthreads; ++i)
            pool.execute(new AdderTask(a, phaser, iters));
        report(nthreads, iters, timeTasks(phaser), a.sum());
    }

    static void report(int nthreads, long iters, long time, long sum) {
        long total = (long)nthreads * iters;
        if (sum != total)
            throw new Error(sum + " != " + total);
        double secs = (double)time / (1000L * 1000 * 1000);
        long rate = total * (1000L) / time;
        System.out.printf("threads:%3d  Time: %7.3fsec  Iters per microsec: %4d\n",
                          nthreads, secs, rate);
    }

    static long timeTasks(Phaser phaser) {
        phaser.arriveAndAwaitAdvance();
        long start = System.nanoTime();
        phaser.arriveAndAwaitAdvance();
        phaser.arriveAndAwaitAdvance();
        return System.nanoTime() - start;
    }

    static final class AdderTask implements Runnable {
        final LongAdder adder;
        final Phaser phaser;
        final long iters;
        volatile long result;
        AdderTask(LongAdder adder, Phaser phaser, long iters) {
            this.adder = adder;
            this.phaser = phaser;
            this.iters = iters;
        }

        public void run() {
            phaser.arriveAndAwaitAdvance();
            phaser.arriveAndAwaitAdvance();
            LongAdder a = adder;
            for (long i = 0; i < iters; ++i)
                a.increment();
            result = a.sum();
            phaser.arrive();
        }
    }

}
