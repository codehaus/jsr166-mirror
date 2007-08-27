/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import static jsr166y.forkjoin.Ops.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * An array supporting parallel operations.
 *
 * <p>A ParallelArray encapsulates a ForkJoinExecutor and an array in
 * order to provide parallel aggregate operations.  The main
 * operations are to <em>apply</em> some procedure to each element, to
 * <em>map</em> each element to a new element, to <em>replace</em>
 * each element, to <em>select</em> a subset of elements based on
 * matching a predicate or ranges of indices, and to <em>reduce</em>
 * all elements into a single value such as a sum.
 *
 * <p>A ParallelArray is not a List, but can be viewed as one, via
 * method {@link #asList}, or created from one, by constructing from
 * array returned by a list's <tt>toArray</tt> method. Arrays differ
 * from lists in that they do not incrementally grow or shrink. Random
 * accessiblity across all elements permits efficient parallel
 * operation. ParallelArrays also support element-by-element access
 * (via methods <tt>get</tt> and <tt>/set</tt>), but are normally
 * manipulated using aggregate operations on all or selected elements.
 *
 * <p> Many operations can be prefixed with range bounds, filters, and
 * mappings using <tt>withBounds</tt>, <tt>withFilter</tt>, and
 * <tt>withMapping</tt>, respectively. For example,
 * <tt>aParallelArray.withFilter(aPredicate).newArray()</tt> creates a
 * new ParallelArray containing only those elements matching the
 * predicate.  As illustrated below, a <em>mapping</em> often
 * represents accessing some field or invoking some method of an
 * element.  These versions are typically more efficient than
 * performing selections, then mappings, then other operations in
 * multiple (parallel) steps. However, not all operations are
 * available under all combinations, either because they wouldn't make
 * sense, or because they would not usually be more efficient than
 * stepwise processing.
 *
 * <p>While ParallelArrays can be based on any kind of an object
 * array, including "boxed" types such as Integer, parallel operations
 * on scalar "unboxed" type are likely to be substantially more
 * efficient. For this reason, classes {@link ParallelIntArray},
 * {@link ParallelLongArray}, and {@link ParallelDoubleArray} are also
 * supplied, and designed to smoothly interoperate with
 * ParallelArrays.  (Other scalar types such as <tt>short</tt> are not
 * useful often enough to further integrate them.)
 *
 * <p>The methods in this class are designed to perform efficiently
 * with both large and small pools, even with single-thread pools on
 * uniprocessors.  However, there is some overhead in parallelizing
 * operations, so short computations on small arrays might not execute
 * faster than sequential versions, and might even be slower.
 *
 * <p>Accesses by other threads of the elements of a ParallelArray
 * while an aggregate operation is in progress have undefined effects.
 * Don't do that.
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
 *     public boolean evaluate(Student s) { return s.credits &gt; 90; }
 *   }
 *   static final IsSenior isSenior = new IsSenior();
 *   static final class GpaField implements MappertoDouble&lt;Student&gt {
 *     public double map(Student s) { return s.gpa; }
 *   }
 *   static final GpaField gpaField = new GpaField();
 * }
 * </pre>
 *
 */
public class ParallelArray<T> implements Iterable<T> {
    final T[] array;
    final ForkJoinExecutor ex;

    /**
     * Creates a new ParallelArray using the given executor and
     * array. In general, the handed off array should not be used for
     * other purposes once constructing this ParallelArray.
     * @param executor the executor
     * @param handoff the array
     */
    public ParallelArray(ForkJoinExecutor executor, T[] handoff) {
        if (executor == null || handoff == null)
            throw new NullPointerException();
        this.ex = executor;
        this.array = handoff;
    }

    /**
     * Creates a new ParallelArray using the given executor and an
     * array of the given size, initially holding copies of the given
     * source truncated or padded with nulls to obtain the specified
     * length.
     * @param executor the executor
     * @param size the array size
     * @param sourceToCopy the source of initial elements
     */
    public ParallelArray(ForkJoinExecutor executor, int size,
                         T[] sourceToCopy) {
        if (executor == null)
            throw new NullPointerException();
        this.ex = executor;
        // For now, avoid copyOf so people can compile with Java5
        this.array = (T[])java.lang.reflect.Array.newInstance
            (sourceToCopy.getClass().getComponentType(), size);
        System.arraycopy(sourceToCopy, 0, array, 0,
                         Math.min(sourceToCopy.length, size));
    }

    /**
     * Creates a new ParallelArray using the given executor and
     * an array of the given size constructed using the
     * indicated base element type.
     * @param executor the executor
     * @param size the array size
     * @param elementType the type of the elements
     */
    public ParallelArray(ForkJoinExecutor executor, int size,
                         Class<? super T> elementType) {
        if (executor == null)
            throw new NullPointerException();
        this.ex = executor;
        this.array =
            (T[])java.lang.reflect.Array.newInstance(elementType, size);
    }

    /**
     * Returns the executor used for computations
     * @return the executor
     */
    public ForkJoinExecutor getExecutor() { return ex; }

    /**
     * Returns the underlying array used for computations
     * @return the array
     */
    public T[] getArray() { return array; }

    /**
     * Returns the length of the underlying array
     * @return the length of the underlying array
     */
    public int size() { return array.length; }

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
     * Returns a fixed-size list backed by the underlying array.
     * @return a list view of the specified array
     */
    public List<T> asList() { return Arrays.asList(array); }

    /**
     * Returns an iterator stepping through each element of the array.
     * This iterator does <em>not</em> support the remove operation.
     * @return an iterator stepping through each element of the array.
     */
    public Iterator<T> iterator() {
        return new ParallelArrayIterator<T>(array);
    }

