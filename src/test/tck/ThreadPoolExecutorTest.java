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
    public void testExecute() {
        ThreadPoolExecutor p1 = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            p1.execute(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(SHORT_DELAY_MS);
                        } catch(InterruptedException e){
                            threadUnexpectedException();
                        }
                    }
                });
	    Thread.sleep(SMALL_DELAY_MS);
        } catch(InterruptedException e){
            unexpectedException();
        } 
        joinPool(p1);
    }

    /**
     *   getActiveCount gives correct values
     */
    public void testGetActiveCount() {
        ThreadPoolExecutor p2 = new ThreadPoolExecutor(2, 2, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(0, p2.getActiveCount());
        p2.execute(new MediumRunnable());
        try {
            Thread.sleep(SHORT_DELAY_MS);
        } catch(Exception e){
            unexpectedException();
        }
        assertEquals(1, p2.getActiveCount());
        joinPool(p2);
    }
    
    /**
     *   getCompleteTaskCount gives correct values
     */
    public void testGetCompletedTaskCount() {
        ThreadPoolExecutor p2 = new ThreadPoolExecutor(2, 2, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(0, p2.getCompletedTaskCount());
        p2.execute(new ShortRunnable());
        try {
            Thread.sleep(MEDIUM_DELAY_MS);
        } catch(Exception e){
            unexpectedException();
        }
        assertEquals(1, p2.getCompletedTaskCount());
        p2.shutdown();
        joinPool(p2);
    }
    
    /**
     *   getCorePoolSize gives correct values
     */
    public void testGetCorePoolSize() {
        ThreadPoolExecutor p1 = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(1, p1.getCorePoolSize());
        joinPool(p1);
    }
    
    /**
     *   getKeepAliveTime gives correct values
     */
    public void testGetKeepAliveTime() {
        ThreadPoolExecutor p2 = new ThreadPoolExecutor(2, 2, 1000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(1, p2.getKeepAliveTime(TimeUnit.SECONDS));
        joinPool(p2);
    }
    
    /**
     *   getLargestPoolSize gives correct values
     */
    public void testGetLargestPoolSize() {
        ThreadPoolExecutor p2 = new ThreadPoolExecutor(2, 2, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(0, p2.getLargestPoolSize());
            p2.execute(new MediumRunnable());
            p2.execute(new MediumRunnable());
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(2, p2.getLargestPoolSize());
        } catch(Exception e){
            unexpectedException();
        } 
        joinPool(p2);
    }
    
    /**
     *   getMaximumPoolSize gives correct values
     */
    public void testGetMaximumPoolSize() {
        ThreadPoolExecutor p2 = new ThreadPoolExecutor(2, 2, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(2, p2.getMaximumPoolSize());
        joinPool(p2);
    }
    
    /**
     *   getPoolSize gives correct values
     */
    public void testGetPoolSize() {
        ThreadPoolExecutor p1 = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertEquals(0, p1.getPoolSize());
        p1.execute(new MediumRunnable());
        assertEquals(1, p1.getPoolSize());
        joinPool(p1);
    }
    
    /**
     *   getTaskCount gives correct values
     */
    public void testGetTaskCount() {
        ThreadPoolExecutor p1 = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            assertEquals(0, p1.getTaskCount());
            p1.execute(new MediumRunnable());
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(1, p1.getTaskCount());
        } catch(Exception e){
            unexpectedException();
        } 
        joinPool(p1);
    }
    
    /**
     *   isShutDown gives correct values
     */
    public void testIsShutdown() {
        
	ThreadPoolExecutor p1 = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        assertFalse(p1.isShutdown());
        p1.shutdown();
	assertTrue(p1.isShutdown());
        joinPool(p1);
    }

        
    /**
     *   isTerminated gives correct values
     *  Makes sure termination does not take an innapropriate
     *  amount of time
     */
    public void testIsTerminated() {
	ThreadPoolExecutor p1 = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            p1.execute(new MediumRunnable());
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
     *  isTerminating gives correct values
     */
    public void testIsTerminating() {
	ThreadPoolExecutor p1 = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
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
     *   purge correctly removes cancelled tasks
     *  from the queue
     */
    public void testPurge() {
        ThreadPoolExecutor p1 = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        CancellableTask[] tasks = new CancellableTask[5];
        for(int i = 0; i < 5; i++){
            tasks[i] = new CancellableTask(new MediumPossiblyInterruptedRunnable());
            p1.execute(tasks[i]);
        }
        tasks[4].cancel(true);
        tasks[3].cancel(true);
        p1.purge();
        long count = p1.getTaskCount();
        assertTrue(count >= 2 && count < 5);
        joinPool(p1);
    }

    /**
     *   shutDownNow returns a list
     *  containing the correct number of elements
     */
    public void testShutDownNow() {
	ThreadPoolExecutor p1 = new ThreadPoolExecutor(1, 1, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        List l;
        try {
            for(int i = 0; i < 5; i++)
                p1.execute(new MediumPossiblyInterruptedRunnable());
        }
        finally {
            l = p1.shutdownNow();
        }
	assertTrue(p1.isShutdown());
	assertTrue(l.size() <= 4);
    }

    // Exception Tests
    

    /** Throws if corePoolSize argument is less than zero */
    public void testConstructor1() {
        try {
            new ThreadPoolExecutor(-1,1,LONG_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }
    
    /** Throws if maximumPoolSize is less than zero */
    public void testConstructor2() {
        try {
            new ThreadPoolExecutor(1,-1,LONG_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }
    
    /** Throws if maximumPoolSize is equal to zero */
    public void testConstructor3() {
        try {
            new ThreadPoolExecutor(1,0,LONG_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if keepAliveTime is less than zero */
    public void testConstructor4() {
        try {
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if corePoolSize is greater than the maximumPoolSize */
    public void testConstructor5() {
        try {
            new ThreadPoolExecutor(2,1,LONG_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }
	
    /** Throws if workQueue is set to null */
    public void testNullPointerException() {
        try {
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,null);
            shouldThrow();
        }
        catch (NullPointerException success){}  
    }
    

    
    /** Throws if corePoolSize argument is less than zero */
    public void testConstructor6() {
        try {
            new ThreadPoolExecutor(-1,1,LONG_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory());
            shouldThrow();
        } catch (IllegalArgumentException success){}
    }
    
    /** Throws if maximumPoolSize is less than zero */
    public void testConstructor7() {
        try {
            new ThreadPoolExecutor(1,-1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if maximumPoolSize is equal to zero */
    public void testConstructor8() {
        try {
            new ThreadPoolExecutor(1,0,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if keepAliveTime is less than zero */
    public void testConstructor9() {
        try {
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if corePoolSize is greater than the maximumPoolSize */
    public void testConstructor10() {
        try {
            new ThreadPoolExecutor(2,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if workQueue is set to null */
    public void testNullPointerException2() {
        try {
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,null,new MyThreadFactory());
            shouldThrow();
        }
        catch (NullPointerException success){}  
    }

    /** Throws if threadFactory is set to null */
    public void testNullPointerException3() {
        try {
            ThreadFactory f = null;
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),f);
            shouldThrow();
        }
        catch (NullPointerException success){}  
    }
 
    
    /** Throws if corePoolSize argument is less than zero */
    public void testConstructor11() {
        try {
            new ThreadPoolExecutor(-1,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyREHandler());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if maximumPoolSize is less than zero */
    public void testConstructor12() {
        try {
            new ThreadPoolExecutor(1,-1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyREHandler());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if maximumPoolSize is equal to zero */
    public void testConstructor13() {
        try {
            new ThreadPoolExecutor(1,0,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyREHandler());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if keepAliveTime is less than zero */
    public void testConstructor14() {
        try {
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyREHandler());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if corePoolSize is greater than the maximumPoolSize */
    public void testConstructor15() {
        try {
            new ThreadPoolExecutor(2,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyREHandler());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if workQueue is set to null */
    public void testNullPointerException4() {
        try {
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,null,new MyREHandler());
            shouldThrow();
        }
        catch (NullPointerException success){}  
    }

    /** Throws if handler is set to null */
    public void testNullPointerException5() {
        try {
            RejectedExecutionHandler r = null;
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),r);
            shouldThrow();
        }
        catch (NullPointerException success){}  
    }

    
    /** Throws if corePoolSize argument is less than zero */
    public void testConstructor16() {
        try {
            new ThreadPoolExecutor(-1,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),new MyREHandler());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if maximumPoolSize is less than zero */
    public void testConstructor17() {
        try {
            new ThreadPoolExecutor(1,-1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),new MyREHandler());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if maximumPoolSize is equal to zero */
    public void testConstructor18() {
        try {
            new ThreadPoolExecutor(1,0,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),new MyREHandler());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if keepAliveTime is less than zero */
    public void testConstructor19() {
        try {
            new ThreadPoolExecutor(1,2,-1L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),new MyREHandler());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if corePoolSize is greater than the maximumPoolSize */
    public void testConstructor20() {
        try {
            new ThreadPoolExecutor(2,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),new MyREHandler());
            shouldThrow();
        }
        catch (IllegalArgumentException success){}
    }

    /** Throws if workQueue is set to null */
    public void testNullPointerException6() {
        try {
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,null,new MyThreadFactory(),new MyREHandler());
            shouldThrow();
        }
        catch (NullPointerException success){}  
    }

    /** Throws if handler is set to null */
    public void testNullPointerException7() {
        try {
            RejectedExecutionHandler r = null;
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),new MyThreadFactory(),r);
            shouldThrow();
        }
        catch (NullPointerException success){}  
    }

    /** Throws if ThreadFactory is set top null */
    public void testNullPointerException8() {
        try {
            ThreadFactory f = null;
            new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10),f,new MyREHandler());
            shouldThrow();
        }
        catch (NullPointerException successdn8){}  
    }
    

    /**
     *   execute will throw RejectedExcutionException
     *  ThreadPoolExecutor will throw one when more runnables are
     *  executed then will fit in the Queue.
     */
    public void testRejectedExecutionException() {
        ThreadPoolExecutor tpe = null;
        try {
	    tpe = new ThreadPoolExecutor(1,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(1));
        } catch(Exception e){}
        tpe.shutdown();
	try {
	    tpe.execute(new NoOpRunnable());
	    shouldThrow();
	} catch(RejectedExecutionException success){}
	
	joinPool(tpe);
    }
    
    /**
     *   setCorePoolSize will throw IllegalArgumentException
     *  when given a negative 
     */
    public void testCorePoolSizeIllegalArgumentException() {
	ThreadPoolExecutor tpe = null;
	try {
	    tpe = new ThreadPoolExecutor(1,2,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
	} catch(Exception e){}
	try {
	    tpe.setCorePoolSize(-1);
	    shouldThrow();
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
    public void testMaximumPoolSizeIllegalArgumentException() {
        ThreadPoolExecutor tpe = null;
        try {
            tpe = new ThreadPoolExecutor(2,3,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
        } catch(Exception e){}
        try {
            tpe.setMaximumPoolSize(1);
            shouldThrow();
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
    public void testMaximumPoolSizeIllegalArgumentException2() {
        ThreadPoolExecutor tpe = null;
        try {
            tpe = new ThreadPoolExecutor(2,3,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
        } catch(Exception e){}
        try {
            tpe.setMaximumPoolSize(-1);
            shouldThrow();
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
    public void testKeepAliveTimeIllegalArgumentException() {
	ThreadPoolExecutor tpe = null;
        try {
            tpe = new ThreadPoolExecutor(2,3,SHORT_DELAY_MS, TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
        } catch(Exception e){}
        
	try {
            tpe.setKeepAliveTime(-1,TimeUnit.MILLISECONDS);
            shouldThrow();
        } catch(IllegalArgumentException success){
        } finally {
            tpe.shutdown();
        }
        joinPool(tpe);
    }
  
}
