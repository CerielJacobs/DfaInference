package test;

import java.io.FileReader;

import org.apache.log4j.Logger;

import sample.SampleIO;
import sample.SampleString;

import DfaInference.DFA;

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
        String baseDFAFile = null;
        String reader = "abbadingo.AbbaDingoIO";

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
                    logger.fatal("-s option requires testSample filename");
                    System.exit(1);
                }
                testInput = args[i];
            } else if (args[i].equals("-base")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-base option requires DFA filename");
                    System.exit(1);
                }
                baseDFAFile = args[i];
            } else if (args[i].equals("-reader")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-reader option requires class name");
                    System.exit(1);
                }
                reader = args[i];
            } else {
                logger.fatal("Unrecognized option: " + args[i]);
                System.exit(1);
            }
        }

        testSample(machinefile, baseDFAFile, testInput, reader);
    }

    static void testSample(String machinefile, String baseDFAFile, String testInput, String reader) {

        FileReader fr = null;
        DFA dfa = null;

        // Read the DFA.
        try {
            fr = new FileReader(machinefile);
            dfa = new DFA(fr);
            fr.close();
        } catch(Exception e) {
            logger.fatal("Could not open input file " + machinefile);
            System.exit(1);
        }

        DFA baseDFA = null;

        if (baseDFAFile != null) {
            try {
                fr = new FileReader(baseDFAFile);
                baseDFA = new DFA(fr);
                fr.close();
            } catch(Exception e) {
                logger.fatal("Could not open input file " + baseDFAFile);
                System.exit(1);
            }
        }

        SampleString[] samples = null;
        try {
            SampleIO sampleReader = new SampleIO(reader);
            samples = sampleReader.getStrings(testInput);
        } catch(java.io.IOException e) {
            logger.fatal("IO Exception", e);
            System.exit(1);
        }

        int[][] testSamples = dfa.symbols.convert2learn(samples);

        if (baseDFA == null) {
            for (int i = 0; i < testSamples.length; i++) {
                System.out.print(dfa.recognize(testSamples[i]) ? "1" : "0");
                if (i % 72 == 71) {
                    System.out.println("");
                }
            }
            System.out.println("");
        } else {
            int okCount = 0;
            int notOkCount = 0;
            int[][] baseTestSamples = baseDFA.symbols.convert2learn(samples);
            for (int i = 0; i < testSamples.length; i++) {
                if (dfa.recognize(testSamples[i])
                        != baseDFA.recognize(baseTestSamples[i])) {
                    notOkCount++;
                } else {
                    okCount++;
                }
            }
            System.out.println("notOkCount = " + notOkCount);
            System.out.println("okCount = " + okCount);
            int total = notOkCount + okCount;
            System.out.println("generalization rate = "
                    + (double) okCount / total);
        }
    }
}
