package jsr166.util.concurrent;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public interface GuardedAction extends GuardedCondition {
    void when(Runnable action) throws InterruptedException;
    <V> V when(Callable<V> action) throws InterruptedException;
}
