/*
 * Written by Doug Lea and Martin Buchholz with assistance from
 * members of JCP JSR-166 Expert Group and released to the public
 * domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class Atomic8Test extends JSR166TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
    public static Test suite() {
        return new TestSuite(Atomic8Test.class);
    }

    /*
     * Tests of atomic class methods accepting lambdas
     * introduced in JDK8.
     */

    static long addLong17(long x) { return x + 17; }
    static int addInt17(int x) { return x + 17; }
    static Integer addInteger17(Integer x) {
        return new Integer(x.intValue() + 17);
    }
    static Integer sumInteger(Integer x, Integer y) {
        return new Integer(x.intValue() + y.intValue());
    }

    volatile long aLongField;
    volatile int anIntField;
    volatile Integer anIntegerField;

    /**
     * AtomicLong getAndUpdate returns previous value and updates
     * result of supplied function
     */
    public void testLongGetAndUpdate() {
        AtomicLong a = new AtomicLong(1L);
        assertEquals(1L, a.getAndUpdate(Atomic8Test::addLong17));
        assertEquals(18L, a.getAndUpdate(Atomic8Test::addLong17));
        assertEquals(35L, a.get());
    }

    /**
     * AtomicLong updateAndGet updates with supplied function and
     * returns result.
     */
    public void testLongUpdateAndGet() {
        AtomicLong a = new AtomicLong(1L);
        assertEquals(18L, a.updateAndGet(Atomic8Test::addLong17));
        assertEquals(35L, a.updateAndGet(Atomic8Test::addLong17));
    }

    /**
     * AtomicLong getAndAccumulate returns previous value and updates
     * with supplied function.
     */
    public void testLongGetAndAccumulate() {
        AtomicLong a = new AtomicLong(1L);
        assertEquals(1L, a.getAndAccumulate(2L, Long::sum));
        assertEquals(3L, a.getAndAccumulate(3L, Long::sum));
        assertEquals(6L, a.get());
    }

    /**
     * AtomicLong accumulateAndGet updates with supplied function and
     * returns result.
     */
    public void testLongAccumulateAndGet() {
        AtomicLong a = new AtomicLong(1L);
        assertEquals(7L, a.accumulateAndGet(6L, Long::sum));
        assertEquals(10L, a.accumulateAndGet(3L, Long::sum));
    }

    /**
     * AtomicInteger getAndUpdate returns previous value and updates
     * result of supplied function
     */
    public void testIntGetAndUpdate() {
        AtomicInteger a = new AtomicInteger(1);
        assertEquals(1, a.getAndUpdate(Atomic8Test::addInt17));
        assertEquals(18, a.getAndUpdate(Atomic8Test::addInt17));
        assertEquals(35, a.get());
    }

    /**
     * AtomicInteger updateAndGet updates with supplied function and
     * returns result.
     */
    public void testIntUpdateAndGet() {
        AtomicInteger a = new AtomicInteger(1);
        assertEquals(18, a.updateAndGet(Atomic8Test::addInt17));
        assertEquals(35, a.updateAndGet(Atomic8Test::addInt17));
    }

    /**
     * AtomicInteger getAndAccumulate returns previous value and updates
     * with supplied function.
     */
    public void testIntGetAndAccumulate() {
        AtomicInteger a = new AtomicInteger(1);
        assertEquals(1, a.getAndAccumulate(2, Integer::sum));
        assertEquals(3, a.getAndAccumulate(3, Integer::sum));
        assertEquals(6, a.get());
    }

    /**
     * AtomicInteger accumulateAndGet updates with supplied function and
     * returns result.
     */
    public void testIntAccumulateAndGet() {
        AtomicInteger a = new AtomicInteger(1);
        assertEquals(7, a.accumulateAndGet(6, Integer::sum));
        assertEquals(10, a.accumulateAndGet(3, Integer::sum));
    }

    /**
     * AtomicReference getAndUpdate returns previous value and updates
     * result of supplied function
     */
    public void testReferenceGetAndUpdate() {
        AtomicReference<Integer> a = new AtomicReference<Integer>(one);
        assertEquals(new Integer(1), a.getAndUpdate(Atomic8Test::addInteger17));
        assertEquals(new Integer(18), a.getAndUpdate(Atomic8Test::addInteger17));
        assertEquals(new Integer(35), a.get());
    }

    /**
     * AtomicReference updateAndGet updates with supplied function and
     * returns result.
     */
    public void testReferenceUpdateAndGet() {
        AtomicReference<Integer> a = new AtomicReference<Integer>(one);
        assertEquals(new Integer(18), a.updateAndGet(Atomic8Test::addInteger17));
        assertEquals(new Integer(35), a.updateAndGet(Atomic8Test::addInteger17));
    }

    /**
     * AtomicReference getAndAccumulate returns previous value and updates
     * with supplied function.
     */
    public void testReferenceGetAndAccumulate() {
        AtomicReference<Integer> a = new AtomicReference<Integer>(one);
        assertEquals(new Integer(1), a.getAndAccumulate(2, Atomic8Test::sumInteger));
        assertEquals(new Integer(3), a.getAndAccumulate(3, Atomic8Test::sumInteger));
        assertEquals(new Integer(6), a.get());
    }

    /**
     * AtomicReference accumulateAndGet updates with supplied function and
     * returns result.
     */
    public void testReferenceAccumulateAndGet() {
        AtomicReference<Integer> a = new AtomicReference<Integer>(one);
        assertEquals(new Integer(7), a.accumulateAndGet(6, Atomic8Test::sumInteger));
        assertEquals(new Integer(10), a.accumulateAndGet(3, Atomic8Test::sumInteger));
    }


    /**
     * AtomicLongArray getAndUpdate returns previous value and updates
     * result of supplied function
     */
    public void testLongArrayGetAndUpdate() {
        AtomicLongArray a = new AtomicLongArray(1);
        a.set(0, 1);
        assertEquals(1L, a.getAndUpdate(0, Atomic8Test::addLong17));
        assertEquals(18L, a.getAndUpdate(0, Atomic8Test::addLong17));
        assertEquals(35L, a.get(0));
    }

    /**
     * AtomicLongArray updateAndGet updates with supplied function and
     * returns result.
     */
    public void testLongArrayUpdateAndGet() {
        AtomicLongArray a = new AtomicLongArray(1);
        a.set(0, 1);
        assertEquals(18L, a.updateAndGet(0, Atomic8Test::addLong17));
        assertEquals(35L, a.updateAndGet(0, Atomic8Test::addLong17));
    }

    /**
     * AtomicLongArray getAndAccumulate returns previous value and updates
     * with supplied function.
     */
    public void testLongArrayGetAndAccumulate() {
        AtomicLongArray a = new AtomicLongArray(1);
        a.set(0, 1);
        assertEquals(1L, a.getAndAccumulate(0, 2L, Long::sum));
        assertEquals(3L, a.getAndAccumulate(0, 3L, Long::sum));
        assertEquals(6L, a.get(0));
    }

    /**
     * AtomicLongArray accumulateAndGet updates with supplied function and
     * returns result.
     */
    public void testLongArrayAccumulateAndGet() {
        AtomicLongArray a = new AtomicLongArray(1);
        a.set(0, 1);
        assertEquals(7L, a.accumulateAndGet(0, 6L, Long::sum));
        assertEquals(10L, a.accumulateAndGet(0, 3L, Long::sum));
    }

    /**
     * AtomicIntegerArray getAndUpdate returns previous value and updates
     * result of supplied function
     */
    public void testIntArrayGetAndUpdate() {
        AtomicIntegerArray a = new AtomicIntegerArray(1);
        a.set(0, 1);
        assertEquals(1, a.getAndUpdate(0, Atomic8Test::addInt17));
        assertEquals(18, a.getAndUpdate(0, Atomic8Test::addInt17));
        assertEquals(35, a.get(0));
    }

    /**
     * AtomicIntegerArray updateAndGet updates with supplied function and
     * returns result.
     */
    public void testIntArrayUpdateAndGet() {
        AtomicIntegerArray a = new AtomicIntegerArray(1);
        a.set(0, 1);
        assertEquals(18, a.updateAndGet(0, Atomic8Test::addInt17));
        assertEquals(35, a.updateAndGet(0, Atomic8Test::addInt17));
    }

    /**
     * AtomicIntegerArray getAndAccumulate returns previous value and updates
     * with supplied function.
     */
    public void testIntArrayGetAndAccumulate() {
        AtomicIntegerArray a = new AtomicIntegerArray(1);
        a.set(0, 1);
        assertEquals(1, a.getAndAccumulate(0, 2, Integer::sum));
        assertEquals(3, a.getAndAccumulate(0, 3, Integer::sum));
        assertEquals(6, a.get(0));
    }

    /**
     * AtomicIntegerArray accumulateAndGet updates with supplied function and
     * returns result.
     */
    public void testIntArrayAccumulateAndGet() {
        AtomicIntegerArray a = new AtomicIntegerArray(1);
        a.set(0, 1);
        assertEquals(7, a.accumulateAndGet(0, 6, Integer::sum));
        assertEquals(10, a.accumulateAndGet(0, 3, Integer::sum));
    }

    /**
     * AtomicReferenceArray getAndUpdate returns previous value and updates
     * result of supplied function
     */
    public void testReferenceArrayGetAndUpdate() {
        AtomicReferenceArray<Integer> a = new AtomicReferenceArray<Integer>(1);
        a.set(0, one);
        assertEquals(new Integer(1), a.getAndUpdate(0, Atomic8Test::addInteger17));
        assertEquals(new Integer(18), a.getAndUpdate(0, Atomic8Test::addInteger17));
        assertEquals(new Integer(35), a.get(0));
    }

    /**
     * AtomicReferenceArray updateAndGet updates with supplied function and
     * returns result.
     */
    public void testReferenceArrayUpdateAndGet() {
        AtomicReferenceArray<Integer> a = new AtomicReferenceArray<Integer>(1);
        a.set(0, one);
        assertEquals(new Integer(18), a.updateAndGet(0, Atomic8Test::addInteger17));
        assertEquals(new Integer(35), a.updateAndGet(0, Atomic8Test::addInteger17));
    }

    /**
     * AtomicReferenceArray getAndAccumulate returns previous value and updates
     * with supplied function.
     */
    public void testReferenceArrayGetAndAccumulate() {
        AtomicReferenceArray<Integer> a = new AtomicReferenceArray<Integer>(1);
        a.set(0, one);
        assertEquals(new Integer(1), a.getAndAccumulate(0, 2, Atomic8Test::sumInteger));
        assertEquals(new Integer(3), a.getAndAccumulate(0, 3, Atomic8Test::sumInteger));
        assertEquals(new Integer(6), a.get(0));
    }

    /**
     * AtomicReferenceArray accumulateAndGet updates with supplied function and
     * returns result.
     */
    public void testReferenceArrayAccumulateAndGet() {
        AtomicReferenceArray<Integer> a = new AtomicReferenceArray<Integer>(1);
        a.set(0, one);
        assertEquals(new Integer(7), a.accumulateAndGet(0, 6, Atomic8Test::sumInteger));
        assertEquals(new Integer(10), a.accumulateAndGet(0, 3, Atomic8Test::sumInteger));
    }

    /**
     * AtomicLongFieldUpdater getAndUpdate returns previous value and updates
     * result of supplied function
     */
    public void testLongFieldUpdaterGetAndUpdate() {
        AtomicLongFieldUpdater a = AtomicLongFieldUpdater.
            newUpdater(Atomic8Test.class, "aLongField");
        a.set(this, 1);
        assertEquals(1L, a.getAndUpdate(this, Atomic8Test::addLong17));
        assertEquals(18L, a.getAndUpdate(this, Atomic8Test::addLong17));
        assertEquals(35L, a.get(this));
    }

    /**
     * AtomicLongFieldUpdater updateAndGet updates with supplied function and
     * returns result.
     */
    public void testLongFieldUpdaterUpdateAndGet() {
        AtomicLongFieldUpdater a = AtomicLongFieldUpdater.
            newUpdater(Atomic8Test.class, "aLongField");
        a.set(this, 1);
        assertEquals(18L, a.updateAndGet(this, Atomic8Test::addLong17));
        assertEquals(35L, a.updateAndGet(this, Atomic8Test::addLong17));
    }

    /**
     * AtomicLongFieldUpdater getAndAccumulate returns previous value
     * and updates with supplied function.
     */
    public void testLongFieldUpdaterGetAndAccumulate() {
        AtomicLongFieldUpdater a = AtomicLongFieldUpdater.
            newUpdater(Atomic8Test.class, "aLongField");
        a.set(this, 1);
        assertEquals(1L, a.getAndAccumulate(this, 2L, Long::sum));
        assertEquals(3L, a.getAndAccumulate(this, 3L, Long::sum));
        assertEquals(6L, a.get(this));
    }

    /**
     * AtomicLongFieldUpdater accumulateAndGet updates with supplied
     * function and returns result.
     */
    public void testLongFieldUpdaterAccumulateAndGet() {
        AtomicLongFieldUpdater a = AtomicLongFieldUpdater.
            newUpdater(Atomic8Test.class, "aLongField");
        a.set(this, 1);
        assertEquals(7L, a.accumulateAndGet(this, 6L, Long::sum));
        assertEquals(10L, a.accumulateAndGet(this, 3L, Long::sum));
    }

    /**
     * AtomicIntegerFieldUpdater getAndUpdate returns previous value and updates
     * result of supplied function
     */
    public void testIntegerFieldUpdaterGetAndUpdate() {
        AtomicIntegerFieldUpdater a = AtomicIntegerFieldUpdater.
            newUpdater(Atomic8Test.class, "anIntField");
        a.set(this, 1);
        assertEquals(1, a.getAndUpdate(this, Atomic8Test::addInt17));
        assertEquals(18, a.getAndUpdate(this, Atomic8Test::addInt17));
        assertEquals(35, a.get(this));
    }

    /**
     * AtomicIntegerFieldUpdater updateAndGet updates with supplied function and
     * returns result.
     */
    public void testIntegerFieldUpdaterUpdateAndGet() {
        AtomicIntegerFieldUpdater a = AtomicIntegerFieldUpdater.
            newUpdater(Atomic8Test.class, "anIntField");
        a.set(this, 1);
        assertEquals(18, a.updateAndGet(this, Atomic8Test::addInt17));
        assertEquals(35, a.updateAndGet(this, Atomic8Test::addInt17));
    }

    /**
     * AtomicIntegerFieldUpdater getAndAccumulate returns previous value
     * and updates with supplied function.
     */
    public void testIntegerFieldUpdaterGetAndAccumulate() {
        AtomicIntegerFieldUpdater a = AtomicIntegerFieldUpdater.
            newUpdater(Atomic8Test.class, "anIntField");
        a.set(this, 1);
        assertEquals(1, a.getAndAccumulate(this, 2, Integer::sum));
        assertEquals(3, a.getAndAccumulate(this, 3, Integer::sum));
        assertEquals(6, a.get(this));
    }

    /**
     * AtomicIntegerFieldUpdater accumulateAndGet updates with supplied
     * function and returns result.
     */
    public void testIntegerFieldUpdaterAccumulateAndGet() {
        AtomicIntegerFieldUpdater a = AtomicIntegerFieldUpdater.
            newUpdater(Atomic8Test.class, "anIntField");
        a.set(this, 1);
        assertEquals(7, a.accumulateAndGet(this, 6, Integer::sum));
        assertEquals(10, a.accumulateAndGet(this, 3, Integer::sum));
    }


    /**
     * AtomicReferenceFieldUpdater getAndUpdate returns previous value
     * and updates result of supplied function
     */
    public void testReferenceFieldUpdaterGetAndUpdate() {
        AtomicReferenceFieldUpdater<Atomic8Test,Integer> a = AtomicReferenceFieldUpdater.
            newUpdater(Atomic8Test.class, Integer.class, "anIntegerField");
        a.set(this, one);
        assertEquals(new Integer(1), a.getAndUpdate(this, Atomic8Test::addInteger17));
        assertEquals(new Integer(18), a.getAndUpdate(this, Atomic8Test::addInteger17));
        assertEquals(new Integer(35), a.get(this));
    }

    /**
     * AtomicReferenceFieldUpdater updateAndGet updates with supplied
     * function and returns result.
     */
    public void testReferenceFieldUpdaterUpdateAndGet() {
        AtomicReferenceFieldUpdater<Atomic8Test,Integer> a = AtomicReferenceFieldUpdater.
            newUpdater(Atomic8Test.class, Integer.class, "anIntegerField");
        a.set(this, one);
        assertEquals(new Integer(18), a.updateAndGet(this, Atomic8Test::addInteger17));
        assertEquals(new Integer(35), a.updateAndGet(this, Atomic8Test::addInteger17));
    }

    /**
     * AtomicReferenceFieldUpdater returns previous value and updates
     * with supplied function.
     */
    public void testReferenceFieldUpdaterGetAndAccumulate() {
        AtomicReferenceFieldUpdater<Atomic8Test,Integer> a = AtomicReferenceFieldUpdater.
            newUpdater(Atomic8Test.class, Integer.class, "anIntegerField");
        a.set(this, one);
        assertEquals(new Integer(1), a.getAndAccumulate(this, 2, Atomic8Test::sumInteger));
        assertEquals(new Integer(3), a.getAndAccumulate(this, 3, Atomic8Test::sumInteger));
        assertEquals(new Integer(6), a.get(this));
    }

    /**
     * AtomicReferenceFieldUpdater accumulateAndGet updates with
     * supplied function and returns result.
     */
    public void testReferenceFieldUpdaterAccumulateAndGet() {
        AtomicReferenceFieldUpdater<Atomic8Test,Integer> a = AtomicReferenceFieldUpdater.
            newUpdater(Atomic8Test.class, Integer.class, "anIntegerField");
        a.set(this, one);
        assertEquals(new Integer(7), a.accumulateAndGet(this, 6, Atomic8Test::sumInteger));
        assertEquals(new Integer(10), a.accumulateAndGet(this, 3, Atomic8Test::sumInteger));
    }

    /**
     * All Atomic getAndUpdate methods throw npe on null function argument
     */
    public void testGetAndUpdateNPE() {
        try { new AtomicLong().getAndUpdate(null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicInteger().getAndUpdate(null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicReference().getAndUpdate(null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicLongArray(1).getAndUpdate(0, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicIntegerArray(1).getAndUpdate(0, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicReferenceArray(1).getAndUpdate(0, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { AtomicLongFieldUpdater.
                newUpdater(Atomic8Test.class, "aLongField").
                getAndUpdate(this, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
        try { AtomicIntegerFieldUpdater.
                newUpdater(Atomic8Test.class, "anIntField").
                getAndUpdate(this, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
        try {  AtomicReferenceFieldUpdater.
                newUpdater(Atomic8Test.class, Integer.class, "anIntegerField").
                getAndUpdate(this, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
    }

    /**
     * All Atomic updateAndGet methods throw npe on null function argument
     */
    public void testUpdateGetAndNPE() {
        try { new AtomicLong().updateAndGet(null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicInteger().updateAndGet(null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicReference().updateAndGet(null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicLongArray(1).updateAndGet(0, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicIntegerArray(1).updateAndGet(0, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicReferenceArray(1).updateAndGet(0, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { AtomicLongFieldUpdater.
                newUpdater(Atomic8Test.class, "aLongField").
                updateAndGet(this, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
        try { AtomicIntegerFieldUpdater.
                newUpdater(Atomic8Test.class, "anIntField").
                updateAndGet(this, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
        try {  AtomicReferenceFieldUpdater.
                newUpdater(Atomic8Test.class, Integer.class, "anIntegerField").
                updateAndGet(this, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
    }

    /**
     * All Atomic getAndAccumulate methods throw npe on null function argument
     */
    public void testGetAndAccumulateNPE() {
        try { new AtomicLong().getAndAccumulate(1L, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicInteger().getAndAccumulate(1, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicReference().getAndAccumulate(one, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicLongArray(1).getAndAccumulate(0, 1L, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicIntegerArray(1).getAndAccumulate(0, 1, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicReferenceArray(1).getAndAccumulate(0, one, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { AtomicLongFieldUpdater.
                newUpdater(Atomic8Test.class, "aLongField").
                getAndAccumulate(this, 1L, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
        try { AtomicIntegerFieldUpdater.
                newUpdater(Atomic8Test.class, "anIntField").
                getAndAccumulate(this, 1, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
        try {  AtomicReferenceFieldUpdater.
                newUpdater(Atomic8Test.class, Integer.class, "anIntegerField").
                getAndAccumulate(this, one, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
    }

    /**
     * All Atomic accumulateAndGet methods throw npe on null function argument
     */
    public void testAccumulateAndGetNPE() {
        try { new AtomicLong().accumulateAndGet(1L, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicInteger().accumulateAndGet(1, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicReference().accumulateAndGet(one, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicLongArray(1).accumulateAndGet(0, 1L, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicIntegerArray(1).accumulateAndGet(0, 1, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { new AtomicReferenceArray(1).accumulateAndGet(0, one, null); shouldThrow();
        } catch(NullPointerException ok) {}
        try { AtomicLongFieldUpdater.
                newUpdater(Atomic8Test.class, "aLongField").
                accumulateAndGet(this, 1L, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
        try { AtomicIntegerFieldUpdater.
                newUpdater(Atomic8Test.class, "anIntField").
                accumulateAndGet(this, 1, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
        try {  AtomicReferenceFieldUpdater.
                newUpdater(Atomic8Test.class, Integer.class, "anIntegerField").
                accumulateAndGet(this, one, null);
            shouldThrow();
        } catch(NullPointerException ok) {}
    }

}

