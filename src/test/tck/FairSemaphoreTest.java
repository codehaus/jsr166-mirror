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

public class FairSemaphoreTest extends TestCase{

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    
    public static Test suite() {
	return new TestSuite(FairSemaphoreTest.class);
    }

    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 


    public void testConstructor1() {
        FairSemaphore s = new FairSemaphore(0);
        assertEquals(0, s.availablePermits());
    }

    public void testConstructor2() {
        FairSemaphore s = new FairSemaphore(-1);
        assertEquals(-1, s.availablePermits());
    }

    public void testTryAcquireInSameThread() {
        FairSemaphore s = new FairSemaphore(2);
        assertEquals(2, s.availablePermits());
        assertTrue(s.tryAcquire());
        assertTrue(s.tryAcquire());
        assertEquals(0, s.availablePermits());
        assertFalse(s.tryAcquire());
    }

    public void testTryAcquireNInSameThread() {
        FairSemaphore s = new FairSemaphore(2);
        assertEquals(2, s.availablePermits());
        assertTrue(s.tryAcquire(2));
        assertEquals(0, s.availablePermits());
        assertFalse(s.tryAcquire());
    }

    public void testAcquireReleaseInSameThread(){
        FairSemaphore s = new FairSemaphore(1);
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
            fail("unexpected exception");
        }
    }

    public void testAcquireReleaseNInSameThread(){
        FairSemaphore s = new FairSemaphore(1);
        try {
            s.release(1);
            s.acquire(1);
            s.release(2);
            s.acquire(2);
            s.release(3);
            s.acquire(3);
            s.release(4);
            s.acquire(4);
            s.release(5);
            s.acquire(5);
            assertEquals(1, s.availablePermits());
	} catch( InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testAcquireUninterruptiblyReleaseNInSameThread(){
        FairSemaphore s = new FairSemaphore(1);
        try {
            s.release(1);
            s.acquireUninterruptibly(1);
            s.release(2);
            s.acquireUninterruptibly(2);
            s.release(3);
            s.acquireUninterruptibly(3);
            s.release(4);
            s.acquireUninterruptibly(4);
            s.release(5);
            s.acquireUninterruptibly(5);
            assertEquals(1, s.availablePermits());
	} finally {
        }
    }

    public void testAcquireReleaseInDifferentThreads() {
        final FairSemaphore s = new FairSemaphore(1);
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
			s.acquire();
                        s.acquire();
                        s.acquire();
                        s.acquire();
                        s.acquire();
		    }catch(InterruptedException ie){
                        fail("unexpected exception");
                    }
		}
	    });
	t.start();
        try {
            s.release();
            s.release();
            s.release();
            s.release();
            s.release();
            t.join();
            assertEquals(1, s.availablePermits());
	} catch( InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testAcquireReleaseNInDifferentThreads() {
        final FairSemaphore s = new FairSemaphore(2);
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
                        s.release(2);
                        s.release(2);
                        s.acquire(2);
                        s.acquire(2);
		    }catch(InterruptedException ie){
                        fail("unexpected exception");
                    }
		}
	    });
	t.start();
        try {
            s.acquire(2);
            s.acquire(2);
            s.release(2);
            s.release(2);
            t.join();
	} catch( InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testTimedAcquireReleaseInSameThread(){
        FairSemaphore s = new FairSemaphore(1);
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
            fail("unexpected exception");
        }
    }

    public void testTimedAcquireReleaseNInSameThread(){
        FairSemaphore s = new FairSemaphore(1);
        try {
            s.release(1);
            assertTrue(s.tryAcquire(1, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release(2);
            assertTrue(s.tryAcquire(2, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release(3);
            assertTrue(s.tryAcquire(3, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release(4);
            assertTrue(s.tryAcquire(4, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release(5);
            assertTrue(s.tryAcquire(5, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            assertEquals(1, s.availablePermits());
	} catch( InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testTimedAcquireReleaseInDifferentThreads() {
        final FairSemaphore s = new FairSemaphore(1);
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
                        assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));

		    }catch(InterruptedException ie){
                        fail("unexpected exception");
                    }
		}
	    });
	t.start();
        try {
            s.release();
            s.release();
            s.release();
            s.release();
            s.release();
            t.join();
	} catch( InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testTimedAcquireReleaseNInDifferentThreads() {
        final FairSemaphore s = new FairSemaphore(2);
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
                        assertTrue(s.tryAcquire(2, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        s.release(2);
                        assertTrue(s.tryAcquire(2, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        s.release(2);
		    }catch(InterruptedException ie){
                        fail("unexpected exception");
                    }
		}
	    });
	t.start();
        try {
            s.release(2);
            assertTrue(s.tryAcquire(2, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release(2);
            assertTrue(s.tryAcquire(2, SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            t.join();
	} catch( InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testAcquire_InterruptedException(){
	final FairSemaphore s = new FairSemaphore(0);
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
			s.acquire();
			fail("should throw");
		    }catch(InterruptedException success){}
		}
	    });
	t.start();
	try{
	    Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testAcquireN_InterruptedException(){
	final FairSemaphore s = new FairSemaphore(2);
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
			s.acquire(3);
			fail("should throw");
		    }catch(InterruptedException success){}
		}
	    });
	t.start();
	try{
	    Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(InterruptedException e){
            fail("unexpected exception");
        }
    }
    
    public void testTryAcquire_InterruptedException(){
	final FairSemaphore s = new FairSemaphore(0);
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
			s.tryAcquire(MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
			fail("should throw");
		    }catch(InterruptedException success){
                    }
		}
	    });
	t.start();
	try{
	    Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testTryAcquireN_InterruptedException(){
	final FairSemaphore s = new FairSemaphore(1);
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
			s.tryAcquire(4, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
			fail("should throw");
		    }catch(InterruptedException success){
                    }
		}
	    });
	t.start();
	try{
	    Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testSerialization() {
        FairSemaphore l = new FairSemaphore(3);

        try {
            l.acquire();
            l.release();
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            FairSemaphore r = (FairSemaphore) in.readObject();
            assertEquals(3, r.availablePermits());
            r.acquire();
            r.release();
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }

}
