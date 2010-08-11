/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */
import junit.framework.*;
import java.util.concurrent.*;
import java.util.*;


public class RecursiveActionTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(RecursiveActionTest.class);
    }

    static final ForkJoinPool mainPool = new ForkJoinPool();
    static final ForkJoinPool singletonPool = new ForkJoinPool(1);
    static final ForkJoinPool asyncSingletonPool = 
        new ForkJoinPool(1, ForkJoinPool.defaultForkJoinWorkerThreadFactory, 
                         null, true);

    static final class FJException extends RuntimeException {
        FJException() { super(); }
    }

    // A simple recursive action for testing
    static final class FibAction extends RecursiveAction {
        final int number;
        int result;
        FibAction(int n) { number = n; }
        public void compute() {
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
     *
     */
    public void testInvoke() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                f.invoke();
                threadAssertTrue(f.result == 21);
                threadAssertTrue(f.isDone());
                threadAssertFalse(f.isCancelled());
                threadAssertFalse(f.isCompletedAbnormally());
                threadAssertTrue(f.getRawResult() == null);
            }};
        mainPool.invoke(a);
    }

    /**
     * quietlyInvoke task returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks
     */
    public void testQuietlyInvoke() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                f.quietlyInvoke();
                threadAssertTrue(f.result == 21);
                threadAssertTrue(f.isDone());
                threadAssertFalse(f.isCancelled());
                threadAssertFalse(f.isCompletedAbnormally());
                threadAssertTrue(f.getRawResult() == null);
            }};
        mainPool.invoke(a);
    }

    /**
     * join of a forked task returns when task completes
     */
    public void testForkJoin() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                f.fork();
                f.join();
                threadAssertTrue(f.result == 21);
                threadAssertTrue(f.isDone());
                threadAssertTrue(f.getRawResult() == null);
            }};
        mainPool.invoke(a);
    }

    /**
     * get of a forked task returns when task completes
     */
    public void testForkGet() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    f.fork();
                    f.get();
                    threadAssertTrue(f.result == 21);
                    threadAssertTrue(f.isDone());
                } catch (Exception ex) {
                    unexpectedException(ex);
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * timed get of a forked task returns when task completes
     */
    public void testForkTimedGet() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    f.fork();
                    f.get(5L, TimeUnit.SECONDS);
                    threadAssertTrue(f.result == 21);
                    threadAssertTrue(f.isDone());
                } catch (Exception ex) {
                    unexpectedException(ex);
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * timed get with null time unit throws NPE
     */
    public void testForkTimedGetNPE() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    f.fork();
                    f.get(5L, null);
                    shouldThrow();
                } catch (NullPointerException success) {
                } catch (Exception ex) {
                    unexpectedException(ex);
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes
     */
    public void testForkQuietlyJoin() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                f.fork();
                f.quietlyJoin();
                threadAssertTrue(f.result == 21);
                threadAssertTrue(f.isDone());
            }};
        mainPool.invoke(a);
    }


    /**
     * helpQuiesce returns when tasks are complete.
     * getQueuedTaskCount returns 0 when quiescent
     */
    public void testForkHelpQuiesce() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                f.fork();
                f.helpQuiesce();
                threadAssertTrue(f.result == 21);
                threadAssertTrue(f.isDone());
                threadAssertTrue(getQueuedTaskCount() == 0);
            }};
        mainPool.invoke(a);
    }


    /**
     * invoke task throws exception when task completes abnormally
     */
    public void testAbnormalInvoke() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FailingFibAction f = new FailingFibAction(8);
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * quietlyInvoke task returns when task completes abnormally
     */
    public void testAbnormalQuietlyInvoke() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FailingFibAction f = new FailingFibAction(8);
                f.quietlyInvoke();
                threadAssertTrue(f.isDone());
            }};
        mainPool.invoke(a);
    }

    /**
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkJoin() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FailingFibAction f = new FailingFibAction(8);
                    f.fork();
                    f.join();
                    shouldThrow();
                } catch (FJException success) {
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkGet() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FailingFibAction f = new FailingFibAction(8);
                    f.fork();
                    f.get();
                    shouldThrow();
                } catch (ExecutionException success) {
                } catch (Exception ex) {
                    unexpectedException(ex);
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * timed get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkTimedGet() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FailingFibAction f = new FailingFibAction(8);
                    f.fork();
                    f.get(5L, TimeUnit.SECONDS);
                    shouldThrow();
                } catch (ExecutionException success) {
                } catch (Exception ex) {
                    unexpectedException(ex);
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes abnormally
     */
    public void testAbnormalForkQuietlyJoin() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FailingFibAction f = new FailingFibAction(8);
                f.fork();
                f.quietlyJoin();
                threadAssertTrue(f.isDone());
                threadAssertTrue(f.isCompletedAbnormally());
                threadAssertTrue(f.getException() instanceof FJException);
            }};
        mainPool.invoke(a);
    }

    /**
     * invoke task throws exception when task cancelled
     */
    public void testCancelledInvoke() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    f.cancel(true);
                    f.invoke();
                    shouldThrow();
                } catch (CancellationException success) {
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkJoin() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    f.cancel(true);
                    f.fork();
                    f.join();
                    shouldThrow();
                } catch (CancellationException success) {
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkGet() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    f.cancel(true);
                    f.fork();
                    f.get();
                    shouldThrow();
                } catch (CancellationException success) {
                } catch (Exception ex) {
                    unexpectedException(ex);
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * timed get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkTimedGet() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    f.cancel(true);
                    f.fork();
                    f.get(5L, TimeUnit.SECONDS);
                    shouldThrow();
                } catch (CancellationException success) {
                } catch (Exception ex) {
                    unexpectedException(ex);
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * quietlyJoin of a forked task returns when task cancelled
     */
    public void testCancelledForkQuietlyJoin() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                f.cancel(true);
                f.fork();
                f.quietlyJoin();
                threadAssertTrue(f.isDone());
                threadAssertTrue(f.isCompletedAbnormally());
                threadAssertTrue(f.getException() instanceof CancellationException);
            }};
        mainPool.invoke(a);
    }

    /**
     * getPool of executing task returns its pool
     */
    public void testGetPool() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                threadAssertTrue(getPool() == mainPool);
            }};
        mainPool.invoke(a);
    }

    /**
     * getPool of non-FJ task returns null
     */
    public void testGetPool2() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                threadAssertTrue(getPool() == null);
            }};
        a.invoke();
    }

    /**
     * inForkJoinPool of executing task returns true
     */
    public void testInForkJoinPool() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                threadAssertTrue(inForkJoinPool());
            }};
        mainPool.invoke(a);
    }

    /**
     * inForkJoinPool of non-FJ task returns false
     */
    public void testInForkJoinPool2() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                threadAssertTrue(!inForkJoinPool());
            }};
        a.invoke();
    }

    /**
     * getPool of current thread in pool returns its pool
     */
    public void testWorkerGetPool() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                ForkJoinWorkerThread w =
                    (ForkJoinWorkerThread)(Thread.currentThread());
                threadAssertTrue(w.getPool() == mainPool);
            }};
        mainPool.invoke(a);
    }

    /**
     * getPoolIndex of current thread in pool returns 0 <= value < poolSize
     *
     */
    public void testWorkerGetPoolIndex() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                ForkJoinWorkerThread w =
                    (ForkJoinWorkerThread)(Thread.currentThread());
                int idx = w.getPoolIndex();
                threadAssertTrue(idx >= 0);
                threadAssertTrue(idx < mainPool.getPoolSize());
            }};
        mainPool.invoke(a);
    }


    /**
     * setRawResult(null) succeeds
     */
    public void testSetRawResult() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                setRawResult(null);
            }};
        a.invoke();
    }

    /**
     * A reinitialized task may be re-invoked
     */
    public void testReinitialize() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                f.invoke();
                threadAssertTrue(f.result == 21);
                threadAssertTrue(f.isDone());
                threadAssertFalse(f.isCancelled());
                threadAssertFalse(f.isCompletedAbnormally());
                f.reinitialize();
                f.invoke();
                threadAssertTrue(f.result == 21);
            }};
        mainPool.invoke(a);
    }

    /**
     * invoke task throws exception after invoking completeExceptionally
     */
    public void testCompleteExceptionally() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    f.completeExceptionally(new FJException());
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * invoke task suppresses execution invoking complete
     */
    public void testComplete() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                f.complete(null);
                f.invoke();
                threadAssertTrue(f.isDone());
                threadAssertTrue(f.result == 0);
            }};
        mainPool.invoke(a);
    }

    /**
     * invokeAll(t1, t2) invokes all task arguments
     */
    public void testInvokeAll2() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                invokeAll(f, g);
                threadAssertTrue(f.isDone());
                threadAssertTrue(f.result == 21);
                threadAssertTrue(g.isDone());
                threadAssertTrue(g.result == 34);
            }};
        mainPool.invoke(a);
    }

    /**
     * invokeAll(tasks) with 1 argument invokes task
     */
    public void testInvokeAll1() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                invokeAll(f);
                threadAssertTrue(f.isDone());
                threadAssertTrue(f.result == 21);
            }};
        mainPool.invoke(a);
    }

    /**
     * invokeAll(tasks) with > 2 argument invokes tasks
     */
    public void testInvokeAll3() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                FibAction h = new FibAction(7);
                invokeAll(f, g, h);
                threadAssertTrue(f.isDone());
                threadAssertTrue(f.result == 21);
                threadAssertTrue(g.isDone());
                threadAssertTrue(g.result == 34);
                threadAssertTrue(h.isDone());
                threadAssertTrue(h.result == 13);
            }};
        mainPool.invoke(a);
    }

    /**
     * invokeAll(collection) invokes all tasks in the collection
     */
    public void testInvokeAllCollection() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction f = new FibAction(8);
                FibAction g = new FibAction(9);
                FibAction h = new FibAction(7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                invokeAll(set);
                threadAssertTrue(f.isDone());
                threadAssertTrue(f.result == 21);
                threadAssertTrue(g.isDone());
                threadAssertTrue(g.result == 34);
                threadAssertTrue(h.isDone());
                threadAssertTrue(h.result == 13);
            }};
        mainPool.invoke(a);
    }


    /**
     * invokeAll(tasks) with any null task throws NPE
     */
    public void testInvokeAllNPE() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    FibAction g = new FibAction(9);
                    FibAction h = null;
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (NullPointerException success) {
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * invokeAll(t1, t2) throw exception if any task does
     */
    public void testAbnormalInvokeAll2() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    FailingFibAction g = new FailingFibAction(9);
                    invokeAll(f, g);
                    shouldThrow();
                } catch (FJException success) {
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * invokeAll(tasks) with 1 argument throws exception if task does
     */
    public void testAbnormalInvokeAll1() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FailingFibAction g = new FailingFibAction(9);
                    invokeAll(g);
                    shouldThrow();
                } catch (FJException success) {
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * invokeAll(tasks) with > 2 argument throws exception if any task does
     */
    public void testAbnormalInvokeAll3() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FibAction f = new FibAction(8);
                    FailingFibAction g = new FailingFibAction(9);
                    FibAction h = new FibAction(7);
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (FJException success) {
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * invokeAll(collection) throws exception if any task does
     */
    public void testAbnormalInvokeAllCollection() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                try {
                    FailingFibAction f = new FailingFibAction(8);
                    FibAction g = new FibAction(9);
                    FibAction h = new FibAction(7);
                    HashSet set = new HashSet();
                    set.add(f);
                    set.add(g);
                    set.add(h);
                    invokeAll(set);
                    shouldThrow();
                } catch (FJException success) {
                }
            }};
        mainPool.invoke(a);
    }

    /**
     * tryUnfork returns true for most recent unexecuted task,
     * and suppresses execution
     */
    public void testTryUnfork() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction g = new FibAction(9);
                g.fork();
                FibAction f = new FibAction(8);
                f.fork();
                threadAssertTrue(f.tryUnfork());
                helpQuiesce();
                threadAssertFalse(f.isDone());
                threadAssertTrue(g.isDone());
            }};
        singletonPool.invoke(a);
    }

    /**
     * getSurplusQueuedTaskCount returns > 0 when
     * there are more tasks than threads
     */
    public void testGetSurplusQueuedTaskCount() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction h = new FibAction(7);
                h.fork();
                FibAction g = new FibAction(9);
                g.fork();
                FibAction f = new FibAction(8);
                f.fork();
                threadAssertTrue(getSurplusQueuedTaskCount() > 0);
                helpQuiesce();
            }};
        singletonPool.invoke(a);
    }

    /**
     * peekNextLocalTask returns most recent unexecuted task.
     */
    public void testPeekNextLocalTask() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction g = new FibAction(9);
                g.fork();
                FibAction f = new FibAction(8);
                f.fork();
                threadAssertTrue(peekNextLocalTask() == f);
                f.join();
                threadAssertTrue(f.isDone());
                helpQuiesce();
            }};
        singletonPool.invoke(a);
    }

    /**
     * pollNextLocalTask returns most recent unexecuted task
     * without executing it
     */
    public void testPollNextLocalTask() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction g = new FibAction(9);
                g.fork();
                FibAction f = new FibAction(8);
                f.fork();
                threadAssertTrue(pollNextLocalTask() == f);
                helpQuiesce();
                threadAssertFalse(f.isDone());
            }};
        singletonPool.invoke(a);
    }

    /**
     * pollTask returns an unexecuted task
     * without executing it
     */
    public void testPollTask() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction g = new FibAction(9);
                g.fork();
                FibAction f = new FibAction(8);
                f.fork();
                threadAssertTrue(pollTask() == f);
                helpQuiesce();
                threadAssertFalse(f.isDone());
                threadAssertTrue(g.isDone());
            }};
        singletonPool.invoke(a);
    }

    /**
     * peekNextLocalTask returns least recent unexecuted task in async mode
     */
    public void testPeekNextLocalTaskAsync() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction g = new FibAction(9);
                g.fork();
                FibAction f = new FibAction(8);
                f.fork();
                threadAssertTrue(peekNextLocalTask() == g);
                f.join();
                helpQuiesce();
                threadAssertTrue(f.isDone());
            }};
        asyncSingletonPool.invoke(a);
    }

    /**
     * pollNextLocalTask returns least recent unexecuted task
     * without executing it, in async mode
     */
    public void testPollNextLocalTaskAsync() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction g = new FibAction(9);
                g.fork();
                FibAction f = new FibAction(8);
                f.fork();
                threadAssertTrue(pollNextLocalTask() == g);
                helpQuiesce();
                threadAssertTrue(f.isDone());
                threadAssertFalse(g.isDone());
            }};
        asyncSingletonPool.invoke(a);
    }

    /**
     * pollTask returns an unexecuted task
     * without executing it, in async mode
     */
    public void testPollTaskAsync() {
        RecursiveAction a = new RecursiveAction() {
            public void compute() {
                FibAction g = new FibAction(9);
                g.fork();
                FibAction f = new FibAction(8);
                f.fork();
                threadAssertTrue(pollTask() == g);
                helpQuiesce();
                threadAssertTrue(f.isDone());
                threadAssertFalse(g.isDone());
            }};
        asyncSingletonPool.invoke(a);
    }

}
