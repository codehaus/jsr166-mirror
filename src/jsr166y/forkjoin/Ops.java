/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;

/**
 * Interfaces and utilities declaring per-element operations used
 * within parallel methods on aggregates. This class provides type
 * names for all operation signatures accepting zero, one or two
 * arguments, and returning zero or one results, for parameterized
 * types, as well as specializations to <tt>int</tt>, <tt>long</tt>,
 * and <tt>double</tt>. In keeping with normal Java evaluation rules
 * that promote, for example <tt>short</tt> to <tt>int</tt>, operation
 * names for these smaller types are absent.
 *
 * <p><b>Preliminary release note: Some of the declarations in this
 * class are likely to be moved elsewhere in the JDK libraries
 * upon actual release, and most likely will not all nested in the
 * same class.</b>
 *
 * <p>The naming conventions are as follows:
 * <ul>
 *
 * <li> The name of the single method declared in each interface is
 * simply <tt>op</tt> (short for "operate").
 *
 * <li> An <tt>Op</tt> (short for "operation") maps a single argument to
 * a result. Example: negating a value.
 *
 * <li> The names for scalar ops accepting and returning the same type
 * are prefaced by their type name.
 *
 * <li> A <tt>BinaryOp</tt> maps two arguments to a result. Example:
 * dividing two numbers
 *
 * <li>A <tt>Reducer</tt> is an <em>associative</em> binary op
 * accepting and returning values of the same type; where op(a, op(b,
 * c)) should have the same result as op(op(a, b), c).  Example:
 * adding two numbers.
 *
 * <li> Scalar binary ops accepting and returning the same type
 * include their type name.
 *
 * <li> Mixed-type operators are named just by their argument type
 * names.
 *
 * <li> A <tt>Generator</tt> takes no arguments and returns a result.
 * Examples: random number generators, builders
 *
 * <li> A <tt>Procedure</tt> accepts an argument but doesn't return a
 * result. Example: printing a value.  An <tt>Action</tt> is a
 * Procedure that takes no arguments.
 *
 * <li>A <tt>Predicate</tt> accepts a value and returns a boolean indicator
 * that the argument obeys some property. Example: testing if a number is even.
 *
 * <li>A <tt>BinaryPredicate</tt> accepts two values and returns a
 * boolean indicator that the arguments obeys some relation. Example:
 * testing if two numbers are relatively prime.
 *
 * <li>Scalar versions of {@link Comparator} have the same properties
 * as the Object version -- returning negative, zero, or positive
 * if the first argument is less, equal, or greater than the second.
 *
 * </ul>
 *
 * <p>In addition to stated signatures, implementations of these
 * interfaces must work safely in parallel. In general, this means
 * methods should operate only on their arguments, and should not rely
 * on ThreadLocals, unsafely published globals, or other unsafe
 * constructions. Additionally, they should not block waiting for
 * synchronization.
 *
 * <p> This class also provides methods returning some commonly used
 * implementations of some of these interfaces
 *
 * <p>This class is normally best used via <tt>import static</tt>.
 */
public class Ops {
    private Ops() {} // disable construction

    // You want to read/edit this with a wide editor panel

    public static interface Op<A,R>                      { R       op(A a);}
    public static interface Predicate<A>                 { boolean op(A a);}
    public static interface Procedure<A>                 { void    op(A a);}
    public static interface Generator<R>                 { R       op();}
    public static interface BinaryPredicate<A,B>         { boolean op(A a, B b);}
    public static interface BinaryOp<A,B,R>              { R       op(A a, B b);}
    public static interface Reducer<A> extends BinaryOp<A, A, A>{}

    public static interface IntOp                        { int     op(int a);}
    public static interface IntPredicate                 { boolean op(int a);}
    public static interface IntProcedure                 { void    op(int a);}
    public static interface IntGenerator                 { int     op();}
    public static interface BinaryIntPredicate           { boolean op(int a, int b);}
    public static interface BinaryIntOp                  { int     op(int a, int b);}
    public static interface IntReducer extends BinaryIntOp{}
    public static interface IntComparator                { int     compare(int a, int b);}

