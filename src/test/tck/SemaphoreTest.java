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

public class SemaphoreTest extends TestCase{

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    
    public static Test suite() {
	return new TestSuite(SemaphoreTest.class);
    }

    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 


    public void testConstructor1() {
        Semaphore s = new Semaphore(0);
        assertEquals(0, s.availablePermits());
    }

    public void testConstructor2() {
        Semaphore s = new Semaphore(-1);
        assertEquals(-1, s.availablePermits());
    }

    public void testTryAcquireInSameThread() {
        Semaphore s = new Semaphore(2);
        assertEquals(2, s.availablePermits());
        assertTrue(s.tryAcquire());
        assertTrue(s.tryAcquire());
        assertEquals(0, s.availablePermits());
        assertFalse(s.tryAcquire());
    }

    public void testAcquireReleaseInSameThread(){
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
            fail("unexpected exception");
        }
    }

    public void testAcquireUninterruptiblyReleaseInSameThread(){
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


    public void testAcquireReleaseInDifferentThreads() {
        final Semaphore s = new Semaphore(1);
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
			s.acquire();
                        s.release();
                        s.release();
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
            s.acquire();
            s.acquire();
            t.join();
	} catch( InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testTimedAcquireReleaseInSameThread(){
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
            fail("unexpected exception");
        }
    }

    public void testTimedAcquireReleaseInDifferentThreads() {
        final Semaphore s = new Semaphore(1);
	Thread t = new Thread(new Runnable(){
		public void run(){
		    try{
                        s.release();
                        assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                        s.release();
                        assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));

		    }catch(InterruptedException ie){
                        fail("unexpected exception");
                    }
		}
	    });
	t.start();
        try {
            assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release();
            assertTrue(s.tryAcquire(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            s.release();
            t.join();
	} catch( InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testAcquire_InterruptedException(){
	final Semaphore s = new Semaphore(0);
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
    
    public void testTryAcquire_InterruptedException(){
	final Semaphore s = new Semaphore(0);
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
            e.printStackTrace();
            fail("unexpected exception");
        }
    }

}
