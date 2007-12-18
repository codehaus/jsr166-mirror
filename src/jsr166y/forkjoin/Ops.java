/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;

/**
 * Interfaces and utilities describing per-element operations used
 * within parallel methods on aggregates. This class provides type
 * names for common operation signatures accepting zero, one or two
 * arguments, and returning zero or one results, for parameterized
 * types, as well as specializations to <tt>int</tt>, <tt>long</tt>,
 * and <tt>double</tt>. (Lesser used types like <tt>short</tt> are
 * absent.)
 * 
 * <p>In addition to stated signatures, implementations of these
 * interfaces must work safely in parallel. In general, this means
 * methods should operate only on their arguments, and should not rely
 * on ThreadLocals, unsafely published globals, or other unsafe
 * constructions. Additionally, they should not block waiting for
 * synchronization.
 *
 * <p> This class also contains a few commonly used implementations
 * of some of these interfaces
 *
 * <p>This class is normally best used via <tt>import static</tt>.
 */
public class Ops {

    /** 
     * A generator (builder) of objects of type T that takes no
     * arguments.
     */
    public static interface Generator<T> { 
        public T generate();               
    }

    /**
     * An object with a method of one argument that does not return a
     * result.
     */
    public static interface Procedure<T> {
        public void apply(T t);
    }

    /**
     * An object with a function accepting objects of type T and
     * returning those of type U
     */
    public static interface Mapper<T, U> {
        public U map(T t);
    }

    /**
     * An object with a function accepting pairs of objects, one of
     * type T and one of type U, returning those of type V
     */
    public static interface Combiner<T, U, V> { 
        public V combine(T t, U u);
    }

    /**
     * A specialized combiner that is associative and accepts pairs of
     * objects of the same type and returning one of the same
     * type. Like for example, an addition operation, a Reducer must
     * be associative: combine(a, combine(b, c)) should have
     * the same result as combine(combine(a, b), c).
     */
    public static interface Reducer<T> extends Combiner<T, T, T> {
        public T combine(T t, T v); 
    }

    /**
     * An object with boolean method of one argument
     */
    public static interface Predicate<T> {
        public boolean evaluate(T t);
    }

    /**
     * An object with boolean method of two arguments
     */
    public static interface RelationalPredicate<T, U> {
        public boolean evaluate(T t, U u);
    }

    /**
     *  A mapper returning an int
     */
    public static interface MapperToInt<T> {
        public int map(T t);
    }

    /**
     * A mapper returning a double
     */
    public static interface MapperToDouble<T> {
        public double map(T t);
    }

    /**
     * A mapper returning a long
     */
    public static interface MapperToLong<T> {
        public long map(T t);
    }

    /**
     * A mapper accepting an int
     */
    public static interface MapperFromInt<T> {
        public T map(int t);
    }

    /**
     * A mapper accepting a double
     */
    public static interface MapperFromDouble<T> {
        public T map(double t);
    }

    /**
     * A mapper accepting a long argument
     */
    public static interface MapperFromLong<T> {
        public T map(long t);
    }

    /** A generator of doubles */
    public static interface DoubleGenerator { 
        public double generate();               
    }

    /** A procedure accepting a double */
    public static interface DoubleProcedure {
        public void apply(double t);
    }

    /**
     * A mapper accepting a double argument and returning an int
     */
    public static interface MapperFromDoubleToInt {
        public int map(double t);
    }

    /**
     * A mapper accepting a double argument and returning a long
     */
    public static interface MapperFromDoubleToLong {
        public long map(double t);
    }

    /**
     * A mapper accepting a double argument and returning a double
     */
    public static interface MapperFromDoubleToDouble {
        public double map(double t);
    }

    /** A reducer accepting and returning doubles */
    public static interface DoubleReducer {
        public double combine(double u, double v);
    }

    /** A predicate accepting a double argument */
    public static interface DoublePredicate {
        public boolean evaluate(double t);
    }

    /** A relationalPredicate accepting double arguments */
    public static interface DoubleRelationalPredicate {
        public boolean evaluate(double t, double u);
    }

