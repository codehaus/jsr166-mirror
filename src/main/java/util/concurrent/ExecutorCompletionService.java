/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;


/**
 * A {@link CompletionService} that uses a supplied {@link Executor}
 * to execute tasks. 
 *
 * <p>
 *
 * <b>Usage Examples.</b>
 * Suppose you have a set of solvers for a certain problem (each returning
 * a value of some type <tt>Result</tt>),
 * and would like to run them concurrently, using the results of each of them
 * that return a non-null value. You could write this as:
 *
 * <pre>
 *    void solve(Executor e, Collection&lt;Callable&lt;Result&gt;&gt; solvers)
 *      throws InterruptedException, ExecutionException {
 *        CompletionService&lt;Result&gt; ecs = new ExecutorCompletionService&lt;Result&gt;(e);
 *        for (Callable&lt;Result&gt; s : solvers)
 *            ecs.submit(s);
 *        int n = solvers.size();
 *        for (int i = 0; i &lt; n; ++i) {
 *            Result r = ecs.take().get();
 *            if (r != null) 
 *                use(r);
 *        }
 *    }
 * </pre>
 *
 * Suppose instead that you would like to use the first non-null result
 * of the set of tasks, ignoring any of those that encounter exceptions,
 * and cancelling all other tasks when the first one is ready:
 *
 * <pre>
 *    void solve(Executor e, Collection&lt;Callable&lt;Result&gt;&gt; solvers) 
 *      throws InterruptedException {
 *        CompletionService&lt;Result&gt; ecs = new ExecutorCompletionService&lt;Result&gt;(e);
 *        int n = solvers.size();
 *        List&lt;Future&lt;Result&gt;&gt; futures = new ArrayList&lt;Future&lt;Result&gt;&gt;(n);
 *        Result result = null;
 *        try {
 *            for (Callable&lt;Result&gt; s : solvers)
 *                futures.add(ecs.submit(s));
 *            for (int i = 0; i &lt; n; ++i) {
 *                try {
 *                    Result r = ecs.take().get();
 *                    if (r != null) {
 *                        result = r;
 *                        break;
 *                    }
 *                } catch(ExecutionException ignore) {}
 *            }
 *        }
 *        finally {
 *            for (Future&lt;Result&gt; f : futures)
 *                f.cancel(true);
 *        }
 *
 *        if (result != null)
 *            use(result);
 *    }
 * </pre>
 */
public class ExecutorCompletionService<V> implements CompletionService<V> {
    private final Executor executor;
    private final BlockingQueue<Future<V>> completionQueue;

    /**
     * FutureTask extension to enqueue upon completion
     */
    private class QueueingFuture extends FutureTask<V> {
        QueueingFuture(Callable<V> c) { super(c); }
        QueueingFuture(Runnable t, V r) { super(t, r); }
        protected void done() { completionQueue.add(this); }
    }

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution and a
     * {@link LinkedBlockingQueue} as a completion queue.
     * @param executor the executor to use
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
     * @param executor the executor to use
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
        QueueingFuture f = new QueueingFuture(task);
        executor.execute(f);
        return f;
    }

    public Future<V> submit(Runnable task, V result) {
        QueueingFuture f = new QueueingFuture(task, result);
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


