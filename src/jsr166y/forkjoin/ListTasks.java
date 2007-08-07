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
     * default granularity for divide-by-two tasks. Provides about
     * four times as many finest-grained tasks as there are CPUs.
     */
    static int defaultGranularity(ForkJoinExecutor ex, int n) {
        int threads = ex.getParallelismLevel();
        return 1 + n / ((threads << 2) - 3);
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
        ex.invoke(new FJApplyer<T>(list, proc, 0, n-1, defaultGranularity(ex, n), null));
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
        return ex.invoke(new FJReducer<T>(list,reducer, base,
                                              0, n-1, defaultGranularity(ex, n)));
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
        return ex.invoke(new FJMapReducer<T, U>(list, mapper, reducer, 
                                                  base,
                                                  0, n-1, defaultGranularity(ex, n)));
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
        List<U> dest = Arrays.asList((U[])new Object[n]);
        ex.invoke(new FJMapper<T,U>(list, dest, mapper,  
                                      0, n-1, defaultGranularity(ex, n)));
        return dest;
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
                                     0, n-1, defaultGranularity(ex, n)));
        return result.get();
    }

    /**
     * Returns a list of all elements of the list matching pred
     * @param ex the executor
     * @param list the list
     * @param pred the predicate
     */
    public static <T> List<T> findAll(ForkJoinExecutor ex,
                                                List<? extends T> list, 
                                                Predicate<? super T> pred) {
        int n = list.size();
        Vector<T> dest = new Vector<T>(); // todo: use smarter list
        ex.invoke(new FJFindAll<T>(list, pred, dest,  
                                     0, n-1, defaultGranularity(ex, n)));
        return dest;
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
        final List<? extends T> list;
        final Procedure<? super T> f;
        final int lo;
        final int hi;
        final int gran;
        FJApplyer<T> next;

        FJApplyer(List<? extends T> list, Procedure<? super T> f, int lo, int hi, int gran, FJApplyer<T> next){
            this.list = list;
            this.f = f;
            this.lo = lo; 
            this.hi = hi;
            this.gran = gran;
            this.next = next;
        }

        protected void compute() {
            FJApplyer<T> right = null;
            int l = lo;
            int h = hi;
            int g = gran;
            while (h - l > g) {
                int mid = (l + h) >>> 1;
                right = new FJApplyer<T>(list, f, mid+1, h, g, right);
                right.fork();
                h = mid;
            }
            for (int i = l; i <= h; ++i)
                f.apply(list.get(i));
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
        final List<? extends T> list;
        final Mapper<? super T, ? extends U> mapper;
        final Reducer<U> reducer;
        final U base;
        final int lo;
        final int hi;
        final int gran;
        FJMapReducer<T, U> next;

        FJMapReducer(List<? extends T> list, 
                   Mapper<? super T, ? extends U> mapper,
                   Reducer<U> reducer,
                   U base,
                   int lo, 
                   int hi, 
                   int gran) {
            this.list = list;
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
                    new FJMapReducer<T, U>(list, mapper, reducer, 
                                           base, mid + 1, h, g);
                r.fork();
                r.next = right;
                right = r;
                h = mid;
            }
            U x = base;
            for (int i = l; i <= h; ++i)
                x = reducer.combine(x, mapper.map(list.get(i)));
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
        final List<U> dest;
        final Mapper<? super T, ? extends U> mapper;
        final int lo;
        final int hi;
        final int gran;
        FJMapper<T, U> next;

        FJMapper(List<? extends T> list, 
                 List<U> dest,
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
                    new FJMapper<T, U>(list, dest, mapper, mid + 1, h, g);
                r.fork();
                r.next = right;
                right = r;
                h = mid;
            }
            for (int i = l; i <= h; ++i)
                dest.set(i, mapper.map(list.get(i)));
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
        final List<? extends T> list;
        final Reducer<T> reducer;
        final T base;
        final int lo;
        final int hi;
        final int gran;
        FJReducer<T> next;

        FJReducer(List<? extends T> list, 
                  Reducer<T> reducer,
                  T base,
                  int lo, 
                  int hi, 
                  int gran) {
            this.list = list;
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
                    new FJReducer<T>(list, reducer, base, mid + 1, h, g);
                r.fork();
                r.next = right;
                right = r;
                h = mid;
            }
            T x = base;
            for (int i = l; i <= h; ++i)
                x = reducer.combine(x, list.get(i));
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
            for (int i = lo; i <= hi; ++i) {
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
            if (hi - lo <= gran) {
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

    /**
     * Fork/Join version of FindAll
     */
    static final class FJFindAll<T> extends RecursiveAction {
        final List<? extends T> list;
        final Predicate<? super T> pred;
        final List<T> result;
        final int lo;
        final int hi;
        final int gran;

        FJFindAll(List<? extends T> list, 
                  Predicate<? super T> pred,
                  List<T> result,
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
            for (int i = lo; i <= hi; ++i) {
                T x = list.get(i);
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
                new FJFindAll<T>(list, pred, result, lo, mid, gran);
            FJFindAll<T> right = 
                new FJFindAll<T>(list, pred, result, mid + 1, hi, gran);
            coInvoke(left, right);
        }
    }              


}
