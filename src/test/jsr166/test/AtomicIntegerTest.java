package jsr166.test;

import java.util.concurrent.atomic.*;

import junit.framework.TestCase;

/**
 * Tests the AtomicInteger implementation.
 */
public class AtomicIntegerTest extends TestCase {

    public void testGetAndSet () {
        int initval = 37;
        AtomicInteger ai = new AtomicInteger(initval);
        int newval = 42;
        assertEquals("getAndSet should return initial value,",
                     initval, ai.getAndSet(newval));
        assertEquals("get should return new value,",
                     newval, ai.get());
    }
}
