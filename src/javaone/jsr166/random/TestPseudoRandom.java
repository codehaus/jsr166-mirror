package jsr166.random;

import java.util.concurrent.*;

/**
 * Compares performance of PseudoRandom implementations.
 */
public final class TestPseudoRandom {

    public static void main(String[] args) throws Exception {
        int ncpus   = args.length < 1 ?     1 : Integer.parseInt(args[0]);
        int ntrials = args.length < 2 ?    10 : Integer.parseInt(args[1]);
        int nrolls  = args.length < 3 ? 25000 : Integer.parseInt(args[2]);
        new TestPseudoRandom(ncpus, ntrials, nrolls).run();
    }

    TestPseudoRandom(int ncpus, int ntrials, int nrolls) {
        this.ncpus = ncpus;
        this.ntrials = ntrials;
        this.nrolls = nrolls;
    }

    private final int ncpus;
    private final int ntrials;
    private final int nrolls;


    enum PseudoRandomImpl {
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
        SYNCH {
            PseudoRandom newInstance(int seed) {
                return new PseudoRandomUsingSynch(seed);
            }
        },
        ENCAP {
            PseudoRandom newInstance(int seed) {
                return new PseudoRandomUsingEncapsulatedSynch(seed);
            }
        },
        LOCK {
            PseudoRandom newInstance(int seed) {
                return new PseudoRandomUsingLock(seed, false);
            }
        },
        OPTLOCK {
            PseudoRandom newInstance(int seed) {
                return new PseudoRandomUsingOptLock(seed, false);
            }
        },
        FAIR {
            PseudoRandom newInstance(int seed) {
                return new PseudoRandomUsingLock(seed, true);
            }
        },
        ATOMIC {
            PseudoRandom newInstance(int seed) {
                return new PseudoRandomUsingAtomic(seed);
            }
        },
    ;
        abstract PseudoRandom newInstance(int seed);
    }


    void run() {
        for (int ntasks = 1; ntasks <= ncpus; ++ntasks) {
            test(ntasks, ntrials, nrolls);
        }
    }


    void test(int ntasks, int ntrials, int nrolls) {
        System.out.println("ntasks="+ntasks);
        System.out.println("ntrials="+ntrials);
        System.out.println("nrolls="+nrolls);

        for (PseudoRandomImpl impl : PseudoRandomImpl.values()) {
            long elapsed = 0;
            for (int i = 0; i < ntrials; ++i) {
                PseudoRandom rnd = impl.newInstance(SEED);
                elapsed += trial(ntasks, nrolls, rnd);
            }

            long avg = elapsed / (ntrials * nrolls);
            System.out.println(""+impl+"\t"+avg);
        }

        System.out.println();
    }

    private long trial(final int ntasks, final int nrolls, PseudoRandom rnd) {

        final CountDownLatch readySignal = new CountDownLatch(ntasks);
        final CountDownLatch startSignal = new CountDownLatch(1);
        final CountDownLatch doneSignal  = new CountDownLatch(ntasks);

        class DieRollTask implements Runnable {
            DieRollTask(PseudoRandom rnd) { this.rnd = rnd; }

            public void run() {
                try {
                    readySignal.countDown();
                    startSignal.await();
                    for (int c = 0; c < nrolls; ++c) rnd.nextInt(6);
                    doneSignal.countDown();
                }
                catch (InterruptedException e) {} // XXX ignored
            }

            private final PseudoRandom rnd;
        }

        ExecutorService executor = Executors.newFixedThreadPool(ntasks);
        try {
            for (int t = 0; t < ntasks; ++t)
                executor.execute(new DieRollTask(rnd));

            readySignal.await();

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

    private static final int SEED = 17;
}