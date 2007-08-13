/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;
import java.util.concurrent.atomic.*;
import static jsr166y.forkjoin.TaskTypes.*;

/**
 * Parallel operations on lists.
 */
public class ListTasks {
    private ListTasks() {}

    /**
     * default granularity for divide-by-two tasks. 
     */
    static int defaultGranularity(ForkJoinExecutor ex, int n) {
        int par = ex.getParallelismLevel();
        return (par > 1)? (1 + n / (par << 3)) : n;
    }

    /**
     * Applies the given procedure to each element of the list.
     * @param ex the executor
     * @param list the list
     * @param proc the procedure
     */
    public static <T> void apply(ForkJoinExecutor ex,
                                 List<? extends T> list, 
                                 Procedure<? super T> proc) {
        int n = list.size();
        FJListParams<T,T> p = 
            new FJListParams<T, T>(list, proc,
                                   null, null, null, null,
                                   defaultGranularity(ex, n));
        ex.invoke(new FJListApplyer<T, T>(p, 0, n));
    }

    /**
     * Applies the given procedure to each element of the list
     * for which the given selector returns true
     * @param ex the executor
     * @param list the list
     * @param selector the predicate
     * @param proc the procedure
     */
    public static <T> void apply(ForkJoinExecutor ex,
                                 List<? extends T> list, 
                                 Predicate<? super T>  selector,
                                 Procedure<? super T> proc) {
        int n = list.size();
        FJListParams<T,T> p = 
            new FJListParams<T, T>(list, proc,
                                   selector, null, null, null,
                                   defaultGranularity(ex, n));
        ex.invoke(new FJListApplyer<T, T>(p, 0, n));
    }

    /**
     * Applies the given procedure to the objects mapped
     * from each element of the list.
     * @param ex the executor
     * @param list the list
     * @param mapper the mapper
     * @param proc the procedure
     */
    public static <T, U> void apply(ForkJoinExecutor ex,
                                    List<? extends T> list, 
                                    Mapper<? super T, ? extends U>  mapper,
                                    Procedure<? super U> proc) {
        int n = list.size();
        FJListParams<T,U> p = 
            new FJListParams<T, U>(list, proc,
                                   null, mapper, null, null,
                                   defaultGranularity(ex, n));
        ex.invoke(new FJListApplyer<T, U>(p, 0, n));
    }

    /**
     * Applies the given procedure to the objects mapped
     * from each element of the list for which the selector returns true.
     * @param ex the executor
     * @param list the list
     * @param selector the predicate
     * @param mapper the mapper
     * @param proc the procedure
     */
    public static <T, U> void apply(ForkJoinExecutor ex,
                                    List<? extends T> list, 
                                    Predicate<? super T>  selector,
                                    Mapper<? super T, ? extends U>  mapper,
                                    Procedure<? super U> proc) {
        int n = list.size();
        FJListParams<T,U> p = 
            new FJListParams<T, U>(list, proc,
                                   selector, mapper, null, null,
                                   defaultGranularity(ex, n));
        ex.invoke(new FJListApplyer<T, U>(p, 0, n));
    }
    
    /**
     * Returns reduction of given list
     * @param ex the executor
     * @param list the list
     * @param reducer the reducer
     * @param base the result for an empty list
     */
    public static <T> T reduce(ForkJoinExecutor ex,
                               List<? extends T> list, 
                               Reducer<T> reducer,
                               T base) {
        int n = list.size();
        FJListParams<T,T> p = 
            new FJListParams<T, T>(list, null, 
                                   null, null, reducer, base,
                                   defaultGranularity(ex, n));
        return ex.invoke(new FJListReducer<T, T>(p, 0, n));
    }

    /**
     * Returns reduction of the elements of the list for which
     * the selector returns true;
     * @param ex the executor
     * @param list the list
     * @param selector the predicate
     * @param reducer the reducer
     * @param base the result for an empty list
     */
    public static <T> T reduce(ForkJoinExecutor ex,
                               List<? extends T> list, 
                               Predicate<? super T>  selector,
                               Reducer<T> reducer,
                               T base) {
        int n = list.size();
        FJListParams<T,T> p = 
            new FJListParams<T, T>(list, null, 
                                   selector, null, reducer, base,
                                   defaultGranularity(ex, n));
        return ex.invoke(new FJListReducer<T, T>(p, 0, n));
    }

