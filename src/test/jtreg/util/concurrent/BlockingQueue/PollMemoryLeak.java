/*
 * @test %I% %E%
 * @bug xxxxxx
 * @compile PollMemoryLeak.java
 * @run -Xmx32m main/timeout=3600 PollMemoryLeak
 * @summary  Checks for OutOfMemoryError when an unbounded
 * number of aborted timed waits occur without a signal.
 */


import java.util.concurrent.*;


public class PollMemoryLeak {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue  bl = new LinkedBlockingQueue(2000);
        while(true) {
            bl.poll(1, TimeUnit.NANOSECONDS);
        }
    }
}
