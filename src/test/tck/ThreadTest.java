/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.Semaphore;

/**
 * Test java.lang.Thread 
 *
 */

public class ThreadTest extends TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run(suite());	
    }
    
    public static Test suite() {
	return new TestSuite(ThreadTest.class);
    }

    static class MyHandler implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e) {
            e.printStackTrace();
        }
    }
    
    public void testGetAndSetUncaughtExceptionHandler() {
        // these must be done all at once to avoid state
        // dependencies across tests
        Thread current = Thread.currentThread();
        ThreadGroup tg = current.getThreadGroup();
	assertNull(Thread.getDefaultUncaughtExceptionHandler());
	assertEquals(tg, current.getUncaughtExceptionHandler());
        MyHandler eh = new MyHandler();
        Thread.setDefaultUncaughtExceptionHandler(eh);
	assertEquals(eh, current.getUncaughtExceptionHandler());
	assertEquals(eh, Thread.getDefaultUncaughtExceptionHandler());
        Thread.setDefaultUncaughtExceptionHandler(null);
	assertNull(Thread.getDefaultUncaughtExceptionHandler());
	assertEquals(tg, current.getUncaughtExceptionHandler());
        current.setUncaughtExceptionHandler(eh);
	assertEquals(eh, current.getUncaughtExceptionHandler());
        current.setUncaughtExceptionHandler(null);
	assertEquals(tg, current.getUncaughtExceptionHandler());
    }
    
    // How to test actually using UEH within junit?

}

