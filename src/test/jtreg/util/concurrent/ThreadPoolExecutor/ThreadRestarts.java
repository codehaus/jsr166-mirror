/*
 * Written by Martin Buchholz and Jason Mehrens with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @summary Only one thread should be created when a thread needs to
 * be kept alive to service a delayed task waiting in the queue.
 */

import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.*;
import java.util.concurrent.atomic.*;

public class ThreadRestarts {
    public static void main(String[] args) throws Exception {
        test(false);
        test(true);
    }

    private static void test(boolean allowTimeout) throws Exception {
        CountingThreadFactory ctf = new CountingThreadFactory();
        ScheduledThreadPoolExecutor stpe
            = new ScheduledThreadPoolExecutor(10, ctf);
        try {
            Runnable nop = new Runnable() { public void run() {}};
            stpe.schedule(nop, 10*1000L, MILLISECONDS);
            stpe.setKeepAliveTime(1L, MILLISECONDS);
            stpe.allowCoreThreadTimeOut(allowTimeout);
            MILLISECONDS.sleep(100L);
        } finally {
            stpe.shutdownNow();
            stpe.awaitTermination(Long.MAX_VALUE, MILLISECONDS);
        }
        if (ctf.count.get() > 1)
            throw new AssertionError(
                String.format("%d threads created, 1 expected",
                              ctf.count.get()));
    }

    static class CountingThreadFactory implements ThreadFactory {
        final AtomicLong count = new AtomicLong(0L);

        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            count.getAndIncrement();
            return t;
        }
    }
}
