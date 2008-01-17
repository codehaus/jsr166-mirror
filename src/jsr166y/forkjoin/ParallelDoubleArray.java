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
 * An array of doubles supporting parallel operations.  This class
 * provides methods supporting the same operations as {@link
 * ParallelArray}, but specialized for scalar doubles. It additionally
 * provides a few methods specific to numerical values.
 */
public class ParallelDoubleArray extends PDAWithBounds {
    // Same internals as ParallelArray, but specialized for doubles
    AsList listView; // lazily constructed

    /**
     * Returns a common default executor for use in ParallelArrays.
     * This executor arranges enough parallelism to use most, but not
     * necessarily all, of the avaliable processors on this system.
     * @return the executor
     */
    public static ForkJoinExecutor defaultExecutor() {
        return PAS.defaultExecutor();
    }

    /**
     * Constructor for use by subclasses to create a new ParallelDoubleArray
     * using the given executor, and initially using the supplied
     * array, with effective size bound by the given limit. This
     * constructor is designed to enable extensions via
     * subclassing. To create a ParallelDoubleArray, use {@link #create},
     * {@link #createEmpty}, {@link #createUsingHandoff} or {@link
     * #createFromCopy}.
     * @param executor the executor
     * @param array the array
     * @param limit the upper bound limit
     */
    protected ParallelDoubleArray(ForkJoinExecutor executor, double[] array,
                                  int limit) {
        super(executor, 0, limit, array);
        if (executor == null || array == null)
            throw new NullPointerException();
        if (limit < 0 || limit > array.length)
            throw new IllegalArgumentException();
    }

    /**
     * Trusted internal version of protected constructor.
     */
    ParallelDoubleArray(ForkJoinExecutor executor, double[] array) {
        super(executor, 0, array.length, array);
    }

    /**
     * Creates a new ParallelDoubleArray using the given executor and
     * an array of the given size
     * @param size the array size
     * @param executor the executor
     */
    public static ParallelDoubleArray create
        (int size, ForkJoinExecutor executor) {
        double[] array = new double[size];
        return new ParallelDoubleArray(executor, array, size);
    }

    /**
     * Creates a new ParallelDoubleArray initially using the given array and
     * executor. In general, the handed off array should not be used
     * for other purposes once constructing this ParallelDoubleArray.  The
     * given array may be internally replaced by another array in the
     * course of methods that add or remove elements.
     * @param handoff the array
     * @param executor the executor
     */
    public static ParallelDoubleArray createUsingHandoff
        (double[] handoff, ForkJoinExecutor executor) {
        return new ParallelDoubleArray(executor, handoff, handoff.length);
    }

    /**
     * Creates a new ParallelDoubleArray using the given executor and
     * initially holding copies of the given
     * source elements.
     * @param source the source of initial elements
     * @param executor the executor
     */
    public static ParallelDoubleArray createFromCopy
        (double[] source, ForkJoinExecutor executor) {
        // For now, avoid copyOf so people can compile with Java5
        int size = source.length;
        double[] array = new double[size];
        System.arraycopy(source, 0, array, 0, size);
        return new ParallelDoubleArray(executor, array, size);
    }

    /**
     * Creates a new ParallelDoubleArray using an array of the given size,
     * initially holding copies of the given source truncated or
     * padded with zeros to obtain the specified length.
     * @param source the source of initial elements
     * @param size the array size
     * @param executor the executor
     */
    public static ParallelDoubleArray createFromCopy
        (int size, double[] source, ForkJoinExecutor executor) {
        // For now, avoid copyOf so people can compile with Java5
        double[] array = new double[size];
        System.arraycopy(source, 0, array, 0,
                         Math.min(source.length, size));
        return new ParallelDoubleArray(executor, array, size);
    }

    /**
     * Creates a new ParallelDoubleArray using the given executor and
     * an array of the given size, but with an initial effective size
     * of zero, enabling incremental insertion via {@link
     * ParallelDoubleArray#asList} operations.
     * @param size the array size
     * @param executor the executor
     */
    public static ParallelDoubleArray createEmpty
        (int size, ForkJoinExecutor executor) {
        double[] array = new double[size];
        return new ParallelDoubleArray(executor, array, 0);
    }

    /**
     * Summary statistics for a possibly bounded, filtered, and/or
     * mapped ParallelDoubleArray.
     */
    public static interface SummaryStatistics {
        /** Return the number of elements */
        public int size();
        /** Return the minimum element, or Double.MAX_VALUE if empty */
        public double min();
        /** Return the maximum element, or -Double.MAX_VALUE if empty */
        public double max();
        /** Return the index of the minimum element, or -1 if empty */
        public int indexOfMin();
        /** Return the index of the maximum element, or -1 if empty */
        public int indexOfMax();
        /** Return the sum of all elements */
        public double sum();
        /** Return the arithmetic average of all elements */
        public double average();
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
    public void apply(DoubleProcedure procedure) {
        super.apply(procedure);
    }

    /**
     * Returns reduction of elements
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public double reduce(DoubleReducer reducer, double base) {
        return super.reduce(reducer, base);
    }

    /**
     * Returns a new ParallelDoubleArray holding all elements
     * @return a new ParallelDoubleArray holding all elements
     */
    public ParallelDoubleArray all() {
        return super.all();
    }

