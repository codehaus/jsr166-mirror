/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import java.util.concurrent.*;
import junit.framework.*;
import java.util.List;

public class ThreadPoolExecutorTest extends TestCase{
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }


    public static Test suite() {
        return new TestSuite(ThreadPoolExecutorTest.class);
    }
    
    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 

//---- testThread class to implement ThreadFactory for use in constructors
    static class testThread implements ThreadFactory{
        public Thread newThread(Runnable r){
            return new Thread(r);
        }   
    }

//---- testReject class to implement RejectedExecutionHandler for use in the constructors
    static class testReject implements RejectedExecutionHandler{
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor){} 
    }

    public Runnable newRunnable(){
	return new Runnable(){
		public void run(){
		    try{Thread.sleep(MEDIUM_DELAY_MS);
                    } catch(Exception e){
                    }
		}
	    };
    }

 
    /**
     *  Test to verify that execute successfully executes a runnable
     */
    public void testExecute(){
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            one.execute(new Runnable(){
                    public void run(){
                        try{
                            Thread.sleep(SHORT_DELAY_MS);
                        }catch(InterruptedException e){
                            fail("unexpected exception");
                        }
                    }
                });
	    Thread.sleep(SHORT_DELAY_MS * 2);
        }catch(InterruptedException e){
            fail("unexpected exception");
        } finally {
            one.shutdown();
        }
    }

    /**
     *  Test to verify getActiveCount gives correct values
     */
    public void testGetActiveCount(){
        ThreadPoolExecutor two = new ThreadPoolExecutor(2, 2, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(0, two.getActiveCount());
            two.execute(newRunnable());
            try{Thread.sleep(10);}catch(Exception e){}
            assertEquals(1, two.getActiveCount());
        } finally {
            two.shutdown();
        }
    }
    
    /**
     *  Test to verify getCompleteTaskCount gives correct values
     */
    public void testGetCompletedTaskCount(){
        ThreadPoolExecutor two = new ThreadPoolExecutor(2, 2, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(0, two.getCompletedTaskCount());
            two.execute(newRunnable());
            try{Thread.sleep(2000);}catch(Exception e){}
            assertEquals(1, two.getCompletedTaskCount());
        } finally {
            two.shutdown();
        }
    }
    
    /**
     *  Test to verify getCorePoolSize gives correct values
     */
    public void testGetCorePoolSize(){
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(1, one.getCorePoolSize());
        } finally {
            one.shutdown();
        }
    }
    
    /**
     *  Test to verify getKeepAliveTime gives correct values
     */
    public void testGetKeepAliveTime(){
        ThreadPoolExecutor two = new ThreadPoolExecutor(2, 2, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(1, two.getKeepAliveTime(TimeUnit.SECONDS));
        } finally {
            two.shutdown();
        }
    }
    
    /**
     *  Test to verify getLargestPoolSize gives correct values
     */
    public void testGetLargestPoolSize(){
        ThreadPoolExecutor two = new ThreadPoolExecutor(2, 2, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(0, two.getLargestPoolSize());
            two.execute(newRunnable());
            two.execute(newRunnable());
            try{Thread.sleep(SHORT_DELAY_MS);} catch(Exception e){}
            assertEquals(2, two.getLargestPoolSize());
        } finally {
            two.shutdown();
        }
    }
    
    /**
     *  Test to verify getMaximumPoolSize gives correct values
     */
    public void testGetMaximumPoolSize(){
        ThreadPoolExecutor two = new ThreadPoolExecutor(2, 2, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(2, two.getMaximumPoolSize());
        } finally {
            two.shutdown();
        }
    }
    
    /**
     *  Test to verify getPoolSize gives correct values
     */
    public void testGetPoolSize(){
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
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
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(0, one.getTaskCount());
            for(int i = 0; i < 5; i++)
                one.execute(newRunnable());
            try{Thread.sleep(SHORT_DELAY_MS);}catch(Exception e){}
            assertEquals(5, one.getTaskCount());
        } finally {
            one.shutdown();
        }
    }
    
    /**
     *  Test to verify isShutDown gives correct values
     */
    public void testIsShutdown(){
        
	ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
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
	ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            one.execute(newRunnable());
        } finally {
            one.shutdown();
        }
	boolean flag = false;
	try{
	    flag = one.awaitTermination(10, TimeUnit.SECONDS);
	}catch(Exception e){}	
	assertTrue(one.isTerminated());
	if(!flag)
	    fail("ThreadPoolExecutor - thread pool did not terminate within suitable timeframe");
    }

    /**
     *  Test to verify that purge correctly removes cancelled tasks
     *  from the queue
     */
    public void testPurge(){
        ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            CancellableTask[] tasks = new CancellableTask[5];
            for(int i = 0; i < 5; i++){
                tasks[i] = new CancellableTask(newRunnable());
                one.execute(tasks[i]);
            }
            tasks[4].cancel(true);
            tasks[3].cancel(true);
            one.purge();
            long count = one.getTaskCount();
            assertTrue(count >= 2 && count < 5);
        } finally {
            one.shutdown();
        }
    }

    /**
     *  Test to verify shutDownNow returns a list
     *  containing the correct number of elements
     */
    public void testShutDownNow(){
	ThreadPoolExecutor one = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        List l;
        try {
            for(int i = 0; i < 5; i++)
                one.execute(newRunnable());
        }
        finally {
            l = one.shutdownNow();
        }
	assertTrue(one.isShutdown());
	assertTrue(l.size() <= 4);
    }

    
 
    
    
      
    // Exception Tests
    

    //---- Tests if corePoolSize argument is less than zero
    public void testConstructor1() {
        try{
            new ThreadPoolExecutor(-1,1,100L,TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i){}
    }
    
    //---- Tests if maximumPoolSize is less than zero
    public void testConstructor2() {
        try{
            new ThreadPoolExecutor(1,-1,100L,TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i2){}
    }
    
    //---- Tests if maximumPoolSize is equal to zero
    public void testConstructor3() {
        try{
            new ThreadPoolExecutor(1,0,100L,TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i3){}
    }

    //---- Tests if keepAliveTime is less than zero
    public void testConstructor4() {
        try{
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i4){}
    }

    //---- Tests if corePoolSize is greater than the maximumPoolSize
    public void testConstructor5() {
        try{
            new ThreadPoolExecutor(2,1,100L,TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i5){}
    }
	
    //---- Tests if workQueue is set to null
    public void testNullPointerException() {
        try{
            new ThreadPoolExecutor(1,2,100L,TimeUnit.MILLISECONDS,null);
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException n){}  
    }
    

    
    //---- Tests if corePoolSize argument is less than zero
    public void testConstructor6() {
        try{
            new ThreadPoolExecutor(-1,1,100L,TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10),new testThread());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }catch (IllegalArgumentException i6){}
    }
    
    //---- Tests if maximumPoolSize is less than zero
    public void testConstructor7() {
        try{
            new ThreadPoolExecutor(1,-1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testThread());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i7){}
    }

    //---- Tests if maximumPoolSize is equal to zero
    public void testConstructor8() {
        try{
            new ThreadPoolExecutor(1,0,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testThread());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i8){}
    }

    //---- Tests if keepAliveTime is less than zero
    public void testConstructor9() {
        try{
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testThread());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i9){}
    }

    //---- Tests if corePoolSize is greater than the maximumPoolSize
    public void testConstructor10() {
        try{
            new ThreadPoolExecutor(2,1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testThread());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i10){}
    }

    //---- Tests if workQueue is set to null
    public void testNullPointerException2() {
        try{
            new ThreadPoolExecutor(1,2,100L,TimeUnit.MILLISECONDS,null,new testThread());
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException n2){}  
    }

    //---- Tests if threadFactory is set to null
    public void testNullPointerException3() {
        try{
            ThreadFactory f = null;
            new ThreadPoolExecutor(1,2,100L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),f);
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException n3){}  
    }
 
    
    //---- Tests if corePoolSize argument is less than zero
    public void testConstructor11() {
        try{
            new ThreadPoolExecutor(-1,1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testReject());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i11){}
    }

    //---- Tests if maximumPoolSize is less than zero
    public void testConstructor12() {
        try{
            new ThreadPoolExecutor(1,-1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testReject());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i12){}
    }

    //---- Tests if maximumPoolSize is equal to zero
    public void testConstructor13() {
        try{
            new ThreadPoolExecutor(1,0,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testReject());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i13){}
    }

    //---- Tests if keepAliveTime is less than zero
    public void testConstructor14() {
        try{
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testReject());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i14){}
    }

    //---- Tests if corePoolSize is greater than the maximumPoolSize
    public void testConstructor15() {
        try{
            new ThreadPoolExecutor(2,1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testReject());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i15){}
    }

    //---- Tests if workQueue is set to null
    public void testNullPointerException4() {
        try{
            new ThreadPoolExecutor(1,2,100L,TimeUnit.MILLISECONDS,null,new testReject());
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException n4){}  
    }

    //---- Tests if handler is set to null
    public void testNullPointerException5() {
        try{
            RejectedExecutionHandler r = null;
            new ThreadPoolExecutor(1,2,100L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),r);
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException n5){}  
    }

    
    //---- Tests if corePoolSize argument is less than zero
    public void testConstructor16() {
        try{
            new ThreadPoolExecutor(-1,1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testThread(),new testReject());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i16){}
    }

    //---- Tests if maximumPoolSize is less than zero
    public void testConstructor17() {
        try{
            new ThreadPoolExecutor(1,-1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testThread(),new testReject());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i17){}
    }

    //---- Tests if maximumPoolSize is equal to zero
    public void testConstructor18() {
        try{
            new ThreadPoolExecutor(1,0,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testThread(),new testReject());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i18){}
    }

    //---- Tests if keepAliveTime is less than zero
    public void testConstructor19() {
        try{
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testThread(),new testReject());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i19){}
    }

    //---- Tests if corePoolSize is greater than the maximumPoolSize
    public void testConstructor20() {
        try{
            new ThreadPoolExecutor(2,1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new testThread(),new testReject());
            fail("ThreadPoolExecutor constructor should throw an IllegalArgumentException");		
        }
        catch (IllegalArgumentException i20){}
    }

    //---- Tests if workQueue is set to null
    public void testNullPointerException6() {
        try{
            new ThreadPoolExecutor(1,2,100L,TimeUnit.MILLISECONDS,null,new testThread(),new testReject());
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException n6){}  
    }

    //---- Tests if handler is set to null
    public void testNullPointerException7() {
        try{
            RejectedExecutionHandler r = null;
            new ThreadPoolExecutor(1,2,100L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),new testThread(),r);
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException n7){}  
    }

    //---- Tests if ThradFactory is set top null
    public void testNullPointerException8() {
        try{
            ThreadFactory f = null;
            new ThreadPoolExecutor(1,2,100L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),f,new testReject());
            fail("ThreadPoolExecutor constructor should throw a NullPointerException");		
        }
        catch (NullPointerException n8){}  
    }
    

    /**
     *  Test to verify execute will throw RejectedExcutionException
     *  ThreadPoolExecutor will throw one when more runnables are
     *  executed then will fit in the Queue.
     */
    public void testRejectedExecutedException(){
        ThreadPoolExecutor tpe = null;
        try{
	    tpe = new ThreadPoolExecutor(1,1,100,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(1));
        }catch(Exception e){}
        tpe.shutdown();
	try{
	    tpe.execute(new Runnable(){
		    public void run(){
			try{
			    Thread.sleep(1000);
			}catch(InterruptedException e){}
		    }
		});
	    fail("ThreadPoolExecutor - void execute(Runnable) should throw RejectedExecutionException");
	}catch(RejectedExecutionException sucess){}
	
	
    }
    
    /**
     *  Test to verify setCorePoolSize will throw IllegalArgumentException
     *  when given a negative 
     */
    public void testIllegalArgumentException1(){
	ThreadPoolExecutor tpe = null;
	try{
	    tpe = new ThreadPoolExecutor(1,2,100,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
	}catch(Exception e){}
	try{
	    tpe.setCorePoolSize(-1);
	    fail("ThreadPoolExecutor - void setCorePoolSize(int) should throw IllegalArgumentException");
	}catch(IllegalArgumentException sucess){
        } finally {
            tpe.shutdown();
        }
    }   

    
    /**
     *  Test to verify setMaximumPoolSize(int) will throw IllegalArgumentException
     *  if given a value less the it's actual core pool size
     */  
    public void testIllegalArgumentException2(){
        ThreadPoolExecutor tpe = null;
        try{
            tpe = new ThreadPoolExecutor(2,3,100,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
        }catch(Exception e){}
        try{
            tpe.setMaximumPoolSize(1);
            fail("ThreadPoolExecutor - void setMaximumPoolSize(int) should throw IllegalArgumentException");
        }catch(IllegalArgumentException sucess){
        } finally {
            tpe.shutdown();
        }
    }
    
    /**
     *  Test to verify that setMaximumPoolSize will throw IllegalArgumentException
     *  if given a negative number
     */
    public void testIllegalArgumentException2SP(){
        ThreadPoolExecutor tpe = null;
        try{
            tpe = new ThreadPoolExecutor(2,3,100,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
        }catch(Exception e){}
        try{
            tpe.setMaximumPoolSize(-1);
            fail("ThreadPoolExecutor - void setMaximumPoolSize(int) should throw IllegalArgumentException");
        }catch(IllegalArgumentException sucess){
        } finally {
            tpe.shutdown();
        }
    }
    

    /**
     *  Test to verify setKeepAliveTime will throw IllegalArgumentException
     *  when given a negative value
     */
    public void testIllegalArgumentException3(){
	ThreadPoolExecutor tpe = null;
        try{
            tpe = new ThreadPoolExecutor(2,3,100,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
        }catch(Exception e){}
        
	try{
            tpe.setKeepAliveTime(-1,TimeUnit.MILLISECONDS);
            fail("ThreadPoolExecutor - void setKeepAliveTime(long, TimeUnit) should throw IllegalArgumentException");
        }catch(IllegalArgumentException sucess){
        } finally {
            tpe.shutdown();
        }
    }
  
    
  
}
