/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import static jsr166y.forkjoin.Ops.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.Array;

/**
 * An array supporting parallel operations.
 *
 * <p>A ParallelArray maintains a {@link ForkJoinExecutor} and an
 * array in order to provide parallel aggregate operations.  The main
 * operations are to <em>apply</em> some procedure to each element, to
 * <em>map</em> each element to a new element, to <em>replace</em>
 * each element, to <em>select</em> a subset of elements based on
 * matching a predicate or ranges of indices, and to <em>reduce</em>
 * all elements into a single value such as a sum.
 *
 * <p> A ParallelArray is constructed by allocating, using, or copying
 * an array, using one of the static factory methods {@link #create},
 * {@link #createEmpty}, {@link #createUsingHandoff} and {@link
 * #createFromCopy}. Upon construction, the encapsulated array managed
 * by the ParallelArray must not be shared between threads without
 * external synchronization. In particular, as is the case with any
 * array, access by another thread of an element of a ParallelArray
 * while another operation is in progress has undefined effects.
 *
 * <p> The ForkJoinExecutor used to construct a ParallelArray can be
 * shared safely by other threads (and used in other
 * ParallelArrays). To avoid the overhead associated with creating
 * multiple executors, it is often a good idea to use the {@link
 * #defaultExecutor()} across all ParallelArrays. However, you might
 * choose to use different ones for the sake of controlling processor
 * usage, isolating faults, and/or ensuring progress.
 *
 * <p>A ParallelArray is not a List. It relies on random access across
 * array elements to support efficient parallel operations.  However,
 * a ParallelArray can be viewed and manipulated as a List, via method
 * {@link ParallelArray#asList}. The <tt>asList</tt> view allows
 * incremental insertion and modification of elements while setting up
 * a ParallelArray, generally before using it for parallel
 * operations. Similarly, the list view may be useful when accessing
 * the results of computations in sequential contexts.  A
 * ParallelArray may also be created using the elements of any other
 * Collection, by constructing from the array returned by the
 * Collection's <tt>toArray</tt> method. The effects of mutative
 * <tt>asList</tt> operations may also be achieved directly using
 * method {@link #setLimit} along with element-by-element access
 * methods {@link #get}</tt> and {@link #set}.
 *
 * <p>While ParallelArrays can be based on any kind of an object
 * array, including "boxed" types such as Long, parallel operations on
 * scalar "unboxed" type are likely to be substantially more
 * efficient. For this reason, classes {@link ParallelLongArray}, and
 * {@link ParallelDoubleArray} are also supplied, and designed to
 * smoothly interoperate with ParallelArrays. You should also use a
 * ParallelLongArray for processing other integral scalar data
 * (<tt>int</tt>, <tt>short</tt>, etc).  And similarly use a
 * ParallelDoubleArray for <tt>float</tt> data.  (Further
 * specializations for these other types would add clutter without
 * significantly improving performance beyond that of the Long and
 * Double versions.)
 *
 * <p> Most usages of ParallelArray chain srts of operations prefixed
 * with range bounds, filters, and mappings (including mappings that
 * combine elements from other ParallelArrays), using
 * <tt>withBounds</tt>, <tt>withFilter</tt>, and <tt>withMapping</tt>,
 * respectively. For example,
 * <tt>aParallelArray.withFilter(aPredicate).all()</tt> creates a new
 * ParallelArray containing only those elements matching the
 * predicate. And for ParallelLongArrays a, b, and c,
 * <tt>a.withMapping(Ops.longAdder(),b).WithMapping(Ops.longAdder(),c).min()</tt>
 * returns the minimum value of a[i]+b[i]+c[i] for all i.  As
 * illustrated below, a <em>mapping</em> often represents accessing
 * some field or invoking some method of an element.  These versions
 * are typically more efficient than performing selections, then
 * mappings, then other operations in multiple (parallel) steps. The
 * basic ideas and usages of filtering and mapping are similar to
 * those in database query systems such as SQL, but take a more
 * restrictive form.  Series of filter and mapping prefixes may each
 * be cascaded, but all filter prefixes must precede all mapping
 * prefixes, to ensure efficient execution in s single parallel step.
 * Instances of <tt>WithFilter</tt> etc are useful only as operation
 * prefixes, and should not normally be stored in variables for future
 * use.
 *
 * <p>This class includes some reductions, such as <tt>min</tt>, that
 * are commonly useful for most element types, as well as a combined
 * version, <tt>summary</tt>, that computes all of them in a single
 * parallel step, which is normally more efficient than computing each
 * in turn.
 *
 * <p>The methods in this class are designed to perform efficiently
 * with both large and small pools, even with single-thread pools on
 * uniprocessors.  However, there is some overhead in parallelizing
 * operations, so short computations on small arrays might not execute
 * faster than sequential versions, and might even be slower.
 *
 * <p><b>Sample usages</b>.
 *
 * The main difference between programming with plain arrays and
 * programming with aggregates is that you must separately define each
 * of the component functions on elements. For example, the following
 * returns the maximum Grade Point Average across all senior students,
 * given a (fictional) <tt>Student</tt> class:
 *
 * <pre>
 * import static Ops.*;
 * class StudentStatistics {
 *   ParallelArray&lt;Student&gt; students = ...
 *   // ...
 *   public double getMaxSeniorGpa() {
 *     return students.withFilter(isSenior).withMapping(gpaField).max();
 *   }
 *
 *   // helpers:
 *   static final class IsSenior implements Predicate&lt;Student&gt; {
 *     public boolean op(Student s) { return s.credits &gt; 90; }
 *   }
 *   static final IsSenior isSenior = new IsSenior();
 *   static final class GpaField implements ObjectToDouble&lt;Student&gt {
 *     public double op(Student s) { return s.gpa; }
 *   }
 *   static final GpaField gpaField = new GpaField();
 * }
 * </pre>
 *
 */
public class ParallelArray<T> extends PAWithBounds<T> implements Iterable<T> {
    /*
     * See class PAS for most of the underlying parallel execution
     * code and explanation.
     */

    /**
     * Returns a common default executor for use in ParallelArrays.
     * This executor arranges enough parallelism to use most, but not
     * necessarily all, of the avaliable processors on this system.
     * @return the executor
     */
    public static ForkJoinExecutor defaultExecutor() {
        return PAS.defaultExecutor();
    }

    /** Lazily constructed list view */
    AsList listView;

    /**
     * Constructor for use by subclasses to create a new ParallelArray
     * using the given executor, and initially using the supplied
     * array, with effective size bound by the given limit. This
     * constructor is designed to enable extensions via
     * subclassing. To create a ParallelArray, use {@link #create},
     * {@link #createEmpty}, {@link #createUsingHandoff} or {@link
     * #createFromCopy}.
     * @param executor the executor
     * @param array the array
     * @param limit the upper bound limit
     */
    protected ParallelArray(ForkJoinExecutor executor, T[] array, int limit) {
        super(executor, 0, limit, array);
        if (executor == null || array == null)
            throw new NullPointerException();
        if (limit < 0 || limit > array.length)
            throw new IllegalArgumentException();
    }

    /**
     * Trusted internal version of protected constructor.
     */
    ParallelArray(ForkJoinExecutor executor, T[] array) {
        super(executor, 0, array.length, array);
    }

