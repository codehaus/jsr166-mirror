/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */


import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class ExecutorsTest extends TestCase{
    
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    

    public static Test suite() {
	return new TestSuite(ExecutorsTest.class);
    }

    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 

    class SleepRun implements Runnable {
        public void run() {
            try{
                Thread.sleep(MEDIUM_DELAY_MS);
            } catch(InterruptedException e){
                fail("unexpected exception");
            }
        }
    }
    

    class SleepCall implements Callable {
        public Object call(){
            try{
                Thread.sleep(MEDIUM_DELAY_MS);
            }catch(InterruptedException e){
                fail("unexpected exception");
            }
            return Boolean.TRUE;
        }
    }



    /**
     *  Test to verify execute(Executor, Runnable) will throw
     *  RejectedExecutionException Attempting to execute a runnable on
     *  a full ThreadPool will cause such an exception here, up to 5
     *  runnables are attempted on a pool capable on handling one
     *  until it throws an exception
     */
    public void testExecute1(){
        ThreadPoolExecutor p = new ThreadPoolExecutor(1,1,100L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1));
        try{
            
            for(int i = 0; i < 5; ++i){
                Executors.execute(p, new SleepRun(), Boolean.TRUE);
            }
            fail("should throw");
        } catch(RejectedExecutionException success){}
        p.shutdownNow();
    }

    /**
     *  Test to verify execute(Executor, Callable) will throw
     *  RejectedExecutionException Attempting to execute a callable on
     *  a full ThreadPool will cause such an exception here, up to 5
     *  runnables are attempted on a pool capable on handling one
     *  until it throws an exception
     */
    public void testExecute2(){
         ThreadPoolExecutor p = new ThreadPoolExecutor(1,1,100L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1));
        try{
            for(int i = 0; i < 5; ++i) {
                Executors.execute(p, new SleepCall());
            }
            fail("should throw");
        }catch(RejectedExecutionException e){}
        p.shutdownNow();
    }


    /**
     *  Test to verify invoke(Executor, Runnable) throws InterruptedException
     *  A single use of invoke starts that will wait long enough
     *  for the invoking thread to be interrupted
     */
    public void testInvoke2(){
        final ThreadPoolExecutor p = new ThreadPoolExecutor(1,1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
                        Executors.invoke(p,new Runnable(){
                                public void run(){
                                    try{
                                        Thread.sleep(MEDIUM_DELAY_MS);
                                        fail("should throw");
                                    }catch(InterruptedException e){
                                    }
                                }
                            });
                    } catch(InterruptedException success){
                    } catch(Exception e) {
                        fail("unexpected exception");
                    }
                    
                }
            }); 
        try{
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
        }catch(Exception e){
            fail("unexpected exception");
        }
        p.shutdownNow();
    }

    /**
     *  Test to verify invoke(Executor, Runnable) will throw
     *  ExecutionException An ExecutionException occurs when the
     *  underlying Runnable throws an exception, here the
     *  DivideByZeroException will cause an ExecutionException
     */
    public void testInvoke3(){
        ThreadPoolExecutor p = new ThreadPoolExecutor(1,1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        try{
            Runnable r = new Runnable(){
                    public void run(){
                        int i = 5/0;
                    }
                };
            
            for(int i =0; i < 5; i++){
                Executors.invoke(p,r);
            }
            
            fail("should throw");
        } catch(ExecutionException success){
        } catch(Exception e){
            fail("should throw EE");
        }
        p.shutdownNow();
    }



    /**
     *  Test to verify invoke(Executor, Callable) throws
     *  InterruptedException A single use of invoke starts that will
     *  wait long enough for the invoking thread to be interrupted
     */
    public void testInvoke5(){
        final ThreadPoolExecutor p = new ThreadPoolExecutor(1,1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        
        final Callable c = new Callable(){
                public Object call(){
                    try{
                        Executors.invoke(p, new SleepCall());
                        fail("should throw");
                    }catch(InterruptedException e){}
                    catch(RejectedExecutionException e2){}
                    catch(ExecutionException e3){}
                    return Boolean.TRUE;
                }
            };


        
        Thread t = new Thread(new Runnable(){
                public void run(){
                    try{
                        c.call();
                    }catch(Exception e){}
                }
          });
        try{
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        }catch(InterruptedException e){
            fail("unexpected exception");
        }
        
        p.shutdownNow();
    }

    /**
     *  Test to verify invoke(Executor, Callable) will throw ExecutionException
     *  An ExecutionException occurs when the underlying Runnable throws
     *  an exception, here the DivideByZeroException will cause an ExecutionException
     */
    public void testInvoke6(){
        ThreadPoolExecutor p = new ThreadPoolExecutor(1,1,100L,TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));

        try{
            Callable c = new Callable(){
                    public Object call(){
                        int i = 5/0;
                        return Boolean.TRUE;
                    }
                };
            
            for(int i =0; i < 5; i++){
                Executors.invoke(p,c);
            }
            
            fail("should throw");
        }catch(RejectedExecutionException e){}
        catch(InterruptedException e2){}
        catch(ExecutionException e3){}
        p.shutdownNow();
    }

    public void testExecuteRunnable () {
        try {
            Executor e = new DirectExecutor();
            Task task = new Task();

            assertFalse("task should not be complete", task.isCompleted());

            Future<String> future = Executors.execute(e, task, TEST_STRING);
            String result = future.get();

            assertTrue("task should be complete", task.isCompleted());
            assertSame("should return test string", TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            fail("Unexpected exception");
        }
        catch (InterruptedException ex) {
            fail("Unexpected exception");
        }
    }

    public void testInvokeRunnable () {
        try {
            Executor e = new DirectExecutor();
            Task task = new Task();

            assertFalse("task should not be complete", task.isCompleted());

            Executors.invoke(e, task);

            assertTrue("task should be complete", task.isCompleted());
        }
        catch (ExecutionException ex) {
            fail("Unexpected exception");
        }
        catch (InterruptedException ex) {
            fail("Unexpected exception");
        }
    }

    public void testExecuteCallable () {
        try {
            Executor e = new DirectExecutor();
            Future<String> future = Executors.execute(e, new StringTask());
            String result = future.get();

            assertSame("should return test string", TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            fail("Unexpected exception");
        }
        catch (InterruptedException ex) {
            fail("Unexpected exception");
        }
    }

    public void testInvokeCallable () {
        try {
            Executor e = new DirectExecutor();
            String result = Executors.invoke(e, new StringTask());

            assertSame("should return test string", TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            fail("Unexpected exception" );
        }
        catch (InterruptedException ex) {
            fail("Unexpected exception");
        }
    }

    private static final String TEST_STRING = "a test string";

    private static class Task implements Runnable {
        public void run() { completed = true; }
        public boolean isCompleted() { return completed; }
        public void reset() { completed = false; }
        private boolean completed = false;
    }

    private static class StringTask implements Callable<String> {
        public String call() { return TEST_STRING; }
    }

    static class DirectExecutor implements Executor {
        public void execute(Runnable r) {
            r.run();
        }
    }


}
