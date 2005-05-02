import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
import java.util.*;

public final class ContextSwitchTest {
    final static int iters = 1000000;
    static AtomicReference turn = new AtomicReference();
    public static void main(String[] args) throws Exception {
        MyThread a = new MyThread();
        MyThread b = new MyThread();
        a.other = b;
        b.other = a;
        turn.set(a);
        long startTime = System.nanoTime();
        a.start();
        b.start();
        a.join();
        b.join();
        long endTime = System.nanoTime();
        int np = a.nparks + b.nparks;
        System.out.println((endTime - startTime) / np);
    }

    private static int nextRandom(int x) { 
        int t = (x % 127773) * 16807 - (x / 127773) * 2836;
        return (t > 0)? t : t + 0x7fffffff;
    }

    static final class MyThread extends Thread {
        volatile Thread other;
        volatile int nparks;
        volatile int result;

        public void run() {
            int x = 17;
            int p = 0;
            for (int i = 0; i < iters; ++i) {
                while (!turn.compareAndSet(other, this)) {
                    LockSupport.park();
                    ++p;
                }
                x = nextRandom(x);
                LockSupport.unpark(other);
            }
            LockSupport.unpark(other);
            nparks = p;
            result = x;
            System.out.println("parks: " + p);
            
        }
    }
}


