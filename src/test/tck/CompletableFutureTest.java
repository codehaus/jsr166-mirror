/*
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import junit.framework.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.*;

public class CompletableFutureTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(CompletableFutureTest.class);
    }

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

    void checkCompletedNormally(CompletableFuture<?> f, Object value) {
        assertTrue(f.isDone());
        assertFalse(f.isCancelled());
        assertTrue(f.toString().contains("[Completed normally]"));
        try {
            assertSame(value, f.join());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertSame(value, f.getNow(null));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertSame(value, f.get());
        } catch (Throwable fail) { threadUnexpectedException(fail); }
        try {
            assertSame(value, f.get(0L, SECONDS));
        } catch (Throwable fail) { threadUnexpectedException(fail); }
    }

    // XXXX Just a skeleton implementation for now.
    public void testTODO() {
        fail("Please add some real tests!");
    }

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
     * allOf(no component futures) returns a future completed normally
     * with the value null
     */
    public void testAllOf_empty() throws Exception {
        CompletableFuture<?> f = CompletableFuture.allOf();
        checkCompletedNormally(f, null);
    }

    /**
     * anyOf(no component futures) returns an incomplete future
     */
    public void testAnyOf_empty() throws Exception {
        CompletableFuture<?> f = CompletableFuture.anyOf();
        checkIncomplete(f);
    }
}
