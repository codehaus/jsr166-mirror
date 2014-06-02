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
        assertEquals(f.getNumberOfDependents(), 0);
        CompletableFuture g = f.thenRun(new Noop());
        assertEquals(f.getNumberOfDependents(), 1);
        assertEquals(g.getNumberOfDependents(), 0);
        CompletableFuture h = f.thenRun(new Noop());
        assertEquals(f.getNumberOfDependents(), 2);
        f.complete(1);
        checkCompletedNormally(g, null);
        assertEquals(f.getNumberOfDependents(), 0);
        assertEquals(g.getNumberOfDependents(), 0);
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
        public boolean ran() { return invocationCount == 1; }
        public void accept(Integer x) {
            invocationCount++;
            value = inc(x);
        }
    }
    static final class IncFunction implements Function<Integer,Integer> {
        int invocationCount = 0;
        Integer value;
        public boolean ran() { return invocationCount == 1; }
        public Integer apply(Integer x) {
            invocationCount++;
            return value = inc(x);
        }
    }
    static final class SubtractAction implements BiConsumer<Integer, Integer> {
        int invocationCount = 0;
        Integer value;
        // Check this action was invoked exactly once when result is computed.
        public boolean ran() { return invocationCount == 1; }
        public void accept(Integer x, Integer y) {
            invocationCount++;
            value = subtract(x, y);
        }
    }
    static final class SubtractFunction implements BiFunction<Integer, Integer, Integer> {
        int invocationCount = 0;
        Integer value;
        // Check this action was invoked exactly once when result is computed.
        public boolean ran() { return invocationCount == 1; }
        public Integer apply(Integer x, Integer y) {
            invocationCount++;
            return value = subtract(x, y);
        }
    }
    static final class Noop implements Runnable {
        int invocationCount = 0;
        boolean ran;
        public void run() {
            invocationCount++;
            ran = true;
        }
    }

    static final class FailingSupplier implements Supplier<Integer> {
        boolean ran;
        public Integer get() { ran = true; throw new CFException(); }
    }
    static final class FailingConsumer implements Consumer<Integer> {
        boolean ran;
        public void accept(Integer x) { ran = true; throw new CFException(); }
    }
    static final class FailingBiConsumer implements BiConsumer<Integer, Integer> {
        boolean ran;
        public void accept(Integer x, Integer y) { ran = true; throw new CFException(); }
    }
    static final class FailingFunction implements Function<Integer, Integer> {
        boolean ran;
        public Integer apply(Integer x) { ran = true; throw new CFException(); }
    }
    static final class FailingBiFunction implements BiFunction<Integer, Integer, Integer> {
        boolean ran;
        public Integer apply(Integer x, Integer y) { ran = true; throw new CFException(); }
    }
    static final class FailingNoop implements Runnable {
        boolean ran;
        public void run() { ran = true; throw new CFException(); }
    }

    static final class CompletableFutureInc
        implements Function<Integer, CompletableFuture<Integer>> {
        boolean ran;
        public CompletableFuture<Integer> apply(Integer x) {
            ran = true;
            CompletableFuture<Integer> f = new CompletableFuture<>();
            f.complete(Integer.valueOf(x.intValue() + 1));
            return f;
        }
    }

    static final class FailingCompletableFutureFunction
        implements Function<Integer, CompletableFuture<Integer>> {
        boolean ran;
        public CompletableFuture<Integer> apply(Integer x) {
            ran = true; throw new CFException();
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

    static final class ExceptionToInteger implements Function<Throwable, Integer> {
        public Integer apply(Throwable x) { return Integer.valueOf(3); }
    }

    static final class IntegerHandler implements BiFunction<Integer, Throwable, Integer> {
        boolean ran;
        public Integer apply(Integer x, Throwable t) {
            ran = true;
            return (t == null) ? two : three;
        }
    }

    /**
     * Permits the testing of parallel code for the 3 different
     * execution modes without repeating all the testing code.
     */
    enum ExecutionMode {
        DEFAULT {
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
            public <T,U> CompletableFuture<U> applyToEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Function<? super T,U> a) {
                return f.applyToEither(g, a);
            }
            public <T> CompletableFuture<Void> acceptEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Consumer<? super T> a) {
                return f.acceptEither(g, a);
            }
            public <T> CompletableFuture<Void> runAfterEither
                (CompletableFuture<T> f,
                 CompletionStage<?> g,
                 java.lang.Runnable a) {
                return f.runAfterEither(g, a);
            }
            public <T,U> CompletableFuture<U> thenCompose
                (CompletableFuture<T> f,
                 Function<? super T,? extends CompletionStage<U>> a) {
                return f.thenCompose(a);
            }
            public <T> CompletableFuture<T> whenComplete
                (CompletableFuture<T> f,
                 BiConsumer<? super T,? super Throwable> a) {
                return f.whenComplete(a);
            }
        },

//             /** Experimental way to do more testing */
//         REVERSE_DEFAULT {
//             public <T,U> CompletableFuture<Void> runAfterBoth
//                 (CompletableFuture<T> f, CompletableFuture<U> g, Runnable a) {
//                 return g.runAfterBoth(f, a);
//             }
//             public <T,U> CompletableFuture<Void> thenAcceptBoth
//                 (CompletableFuture<T> f,
//                  CompletionStage<? extends U> g,
//                  BiConsumer<? super T,? super U> a) {
//                 return DEFAULT.thenAcceptBoth(f, g, a);
//             }
//         },

        DEFAULT_ASYNC {
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
            public <T,U> CompletableFuture<U> applyToEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Function<? super T,U> a) {
                return f.applyToEitherAsync(g, a);
            }
            public <T> CompletableFuture<Void> acceptEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Consumer<? super T> a) {
                return f.acceptEitherAsync(g, a);
            }
            public <T> CompletableFuture<Void> runAfterEither
                (CompletableFuture<T> f,
                 CompletionStage<?> g,
                 java.lang.Runnable a) {
                return f.runAfterEitherAsync(g, a);
            }
            public <T,U> CompletableFuture<U> thenCompose
                (CompletableFuture<T> f,
                 Function<? super T,? extends CompletionStage<U>> a) {
                return f.thenComposeAsync(a);
            }
            public <T> CompletableFuture<T> whenComplete
                (CompletableFuture<T> f,
                 BiConsumer<? super T,? super Throwable> a) {
                return f.whenCompleteAsync(a);
            }
        },

