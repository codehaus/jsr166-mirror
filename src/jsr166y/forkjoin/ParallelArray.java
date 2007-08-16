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
 * <em>map</em> each element to a new element, to <em>select</em> a
 * subset of elements based on matching a predicate or ranges of
 * indices, and to <em>reduce</em> all elements into a single value
 * such as a sum. For both convenience and efficiency, this class
 * support methods that allow common combinations of these to be
 * performed in a single (parallel) step. So you can for example
 * reduce the mappings of all elements within a certain index range
 * that match some predicate.
 * 
 * <p>A ParallelArray is not a List, but can be viewed as one (via
 * method {@link #asList}, or created from one (by constructing from a
 * list's <tt>toArray</tt>). Arrays differ from lists in that they do
 * not incrementally grow or shrink. Random accessiblity across all
 * elements permits efficient parallel operation.
 *
 * <p>While ParallelArrays can be based on any kind of an object
 * array, including "boxed" types such as Integer, parallel operations
 * on scalar "unboxed" type are likely to be substantially more
 * efficient. For this reason, classes {@link ParallelIntArray},
 * {@link ParallelLongArray}, and {@link ParallelDoubleArray} are also
 * supplied, and designed to smoothly interoperate with
 * ParallelArrays.  (Other scalar types such as <tt>short</tt> are not
 * often enough useful to further integrate them.)
 * 
 * <p>The methods in this are designed to perform efficiently with
 * both large and small pools, even with single-thread pools on
 * uniprocessors.  However, there is some overhead in parallelizing
 * operations, so short computations on small arrays might not execute
 * faster than sequential versions.
 *
 * <p>Accesses by other threads of the elements of a ParallelArray
 * while an aggregate operation is in progress have undefined effects.
 * Don't do this.
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
 *     return students.reduce(isSenior, gpaField, DoubleMaxReducer.max, 0.0);
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
 */
public class ParallelArray<T> implements Iterable<T> {
    private final T[] array;
    private final ForkJoinExecutor ex;

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
                         Class<T> elementType) {
        if (executor == null)
            throw new NullPointerException();
        this.ex = executor;
        this.array = 
            (T[])java.lang.reflect.Array.newInstance(elementType, size);
    }

    /**
     * Creates a new ParallelArray using the given executor and an
     * array of the given size containing the mappings of each
     * consecutive integer from zero to <tt>size-1</tt>.
     * @param executor the executor
     * @param size the array size
     * @param mapper the mapper
     */
    public ParallelArray(ForkJoinExecutor executor, int size,
                         MapperFromInt<? extends T> mapper) {
        if (executor == null)
            throw new NullPointerException();
        this.ex = executor;
        this.array = (T[])new Object[size];
        int m = executor.getParallelismLevel();
        int g = defaultGranularity(m, size);
        if (size > 0)
            executor.invoke(new FJGenerator<T>(array, mapper, 0, size, g));
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
     * default granularity for divide-by-two array tasks.
     */
    static int defaultGranularity(int threads, int n) {
        return (threads > 1)? (1 + n / (threads << 4)) : n;
    }

    /**
     * Check that fromIndex and toIndex are in range, and throw an
     * appropriate exception if they aren't, otherwise return length.
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
    static abstract class FJOp<T> {
        final int granularity;
        final T[] array;
        FJOp(int granularity,
             T[] array) {
            this.granularity = granularity;
            this.array = array;
        }
    }

    /**
     * Base for apply actions
     */
    static abstract class FJApplyOp<T,U> extends FJOp<T> {
        final Procedure<? super U> proc;
        FJApplyOp(int granularity,
                  T[] array,
                  Procedure<? super U> proc) {
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
    static final class FJApplyer<T,U> extends RecursiveAction {
        final FJApplyOp<T,U> p;
        final int lo;
        final int hi;
        FJApplyer<T,U> next;
        FJApplyer(FJApplyOp<T,U> p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJApplyer<T,U> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJApplyer<T,U> r =
                    new FJApplyer<T,U>(p, mid, h);
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
    public void apply(Procedure<? super T> proc) {
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
                      Procedure<? super T> proc) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJPlainApplyOp<T> p =
            new FJPlainApplyOp<T>(g, array, proc);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJApplyer<T,T>(p, fromIndex, toIndex));
    }

    static final class FJPlainApplyOp<T> extends FJApplyOp<T,T> {
        FJPlainApplyOp(int granularity,
                       T[] array,
                       Procedure<? super T> proc) {
            super(granularity, array, proc);
        }

        void leafAction(int lo, int hi) {
            for (int i = lo; i < hi; ++i)
                proc.apply((array[i]));
        }
    }

    /**
     * Applies the given procedure to each element of the array
     * for which the given selector returns true
     * @param selector the predicate
     * @param proc the procedure
     */
    public void apply(Predicate<? super T> selector,
                      Procedure<? super T> proc) {
        // evade resolution bug
        int n = array.length;
        int fromIndex = 0;
        int toIndex = n;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectApplyOp<T> p =
            new FJSelectApplyOp<T>(g, array, proc, selector);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJApplyer<T,T>(p, fromIndex, toIndex));
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
                      Predicate<? super T> selector,
                      Procedure<? super T> proc) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectApplyOp<T> p =
            new FJSelectApplyOp<T>(g, array, proc, selector);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJApplyer<T,T>(p, fromIndex, toIndex));
    }

    static final class FJMapApplyOp<T,U> extends FJApplyOp<T,U> {
        final Mapper<? super T, ? extends U> mapper;
        FJMapApplyOp(int granularity,
                     T[] array,
                     Procedure<? super U> proc,
                     Mapper<? super T, ? extends U> mapper) {
            super(granularity, array, proc);
            this.mapper = mapper;
        }

        void leafAction(int lo, int hi) {
            for (int i = lo; i < hi; ++i)
                proc.apply(mapper.map(array[i]));
        }
    }

    /**
     * Applies the given procedure to the objects mapped
     * from each element of the array.
     * @param mapper the mapper
     * @param proc the procedure
     */
    public <U> void apply(Mapper<? super T, ? extends U> mapper,
                          Procedure<? super U> proc) {
        apply(0, array.length, mapper, proc);
    }

    /**
     * Applies the given procedure to the objects mapped
     * from each element of the array from fromIndex to toIndex
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param mapper the mapper
     * @param proc the procedure
     */
    public <U> void apply(int fromIndex, int toIndex,
                          Mapper<? super T, ? extends U> mapper,
                          Procedure<? super U> proc) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJMapApplyOp<T,U> p =
            new FJMapApplyOp<T,U>(g, array, proc, mapper);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJApplyer<T,U>(p, fromIndex, toIndex));
    }

    static final class FJSelectApplyOp<T> extends FJApplyOp<T,T> {
        final Predicate<? super T> pred;
        FJSelectApplyOp(int granularity,
                        T[] array,
                        Procedure<? super T> proc,
                        Predicate<? super T> pred) {
            super(granularity, array, proc);
            this.pred = pred;
        }

        void leafAction(int lo, int hi) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (pred.evaluate(x))
                    proc.apply(x);
            }
        }
    }

    /**
     * Applies the given procedure to the objects mapped
     * from each element of the array for which the selector returns true.
     * @param selector the predicate
     * @param mapper the mapper
     * @param proc the procedure
     */
    public <U> void apply(Predicate<? super T> selector,
                          Mapper<? super T, ? extends U> mapper,
                          Procedure<? super U> proc) {
        // evade resolution bug
        int n = array.length;
        int fromIndex = 0;
        int toIndex = n;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectMapApplyOp<T,U> p =
            new FJSelectMapApplyOp<T,U>(g, array, proc,selector,mapper);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJApplyer<T,U>(p, fromIndex, toIndex));
    }

    /**
     * Applies the given procedure to the objects mapped
     * from each element of the array for which the selector returns true.
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param selector the predicate
     * @param mapper the mapper
     * @param proc the procedure
     */
    public <U> void apply(int fromIndex, int toIndex,
                          Predicate<? super T> selector,
                          Mapper<? super T, ? extends U> mapper,
                          Procedure<? super U> proc) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectMapApplyOp<T,U> p =
            new FJSelectMapApplyOp<T,U>(g, array, proc,selector,mapper);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJApplyer<T,U>(p, fromIndex, toIndex));
    }

    static final class FJSelectMapApplyOp<T,U> extends FJApplyOp<T,U> {
        final Mapper<? super T, ? extends U> mapper;
        final Predicate<? super T> pred;
        FJSelectMapApplyOp(int granularity,
                           T[] array,
                           Procedure<? super U> proc,
                           Predicate<? super T> pred,
                           Mapper<? super T, ? extends U> mapper) {
            super(granularity, array, proc);
            this.pred = pred;
            this.mapper = mapper;
        }

        void leafAction(int lo, int hi) {
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (pred.evaluate(x))
                    proc.apply(mapper.map(x));
            }
        }
    }

    /**
     * Base for reduce operations
     */
    static abstract class FJReduceOp<T,U> extends FJOp<T> {
        final Reducer<U> reducer;
        final U base;
        FJReduceOp(int granularity,
                   T[] array,
                   Reducer<U> reducer,
                   U base) {
            super(granularity, array);
            this.reducer = reducer;
            this.base = base;
        }

        abstract U leafAction(int lo, int hi);
    }

    /**
     * Fork/Join version of reduce
     */
    static final class FJReducer<T,U> extends RecursiveAction {
        final FJReduceOp<T,U> p;
        final int lo;
        final int hi;
        U result;
        FJReducer<T,U> next;

        FJReducer(FJReduceOp<T,U> p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJReducer<T,U> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJReducer<T,U> r = new FJReducer<T,U>(p, mid, h);
                r.next = right;
                right = r;
                right.fork();
                h = mid;
            }
            U x = p.leafAction(l, h);
            Reducer<U> reducer = p.reducer;
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
    public T reduce(Reducer<T> reducer,
                    T base) {
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
    public T reduce(int fromIndex, int toIndex,
                    Reducer<T> reducer,
                    T base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJPlainReduceOp<T> p =
            new FJPlainReduceOp<T>(g, array, reducer, base);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);

        FJReducer<T,T> f =
            new FJReducer<T,T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJPlainReduceOp<T> extends FJReduceOp<T,T> {
        FJPlainReduceOp(int granularity,
                        T[] array,
                        Reducer<T> reducer,
                        T base) {
            super(granularity, array, reducer, base);
        }

        T leafAction(int lo, int hi) {
            boolean gotFirst = false;
            T r = base;
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
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
    public T reduce(Predicate<? super T> selector,
                    Reducer<T> reducer,
                    T base) {
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
    public T reduce(int fromIndex, int toIndex,
                    Predicate<? super T> selector,
                    Reducer<T> reducer,
                    T base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectReduceOp<T> p =
            new FJSelectReduceOp<T>(g, array, reducer, base, selector);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJReducer<T,T> f =
            new FJReducer<T,T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJSelectReduceOp<T> extends FJReduceOp<T,T> {
        final Predicate<? super T> pred;
        FJSelectReduceOp(int granularity,
                         T[] array,
                         Reducer<T> reducer,
                         T base,
                         Predicate<? super T> pred) {
            super(granularity, array, reducer, base);
            this.pred = pred;
        }

        T leafAction(int lo, int hi) {
            boolean gotFirst = false;
            T r = base;
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
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
     * Applies mapper to each element of array and reduces result
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public <U> U reduce(Mapper<? super T, ? extends U> mapper,
                        Reducer<U> reducer,
                        U base) {
        return reduce(0, array.length, mapper, reducer, base);
    }

    /**
     * Applies mapper to each element of array from fromIndex to
     * toIndex and reduces result
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public <U> U reduce(int fromIndex, int toIndex,
                        Mapper<? super T, ? extends U> mapper,
                        Reducer<U> reducer,
                        U base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJMapReduceOp<T,U> p =
            new FJMapReduceOp<T,U>(g, array, reducer, base, mapper);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJReducer<T,U> f =
            new FJReducer<T,U>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJMapReduceOp<T,U> extends FJReduceOp<T,U> {
        final Mapper<? super T, ? extends U> mapper;
        FJMapReduceOp(int granularity,
                      T[] array,
                      Reducer<U> reducer,
                      U base,
                      Mapper<? super T, ? extends U> mapper) {
            super(granularity, array, reducer, base);
            this.mapper = mapper;
        }

        U leafAction(int lo, int hi) {
            boolean gotFirst = false;
            U r = base;
            for (int i = lo; i < hi; ++i) {
                U x = mapper.map(array[i]);
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
     * Returns reduction of the mapped elements of the array for which
     * the selector returns true;
     * @param selector the predicate
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public <U> U reduce(Predicate<? super T> selector,
                        Mapper<? super T, ? extends U> mapper,
                        Reducer<U> reducer,
                        U base) {
        // evade resolution bug
        int n = array.length;
        int fromIndex = 0;
        int toIndex = n;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectMapReduceOp<T,U> p =
            new FJSelectMapReduceOp<T,U>(g, array, reducer, base,
                                         selector, mapper);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJReducer<T,U> f =
            new FJReducer<T,U>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    /**
     * Returns reduction of the mapped elements of the array for which
     * the selector returns true;
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param selector the predicate
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public <U> U reduce(int fromIndex, int toIndex,
                        Predicate<? super T> selector,
                        Mapper<? super T, ? extends U> mapper,
                        Reducer<U> reducer,
                        U base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectMapReduceOp<T,U> p =
            new FJSelectMapReduceOp<T,U>(g, array, reducer, base,
                                         selector, mapper);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJReducer<T,U> f =
            new FJReducer<T,U>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJSelectMapReduceOp<T,U> extends FJReduceOp<T,U> {
        final Mapper<? super T, ? extends U> mapper;
        final Predicate<? super T> pred;
        FJSelectMapReduceOp(int granularity,
                            T[] array,
                            Reducer<U> reducer,
                            U base,
                            Predicate<? super T> pred,
                            Mapper<? super T, ? extends U> mapper) {
            super(granularity, array, reducer, base);
            this.pred = pred;
            this.mapper = mapper;
        }

        U leafAction(int lo, int hi) {
            boolean gotFirst = false;
            U r = base;
            for (int i = lo; i < hi; ++i) {
                T x = array[i];
                if (pred.evaluate(x)) {
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
    }

    /**
     * Base for int reduce operations
     */
    static abstract class FJIntReduceOp<T> extends FJOp<T> {
        final MapperToInt<? super T> mapper;
        final IntReducer reducer;
        final int base;
        FJIntReduceOp(int granularity,
                      T[] array,
                      IntReducer reducer,
                      int base,
                      MapperToInt<? super T> mapper) {
            super(granularity, array);
            this.reducer = reducer;
            this.base = base;
            this.mapper = mapper;
        }

        abstract int leafAction(int lo, int hi);
    }

    /**
     * Fork/Join version of int reduce
     */
    static final class FJIntReducer<T> extends RecursiveAction {
        final FJIntReduceOp<T> p;
        final int lo;
        final int hi;
        int result;
        FJIntReducer<T> next;

        FJIntReducer(FJIntReduceOp<T> p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJIntReducer<T> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJIntReducer<T> r = new FJIntReducer<T>(p, mid, h);
                r.next = right;
                right = r;
                right.fork();
                h = mid;
            }
            int x = p.leafAction(l, h);
            IntReducer reducer = p.reducer;
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
     * Applies mapper to each element of array and reduces result
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public int reduce(MapperToInt<? super T> mapper,
                      IntReducer reducer,
                      int base) {
        return reduce(0, array.length, mapper, reducer, base);
    }

    /**
     * Applies mapper to each element of array from fromIndex to
     * toIndex and reduces result
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public int reduce(int fromIndex, int toIndex,
                      MapperToInt<? super T> mapper,
                      IntReducer reducer,
                      int base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJPlainIntReduceOp<T> p =
            new FJPlainIntReduceOp<T>(g, array, reducer, base, mapper);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJIntReducer<T> f =
            new FJIntReducer<T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJPlainIntReduceOp<T> extends FJIntReduceOp<T> {
        FJPlainIntReduceOp(int granularity,
                           T[] array,
                           IntReducer reducer,
                           int base,
                           MapperToInt<? super T> mapper) {
            super(granularity, array, reducer, base, mapper);
        }

        int leafAction(int lo, int hi) {
            boolean gotFirst = false;
            int r = base;
            for (int i = lo; i < hi; ++i) {
                int y = mapper.map(array[i]);
                if (!gotFirst) {
                    gotFirst = true;
                    r = y;
                }
                else
                    r = reducer.combine(r, y);
            }
            return r;
        }
    }

    /**
     * Applies mapper to each element of array for which the
     * selector returns true, and reduces result
     * @param selector the predicate
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public int reduce(Predicate<? super T> selector,
                      MapperToInt<? super T> mapper,
                      IntReducer reducer,
                      int base) {
        // evade resolution problems
        int n = array.length;
        int fromIndex = 0;
        int toIndex = n;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectIntReduceOp<T> p =
            new FJSelectIntReduceOp<T>(g, array, reducer, base,
                                       mapper, selector);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJIntReducer<T> f =
            new FJIntReducer<T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    /**
     * Applies mapper to each element of array from fromIndex to
     * toIndex for which the selector returns true, and reduces result
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param selector the predicate
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public int reduce(int fromIndex, int toIndex,
                      Predicate<? super T> selector,
                      MapperToInt<? super T> mapper,
                      IntReducer reducer,
                      int base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectIntReduceOp<T> p =
            new FJSelectIntReduceOp<T>(g, array, reducer, base,
                                       mapper, selector);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJIntReducer<T> f =
            new FJIntReducer<T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJSelectIntReduceOp<T> extends FJIntReduceOp<T> {
        final Predicate<? super T> pred;
        FJSelectIntReduceOp(int granularity,
                            T[] array,
                            IntReducer reducer,
                            int base,
                            MapperToInt<? super T> mapper,
                            Predicate<? super T> pred) {
            super(granularity, array, reducer, base, mapper);
            this.pred = pred;
        }

        int leafAction(int lo, int hi) {
            boolean gotFirst = false;
            int r = base;
            for (int i = lo; i < hi; ++i) {
                T t = array[i];
                if (pred.evaluate(t)) {
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
    }

    /**
     * Base for long reduce operations
     */
    static abstract class FJLongReduceOp<T> extends FJOp<T> {
        final MapperToLong<? super T> mapper;
        final LongReducer reducer;
        final long base;
        FJLongReduceOp(int granularity,
                       T[] array,
                       LongReducer reducer,
                       long base,
                       MapperToLong<? super T> mapper) {
            super(granularity, array);
            this.reducer = reducer;
            this.base = base;
            this.mapper = mapper;
        }

        abstract long leafAction(int lo, int hi);
    }

    /**
     * Fork/Join version of long reduce
     */
    static final class FJLongReducer<T> extends RecursiveAction {
        final FJLongReduceOp<T> p;
        final int lo;
        final int hi;
        long result;
        FJLongReducer<T> next;

        FJLongReducer(FJLongReduceOp<T> p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJLongReducer<T> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJLongReducer<T> r = new FJLongReducer<T>(p, mid, h);
                r.next = right;
                right = r;
                right.fork();
                h = mid;
            }
            long x = p.leafAction(l, h);
            LongReducer reducer = p.reducer;
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
     * Applies mapper to each element of array and reduces result
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public long reduce(MapperToLong<? super T> mapper,
                       LongReducer reducer,
                       long base) {
        return reduce(0, array.length, mapper, reducer, base);
    }

    /**
     * Applies mapper to each element of array from fromIndex to
     * toIndex and reduces result
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public long reduce(int fromIndex, int toIndex,
                       MapperToLong<? super T> mapper,
                       LongReducer reducer,
                       long base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJPlainLongReduceOp<T> p =
            new FJPlainLongReduceOp<T>(g, array, reducer, base, mapper);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJLongReducer<T> f =
            new FJLongReducer<T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJPlainLongReduceOp<T> extends FJLongReduceOp<T> {
        FJPlainLongReduceOp(int granularity,
                            T[] array,
                            LongReducer reducer,
                            long base,
                            MapperToLong<? super T> mapper) {
            super(granularity, array, reducer, base, mapper);
        }

        long leafAction(int lo, int hi) {
            boolean gotFirst = false;
            long r = base;
            for (int i = lo; i < hi; ++i) {
                long y = mapper.map(array[i]);
                if (!gotFirst) {
                    gotFirst = true;
                    r = y;
                }
                else
                    r = reducer.combine(r, y);
            }
            return r;
        }
    }

    /**
     * Applies mapper to each element of array for which the
     * selector returns true, and reduces result
     * @param selector the predicate
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public long reduce(Predicate<? super T> selector,
                       MapperToLong<? super T> mapper,
                       LongReducer reducer,
                       long base) {
        // evade resolution problems
        int n = array.length;
        int fromIndex = 0;
        int toIndex = n;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectLongReduceOp<T> p =
            new FJSelectLongReduceOp<T>(g, array, reducer, base,
                                        mapper, selector);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJLongReducer<T> f =
            new FJLongReducer<T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    /**
     * Applies mapper to each element of array from fromIndex to
     * toIndex for which the selector returns true, and reduces result
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param selector the predicate
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public long reduce(int fromIndex, int toIndex,
                       Predicate<? super T> selector,
                       MapperToLong<? super T> mapper,
                       LongReducer reducer,
                       long base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectLongReduceOp<T> p =
            new FJSelectLongReduceOp<T>(g, array, reducer, base,
                                        mapper, selector);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJLongReducer<T> f =
            new FJLongReducer<T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJSelectLongReduceOp<T> extends FJLongReduceOp<T> {
        final Predicate<? super T> pred;
        FJSelectLongReduceOp(int granularity,
                             T[] array,
                             LongReducer reducer,
                             long base,
                             MapperToLong<? super T> mapper,
                             Predicate<? super T> pred) {
            super(granularity, array, reducer, base, mapper);
            this.pred = pred;
        }

        long leafAction(int lo, int hi) {
            boolean gotFirst = false;
            long r = base;
            for (int i = lo; i < hi; ++i) {
                T t = array[i];
                if (pred.evaluate(t)) {
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
    }

    /**
     * Base for double reduce operations
     */
    static abstract class FJDoubleReduceOp<T> extends FJOp<T> {
        final MapperToDouble<? super T> mapper;
        final DoubleReducer reducer;
        final double base;
        FJDoubleReduceOp(int granularity,
                         T[] array,
                         DoubleReducer reducer,
                         double base,
                         MapperToDouble<? super T> mapper) {
            super(granularity, array);
            this.reducer = reducer;
            this.base = base;
            this.mapper = mapper;
        }

        abstract double leafAction(int lo, int hi);
    }

    /**
     * Fork/Join version of double reduce
     */
    static final class FJDoubleReducer<T> extends RecursiveAction {
        final FJDoubleReduceOp<T> p;
        final int lo;
        final int hi;
        double result;
        FJDoubleReducer<T> next;

        FJDoubleReducer(FJDoubleReduceOp<T> p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJDoubleReducer<T> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJDoubleReducer<T> r = new FJDoubleReducer<T>(p, mid, h);
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
     * Applies mapper to each element of array and reduces result
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public double reduce(MapperToDouble<? super T> mapper,
                         DoubleReducer reducer,
                         double base) {
        return reduce(0, array.length, mapper, reducer, base);
    }

    /**
     * Applies mapper to each element of array from fromIndex to
     * toIndex and reduces result
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public double reduce(int fromIndex, int toIndex,
                         MapperToDouble<? super T> mapper,
                         DoubleReducer reducer,
                         double base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJPlainDoubleReduceOp<T> p =
            new FJPlainDoubleReduceOp<T>(g, array, reducer, base, mapper);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJDoubleReducer<T> f =
            new FJDoubleReducer<T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJPlainDoubleReduceOp<T> extends FJDoubleReduceOp<T> {
        FJPlainDoubleReduceOp(int granularity,
                              T[] array,
                              DoubleReducer reducer,
                              double base,
                              MapperToDouble<? super T> mapper) {
            super(granularity, array, reducer, base, mapper);
        }

        double leafAction(int lo, int hi) {
            boolean gotFirst = false;
            double r = base;
            for (int i = lo; i < hi; ++i) {
                double y = mapper.map(array[i]);
                if (!gotFirst) {
                    gotFirst = true;
                    r = y;
                }
                else
                    r = reducer.combine(r, y);
            }
            return r;
        }
    }

    /**
     * Applies mapper to each element of array for which the
     * selector returns true, and reduces result
     * @param selector the predicate
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public double reduce(Predicate<? super T> selector,
                         MapperToDouble<? super T> mapper,
                         DoubleReducer reducer,
                         double base) {
        // evade resolution problems
        int n = array.length;
        int fromIndex = 0;
        int toIndex = n;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectDoubleReduceOp<T> p =
            new FJSelectDoubleReduceOp<T>(g, array, reducer, base,
                                          mapper, selector);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJDoubleReducer<T> f =
            new FJDoubleReducer<T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;

    }

    /**
     * Applies mapper to each element of array from fromIndex to
     * toIndex for which the selector returns true, and reduces result
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param selector the predicate
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @return reduction
     */
    public double reduce(int fromIndex, int toIndex,
                         Predicate<? super T> selector,
                         MapperToDouble<? super T> mapper,
                         DoubleReducer reducer,
                         double base) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectDoubleReduceOp<T> p =
            new FJSelectDoubleReduceOp<T>(g, array, reducer, base,
                                          mapper, selector);
        if (m == 1 || n <= 1)
            return p.leafAction(fromIndex, toIndex);
        FJDoubleReducer<T> f =
            new FJDoubleReducer<T>(p, fromIndex, toIndex);
        ex.invoke(f);
        return f.result;
    }

    static final class FJSelectDoubleReduceOp<T> extends FJDoubleReduceOp<T> {
        final Predicate<? super T> pred;
        FJSelectDoubleReduceOp(int granularity,
                               T[] array,
                               DoubleReducer reducer,
                               double base,
                               MapperToDouble<? super T> mapper,
                               Predicate<? super T> pred) {
            super(granularity, array, reducer, base, mapper);
            this.pred = pred;
        }

        double leafAction(int lo, int hi) {
            boolean gotFirst = false;
            double r = base;
            for (int i = lo; i < hi; ++i) {
                T t = array[i];
                if (pred.evaluate(t)) {
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
    }

    /**
     * Base for map actions
     */
    static final class FJMapOp<T,U> extends FJOp<T> {
        final Mapper<? super T, ? extends U> mapper;
        final U[] dest;
        FJMapOp(int granularity,
                T[] array,
                Mapper<? super T, ? extends U> mapper,
                U[] dest) {
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
    static final class FJMap<T,U> extends RecursiveAction {
        final FJMapOp<T,U> p;
        final int lo;
        final int hi;
        FJMap<T,U> next;

        FJMap(FJMapOp<T,U> p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJMap<T,U> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMap<T,U> r = new FJMap<T,U>(p, mid, h);
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
    public <U> ParallelArray<U> map(Mapper<? super T, ? extends U> mapper) {
        return map(0, array.length, mapper);
    }

    /**
     * Returns an array mapping each element of given array using mapper
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param mapper the mapper
     * @return the array of mappings
     */
    public <U> ParallelArray<U> map(int fromIndex, int toIndex,
                                    Mapper<? super T, ? extends U> mapper) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        U[] dest = (U[])new Object[n];
        FJMapOp<T,U> p = new FJMapOp<T,U>(g, array, mapper, dest);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJMap<T,U>(p, fromIndex, toIndex));
        return new ParallelArray<U>(ex, dest);
    }

    /**
     * Base for int map actions
     */
    static final class FJIntMapOp<T> extends FJOp<T> {
        final MapperToInt<? super T> mapper;
        final int[] dest;
        FJIntMapOp(int granularity,
                   T[] array,
                   MapperToInt<? super T> mapper,
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
    static final class FJIntMap<T> extends RecursiveAction {
        final FJIntMapOp<T> p;
        final int lo;
        final int hi;
        FJIntMap<T> next;

        FJIntMap(FJIntMapOp<T> p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJIntMap<T> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJIntMap<T> r = new FJIntMap<T>(p, mid, h);
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
    public ParallelIntArray map(MapperToInt<? super T> mapper) {
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
                                MapperToInt<? super T> mapper) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        int[] dest = new int[n];
        FJIntMapOp<T> p = new FJIntMapOp<T>(g, array, mapper, dest);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJIntMap<T>(p, fromIndex, toIndex));
        return new ParallelIntArray(ex, dest);
    }

    /**
     * Base for long map actions
     */
    static final class FJLongMapOp<T> extends FJOp<T> {
        final MapperToLong<? super T> mapper;
        final long[] dest;
        FJLongMapOp(int granularity,
                    T[] array,
                    MapperToLong<? super T> mapper,
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
    static final class FJLongMap<T> extends RecursiveAction {
        final FJLongMapOp<T> p;
        final int lo;
        final int hi;
        FJLongMap<T> next;

        FJLongMap(FJLongMapOp<T> p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJLongMap<T> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJLongMap<T> r = new FJLongMap<T>(p, mid, h);
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
    public ParallelLongArray map(MapperToLong<? super T> mapper) {
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
                                 MapperToLong<? super T> mapper) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        long[] dest = new long[n];
        FJLongMapOp<T> p = new FJLongMapOp<T>(g, array, mapper, dest);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJLongMap<T>(p, fromIndex, toIndex));
        return new ParallelLongArray(ex, dest);
    }

    /**
     * Base for double map actions
     */
    static final class FJDoubleMapOp<T> extends FJOp<T> {
        final MapperToDouble<? super T> mapper;
        final double[] dest;
        FJDoubleMapOp(int granularity,
                      T[] array,
                      MapperToDouble<? super T> mapper,
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
     * Fork/Join version of ArrayDoubleMap
     */
    static final class FJDoubleMap<T> extends RecursiveAction {
        final FJDoubleMapOp<T> p;
        final int lo;
        final int hi;
        FJDoubleMap<T> next;

        FJDoubleMap(FJDoubleMapOp<T> p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            FJDoubleMap<T> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJDoubleMap<T> r = new FJDoubleMap<T>(p, mid, h);
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
    public ParallelDoubleArray map(MapperToDouble<? super T> mapper) {
        return map(0, array.length, mapper);
    }

    /**
     * Returns an array mapping each element of given array using mapper
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param mapper the mapper
     * @return the array of mappings
     */
    public ParallelDoubleArray map(int fromIndex, int toIndex,
                                   MapperToDouble<? super T> mapper) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        double[] dest = new double[n];
        FJDoubleMapOp<T> p = new FJDoubleMapOp<T>(g, array, mapper, dest);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJDoubleMap<T>(p, fromIndex, toIndex));
        return new ParallelDoubleArray(ex, dest);
    }

    /**
     * Fork/Join version of generate
     */
    static final class FJGenerator<T> extends RecursiveAction {
        final T[] dest;
        final MapperFromInt<? extends T> mapper;
        final int lo;
        final int hi;
        final int gran;
        FJGenerator<T> next;

        FJGenerator(T[] dest,
                    MapperFromInt<? extends T> mapper,
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
            FJGenerator<T> right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJGenerator<T> r =
                    new FJGenerator<T>(dest, mapper, mid, h, g);
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
    static final class FJIndexOp<T> extends FJOp<T> {
        final Predicate<? super T> pred;
        final AtomicInteger result;
        FJIndexOp(int granularity,
                  T[] array,
                  Predicate<? super T> pred) {
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
    public int indexOf(Predicate<? super T> pred) {
        // evade resolution problems
        int n = array.length;
        int fromIndex = 0;
        int toIndex = n;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJIndexOp<T> p =
            new FJIndexOp<T>(g, array, pred);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJIndexOf<T>(p, fromIndex, toIndex));
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
                       Predicate<? super T> pred) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJIndexOp<T> p =
            new FJIndexOp<T>(g, array, pred);
        if (m == 1 || n <= 1)
            p.leafAction(fromIndex, toIndex);
        else
            ex.invoke(new FJIndexOf<T>(p, fromIndex, toIndex));
        return p.result.get();
    }

    /**
     * Fork/Join version of IndexOf
     */
    static final class FJIndexOf<T> extends RecursiveAction {
        final FJIndexOp<T> p;
        final int lo;
        final int hi;
        FJIndexOf<T> next;

        FJIndexOf(FJIndexOp<T> p, int lo, int hi) {
            this.p = p;
            this.lo = lo;
            this.hi = hi;
        }

        protected void compute() {
            AtomicInteger res = p.result;
            if (res.get() >= 0)
                return;
            FJIndexOf<T> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJIndexOf<T> r = new FJIndexOf<T>(p, mid, h);
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
     * Returns the minimum of all elements, or null if empty
     * @param comparator the comparator
     * @return minimum of all elements, or null if empty
     */
    public T min(Comparator<? super T> comparator) {
        return reduce(new MinReducer<T>(comparator), null);
    }

    /**
     * Returns the minimum of elements from fromIndex to toIndex, or
     * null if empty
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param comparator the comparator
     * @return minimum element, or null if empty
     */
    public T min(int fromIndex, int toIndex,
                 Comparator<? super T> comparator) {
        return reduce(fromIndex, toIndex, new MinReducer<T>(comparator), null);
    }

    /**
     * Returns the minimum of all elements, or null if empty,
     * assuming that all elements are Comparables
     * @return minimum element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public T min() {
        return reduce(new ComparableMinReducer(), null); // raw type cheat
    }

    /**
     * Returns the minimum of elements from fromIndex to toIndex, or
     * null if empty, assuming that all elements are Comparables
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @return minimum element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public T min(int fromIndex, int toIndex) {
        return reduce(fromIndex, toIndex, new ComparableMinReducer(), null);
    }

    /**
     * Returns the maximum of all elements, or null if empty
     * @param comparator the comparator
     * @return maximum element, or null if empty
     */
    public T max(Comparator<? super T> comparator) {
        return reduce(new MaxReducer<T>(comparator), null);
    }

    /**
     * Returns the maximum of elements from fromIndex to toIndex, or
     * null if empty
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param comparator the comparator
     * @return maximum element, or null if empty
     */
    public T max(int fromIndex, int toIndex,
                 Comparator<? super T> comparator) {
        return reduce(fromIndex, toIndex, 
                      new MaxReducer<T>(comparator), null);
    }

    /**
     * Returns the maximum of all elements, or null if empty
     * assuming that all elements are Comparables
     * @return maximum element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public T max() {
        return reduce(new ComparableMaxReducer(), null);
    }

    /**
     * Returns the maximum of elements from fromIndex to toIndex, or
     * null if empty
     * assuming that all elements are Comparables
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @return maximum element, or null if empty
     * @throws ClassCastException if any element is not Comparable.
     */
    public T max(int fromIndex, int toIndex) {
        return reduce(fromIndex, toIndex, new ComparableMaxReducer(), null);
    }

    /**
     * Returns a ParallelArray mapping each element of array for which the
     * selector returns true;
     * @param selector the predicate
     * @param mapper the mapper
     * @return the array of mappings
     */
    public <U> ParallelArray<U> map(Predicate<? super T> selector,
                                    Mapper<? super T, ? extends U> mapper) {
        int n = array.length;
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectAllDriver<T,U> r =
            new FJSelectAllDriver<T,U>(array, selector, mapper, 0, n, g);
        ex.invoke(r);
        return new ParallelArray<U>(ex, r.results);
    }

    /**
     * Returns a ParallelArray mapping each element of array from
     * Returns an array mapping each element of given array from
     * fromIndex to toIndex for which the selector returns true;
     * @param fromIndex (inclusive)
     * @param toIndex (exclusive)
     * @param selector the predicate
     * @param mapper the mapper
     */
    public <U> ParallelArray<U> map(int fromIndex, int toIndex,
                                    Predicate<? super T> selector,
                                    Mapper<? super T, ? extends U> mapper) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectAllDriver<T,U> r =
            new FJSelectAllDriver<T,U>(array, selector, mapper,
                                       fromIndex, toIndex, g);
        ex.invoke(r);
        return new ParallelArray<U>(ex, r.results);
    }

    /**
     * Returns a ParallelArray of all elements of the array matching
     * pred.  The order of appearance of elements in the returned
     * array is the same as in the source array.
     * @param pred the predicate
     * @return an array of all elements matching the predicate.
     */
    public ParallelArray<T> select(Predicate<? super T> pred) {
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
    public ParallelArray<T> select(int fromIndex, int toIndex,
                                   Predicate<? super T> pred) {
        int n = rangeCheck(array.length, fromIndex, toIndex);
        int m = ex.getParallelismLevel();
        int g = defaultGranularity(m, n);
        FJSelectAllDriver<T,T> r =
            new FJSelectAllDriver<T,T>(array, pred, null,
                                       fromIndex, toIndex, g);
        ex.invoke(r);
        return new ParallelArray<T>(ex, r.results);
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
    static final class FJSelectAll<T,U> extends RecursiveAction {
        final T[] array;
        final Predicate<? super T> pred;
        final Mapper<? super T, ? extends U> mapper;
        final int lo;
        final int hi;
        final int gran;
        int[] matches;
        int nmatches;
        int offset;
        U[] results;
        FJSelectAll<T,U> left, right;

        FJSelectAll(T[] array,
                    Predicate<? super T> pred,
                    Mapper<? super T, ? extends U> mapper,
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
                Mapper<? super T, ? extends U> mapper = this.mapper;
                if (mapper != null) {
                    for (int i = 0; i < n; ++i)
                        results[k++] = mapper.map(array[m[i]]);
                }
                else {
                    for (int i = 0; i < n; ++i)
                        results[k++] = (U)(array[m[i]]);
                }
            }
        }

        void internalPhase1() {
            int mid = (lo + hi) >>> 1;
            FJSelectAll<T,U> l =
                new FJSelectAll<T,U>(array, pred, mapper, lo,    mid, gran);
            FJSelectAll<T,U> r =
                new FJSelectAll<T,U>(array, pred, mapper, mid,   hi,  gran);
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
            U[] res = results;
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
    static final class FJSelectAllDriver<T,U> extends RecursiveAction {
        final T[] array;
        final Predicate<? super T> pred;
        final Mapper<? super T, ? extends U> mapper;
        final int gran;
        final int lo;
        final int hi;
        U[] results;

        FJSelectAllDriver(T[] array,
                          Predicate<? super T> pred,
                          Mapper<? super T, ? extends U> mapper,
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
            FJSelectAll<T,U> r =
                new FJSelectAll<T,U>(array, pred, mapper, lo, hi, gran);
            r.compute();
            int rm = r.nmatches;
            U[] res = (U[]) new Object[rm];
            this.results = res;
            r.results = res;
            r.compute();
        }
    }

    /**
     * Sorts the array. Unlike Collections.sort, this sort does
     * not guarantee that elements with equal keys maintain their
     * relative position in the array.
     * @param cmp the comparator to use
     */
    public void sort(Comparator<? super T> cmp) {
        int n = array.length;
        int m = ex.getParallelismLevel();
        if (m == 1 || n < SEQUENTIAL_SORT_THRESHOLD) {
            cmpArrayQuickSort(cmp, array, 0, n-1);
            return;
        }
        T[] ws = (T[])java.lang.reflect.Array.
            newInstance(array.getClass().getComponentType(), n);
        ex.invoke(new FJCmpSorter<T>(cmp, array, 0, ws, 0, n));
    }

    /**
     * Sorts the array, assuming all elements are Comparable. Unlike
     * Collections.sort, this sort does not guarantee that elements
     * with equal keys maintain their relative position in the array.
     * @throws ClassCastException if any element is not Comparable.
     */
    public void sort() {
        Comparable[] ca = (Comparable[])array; // raw type cheat
        int n = ca.length;
        int m = ex.getParallelismLevel();
        if (m == 1 || n < SEQUENTIAL_SORT_THRESHOLD) {
            comparableQuickSort(ca, 0, n-1);
            return;
        }
        Comparable[] ws = (Comparable[])java.lang.reflect.Array.
            newInstance(ca.getClass().getComponentType(), n);
        ex.invoke(new FJComparableSorter(ca, 0, ws, 0, n));
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
    static class FJCmpSorter<T> extends RecursiveAction {
        final Comparator<? super T> cmp;
        final T[] a;   //  to be sorted.
        final int ao;      // origin of the part of array we deal with
        final T[] w;   // workspace for merge
        final int wo;      // its origin
        final int n;       // Number of elements in (sub)arrays.
        
        FJCmpSorter(Comparator<? super T> cmp,
                    T[] a, int ao, T[] w, int wo, int n) {
            this.cmp = cmp;
            this.a = a; this.ao = ao; this.w = w; this.wo = wo; this.n = n;
        }

        protected void compute()  {
            if (n <= SEQUENTIAL_SORT_THRESHOLD)
                cmpArrayQuickSort(cmp, a, ao, ao+n-1);
            else {
                int q = n >>> 2; // lower quarter index
                int h = n >>> 1; // half
                int u = h + q;   // upper quarter

                coInvoke(new FJCmpSubSorter<T>
                         (new FJCmpSorter<T>(cmp, a, ao,   w, wo,   q),
                          new FJCmpSorter<T>(cmp, a, ao+q, w, wo+q, q),
                          new FJCmpMerger<T>(cmp, a, ao,   q, ao+q, q,
                                             w, wo)),
                         new FJCmpSubSorter<T>
                         (new FJCmpSorter<T>(cmp, a, ao+h, w, wo+h, q),
                          new FJCmpSorter<T>(cmp, a, ao+u, w, wo+u, n-u),
                          new FJCmpMerger<T>(cmp, a, ao+h, q, ao+u, n-u,
                                             w, wo+h)));
                new FJCmpMerger<T>(cmp, w, wo, h, wo+h, n-h, a, ao).compute();
            }
        }

    }

    /**
     * Helper class to run two given sorts in parallel, then merge them.
     */
    static class FJCmpSubSorter<T> extends RecursiveAction {
        final FJCmpSorter<T> left;
        final FJCmpSorter<T> right;
        final FJCmpMerger<T> merger;
        FJCmpSubSorter(FJCmpSorter<T> left, FJCmpSorter<T> right,
                       FJCmpMerger<T> merger) {
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
    static class FJCmpMerger<T> extends RecursiveAction {
        final Comparator<? super T> cmp;
        final T[] a;      // partitioned  array.
        final int lo;     // relative origin of left side
        final int ln;     // number of elements on left
        final int ro;     // relative origin of right side
        final int rn;     // number of elements on right

        final T[] w;      // Output array.
        final int wo;

        FJCmpMerger(Comparator<? super T> cmp,
                    T[] a, int lo, int ln, int ro, int rn, T[] w, int wo) {
            this.cmp = cmp;
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
                T split = a[ls];
                int rl = 0;
                int rh = rn;
                while (rl < rh) {
                    int mid = (rl + rh) >>> 1;
                    if (cmp.compare(split, a[ro + mid]) <= 0)
                        rh = mid;
                    else
                        rl = mid + 1;
                }
                coInvoke
                    (new FJCmpMerger<T>(cmp, a, lo, lh,    ro,    rh,    w, wo),
                     new FJCmpMerger<T>(cmp, a, ls, ln-lh, ro+rh, rn-rh, w, wo+lh+rh));
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
                T al = a[l];
                T ar = a[r];
                if (cmp.compare(al, ar) <= 0) {
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
     * recurses along left path
     */
    static <T> void cmpArrayQuickSort(Comparator<? super T> cmp, 
                                      T[] a, int lo, int hi) {
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

            cmpArrayQuickSort(cmp, a, lo,    left);
            lo = left + 1;
        }
    }

    // Almost the same code for comparables. Sadly worth doing.

    static class FJComparableSorter<T extends Comparable<? super T>> extends RecursiveAction {
        final T[] a;     // Array to be sorted.
        final int ao;    // origin of the part of array we deal with
        final T[] w;     // workspace array for merge
        final int wo;    // its origin
        final int n;     // Number of elements in (sub)arrays.

        FJComparableSorter (T[] a, int ao, T[] w, int wo, int n) {
            this.a = a; this.ao = ao; this.w = w; this.wo = wo; this.n = n;
        }

        protected void compute()  {
            if (n <= SEQUENTIAL_SORT_THRESHOLD)
                comparableQuickSort(a, ao, ao+n-1);
            else {
                int q = n >>> 2; // lower quarter index
                int h = n >>> 1; // half
                int u = h + q;   // upper quarter

                coInvoke(new FJComparableSubSorter<T>(new FJComparableSorter<T>(a, ao,   w, wo,   q),
                                                      new FJComparableSorter<T>(a, ao+q, w, wo+q, q),
                                                      new FJComparableMerger<T>(a, ao,   q, ao+q, q, 
                                                                                w, wo)),
                         new FJComparableSubSorter<T>(new FJComparableSorter<T>(a, ao+h, w, wo+h, q),
                                                      new FJComparableSorter<T>(a, ao+u, w, wo+u, n-u),
                                                      new FJComparableMerger<T>(a, ao+h, q, ao+u, n-u, 
                                                                                w, wo+h)));
                new FJComparableMerger<T>(w, wo, h, wo+h, n-h, a, ao).compute();
            }
        }

    }
    static class FJComparableSubSorter<T extends Comparable<? super T>> extends RecursiveAction {
        final FJComparableSorter<T> left;
        final FJComparableSorter<T> right;
        final FJComparableMerger<T> merger;
        FJComparableSubSorter(FJComparableSorter<T> left, FJComparableSorter<T> right, FJComparableMerger<T> merger) {
            this.left = left; this.right = right; this.merger = merger;
        }
        protected void compute() {
            coInvoke(left, right);
            merger.compute();
        }
    }

    static class FJComparableMerger<T extends Comparable<? super T>>  extends RecursiveAction {
        final T[] a;      // partitioned  array.
        final int lo;     // relative origin of left side
        final int ln;     // number of elements on left
        final int ro;     // relative origin of right side
        final int rn;     // number of elements on right

        final T[] w;      // Output array.
        final int wo;

        FJComparableMerger (T[] a, int lo, int ln, int ro, int rn, T[] w, int wo) {
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
                T split = a[ls];
                int rl = 0;
                int rh = rn;
                while (rl < rh) {
                    int mid = (rl + rh) >>> 1;
                    if (split.compareTo(a[ro + mid]) <= 0)
                        rh = mid;
                    else
                        rl = mid + 1;
                }
                coInvoke(new FJComparableMerger<T>(a, lo, lh,    ro,    rh,    w, wo), 
                         new FJComparableMerger<T>(a, ls, ln-lh, ro+rh, rn-rh, w, wo+lh+rh));
            }
        }

        /** a standard sequential merge */
        void merge() {
            int l = lo;
            int lFence = lo+ln;
            int r = ro;
            int rFence = ro+rn;
            int k = wo;
            while (l < lFence && r < rFence)
                w[k++] = (a[l].compareTo(a[r]) <= 0)? a[l++] : a[r++];
            while (l < lFence) 
                w[k++] = a[l++];
            while (r < rFence) 
                w[k++] = a[r++];
        }
    }

    static <T extends Comparable<? super T>> void comparableQuickSort(T[] a, int lo, int hi) {
        for (;;) { // loop along one recursion path
            // If under threshold, use insertion sort
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
            
            //  Use median-of-three(lo, mid, hi) to pick a partition. 
            //  Also swap them into relative order while we are at it.
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

            comparableQuickSort(a, lo,    left);
            lo = left + 1;
        }
    }

}