    /**
     * Replaces elements with the results of applying the given op
     * to their current values.
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArray replaceWithMapping(DoubleOp  op) {
        super.replaceWithMapping(op);
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * op to their indices.
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArray replaceWithMappedIndex(IntToDouble op) {
        super.replaceWithMappedIndex(op);
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * mapping to each index and current element value
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArray replaceWithMappedIndex(IntAndDoubleToDouble op) {
        super.replaceWithMappedIndex(op);
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * generator. For example, to fill the array with uniform random
     * values, use
     * <tt>replaceWithGeneratedValue(Ops.doubleRandom())</tt>
     * @param generator the generator
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArray replaceWithGeneratedValue(DoubleGenerator generator) {
        super.replaceWithGeneratedValue(generator);
        return this;
    }

    /**
     * Replaces elements with the given value.
     * @param value the value
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArray replaceWithValue(double value) {
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
    public ParallelDoubleArray replaceWithMapping(BinaryDoubleOp combiner,
                                   ParallelDoubleArray other) {
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
    public ParallelDoubleArray replaceWithMapping(BinaryDoubleOp combiner,
                                   double[] other) {
        super.replaceWithMapping(combiner, other);
        return this;
    }

    /**
     * Returns the index of some element equal to given target, or -1
     * if not present
     * @param target the element to search for
     * @return the index or -1 if not present
     */
    public int indexOf(double target) {
        return super.indexOf(target);
    }

