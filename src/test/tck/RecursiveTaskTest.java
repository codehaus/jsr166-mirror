/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */
import junit.framework.*;
import java.util.concurrent.*;
import java.util.*;


public class RecursiveTaskTest extends JSR166TestCase {

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(RecursiveTaskTest.class);
    }

    static final ForkJoinPool mainPool = new ForkJoinPool();
    static final ForkJoinPool singletonPool = new ForkJoinPool(1);
    static final ForkJoinPool asyncSingletonPool = new ForkJoinPool(1);
    static {
        asyncSingletonPool.setAsyncMode(true);
    }

    static final class FJException extends RuntimeException {
        FJException() { super(); }
    }

    // An invalid return value for Fib
    static final Integer NoResult = Integer.valueOf(-17);

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

    // A recursive action failing in base case
    static final class FailingFibTask extends RecursiveTask<Integer> { 
        final int number;
        int result;
        FailingFibTask(int n) { number = n; }
        public Integer compute() {
            int n = number;
            if (n <= 1)
                throw new FJException();
            FailingFibTask f1 = new FailingFibTask(n - 1);
            f1.fork();
            return (new FibTask(n - 2)).compute() + f1.join();
        }
    }

    /** 
     * invoke returns value when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks. getRawResult of a completed non-null task
     * returns value;
     * 
     */
    public void testInvoke() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    Integer r = f.invoke();
                    threadAssertTrue(r == 21);
                    threadAssertTrue(f.isDone());
                    threadAssertFalse(f.isCancelled());
                    threadAssertFalse(f.isCompletedAbnormally());
                    threadAssertTrue(f.getRawResult() == 21);
                    return r;
                }
            };
        assertTrue(mainPool.invoke(a) == 21);
    }

    /** 
     * quietlyInvoke task returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks
     */
    public void testQuietlyInvoke() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    f.quietlyInvoke();
                    Integer r = f.getRawResult();
                    threadAssertTrue(r == 21);
                    threadAssertTrue(f.isDone());
                    threadAssertFalse(f.isCancelled());
                    threadAssertFalse(f.isCompletedAbnormally());
                    return r;
                }
            };
        assertTrue(mainPool.invoke(a) == 21);
    }

    /** 
     * join of a forked task returns when task completes
     */
    public void testForkJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    f.fork();
                    Integer r = f.join();
                    threadAssertTrue(r == 21);
                    threadAssertTrue(f.isDone());
                    return r;
                }
            };
        assertTrue(mainPool.invoke(a) == 21);
    }

    /** 
     * get of a forked task returns when task completes
     */
    public void testForkGet() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FibTask f = new FibTask(8);
                        f.fork();
                        Integer r = f.get();
                        threadAssertTrue(r == 21);
                        threadAssertTrue(f.isDone());
                        return r;
                    } catch(Exception ex) {
                        unexpectedException();
                    }
                    return NoResult;
                }
            };
        assertTrue(mainPool.invoke(a) == 21);
    }

    /** 
     * timed get of a forked task returns when task completes
     */
    public void testForkTimedGet() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FibTask f = new FibTask(8);
                        f.fork();
                        Integer r = f.get(5L, TimeUnit.SECONDS);
                        threadAssertTrue(r == 21);
                        threadAssertTrue(f.isDone());
                        return r;
                    } catch(Exception ex) {
                        unexpectedException();
                    }
                    return NoResult;
                }
            };
        assertTrue(mainPool.invoke(a) == 21);
    }

    /** 
     * helpJoin of a forked task returns when task completes
     */
    public void testForkHelpJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    f.fork();
                    Integer r = f.helpJoin();
                    threadAssertTrue(r == 21);
                    threadAssertTrue(f.isDone());
                    return r;
                }
            };
        assertTrue(mainPool.invoke(a) == 21);
    }

    /** 
     * quietlyJoin of a forked task returns when task completes
     */
    public void testForkQuietlyJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    f.fork();
                    f.quietlyJoin();
                    Integer r = f.getRawResult();
                    threadAssertTrue(r == 21);
                    threadAssertTrue(f.isDone());
                    return r;
                }
            };
        assertTrue(mainPool.invoke(a) == 21);
    }


    /** 
     * quietlyHelpJoin of a forked task returns when task completes
     */
    public void testForkQuietlyHelpJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    f.fork();
                    f.quietlyHelpJoin();
                    Integer r = f.getRawResult();
                    threadAssertTrue(r == 21);
                    threadAssertTrue(f.isDone());
                    return r;
                }
            };
        assertTrue(mainPool.invoke(a) == 21);
    }


    /** 
     * helpQuiesce returns when tasks are complete.
     * getQueuedTaskCount returns 0 when quiescent
     */
    public void testForkHelpQuiesce() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    f.fork();
                    f.helpQuiesce();
                    Integer r = f.getRawResult();
                    threadAssertTrue(r == 21);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(getQueuedTaskCount() == 0);
                    return r;
                }
            };
        assertTrue(mainPool.invoke(a) == 21);
    }


    /** 
     * invoke task throws exception when task completes abnormally
     */
    public void testAbnormalInvoke() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FailingFibTask f = new FailingFibTask(8);
                        f.invoke();
                        shouldThrow();
                        return NoResult;
                    } catch(FJException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * quietelyInvoke task returns when task completes abnormally
     */
    public void testAbnormalQuietlyInvoke() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FailingFibTask f = new FailingFibTask(8);
                    f.quietlyInvoke();
                    threadAssertTrue(f.isDone());
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FailingFibTask f = new FailingFibTask(8);
                        f.fork();
                        Integer r = f.join();
                        shouldThrow();
                        return r;
                    } catch(FJException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkGet() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FailingFibTask f = new FailingFibTask(8);
                        f.fork();
                        Integer r = f.get();
                        shouldThrow();
                        return r;
                    } catch(Exception success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * timed get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkTimedGet() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FailingFibTask f = new FailingFibTask(8);
                        f.fork();
                        Integer r = f.get(5L, TimeUnit.SECONDS);
                        shouldThrow();
                        return r;
                    } catch(Exception success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkHelpJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FailingFibTask f = new FailingFibTask(8);
                        f.fork();
                        Integer r = f.helpJoin();
                        shouldThrow();
                        return r;
                    } catch(FJException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * quietlyHelpJoin of a forked task returns when task completes abnormally.
     * getException of failed task returns its exception.
     * isCompletedAbnormally of a failed task returns true.
     * isCancelled of a failed uncancelled task returns false
     */
    public void testAbnormalForkQuietlyHelpJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FailingFibTask f = new FailingFibTask(8);
                    f.fork();
                    f.quietlyHelpJoin();
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.isCompletedAbnormally());
                    threadAssertFalse(f.isCancelled());
                    threadAssertTrue(f.getException() instanceof FJException);
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * quietlyJoin of a forked task returns when task completes abnormally
     */
    public void testAbnormalForkQuietlyJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FailingFibTask f = new FailingFibTask(8);
                    f.fork();
                    f.quietlyJoin();
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.isCompletedAbnormally());
                    threadAssertTrue(f.getException() instanceof FJException);
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * invoke task throws exception when task cancelled
     */
    public void testCancelledInvoke() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FibTask f = new FibTask(8);
                        f.cancel(true);
                        Integer r = f.invoke();
                        shouldThrow();
                        return r;
                    } catch(CancellationException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FibTask f = new FibTask(8);
                        f.cancel(true);
                        f.fork();
                        Integer r = f.join();
                        shouldThrow();
                        return r;
                    } catch(CancellationException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkGet() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FibTask f = new FibTask(8);
                        f.cancel(true);
                        f.fork();
                        Integer r = f.get();
                        shouldThrow();
                        return r;
                    } catch(Exception success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * timed get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkTimedGet() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FibTask f = new FibTask(8);
                        f.cancel(true);
                        f.fork();
                        Integer r = f.get(5L, TimeUnit.SECONDS);
                        shouldThrow();
                        return r;
                    } catch(Exception success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkHelpJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FibTask f = new FibTask(8);
                        f.cancel(true);
                        f.fork();
                        Integer r = f.helpJoin();
                        shouldThrow();
                        return r;
                    } catch(CancellationException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * quietlyHelpJoin of a forked task returns when task cancelled.
     * getException of cancelled task returns its exception
     * isCompletedAbnormally of a cancelled task returns true.
     * isCancelled of a cancelled task returns true
     */
    public void testCancelledForkQuietlyHelpJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    f.cancel(true);
                    f.fork();
                    f.quietlyHelpJoin();
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.isCompletedAbnormally());
                    threadAssertTrue(f.isCancelled());
                    threadAssertTrue(f.getException() instanceof CancellationException);
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * quietlyJoin of a forked task returns when task cancelled
     */
    public void testCancelledForkQuietlyJoin() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    f.cancel(true);
                    f.fork();
                    f.quietlyJoin();
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.isCompletedAbnormally());
                    threadAssertTrue(f.getException() instanceof CancellationException);
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /**
     * getPool of executing task returns its pool
     */
    public void testGetPool() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    threadAssertTrue(getPool() == mainPool);
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /**
     * getPool of non-FJ task returns null
     */
    public void testGetPool2() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
            public Integer compute() {
                threadAssertTrue(getPool() == null);
                return NoResult;
            }
        };
        a.invoke();
    }
    
    /**
     * inForkJoinPool of executing task returns true
     */
    public void testInForkJoinPool() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    threadAssertTrue(inForkJoinPool());
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /**
     * inForkJoinPool of non-FJ task returns false
     */
    public void testInForkJoinPool2() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    threadAssertTrue(!inForkJoinPool());
                    return NoResult;
                }
            };
        a.invoke();
    }

    /**
     * setRawResult(null) succeeds
     */
    public void testSetRawResult() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    setRawResult(NoResult);
                    return NoResult;
                }
            };
        assertEquals(a.invoke(), NoResult);
    }

    /** 
     * A reinitialized task may be re-invoked
     */
    public void testReinitialize() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    Integer r = f.invoke();
                    threadAssertTrue(r == 21);
                    threadAssertTrue(f.isDone());
                    threadAssertFalse(f.isCancelled());
                    threadAssertFalse(f.isCompletedAbnormally());
                    f.reinitialize();
                    r = f.invoke();
                    threadAssertTrue(r == 21);
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * invoke task throws exception after invoking completeExceptionally
     */
    public void testCompleteExceptionally() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FibTask f = new FibTask(8);
                        f.completeExceptionally(new FJException());
                        Integer r = f.invoke();
                        shouldThrow();
                        return r;
                    } catch(FJException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * invoke task suppresses execution invoking complete
     */
    public void testComplete() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    f.complete(NoResult);
                    Integer r = f.invoke();
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(r == NoResult);
                    return r;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * invokeAll(t1, t2) invokes all task arguments
     */
    public void testInvokeAll2() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    FibTask g = new FibTask(9);
                    invokeAll(f, g);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.join() == 21);
                    threadAssertTrue(g.isDone());
                    threadAssertTrue(g.join() == 34);
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * invokeAll(tasks) with 1 argument invokes task
     */
    public void testInvokeAll1() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    invokeAll(f);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.join() == 21);
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * invokeAll(tasks) with > 2 argument invokes tasks
     */
    public void testInvokeAll3() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    FibTask g = new FibTask(9);
                    FibTask h = new FibTask(7);
                    invokeAll(f, g, h);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.join() == 21);
                    threadAssertTrue(g.isDone());
                    threadAssertTrue(g.join() == 34);
                    threadAssertTrue(h.isDone());
                    threadAssertTrue(h.join() == 13);
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * invokeAll(collection) invokes all tasks in the collection
     */
    public void testInvokeAllCollection() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask f = new FibTask(8);
                    FibTask g = new FibTask(9);
                    FibTask h = new FibTask(7);
                    HashSet set = new HashSet();
                    set.add(f);
                    set.add(g);
                    set.add(h);
                    invokeAll(set);
                    threadAssertTrue(f.isDone());
                    threadAssertTrue(f.join() == 21);
                    threadAssertTrue(g.isDone());
                    threadAssertTrue(g.join() == 34);
                    threadAssertTrue(h.isDone());
                    threadAssertTrue(h.join() == 13);
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }


    /** 
     * invokeAll(t1, t2) throw exception if any task does
     */
    public void testAbnormalInvokeAll2() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FibTask f = new FibTask(8);
                        FailingFibTask g = new FailingFibTask(9);
                        invokeAll(f, g);
                        shouldThrow();
                        return NoResult;
                    } catch(FJException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * invokeAll(tasks) with 1 argument throws exception if task does
     */
    public void testAbnormalInvokeAll1() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FailingFibTask g = new FailingFibTask(9);
                        invokeAll(g);
                        shouldThrow();
                        return NoResult;
                    } catch(FJException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * invokeAll(tasks) with > 2 argument throws exception if any task does
     */
    public void testAbnormalInvokeAll3() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FibTask f = new FibTask(8);
                        FailingFibTask g = new FailingFibTask(9);
                        FibTask h = new FibTask(7);
                        invokeAll(f, g, h);
                        shouldThrow();
                        return NoResult;
                    } catch(FJException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * invokeAll(collection)  throws exception if any task does
     */
    public void testAbnormalInvokeAllCollection() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    try {
                        FailingFibTask f = new FailingFibTask(8);
                        FibTask g = new FibTask(9);
                        FibTask h = new FibTask(7);
                        HashSet set = new HashSet();
                        set.add(f);
                        set.add(g);
                        set.add(h);
                        invokeAll(set);
                        shouldThrow();
                        return NoResult;
                    } catch(FJException success) {
                    }
                    return NoResult;
                }
            };
        mainPool.invoke(a);
    }

    /** 
     * tryUnfork returns true for most recent unexecuted task,
     * and suppresses execution
     */
    public void testTryUnfork() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask g = new FibTask(9);
                    g.fork();
                    FibTask f = new FibTask(8);
                    f.fork();
                    threadAssertTrue(f.tryUnfork());
                    helpQuiesce();
                    threadAssertFalse(f.isDone());
                    threadAssertTrue(g.isDone());
                    return NoResult;
                }
            };
        singletonPool.invoke(a);
    }

    /** 
     * getSurplusQueuedTaskCount returns > 0 when
     * there are more tasks than threads
     */
    public void testGetSurplusQueuedTaskCount() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask h = new FibTask(7);
                    h.fork();
                    FibTask g = new FibTask(9);
                    g.fork();
                    FibTask f = new FibTask(8);
                    f.fork();
                    threadAssertTrue(getSurplusQueuedTaskCount() > 0);
                    helpQuiesce();
                    return NoResult;
                }
            };
        singletonPool.invoke(a);
    }

    /** 
     * peekNextLocalTask returns most recent unexecuted task.
     */
    public void testPeekNextLocalTask() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask g = new FibTask(9);
                    g.fork();
                    FibTask f = new FibTask(8);
                    f.fork();
                    threadAssertTrue(peekNextLocalTask() == f);
                    f.join();
                    threadAssertTrue(f.isDone());
                    helpQuiesce();
                    return NoResult;
                }
            };
        singletonPool.invoke(a);
    }

    /** 
     * pollNextLocalTask returns most recent unexecuted task
     * without executing it
     */
    public void testPollNextLocalTask() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask g = new FibTask(9);
                    g.fork();
                    FibTask f = new FibTask(8);
                    f.fork();
                    threadAssertTrue(pollNextLocalTask() == f);
                    helpQuiesce();
                    threadAssertFalse(f.isDone());
                    return NoResult;
                }
            };
        singletonPool.invoke(a);
    }

    /** 
     * pollTask returns an unexecuted task
     * without executing it
     */
    public void testPollTask() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask g = new FibTask(9);
                    g.fork();
                    FibTask f = new FibTask(8);
                    f.fork();
                    threadAssertTrue(pollTask() == f);
                    helpQuiesce();
                    threadAssertFalse(f.isDone());
                    threadAssertTrue(g.isDone());
                    return NoResult;
                }
            };
        singletonPool.invoke(a);
    }

    /** 
     * peekNextLocalTask returns least recent unexecuted task in async mode
     */
    public void testPeekNextLocalTaskAsync() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask g = new FibTask(9);
                    g.fork();
                    FibTask f = new FibTask(8);
                    f.fork();
                    threadAssertTrue(peekNextLocalTask() == g);
                    f.join();
                    helpQuiesce();
                    threadAssertTrue(f.isDone());
                    return NoResult;
                }
            };
        asyncSingletonPool.invoke(a);
    }

    /** 
     * pollNextLocalTask returns least recent unexecuted task
     * without executing it, in async mode
     */
    public void testPollNextLocalTaskAsync() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask g = new FibTask(9);
                    g.fork();
                    FibTask f = new FibTask(8);
                    f.fork();
                    threadAssertTrue(pollNextLocalTask() == g);
                    helpQuiesce();
                    threadAssertTrue(f.isDone());
                    threadAssertFalse(g.isDone());
                    return NoResult;
                }
            };
        asyncSingletonPool.invoke(a);
    }

    /** 
     * pollTask returns an unexecuted task
     * without executing it, in async mode
     */
    public void testPollTaskAsync() {
        RecursiveTask<Integer> a = new RecursiveTask<Integer>() {
                public Integer compute() {
                    FibTask g = new FibTask(9);
                    g.fork();
                    FibTask f = new FibTask(8);
                    f.fork();
                    threadAssertTrue(pollTask() == g);
                    helpQuiesce();
                    threadAssertTrue(f.isDone());
                    threadAssertFalse(g.isDone());
                    return NoResult;
                }
            };
        asyncSingletonPool.invoke(a);
    }

}
