package java.util.concurrent;

public class ThreadPoolExecutor extends ThreadExecutor implements Executor, ExecutorService {

    public ThreadPoolExecutor(int minThreads,
                              int maxThreads,
                              long keepAliveTime,
                              TimeUnit granularity,
                              BlockingQueue queue,
                              ExecutorService.Callbacks callbacks) {
        super(minThreads, maxThreads, keepAliveTime, granularity, queue, callbacks);
    }

    public void setMinimumPoolSize(int minThreads) {
        super.setMinimumPoolSize(minThreads);
    }

    public void setMaximumPoolSize(int maxThreads) {
        super.setMaximumPoolSize(maxThreads);
    }

    public void setKeepAliveTime(long time, TimeUnit granularity) {
        super.setKeepAliveTime(time, granularity);
    }

    public int getMinimumPoolSize() {
        return super.getMinimumPoolSize();
    }

    public int getMaximumPoolSize() {
        return super.getMaximumPoolSize();
    }

    public long getKeepAliveTime(TimeUnit granularity) {
        return super.getKeepAliveTime(granularity);
    }

    public int getPoolSize() {
        return super.getPoolSize();
    }

    public int getActiveCount() {
        return super.getActiveCount();
    }

    public int getMaximumActiveCount() {
        return super.getMaximumActiveCount();
    }

    public int getQueueCount() {
        return super.getQueueCount();
    }

    public int getMaximumQueueCount() {
        return super.getMaximumQueueCount();
    }

    public int getCumulativeTaskCount() {
        return super.getCumulativeTaskCount();
    }

    public int getCumulativeCompletedTaskCount() {
        return super.getCumulativeCompletedTaskCount();
    }

    public ExecutorService.Callbacks getCallbacks() {
        return super.getCallbacks();
    }

    public void setCallbacks(ExecutorService.Callbacks handler) {
        super.setCallbacks(handler);
    }

    public BlockingQueue getQueue() {
        return super.getQueue();
    }
}
