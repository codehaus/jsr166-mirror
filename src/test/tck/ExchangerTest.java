/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class ExchangerTest extends TestCase{
   
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    public static Test suite() {
	return new TestSuite(ExchangerTest.class);
    }

    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 
    
    private final static Integer one = new Integer(1);
    private final static Integer two = new Integer(2);

    public void testExchange(){
        final Exchanger e = new Exchanger();
	Thread t1 = new Thread(new Runnable(){
		public void run(){
		    try{
			Object v = e.exchange(one);
                        assertEquals(v, two);
                        Object w = e.exchange(v);
                        assertEquals(w, one);
		    }catch(InterruptedException e){
                        fail("unexpected exception");
                    }
		}
	    });
	Thread t2 = new Thread(new Runnable(){
		public void run(){
		    try{
			Object v = e.exchange(two);
                        assertEquals(v, one);
                        Object w = e.exchange(v);
                        assertEquals(w, two);
		    }catch(InterruptedException e){
                        fail("unexpected exception");
                    }
		}
	    });
        try {
            t1.start();
            t2.start();
            t1.join();
            t2.join();
        } catch(InterruptedException ex) {
            fail("unexpected exception");
        }
    }

    public void testExchange1_InterruptedException(){
        final Exchanger e = new Exchanger();
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
                        e.exchange(one);
                        fail("should throw");
                    }catch(InterruptedException success){
                    }
                }
            });
        try{
            t.start();
            Thread.sleep(SHORT_DELAY_MS);
            t.interrupt();
            t.join();
        } catch(InterruptedException ex) {
            fail("unexpected exception");
        }
    }

    public void testExchange2_InterruptedException(){
        final Exchanger e = new Exchanger();
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
                        e.exchange(null, MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
                        fail("should throw");
                    } catch(InterruptedException success){
                    } catch(Exception e2){
                        fail("should throw IE");
                    }
                }
            });
        try{
            t.start();
            t.interrupt();
            t.join();
        } catch(InterruptedException ex){
            fail("unexpected exception");
        }
    }

    public void testExchange3_TimeOutException(){
        final Exchanger e = new Exchanger();
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
                        e.exchange(null, SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
                        fail("should throw");
                    }catch(TimeoutException success){}
                    catch(InterruptedException e2){
                        fail("should throw TOE");
                    }
                }
            });
        try{
            t.start();
            t.join();
        } catch(InterruptedException ex){
            fail("unexpected exception");
        }
    }
}
