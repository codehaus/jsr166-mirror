/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */


import junit.framework.*;
import java.util.concurrent.*;
import java.io.*;

public class TimeUnitTest extends TestCase {
      static public boolean DEBUG = false;


    public static void main(String[] args) {
	junit.textui.TestRunner.run(suite());	
    }
    
    public static Test suite() {
	return new TestSuite(TimeUnitTest.class);
    }

    public void testConvert() {
        for (long t = 0; t < 10; ++t) {
            assertEquals(t, 
                         TimeUnit.SECONDS.convert(t, 
                                                  TimeUnit.SECONDS));
            assertEquals(t, 
                         TimeUnit.SECONDS.convert(1000 * t, 
                                                  TimeUnit.MILLISECONDS));
            assertEquals(t, 
                         TimeUnit.SECONDS.convert(1000000 * t, 
                                                  TimeUnit.MICROSECONDS));
            assertEquals(t, 
                         TimeUnit.SECONDS.convert(1000000000 * t, 
                                                  TimeUnit.NANOSECONDS));
            assertEquals(1000 * t, 
                         TimeUnit.MILLISECONDS.convert(t, 
                                                  TimeUnit.SECONDS));
            assertEquals(t, 
                         TimeUnit.MILLISECONDS.convert(t, 
                                                  TimeUnit.MILLISECONDS));
            assertEquals(t, 
                         TimeUnit.MILLISECONDS.convert(1000 * t, 
                                                  TimeUnit.MICROSECONDS));
            assertEquals(t, 
                         TimeUnit.MILLISECONDS.convert(1000000 * t, 
                                                  TimeUnit.NANOSECONDS));
            assertEquals(1000000 * t, 
                         TimeUnit.MICROSECONDS.convert(t, 
                                                  TimeUnit.SECONDS));
            assertEquals(1000 * t, 
                         TimeUnit.MICROSECONDS.convert(t, 
                                                  TimeUnit.MILLISECONDS));
            assertEquals(t, 
                         TimeUnit.MICROSECONDS.convert(t, 
                                                  TimeUnit.MICROSECONDS));
            assertEquals(t, 
                         TimeUnit.MICROSECONDS.convert(1000 * t, 
                                                  TimeUnit.NANOSECONDS));
            assertEquals(1000000000 * t, 
                         TimeUnit.NANOSECONDS.convert(t, 
                                                  TimeUnit.SECONDS));
            assertEquals(1000000 * t, 
                         TimeUnit.NANOSECONDS.convert(t, 
                                                  TimeUnit.MILLISECONDS));
            assertEquals(1000 * t, 
                         TimeUnit.NANOSECONDS.convert(t, 
                                                  TimeUnit.MICROSECONDS));
            assertEquals(t, 
                         TimeUnit.NANOSECONDS.convert(t, 
                                                  TimeUnit.NANOSECONDS));
        }
    }

    public void testToNanos() {
        for (long t = 0; t < 10; ++t) {
            assertEquals(1000000000 * t, 
                         TimeUnit.SECONDS.toNanos(t));

            assertEquals(1000000 * t, 
                         TimeUnit.MILLISECONDS.toNanos(t));
            assertEquals(1000 * t, 
                         TimeUnit.MICROSECONDS.toNanos(t));
            assertEquals(t, 
                         TimeUnit.SECONDS.NANOSECONDS.toNanos(t));
        }
    }


    public void testConvertSaturate() {
        assertEquals(Long.MAX_VALUE,
                     TimeUnit.NANOSECONDS.convert(Long.MAX_VALUE / 2,
                                                  TimeUnit.SECONDS));
        assertEquals(Long.MIN_VALUE,
                     TimeUnit.NANOSECONDS.convert(-Long.MAX_VALUE / 4,
                                                  TimeUnit.SECONDS));

    }

