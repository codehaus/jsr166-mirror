/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class ScheduledExecutorTest extends JSR166TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(ScheduledExecutorTest.class);
    }

    static class MyRunnable implements Runnable {
        volatile boolean done = false;
        public void run(){
            try{
                Thread.sleep(SMALL_DELAY_MS);
                done = true;
            } catch(Exception e){
            }
        }
    }

    static class MyCallable implements Callable {
        volatile boolean done = false;
        public Object call(){
            try{
                Thread.sleep(SMALL_DELAY_MS);
                done = true;
            }catch(Exception e){}
            return Boolean.TRUE;
        }
    }

    public void testExecute(){
	try{
            MyRunnable runnable =new MyRunnable();
            ScheduledExecutor one = new ScheduledExecutor(1);
	    one.execute(runnable);
	    assertFalse(runnable.done);
	    Thread.sleep(SHORT_DELAY_MS);
	    one.shutdown();
	    try{
                Thread.sleep(MEDIUM_DELAY_MS);
            } catch(InterruptedException e){
                fail("unexpected exception");
            }
	    assertTrue(runnable.done);
            one.shutdown();
            joinPool(one);
        }
	catch(Exception e){
            fail("unexpected exception");
        }
        
    }

    public void testSchedule1(){
	try{
            MyCallable callable = new MyCallable();
            ScheduledExecutor one = new ScheduledExecutor(1);
	    Future f = one.schedule(callable, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
	    assertFalse(callable.done);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(callable.done);
	    assertEquals(Boolean.TRUE, f.get());
            one.shutdown();
            joinPool(one);
	}catch(RejectedExecutionException e){}
	catch(Exception e){
            fail("unexpected exception");
        }
    }

    /**
     *  Another version of schedule, only using Runnable instead of Callable
     */
    public void testSchedule3(){
	try{
            MyRunnable runnable = new MyRunnable();
            ScheduledExecutor one = new ScheduledExecutor(1);
	    one.schedule(runnable, SMALL_DELAY_MS, TimeUnit.MILLISECONDS);
	    Thread.sleep(SHORT_DELAY_MS);
	    assertFalse(runnable.done);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(runnable.done);
            one.shutdown();
            joinPool(one);
        } catch(Exception e){
            fail("unexpected exception");
        }
    }
    
    /**
     *  The final version of schedule, using both long, TimeUnit and Runnable
     */
    public void testSchedule4(){
	try{
            MyRunnable runnable = new MyRunnable();
            ScheduledExecutor one = new ScheduledExecutor(1);
	    one.schedule(runnable, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
	    assertFalse(runnable.done);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(runnable.done);
            one.shutdown();
            joinPool(one);
        } catch(Exception e){
            fail("unexpected exception");
        }
    }
    
   
    // exception tests

    /**
     *   schedule(Runnable, long) throws RejectedExecutionException
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testSchedule1_RejectedExecutionException(){
        ScheduledExecutor se = new ScheduledExecutor(1);
        try{
            se.shutdown();
            se.schedule(new NoOpRunnable(),
                        MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            fail("shoud throw");
        } catch(RejectedExecutionException success){
        }
        joinPool(se);

    }

    /**
     *   schedule(Callable, long, TimeUnit) throws RejectedExecutionException
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testSchedule2_RejectedExecutionException(){
        ScheduledExecutor se = new ScheduledExecutor(1);
        try{
            se.shutdown();
            se.schedule(new NoOpCallable(),
                        MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            fail("should throw");
        } catch(RejectedExecutionException success){
        }
        joinPool(se);
    }

    /**
     *   schedule(Callable, long) throws RejectedExecutionException
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
     public void testSchedule3_RejectedExecutionException(){
         ScheduledExecutor se = new ScheduledExecutor(1);
         try{
            se.shutdown();
            se.schedule(new NoOpCallable(),
                        MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            fail("should throw");
        } catch(RejectedExecutionException success){
        } 
         joinPool(se);
    }

    /**
     *   scheduleAtFixedRate(Runnable, long, long, TimeUnit) throws 
     *  RejectedExecutionException.
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testScheduleAtFixedRate1_RejectedExecutionException(){
        ScheduledExecutor se = new ScheduledExecutor(1);
        try{
            se.shutdown();
            se.scheduleAtFixedRate(new NoOpRunnable(),
                                   MEDIUM_DELAY_MS, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            fail("should throw");
        } catch(RejectedExecutionException success){
        } 
        joinPool(se);
    }
    
    /**
     *   scheduleAtFixedRate(Runnable, long, long, TimeUnit) throws 
     *  RejectedExecutionException.
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testScheduleAtFixedRate2_RejectedExecutionException(){
        ScheduledExecutor se = new ScheduledExecutor(1);
        try{
            se.shutdown();
            se.scheduleAtFixedRate(new NoOpRunnable(),
                                   1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            fail("should throw");
        } catch(RejectedExecutionException success){
        } 
        joinPool(se);
    }

    /**
     *   scheduleWithFixedDelay(Runnable, long, long, TimeUnit) throws 
     *  RejectedExecutionException.
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testScheduleWithFixedDelay1_RejectedExecutionException(){
        ScheduledExecutor se = new ScheduledExecutor(1);
        try{
            se.shutdown();
            se.scheduleWithFixedDelay(new NoOpRunnable(),
                                      MEDIUM_DELAY_MS, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            fail("should throw");
        } catch(RejectedExecutionException success){
        } 
        joinPool(se);
    }

    /**
     *   scheduleWithFixedDelay(Runnable, long, long, TimeUnit) throws 
     *  RejectedExecutionException.
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
     public void testScheduleWithFixedDelay2_RejectedExecutionException(){
         ScheduledExecutor se = new ScheduledExecutor(1);
        try{
            se.shutdown();
            se.scheduleWithFixedDelay(new NoOpRunnable(),
                                      1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            fail("should throw");
        } catch(RejectedExecutionException success){
        } 
        joinPool(se);
    }

    /**
     *   execute throws RejectedExecutionException
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testExecute_RejectedExecutionException(){
        ScheduledExecutor se = new ScheduledExecutor(1);
        try{
            se.shutdown();
            se.execute(new NoOpRunnable());
            fail("should throw");
        } catch(RejectedExecutionException success){
        } 
        joinPool(se);
    }

    /**
     *   getActiveCount gives correct values
     */
    public void testGetActiveCount(){
        ScheduledExecutor two = new ScheduledExecutor(2);
        assertEquals(0, two.getActiveCount());
        two.execute(new SmallRunnable());
        try{
            Thread.sleep(SHORT_DELAY_MS);
        } catch(Exception e){
            fail("unexpected exception");
        }
        assertEquals(1, two.getActiveCount());
        joinPool(two);
    }
    
    /**
     *   getCompleteTaskCount gives correct values
     */
    public void testGetCompletedTaskCount(){
        ScheduledExecutor two = new ScheduledExecutor(2);
        assertEquals(0, two.getCompletedTaskCount());
        two.execute(new SmallRunnable());
        try{
            Thread.sleep(MEDIUM_DELAY_MS);
        } catch(Exception e){
            fail("unexpected exception");
        }
        assertEquals(1, two.getCompletedTaskCount());
        joinPool(two);
    }
    
    /**
     *   getCorePoolSize gives correct values
     */
    public void testGetCorePoolSize(){
        ScheduledExecutor one = new ScheduledExecutor(1);
        assertEquals(1, one.getCorePoolSize());
        joinPool(one);
    }
    
    /**
     *   getLargestPoolSize gives correct values
     */
    public void testGetLargestPoolSize(){
        ScheduledExecutor two = new ScheduledExecutor(2);
        assertEquals(0, two.getLargestPoolSize());
        two.execute(new SmallRunnable());
        two.execute(new SmallRunnable());
        try{
            Thread.sleep(SHORT_DELAY_MS);
        } catch(Exception e){
            fail("unexpected exception");
        }
        assertEquals(2, two.getLargestPoolSize());
        joinPool(two);
    }
    
    /**
     *   getPoolSize gives correct values
     */
    public void testGetPoolSize(){
        ScheduledExecutor one = new ScheduledExecutor(1);
        assertEquals(0, one.getPoolSize());
        one.execute(new SmallRunnable());
        assertEquals(1, one.getPoolSize());
        joinPool(one);
    }
    
    /**
     *   getTaskCount gives correct values
     */
    public void testGetTaskCount(){
        ScheduledExecutor one = new ScheduledExecutor(1);
        assertEquals(0, one.getTaskCount());
        for(int i = 0; i < 5; i++)
            one.execute(new SmallRunnable());
        try{
            Thread.sleep(SHORT_DELAY_MS);
        } catch(Exception e){
            fail("unexpected exception");
        }
        assertEquals(5, one.getTaskCount());
        joinPool(one);
    }
    
    /**
     *   isShutDown gives correct values
     */
    public void testIsShutdown(){
        
	ScheduledExecutor one = new ScheduledExecutor(1);
        try {
            assertFalse(one.isShutdown());
        }
        finally {
            one.shutdown();
        }
	assertTrue(one.isShutdown());
    }

        
    /**
     *   isTerminated gives correct values
     *  Makes sure termination does not take an innapropriate
     *  amount of time
     */
    public void testIsTerminated(){
	ScheduledExecutor one = new ScheduledExecutor(1);
        try {
            one.execute(new SmallRunnable());
        } finally {
            one.shutdown();
        }
        try {
	    assertTrue(one.awaitTermination(LONG_DELAY_MS, TimeUnit.MILLISECONDS));
            assertTrue(one.isTerminated());
	} catch(Exception e){
            fail("unexpected exception");
        }	
    }

    /**
     *   that purge correctly removes cancelled tasks
     *  from the queue
     */
    public void testPurge(){
        ScheduledExecutor one = new ScheduledExecutor(1);
        ScheduledCancellable[] tasks = new ScheduledCancellable[5];
        for(int i = 0; i < 5; i++){
            tasks[i] = one.schedule(new SmallRunnable(), 1, TimeUnit.MILLISECONDS);
        }
        int max = 5;
        if (tasks[4].cancel(true)) --max;
        if (tasks[3].cancel(true)) --max;
        one.purge();
        long count = one.getTaskCount();
        assertTrue(count > 0 && count <= max);
        joinPool(one);
    }

    /**
     *   shutDownNow returns a list
     *  containing the correct number of elements
     */
    public void testShutDownNow(){
	ScheduledExecutor one = new ScheduledExecutor(1);
        for(int i = 0; i < 5; i++)
            one.schedule(new SmallRunnable(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
        List l = one.shutdownNow();
	assertTrue(one.isShutdown());
	assertTrue(l.size() > 0 && l.size() <= 5);
        joinPool(one);
    }

    public void testShutDown1(){
        try {
            ScheduledExecutor one = new ScheduledExecutor(1);
            assertTrue(one.getExecuteExistingDelayedTasksAfterShutdownPolicy());
            assertFalse(one.getContinueExistingPeriodicTasksAfterShutdownPolicy());

            ScheduledCancellable[] tasks = new ScheduledCancellable[5];
            for(int i = 0; i < 5; i++)
                tasks[i] = one.schedule(new NoOpRunnable(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
            one.shutdown();
            BlockingQueue q = one.getQueue();
            for (Iterator it = q.iterator(); it.hasNext();) {
                ScheduledCancellable t = (ScheduledCancellable)it.next();
                assertFalse(t.isCancelled());
            }
            assertTrue(one.isShutdown());
            Thread.sleep(SMALL_DELAY_MS);
            for (int i = 0; i < 5; ++i) {
                assertTrue(tasks[i].isDone());
                assertFalse(tasks[i].isCancelled());
            }
            
        }
        catch(Exception ex) {
            fail("unexpected exception");
        }
    }


    public void testShutDown2(){
        try {
            ScheduledExecutor one = new ScheduledExecutor(1);
            one.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            ScheduledCancellable[] tasks = new ScheduledCancellable[5];
            for(int i = 0; i < 5; i++)
                tasks[i] = one.schedule(new NoOpRunnable(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
            one.shutdown();
            assertTrue(one.isShutdown());
            BlockingQueue q = one.getQueue();
            assertTrue(q.isEmpty());
            Thread.sleep(SMALL_DELAY_MS);
            assertTrue(one.isTerminated());
        }
        catch(Exception ex) {
            fail("unexpected exception");
        }
    }


    public void testShutDown3(){
        try {
            ScheduledExecutor one = new ScheduledExecutor(1);
            one.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            ScheduledCancellable task =
                one.scheduleAtFixedRate(new NoOpRunnable(), 5, 5, TimeUnit.MILLISECONDS);
            one.shutdown();
            assertTrue(one.isShutdown());
            BlockingQueue q = one.getQueue();
            assertTrue(q.isEmpty());
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(one.isTerminated());
        }
        catch(Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testShutDown4(){
        ScheduledExecutor one = new ScheduledExecutor(1);
        try {
            one.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
            ScheduledCancellable task =
                one.scheduleAtFixedRate(new NoOpRunnable(), 5, 5, TimeUnit.MILLISECONDS);
            assertFalse(task.isCancelled());
            one.shutdown();
            assertFalse(task.isCancelled());
            assertFalse(one.isTerminated());
            assertTrue(one.isShutdown());
            Thread.sleep(SHORT_DELAY_MS);
            assertFalse(task.isCancelled());
            task.cancel(true);
            assertTrue(task.isCancelled());
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(one.isTerminated());
        }
        catch(Exception ex) {
            fail("unexpected exception");
        }
        finally {
            one.shutdownNow();
        }
    }

}
