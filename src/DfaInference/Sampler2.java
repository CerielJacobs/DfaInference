package DfaInference;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;

import org.apache.log4j.Logger;

import abbadingo.AbbaDingoReader;
import abbadingo.AbbaDingoString;

public class Sampler2 extends EdFold {

    private static final long serialVersionUID = 1L;

    /** Log4j logger. */
    protected static Logger logger = Logger.getLogger(Sampler2.class.getName());

    static Random random = new Random(1);

    ArrayList<Choice> choices;
    private Choice[] initial;

    Sampler2(Symbols symbols, int[][] learningSamples, Choice[] initial) {
        super();
        DFA dfa = new DFA(symbols, learningSamples);
        BitSet[] conflicts = dfa.computeConflicts();
        init(new Samples(symbols, learningSamples, conflicts));
        this.initial = initial;
    }

    Choice[] getOptions() {
        getCandidates();
        if (initial != null) {
            for (int i = 0; i < initial.length; i++) {
                Choice ch = initial[i];
                if (ch.s1 < 0) {
                    promoteToRed(dfa.getState(ch.s2));
                } else {
                    mergeStates(dfa.getState(ch.s1), dfa.getState(ch.s2));
                }
            }
        }

        return mergeCandidates;
    }

    public void fold(int depth) {
        choices = new ArrayList<Choice>();
        getCandidates();
        for (int i = 0; i < depth; i++) {
            if (numBlueStates == 0) {
                break;
            }
            if (initial != null && i < initial.length) {
                Choice ch = initial[i];
                choices.add(ch);
                if (ch.s1 < 0) {
                    promoteToRed(dfa.getState(ch.s2));
                } else {
                    mergeStates(dfa.getState(ch.s1), dfa.getState(ch.s2));
                }
                continue;
            }

            int choice2;

            if (logger.isDebugEnabled()) {
                printSets();
            }
            if (numCandidates == 1) {
                // does not count as a choice!
                choice2 = 0;
                i--;
            } else {
                choice2 = random.nextInt(numCandidates);
            }
            Choice ch = (Choice) mergeCandidates[choice2];
            if (logger.isDebugEnabled()) {
                logger.debug("Choices: " + mergeChoices2Str()
                        + ", chose " + "(" + ch.s1 + ", " + ch.s2 + ")");
            }
            choices.add(ch);
            if (ch.s1 < 0) {
                promoteToRed(dfa.getState(ch.s2));
            } else {
                mergeStates(dfa.getState(ch.s1), dfa.getState(ch.s2));
            }
        }

        doFold(new IntGuidance(new int[0]), 0);
    }

