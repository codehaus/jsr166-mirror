/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.*;
import java.io.*;

public class AtomicReferenceTest extends TestCase {
    public static void main (String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicReferenceTest.class);
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
        AtomicReference ai = new AtomicReference(one);
	assertEquals(one,ai.get());
    }

    public void testConstructor2(){
        AtomicReference ai = new AtomicReference();
	assertNull(ai.get());
    }

    public void testGetSet(){
        AtomicReference ai = new AtomicReference(one);
	assertEquals(one,ai.get());
	ai.set(two);
	assertEquals(two,ai.get());
	ai.set(m3);
	assertEquals(m3,ai.get());
	
    }
    public void testCompareAndSet(){
        AtomicReference ai = new AtomicReference(one);
	assertTrue(ai.compareAndSet(one,two));
	assertTrue(ai.compareAndSet(two,m4));
	assertEquals(m4,ai.get());
	assertFalse(ai.compareAndSet(m5,seven));
	assertFalse((seven.equals(ai.get())));
	assertTrue(ai.compareAndSet(m4,seven));
	assertEquals(seven,ai.get());
    }

    public void testWeakCompareAndSet(){
        AtomicReference ai = new AtomicReference(one);
	while(!ai.weakCompareAndSet(one,two));
	while(!ai.weakCompareAndSet(two,m4));
	assertEquals(m4,ai.get());
        while(!ai.weakCompareAndSet(m4,seven));
	assertEquals(seven,ai.get());
    }

    public void testGetAndSet(){
        AtomicReference ai = new AtomicReference(one);
	assertEquals(one,ai.getAndSet(zero));
	assertEquals(zero,ai.getAndSet(m10));
	assertEquals(m10,ai.getAndSet(one));
    }

    public void testSerialization() {
        AtomicReference l = new AtomicReference();

        try {
            l.set(one);
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            AtomicReference r = (AtomicReference) in.readObject();
            assertEquals(l.get(), r.get());
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }

}

