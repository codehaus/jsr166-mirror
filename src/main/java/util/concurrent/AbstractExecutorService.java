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
import java.util.*;
import java.util.concurrent.locks.*;

/**
 * Provides default implementation of {@link ExecutorService}
 * execution methods. This class implements the <tt>submit</tt> and
 * <tt>invoke</tt> methods using the default {@link FutureTask} and
 * {@link PrivilegedFutureTask} classes provided in this package.  For
 * example, the the implementation of <tt>submit(Runnable)</tt>
 * creates an associated <tt>FutureTask</tt> that is executed and
 * returned. Subclasses overriding these methods to use different
 * {@link Future} implementations should do so consistently for each
 * of these methods.
 *
 * @since 1.5
 * @author Doug Lea
 */
public abstract class AbstractExecutorService implements ExecutorService {

    public <T> Future<T> submit(Runnable task, T result) {
        FutureTask<T> ftask = new FutureTask<T>(task, result);
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

    /**
     * FutureTask extension to provide signal when task completes
     */
    private static class SignallingFuture<T> extends FutureTask<T> {
        private final CountDownLatch signal;
        SignallingFuture(Callable<T> c, CountDownLatch l) {
            super(c); signal = l;
        }
        SignallingFuture(Runnable t, T r, CountDownLatch l) {
            super(t, r); signal = l;
        }
        protected void done() {
            signal.countDown();
        }
    }

    // any/all methods, each a little bit different than the other

    public <T> List<Future<T>> invokeAny(List<Runnable> tasks, T result)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        int n = tasks.size();
        List<Future<T>> futures = new ArrayList<Future<T>>(n);
        if (n == 0)
            return futures;
        CountDownLatch waiter = new CountDownLatch(1);;
        try {
            for (Runnable t : tasks) {
                SignallingFuture<T> f = 
                    new SignallingFuture<T>(t, result, waiter);
                futures.add(f);
                if (waiter.getCount() > 0)
                    execute(f);
            }
            waiter.await();
            return futures;
        } finally {
            for (Future<T> f : futures) 
                f.cancel(true);
        }
    }

    public <T> List<Future<T>> invokeAny(List<Runnable> tasks, T result,
                                         long timeout, TimeUnit unit) 
        throws InterruptedException {
        if (tasks == null || unit == null)
            throw new NullPointerException();
        int n = tasks.size();
        List<Future<T>> futures = new ArrayList<Future<T>>(n);
        if (n == 0)
            return futures;
        CountDownLatch waiter = new CountDownLatch(1);;
        try {
            for (Runnable t : tasks) {
                SignallingFuture<T> f = 
                    new SignallingFuture<T>(t, result, waiter);
                futures.add(f);
                if (waiter.getCount() > 0)
                    execute(f);
            }
            waiter.await(timeout, unit);
            return futures;
        } finally {
            for (Future<T> f : futures) 
                f.cancel(true);
        }
    }

    public <T> List<Future<T>> invokeAny(List<Callable<T>> tasks)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        int n = tasks.size();
        List<Future<T>> futures = new ArrayList<Future<T>>(n);
        if (n == 0)
            return futures;
        CountDownLatch waiter = new CountDownLatch(1);;
        try {
            for (Callable<T> t : tasks) {
                SignallingFuture<T> f = new SignallingFuture<T>(t, waiter);
                futures.add(f);
                if (waiter.getCount() > 0)
                    execute(f);
            }
            waiter.await();
            return futures;
        } finally {
            for (Future<T> f : futures) 
                f.cancel(true);
        }
    }

    public <T> List<Future<T>> invokeAny(List<Callable<T>> tasks, 
                                       long timeout, TimeUnit unit) 
        throws InterruptedException {
        if (tasks == null || unit == null)
            throw new NullPointerException();
        int n = tasks.size();
        List<Future<T>> futures= new ArrayList<Future<T>>(n);
        if (n == 0)
            return futures;
        CountDownLatch waiter = new CountDownLatch(1);;
        try {
            for (Callable<T> t : tasks) {
                SignallingFuture<T> f = new SignallingFuture<T>(t, waiter);
                futures.add(f);
                if (waiter.getCount() > 0)
                    execute(f);
            }
            waiter.await(timeout, unit);
            return futures;
        } finally {
            for (Future<T> f : futures) 
                f.cancel(true);
        }
    }


    public <T> List<Future<T>> invokeAll(List<Runnable> tasks, T result)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Runnable t : tasks) {
                FutureTask<T> f = new FutureTask<T>(t, result);
                futures.add(f);
                execute(f);
            }
            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    try { 
                        f.get(); 
                    } catch(CancellationException ignore) {
                    } catch(ExecutionException ignore) {
                    }
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done)
                for (Future<T> f : futures) 
                    f.cancel(true);
        }
    }

    public <T> List<Future<T>> invokeAll(List<Runnable> tasks, T result,
                                         long timeout, TimeUnit unit) 
        throws InterruptedException {
        if (tasks == null || unit == null)
            throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Runnable t : tasks) {
                FutureTask<T> f = new FutureTask<T>(t, result);
                futures.add(f);
                execute(f);
            }
            long lastTime = System.nanoTime();
            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    if (nanos < 0) 
                        return futures;
                    try { 
                        f.get(nanos, TimeUnit.NANOSECONDS); 
                        long now = System.nanoTime();
                        nanos -= now - lastTime;
                        lastTime = now;
                    } catch(CancellationException ignore) {
                    } catch(ExecutionException ignore) {
                    } catch(TimeoutException toe) {
                        return futures;
                    }
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done)
                for (Future<T> f : futures) 
                    f.cancel(true);
        }
    }

    public <T> List<Future<T>> invokeAll(List<Callable<T>> tasks)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks) {
                FutureTask<T> f = new FutureTask<T>(t);
                futures.add(f);
                execute(f);
            }
            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    try { 
                        f.get(); 
                    } catch(CancellationException ignore) {
                    } catch(ExecutionException ignore) {
                    }
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done)
                for (Future<T> f : futures) 
                    f.cancel(true);
        }
    }

    public <T> List<Future<T>> invokeAll(List<Callable<T>> tasks, 
                                         long timeout, TimeUnit unit) 
        throws InterruptedException {
        if (tasks == null || unit == null)
            throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks) {
                FutureTask<T> f = new FutureTask<T>(t);
                futures.add(f);
                execute(f);
            }
            long lastTime = System.nanoTime();
            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    if (nanos < 0) 
                        return futures; 
                    try { 
                        f.get(nanos, TimeUnit.NANOSECONDS); 
                        long now = System.nanoTime();
                        nanos -= now - lastTime;
                        lastTime = now;
                    } catch(CancellationException ignore) {
                    } catch(ExecutionException ignore) {
                    } catch(TimeoutException toe) {
                        return futures;
                    }
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done)
                for (Future<T> f : futures) 
                    f.cancel(true);
        }
    }

}
