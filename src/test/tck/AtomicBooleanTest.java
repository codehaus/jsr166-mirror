/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.*;
import java.io.*;

public class AtomicBooleanTest extends TestCase {
    public static void main (String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicBooleanTest.class);
    }

    public void testConstructor(){
        AtomicBoolean ai = new AtomicBoolean(true);
	assertEquals(true,ai.get());
    }

    public void testConstructor2(){
        AtomicBoolean ai = new AtomicBoolean();
	assertEquals(false,ai.get());
    }

    public void testGetSet(){
        AtomicBoolean ai = new AtomicBoolean(true);
	assertEquals(true,ai.get());
	ai.set(false);
	assertEquals(false,ai.get());
	ai.set(true);
	assertEquals(true,ai.get());
	
    }
    public void testCompareAndSet(){
        AtomicBoolean ai = new AtomicBoolean(true);
	assertTrue(ai.compareAndSet(true,false));
	assertEquals(false,ai.get());
	assertTrue(ai.compareAndSet(false,false));
	assertEquals(false,ai.get());
	assertFalse(ai.compareAndSet(true,false));
	assertFalse((ai.get()));
	assertTrue(ai.compareAndSet(false,true));
	assertEquals(true,ai.get());
    }

    public void testWeakCompareAndSet(){
        AtomicBoolean ai = new AtomicBoolean(true);
	while(!ai.weakCompareAndSet(true,false));
	assertEquals(false,ai.get());
	while(!ai.weakCompareAndSet(false,false));
        assertEquals(false,ai.get());
        while(!ai.weakCompareAndSet(false,true));
	assertEquals(true,ai.get());
    }

    public void testGetAndSet(){
        AtomicBoolean ai = new AtomicBoolean(true);
	assertEquals(true,ai.getAndSet(false));
	assertEquals(false,ai.getAndSet(false));
	assertEquals(false,ai.getAndSet(true));
	assertEquals(true,ai.get());
    }

    public void testSerialization() {
        AtomicBoolean l = new AtomicBoolean();

        try {
            l.set(true);
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            AtomicBoolean r = (AtomicBoolean) in.readObject();
            assertEquals(l.get(), r.get());
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }


}
