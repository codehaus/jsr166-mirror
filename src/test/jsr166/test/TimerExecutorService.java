package jsr166.test;

import java.util.concurrent.*;

public interface TimerExecutorService extends TimerExecutor, ExecutorService {
    // mix-in interface
}
