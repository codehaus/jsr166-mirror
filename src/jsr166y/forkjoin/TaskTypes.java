/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;

/**
 * Interface type names for common methods forms used in parallel
 * operations. It includes type names for common operation signatures;
 * those accepting zero, one or two arguments, and returning zero or
 * one results, for parameterized types, as well as specializations to
 * <tt>int</tt>, <tt>long</tt>, and <tt>double</tt>. (Lesser used
 * types like <tt>short</tt> are absent.)
 * 
 * <p>This class is a stand-in for functionality that will probably be
 * supported in some other way in Java 7. For now, you can <tt>import
 * static</tt> this class.
 */
public class TaskTypes {

    /** 
     * Interface for a generator (builder) of objects of type T that
     * takes no arguments.
     */
    public static interface Generator<T> { 
        public T generate();               
    }

    /**
     * Interface for a method of one argument that does not return a
     * result.
     */
    public static interface Procedure<T> {
        public void apply(T t);
    }

    /**
     * Interface for a function accepting objects of type T and
     * returning those of type U
     */
    public static interface Mapper<T, U> {
        public U map(T u);
    }

    /**
     * A specialization of a Mapper that produces results of the same
     * type as its argument.
     */
    public static interface Transformer<T> extends Mapper<T, T> {
        public T map(T u);
    }

    /**
     * Interface for a function accepting pairs of objects, one of
     * type T and one of type U, returning those of type V
     */
    public static interface Combiner<T, U, V> { 
        public V combine(T t, U u);
    }

    /**
     * A specialization of combiner that is associative and accepts
     * pairs of objects of the same type and returning one of the same
     * type. Like for example, an addition operation, a Reducer must
     * be (left) associative: combine(a, combine(b, c)) should have
     * the same result as combine(conbine(a, b), c).
     */
    public static interface Reducer<T> extends Combiner<T, T, T> {
        public T combine(T u, T v); 
    }

    /**
     * Interface for a boolean method of one argument
     */
    public static interface Predicate<T> {
        public boolean evaluate(T t);
    }

    /**
     * Version of a mapper that returns an int result
     */
    public static interface MapperToInt<T> {
        public int map(T t);
    }

    /**
     * Version of a mapper that returns a double result
     */
    public static interface MapperToDouble<T> {
        public double map(T t);
    }

    /**
     * Version of a mapper that returns a long result
     */
    public static interface MapperToLong<T> {
        public long map(T t);
    }

    /**
     * Version of a Mapper that accepts int arguments
     */
    public static interface MapperFromInt<T> {
        public T map(int t);
    }

    /**
     * Version of a Mapper that accepts double arguments
     */
    public static interface MapperFromDouble<T> {
        public T map(double t);
    }

    /**
     * Version of a Mapper that accepts long arguments
     */
    public static interface MapperFromLong<T> {
        public T map(long t);
    }

    /** Version of Generator for double */
    public static interface DoubleGenerator { 
        public double generate();               
    }

    /** Version of Procedure for double */
    public static interface DoubleProcedure {
        public void apply(double t);
    }

    /** Version of Transformer for double */
    public static interface DoubleTransformer {
        public double map(double u);
    }

    /** Version of Reducer for double */
    public static interface DoubleReducer {
        public double combine(double u, double v);
    }

    /** Version of Predicate for double */
    public static interface DoublePredicate {
        public boolean evaluate(double t);
    }


    /** Version of Generator for long */
    public static interface LongGenerator { 
        public long generate();               
    }

    /** Version of Procedure for long */
    public static interface LongProcedure {
        public void apply(long t);
    }

    /** Version of Transformer for long */
    public static interface LongTransformer {
        public long map(long u);
    }

    /** Version of Reducer for long */
    public static interface LongReducer {
        public long combine(long u, long v);
    }

    /** Version of Predicate for long */
    public static interface LongPredicate {
        public boolean evaluate(long t);
    }

    /** Version of Generator for int */
    public static interface IntGenerator { 
        public int generate();               
    }

    /** Version of Procedure for int */
    public static interface IntProcedure {
        public void apply(int t);
    }

    /** Version of Transformer for int */
    public static interface IntTransformer {
        public int map(int u);
    }

    /** Version of Reducer for int */
    public static interface IntReducer {
        public int combine(int u, int v);
    }

    /** Version of Predicate for int */
    public static interface IntPredicate {
        public boolean evaluate(int t);
    }

}
