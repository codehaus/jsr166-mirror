/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166y.forkjoin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Recursive result-bearing ForkJoinTasks.
 * <p> For a classic example, here is a task computing Fibonacci numbers:
 *
 * <pre>
 * class Fibonacci extends RecursiveTask&lt;Integer&gt; {
 *   final int n;
 *   Fibonnaci(int n) { this.n = n; }
 *   Integer compute() {
 *     if (n &lt;= 1)
 *        return n;
 *     Fibonacci f1 = new Fibonacci(n - 1);
 *     f1.fork();
 *     Fibonacci f2 = new Fibonacci(n - 2);
 *     return f2.invoke() + f1.join();
 *   }
 * }
 * </pre>
 *
 * However, besides being a dumb way to compute Fibonacci functions
 * (there is a simple fast linear algorithm that you'd use in
 * practice), this is likely to perform poorly because the smallest
 * subtasks are too small to be worthwhile splitting up. Instead, as
 * is the case for nearly all fork/join applications, you'd pick some
 * minimum granularity size (for example 10 here) for which you always
 * sequentially solve rather than subdividing.  Note also the use of
 * <tt>f2.invoke()</tt> instead of <tt>f2.fork(); f2.join()</tt>,
 * which is both more convenient and more efficient.
 *
 */
public abstract class RecursiveTask<V> extends ForkJoinTask<V> {
    /**
     * The result returned by compute method.
     */
    V result;

    /**
     * The main computation performed by this task.  While you must
     * define this method, you should not in general call it directly.
     * To immediately perform the computation, use <tt>invoke</tt>.
     * @return result of the computation
     */
    protected abstract V compute();

    public final V rawResult() { 
        return result; 
    }

    public final V invoke() {
        V v = null;
        if (exception == null) {
            try {
                result = v = compute();
            } catch(Throwable rex) {
                setDoneExceptionally(rex);
            }
        }
        Throwable ex = setDone();
        if (ex != null)
            rethrowException(ex);
        return v;
    }

    public final Throwable exec() {
        if (exception == null) {
            try {
                result = compute();
            } catch(Throwable rex) {
                return setDoneExceptionally(rex);
            }
        }
        return setDone();
    }

    public final void finish(V result) { 
        this.result = result;
        setDone();
    }

    public final void finishExceptionally(Throwable ex) {
        setDoneExceptionally(ex);
    }

    public final void reinitialize() {
        result = null;
        super.reinitialize();
    }

}
