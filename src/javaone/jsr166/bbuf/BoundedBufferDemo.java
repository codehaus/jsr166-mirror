package jsr166.bbuf;

import java.text.NumberFormat;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Compares performance of BoundedBuffer implementation using
 * native Conditions with an implementation using dl.u.c CondVars
 * and an implementation using j.u.c ArrayBlockingQueue.
 *
 * Sample results obtained on Pentium 4 @ 3.2 GHz -client (Win XP SP1)
 *
 * count=  100     size=   1   CONDVAR: 44492.27   CONDITION: 48841.38   ABQ: 30594.15
 * count= 1000     size=   1   CONDVAR: 21418.45   CONDITION: 18795.19   ABQ: 18852.92
 * count=10000     size=   1   CONDVAR: 21274.89   CONDITION: 18072.15   ABQ: 18195.83
 *
 * count=  100     size=   2   CONDVAR: 26484.28   CONDITION: 10297.97   ABQ:  8798.86
 * count= 1000     size=   2   CONDVAR: 27090.81   CONDITION: 10202.68   ABQ:  4347.01
 * count=10000     size=   2   CONDVAR: 26570.30   CONDITION:  9336.42   ABQ:  3521.01
 *
 * count=  100     size=   4   CONDVAR: 23004.82   CONDITION:  5862.64   ABQ:  3952.65
 * count= 1000     size=   4   CONDVAR: 21680.27   CONDITION:  4696.97   ABQ:  1974.28
 * count=10000     size=   4   CONDVAR: 22222.41   CONDITION:  4647.62   ABQ:  1888.04
 *
 * count=  100     size=   8   CONDVAR: 19886.62   CONDITION:  4967.40   ABQ:  2799.95
 * count= 1000     size=   8   CONDVAR: 21742.35   CONDITION:  2532.28   ABQ:  1635.44
 * count=10000     size=   8   CONDVAR: 21753.01   CONDITION:  2465.09   ABQ:  1470.60
 *
 * count=  100     size=  64   CONDVAR: 16366.01   CONDITION:  4921.32   ABQ:  2698.38
 * count= 1000     size=  64   CONDVAR: 19637.40   CONDITION:  1632.69   ABQ:  1745.33
 * count=10000     size=  64   CONDVAR: 21205.39   CONDITION:  1521.99   ABQ:  1690.17
 *
 * count=  100     size=1024   CONDVAR: 13665.57   CONDITION:  2242.70   ABQ:  2708.56
 * count= 1000     size=1024   CONDVAR: 14828.23   CONDITION:  1504.33   ABQ:  1859.53
 * count=10000     size=1024   CONDVAR: 20851.61   CONDITION:  1396.44   ABQ:  1828.14
 */
public final class BoundedBufferDemo {

    public static void main(String[] args) {
        new BoundedBufferDemo().run();
    }


    abstract enum Mode {
        CONDVAR {
            BoundedBuffer<Integer> newBuffer(int size) {
                return new CondVarBoundedBuffer<Integer>(size);
            }
        },
        CONDITION {
            BoundedBuffer<Integer> newBuffer(int size) {
                return new ConditionBoundedBuffer<Integer>(size);
            }
        },
        ABQ {
            BoundedBuffer<Integer> newBuffer(final int size) {
                return new BoundedBuffer<Integer>() {
                    public void put(Integer element) throws InterruptedException {
                        abq.put(element);
                    }
                    public Integer take() throws InterruptedException {
                        return abq.take();
                    }
                    private final ArrayBlockingQueue<Integer> abq
                        = new ArrayBlockingQueue<Integer>(size);
                };
            }
        },
    ;
        abstract BoundedBuffer<Integer> newBuffer(int size);
    }

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

        final BoundedBuffer<Integer> buf = mode.newBuffer(size);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final CountDownLatch startSignal = new CountDownLatch(1);
            final CountDownLatch doneSignal = new CountDownLatch(2);

            Runnable producer = new Runnable() {
                public void run() {
                    try {
                        startSignal.await();
                        for (int c = 0; c < count; ++c)
                            buf.put(random.nextInt(6));
                        doneSignal.countDown();
                    }
                    catch (InterruptedException e) {} // XXX ignored
                }
                Random random = new Random(SEED);
            };

            Runnable consumer = new Runnable() {
                public void run() {
                    try {
                        startSignal.await();
                        for (int c = 0; c < count; ++c)
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

    private static final NumberFormat fmt = NumberFormat.getInstance();
    static {
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);
        fmt.setGroupingUsed(false);
    }
}