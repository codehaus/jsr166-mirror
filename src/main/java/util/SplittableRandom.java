/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Spliterator;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.DoubleConsumer;
import java.util.stream.StreamSupport;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.DoubleStream;


/**
 * A generator of uniform pseudorandom values applicable for use in
 * (among other contexts) isolated parallel computations that may
 * generate subtasks. Class SplittableRandom supports methods for
 * producing pseudorandom nunmbers of type {@code int}, {@code long},
 * and {@code double} with similar usages as for class
 * {@link java.util.Random} but differs in the following ways: <ul>
 *
 * <li>Series of generated values pass the DieHarder suite testing
 * independence and uniformity properties of random number generators.
 * (Most recently validated with <a
 * href="http://www.phy.duke.edu/~rgb/General/dieharder.php"> version
 * 3.31.1</a>.) These tests validate only the methods for certain
 * types and ranges, but similar properties are expected to hold, at
 * least approximately, for others as well.  </li>
 *
 * <li> Method {@link #split} constructs and returns a new
 * SplittableRandom instance that shares no mutable state with the
 * current instance. However, with very high probability, the set of
 * values collectively generated by the two objects has the same
 * statistical properties as if the same quantity of values were
 * generated by a single thread using a single {@code
 * SplittableRandom} object.  </li>
 *
 * <li>Instances of SplittableRandom are <em>not</em> thread-safe.
 * They are designed to be split, not shared, across threads. For
 * example, a {@link java.util.concurrent.ForkJoinTask
 * fork/join-style} computation using random numbers might include a
 * construction of the form {@code new
 * Subtask(aSplittableRandom.split()).fork()}.
 *
 * <li>This class provides additional methods for generating random
 * streams, that employ the above techniques when used in {@code
 * stream.parallel()} mode.</li>
 *
 * </ul>
 *
 * @author  Guy Steele
 * @since   1.8
 */
public class SplittableRandom {

    /*
     * File organization: First the non-public methods that constitute
     * the main algorithm, then the main public methods, followed by
     * some custom spliterator classes needed for stream methods.
     *
     * Credits: Primary algorithm and code by Guy Steele.  Stream
     * support methods by Doug Lea.  Documentation jointly produced
     * with additional help from Brian Goetz.
     */

    /*
     * Implementation Overview.
     *
     * This algorithm was inspired by the "DotMix" algorithm by
     * Leiserson, Schardl, and Sukha "Deterministic Parallel
     * Random-Number Generation for Dynamic-Multithreading Platforms",
     * PPoPP 2012, but improves and extends it in several ways.
     *
     * The primary update step is simply to add a constant ("gamma")
     * to the current seed, modulo a prime ("George"). However, the
     * nextLong and nextInt methods do not return this value, but
     * instead the results of bit-mixing transformations that produce
     * more uniformly distributed sequences.
     *
     * "George" is the otherwise nameless (because it cannot be
     * represented) prime number 2^64+13. Using a prime number larger
     * than can fit in a long ensures that all possible long values
     * can occur, plus 13 others that just get skipped over when they
     * are encountered; see method addGammaModGeorge. For this to
     * work, initial gamma values must be at least 13.
     *
     * The value of gamma differs for each instance across a series of
     * splits, and is generated using a slightly stripped-down variant
     * of the same algorithm, but operating across calls to split(),
     * not calls to nextLong(): Each instance carries the state of
     * this generator as nextSplit, and uses mix64(nextSplit) as its
     * own gamma value. Computations of gammas themselves use a fixed
     * constant as the second argument to the addGammaModGeorge
     * function, GAMMA_GAMMA, a "genuinely random" number from a
     * radioactive decay reading (obtained from
     * http://www.fourmilab.ch/hotbits/) meeting the above range
     * constraint. Using a fixed constant maintains the invariant that
     * the value of gamma is the same for every instance that is at
     * the same split-distance from their common root. (Note: there is
     * nothing especially magic about obtaining this constant from a
     * "truly random" physical source rather than just choosing one
     * arbitrarily; using "hotbits" was merely an aesthetically pleasing
     * choice.  In either case, good statistical behavior of the
     * algorithm should be, and was, verified by using the DieHarder
     * test suite.)
     *
     * The mix64 bit-mixing function called by nextLong and other
     * methods computes the same value as the "64-bit finalizer"
     * function in Austin Appleby's MurmurHash3 algorithm.  See
     * http://code.google.com/p/smhasher/wiki/MurmurHash3 , which
     * comments: "The constants for the finalizers were generated by a
     * simple simulated-annealing algorithm, and both avalanche all
     * bits of 'h' to within 0.25% bias." It also appears to work to
     * use instead any of the variants proposed by David Stafford at
     * http://zimbry.blogspot.com/2011/09/better-bit-mixing-improving-on.html
     * but these variants have not yet been tested as thoroughly
     * in the context of the implementation of SplittableRandom.
     *
     * The mix32 function used for nextInt just consists of two of the
     * five lines of mix64; avalanche testing shows that the 64-bit result
     * has its top 32 bits avalanched well, though not the bottom 32 bits.
     * DieHarder tests show that it is adequate for generating one
     * random int from the 64-bit result of nextSeed.
     *
     * Support for the default (no-argument) constructor relies on an
     * AtomicLong (defaultSeedGenerator) to help perform the
     * equivalent of a split of a statically constructed
     * SplittableRandom. Unlike other cases, this split must be
     * performed in a thread-safe manner. We use
     * AtomicLong.compareAndSet as the (typically) most efficient
     * mechanism. To bootstrap, we start off using System.nanotime(),
     * and update using another "genuinely random" constant
     * DEFAULT_SEED_GAMMA. The default constructor uses GAMMA_GAMMA,
     * not 0, for its splitSeed argument (addGammaModGeorge(0,
     * GAMMA_GAMMA) == GAMMA_GAMMA) to reflect that each is split from
     * this root generator, even though the root is not explicitly
     * represented as a SplittableRandom.
     */

