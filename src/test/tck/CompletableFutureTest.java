/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
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
}
