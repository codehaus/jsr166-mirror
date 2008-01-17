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
 * A prefix view of ParallelArray that causes operations to apply
 * only to elements for which a selector returns true.
 * Instances of this class may be constructed only via prefix
 * methods of ParallelArray or its other prefix classes.
 */
public abstract class ParallelArrayWithFilter<T> extends ParallelArrayWithMapping<T,T>{
    ParallelArrayWithFilter(ForkJoinExecutor ex, int firstIndex, int upperBound, T[] array) {
        super(ex, firstIndex, upperBound, array);
    }

    /**
     * Replaces elements with the results of applying the given
     * op to their current values.
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelArrayWithFilter<? extends T> replaceWithMapping(Op<? super T, ? extends T> op) {
        ex.invoke(new PAS.FJOTransform(this, firstIndex, upperBound,
                                       null, op));
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * op to their indices
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelArrayWithFilter<? extends T> replaceWithMappedIndex(IntToObject<? extends T> op) {
        ex.invoke(new PAS.FJOIndexMap(this, firstIndex, upperBound,
                                      null, op));
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * mapping to each index and current element value
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelArrayWithFilter<? extends T> replaceWithMappedIndex
        (IntAndObjectToObject<? super T, ? extends T> op) {
        ex.invoke(new PAS.FJOBinaryIndexMap
                  (this, firstIndex, upperBound, null, op));
        return this;
    }

    /**
     * Replaces elements with results of applying the given
     * generator.
     * @param generator the generator
     * @return this (to simplify use in expressions)
     */
    public ParallelArrayWithFilter<? extends T> replaceWithGeneratedValue
        (Generator<? extends T> generator) {
        ex.invoke(new PAS.FJOGenerate
                  (this, firstIndex, upperBound, null, generator));
        return this;
    }

    /**
     * Replaces elements with the given value.
     * @param value the value
     * @return this (to simplify use in expressions)
     */
    public ParallelArrayWithFilter<? extends T> replaceWithValue(T value) {
        ex.invoke(new PAS.FJOFill(this, firstIndex, upperBound,
                                  null, value));
        return this;
    }

    /**
     * Replaces elements with results of applying
     * <tt>op(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @return this (to simplify use in expressions)
     * @throws ArrayIndexOutOfBoundsException if other array has
     * fewer than <tt>upperBound</tt> elements.
     */
    public ParallelArrayWithFilter<? extends T> replaceWithMapping(BinaryOp<T,T,T> combiner,
                                   ParallelArray<? extends T> other) {
        ex.invoke(new PAS.FJOPACombineInPlace
                  (this, firstIndex, upperBound, null,
                   other, other.firstIndex - firstIndex, combiner));
        return this;
    }

    /**
     * Replaces elements with results of applying
     * <tt>op(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @return this (to simplify use in expressions)
     * @throws ArrayIndexOutOfBoundsException if other array has
     * fewer than <tt>upperBound</tt> elements.
     */
    public ParallelArrayWithFilter<? extends T> replaceWithMapping(BinaryOp<T,T,T> combiner, T[] other) {
        ex.invoke(new PAS.FJOCombineInPlace
                  (this, firstIndex, upperBound, null, other,
                   -firstIndex, combiner));
        return this;
    }

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
    public abstract ParallelArrayWithFilter<T> withFilter
        (Predicate<? super T> selector);

    /**
     * Returns an operation prefix that causes a method to operate
     * only on elements for which the current selector (if
     * present) or the given selector returns true
     * @param selector the selector
     * @return operation prefix
     */
    public abstract ParallelArrayWithFilter<T> orFilter
        (Predicate<? super T> selector);

    final void leafTransfer(int lo, int hi, Object[] dest, int offset) {
        final Object[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = (a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   Object[] dest, int offset) {
        final Object[] a = this.array;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = (a[indices[i]]);
    }
}

