package jsr166.test;

import java.util.*;
import java.util.concurrent.*;


public interface TimerExecutor extends Executor {

    TimerTask schedule(Runnable task, Date time);

    TimerTask schedule(Runnable task, Date firstTime, long period, TimeUnit periodUnit);

    TimerTask schedule(Runnable task, long delay, TimeUnit delayUnit,
                                      long period, TimeUnit periodUnit);

    // correspondingly for the other Timer.schedule... methods
}
