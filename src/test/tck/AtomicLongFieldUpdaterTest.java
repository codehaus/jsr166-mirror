/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import java.util.concurrent.atomic.*;
import junit.framework.*;
import java.util.*;

public class AtomicLongFieldUpdaterTest extends TestCase{
    volatile long x = 0;
    long z;
    int w;

    public static void main(String[] args){
        junit.textui.TestRunner.run(suite());
    }

   
    public static Test suite() {
        return new TestSuite(AtomicLongFieldUpdaterTest.class);
    }

    public void testConstructor(){
        try{
            AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> 
                a = AtomicLongFieldUpdater.newUpdater
                (getClass(), "y");
            fail("Exception not thrown");
        }

        catch (RuntimeException rt) {}
    }

    public void testConstructor2(){
        try{
            AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> 
                a = AtomicLongFieldUpdater.newUpdater
                (getClass(), "z");
            fail("Exception not thrown");
        }

        catch (RuntimeException rt) {}
    }

    public void testConstructor3(){
        try{
            AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> 
                a = AtomicLongFieldUpdater.newUpdater
                (getClass(), "w");
            fail("Exception not thrown");
        }

        catch (RuntimeException rt) {}
    }

    public void testGetSet(){
        AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> a = AtomicLongFieldUpdater.newUpdater(getClass(), "x");
        x = 1;
	assertEquals(1,a.get(this));
	a.set(this,2);
	assertEquals(2,a.get(this));
	a.set(this,-3);
	assertEquals(-3,a.get(this));
	
    }
    public void testCompareAndSet(){
        AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> a = AtomicLongFieldUpdater.newUpdater(getClass(), "x");
        x = 1;
	assertTrue(a.compareAndSet(this,1,2));
	assertTrue(a.compareAndSet(this,2,-4));
	assertEquals(-4,a.get(this));
	assertFalse(a.compareAndSet(this,-5,7));
	assertFalse((7 == a.get(this)));
	assertTrue(a.compareAndSet(this,-4,7));
	assertEquals(7,a.get(this));
    }

    public void testWeakCompareAndSet(){
        AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> a = AtomicLongFieldUpdater.newUpdater(getClass(), "x");
        x = 1;
	while(!a.weakCompareAndSet(this,1,2));
        while(!a.weakCompareAndSet(this,2,-4));
	assertEquals(-4,a.get(this));
	while(!a.weakCompareAndSet(this,-4,7));
	assertEquals(7,a.get(this));
    }

    public void testGetAndSet(){
        AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> a = AtomicLongFieldUpdater.newUpdater(getClass(), "x");
        x = 1;
	assertEquals(1,a.getAndSet(this, 0));
	assertEquals(0,a.getAndSet(this,-10));
	assertEquals(-10,a.getAndSet(this,1));
    }

    public void testGetAndAdd(){
        AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> a = AtomicLongFieldUpdater.newUpdater(getClass(), "x");
        x = 1;
	assertEquals(1,a.getAndAdd(this,2));
	assertEquals(3,a.get(this));
	assertEquals(3,a.getAndAdd(this,-4));
	assertEquals(-1,a.get(this));
    }

    public void testGetAndDecrement(){
        AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> a = AtomicLongFieldUpdater.newUpdater(getClass(), "x");
        x = 1;
	assertEquals(1,a.getAndDecrement(this));
	assertEquals(0,a.getAndDecrement(this));
	assertEquals(-1,a.getAndDecrement(this));
    }

    public void testGetAndIncrement(){
        AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> a = AtomicLongFieldUpdater.newUpdater(getClass(), "x");
        x = 1;
	assertEquals(1,a.getAndIncrement(this));
	assertEquals(2,a.get(this));
	a.set(this,-2);
	assertEquals(-2,a.getAndIncrement(this));
	assertEquals(-1,a.getAndIncrement(this));
	assertEquals(0,a.getAndIncrement(this));
	assertEquals(1,a.get(this));
    }

    public void testAddAndGet(){
        AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> a = AtomicLongFieldUpdater.newUpdater(getClass(), "x");
        x = 1;
	assertEquals(3,a.addAndGet(this,2));
	assertEquals(3,a.get(this));
	assertEquals(-1,a.addAndGet(this,-4));
	assertEquals(-1,a.get(this));
    }

    public void testDecrementAndGet(){
        AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> a = AtomicLongFieldUpdater.newUpdater(getClass(), "x");
        x = 1;
	assertEquals(0,a.decrementAndGet(this));
	assertEquals(-1,a.decrementAndGet(this));
	assertEquals(-2,a.decrementAndGet(this));
	assertEquals(-2,a.get(this));
    }

    public void testIncrementAndGet(){
        AtomicLongFieldUpdater<AtomicLongFieldUpdaterTest> a = AtomicLongFieldUpdater.newUpdater(getClass(), "x");
        x = 1;
	assertEquals(2,a.incrementAndGet(this));
	assertEquals(2,a.get(this));
	a.set(this,-2);
	assertEquals(-1,a.incrementAndGet(this));
	assertEquals(0,a.incrementAndGet(this));
	assertEquals(1,a.incrementAndGet(this));
	assertEquals(1,a.get(this));
    }

}