    /** A generator of longs */
    public static interface LongGenerator { 
        public long generate();               
    }

    /** A procedure accepting a long */
    public static interface LongProcedure {
        public void apply(long t);
    }

    /**
     * A mapper accepting a long argument and returning an int
     */
    public static interface MapperFromLongToInt {
        public int map(long t);
    }

    /**
     * A mapper accepting a long argument and returning a double
     */
    public static interface MapperFromLongToDouble {
        public double map(long t);
    }

    /**
     * A mapper accepting a long argument and returning a long
     */
    public static interface MapperFromLongToLong {
        public long map(long t);
    }

    /** A reducer accepting and returning longs */
    public static interface LongReducer {
        public long combine(long u, long v);
    }

    /** A predicate accepting a long argument */
    public static interface LongPredicate {
        public boolean evaluate(long t);
    }

    /** A relationalPredicate accepting long arguments */
    public static interface LongRelationalPredicate {
        public boolean evaluate(long t, long u);
    }

    /** A generator of ints */
    public static interface IntGenerator { 
        public int generate();               
    }

    /** A procedure accepting an int */
    public static interface IntProcedure {
        public void apply(int t);
    }

    /** A map accepting an int and returning an int */
    public static interface MapperFromIntToInt {
        public int map(int u);
    }

    /**
     * A mapper accepting an int argument and returning a long
     */
    public static interface MapperFromIntToLong {
        public long map(int t);
    }

    /**
     * A mapper accepting an int argument and returning a double
     */
    public static interface MapperFromIntToDouble {
        public double map(int t);
    }


    /** A reducer accepting and returning ints */
    public static interface IntReducer {
        public int combine(int u, int v);
    }

    /** A predicate accepting an int */
    public static interface IntPredicate {
        public boolean evaluate(int t);
    }

    /** A relationalPredicate accepting int arguments */
    public static interface IntRelationalPredicate {
        public boolean evaluate(int t, int u);
    }

    /**
     * A reducer that adds two double elements
     */
    public static final class DoubleAdder implements DoubleReducer {
        /** Singleton reducer object */
        public static final DoubleAdder adder = new DoubleAdder();
        public double combine(double a, double b) { return a + b; }
    }

    /**
     * A reducer that adds two double elements
     */
    public static final class LongAdder implements LongReducer {
        /** Singleton reducer object */
        public static final LongAdder adder = new LongAdder();
        public long combine(long a, long b) { return a + b; }
    }
 
    /**
     * A reducer that adds two int elements
     */
    public static final class IntAdder implements IntReducer {
        /** Singleton reducer object */
        public static final IntAdder adder = new IntAdder();
        public int combine(int a, int b) { return a + b; }
    }


    // comparators

    /** 
     * A Comparator for doubles
     */
    public static interface DoubleComparator {
        public int compare(double x, double y);
    }

    /** 
     * A Comparator for longs
     */
    public static interface LongComparator {
        public int compare(long x, long y);
    }

    /** 
     * A Comparator for ints
     */
    public static interface IntComparator {
        public int compare(int x, int y);
    }

    /**
     * A Comparator for Comparable.objects
     */
    static final class NaturalComparator<T extends Comparable<? super T>> 
        implements Comparator<T> {
        /**
         * Creates a NaturalComparator for the given element type
         * @param type the type
         */
        NaturalComparator(Class<T> type) {}

        public int compare(T a, T b) {
            return a.compareTo(b);
        }
    }

    /**
     * A reducer returning the maximum of two Comparable elements,
     * treating null as less than any non-null element.
     */
    public static final class 
        NaturalMaxReducer<T extends Comparable<? super T>>
        implements Reducer<T> {
        /**
         * Creates a NaturalMaxReducer for the given element type
         * @param type the type
         */
        NaturalMaxReducer(Class<T> type) {}
        public T combine(T a, T b) {
            return (a != null &&
                    (b == null || a.compareTo(b) >= 0))? a : b;
        }
    }

