package jsr166.util.concurrent;

public interface Function<A, V, X extends Exception> {
    V call(A arg) throws X;
}