//         REVERSE_DEFAULT_ASYNC {
//             public <T,U> CompletableFuture<Void> runAfterBoth
//                 (CompletableFuture<T> f, CompletableFuture<U> g, Runnable a) {
//                 return f.runAfterBothAsync(g, a);
//             }
//             public <T,U> CompletableFuture<Void> thenAcceptBoth
//                 (CompletableFuture<T> f,
//                  CompletionStage<? extends U> g,
//                  BiConsumer<? super T,? super U> a) {
//                 return DEFAULT_ASYNC.thenAcceptBoth(f, g, a);
//             }
//         },

        EXECUTOR {
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
            public <T,U> CompletableFuture<U> applyToEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Function<? super T,U> a) {
                return f.applyToEitherAsync(g, a, new ThreadExecutor());
            }
            public <T> CompletableFuture<Void> acceptEither
                (CompletableFuture<T> f,
                 CompletionStage<? extends T> g,
                 Consumer<? super T> a) {
                return f.acceptEitherAsync(g, a, new ThreadExecutor());
            }
            public <T> CompletableFuture<Void> runAfterEither
                (CompletableFuture<T> f,
                 CompletionStage<?> g,
                 java.lang.Runnable a) {
                return f.runAfterEitherAsync(g, a, new ThreadExecutor());
            }
            public <T,U> CompletableFuture<U> thenCompose
                (CompletableFuture<T> f,
                 Function<? super T,? extends CompletionStage<U>> a) {
                return f.thenComposeAsync(a, new ThreadExecutor());
            }
            public <T> CompletableFuture<T> whenComplete
                (CompletableFuture<T> f,
                 BiConsumer<? super T,? super Throwable> a) {
                return f.whenCompleteAsync(a, new ThreadExecutor());
            }
        };

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
        public abstract <T,U> CompletableFuture<U> applyToEither
            (CompletableFuture<T> f,
             CompletionStage<? extends T> g,
             Function<? super T,U> a);
        public abstract <T> CompletableFuture<Void> acceptEither
            (CompletableFuture<T> f,
             CompletionStage<? extends T> g,
             Consumer<? super T> a);
        public abstract <T> CompletableFuture<Void> runAfterEither
            (CompletableFuture<T> f,
             CompletionStage<?> g,
             java.lang.Runnable a);
        public abstract <T,U> CompletableFuture<U> thenCompose
            (CompletableFuture<T> f,
             Function<? super T,? extends CompletionStage<U>> a);
        public abstract <T> CompletableFuture<T> whenComplete
            (CompletableFuture<T> f,
             BiConsumer<? super T,? super Throwable> a);


    }

    /**
     * exceptionally action completes with function value on source
     * exception; otherwise with source value
     */
    public void testExceptionally() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        ExceptionToInteger r = new ExceptionToInteger();
        CompletableFuture<Integer> g = f.exceptionally(r);
        f.completeExceptionally(new CFException());
        checkCompletedNormally(g, three);

        f = new CompletableFuture<>();
        r = new ExceptionToInteger();
        g = f.exceptionally(r);
        f.complete(one);
        checkCompletedNormally(g, one);
    }

    /**
     * handle action completes normally with function value on either
     * normal or exceptional completion of source
     */
    public void testHandle() {
        CompletableFuture<Integer> f, g;
        IntegerHandler r;

        f = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        g = f.handle(r = new IntegerHandler());
        assertTrue(r.ran);
        checkCompletedNormally(g, three);

        f = new CompletableFuture<>();
        g = f.handle(r = new IntegerHandler());
        assertFalse(r.ran);
        f.completeExceptionally(new CFException());
        checkCompletedNormally(g, three);
        assertTrue(r.ran);

        f = new CompletableFuture<>();
        f.complete(one);
        g = f.handle(r = new IntegerHandler());
        assertTrue(r.ran);
        checkCompletedNormally(g, two);

        f = new CompletableFuture<>();
        g = f.handle(r = new IntegerHandler());
        assertFalse(r.ran);
        f.complete(one);
        assertTrue(r.ran);
        checkCompletedNormally(g, two);
    }

    /**
     * runAsync completes after running Runnable
     */
    public void testRunAsync() {
        Noop r = new Noop();
        CompletableFuture<Void> f = CompletableFuture.runAsync(r);
        assertNull(f.join());
        assertTrue(r.ran);
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
        assertTrue(r.ran);
        checkCompletedNormally(f, null);
        assertEquals(1, exec.count.get());
    }

    /**
     * failing runAsync completes exceptionally after running Runnable
     */
    public void testRunAsync3() {
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> f = CompletableFuture.runAsync(r);
        checkCompletedWithWrappedCFException(f);
        assertTrue(r.ran);
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
        assertTrue(r.ran);
    }

    // seq completion methods

    /**
     * thenRun result completes normally after normal completion of source
     */
    public void testThenRun() {
        CompletableFuture<Integer> f;
        CompletableFuture<Void> g;
        Noop r;

        f = new CompletableFuture<>();
        g = f.thenRun(r = new Noop());
        f.complete(null);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);

        f = new CompletableFuture<>();
        f.complete(null);
        g = f.thenRun(r = new Noop());
        checkCompletedNormally(g, null);
        assertTrue(r.ran);
    }

    /**
     * thenRun result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenRun2() {
        CompletableFuture<Integer> f;
        CompletableFuture<Void> g;
        Noop r;

        f = new CompletableFuture<>();
        g = f.thenRun(r = new Noop());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
        assertFalse(r.ran);

        f = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        g = f.thenRun(r = new Noop());
        checkCompletedWithWrappedCFException(g);
        assertFalse(r.ran);
    }

    /**
     * thenRun result completes exceptionally if action does
     */
    public void testThenRun3() {
        CompletableFuture<Integer> f;
        CompletableFuture<Void> g;
        FailingNoop r;

        f = new CompletableFuture<>();
        g = f.thenRun(r = new FailingNoop());
        f.complete(null);
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<>();
        f.complete(null);
        g = f.thenRun(r = new FailingNoop());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRun result completes exceptionally if source cancelled
     */
    public void testThenRun4() {
        CompletableFuture<Integer> f;
        CompletableFuture<Void> g;
        Noop r;

        f = new CompletableFuture<>();
        g = f.thenRun(r = new Noop());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        f = new CompletableFuture<>();
        assertTrue(f.cancel(true));
        g = f.thenRun(r = new Noop());
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenApply result completes normally after normal completion of source
     */
    public void testThenApply() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.thenApply(inc);
        f.complete(one);
        checkCompletedNormally(g, two);
    }

    /**
     * thenApply result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenApply2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.thenApply(inc);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApply result completes exceptionally if action does
     */
    public void testThenApply3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.thenApply(new FailingFunction());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApply result completes exceptionally if source cancelled
     */
    public void testThenApply4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.thenApply(inc);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenAccept result completes normally after normal completion of source
     */
    public void testThenAccept() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAccept(r);
        f.complete(one);
        checkCompletedNormally(g, null);
        assertEquals(r.value, (Integer) 2);
    }

    /**
     * thenAccept result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenAccept2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAccept(r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAccept result completes exceptionally if action does
     */
    public void testThenAccept3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        FailingConsumer r = new FailingConsumer();
        CompletableFuture<Void> g = f.thenAccept(r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
        assertTrue(r.ran);
    }

    /**
     * thenAccept result completes exceptionally if source cancelled
     */
    public void testThenAccept4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAccept(r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenCombine result completes normally after normal completion
     * of sources
     */
    public void testThenCombine_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        f.complete(v1);
        checkIncomplete(h);
        assertEquals(r.invocationCount, 0);
        g.complete(v2);

        checkCompletedNormally(h, subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        assertEquals(r.invocationCount, 1);
        }
    }

    public void testThenCombine_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        g.complete(v2);
        checkIncomplete(h);
        assertEquals(r.invocationCount, 0);
        f.complete(v1);

        checkCompletedNormally(h, subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        assertEquals(r.invocationCount, 1);
        }
    }

    public void testThenCombine_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();

        g.complete(v2);
        f.complete(v1);
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        checkCompletedNormally(h, subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        assertEquals(r.invocationCount, 1);
        }
    }

    public void testThenCombine_normalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();

        f.complete(v1);
        g.complete(v2);
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        checkCompletedNormally(h, subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        assertEquals(r.invocationCount, 1);
        }
    }

    /**
     * thenCombine result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenCombine_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        checkIncomplete(h);
        g.complete(v1);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    public void testThenCombine_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testThenCombine_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        f.complete(v1);
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testThenCombine_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        g.complete(v1);
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * thenCombine result completes exceptionally if action does
     */
    public void testThenCombine_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingBiFunction r = new FailingBiFunction();
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        f.complete(v1);
        checkIncomplete(h);
        g.complete(v2);

        checkCompletedWithWrappedCFException(h);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testThenCombine_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingBiFunction r = new FailingBiFunction();
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        g.complete(v2);
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCFException(h);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    /**
     * thenCombine result completes exceptionally if either source cancelled
     */
    public void testThenCombine_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        g.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    public void testThenCombine_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testThenCombine_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testThenCombine_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractFunction r = new SubtractFunction();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Integer> h = m.thenCombine(f, g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * thenAcceptBoth result completes normally after normal
     * completion of sources
     */
    public void testThenAcceptBoth_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        f.complete(v1);
        checkIncomplete(h);
        assertFalse(r.ran());
        g.complete(v2);

        checkCompletedNormally(h, null);
        assertEquals(r.value, subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testThenAcceptBoth_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        g.complete(v2);
        checkIncomplete(h);
        assertFalse(r.ran());
        f.complete(v1);

        checkCompletedNormally(h, null);
        assertEquals(r.value, subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testThenAcceptBoth_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();

        g.complete(v2);
        f.complete(v1);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedNormally(h, null);
        assertEquals(r.value, subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testThenAcceptBoth_normalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();

        f.complete(v1);
        g.complete(v2);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedNormally(h, null);
        assertEquals(r.value, subtract(v1, v2));
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    /**
     * thenAcceptBoth result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenAcceptBoth_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

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
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    public void testThenAcceptBoth_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

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
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testThenAcceptBoth_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        f.complete(v1);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testThenAcceptBoth_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        g.complete(v1);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertFalse(r.ran());
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * thenAcceptBoth result completes exceptionally if action does
     */
    public void testThenAcceptBoth_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

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
        }
    }

    public void testThenAcceptBoth_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

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
        }
    }

    /**
     * thenAcceptBoth result completes exceptionally if either source cancelled
     */
    public void testThenAcceptBoth_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        g.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    public void testThenAcceptBoth_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testThenAcceptBoth_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testThenAcceptBoth_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final SubtractAction r = new SubtractAction();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Void> h = m.thenAcceptBoth(f, g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * runAfterBoth result completes normally after normal
     * completion of sources
     */
    public void testRunAfterBoth_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        f.complete(v1);
        checkIncomplete(h);
        assertFalse(r.ran);
        g.complete(v2);

        checkCompletedNormally(h, null);
        assertEquals(r.invocationCount, 1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testRunAfterBoth_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        g.complete(v2);
        checkIncomplete(h);
        assertFalse(r.ran);
        f.complete(v1);

        checkCompletedNormally(h, null);
        assertEquals(r.invocationCount, 1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testRunAfterBoth_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        g.complete(v2);
        f.complete(v1);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedNormally(h, null);
        assertTrue(r.ran);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testRunAfterBoth_normalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        f.complete(v1);
        g.complete(v2);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedNormally(h, null);
        assertEquals(r.invocationCount, 1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    /**
     * runAfterBoth result completes exceptionally after exceptional
     * completion of either source
     */
    public void testRunAfterBoth_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

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
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    public void testRunAfterBoth_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

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
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testRunAfterBoth_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        f.complete(v1);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testRunAfterBoth_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        g.complete(v1);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * runAfterBoth result completes exceptionally if action does
     */
    public void testRunAfterBoth_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingNoop r = new FailingNoop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        f.complete(v1);
        checkIncomplete(h);
        g.complete(v2);

        checkCompletedWithWrappedCFException(h);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testRunAfterBoth_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingNoop r = new FailingNoop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        g.complete(v2);
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCFException(h);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    /**
     * runAfterBoth result completes exceptionally if either source cancelled
     */
    public void testRunAfterBoth_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        g.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    public void testRunAfterBoth_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testRunAfterBoth_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        }
    }

    public void testRunAfterBoth_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Void> h = m.runAfterBoth(f, g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * applyToEither result completes normally after normal completion
     * of either source
     */
    public void testApplyToEither_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

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
        }
    }

    public void testApplyToEither_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

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
        }
    }
    public void testApplyToEither_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

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
        assertEquals(r.invocationCount, 1);
        }
    }

    /**
     * applyToEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testApplyToEither_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        g.complete(v1);

        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedWithWrappedCFException(h, ex);
        }
    }

    public void testApplyToEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        f.complete(v1);

        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedWithWrappedCFException(h, ex);
        }
    }

    public void testApplyToEither_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

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
            assertEquals(h.join(), inc(v1));
            assertEquals(r.invocationCount, 1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(r.invocationCount, 0);
        }

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedNormally(f, v1);
        }
    }

    public void testApplyToEither_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

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
            assertEquals(h.join(), inc(v1));
            assertEquals(r.invocationCount, 1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(r.invocationCount, 0);
        }

        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * applyToEither result completes exceptionally if action does
     */
    public void testApplyToEither_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingFunction r = new FailingFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        f.complete(v1);
        checkCompletedWithWrappedCFException(h);
        g.complete(v2);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testApplyToEither_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingFunction r = new FailingFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        g.complete(v2);
        checkCompletedWithWrappedCFException(h);
        f.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    /**
     * applyToEither result completes exceptionally if either source cancelled
     */
    public void testApplyToEither_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        g.complete(v1);

        checkCancelled(f);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h);
        }
    }

    public void testApplyToEither_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        f.complete(v1);

        checkCancelled(g);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCancellationException(h);
        }
    }

    public void testApplyToEither_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertEquals(h.join(), inc(v1));
            assertEquals(r.invocationCount, 1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(r.invocationCount, 0);
        }

        checkCancelled(g);
        checkCompletedNormally(f, v1);
        }
    }

    public void testApplyToEither_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncFunction r = new IncFunction();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Integer> h = m.applyToEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertEquals(h.join(), inc(v1));
            assertEquals(r.invocationCount, 1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(r.invocationCount, 0);
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * acceptEither result completes normally after normal completion
     * of either source
     */
    public void testAcceptEither_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        f.complete(v1);
        checkCompletedNormally(h, null);
        assertEquals(r.value, inc(v1));
        g.complete(v2);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h, null);
        }
    }

    public void testAcceptEither_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        g.complete(v2);
        checkCompletedNormally(h, null);
        assertEquals(r.value, inc(v2));
        f.complete(v1);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h, null);
        }
    }
    public void testAcceptEither_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

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
        }
    }

    /**
     * acceptEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testAcceptEither_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        g.complete(v1);

        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedWithWrappedCFException(h, ex);
        }
    }

    public void testAcceptEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        f.complete(v1);

        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedWithWrappedCFException(h, ex);
        }
    }

    public void testAcceptEither_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

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
            assertEquals(h.join(), null);
            assertEquals(r.invocationCount, 1);
            assertEquals(inc(v1), r.value);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(r.invocationCount, 0);
        }

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedNormally(f, v1);
        }
    }

    public void testAcceptEither_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

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
            assertEquals(h.join(), null);
            assertEquals(r.invocationCount, 1);
            assertEquals(inc(v1), r.value);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(r.invocationCount, 0);
        }

        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * acceptEither result completes exceptionally if action does
     */
    public void testAcceptEither_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingConsumer r = new FailingConsumer();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        f.complete(v1);
        checkCompletedWithWrappedCFException(h);
        g.complete(v2);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testAcceptEither_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingConsumer r = new FailingConsumer();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        g.complete(v2);
        checkCompletedWithWrappedCFException(h);
        f.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    /**
     * acceptEither result completes exceptionally if either source cancelled
     */
    public void testAcceptEither_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        g.complete(v1);

        checkCancelled(f);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h);
        }
    }

    public void testAcceptEither_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        f.complete(v1);

        checkCancelled(g);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCancellationException(h);
        }
    }

    public void testAcceptEither_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertEquals(h.join(), null);
            assertEquals(r.invocationCount, 1);
            assertEquals(inc(v1), r.value);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(r.invocationCount, 0);
        }

        checkCancelled(g);
        checkCompletedNormally(f, v1);
        }
    }

    public void testAcceptEither_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final IncAction r = new IncAction();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Void> h = m.acceptEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertEquals(h.join(), null);
            assertEquals(r.invocationCount, 1);
            assertEquals(inc(v1), r.value);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(r.invocationCount, 0);
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * runAfterEither result completes normally after normal completion
     * of either source
     */
    public void testRunAfterEither_normalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        f.complete(v1);
        checkCompletedNormally(h, null);
        assertEquals(r.invocationCount, 1);
        g.complete(v2);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h, null);
        assertEquals(r.invocationCount, 1);
        }
    }

    public void testRunAfterEither_normalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        g.complete(v2);
        checkCompletedNormally(h, null);
        assertEquals(r.invocationCount, 1);
        f.complete(v1);

        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        checkCompletedNormally(h, null);
        assertEquals(r.invocationCount, 1);
        }
    }
    public void testRunAfterEither_normalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        f.complete(v1);
        g.complete(v2);
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        checkCompletedNormally(h, null);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        assertEquals(r.invocationCount, 1);
        }
    }

    /**
     * runAfterEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testRunAfterEither_exceptionalCompletion1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        g.complete(v1);

        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedWithWrappedCFException(h, ex);
        }
    }

    public void testRunAfterEither_exceptionalCompletion2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(h, ex);
        f.complete(v1);

        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedWithWrappedCFException(h, ex);
        }
    }

    public void testRunAfterEither_exceptionalCompletion3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

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
            assertEquals(h.join(), null);
            assertEquals(r.invocationCount, 1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(r.invocationCount, 0);
        }

        checkCompletedWithWrappedCFException(g, ex);
        checkCompletedNormally(f, v1);
        }
    }

    public void testRunAfterEither_exceptionalCompletion4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

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
            assertEquals(h.join(), null);
            assertEquals(r.invocationCount, 1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCFException(h, ex);
            assertEquals(r.invocationCount, 0);
        }

        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * runAfterEither result completes exceptionally if action does
     */
    public void testRunAfterEither_actionFailed1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingNoop r = new FailingNoop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        f.complete(v1);
        checkCompletedWithWrappedCFException(h);
        g.complete(v2);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testRunAfterEither_actionFailed2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingNoop r = new FailingNoop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        g.complete(v2);
        checkCompletedWithWrappedCFException(h);
        f.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    /**
     * runAfterEither result completes exceptionally if either source cancelled
     */
    public void testRunAfterEither_sourceCancelled1() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        g.complete(v1);

        checkCancelled(f);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(g, v1);
        checkCompletedWithWrappedCancellationException(h);
        }
    }

    public void testRunAfterEither_sourceCancelled2() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkCompletedWithWrappedCancellationException(h);
        f.complete(v1);

        checkCancelled(g);
        assertEquals(r.invocationCount, 0);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCancellationException(h);
        }
    }

    public void testRunAfterEither_sourceCancelled3() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertEquals(h.join(), null);
            assertEquals(r.invocationCount, 1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(r.invocationCount, 0);
        }

        checkCancelled(g);
        checkCompletedNormally(f, v1);
        }
    }

    public void testRunAfterEither_sourceCancelled4() {
        for (ExecutionMode m : ExecutionMode.values())
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Void> h = m.runAfterEither(f, g, r);

        // unspecified behavior
        Integer v;
        try {
            assertEquals(h.join(), null);
            assertEquals(r.invocationCount, 1);
        } catch (CompletionException ok) {
            checkCompletedWithWrappedCancellationException(h);
            assertEquals(r.invocationCount, 0);
        }

        checkCancelled(f);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * thenCompose result completes normally after normal completion of source
     */
    public void testThenCompose() {
        CompletableFuture<Integer> f, g;
        CompletableFutureInc r;

        f = new CompletableFuture<>();
        g = f.thenCompose(r = new CompletableFutureInc());
        f.complete(one);
        checkCompletedNormally(g, two);
        assertTrue(r.ran);

        f = new CompletableFuture<>();
        f.complete(one);
        g = f.thenCompose(r = new CompletableFutureInc());
        checkCompletedNormally(g, two);
        assertTrue(r.ran);
    }

    /**
     * thenCompose result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenCompose2() {
        CompletableFuture<Integer> f, g;
        CompletableFutureInc r;

        f = new CompletableFuture<>();
        g = f.thenCompose(r = new CompletableFutureInc());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        g = f.thenCompose(r = new CompletableFutureInc());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenCompose result completes exceptionally if action does
     */
    public void testThenCompose3() {
        CompletableFuture<Integer> f, g;
        FailingCompletableFutureFunction r;

        f = new CompletableFuture<>();
        g = f.thenCompose(r = new FailingCompletableFutureFunction());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<>();
        f.complete(one);
        g = f.thenCompose(r = new FailingCompletableFutureFunction());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenCompose result completes exceptionally if source cancelled
     */
    public void testThenCompose4() {
        CompletableFuture<Integer> f, g;
        CompletableFutureInc r;

        f = new CompletableFuture<>();
        g = f.thenCompose(r = new CompletableFutureInc());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        f = new CompletableFuture<>();
        assertTrue(f.cancel(true));
        g = f.thenCompose(r = new CompletableFutureInc());
        checkCompletedWithWrappedCancellationException(g);
    }

    // asyncs

    /**
     * thenRunAsync result completes normally after normal completion of source
     */
    public void testThenRunAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r);
        f.complete(null);
        checkCompletedNormally(g, null);

        // reordered version
        f = new CompletableFuture<>();
        f.complete(null);
        r = new Noop();
        g = f.thenRunAsync(r);
        checkCompletedNormally(g, null);
    }

    /**
     * thenRunAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenRunAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r);
        f.completeExceptionally(new CFException());
        try {
            g.join();
            shouldThrow();
        } catch (CompletionException success) {}
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRunAsync result completes exceptionally if action does
     */
    public void testThenRunAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.thenRunAsync(r);
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRunAsync result completes exceptionally if source cancelled
     */
    public void testThenRunAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenApplyAsync result completes normally after normal completion of source
     */
    public void testThenApplyAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc);
        f.complete(one);
        checkCompletedNormally(g, two);
    }

    /**
     * thenApplyAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenApplyAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApplyAsync result completes exceptionally if action does
     */
    public void testThenApplyAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        FailingFunction r = new FailingFunction();
        CompletableFuture<Integer> g = f.thenApplyAsync(r);
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApplyAsync result completes exceptionally if source cancelled
     */
    public void testThenApplyAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenAcceptAsync result completes normally after normal
     * completion of source
     */
    public void testThenAcceptAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAcceptAsync(r);
        f.complete(one);
        checkCompletedNormally(g, null);
        assertEquals(r.value, (Integer) 2);
    }

    /**
     * thenAcceptAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenAcceptAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAcceptAsync(r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptAsync result completes exceptionally if action does
     */
    public void testThenAcceptAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        FailingConsumer r = new FailingConsumer();
        CompletableFuture<Void> g = f.thenAcceptAsync(r);
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptAsync result completes exceptionally if source cancelled
     */
    public void testThenAcceptAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAcceptAsync(r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenComposeAsync result completes normally after normal
     * completion of source
     */
    public void testThenComposeAsync() {
        CompletableFuture<Integer> f, g;
        CompletableFutureInc r;

        f = new CompletableFuture<>();
        g = f.thenComposeAsync(r = new CompletableFutureInc());
        f.complete(one);
        checkCompletedNormally(g, two);

        f = new CompletableFuture<>();
        f.complete(one);
        g = f.thenComposeAsync(r = new CompletableFutureInc());
        checkCompletedNormally(g, two);
    }

    /**
     * thenComposeAsync result completes exceptionally after
     * exceptional completion of source
     */
    public void testThenComposeAsync2() {
        CompletableFuture<Integer> f, g;
        CompletableFutureInc r;

        f = new CompletableFuture<>();
        g = f.thenComposeAsync(r = new CompletableFutureInc());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
        assertFalse(r.ran);

        f = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        g = f.thenComposeAsync(r = new CompletableFutureInc());
        checkCompletedWithWrappedCFException(g);
        assertFalse(r.ran);
    }

    /**
     * thenComposeAsync result completes exceptionally if action does
     */
    public void testThenComposeAsync3() {
        CompletableFuture<Integer> f, g;
        FailingCompletableFutureFunction r;

        f = new CompletableFuture<>();
        g = f.thenComposeAsync(r = new FailingCompletableFutureFunction());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<>();
        f.complete(one);
        g = f.thenComposeAsync(r = new FailingCompletableFutureFunction());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenComposeAsync result completes exceptionally if source cancelled
     */
    public void testThenComposeAsync4() {
        CompletableFuture<Integer> f, g;
        CompletableFutureInc r;

        f = new CompletableFuture<>();
        g = f.thenComposeAsync(r = new CompletableFutureInc());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        f = new CompletableFuture<>();
        assertTrue(f.cancel(true));
        g = f.thenComposeAsync(r = new CompletableFutureInc());
        checkCompletedWithWrappedCancellationException(g);
    }

    // async with explicit executors

    /**
     * thenRunAsync result completes normally after normal completion of source
     */
    public void testThenRunAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r, new ThreadExecutor());
        f.complete(null);
        checkCompletedNormally(g, null);

        // reordered version
        f = new CompletableFuture<>();
        f.complete(null);
        r = new Noop();
        g = f.thenRunAsync(r, new ThreadExecutor());
        checkCompletedNormally(g, null);
    }

    /**
     * thenRunAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenRunAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        try {
            g.join();
            shouldThrow();
        } catch (CompletionException success) {}
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRunAsync result completes exceptionally if action does
     */
    public void testThenRunAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.thenRunAsync(r, new ThreadExecutor());
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRunAsync result completes exceptionally if source cancelled
     */
    public void testThenRunAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenApplyAsync result completes normally after normal completion of source
     */
    public void testThenApplyAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc, new ThreadExecutor());
        f.complete(one);
        checkCompletedNormally(g, two);
    }

    /**
     * thenApplyAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenApplyAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApplyAsync result completes exceptionally if action does
     */
    public void testThenApplyAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        FailingFunction r = new FailingFunction();
        CompletableFuture<Integer> g = f.thenApplyAsync(r, new ThreadExecutor());
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApplyAsync result completes exceptionally if source cancelled
     */
    public void testThenApplyAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenAcceptAsync result completes normally after normal
     * completion of source
     */
    public void testThenAcceptAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAcceptAsync(r, new ThreadExecutor());
        f.complete(one);
        checkCompletedNormally(g, null);
        assertEquals(r.value, (Integer) 2);
    }

    /**
     * thenAcceptAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenAcceptAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAcceptAsync(r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptAsync result completes exceptionally if action does
     */
    public void testThenAcceptAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        FailingConsumer r = new FailingConsumer();
        CompletableFuture<Void> g = f.thenAcceptAsync(r, new ThreadExecutor());
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptAsync result completes exceptionally if source cancelled
     */
    public void testThenAcceptAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAcceptAsync(r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenComposeAsync result completes normally after normal
     * completion of source
     */
    public void testThenComposeAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFutureInc r = new CompletableFutureInc();
        CompletableFuture<Integer> g = f.thenComposeAsync(r, new ThreadExecutor());
        f.complete(one);
        checkCompletedNormally(g, two);
    }

    /**
     * thenComposeAsync result completes exceptionally after
     * exceptional completion of source
     */
    public void testThenComposeAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFutureInc r = new CompletableFutureInc();
        CompletableFuture<Integer> g = f.thenComposeAsync(r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenComposeAsync result completes exceptionally if action does
     */
    public void testThenComposeAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        FailingCompletableFutureFunction r = new FailingCompletableFutureFunction();
        CompletableFuture<Integer> g = f.thenComposeAsync(r, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenComposeAsync result completes exceptionally if source cancelled
     */
    public void testThenComposeAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFutureInc r = new CompletableFutureInc();
        CompletableFuture<Integer> g = f.thenComposeAsync(r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

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
        for (Integer v1 : new Integer[] { 1, null }) {

        final AtomicInteger a = new AtomicInteger();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g =
            m.whenComplete(f,
                           (Integer x, Throwable t) -> {
                               threadAssertSame(x, v1);
                               threadAssertNull(t);
                               a.getAndIncrement();
                           });
        f.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v1);
        assertEquals(a.get(), 1);
        }
    }

    /**
     * whenComplete action executes on exceptional completion, propagating
     * source result.
     */
    public void testWhenComplete_exceptionalCompletion() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final AtomicInteger a = new AtomicInteger();
        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer x, Throwable t) -> {
                threadAssertNull(x);
                threadAssertSame(t, ex);
                a.getAndIncrement();
            });
        f.completeExceptionally(ex);
        checkCompletedWithWrappedCFException(f, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertEquals(a.get(), 1);
        }
    }

    /**
     * If a whenComplete action throws an exception when triggered by
     * a normal completion, it completes exceptionally
     */
    public void testWhenComplete_actionFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CFException ex = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer x, Throwable t) -> {
                threadAssertSame(x, v1);
                threadAssertNull(t);
                throw ex;
            });
        f.complete(v1);
        checkCompletedNormally(f, v1);
        checkCompletedWithWrappedCFException(g, ex);
        }
    }

    /**
     * If a whenComplete action throws an exception when triggered by
     * a source completion that also throws an exception, the source
     * exception takes precedence.
     */
    public void testWhenComplete_actionFailedSourceFailed() {
        for (ExecutionMode m : ExecutionMode.values())
        for (Integer v1 : new Integer[] { 1, null }) {

        final CFException ex1 = new CFException();
        final CFException ex2 = new CFException();
        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = m.whenComplete
            (f,
             (Integer x, Throwable t) -> {
                threadAssertSame(t, ex1);
                threadAssertNull(x);
                throw ex2;
            });
        f.completeExceptionally(ex1);
        checkCompletedWithWrappedCFException(f, ex1);
        checkCompletedWithWrappedCFException(g, ex1);
        }
    }

    /**
     * handleAsync action completes normally with function value on
     * either normal or exceptional completion of source
     */
    public void testHandleAsync() {
        CompletableFuture<Integer> f, g;
        IntegerHandler r;

        f = new CompletableFuture<>();
        g = f.handleAsync(r = new IntegerHandler());
        assertFalse(r.ran);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(f);
        checkCompletedNormally(g, three);
        assertTrue(r.ran);

        f = new CompletableFuture<>();
        g = f.handleAsync(r = new IntegerHandler());
        assertFalse(r.ran);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(f);
        checkCompletedNormally(g, three);
        assertTrue(r.ran);

        f = new CompletableFuture<>();
        g = f.handleAsync(r = new IntegerHandler());
        assertFalse(r.ran);
        f.complete(one);
        checkCompletedNormally(f, one);
        checkCompletedNormally(g, two);
        assertTrue(r.ran);

        f = new CompletableFuture<>();
        g = f.handleAsync(r = new IntegerHandler());
        assertFalse(r.ran);
        f.complete(one);
        checkCompletedNormally(f, one);
        checkCompletedNormally(g, two);
        assertTrue(r.ran);
    }

    /**
     * handleAsync action with Executor completes normally with
     * function value on either normal or exceptional completion of
     * source
     */
    public void testHandleAsync2() {
        CompletableFuture<Integer> f, g;
        ThreadExecutor exec = new ThreadExecutor();
        IntegerHandler r;

        f = new CompletableFuture<>();
        g = f.handleAsync(r = new IntegerHandler(), exec);
        assertFalse(r.ran);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(f);
        checkCompletedNormally(g, three);
        assertTrue(r.ran);

        f = new CompletableFuture<>();
        g = f.handleAsync(r = new IntegerHandler(), exec);
        assertFalse(r.ran);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(f);
        checkCompletedNormally(g, three);
        assertTrue(r.ran);

        f = new CompletableFuture<>();
        g = f.handleAsync(r = new IntegerHandler(), exec);
        assertFalse(r.ran);
        f.complete(one);
        checkCompletedNormally(f, one);
        checkCompletedNormally(g, two);
        assertTrue(r.ran);

        f = new CompletableFuture<>();
        g = f.handleAsync(r = new IntegerHandler(), exec);
        assertFalse(r.ran);
        f.complete(one);
        checkCompletedNormally(f, one);
        checkCompletedNormally(g, two);
        assertTrue(r.ran);
    }

}