    /**
     * Creates a new ParallelArray using the given executor and
     * an array of the given size constructed using the
     * indicated base element type.
     * @param size the array size
     * @param elementType the type of the elements
     * @param executor the executor
     */
    public static <T> ParallelArray<T> create
        (int size, Class<? super T> elementType,
         ForkJoinExecutor executor) {
        T[] array = (T[])Array.newInstance(elementType, size);
        return new ParallelArray<T>(executor, array, size);
    }

    /**
     * Creates a new ParallelArray initially using the given array and
     * executor. In general, the handed off array should not be used
     * for other purposes once constructing this ParallelArray.  The
     * given array may be internally replaced by another array in the
     * course of methods that add or remove elements.
     * @param handoff the array
     * @param executor the executor
     */
    public static <T> ParallelArray<T> createUsingHandoff
        (T[] handoff, ForkJoinExecutor executor) {
        return new ParallelArray<T>(executor, handoff, handoff.length);
    }

    /**
     * Creates a new ParallelArray using the given executor and
     * initially holding copies of the given
     * source elements.
     * @param source the source of initial elements
     * @param executor the executor
     */
    public static <T> ParallelArray<T> createFromCopy
        (T[] source, ForkJoinExecutor executor) {
        // For now, avoid copyOf so people can compile with Java5
        int size = source.length;
        T[] array = (T[])Array.newInstance
            (source.getClass().getComponentType(), size);
        System.arraycopy(source, 0, array, 0, size);
        return new ParallelArray<T>(executor, array, size);
    }

    /**
     * Creates a new ParallelArray using an array of the given size,
     * initially holding copies of the given source truncated or
     * padded with nulls to obtain the specified length.
     * @param source the source of initial elements
     * @param size the array size
     * @param executor the executor
     */
    public static <T> ParallelArray<T> createFromCopy
        (int size, T[] source, ForkJoinExecutor executor) {
        // For now, avoid copyOf so people can compile with Java5
        T[] array = (T[])Array.newInstance
            (source.getClass().getComponentType(), size);
        System.arraycopy(source, 0, array, 0,
                         Math.min(source.length, size));
        return new ParallelArray<T>(executor, array, size);
    }

    /**
     * Creates a new ParallelArray using the given executor and an
     * array of the given size constructed using the indicated base
     * element type, but with an initial effective size of zero,
     * enabling incremental insertion via {@link ParallelArray#asList}
     * operations.
     * @param size the array size
     * @param elementType the type of the elements
     * @param executor the executor
     */
    public static <T> ParallelArray<T> createEmpty
        (int size, Class<? super T> elementType,
         ForkJoinExecutor executor) {
        T[] array = (T[])Array.newInstance(elementType, size);
        return new ParallelArray<T>(executor, array, 0);
    }

    /**
     * Summary statistics for a possibly bounded, filtered, and/or
     * mapped ParallelArray.
     */
    public static interface SummaryStatistics<T> {
        /** Return the number of elements */
        public int size();
        /** Return the minimum element, or null if empty */
        public T min();
        /** Return the maximum element, or null if empty */
        public T max();
        /** Return the index of the minimum element, or -1 if empty */
        public int indexOfMin();
        /** Return the index of the maximum element, or -1 if empty */
        public int indexOfMax();
    }

    /**
     * Returns the executor used for computations
     * @return the executor
     */
    public ForkJoinExecutor getExecutor() { return ex; }

    /**
     * Applies the given procedure to elements
     * @param procedure the procedure
     */
    public void apply(Procedure<? super T> procedure) {
        super.apply(procedure);
    }

    /**
     * Returns reduction of elements
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public T reduce(Reducer<T> reducer, T base) {
        return super.reduce(reducer, base);
    }

    /**
     * Returns a new ParallelArray holding all elements
     * @return a new ParallelArray holding all elements
     */
    public ParallelArray<T> all() {
        return super.all();
    }

    /**
     * Returns a new ParallelArray with the given element type holding
     * all elements
     * @param elementType the type of the elements
     * @return a new ParallelArray holding all elements
     */
    public ParallelArray<T> all(Class<? super T> elementType) {
        return super.all(elementType);
    }

    /**
     * Replaces elements with the results of applying the given transform
     * to their current values.
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> replaceWithMapping
                                   (Op<? super T, ? extends T> op) {
        super.replaceWithMapping(op);
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * mapping to their indices.
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> replaceWithMappedIndex(IntToObject<? extends T> op) {
        super.replaceWithMappedIndex(op);
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * mapping to each index and current element value
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> replaceWithMappedIndex
        (IntAndObjectToObject<? super T, ? extends T> op) {
        super.replaceWithMappedIndex(op);
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * generator.
     * @param generator the generator
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> replaceWithGeneratedValue(Generator<? extends T> generator) {
        super.replaceWithGeneratedValue(generator);
        return this;
    }

    /**
     * Replaces elements with the given value.
     * @param value the value
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> replaceWithValue(T value) {
        super.replaceWithValue(value);
        return this;
    }

    /**
     * Replaces elements with results of applying
     * <tt>op(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @return this (to simplify use in expressions)
     * @throws ArrayIndexOutOfBoundsException if other array has
     * fewer elements than this array.
     */
    public ParallelArray<? extends T> replaceWithMapping(BinaryOp<T,T,T> combiner,
                                   ParallelArray<? extends T> other) {
        super.replaceWithMapping(combiner, other.array);
        return this;
    }

    /**
     * Replaces elements with results of applying
     * <tt>op(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @return this (to simplify use in expressions)
     * @throws ArrayIndexOutOfBoundsException if other array has
     * fewer elements than this array.
     */
    public ParallelArray<? extends T> replaceWithMapping(BinaryOp<T,T,T> combiner, T[] other) {
        super.replaceWithMapping(combiner, other);
        return this;
    }

    /**
     * Returns the index of some element equal to given target,
     * or -1 if not present
     * @param target the element to search for
     * @return the index or -1 if not present
     */
    public int indexOf(T target) {
        return super.indexOf(target);
    }

    /**
     * Assuming this array is sorted, returns the index of an element
     * equal to given target, or -1 if not present. If the array
     * is not sorted, the results are undefined.
     * @param target the element to search for
     * @return the index or -1 if not present
     */
    public int binarySearch(T target) {
        return super.binarySearch(target);
    }

    /**
     * Assuming this array is sorted with respect to the given
     * comparator, returns the index of an element equal to given
     * target, or -1 if not present. If the array is not sorted, the
     * results are undefined.
     * @param target the element to search for
     * @param comparator the comparator
     * @return the index or -1 if not present
     */
    public int binarySearch(T target, Comparator<? super T> comparator) {
        return super.binarySearch(target, comparator);
    }

    /**
     * Returns summary statistics, using the given comparator
     * to locate minimum and maximum elements.
     * @param comparator the comparator to use for
     * locating minimum and maximum elements
     * @return the summary.
     */
    public ParallelArray.SummaryStatistics<T> summary
        (Comparator<? super T> comparator) {
        return super.summary(comparator);
    }

    /**
     * Returns summary statistics, assuming that all elements are
     * Comparables
     * @return the summary.
     */
    public ParallelArray.SummaryStatistics<T> summary() {
        return super.summary();
    }

    /**
     * Returns the minimum element, or null if empty
     * @param comparator the comparator
     * @return minimum element, or null if empty
     */
    public T min(Comparator<? super T> comparator) {
        return super.min(comparator);
    }

