package java.util.concurrent;

/**
 * ThreadPerTaskExecutor executes each task in its own thread.  It makes
 * no attempt to limit the number of tasks executing concurrently.  It
 * is similar in effect to calling new Thread(runnable).start(). 
 */
public class ThreadPerTaskExecutor extends ThreadExecutor implements Executor, ExecutorService {

    /**
     * Construct a thread pool using parameters that cause it to use a
     * new thread for each task.  This provides no efficiency savings
     * over manually creating new threads, but still offers the
     * manageability benefits of ThreadExecutor for tracking active
     * threads, shutdown, and so on.
     */
    public ThreadPerTaskExecutor() {
        super(0, Integer.MAX_VALUE, 0, TimeUnit.MILLISECONDS,
              new SynchronousQueue(), new ThreadExecutor.DefaultCallbacks());
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
