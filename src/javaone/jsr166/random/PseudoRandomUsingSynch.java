package jsr166.random;

import java.util.concurrent.atomic.*;

public class PseudoRandomUsingSynch implements PseudoRandom {

    public PseudoRandomUsingSynch(int s) {
        seed = s;
    }

    public synchronized int nextInt(int n) {
        int s = seed;
        seed = calculateNext(seed);
        return s % n;
    }

    private int seed;

    private int calculateNext(int s) {
        return Util.calculateNext(s);
    }
}
