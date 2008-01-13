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
public class ParallelArray<T> implements Iterable<T> {
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

    /** The executor */
    final ForkJoinExecutor ex;
    /** The array -- can be replaced within AsList operations */
    T[] array;
    /** upper bound of effective region usef dor parallel operations */
    int limit;
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
        if (executor == null || array == null)
            throw new NullPointerException();
        if (limit < 0 || limit > array.length)
            throw new IllegalArgumentException();
        this.ex = executor;
        this.array = array;
        this.limit = limit;
    }

    /**
     * Trusted internal version of protected constructor.
     */
    ParallelArray(ForkJoinExecutor executor, T[] array) {
        this.ex = executor;
        this.array = array;
        this.limit = array.length;
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
        new WithBounds<T>(this).apply(procedure);
    }

    /**
     * Returns reduction of elements
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public T reduce(Reducer<T> reducer, T base) {
        return new WithBounds<T>(this).reduce(reducer, base);
    }

    /**
     * Returns a new ParallelArray holding all elements
     * @return a new ParallelArray holding all elements
     */
    public ParallelArray<T> all() {
        return new WithBounds<T>(this).all();
    }

    /**
     * Returns a new ParallelArray with the given element type holding
     * all elements
     * @param elementType the type of the elements
     * @return a new ParallelArray holding all elements
     */
    public ParallelArray<T> all(Class<? super T> elementType) {
        return new WithBounds<T>(this).all(elementType);
    }

    /**
     * Replaces elements with the results of applying the given transform
     * to their current values.
     * @param op the op
     */
    public void replaceWithMapping(Op<? super T, ? extends T> op) {
        new WithBounds<T>(this).replaceWithMapping(op);
    }

    /**
     * Replaces elements with the results of applying the given
     * mapping to their indices.
     * @param op the op
     */
    public void replaceWithMappedIndex(IntToObject<? extends T> op) {
        new WithBounds<T>(this).replaceWithMappedIndex(op);
    }

    /**
     * Replaces elements with the results of applying the given
     * generator.
     * @param generator the generator
     */
    public void replaceWithGeneratedValue(Generator<? extends T> generator) {
        new WithBounds<T>(this).replaceWithGeneratedValue(generator);
    }

    /**
     * Replaces elements with the given value.
     * @param value the value
     */
    public void replaceWithValue(T value) {
        new WithBounds<T>(this).replaceWithValue(value);
    }

    /**
     * Replaces elements with results of applying
     * <tt>op(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @throws ArrayIndexOutOfBoundsException if other array has
     * fewer elements than this array.
     */
    public void replaceWithMapping(BinaryOp<T,T,T> combiner,
                                   ParallelArray<? extends T> other) {
        new WithBounds<T>(this).replaceWithMapping(combiner, other.array);
    }

    /**
     * Replaces elements with results of applying
     * <tt>op(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @throws ArrayIndexOutOfBoundsException if other array has
     * fewer elements than this array.
     */
    public void replaceWithMapping(BinaryOp<T,T,T> combiner, T[] other) {
        new WithBounds<T>(this).replaceWithMapping(combiner, other);
    }

    /**
     * Replaces elements with results of applying
     * <tt>op(thisElement, otherElement)</tt>
     * @param other the other array segment
     * @param combiner the combiner
     * @throws ArrayIndexOutOfBoundsException if other segment has
     * fewer elements.than this array,
     */
    public void replaceWithMapping(BinaryOp<T,T,T> combiner,
                                   ParallelArray.WithBounds<? extends T> other){
        new WithBounds<T>(this).replaceWithMapping(combiner, other);
    }

    /**
     * Returns the index of some element equal to given target,
     * or -1 if not present
     * @param target the element to search for
     * @return the index or -1 if not present
     */
    public int indexOf(T target) {
        return new WithBounds<T>(this).indexOf(target);
    }

    /**
     * Assuming this array is sorted, returns the index of an element
     * equal to given target, or -1 if not present. If the array
     * is not sorted, the results are undefined.
     * @param target the element to search for
     * @return the index or -1 if not present
     */
    public int binarySearch(T target) {
        int lo = 0;
        int hi = limit - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = ((Comparable)target).compareTo((Comparable)(array[mid]));
            if (c == 0)
                return mid;
            else if (c < 0)
                hi = mid - 1;
            else
                lo = mid + 1;
        }
        return -1;
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
        int lo = 0;
        int hi = limit - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = comparator.compare(target, array[mid]);
            if (c == 0)
                return mid;
            else if (c < 0)
                hi = mid - 1;
            else
                lo = mid + 1;
        }
        return -1;
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
        return new WithBounds<T>(this).summary(comparator);
    }

    /**
     * Returns summary statistics, assuming that all elements are
     * Comparables
     * @return the summary.
     */
    public ParallelArray.SummaryStatistics<T> summary() {
        return new WithBounds<T>(this).summary();
    }

    /**
     * Returns the minimum element, or null if empty
     * @param comparator the comparator
     * @return minimum element, or null if empty
     */
    public T min(Comparator<? super T> comparator) {
        return new WithBounds<T>(this).min(comparator);
    }

    /**
     * Returns the minimum element, or null if empty,
     * assuming that all elements are Comparables
     * @return minimum element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public T min() {
        return new WithBounds<T>(this).min();
    }

    /**
     * Returns the maximum element, or null if empty
     * @param comparator the comparator
     * @return maximum element, or null if empty
     */
    public T max(Comparator<? super T> comparator) {
        return new WithBounds<T>(this).max(comparator);
    }

    /**
     * Returns the maximum element, or null if empty
     * assuming that all elements are Comparables
     * @return maximum element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public T max() {
        return new WithBounds<T>(this).max();
    }

    /**
     * Replaces each element with the running cumulation of applying
     * the given reducer. For example, if the contents are the numbers
     * <tt>1, 2, 3</tt>, and the reducer operation adds numbers, then
     * after invocation of this method, the contents would be <tt>1,
     * 3, 6</tt> (that is, <tt>1, 1+2, 1+2+3</tt>);
     * @param reducer the reducer
     * @param base the result for an empty array
     */
    public void cumulate(Reducer<T> reducer, T base) {
        new WithBounds<T>(this).cumulate(reducer, base);
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
        return (T)(new WithBounds<T>(this).precumulate(reducer, base));
    }

    /**
     * Sorts the array. Unlike Arrays.sort, this sort does
     * not guarantee that elements with equal keys maintain their
     * relative position in the array.
     * @param comparator the comparator to use
     */
    public void sort(Comparator<? super T> comparator) {
        new WithBounds<T>(this).sort(comparator);
    }

    /**
     * Sorts the array, assuming all elements are Comparable. Unlike
     * Arrays.sort, this sort does not guarantee that elements
     * with equal keys maintain their relative position in the array.
     * @throws ClassCastException if any element is not Comparable.
     */
    public void sort() {
        new WithBounds<T>(this).sort();
    }

    /**
     * Removes consecutive elements that are equal (or null), shifting
     * others leftward, and possibly decreasing size.  This method
     * uses each non-null element's <tt>equals</tt> method to test for
     * duplication. This method may be used after sorting to ensure
     * that this ParallelArray contains a set of unique elements.
     */
    public void removeConsecutiveDuplicates() {
        new WithBounds<T>(this).removeConsecutiveDuplicates();
    }

    /**
     * Removes null elements, shifting others leftward, and possibly
     * decreasing size.
     */
    public void removeNulls() {
        new WithBounds<T>(this).removeNulls();
    }

    /**
     * Returns a new ParallelArray containing only the non-null unique
     * elements of this array (that is, without any duplicates), using
     * each element's <tt>equals</tt> method to test for duplication.
     * @return the new ParallelArray
     */
    public ParallelArray<T> allUniqueElements() {
        return new WithBounds<T>(this).allUniqueElements();
    }

    /**
     * Returns a new ParallelArray containing only the non-null unique
     * elements of this array (that is, without any duplicates), using
     * reference identity to test for duplication.
     * @return the new ParallelArray
     */
    public ParallelArray<T> allNonidenticalElements() {
        return new WithBounds<T>(this).allNonidenticalElements();
    }

    /**
     * Returns an operation prefix that causes a method to
     * operate only on the elements of the array between
     * firstIndex (inclusive) and upperBound (exclusive).
     * @param firstIndex the lower bound (inclusive)
     * @param upperBound the upper bound (exclusive)
     * @return operation prefix
     */
    public WithBounds<T> withBounds(int firstIndex, int upperBound) {
        if (firstIndex > upperBound)
            throw new IllegalArgumentException
                ("firstIndex(" + firstIndex +
                 ") > upperBound(" + upperBound+")");
        if (firstIndex < 0)
            throw new ArrayIndexOutOfBoundsException(firstIndex);
        if (upperBound > this.limit)
            throw new ArrayIndexOutOfBoundsException(upperBound);
        return new WithBounds<T>(this, firstIndex, upperBound);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * only on the elements of the array for which the given selector
     * returns true
     * @param selector the selector
     * @return operation prefix
     */
    public WithFilter<T> withFilter(Predicate<? super T> selector) {
        return new WithBoundedFilter<T>(this, 0, limit, selector);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public <U> WithMapping<T, U> withMapping(Op<? super T, ? extends U> op) {
        return new WithBoundedMapping<T,U>(this, 0, limit, op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public WithDoubleMapping<T> withMapping(ObjectToDouble<? super T> op) {
        return new WithBoundedDoubleMapping<T>(this, 0, limit, op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public WithLongMapping<T> withMapping(ObjectToLong<? super T> op) {
        return new WithBoundedLongMapping<T>(this, 0, limit, op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <U,V> WithMapping<T,V> withMapping
        (BinaryOp<? super T, ? super U, ? extends V> combiner,
         ParallelArray<U> other) {
        return new WithBoundedIndexedMapping<T,V>
            (this, 0, limit,
             PAS.indexedMapper(combiner, other.array, 0));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <V> WithMapping<T,V> withMapping
        (ObjectAndDoubleToObject<? super T, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new WithBoundedIndexedMapping<T,V>
            (this, 0, limit,
             PAS.indexedMapper(combiner, other.array, 0));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <V> WithMapping<T,V> withMapping
        (ObjectAndLongToObject<? super T, ? extends V> combiner,
         ParallelLongArray other) {
        return new WithBoundedIndexedMapping<T,V>
            (this, 0, limit,
             PAS.indexedMapper(combiner, other.array, 0));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <U> WithDoubleMapping<T> withMapping
        (ObjectAndObjectToDouble<? super T, ? super U> combiner,
         ParallelArray<U> other) {
        return new WithBoundedIndexedDoubleMapping<T>
            (this, 0, limit,
             PAS.indexedMapper(combiner, other.array, 0));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public WithDoubleMapping<T> withMapping
        (ObjectAndDoubleToDouble<? super T> combiner,
         ParallelDoubleArray other) {
        return new WithBoundedIndexedDoubleMapping<T>
            (this, 0, limit,
             PAS.indexedMapper(combiner, other.array, 0));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public WithDoubleMapping<T> withMapping
        (ObjectAndLongToDouble<? super T> combiner,
         ParallelLongArray other) {
        return new WithBoundedIndexedDoubleMapping<T>
            (this, 0, limit,
             PAS.indexedMapper(combiner, other.array, 0));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <U> WithLongMapping<T> withMapping
        (ObjectAndObjectToLong<? super T, ? super U> combiner,
         ParallelArray<U> other) {
        return new WithBoundedIndexedLongMapping<T>
            (this, 0, limit,
             PAS.indexedMapper(combiner, other.array, 0));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public WithLongMapping<T> withMapping
        (ObjectAndDoubleToLong<? super T> combiner,
         ParallelDoubleArray other) {
        return new WithBoundedIndexedLongMapping<T>
            (this, 0, limit,
             PAS.indexedMapper(combiner, other.array, 0));
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public WithLongMapping<T> withMapping
        (ObjectAndLongToLong<? super T> combiner,
         ParallelLongArray other) {
        return new WithBoundedIndexedLongMapping<T>
            (this, 0, limit,
             PAS.indexedMapper(combiner, other.array, 0));
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
    public <U> WithMapping<T,U> withIndexedMapping
        (IntAndObjectToObject<? super T, ? extends U> mapper) {
        return new WithBoundedIndexedMapping<T,U>(this, 0, limit, mapper);
    }

    /**
     * Returns an operation prefix that causes a method to operate on
     * mappings of this array using the given mapper that accepts as
     * arguments an element's current index and value, and produces a
     * new value.
     * @param mapper the mapper
     * @return operation prefix
     */
    public WithDoubleMapping<T> withIndexedMapping
        (IntAndObjectToDouble<? super T> mapper) {
        return new WithBoundedIndexedDoubleMapping<T>(this, 0, limit, mapper);
    }

    /**
     * Returns an operation prefix that causes a method to operate on
     * mappings of this array using the given mapper that accepts as
     * arguments an element's current index and value, and produces a
     * new value.
     * @param mapper the mapper
     * @return operation prefix
     */
    public WithLongMapping<T> withIndexedMapping
        (IntAndObjectToLong<? super T> mapper) {
        return new WithBoundedIndexedLongMapping<T>(this, 0, limit, mapper);
    }

    /**
     * A modifier for parallel array operations to apply to mappings
     * of elements, not to the elements themselves
     */
    public static abstract class WithMapping<T,U> extends PAS.OPrefix<T> {
        WithMapping(ParallelArray<T> pa, int firstIndex, int upperBound) {
            super(pa, firstIndex, upperBound);
        }

        /**
         * Applies the given procedure to elements
         * @param procedure the procedure
         */
        public void apply(Procedure<? super U> procedure) {
            ex.invoke(new PAS.FJOApply(this, firstIndex, upperBound, null,
                                       procedure));
        }

        /**
         * Returns reduction of elements
         * @param reducer the reducer
         * @param base the result for an empty array
         * @return reduction
         */
        public U reduce(Reducer<U> reducer, U base) {
            PAS.FJOReduce f = new PAS.FJOReduce
                (this, firstIndex, upperBound, null, reducer, base);
            ex.invoke(f);
            return (U)(f.result);
        }

        /**
         * Returns the index of some element matching bound and filter
         * constraints, or -1 if none.
         * @return index of matching element, or -1 if none.
         */
        public int anyIndex() {
            return super.computeAnyIndex();
        }

        /**
         * Returns some element matching bound and filter
         * constraints, or null if none.
         * @return an element, or null if none.
         */
        public U any() {
            int i = super.computeAnyIndex();
            return (i < 0)? null : (U)oget(i);
        }

        /**
         * Returns the minimum element, or null if empty
         * @param comparator the comparator
         * @return minimum element, or null if empty
         */
        public U min(Comparator<? super U> comparator) {
            return reduce(Ops.<U>minReducer(comparator), null);
        }

        /**
         * Returns the minimum element, or null if empty,
         * assuming that all elements are Comparables
         * @return minimum element, or null if empty
         * @throws ClassCastException if any element is not Comparable.
         */
        public U min() {
            return reduce((Reducer<U>)(Ops.castedMinReducer()), null);
        }

        /**
         * Returns the maximum element, or null if empty
         * @param comparator the comparator
         * @return maximum element, or null if empty
         */
        public U max(Comparator<? super U> comparator) {
            return reduce(Ops.<U>maxReducer(comparator), null);
        }

        /**
         * Returns the maximum element, or null if empty
         * assuming that all elements are Comparables
         * @return maximum element, or null if empty
         * @throws ClassCastException if any element is not Comparable.
         */
        public U max() {
            return reduce((Reducer<U>)(Ops.castedMaxReducer()), null);
        }

        /**
         * Returns summary statistics, using the given comparator
         * to locate minimum and maximum elements.
         * @param comparator the comparator to use for
         * locating minimum and maximum elements
         * @return the summary.
         */
        public ParallelArray.SummaryStatistics<U> summary
            (Comparator<? super U> comparator) {
            PAS.FJOStats f = new PAS.FJOStats
                (this, firstIndex, upperBound, null, comparator);
            ex.invoke(f);
            return (ParallelArray.SummaryStatistics<U>)f;
        }

        /**
         * Returns summary statistics, assuming that all elements are
         * Comparables
         * @return the summary.
         */
        public ParallelArray.SummaryStatistics<U> summary() {
            PAS.FJOStats f = new PAS.FJOStats
                (this, firstIndex, upperBound, null,
                 (Comparator<? super U>)(Ops.castedComparator()));
            ex.invoke(f);
            return (ParallelArray.SummaryStatistics<U>)f;
        }

        /**
         * Returns a new ParallelArray holding elements
         * @return a new ParallelArray holding elements
         */
        public ParallelArray<U> all() {
            return new ParallelArray<U>(ex, (U[])allObjects(null));
        }

        /**
         * Returns a new ParallelArray with the given element type
         * holding elements
         * @param elementType the type of the elements
         * @return a new ParallelArray holding elements
         */
        public ParallelArray<U> all(Class<? super U> elementType) {
            return new ParallelArray<U>(ex, (U[])allObjects(null));
        }

        /**
         * Return the number of elements selected using bound or
         * filter restrictions. Note that this method must evaluate
         * all selectors to return its result.
         * @return the number of elements
         */
        public int size() {
            return super.computeSize();
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given op
         * applied to current op's results
         * @param op the op
         * @return operation prefix
         */
        public abstract <V> WithMapping<T, V> withMapping
            (Op<? super U, ? extends V> op);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given op
         * applied to current op's results
         * @param op the op
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withMapping
            (ObjectToDouble<? super U> op);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given op
         * applied to current op's results
         * @param op the op
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withMapping
            (ObjectToLong<? super U> op);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V,W> WithMapping<T,W> withMapping
            (BinaryOp<? super U, ? super V, ? extends W> combiner,
             ParallelArray<V> other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithMapping<T,V> withMapping
            (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
             ParallelDoubleArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithMapping<T,V> withMapping
            (ObjectAndLongToObject<? super U, ? extends V> combiner,
             ParallelLongArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithDoubleMapping<T> withMapping
            (ObjectAndObjectToDouble<? super U, ? super V> combiner,
             ParallelArray<V> other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withMapping
            (ObjectAndDoubleToDouble<? super U> combiner,
             ParallelDoubleArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withMapping
            (ObjectAndLongToDouble<? super U> combiner,
             ParallelLongArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithLongMapping<T> withMapping
            (ObjectAndObjectToLong<? super U, ? super V> combiner,
             ParallelArray<V> other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withMapping
            (ObjectAndDoubleToLong<? super U> combiner,
             ParallelDoubleArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withMapping
            (ObjectAndLongToLong<? super U> combiner,
             ParallelLongArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mappings of this array using the given mapper that
         * accepts as arguments an element's current index and value
         * (as mapped by preceding mappings, if any), and produces a
         * new value.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract <V> WithMapping<T,V> withIndexedMapping
            (IntAndObjectToObject<? super U, ? extends V> mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mappings of this array using the given mapper that
         * accepts as arguments an element's current index and value
         * (as mapped by preceding mappings, if any), and produces a
         * new value.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withIndexedMapping
            (IntAndObjectToDouble<? super U> mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mappings of this array using the given mapper that
         * accepts as arguments an element's current index and value
         * (as mapped by preceding mappings, if any), and produces a
         * new value.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withIndexedMapping
            (IntAndObjectToLong<? super U> mapper);

        /**
         * Returns an Iterable view to sequentially step through mapped
         * elements also obeying bound and filter constraints, without
         * performing computations to evaluate them in parallel
         * @return the Iterable view
         */
        public Iterable<U> sequentially() {
            return new Sequentially<U>();
        }
    }

    /**
     * A restriction of parallel array operations to apply only to
     * elements for which a selector returns true
     */
    public static abstract class WithFilter<T> extends WithMapping<T,T>{
        WithFilter(ParallelArray<T> pa, int firstIndex, int upperBound) {
            super(pa, firstIndex, upperBound);
        }

        /**
         * Replaces elements with the results of applying the given
         * op to their current values.
         * @param op the op
         */
        public void replaceWithMapping(Op<? super T, ? extends T> op) {
            ex.invoke(new PAS.FJOTransform(this, firstIndex, upperBound,
                                           null, op));
        }

        /**
         * Replaces elements with the results of applying the given
         * op to their indices
         * @param op the op
         */
        public void replaceWithMappedIndex(IntToObject<? extends T> op) {
            ex.invoke(new PAS.FJOIndexMap(this, firstIndex, upperBound,
                                          null, op));
        }

        /**
         * Replaces elements with results of applying the given
         * generator.
         * @param generator the generator
         */
        public void replaceWithGeneratedValue
            (Generator<? extends T> generator) {
            ex.invoke(new PAS.FJOGenerate
                      (this, firstIndex, upperBound, null, generator));
        }

        /**
         * Replaces elements with the given value.
         * @param value the value
         */
        public void replaceWithValue(T value) {
            ex.invoke(new PAS.FJOFill(this, firstIndex, upperBound,
                                      null, value));
        }

        /**
         * Replaces elements with results of applying
         * <tt>op(thisElement, otherElement)</tt>
         * @param other the other array
         * @param combiner the combiner
         * @throws ArrayIndexOutOfBoundsException if other array has
         * fewer than <tt>upperBound</tt> elements.
         */
        public void replaceWithMapping(BinaryOp<T,T,T> combiner,
                                       ParallelArray<? extends T> other) {
            ex.invoke(new PAS.FJOCombineInPlace
                      (this, firstIndex, upperBound, null,
                       other.array, 0, combiner));
        }

        /**
         * Replaces elements with results of applying
         * <tt>op(thisElement, otherElement)</tt>
         * @param other the other array segment
         * @param combiner the combiner
         * @throws ArrayIndexOutOfBoundsException if other array has
         * fewer than <tt>upperBound</tt> elements.
         */
        public void replaceWithMapping
            (BinaryOp<T,T,T> combiner,
             ParallelArray.WithBounds<? extends T> other) {
            ex.invoke(new PAS.FJOCombineInPlace
                      (this, firstIndex, upperBound, null,
                       other.pa.array, other.firstIndex-firstIndex, combiner));
        }

        /**
         * Replaces elements with results of applying
         * <tt>op(thisElement, otherElement)</tt>
         * @param other the other array
         * @param combiner the combiner
         * @throws ArrayIndexOutOfBoundsException if other array has
         * fewer than <tt>upperBound</tt> elements.
         */
        public void replaceWithMapping(BinaryOp<T,T,T> combiner, T[] other) {
            ex.invoke(new PAS.FJOCombineInPlace
                      (this, firstIndex, upperBound, null, other,
                       -firstIndex, combiner));
        }

        /**
         * Removes from the array all elements matching bound and/or
         * filter constraints.
         */
        public abstract void removeAll();

        /**
         * Returns a new ParallelArray containing only non-null unique
         * elements (that is, without any duplicates). This method
         * uses each element's <tt>equals</tt> method to test for
         * duplication.
         * @return the new ParallelArray
         */
        public abstract ParallelArray<T> allUniqueElements();

        /**
         * Returns a new ParallelArray containing only non-null unique
         * elements (that is, without any duplicates). This method
         * uses reference identity to test for duplication.
         * @return the new ParallelArray
         */
        public abstract ParallelArray<T> allNonidenticalElements();

        /**
         * Returns an operation prefix that causes a method to operate
         * only on elements for which the current selector (if
         * present) and the given selector returns true
         * @param selector the selector
         * @return operation prefix
         */
        public abstract WithFilter<T> withFilter
            (Predicate<? super T> selector);

        /**
         * Returns an operation prefix that causes a method to operate
         * only on elements for which the current selector (if
         * present) or the given selector returns true
         * @param selector the selector
         * @return operation prefix
         */
        public abstract WithFilter<T> orFilter
            (Predicate<? super T> selector);

        final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = (array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                       Object[] dest, int offset) {
            final Object[] array = pa.array;
            for (int i = loIdx; i < hiIdx; ++i)
                dest[offset++] = (array[indices[i]]);
        }
    }

    /**
     * A restriction of parallel array operations to apply only within
     * a given range of indices.
     */
    public static final class WithBounds<T> extends WithFilter<T> {
        WithBounds(ParallelArray<T> pa, int firstIndex, int upperBound) {
            super(pa, firstIndex, upperBound);
        }

        /** Version for implementing main ParallelArray methods */
        WithBounds(ParallelArray<T> pa) {
            super(pa, 0, pa.limit);
        }

        Object oget(int i) { return pa.array[i]; }

        /**
         * Returns an operation prefix that causes a method to operate
         * only on the elements of the array between firstIndex
         * (inclusive) and upperBound (exclusive).  The bound
         * arguments are relative to the current bounds.  For example
         * <tt>pa.withBounds(2, 8).withBounds(3, 5)</tt> indexes the
         * 5th (= 2+3) and 6th elements of pa. However, indices
         * returned by methods such as <tt>indexOf</tt> are
         * with respect to the underlying ParallelArray.
         * @param firstIndex the lower bound (inclusive)
         * @param upperBound the upper bound (exclusive)
         * @return operation prefix
         */
        public WithBounds<T> withBounds(int firstIndex, int upperBound) {
            if (firstIndex > upperBound)
                throw new IllegalArgumentException
                    ("firstIndex(" + firstIndex +
                     ") > upperBound(" + upperBound+")");
            if (firstIndex < 0)
                throw new ArrayIndexOutOfBoundsException(firstIndex);
            if (upperBound - firstIndex > this.upperBound - this.firstIndex)
                throw new ArrayIndexOutOfBoundsException(upperBound);
            return new WithBounds<T>(pa,
                                     this.firstIndex + firstIndex,
                                     this.firstIndex + upperBound);
        }

        public WithFilter<T> withFilter(Predicate<? super T> selector) {
            return new WithBoundedFilter<T>
                (pa, firstIndex, upperBound, selector);
        }

        public <U> WithMapping<T, U> withMapping
            (Op<? super T, ? extends U> op) {
            return new WithBoundedMapping<T,U>
                (pa, firstIndex, upperBound, op);
        }

        public WithDoubleMapping<T> withMapping
            (ObjectToDouble<? super T> op) {
            return new WithBoundedDoubleMapping<T>
                (pa, firstIndex, upperBound, op);
        }

        public WithLongMapping<T> withMapping
            (ObjectToLong<? super T> op) {
            return new WithBoundedLongMapping<T>
                (pa, firstIndex, upperBound, op);
        }

        public <U,V> WithMapping<T,V> withMapping
            (BinaryOp<? super T, ? super U, ? extends V> combiner,
             ParallelArray<U> other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndDoubleToObject<? super T, ? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndLongToObject<? super T, ? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public <U> WithDoubleMapping<T> withMapping
            (ObjectAndObjectToDouble<? super T, ? super U> combiner,
             ParallelArray<U> other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndDoubleToDouble<? super T> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndLongToDouble<? super T> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public <U> WithLongMapping<T> withMapping
            (ObjectAndObjectToLong<? super T, ? super U> combiner,
             ParallelArray<U> other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndDoubleToLong<? super T> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndLongToLong<? super T> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndObjectToObject<? super T, ? extends V> mapper) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound, mapper);
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndObjectToDouble<? super T> mapper) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, mapper);
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndObjectToLong<? super T> mapper) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound, mapper);
        }

        public WithFilter<T> orFilter(Predicate<? super T> selector) {
            return new WithBoundedFilter<T>
                (pa, firstIndex, upperBound, selector);
        }

        public ParallelArray<T> allUniqueElements() {
            PAS.OUniquifierTable tab = new PAS.OUniquifierTable
                (upperBound - firstIndex, pa.array, null, false);
            PAS.FJUniquifier f = new PAS.FJUniquifier
                (this, firstIndex, upperBound, null, tab);
            ex.invoke(f);
            T[] res = (T[])(tab.uniqueElements(f.count));
            return new ParallelArray<T>(ex, res);
        }

        public ParallelArray<T> allNonidenticalElements() {
            PAS.OUniquifierTable tab = new PAS.OUniquifierTable
                (upperBound - firstIndex, pa.array, null, true);
            PAS.FJUniquifier f = new PAS.FJUniquifier
                (this, firstIndex, upperBound, null, tab);
            ex.invoke(f);
            T[] res = (T[])(tab.uniqueElements(f.count));
            return new ParallelArray<T>(ex, res);
        }

        /**
         * Returns the index of some element equal to given target, or
         * -1 if not present
         * @param target the element to search for
         * @return the index or -1 if not present
         */
        public int indexOf(T target) {
            AtomicInteger result = new AtomicInteger(-1);
            PAS.FJOIndexOf f = new PAS.FJOIndexOf
                (this, firstIndex, upperBound, null, result, target);
            ex.invoke(f);
            return result.get();
        }

        /**
         * Assuming this array is sorted, returns the index of an
         * element equal to given target, or -1 if not present. If the
         * array is not sorted, the results are undefined.
         * @param target the element to search for
         * @return the index or -1 if not present
         */
        public int binarySearch(T target) {
            final Object[] array = pa.array;
            int lo = firstIndex;
            int hi = upperBound - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int c = ((Comparable)target).compareTo((Comparable)array[mid]);
                if (c == 0)
                    return mid;
                else if (c < 0)
                    hi = mid - 1;
                else
                    lo = mid + 1;
            }
            return -1;
        }

        /**
         * Assuming this array is sorted with respect to the given
         * comparator, returns the index of an element equal to given
         * target, or -1 if not present. If the array is not sorted,
         * the results are undefined.
         * @param target the element to search for
         * @param comparator the comparator
         * @return the index or -1 if not present
         */
        public int binarySearch(T target, Comparator<? super T> comparator) {
            Comparator cmp = comparator;
            final Object[] array = pa.array;
            int lo = firstIndex;
            int hi = upperBound - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int c = cmp.compare(target, array[mid]);
                if (c == 0)
                    return mid;
                else if (c < 0)
                    hi = mid - 1;
                else
                    lo = mid + 1;
            }
            return -1;
        }

        /**
         * Replaces each element with the running cumulation of applying
         * the given reducer.
         * @param reducer the reducer
         * @param base the result for an empty array
         */
        public void cumulate(Reducer<T> reducer, T base) {
            PAS.FJOCumulateOp op = new PAS.FJOCumulateOp(this, reducer, base);
            PAS.FJOScan r = new PAS.FJOScan(null, op, firstIndex, upperBound);
            ex.invoke(r);
        }

        /**
         * Replaces each element with the cumulation of applying the given
         * reducer to all previous values, and returns the total
         * reduction.
         * @param reducer the reducer
         * @param base the result for an empty array
         * @return the total reduction
         */
        public T precumulate(Reducer<T> reducer, T base) {
            PAS.FJOPrecumulateOp op = new PAS.FJOPrecumulateOp
                (this, reducer, base);
            PAS.FJOScan r = new PAS.FJOScan(null, op, firstIndex, upperBound);
            ex.invoke(r);
            return (T)(r.out);
        }

        /**
         * Sorts the elements.
         * Unlike Arrays.sort, this sort does
         * not guarantee that elements with equal keys maintain their
         * relative position in the array.
         * @param cmp the comparator to use
         */
        public void sort(Comparator<? super T> cmp) {
            final Object[] array = pa.array;
            Class tc = array.getClass().getComponentType();
            T[] ws = (T[])Array.newInstance(tc, upperBound);
            ex.invoke(new PAS.FJOSorter
                      (cmp, array, ws, firstIndex,
                       upperBound - firstIndex, threshold));
        }

        /**
         * Sorts the elements, assuming all elements are
         * Comparable. Unlike Arrays.sort, this sort does not
         * guarantee that elements with equal keys maintain their relative
         * position in the array.
         * @throws ClassCastException if any element is not Comparable.
         */
        public void sort() {
            final Object[] array = pa.array;
            Class tc = array.getClass().getComponentType();
            if (!Comparable.class.isAssignableFrom(tc)) {
                sort(Ops.castedComparator());
                return;
            }
            Comparable[] ca = (Comparable[])array;
            Comparable[] ws = (Comparable[])Array.newInstance(tc, upperBound);
            pa.ex.invoke(new PAS.FJOCSorter
                         (ca, ws, firstIndex,
                          upperBound - firstIndex, threshold));
        }

        public void removeAll() {
            pa.removeSlotsAt(firstIndex, upperBound);
        }

        /**
         * Removes consecutive elements that are equal (or null),
         * shifting others leftward, and possibly decreasing size.  This
         * method may be used after sorting to ensure that this
         * ParallelArray contains a set of unique elements.
         */
        public void removeConsecutiveDuplicates() {
            // Sequential implementation for now
            int k = firstIndex;
            int n = upperBound;
            Object[] arr = pa.array;
            Object last = null;
            for (int i = k; i < n; ++i) {
                Object x = arr[i];
                if (x != null && (last == null || !last.equals(x)))
                    arr[k++] = last = x;
            }
            pa.removeSlotsAt(k, n);
        }

        /**
         * Removes null elements, shifting others leftward, and possibly
         * decreasing size.
         */
        public void removeNulls() {
            // Sequential implementation for now
            int k = firstIndex;
            int n = upperBound;
            Object[] arr = pa.array;
            for (int i = k; i < n; ++i) {
                Object x = arr[i];
                if (x != null)
                    arr[k++] = x;
            }
            pa.removeSlotsAt(k, n);
        }

        void leafApply(int lo, int hi, Procedure procedure) {
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                procedure.op(array[i]);
        }

        void leafCombine(int lo, int hi, Object[] other, int otherOffset,
                         Object[] dest, BinaryOp combiner) {
            final Object[] array = pa.array;
            int k = lo - firstIndex;
            for (int i = lo; i < hi; ++i) {
                dest[k] = combiner.op(array[i], other[i + otherOffset]);
                ++k;
            }
        }

        Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
            if (lo >= hi)
                return base;
            final Object[] array = pa.array;
            Object r = array[lo];
            for (int i = lo+1; i < hi; ++i)
                r = reducer.op(r, array[i]);
            return r;
        }

    }

    static final class WithBoundedFilter<T> extends WithFilter<T> {
        final Predicate<? super T> selector;
        WithBoundedFilter(ParallelArray<T> pa,
                          int firstIndex, int upperBound,
                          Predicate<? super T> selector) {
            super(pa, firstIndex, upperBound);
            this.selector = selector;
        }

        boolean hasFilter() { return true; }
        Predicate getPredicate() { return selector; }
        boolean isSelected(int i) { return selector.op(pa.array[i]); }
        Object oget(int i) { return pa.array[i]; }

        public WithFilter<T> withFilter(Predicate<? super T> selector) {
            return new WithBoundedFilter<T>
                (pa, firstIndex, upperBound,
                 Ops.andPredicate(this.selector, selector));
        }

        public WithFilter<T> orFilter(Predicate<? super T> selector) {
            return new WithBoundedFilter<T>
                (pa, firstIndex, upperBound,
                 Ops.orPredicate(this.selector, selector));
        }

        public <U> WithMapping<T, U> withMapping
            (Op<? super T, ? extends U> op) {
            return new WithBoundedFilteredMapping<T,U>
                (pa, firstIndex, upperBound, selector, op);
        }

        public WithDoubleMapping<T> withMapping
            (ObjectToDouble<? super T> op) {
            return new WithBoundedFilteredDoubleMapping<T>
                (pa, firstIndex, upperBound, selector, op);
        }

        public WithLongMapping<T> withMapping
            (ObjectToLong<? super T> op) {
            return new WithBoundedFilteredLongMapping<T>
                (pa, firstIndex, upperBound, selector, op);
        }

        public <U,V> WithMapping<T,V> withMapping
            (BinaryOp<? super T, ? super U, ? extends V> combiner,
             ParallelArray<U> other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndDoubleToObject<? super T, ? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndLongToObject<? super T, ? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public <U> WithDoubleMapping<T> withMapping
            (ObjectAndObjectToDouble<? super T, ? super U> combiner,
             ParallelArray<U> other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndDoubleToDouble<? super T> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndLongToDouble<? super T> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public <U> WithLongMapping<T> withMapping
            (ObjectAndObjectToLong<? super T, ? super U> combiner,
             ParallelArray<U> other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndDoubleToLong<? super T> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndLongToLong<? super T> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.indexedMapper(combiner, other.array, -firstIndex));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndObjectToObject<? super T, ? extends V> mapper) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector, mapper);
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndObjectToDouble<? super T> mapper) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector, mapper);
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndObjectToLong<? super T> mapper) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector, mapper);
        }

        public ParallelArray<T> allUniqueElements() {
            PAS.OUniquifierTable tab = new PAS.OUniquifierTable
                (upperBound - firstIndex, pa.array, selector, false);
            PAS.FJUniquifier f = new PAS.FJUniquifier
                (this, firstIndex, upperBound, null, tab);
            ex.invoke(f);
            T[] res = (T[])(tab.uniqueElements(f.count));
            return new ParallelArray<T>(ex, res);
        }

        public ParallelArray<T> allNonidenticalElements() {
            PAS.OUniquifierTable tab = new PAS.OUniquifierTable
                (upperBound - firstIndex, pa.array, selector, true);
            PAS.FJUniquifier f = new PAS.FJUniquifier
                (this, firstIndex, upperBound, null, tab);
            ex.invoke(f);
            T[] res = (T[])(tab.uniqueElements(f.count));
            return new ParallelArray<T>(ex, res);
        }

        public void removeAll() {
            PAS.FJRemoveAllDriver f = new PAS.FJRemoveAllDriver
                (this, firstIndex, upperBound);
            ex.invoke(f);
            pa.removeSlotsAt(f.offset, upperBound);
        }

        void leafApply(int lo, int hi, Procedure  procedure) {
            final Predicate s = selector;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                Object x = array[i];
                if (s.op(x))
                    procedure.op(x);
            }
        }

        Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
            final Predicate s = selector;
            boolean gotFirst = false;
            Object r = base;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                Object x = array[i];
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

    static abstract class WithMappingBase<T,U> extends WithMapping<T,U> {
        final Op<? super T, ? extends U> op;
        WithMappingBase(ParallelArray<T> pa,
                        int firstIndex, int upperBound,
                        Op<? super T, ? extends U> op) {
            super(pa, firstIndex, upperBound);
            this.op = op;
        }

        final boolean hasMap() { return true; }
        final Object oget(int i) { return op.op(pa.array[i]); }

        final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
            final Op f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = f.op(array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                 Object[] dest, int offset) {
            final Object[] array = pa.array;
            final Op f = op;
            for (int i = loIdx; i < hiIdx; ++i)
                dest[offset++] = f.op(array[indices[i]]);
        }
    }

    static final class WithBoundedMapping<T,U> extends WithMappingBase<T,U> {
        WithBoundedMapping(ParallelArray<T> pa,
                           int firstIndex, int upperBound,
                           Op<? super T, ? extends U> op) {
            super(pa, firstIndex, upperBound, op);
        }

        public <V> WithMapping<T, V> withMapping
            (Op<? super U, ? extends V> op) {
            return new WithBoundedMapping<T,V>
                (pa, firstIndex, upperBound,
                 Ops.compoundOp(this.op, op));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectToDouble<? super U> op) {
            return new WithBoundedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 Ops.compoundOp(this.op, op));
        }

        public WithLongMapping<T> withMapping
            (ObjectToLong<? super U> op) {
            return new WithBoundedLongMapping<T>
                (pa, firstIndex, upperBound,
                 Ops.compoundOp(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (BinaryOp<? super U, ? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedMapping<T,W>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndLongToObject<? super U, ? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (ObjectAndObjectToDouble<? super U, ? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndDoubleToDouble<? super U> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndLongToDouble<? super U> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (ObjectAndObjectToLong<? super U, ? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndDoubleToLong<? super U> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndLongToLong<? super U> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndObjectToObject<? super U, ? extends V> mapper) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndObjectToDouble<? super U> mapper) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndObjectToLong<? super U> mapper) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, Procedure  procedure) {
            final Op f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                procedure.op(f.op(array[i]));
        }

        Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
            if (lo >= hi)
                return base;
            final Object[] array = pa.array;
            final Op f = op;
            Object r = f.op(array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.op(r, f.op(array[i]));
            return r;
        }

    }

    static final class WithBoundedFilteredMapping<T,U>
        extends WithMappingBase<T,U> {
        final Predicate<? super T> selector;

        WithBoundedFilteredMapping(ParallelArray<T> pa,
                                   int firstIndex, int upperBound,
                                   Predicate<? super T> selector,
                                   Op<? super T, ? extends U> op) {
            super(pa, firstIndex, upperBound, op);
            this.selector = selector;
        }

        boolean hasFilter() { return true; }
        Predicate getPredicate() { return selector; }
        boolean isSelected(int i) { return selector.op(pa.array[i]); }

        public <V> WithMapping<T, V> withMapping
            (Op<? super U, ? extends V> op) {
            return new WithBoundedFilteredMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundOp(this.op, op));
        }

        public WithDoubleMapping<T> withMapping(ObjectToDouble<? super U> op) {
            return new WithBoundedFilteredDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundOp(this.op, op));
        }

        public WithLongMapping<T> withMapping(ObjectToLong<? super U> op) {
            return new WithBoundedFilteredLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundOp(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (BinaryOp<? super U, ? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedMapping<T,W>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndLongToObject<? super U, ? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (ObjectAndObjectToDouble<? super U, ? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndDoubleToDouble<? super U> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndLongToDouble<? super U> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (ObjectAndObjectToLong<? super U, ? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndDoubleToLong<? super U> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndLongToLong<? super U> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndObjectToObject<? super U, ? extends V> mapper) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndObjectToDouble<? super U> mapper) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndObjectToLong<? super U> mapper) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, Procedure  procedure) {
            final Predicate s = selector;
            final Object[] array = pa.array;
            final Op f = op;
            for (int i = lo; i < hi; ++i) {
                Object x = array[i];
                if (s.op(x))
                    procedure.op(f.op(x));
            }
        }
        Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
            final Predicate s = selector;
            final Object[] array = pa.array;
            final Op f = op;
            boolean gotFirst = false;
            Object r = base;
            for (int i = lo; i < hi; ++i) {
                Object x = array[i];
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

    static abstract class WithIndexedMappingBase<T,U> extends WithMapping<T,U> {
        final IntAndObjectToObject<? super T, ? extends U> op;
        WithIndexedMappingBase
            (ParallelArray<T> pa,
             int firstIndex, int upperBound,
             IntAndObjectToObject<? super T, ? extends U> op) {
            super(pa, firstIndex, upperBound);
            this.op = op;
        }

        final boolean hasMap() { return true; }
        final Object oget(int i) { return op.op(i, pa.array[i]); }

        final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
            final IntAndObjectToObject f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = f.op(i, array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                 Object[] dest, int offset) {
            final Object[] array = pa.array;
            final IntAndObjectToObject f = op;
            for (int i = loIdx; i < hiIdx; ++i) {
                int idx = indices[i];
                dest[offset++] = f.op(idx, array[idx]);
            }
        }
    }

    static final class WithBoundedIndexedMapping<T,U>
        extends WithIndexedMappingBase<T,U> {
        WithBoundedIndexedMapping
            (ParallelArray<T> pa,
             int firstIndex, int upperBound,
             IntAndObjectToObject<? super T, ? extends U> op) {
            super(pa, firstIndex, upperBound, op);
        }

        public <V> WithMapping<T, V> withMapping
            (Op<? super U, ? extends V> op) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectToDouble<? super U> op) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public WithLongMapping<T> withMapping
            (ObjectToLong<? super U> op) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (BinaryOp<? super U, ? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedMapping<T,W>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndLongToObject<? super U, ? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (ObjectAndObjectToDouble<? super U, ? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndDoubleToDouble<? super U> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndLongToDouble<? super U> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (ObjectAndObjectToLong<? super U, ? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndDoubleToLong<? super U> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndLongToLong<? super U> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndObjectToObject<? super U, ? extends V> mapper) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndObjectToDouble<? super U> mapper) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndObjectToLong<? super U> mapper) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, Procedure  procedure) {
            final IntAndObjectToObject f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                procedure.op(f.op(i, array[i]));
        }

        Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
            if (lo >= hi)
                return base;
            final Object[] array = pa.array;
            final IntAndObjectToObject f = op;
            Object r = f.op(lo, array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.op(r, f.op(i, array[i]));
            return r;
        }

    }

    static final class WithBoundedFilteredIndexedMapping<T,U>
        extends WithIndexedMappingBase<T,U> {
        final Predicate<? super T> selector;

        WithBoundedFilteredIndexedMapping
            (ParallelArray<T> pa,
             int firstIndex, int upperBound,
             Predicate<? super T> selector,
             IntAndObjectToObject<? super T, ? extends U> op) {
            super(pa, firstIndex, upperBound, op);
            this.selector = selector;
        }

        boolean hasFilter() { return true; }
        Predicate getPredicate() { return selector; }
        boolean isSelected(int i) { return selector.op(pa.array[i]); }

        public <V> WithMapping<T, V> withMapping
            (Op<? super U, ? extends V> op) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public WithDoubleMapping<T> withMapping(ObjectToDouble<? super U> op) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public WithLongMapping<T> withMapping(ObjectToLong<? super U> op) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (BinaryOp<? super U, ? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedMapping<T,W>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (ObjectAndLongToObject<? super U, ? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (ObjectAndObjectToDouble<? super U, ? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndDoubleToDouble<? super U> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (ObjectAndLongToDouble<? super U> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (ObjectAndObjectToLong<? super U, ? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndDoubleToLong<? super U> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (ObjectAndLongToLong<? super U> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndObjectToObject<? super U, ? extends V> mapper) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndObjectToDouble<? super U> mapper) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndObjectToLong<? super U> mapper) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, Procedure  procedure) {
            final Predicate s = selector;
            final Object[] array = pa.array;
            final IntAndObjectToObject f = op;
            for (int i = lo; i < hi; ++i) {
                Object x = array[i];
                if (s.op(x))
                    procedure.op(f.op(i, x));
            }
        }
        Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
            final Predicate s = selector;
            final Object[] array = pa.array;
            final IntAndObjectToObject f = op;
            boolean gotFirst = false;
            Object r = base;
            for (int i = lo; i < hi; ++i) {
                Object x = array[i];
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

    /**
     * A modifier for parallel array operations to apply to mappings
     * of elements to doubles, not to the elements themselves
     */
    public static abstract class WithDoubleMapping<T> extends PAS.OPrefix<T> {
        WithDoubleMapping(ParallelArray<T> pa,
                          int firstIndex, int upperBound) {
            super(pa, firstIndex, upperBound);
        }

        /**
         * Applies the given procedure
         * @param procedure the procedure
         */
        public void apply(DoubleProcedure procedure) {
            ex.invoke(new PAS.FJDApply
                      (this, firstIndex, upperBound, null, procedure));
        }

        /**
         * Returns reduction of mapped elements
         * @param reducer the reducer
         * @param base the result for an empty array
         * @return reduction
         */
        public double reduce(DoubleReducer reducer, double base) {
            PAS.FJDReduce f = new PAS.FJDReduce
                (this, firstIndex, upperBound, null, reducer, base);
            ex.invoke(f);
            return f.result;
        }

        /**
         * Returns the minimum element, or Double.MAX_VALUE if empty
         * @return minimum element, or Double.MAX_VALUE if empty
         */
        public double min() {
            return reduce(naturalDoubleMinReducer(), Double.MAX_VALUE);
        }

        /**
         * Returns the minimum element, or Double.MAX_VALUE if empty
         * @param comparator the comparator
         * @return minimum element, or Double.MAX_VALUE if empty
         */
        public double min(DoubleComparator comparator) {
            return reduce(doubleMinReducer(comparator),
                          Double.MAX_VALUE);
        }

        /**
         * Returns the maximum element, or -Double.MAX_VALUE if empty
         * @return maximum element, or -Double.MAX_VALUE if empty
         */
        public double max() {
            return reduce(naturalDoubleMaxReducer(), -Double.MAX_VALUE);
        }

        /**
         * Returns the maximum element, or -Double.MAX_VALUE if empty
         * @param comparator the comparator
         * @return maximum element, or -Double.MAX_VALUE if empty
         */
        public double max(DoubleComparator comparator) {
            return reduce(doubleMaxReducer(comparator),
                          -Double.MAX_VALUE);
        }

        /**
         * Returns the sum of elements
         * @return the sum of elements
         */
        public double sum() {
            return reduce(Ops.doubleAdder(), 0.0);
        }

        /**
         * Returns summary statistics
         * @param comparator the comparator to use for
         * locating minimum and maximum elements
         * @return the summary.
         */
        public ParallelDoubleArray.SummaryStatistics summary
            (DoubleComparator comparator) {
            PAS.FJDStats f = new PAS.FJDStats
                (this, firstIndex, upperBound, null, comparator);
            ex.invoke(f);
            return f;
        }

        /**
         * Returns summary statistics, using natural comparator
         * @return the summary.
         */
        public ParallelDoubleArray.SummaryStatistics summary() {
            PAS.FJDStats f = new PAS.FJDStats
                (this, firstIndex, upperBound, null,
                 naturalDoubleComparator());
            ex.invoke(f);
            return f;
        }

        /**
         * Returns a new ParallelDoubleArray holding mappings
         * @return a new ParallelDoubleArray holding mappings
         */
        public ParallelDoubleArray all() {
            return new ParallelDoubleArray(ex, allDoubles());
        }

        /**
         * Return the number of elements selected using bound or
         * filter restrictions. Note that this method must evaluate
         * all selectors to return its result.
         * @return the number of elements
         */
        public int size() {
            return super.computeSize();
        }

        /**
         * Returns the index of some element matching bound and filter
         * constraints, or -1 if none.
         * @return index of matching element, or -1 if none.
         */
        public int anyIndex() {
            return super.computeAnyIndex();
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given op.
         * @param op the op
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withMapping(DoubleOp op);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given op.
         * @param op the op
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withMapping(DoubleToLong op);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given op.
         * @param op the op
         * @return operation prefix
         */
        public abstract <U> WithMapping<T, U> withMapping
            (DoubleToObject<? extends U> op);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V,W> WithMapping<T,W> withMapping
            (DoubleAndObjectToObject<? super V, ? extends W> combiner,
             ParallelArray<V> other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithMapping<T,V> withMapping
            (DoubleAndDoubleToObject<? extends V> combiner,
             ParallelDoubleArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithMapping<T,V> withMapping
            (DoubleAndLongToObject<? extends V> combiner,
             ParallelLongArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithDoubleMapping<T> withMapping
            (DoubleAndObjectToDouble<? super V> combiner,
             ParallelArray<V> other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withMapping
            (BinaryDoubleOp combiner,
             ParallelDoubleArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withMapping
            (DoubleAndLongToDouble combiner,
             ParallelLongArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithLongMapping<T> withMapping
            (DoubleAndObjectToLong<? super V> combiner,
             ParallelArray<V> other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withMapping
            (DoubleAndDoubleToLong combiner,
             ParallelDoubleArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withMapping
            (DoubleAndLongToLong combiner,
             ParallelLongArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mappings of this array using the given mapper that
         * accepts as arguments an element's current index and value
         * (as mapped by preceding mappings, if any), and produces a
         * new value.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract <V> WithMapping<T,V> withIndexedMapping
            (IntAndDoubleToObject<? extends V> mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mappings of this array using the given mapper that
         * accepts as arguments an element's current index and value
         * (as mapped by preceding mappings, if any), and produces a
         * new value.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withIndexedMapping
            (IntAndDoubleToDouble mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mappings of this array using the given mapper that
         * accepts as arguments an element's current index and value
         * (as mapped by preceding mappings, if any), and produces a
         * new value.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withIndexedMapping
            (IntAndDoubleToLong mapper);

        /**
         * Returns an Iterable view to sequentially step through mapped
         * elements also obeying bound and filter constraints, without
         * performing computations to evaluate them in parallel
         * @return the Iterable view
         */
        public Iterable<Double> sequentially() {
            return new SequentiallyAsDouble();
        }

    }

    static abstract class WithDoubleMappingBase<T>
        extends WithDoubleMapping<T> {
        final ObjectToDouble<? super T> op;
        WithDoubleMappingBase(ParallelArray<T> pa,
                              int firstIndex, int upperBound,
                              ObjectToDouble<? super T> op) {
            super(pa, firstIndex, upperBound);
            this.op = op;
        }

        final boolean hasMap() { return true; }
        final double dget(int i) { return op.op(pa.array[i]); }
        final Object oget(int i) { return Double.valueOf(dget(i)); }

        final void leafTransfer(int lo, int hi, double[] dest, int offset) {
            final ObjectToDouble f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = f.op(array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                       double[] dest, int offset) {
            final Object[] array = pa.array;
            final ObjectToDouble f = op;
            for (int i = loIdx; i < hiIdx; ++i)
                dest[offset++] = f.op(array[indices[i]]);
        }

    }

    static final class WithBoundedDoubleMapping<T>
        extends WithDoubleMappingBase<T> {
        WithBoundedDoubleMapping(ParallelArray<T> pa,
                                 int firstIndex, int upperBound,
                                 ObjectToDouble<? super T> op) {
            super(pa, firstIndex, upperBound, op);
        }

        public WithDoubleMapping<T> withMapping(DoubleOp op) {
            return new WithBoundedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 Ops.compoundOp(this.op, op));
        }

        public WithLongMapping<T> withMapping(DoubleToLong op) {
            return new WithBoundedLongMapping<T>
                (pa, firstIndex, upperBound,
                 Ops.compoundOp(this.op, op));
        }

        public <U> WithMapping<T, U> withMapping
            (DoubleToObject<? extends U> op) {
            return new WithBoundedMapping<T,U>
                (pa, firstIndex, upperBound,
                 Ops.compoundOp(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (DoubleAndObjectToObject<? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedMapping<T,W>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (DoubleAndDoubleToObject<? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (DoubleAndLongToObject<? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (DoubleAndObjectToDouble<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (BinaryDoubleOp combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (DoubleAndLongToDouble combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (DoubleAndObjectToLong<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (DoubleAndDoubleToLong combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (DoubleAndLongToLong combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndDoubleToObject<? extends V> mapper) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndDoubleToDouble mapper) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndDoubleToLong mapper) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, DoubleProcedure procedure) {
            final ObjectToDouble f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                procedure.op(f.op(array[i]));
        }

        double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
            if (lo >= hi)
                return base;
            final Object[] array = pa.array;
            final ObjectToDouble f = op;
            double r = f.op(array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.op(r, f.op(array[i]));
            return r;
        }

    }

    static final class WithBoundedFilteredDoubleMapping<T>
        extends WithDoubleMappingBase<T> {
        final Predicate<? super T> selector;
        WithBoundedFilteredDoubleMapping
            (ParallelArray<T> pa, int firstIndex, int upperBound,
             Predicate<? super T> selector, ObjectToDouble<? super T> op) {
            super(pa, firstIndex, upperBound, op);
            this.selector = selector;
        }

        boolean hasFilter() { return true; }
        Predicate getPredicate() { return selector; }
        boolean isSelected(int i) { return selector.op(pa.array[i]); }

        public WithDoubleMapping<T> withMapping(DoubleOp op) {
            return new WithBoundedFilteredDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundOp(this.op, op));
        }

        public WithLongMapping<T> withMapping(DoubleToLong op) {
            return new WithBoundedFilteredLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundOp(this.op, op));
        }

        public <U> WithMapping<T, U> withMapping
            (DoubleToObject<? extends U> op) {
            return new WithBoundedFilteredMapping<T,U>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundOp(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (DoubleAndObjectToObject<? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedMapping<T,W>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (DoubleAndDoubleToObject<? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (DoubleAndLongToObject<? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (DoubleAndObjectToDouble<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (BinaryDoubleOp combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (DoubleAndLongToDouble combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (DoubleAndObjectToLong<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (DoubleAndDoubleToLong combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (DoubleAndLongToLong combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndDoubleToObject<? extends V> mapper) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndDoubleToDouble mapper) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndDoubleToLong mapper) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, DoubleProcedure procedure) {
            final Predicate s = selector;
            final Object[] array = pa.array;
            final ObjectToDouble f = op;
            for (int i = lo; i < hi; ++i) {
                Object x = array[i];
                if (s.op(x))
                    procedure.op(f.op(x));
            }
        }

        double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
            final Predicate s = selector;
            final ObjectToDouble f = op;
            boolean gotFirst = false;
            double r = base;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                Object t = array[i];
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

    static abstract class WithIndexedDoubleMappingBase<T>
        extends WithDoubleMapping<T> {
        final IntAndObjectToDouble<? super T> op;
        WithIndexedDoubleMappingBase(ParallelArray<T> pa,
                                     int firstIndex, int upperBound,
                                     IntAndObjectToDouble<? super T> op) {
            super(pa, firstIndex, upperBound);
            this.op = op;
        }

        final boolean hasMap() { return true; }
        final double dget(int i) { return op.op(i, pa.array[i]); }
        final Object oget(int i) { return Double.valueOf(dget(i)); }

        final void leafTransfer(int lo, int hi, double[] dest, int offset) {
            final IntAndObjectToDouble f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = f.op(i, array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                       double[] dest, int offset) {
            final Object[] array = pa.array;
            final IntAndObjectToDouble f = op;
            for (int i = loIdx; i < hiIdx; ++i) {
                int idx = indices[i];
                dest[offset++] = f.op(idx, array[idx]);
            }
        }

    }

    static final class WithBoundedIndexedDoubleMapping<T>
        extends WithIndexedDoubleMappingBase<T> {
        WithBoundedIndexedDoubleMapping
            (ParallelArray<T> pa,
             int firstIndex, int upperBound,
             IntAndObjectToDouble<? super T> op) {
            super(pa, firstIndex, upperBound, op);
        }

        public WithDoubleMapping<T> withMapping(DoubleOp op) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public WithLongMapping<T> withMapping(DoubleToLong op) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public <U> WithMapping<T, U> withMapping
            (DoubleToObject<? extends U> op) {
            return new WithBoundedIndexedMapping<T,U>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (DoubleAndObjectToObject<? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedMapping<T,W>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (DoubleAndDoubleToObject<? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (DoubleAndLongToObject<? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (DoubleAndObjectToDouble<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (BinaryDoubleOp combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (DoubleAndLongToDouble combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (DoubleAndObjectToLong<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (DoubleAndDoubleToLong combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (DoubleAndLongToLong combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndDoubleToObject<? extends V> mapper) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndDoubleToDouble mapper) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndDoubleToLong mapper) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, DoubleProcedure procedure) {
            final IntAndObjectToDouble f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                procedure.op(f.op(i, array[i]));
        }

        double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
            if (lo >= hi)
                return base;
            final Object[] array = pa.array;
            final IntAndObjectToDouble f = op;
            double r = f.op(lo, array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.op(r, f.op(i, array[i]));
            return r;
        }

    }

    static final class WithBoundedFilteredIndexedDoubleMapping<T>
        extends WithIndexedDoubleMappingBase<T> {
        final Predicate<? super T> selector;
        WithBoundedFilteredIndexedDoubleMapping
            (ParallelArray<T> pa, int firstIndex, int upperBound,
             Predicate<? super T> selector,
             IntAndObjectToDouble<? super T> op) {
            super(pa, firstIndex, upperBound, op);
            this.selector = selector;
        }

        boolean hasFilter() { return true; }
        Predicate getPredicate() { return selector; }
        boolean isSelected(int i) { return selector.op(pa.array[i]); }

        public WithDoubleMapping<T> withMapping(DoubleOp op) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public WithLongMapping<T> withMapping(DoubleToLong op) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public <U> WithMapping<T, U> withMapping
            (DoubleToObject<? extends U> op) {
            return new WithBoundedFilteredIndexedMapping<T,U>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (DoubleAndObjectToObject<? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedMapping<T,W>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (DoubleAndDoubleToObject<? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (DoubleAndLongToObject<? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (DoubleAndObjectToDouble<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (BinaryDoubleOp combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (DoubleAndLongToDouble combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (DoubleAndObjectToLong<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (DoubleAndDoubleToLong combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (DoubleAndLongToLong combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndDoubleToObject<? extends V> mapper) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndDoubleToDouble mapper) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndDoubleToLong mapper) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, mapper));
        }

        void leafApply(int lo, int hi, DoubleProcedure procedure) {
            final Predicate s = selector;
            final Object[] array = pa.array;
            final IntAndObjectToDouble f = op;
            for (int i = lo; i < hi; ++i) {
                Object x = array[i];
                if (s.op(x))
                    procedure.op(f.op(i, x));
            }
        }

        double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
            final Predicate s = selector;
            final IntAndObjectToDouble f = op;
            boolean gotFirst = false;
            double r = base;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                Object t = array[i];
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

    /**
     * A modifier for parallel array operations to apply to mappings
     * of elements to longs, not to the elements themselves
     */
    public static abstract class WithLongMapping<T> extends PAS.OPrefix<T> {
        WithLongMapping(ParallelArray<T> pa,
                        int firstIndex, int upperBound) {
            super(pa, firstIndex, upperBound);
        }

        /**
         * Applies the given procedure
         * @param procedure the procedure
         */
        public void apply(LongProcedure procedure) {
            ex.invoke(new PAS.FJLApply
                      (this, firstIndex, upperBound, null, procedure));
        }

        /**
         * Returns reduction of mapped elements
         * @param reducer the reducer
         * @param base the result for an empty array
         * @return reduction
         */
        public long reduce(LongReducer reducer, long base) {
            PAS.FJLReduce f = new PAS.FJLReduce
                (this, firstIndex, upperBound, null, reducer, base);
            ex.invoke(f);
            return f.result;
        }

        /**
         * Returns the minimum element, or Long.MAX_VALUE if empty
         * @return minimum element, or Long.MAX_VALUE if empty
         */
        public long min() {
            return reduce(naturalLongMinReducer(), Long.MAX_VALUE);
        }

        /**
         * Returns the minimum element, or Long.MAX_VALUE if empty
         * @param comparator the comparator
         * @return minimum element, or Long.MAX_VALUE if empty
         */
        public long min(LongComparator comparator) {
            return reduce(longMinReducer(comparator), Long.MAX_VALUE);
        }

        /**
         * Returns the maximum element, or Long.MIN_VALUE if empty
         * @return maximum element, or Long.MIN_VALUE if empty
         */
        public long max() {
            return reduce(naturalLongMaxReducer(), Long.MIN_VALUE);
        }

        /**
         * Returns the maximum element, or Long.MIN_VALUE if empty
         * @param comparator the comparator
         * @return maximum element, or Long.MIN_VALUE if empty
         */
        public long max(LongComparator comparator) {
            return reduce(longMaxReducer(comparator), Long.MIN_VALUE);
        }

        /**
         * Returns the sum of elements
         * @return the sum of elements
         */
        public long sum() {
            return reduce(Ops.longAdder(), 0L);
        }

        /**
         * Returns summary statistics
         * @param comparator the comparator to use for
         * locating minimum and maximum elements
         * @return the summary.
         */
        public ParallelLongArray.SummaryStatistics summary
            (LongComparator comparator) {
            PAS.FJLStats f = new PAS.FJLStats
                (this, firstIndex, upperBound, null, comparator);
            ex.invoke(f);
            return f;
        }

        /**
         * Returns summary statistics, using natural comparator
         * @return the summary.
         */
        public ParallelLongArray.SummaryStatistics summary() {
            PAS.FJLStats f = new PAS.FJLStats
                (this, firstIndex, upperBound, null,
                 naturalLongComparator());
            ex.invoke(f);
            return f;
        }

        /**
         * Returns a new ParallelLongArray holding mappings
         * @return a new ParallelLongArray holding mappings
         */
        public ParallelLongArray all() {
            return new ParallelLongArray(ex, allLongs());
        }

        /**
         * Return the number of elements selected using bound or
         * filter restrictions. Note that this method must evaluate
         * all selectors to return its result.
         * @return the number of elements
         */
        public int size() {
            return super.computeSize();
        }

        /**
         * Returns the index of some element matching bound and filter
         * constraints, or -1 if none.
         * @return index of matching element, or -1 if none.
         */
        public int anyIndex() {
            return super.computeAnyIndex();
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given op.
         * @param op the op
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withMapping(LongToDouble op);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given op.
         * @param op the op
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withMapping(LongOp op);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given op.
         * @param op the op
         * @return operation prefix
         */
        public abstract <U> WithMapping<T, U> withMapping
            (LongToObject<? extends U> op);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V,W> WithMapping<T,W> withMapping
            (LongAndObjectToObject<? super V, ? extends W> combiner,
             ParallelArray<V> other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithMapping<T,V> withMapping
            (LongAndDoubleToObject<? extends V> combiner,
             ParallelDoubleArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithMapping<T,V> withMapping
            (LongAndLongToObject<? extends V> combiner,
             ParallelLongArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithDoubleMapping<T> withMapping
            (LongAndObjectToDouble<? super V> combiner,
             ParallelArray<V> other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withMapping
            (LongAndDoubleToDouble combiner,
             ParallelDoubleArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withMapping
            (LongAndLongToDouble combiner,
             ParallelLongArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract <V> WithLongMapping<T> withMapping
            (LongAndObjectToLong<? super V> combiner,
             ParallelArray<V> other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withMapping
            (LongAndDoubleToLong combiner,
             ParallelDoubleArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on binary mappings of this array and the other array.
         * @param combiner the combiner
         * @param other the other array
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withMapping
            (BinaryLongOp combiner,
             ParallelLongArray other);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mappings of this array using the given mapper that
         * accepts as arguments an element's current index and value
         * (as mapped by preceding mappings, if any), and produces a
         * new value.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract <V> WithMapping<T,V> withIndexedMapping
            (IntAndLongToObject<? extends V> mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mappings of this array using the given mapper that
         * accepts as arguments an element's current index and value
         * (as mapped by preceding mappings, if any), and produces a
         * new value.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withIndexedMapping
            (IntAndLongToDouble mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mappings of this array using the given mapper that
         * accepts as arguments an element's current index and value
         * (as mapped by preceding mappings, if any), and produces a
         * new value.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withIndexedMapping
            (IntAndLongToLong mapper);

        /**
         * Returns an Iterable view to sequentially step through mapped
         * elements also obeying bound and filter constraints, without
         * performing computations to evaluate them in parallel
         * @return the Iterable view
         */
        public Iterable<Long> sequentially() {
            return new SequentiallyAsLong();
        }

    }

    static abstract class WithLongMappingBase<T> extends WithLongMapping<T> {
        final ObjectToLong<? super T> op;
        WithLongMappingBase(ParallelArray<T> pa,
                            int firstIndex, int upperBound,
                            final ObjectToLong<? super T> op) {
            super(pa, firstIndex, upperBound);
            this.op = op;
        }

        final boolean hasMap() { return true; }
        final long lget(int i) { return op.op(pa.array[i]); }
        final Object oget(int i) { return Long.valueOf(lget(i)); }

        final void leafTransfer(int lo, int hi, long[] dest, int offset) {
            final ObjectToLong f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = f.op(array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                       long[] dest, int offset) {
            final Object[] array = pa.array;
            final ObjectToLong f = op;
            for (int i = loIdx; i < hiIdx; ++i)
                dest[offset++] = f.op(array[indices[i]]);
        }
    }

    static final class WithBoundedLongMapping<T>
        extends WithLongMappingBase<T> {
        WithBoundedLongMapping(ParallelArray<T> pa,
                               int firstIndex, int upperBound,
                               ObjectToLong<? super T> op) {
            super(pa, firstIndex, upperBound, op);
        }

        public WithDoubleMapping<T> withMapping(LongToDouble op) {
            return new WithBoundedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 Ops.compoundOp(this.op, op));
        }

        public WithLongMapping<T> withMapping(LongOp op) {
            return new WithBoundedLongMapping<T>
                (pa, firstIndex, upperBound,
                 Ops.compoundOp(this.op, op));
        }

        public <U> WithMapping<T, U> withMapping(LongToObject<? extends U> op) {
            return new WithBoundedMapping<T,U>
                (pa, firstIndex, upperBound,
                 Ops.compoundOp(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (LongAndObjectToObject<? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedMapping<T,W>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (LongAndDoubleToObject<? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (LongAndLongToObject<? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (LongAndObjectToDouble<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (LongAndDoubleToDouble combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (LongAndLongToDouble combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (LongAndObjectToLong<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (LongAndDoubleToLong combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (BinaryLongOp combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndLongToObject<? extends V> mapper) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndLongToDouble mapper) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndLongToLong mapper) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, LongProcedure procedure) {
            final Object[] array = pa.array;
            final ObjectToLong f = op;
            for (int i = lo; i < hi; ++i)
                procedure.op(f.op(array[i]));
        }

        long leafReduce(int lo, int hi, LongReducer reducer, long base) {
            if (lo >= hi)
                return base;
            final Object[] array = pa.array;
            final ObjectToLong f = op;
            long r = f.op(array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.op(r, f.op(array[i]));
            return r;
        }

    }

    static final class WithBoundedFilteredLongMapping<T>
        extends WithLongMappingBase<T> {
        final Predicate<? super T> selector;
        WithBoundedFilteredLongMapping
            (ParallelArray<T> pa, int firstIndex, int upperBound,
             Predicate<? super T> selector, ObjectToLong<? super T> op) {
            super(pa, firstIndex, upperBound, op);
            this.selector = selector;
        }

        boolean hasFilter() { return true; }
        Predicate getPredicate() { return selector; }
        boolean isSelected(int i) { return selector.op(pa.array[i]); }

        public WithDoubleMapping<T> withMapping
            (LongToDouble op) {
            return new WithBoundedFilteredDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundOp(this.op, op));
        }

        public WithLongMapping<T> withMapping
            (LongOp op) {
            return new WithBoundedFilteredLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundOp(this.op, op));
        }

        public <U> WithMapping<T, U> withMapping
            (LongToObject<? extends U> op) {
            return new WithBoundedFilteredMapping<T,U>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundOp(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (LongAndObjectToObject<? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedMapping<T,W>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (LongAndDoubleToObject<? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (LongAndLongToObject<? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (LongAndObjectToDouble<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (LongAndDoubleToDouble combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (LongAndLongToDouble combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (LongAndObjectToLong<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (LongAndDoubleToLong combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (BinaryLongOp combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndLongToObject<? extends V> mapper) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndLongToDouble mapper) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndLongToLong mapper) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, LongProcedure procedure) {
            final Predicate s = selector;
            final Object[] array = pa.array;
            final ObjectToLong f = op;
            for (int i = lo; i < hi; ++i) {
                Object x = array[i];
                if (s.op(x))
                    procedure.op(f.op(x));
            }
        }

        long leafReduce(int lo, int hi, LongReducer reducer, long base) {
            final Predicate s = selector;
            final ObjectToLong f = op;
            boolean gotFirst = false;
            long r = base;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                Object t = array[i];
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

    static abstract class WithIndexedLongMappingBase<T>
        extends WithLongMapping<T> {
        final IntAndObjectToLong<? super T> op;
        WithIndexedLongMappingBase(ParallelArray<T> pa,
                                     int firstIndex, int upperBound,
                                     IntAndObjectToLong<? super T> op) {
            super(pa, firstIndex, upperBound);
            this.op = op;
        }

        final boolean hasMap() { return true; }
        final long lget(int i) { return op.op(i, pa.array[i]); }
        final Object oget(int i) { return Long.valueOf(lget(i)); }

        final void leafTransfer(int lo, int hi, long[] dest, int offset) {
            final IntAndObjectToLong f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = f.op(i, array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                       long[] dest, int offset) {
            final Object[] array = pa.array;
            final IntAndObjectToLong f = op;
            for (int i = loIdx; i < hiIdx; ++i) {
                int idx = indices[i];
                dest[offset++] = f.op(idx, array[idx]);
            }
        }

    }

    static final class WithBoundedIndexedLongMapping<T>
        extends WithIndexedLongMappingBase<T> {
        WithBoundedIndexedLongMapping
            (ParallelArray<T> pa,
             int firstIndex, int upperBound,
             IntAndObjectToLong<? super T> op) {
            super(pa, firstIndex, upperBound, op);
        }

        public WithDoubleMapping<T> withMapping(LongToDouble op) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public WithLongMapping<T> withMapping(LongOp op) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public <U> WithMapping<T, U> withMapping
            (LongToObject<? extends U> op) {
            return new WithBoundedIndexedMapping<T,U>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (LongAndObjectToObject<? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedMapping<T,W>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (LongAndDoubleToObject<? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (LongAndLongToObject<? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (LongAndObjectToDouble<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (LongAndDoubleToDouble combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (LongAndLongToDouble combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (LongAndObjectToLong<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (LongAndDoubleToLong combiner,
             ParallelDoubleArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (BinaryLongOp combiner,
             ParallelLongArray other) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndLongToObject<? extends V> mapper) {
            return new WithBoundedIndexedMapping<T,V>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndLongToDouble mapper) {
            return new WithBoundedIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndLongToLong mapper) {
            return new WithBoundedIndexedLongMapping<T>
                (pa, firstIndex, upperBound,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, LongProcedure procedure) {
            final IntAndObjectToLong f = op;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                procedure.op(f.op(i, array[i]));
        }

        long leafReduce(int lo, int hi, LongReducer reducer, long base) {
            if (lo >= hi)
                return base;
            final Object[] array = pa.array;
            final IntAndObjectToLong f = op;
            long r = f.op(lo, array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.op(r, f.op(i, array[i]));
            return r;
        }

    }

    static final class WithBoundedFilteredIndexedLongMapping<T>
        extends WithIndexedLongMappingBase<T> {
        final Predicate<? super T> selector;
        WithBoundedFilteredIndexedLongMapping
            (ParallelArray<T> pa, int firstIndex, int upperBound,
             Predicate<? super T> selector,
             IntAndObjectToLong<? super T> op) {
            super(pa, firstIndex, upperBound, op);
            this.selector = selector;
        }

        boolean hasFilter() { return true; }
        Predicate getPredicate() { return selector; }
        boolean isSelected(int i) { return selector.op(pa.array[i]); }

        public WithDoubleMapping<T> withMapping(LongToDouble op) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public WithLongMapping<T> withMapping(LongOp op) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public <U> WithMapping<T, U> withMapping
            (LongToObject<? extends U> op) {
            return new WithBoundedFilteredIndexedMapping<T,U>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper(this.op, op));
        }

        public <V,W> WithMapping<T,W> withMapping
            (LongAndObjectToObject<? super V, ? extends W> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedMapping<T,W>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (LongAndDoubleToObject<? extends V> combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withMapping
            (LongAndLongToObject<? extends V> combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithDoubleMapping<T> withMapping
            (LongAndObjectToDouble<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (LongAndDoubleToDouble combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithDoubleMapping<T> withMapping
            (LongAndLongToDouble combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithLongMapping<T> withMapping
            (LongAndObjectToLong<? super V> combiner,
             ParallelArray<V> other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (LongAndDoubleToLong combiner,
             ParallelDoubleArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public WithLongMapping<T> withMapping
            (BinaryLongOp combiner,
             ParallelLongArray other) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op,
                  PAS.indexedMapper(combiner, other.array, -firstIndex)));
        }

        public <V> WithMapping<T,V> withIndexedMapping
            (IntAndLongToObject<? extends V> mapper) {
            return new WithBoundedFilteredIndexedMapping<T,V>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithDoubleMapping<T> withIndexedMapping
            (IntAndLongToDouble mapper) {
            return new WithBoundedFilteredIndexedDoubleMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        public WithLongMapping<T> withIndexedMapping
            (IntAndLongToLong mapper) {
            return new WithBoundedFilteredIndexedLongMapping<T>
                (pa, firstIndex, upperBound, selector,
                 PAS.compoundIndexedMapper
                 (this.op, mapper));
        }

        void leafApply(int lo, int hi, LongProcedure procedure) {
            final Predicate s = selector;
            final Object[] array = pa.array;
            final IntAndObjectToLong f = op;
            for (int i = lo; i < hi; ++i) {
                Object x = array[i];
                if (s.op(x))
                    procedure.op(f.op(i, x));
            }
        }

        long leafReduce(int lo, int hi, LongReducer reducer, long base) {
            final Predicate s = selector;
            final IntAndObjectToLong f = op;
            boolean gotFirst = false;
            long r = base;
            final Object[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                Object t = array[i];
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

    /**
     * Returns an iterator stepping through each element of the array
     * up to the current limit. This iterator does <em>not</em>
     * support the remove operation. However, a full
     * <tt>ListIterator</tt> supporting add, remove, and set
     * operations is available via {@link #asList}.
     * @return an iterator stepping through each element.
     */
    public Iterator<T> iterator() {
        return new ParallelArrayIterator<T>(array, limit);
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
    public int size() { return limit; }

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
     * Equivalent to <tt>AsList.addAll</tt> but specialized for array
     * arguments and likely to be more efficient.
     * @param other the elements to add
     */
    public void addAll(T[] other) {
        int csize = other.length;
        int end = limit;
        insertSlotsAt(end, csize);
        System.arraycopy(other, 0, array, end, csize);
    }

    /**
     * Equivalent to <tt>AsList.addAll</tt> but specialized for
     * ParallelArray arguments and likely to be more efficient.
     * @param other the elements to add
     */
    public void addAll(ParallelArray<T> other) {
        int csize = other.size();
        int end = limit;
        insertSlotsAt(end, csize);
        System.arraycopy(other.array, 0, array, end, csize);
    }

    /**
     * Equivalent to <tt>AsList.addAll</tt> but specialized for
     * ParallelArray arguments and likely to be more efficient.
     * @param other the elements to add
     */
    public void addAll(ParallelArray.WithBounds<T> other) {
        int csize = other.size();
        int end = limit;
        insertSlotsAt(end, csize);
        System.arraycopy(other.pa.array, other.firstIndex, array, end, csize);
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
        limit = newLimit;
    }

    final void replaceElementsWith(T[] a) {
        System.arraycopy(a, 0, array, 0, a.length);
        limit = a.length;
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
        int hi = limit++;
        if (hi >= array.length)
            resizeArray((hi * 3)/2 + 1);
        if (hi > index)
            System.arraycopy(array, index, array, index+1, hi - index);
        array[index] = e;
    }

    final void appendElement(T e) {
        int hi = limit++;
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
        int newSize = limit + len;
        if (cap < newSize) {
            cap = (cap * 3)/2 + 1;
            if (cap < newSize)
                cap = newSize;
            resizeArray(cap);
        }
        if (index < limit)
            System.arraycopy(array, index, array, index + len, limit - index);
        limit = newSize;
    }

    final void removeSlotAt(int index) {
        System.arraycopy(array, index + 1, array, index, limit - index - 1);
        array[--limit] = null;
    }

    final void removeSlotsAt(int fromIndex, int toIndex) {
        if (fromIndex < toIndex) {
            int size = limit;
            System.arraycopy(array, toIndex, array, fromIndex, size - toIndex);
            int newSize = size - (toIndex - fromIndex);
            limit = newSize;
            while (size > newSize)
                array[--size] = null;
        }
    }

    final int seqIndexOf(Object target) {
        T[] arr = array;
        int fence = limit;
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
        int last = limit - 1;
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
            this.hi = ParallelArray.this.limit;
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
            hi = ParallelArray.this.limit;
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
            hi = ParallelArray.this.limit;
            lastRet = -1;
            cursor = i + 1;
        }
    }

    final class AsList extends AbstractList<T> implements RandomAccess {
        public T get(int i) {
            if (i >= limit)
                throw new IndexOutOfBoundsException();
            return array[i];
        }

        public T set(int i, T x) {
            if (i >= limit)
                throw new IndexOutOfBoundsException();
            T[] arr = array;
            T t = arr[i];
            arr[i] = x;
            return t;
        }

        public boolean isEmpty() {
            return limit == 0;
        }

        public int size() {
            return limit;
        }

        public Iterator<T> iterator() {
            return new ListIter(0);
        }

        public ListIterator<T> listIterator() {
            return new ListIter(0);
        }

        public ListIterator<T> listIterator(int index) {
            if (index < 0 || index > limit)
                throw new IndexOutOfBoundsException();
            return new ListIter(index);
        }

        public boolean add(T e) {
            appendElement(e);
            return true;
        }

        public void add(int index, T e) {
            if (index < 0 || index > limit)
                throw new IndexOutOfBoundsException();
            insertElementAt(index, e);
        }

        public boolean addAll(Collection<? extends T> c) {
            int csize = c.size();
            if (csize == 0)
                return false;
            int hi = limit;
            setLimit(hi + csize);
            T[] arr = array;
            for (T e : c)
                arr[hi++] = e;
            return true;
        }

        public boolean addAll(int index, Collection<? extends T> c) {
            if (index < 0 || index > limit)
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
            for (int i = 0; i < limit; ++i)
                arr[i] = null;
            limit = 0;
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

        protected void removeRange(int fromIndex, int toIndex) {
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
            int len = limit;
            Object[] a = new Object[len];
            System.arraycopy(array, 0, a, 0, len);
            return a;
        }

        public <V> V[] toArray(V a[]) {
            int len = limit;
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