    /**
     * Applies mapper to each element of list and reduces result
     * @param ex the executor
     * @param list the list
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty list
     */
    public static <T, U> U reduce(ForkJoinExecutor ex,
                                  List<? extends T> list, 
                                  Mapper<? super T, ? extends U> mapper,
                                  Reducer<U> reducer,
                                  U base) {
        int n = list.size();
        FJListParams<T,U> p = 
            new FJListParams<T, U>(list, null, null, mapper, reducer, base,
                                   defaultGranularity(ex, n));
        return ex.invoke(new FJListReducer<T, U>(p, 0, n));
    }

    /**
     * Returns reduction of the mapped elements of the list for which
     * the selector returns true;
     * @param ex the executor
     * @param list the list
     * @param selector the predicate
     * @param mapper the mapper
     * @param reducer the reducer
     * @param base the result for an empty list
     */
    public static <T, U> U reduce(ForkJoinExecutor ex,
                                  List<? extends T> list, 
                                  Predicate<? super T>  selector,
                                  Mapper<? super T, ? extends U> mapper,
                                  Reducer<U> reducer,
                                  U base) {
        int n = list.size();
        FJListParams<T,U> p = 
            new FJListParams<T, U>(list, null, 
                                   selector, mapper, reducer, base,
                                   defaultGranularity(ex, n));
        return ex.invoke(new FJListReducer<T, U>(p, 0, n));
    }


    /**
     * Returns a list mapping each element of given list using mapper
     * @param ex the executor
     * @param list the list
     * @param mapper the mapper
     */
    public static <T, U> List<U> map(ForkJoinExecutor ex,
                                     List<? extends T> list, 
                                     Mapper<? super T, ? extends U>  mapper) {
        int n = list.size();
        U[] dest = (U[])new Object[n];
        ex.invoke(new FJMapper<T,U>(list, dest, mapper,  
                                    0, n, defaultGranularity(ex, n)));
        return Arrays.asList(dest);
    }

    /**
     * Returns a list mapping each element of given list for which
     * the selector returns true;
     * @param ex the executor
     * @param list the list
     * @param selector the predicate
     * @param mapper the mapper
     */
    public static <T, U> List<U> map(ForkJoinExecutor ex,
                                     List<? extends T> list, 
                                     Predicate<? super T>  selector,
                                     Mapper<? super T, ? extends U>  mapper) {
        int n = list.size();
        int gran = defaultGranularity(ex, n);
        FJFindAllDriver<T, U> r = 
            new FJFindAllDriver<T, U>(list, selector, mapper, gran);
        ex.invoke(r);
        return Arrays.asList(r.results); // todo: better list conversion
    }
    /**
     * Returns a list of all elements of the list matching pred.  The
     * order of appearance of elements in the returned list is the
     * same as in the given argument list.
     * @param ex the executor
     * @param list the list
     * @param pred the predicate
     * @return a list of all elements matching the predicate.
     */
    public static <T> List<T> select(ForkJoinExecutor ex,
                                     List<? extends T> list, 
                                     Predicate<? super T> pred) {
        int n = list.size();
        int gran = defaultGranularity(ex, n);
        FJFindAllDriver<T, T> r = 
            new FJFindAllDriver<T, T>(list, pred, null, gran);
        ex.invoke(r);
        return Arrays.asList(r.results); // todo: better list conversion
    }


    /**
     * Returns an element of the list matching the given predicate, or
     * null if none
     * @param ex the executor
     * @param list the list
     * @param pred the predicate
     */
    public static <T> T findAny(ForkJoinExecutor ex,
                                List<? extends T> list, 
                                Predicate<? super T>  pred) {
        int n = list.size();
        AtomicReference<T> result = new AtomicReference<T>();
        ex.invoke(new FJFindAny<T>(list, pred, result,  
                                   0, n, defaultGranularity(ex, n)));
        return result.get();
    }

    /**
     * Returns the minimum of all elements, or null if empty
     * @param ex the executor
     * @param list the list
     */
    public static <T extends Comparable<? super T>> T min(ForkJoinExecutor ex,
                                                          List<? extends T> list) {
        int n = list.size();
        return ex.invoke(new FJMin<T>(list, 0, n-1, defaultGranularity(ex, n)));
    }

