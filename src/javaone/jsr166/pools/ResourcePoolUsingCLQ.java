package jsr166.pools;

import java.util.*;
import java.util.concurrent.*;

public final class ResourcePoolUsingCLQ<E> implements ResourcePool<E> {

    public ResourcePoolUsingCLQ(Set<E> resources) {
        for (E resource : resources)
            queue.add(resource);
    }

    public E getItem() throws InterruptedException {
        return nextAvailable();
    }

    public void returnItem(E element) {
        unmark(element);
    }

    private E nextAvailable() {
        E element;
        while ((element = queue.poll()) == null) // spin
            Thread.yield(); // doesn't seem to make much difference
        return element;
    }

    private boolean unmark(E element) {
        queue.add(element);
        return true;
    }

    private final Queue<E> queue = new ConcurrentLinkedQueue<E>();
}