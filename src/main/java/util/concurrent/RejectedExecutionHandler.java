/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * A handler for tasks that cannot be executed by a {@link ThreadedExecutor}.
 *
 * @since 1.5
 *
 * @spec JSR-166
 * @revised $Date: 2003/05/27 15:50:14 $
 * @editor $Author: dl $
 */
public interface RejectedExecutionHandler {
    
    /**
     * Method invoked by <tt>ThreadPoolExecutor</tt> when
     * <tt>execute</tt> cannot submit a task. This may occur when no
     * more threads or queue slots are available because their bounds
     * would be exceeded, or upon shutdown of the Executor.
     *
     * In the absence other alternatives, the method may throw an
     * unchecked <tt>RejectedExecutionException</tt>, which will be
     * propagated to the caller of <tt>execute</tt>.
     *
     * @param r the runnable task requested to be executed
     * @param executor the executor attempting to execute this task
     * @throws RejectedExecutionException if there is no remedy
     */
    void rejectedExecution(Runnable r, ThreadPoolExecutor executor);
}
