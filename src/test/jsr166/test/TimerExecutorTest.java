package jsr166.test;

import java.util.*;
import java.util.concurrent.*;

import junit.framework.TestCase;


/**
 * Tests the TimerExecutor method
 */
public class TimerExecutorTest extends TestCase {

    public void testTimedExecute () {
        TimerExecutor te = TimerExecutors.newTimerExecutor(new DirectExecutor());
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

    private static class DirectExecutor implements Executor {
        public void execute (Runnable r) {
            r.run();
        }
    }

    private boolean flag = false;
}
