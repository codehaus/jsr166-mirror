/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

import junit.framework.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;

public class RecursiveActionTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(RecursiveActionTest.class);
    }

    private static ForkJoinPool mainPool() {
        return new ForkJoinPool();
    }

    private static ForkJoinPool singletonPool() {
        return new ForkJoinPool(1);
    }

    private static ForkJoinPool asyncSingletonPool() {
        return new ForkJoinPool(1,
                                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                                null, true);
    }

    private void testInvokeOnPool(ForkJoinPool pool, RecursiveAction a) {
        try {
            assertFalse(a.isDone());
            assertFalse(a.isCompletedNormally());
            assertFalse(a.isCompletedAbnormally());
            assertFalse(a.isCancelled());
            assertNull(a.getException());

            assertNull(pool.invoke(a));

            assertTrue(a.isDone());
            assertTrue(a.isCompletedNormally());
            assertFalse(a.isCompletedAbnormally());
            assertFalse(a.isCancelled());
            assertNull(a.getException());
        } finally {
            joinPool(pool);
        }
    }

    static final class FJException extends RuntimeException {
        FJException() { super(); }
    }

    // A simple recursive action for testing
    final class FibAction extends CheckedRecursiveAction {
        final int number;
        int result;
        FibAction(int n) { number = n; }
        public void realCompute() {
            int n = number;
            if (n <= 1)
                result = n;
            else {
                FibAction f1 = new FibAction(n - 1);
                FibAction f2 = new FibAction(n - 2);
                invokeAll(f1, f2);
                result = f1.result + f2.result;
            }
        }
    }

    // A recursive action failing in base case
    static final class FailingFibAction extends RecursiveAction {
        final int number;
        int result;
        FailingFibAction(int n) { number = n; }
        public void compute() {
            int n = number;
            if (n <= 1)
                throw new FJException();
            else {
                FailingFibAction f1 = new FailingFibAction(n - 1);
                FailingFibAction f2 = new FailingFibAction(n - 2);
                invokeAll(f1, f2);
                result = f1.result + f2.result;
            }
        }
    }

    /**
     * invoke returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks. getRawResult of a RecursiveAction returns null;
     */
    public void testInvoke() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                assertNull(f.invoke());
                assertEquals(21, f.result);
                assertTrue(f.isDone());
                assertFalse(f.isCancelled());
                assertFalse(f.isCompletedAbnormally());
                assertNull(f.getRawResult());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks
     */
    public void testQuietlyInvoke() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                f.quietlyInvoke();
                assertEquals(21, f.result);
                assertTrue(f.isDone());
                assertFalse(f.isCancelled());
                assertFalse(f.isCompletedAbnormally());
                assertNull(f.getRawResult());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task returns when task completes
     */
    public void testForkJoin() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertNull(f.join());
                assertEquals(21, f.result);
                assertTrue(f.isDone());
                assertNull(f.getRawResult());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task returns when task completes
     */
    public void testForkGet() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertNull(f.get());
                assertEquals(21, f.result);
                assertTrue(f.isDone());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task returns when task completes
     */
    public void testForkTimedGet() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertNull(f.get(5L, TimeUnit.SECONDS));
                assertEquals(21, f.result);
                assertTrue(f.isDone());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get with null time unit throws NPE
     */
    public void testForkTimedGetNPE() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                try {
                    f.get(5L, null);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes
     */
    public void testForkQuietlyJoin() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertEquals(21, f.result);
                assertTrue(f.isDone());
            }};
        testInvokeOnPool(mainPool(), a);
    }


    /**
     * helpQuiesce returns when tasks are complete.
     * getQueuedTaskCount returns 0 when quiescent
     */
    public void testForkHelpQuiesce() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                f.helpQuiesce();
                assertEquals(21, f.result);
                assertTrue(f.isDone());
                assertEquals(0, getQueuedTaskCount());
            }};
        testInvokeOnPool(mainPool(), a);
    }


    /**
     * invoke task throws exception when task completes abnormally
     */
    public void testAbnormalInvoke() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                try {
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes abnormally
     */
    public void testAbnormalQuietlyInvoke() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                f.quietlyInvoke();
                assertTrue(f.isDone());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkJoin() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (FJException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkGet() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() throws Exception {
                FailingFibAction f = new FailingFibAction(8);
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (ExecutionException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkTimedGet() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() throws Exception {
                FailingFibAction f = new FailingFibAction(8);
                assertSame(f, f.fork());
                try {
                    f.get(5L, TimeUnit.SECONDS);
                    shouldThrow();
                } catch (ExecutionException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes abnormally
     */
    public void testAbnormalForkQuietlyJoin() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertTrue(f.isDone());
                assertTrue(f.isCompletedAbnormally());
                assertTrue(f.getException() instanceof FJException);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task throws exception when task cancelled
     */
    public void testCancelledInvoke() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                assertTrue(f.cancel(true));
                try {
                    f.invoke();
                    shouldThrow();
                } catch (CancellationException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkJoin() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (CancellationException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkGet() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (CancellationException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkTimedGet() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() throws Exception {
                FibAction f = new FibAction(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get(5L, TimeUnit.SECONDS);
                    shouldThrow();
                } catch (CancellationException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task cancelled
     */
    public void testCancelledForkQuietlyJoin() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertTrue(f.isDone());
                assertTrue(f.isCompletedAbnormally());
                assertTrue(f.isCancelled());
                assertTrue(f.getException() instanceof CancellationException);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * getPool of executing task returns its pool
     */
    public void testGetPool() {
        final ForkJoinPool mainPool = mainPool();
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                assertSame(mainPool, getPool());
            }};
        testInvokeOnPool(mainPool, a);
    }

    /**
     * getPool of non-FJ task returns null
     */
    public void testGetPool2() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                assertNull(getPool());
            }};
        assertNull(a.invoke());
    }

    /**
     * inForkJoinPool of executing task returns true
     */
    public void testInForkJoinPool() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                assertTrue(inForkJoinPool());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * inForkJoinPool of non-FJ task returns false
     */
    public void testInForkJoinPool2() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                assertFalse(inForkJoinPool());
            }};
        assertNull(a.invoke());
    }

    /**
     * getPool of current thread in pool returns its pool
     */
    public void testWorkerGetPool() {
        final ForkJoinPool mainPool = mainPool();
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                ForkJoinWorkerThread w =
                    (ForkJoinWorkerThread) Thread.currentThread();
                assertSame(mainPool, w.getPool());
            }};
        testInvokeOnPool(mainPool, a);
    }

    /**
     * getPoolIndex of current thread in pool returns 0 <= value < poolSize
     */
    public void testWorkerGetPoolIndex() {
        final ForkJoinPool mainPool = mainPool();
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                ForkJoinWorkerThread w =
                    (ForkJoinWorkerThread)(Thread.currentThread());
                int idx = w.getPoolIndex();
                assertTrue(idx >= 0);
                assertTrue(idx < mainPool.getPoolSize());
            }};
        testInvokeOnPool(mainPool, a);
    }


    /**
     * setRawResult(null) succeeds
     */
    public void testSetRawResult() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                setRawResult(null);
            }};
        assertNull(a.invoke());
    }

    /**
     * A reinitialized task may be re-invoked
     */
    public void testReinitialize() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                assertNull(f.invoke());
                assertEquals(21, f.result);
                assertTrue(f.isDone());
                assertFalse(f.isCancelled());
                assertFalse(f.isCompletedAbnormally());
                f.reinitialize();
                assertNull(f.invoke());
                assertEquals(21, f.result);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task throws exception after invoking completeExceptionally
     */
    public void testCompleteExceptionally() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                f.completeExceptionally(new FJException());
                try {
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task suppresses execution invoking complete
     */
    public void testComplete() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                f.complete(null);
                assertNull(f.invoke());
                assertTrue(f.isDone());
                assertEquals(0, f.result);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(t1, t2) invokes all task arguments
     */
    public void testInvokeAll2() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                invokeAll(f, g);
                assertTrue(f.isDone());
                assertEquals(21, f.result);
                assertTrue(g.isDone());
                assertEquals(34, g.result);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument invokes task
     */
    public void testInvokeAll1() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                invokeAll(f);
                assertTrue(f.isDone());
                assertEquals(21, f.result);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument invokes tasks
     */
    public void testInvokeAll3() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                FibAction h = new FibAction(7);
                invokeAll(f, g, h);
                assertTrue(f.isDone());
                assertEquals(21, f.result);
                assertTrue(g.isDone());
                assertEquals(34, g.result);
                assertTrue(h.isDone());
                assertEquals(13, h.result);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(collection) invokes all tasks in the collection
     */
    public void testInvokeAllCollection() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                FibAction h = new FibAction(7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                invokeAll(set);
                assertTrue(f.isDone());
                assertEquals(21, f.result);
                assertTrue(g.isDone());
                assertEquals(34, g.result);
                assertTrue(h.isDone());
                assertEquals(13, h.result);
            }};
        testInvokeOnPool(mainPool(), a);
    }


    /**
     * invokeAll(tasks) with any null task throws NPE
     */
    public void testInvokeAllNPE() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                FibAction h = null;
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(t1, t2) throw exception if any task does
     */
    public void testAbnormalInvokeAll2() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                FailingFibAction g = new FailingFibAction(9);
                try {
                    invokeAll(f, g);
                    shouldThrow();
                } catch (FJException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument throws exception if task does
     */
    public void testAbnormalInvokeAll1() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FailingFibAction g = new FailingFibAction(9);
                try {
                    invokeAll(g);
                    shouldThrow();
                } catch (FJException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument throws exception if any task does
     */
    public void testAbnormalInvokeAll3() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction f = new FibAction(8);
                FailingFibAction g = new FailingFibAction(9);
                FibAction h = new FibAction(7);
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (FJException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(collection) throws exception if any task does
     */
    public void testAbnormalInvokeAllCollection() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FailingFibAction f = new FailingFibAction(8);
                FibAction g = new FibAction(9);
                FibAction h = new FibAction(7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                try {
                    invokeAll(set);
                    shouldThrow();
                } catch (FJException success) {}
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * tryUnfork returns true for most recent unexecuted task,
     * and suppresses execution
     */
    public void testTryUnfork() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertTrue(f.tryUnfork());
                helpQuiesce();
                assertFalse(f.isDone());
                assertTrue(g.isDone());
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * getSurplusQueuedTaskCount returns > 0 when
     * there are more tasks than threads
     */
    public void testGetSurplusQueuedTaskCount() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction h = new FibAction(7);
                assertSame(h, h.fork());
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertTrue(getSurplusQueuedTaskCount() > 0);
                helpQuiesce();
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * peekNextLocalTask returns most recent unexecuted task.
     */
    public void testPeekNextLocalTask() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(f, peekNextLocalTask());
                assertNull(f.join());
                assertTrue(f.isDone());
                helpQuiesce();
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * pollNextLocalTask returns most recent unexecuted task
     * without executing it
     */
    public void testPollNextLocalTask() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(f, pollNextLocalTask());
                helpQuiesce();
                assertFalse(f.isDone());
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * pollTask returns an unexecuted task
     * without executing it
     */
    public void testPollTask() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(f, pollTask());
                helpQuiesce();
                assertFalse(f.isDone());
                assertTrue(g.isDone());
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * peekNextLocalTask returns least recent unexecuted task in async mode
     */
    public void testPeekNextLocalTaskAsync() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(g, peekNextLocalTask());
                assertNull(f.join());
                helpQuiesce();
                assertTrue(f.isDone());
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    /**
     * pollNextLocalTask returns least recent unexecuted task
     * without executing it, in async mode
     */
    public void testPollNextLocalTaskAsync() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(g, pollNextLocalTask());
                helpQuiesce();
                assertTrue(f.isDone());
                assertFalse(g.isDone());
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    /**
     * pollTask returns an unexecuted task
     * without executing it, in async mode
     */
    public void testPollTaskAsync() {
        RecursiveAction a = new CheckedRecursiveAction() {
            public void realCompute() {
                FibAction g = new FibAction(9);
                assertSame(g, g.fork());
                FibAction f = new FibAction(8);
                assertSame(f, f.fork());
                assertSame(g, pollTask());
                helpQuiesce();
                assertTrue(f.isDone());
                assertFalse(g.isDone());
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

}
