package java.util.concurrent;

import junit.framework.TestCase;

/**
 * Tests the ArrayBlockingQueue implementation.
 */
public class ArrayBlockingQueueTest extends TestCase {

    public void testCapacity () {
        final int CAP = 2;
        ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<Integer>(CAP);

        //assertEquals("capacity should equal constructor argument", CAP, q.capacity());
    }
}
