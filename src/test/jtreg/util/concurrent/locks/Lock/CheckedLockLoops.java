/*
 * @test %I% %E%
 * @bug 4486658
 * @compile -source 1.5 CheckedLockLoops.java
 * @run main/timeout=7200 CheckedLockLoops
 * @summary basic safety and liveness of ReentrantLocks, and other locks based on them
 */

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;

public final class CheckedLockLoops {
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static final LoopHelpers.SimpleRandom rng = new LoopHelpers.SimpleRandom();
    static boolean print = false;
    static boolean doBuiltin = false;

    public static void main(String[] args) throws Exception {
        int maxThreads = 5;
        int iters = 100000;

        if (args.length > 0) 
            maxThreads = Integer.parseInt(args[0]);

        rng.setSeed(3122688L);

        print = false;
        System.out.println("Warmup...");
        oneTest(3, 10000);
        Thread.sleep(1000);
        oneTest(2, 10000);
        Thread.sleep(100);
        oneTest(1, 100000);
        Thread.sleep(100);
        oneTest(1, 100000);
        Thread.sleep(1000);
        print = true;

        for (int i = 1; i <= maxThreads; i += (i+1) >>> 1) {
            System.out.println("Threads:" + i);
            oneTest(i, iters / i);
            Thread.sleep(100);
        }
        pool.shutdown();
    }

    static void oneTest(int nthreads, int iters) throws Exception {
        int v = rng.next();
        if (doBuiltin) {
            if (print)
                System.out.print("builtin lock          ");
            new BuiltinLockLoop().test(v, nthreads, iters);
            Thread.sleep(10);
        }

        if (print)
            System.out.print("ReentrantLock         ");
        new ReentrantLockLoop().test(v, nthreads, iters);
        Thread.sleep(10);

        if (print)
            System.out.print("ReentrantWriteLock    ");
        new ReentrantWriteLockLoop().test(v, nthreads, iters);
        Thread.sleep(10);
        
        if (print)
            System.out.print("ReentrantReadWriteLock");
        new ReentrantReadWriteLockLoop().test(v, nthreads, iters);
        Thread.sleep(10);

        if (print)
            System.out.print("Semaphore             ");
        new SemaphoreLoop().test(v, nthreads, iters);
        Thread.sleep(10);
        
        if (print)
            System.out.print("fair Semaphore        ");
        new FairSemaphoreLoop().test(v, nthreads, iters);
        Thread.sleep(10);

        if (print)
            System.out.print("FairReentrantLock     ");
        new FairReentrantLockLoop().test(v, nthreads, iters);
        Thread.sleep(10);
        
        if (print)
            System.out.print("FairRWriteLock         ");
        new FairReentrantWriteLockLoop().test(v, nthreads, iters);
        Thread.sleep(10);
        
        if (print)
            System.out.print("FairRReadWriteLock     ");
        new FairReentrantReadWriteLockLoop().test(v, nthreads, iters);
        Thread.sleep(10);
    }

    static abstract class LockLoop implements Runnable {
        int value;
        int checkValue;
        int iters;
        volatile int result;
        final LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier;

        final int setValue(int v) {
            checkValue = v ^ 0x55555555;
            value = v;
            return v;
        }

        final int getValue() {
            int v = value;
            if (checkValue != ~(v ^ 0xAAAAAAAA)) 
                throw new Error("lock protection failure");
            return v;
        }

        final void test(int initialValue, int nthreads, int iters) throws Exception {
            setValue(initialValue);
            this.iters = iters;
            barrier = new CyclicBarrier(nthreads+1, timer);
            for (int i = 0; i < nthreads; ++i) 
                pool.execute(this);
            barrier.await();
            barrier.await();
            long time = timer.getTime();
            if (print) {
                long tpi = time / (iters * nthreads);
                System.out.print("\t" + LoopHelpers.rightJustify(tpi) + " ns per update");
                //                double secs = (double)(time) / 1000000000.0;
                //                System.out.print("\t " + secs + "s run time");
                System.out.println();
            }

            if (result == 0) // avoid overoptimization
                System.out.println("useless result: " + result);
        }
        abstract int loop(int n);
        public final void run() {
            try {
                barrier.await(); 
                result += loop(iters);
                barrier.await();
            }
            catch (Exception ie) { 
                return; 
            }
        }

    }

    private static class BuiltinLockLoop extends LockLoop {
        final int loop(int n) {
            int sum = 0;
            int x = 0;;
            while (n-- > 0) {
                synchronized(this) {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class ReentrantLockLoop extends LockLoop {
        final private ReentrantLock lock = new ReentrantLock();
        final int loop(int n) {
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.lock();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    lock.unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class FairReentrantLockLoop extends LockLoop {
        final private ReentrantLock lock = new ReentrantLock(true);
        final int loop(int n) {
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.lock();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    lock.unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class ReentrantWriteLockLoop extends LockLoop {
        final private Lock lock = new ReentrantReadWriteLock().writeLock();
        final int loop(int n) {
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.lock();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    lock.unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class ReentrantReadWriteLockLoop extends LockLoop {
        final private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final int loop(int n) {
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.readLock().lock();
                try {
                    x = LoopHelpers.compute1(getValue());
                }
                finally {
                    lock.readLock().unlock();
                }
                lock.writeLock().lock();
                try {
                    setValue(x);
                }
                finally {
                    lock.writeLock().unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class FairReentrantWriteLockLoop extends LockLoop {
        final Lock lock = new ReentrantReadWriteLock(true).writeLock();
        final int loop(int n) {
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.lock();
                try {
                    x = setValue(LoopHelpers.compute1(getValue()));
                }
                finally {
                    lock.unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }

    private static class SemaphoreLoop extends LockLoop {
        final private Semaphore sem = new Semaphore(1, false);
        final int loop(int n) {
            int sum = 0;
            int x = 0;
            try {
                while (n-- > 0) {
                    sem.acquire();
                    try {
                        x = setValue(LoopHelpers.compute1(getValue()));
                    }
                    finally {
                        sem.release();
                    }
                    sum += LoopHelpers.compute2(x);
                }
            }
            catch (InterruptedException ie) {
                return sum;
            }
            return sum;
        }
    }
    private static class FairSemaphoreLoop extends LockLoop {
        final private Semaphore sem = new Semaphore(1, true);
        final int loop(int n) {
            int sum = 0;
            int x = 0;
            try {
                while (n-- > 0) {
                    sem.acquire();
                    try {
                        x = setValue(LoopHelpers.compute1(getValue()));
                    }
                    finally {
                        sem.release();
                    }
                    sum += LoopHelpers.compute2(x);
                }
            }
            catch (InterruptedException ie) {
                return sum;
            }
            return sum;
        }
    }

    private static class FairReentrantReadWriteLockLoop extends LockLoop {
        final private ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
        final int loop(int n) {
            int sum = 0;
            int x = 0;
            while (n-- > 0) {
                lock.readLock().lock();
                try {
                    x = LoopHelpers.compute1(getValue());
                }
                finally {
                    lock.readLock().unlock();
                }
                lock.writeLock().lock();
                try {
                    setValue(x);
                }
                finally {
                    lock.writeLock().unlock();
                }
                sum += LoopHelpers.compute2(x);
            }
            return sum;
        }
    }


}
