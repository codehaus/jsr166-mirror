/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;


/**
 * Base class for JSR166 Junit TCK tests.  Defines some constants and
 * utility methods, as well as a simple framework for helping to make
 * sure that assertions failing in generated threads cause the
 * associated test that generated them to itself fail (which JUnit doe
 * not otherwise arrange).  The rules for creating such tests are:
 *
 * <ol>
 *
 * <li> All assertions in code running in generated threads must use
 * the forms {@link threadFail} , {@link threadAssertTrue} {@link
 * threadAssertEquals}, or {@link threadAssertNull}, (not
 * <tt>fail</tt>, <tt>assertTrue</tt>, etc.) It is OK (but not
 * particularly recommended) for other code to use these forms too.
 * Only the most typically used JUnit assertion methods are defined
 * this way, but enough to live with.</li>
 *
 * <li> If you override {@link setUp} or {@link tearDown}, make sure
 * to invoke <tt>super.setUp</tt> and <tt>super.tearDown</tt> within
 * them. These methods are used to clear and check for thread
 * assertion failures.</li>
 *
 * <li>All delays and timeouts must use one of the constants {@link
 * SHORT_DELAY_MS}, {@link SMALL_DELAY_MS}, {@link MEDIUM_DELAY_MS},
 * {@link LONG_DELAY_MS}. The idea here is that a SHORT is always
 * discriminatable from zero time, and always allows enough time for
 * the small amounts of computation (creating a thread, calling a few
 * methods, etc) needed to reach a timeout point. Similarly, a SMALL
 * is always discriminable as larger than SHORT and smaller than
 * MEDIUM.  And so on. These constants are set to conservative values,
 * but even so, if there is ever any doubt, they can all be increased
 * in one spot to rerun tests on slower platforms</li>
 *
 * <li> All threads generated must be joined inside each test case
 * method (or <tt>fail</tt> to do so) before returning from the
 * method. The {@link joinPool} method can be used to do this when
 * using Executors.</li>
 *
 * </ol>
 */
public class JSR166TestCase extends TestCase {

    public static long SHORT_DELAY_MS;
    public static long SMALL_DELAY_MS;
    public static long MEDIUM_DELAY_MS;
    public static long LONG_DELAY_MS;


    /**
     * Return the shortest timed delay. This could
     * be reimplmented to use for example a Property.
     */ 
    protected long getShortDelay() {
        return 50;
    }


    /**
     * Set delays as multiples fo SHORT_DELAY.
     */
    protected  void setDelays() {
        SHORT_DELAY_MS = getShortDelay();
        SMALL_DELAY_MS = SHORT_DELAY_MS * 5;
        MEDIUM_DELAY_MS = SHORT_DELAY_MS * 10;
        LONG_DELAY_MS = SHORT_DELAY_MS * 50;
    }

    /**
     * Flag set true if any threadAssert methods fail
     */
    protected volatile boolean threadFailed;

    /**
     * Initialize test to indicat that no thread assertions have failed
     */
    public void setUp() { 
        setDelays();
        threadFailed = false;  
    }

    /**
     * Trigger test case failure if any thread assertions have failed
     */
    public void tearDown() { 
        assertFalse(threadFailed);  
    }

    public void threadFail(String reason) {
        threadFailed = true;
        fail(reason);
    }

    public void threadAssertTrue(boolean b) {
        if (!b) {
            threadFailed = true;
            assertTrue(b);
        }
    }
    public void threadAssertFalse(boolean b) {
        if (b) {
            threadFailed = true;
            assertFalse(b);
        }
    }
    public void threadAssertNull(Object x) {
        if (x != null) {
            threadFailed = true;
            assertNull(x);
        }
    }
    public void threadAssertEquals(long x, long y) {
        if (x != y) {
            threadFailed = true;
            assertEquals(x, y);
        }
    }
    public void threadAssertEquals(Object x, Object y) {
        if (x != y && (x == null || !x.equals(y))) {
            threadFailed = true;
            assertEquals(x, y);
        }
    }

    /**
     * Wait out termination of a thread pool or fail doing so
     */
    public void joinPool(ExecutorService exec) {
        try {
            exec.shutdown();
            assertTrue(exec.awaitTermination(LONG_DELAY_MS, TimeUnit.MILLISECONDS));
        } catch(InterruptedException ie) {
            fail("unexpected exception");
        }
    }



    /**
     * The number of elements to place in collections, arrays, etc.
     */
    public static final int SIZE = 20;

    // Some convenient Integer constants

    public static final Integer zero = new Integer(0);
    public static final Integer one = new Integer(1);
    public static final Integer two = new Integer(2);
    public static final Integer three  = new Integer(3);
    public static final Integer four  = new Integer(4);
    public static final Integer five  = new Integer(5);
    public static final Integer six = new Integer(6);
    public static final Integer seven = new Integer(7);
    public static final Integer eight = new Integer(8);
    public static final Integer nine = new Integer(9);
    public static final Integer m1  = new Integer(-1);
    public static final Integer m2  = new Integer(-2);
    public static final Integer m3  = new Integer(-3);
    public static final Integer m4 = new Integer(-4);
    public static final Integer m5 = new Integer(-5);
    public static final Integer m10 = new Integer(-10);


    // Some convenient Runnable classes

    public static class NoOpRunnable implements Runnable {
        public void run() {}
    }

    public static class NoOpCallable implements Callable {
        public Object call() { return Boolean.TRUE; }
    }

    public class ShortRunnable implements Runnable {
        public void run() {
            try {
                Thread.sleep(SHORT_DELAY_MS);
            }
            catch(Exception e) {
                threadFail("unexpectedException");
            }
        }
    }

    public class ShortInterruptedRunnable implements Runnable {
        public void run() {
            try {
                Thread.sleep(SHORT_DELAY_MS);
                threadFail("should throw IE");
            }
            catch(InterruptedException success) {
            }
        }
    }

    public class SmallRunnable implements Runnable {
        public void run() {
            try {
                Thread.sleep(SMALL_DELAY_MS);
            }
            catch(Exception e) {
                threadFail("unexpectedException");
            }
        }
    }

    public class SmallCallable implements Callable {
        public Object call() {
            try {
                Thread.sleep(SMALL_DELAY_MS);
            }
            catch(Exception e) {
                threadFail("unexpectedException");
            }
            return Boolean.TRUE;
        }
    }

    public class SmallInterruptedRunnable implements Runnable {
        public void run() {
            try {
                Thread.sleep(SMALL_DELAY_MS);
                threadFail("should throw IE");
            }
            catch(InterruptedException success) {
            }
        }
    }


    public class MediumRunnable implements Runnable {
        public void run() {
            try {
                Thread.sleep(MEDIUM_DELAY_MS);
            }
            catch(Exception e) {
                threadFail("unexpectedException");
            }
        }
    }

    public class MediumInterruptedRunnable implements Runnable {
        public void run() {
            try {
                Thread.sleep(MEDIUM_DELAY_MS);
                threadFail("should throw IE");
            }
            catch(InterruptedException success) {
            }
        }
    }

    public class MediumPossiblyInterruptedRunnable implements Runnable {
        public void run() {
            try {
                Thread.sleep(MEDIUM_DELAY_MS);
            }
            catch(InterruptedException success) {
            }
        }
    }
    
}
