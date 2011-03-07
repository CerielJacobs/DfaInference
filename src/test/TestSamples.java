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

    private static String cvt(int cnt, int size) {
        String result = "" + cnt;
        int v = 10;
        while (size > v) {
            if (cnt < v) {
                result = "0" + result;
            }
            v *= 10;
        }
        return result;
    }


    public static void main(String[] args) {

        String size = "1500";
        String version = "V1";
        String reader = "abbadingo.AbbaDingoIO";
        String prefix = "DFAs/DFA-";
        int count = 1000;


        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-s")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-s option requires size");
                    System.exit(1);
                }
                size = args[i];
            } else if (args[i].equals("-prefix")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-prefix option requires prefix");
                    System.exit(1);
                }
                prefix = args[i];
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
            } else if (args[i].equals("-count")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-count option requires number");
                    System.exit(1);
                }
                count = (new Integer(args[i])).intValue();

            } else {
                logger.fatal("Unrecognized option: " + args[i]);
                System.exit(1);
            }
        }

        for (int i = 0; i < count; i++) {
            String s = cvt(i, count);
            String dfa = prefix + s;
            String learned = "learned." + version + "/Learned-" + s + "." + size;
            TestSample.testSample(learned, dfa, dfa + ".test", reader);
        }
    }
}