    /**
     * Returns the minimum element, or null if empty,
     * assuming that all elements are Comparables
     * @return minimum element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public T min() {
        return super.min();
    }

    /**
     * Returns the maximum element, or null if empty
     * @param comparator the comparator
     * @return maximum element, or null if empty
     */
    public T max(Comparator<? super T> comparator) {
        return super.max(comparator);
    }

    /**
     * Returns the maximum element, or null if empty
     * assuming that all elements are Comparables
     * @return maximum element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public T max() {
        return super.max();
    }

    /**
     * Replaces each element with the running cumulation of applying
     * the given reducer. For example, if the contents are the numbers
     * <tt>1, 2, 3</tt>, and the reducer operation adds numbers, then
     * after invocation of this method, the contents would be <tt>1,
     * 3, 6</tt> (that is, <tt>1, 1+2, 1+2+3</tt>);
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> cumulate(Reducer<T> reducer, T base) {
        super.cumulate(reducer, base);
        return this;
    }

    /**
     * Replaces each element with the cumulation of applying the given
     * reducer to all previous values, and returns the total
     * reduction. For example, if the contents are the numbers <tt>1,
     * 2, 3</tt>, and the reducer operation adds numbers, then after
     * invocation of this method, the contents would be <tt>0, 1,
     * 3</tt> (that is, <tt>0, 0+1, 0+1+2</tt>, and the return value
     * would be 6 (that is, <tt> 1+2+3</tt>);
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return the total reduction
     */
    public T precumulate(Reducer<T> reducer, T base) {
        return (T)(super.precumulate(reducer, base));
    }

    /**
     * Sorts the array. Unlike Arrays.sort, this sort does
     * not guarantee that elements with equal keys maintain their
     * relative position in the array.
     * @param comparator the comparator to use
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> sort(Comparator<? super T> comparator) {
        super.sort(comparator);
        return this;
    }

    /**
     * Sorts the array, assuming all elements are Comparable. Unlike
     * Arrays.sort, this sort does not guarantee that elements
     * with equal keys maintain their relative position in the array.
     * @throws ClassCastException if any element is not Comparable.
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> sort() {
        super.sort();
        return this;
    }

    /**
     * Returns a new ParallelArray containing only the non-null unique
     * elements of this array (that is, without any duplicates), using
     * each element's <tt>equals</tt> method to test for duplication.
     * @return the new ParallelArray
     */
    public ParallelArray<T> allUniqueElements() {
        return super.allUniqueElements();
    }

    /**
     * Returns a new ParallelArray containing only the non-null unique
     * elements of this array (that is, without any duplicates), using
     * reference identity to test for duplication.
     * @return the new ParallelArray
     */
    public ParallelArray<T> allNonidenticalElements() {
        return super.allNonidenticalElements();
    }

    /**
     * Removes from the array all elements for which the given
     * selector holds.
     * @param selector the selector
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> removeAll(Predicate<? super T> selector) {
        PAWithBoundedFilter<T> v = 
            new PAWithBoundedFilter<T>(ex, 0, upperBound, array, selector);
        PAS.FJRemoveAllDriver f = new PAS.FJRemoveAllDriver(v, 0, upperBound);
        ex.invoke(f);
        removeSlotsAt(f.offset, upperBound);
        return this;
    }

    /**
     * Removes consecutive elements that are equal (or null),
     * shifting others leftward, and possibly decreasing size.  This
     * method may be used after sorting to ensure that this
     * ParallelArray contains a set of unique elements.
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> removeConsecutiveDuplicates() {
        // Sequential implementation for now
        int k = 0;
        int n = upperBound;
        Object[] arr = this.array;
        Object last = null;
        for (int i = k; i < n; ++i) {
            Object x = arr[i];
            if (x != null && (last == null || !last.equals(x)))
                arr[k++] = last = x;
        }
        removeSlotsAt(k, n);
        return this;
    }

    /**
     * Removes null elements, shifting others leftward, and possibly
     * decreasing size.
     * @return this (to simplify use in expressions)
     */
    public ParallelArray<? extends T> removeNulls() {
        // Sequential implementation for now
        int k = 0;
        int n = upperBound;
        Object[] arr = this.array;
        for (int i = k; i < n; ++i) {
            Object x = arr[i];
            if (x != null)
                arr[k++] = x;
        }
        removeSlotsAt(k, n);
        return this;
    }

