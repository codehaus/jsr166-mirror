package jsr166.test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * Sample program, not a test, for trying out ad hoc JSR-166
 * functionality.
 */
public final class Sample {
    
    public static void main(String[] args) {
        
        System.out.println("starting main");
        
        new FixedRateBlurt("fixed rate a", 700);
        new FixedRateBlurt("fixed rate b", 500);
        new FixedRateBlurt("fixed rate c", 1300);
        
        new FixedDelayBlurt("fixed delay a", 700);
        new FixedDelayBlurt("fixed delay b", 500);
        new FixedDelayBlurt("fixed delay c", 1300);
        
        se.schedule(new Runnable() {
            public void run() {
                System.out.println("shutting down scheduler");
                se.shutdown();
                System.out.println("scheduler is shut down");
            }
        }, 10, SECONDS);
        
        System.out.println("finished main");
    }
    
    abstract static class Blurt implements Runnable {
        private final long start = milliTime();
        private int count = 0;
        private final String msg;
        protected final long msecs;

        Blurt(String msg, long msecs) {
            this.msg = msg;
            this.msecs = msecs;
            add();
        }

        abstract void add();

        public void run() {
            ++count;
            long t = milliTime() - start;
            System.out.println("t="+t+": "+msg+" "+count);
        }
    }

    static class FixedRateBlurt extends Blurt {
        FixedRateBlurt(String msg, long msecs) { super(msg, msecs); }
        void add() {
            blurts.add(se.scheduleAtFixedRate(this, msecs, msecs, MILLISECONDS));
        }
    }

    static class FixedDelayBlurt extends Blurt {
        FixedDelayBlurt(String msg, long msecs) { super(msg, msecs); }
        void add() {
            blurts.add(se.scheduleWithFixedDelay(this, msecs, msecs, MILLISECONDS));
        }
    }
    
    private static final ScheduledExecutorService se = 
        Executors.newScheduledThreadPool();
        
    private static final List< ScheduledFuture<?> > blurts = 
        new ArrayList< ScheduledFuture<?> >();
    
    private static long milliTime() {
        return MILLISECONDS.convert(System.nanoTime(), NANOSECONDS);
    }
}
