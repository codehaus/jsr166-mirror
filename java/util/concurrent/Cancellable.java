package java.util.concurrent;

/**
 * Cancellable objects provide methods to cancel the asynchronous
 * operations they represent, and to query their status to determine if
 * they have been cancelled.
 * @see FutureTask
 */
public interface Cancellable {

    /**
     * Cancel execution of this task if it has not already completed.
     * If the task has not started when <tt>cancel</tt> is called, the task
     * will be cancelled.  If it has already started, then whether or
     * not the computation is cancelled depends on the value of the
     * <tt>interruptIfRunning</tt> argument.
     *
     * @param interruptIfRunning <tt>true</tt> if execution of the <tt>run</tt>
     * method computing this value should be interrupted. Otherwise,
     * in-progress executions are allowed to complete.
     * @return <tt>true</tt> unless the task has already been cancelled or
     * completed.
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Returns <tt>true</tt> if this task was cancelled before completion.
     *
     * @return <tt>true</tt> if this task was cancelled before completion.
     */
    boolean isCancelled();

    /**
     * Returns <tt>true</tt> if this task has completed, either normally or via
     * cancellation.
     *
     * @return <tt>true</tt> if this task has completed normally or was cancelled.
     */
    boolean isDone();
}
