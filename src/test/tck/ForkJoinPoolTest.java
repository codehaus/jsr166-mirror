/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */


import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.security.*;

public class ForkJoinPoolTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(ForkJoinPoolTest.class);
    }

    /**
     * Testing coverage notes:
     *
     * 1. shutdown and related methods are tested via super.joinPool.
     *
     * 2. newTaskFor and adapters are tested in submit/invoke tests
     *
     * 3. We cannot portably test monitoring methods such as
     * getStealCount() since they rely ultimately on random task
     * stealing that may cause tasks not to be stolen/propagated
     * across threads, especially on uniprocessors.
     *
     * 4. There are no independently testable ForkJoinWorkerThread
     * methods, but they are covered here and in task tests.
     */

    // Some classes to test extension and factory methods

    static class MyHandler implements Thread.UncaughtExceptionHandler {
        int catches = 0;
        public void uncaughtException(Thread t, Throwable e) {
            ++catches;
        }
    }

    // to test handlers
    static class FailingFJWSubclass extends ForkJoinWorkerThread {
        public FailingFJWSubclass(ForkJoinPool p) { super(p) ; }
        protected void onStart() { throw new Error(); }
    }

    static class FailingThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        int calls = 0;
        public ForkJoinWorkerThread newThread(ForkJoinPool p) {
            if (++calls > 1) return null;
            return new FailingFJWSubclass(p);
        }
    }

    static class SubFJP extends ForkJoinPool { // to expose protected
        SubFJP() { super(1); }
        public int drainTasksTo(Collection<? super ForkJoinTask<?>> c) {
            return super.drainTasksTo(c);
        }
        public ForkJoinTask<?> pollSubmission() {
            return super.pollSubmission();
        }
    }

    static class ManagedLocker implements ForkJoinPool.ManagedBlocker {
        final ReentrantLock lock;
        boolean hasLock = false;
        ManagedLocker(ReentrantLock lock) { this.lock = lock; }
        public boolean block() {
            if (!hasLock)
                lock.lock();
            return true;
        }
        public boolean isReleasable() {
            return hasLock || (hasLock = lock.tryLock());
        }
    }

    // A simple recursive task for testing
    static final class FibTask extends RecursiveTask<Integer> {
        final int number;
        FibTask(int n) { number = n; }
        public Integer compute() {
            int n = number;
            if (n <= 1)
                return n;
            FibTask f1 = new FibTask(n - 1);
            f1.fork();
            return (new FibTask(n - 2)).compute() + f1.join();
        }
    }

    // A failing task for testing
    static final class FailingTask extends ForkJoinTask<Void> {
        public final Void getRawResult() { return null; }
        protected final void setRawResult(Void mustBeNull) { }
        protected final boolean exec() { throw new Error(); }
        FailingTask() {}
    }

    // Fib needlessly using locking to test ManagedBlockers
    static final class LockingFibTask extends RecursiveTask<Integer> {
        final int number;
        final ManagedLocker locker;
        final ReentrantLock lock;
        LockingFibTask(int n, ManagedLocker locker, ReentrantLock lock) {
            number = n;
            this.locker = locker;
            this.lock = lock;
        }
        public Integer compute() {
            int n;
            LockingFibTask f1 = null;
            LockingFibTask f2 = null;
            locker.block();
            n = number;
            if (n > 1) {
                f1 = new LockingFibTask(n - 1, locker, lock);
                f2 = new LockingFibTask(n - 2, locker, lock);
            }
            lock.unlock();
            if (n <= 1)
                return n;
            else {
                f1.fork();
                return f2.compute() + f1.join();
            }
        }
    }

    /**
     * Succesfully constructed pool reports default factory,
     * parallelism and async mode policies, no active threads or
     * tasks, and quiescent running state.
     */
    public void testDefaultInitialState() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            assertTrue(p.getFactory() == ForkJoinPool.defaultForkJoinWorkerThreadFactory);
            assertTrue(p.isQuiescent());
            assertTrue(p.getMaintainsParallelism());
            assertFalse(p.getAsyncMode());
            assertTrue(p.getActiveThreadCount() == 0);
            assertTrue(p.getStealCount() == 0);
            assertTrue(p.getQueuedTaskCount() == 0);
            assertTrue(p.getQueuedSubmissionCount() == 0);
            assertFalse(p.hasQueuedSubmissions());
            assertFalse(p.isShutdown());
            assertFalse(p.isTerminating());
            assertFalse(p.isTerminated());
        } finally {
            joinPool(p);
        }
    }

    /**
     * Constructor throws if size argument is less than zero
     */
    public void testConstructor1() {
        try {
            new ForkJoinPool(-1);
            shouldThrow();
        }
        catch (IllegalArgumentException success) {}
    }

    /**
     * Constructor throws if factory argument is null
     */
    public void testConstructor2() {
        try {
            new ForkJoinPool(1, null);
            shouldThrow();
        }
        catch (NullPointerException success) {}
    }


    /**
     * getParallelism returns size set in constructor
     */
    public void testGetParallelism() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            assertTrue(p.getParallelism() == 1);
        } finally {
            joinPool(p);
        }
    }

    /**
     * setParallelism changes reported parallelism level.
     */
    public void testSetParallelism() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            assertTrue(p.getParallelism() == 1);
            p.setParallelism(2);
            assertTrue(p.getParallelism() == 2);
        } finally {
            joinPool(p);
        }
    }

    /**
     * setParallelism with argument <= 0 throws exception
     */
    public void testSetParallelism2() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            assertTrue(p.getParallelism() == 1);
            p.setParallelism(-2);
            shouldThrow();
        } catch (IllegalArgumentException success) {
        } finally {
            joinPool(p);
        }
    }

    /**
     * getPoolSize returns number of started workers.
     */
    public void testGetPoolSize() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            assertTrue(p.getPoolSize() == 0);
            Future<String> future = p.submit(new StringTask());
            assertTrue(p.getPoolSize() == 1);

        } finally {
            joinPool(p);
        }
    }

    /**
     * setMaximumPoolSize changes size reported by getMaximumPoolSize.
     */
    public void testSetMaximumPoolSize() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            p.setMaximumPoolSize(2);
            assertTrue(p.getMaximumPoolSize() == 2);
        } finally {
            joinPool(p);
        }
    }

    /**
     * setMaximumPoolSize with argument <= 0 throws exception
     */
    public void testSetMaximumPoolSize2() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            p.setMaximumPoolSize(-2);
            shouldThrow();
        } catch (IllegalArgumentException success) {
        } finally {
            joinPool(p);
        }
    }

    /**
     * setMaintainsParallelism changes policy reported by
     * getMaintainsParallelism.
     */
    public void testSetMaintainsParallelism() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            p.setMaintainsParallelism(false);
            assertFalse(p.getMaintainsParallelism());
        } finally {
            joinPool(p);
        }
    }

    /**
     * setAsyncMode changes policy reported by
     * getAsyncMode.
     */
    public void testSetAsyncMode() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            p.setAsyncMode(true);
            assertTrue(p.getAsyncMode());
        } finally {
            joinPool(p);
        }
    }

    /**
     * setUncaughtExceptionHandler changes handler for uncaught exceptions.
     *
     * Additionally tests: Overriding ForkJoinWorkerThread.onStart
     * performs its defined action
     */
    public void testSetUncaughtExceptionHandler() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1, new FailingThreadFactory());
            MyHandler eh = new MyHandler();
            p.setUncaughtExceptionHandler(eh);
            assertEquals(eh, p.getUncaughtExceptionHandler());
            p.execute(new FailingTask());
            Thread.sleep(MEDIUM_DELAY_MS);
            assertTrue(eh.catches > 0);
        } catch (InterruptedException e) {
            unexpectedException();
        } finally {
            joinPool(p);
        }
    }

    /**
     * setUncaughtExceptionHandler of null removes handler
     */
    public void testSetUncaughtExceptionHandler2() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            p.setUncaughtExceptionHandler(null);
            assertNull(p.getUncaughtExceptionHandler());
        } finally {
            joinPool(p);
        }
    }


    /**
     * After invoking a single task, isQuiescent is true,
     * queues are empty, threads are not active, and
     * construction parameters continue to hold
     */
    public void testisQuiescent() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(2);
            p.invoke(new FibTask(20));
            assertTrue(p.getFactory() == ForkJoinPool.defaultForkJoinWorkerThreadFactory);
            Thread.sleep(MEDIUM_DELAY_MS);
            assertTrue(p.isQuiescent());
            assertTrue(p.getMaintainsParallelism());
            assertFalse(p.getAsyncMode());
            assertTrue(p.getActiveThreadCount() == 0);
            assertTrue(p.getQueuedTaskCount() == 0);
            assertTrue(p.getQueuedSubmissionCount() == 0);
            assertFalse(p.hasQueuedSubmissions());
            assertFalse(p.isShutdown());
            assertFalse(p.isTerminating());
            assertFalse(p.isTerminated());
        } catch (InterruptedException e) {
            unexpectedException();
        } finally {
            joinPool(p);
        }
    }

    /**
     * Completed submit(ForkJoinTask) returns result
     */
    public void testSubmitForkJoinTask() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            ForkJoinTask<Integer> f = p.submit(new FibTask(8));
            int r = f.get();
            assertTrue(r == 21);
        } catch (ExecutionException ex) {
            unexpectedException();
        } catch (InterruptedException ex) {
            unexpectedException();
        } finally {
            joinPool(p);
        }
    }

    /**
     * A task submitted after shutdown is rejected
     */
    public void testSubmitAfterShutdown() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(1);
            p.shutdown();
            assertTrue(p.isShutdown());
            ForkJoinTask<Integer> f = p.submit(new FibTask(8));
            shouldThrow();
        } catch (RejectedExecutionException success) {
        } finally {
            joinPool(p);
        }
    }

    /**
     * Pool maintains parallelism when using ManagedBlocker
     */
    public void testBlockingForkJoinTask() {
        ForkJoinPool p = null;
        try {
            p = new ForkJoinPool(4);
            ReentrantLock lock = new ReentrantLock();
            ManagedLocker locker = new ManagedLocker(lock);
            ForkJoinTask<Integer> f = new LockingFibTask(30, locker, lock);
            p.execute(f);
            assertTrue(p.getPoolSize() >= 4);
            int r = f.get();
            assertTrue(r ==  832040);
        } catch (ExecutionException ex) {
            unexpectedException();
        } catch (InterruptedException ex) {
            unexpectedException();
        } finally {
            joinPool(p);
        }
    }

    /**
     * pollSubmission returns unexecuted submitted task, if present
     */
    public void testPollSubmission() {
        SubFJP p = null;
        try {
            p = new SubFJP();
            ForkJoinTask a = p.submit(new MediumRunnable());
            ForkJoinTask b = p.submit(new MediumRunnable());
            ForkJoinTask c = p.submit(new MediumRunnable());
            ForkJoinTask r = p.pollSubmission();
            assertTrue(r == a || r == b || r == c);
            assertFalse(r.isDone());
        } finally {
            joinPool(p);
        }
    }

    /**
     * drainTasksTo transfers unexecuted submitted tasks, if present
     */
    public void testDrainTasksTo() {
        SubFJP p = null;
        try {
            p = new SubFJP();
            ForkJoinTask a = p.submit(new MediumRunnable());
            ForkJoinTask b = p.submit(new MediumRunnable());
            ForkJoinTask c = p.submit(new MediumRunnable());
            ArrayList<ForkJoinTask> al = new ArrayList();
            p.drainTasksTo(al);
            assertTrue(al.size() > 0);
            for (ForkJoinTask r : al) {
                assertTrue(r == a || r == b || r == c);
                assertFalse(r.isDone());
            }
        } finally {
            joinPool(p);
        }
    }


    // FJ Versions of AbstractExecutorService tests

    /**
     * execute(runnable) runs it to completion
     */
    public void testExecuteRunnable() {
        try {
            ExecutorService e = new ForkJoinPool(1);
            TrackedShortRunnable task = new TrackedShortRunnable();
            assertFalse(task.done);
            Future<?> future = e.submit(task);
            future.get();
            assertTrue(task.done);
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
    }


    /**
     * Completed submit(callable) returns result
     */
    public void testSubmitCallable() {
        try {
            ExecutorService e = new ForkJoinPool(1);
            Future<String> future = e.submit(new StringTask());
            String result = future.get();
            assertSame(TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
    }

    /**
     * Completed submit(runnable) returns successfully
     */
    public void testSubmitRunnable() {
        try {
            ExecutorService e = new ForkJoinPool(1);
            Future<?> future = e.submit(new NoOpRunnable());
            future.get();
            assertTrue(future.isDone());
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
    }

    /**
     * Completed submit(runnable, result) returns result
     */
    public void testSubmitRunnable2() {
        try {
            ExecutorService e = new ForkJoinPool(1);
            Future<String> future = e.submit(new NoOpRunnable(), TEST_STRING);
            String result = future.get();
            assertSame(TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
    }


    /**
     * A submitted privileged action to completion
     */
    public void testSubmitPrivilegedAction() {
        Policy savedPolicy = null;
        try {
            savedPolicy = Policy.getPolicy();
            AdjustablePolicy policy = new AdjustablePolicy();
            policy.addPermission(new RuntimePermission("getContextClassLoader"));
            policy.addPermission(new RuntimePermission("setContextClassLoader"));
            Policy.setPolicy(policy);
        } catch (AccessControlException ok) {
            return;
        }
        try {
            ExecutorService e = new ForkJoinPool(1);
            Future future = e.submit(Executors.callable(new PrivilegedAction() {
                    public Object run() {
                        return TEST_STRING;
                    }}));

            Object result = future.get();
            assertSame(TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
        finally {
            try {
                Policy.setPolicy(savedPolicy);
            } catch (AccessControlException ok) {
                return;
            }
        }
    }

    /**
     * A submitted a privileged exception action runs to completion
     */
    public void testSubmitPrivilegedExceptionAction() {
        Policy savedPolicy = null;
        try {
            savedPolicy = Policy.getPolicy();
            AdjustablePolicy policy = new AdjustablePolicy();
            policy.addPermission(new RuntimePermission("getContextClassLoader"));
            policy.addPermission(new RuntimePermission("setContextClassLoader"));
            Policy.setPolicy(policy);
        } catch (AccessControlException ok) {
            return;
        }

        try {
            ExecutorService e = new ForkJoinPool(1);
            Future future = e.submit(Executors.callable(new PrivilegedExceptionAction() {
                    public Object run() {
                        return TEST_STRING;
                    }}));

            Object result = future.get();
            assertSame(TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
        finally {
            Policy.setPolicy(savedPolicy);
        }
    }

    /**
     * A submitted failed privileged exception action reports exception
     */
    public void testSubmitFailedPrivilegedExceptionAction() {
        Policy savedPolicy = null;
        try {
            savedPolicy = Policy.getPolicy();
            AdjustablePolicy policy = new AdjustablePolicy();
            policy.addPermission(new RuntimePermission("getContextClassLoader"));
            policy.addPermission(new RuntimePermission("setContextClassLoader"));
            Policy.setPolicy(policy);
        } catch (AccessControlException ok) {
            return;
        }


        try {
            ExecutorService e = new ForkJoinPool(1);
            Future future = e.submit(Executors.callable(new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        throw new IndexOutOfBoundsException();
                    }}));

            Object result = future.get();
            shouldThrow();
        }
        catch (ExecutionException success) {
        } catch (CancellationException success) {
        } catch (InterruptedException ex) {
            unexpectedException();
        }
        finally {
            Policy.setPolicy(savedPolicy);
        }
    }

    /**
     * execute(null runnable) throws NPE
     */
    public void testExecuteNullRunnable() {
        try {
            ExecutorService e = new ForkJoinPool(1);
            TrackedShortRunnable task = null;
            Future<?> future = e.submit(task);
            shouldThrow();
        }
        catch (NullPointerException success) {
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }


    /**
     * submit(null callable) throws NPE
     */
    public void testSubmitNullCallable() {
        try {
            ExecutorService e = new ForkJoinPool(1);
            StringTask t = null;
            Future<String> future = e.submit(t);
            shouldThrow();
        }
        catch (NullPointerException success) {
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }


    /**
     *  Blocking on submit(callable) throws InterruptedException if
     *  caller interrupted.
     */
    public void testInterruptedSubmit() {
        final ForkJoinPool p = new ForkJoinPool(1);
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        p.submit(new Callable<Object>() {
                                public Object call() {
                                    try {
                                        Thread.sleep(MEDIUM_DELAY_MS);
                                        shouldThrow();
                                    } catch (InterruptedException e) {
                                    }
                                    return null;
                                }
                            }).get();
                    } catch (InterruptedException success) {
                    } catch (Exception e) {
                        unexpectedException();
                    }

                }
            });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
        } catch (Exception e) {
            unexpectedException();
        }
        joinPool(p);
    }

    /**
     *  get of submit(callable) throws ExecutionException if callable
     *  throws exception
     */
    public void testSubmitEE() {
        ForkJoinPool p = new ForkJoinPool(1);

        try {
            Callable c = new Callable() {
                    public Object call() {
                        int i = 5/0;
                        return Boolean.TRUE;
                    }
                };

            for (int i = 0; i < 5; i++) {
                p.submit(c).get();
            }

            shouldThrow();
        } catch (ExecutionException success) {
        } catch (CancellationException success) {
        } catch (Exception e) {
            unexpectedException();
        }
        joinPool(p);
    }

    /**
     * invokeAny(null) throws NPE
     */
    public void testInvokeAny1() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            e.invokeAny(null);
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * invokeAny(empty collection) throws IAE
     */
    public void testInvokeAny2() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            e.invokeAny(new ArrayList<Callable<String>>());
        } catch (IllegalArgumentException success) {
        } catch (Exception ex) {
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * invokeAny(c) throws NPE if c has null elements
     */
    public void testInvokeAny3() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(null);
            e.invokeAny(l);
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * invokeAny(c) throws ExecutionException if no task in c completes
     */
    public void testInvokeAny4() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new NPETask());
            e.invokeAny(l);
        } catch (ExecutionException success) {
        } catch (CancellationException success) {
        } catch (Exception ex) {
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * invokeAny(c) returns result of some task in c if at least one completes
     */
    public void testInvokeAny5() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(new StringTask());
            String result = e.invokeAny(l);
            assertSame(TEST_STRING, result);
        } catch (ExecutionException success) {
        } catch (CancellationException success) {
        } catch (Exception ex) {
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * invokeAll(null) throws NPE
     */
    public void testInvokeAll1() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            e.invokeAll(null);
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * invokeAll(empty collection) returns empty collection
     */
    public void testInvokeAll2() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            List<Future<String>> r = e.invokeAll(new ArrayList<Callable<String>>());
            assertTrue(r.isEmpty());
        } catch (Exception ex) {
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * invokeAll(c) throws NPE if c has null elements
     */
    public void testInvokeAll3() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(null);
            e.invokeAll(l);
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * get of returned element of invokeAll(c) throws exception on failed task
     */
    public void testInvokeAll4() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new NPETask());
            List<Future<String>> result = e.invokeAll(l);
            assertEquals(1, result.size());
            for (Iterator<Future<String>> it = result.iterator(); it.hasNext();)
                it.next().get();
        } catch (ExecutionException success) {
        } catch (CancellationException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * invokeAll(c) returns results of all completed tasks in c
     */
    public void testInvokeAll5() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(new StringTask());
            List<Future<String>> result = e.invokeAll(l);
            assertEquals(2, result.size());
            for (Iterator<Future<String>> it = result.iterator(); it.hasNext();)
                assertSame(TEST_STRING, it.next().get());
        } catch (ExecutionException success) {
        } catch (CancellationException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }


    /**
     * timed invokeAny(null) throws NPE
     */
    public void testTimedInvokeAny1() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            e.invokeAny(null, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * timed invokeAny(null time unit) throws NPE
     */
    public void testTimedInvokeAnyNullTimeUnit() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            e.invokeAny(l, MEDIUM_DELAY_MS, null);
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * timed invokeAny(empty collection) throws IAE
     */
    public void testTimedInvokeAny2() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            e.invokeAny(new ArrayList<Callable<String>>(), MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (IllegalArgumentException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * timed invokeAny(c) throws NPE if c has null elements
     */
    public void testTimedInvokeAny3() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(null);
            e.invokeAny(l, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * timed invokeAny(c) throws ExecutionException if no task completes
     */
    public void testTimedInvokeAny4() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new NPETask());
            e.invokeAny(l, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException success) {
        } catch (CancellationException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * timed invokeAny(c) returns result of some task in c
     */
    public void testTimedInvokeAny5() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(new StringTask());
            String result = e.invokeAny(l, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            assertSame(TEST_STRING, result);
        } catch (ExecutionException success) {
        } catch (CancellationException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * timed invokeAll(null) throws NPE
     */
    public void testTimedInvokeAll1() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            e.invokeAll(null, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * timed invokeAll(null time unit) throws NPE
     */
    public void testTimedInvokeAllNullTimeUnit() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            e.invokeAll(l, MEDIUM_DELAY_MS, null);
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * timed invokeAll(empty collection) returns empty collection
     */
    public void testTimedInvokeAll2() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            List<Future<String>> r = e.invokeAll(new ArrayList<Callable<String>>(), MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            assertTrue(r.isEmpty());
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * timed invokeAll(c) throws NPE if c has null elements
     */
    public void testTimedInvokeAll3() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(null);
            e.invokeAll(l, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * get of returned element of invokeAll(c) throws exception on failed task
     */
    public void testTimedInvokeAll4() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new NPETask());
            List<Future<String>> result = e.invokeAll(l, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            assertEquals(1, result.size());
            for (Iterator<Future<String>> it = result.iterator(); it.hasNext();)
                it.next().get();
        } catch (ExecutionException success) {
        } catch (CancellationException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * timed invokeAll(c) returns results of all completed tasks in c
     */
    public void testTimedInvokeAll5() {
        ExecutorService e = new ForkJoinPool(1);
        try {
            ArrayList<Callable<String>> l = new ArrayList<Callable<String>>();
            l.add(new StringTask());
            l.add(new StringTask());
            List<Future<String>> result = e.invokeAll(l, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            assertEquals(2, result.size());
            for (Iterator<Future<String>> it = result.iterator(); it.hasNext();)
                assertSame(TEST_STRING, it.next().get());
        } catch (ExecutionException success) {
        } catch (CancellationException success) {
        } catch (Exception ex) {
            ex.printStackTrace();
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

}
