package java.util.concurrent;

/**
 * In a CountDownLatch initialized with a given <em>count</em>, the
 * <tt>await</tt> method blocks until <em>count</em> <tt>
 * countdown</tt> operations. After reaching zero, <tt>await</tt> is
 * unblocked forever more.  This is a one-shot phenomenon -- the count
 * cannot be reset.  If you need a version that resets the count,
 * consider using a CyclicBarrier.
 *
 * <p> A CountDownLatch initialized to one serves as a simple on/off latch.
 *
 * <p> <b>Sample usage.</b> Here are a set of classes in which a group
 * of worker threads use two countdown latches, the first as a start
 * signal, and the second as a way to notify a driver when all threads
 * are complete.
 *
 * <pre>
 * class Worker implements Runnable {
 *   private final CountDownLatch startSignal;
 *   private final CountDownLatch doneSignal;
 *   Worker(CountDownLatch startSignal, CountDownLatch doneSignal) {
 *      this.startSignal = startSignal;
 *      this.doneSignal = doneSignal;
 *   }
 *   public void run() {
 *      try {
 *        startSignal.await();
 *        doWork();
 *        doneSignal.countDown();
 *      }
 *      catch (InterruptedException ex) {} // return;
 *   }
 *
 *   void doWork() { ... }
 * }
 *
 * class Driver { // ...
 *   void main() throws InterruptedException {
 *     CountDownLatch startSignal = new CountDownLatch(1);
 *     CountDownLatch doneSignal = new CountDownLatch(N);
 *
 *     for (int i = 0; i < N; ++i) // create and start threads
 *       new Thread(new Worker(startSignal, doneSignal)).start();
 *
 *     doSomethingElse();            // don't let run yet
 *     startSignal.countDown();      // let all threads proceed
 *     doSomethingElse();
 *     doneSignal.await();           // wait for all to finish
 *   }
 * }
 * </pre>
 *
 **/
public class CountDownLatch {
    public CountDownLatch(int count) {}
    public void await() throws InterruptedException {}
    public boolean await(long time, TimeUnit granularity) throws InterruptedException {
        return false;
    }


    public void countDown() {}

    /**
     * Return the current count value.
     **/
    public long getCount() {
        return 0;
    }

}
