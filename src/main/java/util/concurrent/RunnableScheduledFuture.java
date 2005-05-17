/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package java.util.concurrent;

/**
 * A scheduled future that is also runnable.
 * @see FutureTask
 * @see Executor
 * @since 1.6
 * @author Doug Lea
 * @param <V> The result type returned by this Future's <tt>get</tt> method
 */
public interface RunnableScheduledFuture<V> extends Runnable, ScheduledFuture<V> {

    /**
     * Returns true if this is a periodic task. A periodic task may
     * re-run according to some schedule. A non-periodic task can be
     * run only once.
     * @return true if this task is periodic.
     */ 
    boolean isPeriodic();
}
