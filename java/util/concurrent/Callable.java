/*
 * @(#)Callable.java
 */

package java.util.concurrent;

/**
 * An asynchronous task that returns a result and may throw an exception.
 * Defines a single method of no arguments called <tt>call</tt>.
 * The <tt>Callable</tt> interface is similar to {@link Runnable}, in that
 * both are designed for classes whose instances are executed by a thread.
 * A <tt>Runnable</tt>, however, does not return a result and cannot throw a
 * checked exception.
 *
 * @fixme Should "asynchronous task" be defined somewhere?
 *
 * @see Executor
 * @see FutureTask
 * @since 1.5
 * @spec JSR-166
 * @revised $Date: 2003/02/19 10:53:58 $
 * @editor $Author: jozart $
 */
public interface Callable<V> {
    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     * @throws Exception if unable to compute a result
     */
    V call() throws Exception;
}
