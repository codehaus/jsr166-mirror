/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.List;

/**
 * A partial <tt>ExecutorService</tt> implementation.
 *
 * @since 1.5
 * @author Doug Lea
 */
public abstract class AbstractExecutorService implements ExecutorService {

    public Future<?> submit(Runnable task) {
        FutureTask<?> ftask = new FutureTask<Boolean>(task, Boolean.TRUE);
        execute(ftask);
        return ftask;
    }

    public <T> Future<T> submit(Callable<T> task) {
        FutureTask<T> ftask = new FutureTask<T>(task);
        execute(ftask);
        return ftask;
    }

    public void invoke(Runnable task) throws ExecutionException, InterruptedException {
        FutureTask<?> ftask = new FutureTask<Boolean>(task, Boolean.TRUE);
        execute(ftask);
        ftask.get();
    }

    public <T> T invoke(Callable<T> task) throws ExecutionException, InterruptedException {
        FutureTask<T> ftask = new FutureTask<T>(task);
        execute(ftask);
        return ftask.get();
    }

    public Future<Object> submit(PrivilegedAction action) {
        Callable<Object> task = new PrivilegedActionAdapter(action);
        FutureTask<Object> future = new PrivilegedFutureTask<Object>(task);
        execute(future);
        return future;
    }

    public Future<Object> submit(PrivilegedExceptionAction action) {
        Callable<Object> task = new PrivilegedExceptionActionAdapter(action);
        FutureTask<Object> future = new PrivilegedFutureTask<Object>(task);
        execute(future);
        return future;
    }

    private static class PrivilegedActionAdapter implements Callable<Object> {
        PrivilegedActionAdapter(PrivilegedAction action) {
            this.action = action;
        }
        public Object call () {
            return action.run();
        }
        private final PrivilegedAction action;
    }
    
    private static class PrivilegedExceptionActionAdapter implements Callable<Object> {
        PrivilegedExceptionActionAdapter(PrivilegedExceptionAction action) {
            this.action = action;
        }
        public Object call () throws Exception {
            return action.run();
        }
        private final PrivilegedExceptionAction action;
    }
}
