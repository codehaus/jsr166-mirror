/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

public class SemaphoreTest extends JSR166TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(SemaphoreTest.class);
    }

    /**
     * Zero, negative, and positive initial values are allowed in constructor
     */
    public void testConstructor() {
        Semaphore s0 = new Semaphore(0);
        assertEquals(0, s0.availablePermits());
        Semaphore s1 = new Semaphore(-1);
        assertEquals(-1, s1.availablePermits());
        Semaphore s2 = new Semaphore(-1);
        assertEquals(-1, s2.availablePermits());
    }

    /**
     * tryAcquire succeeds when sufficent permits, else fails
     */
    public void testTryAcquireInSameThread() {
        Semaphore s = new Semaphore(2);
        assertEquals(2, s.availablePermits());
        assertTrue(s.tryAcquire());
        assertTrue(s.tryAcquire());
        assertEquals(0, s.availablePermits());
        assertFalse(s.tryAcquire());
    }

    /**
     * Acquire and release of semaphore succeed if initially available
     */
    public void testAcquireReleaseInSameThread() {
        Semaphore s = new Semaphore(1);
        try {
            s.acquire();
            s.release();
            s.acquire();
            s.release();
            s.acquire();
            s.release();
            s.acquire();
            s.release();
            s.acquire();
            s.release();
            assertEquals(1, s.availablePermits());
	} catch( InterruptedException e){
            unexpectedException();
        }
    }

    /**
     * Uninterruptible acquire and release of semaphore succeed if
     * initially available
     */
    public void testAcquireUninterruptiblyReleaseInSameThread() {
        Semaphore s = new Semaphore(1);
        try {
            s.acquireUninterruptibly();
            s.release();
            s.acquireUninterruptibly();
            s.release();
            s.acquireUninterruptibly();
            s.release();
            s.acquireUninterruptibly();
            s.release();
            s.acquireUninterruptibly();
            s.release();
            assertEquals(1, s.availablePermits());
	} finally {
        }
    }

    /**
     * Timed Acquire and release of semaphore succeed if
     * initially available
     */
    public void testTimedAcquireReleaseInSameThread() {
        Semaphore s = new Semaphore(1);
        try {
            assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release();
            assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release();
            assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release();
            assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release();
            assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release();
            assertEquals(1, s.availablePermits());
	} catch( InterruptedException e){
            unexpectedException();
        }
    }

    /**
     * A release in one thread enables an acquire in another thread
     */
    public void testAcquireReleaseInDifferentThreads() {
        final Semaphore s = new Semaphore(0);
	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
			s.acquire();
                        s.release();
                        s.release();
                        s.acquire();
		    } catch(InterruptedException ie){
                        threadUnexpectedException();
                    }
		}
	    });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            s.release();
            s.release();
            s.acquire();
            s.acquire();
            s.release();
            t.join();
	} catch( InterruptedException e){
            unexpectedException();
        }
    }

    /**
     * A release in one thread enables an uninterruptible acquire in another thread
     */
    public void testUninterruptibleAcquireReleaseInDifferentThreads() {
        final Semaphore s = new Semaphore(0);
	Thread t = new Thread(new Runnable() {
		public void run() {
                    s.acquireUninterruptibly();
                    s.release();
                    s.release();
                    s.acquireUninterruptibly();
		}
	    });
        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            s.release();
            s.release();
            s.acquireUninterruptibly();
            s.acquireUninterruptibly();
            s.release();
            t.join();
	} catch( InterruptedException e){
            unexpectedException();
        }
    }


    /**
     *  A release in one thread enables a timed acquire in another thread
     */
    public void testTimedAcquireReleaseInDifferentThreads() {
        final Semaphore s = new Semaphore(1);
	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
                        s.release();
                        threadAssertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        s.release();
                        threadAssertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));

		    } catch(InterruptedException ie){
                        threadUnexpectedException();
                    }
		}
	    });
        try {
            t.start();
            assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release();
            assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release();
            s.release();
            t.join();
	} catch( InterruptedException e){
            unexpectedException();
        }
    }

    /**
     * A waiting acquire blocks interruptibly
     */
    public void testAcquire_InterruptedException() {
	final Semaphore s = new Semaphore(0);
	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
			s.acquire();
			threadShouldThrow();
		    } catch(InterruptedException success){}
		}
	    });
	t.start();
	try {
	    Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(InterruptedException e){
            unexpectedException();
        }
    }
    
    /**
     *  A waiting timed acquire blocks interruptibly
     */
    public void testTryAcquire_InterruptedException() {
	final Semaphore s = new Semaphore(0);
	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
			s.tryAcquire(MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
			threadShouldThrow();
		    } catch(InterruptedException success){
                    }
		}
	    });
	t.start();
	try {
	    Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(InterruptedException e){
            unexpectedException();
        }
    }

    /**
     * a deserialized serialized semaphore has same number of permits
     */
    public void testSerialization() {
        Semaphore l = new Semaphore(3);
        try {
            l.acquire();
            l.release();
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            Semaphore r = (Semaphore) in.readObject();
            assertEquals(3, r.availablePermits());
            r.acquire();
            r.release();
        } catch(Exception e){
            unexpectedException();
        }
    }

}
