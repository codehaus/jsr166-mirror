/*
 * Written by Martin Buchholz with assistance from members of JCP
 * JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @summary Stress test looks for lost unparks
 */

import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;

public final class ParkLoops {
    public static void main(String[] args) throws Exception {
        final int nThreads = 4; // must be power of two
        final int iters = 300_000; // crank up for serious stress testing
        final ExecutorService pool = Executors.newCachedThreadPool();
        final AtomicReferenceArray<Thread> threads
            = new AtomicReferenceArray<>(nThreads);
        final CountDownLatch done = new CountDownLatch(nThreads);
        final Runnable parker = new Runnable() { public void run() {
            final SimpleRandom rng = new SimpleRandom();
            final Thread current = Thread.currentThread();
            for (int k = iters;;) {
                int j = rng.next() & (nThreads - 1);
                if (threads.compareAndSet(j, null, current)) {
                    do {                // handle spurious wakeups
                        LockSupport.park();
                    } while (threads.get(j) == current);
                    if (k-- <= 0) break;
                }
            }
            done.countDown();
        }};
        final Runnable unparker = new Runnable() { public void run() {
            final SimpleRandom rng = new SimpleRandom();
            for (int n = 0; (n++ & 0xff) != 0 || done.getCount() > 0;) {
                int j = rng.next() & (nThreads - 1);
                Thread parker = threads.get(j);
                if (parker != null &&
                    threads.compareAndSet(j, parker, null)) {
                    LockSupport.unpark(parker);
                }
            }
        }};
        for (int i = 0; i < nThreads; i++) {
            pool.submit(parker);
            pool.submit(unparker);
        }
        done.await();
        pool.shutdown();
        pool.awaitTermination(10L, SECONDS);
    }

    /**
     * An actually useful random number generator, but unsynchronized.
     * Basically same as java.util.Random.
     */
    public static class SimpleRandom {
        private static final long multiplier = 0x5DEECE66DL;
        private static final long addend = 0xBL;
        private static final long mask = (1L << 48) - 1;
        static final AtomicLong seq = new AtomicLong(1);
        private long seed = System.nanoTime() + seq.getAndIncrement();

        public int next() {
            long nextseed = (seed * multiplier + addend) & mask;
            seed = nextseed;
            return ((int)(nextseed >>> 17)) & 0x7FFFFFFF;
        }
    }
}
