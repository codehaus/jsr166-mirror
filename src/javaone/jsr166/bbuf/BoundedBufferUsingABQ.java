package jsr166.bbuf;

import EDU.oswego.cs.dl.util.concurrent.*;
import java.util.concurrent.*;

public final class BoundedBufferUsingABQ<E> implements BoundedBuffer<E> {

    public BoundedBufferUsingABQ(int capacity) {
        this.abq = new ArrayBlockingQueue<E>(capacity);
    }

    public void put(E element) throws InterruptedException {
        abq.put(element);
    }

    public E take() throws InterruptedException {
        return abq.take();
    }

    private final ArrayBlockingQueue<E> abq;
}