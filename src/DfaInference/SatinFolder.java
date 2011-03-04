package DfaInference;

import ibis.satin.SatinObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

import org.apache.log4j.Logger;

import sample.Samples;

/**
 * This class implements a "top N" search strategy. The current best N
 * DFAs are maintained and searched one merge step deeper. These merge results
 * are again sorted, and the best N are taken, et cetera.
 * This is a Satin application, which means that the search process can be
 * run on multiple hosts.
 *
 * The search process is parameterized by the heuristic, which can be any
 * extension of the {@link DfaInference.RedBlue} class.
 *
 * The search can optionally be randomized somewhat, by having part of the
 * space for "best N" occupied by random branches instead of the best. The idea
 * here is that the first few steps are very important, and when the learning
 * sample is thin the simple heuristics don't do much to discriminate between
 * "bad" choices and "good" choices.
 */
public class SatinFolder extends SatinObject implements SatinFolderInterface, Configuration {

    private static final long serialVersionUID = 1L;
    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(SatinFolder.class.getName());

    /** The heuristic used. */
    private RedBlue folder;

    /** For random guidance. */
    private transient Random random = null;

    /**
     * When using random guidance, keep the best <code>bestf</code> in the
     * top N, and use the rest of the space for random branches.
     */
    private transient int bestf;

    /** Dump file. */
    private transient File dumpfile = null;

    /** Current depth in the search. */
    private transient int currentDepth;

    private transient boolean masterWorker;

    /**
     * Constructor.
     * @param f the heuristic to be used.
     * @param dump name of a dump file.
     * @param random whether to use random branches.
     * @param bestf which part not to use for random branches.
     * @param mw master-worker?
     */
    public SatinFolder(RedBlue f, String dump, boolean random, int bestf,
            boolean mw) {
        folder = f;
        if (random) {
            this.random = new Random(1);
        }
        if (dump != null) {
            dumpfile = new File(dump);
        }
        this.bestf = bestf;
        this.masterWorker = mw;
    }

    public double buildPair(ControlResultPair p, Samples learningSamples) {
        if (logger.isDebugEnabled()) {
            logger.debug("buildPair: " + p);
        }
        return tryControl(p.control, learningSamples);
    }

    public ControlResultPair[] examineChoice(int[] pcontrol, int windex,
            int percentage, Samples learningSamples) {

        DFA dfa = new DFA(learningSamples);
        Guidance g = new IntGuidance(pcontrol);
        Choice[] choice = folder.getOptions(dfa, g, percentage);

        logger.info("choice length = " + choice.length);

        ControlResultPair[] result = new ControlResultPair[choice.length];

        for (int k = 0; k < choice.length; k++) {
            int[] control = new int[pcontrol.length+1];

            for (int j = 0; j < pcontrol.length; j++) {
                control[j] = pcontrol[j];
            }
            control[pcontrol.length] = k;
            result[k] = new ControlResultPair(-1, control, windex,
                    control[pcontrol.length]);
            logger.debug("Spawning " + result[k]);
            result[k].score = buildPair(result[k], learningSamples);
        }
        sync();
        return result;
    }

