/*
 * @test %I% %E%
 * @bug 4486658
 * @compile -source 1.5 TimeoutLockLoops.java
 * @run main TimeoutLockLoops
 * @summary Checks for responsiveness of locks to timeouts.
 * Runs under the assumption that ITERS computations require more than
 * TIMEOUT msecs to complete, which seems to be a safe assumption for
 * another decade.
 */

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;

public final class TimeoutLockLoops {
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static final LoopHelpers.SimpleRandom rng = new LoopHelpers.SimpleRandom();
    static boolean print = false;
    static final int ITERS = Integer.MAX_VALUE;
    static final long TIMEOUT = 8; 

    public static void main(String[] args) throws Exception {
        int maxThreads = 100;
        if (args.length > 0) 
            maxThreads = Integer.parseInt(args[0]);

        // Warm up to avoid transient hotspot problems
        for (int i = 1; i < 4; ++i) 
            new SimpleReentrantLockLoop(i).test();

        print = true;

        for (int i = 1; i <= maxThreads; i += (i+1) >>> 1) {
            System.out.print("Threads: " + i);
            new ReentrantLockLoop(i).test();
            Thread.sleep(10);
        }
        pool.shutdown();
    }

    static final class ReentrantLockLoop implements Runnable {
        private int v = rng.next();
        private volatile boolean completed;
        private volatile int result = 17;
        private final ReentrantLock lock = new ReentrantLock();
        private final LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        private final CyclicBarrier barrier;
        private final int nthreads;
        ReentrantLockLoop(int nthreads) {
            this.nthreads = nthreads;
            barrier = new CyclicBarrier(nthreads+1, timer);
        }

        final void test() throws Exception {
            for (int i = 0; i < nthreads; ++i) {
                lock.lock();
                pool.execute(this);
                lock.unlock();
            }
            barrier.await();
            Thread.sleep(TIMEOUT);
            while (!lock.tryLock()); // Jam lock
            //            lock.lock();
            barrier.await();
            if (print) {
                long time = timer.getTime();
                double secs = (double)(time) / 1000000000.0;
                System.out.println("\t " + secs + "s run time");
            }

            if (completed)
                throw new Error("Some thread completed instead of timing out");
            int r = result;
            if (r == 0) // avoid overoptimization
                System.out.println("useless result: " + r);
        }

        public final void run() {
            try {
                barrier.await(); 
                int sum = v;
                int x = 0;
                int n = ITERS;
                final ReentrantLock lock = this.lock;
                do {
                    if (!lock.tryLock(TIMEOUT, TimeUnit.MILLISECONDS))
                        break;
                    else {
                        try {
                            v = x = LoopHelpers.compute1(v);
                        }
                        finally {
                            lock.unlock();
                        }
                    }
                    sum += LoopHelpers.compute2(x);
                } while (n-- > 0);
                if (n <= 0)
                    completed = true;
                barrier.await();
                result += sum;
            }
            catch (Exception ex) { 
                ex.printStackTrace();
                return; 
            }
        }
    }

    static final class SimpleReentrantLockLoop implements Runnable {
        static int iters = 100000;
        private int v = rng.next();
        private volatile int result = 17;
        private final ReentrantLock lock = new ReentrantLock();
        private final LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        private final CyclicBarrier barrier;
        private final int nthreads;
        SimpleReentrantLockLoop(int nthreads) {
            this.nthreads = nthreads;
            barrier = new CyclicBarrier(nthreads+1, timer);
        }

        final void test() throws Exception {
            for (int i = 0; i < nthreads; ++i) 
                pool.execute(this);
            barrier.await();
            barrier.await();
            if (print) {
                long time = timer.getTime();
                long tpi = time / ((long)iters * nthreads);
                System.out.print("\t" + LoopHelpers.rightJustify(tpi) + " ns per lock");
                double secs = (double)(time) / 1000000000.0;
                System.out.println("\t " + secs + "s run time");
            }

            int r = result;
            if (r == 0) // avoid overoptimization
                System.out.println("useless result: " + r);
        }

        public final void run() {
            final ReentrantLock lock = this.lock;
            try {
                barrier.await(); 
                int sum = v;
                int x = 0;
                int n = iters;
                while (n-- > 0) {
                    lock.lock();
                    if ((n & 255) == 0)
                        v = x = LoopHelpers.compute2(LoopHelpers.compute1(v));
                    else
                        v = x += ~(v - n);
                    lock.unlock();

                    // Once in a while, do something more expensive
                    if ((~n & 255) == 0) 
                        sum += LoopHelpers.compute1(LoopHelpers.compute2(x));
                    else
                        sum += sum ^ x;
                } 
                barrier.await();
                result += sum;
            }
            catch (Exception ie) { 
                return; 
            }
        }
    }


}
