/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class ScheduledExecutorTest extends TestCase{
    
    boolean flag = false;

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    

    public static Test suite() {
	return new TestSuite(ScheduledExecutorTest.class);
    }

    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 

    static class MyRunnable implements Runnable {
        volatile boolean waiting = true;
        volatile boolean done = false;
        public void run(){
            try{
                Thread.sleep(SHORT_DELAY_MS);
                waiting = false;
                done = true;
            } catch(Exception e){
            }
        }
    }

    static class MyCallable implements Callable {
        volatile boolean waiting = true;
        volatile boolean done = false;
        public Object call(){
            try{
                Thread.sleep(SHORT_DELAY_MS);
                waiting = false;
                done = true;
            }catch(Exception e){}
            return Boolean.TRUE;
        }
    }

    public Runnable newRunnable(){
	return new Runnable(){
		public void run(){
		    try{Thread.sleep(SHORT_DELAY_MS);
                    } catch(Exception e){
                    }
		}
	    };
    }

    public Runnable newNoopRunnable() {
	return new Runnable(){
		public void run(){
		}
	    };
    }

    /**
     *  Test to verify execute successfully runs the given Runnable
     */
    public void testExecute(){
	try{
            MyRunnable runnable =new MyRunnable();
            ScheduledExecutor one = new ScheduledExecutor(1);
	    one.execute(runnable);
	    Thread.sleep(SHORT_DELAY_MS/2);
	    assertTrue(runnable.waiting);
	    one.shutdown();
	    try{
                Thread.sleep(MEDIUM_DELAY_MS);
            } catch(InterruptedException e){
                fail("unexpected exception");
            }
            assertFalse(runnable.waiting);
	    assertTrue(runnable.done);
            one.shutdown();
        }
	catch(Exception e){
            fail("unexpected exception");
        }
    }

    /**
     *  Test to verify schedule successfully runs the given Callable.
     *  The waiting flag shows that the Callable is not started until
     *  immediately.
     */
    public void testSchedule1(){
	try{
            MyCallable callable = new MyCallable();
            ScheduledExecutor one = new ScheduledExecutor(1);
	    Future f = one.schedule(callable, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
	    assertTrue(callable.waiting);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(callable.done);
	    assertEquals(Boolean.TRUE, f.get());
            one.shutdown();
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
	    one.schedule(runnable, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
	    Thread.sleep(SHORT_DELAY_MS/2);
	    assertTrue(runnable.waiting);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(runnable.done);
            one.shutdown();
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
            //	    Thread.sleep(505);
	    assertTrue(runnable.waiting);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(runnable.done);
            one.shutdown();
        } catch(Exception e){
            fail("unexpected exception");
        }
    }
    
   
    // exception tests

    /**
     *  Test to verify schedule(Runnable, long) throws RejectedExecutionException
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testSchedule1_RejectedExecutionException(){
        try{
            ScheduledExecutor se = new ScheduledExecutor(1);
            se.shutdown();
            se.schedule(new Runnable(){
                    public void run(){}
                }, 10000, TimeUnit.MILLISECONDS);
            fail("shoud throw");
        }catch(RejectedExecutionException e){}    
    }

    /**
     *  Test to verify schedule(Callable, long, TimeUnit) throws RejectedExecutionException
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testSchedule2_RejectedExecutionException(){
        try{
            ScheduledExecutor se = new ScheduledExecutor(1);
            se.shutdown();
            se.schedule(new Callable(){
                    public Object call(){ 
                        return Boolean.TRUE;
                    }
                }, (long)100, TimeUnit.SECONDS);
            fail("should throw");
        }catch(RejectedExecutionException e){}    
    }

    /**
     *  Test to verify schedule(Callable, long) throws RejectedExecutionException
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
     public void testSchedule3_RejectedExecutionException(){
        try{
            ScheduledExecutor se = new ScheduledExecutor(1);
            se.shutdown();
            se.schedule(new Callable(){
                    public Object call(){ 
                        return Boolean.TRUE;
                    }
                },  10000, TimeUnit.MILLISECONDS);
            fail("should throw");
        }catch(RejectedExecutionException e){}    
    }

    /**
     *  Test to verify scheduleAtFixedRate(Runnable, long, long, TimeUnit) throws 
     *  RejectedExecutionException.
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testScheduleAtFixedRate1_RejectedExecutionException(){
        try{
            ScheduledExecutor se = new ScheduledExecutor(1);
            se.shutdown();
            se.scheduleAtFixedRate(new Runnable(){
                    public void run(){}
                }, 100, 100, TimeUnit.SECONDS);
            fail("should throw");
        }catch(RejectedExecutionException e){}    
    }
    
    /**
     *  Test to verify scheduleAtFixedRate(Runnable, long, long, TimeUnit) throws 
     *  RejectedExecutionException.
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testScheduleAtFixedRate2_RejectedExecutionException(){
        try{
            ScheduledExecutor se = new ScheduledExecutor(1);
            se.shutdown();
            se.scheduleAtFixedRate(new Runnable(){
                    public void run(){}
                },  1, 100, TimeUnit.SECONDS);
            fail("should throw");
        }catch(RejectedExecutionException e){}    
    }

    /**
     *  Test to verify scheduleWithFixedDelay(Runnable, long, long, TimeUnit) throws 
     *  RejectedExecutionException.
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testScheduleWithFixedDelay1_RejectedExecutionException(){
        try{
            ScheduledExecutor se = new ScheduledExecutor(1);
            se.shutdown();
            se.scheduleWithFixedDelay(new Runnable(){
                    public void run(){}
                }, 100, 100, TimeUnit.SECONDS);
            fail("should throw");
        }catch(RejectedExecutionException e){}    
    }

    /**
     *  Test to verify scheduleWithFixedDelay(Runnable, long, long, TimeUnit) throws 
     *  RejectedExecutionException.
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
     public void testScheduleWithFixedDelay2_RejectedExecutionException(){
        try{
            ScheduledExecutor se = new ScheduledExecutor(1);
            se.shutdown();
            se.scheduleWithFixedDelay(new Runnable(){
                    public void run(){}
                },  1, 100, TimeUnit.SECONDS);
            fail("should throw");
        }catch(RejectedExecutionException e){}    
    }

    /**
     *  Test to verify execute throws RejectedExecutionException
     *  This occurs on an attempt to schedule a task on a shutdown executor
     */
    public void testExecute_RejectedExecutionException(){
        try{
            ScheduledExecutor se = new ScheduledExecutor(1);
            se.shutdown();
            se.execute(new Runnable(){
                    public void run(){}
                });
            fail("should throw");
        }catch(RejectedExecutionException e){}    
    }



    /**
     *  Test to verify getActiveCount gives correct values
     */
    public void testGetActiveCount(){
        ScheduledExecutor two = new ScheduledExecutor(2);
        try {
            assertEquals(0, two.getActiveCount());
            two.execute(newRunnable());
            try{
                Thread.sleep(SHORT_DELAY_MS/2);
            } catch(Exception e){
                fail("unexpected exception");
            }
            assertEquals(1, two.getActiveCount());
        } finally {
            two.shutdown();
        }
    }
    
    /**
     *  Test to verify getCompleteTaskCount gives correct values
     */
    public void testGetCompletedTaskCount(){
        ScheduledExecutor two = new ScheduledExecutor(2);
        try {
            assertEquals(0, two.getCompletedTaskCount());
            two.execute(newRunnable());
            try{
                Thread.sleep(MEDIUM_DELAY_MS);
            } catch(Exception e){
                fail("unexpected exception");
            }
            assertEquals(1, two.getCompletedTaskCount());
        } finally {
            two.shutdown();
        }
    }
    
    /**
     *  Test to verify getCorePoolSize gives correct values
     */
    public void testGetCorePoolSize(){
        ScheduledExecutor one = new ScheduledExecutor(1);
        try {
            assertEquals(1, one.getCorePoolSize());
        } finally {
            one.shutdown();
        }
    }
    
    /**
     *  Test to verify getLargestPoolSize gives correct values
     */
    public void testGetLargestPoolSize(){
        ScheduledExecutor two = new ScheduledExecutor(2);
        try {
            assertEquals(0, two.getLargestPoolSize());
            two.execute(newRunnable());
            two.execute(newRunnable());
            try{
                Thread.sleep(SHORT_DELAY_MS);
            } catch(Exception e){
                fail("unexpected exception");
            }
            assertEquals(2, two.getLargestPoolSize());
        } finally {
            two.shutdown();
        }
    }
    
    /**
     *  Test to verify getPoolSize gives correct values
     */
    public void testGetPoolSize(){
        ScheduledExecutor one = new ScheduledExecutor(1);
        try { 
            assertEquals(0, one.getPoolSize());
            one.execute(newRunnable());
            assertEquals(1, one.getPoolSize());
        } finally {
            one.shutdown();
        }
    }
    
    /**
     *  Test to verify getTaskCount gives correct values
     */
    public void testGetTaskCount(){
        ScheduledExecutor one = new ScheduledExecutor(1);
        try {
            assertEquals(0, one.getTaskCount());
            for(int i = 0; i < 5; i++)
                one.execute(newRunnable());
            try{
                Thread.sleep(SHORT_DELAY_MS);
            } catch(Exception e){
                fail("unexpected exception");
            }
            assertEquals(5, one.getTaskCount());
        } finally {
            one.shutdown();
        }
    }
    
    /**
     *  Test to verify isShutDown gives correct values
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
     *  Test to verify isTerminated gives correct values
     *  Makes sure termination does not take an innapropriate
     *  amount of time
     */
    public void testIsTerminated(){
	ScheduledExecutor one = new ScheduledExecutor(1);
        try {
            one.execute(newRunnable());
        } finally {
            one.shutdown();
        }
	boolean flag = false;
	try{
	    flag = one.awaitTermination(10, TimeUnit.SECONDS);
	} catch(Exception e){
            fail("unexpected exception");
        }	
	assertTrue(one.isTerminated());
	if(!flag)
	    fail("ThreadPoolExecutor - thread pool did not terminate within suitable timeframe");
    }

    /**
     *  Test to verify that purge correctly removes cancelled tasks
     *  from the queue
     */
    public void testPurge(){
        ScheduledExecutor one = new ScheduledExecutor(1);
        try {
            ScheduledCancellable[] tasks = new ScheduledCancellable[5];
            for(int i = 0; i < 5; i++){
                tasks[i] = one.schedule(newRunnable(), 1, TimeUnit.MILLISECONDS);
            }
            int max = 5;
            if (tasks[4].cancel(true)) --max;
            if (tasks[3].cancel(true)) --max;
            one.purge();
            long count = one.getTaskCount();
            assertTrue(count > 0 && count <= max);
        } finally {
            one.shutdown();
        }
    }

    /**
     *  Test to verify shutDownNow returns a list
     *  containing the correct number of elements
     */
    public void testShutDownNow(){
	ScheduledExecutor one = new ScheduledExecutor(1);
        for(int i = 0; i < 5; i++)
            one.schedule(newRunnable(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
        List l = one.shutdownNow();
	assertTrue(one.isShutdown());
	assertTrue(l.size() > 0 && l.size() <= 5);
    }

    public void testShutDown1(){
        try {
            ScheduledExecutor one = new ScheduledExecutor(1);
            assertTrue(one.getExecuteExistingDelayedTasksAfterShutdownPolicy());
            assertFalse(one.getContinueExistingPeriodicTasksAfterShutdownPolicy());

            ScheduledCancellable[] tasks = new ScheduledCancellable[5];
            for(int i = 0; i < 5; i++)
                tasks[i] = one.schedule(newNoopRunnable(), SHORT_DELAY_MS/2, TimeUnit.MILLISECONDS);
            one.shutdown();
            BlockingQueue q = one.getQueue();
            for (Iterator it = q.iterator(); it.hasNext();) {
                ScheduledCancellable t = (ScheduledCancellable)it.next();
                assertFalse(t.isCancelled());
            }
            assertTrue(one.isShutdown());
            Thread.sleep(SHORT_DELAY_MS);
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
                tasks[i] = one.schedule(newNoopRunnable(), SHORT_DELAY_MS/2, TimeUnit.MILLISECONDS);
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


    public void testShutDown3(){
        try {
            ScheduledExecutor one = new ScheduledExecutor(1);
            one.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            ScheduledCancellable task =
                one.scheduleAtFixedRate(newNoopRunnable(), 5, 5, TimeUnit.MILLISECONDS);
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
                one.scheduleAtFixedRate(newNoopRunnable(), 5, 5, TimeUnit.MILLISECONDS);
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
