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
     * For use as ThreadFactory in constructors
     */
    static class MyThreadFactory implements ThreadFactory{
        public Thread newThread(Runnable r){
            return new Thread(r);
        }   
    }

    /**
     * A newCachedThreadPool can execute runnables
     */
    public void testNewCachedThreadPool1() {
        ExecutorService e = Executors.newCachedThreadPool();
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.shutdown();
    }

    /**
     * A newCachedThreadPool with given ThreadFactory can execute runnables
     */
    public void testNewCachedThreadPool2() {
        ExecutorService e = Executors.newCachedThreadPool(new MyThreadFactory());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.shutdown();
    }

    /**
     * A newCachedThreadPool with null ThreadFactory throws NPE
     */
    public void testNewCachedThreadPool3() {
        try {
            ExecutorService e = Executors.newCachedThreadPool(null);
            shouldThrow();
        }
        catch(NullPointerException success) {
        }
    }


    /**
     * A new SingleThreadExecutor can execute runnables
     */
    public void testNewSingleThreadExecutor1() {
        ExecutorService e = Executors.newSingleThreadExecutor();
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.shutdown();
    }

    /**
     * A new SingleThreadExecutor with given ThreadFactory can execute runnables
     */
    public void testNewSingleThreadExecutor2() {
        ExecutorService e = Executors.newSingleThreadExecutor(new MyThreadFactory());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.shutdown();
    }

    /**
     * A new SingleThreadExecutor with null ThreadFactory throws NPE
     */
    public void testNewSingleThreadExecutor3() {
        try {
            ExecutorService e = Executors.newSingleThreadExecutor(null);
            shouldThrow();
        }
        catch(NullPointerException success) {
        }
    }

    /**
     * A new newFixedThreadPool can execute runnables
     */
    public void testNewFixedThreadPool1() {
        ExecutorService e = Executors.newFixedThreadPool(2);
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.shutdown();
    }

    /**
     * A new newFixedThreadPool with given ThreadFactory can execute runnables
     */
    public void testNewFixedThreadPool2() {
        ExecutorService e = Executors.newFixedThreadPool(2, new MyThreadFactory());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.execute(new NoOpRunnable());
        e.shutdown();
    }

    /**
     * A new newFixedThreadPool with null ThreadFactory throws NPE
     */
    public void testNewFixedThreadPool3() {
        try {
            ExecutorService e = Executors.newFixedThreadPool(2, null);
            shouldThrow();
        }
        catch(NullPointerException success) {
        }
    }

    /**
     * A new newFixedThreadPool with 0 threads throws IAE
     */
    public void testNewFixedThreadPool4() {
        try {
            ExecutorService e = Executors.newFixedThreadPool(0);
            shouldThrow();
        }
        catch(IllegalArgumentException success) {
        }
    }

    /**
     * execute of runnable runs it to completion 
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
     * invoke of a runnable runs it to completion 
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
     * execute of a callable runs it to completion
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
     * invoke of a collable runs it to completion
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
     * execute with null executor throws NPE
     */
    public void testNullExecuteRunnable () {
        try {
            MyTask task = new MyTask();
            assertFalse(task.isCompleted());
            Future<String> future = Executors.execute(null, task, TEST_STRING);
            shouldThrow();
        }
        catch (NullPointerException success) {
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * execute with a null runnable throws NPE
     */
    public void testExecuteNullRunnable() {
        try {
            Executor e = new DirectExecutor();
            MyTask task = null;
            Future<String> future = Executors.execute(e, task, TEST_STRING);
            shouldThrow();
        }
        catch (NullPointerException success) {
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * invoke of a null runnable throws NPE
     */
    public void testInvokeNullRunnable () {
        try {
            Executor e = new DirectExecutor();
            MyTask task = null;
            Executors.invoke(e, task);
            shouldThrow();
        }
        catch (NullPointerException success) {
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * execute of a null callable throws NPE
     */
    public void testExecuteNullCallable () {
        try {
            Executor e = new DirectExecutor();
            StringTask t = null;
            Future<String> future = Executors.execute(e, t);
            shouldThrow();
        }
        catch (NullPointerException success) {
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * invoke of a null callable throws NPE
     */
    public void testInvokeNullCallable () {
        try {
            Executor e = new DirectExecutor();
            StringTask t = null;
            String result = Executors.invoke(e, t);
            shouldThrow();
        }
        catch (NullPointerException success) {
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     *  execute(Executor, Runnable) throws RejectedExecutionException
     *  if saturated.
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
     *  execute(Executor, Callable)throws RejectedExecutionException
     *  if saturated.
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
     *  invoke(Executor, Runnable) throws InterruptedException if
     *  caller interrupted.
     */
    public void testInterruptedInvoke() {
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
     *  invoke(Executor, Runnable) throws ExecutionException if
     *  runnable throws exception.
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
     *  invoke(Executor, Callable) throws InterruptedException if
     *  callable throws exception
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
     *  invoke(Executor, Callable) will throw ExecutionException
     *  if callable throws exception
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
        } 
        catch(ExecutionException success){
        } catch(Exception e) {
            unexpectedException();
        }
        joinPool(p);
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
