/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;

public class ThreadTest extends JSR166TestCase {
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
        MyHandler eh = new MyHandler();
	assertEquals(tg, current.getUncaughtExceptionHandler());
        current.setUncaughtExceptionHandler(eh);
	assertEquals(eh, current.getUncaughtExceptionHandler());
        current.setUncaughtExceptionHandler(null);
	assertEquals(tg, current.getUncaughtExceptionHandler());
    }
    
    // How to test actually using UEH within junit?

}

