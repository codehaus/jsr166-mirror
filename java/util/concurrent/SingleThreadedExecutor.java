package java.util.concurrent;

public class SingleThreadedExecutor extends ThreadExecutor implements Executor, ExecutorService {

    public SingleThreadedExecutor() {
        /**
         * Construct a thread executor using parameters that cause it to
         * use a single thread operating off an unbounded queue, and a
         * default set of intercepts. (Note however that if this single
         * thread terminates due to a failure during execution prior to
         * shutdown, a new one will take its place if needed to execute
         * subsequent tasks.)  Tasks are guaranteed to execute
         * sequentially, and no more than one task will be active at any
         * given time.
         *
         **/
        super(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue(), new ThreadExecutor.DefaultCallbacks());
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
}
