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
 * This version is suitable for Satin, random-job-stealing.
 */
public class BestBlue extends SatinObject implements BestBlueInterface {

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(BestBlue.class.getName());

    /** The heuristic used. */
    private RedBlue folder;

    /** Depth of search. */
    private int maxDepth;

    /**
     * Constructor.
     * @param folder the heuristic to be used for folding.
     * @param maxDepth the search depth.
     */
    public BestBlue(RedBlue folder, int maxDepth) {
        this.folder = folder;
        this.folder.disableChoices = false;
        this.maxDepth = maxDepth;
    }

    public ControlResultPair buildPair(ControlResultPair p,
            Samples learningSamples, int depth) {
        if (logger.isDebugEnabled()) {
            logger.debug("buildPair: " + p);
        }
        if (depth == maxDepth) {
            p.score = tryControl(p.control, learningSamples);
            return p;
        }
        return tryExtending(p, depth, learningSamples);
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
    ControlResultPair tryExtending(ControlResultPair p, int depth,
            Samples learningSamples) {
        ControlResultPair[] pairs;
        DFA dfa = new DFA(learningSamples.learningSamples);
        dfa.setConflicts(learningSamples.conflicts);
        Guidance g;
        g = new IntGuidance(p.control);
        Choice[] choice = folder.getOptions(dfa, g, 100);

        if (logger.isInfoEnabled()) {
            logger.info("depth = " + depth
                    + ", choice length = " + choice.length);
        }

        pairs = new ControlResultPair[choice.length];

        for (int k = 0; k < choice.length; k++) {
            int[] control = new int[p.control.length+1];
            System.arraycopy(p.control, 0, control, 0, p.control.length);
            control[p.control.length] = k;

            pairs[k] = new ControlResultPair(-1, control, -1,
                    control[p.control.length]);
            pairs[k] = buildPair(pairs[k], learningSamples, depth+1);
        }

        sync(); // wait for all spawned jobs.

        Arrays.sort(pairs);

        return pairs[0];
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

    /**
     * Overall search process driver.
     * @param samples the samples to learn from.
     */
    ControlResultPair doSearch(Samples samples, int minD, int maxD) {

        ControlResultPair pop = null;
        // samples.exportObject();

        for (int i = minD; i <= maxD; i++) {
            int fixD = i - minD;
            maxDepth = i;
            int[] control = new int[fixD];
            if (pop != null) {
                System.out.print("Fixing up until depth " + fixD + ":");
                for (int j = 0; j < fixD; j++) {
                    control[j] = pop.control[j];
                    System.out.print(" " + control[j]);
                }
                System.out.println("");
            }
            pop = tryExtending(new ControlResultPair(Integer.MAX_VALUE, control, 0, 0),
                    fixD, samples);
        }
        return pop;
    }

    /**
     * Main program of the Satin "BestBlue" searcher.
     * @param args the program arguments.
     */
    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        String folder = "DfaInference.EdFold";
        String blueStrategy = "DfaInference.ChoiceCountStrategy";
        boolean printInfo = false;
        int mindepth = 5;
        int maxdepth = 5;
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
                mindepth = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-maxdepth")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-maxdepth option requires number");
                    System.exit(1);
                }
                maxdepth = (new Integer(args[i])).intValue();
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
            } else if (args[i].equals("-printInfo")) {
                printInfo = true;
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

        f.printInfo = printInfo;

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

        DFA dfa = new DFA(iSamples);
        BitSet[] conflicts = dfa.computeConflicts();

        Samples learningSamples = new Samples(iSamples, conflicts);

        BestBlue b = new BestBlue(f, mindepth);

        long initializationTime = System.currentTimeMillis();

        ControlResultPair p = b.doSearch(learningSamples, mindepth, maxdepth);

        long searchTime = System.currentTimeMillis();

        DFA bestDFA = f.doFold(learningSamples, new IntGuidance(p.control), 0);

        long endTime = System.currentTimeMillis();

        System.out.println("The winner DFA has MDL complexity "
                + bestDFA.getMDLComplexity() + " and " + bestDFA.nProductive
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
