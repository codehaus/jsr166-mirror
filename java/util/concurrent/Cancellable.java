/*
 * @(#)Cancellable.java
 */

package java.util.concurrent;

/**
 * An asynchronous task that can be cancelled.  Cancellation is performed by
 * the <tt>cancel</tt> method.  Additional methods are provided to determine
 * if the task completed normally or was cancelled.
 *
 * @since 1.5
 * @spec JSR-166
 */
public interface Cancellable {

    /**
     * Cancels execution of this task if it has not already completed.
     * If the task has not started when <tt>cancel</tt> is called, the
     * task will be cancelled.  If the task has already started, then
     * the <tt>interruptIfRunning</tt> parameter determines whether the
     * thread executing this task should be interrupted.
     *
     * @param interruptIfRunning <tt>true</tt> if the thread executing this
     * task should be interrupted; otherwise, in-progress tasks are allowed
     * to complete
     * @return <tt>true</tt> unless the task has already been cancelled or
     * completed
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Returns <tt>true</tt> if this task was cancelled before it completed
     * normally.
     *
     * @return <tt>true</tt> if task was cancelled before it completed
     */
    boolean isCancelled();

    /**
     * Returns <tt>true</tt> if this task completed, either normally
     * or by cancellation.
     *
     * @return <tt>true</tt> if task completed normally or was cancelled
     */
    boolean isDone();
}