    public void testToNanosSaturate() {
            assertEquals(Long.MAX_VALUE,
                         TimeUnit.MILLISECONDS.toNanos(Long.MAX_VALUE / 2));
            assertEquals(Long.MIN_VALUE,
                         TimeUnit.MILLISECONDS.toNanos(-Long.MAX_VALUE / 3));
            
    }


    public void testToString() {
        String s = TimeUnit.SECONDS.toString();
        assertTrue(s.indexOf("econd") >= 0);
    }

    // Exception tests
    
    /**
     *  This test specifically to catch the unreported exception
     */
    public void testTimedWaitForUnreportedIllegalMonitorException() {
	//created a new thread with anonymous runnable

        Thread t = new Thread(new Runnable() {
                public void run() {
                    Object o = new Object();
                    TimeUnit tu = TimeUnit.MILLISECONDS;
                    try {
                        tu.timedWait(o,40000);
                        fail("should throw");
                    }
                    catch (InterruptedException ie) {
                        fail("should not throw IE here");
                    } 
                    catch(IllegalMonitorStateException success) {
                    }
                    
                }
            });
        t.start();
        try {
            Thread.sleep(100);
            t.interrupt();
            t.join();
        } catch(Exception e) {
            fail("Unexpected exception");
        }
    }
    
    /**
     *  Test to verify that timedWait will throw InterruptedException.
     *  Thread t waits on timedWait while the main thread interrupts it.
     *  Note:  This does not throw IllegalMonitorException since timeWait
     *         is synchronized on o
     */
    public void testTimedWait() {
	Thread t = new Thread(new Runnable() {
		public void run() {
		    Object o = new Object();
		    
		    TimeUnit tu = TimeUnit.MILLISECONDS;
		    try {
			synchronized(o) {
			    tu.timedWait(o,1000);
			}
                        fail("should throw");
		    }
		    catch(InterruptedException success) {} 
		    catch(IllegalMonitorStateException failure) {
			fail("should not throw");
		    }
		}
	    });
	t.start();
        try {
            Thread.sleep(100);
            t.interrupt();
            t.join();
        } catch(Exception e) {
            fail("Unexpected exception");
        }
    }
    
    
    /**
     *  Test to verify that timedJoin will throw InterruptedException.
     *  Thread t waits on timedJoin while the main thread interrupts it.
     */
    public void testTimedJoin() {
	Thread t = new Thread(new Runnable() {
		public void run() {
		    TimeUnit tu = TimeUnit.MILLISECONDS;	
		    try {
			Thread s = new Thread(new Runnable() {
                                public void run() {
                                    try{
                                        Thread.sleep(1000);
                                    }catch(InterruptedException success){}
                                }
                            });
			s.start();
			tu.timedJoin(s,1000);
                        fail("should throw");
		    }
		    catch(Exception e) {}
		}
	    });
	t.start();
        try {
            Thread.sleep(100);
            t.interrupt();
            t.join();
        } catch(Exception e) {
            fail("Unexpected exception");
        }
    }
    
    /**
     *  Test to verify that timedSleep will throw InterruptedException.
     *  Thread t waits on timedSleep while the main thread interrupts it.
     */
    public void testTimedSleep() {
	//created a new thread with anonymous runnable

	Thread t = new Thread(new Runnable() {
		public void run() {
		    TimeUnit tu = TimeUnit.MILLISECONDS;
		    try {
			tu.sleep(1000);
                        fail("should throw");
		    }
		    catch(InterruptedException success) {} 
		}
	    });
	t.start();
        try {
            Thread.sleep(100);
            t.interrupt();
            t.join();
        } catch(Exception e) {
            fail("Unexpected exception");
        }
    }

    public void testSerialization() {
        TimeUnit q = TimeUnit.MILLISECONDS;

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream(10000);
            ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(bout));
            out.writeObject(q);
            out.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
            ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(bin));
            TimeUnit r = (TimeUnit)in.readObject();
            
            assertEquals(q.toString(), r.toString());
        } catch(Exception e){
            e.printStackTrace();
            fail("unexpected exception");
        }
    }

}
