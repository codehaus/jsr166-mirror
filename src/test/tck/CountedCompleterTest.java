/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.HashSet;
import junit.framework.*;

public class CountedCompleterTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(CountedCompleterTest.class);
    }

    // Runs with "mainPool" use > 1 thread. singletonPool tests use 1
    static final int mainPoolSize =
        Math.max(2, Runtime.getRuntime().availableProcessors());

    /**
     * Analog of CheckedRunnable for CountedCompleter
     */
    public abstract class CheckedFJTask extends RecursiveAction {
        protected abstract void realCompute() throws Throwable;

        public final void compute() {
            try {
                realCompute();
            } catch (Throwable t) {
                threadUnexpectedException(t);
            }
        }
    }

    private static ForkJoinPool mainPool() {
        return new ForkJoinPool(mainPoolSize);
    }

    private static ForkJoinPool singletonPool() {
        return new ForkJoinPool(1);
    }

    private static ForkJoinPool asyncSingletonPool() {
        return new ForkJoinPool(1,
                                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                                null, true);
    }

    private void testInvokeOnPool(ForkJoinPool pool, ForkJoinTask a) {
        try {
            assertFalse(a.isDone());
            assertFalse(a.isCompletedNormally());
            assertFalse(a.isCompletedAbnormally());
            assertFalse(a.isCancelled());
            assertNull(a.getException());
            assertNull(a.getRawResult());

            assertNull(pool.invoke(a));

            assertTrue(a.isDone());
            assertTrue(a.isCompletedNormally());
            assertFalse(a.isCompletedAbnormally());
            assertFalse(a.isCancelled());
            assertNull(a.getException());
            assertNull(a.getRawResult());
        } finally {
            joinPool(pool);
        }
    }

    void checkNotDone(CountedCompleter a) {
        assertFalse(a.isDone());
        assertFalse(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertFalse(a.isCancelled());
        assertNull(a.getException());
        assertNull(a.getRawResult());

        try {
            a.get(0L, SECONDS);
            shouldThrow();
        } catch (TimeoutException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    <T> void checkCompletedNormally(CountedCompleter<T> a) {
        checkCompletedNormally(a, null);
    }

    <T> void checkCompletedNormally(CountedCompleter<T> a, T expected) {
        assertTrue(a.isDone());
        assertFalse(a.isCancelled());
        assertTrue(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertNull(a.getException());
        assertSame(expected, a.getRawResult());

        {
            Thread.currentThread().interrupt();
            long t0 = System.nanoTime();
            assertSame(expected, a.join());
            assertTrue(millisElapsedSince(t0) < SMALL_DELAY_MS);
            Thread.interrupted();
        }

        {
            Thread.currentThread().interrupt();
            long t0 = System.nanoTime();
            a.quietlyJoin();        // should be no-op
            assertTrue(millisElapsedSince(t0) < SMALL_DELAY_MS);
            Thread.interrupted();
        }

        assertFalse(a.cancel(false));
        assertFalse(a.cancel(true));
        try {
            assertSame(expected, a.get());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertSame(expected, a.get(5L, SECONDS));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCancelled(CountedCompleter a) {
        assertTrue(a.isDone());
        assertTrue(a.isCancelled());
        assertFalse(a.isCompletedNormally());
        assertTrue(a.isCompletedAbnormally());
        assertTrue(a.getException() instanceof CancellationException);
        assertNull(a.getRawResult());
        assertTrue(a.cancel(false));
        assertTrue(a.cancel(true));

        try {
            Thread.currentThread().interrupt();
            a.join();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        Thread.interrupted();

        {
            long t0 = System.nanoTime();
            a.quietlyJoin();        // should be no-op
            assertTrue(millisElapsedSince(t0) < SMALL_DELAY_MS);
        }

        try {
            a.get();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.get(5L, SECONDS);
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCompletedAbnormally(CountedCompleter a, Throwable t) {
        assertTrue(a.isDone());
        assertFalse(a.isCancelled());
        assertFalse(a.isCompletedNormally());
        assertTrue(a.isCompletedAbnormally());
        assertSame(t.getClass(), a.getException().getClass());
        assertNull(a.getRawResult());
        assertFalse(a.cancel(false));
        assertFalse(a.cancel(true));

        try {
            Thread.currentThread().interrupt();
            a.join();
            shouldThrow();
        } catch (Throwable expected) {
            assertSame(t.getClass(), expected.getClass());
        }
        Thread.interrupted();

        {
            long t0 = System.nanoTime();
            a.quietlyJoin();        // should be no-op
            assertTrue(millisElapsedSince(t0) < SMALL_DELAY_MS);
        }

        try {
            a.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(t.getClass(), success.getCause().getClass());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        try {
            a.get(5L, SECONDS);
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(t.getClass(), success.getCause().getClass());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    public static final class FJException extends RuntimeException {
        FJException() { super(); }
    }

    static abstract class FailingCCF extends CountedCompleter {
        int number;
        int rnumber;

        public FailingCCF(CountedCompleter parent, int n) {
            super(parent, 1);
            this.number = n;
        }

        public final void compute() {
            CountedCompleter p;
            FailingCCF f = this;
            int n = number;
            while (n >= 2) {
                new RFCCF(f, n - 2).fork();
                f = new LFCCF(f, --n);
            }
            f.number = n;
            f.onCompletion(f);
            if ((p = f.getCompleter()) != null)
                p.tryComplete();
            else 
                f.quietlyComplete(); 
        }
    }

    static final class LFCCF extends FailingCCF {
        public LFCCF(CountedCompleter parent, int n) {
            super(parent, n);
        }
        public final void onCompletion(CountedCompleter caller) {
            FailingCCF p = (FailingCCF)getCompleter();
            int n = number + rnumber;
            if (p != null)
                p.number = n;
            else
                number = n;
        }
    }
    static final class RFCCF extends FailingCCF {
        public RFCCF(CountedCompleter parent, int n) {
            super(parent, n);
        }
        public final void onCompletion(CountedCompleter caller) {
            completeExceptionally(new FJException());
        }
    }

    static abstract class CCF extends CountedCompleter {
        int number;
        int rnumber;

        public CCF(CountedCompleter parent, int n) {
            super(parent, 1);
            this.number = n;
        }

        public final void compute() {
            CountedCompleter p;
            CCF f = this;
            int n = number;
            while (n >= 2) {
                new RCCF(f, n - 2).fork();
                f = new LCCF(f, --n);
            }
            f.number = n;
            f.onCompletion(f);
            if ((p = f.getCompleter()) != null)
                p.tryComplete();
            else 
                f.quietlyComplete(); 
        }
    }

    static final class LCCF extends CCF {
        public LCCF(CountedCompleter parent, int n) {
            super(parent, n);
        }
        public final void onCompletion(CountedCompleter caller) {
            CCF p = (CCF)getCompleter();
            int n = number + rnumber;
            if (p != null)
                p.number = n;
            else
                number = n;
        }
    }
    static final class RCCF extends CCF {
        public RCCF(CountedCompleter parent, int n) {
            super(parent, n);
        }
        public final void onCompletion(CountedCompleter caller) {
            CCF p = (CCF)getCompleter();
            int n = number + rnumber;
            if (p != null)
                p.rnumber = n;
            else
                number = n;
        }
    }

    static final class NoopCountedCompleter extends CountedCompleter {
        boolean post; // set true if onCompletion called
        NoopCountedCompleter() { super(); }
        NoopCountedCompleter(CountedCompleter p) { super(p); }
        public void compute() {}
        public final void onCompletion(CountedCompleter caller) {
            post = true;
        }
    }

    /**
     * A newly constructed CountedCompleter is not completed; 
     * complete() causes completion.
     */
    public void testComplete() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        assertFalse(a.isDone());
        assertFalse(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertFalse(a.isCancelled());
        assertNull(a.getException());
        assertNull(a.getRawResult());
        assertFalse(a.post);
        a.complete(null);
        assertTrue(a.post);
        assertTrue(a.isDone());
        assertTrue(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertFalse(a.isCancelled());
        assertNull(a.getException());
        assertNull(a.getRawResult());
    }

    /**
     * completeExceptionally completes exceptionally
     */
    public void testCompleteExceptionally() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        assertFalse(a.isDone());
        assertFalse(a.isCompletedNormally());
        assertFalse(a.isCompletedAbnormally());
        assertFalse(a.isCancelled());
        assertNull(a.getException());
        assertNull(a.getRawResult());
        assertFalse(a.post);
        a.completeExceptionally(new FJException());
        assertFalse(a.post);
        assertTrue(a.isDone());
        assertFalse(a.isCompletedNormally());
        assertTrue(a.isCompletedAbnormally());
        assertFalse(a.isCancelled());
        assertTrue(a.getException() instanceof FJException);
        assertNull(a.getRawResult());
    }

    /**
     * setPendingCount sets the reported pending count
     */
    public void testSetPendingCount() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        assertEquals(0, a.getPendingCount());
        a.setPendingCount(1);
        assertEquals(1, a.getPendingCount());
        a.setPendingCount(27);
        assertEquals(27, a.getPendingCount());
    }

    /**
     * addToPendingCount adds to the reported pending count
     */
    public void testAddToPendingCount() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        assertEquals(0, a.getPendingCount());
        a.addToPendingCount(1);
        assertEquals(1, a.getPendingCount());
        a.addToPendingCount(27);
        assertEquals(28, a.getPendingCount());
    }

    /**
     * decrementPendingCountUnlessZero decrements reported pending
     * count unless zero
     */
    public void testDecrementPendingCount() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        assertEquals(0, a.getPendingCount());
        a.addToPendingCount(1);
        assertEquals(1, a.getPendingCount());
        a.decrementPendingCountUnlessZero();
        assertEquals(0, a.getPendingCount());
        a.decrementPendingCountUnlessZero();
        assertEquals(0, a.getPendingCount());
    }

    /**
     * getCompleter returns parent or null if at root
     */
    public void testGetCompleter() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        assertNull(a.getCompleter());
        CountedCompleter b = new NoopCountedCompleter(a);
        assertEquals(a, b.getCompleter());
    }
     
    /**
     * getRoot returns self if no parent, else parent's root
     */
    public void testGetRoot() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        NoopCountedCompleter b = new NoopCountedCompleter(a);
        assertEquals(a, a.getRoot());
        assertEquals(a, b.getRoot());
    }
              
    /**
     * tryComplete causes completion if pending count is zero
     */
    public void testTryComplete1() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        assertEquals(0, a.getPendingCount());
        a.tryComplete();
        assertTrue(a.post);
        assertTrue(a.isDone());
    }

    /**
     * propagateCompletion causes completion without invokein
     * onCompletion if pending count is zero
     */
    public void testPropagateCompletion() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        assertEquals(0, a.getPendingCount());
        a.propagateCompletion();
        assertFalse(a.post);
        assertTrue(a.isDone());
    }

    /**
     * tryComplete decrments pending count unless zero
     */
    public void testTryComplete2() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        assertEquals(0, a.getPendingCount());
        a.setPendingCount(1);
        a.tryComplete();
        assertFalse(a.post);
        assertFalse(a.isDone());
        assertEquals(0, a.getPendingCount());
        a.tryComplete();
        assertTrue(a.post);
        assertTrue(a.isDone());
    }

    /**
     * firstComplete returns this if pending count is zero else null
     */
    public void testFirstComplete() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        a.setPendingCount(1);
        assertNull(a.firstComplete());
        assertEquals(a, a.firstComplete());
    }

    /**
     * firstComplete.nextComplete returns parent if pending count is
     * zero else null
     */
    public void testNextComplete() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        NoopCountedCompleter b = new NoopCountedCompleter(a);
        a.setPendingCount(1);
        b.setPendingCount(1);
        assertNull(b.firstComplete());
        CountedCompleter c = b.firstComplete();
        assertEquals(b, c);
        CountedCompleter d = c.nextComplete();
        assertNull(d);
        CountedCompleter e = c.nextComplete();
        assertEquals(a, e);
    }

    /**
     * quietlyCompleteRoot completes root task
     */
    public void testQuietlyCompleteRoot() {
        NoopCountedCompleter a = new NoopCountedCompleter();
        NoopCountedCompleter b = new NoopCountedCompleter(a);
        a.setPendingCount(1);
        b.setPendingCount(1);
        b.quietlyCompleteRoot();
        assertTrue(a.isDone());
        assertFalse(b.isDone());
    }
    
    /**
     * invoke returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks; getRawResult returns null.
     */
    public void testInvoke() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertNull(f.invoke());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks
     */
    public void testQuietlyInvoke() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                f.quietlyInvoke();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task returns when task completes
     */
    public void testForkJoin() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertNull(f.join());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task returns when task completes
     */
    public void testForkGet() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertNull(f.get());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task returns when task completes
     */
    public void testForkTimedGet() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertNull(f.get(LONG_DELAY_MS, MILLISECONDS));
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get with null time unit throws NPE
     */
    public void testForkTimedGetNPE() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                CCF f = new LCCF(null, 8);
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
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * helpQuiesce returns when tasks are complete.
     * getQueuedTaskCount returns 0 when quiescent
     */
    public void testForkHelpQuiesce() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                helpQuiesce();
                assertEquals(21, f.number);
                assertEquals(0, getQueuedTaskCount());
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task throws exception when task completes abnormally
     */
    public void testAbnormalInvoke() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF f = new LFCCF(null, 8);
                try {
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes abnormally
     */
    public void testAbnormalQuietlyInvoke() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF f = new LFCCF(null, 8);
                f.quietlyInvoke();
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkJoin() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF f = new LFCCF(null, 8);
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkGet() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                FailingCCF f = new LFCCF(null, 8);
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (ExecutionException success) {
                    Throwable cause = success.getCause();
                    assertTrue(cause instanceof FJException);
                    checkCompletedAbnormally(f, cause);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkTimedGet() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                FailingCCF f = new LFCCF(null, 8);
                assertSame(f, f.fork());
                try {
                    f.get(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (ExecutionException success) {
                    Throwable cause = success.getCause();
                    assertTrue(cause instanceof FJException);
                    checkCompletedAbnormally(f, cause);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes abnormally
     */
    public void testAbnormalForkQuietlyJoin() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF f = new LFCCF(null, 8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invoke task throws exception when task cancelled
     */
    public void testCancelledInvoke() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertTrue(f.cancel(true));
                try {
                    f.invoke();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkJoin() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkGet() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                CCF f = new LCCF(null, 8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkTimedGet() throws Exception {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                CCF f = new LCCF(null, 8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task cancelled
     */
    public void testCancelledForkQuietlyJoin() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                f.quietlyJoin();
                checkCancelled(f);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * getPool of executing task returns its pool
     */
    public void testGetPool() {
        final ForkJoinPool mainPool = mainPool();
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                assertSame(mainPool, getPool());
            }};
        testInvokeOnPool(mainPool, a);
    }

    /**
     * getPool of non-FJ task returns null
     */
    public void testGetPool2() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                assertNull(getPool());
            }};
        assertNull(a.invoke());
    }

    /**
     * inForkJoinPool of executing task returns true
     */
    public void testInForkJoinPool() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                assertTrue(inForkJoinPool());
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * inForkJoinPool of non-FJ task returns false
     */
    public void testInForkJoinPool2() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                assertFalse(inForkJoinPool());
            }};
        assertNull(a.invoke());
    }

    /**
     * setRawResult(null) succeeds
     */
    public void testSetRawResult() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                setRawResult(null);
                assertNull(getRawResult());
            }};
        assertNull(a.invoke());
    }

    /**
     * invoke task throws exception after invoking completeExceptionally
     */
    public void testCompleteExceptionally2() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                f.completeExceptionally(new FJException());
                try {
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(t1, t2) invokes all task arguments
     */
    public void testInvokeAll2() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                CCF g = new LCCF(null, 9);
                invokeAll(f, g);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument invokes task
     */
    public void testInvokeAll1() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                invokeAll(f);
                checkCompletedNormally(f);
                assertEquals(21, f.number);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument invokes tasks
     */
    public void testInvokeAll3() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                CCF g = new LCCF(null, 9);
                CCF h = new LCCF(null, 7);
                invokeAll(f, g, h);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                assertEquals(13, h.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(collection) invokes all tasks in the collection
     */
    public void testInvokeAllCollection() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                CCF g = new LCCF(null, 9);
                CCF h = new LCCF(null, 7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                invokeAll(set);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                assertEquals(13, h.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with any null task throws NPE
     */
    public void testInvokeAllNPE() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                CCF g = new LCCF(null, 9);
                CCF h = null;
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
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                FailingCCF g = new LFCCF(null, 9);
                try {
                    invokeAll(f, g);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument throws exception if task does
     */
    public void testAbnormalInvokeAll1() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF g = new LFCCF(null, 9);
                try {
                    invokeAll(g);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument throws exception if any task does
     */
    public void testAbnormalInvokeAll3() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                FailingCCF g = new LFCCF(null, 9);
                CCF h = new LCCF(null, 7);
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * invokeAll(collection)  throws exception if any task does
     */
    public void testAbnormalInvokeAllCollection() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF f = new LFCCF(null, 8);
                CCF g = new LCCF(null, 9);
                CCF h = new LCCF(null, 7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                try {
                    invokeAll(set);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(mainPool(), a);
    }

    /**
     * tryUnfork returns true for most recent unexecuted task,
     * and suppresses execution
     */
    public void testTryUnfork() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF g = new LCCF(null, 9);
                assertSame(g, g.fork());
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertTrue(f.tryUnfork());
                helpQuiesce();
                checkNotDone(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * getSurplusQueuedTaskCount returns > 0 when
     * there are more tasks than threads
     */
    public void testGetSurplusQueuedTaskCount() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF h = new LCCF(null, 7);
                assertSame(h, h.fork());
                CCF g = new LCCF(null, 9);
                assertSame(g, g.fork());
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertTrue(getSurplusQueuedTaskCount() > 0);
                helpQuiesce();
                assertEquals(0, getSurplusQueuedTaskCount());
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * peekNextLocalTask returns most recent unexecuted task.
     */
    public void testPeekNextLocalTask() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF g = new LCCF(null, 9);
                assertSame(g, g.fork());
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertSame(f, peekNextLocalTask());
                assertNull(f.join());
                checkCompletedNormally(f);
                helpQuiesce();
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * pollNextLocalTask returns most recent unexecuted task without
     * executing it
     */
    public void testPollNextLocalTask() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF g = new LCCF(null, 9);
                assertSame(g, g.fork());
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertSame(f, pollNextLocalTask());
                helpQuiesce();
                checkNotDone(f);
                assertEquals(34, g.number);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * pollTask returns an unexecuted task without executing it
     */
    public void testPollTask() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF g = new LCCF(null, 9);
                assertSame(g, g.fork());
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertSame(f, pollTask());
                helpQuiesce();
                checkNotDone(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * peekNextLocalTask returns least recent unexecuted task in async mode
     */
    public void testPeekNextLocalTaskAsync() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF g = new LCCF(null, 9);
                assertSame(g, g.fork());
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertSame(g, peekNextLocalTask());
                assertNull(f.join());
                helpQuiesce();
                checkCompletedNormally(f);
                assertEquals(34, g.number);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    /**
     * pollNextLocalTask returns least recent unexecuted task without
     * executing it, in async mode
     */
    public void testPollNextLocalTaskAsync() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF g = new LCCF(null, 9);
                assertSame(g, g.fork());
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertSame(g, pollNextLocalTask());
                helpQuiesce();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
                checkNotDone(g);
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    /**
     * pollTask returns an unexecuted task without executing it, in
     * async mode
     */
    public void testPollTaskAsync() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF g = new LCCF(null, 9);
                assertSame(g, g.fork());
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertSame(g, pollTask());
                helpQuiesce();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
                checkNotDone(g);
            }};
        testInvokeOnPool(asyncSingletonPool(), a);
    }

    // versions for singleton pools

    /**
     * invoke returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks; getRawResult returns null.
     */
    public void testInvokeSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertNull(f.invoke());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes normally.
     * isCompletedAbnormally and isCancelled return false for normally
     * completed tasks
     */
    public void testQuietlyInvokeSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                f.quietlyInvoke();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * join of a forked task returns when task completes
     */
    public void testForkJoinSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertNull(f.join());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * get of a forked task returns when task completes
     */
    public void testForkGetSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertNull(f.get());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * timed get of a forked task returns when task completes
     */
    public void testForkTimedGetSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                assertNull(f.get(LONG_DELAY_MS, MILLISECONDS));
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * timed get with null time unit throws NPE
     */
    public void testForkTimedGetNPESingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                try {
                    f.get(5L, null);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes
     */
    public void testForkQuietlyJoinSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * helpQuiesce returns when tasks are complete.
     * getQueuedTaskCount returns 0 when quiescent
     */
    public void testForkHelpQuiesceSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertSame(f, f.fork());
                helpQuiesce();
                assertEquals(0, getQueuedTaskCount());
                assertEquals(21, f.number);
                checkCompletedNormally(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invoke task throws exception when task completes abnormally
     */
    public void testAbnormalInvokeSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF f = new LFCCF(null, 8);
                try {
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * quietlyInvoke task returns when task completes abnormally
     */
    public void testAbnormalQuietlyInvokeSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF f = new LFCCF(null, 8);
                f.quietlyInvoke();
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * join of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkJoinSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF f = new LFCCF(null, 8);
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkGetSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                FailingCCF f = new LFCCF(null, 8);
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (ExecutionException success) {
                    Throwable cause = success.getCause();
                    assertTrue(cause instanceof FJException);
                    checkCompletedAbnormally(f, cause);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task completes abnormally
     */
    public void testAbnormalForkTimedGetSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                FailingCCF f = new LFCCF(null, 8);
                assertSame(f, f.fork());
                try {
                    f.get(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (ExecutionException success) {
                    Throwable cause = success.getCause();
                    assertTrue(cause instanceof FJException);
                    checkCompletedAbnormally(f, cause);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task completes abnormally
     */
    public void testAbnormalForkQuietlyJoinSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF f = new LFCCF(null, 8);
                assertSame(f, f.fork());
                f.quietlyJoin();
                assertTrue(f.getException() instanceof FJException);
                checkCompletedAbnormally(f, f.getException());
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invoke task throws exception when task cancelled
     */
    public void testCancelledInvokeSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertTrue(f.cancel(true));
                try {
                    f.invoke();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * join of a forked task throws exception when task cancelled
     */
    public void testCancelledForkJoinSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.join();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkGetSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                CCF f = new LCCF(null, 8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get();
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * timed get of a forked task throws exception when task cancelled
     */
    public void testCancelledForkTimedGetSingleton() throws Exception {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() throws Exception {
                CCF f = new LCCF(null, 8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                try {
                    f.get(LONG_DELAY_MS, MILLISECONDS);
                    shouldThrow();
                } catch (CancellationException success) {
                    checkCancelled(f);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * quietlyJoin of a forked task returns when task cancelled
     */
    public void testCancelledForkQuietlyJoinSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                assertTrue(f.cancel(true));
                assertSame(f, f.fork());
                f.quietlyJoin();
                checkCancelled(f);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invoke task throws exception after invoking completeExceptionally
     */
    public void testCompleteExceptionallySingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                f.completeExceptionally(new FJException());
                try {
                    f.invoke();
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(t1, t2) invokes all task arguments
     */
    public void testInvokeAll2Singleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                CCF g = new LCCF(null, 9);
                invokeAll(f, g);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument invokes task
     */
    public void testInvokeAll1Singleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                invokeAll(f);
                checkCompletedNormally(f);
                assertEquals(21, f.number);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument invokes tasks
     */
    public void testInvokeAll3Singleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                CCF g = new LCCF(null, 9);
                CCF h = new LCCF(null, 7);
                invokeAll(f, g, h);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                assertEquals(13, h.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(collection) invokes all tasks in the collection
     */
    public void testInvokeAllCollectionSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                CCF g = new LCCF(null, 9);
                CCF h = new LCCF(null, 7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                invokeAll(set);
                assertEquals(21, f.number);
                assertEquals(34, g.number);
                assertEquals(13, h.number);
                checkCompletedNormally(f);
                checkCompletedNormally(g);
                checkCompletedNormally(h);
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(tasks) with any null task throws NPE
     */
    public void testInvokeAllNPESingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                CCF g = new LCCF(null, 9);
                CCF h = null;
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (NullPointerException success) {}
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(t1, t2) throw exception if any task does
     */
    public void testAbnormalInvokeAll2Singleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                FailingCCF g = new LFCCF(null, 9);
                try {
                    invokeAll(f, g);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(tasks) with 1 argument throws exception if task does
     */
    public void testAbnormalInvokeAll1Singleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF g = new LFCCF(null, 9);
                try {
                    invokeAll(g);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(tasks) with > 2 argument throws exception if any task does
     */
    public void testAbnormalInvokeAll3Singleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                CCF f = new LCCF(null, 8);
                FailingCCF g = new LFCCF(null, 9);
                CCF h = new LCCF(null, 7);
                try {
                    invokeAll(f, g, h);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(g, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

    /**
     * invokeAll(collection)  throws exception if any task does
     */
    public void testAbnormalInvokeAllCollectionSingleton() {
       ForkJoinTask a =  new CheckedFJTask() {
            public void realCompute() {
                FailingCCF f = new LFCCF(null, 8);
                CCF g = new LCCF(null, 9);
                CCF h = new LCCF(null, 7);
                HashSet set = new HashSet();
                set.add(f);
                set.add(g);
                set.add(h);
                try {
                    invokeAll(set);
                    shouldThrow();
                } catch (FJException success) {
                    checkCompletedAbnormally(f, success);
                }
            }};
        testInvokeOnPool(singletonPool(), a);
    }

}
