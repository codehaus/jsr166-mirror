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
 * <b>Sample Usage</b> (Note that the following classes are all
 * made-up.) <p>
 * <pre>
 * interface ArchiveSearcher { String search(String target); }
 * class App {
 *   Executor executor = ...
 *   ArchiveSearcher searcher = ...
 *   void showSearch(final String target) throws InterruptedException {
 *     Future&lt;String&gt; future =
 *       new FutureTask&lt;String&gt;(new Callable&lt;String&gt;() {
 *         public String call() {
 *           return searcher.search(target);
 *       }});
 *     executor.execute(future);
 *     displayOtherThings(); // do other things while searching
 *     try {
 *       displayText(future.get()); // use future
 *     } catch (ExecutionException ex) { cleanup(); return; }
 *   }
 * }
 * </pre>
 *
 * The {@link Executors} class contains more convenient methods 
 * for common usages. For example, the above explicit
 * construction could be replaced with:
 * <pre>
 * Future&lt;String&gt; future = Executors.execute(executor, 
 *    new Callable&lt;String&gt;() {
 *       public String call() {
 *         return searcher.search(target);
 *    }});
 * </pre>
 * @since 1.5
 * @see FutureTask
 * @see Executor
 *
 * @spec JSR-166
 * @revised $Date: 2003/08/24 14:47:31 $
 * @editor $Author: dl $
 * @author Doug Lea
 */
public interface Future<V> extends Cancellable {

    /**
     * Waits if necessary for computation to complete, and then
     * retrieves its result.
     *
     * @return computed result
     * @throws CancellationException here???
     * @throws ExecutionException if underlying computation threw an
     * exception
     * @throws InterruptedException if current thread was interrupted
     * while waiting
     */
    V get() throws InterruptedException, ExecutionException;

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result.
     *
     * @param timeout the maximum time to wait
     * @param granularity the time unit of the timeout argument
     * @return computed result
     * @throws ExecutionException if underlying computation threw an
     * exception
     * @throws InterruptedException if current thread was interrupted
     * while waiting
     * @throws TimeoutException if the wait timed out
     */
    V get(long timeout, TimeUnit granularity)
        throws InterruptedException, ExecutionException, TimeoutException;
}



