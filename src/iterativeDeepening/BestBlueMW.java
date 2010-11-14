package iterativeDeepening;

import ibis.satin.SatinObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Vector;

import org.apache.log4j.Logger;

import DfaInference.Choice;
import DfaInference.ControlResultPair;
import DfaInference.DFA;
import DfaInference.Guidance;
import DfaInference.Helpers;
import DfaInference.IntGuidance;
import DfaInference.PickBlueStrategy;
import DfaInference.RedBlue;
import DfaInference.Samples;
import DfaInference.Symbols;

import sample.SampleReader;
import sample.SampleString;

/**
 * This class implements a search strategy that, up to a certain depth,
 * at each level determines which "blue" node is dealt with next. The possible
 * choices are to merge it with one of the "red" nodes, if possible, and to
 * promote it to a "red" node. Deciding which blue node to deal with next
 * is determined by the search strategy specified.
 *
 * This version is suitable for Satin, Master-Worker.
 */
public class BestBlueMW extends SatinObject implements BestBlueMWInterface {

    private static final long serialVersionUID = 1L;

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(BestBlueMW.class.getName());

    /** The heuristic used. */
    private RedBlue folder;

    /** Learning samples. */
    private transient Samples samples;

    /** Job list. */
    private transient final Vector<ControlResultPair> jobList = new Vector<ControlResultPair>();

    /** Job list termination. */
    private transient int jobsDone = 0;

    /** Job result container. */
    public static class ResultContainer {
        public ControlResultPair result;
        public ResultContainer() {
        }
    }

    /** Job results. */
    private transient ArrayList<ResultContainer> results = new ArrayList<ResultContainer>();

    /**
     * Constructor.
     * @param folder the heuristic to be used for folding.
     */
    public BestBlueMW(RedBlue folder) {
        this.folder = folder;
        this.folder.disableChoices = false;
    }

    private void walk(ControlResultPair p, int depth, int targetDepth) {
        if (logger.isDebugEnabled()) {
            logger.debug("walk: " + p);
        }
        if (depth >= targetDepth) {
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

    private void spawnJobs(int targetCount) {
        logger.warn("Starting spawns");
        for (;;) {
            ControlResultPair[] jobs;
            synchronized(this) {
                while (jobList.size() == 0) {
                    if (jobsDone >= targetCount) {
                        break;
                    }
                    try {
                        wait();
                    } catch(Exception e) {
                    }
                }
                if (jobList.size() == 0) {
                    break;
                }
                jobs = jobList.toArray(new ControlResultPair[jobList.size()]);
                jobList.clear();
            }
            if (logger.isDebugEnabled()) {
                logger.debug("spawnJobs: spawning ...");
            }
            for (int i = 0; i < jobs.length; i++) {
                ResultContainer r = new ResultContainer();
                results.add(r);
                ControlResultPair p = jobs[i];
                r.result = buildPair(p, samples);
            }
        }
        logger.warn("Spawns done");

        sync();
    }


    /**
     * Extends the search list one more step by expanding the entries in
     * the specified list. All possibilities for this one step are tried.
     * @param p the current control/result pair.
     * @param depth indicates the current depth.
     * @param learningSamples the samples to learn from.
     */
    void tryExtending(ControlResultPair p, int depth, int targetDepth) {
        DFA dfa = new DFA(samples);
        // DFA dfa = new DFA(initialDFA);
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
        BestBlueMW folder;
        int[] control;
        int targetDepth;

        JobBuilder(BestBlueMW folder, int[] control, int targetDepth) {
            this.folder = folder;
            this.control = new int[control.length];
            this.targetDepth = targetDepth;
            System.arraycopy(control, 0, this.control, 0, control.length);
        }

        public void run() {
            ControlResultPair pop;
            pop = new ControlResultPair(Integer.MAX_VALUE, control, 0, 0);
            folder.tryExtending(pop, control.length, targetDepth);
            synchronized(folder) {
                folder.jobsDone++;
                folder.notify();
            }
        }
    }

    /**
     * Overall search process driver.
     * @param samples the samples to learn from.
     */
    ControlResultPair doSearch(Samples samples, int minD, int maxD,
            File dumpfile) {

        ControlResultPair pop = null;

        samples.exportObject();

        this.samples = samples;

        if (dumpfile != null) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(dumpfile));
                pop = new ControlResultPair(br);
            } catch(Exception e) {
                // ignored
            }
        }

