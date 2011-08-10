/*
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import junit.framework.*;
import java.util.Arrays;
import jsr166e.extra.AtomicDoubleArray;

public class AtomicDoubleArrayTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicDoubleArrayTest.class);
    }

    final double[] VALUES = {
        Double.NEGATIVE_INFINITY,
        -Double.MAX_VALUE,
        (double)Long.MIN_VALUE,
        (double)Integer.MIN_VALUE,
        -Math.PI,
        -1.0,
        -Double.MIN_VALUE,
        -0.0,
        +0.0,
        Double.MIN_VALUE,
        1.0,
        Math.PI,
        (double)Integer.MAX_VALUE,
        (double)Long.MAX_VALUE,
        Double.MAX_VALUE,
        Double.POSITIVE_INFINITY,
        Double.NaN,
    };

    /**
     * constructor creates array of given size with all elements zero
     */
    public void testConstructor() {
        AtomicDoubleArray aa = new AtomicDoubleArray(SIZE);
        for (int i = 0; i < SIZE; ++i)
            assertEquals(0.0, aa.get(i));
    }

    /**
     * constructor with null array throws NPE
     */
    public void testConstructor2NPE() {
        try {
            double[] a = null;
            AtomicDoubleArray aa = new AtomicDoubleArray(a);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * constructor with array is of same size and has all elements
     */
    public void testConstructor2() {
        double[] a = { 17.0, 3.0, -42.0, 99.0, -7.0 };
        AtomicDoubleArray aa = new AtomicDoubleArray(a);
        assertEquals(a.length, aa.length());
        for (int i = 0; i < a.length; ++i)
            assertEquals(a[i], aa.get(i));
    }

    /**
     * get and set for out of bound indices throw IndexOutOfBoundsException
     */
    public void testIndexing() {
        AtomicDoubleArray aa = new AtomicDoubleArray(SIZE);
        try {
            aa.get(SIZE);
            shouldThrow();
        } catch (IndexOutOfBoundsException success) {
        }
        try {
            aa.get(-1);
            shouldThrow();
        } catch (IndexOutOfBoundsException success) {
        }
        try {
            aa.set(SIZE, 0);
            shouldThrow();
        } catch (IndexOutOfBoundsException success) {
        }
        try {
            aa.set(-1, 0.0);
            shouldThrow();
        } catch (IndexOutOfBoundsException success) {
        }
    }

    /**
     * get returns the last value set at index
     */
    public void testGetSet() {
        AtomicDoubleArray aa = new AtomicDoubleArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            aa.set(i, 1.0);
            assertEquals(1.0, aa.get(i));
            aa.set(i, 2.0);
            assertEquals(2.0, aa.get(i));
            aa.set(i, -3.0);
            assertEquals(-3.0, aa.get(i));
        }
    }

    /**
     * get returns the last value lazySet at index by same thread
     */
    public void testGetLazySet() {
        AtomicDoubleArray aa = new AtomicDoubleArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            aa.lazySet(i, 1.0);
            assertEquals(1.0, aa.get(i));
            aa.lazySet(i, 2.0);
            assertEquals(2.0, aa.get(i));
            aa.lazySet(i, -3.0);
            assertEquals(-3.0, aa.get(i));
        }
    }

    /**
     * compareAndSet succeeds in changing value if equal to expected else fails
     */
    public void testCompareAndSet() {
        AtomicDoubleArray aa = new AtomicDoubleArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            aa.set(i, 1);
            assertTrue(aa.compareAndSet(i, 1.0, 2.0));
            assertTrue(aa.compareAndSet(i, 2.0, -4.0));
            assertEquals(-4.0, aa.get(i));
            assertFalse(aa.compareAndSet(i, -5.0, 7.0));
            assertEquals(-4.0, aa.get(i));
            assertTrue(aa.compareAndSet(i, -4.0, 7.0));
            assertEquals(7.0, aa.get(i));
        }
    }

    /**
     * compareAndSet in one thread enables another waiting for value
     * to succeed
     */
    public void testCompareAndSetInMultipleThreads() throws InterruptedException {
        final AtomicDoubleArray a = new AtomicDoubleArray(1);
        a.set(0, 1);
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() {
                while (!a.compareAndSet(0, 2.0, 3.0))
                    Thread.yield();
            }});

        t.start();
        assertTrue(a.compareAndSet(0, 1.0, 2.0));
        t.join(LONG_DELAY_MS);
        assertFalse(t.isAlive());
        assertEquals(3.0, a.get(0));
    }

    /**
     * repeated weakCompareAndSet succeeds in changing value when equal
     * to expected
     */
    public void testWeakCompareAndSet() {
        AtomicDoubleArray aa = new AtomicDoubleArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            aa.set(i, 1.0);
            while (!aa.weakCompareAndSet(i, 1.0, 2.0));
            while (!aa.weakCompareAndSet(i, 2.0, -4.0));
            assertEquals(-4.0, aa.get(i));
            while (!aa.weakCompareAndSet(i, -4.0, 7.0));
            assertEquals(7.0, aa.get(i));
        }
    }

    /**
     * getAndSet returns previous value and sets to given value at given index
     */
    public void testGetAndSet() {
        AtomicDoubleArray aa = new AtomicDoubleArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            aa.set(i, 1.0);
            assertEquals(1.0, aa.getAndSet(i, 0.0));
            assertEquals(0.0, aa.getAndSet(i, -10.0));
            assertEquals(-10.0, aa.getAndSet(i, 1.0));
        }
    }

    /**
     * getAndAdd returns previous value and adds given value
     */
    public void testGetAndAdd() {
        AtomicDoubleArray aa = new AtomicDoubleArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            aa.set(i, 1.0);
            assertEquals(1.0, aa.getAndAdd(i, 2.0));
            assertEquals(3.0, aa.get(i));
            assertEquals(3.0, aa.getAndAdd(i, -4.0));
            assertEquals(-1.0, aa.get(i));
        }
    }

    /**
     * addAndGet adds given value to current, and returns current value
     */
    public void testAddAndGet() {
        AtomicDoubleArray aa = new AtomicDoubleArray(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            aa.set(i, 1.0);
            assertEquals(3.0, aa.addAndGet(i, 2.0));
            assertEquals(3.0, aa.get(i));
            assertEquals(-1.0, aa.addAndGet(i, -4.0));
            assertEquals(-1.0, aa.get(i));
        }
    }

    static final long COUNTDOWN = 100000;

    class Counter extends CheckedRunnable {
        final AtomicDoubleArray aa;
        volatile long counts;
        Counter(AtomicDoubleArray a) { aa = a; }
        public void realRun() {
            for (;;) {
                boolean done = true;
                for (int i = 0; i < aa.length(); ++i) {
                    double v = aa.get(i);
                    assertTrue(v >= 0);
                    if (v != 0) {
                        done = false;
                        if (aa.compareAndSet(i, v, v-1))
                            ++counts;
                    }
                }
                if (done)
                    break;
            }
        }
    }

    /**
     * Multiple threads using same array of counters successfully
     * update a number of times equal to total count
     */
    public void testCountingInMultipleThreads() throws InterruptedException {
        final AtomicDoubleArray aa = new AtomicDoubleArray(SIZE);
        for (int i = 0; i < SIZE; ++i)
            aa.set(i, COUNTDOWN);
        Counter c1 = new Counter(aa);
        Counter c2 = new Counter(aa);
        Thread t1 = new Thread(c1);
        Thread t2 = new Thread(c2);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        assertEquals(c1.counts+c2.counts, SIZE * COUNTDOWN);
    }

    /**
     * a deserialized serialized array holds same values
     */
    public void testSerialization() throws Exception {
        AtomicDoubleArray x = new AtomicDoubleArray(SIZE);
        for (int i = 0; i < SIZE; ++i)
            x.set(i, -i);
        AtomicDoubleArray y = serialClone(x);
        assertTrue(x != y);
        assertEquals(x.length(), y.length());
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(x.get(i), y.get(i));
        }
    }

    /**
     * toString returns current value.
     */
    public void testToString() {
        AtomicDoubleArray aa = new AtomicDoubleArray(VALUES);
        assertEquals(Arrays.toString(VALUES), aa.toString());
    }

}
