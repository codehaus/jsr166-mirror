/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.*;

public class AtomicStampedReferenceTest extends JSR166TestCase{
    public static void main (String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicStampedReferenceTest.class);
    }
    
    static final Integer zero = new Integer(0);
    static final Integer one = new Integer(1);
    static final Integer two = new Integer(2);
    static final Integer m3  = new Integer(-3);

    public void testConstructor(){
        AtomicStampedReference ai = new AtomicStampedReference(one, 0);
	assertEquals(one,ai.getReference());
	assertEquals(0, ai.getStamp());
        AtomicStampedReference a2 = new AtomicStampedReference(null, 1);
	assertNull(a2.getReference());
	assertEquals(1, a2.getStamp());

    }

    public void testGetSet(){
        int[] mark = new int[1];
        AtomicStampedReference ai = new AtomicStampedReference(one, 0);
	assertEquals(one,ai.getReference());
	assertEquals(0, ai.getStamp());
        assertEquals(one, ai.get(mark));
        assertEquals(0, mark[0]);
	ai.set(two, 0);
	assertEquals(two,ai.getReference());
	assertEquals(0, ai.getStamp());
        assertEquals(two, ai.get(mark));
        assertEquals(0, mark[0]);
	ai.set(one, 1);
	assertEquals(one,ai.getReference());
	assertEquals(1, ai.getStamp());
        assertEquals(one, ai.get(mark));
        assertEquals(1,mark[0]);
    }

    public void testAttemptStamp(){
        int[] mark = new int[1];
        AtomicStampedReference ai = new AtomicStampedReference(one, 0);
        assertEquals(0, ai.getStamp());
        assertTrue(ai.attemptStamp(one, 1));
	assertEquals(1, ai.getStamp());
        assertEquals(one, ai.get(mark));
        assertEquals(1, mark[0]);
    }

    public void testCompareAndSet(){
        int[] mark = new int[1];
        AtomicStampedReference ai = new AtomicStampedReference(one, 0);
	assertEquals(one, ai.get(mark));
        assertEquals(0, ai.getStamp());
	assertEquals(0, mark[0]);

        assertTrue(ai.compareAndSet(one, two, 0, 0));
	assertEquals(two, ai.get(mark));
	assertEquals(0, mark[0]);

        assertTrue(ai.compareAndSet(two, m3, 0, 1));
	assertEquals(m3, ai.get(mark));
	assertEquals(1, mark[0]);

        assertFalse(ai.compareAndSet(two, m3, 1, 1));
	assertEquals(m3, ai.get(mark));
	assertEquals(1, mark[0]);
    }

    public void testWeakCompareAndSet(){
        int[] mark = new int[1];
        AtomicStampedReference ai = new AtomicStampedReference(one, 0);
	assertEquals(one, ai.get(mark));
        assertEquals(0, ai.getStamp ());
	assertEquals(0, mark[0]);

        while(!ai.weakCompareAndSet(one, two, 0, 0));
	assertEquals(two, ai.get(mark));
	assertEquals(0, mark[0]);

        while(!ai.weakCompareAndSet(two, m3, 0, 1));
	assertEquals(m3, ai.get(mark));
	assertEquals(1, mark[0]);
    }

}