    /**
     * Returns an operation prefix that causes a method to
     * operate only on the elements of the array between
     * firstIndex (inclusive) and upperBound (exclusive).
     * @param firstIndex the lower bound (inclusive)
     * @param upperBound the upper bound (exclusive)
     * @return operation prefix
     */
    public ParallelArrayWithBounds<T> withBounds
        (int firstIndex, int upperBound) {
        return super.withBounds(firstIndex, upperBound);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * only on the elements of the array for which the given selector
     * returns true
     * @param selector the selector
     * @return operation prefix
     */
    public ParallelArrayWithFilter<T> withFilter(Predicate<? super T> selector) {
        return super.withFilter(selector);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public <U> ParallelArrayWithMapping<T, U> withMapping
        (Op<? super T, ? extends U> op) {
        return super.withMapping(op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectToDouble<? super T> op) {
        return super.withMapping(op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectToLong<? super T> op) {
        return super.withMapping(op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <U,V> ParallelArrayWithMapping<T,V> withMapping
        (BinaryOp<? super T, ? super U, ? extends V> combiner,
         ParallelArray<U> other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndDoubleToObject<? super T, ? extends V> combiner,
         ParallelDoubleArray other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndLongToObject<? super T, ? extends V> combiner,
         ParallelLongArray other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <U> ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndObjectToDouble<? super T, ? super U> combiner,
         ParallelArray<U> other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndDoubleToDouble<? super T> combiner,
         ParallelDoubleArray other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndLongToDouble<? super T> combiner,
         ParallelLongArray other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <U> ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndObjectToLong<? super T, ? super U> combiner,
         ParallelArray<U> other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndDoubleToLong<? super T> combiner,
         ParallelDoubleArray other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndLongToLong<? super T> combiner,
         ParallelLongArray other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate on
     * mappings of this array using the given mapper that accepts as
     * arguments an element's current index and value, and produces a
     * new value. Index-based mappings allow parallel computation of
     * many common array operations. For example, you could create
     * function to average the values at the same index of multiple
     * arrays and apply it using this method.
     * @param mapper the mapper
     * @return operation prefix
     */
    public <U> ParallelArrayWithMapping<T,U> withIndexedMapping
        (IntAndObjectToObject<? super T, ? extends U> mapper) {
        return super.withIndexedMapping(mapper);
    }

    /**
     * Returns an operation prefix that causes a method to operate on
     * mappings of this array using the given mapper that accepts as
     * arguments an element's current index and value, and produces a
     * new value.
     * @param mapper the mapper
     * @return operation prefix
     */
    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndObjectToDouble<? super T> mapper) {
        return super.withIndexedMapping(mapper);
    }

    /**
     * Returns an operation prefix that causes a method to operate on
     * mappings of this array using the given mapper that accepts as
     * arguments an element's current index and value, and produces a
     * new value.
     * @param mapper the mapper
     * @return operation prefix
     */
    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndObjectToLong<? super T> mapper) {
        return super.withIndexedMapping(mapper);
    }

    /**
     * Returns an iterator stepping through each element of the array
     * up to the current limit. This iterator does <em>not</em>
     * support the remove operation. However, a full
     * <tt>ListIterator</tt> supporting add, remove, and set
     * operations is available via {@link #asList}.
     * @return an iterator stepping through each element.
     */
    public Iterator<T> iterator() {
        return new ParallelArrayIterator<T>(array, upperBound);
    }

    static final class ParallelArrayIterator<T> implements Iterator<T> {
        int cursor;
        final T[] arr;
        final int hi;
        ParallelArrayIterator(T[] a, int limit) { arr = a; hi = limit; }
        public boolean hasNext() { return cursor < hi; }
        public T next() {
            if (cursor >= hi)
                throw new NoSuchElementException();
            return arr[cursor++];
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    // List support

    /**
     * Returns a view of this ParallelArray as a List. This List has
     * the same structural and performance characteristics as {@link
     * ArrayList}, and may be used to modify, replace or extend the
     * bounds of the array underlying this ParallelArray.  The methods
     * supported by this list view are <em>not</em> in general
     * implemented as parallel operations. This list is also not
     * itself thread-safe.  In particular, performing list updates
     * while other parallel operations are in progress has undefined
     * (and surely undesired) effects.
     * @return a list view
     */
    public List<T> asList() {
        AsList lv = listView;
        if (lv == null)
            listView = lv = new AsList();
        return lv;
    }

    /**
     * Returns the effective size of the underlying array. The
     * effective size is the current limit, if used (see {@link
     * #setLimit}), or the length of the array otherwise.
     * @return the effective size of array
     */
    public int size() { return upperBound; }

    /**
     * Returns the element of the array at the given index
     * @param i the index
     * @return the element of the array at the given index
     */
    public T get(int i) { return array[i]; }

    /**
     * Sets the element of the array at the given index to the given value
     * @param i the index
     * @param x the value
     */
    public void set(int i, T x) { array[i] = x; }

    /**
     * Returns the underlying array used for computations
     * @return the array
     */
    public T[] getArray() { return array; }

    /**
     * Equivalent to <tt>asList().toString()</tt>
     * @return a string representation
     */
    public String toString() {
        return asList().toString();
    }

    /**
     * Equivalent to <tt>asList().addAll</tt> but specialized for array
     * arguments and likely to be more efficient.
     * @param other the elements to add
     * @return this (to simplify use in expressions)
     */
    public  ParallelArray<? extends T> addAll(T[] other) {
        int csize = other.length;
        int end = upperBound;
        insertSlotsAt(end, csize);
        System.arraycopy(other, 0, array, end, csize);
        return this;
    }

    /**
     * Equivalent to <tt>asList().addAll</tt> but specialized for
     * ParallelArray arguments and likely to be more efficient.
     * @param other the elements to add
     * @return this (to simplify use in expressions)
     */
    public  ParallelArray<? extends T> addAll(ParallelArray<T> other) {
        int csize = other.size();
        int end = upperBound;
        insertSlotsAt(end, csize);
        if (!other.hasMap())
            System.arraycopy(other.array, 0, array, end, csize);
        else {
            int k = end;
            for (int i = other.firstIndex; i < other.upperBound; ++i)
                array[k++] = (T)(other.oget(i));
        }
        return this;
    }


    /**
     * Ensures that the underlying array can be accessed up to the
     * given upper bound, reallocating and copying the underlying
     * array to expand if necessary. Or, if the given limit is less
     * than the length of the underlying array, causes computations to
     * ignore elements past the given limit.
     * @param newLimit the new upper bound
     * @throws IllegalArgumentException if newLimit less than zero.
     */
    public final void setLimit(int newLimit) {
        if (newLimit < 0)
            throw new IllegalArgumentException();
        int cap = array.length;
        if (newLimit > cap)
            resizeArray(newLimit);
        upperBound = newLimit;
    }

    final void replaceElementsWith(T[] a) {
        System.arraycopy(a, 0, array, 0, a.length);
        upperBound = a.length;
    }

    final void resizeArray(int newCap) {
        int cap = array.length;
        if (newCap > cap) {
            Class elementType = array.getClass().getComponentType();
            T[] a =(T[])Array.newInstance(elementType, newCap);
            System.arraycopy(array, 0, a, 0, cap);
            array = a;
        }
    }

    final void insertElementAt(int index, T e) {
        int hi = upperBound++;
        if (hi >= array.length)
            resizeArray((hi * 3)/2 + 1);
        if (hi > index)
            System.arraycopy(array, index, array, index+1, hi - index);
        array[index] = e;
    }

    final void appendElement(T e) {
        int hi = upperBound++;
        if (hi >= array.length)
            resizeArray((hi * 3)/2 + 1);
        array[hi] = e;
    }

    /**
     * Make len slots available at index
     */
    final void insertSlotsAt(int index, int len) {
        if (len <= 0)
            return;
        int cap = array.length;
        int newSize = upperBound + len;
        if (cap < newSize) {
            cap = (cap * 3)/2 + 1;
            if (cap < newSize)
                cap = newSize;
            resizeArray(cap);
        }
        if (index < upperBound)
            System.arraycopy(array, index, array, index + len, upperBound - index);
        upperBound = newSize;
    }

    final void removeSlotAt(int index) {
        System.arraycopy(array, index + 1, array, index, upperBound - index - 1);
        array[--upperBound] = null;
    }

    final void removeSlotsAt(int fromIndex, int toIndex) {
        if (fromIndex < toIndex) {
            int size = upperBound;
            System.arraycopy(array, toIndex, array, fromIndex, size - toIndex);
            int newSize = size - (toIndex - fromIndex);
            upperBound = newSize;
            while (size > newSize)
                array[--size] = null;
        }
    }

    final int seqIndexOf(Object target) {
        T[] arr = array;
        int fence = upperBound;
        if (target == null) {
            for (int i = 0; i < fence; i++)
                if (arr[i] == null)
                    return i;
        } else {
            for (int i = 0; i < fence; i++)
                if (target.equals(arr[i]))
                    return i;
        }
        return -1;
    }

    final int seqLastIndexOf(Object target) {
        T[] arr = array;
        int last = upperBound - 1;
        if (target == null) {
            for (int i = last; i >= 0; i--)
                if (arr[i] == null)
                    return i;
        } else {
            for (int i = last; i >= 0; i--)
                if (target.equals(arr[i]))
                    return i;
        }
        return -1;
    }

    final class ListIter implements ListIterator<T> {
        int cursor;
        int lastRet;
        T[] arr; // cache array and bound
        int hi;
        ListIter(int lo) {
            this.cursor = lo;
            this.lastRet = -1;
            this.arr = ParallelArray.this.array;
            this.hi = ParallelArray.this.upperBound;
        }

        public boolean hasNext() {
            return cursor < hi;
        }

        public T next() {
            int i = cursor;
            if (i < 0 || i >= hi)
                throw new NoSuchElementException();
            T next = arr[i];
            lastRet = i;
            cursor = i + 1;
            return next;
        }

        public void remove() {
            int k = lastRet;
            if (k < 0)
                throw new IllegalStateException();
            ParallelArray.this.removeSlotAt(k);
            hi = ParallelArray.this.upperBound;
            if (lastRet < cursor)
                cursor--;
            lastRet = -1;
        }

        public boolean hasPrevious() {
            return cursor > 0;
        }

        public T previous() {
            int i = cursor - 1;
            if (i < 0 || i >= hi)
                throw new NoSuchElementException();
            T previous = arr[i];
            lastRet = cursor = i;
            return previous;
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor - 1;
        }

        public void set(T e) {
            int i = lastRet;
            if (i < 0 || i >= hi)
                throw new NoSuchElementException();
            arr[i] = e;
        }

        public void add(T e) {
            int i = cursor;
            ParallelArray.this.insertElementAt(i, e);
            arr = ParallelArray.this.array;
            hi = ParallelArray.this.upperBound;
            lastRet = -1;
            cursor = i + 1;
        }
    }

    final class AsList extends AbstractList<T> implements RandomAccess {
        public T get(int i) {
            if (i >= upperBound)
                throw new IndexOutOfBoundsException();
            return array[i];
        }

        public T set(int i, T x) {
            if (i >= upperBound)
                throw new IndexOutOfBoundsException();
            T[] arr = array;
            T t = arr[i];
            arr[i] = x;
            return t;
        }

        public boolean isEmpty() {
            return upperBound == 0;
        }

        public int size() {
            return upperBound;
        }

        public Iterator<T> iterator() {
            return new ListIter(0);
        }

        public ListIterator<T> listIterator() {
            return new ListIter(0);
        }

        public ListIterator<T> listIterator(int index) {
            if (index < 0 || index > upperBound)
                throw new IndexOutOfBoundsException();
            return new ListIter(index);
        }

        public boolean add(T e) {
            appendElement(e);
            return true;
        }

        public void add(int index, T e) {
            if (index < 0 || index > upperBound)
                throw new IndexOutOfBoundsException();
            insertElementAt(index, e);
        }

        public boolean addAll(Collection<? extends T> c) {
            int csize = c.size();
            if (csize == 0)
                return false;
            int hi = upperBound;
            setLimit(hi + csize);
            T[] arr = array;
            for (T e : c)
                arr[hi++] = e;
            return true;
        }

        public boolean addAll(int index, Collection<? extends T> c) {
            if (index < 0 || index > upperBound)
                throw new IndexOutOfBoundsException();
            int csize = c.size();
            if (csize == 0)
                return false;
            insertSlotsAt(index, csize);
            T[] arr = array;
            for (T e : c)
                arr[index++] = e;
            return true;
        }

        public void clear() {
            T[] arr = array;
            for (int i = 0; i < upperBound; ++i)
                arr[i] = null;
            upperBound = 0;
        }

        public boolean remove(Object o) {
            int idx = seqIndexOf(o);
            if (idx < 0)
                return false;
            removeSlotAt(idx);
            return true;
        }

        public T remove(int index) {
            T oldValue = get(index);
            removeSlotAt(index);
            return oldValue;
        }

        public void removeRange(int fromIndex, int toIndex) {
            removeSlotsAt(fromIndex, toIndex);
        }

        public boolean contains(Object o) {
            return seqIndexOf(o) >= 0;
        }

        public int indexOf(Object o) {
            return seqIndexOf(o);
        }

        public int lastIndexOf(Object o) {
            return seqLastIndexOf(o);
        }

        public Object[] toArray() {
            int len = upperBound;
            Object[] a = new Object[len];
            System.arraycopy(array, 0, a, 0, len);
            return a;
        }

        public <V> V[] toArray(V a[]) {
            int len = upperBound;
            if (a.length < len) {
                Class elementType = a.getClass().getComponentType();
                a =(V[])Array.newInstance(elementType, len);
            }
            System.arraycopy(array, 0, a, 0, len);
            if (a.length > len)
                a[len] = null;
            return a;
        }
    }
}

/**
 * A restriction of parallel array operations to apply only within
 * a given range of indices.
 */
class PAWithBounds<T> extends ParallelArrayWithBounds<T> {
    PAWithBounds
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array) {
        super(ex, firstIndex, upperBound, array);
    }

    /** Version for implementing main ParallelArray methods */
    PAWithBounds(ParallelArray<T> pa) {
        super(pa.ex, 0, pa.upperBound, pa.array);
    }

    final Object oget(int i) { return this.array[i]; }

    public ParallelArrayWithBounds<T> withBounds
        (int firstIndex, int upperBound) {
        if (firstIndex > upperBound)
            throw new IllegalArgumentException
                ("firstIndex(" + firstIndex +
                 ") > upperBound(" + upperBound+")");
        if (firstIndex < 0)
            throw new ArrayIndexOutOfBoundsException(firstIndex);
        if (upperBound - firstIndex > this.upperBound - this.firstIndex)
            throw new ArrayIndexOutOfBoundsException(upperBound);
        return new PAWithBounds<T>(ex,
                                 this.firstIndex + firstIndex,
                                 this.firstIndex + upperBound,
                                 array);
    }

    public ParallelArrayWithFilter<T> withFilter
        (Predicate<? super T> selector) {
        return new PAWithBoundedFilter<T>
            (ex, firstIndex, upperBound, array, selector);
    }

    public <U> ParallelArrayWithMapping<T, U> withMapping
        (Op<? super T, ? extends U> op) {
        return new PAWithBoundedMapping<T,U>
            (ex, firstIndex, upperBound, array, op);
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectToDouble<? super T> op) {
        return new PAWithBoundedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, op);
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectToLong<? super T> op) {
        return new PAWithBoundedLongMapping<T>
            (ex, firstIndex, upperBound, array, op);
    }

    public <U,V> ParallelArrayWithMapping<T,V> withMapping
        (BinaryOp<? super T, ? super U, ? extends V> combiner,
         ParallelArray<U> other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndDoubleToObject<? super T, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndLongToObject<? super T, ? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <U> ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndObjectToDouble<? super T, ? super U> combiner,
         ParallelArray<U> other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndDoubleToDouble<? super T> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndLongToDouble<? super T> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <U> ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndObjectToLong<? super T, ? super U> combiner,
         ParallelArray<U> other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndDoubleToLong<? super T> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndLongToLong<? super T> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndObjectToObject<? super T, ? extends V> mapper) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, mapper);
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndObjectToDouble<? super T> mapper) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, mapper);
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndObjectToLong<? super T> mapper) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, mapper);
    }

    public ParallelArrayWithFilter<T> orFilter(Predicate<? super T> selector) {
        return new PAWithBoundedFilter<T>
            (ex, firstIndex, upperBound, array, selector);
    }

    public ParallelArray<T> allUniqueElements() {
        PAS.OUniquifierTable tab = new PAS.OUniquifierTable
            (upperBound - firstIndex, this.array, null, false);
        PAS.FJUniquifier f = new PAS.FJUniquifier
            (this, firstIndex, upperBound, null, tab);
        ex.invoke(f);
        T[] res = (T[])(tab.uniqueElements(f.count));
        return new ParallelArray<T>(ex, res);
    }

    public ParallelArray<T> allNonidenticalElements() {
        PAS.OUniquifierTable tab = new PAS.OUniquifierTable
            (upperBound - firstIndex, this.array, null, true);
        PAS.FJUniquifier f = new PAS.FJUniquifier
            (this, firstIndex, upperBound, null, tab);
        ex.invoke(f);
        T[] res = (T[])(tab.uniqueElements(f.count));
        return new ParallelArray<T>(ex, res);
    }

    public int indexOf(T target) {
        AtomicInteger result = new AtomicInteger(-1);
        PAS.FJOIndexOf f = new PAS.FJOIndexOf
            (this, firstIndex, upperBound, null, result, target);
        ex.invoke(f);
        return result.get();
    }

    public int binarySearch(T target) {
        final Object[] a = this.array;
        int lo = firstIndex;
        int hi = upperBound - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = ((Comparable)target).compareTo((Comparable)a[mid]);
            if (c == 0)
                return mid;
            else if (c < 0)
                hi = mid - 1;
            else
                lo = mid + 1;
        }
        return -1;
    }

    public int binarySearch(T target, Comparator<? super T> comparator) {
        Comparator cmp = comparator;
        final Object[] a = this.array;
        int lo = firstIndex;
        int hi = upperBound - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = cmp.compare(target, a[mid]);
            if (c == 0)
                return mid;
            else if (c < 0)
                hi = mid - 1;
            else
                lo = mid + 1;
        }
        return -1;
    }

    public ParallelArrayWithBounds<? extends T> cumulate(Reducer<T> reducer, T base) {
        PAS.FJOCumulateOp op = new PAS.FJOCumulateOp(this, reducer, base);
        PAS.FJOScan r = new PAS.FJOScan(null, op, firstIndex, upperBound);
        ex.invoke(r);
        return this;
    }

    public T precumulate(Reducer<T> reducer, T base) {
        PAS.FJOPrecumulateOp op = new PAS.FJOPrecumulateOp
            (this, reducer, base);
        PAS.FJOScan r = new PAS.FJOScan(null, op, firstIndex, upperBound);
        ex.invoke(r);
        return (T)(r.out);
    }

    public ParallelArrayWithBounds<? extends T> sort(Comparator<? super T> cmp) {
        final Object[] a = this.array;
        Class tc = array.getClass().getComponentType();
        T[] ws = (T[])Array.newInstance(tc, upperBound);
        ex.invoke(new PAS.FJOSorter
                  (cmp, array, ws, firstIndex,
                   upperBound - firstIndex, getThreshold()));
        return this;
    }

    public ParallelArrayWithBounds<? extends T> sort() {
        final Object[] a = this.array;
        Class tc = array.getClass().getComponentType();
        if (!Comparable.class.isAssignableFrom(tc)) {
            sort(Ops.castedComparator());
        }
        else {
            Comparable[] ca = (Comparable[])array;
            Comparable[] ws = (Comparable[])Array.newInstance(tc, upperBound);
            ex.invoke(new PAS.FJOCSorter
                      (ca, ws, firstIndex,
                       upperBound - firstIndex, getThreshold()));
        }
        return this;
    }


    void leafApply(int lo, int hi, Procedure procedure) {
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(a[i]);
    }

    void leafCombine(int lo, int hi, Object[] other, int otherOffset,
                     Object[] dest, BinaryOp combiner) {
        final Object[] a = this.array;
        int k = lo - firstIndex;
        for (int i = lo; i < hi; ++i) {
            dest[k] = combiner.op(a[i], other[i + otherOffset]);
            ++k;
        }
    }

    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        if (lo >= hi)
            return base;
        final Object[] a = this.array;
        Object r = a[lo];
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, a[i]);
        return r;
    }

}