    public static interface LongOp                       { long    op(long a);}
    public static interface LongPredicate                { boolean op(long a);}
    public static interface LongProcedure                { void    op(long a);}
    public static interface LongGenerator                { long    op();}
    public static interface BinaryLongPredicate          { boolean op(long a, long b);}
    public static interface BinaryLongOp                 { long    op(long a, long b);}
    public static interface LongReducer extends BinaryLongOp{}
    public static interface LongComparator               { int     compare(long a, long b);}

    public static interface DoubleOp                     { double  op(double a);}
    public static interface DoublePredicate              { boolean op(double a);}
    public static interface DoubleProcedure              { void    op(double a);}
    public static interface DoubleGenerator              { double  op();}
    public static interface DoubleBinaryPredicate        { boolean op(double a, double b);}
    public static interface BinaryDoubleOp               { double  op(double a, double b);}
    public static interface DoubleReducer extends BinaryDoubleOp{}
    public static interface DoubleComparator             { int     compare(double a, double b);}

    public static interface Action                       { void    op();}

    // mixed mode ops
    public static interface IntToLong                    { long    op(int a);}
    public static interface IntToDouble                  { double  op(int a);}
    public static interface IntToObject<R>               { R       op(int a);}
    public static interface LongToInt                    { int     op(long a);}
    public static interface LongToDouble                 { double  op(long a);}
    public static interface LongToObject<R>              { R       op(long a);}
    public static interface DoubleToInt                  { int     op(double a);}
    public static interface DoubleToLong                 { long    op(double a);}
    public static interface DoubleToObject<R>            { R       op(double a);}
    public static interface ObjectToInt<A>               { int     op(A a);}
    public static interface ObjectToLong<A>              { long    op(A a);}
    public static interface ObjectToDouble<A>            { double  op(A a);}

    // mixed mode binary ops
    public static interface IntAndIntToLong              { long   op(int a, int b);}
    public static interface IntAndIntToDouble            { double op(int a, int b);}
    public static interface IntAndIntToObject<R>         { R      op(int a, int b);}
    public static interface IntAndLongToInt              { int    op(int a, long b);}
    public static interface IntAndLongToLong             { long   op(int a, long b);}
    public static interface IntAndLongToDouble           { double op(int a, long b);}
    public static interface IntAndLongToObject<R>        { R      op(int a, long b);}
    public static interface IntAndDoubleToInt            { int    op(int a, double b);}
    public static interface IntAndDoubleToLong           { long   op(int a, double b);}
    public static interface IntAndDoubleToDouble         { double op(int a, double b);}
    public static interface IntAndDoubleToObject<R>      { R      op(int a, double b);}
    public static interface IntAndObjectToInt<A>         { int    op(int a, A b);}
    public static interface IntAndObjectToLong<A>        { long   op(int a, A b);}
    public static interface IntAndObjectToDouble<A>      { double op(int a, A b);}
    public static interface IntAndObjectToObject<A,R>    { R      op(int a, A b);}
    public static interface LongAndIntToInt              { int    op(long a, int b);}
    public static interface LongAndIntToLong             { long   op(long a, int b);}
    public static interface LongAndIntToDouble           { double op(long a, int b);}
    public static interface LongAndIntToObject<R>        { R      op(long a, int b);}
    public static interface LongAndLongToInt             { int    op(long a, long b);}
    public static interface LongAndLongToDouble          { double op(long a, long b);}
    public static interface LongAndLongToObject<R>       { R      op(long a, long b);}
    public static interface LongAndDoubleToInt           { int    op(long a, double b);}
    public static interface LongAndDoubleToLong          { long   op(long a, double b);}
    public static interface LongAndDoubleToDouble        { double op(long a, double b);}
    public static interface LongAndDoubleToObject<R>     { R      op(long a, double b);}
    public static interface LongAndObjectToInt<A>        { int    op(long a, A b);}
    public static interface LongAndObjectToLong<A>       { long   op(long a, A b);}
    public static interface LongAndObjectToDouble<A>     { double op(long a, A b);}
    public static interface LongAndObjectToObject<A,R>   { R      op(long a, A b);}
    public static interface DoubleAndIntToInt            { int    op(double a, int b);}
    public static interface DoubleAndIntToLong           { long   op(double a, int b);}
    public static interface DoubleAndIntToDouble         { double op(double a, int b);}
    public static interface DoubleAndIntToObject<R>      { R      op(double a, int b);}
    public static interface DoubleAndLongToInt           { int    op(double a, long b);}
    public static interface DoubleAndLongToLong          { long   op(double a, long b);}
    public static interface DoubleAndLongToDouble        { double op(double a, long b);}
    public static interface DoubleAndLongToObject<R>     { R      op(double a, long b);}
    public static interface DoubleAndDoubleToInt         { int    op(double a, double b);}
    public static interface DoubleAndDoubleToLong        { long   op(double a, double b);}
    public static interface DoubleAndDoubleToObject<R>   { R      op(double a, double b);}
    public static interface DoubleAndObjectToInt<A>      { int    op(double a, A b);}
    public static interface DoubleAndObjectToLong<A>     { long   op(double a, A b);}
    public static interface DoubleAndObjectToDouble<A>   { double op(double a, A b);}
    public static interface DoubleAndObjectToObject<A,R> { R      op(double a, A b);}
    public static interface ObjectAndIntToInt<A>         { int    op(A a, int b);}
    public static interface ObjectAndIntToLong<A>        { long   op(A a, int b);}
    public static interface ObjectAndIntToDouble<A>      { double op(A a, int b);}
    public static interface ObjectAndIntToObject<A,R>    { R      op(A a, int b);}
    public static interface ObjectAndLongToInt<A>        { int    op(A a, long b);}
    public static interface ObjectAndLongToLong<A>       { long   op(A a, long b);}
    public static interface ObjectAndLongToDouble<A>     { double op(A a, long b);}
    public static interface ObjectAndLongToObject<A,R>   { R      op(A a, long b);}
    public static interface ObjectAndDoubleToInt<A>      { int    op(A a, double b);}
    public static interface ObjectAndDoubleToLong<A>     { long   op(A a, double b);}
    public static interface ObjectAndDoubleToDouble<A>   { double op(A a, double b);}
    public static interface ObjectAndDoubleToObject<A,R> { R      op(A a, double b);}
    public static interface ObjectAndObjectToInt<A,B>    { int    op(A a, B b);}
    public static interface ObjectAndObjectToLong<A,B>   { long   op(A a, B b);}
    public static interface ObjectAndObjectToDouble<A,B> { double op(A a, B b);}

