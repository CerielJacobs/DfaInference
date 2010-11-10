package test;

import org.apache.log4j.Logger;

/**
 * Utility to run a test samples through DFAs, and produce a single
 * bit for each string in the sample, telling whether the string belongs to
 * the language recognized by the DFA.
 */
public class TestSamples {

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(TestSamples.class.getName());

    public static void main(String[] args) {

        String size = "1500";
        String version = "V1";
        String reader = "abbadingo.AbbaDingoReader";


        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-s")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-s option requires size");
                    System.exit(1);
                }
                size = args[i];
            } else if (args[i].equals("-v")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-v option requires version");
                    System.exit(1);
                }
                version = args[i];
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

        for (int i = 0; i < 1000; i++) {
            String s = (i < 10 ? "00" : (i < 100 ? "0" : "")) + i;
            String dfa = "DFAs/DFA-" + s;
            String learned = "learned." + version + "/Learned-" + s + "." + size;
            TestSample.testSample(learned, dfa, dfa + ".test", reader);
        }
    }
}
