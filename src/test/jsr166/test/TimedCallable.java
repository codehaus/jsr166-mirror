package jsr166.test;

import java.util.concurrent.*;

public class TimedCallable<T> implements Callable<T> {

    private final Executor exec;
    private final Callable<T> func;
    private final long msecs;

    public TimedCallable(Executor exec, Callable<T> func, long msecs) {
        this.exec = exec;
        this.func = func;
        this.msecs = msecs;
    }

    public T call() throws Exception {
        Future<T> ftask = Executors.execute(exec, func);
        try {
            return ftask.get(msecs, TimeUnit.MILLISECONDS);
        //} catch (InterruptedException e) {
        //    ftask.cancel(true);
        //    throw e;
        //} catch (TimeoutException e) {
        //    ftask.cancel(true);
        //    throw e;
        } finally {
            ftask.cancel(true);
        }
    }
}
