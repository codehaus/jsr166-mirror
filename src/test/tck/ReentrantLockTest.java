/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.*;
import java.util.*;
import java.io.*;

public class ReentrantLockTest extends JSR166TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(ReentrantLockTest.class);
    }

    static int HOLD_COUNT_TEST_LIMIT = 20;

    class InterruptibleLockRunnable implements Runnable {
        final ReentrantLock lock;
        InterruptibleLockRunnable(ReentrantLock l) { lock = l; }
        public void run(){
            try{
                lock.lockInterruptibly();
            } catch(InterruptedException success){}
        }
    }

    // Same, except must interrupt
    class InterruptedLockRunnable implements Runnable {
        final ReentrantLock lock;
        InterruptedLockRunnable(ReentrantLock l) { lock = l; }
        public void run(){
            try{
                lock.lockInterruptibly();
                threadFail("should throw");
            } catch(InterruptedException success){}
        }
    }

    /**
     * To expose protected methods
     */
    static class MyReentrantLock extends ReentrantLock {
        MyReentrantLock() { super(); }
        public Collection<Thread> getQueuedThreads() { 
            return super.getQueuedThreads(); 
        }
        public ConditionObject newCondition() { 
            return new MyCondition(this);
        }

        static class MyCondition extends ReentrantLock.ConditionObject {
            MyCondition(MyReentrantLock l) { super(l); }
            public Collection<Thread> getWaitingThreads() { 
                return super.getWaitingThreads(); 
            }
        }

    }

    /*
     * Unlocking an unlocked lock throws IllegalMonitorStateException
     */
    public void testIllegalMonitorStateException(){ 
	ReentrantLock rl = new ReentrantLock();
	try{
	    rl.unlock();
	    fail("Should of thown Illegal Monitor State Exception");

	} catch(IllegalMonitorStateException success){}
    }

    /*
     * lockInterruptibly is interruptible.
     */
    public void testInterruptedException(){ 
	final ReentrantLock lock = new ReentrantLock();
	lock.lock();
	Thread t = new Thread(new InterruptedLockRunnable(lock));
        try {
            t.start();
            t.interrupt();
            lock.unlock();
            t.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    /*
     * getLockQueueLength reports number of waiting threads
     */
    public void testgetLockQueueLength(){ 
	final ReentrantLock lock = new ReentrantLock();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        try {
            assertEquals(0, lock.getLockQueueLength());
            lock.lock();
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(1, lock.getLockQueueLength());
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(2, lock.getLockQueueLength());
            t1.interrupt();
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(1, lock.getLockQueueLength());
            lock.unlock();
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(0, lock.getLockQueueLength());
            t1.join();
            t2.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    /*
     * getQueuedThreads includes waiting threads
     */
    public void testGetQueuedThreads(){ 
	final MyReentrantLock lock = new MyReentrantLock();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        try {
            assertTrue(lock.getQueuedThreads().isEmpty());
            lock.lock();
            assertTrue(lock.getQueuedThreads().isEmpty());
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.getQueuedThreads().contains(t1));
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.getQueuedThreads().contains(t1));
            assertTrue(lock.getQueuedThreads().contains(t2));
            t1.interrupt();
            Thread.sleep(SHORT_DELAY_MS);
            assertFalse(lock.getQueuedThreads().contains(t1));
            assertTrue(lock.getQueuedThreads().contains(t2));
            lock.unlock();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.getQueuedThreads().isEmpty());
            t1.join();
            t2.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 


    /*
     * timed trylock is interruptible.
     */
    public void testInterruptedException2(){ 
	final ReentrantLock lock = new ReentrantLock();
	lock.lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
			lock.tryLock(MEDIUM_DELAY_MS,TimeUnit.MILLISECONDS);
			threadFail("should throw");
		    } catch(InterruptedException success){}
		}
	    });
        try {
            t.start();
            t.interrupt();
        } catch(Exception e){
            fail("unexpected exception");
        }
    }


    /**
     * Trylock on a locked lock fails
     */
    public void testTryLockWhenLocked() { 
	final ReentrantLock lock = new ReentrantLock();
	lock.lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    threadAssertFalse(lock.tryLock());
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

    /**
     * Timed Trylock on a locked lock times out
     */
    public void testTryLock_Timeout(){ 
	final ReentrantLock lock = new ReentrantLock();
	lock.lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
		    try {
                        threadAssertFalse(lock.tryLock(1, TimeUnit.MILLISECONDS));
                    } catch (Exception ex) {
                        threadFail("unexpected exception");
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
    
    /**
     * getHoldCount returns number of recursive holds
     */
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
    
   
    /**
     * isLocked is true when locked and false when not
     */
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
			Thread.sleep(SMALL_DELAY_MS);
		    }
		    catch(Exception e) {
                        threadFail("unexpected exception");
                    }
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


    /**
     * lockInterruptibly succeeds when unlocked, else is interruptible
     */
    public void testLockInterruptibly() {
	final ReentrantLock lock = new ReentrantLock();	
	try {
            lock.lockInterruptibly();
        } catch(Exception e) {
            fail("unexpected exception");
        }
	Thread t = new Thread(new InterruptedLockRunnable(lock));
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
            assertFalse(c.await(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
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
        final ReentrantLock.ConditionObject c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        c.await();
                        lock.unlock();
		    }
		    catch(InterruptedException e) {
                        threadFail("unexpected exception");
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

    public void testHasWaiters() {
	final ReentrantLock lock = new ReentrantLock();	
        final ReentrantLock.ConditionObject c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        threadAssertFalse(c.hasWaiters());
                        threadAssertEquals(0, c.getWaitQueueLength());
                        c.await();
                        lock.unlock();
		    }
		    catch(InterruptedException e) {
                        threadFail("unexpected exception");
                    }
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.lock();
            assertTrue(c.hasWaiters());
            assertEquals(1, c.getWaitQueueLength());
            c.signal();
            lock.unlock();
            Thread.sleep(SHORT_DELAY_MS);
            lock.lock();
            assertFalse(c.hasWaiters());
            assertEquals(0, c.getWaitQueueLength());
            lock.unlock();
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testGetWaitQueueLength() {
	final ReentrantLock lock = new ReentrantLock();	
        final ReentrantLock.ConditionObject c = lock.newCondition();
	Thread t1 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        threadAssertFalse(c.hasWaiters());
                        threadAssertEquals(0, c.getWaitQueueLength());
                        c.await();
                        lock.unlock();
		    }
		    catch(InterruptedException e) {
                        threadFail("unexpected exception");
                    }
		}
	    });

	Thread t2 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        threadAssertTrue(c.hasWaiters());
                        threadAssertEquals(1, c.getWaitQueueLength());
                        c.await();
                        lock.unlock();
		    }
		    catch(InterruptedException e) {
                        threadFail("unexpected exception");
                    }
		}
	    });

        try {
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.lock();
            assertTrue(c.hasWaiters());
            assertEquals(2, c.getWaitQueueLength());
            c.signalAll();
            lock.unlock();
            Thread.sleep(SHORT_DELAY_MS);
            lock.lock();
            assertFalse(c.hasWaiters());
            assertEquals(0, c.getWaitQueueLength());
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

    public void testGetWaitingThreads() {
	final MyReentrantLock lock = new MyReentrantLock();	
        final MyReentrantLock.MyCondition c = (MyReentrantLock.MyCondition)lock.newCondition();
	Thread t1 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        threadAssertTrue(c.getWaitingThreads().isEmpty());
                        c.await();
                        lock.unlock();
		    }
		    catch(InterruptedException e) {
                        threadFail("unexpected exception");
                    }
		}
	    });

	Thread t2 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        threadAssertFalse(c.getWaitingThreads().isEmpty());
                        c.await();
                        lock.unlock();
		    }
		    catch(InterruptedException e) {
                        threadFail("unexpected exception");
                    }
		}
	    });

        try {
            lock.lock();
            assertTrue(c.getWaitingThreads().isEmpty());
            lock.unlock();
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.lock();
            assertTrue(c.hasWaiters());
            assertTrue(c.getWaitingThreads().contains(t1));
            assertTrue(c.getWaitingThreads().contains(t2));
            c.signalAll();
            lock.unlock();
            Thread.sleep(SHORT_DELAY_MS);
            lock.lock();
            assertFalse(c.hasWaiters());
            assertTrue(c.getWaitingThreads().isEmpty());
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
                        threadFail("should throw");
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
                        threadFail("should throw");
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
                        threadFail("should throw");
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
                        threadFail("unexpected exception");
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
                        threadFail("unexpected exception");
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
