package generate;

import java.util.ArrayList;
import java.util.Random;

import DfaInference.DFA;
import DfaInference.Symbols;
import abbadingo.AbbaDingoString;

/**
 * Utility to generate random strings.
 */
public class GenerateSentences {

    /** Random number generator for generating strings. */
    private static Random randomizer = new Random();

    private GenerateSentences() {
        // to prevent construction.
    }

    /**
     * Generates a random string with the specified maximum length and
     * the specified number of symbols. The strings are selected
     * according to uniform distribution over the total string space.
     * @param maxl the maximum length
     * @param nsym the number of symbols
     * @return the string as an array of token numbers.
     */
    static int[] generateString(int maxl, int nsym) {
        // Uniform distribution over the total string space.
        // There are nsym^maxl + nsym^(maxl-1) + .... + nsym + 1 such strings.
        double[] nStrings = new double[maxl+1];
        double[] sums = new double[maxl+1];
        nStrings[0] = 1.0;
        sums[0] = 1.0;
        for (int i = 1; i <= maxl; i++) {
            nStrings[i] = nsym * nStrings[i-1];
            sums[i]= sums[i-1] + nStrings[i];
        }
        // First pick a random length, in accordance with the number of 
        // strings of each length.
        int len = maxl;
        double rand = sums[maxl] * randomizer.nextDouble();
        for (int i = 0; i <= maxl; i++) {
            if (sums[i] > rand) {
                len = i;
                break;
            }
        }

        int[] str = new int[len];
        for (int i = 0; i < len; i++) {
            str[i] = randomizer.nextInt(nsym);
        }
        return str;
    }

    /**
     * Generates the specified number of random strings with the specified
     * maximum length and the specified number of symbols.
     * The strings are selected according to uniform distribution over the
     * total string space. The strings are printed on standard output
     * @param count the number of strings to generate
     * @param maxl the maximum length
     * @param nsym the number of symbols
     * @return the generated strings.
     */
    public static int[][] generateStrings(int count, int maxl, int nsym) {
        int[][] samples = new int[count][];

        DFA sampleDFA = new DFA(nsym);
        int nStates = sampleDFA.getNumStates();
        // Generate count strings
        for (int i = 0; i < count; i++) {
            int[] attempt;
            do {
                attempt = generateString(maxl, nsym);
                int[] adds = new int[attempt.length+1];
                System.arraycopy(attempt, 0, adds, 1, attempt.length);
                adds[0] = 1;
                sampleDFA.addString(adds);
                int ns = sampleDFA.getNumStates();
                if (ns != nStates) {
                    nStates = ns;
                    break;
                }
                // no new states means sentence has already been added.
                // Generate another one.
            } while (true);
            samples[i] = attempt;
        }
        return samples;
    }
}