    /**
     * The "genuinely random" value for producing new gamma values.
     * The value is arbitrary, subject to the requirement that it be
     * greater or equal to 13.
     */
    private static final long GAMMA_GAMMA = 0xF2281E2DBA6606F3L;

    /**
     * The "genuinely random" seed update value for default constructors.
     * The value is arbitrary, subject to the requirement that it be
     * greater or equal to 13.
     */
    private static final long DEFAULT_SEED_GAMMA = 0xBD24B73A95FB84D9L;

    /**
     * The next seed for default constructors.
     */
    private static final AtomicLong defaultSeedGenerator =
        new AtomicLong(System.nanoTime());

    /**
     * The seed, updated only via method nextSeed.
     */
    private long seed;

    /**
     * The constant value added to seed (mod George) on each update.
     */
    private final long gamma;

    /**
     * The next seed to use for splits. Propagated using
     * addGammaModGeorge across instances.
     */
    private final long nextSplit;

    /**
     * Internal constructor used by all other constructors and by
     * method split. Establishes the initial seed for this instance,
     * and uses the given splitSeed to establish gamma, as well as the
     * nextSplit to use by this instance.
     */
    private SplittableRandom(long seed, long splitSeed) {
        this.seed = seed;
        long s = splitSeed, g;
        do { // ensure gamma >= 13, considered as an unsigned integer
            s = addGammaModGeorge(s, GAMMA_GAMMA);
            g = mix64(s);
        } while (Long.compareUnsigned(g, 13L) < 0);
        this.gamma = g;
        this.nextSplit = s;
    }

    /**
     * Adds the given gamma value, g, to the given seed value s, mod
     * George (2^64+13). We regard s and g as unsigned values
     * (ranging from 0 to 2^64-1). We add g to s either once or twice
     * (mod George) as necessary to produce an (unsigned) result less
     * than 2^64.  We require that g must be at least 13. This
     * guarantees that if (s+g) mod George >= 2^64 then (s+g+g) mod
     * George < 2^64; thus we need only a conditional, not a loop,
     * to be sure of getting a representable value.
     *
     * @param s a seed value
     * @param g a gamma value, 13 <= g (as unsigned)
     */
    private static long addGammaModGeorge(long s, long g) {
        long p = s + g;
        if (Long.compareUnsigned(p, g) >= 0)
            return p;
        long q = p - 13L;
        return (Long.compareUnsigned(p, 13L) >= 0) ? q : (q + g);
    }

    /**
     * Updates in-place and returns seed.
     * See above for explanation.
     */
    private long nextSeed() {
        return seed = addGammaModGeorge(seed, gamma);
    }

    /**
     * Returns a bit-mixed transformation of its argument.
     * See above for explanation.
     */
    private static long mix64(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }

