/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.*;
import java.io.*;

public class ReentrantLockTest extends TestCase {
    static int HOLD_COUNT_TEST_LIMIT = 20;

    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    
    public static Test suite() {
	return new TestSuite(ReentrantLockTest.class);
    }

    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 

    /*
     * Unlocks an unlocked lock, throws Illegal Monitor State
     * 
     */
    
    public void testIllegalMonitorStateException(){ 
	ReentrantLock rl = new ReentrantLock();
	try{
	    rl.unlock();
	    fail("Should of thown Illegal Monitor State Exception");

	} catch(IllegalMonitorStateException success){}


    }
    
    /*
     * makes a lock, locks it, tries to aquire the lock in another thread
     * interrupts that thread and waits for an interrupted Exception to
     * be thrown.
     */

    public void testInterruptedException(){ 
	final ReentrantLock lock = new ReentrantLock();
	lock.lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
			lock.lockInterruptibly();
			fail("should throw");
		    } catch(InterruptedException success){}
		}
	    });
	t.start();
	t.interrupt();
	lock.unlock();
    } 

    /*
     * tests for interrupted exception on a timed wait
     *
     */
    

    public void testInterruptedException2(){ 
	final ReentrantLock lock = new ReentrantLock();
	lock.lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
			lock.tryLock(1000,TimeUnit.MILLISECONDS);
			fail("should throw");
		    } catch(InterruptedException success){}
		}
	    });
	t.start();
	t.interrupt();
    }


    public void testTryLockWhenLocked() { 
	final ReentrantLock lock = new ReentrantLock();
	lock.lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    assertFalse(lock.tryLock());
		}
	    });
        try {
            t.start();
            t.join();
            lock.unlock();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    public void testTryLock_Timeout(){ 
	final ReentrantLock lock = new ReentrantLock();
	lock.lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
		    try {
                        assertFalse(lock.tryLock(1, TimeUnit.MILLISECONDS));
                    } catch (Exception ex) {
                        fail("unexpected exception");
                    }
		}
	    });
        try {
            t.start();
            t.join();
            lock.unlock();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 
    
    public void testGetHoldCount() {
	ReentrantLock lock = new ReentrantLock();
	for(int i = 1; i <= ReentrantLockTest.HOLD_COUNT_TEST_LIMIT;i++) {
	    lock.lock();
	    assertEquals(i,lock.getHoldCount());
	}
	for(int i = ReentrantLockTest.HOLD_COUNT_TEST_LIMIT; i > 0; i--) {
	    lock.unlock();
	    assertEquals(i-1,lock.getHoldCount());
	}
    }
    
   


    public void testIsLocked() {
	final ReentrantLock lock = new ReentrantLock();
	lock.lock();
	assertTrue(lock.isLocked());
	lock.unlock();
	assertFalse(lock.isLocked());
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    lock.lock();
		    try {
			Thread.sleep(SHORT_DELAY_MS * 2);
		    }
		    catch(Exception e) {}
		    lock.unlock();
		}
	    });
	try{
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.isLocked());
            t.join();
            assertFalse(lock.isLocked());
        } catch(Exception e){
            fail("unexpected exception");
        }
    }


    public void testLockInterruptibly() {
	final ReentrantLock lock = new ReentrantLock();	
	try {
            lock.lockInterruptibly();
        } catch(Exception e) {
            fail("unexpected exception");
        }
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lockInterruptibly();
			fail("should throw");
		    }
		    catch(InterruptedException e) {}
		}
	    });
        try {
            t.start();
            t.interrupt();
            assertTrue(lock.isLocked());
            assertTrue(lock.isHeldByCurrentThread());
            t.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    }

    public void testAwait_IllegalMonitor() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
        try {
            c.await();
            fail("should throw");
        }
        catch (IllegalMonitorStateException success) {
        }
        catch (Exception ex) {
            fail("should throw IMSE");
        }
    }

    public void testSignal_IllegalMonitor() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
        try {
            c.signal();
            fail("should throw");
        }
        catch (IllegalMonitorStateException success) {
        }
        catch (Exception ex) {
            fail("should throw IMSE");
        }
    }

    public void testAwaitNanos_Timeout() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
        try {
            lock.lock();
            long t = c.awaitNanos(100);
            assertTrue(t <= 0);
            lock.unlock();
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testAwait_Timeout() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
        try {
            lock.lock();
            assertFalse(c.await(10, TimeUnit.MILLISECONDS));
            lock.unlock();
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testAwaitUntil_Timeout() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
        try {
            lock.lock();
            java.util.Date d = new java.util.Date();
            assertFalse(c.awaitUntil(new java.util.Date(d.getTime() + 10)));
            lock.unlock();
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testAwait() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        c.await();
                        lock.unlock();
		    }
		    catch(InterruptedException e) {
                        fail("unexpected exception");
                    }
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.lock();
            c.signal();
            lock.unlock();
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testAwaitUninterruptibly() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
                    lock.lock();
                    c.awaitUninterruptibly();
                    lock.unlock();
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            lock.lock();
            c.signal();
            lock.unlock();
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testAwait_Interrupt() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        c.await();
                        lock.unlock();
                        fail("should throw");
		    }
		    catch(InterruptedException success) {
                    }
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testAwaitNanos_Interrupt() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        c.awaitNanos(SHORT_DELAY_MS * 2 * 1000000);
                        lock.unlock();
                        fail("should throw");
		    }
		    catch(InterruptedException success) {
                    }
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testAwaitUntil_Interrupt() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        java.util.Date d = new java.util.Date();
                        c.awaitUntil(new java.util.Date(d.getTime() + 10000));
                        lock.unlock();
                        fail("should throw");
		    }
		    catch(InterruptedException success) {
                    }
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testSignalAll() {
	final ReentrantLock lock = new ReentrantLock();	
        final Condition c = lock.newCondition();
	Thread t1 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        c.await();
                        lock.unlock();
		    }
		    catch(InterruptedException e) {
                        fail("unexpected exception");
                    }
		}
	    });

	Thread t2 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        c.await();
                        lock.unlock();
		    }
		    catch(InterruptedException e) {
                        fail("unexpected exception");
                    }
		}
	    });

        try {
            t1.start();
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.lock();
            c.signalAll();
            lock.unlock();
            t1.join(SHORT_DELAY_MS);
            t2.join(SHORT_DELAY_MS);
            assertFalse(t1.isAlive());
            assertFalse(t2.isAlive());
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testSerialization() {
        ReentrantLock l = new ReentrantLock();
        l.lock();
        l.unlock();

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            ReentrantLock r = (ReentrantLock) in.readObject();
            r.lock();
            r.unlock();
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }

}