    /**
     * Extends the search list one more step by expanding the entries in
     * the specified list. All possibilities for this one step are tried.
     * @param l the current search list.
     * @param percentage a percentage for the folder to use in early cut-off.
     * @param window cut-off size for the new list.
     * @param learningSamples the samples to learn from.
     * @return the new search list.
     */
    ControlResultPair[] tryExtending(ControlResultPair[] l, int percentage,
            int scorePercentage, int window, Samples learningSamples) {
        ControlResultPair[][] pairs = new ControlResultPair[l.length][];

        if (masterWorker) {
            logger.warn("Starting spawns");
        }
        for (int i = 0; i < l.length; i++) {
            if (! masterWorker) {
                pairs[i] = examineChoice(l[i].control, i, percentage,
                        learningSamples);
            }
            else {
                DFA dfa = new DFA(learningSamples);
                ControlResultPair p = l[i];
                Guidance g = new IntGuidance(p.control);
                Choice[] choice = folder.getOptions(dfa, g, percentage);
                logger.info("choice length = " + choice.length);

                int count = choice.length;

                pairs[i] = new ControlResultPair[count];

                for (int k = 0; k < count; k++) {
                    int[] control = new int[p.control.length+1];

                    for (int j = 0; j < p.control.length; j++) {
                        control[j] = p.control[j];
                    }
                    control[p.control.length] = k;
                    pairs[i][k] = new ControlResultPair(-1, control, i, control[p.control.length]);
                    logger.debug("Spawning " + pairs[i][k]);
                    pairs[i][k].score = buildPair(pairs[i][k], learningSamples);
                }
            }
        }
        if (masterWorker) {
            logger.warn("Spawns done");
        }
        sync(); // wait for all spawned jobs.
        int sz = 0;
        for (int i = 0; i < l.length; i++) {
            sz += pairs[i].length;
        }
        ControlResultPair[] result = new ControlResultPair[sz];
        int k = 0;
        for (int i = 0; i < l.length; i++) {
            for (int j = 0; j < pairs[i].length; j++) {
                result[k++] = pairs[i][j];
            }
        }

        Arrays.sort(result);

        if (sz == 0) {
            return result;
        }

        sz = Math.min(window, sz);

        if (scorePercentage >= 0) {
            double score = result[0].score * (1 + scorePercentage / 100.0);
            for (int i = 1; i < sz; i++) {
                if (result[i].score > score) {
                    sz = i;
                }
            }
        }

        if (sz >= result.length) {
            return result;
        }
        l = new ControlResultPair[sz];
        if (random != null && sz > bestf) {
            // Fill first bestf slots with best ones.
            for (int i = 0; i < bestf; i++) {
                l[i] = result[i];
            }
            // Fill the other part with random choices.
            for (int i = bestf; i < sz; i++) {
                int n = random.nextInt(result.length - i) + i;
                l[i] = result[n];
                // Unless we picked i, the entry there should be part of
                // the next choice.
                if (n != i) {
                    result[n] = result[i];
                }
            }
        } else {
            for (int i = 0; i < sz; i++) {
                l[i] = result[i];
            }
        }
        return l;
    }

    /**
     * Uses the specified guidance and the folder's heuristic to compress
     * the DFA and determine the resulting score.
     * @param control the specified guidance
     * @param learningSamples the samples to learn from.
     * @return the resulting score.
     */
    double tryControl(int[] control, Samples learningSamples) {
        folder.doFold(learningSamples, new IntGuidance(control), 0);
        double score = folder.getScore();
        /* TODO: look at this.
        if (maxSteps != 0) {
            // Don't just look at the score, also take into account the
            // number of red states. Higher numbers of red states are worse.
            return (500.0 + folder.numRedStates) * score / 500.0;
        }
        */
        return score;
    }

    /**
     * Overall search process driver.
     * Attempts to restart from a dump if a dump file is specified.
     * @param window    the number "N" in "top N".
     * @param depth     the depth of the search (after which the heuristic takes
     *  over).
     * @param percentage percentage used in the early-cutoff.
     * @param scorePercentage percentage indicating a range from the best score
     *    to include.
     * @param samples the samples to learn from.
     */
    ControlResultPair doSearch(int window, int depth, int percentage, 
            int scorePercentage, Samples samples) {
        ControlResultPair[] pop;

        samples.exportObject();

        if (depth == 0) {
            pop = new ControlResultPair[1];
            int[] control = new int[0];
            pop[0] = new ControlResultPair(tryControl(control, samples),
                    control, 0, 0);
        }
        else {
            pop = readDump();
            for (int i = currentDepth+1; i < depth; i++) {
                pop = tryExtending(pop, percentage, scorePercentage,
                        window, samples);
                currentDepth = i;
                dump(pop);
            }
        }
        return pop[0];
    }

    /**
     * Dumps the current search state to a file, from which the search
     * can be restarted. If no dump file is specified, only some
     * debugging information is printed.
     * @param pop the current search state.
     */
    private void dump(ControlResultPair[] pop) {
        if (dumpfile != null) {
            try {
                File temp = File.createTempFile("dfa", "dmp", new File("."));
                BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
                bw.write("" + currentDepth + "\n");
                bw.write("" + pop.length + "\n");
                for (int i = 0; i < pop.length; i++) {
                    pop[i].write(bw);
                }
                bw.close();
                if (! temp.renameTo(dumpfile)) {
                    throw new IOException("rename failed");
                }
            } catch(Exception e) {
                logger.warn("Could not dump ...", e);
            }
        }
        if (logger.isInfoEnabled()) {
            String str = "";
            for (int i = 0; i < pop.length; i++) {
                str += " " + pop[i].score
                    + "(" + pop[i].getFromChoiceIndex() + ")";
            }
            logger.info("dump at depth " + currentDepth + ", scores:" + str);
        }
    }