    /**
     * A reducer returning the minimum of two Comparable elements,
     * treating null as less than any non-null element.
     */
    public static final class 
        NaturalMinReducer<T extends Comparable<? super T>>
        implements Reducer<T> {
        /**
         * Creates a NaturalMinReducer for the given element type
         * @param type the type
         */
        NaturalMinReducer(Class<T> type) {}
        public T combine(T a, T b) {
            return (a != null &&
                    (b == null || a.compareTo(b) <= 0))? a : b;
        }
    }
    
    /**
     * A reducer returning the maximum of two elements, using the
     * given comparator, and treating null as less than any non-null
     * element.
     */
    public static final class MaxReducer<T> implements Reducer<T> {
        private final Comparator<? super T> comparator;
        public MaxReducer(Comparator<? super T> comparator) {
            this.comparator = comparator;
        }
        public T combine(T a, T b) {
            return (a != null &&
                    (b == null || comparator.compare(a, b) >= 0))? a : b;
        }
    }
    
    /**
     * A reducer returning the minimum of two elements, using the
     * given comparator, and treating null as greater than any non-null
     * element.
     */
    public static final class MinReducer<T> implements Reducer<T> {
        private final Comparator<? super T> comparator;
        public MinReducer(Comparator<? super T> comparator) {
            this.comparator = comparator;
        }
        public T combine(T a, T b) {
            return (a != null &&
                    (b == null || comparator.compare(a, b) <= 0))? a : b;
        }
    }
    
    /**
     * A comparator for doubles relying on natural ordering
     */
    public static final class NaturalDoubleComparator 
        implements DoubleComparator {
        /** Singleton comparator object */
        static final NaturalDoubleComparator comparator = new
            NaturalDoubleComparator();
        public int compare(double a, double b) { 
            return Double.compare(a, b);
        }
    }

    /**
     * A reducer returning the maximum of two double elements, using
     * natural comparator
     */
    public static final class NaturalDoubleMaxReducer 
        implements DoubleReducer {
        /** Singleton reducer object */
        public static final NaturalDoubleMaxReducer max = 
            new NaturalDoubleMaxReducer();
        public double combine(double a, double b) { return Math.max(a, b); }
    }

    /**
     * A reducer returning the minimum of two double elements,
     * using natural comparator
     */
    public static final class NaturalDoubleMinReducer 
        implements DoubleReducer {
        /** Singleton reducer object */
        public static final NaturalDoubleMinReducer min = 
            new NaturalDoubleMinReducer();
        public double combine(double a, double b) { return Math.min(a, b); }
    }

    /**
     * A reducer returning the maximum of two double elements,
     * using the given comparator
     */
    public static final class DoubleMaxReducer implements DoubleReducer {
        final DoubleComparator comparator;
        /**
         * Creates a DoubleMaxReducer using the given comparator
         */
        public DoubleMaxReducer(DoubleComparator comparator) {
            this.comparator = comparator;
        }
        public double combine(double a, double b) { 
            return (comparator.compare(a, b) >= 0)? a : b; 
        }
    }

    /**
     * A reducer returning the minimum of two double elements,
     * using the given comparator
     */
    public static final class DoubleMinReducer implements DoubleReducer {
        final DoubleComparator comparator;
        /**
         * Creates a DoubleMinReducer using the given comparator
         */
        public DoubleMinReducer(DoubleComparator comparator) {
            this.comparator = comparator;
        }
        public double combine(double a, double b) { 
            return (comparator.compare(a, b) <= 0)? a : b; 
        }
    }

    /**
     * A comparator for longs relying on natural ordering
     */
    public static final class NaturalLongComparator 
        implements LongComparator {
        /** Singleton comparator object */
        static final NaturalLongComparator comparator = new
            NaturalLongComparator();
        public int compare(long a, long b) { 
            return a < b? -1 : ((a > b)? 1 : 0);
        }
    }

