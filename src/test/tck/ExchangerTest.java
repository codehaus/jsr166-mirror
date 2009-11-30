/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ExchangerTest extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(ExchangerTest.class);
    }

    /**
     * exchange exchanges objects across two threads
     */
    public void testExchange() throws InterruptedException {
        final Exchanger e = new Exchanger();
        Thread t1 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Object v = e.exchange(one);
                threadAssertEquals(v, two);
                Object w = e.exchange(v);
                threadAssertEquals(w, one);
            }});
        Thread t2 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Object v = e.exchange(two);
                threadAssertEquals(v, one);
                Object w = e.exchange(v);
                threadAssertEquals(w, two);
            }});

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    /**
     * timed exchange exchanges objects across two threads
     */
    public void testTimedExchange() throws InterruptedException {
        final Exchanger e = new Exchanger();
        Thread t1 = new Thread(new CheckedRunnable() {
            public void realRun() throws Exception {
                Object v = e.exchange(one, SHORT_DELAY_MS, MILLISECONDS);
                threadAssertEquals(v, two);
                Object w = e.exchange(v, SHORT_DELAY_MS, MILLISECONDS);
                threadAssertEquals(w, one);
            }});
        Thread t2 = new Thread(new CheckedRunnable() {
            public void realRun() throws Exception {
                Object v = e.exchange(two, SHORT_DELAY_MS, MILLISECONDS);
                threadAssertEquals(v, one);
                Object w = e.exchange(v, SHORT_DELAY_MS, MILLISECONDS);
                threadAssertEquals(w, two);
            }});

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    /**
     * interrupt during wait for exchange throws IE
     */
    public void testExchange_InterruptedException() throws InterruptedException {
        final Exchanger e = new Exchanger();
        Thread t = new Thread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                e.exchange(one);
            }});

        t.start();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        t.join();
    }

    /**
     * interrupt during wait for timed exchange throws IE
     */
    public void testTimedExchange_InterruptedException() throws InterruptedException {
        final Exchanger e = new Exchanger();
        Thread t = new Thread(new CheckedInterruptedRunnable() {
            public void realRun() throws Exception {
                e.exchange(null, MEDIUM_DELAY_MS, MILLISECONDS);
            }});

        t.start();
        Thread.sleep(SHORT_DELAY_MS);
        t.interrupt();
        t.join();
    }

    /**
     * timeout during wait for timed exchange throws TOE
     */
    public void testExchange_TimeOutException() throws InterruptedException {
        final Exchanger e = new Exchanger();
        Thread t = new Thread(new CheckedRunnable() {
            public void realRun() throws Exception {
                try {
                    e.exchange(null, SHORT_DELAY_MS, MILLISECONDS);
                    threadShouldThrow();
                } catch (TimeoutException success) {}
            }});

        t.start();
        t.join();
    }

    /**
     * If one exchanging thread is interrupted, another succeeds.
     */
    public void testReplacementAfterExchange() throws InterruptedException {
        final Exchanger e = new Exchanger();
        Thread t1 = new Thread(new CheckedInterruptedRunnable() {
            public void realRun() throws InterruptedException {
                Object v = e.exchange(one);
                threadAssertEquals(v, two);
                Object w = e.exchange(v);
            }});
        Thread t2 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Object v = e.exchange(two);
                threadAssertEquals(v, one);
                Thread.sleep(SMALL_DELAY_MS);
                Object w = e.exchange(v);
                threadAssertEquals(w, three);
            }});
        Thread t3 = new Thread(new CheckedRunnable() {
            public void realRun() throws InterruptedException {
                Thread.sleep(SMALL_DELAY_MS);
                Object w = e.exchange(three);
                threadAssertEquals(w, one);
            }});

        t1.start();
        t2.start();
        t3.start();
        Thread.sleep(SHORT_DELAY_MS);
        t1.interrupt();
        t1.join();
        t2.join();
        t3.join();
    }

}
