package DfaInference;

import sample.SampleString;
import abbadingo.AbbaDingoReader;

public class MDLFold extends RedBlue implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    double getScore() {
        return dfa.getMDLComplexity();
    }

    boolean testMerge(State r, State b) {
        boolean foundMerge = false;

        if (r == null) {
            addChoice(Choice.getChoice(-1, b.getId(), dfa.getNumStates(), getScore()));
            return true;
        }
        UndoInfo u = dfa.treeMerge(r, b, true, redStates, numRedStates);
        if (! dfa.conflict) {
            double sc = getScore();
            double dfascore = dfa.getDFAComplexity();

            // As long as the sample part of the score is less than the best
            // score found sofar, there is hope ...
            if (pickBlueStrategy != null || sc-dfascore < bestScore) {
                addChoice(Choice.getChoice(r.getId(), b.getId(), dfa.getNumStates(), sc));
                foundMerge = true;
            }
        }
        dfa.undoMerge(u);
        return foundMerge;
    }

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

        SampleString[] samples = null;
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


        MDLFold m = new MDLFold();
        m.printInfo = true;
        DFA bestDFA = m.doFold(new Samples(symbols, learningSamples, null),
                new Guidance(), 0);

        if (logger.isInfoEnabled()) {
            logger.info("The winner DFA has complexity " + bestDFA.getMDLComplexity());
            logger.info("and the winner is:\n" + bestDFA);
        }

        bestDFA.write(outputfile);
    }
}

