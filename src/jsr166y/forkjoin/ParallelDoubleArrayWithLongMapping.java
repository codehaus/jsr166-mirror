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
 * A prefix view of ParallelDoubleArray that causes operations to apply
 * to mappings of elements, not to the elements themselves.
 * Instances of this class may be constructed only via prefix
 * methods of ParallelDoubleArray or its other prefix classes.
 */
public abstract class ParallelDoubleArrayWithLongMapping extends PAS.DPrefix {
    ParallelDoubleArrayWithLongMapping
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array) {
        super(ex, firstIndex, upperBound, array);
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
    public abstract ParallelDoubleArrayWithDoubleMapping withMapping
        (LongToDouble op);

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public abstract ParallelDoubleArrayWithLongMapping withMapping
        (LongOp op);

    /**
     * Returns an operation prefix that causes a method to operate
     * on mapped elements of the array using the given op.
     * @param op the op
     * @return operation prefix
     */
    public abstract <U> ParallelDoubleArrayWithMapping<U> withMapping
        (LongToObject<? extends U> op);

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public abstract <V,W> ParallelDoubleArrayWithMapping<W> withMapping
        (LongAndObjectToObject<? super V, ? extends W> combiner,
         ParallelArray<V> other);

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public abstract <V> ParallelDoubleArrayWithMapping<V> withMapping
        (LongAndDoubleToObject<? extends V> combiner,
         ParallelDoubleArray other);

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public abstract <V> ParallelDoubleArrayWithMapping<V> withMapping
        (LongAndLongToObject<? extends V> combiner,
         ParallelLongArray other);

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public abstract <V> ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndObjectToDouble<? super V> combiner,
         ParallelArray<V> other);

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public abstract ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndDoubleToDouble combiner,
         ParallelDoubleArray other);

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public abstract ParallelDoubleArrayWithDoubleMapping withMapping
        (LongAndLongToDouble combiner,
         ParallelLongArray other);

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public abstract <V> ParallelDoubleArrayWithLongMapping withMapping
        (LongAndObjectToLong<? super V> combiner,
         ParallelArray<V> other);

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public abstract ParallelDoubleArrayWithLongMapping withMapping
        (LongAndDoubleToLong combiner,
         ParallelDoubleArray other);

    /**
     * Returns an operation prefix that causes a method to operate
     * on binary mappings of this array and the other array.
     * @param combiner the combiner
     * @param other the other array
     * @return operation prefix
     */
    public abstract ParallelDoubleArrayWithLongMapping withMapping
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
    public abstract <V> ParallelDoubleArrayWithMapping<V> withIndexedMapping
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
    public abstract ParallelDoubleArrayWithDoubleMapping withIndexedMapping
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
    public abstract ParallelDoubleArrayWithLongMapping withIndexedMapping
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

