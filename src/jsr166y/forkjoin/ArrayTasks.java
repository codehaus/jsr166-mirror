/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import static jsr166y.forkjoin.TaskTypes.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Parallel operations on arrays. This class is a stand-in for
 * functionality that will probably be supported in some other way in
 * Java 7.
 */
public class ArrayTasks {
    /**
     * default granularity for divide-by-two tasks. Provides about
     * four times as many finest-grained tasks as there are CPUs.
     */
    static int defaultGranularity(ForkJoinExecutor ex, int n) {
        int threads = ex.getParallelismLevel();
        return 1 + n / ((threads << 2) - 3);
    }

    /**
     * Applies the given procedure to each element of the array.
     * @param ex the executor
     * @param array the array
     * @param proc the procedure
     */
    public static <T> void apply(ForkJoinExecutor ex,
                                 T[] array, 
                                 Procedure<? super T> proc) {
        int n = array.length;
        ex.invoke(new FJApplyer<T>(array, proc, 0, n-1, defaultGranularity(ex, n)));
    }

    /**
     * Applies the given procedure to given elements of the array.
     * @param ex the executor
     * @param array the array
     * @param proc the procedure
     * @param fromIndex the lower (inclusive) index
     * @param toIndex the upper fence (exclusive) index
     */
    public static <T> void apply(ForkJoinExecutor ex,
                                 T[] array, 
                                 Procedure<? super T> proc,
                                 int fromIndex,
                                 int toIndex) {
        ex.invoke(new FJApplyer<T>(array, proc, fromIndex, toIndex-1, 
                                     defaultGranularity(ex, toIndex - fromIndex)));
    }
    
    /**
     * Returns reduction of given array
     * @param ex the executor
     * @param array the array
     * @param reducer the reducer
     * @param base the result for an empty array
     */
    public static <T> T reduce(ForkJoinExecutor ex,
                               T[] array, 
                               Reducer<T> reducer,
                               T base) {
        int n = array.length;
        return ex.invoke(new FJReducer<T>(array,reducer, base,
                                              0, n-1, defaultGranularity(ex, n)));
    }

    /**
     * Returns reduction of given elements of array
     * @param ex the executor
     * @param array the array
     * @param reducer the reducer
     * @param base the result for an empty array
     * @param fromIndex the lower (inclusive) index
     * @param toIndex the upper fence (exclusive) index
     */
    public static <T> T reduce(ForkJoinExecutor ex,
                               T[] array, 
                               Reducer<T> reducer,
                               T base,
                               int fromIndex,
                               int toIndex) {
        return ex.invoke(new FJReducer<T>(array,reducer, base,
                                            fromIndex, toIndex-1, 
                                            defaultGranularity(ex, toIndex-fromIndex)));
    }

    /**
     * Applies mapper to each element of array and reduces result
     * @param ex the executor
     * @param array the array
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     */
    public static <T, U> U reduce(ForkJoinExecutor ex,
                                  T[] array, 
                                  Mapper<? super T, ? extends U> mapper,
                                  Reducer<U> reducer,
                                  U base) {
        int n = array.length;
        return ex.invoke(new FJMapReducer<T, U>(array, mapper, reducer, 
                                                  base,
                                                  0, n-1, defaultGranularity(ex, n)));
    }

    /**
     * Applies mapper to given elements of array and reduces result
     * @param ex the executor
     * @param array the array
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty array
     * @param fromIndex the lower (inclusive) index
     * @param toIndex the upper fence (exclusive) index
     */
    public static <T, U> U reduce(ForkJoinExecutor ex,
                                  T[] array, 
                                  Mapper<? super T, ? extends U> mapper,
                                  Reducer<U> reducer,
                                  U base,
                                  int fromIndex,
                                  int toIndex) {
        return ex.invoke(new FJMapReducer<T, U>(array, mapper, reducer, 
                                                  base,
                                                  fromIndex, toIndex-1, 
                                                  defaultGranularity(ex, toIndex-fromIndex)));
    }

