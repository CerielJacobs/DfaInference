package test;

import java.io.FileReader;

import org.apache.log4j.Logger;

import DfaInference.DFA;
import DfaInference.Symbols;
import abbadingo.AbbaDingoReader;
import abbadingo.AbbaDingoString;

/**
 * Utility to run a test sample through a specified DFA, and produce a single
 * bit for each string in the sample, telling whether the string belongs to
 * the language recognized by the DFA.
 */
public class TestSample {

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(TestSample.class.getName());

    public static void main(String[] args) {

        String machinefile = "LearnedDFA";
        String testInput = "TestSample";

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-m")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-m option requires DFA filename");
                    System.exit(1);
                }
                machinefile = args[i];
            } else if (args[i].equals("-s")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-s option requires sample filename");
                    System.exit(1);
                }
                testInput = args[i];
            } else {
                logger.fatal("Unrecognized option: " + args[i]);
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

        System.out.println(dfa.dumpDFA());

        AbbaDingoString[] samples = null;
        try {
            samples = AbbaDingoReader.getStrings(testInput);
        } catch(java.io.IOException e) {
            logger.fatal("IO Exception", e);
            System.exit(1);
        }

        int[][] testSamples = Symbols.convert2learn(samples);

        for (int i = 0; i < testSamples.length; i++) {
            System.out.print(dfa.recognize(testSamples[i]) ? "1" : "0");
            if (i % 72 == 71) {
                System.out.println("");
            }
        }
        System.out.println("");
    }
}
