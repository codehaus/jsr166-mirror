/*
 * @(#)CannotExecuteHandler.java
 */

package java.util.concurrent;

/**
 * A handler for tasks that cannot be executed by a <tt>ThreadExecutor</tt>.
 *
 * @since 1.5
 * @spec JSR-166
 */
public interface CannotExecuteHandler {
    
    /**
     * Method invoked by <tt>ThreadExecutor</tt> when <tt>execute</tt> cannot
     * submit a task. This may occur when no more threads or queue slots are
     * available because their bounds would be exceeded, or upon
     * shutdown of the Executor. The <tt>isShutdown</tt> parameter can be
     * used to distinguish between these cases.
     *
     * Returns false if <tt>execute</tt> should be retried, else true.
     * You may alternatively throw an unchecked <tt>CannotExecuteException</tt>,
     * which will be propagated to the caller of <tt>execute</tt>.
     *
     * @param r the runnable task requested to be executed
     * @param isShutdown true if the ThreadExecutor has been shutdown
     * @return false if <tt>execute</tt> should be retried, else true
     * @throws CannotExecuteException if there is no remedy
     */
    boolean cannotExecute(Runnable r, boolean isShutdown);
}
