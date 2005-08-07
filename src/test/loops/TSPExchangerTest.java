/*
 * Written by Bill Scherer and Doug Lea with assistance from members
 * of JCP JSR-166 Expert Group and released to the public domain. Use,
 * modify, and redistribute this code in any way without
 * acknowledgement.
 */

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

public class TSPExchangerTest {
    // Set SLS true to use as default the settings in Scherer, Lea, and
    // Scott paper. Otherwise smaller values are used to speed up testing
    static final boolean SLS = false;

    static final int DEFAULT_THREADS      = SLS?    32:     8;
    static final int DEFAULT_CITIES       = SLS?   100:    50;
    static final int DEFAULT_POPULATION   = SLS?  1000:   500;
    static final int DEFAULT_BREEDERS     = SLS?   200:   100;
    static final int DEFAULT_GENERATIONS  = SLS? 20000: 10000;


    public static void main(String[] args) throws Exception {
        int maxThreads = DEFAULT_THREADS;
        int nCities = DEFAULT_CITIES;
        int pSize = DEFAULT_POPULATION;
        int nBreeders = DEFAULT_BREEDERS;
        int numGenerations = DEFAULT_GENERATIONS;

        // Parse and check args
        int argc = 0;
        try {
            while (argc < args.length) {
                String option = args[argc++];
		if (option.equals("-b"))
		    nBreeders = Integer.parseInt(args[argc]);
                else if (option.equals("-c"))
                    nCities = Integer.parseInt(args[argc]);
                else if (option.equals("-g"))
                    numGenerations = Integer.parseInt(args[argc]);
		else if (option.equals("-p"))
		    pSize = Integer.parseInt(args[argc]);
                else 
                    maxThreads = Integer.parseInt(option);
                argc++;
            }
        }
        catch (NumberFormatException e) {
            reportUsageErrorAndDie();
            System.exit(0);
        }
        catch (Exception e) {
            reportUsageErrorAndDie();
        }

	// Display runtime parameters
	System.out.print("TSPExchangerTest -b " + nBreeders);
	System.out.print(" -c " + nCities);
	System.out.print(" -g " + numGenerations);
	System.out.print(" -p " + pSize);
	System.out.print(" max threads " + maxThreads);
	System.out.println();

        // warmup
        System.out.print("Threads: " + 2 + "\t");
        oneRun(2,
               nCities,
               pSize,
               nBreeders,
               numGenerations);
        Thread.sleep(100);

        int k = 4;
        for (int i = 2; i <= maxThreads;) {
            System.out.print("Threads: " + i + "\t");
            oneRun(i,
                   nCities,
                   pSize,
                   nBreeders,
                   numGenerations);
            Thread.sleep(100);
            if (i == k) {
                k = i << 1;
                i = i + (i >>> 1);
            } 
            else 
                i = k;
        }
    }

    private static void reportUsageErrorAndDie() {
        System.out.print("usage: TSPExchangerTest [-b #breeders] [-c #cities]");
	System.out.println(" [-g #generations]");
        System.out.println(" [-p population size] [ #threads]");
        System.exit(0);
    }

    static void oneRun(int nThreads,
                       int nCities,
                       int pSize,
                       int nBreeders,
                       int numGenerations)
        throws Exception {
        CyclicBarrier runBarrier = new CyclicBarrier(nThreads + 1);
	Population p = new Population(nCities, pSize, nBreeders, nThreads,
                                      numGenerations, runBarrier);

	// Run the test
        long startTime = System.currentTimeMillis();
	runBarrier.await(); // start 'em off
	runBarrier.await(); // wait 'til they're done
        long stopTime = System.currentTimeMillis();
        long elapsed = stopTime - startTime;
        long rate = (numGenerations * 1000) / elapsed;
        double secs = (double)elapsed / 1000.0;

	// Display results
        System.out.print(LoopHelpers.rightJustify((int)p.bestFitness()) + 
                         " fitness");
        System.out.print(LoopHelpers.rightJustify(rate) + " gen/s \t");
        System.out.print(secs + "s elapsed");
        System.out.println();
    }

