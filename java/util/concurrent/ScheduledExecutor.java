package java.util.concurrent;

import java.util.*;

/**
 * A ScheduledExecutor asynchronously executes optionally
 * time-delayed tasks. Non-delayed tasks submitted using
 * <tt>execute</tt> are scheduled as if they had a requested delay of
 * zero. This class is preferable to <tt>java.util.Timer</tt> when you
 * need multiple worker threads or the additional flexibility or
 * capabilities of <tt>ThreadExecutor</tt> (of which this is a
 * subclass.)
 */
public class ScheduledExecutor extends ThreadExecutor  {

    public ScheduledExecutor(int minThreads,
    int maxThreads,
    long keepAliveTime,
    TimeUnit granularity,
    ExecutorIntercepts handler) {
        super(minThreads, maxThreads, keepAliveTime, granularity,
        new PriorityBlockingQueue(), handler);
    }

    /**
     * A ScheduledTask is a delayed or periodic task that can be run by
     * a ScheduledExecutor.
     **/
    public abstract class ScheduledTask implements Runnable, Cancellable, Comparable {
        /** Return the time this task can run next,
         * in requested units.
         */
        long getExecutionTime(TimeUnit unit) {
            return 0;
        }
        ScheduledTask() {}
    }

    /**
     * A ScheduledFutureTask is a delayed result-bearing action that can
     * be run by a ScheduledExecutor.
     **/
    public abstract class ScheduledFutureTask extends ScheduledTask implements Future {
        ScheduledFutureTask() {}
    }

    public static ScheduledTask newDelayedTask(Runnable r, long delay, TimeUnit unit) {
        return null;
    }

    public static ScheduledTask newPeriodicTask(Runnable r, long delay, long period, TimeUnit unit) {
        return null;
    }

    public static ScheduledTask newFixedRateTask(Runnable r, long delay, long period, TimeUnit unit) {
        return null;
    }

    public static ScheduledFutureTask newDelayedFutureTask(Callable c, long delay, TimeUnit unit) {
        return null;
    }

    public void schedule(ScheduledTask t) {}

}


/*
 * Todo:
 * static factories on internal or external classes?
 */
