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
public class ParallelDoubleArray {
    // Same internals as ParallelArray, but specialized for doubles
    double[] array;
    final ForkJoinExecutor ex;
    int limit;
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
    ParallelDoubleArray(ForkJoinExecutor executor, double[] array) {
        this.ex = executor;
        this.array = array;
        this.limit = array.length;
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
        new WithBounds(this).apply(procedure);
    }

    /**
     * Returns reduction of elements
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public double reduce(DoubleReducer reducer, double base) {
        return new WithBounds(this).reduce(reducer, base);
    }

    /**
     * Returns a new ParallelDoubleArray holding all elements
     * @return a new ParallelDoubleArray holding all elements
     */
    public ParallelDoubleArray all() {
        return new WithBounds(this).all();
    }

    /**
     * Returns a ParallelDoubleArray containing results of
     * applying <tt>combine(thisElement, otherElement)</tt>
     * for each element.
     * @param other the other array
     * @param combiner the combiner
     * @return the array of mappings
     * @throws ArrayIndexOutOfBoundsException if other array is
     * shorter than this array.
     */
    public ParallelDoubleArray combine
        (double[] other,
         DoubleReducer combiner) {
        return new WithBounds(this).combine(other, combiner);
    }

    /**
     * Returns a ParallelDoubleArray containing results of
     * applying <tt>combine(thisElement, otherElement)</tt>
     * for each element.
     * @param other the other array
     * @param combiner the combiner
     * @return the array of mappings
     * @throws ArrayIndexOutOfBoundsException if other array is not
     * the same length as this array.
     */
    public <U,V> ParallelDoubleArray combine
        (ParallelDoubleArray other,
         DoubleReducer combiner) {
        return new WithBounds(this).combine(other, combiner);
    }

    /**
     * Returns a ParallelDoubleArray containing results of
     * applying <tt>combine(thisElement, otherElement)</tt>
     * for each element.
     * @param other the other array segment
     * @param combiner the combiner
     * @return the array of mappings
     * @throws ArrayIndexOutOfBoundsException if other segment is
     * shorter than this array.
     */
    public <U,V> ParallelDoubleArray combine
        (ParallelDoubleArray.WithBounds other,
         DoubleReducer combiner) {
        return new WithBounds(this).combine(other, combiner);
    }

    /**
     * Replaces elements with the results of applying the given mapper
     * to their current values.
     * @param mapper the mapper
     */
    public void replaceWithTransform(DoubleMapper  mapper) {
        new WithBounds(this).replaceWithTransform(mapper);
    }

    /**
     * Replaces elements with the results of applying the given
     * mapper to their indices.
     * @param mapper the mapper
     */
    public void replaceWithMappedIndex(MapperFromIntToDouble mapper) {
        new WithBounds(this).replaceWithMappedIndex(mapper);
    }

    /**
     * Replaces elements with the results of applying the given
     * generator. For example, to fill the array with uniform random
     * values, use
     * <tt>replaceWithGeneratedValue(Ops.doubleRandom())</tt>
     * @param generator the generator
     */
    public void replaceWithGeneratedValue(DoubleGenerator generator) {
        new WithBounds(this).replaceWithGeneratedValue(generator);
    }