    /**
     * Returns the maximum of all elements, or null if empty
     * @param ex the executor
     * @param list the list
     */
    public static <T extends Comparable<? super T>> T max(ForkJoinExecutor ex,
                                                          List<? extends T> list) {
        int n = list.size();
        return ex.invoke(new FJMax<T>(list, 0, n-1, defaultGranularity(ex, n)));
    }


    /**
     * Holds constant parameters and performs leaf actions for
     * all the apply/reduce variants
     */
    static final class FJListParams<T, U> {
        final List<? extends T> list;
        final Procedure<? super U> proc;
        final Predicate<? super T> pred;
        final Mapper<? super T, ? extends U> mapper;
        final Reducer<U> reducer;
        final U base;
        final int granularity;
        FJListParams(List<? extends T> list, 
                     Procedure<? super U> proc,
                     Predicate<? super T> pred,
                     Mapper<? super T, ? extends U> mapper,
                     Reducer<U> reducer,
                     U base,
                     int granularity) {
            this.list = list;
            this.proc = proc;
            this.pred = pred;
            this.mapper = mapper;
            this.reducer = reducer;
            this.base = base;
            this.granularity = granularity;
        }

        void leafApply(int lo, int hi) {
            List<? extends T> list = this.list;
            Procedure<? super T> proc =
                (Procedure<? super T>)(this.proc);
            for (int i = lo; i < hi; ++i)
                proc.apply(list.get(i));
        }

        void leafMapApply(int lo, int hi) {
            List<? extends T> list = this.list;
            Mapper<? super T, ? extends U> mapper = this.mapper;
            Procedure<? super U> proc = this.proc;
            for (int i = lo; i < hi; ++i)
                proc.apply(mapper.map(list.get(i)));
        }

        void leafSelectApply(int lo, int hi) { 
            List<? extends T> list = this.list;
            Predicate<? super T> pred = this.pred;
            Procedure<? super T> proc =
                (Procedure<? super T>)(this.proc);
            for (int i = lo; i < hi; ++i) {
                T x = list.get(i);
                if (pred.evaluate(x))
                    proc.apply(x);
            }
        }

        void leafSelectMapApply(int lo, int hi) { 
            List<? extends T> list = this.list;
            Predicate<? super T> pred = this.pred;
            Mapper<? super T, ? extends U> mapper = this.mapper;
            Procedure<? super U> proc = this.proc;
            for (int i = lo; i < hi; ++i) {
                T x = list.get(i);
                if (pred.evaluate(x))
                    proc.apply(mapper.map(x));
            }
        }

        U leafReduce(int lo, int hi) {
            List<? extends U> list = (List<? extends U>)(this.list);
            Reducer<U> reducer = this.reducer;
            boolean gotFirst = false;
            U result = null;
            for (int i = lo; i < hi; ++i) {
                U x = list.get(i);
                if (!gotFirst) {
                    gotFirst = true;
                    result = x;
                }
                else
                    result = reducer.combine(result, x);
            }
            return gotFirst? result: base;
        }
        
        U leafSelectReduce(int lo, int hi) { 
            List<? extends U> list = (List<? extends U>)(this.list);
            Reducer<U> reducer = this.reducer;
            Predicate<? super U> pred = 
                (Predicate<? super U>)(this.pred);

            boolean gotFirst = false;
            U result = null;
            for (int i = lo; i < hi; ++i) {
                U x = list.get(i);
                if (pred.evaluate(x)) {
                    if (!gotFirst) {
                        gotFirst = true;
                        result = x;
                    }
                    else 
                        result = reducer.combine(result, x);
                }
            }
            return gotFirst? result: base;
        }

        U leafMapReduce(int lo, int hi) {
            List<? extends T> list = this.list;
            Mapper<? super T, ? extends U> mapper = this.mapper;
            Reducer<U> reducer = this.reducer;

            boolean gotFirst = false;
            U result = null;
            for (int i = lo; i < hi; ++i) {
                U x = mapper.map(list.get(i));
                if (!gotFirst) {
                    gotFirst = true;
                    result = x;
                }
                else
                    result = reducer.combine(result, x);
            }
            return gotFirst? result: base;
        }

