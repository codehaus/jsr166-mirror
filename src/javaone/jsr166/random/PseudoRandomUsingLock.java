package jsr166.random;

import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

public class PseudoRandomUsingLock implements PseudoRandom {

    public PseudoRandomUsingLock(int s) {
        seed = s;
    }

    public int nextInt(int n) {
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
    private final Lock lock = new ReentrantLock(true);

    private int calculateNext(int s) {
        return Util.calculateNext(s);
    }
}
