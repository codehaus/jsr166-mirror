/*
 * Written by members of JCP JSR-166 Expert Group and released to the
 * public domain. Use, modify, and redistribute this code in any way
 * without acknowledgement. Other contributors include Andrew Wright,
 * Jeffrey Hayes, Pat Fischer, Mike Judd.
 */

import junit.framework.*;
import java.util.*;
import java.util.concurrent.*;

public class CyclicBarrierTest extends TestCase{
    
    public static void main(String[] args) {
	junit.textui.TestRunner.run (suite());	
    }
    
    
    public static Test suite() {
	return new TestSuite(CyclicBarrierTest.class);
    }
    
    private static long SHORT_DELAY_MS = 100; 
    private static long MEDIUM_DELAY_MS = 1000;
    private static long LONG_DELAY_MS = 10000; 

    public void testConstructor1(){
        try{
            new CyclicBarrier(-1, (Runnable)null);
            fail("should throw");
        } catch(IllegalArgumentException e){}
    }

    public void testConstructor2(){
        try{
            new CyclicBarrier(-1);
            fail("should throw");
        } catch(IllegalArgumentException e){}
    }

    public void testConstructor3(){
        CyclicBarrier b = new CyclicBarrier(2);
	assertEquals(2, b.getParties());
        assertEquals(0, b.getNumberWaiting());
    }

    public void testSingleParty() {
        try {
            CyclicBarrier b = new CyclicBarrier(1);
            assertEquals(1, b.getParties());
            assertEquals(0, b.getNumberWaiting());
            b.await();
            b.await();
            assertEquals(0, b.getNumberWaiting());
        }
        catch(Exception e) {
            fail("unexpected exception");
        }
    }
    
    private volatile int countAction;
    private class MyAction implements Runnable {
        public void run() { ++countAction; }
    }

    public void testBarrierAction() {
        try {
            countAction = 0;
            CyclicBarrier b = new CyclicBarrier(1, new MyAction());
            assertEquals(1, b.getParties());
            assertEquals(0, b.getNumberWaiting());
            b.await();
            b.await();
            assertEquals(0, b.getNumberWaiting());
            assertEquals(countAction, 2);
        }
        catch(Exception e) {
            fail("unexpected exception");
        }
    }


    public void testTwoParties(){
        final CyclicBarrier b = new CyclicBarrier(2);
	Thread t = new Thread(new Runnable() {
		public void run(){
                    try {
                        b.await();
                        b.await();
                        b.await();
                        b.await();
                    } catch(Exception e){
                        fail("unexpected exception");
                    }}});

        try {
            t.start();
            b.await();
            b.await();
            b.await();
            b.await();
            t.join();
        } catch(Exception e){
            fail("unexpected exception");
        }
    }


    public void testAwait1_Interrupted_BrokenBarrier(){
        final CyclicBarrier c = new CyclicBarrier(3);
        Thread t1 = new Thread(new Runnable() {
                public void run(){
                    try{
                        c.await();
                        fail("should throw");
                    } catch(InterruptedException success){}                
                    catch(Exception b){
                        fail("should throw IE");
                    }
                }
            });
        Thread t2 = new Thread(new Runnable(){
                public void run(){
                    try{
                        c.await();
                        fail("should throw");                        
                    } catch(BrokenBarrierException success){
                    } catch(Exception i){
                        fail("should throw BBE");
                    }
                }
            });
        try {
            t1.start();
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            t1.interrupt();
            t1.join(); 
            t2.join();
        } catch(InterruptedException e){
            fail("unexpected exception");
        }
    }

    public void testAwait2_Interrupted_BrokenBarrier(){
      final CyclicBarrier c = new CyclicBarrier(3);
        Thread t1 = new Thread(new Runnable() {
                public void run(){
                    try{
                        c.await(MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
                        fail("should throw");
                    } catch(InterruptedException success){
                    } catch(Exception b){
                        fail("should throw IE");
                    }
                }
            });
        Thread t2 = new Thread(new Runnable(){
                public void run(){
                    try{
                        c.await(MEDIUM_DELAY_MS, TimeUnit.MILLISECONDS);
                        fail("should throw");                        
                    } catch(BrokenBarrierException success){
                    } catch(Exception i){
                        fail("should throw BBE");
                    }
                }
            });
        try {
            t1.start();
            t2.start();
            Thread.sleep(SHORT_DELAY_MS);
            t1.interrupt();
            t1.join(); 
            t2.join();
        } catch(InterruptedException e){
            fail("unexpected exception");
        }
    }
    
    public void testAwait3_TimeOutException(){
        final CyclicBarrier c = new CyclicBarrier(2);
        Thread t = new Thread(new Runnable() {
                public void run(){
                    try{
                        c.await(SHORT_DELAY_MS, TimeUnit.MILLISECONDS);
                        fail("should throw");
                    } catch(TimeoutException success){
                    } catch(Exception b){
                        fail("should throw TOE");
                        
                    }
                }
            });
        try {
            t.start();
            t.join(); 
        } catch(InterruptedException e){
            fail("unexpected exception");
        }
    }
    
}
