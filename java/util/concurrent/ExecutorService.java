/*
 * @(#)ExecutorService.java
 */

package java.util.concurrent;

import java.util.List;

/**
 * An <tt>Executor</tt> that provides lifecycle-management functions. 
 *
 * <p> An <tt>ExecutorService</tt> can be shut down, which will cause it to stop
 * processing submitted tasks.  After being shut down, it will
 * normally reach a quiescent terminal state in which no tasks are executing
 * and no new ones will be processed.
 *
 * <p> Several concrete implementations of <tt>ExecutorService</tt> are
 * included in <tt>java.util.concurrent</tt>, including <tt>ThreadExecutor</tt>,
 * a flexible thread pool, and <tt>ScheduledExecutor</tt>, which adds support
 * for timed, delayed and periodic task execution.
 *
 * <p> The <tt>Executors</tt> class provides factory methods for all of the
 * types of executors provided in <tt>java.util.concurrent</tt>.
 *
 * @see Executors
 * @since 1.5
 * @spec JSR-166
 */
public interface ExecutorService extends Executor {

    /**
     * Initiates an orderly shutdown in which previously submitted tasks
     * are executed, but new tasks submitted to <tt>execute</tt> subsequent to
     * calling <tt>shutdown</tt> are not.
     *
     * <p>The exact fate of tasks submitted in subsequent calls to
     * <tt>execute</tt> is left unspecified in this interface.
     * Implementations may provide different options, such
     * as ignoring them, or causing <tt>execute</tt> to throw an
     * (unchecked) <tt>IllegalStateException</tt>.
     */
    void shutdown();

    /**
     * Attempts to stop processing all actively executing tasks, never
     * start processing previously submitted tasks that have not yet
     * commenced execution, and cause subsequently submitted tasks not
     * to be processed.  The exact fate of tasks submitted in
     * subsequent calls to <tt>execute</tt> is left unspecified in
     * this interface. Implementations may provide different options,
     * such as ignoring them, or causing <tt>execute</tt> to throw an
     * (unchecked) <tt>IllegalStateException</tt>.  Similarly, there
     * are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  For example typical
     * thread-based Executors will cancel via
     * <tt>Thread.interrupt</tt>, so if any tasks mask or fail to
     * respond to interrupts, they might never terminate.
     *
     * @return a list of all tasks that never commenced execution
     */
    List shutdownNow();

    /**
     * Returns <tt>true</tt> if this executor has been shut down.
     *
     * @return <tt>true</tt> if this executor has been shut down
     */
    boolean isShutdown();

    /**
     * Interrupts the processing of all current tasks.  Depending on
     * whether the tasks ignore the <tt>InterruptedException</tt>, this may or
     * may not speed the completion of queued tasks, and may cause
     * improperly written tasks to fail.  This executor remains enabled
     * for future executions.
     *
     * @fixme needs to document why you might want to interrupt the current task set
     */
    void interrupt();

    /**
     * Returns <tt>true</tt> if all tasks have completed following shut down.
     * Note that <tt>isTerminated</tt> is never <tt>true</tt> unless
     * <tt>shutdown</tt> or <tt>shutdownNow</tt> have been invoked.
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
     * if waiting time elapsed before termination.
     * @throws IllegalStateException if not shut down
     * @throws InterruptedException if interrupted while waiting
     */
    boolean awaitTermination(long timeout, TimeUnit granularity)
        throws InterruptedException;
}
