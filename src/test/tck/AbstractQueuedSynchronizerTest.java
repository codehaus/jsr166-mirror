/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 * Other contributors include Andrew Wright, Jeffrey Hayes, 
 * Pat Fisher, Mike Judd. 
 */


import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.io.*;

public class AbstractQueuedSynchronizerTest extends JSR166TestCase {
    public static void main(String[] args) {
        junit.textui.TestRunner.run (suite());
    }
    public static Test suite() {
        return new TestSuite(AbstractQueuedSynchronizerTest.class);
    }

    /**
     * A simple mutex class, adapted from the
     * AbstractQueuedSynchronizer javadoc.  Exclusive acquire tests
     * exercise this as a sample user extension.  Other
     * methods/features of AbstractQueuedSynchronizerTest are tested
     * via other test classes, including those for ReentrantLock,
     * ReentrantReadWriteLock, and Semaphore
     */
    static class Mutex extends AbstractQueuedSynchronizer {
        public boolean isHeldExclusively() { return getState() == 1; }
        
        public boolean tryAcquire(int acquires) {
            assertTrue(acquires == 1); 
            return compareAndSetState(0, 1);
        }
        
        public boolean tryRelease(int releases) {
            if (getState() == 0) throw new IllegalMonitorStateException();
            setState(0);
            return true;
        }
        
        public AbstractQueuedSynchronizer.ConditionObject newCondition() { return new AbstractQueuedSynchronizer.ConditionObject(); }
        
        public void lock() { 
            acquire(1);
        }

    }

    
    /**
     * A simple latch class, to test shared mode.
     */
    static class BooleanLatch extends AbstractQueuedSynchronizer { 
        public boolean isSignalled() { return getState() != 0; }

        public int tryAcquireShared(int ignore) {
            return isSignalled()? 1 : -1;
        }
        
        public boolean tryReleaseShared(int ignore) {
            setState(1);
            return true;
        }
    }

    /**
     * A runnable calling acquireInterruptibly
     */
    class InterruptibleLockRunnable implements Runnable {
        final Mutex lock;
        InterruptibleLockRunnable(Mutex l) { lock = l; }
        public void run() {
            try {
                lock.acquireInterruptibly(1);
            } catch(InterruptedException success){}
        }
    }


    /**
     * A runnable calling acquireInterruptibly that expects to be
     * interrupted
     */
    class InterruptedLockRunnable implements Runnable {
        final Mutex lock;
        InterruptedLockRunnable(Mutex l) { lock = l; }
        public void run() {
            try {
                lock.acquireInterruptibly(1);
                threadShouldThrow();
            } catch(InterruptedException success){}
        }
    }

    /**
     * isHeldExclusively is false upon construction
     */
    public void testIsHeldExclusively() { 
	Mutex rl = new Mutex();
        assertFalse(rl.isHeldExclusively());
    }
    
    /**
     * acquiring released lock succeeds
     */
    public void testAcquire() { 
	Mutex rl = new Mutex();
        rl.acquire(1);
        assertTrue(rl.isHeldExclusively());
        rl.release(1);
    }

    /**
     * tryAcquire on an released lock succeeds
     */
    public void testTryAcquire() { 
	Mutex rl = new Mutex();
        assertTrue(rl.tryAcquire(1));
        assertTrue(rl.isHeldExclusively());
        rl.release(1);
    }

