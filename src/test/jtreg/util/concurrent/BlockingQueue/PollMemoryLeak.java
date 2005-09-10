/*
 * @test %I% %E%
 * @bug 6236036 6264015
 * @compile PollMemoryLeak.java
 * @run main/othervm -Xmx8m PollMemoryLeak
 * @summary  Checks for OutOfMemoryError when an unbounded
 * number of aborted timed waits occur without a signal.
 */

import java.util.concurrent.*;

public class PollMemoryLeak {
    public static void main(String[] args) throws InterruptedException {
	final BlockingQueue[] qs = {
	    new LinkedBlockingQueue(10),
	    new ArrayBlockingQueue(10),
	    new SynchronousQueue(),
	    new SynchronousQueue(true),
	};
        final long start = System.currentTimeMillis();
        final long end = start + 10 * 1000;
        while (System.currentTimeMillis() < end)
	    for (BlockingQueue q : qs)
		q.poll(1, TimeUnit.NANOSECONDS);
    }
}
