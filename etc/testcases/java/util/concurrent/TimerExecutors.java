package java.util.concurrent;

import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TimerExecutors {

    public static TimerExecutor newTimerExecutor (Executor executor) {
        return new SimpleTimerExecutor(executor);
    }

    public static TimerThreadedExecutor newTimerExecutor (ThreadedExecutor executor) {
        return new SimpleTimerThreadedExecutor(executor);
    }


    // Implementation

    private static class SimpleTimerExecutor implements TimerExecutor {

        SimpleTimerExecutor (Executor executor) {
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

        protected final Timer timer = new Timer();
        protected final Executor executor;
    }

    private static class SimpleTimerThreadedExecutor extends SimpleTimerExecutor
                implements TimerThreadedExecutor {

        SimpleTimerThreadedExecutor (ThreadedExecutor executor) {
            super(executor);
        }

        public void setThreadFactory(ThreadFactory threadFactory) {
            ((ThreadedExecutor) executor).setThreadFactory(threadFactory);
        }

        public ThreadFactory getThreadFactory() {
            return ((ThreadedExecutor) executor).getThreadFactory();
        }

        public void setCannotExecuteHandler(CannotExecuteHandler handler) {
            ((ThreadedExecutor) executor).setCannotExecuteHandler(handler);
        }

        public CannotExecuteHandler getCannotExecuteHandler() {
            return ((ThreadedExecutor) executor).getCannotExecuteHandler();
        }

        public BlockingQueue getQueue() {
            return ((ThreadedExecutor) executor).getQueue();
        }

        public void shutdown() {
            timer.cancel();
            ((ThreadedExecutor) executor).shutdown();
        }

        public List shutdownNow() {
            timer.cancel();
            return ((ThreadedExecutor) executor).shutdownNow();
        }

        public boolean isShutdown() {
            return ((ThreadedExecutor) executor).isShutdown();
        }

        public void interrupt() {
            ((ThreadedExecutor) executor).interrupt();
        }

        public boolean isTerminated() {
            return ((ThreadedExecutor) executor).isTerminated();
        }

        public boolean awaitTermination(long timeout, TimeUnit granularity)
                throws InterruptedException {
            timer.cancel();
            return ((ThreadedExecutor) executor).awaitTermination(timeout, granularity);
        }
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
