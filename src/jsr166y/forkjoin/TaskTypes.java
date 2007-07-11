/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;

/**
 * Interface type names for common methods forms used in parallel
 * operations. This class provides type names for common operation
 * signatures; those accepting zero, one or two arguments, and
 * returning zero or one results, for parameterized types, as well as
 * specializations to <tt>int</tt>, <tt>long</tt>, and
 * <tt>double</tt>. (Lesser used types like <tt>short</tt> are
 * absent.)
 * 
 * <p>This class is a stand-in for functionality that may be supported
 * in some other way in Java 7. For now, you can <tt>import
 * static</tt> this class.
 */
public class TaskTypes {

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

}
