/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package jsr166.selector;

import java.io.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

// Useful statics
import static java.nio.channels.SelectionKey.*;
import static java.util.concurrent.Executors.callable;

/**
 * A {@link CompletionService} that uses a {@link Selector}
 * to select tasks for execution by readiness for I/O operations.
 *
 * @author Tim Peierls
 */
public class SelectorCompletionService<V> implements CompletionService<V> {

    // Selectable channel I/O operations

    enum Op {
        ACCEPT (OP_ACCEPT ),
        CONNECT(OP_CONNECT),
        READ   (OP_READ   ),
        WRITE  (OP_WRITE  );

        int bit() { return bit; }

        Op(int bit) {
            this.bit = bit;
        }

        private final int bit;
    }

    static int ops2bits(EnumSet<Op> ops) {
        int bits = 0;
        for (Op op : ops) bits |= op.bit();
        return bits;
    }

    static EnumSet<Op> bits2ops(int bits) {
        EnumSet<Op> ops = EnumSet.noneOf(Op.class);
        for (Op op : Op.values()) if ((bits & op.bit()) != 0) ops.add(op);
        return ops;
    }


    // Selectable tasks

    public interface SelectableTask<V> {
        V process(Future<V> future, SelectableChannel channel) throws IOException;
    }

    public static <V> Callable<V> accept(SelectableChannel channel, SelectableTask<V> task) {
        return new Task<V>(channel, task, Op.ACCEPT);
    }

    public static <V> Callable<V> connect(SelectableChannel channel, SelectableTask<V> task) {
        return new Task<V>(channel, task, Op.CONNECT);
    }

    public static <V> Callable<V> read(SelectableChannel channel, SelectableTask<V> task) {
        return new Task<V>(channel, task, Op.READ);
    }

    public static <V> Callable<V> write(SelectableChannel channel, SelectableTask<V> task) {
        return new Task<V>(channel, task, Op.WRITE);
    }

    private static class Task<V> implements Callable<V> {
        Task(SelectableChannel sc, SelectableTask<V> task, Op op) {
            this.sc = sc;
            this.task = task;
            this.op = op;
        }

        public V call() {
            throw new RejectedExecutionException("not a real callable");
        }

        final SelectableChannel sc;
        final SelectableTask<V> task;
        final Op op;
    }


    /**
     * Creates a SelectorCompletionService.
     */
    public SelectorCompletionService() throws IOException {
        this.selector = Selector.open();
    }

    public void shutdown() throws IOException {
        selector.close();
    }

    protected void finalize() throws Throwable {
        try {
            shutdown();
        } finally {
            super.finalize();
        }
    }

    private final Selector selector;


    /**
     * @throws RejectedExecutionException if task was not produced by
     * binding a selectable task and a channel together as an operation.
     */
    public Future<V> submit(Callable<V> task) {
        if (task instanceof Task) {
            Task<V> t = (Task<V>) task;
            return new FutureImpl<V>(t.sc, t.task, t.op, selector);
        }

        throw new RejectedExecutionException("can't execute non-selectable task");
    }

    /**
     * Rejects all runnable submissions.
     *
     * @throws RejectedExecutionException if task is not a selectable task
     */
    public Future<V> submit(Runnable task, V result) {
        throw new RejectedExecutionException("can't execute non-selectable task");
    }

    /**
     * @throws RejectedExecutionException if tasks cannot be selected
     */
    public Future<V> take() {
        // XXX add interrupted check?
        try {
            Future<V> result = pollSelectedKeys();
            while (result == null)
                if (selector.select() > 0)
                    result = pollSelectedKeys();
            return result;
        } catch (IOException e) {
            throw new RejectedExecutionException(e);
        }
    }

    /**
     * @throws RejectedExecutionException if tasks cannot be selected
     */
    public Future<V> poll() {
        try {
            Future<V> result = pollSelectedKeys();
            if (result == null && selector.selectNow() > 0)
                result = pollSelectedKeys();
            return result;
        } catch (IOException e) {
            throw new RejectedExecutionException(e);
        }
    }

