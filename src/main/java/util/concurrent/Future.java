/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

/**
 * A <tt>Future</tt> represents the result of an asynchronous
 * computation.  Methods are provided to check if the computation is
 * complete, to wait for its completion, and to retrieve the result of
 * the computation.  The result can only be retrieved using method
 * <tt>get</tt> when the computation has completed, blocking if
 * necessary until it is ready.  Once the computation has completed,
 * the computation cannot be restarted or cancelled.
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
 * @see FutureTask
 * @see Executor
 * @since 1.5
 * @author Doug Lea
 */
public interface Future<V> extends Cancellable {

    /**
     * Waits if necessary for computation to complete, and then
     * retrieves its result.
     *
     * @return computed result
     * @throws CancellationException if this future was cancelled.
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



