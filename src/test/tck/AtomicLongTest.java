/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.*;
import java.io.*;

public class AtomicLongTest extends TestCase {
    public static void main (String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicLongTest.class);
    }

    public void testConstructor(){
        AtomicLong ai = new AtomicLong(1);
	assertEquals(1,ai.get());
    }

    public void testConstructor2(){
        AtomicLong ai = new AtomicLong();
	assertEquals(0,ai.get());
    }

    public void testGetSet(){
        AtomicLong ai = new AtomicLong(1);
	assertEquals(1,ai.get());
	ai.set(2);
	assertEquals(2,ai.get());
	ai.set(-3);
	assertEquals(-3,ai.get());
	
    }
    public void testCompareAndSet(){
        AtomicLong ai = new AtomicLong(1);
	assertTrue(ai.compareAndSet(1,2));
	assertTrue(ai.compareAndSet(2,-4));
	assertEquals(-4,ai.get());
	assertFalse(ai.compareAndSet(-5,7));
	assertFalse((7 == ai.get()));
	assertTrue(ai.compareAndSet(-4,7));
	assertEquals(7,ai.get());
    }

    public void testWeakCompareAndSet(){
        AtomicLong ai = new AtomicLong(1);
	while(!ai.weakCompareAndSet(1,2));
	while(!ai.weakCompareAndSet(2,-4));
	assertEquals(-4,ai.get());
	while(!ai.weakCompareAndSet(-4,7));
	assertEquals(7,ai.get());
    }

    public void testGetAndSet(){
        AtomicLong ai = new AtomicLong(1);
	assertEquals(1,ai.getAndSet(0));
	assertEquals(0,ai.getAndSet(-10));
	assertEquals(-10,ai.getAndSet(1));
    }

    public void testGetAndAdd(){
        AtomicLong ai = new AtomicLong(1);
	assertEquals(1,ai.getAndAdd(2));
	assertEquals(3,ai.get());
	assertEquals(3,ai.getAndAdd(-4));
	assertEquals(-1,ai.get());
    }

    public void testGetAndDecrement(){
        AtomicLong ai = new AtomicLong(1);
	assertEquals(1,ai.getAndDecrement());
	assertEquals(0,ai.getAndDecrement());
	assertEquals(-1,ai.getAndDecrement());
    }

    public void testGetAndIncrement(){
        AtomicLong ai = new AtomicLong(1);
	assertEquals(1,ai.getAndIncrement());
	assertEquals(2,ai.get());
	ai.set(-2);
	assertEquals(-2,ai.getAndIncrement());
	assertEquals(-1,ai.getAndIncrement());
	assertEquals(0,ai.getAndIncrement());
	assertEquals(1,ai.get());
    }

    public void testAddAndGet(){
        AtomicLong ai = new AtomicLong(1);
	assertEquals(3,ai.addAndGet(2));
	assertEquals(3,ai.get());
	assertEquals(-1,ai.addAndGet(-4));
	assertEquals(-1,ai.get());
    }

    public void testDecrementAndGet(){
        AtomicLong ai = new AtomicLong(1);
	assertEquals(0,ai.decrementAndGet());
	assertEquals(-1,ai.decrementAndGet());
	assertEquals(-2,ai.decrementAndGet());
	assertEquals(-2,ai.get());
    }

    public void testIncrementAndGet(){
        AtomicLong ai = new AtomicLong(1);
	assertEquals(2,ai.incrementAndGet());
	assertEquals(2,ai.get());
	ai.set(-2);
	assertEquals(-1,ai.incrementAndGet());
	assertEquals(0,ai.incrementAndGet());
	assertEquals(1,ai.incrementAndGet());
	assertEquals(1,ai.get());
    }

    public void testSerialization() {
        AtomicLong l = new AtomicLong();

        try {
            l.set(-22);
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            AtomicLong r = (AtomicLong) in.readObject();
            assertEquals(l.get(), r.get());
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }

}
