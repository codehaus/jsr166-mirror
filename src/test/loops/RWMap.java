/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;


/**
 * This is an incomplete implementation of a wrapper class
 * that places read-write locks around unsynchronized Maps.
 * Exists as a sample input for MapLoops test.
 */
public class RWMap implements ConcurrentMap {
    private final Map m;
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    public RWMap(Map m) {
        if (m == null)
            throw new NullPointerException();
        this.m = m;
    }

    public RWMap() {
        this(new TreeMap()); // use TreeMap by default
        //        this(new IdentityHashMap());
    }

    public int size() {
        ReentrantReadWriteLock.ReadLock l = rwl.readLock();
        l.lock();
        try { return m.size(); }
        finally { l.unlock(); }
    }

    public boolean isEmpty() {
        ReentrantReadWriteLock.ReadLock l = rwl.readLock();
        l.lock();
        try { return m.isEmpty(); }
        finally { l.unlock(); }
    }

    public Object get(Object key) {
        ReentrantReadWriteLock.ReadLock l = rwl.readLock();
        l.lock();
        try { return m.get(key); }
        finally { l.unlock(); }
    }

    public boolean containsKey(Object key) {
        ReentrantReadWriteLock.ReadLock l = rwl.readLock();
        l.lock();
        try { return m.containsKey(key); }
        finally { l.unlock(); }
    }

    public boolean containsValue(Object value) {
        ReentrantReadWriteLock.ReadLock l = rwl.readLock();
        l.lock();
        try { return m.containsValue(value); }
        finally { l.unlock(); }
    }

    public Set keySet() { // Not implemented
        return m.keySet();
    }

    public Set entrySet() { // Not implemented
        return m.entrySet();
    }

    public Collection values() { // Not implemented
        return m.values();
    }

    public boolean equals(Object o) {
        ReentrantReadWriteLock.ReadLock l = rwl.readLock();
        l.lock();
        try { return m.equals(o); }
        finally { l.unlock(); }
    }

    public int hashCode() {
        ReentrantReadWriteLock.ReadLock l = rwl.readLock();
        l.lock();
        try { return m.hashCode(); }
        finally { l.unlock(); }
    }

    public String toString() {
        ReentrantReadWriteLock.ReadLock l = rwl.readLock();
        l.lock();
        try { return m.toString(); }
        finally { l.unlock(); }
    }

    public Object put(Object key, Object value) {
        ReentrantReadWriteLock.WriteLock l = rwl.writeLock();
        l.lock();
        try { return m.put(key, value); }
        finally { l.unlock(); }
    }

    public Object putIfAbsent(Object key, Object value) {
        ReentrantReadWriteLock.WriteLock l = rwl.writeLock();
        l.lock();
        try {
            Object v = m.get(key);
            return v == null?  m.put(key, value) : v;
        }
        finally { l.unlock(); }
    }

    public boolean replace(Object key, Object oldValue, Object newValue) {
        ReentrantReadWriteLock.WriteLock l = rwl.writeLock();
        l.lock();
        try {
            if (m.get(key).equals(oldValue)) {
                m.put(key, newValue);
                return true;
            }
            return false;
        }
        finally { l.unlock(); }
    }

    public Object replace(Object key, Object newValue) {
        ReentrantReadWriteLock.WriteLock l = rwl.writeLock();
        l.lock();
        try {
            if (m.containsKey(key))
                return m.put(key, newValue);
            return null;
        }
        finally { l.unlock(); }
    }

    public Object remove(Object key) {
        ReentrantReadWriteLock.WriteLock l = rwl.writeLock();
        l.lock();
        try { return m.remove(key); }
        finally { l.unlock(); }
    }

    public boolean remove(Object key, Object value) {
        ReentrantReadWriteLock.WriteLock l = rwl.writeLock();
        l.lock();
        try {
            if (m.get(key).equals(value)) {
                m.remove(key);
                return true;
            }
            return false;
        }
        finally { l.unlock(); }
    }

    public void putAll(Map map) {
        ReentrantReadWriteLock.WriteLock l = rwl.writeLock();
        l.lock();
        try { m.putAll(map); }
        finally { l.unlock(); }
    }

    public void clear() {
        ReentrantReadWriteLock.WriteLock l = rwl.writeLock();
        l.lock();
        try { m.clear(); }
        finally { l.unlock(); }
    }

}
