package DfaInference;

import java.util.ArrayList;
import java.util.HashSet;

import org.apache.log4j.Logger;

import abbadingo.AbbaDingoReader;
import abbadingo.AbbaDingoString;

public class ExhSearch implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(ExhSearch.class.getName());

    DFA dfa;
    transient DFA bestDFA;
    transient double bestScore;

    public ExhSearch(DFA dfa) {
        this.dfa = dfa;
        bestDFA = dfa;
        bestScore = dfa.getMDLComplexity();
    }

    private ArrayList<State> getBlueStates(HashSet<State> redStates) {
        ArrayList<State> l = new ArrayList<State>();
        for (State red : redStates) {
            for (int j = 0; j < red.children.length; j++) {
                State s = red.children[j];
                if (s != null && ! redStates.contains(s)) {
                    l.add(s);
                }
            }
        }
        return l;
    }

    /**
     * Picks a blue state according to the heuristic described below.
     * We pick the blue state which has the least number
     * of possibilities. This gives the most constrained search.
     * @param blueStates the list of blue states
     * @param redStates the list of red states
     * @return the blue state selected.
     */
    private State pickBlueState(ArrayList<State> blueStates, HashSet<State> redStates) {
        if (logger.isInfoEnabled()) {
            logger.info("pickBlueStates: blue size = " + blueStates.size()
                    + ", red size = " + redStates.size());
        }

        if (blueStates.size() == 1) {
            return blueStates.get(0);
        }

        // int score = dfa.getMDLComplexity();

        State bestBlue = null;
        int bestCount = Integer.MAX_VALUE;
        State[] reds = redStates.toArray(new State[redStates.size()]);

        for (int i = 0; i < blueStates.size(); i++) {
            State blue = blueStates.get(i);
            int count = 1;      // promoting blue to red is one possibility
            for (State red : redStates) {
                UndoInfo u = dfa.treeMerge(red, blue, true, reds, reds.length);
                if (! dfa.conflict) {
                    double sc = dfa.getMDLComplexity();
                    double dfasc = dfa.getDFAComplexity();
                    if (sc != dfasc && sc - dfasc < bestScore) {
                        count++;
                    }
                }
                dfa.undoMerge(u);
            }
            if (count < bestCount) {
                if (bestBlue != null) {
                    bestBlue.conflicting = null;
                }
                bestBlue = blue;
                bestCount = count;
                if (count == 1) {
                    break;
                }
            } else {
                blue.conflicting = null;
            }
        }
        logger.info("best blue: " + bestBlue + ", count = " + bestCount);
        return bestBlue;
    }

    public boolean doSearch(HashSet<State> redStates, int max) {
        // double score = dfa.getMDLComplexity();
        ArrayList<State> blueStates = getBlueStates(redStates);
        State[] reds = redStates.toArray(new State[redStates.size()]);
        while (blueStates.size() > 0) {
            if (redStates.size() > max) {
                return false;
            }
            for (State red : redStates) {
                if (red.conflicting != null) {
                    red.conflicting.clear();
                }
            }
            State blue = pickBlueState(blueStates, redStates);
            // Try all possible merges between a red state and blue.
            for (State red : redStates) {
                UndoInfo u = dfa.treeMerge(red, blue, true, reds, reds.length);
                if (! dfa.conflict) {
                    double sc = dfa.getMDLComplexity();
                    double dfasc = dfa.getDFAComplexity();
                    if (sc != dfasc && sc - dfasc < bestScore) {
                        if (sc < bestScore) {
                            bestScore = sc;
                            bestDFA = new DFA(dfa);
                            logger.info("Best DFA with MDL score " + sc
                                    + " and DFA score " + dfasc);
                        }
                        logger.info("Intermediate DFA with MDL score " + sc
                                + " and DFA score " + dfasc);
                        if (doSearch(new HashSet<State>(redStates), max)) {
                            return true;
                        }
                    }
                }
                dfa.undoMerge(u);
            }
            blue.conflicting = null;
            // Also try promoting blue to a red state.
            redStates.add(blue);
            blueStates = getBlueStates(redStates);
        }

        logger.info("Learned DFA with MDL score " + dfa.getMDLComplexity()
                + " and DFA score " + dfa.getDFAComplexity());
        bestScore = dfa.getMDLComplexity();
        bestDFA = new DFA(dfa);
        return true;
    }

    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        int max = Integer.MAX_VALUE;

        System.out.println(Helpers.getPlatformVersion() + "\n\n");

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-maxStates")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-maxStates option requires number");
                    System.exit(1);
                }
                max = new Integer(args[i]).intValue();
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

        /*
           for (int i = 1; i < max; i++) {
           DFA dfa = new DFA(learningSamples);
           if (logger.isInfoEnabled()) {
           logger.info("Max = " + i + ", initial DFA has complexity "
           + dfa.getMDLComplexity());
           }
           ExhSearch m = new ExhSearch(dfa);
           HashSet redStates = new HashSet();
           redStates.add(dfa.startState);
           if (m.doSearch(redStates, i)) {
           if (logger.isInfoEnabled()) {
           logger.info("The winner DFA has complexity " + m.bestScore);
           logger.info("and the winner is:\n" + m.bestDFA);
           }

           m.bestDFA.write(outputfile);
           break;
           }
           }
           */
        DFA dfa = new DFA(new Samples(symbols, learningSamples, null));
        if (logger.isInfoEnabled()) {
            logger.info("Max = " + max + ", initial DFA has complexity "
                    + dfa.getMDLComplexity());
        }
        ExhSearch m = new ExhSearch(dfa);
        HashSet<State> redStates = new HashSet<State>();
        redStates.add(dfa.startState);
        m.doSearch(redStates, max);
        m.bestDFA.write(outputfile);
    }
}
