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
 * An array of longs supporting parallel operations.  This class
 * provides methods supporting the same operations as {@link
 * ParallelArray}, but specialized for scalar longs. It additionally
 * provides a few methods specific to numerical values.
 */
public class ParallelLongArray extends PLAWithBounds {
    // Same internals as ParallelArray, but specialized for longs
    AsList listView;

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
     * Constructor for use by subclasses to create a new ParallelLongArray
     * using the given executor, and initially using the supplied
     * array, with effective size bound by the given limit. This
     * constructor is designed to enable extensions via
     * subclassing. To create a ParallelLongArray, use {@link #create},
     * {@link #createEmpty}, {@link #createUsingHandoff} or {@link
     * #createFromCopy}.
     * @param executor the executor
     * @param array the array
     * @param limit the upper bound limit
     */
    protected ParallelLongArray(ForkJoinExecutor executor, long[] array,
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
    ParallelLongArray(ForkJoinExecutor executor, long[] array) {
        super(executor, 0, array.length, array);
    }

    /**
     * Creates a new ParallelLongArray using the given executor and
     * an array of the given size
     * @param size the array size
     * @param executor the executor
     */
    public static ParallelLongArray create
        (int size, ForkJoinExecutor executor) {
        long[] array = new long[size];
        return new ParallelLongArray(executor, array, size);
    }

    /**
     * Creates a new ParallelLongArray initially using the given array and
     * executor. In general, the handed off array should not be used
     * for other purposes once constructing this ParallelLongArray.  The
     * given array may be internally replaced by another array in the
     * course of methods that add or remove elements.
     * @param handoff the array
     * @param executor the executor
     */
    public static ParallelLongArray createUsingHandoff
        (long[] handoff, ForkJoinExecutor executor) {
        return new ParallelLongArray(executor, handoff, handoff.length);
    }

    /**
     * Creates a new ParallelLongArray using the given executor and
     * initially holding copies of the given
     * source elements.
     * @param source the source of initial elements
     * @param executor the executor
     */
    public static ParallelLongArray createFromCopy
        (long[] source, ForkJoinExecutor executor) {
        // For now, avoid copyOf so people can compile with Java5
        int size = source.length;
        long[] array = new long[size];
        System.arraycopy(source, 0, array, 0, size);
        return new ParallelLongArray(executor, array, size);
    }

    /**
     * Creates a new ParallelLongArray using an array of the given size,
     * initially holding copies of the given source truncated or
     * padded with zeros to obtain the specified length.
     * @param source the source of initial elements
     * @param size the array size
     * @param executor the executor
     */
    public static ParallelLongArray createFromCopy
        (int size, long[] source, ForkJoinExecutor executor) {
        // For now, avoid copyOf so people can compile with Java5
        long[] array = new long[size];
        System.arraycopy(source, 0, array, 0,
                         Math.min(source.length, size));
        return new ParallelLongArray(executor, array, size);
    }

    /**
     * Creates a new ParallelLongArray using the given executor and
     * an array of the given size, but with an initial effective size
     * of zero, enabling incremental insertion via {@link
     * ParallelLongArray#asList} operations.
     * @param size the array size
     * @param executor the executor
     */
    public static ParallelLongArray createEmpty
        (int size, ForkJoinExecutor executor) {
        long[] array = new long[size];
        return new ParallelLongArray(executor, array, 0);
    }

    /**
     * Summary statistics for a possibly bounded, filtered, and/or
     * mapped ParallelLongArray.
     */
    public static interface SummaryStatistics {
        /** Return the number of elements */
        public int size();
        /** Return the minimum element, or Long.MAX_VALUE if empty */
        public long min();
        /** Return the maximum element, or Long.MIN_VALUE if empty */
        public long max();
        /** Return the index of the minimum element, or -1 if empty */
        public int indexOfMin();
        /** Return the index of the maximum element, or -1 if empty */
        public int indexOfMax();
        /** Return the sum of all elements */
        public long sum();
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
    public void apply(LongProcedure procedure) {
        super.apply(procedure);
    }

    /**
     * Returns reduction of elements
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public long reduce(LongReducer reducer, long base) {
        return super.reduce(reducer, base);
    }

    /**
     * Returns a new ParallelLongArray holding all elements
     * @return a new ParallelLongArray holding all elements
     */
    public ParallelLongArray all() {
        return super.all();
    }

    /**
     * Replaces elements with the results of applying the given op
     * to their current values.
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray replaceWithMapping(LongOp  op) {
        super.replaceWithMapping(op);
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * op to their indices.
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray replaceWithMappedIndex(IntToLong op) {
        super.replaceWithMappedIndex(op);
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * mapping to each index and current element value
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray replaceWithMappedIndex(IntAndLongToLong op) {
        super.replaceWithMappedIndex(op);
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * generator. For example, to fill the array with uniform random
     * values, use
     * <tt>replaceWithGeneratedValue(Ops.longRandom())</tt>
     * @param generator the generator
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray replaceWithGeneratedValue(LongGenerator generator) {
        super.replaceWithGeneratedValue(generator);
        return this;
    }

    /**
     * Replaces elements with the given value.
     * @param value the value
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray replaceWithValue(long value) {
        super.replaceWithValue(value);
        return this;
    }

    /**
     * Replaces elements with results of applying
     * <tt>op(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray replaceWithMapping(BinaryLongOp combiner,
                                   ParallelLongArray other) {
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
    public ParallelLongArray replaceWithMapping(BinaryLongOp combiner, long[] other) {
        super.replaceWithMapping(combiner, other);
        return this;
    }

    /**
     * Returns the index of some element equal to given target, or -1
     * if not present
     * @param target the element to search for
     * @return the index or -1 if not present
     */
    public int indexOf(long target) {
        return super.indexOf(target);
    }

    /**
     * Assuming this array is sorted, returns the index of an element
     * equal to given target, or -1 if not present. If the array
     * is not sorted, the results are undefined.
     * @param target the element to search for
     * @return the index or -1 if not present
     */
    public int binarySearch(long target) {
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
    public int binarySearch(long target, LongComparator comparator) {
        return super.binarySearch(target, comparator);
    }

    /**
     * Returns summary statistics, using the given comparator
     * to locate minimum and maximum elements.
     * @param comparator the comparator to use for
     * locating minimum and maximum elements
     * @return the summary.
     */
    public ParallelLongArray.SummaryStatistics summary
        (LongComparator comparator) {
        return super.summary(comparator);
    }

    /**
     * Returns summary statistics, using natural comparator
     * @return the summary.
     */
    public ParallelLongArray.SummaryStatistics summary() {
        return super.summary();
    }

    /**
     * Returns the minimum element, or Long.MAX_VALUE if empty
     * @param comparator the comparator
     * @return minimum element, or Long.MAX_VALUE if empty
     */
    public long min(LongComparator comparator) {
        return super.min(comparator);
    }

    /**
     * Returns the minimum element, or Long.MAX_VALUE if empty,
     * @return minimum element, or Long.MAX_VALUE if empty
     */
    public long min() {
        return super.min();
    }

    /**
     * Returns the maximum element, or Long.MIN_VALUE if empty
     * @param comparator the comparator
     * @return maximum element, or Long.MIN_VALUE if empty
     */
    public long max(LongComparator comparator) {
        return super.max(comparator);
    }

    /**
     * Returns the maximum element, or Long.MIN_VALUE if empty
     * @return maximum element, or Long.MIN_VALUE if empty
     */
    public long max() {
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
    public ParallelLongArray cumulate(LongReducer reducer, long base) {
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
    public long precumulate(LongReducer reducer, long base) {
        return super.precumulate(reducer, base);
    }

    /**
     * Sorts the array. Unlike Arrays.sort, this sort does
     * not guarantee that elements with equal keys maintain their
     * relative position in the array.
     * @param comparator the comparator to use
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray sort(LongComparator comparator) {
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
    public ParallelLongArray sort() {
        super.sort();
        return this;
    }

    /**
     * Removes consecutive elements that are equal,
     * shifting others leftward, and possibly decreasing size.  This
     * method may be used after sorting to ensure that this
     * ParallelLongArray contains a set of unique elements.
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray removeConsecutiveDuplicates() {
        // Sequential implementation for now
        int k = 0;
        int n = upperBound;
        if (k < n) {
            long[] arr = this.array;
            long last = arr[k++];
            for (int i = k; i < n; ++i) {
                long x = arr[i];
                if (last != x)
                    arr[k++] = last = x;
            }
            removeSlotsAt(k, n);
        }
        return this;
    }

    /**
     * Returns a new ParallelLongArray containing only the unique
     * elements of this array (that is, without any duplicates).
     * @return the new ParallelLongArray
     */
    public ParallelLongArray allUniqueElements() {
        return super.allUniqueElements();
    }

    /**
     * Removes from the array all elements for which the given
     * selector holds.
     * @param selector the selector
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray removeAll(LongPredicate selector) {
        PLAWithBoundedFilter v = 
            new PLAWithBoundedFilter(ex, 0, upperBound, array, selector);
        PAS.FJRemoveAllDriver f = new PAS.FJRemoveAllDriver(v, 0, upperBound);
        ex.invoke(f);
        removeSlotsAt(f.offset, upperBound);
        return this;
    }

    /**
     * Returns the sum of elements
     * @return the sum of elements
     */
    public long sum() {
        return super.sum();
    }

    /**
     * Replaces each element with the running sum
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray cumulateSum() {
        super.cumulateSum();
        return this;
    }

    /**
     * Replaces each element with its prefix sum
     * @return the total sum
     */
    public long precumulateSum() {
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
    public ParallelLongArrayWithBounds withBounds(int firstIndex, int upperBound) {
        return super.withBounds(firstIndex, upperBound);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * only on the elements of the array for which the given selector
     * returns true
     * @param selector the selector
     * @return operation prefix
     */
    public ParallelLongArrayWithFilter withFilter(LongPredicate selector) {
        return super.withFilter(selector);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public <U> ParallelLongArrayWithMapping<U> withMapping
        (LongToObject<? extends U> op) {
        return super.withMapping(op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public ParallelLongArrayWithLongMapping withMapping(LongOp op) {
        return super.withMapping(op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public ParallelLongArrayWithDoubleMapping withMapping(LongToDouble op) {
        return super.withMapping(op);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
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
    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
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
    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
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
    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
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
    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
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
    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
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
    public <V> ParallelLongArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
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
    public ParallelLongArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
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
    public ParallelLongArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
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
    public <U> ParallelLongArrayWithMapping<U> withIndexedMapping
        (IntAndLongToObject<? extends U> mapper) {
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
    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
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
    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
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
    public Iterator<Long> iterator() {
        return new ParallelLongArrayIterator(array, upperBound);
    }

    static final class ParallelLongArrayIterator implements Iterator<Long> {
        int cursor;
        final long[] arr;
        final int hi;
        ParallelLongArrayIterator(long[] a, int limit) { arr = a; hi = limit; }
        public boolean hasNext() { return cursor < hi; }
        public Long next() {
            if (cursor >= hi)
                throw new NoSuchElementException();
            return Long.valueOf(arr[cursor++]);
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    // List support

    /**
     * Returns a view of this ParallelLongArray as a List. This List has
     * the same structural and performance characteristics as {@link
     * ArrayList}, and may be used to modify, replace or extend the
     * bounds of the array underlying this ParallelLongArray.  The methods
     * supported by this list view are <em>not</em> in general
     * implemented as parallel operations. This list is also not
     * itself thread-safe.  In particular, performing list updates
     * while other parallel operations are in progress has undefined
     * (and surely undesired) effects.
     * @return a list view
     */
    public List<Long> asList() {
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
    public long[] getArray() { return array; }

    /**
     * Returns the element of the array at the given index
     * @param i the index
     * @return the element of the array at the given index
     */
    public long get(int i) { return array[i]; }

    /**
     * Sets the element of the array at the given index to the given value
     * @param i the index
     * @param x the value
     */
    public void set(int i, long x) { array[i] = x; }

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
    public ParallelLongArray addAll(long[] other) {
        int csize = other.length;
        int end = upperBound;
        insertSlotsAt(end, csize);
        System.arraycopy(other, 0, array, end, csize);
        return this;
    }

    /**
     * Equivalent to <tt>asList().addAll</tt> but specialized for
     * ParallelLongArray arguments and likely to be more efficient.
     * @param other the elements to add
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArray addAll(ParallelLongArray other) {
        int csize = other.size();
        int end = upperBound;
        insertSlotsAt(end, csize);
        if (!other.hasMap()) 
            System.arraycopy(other.array, 0, array, end, csize);
        else {
            int k = end;
            for (int i = other.firstIndex; i < other.upperBound; ++i)
                array[k++] = other.lget(i);
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

    final void replaceElementsWith(long[] a) {
        System.arraycopy(a, 0, array, 0, a.length);
        upperBound = a.length;
    }

    final void resizeArray(int newCap) {
        int cap = array.length;
        if (newCap > cap) {
            long[] a = new long[newCap];
            System.arraycopy(array, 0, a, 0, cap);
            array = a;
        }
    }

    final void insertElementAt(int index, long e) {
        int hi = upperBound++;
        if (hi >= array.length)
            resizeArray((hi * 3)/2 + 1);
        if (hi > index)
            System.arraycopy(array, index, array, index+1, hi - index);
        array[index] = e;
    }

    final void appendElement(long e) {
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

    final int seqIndexOf(long target) {
        long[] arr = array;
        int fence = upperBound;
        for (int i = 0; i < fence; i++)
            if (target == arr[i])
                return i;
        return -1;
    }

    final int seqLastIndexOf(long target) {
        long[] arr = array;
        for (int i = upperBound - 1; i >= 0; i--)
            if (target == arr[i])
                return i;
        return -1;
    }

    final class ListIter implements ListIterator<Long> {
        int cursor;
        int lastRet;
        long[] arr; // cache array and bound
        int hi;
        ListIter(int lo) {
            this.cursor = lo;
            this.lastRet = -1;
            this.arr = ParallelLongArray.this.array;
            this.hi = ParallelLongArray.this.upperBound;
        }

        public boolean hasNext() {
            return cursor < hi;
        }

        public Long next() {
            int i = cursor;
            if (i < 0 || i >= hi)
                throw new NoSuchElementException();
            long next = arr[i];
            lastRet = i;
            cursor = i + 1;
            return Long.valueOf(next);
        }

        public void remove() {
            int k = lastRet;
            if (k < 0)
                throw new IllegalStateException();
            ParallelLongArray.this.removeSlotAt(k);
            hi = ParallelLongArray.this.upperBound;
            if (lastRet < cursor)
                cursor--;
            lastRet = -1;
        }

        public boolean hasPrevious() {
            return cursor > 0;
        }

        public Long previous() {
            int i = cursor - 1;
            if (i < 0 || i >= hi)
                throw new NoSuchElementException();
            long previous = arr[i];
            lastRet = cursor = i;
            return Long.valueOf(previous);
        }

        public int nextIndex() {
            return cursor;
        }

        public int previousIndex() {
            return cursor - 1;
        }

        public void set(Long e) {
            int i = lastRet;
            if (i < 0 || i >= hi)
                throw new NoSuchElementException();
            arr[i] = e.longValue();
        }

        public void add(Long e) {
            int i = cursor;
            ParallelLongArray.this.insertElementAt(i, e.longValue());
            arr = ParallelLongArray.this.array;
            hi = ParallelLongArray.this.upperBound;
            lastRet = -1;
            cursor = i + 1;
        }
    }

    final class AsList extends AbstractList<Long> implements RandomAccess {
        public Long get(int i) {
            if (i >= upperBound)
                throw new IndexOutOfBoundsException();
            return Long.valueOf(array[i]);
        }

        public Long set(int i, Long x) {
            if (i >= upperBound)
                throw new IndexOutOfBoundsException();
            long[] arr = array;
            Long t = Long.valueOf(arr[i]);
            arr[i] = x.longValue();
            return t;
        }

        public boolean isEmpty() {
            return upperBound == 0;
        }

        public int size() {
            return upperBound;
        }

        public Iterator<Long> iterator() {
            return new ListIter(0);
        }

        public ListIterator<Long> listIterator() {
            return new ListIter(0);
        }

        public ListIterator<Long> listIterator(int index) {
            if (index < 0 || index > upperBound)
                throw new IndexOutOfBoundsException();
            return new ListIter(index);
        }

        public boolean add(Long e) {
            appendElement(e.longValue());
            return true;
        }

        public void add(int index, Long e) {
            if (index < 0 || index > upperBound)
                throw new IndexOutOfBoundsException();
            insertElementAt(index, e.longValue());
        }

        public boolean addAll(Collection<? extends Long> c) {
            int csize = c.size();
            if (csize == 0)
                return false;
            int hi = upperBound;
            setLimit(hi + csize);
            long[] arr = array;
            for (Long e : c)
                arr[hi++] = e.longValue();
            return true;
        }

        public boolean addAll(int index, Collection<? extends Long> c) {
            if (index < 0 || index > upperBound)
                throw new IndexOutOfBoundsException();
            int csize = c.size();
            if (csize == 0)
                return false;
            insertSlotsAt(index, csize);
            long[] arr = array;
            for (Long e : c)
                arr[index++] = e.longValue();
            return true;
        }

        public void clear() {
            upperBound = 0;
        }

        public boolean remove(Object o) {
            if (!(o instanceof Long))
                return false;
            int idx = seqIndexOf(((Long)o).longValue());
            if (idx < 0)
                return false;
            removeSlotAt(idx);
            return true;
        }

        public Long remove(int index) {
            Long oldValue = get(index);
            removeSlotAt(index);
            return oldValue;
        }

        public void removeRange(int fromIndex, int toIndex) {
            removeSlotsAt(fromIndex, toIndex);
        }

        public boolean contains(Object o) {
            if (!(o instanceof Long))
                return false;
            return seqIndexOf(((Long)o).longValue()) >= 0;
        }

        public int indexOf(Object o) {
            if (!(o instanceof Long))
                return -1;
            return seqIndexOf(((Long)o).longValue());
        }

        public int lastIndexOf(Object o) {
            if (!(o instanceof Long))
                return -1;
            return seqLastIndexOf(((Long)o).longValue());
        }
    }
}

abstract class PLAWithMappingBase<U> extends ParallelLongArrayWithMapping<U> {
    final LongToObject<? extends U> op;
    PLAWithMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final Object oget(int i) { return op.op(this.array[i]); }

    final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
        final long[] a = this.array;
        final LongToObject f = op;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   Object[] dest, int offset) {
        final long[] a = this.array;
        final LongToObject f = op;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = f.op(a[indices[i]]);
    }
}

final class PLAWithBoundedMapping<U> extends PLAWithMappingBase<U> {
    PLAWithBoundedMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping(Op<? super U, ? extends V> op) {
        return new PLAWithBoundedMapping<V>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public ParallelLongArrayWithLongMapping withMapping(ObjectToLong<? super U> op) {
        return new PLAWithBoundedLongMapping
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public ParallelLongArrayWithDoubleMapping withMapping(ObjectToDouble<? super U> op) {
        return new PLAWithBoundedDoubleMapping
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final long[] a = this.array;
        final LongToObject f = op;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(a[i]));
    }

    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        if (lo >= hi)
            return base;
        final long[] a = this.array;
        final LongToObject f = op;
        Object r = f.op(a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(a[i]));
        return r;
    }

}

final class PLAWithBoundedFilteredMapping<U>
    extends PLAWithMappingBase<U> {
    final LongPredicate selector;
    PLAWithBoundedFilteredMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongPredicate selector,
         LongToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    LongPredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (Op<? super U, ? extends V> op) {
        return new PLAWithBoundedFilteredMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (ObjectToLong<? super U> op) {
        return new PLAWithBoundedFilteredLongMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectToDouble<? super U> op) {
        return new PLAWithBoundedFilteredDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final LongPredicate s = selector;
        final LongToObject f = op;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            long x = a[i];
            if (s.op(x))
                procedure.op(f.op(x));
        }
    }

    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        final LongPredicate s = selector;
        final LongToObject f = op;
        boolean gotFirst = false;
        Object r = base;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            long x = a[i];
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

abstract class PLAWithIndexedMappingBase<U> extends ParallelLongArrayWithMapping<U> {
    final IntAndLongToObject<? extends U> op;
    PLAWithIndexedMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         IntAndLongToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final Object oget(int i) { return op.op(i, this.array[i]); }

    final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
        final IntAndLongToObject f = op;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(i, a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   Object[] dest, int offset) {
        final long[] a = this.array;
        final IntAndLongToObject f = op;
        for (int i = loIdx; i < hiIdx; ++i) {
            int idx = indices[i];
            dest[offset++] = f.op(idx, a[idx]);
        }
    }
}

final class PLAWithBoundedIndexedMapping<U>
    extends PLAWithIndexedMappingBase<U> {
    PLAWithBoundedIndexedMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         IntAndLongToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public <V> ParallelLongArrayWithMapping< V> withMapping
        (Op<? super U, ? extends V> op) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectToDouble<? super U> op) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (ObjectToLong<? super U> op) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final IntAndLongToObject f = op;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(i, a[i]));
    }

    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        if (lo >= hi)
            return base;
        final long[] a = this.array;
        final IntAndLongToObject f = op;
        Object r = f.op(lo, a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(i, a[i]));
        return r;
    }

}

final class PLAWithBoundedFilteredIndexedMapping<U>
    extends PLAWithIndexedMappingBase<U> {
    final LongPredicate selector;

    PLAWithBoundedFilteredIndexedMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongPredicate selector,
         IntAndLongToObject<? extends U> op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    LongPredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public <V> ParallelLongArrayWithMapping< V> withMapping
        (Op<? super U, ? extends V> op) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelLongArrayWithDoubleMapping withMapping(ObjectToDouble<? super U> op) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelLongArrayWithLongMapping withMapping(ObjectToLong<? super U> op) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (BinaryOp<? super U, ? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (ObjectAndDoubleToObject<? super U, ? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (ObjectAndLongToObject<? super U, ? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndObjectToDouble<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndDoubleToDouble<? super U> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (ObjectAndLongToDouble<? super U> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (ObjectAndObjectToLong<? super U, ? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (ObjectAndDoubleToLong<? super U> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (ObjectAndLongToLong<? super U> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndObjectToObject<? super U, ? extends V> mapper) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndObjectToDouble<? super U> mapper) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndObjectToLong<? super U> mapper) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, Procedure  procedure) {
        final LongPredicate s = selector;
        final long[] a = this.array;
        final IntAndLongToObject f = op;
        for (int i = lo; i < hi; ++i) {
            long x = a[i];
            if (s.op(x))
                procedure.op(f.op(i, x));
        }
    }
    Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
        final LongPredicate s = selector;
        final long[] a = this.array;
        final IntAndLongToObject f = op;
        boolean gotFirst = false;
        Object r = base;
        for (int i = lo; i < hi; ++i) {
            long x = a[i];
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

class PLAWithBounds extends ParallelLongArrayWithBounds {
    PLAWithBounds
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array) {
        super(ex, firstIndex, upperBound, array);
    }

    PLAWithBounds(ParallelLongArray pa) {
        super(pa.ex, 0, pa.upperBound, pa.array);
    }

    final long lget(int i) { return this.array[i]; }

    public ParallelLongArrayWithBounds withBounds(int firstIndex, int upperBound) {
        if (firstIndex > upperBound)
            throw new IllegalArgumentException
                ("firstIndex(" + firstIndex +
                 ") > upperBound(" + upperBound+")");
        if (firstIndex < 0)
            throw new ArrayIndexOutOfBoundsException(firstIndex);
        if (upperBound - firstIndex > this.upperBound - this.firstIndex)
            throw new ArrayIndexOutOfBoundsException(upperBound);
        return new PLAWithBounds(ex,
                              this.firstIndex + firstIndex,
                              this.firstIndex + upperBound,
                              array);
    }

    public ParallelLongArrayWithFilter withFilter(LongPredicate selector) {
        return new PLAWithBoundedFilter
            (ex, firstIndex, upperBound, array, selector);
    }

    public <U> ParallelLongArrayWithMapping<U> withMapping
        (LongToObject<? extends U> op) {
        return new PLAWithBoundedMapping<U>
            (ex, firstIndex, upperBound, array, op);
    }

    public ParallelLongArrayWithLongMapping withMapping(LongOp op) {
        return new PLAWithBoundedLongMapping
            (ex, firstIndex, upperBound, array, op);
    }

    public ParallelLongArrayWithDoubleMapping withMapping(LongToDouble op) {
        return new PLAWithBoundedDoubleMapping
            (ex, firstIndex, upperBound, array, op);
    }

    public ParallelLongArrayWithFilter orFilter(LongPredicate selector) {
        return new PLAWithBoundedFilter
            (ex, firstIndex, upperBound, array, selector);
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array, mapper);
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, mapper);
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,  mapper);
    }

    public ParallelLongArray allUniqueElements() {
        PAS.LUniquifierTable tab = new PAS.LUniquifierTable
            (upperBound - firstIndex, this.array, null);
        PAS.FJUniquifier f = new PAS.FJUniquifier
            (this, firstIndex, upperBound, null, tab);
        ex.invoke(f);
        long[] res = tab.uniqueElements(f.count);
        return new ParallelLongArray(ex, res);
    }

    public int indexOf(long target) {
        AtomicInteger result = new AtomicInteger(-1);
        PAS.FJLIndexOf f = new PAS.FJLIndexOf
            (this, firstIndex, upperBound, null, result, target);
        ex.invoke(f);
        return result.get();
    }

    public int binarySearch(long target) {
        final long[] a = this.array;
        int lo = firstIndex;
        int hi = upperBound - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long m = a[mid];
            if (target == m)
                return mid;
            else if (target < m)
                hi = mid - 1;
            else
                lo = mid + 1;
        }
        return -1;
    }

    public int binarySearch(long target, LongComparator comparator) {
        final long[] a = this.array;
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


    public ParallelLongArrayWithBounds cumulate(LongReducer reducer, long base) {
        PAS.FJLCumulateOp op = new PAS.FJLCumulateOp(this, reducer, base);
        PAS.FJLScan r = new PAS.FJLScan(null, op, firstIndex, upperBound);
        ex.invoke(r);
        return this;
    }

    public ParallelLongArrayWithBounds cumulateSum() {
        PAS.FJLCumulatePlusOp op = new PAS.FJLCumulatePlusOp(this);
        PAS.FJLScan r = new PAS.FJLScan(null, op, firstIndex, upperBound);
        ex.invoke(r);
        return this;
    }

    public long precumulate(LongReducer reducer, long base) {
        PAS.FJLPrecumulateOp op = new PAS.FJLPrecumulateOp
            (this, reducer, base);
        PAS.FJLScan r = new PAS.FJLScan(null, op, firstIndex, upperBound);
        ex.invoke(r);
        return r.out;
    }

    public long precumulateSum() {
        PAS.FJLPrecumulatePlusOp op = new PAS.FJLPrecumulatePlusOp(this);
        PAS.FJLScan r = new PAS.FJLScan(null, op, firstIndex, upperBound);
        ex.invoke(r);
        return r.out;
    }

    public ParallelLongArrayWithBounds sort(LongComparator cmp) {
        ex.invoke(new PAS.FJLSorter
                  (cmp, this.array, new long[upperBound],
                   firstIndex, upperBound - firstIndex, getThreshold()));
        return this;
    }

    public ParallelLongArrayWithBounds sort() {
        ex.invoke(new PAS.FJLCSorter
                  (this.array, new long[upperBound],
                   firstIndex, upperBound - firstIndex, getThreshold()));
        return this;
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(a[i]);
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        if (lo >= hi)
            return base;
        final long[] a = this.array;
        long r = a[lo];
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, a[i]);
        return r;
    }

    void leafCombine(int lo, int hi, long[] other, int otherOffset,
                     long[] dest, BinaryLongOp combiner) {
        final long[] a = this.array;
        int k = lo - firstIndex;
        for (int i = lo; i < hi; ++i) {
            dest[k] = combiner.op(a[i], other[i + otherOffset]);
            ++k;
        }
    }
}

final class PLAWithBoundedFilter extends ParallelLongArrayWithFilter {
    final LongPredicate selector;
    PLAWithBoundedFilter
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongPredicate selector) {
        super(ex, firstIndex, upperBound, array);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    LongPredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }
    long lget(int i) { return this.array[i]; }

    public ParallelLongArrayWithFilter withFilter(LongPredicate selector) {
        return new PLAWithBoundedFilter
            (ex, firstIndex, upperBound, array,
             Ops.andPredicate(this.selector, selector));
    }

    public ParallelLongArrayWithFilter orFilter(LongPredicate selector) {
        return new PLAWithBoundedFilter
            (ex, firstIndex, upperBound, array,
             Ops.orPredicate(this.selector, selector));
    }

    public <U> ParallelLongArrayWithMapping<U> withMapping
        (LongToObject<? extends U> op) {
        return new PLAWithBoundedFilteredMapping<U>
            (ex, firstIndex, upperBound, array, selector, op);
    }

    public ParallelLongArrayWithLongMapping withMapping
        (LongOp op) {
        return new PLAWithBoundedFilteredLongMapping
            (ex, firstIndex, upperBound, array, selector, op);
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongToDouble op) {
        return new PLAWithBoundedFilteredDoubleMapping
            (ex, firstIndex, upperBound, array, selector, op);
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.indexedMapper(combiner, other, firstIndex));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector, mapper);
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector, mapper);
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector, mapper);
    }

    public ParallelLongArray allUniqueElements() {
        PAS.LUniquifierTable tab = new PAS.LUniquifierTable
            (upperBound - firstIndex, this.array, selector);
        PAS.FJUniquifier f = new PAS.FJUniquifier
            (this, firstIndex, upperBound, null, tab);
        ex.invoke(f);
        long[] res = tab.uniqueElements(f.count);
        return new ParallelLongArray(ex, res);
    }

    void leafApply(int lo, int hi, LongProcedure  procedure) {
        final LongPredicate s = selector;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            long x = a[i];
            if (s.op(x))
                procedure.op(x);
        }
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        final LongPredicate s = selector;
        boolean gotFirst = false;
        long r = base;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            long x = a[i];
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

abstract class PLAWithLongMappingBase extends ParallelLongArrayWithLongMapping {
    final LongOp op;
    PLAWithLongMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array, 
         LongOp op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final long lget(int i) { return op.op(this.array[i]); }

    final void leafTransfer(int lo, int hi, long[] dest, int offset) {
        final long[] a = this.array;
        final LongOp f = op;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   long[] dest, int offset) {
        final long[] a = this.array;
        final LongOp f = op;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = f.op(a[indices[i]]);
    }

}

final class PLAWithBoundedLongMapping extends PLAWithLongMappingBase {
    PLAWithBoundedLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array, 
         LongOp op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelLongArrayWithLongMapping withMapping(LongOp op) {
        return new PLAWithBoundedLongMapping
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public ParallelLongArrayWithDoubleMapping withMapping(LongToDouble op) {
        return new PLAWithBoundedDoubleMapping
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelLongArrayWithMapping<U> withMapping
        (LongToObject<? extends U> op) {
        return new PLAWithBoundedMapping<U>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final LongOp f = op;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(a[i]));
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        if (lo >= hi)
            return base;
        final long[] a = this.array;
        final LongOp f = op;
        long r = f.op(a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(a[i]));
        return r;
    }
}

final class PLAWithBoundedFilteredLongMapping
    extends PLAWithLongMappingBase {
    final LongPredicate selector;
    PLAWithBoundedFilteredLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongPredicate selector, LongOp op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    LongPredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelLongArrayWithLongMapping withMapping(LongOp op) {
        return new PLAWithBoundedFilteredLongMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelLongArrayWithDoubleMapping withMapping(LongToDouble op) {
        return new PLAWithBoundedFilteredDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelLongArrayWithMapping<U> withMapping
        (LongToObject<? extends U> op) {
        return new PLAWithBoundedFilteredMapping<U>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final LongPredicate s = selector;
        final LongOp f = op;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            long x = a[i];
            if (s.op(x))
                procedure.op(f.op(x));
        }
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        final LongPredicate s = selector;
        final LongOp f = op;
        boolean gotFirst = false;
        long r = base;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            long t = a[i];
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

abstract class PLAWithIndexedLongMappingBase
    extends ParallelLongArrayWithLongMapping {
    final IntAndLongToLong op;
    PLAWithIndexedLongMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         IntAndLongToLong op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final long lget(int i) { return op.op(i, this.array[i]); }

    final void leafTransfer(int lo, int hi, long[] dest, int offset) {
        final IntAndLongToLong f = op;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(i, a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   long[] dest, int offset) {
        final long[] a = this.array;
        final IntAndLongToLong f = op;
        for (int i = loIdx; i < hiIdx; ++i) {
            int idx = indices[i];
            dest[offset++] = f.op(idx, a[idx]);
        }
    }

}

final class PLAWithBoundedIndexedLongMapping
    extends PLAWithIndexedLongMappingBase {
    PLAWithBoundedIndexedLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         IntAndLongToLong op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelLongArrayWithDoubleMapping withMapping(LongToDouble op) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelLongArrayWithLongMapping withMapping(LongOp op) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelLongArrayWithMapping< U> withMapping
        (LongToObject<? extends U> op) {
        return new PLAWithBoundedIndexedMapping<U>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final IntAndLongToLong f = op;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(i, a[i]));
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        if (lo >= hi)
            return base;
        final long[] a = this.array;
        final IntAndLongToLong f = op;
        long r = f.op(lo, a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(i, a[i]));
        return r;
    }

}

final class PLAWithBoundedFilteredIndexedLongMapping
    extends PLAWithIndexedLongMappingBase {
    final LongPredicate selector;
    PLAWithBoundedFilteredIndexedLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongPredicate selector,
         IntAndLongToLong op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    LongPredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelLongArrayWithDoubleMapping withMapping(LongToDouble op) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelLongArrayWithLongMapping withMapping(LongOp op) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelLongArrayWithMapping< U> withMapping
        (LongToObject<? extends U> op) {
        return new PLAWithBoundedFilteredIndexedMapping<U>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (BinaryLongOp combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndLongToObject<? extends V> mapper) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndLongToDouble mapper) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndLongToLong mapper) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, LongProcedure procedure) {
        final LongPredicate s = selector;
        final long[] a = this.array;
        final IntAndLongToLong f = op;
        for (int i = lo; i < hi; ++i) {
            long x = a[i];
            if (s.op(x))
                procedure.op(f.op(i, x));
        }
    }

    long leafReduce(int lo, int hi, LongReducer reducer, long base) {
        final LongPredicate s = selector;
        final IntAndLongToLong f = op;
        boolean gotFirst = false;
        long r = base;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            long t = a[i];
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

abstract class PLAWithDoubleMappingBase extends ParallelLongArrayWithDoubleMapping {
    final LongToDouble op;
    PLAWithDoubleMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongToDouble op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final double dget(int i) { return op.op(this.array[i]); }

    final void leafTransfer(int lo, int hi, double[] dest, int offset) {
        final long[] a = this.array;
        final LongToDouble f = op;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   double[] dest, int offset) {
        final long[] a = this.array;
        final LongToDouble f = op;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = f.op(a[indices[i]]);
    }

}

final class PLAWithBoundedDoubleMapping
    extends PLAWithDoubleMappingBase {
    PLAWithBoundedDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongToDouble op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelLongArrayWithLongMapping withMapping(DoubleToLong op) {
        return new PLAWithBoundedLongMapping
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public ParallelLongArrayWithDoubleMapping withMapping(DoubleOp op) {
        return new PLAWithBoundedDoubleMapping
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelLongArrayWithMapping<U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PLAWithBoundedMapping<U>
            (ex, firstIndex, upperBound, array,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }
    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final LongToDouble f = op;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(a[i]));
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        if (lo >= hi)
            return base;
        final long[] a = this.array;
        final LongToDouble f = op;
        double r = f.op(a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(a[i]));
        return r;
    }

}

final class PLAWithBoundedFilteredDoubleMapping
    extends PLAWithDoubleMappingBase {
    final LongPredicate selector;
    PLAWithBoundedFilteredDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongPredicate selector, LongToDouble op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    LongPredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelLongArrayWithLongMapping withMapping(DoubleToLong op) {
        return new PLAWithBoundedFilteredLongMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public ParallelLongArrayWithDoubleMapping withMapping(DoubleOp op) {
        return new PLAWithBoundedFilteredDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <U> ParallelLongArrayWithMapping<U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PLAWithBoundedFilteredMapping<U>
            (ex, firstIndex, upperBound, array, selector,
             Ops.compoundOp(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final LongPredicate s = selector;
        final long[] a = this.array;
        final LongToDouble f = op;
        for (int i = lo; i < hi; ++i) {
            long x = a[i];
            if (s.op(x))
                procedure.op(f.op(x));
        }
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        final LongPredicate s = selector;
        final LongToDouble f = op;
        boolean gotFirst = false;
        double r = base;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            long t = a[i];
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

abstract class PLAWithIndexedDoubleMappingBase
    extends ParallelLongArrayWithDoubleMapping {
    final IntAndLongToDouble op;
    PLAWithIndexedDoubleMappingBase
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         IntAndLongToDouble op) {
        super(ex, firstIndex, upperBound, array);
        this.op = op;
    }

    final boolean hasMap() { return true; }
    final double dget(int i) { return op.op(i, this.array[i]); }

    final void leafTransfer(int lo, int hi, double[] dest, int offset) {
        final IntAndLongToDouble f = op;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = f.op(i, a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   double[] dest, int offset) {
        final long[] a = this.array;
        final IntAndLongToDouble f = op;
        for (int i = loIdx; i < hiIdx; ++i) {
            int idx = indices[i];
            dest[offset++] = f.op(idx, a[idx]);
        }
    }

}

final class PLAWithBoundedIndexedDoubleMapping
    extends PLAWithIndexedDoubleMappingBase {
    PLAWithBoundedIndexedDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         IntAndLongToDouble op) {
        super(ex, firstIndex, upperBound, array, op);
    }

    public ParallelLongArrayWithDoubleMapping withMapping(DoubleOp op) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelLongArrayWithLongMapping withMapping(DoubleToLong op) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelLongArrayWithMapping< U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PLAWithBoundedIndexedMapping<U>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedMapping<W>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PLAWithBoundedIndexedMapping<V>
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PLAWithBoundedIndexedDoubleMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PLAWithBoundedIndexedLongMapping
            (ex, firstIndex, upperBound, array,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final IntAndLongToDouble f = op;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i)
            procedure.op(f.op(i, a[i]));
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        if (lo >= hi)
            return base;
        final long[] a = this.array;
        final IntAndLongToDouble f = op;
        double r = f.op(lo, a[lo]);
        for (int i = lo+1; i < hi; ++i)
            r = reducer.op(r, f.op(i, a[i]));
        return r;
    }

}

final class PLAWithBoundedFilteredIndexedDoubleMapping
    extends PLAWithIndexedDoubleMappingBase {
    final LongPredicate selector;
    PLAWithBoundedFilteredIndexedDoubleMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array,
         LongPredicate selector,
         IntAndLongToDouble op) {
        super(ex, firstIndex, upperBound, array, op);
        this.selector = selector;
    }

    boolean hasFilter() { return true; }
    LongPredicate getPredicate() { return selector; }
    boolean isSelected(int i) { return selector.op(this.array[i]); }

    public ParallelLongArrayWithDoubleMapping withMapping(DoubleOp op) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public ParallelLongArrayWithLongMapping withMapping(DoubleToLong op) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <U> ParallelLongArrayWithMapping< U> withMapping
        (DoubleToObject<? extends U> op) {
        return new PLAWithBoundedFilteredIndexedMapping<U>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper(this.op, op));
    }

    public <V,W> ParallelLongArrayWithMapping<W> withMapping
        (DoubleAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedMapping<W>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (DoubleAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withMapping
        (DoubleAndLongToObject<? extends V> combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithDoubleMapping withMapping
        (DoubleAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (BinaryDoubleOp combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithDoubleMapping withMapping
        (DoubleAndLongToDouble combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithLongMapping withMapping
        (DoubleAndObjectToLong<? super V> combiner,
         ParallelArray<V> other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (DoubleAndDoubleToLong combiner,
         ParallelDoubleArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public ParallelLongArrayWithLongMapping withMapping
        (DoubleAndLongToLong combiner,
         ParallelLongArray other) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op,
              PAS.indexedMapper(combiner, other, firstIndex)));
    }

    public <V> ParallelLongArrayWithMapping<V> withIndexedMapping
        (IntAndDoubleToObject<? extends V> mapper) {
        return new PLAWithBoundedFilteredIndexedMapping<V>
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithDoubleMapping withIndexedMapping
        (IntAndDoubleToDouble mapper) {
        return new PLAWithBoundedFilteredIndexedDoubleMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    public ParallelLongArrayWithLongMapping withIndexedMapping
        (IntAndDoubleToLong mapper) {
        return new PLAWithBoundedFilteredIndexedLongMapping
            (ex, firstIndex, upperBound, array, selector,
             PAS.compoundIndexedMapper
             (this.op, mapper));
    }

    void leafApply(int lo, int hi, DoubleProcedure procedure) {
        final LongPredicate s = selector;
        final long[] a = this.array;
        final IntAndLongToDouble f = op;
        for (int i = lo; i < hi; ++i) {
            long x = a[i];
            if (s.op(x))
                procedure.op(f.op(i, x));
        }
    }

    double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
        final LongPredicate s = selector;
        final IntAndLongToDouble f = op;
        boolean gotFirst = false;
        double r = base;
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i) {
            long t = a[i];
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

