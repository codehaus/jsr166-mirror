package jsr166.bbuf;

import EDU.oswego.cs.dl.util.concurrent.*;

public final class BoundedBufferUsingCondVar<E> implements BoundedBuffer<E> {

    public BoundedBufferUsingCondVar(int capacity) {
        this.capacity = capacity;
        this.items = (E[]) new Object[capacity];
    }

    public void put(E element) throws InterruptedException {
        mutex.acquire();
        try {
            while (count == capacity)
                notFull.await();
            items[putIndex++] = element;
            if (putIndex == capacity) putIndex = 0;
            ++count;
            notEmpty.signal();
        }
        finally {
            mutex.release();
        }
    }

    public E take() throws InterruptedException {
        mutex.acquire();
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
            mutex.release();
        }
    }

    private int putIndex = 0;
    private int takeIndex = 0;
    private int count = 0;

    private final E[] items;
    private final int capacity;
    private final Mutex mutex = new Mutex();
    private final CondVar notFull = new CondVar(mutex);
    private final CondVar notEmpty = new CondVar(mutex);
}