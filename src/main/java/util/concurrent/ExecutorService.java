/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

import java.util.List;
import java.util.Collection;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

/**
 * An <tt>Executor</tt> that provides methods to manage termination
 * and methods that can produce a {@link Future} for tracking
 * progress of one or more asynchronous tasks.
 * <p>
 *
 * An <tt>ExecutorService</tt> can be shut down, which will cause it
 * to stop accepting new tasks.  After being shut down, the executor
 * will eventually terminate, at which point no tasks are actively
 * executing, no tasks are awaiting execution, and no new tasks can be
 * submitted.
 *
 * <p> Method <tt>submit</tt> extends base method <tt>execute</tt> by
 * creating and returning a {@link Future} that can be used to cancel
 * execution and/or wait for completion.  Methods <tt>invokeAny</tt>
 * and <tt>invokeAll</tt> perform the most commonly useful forms of
 * bulk execution, executing a collection of tasks and then waiting
 * for at least one, or all to complete. (Class {@link
 * ExecutorCompletionService} can be used to write customizable
 * versions of such methods.)
 *
 * <p>The {@link Executors} class provides factory methods for the
 * executor services provided in this package.
 *
 * @since 1.5
 * @author Doug Lea
 */
public interface ExecutorService extends Executor {


    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be
     * accepted. Invocation has no additional effect if already shut
     * down.
     * @throws SecurityException if a security manager exists and
     * shutting down this ExecutorService may manipulate threads that
     * the caller is not permitted to modify because it does not hold
     * {@link java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     * or the security manager's <tt>checkAccess</tt>  method denies access.
     */
    void shutdown();

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks that were
     * awaiting execution. 
     *  
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  For example, typical
     * implementations will cancel via {@link Thread#interrupt}, so if any
     * tasks mask or fail to respond to interrupts, they may never terminate.
     *
     * @return list of tasks that never commenced execution
     * @throws SecurityException if a security manager exists and
     * shutting down this ExecutorService may manipulate threads that
     * the caller is not permitted to modify because it does not hold
     * {@link java.lang.RuntimePermission}<tt>("modifyThread")</tt>,
     * or the security manager's <tt>checkAccess</tt> method denies access.
     */
    List<Runnable> shutdownNow();

    /**
     * Returns <tt>true</tt> if this executor has been shut down.
     *
     * @return <tt>true</tt> if this executor has been shut down
     */
    boolean isShutdown();

    /**
     * Returns <tt>true</tt> if all tasks have completed following shut down.
     * Note that <tt>isTerminated</tt> is never <tt>true</tt> unless
     * either <tt>shutdown</tt> or <tt>shutdownNow</tt> was called first.
     *
     * @return <tt>true</tt> if all tasks have completed following shut down
     */
    boolean isTerminated();

    /**
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is
     * interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return <tt>true</tt> if this executor terminated and <tt>false</tt>
     * if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;


    /**
     * Submits a value-returning task for execution and returns a Future
     * representing the pending results of the task.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution
     */
    <T> Future<T> submit(Callable<T> task);

    /**
     * Submits a Runnable task for execution and returns a Future 
     * representing that task.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task,
     * and whose <tt>get()</tt> method will return <tt>null</tt>
     * upon completion.
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution
     */
    Future<?> submit(Runnable task);

    /**
     * Executes a value-returning task and blocks until it returns a
     * value or throws an exception.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if task cannot be scheduled
     * for execution
     * @throws InterruptedException if interrupted while waiting for
     * completion
     * @throws ExecutionException if the task encountered an exception
     * while executing
     */
    <T> T invoke(Callable<T> task) throws ExecutionException, InterruptedException;

    /**
     * Executes the given tasks, returning their results
     * when all complete.
     * Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.
     * @param tasks the collection of tasks
     * @return A list of Futures representing the tasks, in the same
     * sequential order as produced by the iterator for the given task list, each of which has
     * completed.
     * @throws InterruptedException if interrupted while waiting, in
     * which case unfinished tasks are cancelled.
     * @throws NullPointerException if tasks or any of its elements are <tt>null</tt>
     * @throws RejectedExecutionException if any task cannot be scheduled
     * for execution
     */

    <T> List<Future<T>> invokeAll(Collection<Callable<T>> tasks)
        throws InterruptedException;

    /**
     * Executes the given tasks, returning their results
     * when all complete or the timeout expires, whichever happens first.
     * Upon return, tasks that have not completed are cancelled.
     * Note that a <em>completed</em> task could have
     * terminated either normally or by throwing an exception.
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return A list of Futures representing the tasks, in the same
     * sequential order as as produced by the iterator for the given task list. If the operation did
     * not time out, each task will have completed. If it did time
     * out, some of thiese tasks will not have completed.
     * @throws InterruptedException if interrupted while waiting, in
     * which case unfinished tasks are cancelled.
     * @throws NullPointerException if tasks, any of its elements, or
     * unit are <tt>null</tt>
     * @throws RejectedExecutionException if any task cannot be scheduled
     * for execution
     */
    <T> List<Future<T>> invokeAll(Collection<Callable<T>> tasks, 
                                  long timeout, TimeUnit unit) 
        throws InterruptedException;

    /**
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do. Upon normal or exceptional return,
     * tasks that have not completed are cancelled.
     * @param tasks the collection of tasks
     * @return The result returned by one of the tasks.
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks or any of its elements
     * are <tt>null</tt>
     * @throws IllegalArgumentException if tasks empty
     * @throws ExecutionException if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled
     * for execution
     */
    <T> T invokeAny(Collection<Callable<T>> tasks)
        throws InterruptedException, ExecutionException;

    /**
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do before the given timeout elapses.
     * Upon normal or exceptional return, tasks that have not
     * completed are cancelled.
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return The result returned by one of the tasks.
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks, any of its elements, or
     * unit are <tt>null</tt>
     * @throws TimeoutException if the given timeout elapses before
     * any task successfully completes
     * @throws ExecutionException if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled
     * for execution
     */
    <T> T invokeAny(Collection<Callable<T>> tasks, 
                    long timeout, TimeUnit unit) 
        throws InterruptedException, ExecutionException, TimeoutException;

}
