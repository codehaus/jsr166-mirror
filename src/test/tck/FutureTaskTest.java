/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.*;

public class FutureTaskTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(FutureTaskTest.class);
    }

    void checkNotDone(Future<?> f) {
        assertFalse(f.isDone());
        assertFalse(f.isCancelled());
    }

    <T> void checkCompletedNormally(Future<T> f, T expected) {
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());

        try {
            assertSame(expected, f.get());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertSame(expected, f.get(5L, SECONDS));
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        assertFalse(f.cancel(false));
        assertFalse(f.cancel(true));
    }

    void checkCancelled(Future<?> f) {
        assertTrue(f.isDone());
        assertTrue(f.isCancelled());

        try {
            f.get();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            f.get(5L, SECONDS);
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        assertFalse(f.cancel(false));
        assertFalse(f.cancel(true));
    }

    void checkCompletedAbnormally(Future<?> f, Throwable t) {
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());

        try {
            f.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(t, success.getCause());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            f.get(5L, SECONDS);
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(t, success.getCause());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        assertFalse(f.cancel(false));
        assertFalse(f.cancel(true));
    }

    /**
     * Subclass to expose protected methods
     */
    static class PublicFutureTask extends FutureTask {
        public PublicFutureTask(Callable r) { super(r); }
        public boolean runAndReset() { return super.runAndReset(); }
        public void set(Object x) { super.set(x); }
        public void setException(Throwable t) { super.setException(t); }
    }

    /**
     * Creating a future with a null callable throws NPE
     */
    public void testConstructor() {
        try {
            FutureTask task = new FutureTask(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * creating a future with null runnable fails
     */
    public void testConstructor2() {
        try {
            FutureTask task = new FutureTask(null, Boolean.TRUE);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * isDone is true when a task completes
     */
    public void testIsDone() {
        FutureTask task = new FutureTask(new NoOpCallable());
        task.run();
        assertTrue(task.isDone());
        checkCompletedNormally(task, Boolean.TRUE);
    }

    /**
     * runAndReset of a non-cancelled task succeeds
     */
    public void testRunAndReset() {
        PublicFutureTask task = new PublicFutureTask(new NoOpCallable());
        assertTrue(task.runAndReset());
        checkNotDone(task);
    }

    /**
     * runAndReset after cancellation fails
     */
    public void testResetAfterCancel() {
        PublicFutureTask task = new PublicFutureTask(new NoOpCallable());
        assertTrue(task.cancel(false));
        assertFalse(task.runAndReset());
        checkCancelled(task);
    }


    /**
     * setting value causes get to return it
     */
    public void testSet() throws Exception {
        PublicFutureTask task = new PublicFutureTask(new NoOpCallable());
        task.set(one);
        assertSame(task.get(), one);
        checkCompletedNormally(task, one);
    }

    /**
     * setException causes get to throw ExecutionException
     */
    public void testSetException() throws Exception {
        Exception nse = new NoSuchElementException();
        PublicFutureTask task = new PublicFutureTask(new NoOpCallable());
        task.setException(nse);
        try {
            Object x = task.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(success.getCause(), nse);
            checkCompletedAbnormally(task, nse);
        }
    }

    /**
     * Cancelling before running succeeds
     */
    public void testCancelBeforeRun() {
        FutureTask task = new FutureTask(new NoOpCallable());
        assertTrue(task.cancel(false));
        task.run();
        checkCancelled(task);
    }

    /**
     * Cancel(true) before run succeeds
     */
    public void testCancelBeforeRun2() {
        FutureTask task = new FutureTask(new NoOpCallable());
        assertTrue(task.cancel(true));
        task.run();
        checkCancelled(task);
    }

    /**
     * cancel of a completed task fails
     */
    public void testCancelAfterRun() {
        FutureTask task = new FutureTask(new NoOpCallable());
        task.run();
        assertFalse(task.cancel(false));
        checkCompletedNormally(task, Boolean.TRUE);
    }

    /**
     * cancel(true) interrupts a running task
     */
    public void testCancelInterrupt() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final FutureTask task =
            new FutureTask(new CheckedCallable<Object>() {
                public Object realCall() {
                    threadStarted.countDown();
                    long t0 = System.nanoTime();
                    for (;;) {
                        if (Thread.interrupted())
                            return Boolean.TRUE;
                        if (millisElapsedSince(t0) > MEDIUM_DELAY_MS)
                            fail("interrupt not delivered");
                        Thread.yield();
                    }
                }});

        Thread t = newStartedThread(task);
        threadStarted.await();
        assertTrue(task.cancel(true));
        checkCancelled(task);
        awaitTermination(t, MEDIUM_DELAY_MS);
        checkCancelled(task);
    }

    /**
     * cancel(false) does not interrupt a running task
     */
    public void testCancelNoInterrupt() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final CountDownLatch cancelled = new CountDownLatch(1);
        final FutureTask<Boolean> task =
            new FutureTask<Boolean>(new CheckedCallable<Boolean>() {
                public Boolean realCall() throws InterruptedException {
                    threadStarted.countDown();
                    cancelled.await(MEDIUM_DELAY_MS, MILLISECONDS);
                    assertFalse(Thread.interrupted());
                    return Boolean.TRUE;
                }});

        Thread t = newStartedThread(task);
        threadStarted.await();
        assertTrue(task.cancel(false));
        checkCancelled(task);
        cancelled.countDown();
        awaitTermination(t, MEDIUM_DELAY_MS);
        checkCancelled(task);
    }

    /**
     * run in one thread causes get in another thread to retrieve value
     */
    public void testGetRun() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);

        final FutureTask task =
            new FutureTask(new CheckedCallable<Object>() {
                public Object realCall() throws InterruptedException {
                    return Boolean.TRUE;
                }});

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                assertSame(Boolean.TRUE, task.get());
            }});

        threadStarted.await();
        checkNotDone(task);
        assertTrue(t.isAlive());
        task.run();
        checkCompletedNormally(task, Boolean.TRUE);
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * set in one thread causes get in another thread to retrieve value
     */
    public void testGetSet() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);

        final PublicFutureTask task =
            new PublicFutureTask(new CheckedCallable<Object>() {
                public Object realCall() throws InterruptedException {
                    return Boolean.TRUE;
                }});

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                assertSame(Boolean.FALSE, task.get());
            }});

        threadStarted.await();
        checkNotDone(task);
        assertTrue(t.isAlive());
        task.set(Boolean.FALSE);
        checkCompletedNormally(task, Boolean.FALSE);
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * run in one thread causes timed get in another thread to retrieve value
     */
    public void testTimedGetRun() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);

        final FutureTask task =
            new FutureTask(new CheckedCallable<Object>() {
                public Object realCall() throws InterruptedException {
                    return Boolean.TRUE;
                }});

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                assertSame(Boolean.TRUE,
                           task.get(MEDIUM_DELAY_MS, MILLISECONDS));
            }});

        threadStarted.await();
        checkNotDone(task);
        assertTrue(t.isAlive());
        task.run();
        checkCompletedNormally(task, Boolean.TRUE);
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * set in one thread causes timed get in another thread to retrieve value
     */
    public void testTimedGetSet() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);

        final PublicFutureTask task =
            new PublicFutureTask(new CheckedCallable<Object>() {
                public Object realCall() throws InterruptedException {
                    return Boolean.TRUE;
                }});

        Thread t = newStartedThread(new CheckedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                assertSame(Boolean.FALSE,
                           task.get(MEDIUM_DELAY_MS, MILLISECONDS));
            }});

        threadStarted.await();
        checkNotDone(task);
        assertTrue(t.isAlive());
        task.set(Boolean.FALSE);
        checkCompletedNormally(task, Boolean.FALSE);
        awaitTermination(t, MEDIUM_DELAY_MS);
    }

    /**
     * Cancelling a task causes timed get in another thread to throw
     * CancellationException
     */
    public void testTimedGet_Cancellation() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(2);
        final FutureTask task =
            new FutureTask(new CheckedInterruptedCallable<Object>() {
                public Object realCall() throws InterruptedException {
                    threadStarted.countDown();
                    Thread.sleep(LONG_DELAY_MS);
                    return Boolean.TRUE;
                }});

        Thread t1 = new ThreadShouldThrow(CancellationException.class) {
            public void realRun() throws Exception {
                threadStarted.countDown();
                task.get(MEDIUM_DELAY_MS, MILLISECONDS);
            }};
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        threadStarted.await();
        task.cancel(true);
        awaitTermination(t1, MEDIUM_DELAY_MS);
        awaitTermination(t2, MEDIUM_DELAY_MS);
        checkCancelled(task);
    }

    /**
     * Cancelling a task causes get in another thread to throw
     * CancellationException
     */
    public void testGet_Cancellation() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(2);
        final FutureTask task =
            new FutureTask(new CheckedInterruptedCallable<Object>() {
                public Object realCall() throws InterruptedException {
                    threadStarted.countDown();
                    Thread.sleep(LONG_DELAY_MS);
                    return Boolean.TRUE;
                }});

        Thread t1 = new ThreadShouldThrow(CancellationException.class) {
            public void realRun() throws Exception {
                threadStarted.countDown();
                task.get();
            }};
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        threadStarted.await();
        task.cancel(true);
        awaitTermination(t1, MEDIUM_DELAY_MS);
        awaitTermination(t2, MEDIUM_DELAY_MS);
        checkCancelled(task);
    }


    /**
     * A runtime exception in task causes get to throw ExecutionException
     */
    public void testGet_ExecutionException() throws InterruptedException {
        final FutureTask task = new FutureTask(new Callable() {
            public Object call() {
                return 5/0;
            }});

        task.run();
        try {
            task.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertTrue(success.getCause() instanceof ArithmeticException);
            checkCompletedAbnormally(task, success.getCause());
        }
    }

    /**
     * A runtime exception in task causes timed get to throw ExecutionException
     */
    public void testTimedGet_ExecutionException2() throws Exception {
        final FutureTask task = new FutureTask(new Callable() {
            public Object call() {
                return 5/0;
            }});

        task.run();
        try {
            task.get(SHORT_DELAY_MS, MILLISECONDS);
            shouldThrow();
        } catch (ExecutionException success) {
            assertTrue(success.getCause() instanceof ArithmeticException);
            checkCompletedAbnormally(task, success.getCause());
        }
    }


    /**
     * Interrupting a waiting get causes it to throw InterruptedException
     */
    public void testGet_InterruptedException() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final FutureTask task = new FutureTask(new NoOpCallable());
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                task.get();
            }});

        threadStarted.await();
        t.interrupt();
        awaitTermination(t, MEDIUM_DELAY_MS);
        checkNotDone(task);
    }

    /**
     * Interrupting a waiting timed get causes it to throw InterruptedException
     */
    public void testTimedGet_InterruptedException2() throws InterruptedException {
        final CountDownLatch threadStarted = new CountDownLatch(1);
        final FutureTask task = new FutureTask(new NoOpCallable());
        Thread t = newStartedThread(new CheckedInterruptedRunnable() {
            public void realRun() throws Exception {
                threadStarted.countDown();
                task.get(LONG_DELAY_MS, MILLISECONDS);
            }});

        threadStarted.await();
        t.interrupt();
        awaitTermination(t, MEDIUM_DELAY_MS);
        checkNotDone(task);
    }

    /**
     * A timed out timed get throws TimeoutException
     */
    public void testGet_TimeoutException() throws Exception {
        try {
            FutureTask task = new FutureTask(new NoOpCallable());
            task.get(1, MILLISECONDS);
            shouldThrow();
        } catch (TimeoutException success) {}
    }

}
