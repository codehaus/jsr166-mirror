/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.Semaphore;

public class ThreadLocalTest extends JSR166TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run(suite());	
    }
    
    public static Test suite() {
	return new TestSuite(ThreadLocalTest.class);
    }

    static ThreadLocal tl = new ThreadLocal() {
            public Object initialValue() {
                return new Integer(1);
            }
        };

    
    /**
     * remove causes next access to return initial value
     */
    public void testRemove() {
        Integer one = new Integer(1);
        Integer two = new Integer(2);
        assertEquals(tl.get(), one);
        tl.set(two);
        assertEquals(tl.get(), two);
        tl.remove();
        assertEquals(tl.get(), one);
    }
}

