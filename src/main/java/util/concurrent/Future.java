/*
 * @(#)Future.java
 */

package java.util.concurrent;

/**
 * A <tt>Future</tt> represents the result of an asynchronous computation.
 * Methods are provided to check if the computation is complete,
 * to wait for its completion, and to retrieve the result of the
 * computation.  The result can only be retrieved when the computation
 * has completed.  The <tt>get</tt> method will block until the computation
 * has completed.  Once the computation has completed, the result cannot
 * be changed, nor can the computation be restarted or cancelled.
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
 *
 * @since 1.5
 * @see FutureTask
 * @see Executor
 *
 * @spec JSR-166
 * @revised $Date: 2003/05/29 13:21:28 $
 * @editor $Author: dl $
 */
public interface Future<V> {

    /**
     * Returns <tt>true</tt> if the underlying task has completed.
     *
     * @fixme relation to isDone in Cancellable?
     *
     * @return <tt>true</tt> if underlying task has completed
     */
    boolean isDone();

    /**
     * Waits if necessary for computation to complete, and then
     * retrieves its result.
     *
     * @return computed result
     * @throws CancellationException here???
     * @throws ExecutionException if underlying computation threw an exception
     * @throws InterruptedException if current thread was interrupted while waiting
     */
    V get() throws InterruptedException, ExecutionException;

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result.
     *
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument
     * @return computed result
     * @throws ExecutionException if underlying computation threw an exception
     * @throws InterruptedException if current thread was interrupted while waiting
     * @throws TimeoutException if the wait timed out
     */
    V get(long timeout, TimeUnit granularity)
        throws InterruptedException, ExecutionException, TimeoutException;
}



