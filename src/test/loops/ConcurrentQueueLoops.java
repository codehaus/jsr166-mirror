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
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

public class ConcurrentQueueLoops {
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static boolean print = false;
    static final Integer even = new Integer(42);
    static final Integer odd = new Integer(17);
    static int workMask;
    static final long RUN_TIME_NANOS = 5 * 1000L * 1000L * 1000L;

    public static void main(String[] args) throws Exception {
        int maxStages = 48;
        int work = 32;
        Class klass = null;
        if (args.length > 0) {
            try {
                klass = Class.forName(args[0]);
            } catch(ClassNotFoundException e) {
                throw new RuntimeException("Class " + args[0] + " not found.");
            }
        }

        if (args.length > 1) 
            maxStages = Integer.parseInt(args[1]);

        if (args.length > 2) 
            work = Integer.parseInt(args[2]);

        workMask = work - 1;
        System.out.print("Class: " + klass.getName());
        System.out.print(" stages: " + maxStages);
        System.out.println(" work: " + work);

        print = false;
        System.out.println("Warmup...");
        oneRun(klass, 4);
        Thread.sleep(100);
        oneRun(klass, 1);
        Thread.sleep(100);
        print = true;

        int k = 1;
        for (int i = 1; i <= maxStages;) {
            oneRun(klass, i);
            if (i == k) {
                k = i << 1;
                i = i + (i >>> 1);
            } 
            else 
                i = k;
        }
        pool.shutdown();
   }

    static final class Stage implements Callable<Integer> {
        final Queue<Integer> queue;
        final CyclicBarrier barrier;
        Stage (Queue<Integer> q, CyclicBarrier b) {
            queue = q; 
            barrier = b;
        }

        static int compute127(int l) {
            l = LoopHelpers.compute1(l);
            l = LoopHelpers.compute2(l);
            l = LoopHelpers.compute3(l);
            l = LoopHelpers.compute4(l);
            l = LoopHelpers.compute5(l);
            l = LoopHelpers.compute6(l);
            return l;
        }

        static int compute(int l) {
            if (l == 0) 
                return (int)System.nanoTime();
            int nn =  l & workMask;
            while (nn-- > 0) 
                l = compute127(l);
            return l;
        }

        public Integer call() {
            try {
                barrier.await();
                long now = System.nanoTime();
                long stopTime = now + RUN_TIME_NANOS;
                int l = (int)now;
                int takes = 0;
                int misses = 0;
                for (;;) {
                    l = compute(l);
                    Integer item = queue.poll();
                    if (item != null) {
                        l += item.intValue();
                        ++takes;
                    } else if ((misses++ & 255) == 0 &&
                               System.nanoTime() >= stopTime) {
                        break;
                    } else {
                        Integer a = ((l++ & 4)== 0)? even : odd;
                        queue.add(a);
                    }
                }
                return new Integer(takes);
            }
            catch (Exception ie) { 
                ie.printStackTrace();
                throw new Error("Call loop failed");
            }
        }
    }

    static void oneRun(Class klass, int n) throws Exception {
        Queue<Integer> q = (Queue<Integer>)klass.newInstance();
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier = new CyclicBarrier(n + 1, timer);
        ArrayList<Future<Integer>> results = new ArrayList<Future<Integer>>(n);
        for (int i = 0; i < n; ++i) 
            results.add(pool.submit(new Stage(q, barrier)));

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
        long tpi = time / total;
        if (print)
            System.out.println(LoopHelpers.rightJustify(tpi) + " ns per item");
        
    }

}
