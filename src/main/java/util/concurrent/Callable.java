/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * An inteface for describing an asynchronous task that returns a result and may throw an exception.
 * Implementors define a single method with no arguments called <tt>call</tt>.
 *
 * <p>The <tt>Callable</tt> interface is similar to {@link Runnable}, in that
 * both are designed for classes whose instances are potentially executed by another thread.
 * A <tt>Runnable</tt>, however, does not return a result and cannot throw a
 * checked exception.
 *
 * @fixme Should "asynchronous task" be defined somewhere?
 *
 * @since 1.5
 * @see Executor
 * @see FutureTask
 *
 * @spec JSR-166
 * @revised $Date: 2003/06/23 02:26:16 $
 * @editor $Author: brian $
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
