package DfaInference;

import java.util.Arrays;
import java.util.BitSet;

import org.apache.log4j.Logger;

import abbadingo.AbbaDingoReader;
import abbadingo.AbbaDingoString;

public class BeamFold {

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(BeamFold.class.getName());

    /** Class representing a score/controlstring pair. */
    private static class Pair implements Comparable {
        double score;
        boolean[] control;

        public Pair(double score, boolean[] control) {
            this.score = score;
            this.control = control;
        }

        public int compareTo(Object o) {
            Pair p = (Pair) o;
            if (score == p.score) {
                return 0;
            }
            if (score > p.score) {
                return 1;
            }
            return -1;
        }
    };

    Samples learningSamples;
    double bestDfaScore;
    double bestScore;
    DFA bestDFA;
    RedBlue folder;

    public BeamFold(Samples samples, RedBlue f) {
        learningSamples = samples;
        bestScore = Double.MAX_VALUE;
        bestDfaScore = Integer.MAX_VALUE;
        bestDFA = null;
        folder = f;
    }

    Pair[] tryExtending(Pair[] l, int maxlen) {
        Pair[] result = new Pair[2*l.length];
        for (int i = 0; i < l.length; i++) {
            Pair p = l[i];
            boolean[] control1 = new boolean[p.control.length+1];
            boolean[] control2 = new boolean[p.control.length+1];
            for (int j = 0; j < p.control.length; j++) {
                control1[j] = p.control[j];
                control2[j] = p.control[j];
            }
            control1[p.control.length] = true;
            control2[p.control.length] = false;
            result[2*i] = new Pair(p.score, control1);
            result[2*i+1]= new Pair(tryControl(control2, maxlen), control2);
        }
        return result;
    }

    double tryControl(boolean[] control, int maxlen) {
        if (control.length < maxlen) {
            boolean[] control1 = new boolean[maxlen];
            for (int i = 0; i < control.length; i++) {
                control1[i] = control[i];
            }
            for (int i = control.length; i < maxlen; i++) {
                control1[i] = true;
            }
            control = control1;
        }
        DFA dfa = folder.doFold(learningSamples, new BitGuidance(control), 0);
        if (dfa != null) {
            double score = folder.getScore();
            double dfascore = dfa.getDFAComplexity();
            if (score < bestScore || (score == bestScore && dfascore < bestDfaScore)) {
                bestDFA = dfa;
                bestDfaScore = dfascore;
                bestScore = score;
            }
            return score;
        }
        return Integer.MAX_VALUE;
    }

    void doSearch(int popSize, int maxTries) {
        Pair[] pop = new Pair[1];
        boolean[] control = new boolean[0];
        int nTries = 1;
        int maxlen = 1;
        int sz = 1;
        while (nTries < maxTries) {
            nTries = nTries + sz;
            sz += sz;
            if (sz > popSize) sz = popSize;
            maxlen++;
        }

        nTries = 1;
        pop[0] = new Pair(tryControl(control, maxlen), control);
        while (nTries < maxTries) {
            nTries = nTries + pop.length;
            pop = tryExtending(pop, maxlen);
            Arrays.sort(pop);
            if (pop.length > popSize) {
                Pair[] p = new Pair[popSize];
                for (int i = 0; i < popSize; i++) {
                    p[i] = pop[i];
                }
                pop = p;
            }
        }
    }

    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        String folder = "DfaInference.EdFold";
        int maxX = 5;

        System.out.println(Helpers.getPlatformVersion() + "\n\n");

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-maxX")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-maxX option requires number");
                    System.exit(1);
                }
                maxX = (new Integer(args[i])).intValue();
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
            } else {
                logger.fatal("Unrecognized option: " + args[i]);
                System.exit(1);
            }
        }

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

        Symbols symbols = new Symbols();
        int[][] iSamples = symbols.convert2learn(samples);

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

        Samples sl = new Samples(symbols, iSamples, null);
        DFA dfa = new DFA(sl);
        BitSet[] conflicts = dfa.computeConflicts();
        Samples learningSamples = new Samples(symbols, iSamples, conflicts);

        BeamFold b = new BeamFold(learningSamples, f);

        b.doSearch(1 << maxX, (1+maxX) << maxX);

        DFA bestDFA = b.bestDFA;

        if (logger.isInfoEnabled()) {
            logger.info("The winner DFA has complexity " + bestDFA.getMDLComplexity());
            logger.info("and the winner is:\n" + bestDFA);
        }

        bestDFA.write(outputfile);
    }
}
