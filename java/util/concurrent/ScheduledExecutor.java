package java.util.concurrent;

import java.util.*;

/**
 * A ScheduledExecutor is an Executor which can schedule tasks to run
 * at a given future time, or execute periodically. Tasks submitted
 * using <tt>execute</tt> are scheduled as if they had a requested
 * delay of zero. This class is preferable to <tt>java.util.Timer</tt>
 * when you need multiple worker threads or the additional flexibility
 * or capabilities of <tt>ThreadExecutor</tt>.
 * @see ThreadExecutor
 * */
public class ScheduledExecutor extends ThreadExecutor {

    public ScheduledExecutor(int minThreads,
                             int maxThreads,
                             long keepAliveTime,
                             TimeUnit granularity,
                             ExecutorService.Callbacks handler) {
        super(minThreads, maxThreads, keepAliveTime, granularity,
              new PriorityBlockingQueue(), handler);
    }

    /**
     * A ScheduledTask is a delayed or periodic task that can be run
     * by a ScheduledExecutor.
     **/
    public abstract class ScheduledTask implements Runnable, Cancellable, Comparable {
        /** Return the time this task can run next,
         * in requested units.
         */
        long getExecutionTime(TimeUnit unit) {
            return 0;
        }

        ScheduledTask() {
        }
    }

    /**
     * A ScheduledFutureTask is a delayed result-bearing action that
     * can be run by a ScheduledExecutor.
     * @see Future
     **/
    public abstract class ScheduledFutureTask extends ScheduledTask implements Future {
        ScheduledFutureTask() {
        }
    }

    /**
     * Create and schedule a delayed task.  A delayed task becomes
     * eligible to run after the specified delay expires.
     * @return The associated ScheduledTask object, which lets you
     * cancel or query the status of the execution
     */
    public static ScheduledTask newDelayedTask(Runnable r, long delay, TimeUnit unit) {
        return null;
    }

    /** Create and schedule a periodic task.  A periodic task is like a
     * delayed task, except that upon its completion, it is rescheduled
     * for repeated execution after the specified delay.  Periodic tasks
     * will run no more than once every delay cycle, but are subject to
     * drift over time.
     * @return The associated ScheduledTask object, which lets you
     * cancel or query the status of the execution
     */
    public static ScheduledTask newPeriodicTask(Runnable r, long delay, long period, TimeUnit unit) {
        return null;
    }

    /**
     * Create and schedule a fixed-rate task.  A fixed rate task is like
     * a periodic task, except that upon completion, it is rescheduled
     * to run at the specified delay after the scheduled start time of
     * the current execution.  ScheduledExecutor attempts to execute
     * fixed rate tasks at the desired frequency, even if the previous
     * execution was delayed.
     * @return The associated ScheduledTask object, which lets you
     * cancel or query the status of the execution
     */
    public static ScheduledTask newFixedRateTask(Runnable r, long delay, long period, TimeUnit unit) {
        return null;
    }

    /** Create a schedule a delayed Callable task, which computes a result.
     * @return The associated ScheduledFutureTask object, which lets you
     * cancel or query the result of the computation
     */
    public static ScheduledFutureTask newDelayedFutureTask(Callable c, long delay, TimeUnit unit) {
        return null;
    }

    /**
     * Schedule a ScheduledTask for execution.
     */
    public void schedule(ScheduledTask t) {
    }

}


/*
 * Todo:
 * static factories on internal or external classes?
 */
