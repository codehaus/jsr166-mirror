package jsr166.pools;

import java.util.*;
import java.util.concurrent.*;

public final class ResourcePoolUsingSemaphore<E> implements ResourcePool<E> {

    public ResourcePoolUsingSemaphore(Set<E> resources) {
        for (E resource : resources)
            queue.add(resource);
        available = new Semaphore(queue.size()); // potentially O(n)
    }

    public E getItem() throws InterruptedException {
        available.acquire();
        return nextAvailable();
    }

    public void returnItem(E element) {
        if (unmark(element))
            available.release();
    }

    private E nextAvailable() {
        synchronized (queue) {
            return queue.remove();
        }
    }

    private boolean unmark(E element) {
        synchronized (queue) {
            queue.add(element);
            return true;
        }
    }

    private final Queue<E> queue = new LinkedList<E>();
    private final Semaphore available;
}