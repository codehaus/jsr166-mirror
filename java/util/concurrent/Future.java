package java.util.concurrent;

/**
 * A Future represents the results of an asynchronous computation.
 * Futures maintain a single value serving as the result of an
 * operation. The result cannot be accessed until the computation has
 * completed.
 *
 * <p>
 * <b>Sample Usage</b> <p>
 * <pre>
 * class Image { ... };
 * class ImageRenderer { Image render(byte[] raw); }
 * class App {
 *   Executor executor = ...
 *   ImageRenderer renderer = ...
 *   void display(final byte[] rawimage) throws InterruptedException {
 *     Future futureImage =
 *       new FutureTask(new Callable() {
 *         public Object call() {
 *           return renderer.render(rawImage);
 *       }});
 *     executor.execute(futureImage);
 *     drawBorders(); // do other things while executing
 *     drawCaption();
 *     try {
 *       drawImage((Image)(futureImage.get())); // use future
 *     }
 *     catch (ExecutionException ex) { cleanup(); return; }
 *   }
 * }
 * </pre>
 **/
public interface Future<V> {

    /**
     * Return true if the underlying task has completed.
     **/
    public boolean isDone();

    /**
     * Wait if necessary for object to exist, then get it
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws ExecutionException if the underlying computation
     * threw an exception.
     **/
    public V get() throws InterruptedException, ExecutionException;

    /**
     * Wait if necessary for at most the given time for object to exist,
     * then get it.
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws TimeOutException if the wait timed out
     * @throws ExecutionException if the underlying computation
     * threw an exception.
     **/
    public V get(long timeout, TimeUnit granularity)
        throws InterruptedException, ExecutionException;

}
