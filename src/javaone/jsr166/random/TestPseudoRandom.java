package jsr166.random;

import java.util.concurrent.*;

/**
 * Compares performance of PseudoRandom implementations.
 */
public final class TestPseudoRandom {

    public static void main(String[] args) {
        new TestPseudoRandom().run();
    }


    enum PseudoRandomImpl {
        SYNCH {
            PseudoRandom newInstance(int seed) {
                return new PseudoRandomUsingSynch(seed);
            }
        },
        LOCK {
            PseudoRandom newInstance(int seed) {
                return new PseudoRandomUsingLock(seed);
            }
        },
        ATOMIC {
            PseudoRandom newInstance(int seed) {
                return new PseudoRandomUsingAtomic(seed);
            }
        },
        LIBRARY {
            PseudoRandom newInstance(final int seed) {
                return new PseudoRandom() {
                    public int nextInt(int n) {
                        return rnd.nextInt(n);
                    }
                    private final java.util.Random rnd = new java.util.Random(seed);
                };
            }
        },
    ;
        abstract PseudoRandom newInstance(int seed);
    }

    static final int NROLLS = 100000;
    static final int NTRIALS = 25;

    void run() {

        System.out.println("ntrials="+NTRIALS);
        for (PseudoRandomImpl impl : PseudoRandomImpl.values()) {
            long elapsed = 0;
            for (int i = 0; i < NTRIALS; ++i) {
                PseudoRandom rnd = impl.newInstance(SEED);
                elapsed += trial(NROLLS, rnd);
            }

            long avg = elapsed / (NTRIALS * NROLLS);
            System.out.println(""+impl+"\t"+avg);
        }
    }

    private long trial(final int NROLLS, PseudoRandom rnd) {

        class DieRollTask implements Runnable {
            DieRollTask(PseudoRandom rnd) { this.rnd = rnd; }

            public void run() {
                try {
                    startSignal.await();
                    for (int c = 0; c < NROLLS; ++c) rnd.nextInt(6);
                    doneSignal.countDown();
                }
                catch (InterruptedException e) {} // XXX ignored
            }

            private final PseudoRandom rnd;
        }

        final int NTASKS = 2;

        ExecutorService executor = Executors.newFixedThreadPool(NTASKS);
        try {
            startSignal = new CountDownLatch(1);
            doneSignal = new CountDownLatch(2);

            for (int t = 0; t < NTASKS; ++t)
                executor.execute(new DieRollTask(rnd));

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

    private static final int SEED = 17;
}