    // Static factories for builtin ops

    /**
     * Returns a Comparator for Comparable objects
     */
    public static <T extends Comparable<? super T>> Comparator<T>
                             naturalComparator(Class<T> type) {
        return new Comparator<T>() {
            public int compare(T a, T b) { return a.compareTo(b); }
        };
    }

    /**
     * Returns a reducer returning the maximum of two Comparable
     * elements, treating null as less than any non-null element.
     */
    public static <T extends Comparable<? super T>> Reducer<T>
                             naturalMaxReducer(Class<T> type) {
        return new Reducer<T>() {
            public T op(T a, T b) {
                return (a != null &&
                        (b == null || a.compareTo(b) >= 0))? a : b;
            }
        };
    }

    /**
     * Returns a reducer returning the minimum of two Comparable
     * elements, treating null as greater than any non-null element.
     */
    public static <T extends Comparable<? super T>> Reducer<T>
                             naturalMinReducer(Class<T> type) {
        return new Reducer<T>() {
            public T op(T a, T b) {
                return (a != null &&
                        (b == null || a.compareTo(b) <= 0))? a : b;
            }
        };
    }

    /**
     * Returns a reducer returning the maximum of two elements, using
     * the given comparator, and treating null as less than any
     * non-null element.
     */
    public static <T> Reducer<T> maxReducer
        (final Comparator<? super T> comparator) {
        return new Reducer<T>() {
            public T op(T a, T b) {
                return (a != null &&
                        (b == null || comparator.compare(a, b) >= 0))? a : b;
            }
        };
    }

    /**
     * Returns a reducer returning the minimum of two elements, using
     * the given comparator, and treating null as greater than any
     * non-null element.
     */
    public static <T> Reducer<T> minReducer
        (final Comparator<? super T> comparator) {
        return new Reducer<T>() {
            public T op(T a, T b) {
                return (a != null &&
                        (b == null || comparator.compare(a, b) <= 0))? a : b;
            }
        };
    }