    static final class ParallelArrayIterator<T> implements Iterator<T> {
        int cursor;
        final T[] arr;
        ParallelArrayIterator(T[] a) { arr = a; }
        public boolean hasNext() { return cursor < arr.length; }
        public T next() {
            if (cursor >= arr.length)
                throw new NoSuchElementException();
            return arr[cursor++];
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * A comparator relying on arguments being Comparable.
     * Uses raw types to simplify coercions.
     */
    static final class RawComparator implements Comparator {
        static final RawComparator cmp = new RawComparator();
        public int compare(Object a, Object b) {
            return ((Comparable)a).compareTo((Comparable)b);
        }
    }

    static final class RawMaxReducer<T> implements Reducer<T> {
        public T combine(T a, T b) {
            return (a != null &&
                    (b == null ||
                     ((Comparable)a).compareTo((Comparable)b) >= 0))? a : b;
        }
    }

    static final class RawMinReducer<T> implements Reducer<T> {
        public T combine(T a, T b) {
            return (a != null &&
                    (b == null ||
                     ((Comparable)a).compareTo((Comparable)b) <= 0))? a : b;
        }
    }

    /**
     * Applies the given procedure to elements
     * @param procedure the procedure
     */
    public void apply(Procedure<? super T> procedure) {
        new WithBounds<T>(ex, array).apply(procedure);
    }

    /**
     * Returns reduction of elements
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public T reduce(Reducer<T> reducer, T base) {
        return new WithBounds<T>(ex, array).reduce(reducer, base);
    }

    /**
     * Returns a new ParallelArray holding elements
     * @return a new ParallelArray holding elements
     */
    public ParallelArray<T> newArray() {
        return new WithBounds(ex, array).newArray();
    }

    /**
     * Returns a new ParallelArray with the given element type holding
     * elements
     * @param elementType the type of the elements
     * @return a new ParallelArray holding elements
     */
    public ParallelArray<T> newArray(Class<? super T> elementType) {
        return new WithBounds(ex, array).newArray(elementType);
    }

    /**
     * Returns a ParallelArray containing results of
     * applying <tt>combine(thisElement, otherElement)</tt>
     * for each element.
     * @param other the other array
     * @param combiner the combiner
     * @return the array of mappings
     * @throws ArrayIndexOutOfBoundsException if other array is
     * shorter than this array.
     */
    public <U,V> ParallelArray<V> combine
        (U[] other,
         Combiner<? super T, ? super U, ? extends V> combiner) {
        return new WithBounds<T>(ex, array).combine(other, combiner);
    }

    /**
     * Returns a ParallelArray containing results of
     * applying <tt>combine(thisElement, otherElement)</tt>
     * for each element.
     * @param other the other array
     * @param combiner the combiner
     * @return the array of mappings
     * @throws ArrayIndexOutOfBoundsException if other array is not
     * the same length as this array.
     */
    public <U,V> ParallelArray<V> combine
        (ParallelArray<U> other,
         Combiner<? super T, ? super U, ? extends V> combiner) {
        return new WithBounds<T>(ex, array).combine(other.array, combiner);
    }

    /**
     * Returns a ParallelArray containing results of
     * applying <tt>combine(thisElement, otherElement)</tt>
     * for each element.
     * @param other the other array
     * @param combiner the combiner
     * @param elementType the type of elements of returned array
     * @return the array of mappings
     * @throws ArrayIndexOutOfBoundsException if other array is
     * shorter than this array.
     */
    public <U,V> ParallelArray<V> combine
        (U[] other,
         Combiner<? super T, ? super U, ? extends V> combiner,
         Class<? super V> elementType) {
        return new WithBounds<T>(ex, array).combine(other,
                                                    combiner, elementType);
    }

    /**
     * Returns a ParallelArray containing results of
     * applying <tt>combine(thisElement, otherElement)</tt>
     * for each element.
     * @param other the other array
     * @param combiner the combiner
     * @param elementType the type of elements of returned array
     * @return the array of mappings
     * @throws ArrayIndexOutOfBoundsException if other array is not
     * the same length as this array.
     */
    public <U,V> ParallelArray<V> combine
        (ParallelArray<U> other,
         Combiner<? super T, ? super U, ? extends V> combiner,
         Class<? super V> elementType) {
        return new WithBounds<T>(ex, array).combine(other.array,
                                                    combiner, elementType);
    }

    /**
     * Replaces elements with the results of applying the given mapper
     * to their current values.
     * @param mapper the mapper
     */
    public void replaceWithTransform(Mapper<? super T, ? extends T> mapper) {
        new WithBounds<T>(ex, array).replaceWithTransform(mapper);
    }

    /**
     * Replaces elements with the results of applying the given
     * mapper to their indices.
     * @param mapper the mapper
     */
    public void replaceWithMappedIndex(MapperFromInt<? extends T> mapper) {
        new WithBounds<T>(ex, array).replaceWithMappedIndex(mapper);
    }

    /**
     * Replaces elements with the results of applying the given
     * generator.
     * @param generator the generator
     */
    public void replaceWithGeneratedValue(Generator<? extends T> generator) {
        new WithBounds<T>(ex, array).replaceWithGeneratedValue(generator);
    }

    /**
     * Replaces elements with the given value.
     * @param value the value
     */
    public void replaceWithValue(T value) {
        new WithBounds<T>(ex, array).replaceWithValue(value);
    }

    /**
     * Replaces elements with results of applying
     * <tt>combine(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @throws ArrayIndexOutOfBoundsException if other array has
     * fewer elements than this array.
     */
    public void replaceWithCombination
        (ParallelArray<? extends T> other, Reducer<T> combiner) {
        new WithBounds<T>(ex, array).replaceWithCombination(other.array,
                                                            combiner);
    }

    /**
     * Replaces elements with results of applying
     * <tt>combine(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @throws ArrayIndexOutOfBoundsException if other array has
     * fewer elements than this array.
     */
    public void replaceWithCombination(T[] other, Reducer<T> combiner) {
        new WithBounds<T>(ex, array).replaceWithCombination(other, combiner);
    }

    /**
     * Returns the index of the least element , or -1 if empty
     * @param comparator the comparator
     * @return the index of least element or -1 if empty.
     */
    public int indexOfMin(Comparator<? super T> comparator) {
        return new WithBounds(ex, array).indexOfMin(comparator);
    }

    /**
     * Returns the index of the greatest element , or -1 if empty
     * @param comparator the comparator
     * @return the index of greatest element or -1 if empty.
     */
    public int indexOfMax(Comparator<? super T> comparator) {
        return new WithBounds(ex, array).indexOfMax(comparator);
    }

    /**
     * Returns the index of the least element , or -1 if empty
     * assuming that all elements are Comparables
     * @return the index of least element or -1 if empty.
     * @throws ClassCastException if any element is not Comparable.
     */
    public int indexOfMin() {
        return new WithBounds(ex, array).indexOfMin();
    }

    /**
     * Returns the index of the greatest element , or -1 if empty
     * assuming that all elements are Comparables
     * @return the index of greatest element or -1 if empty.
     * @throws ClassCastException if any element is not Comparable.
     */
    public int indexOfMax() {
        return new WithBounds(ex, array).indexOfMax();
    }

    /**
     * Returns the minimum element, or null if empty
     * @param comparator the comparator
     * @return minimum element, or null if empty
     */
    public T min(Comparator<? super T> comparator) {
        return reduce(new MinReducer<T>(comparator), null);
    }

    /**
     * Returns the minimum element, or null if empty,
     * assuming that all elements are Comparables
     * @return minimum element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public T min() {
        return reduce(new RawMinReducer<T>(), null);
    }

    /**
     * Returns the maximum element, or null if empty
     * @param comparator the comparator
     * @return maximum element, or null if empty
     */
    public T max(Comparator<? super T> comparator) {
        return reduce(new MaxReducer<T>(comparator), null);
    }

    /**
     * Returns the maximum element, or null if empty
     * assuming that all elements are Comparables
     * @return maximum element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public T max() {
        return reduce(new RawMaxReducer<T>(), null);
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
        new WithBounds(ex, array).cumulate(reducer, base);
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
        return (T)(new WithBounds(ex, array).precumulate(reducer, base));
    }

    /**
     * Sorts the array. Unlike Arrays.sort, this sort does
     * not guarantee that elements with equal keys maintain their
     * relative position in the array.
     * @param comparator the comparator to use
     */
    public void sort(Comparator<? super T> comparator) {
        new WithBounds(ex, array).sort(comparator);
    }

    /**
     * Sorts the array, assuming all elements are Comparable. Unlike
     * Arrays.sort, this sort does not guarantee that elements
     * with equal keys maintain their relative position in the array.
     * @throws ClassCastException if any element is not Comparable.
     */
    public void sort() {
        new WithBounds(ex, array).sort();
    }

    /**
     * Returns an operation prefix that causes a method to
     * operate only on the elements of the array between
     * fromIndex (inclusive) and toIndex (exclusive).
     * @param fromIndex the lower bound (inclusive)
     * @param toIndex the upper bound (exclusive)
     * @return operation prefix
     */
    public WithBounds<T> withBounds(int fromIndex, int toIndex) {
        return new WithBounds<T>(ex, array, fromIndex, toIndex);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * only on the elements of the array for which the given selector
     * returns true
     * @param selector the selector
     * @return operation prefix
     */
    public WithFilter<T> withFilter(Predicate<? super T> selector) {
        return new WithBoundedFilter<T>
            (ex, array, 0, array.length, selector);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given mapper.
     * @param mapper the mapper
     * @return operation prefix
     */
    public <U> WithMapping<T, U> withMapping
        (Mapper<? super T, ? extends U> mapper) {
        return new WithBoundedMapping<T,U>
            (ex, array, 0, array.length, mapper);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given mapper.
     * @param mapper the mapper
     * @return operation prefix
     */
    public WithDoubleMapping<T> withMapping
        (MapperToDouble<? super T> mapper) {
        return new WithBoundedDoubleMapping<T>
            (ex, array, 0, array.length, mapper);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given mapper.
     * @param mapper the mapper
     * @return operation prefix
     */
    public WithLongMapping<T> withMapping
        (MapperToLong<? super T> mapper) {
        return new WithBoundedLongMapping<T>
            (ex, array, 0, array.length, mapper);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given mapper.
     * @param mapper the mapper
     * @return operation prefix
     */
    public WithIntMapping<T> withMapping(MapperToInt<? super T> mapper) {
        return new WithBoundedIntMapping<T>
            (ex, array, 0, array.length, mapper);
    }


    /**
     * Base of prefix classes
     */
    static abstract class Params<T> {
        final ForkJoinExecutor ex;
        final T[] array;
        final int fromIndex;
        final int toIndex;
        final int granularity;
        Params(ForkJoinExecutor ex, T[] array, int fromIndex, int toIndex) {
            this.ex = ex;
            this.array = array;
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
            this.granularity = defaultGranularity(ex.getParallelismLevel(),
                                                  toIndex - fromIndex);
        }

        /**
         * default granularity for divide-by-two array tasks.
         */
        static int defaultGranularity(int threads, int n) {
            return (threads > 1)? (1 + n / (threads << 4)) : n;
        }
    }

    /**
     * A modifier for parallel array operations to apply to mappings
     * of elements, not to the elements themselves
     */
    public static abstract class WithMapping<T,U>
        extends Params<T> {
        WithMapping(ForkJoinExecutor ex, T[] array,
                    int fromIndex, int toIndex) {
            super(ex, array, fromIndex, toIndex);
        }

        /**
         * Applies the given procedure to mapped elements
         * @param procedure the procedure
         */
        public void apply(Procedure<? super U> procedure) {
            ex.invoke(new FJApply<T,U>(this, fromIndex, toIndex, procedure));
        }

        abstract void leafApply(int lo, int hi,
                                Procedure<? super U> procedure);

        /**
         * Returns reduction of mapped elements
         * @param reducer the reducer
         * @param base the result for an empty array
         * @return reduction
         */
        public U reduce(Reducer<U> reducer, U base) {
            FJReduce<T,U> f =
                new FJReduce<T,U>(this, fromIndex, toIndex, reducer, base);
            ex.invoke(f);
            return f.result;
        }

        abstract U leafReduce(int lo, int hi,
                              Reducer<U> reducer, U base);

        /**
         * Returns the index of some element matching bound and filter
         * constraints, or -1 if none.
         * @return index of matching element, or -1 if none.
         */
        public abstract int anyIndex();

        /**
         * Returns mapping of some element matching bound and filter
         * constraints, or null if none.
         * @return mapping of matching element, or null if none.
         */
        public abstract U any();

        /**
         * Returns the minimum mapped element, or null if empty
         * @param comparator the comparator
         * @return minimum mapped element, or null if empty
         */
        public U min(Comparator<? super U> comparator) {
            return reduce(new MinReducer<U>(comparator), null);
        }

        /**
         * Returns the minimum mapped element, or null if empty,
         * assuming that all elements are Comparables
         * @return minimum mapped element, or null if empty
         * @throws ClassCastException if any element is not Comparable.
         */
        public U min() {
            return reduce(new RawMinReducer<U>(), null);
        }

        /**
         * Returns the maximum mapped element, or null if empty
         * @param comparator the comparator
         * @return maximum mapped element, or null if empty
         */
        public U max(Comparator<? super U> comparator) {
            return reduce(new MaxReducer<U>(comparator), null);
        }

        /**
         * Returns the maximum mapped element, or null if empty
         * assuming that all elements are Comparables
         * @return maximum mapped element, or null if empty
         * @throws ClassCastException if any element is not Comparable.
         */
        public U max() {
            return reduce(new RawMaxReducer<U>(), null);
        }

        /**
         * Returns the index corresponding to the least mapped element
         * or -1 if empty
         * @param comparator the comparator
         * @return the index of least mapped element or -1 if empty.
         */
        public int indexOfMin(Comparator<? super U> comparator) {
            FJMinIndex<T,U> f = new FJMinIndex<T,U>
                (this, fromIndex, toIndex, comparator, false);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the greatest mapped
         * element, or -1 if empty
         * @param comparator the comparator
         * @return the index of greatest mapped element or -1 if empty.
         */
        public int indexOfMax(Comparator<? super U> comparator) {
            FJMinIndex<T,U> f = new FJMinIndex<T,U>
                (this, fromIndex, toIndex, comparator, true);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the least mapped element
         * or -1 if empty,
         * assuming that all elements are Comparables
         * @return the index of least element or -1 if empty.
         * @throws ClassCastException if any element is not Comparable.
         */
        public int indexOfMin() {
            FJMinIndex<T,U> f = new FJMinIndex<T,U>
                (this, fromIndex, toIndex,
                 (Comparator<? super U>)(RawComparator.cmp), false);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the greatest mapped element or
         * -1 if empty, assuming that all elements are Comparables
         * @return the index of greatest mapped element or -1 if empty.
         * @throws ClassCastException if any element is not Comparable.
         */
        public int indexOfMax() {
            FJMinIndex<T,U> f = new FJMinIndex<T,U>
                (this, fromIndex, toIndex,
                 (Comparator<? super U>)(RawComparator.cmp), true);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns a new ParallelArray holding mapped elements
         * @return a new ParallelArray holding mapped elements
         */
        public abstract ParallelArray<U> newArray();

        /**
         * Returns a new ParallelArray with the given element type
         * holding mapped elements
         * @param elementType the type of the elements
         * @return a new ParallelArray holding mapped elements
         */
        public abstract ParallelArray<U> newArray
            (Class<? super U> elementType);

        /**
         * Return the number of elements selected using bound or
         * filter restrictions. Note that this method must evaluate
         * all selectors to return its result.
         * @return the number of elements
         */
        public abstract int size();

        abstract void leafMinIndex(int lo, int hi,
                                   Comparator<? super U> comparator,
                                   boolean reverse,
                                   FJMinIndex<T,U> task);

    }

    /**
     * A restriction of parallel array operations to apply only to
     * elements for which a selector returns true
     */
    public static abstract class WithFilter<T> extends WithMapping<T,T>{
        WithFilter(ForkJoinExecutor ex, T[] array,
                   int fromIndex, int toIndex) {
            super(ex, array, fromIndex, toIndex);
        }

        /**
         * Applies the given procedure
         * @param procedure the procedure
         */
        public void apply(Procedure<? super T> procedure) {
            ex.invoke(new FJApply<T,T>(this, fromIndex, toIndex, procedure));
        }

        /**
         * Returns reduction of elements
         * @param reducer the reducer
         * @param base the result for an empty array
         * @return reduction
         */
        public T reduce(Reducer<T> reducer, T base) {
            FJReduce<T,T> f =
                new FJReduce<T,T>(this, fromIndex, toIndex, reducer, base);
            ex.invoke(f);
            return f.result;
        }

        /**
         * Returns some element matching bound and filter constraints,
         * or null if none.
         * @return matching element, or null if none.
         */
        public abstract T any();

        /**
         * Returns the minimum element, or null if empty
         * @param comparator the comparator
         * @return minimum element, or null if empty
         */
        public T min(Comparator<? super T> comparator) {
            return reduce(new MinReducer<T>(comparator), null);
        }

        /**
         * Returns the minimum element, or null if empty,
         * assuming that all elements are Comparables
         * @return minimum element, or null if empty
         * @throws ClassCastException if any element is not Comparable.
         */
        public T min() {
            return reduce(new RawMinReducer<T>(), null);
        }

        /**
         * Returns the maximum element, or null if empty
         * @param comparator the comparator
         * @return maximum element, or null if empty
         */
        public T max(Comparator<? super T> comparator) {
            return reduce(new MaxReducer<T>(comparator), null);
        }

        /**
         * Returns the maximum element, or null if empty
         * assuming that all elements are Comparables
         * @return maximum element, or null if empty
         * @throws ClassCastException if any element is not Comparable.
         */
        public T max() {
            return reduce(new RawMaxReducer<T>(), null);
        }

        /**
         * Returns the index corresponding to the least element
         * or -1 if empty
         * @param comparator the comparator
         * @return the index of least element or -1 if empty.
         */
        public int indexOfMin(Comparator<? super T> comparator) {
            FJMinIndex<T,T> f = new FJMinIndex<T,T>
                (this, fromIndex, toIndex, comparator, false);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the greatest
         * element, or -1 if empty
         * @param comparator the comparator
         * @return the index of greatest element or -1 if empty.
         */
        public int indexOfMax(Comparator<? super T> comparator) {
            FJMinIndex<T,T> f = new FJMinIndex<T,T>
                (this, fromIndex, toIndex, comparator, true);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the least element
         * or -1 if empty,
         * assuming that all elements are Comparables
         * @return the index of least element or -1 if empty.
         * @throws ClassCastException if any element is not Comparable.
         */
        public int indexOfMin() {
            FJMinIndex<T,T> f = new FJMinIndex<T,T>
                (this, fromIndex, toIndex,
                 (Comparator<? super T>)(RawComparator.cmp), false);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the greatest element or
         * -1 if empty, assuming that all elements are Comparables
         * @return the index of greatest element or -1 if empty.
         * @throws ClassCastException if any element is not Comparable.
         */
        public int indexOfMax() {
            FJMinIndex<T,T> f = new FJMinIndex<T,T>
                (this, fromIndex, toIndex,
                 (Comparator<? super T>)(RawComparator.cmp), true);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns a new ParallelArray holding elements
         * @return a new ParallelArray holding elements
         */
        public abstract ParallelArray<T> newArray();

        /**
         * Returns a new ParallelArray with the given element type
         * holding elements
         * @param elementType the type of the elements
         * @return a new ParallelArray holding elements
         */
        public abstract ParallelArray<T> newArray
            (Class<? super T> elementType);

        /**
         * Replaces elements with the results of applying the given
         * mapper to their current values.
         * @param mapper the mapper
         */
        public void replaceWithTransform
            (Mapper<? super T, ? extends T> mapper) {
            ex.invoke(new FJTransform<T>(this, fromIndex, toIndex, mapper));
        }

        abstract void leafTransform
            (int lo, int hi, Mapper<? super T, ? extends T> mapper);

        /**
         * Replaces elements with the results of applying the given
         * mapper to their indices
         * @param mapper the mapper
         */
        public void replaceWithMappedIndex
            (MapperFromInt<? extends T> mapper) {
            ex.invoke(new FJIndexMap<T>(this, fromIndex, toIndex, mapper));
        }

        abstract void leafIndexMap
            (int lo, int hi, MapperFromInt<? extends T> mapper);

        /**
         * Replaces elements with results of applying the given
         * generator.
         * @param generator the generator
         */
        public void replaceWithGeneratedValue
            (Generator<? extends T> generator) {
            ex.invoke(new FJGenerate<T>
                      (this, fromIndex, toIndex, generator));
        }

        abstract void leafGenerate
            (int lo, int hi, Generator<? extends T> generator);

        /**
         * Replaces elements with the given value.
         * @param value the value
         */
        public void replaceWithValue(T value) {
            ex.invoke(new FJFill<T>(this, fromIndex, toIndex, value));
        }

        abstract void leafFill(int lo, int hi, T value);

        /**
         * Replaces elements with results of applying
         * <tt>combine(thisElement, otherElement)</tt>
         * @param other the other array
         * @param combiner the combiner
         * @throws ArrayIndexOutOfBoundsException if other array has
         * fewer than <tt>toIndex</tt> elements.
         */
        public void replaceWithCombination(ParallelArray<? extends T> other,
                                           Reducer<T> combiner) {
            replaceWithCombination(other.array, combiner);
        }

        /**
         * Replaces elements with results of applying
         * <tt>combine(thisElement, otherElement)</tt>
         * @param other the other array
         * @param combiner the combiner
         * @throws ArrayIndexOutOfBoundsException if other array has
         * fewer than <tt>toIndex</tt> elements.
         */
        public void replaceWithCombination(T[] other,
                                           Reducer<T> combiner) {
            if (other.length < toIndex)
                throw new ArrayIndexOutOfBoundsException();
            ex.invoke(new FJCombineInPlace<T>
                      (this, fromIndex, toIndex, other, combiner));
        }

        abstract void leafCombineInPlace
            (int lo, int hi, T[] other, Reducer<T> combiner);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract <U> WithMapping<T, U> withMapping
            (Mapper<? super T, ? extends U> mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithDoubleMapping<T> withMapping
            (MapperToDouble<? super T> mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithLongMapping<T> withMapping
            (MapperToLong<? super T> mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithIntMapping<T> withMapping
            (MapperToInt<? super T> mapper);

    }

    /**
     * A restriction of parallel array operations to apply only within
     * a given range of indices.
     */
    public static final class WithBounds<T> extends WithFilter<T> {
        WithBounds(ForkJoinExecutor ex, T[] array,
                   int fromIndex, int toIndex) {
            super(ex, array, fromIndex, toIndex);
            if (fromIndex > toIndex)
                throw new IllegalArgumentException
                    ("fromIndex(" + fromIndex +
                     ") > toIndex(" + toIndex+")");
            if (fromIndex < 0)
                throw new ArrayIndexOutOfBoundsException(fromIndex);
            if (toIndex > array.length)
                throw new ArrayIndexOutOfBoundsException(toIndex);
        }

        WithBounds(ForkJoinExecutor ex, T[] array) {
            super(ex, array, 0, array.length);
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * only on the elements of the array for which the given selector
         * returns true
         * @param selector the selector
         * @return operation prefix
         */
        public WithFilter<T> withFilter(Predicate<? super T> selector) {
            return new WithBoundedFilter<T>
                (ex, array, fromIndex, toIndex, selector);
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public <U> WithMapping<T, U> withMapping
            (Mapper<? super T, ? extends U> mapper) {
            return new WithBoundedMapping<T,U>
                (ex, array, fromIndex,toIndex, mapper);
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public WithDoubleMapping<T> withMapping
            (MapperToDouble<? super T> mapper) {
            return new WithBoundedDoubleMapping<T>
                (ex, array, fromIndex, toIndex, mapper);
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public WithLongMapping<T> withMapping
            (MapperToLong<? super T> mapper) {
            return new WithBoundedLongMapping<T>
                (ex, array, fromIndex, toIndex, mapper);
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public WithIntMapping<T> withMapping
            (MapperToInt<? super T> mapper) {
            return new WithBoundedIntMapping<T>
                (ex, array, fromIndex, toIndex, mapper);
        }

        /**
         * Returns the index of some element matching bound
         * filter constraints, or -1 if none.
         * @return index of matching element, or -1 if none.
         */
        public int anyIndex() {
            return (fromIndex < toIndex)? fromIndex : -1;
        }

        /**
         * Returns some element matching bound
         * constraints, or null if none.
         * @return matching element, or null if none.
         */
        public T any() {
            return (fromIndex < toIndex)? array[fromIndex] : null;
        }

        /**
         * Returns a ParallelArray containing results of
         * applying <tt>combine(thisElement, otherElement)</tt>
         * for each element.
         * @param other the other array
         * @param combiner the combiner
         * @return the array of mappings
         * @throws ArrayIndexOutOfBoundsException if other array is
         * shorter than this array.
         */
        public <U,V> ParallelArray<V> combine
            (U[] other,
             Combiner<? super T, ? super U, ? extends V> combiner) {
            if (other.length < array.length)
                throw new ArrayIndexOutOfBoundsException();
            V[] dest = (V[])new Object[toIndex];
            ex.invoke(new FJCombine<T,U,V>(this, fromIndex, toIndex,
                                           other, dest, combiner));
            return new ParallelArray<V>(ex, dest);
        }

        /**
         * Returns a ParallelArray containing results of
         * applying <tt>combine(thisElement, otherElement)</tt>
         * for each element.
         * @param other the other array
         * @param combiner the combiner
         * @param elementType the type of elements of returned array
         * @return the array of mappings
         * @throws ArrayIndexOutOfBoundsException if other array is
         * shorter than this array.
         */
        public <U,V> ParallelArray<V> combine
            (U[] other,
             Combiner<? super T, ? super U, ? extends V> combiner,
             Class<? super V> elementType) {
            if (other.length < array.length)
                throw new ArrayIndexOutOfBoundsException();
            V[] dest = (V[])
                java.lang.reflect.Array.newInstance(elementType, toIndex);
            ex.invoke(new FJCombine<T,U,V>(this, fromIndex, toIndex,
                                           other, dest, combiner));
            return new ParallelArray<V>(ex, dest);
        }


        /**
         * Returns a ParallelArray containing results of
         * applying <tt>combine(thisElement, otherElement)</tt>
         * for each element.
         * @param other the other array
         * @param combiner the combiner
         * @return the array of mappings
         * @throws ArrayIndexOutOfBoundsException if other array is
         * shorter than this array.
         */
        public <U,V> ParallelArray<V> combine
            (ParallelArray<U> other,
             Combiner<? super T, ? super U, ? extends V> combiner) {
            return combine(other.array, combiner);
        }

        /**
         * Returns a ParallelArray containing results of
         * applying <tt>combine(thisElement, otherElement)</tt>
         * for each element.
         * @param other the other array
         * @param combiner the combiner
         * @param elementType the type of elements of returned array
         * @return the array of mappings
         * @throws ArrayIndexOutOfBoundsException if other array is
         * shorter than this array.
         */
        public <U,V> ParallelArray<V> combine
            (ParallelArray<U> other,
             Combiner<? super T, ? super U, ? extends V> combiner,
             Class<? super V> elementType) {
            return combine(other.array, combiner, elementType);
        }

        /**
         * Returns a new ParallelArray holding elements
         * @return a new ParallelArray holding elements
         */
        public ParallelArray<T> newArray() {
            // For now, avoid copyOf so people can compile with Java5
            int size = toIndex - fromIndex;
            T[] dest = (T[])java.lang.reflect.Array.newInstance
                (array.getClass().getComponentType(), size);
            System.arraycopy(array, fromIndex, dest, 0, size);
            return new ParallelArray<T>(ex, dest);
        }

        /**
         * Returns a new ParallelArray with the given element type holding
         * elements
         * @param elementType the type of the elements
         * @return a new ParallelArray holding elements
         */
        public ParallelArray<T> newArray(Class<? super T> elementType) {
            int size = toIndex - fromIndex;
            T[] dest = (T[])java.lang.reflect.Array.newInstance
                (elementType, size);
            System.arraycopy(array, fromIndex, dest, 0, size);
            return new ParallelArray<T>(ex, dest);
        }

        /**
         * Returns the number of elements within bounds
         * @return the number of elements within bounds
         */
        public int size() {
            return toIndex - fromIndex;
        }

        /**
         * Replaces each element with the running cumulation of applying
         * the given reducer.
         * @param reducer the reducer
         * @param base the result for an empty array
         */
        public void cumulate(Reducer<T> reducer, T base) {
            FJCumulateOp<T> op = new FJCumulateOp<T>
                (ex, array, fromIndex, toIndex, reducer, base);
            if (op.granularity >= toIndex - fromIndex)
                op.sumAndCumulateLeaf(fromIndex, toIndex);
            else {
                FJScan<T> r = new FJScan<T>(null, op, fromIndex, toIndex);
                ex.invoke(r);
            }
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
            FJPrecumulateOp<T> op = new FJPrecumulateOp<T>
                (ex, array, fromIndex, toIndex, reducer, base);
            if (op.granularity >= toIndex - fromIndex)
                return op.sumAndCumulateLeaf(fromIndex, toIndex);
            else {
                FJScan<T> r = new FJScan<T>(null, op, fromIndex, toIndex);
                ex.invoke(r);
                return r.out;
            }
        }

        /**
         * Sorts the elements.
         * Unlike Arrays.sort, this sort does
         * not guarantee that elements with equal keys maintain their
         * relative position in the array.
         * @param cmp the comparator to use
         */
        public void sort(Comparator<? super T> cmp) {
            int n = toIndex - fromIndex;
            T[] ws = (T[])java.lang.reflect.Array.
                newInstance(array.getClass().getComponentType(), toIndex);
            ex.invoke(new FJSorter<T>(cmp, array, ws, fromIndex,
                                      n, granularity));
        }

        /**
         * Sorts the elements, assuming all elements are
         * Comparable. Unlike Arrays.sort, this sort does not
         * guarantee that elements with equal keys maintain their relative
         * position in the array.
         * @throws ClassCastException if any element is not Comparable.
         */
        public void sort() {
            Class tclass = array.getClass().getComponentType();
            if (!Comparable.class.isAssignableFrom(tclass))
                sort((Comparator<? super T>)(RawComparator.cmp));
            Comparable[] ca = (Comparable[])array;
            int n = toIndex - fromIndex;
            Comparable[] ws = (Comparable[])java.lang.reflect.Array.
                newInstance(tclass, n);
            ex.invoke(new FJComparableSorter(ca, ws, fromIndex,
                                             n, granularity));
        }

        void leafApply(int lo, int hi, Procedure<? super T> procedure) {
            for (int i = lo; i < hi; ++i)
                procedure.apply(array[i]);
        }

        void leafTransform(int lo, int hi,
                           Mapper<? super T, ? extends T> mapper) {
            for (int i = lo; i < hi; ++i)
                array[i] = mapper.map(array[i]);
        }

        void leafIndexMap(int lo, int hi,
                          MapperFromInt<? extends T> mapper) {
            for (int i = lo; i < hi; ++i)
                array[i] = mapper.map(i);
        }

        void leafGenerate(int lo, int hi,
                          Generator<? extends T> generator) {
            for (int i = lo; i < hi; ++i)
                array[i] = generator.generate();
        }
        void leafFill(int lo, int hi,
                      T value) {
            for (int i = lo; i < hi; ++i)
                array[i] = value;
        }
        void leafCombineInPlace(int lo, int hi,
                                T[] other, Reducer<T> combiner) {
            for (int i = lo; i < hi; ++i)
                array[i] = combiner.combine(array[i], other[i]);
        }

        T leafReduce(int lo, int hi,
                     Reducer<T> reducer, T base) {
            if (lo >= hi)
                return base;
            T r = array[lo];
            for (int i = lo+1; i < hi; ++i)
                r = reducer.combine(r, array[i]);
            return r;
        }

        void leafMinIndex(int lo, int hi,
                          Comparator<? super T> comparator,
                          boolean reverse,
                          FJMinIndex<T,T> task) {
            T best = null;
            int bestIndex = -1;
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                int c = 1;
                if (bestIndex >= 0) {
                    c = comparator.compare(best, x);
                    if (reverse) c = -c;
                }
                if (c > 0) {
                    bestIndex = i;
                    best = x;
                }
            }
            task.result = best;
            task.indexResult = bestIndex;
        }
    }

    static final class WithBoundedFilter<T> extends WithFilter<T> {
        final Predicate<? super T> selector;
        WithBoundedFilter(ForkJoinExecutor ex, T[] array,
                          int fromIndex, int toIndex,
                          Predicate<? super T> selector) {
            super(ex, array, fromIndex, toIndex);
            this.selector = selector;
        }

        public <U> WithMapping<T, U> withMapping
            (Mapper<? super T, ? extends U> mapper) {
            return new WithBoundedFilteredMapping<T,U>
                (ex, array, fromIndex, toIndex, selector, mapper);
        }

        public WithDoubleMapping<T> withMapping
            (MapperToDouble<? super T> mapper) {
            return new WithBoundedFilteredDoubleMapping<T>
                (ex, array, fromIndex, toIndex, selector, mapper);
        }

        public WithLongMapping<T> withMapping
            (MapperToLong<? super T> mapper) {
            return new WithBoundedFilteredLongMapping<T>
                (ex, array, fromIndex, toIndex, selector, mapper);
        }

        public WithIntMapping<T> withMapping
            (MapperToInt<? super T> mapper) {
            return new WithBoundedFilteredIntMapping<T>
                (ex, array, fromIndex, toIndex, selector, mapper);
        }

        public int anyIndex() {
            AtomicInteger result = new AtomicInteger(-1);
            FJSelectAny<T> f =
                new FJSelectAny<T>(this, fromIndex, toIndex,
                                   selector, result);
            ex.invoke(f);
            return result.get();
        }

        public T any() {
            int idx = anyIndex();
            return (idx < 0)?  null : array[idx];
        }

        public ParallelArray<T> newArray() {
            Class<? super T> elementType =
                (Class<? super T>)array.getClass().getComponentType();
            FJPlainRefSelectAllDriver<T> r =
                new FJPlainRefSelectAllDriver<T>
                (this, selector, elementType);
            ex.invoke(r);
            return new ParallelArray<T>(ex, r.results);
        }

        public ParallelArray<T> newArray(Class<? super T> elementType) {
            FJPlainRefSelectAllDriver<T> r =
                new FJPlainRefSelectAllDriver<T>
                (this, selector, elementType);
            ex.invoke(r);
            return new ParallelArray<T>(ex, r.results);
        }

        public int size() {
            FJCountAll<T> f = new FJCountAll<T>
                (this, fromIndex, toIndex, selector);
            ex.invoke(f);
            return f.result;
        }

        void leafApply(int lo, int hi, Procedure<? super T>  procedure) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x))
                    procedure.apply(x);
            }
        }

        void leafTransform(int lo, int hi,
                           Mapper<? super T, ? extends T> mapper) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x))
                    array[i] = mapper.map(x);
            }
        }
        void leafIndexMap(int lo, int hi,
                          MapperFromInt<? extends T> mapper) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x))
                    array[i] = mapper.map(i);
            }
        }

        void leafGenerate(int lo, int hi,
                          Generator<? extends T> generator) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x))
                    array[i] = generator.generate();
            }
        }
        void leafFill(int lo, int hi,
                      T value) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x))
                    array[i] = value;
            }
        }
        void leafCombineInPlace(int lo, int hi,
                                T[] other, Reducer<T> combiner) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x))
                    array[i] = combiner.combine(x, other[i]);
            }
        }
        T leafReduce(int lo, int hi,
                     Reducer<T> reducer, T base) {
            boolean gotFirst = false;
            T r = base;
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x)) {
                    if (!gotFirst) {
                        gotFirst = true;
                        r = x;
                    }
                    else
                        r = reducer.combine(r, x);
                }
            }
            return r;
        }

        void leafMinIndex(int lo, int hi,
                          Comparator<? super T> comparator,
                          boolean reverse,
                          FJMinIndex<T,T> task) {
            T best = null;
            int bestIndex = -1;
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x)) {
                    int c = 1;
                    if (bestIndex >= 0) {
                        c = comparator.compare(best, x);
                        if (reverse) c = -c;
                    }
                    if (c > 0) {
                        bestIndex = i;
                        best = x;
                    }
                }
            }
            task.result = best;
            task.indexResult = bestIndex;
        }

    }

    static final class WithBoundedMapping<T,U> extends WithMapping<T,U> {
        final Mapper<? super T, ? extends U> mapper;
        WithBoundedMapping(ForkJoinExecutor ex, T[] array,
                           int fromIndex, int toIndex,
                           Mapper<? super T, ? extends U> mapper) {
            super(ex, array, fromIndex, toIndex);
            this.mapper = mapper;
        }

        public ParallelArray<U> newArray() {
            int n = toIndex - fromIndex;
            U[] dest = (U[])new Object[n];
            FJMap<T,U> f =
                new FJMap<T,U>(this, fromIndex, toIndex, dest, mapper);
            ex.invoke(f);
            return new ParallelArray<U>(ex, dest);
        }

        public ParallelArray<U> newArray(Class<? super U> elementType) {
            int n = toIndex - fromIndex;
            U[] dest = (U[])
                java.lang.reflect.Array.newInstance(elementType, n);
            FJMap<T,U> f =
                new FJMap<T,U>(this, fromIndex, toIndex, dest, mapper);
            ex.invoke(f);
            return new ParallelArray<U>(ex, dest);
        }

        public int size() {
            return toIndex - fromIndex;
        }

        public int anyIndex() {
            return (fromIndex < toIndex)? fromIndex : -1;
        }

        public U any() {
            return (fromIndex < toIndex)?
                mapper.map(array[fromIndex]) : null;
        }

        void leafApply(int lo, int hi, Procedure<? super U>  procedure) {
            for (int i = lo; i < hi; ++i)
                procedure.apply(mapper.map(array[i]));
        }

        U leafReduce(int lo, int hi,
                     Reducer<U> reducer, U base) {
            if (lo >= hi)
                return base;
            U r = mapper.map(array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.combine(r, mapper.map(array[i]));
            return r;
        }

        void leafMinIndex(int lo, int hi,
                          Comparator<? super U> comparator,
                          boolean reverse,
                          FJMinIndex<T,U> task) {
            U best = null;
            int bestIndex = -1;
            for (int i = lo; i < hi; ++i) {
                U x = mapper.map(array[i]);
                int c = 1;
                if (bestIndex >= 0) {
                    c = comparator.compare(best, x);
                    if (reverse) c = -c;
                }
                if (c > 0) {
                    bestIndex = i;
                    best = x;
                }
            }
            task.result = best;
            task.indexResult = bestIndex;
        }
    }

    static final class WithBoundedFilteredMapping<T,U>
        extends WithMapping<T,U> {
        final Predicate<? super T> selector;
        final Mapper<? super T, ? extends U> mapper;
        WithBoundedFilteredMapping(ForkJoinExecutor ex, T[] array,
                                   int fromIndex, int toIndex,
                                   Predicate<? super T> selector,
                                   Mapper<? super T, ? extends U> mapper) {
            super(ex, array, fromIndex, toIndex);
            this.selector = selector;
            this.mapper = mapper;
        }
        public ParallelArray<U> newArray() {
            FJMapRefSelectAllDriver<T,U> r =
                new FJMapRefSelectAllDriver<T,U>
                (this, selector, null, mapper);
            ex.invoke(r);
            return new ParallelArray<U>(ex, r.results);
        }

        public ParallelArray<U> newArray(Class<? super U> elementType) {
            FJMapRefSelectAllDriver<T,U> r =
                new FJMapRefSelectAllDriver<T,U>
                (this, selector, elementType, mapper);
            ex.invoke(r);
            return new ParallelArray<U>(ex, r.results);
        }

        public int size() {
            FJCountAll<T> f = new FJCountAll<T>
                (this, fromIndex, toIndex, selector);
            ex.invoke(f);
            return f.result;
        }

        public int anyIndex() {
            AtomicInteger result = new AtomicInteger(-1);
            FJSelectAny<T> f =
                new FJSelectAny<T>(this, fromIndex, toIndex,
                                   selector, result);
            ex.invoke(f);
            return result.get();
        }

        public U any() {
            int idx = anyIndex();
            return (idx < 0)?  null : mapper.map(array[idx]);
        }

        void leafApply(int lo, int hi, Procedure<? super U>  procedure) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x))
                    procedure.apply(mapper.map(x));
            }
        }
        U leafReduce(int lo, int hi,
                     Reducer<U> reducer, U base) {
            boolean gotFirst = false;
            U r = base;
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x)) {
                    U y = mapper.map(x);
                    if (!gotFirst) {
                        gotFirst = true;
                        r = y;
                    }
                    else
                        r = reducer.combine(r, y);
                }
            }
            return r;
        }

        void leafRefMap(int lo, int hi,
                        U[] dest) {
            int k = lo - fromIndex;
            for (int i = lo; i < hi; ++i)
                dest[k++] = mapper.map(array[i]);
        }

        void leafMinIndex(int lo, int hi,
                          Comparator<? super U> comparator,
                          boolean reverse,
                          FJMinIndex<T,U> task) {
            U best = null;
            int bestIndex = -1;
            for (int i = lo; i < hi; ++i) {
                T t = array[i];
                if (selector.evaluate(t)) {
                    U x = mapper.map(t);
                    int c = 1;
                    if (bestIndex >= 0) {
                        c = comparator.compare(best, x);
                        if (reverse) c = -c;
                    }
                    if (c > 0) {
                        bestIndex = i;
                        best = x;
                    }
                }
            }
            task.result = best;
            task.indexResult = bestIndex;
        }
    }

    /**
     * A modifier for parallel array operations to apply to mappings
     * of elements to doubles, not to the elements themselves
     */
    public static abstract class WithDoubleMapping<T>
        extends Params<T> {
        WithDoubleMapping(ForkJoinExecutor ex, T[] array,
                          int fromIndex, int toIndex) {
            super(ex, array, fromIndex, toIndex);
        }

        /**
         * Applies the given procedure
         * @param procedure the procedure
         */
        public void apply(DoubleProcedure procedure) {
            ex.invoke(new FJDoubleApply<T>
                      (this, fromIndex, toIndex, procedure));
        }

        abstract void leafApply(int lo, int hi,
                                DoubleProcedure procedure);

        /**
         * Returns reduction of mapped elements
         * @param reducer the reducer
         * @param base the result for an empty array
         * @return reduction
         */
        public double reduce(DoubleReducer reducer, double base) {
            FJDoubleReduce<T> f =
                new FJDoubleReduce<T>
                (this, fromIndex, toIndex, reducer, base);
            ex.invoke(f);
            return f.result;
        }

        abstract double leafReduce
            (int lo, int hi, DoubleReducer reducer, double base);

        /**
         * Returns the minimum element, or Double.MAX_VALUE if empty
         * @return minimum element, or Double.MAX_VALUE if empty
         */
        public double min() {
            return reduce(NaturalDoubleMinReducer.min, Double.MAX_VALUE);
        }

        /**
         * Returns the minimum element, or Double.MAX_VALUE if empty
         * @param comparator the comparator
         * @return minimum element, or Double.MAX_VALUE if empty
         */
        public double min(DoubleComparator comparator) {
            return reduce(new DoubleMinReducer(comparator),
                          Double.MAX_VALUE);
        }

        /**
         * Returns the maximum element, or Double.MIN_VALUE if empty
         * @return maximum element, or Double.MIN_VALUE if empty
         */
        public double max() {
            return reduce(NaturalDoubleMaxReducer.max, Double.MIN_VALUE);
        }

        /**
         * Returns the maximum element, or Double.MIN_VALUE if empty
         * @param comparator the comparator
         * @return maximum element, or Double.MIN_VALUE if empty
         */
        public double max(DoubleComparator comparator) {
            return reduce(new DoubleMaxReducer(comparator),
                          Double.MIN_VALUE);
        }

        /**
         * Returns the sum of elements
         * @return the sum of elements
         */
        public double sum() {
            return reduce(DoubleAdder.adder, 0);
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is least, or -1 if empty
         * @return the index of least element or -1 if empty.
         */
        public int indexOfMin() {
            FJDoubleMinIndex<T> f = new FJDoubleMinIndex<T>
                (this, fromIndex, toIndex,
                 NaturalDoubleComparator.comparator, false);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is greatest, or -1 if empty
         * @return the index of greatest element or -1 if empty.
         */
        public int indexOfMax() {
            FJDoubleMinIndex<T> f = new FJDoubleMinIndex<T>
                (this, fromIndex, toIndex,
                 NaturalDoubleComparator.comparator, true);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is least, or -1 if empty
         * @param comparator the comparator
         * @return the index of least element or -1 if empty.
         */
        public int indexOfMin(DoubleComparator comparator) {
            FJDoubleMinIndex<T> f = new FJDoubleMinIndex<T>
                (this, fromIndex, toIndex, comparator, false);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is greatest, or -1 if empty
         * @param comparator the comparator
         * @return the index of greatest element or -1 if empty.
         */
        public int indexOfMax(DoubleComparator comparator) {
            FJDoubleMinIndex<T> f = new FJDoubleMinIndex<T>
                (this, fromIndex, toIndex, comparator, true);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns a new ParallelDoubleArray holding mappings
         * @return a new ParallelDoubleArray holding mappings
         */
        public abstract ParallelDoubleArray newArray();

        /**
         * Return the number of elements selected using bound or
         * filter restrictions. Note that this method must evaluate
         * all selectors to return its result.
         * @return the number of elements
         */
        public abstract int size();

        /**
         * Returns the index of some element matching bound and filter
         * constraints, or -1 if none.
         * @return index of matching element, or -1 if none.
         */
        public abstract int anyIndex();

        /**
         * Returns mapping of some element matching bound and filter
         * constraints
         * @return mapping of matching element
         * @throws NoSuchElementException if empty
         */
        public abstract double any();

        abstract void leafMinIndex(int lo, int hi,
                                   DoubleComparator comparator,
                                   boolean reverse,
                                   FJDoubleMinIndex<T> task);

    }

    static final class WithBoundedDoubleMapping<T>
        extends WithDoubleMapping<T> {
        final MapperToDouble<? super T> mapper;
        WithBoundedDoubleMapping(ForkJoinExecutor ex, T[] array,
                                 int fromIndex, int toIndex,
                                 MapperToDouble<? super T> mapper) {
            super(ex, array, fromIndex, toIndex);
            this.mapper = mapper;
        }

        public ParallelDoubleArray newArray() {
            double[] dest = new double[toIndex - fromIndex];
            FJDoubleMap<T> f =
                new FJDoubleMap<T>(this, fromIndex, toIndex, dest, mapper);
            ex.invoke(f);
            return new ParallelDoubleArray(ex, dest);
        }

        public int size() {
            return toIndex - fromIndex;
        }

        void leafApply(int lo, int hi, DoubleProcedure procedure) {
            for (int i = lo; i < hi; ++i)
                procedure.apply(mapper.map(array[i]));
        }

        void leafMap(int lo, int hi,
                     double[] dest) {
            int k = lo - fromIndex;
            for (int i = lo; i < hi; ++i)
                dest[k++] = mapper.map(array[i]);
        }

        double leafReduce(int lo, int hi,
                          DoubleReducer reducer, double base) {
            if (lo >= hi)
                return base;
            double r = mapper.map(array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.combine(r, mapper.map(array[i]));
            return r;
        }

        void leafMinIndex(int lo, int hi,
                          DoubleComparator comparator,
                          boolean reverse,
                          FJDoubleMinIndex<T> task) {
            double best = reverse? Double.MIN_VALUE : Double.MAX_VALUE;
            int bestIndex = -1;
            for (int i = lo; i < hi; ++i) {
                double x = mapper.map(array[i]);
                int c = 1;
                if (bestIndex >= 0) {
                    c = comparator.compare(best, x);
                    if (reverse) c = -c;
                }
                if (c > 0) {
                    bestIndex = i;
                    best = x;
                }
            }
            task.result = best;
            task.indexResult = bestIndex;
        }

        public int anyIndex() {
            return (fromIndex < toIndex)? fromIndex : -1;
        }

        public double any() {
            if (fromIndex >= toIndex)
                throw new NoSuchElementException();
            return mapper.map(array[fromIndex]);
        }


    }

    static final class WithBoundedFilteredDoubleMapping<T>
        extends WithDoubleMapping<T> {
        final Predicate<? super T> selector;
        final MapperToDouble<? super T> mapper;
        WithBoundedFilteredDoubleMapping
            (ForkJoinExecutor ex, T[] array,
             int fromIndex, int toIndex,
             Predicate<? super T> selector,
             MapperToDouble<? super T> mapper) {
            super(ex, array, fromIndex, toIndex);
            this.selector = selector;
            this.mapper = mapper;
        }
        public ParallelDoubleArray  newArray() {
            FJDoubleMapSelectAllDriver<T> r =
                new FJDoubleMapSelectAllDriver<T>(this, selector, mapper);
            ex.invoke(r);
            return new ParallelDoubleArray(ex, r.results);
        }

        public int size() {
            FJCountAll<T> f = new FJCountAll<T>
                (this, fromIndex, toIndex, selector);
            ex.invoke(f);
            return f.result;
        }

        double leafReduce(int lo, int hi,
                          DoubleReducer reducer, double base) {
            boolean gotFirst = false;
            double r = base;
            for (int i = lo; i < hi; ++i) {
                T t = array[i];
                if (selector.evaluate(t)) {
                    double y = mapper.map(t);
                    if (!gotFirst) {
                        gotFirst = true;
                        r = y;
                    }
                    else
                        r = reducer.combine(r, y);
                }
            }
            return r;
        }

        void leafApply(int lo, int hi, DoubleProcedure procedure) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x))
                    procedure.apply(mapper.map(x));
            }
        }

        void leafMinIndex(int lo, int hi,
                          DoubleComparator comparator,
                          boolean reverse,
                          FJDoubleMinIndex<T> task) {
            double best = reverse? Double.MIN_VALUE : Double.MAX_VALUE;
            int bestIndex = -1;
            for (int i = lo; i < hi; ++i) {
                T t = array[i];
                if (selector.evaluate(t)) {
                    double x = mapper.map(t);
                    int c = 1;
                    if (bestIndex >= 0) {
                        c = comparator.compare(best, x);
                        if (reverse) c = -c;
                    }
                    if (c > 0) {
                        bestIndex = i;
                        best = x;
                    }
                }
            }
            task.result = best;
            task.indexResult = bestIndex;
        }

        public int anyIndex() {
            AtomicInteger result = new AtomicInteger(-1);
            FJSelectAny<T> f =
                new FJSelectAny<T>(this, fromIndex, toIndex,
                                   selector, result);
            ex.invoke(f);
            return result.get();
        }

        public double any() {
            int idx = anyIndex();
            if (idx < 0)
                throw new NoSuchElementException();
            return mapper.map(array[idx]);
        }


    }

    /**
     * A modifier for parallel array operations to apply to mappings
     * of elements to longs, not to the elements themselves
     */
    public static abstract class WithLongMapping<T>
        extends Params<T> {
        WithLongMapping(ForkJoinExecutor ex, T[] array,
                        int fromIndex, int toIndex) {
            super(ex, array, fromIndex, toIndex);
        }

        /**
         * Applies the given procedure
         * @param procedure the procedure
         */
        public void apply(LongProcedure procedure) {
            ex.invoke(new FJLongApply<T>
                      (this, fromIndex, toIndex, procedure));
        }

        abstract void leafApply(int lo, int hi,
                                LongProcedure procedure);


        /**
         * Returns reduction of mapped elements
         * @param reducer the reducer
         * @param base the result for an empty array
         * @return reduction
         */
        public long reduce(LongReducer reducer, long base) {
            FJLongReduce<T> f =
                new FJLongReduce<T>(this, fromIndex, toIndex, reducer, base);
            ex.invoke(f);
            return f.result;
        }

        abstract long leafReduce(int lo, int hi,
                                 LongReducer reducer, long base);

        /**
         * Returns the minimum element, or Long.MAX_VALUE if empty
         * @return minimum element, or Long.MAX_VALUE if empty
         */
        public long min() {
            return reduce(NaturalLongMinReducer.min, Long.MAX_VALUE);
        }

        /**
         * Returns the minimum element, or Long.MAX_VALUE if empty
         * @param comparator the comparator
         * @return minimum element, or Long.MAX_VALUE if empty
         */
        public long min(LongComparator comparator) {
            return reduce(new LongMinReducer(comparator),
                          Long.MAX_VALUE);
        }

        /**
         * Returns the maximum element, or Long.MIN_VALUE if empty
         * @return maximum element, or Long.MIN_VALUE if empty
         */
        public long max() {
            return reduce(NaturalLongMaxReducer.max, Long.MIN_VALUE);
        }

        /**
         * Returns the maximum element, or Long.MIN_VALUE if empty
         * @param comparator the comparator
         * @return maximum element, or Long.MIN_VALUE if empty
         */
        public long max(LongComparator comparator) {
            return reduce(new LongMaxReducer(comparator),
                          Long.MIN_VALUE);
        }

        /**
         * Returns the sum of elements
         * @return the sum of elements
         */
        public long sum() {
            return reduce(LongAdder.adder, 0);
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is least, or -1 if empty
         * @return the index of least element or -1 if empty.
         */
        public int indexOfMin() {
            FJLongMinIndex<T> f = new FJLongMinIndex<T>
                (this, fromIndex, toIndex,
                 NaturalLongComparator.comparator, false);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is greatest, or -1 if empty
         * @return the index of greatest element or -1 if empty.
         */
        public int indexOfMax() {
            FJLongMinIndex<T> f = new FJLongMinIndex<T>
                (this, fromIndex, toIndex,
                 NaturalLongComparator.comparator, true);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is least, or -1 if empty
         * @param comparator the comparator
         * @return the index of least element or -1 if empty.
         */
        public int indexOfMin(LongComparator comparator) {
            FJLongMinIndex<T> f = new FJLongMinIndex<T>
                (this, fromIndex, toIndex, comparator, false);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is greatest, or -1 if empty
         * @param comparator the comparator
         * @return the index of greatest element or -1 if empty.
         */
        public int indexOfMax(LongComparator comparator) {
            FJLongMinIndex<T> f = new FJLongMinIndex<T>
                (this, fromIndex, toIndex, comparator, true);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns a new ParallelLongArray holding mappings
         * @return a new ParallelLongArray holding mappings
         */
        public abstract ParallelLongArray newArray();

        /**
         * Return the number of elements selected using bound or
         * filter restrictions. Note that this method must evaluate
         * all selectors to return its result.
         * @return the number of elements
         */
        public abstract int size();

        /**
         * Returns the index of some element matching bound and filter
         * constraints, or -1 if none.
         * @return index of matching element, or -1 if none.
         */
        public abstract int anyIndex();

        /**
         * Returns mapping of some element matching bound and filter
         * constraints
         * @return mapping of matching element
         * @throws NoSuchElementException if empty
         */
        public abstract long any();

        abstract void leafMinIndex(int lo, int hi,
                                   LongComparator comparator,
                                   boolean reverse,
                                   FJLongMinIndex<T> task);

    }

    static final class WithBoundedLongMapping<T>
        extends WithLongMapping<T> {
        final MapperToLong<? super T> mapper;
        WithBoundedLongMapping(ForkJoinExecutor ex, T[] array,
                               int fromIndex, int toIndex,
                               MapperToLong<? super T> mapper) {
            super(ex, array, fromIndex, toIndex);
            this.mapper = mapper;
        }

        public ParallelLongArray newArray() {
            long[] dest = new long[toIndex - fromIndex];
            FJLongMap<T> f =
                new FJLongMap<T>(this, fromIndex, toIndex, dest, mapper);
            ex.invoke(f);
            return new ParallelLongArray(ex, dest);
        }

        public int size() {
            return toIndex - fromIndex;
        }
        void leafApply(int lo, int hi, LongProcedure procedure) {
            for (int i = lo; i < hi; ++i)
                procedure.apply(mapper.map(array[i]));
        }


        void leafMap(int lo, int hi,
                     long[] dest) {
            int k = lo - fromIndex;
            for (int i = lo; i < hi; ++i)
                dest[k++] = mapper.map(array[i]);
        }
        long leafReduce(int lo, int hi,
                        LongReducer reducer, long base) {
            if (lo >= hi)
                return base;
            long r = mapper.map(array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.combine(r, mapper.map(array[i]));
            return r;
        }
        void leafMinIndex(int lo, int hi,
                          LongComparator comparator,
                          boolean reverse,
                          FJLongMinIndex<T> task) {
            long best = reverse? Long.MIN_VALUE : Long.MAX_VALUE;
            int bestIndex = -1;
            for (int i = lo; i < hi; ++i) {
                long x = mapper.map(array[i]);
                int c = 1;
                if (bestIndex >= 0) {
                    c = comparator.compare(best, x);
                    if (reverse) c = -c;
                }
                if (c > 0) {
                    bestIndex = i;
                    best = x;
                }
            }
            task.result = best;
            task.indexResult = bestIndex;
        }

        public int anyIndex() {
            return (fromIndex < toIndex)? fromIndex : -1;
        }

        public long any() {
            if (fromIndex >= toIndex)
                throw new NoSuchElementException();
            return mapper.map(array[fromIndex]);
        }

    }

    static final class WithBoundedFilteredLongMapping<T>
        extends WithLongMapping<T> {
        final Predicate<? super T> selector;
        final MapperToLong<? super T> mapper;
        WithBoundedFilteredLongMapping
            (ForkJoinExecutor ex, T[] array,
             int fromIndex, int toIndex,
             Predicate<? super T> selector,
             MapperToLong<? super T> mapper) {
            super(ex, array, fromIndex, toIndex);
            this.selector = selector;
            this.mapper = mapper;
        }
        public ParallelLongArray  newArray() {
            FJLongMapSelectAllDriver<T> r =
                new FJLongMapSelectAllDriver<T>(this, selector, mapper);
            ex.invoke(r);
            return new ParallelLongArray(ex, r.results);
        }

        public int size() {
            FJCountAll<T> f = new FJCountAll<T>(this, fromIndex,
                                                toIndex, selector);
            ex.invoke(f);
            return f.result;
        }

        void leafApply(int lo, int hi, LongProcedure procedure) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x))
                    procedure.apply(mapper.map(x));
            }
        }

        long leafReduce(int lo, int hi,
                        LongReducer reducer, long base) {
            boolean gotFirst = false;
            long r = base;
            for (int i = lo; i < hi; ++i) {
                T t = array[i];
                if (selector.evaluate(t)) {
                    long y = mapper.map(t);
                    if (!gotFirst) {
                        gotFirst = true;
                        r = y;
                    }
                    else
                        r = reducer.combine(r, y);
                }
            }
            return r;
        }
        void leafMinIndex(int lo, int hi,
                          LongComparator comparator,
                          boolean reverse,
                          FJLongMinIndex<T> task) {
            long best = reverse? Long.MIN_VALUE : Long.MAX_VALUE;
            int bestIndex = -1;
            for (int i = lo; i < hi; ++i) {
                T t = array[i];
                if (selector.evaluate(t)) {
                    long x = mapper.map(t);
                    int c = 1;
                    if (bestIndex >= 0) {
                        c = comparator.compare(best, x);
                        if (reverse) c = -c;
                    }
                    if (c > 0) {
                        bestIndex = i;
                        best = x;
                    }
                }
            }
            task.result = best;
            task.indexResult = bestIndex;
        }

        public int anyIndex() {
            AtomicInteger result = new AtomicInteger(-1);
            FJSelectAny<T> f =
                new FJSelectAny<T>(this, fromIndex, toIndex,
                                   selector, result);
            ex.invoke(f);
            return result.get();
        }

        public long any() {
            int idx = anyIndex();
            if (idx < 0)
                throw new NoSuchElementException();
            return mapper.map(array[idx]);
        }
    }

    /**
     * A modifier for parallel array operations to apply to mappings
     * of elements to ints, not to the elements themselves
     */
    public static abstract class WithIntMapping<T>
        extends Params<T> {
        WithIntMapping(ForkJoinExecutor ex, T[] array,
                       int fromIndex, int toIndex) {
            super(ex, array, fromIndex, toIndex);
        }

        /**
         * Applies the given procedure
         * @param procedure the procedure
         */
        public void apply(IntProcedure procedure) {
            ex.invoke(new FJIntApply<T>
                      (this, fromIndex, toIndex, procedure));
        }

        abstract void leafApply(int lo, int hi,
                                IntProcedure procedure);

        /**
         * Returns reduction of mapped elements
         * @param reducer the reducer
         * @param base the result for an empty array
         * @return reduction
         */
        public int reduce(IntReducer reducer, int base) {
            FJIntReduce<T> f =
                new FJIntReduce<T>(this, fromIndex, toIndex, reducer, base);
            ex.invoke(f);
            return f.result;
        }

        abstract int leafReduce(int lo, int hi,
                                IntReducer reducer, int base);

        /**
         * Returns the minimum element, or Integer.MAX_VALUE if empty
         * @return minimum element, or Integer.MAX_VALUE if empty
         */
        public int min() {
            return reduce(NaturalIntMinReducer.min, Integer.MAX_VALUE);
        }

        /**
         * Returns the minimum element, or Integer.MAX_VALUE if empty
         * @param comparator the comparator
         * @return minimum element, or Integer.MAX_VALUE if empty
         */
        public int min(IntComparator comparator) {
            return reduce(new IntMinReducer(comparator),
                          Integer.MAX_VALUE);
        }

        /**
         * Returns the maximum element, or Integer.MIN_VALUE if empty
         * @return maximum element, or Integer.MIN_VALUE if empty
         */
        public int max() {
            return reduce(NaturalIntMaxReducer.max, Integer.MIN_VALUE);
        }

        /**
         * Returns the maximum element, or Integer.MIN_VALUE if empty
         * @param comparator the comparator
         * @return maximum element, or Integer.MIN_VALUE if empty
         */
        public int max(IntComparator comparator) {
            return reduce(new IntMaxReducer(comparator),
                          Integer.MIN_VALUE);
        }

        /**
         * Returns the sum of elements
         * @return the sum of elements
         */
        public int sum() {
            return reduce(IntAdder.adder, 0);
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is least, or -1 if empty
         * @return the index of least element or -1 if empty.
         */
        public int indexOfMin() {
            FJIntMinIndex<T> f = new FJIntMinIndex<T>
                (this, fromIndex, toIndex,
                 NaturalIntComparator.comparator, false);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is greatest, or -1 if empty
         * @return the index of greatest element or -1 if empty.
         */
        public int indexOfMax() {
            FJIntMinIndex<T> f = new FJIntMinIndex<T>
                (this, fromIndex, toIndex,
                 NaturalIntComparator.comparator, true);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is least, or -1 if empty
         * @param comparator the comparator
         * @return the index of least element or -1 if empty.
         */
        public int indexOfMin(IntComparator comparator) {
            FJIntMinIndex<T> f = new FJIntMinIndex<T>
                (this, fromIndex, toIndex, comparator, false);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns the index corresponding to the element for which
         * the given mapping is greatest, or -1 if empty
         * @param comparator the comparator
         * @return the index of greatest element or -1 if empty.
         */
        public int indexOfMax(IntComparator comparator) {
            FJIntMinIndex<T> f = new FJIntMinIndex<T>
                (this, fromIndex, toIndex, comparator, true);
            ex.invoke(f);
            return f.indexResult;
        }

        /**
         * Returns a new ParallelIntArray holding mappings
         * @return a new ParallelIntArray holding mappings
         */
        public abstract ParallelIntArray newArray();

        /**
         * Return the number of elements selected using bound or
         * filter restrictions. Note that this method must evaluate
         * all selectors to return its result.
         * @return the number of elements
         */
        public abstract int size();

        /**
         * Returns the index of some element matching bound and filter
         * constraints, or -1 if none.
         * @return index of matching element, or -1 if none.
         */
        public abstract int anyIndex();

        /**
         * Returns mapping of some element matching bound and filter
         * constraints
         * @return mapping of matching element
         * @throws NoSuchElementException if empty
         */
        public abstract int any();

        abstract void leafMinIndex(int lo, int hi,
                                   IntComparator comparator,
                                   boolean reverse,
                                   FJIntMinIndex<T> task);
    }

    static final class WithBoundedIntMapping<T>
        extends WithIntMapping<T> {
        final MapperToInt<? super T> mapper;
        WithBoundedIntMapping(ForkJoinExecutor ex, T[] array,
                              int fromIndex, int toIndex,
                              MapperToInt<? super T> mapper) {
            super(ex, array, fromIndex, toIndex);
            this.mapper = mapper;
        }

        public ParallelIntArray newArray() {
            int[] dest = new int[toIndex - fromIndex];
            FJIntMap<T> f =
                new FJIntMap<T>(this, fromIndex, toIndex, dest, mapper);
            ex.invoke(f);
            return new ParallelIntArray(ex, dest);
        }

        public int size() {
            return toIndex - fromIndex;
        }
        void leafMap(int lo, int hi,
                     int[] dest) {
            int k = lo - fromIndex;
            for (int i = lo; i < hi; ++i)
                dest[k++] = mapper.map(array[i]);
        }
        void leafApply(int lo, int hi, IntProcedure procedure) {
            for (int i = lo; i < hi; ++i)
                procedure.apply(mapper.map(array[i]));
        }

        int leafReduce(int lo, int hi,
                       IntReducer reducer, int base) {
            if (lo >= hi)
                return base;
            int r = mapper.map(array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.combine(r, mapper.map(array[i]));
            return r;
        }
        void leafMinIndex(int lo, int hi,
                          IntComparator comparator,
                          boolean reverse,
                          FJIntMinIndex<T> task) {
            int best = reverse? Integer.MIN_VALUE : Integer.MAX_VALUE;
            int bestIndex = -1;
            for (int i = lo; i < hi; ++i) {
                int x = mapper.map(array[i]);
                int c = 1;
                if (bestIndex >= 0) {
                    c = comparator.compare(best, x);
                    if (reverse) c = -c;
                }
                if (c > 0) {
                    bestIndex = i;
                    best = x;
                }
            }
            task.result = best;
            task.indexResult = bestIndex;
        }
        public int anyIndex() {
            return (fromIndex < toIndex)? fromIndex : -1;
        }

        public int any() {
            if (fromIndex >= toIndex)
                throw new NoSuchElementException();
            return mapper.map(array[fromIndex]);
        }

    }

    static final class WithBoundedFilteredIntMapping<T>
        extends WithIntMapping<T> {
        final Predicate<? super T> selector;
        final MapperToInt<? super T> mapper;
        WithBoundedFilteredIntMapping
            (ForkJoinExecutor ex, T[] array,
             int fromIndex, int toIndex,
             Predicate<? super T> selector,
             MapperToInt<? super T> mapper) {
            super(ex, array, fromIndex, toIndex);
            this.selector = selector;
            this.mapper = mapper;
        }
        public ParallelIntArray  newArray() {
            FJIntMapSelectAllDriver<T> r =
                new FJIntMapSelectAllDriver<T>(this, selector, mapper);
            ex.invoke(r);
            return new ParallelIntArray(ex, r.results);
        }

        public int size() {
            FJCountAll<T> f = new FJCountAll<T>(this, fromIndex,
                                                toIndex, selector);
            ex.invoke(f);
            return f.result;
        }

        void leafApply(int lo, int hi, IntProcedure procedure) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (selector.evaluate(x))
                    procedure.apply(mapper.map(x));
            }
        }

        int leafReduce(int lo, int hi,
                       IntReducer reducer, int base) {
            boolean gotFirst = false;
            int r = base;
            for (int i = lo; i < hi; ++i) {
                T t = array[i];
                if (selector.evaluate(t)) {
                    int y = mapper.map(t);
                    if (!gotFirst) {
                        gotFirst = true;
                        r = y;
                    }
                    else
                        r = reducer.combine(r, y);
                }
            }
            return r;
        }

        void leafMinIndex(int lo, int hi,
                          IntComparator comparator,
                          boolean reverse,
                          FJIntMinIndex<T> task) {
            int best = reverse? Integer.MIN_VALUE : Integer.MAX_VALUE;
            int bestIndex = -1;
            for (int i = lo; i < hi; ++i) {
                T t = array[i];
                if (selector.evaluate(t)) {
                    int x = mapper.map(t);
                    int c = 1;
                    if (bestIndex >= 0) {
                        c = comparator.compare(best, x);
                        if (reverse) c = -c;
                    }
                    if (c > 0) {
                        bestIndex = i;
                        best = x;
                    }
                }
            }
            task.result = best;
            task.indexResult = bestIndex;
        }

        public int anyIndex() {
            AtomicInteger result = new AtomicInteger(-1);
            FJSelectAny<T> f =
                new FJSelectAny<T>(this, fromIndex, toIndex,
                                   selector, result);
            ex.invoke(f);
            return result.get();
        }

        public int any() {
            int idx = anyIndex();
            if (idx < 0)
                throw new NoSuchElementException();
            return mapper.map(array[idx]);
        }

    }

    /*
     * ForkJoin Implementations. There are a bunch of them,
     * all just a little different than others.
     */

    /**
     * ForkJoin tasks for Apply. Like other divide-and-conquer tasks
     * used for computing ParallelArray operations, rather than pure
     * recursion, it link right-hand-sides and then joins up the tree,
     * exploiting cases where tasks aren't stolen.  This generates and
     * joins tasks with a bit less overhead than pure recursive style.
     */
    static final class FJApply<T,U> extends RecursiveAction {
        final WithMapping<T,U> params;
        final int lo;
        final int hi;
        final Procedure<? super U> procedure;
        FJApply<T,U> next;
        FJApply(WithMapping<T,U> params, int lo, int hi,
                Procedure<? super U> procedure) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.procedure = procedure;
        }
        protected void compute() {
            FJApply<T,U> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJApply<T,U> r =
                    new FJApply<T,U>(params, mid, h, procedure);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafApply(l, h, procedure);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJReduce<T,U> extends RecursiveAction {
        final WithMapping<T,U> params;
        final int lo;
        final int hi;
        final Reducer<U> reducer;
        U result;
        FJReduce<T,U> next;
        FJReduce(WithMapping<T,U> params, int lo, int hi,
                 Reducer<U> reducer, U base) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.reducer = reducer;
            this.result = base;
        }
        protected void compute() {
            FJReduce<T,U> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJReduce<T,U> r =
                    new FJReduce<T,U>(params, mid, h, reducer, result);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            result = params.leafReduce(l, h, reducer, result);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                result = reducer.combine(result, right.result);
                right = right.next;
            }
        }
    }

    static final class FJMap<T,U> extends RecursiveAction {
        final Params<T> params;
        final U[] dest;
        final Mapper<? super T, ? extends U> mapper;
        final int lo;
        final int hi;
        FJMap<T,U> next;
        FJMap(Params<T> params, int lo, int hi,
              U[] dest,
              Mapper<? super T, ? extends U> mapper) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.dest = dest;
            this.mapper = mapper;
        }

        void leafMap(int l, int h) {
            T[] array = params.array;
            int k = l - params.fromIndex;
            for (int i = l; i < h; ++i)
                dest[k++] = mapper.map(array[i]);
        }

        protected void compute() {
            FJMap<T,U> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMap<T,U> r =
                    new FJMap<T,U>(params, mid, h, dest, mapper);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            leafMap(l, h);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJTransform<T> extends RecursiveAction {
        final WithFilter<T> params;
        final int lo;
        final int hi;
        final Mapper<? super T, ? extends T> mapper;
        FJTransform<T> next;
        FJTransform(WithFilter<T> params, int lo, int hi,
                    Mapper<? super T, ? extends T> mapper) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.mapper = mapper;
        }
        protected void compute() {
            FJTransform<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJTransform<T> r =
                    new FJTransform<T>(params, mid, h, mapper);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafTransform(l, h, mapper);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJIndexMap<T> extends RecursiveAction {
        final WithFilter<T> params;
        final int lo;
        final int hi;
        final MapperFromInt<? extends T> mapper;
        FJIndexMap<T> next;
        FJIndexMap(WithFilter<T> params, int lo, int hi,
                   MapperFromInt<? extends T> mapper) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.mapper = mapper;
        }
        protected void compute() {
            FJIndexMap<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJIndexMap<T> r =
                    new FJIndexMap<T>(params, mid, h, mapper);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafIndexMap(l, h, mapper);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJGenerate<T> extends RecursiveAction {
        final WithFilter<T> params;
        final int lo;
        final int hi;
        final Generator<? extends T> generator;
        FJGenerate<T> next;
        FJGenerate(WithFilter<T> params, int lo, int hi,
                   Generator<? extends T> generator) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.generator = generator;
        }
        protected void compute() {
            FJGenerate<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJGenerate<T> r =
                    new FJGenerate<T>(params, mid, h, generator);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafGenerate(l, h, generator);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJFill<T> extends RecursiveAction {
        final WithFilter<T> params;
        final int lo;
        final int hi;
        final T value;
        FJFill<T> next;
        FJFill(WithFilter<T> params, int lo, int hi,
               T value) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.value = value;
        }
        protected void compute() {
            FJFill<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJFill<T> r =
                    new FJFill<T>(params, mid, h, value);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafFill(l, h, value);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJCombineInPlace<T> extends RecursiveAction {
        final WithFilter<T> params;
        final int lo;
        final int hi;
        final T[] other;
        final Reducer<T> combiner;
        FJCombineInPlace<T> next;
        FJCombineInPlace(WithFilter<T> params, int lo, int hi,
                         T[] other, Reducer<T> combiner) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.other = other;
            this.combiner = combiner;
        }
        protected void compute() {
            FJCombineInPlace<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJCombineInPlace<T> r =
                    new FJCombineInPlace<T>(params, mid, h, other, combiner);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafCombineInPlace(l, h, other, combiner);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJCountAll<T> extends RecursiveAction {
        final Params<T> params;
        final Predicate<? super T> selector;
        final int lo;
        final int hi;
        int result;
        FJCountAll<T> next;
        FJCountAll(Params<T> params, int lo, int hi,
                   Predicate<? super T> selector) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.selector = selector;
        }
        protected void compute() {
            FJCountAll<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJCountAll<T> r =
                    new FJCountAll<T>(params, mid, h, selector);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            T[] array = params.array;
            int n = 0;
            for (int i = lo; i < hi; ++i) {
                if (selector.evaluate(array[i]))
                    ++n;
            }
            result = n;

            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                result += right.result;
                right = right.next;
            }
        }
    }

    static final class FJCombine<T,U,V> extends RecursiveAction {
        final Params<T> params;
        final U[] other;
        final V[] dest;
        final Combiner<? super T, ? super U, ? extends V> combiner;
        final int lo;
        final int hi;
        FJCombine<T,U,V> next;
        FJCombine(Params<T> params, int lo, int hi,
                  U[] other, V[] dest,
                  Combiner<? super T, ? super U, ? extends V> combiner) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.other = other;
            this.dest = dest;
            this.combiner = combiner;
        }

        void  leafCombine(int l, int h) {
            T[] array = params.array;
            int k = l - params.fromIndex;
            for (int i = l; i < h; ++i)
                dest[k++] = combiner.combine(array[i], other[i]);
        }

        protected void compute() {
            FJCombine<T,U,V> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJCombine<T,U,V> r =
                    new FJCombine<T,U,V>(params, mid, h, other,
                                         dest, combiner);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }

            leafCombine(l, h);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJMinIndex<T,U> extends RecursiveAction {
        final WithMapping<T,U> params;
        final int lo;
        final int hi;
        final Comparator<? super U> comparator;
        final boolean reverse;
        U result;
        int indexResult;
        FJMinIndex<T,U> next;
        FJMinIndex(WithMapping<T,U> params, int lo, int hi,
                   Comparator<? super U> comparator, boolean reverse) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.comparator = comparator;
            this.reverse = reverse;
        }
        protected void compute() {
            FJMinIndex<T,U> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMinIndex<T,U> r =
                    new FJMinIndex<T,U>(params, mid, h, comparator, reverse);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafMinIndex(l, h, comparator, reverse, this);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                int ridx = right.indexResult;
                if (ridx > 0) {
                    if (indexResult < 0) {
                        indexResult = ridx;
                        result = right.result;
                    }
                    else {
                        U rbest = right.result;
                        int c = comparator.compare(result, rbest);
                        if (reverse) c = -c;
                        if (c > 0) {
                            indexResult = ridx;
                            result = rbest;
                        }
                    }
                }
                right = right.next;
            }
        }
    }

    // Versions for Double mappings

    static final class FJDoubleApply<T> extends RecursiveAction {
        final WithDoubleMapping<T> params;
        final int lo;
        final int hi;
        final DoubleProcedure procedure;
        FJDoubleApply<T> next;
        FJDoubleApply(WithDoubleMapping<T> params, int lo, int hi,
                      DoubleProcedure procedure) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.procedure = procedure;
        }
        protected void compute() {
            FJDoubleApply<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJDoubleApply<T> r =
                    new FJDoubleApply<T>(params, mid, h, procedure);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafApply(l, h, procedure);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJDoubleReduce<T> extends RecursiveAction {
        final WithDoubleMapping<T> params;
        final int lo;
        final int hi;
        final DoubleReducer reducer;
        double result;
        FJDoubleReduce<T> next;
        FJDoubleReduce(WithDoubleMapping<T> params, int lo, int hi,
                       DoubleReducer reducer, double base) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.reducer = reducer;
            this.result = base;
        }
        protected void compute() {
            FJDoubleReduce<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJDoubleReduce<T> r =
                    new FJDoubleReduce<T>(params, mid, h, reducer, result);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            result = params.leafReduce(l, h, reducer, result);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                result = reducer.combine(result, right.result);
                right = right.next;
            }
        }
    }

    static final class FJDoubleMap<T> extends RecursiveAction {
        final Params<T> params;
        final double[] dest;
        MapperToDouble<? super T> mapper;
        final int lo;
        final int hi;
        FJDoubleMap<T> next;
        FJDoubleMap(Params<T> params, int lo, int hi,
                    double[] dest,
                    MapperToDouble<? super T> mapper) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.dest = dest;
            this.mapper = mapper;
        }

        void leafMap(int l, int h) {
            T[] array = params.array;
            int k = l - params.fromIndex;
            for (int i = l; i < h; ++i)
                dest[k++] = mapper.map(array[i]);
        }

        protected void compute() {
            FJDoubleMap<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJDoubleMap<T> r =
                    new FJDoubleMap<T>(params, mid, h, dest, mapper);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            leafMap(l, h);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }
    static final class FJDoubleMinIndex<T> extends RecursiveAction {
        final WithDoubleMapping<T> params;
        final int lo;
        final int hi;
        final DoubleComparator comparator;
        final boolean reverse;
        double result;
        int indexResult;
        FJDoubleMinIndex<T> next;
        FJDoubleMinIndex(WithDoubleMapping<T> params, int lo, int hi,
                         DoubleComparator comparator, boolean reverse) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.comparator = comparator;
            this.reverse = reverse;
        }
        protected void compute() {
            FJDoubleMinIndex<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJDoubleMinIndex<T> r =
                    new FJDoubleMinIndex<T>(params, mid, h, comparator, reverse);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafMinIndex(l, h, comparator, reverse, this);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                int ridx = right.indexResult;
                if (ridx > 0) {
                    if (indexResult < 0) {
                        indexResult = ridx;
                        result = right.result;
                    }
                    else {
                        double rbest = right.result;
                        int c = comparator.compare(result, rbest);
                        if (reverse) c = -c;
                        if (c > 0) {
                            indexResult = ridx;
                            result = rbest;
                        }
                    }
                }
                right = right.next;
            }
        }
    }

    // Versions for Long mappings

    static final class FJLongApply<T> extends RecursiveAction {
        final WithLongMapping<T> params;
        final int lo;
        final int hi;
        final LongProcedure procedure;
        FJLongApply<T> next;
        FJLongApply(WithLongMapping<T> params, int lo, int hi,
                    LongProcedure procedure) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.procedure = procedure;
        }
        protected void compute() {
            FJLongApply<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJLongApply<T> r =
                    new FJLongApply<T>(params, mid, h, procedure);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafApply(l, h, procedure);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJLongReduce<T> extends RecursiveAction {
        final WithLongMapping<T> params;
        final int lo;
        final int hi;
        final LongReducer reducer;
        long result;
        FJLongReduce<T> next;
        FJLongReduce(WithLongMapping<T> params, int lo, int hi,
                     LongReducer reducer, long base) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.reducer = reducer;
            this.result = base;
        }
        protected void compute() {
            FJLongReduce<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJLongReduce<T> r =
                    new FJLongReduce<T>(params, mid, h, reducer, result);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            result = params.leafReduce(l, h, reducer, result);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                result = reducer.combine(result, right.result);
                right = right.next;
            }
        }
    }

    static final class FJLongMap<T> extends RecursiveAction {
        final Params<T> params;
        final long[] dest;
        MapperToLong<? super T> mapper;
        final int lo;
        final int hi;
        FJLongMap<T> next;
        FJLongMap(Params<T> params, int lo, int hi,
                  long[] dest,
                  MapperToLong<? super T> mapper) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.dest = dest;
            this.mapper = mapper;
        }

        void leafMap(int l, int h) {
            T[] array = params.array;
            int k = l - params.fromIndex;
            for (int i = l; i < h; ++i)
                dest[k++] = mapper.map(array[i]);
        }

        protected void compute() {
            FJLongMap<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJLongMap<T> r =
                    new FJLongMap<T>(params, mid, h, dest, mapper);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            leafMap(l, h);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJLongMinIndex<T> extends RecursiveAction {
        final WithLongMapping<T> params;
        final int lo;
        final int hi;
        final LongComparator comparator;
        final boolean reverse;
        long result;
        int indexResult;
        FJLongMinIndex<T> next;
        FJLongMinIndex(WithLongMapping<T> params, int lo, int hi,
                       LongComparator comparator, boolean reverse) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.comparator = comparator;
            this.reverse = reverse;
        }
        protected void compute() {
            FJLongMinIndex<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJLongMinIndex<T> r =
                    new FJLongMinIndex<T>(params, mid, h, comparator, reverse);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafMinIndex(l, h, comparator, reverse, this);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                int ridx = right.indexResult;
                if (ridx > 0) {
                    if (indexResult < 0) {
                        indexResult = ridx;
                        result = right.result;
                    }
                    else {
                        long rbest = right.result;
                        int c = comparator.compare(result, rbest);
                        if (reverse) c = -c;
                        if (c > 0) {
                            indexResult = ridx;
                            result = rbest;
                        }
                    }
                }
                right = right.next;
            }
        }
    }


    // Versions for Int mappings

    static final class FJIntApply<T> extends RecursiveAction {
        final WithIntMapping<T> params;
        final int lo;
        final int hi;
        final IntProcedure procedure;
        FJIntApply<T> next;
        FJIntApply(WithIntMapping<T> params, int lo, int hi,
                   IntProcedure procedure) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.procedure = procedure;
        }
        protected void compute() {
            FJIntApply<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJIntApply<T> r =
                    new FJIntApply<T>(params, mid, h, procedure);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafApply(l, h, procedure);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJIntReduce<T> extends RecursiveAction {
        final WithIntMapping<T> params;
        final int lo;
        final int hi;
        final IntReducer reducer;
        int result;
        FJIntReduce<T> next;
        FJIntReduce(WithIntMapping<T> params, int lo, int hi,
                    IntReducer reducer, int base) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.reducer = reducer;
            this.result = base;
        }
        protected void compute() {
            FJIntReduce<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJIntReduce<T> r =
                    new FJIntReduce<T>(params, mid, h, reducer, result);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            result = params.leafReduce(l, h, reducer, result);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                result = reducer.combine(result, right.result);
                right = right.next;
            }
        }
    }

    static final class FJIntMap<T> extends RecursiveAction {
        final Params<T> params;
        final int[] dest;
        MapperToInt<? super T> mapper;
        final int lo;
        final int hi;
        FJIntMap<T> next;
        FJIntMap(Params<T> params, int lo, int hi,
                 int[] dest,
                 MapperToInt<? super T> mapper) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.dest = dest;
            this.mapper = mapper;
        }

        void leafMap(int l, int h) {
            T[] array = params.array;
            int k = l - params.fromIndex;
            for (int i = l; i < h; ++i)
                dest[k++] = mapper.map(array[i]);
        }

        protected void compute() {
            FJIntMap<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJIntMap<T> r =
                    new FJIntMap<T>(params, mid, h, dest, mapper);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            leafMap(l, h);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    static final class FJIntMinIndex<T> extends RecursiveAction {
        final WithIntMapping<T> params;
        final int lo;
        final int hi;
        final IntComparator comparator;
        final boolean reverse;
        int result;
        int indexResult;
        FJIntMinIndex<T> next;
        FJIntMinIndex(WithIntMapping<T> params, int lo, int hi,
                      IntComparator comparator, boolean reverse) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.comparator = comparator;
            this.reverse = reverse;
        }
        protected void compute() {
            FJIntMinIndex<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJIntMinIndex<T> r =
                    new FJIntMinIndex<T>(params, mid, h, comparator, reverse);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            params.leafMinIndex(l, h, comparator, reverse, this);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                int ridx = right.indexResult;
                if (ridx > 0) {
                    if (indexResult < 0) {
                        indexResult = ridx;
                        result = right.result;
                    }
                    else {
                        int rbest = right.result;
                        int c = comparator.compare(result, rbest);
                        if (reverse) c = -c;
                        if (c > 0) {
                            indexResult = ridx;
                            result = rbest;
                        }
                    }
                }
                right = right.next;
            }
        }
    }

    /**
     * ForkJoin task for SelectAny; relies on cancellation
     */
    static final class FJSelectAny<T> extends RecursiveAction {
        final Params<T> params;
        final int lo;
        final int hi;
        final Predicate<? super T> selector;
        final AtomicInteger result;
        FJSelectAny<T> next;

        FJSelectAny(Params<T> params, int lo, int hi,
                    Predicate<? super T> selector,
                    AtomicInteger result) {
            this.params = params;
            this.lo = lo;
            this.hi = hi;
            this.selector = selector;
            this.result = result;
        }

        void leafSelectAny(int l, int h) {
            T[] array = params.array;
            Predicate<? super T> sel = this.selector;
            AtomicInteger res = this.result;
            for (int i = l; i < h && res.get() < 0; ++i) {
                if (sel.evaluate(array[i])) {
                    res.compareAndSet(-1, i);
                    break;
                }
            }
        }

        protected void compute() {
            AtomicInteger res = result;
            if (res.get() >= 0)
                return;
            FJSelectAny<T> right = null;
            int l = lo;
            int h = hi;
            int g = params.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJSelectAny<T> r =
                    new FJSelectAny<T>(params, mid, h, selector, res);
                h = mid;
                r.next = right;
                right = r;
                right.fork();
            }
            leafSelectAny(l, h);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right)) {
                    if (res.get() < 0)
                        right.compute();
                }
                else if (res.get() >= 0)
                    right.cancel();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    /**
     * SelectAll proceeds in two passes. In the first pass, indices of
     * matching elements are recorded in match array.  In second pass,
     * once the size of results is known and result array is
     * constructed in driver, the matching elements are placed into
     * corresponding result positions.
     *
     * As a compromise to get good performance in cases of both dense
     * and sparse result sets, the matches array is allocated only on
     * demand, and subtask calls for empty subtrees are suppressed.
     */
    static final class FJSelectAll<T> extends RecursiveAction {
        final FJSelectAllDriver<T> driver;
        final int lo;
        final int hi;
        int[] matches;
        int nmatches;
        int offset;
        FJSelectAll<T> left, right;

        FJSelectAll(FJSelectAllDriver<T> driver, int lo, int hi) {
            this.driver = driver;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            if (driver.phase == 0) {
                if (hi - lo < driver.params.granularity)
                    leafPhase0();
                else
                    internalPhase0();
            }
            else if (nmatches != 0) {
                if (hi - lo < driver.params.granularity)
                    driver.leafPhase1(offset, nmatches, matches);
                else
                    internalPhase1();
            }
        }

        void leafPhase0() {
            T[] array = driver.params.array;
            Predicate<? super T> selector = driver.selector;
            int[] m = null; // only construct if find at least one match
            int n = 0;
            for (int j = lo; j < hi; ++j) {
                if (selector.evaluate(array[j])) {
                    if (m == null)
                        m = new int[hi - j];
                    m[n++] = j;
                }
            }
            nmatches = n;
            matches = m;
        }

        void internalPhase0() {
            int mid = (lo + hi) >>> 1;
            FJSelectAll<T> l = new FJSelectAll<T>(driver, lo, mid);
            FJSelectAll<T> r = new FJSelectAll<T>(driver, mid, hi);
            coInvoke(l, r);
            int lnm = l.nmatches;
            if (lnm != 0)
                left = l;
            int rnm = r.nmatches;
            if (rnm != 0)
                right = r;
            nmatches = lnm + rnm;
        }

        void internalPhase1() {
            int k = offset;
            if (left != null) {
                int lnm = left.nmatches;
                left.offset = k;
                left.reinitialize();
                if (right != null) {
                    right.offset = k + lnm;
                    right.reinitialize();
                    coInvoke(left, right);
                }
                else
                    left.compute();
            }
            else if (right != null) {
                right.offset = k;
                right.compute();
            }
        }
    }

    static abstract class FJSelectAllDriver<T> extends RecursiveAction {
        final Params<T> params;
        final Predicate<? super T> selector;
        int nresults;
        int phase;
        FJSelectAllDriver(Params<T> params,
                          Predicate<? super T> selector) {
            this.params = params;
            this.selector = selector;
        }

        protected final void compute() {
            FJSelectAll<T> r = new FJSelectAll<T>
                (this, params.fromIndex, params.toIndex);
            r.compute();
            createResults(r.nmatches);
            phase = 1;
            r.compute();
        }

        abstract void createResults(int size);
        abstract void leafPhase1(int offset, int nmatches, int[] m);
    }

    static abstract class FJRefSelectAllDriver<T,U>
        extends FJSelectAllDriver<T> {
        final Class<? super U> elementType; // null for Object
        U[] results;
        FJRefSelectAllDriver(Params<T> params,
                             Predicate<? super T> selector,
                             Class<? super U> elementType) {
            super(params, selector);
            this.elementType = elementType;
        }
        final void createResults(int size) {
            if (elementType == null)
                results = (U[])new Object[size];
            else
                results = (U[])
                    java.lang.reflect.Array.newInstance(elementType, size);
        }
    }

    static final class FJPlainRefSelectAllDriver<T>
        extends FJRefSelectAllDriver<T,T> {
        FJPlainRefSelectAllDriver(Params<T> params,
                                  Predicate<? super T> selector,
                                  Class<? super T> elementType) {
            super(params, selector, elementType);
        }
        final void leafPhase1(int offset, int nmatches, int[] m) {
            if (m != null) {
                int n = nmatches;
                int k = offset;
                T[] array = params.array;
                for (int i = 0; i < n; ++i)
                    results[k++] = array[m[i]];
            }
        }
    }

    static final class FJMapRefSelectAllDriver<T,U>
        extends FJRefSelectAllDriver<T, U> {
        final Mapper<? super T, ? extends U> mapper;
        FJMapRefSelectAllDriver(Params<T> params,
                                Predicate<? super T> selector,
                                Class<? super U> elementType,
                                Mapper<? super T, ? extends U> mapper) {
            super(params, selector, elementType);
            this.mapper = mapper;
        }
        final void leafPhase1(int offset, int nmatches, int[] m) {
            if (m != null) {
                int n = nmatches;
                int k = offset;
                T[] array = params.array;
                for (int i = 0; i < n; ++i)
                    results[k++] = mapper.map(array[m[i]]);
            }
        }
    }

    static final class FJDoubleMapSelectAllDriver<T>
        extends FJSelectAllDriver<T> {
        double[] results;
        final MapperToDouble<? super T> mapper;
        FJDoubleMapSelectAllDriver(Params<T> params,
                                   Predicate<? super T> selector,
                                   MapperToDouble<? super T> mapper) {
            super(params, selector);
            this.mapper = mapper;
        }
        final void createResults(int size) {
            results = new double[size];
        }
        final void leafPhase1(int offset, int nmatches, int[] m) {
            if (m != null) {
                int n = nmatches;
                int k = offset;
                T[] array = params.array;
                for (int i = 0; i < n; ++i)
                    results[k++] = mapper.map(array[m[i]]);
            }
        }
    }

    static final class FJLongMapSelectAllDriver<T>
        extends FJSelectAllDriver<T> {
        long[] results;
        final MapperToLong<? super T> mapper;
        FJLongMapSelectAllDriver(Params<T> params,
                                 Predicate<? super T> selector,
                                 MapperToLong<? super T> mapper) {
            super(params, selector);
            this.mapper = mapper;
        }
        final void createResults(int size) {
            results = new long[size];
        }
        final void leafPhase1(int offset, int nmatches, int[] m) {
            if (m != null) {
                int n = nmatches;
                int k = offset;
                T[] array = params.array;
                for (int i = 0; i < n; ++i)
                    results[k++] = mapper.map(array[m[i]]);
            }
        }
    }

    static final class FJIntMapSelectAllDriver<T>
        extends FJSelectAllDriver<T> {
        int[] results;
        final MapperToInt<? super T> mapper;
        FJIntMapSelectAllDriver(Params<T> params,
                                Predicate<? super T> selector,
                                MapperToInt<? super T> mapper) {
            super(params, selector);
            this.mapper = mapper;
        }
        final void createResults(int size) {
            results = new int[size];
        }
        final void leafPhase1(int offset, int nmatches, int[] m) {
            if (m != null) {
                int n = nmatches;
                int k = offset;
                T[] array = params.array;
                for (int i = 0; i < n; ++i)
                    results[k++] = mapper.map(array[m[i]]);
            }
        }
    }

    /**
     * Sorter based mainly on CilkSort
     * <A href="http://supertech.lcs.mit.edu/cilk/"> Cilk</A>:
     * if array size is small, just use a sequential quicksort
     *         Otherwise:
     *         1. Break array in half.
     *         2. For each half,
     *             a. break the half in half (i.e., quarters),
     *             b. sort the quarters
     *             c. merge them together
     *         3. merge together the two halves.
     *
     * One reason for splitting in quarters is that this guarantees
     * that the final sort is in the main array, not the workspace array.
     * (workspace and main swap roles on each subsort step.)
     *
     */
    static final class FJSorter<T> extends RecursiveAction {
        /** Cutoff for when to use insertion-sort instead of quicksort */
        static final int INSERTION_SORT_THRESHOLD = 8;

        final Comparator<? super T> cmp;
        final T[] a;       //  to be sorted.
        final T[] w;       // workspace for merge
        final int origin;  // origin of the part of array we deal with
        final int n;       // Number of elements in (sub)arrays.
        final int granularity;

        FJSorter(Comparator<? super T> cmp,
                 T[] a, T[] w, int origin, int n, int granularity) {
            this.cmp = cmp;
            this.a = a; this.w = w; this.origin = origin; this.n = n;
            this.granularity = granularity;
        }

        protected void compute()  {
            int g = granularity;
            if (n > g) {
                int h = n >>> 1; // half
                int q = n >>> 2; // lower quarter index
                int u = h + q;   // upper quarter
                coInvoke
                    (new FJSubSorter<T>
                     (new FJSorter<T>(cmp, a, w, origin,   q,   g),
                      new FJSorter<T>(cmp, a, w, origin+q, q,   g),
                      new FJMerger<T>(cmp, a, w, origin,   q,
                                      origin+q, q, origin, g)
                      ),
                     new FJSubSorter<T>
                     (new FJSorter<T>(cmp, a, w, origin+h, q,   g),
                      new FJSorter<T>(cmp, a, w, origin+u, n-u, g),
                      new FJMerger<T>(cmp, a, w, origin+h, q,
                                      origin+u, n-u, origin+h, g)
                      )
                     );
                new FJMerger<T>(cmp, w, a, origin, h,
                                origin+h, n-h, origin, g).compute();
            }
            else
                quickSort(origin, origin+n-1);
        }

        /**
         * Sequential quicksort. Uses insertion sort if under
         * threshold.  Otherwise uses median of three to pick
         * pivot. Loops rather than recurses along left path
         */
        void quickSort(int lo, int hi) {
            for (;;) {
                if (hi - lo <= INSERTION_SORT_THRESHOLD) {
                    for (int i = lo + 1; i <= hi; i++) {
                        T t = a[i];
                        int j = i - 1;
                        while (j >= lo && cmp.compare(t, a[j]) < 0) {
                            a[j+1] = a[j];
                            --j;
                        }
                        a[j+1] = t;
                    }
                    return;
                }

                int mid = (lo + hi) >>> 1;
                if (cmp.compare(a[lo], a[mid]) > 0) {
                    T t = a[lo]; a[lo] = a[mid]; a[mid] = t;
                }
                if (cmp.compare(a[mid], a[hi]) > 0) {
                    T t = a[mid]; a[mid] = a[hi]; a[hi] = t;
                    if (cmp.compare(a[lo], a[mid]) > 0) {
                        t = a[lo]; a[lo] = a[mid]; a[mid] = t;
                    }
                }

                T pivot = a[mid];
                int left = lo+1;
                int right = hi-1;
                for (;;) {
                    while (cmp.compare(pivot, a[right]) < 0)
                        --right;
                    while (left < right && cmp.compare(pivot, a[left]) >= 0)
                        ++left;
                    if (left < right) {
                        T t = a[left]; a[left] = a[right]; a[right] = t;
                        --right;
                    }
                    else break;
                }

                quickSort(lo, left);
                lo = left + 1;
            }
        }
    }

    /** Utility class to sort half a partitioned array */
    static final class FJSubSorter<T> extends RecursiveAction {
        final FJSorter<T> left;
        final FJSorter<T> right;
        final FJMerger<T> merger;
        FJSubSorter(FJSorter<T> left, FJSorter<T> right, FJMerger<T> merger){
            this.left = left; this.right = right; this.merger = merger;
        }
        protected void compute() {
            right.fork();
            left.compute();
            if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                right.compute();
            else
                right.join();
            merger.compute();
        }
    }

    /**
     * Merger for FJ sort. If partitions are small, then just
     * sequentially merges.  Otherwise: Splits Left partition in half,
     * Finds the greatest point in Right partition less than the
     * beginning of the second half of left via binary search, And
     * then, in parallel, merges left half of L with elements of R up
     * to split point, and merges right half of L with elements of R
     * past split point
     */
    static final class FJMerger<T> extends RecursiveAction {
        final Comparator<? super T> cmp;
        final T[] a;      // partitioned  array.
        final T[] w;      // Output array.
        final int lo;     // relative origin of left side of a
        final int ln;     // number of elements on left of a
        final int ro;     // relative origin of right side of a
        final int rn;     // number of elements on right of a
        final int wo;     // origin for output
        final int granularity;
        FJMerger<T> next;

        FJMerger(Comparator<? super T> cmp, T[] a, T[] w,
                 int lo, int ln, int ro, int rn, int wo, int granularity) {
            this.cmp = cmp;
            this.a = a;    this.w = w;
            this.lo = lo;  this.ln = ln;
            this.ro = ro;  this.rn = rn;
            this.wo = wo;
            this.granularity = granularity;
        }

        protected void compute() {
            FJMerger<T> rights = null;
            int lln = ln;
            int lrn = rn;
            while (lln > granularity) {
                int lh = lln >>> 1;
                int ls = lo + lh;   // index of split
                T split = a[ls];
                int rl = 0;
                int rh = lrn;
                while (rl < rh) {
                    int mid = (rl + rh) >>> 1;
                    if (cmp.compare(split, a[ro + mid]) <= 0)
                        rh = mid;
                    else
                        rl = mid + 1;
                }
                FJMerger<T> rm =
                    new FJMerger<T>(cmp, a, w, ls, lln-lh, ro+rh,
                                    lrn-rh, wo+lh+rh, granularity);
                lln = lh;
                lrn = rh;
                rm.next = rights;
                rights = rm;
                rm.fork();
            }
            merge(lo+lln, ro+lrn);
            while (rights != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(rights))
                    rights.compute();
                else
                    rights.join();
                rights = rights.next;
            }
        }

        /** a standard sequential merge */
        void merge(int lFence, int rFence) {
            int l = lo;
            int r = ro;
            int k = wo;
            while (l < lFence && r < rFence) {
                T al = a[l];
                T ar = a[r];
                T t;
                if (cmp.compare(al, ar) <= 0) {
                    ++l;
                    t = al;
                }
                else {
                    ++r;
                    t = ar;
                }
                w[k++] = t;
            }
            while (l < lFence)
                w[k++] = a[l++];
            while (r < rFence)
                w[k++] = a[r++];
        }
    }

    // version for Comparables. Sadly worth doing.

    static final class FJComparableSorter<T extends Comparable<? super T>>
        extends RecursiveAction {
        /** Cutoff for when to use insertion-sort instead of quicksort */
        static final int INSERTION_SORT_THRESHOLD = 8;

        final T[] a;       //  to be sorted.
        final T[] w;       // workspace for merge
        final int origin;  // origin of the part of array we deal with
        final int n;       // Number of elements in (sub)arrays.
        final int granularity;

        FJComparableSorter(
                           T[] a, T[] w, int origin, int n, int granularity) {
            this.a = a; this.w = w; this.origin = origin; this.n = n;
            this.granularity = granularity;
        }

        protected void compute()  {
            int g = granularity;
            if (n > g) {
                int h = n >>> 1; // half
                int q = n >>> 2; // lower quarter index
                int u = h + q;   // upper quarter
                coInvoke
                    (new FJComparableSubSorter<T>
                     (new FJComparableSorter<T>(a, w, origin,   q,   g),
                      new FJComparableSorter<T>(a, w, origin+q, q,   g),
                      new FJComparableMerger<T>(a, w, origin,   q,
                                                origin+q, q, origin, g)
                      ),
                     new FJComparableSubSorter<T>
                     (new FJComparableSorter<T>(a, w, origin+h, q,   g),
                      new FJComparableSorter<T>(a, w, origin+u, n-u, g),
                      new FJComparableMerger<T>(a, w, origin+h, q,
                                                origin+u, n-u, origin+h, g)
                      )
                     );
                new FJComparableMerger<T>(w, a, origin, h,
                                          origin+h, n-h, origin, g).compute();
            }
            else
                quickSort(origin, origin+n-1);
        }

        void quickSort(int lo, int hi) {
            for (;;) {
                if (hi - lo <= INSERTION_SORT_THRESHOLD) {
                    for (int i = lo + 1; i <= hi; i++) {
                        T t = a[i];
                        int j = i - 1;
                        while (j >= lo && t.compareTo(a[j]) < 0) {
                            a[j+1] = a[j];
                            --j;
                        }
                        a[j+1] = t;
                    }
                    return;
                }

                int mid = (lo + hi) >>> 1;
                if (a[lo].compareTo(a[mid]) > 0) {
                    T t = a[lo]; a[lo] = a[mid]; a[mid] = t;
                }
                if (a[mid].compareTo(a[hi]) > 0) {
                    T t = a[mid]; a[mid] = a[hi]; a[hi] = t;
                    if (a[lo].compareTo(a[mid]) > 0) {
                        t = a[lo]; a[lo] = a[mid]; a[mid] = t;
                    }
                }

                T pivot = a[mid];
                int left = lo+1;
                int right = hi-1;
                for (;;) {
                    while (pivot.compareTo(a[right]) < 0)
                        --right;
                    while (left < right && pivot.compareTo(a[left]) >= 0)
                        ++left;
                    if (left < right) {
                        T t = a[left]; a[left] = a[right]; a[right] = t;
                        --right;
                    }
                    else break;
                }

                quickSort(lo, left);
                lo = left + 1;
            }
        }
    }

    /** Utility class to sort half a partitioned array */
    static final class FJComparableSubSorter<T extends Comparable<? super T>>  extends RecursiveAction {
        final FJComparableSorter<T> left;
        final FJComparableSorter<T> right;
        final FJComparableMerger<T> merger;
        FJComparableSubSorter(FJComparableSorter<T> left, FJComparableSorter<T> right, FJComparableMerger<T> merger){
            this.left = left; this.right = right; this.merger = merger;
        }
        protected void compute() {
            right.fork();
            left.compute();
            if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                right.compute();
            else
                right.join();
            merger.compute();
        }
    }

    static final class FJComparableMerger<T extends Comparable<? super T>>
        extends RecursiveAction {
        final T[] a;      // partitioned  array.
        final T[] w;      // Output array.
        final int lo;     // relative origin of left side of a
        final int ln;     // number of elements on left of a
        final int ro;     // relative origin of right side of a
        final int rn;     // number of elements on right of a
        final int wo;     // origin for output
        final int granularity;
        FJComparableMerger<T> next;

        FJComparableMerger(T[] a, T[] w,
                           int lo, int ln, int ro, int rn, int wo, int granularity) {
            this.a = a;    this.w = w;
            this.lo = lo;  this.ln = ln;
            this.ro = ro;  this.rn = rn;
            this.wo = wo;
            this.granularity = granularity;
        }

        protected void compute() {
            FJComparableMerger<T> rights = null;
            int lln = ln;
            int lrn = rn;
            while (lln > granularity) {
                int lh = lln >>> 1;
                int ls = lo + lh;   // index of split
                T split = a[ls];
                int rl = 0;
                int rh = lrn;
                while (rl < rh) {
                    int mid = (rl + rh) >>> 1;
                    if (split.compareTo(a[ro + mid]) <= 0)
                        rh = mid;
                    else
                        rl = mid + 1;
                }
                FJComparableMerger<T> rm =
                    new FJComparableMerger<T>(a, w, ls, lln-lh, ro+rh,
                                              lrn-rh, wo+lh+rh, granularity);
                lln = lh;
                lrn = rh;
                rm.next = rights;
                rights = rm;
                rm.fork();
            }
            merge(lo+lln, ro+lrn);
            while (rights != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(rights))
                    rights.compute();
                else
                    rights.join();
                rights = rights.next;
            }
        }

        /** a standard sequential merge */
        void merge(int lFence, int rFence) {
            int l = lo;
            int r = ro;
            int k = wo;
            while (l < lFence && r < rFence) {
                T al = a[l];
                T ar = a[r];
                T t;
                if (al.compareTo(ar) <= 0) {
                    ++l;
                    t = al;
                }
                else {
                    ++r;
                    t = ar;
                }
                w[k++] = t;
            }
            while (l < lFence)
                w[k++] = a[l++];
            while (r < rFence)
                w[k++] = a[r++];
        }
    }

    // Scan (cumulate) operations

    static abstract class FJScanOp<T> extends Params<T> {
        final Reducer<T> reducer;
        final T base;

        FJScanOp(ForkJoinExecutor ex, T[] array,
                 int fromIndex, int toIndex,
                 Reducer<T> reducer,
                 T base) {
            super(ex, array, fromIndex, toIndex);
            this.reducer = reducer;
            this.base = base;
        }

        abstract T sumLeaf(int lo, int hi);
        abstract void cumulateLeaf(int lo, int hi, T in);
        abstract T sumAndCumulateLeaf(int lo, int hi);

    }

    static final class FJCumulateOp<T> extends FJScanOp<T> {
        FJCumulateOp(ForkJoinExecutor ex, T[] array,
                     int fromIndex, int toIndex,
                     Reducer<T> reducer,
                     T base) {
            super(ex, array, fromIndex, toIndex, reducer, base);
        }

        T sumLeaf(int lo, int hi) {
            T sum = base;
            if (hi != toIndex) {
                for (int i = lo; i < hi; ++i)
                    sum = reducer.combine(sum, array[i]);
            }
            return sum;
        }

        void cumulateLeaf(int lo, int hi, T in) {
            T sum = in;
            for (int i = lo; i < hi; ++i)
                array[i] = sum = reducer.combine(sum, array[i]);
        }

        T sumAndCumulateLeaf(int lo, int hi) {
            T sum = base;
            for (int i = lo; i < hi; ++i)
                array[i] = sum = reducer.combine(sum, array[i]);
            return sum;
        }
    }

    static final class FJPrecumulateOp<T> extends FJScanOp<T> {
        FJPrecumulateOp(ForkJoinExecutor ex, T[] array,
                        int fromIndex, int toIndex,
                        Reducer<T> reducer,
                        T base) {
            super(ex, array, fromIndex, toIndex, reducer, base);
        }

        T sumLeaf(int lo, int hi) {
            T sum = base;
            for (int i = lo; i < hi; ++i)
                sum = reducer.combine(sum, array[i]);
            return sum;
        }

        void cumulateLeaf(int lo, int hi, T in) {
            T sum = in;
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                array[i] = sum;
                sum = reducer.combine(sum, x);
            }
        }

        T sumAndCumulateLeaf(int lo, int hi) {
            T sum = base;
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                array[i] = sum;
                sum = reducer.combine(sum, x);
            }
            return sum;
        }
    }


    /**
     * Cumulative scan
     *
     * A basic version of scan is straightforward.
     *  Keep dividing by two to threshold segment size, and then:
     *   Pass 1: Create tree of partial sums for each segment
     *   Pass 2: For each segment, cumulate with offset of left sibling
     * See G. Blelloch's http://www.cs.cmu.edu/~scandal/alg/scan.html
     *
     * This version improves performance within FJ framework mainly by
     * allowing second pass of ready left-hand sides to proceed even
     * if some right-hand side first passes are still executing.  It
     * also combines first and second pass for leftmost segment, and
     * for cumulate (not precumulate) also skips first pass for
     * rightmost segment (whose result is not needed for second pass).
     *
     * To manage this, it relies on "phase" phase/state control field
     * maintaining bits CUMULATE, SUMMED, and FINISHED. CUMULATE is
     * main phase bit. When false, segments compute only their sum.
     * When true, they cumulate array elements. CUMULATE is set at
     * root at beginning of second pass and then propagated down. But
     * it may also be set earlier for subtrees with lo==fromIndex (the
     * left spine of tree). SUMMED is a one bit join count. For leafs,
     * set when summed. For internal nodes, becomes true when one
     * child is summed.  When second child finishes summing, it then
     * moves up tree to trigger cumulate phase. FINISHED is also a one
     * bit join count. For leafs, it is set when cumulated. For
     * internal nodes, it becomes true when one child is cumulated.
     * When second child finishes cumulating, it then moves up tree,
     * excecuting finish() at the root.
     */
    static final class FJScan<T> extends AsyncAction {
        static final int CUMULATE = 1;
        static final int SUMMED   = 2;
        static final int FINISHED = 4;

        final FJScan<T> parent;
        final FJScanOp<T> op;
        FJScan<T> left, right;
        volatile int phase;  // phase/state
        final int lo;
        final int hi;
        T in;           // Incoming cumulation
        T out;          // Outgoing cumulation of this subtree

        static final AtomicIntegerFieldUpdater<FJScan> phaseUpdater =
            AtomicIntegerFieldUpdater.newUpdater(FJScan.class, "phase");

        FJScan(FJScan<T> parent, FJScanOp<T> op, int lo, int hi) {
            this.parent = parent;
            this.op = op;
            this.lo = lo;
            this.hi = hi;
            this.in = op.base;
            this.out = op.base;
        }

        /** Returns true if can CAS CUMULATE bit true */
        boolean transitionToCumulate() {
            int c;
            while (((c = phase) & CUMULATE) == 0)
                if (phaseUpdater.compareAndSet(this, c, c | CUMULATE))
                    return true;
            return false;
        }

        public void compute() {
            if (hi - lo > op.granularity) {
                if (left == null) { // first pass
                    int mid = (lo + hi) >>> 1;
                    left =  new FJScan<T>(this, op, lo, mid);
                    right = new FJScan<T>(this, op, mid, hi);
                }

                boolean cumulate = (phase & CUMULATE) != 0;
                if (cumulate) { // push down sums
                    T cin = in;
                    left.in = cin;
                    right.in = op.reducer.combine(cin, left.out);
                }

                if (!cumulate || right.transitionToCumulate())
                    right.fork();
                if (!cumulate || left.transitionToCumulate())
                    left.compute();
            }
            else {
                int cb;
                for (;;) { // Establish action: sum, cumulate, or both
                    int b = phase;
                    if ((b & FINISHED) != 0) // already done
                        return;
                    if ((b & CUMULATE) != 0)
                        cb = FINISHED;
                    else if (lo == op.fromIndex) // combine leftmost
                        cb = (SUMMED|FINISHED);
                    else
                        cb = SUMMED;
                    if (phaseUpdater.compareAndSet(this, b, b|cb))
                        break;
                }

                // perform the action
                if (cb == SUMMED)
                    out = op.sumLeaf(lo, hi);
                else if (cb == FINISHED)
                    op.cumulateLeaf(lo, hi, in);
                else if (cb == (SUMMED|FINISHED))
                    out = op.sumAndCumulateLeaf(lo, hi);

                // propagate up
                FJScan<T> ch = this;
                FJScan<T> par = parent;
                for (;;) {
                    if (par == null) {
                        if ((cb & FINISHED) != 0)
                            ch.finish();
                        break;
                    }
                    int pb = par.phase;
                    if ((pb & cb & FINISHED) != 0) { // both finished
                        ch = par;
                        par = par.parent;
                    }
                    else if ((pb & cb & SUMMED) != 0) { // both summed
                        par.out = op.reducer.combine(par.left.out,
                                                     par.right.out);
                        int refork = ((pb & CUMULATE) == 0 &&
                                      par.lo == op.fromIndex)? CUMULATE : 0;
                        int nextPhase = pb|cb|refork;
                        if (pb == nextPhase ||
                            phaseUpdater.compareAndSet(par, pb, nextPhase)) {
                            if (refork != 0)
                                par.fork();
                            cb = SUMMED; // drop finished bit
                            ch = par;
                            par = par.parent;
                        }
                    }
                    else if (phaseUpdater.compareAndSet(par, pb, pb|cb))
                        break;
                }
            }
        }
    }
}
