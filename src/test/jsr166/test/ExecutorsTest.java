package jsr166.test;

import java.util.concurrent.*;

import junit.framework.TestCase;

/**
 * Tests the Executors static methods.
 */
public class ExecutorsTest extends TestCase {

    public void testExecuteRunnable () {
        try {
            Executor e = new DirectExecutor();
            Task task = new Task();

            assertFalse("task should not be complete", task.isCompleted());

            Future<String> future = Executors.execute(e, task, TEST_STRING);
            String result = future.get();

            assertTrue("task should be complete", task.isCompleted());
            assertSame("should return test string", TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            fail("Unexpected execution exception: " + ex);
        }
        catch (InterruptedException ex) {
            fail("Unexpected interruption exception: " + ex);
        }
    }

    public void testInvokeRunnable () {
        try {
            Executor e = new DirectExecutor();
            Task task = new Task();

            assertFalse("task should not be complete", task.isCompleted());

            Executors.invoke(e, task);

            assertTrue("task should be complete", task.isCompleted());
        }
        catch (ExecutionException ex) {
            fail("Unexpected execution exception: " + ex);
        }
        catch (InterruptedException ex) {
            fail("Unexpected interruption exception: " + ex);
        }
    }

    public void testExecuteCallable () {
        try {
            Executor e = new DirectExecutor();
            Future<String> future = Executors.execute(e, new StringTask());
            String result = future.get();

            assertSame("should return test string", TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            fail("Unexpected execution exception: " + ex);
        }
        catch (InterruptedException ex) {
            fail("Unexpected interruption exception: " + ex);
        }
    }

    public void testInvokeCallable () {
        try {
            Executor e = new DirectExecutor();
            String result = Executors.invoke(e, new StringTask());

            assertSame("should return test string", TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            fail("Unexpected execution exception: " + ex);
        }
        catch (InterruptedException ex) {
            fail("Unexpected interruption exception: " + ex);
        }
    }

    private static final String TEST_STRING = "a test string";

    private static class Task implements Runnable {
        public void run() { completed = true; }
        public boolean isCompleted() { return completed; }
        public void reset() { completed = false; }
        private boolean completed = false;
    }

    private static class StringTask implements Callable<String> {
        public String call() { return TEST_STRING; }
    }
}
