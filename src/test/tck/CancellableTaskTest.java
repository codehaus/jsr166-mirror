/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class CancellableTaskTest extends JSR166TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(CancellableTaskTest.class);
    }

    /**
     * Subclass to expose protected methods
     */
    static class PublicCancellableTask extends CancellableTask {
        public PublicCancellableTask() {}
        public PublicCancellableTask(Runnable r) { super(r); }
        public boolean reset() { return super.reset(); }
        public Runnable getRunnable() { return super.getRunnable(); }
        public void setRunnable(Runnable r) { super.setRunnable(r); }
        public void setCancelled() { super.setCancelled(); }
        public void setDone() { super.setDone(); }
    }

    /**
     * creating task with null runnable throws NPE
     */
    public void testConstructor(){
        try {
            CancellableTask task = new CancellableTask(null);
            shouldThrow();
        }
        catch(NullPointerException success) {
        }
    }
    
    /**
     * isDone is true after task is run
     */
    public void testIsDone(){
        CancellableTask task = new CancellableTask(new NoOpRunnable());
	task.run();
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    /**
     * Cancelling before running succeeds
     */
    public void testCancelBeforeRun() {
        CancellableTask task = new CancellableTask(new NoOpRunnable());
        assertTrue(task.cancel(false));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    /**
     * reset of a done task succeeds and changes status to not done
     */
    public void testReset(){
        PublicCancellableTask task = new PublicCancellableTask(new NoOpRunnable());
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.reset());
	assertFalse(task.isDone());
    }

    /**
     * Resetting after cancellation fails
     */
    public void testResetAfterCancel() {
        PublicCancellableTask task = new PublicCancellableTask(new NoOpRunnable());
        assertTrue(task.cancel(false));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
        assertFalse(task.reset());
    }

    /**
     * Setting the runnable causes it to be used
     */
    public void testSetRunnable() {
        PublicCancellableTask task = new PublicCancellableTask();
        assertNull(task.getRunnable());
        Runnable r = new NoOpRunnable();
        task.setRunnable(r);
        assertEquals(r, task.getRunnable());
        assertTrue(task.cancel(false));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
        assertFalse(task.reset());
    }

    /**
     * setDone of new task causes isDone to be true
     */
    public void testSetDone() {
        PublicCancellableTask task = new PublicCancellableTask(new NoOpRunnable());
	task.setDone();
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    /**
     * setCancelled of a new task causes isCancelled to be true
     */
    public void testSetCancelled() {
        PublicCancellableTask task = new PublicCancellableTask(new NoOpRunnable());
        assertTrue(task.cancel(false));
	task.setCancelled();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    /**
     * Cancel(true) before run succeeds
     */
    public void testCancelBeforeRun2() {
        CancellableTask task = new CancellableTask(new NoOpRunnable());
        assertTrue(task.cancel(true));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    /**
     * cancel of a completed task fails
     */
    public void testCancelAfterRun() {
        CancellableTask task = new CancellableTask(new NoOpRunnable());
	task.run();
        assertFalse(task.cancel(false));
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    /**
     * cancel(true) interrupts a running task
     */
    public void testCancelInterrupt(){
        CancellableTask task = new CancellableTask(new SmallInterruptedRunnable());
        Thread t = new  Thread(task);
        
        try{
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(task.cancel(true));
            t.join();
            assertTrue(task.isDone());
            assertTrue(task.isCancelled());
        } catch(InterruptedException e){
            unexpectedException();
        }
    }


    /**
     * cancel(false) does not interrupt a running task
     */
    public void testCancelNoInterrupt(){
        CancellableTask task = new CancellableTask(new SmallRunnable());
        Thread t = new  Thread(task);
        try{
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(task.cancel(false));
            t.join();
            assertTrue(task.isDone());
            assertTrue(task.isCancelled());
        } catch(InterruptedException e){
            unexpectedException();
        }
    }


}
