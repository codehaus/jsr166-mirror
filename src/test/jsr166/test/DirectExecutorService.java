package jsr166.test;

import java.util.*;
import java.util.concurrent.*;

/**
 * Trivial executor for use in testing
 */
class DirectExecutorService extends AbstractExecutorService {
    public void execute(Runnable r) { r.run(); }
    public void shutdown() { shutdown = true; }
    public List<Runnable> shutdownNow() { shutdown = true; return Collections.EMPTY_LIST; }
    public boolean isShutdown() { return shutdown; }
    public boolean isTerminated() { return isShutdown(); }
    public boolean awaitTermination(long timeout, TimeUnit unit) { return isShutdown(); }
    private volatile boolean shutdown = false;
}

