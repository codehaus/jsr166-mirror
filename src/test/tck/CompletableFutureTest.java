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
            assertEquals(value, f.join());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertEquals(value, f.getNow(null));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertEquals(value, f.get());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertEquals(value, f.get(0L, SECONDS));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());
        assertTrue(f.toString().contains("[Completed normally]"));
    }

    void checkCompletedWithWrappedCFException(CompletableFuture<?> f) {
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
        try {
            f.get(0L, SECONDS);
            shouldThrow();
        } catch (ExecutionException success) {
            assertTrue(success.getCause() instanceof CFException);
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());
        assertTrue(f.toString().contains("[Completed exceptionally]"));
    }

    void checkCancelled(CompletableFuture<?> f) {
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
        try {
            f.get(0L, SECONDS);
            shouldThrow();
        } catch (CancellationException success) {
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(f.isDone());
        assertTrue(f.isCancelled());
        assertTrue(f.toString().contains("[Completed exceptionally]"));
    }

    void checkCompletedWithWrappedCancellationException(CompletableFuture<?> f) {
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
        try {
            f.get(0L, SECONDS);
            shouldThrow();
        } catch (ExecutionException success) {
            assertTrue(success.getCause() instanceof CancellationException);
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());
        assertTrue(f.toString().contains("[Completed exceptionally]"));
    }

    /**
     * A newly constructed CompletableFuture is incomplete, as indicated
     * by methods isDone, isCancelled, and getNow
     */
    public void testConstructor() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        checkIncomplete(f);
    }

    /**
     * complete completes normally, as indicated by methods isDone,
     * isCancelled, join, get, and getNow
     */
    public void testComplete() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        checkIncomplete(f);
        f.complete(one);
        checkCompletedNormally(f, one);
    }

    /**
     * completeExceptionally completes exceptionally, as indicated by
     * methods isDone, isCancelled, join, get, and getNow
     */
    public void testCompleteExceptionally() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        checkIncomplete(f);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(f);
    }

    /**
     * cancel completes exceptionally and reports cancelled, as indicated by
     * methods isDone, isCancelled, join, get, and getNow
     */
    public void testCancel() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        checkIncomplete(f);
        assertTrue(f.cancel(true));
        checkCancelled(f);
    }

    /**
     * obtrudeValue forces completion with given value
     */
    public void testObtrudeValue() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        checkIncomplete(f);
        f.complete(one);
        checkCompletedNormally(f, one);
        f.obtrudeValue(three);
        checkCompletedNormally(f, three);
        f.obtrudeValue(two);
        checkCompletedNormally(f, two);
        f = new CompletableFuture<Integer>();
        f.obtrudeValue(three);
        checkCompletedNormally(f, three);
        f = new CompletableFuture<Integer>();
        f.completeExceptionally(new CFException());
        f.obtrudeValue(four);
        checkCompletedNormally(f, four);
    }

    /**
     * obtrudeException forces completion with given exception
     */
    public void testObtrudeException() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        checkIncomplete(f);
        f.complete(one);
        checkCompletedNormally(f, one);
        f.obtrudeException(new CFException());
        checkCompletedWithWrappedCFException(f);
        f = new CompletableFuture<Integer>();
        f.obtrudeException(new CFException());
        checkCompletedWithWrappedCFException(f);
        f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
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

    static final Supplier<Integer> supplyOne =
        () -> Integer.valueOf(1);
    static final Function<Integer, Integer> inc =
        (Integer x) -> Integer.valueOf(x.intValue() + 1);
    static final BiFunction<Integer, Integer, Integer> add =
        (Integer x, Integer y) -> Integer.valueOf(x.intValue() + y.intValue());
    static final class IncAction implements Consumer<Integer> {
        int value;
        public void accept(Integer x) { value = x.intValue() + 1; }
    }
    static final class AddAction implements BiConsumer<Integer, Integer> {
        int value;
        public void accept(Integer x, Integer y) {
            value = x.intValue() + y.intValue();
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

    static final class CompletableFutureInc implements Function<Integer, CompletableFuture<Integer>> {
        public CompletableFuture<Integer> apply(Integer x) {
            CompletableFuture<Integer> f = new CompletableFuture<Integer>();
            f.complete(Integer.valueOf(x.intValue() + 1));
            return f;
        }
    }

    static final class FailingCompletableFutureFunction implements Function<Integer, CompletableFuture<Integer>> {
        boolean ran;
        public CompletableFuture<Integer> apply(Integer x) {
            ran = true; throw new CFException();
        }
    }

    // Used for explicit executor tests
    static final class ThreadExecutor implements Executor {
        public void execute(Runnable r) {
            new Thread(r).start();
        }
    }

    static final class ExceptionToInteger implements Function<Throwable, Integer> {
        public Integer apply(Throwable x) { return Integer.valueOf(3); }
    }

    static final class IntegerHandler implements BiFunction<Integer, Throwable, Integer> {
        public Integer apply(Integer x, Throwable t) {
            return (t == null) ? two : three;
        }
    }


    /**
     * exceptionally action completes with function value on source
     * exception;  otherwise with source value
     */
    public void testExceptionally() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        ExceptionToInteger r = new ExceptionToInteger();
        CompletableFuture<Integer> g = f.exceptionally(r);
        f.completeExceptionally(new CFException());
        checkCompletedNormally(g, three);

        f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        IntegerHandler r = new IntegerHandler();
        CompletableFuture<Integer> g = f.handle(r);
        f.completeExceptionally(new CFException());
        checkCompletedNormally(g, three);

        f = new CompletableFuture<Integer>();
        r = new IntegerHandler();
        g = f.handle(r);
        f.complete(one);
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
        CompletableFuture<Void> f = CompletableFuture.runAsync(r, new ThreadExecutor());
        assertNull(f.join());
        assertTrue(r.ran);
        checkCompletedNormally(f, null);
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
        CompletableFuture<Integer> f = CompletableFuture.supplyAsync(supplyOne);
        assertEquals(f.join(), one);
    }

    /**
     * supplyAsync with executor completes with result of supplier
     */
    public void testSupplyAsync2() {
        CompletableFuture<Integer> f = CompletableFuture.supplyAsync(supplyOne, new ThreadExecutor());
        assertEquals(f.join(), one);
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRun(r);
        f.complete(null);
        checkCompletedNormally(g, null);
        // reordered version
        f = new CompletableFuture<Integer>();
        f.complete(null);
        r = new Noop();
        g = f.thenRun(r);
        checkCompletedNormally(g, null);
    }

    /**
     * thenRun result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenRun2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRun(r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRun result completes exceptionally if action does
     */
    public void testThenRun3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.thenRun(r);
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRun result completes exceptionally if source cancelled
     */
    public void testThenRun4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRun(r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenApply result completes normally after normal completion of source
     */
    public void testThenApply() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenApply(inc);
        f.complete(one);
        checkCompletedNormally(g, two);
    }

    /**
     * thenApply result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenApply2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenApply(inc);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApply result completes exceptionally if action does
     */
    public void testThenApply3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenApply(new FailingFunction());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApply result completes exceptionally if source cancelled
     */
    public void testThenApply4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenApply(inc);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenAccept result completes normally after normal completion of source
     */
    public void testThenAccept() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAccept(r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAccept result completes exceptionally if action does
     */
    public void testThenAccept3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAccept(r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }


    /**
     * thenCombine result completes normally after normal completion of sources
     */
    public void testThenCombine() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenCombine(f2, add);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, three);

        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.thenCombine(f2, add);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, three);
    }

    /**
     * thenCombine result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenCombine2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenCombine(f2, add);
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.thenCombine(f2, add);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenCombine result completes exceptionally if action does
     */
    public void testThenCombine3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingBiFunction r = new FailingBiFunction();
        CompletableFuture<Integer> g = f.thenCombine(f2, r);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenCombine result completes exceptionally if either source cancelled
     */
    public void testThenCombine4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenCombine(f2, add);
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.thenCombine(f2, add);
        f.complete(one);
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenAcceptBoth result completes normally after normal
     * completion of sources
     */
    public void testThenAcceptBoth() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        AddAction r = new AddAction();
        CompletableFuture<Void> g = f.thenAcceptBoth(f2, r);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 3);

        r = new AddAction();
        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.thenAcceptBoth(f2, r);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 3);
    }

    /**
     * thenAcceptBoth result completes exceptionally after exceptional
     * completion of either source
     */
    public void testThenAcceptBoth2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        AddAction r = new AddAction();
        CompletableFuture<Void> g = f.thenAcceptBoth(f2, r);
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        r = new AddAction();
        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.thenAcceptBoth(f2, r);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptBoth result completes exceptionally if action does
     */
    public void testThenAcceptBoth3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingBiConsumer r = new FailingBiConsumer();
        CompletableFuture<Void> g = f.thenAcceptBoth(f2, r);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptBoth result completes exceptionally if either source cancelled
     */
    public void testThenAcceptBoth4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        AddAction r = new AddAction();
        CompletableFuture<Void> g = f.thenAcceptBoth(f2, r);
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        r = new AddAction();
        g = f.thenAcceptBoth(f2, r);
        f.complete(one);
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * runAfterBoth result completes normally after normal
     * completion of sources
     */
    public void testRunAfterBoth() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBoth(f2, r);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.runAfterBoth(f2, r);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);
    }

    /**
     * runAfterBoth result completes exceptionally after exceptional
     * completion of either source
     */
    public void testRunAfterBoth2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBoth(f2, r);
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.runAfterBoth(f2, r);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterBoth result completes exceptionally if action does
     */
    public void testRunAfterBoth3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.runAfterBoth(f2, r);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterBoth result completes exceptionally if either source cancelled
     */
    public void testRunAfterBoth4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBoth(f2, r);
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        r = new Noop();
        g = f.runAfterBoth(f2, r);
        f.complete(one);
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * applyToEither result completes normally after normal completion
     * of either source
     */
    public void testApplyToEither() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.applyToEither(f2, inc);
        f.complete(one);
        checkCompletedNormally(g, two);
        f2.complete(one);
        checkCompletedNormally(g, two);

        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.applyToEither(f2, inc);
        checkCompletedNormally(g, two);
    }

    /**
     * applyToEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testApplyToEither2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.applyToEither(f2, inc);
        f.completeExceptionally(new CFException());
        f2.complete(one);
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        f2.completeExceptionally(new CFException());
        g = f.applyToEither(f2, inc);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEither result completes exceptionally if action does
     */
    public void testApplyToEither3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingFunction r = new FailingFunction();
        CompletableFuture<Integer> g = f.applyToEither(f2, r);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEither result completes exceptionally if either source cancelled
     */
    public void testApplyToEither4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.applyToEither(f2, inc);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * acceptEither result completes normally after normal completion
     * of either source
     */
    public void testAcceptEither() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEither(f2, r);
        f.complete(one);
        checkCompletedNormally(g, null);
        f2.complete(one);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);

        r = new IncAction();
        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.acceptEither(f2, r);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);
    }

    /**
     * acceptEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testAcceptEither2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEither(f2, r);
        f.completeExceptionally(new CFException());
        f2.complete(one);
        checkCompletedWithWrappedCFException(g);

        r = new IncAction();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        f2.completeExceptionally(new CFException());
        g = f.acceptEither(f2, r);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * acceptEither result completes exceptionally if action does
     */
    public void testAcceptEither3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingConsumer r = new FailingConsumer();
        CompletableFuture<Void> g = f.acceptEither(f2, r);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * acceptEither result completes exceptionally if either source cancelled
     */
    public void testAcceptEither4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEither(f2, r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }


    /**
     * runAfterEither result completes normally after normal completion
     * of either source
     */
    public void testRunAfterEither() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEither(f2, r);
        f.complete(one);
        checkCompletedNormally(g, null);
        f2.complete(one);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.runAfterEither(f2, r);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);
    }

    /**
     * runAfterEither result completes exceptionally after exceptional
     * completion of either source
     */
    public void testRunAfterEither2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEither(f2, r);
        f.completeExceptionally(new CFException());
        f2.complete(one);
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        f2.completeExceptionally(new CFException());
        g = f.runAfterEither(f2, r);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterEither result completes exceptionally if action does
     */
    public void testRunAfterEither3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.runAfterEither(f2, r);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterEither result completes exceptionally if either source cancelled
     */
    public void testRunAfterEither4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEither(f2, r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenCompose result completes normally after normal completion of source
     */
    public void testThenCompose() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFutureInc r = new CompletableFutureInc();
        CompletableFuture<Integer> g = f.thenCompose(r);
        f.complete(one);
        checkCompletedNormally(g, two);
    }

    /**
     * thenCompose result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenCompose2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFutureInc r = new CompletableFutureInc();
        CompletableFuture<Integer> g = f.thenCompose(r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenCompose result completes exceptionally if action does
     */
    public void testThenCompose3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        FailingCompletableFutureFunction r = new FailingCompletableFutureFunction();
        CompletableFuture<Integer> g = f.thenCompose(r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenCompose result completes exceptionally if source cancelled
     */
    public void testThenCompose4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFutureInc r = new CompletableFutureInc();
        CompletableFuture<Integer> g = f.thenCompose(r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }


    // asyncs

    /**
     * thenRunAsync result completes normally after normal completion of source
     */
    public void testThenRunAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r);
        f.complete(null);
        checkCompletedNormally(g, null);

        // reordered version
        f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r);
        f.completeExceptionally(new CFException());
        try {
            g.join();
            shouldThrow();
        } catch (Exception ok) {
        }
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRunAsync result completes exceptionally if action does
     */
    public void testThenRunAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.thenRunAsync(r);
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRunAsync result completes exceptionally if source cancelled
     */
    public void testThenRunAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenApplyAsync result completes normally after normal completion of source
     */
    public void testThenApplyAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc);
        f.complete(one);
        checkCompletedNormally(g, two);
    }

    /**
     * thenApplyAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenApplyAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApplyAsync result completes exceptionally if action does
     */
    public void testThenApplyAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        FailingFunction r = new FailingFunction();
        CompletableFuture<Integer> g = f.thenApplyAsync(r);
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApplyAsync result completes exceptionally if source cancelled
     */
    public void testThenApplyAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenAcceptAsync result completes normally after normal
     * completion of source
     */
    public void testThenAcceptAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAcceptAsync(r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptAsync result completes exceptionally if action does
     */
    public void testThenAcceptAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        FailingConsumer r = new FailingConsumer();
        CompletableFuture<Void> g = f.thenAcceptAsync(r);
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptAsync result completes exceptionally if source cancelled
     */
    public void testThenAcceptAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenCombineAsync(f2, add);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, three);
    }

    /**
     * thenCombineAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenCombineAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenCombineAsync(f2, add);
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.thenCombineAsync(f2, add);
        f.complete(one);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenCombineAsync result completes exceptionally if action does
     */
    public void testThenCombineAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingBiFunction r = new FailingBiFunction();
        CompletableFuture<Integer> g = f.thenCombineAsync(f2, r);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenCombineAsync result completes exceptionally if either source cancelled
     */
    public void testThenCombineAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenCombineAsync(f2, add);
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);

        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.thenCombineAsync(f2, add);
        f.complete(one);
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenAcceptBothAsync result completes normally after normal
     * completion of sources
     */
    public void testThenAcceptBothAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        AddAction r = new AddAction();
        CompletableFuture<Void> g = f.thenAcceptBothAsync(f2, r);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 3);
    }

    /**
     * thenAcceptBothAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenAcceptBothAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        AddAction r = new AddAction();
        CompletableFuture<Void> g = f.thenAcceptBothAsync(f2, r);
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        r = new AddAction();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.thenAcceptBothAsync(f2, r);
        f.complete(one);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptBothAsync result completes exceptionally if action does
     */
    public void testThenAcceptBothAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingBiConsumer r = new FailingBiConsumer();
        CompletableFuture<Void> g = f.thenAcceptBothAsync(f2, r);
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptBothAsync result completes exceptionally if either source cancelled
     */
    public void testThenAcceptBothAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        AddAction r = new AddAction();
        CompletableFuture<Void> g = f.thenAcceptBothAsync(f2, r);
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);

        r = new AddAction();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.thenAcceptBothAsync(f2, r);
        f.complete(one);
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * runAfterBothAsync result completes normally after normal
     * completion of sources
     */
    public void testRunAfterBothAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r);
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.runAfterBothAsync(f2, r);
        f.complete(one);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterBothAsync result completes exceptionally if action does
     */
    public void testRunAfterBothAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r);
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc);
        f.complete(one);
        checkCompletedNormally(g, two);

        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.applyToEitherAsync(f2, inc);
        checkCompletedNormally(g, two);
    }

    /**
     * applyToEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testApplyToEitherAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        f2.completeExceptionally(new CFException());
        g = f.applyToEitherAsync(f2, inc);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEitherAsync result completes exceptionally if action does
     */
    public void testApplyToEitherAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingFunction r = new FailingFunction();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEitherAsync result completes exceptionally if either source cancelled
     */
    public void testApplyToEitherAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        assertTrue(f2.cancel(true));
        g = f.applyToEitherAsync(f2, inc);
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * acceptEitherAsync result completes normally after normal
     * completion of sources
     */
    public void testAcceptEitherAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r);
        f.complete(one);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);

        r = new IncAction();
        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.acceptEitherAsync(f2, r);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);
    }

    /**
     * acceptEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testAcceptEitherAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        r = new IncAction();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        f2.completeExceptionally(new CFException());
        g = f.acceptEitherAsync(f2, r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * acceptEitherAsync result completes exceptionally if action does
     */
    public void testAcceptEitherAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        r = new IncAction();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        assertTrue(f2.cancel(true));
        g = f.acceptEitherAsync(f2, r);
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * runAfterEitherAsync result completes normally after normal
     * completion of sources
     */
    public void testRunAfterEitherAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r);
        f.complete(one);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.runAfterEitherAsync(f2, r);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);
    }

    /**
     * runAfterEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testRunAfterEitherAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        f2.completeExceptionally(new CFException());
        g = f.runAfterEitherAsync(f2, r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterEitherAsync result completes exceptionally if action does
     */
    public void testRunAfterEitherAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        assertTrue(f2.cancel(true));
        g = f.runAfterEitherAsync(f2, r);
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenComposeAsync result completes normally after normal
     * completion of source
     */
    public void testThenComposeAsync() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFutureInc r = new CompletableFutureInc();
        CompletableFuture<Integer> g = f.thenComposeAsync(r);
        f.complete(one);
        checkCompletedNormally(g, two);
    }

    /**
     * thenComposeAsync result completes exceptionally after
     * exceptional completion of source
     */
    public void testThenComposeAsync2() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFutureInc r = new CompletableFutureInc();
        CompletableFuture<Integer> g = f.thenComposeAsync(r);
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenComposeAsync result completes exceptionally if action does
     */
    public void testThenComposeAsync3() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        FailingCompletableFutureFunction r = new FailingCompletableFutureFunction();
        CompletableFuture<Integer> g = f.thenComposeAsync(r);
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenComposeAsync result completes exceptionally if source cancelled
     */
    public void testThenComposeAsync4() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFutureInc r = new CompletableFutureInc();
        CompletableFuture<Integer> g = f.thenComposeAsync(r);
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }


    // async with explicit executors

    /**
     * thenRunAsync result completes normally after normal completion of source
     */
    public void testThenRunAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r, new ThreadExecutor());
        f.complete(null);
        checkCompletedNormally(g, null);

        // reordered version
        f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        try {
            g.join();
            shouldThrow();
        } catch (Exception ok) {
        }
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRunAsync result completes exceptionally if action does
     */
    public void testThenRunAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        FailingNoop r = new FailingNoop();
        CompletableFuture<Void> g = f.thenRunAsync(r, new ThreadExecutor());
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenRunAsync result completes exceptionally if source cancelled
     */
    public void testThenRunAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.thenRunAsync(r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenApplyAsync result completes normally after normal completion of source
     */
    public void testThenApplyAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc, new ThreadExecutor());
        f.complete(one);
        checkCompletedNormally(g, two);
    }

    /**
     * thenApplyAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenApplyAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApplyAsync result completes exceptionally if action does
     */
    public void testThenApplyAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        FailingFunction r = new FailingFunction();
        CompletableFuture<Integer> g = f.thenApplyAsync(r, new ThreadExecutor());
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenApplyAsync result completes exceptionally if source cancelled
     */
    public void testThenApplyAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenApplyAsync(inc, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenAcceptAsync result completes normally after normal
     * completion of source
     */
    public void testThenAcceptAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.thenAcceptAsync(r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptAsync result completes exceptionally if action does
     */
    public void testThenAcceptAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        FailingConsumer r = new FailingConsumer();
        CompletableFuture<Void> g = f.thenAcceptAsync(r, new ThreadExecutor());
        f.complete(null);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptAsync result completes exceptionally if source cancelled
     */
    public void testThenAcceptAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenCombineAsync(f2, add, new ThreadExecutor());
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, three);
    }

    /**
     * thenCombineAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenCombineAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenCombineAsync(f2, add, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.thenCombineAsync(f2, add, new ThreadExecutor());
        f.complete(one);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenCombineAsync result completes exceptionally if action does
     */
    public void testThenCombineAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingBiFunction r = new FailingBiFunction();
        CompletableFuture<Integer> g = f.thenCombineAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenCombineAsync result completes exceptionally if either source cancelled
     */
    public void testThenCombineAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.thenCombineAsync(f2, add, new ThreadExecutor());
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);

        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.thenCombineAsync(f2, add, new ThreadExecutor());
        f.complete(one);
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenAcceptBothAsync result completes normally after normal
     * completion of sources
     */
    public void testThenAcceptBothAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        AddAction r = new AddAction();
        CompletableFuture<Void> g = f.thenAcceptBothAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 3);
    }

    /**
     * thenAcceptBothAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testThenAcceptBothAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        AddAction r = new AddAction();
        CompletableFuture<Void> g = f.thenAcceptBothAsync(f2, r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        r = new AddAction();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.thenAcceptBothAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptBothAsync result completes exceptionally if action does
     */
    public void testThenAcceptBothAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingBiConsumer r = new FailingBiConsumer();
        CompletableFuture<Void> g = f.thenAcceptBothAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkIncomplete(g);
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenAcceptBothAsync result completes exceptionally if either source cancelled
     */
    public void testThenAcceptBothAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        AddAction r = new AddAction();
        CompletableFuture<Void> g = f.thenAcceptBothAsync(f2, r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);

        r = new AddAction();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.thenAcceptBothAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        assertTrue(f2.cancel(true));
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * runAfterBothAsync result completes normally after normal
     * completion of sources
     */
    public void testRunAfterBothAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        f2.complete(two);
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        g = f.runAfterBothAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        f2.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterBothAsync result completes exceptionally if action does
     */
    public void testRunAfterBothAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterBothAsync(f2, r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        f2.complete(two);
        checkCompletedWithWrappedCancellationException(g);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        f.complete(one);
        checkCompletedNormally(g, two);

        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        checkCompletedNormally(g, two);
    }

    /**
     * applyToEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testApplyToEitherAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        f2.completeExceptionally(new CFException());
        g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEitherAsync result completes exceptionally if action does
     */
    public void testApplyToEitherAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        FailingFunction r = new FailingFunction();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * applyToEitherAsync result completes exceptionally if either source cancelled
     */
    public void testApplyToEitherAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        assertTrue(f2.cancel(true));
        g = f.applyToEitherAsync(f2, inc, new ThreadExecutor());
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * acceptEitherAsync result completes normally after normal
     * completion of sources
     */
    public void testAcceptEitherAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);

        r = new IncAction();
        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        checkCompletedNormally(g, null);
        assertEquals(r.value, 2);
    }

    /**
     * acceptEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testAcceptEitherAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        r = new IncAction();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        f2.completeExceptionally(new CFException());
        g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * acceptEitherAsync result completes exceptionally if action does
     */
    public void testAcceptEitherAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        IncAction r = new IncAction();
        CompletableFuture<Void> g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        r = new IncAction();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        assertTrue(f2.cancel(true));
        g = f.acceptEitherAsync(f2, r, new ThreadExecutor());
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * runAfterEitherAsync result completes normally after normal
     * completion of sources
     */
    public void testRunAfterEitherAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedNormally(g, null);
        assertTrue(r.ran);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f.complete(one);
        f2 = new CompletableFuture<Integer>();
        g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        checkCompletedNormally(g, null);
        assertTrue(r.ran);
    }

    /**
     * runAfterEitherAsync result completes exceptionally after exceptional
     * completion of source
     */
    public void testRunAfterEitherAsync2E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        f2.completeExceptionally(new CFException());
        g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * runAfterEitherAsync result completes exceptionally if action does
     */
    public void testRunAfterEitherAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
        Noop r = new Noop();
        CompletableFuture<Void> g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        assertTrue(f.cancel(true));
        checkCompletedWithWrappedCancellationException(g);

        r = new Noop();
        f = new CompletableFuture<Integer>();
        f2 = new CompletableFuture<Integer>();
        assertTrue(f2.cancel(true));
        g = f.runAfterEitherAsync(f2, r, new ThreadExecutor());
        checkCompletedWithWrappedCancellationException(g);
    }

    /**
     * thenComposeAsync result completes normally after normal
     * completion of source
     */
    public void testThenComposeAsyncE() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
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
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFutureInc r = new CompletableFutureInc();
        CompletableFuture<Integer> g = f.thenComposeAsync(r, new ThreadExecutor());
        f.completeExceptionally(new CFException());
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenComposeAsync result completes exceptionally if action does
     */
    public void testThenComposeAsync3E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        FailingCompletableFutureFunction r = new FailingCompletableFutureFunction();
        CompletableFuture<Integer> g = f.thenComposeAsync(r, new ThreadExecutor());
        f.complete(one);
        checkCompletedWithWrappedCFException(g);
    }

    /**
     * thenComposeAsync result completes exceptionally if source cancelled
     */
    public void testThenComposeAsync4E() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
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
        CompletableFuture<?> f = CompletableFuture.allOf();
        checkCompletedNormally(f, null);
    }

    /**
     * allOf returns a future completed when all components complete
     */
    public void testAllOf() throws Exception {
        for (int k = 1; k < 20; ++k) {
            CompletableFuture[] fs = new CompletableFuture[k];
            for (int i = 0; i < k; ++i)
                fs[i] = new CompletableFuture<Integer>();
            CompletableFuture<Void> f = CompletableFuture.allOf(fs);
            for (int i = 0; i < k; ++i) {
                checkIncomplete(f);
                fs[i].complete(one);
            }
            checkCompletedNormally(f, null);
        }
    }

    /**
     * anyOf(no component futures) returns an incomplete future
     */
    public void testAnyOf_empty() throws Exception {
        CompletableFuture<?> f = CompletableFuture.anyOf();
        checkIncomplete(f);
    }

    /**
     * anyOf returns a future completed when any components complete
     */
    public void testAnyOf() throws Exception {
        for (int k = 1; k < 20; ++k) {
            CompletableFuture[] fs = new CompletableFuture[k];
            for (int i = 0; i < k; ++i)
                fs[i] = new CompletableFuture<Integer>();
            CompletableFuture<Object> f = CompletableFuture.anyOf(fs);
            checkIncomplete(f);
            for (int i = 0; i < k; ++i) {
                fs[i].complete(one);
                checkCompletedNormally(f, one);
            }
        }
    }

    /**
     * Completion methods throw NullPointerException with null arguments
     */
    public void testNPE() {
        CompletableFuture<Integer> f = new CompletableFuture<Integer>();
        CompletableFuture<Integer> g = new CompletableFuture<Integer>();
        CompletableFuture<Integer> nullFuture = (CompletableFuture<Integer>)null;
        CompletableFuture<?> h;
        Executor exec = new ThreadExecutor();

        Runnable[] throwingActions = {
            () -> { CompletableFuture.supplyAsync(null); },
            () -> { CompletableFuture.supplyAsync(null, exec); },
            () -> { CompletableFuture.supplyAsync(() -> one, null); },

            () -> { CompletableFuture.runAsync(null); },
            () -> { CompletableFuture.runAsync(null, exec); },
            () -> { CompletableFuture.runAsync(() -> {}, null); },

            () -> { f.completeExceptionally(null); },

            () -> { f.thenApply(null); },
            () -> { f.thenApplyAsync(null); },
            () -> { f.thenApplyAsync((x) -> x, null); },
            () -> { f.thenApplyAsync(null, exec); },

            () -> { f.thenAccept(null); },
            () -> { f.thenAcceptAsync(null); },
            () -> { f.thenAcceptAsync((x) -> { ; }, null); },
            () -> { f.thenAcceptAsync(null, exec); },

            () -> { f.thenRun(null); },
            () -> { f.thenRunAsync(null); },
            () -> { f.thenRunAsync(() -> { ; }, null); },
            () -> { f.thenRunAsync(null, exec); },

            () -> { f.thenCombine(g, null); },
            () -> { f.thenCombineAsync(g, null); },
            () -> { f.thenCombineAsync(g, null, exec); },
            () -> { f.thenCombine(nullFuture, (x, y) -> x); },
            () -> { f.thenCombineAsync(nullFuture, (x, y) -> x); },
            () -> { f.thenCombineAsync(nullFuture, (x, y) -> x, exec); },
            () -> { f.thenCombineAsync(g, (x, y) -> x, null); },

            () -> { f.thenAcceptBoth(g, null); },
            () -> { f.thenAcceptBothAsync(g, null); },
            () -> { f.thenAcceptBothAsync(g, null, exec); },
            () -> { f.thenAcceptBoth(nullFuture, (x, y) -> {}); },
            () -> { f.thenAcceptBothAsync(nullFuture, (x, y) -> {}); },
            () -> { f.thenAcceptBothAsync(nullFuture, (x, y) -> {}, exec); },
            () -> { f.thenAcceptBothAsync(g, (x, y) -> {}, null); },

            () -> { f.runAfterBoth(g, null); },
            () -> { f.runAfterBothAsync(g, null); },
            () -> { f.runAfterBothAsync(g, null, exec); },
            () -> { f.runAfterBoth(nullFuture, () -> {}); },
            () -> { f.runAfterBothAsync(nullFuture, () -> {}); },
            () -> { f.runAfterBothAsync(nullFuture, () -> {}, exec); },
            () -> { f.runAfterBothAsync(g, () -> {}, null); },

            () -> { f.applyToEither(g, null); },
            () -> { f.applyToEitherAsync(g, null); },
            () -> { f.applyToEitherAsync(g, null, exec); },
            () -> { f.applyToEither(nullFuture, (x) -> x); },
            () -> { f.applyToEitherAsync(nullFuture, (x) -> x); },
            () -> { f.applyToEitherAsync(nullFuture, (x) -> x, exec); },
            () -> { f.applyToEitherAsync(g, (x) -> x, null); },

            () -> { f.acceptEither(g, null); },
            () -> { f.acceptEitherAsync(g, null); },
            () -> { f.acceptEitherAsync(g, null, exec); },
            () -> { f.acceptEither(nullFuture, (x) -> {}); },
            () -> { f.acceptEitherAsync(nullFuture, (x) -> {}); },
            () -> { f.acceptEitherAsync(nullFuture, (x) -> {}, exec); },
            () -> { f.acceptEitherAsync(g, (x) -> {}, null); },

            () -> { f.runAfterEither(g, null); },
            () -> { f.runAfterEitherAsync(g, null); },
            () -> { f.runAfterEitherAsync(g, null, exec); },
            () -> { f.runAfterEither(nullFuture, () -> {}); },
            () -> { f.runAfterEitherAsync(nullFuture, () -> {}); },
            () -> { f.runAfterEitherAsync(nullFuture, () -> {}, exec); },
            () -> { f.runAfterEitherAsync(g, () -> {}, null); },

            () -> { f.thenCompose(null); },
            () -> { f.thenComposeAsync(null); },
            () -> { f.thenComposeAsync(new CompletableFutureInc(), null); },
            () -> { f.thenComposeAsync(null, exec); },

            () -> { f.exceptionally(null); },

            () -> { f.handle(null); },

            () -> { CompletableFuture.allOf((CompletableFuture<?>)null); },
            () -> { CompletableFuture.allOf((CompletableFuture<?>[])null); },
            () -> { CompletableFuture.allOf(f, null); },
            () -> { CompletableFuture.allOf(null, f); },

            () -> { CompletableFuture.anyOf((CompletableFuture<?>)null); },
            () -> { CompletableFuture.anyOf((CompletableFuture<?>[])null); },
            () -> { CompletableFuture.anyOf(f, null); },
            () -> { CompletableFuture.anyOf(null, f); },

            // TODO: Crashes javac with lambda-8-2013-03-31...
            //() -> { CompletableFuture<?> x = f.thenAccept(null); },
            //() -> { CompletableFuture<Void> x = f.thenRun(null); },
            //() -> { CompletableFuture<Integer> x = f.thenApply(() -> { ; }); },
        };

        assertThrows(NullPointerException.class, throwingActions);
    }

}
