package DfaInference;

import abbadingo.AbbaDingoReader;
import abbadingo.AbbaDingoString;

/**
 * This class implements an evidence-driven state folder.
 * Evidence consists of the number of corresponding state labels that result
 * when doing a merge and making the resulting DFA deterministic by collapsing
 * states. Corresponding means: both states are rejecting or both states are
 * accepting.
 */
public class EdFold extends RedBlue implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    boolean testMerge(State r, State b) {
        boolean foundMerge = false;

        if (r == null) {
            addChoice(Choice.getChoice(-1, b.id, dfa.getNumStates(), 0));
            return true;
        }

        UndoInfo u = dfa.treeMerge(r, b, true, redStates, numRedStates);
        if (! dfa.conflict) {
            addChoice(Choice.getChoice(r.id, b.id, dfa.getNumStates(),
                        -dfa.labelScore));
            foundMerge = true;
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

        // Print Java version and system.
        System.out.println(Helpers.getPlatformVersion() + "\n\n");

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

        int[][] learningSamples = Symbols.convert2learn(samples);


        EdFold m = new EdFold();
        m.printInfo = true;
        logger.info("Starting fold ...");
        DFA bestDFA = m.doFold(new Samples(learningSamples, null),
                new Guidance(), 0);

        if (logger.isInfoEnabled()) {
            logger.info("The winner DFA has complexity "
                    + bestDFA.getMDLComplexity() + "\n" + bestDFA);
        }

        bestDFA.write(outputfile);
    }
}
