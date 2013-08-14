/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
import junit.framework.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

public class ThreadLocalRandomTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(ThreadLocalRandomTest.class);
    }

    /*
     * Testing coverage notes:
     *
     * We don't test randomness properties, but only that repeated
     * calls, up to NCALLS tries, produce at least one different
     * result.  For bounded versions, we sample various intervals
     * across multiples of primes.
     */

    // max numbers of calls to detect getting stuck on one value
    static final int NCALLS = 10000;

    // max sampled int bound
    static final int MAX_INT_BOUND = (1 << 28);

    // max sampled long bound
    static final long MAX_LONG_BOUND = (1L << 42);

    // Number of replications for other checks
    static final int REPS = 20;

    /**
     * setSeed throws UnsupportedOperationException
     */
    public void testSetSeed() {
        try {
            ThreadLocalRandom.current().setSeed(17);
            shouldThrow();
        } catch (UnsupportedOperationException success) {}
    }

    /**
     * Repeated calls to nextInt produce at least two distinct results
     */
    public void testNextInt() {
        int f = ThreadLocalRandom.current().nextInt();
        int i = 0;
        while (i < NCALLS && ThreadLocalRandom.current().nextInt() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * Repeated calls to nextLong produce at least two distinct results
     */
    public void testNextLong() {
        long f = ThreadLocalRandom.current().nextLong();
        int i = 0;
        while (i < NCALLS && ThreadLocalRandom.current().nextLong() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * Repeated calls to nextBoolean produce at least two distinct results
     */
    public void testNextBoolean() {
        boolean f = ThreadLocalRandom.current().nextBoolean();
        int i = 0;
        while (i < NCALLS && ThreadLocalRandom.current().nextBoolean() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * Repeated calls to nextFloat produce at least two distinct results
     */
    public void testNextFloat() {
        float f = ThreadLocalRandom.current().nextFloat();
        int i = 0;
        while (i < NCALLS && ThreadLocalRandom.current().nextFloat() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * Repeated calls to nextDouble produce at least two distinct results
     */
    public void testNextDouble() {
        double f = ThreadLocalRandom.current().nextDouble();
        int i = 0;
        while (i < NCALLS && ThreadLocalRandom.current().nextDouble() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * Repeated calls to nextGaussian produce at least two distinct results
     */
    public void testNextGaussian() {
        double f = ThreadLocalRandom.current().nextGaussian();
        int i = 0;
        while (i < NCALLS && ThreadLocalRandom.current().nextGaussian() == f)
            ++i;
        assertTrue(i < NCALLS);
    }

    /**
     * nextInt(negative) throws IllegalArgumentException
     */
    public void testNextIntBoundedNeg() {
        try {
            int f = ThreadLocalRandom.current().nextInt(-17);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * nextInt(least >= bound) throws IllegalArgumentException
     */
    public void testNextIntBadBounds() {
        try {
            int f = ThreadLocalRandom.current().nextInt(17, 2);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * nextInt(bound) returns 0 <= value < bound;
     * repeated calls produce at least two distinct results
     */
    public void testNextIntBounded() {
        // sample bound space across prime number increments
        for (int bound = 2; bound < MAX_INT_BOUND; bound += 524959) {
            int f = ThreadLocalRandom.current().nextInt(bound);
            assertTrue(0 <= f && f < bound);
            int i = 0;
            int j;
            while (i < NCALLS &&
                   (j = ThreadLocalRandom.current().nextInt(bound)) == f) {
                assertTrue(0 <= j && j < bound);
                ++i;
            }
            assertTrue(i < NCALLS);
        }
    }

    /**
     * nextInt(least, bound) returns least <= value < bound;
     * repeated calls produce at least two distinct results
     */
    public void testNextIntBounded2() {
        for (int least = -15485863; least < MAX_INT_BOUND; least += 524959) {
            for (int bound = least + 2; bound > least && bound < MAX_INT_BOUND; bound += 49979687) {
                int f = ThreadLocalRandom.current().nextInt(least, bound);
                assertTrue(least <= f && f < bound);
                int i = 0;
                int j;
                while (i < NCALLS &&
                       (j = ThreadLocalRandom.current().nextInt(least, bound)) == f) {
                    assertTrue(least <= j && j < bound);
                    ++i;
                }
                assertTrue(i < NCALLS);
            }
        }
    }

    /**
     * nextLong(negative) throws IllegalArgumentException
     */
    public void testNextLongBoundedNeg() {
        try {
            long f = ThreadLocalRandom.current().nextLong(-17);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * nextLong(least >= bound) throws IllegalArgumentException
     */
    public void testNextLongBadBounds() {
        try {
            long f = ThreadLocalRandom.current().nextLong(17, 2);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * nextLong(bound) returns 0 <= value < bound;
     * repeated calls produce at least two distinct results
     */
    public void testNextLongBounded() {
        for (long bound = 2; bound < MAX_LONG_BOUND; bound += 15485863) {
            long f = ThreadLocalRandom.current().nextLong(bound);
            assertTrue(0 <= f && f < bound);
            int i = 0;
            long j;
            while (i < NCALLS &&
                   (j = ThreadLocalRandom.current().nextLong(bound)) == f) {
                assertTrue(0 <= j && j < bound);
                ++i;
            }
            assertTrue(i < NCALLS);
        }
    }

    /**
     * nextLong(least, bound) returns least <= value < bound;
     * repeated calls produce at least two distinct results
     */
    public void testNextLongBounded2() {
        for (long least = -86028121; least < MAX_LONG_BOUND; least += 982451653L) {
            for (long bound = least + 2; bound > least && bound < MAX_LONG_BOUND; bound += Math.abs(bound * 7919)) {
                long f = ThreadLocalRandom.current().nextLong(least, bound);
                assertTrue(least <= f && f < bound);
                int i = 0;
                long j;
                while (i < NCALLS &&
                       (j = ThreadLocalRandom.current().nextLong(least, bound)) == f) {
                    assertTrue(least <= j && j < bound);
                    ++i;
                }
                assertTrue(i < NCALLS);
            }
        }
    }

    /**
     * nextDouble(least, bound) returns least <= value < bound;
     * repeated calls produce at least two distinct results
     */
    public void testNextDoubleBounded2() {
        for (double least = 0.0001; least < 1.0e20; least *= 8) {
            for (double bound = least * 1.001; bound < 1.0e20; bound *= 16) {
                double f = ThreadLocalRandom.current().nextDouble(least, bound);
                assertTrue(least <= f && f < bound);
                int i = 0;
                double j;
                while (i < NCALLS &&
                       (j = ThreadLocalRandom.current().nextDouble(least, bound)) == f) {
                    assertTrue(least <= j && j < bound);
                    ++i;
                }
                assertTrue(i < NCALLS);
            }
        }
    }

    /**
     * Different threads produce different pseudo-random sequences
     */
    public void testDifferentSequences() {
        // Don't use main thread's ThreadLocalRandom - it is likely to
        // be polluted by previous tests.
        final AtomicReference<ThreadLocalRandom> threadLocalRandom =
            new AtomicReference<ThreadLocalRandom>();
        final AtomicLong rand = new AtomicLong();

        long firstRand = 0;
        ThreadLocalRandom firstThreadLocalRandom = null;

        final CheckedRunnable getRandomState = new CheckedRunnable() {
            public void realRun() {
                ThreadLocalRandom current = ThreadLocalRandom.current();
                assertSame(current, ThreadLocalRandom.current());
                // test bug: the following is not guaranteed and not true in JDK8
                //                assertNotSame(current, threadLocalRandom.get());
                rand.set(current.nextLong());
                threadLocalRandom.set(current);
            }};

        Thread first = newStartedThread(getRandomState);
        awaitTermination(first);
        firstRand = rand.get();
        firstThreadLocalRandom = threadLocalRandom.get();

        for (int i = 0; i < NCALLS; i++) {
            Thread t = newStartedThread(getRandomState);
            awaitTermination(t);
            if (firstRand != rand.get())
                return;
        }
        fail("all threads generate the same pseudo-random sequence");
    }

    /**
     * Invoking sized ints, long, doubles, with negative sizes throws
     * IllegalArgumentException
     */
    public void testBadStreamSize() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        try {
            java.util.stream.IntStream x = r.ints(-1L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.IntStream x = r.ints(-1L, 2, 3);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.LongStream x = r.longs(-1L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.LongStream x = r.longs(-1L, -1L, 1L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.DoubleStream x = r.doubles(-1L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.DoubleStream x = r.doubles(-1L, .5, .6);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * Invoking bounded ints, long, doubles, with illegal bounds throws
     * IllegalArgumentException
     */
    public void testBadStreamBounds() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        try {
            java.util.stream.IntStream x = r.ints(2, 1);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.IntStream x = r.ints(10, 42, 42);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.LongStream x = r.longs(-1L, -1L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.LongStream x = r.longs(10, 1L, -2L);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.DoubleStream x = r.doubles(0.0, 0.0);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
        try {
            java.util.stream.DoubleStream x = r.doubles(10, .5, .4);
            shouldThrow();
        } catch (IllegalArgumentException success) {}
    }

    /**
     * A parallel sized stream of ints generates the given number of values
     */
    public void testIntsCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 0;
        for (int reps = 0; reps < REPS; ++reps) {
            counter.reset();
            r.ints(size).parallel().forEach(x -> {counter.increment();});
            assertEquals(size, counter.sum());
            size += 524959;
        }
    }

    /**
     * A parallel sized stream of longs generates the given number of values
     */
    public void testLongsCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 0;
        for (int reps = 0; reps < REPS; ++reps) {
            counter.reset();
            r.longs(size).parallel().forEach(x -> {counter.increment();});
            assertEquals(size, counter.sum());
            size += 524959;
        }
    }

    /**
     * A parallel sized stream of doubles generates the given number of values
     */
    public void testDoublesCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 0;
        for (int reps = 0; reps < REPS; ++reps) {
            counter.reset();
            r.doubles(size).parallel().forEach(x -> {counter.increment();});
            assertEquals(size, counter.sum());
            size += 524959;
        }
    }

    /**
     * Each of a parallel sized stream of bounded ints is within bounds
     */
    public void testBoundedInts() {
        AtomicInteger fails = new AtomicInteger(0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 12345L;
        for (int least = -15485867; least < MAX_INT_BOUND; least += 524959) {
            for (int bound = least + 2; bound > least && bound < MAX_INT_BOUND; bound += 67867967) {
                final int lo = least, hi = bound;
                r.ints(size, lo, hi).parallel().
                    forEach(x -> {if (x < lo || x >= hi)
                                fails.getAndIncrement(); });
            }
        }
        assertEquals(0, fails.get());
    }

    /**
     * Each of a parallel sized stream of bounded longs is within bounds
     */
    public void testBoundedLongs() {
        AtomicInteger fails = new AtomicInteger(0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 123L;
        for (long least = -86028121; least < MAX_LONG_BOUND; least += 1982451653L) {
            for (long bound = least + 2; bound > least && bound < MAX_LONG_BOUND; bound += Math.abs(bound * 7919)) {
                final long lo = least, hi = bound;
                r.longs(size, lo, hi).parallel().
                    forEach(x -> {if (x < lo || x >= hi)
                                fails.getAndIncrement(); });
            }
        }
        assertEquals(0, fails.get());
    }

    /**
     * Each of a parallel sized stream of bounded doubles is within bounds
     */
    public void testBoundedDoubles() {
        AtomicInteger fails = new AtomicInteger(0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 456;
        for (double least = 0.00011; least < 1.0e20; least *= 9) {
            for (double bound = least * 1.0011; bound < 1.0e20; bound *= 17) {
                final double lo = least, hi = bound;
                r.doubles(size, lo, hi).parallel().
                    forEach(x -> {if (x < lo || x >= hi)
                                fails.getAndIncrement(); });
            }
        }
        assertEquals(0, fails.get());
    }

    /**
     * A parallel unsized stream of ints generates at least 100 values
     */
    public void testUnsizedIntsCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.ints().limit(size).parallel().forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

    /**
     * A parallel unsized stream of longs generates at least 100 values
     */
    public void testUnsizedLongsCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.longs().limit(size).parallel().forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

    /**
     * A parallel unsized stream of doubles generates at least 100 values
     */
    public void testUnsizedDoublesCount() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.doubles().limit(size).parallel().forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

    /**
     * A sequential unsized stream of ints generates at least 100 values
     */
    public void testUnsizedIntsCountSeq() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.ints().limit(size).forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

    /**
     * A sequential unsized stream of longs generates at least 100 values
     */
    public void testUnsizedLongsCountSeq() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.longs().limit(size).forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

    /**
     * A sequential unsized stream of doubles generates at least 100 values
     */
    public void testUnsizedDoublesCountSeq() {
        LongAdder counter = new LongAdder();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long size = 100;
        r.doubles().limit(size).forEach(x -> {counter.increment();});
        assertEquals(size, counter.sum());
    }

}
