/*
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @summary Test drainTo failing due to c.add throwing
 */

import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class DrainToFails {
    final int CAPACITY = 10;

    void test(String[] args) throws Throwable {
        test(new LinkedBlockingQueue(CAPACITY));
        test(new LinkedBlockingDeque(CAPACITY));
        test(new ArrayBlockingQueue(CAPACITY));
    }

    void test(final BlockingQueue q) throws Throwable {
        System.out.println(q.getClass().getSimpleName());
        for (int i = 0; i < CAPACITY; i++)
            q.add(i);
        List<Thread> putters = new ArrayList<Thread>();
        for (int i = 0; i < 2; i++) {
            Thread putter = new Thread(putter(q, 42 + i));
            putters.add(putter);
            putter.setDaemon(true);
            putter.start();
        }
        ArrayBlockingQueue q2 = new ArrayBlockingQueue(2);
        try {
            q.drainTo(q2, 4);
            fail("should throw");
        } catch (IllegalStateException success) {
            for (Thread putter : putters) {
                putter.join(2000L);
                check(! putter.isAlive());
            }
            // expect q to contain [2, 3, 4, 5, 6, 7, 8, 9, 42, 43]
            // expect q2 to contain [0, 1]
            equal(2, q2.size());
            equal(0, q2.poll());
            equal(1, q2.poll());
            check(q2.isEmpty());
            for (int i = 2; i < CAPACITY; i++)
                equal(i, q.poll());
            equal(2, q.size());
            check(q.contains(42));
            check(q.contains(43));
            // System.err.println(q);
            // System.err.println(q2);
        }
    }

    Runnable putter(final BlockingQueue q, final int elt) {
        return new Runnable() {
            public void run() {
                try { q.put(elt); }
                catch (Throwable t) { unexpected(t); }}};
    }

    //--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new DrainToFails().instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
