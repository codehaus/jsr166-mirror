package java.util.concurrent.atomic;

import junit.framework.TestCase;

/**
 * Tests the AtomicBoolean implementation.
 */
public class AtomicBooleanTest extends TestCase {

    public void testGetAndSet () {
        boolean initval = false;
        AtomicBoolean ai = new AtomicBoolean(initval);
        boolean newval = true;
        assertEquals("getAndSet should return initial value,",
                     initval, ai.getAndSet(newval));
        assertEquals("get should return new value,",
                     newval, ai.get());
    }
}
