package jsr166.random;

import java.util.concurrent.atomic.*;

public class PseudoRandomUsingAtomic implements PseudoRandom {

    public PseudoRandomUsingAtomic(int s) {
        seed = new AtomicInteger(s);
    }

    public int nextInt(int n) {
        for (;;) {
            int s = seed.get();
            int nexts = calculateNext(s);
            if (seed.compareAndSet(s, nexts))
                return s % n;
        }
    }

    private final AtomicInteger seed;

    private int calculateNext(int s) {
        return Util.calculateNext(s);
    }
}