    /**
     * Replaces elements with the given value.
     * @param value the value
     */
    public void replaceWithValue(double value) {
        new WithBounds(this).replaceWithValue(value);
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
        (ParallelDoubleArray other, DoubleReducer combiner) {
        new WithBounds(this).replaceWithCombination(other.array, combiner);
    }

    /**
     * Replaces elements with results of applying
     * <tt>combine(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @throws ArrayIndexOutOfBoundsException if other array has
     * fewer elements than this array.
     */
    public void replaceWithCombination(double[] other, DoubleReducer combiner) {
        new WithBounds(this).replaceWithCombination(other, combiner);
    }

    /**
     * Replaces elements with results of applying
     * <tt>combine(thisElement, otherElement)</tt>
     * @param other the other array segment
     * @param combiner the combiner
     * @throws ArrayIndexOutOfBoundsException if other segment has
     * fewer elements.than this array,
     */
    public void replaceWithCombination
        (ParallelDoubleArray.WithBounds other,
         DoubleReducer combiner) {
        new WithBounds(this).replaceWithCombination(other, combiner);
    }

    /**
     * Returns the index of some element equal to given target, or -1
     * if not present
     * @param target the element to search for
     * @return the index or -1 if not present
     */
    public int indexOf(double target) {
        return new WithBounds(this).indexOf(target);
    }

    /**
     * Assuming this array is sorted, returns the index of an element
     * equal to given target, or -1 if not present. If the array
     * is not sorted, the results are undefined.
     * @param target the element to search for
     * @return the index or -1 if not present
     */
    public int binarySearch(double target) {
        int lo = 0;
        int hi = limit - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            double m = array[mid];
            if (target == m)
                return mid;
            else if (target < m)
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
    public int binarySearch(double target, DoubleComparator comparator) {
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
    public ParallelDoubleArray.SummaryStatistics summary
        (DoubleComparator comparator) {
        return new WithBounds(this).summary(comparator);
    }

    /**
     * Returns summary statistics, using natural comparator
     * @return the summary.
     */
    public ParallelDoubleArray.SummaryStatistics summary() {
        return new WithBounds(this).summary();
    }

    /**
     * Returns the minimum element, or Double.MAX_VALUE if empty
     * @param comparator the comparator
     * @return minimum element, or Double.MAX_VALUE if empty
     */
    public double min(DoubleComparator comparator) {
        return new WithBounds(this).min(comparator);
    }

    /**
     * Returns the minimum element, or Double.MAX_VALUE if empty,
     * @return minimum element, or Double.MAX_VALUE if empty
     */
    public double min() {
        return new WithBounds(this).min();
    }

    /**
     * Returns the maximum element, or -Double.MAX_VALUE if empty
     * @param comparator the comparator
     * @return maximum element, or -Double.MAX_VALUE if empty
     */
    public double max(DoubleComparator comparator) {
        return new WithBounds(this).max(comparator);
    }

    /**
     * Returns the maximum element, or -Double.MAX_VALUE if empty
     * @return maximum element, or -Double.MAX_VALUE if empty
     */
    public double max() {
        return new WithBounds(this).max();
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
    public void cumulate(DoubleReducer reducer, double base) {
        new WithBounds(this).cumulate(reducer, base);
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
        return new WithBounds(this).precumulate(reducer, base);
    }

    /**
     * Sorts the array. Unlike Arrays.sort, this sort does
     * not guarantee that elements with equal keys maintain their
     * relative position in the array.
     * @param comparator the comparator to use
     */
    public void sort(DoubleComparator comparator) {
        new WithBounds(this).sort(comparator);
    }

    /**
     * Sorts the array, assuming all elements are Comparable. Unlike
     * Arrays.sort, this sort does not guarantee that elements
     * with equal keys maintain their relative position in the array.
     * @throws ClassCastException if any element is not Comparable.
     */
    public void sort() {
        new WithBounds(this).sort();
    }

    /**
     * Removes consecutive elements that are equal,
     * shifting others leftward, and possibly decreasing size.  This
     * method may be used after sorting to ensure that this
     * ParallelDoubleArray contains a set of unique elements.
     */
    public void removeConsecutiveDuplicates() {
        new WithBounds(this).removeConsecutiveDuplicates();
    }

    /**
     * Returns a new ParallelDoubleArray containing only the unique
     * elements of this array (that is, without any duplicates).
     * @return the new ParallelDoubleArray
     */
    public ParallelDoubleArray allUniqueElements() {
        return new WithBounds(this).allUniqueElements();
    }

    /**
     * Returns the sum of elements
     * @return the sum of elements
     */
    public double sum() {
        return new WithBounds(this).sum();
    }

    /**
     * Replaces each element with the running sum
     */
    public void cumulateSum() {
        new WithBounds(this).cumulateSum();
    }

    /**
     * Replaces each element with its prefix sum
     * @return the total sum
     */
    public double precumulateSum() {
        return new WithBounds(this).precumulateSum();
    }

    /**
     * Returns an operation prefix that causes a method to
     * operate only on the elements of the array between
     * firstIndex (inclusive) and upperBound (exclusive).
     * @param firstIndex the lower bound (inclusive)
     * @param upperBound the upper bound (exclusive)
     * @return operation prefix
     */
    public WithBounds withBounds(int firstIndex, int upperBound) {
        if (firstIndex > upperBound)
            throw new IllegalArgumentException
                ("firstIndex(" + firstIndex +
                 ") > upperBound(" + upperBound+")");
        if (firstIndex < 0)
            throw new ArrayIndexOutOfBoundsException(firstIndex);
        if (upperBound > this.limit)
            throw new ArrayIndexOutOfBoundsException(upperBound);
        return new WithBounds(this, firstIndex, upperBound);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * only on the elements of the array for which the given selector
     * returns true
     * @param selector the selector
     * @return operation prefix
     */
    public WithFilter withFilter(DoublePredicate selector) {
        return new WithBoundedFilter(this, 0, limit, selector);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given mapper.
     * @param mapper the mapper
     * @return operation prefix
     */
    public <U> WithMapping<U> withMapping
        (MapperFromDouble<? extends U> mapper) {
        return new WithBoundedMapping<U>(this, 0, limit, mapper);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given mapper.
     * @param mapper the mapper
     * @return operation prefix
     */
    public WithDoubleMapping withMapping(DoubleMapper mapper) {
        return new WithBoundedDoubleMapping(this, 0, limit, mapper);
    }

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given mapper.
     * @param mapper the mapper
     * @return operation prefix
     */
    public WithLongMapping withMapping(MapperFromDoubleToLong mapper) {
        return new WithBoundedLongMapping(this, 0, limit, mapper);
    }

    /**
     * A modifier for parallel array operations to apply to mappings
     * of elements, not to the elements themselves
     */
    public static abstract class WithMapping<U> extends PAS.DPrefix {
        final MapperFromDouble<? extends U> mapper;
        WithMapping(ParallelDoubleArray pa,
                    int firstIndex, int upperBound,
                    MapperFromDouble<? extends U> mapper) {
            super(pa, firstIndex, upperBound);
            this.mapper = mapper;
        }

        /**
         * Applies the given procedure to mapped elements
         * @param procedure the procedure
         */
        public void apply(Procedure<? super U> procedure) {
            ex.invoke(new PAS.FJRApply(this, firstIndex, upperBound, null,
                                       procedure));
        }

        /**
         * Returns reduction of mapped elements
         * @param reducer the reducer
         * @param base the result for an empty array
         * @return reduction
         */
        public U reduce(Reducer<U> reducer, U base) {
            PAS.FJRReduce f = new PAS.FJRReduce
                (this, firstIndex, upperBound, null, reducer, base);
            ex.invoke(f);
            return (U)(f.result);
        }

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
            return reduce(Ops.<U>minReducer(comparator), null);
        }

        /**
         * Returns the minimum mapped element, or null if empty,
         * assuming that all elements are Comparables
         * @return minimum mapped element, or null if empty
         * @throws ClassCastException if any element is not Comparable.
         */
        public U min() {
            return reduce((Reducer<U>)(Ops.castedMinReducer()), null);
        }

        /**
         * Returns the maximum mapped element, or null if empty
         * @param comparator the comparator
         * @return maximum mapped element, or null if empty
         */
        public U max(Comparator<? super U> comparator) {
            return reduce(Ops.<U>maxReducer(comparator), null);
        }

        /**
         * Returns the maximum mapped element, or null if empty
         * assuming that all elements are Comparables
         * @return maximum mapped element, or null if empty
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
            PAS.FJRStats f = new PAS.FJRStats
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
            PAS.FJRStats f = new PAS.FJRStats
                (this, firstIndex, upperBound, null,
                 (Comparator<? super U>)(Ops.castedComparator()));
            ex.invoke(f);
            return (ParallelArray.SummaryStatistics<U>)f;
        }

        /**
         * Returns a new ParallelArray holding elements
         * @return a new ParallelArray holding elements
         */
        public abstract ParallelArray<U> all();

        /**
         * Returns a new ParallelArray with the given element type holding
         * elements
         * @param elementType the type of the elements
         * @return a new ParallelArray holding elements
         */
        public abstract ParallelArray<U> all(Class<? super U> elementType);

        /**
         * Return the number of elements selected using bound or
         * filter restrictions. Note that this method must evaluate
         * all selectors to return its result.
         * @return the number of elements
         */
        public abstract int size();

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper
         * applied to current mapper's results
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract <V> WithMapping<V> withMapping
            (Mapper<? super U, ? extends V> mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper
         * applied to current mapper's results
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithDoubleMapping withMapping
            (MapperToDouble<? super U> mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper
         * applied to current mapper's results
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithLongMapping withMapping
            (MapperToLong<? super U> mapper);

        final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
            final double[] array = pa.array;
            final MapperFromDouble mpr = mapper;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = mpr.map(array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                       Object[] dest, int offset) {
            final double[] array = pa.array;
            final MapperFromDouble mpr = mapper;
            for (int i = loIdx; i < hiIdx; ++i)
                dest[offset++] = mpr.map(array[indices[i]]);
        }
    }

    static final class WithBoundedMapping<U> extends WithMapping<U> {
        WithBoundedMapping(ParallelDoubleArray pa,
                           int firstIndex, int upperBound,
                           MapperFromDouble<? extends U> mapper) {
            super(pa, firstIndex, upperBound, mapper);
        }

        public ParallelArray<U> all() {
            int n = upperBound - firstIndex;
            U[] dest = (U[])new Object[n];
            PAS.FJRMap f = new PAS.FJRMap
                (this, firstIndex, upperBound, null, dest, firstIndex);
            ex.invoke(f);
            return new ParallelArray<U>(ex, dest);
        }

        public ParallelArray<U> all(Class<? super U> elementType) {
            int n = upperBound - firstIndex;
            U[] dest = (U[])Array.newInstance(elementType, n);
            PAS.FJRMap f = new PAS.FJRMap
                (this, firstIndex, upperBound, null, dest, firstIndex);
            ex.invoke(f);
            return new ParallelArray<U>(ex, dest);
        }

        public int size() {
            return upperBound - firstIndex;
        }

        public int anyIndex() {
            return (firstIndex < upperBound)? firstIndex : -1;
        }

        public U any() {
            final double[] array = pa.array;
            final MapperFromDouble mpr = mapper;
            return (firstIndex < upperBound)?
                (U)(mpr.map(array[firstIndex])) : null;
        }

        public <V> WithMapping<V> withMapping
            (Mapper<? super U, ? extends V> mapper) {
            return new WithBoundedMapping<V>
                (pa, firstIndex, upperBound,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public WithDoubleMapping withMapping(MapperToDouble<? super U> mapper){
            return new WithBoundedDoubleMapping
                (pa, firstIndex, upperBound,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public WithLongMapping withMapping(MapperToLong<? super U> mapper) {
            return new WithBoundedLongMapping
                (pa, firstIndex, upperBound,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        void leafApply(int lo, int hi, Procedure  procedure) {
            final double[] array = pa.array;
            final MapperFromDouble mpr = mapper;
            for (int i = lo; i < hi; ++i)
                procedure.apply(mpr.map(array[i]));
        }

        Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
            if (lo >= hi)
                return base;
            final double[] array = pa.array;
            final MapperFromDouble mpr = mapper;
            Object r = mpr.map(array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.combine(r, mpr.map(array[i]));
            return r;
        }

        void leafStats(int lo, int hi, PAS.FJRStats task) {
            final double[] array = pa.array;
            final MapperFromDouble mpr = mapper;
            task.size = hi - lo;
            for (int i = lo; i < hi; ++i) {
                Object x = mpr.map(array[i]);
                task.updateMin(i, x);
                task.updateMax(i, x);
            }
        }

    }

    static final class WithBoundedFilteredMapping<U>
        extends WithMapping<U> {
        final DoublePredicate selector;
        WithBoundedFilteredMapping(ParallelDoubleArray pa,
                                   int firstIndex, int upperBound,
                                   DoublePredicate selector,
                                   MapperFromDouble<? extends U> mapper) {
            super(pa, firstIndex, upperBound, mapper);
            this.selector = selector;
        }

        public ParallelArray<U> all() {
            PAS.FJRSelectAllDriver r = new PAS.FJRSelectAllDriver
                (this, Object.class);
            ex.invoke(r);
            return new ParallelArray<U>(ex, (U[])(r.results));
        }

        public ParallelArray<U> all(Class<? super U> elementType) {
            PAS.FJRSelectAllDriver r = new PAS.FJRSelectAllDriver
                (this, elementType);
            ex.invoke(r);
            return new ParallelArray<U>(ex, (U[])(r.results));
        }

        public int size() {
            PAS.FJDCountSelected f = new PAS.FJDCountSelected
                (this, firstIndex, upperBound, null, selector);
            ex.invoke(f);
            return f.count;
        }

        public int anyIndex() {
            AtomicInteger result = new AtomicInteger(-1);
            PAS.FJDSelectAny f = new PAS.FJDSelectAny
                (this, firstIndex, upperBound, null, result, selector);
            ex.invoke(f);
            return result.get();
        }

        public U any() {
            int idx = anyIndex();
            final double[] array = pa.array;
            final MapperFromDouble mpr = mapper;
            return (idx < 0)?  null : (U)(mpr.map(array[idx]));
        }

        public <V> WithMapping<V> withMapping
            (Mapper<? super U, ? extends V> mapper) {
            return new WithBoundedFilteredMapping<V>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public WithDoubleMapping withMapping
            (MapperToDouble<? super U> mapper) {
            return new WithBoundedFilteredDoubleMapping
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public WithLongMapping withMapping
            (MapperToLong<? super U> mapper) {
            return new WithBoundedFilteredLongMapping
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundMapper(this.mapper, mapper));
        }
        void leafApply(int lo, int hi, Procedure  procedure) {
            final DoublePredicate sel = selector;
            final MapperFromDouble mpr = mapper;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x))
                    procedure.apply(mpr.map(x));
            }
        }

        Object leafReduce(int lo, int hi, Reducer reducer, Object base) {
            boolean gotFirst = false;
            Object r = base;
            final DoublePredicate sel = selector;
            final MapperFromDouble mpr = mapper;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x)) {
                    Object y = mpr.map(x);
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

        void leafStats(int lo, int hi, PAS.FJRStats task) {
            final double[] array = pa.array;
            final DoublePredicate sel = selector;
            final MapperFromDouble mpr = mapper;
            int count = 0;
            for (int i = lo; i < hi; ++i) {
                double t = array[i];
                if (sel.evaluate(t)) {
                    Object x = mpr.map(t);
                    ++count;
                    task.updateMin(i, x);
                    task.updateMax(i, x);
                }
            }
            task.size = count;
        }

        int leafIndexSelected(int lo, int hi, boolean positive, int[] indices){
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            int k = 0;
            for (int i = lo; i < hi; ++i) {
                if (sel.evaluate(array[i]) == positive)
                    indices[lo + k++] = i;
            }
            return k;
        }

        int leafMoveSelected(int lo, int hi, int offset, boolean positive) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double t = array[i];
                if (sel.evaluate(t) == positive)
                    array[offset++] = t;
            }
            return offset;
        }
    }

    /**
     * A modifier for parallel array operations to apply to mappings
     * of elements to doubles, not to the elements themselves
     */
    public static abstract class WithDoubleMapping extends PAS.DPrefix {
        WithDoubleMapping(ParallelDoubleArray pa,
                          int firstIndex, int upperBound) {
            super(pa, firstIndex, upperBound);
        }

        /**
         * Applies the given procedure to elements
         * @param procedure the procedure
         */
        public void apply(DoubleProcedure procedure) {
            ex.invoke(new PAS.FJDApply
                      (this, firstIndex, upperBound, null, procedure));
        }

        /**
         * Returns reduction of elements
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
            return reduce(doubleMinReducer(comparator), Double.MAX_VALUE);
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
            return reduce(doubleMaxReducer(comparator), -Double.MAX_VALUE);
        }

        /**
         * Returns the sum of elements
         * @return the sum of elements
         */
        public double sum() {
            return reduce(Ops.doubleAdder(), 0);
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
                (this, firstIndex, upperBound, null,naturalDoubleComparator());
            ex.invoke(f);
            return f;
        }

        /**
         * Returns a new ParallelDoubleArray holding elements
         * @return a new ParallelDoubleArray holding elements
         */
        public abstract ParallelDoubleArray all();

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
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithDoubleMapping withMapping(DoubleMapper mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithLongMapping withMapping
            (MapperFromDoubleToLong mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract <U> WithMapping<U> withMapping
            (MapperFromDouble<? extends U> mapper);
    }

    /**
     * A restriction of parallel array operations to apply only to
     * elements for which a selector returns true
     */
    public static abstract class WithFilter extends WithDoubleMapping {
        WithFilter(ParallelDoubleArray pa, int firstIndex, int upperBound) {
            super(pa, firstIndex, upperBound);
        }

        /**
         * Replaces elements with the results of applying the given
         * mapper to their current values.
         * @param mapper the mapper
         */
        public void replaceWithTransform
            (DoubleMapper  mapper) {
            ex.invoke(new PAS.FJDTransform(this, firstIndex,
                                           upperBound, null, mapper));
        }

        /**
         * Replaces elements with the results of applying the given
         * mapper to their indices
         * @param mapper the mapper
         */
        public void replaceWithMappedIndex(MapperFromIntToDouble mapper) {
            ex.invoke(new PAS.FJDIndexMap(this, firstIndex, upperBound,
                                          null, mapper));
        }

        /**
         * Replaces elements with results of applying the given
         * generator.
         * @param generator the generator
         */
        public void replaceWithGeneratedValue(DoubleGenerator generator) {
            ex.invoke(new PAS.FJDGenerate
                      (this, firstIndex, upperBound, null, generator));
        }

        /**
         * Replaces elements with the given value.
         * @param value the value
         */
        public void replaceWithValue(double value) {
            ex.invoke(new PAS.FJDFill(this, firstIndex, upperBound,
                                      null, value));
        }

        /**
         * Replaces elements with results of applying
         * <tt>combine(thisElement, otherElement)</tt>
         * @param other the other array
         * @param combiner the combiner
         * @throws ArrayIndexOutOfBoundsException if other array has
         * fewer than <tt>upperBound</tt> elements.
         */
        public void replaceWithCombination(ParallelDoubleArray other,
                                           DoubleReducer combiner) {
            if (other.size() < size())
                throw new ArrayIndexOutOfBoundsException();
            ex.invoke(new PAS.FJDCombineInPlace
                      (this, firstIndex, upperBound, null,
                       other.array, 0, combiner));
        }

        /**
         * Replaces elements with results of applying
         * <tt>combine(thisElement, otherElement)</tt>
         * @param other the other array segment
         * @param combiner the combiner
         * @throws ArrayIndexOutOfBoundsException if other array has
         * fewer than <tt>upperBound</tt> elements.
         */
        public void replaceWithCombination
            (ParallelDoubleArray.WithBounds other,
             DoubleReducer combiner) {
            if (other.size() < size())
                throw new ArrayIndexOutOfBoundsException();
            ex.invoke(new PAS.FJDCombineInPlace
                      (this, firstIndex, upperBound, null,
                       other.pa.array, other.firstIndex - firstIndex, combiner));
        }

        /**
         * Replaces elements with results of applying
         * <tt>combine(thisElement, otherElement)</tt>
         * @param other the other array
         * @param combiner the combiner
         * @throws ArrayIndexOutOfBoundsException if other array has
         * fewer than <tt>upperBound</tt> elements.
         */
        public void replaceWithCombination(double[] other,
                                           DoubleReducer combiner) {
            if (other.length < size())
                throw new ArrayIndexOutOfBoundsException();
            ex.invoke(new PAS.FJDCombineInPlace
                      (this, firstIndex, upperBound, null, other,
                       -firstIndex, combiner));
        }

        /**
         * Removes from the array all elements matching bound and/or
         * filter constraints.
         */
        public abstract void removeAll();

        /**
         * Returns a new ParallelDoubleArray containing only unique
         * elements (that is, without any duplicates).
         * @return the new ParallelDoubleArray
         */
        public abstract ParallelDoubleArray allUniqueElements();

        /**
         * Returns an operation prefix that causes a method to operate
         * only on elements for which the current selector (if
         * present) and the given selector returns true
         * @param selector the selector
         * @return operation prefix
         */
        public abstract WithFilter withFilter(DoublePredicate selector);

        /**
         * Returns an operation prefix that causes a method to operate
         * only on elements for which the current selector (if
         * present) or the given selector returns true
         * @param selector the selector
         * @return operation prefix
         */
        public abstract WithFilter orFilter(DoublePredicate selector);

        final void leafTransfer(int lo, int hi, double[] dest, int offset) {
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = (array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                       double[] dest, int offset) {
            final double[] array = pa.array;
            for (int i = loIdx; i < hiIdx; ++i)
                dest[offset++] = (array[indices[i]]);
        }

    }

    /**
     * A restriction of parallel array operations to apply only within
     * a given range of indices.
     */
    public static final class WithBounds extends WithFilter {
        WithBounds(ParallelDoubleArray pa, int firstIndex, int upperBound) {
            super(pa, firstIndex, upperBound);
        }

        WithBounds(ParallelDoubleArray pa) {
            super(pa, 0, pa.limit);
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * only on the elements of the array between firstIndex
         * (inclusive) and upperBound (exclusive).  The bound
         * arguments are relative to the current bounds.  For example
         * <tt>pa.withBounds(2, 8).withBounds(3, 5)</tt> indexes the
         * 5th (= 2+3) and 6th elements of pa. However, indices
         * returned by methods such as <tt>indexOf</tt> are
         * with respect to the underlying ParallelDoubleArray.
         * @param firstIndex the lower bound (inclusive)
         * @param upperBound the upper bound (exclusive)
         * @return operation prefix
         */
        public WithBounds withBounds(int firstIndex, int upperBound) {
            if (firstIndex > upperBound)
                throw new IllegalArgumentException
                    ("firstIndex(" + firstIndex +
                     ") > upperBound(" + upperBound+")");
            if (firstIndex < 0)
                throw new ArrayIndexOutOfBoundsException(firstIndex);
            if (upperBound - firstIndex > this.upperBound - this.firstIndex)
                throw new ArrayIndexOutOfBoundsException(upperBound);
            return new WithBounds(pa,
                                  this.firstIndex + firstIndex,
                                  this.firstIndex + upperBound);
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * only on the elements of the array for which the given selector
         * returns true
         * @param selector the selector
         * @return operation prefix
         */
        public WithFilter withFilter(DoublePredicate selector) {
            return new WithBoundedFilter
                (pa, firstIndex, upperBound, selector);
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public <U> WithMapping<U> withMapping
            (MapperFromDouble<? extends U> mapper) {
            return new WithBoundedMapping<U>
                (pa, firstIndex, upperBound, mapper);
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public WithDoubleMapping withMapping(DoubleMapper mapper) {
            return new WithBoundedDoubleMapping
                (pa, firstIndex, upperBound, mapper);
        }

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public WithLongMapping withMapping(MapperFromDoubleToLong mapper) {
            return new WithBoundedLongMapping
                (pa, firstIndex, upperBound, mapper);
        }

        public WithFilter orFilter(DoublePredicate selector) {
            return new WithBoundedFilter
                (pa, firstIndex, upperBound, selector);
        }

        public int anyIndex() {
            return (firstIndex < upperBound)? firstIndex : -1;
        }

        /**
         * Returns a ParallelDoubleArray containing results of
         * applying <tt>combine(thisElement, otherElement)</tt>
         * for each element.
         * @param other the other array
         * @param combiner the combiner
         * @return the array of mappings
         * @throws ArrayIndexOutOfBoundsException if other array is
         * shorter than this array.
         */
        public ParallelDoubleArray combine(double[] other,
                                           DoubleReducer combiner) {
            int size = upperBound - firstIndex;
            if (other.length < size)
                throw new ArrayIndexOutOfBoundsException();
            double[] dest = new double[size];
            ex.invoke(new PAS.FJDCombine
                      (this, firstIndex, upperBound,
                       null, other, -firstIndex,
                       dest, combiner));
            return new ParallelDoubleArray(ex, dest);
        }

        /**
         * Returns a ParallelDoubleArray containing results of
         * applying <tt>combine(thisElement, otherElement)</tt>
         * for each element.
         * @param other the other array
         * @param combiner the combiner
         * @return the array of mappings
         * @throws ArrayIndexOutOfBoundsException if other array is
         * shorter than this array.
         */
        public ParallelDoubleArray combine(ParallelDoubleArray other,
                                           DoubleReducer combiner) {
            int size = upperBound - firstIndex;
            if (other.size() < size)
                throw new ArrayIndexOutOfBoundsException();
            double[] dest = new double[size];
            ex.invoke(new PAS.FJDCombine
                      (this, firstIndex, upperBound,
                       null, other.array,
                       -firstIndex,
                       dest, combiner));
            return new ParallelDoubleArray(ex, dest);
        }

        /**
         * Returns a ParallelDoubleArray containing results of
         * applying <tt>combine(thisElement, otherElement)</tt>
         * for each element.
         * @param other the other array segment
         * @param combiner the combiner
         * @return the array of mappings
         * @throws ArrayIndexOutOfBoundsException if other segment is
         * shorter than this array.
         */
        public <U,V> ParallelDoubleArray combine
            (ParallelDoubleArray.WithBounds other,
             DoubleReducer combiner) {
            int size = upperBound - firstIndex;
            if (other.size() < size)
                throw new ArrayIndexOutOfBoundsException();
            double[] dest = new double[size];
            ex.invoke(new PAS.FJDCombine
                      (this, firstIndex, upperBound,
                       null, other.pa.array,
                       other.firstIndex - firstIndex,
                       dest, combiner));
            return new ParallelDoubleArray(ex, dest);
        }

        public ParallelDoubleArray all() {
            final double[] array = pa.array;
            // For now, avoid copyOf so people can compile with Java5
            int size = upperBound - firstIndex;
            double[] dest = new double[size];
            System.arraycopy(array, firstIndex, dest, 0, size);
            return new ParallelDoubleArray(ex, dest);
        }

        public ParallelDoubleArray allUniqueElements() {
            PAS.DUniquifierTable tab = new PAS.DUniquifierTable
                (upperBound - firstIndex, pa.array, null);
            PAS.FJUniquifier f = new PAS.FJUniquifier
                (this, firstIndex, upperBound, null, tab);
            ex.invoke(f);
            double[] res = tab.uniqueElements(f.count);
            return new ParallelDoubleArray(ex, res);
        }

        /**
         * Returns the index of some element equal to given target,
         * or -1 if not present
         * @param target the element to search for
         * @return the index or -1 if not present
         */
        public int indexOf(double target) {
            AtomicInteger result = new AtomicInteger(-1);
            PAS.FJDIndexOf f = new PAS.FJDIndexOf
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
        public int binarySearch(double target) {
            final double[] array = pa.array;
            int lo = firstIndex;
            int hi = upperBound - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                double m = array[mid];
                if (target == m)
                    return mid;
                else if (target < m)
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
        public int binarySearch(double target, DoubleComparator comparator) {
            final double[] array = pa.array;
            int lo = firstIndex;
            int hi = upperBound - 1;
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
         * Returns the number of elements within bounds
         * @return the number of elements within bounds
         */
        public int size() {
            return upperBound - firstIndex;
        }

        /**
         * Replaces each element with the running cumulation of applying
         * the given reducer.
         * @param reducer the reducer
         * @param base the result for an empty array
         */
        public void cumulate(DoubleReducer reducer, double base) {
            PAS.FJDCumulateOp op = new PAS.FJDCumulateOp(this, reducer, base);
            PAS.FJDScan r = new PAS.FJDScan(null, op, firstIndex, upperBound);
            ex.invoke(r);
        }

        /**
         * Replaces each element with the running sum
         */
        public void cumulateSum() {
            PAS.FJDCumulatePlusOp op = new PAS.FJDCumulatePlusOp(this);
            PAS.FJDScan r = new PAS.FJDScan(null, op, firstIndex, upperBound);
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
        public double precumulate(DoubleReducer reducer, double base) {
            PAS.FJDPrecumulateOp op = new PAS.FJDPrecumulateOp
                (this, reducer, base);
            PAS.FJDScan r = new PAS.FJDScan(null, op, firstIndex, upperBound);
            ex.invoke(r);
            return r.out;
        }

        /**
         * Replaces each element with its prefix sum
         * @return the total sum
         */
        public double precumulateSum() {
            PAS.FJDPrecumulatePlusOp op = new PAS.FJDPrecumulatePlusOp(this);
            PAS.FJDScan r = new PAS.FJDScan(null, op, firstIndex, upperBound);
            ex.invoke(r);
            return r.out;
        }

        /**
         * Sorts the elements.
         * Unlike Arrays.sort, this sort does
         * not guarantee that elements with equal keys maintain their
         * relative position in the array.
         * @param cmp the comparator to use
         */
        public void sort(DoubleComparator cmp) {
            ex.invoke(new PAS.FJDSorter
                      (cmp, pa.array, new double[upperBound],
                       firstIndex, upperBound - firstIndex, threshold));
        }

        /**
         * Sorts the elements, assuming all elements are
         * Comparable. Unlike Arrays.sort, this sort does not
         * guarantee that elements with equal keys maintain their relative
         * position in the array.
         * @throws ClassCastException if any element is not Comparable.
         */
        public void sort() {
            ex.invoke(new PAS.FJDCSorter
                      (pa.array, new double[upperBound],
                       firstIndex, upperBound - firstIndex, threshold));
        }

        public void removeAll() {
            pa.removeSlotsAt(firstIndex, upperBound);
        }

        /**
         * Removes consecutive elements that are equal (or null),
         * shifting others leftward, and possibly decreasing size.  This
         * method may be used after sorting to ensure that this
         * ParallelDoubleArray contains a set of unique elements.
         */
        public void removeConsecutiveDuplicates() {
            // Sequential implementation for now
            int k = firstIndex;
            int n = upperBound;
            if (k < n) {
                double[] arr = pa.array;
                double last = arr[k++];
                for (int i = k; i < n; ++i) {
                    double x = arr[i];
                    if (last != x)
                        arr[k++] = last = x;
                }
                pa.removeSlotsAt(k, n);
            }
        }

        void leafApply(int lo, int hi, DoubleProcedure procedure) {
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                procedure.apply(array[i]);
        }

        double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
            if (lo >= hi)
                return base;
            final double[] array = pa.array;
            double r = array[lo];
            for (int i = lo+1; i < hi; ++i)
                r = reducer.combine(r, array[i]);
            return r;
        }

        void leafStats(int lo, int hi, PAS.FJDStats task) {
            final double[] array = pa.array;
            task.size = hi - lo;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                task.sum += x;
                task.updateMin(i, x);
                task.updateMax(i, x);
            }
        }

        void leafTransform(int lo, int hi, DoubleMapper  mapper) {
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                array[i] = mapper.map(array[i]);
        }

        void leafIndexMap(int lo, int hi, MapperFromIntToDouble mapper) {
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                array[i] = mapper.map(i);
        }

        void leafGenerate(int lo, int hi, DoubleGenerator generator) {
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                array[i] = generator.generate();
        }
        void leafFillValue(int lo, int hi, double value) {
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                array[i] = value;
        }
        void leafCombineInPlace(int lo, int hi, double[] other,
                                int otherOffset, DoubleReducer combiner) {
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i)
                array[i] = combiner.combine(array[i], other[i+otherOffset]);
        }

        void leafCombine(int lo, int hi, double[] other, int otherOffset,
                         double[] dest, DoubleReducer combiner) {
            final double[] array = pa.array;
            int k = lo - firstIndex;
            for (int i = lo; i < hi; ++i) {
                dest[k] = combiner.combine(array[i], other[i + otherOffset]);
                ++k;
            }
        }
    }

    static final class WithBoundedFilter extends WithFilter {
        final DoublePredicate selector;
        WithBoundedFilter(ParallelDoubleArray pa,
                          int firstIndex, int upperBound,
                          DoublePredicate selector) {
            super(pa, firstIndex, upperBound);
            this.selector = selector;
        }

        public WithFilter withFilter(DoublePredicate selector) {
            return new WithBoundedFilter
                (pa, firstIndex, upperBound,
                 Ops.andPredicate(this.selector, selector));
        }

        public WithFilter orFilter(DoublePredicate selector) {
            return new WithBoundedFilter
                (pa, firstIndex, upperBound,
                 Ops.orPredicate(this.selector, selector));
        }

        public <U> WithMapping<U> withMapping
            (MapperFromDouble<? extends U> mapper) {
            return new WithBoundedFilteredMapping<U>
                (pa, firstIndex, upperBound, selector, mapper);
        }

        public WithDoubleMapping withMapping(DoubleMapper mapper) {
            return new WithBoundedFilteredDoubleMapping
                (pa, firstIndex, upperBound, selector, mapper);
        }

        public WithLongMapping withMapping(MapperFromDoubleToLong mapper) {
            return new WithBoundedFilteredLongMapping
                (pa, firstIndex, upperBound, selector, mapper);
        }

        public int anyIndex() {
            AtomicInteger result = new AtomicInteger(-1);
            PAS.FJDSelectAny f =
                new PAS.FJDSelectAny(this, firstIndex, upperBound,
                                     null, result, selector);
            ex.invoke(f);
            return result.get();
        }

        public ParallelDoubleArray all() {
            PAS.FJDSelectAllDriver r = new PAS.FJDSelectAllDriver(this);
            ex.invoke(r);
            return new ParallelDoubleArray(ex, r.results);
        }

        public int size() {
            PAS.FJDCountSelected f = new PAS.FJDCountSelected
                (this, firstIndex, upperBound, null, selector);
            ex.invoke(f);
            return f.count;
        }

        public ParallelDoubleArray allUniqueElements() {
            PAS.DUniquifierTable tab = new PAS.DUniquifierTable
                (upperBound - firstIndex, pa.array, selector);
            PAS.FJUniquifier f = new PAS.FJUniquifier
                (this, firstIndex, upperBound, null, tab);
            ex.invoke(f);
            double[] res = tab.uniqueElements(f.count);
            return new ParallelDoubleArray(ex, res);
        }

        public void removeAll() {
            PAS.FJRemoveAllDriver f = new PAS.FJRemoveAllDriver
                (this, firstIndex, upperBound);
            ex.invoke(f);
            pa.removeSlotsAt(f.offset, upperBound);
        }

        void leafApply(int lo, int hi, DoubleProcedure  procedure) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x))
                    procedure.apply(x);
            }
        }

        double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
            final DoublePredicate sel = selector;
            boolean gotFirst = false;
            double r = base;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x)) {
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

        void leafStats(int lo, int hi, PAS.FJDStats task) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            int count = 0;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x)) {
                    ++count;
                    task.sum += x;
                    task.updateMin(i, x);
                    task.updateMax(i, x);
                }
            }
            task.size = count;
        }

        void leafTransform(int lo, int hi, DoubleMapper  mapper) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x))
                    array[i] = mapper.map(x);
            }
        }

        void leafIndexMap(int lo, int hi, MapperFromIntToDouble mapper) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x))
                    array[i] = mapper.map(i);
            }
        }

        void leafGenerate(int lo, int hi, DoubleGenerator generator) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x))
                    array[i] = generator.generate();
            }
        }

        void leafFillValue(int lo, int hi, double value) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x))
                    array[i] = value;
            }
        }

        void leafCombineInPlace(int lo, int hi, double[] other,
                                int otherOffset, DoubleReducer combiner) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x))
                    array[i] = combiner.combine(x, other[i+otherOffset]);
            }
        }

        int leafIndexSelected(int lo, int hi, boolean positive, int[] indices){
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            int k = 0;
            for (int i = lo; i < hi; ++i) {
                if (sel.evaluate(array[i]) == positive)
                    indices[lo + k++] = i;
            }
            return k;
        }

        int leafMoveSelected(int lo, int hi, int offset, boolean positive) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double t = array[i];
                if (sel.evaluate(t) == positive)
                    array[offset++] = t;
            }
            return offset;
        }

    }

    static final class WithBoundedDoubleMapping extends WithDoubleMapping {
        final DoubleMapper mapper;
        WithBoundedDoubleMapping(ParallelDoubleArray pa,
                                 int firstIndex, int upperBound,
                                 DoubleMapper mapper) {
            super(pa, firstIndex, upperBound);
            this.mapper = mapper;
        }

        public ParallelDoubleArray all() {
            double[] dest = new double[upperBound - firstIndex];
            PAS.FJDMap f = new PAS.FJDMap
                (this, firstIndex, upperBound, null, dest, firstIndex);
            ex.invoke(f);
            return new ParallelDoubleArray(ex, dest);
        }

        public int size() {
            return upperBound - firstIndex;
        }

        public int anyIndex() {
            return (firstIndex < upperBound)? firstIndex : -1;
        }

        public WithDoubleMapping withMapping(DoubleMapper mapper) {
            return new WithBoundedDoubleMapping
                (pa, firstIndex, upperBound,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public WithLongMapping withMapping(MapperFromDoubleToLong mapper) {
            return new WithBoundedLongMapping
                (pa, firstIndex, upperBound,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public <U> WithMapping<U> withMapping
            (MapperFromDouble<? extends U> mapper) {
            return new WithBoundedMapping<U>
                (pa, firstIndex, upperBound,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        void leafApply(int lo, int hi, DoubleProcedure procedure) {
            final double[] array = pa.array;
            final DoubleMapper mpr = mapper;
            for (int i = lo; i < hi; ++i)
                procedure.apply(mpr.map(array[i]));
        }

        double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
            if (lo >= hi)
                return base;
            final double[] array = pa.array;
            final DoubleMapper mpr = mapper;
            double r = mpr.map(array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.combine(r, mpr.map(array[i]));
            return r;
        }

        void leafStats(int lo, int hi, PAS.FJDStats task) {
            task.size = hi - lo;
            final double[] array = pa.array;
            final DoubleMapper mpr = mapper;
            for (int i = lo; i < hi; ++i) {
                double x = mpr.map(array[i]);
                task.sum += x;
                task.updateMin(i, x);
                task.updateMax(i, x);
            }
        }

        void leafTransfer(int lo, int hi, double[] dest, int offset) {
            final double[] array = pa.array;
            final DoubleMapper mpr = mapper;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = mpr.map(array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                       double[] dest, int offset) {
            final double[] array = pa.array;
            final DoubleMapper mpr = mapper;
            for (int i = loIdx; i < hiIdx; ++i)
                dest[offset++] = (array[indices[i]]);
        }

    }

    static final class WithBoundedFilteredDoubleMapping
        extends WithDoubleMapping {
        final DoublePredicate selector;
        final DoubleMapper mapper;
        WithBoundedFilteredDoubleMapping
            (ParallelDoubleArray pa, int firstIndex, int upperBound,
             DoublePredicate selector, DoubleMapper mapper) {
            super(pa, firstIndex, upperBound);
            this.selector = selector;
            this.mapper = mapper;
        }

        public ParallelDoubleArray all() {
            PAS.FJDSelectAllDriver r = new PAS.FJDSelectAllDriver(this);
            ex.invoke(r);
            return new ParallelDoubleArray(ex, r.results);
        }

        public int size() {
            PAS.FJDCountSelected f = new PAS.FJDCountSelected
                (this, firstIndex, upperBound, null, selector);
            ex.invoke(f);
            return f.count;
        }

        public int anyIndex() {
            AtomicInteger result = new AtomicInteger(-1);
            PAS.FJDSelectAny f = new PAS.FJDSelectAny
                (this, firstIndex, upperBound, null, result, selector);
            ex.invoke(f);
            return result.get();
        }

        public WithDoubleMapping withMapping(DoubleMapper mapper) {
            return new WithBoundedFilteredDoubleMapping
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public WithLongMapping withMapping(MapperFromDoubleToLong mapper) {
            return new WithBoundedFilteredLongMapping
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public <U> WithMapping<U> withMapping
            (MapperFromDouble<? extends U> mapper) {
            return new WithBoundedFilteredMapping<U>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        void leafApply(int lo, int hi, DoubleProcedure procedure) {
            final DoublePredicate sel = selector;
            final DoubleMapper mpr = mapper;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x))
                    procedure.apply(mpr.map(x));
            }
        }

        double leafReduce(int lo, int hi, DoubleReducer reducer, double base) {
            final DoublePredicate sel = selector;
            boolean gotFirst = false;
            double r = base;
            final double[] array = pa.array;
            final DoubleMapper mpr = mapper;
            for (int i = lo; i < hi; ++i) {
                double t = array[i];
                if (sel.evaluate(t)) {
                    double y = mpr.map(t);
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

        void leafStats(int lo, int hi, PAS.FJDStats task) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            final DoubleMapper mpr = mapper;
            int count = 0;
            for (int i = lo; i < hi; ++i) {
                double t = array[i];
                if (sel.evaluate(t)) {
                    ++count;
                    double x = mpr.map(t);
                    task.sum += x;
                    task.updateMin(i, x);
                    task.updateMax(i, x);
                }
            }
            task.size = count;
        }

        void leafTransfer(int lo, int hi, double[] dest, int offset) {
            final double[] array = pa.array;
            final DoubleMapper mpr = mapper;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = mpr.map(array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                       double[] dest, int offset) {
            final double[] array = pa.array;
            final DoubleMapper mpr = mapper;
            for (int i = loIdx; i < hiIdx; ++i)
                dest[offset++] = (array[indices[i]]);
        }

        int leafIndexSelected(int lo, int hi, boolean positive, int[] indices){
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            int k = 0;
            for (int i = lo; i < hi; ++i) {
                if (sel.evaluate(array[i]) == positive)
                    indices[lo + k++] = i;
            }
            return k;
        }

        int leafMoveSelected(int lo, int hi, int offset, boolean positive) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double t = array[i];
                if (sel.evaluate(t) == positive)
                    array[offset++] = t;
            }
            return offset;
        }
    }

    /**
     * A modifier for parallel array operations to apply to mappings
     * of elements to longs, not to the elements themselves
     */
    public static abstract class WithLongMapping extends PAS.DPrefix {
        final MapperFromDoubleToLong mapper;
        WithLongMapping(ParallelDoubleArray pa,
                        int firstIndex, int upperBound,
                        MapperFromDoubleToLong mapper) {
            super(pa, firstIndex, upperBound);
            this.mapper = mapper;
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
            return reduce(longMinReducer(comparator),
                          Long.MAX_VALUE);
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
            return reduce(longMaxReducer(comparator),
                          Long.MIN_VALUE);
        }

        /**
         * Returns the sum of elements
         * @return the sum of elements
         */
        public long sum() {
            return reduce(Ops.longAdder(), 0);
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
        public abstract ParallelLongArray all();

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
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithDoubleMapping withMapping
            (MapperFromLongToDouble mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract WithLongMapping withMapping
            (LongMapper mapper);

        /**
         * Returns an operation prefix that causes a method to operate
         * on mapped elements of the array using the given mapper.
         * @param mapper the mapper
         * @return operation prefix
         */
        public abstract <U> WithMapping<U> withMapping
            (MapperFromLong<? extends U> mapper);

        final void leafTransfer(int lo, int hi, long[] dest, int offset) {
            final double[] array = pa.array;
            final MapperFromDoubleToLong mpr = mapper;
            for (int i = lo; i < hi; ++i)
                dest[offset++] = mpr.map(array[i]);
        }

        final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                       long[] dest, int offset) {
            final double[] array = pa.array;
            final MapperFromDoubleToLong mpr = mapper;
            for (int i = loIdx; i < hiIdx; ++i)
                dest[offset++] = mpr.map(array[indices[i]]);
        }

    }

    static final class WithBoundedLongMapping extends WithLongMapping {
        WithBoundedLongMapping(ParallelDoubleArray pa,
                               int firstIndex, int upperBound,
                               MapperFromDoubleToLong mapper) {
            super(pa, firstIndex, upperBound, mapper);
        }

        public ParallelLongArray all() {
            long[] dest = new long[upperBound - firstIndex];
            PAS.FJLMap f = new PAS.FJLMap
                (this, firstIndex, upperBound, null, dest, firstIndex);
            ex.invoke(f);
            return new ParallelLongArray(ex, dest);
        }

        public int size() {
            return upperBound - firstIndex;
        }

        public int anyIndex() {
            return (firstIndex < upperBound)? firstIndex : -1;
        }

        public WithDoubleMapping withMapping
            (MapperFromLongToDouble mapper) {
            return new WithBoundedDoubleMapping
                (pa, firstIndex, upperBound,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public WithLongMapping withMapping
            (LongMapper mapper) {
            return new WithBoundedLongMapping
                (pa, firstIndex, upperBound,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public <U> WithMapping<U> withMapping
            (MapperFromLong<? extends U> mapper) {
            return new WithBoundedMapping<U>
                (pa, firstIndex, upperBound,
                 Ops.compoundMapper(this.mapper, mapper));
        }


        void leafApply(int lo, int hi, LongProcedure procedure) {
            final double[] array = pa.array;
            final MapperFromDoubleToLong mpr = mapper;
            for (int i = lo; i < hi; ++i)
                procedure.apply(mpr.map(array[i]));
        }

        long leafReduce(int lo, int hi, LongReducer reducer, long base) {
            if (lo >= hi)
                return base;
            final double[] array = pa.array;
            final MapperFromDoubleToLong mpr = mapper;
            long r = mpr.map(array[lo]);
            for (int i = lo+1; i < hi; ++i)
                r = reducer.combine(r, mpr.map(array[i]));
            return r;
        }

        void leafStats(int lo, int hi, PAS.FJLStats task) {
            final double[] array = pa.array;
            final MapperFromDoubleToLong mpr = mapper;
            task.size = hi - lo;
            for (int i = lo; i < hi; ++i) {
                long x = mpr.map(array[i]);
                task.sum += x;
                task.updateMin(i, x);
                task.updateMax(i, x);
            }
        }
    }

    static final class WithBoundedFilteredLongMapping extends WithLongMapping {
        final DoublePredicate selector;
        WithBoundedFilteredLongMapping
            (ParallelDoubleArray pa,
             int firstIndex, int upperBound,
             DoublePredicate selector,
             MapperFromDoubleToLong mapper) {
            super(pa, firstIndex, upperBound, mapper);
            this.selector = selector;
        }

        public ParallelLongArray all() {
            PAS.FJLSelectAllDriver r = new PAS.FJLSelectAllDriver(this);
            ex.invoke(r);
            return new ParallelLongArray(ex, r.results);
        }

        public int size() {
            PAS.FJDCountSelected f = new PAS.FJDCountSelected
                (this, firstIndex, upperBound, null, selector);
            ex.invoke(f);
            return f.count;
        }
        public int anyIndex() {
            AtomicInteger result = new AtomicInteger(-1);
            PAS.FJDSelectAny f = new PAS.FJDSelectAny
                (this, firstIndex, upperBound, null, result, selector);
            ex.invoke(f);
            return result.get();
        }


        public WithDoubleMapping withMapping
            (MapperFromLongToDouble mapper) {
            return new WithBoundedFilteredDoubleMapping
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public WithLongMapping withMapping
            (LongMapper mapper) {
            return new WithBoundedFilteredLongMapping
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        public <U> WithMapping<U> withMapping
            (MapperFromLong<? extends U> mapper) {
            return new WithBoundedFilteredMapping<U>
                (pa, firstIndex, upperBound, selector,
                 Ops.compoundMapper(this.mapper, mapper));
        }

        void leafApply(int lo, int hi, LongProcedure procedure) {
            final DoublePredicate sel = selector;
            final MapperFromDoubleToLong mpr = mapper;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (sel.evaluate(x))
                    procedure.apply(mpr.map(x));
            }
        }

        long leafReduce(int lo, int hi, LongReducer reducer, long base) {
            boolean gotFirst = false;
            long r = base;
            final double[] array = pa.array;
            final DoublePredicate sel = selector;
            final MapperFromDoubleToLong mpr = mapper;
            for (int i = lo; i < hi; ++i) {
                double t = array[i];
                if (sel.evaluate(t)) {
                    long y = mpr.map(t);
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

        void leafStats(int lo, int hi, PAS.FJLStats task) {
            final double[] array = pa.array;
            final DoublePredicate sel = selector;
            final MapperFromDoubleToLong mpr = mapper;
            int count = 0;
            for (int i = lo; i < hi; ++i) {
                double t = array[i];
                if (sel.evaluate(t)) {
                    ++count;
                    long x = mpr.map(t);
                    task.sum += x;
                    task.updateMin(i, x);
                    task.updateMax(i, x);
                }
            }
            task.size = count;
        }

        int leafIndexSelected(int lo, int hi, boolean positive, int[] indices){
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            int k = 0;
            for (int i = lo; i < hi; ++i) {
                if (sel.evaluate(array[i]) == positive)
                    indices[lo + k++] = i;
            }
            return k;
        }

        int leafMoveSelected(int lo, int hi, int offset, boolean positive) {
            final DoublePredicate sel = selector;
            final double[] array = pa.array;
            for (int i = lo; i < hi; ++i) {
                double t = array[i];
                if (sel.evaluate(t) == positive)
                    array[offset++] = t;
            }
            return offset;
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
    public Iterator<Double> iterator() {
        return new ParallelDoubleArrayIterator(array, limit);
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
    public int size() { return limit; }

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
     * Equivalent to <tt>AsList.addAll</tt> but specialized for array
     * arguments and likely to be more efficient.
     * @param other the elements to add
     */
    public void addAll(double[] other) {
        int csize = other.length;
        int end = limit;
        insertSlotsAt(end, csize);
        System.arraycopy(other, 0, array, end, csize);
    }

    /**
     * Equivalent to <tt>AsList.addAll</tt> but specialized for
     * ParallelDoubleArray arguments and likely to be more efficient.
     * @param other the elements to add
     */
    public void addAll(ParallelDoubleArray other) {
        int csize = other.size();
        int end = limit;
        insertSlotsAt(end, csize);
        System.arraycopy(other.array, 0, array, end, csize);
    }

    /**
     * Equivalent to <tt>AsList.addAll</tt> but specialized for
     * ParallelDoubleArray arguments and likely to be more efficient.
     * @param other the elements to add
     */
    public void addAll(ParallelDoubleArray.WithBounds other) {
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

    final void replaceElementsWith(double[] a) {
        System.arraycopy(a, 0, array, 0, a.length);
        limit = a.length;
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
        int hi = limit++;
        if (hi >= array.length)
            resizeArray((hi * 3)/2 + 1);
        if (hi > index)
            System.arraycopy(array, index, array, index+1, hi - index);
        array[index] = e;
    }

    final void appendElement(double e) {
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
        --limit;
    }

    final void removeSlotsAt(int fromIndex, int toIndex) {
        if (fromIndex < toIndex) {
            int size = limit;
            System.arraycopy(array, toIndex, array, fromIndex, size - toIndex);
            int newSize = size - (toIndex - fromIndex);
            limit = newSize;
        }
    }

    final int seqIndexOf(double target) {
        double[] arr = array;
        int fence = limit;
        for (int i = 0; i < fence; i++)
            if (target == arr[i])
                return i;
        return -1;
    }

    final int seqLastIndexOf(double target) {
        double[] arr = array;
        for (int i = limit - 1; i >= 0; i--)
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
            this.hi = ParallelDoubleArray.this.limit;
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
            hi = ParallelDoubleArray.this.limit;
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
            hi = ParallelDoubleArray.this.limit;
            lastRet = -1;
            cursor = i + 1;
        }
    }

    final class AsList extends AbstractList<Double> implements RandomAccess {
        public Double get(int i) {
            if (i >= limit)
                throw new IndexOutOfBoundsException();
            return Double.valueOf(array[i]);
        }

        public Double set(int i, Double x) {
            if (i >= limit)
                throw new IndexOutOfBoundsException();
            double[] arr = array;
            Double t = Double.valueOf(arr[i]);
            arr[i] = x.doubleValue();
            return t;
        }

        public boolean isEmpty() {
            return limit == 0;
        }

        public int size() {
            return limit;
        }

        public Iterator<Double> iterator() {
            return new ListIter(0);
        }

        public ListIterator<Double> listIterator() {
            return new ListIter(0);
        }

        public ListIterator<Double> listIterator(int index) {
            if (index < 0 || index > limit)
                throw new IndexOutOfBoundsException();
            return new ListIter(index);
        }

        public boolean add(Double e) {
            appendElement(e.doubleValue());
            return true;
        }

        public void add(int index, Double e) {
            if (index < 0 || index > limit)
                throw new IndexOutOfBoundsException();
            insertElementAt(index, e.doubleValue());
        }

        public boolean addAll(Collection<? extends Double> c) {
            int csize = c.size();
            if (csize == 0)
                return false;
            int hi = limit;
            setLimit(hi + csize);
            double[] arr = array;
            for (Double e : c)
                arr[hi++] = e.doubleValue();
            return true;
        }

        public boolean addAll(int index, Collection<? extends Double> c) {
            if (index < 0 || index > limit)
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
            limit = 0;
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

        protected void removeRange(int fromIndex, int toIndex) {
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

