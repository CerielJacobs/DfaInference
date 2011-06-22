package DfaInference;

import java.io.IOException;

import sample.Samples;

public class StaminaFold extends RedBlue implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    public double THRESHOLD = Configuration.THRESHOLD;
    
    // public double SQ = Math.sqrt(THRESHOLD);

    boolean testMerge(State r, State b) {
        boolean foundMerge = false;

        if (r == null) {
            addChoice(Choice.getChoice(-1, b.getId(), -THRESHOLD, 0));
            // addChoice(Choice.getChoice(-1, b.getId(), 0, b.getDepth()));
            return true;
        }

        // double oldScore = getSimpleScore(dfa);

        UndoInfo u = dfa.treeMerge(r, b, true, redStates, numRedStates);


        if (! dfa.conflict && dfa.chance >= THRESHOLD) {
            /* System.out.println("Red = " + r.getId() + ", blue = " + b.getId()
                    + ", chance = " + dfa.chance + ", penalty = " + dfa.staminaPenalty
                    + ", similarStates = " + dfa.similarStates + ", labelScore = " + dfa.labelScore);
            */
            
            // double score = -dfa.labelScore - dfa.similarStates + dfa.staminaPenalty;
            // double score = -b.getDepth();
            // score -= b.getTraffic() + b.getxTraffic();
            /*
            if (chance < THRESHOLD) {
                score = 1;
            }
             */
            // double score = - (dfa.similarStates + dfa.labelScore - dfa.staminaPenalty /* + (oldScore - getSimpleScore(dfa)) */);

            /*
            if (! r.isProductive() && ! b.isProductive()) {
                // Penalty on score.
                score = 1;
            }
            */

            // double score = dfa.staminaPenalty - dfa.labelScore;
            double score = -dfa.labelScore;
            /*
            if (dfa.chance > SQ) {
                score *= Math.pow(1.5, Math.log10(dfa.chance));
            } else {
                score *= Math.pow(2.0, Math.log10(dfa.chance));
            }
            */
            
            // addChoice(Choice.getChoice(r.getId(), b.getId(), score,
            //         b.getDepth()));
            addChoice(Choice.getChoice(r.getId(), b.getId(), -dfa.chance, score));

            foundMerge = true;
        }
        dfa.undoMerge(u);
        return foundMerge;
    }

    public int getSimpleScore(DFA dfa) {
        // return dfa.getStaminaScore();
	// This either does not work properly yet, or gives unreasonable scores.
	// For now:
        
        // return dfa.getNumStates();
        // if (Configuration.NEGATIVES) {
            // return dfa.getNumEdges() + dfa.getNumAcceptingStates() + dfa.getNumRejectingStates();
        // }
        
        return dfa.getNumProductiveEdges() + dfa.getNumAcceptingStates() + dfa.getNumProductiveStates();
    }

    public double getScore() {
        // return dfa.getMDLComplexity();
        return getSimpleScore(dfa);
    }

    /**
     * Main program for Evidence driven state merging.
     * @param args the program arguments.
     */
    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        String reader = "abbadingo.AbbaDingoIO";
        boolean full = false;

        // Print Java version and system.
        System.out.println(Helpers.getPlatformVersion() + "\n\n");

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-full")) {
        	full = true;
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

        StaminaFold m = new StaminaFold();
        m.printInfo = true;
        
        DFA bestDFA;
        if (full) {
            DFA dfa = new DFA(learningSamples);
            m.dfa = dfa;
            bestDFA = m.fullBlownLearn();
        } else {            
            logger.info("Starting fold ...");
            bestDFA = m.doFold(learningSamples, new Guidance(), 0, 0);
        }
        bestDFA.write(outputfile);
    }
    
    public DFA fullBlownLearn() {
	int attempt = 0;
	for (;;) {
	    int bestGain = 0;
	    DFA best = null;
	    int nStates = dfa.getNumStates();
            int stop = nStates < 256 ? nStates : 256;

	    logger.info("Full blown learn for " + nStates + " states...");
	    for (int i = 0; i < stop; i++) {
		    for (int j = i + 1; j < stop; j++) {
			    DFA merge = new DFA(dfa);
			    System.out.println("Trying merge between state " + i + " and " + j);
			    try {
				merge.fullMerge(merge.getState(i), merge.getState(j));
			    } catch (ConflictingMerge e) {
				System.out.println("Gives conflict");
				continue;
			    }
			    int gain = getSimpleScore(dfa) - getSimpleScore(merge);
			    System.out.println("Gives gain " + gain + " and chance " + merge.chance);
			    if (gain > bestGain) {
				bestGain = gain;
				best = merge;
			    }
		    }
	    }
	    attempt++;

	    if (bestGain > 0) {
		logger.info("Found a better DFA with score " + bestGain);
		dfa = best;
	    } else {
		logger.info("exit full blown learn at attempt=" + attempt);
		break;
	    }
	}
	return dfa;
    }
}
