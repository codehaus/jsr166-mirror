/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */


import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigInteger;

public class ExecutorsTest extends JSR166TestCase{
   
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(ExecutorsTest.class);
    }

    private static final String TEST_STRING = "a test string";

    private static class MyTask implements Runnable {
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

    static class TimedCallable<T> implements Callable<T> {
        private final Executor exec;
        private final Callable<T> func;
        private final long msecs;
        
        TimedCallable(Executor exec, Callable<T> func, long msecs) {
            this.exec = exec;
            this.func = func;
            this.msecs = msecs;
        }
        
        public T call() throws Exception {
            Future<T> ftask = Executors.execute(exec, func);
            try {
                return ftask.get(msecs, TimeUnit.MILLISECONDS);
            } finally {
                ftask.cancel(true);
            }
        }
    }


    private static class Fib implements Callable<BigInteger> {
        private final BigInteger n;
        Fib(long n) {
            if (n < 0) throw new IllegalArgumentException("need non-negative arg, but got " + n);
            this.n = BigInteger.valueOf(n);
        }
        public BigInteger call() {
            BigInteger f1 = BigInteger.ONE;
            BigInteger f2 = f1;
            for (BigInteger i = BigInteger.ZERO; i.compareTo(n) < 0; i = i.add(BigInteger.ONE)) {
                BigInteger t = f1.add(f2);
                f1 = f2;
                f2 = t;
            }
            return f1;
        }
    };



    /**
     *   execute(Executor, Runnable) will throw
     *  RejectedExecutionException Attempting to execute a runnable on
     *  a full ThreadPool will cause such an exception here, up to 5
     *  runnables are attempted on a pool capable on handling one
     *  until it throws an exception
     */
    public void testExecute1() {
        ThreadPoolExecutor p = new ThreadPoolExecutor(1,1, SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1));
        try {
            
            for(int i = 0; i < 5; ++i){
                Executors.execute(p, new MediumRunnable(), Boolean.TRUE);
            }
            shouldThrow();
        } catch(RejectedExecutionException success){}
        joinPool(p);
    }

    /**
     *   execute(Executor, Callable) will throw
     *  RejectedExecutionException Attempting to execute a callable on
     *  a full ThreadPool will cause such an exception here, up to 5
     *  runnables are attempted on a pool capable on handling one
     *  until it throws an exception
     */
    public void testExecute2() {
         ThreadPoolExecutor p = new ThreadPoolExecutor(1,1, SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(1));
        try {
            for(int i = 0; i < 5; ++i) {
                Executors.execute(p, new SmallCallable());
            }
            shouldThrow();
        } catch(RejectedExecutionException e){}
        joinPool(p);
    }


    /**
     *   invoke(Executor, Runnable) throws InterruptedException
     *  A single use of invoke starts that will wait long enough
     *  for the invoking thread to be interrupted
     */
    public void testInvoke2() {
        final ThreadPoolExecutor p = new ThreadPoolExecutor(1,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        Executors.invoke(p,new Runnable() {
                                public void run() {
                                    try {
                                        Thread.sleep(MEDIUM_DELAY_MS);
                                        shouldThrow();
                                    } catch(InterruptedException e){
                                    }
                                }
                            });
                    } catch(InterruptedException success){
                    } catch(Exception e) {
                        unexpectedException();
                    }
                    
                }
            }); 
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
        } catch(Exception e){
            unexpectedException();
        }
        joinPool(p);
    }

    /**
     *   invoke(Executor, Runnable) will throw
     *  ExecutionException An ExecutionException occurs when the
     *  underlying Runnable throws an exception, here the
     *  DivideByZeroException will cause an ExecutionException
     */
    public void testInvoke3() {
        ThreadPoolExecutor p = new ThreadPoolExecutor(1,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        try {
            Runnable r = new Runnable() {
                    public void run() {
                        int i = 5/0;
                    }
                };
            
            for(int i =0; i < 5; i++){
                Executors.invoke(p,r);
            }
            
            shouldThrow();
        } catch(ExecutionException success){
        } catch(Exception e){
            unexpectedException();
        }
        joinPool(p);
    }



    /**
     *   invoke(Executor, Callable) throws
     *  InterruptedException A single use of invoke starts that will
     *  wait long enough for the invoking thread to be interrupted
     */
    public void testInvoke5() {
        final ThreadPoolExecutor p = new ThreadPoolExecutor(1,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));
        
        final Callable c = new Callable() {
                public Object call() {
                    try {
                        Executors.invoke(p, new SmallCallable());
                        shouldThrow();
                    } catch(InterruptedException e){}
                    catch(RejectedExecutionException e2){}
                    catch(ExecutionException e3){}
                    return Boolean.TRUE;
                }
            };


        
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        c.call();
                    } catch(Exception e){}
                }
          });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(InterruptedException e){
            unexpectedException();
        }
        
        joinPool(p);
    }

    /**
     *   invoke(Executor, Callable) will throw ExecutionException
     *  An ExecutionException occurs when the underlying Runnable throws
     *  an exception, here the DivideByZeroException will cause an ExecutionException
     */
    public void testInvoke6() {
        ThreadPoolExecutor p = new ThreadPoolExecutor(1,1,SHORT_DELAY_MS, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(10));

        try {
            Callable c = new Callable() {
                    public Object call() {
                        int i = 5/0;
                        return Boolean.TRUE;
                    }
                };
            
            for(int i =0; i < 5; i++){
                Executors.invoke(p,c);
            }
            
            shouldThrow();
        } catch(RejectedExecutionException e){}
        catch(InterruptedException e2){}
        catch(ExecutionException e3){}
        joinPool(p);
    }

    /**
     *
     */
    public void testExecuteRunnable () {
        try {
            Executor e = new DirectExecutor();
            MyTask task = new MyTask();
            assertFalse(task.isCompleted());
            Future<String> future = Executors.execute(e, task, TEST_STRING);
            String result = future.get();
            assertTrue(task.isCompleted());
            assertSame(TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
    }

    /**
     *
     */
    public void testInvokeRunnable () {
        try {
            Executor e = new DirectExecutor();
            MyTask task = new MyTask();
            assertFalse(task.isCompleted());
            Executors.invoke(e, task);
            assertTrue(task.isCompleted());
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
    }

    /**
     *
     */
    public void testExecuteCallable () {
        try {
            Executor e = new DirectExecutor();
            Future<String> future = Executors.execute(e, new StringTask());
            String result = future.get();
            assertSame(TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
    }

    /**
     *
     */
    public void testInvokeCallable () {
        try {
            Executor e = new DirectExecutor();
            String result = Executors.invoke(e, new StringTask());

            assertSame(TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
    }

    /**
     *  timeouts from execute will time out if they compute too long.
     */
    public void testTimedCallable() {
        int N = 10000;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<Callable<BigInteger>> tasks = new ArrayList<Callable<BigInteger>>(N);
        try {
            long startTime = System.currentTimeMillis();
            
            long i = 0;
            while (tasks.size() < N) {
                tasks.add(new TimedCallable<BigInteger>(executor, new Fib(i), 1));
                i += 10;
            }
            
            int iters = 0;
            BigInteger sum = BigInteger.ZERO;
            for (Iterator<Callable<BigInteger>> it = tasks.iterator(); it.hasNext();) {
                try {
                    ++iters;
                    sum = sum.add(it.next().call());
                }
                catch (TimeoutException success) {
                    assertTrue(iters > 0);
                    return;
                }
                catch (Exception e) {
                    unexpectedException();
                }
            }
            // if by chance we didn't ever time out, total time must be small
            long elapsed = System.currentTimeMillis() - startTime;
            assertTrue(elapsed < N);
        }
        finally {
            joinPool(executor);
        }
    }

    


}
