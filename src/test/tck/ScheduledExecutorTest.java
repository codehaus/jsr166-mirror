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
                Thread.sleep(MEDIUM_DELAY_MS);
                waiting = false;
                done = true;
            } catch(Exception e){}
        }
    }

    static class MyCallable implements Callable {
        volatile boolean waiting = true;
        volatile boolean done = false;
        public Object call(){
            try{
                Thread.sleep(MEDIUM_DELAY_MS);
                waiting = false;
                done = true;
            }catch(Exception e){}
            return Boolean.TRUE;
        }
    }

    /**
     *  Test to verify execute successfully runs the given Runnable
     */
    public void testExecute(){
	try{
            MyRunnable runnable =new MyRunnable();
            ScheduledExecutor one = new ScheduledExecutor(1);
	    one.execute(runnable);
	    Thread.sleep(100);
	    assertTrue(runnable.waiting);
	    one.shutdown();
            // make sure the Runnable has time to complete
	    try{Thread.sleep(1010);}catch(InterruptedException e){}
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
	    Future f = one.schedule(callable, 500, TimeUnit.MILLISECONDS);
            //	    Thread.sleep(505);
	    assertTrue(callable.waiting);
	    Thread.sleep(2000);
	    assertTrue(callable.done);
	    assertEquals(Boolean.TRUE, f.get());
            one.shutdown();
	}catch(RejectedExecutionException e){}
	catch(Exception e){}
    }

    /**
     *  Another version of schedule, only using Runnable instead of Callable
     */
    public void testSchedule3(){
	try{
            MyRunnable runnable = new MyRunnable();
            ScheduledExecutor one = new ScheduledExecutor(1);
	    one.schedule(runnable, 500, TimeUnit.MILLISECONDS);
	    Thread.sleep(50);
	    assertTrue(runnable.waiting);
	    Thread.sleep(2000);
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
	    one.schedule(runnable, 500, TimeUnit.MILLISECONDS);
            //	    Thread.sleep(505);
	    assertTrue(runnable.waiting);
	    Thread.sleep(2000);
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

}
