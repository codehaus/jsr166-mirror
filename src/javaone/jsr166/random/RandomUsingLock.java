package jsr166.random;

import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

public class RandomUsingLock implements Random {
    public RandomUsingLock(long s) {
        seed = s;
    }

    public long next() {
        lock.lock();
        try {
            return seed = calculateNext(seed);
        }
        finally {
            lock.unlock();
        }
    }

    private long seed;
    private final Lock lock = new ReentrantLock();

    private long calculateNext(long s) {
        long t = (s % 127773) * 16807 - (s / 127773) * 2836;
        return (t > 0) ? t : t + 0x7fffffff;
    }
}
