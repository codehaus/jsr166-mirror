/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * Something, usually a task, that can be cancelled.  Cancellation is
 * performed by the <tt>cancel</tt> method.  Additional methods are
 * provided to determine if the task completed normally or was
 * cancelled.
 *
 * @since 1.5
 *
 * @spec JSR-166
 * @revised $Date: 2003/06/23 02:26:16 $
 * @editor $Author: brian $
 * @see FutureTask
 * @see Executor
 */
public interface Cancellable {

    /**
     * Attempt to cancel execution of this task.  This attempt will
     * fail if the task has already completed, already been cancelled, or could not be
     * cancelled for some other reason. If successful, and this task
     * has not started when <tt>cancel</tt> is called, this task will
     * never run.  If the task has already started, then the
     * <tt>interruptIfRunning</tt> parameter determines whether the
     * thread executing this task should be interrupted in an attempt
     * to stop the task.
     *
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
     * task should be interrupted; otherwise, in-progress tasks are allowed
     * to complete
     * @return <tt>false</tt> if the task could not be cancelled,
     * typically because is has already completed normally;
     * <tt>true</tt> otherwise
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
     * Returns <tt>true</tt> if this task ran to completion or was cancelled.
     *
     * @return <tt>true</tt> if task completed normally or was cancelled
     */
    boolean isDone();
}
