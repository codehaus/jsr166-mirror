/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import java.util.concurrent.*;
import junit.framework.*;
import java.util.List;

public class ThreadPoolExecutorTest extends JSR166TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
        return new TestSuite(ThreadPoolExecutorTest.class);
    }
    
    /**
     * For use as ThreadFactory in constructors
     */
    static class MyThreadFactory implements ThreadFactory{
        public Thread newThread(Runnable r){
            return new Thread(r);
        }   
    }

    /**
     * For use as RejectedExecutionHandler in constructors
     */
    static class MyREHandler implements RejectedExecutionHandler{
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor){} 
    }
 
    /**
     *   execute successfully executes a runnable
     */
    public void testExecute(){
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            one.execute(new Runnable(){
                    public void run(){
                        try{
                            Thread.sleep(SHORT_DELAY_MS);
                        } catch(InterruptedException e){
                            fail("unexpected exception");
                        }
                    }
                });
	    Thread.sleep(SMALL_DELAY_MS);
        } catch(InterruptedException e){
            fail("unexpected exception");
        } 
        joinPool(one);
    }

    /**
     *   getActiveCount gives correct values
     */
    public void testGetActiveCount(){
        ThreadPoolExecutor two = new ThreadPoolExecutor(2, 2, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(0, two.getActiveCount());
        two.execute(new MediumRunnable());
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
        ThreadPoolExecutor two = new ThreadPoolExecutor(2, 2, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(0, two.getCompletedTaskCount());
        two.execute(new ShortRunnable());
        try{
            Thread.sleep(MEDIUM_DELAY_MS);
        } catch(Exception e){
            fail("unexpected exception");
        }
        assertEquals(1, two.getCompletedTaskCount());
        two.shutdown();
        joinPool(two);
    }
    
    /**
     *   getCorePoolSize gives correct values
     */
    public void testGetCorePoolSize(){
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(1, one.getCorePoolSize());
        joinPool(one);
    }
    
    /**
     *   getKeepAliveTime gives correct values
     */
    public void testGetKeepAliveTime(){
        ThreadPoolExecutor two = new ThreadPoolExecutor(2, 2, 1000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(1, two.getKeepAliveTime(TimeUnit.SECONDS));
        joinPool(two);
    }
    
    /**
     *   getLargestPoolSize gives correct values
     */
    public void testGetLargestPoolSize(){
        ThreadPoolExecutor two = new ThreadPoolExecutor(2, 2, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(0, two.getLargestPoolSize());
            two.execute(new MediumRunnable());
            two.execute(new MediumRunnable());
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(2, two.getLargestPoolSize());
        } catch(Exception e){
            fail("unexpected exception");
        } 
        joinPool(two);
    }
    
    /**
     *   getMaximumPoolSize gives correct values
     */
    public void testGetMaximumPoolSize(){
        ThreadPoolExecutor two = new ThreadPoolExecutor(2, 2, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(2, two.getMaximumPoolSize());
        joinPool(two);
    }
    
    /**
     *   getPoolSize gives correct values
     */
    public void testGetPoolSize(){
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(0, one.getPoolSize());
        one.execute(new MediumRunnable());
        assertEquals(1, one.getPoolSize());
        joinPool(one);
    }
    
    /**
     *   getTaskCount gives correct values
     */
    public void testGetTaskCount(){
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(0, one.getTaskCount());
            one.execute(new MediumRunnable());
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(1, one.getTaskCount());
        } catch(Exception e){
            fail("unexpected exception");
        } 
        joinPool(one);
    }
    
    /**
     *   isShutDown gives correct values
     */
    public void testIsShutdown(){
        
	ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertFalse(one.isShutdown());
        one.shutdown();
	assertTrue(one.isShutdown());
        joinPool(one);
    }

        
    /**
     *   isTerminated gives correct values
     *  Makes sure termination does not take an innapropriate
     *  amount of time
     */
    public void testIsTerminated(){
	ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            one.execute(new MediumRunnable());
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
     *   purge correctly removes cancelled tasks
     *  from the queue
     */
    public void testPurge(){
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        CancellableTask[] tasks = new CancellableTask[5];
        for(int i = 0; i < 5; i++){
            tasks[i] = new CancellableTask(new MediumPossiblyInterruptedRunnable());
            one.execute(tasks[i]);
        }
        tasks[4].cancel(true);
        tasks[3].cancel(true);
        one.purge();
        long count = one.getTaskCount();
        assertTrue(count >= 2 && count < 5);
        joinPool(one);
    }

    /**
     *   shutDownNow returns a list
     *  containing the correct number of elements
     */
    public void testShutDownNow(){
	ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        List l;
        try {
            for(int i = 0; i < 5; i++)
                one.execute(new MediumPossiblyInterruptedRunnable());
        }
        finally {
            l = one.shutdownNow();
        }
	assertTrue(one.isShutdown());
	assertTrue(l.size() <= 4);
    }

    // Exception Tests
    

    /** Throws if corePoolSize argument is less than zero */
    public void testConstructor1() {
        try{
            new ThreadPoolExecutor(-1,1,LONG_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }
    
    /** Throws if maximumPoolSize is less than zero */
    public void testConstructor2() {
        try{
            new ThreadPoolExecutor(1,-1,LONG_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }
    
    /** Throws if maximumPoolSize is equal to zero */
    public void testConstructor3() {
        try{
            new ThreadPoolExecutor(1,0,LONG_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if keepAliveTime is less than zero */
    public void testConstructor4() {
        try{
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if corePoolSize is greater than the maximumPoolSize */
    public void testConstructor5() {
        try{
            new ThreadPoolExecutor(2,1,LONG_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }
	
    /** Throws if workQueue is set to null */
    public void testNullPointerException() {
        try{
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,null);
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException success){}  
    }
    

    
    /** Throws if corePoolSize argument is less than zero */
    public void testConstructor6() {
        try{
            new ThreadPoolExecutor(-1,1,LONG_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        } catch (IllegalArgumentException success){}
    }
    
    /** Throws if maximumPoolSize is less than zero */
    public void testConstructor7() {
        try{
            new ThreadPoolExecutor(1,-1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if maximumPoolSize is equal to zero */
    public void testConstructor8() {
        try{
            new ThreadPoolExecutor(1,0,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if keepAliveTime is less than zero */
    public void testConstructor9() {
        try{
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if corePoolSize is greater than the maximumPoolSize */
    public void testConstructor10() {
        try{
            new ThreadPoolExecutor(2,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if workQueue is set to null */
    public void testNullPointerException2() {
        try{
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,null,new MyThreadFactory());
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException success){}  
    }

    /** Throws if threadFactory is set to null */
    public void testNullPointerException3() {
        try{
            ThreadFactory f = null;
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),f);
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException success){}  
    }
 
    
    /** Throws if corePoolSize argument is less than zero */
    public void testConstructor11() {
        try{
            new ThreadPoolExecutor(-1,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if maximumPoolSize is less than zero */
    public void testConstructor12() {
        try{
            new ThreadPoolExecutor(1,-1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if maximumPoolSize is equal to zero */
    public void testConstructor13() {
        try{
            new ThreadPoolExecutor(1,0,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if keepAliveTime is less than zero */
    public void testConstructor14() {
        try{
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if corePoolSize is greater than the maximumPoolSize */
    public void testConstructor15() {
        try{
            new ThreadPoolExecutor(2,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if workQueue is set to null */
    public void testNullPointerException4() {
        try{
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,null,new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException success){}  
    }

    /** Throws if handler is set to null */
    public void testNullPointerException5() {
        try{
            RejectedExecutionHandler r = null;
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),r);
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException success){}  
    }

    
    /** Throws if corePoolSize argument is less than zero */
    public void testConstructor16() {
        try{
            new ThreadPoolExecutor(-1,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if maximumPoolSize is less than zero */
    public void testConstructor17() {
        try{
            new ThreadPoolExecutor(1,-1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if maximumPoolSize is equal to zero */
    public void testConstructor18() {
        try{
            new ThreadPoolExecutor(1,0,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if keepAliveTime is less than zero */
    public void testConstructor19() {
        try{
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if corePoolSize is greater than the maximumPoolSize */
    public void testConstructor20() {
        try{
            new ThreadPoolExecutor(2,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if workQueue is set to null */
    public void testNullPointerException6() {
        try{
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,null,new MyThreadFactory(),new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException success){}  
    }

    /** Throws if handler is set to null */
    public void testNullPointerException7() {
        try{
            RejectedExecutionHandler r = null;
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),r);
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException success){}  
    }

    /** Throws if ThreadFactory is set top null */
    public void testNullPointerException8() {
        try{
            ThreadFactory f = null;
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),f,new MyREHandler());
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException successdn8){}  
    }
    

    /**
     *   execute will throw RejectedExcutionException
     *  ThreadPoolExecutor will throw one when more runnables are
     *  executed then will fit in the Queue.
     */
    public void testRejectedExecutionException(){
        ThreadPoolExecutor tpe = null;
        try{
	    tpe = new ThreadPoolExecutor(1,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(1));
        } catch(Exception e){}
        tpe.shutdown();
	try{
	    tpe.execute(new NoOpRunnable());
	    fail("ThreadPoolExecutor - void execute(Runnable) should throw RejectedExecutionException");
	} catch(RejectedExecutionException success){}
	
	joinPool(tpe);
    }
    
    /**
     *   setCorePoolSize will throw IllegalArgumentException
     *  when given a negative 
     */
    public void testCorePoolSizeIllegalArgumentException(){
	ThreadPoolExecutor tpe = null;
	try{
	    tpe = new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
	} catch(Exception e){}
	try{
	    tpe.setCorePoolSize(-1);
	    fail("ThreadPoolExecutor - void setCorePoolSize(int) should throw IllegalArgumentException");
	} catch(IllegalArgumentException success){
        } finally {
            tpe.shutdown();
        }
        joinPool(tpe);
    }   

    
    /**
     *   setMaximumPoolSize(int) will throw IllegalArgumentException
     *  if given a value less the it's actual core pool size
     */  
    public void testMaximumPoolSizeIllegalArgumentException(){
        ThreadPoolExecutor tpe = null;
        try{
            tpe = new ThreadPoolExecutor(2,3,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
        } catch(Exception e){}
        try{
            tpe.setMaximumPoolSize(1);
            fail("ThreadPoolExecutor - void setMaximumPoolSize(int) should throw IllegalArgumentException");
        } catch(IllegalArgumentException success){
        } finally {
            tpe.shutdown();
        }
        joinPool(tpe);
    }
    
    /**
     *   setMaximumPoolSize will throw IllegalArgumentException
     *  if given a negative number
     */
    public void testMaximumPoolSizeIllegalArgumentException2(){
        ThreadPoolExecutor tpe = null;
        try{
            tpe = new ThreadPoolExecutor(2,3,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
        } catch(Exception e){}
        try{
            tpe.setMaximumPoolSize(-1);
            fail("ThreadPoolExecutor - void setMaximumPoolSize(int) should throw IllegalArgumentException");
        } catch(IllegalArgumentException success){
        } finally {
            tpe.shutdown();
        }
        joinPool(tpe);
    }
    

    /**
     *   setKeepAliveTime will throw IllegalArgumentException
     *  when given a negative value
     */
    public void testKeepAliveTimeIllegalArgumentException(){
	ThreadPoolExecutor tpe = null;
        try{
            tpe = new ThreadPoolExecutor(2,3,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
        } catch(Exception e){}
        
	try{
            tpe.setKeepAliveTime(-1,TimeUnit.MILLISECONDS);
            fail("ThreadPoolExecutor - void setKeepAliveTime(long, TimeUnit) should throw IllegalArgumentException");
        } catch(IllegalArgumentException success){
        } finally {
            tpe.shutdown();
        }
        joinPool(tpe);
    }
  
}
