package jsr166.pools;

import java.util.*;
import java.util.concurrent.*;


/**
 * Compares performance of ResourcePool implementations.
 */
public final class TestResourcePool {

    public static void main(String[] args) {
        new TestResourcePool().run();
    }

    enum Rank { ACE, DEUCE, TREY, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, JACK, QUEEN, KING }

    enum Suit { SPADE, HEART, DIAMOND, CLUB }

    static class Card implements Comparable<Card> {
        public final Rank rank;
        public final Suit suit;
        public int compareTo(Card that) {
            int r = this.rank.compareTo(that.rank);
            return r != 0 ? r : this.suit.compareTo(that.suit);
        }
        Card(Rank rank, Suit suit) { this.rank = rank; this.suit = suit; }
    }

    static class Shuffler {
        public Shuffler(int id) { this.id = id; }
        public void shuffle(List<Card> deck) {
            Collections.shuffle(deck);
            ++useCount;
        }
        public int id() { return id; }
        public int useCount() { return useCount; }
        public void clearUseCount() { useCount = 0; }
        private int useCount = 0;
        private final int id;
    }

    enum Mode {
        SEMAPHORE {
            ResourcePool<Shuffler> newResourcePool(Set<Shuffler> shufflers) {
                return new ResourcePoolUsingSemaphore<Shuffler>(shufflers);
            }
        },
        CLQ {
            ResourcePool<Shuffler> newResourcePool(Set<Shuffler> shufflers) {
                return new ResourcePoolUsingCLQ<Shuffler>(shufflers);
            }
        },
    ;
        abstract ResourcePool<Shuffler> newResourcePool(Set<Shuffler> resources);
    }

    final int count = 200;
    final int NTRIALS = 200;
    final int NRES = 2;
    final int NTASKS = 5;

    void run() {

        Set<Shuffler> shufflers = new HashSet<Shuffler>();
        for (int r = 0; r < NRES; ++r) shufflers.add(new Shuffler(r));

        System.out.println("ntrials="+NTRIALS);
        for (Mode mode : Mode.values()) {
            long elapsed = 0;
            int totalUses = 0;
            for (int i = 0; i < NTRIALS; ++i) {
                elapsed += trial(count, shufflers, mode);
                int uses = 0;
                for (Shuffler shuffler : shufflers) {
                    uses += shuffler.useCount();
                    shuffler.clearUseCount();
                }
                totalUses += uses;
            }

            long avg = elapsed / (NTRIALS * count);
            System.out.println(""+mode+"\t"+avg+"\t"+(totalUses/(NTASKS*NTRIALS)));
        }
    }


    private long trial(final int count, Set<Shuffler> shufflers, Mode mode) {

        final ResourcePool<Shuffler> pool = mode.newResourcePool(shufflers);

        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            startSignal = new CountDownLatch(1);
            doneSignal = new CountDownLatch(NTASKS);

            for (int t = 0; t < NTASKS; ++t)
                executor.execute(new ShufflerTask(t, count, pool));

            long start = System.nanoTime();

            startSignal.countDown();
            doneSignal.await();

            return System.nanoTime() - start;
        }
        catch (InterruptedException e) {
            return 0; // no useful timing
        }
        finally {
            executor.shutdown();
        }
    }

    volatile CountDownLatch startSignal;
    volatile CountDownLatch doneSignal;

    class ShufflerTask implements Runnable {
        ShufflerTask(int id, int count, ResourcePool<Shuffler> pool) {
            this.id = id;
            this.count = count;
            this.pool = pool;
        }

        public void run() {
            for (Suit suit : Suit.values())
                for (Rank rank : Rank.values())
                    deck.add(new Card(rank, suit));

            try {
                startSignal.await();
                for (int c = 0; c < count; ++c) {
                    Shuffler shuffler = pool.getItem();
                    shuffler.shuffle(deck);
                    //System.out.println("t="+id+", cnt="+c+", s="+shuffler.id()+", uses="+shuffler.useCount());
                    pool.returnItem(shuffler);
                }
                doneSignal.countDown();
            }
            catch (InterruptedException e) {} // XXX ignored
        }

        private final int id;
        private final int count;
        private final ResourcePool<Shuffler> pool;
        private final List<Card> deck = new ArrayList<Card>();
    }
}