    /**
     * Returns a bit-mixed int transformation of its argument.
     * See above for explanation.
     */
    private static int mix32(long z) {
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        return (int)(z >>> 32);
    }

    /**
     * Atomically updates and returns next seed for default constructor
     */
    private static long nextDefaultSeed() {
        long oldSeed, newSeed;
        do {
            oldSeed = defaultSeedGenerator.get();
            newSeed = addGammaModGeorge(oldSeed, DEFAULT_SEED_GAMMA);
        } while (!defaultSeedGenerator.compareAndSet(oldSeed, newSeed));
        return mix64(newSeed);
    }

    /*
     * Internal versions of nextX methods used by streams, as well as
     * the public nextX(origin, bound) methods.  These exist mainly to
     * avoid the need for multiple versions of stream spliterators
     * across the different exported forms of streams.
     */

    /**
     * The form of nextLong used by LongStream Spliterators.  If
     * origin is greater than bound, acts as unbounded form of
     * nextLong, else as bounded form.
     *
     * @param origin the least value, unless greater than bound
     * @param bound the upper bound (exclusive), must not equal origin
     * @return a pseudorandom value
     */
    final long internalNextLong(long origin, long bound) {
        /*
         * Four Cases:
         *
         * 1. If the arguments indicate unbounded form, act as
         * nextLong().
         *
         * 2. If the range is an exact power of two, apply the
         * associated bit mask.
         *
         * 3. If the range is positive, loop to avoid potential bias
         * when the implicit nextLong() bound (2<sup>64</sup>) is not
         * evenly divisible by the range. The loop rejects candidates
         * computed from otherwise over-represented values.  The
         * expected number of iterations under an ideal generator
         * varies from 1 to 2, depending on the bound.
         *
         * 4. Otherwise, the range cannot be represented as a positive
         * long.  Repeatedly generate unbounded longs until obtaining
         * a candidate meeting constraints (with an expected number of
         * iterations of less than two).
         */

        long r = mix64(nextSeed());
        if (origin < bound) {
            long n = bound - origin, m = n - 1;
            if ((n & m) == 0L) // power of two
                r = (r & m) + origin;
            else if (n > 0) { // reject over-represented candidates
                for (long u = r >>> 1;            // ensure nonnegative
                     u + m - (r = u % n) < 0L;    // reject
                     u = mix64(nextSeed()) >>> 1) // retry
                    ;
                r += origin;
            }
            else {             // range not representable as long
                while (r < origin || r >= bound)
                    r = mix64(nextSeed());
            }
        }
        return r;
    }

    /**
     * The form of nextInt used by IntStream Spliterators.
     * Exactly the same as long version, except for types.
     *
     * @param origin the least value, unless greater than bound
     * @param bound the upper bound (exclusive), must not equal origin
     * @return a pseudorandom value
     */
    final int internalNextInt(int origin, int bound) {
        int r = mix32(nextSeed());
        if (origin < bound) {
            int n = bound - origin, m = n - 1;
            if ((n & m) == 0L)
                r = (r & m) + origin;
            else if (n > 0) {
                for (int u = r >>> 1;
                     u + m - (r = u % n) < 0L;
                     u = mix32(nextSeed()) >>> 1)
                    ;
                r += origin;
            }
            else {
                while (r < origin || r >= bound)
                    r = mix32(nextSeed());
            }
        }
        return r;
    }

    /**
     * The form of nextDouble used by DoubleStream Spliterators.
     *
     * @param origin the least value, unless greater than bound
     * @param bound the upper bound (exclusive), must not equal origin
     * @return a pseudorandom value
     */
    final double internalNextDouble(double origin, double bound) {
        long bits = (1023L << 52) | (nextLong() >>> 12);
        double r = Double.longBitsToDouble(bits) - 1.0;
        if (origin < bound) {
            r = r * (bound - origin) + origin;
            if (r == bound) // correct for rounding
                r = Double.longBitsToDouble(Double.doubleToLongBits(bound) - 1);
        }
        return r;
    }

    /* ---------------- public methods ---------------- */