    static final class Population {
        final Chromosome[] individuals;
        final Exchanger<Chromosome> x;
        final CitySet cities;
        final int[] dyers;
        final int[] breeders;
        final CyclicBarrier generationBarrier;
        final Thread[] threads;
        final boolean[] doneMating;
        final ReentrantLock matingBarrierLock;
        final Condition matingBarrier;
        final LoopHelpers.SimpleRandom[] rngs;
        final int nThreads;
        volatile int matingBarrierCount;

        // action to run between each generation
        class BarrierAction implements Runnable {
            public void run() {
                prepareToBreed();
                resetMatingBarrier();
            }
        }

        Population(int nCities, 
                   int pSize, 
                   int nBreeders, 
                   int nThreads, 
                   int nGen, 
                   CyclicBarrier runBarrier) {
            this.nThreads = nThreads;
            // rngs[nThreads] is for global actions; others are per-thread
            this.rngs = new LoopHelpers.SimpleRandom[nThreads+1];
            for (int i = 0; i < rngs.length; ++i) 
                rngs[i] = new LoopHelpers.SimpleRandom();
            this.cities = new CitySet(nCities, rngs[nThreads]);
            this.individuals = new Chromosome[pSize];
            for (int i = 0; i < individuals.length; i++)
                individuals[i] = new Chromosome(cities, nCities, 
                                                rngs[nThreads]);
            this.doneMating = new boolean[nThreads];
            this.dyers = new int[nBreeders];
            this.breeders = new int[nBreeders];

            this.x = new Exchanger();

            this.matingBarrierLock = new ReentrantLock();
            this.matingBarrier = matingBarrierLock.newCondition();

            BarrierAction ba = new BarrierAction();
            this.generationBarrier = new CyclicBarrier(nThreads, ba);
            ba.run(); // prepare for first generation

            this.threads = new Thread[nThreads];
            for (int i = 0; i < nThreads; i++) {
                Runner r = new Runner(i, this, runBarrier, nGen);
                threads[i] = new Thread(r);
                threads[i].start(); 
            }
        }
        
        double averageFitness() {
            double total = 0;
            for (int i = 0; i < individuals.length; i++)
                total += individuals[i].fitness;
            return total/(double)individuals.length;
        }

        double bestFitness() {
            double best = individuals[0].fitness;
            for (int i = 0; i < individuals.length; i++)
                if (individuals[i].fitness < best)
                    best = individuals[i].fitness;
            return best;
        }

        void resetMatingBarrier() {
            matingBarrierCount = nThreads - 1;
        }

        void awaitMatingBarrier(int tid) {
            doneMating[tid] = true; // heuristically set before lock
            matingBarrierLock.lock();
            try {
                int m = matingBarrierCount--;
                if (m < 1) {
                    for (int i = 0; i < doneMating.length; ++i) 
                        doneMating[i] = false;
                    Thread.interrupted(); // clear
                    matingBarrier.signalAll();
                } else {
                    doneMating[tid] = true;
                    if (m == 1 && nThreads > 2) {
                        for (int j = 0; j < doneMating.length; ++j) {
                            if (!doneMating[j]) {
                                threads[j].interrupt();
                                break;
                            }
                        }
                    }
                    try {
                        do { 
                            matingBarrier.await(); 
                        } while (matingBarrierCount >= 0);
                    } catch(InterruptedException ie) {}
                }
            } finally {
                matingBarrierLock.unlock();
            }
        }

