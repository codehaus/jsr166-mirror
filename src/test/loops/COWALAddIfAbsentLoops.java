/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * A simple test for evaluating different implementations of
 * CopyOnWriteArrayList.addIfAbsent.
 */
public class COWALAddIfAbsentLoops {

    static final int SIZE = 35000;

    /**
     * Set to 1 for  0% cache hit ratio (every addIfAbsent a cache miss).
     * Set to 2 for 50% cache hit ratio.
     */
    static final int CACHE_HIT_FACTOR = 1;

    public static void main(String[] args) throws Exception {
        for (int reps = 0; reps < 4; ++reps) {
            for (int i = 1; i <= 4; ++i)
                test(i);
        }
    }

    static final AtomicInteger result = new AtomicInteger();

    public static void test(int n) throws Exception {
        result.set(0);
        Thread[] ts = new Thread[CACHE_HIT_FACTOR*n];
        Phaser started = new Phaser(ts.length + 1);
        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<Integer>();
        for (int i = 0; i < ts.length; ++i)
            (ts[i] = new Thread(new Task(i%n, n, list, started))).start();
        long p = started.arriveAndAwaitAdvance();
        long st = System.nanoTime();
        for (Thread thread : ts)
            thread.join();
        double secs = ((double)System.nanoTime() - st) / (1000L * 1000 * 1000);
        System.out.println("Threads: " + n + " Time: " + secs);
        if (result.get() != SIZE)
            throw new Error();
    }

    static final class Task implements Runnable {
        final int id, stride;
        final CopyOnWriteArrayList<Integer> list;
        final Phaser started;
        Task(int id, int stride,
             CopyOnWriteArrayList<Integer> list, Phaser started) {
            this.id = id;
            this.stride = stride;
            this.list = list;
            this.started = started;
        }
        public void run() {
            final CopyOnWriteArrayList<Integer> list = this.list;
            int origin = id, inc = stride, adds = 0;
            started.arriveAndAwaitAdvance();
            for (int i = origin; i < SIZE; i += inc) {
                if (list.addIfAbsent(i))
                    ++adds;
            }
            result.getAndAdd(adds);
        }
    }
}
