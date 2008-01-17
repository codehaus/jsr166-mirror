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
 * only to elements for which a selector returns true.
 * Instances of this class may be constructed only via prefix
 * methods of ParallelDoubleArray or its other prefix classes.
 */
public abstract class ParallelDoubleArrayWithFilter extends ParallelDoubleArrayWithDoubleMapping {
    ParallelDoubleArrayWithFilter
        (ForkJoinExecutor ex, int firstIndex, int upperBound, double[] array) {
        super(ex, firstIndex, upperBound, array);
    }

    /**
     * Replaces elements with the results of applying the given
     * op to their current values.
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArrayWithFilter replaceWithMapping(DoubleOp  op) {
        ex.invoke(new PAS.FJDTransform(this, firstIndex,
                                       upperBound, null, op));
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * op to their indices
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArrayWithFilter replaceWithMappedIndex(IntToDouble op) {
        ex.invoke(new PAS.FJDIndexMap(this, firstIndex, upperBound,
                                      null, op));
        return this;
    }

    /**
     * Replaces elements with the results of applying the given
     * mapping to each index and current element value
     * @param op the op
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArrayWithFilter replaceWithMappedIndex(IntAndDoubleToDouble op) {
        ex.invoke(new PAS.FJDBinaryIndexMap
                  (this, firstIndex, upperBound, null, op));
        return this;
    }

    /**
     * Replaces elements with results of applying the given
     * generator.
     * @param generator the generator
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArrayWithFilter replaceWithGeneratedValue(DoubleGenerator generator) {
        ex.invoke(new PAS.FJDGenerate
                  (this, firstIndex, upperBound, null, generator));
        return this;
    }

    /**
     * Replaces elements with the given value.
     * @param value the value
     * @return this (to simplify use in expressions)
     */
    public ParallelDoubleArrayWithFilter replaceWithValue(double value) {
        ex.invoke(new PAS.FJDFill(this, firstIndex, upperBound,
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
    public ParallelDoubleArrayWithFilter replaceWithMapping(BinaryDoubleOp combiner,
                                   ParallelDoubleArray other) {
        ex.invoke(new PAS.FJDPACombineInPlace
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
    public ParallelDoubleArrayWithFilter replaceWithMapping(BinaryDoubleOp combiner,
                                   double[] other) {
        ex.invoke(new PAS.FJDCombineInPlace
                  (this, firstIndex, upperBound, null, other,
                   -firstIndex, combiner));
        return this;
    }

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
    public abstract ParallelDoubleArrayWithFilter withFilter(DoublePredicate selector);

    /**
     * Returns an operation prefix that causes a method to operate
     * only on elements for which the current selector (if
     * present) or the given selector returns true
     * @param selector the selector
     * @return operation prefix
     */
    public abstract ParallelDoubleArrayWithFilter orFilter(DoublePredicate selector);

    final void leafTransfer(int lo, int hi, double[] dest, int offset) {
        final double[] a = this.array;
        for (int i = lo; i < hi; ++i)
            dest[offset++] = (a[i]);
    }

    final void leafTransferByIndex(int[] indices, int loIdx, int hiIdx,
                                   double[] dest, int offset) {
        final double[] a = this.array;
        for (int i = loIdx; i < hiIdx; ++i)
            dest[offset++] = (a[indices[i]]);
    }

}

