package java.util.concurrent;

import java.math.BigInteger;

import junit.framework.TestCase;

/**
 */
public class TimedCallableTest extends TestCase {
    
    private final Executor EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long MSECS = 10;

    private static class Fib implements Callable<BigInteger> {
        private final BigInteger n;
        Fib(long n) {
            if (n < 0) throw new IllegalArgumentException("need non-negative arg, but got " + n);
            this.n = BigInteger.valueOf(n);
        }
        public BigInteger call() {
            BigInteger f1 = BigInteger.ONE;
            BigInteger f2 = f1;
            for (BigInteger i = BigInteger.ZERO;  i.compareTo(n) < 0;  i = i.add(BigInteger.ONE)) {
                BigInteger t = f1.add(f2);
                f1 = f2;
                f2 = t;
            }
            return f1;
        }
    };
    
    public void testTimedCallable() {
        for (long i = 0; i < 100; ++i) {
            Callable<BigInteger> c = new TimedCallable<BigInteger>(EXECUTOR, new Fib(i), MSECS);
            try {
                BigInteger f = c.call();
                System.out.println("fib(" + i + ") = " + f);
            }
            catch (TimeoutException e) {
                System.out.println("fib(" + i + ") = TIMEOUT");
            }
            catch (Exception e) {
                System.err.println("unexpected exception: " + e);
                return;
            }
        }
    }
}