final class PAWithBoundedFilter<T> extends ParallelArrayWithFilter<T> {
    final Predicate<? super T> selector;
    PAWithBoundedFilter
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         Predicate<? super T> selector) {
        super(ex, firstIndex, upperBound, array);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    Predicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }
    Object oget(int i) { return this.array[i]; }

    public ParallelArrayWithFilter<T> withFilter
        (Predicate<? super T> selector) {
        return new PAWithBoundedFilter<T>
            (ex, firstIndex, upperBound, array,
             Ops.andPredicate(this.selector, selector));
    }

    public ParallelArrayWithFilter<T> orFilter(Predicate<? super T> selector) {
        return new PAWithBoundedFilter<T>
            (ex, firstIndex, upperBound, array, 
             Ops.orPredicate(this.selector, selector));
    }

    public <U> ParallelArrayWithMapping<T, U> withMapping
        (Op<? super T, ? extends U> op) {
        return new PAWithBoundedFilteredMapping<T,U>
            (ex, firstIndex, upperBound, array, selector, op);
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectToDouble<? super T> op) {
        return new PAWithBoundedFilteredDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector, op);
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectToLong<? super T> op) {
        return new PAWithBoundedFilteredLongMapping<T>
            (ex, firstIndex, upperBound, array, selector, op);
    }

    public <U,V> ParallelArrayWithMapping<T,V> withMapping
        (BinaryOp<? super T, ? super U, ? extends V> combiner,
         ParallelArray<U> other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndDoubleToObject<? super T, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndLongToObject<? super T, ? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <U> ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndObjectToDouble<? super T, ? super U> combiner,
         ParallelArray<U> other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndDoubleToDouble<? super T> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndLongToDouble<? super T> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <U> ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndObjectToLong<? super T, ? super U> combiner,
         ParallelArray<U> other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndDoubleToLong<? super T> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndLongToLong<? super T> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndObjectToObject<? super T, ? extends V> mapper) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector, mapper);
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndObjectToDouble<? super T> mapper) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector, mapper);
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndObjectToLong<? super T> mapper) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector, mapper);
    }

    public ParallelArray<T> allUniqueElements() {
        PAS.OUniquifierTable tab = new PAS.OUniquifierTable
            (upperBound - firstIndex, this.array, selector, false);
        PAS.FJUniquifier f = new PAS.FJUniquifier
            (this, firstIndex, upperBound, null, tab);
        ex.invoke(f);
        T[] res = (T[])(tab.uniqueElements(f.count));
        return new ParallelArray<T>(ex, res);
    }

    public ParallelArray<T> allNonidenticalElements() {
        PAS.OUniquifierTable tab = new PAS.OUniquifierTable
            (upperBound - firstIndex, this.array, selector, true);
        PAS.FJUniquifier f = new PAS.FJUniquifier
            (this, firstIndex, upperBound, null, tab);
        ex.invoke(f);
        T[] res = (T[])(tab.uniqueElements(f.count));
        return new ParallelArray<T>(ex, res);
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final Predicate s = selector;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            Object x = a[i];
            if (s.op(x))
                procedure.op(x);
        }
    }

    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        final Predicate s = selector;
        boolean gotFirst = false;
        Object r = base;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            Object x = a[i];
            if (s.op(x)) {
                if (!gotFirst) {
                    gotFirst = true;
                    r = x;
                }
                else
                    r = reducer.op(r, x);
            }
        }
        return r;
    }

}


