import java.util.concurrent.Executor;

class DirectExecutor implements Executor {
    public void execute(Runnable r) {
        r.run();
    }
}

