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

public class ExecutorsTest extends JSR166TestCase{
    public static void main(String[] args) {
        junit.textui.TestRunner.run (suite());  
    }
    public static Test suite() {
        return new TestSuite(ExecutorsTest.class);
    }

    static class TimedCallable<T> implements Callable<T> {
        private final ExecutorService exec;
        private final Callable<T> func;
        private final long msecs;
        
        TimedCallable(ExecutorService exec, Callable<T> func, long msecs) {
            this.exec = exec;
            this.func = func;
            this.msecs = msecs;
        }
        
        public T call() throws Exception {
            Future<T> ftask = exec.submit(func);
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
        ExecutorService e = Executors.newCachedThreadPool(new SimpleThreadFactory());
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
        ExecutorService e = Executors.newSingleThreadExecutor(new SimpleThreadFactory());
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
        ExecutorService e = Executors.newFixedThreadPool(2, new SimpleThreadFactory());
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

    
    /**
     * ThreadPoolExecutor using defaultThreadFactory has
     * specified group, priority, daemon status, and name
     */
    public void testDefaultThreadFactory() {
        final ThreadGroup egroup = Thread.currentThread().getThreadGroup();
        Runnable r = new Runnable() {
                public void run() {
                    Thread current = Thread.currentThread();
                    threadAssertTrue(!current.isDaemon());
                    threadAssertTrue(current.getPriority() == Thread.NORM_PRIORITY);
                    ThreadGroup g = current.getThreadGroup();
                    SecurityManager s = System.getSecurityManager();
                    if (s != null)
                        threadAssertTrue(g == s.getThreadGroup());
                    else
                        threadAssertTrue(g == egroup);
                    String name = current.getName();
                    threadAssertTrue(name.endsWith("thread-1"));
                }
            };
        ExecutorService e = Executors.newSingleThreadExecutor(Executors.defaultThreadFactory());
        
        e.execute(r);
        e.shutdown();
        try {
            Thread.sleep(SHORT_DELAY_MS);
        } catch (Exception eX) {
            unexpectedException();
        } finally {
            joinPool(e);
        }
    }

    /**
     * ThreadPoolExecutor using privilegedThreadFactory has
     * specified group, priority, daemon status, name,
     * access control context and context class loader
     */
    public void testPrivilegedThreadFactory() {
        Policy savedPolicy = Policy.getPolicy();
        AdjustablePolicy policy = new AdjustablePolicy();
        policy.addPermission(new RuntimePermission("getContextClassLoader"));
        policy.addPermission(new RuntimePermission("setContextClassLoader"));
        Policy.setPolicy(policy);
        final ThreadGroup egroup = Thread.currentThread().getThreadGroup();
        final ClassLoader thisccl = Thread.currentThread().getContextClassLoader();
        final AccessControlContext thisacc = AccessController.getContext();
        Runnable r = new Runnable() {
                public void run() {
                    Thread current = Thread.currentThread();
                    threadAssertTrue(!current.isDaemon());
                    threadAssertTrue(current.getPriority() == Thread.NORM_PRIORITY);
                    ThreadGroup g = current.getThreadGroup();
                    SecurityManager s = System.getSecurityManager();
                    if (s != null)
                        threadAssertTrue(g == s.getThreadGroup());
                    else
                        threadAssertTrue(g == egroup);
                    String name = current.getName();
                    threadAssertTrue(name.endsWith("thread-1"));
                    threadAssertTrue(thisccl == current.getContextClassLoader());
                    threadAssertTrue(thisacc.equals(AccessController.getContext()));
                }
            };
        ExecutorService e = Executors.newSingleThreadExecutor(Executors.privilegedThreadFactory());
        
        Policy.setPolicy(savedPolicy);
        e.execute(r);
        e.shutdown();
        try {
            Thread.sleep(SHORT_DELAY_MS);
        } catch (Exception ex) {
            unexpectedException();
        } finally {
            joinPool(e);
        }

    }

}
