/*
 * @(#)ExecutorService.java
 */

package java.util.concurrent;

import java.util.List;

/**
 * An executor that provides methods to manage termination.  An
 * <tt>ExecutorService</tt> can be shut down, which will cause it to
 * stop accepting new tasks.  After being shut down, the executor will
 * eventually terminate, at which point no tasks are actively
 * executing, no tasks are awaiting execution, and no new tasks can be
 * submitted.
 *
 * <p>The <tt>Executors</tt> class provides factory methods for the
 * executors provided in <tt>java.util.concurrent</tt>.
 *
 * @since 1.5
 * @see Executors
 *
 * @spec JSR-166
 * @revised $Date: 2003/05/25 19:31:16 $
 * @editor $Author: tim $
 */
public interface ExecutorService extends Executor {

    /**
     * Initiates an orderly shutdown in which previously submitted tasks
     * are executed, but no new tasks will be accepted.
     *
     * After shutdown, subsequent calls to <tt>execute</tt> will be handled
     * by the current <tt>BlockedExecutionHandler</tt>, which may discard the
     * submitted tasks, or throw a <tt>BlockedExecutionException</tt>,
     * among other options.
     */
    void shutdown();

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks that were
     * awaiting execution. As with <tt>shutdown</tt>, subsequent calls to
     * <tt>execute</tt> will be handled by the current <tt>BlockedExecutionHandler</tt>.
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
     * @throws IllegalStateException if not shut down
     * @throws InterruptedException if interrupted while waiting
     */
    boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;
}
