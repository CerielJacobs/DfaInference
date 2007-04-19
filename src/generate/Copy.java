package generate;

import java.io.FileReader;

import org.apache.log4j.Logger;

import DfaInference.DFA;

/**
 * Utility to copy a DFA, and using breadth-first ordering.
 */
public class Copy {

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(Copy.class.getName());

    public static void main(String[] args) {

        String  machinefile = "machine";
        String  targetfile = "copy";

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-m")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-m option requires filename");
                    System.exit(1);
                }
                machinefile = args[i];
            } else if (args[i].equals("-o")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-o option requires filename");
                    System.exit(1);
                }
                targetfile = args[i];
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

        try {
            dfa.write(targetfile);
        } catch(Throwable e) {
            e.printStackTrace();
        }
    }
}
