/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.atomic.*;

public class AtomicMarkableReferenceTest extends TestCase{
    public static void main (String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(AtomicMarkableReferenceTest.class);
    }
    
    static final Integer zero = new Integer(0);
    static final Integer one = new Integer(1);
    static final Integer two = new Integer(2);
    static final Integer m3  = new Integer(-3);

    public void testConstructor(){
        AtomicMarkableReference ai = new AtomicMarkableReference(one, false);
	assertEquals(one,ai.getReference());
	assertFalse(ai.isMarked());
        AtomicMarkableReference a2 = new AtomicMarkableReference(null, true);
	assertNull(a2.getReference());
	assertTrue(a2.isMarked());

    }

    public void testGetSet(){
        boolean[] mark = new boolean[1];
        AtomicMarkableReference ai = new AtomicMarkableReference(one, false);
	assertEquals(one,ai.getReference());
	assertFalse(ai.isMarked());
        assertEquals(one, ai.get(mark));
        assertFalse(mark[0]);
	ai.set(two, false);
	assertEquals(two,ai.getReference());
	assertFalse(ai.isMarked());
        assertEquals(two, ai.get(mark));
        assertFalse(mark[0]);
	ai.set(one, true);
	assertEquals(one,ai.getReference());
	assertTrue(ai.isMarked());
        assertEquals(one, ai.get(mark));
        assertTrue(mark[0]);
    }

    public void testAttemptMark(){
        boolean[] mark = new boolean[1];
        AtomicMarkableReference ai = new AtomicMarkableReference(one, false);
        assertFalse(ai.isMarked());
        assertTrue(ai.attemptMark(one, true));
	assertTrue(ai.isMarked());
        assertEquals(one, ai.get(mark));
        assertTrue(mark[0]);
    }

    public void testCompareAndSet(){
        boolean[] mark = new boolean[1];
        AtomicMarkableReference ai = new AtomicMarkableReference(one, false);
	assertEquals(one, ai.get(mark));
        assertFalse(ai.isMarked());
	assertFalse(mark[0]);

        assertTrue(ai.compareAndSet(one, two, false, false));
	assertEquals(two, ai.get(mark));
	assertFalse(mark[0]);

        assertTrue(ai.compareAndSet(two, m3, false, true));
	assertEquals(m3, ai.get(mark));
	assertTrue(mark[0]);

        assertFalse(ai.compareAndSet(two, m3, true, true));
	assertEquals(m3, ai.get(mark));
	assertTrue(mark[0]);
    }

    public void testWeakCompareAndSet(){
        boolean[] mark = new boolean[1];
        AtomicMarkableReference ai = new AtomicMarkableReference(one, false);
	assertEquals(one, ai.get(mark));
        assertFalse(ai.isMarked());
	assertFalse(mark[0]);

        while(!ai.weakCompareAndSet(one, two, false, false));
	assertEquals(two, ai.get(mark));
	assertFalse(mark[0]);

        while(!ai.weakCompareAndSet(two, m3, false, true));
	assertEquals(m3, ai.get(mark));
	assertTrue(mark[0]);
    }

}