    /**
     * Returns a Comparator that casts its arguments as Comparable on
     * each comparison, throwing ClassCastException on failure.
     */
    public static Comparator<Object> castedComparator() {
        return (Comparator<Object>)(RawComparator.cmp);
    }
    static final class RawComparator implements Comparator {
        static final RawComparator cmp = new RawComparator();
        public int compare(Object a, Object b) {
            return ((Comparable)a).compareTo((Comparable)b);
        }
    }

    /**
     * Returns a reducer returning maximum of two values, or
     * <tt>null</tt> if both arguments are null, and that casts
     * its arguments as Comparable on each comparison, throwing
     * ClassCastException on failure.
     */
    public static Reducer<Object> castedMaxReducer() {
        return (Reducer<Object>)RawMaxReducer.max;
    }
    static final class RawMaxReducer implements Reducer {
        static final RawMaxReducer max = new RawMaxReducer();
        public Object op(Object a, Object b) {
            return (a != null &&
                    (b == null ||
                     ((Comparable)a).compareTo((Comparable)b) >= 0))? a : b;
        }
    }

    /**
     * Returns a reducer returning minimum of two values, or
     * <tt>null</tt> if both arguments are null, and that casts
     * its arguments as Comparable on each comparison, throwing
     * ClassCastException on failure.
     */
    public static Reducer<Object> castedMinReducer() {
        return (Reducer<Object>)RawMinReducer.min;
    }
    static final class RawMinReducer implements Reducer {
        static final RawMinReducer min = new RawMinReducer();
        public Object op(Object a, Object b) {
            return (a != null &&
                    (b == null ||
                     ((Comparable)a).compareTo((Comparable)b) <= 0))? a : b;
        }
    }


    /**
     * Returns a comparator for doubles relying on natural ordering
     */
    public static DoubleComparator naturalDoubleComparator() {
        return NaturalDoubleComparator.comparator;
    }
    static final class NaturalDoubleComparator
        implements DoubleComparator {
        static final NaturalDoubleComparator comparator = new
            NaturalDoubleComparator();
        public int compare(double a, double b) {
            return Double.compare(a, b);
        }
    }

    /**
     * Returns a reducer returning the maximum of two double elements,
     * using natural comparator
     */
    public static DoubleReducer naturalDoubleMaxReducer() {
        return NaturalDoubleMaxReducer.max;
    }

    static final class NaturalDoubleMaxReducer
        implements DoubleReducer {
        public static final NaturalDoubleMaxReducer max =
            new NaturalDoubleMaxReducer();
        public double op(double a, double b) { return Math.max(a, b); }
    }

    /**
     * Returns a reducer returning the minimum of two double elements,
     * using natural comparator
     */
    public static DoubleReducer naturalDoubleMinReducer() {
        return NaturalDoubleMinReducer.min;
    }
    static final class NaturalDoubleMinReducer
        implements DoubleReducer {
        public static final NaturalDoubleMinReducer min =
            new NaturalDoubleMinReducer();
        public double op(double a, double b) { return Math.min(a, b); }
    }

    /**
     * Returns a reducer returning the maximum of two double elements,
     * using the given comparator
     */
    public static DoubleReducer doubleMaxReducer
        (final DoubleComparator comparator) {
        return new DoubleReducer() {
                public double op(double a, double b) {
                    return (comparator.compare(a, b) >= 0)? a : b;
                }
            };
    }

    /**
     * Returns a reducer returning the minimum of two double elements,
     * using the given comparator
     */
    public static DoubleReducer doubleMinReducer
        (final DoubleComparator comparator) {
        return new DoubleReducer() {
                public double op(double a, double b) {
                    return (comparator.compare(a, b) <= 0)? a : b;
                }
            };
    }

    /**
     * Returns a comparator for longs relying on natural ordering
     */
    public static LongComparator naturalLongComparator() {
        return NaturalLongComparator.comparator;
    }
    static final class NaturalLongComparator
        implements LongComparator {
        static final NaturalLongComparator comparator = new
            NaturalLongComparator();
        public int compare(long a, long b) {
            return a < b? -1 : ((a > b)? 1 : 0);
        }
    }

    /**
     * Returns a reducer returning the maximum of two long elements,
     * using natural comparator
     */
    public static LongReducer naturalLongMaxReducer() {
        return NaturalLongMaxReducer.max;
    }

    static final class NaturalLongMaxReducer
        implements LongReducer {
        public static final NaturalLongMaxReducer max =
            new NaturalLongMaxReducer();
        public long op(long a, long b) { return a >= b? a : b; }
    }

