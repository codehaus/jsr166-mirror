/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class CancellableTaskTest extends TestCase{
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(CancellableTaskTest.class);
    }

    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 

    
    public void testIsDone(){
        CancellableTask task = new CancellableTask(new Runnable() {
                public void run() {} });
	task.run();
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    public void testCancelBeforeRun() {
        CancellableTask task = new CancellableTask(new Runnable() {
                public void run() {} });
        assertTrue(task.cancel(false));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    public void testCancelBeforeRun2() {
        CancellableTask task = new CancellableTask(new Runnable() {
                public void run() {} });
        assertTrue(task.cancel(true));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    public void testCancelAfterRun() {
        CancellableTask task = new CancellableTask(new Runnable() {
                public void run() {} });
	task.run();
        assertFalse(task.cancel(false));
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    public void testCancelInterrupt(){
        CancellableTask task = new CancellableTask(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(SHORT_DELAY_MS* 2);
                        fail("should throw");
                    }
                    catch (InterruptedException success) {}
                } });
        Thread t = new  Thread(task);
        t.start();
        
        try{
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
        CancellableTask task = new CancellableTask(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(SHORT_DELAY_MS* 2);
                    }
                    catch (InterruptedException success) {
                        fail("should not interrupt");
                    }
                } });
        Thread t = new  Thread(task);
        t.start();
        
        try{
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
