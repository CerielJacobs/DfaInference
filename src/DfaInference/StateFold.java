package DfaInference;

import abbadingo.AbbaDingoReader;
import abbadingo.AbbaDingoString;

/**
 * This state folder implements a search strategy that first selects the merge
 * that gives the largest reduction in number of states.
 */
public class StateFold extends RedBlue implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    boolean testMerge(State r, State b) {

        if (r == null) {
            addChoice(Choice.getChoice(-1, b.id, dfa.getNumStates(),
                        dfa.getNumStates()));
            return true;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("testMerge " + r.id + ", " + b.id);
        }
        try {
            UndoInfo u = dfa.treeMerge(r, b, true, redStates, numRedStates);
            addChoice(Choice.getChoice(r.id, b.id, dfa.getNumStates(),
                        dfa.getNumStates()));
            dfa.undoMerge(u);
            return true;
        } catch(Throwable e) {
            return false;
        }
    }

    double getScore() {
        return dfa.getNumStates();
    }

    /**
     * Main program for state merging guided by largest reduction in
     * number of states.
     * @param args the program arguments.
     */
    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";

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

        Symbols symbols = new Symbols();
        int[][] learningSamples = symbols.convert2learn(samples);

        StateFold m = new StateFold();
        DFA bestDFA = m.doFold(new Samples(symbols, learningSamples, null),
                new Guidance(), 0);

        if (logger.isInfoEnabled()) {
            logger.info("The winner DFA has complexity "
                    + bestDFA.getMDLComplexity() + "\n" + bestDFA);
        }

        bestDFA.write(outputfile);
    }
}