    /**
     * A reducer returning the maximum of two long elements, using
     * natural comparator
     */
    public static final class NaturalLongMaxReducer 
        implements LongReducer {
        /** Singleton reducer object */
        public static final NaturalLongMaxReducer max = 
            new NaturalLongMaxReducer();
        public long combine(long a, long b) { return a >= b? a : b; }
    }

    /**
     * A reducer returning the minimum of two long elements,
     * using natural comparator
     */
    public static final class NaturalLongMinReducer 
        implements LongReducer {
        /** Singleton reducer object */
        public static final NaturalLongMinReducer min = 
            new NaturalLongMinReducer();
        public long combine(long a, long b) { return a <= b? a : b; }
    }

    /**
     * A reducer returning the maximum of two long elements,
     * using the given comparator
     */
    public static final class LongMaxReducer implements LongReducer {
        final LongComparator comparator;
        /**
         * Creates a LongMaxReducer using the given comparator
         */
        public LongMaxReducer(LongComparator comparator) {
            this.comparator = comparator;
        }
        public long combine(long a, long b) { 
            return (comparator.compare(a, b) >= 0)? a : b; 
        }
    }

    /**
     * A reducer returning the minimum of two long elements,
     * using the given comparator
     */
    public static final class LongMinReducer implements LongReducer {
        final LongComparator comparator;
        /**
         * Creates a LongMinReducer using the given comparator
         */
        public LongMinReducer(LongComparator comparator) {
            this.comparator = comparator;
        }
        public long combine(long a, long b) { 
            return (comparator.compare(a, b) <= 0)? a : b; 
        }
    }

    /**
     * A comparator for ints relying on natural ordering
     */
    public static final class NaturalIntComparator 
        implements IntComparator {
        /** Singleton comparator object */
        static final NaturalIntComparator comparator = new
            NaturalIntComparator();
        public int compare(int a, int b) { 
            return a < b? -1 : ((a > b)? 1 : 0);
        }
    }

    /**
     * A reducer returning the maximum of two int elements, using
     * natural comparator
     */
    public static final class NaturalIntMaxReducer 
        implements IntReducer {
        /** Singleton reducer object */
        public static final NaturalIntMaxReducer max = 
            new NaturalIntMaxReducer();
        public int combine(int a, int b) { return a >= b? a : b; }
    }

    /**
     * A reducer returning the minimum of two int elements,
     * using natural comparator
     */
    public static final class NaturalIntMinReducer 
        implements IntReducer {
        /** Singleton reducer object */
        public static final NaturalIntMinReducer min = 
            new NaturalIntMinReducer();
        public int combine(int a, int b) { return a <= b? a : b; }
    }

    /**
     * A reducer returning the maximum of two int elements,
     * using the given comparator
     */
    public static final class IntMaxReducer implements IntReducer {
        final IntComparator comparator;
        /**
         * Creates a IntMaxReducer using the given comparator
         */
        public IntMaxReducer(IntComparator comparator) {
            this.comparator = comparator;
        }
        public int combine(int a, int b) { 
            return (comparator.compare(a, b) >= 0)? a : b; 
        }
    }

    /**
     * A reducer returning the minimum of two int elements,
     * using the given comparator
     */
    public static final class IntMinReducer implements IntReducer {
        final IntComparator comparator;
        /**
         * Creates a IntMinReducer using the given comparator
         */
        public IntMinReducer(IntComparator comparator) {
            this.comparator = comparator;
        }
        public int combine(int a, int b) { 
            return (comparator.compare(a, b) <= 0)? a : b; 
        }
    }

    /**
     * A composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static final class CompoundMapper<T,U,V> implements Mapper<T,V>{
        final Mapper<? super T, ? extends U> first;
        final Mapper<? super U, ? extends V> second;
        public CompoundMapper
            (Mapper<? super T, ? extends U> first,
             Mapper<? super U, ? extends V> second) {
            this.first = first;
            this.second = second;
        }
        /** Returns <tt>second.map(first.map(t))</tt> */
        public V map(T t) { return second.map(first.map(t)); }
    }
        

}
