package jsr166.test;

import java.util.*;
import java.util.concurrent.*;

public class TimerExecutors {

    public static TimerExecutor newTimerExecutor (ExecutorService executor) {
        return new SimpleTimerExecutor(executor);
    }


    // Implementation

    private static class SimpleTimerExecutor implements TimerExecutor {

        SimpleTimerExecutor (ExecutorService executor) {
            this.executor = executor;
        }

        public void execute(Runnable task) {
            executor.execute(task);
        }

        public TimerTask schedule(Runnable task, Date time) {
            TimerTask timerTask = new ExecutionTimerTask(executor, task);
            timer.schedule(timerTask, time);
            return timerTask;
        }

        public TimerTask schedule(Runnable task, Date firstTime,
                                  long period, TimeUnit periodUnit) {
            TimerTask timerTask = new ExecutionTimerTask(executor, task);
            timer.schedule(timerTask, firstTime, timeInMillis(period, periodUnit));
            return timerTask;
        }

        public TimerTask schedule(Runnable task, long delay, TimeUnit delayUnit,
                                  long period, TimeUnit periodUnit) {
            TimerTask timerTask = new ExecutionTimerTask(executor, task);
            timer.schedule(timerTask, timeInMillis(delay, delayUnit),
                                      timeInMillis(period, periodUnit));
            return timerTask;
        }

        public void shutdown() {
            timer.cancel();
            ((ExecutorService) executor).shutdown();
        }

        public List shutdownNow() {
            timer.cancel();
            return ((ExecutorService) executor).shutdownNow();
        }

        public boolean isShutdown() {
            return ((ExecutorService) executor).isShutdown();
        }

        public boolean isTerminated() {
            return ((ExecutorService) executor).isTerminated();
        }

        public boolean awaitTermination(long timeout, TimeUnit granularity)
                throws InterruptedException {
            timer.cancel();
            return ((ExecutorService) executor).awaitTermination(timeout, granularity);
        }

        protected final Timer timer = new Timer();
        protected final ExecutorService executor;
    }

    private static class ExecutionTimerTask extends TimerTask {

        ExecutionTimerTask (Executor executor, Runnable task) {
            this.executor = executor;
            this.task = task;
        }

        public void run () {
            executor.execute(task);
        }

        private final Executor executor;
        private final Runnable task;
    }

    private static long timeInMillis (long time, TimeUnit timeUnit) {
        return time; // XXX assumes timeUnit is millis; fix this
    }
}
