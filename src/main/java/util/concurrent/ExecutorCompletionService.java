/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;


/**
 * A {@link CompletionService} that uses a supplied {@link ExecutorService}
 * to execute tasks.
 */
public class ExecutorCompletionService<V> implements CompletionService<V> {
    private final ExecutorService executor;
    private final LinkedBlockingQueue<Future<V>> cq = 
        new LinkedBlockingQueue<Future<V>>();

    /**
     * FutureTask extension to enqueue upon completion
     */
    private class QueueingFuture<T> extends FutureTask<T> {
        QueueingFuture(Callable<T> c) { super(c); }
        QueueingFuture(Runnable t, T r) { super(t, r); }
        protected void done() { cq.add((Future<V>)this); }
    }

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution. Normally, this
     * executor should be dedicated for use by this service
     8 @throws NullPointerException if executor is null
     */
    public ExecutorCompletionService(ExecutorService executor) {
        if (executor == null) 
            throw new NullPointerException();
        this.executor = executor;
    }


    /**
     * Return the {@link ExecutorService} used for base
     * task execution. This may for example be used to shut
     * down the service.
     * @return the executor
     */
    public ExecutorService getExecutor() { 
        return executor; 
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
        return cq.take();
    }

    public Future<V> poll() {
        return cq.poll();
    }

    public Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
        return cq.poll(timeout, unit);
    }
}


