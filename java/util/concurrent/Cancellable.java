package java.util.concurrent;

/**
 * Cancellable objects provide methods to cancel and check status
 * of asynchronous tasks.
 **/
public interface Cancellable {
  /**
   * Cancel execution of this task if it has not already completed.
   * @param mayInterruptIfRunning true if the execution of this task
   * may be interrupted if it is currently running.  Otherwise,
   * in-progress executions are allowed to complete.
   * @return true unless the task has already
   * been cancelled or completed.
   **/
  public boolean cancel(boolean mayInterruptIfRunning);

  /**
   * Return true if this task was cancelled before completion.
   **/
  public boolean isCancelled();

  /**
   * Return true if this task has completed,
   * either normally or via cancellation,
   **/
  public boolean isDone();

}

