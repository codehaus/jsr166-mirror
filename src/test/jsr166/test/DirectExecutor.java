package jsr166.test;

import java.util.concurrent.Executor;


/**
 * Trivial executor for use in testing
 */
class DirectExecutor implements Executor {
    public void execute(Runnable r) {
        r.run();
    }
}