        for (int i = minD; i <= maxD; i++) {
            int fixD = i - minD;
            int[] control = new int[fixD];
            if (pop != null) {
                if (pop.control.length >= i) {
                    continue;
                }
                System.out.print("Fixing up until depth " + fixD + ":");
                for (int j = 0; j < fixD; j++) {
                    control[j] = pop.control[j];
                    System.out.print(" " + control[j]);
                }
                System.out.println("");
            }
            (new JobBuilder(this, control, i)).start();
            spawnJobs(1);
            jobsDone = 0;

            ControlResultPair[] result = new ControlResultPair[results.size()];
            for (int j = 0; j < result.length; j++) {
                result[j] = results.get(j).result;
            }
            results.clear();
            Arrays.sort(result);
            pop = result[0];

            if (dumpfile != null) {
                try {
                    File temp = File.createTempFile("dfa", "dmp", new File("."));
                    BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
                    pop.write(bw);
                    bw.close();
                    if (! temp.renameTo(dumpfile)) {
                        throw new IOException("rename failed");
                    }
                } catch(IOException e) {
                    logger.warn("Could not write dump ...", e);
                }
            }
        }

        return pop;
    }

    /**
     * Main program of the Satin "BestBlueMW" searcher.
     * @param args the program arguments.
     */
    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        String folder = "DfaInference.EdFold";
        String blueStrategy = "DfaInference.ChoiceCountStrategy";
        String reader = "abbadingo.AbbaDingoReader";
        int minDepth = 5;
        int maxDepth = -1;
        boolean maxDepthSpecified = false;
        PickBlueStrategy strategy;
        File dumpfile = null;

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
                maxDepthSpecified = true;
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
            } else if (args[i].equals("-reader")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-reader option requires class name");
                    System.exit(1);
                }
                reader = args[i];
            } else if (args[i].equals("-output")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-output option requires outputfile");
                    System.exit(1);
                }
                outputfile = args[i];
            } else if (args[i].equals("-dump")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-dump option requires filename");
                    System.exit(1);
                }
                dumpfile = new File(args[i]);
            } else {
                logger.fatal("Unrecognized option: " + args[i]);
                System.exit(1);
            }
        }

        if (maxDepth < minDepth) {
            if (maxDepthSpecified) {
                logger.warn("maxdepth < mindepth, setting to mindepth");
            }
            maxDepth = minDepth;
        }

        Class<?> cl;
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

        SampleString[] samples = null;
        try {
            SampleReader sampleReader = new SampleReader(reader);
            if (learningSetFile != null) {
                samples = sampleReader.getStrings(learningSetFile);
            }
            else {
                samples = sampleReader.getStrings(System.in);
            }
        } catch(java.io.IOException e) {
            logger.fatal("IO Exception", e);
            System.exit(1);
        }

        Symbols symbols = new Symbols();
        int[][] iSamples = symbols.convert2learn(samples);
        Samples ls = new Samples(symbols, iSamples, null);
        DFA initialDFA = new DFA(ls);
        BitSet[] conflicts = initialDFA.computeConflicts();

        Samples learningSamples = new Samples(symbols, iSamples, conflicts);

        BestBlueMW b = new BestBlueMW(f);

        long initializationTime = System.currentTimeMillis();

        ControlResultPair p = b.doSearch(learningSamples, minDepth, maxDepth,
                dumpfile);

        long searchTime = System.currentTimeMillis();

        DFA bestDFA = f.doFold(learningSamples, new IntGuidance(p.control), 0);

        long endTime = System.currentTimeMillis();

        System.out.println("The winner DFA has MDL complexity "
                + bestDFA.getMDLComplexity() + " and " + bestDFA.getNumStates()
                + " states");
        System.out.println("Total time     = " + (endTime - startTime) / 1000.0);
        System.out.println("Initialization = " + (initializationTime - startTime) / 1000.0);
        System.out.println("Search         = " + (searchTime - initializationTime) / 1000.0);
        System.out.println("Solution       = " + (endTime - searchTime) / 1000.0);

        if (logger.isInfoEnabled()) {
            logger.info("The winner is:\n" + bestDFA);
        }

        bestDFA.write(outputfile);
    }
}