abstract class PAWithMappingBase<T,U> extends ParallelArrayWithMapping<T,U> {
    final Op<? super T, ? extends U> op;
    PAWithMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         Op<? super T, ? extends U> op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final Object oget(int i) { return op.op(this.array[i]); }

    final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
        final Op f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                             Object[] dest, int offset) {
        final Object[] a = this.array;
        final Op f = op;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = f.op(a[indices[i]]);
    }
}

final class PAWithBoundedMapping<T,U> extends PAWithMappingBase<T,U> {
    PAWithBoundedMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         Op<? super T, ? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public <V> ParallelArrayWithMapping<T, V> withMapping
        (Op<? super U, ? extends V> op) {
        return new PAWithBoundedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectToDouble<? super U> op) {
        return new PAWithBoundedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectToLong<? super U> op) {
        return new PAWithBoundedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final Op f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(a[i]));
    }

    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        if (lo >= hi)
            return base;
        final Object[] a = this.array;
        final Op f = op;
        Object r = f.op(a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(a[i]));
        return r;
    }

}

final class PAWithBoundedFilteredMapping<T,U>
    extends PAWithMappingBase<T,U> {
    final Predicate<? super T> selector;

    PAWithBoundedFilteredMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         Predicate<? super T> selector,
         Op<? super T, ? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    Predicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public <V> ParallelArrayWithMapping<T, V> withMapping
        (Op<? super U, ? extends V> op) {
        return new PAWithBoundedFilteredMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping(ObjectToDouble<? super U> op) {
        return new PAWithBoundedFilteredDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping(ObjectToLong<? super U> op) {
        return new PAWithBoundedFilteredLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final Predicate s = selector;
        final Object[] a = this.array;
        final Op f = op;
        for (int i = lo; i < hi; ++i) {
            Object x = a[i];
            if (s.op(x))
                procedure.op(f.op(x));
        }
    }
    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        final Predicate s = selector;
        final Object[] a = this.array;
        final Op f = op;
        boolean gotFirst = false;
        Object r = base;
        for (int i = lo; i < hi; ++i) {
            Object x = a[i];
            if (s.op(x)) {
                Object y = f.op(x);
                if (!gotFirst) {
                    gotFirst = true;
                    r = y;
                }
                else
                    r = reducer.op(r, y);
            }
        }
        return r;
    }

}

abstract class PAWithIndexedMappingBase<T,U> extends ParallelArrayWithMapping<T,U> {
    final IntAndObjectToObject<? super T, ? extends U> op;
    PAWithIndexedMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         IntAndObjectToObject<? super T, ? extends U> op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final Object oget(int i) { return op.op(i, this.array[i]); }

    final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
        final IntAndObjectToObject f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(i, a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                             Object[] dest, int offset) {
        final Object[] a = this.array;
        final IntAndObjectToObject f = op;
        for (int i = loIdx; i < hiIdx; ++i) {
            int idx = indices[i];
            dest[offset++] = f.op(idx, a[idx]);
        }
    }
}

final class PAWithBoundedIndexedMapping<T,U>
    extends PAWithIndexedMappingBase<T,U> {
    PAWithBoundedIndexedMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         IntAndObjectToObject<? super T, ? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public <V> ParallelArrayWithMapping<T, V> withMapping
        (Op<? super U, ? extends V> op) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectToDouble<? super U> op) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectToLong<? super U> op) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final IntAndObjectToObject f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(i, a[i]));
    }

    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        if (lo >= hi)
            return base;
        final Object[] a = this.array;
        final IntAndObjectToObject f = op;
        Object r = f.op(lo, a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(i, a[i]));
        return r;
    }

}

