/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.*;
import java.io.*;

public class AtomicLongArrayTest extends TestCase 
{
    static final int N = 10;

    public static void main (String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicLongArrayTest.class);
    }

    public void testConstructor(){
        AtomicLongArray ai = new AtomicLongArray(N);
        for (int i = 0; i < N; ++i) 
            assertEquals(0,ai.get(i));
    }

    public void testGetSet(){
        AtomicLongArray ai = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) {
            ai.set(i, 1);
            assertEquals(1,ai.get(i));
            ai.set(i, 2);
            assertEquals(2,ai.get(i));
            ai.set(i, -3);
            assertEquals(-3,ai.get(i));
        }
    }

    public void testCompareAndSet(){
        AtomicLongArray ai = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) {
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

    public void testWeakCompareAndSet(){
        AtomicLongArray ai = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) {
            ai.set(i, 1);
            while(!ai.weakCompareAndSet(i, 1,2));
            while(!ai.weakCompareAndSet(i, 2,-4));
            assertEquals(-4,ai.get(i));
            while(!ai.weakCompareAndSet(i, -4,7));
            assertEquals(7,ai.get(i));
        }
    }

    public void testGetAndSet(){
        AtomicLongArray ai = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) {
            ai.set(i, 1);
            assertEquals(1,ai.getAndSet(i,0));
            assertEquals(0,ai.getAndSet(i,-10));
            assertEquals(-10,ai.getAndSet(i,1));
        }
    }

    public void testGetAndAdd(){
        AtomicLongArray ai = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) {
            ai.set(i, 1);
            assertEquals(1,ai.getAndAdd(i,2));
            assertEquals(3,ai.get(i));
            assertEquals(3,ai.getAndAdd(i,-4));
            assertEquals(-1,ai.get(i));
        }
    }

    public void testGetAndDecrement(){
        AtomicLongArray ai = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) {
            ai.set(i, 1);
            assertEquals(1,ai.getAndDecrement(i));
            assertEquals(0,ai.getAndDecrement(i));
            assertEquals(-1,ai.getAndDecrement(i));
        }
    }

    public void testGetAndIncrement(){
        AtomicLongArray ai = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) {
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

    public void testAddAndGet() {
        AtomicLongArray ai = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) {
            ai.set(i, 1);
            assertEquals(3,ai.addAndGet(i,2));
            assertEquals(3,ai.get(i));
            assertEquals(-1,ai.addAndGet(i,-4));
            assertEquals(-1,ai.get(i));
        }
    }

    public void testDecrementAndGet(){
        AtomicLongArray ai = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) {
            ai.set(i, 1);
            assertEquals(0,ai.decrementAndGet(i));
            assertEquals(-1,ai.decrementAndGet(i));
            assertEquals(-2,ai.decrementAndGet(i));
            assertEquals(-2,ai.get(i));
        }
    }

    public void testIncrementAndGet() {
        AtomicLongArray ai = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) {
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

    public void testSerialization() {
        AtomicLongArray l = new AtomicLongArray(N); 
        for (int i = 0; i < N; ++i) 
            l.set(i, -i);

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            AtomicLongArray r = (AtomicLongArray) in.readObject();
            for (int i = 0; i < N; ++i) {
                assertEquals(l.get(i), r.get(i));
            }
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }


}
