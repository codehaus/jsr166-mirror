package java.util.concurrent;

/**
 * A Callable computes a result, or throws an exception if unable to
 * do so.  The <tt>Callable</tt> interface plays a role similar to
 * that of <tt>Runnable</tt>, except that it represents methods that
 * return results and (possibly) throw exceptions.  The
 * <tt>Callable</tt> interface may be implemented by any class with a
 * method computing a function that can be invoked in a "blind"
 * fashion by some other execution agent, for example a
 * java.util.concurrent.Executor.  The most common implementations of
 * Callable are inner classes that supply arguments operated on by the
 * function.
 *
 * @see     java.util.concurrent.Executor
 * @see     java.util.concurrent.Future
 * @see     java.util.concurrent.FutureTask
 **/
public interface Callable<V> {
    V call() throws Exception;
}

