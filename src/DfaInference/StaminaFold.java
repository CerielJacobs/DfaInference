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
    
    public double SQ = Math.sqrt(THRESHOLD);

    boolean testMerge(State r, State b) {
        boolean foundMerge = false;

        if (r == null) {
            addChoice(Choice.getChoice(-1, b.getId(), -THRESHOLD, 0));
            return true;
        }

        double oldScore = getSimpleScore(dfa);

        UndoInfo u = dfa.treeMerge(r, b, true, redStates, numRedStates);


        if (! dfa.conflict) {
            double chance = dfa.chance;
            // double sum = dfa.zSum;
            // int count = dfa.sumCount;
            // double avgChance = sum / count;
            
            /*
            try {
                chance = DFA.normal.cumulativeProbability(sum/Math.sqrt(count));
                if (chance < 0) {
                    chance = 0;
                }
                if (chance > 1) {
                    chance = 1;
                }
            } catch(Throwable e) {
                chance = 0;
            }
            */
            
            // chance = avgChance;
            // System.out.println("sum = " + sum + ", count = " + count + ", chance = " + chance);
            
                        /*
            if (dfa.staminaPenalty != 0) {
        	score /= dfa.staminaPenalty;
            }
            */
            // double score = -dfa.labelScore;
            // score -= b.getTraffic() + b.getxTraffic();
            /*
            if (chance < THRESHOLD) {
                score = 1;
            }
             */
           if (chance > THRESHOLD) {
        	double score = - (dfa.similarStates + dfa.labelScore + (oldScore - getSimpleScore(dfa)));
                if (chance > SQ) {
                    score *= Math.pow(1.5, Math.log10(chance));
                } else {
                    score *= Math.pow(2.0, Math.log10(chance));
                }
                
        	addChoice(Choice.getChoice(r.getId(), b.getId(), -chance,
        		score));
        	foundMerge = true;
            }
        }
        dfa.undoMerge(u);
        return foundMerge;
    }

    public int getSimpleScore(DFA dfa) {
        // return dfa.getStaminaScore();
	// This either does not work properly yet, or gives unreasonable scores.
	// For now:
        
        // return dfa.getNumStates();
        if (Configuration.NEGATIVES) {
            return dfa.getNumEdges() + dfa.getNumAcceptingStates() + dfa.getNumRejectingStates();
        }
        
        return dfa.getNumProductiveEdges() + dfa.getNumAcceptingStates();
    }

    public double getScore() {
        return getSimpleScore(dfa);
    }

    /**
     * Main program for Evidence driven state merging.
     * @param args the program arguments.
     */
    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        String reader = "abbadingo.AbbaDingoReader";
        boolean full = false;

        // Print Java version and system.
        System.out.println(Helpers.getPlatformVersion() + "\n\n");

        if (! Configuration.USE_STAMINA) {
            System.err.println("Should set Stamina property!");
            System.exit(1);
        }
        
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
        
        DFA bestDFA;
        if (full) {
            DFA dfa = new DFA(new Samples(symbols, learningSamples, null));
            m.dfa = dfa;
            bestDFA = m.fullBlownLearn();
        } else {            
            logger.info("Starting fold ...");
            bestDFA = m.doFold(new Samples(symbols, learningSamples, null),
        	    new Guidance(), 0);
        }
        bestDFA.write(outputfile);
    }
    
    public DFA fullBlownLearn() {
	int attempt = 0;
	for (;;) {
	    int bestGain = 0;
	    DFA best = null;
	    int nStates = dfa.getNumStates();

	    logger.info("Full blown learn for " + nStates + " states...");
	    for (int i = 0; i < nStates; i++) {
		if (dfa.getState(i).getDepth() < 10) {
		    for (int j = i + 1; j < nStates; j++) {
			if (dfa.getState(j).getDepth() < 10) {
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