        void prepareToBreed() {

            // Calculate statistics
            double totalFitness = 0;
            double worstFitness = 0;
            double bestFitness = individuals[0].fitness;

            for (int i = 0; i < individuals.length; i++) {
                totalFitness += individuals[i].fitness;
                if (individuals[i].fitness > worstFitness)
                    worstFitness = individuals[i].fitness;
                if (individuals[i].fitness < bestFitness)
                    bestFitness = individuals[i].fitness;
            }
            
            double[] lifeNorm = new double[individuals.length];
            double lifeNormTotal = 0;
            double[] deathNorm = new double[individuals.length];
            double deathNormTotal = 0;
            for (int i = 0; i < individuals.length; i++) {
                deathNorm[i] = (individuals[i].fitness - bestFitness) 
                    / (worstFitness - bestFitness + 1) + .05;
                deathNorm[i] = (deathNorm[i] * deathNorm[i]);
                lifeNorm[i] = 1.0 - deathNorm[i];
                lifeNormTotal += lifeNorm[i];
                deathNormTotal += deathNorm[i];
            }
            
            double deathScale = deathNormTotal / (double)0x7FFFFFFF;
            double lifeScale = lifeNormTotal / (double)0x7FFFFFFF;
            
            int nSub = breeders.length / nThreads;
            LoopHelpers.SimpleRandom random = rngs[nThreads];

            // Select breeders (need to be distinct)
            for (int i = 0; i < nSub; i++) {
                boolean newBreeder;
                int lucky;
                do {
                    newBreeder = true;
                    double choice = lifeScale * (double)random.next();
                    for (lucky = 0; lucky < individuals.length; lucky++) {
                        choice -= lifeNorm[lucky];
                        if (choice <= 0)
                            break;
                    }
                    for (int j = 0; j < i; j++)
                        if (breeders[j] == lucky)
                            newBreeder = false;
                } while (!newBreeder);
                breeders[i] = lucky;
            }
            
            // Select dead guys (need to be distinct)
            for (int i = 0; i < nSub; i++) {
                boolean newDead;
                int victim;
                do {
                    newDead = true;
                    double choice = deathScale * (double)random.next();
                    for (victim = 0; victim < individuals.length; victim++) {
                        choice -= deathNorm[victim];
                        if (choice <= 0)
                            break;
                    }
                    for (int j = 0; j < i; j++)
                        if (dyers[j] == victim)
                            newDead = false;
                } while (!newDead);
                dyers[i] = victim;
            }

        }

        
        void nextGeneration(int tid, int matings)
            throws InterruptedException, BrokenBarrierException {

            int firstChild = ((individuals.length * tid) / nThreads);
            int lastChild = ((individuals.length * (tid + 1)) / nThreads); 
            int nChildren =  lastChild - firstChild;
	    
            int firstSub = ((breeders.length * tid) / nThreads);
            int lastSub = ((breeders.length * (tid + 1)) / nThreads); 
            int nSub = lastSub - firstSub;
	    
            Chromosome[] children = new Chromosome[nChildren];

            LoopHelpers.SimpleRandom random = rngs[tid];

            for (int i = 0; i < nSub; i++) {
                Chromosome parent = individuals[breeders[firstSub + i]];
                Chromosome offspring = new Chromosome(parent);
                int k = 0;
                while (k < matings && matingBarrierCount > 0) {
                    try {
                        Chromosome other = x.exchange(offspring);
                        offspring = offspring.reproduceWith(other, random);
                        ++k;
                    } catch (InterruptedException to) {
                        break;
                    }
                }
                if (k != 0)
                    children[i] = offspring;
                else {
                    // No peers, so we mate with ourselves
                    for ( ; i < nSub - 1; i += 2) {
                        int cur = firstSub + i;
                        Chromosome bro = individuals[breeders[cur]];
                        Chromosome sis = individuals[breeders[cur + 1]];
                        
                        children[i] = bro.breedWith(sis, matings, random);
                        children[i+1] = sis.breedWith(bro, matings, random);
                    }
                    
                    // Not even a sibling, so we go to asexual reproduction
                    if (i < nSub)
                        children[i] = individuals[breeders[firstSub + 1]];
                    break;
                }

            }

            awaitMatingBarrier(tid);

            // Kill off dead guys
            for (int i = 0; i < nSub; i++) {
                individuals[dyers[firstSub + 1]] = children[i];
            }

            generationBarrier.await();
        }
    }

    static final class Chromosome {
        private final CitySet cities; 
        private final int[] alleles;  
        private final int length;     
        public  double fitness; // immutable after publication
        
        // Basic constructor - gets randomized genetic code
        Chromosome(CitySet cities, int length, 
                   LoopHelpers.SimpleRandom random) {
            this.length = length;
            this.cities = cities;
            // Initialize alleles to a random shuffle
            alleles = new int[length];
            for (int i = 0; i < length; i++)
                alleles[i] = i;
            for (int i = length - 1; i > 0; i--) {
                int tmp = alleles[i];
                int idx = random.next() % length;
                alleles[i] = alleles[idx];
                alleles[idx] = tmp;
            }
            recalcFitness();
        }
        