    /**
     * hasQueuedThreads reports whether there are waiting threads
     */
    public void testhasQueuedThreads() { 
	final Mutex lock = new Mutex();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        try {
            assertFalse(lock.hasQueuedThreads());
            lock.acquire(1);
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.hasQueuedThreads());
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.hasQueuedThreads());
            t1.interrupt();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.hasQueuedThreads());
            lock.release(1);
            Thread.sleep(SHORT_DELAY_MS);
            assertFalse(lock.hasQueuedThreads());
            t1.join();
            t2.join();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * isQueued(null) throws NPE
     */
    public void testIsQueuedNPE() { 
	final Mutex lock = new Mutex();
        try {
            lock.isQueued(null);
            shouldThrow();
        } catch (NullPointerException success) {
        }
    }

    /**
     * isQueued reports whether a thread is queued.
     */
    public void testIsQueued() { 
	final Mutex lock = new Mutex();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        try {
            assertFalse(lock.isQueued(t1));
            assertFalse(lock.isQueued(t2));
            lock.acquire(1);
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.isQueued(t1));
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.isQueued(t1));
            assertTrue(lock.isQueued(t2));
            t1.interrupt();
            Thread.sleep(SHORT_DELAY_MS);
            assertFalse(lock.isQueued(t1));
            assertTrue(lock.isQueued(t2));
            lock.release(1);
            Thread.sleep(SHORT_DELAY_MS);
            assertFalse(lock.isQueued(t1));
            assertFalse(lock.isQueued(t2));
            t1.join();
            t2.join();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * getFirstQueuedThread returns first waiting thread or null is none
     */
    public void testGetFirstQueuedThread() { 
	final Mutex lock = new Mutex();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        try {
            assertNull(lock.getFirstQueuedThread());
            lock.acquire(1);
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(t1, lock.getFirstQueuedThread());
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(t1, lock.getFirstQueuedThread());
            t1.interrupt();
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(t2, lock.getFirstQueuedThread());
            lock.release(1);
            Thread.sleep(SHORT_DELAY_MS);
            assertNull(lock.getFirstQueuedThread());
            t1.join();
            t2.join();
        } catch(Exception e){
            unexpectedException();
        }
    } 


    /**
     * hasContended reports false if no thread has ever blocked, else true
     */
    public void testHasContended() { 
	final Mutex lock = new Mutex();
        Thread t1 = new Thread(new InterruptedLockRunnable(lock));
        Thread t2 = new Thread(new InterruptibleLockRunnable(lock));
        try {
            assertFalse(lock.hasContended());
            lock.acquire(1);
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.hasContended());
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.hasContended());
            t1.interrupt();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.hasContended());
            lock.release(1);
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.hasContended());
            t1.join();
            t2.join();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * tryAcquireNanos is interruptible.
     */
    public void testInterruptedException2() { 
	final Mutex lock = new Mutex();
	lock.acquire(1);
	Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
			lock.tryAcquireNanos(1, MEDIUM_DELAY_MS * 1000 * 1000);
			threadShouldThrow();
		    } catch(InterruptedException success){}
		}
	    });
        try {
            t.start();
            t.interrupt();
        } catch(Exception e){
            unexpectedException();
        }
    }


    /**
     * TryAcquire on a locked lock fails
     */
    public void testTryAcquireWhenLocked() { 
	final Mutex lock = new Mutex();
	lock.acquire(1);
	Thread t = new Thread(new Runnable() {
                public void run() {
                    threadAssertFalse(lock.tryAcquire(1));
		}
	    });
        try {
            t.start();
            t.join();
            lock.release(1);
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * tryAcquireNanos on a locked lock times out
     */
    public void testAcquireNanos_Timeout() { 
	final Mutex lock = new Mutex();
	lock.acquire(1);
	Thread t = new Thread(new Runnable() {
                public void run() {
		    try {
                        threadAssertFalse(lock.tryAcquireNanos(1, 1000 * 1000));
                    } catch (Exception ex) {
                        threadUnexpectedException();
                    }
		}
	    });
        try {
            t.start();
            t.join();
            lock.release(1);
        } catch(Exception e){
            unexpectedException();
        }
    } 
    
   
    /**
     * getState is true when acquired and false when not
     */
    public void testGetState() {
	final Mutex lock = new Mutex();
	lock.acquire(1);
	assertTrue(lock.isHeldExclusively());
	lock.release(1);
	assertFalse(lock.isHeldExclusively());
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    lock.acquire(1);
		    try {
			Thread.sleep(SMALL_DELAY_MS);
		    }
		    catch(Exception e) {
                        threadUnexpectedException();
                    }
		    lock.release(1);
		}
	    });
	try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.isHeldExclusively());
            t.join();
            assertFalse(lock.isHeldExclusively());
        } catch(Exception e){
            unexpectedException();
        }
    }


    /**
     * acquireInterruptibly is interruptible.
     */
    public void testAcquireInterruptibly1() { 
	final Mutex lock = new Mutex();
	lock.acquire(1);
	Thread t = new Thread(new InterruptedLockRunnable(lock));
        try {
            t.start();
            t.interrupt();
            lock.release(1);
            t.join();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * acquireInterruptibly succeeds when released, else is interruptible
     */
    public void testAcquireInterruptibly2() {
	final Mutex lock = new Mutex();	
	try {
            lock.acquireInterruptibly(1);
        } catch(Exception e) {
            unexpectedException();
        }
	Thread t = new Thread(new InterruptedLockRunnable(lock));
        try {
            t.start();
            t.interrupt();
            assertTrue(lock.isHeldExclusively());
            t.join();
        } catch(Exception e){
            unexpectedException();
        }
    }

    /**
     * owns is true for a condition created by lock else false
     */
    public void testOwns() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
        final Mutex lock2 = new Mutex();
        assertTrue(lock.owns(c));
        assertFalse(lock2.owns(c));
    }

    /**
     * Calling await without holding lock throws IllegalMonitorStateException
     */
    public void testAwait_IllegalMonitor() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
        try {
            c.await();
            shouldThrow();
        }
        catch (IllegalMonitorStateException success) {
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * Calling signal without holding lock throws IllegalMonitorStateException
     */
    public void testSignal_IllegalMonitor() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
        try {
            c.signal();
            shouldThrow();
        }
        catch (IllegalMonitorStateException success) {
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * awaitNanos without a signal times out
     */
    public void testAwaitNanos_Timeout() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
        try {
            lock.acquire(1);
            long t = c.awaitNanos(100);
            assertTrue(t <= 0);
            lock.release(1);
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     *  timed await without a signal times out
     */
    public void testAwait_Timeout() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
        try {
            lock.acquire(1);
            assertFalse(c.await(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            lock.release(1);
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * awaitUntil without a signal times out
     */
    public void testAwaitUntil_Timeout() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
        try {
            lock.acquire(1);
            java.util.Date d = new java.util.Date();
            assertFalse(c.awaitUntil(new java.util.Date(d.getTime() + 10)));
            lock.release(1);
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * await returns when signalled
     */
    public void testAwait() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        c.await();
                        lock.release(1);
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
                    }
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.acquire(1);
            c.signal();
            lock.release(1);
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }



    /**
     * hasWaiters throws NPE if null
     */
    public void testHasWaitersNPE() {
	final Mutex lock = new Mutex();
        try {
            lock.hasWaiters(null);
            shouldThrow();
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * getWaitQueueLength throws NPE if null
     */
    public void testGetWaitQueueLengthNPE() {
	final Mutex lock = new Mutex();
        try {
            lock.getWaitQueueLength(null);
            shouldThrow();
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            unexpectedException();
        }
    }


    /**
     * getWaitingThreads throws NPE if null
     */
    public void testGetWaitingThreadsNPE() {
	final Mutex lock = new Mutex();
        try {
            lock.getWaitingThreads(null);
            shouldThrow();
        } catch (NullPointerException success) {
        } catch (Exception ex) {
            unexpectedException();
        }
    }


    /**
     * hasWaiters throws IAE if not owned
     */
    public void testHasWaitersIAE() {
	final Mutex lock = new Mutex();
        final AbstractQueuedSynchronizer.ConditionObject c = (lock.newCondition());
	final Mutex lock2 = new Mutex();
        try {
            lock2.hasWaiters(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {
        } catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * hasWaiters throws IMSE if not locked
     */
    public void testHasWaitersIMSE() {
	final Mutex lock = new Mutex();
        final AbstractQueuedSynchronizer.ConditionObject c = (lock.newCondition());
        try {
            lock.hasWaiters(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {
        } catch (Exception ex) {
            unexpectedException();
        }
    }


    /**
     * getWaitQueueLength throws IAE if not owned
     */
    public void testGetWaitQueueLengthIAE() {
	final Mutex lock = new Mutex();
        final AbstractQueuedSynchronizer.ConditionObject c = (lock.newCondition());
	final Mutex lock2 = new Mutex();
        try {
            lock2.getWaitQueueLength(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {
        } catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * getWaitQueueLength throws IMSE if not locked
     */
    public void testGetWaitQueueLengthIMSE() {
	final Mutex lock = new Mutex();
        final AbstractQueuedSynchronizer.ConditionObject c = (lock.newCondition());
        try {
            lock.getWaitQueueLength(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {
        } catch (Exception ex) {
            unexpectedException();
        }
    }


    /**
     * getWaitingThreads throws IAE if not owned
     */
    public void testGetWaitingThreadsIAE() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = (lock.newCondition());
	final Mutex lock2 = new Mutex();	
        try {
            lock2.getWaitingThreads(c);
            shouldThrow();
        } catch (IllegalArgumentException success) {
        } catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * getWaitingThreads throws IMSE if not locked
     */
    public void testGetWaitingThreadsIMSE() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = (lock.newCondition());
        try {
            lock.getWaitingThreads(c);
            shouldThrow();
        } catch (IllegalMonitorStateException success) {
        } catch (Exception ex) {
            unexpectedException();
        }
    }



    /**
     * hasWaiters returns true when a thread is waiting, else false
     */
    public void testHasWaiters() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        threadAssertFalse(lock.hasWaiters(c));
                        threadAssertEquals(0, lock.getWaitQueueLength(c));
                        c.await();
                        lock.release(1);
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
                    }
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.acquire(1);
            assertTrue(lock.hasWaiters(c));
            assertEquals(1, lock.getWaitQueueLength(c));
            c.signal();
            lock.release(1);
            Thread.sleep(SHORT_DELAY_MS);
            lock.acquire(1);
            assertFalse(lock.hasWaiters(c));
            assertEquals(0, lock.getWaitQueueLength(c));
            lock.release(1);
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * getWaitQueueLength returns number of waiting threads
     */
    public void testGetWaitQueueLength() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
	Thread t1 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        threadAssertFalse(lock.hasWaiters(c));
                        threadAssertEquals(0, lock.getWaitQueueLength(c));
                        c.await();
                        lock.release(1);
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
                    }
		}
	    });

	Thread t2 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        threadAssertTrue(lock.hasWaiters(c));
                        threadAssertEquals(1, lock.getWaitQueueLength(c));
                        c.await();
                        lock.release(1);
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
                    }
		}
	    });

        try {
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.acquire(1);
            assertTrue(lock.hasWaiters(c));
            assertEquals(2, lock.getWaitQueueLength(c));
            c.signalAll();
            lock.release(1);
            Thread.sleep(SHORT_DELAY_MS);
            lock.acquire(1);
            assertFalse(lock.hasWaiters(c));
            assertEquals(0, lock.getWaitQueueLength(c));
            lock.release(1);
            t1.join(SHORT_DELAY_MS);
            t2.join(SHORT_DELAY_MS);
            assertFalse(t1.isAlive());
            assertFalse(t2.isAlive());
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * getWaitingThreads returns only and all waiting threads
     */
    public void testGetWaitingThreads() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
	Thread t1 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        threadAssertTrue(lock.getWaitingThreads(c).isEmpty());
                        c.await();
                        lock.release(1);
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
                    }
		}
	    });

	Thread t2 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        threadAssertFalse(lock.getWaitingThreads(c).isEmpty());
                        c.await();
                        lock.release(1);
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
                    }
		}
	    });

        try {
            lock.acquire(1);
            assertTrue(lock.getWaitingThreads(c).isEmpty());
            lock.release(1);
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.acquire(1);
            assertTrue(lock.hasWaiters(c));
            assertTrue(lock.getWaitingThreads(c).contains(t1));
            assertTrue(lock.getWaitingThreads(c).contains(t2));
            c.signalAll();
            lock.release(1);
            Thread.sleep(SHORT_DELAY_MS);
            lock.acquire(1);
            assertFalse(lock.hasWaiters(c));
            assertTrue(lock.getWaitingThreads(c).isEmpty());
            lock.release(1);
            t1.join(SHORT_DELAY_MS);
            t2.join(SHORT_DELAY_MS);
            assertFalse(t1.isAlive());
            assertFalse(t2.isAlive());
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }



    /**
     * awaitUninterruptibly doesn't abort on interrupt
     */
    public void testAwaitUninterruptibly() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
                    lock.acquire(1);
                    c.awaitUninterruptibly();
                    lock.release(1);
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            lock.acquire(1);
            c.signal();
            lock.release(1);
            assert(t.isInterrupted());
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * await is interruptible
     */
    public void testAwait_Interrupt() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        c.await();
                        lock.release(1);
                        threadShouldThrow();
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
            unexpectedException();
        }
    }

    /**
     * awaitNanos is interruptible
     */
    public void testAwaitNanos_Interrupt() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        c.awaitNanos(1000 * 1000 * 1000); // 1 sec
                        lock.release(1);
                        threadShouldThrow();
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
            unexpectedException();
        }
    }

    /**
     * awaitUntil is interruptible
     */
    public void testAwaitUntil_Interrupt() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        java.util.Date d = new java.util.Date();
                        c.awaitUntil(new java.util.Date(d.getTime() + 10000));
                        lock.release(1);
                        threadShouldThrow();
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
            unexpectedException();
        }
    }

    /**
     * signalAll wakes up all threads
     */
    public void testSignalAll() {
	final Mutex lock = new Mutex();	
        final AbstractQueuedSynchronizer.ConditionObject c = lock.newCondition();
	Thread t1 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        c.await();
                        lock.release(1);
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
                    }
		}
	    });

	Thread t2 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.acquire(1);
                        c.await();
                        lock.release(1);
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
                    }
		}
	    });

        try {
            t1.start();
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.acquire(1);
            c.signalAll();
            lock.release(1);
            t1.join(SHORT_DELAY_MS);
            t2.join(SHORT_DELAY_MS);
            assertFalse(t1.isAlive());
            assertFalse(t2.isAlive());
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }


    /**
     * toString indicates current state
     */
    public void testToString() {
        Mutex lock = new Mutex();
        String us = lock.toString();
        assertTrue(us.indexOf("State = 0") >= 0);
        lock.acquire(1);
        String ls = lock.toString();
        assertTrue(ls.indexOf("State = 1") >= 0);
    }

    /**
     * A serialized AQS deserializes with current state
     */
    public void testSerialization() {
        Mutex l = new Mutex();
        l.acquire(1);
        assertTrue(l.isHeldExclusively());

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            Mutex r = (Mutex) in.readObject();
            assertTrue(r.isHeldExclusively());
        } catch(Exception e){
            e.printStackTrace();
            unexpectedException();
        }
    }


    /**
     * tryReleaseShared setting state changes getState
     */
    public void testGetStateWithReleaseShared() {
	final BooleanLatch l = new BooleanLatch();
	assertFalse(l.isSignalled());
	l.releaseShared(0);
	assertTrue(l.isSignalled());
    }

    /**
     * release and has no effect when already signalled
     */
    public void testReleaseShared() {
	final BooleanLatch l = new BooleanLatch();
	assertFalse(l.isSignalled());
	l.releaseShared(0);
	assertTrue(l.isSignalled());
	l.releaseShared(0);
	assertTrue(l.isSignalled());
    }

    /**
     * acquireSharedInterruptibly returns after release, but not before
     */
    public void testAcquireSharedInterruptibly() {
	final BooleanLatch l = new BooleanLatch();

	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
                        threadAssertFalse(l.isSignalled());
			l.acquireSharedInterruptibly(0);
                        threadAssertTrue(l.isSignalled());
		    } catch(InterruptedException e){
                        threadUnexpectedException();
                    }
		}
	    });
	try {
            t.start();
            assertFalse(l.isSignalled());
            Thread.sleep(SHORT_DELAY_MS);
            l.releaseShared(0);
            assertTrue(l.isSignalled());
            t.join();
        } catch (InterruptedException e){
            unexpectedException();
        }
    }
    

    /**
     * acquireSharedTimed returns after release
     */
    public void testAsquireSharedTimed() {
	final BooleanLatch l = new BooleanLatch();

	Thread t = new Thread(new Runnable() {
		public void run() {
		    try {
                        threadAssertFalse(l.isSignalled());
			threadAssertTrue(l.tryAcquireSharedNanos(0, MEDIUM_DELAY_MS* 1000 * 1000));
                        threadAssertTrue(l.isSignalled());

		    } catch(InterruptedException e){
                        threadUnexpectedException();
                    }
		}
	    });
	try {
            t.start();
            assertFalse(l.isSignalled());
            Thread.sleep(SHORT_DELAY_MS);
            l.releaseShared(0);
            assertTrue(l.isSignalled());
            t.join();
        } catch (InterruptedException e){
            unexpectedException();
        }
    }
    
    /**
     * acquireSharedInterruptibly throws IE if interrupted before released
     */
    public void testAcquireSharedInterruptibly_InterruptedException() {
        final BooleanLatch l = new BooleanLatch();
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        threadAssertFalse(l.isSignalled());
                        l.acquireSharedInterruptibly(0);
                        threadShouldThrow();
                    } catch(InterruptedException success){}
                }
            });
	t.start();
	try {
            assertFalse(l.isSignalled());
            t.interrupt();
            t.join();
        } catch (InterruptedException e){
            unexpectedException();
        }
    }

    /**
     * acquireSharedTimed throws IE if interrupted before released
     */
    public void testAcquireSharedNanos_InterruptedException() {
        final BooleanLatch l = new BooleanLatch();
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        threadAssertFalse(l.isSignalled());
                        l.tryAcquireSharedNanos(0, SMALL_DELAY_MS* 1000 * 1000);
                        threadShouldThrow();                        
                    } catch(InterruptedException success){}
                }
            });
        t.start();
        try {
            Thread.sleep(SHORT_DELAY_MS);
            assertFalse(l.isSignalled());
            t.interrupt();
            t.join();
        } catch (InterruptedException e){
            unexpectedException();
        }
    }

    /**
     * acquireSharedTimed times out if not released before timeout
     */
    public void testAcquireSharedNanos_Timeout() {
        final BooleanLatch l = new BooleanLatch();
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        threadAssertFalse(l.isSignalled());
                        threadAssertFalse(l.tryAcquireSharedNanos(0, SMALL_DELAY_MS* 1000 * 1000));
                    } catch(InterruptedException ie){
                        threadUnexpectedException();
                    }
                }
            });
        t.start();
        try {
            Thread.sleep(SHORT_DELAY_MS);
            assertFalse(l.isSignalled());
            t.join();
        } catch (InterruptedException e){
            unexpectedException();
        }
    }

    
}
