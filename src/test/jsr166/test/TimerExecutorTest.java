package jsr166.test;

import java.util.*;
import java.util.concurrent.*;

import junit.framework.TestCase;


/**
 * Tests the TimerExecutor method
 */
public class TimerExecutorTest extends TestCase {

    public void testTimedExecute () {
        TimerExecutor te = TimerExecutors.newTimerExecutor(new DirectExecutorService());
        Date inASecond= new Date(System.currentTimeMillis() + 1000);
        flag = false;
        TimerTask timerTask = te.schedule(new Runnable() {
            public void run () {
                flag = true;
            }
        }, inASecond);
        try {
            Thread.sleep(3000);
        }
        catch (InterruptedException e) {
            fail("task interrupted");
        }
        assertTrue("flag should have been set", flag);
    }

    private boolean flag = false;
}
