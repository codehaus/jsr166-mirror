/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.*;

public class AtomicReferenceArrayTest extends TestCase 
{
    static final int N = 10;

    static final Integer zero = new Integer(0);
    static final Integer one = new Integer(1);
    static final Integer two = new Integer(2);
    static final Integer m3  = new Integer(-3);
    static final Integer m4 = new Integer(-4);
    static final Integer m5 = new Integer(-5);
    static final Integer seven = new Integer(7);
    static final Integer m10 = new Integer(-10);

    public static void main (String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicReferenceArrayTest.class);
    }

    public void testConstructor(){
        AtomicReferenceArray<Integer> ai = new AtomicReferenceArray<Integer>(N);
        for (int i = 0; i < N; ++i) {
            assertNull(ai.get(i));
        }
    }

    public void testGetSet(){
        AtomicReferenceArray ai = new AtomicReferenceArray(N); 
        for (int i = 0; i < N; ++i) {
            ai.set(i, one);
            assertEquals(one,ai.get(i));
            ai.set(i, two);
            assertEquals(two,ai.get(i));
            ai.set(i, m3);
            assertEquals(m3,ai.get(i));
        }
    }

    public void testCompareAndSet(){
        AtomicReferenceArray ai = new AtomicReferenceArray(N); 
        for (int i = 0; i < N; ++i) {
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

    public void testWeakCompareAndSet(){
        AtomicReferenceArray ai = new AtomicReferenceArray(N); 
        for (int i = 0; i < N; ++i) {
            ai.set(i, one);
            while(!ai.weakCompareAndSet(i, one,two));
            while(!ai.weakCompareAndSet(i, two,m4));
            assertEquals(m4,ai.get(i));
            while(!ai.weakCompareAndSet(i, m4,seven));
            assertEquals(seven,ai.get(i));
        }
    }

    public void testGetAndSet(){
        AtomicReferenceArray ai = new AtomicReferenceArray(N); 
        for (int i = 0; i < N; ++i) {
            ai.set(i, one);
            assertEquals(one,ai.getAndSet(i,zero));
            assertEquals(0,ai.getAndSet(i,m10));
            assertEquals(m10,ai.getAndSet(i,one));
        }
    }


}
