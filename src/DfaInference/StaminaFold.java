package DfaInference;

import sample.SampleReader;
import sample.SampleString;

/**
 * This class implements an evidence-driven state folder.
 * Evidence consists of the number of corresponding state labels that result
 * when doing a merge and making the resulting DFA deterministic by collapsing
 * states. Corresponding means: both states are rejecting or both states are
 * accepting.
 */
public class StaminaFold extends RedBlue implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    public double THRESHOLD = Configuration.THRESHOLD;

    boolean testMerge(State r, State b) {
        boolean foundMerge = false;

        if (r == null) {
            addChoice(Choice.getChoice(-1, b.getId(), 0, 0));
            return true;
        }

        double oldScore = getSimpleScore();

        UndoInfo u = dfa.treeMerge(r, b, true, redStates, numRedStates);


        if (! dfa.conflict) {
            double score = getSimpleScore() - oldScore - dfa.labelScore;
            // score -= b.getTraffic() + b.getxTraffic();
            
            // double score = -dfa.labelScore;

            if (dfa.chance < THRESHOLD) {
                score = -.1;
            }

            /*
            double sum = dfa.zSum;
            int count = dfa.sumCount;
            double score = 0.0;
            try {
                score = -DFA.normal.cumulativeProbability(sum/Math.sqrt(count));
            } catch(Throwable e) {
                // ignored
            }

            System.out.println("sum = " + sum + ", count = " + count + ", score = " + score);
            */
        
            // double score = -dfa.chance;
            /*
            if (r.isProductive() && ! r.isXProductive() && ! b.isProductive()) {
        	// score = -1e-8;
        	score = -.01;
            }
            
            if (dfa.chance > .1 && dfa.scoreCorrection == 0) {
        	score *= 10;
            }
            */
            
            // if (score < 1e-4) {
                // score *= Math.pow(1.25, Math.log10(dfa.chance));
            // }

            addChoice(Choice.getChoice(r.getId(), b.getId(), -dfa.chance,
                    score));
            foundMerge = true;
        }
        dfa.undoMerge(u);
        return foundMerge;
    }

    public double getSimpleScore() {
        // return dfa.getStaminaScore();
	// This either does not work properly yet, or gives unreasonable scores.
	// For now:
        /*
        if (Configuration.NEGATIVES) {
            return dfa.getNumEdges() + dfa.getNumAcceptingStates() + dfa.getNumStates() + dfa.getNumRejectingStates();
        }
        */
        return dfa.getNumProductiveEdges() + dfa.getNumAcceptingStates() + dfa.getNumProductiveStates();
    }

    public double getScore() {
        return getSimpleScore();
        // return dfa.getMDLComplexity();
        // This needs modification in DFA.java, because the weight of the endstates is wrong for MDL,
        // since a sentence in the sample may occur more than once in Stamina.
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

        if (! Configuration.USE_STAMINA) {
            System.err.println("Should set Stamina property!");
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


        StaminaFold m = new StaminaFold();
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
