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
 * An array of doubles supporting parallel operations.  This class
 * provides methods supporting the same operations as {@link
 * ParallelArray}, but specialized for scalar doubles. It additionally
 * provides a few methods (for example <tt>cumulate</tt>) specific to
 * numerical values.
 */
public class ParallelDoubleArray {
    private final double[] array;
    private final ForkJoinExecutor ex;

    /**
     * Creates a new ParallelDoubleArray using the given executor and
     * array. In general, the handed off array should not be used for
     * other purposes once constructing this ParallelDoubleArray.
     * @param executor the executor
     * @param handoff the array
     */ 
    public ParallelDoubleArray(ForkJoinExecutor executor, 
                               double[] handoff) {
        if (executor == null || handoff == null)
            throw new NullPointerException();
        this.ex = executor;
        this.array = handoff;
    }

    /**
     * Creates a new ParallelDoubleArray using the given executor and an
     * array of the given size, initially holding copies of the given
     * source truncated or padded with zero to obtain the specified
     * length.
     * @param executor the executor
     * @param size the array size
     * @param sourceToCopy the source of initial elements
     */ 
    public ParallelDoubleArray(ForkJoinExecutor executor, int size,
                               double[] sourceToCopy) {
        if (executor == null)
            throw new NullPointerException();
        this.ex = executor;
        this.array = new double[size];
        System.arraycopy(sourceToCopy, 0, array, 0,
                         Math.min(sourceToCopy.length, size));
    }

    /**
     * Creates a new ParallelDoubleArray using the given executor and
     * an array of the given size.
     * @param executor the executor
     * @param size the array size
     */ 
    public ParallelDoubleArray(ForkJoinExecutor executor, int size) {
        if (executor == null)
            throw new NullPointerException();
        this.ex = executor;
        this.array = new double[size];
    }

    /**
     * Creates a new ParallelDoubleArray using the given executor and an
     * array of the given size containing the mappings of each
     * consecutive integer from zero to <tt>size-1</tt>.
     * @param executor the executor
     * @param size the array size
     * @param mapper the mapper
     */
    public ParallelDoubleArray(ForkJoinExecutor executor, int size,
                               MapperFromIntToDouble  mapper) {
        if (executor == null)
            throw new NullPointerException();
        this.ex = executor;
        this.array = new double[size];
        int m = executor.getParallelismLevel();
        int g = defaultGranularity(m, size);
        if (size > 0)
            executor.invoke(new FJGenerator(array, mapper, 0, size, g));
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
    public double[] getArray() { return array; }

    /**
     * Returns the length of the underlying array
     * @return the length of the underlying array
     */
    public int size() { return array.length;  }

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
     * default granularity for divide-by-two array tasks.
     */
    static int defaultGranularity(int threads, int n) {
        return (threads > 1)? (1 + n / (threads << 4)) : n;
    }

    /**
     * Check that fromIndex and toIndex are in range, and throw an
     * appropriate exception if they aren't.
     * @return length
     */
    private static int rangeCheck(int size, int fromIndex, int toIndex) {
        if (fromIndex > toIndex)
            throw new IllegalArgumentException("fromIndex(" + fromIndex +
                                               ") > toIndex(" + toIndex+")");
        if (fromIndex < 0)
            throw new ArrayIndexOutOfBoundsException(fromIndex);
        if (toIndex > size)
            throw new ArrayIndexOutOfBoundsException(toIndex);
        return toIndex - fromIndex;
    }

    /**
     * Holds parameters and performs base actions for various
     * apply/select/map/reduce combinations. For uniformity, all use
     * RecursiveActions, holding results if any in results fields
     * introduced in subclasses.
     */
    static abstract class FJOp {
        final int granularity;
        final double[] array;
        FJOp(int granularity,
                  double[] array) {
            this.granularity = granularity;
            this.array = array;
        }
    }

    /**
     * Base for apply actions
     */
    static abstract class FJApplyOp extends FJOp {
        final DoubleProcedure proc;
        FJApplyOp(int granularity,
                       double[] array,
                       DoubleProcedure proc) {
            super(granularity, array);
            this.proc = proc;
        }
        abstract void leafAction(int lo, int hi);
    }

    /*
     * Fork/Join version of apply. This and other Parallel
     * divide-and-conquer drivers for aggregate operations.  have the
     * same structure. Rather than pure recursion, most link
     * right-hand-sides in arrays and then join up the tree, exploiting
     * cases where tasks aren't stolen.  This generates tasks a bit
     * faster than recursive style, leading to better work-stealing
     * performance.
     */
    static final class FJApplyer extends RecursiveAction {
        final FJApplyOp p;
        final int lo;
        final int hi;
        FJApplyer next;
        FJApplyer(FJApplyOp p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJApplyer right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJApplyer r =
                    new FJApplyer(p, mid, h);
                r.next = right;
                right = r;
                right.fork();
                h = mid;
            }
            p.leafAction(l, h);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    /**
     * Applies the given procedure to each element of the array.
     * @param proc the procedure
     */
    public void apply(DoubleProcedure proc) {
        apply(0, array.length, proc);
    }

    /**
     * Applies the given procedure to each element of the array
     * from fromIndex (inclusive) to toIndex(exclusive);
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param proc the procedure
     */
    public void apply(int fromIndex, int toIndex,
                      DoubleProcedure proc) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJPlainApplyOp p =
            new FJPlainApplyOp(g, array, proc);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJApplyer(p, fromIndex, toIndex));
    }