    /**
     * @throws RejectedExecutionException if tasks cannot be selected
     */
    public Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 0)
            throw new IllegalArgumentException("negative timeout");

        if (unit == null)
            throw new NullPointerException("null unit");

        try {
            Future<V> result = pollSelectedKeys();
            if (result == null && selector.select(unit.toMillis(timeout)) > 0)
                result = pollSelectedKeys();
            return result;
        } catch (IOException e) {
            throw new RejectedExecutionException(e);
        }
    }


    private final Set<SelectionKey> selectedKeys = new LinkedHashSet<SelectionKey>();
    private EnumSet<Op> ops = EnumSet.noneOf(Op.class);
    private Map<Op, Future<V>> futures = null;

    private Future<V> pollSelectedKeys() {
        for (;;) {
            while (ops.isEmpty()) {
                SelectionKey key;

                synchronized (selectedKeys) {
                    processSelectedKeys();
                    if (selectedKeys.isEmpty()) return null;
                    Iterator<SelectionKey> it = selectedKeys.iterator();
                    key = it.next();
                    it.remove();
                }

                ops = bits2ops(key.readyOps());

                System.err.println("ops="+ops);

                synchronized (key) {
                    futures = (Map<Op, Future<V>>) key.attachment();
                }

                assert futures != null;
            }

            Iterator<Op> it = ops.iterator();
            Op op = it.next();
            it.remove();

            Future<V> future = futures.get(op);
            if (future != null) return future;
        }
    }

    private void processSelectedKeys() {
        // PRE: holds selectedKeys synch lock
        if (selectedKeys.isEmpty()) {
            while (true) {
                try {
                    for (Iterator it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                        SelectionKey key = (SelectionKey) it.next();
                        it.remove();
                        selectedKeys.add(key);
                    }
                    return;
                } catch (ConcurrentModificationException e) {
                    // retry
                }
            }
        }
    }


    private static class FutureImpl<V> implements Future<V> {

        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (lock) {
                if (isCancelled()) return false;

                cancelled = true;

                EnumSet<Op> interestOps = bits2ops(key.interestOps());
                boolean found = interestOps.remove(op);
                if (interestOps.isEmpty()) {
                    key.cancel();
                    System.err.println("canceled key for channel: "+channel);
                } else {
                    key.interestOps(ops2bits(interestOps));
                    System.err.println("reduced ops for channel "+channel+" to "+bits2ops(key.interestOps()));
                }

                return found;
            }
        }

        public boolean isCancelled() {
            synchronized (lock) {
                if (!cancelled && !key.isValid()) cancelled = true;
                return cancelled;
            }
        }

        public boolean isDone() {
            return isCancelled();
        }

        public V get() {
            try {
                return task.process(this, channel);
            } catch (IOException e) {
                this.cancel(true);
                throw new RejectedExecutionException("I/O exception", e);
            }
        }

        public V get(long timeout, TimeUnit unit) {
            return get();
        }

        FutureImpl(SelectableChannel channel, SelectableTask<V> task, Op op, Selector selector) {
            this.task = task;
            this.channel = channel;
            this.op = op;
            try {
                if (channel.isBlocking())
                    channel.configureBlocking(false);

                synchronized (lock) {
                    SelectionKey key = channel.keyFor(selector);

                    if (key == null)
                        key = channel.register(selector, op.bit());
                    else
                        key.interestOps(key.interestOps() | op.bit());

                    assert key != null;

                    this.key = key;

                    Map<Op, Future<V>> futures = (Map<Op, Future<V>>) key.attachment();
                    if (futures == null)
                        key.attach(futures = new LinkedHashMap<Op, Future<V>>());
                    futures.put(op, this);

                    System.err.println("registered channel "+channel+" with ops="+bits2ops(key.interestOps()));
                }
            } catch (IOException e) {
                this.cancel(false);
                throw new RejectedExecutionException("while registering", e);
            }
        }

        // initialized at construction, before there is a selector
        private final SelectableTask<V> task;
        private final SelectableChannel channel;
        private final SelectionKey      key;
        private final Op                op;
        private final Object            lock = new Object();

        private boolean cancelled = false;
    }
}
