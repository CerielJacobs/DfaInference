package generate;

import org.apache.log4j.Logger;

import abbadingo.*;
import DfaInference.*;
import java.util.Random;
import java.io.FileReader;

/**
 * Utility to generate random strings for a specified DFA.
 */
public class GenerateSample {

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(GenerateSample.class.getName());

    /** Random number generator for generating strings. */
    private static Random randomizer = new Random();

    /**
     * Converts a string consisting of token numbers to an AbbaDingoString.
     * @param str the string to be converted
     * @param flag the flag.
     * @return the AbbaDingoString.
     */
    static AbbaDingoString cvt2AbbaDingo(int[] str, int flag) {
        AbbaDingoString s = new AbbaDingoString(str.length, flag);

        for (int i = 0; i < str.length; i++) {
            s.addToken(Symbols.getSymbol(str[i]));
        }
        return s;
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
        // There are maxl^nsym + (maxl-1)^nsym + .... + nsym + 1 such strings.
        int len = maxl;
        while (len > 0) {
            if (randomizer.nextInt(nsym) == 0) {
                len--;
            } else {
                break;
            }
        }
        int[] str = new int[len];
        for (int i = 0; i < len; i++) {
            str[i] = randomizer.nextInt(nsym);
        }
        return str;
    }

    public static void main(String[] args) {

        String  machinefile = "machine";
        int maxl = 15;
        int count = 1000;

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
            } else if (args[i].equals("-count")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-count option requires count");
                    System.exit(1);
                }
                count = new Integer(args[i]).intValue();
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

        // Print number of strings and number of symbols.
        System.out.println(count + " " + nsym);
        int[][] samples = new int[count][];

        // Generate count acceptable strings
        for (int i = 0; i < count; i++) {
            int[] attempt;
            // Generate while not recognized.
            do {
                attempt = generateString(maxl, nsym);
            } while (! dfa.recognize(attempt));
            samples[i] = attempt;
            // Print the string.
            System.out.println(cvt2AbbaDingo(attempt, 1));
        }

        // Run the sample set through the DFA, print the scores.
        dfa.runSample(samples);
        System.out.println("# DFA C = " + dfa.getDFAComplexity() + ", MDL C = " + dfa.getMDLComplexity());
    }
}
