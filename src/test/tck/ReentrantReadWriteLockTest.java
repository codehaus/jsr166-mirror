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

	}catch(IllegalMonitorStateException success){}
    }



    public void testInterruptedException(){ 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
			lock.writeLock().lockInterruptibly();
			fail("should throw");
		    }catch(InterruptedException success){}
		}
	    });
        try {
            t.start();
            t.interrupt();
            lock.writeLock().unlock();
            t.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    public void testInterruptedException2(){ 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
			lock.writeLock().tryLock(1000,TimeUnit.MILLISECONDS);
			fail("should throw");
		    }catch(InterruptedException success){}
		}
	    });
        try {
            t.start();
            t.interrupt();
            lock.writeLock().unlock();
            t.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    }

    public void testInterruptedException3(){ 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
			lock.readLock().lockInterruptibly();
			fail("should throw");
		    }catch(InterruptedException success){}
		}
	    });
        try {
            t.start();
            t.interrupt();
            lock.writeLock().unlock();
            t.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    public void testInterruptedException4(){ 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
			lock.readLock().tryLock(1000,TimeUnit.MILLISECONDS);
			fail("should throw");
		    }catch(InterruptedException success){}
		}
	    });
        try {
            t.start();
            t.interrupt();
            t.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    }

    
    public void testTryLockWhenLocked() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    assertFalse(lock.writeLock().tryLock());
		}
	    });
        try {
            t.start();
            t.join();
            lock.writeLock().unlock();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    public void testTryLockWhenLocked2() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    assertFalse(lock.readLock().tryLock());
		}
	    });
        try {
            t.start();
            t.join();
            lock.writeLock().unlock();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    public void testMultipleReadLocks() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.readLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    assertTrue(lock.readLock().tryLock());
                    lock.readLock().unlock();
		}
	    });
        try {
            t.start();
            t.join();
            lock.readLock().unlock();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    public void testWriteAfterMultipleReadLocks() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.readLock().lock();
	Thread t1 = new Thread(new Runnable() {
                public void run(){
                    lock.readLock().lock();
                    lock.readLock().unlock();
		}
	    });
	Thread t2 = new Thread(new Runnable() {
                public void run(){
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
            fail("unexpected exception");
        }
    } 

    public void testReadAfterWriteLock() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t1 = new Thread(new Runnable() {
                public void run(){
                    lock.readLock().lock();
                    lock.readLock().unlock();
		}
	    });
	Thread t2 = new Thread(new Runnable() {
                public void run(){
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
            fail("unexpected exception");
        }
    } 


    public void testTryLockWhenReadLocked() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.readLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    assertTrue(lock.readLock().tryLock());
                    lock.readLock().unlock();
		}
	    });
        try {
            t.start();
            t.join();
            lock.readLock().unlock();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    

    public void testWriteTryLockWhenReadLocked() { 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.readLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
                    assertFalse(lock.writeLock().tryLock());
		}
	    });
        try {
            t.start();
            t.join();
            lock.readLock().unlock();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    

    public void testTryLock_Timeout(){ 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
		    try {
                        assertFalse(lock.writeLock().tryLock(1, TimeUnit.MILLISECONDS));
                    } catch (Exception ex) {
                        fail("unexpected exception");
                    }
		}
	    });
        try {
            t.start();
            t.join();
            lock.writeLock().unlock();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 

    public void testTryLock_Timeout2(){ 
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	lock.writeLock().lock();
	Thread t = new Thread(new Runnable() {
                public void run(){
		    try {
                        assertFalse(lock.readLock().tryLock(1, TimeUnit.MILLISECONDS));
                    } catch (Exception ex) {
                        fail("unexpected exception");
                    }
		}
	    });
        try {
            t.start();
            t.join();
            lock.writeLock().unlock();
        } catch(Exception e){
            fail("unexpected exception");
        }
    } 


    public void testLockInterruptibly() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	try {
            lock.writeLock().lockInterruptibly();
        } catch(Exception e) {
            fail("unexpected exception");
        }
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lockInterruptibly();
			fail("should throw");
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
            fail("unexpected exception");
        }
    }

    public void testLockInterruptibly2() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	try {
            lock.writeLock().lockInterruptibly();
        } catch(Exception e) {
            fail("unexpected exception");
        }
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.readLock().lockInterruptibly();
			fail("should throw");
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
            fail("unexpected exception");
        }
    }

    public void testAwait_IllegalMonitor() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
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
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
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
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
        try {
            lock.writeLock().lock();
            long t = c.awaitNanos(100);
            assertTrue(t <= 0);
            lock.writeLock().unlock();
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testAwait_Timeout() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
        try {
            lock.writeLock().lock();
            assertFalse(c.await(10, TimeUnit.MILLISECONDS));
            lock.writeLock().unlock();
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

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
            fail("unexpected exception");
        }
    }

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
                        fail("unexpected exception");
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
            fail("unexpected exception");
        }
    }

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
            t.join(SHORT_DELAY_MS);
            assertFalse(t.isAlive());
        }
        catch (Exception ex) {
            fail("unexpected exception");
        }
    }

    public void testAwait_Interrupt() {
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lock();
                        c.await();
                        lock.writeLock().unlock();
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
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lock();
                        c.awaitNanos(SHORT_DELAY_MS * 2 * 1000000);
                        lock.writeLock().unlock();
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
	final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();	
        final Condition c = lock.writeLock().newCondition();
	Thread t = new Thread(new Runnable() { 
		public void run() {
		    try {
			lock.writeLock().lock();
                        java.util.Date d = new java.util.Date();
                        c.awaitUntil(new java.util.Date(d.getTime() + 10000));
                        lock.writeLock().unlock();
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
                        fail("unexpected exception");
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
                        fail("unexpected exception");
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
            fail("unexpected exception");
        }
    }

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
            fail("unexpected exception");
        }
    }


}