    /**
     * Creates a new SplittableRandom instance using the given initial
     * seed. Two SplittableRandom instances created with the same seed
     * generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public SplittableRandom(long seed) {
        this(seed, 0);
    }

    /**
     * Creates a new SplittableRandom instance that is likely to
     * generate sequences of values that are statistically independent
     * of those of any other instances in the current program; and
     * may, and typically does, vary across program invocations.
     */
    public SplittableRandom() {
        this(nextDefaultSeed(), GAMMA_GAMMA);
    }

    /**
     * Constructs and returns a new SplittableRandom instance that
     * shares no mutable state with this instance. However, with very
     * high probability, the set of values collectively generated by
     * the two objects has the same statistical properties as if the
     * same quantity of values were generated by a single thread using
     * a single SplittableRandom object.  Either or both of the two
     * objects may be further split using the {@code split()} method,
     * and the same expected statistical properties apply to the
     * entire set of generators constructed by such recursive
     * splitting.
     *
     * @return the new SplittableRandom instance
     */
    public SplittableRandom split() {
        return new SplittableRandom(nextSeed(), nextSplit);
    }

    /**
     * Returns a pseudorandom {@code int} value.
     *
     * @return a pseudorandom value
     */
    public int nextInt() {
        return mix32(nextSeed());
    }

    /**
     * Returns a pseudorandom {@code int} value between 0 (inclusive)
     * and the specified bound (exclusive).
     *
     * @param bound the bound on the random number to be returned.  Must be
     *        positive.
     * @return a pseudorandom {@code int} value between {@code 0}
     *         (inclusive) and the bound (exclusive).
     * @exception IllegalArgumentException if the bound is not positive
     */
    public int nextInt(int bound) {
        if (bound <= 0)
            throw new IllegalArgumentException("bound must be positive");
        // Specialize internalNextInt for origin 0
        int r = mix32(nextSeed());
        int m = bound - 1;
        if ((bound & m) == 0L) // power of two
            r &= m;
        else { // reject over-represented candidates
            for (int u = r >>> 1;
                 u + m - (r = u % bound) < 0L;
                 u = mix32(nextSeed()) >>> 1)
                ;
        }
        return r;
    }

    /**
     * Returns a pseudorandom {@code int} value between the specified
     * origin (inclusive) and the specified bound (exclusive).
     *
     * @param origin the least value returned
     * @param bound the upper bound (exclusive)
     * @return a pseudorandom {@code int} value between the origin
     *         (inclusive) and the bound (exclusive).
     * @exception IllegalArgumentException if {@code origin} is greater than
     *         or equal to {@code bound}
     */
    public int nextInt(int origin, int bound) {
        if (origin >= bound)
            throw new IllegalArgumentException("bound must be greater than origin");
        return internalNextInt(origin, bound);
    }

    /**
     * Returns a pseudorandom {@code long} value.
     *
     * @return a pseudorandom value
     */
    public long nextLong() {
        return mix64(nextSeed());
    }

    /**
     * Returns a pseudorandom {@code long} value between 0 (inclusive)
     * and the specified bound (exclusive).
     *
     * @param bound the bound on the random number to be returned.  Must be
     *        positive.
     * @return a pseudorandom {@code long} value between {@code 0}
     *         (inclusive) and the bound (exclusive).
     * @exception IllegalArgumentException if the bound is not positive
     */
    public long nextLong(long bound) {
        if (bound <= 0)
            throw new IllegalArgumentException("bound must be positive");
        // Specialize internalNextLong for origin 0
        long r = mix64(nextSeed());
        long m = bound - 1;
        if ((bound & m) == 0L) // power of two
            r &= m;
        else { // reject over-represented candidates
            for (long u = r >>> 1;
                 u + m - (r = u % bound) < 0L;
                 u = mix64(nextSeed()) >>> 1)
                ;
        }
        return r;
    }

    /**
     * Returns a pseudorandom {@code long} value between the specified
     * origin (inclusive) and the specified bound (exclusive).
     *
     * @param origin the least value returned
     * @param bound the upper bound (exclusive)
     * @return a pseudorandom {@code long} value between the origin
     *         (inclusive) and the bound (exclusive).
     * @exception IllegalArgumentException if {@code origin} is greater than
     *         or equal to {@code bound}
     */
    public long nextLong(long origin, long bound) {
        if (origin >= bound)
            throw new IllegalArgumentException("bound must be greater than origin");
        return internalNextLong(origin, bound);
    }

