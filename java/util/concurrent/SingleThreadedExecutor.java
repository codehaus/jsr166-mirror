package java.util.concurrent;

    /**
     * SingleThreadedExecutor ensures that tasks are executed
     * sequentially in a thread separate from the submitter, in the
     * order they were submitted, with no more than one task executing
     * at a time.  Generally, the tasks will all execute in the same
     * background thread, but if this single thread terminates due to
     * a failure during execution prior to shutdown, a new one will
     * take its place if needed to execute subsequent tasks.  This is
     * similar to the threading and execution model used by AWT/Swing.
     */
public class SingleThreadedExecutor
    extends ThreadExecutor implements Executor, ExecutorService {

    /**
     * Construct a thread executor using parameters that cause it to
     * use a single thread operating off an unbounded queue, and a
     * default set of intercepts. (Note however that if this single
     * thread terminates due to a failure during execution prior to
     * shutdown, a new one will take its place if needed to execute
     * subsequent tasks.)  Tasks are guaranteed to execute sequentially,
     * and no more than one task will be active at any given time.
     */
    public SingleThreadedExecutor() {
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
