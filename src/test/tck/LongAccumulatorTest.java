/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.LongAccumulator;

import junit.framework.Test;
import junit.framework.TestSuite;

public class LongAccumulatorTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(LongAccumulatorTest.class);
    }

    /**
     * default constructed initializes to zero
     */
    public void testConstructor() {
        LongAccumulator ai = new LongAccumulator(Long::max, 0L);
        assertEquals(0, ai.get());
    }

    /**
     * accumulate accumulates given value to current, and get returns current value
     */
    public void testAccumulateAndGet() {
        LongAccumulator ai = new LongAccumulator(Long::max, 0L);
        ai.accumulate(2);
        assertEquals(2, ai.get());
        ai.accumulate(-4);
        assertEquals(2, ai.get());
        ai.accumulate(4);
        assertEquals(4, ai.get());
    }

    /**
     * reset zeroes get
     */
    public void testReset() {
        LongAccumulator ai = new LongAccumulator(Long::max, 0L);
        ai.accumulate(2);
        assertEquals(2, ai.get());
        ai.reset();
        assertEquals(0, ai.get());
    }

    /**
     * getThenReset returns get then zeros
     */
    public void testGetThenReset() {
        LongAccumulator ai = new LongAccumulator(Long::max, 0L);
        ai.accumulate(2);
        assertEquals(2, ai.get());
        assertEquals(2, ai.getThenReset());
        assertEquals(0, ai.get());
    }

    /**
     * toString returns current value.
     */
    public void testToString() {
        LongAccumulator ai = new LongAccumulator(Long::max, 0L);
        assertEquals("0", ai.toString());
        ai.accumulate(1);
        assertEquals(Long.toString(1), ai.toString());
    }

    /**
     * intValue returns current value.
     */
    public void testIntValue() {
        LongAccumulator ai = new LongAccumulator(Long::max, 0L);
        assertEquals(0, ai.intValue());
        ai.accumulate(1);
        assertEquals(1, ai.intValue());
    }

    /**
     * longValue returns current value.
     */
    public void testLongValue() {
        LongAccumulator ai = new LongAccumulator(Long::max, 0L);
        assertEquals(0, ai.longValue());
        ai.accumulate(1);
        assertEquals(1, ai.longValue());
    }

    /**
     * floatValue returns current value.
     */
    public void testFloatValue() {
        LongAccumulator ai = new LongAccumulator(Long::max, 0L);
        assertEquals(0.0f, ai.floatValue());
        ai.accumulate(1);
        assertEquals(1.0f, ai.floatValue());
    }

    /**
     * doubleValue returns current value.
     */
    public void testDoubleValue() {
        LongAccumulator ai = new LongAccumulator(Long::max, 0L);
        assertEquals(0.0, ai.doubleValue());
        ai.accumulate(1);
        assertEquals(1.0, ai.doubleValue());
    }

    /**
     * accumulates by multiple threads produce correct result
     */
    public void testAccumulateAndGetMT() {
        final int incs = 1000000;
        final int nthreads = 4;
        final ExecutorService pool = Executors.newCachedThreadPool();
        LongAccumulator a = new LongAccumulator(Long::max, 0L);
        Phaser phaser = new Phaser(nthreads + 1);
        for (int i = 0; i < nthreads; ++i)
            pool.execute(new AccTask(a, phaser, incs));
        phaser.arriveAndAwaitAdvance();
        phaser.arriveAndAwaitAdvance();
        long expected = incs - 1;
        long result = a.get();
        assertEquals(expected, result);
        pool.shutdown();
    }

    static final class AccTask implements Runnable {
        final LongAccumulator acc;
        final Phaser phaser;
        final int incs;
        volatile long result;
        AccTask(LongAccumulator acc, Phaser phaser, int incs) {
            this.acc = acc;
            this.phaser = phaser;
            this.incs = incs;
        }

        public void run() {
            phaser.arriveAndAwaitAdvance();
            LongAccumulator a = acc;
            for (int i = 0; i < incs; ++i)
                a.accumulate(i);
            result = a.get();
            phaser.arrive();
        }
    }

}
