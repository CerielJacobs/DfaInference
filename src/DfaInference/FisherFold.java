package DfaInference;

import org.apache.commons.math.special.Gamma;

import sample.SampleReader;
import sample.SampleString;

public class FisherFold extends RedBlue implements java.io.Serializable {

    private static final long serialVersionUID = 1L;
    
    // Threshold for promoting to red.
    private static final double PROBABILITY_THRESHOLD = .05;
    
    // Reject if FisherScore is below this.
    private static final double LIMIT = .001;

    boolean testMerge(State r, State b) {
        boolean foundMerge = false;

        if (r == null) {
            addChoice(Choice.getChoice(-1, b.getId(), dfa.getNumStates(), -PROBABILITY_THRESHOLD));
            return true;
        }

        UndoInfo u = dfa.treeMerge(r, b, true, redStates, numRedStates);
        if (! dfa.conflict) {
            // We have computed the sum of the logs of the P's for
            // all state merges. Now, apply Fisher's method:
            // X = -2 * sum
            // P = P(2*nmerges, X/2).
            double fisherScore = .01;
            int count = dfa.sumCount;
            double sum = dfa.chiSquareSum;
            if (NEGATIVES) {
                count += dfa.xSumCount;
                sum += dfa.xChiSquareSum;
            }
            if (count != 0) {
                // System.out.println("Scores for merge of " + r + " and "
                //      + b + ": count = " + count + ", sum = " + sum);
                if (! Double.isInfinite(sum)) {
                    try {
                        fisherScore = 1.0 - Gamma.regularizedGammaP(count, -sum);
                    } catch(Throwable e) {
                        // ignored
                    }
                }
            } else {
                fisherScore = .8;
            }

            if (fisherScore > LIMIT) {
                addChoice(Choice.getChoice(r.getId(), b.getId(), dfa.getNumStates(),
                        -fisherScore));
                foundMerge = true;
            }
        }
        dfa.undoMerge(u);
        return foundMerge;
    }

    double getScore() {
        return dfa.getNumStates();
    }

    /**
     * Main program for Evidence driven state merging.
     * @param args the program arguments.
     */
    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        String reader = "abbadingo.AbbaDingoReader";

        // Print Java version and system.
        System.out.println(Helpers.getPlatformVersion() + "\n\n");
        
        if (! Configuration.USE_CHISQUARE) {
            System.err.println("Should set ChiSquare property!");
            System.exit(1);
        }

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-input")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-input option requires filename");
                    System.exit(1);
                }
                learningSetFile = args[i];
            } else if (args[i].equals("-output")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-output option requires outputfile");
                    System.exit(1);
                }
                outputfile = args[i];
            } else if (args[i].equals("-reader")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-reader option requires class name");
                    System.exit(1);
                }
                reader = args[i];
            } else {
                logger.fatal("Unrecognized option: " + args[i]);
                System.exit(1);
            }
        }

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
        int[][] learningSamples = symbols.convert2learn(samples);


        FisherFold m = new FisherFold();
        m.printInfo = true;
        logger.info("Starting fold ...");
        DFA bestDFA = m.doFold(new Samples(symbols, learningSamples, null),
                new Guidance(), 0);

        if (logger.isInfoEnabled()) {
            logger.info("The winner DFA has complexity "
                    + bestDFA.getMDLComplexity() + "\n" + bestDFA);
        }

        bestDFA.write(outputfile);
    }
}
