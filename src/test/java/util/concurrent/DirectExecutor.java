package java.util.concurrent;

/**
 * Trivial executor for use in testing
 */
class DirectExecutor implements Executor {
    public void execute(Runnable r) {
        r.run();
    }
}

