/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;


/**
 * A {@link CompletionService} that uses a supplied {@link Executor}
 * to execute tasks.
 */
public class ExecutorCompletionService<V> implements CompletionService<V> {
    private final Executor executor;
    private final BlockingQueue<Future<V>> completionQueue;

    /**
     * FutureTask extension to enqueue upon completion
     */
    private class QueueingFuture<T> extends FutureTask<T> {
        QueueingFuture(Callable<T> c) { super(c); }
        QueueingFuture(Runnable t, T r) { super(t, r); }
        protected void done() { completionQueue.add((Future<V>)this); }
    }

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution and a
     * {@link LinkedBlockingQueue} as a completion queue.
     * @param executor the executor to use; normally
     * one dedicated for use by this service
     8 @throws NullPointerException if executor is <tt>null</tt>
     */
    public ExecutorCompletionService(Executor executor) {
        if (executor == null) 
            throw new NullPointerException();
        this.executor = executor;
        this.completionQueue = new LinkedBlockingQueue<Future<V>>();
    }

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution and the supplied queue as its
     * completion queue.
     * @param executor the executor to use; normally
     * one dedicated for use by this service
     * @param completionQueue the queue to use as the completion queue;
     * normally one dedicated for use by this service
     8 @throws NullPointerException if executor or completionQueue are <tt>null</tt>
     */
    public ExecutorCompletionService(Executor executor,
                                     BlockingQueue<Future<V>> completionQueue) {
        if (executor == null || completionQueue == null) 
            throw new NullPointerException();
        this.executor = executor;
        this.completionQueue = completionQueue;
    }

    public Future<V> submit(Callable<V> task) {
        QueueingFuture<V> f = new QueueingFuture<V>(task);
        executor.execute(f);
        return f;
    }

    public Future<V> submit(Runnable task, V result) {
        QueueingFuture<V> f = new QueueingFuture<V>(task, result);
        executor.execute(f);
        return f;
    }

    public Future<V> take() throws InterruptedException {
        return completionQueue.take();
    }

    public Future<V> poll() {
        return completionQueue.poll();
    }

    public Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return completionQueue.poll(timeout, unit);
    }
}