    static boolean comparesEqual(Choice[] a, Choice[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i].s1 != b[i].s1) {
                return false;
            }
            if (a[i].s2 != b[i].s2) {
                return false;
            }
        }
        return true;
    }

    static Choice[] compete(Symbols symbols, int[][] learningSamples, int depth, int randomDepth,
            int populationRoot, int diversityThreshold) {
        int[][] populationScores = new int[populationRoot][populationRoot];
        Choice[][][] choices = new Choice[populationRoot][populationRoot][0];
        int level = 0;

        while (level < depth) {
            // Initialize for new level.
            for (int i = 0; i < populationRoot; i++) {
                for (int j = 0; j < populationRoot; j++) {
                    Sampler2 s = new Sampler2(symbols, learningSamples, choices[i][j]);
                    Choice[] chs = s.getOptions();
                    if (chs.length != 0) {
                        Choice[] newchoice = new Choice[level+1];
                        for (int k = 0; k < level; k++) {
                            newchoice[k] = choices[i][j][k];
                        }
                        newchoice[level] = chs[random.nextInt(chs.length)];
                        choices[i][j] = newchoice;
                    }
                }
            }

            for (;;) {
                // Construction phase.
                // run sampler on all population members.
                for (int i = 0; i < populationRoot; i++) {
                    for (int j = 0; j < populationRoot; j++) {
                        if (choices[i][j].length == level+1) {
                            Sampler2 s = new Sampler2(symbols, learningSamples, choices[i][j]);
                            s.fold(randomDepth);
                            populationScores[i][j] = s.dfa.nProductiveStates;
                        }
                    }
                }

                // Competition phase. Competition is with neighbours in a
                // wrap-around mesh (torus).
                Choice[][][] newChoices = new Choice[populationRoot][populationRoot][];
                for (int i = 0; i < populationRoot; i++) {
                    int im1 = (i == 0 ? populationRoot : i) - 1;
                    int ip1 = (i == (populationRoot-1) ? -1 : i) + 1;
                    for (int j = 0; j < populationRoot; j++) {
                        int jm1 = (j == 0 ? populationRoot : j) - 1;
                        int jp1 = (j == (populationRoot-1) ? -1 : j) + 1;
                        int sc = populationScores[i][j];
                        newChoices[i][j] = choices[i][j];

                        if (sc > populationScores[im1][j]) {
                            sc = populationScores[im1][j];
                            newChoices[i][j] = choices[im1][j];
                        }
                        if (sc > populationScores[ip1][j]) {
                            sc = populationScores[ip1][j];
                            newChoices[i][j] = choices[ip1][j];
                        }
                        if (sc > populationScores[i][jm1]) {
                            sc = populationScores[i][jm1];
                            newChoices[i][jm1] = choices[i][jm1];
                        }
                        if (sc > populationScores[i][jp1]) {
                            sc = populationScores[i][jp1];
                            newChoices[i][jp1] = choices[i][jp1];
                        }
                    }
                }
                choices = newChoices;
                // TODO: deal with ties

                // Metalevel heuristic: decides when to promote to new level.
                // Uses diversity measure.
                int diversity = 0;
                for (int i = 0; i < populationRoot; i++) {
                    int im1 = (i == 0 ? populationRoot : i) - 1;
                    int ip1 = (i == (populationRoot-1) ? -1 : i) + 1;
                    for (int j = 0; j < populationRoot; j++) {
                        int jm1 = (j == 0 ? populationRoot : j) - 1;
                        int jp1 = (j == (populationRoot-1) ? -1 : j) + 1;
                        if (! comparesEqual(choices[i][j], choices[im1][j])) {
                            diversity++;
                        }
                        if (! comparesEqual(choices[i][j], choices[ip1][j])) {
                            diversity++;
                        }
                        if (! comparesEqual(choices[i][j], choices[i][jm1])) {
                            diversity++;
                        }
                        if (! comparesEqual(choices[i][j], choices[i][jp1])) {
                            diversity++;
                        }
                    }
                }

                logger.info("Diversity = " + diversity);

                if (diversity <= diversityThreshold) {
                    logger.info("Increasing level");
                    level++;
                    break;
                }
            }
        }

        // Now, find the best one.
        int bestScore = Integer.MAX_VALUE;
        Choice[] bestChoice = null;
        for (int i = 0; i < populationRoot; i++) {
            for (int j = 0; j < populationRoot; j++) {
                if (populationScores[i][j] < bestScore) {
                    bestScore = populationScores[i][j];
                    bestChoice = choices[i][j];
                }
            }
        }
        return bestChoice;
    }

    public static void main(String[] args) {
        String  learningSetFile = null;
        String outputfile = "LearnedDFA";
        int depth = 200;
        int randomDepth = 5000;
        int diversityThreshold = 200;
        int populationRoot = 8;

        System.out.println(Helpers.getPlatformVersion() + "\n\n");

        for (int i = 0; i < args.length; i++) {
            if (false) {
            } else if (args[i].equals("-populationRoot")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-populationRoot option requires number");
                    System.exit(1);
                }
                populationRoot = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-randomDepth")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-randomDepth option requires number");
                    System.exit(1);
                }
                randomDepth = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-depth")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-depth option requires number");
                    System.exit(1);
                }
                depth = (new Integer(args[i])).intValue();
            } else if (args[i].equals("-diversityThreshold")) {
                i++;
                if (i >= args.length) {
                    logger.fatal("-diversityThreshold option requires number");
                    System.exit(1);
                }
                diversityThreshold = (new Integer(args[i])).intValue();
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

        Choice[] bestChoice = compete(symbols, learningSamples, depth, randomDepth,
                populationRoot, diversityThreshold);

        Sampler2 s = new Sampler2(symbols, learningSamples, bestChoice);
        s.fold(randomDepth);
        int nstates = s.dfa.nProductiveStates;

        logger.info("Learned DFA with " + nstates + " states");
        logger.info("and the winner is:\n" + s.dfa);

        s.dfa.write(outputfile);
    }
}
