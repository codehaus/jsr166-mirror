package jsr166.random;

import java.util.concurrent.atomic.*;

public class RandomUsingSynch implements Random {
    public RandomUsingSynch(long s) {
        seed = s;
    }

    public long next() {
        synchronized (lock) {
            return seed = calculateNext(seed);
        }
    }

    private long seed;
    private final Object lock = new Object();

    private long calculateNext(long s) {
        long t = (s % 127773) * 16807 - (s / 127773) * 2836;
        return (t > 0) ? t : t + 0x7fffffff;
    }
}
