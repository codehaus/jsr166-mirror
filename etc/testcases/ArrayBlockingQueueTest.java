import java.util.concurrent.ArrayBlockingQueue;
import junit.framework.TestCase;

/**
 * Tests the ArrayBlockingQueue implementation.
 */
public class ArrayBlockingQueueTest extends TestCase {

    public void testCapacity () {
        final int initcap = 2;
        ArrayBlockingQueue<Integer> iq = new ArrayBlockingQueue<Integer>(initcap);
        assertEquals("capacity should equal constructor argument,",
                     initcap, iq.capacity());
    }
}