    static final class FJPlainApplyOp extends FJApplyOp {
        FJPlainApplyOp(int granularity,
                            double[] array,
                            DoubleProcedure proc) {
            super(granularity, array, proc);
        }

        void leafAction(int lo, int hi) {
            for (int i = lo; i < hi; ++i)
                proc.apply(array[i]);
        }
    }

    /**
     * Applies the given procedure to each element of the array
     * for which the given selector returns true
     * @param selector the predicate
     * @param proc the procedure
     */
    public void apply(DoublePredicate selector,
                      DoubleProcedure proc) {
        // evade resolution bug
        int n = array.length;
        int fromIndex = 0;
        int toIndex = n;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectApplyOp p =
            new FJSelectApplyOp(g, array, proc, selector);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJApplyer(p, fromIndex, toIndex));
    }

    /**
     * Applies the given procedure to each element of the array from
     * fromIndex to toIndex for which the given selector returns true
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param selector the predicate
     * @param proc the procedure
     */
    public void apply(int fromIndex, int toIndex,
                      DoublePredicate selector,
                      DoubleProcedure proc) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectApplyOp p =
            new FJSelectApplyOp(g, array, proc, selector);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJApplyer(p, fromIndex, toIndex));
    }

    static final class FJSelectApplyOp extends FJApplyOp {
        final DoublePredicate pred;
        FJSelectApplyOp(int granularity,
                             double[] array,
                             DoubleProcedure proc,
                             DoublePredicate pred) {
            super(granularity, array, proc);
            this.pred = pred;
        }

        void leafAction(int lo, int hi) {
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (pred.evaluate(x))
                    proc.apply(x);
            }
        }
    }

    /**
     * Base for reduce operations
     */
    static abstract class FJReduceOp extends FJOp {
        final DoubleReducer reducer;
        final double base;
        FJReduceOp(int granularity,
                        double[] array,
                        DoubleReducer reducer,
                        double base) {
            super(granularity, array);
            this.reducer = reducer;
            this.base = base;
        }

        abstract double leafAction(int lo, int hi);
    }

    /**
     * Fork/Join version of reduce
     */
    static final class FJReducer extends RecursiveAction {
        final FJReduceOp p;
        final int lo;
        final int hi;
        double result;
        FJReducer next;

        FJReducer(FJReduceOp p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJReducer right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJReducer r = new FJReducer(p, mid, h);
                r.next = right;
                right = r;
                right.fork();
                h = mid;
            }
            double x = p.leafAction(l, h);
            DoubleReducer reducer = p.reducer;
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                x = reducer.combine(x, right.result);
                right = right.next;
            }
            result = x;
        }
    }

    /**
     * Returns reduction of array
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public double reduce(DoubleReducer reducer,
                         double base) {
        return reduce(0, array.length, reducer, base);
    }

    /**
     * Returns reduction of array elements from fromIndex to
     * toIndex
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public double reduce(int fromIndex, int toIndex,
                         DoubleReducer reducer,
                         double base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJPlainReduceOp p =
            new FJPlainReduceOp(g, array, reducer, base);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);

        FJReducer f =
            new FJReducer(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJPlainReduceOp extends FJReduceOp {
        FJPlainReduceOp(int granularity,
                             double[] array,
                             DoubleReducer reducer,
                             double base) {
            super(granularity, array, reducer, base);
        }

        double leafAction(int lo, int hi) {
            boolean gotFirst = false;
            double r = base;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (!gotFirst) {
                    gotFirst = true;
                    r = x;
                }
                else
                    r = reducer.combine(r, x);
            }
            return r;
        }
    }

    /**
     * Returns reduction of the elements of the array for which
     * the selector returns true;
     * @param selector the predicate
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public double reduce(DoublePredicate selector,
                         DoubleReducer reducer,
                         double base) {
        return reduce(0, array.length, selector, reducer, base);
    }

    /**
     * Returns reduction of the elements of the array from fromIndex to
     * toIndex for which the selector returns true;
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param selector the predicate
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public double reduce(int fromIndex, int toIndex,
                         DoublePredicate selector,
                         DoubleReducer reducer,
                         double base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectReduceOp p =
            new FJSelectReduceOp(g, array, reducer, base, selector);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJReducer f =
            new FJReducer(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJSelectReduceOp extends FJReduceOp {
        final DoublePredicate pred;
        FJSelectReduceOp(int granularity,
                              double[] array,
                              DoubleReducer reducer,
                              double base,
                              DoublePredicate pred) {
            super(granularity, array, reducer, base);
            this.pred = pred;
        }

        double leafAction(int lo, int hi) {
            boolean gotFirst = false;
            double r = base;
            for (int i = lo; i < hi; ++i) {
                double x = array[i];
                if (pred.evaluate(x)) {
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

    }

    /**
     * Base for map actions
     */
    static final class FJMapOp extends FJOp {
        final DoubleTransformer mapper;
        final double[] dest;
        FJMapOp(int granularity,
                     double[] array,
                     DoubleTransformer mapper,
                     double[] dest) {
            super(granularity, array);
            this.mapper = mapper;
            this.dest = dest;
        }

        void leafAction(int lo, int hi) {
            for (int i = lo; i < hi; ++i)
                dest[i] = mapper.map(array[i]);;
        }
    }

    /**
     * Fork/Join version of ArrayMap
     */
    static final class FJMap extends RecursiveAction {
        final FJMapOp p;
        final int lo;
        final int hi;
        FJMap next;

        FJMap(FJMapOp p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJMap right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMap r = new FJMap(p, mid, h);
                r.next = right;
                right = r;
                right.fork();
                h = mid;
            }
            p.leafAction(l, h);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    /**
     * Returns an array mapping each element of given array using mapper
     * @param mapper the mapper
     * @return the array of mappings
     */
    public  ParallelDoubleArray map(DoubleTransformer mapper) {
        return map(0, array.length, mapper);
    }

    /**
     * Returns an array mapping each element of given array using mapper
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param mapper the mapper
     * @return the array of mappings
     */
    public  ParallelDoubleArray map(int fromIndex, int toIndex,
                                    DoubleTransformer mapper) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        double[] dest = new double[n];
        FJMapOp p = new FJMapOp(g, array, mapper, dest);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJMap(p, fromIndex, toIndex));
        return new ParallelDoubleArray(ex, dest);
    }

    /**
     * Base for int map actions
     */
    static final class FJIntMapOp extends FJOp {
        final MapperFromDoubleToInt mapper;
        final int[] dest;
        FJIntMapOp(int granularity,
                        double[] array,
                        MapperFromDoubleToInt mapper,
                        int[] dest) {
            super(granularity, array);
            this.mapper = mapper;
            this.dest = dest;
        }

        void leafAction(int lo, int hi) {
            for (int i = lo; i < hi; ++i)
                dest[i] = mapper.map(array[i]);;
        }
    }

    /**
     * Fork/Join version of ArrayIntMap
     */
    static final class FJIntMap extends RecursiveAction {
        final FJIntMapOp p;
        final int lo;
        final int hi;
        FJIntMap next;

        FJIntMap(FJIntMapOp p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJIntMap right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJIntMap r = new FJIntMap(p, mid, h);
                r.next = right;
                right = r;
                right.fork();
                h = mid;
            }
            p.leafAction(l, h);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    /**
     * Returns an array mapping each element of given array using mapper
     * @param mapper the mapper
     * @return the array of mappings
     */
    public ParallelIntArray map(MapperFromDoubleToInt  mapper) {
        return map(0, array.length, mapper);
    }

    /**
     * Returns an array mapping each element of given array using mapper
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param mapper the mapper
     * @return the array of mappings
     */
    public ParallelIntArray map(int fromIndex, int toIndex,
                                MapperFromDoubleToInt  mapper) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        int[] dest = new int[n];
        FJIntMapOp p = new FJIntMapOp(g, array, mapper, dest);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJIntMap(p, fromIndex, toIndex));
        return new ParallelIntArray(ex, dest);
    }

    /**
     * Base for long map actions
     */
    static final class FJLongMapOp extends FJOp {
        final MapperFromDoubleToLong mapper;
        final long[] dest;
        FJLongMapOp(int granularity,
                         double[] array,
                         MapperFromDoubleToLong mapper,
                         long[] dest) {
            super(granularity, array);
            this.mapper = mapper;
            this.dest = dest;
        }

        void leafAction(int lo, int hi) {
            for (int i = lo; i < hi; ++i)
                dest[i] = mapper.map(array[i]);;
        }
    }

    /**
     * Fork/Join version of ArrayLongMap
     */
    static final class FJLongMap extends RecursiveAction {
        final FJLongMapOp p;
        final int lo;
        final int hi;
        FJLongMap next;

        FJLongMap(FJLongMapOp p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJLongMap right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJLongMap r = new FJLongMap(p, mid, h);
                r.next = right;
                right = r;
                right.fork();
                h = mid;
            }
            p.leafAction(l, h);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    /**
     * Returns an array mapping each element of given array using mapper
     * @param mapper the mapper
     * @return the array of mappings
     */
    public ParallelLongArray map(MapperFromDoubleToLong  mapper) {
        return map(0, array.length, mapper);
    }

    /**
     * Returns an array mapping each element of given array using mapper
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param mapper the mapper
     * @return the array of mappings
     */
    public ParallelLongArray map(int fromIndex, int toIndex,
                      MapperFromDoubleToLong  mapper) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        long[] dest = new long[n];
        FJLongMapOp p = new FJLongMapOp(g, array, mapper, dest);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJLongMap(p, fromIndex, toIndex));
        return new ParallelLongArray(ex, dest);
    }

    /**
     * Fork/Join version of generate
     */
    static final class FJGenerator extends RecursiveAction {
        final double[] dest;
        final MapperFromIntToDouble mapper;
        final int lo;
        final int hi;
        final int gran;
        FJGenerator next;

        FJGenerator(double[] dest,
                    MapperFromIntToDouble mapper,
                    int lo,
                    int hi,
                    int gran) {
            this.dest = dest;
            this.mapper = mapper;
            this.lo = lo;
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            FJGenerator right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJGenerator r =
                    new FJGenerator(dest, mapper, mid, h, g);
                r.next = right;
                right = r;
                right.fork();
                h = mid;
            }
            for (int i = l; i < h; ++i)
                dest[i] = mapper.map(i);
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right))
                    right.compute();
                else
                    right.join();
                right = right.next;
            }
        }
    }

    /**
     * Base for index actions
     */
    static final class FJIndexOp extends FJOp {
        final DoublePredicate  pred;
        final AtomicInteger result;
        FJIndexOp(int granularity,
                       double[] array,
                       DoublePredicate  pred) {
            super(granularity, array);
            this.pred = pred;
            result = new AtomicInteger(-1);
        }

        void leafAction(int lo, int hi) {
            AtomicInteger res = result;
            for (int i = lo; i < hi && res.get() < 0; ++i) {
                if (pred.evaluate(array[i])) {
                    res.compareAndSet(-1, i);
                    break;
                }
            }
        }
    }

    /**
     * Returns the index of some element of the array matching the
     * given predicate, or -1 if none
     * @param pred the predicate
     * @return the index of some element of the array matching
     * the predicate, or -1 if none.
     */
    public int indexOf(DoublePredicate  pred) {
        // evade resolution problems
        int n = array.length;
        int fromIndex = 0;
        int toIndex = n;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJIndexOp p =
            new FJIndexOp(g, array, pred);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJIndexOf(p, fromIndex, toIndex));
        return p.result.get();

    }

    /**
     * Returns the index of some element of the array from fromIndex to
     * toIndex matching the given predicate, or -1 if none
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param pred the predicate
     * @return the index of some element of the array matching
     * the predicate, or -1 if none.
     */
    public int indexOf(int fromIndex, int toIndex,
                       DoublePredicate  pred) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJIndexOp p =
            new FJIndexOp(g, array, pred);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJIndexOf(p, fromIndex, toIndex));
        return p.result.get();
    }

    /**
     * Fork/Join version of IndexOf
     */
    static final class FJIndexOf extends RecursiveAction {
        final FJIndexOp p;
        final int lo;
        final int hi;
        FJIndexOf next;

        FJIndexOf(FJIndexOp p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            AtomicInteger res = p.result;
            if (res.get() >= 0)
                return;
            FJIndexOf right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJIndexOf r = new FJIndexOf(p, mid, h);
                r.next = right;
                right = r;
                right.fork();
                h = mid;
            }
            p.leafAction(l, h);
            boolean done = res.get() >= 0;
            while (right != null) {
                if (ForkJoinWorkerThread.removeIfNextLocalTask(right)) {
                    if (!done)
                        right.compute();
                }
                else if (done)
                    right.cancel();
                else
                    right.join();
                right = right.next;
                if (!done)
                    done = res.get() >= 0;
            }
        }
    }
    /**
     * Returns the minimum of all elements, or Double.MAX_VALUE if empty
     * @return minimum of all elements, or Double.MAX_VALUE if empty
     */
    public double min() {
        return reduce(DoubleMinReducer.min, Double.MAX_VALUE);
    }

    /**
     * Returns the minimum of elements from fromIndex to toIndex, or
     * Double.MAX_VALUE if empty
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @return minimum of all elements, or Double.MAX_VALUE if empty
     */
    public double min(int fromIndex, int toIndex) {
        return reduce(fromIndex, toIndex, DoubleMinReducer.min,
                      Double.MAX_VALUE);
    }

    /**
     * Returns the maximum of all elements, or Double.MIN_VALUE if empty
     * @return maximum element, or Double.MIN_VALUE if empty
     */
    public double max() {
        return reduce(DoubleMaxReducer.max, Double.MIN_VALUE);
    }

    /**
     * Returns the maximum of elements from fromIndex to toIndex, or
     * Double.MIN_VALUE if empty
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @return maximum element, or Double.MIN_VALUE if empty
     */
    public double max(int fromIndex, int toIndex) {
        return reduce(fromIndex, toIndex, DoubleMaxReducer.max,
                      Double.MIN_VALUE);
    }

    /**
     * Returns the sum of all elements
     * @return the sum of all elements
     */
    public double sum() {
        return reduce(DoubleAdder.adder, 0.0);
    }

    /**
     * Returns the sum of elements from fromIndex to toIndex
     * @return the sum of elements from fromIndex to toIndex
     */
    public double sum(int fromIndex, int toIndex) {
        return reduce(fromIndex, toIndex, DoubleAdder.adder, 0.0);
    }

    /**
     * Returns a ParallelDoubleArray mapping each element of array for
     * which the selector returns true;
     * @param selector the predicate
     * @param mapper the mapper
     * @return the array of mappings
     */
    public  ParallelDoubleArray map(DoublePredicate selector,
                                    DoubleTransformer mapper) {
        int n = array.length;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectAllDriver r =
            new FJSelectAllDriver(array, selector, mapper, 0, n, g);
        ex.invoke(r);
        return new ParallelDoubleArray(ex, r.results);
    }

    /**
     * Returns a ParallelDoubleArray mapping each element of array from
     * fromIndex to toIndex for which the selector returns true;
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param selector the predicate
     * @param mapper the mapper
     */
    public  ParallelDoubleArray map(int fromIndex, int toIndex,
                                    DoublePredicate selector,
                                    DoubleTransformer mapper) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectAllDriver r =
            new FJSelectAllDriver(array, selector, mapper,
                                       fromIndex, toIndex, g);
        ex.invoke(r);
        return new ParallelDoubleArray(ex, r.results);
    }

    /**
     * Returns a ParallelDoubleArray of all elements of the array matching
     * pred.  The order of appearance of elements in the returned
     * array is the same as in the source array.
     * @param pred the predicate
     * @return an array of all elements matching the predicate.
     */
    public ParallelDoubleArray select(DoublePredicate pred) {
        return select(0, array.length, pred);
    }

    /**
     * Returns an array of all elements from fromIndex to toIndex of the
     * array matching pred.  The order of appearance of elements in the
     * returned array is the same as in the source array.
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param pred the predicate
     * @return an array of all elements matching the predicate.
     */
    public ParallelDoubleArray select(int fromIndex, int toIndex,
                                      DoublePredicate pred) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectAllDriver r =
            new FJSelectAllDriver(array, pred, null,
                                       fromIndex, toIndex, g);
        ex.invoke(r);
        return new ParallelDoubleArray(ex, r.results);
    }

    /**
     * Fork/Join version of ArraySelectAll.  Proceeds in two passes. In the
     * first pass, indices of matching elements are recorded in match
     * array.  In second pass, once the size of results is known and
     * result array is constructed in driver, the matching elements
     * are placed into corresponding result positions.
     *
     * As a compromise to get good performance in cases of both dense
     * and sparse result sets, the matches array is allocated only on
     * demand, and subtask calls for empty subtrees are suppressed.
     */
    static final class FJSelectAll extends RecursiveAction {
        final double[] array;
        final DoublePredicate pred;
        final DoubleTransformer mapper;
        final int lo;
        final int hi;
        final int gran;
        int[] matches;
        int nmatches;
        int offset;
        double[] results;
        FJSelectAll left, right;

        FJSelectAll(double[] array,
                         DoublePredicate pred,
                         DoubleTransformer mapper,
                         int lo,
                         int hi,
                         int gran) {
            this.array = array;
            this.pred = pred;
            this.mapper = mapper;
            this.lo = lo;
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            if (hi - lo < gran) {
                if (results == null)
                    leafPhase1();
                else if (nmatches != 0)
                    leafPhase2();
            }
            else {
                if (results == null)
                    internalPhase1();
                else if (nmatches != 0)
                    internalPhase2();
            }
        }

        void leafPhase1() {
            int[] m = null; // only construct if find at least one match
            int n = 0;
            for (int j = lo; j < hi; ++j) {
                if (pred.evaluate(array[j])) {
                    if (m == null)
                        m = new int[hi - j];
                    m[n++] = j;
                }
            }
            nmatches = n;
            matches = m;
        }

        void leafPhase2() {
            int[] m = matches;
            if (m != null) {
                int n = nmatches;
                int k = offset;
                DoubleTransformer mapper = this.mapper;
                if (mapper != null) {
                    for (int i = 0; i < n; ++i)
                        results[k++] = mapper.map(array[m[i]]);
                }
                else {
                    for (int i = 0; i < n; ++i)
                        results[k++] = array[m[i]];
                }
            }
        }

        void internalPhase1() {
            int mid = (lo + hi) >>> 1;
            FJSelectAll l =
                new FJSelectAll(array, pred, mapper, lo,    mid, gran);
            FJSelectAll r =
                new FJSelectAll(array, pred, mapper, mid,   hi,  gran);
            coInvoke(l, r);
            int lnm = l.nmatches;
            if (lnm != 0)
                left = l;
            int rnm = r.nmatches;
            if (rnm != 0)
                right = r;
            nmatches = lnm + rnm;
        }

        void internalPhase2() {
            int k = offset;
            double[] res = results;
            if (left != null) {
                int lnm = left.nmatches;
                left.offset = k;
                left.results = res;
                left.reinitialize();
                if (right != null) {
                    right.offset = k + lnm;
                    right.results = res;
                    right.reinitialize();
                    coInvoke(left, right);
                }
                else
                    left.compute();
            }
            else if (right != null) {
                right.offset = k;
                right.results = res;
                right.compute();
            }
        }

    }

    /**
     * Runs the two passes of ArraySelectAll
     */
    static final class FJSelectAllDriver extends RecursiveAction {
        final double[] array;
        final DoublePredicate pred;
        final DoubleTransformer mapper;
        final int gran;
        final int lo;
        final int hi;
        double[] results;

        FJSelectAllDriver(double[] array,
                               DoublePredicate pred,
                               DoubleTransformer mapper,
                               int lo, int hi,
                               int gran) {
            this.array = array;
            this.pred = pred;
            this.mapper = mapper;
            this.lo = lo;
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            int n = hi - lo;
            FJSelectAll r =
                new FJSelectAll(array, pred, mapper, lo, hi, gran);
            r.compute();
            int rm = r.nmatches;
            double[] res = new double[rm];
            this.results = res;
            r.results = res;
            r.compute();
        }
    }

    /**
     * Sorts the array.
     */
    public void sort() {
        int n = array.length;
        int m = ex.getParallelismLevel();
        if (m == 1 || n < SEQUENTIAL_SORT_THRESHOLD) {
            cmpArrayQuickSort(array, 0, n-1);
            return;
        }
        double[] ws = new double[n];
        ex.invoke(new FJCmpSorter(array, 0, ws, 0, n));
    }

    /** Cutoff for when to do sequential versus parallel sorts and merges */
    static final int SEQUENTIAL_SORT_THRESHOLD = 1024;

    /** Cutoff for when to use insertion-sort instead of quicksort */
    static final int INSERTION_SORT_THRESHOLD = 8;

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
    static class FJCmpSorter extends RecursiveAction {
        final double[] a;   //  to be sorted.
        final int ao;      // origin of the part of array we deal with
        final double[] w;   // workspace for merge
        final int wo;      // its origin
        final int n;       // Number of elements in (sub)arrays.
        
        FJCmpSorter(
                         double[] a, int ao, double[] w, int wo, int n) {
            this.a = a; this.ao = ao; this.w = w; this.wo = wo; this.n = n;
        }

        protected void compute()  {
            if (n <= SEQUENTIAL_SORT_THRESHOLD)
                cmpArrayQuickSort(a, ao, ao+n-1);
            else {
                int q = n >>> 2; // lower quarter index
                int h = n >>> 1; // half
                int u = h + q;   // upper quarter

                coInvoke(new FJCmpSubSorter
                         (new FJCmpSorter(a, ao,   w, wo,   q),
                          new FJCmpSorter(a, ao+q, w, wo+q, q),
                          new FJCmpMerger(a, ao,   q, ao+q, q,
                                               w, wo)),
                         new FJCmpSubSorter
                         (new FJCmpSorter(a, ao+h, w, wo+h, q),
                          new FJCmpSorter(a, ao+u, w, wo+u, n-u),
                          new FJCmpMerger(a, ao+h, q, ao+u, n-u,
                                               w, wo+h)));
                new FJCmpMerger(w, wo, h, wo+h, n-h, a, ao).compute();
            }
        }

    }

    /**
     * Helper class to run two given sorts in parallel, then merge them.
     */
    static class FJCmpSubSorter extends RecursiveAction {
        final FJCmpSorter left;
        final FJCmpSorter right;
        final FJCmpMerger merger;
        FJCmpSubSorter(FJCmpSorter left, FJCmpSorter right,
                            FJCmpMerger merger) {
            this.left = left; this.right = right; this.merger = merger;
        }
        protected void compute() {
            coInvoke(left, right);
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
    static class FJCmpMerger  extends RecursiveAction {
        final double[] a;      // partitioned  array.
        final int lo;     // relative origin of left side
        final int ln;     // number of elements on left
        final int ro;     // relative origin of right side
        final int rn;     // number of elements on right

        final double[] w;      // Output array.
        final int wo;

        FJCmpMerger(double[] a, int lo, int ln, int ro, int rn, double[] w, int wo) {
            this.a = a;
            this.w = w;
            this.wo = wo;
            // Left side should be largest of the two for finding split.
            // Swap now, since left/right doesn't otherwise matter
            if (ln >= rn) {
                this.lo = lo;    this.ln = ln;
                this.ro = ro;    this.rn = rn;
            }
            else {
                this.lo = ro;    this.ln = rn;
                this.ro = lo;    this.rn = ln;
            }
        }

        protected void compute() {
            if (ln <= SEQUENTIAL_SORT_THRESHOLD)
                merge();
            else {
                int lh = ln >>> 1;
                int ls = lo + lh;   // index of split
                double split = a[ls];
                int rl = 0;
                int rh = rn;
                while (rl < rh) {
                    int mid = (rl + rh) >>> 1;
                    if (split <= a[ro + mid])
                        rh = mid;
                    else
                        rl = mid + 1;
                }
                coInvoke
                    (new FJCmpMerger(a, lo, lh,    ro,    rh,    w, wo),
                     new FJCmpMerger(a, ls, ln-lh, ro+rh, rn-rh, w, wo+lh+rh));
            }
        }

        /** a standard sequential merge */
        void merge() {
            int l = lo;
            int lFence = lo+ln;
            int r = ro;
            int rFence = ro+rn;
            int k = wo;
            while (l < lFence && r < rFence) {
                double al = a[l];
                double ar = a[r];
                if (al <= ar) {
                    w[k++] = al;
                    ++l;
                }
                else {
                    w[k++] = ar;
                    ++r;
                }
            }
            while (l < lFence)
                w[k++] = a[l++];
            while (r < rFence)
                w[k++] = a[r++];
        }
    }

    /**
     * Sequential quicksort. Uses insertion sort if under threshold.
     * Otherwise uses median of three to pick pivot. Loops rather than
     * recurses adouble left path
     */
    static  void cmpArrayQuickSort(double[] a, int lo, int hi) {
        for (;;) { 
            if (hi - lo <= INSERTION_SORT_THRESHOLD) {
                for (int i = lo + 1; i <= hi; i++) {
                    double t = a[i];
                    int j = i - 1;
                    while (j >= lo && t < a[j]) {
                        a[j+1] = a[j];
                        --j;
                    }
                    a[j+1] = t;
                }
                return;
            }

            int mid = (lo + hi) >>> 1;
            if (a[lo] > a[mid]) {
                double t = a[lo]; a[lo] = a[mid]; a[mid] = t;
            }
            if (a[mid] > a[hi]) {
                double t = a[mid]; a[mid] = a[hi]; a[hi] = t;
                if (a[lo] > a[mid]) {
                    t = a[lo]; a[lo] = a[mid]; a[mid] = t;
                }
            }

            double pivot = a[mid];
            int left = lo+1;
            int right = hi-1;
            for (;;) {
                while (pivot < a[right])
                    --right;
                while (left < right && pivot >= a[left])
                    ++left;
                if (left < right) {
                    double t = a[left]; a[left] = a[right]; a[right] = t;
                    --right;
                }
                else break;
            }

            cmpArrayQuickSort(a, lo,    left);
            lo = left + 1;
        }
    }

    /**
     * Replaces each element with running cumulative sum.
     * @return the sum of all elements
     */
    public double cumulate() {
        int n = array.length;
        if (n == 0)
            return 0;
        if (n == 1)
            return array[0];
        int m = ex.getParallelismLevel();
        if (m == 1 || n <= 1024)
            seqCumulate(array);
        else {
            int g = defaultGranularity(m, n);
            if (g < 1024)
                g = 1024;
            FJCumulator.Ctl ctl = new FJCumulator.Ctl(array, g);
            FJCumulator r = new FJCumulator(null, ctl, 0, n);
            ex.invoke(r);
        }
        return array[n-1];
    }

    static void seqCumulate(double[] array) {
        double sum = 0;
        for (int i = 0; i < array.length; ++i)
            sum = array[i] += sum;
    }

    /**
     * Fork/Join version of scan
     *
     * A basic version of scan is straightforward.
     *  Keep dividing by two to threshold segment size, and then:
     *   Pass 1: Create tree of partial sums for each segment
     *   Pass 2: For each segment, cumulate with offset of left sibling
     * See G. Blelloch's http://www.cs.cmu.edu/~scandal/alg/scan.html
     *
     * This version improves performance within FJ framework:
     * a) It allows second pass of ready left-hand sides to proceed even 
     *    if some right-hand side first passes are still executing.
     * b) It collapses the first and second passes of segments for which
     *    incoming cumulations are ready before summing.
     * c) It skips first pass for rightmost segment (whose
     *    result is not needed for second pass).
     *
     */
    static final class FJCumulator extends AsyncAction {

        /**
         * Shared control across nodes
         */
        static final class Ctl {
            final double[] array;
            final int granularity;
            /**
             * The index of the max current consecutive
             * cumulation starting from leftmost. Initially zero.
             */
            volatile int consecutiveIndex;
            /**
             * The current consecutive cumulation
             */
            double consecutiveSum;

            Ctl(double[] array, int granularity) {
                this.array = array;
                this.granularity = granularity;
            }
        }

        final FJCumulator parent;
        final FJCumulator.Ctl ctl;
        FJCumulator left, right;
        final int lo;
        final int hi;

        /** Incoming cumulative sum */
        double in;

        /** Sum of this subtree */
        double out;
        
        /**
         * Phase/state control, updated only via transitionTo, for
         * CUMULATE, SUMMED, and FINISHED bits.
         */
        volatile int phase;

        /**
         * Phase bit. When false, segments compute only their sum.
         * When true, they cumulate array elements. CUMULATE is set at
         * root at beginning of second pass and then propagated
         * down. But it may also be set earlier in two cases when
         * cumulations are known to be ready: (1) For subtrees with
         * lo==0 (the left spine of tree) (2) Leaf nodes with
         * completed predecessors.
         */
        static final int CUMULATE = 1;

        /**
         * One bit join count. For leafs, set when summed. For
         * internal nodes, becomes true when one child is summed.
         * When second child finishes summing, it then moves up tree
         * to trigger cumulate phase.
         */
        static final int SUMMED   = 2;

        /**
         * One bit join count. For leafs, set when cumulated. For
         * internal nodes, becomes true when one child is cumulated.
         * When second child finishes cumulating, it then moves up
         * tree, excecuting finish() at the root.
         */
        static final int FINISHED = 4;

        static final AtomicIntegerFieldUpdater<FJCumulator> phaseUpdater =
            AtomicIntegerFieldUpdater.newUpdater(FJCumulator.class, "phase");

        FJCumulator(FJCumulator parent,
                    FJCumulator.Ctl ctl,
                    int lo, 
                    int hi) {
            this.parent = parent;
            this.ctl = ctl;
            this.lo = lo; 
            this.hi = hi;
        }

        public void compute() {
            if (hi - lo <= ctl.granularity) {
                int cb = establishLeafPhase();
                leafSum(cb);
                propagateLeafPhase(cb);
            }
            else
                spawnTasks();
        }
        
        /**
         * decide which leaf action to take - sum, cumulate, or both
         * @return associated bit s
         */
        int establishLeafPhase() {
            for (;;) {
                int b = phase;
                if ((b & FINISHED) != 0) // already done
                    return 0;
                int cb;
                if ((b & CUMULATE) != 0)
                    cb = FINISHED;
                else if (lo == ctl.consecutiveIndex)
                    cb = (SUMMED|FINISHED);
                else 
                    cb = SUMMED;
                if (phaseUpdater.compareAndSet(this, b, b|cb))
                    return cb;
            }
        }

        void propagateLeafPhase(int cb) {
            FJCumulator c = this;
            FJCumulator p = parent;
            for (;;) {
                if (p == null) {
                    if ((cb & FINISHED) != 0)
                        c.finish();
                    break;
                }
                int pb = p.phase;
                if ((pb & cb & FINISHED) != 0) { // both finished
                    c = p;
                    p = p.parent;
                }
                else if ((pb & cb & SUMMED) != 0) { // both summed
                    int refork = 0;
                    if ((pb & CUMULATE) == 0 && p.lo == 0)
                        refork = CUMULATE;
                    int next = pb|cb|refork;
                    if (pb == next || 
                        phaseUpdater.compareAndSet(p, pb, next)) {
                        if (refork != 0)
                            p.fork();
                        cb = SUMMED; // drop finished bit
                        c = p;
                        p = p.parent;
                    }
                }
                else if (phaseUpdater.compareAndSet(p, pb, pb|cb))
                    break;
            }
        }

        void leafSum(int cb) {
            double[] array = ctl.array;
            if (cb == SUMMED) {
                if (hi < array.length) { // skip rightmost
                    double sum = 0;
                    for (int i = lo; i < hi; ++i)
                        sum += array[i];
                    out = sum;
                }
            }
            else if (cb == FINISHED) {
                double sum = in;
                for (int i = lo; i < hi; ++i)
                    sum = array[i] += sum;
            }
            else if (cb == (SUMMED|FINISHED)) {
                double cin = ctl.consecutiveSum;
                double sum = cin;
                for (int i = lo; i < hi; ++i)
                    sum = array[i] += sum;
                out = sum - cin;
                ctl.consecutiveSum = sum;
                ctl.consecutiveIndex = hi;
            }
        }

        /**
         * Returns true if can CAS CUMULATE bit true
         */
        boolean transitionToCumulate() {
            int c;
            while (((c = phase) & CUMULATE) == 0)
                if (phaseUpdater.compareAndSet(this, c, c | CUMULATE))
                    return true;
            return false;
        }

        void spawnTasks() {
            if (left == null) {
                int mid = (lo + hi) >>> 1;
                left =  new FJCumulator(this, ctl, lo, mid);
                right = new FJCumulator(this, ctl, mid, hi);
            }

            boolean cumulate = (phase & CUMULATE) != 0;
            if (cumulate) { // push down sums
                double cin = in;
                left.in = cin;
                right.in = cin + left.out;
            }

            if (!cumulate || right.transitionToCumulate())
                right.fork();
            if (!cumulate || left.transitionToCumulate())
                left.compute();
        }

    }

}