    /**
     * Maps each element of given array using mapper to dest
     * @param ex the executor
     * @param array the array
     * @param mapper the mapper
     * @param dest the destination array
     */
    public static <T, U> void map(ForkJoinExecutor ex,
                                  T[] array, 
                                  Mapper<? super T, ? extends U>  mapper,
                                  U[] dest) {
        int n = array.length;
        ex.invoke(new FJMapper<T, U>(array, dest, mapper,  
                                       0, n-1, defaultGranularity(ex, n)));
    }

    /**
     * Returns an element of the array matching the given predicate, or
     * null if none
     * @param ex the executor
     * @param array the array
     * @param pred the predicate
     */
    public static <T> T findAny(ForkJoinExecutor ex,
                                T[] array, 
                                Predicate<? super T>  pred) {
        int n = array.length;
        AtomicReference<T> result = new AtomicReference<T>();
        ex.invoke(new FJFindAny<T>(array, pred, result,  
                                     0, n-1, defaultGranularity(ex, n)));
        return result.get();
    }

    /**
     * Returns an element of the array range matching the given predicate, or
     * null if none
     * @param ex the executor
     * @param array the array
     * @param pred the predicate
     * @param fromIndex the lower (inclusive) index
     * @param toIndex the upper fence (exclusive) index
     */
    public static <T> T findAny(ForkJoinExecutor ex,
                                T[] array, 
                                Predicate<? super T>  pred,
                                int fromIndex,
                                int toIndex) {
        AtomicReference<T> result = new AtomicReference<T>();
        ex.invoke(new FJFindAny<T>(array, pred, result,  
                                     fromIndex, toIndex-1, 
                                     defaultGranularity(ex, toIndex-fromIndex)));
        return result.get();
    }

    /**
     * Returns a list of all elements of the array matching pred
     * @param ex the executor
     * @param array the array
     * @param pred the predicate
     */
    public static <T> List<T> findAll(ForkJoinExecutor ex,
                                      T[] array, 
                                      Predicate<? super T> pred) {
        int n = array.length;
        Vector<T> dest = new Vector<T>(); // todo: use smarter list
        ex.invoke(new FJFindAll<T>(array, pred, dest,  
                                     0, n-1, defaultGranularity(ex, n)));
        return dest;
    }

    /**
     * Returns a list of all elements of the array range matching pred
     * @param ex the executor
     * @param array the array
     * @param pred the predicate
     * @param fromIndex the lower (inclusive) index
     * @param toIndex the upper fence (exclusive) index
     */
    public static <T> List<T> findAll(ForkJoinExecutor ex,
                                      T[] array, 
                                      Predicate<? super T> pred,
                                      int fromIndex,
                                      int toIndex) {
        Vector<T> dest = new Vector<T>(); // todo: use smarter list
        ex.invoke(new FJFindAll<T>(array, pred, dest,  
                                     fromIndex, toIndex-1, 
                                     defaultGranularity(ex, toIndex-fromIndex)));
        return dest;
    }

    /**
     * Sorts the given array
     * @param ex the executor
     * @param array the array
     */
    public static <T extends Comparable<? super T>> void sort(ForkJoinExecutor ex, T[] array) {
        int n = array.length;
        int threads = ex.getParallelismLevel();
        if (threads == 1) {
            quickSort(array, 0, n-1);
            return;
        }
        T[] workSpace = (T[])java.lang.reflect.Array.
            newInstance(array.getClass().getComponentType(), n);
        ex.invoke(new FJSorter<T>(array, 0, workSpace, 0, n));
    }

    /**
     * Returns the minimum of all elements, or null if empty
     * @param ex the executor
     * @param array the array
     */
    public static <T extends Comparable<? super T>> T min(ForkJoinExecutor ex,
                                                          T[] array) {
        int n = array.length;
        return ex.invoke(new FJMin<T>(array, 0, n-1, defaultGranularity(ex, n)));
    }