        U leafSelectMapReduce(int lo, int hi) {
            List<? extends T> list = this.list;
            Predicate<? super T> pred = this.pred;
            Mapper<? super T, ? extends U> mapper = this.mapper;
            Reducer<U> reducer = this.reducer;

            boolean gotFirst = false;
            U result = null;
            for (int i = lo; i < hi; ++i) {
                T x = list.get(i);
                if (pred.evaluate(x)) {
                    U r = mapper.map(x);
                    if (!gotFirst) {
                        gotFirst = true;
                        result = r;
                    }
                    else
                        result = reducer.combine(result, r);
                }
            }
            return gotFirst? result: base;
        }

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
    static final class FJListApplyer<T, U> extends RecursiveAction {
        final FJListParams<T,U> p;
        final int lo;
        final int hi;
        FJListApplyer<T, U> next;
        FJListApplyer(FJListParams<T, U> p, int lo, int hi) {
            this.p = p;
            this.lo = lo; 
            this.hi = hi;
        }
            
        protected void compute() {
            FJListApplyer<T, U> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                right = new FJListApplyer<T, U>(p, mid, h);
                right.fork();
                h = mid;
            }
            if (p.pred != null) {
                if (p.mapper != null) 
                    p.leafSelectMapApply(l, h);
                else
                    p.leafSelectApply(l, h);
            }
            else if (p.mapper != null)
                p.leafMapApply(l, h);
            else
                p.leafApply(l, h);
            while (right != null) {
                right.join();
                right = right.next;
            }
        }

    }              
    
    /**
     * Fork/Join version of reduce
     */
    static final class FJListReducer<T, U> extends RecursiveTask<U> {
        final FJListParams<T, U> p;
        final int lo;
        final int hi;
        FJListReducer<T, U> next;

        FJListReducer(FJListParams<T, U> p, int lo, int hi) {
            this.p = p;
            this.lo = lo; 
            this.hi = hi;
        }

        protected U compute() {
            FJListReducer<T, U> right = null;
            int l = lo;
            int h = hi;
            int g = p.granularity;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                FJListReducer<T, U> r = new FJListReducer<T, U>(p, mid, h);
                r.fork();
                r.next = right;
                right = r;
                h = mid;
            }
            U x;
            if (p.pred != null) {
                if (p.mapper != null) 
                    x = p.leafSelectMapReduce(l, h);
                else
                    x = p.leafSelectReduce(l, h);
            }
            else if (p.mapper != null)
                x = p.leafMapReduce(l, h);
            else
                x = p.leafReduce(l, h);
            
            Reducer<U> reducer = p.reducer;
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
        final List<? extends T> list;
        final U[] dest;
        final Mapper<? super T, ? extends U> mapper;
        final int lo;
        final int hi;
        final int gran;
        FJMapper<T, U> next;

