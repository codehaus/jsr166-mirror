package jsr166.util.concurrent;

import java.util.concurrent.locks.*;

public interface GuardedCondition extends Condition {
    boolean trySignal();
    boolean trySignalAll();
}