    /**
     * Assuming this array is sorted, returns the index of an element
     * equal to given target, or -1 if not present. If the array
     * is not sorted, the results are undefined.
     * @param target the element to search for
     * @return the index or -1 if not present
     */
    public int binarySearch(double target) {
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
    public int binarySearch(double target, DoubleComparator comparator) {
        return super.binarySearch(target, comparator);
    }

    /**
     * Returns summary statistics, using the given comparator
     * to locate minimum and maximum elements.
     * @param comparator the comparator to use for
     * locating minimum and maximum elements
     * @return the summary.
     */
    public ParallelDoubleArray.SummaryStatistics summary
        (DoubleComparator comparator) {
        return super.summary(comparator);
    }

    /**
     * Returns summary statistics, using natural comparator
     * @return the summary.
     */
    public ParallelDoubleArray.SummaryStatistics summary() {
        return super.summary();
    }

    /**
     * Returns the minimum element, or Double.MAX_VALUE if empty
     * @param comparator the comparator
     * @return minimum element, or Double.MAX_VALUE if empty
     */
    public double min(DoubleComparator comparator) {
        return super.min(comparator);
    }

    /**
     * Returns the minimum element, or Double.MAX_VALUE if empty,
     * @return minimum element, or Double.MAX_VALUE if empty
     */
    public double min() {
        return super.min();
    }

    /**
     * Returns the maximum element, or -Double.MAX_VALUE if empty
     * @param comparator the comparator
     * @return maximum element, or -Double.MAX_VALUE if empty
     */
    public double max(DoubleComparator comparator) {
        return super.max(comparator);
    }

    /**
     * Returns the maximum element, or -Double.MAX_VALUE if empty
     * @return maximum element, or -Double.MAX_VALUE if empty
     */
    public double max() {
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
    public ParallelDoubleArray cumulate(DoubleReducer reducer, double base) {
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
    public double precumulate(DoubleReducer reducer, double base) {
        return super.precumulate(reducer, base);
    }

    /**
     * Sorts the array. Unlike Arrays.sort, this sort does
     * not guarantee that elements with equal keys maintain their
     * relative position in the array.
     * @param comparator the comparator to use
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArray sort(DoubleComparator comparator) {
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
    public ParallelDoubleArray sort() {
        super.sort();
        return this;
    }

    /**
     * Removes consecutive elements that are equal,
     * shifting others leftward, and possibly decreasing size.  This
     * method may be used after sorting to ensure that this
     * ParallelDoubleArray contains a set of unique elements.
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArray removeConsecutiveDuplicates() {
        // Sequential implementation for now
        int k = 0;
        int n = upperBound;
        if (k < n) {
            double[] arr = this.array;
            double last = arr[k++];
            for (int i = k; i < n; ++i) {
                double x = arr[i];
                if (last != x)
                    arr[k++] = last = x;
            }
            removeSlotsAt(k, n);
        }
        return this;
    }

    /**
     * Returns a new ParallelDoubleArray containing only the unique
     * elements of this array (that is, without any duplicates).
     * @return the new ParallelDoubleArray
     */
    public ParallelDoubleArray allUniqueElements() {
        return super.allUniqueElements();
    }

    /**
     * Removes from the array all elements for which the given
     * selector holds.
     * @param selector the selector
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArray removeAll(DoublePredicate selector) {
        PDAWithBoundedFilter v = 
            new PDAWithBoundedFilter(ex, 0, upperBound, array, selector);
        PAS.FJRemoveAllDriver f = new PAS.FJRemoveAllDriver(v, 0, upperBound);
        ex.invoke(f);
        removeSlotsAt(f.offset, upperBound);
        return this;
    }


    /**
     * Returns the sum of elements
     * @return the sum of elements
     */
    public double sum() {
        return super.sum();
    }

    /**
     * Replaces each element with the running sum
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArray cumulateSum() {
        super.cumulateSum();
        return this;
    }

    /**
     * Replaces each element with its prefix sum
     * @return the total sum
     */
    public double precumulateSum() {
        return super.precumulateSum();
    }

    /**
     * Returns an operation prefix that causes a method to
     * operate only on the elements of the array between
     * firstIndex (inclusive) and upperBound (exclusive).
     * @param firstIndex the lower bound (inclusive)
     * @param upperBound the upper bound (exclusive)
     * @return operation prefix
     */
    public ParallelDoubleArrayWithBounds withBounds(int firstIndex, int upperBound) {
        return super.withBounds(firstIndex, upperBound);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * only on the elements of the array for which the given selector
     * returns true
     * @param selector the selector
     * @return operation prefix
     */
    public ParallelDoubleArrayWithFilter withFilter(DoublePredicate selector) {
        return super.withFilter(selector);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public <U> ParallelDoubleArrayWithMapping<U> withMapping
        (DoubleToObject<? extends U> op) {
        return super.withMapping(op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public ParallelDoubleArrayWithDoubleMapping withMapping(DoubleOp op) {
        return super.withMapping(op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public ParallelDoubleArrayWithLongMapping withMapping(DoubleToLong op) {
        return super.withMapping(op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
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
    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
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
    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public ParallelDoubleArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
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
    public ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
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
    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
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
    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return super.withMapping(combiner, other);
    }

    /**
     * Returns an operation prefix that causes a method to operate on
     * mappings of this array using the given mapper that accepts as
     * arguments an element's current index and value, and produces a
     * new value.
     * @param mapper the mapper
     * @return operation prefix
     */
    public <U> ParallelDoubleArrayWithMapping<U> withIndexedMapping
        (IntAndDoubleToObject<? extends U> mapper) {
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
    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
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
    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
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
    public Iterator<Double> iterator() {
        return new ParallelDoubleArrayIterator(array, upperBound);
    }

    static final class ParallelDoubleArrayIterator
        implements Iterator<Double> {
        int cursor;
        final double[] arr;
        final int hi;
        ParallelDoubleArrayIterator(double[] a, int limit) { arr = a; hi = limit; }
        public boolean hasNext() { return cursor < hi; }
        public Double next() {
            if (cursor >= hi)
                throw new NoSuchElementException();
            return Double.valueOf(arr[cursor++]);
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    // List support

    /**
     * Returns a view of this ParallelDoubleArray as a List. This List
     * has the same structural and performance characteristics as
     * {@link ArrayList}, and may be used to modify, replace or extend
     * the bounds of the array underlying this ParallelDoubleArray.
     * The methods supported by this list view are <em>not</em> in
     * general implemented as parallel operations. This list is also
     * not itself thread-safe.  In particular, performing list updates
     * while other parallel operations are in progress has undefined
     * (and surely undesired) effects.
     * @return a list view
     */
    public List<Double> asList() {
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
     * Returns the underlying array used for computations
     * @return the array
     */
    public double[] getArray() { return array; }

    /**
     * Returns the element of the array at the given index
     * @param i the index
     * @return the element of the array at the given index
     */
    public double get(int i) { return array[i]; }

    /**
     * Sets the element of the array at the given index to the given value
     * @param i the index
     * @param x the value
     */
    public void set(int i, double x) { array[i] = x; }

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
    public ParallelDoubleArray addAll(double[] other) {
        int csize = other.length;
        int end = upperBound;
        insertSlotsAt(end, csize);
        System.arraycopy(other, 0, array, end, csize);
        return this;
    }

    /**
     * Equivalent to <tt>asList().addAll</tt> but specialized for
     * ParallelDoubleArray arguments and likely to be more efficient.
     * @param other the elements to add
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArray addAll(ParallelDoubleArray other) {
        int csize = other.size();
        int end = upperBound;
        insertSlotsAt(end, csize);
        if (!other.hasMap()) 
            System.arraycopy(other.array, 0, array, end, csize);
        else {
            int k = end;
            for (int i = other.firstIndex; i < other.upperBound; ++i)
                array[k++] = other.dget(i);
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

    final void replaceElementsWith(double[] a) {
        System.arraycopy(a, 0, array, 0, a.length);
        upperBound = a.length;
    }

    final void resizeArray(int newCap) {
        int cap = array.length;
        if (newCap > cap) {
            double[] a = new double[newCap];
            System.arraycopy(array, 0, a, 0, cap);
            array = a;
        }
    }

    final void insertElementAt(int index, double e) {
        int hi = upperBound++;
        if (hi >= array.length)
            resizeArray((hi * 3)/2 + 1);
        if (hi > index)
            System.arraycopy(array, index, array, index+1, hi - index);
        array[index] = e;
    }

    final void appendElement(double e) {
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
        --upperBound;
    }

    final void removeSlotsAt(int fromIndex, int toIndex) {
        if (fromIndex < toIndex) {
            int size = upperBound;
            System.arraycopy(array, toIndex, array, fromIndex, size - toIndex);
            int newSize = size - (toIndex - fromIndex);
            upperBound = newSize;
        }
    }

    final int seqIndexOf(double target) {
        double[] arr = array;
        int fence = upperBound;
        for (int i = 0; i < fence; i++)
            if (target == arr[i])
                return i;
        return -1;
    }

    final int seqLastIndexOf(double target) {
        double[] arr = array;
        for (int i = upperBound - 1; i >= 0; i--)
            if (target == arr[i])
                return i;
        return -1;
    }

    final class ListIter implements ListIterator<Double> {
        int cursor;
        int lastRet;
        double[] arr; // cache array and bound
        int hi;
        ListIter(int lo) {
            this.cursor = lo;
            this.lastRet = -1;
            this.arr = ParallelDoubleArray.this.array;
            this.hi = ParallelDoubleArray.this.upperBound;
        }

        public boolean hasNext() {
            return cursor < hi;
        }

        public Double next() {
            int i = cursor;
            if (i < 0 || i >= hi)
                throw new NoSuchElementException();
            double next = arr[i];
            lastRet = i;
            cursor = i + 1;
            return Double.valueOf(next);
        }

        public void remove() {
            int k = lastRet;
            if (k < 0)
                throw new IllegalStateException();
            ParallelDoubleArray.this.removeSlotAt(k);
            hi = ParallelDoubleArray.this.upperBound;
            if (lastRet < cursor)
                cursor--;
            lastRet = -1;
        }

        public boolean hasPrevious() {
            return cursor > 0;
        }

        public Double previous() {
            int i = cursor - 1;
            if (i < 0 || i >= hi)
                throw new NoSuchElementException();
            double previous = arr[i];
            lastRet = cursor = i;
            return Double.valueOf(previous);
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor - 1;
        }

        public void set(Double e) {
            int i = lastRet;
            if (i < 0 || i >= hi)
                throw new NoSuchElementException();
            arr[i] = e.doubleValue();
        }

        public void add(Double e) {
            int i = cursor;
            ParallelDoubleArray.this.insertElementAt(i, e.doubleValue());
            arr = ParallelDoubleArray.this.array;
            hi = ParallelDoubleArray.this.upperBound;
            lastRet = -1;
            cursor = i + 1;
        }
    }

    final class AsList extends AbstractList<Double> implements RandomAccess {
        public Double get(int i) {
            if (i >= upperBound)
                throw new IndexOutOfBoundsException();
            return Double.valueOf(array[i]);
        }

        public Double set(int i, Double x) {
            if (i >= upperBound)
                throw new IndexOutOfBoundsException();
            double[] arr = array;
            Double t = Double.valueOf(arr[i]);
            arr[i] = x.doubleValue();
            return t;
        }

        public boolean isEmpty() {
            return upperBound == 0;
        }

        public int size() {
            return upperBound;
        }

        public Iterator<Double> iterator() {
            return new ListIter(0);
        }

        public ListIterator<Double> listIterator() {
            return new ListIter(0);
        }

        public ListIterator<Double> listIterator(int index) {
            if (index < 0 || index > upperBound)
                throw new IndexOutOfBoundsException();
            return new ListIter(index);
        }

        public boolean add(Double e) {
            appendElement(e.doubleValue());
            return true;
        }

        public void add(int index, Double e) {
            if (index < 0 || index > upperBound)
                throw new IndexOutOfBoundsException();
            insertElementAt(index, e.doubleValue());
        }

        public boolean addAll(Collection<? extends Double> c) {
            int csize = c.size();
            if (csize == 0)
                return false;
            int hi = upperBound;
            setLimit(hi + csize);
            double[] arr = array;
            for (Double e : c)
                arr[hi++] = e.doubleValue();
            return true;
        }

        public boolean addAll(int index, Collection<? extends Double> c) {
            if (index < 0 || index > upperBound)
                throw new IndexOutOfBoundsException();
            int csize = c.size();
            if (csize == 0)
                return false;
            insertSlotsAt(index, csize);
            double[] arr = array;
            for (Double e : c)
                arr[index++] = e.doubleValue();
            return true;
        }

        public void clear() {
            upperBound = 0;
        }

        public boolean remove(Object o) {
            if (!(o instanceof Double))
                return false;
            int idx = seqIndexOf(((Double)o).doubleValue());
            if (idx < 0)
                return false;
            removeSlotAt(idx);
            return true;
        }

        public Double remove(int index) {
            Double oldValue = get(index);
            removeSlotAt(index);
            return oldValue;
        }

        public void removeRange(int fromIndex, int toIndex) {
            removeSlotsAt(fromIndex, toIndex);
        }

        public boolean contains(Object o) {
            if (!(o instanceof Double))
                return false;
            return seqIndexOf(((Double)o).doubleValue()) >= 0;
        }

        public int indexOf(Object o) {
            if (!(o instanceof Double))
                return -1;
            return seqIndexOf(((Double)o).doubleValue());
        }

        public int lastIndexOf(Object o) {
            if (!(o instanceof Double))
                return -1;
            return seqLastIndexOf(((Double)o).doubleValue());
        }
    }
}

abstract class PDAWithMappingBase<U> extends ParallelDoubleArrayWithMapping<U> {
    final DoubleToObject<? extends U> op;
    PDAWithMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
                    DoubleToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final Object oget(int i) { return op.op(this.array[i]); }

    final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
        final double[] a = this.array;
        final DoubleToObject f = op;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   Object[] dest, int offset) {
        final double[] a = this.array;
        final DoubleToObject f = op;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = f.op(a[indices[i]]);
    }
}

final class PDAWithBoundedMapping<U> extends PDAWithMappingBase<U> {
    PDAWithBoundedMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
                       DoubleToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping(Op<? super U, ? extends V> op) {
        return new PDAWithBoundedMapping<V>
            (ex, firstIndex, upperBound, array, Ops.compoundOp(this.op, op));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping(ObjectToDouble<? super U> op){
        return new PDAWithBoundedDoubleMapping
            (ex, firstIndex, upperBound, array, Ops.compoundOp(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping(ObjectToLong<? super U> op) {
        return new PDAWithBoundedLongMapping
            (ex, firstIndex, upperBound, array, Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final double[] a = this.array;
        final DoubleToObject f = op;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(a[i]));
    }

    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        if (lo >= hi)
            return base;
        final double[] a = this.array;
        final DoubleToObject f = op;
        Object r = f.op(a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(a[i]));
        return r;
    }

}

final class PDAWithBoundedFilteredMapping<U>
    extends PDAWithMappingBase<U> {
    final DoublePredicate selector;

    PDAWithBoundedFilteredMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoublePredicate selector,
         DoubleToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    DoublePredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelArray<U> all(Class<? super U> elementType) {
        PAS.FJOSelectAllDriver r = new PAS.FJOSelectAllDriver
            (this, elementType);
        ex.invoke(r);
        return new ParallelArray<U>(ex, (U[])(r.results));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping(Op<? super U, ? extends V> op) {
        return new PDAWithBoundedFilteredMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping(ObjectToDouble<? super U> op) {
        return new PDAWithBoundedFilteredDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping(ObjectToLong<? super U> op) {
        return new PDAWithBoundedFilteredLongMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final DoublePredicate s = selector;
        final DoubleToObject f = op;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            double x = a[i];
            if (s.op(x))
                procedure.op(f.op(x));
        }
    }

    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        boolean gotFirst = false;
        Object r = base;
        final DoublePredicate s = selector;
        final DoubleToObject f = op;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            double x = a[i];
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

abstract class PDAWithIndexedMappingBase<U> extends ParallelDoubleArrayWithMapping<U> {
    final IntAndDoubleToObject<? extends U> op;
    PDAWithIndexedMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         IntAndDoubleToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final Object oget(int i) { return op.op(i, this.array[i]); }

    final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
        final IntAndDoubleToObject f = op;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(i, a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   Object[] dest, int offset) {
        final double[] a = this.array;
        final IntAndDoubleToObject f = op;
        for (int i = loIdx; i < hiIdx; ++i) {
            int idx = indices[i];
            dest[offset++] = f.op(idx, a[idx]);
        }
    }
}

final class PDAWithBoundedIndexedMapping<U>
    extends PDAWithIndexedMappingBase<U> {
    PDAWithBoundedIndexedMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         IntAndDoubleToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public <V> ParallelDoubleArrayWithMapping< V> withMapping
        (Op<? super U, ? extends V> op) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectToDouble<? super U> op) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (ObjectToLong<? super U> op) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final IntAndDoubleToObject f = op;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(i, a[i]));
    }

    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        if (lo >= hi)
            return base;
        final double[] a = this.array;
        final IntAndDoubleToObject f = op;
        Object r = f.op(lo, a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(i, a[i]));
        return r;
    }

}

final class PDAWithBoundedFilteredIndexedMapping<U>
    extends PDAWithIndexedMappingBase<U> {
    final DoublePredicate selector;

    PDAWithBoundedFilteredIndexedMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoublePredicate selector,
         IntAndDoubleToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    DoublePredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public <V> ParallelDoubleArrayWithMapping< V> withMapping
        (Op<? super U, ? extends V> op) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping(ObjectToDouble<? super U> op) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping(ObjectToLong<? super U> op) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final DoublePredicate s = selector;
        final double[] a = this.array;
        final IntAndDoubleToObject f = op;
        for (int i = lo; i < hi; ++i) {
            double x = a[i];
            if (s.op(x))
                procedure.op(f.op(i, x));
        }
    }
    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        final DoublePredicate s = selector;
        final double[] a = this.array;
        final IntAndDoubleToObject f = op;
        boolean gotFirst = false;
        Object r = base;
        for (int i = lo; i < hi; ++i) {
            double x = a[i];
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


class PDAWithBounds extends ParallelDoubleArrayWithBounds {
    PDAWithBounds
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array) {
        super(ex, firstIndex, upperBound, array);
    }

    PDAWithBounds(ParallelDoubleArray pa) {
        super(pa.ex, 0, pa.upperBound, pa.array);
    }

    double dget(int i) { return this.array[i]; }

    public ParallelDoubleArrayWithBounds withBounds(int firstIndex, int upperBound) {
        if (firstIndex > upperBound)
            throw new IllegalArgumentException
                ("firstIndex(" + firstIndex +
                 ") > upperBound(" + upperBound+")");
        if (firstIndex < 0)
            throw new ArrayIndexOutOfBoundsException(firstIndex);
        if (upperBound - firstIndex > this.upperBound - this.firstIndex)
            throw new ArrayIndexOutOfBoundsException(upperBound);
        return new PDAWithBounds(ex,
                              this.firstIndex + firstIndex,
                              this.firstIndex + upperBound,
                              array);
    }

    public ParallelDoubleArrayWithFilter withFilter(DoublePredicate selector) {
        return new PDAWithBoundedFilter
            (ex, firstIndex, upperBound, array, selector);
    }

    public <U> ParallelDoubleArrayWithMapping<U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PDAWithBoundedMapping<U>
            (ex, firstIndex, upperBound, array, op);
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping(DoubleOp op) {
        return new PDAWithBoundedDoubleMapping
            (ex, firstIndex, upperBound, array, op);
    }

    public ParallelDoubleArrayWithLongMapping withMapping(DoubleToLong op) {
        return new PDAWithBoundedLongMapping
            (ex, firstIndex, upperBound, array, op);
    }

    public ParallelDoubleArrayWithFilter orFilter(DoublePredicate selector) {
        return new PDAWithBoundedFilter
            (ex, firstIndex, upperBound, array, selector);
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array, mapper);
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, mapper);
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array, mapper);
    }

    public ParallelDoubleArray allUniqueElements() {
        PAS.DUniquifierTable tab = new PAS.DUniquifierTable
            (upperBound - firstIndex, this.array, null);
        PAS.FJUniquifier f = new PAS.FJUniquifier
            (this, firstIndex, upperBound, null, tab);
        ex.invoke(f);
        double[] res = tab.uniqueElements(f.count);
        return new ParallelDoubleArray(ex, res);
    }

    public int indexOf(double target) {
        AtomicInteger result = new AtomicInteger(-1);
        PAS.FJDIndexOf f = new PAS.FJDIndexOf
            (this, firstIndex, upperBound, null, result, target);
        ex.invoke(f);
        return result.get();
    }

    public int binarySearch(double target) {
        final double[] a = this.array;
        int lo = firstIndex;
        int hi = upperBound - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            double m = a[mid];
            if (target == m)
                return mid;
            else if (target < m)
                hi = mid - 1;
            else
                lo = mid + 1;
        }
        return -1;
    }

    public int binarySearch(double target, DoubleComparator comparator) {
        final double[] a = this.array;
        int lo = firstIndex;
        int hi = upperBound - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = comparator.compare(target, a[mid]);
            if (c == 0)
                return mid;
            else if (c < 0)
                hi = mid - 1;
            else
                lo = mid + 1;
        }
        return -1;
    }

    public ParallelDoubleArrayWithBounds cumulate(DoubleReducer reducer, double base) {
        PAS.FJDCumulateOp op = new PAS.FJDCumulateOp(this, reducer, base);
        PAS.FJDScan r = new PAS.FJDScan(null, op, firstIndex, upperBound);
        ex.invoke(r);
        return this;
    }

    public ParallelDoubleArrayWithBounds cumulateSum() {
        PAS.FJDCumulatePlusOp op = new PAS.FJDCumulatePlusOp(this);
        PAS.FJDScan r = new PAS.FJDScan(null, op, firstIndex, upperBound);
        ex.invoke(r);
        return this;
    }

    public double precumulate(DoubleReducer reducer, double base) {
        PAS.FJDPrecumulateOp op = new PAS.FJDPrecumulateOp
            (this, reducer, base);
        PAS.FJDScan r = new PAS.FJDScan(null, op, firstIndex, upperBound);
        ex.invoke(r);
        return r.out;
    }

    public double precumulateSum() {
        PAS.FJDPrecumulatePlusOp op = new PAS.FJDPrecumulatePlusOp(this);
        PAS.FJDScan r = new PAS.FJDScan(null, op, firstIndex, upperBound);
        ex.invoke(r);
        return r.out;
    }

    public ParallelDoubleArrayWithBounds sort(DoubleComparator cmp) {
        ex.invoke(new PAS.FJDSorter
                  (cmp, this.array, new double[upperBound],
                   firstIndex, upperBound - firstIndex, getThreshold()));
        return this;
    }

    public ParallelDoubleArrayWithBounds sort() {
        ex.invoke(new PAS.FJDCSorter
                  (this.array, new double[upperBound],
                   firstIndex, upperBound - firstIndex, getThreshold()));
        return this;
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(a[i]);
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        if (lo >= hi)
            return base;
        final double[] a = this.array;
        double r = a[lo];
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, a[i]);
        return r;
    }

    void leafCombine(int lo, int hi, double[] other, int otherOffset,
                     double[] dest, BinaryDoubleOp combiner) {
        final double[] a = this.array;
        int k = lo - firstIndex;
        for (int i = lo; i < hi; ++i) {
            dest[k] = combiner.op(a[i], other[i + otherOffset]);
            ++k;
        }
    }
}

final class PDAWithBoundedFilter extends ParallelDoubleArrayWithFilter {
    final DoublePredicate selector;
    PDAWithBoundedFilter
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoublePredicate selector) {
        super(ex, firstIndex, upperBound, array);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    DoublePredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }
    double dget(int i) { return this.array[i]; }

    public ParallelDoubleArrayWithFilter withFilter(DoublePredicate selector) {
        return new PDAWithBoundedFilter
            (ex, firstIndex, upperBound, array,
             Ops.andPredicate(this.selector, selector));
    }

    public ParallelDoubleArrayWithFilter orFilter(DoublePredicate selector) {
        return new PDAWithBoundedFilter
            (ex, firstIndex, upperBound, array,
             Ops.orPredicate(this.selector, selector));
    }

    public <U> ParallelDoubleArrayWithMapping<U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PDAWithBoundedFilteredMapping<U>
            (ex, firstIndex, upperBound, array, selector, op);
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping(DoubleOp op) {
        return new PDAWithBoundedFilteredDoubleMapping
            (ex, firstIndex, upperBound, array, selector, op);
    }

    public ParallelDoubleArrayWithLongMapping withMapping(DoubleToLong op) {
        return new PDAWithBoundedFilteredLongMapping
            (ex, firstIndex, upperBound, array, selector, op);
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector, mapper);
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector, mapper);
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector, mapper);
    }

    public ParallelDoubleArray allUniqueElements() {
        PAS.DUniquifierTable tab = new PAS.DUniquifierTable
            (upperBound - firstIndex, this.array, selector);
        PAS.FJUniquifier f = new PAS.FJUniquifier
            (this, firstIndex, upperBound, null, tab);
        ex.invoke(f);
        double[] res = tab.uniqueElements(f.count);
        return new ParallelDoubleArray(ex, res);
    }

    void leafApply(int lo, int hi, DoubleProcedure  procedure) {
        final DoublePredicate s = selector;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            double x = a[i];
            if (s.op(x))
                procedure.op(x);
        }
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        final DoublePredicate s = selector;
        boolean gotFirst = false;
        double r = base;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            double x = a[i];
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

abstract class PDAWithDoubleMappingBase extends ParallelDoubleArrayWithDoubleMapping {
    final DoubleOp op;
    PDAWithDoubleMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoubleOp op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final double dget(int i) { return op.op(this.array[i]); }

    final void leafTransfer(int lo, int hi, double[] dest, int offset) {
        final double[] a = this.array;
        final DoubleOp f = op;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   double[] dest, int offset) {
        final double[] a = this.array;
        final DoubleOp f = op;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = (a[indices[i]]);
    }
}

final class PDAWithBoundedDoubleMapping extends PDAWithDoubleMappingBase {
    PDAWithBoundedDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoubleOp op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping(DoubleOp op) {
        return new PDAWithBoundedDoubleMapping
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping(DoubleToLong op) {
        return new PDAWithBoundedLongMapping
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelDoubleArrayWithMapping<U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PDAWithBoundedMapping<U>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final double[] a = this.array;
        final DoubleOp f = op;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(a[i]));
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        if (lo >= hi)
            return base;
        final double[] a = this.array;
        final DoubleOp f = op;
        double r = f.op(a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(a[i]));
        return r;
    }

}

final class PDAWithBoundedFilteredDoubleMapping
    extends PDAWithDoubleMappingBase {
    final DoublePredicate selector;
    PDAWithBoundedFilteredDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoublePredicate selector, DoubleOp op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    DoublePredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelDoubleArrayWithDoubleMapping withMapping(DoubleOp op) {
        return new PDAWithBoundedFilteredDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping(DoubleToLong op) {
        return new PDAWithBoundedFilteredLongMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelDoubleArrayWithMapping<U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PDAWithBoundedFilteredMapping<U>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final DoublePredicate s = selector;
        final DoubleOp f = op;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            double x = a[i];
            if (s.op(x))
                procedure.op(f.op(x));
        }
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        final DoublePredicate s = selector;
        boolean gotFirst = false;
        double r = base;
        final double[] a = this.array;
        final DoubleOp f = op;
        for (int i = lo; i < hi; ++i) {
            double t = a[i];
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

abstract class PDAWithIndexedDoubleMappingBase
    extends ParallelDoubleArrayWithDoubleMapping {
    final IntAndDoubleToDouble op;
    PDAWithIndexedDoubleMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         IntAndDoubleToDouble op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final double dget(int i) { return op.op(i, this.array[i]); }

    final void leafTransfer(int lo, int hi, double[] dest, int offset) {
        final IntAndDoubleToDouble f = op;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(i, a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   double[] dest, int offset) {
        final double[] a = this.array;
        final IntAndDoubleToDouble f = op;
        for (int i = loIdx; i < hiIdx; ++i) {
            int idx = indices[i];
            dest[offset++] = f.op(idx, a[idx]);
        }
    }

}

final class PDAWithBoundedIndexedDoubleMapping
    extends PDAWithIndexedDoubleMappingBase {
    PDAWithBoundedIndexedDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         IntAndDoubleToDouble op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping(DoubleOp op) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping(DoubleToLong op) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelDoubleArrayWithMapping< U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PDAWithBoundedIndexedMapping<U>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final IntAndDoubleToDouble f = op;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(i, a[i]));
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        if (lo >= hi)
            return base;
        final double[] a = this.array;
        final IntAndDoubleToDouble f = op;
        double r = f.op(lo, a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(i, a[i]));
        return r;
    }

}

final class PDAWithBoundedFilteredIndexedDoubleMapping
    extends PDAWithIndexedDoubleMappingBase {
    final DoublePredicate selector;
    PDAWithBoundedFilteredIndexedDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoublePredicate selector,
         IntAndDoubleToDouble op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    DoublePredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelDoubleArrayWithDoubleMapping withMapping(DoubleOp op) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping(DoubleToLong op) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelDoubleArrayWithMapping< U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PDAWithBoundedFilteredIndexedMapping<U>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final DoublePredicate s = selector;
        final double[] a = this.array;
        final IntAndDoubleToDouble f = op;
        for (int i = lo; i < hi; ++i) {
            double x = a[i];
            if (s.op(x))
                procedure.op(f.op(i, x));
        }
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        final DoublePredicate s = selector;
        final IntAndDoubleToDouble f = op;
        boolean gotFirst = false;
        double r = base;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            double t = a[i];
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

abstract class PDAWithLongMappingBase extends ParallelDoubleArrayWithLongMapping {
    final DoubleToLong op;
    PDAWithLongMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoubleToLong op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final long lget(int i) { return op.op(this.array[i]); }

    final void leafTransfer(int lo, int hi, long[] dest, int offset) {
        final double[] a = this.array;
        final DoubleToLong f = op;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   long[] dest, int offset) {
        final double[] a = this.array;
        final DoubleToLong f = op;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = f.op(a[indices[i]]);
    }

}

final class PDAWithBoundedLongMapping extends PDAWithLongMappingBase {
    PDAWithBoundedLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoubleToLong op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (LongToDouble op) {
        return new PDAWithBoundedDoubleMapping
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (LongOp op) {
        return new PDAWithBoundedLongMapping
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelDoubleArrayWithMapping<U> withMapping
        (LongToObject<? extends U> op) {
        return new PDAWithBoundedMapping<U>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final double[] a = this.array;
        final DoubleToLong f = op;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(a[i]));
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        if (lo >= hi)
            return base;
        final double[] a = this.array;
        final DoubleToLong f = op;
        long r = f.op(a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(a[i]));
        return r;
    }

}

final class PDAWithBoundedFilteredLongMapping
    extends PDAWithLongMappingBase {
    final DoublePredicate selector;
    PDAWithBoundedFilteredLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoublePredicate selector,
         DoubleToLong op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    DoublePredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (LongToDouble op) {
        return new PDAWithBoundedFilteredDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (LongOp op) {
        return new PDAWithBoundedFilteredLongMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelDoubleArrayWithMapping<U> withMapping
        (LongToObject<? extends U> op) {
        return new PDAWithBoundedFilteredMapping<U>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final DoublePredicate s = selector;
        final DoubleToLong f = op;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            double x = a[i];
            if (s.op(x))
                procedure.op(f.op(x));
        }
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        boolean gotFirst = false;
        long r = base;
        final double[] a = this.array;
        final DoublePredicate s = selector;
        final DoubleToLong f = op;
        for (int i = lo; i < hi; ++i) {
            double t = a[i];
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

abstract class PDAWithIndexedLongMappingBase
    extends ParallelDoubleArrayWithLongMapping {
    final IntAndDoubleToLong op;
    PDAWithIndexedLongMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         IntAndDoubleToLong op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final long lget(int i) { return op.op(i, this.array[i]); }

    final void leafTransfer(int lo, int hi, long[] dest, int offset) {
        final IntAndDoubleToLong f = op;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(i, a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   long[] dest, int offset) {
        final double[] a = this.array;
        final IntAndDoubleToLong f = op;
        for (int i = loIdx; i < hiIdx; ++i) {
            int idx = indices[i];
            dest[offset++] = f.op(idx, a[idx]);
        }
    }

}

final class PDAWithBoundedIndexedLongMapping
    extends PDAWithIndexedLongMappingBase {
    PDAWithBoundedIndexedLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         IntAndDoubleToLong op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping(LongToDouble op) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping(LongOp op) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelDoubleArrayWithMapping< U> withMapping
        (LongToObject<? extends U> op) {
        return new PDAWithBoundedIndexedMapping<U>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PDAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PDAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PDAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final IntAndDoubleToLong f = op;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(i, a[i]));
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        if (lo >= hi)
            return base;
        final double[] a = this.array;
        final IntAndDoubleToLong f = op;
        long r = f.op(lo, a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(i, a[i]));
        return r;
    }
}

final class PDAWithBoundedFilteredIndexedLongMapping
    extends PDAWithIndexedLongMappingBase {
    final DoublePredicate selector;
    PDAWithBoundedFilteredIndexedLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array,
         DoublePredicate selector,
         IntAndDoubleToLong op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    DoublePredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelDoubleArrayWithDoubleMapping withMapping(LongToDouble op) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelDoubleArrayWithLongMapping withMapping(LongOp op) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelDoubleArrayWithMapping< U> withMapping
        (LongToObject<? extends U> op) {
        return new PDAWithBoundedFilteredIndexedMapping<U>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelDoubleArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PDAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, mapper));
    }

    public ParallelDoubleArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PDAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, mapper));
    }

    public ParallelDoubleArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PDAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final DoublePredicate s = selector;
        final double[] a = this.array;
        final IntAndDoubleToLong f = op;
        for (int i = lo; i < hi; ++i) {
            double x = a[i];
            if (s.op(x))
                procedure.op(f.op(i, x));
        }
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        final DoublePredicate s = selector;
        final IntAndDoubleToLong f = op;
        boolean gotFirst = false;
        long r = base;
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            double t = a[i];
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

