/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * A service that decouples the production of new asynchronous tasks
 * and the comsumption of the reasults of completed tasks.
 */
public interface CompletionService<V> {
    /**
     * Submits a value-returning task for execution and returns a Future
     * representing the pending results of the task. Upon completion,
     * this task may be taken or polled.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution
     */
    Future<V> submit(Callable<V> task);


    /**
     * Submits a Runnable task for execution and returns a Future 
     * representing that task.Upon completion,
     * this task may be taken or polled.
     *
     * @param task the task to submit
     * @param result the result to return upon successful completion
     * @return a Future representing pending completion of the task,
     * and whose <tt>get()</tt> method will return the given result value 
     * upon completion
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution
     */
    Future<V> submit(Runnable task, V result);

    /**
     * Retrieves and removes the Future representing the next
     * completed task, waiting if none are yet present.
     * @return the Future representing the next completed task
     * @throws InterruptedException if interrupted while waiting.
     */
    Future<V> take() throws InterruptedException;


    /**
     * Retrieves and removes the Future representing the next
     * completed task or <tt>null</tt> if none are present.
     *
     * @return the Future representing the next completed task, or
     * <tt>null</tt> if none are present.
     */
    Future<V> poll();

    /**
     * Retrieves and removes the Future representing the next
     * completed task, waiting if necessary up to the specified wait
     * time if none are yet present.
     * @param timeout how long to wait before giving up, in units of
     * <tt>unit</tt>
     * @param unit a <tt>TimeUnit</tt> determining how to interpret the
     * <tt>timeout</tt> parameter
     * @return the Future representing the next completed task or
     * <tt>null</tt> if the specified waiting time elapses before one
     * is present.
     * @throws InterruptedException if interrupted while waiting.
     */
    Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException;
}
