/*
 * @(#)SingleThreadedExecutor.java
 */

package java.util.concurrent;

import java.util.List;

/**
 * A {@link ThreadedExecutor} that runs tasks on a single background thread.
 * Tasks are executed sequentially in the order they were submitted, with
 * no more than one task executing at a time.  Generally, the tasks will
 * all execute in the same background thread, but if this single thread
 * terminates due to a failure during execution, a new thread will take
 * its place if needed to execute subsequent tasks.
 *
 * @since 1.5
 *
 * @spec JSR-166
 * @revised $Date: 2003/02/26 10:48:09 $
 * @editor $Author: jozart $
 */
public class SingleThreadedExecutor implements ThreadedExecutor {

    private final ThreadedExecutor executor;

    /**
     * Creates a threaded executor that uses a single thread operating off an
     * unbounded queue. (Note however that if this single thread terminates
     * due to a failure during execution prior to shutdown, a new one will
     * take its place if needed to execute subsequent tasks.)  Tasks are
     * guaranteed to execute sequentially, and no more than one task will be
     * active at any given time.
     */
    public SingleThreadedExecutor() {
        executor = new ThreadPoolExecutor(1, 1,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue());
    }

    /* Executor implementation. Inherit javadoc from ThreadedExecutor. */

    public void execute(Runnable command) {
        executor.execute(command);
    }

    /* ThreadedExecutor implementation.  Inherit javadoc. */

    public void setThreadFactory(ThreadFactory threadFactory) {
        executor.setThreadFactory(threadFactory);
    }

    public ThreadFactory getThreadFactory() {
        return executor.getThreadFactory();
    }

    public void setCannotExecuteHandler(CannotExecuteHandler handler) {
        executor.setCannotExecuteHandler(handler);
    }

    public CannotExecuteHandler getCannotExecuteHandler() {
        return executor.getCannotExecuteHandler();
    }

    public BlockingQueue getQueue() {
        return executor.getQueue();
    }

    public void shutdown() {
        executor.shutdown();
    }

    public List shutdownNow() {
        return executor.shutdownNow();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    public void interrupt() {
        executor.interrupt();
    }

    public boolean isTerminated() {
        return executor.isTerminated();
    }

    public boolean awaitTermination(long timeout, TimeUnit granularity)
    throws InterruptedException {
        return executor.awaitTermination(timeout, granularity);
    }
}
