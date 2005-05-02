/*
 * @test %I% %E%
 * @bug 4486658
 * @compile -source 1.5 ConcurrentQueueLoops.java
 * @run main/timeout=230 ConcurrentQueueLoops
 * @summary Checks that a set of threads can repeatedly get and modify items
 */
/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ConcurrentQueueLoops {
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static AtomicInteger totalItems;
    static boolean print = false;

    public static void main(String[] args) throws Exception {
        int maxStages = 8;
        int items = 100000;

        Class klass = null;
        if (args.length > 0) {
            try {
                klass = Class.forName(args[0]);
            } catch(ClassNotFoundException e) {
                throw new RuntimeException("Class " + args[0] + " not found.");
            }
        }
        else 
            klass = java.util.concurrent.ConcurrentLinkedQueue.class;

        if (args.length > 1) 
            maxStages = Integer.parseInt(args[1]);

        System.out.print("Class: " + klass.getName());
        System.out.println(" stages: " + maxStages);

        print = false;
        System.out.println("Warmup...");
        oneRun(klass, 1, items);
        Thread.sleep(100);
        oneRun(klass, 1, items);
        Thread.sleep(100);
        print = true;

        for (int i = 1; i <= maxStages; i += (i+1) >>> 1) {
            oneRun(klass, i, items);
        }
        pool.shutdown();
   }

    static class Stage implements Callable<Integer> {
        final Queue<Integer> queue;
        final CyclicBarrier barrier;
        int items;
        Stage (Queue<Integer> q, CyclicBarrier b, int items) {
            queue = q; 
            barrier = b;
            this.items = items;
        }

        public Integer call() {
            // Repeatedly take something from queue if possible,
            // transform it, and put back in.
            try {
                barrier.await();
                int l = (int)System.nanoTime();
                int takes = 0;
                int seq = l;
                for (;;) {
                    Integer item = queue.poll();
                    if (item != null) {
                        ++takes;
                        l = LoopHelpers.compute2(item.intValue());
                    }
                    else if (takes != 0) {
                        totalItems.getAndAdd(-takes);
                        takes = 0;
                    }
                    else if (totalItems.get() <= 0)
                        break;
                    l = LoopHelpers.compute1(l);
                    if (items > 0) {
                        --items;
                        while (!queue.offer(new Integer(l^seq++))) ;
                    }
                    else if ( (l & (3 << 5)) == 0) // spinwait
                        Thread.sleep(1);
                }
                return new Integer(l);
            }
            catch (Exception ie) { 
                ie.printStackTrace();
                throw new Error("Call loop failed");
            }
        }
    }

    static void oneRun(Class klass, int n, int items) throws Exception {
        Queue<Integer> q = (Queue<Integer>)klass.newInstance();
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier = new CyclicBarrier(n + 1, timer);
        totalItems = new AtomicInteger(n * items);
        ArrayList<Future<Integer>> results = new ArrayList<Future<Integer>>(n);
        for (int i = 0; i < n; ++i) 
            results.add(pool.submit(new Stage(q, barrier, items)));

        if (print)
            System.out.print("Threads: " + n + "\t:");
        barrier.await();
        int total = 0;
        for (int i = 0; i < n; ++i) {
            Future<Integer> f = results.get(i);
            Integer r = f.get();
            total += r.intValue();
        }
        long endTime = System.nanoTime();
        long time = endTime - timer.startTime;
        if (print)
            System.out.println(LoopHelpers.rightJustify(time / (items * n)) + " ns per item");
        if (total == 0) // avoid overoptimization
            System.out.println("useless result: " + total);
        
    }
}
