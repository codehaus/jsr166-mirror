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
 * class Fibonacci extends ForkJoinTask&lt;Integer&gt; {
 *   final int n;
 *   Fibonnaci(int n) { this.n = n; }
 *   Integer compute() {
 *     if (n &lt;= 1)
 *        return n;
 *     Fibonacci f1 = new Fibonacci(n - 1);
 *     Fibonacci f2 = new Fibonacci(n - 2);
 *     coInvoke(f1, f2);
 *     return f1.join() + f2.join();
 *   }
 * }
 * </pre>
 * However, besides being a dumb way to compute fibonacci functions
 * (there is a simple fast linear algorithm that you'd use in
 * practice), this would likely not perform too well because
 * the smallest subtasks are too small to be worthwhile splitting
 * up. Instead, as is the case for nearly all fork/join applications,
 * you'd pick some minimum granularity size (for example 10 here)
 * for which you always sequentially solve rather than subdividing.
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

    final RuntimeException exec() {
        if (exception == null) {
            try {
                result = compute();
            } catch(RuntimeException rex) {
                exceptionUpdater.compareAndSet(this, null, rex);
            }
        }
        return setDone();
    }

    public final V invoke() {
        V v = null;
        try {
            if (exception == null)
                result = v = compute();
        } catch(RuntimeException rex) {
            exceptionUpdater.compareAndSet(this, null, rex);
            throw rex;
        }
        RuntimeException ex = setDone();
        if (ex != null)
            throw ex;
        return v;
    }

    public final V join() {
        return ((ForkJoinPool.Worker)(Thread.currentThread())).joinTask(this);
    }

    public final V getResult() { 
        return result; 
    }

    public final void reinitialize() {
        result = null;
        super.reinitialize();
    }

}
