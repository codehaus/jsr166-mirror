/*
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import junit.framework.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.BiFunction;

public class CompletableFutureTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(CompletableFutureTest.class);
    }

    static class CFException extends RuntimeException {}

    void checkIncomplete(CompletableFuture<?> f) {
        assertFalse(f.isDone());
        assertFalse(f.isCancelled());
        assertTrue(f.toString().contains("[Not completed]"));
        try {
            assertNull(f.getNow(null));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            f.get(0L, SECONDS);
            shouldThrow();
        }
        catch (TimeoutException success) {}
        catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    <T> void checkCompletedNormally(CompletableFuture<T> f, T value) {
        try {
            assertEquals(value, f.get(LONG_DELAY_MS, MILLISECONDS));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertEquals(value, f.join());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertEquals(value, f.getNow(null));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertEquals(value, f.get());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());
        assertFalse(f.isCompletedExceptionally());
        assertTrue(f.toString().contains("[Completed normally]"));
    }

    void checkCompletedWithWrappedCFException(CompletableFuture<?> f) {
        try {
            f.get(LONG_DELAY_MS, MILLISECONDS);
            shouldThrow();
        } catch (ExecutionException success) {
            assertTrue(success.getCause() instanceof CFException);
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            f.join();
            shouldThrow();
        } catch (CompletionException success) {
            assertTrue(success.getCause() instanceof CFException);
        }
        try {
            f.getNow(null);
            shouldThrow();
        } catch (CompletionException success) {
            assertTrue(success.getCause() instanceof CFException);
        }
        try {
            f.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertTrue(success.getCause() instanceof CFException);
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());
        assertTrue(f.toString().contains("[Completed exceptionally]"));
    }

    <U> void checkCompletedExceptionallyWithRootCause(CompletableFuture<U> f,
                                                      Throwable ex) {
        try {
            f.get(LONG_DELAY_MS, MILLISECONDS);
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(ex, success.getCause());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            f.join();
            shouldThrow();
        } catch (CompletionException success) {
            assertSame(ex, success.getCause());
        }
        try {
            f.getNow(null);
            shouldThrow();
        } catch (CompletionException success) {
            assertSame(ex, success.getCause());
        }
        try {
            f.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertSame(ex, success.getCause());
        } catch (Throwable fail) { threadUnexpectedException(fail); }

        assertTrue(f.isDone());
        assertFalse(f.isCancelled());
        assertTrue(f.toString().contains("[Completed exceptionally]"));
    }

    <U> void checkCompletedWithWrappedException(CompletableFuture<U> f,
                                                Throwable ex) {
        checkCompletedExceptionallyWithRootCause(f, ex);
        try {
            CompletableFuture<Throwable> spy = f.handle
                ((U u, Throwable t) -> t);
            assertTrue(spy.join() instanceof CompletionException);
            assertSame(ex, spy.join().getCause());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    <U> void checkCompletedExceptionally(CompletableFuture<U> f, Throwable ex) {
        checkCompletedExceptionallyWithRootCause(f, ex);
        try {
            CompletableFuture<Throwable> spy = f.handle
                ((U u, Throwable t) -> t);
            assertSame(ex, spy.join());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    void checkCancelled(CompletableFuture<?> f) {
        try {
            f.get(LONG_DELAY_MS, MILLISECONDS);
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            f.join();
            shouldThrow();
        } catch (CancellationException success) {}
        try {
            f.getNow(null);
            shouldThrow();
        } catch (CancellationException success) {}
        try {
            f.get();
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(f.isDone());
        assertTrue(f.isCompletedExceptionally());
        assertTrue(f.isCancelled());
        assertTrue(f.toString().contains("[Completed exceptionally]"));
    }

    void checkCompletedWithWrappedCancellationException(CompletableFuture<?> f) {
        try {
            f.get(LONG_DELAY_MS, MILLISECONDS);
            shouldThrow();
        } catch (ExecutionException success) {
            assertTrue(success.getCause() instanceof CancellationException);
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            f.join();
            shouldThrow();
        } catch (CompletionException success) {
            assertTrue(success.getCause() instanceof CancellationException);
        }
        try {
            f.getNow(null);
            shouldThrow();
        } catch (CompletionException success) {
            assertTrue(success.getCause() instanceof CancellationException);
        }
        try {
            f.get();
            shouldThrow();
        } catch (ExecutionException success) {
            assertTrue(success.getCause() instanceof CancellationException);
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());
        assertTrue(f.isCompletedExceptionally());
        assertTrue(f.toString().contains("[Completed exceptionally]"));
    }

    /**
     * A newly constructed CompletableFuture is incomplete, as indicated
     * by methods isDone, isCancelled, and getNow
     */
    public void testConstructor() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
    }

    /**
     * complete completes normally, as indicated by methods isDone,
     * isCancelled, join, get, and getNow
     */
    public void testComplete() {
        for (Integer v1 : new Integer[] { 1, null })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        assertTrue(f.complete(v1));
        assertFalse(f.complete(v1));
        checkCompletedNormally(f, v1);
    }}

    /**
     * completeExceptionally completes exceptionally, as indicated by
     * methods isDone, isCancelled, join, get, and getNow
     */
    public void testCompleteExceptionally() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CFException ex = new CFException();
        checkIncomplete(f);
        f.completeExceptionally(ex);
        checkCompletedExceptionally(f, ex);
    }

    /**
     * cancel completes exceptionally and reports cancelled, as indicated by
     * methods isDone, isCancelled, join, get, and getNow
     */
    public void testCancel() {
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        assertTrue(f.cancel(true));
        assertTrue(f.cancel(true));
        checkCancelled(f);
    }}

    /**
     * obtrudeValue forces completion with given value
     */
    public void testObtrudeValue() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        assertTrue(f.complete(one));
        checkCompletedNormally(f, one);
        f.obtrudeValue(three);
        checkCompletedNormally(f, three);
        f.obtrudeValue(two);
        checkCompletedNormally(f, two);
        f = new CompletableFuture<>();
        f.obtrudeValue(three);
        checkCompletedNormally(f, three);
        f.obtrudeValue(null);
        checkCompletedNormally(f, null);
        f = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        f.obtrudeValue(four);
        checkCompletedNormally(f, four);
    }

    /**
     * obtrudeException forces completion with given exception
     */
    public void testObtrudeException() {
        for (Integer v1 : new Integer[] { 1, null })
    {
        CFException ex;
        CompletableFuture<Integer> f;

        f = new CompletableFuture<>();
        assertTrue(f.complete(v1));
        for (int i = 0; i < 2; i++) {
            f.obtrudeException(ex = new CFException());
            checkCompletedExceptionally(f, ex);
        }

        f = new CompletableFuture<>();
        for (int i = 0; i < 2; i++) {
            f.obtrudeException(ex = new CFException());
            checkCompletedExceptionally(f, ex);
        }

        f = new CompletableFuture<>();
        f.completeExceptionally(ex = new CFException());
        f.obtrudeValue(v1);
        checkCompletedNormally(f, v1);
        f.obtrudeException(ex = new CFException());
        checkCompletedExceptionally(f, ex);
        f.completeExceptionally(new CFException());
        checkCompletedExceptionally(f, ex);
        assertFalse(f.complete(v1));
        checkCompletedExceptionally(f, ex);
    }}

    /**
     * getNumberOfDependents returns number of dependent tasks
     */
    public void testGetNumberOfDependents() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        assertEquals(0, f.getNumberOfDependents());
        final CompletableFuture<Void> g = m.thenRun(f, new Noop(m));
        assertEquals(1, f.getNumberOfDependents());
        assertEquals(0, g.getNumberOfDependents());
        final CompletableFuture<Void> h = m.thenRun(f, new Noop(m));
        assertEquals(2, f.getNumberOfDependents());
        assertEquals(0, h.getNumberOfDependents());
        assertTrue(f.complete(v1));
        checkCompletedNormally(g, null);
        checkCompletedNormally(h, null);
        assertEquals(0, f.getNumberOfDependents());
        assertEquals(0, g.getNumberOfDependents());
        assertEquals(0, h.getNumberOfDependents());
    }}

    /**
     * toString indicates current completion state
     */
    public void testToString() {
        CompletableFuture<String> f;

        f = new CompletableFuture<String>();
        assertTrue(f.toString().contains("[Not completed]"));

        assertTrue(f.complete("foo"));
        assertTrue(f.toString().contains("[Completed normally]"));

        f = new CompletableFuture<String>();
        assertTrue(f.completeExceptionally(new IndexOutOfBoundsException()));
        assertTrue(f.toString().contains("[Completed exceptionally]"));

        for (boolean mayInterruptIfRunning : new boolean[] { true, false }) {
            f = new CompletableFuture<String>();
            assertTrue(f.cancel(mayInterruptIfRunning));
            assertTrue(f.toString().contains("[Completed exceptionally]"));
        }
    }

    /**
     * completedFuture returns a completed CompletableFuture with given value
     */
    public void testCompletedFuture() {
        CompletableFuture<String> f = CompletableFuture.completedFuture("test");
        checkCompletedNormally(f, "test");
    }

    abstract class CheckedAction {
        int invocationCount = 0;
        final ExecutionMode m;
        CheckedAction(ExecutionMode m) { this.m = m; }
        void invoked() {
            m.checkExecutionMode();
            assertEquals(0, invocationCount++);
        }
        void assertNotInvoked() { assertEquals(0, invocationCount); }
        void assertInvoked() { assertEquals(1, invocationCount); }
    }

    abstract class CheckedIntegerAction extends CheckedAction {
        Integer value;
        CheckedIntegerAction(ExecutionMode m) { super(m); }
        void assertValue(Integer expected) {
            assertInvoked();
            assertEquals(expected, value);
        }
    }

    class IntegerSupplier extends CheckedAction
        implements Supplier<Integer>
    {
        final Integer value;
        IntegerSupplier(ExecutionMode m, Integer value) {
            super(m);
            this.value = value;
        }
        public Integer get() {
            invoked();
            return value;
        }
    }

    // A function that handles and produces null values as well.
    static Integer inc(Integer x) {
        return (x == null) ? null : x + 1;
    }

    class NoopConsumer extends CheckedIntegerAction
        implements Consumer<Integer>
    {
        NoopConsumer(ExecutionMode m) { super(m); }
        public void accept(Integer x) {
            invoked();
            value = x;
        }
    }

    class IncFunction extends CheckedIntegerAction
        implements Function<Integer,Integer>
    {
        IncFunction(ExecutionMode m) { super(m); }
        public Integer apply(Integer x) {
            invoked();
            return value = inc(x);
        }
    }

    // Choose non-commutative actions for better coverage
    // A non-commutative function that handles and produces null values as well.
    static Integer subtract(Integer x, Integer y) {
        return (x == null && y == null) ? null :
            ((x == null) ? 42 : x.intValue())
            - ((y == null) ? 99 : y.intValue());
    }

    class SubtractAction extends CheckedIntegerAction
        implements BiConsumer<Integer, Integer>
    {
        SubtractAction(ExecutionMode m) { super(m); }
        public void accept(Integer x, Integer y) {
            invoked();
            value = subtract(x, y);
        }
    }

    class SubtractFunction extends CheckedIntegerAction
        implements BiFunction<Integer, Integer, Integer>
    {
        SubtractFunction(ExecutionMode m) { super(m); }
        public Integer apply(Integer x, Integer y) {
            invoked();
            return value = subtract(x, y);
        }
    }

    class Noop extends CheckedAction implements Runnable {
        Noop(ExecutionMode m) { super(m); }
        public void run() {
            invoked();
        }
    }

    class FailingSupplier extends CheckedAction
        implements Supplier<Integer>
    {
        FailingSupplier(ExecutionMode m) { super(m); }
        public Integer get() {
            invoked();
            throw new CFException();
        }
    }

    class FailingConsumer extends CheckedIntegerAction
        implements Consumer<Integer>
    {
        FailingConsumer(ExecutionMode m) { super(m); }
        public void accept(Integer x) {
            invoked();
            value = x;
            throw new CFException();
        }
    }

    class FailingBiConsumer extends CheckedIntegerAction
        implements BiConsumer<Integer, Integer>
    {
        FailingBiConsumer(ExecutionMode m) { super(m); }
        public void accept(Integer x, Integer y) {
            invoked();
            value = subtract(x, y);
            throw new CFException();
        }
    }

    class FailingFunction extends CheckedIntegerAction
        implements Function<Integer, Integer>
    {
        FailingFunction(ExecutionMode m) { super(m); }
        public Integer apply(Integer x) {
            invoked();
            value = x;
            throw new CFException();
        }
    }

    class FailingBiFunction extends CheckedIntegerAction
        implements BiFunction<Integer, Integer, Integer>
    {
        FailingBiFunction(ExecutionMode m) { super(m); }
        public Integer apply(Integer x, Integer y) {
            invoked();
            value = subtract(x, y);
            throw new CFException();
        }
    }

    class FailingRunnable extends CheckedAction implements Runnable {
        FailingRunnable(ExecutionMode m) { super(m); }
        public void run() {
            invoked();
            throw new CFException();
        }
    }


    class CompletableFutureInc extends CheckedIntegerAction
        implements Function<Integer, CompletableFuture<Integer>>
    {
        CompletableFutureInc(ExecutionMode m) { super(m); }
        public CompletableFuture<Integer> apply(Integer x) {
            invoked();
            value = x;
            CompletableFuture<Integer> f = new CompletableFuture<>();
            assertTrue(f.complete(inc(x)));
            return f;
        }
    }

    class FailingCompletableFutureFunction extends CheckedIntegerAction
        implements Function<Integer, CompletableFuture<Integer>>
    {
        FailingCompletableFutureFunction(ExecutionMode m) { super(m); }
        public CompletableFuture<Integer> apply(Integer x) {
            invoked();
            value = x;
            throw new CFException();
        }
    }

    // Used for explicit executor tests
    static final class ThreadExecutor implements Executor {
        final AtomicInteger count = new AtomicInteger(0);
        static final ThreadGroup tg = new ThreadGroup("ThreadExecutor");
        static boolean startedCurrentThread() {
            return Thread.currentThread().getThreadGroup() == tg;
        }

        public void execute(Runnable r) {
            count.getAndIncrement();
            new Thread(tg, r).start();
        }
    }

    /**
     * Permits the testing of parallel code for the 3 different
     * execution modes without copy/pasting all the test methods.
     */
    enum ExecutionMode {
        DEFAULT {
            public void checkExecutionMode() {
                assertFalse(ThreadExecutor.startedCurrentThread());
                assertNull(ForkJoinTask.getPool());
            }
            public CompletableFuture<Void> runAsync(Runnable a) {
                throw new UnsupportedOperationException();
            }
            public <U> CompletableFuture<U> supplyAsync(Supplier<U> a) {
                throw new UnsupportedOperationException();
            }
            public <T> CompletableFuture<Void> thenRun
                (CompletableFuture<T> f, Runnable a) {
                return f.thenRun(a);
            }
            public <T> CompletableFuture<Void> thenAccept
                (CompletableFuture<T> f, Consumer<? super T> a) {
                return f.thenAccept(a);
            }
            public <T,U> CompletableFuture<U> thenApply
                (CompletableFuture<T> f, Function<? super T,U> a) {
                return f.thenApply(a);
            }
            public <T,U> CompletableFuture<U> thenCompose
                (CompletableFuture<T> f,
                 Function<? super T,? extends CompletionStage<U>> a) {
                return f.thenCompose(a);
            }
            public <T,U> CompletableFuture<U> handle
                (CompletableFuture<T> f,
                 BiFunction<? super T,Throwable,? extends U> a) {
                return f.handle(a);
            }
            public <T> CompletableFuture<T> whenComplete
                (CompletableFuture<T> f,
                 BiConsumer<? super T,? super Throwable> a) {
                return f.whenComplete(a);
            }
            public <T,U> CompletableFuture<Void> runAfterBoth
                (CompletableFuture<T> f, CompletableFuture<U> g, Runnable a) {
                return f.runAfterBoth(g, a);
            }
            public <T,U> CompletableFuture<Void> thenAcceptBoth
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiConsumer<? super T,? super U> a) {
                return f.thenAcceptBoth(g, a);
            }
            public <T,U,V> CompletableFuture<V> thenCombine
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiFunction<? super T,? super U,? extends V> a) {
                return f.thenCombine(g, a);
            }
            public <T> CompletableFuture<Void> runAfterEither
                (CompletableFuture<T> f,
                 CompletionStage<?> g,
                 java.lang.Runnable a) {
                return f.runAfterEither(g, a);
            }
            public <T> CompletableFuture<Void> acceptEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Consumer<? super T> a) {
                return f.acceptEither(g, a);
            }
            public <T,U> CompletableFuture<U> applyToEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Function<? super T,U> a) {
                return f.applyToEither(g, a);
            }
        },

        ASYNC {
            public void checkExecutionMode() {
                assertSame(ForkJoinPool.commonPool(),
                           ForkJoinTask.getPool());
            }
            public CompletableFuture<Void> runAsync(Runnable a) {
                return CompletableFuture.runAsync(a);
            }
            public <U> CompletableFuture<U> supplyAsync(Supplier<U> a) {
                return CompletableFuture.supplyAsync(a);
            }
            public <T> CompletableFuture<Void> thenRun
                (CompletableFuture<T> f, Runnable a) {
                return f.thenRunAsync(a);
            }
            public <T> CompletableFuture<Void> thenAccept
                (CompletableFuture<T> f, Consumer<? super T> a) {
                return f.thenAcceptAsync(a);
            }
            public <T,U> CompletableFuture<U> thenApply
                (CompletableFuture<T> f, Function<? super T,U> a) {
                return f.thenApplyAsync(a);
            }
            public <T,U> CompletableFuture<U> thenCompose
                (CompletableFuture<T> f,
                 Function<? super T,? extends CompletionStage<U>> a) {
                return f.thenComposeAsync(a);
            }
            public <T,U> CompletableFuture<U> handle
                (CompletableFuture<T> f,
                 BiFunction<? super T,Throwable,? extends U> a) {
                return f.handleAsync(a);
            }
            public <T> CompletableFuture<T> whenComplete
                (CompletableFuture<T> f,
                 BiConsumer<? super T,? super Throwable> a) {
                return f.whenCompleteAsync(a);
            }
            public <T,U> CompletableFuture<Void> runAfterBoth
                (CompletableFuture<T> f, CompletableFuture<U> g, Runnable a) {
                return f.runAfterBothAsync(g, a);
            }
            public <T,U> CompletableFuture<Void> thenAcceptBoth
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiConsumer<? super T,? super U> a) {
                return f.thenAcceptBothAsync(g, a);
            }
            public <T,U,V> CompletableFuture<V> thenCombine
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiFunction<? super T,? super U,? extends V> a) {
                return f.thenCombineAsync(g, a);
            }
            public <T> CompletableFuture<Void> runAfterEither
                (CompletableFuture<T> f,
                 CompletionStage<?> g,
                 java.lang.Runnable a) {
                return f.runAfterEitherAsync(g, a);
            }
            public <T> CompletableFuture<Void> acceptEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Consumer<? super T> a) {
                return f.acceptEitherAsync(g, a);
            }
            public <T,U> CompletableFuture<U> applyToEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Function<? super T,U> a) {
                return f.applyToEitherAsync(g, a);
            }
        },

        EXECUTOR {
            public void checkExecutionMode() {
                assertTrue(ThreadExecutor.startedCurrentThread());
            }
            public CompletableFuture<Void> runAsync(Runnable a) {
                return CompletableFuture.runAsync(a, new ThreadExecutor());
            }
            public <U> CompletableFuture<U> supplyAsync(Supplier<U> a) {
                return CompletableFuture.supplyAsync(a, new ThreadExecutor());
            }
            public <T> CompletableFuture<Void> thenRun
                (CompletableFuture<T> f, Runnable a) {
                return f.thenRunAsync(a, new ThreadExecutor());
            }
            public <T> CompletableFuture<Void> thenAccept
                (CompletableFuture<T> f, Consumer<? super T> a) {
                return f.thenAcceptAsync(a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<U> thenApply
                (CompletableFuture<T> f, Function<? super T,U> a) {
                return f.thenApplyAsync(a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<U> thenCompose
                (CompletableFuture<T> f,
                 Function<? super T,? extends CompletionStage<U>> a) {
                return f.thenComposeAsync(a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<U> handle
                (CompletableFuture<T> f,
                 BiFunction<? super T,Throwable,? extends U> a) {
                return f.handleAsync(a, new ThreadExecutor());
            }
            public <T> CompletableFuture<T> whenComplete
                (CompletableFuture<T> f,
                 BiConsumer<? super T,? super Throwable> a) {
                return f.whenCompleteAsync(a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<Void> runAfterBoth
                (CompletableFuture<T> f, CompletableFuture<U> g, Runnable a) {
                return f.runAfterBothAsync(g, a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<Void> thenAcceptBoth
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiConsumer<? super T,? super U> a) {
                return f.thenAcceptBothAsync(g, a, new ThreadExecutor());
            }
            public <T,U,V> CompletableFuture<V> thenCombine
                (CompletableFuture<T> f,
                 CompletionStage<? extends U> g,
                 BiFunction<? super T,? super U,? extends V> a) {
                return f.thenCombineAsync(g, a, new ThreadExecutor());
            }
            public <T> CompletableFuture<Void> runAfterEither
                (CompletableFuture<T> f,
                 CompletionStage<?> g,
                 java.lang.Runnable a) {
                return f.runAfterEitherAsync(g, a, new ThreadExecutor());
            }
            public <T> CompletableFuture<Void> acceptEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Consumer<? super T> a) {
                return f.acceptEitherAsync(g, a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<U> applyToEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Function<? super T,U> a) {
                return f.applyToEitherAsync(g, a, new ThreadExecutor());
            }
        };

        public abstract void checkExecutionMode();
        public abstract CompletableFuture<Void> runAsync(Runnable a);
        public abstract <U> CompletableFuture<U> supplyAsync(Supplier<U> a);
        public abstract <T> CompletableFuture<Void> thenRun
            (CompletableFuture<T> f, Runnable a);
        public abstract <T> CompletableFuture<Void> thenAccept
            (CompletableFuture<T> f, Consumer<? super T> a);
        public abstract <T,U> CompletableFuture<U> thenApply
            (CompletableFuture<T> f, Function<? super T,U> a);
        public abstract <T,U> CompletableFuture<U> thenCompose
            (CompletableFuture<T> f,
             Function<? super T,? extends CompletionStage<U>> a);
        public abstract <T,U> CompletableFuture<U> handle
            (CompletableFuture<T> f,
             BiFunction<? super T,Throwable,? extends U> a);
        public abstract <T> CompletableFuture<T> whenComplete
            (CompletableFuture<T> f,
             BiConsumer<? super T,? super Throwable> a);
        public abstract <T,U> CompletableFuture<Void> runAfterBoth
            (CompletableFuture<T> f, CompletableFuture<U> g, Runnable a);
        public abstract <T,U> CompletableFuture<Void> thenAcceptBoth
            (CompletableFuture<T> f,
             CompletionStage<? extends U> g,
             BiConsumer<? super T,? super U> a);
        public abstract <T,U,V> CompletableFuture<V> thenCombine
            (CompletableFuture<T> f,
             CompletionStage<? extends U> g,
             BiFunction<? super T,? super U,? extends V> a);
        public abstract <T> CompletableFuture<Void> runAfterEither
            (CompletableFuture<T> f,
             CompletionStage<?> g,
             java.lang.Runnable a);
        public abstract <T> CompletableFuture<Void> acceptEither
            (CompletableFuture<T> f,
             CompletionStage<? extends T> g,
             Consumer<? super T> a);
        public abstract <T,U> CompletableFuture<U> applyToEither
            (CompletableFuture<T> f,
             CompletionStage<? extends T> g,
             Function<? super T,U> a);
    }

    /**
     * exceptionally action is not invoked when source completes
     * normally, and source result is propagated
     */
    public void testExceptionally_normalCompletion() {
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = f.exceptionally
            ((Throwable t) -> {
                // Should not be called
                a.getAndIncrement();
                throw new AssertionError();
            });
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedNormally(g, v1);
        checkCompletedNormally(f, v1);
        assertEquals(0, a.get());
    }}

    /**
     * exceptionally action completes with function value on source
     * exception
     */
    public void testExceptionally_exceptionalCompletion() {
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = f.exceptionally
            ((Throwable t) -> {
                ExecutionMode.DEFAULT.checkExecutionMode();
                threadAssertSame(t, ex);
                a.getAndIncrement();
                return v1;
            });
        if (createIncomplete) f.completeExceptionally(ex);

        checkCompletedNormally(g, v1);
        assertEquals(1, a.get());
    }}

    public void testExceptionally_exceptionalCompletionActionFailed() {
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex1 = new CFException();
        final CFException ex2 = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) f.completeExceptionally(ex1);
        final CompletableFuture<Integer> g = f.exceptionally
            ((Throwable t) -> {
                ExecutionMode.DEFAULT.checkExecutionMode();
                threadAssertSame(t, ex1);
                a.getAndIncrement();
                throw ex2;
            });
        if (createIncomplete) f.completeExceptionally(ex1);

        checkCompletedWithWrappedException(g, ex2);
        assertEquals(1, a.get());
    }}

    /**
     * whenComplete action executes on normal completion, propagating
     * source result.
     */
    public void testWhenComplete_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer x, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertSame(x, v1);
                threadAssertNull(t);
                a.getAndIncrement();
            });
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedNormally(g, v1);
        checkCompletedNormally(f, v1);
        assertEquals(1, a.get());
    }}

    /**
     * whenComplete action executes on exceptional completion, propagating
     * source result.
     */
    public void testWhenComplete_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer x, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertNull(x);
                threadAssertSame(t, ex);
                a.getAndIncrement();
            });
        if (createIncomplete) f.completeExceptionally(ex);

        checkCompletedWithWrappedException(g, ex);
        checkCompletedExceptionally(f, ex);
        assertEquals(1, a.get());
    }}

    /**
     * whenComplete action executes on cancelled source, propagating
     * CancellationException.
     */
    public void testWhenComplete_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean createIncomplete : new boolean[] { true, false })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer x, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertNull(x);
                threadAssertTrue(t instanceof CancellationException);
                a.getAndIncrement();
            });
        if (createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));

        checkCompletedWithWrappedCancellationException(g);
        checkCancelled(f);
        assertEquals(1, a.get());
    }}

    /**
     * If a whenComplete action throws an exception when triggered by
     * a normal completion, it completes exceptionally
     */
    public void testWhenComplete_actionFailed() {
        for (boolean createIncomplete : new boolean[] { true, false })
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer x, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertSame(x, v1);
                threadAssertNull(t);
                a.getAndIncrement();
                throw ex;
            });
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedWithWrappedException(g, ex);
        checkCompletedNormally(f, v1);
        assertEquals(1, a.get());
    }}

    /**
     * If a whenComplete action throws an exception when triggered by
     * a source completion that also throws an exception, the source
     * exception takes precedence.
     */
    public void testWhenComplete_actionFailedSourceFailed() {
        for (boolean createIncomplete : new boolean[] { true, false })
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex1 = new CFException();
        final CFException ex2 = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();

        if (!createIncomplete) f.completeExceptionally(ex1);
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer x, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertSame(t, ex1);
                threadAssertNull(x);
                a.getAndIncrement();
                throw ex2;
            });
        if (createIncomplete) f.completeExceptionally(ex1);

        checkCompletedWithWrappedException(g, ex1);
        checkCompletedExceptionally(f, ex1);
        assertEquals(1, a.get());
    }}

    /**
     * handle action completes normally with function value on normal
     * completion of source
     */
    public void testHandle_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final AtomicInteger a = new AtomicInteger(0);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer x, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertSame(x, v1);
                threadAssertNull(t);
                a.getAndIncrement();
                return inc(v1);
            });
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedNormally(g, inc(v1));
        checkCompletedNormally(f, v1);
        assertEquals(1, a.get());
    }}

    /**
     * handle action completes normally with function value on
     * exceptional completion of source
     */
    public void testHandle_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex = new CFException();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer x, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertNull(x);
                threadAssertSame(t, ex);
                a.getAndIncrement();
                return v1;
            });
        if (createIncomplete) f.completeExceptionally(ex);

        checkCompletedNormally(g, v1);
        checkCompletedExceptionally(f, ex);
        assertEquals(1, a.get());
    }}

    /**
     * handle action completes normally with function value on
     * cancelled source
     */
    public void testHandle_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final AtomicInteger a = new AtomicInteger(0);
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer x, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertNull(x);
                threadAssertTrue(t instanceof CancellationException);
                a.getAndIncrement();
                return v1;
            });
        if (createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));

        checkCompletedNormally(g, v1);
        checkCancelled(f);
        assertEquals(1, a.get());
    }}

    /**
     * handle result completes exceptionally if action does
     */
    public void testHandle_sourceFailedActionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex1 = new CFException();
        final CFException ex2 = new CFException();
        if (!createIncomplete) f.completeExceptionally(ex1);
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer x, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertNull(x);
                threadAssertSame(ex1, t);
                a.getAndIncrement();
                throw ex2;
            });
        if (createIncomplete) f.completeExceptionally(ex1);

        checkCompletedWithWrappedException(g, ex2);
        checkCompletedExceptionally(f, ex1);
        assertEquals(1, a.get());
    }}

    public void testHandle_sourceCompletedNormallyActionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final AtomicInteger a = new AtomicInteger(0);
        final CFException ex = new CFException();
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer x, Throwable t) -> {
                m.checkExecutionMode();
                threadAssertSame(x, v1);
                threadAssertNull(t);
                a.getAndIncrement();
                throw ex;
            });
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedWithWrappedException(g, ex);
        checkCompletedNormally(f, v1);
        assertEquals(1, a.get());
    }}

    /**
     * runAsync completes after running Runnable
     */
    public void testRunAsync_normalCompletion() {
        ExecutionMode[] executionModes = {
            ExecutionMode.ASYNC,
            ExecutionMode.EXECUTOR,
        };
        for (ExecutionMode m : executionModes)
    {
        final Noop r = new Noop(m);
        final CompletableFuture<Void> f = m.runAsync(r);
        assertNull(f.join());
        checkCompletedNormally(f, null);
        r.assertInvoked();
    }}

    /**
     * failing runAsync completes exceptionally after running Runnable
     */
    public void testRunAsync_exceptionalCompletion() {
        ExecutionMode[] executionModes = {
            ExecutionMode.ASYNC,
            ExecutionMode.EXECUTOR,
        };
        for (ExecutionMode m : executionModes)
    {
        final FailingRunnable r = new FailingRunnable(m);
        final CompletableFuture<Void> f = m.runAsync(r);
        checkCompletedWithWrappedCFException(f);
        r.assertInvoked();
    }}

    /**
     * supplyAsync completes with result of supplier
     */
    public void testSupplyAsync_normalCompletion() {
        ExecutionMode[] executionModes = {
            ExecutionMode.ASYNC,
            ExecutionMode.EXECUTOR,
        };
        for (ExecutionMode m : executionModes)
        for (Integer v1 : new Integer[] { 1, null })
    {
        final IntegerSupplier r = new IntegerSupplier(m, v1);
        final CompletableFuture<Integer> f = m.supplyAsync(r);
        assertSame(v1, f.join());
        checkCompletedNormally(f, v1);
        r.assertInvoked();
    }}

    /**
     * Failing supplyAsync completes exceptionally
     */
    public void testSupplyAsync_exceptionalCompletion() {
        ExecutionMode[] executionModes = {
            ExecutionMode.ASYNC,
            ExecutionMode.EXECUTOR,
        };
        for (ExecutionMode m : executionModes)
    {
        FailingSupplier r = new FailingSupplier(m);
        CompletableFuture<Integer> f = m.supplyAsync(r);
        checkCompletedWithWrappedCFException(f);
        r.assertInvoked();
    }}

    // seq completion methods

    /**
     * thenRun result completes normally after normal completion of source
     */
    public void testThenRun_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final Noop r = new Noop(m);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Void> g = m.thenRun(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.complete(v1));
        }

        checkCompletedNormally(g, null);
        checkCompletedNormally(f, v1);
        r.assertInvoked();
    }}

    /**
     * thenRun result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenRun_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
    {
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final Noop r = new Noop(m);
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Void> g = m.thenRun(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.completeExceptionally(ex);
        }

        checkCompletedWithWrappedException(g, ex);
        checkCompletedExceptionally(f, ex);
        r.assertNotInvoked();
    }}

    /**
     * thenRun result completes exceptionally if source cancelled
     */
    public void testThenRun_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final Noop r = new Noop(m);
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Void> g = m.thenRun(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.cancel(mayInterruptIfRunning));
        }

        checkCompletedWithWrappedCancellationException(g);
        checkCancelled(f);
        r.assertNotInvoked();
    }}

    /**
     * thenRun result completes exceptionally if action does
     */
    public void testThenRun_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final FailingRunnable r = new FailingRunnable(m);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Void> g = m.thenRun(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.complete(v1));
        }

        checkCompletedWithWrappedCFException(g);
        checkCompletedNormally(f, v1);
    }}

    /**
     * thenApply result completes normally after normal completion of source
     */
    public void testThenApply_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final IncFunction r = new IncFunction(m);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.thenApply(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.complete(v1));
        }

        checkCompletedNormally(g, inc(v1));
        checkCompletedNormally(f, v1);
        r.assertValue(inc(v1));
    }}

    /**
     * thenApply result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenApply_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
    {
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final IncFunction r = new IncFunction(m);
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = m.thenApply(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.completeExceptionally(ex);
        }

        checkCompletedWithWrappedException(g, ex);
        checkCompletedExceptionally(f, ex);
        r.assertNotInvoked();
    }}

    /**
     * thenApply result completes exceptionally if source cancelled
     */
    public void testThenApply_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final IncFunction r = new IncFunction(m);
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> g = m.thenApply(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.cancel(mayInterruptIfRunning));
        }

        checkCompletedWithWrappedCancellationException(g);
        checkCancelled(f);
        r.assertNotInvoked();
    }}

    /**
     * thenApply result completes exceptionally if action does
     */
    public void testThenApply_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final FailingFunction r = new FailingFunction(m);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.thenApply(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.complete(v1));
        }

        checkCompletedWithWrappedCFException(g);
        checkCompletedNormally(f, v1);
    }}

    /**
     * thenAccept result completes normally after normal completion of source
     */
    public void testThenAccept_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final NoopConsumer r = new NoopConsumer(m);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Void> g = m.thenAccept(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.complete(v1));
        }

        checkCompletedNormally(g, null);
        r.assertValue(v1);
        checkCompletedNormally(f, v1);
    }}

    /**
     * thenAccept result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenAccept_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
    {
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final NoopConsumer r = new NoopConsumer(m);
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Void> g = m.thenAccept(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.completeExceptionally(ex);
        }

        checkCompletedWithWrappedException(g, ex);
        checkCompletedExceptionally(f, ex);
        r.assertNotInvoked();
    }}

    /**
     * thenAccept result completes exceptionally if source cancelled
     */
    public void testThenAccept_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final NoopConsumer r = new NoopConsumer(m);
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Void> g = m.thenAccept(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.cancel(mayInterruptIfRunning));
        }

        checkCompletedWithWrappedCancellationException(g);
        checkCancelled(f);
        r.assertNotInvoked();
    }}

    /**
     * thenAccept result completes exceptionally if action does
     */
    public void testThenAccept_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final FailingConsumer r = new FailingConsumer(m);
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Void> g = m.thenAccept(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.complete(v1);
        }

        checkCompletedWithWrappedCFException(g);
        checkCompletedNormally(f, v1);
    }}

    /**
     * thenCombine result completes normally after normal completion
     * of sources
     */
    public void testThenCombine_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r1 = new SubtractFunction(m);
        final SubtractFunction r2 = new SubtractFunction(m);
        final SubtractFunction r3 = new SubtractFunction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Integer> h1 = m.thenCombine(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Integer> h2 = m.thenCombine(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        assertTrue(snd.complete(w2));
        final CompletableFuture<Integer> h3 = m.thenCombine(f, g, r3);

        checkCompletedNormally(h1, subtract(v1, v2));
        checkCompletedNormally(h2, subtract(v1, v2));
        checkCompletedNormally(h3, subtract(v1, v2));
        r1.assertValue(subtract(v1, v2));
        r2.assertValue(subtract(v1, v2));
        r3.assertValue(subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * thenCombine result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenCombine_exceptionalCompletion() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final SubtractFunction r1 = new SubtractFunction(m);
        final SubtractFunction r2 = new SubtractFunction(m);
        final SubtractFunction r3 = new SubtractFunction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.completeExceptionally(ex) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.completeExceptionally(ex);

        final CompletableFuture<Integer> h1 = m.thenCombine(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Integer> h2 = m.thenCombine(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Integer> h3 = m.thenCombine(f, g, r3);

        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCompletedExceptionally(failFirst ? fst : snd, ex);
    }}

    /**
     * thenCombine result completes exceptionally if either source cancelled
     */
    public void testThenCombine_sourceCancelled() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r1 = new SubtractFunction(m);
        final SubtractFunction r2 = new SubtractFunction(m);
        final SubtractFunction r3 = new SubtractFunction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.cancel(mayInterruptIfRunning) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.cancel(mayInterruptIfRunning);

        final CompletableFuture<Integer> h1 = m.thenCombine(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Integer> h2 = m.thenCombine(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Integer> h3 = m.thenCombine(f, g, r3);

        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCancelled(failFirst ? fst : snd);
    }}

    /**
     * thenCombine result completes exceptionally if action does
     */
    public void testThenCombine_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingBiFunction r1 = new FailingBiFunction(m);
        final FailingBiFunction r2 = new FailingBiFunction(m);
        final FailingBiFunction r3 = new FailingBiFunction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Integer> h1 = m.thenCombine(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Integer> h2 = m.thenCombine(f, g, r2);
        assertTrue(snd.complete(w2));
        final CompletableFuture<Integer> h3 = m.thenCombine(f, g, r3);

        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        r1.assertInvoked();
        r2.assertInvoked();
        r3.assertInvoked();
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * thenAcceptBoth result completes normally after normal
     * completion of sources
     */
    public void testThenAcceptBoth_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r1 = new SubtractAction(m);
        final SubtractAction r2 = new SubtractAction(m);
        final SubtractAction r3 = new SubtractAction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Void> h1 = m.thenAcceptBoth(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Void> h2 = m.thenAcceptBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        assertTrue(snd.complete(w2));
        final CompletableFuture<Void> h3 = m.thenAcceptBoth(f, g, r3);

        checkCompletedNormally(h1, null);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        r1.assertValue(subtract(v1, v2));
        r2.assertValue(subtract(v1, v2));
        r3.assertValue(subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * thenAcceptBoth result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenAcceptBoth_exceptionalCompletion() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final SubtractAction r1 = new SubtractAction(m);
        final SubtractAction r2 = new SubtractAction(m);
        final SubtractAction r3 = new SubtractAction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.completeExceptionally(ex) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.completeExceptionally(ex);

        final CompletableFuture<Void> h1 = m.thenAcceptBoth(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Void> h2 = m.thenAcceptBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Void> h3 = m.thenAcceptBoth(f, g, r3);

        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCompletedExceptionally(failFirst ? fst : snd, ex);
    }}

    /**
     * thenAcceptBoth result completes exceptionally if either source cancelled
     */
    public void testThenAcceptBoth_sourceCancelled() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r1 = new SubtractAction(m);
        final SubtractAction r2 = new SubtractAction(m);
        final SubtractAction r3 = new SubtractAction(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.cancel(mayInterruptIfRunning) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.cancel(mayInterruptIfRunning);

        final CompletableFuture<Void> h1 = m.thenAcceptBoth(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Void> h2 = m.thenAcceptBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Void> h3 = m.thenAcceptBoth(f, g, r3);

        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCancelled(failFirst ? fst : snd);
    }}

    /**
     * thenAcceptBoth result completes exceptionally if action does
     */
    public void testThenAcceptBoth_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingBiConsumer r1 = new FailingBiConsumer(m);
        final FailingBiConsumer r2 = new FailingBiConsumer(m);
        final FailingBiConsumer r3 = new FailingBiConsumer(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Void> h1 = m.thenAcceptBoth(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Void> h2 = m.thenAcceptBoth(f, g, r2);
        assertTrue(snd.complete(w2));
        final CompletableFuture<Void> h3 = m.thenAcceptBoth(f, g, r3);

        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        r1.assertInvoked();
        r2.assertInvoked();
        r3.assertInvoked();
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * runAfterBoth result completes normally after normal
     * completion of sources
     */
    public void testRunAfterBoth_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r1 = new Noop(m);
        final Noop r2 = new Noop(m);
        final Noop r3 = new Noop(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Void> h1 = m.runAfterBoth(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Void> h2 = m.runAfterBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        assertTrue(snd.complete(w2));
        final CompletableFuture<Void> h3 = m.runAfterBoth(f, g, r3);

        checkCompletedNormally(h1, null);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        r1.assertInvoked();
        r2.assertInvoked();
        r3.assertInvoked();
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * runAfterBoth result completes exceptionally after exceptional
     * completion of either source
     */
    public void testRunAfterBoth_exceptionalCompletion() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final Noop r1 = new Noop(m);
        final Noop r2 = new Noop(m);
        final Noop r3 = new Noop(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.completeExceptionally(ex) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.completeExceptionally(ex);

        final CompletableFuture<Void> h1 = m.runAfterBoth(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Void> h2 = m.runAfterBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Void> h3 = m.runAfterBoth(f, g, r3);

        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCompletedExceptionally(failFirst ? fst : snd, ex);
    }}

    /**
     * runAfterBoth result completes exceptionally if either source cancelled
     */
    public void testRunAfterBoth_sourceCancelled() throws Throwable {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (boolean failFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r1 = new Noop(m);
        final Noop r2 = new Noop(m);
        final Noop r3 = new Noop(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Callable<Boolean> complete1 = failFirst ?
            () -> fst.cancel(mayInterruptIfRunning) :
            () -> fst.complete(v1);
        final Callable<Boolean> complete2 = failFirst ?
            () -> snd.complete(v1) :
            () -> snd.cancel(mayInterruptIfRunning);

        final CompletableFuture<Void> h1 = m.runAfterBoth(f, g, r1);
        assertTrue(complete1.call());
        final CompletableFuture<Void> h2 = m.runAfterBoth(f, g, r2);
        checkIncomplete(h1);
        checkIncomplete(h2);
        assertTrue(complete2.call());
        final CompletableFuture<Void> h3 = m.runAfterBoth(f, g, r3);

        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        r1.assertNotInvoked();
        r2.assertNotInvoked();
        r3.assertNotInvoked();
        checkCompletedNormally(failFirst ? snd : fst, v1);
        checkCancelled(failFirst ? fst : snd);
    }}

    /**
     * runAfterBoth result completes exceptionally if action does
     */
    public void testRunAfterBoth_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingRunnable r1 = new FailingRunnable(m);
        final FailingRunnable r2 = new FailingRunnable(m);
        final FailingRunnable r3 = new FailingRunnable(m);

        final CompletableFuture<Integer> fst =  fFirst ? f : g;
        final CompletableFuture<Integer> snd = !fFirst ? f : g;
        final Integer w1 =  fFirst ? v1 : v2;
        final Integer w2 = !fFirst ? v1 : v2;

        final CompletableFuture<Void> h1 = m.runAfterBoth(f, g, r1);
        assertTrue(fst.complete(w1));
        final CompletableFuture<Void> h2 = m.runAfterBoth(f, g, r2);
        assertTrue(snd.complete(w2));
        final CompletableFuture<Void> h3 = m.runAfterBoth(f, g, r3);

        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        r1.assertInvoked();
        r2.assertInvoked();
        r3.assertInvoked();
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * applyToEither result completes normally after normal completion
     * of either source
     */
    public void testApplyToEither_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction[] rs = new IncFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.complete(v1);
        checkCompletedNormally(h0, inc(v1));
        checkCompletedNormally(h1, inc(v1));
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);
        checkCompletedNormally(h2, inc(v1));
        checkCompletedNormally(h3, inc(v1));
        g.complete(v2);

        // unspecified behavior - both source completions available
        final CompletableFuture<Integer> h4 = m.applyToEither(f, g, rs[4]);
        final CompletableFuture<Integer> h5 = m.applyToEither(g, f, rs[5]);
        rs[4].assertValue(h4.join());
        rs[5].assertValue(h5.join());
        assertTrue(Objects.equals(inc(v1), h4.join()) ||
                   Objects.equals(inc(v2), h4.join()));
        assertTrue(Objects.equals(inc(v1), h5.join()) ||
                   Objects.equals(inc(v2), h5.join()));

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h0, inc(v1));
        checkCompletedNormally(h1, inc(v1));
        checkCompletedNormally(h2, inc(v1));
        checkCompletedNormally(h3, inc(v1));
        for (int i = 0; i < 4; i++) rs[i].assertValue(inc(v1));
    }}

    /**
     * applyToEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testApplyToEither_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final IncFunction[] rs = new IncFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.completeExceptionally(ex);
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        g.complete(v1);

        // unspecified behavior - both source completions available
        final CompletableFuture<Integer> h4 = m.applyToEither(f, g, rs[4]);
        final CompletableFuture<Integer> h5 = m.applyToEither(g, f, rs[5]);
        try {
            assertEquals(inc(v1), h4.join());
            rs[4].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h4, ex);
            rs[4].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h5.join());
            rs[5].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h5, ex);
            rs[5].assertNotInvoked();
        }

        checkCompletedExceptionally(f, ex);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        checkCompletedWithWrappedException(h4, ex);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    public void testApplyToEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final IncFunction[] rs = new IncFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        assertTrue(fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        assertTrue(!fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);

        // unspecified behavior - both source completions available
        try {
            assertEquals(inc(v1), h0.join());
            rs[0].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h0, ex);
            rs[0].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h1.join());
            rs[1].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h1, ex);
            rs[1].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h2.join());
            rs[2].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h2, ex);
            rs[2].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h3.join());
            rs[3].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h3, ex);
            rs[3].assertNotInvoked();
        }

        checkCompletedNormally(f, v1);
        checkCompletedExceptionally(g, ex);
    }}

    /**
     * applyToEither result completes exceptionally if either source cancelled
     */
    public void testApplyToEither_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction[] rs = new IncFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.cancel(mayInterruptIfRunning);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        g.complete(v1);

        // unspecified behavior - both source completions available
        final CompletableFuture<Integer> h4 = m.applyToEither(f, g, rs[4]);
        final CompletableFuture<Integer> h5 = m.applyToEither(g, f, rs[5]);
        try {
            assertEquals(inc(v1), h4.join());
            rs[4].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h4);
            rs[4].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h5.join());
            rs[5].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h5);
            rs[5].assertNotInvoked();
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    public void testApplyToEither_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction[] rs = new IncFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new IncFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        assertTrue(fFirst ? f.complete(v1) : g.cancel(mayInterruptIfRunning));
        assertTrue(!fFirst ? f.complete(v1) : g.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);

        // unspecified behavior - both source completions available
        try {
            assertEquals(inc(v1), h0.join());
            rs[0].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h0);
            rs[0].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h1.join());
            rs[1].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h1);
            rs[1].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h2.join());
            rs[2].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h2);
            rs[2].assertNotInvoked();
        }
        try {
            assertEquals(inc(v1), h3.join());
            rs[3].assertValue(inc(v1));
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h3);
            rs[3].assertNotInvoked();
        }

        checkCompletedNormally(f, v1);
        checkCancelled(g);
    }}

    /**
     * applyToEither result completes exceptionally if action does
     */
    public void testApplyToEither_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingFunction[] rs = new FailingFunction[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new FailingFunction(m);

        final CompletableFuture<Integer> h0 = m.applyToEither(f, g, rs[0]);
        final CompletableFuture<Integer> h1 = m.applyToEither(g, f, rs[1]);
        f.complete(v1);
        final CompletableFuture<Integer> h2 = m.applyToEither(f, g, rs[2]);
        final CompletableFuture<Integer> h3 = m.applyToEither(g, f, rs[3]);
        checkCompletedWithWrappedCFException(h0);
        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertValue(v1);

        g.complete(v2);

        // unspecified behavior - both source completions available
        final CompletableFuture<Integer> h4 = m.applyToEither(f, g, rs[4]);
        final CompletableFuture<Integer> h5 = m.applyToEither(g, f, rs[5]);

        checkCompletedWithWrappedCFException(h4);
        assertTrue(Objects.equals(v1, rs[4].value) ||
                   Objects.equals(v2, rs[4].value));
        checkCompletedWithWrappedCFException(h5);
        assertTrue(Objects.equals(v1, rs[5].value) ||
                   Objects.equals(v2, rs[5].value));

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * acceptEither result completes normally after normal completion
     * of either source
     */
    public void testAcceptEither_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final NoopConsumer[] rs = new NoopConsumer[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.acceptEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.complete(v1);
        checkCompletedNormally(h0, null);
        checkCompletedNormally(h1, null);
        rs[0].assertValue(v1);
        rs[1].assertValue(v1);
        final CompletableFuture<Void> h2 = m.acceptEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(g, f, rs[3]);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        rs[2].assertValue(v1);
        rs[3].assertValue(v1);
        g.complete(v2);

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.acceptEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.acceptEither(g, f, rs[5]);
        checkCompletedNormally(h4, null);
        checkCompletedNormally(h5, null);
        assertTrue(Objects.equals(v1, rs[4].value) ||
                   Objects.equals(v2, rs[4].value));
        assertTrue(Objects.equals(v1, rs[5].value) ||
                   Objects.equals(v2, rs[5].value));

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h0, null);
        checkCompletedNormally(h1, null);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        for (int i = 0; i < 4; i++) rs[i].assertValue(v1);
    }}

    /**
     * acceptEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testAcceptEither_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final NoopConsumer[] rs = new NoopConsumer[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.acceptEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.completeExceptionally(ex);
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        final CompletableFuture<Void> h2 = m.acceptEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(g, f, rs[3]);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);

        g.complete(v1);

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.acceptEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.acceptEither(g, f, rs[5]);
        try {
            assertNull(h4.join());
            rs[4].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h4, ex);
            rs[4].assertNotInvoked();
        }
        try {
            assertNull(h5.join());
            rs[5].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h5, ex);
            rs[5].assertNotInvoked();
        }

        checkCompletedExceptionally(f, ex);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        checkCompletedWithWrappedException(h4, ex);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    public void testAcceptEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final NoopConsumer[] rs = new NoopConsumer[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.acceptEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(g, f, rs[1]);
        assertTrue(fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        assertTrue(!fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        final CompletableFuture<Void> h2 = m.acceptEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(g, f, rs[3]);

        // unspecified behavior - both source completions available
        try {
            assertEquals(null, h0.join());
            rs[0].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h0, ex);
            rs[0].assertNotInvoked();
        }
        try {
            assertEquals(null, h1.join());
            rs[1].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h1, ex);
            rs[1].assertNotInvoked();
        }
        try {
            assertEquals(null, h2.join());
            rs[2].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h2, ex);
            rs[2].assertNotInvoked();
        }
        try {
            assertEquals(null, h3.join());
            rs[3].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h3, ex);
            rs[3].assertNotInvoked();
        }

        checkCompletedNormally(f, v1);
        checkCompletedExceptionally(g, ex);
    }}

    /**
     * acceptEither result completes exceptionally if either source cancelled
     */
    public void testAcceptEither_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final NoopConsumer[] rs = new NoopConsumer[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new NoopConsumer(m);

        final CompletableFuture<Void> h0 = m.acceptEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.cancel(mayInterruptIfRunning);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        final CompletableFuture<Void> h2 = m.acceptEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(g, f, rs[3]);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);

        g.complete(v1);

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.acceptEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.acceptEither(g, f, rs[5]);
        try {
            assertNull(h4.join());
            rs[4].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h4);
            rs[4].assertNotInvoked();
        }
        try {
            assertNull(h5.join());
            rs[5].assertValue(v1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h5);
            rs[5].assertNotInvoked();
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    /**
     * acceptEither result completes exceptionally if action does
     */
    public void testAcceptEither_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingConsumer[] rs = new FailingConsumer[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new FailingConsumer(m);

        final CompletableFuture<Void> h0 = m.acceptEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.acceptEither(g, f, rs[1]);
        f.complete(v1);
        final CompletableFuture<Void> h2 = m.acceptEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.acceptEither(g, f, rs[3]);
        checkCompletedWithWrappedCFException(h0);
        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertValue(v1);

        g.complete(v2);

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.acceptEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.acceptEither(g, f, rs[5]);

        checkCompletedWithWrappedCFException(h4);
        assertTrue(Objects.equals(v1, rs[4].value) ||
                   Objects.equals(v2, rs[4].value));
        checkCompletedWithWrappedCFException(h5);
        assertTrue(Objects.equals(v1, rs[5].value) ||
                   Objects.equals(v2, rs[5].value));

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * runAfterEither result completes normally after normal completion
     * of either source
     */
    public void testRunAfterEither_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.complete(v1);
        checkCompletedNormally(h0, null);
        checkCompletedNormally(h1, null);
        rs[0].assertInvoked();
        rs[1].assertInvoked();
        final CompletableFuture<Void> h2 = m.runAfterEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.runAfterEither(g, f, rs[3]);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        rs[2].assertInvoked();
        rs[3].assertInvoked();

        g.complete(v2);

        final CompletableFuture<Void> h4 = m.runAfterEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(g, f, rs[5]);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h0, null);
        checkCompletedNormally(h1, null);
        checkCompletedNormally(h2, null);
        checkCompletedNormally(h3, null);
        checkCompletedNormally(h4, null);
        checkCompletedNormally(h5, null);
        for (int i = 0; i < 6; i++) rs[i].assertInvoked();
    }}

    /**
     * runAfterEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testRunAfterEither_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        assertTrue(f.completeExceptionally(ex));
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        final CompletableFuture<Void> h2 = m.runAfterEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.runAfterEither(g, f, rs[3]);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);

        assertTrue(g.complete(v1));

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.runAfterEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(g, f, rs[5]);
        try {
            assertNull(h4.join());
            rs[4].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h4, ex);
            rs[4].assertNotInvoked();
        }
        try {
            assertNull(h5.join());
            rs[5].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h5, ex);
            rs[5].assertNotInvoked();
        }

        checkCompletedExceptionally(f, ex);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedException(h0, ex);
        checkCompletedWithWrappedException(h1, ex);
        checkCompletedWithWrappedException(h2, ex);
        checkCompletedWithWrappedException(h3, ex);
        checkCompletedWithWrappedException(h4, ex);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    public void testRunAfterEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
        assertTrue( fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        assertTrue(!fFirst ? f.complete(v1) : g.completeExceptionally(ex));
        final CompletableFuture<Void> h2 = m.runAfterEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.runAfterEither(g, f, rs[3]);

        // unspecified behavior - both source completions available
        try {
            assertEquals(null, h0.join());
            rs[0].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h0, ex);
            rs[0].assertNotInvoked();
        }
        try {
            assertEquals(null, h1.join());
            rs[1].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h1, ex);
            rs[1].assertNotInvoked();
        }
        try {
            assertEquals(null, h2.join());
            rs[2].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h2, ex);
            rs[2].assertNotInvoked();
        }
        try {
            assertEquals(null, h3.join());
            rs[3].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedException(h3, ex);
            rs[3].assertNotInvoked();
        }

        checkCompletedNormally(f, v1);
        checkCompletedExceptionally(g, ex);
    }}

    /**
     * runAfterEither result completes exceptionally if either source cancelled
     */
    public void testRunAfterEither_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop[] rs = new Noop[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);

        final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
        checkIncomplete(h0);
        checkIncomplete(h1);
        rs[0].assertNotInvoked();
        rs[1].assertNotInvoked();
        f.cancel(mayInterruptIfRunning);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        final CompletableFuture<Void> h2 = m.runAfterEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.runAfterEither(g, f, rs[3]);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);

        assertTrue(g.complete(v1));

        // unspecified behavior - both source completions available
        final CompletableFuture<Void> h4 = m.runAfterEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(g, f, rs[5]);
        try {
            assertNull(h4.join());
            rs[4].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h4);
            rs[4].assertNotInvoked();
        }
        try {
            assertNull(h5.join());
            rs[5].assertInvoked();
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h5);
            rs[5].assertNotInvoked();
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h0);
        checkCompletedWithWrappedCancellationException(h1);
        checkCompletedWithWrappedCancellationException(h2);
        checkCompletedWithWrappedCancellationException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertNotInvoked();
    }}

    /**
     * runAfterEither result completes exceptionally if action does
     */
    public void testRunAfterEither_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingRunnable[] rs = new FailingRunnable[6];
        for (int i = 0; i < rs.length; i++) rs[i] = new FailingRunnable(m);

        final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
        final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
        assertTrue(f.complete(v1));
        final CompletableFuture<Void> h2 = m.runAfterEither(f, g, rs[2]);
        final CompletableFuture<Void> h3 = m.runAfterEither(g, f, rs[3]);
        checkCompletedWithWrappedCFException(h0);
        checkCompletedWithWrappedCFException(h1);
        checkCompletedWithWrappedCFException(h2);
        checkCompletedWithWrappedCFException(h3);
        for (int i = 0; i < 4; i++) rs[i].assertInvoked();
        assertTrue(g.complete(v2));
        final CompletableFuture<Void> h4 = m.runAfterEither(f, g, rs[4]);
        final CompletableFuture<Void> h5 = m.runAfterEither(g, f, rs[5]);
        checkCompletedWithWrappedCFException(h4);
        checkCompletedWithWrappedCFException(h5);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        for (int i = 0; i < 6; i++) rs[i].assertInvoked();
    }}

    /**
     * thenCompose result completes normally after normal completion of source
     */
    public void testThenCompose_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFutureInc r = new CompletableFutureInc(m);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.thenCompose(f, r);
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedNormally(g, inc(v1));
        checkCompletedNormally(f, v1);
        r.assertValue(v1);
    }}

    /**
     * thenCompose result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenCompose_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
    {
        final CFException ex = new CFException();
        final CompletableFutureInc r = new CompletableFutureInc(m);
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = m.thenCompose(f, r);
        if (createIncomplete) f.completeExceptionally(ex);

        checkCompletedWithWrappedException(g, ex);
        checkCompletedExceptionally(f, ex);
        r.assertNotInvoked();
    }}

    /**
     * thenCompose result completes exceptionally if action does
     */
    public void testThenCompose_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final FailingCompletableFutureFunction r
            = new FailingCompletableFutureFunction(m);
        if (!createIncomplete) assertTrue(f.complete(v1));
        final CompletableFuture<Integer> g = m.thenCompose(f, r);
        if (createIncomplete) assertTrue(f.complete(v1));

        checkCompletedWithWrappedCFException(g);
        checkCompletedNormally(f, v1);
    }}

    /**
     * thenCompose result completes exceptionally if source cancelled
     */
    public void testThenCompose_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFutureInc r = new CompletableFutureInc(m);
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> g = m.thenCompose(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.cancel(mayInterruptIfRunning));
        }

        checkCompletedWithWrappedCancellationException(g);
        checkCancelled(f);
    }}

    // other static methods

    /**
     * allOf(no component futures) returns a future completed normally
     * with the value null
     */
    public void testAllOf_empty() throws Exception {
        CompletableFuture<Void> f = CompletableFuture.allOf();
        checkCompletedNormally(f, null);
    }

    /**
     * allOf returns a future completed normally with the value null
     * when all components complete normally
     */
    public void testAllOf_normal() throws Exception {
        for (int k = 1; k < 20; ++k) {
            CompletableFuture<Integer>[] fs
                = (CompletableFuture<Integer>[]) new CompletableFuture[k];
            for (int i = 0; i < k; ++i)
                fs[i] = new CompletableFuture<>();
            CompletableFuture<Void> f = CompletableFuture.allOf(fs);
            for (int i = 0; i < k; ++i) {
                checkIncomplete(f);
                checkIncomplete(CompletableFuture.allOf(fs));
                fs[i].complete(one);
            }
            checkCompletedNormally(f, null);
            checkCompletedNormally(CompletableFuture.allOf(fs), null);
        }
    }

    public void testAllOf_backwards() throws Exception {
        for (int k = 1; k < 20; ++k) {
            CompletableFuture<Integer>[] fs
                = (CompletableFuture<Integer>[]) new CompletableFuture[k];
            for (int i = 0; i < k; ++i)
                fs[i] = new CompletableFuture<>();
            CompletableFuture<Void> f = CompletableFuture.allOf(fs);
            for (int i = k - 1; i >= 0; i--) {
                checkIncomplete(f);
                checkIncomplete(CompletableFuture.allOf(fs));
                fs[i].complete(one);
            }
            checkCompletedNormally(f, null);
            checkCompletedNormally(CompletableFuture.allOf(fs), null);
        }
    }

    /**
     * anyOf(no component futures) returns an incomplete future
     */
    public void testAnyOf_empty() throws Exception {
        CompletableFuture<Object> f = CompletableFuture.anyOf();
        checkIncomplete(f);
    }

    /**
     * anyOf returns a future completed normally with a value when
     * a component future does
     */
    public void testAnyOf_normal() throws Exception {
        for (int k = 0; k < 10; ++k) {
            CompletableFuture[] fs = new CompletableFuture[k];
            for (int i = 0; i < k; ++i)
                fs[i] = new CompletableFuture<>();
            CompletableFuture<Object> f = CompletableFuture.anyOf(fs);
            checkIncomplete(f);
            for (int i = 0; i < k; ++i) {
                fs[i].complete(one);
                checkCompletedNormally(f, one);
                checkCompletedNormally(CompletableFuture.anyOf(fs), one);
            }
        }
    }

    /**
     * anyOf result completes exceptionally when any component does.
     */
    public void testAnyOf_exceptional() throws Exception {
        for (int k = 0; k < 10; ++k) {
            CompletableFuture[] fs = new CompletableFuture[k];
            for (int i = 0; i < k; ++i)
                fs[i] = new CompletableFuture<>();
            CompletableFuture<Object> f = CompletableFuture.anyOf(fs);
            checkIncomplete(f);
            for (int i = 0; i < k; ++i) {
                fs[i].completeExceptionally(new CFException());
                checkCompletedWithWrappedCFException(f);
                checkCompletedWithWrappedCFException(CompletableFuture.anyOf(fs));
            }
        }
    }

    /**
     * Completion methods throw NullPointerException with null arguments
     */
    public void testNPE() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = new CompletableFuture<>();
        CompletableFuture<Integer> nullFuture = (CompletableFuture<Integer>)null;
        CompletableFuture<?> h;
        ThreadExecutor exec = new ThreadExecutor();

        Runnable[] throwingActions = {
            () -> CompletableFuture.supplyAsync(null),
            () -> CompletableFuture.supplyAsync(null, exec),
            () -> CompletableFuture.supplyAsync(new IntegerSupplier(ExecutionMode.DEFAULT, 42), null),

            () -> CompletableFuture.runAsync(null),
            () -> CompletableFuture.runAsync(null, exec),
            () -> CompletableFuture.runAsync(() -> {}, null),

            () -> f.completeExceptionally(null),

            () -> f.thenApply(null),
            () -> f.thenApplyAsync(null),
            () -> f.thenApplyAsync((x) -> x, null),
            () -> f.thenApplyAsync(null, exec),

            () -> f.thenAccept(null),
            () -> f.thenAcceptAsync(null),
            () -> f.thenAcceptAsync((x) -> {} , null),
            () -> f.thenAcceptAsync(null, exec),

            () -> f.thenRun(null),
            () -> f.thenRunAsync(null),
            () -> f.thenRunAsync(() -> {} , null),
            () -> f.thenRunAsync(null, exec),

            () -> f.thenCombine(g, null),
            () -> f.thenCombineAsync(g, null),
            () -> f.thenCombineAsync(g, null, exec),
            () -> f.thenCombine(nullFuture, (x, y) -> x),
            () -> f.thenCombineAsync(nullFuture, (x, y) -> x),
            () -> f.thenCombineAsync(nullFuture, (x, y) -> x, exec),
            () -> f.thenCombineAsync(g, (x, y) -> x, null),

            () -> f.thenAcceptBoth(g, null),
            () -> f.thenAcceptBothAsync(g, null),
            () -> f.thenAcceptBothAsync(g, null, exec),
            () -> f.thenAcceptBoth(nullFuture, (x, y) -> {}),
            () -> f.thenAcceptBothAsync(nullFuture, (x, y) -> {}),
            () -> f.thenAcceptBothAsync(nullFuture, (x, y) -> {}, exec),
            () -> f.thenAcceptBothAsync(g, (x, y) -> {}, null),

            () -> f.runAfterBoth(g, null),
            () -> f.runAfterBothAsync(g, null),
            () -> f.runAfterBothAsync(g, null, exec),
            () -> f.runAfterBoth(nullFuture, () -> {}),
            () -> f.runAfterBothAsync(nullFuture, () -> {}),
            () -> f.runAfterBothAsync(nullFuture, () -> {}, exec),
            () -> f.runAfterBothAsync(g, () -> {}, null),

            () -> f.applyToEither(g, null),
            () -> f.applyToEitherAsync(g, null),
            () -> f.applyToEitherAsync(g, null, exec),
            () -> f.applyToEither(nullFuture, (x) -> x),
            () -> f.applyToEitherAsync(nullFuture, (x) -> x),
            () -> f.applyToEitherAsync(nullFuture, (x) -> x, exec),
            () -> f.applyToEitherAsync(g, (x) -> x, null),

            () -> f.acceptEither(g, null),
            () -> f.acceptEitherAsync(g, null),
            () -> f.acceptEitherAsync(g, null, exec),
            () -> f.acceptEither(nullFuture, (x) -> {}),
            () -> f.acceptEitherAsync(nullFuture, (x) -> {}),
            () -> f.acceptEitherAsync(nullFuture, (x) -> {}, exec),
            () -> f.acceptEitherAsync(g, (x) -> {}, null),

            () -> f.runAfterEither(g, null),
            () -> f.runAfterEitherAsync(g, null),
            () -> f.runAfterEitherAsync(g, null, exec),
            () -> f.runAfterEither(nullFuture, () -> {}),
            () -> f.runAfterEitherAsync(nullFuture, () -> {}),
            () -> f.runAfterEitherAsync(nullFuture, () -> {}, exec),
            () -> f.runAfterEitherAsync(g, () -> {}, null),

            () -> f.thenCompose(null),
            () -> f.thenComposeAsync(null),
            () -> f.thenComposeAsync(new CompletableFutureInc(ExecutionMode.EXECUTOR), null),
            () -> f.thenComposeAsync(null, exec),

            () -> f.exceptionally(null),

            () -> f.handle(null),

            () -> CompletableFuture.allOf((CompletableFuture<?>)null),
            () -> CompletableFuture.allOf((CompletableFuture<?>[])null),
            () -> CompletableFuture.allOf(f, null),
            () -> CompletableFuture.allOf(null, f),

            () -> CompletableFuture.anyOf((CompletableFuture<?>)null),
            () -> CompletableFuture.anyOf((CompletableFuture<?>[])null),
            () -> CompletableFuture.anyOf(f, null),
            () -> CompletableFuture.anyOf(null, f),

            () -> f.obtrudeException(null),
        };

        assertThrows(NullPointerException.class, throwingActions);
        assertEquals(0, exec.count.get());
    }

    /**
     * toCompletableFuture returns this CompletableFuture.
     */
    public void testToCompletableFuture() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        assertSame(f, f.toCompletableFuture());
    }

    //--- tests of implementation details; not part of official tck ---

    Object resultOf(CompletableFuture<?> f) {
        try {
            java.lang.reflect.Field resultField
                = CompletableFuture.class.getDeclaredField("result");
            resultField.setAccessible(true);
            return resultField.get(f);
        } catch (Throwable t) { throw new AssertionError(t); }
    }

    public void testExceptionPropagationReusesResultObject() {
        if (!testImplementationDetails) return;
        for (ExecutionMode m : ExecutionMode.values())
    {
        final CFException ex = new CFException();
        final CompletableFuture<Integer> v42 = CompletableFuture.completedFuture(42);
        final CompletableFuture<Integer> incomplete = new CompletableFuture<>();

        List<Function<CompletableFuture<Integer>, CompletableFuture<?>>> dependentFactories
            = new ArrayList<>();

        dependentFactories.add((y) -> m.thenRun(y, new Noop(m)));
        dependentFactories.add((y) -> m.thenAccept(y, new NoopConsumer(m)));
        dependentFactories.add((y) -> m.thenApply(y, new IncFunction(m)));

        dependentFactories.add((y) -> m.runAfterEither(y, incomplete, new Noop(m)));
        dependentFactories.add((y) -> m.acceptEither(y, incomplete, new NoopConsumer(m)));
        dependentFactories.add((y) -> m.applyToEither(y, incomplete, new IncFunction(m)));

        dependentFactories.add((y) -> m.runAfterBoth(y, v42, new Noop(m)));
        dependentFactories.add((y) -> m.thenAcceptBoth(y, v42, new SubtractAction(m)));
        dependentFactories.add((y) -> m.thenCombine(y, v42, new SubtractFunction(m)));

        dependentFactories.add((y) -> m.whenComplete(y, (Integer x, Throwable t) -> {}));

        dependentFactories.add((y) -> m.thenCompose(y, new CompletableFutureInc(m)));

        for (Function<CompletableFuture<Integer>, CompletableFuture<?>>
                 dependentFactory : dependentFactories) {
            CompletableFuture<Integer> f = new CompletableFuture<>();
            f.completeExceptionally(ex);
            CompletableFuture<Integer> src = m.thenApply(f, new IncFunction(m));
            checkCompletedWithWrappedException(src, ex);
            CompletableFuture<?> dep = dependentFactory.apply(src);
            checkCompletedWithWrappedException(dep, ex);
            assertSame(resultOf(src), resultOf(dep));
        }

        for (Function<CompletableFuture<Integer>, CompletableFuture<?>>
                 dependentFactory : dependentFactories) {
            CompletableFuture<Integer> f = new CompletableFuture<>();
            CompletableFuture<Integer> src = m.thenApply(f, new IncFunction(m));
            CompletableFuture<?> dep = dependentFactory.apply(src);
            f.completeExceptionally(ex);
            checkCompletedWithWrappedException(src, ex);
            checkCompletedWithWrappedException(dep, ex);
            assertSame(resultOf(src), resultOf(dep));
        }

        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Function<CompletableFuture<Integer>, CompletableFuture<?>>
                 dependentFactory : dependentFactories) {
            CompletableFuture<Integer> f = new CompletableFuture<>();
            f.cancel(mayInterruptIfRunning);
            checkCancelled(f);
            CompletableFuture<Integer> src = m.thenApply(f, new IncFunction(m));
            checkCompletedWithWrappedCancellationException(src);
            CompletableFuture<?> dep = dependentFactory.apply(src);
            checkCompletedWithWrappedCancellationException(dep);
            assertSame(resultOf(src), resultOf(dep));
        }

        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Function<CompletableFuture<Integer>, CompletableFuture<?>>
                 dependentFactory : dependentFactories) {
            CompletableFuture<Integer> f = new CompletableFuture<>();
            CompletableFuture<Integer> src = m.thenApply(f, new IncFunction(m));
            CompletableFuture<?> dep = dependentFactory.apply(src);
            f.cancel(mayInterruptIfRunning);
            checkCancelled(f);
            checkCompletedWithWrappedCancellationException(src);
            checkCompletedWithWrappedCancellationException(dep);
            assertSame(resultOf(src), resultOf(dep));
        }
    }}

//     public void testRunAfterEither_resultDeterminedAtTimeOfCreation() {
//         for (ExecutionMode m : ExecutionMode.values())
//         for (boolean mayInterruptIfRunning : new boolean[] { true, false })
//         for (Integer v1 : new Integer[] { 1, null })
//     {
//         final CompletableFuture<Integer> f = new CompletableFuture<>();
//         final CompletableFuture<Integer> g = new CompletableFuture<>();
//         final Noop[] rs = new Noop[2];
//         for (int i = 0; i < rs.length; i++) rs[i] = new Noop(m);
//         f.complete(v1);
//         final CompletableFuture<Void> h0 = m.runAfterEither(f, g, rs[0]);
//         final CompletableFuture<Void> h1 = m.runAfterEither(g, f, rs[1]);
//         assertTrue(g.cancel(mayInterruptIfRunning));
//         checkCompletedNormally(h0, null);
//         checkCompletedNormally(h1, null);
//         for (Noop r : rs) r.assertInvoked();
//     }}

}
