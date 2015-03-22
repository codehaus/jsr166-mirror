/*
 * Written by Martin Buchholz with assistance from members of JCP
 * JSR-166 Expert Group and released to the public domain, as
 * explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @bug 8073704
 * @summary Checks that once isDone() returns true,
 * get() never throws InterruptedException or TimeoutException
 */

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DoneMeansDone {
    public static void main(String[] args) throws Throwable {
        final int iters = 1000;
        final int nThreads = 2;
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicReference<FutureTask<Boolean>> a = new AtomicReference<>();
        final CountDownLatch threadsStarted = new CountDownLatch(nThreads);
        final Callable<Boolean> alwaysTrue = new Callable<Boolean>() {
            public Boolean call() {
                return true;
            }};

        final Runnable observer = new Runnable() { public void run() {
            threadsStarted.countDown();
            final ThreadLocalRandom rnd = ThreadLocalRandom.current();
            try {
                for (FutureTask<Boolean> f; !done.get();) {
                    f = a.get();
                    if (f != null) {
                        do {} while (!f.isDone());
                        Thread.currentThread().interrupt();
                        if (!(rnd.nextBoolean()
                              ? f.get()
                              : f.get(-1L, TimeUnit.DAYS)))
                            throw new AssertionError();
                    }
                }
            } catch (Exception t) { throw new AssertionError(t); }
        }};

        final ArrayList<Future<?>> futures = new ArrayList<>(nThreads);
        final ExecutorService pool = Executors.newCachedThreadPool();
        for (int i = 0; i < nThreads; i++)
            futures.add(pool.submit(observer));
        threadsStarted.await();
        for (int i = 0; i < iters; i++) {
            FutureTask<Boolean> f = new FutureTask<>(alwaysTrue);
            a.set(f);
            f.run();
        }
        done.set(true);
        pool.shutdown();
        if (!pool.awaitTermination(10L, TimeUnit.SECONDS))
            throw new AssertionError();
        for (Future<?> future : futures)
            future.get();
    }
}
