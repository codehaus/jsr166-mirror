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


    /**
     * execute successfully executes a runnable
     */
    public void testExecute() {
	try {
            TrackedRunnable runnable =new TrackedRunnable();
            ScheduledExecutor p1 = new ScheduledExecutor(1);
	    p1.execute(runnable);
	    assertFalse(runnable.done);
	    Thread.sleep(SHORT_DELAY_MS);
	    p1.shutdown();
	    try {
                Thread.sleep(MEDIUM_DELAY_MS);
            } catch(InterruptedException e){
                unexpectedException();
            }
	    assertTrue(runnable.done);
            p1.shutdown();
            joinPool(p1);
        }
	catch(Exception e){
            unexpectedException();
        }
        
    }


    /**
     * delayed schedule of callable successfully executes after delay
     */
    public void testSchedule1() {
	try {
            TrackedCallable callable = new TrackedCallable();
            ScheduledExecutor p1 = new ScheduledExecutor(1);
	    Future f = p1.schedule(callable, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
	    assertFalse(callable.done);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(callable.done);
	    assertEquals(Boolean.TRUE, f.get());
            p1.shutdown();
            joinPool(p1);
	} catch(RejectedExecutionException e){}
	catch(Exception e){
            unexpectedException();
        }
    }

    /**
     *  delayed schedule of runnable successfully executes after delay
     */
    public void testSchedule3() {
	try {
            TrackedRunnable runnable = new TrackedRunnable();
            ScheduledExecutor p1 = new ScheduledExecutor(1);
	    p1.schedule(runnable, SMALL_DELAY_MS, TimeUnit.MILLISECONDS);
	    Thread.sleep(SHORT_DELAY_MS);
	    assertFalse(runnable.done);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(runnable.done);
            p1.shutdown();
            joinPool(p1);
        } catch(Exception e){
            unexpectedException();
        }
    }
    
    /**
     * scheduleAtFixedRate executes runnable after given initial delay
     */
    public void testSchedule4() {
	try {
            TrackedRunnable runnable = new TrackedRunnable();
            ScheduledExecutor p1 = new ScheduledExecutor(1);
	    ScheduledCancellable h = p1.scheduleAtFixedRate(runnable, SHORT_DELAY_MS, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
	    assertFalse(runnable.done);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(runnable.done);
            h.cancel(true);
            p1.shutdown();
            joinPool(p1);
        } catch(Exception e){
            unexpectedException();
        }
    }

    /**
     * scheduleWithFixedDelay executes runnable after given initial delay
     */
    public void testSchedule5() {
	try {
            TrackedRunnable runnable = new TrackedRunnable();
            ScheduledExecutor p1 = new ScheduledExecutor(1);
	    ScheduledCancellable h = p1.scheduleWithFixedDelay(runnable, SHORT_DELAY_MS, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
	    assertFalse(runnable.done);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(runnable.done);
            h.cancel(true);
            p1.shutdown();
            joinPool(p1);
        } catch(Exception e){
            unexpectedException();
        }
    }
    
    /**
     *  execute (null) throws NPE
     */
    public void testExecuteNull() {
        ScheduledExecutor se = null;
        try {
	    se = new ScheduledExecutor(1);
	    se.execute(null);
            shouldThrow();
	} catch(NullPointerException success){}
	catch(Exception e){
            unexpectedException();
        }
	
	joinPool(se);
    }

    /**
     * schedule (null) throws NPE
     */
    public void testScheduleNull() {
        ScheduledExecutor se = new ScheduledExecutor(1);
	try {
            TrackedCallable callable = null;
	    Future f = se.schedule(callable, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
            shouldThrow();
	} catch(NullPointerException success){}
	catch(Exception e){
            unexpectedException();
        }
	joinPool(se);
    }
   
    /**
     * execute throws RejectedExecutionException if shutdown
     */
    public void testSchedule1_RejectedExecutionException() {
        ScheduledExecutor se = new ScheduledExecutor(1);
        try {
            se.shutdown();
            se.schedule(new NoOpRunnable(),
                        MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            shouldThrow();
        } catch(RejectedExecutionException success){
        }
        joinPool(se);

    }

    /**
     * schedule throws RejectedExecutionException if shutdown
     */
    public void testSchedule2_RejectedExecutionException() {
        ScheduledExecutor se = new ScheduledExecutor(1);
        try {
            se.shutdown();
            se.schedule(new NoOpCallable(),
                        MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            shouldThrow();
        } catch(RejectedExecutionException success){
        }
        joinPool(se);
    }

    /**
     * schedule callable throws RejectedExecutionException if shutdown
     */
     public void testSchedule3_RejectedExecutionException() {
         ScheduledExecutor se = new ScheduledExecutor(1);
         try {
            se.shutdown();
            se.schedule(new NoOpCallable(),
                        MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            shouldThrow();
        } catch(RejectedExecutionException success){
        } 
         joinPool(se);
    }

    /**
     *  scheduleAtFixedRate throws RejectedExecutionException if shutdown
     */
    public void testScheduleAtFixedRate1_RejectedExecutionException() {
        ScheduledExecutor se = new ScheduledExecutor(1);
        try {
            se.shutdown();
            se.scheduleAtFixedRate(new NoOpRunnable(),
                                   MEDIUM_DELAY_MS, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            shouldThrow();
        } catch(RejectedExecutionException success){
        } 
        joinPool(se);
    }
    
    /**
     * scheduleWithFixedDelay throws RejectedExecutionException if shutdown
     */
    public void testScheduleWithFixedDelay1_RejectedExecutionException() {
        ScheduledExecutor se = new ScheduledExecutor(1);
        try {
            se.shutdown();
            se.scheduleWithFixedDelay(new NoOpRunnable(),
                                      MEDIUM_DELAY_MS, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
            shouldThrow();
        } catch(RejectedExecutionException success){
        } 
        joinPool(se);
    }

    /**
     *  getActiveCount increases but doesn't overestimate, when a
     *  thread becomes active
     */
    public void testGetActiveCount() {
        ScheduledExecutor p2 = new ScheduledExecutor(2);
        assertEquals(0, p2.getActiveCount());
        p2.execute(new SmallRunnable());
        try {
            Thread.sleep(SHORT_DELAY_MS);
        } catch(Exception e){
            unexpectedException();
        }
        assertEquals(1, p2.getActiveCount());
        joinPool(p2);
    }
    
    /**
     *    getCompletedTaskCount increases, but doesn't overestimate,
     *   when tasks complete
     */
    public void testGetCompletedTaskCount() {
        ScheduledExecutor p2 = new ScheduledExecutor(2);
        assertEquals(0, p2.getCompletedTaskCount());
        p2.execute(new SmallRunnable());
        try {
            Thread.sleep(MEDIUM_DELAY_MS);
        } catch(Exception e){
            unexpectedException();
        }
        assertEquals(1, p2.getCompletedTaskCount());
        joinPool(p2);
    }
    
    /**
     *  getCorePoolSize returns size given in constructor if not otherwise set 
     */
    public void testGetCorePoolSize() {
        ScheduledExecutor p1 = new ScheduledExecutor(1);
        assertEquals(1, p1.getCorePoolSize());
        joinPool(p1);
    }
    
    /**
     *    getLargestPoolSize increases, but doesn't overestimate, when
     *   multiple threads active
     */
    public void testGetLargestPoolSize() {
        ScheduledExecutor p2 = new ScheduledExecutor(2);
        assertEquals(0, p2.getLargestPoolSize());
        p2.execute(new SmallRunnable());
        p2.execute(new SmallRunnable());
        try {
            Thread.sleep(SHORT_DELAY_MS);
        } catch(Exception e){
            unexpectedException();
        }
        assertEquals(2, p2.getLargestPoolSize());
        joinPool(p2);
    }
    
    /**
     *   getPoolSize increases, but doesn't overestimate, when threads
     *   become active
     */
    public void testGetPoolSize() {
        ScheduledExecutor p1 = new ScheduledExecutor(1);
        assertEquals(0, p1.getPoolSize());
        p1.execute(new SmallRunnable());
        assertEquals(1, p1.getPoolSize());
        joinPool(p1);
    }
    
    /**
     *    getTaskCount increases, but doesn't overestimate, when tasks
     *    submitted
     */
    public void testGetTaskCount() {
        ScheduledExecutor p1 = new ScheduledExecutor(1);
        assertEquals(0, p1.getTaskCount());
        for(int i = 0; i < 5; i++)
            p1.execute(new SmallRunnable());
        try {
            Thread.sleep(SHORT_DELAY_MS);
        } catch(Exception e){
            unexpectedException();
        }
        assertEquals(5, p1.getTaskCount());
        joinPool(p1);
    }
    
    /**
     *   is isShutDown is false before shutdown, true after
     */
    public void testIsShutdown() {
        
	ScheduledExecutor p1 = new ScheduledExecutor(1);
        try {
            assertFalse(p1.isShutdown());
        }
        finally {
            p1.shutdown();
        }
	assertTrue(p1.isShutdown());
    }

        
    /**
     *   isTerminated is false before termination, true after
     */
    public void testIsTerminated() {
	ScheduledExecutor p1 = new ScheduledExecutor(1);
        try {
            p1.execute(new SmallRunnable());
        } finally {
            p1.shutdown();
        }
        try {
	    assertTrue(p1.awaitTermination(LONG_DELAY_MS, TimeUnit.MILLISECONDS));
            assertTrue(p1.isTerminated());
	} catch(Exception e){
            unexpectedException();
        }	
    }

    /**
     *  isTerminating is not true when running or when terminated
     */
    public void testIsTerminating() {
	ScheduledExecutor p1 = new ScheduledExecutor(1);
        assertFalse(p1.isTerminating());
        try {
            p1.execute(new SmallRunnable());
            assertFalse(p1.isTerminating());
        } finally {
            p1.shutdown();
        }
        try {
	    assertTrue(p1.awaitTermination(LONG_DELAY_MS, TimeUnit.MILLISECONDS));
            assertTrue(p1.isTerminated());
            assertFalse(p1.isTerminating());
	} catch(Exception e){
            unexpectedException();
        }	
    }

    /**
     *   purge removes cancelled tasks from the queue
     */
    public void testPurge() {
        ScheduledExecutor p1 = new ScheduledExecutor(1);
        ScheduledCancellable[] tasks = new ScheduledCancellable[5];
        for(int i = 0; i < 5; i++){
            tasks[i] = p1.schedule(new SmallRunnable(), 1, TimeUnit.MILLISECONDS);
        }
        int max = 5;
        if (tasks[4].cancel(true)) --max;
        if (tasks[3].cancel(true)) --max;
        p1.purge();
        long count = p1.getTaskCount();
        assertTrue(count > 0 && count <= max);
        joinPool(p1);
    }

    /**
     *  shutDownNow returns a list containing tasks that were not run
     */
    public void testShutDownNow() {
	ScheduledExecutor p1 = new ScheduledExecutor(1);
        for(int i = 0; i < 5; i++)
            p1.schedule(new SmallRunnable(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
        List l = p1.shutdownNow();
	assertTrue(p1.isShutdown());
	assertTrue(l.size() > 0 && l.size() <= 5);
        joinPool(p1);
    }

    /**
     * In default setting, shutdown cancels periodic but not delayed
     * tasks at shutdown
     */
    public void testShutDown1() {
        try {
            ScheduledExecutor p1 = new ScheduledExecutor(1);
            assertTrue(p1.getExecuteExistingDelayedTasksAfterShutdownPolicy());
            assertFalse(p1.getContinueExistingPeriodicTasksAfterShutdownPolicy());

            ScheduledCancellable[] tasks = new ScheduledCancellable[5];
            for(int i = 0; i < 5; i++)
                tasks[i] = p1.schedule(new NoOpRunnable(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
            p1.shutdown();
            BlockingQueue q = p1.getQueue();
            for (Iterator it = q.iterator(); it.hasNext();) {
                ScheduledCancellable t = (ScheduledCancellable)it.next();
                assertFalse(t.isCancelled());
            }
            assertTrue(p1.isShutdown());
            Thread.sleep(SMALL_DELAY_MS);
            for (int i = 0; i < 5; ++i) {
                assertTrue(tasks[i].isDone());
                assertFalse(tasks[i].isCancelled());
            }
            
        }
        catch(Exception ex) {
            unexpectedException();
        }
    }


    /**
     * If setExecuteExistingDelayedTasksAfterShutdownPolicy is false,
     * delayed tasks are cancelled at shutdown
     */
    public void testShutDown2() {
        try {
            ScheduledExecutor p1 = new ScheduledExecutor(1);
            p1.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            ScheduledCancellable[] tasks = new ScheduledCancellable[5];
            for(int i = 0; i < 5; i++)
                tasks[i] = p1.schedule(new NoOpRunnable(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
            p1.shutdown();
            assertTrue(p1.isShutdown());
            BlockingQueue q = p1.getQueue();
            assertTrue(q.isEmpty());
            Thread.sleep(SMALL_DELAY_MS);
            assertTrue(p1.isTerminated());
        }
        catch(Exception ex) {
            unexpectedException();
        }
    }


    /**
     * If setContinueExistingPeriodicTasksAfterShutdownPolicy is set false,
     * periodic tasks are not cancelled at shutdown
     */
    public void testShutDown3() {
        try {
            ScheduledExecutor p1 = new ScheduledExecutor(1);
            p1.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            ScheduledCancellable task =
                p1.scheduleAtFixedRate(new NoOpRunnable(), 5, 5, TimeUnit.MILLISECONDS);
            p1.shutdown();
            assertTrue(p1.isShutdown());
            BlockingQueue q = p1.getQueue();
            assertTrue(q.isEmpty());
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(p1.isTerminated());
        }
        catch(Exception ex) {
            unexpectedException();
        }
    }

    /**
     * if setContinueExistingPeriodicTasksAfterShutdownPolicy is true,
     * periodic tasks are cancelled at shutdown
     */
    public void testShutDown4() {
        ScheduledExecutor p1 = new ScheduledExecutor(1);
        try {
            p1.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
            ScheduledCancellable task =
                p1.scheduleAtFixedRate(new NoOpRunnable(), 5, 5, TimeUnit.MILLISECONDS);
            assertFalse(task.isCancelled());
            p1.shutdown();
            assertFalse(task.isCancelled());
            assertFalse(p1.isTerminated());
            assertTrue(p1.isShutdown());
            Thread.sleep(SHORT_DELAY_MS);
            assertFalse(task.isCancelled());
            task.cancel(true);
            assertTrue(task.isCancelled());
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(p1.isTerminated());
        }
        catch(Exception ex) {
            unexpectedException();
        }
        finally {
            p1.shutdownNow();
        }
    }

}
