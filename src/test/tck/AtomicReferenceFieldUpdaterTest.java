/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import java.util.concurrent.atomic.*;
import junit.framework.*;
import java.util.*;

public class AtomicReferenceFieldUpdaterTest extends TestCase{
    volatile Integer x = null;
    Object z;
    Integer w;

    public static void main(String[] args){
        junit.textui.TestRunner.run(suite());
    }

   
    public static Test suite() {
        return new TestSuite(AtomicReferenceFieldUpdaterTest.class);
    }

    static final Integer zero = new Integer(0);
    static final Integer one = new Integer(1);
    static final Integer two = new Integer(2);
    static final Integer m3  = new Integer(-3);
    static final Integer m4 = new Integer(-4);
    static final Integer m5 = new Integer(-5);
    static final Integer seven = new Integer(7);
    static final Integer m10 = new Integer(-10);

    public void testConstructor(){
        try{
            AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer>
                a = AtomicReferenceFieldUpdater.newUpdater
                (getClass(), Integer.class, "y");
            fail("Exception not thrown");
        }

        catch (RuntimeException rt) {}
    }


    public void testConstructor2(){
        try{
            AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer>
                a = AtomicReferenceFieldUpdater.newUpdater
                (getClass(), Integer.class, "z");
            fail("Exception not thrown");
        }

        catch (RuntimeException rt) {}
    }

    public void testConstructor3(){
        try{
            AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer>
                a = AtomicReferenceFieldUpdater.newUpdater
                (getClass(), Integer.class, "w");
            fail("Exception not thrown");
        }

        catch (RuntimeException rt) {}
    }

    public void testGetSet(){
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer>a = AtomicReferenceFieldUpdater.newUpdater(getClass(), Integer.class, "x");
        x = one;
	assertEquals(one,a.get(this));
	a.set(this,two);
	assertEquals(two,a.get(this));
	a.set(this,-3);
	assertEquals(-3,a.get(this));
	
    }
    public void testCompareAndSet(){
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer>a = AtomicReferenceFieldUpdater.newUpdater(getClass(), Integer.class, "x");
        x = one;
	assertTrue(a.compareAndSet(this,one,two));
	assertTrue(a.compareAndSet(this,two,m4));
	assertEquals(m4,a.get(this));
	assertFalse(a.compareAndSet(this,m5,seven));
	assertFalse((seven == a.get(this)));
	assertTrue(a.compareAndSet(this,m4,seven));
	assertEquals(seven,a.get(this));
    }

    public void testWeakCompareAndSet(){
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer>a = AtomicReferenceFieldUpdater.newUpdater(getClass(), Integer.class, "x");
        x = one;
	while(!a.weakCompareAndSet(this,one,two));
	while(!a.weakCompareAndSet(this,two,m4));
	assertEquals(m4,a.get(this));
	while(!a.weakCompareAndSet(this,m4,seven));
	assertEquals(seven,a.get(this));
    }

    public void testGetAndSet(){
        AtomicReferenceFieldUpdater<AtomicReferenceFieldUpdaterTest, Integer>a = AtomicReferenceFieldUpdater.newUpdater(getClass(), Integer.class, "x");
        x = one;
	assertEquals(one,a.getAndSet(this, zero));
	assertEquals(zero,a.getAndSet(this,m10));
	assertEquals(m10,a.getAndSet(this,1));
    }

}
