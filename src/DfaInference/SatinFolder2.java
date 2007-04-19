package DfaInference;

import abbadingo.*;

import ibis.satin.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.util.*;

import org.apache.log4j.Logger;

/**
 * This class implements a search strategy that, up to a certain depth,
 * at each level determines which "blue" node is dealt with next. The possible
 * choices are to merge it with one of the "red" nodes, if possible, and to
 * promote it to a "red" node. Deciding which blue node to deal with next
 * is determined by the search strategy specified.
 *
 * This version is suitable for Satin, Master-Worker.
 */
public class SatinFolder2 extends SatinObject implements SatinFolder2Interface {

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(SatinFolder2.class.getName());

    /** The heuristic used. */
    private RedBlue folder;

    /** Maximum depth of search. */
    private transient int maxDepth;

    /** Minimum depth of search. */
    private transient int minDepth;

    /** Learning samples. */
    private transient Samples samples;

    /** DFA from samples. */
    private transient DFA initialDFA;

    /** Job list. */
    private transient final Vector jobList = new Vector();

    /** Job list termination. */
    private transient int jobsDone = 0;

    /** Job result container. */
    public static class ResultContainer {
        public ControlResultPair result;
        public ResultContainer() {
        }
    }

    /** Job results. */
    private transient ArrayList results = new ArrayList();

    /**
     * Constructor.
     * @param folder the heuristic to be used for folding.
     * @param maxDepth the search depth.
     */
    public SatinFolder2(RedBlue folder, int minDepth, int maxDepth) {
        this.folder = folder;
        this.folder.disableChoices = false;
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
    }

    private void walk(ControlResultPair p, int depth, int targetDepth) {
        if (logger.isDebugEnabled()) {
            logger.debug("walk: " + p);
        }
        if (depth == targetDepth) {
            synchronized(this) {
                jobList.add(p);
                notify();
            }
        } else {
            tryExtending(p, depth, targetDepth);
        }
    }

    /**
     * Uses the specified guidance and the folder's heuristic to compress
     * the DFA and determine the resulting score.
     * @param control the specified guidance
     * @return the resulting score.
     */
    double tryControl(int[] control, Samples learningSamples) {
        folder.doFold(learningSamples, new IntGuidance(control), 0);
        return folder.getScore();
    }

    public ControlResultPair buildPair(ControlResultPair p,
            Samples learningSamples) {
        if (logger.isDebugEnabled()) {
            logger.debug("buildPair: " + p);
        }
        p.score = tryControl(p.control, learningSamples);
        return p;
    }

    private synchronized void spawnJobs(int targetCount) {
        while (jobsDone < targetCount || jobList.size() != 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("spawnJobs: waiting ...");
            }
            if (jobList.size() == 0) {
                try {
                    wait();
                } catch(Exception e) {
                    // ignored
                }
            }
            if (jobList.size() != 0) {
                if (logger.isDebugEnabled()) {
                    logger.debug("spawnJobs: spawning ...");
                }
                for (int i = 0; i < jobList.size(); i++) {
                    ResultContainer r = new ResultContainer();
                    results.add(r);
                    ControlResultPair p = (ControlResultPair) jobList.get(i);
                    r.result = buildPair(p, samples);
                }
                jobList.clear();
            }
        }

