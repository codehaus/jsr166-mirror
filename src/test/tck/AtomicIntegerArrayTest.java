/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.*;
import java.io.*;

public class AtomicIntegerArrayTest extends JSR166TestCase {

    public static void main (String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicIntegerArrayTest.class);
    }


    /**
     *
     */
    public void testConstructor() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE);
        for (int i = 0; i < SIZE; ++i) 
            assertEquals(0,ai.get(i));
    }

    /**
     *
     */
    public void testGetSet() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(1,ai.get(i));
            ai.set(i, 2);
            assertEquals(2,ai.get(i));
            ai.set(i, -3);
            assertEquals(-3,ai.get(i));
        }
    }

    /**
     *
     */
    public void testCompareAndSet() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertTrue(ai.compareAndSet(i, 1,2));
            assertTrue(ai.compareAndSet(i, 2,-4));
            assertEquals(-4,ai.get(i));
            assertFalse(ai.compareAndSet(i, -5,7));
            assertFalse((7 == ai.get(i)));
            assertTrue(ai.compareAndSet(i, -4,7));
            assertEquals(7,ai.get(i));
        }
    }

    /**
     *
     */
    public void testWeakCompareAndSet() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            while(!ai.weakCompareAndSet(i, 1,2));
            while(!ai.weakCompareAndSet(i, 2,-4));
            assertEquals(-4,ai.get(i));
            while(!ai.weakCompareAndSet(i, -4,7));
            assertEquals(7,ai.get(i));
        }
    }

    /**
     *
     */
    public void testGetAndSet() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(1,ai.getAndSet(i,0));
            assertEquals(0,ai.getAndSet(i,-10));
            assertEquals(-10,ai.getAndSet(i,1));
        }
    }

    /**
     *
     */
    public void testGetAndAdd() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(1,ai.getAndAdd(i,2));
            assertEquals(3,ai.get(i));
            assertEquals(3,ai.getAndAdd(i,-4));
            assertEquals(-1,ai.get(i));
        }
    }

    /**
     *
     */
    public void testGetAndDecrement() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(1,ai.getAndDecrement(i));
            assertEquals(0,ai.getAndDecrement(i));
            assertEquals(-1,ai.getAndDecrement(i));
        }
    }

    /**
     *
     */
    public void testGetAndIncrement() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(1,ai.getAndIncrement(i));
            assertEquals(2,ai.get(i));
            ai.set(i,-2);
            assertEquals(-2,ai.getAndIncrement(i));
            assertEquals(-1,ai.getAndIncrement(i));
            assertEquals(0,ai.getAndIncrement(i));
            assertEquals(1,ai.get(i));
        }
    }

    /**
     *
     */
    public void testAddAndGet() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(3,ai.addAndGet(i,2));
            assertEquals(3,ai.get(i));
            assertEquals(-1,ai.addAndGet(i,-4));
            assertEquals(-1,ai.get(i));
        }
    }

    /**
     *
     */
    public void testDecrementAndGet() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(0,ai.decrementAndGet(i));
            assertEquals(-1,ai.decrementAndGet(i));
            assertEquals(-2,ai.decrementAndGet(i));
            assertEquals(-2,ai.get(i));
        }
    }

    /**
     *
     */
    public void testIncrementAndGet() {
        AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, 1);
            assertEquals(2,ai.incrementAndGet(i));
            assertEquals(2,ai.get(i));
            ai.set(i, -2);
            assertEquals(-1,ai.incrementAndGet(i));
            assertEquals(0,ai.incrementAndGet(i));
            assertEquals(1,ai.incrementAndGet(i));
            assertEquals(1,ai.get(i));
        }
    }

    static final int COUNTDOWN = 100000;
    
    class Counter implements Runnable {
        final AtomicIntegerArray ai;
        volatile int counts;
        Counter(AtomicIntegerArray a) { ai = a; }
        public void run() {
            for (;;) {
                boolean done = true;
                for (int i = 0; i < ai.length(); ++i) {
                    int v = ai.get(i);
                    threadAssertTrue(v >= 0);
                    if (v != 0) {
                        done = false;
                        if (ai.compareAndSet(i, v, v-1))
                            ++counts;
                    }
                }
                if (done)
                    break;
            }
        }
    }

    /**
     *
     */
    public void testCountingInMultipleThreads() {
        try {
            final AtomicIntegerArray ai = new AtomicIntegerArray(SIZE); 
            for (int i = 0; i < SIZE; ++i) 
                ai.set(i, COUNTDOWN);
            Counter c1 = new Counter(ai);
            Counter c2 = new Counter(ai);
            Thread t1 = new Thread(c1);
            Thread t2 = new Thread(c2);
            t1.start();
            t2.start();
            t1.join();
            t2.join();
            assertEquals(c1.counts+c2.counts, SIZE * COUNTDOWN);
        }
        catch(InterruptedException ie) {
            unexpectedException();
        }
    }


    /**
     *
     */
    public void testSerialization() {
        AtomicIntegerArray l = new AtomicIntegerArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) 
            l.set(i, -i);

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            AtomicIntegerArray r = (AtomicIntegerArray) in.readObject();
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(l.get(i), r.get(i));
            }
        } catch(Exception e){
            e.printStackTrace();
            unexpectedException();
        }
    }

}
