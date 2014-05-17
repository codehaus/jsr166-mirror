/*
 * Written by Martin Buchholz and Doug Lea with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import junit.framework.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import java.util.*;

public class ThreadPoolExecutor9Test extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(ThreadPoolExecutor9Test.class);
    }

    /**
     * Configuration changes that allow core pool size greater than
     * max pool size result in IllegalArgumentException.
     */
    public void testPoolSizeInvariants() {
        ThreadPoolExecutor p =
            new ThreadPoolExecutor(1, 1,
                                   LONG_DELAY_MS, MILLISECONDS,
                                   new ArrayBlockingQueue<Runnable>(10));
        for (int s = 1; s < 5; s++) {
            p.setMaximumPoolSize(s);
            p.setCorePoolSize(s);
            try {
                p.setMaximumPoolSize(s - 1);
                shouldThrow();
            } catch (IllegalArgumentException success) {}
            assertEquals(s, p.getCorePoolSize());
            assertEquals(s, p.getMaximumPoolSize());
            try {
                p.setCorePoolSize(s + 1);
                shouldThrow();
            } catch (IllegalArgumentException success) {}
            assertEquals(s, p.getCorePoolSize());
            assertEquals(s, p.getMaximumPoolSize());
        }
        joinPool(p);
    }

}
