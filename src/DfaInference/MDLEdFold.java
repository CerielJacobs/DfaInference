package DfaInference;

import java.io.IOException;

import sample.Samples;

public class MDLEdFold extends RedBlue implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    public double getScore() {
        return dfa.getMDLComplexity();
    }

    boolean testMerge(State r, State b) {
        boolean foundMerge = false;

        if (r == null) {
            addChoice(Choice.getChoice(-1, b.getId(), dfa.getNumStates(), 0));
            return true;
        }
        UndoInfo u = dfa.treeMerge(r, b, true, redStates, numRedStates);
        if (! dfa.conflict) {
            addChoice(Choice.getChoice(r.getId(), b.getId(), dfa.getNumStates(), -dfa.labelScore));
            foundMerge = true;
        }
        dfa.undoMerge(u);
        return foundMerge;
    }

    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        String reader = "abbadingo.AbbaDingoIO";

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

	MDLEdFold m = new MDLEdFold();
        m.printInfo = true;
        DFA bestDFA = m.doFold(learningSamples, new Guidance(), 0, 0);

        if (logger.isInfoEnabled()) {
            logger.info("The winner DFA has complexity " + bestDFA.getMDLComplexity());
            logger.info("and the winner is:\n" + bestDFA);
        }

        bestDFA.write(outputfile);
    }
}