final class PAWithBoundedFilteredIndexedMapping<T,U>
    extends PAWithIndexedMappingBase<T,U> {
    final Predicate<? super T> selector;
    PAWithBoundedFilteredIndexedMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         Predicate<? super T> selector,
         IntAndObjectToObject<? super T, ? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    Predicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public <V> ParallelArrayWithMapping<T, V> withMapping
        (Op<? super U, ? extends V> op) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping(ObjectToDouble<? super U> op) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping(ObjectToLong<? super U> op) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final Predicate s = selector;
        final Object[] a = this.array;
        final IntAndObjectToObject f = op;
        for (int i = lo; i < hi; ++i) {
            Object x = a[i];
            if (s.op(x))
                procedure.op(f.op(i, x));
        }
    }
    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        final Predicate s = selector;
        final Object[] a = this.array;
        final IntAndObjectToObject f = op;
        boolean gotFirst = false;
        Object r = base;
        for (int i = lo; i < hi; ++i) {
            Object x = a[i];
            if (s.op(x)) {
                Object y = f.op(i, x);
                if (!gotFirst) {
                    gotFirst = true;
                    r = y;
                }
                else
                    r = reducer.op(r, y);
            }
        }
        return r;
    }
}

abstract class PAWithDoubleMappingBase<T>
    extends ParallelArrayWithDoubleMapping<T> {
    final ObjectToDouble<? super T> op;
    PAWithDoubleMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         ObjectToDouble<? super T> op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final double dget(int i) { return op.op(this.array[i]); }
    final Object oget(int i) { return Double.valueOf(dget(i)); }

    final void leafTransfer(int lo, int hi, double[] dest, int offset) {
        final ObjectToDouble f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   double[] dest, int offset) {
        final Object[] a = this.array;
        final ObjectToDouble f = op;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = f.op(a[indices[i]]);
    }

}

final class PAWithBoundedDoubleMapping<T>
    extends PAWithDoubleMappingBase<T> {
    PAWithBoundedDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         ObjectToDouble<? super T> op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelArrayWithDoubleMapping<T> withMapping(DoubleOp op) {
        return new PAWithBoundedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping(DoubleToLong op) {
        return new PAWithBoundedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelArrayWithMapping<T, U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PAWithBoundedMapping<T,U>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final ObjectToDouble f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(a[i]));
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        if (lo >= hi)
            return base;
        final Object[] a = this.array;
        final ObjectToDouble f = op;
        double r = f.op(a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(a[i]));
        return r;
    }

}

final class PAWithBoundedFilteredDoubleMapping<T>
    extends PAWithDoubleMappingBase<T> {
    final Predicate<? super T> selector;
    PAWithBoundedFilteredDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         Predicate<? super T> selector, ObjectToDouble<? super T> op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    Predicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelArrayWithDoubleMapping<T> withMapping(DoubleOp op) {
        return new PAWithBoundedFilteredDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping(DoubleToLong op) {
        return new PAWithBoundedFilteredLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelArrayWithMapping<T, U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PAWithBoundedFilteredMapping<T,U>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final Predicate s = selector;
        final Object[] a = this.array;
        final ObjectToDouble f = op;
        for (int i = lo; i < hi; ++i) {
            Object x = a[i];
            if (s.op(x))
                procedure.op(f.op(x));
        }
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        final Predicate s = selector;
        final ObjectToDouble f = op;
        boolean gotFirst = false;
        double r = base;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            Object t = a[i];
            if (s.op(t)) {
                double y = f.op(t);
                if (!gotFirst) {
                    gotFirst = true;
                    r = y;
                }
                else
                    r = reducer.op(r, y);
            }
        }
        return r;
    }
}

abstract class PAWithIndexedDoubleMappingBase<T>
    extends ParallelArrayWithDoubleMapping<T> {
    final IntAndObjectToDouble<? super T> op;
    PAWithIndexedDoubleMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         IntAndObjectToDouble<? super T> op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final double dget(int i) { return op.op(i, this.array[i]); }
    final Object oget(int i) { return Double.valueOf(dget(i)); }

    final void leafTransfer(int lo, int hi, double[] dest, int offset) {
        final IntAndObjectToDouble f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(i, a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   double[] dest, int offset) {
        final Object[] a = this.array;
        final IntAndObjectToDouble f = op;
        for (int i = loIdx; i < hiIdx; ++i) {
            int idx = indices[i];
            dest[offset++] = f.op(idx, a[idx]);
        }
    }

}

final class PAWithBoundedIndexedDoubleMapping<T>
    extends PAWithIndexedDoubleMappingBase<T> {
    PAWithBoundedIndexedDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         IntAndObjectToDouble<? super T> op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelArrayWithDoubleMapping<T> withMapping(DoubleOp op) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping(DoubleToLong op) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelArrayWithMapping<T, U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PAWithBoundedIndexedMapping<T,U>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final IntAndObjectToDouble f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(i, a[i]));
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        if (lo >= hi)
            return base;
        final Object[] a = this.array;
        final IntAndObjectToDouble f = op;
        double r = f.op(lo, a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(i, a[i]));
        return r;
    }

}

