package jsr166.bbuf;

import java.util.concurrent.locks.*;

public interface BoundedBuffer<E> {
    void put(E element) throws InterruptedException;
    E take() throws InterruptedException;
}