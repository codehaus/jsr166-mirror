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
    static class MyCancellableTask extends CancellableTask {
        public MyCancellableTask() {}
        public MyCancellableTask(Runnable r) { super(r); }
        public boolean reset() { return super.reset(); }
        public Runnable getRunnable() { return super.getRunnable(); }
        public void setRunnable(Runnable r) { super.setRunnable(r); }
        public void setCancelled() { super.setCancelled(); }
        public void setDone() { super.setDone(); }
    }

    public void testConstructor(){
        try {
            CancellableTask task = new CancellableTask(null);
            fail("should throw");
        }
        catch(NullPointerException success) {
        }
    }
    
    public void testIsDone(){
        CancellableTask task = new CancellableTask(new NoOpRunnable());
	task.run();
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    public void testReset(){
        MyCancellableTask task = new MyCancellableTask(new NoOpRunnable());
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.reset());
    }

    public void testCancelBeforeRun() {
        CancellableTask task = new CancellableTask(new NoOpRunnable());
        assertTrue(task.cancel(false));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    public void testResetAfterCancel() {
        MyCancellableTask task = new MyCancellableTask(new NoOpRunnable());
        assertTrue(task.cancel(false));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
        assertFalse(task.reset());
    }

    public void testSetRunnable() {
        MyCancellableTask task = new MyCancellableTask();
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

    public void testSetDone() {
        MyCancellableTask task = new MyCancellableTask(new NoOpRunnable());
	task.setDone();
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    public void testSetCancelled() {
        MyCancellableTask task = new MyCancellableTask(new NoOpRunnable());
        assertTrue(task.cancel(false));
	task.setCancelled();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    public void testCancelBeforeRun2() {
        CancellableTask task = new CancellableTask(new NoOpRunnable());
        assertTrue(task.cancel(true));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    public void testCancelAfterRun() {
        CancellableTask task = new CancellableTask(new NoOpRunnable());
	task.run();
        assertFalse(task.cancel(false));
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

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
            fail("unexpected exception");
        }
    }


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
            fail("unexpected exception");
        }
    }


}
