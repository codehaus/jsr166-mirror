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
        public U map(T u);
    }

    /**
     * A specialized Mapper that produces results of the same type as
     * its argument.
     */
    public static interface Transformer<T> extends Mapper<T, T> {
        public T map(T u);
    }

    /**
     * On object with a function accepting pairs of objects, one of
     * type T and one of type U, returning those of type V
     */
    public static interface Combiner<T, U, V> { 
        public V combine(T t, U u);
    }

    /**
     * A specialized Combiner that is associative and accepts pairs of
     * objects of the same type and returning one of the same
     * type. Like for example, an addition operation, a Reducer must
     * be (left) associative: combine(a, combine(b, c)) should have
     * the same result as combine(conbine(a, b), c).
     */
    public static interface Reducer<T> extends Combiner<T, T, T> {
        public T combine(T u, T v); 
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
     *  A Mapper returning an int
     */
    public static interface MapperToInt<T> {
        public int map(T t);
    }

    /**
     * A Mapper returning a double
     */
    public static interface MapperToDouble<T> {
        public double map(T t);
    }

    /**
     * A Mapper returning a long
     */
    public static interface MapperToLong<T> {
        public long map(T t);
    }

    /**
     * A Mapper accepting an int
     */
    public static interface MapperFromInt<T> {
        public T map(int t);
    }

    /**
     * A Mapper accepting a double
     */
    public static interface MapperFromDouble<T> {
        public T map(double t);
    }

    /**
     * A Mapper accepting a long argument
     */
    public static interface MapperFromLong<T> {
        public T map(long t);
    }

    /** A Generator of doubles */
    public static interface DoubleGenerator { 
        public double generate();               
    }

    /** A Procedure accepting a double */
    public static interface DoubleProcedure {
        public void apply(double t);
    }

    /** A Transformer accepting and returing doubles */
    public static interface DoubleTransformer {
        public double map(double u);
    }

    /** A Reducer accepting and returning doubles */
    public static interface DoubleReducer {
        public double combine(double u, double v);
    }

    /** A Predicate accepting a double argument */
    public static interface DoublePredicate {
        public boolean evaluate(double t);
    }

    /** A RelationalPredicate accepting double arguments */
    public static interface DoubleRelationalPredicate {
        public boolean evaluate(double t, double u);
    }

    /** A Generator of longs */
    public static interface LongGenerator { 
        public long generate();               
    }

    /** A Procedure accepting a long */
    public static interface LongProcedure {
        public void apply(long t);
    }

    /** A Transformer accepting and returning longs */
    public static interface LongTransformer {
        public long map(long u);
    }

    /** A Reducer accepting and returning longs */
    public static interface LongReducer {
        public long combine(long u, long v);
    }

    /** A Predicate accepting a long argument */
    public static interface LongPredicate {
        public boolean evaluate(long t);
    }

    /** A RelationalPredicate accepting long arguments */
    public static interface LongRelationalPredicate {
        public boolean evaluate(long t, long u);
    }

    /** A Generator of ints */
    public static interface IntGenerator { 
        public int generate();               
    }

    /** A Procedure accepting an int */
    public static interface IntProcedure {
        public void apply(int t);
    }

    /** A Transformer accepting and returning ints */
    public static interface IntTransformer {
        public int map(int u);
    }

    /** A Reducer accepting and returning ints */
    public static interface IntReducer {
        public int combine(int u, int v);
    }

    /** A Predicate accepting an int */
    public static interface IntPredicate {
        public boolean evaluate(int t);
    }

    /** A RelationalPredicate accepting int arguments */
    public static interface IntRelationalPredicate {
        public boolean evaluate(int t, int u);
    }

    /**
     * A Mapper accepting a double argument and returning an int
     */
    public static interface MapperFromDoubleToInt {
        public int map(double t);
    }

    /**
     * A Mapper accepting a long argument and returning an int
     */
    public static interface MapperFromLongToInt {
        public int map(long t);
    }

    /**
     * A Mapper accepting an int argument and returning a long
     */
    public static interface MapperFromIntToLong {
        public long map(int t);
    }

    /**
     * A Mapper accepting an int argument and returning a double
     */
    public static interface MapperFromIntToDouble {
        public double map(int t);
    }

    /**
     * A Mapper accepting a long argument and returning a double
     */
    public static interface MapperFromLongToDouble {
        public double map(long t);
    }

    /**
     * A Mapper accepting a double argument and returning a long
     */
    public static interface MapperFromDoubleToLong {
        public long map(double t);
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
     * A reducer returning the maximum of two Comparable elements,
     * treating null as less than any non-null element.
     */
    public static final class 
        ComparableMaxReducer<T extends Comparable<? super T>>
        implements Reducer<T> {
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
        ComparableMinReducer<T extends Comparable<? super T>>
        implements Reducer<T> {
        public T combine(T a, T b) {
            return (a != null &&
                    (b == null || a.compareTo(b) <= 0))? a : b;
        }
    }
    
    /**
     * A reducer returning the maximum of two double elements,
     */
    public static final class DoubleMaxReducer implements DoubleReducer {
        /** Singleton reducer object */
        public static final DoubleMaxReducer max = new DoubleMaxReducer();
        public double combine(double a, double b) { return a >= b? a : b; }
    }

    /**
     * A reducer returning the minimum of two double elements,
     */
    public static final class DoubleMinReducer implements DoubleReducer {
        /** Singleton reducer object */
        public static final DoubleMinReducer min = new DoubleMinReducer();
        public double combine(double a, double b) { return a <= b? a : b; }
    }

    /**
     * A reducer returning the maximum of two long elements,
     */
    public static final class LongMaxReducer implements LongReducer {
        /** Singleton reducer object */
        public static final LongMaxReducer max = new LongMaxReducer();
        public long combine(long a, long b) { return a >= b? a : b; }
    }

    /**
     * A reducer returning the minimum of two long elements,
     */
    public static final class LongMinReducer implements LongReducer {
        /** Singleton reducer object */
        public static final LongMinReducer min = new LongMinReducer();
        public long combine(long a, long b) { return a <= b? a : b; }
    }

    /**
     * A reducer returning the maximum of two int elements,
     */
    public static final class IntMaxReducer implements IntReducer {
        /** Singleton reducer object */
        public static final IntMaxReducer max = new IntMaxReducer();
        public int combine(int a, int b) { return a >= b? a : b; }
    }

    /**
     * A reducer returning the minimum of two int elements,
     */
    public static final class IntMinReducer implements IntReducer {
        /** Singleton reducer object */
        public static final IntMinReducer min = new IntMinReducer();
        public int combine(int a, int b) { return a <= b? a : b; }
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


}