        // Copy constructor - clones parent's genetic code
        Chromosome(Chromosome clone) {
            length = clone.length;
            cities = clone.cities;
            fitness = clone.fitness;
            alleles = new int[length];
            System.arraycopy(clone.alleles, 0, alleles, 0, length);
        }
        
        int getAllele(int offset) {
            return alleles[offset % length];
        }
        void setAllele(int offset, int v) {
            alleles[offset % length] = v;
        }
        
        void recalcFitness() {
            fitness = cities.distanceBetween(alleles[0], alleles[length-1]);
            for (int i = 1; i < length; i++) {
                fitness += cities.distanceBetween(alleles[i-1], alleles[i]);
            }
        }
        
        Chromosome breedWith(Chromosome partner, int n, 
                             LoopHelpers.SimpleRandom random) {
            Chromosome offspring = new Chromosome(this);
            for (int i = 0; i < n; i++)
                offspring = offspring.reproduceWith(partner, random);
            return offspring;
        }
        
        Chromosome reproduceWith(Chromosome other, 
                                 LoopHelpers.SimpleRandom random) {
            Chromosome child = new Chromosome(this);
            int coStart = random.next() % length;
            int coLen = 3;
            while (1 == (random.next() & 1) && (coLen < length))
                coLen++;
            int cPos, pPos;
            
            int join = other.getAllele(coStart);
            child.alleles[0] = join;
            
            for (pPos = 0; alleles[pPos] != join; pPos++)
                ;
            
            for (cPos = 1; cPos < coLen; cPos++)
                child.setAllele(cPos, other.getAllele(coStart + cPos));
            
            for (int i = 0; i < length; i++) {
                boolean found = false;
                int allele = getAllele(pPos++);
                for (int j = 0; j < coLen; j++) {
                    if (found = (child.getAllele(j) == allele))
                        break;
                }
                if (!found) 
                    child.setAllele(cPos++, allele);
            }
            
            child.recalcFitness();
            return child;
        }
        
    }
    /**
     * A collection of (x,y) points that represent cities
     */
    static final class CitySet {
        final int XMAX = 1000;
        final int YMAX = 1000;
        final int length;
        final int xPts[];
        final int yPts[];
        final double distances[];
        
        CitySet(int n, LoopHelpers.SimpleRandom random) {
            this.length = n;
            xPts = new int[n];
            yPts = new int [n];
            for (int i = 0; i < n; i++) {
                xPts[i] = random.next() % XMAX;
                yPts[i] = random.next() % YMAX;
            }
            distances = new double[n * n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double dX = (double)(xPts[i] - xPts[j]);
                    double dY = (double)(yPts[i] - yPts[j]);
                    distances[i + j * n] = Math.hypot(dX, dY);
                }
            }
        }
        
        // Retrieve the cached distance between a pair of cities
        double distanceBetween(int idx1, int idx2) {
            return distances[idx1 + idx2 * length];
        }
    }

    static final class Runner implements Runnable {
        final Population pop;
        final CyclicBarrier b;
        final int nGen;
        final int tid;
        static final boolean verbose = false;
        
        Runner(int tid, Population p, CyclicBarrier b, int n) {
            this.tid = tid;
            this.pop = p;
            this.b = b;
            this.nGen = n;
        }
        
        public void run() {
            try {
                b.await();
                for (int i = 0; i < nGen; i++) {
                    if (verbose && 0 == tid && 0 == i % 1000) {
                        System.out.print("Gen " + i + " fitness:");
                        System.out.print(" best=" + (int)pop.bestFitness());
                        System.out.println("; avg=" + (int)pop.averageFitness());
                    }
                    int matings = (((nGen - i) * 2) / (nGen)) + 1;
                    pop.nextGeneration(tid, matings);
                }
                b.await();
            } 
            catch (InterruptedException e) {
                e.printStackTrace(System.out);
                System.exit(0);
            }
            catch (BrokenBarrierException e) {
                e.printStackTrace(System.out);
                System.exit(0);
            }
        }
    }
}
