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
    
    /**
     *
     */
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

    /**
     *
     */
    public void testGetAndSetDefaultUncaughtExceptionHandler() {
        assertEquals(null, Thread.getDefaultUncaughtExceptionHandler());
        // failure due to securityException is OK.
        // Would be nice to explicitly test both ways, but cannot yet.
        try {
            Thread current = Thread.currentThread();
            ThreadGroup tg = current.getThreadGroup();
            MyHandler eh = new MyHandler();
            assertEquals(tg, current.getUncaughtExceptionHandler());
            Thread.setDefaultUncaughtExceptionHandler(eh);
            Thread.setDefaultUncaughtExceptionHandler(null);
            assertEquals(tg, current.getUncaughtExceptionHandler());
        }
        catch(SecurityException ok) {
        }
        assertEquals(null, Thread.getDefaultUncaughtExceptionHandler());
    }
    
    // How to test actually using UEH within junit?

}

