/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.*;
import java.io.*;

public class AtomicReferenceArrayTest extends JSR166TestCase 
{
    public static void main (String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicReferenceArrayTest.class);
    }

    /**
     *
     */
    public void testConstructor(){
        AtomicReferenceArray<Integer> ai = new AtomicReferenceArray<Integer>(SIZE);
        for (int i = 0; i < SIZE; ++i) {
            assertNull(ai.get(i));
        }
    }

    /**
     *
     */
    public void testGetSet(){
        AtomicReferenceArray ai = new AtomicReferenceArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, one);
            assertEquals(one,ai.get(i));
            ai.set(i, two);
            assertEquals(two,ai.get(i));
            ai.set(i, m3);
            assertEquals(m3,ai.get(i));
        }
    }

    /**
     *
     */
    public void testCompareAndSet(){
        AtomicReferenceArray ai = new AtomicReferenceArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, one);
            assertTrue(ai.compareAndSet(i, one,two));
            assertTrue(ai.compareAndSet(i, two,m4));
            assertEquals(m4,ai.get(i));
            assertFalse(ai.compareAndSet(i, m5,seven));
            assertFalse((seven.equals(ai.get(i))));
            assertTrue(ai.compareAndSet(i, m4,seven));
            assertEquals(seven,ai.get(i));
        }
    }

    /**
     *
     */
    public void testWeakCompareAndSet(){
        AtomicReferenceArray ai = new AtomicReferenceArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, one);
            while(!ai.weakCompareAndSet(i, one,two));
            while(!ai.weakCompareAndSet(i, two,m4));
            assertEquals(m4,ai.get(i));
            while(!ai.weakCompareAndSet(i, m4,seven));
            assertEquals(seven,ai.get(i));
        }
    }

    /**
     *
     */
    public void testGetAndSet(){
        AtomicReferenceArray ai = new AtomicReferenceArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            ai.set(i, one);
            assertEquals(one,ai.getAndSet(i,zero));
            assertEquals(0,ai.getAndSet(i,m10));
            assertEquals(m10,ai.getAndSet(i,one));
        }
    }

    /**
     *
     */
    public void testSerialization() {
        AtomicReferenceArray l = new AtomicReferenceArray(SIZE); 
        for (int i = 0; i < SIZE; ++i) {
            l.set(i, new Integer(-i));
        }

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            AtomicReferenceArray r = (AtomicReferenceArray) in.readObject();
            assertEquals(l.length(), r.length());
            for (int i = 0; i < SIZE; ++i) {
                assertEquals(r.get(i), l.get(i));
            }
        } catch(Exception e){
            unexpectedException();
        }
    }

}
