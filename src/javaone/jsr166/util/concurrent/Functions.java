package jsr166.util.concurrent;

import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class Functions {

    public static <A, V, X extends Exception>
    CachedFunction<A, V, X> newCachedFunction(Executor executor, Function<A, V, X> function, Class<X> xtype) {
        return new CachedFunction<A, V, X>(executor, function, xtype);
    }

    public static <A, V>
    CachedFunction<A, V, RuntimeException> newCachedFunction(
            Executor executor,  Function<A, V, RuntimeException> function) {
        return new CachedFunction<A, V, RuntimeException>(executor, function, RuntimeException.class);
    }

    public static <A, V, X extends Exception>
    CompletionFunction<A, V, X> newCompletionFunction(final Executor executor,
                                                      final Function<A, V, X> function,
                                                      final Class<X> xtype) {
        return new CompletionFunction<A, V, X>() {

            public V call(A arg) throws X {
                return cachedFunction.call(arg);
            }

            public void call(final Collection<A> args,
                             long timeout, TimeUnit unit,
                             CompletionHandler<V> handler) {
                final CompletionService<V> completion =
                    new ExecutorCompletionService<V>(executor);
                int n = 0;
                for (final A arg : args) {
                    completion.submit(new Callable<V>() {
                        public V call() throws X { return cachedFunction.call(arg); }
                    });
                    ++n;
                }

                for (int i = 0; i < n; ++i)
                    try {
                        Future<V> future = completion.poll(timeout, unit);
                        if (future == null) return;
                        handler.handle(future.get());
                    }
                    catch (InterruptedException e) { return; }
                    catch (ExecutionException e) { continue; }
            }

            private final Function<A, V, X> cachedFunction =
                newCachedFunction(executor, function, xtype);
        };
    }

    private static class CachedFunction<A, V, X extends Exception>
            implements Function<A, V, X> {

        public V call(final A arg) throws X {
            try {
                Future<V> future = cache.get(arg);
                if (future != null) return future.get();

                Callable<V> functionCall = new Callable<V>() {
                    public V call() throws X { return function.call(arg); }
                };
                FutureTask<V> task = new FutureTask<V>(functionCall);
                future = cache.putIfAbsent(arg, task);
                if (future == null) {
                    future = task;
                    executor.execute(task);
                }
                return future.get();
            }
            catch (InterruptedException e) {
                return null;
            }
            catch (ExecutionException e) {
                Throwable t = e.getCause();
                if (t instanceof RuntimeException) throw (RuntimeException) t;
                assert xtype.isInstance(t) : "expected "+xtype.getName()+", got"+t;
                // unchecked cast
                throw (X) t;
            }
        }

        private CachedFunction(Executor executor, Function<A, V, X> function, Class<X> xtype) {
            this.executor = executor;
            this.function = function;
            this.xtype = xtype;
        }
        private final Executor executor;
        private final Function<A, V, X> function;
        private final Class<X> xtype;
        private final ConcurrentMap<A, Future<V>> cache =
            new ConcurrentHashMap<A, Future<V>>();
    }
}