    /**
     * A reducer returning the minimum of two long elements,
     * using natural comparator
     */
    public static LongReducer naturalLongMinReducer() {
        return NaturalLongMinReducer.min;
    }
    static final class NaturalLongMinReducer
        implements LongReducer {
        public static final NaturalLongMinReducer min =
            new NaturalLongMinReducer();
        public long op(long a, long b) { return a <= b? a : b; }
    }

    /**
     * Returns a reducer returning the maximum of two long elements,
     * using the given comparator
     */
    public static LongReducer longMaxReducer
        (final LongComparator comparator) {
        return new LongReducer() {
                public long op(long a, long b) {
                    return (comparator.compare(a, b) >= 0)? a : b;
                }
            };
    }

    /**
     * Returns a reducer returning the minimum of two long elements,
     * using the given comparator
     */
    public static LongReducer longMinReducer
        (final LongComparator comparator) {
        return new LongReducer() {
                public long op(long a, long b) {
                    return (comparator.compare(a, b) <= 0)? a : b;
                }
            };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T,U,V> Op<T,V> compoundOp
        (final Op<? super T, ? extends U> first,
         final Op<? super U, ? extends V> second) {
        return new Op<T,V>() {
            public final V op(T t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T,V> Op<T,V> compoundOp
        (final ObjectToDouble<? super T> first,
         final DoubleToObject<? extends V> second) {
        return new Op<T,V>() {
            public final V op(T t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T,V> Op<T,V> compoundOp
        (final ObjectToLong<? super T> first,
         final LongToObject<? extends V> second) {
        return new Op<T,V>() {
            public final V op(T t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T,V> DoubleToObject<V> compoundOp
        (final DoubleToObject<? extends T> first,
         final Op<? super T,? extends V> second) {
        return new DoubleToObject<V>() {
            public final V op(double t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T,V> LongToObject<V> compoundOp
        (final LongToObject<? extends T> first,
         final Op<? super T,? extends V> second) {
        return new LongToObject<V>() {
            public final V op(long t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T,U> ObjectToDouble<T> compoundOp
        (final Op<? super T, ? extends U> first,
         final ObjectToDouble<? super U> second) {
        return new ObjectToDouble<T>() {
            public final double op(T t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T,U> ObjectToLong<T> compoundOp
        (final Op<? super T, ? extends U> first,
         final ObjectToLong<? super U> second) {
        return new ObjectToLong<T>() {
            public final long op(T t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> ObjectToDouble<T> compoundOp
        (final ObjectToDouble<? super T> first,
         final DoubleOp second) {
        return new ObjectToDouble<T>() {
            public final double op(T t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> ObjectToLong<T> compoundOp
        (final ObjectToDouble<? super T> first,
         final DoubleToLong second) {
        return new ObjectToLong<T>() {
            public final long op(T t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> ObjectToLong<T> compoundOp
        (final ObjectToLong<? super T> first,
         final LongOp second) {
        return new ObjectToLong<T>() {
            public final long op(T t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> ObjectToDouble<T> compoundOp
        (final ObjectToLong<? super T> first,
         final LongToDouble second) {
        return new ObjectToDouble<T>() {
            public final double op(T t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static DoubleOp compoundOp
        (final DoubleOp first,
         final DoubleOp second) {
        return new DoubleOp() {
            public final double op(double t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static DoubleToLong compoundOp
        (final DoubleOp first,
         final DoubleToLong second) {
        return new DoubleToLong() {
            public final long op(double t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static DoubleToLong compoundOp
        (final DoubleToLong first,
         final LongOp second) {
        return new DoubleToLong() {
            public final long op(double t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> DoubleToObject<T> compoundOp
        (final DoubleToLong first,
         final LongToObject<? extends T> second) {
        return new DoubleToObject<T>() {
            public final T op(double t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> LongToObject<T> compoundOp
        (final LongToDouble first,
         final DoubleToObject<? extends T> second) {
        return new LongToObject<T>() {
            public final T op(long t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static LongToDouble compoundOp
        (final LongOp first,
         final LongToDouble second) {
        return new LongToDouble() {
            public final double op(long t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static LongToDouble compoundOp
        (final LongToDouble first,
         final DoubleOp second) {
        return new LongToDouble() {
            public final double op(long t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> DoubleToObject<T> compoundOp
        (final DoubleOp first,
         final DoubleToObject<? extends T> second) {
        return new DoubleToObject<T>() {
            public final T op(double t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> LongToObject<T> compoundOp
        (final LongOp first,
         final LongToObject<? extends T> second) {
        return new LongToObject<T>() {
            public final T op(long t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> DoubleOp compoundOp
        (final DoubleToObject<? extends T> first,
         final ObjectToDouble<? super T>  second) {
        return new DoubleOp() {
            public final double op(double t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> LongToDouble compoundOp
        (final LongToObject<? extends T> first,
         final ObjectToDouble<? super T>  second) {
        return new LongToDouble() {
            public final double op(long t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> DoubleToLong compoundOp
        (final DoubleToObject<? extends T> first,
         final ObjectToLong<? super T>  second) {
        return new DoubleToLong() {
            public final long op(double t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static <T> LongOp compoundOp
        (final LongToObject<? extends T> first,
         final ObjectToLong<? super T>  second) {
        return new LongOp() {
            public final long op(long t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static LongOp compoundOp
        (final LongOp first,
         final LongOp second) {
        return new LongOp() {
            public final long op(long t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static DoubleOp compoundOp
        (final DoubleToLong first,
         final LongToDouble second) {
        return new DoubleOp() {
            public final double op(double t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a composite mapper that applies a second mapper to the results
     * of applying the first one
     */
    public static LongOp compoundOp
        (final LongToDouble first,
         final DoubleToLong second) {
        return new LongOp() {
            public final long op(long t) { return second.op(first.op(t)); }
        };
    }

    /**
     * Returns a predicate evaluating to the negation of its contained predicate
     */
    public static <T> Predicate<T> notPredicate
        (final Predicate<T> pred) {
        return new Predicate<T>() {
            public final boolean op(T x) { return !pred.op(x); }
        };
    }

    /**
     * Returns a predicate evaluating to the negation of its contained predicate
     */
    public static DoublePredicate notPredicate
        (final DoublePredicate pred) {
        return new DoublePredicate() {
                public final boolean op(double x) { return !pred.op(x); }
            };
    }

    /**
     * Returns a predicate evaluating to the negation of its contained predicate
     */
    public static LongPredicate notPredicate
        (final LongPredicate pred) {
        return new LongPredicate() {
                public final boolean op(long x) { return !pred.op(x); }
            };
    }

    /**
     * Returns a predicate evaluating to the conjunction of its contained predicates
     */
    public static <S, T extends S> Predicate<T> andPredicate
        (final Predicate<S> first,
         final Predicate<? super T> second) {
        return new Predicate<T>() {
            public final boolean op(T x) {
                return first.op(x) && second.op(x);
            }
        };
    }

    /**
     * Returns a predicate evaluating to the disjuntion of its contained predicates
     */
    public static <S, T extends S> Predicate<T> orPredicate
        (final Predicate<S> first,
         final Predicate<? super T> second) {
        return new Predicate<T>() {
            public final boolean op(T x) {
                return first.op(x) || second.op(x);
            }
        };
    }

    /**
     * Returns a predicate evaluating to the conjunction of its contained predicates
     */
    public static DoublePredicate andPredicate
        (final DoublePredicate first,
         final DoublePredicate second) {
        return new DoublePredicate() {
            public final boolean op(double x) {
                return first.op(x) && second.op(x);
            }
        };
    }

    /**
     * Returns a predicate evaluating to the disjuntion of its contained predicates
     */
    public static DoublePredicate orPredicate
        (final DoublePredicate first,
         final DoublePredicate second) {
        return new DoublePredicate() {
            public final boolean op(double x) {
                return first.op(x) || second.op(x);
            }
        };
    }


    /**
     * Returns a predicate evaluating to the conjunction of its contained predicates
     */
    public static LongPredicate andPredicate
        (final LongPredicate first,
         final LongPredicate second) {
        return new LongPredicate() {
            public final boolean op(long x) {
                return first.op(x) && second.op(x);
            }
        };
    }

    /**
     * Returns a predicate evaluating to the disjuntion of its contained predicates
     */
    public static LongPredicate orPredicate
        (final LongPredicate first,
         final LongPredicate second) {
        return new LongPredicate() {
            public final boolean op(long x) {
                return first.op(x) || second.op(x);
            }
        };
    }

    /**
     * Returns a predicate evaluating to true if its argument is non-null
     */
    public static  Predicate<Object> isNonNullPredicate() {
        return IsNonNullPredicate.predicate;
    }
    static final class IsNonNullPredicate implements Predicate<Object> {
        static final IsNonNullPredicate predicate =
            new IsNonNullPredicate();
        public final boolean op(Object x) {
            return x != null;
        }
    }

    /**
     * Returns a predicate evaluating to true if its argument is null
     */
    public static  Predicate<Object> isNullPredicate() {
        return IsNullPredicate.predicate;
    }
    static final class IsNullPredicate implements Predicate<Object> {
        static final IsNullPredicate predicate =
            new IsNullPredicate();
        public final boolean op(Object x) {
            return x != null;
        }
    }

    /**
     * Returns a predicate evaluating to true if its argument is an instance
     * of (see {@link Class#isInstance} the given type (class).
     */
    public static Predicate<Object> instanceofPredicate(final Class type) {
        return new Predicate<Object>() {
            public final boolean op(Object x) {
                return type.isInstance(x);
            }
        };
    }

    /**
     * Returns a predicate evaluating to true if its argument is assignable
     * from (see {@link Class#isAssignableFrom} the given type (class).
     */
    public static Predicate<Object> isAssignablePredicate(final Class type) {
        return new Predicate<Object>() {
            public final boolean op(Object x) {
                return type.isAssignableFrom(x.getClass());
            }
        };
    }

    /**
     * Returns a reducer that adds two double elements
     */
    public static DoubleReducer doubleAdder() { return DoubleAdder.adder; }
    static final class DoubleAdder implements DoubleReducer {
        static final DoubleAdder adder = new DoubleAdder();
        public double op(double a, double b) { return a + b; }
    }

    /**
     * Returns a reducer that adds two long elements
     */
    public static LongReducer longAdder() { return LongAdder.adder; }
    static final class LongAdder implements LongReducer {
        static final LongAdder adder = new LongAdder();
        public long op(long a, long b) { return a + b; }
    }

    /**
     * Returns a reducer that adds two int elements
     */
    public static IntReducer intAdder() { return IntAdder.adder; }
    static final class IntAdder implements IntReducer {
        static final IntAdder adder = new IntAdder();
        public int op(int a, int b) { return a + b; }
    }

    /**
     * Returns a generator producing uniform random values between
     * zero and one, with the same properties as {@link
     * java.util.Random#nextDouble} but operating independently across
     * ForkJoinWorkerThreads and usable only within forkjoin
     * computations.
     */
    public static DoubleGenerator doubleRandom() {
        return DoubleRandomGenerator.generator;
    }
    static final class DoubleRandomGenerator implements DoubleGenerator {
        static final DoubleRandomGenerator generator =
            new DoubleRandomGenerator();
        public double op() {
            return ForkJoinWorkerThread.nextRandomDouble();
        }
    }

    /**
     * Returns a generator producing uniform random values between
     * zero and the given bound, with the same properties as {@link
     * java.util.Random#nextDouble} but operating independently across
     * ForkJoinWorkerThreads and usable only within forkjoin
     * computations.
     * @param bound the upper bound (exclusive) of opd values
     */
    public static DoubleGenerator doubleRandom(double bound) {
        return new DoubleBoundedRandomGenerator(bound);
    }
    static final class DoubleBoundedRandomGenerator implements DoubleGenerator {
        final double bound;
        DoubleBoundedRandomGenerator(double bound) { this.bound = bound; }
        public double op() {
            return ForkJoinWorkerThread.nextRandomDouble() * bound;
        }
    }

    /**
     * Returns a generator producing uniform random values between the
     * given least value (inclusive) and bound (exclusive), operating
     * independently across ForkJoinWorkerThreads and usable only
     * within forkjoin computations.
     * @param least the least value returned
     * @param bound the upper bound (exclusive) of opd values
     */
    public static DoubleGenerator doubleRandom(double least, double bound) {
        return new DoubleIntervalRandomGenerator(least, bound);
    }
    static final class DoubleIntervalRandomGenerator implements DoubleGenerator {
        final double least;
        final double range;
        DoubleIntervalRandomGenerator(double least, double bound) {
            this.least = least; this.range = bound - least;
        }
        public double op() {
            return ForkJoinWorkerThread.nextRandomDouble() * range + least;
        }
    }

    /**
     * Returns a generator producing uniform random values with the
     * same properties as {@link java.util.Random#nextLong} but
     * operating independently across ForkJoinWorkerThreads and usable
     * only within forkjoin computations.
     */
    public static LongGenerator longRandom() {
        return LongRandomGenerator.generator;
    }
    static final class LongRandomGenerator implements LongGenerator {
        static final LongRandomGenerator generator =
            new LongRandomGenerator();
        public long op() {
            return ForkJoinWorkerThread.nextRandomLong();
        }
    }

    /**
     * Returns a generator producing uniform random values with the
     * same properties as {@link java.util.Random#nextInt(int)} but
     * operating independently across ForkJoinWorkerThreads and usable
     * only within forkjoin computations.
     * @param bound the upper bound (exclusive) of opd values
     */
    public static LongGenerator longRandom(long bound) {
        if (bound <= 0)
            throw new IllegalArgumentException();
        return new LongBoundedRandomGenerator(bound);
    }
    static final class LongBoundedRandomGenerator implements LongGenerator {
        final long bound;
        LongBoundedRandomGenerator(long bound) { this.bound = bound; }
        public long op() {
            return ForkJoinWorkerThread.nextRandomLong(bound);
        }
    }

    /**
     * Returns a generator producing uniform random values between the
     * given least value (inclusive) and bound (exclusive), operating
     * independently across ForkJoinWorkerThreads and usable only
     * within forkjoin computations.
     * @param least the least value returned
     * @param bound the upper bound (exclusive) of opd values
     */
    public static LongGenerator longRandom(long least, long bound) {
        if (least >= bound)
            throw new IllegalArgumentException();
        return new LongIntervalRandomGenerator(least, bound);
    }
    static final class LongIntervalRandomGenerator implements LongGenerator {
        final long least;
        final long range;
        LongIntervalRandomGenerator(long least, long bound) {
            this.least = least; this.range = bound - least;
        }
        public long op() {
            return ForkJoinWorkerThread.nextRandomLong(range) + least;
        }
    }

    /**
     * Returns a generator producing uniform random values with the
     * same properties as {@link java.util.Random#nextInt} but
     * operating independently across ForkJoinWorkerThreads and usable
     * only within forkjoin computations.
     */
    public static IntGenerator intRandom() {
        return IntRandomGenerator.generator;
    }
    static final class IntRandomGenerator implements IntGenerator {
        static final IntRandomGenerator generator =
            new IntRandomGenerator();
        public int op() {
            return ForkJoinWorkerThread.nextRandomInt();
        }
    }

    /**
     * Returns a generator producing uniform random values with the
     * same properties as {@link java.util.Random#nextInt(int)} but
     * operating independently across ForkJoinWorkerThreads and usable
     * only within forkjoin computations.
     * @param bound the upper bound (exclusive) of opd values
     */
    public static IntGenerator intRandom(int bound) {
        if (bound <= 0)
            throw new IllegalArgumentException();
        return new IntBoundedRandomGenerator(bound);
    }
    static final class IntBoundedRandomGenerator implements IntGenerator {
        final int bound;
        IntBoundedRandomGenerator(int bound) { this.bound = bound; }
        public int op() {
            return ForkJoinWorkerThread.nextRandomInt(bound);
        }
    }

    /**
     * Returns a generator producing uniform random values between the
     * given least value (inclusive) and bound (exclusive), operating
     * independently across ForkJoinWorkerThreads and usable only
     * within forkjoin computations.
     * @param least the least value returned
     * @param bound the upper bound (exclusive) of opd values
     */
    public static IntGenerator intRandom(int least, int bound) {
        if (least >= bound)
            throw new IllegalArgumentException();
        return new IntIntervalRandomGenerator(least, bound);
    }
    static final class IntIntervalRandomGenerator implements IntGenerator {
        final int least;
        final int range;
        IntIntervalRandomGenerator(int least, int bound) {
            this.least = least; this.range = bound - least;
        }
        public int op() {
            return ForkJoinWorkerThread.nextRandomInt(range) + least;
        }
    }


}
