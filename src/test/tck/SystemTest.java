/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;

public class SystemTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());   
    }
    
    public static Test suite() {
        return new TestSuite(SystemTest.class);
    }

    /**
     * Nanos between readings of millis is no longer than millis (plus
     * one milli to allow for rounding).
     * This shows only that nano timing not (much) worse than milli.
     */
    public void testNanoTime1() {
        try {
            long m1 = System.currentTimeMillis();
            Thread.sleep(1);
            long n1 = System.nanoTime();
            Thread.sleep(SHORT_DELAY_MS);
            long n2 = System.nanoTime();
            Thread.sleep(1);
            long m2 = System.currentTimeMillis();
            long millis = m2 - m1;
            long nanos = n2 - n1;
            
            assertTrue(nanos >= 0);
            assertTrue(nanos < (millis+1) * 1000000);
        }
        catch(InterruptedException ie) {
            unexpectedException();
        }
    }

    /**
     * Millis between readings of nanos is no longer than nanos
     * This shows only that nano timing not (much) worse than milli.
     */
    public void testNanoTime2() {
        try {
            long n1 = System.nanoTime();
            Thread.sleep(1);
            long m1 = System.currentTimeMillis();
            Thread.sleep(SHORT_DELAY_MS);
            long m2 = System.currentTimeMillis();
            Thread.sleep(1);
            long n2 = System.nanoTime();
            long millis = m2 - m1;
            long nanos = n2 - n1;
            
            assertTrue(nanos >= 0);
            assertTrue(millis * 1000000 <= nanos);
        }
        catch(InterruptedException ie) {
            unexpectedException();
        }
    }

}

