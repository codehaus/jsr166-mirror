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
            TrackedShortRunnable runnable =new TrackedShortRunnable();
            ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
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
            ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
	    Future f = p1.schedule(callable, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
	    assertFalse(callable.done);
	    Thread.sleep(MEDIUM_DELAY_MS);
	    assertTrue(callable.done);
	    assertEquals(Boolean.TRUE, f.get());
            p1.shutdown();
            joinPool(p1);
	} catch(RejectedExecutionException e){}
	catch(Exception e){
            e.printStackTrace();
            unexpectedException();
        }
    }

    /**
     *  delayed schedule of runnable successfully executes after delay
     */
    public void testSchedule3() {
	try {
            TrackedShortRunnable runnable = new TrackedShortRunnable();
            ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
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
            TrackedShortRunnable runnable = new TrackedShortRunnable();
            ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
	    ScheduledFuture h = p1.scheduleAtFixedRate(runnable, SHORT_DELAY_MS, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
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
            TrackedShortRunnable runnable = new TrackedShortRunnable();
            ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
	    ScheduledFuture h = p1.scheduleWithFixedDelay(runnable, SHORT_DELAY_MS, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
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
        ScheduledThreadPoolExecutor se = null;
        try {
	    se = new ScheduledThreadPoolExecutor(1);
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
        ScheduledThreadPoolExecutor se = new ScheduledThreadPoolExecutor(1);
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
        ScheduledThreadPoolExecutor se = new ScheduledThreadPoolExecutor(1);
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
        ScheduledThreadPoolExecutor se = new ScheduledThreadPoolExecutor(1);
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
         ScheduledThreadPoolExecutor se = new ScheduledThreadPoolExecutor(1);
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
        ScheduledThreadPoolExecutor se = new ScheduledThreadPoolExecutor(1);
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
        ScheduledThreadPoolExecutor se = new ScheduledThreadPoolExecutor(1);
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
        ScheduledThreadPoolExecutor p2 = new ScheduledThreadPoolExecutor(2);
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
        ScheduledThreadPoolExecutor p2 = new ScheduledThreadPoolExecutor(2);
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
        ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
        assertEquals(1, p1.getCorePoolSize());
        joinPool(p1);
    }
    
    /**
     *    getLargestPoolSize increases, but doesn't overestimate, when
     *   multiple threads active
     */
    public void testGetLargestPoolSize() {
        ScheduledThreadPoolExecutor p2 = new ScheduledThreadPoolExecutor(2);
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
        ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
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
        ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
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
     * getThreadFactory returns factory in constructor if not set
     */
    public void testGetThreadFactory() {
        ThreadFactory tf = new SimpleThreadFactory();
	ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1, tf);
        assertSame(tf, p.getThreadFactory());
        p.shutdown();
        joinPool(p);
    }

    /** 
     * setThreadFactory sets the thread factory returned by getThreadFactory
     */
    public void testSetThreadFactory() {
        ThreadFactory tf = new SimpleThreadFactory();
	ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        p.setThreadFactory(tf);
        assertSame(tf, p.getThreadFactory());
        p.shutdown();
        joinPool(p);
    }

    /** 
     * setThreadFactory(null) throws NPE
     */
    public void testSetThreadFactoryNull() {
	ScheduledThreadPoolExecutor p = new ScheduledThreadPoolExecutor(1);
        try {
            p.setThreadFactory(null);
            shouldThrow();
        } catch (NullPointerException success) {
        } finally {
            joinPool(p);
        }
    }
    
    /**
     *   is isShutDown is false before shutdown, true after
     */
    public void testIsShutdown() {
        
	ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
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
	ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
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
	ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
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
     * getQueue returns the work queue, which contains queued tasks
     */
    public void testGetQueue() {
        ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
        ScheduledFuture[] tasks = new ScheduledFuture[5];
        for(int i = 0; i < 5; i++){
            tasks[i] = p1.schedule(new SmallPossiblyInterruptedRunnable(), 1, TimeUnit.MILLISECONDS);
        }
        try {
            Thread.sleep(SHORT_DELAY_MS);
            BlockingQueue<Runnable> q = p1.getQueue();
            assertTrue(q.contains(tasks[4]));
            assertFalse(q.contains(tasks[0]));
            p1.shutdownNow();
        } catch(Exception e) {
            unexpectedException();
        } finally {
            joinPool(p1);
        }
    }

    /**
     * remove(task) removes queued task, and fails to remove active task
     */
    public void testRemove() {
        ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
        ScheduledFuture[] tasks = new ScheduledFuture[5];
        for(int i = 0; i < 5; i++){
            tasks[i] = p1.schedule(new SmallPossiblyInterruptedRunnable(), 1, TimeUnit.MILLISECONDS);
        }
        try {
            Thread.sleep(SHORT_DELAY_MS);
            BlockingQueue<Runnable> q = p1.getQueue();
            assertFalse(p1.remove((Runnable)tasks[0]));
            assertTrue(q.contains((Runnable)tasks[4]));
            assertTrue(q.contains((Runnable)tasks[3]));
            assertTrue(p1.remove((Runnable)tasks[4]));
            assertFalse(p1.remove((Runnable)tasks[4]));
            assertFalse(q.contains((Runnable)tasks[4]));
            assertTrue(q.contains((Runnable)tasks[3]));
            assertTrue(p1.remove((Runnable)tasks[3]));
            assertFalse(q.contains((Runnable)tasks[3]));
            p1.shutdownNow();
        } catch(Exception e) {
            unexpectedException();
        } finally {
            joinPool(p1);
        }
    }

    /**
     *  purge removes cancelled tasks from the queue
     */
    public void testPurge() {
        ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
        ScheduledFuture[] tasks = new ScheduledFuture[5];
        for(int i = 0; i < 5; i++){
            tasks[i] = p1.schedule(new SmallPossiblyInterruptedRunnable(), 1, TimeUnit.MILLISECONDS);
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
	ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
        for(int i = 0; i < 5; i++)
            p1.schedule(new SmallPossiblyInterruptedRunnable(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
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
            ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
            assertTrue(p1.getExecuteExistingDelayedTasksAfterShutdownPolicy());
            assertFalse(p1.getContinueExistingPeriodicTasksAfterShutdownPolicy());

            ScheduledFuture[] tasks = new ScheduledFuture[5];
            for(int i = 0; i < 5; i++)
                tasks[i] = p1.schedule(new NoOpRunnable(), SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
            p1.shutdown();
            BlockingQueue q = p1.getQueue();
            for (Iterator it = q.iterator(); it.hasNext();) {
                ScheduledFuture t = (ScheduledFuture)it.next();
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
            ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
            p1.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            ScheduledFuture[] tasks = new ScheduledFuture[5];
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
            ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
            p1.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            ScheduledFuture task =
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
        ScheduledThreadPoolExecutor p1 = new ScheduledThreadPoolExecutor(1);
        try {
            p1.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
            ScheduledFuture task =
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
