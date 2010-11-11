package DfaInference;

import ibis.ipl.IbisCreationFailedException;
import ibis.satin.SatinObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

import org.apache.log4j.Logger;

import sample.SampleReader;
import sample.SampleString;

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

    private static final long serialVersionUID = 1L;
    
    private static TableManager tableManager = null;
    
    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(BestBlue.class.getName());
        
    static {
        try {
            tableManager = new TableManager();
        } catch (IbisCreationFailedException e) {
            logger.warn("Could not create table manager");
        }
    }
    
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

    public boolean guard_buildPair(int fixOffset, ControlResultPair p,
            Samples learningSamples, ControlResultPairTable table, int depth) {
        return fixOffset == table.getFixOffset();
    }

    public ControlResultPair buildPair(int fixOffset, ControlResultPair p,
            Samples learningSamples, ControlResultPairTable table, int depth) {
        if (tableManager != null) {
            try {
                tableManager.client(table);
            } catch (IOException e) {
                // ignored
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("buildPair: " + p);
        }
        if (depth >= maxDepth) {
            // p = randomShooter(p.control, learningSamples);
            p.score = tryControl(p.control, learningSamples);
        } else {
            p = tryExtending(fixOffset, p, depth, learningSamples, table);
        }
        if (tableManager != null) {
            try {
                tableManager.sendResult(fixOffset, depth, p);
            } catch (IOException e) {
                // ignored
            }
        }
        return p;
    }
    
    public ControlResultPair randomShooter(int[] control, Samples learningSamples, Random r) {
        
        DFA dfa = new DFA(learningSamples);
        Guidance g = new IntGuidance(control);
        control = folder.doFold(dfa, g, 24, r);
        return new ControlResultPair(folder.getScore(), control, 0, 0);
    }

    /**
     * Extends the search list one more step by expanding the entries in
     * the specified list. All possibilities for this one step are tried.
     * @param p the current control/result pair.
     * @param depth indicates the current depth.
     * @param learningSamples the samples to learn from.
     * @param table table of already known results from an earlier run.
     * @return the new control/result pair.
     */
    ControlResultPair tryExtending(int fixOffset, ControlResultPair p, int depth,
            Samples learningSamples, ControlResultPairTable table) {
        ControlResultPair[] pairs;
        DFA dfa = new DFA(learningSamples);
        Guidance g = new IntGuidance(p.control);
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
            ControlResultPair t = table.getResult(control);
            if (t == null) {
                t = new ControlResultPair(-1, control, 0, 0);
                pairs[k] = buildPair(fixOffset, t, learningSamples, table, depth+1);
            } else {
                pairs[k] = t;
            }
        }

        sync(); // wait for all spawned jobs.

        Arrays.sort(pairs);

        return pairs[0];
    }

    ControlResultPair randomShooter(int[] control, Samples learningSamples) {
        ControlResultPair[] pairs = new ControlResultPair[64];        
        DFA dfa = new DFA(learningSamples);
        Guidance g = new IntGuidance(control);
        dfa = folder.doFold(dfa, g, 0);
        
        Random r = new Random(12345);
        double score = folder.getScore();
        
        pairs[0] = new ControlResultPair(score, control, 0, 0);
        
        for (int i = 1; i < pairs.length; i++) {
            pairs[i] = randomShooter(control, learningSamples, r);
        }
        Arrays.sort(pairs);
        System.out.println("heuristic score = " + score + ", shooter score = " + pairs[0].score);
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
    ControlResultPair doSearch(Samples samples, int minD, int maxD, ControlResultPairTable table) {

        ControlResultPair pop = null;
        ControlResultPair result = null;
        int[] control;

        samples.exportObject();
        table.exportObject();

        control = table.getFixControl();
        if (control == null) {
            control = new int[0];
        }
        System.out.print("Was fixed with depth " + control.length);   
        
        for (int i = minD + control.length; i <= maxD; i++) {
            
            maxDepth = i;

            pop = tryExtending(control.length, new ControlResultPair(Integer.MAX_VALUE, control, 0, 0),
                    i - minD, samples, table);
            
            if (result == null || result.score >= pop.score) {
                result = new ControlResultPair(pop);
            }
            if (i < maxD) {               
                int fixD = i - minD + 1;
                System.out.print("Fixing up until depth " + fixD + ":");
                control = new int[fixD];
                for (int j = 0; j < fixD; j++) {
                    control[j] = pop.control[j];
                    System.out.print(" " + control[j]);
                }
                System.out.println("");
                System.out.println("Best ControlResultPair: " + pop);
                pop.control = control;
                table.fix(pop);
            }
        }
        
        table.finish();
        
        return result;
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
        String reader = "abbadingo.AbbaDingoReader";
        boolean printInfo = false;
        int mindepth = 5;
        int maxdepth = -1;
        PickBlueStrategy strategy;
        String dumpfile = null;
        boolean maxDepthSpecified = false;

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
            } else if (args[i].equals("-dump")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-dump option requires filename");
                    System.exit(1);
                }
                dumpfile = args[i]; 
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
                folder = args[i];
            } else if (args[i].equals("-printInfo")) {
                printInfo = true;
            } else {
                logger.fatal("Unrecognized option: " + args[i]);
                System.exit(1);
            }
        }

        if (maxdepth < mindepth) {
            if (maxDepthSpecified) {
                logger.warn("maxdepth < mindepth, setting to mindepth");
            }
            maxdepth = mindepth;
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

        DFA dfa = new DFA(ls);
        BitSet[] conflicts = dfa.computeConflicts();

        Samples learningSamples = new Samples(symbols, iSamples, conflicts);

        BestBlue b = new BestBlue(f, mindepth);

        long initializationTime = System.currentTimeMillis();
        
        ControlResultPairTable table;
        
        if (dumpfile != null) {
            table = new ControlResultPairTable(dumpfile);
        } else {
            table = new ControlResultPairTable();
        }
        if (tableManager != null) {
            try {
                tableManager.master(table);
            } catch (IOException e) {
                // ignored
            }
        }

        ControlResultPair p = b.doSearch(learningSamples, mindepth, maxdepth, table);
        
        System.out.println("Best ControlResultPair: " + p);

        long searchTime = System.currentTimeMillis();

        DFA bestDFA = f.doFold(learningSamples, new IntGuidance(p.control), 0);
        
        if (dumpfile != null) {
            table.doWrite();
        }

        long endTime = System.currentTimeMillis();

        System.out.println("The winner DFA has MDL complexity "
                + bestDFA.getMDLComplexity() + " and " + bestDFA.nProductiveStates
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
