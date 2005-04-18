/*
 * @test %I% %E%
 * @bug 6236036
 * @compile PollMemoryLeak.java
 * @run -Xmx16m main/timeout=3600 PollMemoryLeak
 * @summary  Checks for OutOfMemoryError when an unbounded
 * number of aborted timed waits occur without a signal.
 */


import java.util.concurrent.*;


public class PollMemoryLeak {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue b1 = new LinkedBlockingQueue(10);
        BlockingQueue b2 = new ArrayBlockingQueue(10);
        BlockingQueue b3 = new SynchronousQueue();
        BlockingQueue b4 = new SynchronousQueue(true);
        long start = System.currentTimeMillis();
        long end = start + 2 * 60 * 1000; // 2 minutes
        while(System.currentTimeMillis() < end) {
            b1.poll(1, TimeUnit.NANOSECONDS);
            b2.poll(1, TimeUnit.NANOSECONDS);
            b3.poll(1, TimeUnit.NANOSECONDS);
            b4.poll(1, TimeUnit.NANOSECONDS);
        }
    }
}
