/*
 * Written by Doug Lea and Martin Buchholz with assistance from members of
 * JCP JSR-166 Expert Group and released to the public domain, as explained
 * at http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Tries to demonstrate a leaked interrupt.
 */
public class FutureTaskCancelLoops {
    static final AtomicLong count = new AtomicLong(0);

    static volatile Future<?> cancelMe = null;

    static volatile boolean leakedInterrupt = false;

    static class InterruptMeTask extends FutureTask<Void> {
        static class InterruptMe implements Runnable {
            volatile Future<?> myFuture;

            public void run() {
                assert myFuture != null;
                if (cancelMe != null) {
                    // We're likely to get the interrupt meant for previous task.
                    // Clear interrupts first to prove *we* got interrupted.
                    Thread.interrupted();
                    while (cancelMe != null && !leakedInterrupt) {
                        if (Thread.interrupted()) {
                            leakedInterrupt = true;
                            System.err.println("leaked interrupt!");
                        }
                    }
                } else {
                    cancelMe = myFuture;
                    do {} while (! myFuture.isCancelled() && !leakedInterrupt);
                }
                count.getAndIncrement();
            }
        }
        InterruptMeTask() { this(new InterruptMe()); }
        InterruptMeTask(InterruptMe r) {
            super(r, null);
            r.myFuture = this;
        }
    }

    static long millisElapsedSince(long startTimeNanos) {
        return (System.nanoTime() - startTimeNanos)/(1000L*1000L);
    }

    public static void main(String[] args) throws Exception {
        long startTime = System.nanoTime();
        final ThreadPoolExecutor pool =
            new ThreadPoolExecutor(1, 1,
                                   0L, TimeUnit.MILLISECONDS,
                                   new LinkedBlockingQueue<Runnable>(10000));

        final Thread cancelBot = new Thread(new Runnable() {
            public void run() {
                while (!leakedInterrupt) {
                    Future<?> future = cancelMe;
                    if (future != null) {
                        future.cancel(true);
                        cancelMe = null;
                    }}}});
        cancelBot.setDaemon(true);
        cancelBot.start();

        while (!leakedInterrupt && millisElapsedSince(startTime) < 1000L) {
            try {
                pool.execute(new InterruptMeTask());
            } catch (RejectedExecutionException ree) {
                Thread.sleep(1);
            }
        }
        pool.shutdownNow();
        if (leakedInterrupt) {
            String msg = String.format
                ("%d tasks run, %d millis elapsed, till leaked interrupt%n",
                 count.get(), millisElapsedSince(startTime));
            throw new IllegalStateException(msg);
        } else {
            System.out.printf
                ("%d tasks run, %d millis elapsed%n",
                 count.get(), millisElapsedSince(startTime));
        }
    }
}
