package jsr166.bbuf;

import java.text.NumberFormat;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Compares performance of BoundedBuffer implementation using
 * native Conditions with an implementation using dl.u.c CondVars.
 *
 * Sample results obtained on Pentium 4 @ 3.2 GHz -client (Win XP SP1)
 *
 * count=100       size=1     NATIVE: 52164.70    DL_U_C: 41299.12
 * count=1000      size=1     NATIVE: 17886.59    DL_U_C: 21110.29
 * count=10000     size=1     NATIVE: 17736.65    DL_U_C: 21178.19
 *
 * count=100       size=2     NATIVE: 11318.42    DL_U_C: 29474.30
 * count=1000      size=2     NATIVE:  9456.33    DL_U_C: 27046.71
 * count=10000     size=2     NATIVE:  8995.79    DL_U_C: 27220.45
 *
 * count=100       size=4     NATIVE:  7838.94    DL_U_C: 23670.77
 * count=1000      size=4     NATIVE:  5007.80    DL_U_C: 22594.43
 * count=10000     size=4     NATIVE:  4648.41    DL_U_C: 22383.00
 *
 * count=100       size=8     NATIVE:  7565.63    DL_U_C: 22412.73
 * count=1000      size=8     NATIVE:  2654.70    DL_U_C: 22254.11
 * count=10000     size=8     NATIVE:  2468.30    DL_U_C: 22111.69
 *
 * count=100       size=64    NATIVE:  4743.20    DL_U_C: 20079.90
 * count=1000      size=64    NATIVE:  2074.30    DL_U_C: 21592.09
 * count=10000     size=64    NATIVE:  1378.57    DL_U_C: 21692.48
 *
 * count=100       size=1024  NATIVE:  5377.27    DL_U_C: 17551.21
 * count=1000      size=1024  NATIVE:  1170.03    DL_U_C: 16591.20
 * count=10000     size=1024  NATIVE:  1369.83    DL_U_C: 21369.30
 */
public final class BoundedBufferDemo {

    public static void main(String[] args) {
        new BoundedBufferDemo().run();
    }

    enum Mode { NATIVE, DL_U_C }

    void run() {
        int[] counts = { 100, 1000, 10000 };
        int[] sizes  = { 1, 2, 4, 8, 64, 1024};
        final int NTRIALS = 10;

        for (int size : sizes) {
            for (int count : counts) {
                System.out.print( "count="+count+"\tsize="+size);
                for (Mode mode : Mode.values()) {
                    long elapsed = 0;
                    for (int i = 0; i < NTRIALS; ++i)
                        elapsed += trial(count, size, mode);

                    double avg = ((double) elapsed) / (NTRIALS * count);
                    System.out.print("\t"+mode+": "+fmt.format(avg));
                }
                System.out.println();
            }
        }
    }

    private long trial(final int count, int size, Mode mode) {

        final BoundedBuffer<Integer> buf = (mode == Mode.NATIVE) ?
                new ConditionBoundedBuffer<Integer>(size) :
                new CondVarBoundedBuffer<Integer>(size);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final CountDownLatch startSignal = new CountDownLatch(1);
            final CountDownLatch doneSignal = new CountDownLatch(2);

            Runnable producer = new Runnable() {
                public void run() {
                    try {
                        Random random = new Random(SEED);
                        startSignal.await();
                        for (int c = 0; c < count; ++c) {
                            int i = random.nextInt(6);
                            buf.put(i);
                        }
                        doneSignal.countDown();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // propagate
                    }
                }
            };

            Runnable consumer = new Runnable() {
                public void run() {
                    try {
                        startSignal.await();
                        for (int c = 0; c < count; ++c) {
                            int i = buf.take();
                        }
                        doneSignal.countDown();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // propagate
                    }
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

    private static final NumberFormat fmt = NumberFormat.getInstance();
    static {
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);
        fmt.setGroupingUsed(false);
    }
}