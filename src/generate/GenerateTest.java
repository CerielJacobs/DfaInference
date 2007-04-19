package generate;

import org.apache.log4j.Logger;

/**
 * Utility to generate random test strings for a specified DFA.
 */
public class GenerateTest {

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(GenerateTest.class.getName());

    public static void main(String[] args) {

        int maxl = 15;
        int count = 1000;
        int nsym = 2;

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-maxl")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-maxl option requires length");
                    System.exit(1);
                }
                maxl = new Integer(args[i]).intValue();
            } else if (args[i].equals("-nsym")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-nsym option requires number");
                    System.exit(1);
                }
                nsym = new Integer(args[i]).intValue();
            } else if (args[i].equals("-count")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-count option requires count");
                    System.exit(1);
                }
                count = new Integer(args[i]).intValue();
            }
        }

        // Print number of strings and number of symbols.
        System.out.println(count + " " + nsym);

        // Generate count strings
        for (int i = 0; i < count; i++) {
            int[] sample = GenerateSample.generateString(maxl, nsym);
            System.out.println(GenerateSample.cvt2AbbaDingo(sample, -1));
        }
    }
}
