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

    /**
     * Helper class to wait for tasks in bulk-execute methods
     */
    private static class TaskGroupWaiter {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition monitor = lock.newCondition();
        private int firstIndex = -1;
        private int countDown;
        TaskGroupWaiter(int ntasks) { countDown = ntasks; }

        void signalDone(int index) {
            lock.lock();
            try {
                if (firstIndex < 0)
                    firstIndex = index;
                if (--countDown == 0)
                    monitor.signalAll();
            }
            finally {
                lock.unlock();
            }
        }

        int await() throws InterruptedException {
            lock.lock();
            try {
                while (countDown > 0)
                    monitor.await();
                return firstIndex;
            }
            finally {
                lock.unlock();
            }
        }

        int awaitNanos(long nanos) throws InterruptedException {
            lock.lock();
            try {
                while (countDown > 0 && nanos > 0)
                    nanos = monitor.awaitNanos(nanos);
                return firstIndex;
            }
            finally {
                lock.unlock();
            }
        }

        boolean isDone() {
            lock.lock();
            try {
                return countDown <= 0;
            }
            finally {
                lock.unlock();
            }
        }
    }

    /**
     * FutureTask extension to provide signal when task completes
     */
    private static class SignallingFuture<T> extends FutureTask<T> {
        private final TaskGroupWaiter waiter;
        private final int index;
        SignallingFuture(Callable<T> c, TaskGroupWaiter w, int i) { 
            super(c); waiter = w; index = i;
        }
        SignallingFuture(Runnable t, T r, TaskGroupWaiter w, int i) {
            super(t, r); waiter = w; index = i;
        }
        protected void done() {
            waiter.signalDone(index);
        }
    }


    /**
     * Helper method to cancel unfinished tasks before return of
     * bulk execute methods
     */
    private static void cancelUnfinishedTasks(List<Future<?>> futures) {
        for (Future<?> f : futures) 
            f.cancel(true);
    }

    /**
     * Same as cancelUnfinishedTasks; Workaround for compiler oddity
     */
    private static <T> void cancelUnfinishedTasks2(List<Future<T>> futures) {
        for (Future<T> f : futures) 
            f.cancel(true);
    }

    // any/all methods, each a little bit different than each other

    public List<Future<?>> runAny(Collection<Runnable> tasks)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        int n = tasks.size();
        List<Future<?>> futures = new ArrayList<Future<?>>(n);
        if (n == 0)
            return futures;
        TaskGroupWaiter waiter = new TaskGroupWaiter(1);
        try {
            int i = 0;
            for (Runnable t : tasks) {
                SignallingFuture<Boolean> f = 
                    new SignallingFuture<Boolean>(t, Boolean.TRUE, waiter, i++);
                futures.add(f);
                if (!waiter.isDone())
                    execute(f);
            }
            int first = waiter.await();
            if (first > 0)
                Collections.swap(futures, first, 0);
            return futures;
        } finally {
            cancelUnfinishedTasks(futures);
        }
    }

    public List<Future<?>> runAny(Collection<Runnable> tasks, 
                                  long timeout, TimeUnit unit) 
        throws InterruptedException {
        if (tasks == null || unit == null)
            throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        int n = tasks.size();
        List<Future<?>> futures = new ArrayList<Future<?>>(n);
        if (n == 0)
            return futures;
        TaskGroupWaiter waiter = new TaskGroupWaiter(1);
        try {
            int i = 0;
            for (Runnable t : tasks) {
                SignallingFuture<Boolean> f = 
                    new SignallingFuture<Boolean>(t, Boolean.TRUE, waiter, i++);
                futures.add(f);
                if (!waiter.isDone())
                    execute(f);
            }
            int first = waiter.awaitNanos(nanos);
            if (first > 0)
                Collections.swap(futures, first, 0);
            return futures;
        } finally {
            cancelUnfinishedTasks(futures);
        }
    }



    public List<Future<?>> runAll(Collection<Runnable> tasks)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        int n = tasks.size();
        List<Future<?>> futures = new ArrayList<Future<?>>(n);
        if (n == 0)
            return futures;
        TaskGroupWaiter waiter = new TaskGroupWaiter(n);
        int i = 0;
        try {
            for (Runnable t : tasks) {
                SignallingFuture<Boolean> f = 
                    new SignallingFuture<Boolean>(t, Boolean.TRUE, waiter, i++);
                futures.add(f);
                execute(f);
            }
            waiter.await();
            return futures;
        } finally {
            if (!waiter.isDone())
                cancelUnfinishedTasks(futures);
        }
    }

    public List<Future<?>> runAll(Collection<Runnable> tasks, 
                                     long timeout, TimeUnit unit) 
        throws InterruptedException {
        if (tasks == null || unit == null)
            throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        int n = tasks.size();
        List<Future<?>> futures = new ArrayList<Future<?>>(n);
        if (n == 0)
            return futures;
        TaskGroupWaiter waiter = new TaskGroupWaiter(n);
        try {
            int i = 0;
            for (Runnable t : tasks) {
                SignallingFuture<Boolean> f = 
                    new SignallingFuture<Boolean>(t, Boolean.TRUE, waiter, i++);
                futures.add(f);
                execute(f);
            }
            waiter.awaitNanos(nanos);
            return futures;
        } finally {
            if (!waiter.isDone())
                cancelUnfinishedTasks(futures);
        }
    }

    public <T> List<Future<T>> callAny(Collection<Callable<T>> tasks)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        int n = tasks.size();
        List<Future<T>> futures = new ArrayList<Future<T>>(n);
        if (n == 0)
            return futures;
        TaskGroupWaiter waiter = new TaskGroupWaiter(1);
        int i = 0;
        try {
            for (Callable<T> t : tasks) {
                SignallingFuture<T> f = new SignallingFuture<T>(t, waiter, i++);
                futures.add(f);
                if (!waiter.isDone())
                    execute(f);
            }
            int first = waiter.await();
            if (first > 0)
                Collections.swap(futures, first, 0);
            return futures;
        } finally {
            cancelUnfinishedTasks2(futures);
        }
    }

    public <T> List<Future<T>> callAny(Collection<Callable<T>> tasks, 
                                       long timeout, TimeUnit unit) 
        throws InterruptedException {
        if (tasks == null || unit == null)
            throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        int n = tasks.size();
        List<Future<T>> futures= new ArrayList<Future<T>>(n);
        if (n == 0)
            return futures;
        TaskGroupWaiter waiter = new TaskGroupWaiter(1);
        try {
            int i = 0;
            for (Callable<T> t : tasks) {
                SignallingFuture<T> f = new SignallingFuture<T>(t, waiter, i++);
                futures.add(f);
                if (!waiter.isDone())
                    execute(f);
            }
            int first = waiter.awaitNanos(nanos);
            if (first > 0)
                Collections.swap(futures, first, 0);
            return futures;
        } finally {
            cancelUnfinishedTasks2(futures);
        }
    }


    public <T> List<Future<T>> callAll(Collection<Callable<T>> tasks)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        int n = tasks.size();
        List<Future<T>> futures = new ArrayList<Future<T>>(n);
        if (n == 0)
            return futures;
        TaskGroupWaiter waiter = new TaskGroupWaiter(n);
        try {
            int i = 0;
            for (Callable<T> t : tasks) {
                SignallingFuture<T> f = new SignallingFuture<T>(t, waiter, i++);
                futures.add(f);
                execute(f);
            }
            waiter.await();
            return futures;
        } finally {
            if (!waiter.isDone())
                cancelUnfinishedTasks2(futures);
        }
    }

    public <T> List<Future<T>> callAll(Collection<Callable<T>> tasks, 
                                       long timeout, TimeUnit unit) 
        throws InterruptedException {
        if (tasks == null || unit == null)
            throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        int n = tasks.size();
        List<Future<T>> futures = new ArrayList<Future<T>>(n);
        if (n == 0)
            return futures;
        TaskGroupWaiter waiter = new TaskGroupWaiter(n);
        try {
            int i = 0;
            for (Callable<T> t : tasks) {
                SignallingFuture<T> f = new SignallingFuture<T>(t, waiter, i++);
                futures.add(f);
                execute(f);
            }
            waiter.awaitNanos(nanos);
            return futures;
        } finally {
            if (!waiter.isDone())
                cancelUnfinishedTasks2(futures);
        }
    }

}