    /**
     * Attempts to restart the search from a dump.
     * Returns the search state at the time of the dump.
     * If the dump file could not be opened or was not specified,
     * an initial search states is returned.
     * @return a search state from which to restart the search.
     */
    private ControlResultPair[] readDump() {
        ControlResultPair[] pop;
        if (dumpfile != null) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(dumpfile));
                String line;

                line = br.readLine();
                int d = (new Integer(line)).intValue();
                currentDepth = d;
                line = br.readLine();
                int poplen = (new Integer(line)).intValue();
                pop = new ControlResultPair[poplen];

                for (int i = 0; i < pop.length; i++) {
                    pop[i] = new ControlResultPair(br);
                }
                br.close();
                return pop;
            } catch(Exception e) {
                logger.warn("Could not read dump ...", e);
            }
        }
        pop = new ControlResultPair[1];
        pop[0] = new ControlResultPair(-1, new int[0], 0, 0);
        currentDepth = -1;

        return pop;
    }

    /**
     * Main program of the Satin "top N" searcher.
     * @param args the program arguments.
     */
    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        String folder = "DfaInference.EdFold";
        String reader = "abbadingo.AbbaDingoIO";
        String dump = null;
        int window = 1;
        int depth = 0;
        int percentage = 100;
        int scorePercentage = -1;
        int bestf = 32;
        boolean random = false;
        boolean doFold = true;
        boolean printInfo = false;
        boolean mw = true;

        long startTime = System.currentTimeMillis();

        System.out.println(Helpers.getPlatformVersion() + "\n\n");

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-window")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-window option requires number");
                    System.exit(1);
                }
                window = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-bestf")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-bestf option requires number");
                    System.exit(1);
                }
                bestf = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-percentage")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-percentage option requires number");
                    System.exit(1);
                }
                percentage = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-scorePercentage")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-scorePercentage option requires number");
                    System.exit(1);
                }
                scorePercentage = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-depth")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-depth option requires number");
                    System.exit(1);
                }
                depth = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-input")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-input option requires filename");
                    System.exit(1);
                }
                learningSetFile = args[i];
            } else if (args[i].equals("-dump")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-dump option requires filename");
                    System.exit(1);
                }
                dump = args[i];
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
                folder = args[i];
            } else if (args[i].equals("-random")) {
                random = true;
            } else if (args[i].equals("-mw")) {
                mw = true;
            } else if (args[i].equals("-no-mw")) {
                mw = false;
            } else if (args[i].equals("-no-fold")) {
                doFold = false;
            } else if (args[i].equals("-printInfo")) {
                printInfo = true;
            } else {
                logger.fatal("Unrecognized option: " + args[i]);
                System.exit(1);
            }
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
            f.printInfo = printInfo;
        } catch(Exception e) {
            throw new Error("Could not instantiate " + folder, e);
        }
        
        Samples learningSamples = null;
        try {
	    learningSamples = new Samples(reader, learningSetFile);
	} catch (IOException e) {
	    logger.error("got IO exception", e);
	    System.exit(1);
	}
	
        SatinFolder b = new SatinFolder(f, dump, random, bestf, mw);

        long initializationTime = System.currentTimeMillis();

        BitSet[] conflicts = null;
        // DFA dfa = new DFA(iSamples);
        // conflicts = dfa.computeConflicts();
        learningSamples.setConflicts(conflicts);

        ControlResultPair p = b.doSearch(window, depth, percentage,
                scorePercentage, learningSamples);

        if (doFold) {
            long searchTime = System.currentTimeMillis();

            f.printInfo = true;

            DFA bestDFA = f.doFold(learningSamples, new IntGuidance(p.control), 0);

            long endTime = System.currentTimeMillis();

            System.out.println("The winner DFA has MDL complexity "
                    + bestDFA.getMDLComplexity()
                    + ", DFA complexity " + bestDFA.getDFAComplexity()
                    + " and " + bestDFA.nProductiveStates + " states");
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
}
