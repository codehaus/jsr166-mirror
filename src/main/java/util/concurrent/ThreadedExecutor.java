/*
 * @(#)ThreadedExecutor.java
 */

package java.util.concurrent;

import java.util.List;

/**
 * An executor that executes submitted tasks on one or more dedicated threads.
 *
 * @fixme Document ThreadFactory, CannotExecuteHandler, Queue
 *
 * <p>A <tt>ThreadedExecutor</tt> can be shut down, which will cause it to stop
 * accepting new tasks.  After being shut down, the executor will 
 * eventually terminate, at which point no tasks are actively executing, no
 * tasks are awaiting execution, and no new tasks can be submitted.
 *
 * <p>Several concrete implementations of <tt>ThreadedExecutor</tt> are
 * included in <tt>java.util.concurrent</tt>, including <tt>ThreadPoolExecutor</tt>,
 * a flexible thread pool, and <tt>ScheduledExecutor</tt>, which adds support
 * for time-delayed and periodic execution.
 *
 * <p>The <tt>Executors</tt> class provides factory methods for the
 * executors provided in <tt>java.util.concurrent</tt>.
 *
 * @since 1.5
 * @see CannotExecuteHandler
 * @see Executors
 * @see ThreadFactory
 *
 * @spec JSR-166
 * @revised $Date: 2003/05/14 21:30:48 $
 * @editor $Author: tim $
 */
public interface ThreadedExecutor extends Executor {

    /*
     * Executor implementation. Overrides javadoc from Executor to indicate
     * that CannotExecuteHandler handles unexecutable tasks, and may throw
     * CannotExecuteException.
     */
    
    /**
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread, in a pooled thread, or even in the calling
     * thread, at the discretion of the <tt>ThreadedExecutor</tt>
     * implementation.
     *
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current <tt>CannotExecuteHandler</tt>.  
     *
     * @param command the task to execute
     * @throws CannotExecuteException at discretion of
     * <tt>CannotExecuteHandler</tt>, if task cannot be accepted for execution
     */
    void execute(Runnable command);

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     */
    void setThreadFactory(ThreadFactory threadFactory);

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     */
    ThreadFactory getThreadFactory();

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     */
    void setCannotExecuteHandler(CannotExecuteHandler handler);

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     */
    CannotExecuteHandler getCannotExecuteHandler();

    /**
     * Returns the task queue used by this executor.  Note that
     * this queue may be in active use.  Retrieveing the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    BlockingQueue getQueue();

    /* Life-cycle management methods. */

    /**
     * Initiates an orderly shutdown in which previously submitted tasks
     * are executed, but no new tasks will be accepted.
     *
     * After shutdown, subsequent calls to <tt>execute</tt> will be handled
     * by the current <tt>CannotExecuteHandler</tt>, which may discard the
     * submitted tasks, or throw a <tt>CannotExecuteException</tt>,
     * among other options.
     */
    void shutdown();

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks that were
     * awaiting execution. As with <tt>shutdown</tt>, subsequent calls to
     * <tt>execute</tt> will be handled by the current <tt>CannotExecuteHandler</tt>.
     *  
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  For example, typical
     * implementations will cancel via {@link Thread#interrupt}, so if any
     * tasks mask or fail to respond to interrupts, they may never terminate.
     *
     * @return list of tasks that never commenced execution
     */
    List shutdownNow();

    /**
     * Returns <tt>true</tt> if this executor has been shut down.
     *
     * @return <tt>true</tt> if this executor has been shut down
     */
    boolean isShutdown();

    /**
     * Interrupts the processing of all current tasks, while leaving this
     * executor enabled for future executions.
     * 
     * <p>Depending on whether the tasks ignore <tt>InterruptedException</tt>,
     * this method may or may not speed the completion of queued tasks,
     * and may cause improperly written tasks to fail.
     *
     * @fixme needs to document why you might want to interrupt the current task set
     */
    void interrupt();

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
     * @param granularity the time unit of the timeout argument
     * @return <tt>true</tt> if this executor terminated and <tt>false</tt>
     * if the timeout elapsed before termination
     * @throws IllegalStateException if not shut down
     * @throws InterruptedException if interrupted while waiting
     */
    boolean awaitTermination(long timeout, TimeUnit granularity)
        throws InterruptedException;
}