    /**
     * Returns the maximum of all elements, or null if empty
     * @param ex the executor
     * @param array the array
     */
    public static <T extends Comparable<? super T>> T max(ForkJoinExecutor ex,
                                                          T[] array) {
        int n = array.length;
        return ex.invoke(new FJMax<T>(array, 0, n-1, defaultGranularity(ex, n)));
    }

    /*
     * Parallel divide-and-conquer drivers for aggregate operations.
     * Most have the same structure. Rather than pure recursion, most
     * link right-hand-sides in lists and then join up the tree. This
     * generates tasks a bit faster than recursive style, leading to
     * better work-stealing performance.
     */

    /**
     * Fork/Join version of apply
     */
    static final class FJApplyer<T> extends RecursiveAction {
        final T[] array;
        final Procedure<? super T> f;
        final int lo;
        final int hi;
        final int gran;
        FJApplyer<T> next;

        FJApplyer(T[] array, Procedure<? super T> f, int lo, int hi, int gran){
            this.array = array;
            this.f = f;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            FJApplyer<T> right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJApplyer<T> r = new FJApplyer<T>(array, f, mid+1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            for (int i = l; i <= h; ++i)
                f.apply(array[i]);
            while (right != null) {
                right.join();
                right = right.next;
            }
        }

    }              
    
    /**
     * Fork/Join version of MapReduce
     */
    static final class FJMapReducer<T, U> extends RecursiveTask<U> {
        final T[] array;
        final Mapper<? super T, ? extends U> mapper;
        final Reducer<U> reducer;
        final U base;
        final int lo;
        final int hi;
        final int gran;
        FJMapReducer<T, U> next;