    /**
     * Returns a pseudorandom {@code double} value between {@code 0.0}
     * (inclusive) and {@code 1.0} (exclusive).
     *
     * @return a pseudorandom value between {@code 0.0}
     * (inclusive) and {@code 1.0} (exclusive)
     */
    public double nextDouble() {
        long bits = (1023L << 52) | (nextLong() >>> 12);
        return Double.longBitsToDouble(bits) - 1.0;
    }

    /**
     * Returns a pseudorandom {@code double} value between 0.0
     * (inclusive) and the specified bound (exclusive).
     *
     * @param bound the bound on the random number to be returned.  Must be
     *        positive.
     * @return a pseudorandom {@code double} value between {@code 0.0}
     *         (inclusive) and the bound (exclusive).
     * @throws IllegalArgumentException if {@code bound} is not positive
     */
    public double nextDouble(double bound) {
        if (bound <= 0.0)
            throw new IllegalArgumentException("bound must be positive");
        double result = nextDouble() * bound;
        return (result < bound) ?  result : // correct for rounding
            Double.longBitsToDouble(Double.doubleToLongBits(bound) - 1);
    }

    /**
     * Returns a pseudorandom {@code double} value between the given
     * origin (inclusive) and bound (exclusive).
     *
     * @param origin the least value returned
     * @param bound the upper bound
     * @return a pseudorandom {@code double} value between the origin
     *         (inclusive) and the bound (exclusive).
     * @throws IllegalArgumentException if {@code origin} is greater than
     *         or equal to {@code bound}
     */
    public double nextDouble(double origin, double bound) {
        if (origin >= bound)
            throw new IllegalArgumentException("bound must be greater than origin");
        return internalNextDouble(origin, bound);
    }

    // stream methods, coded in a way intended to better isolate for
    // maintenance purposes the small differences across forms.

