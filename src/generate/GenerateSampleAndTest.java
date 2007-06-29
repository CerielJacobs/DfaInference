package generate;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

import org.apache.log4j.Logger;

import DfaInference.DFA;
import DfaInference.Symbols;
import abbadingo.AbbaDingoString;

/**
 * Utility to generate learning set and test for a specified DFA.
 */
public class GenerateSampleAndTest {

    /** Log4j logger. */
    private static Logger logger
            = Logger.getLogger(GenerateSampleAndTest.class.getName());

    private static BufferedWriter learn;
    private static BufferedWriter test;

    /** Random number generator for generating strings. */
    private static Random randomizer = new Random();

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
    private static int[][] generateStrings(int count, int maxl, int nsym) {
        int[][] samples = new int[count][];

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

        // Generate count strings
        for (int i = 0; i < count; i++) {
            int[] attempt;
            do {
                int len = maxl;
                double rand = sums[maxl] * randomizer.nextDouble();
                for (int j = 0; j <= maxl; j++) {
                    if (sums[j] > rand) {
                        len = j;
                        break;
                    }
                }

                attempt = new int[len];
                for (int j = 0; j < len; j++) {
                    attempt[j] = randomizer.nextInt(nsym);
                }
                boolean present = false;
                for (int k = 0; k < i; k++) {
                    boolean equal = true;
                    if (samples[k].length == attempt.length) {
                        for (int l = 0; l < attempt.length; l++) {
                            if (samples[k][l] != attempt[l]) {
                                equal = false;
                                break;
                            }
                        }
                    } else {
                        equal = false;
                    }
                    if (equal) {
                        present = true;
                        break;
                    }
                }
                if (! present) {
                    break;
                }
                // no new states means sentence has already been added.
                // Generate another one.
            } while (true);
            samples[i] = attempt;
        }
        return samples;
    }

    /**
     * Converts a string consisting of token numbers to an AbbaDingoString.
     * @param str the string to be converted
     * @param flag the flag.
     * @return the AbbaDingoString.
     */
    private static AbbaDingoString cvt2AbbaDingo(int[] str, int flag) {
        AbbaDingoString s = new AbbaDingoString(str.length, flag);

        for (int i = 0; i < str.length; i++) {
            s.addToken(Symbols.getSymbol(str[i]));
        }
        return s;
    }

    public static void main(String[] args) {
        String  machinefile = "machine";
        int maxl = 15;
        int count = 2000;
        int testcount = 0;
        boolean negativeSamples = true;
        String prefix = "data";

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-m")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-m option requires filename");
                    System.exit(1);
                }
                machinefile = args[i];
            } else if (args[i].equals("-maxl")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-maxl option requires length");
                    System.exit(1);
                }
                maxl = new Integer(args[i]).intValue();
            } else if (args[i].equals("-testcount")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-testcount option requires testcount");
                    System.exit(1);
                }
                testcount = new Integer(args[i]).intValue();
            } else if (args[i].equals("-count")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-count option requires count");
                    System.exit(1);
                }
                count = new Integer(args[i]).intValue();
            } else if (args[i].equals("-no-negatives")) {
                negativeSamples = false;
            } else if (args[i].equals("-negatives")) {
                negativeSamples = true;
            } else if (args[i].equals("-prefix")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-prefix option requires prefix");
                    System.exit(1);
                }
                prefix = args[i];
            } else {
                logger.fatal("Usage: java generate.GenerateSampleAndTest [ -m <machine ] [ -count <count>] [ -testcount <testcount>] [-prefix <prefix>] [-negatives|-no-negatives]");
                System.exit(1);
            }
        }


        FileReader fr = null;

        // Read the DFA.
        try {
            fr = new FileReader(machinefile);
        } catch(Exception e) {
            logger.fatal("Could not open input file");
            System.exit(1);
        }

        DFA dfa = new DFA(fr);
        int nsym = Symbols.nSymbols();

        try {
            learn = new BufferedWriter(new FileWriter(prefix + "." + count));
            test = new BufferedWriter(new FileWriter(prefix + ".test"));
        } catch(IOException e) {
            logger.fatal("Could not open output", e);
            System.exit(1);
        }

        int[][] samples = new int[count][];

        // Generate enough strings. If we don't want negatives in the learn
        // sample, we don't know what is enough ...
        int numSentences = (! negativeSamples ? 5 * count : count )
                + testcount;
        int[][] sentences = generateStrings(numSentences, maxl, nsym);
        int sentenceIndex = 0;

        for (int i = 0; i < count; i++) {
            do {
                if (sentenceIndex >= sentences.length) {
                    logger.fatal("Not enough sentences generated. Try again");
                }
                int[] s = new int[sentences[sentenceIndex].length+1];
                System.arraycopy(sentences[sentenceIndex++], 0, s, 1, s.length-1);
                s[0] = -1;
                boolean recognize = dfa.recognize(s);
                if (! recognize && ! negativeSamples) {
                    continue;
                }
                break;
            } while (true);
            samples[i] = sentences[sentenceIndex-1];
        }

        if (sentences.length - sentenceIndex < testcount) {
            logger.fatal("Not enough sentences generated. Try again");
        }

        try {
            // Print number of strings and number of symbols.
            learn.write("" + count + " " + nsym);
            learn.newLine();
            for (int i = 0; i < count; i++) {
                int[] s = new int[samples[i].length+1];
                System.arraycopy(samples[i], 0, s, 1, s.length-1);
                s[0] = -1;
                boolean recognize = dfa.recognize(s);
                learn.write("" + cvt2AbbaDingo(samples[i], recognize ? 1 : 0));
                learn.newLine();
            }
            learn.close();
        } catch(IOException e) {
            logger.fatal("Could not write learn-set", e);
            System.exit(1);
        }

        if (testcount > 0) {
            int[][] tests = new int[testcount][];
            System.arraycopy(sentences, sentenceIndex, tests, 0, testcount);

            try {
                // Print number of strings and number of symbols.
                test.write("" + testcount + " " + nsym);
                test.newLine();
                for (int i = 0; i < testcount; i++) {
                    test.write("" + cvt2AbbaDingo(tests[i], -1));
                    test.newLine();
                }
                test.close();
            } catch(IOException e) {
                logger.fatal("Could not write test-set", e);
                System.exit(1);
            }
        }
    }
}
