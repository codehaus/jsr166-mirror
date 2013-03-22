/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import junit.framework.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.DoubleAdder;

public class DoubleAdderTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(DoubleAdderTest.class);
    }

    /**
     * default constructed initializes to zero
     */
    public void testConstructor() {
        DoubleAdder ai = new DoubleAdder();
        assertEquals(0.0, ai.sum());
    }

    /**
     * add adds given value to current, and sum returns current value
     */
    public void testAddAndSum() {
        DoubleAdder ai = new DoubleAdder();
        ai.add(2.0);
        assertEquals(2.0, ai.sum());
        ai.add(-4.0);
        assertEquals(-2.0, ai.sum());
    }

    /**
     * reset zeroes sum
     */
    public void testReset() {
        DoubleAdder ai = new DoubleAdder();
        ai.add(2.0);
        assertEquals(2.0, ai.sum());
        ai.reset();
        assertEquals(0.0, ai.sum());
    }

    /**
     * sumThenReset returns sum then zeros
     */
    public void testSumThenReset() {
        DoubleAdder ai = new DoubleAdder();
        ai.add(2.0);
        assertEquals(2.0, ai.sum());
        assertEquals(2.0, ai.sumThenReset());
        assertEquals(0.0, ai.sum());
    }

    /**
     * a deserialized serialized adder holds same value
     */
    public void testSerialization() throws Exception {
        DoubleAdder x = new DoubleAdder();
        DoubleAdder y = serialClone(x);
        assertTrue(x != y);
        x.add(-22.0);
        DoubleAdder z = serialClone(x);
        assertEquals(-22.0, x.sum());
        assertEquals(0.0, y.sum());
        assertEquals(-22.0, z.sum());
    }

    /**
     * toString returns current value.
     */
    public void testToString() {
        DoubleAdder ai = new DoubleAdder();
        assertEquals(Double.toString(0.0), ai.toString());
        ai.add(1.0);
        assertEquals(Double.toString(1.0), ai.toString());
    }

    /**
     * intValue returns current value.
     */
    public void testIntValue() {
        DoubleAdder ai = new DoubleAdder();
        assertEquals(0, ai.intValue());
        ai.add(1.0);
        assertEquals(1, ai.intValue());
    }

    /**
     * longValue returns current value.
     */
    public void testLongValue() {
        DoubleAdder ai = new DoubleAdder();
        assertEquals(0, ai.longValue());
        ai.add(1.0);
        assertEquals(1, ai.longValue());
    }

    /**
     * floatValue returns current value.
     */
    public void testFloatValue() {
        DoubleAdder ai = new DoubleAdder();
        assertEquals(0.0f, ai.floatValue());
        ai.add(1.0);
        assertEquals(1.0f, ai.floatValue());
    }

    /**
     * doubleValue returns current value.
     */
    public void testDoubleValue() {
        DoubleAdder ai = new DoubleAdder();
        assertEquals(0.0, ai.doubleValue());
        ai.add(1.0);
        assertEquals(1.0, ai.doubleValue());
    }

    /**
     * adds by multiple threads produce correct sum
     */
    public void testAddAndSumMT() {
        final int incs = 1000000;
        final int nthreads = 4;
        final ExecutorService pool = Executors.newCachedThreadPool();
        DoubleAdder a = new DoubleAdder();
        Phaser phaser = new Phaser(nthreads + 1);
        for (int i = 0; i < nthreads; ++i)
            pool.execute(new AdderTask(a, phaser, incs));
        phaser.arriveAndAwaitAdvance();
        phaser.arriveAndAwaitAdvance();
        double total = (long)nthreads * incs;
        double sum = a.sum();
        assertEquals(sum, total);
        pool.shutdown();
    }

    static final class AdderTask implements Runnable {
        final DoubleAdder adder;
        final Phaser phaser;
        final int incs;
        volatile double result;
        AdderTask(DoubleAdder adder, Phaser phaser, int incs) {
            this.adder = adder;
            this.phaser = phaser;
            this.incs = incs;
        }

        public void run() {
            phaser.arriveAndAwaitAdvance();
            DoubleAdder a = adder;
            for (int i = 0; i < incs; ++i)
                a.add(1.0);
            result = a.sum();
            phaser.arrive();
        }
    }

}
