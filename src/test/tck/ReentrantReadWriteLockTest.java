/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.*;

public class ReentrantReadWriteLockTest extends TestCase {
    static int HOLD_COUNT_TEST_LIMIT = 20;
    
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    
    public static Test suite() {
	return new TestSuite(ReentrantReadWriteLockTest.class);
    }


    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 

    /*
     * Unlocks an unlocked lock, throws Illegal Monitor State
     * 
     */
    
    public void testIllegalMonitorStateException(){ 
	ReentrantReadWriteLock rl = new ReentrantReadWriteLock();
	try{
	    rl.writeLock().unlock();
	    fail("Should of thown Illegal Monitor State Exception");

	}catch(IllegalMonitorStateException sucess){}


    }
    
    /*
     * makes a lock, locks it, tries to aquire the lock in another thread
     * interrupts that thread and waits for an interrupted Exception to
     * be thrown.
     */

    public void testInterruptedException(){ 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
			lock.writeLock().lockInterruptibly();
			fail("should throw");
		    }catch(InterruptedException sucess){}
		}
	    });
	t.start();
	t.interrupt();
	lock.writeLock().unlock();
    } 

    /*
     * tests for interrupted exception on a timed wait
     *
     */
    

    public void testInterruptedException2(){ 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
			lock.writeLock().tryLock(1000,TimeUnit.MILLISECONDS);
			fail("should throw");
		    }catch(InterruptedException sucess){}
		}
	    });
	t.start();
	t.interrupt();
    }

    

    /*
     * current thread locks interruptibly the thread
     * another thread tries to aquire the lock and blocks 
     * on the call. interrupt the attempted aquireLock
     * assert that the first lock() call actually locked the lock
     * assert that the current thread is the one holding the lock
     */

    public void testLockedInterruptibly() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
	try {lock.writeLock().lockInterruptibly();} catch(Exception e) {}
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lockInterruptibly();
			fail("Failed to generate an Interrupted Exception");
		    }
		    catch(InterruptedException e) {}
		}
	    });
	t.start();
	t.interrupt();
    }
    

}
