package jsr166.util.concurrent;

public interface CompletionHandler<V> {
    void handle(V value);
}
