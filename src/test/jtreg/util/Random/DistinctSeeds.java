/**
 * @test 1.1 03/11/06
 * @bug 4949279
 * @summary Independent instantiations of Random() have distinct seeds.
 */

import java.util.Random;

public class DistinctSeeds {
    public static void main(String[] args) throws Exception {
	// Strictly speaking, it is possible for these to be equal,
	// but the likelihood should be *extremely* small.
	if (new Random().nextLong() == new Random().nextLong())
            throw new RuntimeException("Random seeds not unique.");
    }
}
