/*
 * @(#)BlockedExecutionHandler.java
 */

package java.util.concurrent;

/**
 * A handler for tasks that cannot be executed by a {@link ThreadedExecutor}.
 *
 * @since 1.5
 *
 * @spec JSR-166
 * @revised $Date: 2003/05/25 19:31:15 $
 * @editor $Author: tim $
 */
public interface BlockedExecutionHandler {

    /**
     * Method invoked by <tt>ThreadedExecutor</tt> when <tt>execute</tt> cannot
     * submit a task. This may occur when no more threads or queue slots are
     * available because their bounds would be exceeded, or upon
     * shutdown of the Executor.
     *
     * Returns true if <tt>execute</tt> should be retried, else false.
     * You may alternatively throw an unchecked <tt>BlockedExecutionException</tt>,
     * which will be propagated to the caller of <tt>execute</tt>.
     *
     * @param r the runnable task requested to be executed
     * @param executor the executor attempting to execute this task
     * @return true if <tt>execute</tt> should be retried, else false
     * @throws BlockedExecutionException if there is no remedy
     */
    boolean canRetryExecution(Runnable r, ThreadPoolExecutor executor);
}
