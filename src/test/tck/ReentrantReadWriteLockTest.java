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

public class ReentrantReadWriteLockTest extends JSR166TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(ReentrantReadWriteLockTest.class);
    }


    /*
     * write-locking and read-locking an unlocked lock succeed
     */
    public void testLock() { 
	ReentrantReadWriteLock rl = new ReentrantReadWriteLock();
        rl.writeLock().lock();
        rl.writeLock().unlock();
        rl.readLock().lock();
        rl.readLock().unlock();
    }


    /*
     * locking an unlocked fair lock succeeds
     */
    public void testFairLock() { 
	ReentrantReadWriteLock rl = new ReentrantReadWriteLock(true);
        rl.writeLock().lock();
        rl.writeLock().unlock();
        rl.readLock().lock();
        rl.readLock().unlock();
    }

    /**
     * write-unlocking an unlocked lock throws IllegalMonitorStateException
     */
    public void testUnlock_IllegalMonitorStateException() { 
	ReentrantReadWriteLock rl = new ReentrantReadWriteLock();
	try {
	    rl.writeLock().unlock();
	    shouldThrow();
	} catch(IllegalMonitorStateException success){}
    }


    /**
     * write-lockInterruptibly is interruptible
     */
    public void testWriteLockInterruptibly_Interrupted() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
			lock.writeLock().lockInterruptibly();
			threadShouldThrow();
		    } catch(InterruptedException success){}
		}
	    });
        try {
            t.start();
            t.interrupt();
            lock.writeLock().unlock();
            t.join();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * timed write-trylock is interruptible
     */
    public void testWriteTryLock_Interrupted() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
			lock.writeLock().tryLock(1000,TimeUnit.MILLISECONDS);
			threadShouldThrow();
		    } catch(InterruptedException success){}
		}
	    });
        try {
            t.start();
            t.interrupt();
            lock.writeLock().unlock();
            t.join();
        } catch(Exception e){
            unexpectedException();
        }
    }

    /**
     * read-lockInterruptibly is interruptible
     */
    public void testReadLockInterruptibly_Interrupted() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
			lock.readLock().lockInterruptibly();
			threadShouldThrow();
		    } catch(InterruptedException success){}
		}
	    });
        try {
            t.start();
            t.interrupt();
            lock.writeLock().unlock();
            t.join();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * timed read-trylock is interruptible
     */
    public void testReadTryLock_Interrupted() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
			lock.readLock().tryLock(1000,TimeUnit.MILLISECONDS);
			threadShouldThrow();
		    } catch(InterruptedException success){}
		}
	    });
        try {
            t.start();
            t.interrupt();
            t.join();
        } catch(Exception e){
            unexpectedException();
        }
    }

    
    /**
     * write-trylock fails if locked
     */
    public void testWriteTryLockWhenLocked() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    threadAssertFalse(lock.writeLock().tryLock());
		}
	    });
        try {
            t.start();
            t.join();
            lock.writeLock().unlock();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * read-trylock fails if locked
     */
    public void testReadTryLockWhenLocked() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    threadAssertFalse(lock.readLock().tryLock());
		}
	    });
        try {
            t.start();
            t.join();
            lock.writeLock().unlock();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * Multiple threads can hold a read lock when not write-locked
     */
    public void testMultipleReadLocks() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.readLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    threadAssertTrue(lock.readLock().tryLock());
                    lock.readLock().unlock();
		}
	    });
        try {
            t.start();
            t.join();
            lock.readLock().unlock();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * A writelock succeeds after reading threads unlock
     */
    public void testWriteAfterMultipleReadLocks() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.readLock().lock();
	Thread t1 = new Thread(new Runnable() {
                public void run() {
                    lock.readLock().lock();
                    lock.readLock().unlock();
		}
	    });
	Thread t2 = new Thread(new Runnable() {
                public void run() {
                    lock.writeLock().lock();
                    lock.writeLock().unlock();
		}
	    });

        try {
            t1.start();
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.readLock().unlock();
            t1.join(MEDIUM_DELAY_MS);
            t2.join(MEDIUM_DELAY_MS);
            assertTrue(!t1.isAlive());
            assertTrue(!t2.isAlive());
           
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * Readlocks succeed after a writing thread unlocks
     */
    public void testReadAfterWriteLock() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t1 = new Thread(new Runnable() {
                public void run() {
                    lock.readLock().lock();
                    lock.readLock().unlock();
		}
	    });
	Thread t2 = new Thread(new Runnable() {
                public void run() {
                    lock.readLock().lock();
                    lock.readLock().unlock();
		}
	    });

        try {
            t1.start();
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.writeLock().unlock();
            t1.join(MEDIUM_DELAY_MS);
            t2.join(MEDIUM_DELAY_MS);
            assertTrue(!t1.isAlive());
            assertTrue(!t2.isAlive());
           
        } catch(Exception e){
            unexpectedException();
        }
    } 


    /**
     * Read trylock succeeds if readlocked but not writelocked
     */
    public void testTryLockWhenReadLocked() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.readLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    threadAssertTrue(lock.readLock().tryLock());
                    lock.readLock().unlock();
		}
	    });
        try {
            t.start();
            t.join();
            lock.readLock().unlock();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    

    /**
     * write trylock fails when readlocked
     */
    public void testWriteTryLockWhenReadLocked() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.readLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
                    threadAssertFalse(lock.writeLock().tryLock());
		}
	    });
        try {
            t.start();
            t.join();
            lock.readLock().unlock();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    

    /**
     * write timed trylock times out if locked
     */
    public void testWriteTryLock_Timeout() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
		    try {
                        threadAssertFalse(lock.writeLock().tryLock(1, TimeUnit.MILLISECONDS));
                    } catch (Exception ex) {
                        threadUnexpectedException();
                    }
		}
	    });
        try {
            t.start();
            t.join();
            lock.writeLock().unlock();
        } catch(Exception e){
            unexpectedException();
        }
    } 

    /**
     * read timed trylock times out if write-locked
     */
    public void testReadTryLock_Timeout() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run() {
		    try {
                        threadAssertFalse(lock.readLock().tryLock(1, TimeUnit.MILLISECONDS));
                    } catch (Exception ex) {
                        threadUnexpectedException();
                    }
		}
	    });
        try {
            t.start();
            t.join();
            lock.writeLock().unlock();
        } catch(Exception e){
            unexpectedException();
        }
    } 


    /**
     * write lockInterruptibly succeeds if lock free else is interruptible
     */
    public void testWriteLockInterruptibly() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	try {
            lock.writeLock().lockInterruptibly();
        } catch(Exception e) {
            unexpectedException();
        }
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lockInterruptibly();
			threadShouldThrow();
		    }
		    catch(InterruptedException success) {
                    }
		}
	    });
        try {
            t.start();
            t.interrupt();
            t.join();
            lock.writeLock().unlock();
        } catch(Exception e){
            unexpectedException();
        }
    }

    /**
     *  read lockInterruptibly succeeds if lock free else is interruptible
     */
    public void testReadLockInterruptibly() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	try {
            lock.writeLock().lockInterruptibly();
        } catch(Exception e) {
            unexpectedException();
        }
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.readLock().lockInterruptibly();
			threadShouldThrow();
		    }
		    catch(InterruptedException success) {
                    }
		}
	    });
        try {
            t.start();
            t.interrupt();
            t.join();
            lock.writeLock().unlock();
        } catch(Exception e){
            unexpectedException();
        }
    }

    /**
     * Calling await without holding lock throws IllegalMonitorStateException
     */
    public void testAwait_IllegalMonitor() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
        try {
            c.await();
            shouldThrow();
        }
        catch (IllegalMonitorStateException success) {
        }
        catch (Exception ex) {
            shouldThrow();
        }
    }

    /**
     * Calling signal without holding lock throws IllegalMonitorStateException
     */
    public void testSignal_IllegalMonitor() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
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
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
        try {
            lock.writeLock().lock();
            long t = c.awaitNanos(100);
            assertTrue(t <= 0);
            lock.writeLock().unlock();
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }


    /**
     *  timed await without a signal times out
     */
    public void testAwait_Timeout() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
        try {
            lock.writeLock().lock();
            assertFalse(c.await(10, TimeUnit.MILLISECONDS));
            lock.writeLock().unlock();
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * awaitUntil without a signal times out
     */
    public void testAwaitUntil_Timeout() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
        try {
            lock.writeLock().lock();
            java.util.Date d = new java.util.Date();
            assertFalse(c.awaitUntil(new java.util.Date(d.getTime() + 10)));
            lock.writeLock().unlock();
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * await returns when signalled
     */
    public void testAwait() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lock();
                        c.await();
                        lock.writeLock().unlock();
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
                    }
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            lock.writeLock().lock();
            c.signal();
            lock.writeLock().unlock();
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            unexpectedException();
        }
    }

    /**
     * awaitUninterruptibly doesn't abort on interrupt
     */
    public void testAwaitUninterruptibly() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
                    lock.writeLock().lock();
                    c.awaitUninterruptibly();
                    lock.writeLock().unlock();
		}
	    });

        try {
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            lock.writeLock().lock();
            c.signal();
            lock.writeLock().unlock();
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
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lock();
                        c.await();
                        lock.writeLock().unlock();
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
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lock();
                        c.awaitNanos(SHORT_DELAY_MS * 2 * 1000000);
                        lock.writeLock().unlock();
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
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lock();
                        java.util.Date d = new java.util.Date();
                        c.awaitUntil(new java.util.Date(d.getTime() + 10000));
                        lock.writeLock().unlock();
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
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
	Thread t1 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lock();
                        c.await();
                        lock.writeLock().unlock();
		    }
		    catch(InterruptedException e) {
                        threadUnexpectedException();
                    }
		}
	    });

	Thread t2 = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lock();
                        c.await();
                        lock.writeLock().unlock();
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
            lock.writeLock().lock();
            c.signalAll();
            lock.writeLock().unlock();
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
     * A serialized lock deserializes as unlocked
     */
    public void testSerialization() {
        ReentrantReadWriteLock l = new ReentrantReadWriteLock();
        l.readLock().lock();
        l.readLock().unlock();

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(l);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            ReentrantReadWriteLock r = (ReentrantReadWriteLock) in.readObject();
            r.readLock().lock();
            r.readLock().unlock();
        } catch(Exception e){
            e.printStackTrace();
            unexpectedException();
        }
    }


}
