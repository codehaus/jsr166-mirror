/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.*;
import java.util.*;
import java.security.*;

public class PrivilegedFutureTaskTest extends JSR166TestCase {

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(PrivilegedFutureTaskTest.class);
    }

    Policy savedPolicy;

    /**
     * Establish permissions for get/set for contextClassLoader for each test
     */
    public void setUp() {
	super.setUp();
	savedPolicy = Policy.getPolicy();
        AdjustablePolicy policy = new AdjustablePolicy();
        policy.addPermission(new RuntimePermission("getContextClassLoader"));
        policy.addPermission(new RuntimePermission("setContextClassLoader"));
	Policy.setPolicy(policy);
	//	System.setSecurityManager(new SecurityManager());
    }

    public void tearDown() {
	Policy.setPolicy(savedPolicy);
	super.tearDown();
    }

    /**
     * Subclass to expose protected methods
     */
    static class PublicPrivilegedFutureTask extends PrivilegedFutureTask {
        public PublicPrivilegedFutureTask(Callable r) { super(r); }
        public boolean reset() { return super.reset(); }
        public void setCancelled() { super.setCancelled(); }
        public void setDone() { super.setDone(); }
        public void set(Object x) { super.set(x); }
        public void setException(Throwable t) { super.setException(t); }
    }

    /**
     * Creating a future with a null callable throws NPE
     */
    public void testConstructor() {
        try {
            PrivilegedFutureTask task = new PrivilegedFutureTask(null);
            shouldThrow();
        }
        catch(NullPointerException success) {
        }
    }

    /**
     * isDone is true when a task completes
     */
    public void testIsDone() {
        PrivilegedFutureTask task = new PrivilegedFutureTask( new NoOpCallable());
	task.run();
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    /**
     * reset of a done task succeeds and changes status to not done
     */
    public void testReset() {
        PublicPrivilegedFutureTask task = new PublicPrivilegedFutureTask(new NoOpCallable());
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.reset());	
        assertFalse(task.isDone());
    }

    /**
     * Resetting after cancellation fails
     */
    public void testResetAfterCancel() {
        PublicPrivilegedFutureTask task = new PublicPrivilegedFutureTask(new NoOpCallable());
        assertTrue(task.cancel(false));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
        assertFalse(task.reset());
    }

    /**
     * setDone of new task causes isDone to be true
     */
    public void testSetDone() {
        PublicPrivilegedFutureTask task = new PublicPrivilegedFutureTask(new NoOpCallable());
	task.setDone();
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    /**
     * setCancelled of a new task causes isCancelled to be true
     */
    public void testSetCancelled() {
        PublicPrivilegedFutureTask task = new PublicPrivilegedFutureTask(new NoOpCallable());
        assertTrue(task.cancel(false));
	task.setCancelled();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    /**
     * setting value gauses get to return it
     */
    public void testSet() {
        PublicPrivilegedFutureTask task = new PublicPrivilegedFutureTask(new NoOpCallable());
        task.set(one);
        try {
            assertEquals(task.get(), one);
        }
        catch(Exception e) {
            unexpectedException();
        }
    }

    /**
     * setException causes get to throw ExecutionException
     */
    public void testSetException() {
        Exception nse = new NoSuchElementException();
        PublicPrivilegedFutureTask task = new PublicPrivilegedFutureTask(new NoOpCallable());
        task.setException(nse);
        try {
            Object x = task.get();
            shouldThrow();
        }
        catch(ExecutionException ee) {
            Throwable cause = ee.getCause();
            assertEquals(cause, nse);
        }
        catch(Exception e) {
            unexpectedException();
        }
    }

    /**
     *  Cancelling before running succeeds
     */
    public void testCancelBeforeRun() {
        PrivilegedFutureTask task = new PrivilegedFutureTask( new NoOpCallable());
        assertTrue(task.cancel(false));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    /**
     * Cancel(true) before run succeeds
     */
    public void testCancelBeforeRun2() {
        PrivilegedFutureTask task = new PrivilegedFutureTask( new NoOpCallable());
        assertTrue(task.cancel(true));
	task.run();
	assertTrue(task.isDone());
	assertTrue(task.isCancelled());
    }

    /**
     * cancel of a completed task fails
     */
    public void testCancelAfterRun() {
        PrivilegedFutureTask task = new PrivilegedFutureTask( new NoOpCallable());
	task.run();
        assertFalse(task.cancel(false));
	assertTrue(task.isDone());
	assertFalse(task.isCancelled());
    }

    /**
     * cancel(true) interrupts a running task
     */
    public void testCancelInterrupt() {
        PrivilegedFutureTask task = new PrivilegedFutureTask( new Callable() {
                public Object call() {
                    try {
                        Thread.sleep(MEDIUM_DELAY_MS);
                        threadShouldThrow();
                    }
                    catch (InterruptedException success) {}
                    return Boolean.TRUE;
                } });
        Thread t = new  Thread(task);
        t.start();
        
        try {
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(task.cancel(true));
            t.join();
            assertTrue(task.isDone());
            assertTrue(task.isCancelled());
        } catch(InterruptedException e){
            unexpectedException();
        }
    }


    /**
     * cancel(false) does not interrupt a running task
     */
    public void testCancelNoInterrupt() {
        PrivilegedFutureTask task = new PrivilegedFutureTask( new Callable() {
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
        
        try {
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(task.cancel(false));
            t.join();
            assertTrue(task.isDone());
            assertTrue(task.isCancelled());
        } catch(InterruptedException e){
            unexpectedException();
        }
    }

    /**
     * set in one thread causes get in another thread to retrieve value
     */
    public void testGet1() {
	final PrivilegedFutureTask ft = new PrivilegedFutureTask(new Callable() {
		public Object call() {
		    try {
			Thread.sleep(MEDIUM_DELAY_MS);
		    } catch(InterruptedException e){
                        threadUnexpectedException();
                    }
                    return Boolean.TRUE;
		}
	});
	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
			ft.get();
		    } catch(Exception e){
                        threadUnexpectedException();
                    }
		}
	    });
	try {
            assertFalse(ft.isDone());
            assertFalse(ft.isCancelled());
            t.start();
	    Thread.sleep(SHORT_DELAY_MS);
	    ft.run();
	    t.join();
	    assertTrue(ft.isDone());
            assertFalse(ft.isCancelled());
	} catch(InterruptedException e){
            unexpectedException();

        }	
    }

    /**
     * set in one thread causes timed get in another thread to retrieve value
     */
    public void testTimedGet1() {
	final PrivilegedFutureTask ft = new PrivilegedFutureTask(new Callable() {
		public Object call() {
		    try {
			Thread.sleep(MEDIUM_DELAY_MS);
		    } catch(InterruptedException e){
                        threadUnexpectedException();
                    }
                    return Boolean.TRUE;
		}
            });
	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
			ft.get(SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
		    } catch(TimeoutException success) {
                    } catch(Exception e){
                        threadUnexpectedException();
                    }
		}
	    });
	try {
            assertFalse(ft.isDone());
            assertFalse(ft.isCancelled());
            t.start();
	    ft.run();
	    t.join();
	    assertTrue(ft.isDone());
            assertFalse(ft.isCancelled());
	} catch(InterruptedException e){
            unexpectedException();
            
        }	
    }

    /**
     *  Cancelling a task causes timed get in another thread to throw CancellationException
     */
    public void testTimedGet_Cancellation() {
	final PrivilegedFutureTask ft = new PrivilegedFutureTask(new Callable() {
		public Object call() {
		    try {
			Thread.sleep(SMALL_DELAY_MS);
                        threadShouldThrow();
		    } catch(InterruptedException e) {
                    }
		    return Boolean.TRUE;
		}
	    });
	try {
	    Thread t1 = new Thread(new Runnable() {
		    public void run() {
			try {
			    ft.get(MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
			    threadShouldThrow();
			} catch(CancellationException success) {}
			catch(Exception e){
                            threadUnexpectedException();
			}
		    }
		});
            Thread t2 = new Thread(ft);
            t1.start(); 
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
	    ft.cancel(true);
	    t1.join();
	    t2.join();
	} catch(InterruptedException ie){
            unexpectedException();
        }
    }

    /**
     * Cancelling a task causes get in another thread to throw CancellationException
     */
    public void testGet_Cancellation() {
	final PrivilegedFutureTask ft = new PrivilegedFutureTask(new Callable() {
		public Object call() {
		    try {
			Thread.sleep(MEDIUM_DELAY_MS);
                        threadShouldThrow();
		    } catch(InterruptedException e){
                    }
                    return Boolean.TRUE;
		}
	    });
	try {
	    Thread t1 = new Thread(new Runnable() {
		    public void run() {
			try {
			    ft.get();
			    threadShouldThrow();
			} catch(CancellationException success){
                        }
			catch(Exception e){
                            threadUnexpectedException();
                        }
		    }
		});
            Thread t2 = new Thread(ft);
            t1.start(); 
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
	    ft.cancel(true);
	    t1.join();
	    t2.join();
	} catch(InterruptedException success){
            unexpectedException();
        }
    }
    

    /**
     * A runtime exception in task causes get to throw ExecutionException
     */
    public void testGet_ExecutionException() {
	final PrivilegedFutureTask ft = new PrivilegedFutureTask(new Callable() {
		public Object call() {
		    int i = 5/0;
		    return Boolean.TRUE;
		}
	    });
	try {
	    ft.run();
	    ft.get();
	    shouldThrow();
	} catch(ExecutionException success){
        }
	catch(Exception e){
            unexpectedException();
	}
    }
  
    /**
     *  A runtime exception in task causes timed get to throw ExecutionException
     */
    public void testTimedGet_ExecutionException2() {
	final PrivilegedFutureTask ft = new PrivilegedFutureTask(new Callable() {
		public Object call() {
		    int i = 5/0;
		    return Boolean.TRUE;
		}
	    });
	try {
	    ft.run();
	    ft.get(SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
	    shouldThrow();
	} catch(ExecutionException success) { 
        } catch(TimeoutException success) { } // unlikely but OK
	catch(Exception e){
            unexpectedException();
	}
    }
      

    /**
     * Interrupting a waiting get causes it to throw InterruptedException
     */
    public void testGet_InterruptedException() {
	final PrivilegedFutureTask ft = new PrivilegedFutureTask(new NoOpCallable());
	Thread t = new Thread(new Runnable() {
		public void run() {		    
		    try {
			ft.get();
			threadShouldThrow();
		    } catch(InterruptedException success){
                    } catch(Exception e){
                        threadUnexpectedException();
                    }
		}
	    });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(Exception e){
            unexpectedException();
        }
    }

    /**
     *  Interrupting a waiting timed get causes it to throw InterruptedException
     */
    public void testTimedGet_InterruptedException2() {
	final PrivilegedFutureTask ft = new PrivilegedFutureTask(new NoOpCallable());
	Thread t = new Thread(new Runnable() {
	 	public void run() {		    
		    try {
			ft.get(LONG_DELAY_MS,TimeUnit.MILLISECONDS);
			threadShouldThrow();
		    } catch(InterruptedException success){}
		    catch(Exception e){
                        threadUnexpectedException();
		    }
		}
	    });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(Exception e){
            unexpectedException();
        }
    }
    
    /**
     * A timed out timed get throws TimeoutException
     */
    public void testGet_TimeoutException() {
	try {
            PrivilegedFutureTask ft = new PrivilegedFutureTask(new NoOpCallable());
	    ft.get(1,TimeUnit.MILLISECONDS);
	    shouldThrow();
	} catch(TimeoutException success){}
	catch(Exception success){
	    unexpectedException();
	}
    }

    /**
     * Without privileges, run with default context throws ACE
     */
    public void testRunWithNoPrivs() {
        AdjustablePolicy policy = new AdjustablePolicy();
	Policy.setPolicy(policy);
        try {
            PrivilegedFutureTask task = new PrivilegedFutureTask(new NoOpCallable());
            task.run();
            shouldThrow();
        } catch(AccessControlException success) {
        }
    }
    
}
