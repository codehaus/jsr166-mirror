package jsr166.random;

public interface PseudoRandom {
    /**
     * Return pseudorandom int in range [0, n)
     */
    int nextInt(int n);
}