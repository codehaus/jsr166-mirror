/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.*;

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

	}catch(IllegalMonitorStateException sucess){}


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
		    }catch(InterruptedException sucess){}
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
		    }catch(InterruptedException sucess){}
		}
	    });
	t.start();
	t.interrupt();
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

    /*
     * current thread locks interruptibly the thread
     * another thread tries to aquire the lock and blocks 
     * on the call. interrupt the attempted aquireLock
     * assert that the first lock() call actually locked the lock
     * assert that the current thread is the one holding the lock
     */

    public void testLockedInterruptibly() {
	final ReentrantLock lock = new ReentrantLock();	
	try {lock.lockInterruptibly();} catch(Exception e) {}
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lockInterruptibly();
			fail("Failed to generate an Interrupted Exception");
		    }
		    catch(InterruptedException e) {}
		}
	    });
	t.start();
	t.interrupt();
	assertTrue(lock.isLocked());
	assertTrue(lock.isHeldByCurrentThread());
    }
    

}
