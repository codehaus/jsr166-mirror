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
import java.security.*;

public class AbstractExecutorServiceTest extends JSR166TestCase{
    public static void main(String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(ExecutorsTest.class);
    }

    /** 
     * A no-frills implementation of AbstractExecutorService, designed
     * to test the submit/invoke methods only.
     */
    static class DirectExecutorService extends AbstractExecutorService {
        public void execute(Runnable r) { r.run(); }
        public void shutdown() { shutdown = true; }
        public List<Runnable> shutdownNow() { shutdown = true; return Collections.EMPTY_LIST; }
        public boolean isShutdown() { return shutdown; }
        public boolean isTerminated() { return isShutdown(); }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return isShutdown(); }
        private volatile boolean shutdown = false;
    }

    private static final String TEST_STRING = "a test string";

    private static class StringTask implements Callable<String> {
        public String call() { return TEST_STRING; }
    }

    /**
     * execute of runnable runs it to completion
     */
    public void testExecuteRunnable() {
        try {
            ExecutorService e = new DirectExecutorService();
            TrackedShortRunnable task = new TrackedShortRunnable();
            assertFalse(task.done);
            Future<?> future = e.submit(task);
            future.get();
            assertTrue(task.done);
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
    public void testInvokeRunnable() {
        try {
            ExecutorService e = new DirectExecutorService();
            TrackedShortRunnable task = new TrackedShortRunnable();
            assertFalse(task.done);
            e.invoke(task);
            assertTrue(task.done);
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
    public void testExecuteCallable() {
        try {
            ExecutorService e = new DirectExecutorService();
            Future<String> future = e.submit(new StringTask());
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
     * execute of a privileged action runs it to completion
     */
    public void testExecutePrivilegedAction() {
        Policy savedPolicy = Policy.getPolicy();
        AdjustablePolicy policy = new AdjustablePolicy();
        policy.addPermission(new RuntimePermission("getContextClassLoader"));
        policy.addPermission(new RuntimePermission("setContextClassLoader"));
        Policy.setPolicy(policy);
        try {
            ExecutorService e = new DirectExecutorService();
            Future future = e.submit(new PrivilegedAction() {
                    public Object run() {
                        return TEST_STRING;
                    }});

            Object result = future.get();
            assertSame(TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
        finally {
            Policy.setPolicy(savedPolicy);
        }
    }

    /**
     * execute of a privileged exception action runs it to completion
     */
    public void testExecutePrivilegedExceptionAction() {
        Policy savedPolicy = Policy.getPolicy();
        AdjustablePolicy policy = new AdjustablePolicy();
        policy.addPermission(new RuntimePermission("getContextClassLoader"));
        policy.addPermission(new RuntimePermission("setContextClassLoader"));
        Policy.setPolicy(policy);
        try {
            ExecutorService e = new DirectExecutorService();
            Future future = e.submit(new PrivilegedExceptionAction() {
                    public Object run() {
                        return TEST_STRING;
                    }});

            Object result = future.get();
            assertSame(TEST_STRING, result);
        }
        catch (ExecutionException ex) {
            unexpectedException();
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
        finally {
            Policy.setPolicy(savedPolicy);
        }
    }

    /**
     * execute of a failed privileged exception action reports exception
     */
    public void testExecuteFailedPrivilegedExceptionAction() {
        Policy savedPolicy = Policy.getPolicy();
        AdjustablePolicy policy = new AdjustablePolicy();
        policy.addPermission(new RuntimePermission("getContextClassLoader"));
        policy.addPermission(new RuntimePermission("setContextClassLoader"));
        Policy.setPolicy(policy);
        try {
            ExecutorService e = new DirectExecutorService();
            Future future = e.submit(new PrivilegedExceptionAction() {
                    public Object run() throws Exception {
                        throw new IndexOutOfBoundsException();
                    }});

            Object result = future.get();
            shouldThrow();
        }
        catch (ExecutionException success) {
        }
        catch (InterruptedException ex) {
            unexpectedException();
        }
        finally {
            Policy.setPolicy(savedPolicy);
        }
    }

    /**
     * invoke of a collable runs it to completion
     */
    public void testInvokeCallable() {
        try {
            ExecutorService e = new DirectExecutorService();
            String result = e.invoke(new StringTask());

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
     * execute with a null runnable throws NPE
     */
    public void testExecuteNullRunnable() {
        try {
            ExecutorService e = new DirectExecutorService();
            TrackedShortRunnable task = null;
            Future<?> future = e.submit(task);
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
    public void testInvokeNullRunnable() {
        try {
            ExecutorService e = new DirectExecutorService();
            TrackedShortRunnable task = null;
            e.invoke(task);
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
    public void testExecuteNullCallable() {
        try {
            ExecutorService e = new DirectExecutorService();
            StringTask t = null;
            Future<String> future = e.submit(t);
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
    public void testInvokeNullCallable() {
        try {
            ExecutorService e = new DirectExecutorService();
            StringTask t = null;
            String result = e.invoke(t);
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
                p.submit(new MediumRunnable());
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
                p.submit(new SmallCallable());
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
                        p.invoke(new Runnable() {
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
                p.invoke(r);
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
                        p.invoke(new SmallCallable());
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
                p.invoke(c);
            }

            shouldThrow();
        }
        catch(ExecutionException success){
        } catch(Exception e) {
            unexpectedException();
        }
        joinPool(p);
    }

}