        sync();
    }

    private int getCount(Choice[] choices, int blue) {
        int cnt = 0;
        for (int i = 0; i < choices.length; i++) {
            if (choices[i].s2 == blue) {
                cnt++;
            }
        }
        return cnt;
    }

    /**
     * Extends the search list one more step by expanding the entries in
     * the specified list. All possibilities for this one step are tried.
     * @param p the current control/result pair.
     * @param depth indicates the current depth.
     * @param learningSamples the samples to learn from.
     * @return the new control/result pair.
     */
    void tryExtending(ControlResultPair p, int depth, int targetDepth) {
        DFA dfa = new DFA(initialDFA);
        Guidance g;
        g = new IntGuidance(p.control);
        Choice[] choice = folder.getOptions(dfa, g, 100);

        dfa = null;

        if (logger.isInfoEnabled()) {
            logger.info("depth = " + depth
                    + ", choice length = " + choice.length);
        }


        for (int k = 0; k < choice.length; k++) {
            int[] control = new int[p.control.length+1];
            System.arraycopy(p.control, 0, control, 0, p.control.length);
            control[p.control.length] = k;

            ControlResultPair pair
                    = new ControlResultPair(-1, control, -1,
                            control[p.control.length]);
            walk(pair, depth+1, targetDepth);
        }
    }

    private static class JobBuilder extends Thread {
        SatinFolder2 satinFolder;
        int[] control;
        int targetDepth;

        JobBuilder(SatinFolder2 satinFolder, int[] control, int targetDepth) {
            this.satinFolder = satinFolder;
            this.control = new int[control.length];
            this.targetDepth = targetDepth;
            System.arraycopy(control, 0, this.control, 0, control.length);
        }

        public void run() {
            ControlResultPair pop;
            pop = new ControlResultPair(Integer.MAX_VALUE, control, 0, 0);
            satinFolder.tryExtending(pop, control.length, targetDepth);
            synchronized(satinFolder) {
                satinFolder.jobsDone++;
                satinFolder.notify();
            }
        }
    }

    /**
     * Overall search process driver.
     * @param samples the samples to learn from.
     */
    ControlResultPair doSearch(DFA initialDFA, Samples samples) {
        this.initialDFA = initialDFA;
        samples.exportObject();
        this.samples = samples;

        (new JobBuilder(this, new int[0], minDepth)).start();
        spawnJobs(1);

        ControlResultPair[] result = new ControlResultPair[results.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = ((ResultContainer) results.get(i)).result;
        }
        results.clear();
        Arrays.sort(result);
        return result[0];
    }

    /**
     * Main program of the Satin "SatinFolder2" searcher.
     * @param args the program arguments.
     */
    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        String folder = "DfaInference.EdFold";
        String blueStrategy = "DfaInference.ChoiceCountStrategy";
        int minDepth = 0;
        int maxDepth = 0;
        PickBlueStrategy strategy;

        long startTime = System.currentTimeMillis();

        System.out.println(Helpers.getPlatformVersion() + "\n\n");

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-mindepth")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-mindepth option requires number");
                    System.exit(1);
                }
                minDepth = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-maxdepth")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-maxdepth option requires number");
                    System.exit(1);
                }
                maxDepth = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-strategy")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-strategy option requires class name");
                    System.exit(1);
                }
                blueStrategy = args[i];
            } else if (args[i].equals("-input")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-input option requires filename");
                    System.exit(1);
                }
                learningSetFile = args[i];
            } else if (args[i].equals("-folder")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-folder option requires class name");
                    System.exit(1);
                }
                folder = args[i];
            } else if (args[i].equals("-output")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-output option requires outputfile");
                    System.exit(1);
                }
                outputfile = args[i];
                folder = args[i];
            } else {
                logger.fatal("Unrecognized option: " + args[i]);
                System.exit(1);
            }
        }

        Class cl;
        try {
            cl = Class.forName(folder);
        } catch(ClassNotFoundException e) {
            throw new Error("Class " + folder + " not found", e);
        }

        RedBlue f;

        try {
            f = (RedBlue) cl.newInstance();
        } catch(Exception e) {
            throw new Error("Could not instantiate " + folder, e);
        }

        try {
            cl = Class.forName(blueStrategy);
        } catch(ClassNotFoundException e) {
            throw new Error("Class " + blueStrategy + " not found", e);
        }

        try {
            strategy = (PickBlueStrategy) cl.newInstance();
        } catch(Exception e) {
            throw new Error("Could not instantiate " + blueStrategy, e);
        }

        f.setBlueStrategy(strategy);

        AbbaDingoString[] samples = null;
        try {
            if (learningSetFile != null) {
                samples = AbbaDingoReader.getStrings(learningSetFile);
            }
            else {
                samples = AbbaDingoReader.getStrings(System.in);
            }
        } catch(java.io.IOException e) {
            logger.fatal("IO Exception", e);
            System.exit(1);
        }

        int[][] iSamples = Symbols.convert2learn(samples);
        DFA initialDFA = new DFA(iSamples);
        BitSet[] conflicts = initialDFA.computeConflicts();
        initialDFA.setConflicts(conflicts);
        Samples learningSamples = new Samples(iSamples, conflicts);

        SatinFolder2 b = new SatinFolder2(f, minDepth, maxDepth);

        long initializationTime = System.currentTimeMillis();

        ControlResultPair p = b.doSearch(initialDFA, learningSamples);

        long searchTime = System.currentTimeMillis();

        DFA bestDFA = f.doFold(learningSamples, new IntGuidance(p.control), 0);

        long endTime = System.currentTimeMillis();

        System.out.println("The winner DFA has MDL complexity "
                + bestDFA.getMDLComplexity() + " and " + bestDFA.getNumStates()
                + " states");
        System.out.println("Total time     = " + (double)(endTime - startTime) / 1000.0);
        System.out.println("Initialization = " + (double)(initializationTime - startTime) / 1000.0);
        System.out.println("Search         = " + (double)(searchTime - initializationTime) / 1000.0);
        System.out.println("Solution       = " + (double)(endTime - searchTime) / 1000.0);

        if (logger.isInfoEnabled()) {
            logger.info("The winner is:\n" + bestDFA);
        }

        bestDFA.write(outputfile);
    }
}
