package jsr166.pools;

import java.util.concurrent.locks.*;

public interface ResourcePool<E> {
    E getItem() throws InterruptedException;
    void returnItem(E x);
}