import java.util.concurrent.atomic.AtomicBoolean;
import junit.framework.TestCase;

/**
 * Tests the AtomicBoolean implementation.
 */
public class AtomicBooleanTest extends TestCase {

    public void testGetAndSet () {
        boolean initval = false;
        AtomicBoolean b = new AtomicBoolean(initval);
        boolean newval = true;
        if (false /* remove once this is implemented */) {
            assertEquals("getAndSet should return initial value,",
                         initval, b.getAndSet(newval));
            assertEquals("get should return new value,",
                         newval, b.get());
        }
    }
}
