package jsr166.random;

import java.util.concurrent.atomic.*;

public class PseudoRandomUsingEncapsulatedSynch implements PseudoRandom {

    public PseudoRandomUsingEncapsulatedSynch(int s) {
        seed = s;
    }

    public int nextInt(int n) {
        synchronized (lock) {
            int s = seed;
            seed = calculateNext(seed);
            return s % n;
        }
    }

    private int seed;
    private final Object lock = new Object();

    private int calculateNext(int s) {
        return Util.calculateNext(s);
    }
}
