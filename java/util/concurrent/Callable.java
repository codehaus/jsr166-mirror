package java.util.concurrent;

/**
 * Computes a result, or throws an exception if unable to do so.
 * The <tt>Callable</tt> interface plays a role similar to that of
 * {@link java.lang.Runnable Runnable}, except that it represents
 * methods that return results, and may throw exceptions.
 *
 * REWRITE OR REMOVE This interface may be
 * implemented by any class with a method computing a function that
 * can be invoked in a "blind" fashion by some other execution agent,
 * such as an <tt>Executor</tt>.  The most common implementations of
 * <tt>Callable</tt> are inner classes that supply arguments operated
 * on by the function. REWRITE OR REMOVE
 *
 * @see Executor
 * @see Future
 * @see FutureTask
 */
public interface Callable<V> {
    V call() throws Exception;
}
