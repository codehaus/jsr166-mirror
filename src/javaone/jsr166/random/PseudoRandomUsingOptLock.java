package jsr166.random;

import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

public class PseudoRandomUsingOptLock implements PseudoRandom {

    public PseudoRandomUsingOptLock(int s, boolean fair) {
        seed = s;
        lock = new ReentrantLock(fair);
    }

    public int nextInt(int n) {
        ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int s = seed;
            seed = calculateNext(seed);
            return s % n;
        }
        finally {
            lock.unlock();
        }
    }

    private int seed;
    private final ReentrantLock lock;

    private int calculateNext(int s) {
        return Util.calculateNext(s);
    }
}
