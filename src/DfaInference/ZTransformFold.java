package DfaInference;

import java.io.IOException;

import sample.Samples;

public class ZTransformFold extends RedBlue implements java.io.Serializable {

    private static final long serialVersionUID = 1L;
    
    // Threshold for promoting to red.
    private static final double PROBABILITY_THRESHOLD = .05;

    // Reject if Score is below this.
    private static final double LIMIT = .001;
    
    boolean testMerge(State r, State b) {
        boolean foundMerge = false;

        if (r == null) {
            addChoice(Choice.getChoice(-1, b.getId(), dfa.getNumStates(), -PROBABILITY_THRESHOLD));
            return true;
        }

        UndoInfo u = dfa.treeMerge(r, b, true, redStates, numRedStates);
        if (! dfa.conflict) {
            double score = .01;
            double sum = dfa.zSum;
            int count = dfa.sumCount;
            if (NEGATIVES) {
                sum += dfa.xZSum;
                count += dfa.xSumCount;
            }
            if (count != 0) {
                try {
                    score = DFA.normal.cumulativeProbability(sum/Math.sqrt(count));
                } catch(Throwable e) {
                    // ignored
                }
            } else {
                score = .8;
            }
            if (score > LIMIT) {
                addChoice(Choice.getChoice(r.getId(), b.getId(), dfa.getNumStates(),
                        -score));
                foundMerge = true;
            }
        }
        dfa.undoMerge(u);
        return foundMerge;
    }

    public double getScore() {
        return dfa.getNumStates();
    }

    /**
     * Main program for Evidence driven state merging.
     * @param args the program arguments.
     */
    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        String reader = "abbadingo.AbbaDingoIO";

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
        
        Samples learningSamples = null;
        try {
	    learningSamples = new Samples(reader, learningSetFile);
	} catch (IOException e) {
	    logger.error("got IO exception", e);
	    System.exit(1);
	}

        ZTransformFold m = new ZTransformFold();
        m.printInfo = true;
        logger.info("Starting fold ...");
        DFA bestDFA = m.doFold(learningSamples, new Guidance(), 0, 0);

        if (logger.isInfoEnabled()) {
            logger.info("The winner DFA has complexity "
                    + bestDFA.getMDLComplexity() + "\n" + bestDFA);
        }

        bestDFA.write(outputfile);
    }
}