final class PAWithBoundedFilteredIndexedDoubleMapping<T>
    extends PAWithIndexedDoubleMappingBase<T> {
    final Predicate<? super T> selector;
    PAWithBoundedFilteredIndexedDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         Predicate<? super T> selector,
         IntAndObjectToDouble<? super T> op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    Predicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelArrayWithDoubleMapping<T> withMapping(DoubleOp op) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping(DoubleToLong op) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelArrayWithMapping<T, U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PAWithBoundedFilteredIndexedMapping<T,U>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final Predicate s = selector;
        final Object[] a = this.array;
        final IntAndObjectToDouble f = op;
        for (int i = lo; i < hi; ++i) {
            Object x = a[i];
            if (s.op(x))
                procedure.op(f.op(i, x));
        }
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        final Predicate s = selector;
        final IntAndObjectToDouble f = op;
        boolean gotFirst = false;
        double r = base;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            Object t = a[i];
            if (s.op(t)) {
                double y = f.op(i, t);
                if (!gotFirst) {
                    gotFirst = true;
                    r = y;
                }
                else
                    r = reducer.op(r, y);
            }
        }
        return r;
    }
}

abstract class PAWithLongMappingBase<T> 
    extends ParallelArrayWithLongMapping<T> {
    final ObjectToLong<? super T> op;
    PAWithLongMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         final ObjectToLong<? super T> op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final long lget(int i) { return op.op(this.array[i]); }
    final Object oget(int i) { return Long.valueOf(lget(i)); }

    final void leafTransfer(int lo, int hi, long[] dest, int offset) {
        final ObjectToLong f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   long[] dest, int offset) {
        final Object[] a = this.array;
        final ObjectToLong f = op;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = f.op(a[indices[i]]);
    }
}

final class PAWithBoundedLongMapping<T>
    extends PAWithLongMappingBase<T> {
    PAWithBoundedLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
                           ObjectToLong<? super T> op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelArrayWithDoubleMapping<T> withMapping(LongToDouble op) {
        return new PAWithBoundedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping(LongOp op) {
        return new PAWithBoundedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelArrayWithMapping<T, U> withMapping(LongToObject<? extends U> op) {
        return new PAWithBoundedMapping<T,U>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final Object[] a = this.array;
        final ObjectToLong f = op;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(a[i]));
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        if (lo >= hi)
            return base;
        final Object[] a = this.array;
        final ObjectToLong f = op;
        long r = f.op(a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(a[i]));
        return r;
    }

}

final class PAWithBoundedFilteredLongMapping<T>
    extends PAWithLongMappingBase<T> {
    final Predicate<? super T> selector;
    PAWithBoundedFilteredLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         Predicate<? super T> selector, ObjectToLong<? super T> op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    Predicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (LongToDouble op) {
        return new PAWithBoundedFilteredDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (LongOp op) {
        return new PAWithBoundedFilteredLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelArrayWithMapping<T, U> withMapping
        (LongToObject<? extends U> op) {
        return new PAWithBoundedFilteredMapping<T,U>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final Predicate s = selector;
        final Object[] a = this.array;
        final ObjectToLong f = op;
        for (int i = lo; i < hi; ++i) {
            Object x = a[i];
            if (s.op(x))
                procedure.op(f.op(x));
        }
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        final Predicate s = selector;
        final ObjectToLong f = op;
        boolean gotFirst = false;
        long r = base;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            Object t = a[i];
            if (s.op(t)) {
                long y = f.op(t);
                if (!gotFirst) {
                    gotFirst = true;
                    r = y;
                }
                else
                    r = reducer.op(r, y);
            }
        }
        return r;
    }

}

abstract class PAWithIndexedLongMappingBase<T>
    extends ParallelArrayWithLongMapping<T> {
    final IntAndObjectToLong<? super T> op;
    PAWithIndexedLongMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         IntAndObjectToLong<? super T> op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final long lget(int i) { return op.op(i, this.array[i]); }
    final Object oget(int i) { return Long.valueOf(lget(i)); }

    final void leafTransfer(int lo, int hi, long[] dest, int offset) {
        final IntAndObjectToLong f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(i, a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   long[] dest, int offset) {
        final Object[] a = this.array;
        final IntAndObjectToLong f = op;
        for (int i = loIdx; i < hiIdx; ++i) {
            int idx = indices[i];
            dest[offset++] = f.op(idx, a[idx]);
        }
    }

}

final class PAWithBoundedIndexedLongMapping<T>
    extends PAWithIndexedLongMappingBase<T> {
    PAWithBoundedIndexedLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         IntAndObjectToLong<? super T> op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelArrayWithDoubleMapping<T> withMapping(LongToDouble op) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping(LongOp op) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelArrayWithMapping<T, U> withMapping
        (LongToObject<? extends U> op) {
        return new PAWithBoundedIndexedMapping<T,U>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PAWithBoundedIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PAWithBoundedIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PAWithBoundedIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final IntAndObjectToLong f = op;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(i, a[i]));
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        if (lo >= hi)
            return base;
        final Object[] a = this.array;
        final IntAndObjectToLong f = op;
        long r = f.op(lo, a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(i, a[i]));
        return r;
    }

}

final class PAWithBoundedFilteredIndexedLongMapping<T>
    extends PAWithIndexedLongMappingBase<T> {
    final Predicate<? super T> selector;
    PAWithBoundedFilteredIndexedLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array,
         Predicate<? super T> selector,
         IntAndObjectToLong<? super T> op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    Predicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelArrayWithDoubleMapping<T> withMapping(LongToDouble op) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelArrayWithLongMapping<T> withMapping(LongOp op) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelArrayWithMapping<T, U> withMapping
        (LongToObject<? extends U> op) {
        return new PAWithBoundedFilteredIndexedMapping<T,U>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelArrayWithMapping<T,W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedMapping<T,W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithDoubleMapping<T> withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithLongMapping<T> withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelArrayWithLongMapping<T> withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelArrayWithMapping<T,V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PAWithBoundedFilteredIndexedMapping<T,V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithDoubleMapping<T> withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PAWithBoundedFilteredIndexedDoubleMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelArrayWithLongMapping<T> withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PAWithBoundedFilteredIndexedLongMapping<T>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final Predicate s = selector;
        final Object[] a = this.array;
        final IntAndObjectToLong f = op;
        for (int i = lo; i < hi; ++i) {
            Object x = a[i];
            if (s.op(x))
                procedure.op(f.op(i, x));
        }
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        final Predicate s = selector;
        final IntAndObjectToLong f = op;
        boolean gotFirst = false;
        long r = base;
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            Object t = a[i];
            if (s.op(t)) {
                long y = f.op(i, t);
                if (!gotFirst) {
                    gotFirst = true;
                    r = y;
                }
                else
                    r = reducer.op(r, y);
            }
        }
        return r;
    }
}

