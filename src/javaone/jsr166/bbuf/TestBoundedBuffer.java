package jsr166.bbuf;

import jsr166.random.PseudoRandom;
import jsr166.random.PseudoRandomUsingAtomic;
import java.util.concurrent.*;

/**
 * Compares performance of BoundedBuffer implementation using
 * native Conditions with an implementation using dl.u.c CondVars
 * and an implementation using j.u.c ArrayBlockingQueue.
 *
 * Sample results obtained on Pentium 4 @ 3.2 GHz -client (Win XP SP1)
 * [see timings.xls]
 */
public final class TestBoundedBuffer {

    public static void main(String[] args) {
        new TestBoundedBuffer().run();
    }


    enum Mode {
        CONDVAR {
            BoundedBuffer<Integer> newBuffer(int capacity) {
                return new BoundedBufferUsingCondVar<Integer>(capacity);
            }
        },
        CONDITION {
            BoundedBuffer<Integer> newBuffer(int capacity) {
                return new BoundedBufferUsingCondition<Integer>(capacity);
            }
        },
        ABQ {
            BoundedBuffer<Integer> newBuffer(final int capacity) {
                return new BoundedBufferUsingABQ<Integer>(capacity);
            }
        },
    ;
        abstract BoundedBuffer<Integer> newBuffer(int capacity);
    }

    void run() {
        int[] counts = { 100, 1000, 10000 };
        int[] capacities  = { 1, 2, 4, 8, 64, 1024};
        final int NTRIALS = 10;

        System.out.print( "N\tCAPACITY");
        for (Mode mode : Mode.values()) System.out.print("\t"+mode);
        System.out.println();

        for (int capacity : capacities) {
            for (int count : counts) {
                System.out.print(""+count+"\t"+capacity);
                for (Mode mode : Mode.values()) {
                    long elapsed = 0;
                    for (int i = 0; i < NTRIALS; ++i)
                        elapsed += trial(count, capacity, mode);

                    long avg = elapsed / (NTRIALS * count);
                    System.out.print("\t"+avg);
                }
                System.out.println();
            }
        }
    }

    private long trial(final int N, int capacity, Mode mode) {

        final BoundedBuffer<Integer> buf = mode.newBuffer(capacity);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final CountDownLatch startSignal = new CountDownLatch(1);
            final CountDownLatch doneSignal = new CountDownLatch(2);

            Runnable producer = new Runnable() {
                public void run() {
                    try {
                        startSignal.await();
                        for (int i = 0; i < N; ++i)
                            buf.put(rnd.nextInt(6));
                        doneSignal.countDown();
                    }
                    catch (InterruptedException e) {} // XXX ignored
                }
                PseudoRandom rnd = new PseudoRandomUsingAtomic(SEED);
            };

            Runnable consumer = new Runnable() {
                public void run() {
                    try {
                        startSignal.await();
                        for (int i = 0; i < N; ++i)
                            buf.take();
                        doneSignal.countDown();
                    }
                    catch (InterruptedException e) {} // XXX ignored
                }
            };

            executor.execute(consumer);
            executor.execute(producer);

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