        FJMapReducer(T[] array, 
                   Mapper<? super T, ? extends U> mapper,
                   Reducer<U> reducer,
                   U base,
                   int lo, 
                   int hi, 
                   int gran) {
            this.array = array;
            this.mapper = mapper;
            this.reducer = reducer;
            this.base = base;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected U compute() {
            FJMapReducer<T, U> right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMapReducer<T, U> r = 
                    new FJMapReducer<T, U>(array, mapper, reducer, 
                                           base, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            U x = base;
            for (int i = l; i <= h; ++i)
                x = reducer.combine(x, mapper.map(array[i]));
            while (right != null) {
                x = reducer.combine(x, right.join());
                right = right.next;
            }
            return x;
        }
    }              


    /**
     * Fork/Join version of Map
     */
    static final class FJMapper<T, U> extends RecursiveAction {
        final T[] array;
        final U[] dest;
        final Mapper<? super T, ? extends U> mapper;
        final int lo;
        final int hi;
        final int gran;
        FJMapper<T, U> next;

        FJMapper(T[] array, 
                 U[] dest,
                 Mapper<? super T, ? extends U> mapper,
                 int lo, 
                 int hi, 
                 int gran) {
            this.array = array;
            this.dest = dest;
            this.mapper = mapper;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected void compute() {
            FJMapper<T, U> right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMapper<T, U> r = 
                    new FJMapper<T, U>(array, dest, mapper, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            for (int i = l; i <= h; ++i)
                dest[i] = mapper.map(array[i]);
            while (right != null) {
                right.join();
                right = right.next;
            }
        }
    }              

    /**
     * Fork/Join version of Reduce
     */
    static final class FJReducer<T> extends RecursiveTask<T> {
        final T[] array;
        final Reducer<T> reducer;
        final T base;
        final int lo;
        final int hi;
        final int gran;
        FJReducer<T> next;

        FJReducer(T[] array, 
                  Reducer<T> reducer,
                  T base,
                  int lo, 
                  int hi, 
                  int gran) {
            this.array = array;
            this.reducer = reducer;
            this.base = base;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected T compute() {
            FJReducer<T> right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJReducer<T> r = 
                    new FJReducer<T>(array, reducer, base, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            T x = base;
            for (int i = l; i <= h; ++i)
                x = reducer.combine(x, array[i]);
            while (right != null) {
                x = reducer.combine(x, right.join());
                right = right.next;
            }
            return x;
        }
    }              

    /**
     * Fork/Join version of min
     */
    static final class FJMin<T extends Comparable<? super T>> extends RecursiveTask<T> {
        final T[] array;
        final int lo;
        final int hi;
        final int gran;
        FJMin<T> next;

        FJMin(T[] array, 
              int lo, 
              int hi, 
              int gran) {
            this.array = array;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected T compute() {
            FJMin<T> right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMin r = 
                    new FJMin(array, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            T x = null;;
            for (int i = l; i <= h; ++i) {
                T y = array[i];
                if (x == null || (y != null && x.compareTo(y) > 0))
                    x = y;
            }
            while (right != null) {
                T y = right.join();
                if (x == null || (y != null && x.compareTo(y) > 0))
                    x = y;
                right = right.next;
            }
            return x;
        }
    }              

    /**
     * Fork/Join version of max
     */
    static final class FJMax<T extends Comparable<? super T>> extends RecursiveTask<T> {
        final T[] array;
        final int lo;
        final int hi;
        final int gran;
        FJMax<T> next;

        FJMax(T[] array, 
              int lo, 
              int hi, 
              int gran) {
            this.array = array;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        protected T compute() {
            FJMax<T> right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJMax r = 
                    new FJMax(array, mid + 1, h, g);
                r.fork();
                r.next = right;
                    
                right = r;
                h = mid;
            }
            T x = null;;
            for (int i = l; i <= h; ++i) {
                T y = array[i];
                if (x == null || (y != null && x.compareTo(y) < 0))
                    x = y;
            }
            while (right != null) {
                T y = right.join();
                if (x == null || (y != null && x.compareTo(y) < 0))
                    x = y;
                right = right.next;
            }
            return x;
        }
    }              

    /**
     * Fork/Join version of FindAny
     */
    static final class FJFindAny<T> extends RecursiveAction {
        final T[] array;
        final Predicate<? super T> pred;
        final AtomicReference<T> result;
        final int lo;
        final int hi;
        final int gran;

        FJFindAny(T[] array, 
                  Predicate<? super T> pred,
                  AtomicReference<T> result,
                  int lo, 
                  int hi, 
                  int gran) {
            this.array = array;
            this.pred = pred;
            this.result = result;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        void seqCompute() {
            for (int i = lo; i <= hi; ++i) {
                T x = array[i];
                if (pred.evaluate(x)) {
                    result.compareAndSet(null, x);
                    break;
                }
            }
        }

        protected void compute() {
            if (result.get() != null)
                return;
            if (hi - lo <= gran) {
                seqCompute();
                return;
            }
            int mid = (lo + hi) >>> 1;
            FJFindAny<T> left = 
                new FJFindAny<T>(array, pred, result, lo, mid, gran);
            left.fork();
            FJFindAny<T> right = 
                new FJFindAny<T>(array, pred, result, mid + 1, hi, gran);
            right.invoke();
            if (result.get() != null)
                left.cancel();
            else
                left.join();
        }
    }              

    /**
     * Fork/Join version of FindAll
     */
    static final class FJFindAll<T> extends RecursiveAction {
        final T[] array;
        final Predicate<? super T> pred;
        final List<T> result;
        final int lo;
        final int hi;
        final int gran;

        FJFindAll(T[] array, 
                  Predicate<? super T> pred,
                  List<T> result,
                  int lo, 
                  int hi, 
                  int gran) {
            this.array = array;
            this.pred = pred;
            this.result = result;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }


        void seqCompute() {
            for (int i = lo; i <= hi; ++i) {
                T x = array[i];
                if (pred.evaluate(x))
                    result.add(x);
            }
        }

        protected void compute() {
            if (hi - lo <= gran) {
                seqCompute();
                return;
            }
            int mid = (lo + hi) >>> 1;
            FJFindAll<T> left = 
                new FJFindAll<T>(array, pred, result, lo, mid, gran);
            FJFindAll<T> right = 
                new FJFindAll<T>(array, pred, result, mid + 1, hi, gran);
            coInvoke(left, right);
        }
    }              


    /*
     * Sort algorithm based mainly on CilkSort
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

    // Cutoff for when to do sequential versus parallel sorts and merges 
    static final int SEQUENTIAL_THRESHOLD = 1024; 

    static class FJSorter<T extends Comparable<? super T>> extends RecursiveAction {
        final T[] a;     // Array to be sorted.
        final int ao;    // origin of the part of array we deal with
        final T[] w;     // workspace array for merge
        final int wo;    // its origin
        final int n;     // Number of elements in (sub)arrays.

        FJSorter (T[] a, int ao, T[] w, int wo, int n) {
            this.a = a; this.ao = ao; this.w = w; this.wo = wo; this.n = n;
        }

        protected void compute()  {
            if (n <= SEQUENTIAL_THRESHOLD)
                quickSort(a, ao, ao+n-1);
            else {
                int q = n >>> 2; // lower quarter index
                int h = n >>> 1; // half
                int u = h + q;   // upper quarter

                coInvoke(new SubSorter<T>(new FJSorter<T>(a, ao,   w, wo,   q),
                                          new FJSorter<T>(a, ao+q, w, wo+q, q),
                                          new FJMerger<T>(a, ao,   q, ao+q, q, 
                                                        w, wo)),
                         new SubSorter<T>(new FJSorter<T>(a, ao+h, w, wo+h, q),
                                          new FJSorter<T>(a, ao+u, w, wo+u, n-u),
                                          new FJMerger<T>(a, ao+h, q, ao+u, n-u, 
                                                        w, wo+h)));
                new FJMerger<T>(w, wo, h, wo+h, n-h, a, ao).compute();
            }
        }

    }

    /** 
     * A boring class to run two given sorts in parallel, then merge them.
     */
    static class SubSorter<T extends Comparable<? super T>> extends RecursiveAction {
        final FJSorter<T> left;
        final FJSorter<T> right;
        final FJMerger<T> merger;
        SubSorter(FJSorter<T> left, FJSorter<T> right, FJMerger<T> merger) {
            this.left = left; this.right = right; this.merger = merger;
        }
        protected void compute() {
            coInvoke(left, right);
            merger.compute();
        }
    }

    static class FJMerger<T extends Comparable<? super T>>  extends RecursiveAction {
        final T[] a;      // partitioned  array.
        final int lo;     // relative origin of left side
        final int ln;     // number of elements on left
        final int ro;     // relative origin of right side
        final int rn;     // number of elements on right

        final T[] w;      // Output array.
        final int wo;

        FJMerger (T[] a, int lo, int ln, int ro, int rn, T[] w, int wo) {
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
            /*
              If partiions are small, then just sequentially merge.
              Otherwise:
              1. Split Left partition in half.
              2. Find the greatest point in Right partition
                 less than the beginning of the second half of left, 
                 via binary search.
              3. In parallel:
                  merge left half of  L with elements of R up to split point
                  merge right half of L with elements of R past split point
            */

            if (ln <= SEQUENTIAL_THRESHOLD)
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
                coInvoke(new FJMerger<T>(a, lo, lh,    ro,    rh,    w, wo), 
                         new FJMerger<T>(a, ls, ln-lh, ro+rh, rn-rh, w, wo+lh+rh));
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

    // Cutoff for when to use insertion-sort instead of quicksort
    static final int INSERTION_SORT_THRESHOLD = 8;

    /** A standard sequential quicksort */
    static <T extends Comparable<? super T>> void quickSort(T[] a, int lo, int hi) {
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

            quickSort(a, lo,    left);
            lo = left + 1;
        }
    }


}
