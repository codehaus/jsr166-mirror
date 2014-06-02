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

    void checkCompletedWithWrappedCFException(CompletableFuture<?> f,
                                              CFException ex) {
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
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        f.complete(one);
        checkCompletedNormally(f, one);
    }

    /**
     * completeExceptionally completes exceptionally, as indicated by
     * methods isDone, isCancelled, join, get, and getNow
     */
    public void testCompleteExceptionally() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(f);
    }

    /**
     * cancel completes exceptionally and reports cancelled, as indicated by
     * methods isDone, isCancelled, join, get, and getNow
     */
    public void testCancel() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        assertTrue(f.cancel(true));
        checkCancelled(f);
    }

    /**
     * obtrudeValue forces completion with given value
     */
    public void testObtrudeValue() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        f.complete(one);
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
        CompletableFuture<Integer> f = new CompletableFuture<>();
        checkIncomplete(f);
        f.complete(one);
        checkCompletedNormally(f, one);
        f.obtrudeException(new CFException());
        checkCompletedWithWrappedCFException(f);
        f = new CompletableFuture<>();
        f.obtrudeException(new CFException());
        checkCompletedWithWrappedCFException(f);
        f = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        f.obtrudeValue(four);
        checkCompletedNormally(f, four);
        f.obtrudeException(new CFException());
        checkCompletedWithWrappedCFException(f);
    }

    /**
     * getNumberOfDependents returns number of dependent tasks
     */
    public void testGetNumberOfDependents() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        assertEquals(0, f.getNumberOfDependents());
        CompletableFuture g = f.thenRun(new Noop());
        assertEquals(1, f.getNumberOfDependents());
        assertEquals(0, g.getNumberOfDependents());
        CompletableFuture h = f.thenRun(new Noop());
        assertEquals(2, f.getNumberOfDependents());
        f.complete(1);
        checkCompletedNormally(g, null);
        assertEquals(0, f.getNumberOfDependents());
        assertEquals(0, g.getNumberOfDependents());
    }

    /**
     * toString indicates current completion state
     */
    public void testToString() {
        CompletableFuture<String> f;

        f = new CompletableFuture<String>();
        assertTrue(f.toString().contains("[Not completed]"));

        f.complete("foo");
        assertTrue(f.toString().contains("[Completed normally]"));

        f = new CompletableFuture<String>();
        f.completeExceptionally(new IndexOutOfBoundsException());
        assertTrue(f.toString().contains("[Completed exceptionally]"));
    }

    /**
     * completedFuture returns a completed CompletableFuture with given value
     */
    public void testCompletedFuture() {
        CompletableFuture<String> f = CompletableFuture.completedFuture("test");
        checkCompletedNormally(f, "test");
    }

    // Choose non-commutative actions for better coverage

    // A non-commutative function that handles and produces null values as well.
    static Integer subtract(Integer x, Integer y) {
        return (x == null && y == null) ? null :
            ((x == null) ? 42 : x.intValue())
            - ((y == null) ? 99 : y.intValue());
    }

    // A function that handles and produces null values as well.
    static Integer inc(Integer x) {
        return (x == null) ? null : x + 1;
    }

    static final Supplier<Integer> supplyOne =
        () -> Integer.valueOf(1);
    static final Function<Integer, Integer> inc =
        (Integer x) -> Integer.valueOf(x.intValue() + 1);
    static final BiFunction<Integer, Integer, Integer> subtract =
        (Integer x, Integer y) -> subtract(x, y);
    static final class IncAction implements Consumer<Integer> {
        int invocationCount = 0;
        Integer value;
        public void accept(Integer x) {
            invocationCount++;
            value = inc(x);
        }
    }
    static final class IncFunction implements Function<Integer,Integer> {
        int invocationCount = 0;
        Integer value;
        public Integer apply(Integer x) {
            invocationCount++;
            return value = inc(x);
        }
    }
    static final class SubtractAction implements BiConsumer<Integer, Integer> {
        int invocationCount = 0;
        Integer value;
        // Check this action was invoked exactly once when result is computed.
        public void accept(Integer x, Integer y) {
            invocationCount++;
            value = subtract(x, y);
        }
    }
    static final class SubtractFunction implements BiFunction<Integer, Integer, Integer> {
        int invocationCount = 0;
        Integer value;
        // Check this action was invoked exactly once when result is computed.
        public Integer apply(Integer x, Integer y) {
            invocationCount++;
            return value = subtract(x, y);
        }
    }
    static final class Noop implements Runnable {
        int invocationCount = 0;
        public void run() {
            invocationCount++;
        }
    }

    static final class FailingSupplier implements Supplier<Integer> {
        int invocationCount = 0;
        public Integer get() {
            invocationCount++;
            throw new CFException();
        }
    }
    static final class FailingConsumer implements Consumer<Integer> {
        int invocationCount = 0;
        public void accept(Integer x) {
            invocationCount++;
            throw new CFException();
        }
    }
    static final class FailingBiConsumer implements BiConsumer<Integer, Integer> {
        int invocationCount = 0;
        public void accept(Integer x, Integer y) {
            invocationCount++;
            throw new CFException();
        }
    }
    static final class FailingFunction implements Function<Integer, Integer> {
        int invocationCount = 0;
        public Integer apply(Integer x) {
            invocationCount++;
            throw new CFException();
        }
    }
    static final class FailingBiFunction implements BiFunction<Integer, Integer, Integer> {
        int invocationCount = 0;
        public Integer apply(Integer x, Integer y) {
            invocationCount++;
            throw new CFException();
        }
    }
    static final class FailingRunnable implements Runnable {
        int invocationCount = 0;
        public void run() {
            invocationCount++;
            throw new CFException();
        }
    }

    static final class CompletableFutureInc
        implements Function<Integer, CompletableFuture<Integer>> {
        int invocationCount = 0;
        public CompletableFuture<Integer> apply(Integer x) {
            invocationCount++;
            CompletableFuture<Integer> f = new CompletableFuture<>();
            f.complete(inc(x));
            return f;
        }
    }

    static final class FailingCompletableFutureFunction
        implements Function<Integer, CompletableFuture<Integer>> {
        int invocationCount = 0;
        public CompletableFuture<Integer> apply(Integer x) {
            invocationCount++;
            throw new CFException();
        }
    }

    // Used for explicit executor tests
    static final class ThreadExecutor implements Executor {
        AtomicInteger count = new AtomicInteger(0);

        public void execute(Runnable r) {
            count.getAndIncrement();
            new Thread(r).start();
        }
    }

    /**
     * Permits the testing of parallel code for the 3 different
     * execution modes without repeating all the testing code.
     */
    enum ExecutionMode {
        DEFAULT {
            public void checkExecutionMode() {
                assertNull(ForkJoinTask.getPool());
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
                //TODO
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
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Integer> g = f.exceptionally
            ((Throwable t) -> {
                // Should not be called
                a.getAndIncrement();
                throw new AssertionError();
            });
        if (createIncomplete) f.complete(v1);

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
                threadAssertSame(t, ex1);
                a.getAndIncrement();
                throw ex2;
            });
        if (createIncomplete) f.completeExceptionally(ex1);

        checkCompletedWithWrappedCFException(g, ex2);
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
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer x, Throwable t) -> {
                threadAssertSame(x, v1);
                threadAssertNull(t);
                a.getAndIncrement();
                return inc(v1);
            });
        if (createIncomplete) f.complete(v1);

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
                threadAssertNull(x);
                threadAssertSame(t, ex);
                a.getAndIncrement();
                return v1;
            });
        if (createIncomplete) f.completeExceptionally(ex);

        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCFException(f, ex);
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
                threadAssertNull(x);
                threadAssertSame(ex1, t);
                a.getAndIncrement();
                throw ex2;
            });
        if (createIncomplete) f.completeExceptionally(ex1);

        checkCompletedWithWrappedCFException(g, ex2);
        checkCompletedWithWrappedCFException(f, ex1);
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
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Integer> g = m.handle
            (f,
             (Integer x, Throwable t) -> {
                threadAssertSame(x, v1);
                threadAssertNull(t);
                a.getAndIncrement();
                throw ex;
            });
        if (createIncomplete) f.complete(v1);

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedNormally(f, v1);
        assertEquals(1, a.get());
    }}

    /**
     * runAsync completes after running Runnable
     */
    public void testRunAsync() {
        Noop r = new Noop();
        CompletableFuture<Void> f = CompletableFuture.runAsync(r);
        assertNull(f.join());
        assertEquals(1, r.invocationCount);
        checkCompletedNormally(f, null);
    }

    /**
     * runAsync with executor completes after running Runnable
     */
    public void testRunAsync2() {
        Noop r = new Noop();
        ThreadExecutor exec = new ThreadExecutor();
        CompletableFuture<Void> f = CompletableFuture.runAsync(r, exec);
        assertNull(f.join());
        assertEquals(1, r.invocationCount);
        checkCompletedNormally(f, null);
        assertEquals(1, exec.count.get());
    }

    /**
     * failing runAsync completes exceptionally after running Runnable
     */
    public void testRunAsync3() {
        FailingRunnable r = new FailingRunnable();
        CompletableFuture<Void> f = CompletableFuture.runAsync(r);
        checkCompletedWithWrappedCFException(f);
        assertEquals(1, r.invocationCount);
    }

    /**
     * supplyAsync completes with result of supplier
     */
    public void testSupplyAsync() {
        CompletableFuture<Integer> f;
        f = CompletableFuture.supplyAsync(supplyOne);
        assertEquals(f.join(), one);
        checkCompletedNormally(f, one);
    }

    /**
     * supplyAsync with executor completes with result of supplier
     */
    public void testSupplyAsync2() {
        CompletableFuture<Integer> f;
        f = CompletableFuture.supplyAsync(supplyOne, new ThreadExecutor());
        assertEquals(f.join(), one);
        checkCompletedNormally(f, one);
    }

    /**
     * Failing supplyAsync completes exceptionally
     */
    public void testSupplyAsync3() {
        FailingSupplier r = new FailingSupplier();
        CompletableFuture<Integer> f = CompletableFuture.supplyAsync(r);
        checkCompletedWithWrappedCFException(f);
        assertEquals(1, r.invocationCount);
    }

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
        final Noop r = new Noop();
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Void> g = m.thenRun(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.complete(v1);
        }

        checkCompletedNormally(g, null);
        checkCompletedNormally(f, v1);
        assertEquals(1, r.invocationCount);
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
        final Noop r = new Noop();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Void> g = m.thenRun(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.completeExceptionally(ex);
        }

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(0, r.invocationCount);
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
        final Noop r = new Noop();
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Void> g = f.thenRun(r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.cancel(mayInterruptIfRunning));
        }

        checkCompletedWithWrappedCancellationException(g);
        checkCancelled(f);
        assertEquals(0, r.invocationCount);
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
        final FailingRunnable r = new FailingRunnable();
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Void> g = f.thenRun(r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.complete(v1);
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
        final IncFunction r = new IncFunction();
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Integer> g = m.thenApply(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.complete(v1);
        }

        checkCompletedNormally(g, inc(v1));
        checkCompletedNormally(f, v1);
        assertEquals(1, r.invocationCount);
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
        final IncFunction r = new IncFunction();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = m.thenApply(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.completeExceptionally(ex);
        }

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(0, r.invocationCount);
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
        final IncFunction r = new IncFunction();
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> g = f.thenApply(r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.cancel(mayInterruptIfRunning));
        }

        checkCompletedWithWrappedCancellationException(g);
        checkCancelled(f);
        assertEquals(0, r.invocationCount);
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
        final FailingFunction r = new FailingFunction();
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Integer> g = f.thenApply(r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.complete(v1);
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
        final IncAction r = new IncAction();
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Void> g = m.thenAccept(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.complete(v1);
        }

        checkCompletedNormally(g, null);
        checkCompletedNormally(f, v1);
        assertEquals(1, r.invocationCount);
        assertEquals(inc(v1), r.value);
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
        final IncAction r = new IncAction();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Void> g = m.thenAccept(f, r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.completeExceptionally(ex);
        }

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(0, r.invocationCount);
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
        final FailingConsumer r = new FailingConsumer();
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Void> g = f.thenAccept(r);
        if (createIncomplete) {
            checkIncomplete(g);
            f.complete(v1);
        }

        checkCompletedWithWrappedCFException(g);
        checkCompletedNormally(f, v1);
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
        final IncAction r = new IncAction();
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Void> g = f.thenAccept(r);
        if (createIncomplete) {
            checkIncomplete(g);
            assertTrue(f.cancel(mayInterruptIfRunning));
        }

        checkCompletedWithWrappedCancellationException(g);
        checkCancelled(f);
        assertEquals(0, r.invocationCount);
    }}

    /**
     * thenCombine result completes normally after normal completion
     * of sources
     */
    public void testThenCombine_normalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();

        if (fFirst) f.complete(v1); else g.complete(v2);
        if (!createIncomplete)
            if (!fFirst) f.complete(v1); else g.complete(v2);
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);
        if (createIncomplete) {
            checkIncomplete(h);
            assertEquals(0, r.invocationCount);
            if (!fFirst) f.complete(v1); else g.complete(v2);
        }

        checkCompletedNormally(h, subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        assertEquals(1, r.invocationCount);
    }}

    /**
     * thenCombine result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenCombine_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean createIncomplete : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final CFException ex = new CFException();
        final SubtractFunction r = new SubtractFunction();

        (fFirst ? f : g).complete(v1);
        if (!createIncomplete)
            (!fFirst ? f : g).completeExceptionally(ex);
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);
        if (createIncomplete) {
            checkIncomplete(h);
            (!fFirst ? f : g).completeExceptionally(ex);
        }

        checkCompletedWithWrappedCFException(h, ex);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(fFirst ? f : g, v1);
        checkCompletedWithWrappedCFException(!fFirst ? f : g, ex);
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
        final FailingBiFunction r = new FailingBiFunction();
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        if (fFirst) {
            f.complete(v1);
            g.complete(v2);
        } else {
            g.complete(v2);
            f.complete(v1);
        }

        checkCompletedWithWrappedCFException(h);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * thenCombine result completes exceptionally if either source cancelled
     */
    public void testThenCombine_sourceCancelled() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (boolean createIncomplete : new boolean[] { true, false })
        for (boolean fFirst : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();

        (fFirst ? f : g).complete(v1);
        if (!createIncomplete)
            assertTrue((!fFirst ? f : g).cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);
        if (createIncomplete) {
            checkIncomplete(h);
            assertTrue((!fFirst ? f : g).cancel(mayInterruptIfRunning));
        }

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(!fFirst ? f : g);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(fFirst ? f : g, v1);
    }}

    /**
     * thenAcceptBoth result completes normally after normal
     * completion of sources
     */
    public void testThenAcceptBoth_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        f.complete(v1);
        checkIncomplete(h);
        assertEquals(0, r.invocationCount);
        g.complete(v2);

        checkCompletedNormally(h, null);
        assertEquals(subtract(v1, v2), r.value);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testThenAcceptBoth_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        g.complete(v2);
        checkIncomplete(h);
        assertEquals(0, r.invocationCount);
        f.complete(v1);

        checkCompletedNormally(h, null);
        assertEquals(subtract(v1, v2), r.value);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testThenAcceptBoth_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();

        g.complete(v2);
        f.complete(v1);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedNormally(h, null);
        assertEquals(subtract(v1, v2), r.value);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testThenAcceptBoth_normalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();

        f.complete(v1);
        g.complete(v2);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedNormally(h, null);
        assertEquals(subtract(v1, v2), r.value);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * thenAcceptBoth result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenAcceptBoth_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        checkIncomplete(h);
        g.complete(v1);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
    }}

    public void testThenAcceptBoth_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
    }}

    public void testThenAcceptBoth_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        f.complete(v1);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
    }}

    public void testThenAcceptBoth_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        g.complete(v1);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
    }}

    /**
     * thenAcceptBoth result completes exceptionally if action does
     */
    public void testThenAcceptBoth_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingBiConsumer r = new FailingBiConsumer();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        f.complete(v1);
        checkIncomplete(h);
        g.complete(v2);

        checkCompletedWithWrappedCFException(h);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testThenAcceptBoth_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingBiConsumer r = new FailingBiConsumer();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        g.complete(v2);
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCFException(h);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * thenAcceptBoth result completes exceptionally if either source cancelled
     */
    public void testThenAcceptBoth_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        g.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
    }}

    public void testThenAcceptBoth_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
    }}

    public void testThenAcceptBoth_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
    }}

    public void testThenAcceptBoth_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
    }}

    /**
     * runAfterBoth result completes normally after normal
     * completion of sources
     */
    public void testRunAfterBoth_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        f.complete(v1);
        checkIncomplete(h);
        assertEquals(0, r.invocationCount);
        g.complete(v2);

        checkCompletedNormally(h, null);
        assertEquals(1, r.invocationCount);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testRunAfterBoth_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        g.complete(v2);
        checkIncomplete(h);
        assertEquals(0, r.invocationCount);
        f.complete(v1);

        checkCompletedNormally(h, null);
        assertEquals(1, r.invocationCount);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testRunAfterBoth_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        g.complete(v2);
        f.complete(v1);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedNormally(h, null);
        assertEquals(1, r.invocationCount);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testRunAfterBoth_normalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        f.complete(v1);
        g.complete(v2);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedNormally(h, null);
        assertEquals(1, r.invocationCount);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * runAfterBoth result completes exceptionally after exceptional
     * completion of either source
     */
    public void testRunAfterBoth_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        checkIncomplete(h);
        g.complete(v1);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
    }}

    public void testRunAfterBoth_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
    }}

    public void testRunAfterBoth_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        f.complete(v1);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
    }}

    public void testRunAfterBoth_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        g.complete(v1);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
    }}

    /**
     * runAfterBoth result completes exceptionally if action does
     */
    public void testRunAfterBoth_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingRunnable r = new FailingRunnable();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        f.complete(v1);
        checkIncomplete(h);
        g.complete(v2);

        checkCompletedWithWrappedCFException(h);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testRunAfterBoth_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingRunnable r = new FailingRunnable();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        g.complete(v2);
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCFException(h);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * runAfterBoth result completes exceptionally if either source cancelled
     */
    public void testRunAfterBoth_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        g.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
    }}

    public void testRunAfterBoth_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
    }}

    public void testRunAfterBoth_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
    }}

    public void testRunAfterBoth_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
    }}

    /**
     * applyToEither result completes normally after normal completion
     * of either source
     */
    public void testApplyToEither_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        f.complete(v1);
        checkCompletedNormally(h, inc(v1));
        g.complete(v2);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h, inc(v1));
    }}

    public void testApplyToEither_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        g.complete(v2);
        checkCompletedNormally(h, inc(v2));
        f.complete(v1);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h, inc(v2));
        }}

    public void testApplyToEither_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();

        f.complete(v1);
        g.complete(v2);
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);

        // unspecified behavior
        assertTrue(Objects.equals(h.join(), inc(v1)) ||
                   Objects.equals(h.join(), inc(v2)));
        assertEquals(1, r.invocationCount);
    }}

    /**
     * applyToEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testApplyToEither_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        g.complete(v1);

        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedWithWrappedCFException(h, ex);
    }}

    public void testApplyToEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        f.complete(v1);

        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedWithWrappedCFException(h, ex);
    }}

    public void testApplyToEither_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        f.complete(v1);
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertEquals(inc(v1), h.join());
            assertEquals(1, r.invocationCount);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(0, r.invocationCount);
        }

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedNormally(f, v1);
    }}

    public void testApplyToEither_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        g.complete(v1);
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertEquals(inc(v1), h.join());
            assertEquals(1, r.invocationCount);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(0, r.invocationCount);
        }

        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedNormally(g, v1);
    }}

    /**
     * applyToEither result completes exceptionally if action does
     */
    public void testApplyToEither_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingFunction r = new FailingFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        f.complete(v1);
        checkCompletedWithWrappedCFException(h);
        g.complete(v2);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testApplyToEither_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingFunction r = new FailingFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        g.complete(v2);
        checkCompletedWithWrappedCFException(h);
        f.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * applyToEither result completes exceptionally if either source cancelled
     */
    public void testApplyToEither_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        g.complete(v1);

        checkCancelled(f);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h);
    }}

    public void testApplyToEither_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        f.complete(v1);

        checkCancelled(g);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCancellationException(h);
    }}

    public void testApplyToEither_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertEquals(inc(v1), h.join());
            assertEquals(1, r.invocationCount);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(0, r.invocationCount);
        }

        checkCancelled(g);
        checkCompletedNormally(f, v1);
    }}

    public void testApplyToEither_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertEquals(inc(v1), h.join());
            assertEquals(1, r.invocationCount);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(0, r.invocationCount);
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
    }}

    /**
     * acceptEither result completes normally after normal completion
     * of either source
     */
    public void testAcceptEither_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        f.complete(v1);
        checkCompletedNormally(h, null);
        assertEquals(inc(v1), r.value);
        g.complete(v2);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h, null);
    }}

    public void testAcceptEither_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        g.complete(v2);
        checkCompletedNormally(h, null);
        assertEquals(inc(v2), r.value);
        f.complete(v1);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h, null);
    }}

    public void testAcceptEither_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();

        f.complete(v1);
        g.complete(v2);
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        checkCompletedNormally(h, null);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);

        // unspecified behavior
        assertTrue(Objects.equals(r.value, inc(v1)) ||
                   Objects.equals(r.value, inc(v2)));
    }}

    /**
     * acceptEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testAcceptEither_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        g.complete(v1);

        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedWithWrappedCFException(h, ex);
    }}

    public void testAcceptEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        f.complete(v1);

        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedWithWrappedCFException(h, ex);
    }}

    public void testAcceptEither_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        f.complete(v1);
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertNull(h.join());
            assertEquals(1, r.invocationCount);
            assertEquals(inc(v1), r.value);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(0, r.invocationCount);
        }

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedNormally(f, v1);
    }}

    public void testAcceptEither_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        g.complete(v1);
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertNull(h.join());
            assertEquals(1, r.invocationCount);
            assertEquals(inc(v1), r.value);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(0, r.invocationCount);
        }

        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedNormally(g, v1);
    }}

    /**
     * acceptEither result completes exceptionally if action does
     */
    public void testAcceptEither_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingConsumer r = new FailingConsumer();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        f.complete(v1);
        checkCompletedWithWrappedCFException(h);
        g.complete(v2);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testAcceptEither_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingConsumer r = new FailingConsumer();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        g.complete(v2);
        checkCompletedWithWrappedCFException(h);
        f.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * acceptEither result completes exceptionally if either source cancelled
     */
    public void testAcceptEither_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        g.complete(v1);

        checkCancelled(f);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h);
    }}

    public void testAcceptEither_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        f.complete(v1);

        checkCancelled(g);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCancellationException(h);
    }}

    public void testAcceptEither_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertNull(h.join());
            assertEquals(1, r.invocationCount);
            assertEquals(inc(v1), r.value);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(0, r.invocationCount);
        }

        checkCancelled(g);
        checkCompletedNormally(f, v1);
    }}

    public void testAcceptEither_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertNull(h.join());
            assertEquals(1, r.invocationCount);
            assertEquals(inc(v1), r.value);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(0, r.invocationCount);
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
    }}

    /**
     * runAfterEither result completes normally after normal completion
     * of either source
     */
    public void testRunAfterEither_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        f.complete(v1);
        checkCompletedNormally(h, null);
        assertEquals(1, r.invocationCount);
        g.complete(v2);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h, null);
        assertEquals(1, r.invocationCount);
    }}

    public void testRunAfterEither_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        g.complete(v2);
        checkCompletedNormally(h, null);
        assertEquals(1, r.invocationCount);
        f.complete(v1);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h, null);
        assertEquals(1, r.invocationCount);
        }}

    public void testRunAfterEither_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        f.complete(v1);
        g.complete(v2);
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        checkCompletedNormally(h, null);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        assertEquals(1, r.invocationCount);
    }}

    /**
     * runAfterEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testRunAfterEither_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        g.complete(v1);

        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedWithWrappedCFException(h, ex);
    }}

    public void testRunAfterEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        f.complete(v1);

        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedWithWrappedCFException(h, ex);
    }}

    public void testRunAfterEither_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        f.complete(v1);
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertNull(h.join());
            assertEquals(1, r.invocationCount);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(0, r.invocationCount);
        }

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedNormally(f, v1);
    }}

    public void testRunAfterEither_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        g.complete(v1);
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertNull(h.join());
            assertEquals(1, r.invocationCount);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(0, r.invocationCount);
        }

        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedNormally(g, v1);
    }}

    /**
     * runAfterEither result completes exceptionally if action does
     */
    public void testRunAfterEither_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingRunnable r = new FailingRunnable();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        f.complete(v1);
        checkCompletedWithWrappedCFException(h);
        g.complete(v2);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    public void testRunAfterEither_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingRunnable r = new FailingRunnable();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        g.complete(v2);
        checkCompletedWithWrappedCFException(h);
        f.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
    }}

    /**
     * runAfterEither result completes exceptionally if either source cancelled
     */
    public void testRunAfterEither_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        g.complete(v1);

        checkCancelled(f);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h);
    }}

    public void testRunAfterEither_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        f.complete(v1);

        checkCancelled(g);
        assertEquals(0, r.invocationCount);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCancellationException(h);
    }}

    public void testRunAfterEither_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertNull(h.join());
            assertEquals(1, r.invocationCount);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(0, r.invocationCount);
        }

        checkCancelled(g);
        checkCompletedNormally(f, v1);
    }}

    public void testRunAfterEither_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null })
    {
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertNull(h.join());
            assertEquals(1, r.invocationCount);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(0, r.invocationCount);
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
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
        final CompletableFutureInc r = new CompletableFutureInc();
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Integer> g = f.thenCompose(r);
        if (createIncomplete) f.complete(v1);

        checkCompletedNormally(g, inc(v1));
        checkCompletedNormally(f, v1);
        assertEquals(1, r.invocationCount);
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
        final CompletableFutureInc r = new CompletableFutureInc();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        if (!createIncomplete) f.completeExceptionally(ex);
        final CompletableFuture<Integer> g = f.thenCompose(r);
        if (createIncomplete) f.completeExceptionally(ex);

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(0, r.invocationCount);
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
            = new FailingCompletableFutureFunction();
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Integer> g = f.thenCompose(r);
        if (createIncomplete) f.complete(v1);

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
        final CompletableFutureInc r = new CompletableFutureInc();
        if (!createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));
        final CompletableFuture<Integer> g = f.thenCompose(r);
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
            CompletableFuture<Integer>[] fs = (CompletableFuture<Integer>[]) new CompletableFuture[k];
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
            () -> CompletableFuture.supplyAsync(supplyOne, null),

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
            () -> f.thenComposeAsync(new CompletableFutureInc(), null),
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
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer x, Throwable t) -> {
                threadAssertSame(x, v1);
                threadAssertNull(t);
                a.getAndIncrement();
            });
        if (createIncomplete) f.complete(v1);

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
                threadAssertNull(x);
                threadAssertSame(t, ex);
                a.getAndIncrement();
            });
        if (createIncomplete) f.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedWithWrappedCFException(g, ex);
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
                threadAssertNull(x);
                threadAssertTrue(t instanceof CancellationException);
                a.getAndIncrement();
            });
        if (createIncomplete) assertTrue(f.cancel(mayInterruptIfRunning));

        //try { g.join(); } catch (Throwable t) { throw new Error(t); }
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
        if (!createIncomplete) f.complete(v1);
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer x, Throwable t) -> {
                threadAssertSame(x, v1);
                threadAssertNull(t);
                a.getAndIncrement();
                throw ex;
            });
        if (createIncomplete) f.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCFException(g, ex);
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
                threadAssertSame(t, ex1);
                threadAssertNull(x);
                a.getAndIncrement();
                throw ex2;
            });
        if (createIncomplete) f.completeExceptionally(ex1);

        checkCompletedWithWrappedCFException(f, ex1);
        checkCompletedWithWrappedCFException(g, ex1);
        assertEquals(1, a.get());
    }}

}
