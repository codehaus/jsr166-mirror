package jsr166.random;

import java.util.concurrent.atomic.*;

public class RandomUsingAtomic implements Random {
    public RandomUsingAtomic(long s) {
        seed = new AtomicLong(s);
    }

    public long next() {
        for (;;) {
            long s = seed.get();
            long nexts = calculateNext(s);
            if (seed.compareAndSet(s, nexts))
                return s;
        }
    }

    private final AtomicLong seed;

    private long calculateNext(long s) {
        long t = (s % 127773) * 16807 - (s / 127773) * 2836;
        return (t > 0) ? t : t + 0x7fffffff;
    }
}
