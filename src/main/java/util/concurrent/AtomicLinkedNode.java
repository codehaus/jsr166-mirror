/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain. Use, modify, and
 * redistribute this code in any way without acknowledgement.
 */

package java.util.concurrent;
import java.util.concurrent.atomic.*;

/**
 * A linked list node supporting atomic operations on both item and
 * next fields, Used by non-blocking linked-list based classes.
 */

final class AtomicLinkedNode {
    private volatile Object item;
    private volatile AtomicLinkedNode next;

    private final static AtomicReferenceFieldUpdater<AtomicLinkedNode, AtomicLinkedNode> nextUpdater =
    new AtomicReferenceFieldUpdater<AtomicLinkedNode, AtomicLinkedNode>(new AtomicLinkedNode[0], new AtomicLinkedNode[0], "next");
    private final static AtomicReferenceFieldUpdater<AtomicLinkedNode, Object> itemUpdater
     = new AtomicReferenceFieldUpdater<AtomicLinkedNode, Object>(new AtomicLinkedNode[0], new Object[0], "item");

    AtomicLinkedNode(Object x) { item = x; }

    AtomicLinkedNode(Object x, AtomicLinkedNode n) { item = x; next = n; }

    Object getItem() {
        return item;
    }

    boolean casItem(Object cmp, Object val) {
        return itemUpdater.compareAndSet(this, cmp, val);
    }

    void setItem(Object val) {
        itemUpdater.set(this, val);
    }

    AtomicLinkedNode getNext() {
        return next;
    }

    boolean casNext(AtomicLinkedNode cmp, AtomicLinkedNode val) {
        return nextUpdater.compareAndSet(this, cmp, val);
    }

    void setNext(AtomicLinkedNode val) {
        nextUpdater.set(this, val);
    }

}
