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

    static final Supplier<Integer> supplyOne =
        () -> Integer.valueOf(1);
    static final Function<Integer, Integer> inc =
        (Integer x) -> Integer.valueOf(x.intValue() + 1);
    static final BiFunction<Integer, Integer, Integer> subtract =
        (Integer x, Integer y) -> Integer.valueOf(x.intValue() - y.intValue());
    static final class IncAction implements Consumer<Integer> {
        int value;
        public void accept(Integer x) { value = x.intValue() + 1; }
    }
    static final class SubtractAction implements BiConsumer<Integer, Integer> {
        int value;
        public void accept(Integer x, Integer y) {
            value = x.intValue() - y.intValue();
        }
    }
    static final class Noop implements Runnable {
        boolean ran;
        public void run() { ran = true; }
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
        assertEquals(r.value, 2);
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
    public void testThenCombine() {
        CompletableFuture<Integer> f, g, h;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombine(g, subtract);
        f.complete(3);
        checkIncomplete(h);
        g.complete(1);
        checkCompletedNormally(h, 2);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombine(g, subtract);
        g.complete(1);
        checkIncomplete(h);
        f.complete(3);
        checkCompletedNormally(h, 2);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        g.complete(1);
        f.complete(3);
        h = f.thenCombine(g, subtract);
        checkCompletedNormally(h, 2);
    }

    /**
     * thenCombine result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenCombine2() {
        CompletableFuture<Integer> f, g, h;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombine(g, subtract);
        f.completeExceptionally(new CFException());
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombine(g, subtract);
        g.completeExceptionally(new CFException());
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        g.completeExceptionally(new CFException());
        h = f.thenCombine(g, subtract);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        g.complete(3);
        h = f.thenCombine(g, subtract);
        checkCompletedWithWrappedCFException(h);
    }

    /**
     * thenCombine result completes exceptionally if action does
     */
    public void testThenCombine3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingBiFunction r = new FailingBiFunction();
        CompletableFuture<Integer> g = f.thenCombine(f2, r);
        f.complete(one);
        checkIncomplete(g);
        assertFalse(r.ran);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
        assertTrue(r.ran);
    }

    /**
     * thenCombine result completes exceptionally if either source cancelled
     */
    public void testThenCombine4() {
        CompletableFuture<Integer> f, g, h;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombine(g, subtract);
        assertTrue(f.cancel(true));
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombine(g, subtract);
        assertTrue(g.cancel(true));
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        assertTrue(f.cancel(true));
        assertTrue(g.cancel(true));
        h = f.thenCombine(g, subtract);
        checkCompletedWithWrappedCancellationException(h);
    }

    /**
     * thenAcceptBoth result completes normally after normal
     * completion of sources
     */
    public void testThenAcceptBoth() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        SubtractAction r;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        f.complete(3);
        checkIncomplete(h);
        g.complete(1);
        checkCompletedNormally(h, null);
        assertEquals(r.value, 2);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        g.complete(1);
        checkIncomplete(h);
        f.complete(3);
        checkCompletedNormally(h, null);
        assertEquals(r.value, 2);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        g.complete(1);
        f.complete(3);
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        checkCompletedNormally(h, null);
        assertEquals(r.value, 2);
    }

    /**
     * thenAcceptBoth result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenAcceptBoth2() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        SubtractAction r;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        f.completeExceptionally(new CFException());
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        g.completeExceptionally(new CFException());
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        g.completeExceptionally(new CFException());
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        g.complete(3);
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        checkCompletedWithWrappedCFException(h);
    }

    /**
     * thenAcceptBoth result completes exceptionally if action does
     */
    public void testThenAcceptBoth3() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        FailingBiConsumer r;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBoth(g, r = new FailingBiConsumer());
        f.complete(3);
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        g.complete(1);
        h = f.thenAcceptBoth(g, r = new FailingBiConsumer());
        checkCompletedWithWrappedCFException(h);
    }

    /**
     * thenAcceptBoth result completes exceptionally if either source cancelled
     */
    public void testThenAcceptBoth4() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        SubtractAction r;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        assertTrue(f.cancel(true));
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        assertTrue(g.cancel(true));
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        assertTrue(g.cancel(true));
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        assertTrue(f.cancel(true));
        g.complete(3);
        h = f.thenAcceptBoth(g, r = new SubtractAction());
        checkCompletedWithWrappedCancellationException(h);
    }

    /**
     * runAfterBoth result completes normally after normal
     * completion of sources
     */
    public void testRunAfterBoth_normalCompletion1() {
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        f.complete(v1);
        checkIncomplete(h);
        assertFalse(r.ran);
        g.complete(v2);

        checkCompletedNormally(h, null);
        assertTrue(r.ran);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testRunAfterBoth_normalCompletion2() {
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        g.complete(v2);
        checkIncomplete(h);
        assertFalse(r.ran);
        f.complete(v1);

        checkCompletedNormally(h, null);
        assertTrue(r.ran);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testRunAfterBoth_normalCompletion3() {
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        g.complete(v2);
        f.complete(v1);
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        checkCompletedNormally(h, null);
        assertTrue(r.ran);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testRunAfterBoth_normalCompletion4() {
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        f.complete(v1);
        g.complete(v2);
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        checkCompletedNormally(h, null);
        assertTrue(r.ran);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    /**
     * runAfterBoth result completes exceptionally after exceptional
     * completion of either source
     */
    public void testRunAfterBoth_exceptionalCompletion1() {
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        checkIncomplete(h);
        g.complete(v1);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertFalse(r.ran);
        checkCompletedNormally(g, v1);
        }
    }

    public void testRunAfterBoth_exceptionalCompletion2() {
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertFalse(r.ran);
        checkCompletedNormally(f, v1);
        }
    }

    public void testRunAfterBoth_exceptionalCompletion3() {
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CFException ex = new CFException();

        g.completeExceptionally(ex);
        f.complete(v1);
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(g, ex);
        assertFalse(r.ran);
        checkCompletedNormally(f, v1);
        }
    }

    public void testRunAfterBoth_exceptionalCompletion4() {
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CFException ex = new CFException();

        f.completeExceptionally(ex);
        g.complete(v1);
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        checkCompletedWithWrappedCFException(h, ex);
        checkCompletedWithWrappedCFException(f, ex);
        assertFalse(r.ran);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * runAfterBoth result completes exceptionally if action does
     */
    public void testRunAfterBoth_actionFailed1() {
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingNoop r = new FailingNoop();
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        f.complete(v1);
        checkIncomplete(h);
        g.complete(v2);

        checkCompletedWithWrappedCFException(h);
        checkCompletedNormally(f, v1);
        checkCompletedNormally(g, v2);
        }
    }

    public void testRunAfterBoth_actionFailed2() {
        for (Integer v1 : new Integer[] { 1, null })
        for (Integer v2 : new Integer[] { 2, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final FailingNoop r = new FailingNoop();
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

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
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        assertTrue(f.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        g.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertFalse(r.ran);
        checkCompletedNormally(g, v1);
        }
    }

    public void testRunAfterBoth_sourceCancelled2() {
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        assertTrue(g.cancel(mayInterruptIfRunning));
        checkIncomplete(h);
        f.complete(v1);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertFalse(r.ran);
        checkCompletedNormally(f, v1);
        }
    }

    public void testRunAfterBoth_sourceCancelled3() {
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        assertTrue(g.cancel(mayInterruptIfRunning));
        f.complete(v1);
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(g);
        assertFalse(r.ran);
        checkCompletedNormally(f, v1);
        }
    }

    public void testRunAfterBoth_sourceCancelled4() {
        for (boolean mayInterruptIfRunning : new boolean[] { true, false })
        for (Integer v1 : new Integer[] { 1, null }) {

        final CompletableFuture<Integer> f = new CompletableFuture<>();
        final CompletableFuture<Integer> g = new CompletableFuture<>();
        final Noop r = new Noop();

        assertTrue(f.cancel(mayInterruptIfRunning));
        g.complete(v1);
        final CompletableFuture<Void> h = f.runAfterBoth(g, r);

        checkCompletedWithWrappedCancellationException(h);
        checkCancelled(f);
        assertFalse(r.ran);
        checkCompletedNormally(g, v1);
        }
    }

    /**
     * applyToEither result completes normally after normal completion
     * of either source
     */
    public void testApplyToEither() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.applyToEither(f2, inc);
        f.complete(one);
        checkCompletedNormally(g, two);
        f2.complete(one);
        checkCompletedNormally(g, two);

        f = new CompletableFuture<>();
        f.complete(one);
        f2 = new CompletableFuture<>();
        g = f.applyToEither(f2, inc);
        checkCompletedNormally(g, two);
    }

    /**
     * applyToEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testApplyToEither2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.applyToEither(f2, inc);
        f.completeExceptionally(new CFException());
        f2.complete(one);
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        f2.completeExceptionally(new CFException());
        g = f.applyToEither(f2, inc);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEither result completes exceptionally if action does
     */
    public void testApplyToEither3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingFunction r = new FailingFunction();
        CompletableFuture<Integer> g = f.applyToEither(f2, r);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEither result completes exceptionally if either source cancelled
     */
    public void testApplyToEither4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.applyToEither(f2, inc);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * acceptEither result completes normally after normal completion
     * of either source
     */
    public void testAcceptEither() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEither(f2, r);
        f.complete(one);
        checkCompletedNormally(g, null);
        f2.complete(one);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);

        r = new IncAction();
        f = new CompletableFuture<>();
        f.complete(one);
        f2 = new CompletableFuture<>();
        g = f.acceptEither(f2, r);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);
    }

    /**
     * acceptEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testAcceptEither2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEither(f2, r);
        f.completeExceptionally(new CFException());
        f2.complete(one);
        checkCompletedWithWrappedCFException(g);

        r = new IncAction();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        f2.completeExceptionally(new CFException());
        g = f.acceptEither(f2, r);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * acceptEither result completes exceptionally if action does
     */
    public void testAcceptEither3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingConsumer r = new FailingConsumer();
        CompletableFuture<Void> g = f.acceptEither(f2, r);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * acceptEither result completes exceptionally if either source cancelled
     */
    public void testAcceptEither4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEither(f2, r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * runAfterEither result completes normally after normal completion
     * of either source
     */
    public void testRunAfterEither() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEither(f2, r);
        f.complete(one);
        checkCompletedNormally(g, null);
        f2.complete(one);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);

        r = new Noop();
        f = new CompletableFuture<>();
        f.complete(one);
        f2 = new CompletableFuture<>();
        g = f.runAfterEither(f2, r);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);
    }

    /**
     * runAfterEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testRunAfterEither2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEither(f2, r);
        f.completeExceptionally(new CFException());
        f2.complete(one);
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        f2.completeExceptionally(new CFException());
        g = f.runAfterEither(f2, r);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterEither result completes exceptionally if action does
     */
    public void testRunAfterEither3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.runAfterEither(f2, r);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterEither result completes exceptionally if either source cancelled
     */
    public void testRunAfterEither4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEither(f2, r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
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
        assertEquals(r.value, 2);
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
     * thenCombineAsync result completes normally after normal
     * completion of sources
     */
    public void testThenCombineAsync() {
        CompletableFuture<Integer> f, g, h;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract);
        f.complete(3);
        checkIncomplete(h);
        g.complete(1);
        checkCompletedNormally(h, 2);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract);
        g.complete(1);
        checkIncomplete(h);
        f.complete(3);
        checkCompletedNormally(h, 2);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        g.complete(1);
        f.complete(3);
        h = f.thenCombineAsync(g, subtract);
        checkCompletedNormally(h, 2);
    }

    /**
     * thenCombineAsync result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenCombineAsync2() {
        CompletableFuture<Integer> f, g, h;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract);
        f.completeExceptionally(new CFException());
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract);
        g.completeExceptionally(new CFException());
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        g.completeExceptionally(new CFException());
        f.complete(3);
        h = f.thenCombineAsync(g, subtract);
        checkCompletedWithWrappedCFException(h);
    }

    /**
     * thenCombineAsync result completes exceptionally if action does
     */
    public void testThenCombineAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingBiFunction r = new FailingBiFunction();
        CompletableFuture<Integer> g = f.thenCombineAsync(f2, r);
        f.complete(one);
        checkIncomplete(g);
        assertFalse(r.ran);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
        assertTrue(r.ran);
    }

    /**
     * thenCombineAsync result completes exceptionally if either source cancelled
     */
    public void testThenCombineAsync4() {
        CompletableFuture<Integer> f, g, h;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract);
        assertTrue(f.cancel(true));
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract);
        assertTrue(g.cancel(true));
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        g.complete(3);
        assertTrue(f.cancel(true));
        h = f.thenCombineAsync(g, subtract);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        assertTrue(g.cancel(true));
        h = f.thenCombineAsync(g, subtract);
        checkCompletedWithWrappedCancellationException(h);
    }

    /**
     * thenAcceptBothAsync result completes normally after normal
     * completion of sources
     */
    public void testThenAcceptBothAsync() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        SubtractAction r;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        f.complete(3);
        checkIncomplete(h);
        g.complete(1);
        checkCompletedNormally(h, null);
        assertEquals(r.value, 2);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        g.complete(1);
        checkIncomplete(h);
        f.complete(3);
        checkCompletedNormally(h, null);
        assertEquals(r.value, 2);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        g.complete(1);
        f.complete(3);
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        checkCompletedNormally(h, null);
        assertEquals(r.value, 2);
    }

    /**
     * thenAcceptBothAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenAcceptBothAsync2() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        SubtractAction r;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        f.completeExceptionally(new CFException());
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        g.completeExceptionally(new CFException());
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        g.completeExceptionally(new CFException());
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        g.complete(3);
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        checkCompletedWithWrappedCFException(h);
    }

    /**
     * thenAcceptBothAsync result completes exceptionally if action does
     */
    public void testThenAcceptBothAsync3() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        FailingBiConsumer r;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new FailingBiConsumer());
        f.complete(3);
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        g.complete(1);
        h = f.thenAcceptBothAsync(g, r = new FailingBiConsumer());
        checkCompletedWithWrappedCFException(h);
    }

    /**
     * thenAcceptBothAsync result completes exceptionally if either source cancelled
     */
    public void testThenAcceptBothAsync4() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        SubtractAction r;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        assertTrue(f.cancel(true));
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        assertTrue(g.cancel(true));
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        assertTrue(g.cancel(true));
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        assertTrue(f.cancel(true));
        g.complete(3);
        h = f.thenAcceptBothAsync(g, r = new SubtractAction());
        checkCompletedWithWrappedCancellationException(h);
    }

    /**
     * runAfterBothAsync result completes normally after normal
     * completion of sources
     */
    public void testRunAfterBothAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);
    }

    /**
     * runAfterBothAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testRunAfterBothAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r);
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        g = f.runAfterBothAsync(f2, r);
        f.complete(one);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterBothAsync result completes exceptionally if action does
     */
    public void testRunAfterBothAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterBothAsync result completes exceptionally if either source cancelled
     */
    public void testRunAfterBothAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r);
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);

        r = new Noop();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        g = f.runAfterBothAsync(f2, r);
        f.complete(one);
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * applyToEitherAsync result completes normally after normal
     * completion of sources
     */
    public void testApplyToEitherAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc);
        f.complete(one);
        checkCompletedNormally(g, two);

        f = new CompletableFuture<>();
        f.complete(one);
        f2 = new CompletableFuture<>();
        g = f.applyToEitherAsync(f2, inc);
        checkCompletedNormally(g, two);
    }

    /**
     * applyToEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testApplyToEitherAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        f2.completeExceptionally(new CFException());
        g = f.applyToEitherAsync(f2, inc);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEitherAsync result completes exceptionally if action does
     */
    public void testApplyToEitherAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingFunction r = new FailingFunction();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEitherAsync result completes exceptionally if either source cancelled
     */
    public void testApplyToEitherAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        assertTrue(f2.cancel(true));
        g = f.applyToEitherAsync(f2, inc);
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * acceptEitherAsync result completes normally after normal
     * completion of sources
     */
    public void testAcceptEitherAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r);
        f.complete(one);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);

        r = new IncAction();
        f = new CompletableFuture<>();
        f.complete(one);
        f2 = new CompletableFuture<>();
        g = f.acceptEitherAsync(f2, r);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);
    }

    /**
     * acceptEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testAcceptEitherAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        r = new IncAction();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        f2.completeExceptionally(new CFException());
        g = f.acceptEitherAsync(f2, r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * acceptEitherAsync result completes exceptionally if action does
     */
    public void testAcceptEitherAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingConsumer r = new FailingConsumer();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * acceptEitherAsync result completes exceptionally if either
     * source cancelled
     */
    public void testAcceptEitherAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        r = new IncAction();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        assertTrue(f2.cancel(true));
        g = f.acceptEitherAsync(f2, r);
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * runAfterEitherAsync result completes normally after normal
     * completion of sources
     */
    public void testRunAfterEitherAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r);
        f.complete(one);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);

        r = new Noop();
        f = new CompletableFuture<>();
        f.complete(one);
        f2 = new CompletableFuture<>();
        g = f.runAfterEitherAsync(f2, r);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);
    }

    /**
     * runAfterEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testRunAfterEitherAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        f2.completeExceptionally(new CFException());
        g = f.runAfterEitherAsync(f2, r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterEitherAsync result completes exceptionally if action does
     */
    public void testRunAfterEitherAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterEitherAsync result completes exceptionally if either
     * source cancelled
     */
    public void testRunAfterEitherAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        r = new Noop();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        assertTrue(f2.cancel(true));
        g = f.runAfterEitherAsync(f2, r);
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
        assertEquals(r.value, 2);
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
     * thenCombineAsync result completes normally after normal
     * completion of sources
     */
    public void testThenCombineAsyncE() {
        CompletableFuture<Integer> f, g, h;
        ThreadExecutor e = new ThreadExecutor();
        int count = 0;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract, e);
        f.complete(3);
        checkIncomplete(h);
        g.complete(1);
        checkCompletedNormally(h, 2);
        assertEquals(++count, e.count.get());

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract, e);
        g.complete(1);
        checkIncomplete(h);
        f.complete(3);
        checkCompletedNormally(h, 2);
        assertEquals(++count, e.count.get());

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        g.complete(1);
        f.complete(3);
        h = f.thenCombineAsync(g, subtract, e);
        checkCompletedNormally(h, 2);
        assertEquals(++count, e.count.get());
    }

    /**
     * thenCombineAsync result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenCombineAsync2E() {
        CompletableFuture<Integer> f, g, h;
        ThreadExecutor e = new ThreadExecutor();
        int count = 0;

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract, e);
        f.completeExceptionally(new CFException());
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract, e);
        g.completeExceptionally(new CFException());
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        g.completeExceptionally(new CFException());
        h = f.thenCombineAsync(g, subtract, e);
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCFException(h);

        assertEquals(0, e.count.get());
    }

    /**
     * thenCombineAsync result completes exceptionally if action does
     */
    public void testThenCombineAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingBiFunction r = new FailingBiFunction();
        CompletableFuture<Integer> g = f.thenCombineAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkIncomplete(g);
        assertFalse(r.ran);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
        assertTrue(r.ran);
    }

    /**
     * thenCombineAsync result completes exceptionally if either source cancelled
     */
    public void testThenCombineAsync4E() {
        CompletableFuture<Integer> f, g, h;
        ThreadExecutor e = new ThreadExecutor();

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract, e);
        assertTrue(f.cancel(true));
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenCombineAsync(g, subtract, e);
        assertTrue(g.cancel(true));
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        assertTrue(g.cancel(true));
        h = f.thenCombineAsync(g, subtract, e);
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        assertTrue(f.cancel(true));
        assertTrue(g.cancel(true));
        h = f.thenCombineAsync(g, subtract, e);
        checkCompletedWithWrappedCancellationException(h);

        assertEquals(0, e.count.get());
    }

    /**
     * thenAcceptBothAsync result completes normally after normal
     * completion of sources
     */
    public void testThenAcceptBothAsyncE() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        SubtractAction r;
        ThreadExecutor e = new ThreadExecutor();

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        f.complete(3);
        checkIncomplete(h);
        g.complete(1);
        checkCompletedNormally(h, null);
        assertEquals(r.value, 2);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        g.complete(1);
        checkIncomplete(h);
        f.complete(3);
        checkCompletedNormally(h, null);
        assertEquals(r.value, 2);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        g.complete(1);
        f.complete(3);
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        checkCompletedNormally(h, null);
        assertEquals(r.value, 2);

        assertEquals(3, e.count.get());
    }

    /**
     * thenAcceptBothAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenAcceptBothAsync2E() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        SubtractAction r;
        ThreadExecutor e = new ThreadExecutor();

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        f.completeExceptionally(new CFException());
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        g.completeExceptionally(new CFException());
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        g.completeExceptionally(new CFException());
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.completeExceptionally(new CFException());
        g.complete(3);
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        checkCompletedWithWrappedCFException(h);

        assertEquals(0, e.count.get());
    }

    /**
     * thenAcceptBothAsync result completes exceptionally if action does
     */
    public void testThenAcceptBothAsync3E() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        FailingBiConsumer r;
        ThreadExecutor e = new ThreadExecutor();

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new FailingBiConsumer(), e);
        f.complete(3);
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCFException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        g.complete(1);
        h = f.thenAcceptBothAsync(g, r = new FailingBiConsumer(), e);
        checkCompletedWithWrappedCFException(h);

        assertEquals(2, e.count.get());
    }

    /**
     * thenAcceptBothAsync result completes exceptionally if either source cancelled
     */
    public void testThenAcceptBothAsync4E() {
        CompletableFuture<Integer> f, g;
        CompletableFuture<Void> h;
        SubtractAction r;
        ThreadExecutor e = new ThreadExecutor();

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        assertTrue(f.cancel(true));
        checkIncomplete(h);
        g.complete(1);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        assertTrue(g.cancel(true));
        checkIncomplete(h);
        f.complete(3);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        f.complete(3);
        assertTrue(g.cancel(true));
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        checkCompletedWithWrappedCancellationException(h);

        f = new CompletableFuture<>();
        g = new CompletableFuture<>();
        assertTrue(f.cancel(true));
        g.complete(3);
        h = f.thenAcceptBothAsync(g, r = new SubtractAction(), e);
        checkCompletedWithWrappedCancellationException(h);

        assertEquals(0, e.count.get());
    }

    /**
     * runAfterBothAsync result completes normally after normal
     * completion of sources
     */
    public void testRunAfterBothAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);
    }

    /**
     * runAfterBothAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testRunAfterBothAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        g = f.runAfterBothAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterBothAsync result completes exceptionally if action does
     */
    public void testRunAfterBothAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterBothAsync result completes exceptionally if either source cancelled
     */
    public void testRunAfterBothAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);

        r = new Noop();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        g = f.runAfterBothAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * applyToEitherAsync result completes normally after normal
     * completion of sources
     */
    public void testApplyToEitherAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        f.complete(one);
        checkCompletedNormally(g, two);

        f = new CompletableFuture<>();
        f.complete(one);
        f2 = new CompletableFuture<>();
        g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        checkCompletedNormally(g, two);
    }

    /**
     * applyToEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testApplyToEitherAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        f2.completeExceptionally(new CFException());
        g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEitherAsync result completes exceptionally if action does
     */
    public void testApplyToEitherAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingFunction r = new FailingFunction();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEitherAsync result completes exceptionally if either source cancelled
     */
    public void testApplyToEitherAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        assertTrue(f2.cancel(true));
        g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * acceptEitherAsync result completes normally after normal
     * completion of sources
     */
    public void testAcceptEitherAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);

        r = new IncAction();
        f = new CompletableFuture<>();
        f.complete(one);
        f2 = new CompletableFuture<>();
        g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);
    }

    /**
     * acceptEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testAcceptEitherAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        r = new IncAction();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        f2.completeExceptionally(new CFException());
        g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * acceptEitherAsync result completes exceptionally if action does
     */
    public void testAcceptEitherAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingConsumer r = new FailingConsumer();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * acceptEitherAsync result completes exceptionally if either
     * source cancelled
     */
    public void testAcceptEitherAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        r = new IncAction();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        assertTrue(f2.cancel(true));
        g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * runAfterEitherAsync result completes normally after normal
     * completion of sources
     */
    public void testRunAfterEitherAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);

        r = new Noop();
        f = new CompletableFuture<>();
        f.complete(one);
        f2 = new CompletableFuture<>();
        g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        checkCompletedNormally(g, null);
        assertTrue(r.ran);
    }

    /**
     * runAfterEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testRunAfterEitherAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        f2.completeExceptionally(new CFException());
        g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterEitherAsync result completes exceptionally if action does
     */
    public void testRunAfterEitherAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterEitherAsync result completes exceptionally if either
     * source cancelled
     */
    public void testRunAfterEitherAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> f2 = new CompletableFuture<>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        r = new Noop();
        f = new CompletableFuture<>();
        f2 = new CompletableFuture<>();
        assertTrue(f2.cancel(true));
        g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
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
    public void testWhenComplete1() {
        final AtomicInteger a = new AtomicInteger();
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g =
            f.whenComplete((Integer x, Throwable t) -> a.getAndIncrement());
        f.complete(three);
        checkCompletedNormally(f, three);
        checkCompletedNormally(g, three);
        assertEquals(a.get(), 1);
    }

    /**
     * whenComplete action executes on exceptional completion, propagating
     * source result.
     */
    public void testWhenComplete2() {
        final AtomicInteger a = new AtomicInteger();
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g =
            f.whenComplete((Integer x, Throwable t) -> a.getAndIncrement());
        f.completeExceptionally(new CFException());
        assertTrue(f.isCompletedExceptionally());
        assertTrue(g.isCompletedExceptionally());
        assertEquals(a.get(), 1);
    }

    /**
     * If a whenComplete action throws an exception when triggered by
     * a normal completion, it completes exceptionally
     */
    public void testWhenComplete3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g =
            f.whenComplete((Integer x, Throwable t) ->
                           { throw new CFException(); } );
        f.complete(three);
        checkCompletedNormally(f, three);
        assertTrue(g.isCompletedExceptionally());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * whenCompleteAsync action executes on normal completion, propagating
     * source result.
     */
    public void testWhenCompleteAsync1() {
        final AtomicInteger a = new AtomicInteger();
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g =
            f.whenCompleteAsync((Integer x, Throwable t) -> a.getAndIncrement());
        f.complete(three);
        checkCompletedNormally(f, three);
        checkCompletedNormally(g, three);
        assertEquals(a.get(), 1);
    }

    /**
     * whenCompleteAsync action executes on exceptional completion, propagating
     * source result.
     */
    public void testWhenCompleteAsync2() {
        final AtomicInteger a = new AtomicInteger();
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g =
            f.whenCompleteAsync((Integer x, Throwable t) -> a.getAndIncrement());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(f);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * If a whenCompleteAsync action throws an exception when
     * triggered by a normal completion, it completes exceptionally
     */
    public void testWhenCompleteAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g =
            f.whenCompleteAsync((Integer x, Throwable t) ->
                           { throw new CFException(); } );
        f.complete(three);
        checkCompletedNormally(f, three);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * whenCompleteAsync action executes on normal completion, propagating
     * source result.
     */
    public void testWhenCompleteAsync1e() {
        final AtomicInteger a = new AtomicInteger();
        ThreadExecutor exec = new ThreadExecutor();
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g =
            f.whenCompleteAsync((Integer x, Throwable t) -> a.getAndIncrement(),
                                exec);
        f.complete(three);
        checkCompletedNormally(f, three);
        checkCompletedNormally(g, three);
        assertEquals(a.get(), 1);
    }

    /**
     * whenCompleteAsync action executes on exceptional completion, propagating
     * source result.
     */
    public void testWhenCompleteAsync2e() {
        final AtomicInteger a = new AtomicInteger();
        ThreadExecutor exec = new ThreadExecutor();
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g =
            f.whenCompleteAsync((Integer x, Throwable t) -> a.getAndIncrement(),
                                exec);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(f);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * If a whenCompleteAsync action throws an exception when triggered
     * by a normal completion, it completes exceptionally
     */
    public void testWhenCompleteAsync3e() {
        ThreadExecutor exec = new ThreadExecutor();
        CompletableFuture<Integer> f = new CompletableFuture<>();
        CompletableFuture<Integer> g =
            f.whenCompleteAsync((Integer x, Throwable t) ->
                                { throw new CFException(); },
                                exec);
        f.complete(three);
        checkCompletedNormally(f, three);
        checkCompletedWithWrappedCFException(g);
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
