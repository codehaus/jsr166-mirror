package jsr166.bbuf;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import jsr166.util.concurrent.*;

public final class BoundedBufferUsingCondition<E> implements BoundedBuffer<E> {

    public BoundedBufferUsingCondition(int capacity) {
        this.items = (E[]) new Object[capacity];
    }

    public void put(final E element) throws InterruptedException {
        notFull.when(new Runnable() {
            public void run() {
                items[putIndex++] = element;
                if (putIndex == items.length) putIndex = 0;
                ++size;
                notEmpty.signal();
            }
        });
    }

    public E take() throws InterruptedException {
        return notEmpty.when(new Callable<E>() {
            public E call() {
                E element = items[takeIndex++];
                if (takeIndex == items.length) takeIndex = 0;
                --size;
                notFull.signal();
                return element;
            }
        });
    }

    private int putIndex = 0;
    private int takeIndex = 0;
    private int size = 0;

    private final E[] items;
    private final Lock lock = new ReentrantLock(true);
    private final GuardedAction notFull = Conditions.newGuardedAction(
        lock, new Conditions.Guard() {
            public boolean call() { return size < items.length; }
        });
    private final GuardedAction notEmpty = Conditions.newGuardedAction(
        lock, new Conditions.Guard() {
            public boolean call() { return size > 0; }
        });
}