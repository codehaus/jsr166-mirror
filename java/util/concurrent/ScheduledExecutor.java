/*
 * @(#)ScheduledExecutor.java
 */

package java.util.concurrent;

/**
 * An <tt>Executor</tt> that can schedule tasks to run after a given delay,
 * or to execute periodically. This class is preferable to
 * <tt>java.util.Timer</tt> when multiple worker threads are needed,
 * or when the additional flexibility or capabilities of
 * <tt>ThreadExecutor</tt> are required.  Tasks submitted using the
 * <tt>execute</tt> method are scheduled as if they had a requested
 * delay of zero. 
 *
 * @see Executors
 * @since 1.5
 * @spec JSR-166
 */
public class ScheduledExecutor extends ThreadExecutor {

    /**
     * Creates a new ScheduledExecutor with the given initial parameters.
     * 
     * @param minThreads the minimum number of threads to keep in the pool,
     * even if they are idle
     * @param maxThreads the maximum number of threads to allow in the pool
     * @param keepAliveTime when the number of threads is greater than
     * the minimum, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating
     * @param granularity the time unit for the keepAliveTime argument
     */
    public ScheduledExecutor(int minThreads,
                             int maxThreads,
                             long keepAliveTime,
                             TimeUnit granularity) {
        super(minThreads, maxThreads, keepAliveTime, granularity,
              new PriorityBlockingQueue());
    }

    /**
     * A delayed or periodic task that can be run by a <tt>ScheduledExecutor</tt>.
     */
    public abstract class ScheduledTask implements Runnable, Cancellable, Comparable {

        /**
         * Returns the time interval until this task can run next,
         * in the specified unit.
         *
         * @param unit time unit for interval returned
         * @return time interval until task can run again
         */
        long getExecutionTime(TimeUnit unit) {
            return 0;
        }

        /**
         * Constructs scheduled task.
         * @fixme default package access?
         */
        ScheduledTask() {
        }
    }

    /**
     * A delayed result-bearing action that can be run by a <tt>ScheduledExecutor</tt>.
     *
     * @see Future
     */
    public abstract class ScheduledFutureTask extends ScheduledTask implements Future {

        /**
         * Constructs scheduled future task.
         * @fixme default package access?
         */
        ScheduledFutureTask() {
        }
    }

    /**
     * Creates a delayed task that can be scheduled on this executor.
     * A delayed task becomes eligible to run after the specified delay expires.
     *
     * @param r ???
     * @param delay ???
     * @param unit ???
     * @return a task that becomes eligible to run after the specified delay
     */
    public static ScheduledTask newDelayedTask(Runnable r, long delay, TimeUnit unit) {
        return null;
    }

    /**
     * Creates a periodic task.  A periodic task is like a
     * delayed task, except that upon its completion, it is rescheduled
     * for repeated execution after the specified delay.  Periodic tasks
     * will run no more than once every delay cycle, but are subject to
     * drift over time.
     *
     * @param r ???
     * @param delay ???
     * @param period ???
     * @param unit ???
     * @return a task that executes periodically
     */
    public static ScheduledTask newPeriodicTask(Runnable r, long delay, long period, TimeUnit unit) {
        return null;
    }

    /**
     * Creates a fixed-rate task.  A fixed rate task is like
     * a periodic task, except that upon completion, it is rescheduled
     * to run at the specified delay after the scheduled start time of
     * the current execution.  ScheduledExecutor attempts to execute
     * fixed rate tasks at the desired frequency, even if the previous
     * execution was delayed.
     *
     * @param r ???
     * @param delay ???
     * @param period ???
     * @param unit ???
     * @return a task that executes periodically at a fixed rate
     */
    public static ScheduledTask newFixedRateTask(Runnable r, long delay, long period, TimeUnit unit) {
        return null;
    }

    /**
     * Creates a delayed Callable task, which computes a result.
     *
     * @param c ???
     * @param delay ???
     * @param unit ???
     * @return a task that computes a result after the specified delay
     */
    public static ScheduledFutureTask newDelayedFutureTask(Callable c, long delay, TimeUnit unit) {
        return null;
    }

    /**
     * Schedules a ScheduledTask for execution.
     *
     * @param t ???
     * @throws CannotExecuteException if command cannot be scheduled for
     * execution
     */
    public void schedule(ScheduledTask t) {
    }
}


/*
 * @fixme static factories on internal or external classes?
 */
