package java.util.concurrent;

/**
 * A Future represents the results of an asynchronous computation,
 * providing methods to query the computation to see if it is
 * complete, wait for its completion, and retrieve the result of the
 * computation.  The result can only be retrieved when the computation
 * has completed; the get() method will block if the computation has
 * not yet completed.  Once the computation is completed, the result
 * cannot be changed, nor can the computation be restarted or
 * cancelled.
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
 * @see FutureTask
 * @see Executor
 **/
public interface Future<V> {

    /**
     * Return true if the underlying task has completed.
     **/
    public boolean isDone();

    /**
     * Wait if necessary for computation to complete, and then
     * retrieve its result.
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws ExecutionException if the underlying computation
     * threw an exception.
     **/
    public V get() throws InterruptedException, ExecutionException;

    /**
     * Wait if necessary for at most the given time for the computation
     * to complete, and then retrieve its result.
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
