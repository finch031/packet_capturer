package com.github.capture.utils;

import java.util.Random;

/**
 * Implement a random number generator based on the XORShift algorithm discovered by George Marsaglia.
 * This RNG is observed 4.5 times faster than {@link java.util.Random} in benchmark, with the cost
 * that abandon thread-safety. So it's recommended to create a new {@link XORShiftRandom} for each
 * thread.
 *
 * @see <a href="http://www.jstatsoft.org/v08/i14/paper">XORShift Algorithm Paper</a>
 */
public class XORShiftRandom extends Random implements IdGenerator {
    private static final long serialVersionUID = -825722456120842841L;
    private long seed;

    public XORShiftRandom() {
        this(System.nanoTime());
    }

    public XORShiftRandom(long input) {
        super(input);
        this.seed = murmurHash((int) input) ^ murmurHash((int) (input >>> 32));
    }

    @Override
    public int id(int bits) {
        return next(bits);
    }

    @Override
    public long id() {
        return next(8);
    }

    /**
     * All other methods like nextInt()/nextDouble()... depends on this, so we just need to overwrite
     * this.
     *
     * @param bits Random bits
     * @return The next pseudorandom value from this random number
     * generator's sequence
     */
    @Override
    public int next(int bits) {
        long nextSeed = seed ^ (seed << 21);
        nextSeed ^= (nextSeed >>> 35);
        nextSeed ^= (nextSeed << 4);
        seed = nextSeed;
        return (int) (nextSeed & ((1L << bits) - 1));
    }

    /**
     * This function hashes an integer value.
     *
     * <p>It is crucial to use different hash functions to partition data across machines and the internal partitioning of
     * data structures. This hash function is intended for partitioning across machines.
     *
     * @param code The integer to be hashed.
     * @return The non-negative hash code for the integer.
     */
    public static int murmurHash(int code) {
        code *= 0xcc9e2d51;
        code = Integer.rotateLeft(code, 15);
        code *= 0x1b873593;

        code = Integer.rotateLeft(code, 13);
        code = code * 5 + 0xe6546b64;

        code ^= 4;
        code = bitMix(code);

        if (code >= 0) {
            return code;
        }
        else if (code != Integer.MIN_VALUE) {
            return -code;
        }
        else {
            return 0;
        }
    }

    /**
     * Bit-mixing for pseudo-randomization of integers (e.g., to guard against bad hash functions). Implementation is
     * from Murmur's 32 bit finalizer.
     *
     * @param in the input value
     * @return the bit-mixed output value
     */
    private static int bitMix(int in) {
        in ^= in >>> 16;
        in *= 0x85ebca6b;
        in ^= in >>> 13;
        in *= 0xc2b2ae35;
        in ^= in >>> 16;
        return in;
    }
}