/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class CountDownLatchTest extends JSR166TestCase {
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(CountDownLatchTest.class);
    }

    public void testConstructor(){
        try {
            new CountDownLatch(-1);
            fail("should throw IllegalArgumentException");
        } catch(IllegalArgumentException success){}
    }

    public void testGetCount(){
	final CountDownLatch l = new CountDownLatch(2);
	assertEquals(2, l.getCount());
	l.countDown();
	assertEquals(1, l.getCount());
    }

    public void testAwait(){
	final CountDownLatch l = new CountDownLatch(2);

	Thread t = new Thread(new Runnable(){
		public void run(){
		    try {
			l.await();
		    } catch(InterruptedException e){
                        threadFail("unexpected exception");
                    }
		}
	    });
	t.start();
	try {
            assertEquals(l.getCount(), 2);
            Thread.sleep(SHORT_DELAY_MS);
            l.countDown();
            assertEquals(l.getCount(), 1);
            l.countDown();
            assertEquals(l.getCount(), 0);
            t.join();
        } catch (InterruptedException e){
            fail("unexpected exception");
        }
    }
    

    public void testTimedAwait(){
	final CountDownLatch l = new CountDownLatch(2);

	Thread t = new Thread(new Runnable(){
		public void run(){
		    try {
			threadAssertTrue(l.await(SMALL_DELAY_MS, TimeUnit.MILLISECONDS));
		    } catch(InterruptedException e){
                        threadFail("unexpected exception");
                    }
		}
	    });
	t.start();
	try {
            assertEquals(l.getCount(), 2);
            Thread.sleep(SHORT_DELAY_MS);
            l.countDown();
            assertEquals(l.getCount(), 1);
            l.countDown();
            assertEquals(l.getCount(), 0);
            t.join();
        } catch (InterruptedException e){
            fail("unexpected exception");
        }
    }
    



    public void testAwait_InterruptedException(){
        final CountDownLatch l = new CountDownLatch(1);
        Thread t = new Thread(new Runnable(){
                public void run(){
                    try {
                        l.await();
                        threadFail("should throw");
                    } catch(InterruptedException success){}
                }
            });
	t.start();
	try {
            assertEquals(l.getCount(), 1);
            t.interrupt();
            t.join();
        } catch (InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testTimedAwait_InterruptedException(){
        final CountDownLatch l = new CountDownLatch(1);
        Thread t = new Thread(new Runnable(){
                public void run(){
                    try {
                        l.await(MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
                        threadFail("should throw");                        
                    } catch(InterruptedException success){}
                }
            });
        t.start();
        try {
            Thread.sleep(SHORT_DELAY_MS);
            assertEquals(l.getCount(), 1);
            t.interrupt();
            t.join();
        } catch (InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testAwaitTimeout(){
        final CountDownLatch l = new CountDownLatch(1);
        Thread t = new Thread(new Runnable(){
                public void run(){
                    try {
                        threadAssertFalse(l.await(SHORT_DELAY_MS, TimeUnit.MILLISECONDS));
                    } catch(InterruptedException ie){
                        threadFail("unexpected exception");
                    }
                }
            });
        t.start();
        try {
            assertEquals(l.getCount(), 1);
            t.join();
        } catch (InterruptedException e){
            fail("unexpected exception");
        }
    }

}
