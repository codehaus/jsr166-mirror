package java.util.concurrent;

/**
 * Cancellable objects provide methods to cancel the asynchronous
 * operations they represent, and query their status to determine if
 * they have been cancelled.
 * @see FutureTask
 **/
public interface Cancellable {
    /**
     * Cancel execution of this task if it has not already completed.
     * If the task has not started when cancel() is called, the task
     * will be cancelled.  If it has already started, then whether or
     * not the computation is cancelled depends on the value of the
     * interruptIfRunning argument.
     * @param interruptIfRunning true if the execution of the run method
     * computing this value should be interrupted. Otherwise,
     * in-progress executions are allowed to complete.
     * @return true unless the task has already been cancelled or
     * completed.
     **/
    public boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Return true if this task was cancelled before completion.
     **/
    public boolean isCancelled();

    /**
     * Return true if this task has completed, either normally or via
     * cancellation.
     **/
    public boolean isDone();

}

