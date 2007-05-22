package DfaInference;

import ibis.satin.SatinObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.log4j.Logger;

import abbadingo.AbbaDingoReader;
import abbadingo.AbbaDingoString;

public class SatinRunner extends SatinObject implements SatinRunnerInterface {

    private static final long serialVersionUID = 1L;
    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(SatinRunner.class.getName());

    public SatinRunner() {
    }

    public void doRun(String command, String name) {
        Process p;
        String[] cmd = new String[2];
        cmd[0] = command;
        cmd[1] = name;
        try {
            p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
        } catch(Exception e) {
            logger.warn("Got exception", e);
        }
    }

    private String cvt(int cnt) {
        if (cnt < 10) {
            return "00" + cnt;
        }
        if (cnt < 100) {
            return "0" + cnt;
        }
        return "" + cnt;
    }

    public void run(String command, String size, int count) {
        for (int i = 0; i < count; i++) {
            doRun(command, cvt(i) + "." + size);
        }
        sync();
    }

    public static void main(String[] args) {

        String command = "./learn-EDSM";
        String size = "2000";
        int count = 1000;

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-command")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-command option requires command");
                    System.exit(1);
                }
                command = args[i];
            } else if (args[i].equals("-size")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-size option requires size");
                    System.exit(1);
                }
                size = args[i];
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

        SatinRunner r = new SatinRunner();
        r.run(command, size, count);
    }
}

