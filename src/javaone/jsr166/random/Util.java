package jsr166.random;

class Util {
    static int calculateNext(int s) {
        int t = (s % 127773) * 16807 - (s / 127773) * 2836;
        return (t > 0) ? t : (t + 0x7fff);
    }
}
