/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 *
 */

package jsr166y.forkjoin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Resultless ForkJoinTasks with explicit completions.  Unlike some
 * other kinds of tasks, AsyncActions do not intrinisically complete
 * upon exit from their <tt>compute</tt> methods, but instead require
 * explicit invocation of their <tt>finish</tt> methods.
 *
 * <p> Unlike LinkedAsyncActions, AsyncActions do not establish links
 * to parent tasks or count child tasks.  This class can thus form a
 * more flexible basis for classes creating custom linkages.
 * 
 */
public abstract class AsyncAction extends ForkJoinTask<Void> {
    /**
     * The asynchronous part of the computation performed by this
     * task.  While you must define this method, you should not in
     * general call it directly (although you can invoke immediately
     * via <tt>exec</tt>.) If this method throws an excaption,
     * <tt>finishExceptionally</tt> is immediately invoked.
     */
    protected abstract void compute();

    /**
     * Equivalent to <tt>finish(null)</tt>.
     */
    public final void finish() {
        setDone();
    }

    public final void finish(Void result) {
        setDone();
    }

    public final void finishExceptionally(Throwable ex) {
        checkedSetDoneExceptionally(ex);
    }

    /**
     * Always returns null.
     * @return null
     */
    public final Void rawResult() { 
        return null; 
    }

    public final Void forkJoin() {
        exec();
        return join();
    }

    public final Throwable exec() {
        try {
            if (exception == null)
                compute();
        } catch(Throwable rex) {
            return setDoneExceptionally(rex);
        }
        return exception;
    }

}
