/*
 * Written by Martin Buchholz with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * Measures repeated futile timed attempts to acquire a ReentrantLock.
 * Hard to draw any conclusions from.
 */
public final class FutileTimedTryLockLoops {

    static Callable<Void> tryLocker(ReentrantLock lock, long iters, long nanos) {
        return new Callable<Void>() {
            public Void call() throws InterruptedException {
                for (long i = 0; i < iters; i++)
                    if (lock.tryLock(nanos, TimeUnit.NANOSECONDS))
                        throw new AssertionError();
                return null;
            }};
    }

    public static void main(String[] args) throws Exception {
        int minThreads = 1;
        int maxThreads = 4;
        long nanos = 1001L;
        long iters = 60_000_000L;
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
                case "nanos":
                    nanos = Long.valueOf(val);
                    continue nextArg;
                case "iters":
                    iters = Long.valueOf(val);
                    continue nextArg;
                }
            }
            throw new Error("Usage: FutileTimedTryLockLoops minThreads=n maxThreads=n Threads=n nanos=n iters=n");
        }
        final ExecutorService pool = Executors.newCachedThreadPool();
        final ReentrantLock lock = new ReentrantLock();
        final Callable<Void> task = tryLocker(lock, iters, nanos);
        lock.lock();

        for (int i = minThreads; i <= maxThreads; i += (i+1) >>> 1) {
            long startTime = System.nanoTime();
            pool.invokeAll(Collections.nCopies(i, task));
            long elapsed = System.nanoTime() - startTime;
            System.out.printf
                ("Threads: %d, %d ms total, %d ns/iter, overhead %d ns/iter%n",
                 i,
                 elapsed / (1000*1000),
                 elapsed / iters,
                 (elapsed / iters) - nanos);
        }
        pool.shutdown();
    }
}
