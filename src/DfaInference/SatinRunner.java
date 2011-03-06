package DfaInference;

import ibis.satin.SatinObject;

import org.apache.log4j.Logger;

public class SatinRunner extends SatinObject implements SatinRunnerInterface {

    private static final long serialVersionUID = 1L;
    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(SatinRunner.class.getName());

    public SatinRunner() {
    }

    public long doRun(String command, String name) {
        Process p;
        String[] cmd = new String[2];
        cmd[0] = command;
        cmd[1] = name;
        long time = System.currentTimeMillis();
        try {
            p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
        } catch(Exception e) {
            logger.warn("Got exception", e);
        }
        return System.currentTimeMillis() - time;
    }

    private String cvt(int cnt, int size) {
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

    public long[] run(String command, String size, int count) {
	long times[] = new long[count];
        for (int i = 0; i < count; i++) {
            times[i] = doRun(command, cvt(i, count) + "." + size);
        }
        sync();
        return times;
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
        long[] times = r.run(command, size, count);
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = 0;
        for (int i = 0; i < count; i++) {
            sum += times[i];
            if (times[i] < min) {
        	min = times[i];
            }
            if (times[i] > max) {
        	max = times[i];
            }
        }
        System.out.println("Command = " + command + ", size = " + size);
        System.out.println("Average time = " + (((float) sum)/(count*1000))
        	+ " sec, min = " + (min/1000.0) + " sec, max = " + (max/1000.0) + " sec.");
    }
}

