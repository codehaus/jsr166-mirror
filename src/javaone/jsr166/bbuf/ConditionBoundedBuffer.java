package jsr166.bbuf;

import java.util.concurrent.locks.*;

public final class ConditionBoundedBuffer<E> implements BoundedBuffer<E> {

    public ConditionBoundedBuffer(int capacity) {
        this.capacity = capacity;
        this.items = (E[]) new Object[capacity];
    }

    public void put(E element) throws InterruptedException {
        lock.lock();
        try {
            while (count == capacity)
                notFull.await();
            items[putIndex++] = element;
            if (putIndex == capacity) putIndex = 0;
            ++count;
            notEmpty.signal();
        }
        finally {
            lock.unlock();
        }
    }

    public E take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0)
                notEmpty.await();
            E element = items[takeIndex++];
            if (takeIndex == capacity) takeIndex = 0;
            --count;
            notFull.signal();
            return element;
        }
        finally {
            lock.unlock();
        }
    }

    private int putIndex = 0;
    private int takeIndex = 0;
    private int count = 0;

    private final E[] items;
    private final int capacity;
    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
}