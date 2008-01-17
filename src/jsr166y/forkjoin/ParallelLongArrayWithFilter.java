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
 * A prefix view of ParallelLongArray that causes operations to apply
 * only to elements for which a selector returns true.  Instances of
 * this class may be constructed only via prefix methods of
 * ParallelLongArray or its other prefix classes.
 */
public abstract class ParallelLongArrayWithFilter extends ParallelLongArrayWithLongMapping {
    ParallelLongArrayWithFilter
        (ForkJoinExecutor ex, int firstIndex, int upperBound, long[] array) {
        super(ex, firstIndex, upperBound, array);
    }

    /**
     * Replaces elements with the results of applying the given
     * op to their current values.
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArrayWithFilter replaceWithMapping(LongOp  op) {
        ex.invoke(new PAS.FJLTransform
                  (this, firstIndex, upperBound, null, op));
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * op to their indices
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArrayWithFilter replaceWithMappedIndex(IntToLong op) {
        ex.invoke(new PAS.FJLIndexMap
                  (this, firstIndex, upperBound, null, op));
        return this;
    }

   /**
    * Replaces elements with the results of applying the given
    * mapping to each index and current element value
    * @param op the op
     * @return this (to simplify use in expressions)
    */
   public ParallelLongArrayWithFilter replaceWithMappedIndex(IntAndLongToLong op) {
        ex.invoke(new PAS.FJLBinaryIndexMap
                  (this, firstIndex, upperBound, null, op));
        return this;
    }

    /**
     * Replaces elements with results of applying the given
     * generator.
     * @param generator the generator
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArrayWithFilter replaceWithGeneratedValue(LongGenerator generator) {
        ex.invoke(new PAS.FJLGenerate
                  (this, firstIndex, upperBound, null, generator));
        return this;
    }

    /**
     * Replaces elements with the given value.
     * @param value the value
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArrayWithFilter replaceWithValue(long value) {
        ex.invoke(new PAS.FJLFill
                  (this, firstIndex, upperBound, null, value));
        return this;
    }

    /**
     * Replaces elements with results of applying
     * <tt>op(thisElement, otherElement)</tt>
     * @param other the other array
     * @param combiner the combiner
     * @return this (to simplify use in expressions)
     */
    public ParallelLongArrayWithFilter replaceWithMapping(BinaryLongOp combiner,
                                   ParallelLongArray other) {
        ex.invoke(new PAS.FJLPACombineInPlace
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
     */
    public ParallelLongArrayWithFilter replaceWithMapping(BinaryLongOp combiner,
                                   long[] other) {
        ex.invoke(new PAS.FJLCombineInPlace
                  (this, firstIndex, upperBound, null, other,
                   -firstIndex, combiner));
        return this;
    }

    /**
     * Returns a new ParallelLongArray containing only unique
     * elements (that is, without any duplicates).
     * @return the new ParallelLongArray
     */
    public abstract ParallelLongArray allUniqueElements();

    /**
     * Returns an operation prefix that causes a method to operate
     * only on elements for which the current selector (if
     * present) and the given selector returns true
     * @param selector the selector
     * @return operation prefix
     */
    public abstract ParallelLongArrayWithFilter withFilter(LongPredicate selector);

    /**
     * Returns an operation prefix that causes a method to operate
     * only on elements for which the current selector (if
     * present) or the given selector returns true
     * @param selector the selector
     * @return operation prefix
     */
    public abstract ParallelLongArrayWithFilter orFilter(LongPredicate selector);

    final void leafTransfer(int lo, int hi, long[] dest, int offset) {
        final long[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = (a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   long[] dest, int offset) {
        final long[] a = this.array;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = (a[indices[i]]);
    }

}

