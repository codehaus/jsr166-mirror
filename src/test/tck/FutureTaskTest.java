/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.*;
import java.util.*;

public class FutureTaskTest extends JSR166TestCase {

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(FutureTaskTest.class);
    }

    /**
     * Subclass to expose protected methods
     */
    static class MyFutureTask extends FutureTask {
        public MyFutureTask(Callable r) { super(r); }
        public boolean reset() { return super.reset(); }
        public void setCancelled() { super.setCancelled(); }
        public void setDone() { super.setDone(); }
        public void set(Object x) { super.set(x); }
        public void setException(Throwable t) { super.setException(t); }
    }

    public void testConstructor(){
        try {
            FutureTask task = new FutureTask(null);
            fail("should throw");
        }
        catch(NullPointerException success) {
        }
    }

    public void testConstructor2(){
        try {
            FutureTask task = new FutureTask(null, Boolean.TRUE);
            fail("should throw");
        }
        catch(NullPointerException success) {
        }
    }

    public void testIsDone(){
        FutureTask task = new FutureTask( new NoOpCallable());
	task.run();
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    public void testReset(){
        MyFutureTask task = new MyFutureTask(new NoOpCallable());
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.reset());
    }

    public void testResetAfterCancel() {
        MyFutureTask task = new MyFutureTask(new NoOpCallable());
        assertTrue(task.cancel(false));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
        assertFalse(task.reset());
    }

    public void testSetDone() {
        MyFutureTask task = new MyFutureTask(new NoOpCallable());
	task.setDone();
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    public void testSetCancelled() {
        MyFutureTask task = new MyFutureTask(new NoOpCallable());
        assertTrue(task.cancel(false));
	task.setCancelled();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    public void testSet() {
        MyFutureTask task = new MyFutureTask(new NoOpCallable());
        task.set(one);
        try {
            assertEquals(task.get(), one);
        }
        catch(Exception e) {
            fail("unexpected exception");
        }
    }

    public void testSetException() {
        Exception nse = new NoSuchElementException();
        MyFutureTask task = new MyFutureTask(new NoOpCallable());
        task.setException(nse);
        try {
            Object x = task.get();
            fail("should throw");
        }
        catch(ExecutionException ee) {
            Throwable cause = ee.getCause();
            assertEquals(cause, nse);
        }
        catch(Exception e) {
            fail("unexpected exception");
        }
    }

    public void testCancelBeforeRun() {
        FutureTask task = new FutureTask( new NoOpCallable());
        assertTrue(task.cancel(false));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    public void testCancelBeforeRun2() {
        FutureTask task = new FutureTask( new NoOpCallable());
        assertTrue(task.cancel(true));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    public void testCancelAfterRun() {
        FutureTask task = new FutureTask( new NoOpCallable());
	task.run();
        assertFalse(task.cancel(false));
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    public void testCancelInterrupt(){
        FutureTask task = new FutureTask( new Callable() {
                public Object call() {
                    try {
                        Thread.sleep(MEDIUM_DELAY_MS);
                        threadFail("should throw");
                    }
                    catch (InterruptedException success) {}
                    return Boolean.TRUE;
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
        FutureTask task = new FutureTask( new Callable() {
                public Object call() {
                    try {
                        Thread.sleep(MEDIUM_DELAY_MS);
                    }
                    catch (InterruptedException success) {
                        threadFail("should not interrupt");
                    }
                    return Boolean.TRUE;
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

    public void testGet1() {
	final FutureTask ft = new FutureTask(new Callable(){
		public Object call(){
		    try{
			Thread.sleep(MEDIUM_DELAY_MS);
		    } catch(InterruptedException e){
                        threadFail("unexpected exception");
                    }
                    return Boolean.TRUE;
		}
	});
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
			ft.get();
		    } catch(Exception e){
                        threadFail("unexpected exception");
                    }
		}
	    });
	try{
            assertFalse(ft.isDone());
            assertFalse(ft.isCancelled());
            t.start();
	    Thread.sleep(SHORT_DELAY_MS);
	    ft.run();
	    t.join();
	    assertTrue(ft.isDone());
            assertFalse(ft.isCancelled());
	} catch(InterruptedException e){
            fail("unexpected exception");

        }	
    }

    public void testTimedGet1() {
	final FutureTask ft = new FutureTask(new Callable(){
		public Object call(){
		    try{
			Thread.sleep(MEDIUM_DELAY_MS);
		    } catch(InterruptedException e){
                        threadFail("unexpected exception");
                    }
                    return Boolean.TRUE;
		}
            });
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
			ft.get(SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
		    } catch(TimeoutException success) {
                    } catch(Exception e){
                        threadFail("unexpected exception");
                    }
		}
	    });
	try{
            assertFalse(ft.isDone());
            assertFalse(ft.isCancelled());
            t.start();
	    ft.run();
	    t.join();
	    assertTrue(ft.isDone());
            assertFalse(ft.isCancelled());
	} catch(InterruptedException e){
            fail("unexpected exception");
            
        }	
    }


    public void testGet_Cancellation(){
	final FutureTask ft = new FutureTask(new Callable(){
		public Object call(){
		    try{
			Thread.sleep(MEDIUM_DELAY_MS);
		    } catch(InterruptedException e){
                        threadFail("unexpected exception");
                    }
                    return Boolean.TRUE;
		}
	    });
	try {
	    Thread.sleep(SHORT_DELAY_MS);
	    Thread t = new Thread(new Runnable(){
		    public void run(){
			try{
			    ft.get();
			    threadFail("should throw");
			} catch(CancellationException success){
                        }
			catch(Exception e){
                            threadFail("unexpected exception");
                        }
		    }
		});
            t.start(); 
	    ft.cancel(true);
	    t.join();
	} catch(InterruptedException success){
            fail("unexpected exception");
        }
    }
    
    public void testGet_Cancellation2(){
	final FutureTask ft = new FutureTask(new Callable(){
		public Object call(){
		    try{
			Thread.sleep(SHORT_DELAY_MS);
		    } catch(InterruptedException e) {
                        threadFail("unexpected exception");
                    }
		    return Boolean.TRUE;
		}
	    });
	try{
	    Thread.sleep(SHORT_DELAY_MS);
	    Thread t = new Thread(new Runnable(){
		    public void run(){
			try{
			    ft.get(MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
			    threadFail("should throw");
			} catch(CancellationException success) {}
			catch(Exception e){
                            threadFail("unexpected exception");
			}
		    }
		});
	    t.start();
	    Thread.sleep(SHORT_DELAY_MS);
	    ft.cancel(true);
	    Thread.sleep(SHORT_DELAY_MS);
	    t.join();
	} catch(InterruptedException ie){
            fail("unexpected exception");
        }
    }

    public void testGet_ExecutionException(){
	final FutureTask ft = new FutureTask(new Callable(){
		public Object call(){
		    int i = 5/0;
		    return Boolean.TRUE;
		}
	    });
	try{
	    ft.run();
	    ft.get();
	    fail("should throw");
	} catch(ExecutionException success){
        }
	catch(Exception e){
            fail("unexpected exception");
	}
    }
  
    public void testTimedGet_ExecutionException2(){
	final FutureTask ft = new FutureTask(new Callable(){
		public Object call(){
		    int i = 5/0;
		    return Boolean.TRUE;
		}
	    });
	try{
	    ft.run();
	    ft.get(SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
	    fail("should throw");
	} catch(ExecutionException success) { 
        } catch(TimeoutException success) { } // unlikely but OK
	catch(Exception e){
            fail("unexpected exception");
	}
    }
      

    public void testGet_InterruptedException(){
	final FutureTask ft = new FutureTask(new NoOpCallable());
	Thread t = new Thread(new Runnable(){
		public void run(){		    
		    try{
			ft.get();
			threadFail("should throw");
		    } catch(InterruptedException success){
                    } catch(Exception e){
                        threadFail("unexpected exception");
                    }
		}
	    });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    }

    public void testTimedGet_InterruptedException2(){
	final FutureTask ft = new FutureTask(new NoOpCallable());
	Thread t = new Thread(new Runnable(){
	 	public void run(){		    
		    try{
			ft.get(LONG_DELAY_MS,TimeUnit.MILLISECONDS);
			threadFail("should throw");
		    } catch(InterruptedException success){}
		    catch(Exception e){
                        threadFail("unexpected exception");
		    }
		}
	    });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    }
    
    public void testGet_TimeoutException(){
	try{
            FutureTask ft = new FutureTask(new NoOpCallable());
	    ft.get(1,TimeUnit.MILLISECONDS);
	    fail("should throw");
	} catch(TimeoutException success){}
	catch(Exception success){
	    fail("unexpected exception");
	}
    }
    
}
