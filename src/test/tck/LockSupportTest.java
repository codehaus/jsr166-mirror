/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class LockSupportTest extends JSR166TestCase{
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(LockSupportTest.class);
    }

    /**
     *
     */
    public void testUnpark() { 
	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
			LockSupport.park();
		    } catch(Exception e){
                        threadUnexpectedException();
                    }
		}
	    });
	t.start();
	try {
            LockSupport.unpark(t);
            t.join();
	}
	catch(Exception e) {
            unexpectedException();
        }
    }

    /**
     *
     */
    public void testParkNanos() { 
	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
			LockSupport.parkNanos(1000);
		    } catch(Exception e){
                        threadUnexpectedException();
                    }
		}
	    });
	try {
            t.start();
            t.join();
	}
	catch(Exception e) {
            unexpectedException();
        }
    }


    /**
     *
     */
    public void testParkUntil() { 
	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
                        long d = new Date().getTime() + 100;
			LockSupport.parkUntil(d);
		    } catch(Exception e){
                        threadUnexpectedException();
                    }
		}
	    });
	try {
            t.start();
            t.join();
	}
	catch(Exception e) {
            unexpectedException();
        }
    }
}
