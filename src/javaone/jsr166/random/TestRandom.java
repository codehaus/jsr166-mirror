package jsr166.random;

import java.util.concurrent.*;

/**
 * Compares performance of Random implementations.
 */
public final class TestRandom {

    public static void main(String[] args) {
        new TestRandom().run();
    }


    abstract enum Mode {
        SYNCH {
            Random newRandom(long seed) {
                return new RandomUsingSynch(seed);
            }
        },
        LOCK {
            Random newRandom(long seed) {
                return new RandomUsingLock(seed);
            }
        },
        ATOMIC {
            Random newRandom(long seed) {
                return new RandomUsingAtomic(seed);
            }
        },
        LIBRARY {
            Random newRandom(final long seed) {
                return new Random() {
                    public long next() {
                        return rnd.nextLong();
                    }
                    private final java.util.Random rnd = new java.util.Random(seed);
                };
            }
        },
    ;
        abstract Random newRandom(long seed);
    }

    void run() {
        final int count = 2000;
        final int NTRIALS = 200;

        System.out.println("ntrials="+NTRIALS);
        for (Mode mode : Mode.values()) {
            long elapsed = 0;
            for (int i = 0; i < NTRIALS; ++i) {
                Random rnd = mode.newRandom(SEED);
                elapsed += trial(count, rnd);
            }

            long avg = elapsed / (NTRIALS * count);
            System.out.println(""+mode+"\t"+avg);
        }
    }

    private long trial(int count, Random rnd) {

        final int NTASKS = 2;
        ExecutorService executor = Executors.newFixedThreadPool(NTASKS);
        try {
            startSignal = new CountDownLatch(1);
            doneSignal = new CountDownLatch(2);

            for (int t = 0; t < NTASKS; ++t)
                executor.execute(new RandomTask(count, rnd));

            long start = System.nanoTime();

            startSignal.countDown();
            doneSignal.await();

            return System.nanoTime() - start;
        }
        catch (InterruptedException e) {
            return 0; // no useful timing
        }
        finally {
            executor.shutdown();
        }
    }

    volatile CountDownLatch startSignal;
    volatile CountDownLatch doneSignal;

    class RandomTask implements Runnable {
        RandomTask(int count, Random rnd) {
            this.count = count;
            this.rnd = rnd;
        }

        public void run() {
            try {
                startSignal.await();
                for (int c = 0; c < count; ++c)
                    rnd.next();
                doneSignal.countDown();
            }
            catch (InterruptedException e) {} // XXX ignored
        }

        private final int count;
        private final Random rnd;
    }

    private static final long SEED = 17;
}