    /**
     * Returns a stream with the given {@code streamSize} number of
     * pseudorandom {@code int} values.
     *
     * @param streamSize the number of values to generate
     * @return a stream of pseudorandom {@code int} values
     * @throws IllegalArgumentException if {@code streamSize} is
     * less than zero
     */
    public IntStream ints(long streamSize) {
        if (streamSize < 0L)
            throw new IllegalArgumentException("negative Stream size");
        return StreamSupport.intStream
            (new RandomIntsSpliterator
             (this, 0L, streamSize, Integer.MAX_VALUE, 0),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code int}
     * values
     *
     * @implNote This method is implemented to be equivalent to {@code
     * ints(Long.MAX_VALUE)}.
     *
     * @return a stream of pseudorandom {@code int} values
     */
    public IntStream ints() {
        return StreamSupport.intStream
            (new RandomIntsSpliterator
             (this, 0L, Long.MAX_VALUE, Integer.MAX_VALUE, 0),
             false);
    }

    /**
     * Returns a stream with the given {@code streamSize} number of
     * pseudorandom {@code int} values, each conforming to the given
     * origin and bound.
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the origin of each random value
     * @param randomNumberBound the bound of each random value
     * @return a stream of pseudorandom {@code int} values,
     * each with the given origin and bound.
     * @throws IllegalArgumentException if {@code streamSize} is
     * less than zero.
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public IntStream ints(long streamSize, int randomNumberOrigin,
                          int randomNumberBound) {
        if (streamSize < 0L)
            throw new IllegalArgumentException("negative Stream size");
        if (randomNumberOrigin >= randomNumberBound)
            throw new IllegalArgumentException("bound must be greater than origin");
        return StreamSupport.intStream
            (new RandomIntsSpliterator
             (this, 0L, streamSize, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code
     * int} values, each conforming to the given origin and bound.
     *
     * @implNote This method is implemented to be equivalent to {@code
     * ints(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     *
     * @param randomNumberOrigin the origin of each random value
     * @param randomNumberBound the bound of each random value
     * @return a stream of pseudorandom {@code int} values,
     * each with the given origin and bound.
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
        if (randomNumberOrigin >= randomNumberBound)
            throw new IllegalArgumentException("bound must be greater than origin");
        return StreamSupport.intStream
            (new RandomIntsSpliterator
             (this, 0L, Long.MAX_VALUE, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Returns a stream with the given {@code streamSize} number of
     * pseudorandom {@code long} values.
     *
     * @param streamSize the number of values to generate
     * @return a stream of {@code long} values
     * @throws IllegalArgumentException if {@code streamSize} is
     * less than zero
     */
    public LongStream longs(long streamSize) {
        if (streamSize < 0L)
            throw new IllegalArgumentException("negative Stream size");
        return StreamSupport.longStream
            (new RandomLongsSpliterator
             (this, 0L, streamSize, Long.MAX_VALUE, 0L),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code long}
     * values.
     *
     * @implNote This method is implemented to be equivalent to {@code
     * longs(Long.MAX_VALUE)}.
     *
     * @return a stream of pseudorandom {@code long} values
     */
    public LongStream longs() {
        return StreamSupport.longStream
            (new RandomLongsSpliterator
             (this, 0L, Long.MAX_VALUE, Long.MAX_VALUE, 0L),
             false);
    }

    /**
     * Returns a stream with the given {@code streamSize} number of
     * pseudorandom {@code long} values, each conforming to the
     * given origin and bound.
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the origin of each random value
     * @param randomNumberBound the bound of each random value
     * @return a stream of pseudorandom {@code long} values,
     * each with the given origin and bound.
     * @throws IllegalArgumentException if {@code streamSize} is
     * less than zero.
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public LongStream longs(long streamSize, long randomNumberOrigin,
                            long randomNumberBound) {
        if (streamSize < 0L)
            throw new IllegalArgumentException("negative Stream size");
        if (randomNumberOrigin >= randomNumberBound)
            throw new IllegalArgumentException("bound must be greater than origin");
        return StreamSupport.longStream
            (new RandomLongsSpliterator
             (this, 0L, streamSize, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code
     * long} values, each conforming to the given origin and bound.
     *
     * @implNote This method is implemented to be equivalent to {@code
     * longs(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     *
     * @param randomNumberOrigin the origin of each random value
     * @param randomNumberBound the bound of each random value
     * @return a stream of pseudorandom {@code long} values,
     * each with the given origin and bound.
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
        if (randomNumberOrigin >= randomNumberBound)
            throw new IllegalArgumentException("bound must be greater than origin");
        return StreamSupport.longStream
            (new RandomLongsSpliterator
             (this, 0L, Long.MAX_VALUE, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Returns a stream with the given {@code streamSize} number of
     * pseudorandom {@code double} values.
     *
     * @param streamSize the number of values to generate
     * @return a stream of {@code double} values
     * @throws IllegalArgumentException if {@code streamSize} is
     * less than zero
     */
    public DoubleStream doubles(long streamSize) {
        if (streamSize < 0L)
            throw new IllegalArgumentException("negative Stream size");
        return StreamSupport.doubleStream
            (new RandomDoublesSpliterator
             (this, 0L, streamSize, Double.MAX_VALUE, 0.0),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code
     * double} values.
     *
     * @implNote This method is implemented to be equivalent to {@code
     * doubles(Long.MAX_VALUE)}.
     *
     * @return a stream of pseudorandom {@code double} values
     */
    public DoubleStream doubles() {
        return StreamSupport.doubleStream
            (new RandomDoublesSpliterator
             (this, 0L, Long.MAX_VALUE, Double.MAX_VALUE, 0.0),
             false);
    }

    /**
     * Returns a stream with the given {@code streamSize} number of
     * pseudorandom {@code double} values, each conforming to the
     * given origin and bound.
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the origin of each random value
     * @param randomNumberBound the bound of each random value
     * @return a stream of pseudorandom {@code double} values,
     * each with the given origin and bound.
     * @throws IllegalArgumentException if {@code streamSize} is
     * less than zero.
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public DoubleStream doubles(long streamSize, double randomNumberOrigin,
                                double randomNumberBound) {
        if (streamSize < 0L)
            throw new IllegalArgumentException("negative Stream size");
        if (randomNumberOrigin >= randomNumberBound)
            throw new IllegalArgumentException("bound must be greater than origin");
        return StreamSupport.doubleStream
            (new RandomDoublesSpliterator
             (this, 0L, streamSize, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code
     * double} values, each conforming to the given origin and bound.
     *
     * @implNote This method is implemented to be equivalent to {@code
     * doubles(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     *
     * @param randomNumberOrigin the origin of each random value
     * @param randomNumberBound the bound of each random value
     * @return a stream of pseudorandom {@code double} values,
     * each with the given origin and bound.
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
        if (randomNumberOrigin >= randomNumberBound)
            throw new IllegalArgumentException("bound must be greater than origin");
        return StreamSupport.doubleStream
            (new RandomDoublesSpliterator
             (this, 0L, Long.MAX_VALUE, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Spliterator for int streams.  We multiplex the four int
     * versions into one class by treating and bound < origin as
     * unbounded, and also by treating "infinite" as equivalent to
     * Long.MAX_VALUE. For splits, it uses the standard divide-by-two
     * approach. The long and double versions of this class are
     * identical except for types.
     */
    static class RandomIntsSpliterator implements Spliterator.OfInt {
        final SplittableRandom rng;
        long index;
        final long fence;
        final int origin;
        final int bound;
        RandomIntsSpliterator(SplittableRandom rng, long index, long fence,
                              int origin, int bound) {
            this.rng = rng; this.index = index; this.fence = fence;
            this.origin = origin; this.bound = bound;
        }

        public RandomIntsSpliterator trySplit() {
            long i = index, m = (i + fence) >>> 1;
            return (m <= i) ? null :
                new RandomIntsSpliterator(rng.split(), i, index = m, origin, bound);
        }

        public long estimateSize() {
            return fence - index;
        }

        public int characteristics() {
            return (Spliterator.SIZED | Spliterator.SUBSIZED |
                    Spliterator.ORDERED | Spliterator.NONNULL |
                    Spliterator.IMMUTABLE);
        }

        public boolean tryAdvance(IntConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                consumer.accept(rng.internalNextInt(origin, bound));
                index = i + 1;
                return true;
            }
            return false;
        }

        public void forEachRemaining(IntConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                index = f;
                int o = origin, b = bound;
                do {
                    consumer.accept(rng.internalNextInt(o, b));
                } while (++i < f);
            }
        }
    }

    /**
     * Spliterator for long streams.
     */
    static class RandomLongsSpliterator implements Spliterator.OfLong {
        final SplittableRandom rng;
        long index;
        final long fence;
        final long origin;
        final long bound;
        RandomLongsSpliterator(SplittableRandom rng, long index, long fence,
                               long origin, long bound) {
            this.rng = rng; this.index = index; this.fence = fence;
            this.origin = origin; this.bound = bound;
        }

        public RandomLongsSpliterator trySplit() {
            long i = index, m = (i + fence) >>> 1;
            return (m <= i) ? null :
                new RandomLongsSpliterator(rng.split(), i, index = m, origin, bound);
        }

        public long estimateSize() {
            return fence - index;
        }

        public int characteristics() {
            return (Spliterator.SIZED | Spliterator.SUBSIZED |
                    Spliterator.ORDERED | Spliterator.NONNULL |
                    Spliterator.IMMUTABLE);
        }

        public boolean tryAdvance(LongConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                consumer.accept(rng.internalNextLong(origin, bound));
                index = i + 1;
                return true;
            }
            return false;
        }

        public void forEachRemaining(LongConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                index = f;
                long o = origin, b = bound;
                do {
                    consumer.accept(rng.internalNextLong(o, b));
                } while (++i < f);
            }
        }

    }

    /**
     * Spliterator for double streams.
     */
    static class RandomDoublesSpliterator implements Spliterator.OfDouble {
        final SplittableRandom rng;
        long index;
        final long fence;
        final double origin;
        final double bound;
        RandomDoublesSpliterator(SplittableRandom rng, long index, long fence,
                                 double origin, double bound) {
            this.rng = rng; this.index = index; this.fence = fence;
            this.origin = origin; this.bound = bound;
        }

        public RandomDoublesSpliterator trySplit() {
            long i = index, m = (i + fence) >>> 1;
            return (m <= i) ? null :
                new RandomDoublesSpliterator(rng.split(), i, index = m, origin, bound);
        }

        public long estimateSize() {
            return fence - index;
        }

        public int characteristics() {
            return (Spliterator.SIZED | Spliterator.SUBSIZED |
                    Spliterator.ORDERED | Spliterator.NONNULL |
                    Spliterator.IMMUTABLE);
        }

        public boolean tryAdvance(DoubleConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                consumer.accept(rng.internalNextDouble(origin, bound));
                index = i + 1;
                return true;
            }
            return false;
        }

        public void forEachRemaining(DoubleConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                index = f;
                double o = origin, b = bound;
                do {
                    consumer.accept(rng.internalNextDouble(o, b));
                } while (++i < f);
            }
        }
    }

}

