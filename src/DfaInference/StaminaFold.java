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

    boolean testMerge(State r, State b) {
        boolean foundMerge = false;

        if (r == null) {
            addChoice(Choice.getChoice(-1, b.getId(), dfa.getNumStates(), 0));
            return true;
        }

        UndoInfo u = dfa.treeMerge(r, b, true, redStates, numRedStates);
        if (! dfa.conflict && dfa.chance > 1e-20) {
            double score = -dfa.labelScore;
            if (dfa.chance < 1e-9) {
                score /= (1 + 4 * ((1e-9 - dfa.chance) * 1e9));
            }
            /*
            if (dfa.chance < 1e-6) {
                System.out.println("r = " + r.getId() + ", b = " + b.getId()
                        + " chance = " + dfa.chance
                        + ", score reduced from " + dfa.labelScore + " to " + (-score));
            }
            */
            addChoice(Choice.getChoice(r.getId(), b.getId(), (int) getScore(),
                    score));
            foundMerge = true;
        }
        dfa.undoMerge(u);
        return foundMerge;
    }

    public double getScore() {
        // return dfa.getStaminaScore();
	// This either does not work properly yet, or gives unreasonable scores.
	// For now:
	return dfa.getNumProductiveEdges() + + dfa.getNumAcceptingStates() + dfa.getNumProductiveStates();
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
