package jsr166.util.concurrent;

import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public interface CompletionFunction<A, V, X extends Exception> extends Function<A, V, X> {
    void call(Collection<A> args, long timeout, TimeUnit unit, CompletionHandler<V> handler);
}
