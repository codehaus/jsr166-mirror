package java.util.concurrent;

import junit.framework.TestCase;

/**
 * Tests the ScheduledExecutor implementation.
 */
public class ScheduledExecutorTest extends TestCase {

    public void testScheduleSleepGet () {
        /*
        try {
            ScheduledExecutor se = new ScheduledExecutor(1);
            StringTask task = new StringTask();

            assertFalse("task should not be complete", task.isCompleted());

            ScheduledFuture<String> future = se.schedule(task, 2, TimeUnit.MILLISECONDS);
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException e) {
                fail("Shouldn't be interrupted");
            }
            
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
        */
    }

    private static final String TEST_STRING = "a test string";

    private static class StringTask implements Callable<String> {
        public String call() { 
            completed = true; 
            return TEST_STRING; 
        }
        public boolean isCompleted() { return completed; }
        public void reset() { completed = false; }
        private boolean completed = false;
    }
}