        FJMapper(List<? extends T> list, 
                 U[] dest,
                 Mapper<? super T, ? extends U> mapper,
                 int lo, 
                 int hi, 
                 int gran) {
            this.list = list;
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
                    new FJMapper<T, U>(list, dest, mapper, mid, h, g);
                r.fork();
                r.next = right;
                right = r;
                h = mid;
            }
            for (int i = l; i < h; ++i)
                dest[i] = mapper.map(list.get(i));;
            while (right != null) {
                right.join();
                right = right.next;
            }
        }
    }              


    /**
     * Fork/Join version of min
     */
    static final class FJMin<T extends Comparable<? super T>> extends RecursiveTask<T> {
        final List<? extends T> list;
        final int lo;
        final int hi;
        final int gran;
        FJMin<T> next;

        FJMin(List<? extends T> list, 
              int lo, 
              int hi, 
              int gran) {
            this.list = list;
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
                    new FJMin(list, mid + 1, h, g);
                r.fork();
                r.next = right;
                right = r;
                h = mid;
            }
            T x = null;;
            for (int i = l; i <= h; ++i) {
                T y = list.get(i);
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
        final List<? extends T> list;
        final int lo;
        final int hi;
        final int gran;
        FJMax<T> next;

        FJMax(List<? extends T> list, 
              int lo, 
              int hi, 
              int gran) {
            this.list = list;
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
                    new FJMax(list, mid + 1, h, g);
                r.fork();
                r.next = right;
                right = r;
                h = mid;
            }
            T x = null;;
            for (int i = l; i <= h; ++i) {
                T y = list.get(i);
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
        final List<? extends T> list;
        final Predicate<? super T> pred;
        final AtomicReference<T> result;
        final int lo;
        final int hi;
        final int gran;

        FJFindAny(List<? extends T> list, 
                  Predicate<? super T> pred,
                  AtomicReference<T> result,
                  int lo, 
                  int hi, 
                  int gran) {
            this.list = list;
            this.pred = pred;
            this.result = result;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
        }

        void seqCompute() {
            for (int i = lo; i < hi; ++i) {
                T x = list.get(i);
                if (pred.evaluate(x)) {
                    result.compareAndSet(null, x);
                    break;
                }
            }
        }

        protected void compute() {
            if (result.get() != null)
                return;
            if (hi - lo < gran) {
                seqCompute();
                return;
            }
            int mid = (lo + hi) >>> 1;
            FJFindAny<T> left = 
                new FJFindAny<T>(list, pred, result, lo, mid, gran);
            left.fork();
            FJFindAny<T> right = 
                new FJFindAny<T>(list, pred, result, mid + 1, hi, gran);
            right.invoke();
            if (result.get() != null)
                left.cancel();
            else
                left.join();
        }
    }              

    static final class FJFindAllDriver<T, U> extends RecursiveAction {
        final List<? extends T> list;
        final Predicate<? super T> pred;
        final Mapper<? super T, ? extends U> mapper;
        final int gran;
        U[] results;
        
        FJFindAllDriver(List<? extends T> list, 
                        Predicate<? super T> pred,
                        Mapper<? super T, ? extends U> mapper,
                        int gran) {
            this.list = list;
            this.pred = pred;
            this.mapper = mapper;
            this.gran = gran;
        }

        protected void compute() {
            int n = list.size();
            FJFindAll<T, U> r = new FJFindAll<T, U>(list, pred, mapper,
                                                    0, n, gran);
            r.compute();
            int rm = r.nmatches;
            U[] res = (U[]) new Object[rm];
            this.results = res;
            r.results = res;
            r.compute();
        }
    }

    /**
     * Fork/Join version of FindAll.  Proceeds in two passes. In the
     * first pass, indices of matching elements are recorded in match
     * array.  In second pass, once the size of results is known and
     * result array is constructed in driver, the matching elements
     * are placed into corresponding result positions.
     *
     * As a compromise to get good performance in cases of both dense
     * and sparse result sets, the matches array is allocated only on
     * demand, and subtask calls for empty subtrees are suppressed.
     */
    static final class FJFindAll<T, U> extends RecursiveAction {
        final List<? extends T> list;
        final Predicate<? super T> pred;
        final Mapper<? super T, ? extends U> mapper;
        final int lo;
        final int hi;
        final int gran;
        int[] matches;
        int nmatches;
        int offset;
        U[] results;
        FJFindAll<T,U> left, right;

        FJFindAll(List<? extends T> list, 
                  Predicate<? super T> pred,
                  Mapper<? super T, ? extends U> mapper,
                  int lo, 
                  int hi, 
                  int gran) {
            this.list = list;
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

        private void leafPhase1() {
            int[] m = null; // only construct if find at least one match
            int n = 0;
            for (int j = lo; j < hi; ++j) {
                if (pred.evaluate(list.get(j))) {
                    if (m == null)
                        m = new int[hi - j];
                    m[n++] = j;
                }
            }
            nmatches = n;
            matches = m; 
        }

        private void leafPhase2() {
            int[] m = matches;
            if (m != null) {
                int n = nmatches;
                int k = offset;
                Mapper<? super T, ? extends U> mapper = this.mapper;
                if (mapper != null) {
                    for (int i = 0; i < n; ++i)
                        results[k++] = mapper.map(list.get(m[i]));
                }
                else {
                    for (int i = 0; i < n; ++i)
                        results[k++] = (U)(list.get(m[i]));
                }
            }
        }

        private void internalPhase1() {
            int mid = (lo + hi) >>> 1;
            FJFindAll<T, U> l = 
                new FJFindAll<T, U>(list, pred, mapper, lo,    mid, gran);
            FJFindAll<T, U> r = 
                new FJFindAll<T, U>(list, pred, mapper, mid,   hi,  gran);
            coInvoke(l, r);
            int lnm = l.nmatches;
            if (lnm != 0)
                left = l;
            int rnm = r.nmatches;
            if (rnm != 0)
                right = r;
            nmatches = lnm + rnm;
        }

        private void internalPhase2() {
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

}
