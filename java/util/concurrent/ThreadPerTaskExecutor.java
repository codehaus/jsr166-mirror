/*
 * @(#)ThreadPerTaskExecutor.java
 */

package java.util.concurrent;

/**
 * An <tt>Executor</tt> that executes each submitted task in a new thread.
 * This executor makes no attempt to limit the number of tasks executing
 * concurrently.  It is similar in effect to calling
 * <tt>new Thread(runnable).start()</tt> and provides no efficiency savings
 * over manually creating new threads, but still offers the
 * manageability benefits of ThreadExecutor for tracking active
 * threads, shutdown, and so on.
 *
 * @since 1.5
 * @spec JSR-166
 */
public class ThreadPerTaskExecutor extends ThreadExecutor {

    /**
     * Constructs an executor using parameters that cause it to use a
     * new thread for each task.
     */
    public ThreadPerTaskExecutor() {
        super(0, Integer.MAX_VALUE, 0, TimeUnit.MILLISECONDS, new SynchronousQueue());
    }

    public int getActiveCount() {
        return super.getActiveCount();
    }

    public int getMaximumActiveCount() {
        return super.getMaximumActiveCount();
    }

    public int getCumulativeTaskCount() {
        return super.getCumulativeTaskCount();
    }

    public int getCumulativeCompletedTaskCount() {
        return super.getCumulativeCompletedTaskCount();
    }
}
