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
     * A simple mutex class, from the AbstractQueuedSynchronizer
     * javadoc.  All tests exercise this as a sample user extension.
     * Other methods/features of AbstractQueuedSynchronizerTest are
     * tested implicitly in ReentrantLock, ReentrantReadWriteLock, and
     * Semaphore test classes
     */
    static class Mutex implements Lock, java.io.Serializable {
        private static class Sync extends AbstractQueuedSynchronizer {
            boolean isLocked() { return getState() == 1; }

            public boolean tryAcquireExclusive(boolean isQueued, int acquires) {
                assert acquires == 1; // Does not use multiple acquires
                return compareAndSetState(0, 1);
            }
            
            public boolean tryReleaseExclusive(int releases) {
                setState(0);
                return true;
            }
            
            public void checkConditionAccess(Thread thread, boolean waiting) {
                if (getState() == 0) throw new IllegalMonitorStateException();
            }
            
            Condition newCondition() { return new ConditionObject(); }
            
            private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
                s.defaultReadObject();
                setState(0); // reset to unlocked state
            }
        }
         
        private final Sync sync = new Sync();
        public boolean tryLock() { 
            return sync.tryAcquireExclusive(false, 1);
        }
        public void lock() { 
            sync.acquireExclusiveUninterruptibly(1);
        }
        public void lockInterruptibly() throws InterruptedException { 
            sync.acquireExclusiveInterruptibly(1);
        }
        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return sync.acquireExclusiveTimed(1, unit.toNanos(timeout));
        }
        public void unlock() { sync.releaseExclusive(1); }
        public Condition newCondition() { return sync.newCondition(); }
        public boolean isLocked() { return sync.isLocked(); }
        public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
    }

    /**
     * A runnable calling lockInterruptibly
     */
    class InterruptibleLockRunnable implements Runnable {
        final Mutex lock;
        InterruptibleLockRunnable(Mutex l) { lock = l; }
        public void run() {
            try {
                lock.lockInterruptibly();
            } catch(InterruptedException success){}
        }
    }


    /**
     * A runnable calling lockInterruptibly that expects to be
     * interrupted
     */
    class InterruptedLockRunnable implements Runnable {
        final Mutex lock;
        InterruptedLockRunnable(Mutex l) { lock = l; }
        public void run() {
            try {
                lock.lockInterruptibly();
                threadShouldThrow();
            } catch(InterruptedException success){}
        }
    }
    
    /**
     * locking an unlocked lock succeeds
     */
    public void testLock() { 
	Mutex rl = new Mutex();
        rl.lock();
        assertTrue(rl.isLocked());
        rl.unlock();
    }

    /**
     * tryLock on an unlocked lock succeeds
     */
    public void testTryLock() { 
	Mutex rl = new Mutex();
        assertTrue(rl.tryLock());
        assertTrue(rl.isLocked());
        rl.unlock();
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
            lock.lock();
            t1.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.hasQueuedThreads());
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.hasQueuedThreads());
            t1.interrupt();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.hasQueuedThreads());
            lock.unlock();
            Thread.sleep(SHORT_DELAY_MS);
            assertFalse(lock.hasQueuedThreads());
            t1.join();
            t2.join();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * timed tryLock is interruptible.
     */
    public void testInterruptedException2() { 
	final Mutex lock = new Mutex();
	lock.lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
			lock.tryLock(MEDIUM_DELAY_MS,TimeUnit.MILLISECONDS);
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
     * TryLock on a locked lock fails
     */
    public void testTryLockWhenLocked() { 
	final Mutex lock = new Mutex();
	lock.lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    threadAssertFalse(lock.tryLock());
		}
	    });
        try {
            t.start();
            t.join();
            lock.unlock();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * Timed tryLock on a locked lock times out
     */
    public void testTryLock_Timeout() { 
	final Mutex lock = new Mutex();
	lock.lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
		    try {
                        threadAssertFalse(lock.tryLock(1, TimeUnit.MILLISECONDS));
                    } catch (Exception ex) {
                        threadUnexpectedException();
                    }
		}
	    });
        try {
            t.start();
            t.join();
            lock.unlock();
        } catch(Exception e){
            unexpectedException();
        }
    } 
    
   
    /**
     * isLocked is true when locked and false when not
     */
    public void testIsLocked() {
	final Mutex lock = new Mutex();
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
                        threadUnexpectedException();
                    }
		    lock.unlock();
		}
	    });
	try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            assertTrue(lock.isLocked());
            t.join();
            assertFalse(lock.isLocked());
        } catch(Exception e){
            unexpectedException();
        }
    }


    /**
     * lockInterruptibly is interruptible.
     */
    public void testLockInterruptibly1() { 
	final Mutex lock = new Mutex();
	lock.lock();
	Thread t = new Thread(new InterruptedLockRunnable(lock));
        try {
            t.start();
            t.interrupt();
            lock.unlock();
            t.join();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * lockInterruptibly succeeds when unlocked, else is interruptible
     */
    public void testLockInterruptibly2() {
	final Mutex lock = new Mutex();	
	try {
            lock.lockInterruptibly();
        } catch(Exception e) {
            unexpectedException();
        }
	Thread t = new Thread(new InterruptedLockRunnable(lock));
        try {
            t.start();
            t.interrupt();
            assertTrue(lock.isLocked());
            t.join();
        } catch(Exception e){
            unexpectedException();
        }
    }

    /**
     * Calling await without holding lock throws IllegalMonitorStateException
     */
    public void testAwait_IllegalMonitor() {
	final Mutex lock = new Mutex();	
        final Condition c = lock.newCondition();
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
        final Condition c = lock.newCondition();
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
        final Condition c = lock.newCondition();
        try {
            lock.lock();
            long t = c.awaitNanos(100);
            assertTrue(t <= 0);
            lock.unlock();
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
        final Condition c = lock.newCondition();
        try {
            lock.lock();
            assertFalse(c.await(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
            lock.unlock();
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
        final Condition c = lock.newCondition();
        try {
            lock.lock();
            java.util.Date d = new java.util.Date();
            assertFalse(c.awaitUntil(new java.util.Date(d.getTime() + 10)));
            lock.unlock();
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
        final Condition c = lock.newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.lock();
                        c.await();
                        lock.unlock();
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
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
            unexpectedException();
        }
    }
    
}
