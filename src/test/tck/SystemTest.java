/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;

public class SystemTest extends TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run(suite());	
    }
    
    public static Test suite() {
	return new TestSuite(SystemTest.class);
    }

    public void testNanoTime1() {
        // Nanos between readings of millis must be no longer than millis
        long m1 = System.currentTimeMillis();
        long n1 = System.nanoTime();

        // Ensure some computation that is not optimized away.
        long sum = 0;
        for (long i = 1; i < 10000; ++i)
            sum += i;
        assertTrue(sum != 0);

        long n2 = System.nanoTime();

        for (long i = 1; i < 10000; ++i)
            sum -= i;
        assertTrue(sum == 0);

        long m2 = System.currentTimeMillis();
        long millis = m2 - m1;
        long nanos = n2 - n1;

        assertTrue(nanos >= 0);
        assertTrue(nanos <= millis * 1000000);
    }

    public void testNanoTime2() {
        // Millis between readings of nanos must be no longer than nanos
        long n1 = System.nanoTime();
        long m1 = System.currentTimeMillis();

        // Ensure some computation that is not optimized away.
        long sum = 0;
        for (long i = 1; i < 10000; ++i)
            sum += i;
        assertTrue(sum != 0);

        long m2 = System.currentTimeMillis();

        for (long i = 1; i < 10000; ++i)
            sum -= i;
        assertTrue(sum == 0);

        long n2 = System.nanoTime();

        long millis = m2 - m1;
        long nanos = n2 - n1;

        assertTrue(nanos >= 0);
        assertTrue(millis * 1000000 <= nanos);
    }

}

