package DfaInference;

import ibis.util.Stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import org.apache.log4j.Logger;

/**
 * This class implements a red-blue search strategy. The red states of a
 * DFA are the states that will not be merged with each other anymore. The
 * blue states are the children of the red states that are not themselves a red
 * state. Merge candidates that are considered are all possible red/blue pairs.
 * This class must be extended with a specific "simple" strategy which
 * determines a score for each merge possibility suggested. The "simple"
 * strategy could be for instance "MDL score" or "Evidence Driven".
 *
 * Note that in general we have two types of scores: one that determines
 * how much we "like" a proposed merge, and one that determines which is the
 * best DFA. For instance, when using evidence-driven state merging, the
 * one that determines the best DFA is "the number of states in the DFA",
 * and the one that determines how much we like a proposed merge is the number
 * of states with matching labels that get merged as the result of the proposed
 * merge. On the other hand, when using MDL scores, there is only one measure:
 * the MDL score of the resulting DFA.
 */
public abstract class RedBlue implements java.io.Serializable, Configuration {

    /** Log4j logger. */
    protected static Logger logger = Logger.getLogger(RedBlue.class.getName());

    /** The red states. */
    protected transient State[] redStates;

    /** The number of red states. */
    protected transient int numRedStates;

    /** The blue states. */
    protected transient State[] blueStates;

    /** The number of blue states. */
    protected transient int numBlueStates;

    /**
     * Set of blue states that have no merge possibilities anymore, so
     * must be promoted to red.
     */
    protected transient HashSet<State> toPromote;

    /**
     * The merge candidates, with their score according to the search
     * heuristic.
     */
    protected transient Choice[] mergeCandidates;

    /** The number of merge candidates. */
    protected transient int numCandidates;

    /**
     * The red states are also maintained as a <code>HashSet</code> for
     * quick lookup. We do, however, also need them as an array, so that
     * there is a determined iteration order.
     */
    private transient HashSet<State> reds;

    /** The best DFA found sofar. */
    transient DFA bestDFA = null;

    /** Score of the best DFA found sofar. */
    double bestScore = Double.MAX_VALUE;

    /**
     * Determines which merges are forbidden in this search because they are
     * explored through another path.
     */
    protected transient BitSet[] noMerges;

    /** The current DFA. */
    public transient DFA dfa;

    /** Wether to disable choices. */
    public boolean disableChoices = true;

    /** Wether to print information about the choices. */
    public boolean printInfo = false;

    /** Optional strategy for picking blue states to deal with first. */
    protected PickBlueStrategy pickBlueStrategy = null;

    /**
     * Initializes from a sample.
     * @param learningSamples the sample from which the initial DFA is built.
     */
    public void init(Samples learningSamples) {
        DFA dfa = new DFA(learningSamples);
        init(dfa);
    }

    /**
     * Initializes from a specified DFA.
     * @param dfa the DFA used to initialize.
     */
    public void init(DFA dfa) {

        int numStates = dfa.getNumStates();

        this.dfa = dfa;


        // Initialize the red states.
        redStates = new State[numStates];
        redStates[0] = dfa.startState;
        numRedStates = 1;
        reds = new HashSet<State>();
        reds.add(dfa.startState);

        // Initialize the blue states.
        blueStates = new State[numStates];
        numBlueStates = 0;
        getBlueStates();

        toPromote = new HashSet<State>();

        noMerges = new BitSet[numStates+1];

        // Is this large enough ???
        mergeCandidates = new Choice[numStates];
        numCandidates = 0;
    }

    protected void addChoice(Choice c) {
        if (numCandidates >= mergeCandidates.length) {
            Choice[] n = new Choice[2*numCandidates];
            System.arraycopy(mergeCandidates, 0, n, 0, numCandidates);
            mergeCandidates = n;
        }
        mergeCandidates[numCandidates++] = c;
    }

    /**
     * Evaluates and adds the proposed merge to <code>mergeCandidates</code>,
     * together with a score, unless the proposed merge results in a conflict.
     * @param r the red state, or <code>null</code> if the proposal is not,
     * in fact, a merge, but a promotion of a blue state to red.
     * @param b the blue state.
     * @return <code>true</code> if the proposed merge is added,
     * </code>false</code> otherwise.
     */
    abstract boolean testMerge(State r, State b);

    /**
     * Returns the score of the current DFA.
     * @return the score.
     */
    abstract double getScore();

    /**
     * Determines if the proposed merge is prohibited in the current search.
     * @param r the red state of the merge pair
     * @param b the blue state of the merge pair
     * @return <code>true</code> if the merge is allowed, <code>false</code>
     * if it is not.
     */
    private boolean mayMerge(State r, State b) {
        if (! disableChoices) {
            return true;
        }
        BitSet bs = noMerges[r == null ? (noMerges.length-1) : r.id];
        if (bs != null) {
            if (printInfo && logger.isDebugEnabled()) {
                logger.debug("b.id = " + b.id + ", bs = " + bs);
            }
            return ! bs.get(b.id);
        }
        return true;
    }

    /**
     * Prohibits the specified merge in the current search.
     * @param r the red state of the specified merge.
     * @param b the blue state of the specified merge.
     */
    private void addNoMerge(State r, State b) {
        int ri = r == null ? (noMerges.length-1) : r.id;
        BitSet bs = noMerges[ri];
        if (bs == null) {
            bs = new BitSet();
            noMerges[ri] = bs;
        }
        bs.set(b.id);
        if (printInfo && logger.isDebugEnabled()) {
            logger.debug("noMerges[" + ri + "] becomes " + noMerges[ri]);
        }
    }

    /**
     * Finds a specified state in an array of states. Linear search, so this
     * had better not be called too often.
     * @param states the array of states
     * @param maxIndex maximum index to use
     * @param s the state to be searched
     * @return the index of the state in the array, or -1 if not found.
     */
    private int getIndex(State[] states, int maxIndex, State s) {
        for (int i = 0; i < maxIndex; i++) {
            if (states[i] == s) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Computes the blue states from the red states.
     * A blue state is a state that is a child of a red state, but not itself
     * a red state. All blue states are heads of trees.
     */
    private void getBlueStates() {
        numBlueStates = 0;

        for (int i = 0; i < numRedStates; i++) {
            State red = redStates[i];
            for (int j = 0; j < red.children.length; j++) {
                State s = red.children[j];
                if ((s != null) && ! reds.contains(s)) {
                    s.depth = red.depth+1;
                    s.parent = red;
                    blueStates[numBlueStates++] = s;
                }
            }
        }
    }

    /**
     * Attempts to add a merge candidate.
     * First tests if this particular candidate is allowed, and then determines
     * its score (how much we "like" it).
     * @param r the red state, or <code>null</code> if the proposal is not,
     * in fact, a merge, but a promotion of a blue state to red.
     * @param b the blue state.
     * @return <code>true</code> if the proposed merge is added,
     * </code>false</code> otherwise.
     */
    private boolean doTestMerge(State r, State b) {
        if (! mayMerge(r, b)) {
            return false;
        }
        return testMerge(r, b);
    }

    /**
     * Tries to add all merge candidates that have the specified state as
     * "blue" candidate.
     * @param blue the blue candidate.
     * @return <code>true</code> if any merge candidates were added,
     * <code>false</code> if not.
     */
    private boolean getMergeCandidates(State blue) {
        boolean retval = false;
        for (int i = 0; i < numRedStates; i++) {
            if (doTestMerge(redStates[i], blue)) {
                retval = true;
            }
        }
        return retval;
    }

    /**
     * Promotes blue states that don't have merge possibilities to red.
     * Note that the promotion of a blue state to red may result in merge
     * possibilities for the other blue states. We first promote the
     * shallowest blue state.
     */
    private void handlePromotions() {
        int sz = toPromote.size();
        while (sz != 0) {
            Iterator<State> iter = toPromote.iterator();
            // Find the shallowest state to promote.
            State shallowestState = iter.next();
            while (iter.hasNext()) {
                State s = iter.next();
                if (s.depth < shallowestState.depth ||
                        (s.depth == shallowestState.depth
                         && s.id < shallowestState.id)) {
                    shallowestState = s;
                 }
            }

            // Promote this state.
            promoteToRed(shallowestState);
            sz = toPromote.size();
        }
    }

    /**
     * Returns the contents of an array of states as a string.
     * @param states the array of states
     * @param count the number of states in the array.
     * @return the string with state ids.
     */
    private String getSet(State[] states, int count) {
        String str = "";
        for (int i = 0; i < count; i++) {
            str += " " + states[i].id;
        }
        return str;
    }

    /**
     * Returns the contents of the candidates array as a string.
     * @return a string with merge candidates.
     */
    protected String mergeChoices2Str() {
        String str = "";
        for (int j = 0; j < numCandidates; j++) {
            Choice c = mergeCandidates[j];
            str += "\n    (" + c.s1 + ", " + c.s2 + ", score = " + c.score + ")";
        }
        return str;
    }

    /**
     * Prints the current red and blue states on a debug logger.
     */
    protected void printSets() {
        logger.debug("red states: " + getSet(redStates, numRedStates));
        logger.debug("blue states: " + getSet(blueStates, numBlueStates));
    }

    /**
     * Process the consequences of taking the specified decision.
     * @param choice the choice taken
     */
    private void doStep(Choice choice) {
        Choice ch;
        State s1;
        State s2;

        // Disallow choices higher up.
        if (pickBlueStrategy == null && disableChoices) {

            for (int i = 0; i < numCandidates; i++) {
                ch = mergeCandidates[i];
                s1 = dfa.getState(ch.s1);
                s2 = dfa.getState(ch.s2);
                if (ch == choice) {
                    if (i != 0 && s1 != null) {
                        // Remove candidates before this one.
                        // But only if the choice is a merge.
                        for (int j = i + 1; j < numCandidates; j++) {
                            mergeCandidates[j-(i+1)] = mergeCandidates[j];
                        }
                        numCandidates -= i+1;
                    }
                    break;
                }
                if (printInfo && logger.isDebugEnabled()) {
                    if (s1 != null) {
                        logger.debug("Merge between " + s1.id + " and " + s2.id
                                + " prevented");
                    } else {
                        logger.debug("Explicit promotion of state " + s2.id
                                + " prevented");
                    }
                }
                addNoMerge(s1, s2);
            }
        }

        s1 = dfa.getState(choice.s1);
        s2 = dfa.getState(choice.s2);

        if (s1 != null) {
            mergeStates(s1, s2);
            if (printInfo && logger.isInfoEnabled()) {
                String str = "Score after merge = " + getScore()
                    + ", nStates = " + dfa.nStates;
                if (USE_PRODUCTIVE) {
                    str += ", nProductiveStates = " + dfa.nProductiveStates;
                }
                if (NEGATIVES) {
                    if (USE_PRODUCTIVE) {
                        str += ", nXProductiveStates = " + dfa.nXProductiveStates;
                    }
                }
                // str += ", MDL score = " + dfa.getMDLComplexity();
                logger.info(str);
            }
        } else {
            promoteToRed(s2);
            handlePromotions();
        }

        Arrays.sort(mergeCandidates, 0, numCandidates);

	if (printInfo && logger.isDebugEnabled()) {
	    logger.debug("After doStep: "
	             + mergeChoices2Str());
	}
    }

    /**
     * Applies merges and promotions as indicated by the guide.
     * When the guide is exhausted, all possibilities are computed,
     * they are sorted according to their score, and the specified percentage
     * is kept.
     * @param guide decides between the possibilities at each step
     * @param perc the percentage that is to be kept.
     */
    private void getOptions(Guidance guide, int perc) {
        if (! guide.exhausted()) {
            if (pickBlueStrategy != null) {
                Choice[] choices = applyBluePicker();
                int decision = guide.getDecision(choices.length);
                doStep(choices[decision]);
            } else {
                int decision = guide.getDecision(numCandidates);
                if (printInfo && logger.isDebugEnabled()) {
                    printCandidates();
                }
                doStep(mergeCandidates[decision]);
            }
            getOptions(guide, perc);
            return;
        }

        if (numCandidates == 0) {
            return;
        }

        int len = (numCandidates * perc) / 100;
        if (len >= numCandidates) {
            len = numCandidates;
        } else if (len <= 0) {
            len = 1;
        } else {
            while (len < numCandidates) {
                Choice ch1 = (Choice) mergeCandidates[len-1];
                Choice ch2 = (Choice) mergeCandidates[len];
                if (ch1.score != ch2.score) {
                    break;
                }
                len++;
            }
        }
        for (int i = len; i < numCandidates; i++) {
            Choice.release(mergeCandidates[i]);
        }
        numCandidates = len;
    }

    /**
     * Installs a strategy for picking blue states to deal with first.
     * @param strategy the strategy.
     */
    public void setBlueStrategy(PickBlueStrategy strategy) {
        pickBlueStrategy = strategy;
    }

    /**
     * Adds all merge candidates, given the current DFA, red states, and blue
     * states. Some blue states may not have merge candidates, in which case
     * they are promoted to red, which may result in new blue states, et
     * cetera. In the end, there are merge candidates for all blue states.
     */
    protected void getCandidates() {

        Choice.release(mergeCandidates, numCandidates);

        numCandidates = 0;

        for (int i = 0; i < numBlueStates; i++) {
            State b = blueStates[i];
            if (! getMergeCandidates(b)) {
                toPromote.add(b);
            } else {
                doTestMerge(null, b);
            }
        }

        handlePromotions();

        Arrays.sort(mergeCandidates, 0, numCandidates);

	if (printInfo && logger.isDebugEnabled()) {
	    logger.debug("After getCandidates: "
	             + mergeChoices2Str());
	}
    }

    public Choice[] applyBluePicker() {
        if (pickBlueStrategy != null) {
            Choice[] candidates = new Choice[numCandidates];
            System.arraycopy(mergeCandidates, 0, candidates, 0, numCandidates);
            int blue = pickBlueStrategy.getBlue(dfa, candidates);
            int count = 0;
            for (int i = 0; i < candidates.length; i++) {
                if (candidates[i].s2 == blue) {
                    count++;
                }
            }
            Choice[] result = new Choice[count];
            int index = 0;
            for (int i = 0; i < candidates.length; i++) {
                if (candidates[i].s2 == blue) {
                    result[index] = candidates[i];
                    index++;
                }
            }
            return result;
        }
        return null;
    }

    /**
     * Processes the consequences of promoting a blue state to red.
     * These consequences are: add it to the set of red states, remove it
     * from the list of blue states, remove any merge candidates that have
     * it as a blue state, add merge candidates that have it as a red state,
     * add new blue states (the children of the newly promoted state), and
     * add their possible merges.
     * @param r the state to promote.
     */
    protected void promoteToRed(State r) {

        if (printInfo && logger.isInfoEnabled()) {
            logger.info("Promoting blue state " + r.id + " to red");
        }

        // Add it to the red states.
        redStates[numRedStates++] = r;
        reds.add(r);

        r.conflicting = null;

        // Remove it from the blue states.
        int ind = getIndex(blueStates, numBlueStates, r);
        blueStates[ind] = blueStates[numBlueStates-1];
        numBlueStates--;
        toPromote.remove(r);

        // Remove any merge candidates that have it as a blue state.
        for (int i = 0; i < numCandidates; i++) {
            Choice ch = mergeCandidates[i];
            if (ch.s2 == r.id) {
                numCandidates--;
                Choice.release(mergeCandidates[i]);
                mergeCandidates[i] = mergeCandidates[numCandidates];
                i--;
            }
        }

        // Test all blues against the newly promoted state.
        for (int i = 0; i < numBlueStates; i++) {
            State b1 = blueStates[i];
            if (doTestMerge(r, b1)) {
                // Maybe this state does not have to be promoted anymore?
                if (toPromote.contains(b1)) {
                    toPromote.remove(b1);
                    doTestMerge(null, b1);
                }
            }
        }

        // New blue states, the children of the promoted state.
        for (int i = 0; i < r.children.length; i++) {
            State s = r.children[i];
            if (s != null) {
                s.parent = r;
                s.depth = r.depth+1;
                blueStates[numBlueStates++] = s;
                if (! getMergeCandidates(s)) {
                    // No merge candidates for this new blue state. Promote
                    // it to red as well.
                    toPromote.add(s);
                } else {
                    doTestMerge(null, s);
                }
            }
        }
    }

    /**
     * Processes the consequences of a state merge.
     * These consequences are that after the merge we need to recompute the
     * list of blue states, remove all merge candidates of which the blue state
     * no longer exists, check if all other merge candidates are still possible
     * (possibly with changed scores), promote blue states that don't have
     * merge possibilities anymore to red.
     * @param red the red state of the merge candidate.
     * @param blue the blue state of the merge candidate.
     */
    protected void mergeStates(State red, State blue) {
        State[] oldBlueStates = new State[numBlueStates];
        System.arraycopy(blueStates, 0, oldBlueStates, 0, numBlueStates);

        dfa.treeMerge(red, blue, false, redStates, numRedStates);

        if (printInfo && logger.isInfoEnabled()) {
            logger.info("Merging blue state " + blue.id + " into " + red.id
                    + " gives score " + getScore());
        }

        // Recompute blue states. Adapting it is difficult and error prone.
        // Hmmm. The only blue state that disappears is the one being merged
        // in here, and the only new ones are from red states that did not have
        // a child on all symbols yet. Have to think a bit more here ...
        // On the other hand, this probably is not critical for the performance.
        getBlueStates();
        if (printInfo && logger.isDebugEnabled()) {
            logger.debug("Current blue states: "
                    + getSet(blueStates, numBlueStates));
        }

        // Remove merge candidates of which the blue state no longer exists.
        // Of the other merge candidates, check if they are still possible.
        // Note that a merge may affect scores of other merges, so recompute
        // them.

        int oldnum = numCandidates;
        Choice[] oldMergeCandidates = new Choice[oldnum];
        System.arraycopy(mergeCandidates, 0, oldMergeCandidates, 0, oldnum);
        numCandidates = 0;

        for (int j = 0; j < oldnum; j++) {
            Choice ch = oldMergeCandidates[j];
            if (ch.s2 == blue.id) {
                if (printInfo && logger.isDebugEnabled()) {
                    logger.debug("Removing choice " + ch.s1 + " " + ch.s2);
                }
            } else if (ch.s1 != -1) {
                // A promotion is always possible.
                State s1 = dfa.getState(ch.s1);
                State s2 = dfa.getState(ch.s2);
                if (! doTestMerge(s1, s2)) {
                    // Not possible anymore
                    if (printInfo && logger.isDebugEnabled()) {
                        logger.debug("No longer possible: " + ch.s1 + " "
                                + ch.s2);
                    }
                }
            } else {
                testMerge(dfa.getState(ch.s1), dfa.getState(ch.s2));
            }
        }

        Choice.release(oldMergeCandidates, oldnum);

        // Now, some of the "old" blue states may not have merge possibilities
        // anymore.
        for (int i = 0; i < oldBlueStates.length; i++) {
            State b = oldBlueStates[i];
            if (b != blue) {
                int index = -1;
                boolean ok = false;
                for (int j = 0; j < numCandidates; j++) {
                    Choice ch = mergeCandidates[j];
                    if (ch.s2 == b.id) {
                        if (ch.s1 >= 0) {
                            // Found a real merge opportunity.
                            ok = true;
                            break;
                        }
                        index = j;
                    }
                }
                if (! ok) {
                    if (index >= 0) {
                        numCandidates--;
                        Choice.release(mergeCandidates[index]);
                        mergeCandidates[index] = mergeCandidates[numCandidates];
                    }
                    toPromote.add(b);
                }
            }
        }

        // Add all possibilities for the new blue states.
        if (numBlueStates != oldBlueStates.length-1) {
            if (printInfo && logger.isDebugEnabled()) {
                logger.debug("Merge resulted in new blue states ...");
            }
            for (int i = 0; i < numBlueStates; i++) {
                State s = blueStates[i];
                if (getIndex(oldBlueStates, oldBlueStates.length, s) < 0) {
                    if (printInfo && logger.isDebugEnabled()) {
                        logger.debug("New blue state: " + s.id);
                    }
                    if (! getMergeCandidates(s)) {
                        // No merge candidates for this new blue state. Promote
                        // it to red as well.
                        toPromote.add(s);
                    } else {
                        doTestMerge(null, s);
                    }
                }
            }
        }

        handlePromotions();
    }

    private void printCandidates() {
        for (int i = 0; i < numCandidates; i++) {
            Choice c = mergeCandidates[i];
            System.out.print("" + i + ":      ");
            printCandidate(c);
        }
    }

    private void printCandidate(Choice c) {
        System.out.println("s2 = " + c.s2 + ", s1 = " + c.s1
                + ", nstates = " + c.nstates
                + ", score = " + c.score);
    }

    /**
     * Applies merges and promotions as indicated by the guide.
     * All possibilities are computed at each step, the guide decides between
     * the possibilities.
     * @param guide decides between the possibilities at each step.
     * @param maxSteps if != 0, a threshold for the number of steps.
     * @return the resulting DFA.
     */
    protected DFA doFold(Guidance guide, int maxSteps) {
        bestScore = Double.MAX_VALUE;
        bestDFA = null;

        if (printInfo && logger.isDebugEnabled()) {
            logger.debug("Initial DFA has score " + getScore()
                    + ", #states = " + dfa.nStates
                    + ", DFA score = " + dfa.getDFAComplexity());
        }

        getCandidates();

        int numSteps = 0;
        int count = 0;

        for (;;) {
            boolean print = false;

            if (printInfo && logger.isInfoEnabled()) {
                logger.info("numCandidates = " + numCandidates);
            }

            if (numCandidates == 0) {
                break;
            }

            getStatistics();

            if (pickBlueStrategy != null && ! guide.exhausted()) {
                Choice[] choices = applyBluePicker();
                int decision = guide.getDecision(choices.length);
                doStep(choices[decision]);
            } else {
                boolean exhausted = guide.exhausted();
                if (printInfo && ! exhausted) {
                    print = true;
                    if (printInfo && logger.isDebugEnabled()) {
                        printCandidates();
                    }
                }
                int decision = guide.getDecision(numCandidates);
                if (! exhausted && logger.isInfoEnabled()) {
                    logger.info("Guide: " + count
                            + ": " + mergeCandidates[decision].readable());
                }
                if (print) {
                    System.out.println("Choice = " + decision);
                } else if (printInfo) {
                    System.out.print("Decision: "); 
                    printCandidate(mergeCandidates[decision]);
                }
                doStep(mergeCandidates[decision]);
                if (exhausted) {
                    numSteps++;
                    if (maxSteps != 0 && numSteps >= maxSteps) {
                        break;
                    }
                }
                /*
                if (! exhausted && guide.exhausted()) {
                    disableChoices = false;
                    getCandidates();
                }
                */
            }
            count++;
        }

        dfa.minimize();

        double score = getScore();

        if (bestDFA == null || score < bestScore) {
            bestDFA = dfa;
            bestScore = score;
        }
        if (logger.isInfoEnabled()) {
            logger.info("" + guide + ", learned DFA with score " + score);
        }
        return bestDFA;
    }
    
    /**
     * Applies merges and promotions as indicated by the guide.
     * All possibilities are computed at each step, the guide decides between
     * the possibilities.
     * @param guide decides between the possibilities at each step.
     * @return the control sequence.
     */
    protected int[] doFold(Guidance guide, int randomSteps, Random r) {

        if (printInfo && logger.isDebugEnabled()) {
            logger.debug("Initial DFA has score " + getScore()
                    + ", #states = " + dfa.nStates
                    + ", DFA score = " + dfa.getDFAComplexity());
        }

        getCandidates();

        int numSteps = 0;
        int count = 0;
        ArrayList<Integer> steps = new ArrayList<Integer>();

        for (;;) {
            boolean print = false;

            if (printInfo && logger.isInfoEnabled()) {
                logger.info("numCandidates = " + numCandidates);
            }

            if (numCandidates == 0) {
                break;
            }

            getStatistics();

            if (pickBlueStrategy != null && (! guide.exhausted() || numSteps < randomSteps)) {
                Choice[] choices = applyBluePicker();
                int decision;
                if (! guide.exhausted()) {
                    decision = guide.getDecision(choices.length);
                } else {
                    decision = r.nextInt(choices.length);
                    numSteps++;
                }
                steps.add(decision);
                doStep(choices[decision]);
            } else {
                boolean exhausted = guide.exhausted();
                if (printInfo && ! exhausted) {
                    print = true;
                    if (printInfo && logger.isDebugEnabled()) {
                        printCandidates();
                    }
                }
                int decision = guide.getDecision(numCandidates);
                if (exhausted) {
                    if (numSteps < randomSteps) {
                        decision = r.nextInt(numCandidates);
                    }
                    numSteps++;
                }
                if (! exhausted || numSteps <= randomSteps) {
                    steps.add(decision);
                }
                if (! exhausted && logger.isInfoEnabled()) {
                    logger.info("Guide: " + count
                            + ": " + mergeCandidates[decision].readable());
                }
                if (print) {
                    System.out.println("Choice = " + decision);
                } else if (printInfo) {
                    System.out.print("Decision: "); 
                    printCandidate(mergeCandidates[decision]);
                }
                doStep(mergeCandidates[decision]);

                /*
                if (! exhausted && guide.exhausted()) {
                    disableChoices = false;
                    getCandidates();
                }
                */
            }
            count++;
        }

        dfa.minimize();
        int[] result = new int[steps.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = steps.get(i);
        }

        return result;
    }


    private void getStatistics() {
        if (printInfo && logger.isInfoEnabled()) {
            double[] scores = new double[numCandidates];
            for (int i = 0; i < numCandidates; i++) {
                scores[i] = mergeCandidates[i].score;
            }

            double avg = Stats.mean(scores);
            double stdDev = Stats.stdDev(scores);
            double bestScore = scores[0];
            double worstScore = scores[numCandidates-1];

            logger.info("Best score = " + bestScore
                    + ", worst score = " + worstScore
                    + ", average score = " + avg
                    + ", stddev = " + stdDev);
        }
    }

    /**
     * Initializes from the specified sample, and applies merges and promotions
     * as indicated by the guide.
     * All possibilities are computed at each step, the guide decides between
     * the possibilities.
     * @param learningSamples samples from which to initialize.
     * @param guide decides between the possibilities at each step.
     * @param maxSteps if != 0, a threshold for the number of steps.
     * @return the resulting DFA.
     */
    public DFA doFold(Samples learningSamples, Guidance guide, int maxSteps) {
        init(learningSamples);
        return doFold(guide, maxSteps);
    }

    /**
     * Initializes from the specified DFA, and applies merges and promotions
     * as indicated by the guide.
     * All possibilities are computed at each step, the guide decides between
     * the possibilities.
     * @param dfa the initial DFA
     * @param guide decides between the possibilities at each step
     * @param maxSteps if != 0, a threshold for the number of steps.
     * @return the resulting DFA.
     */
    public DFA doFold(DFA dfa, Guidance guide, int maxSteps) {
        init(dfa);
        return doFold(guide, maxSteps);
    }
    
    /**
     * Initializes from the specified DFA, and applies merges and promotions
     * as indicated by the guide.
     * All possibilities are computed at each step, the guide decides between
     * the possibilities.
     * @param dfa the initial DFA
     * @param guide decides between the possibilities at each step
     * @param randomSteps the number of random steps to be taken after following
     *          the guide.
     * @param r the random number generator to use.
     * @return the control sequence.
     */
    public int[] doFold(DFA dfa, Guidance guide, int randomSteps, Random r) {
        init(dfa);
        return doFold(guide, randomSteps, r);
    }

    /**
     * Initializes from the specified sample, and applies merges and promotions
     * as indicated by the guide until the guide is exhausted, and then computes
     * the possibilities at this point.
     * The possibilities are sorted according to their score, and the
     * specified percentage is kept. The resulting list of choices is returned.
     * @param learningSamples samples from which to initialize.
     * @param guide decides between the possibilities at each step
     * @param perc the percentage that is to be kept.
     * @return the choices.
     */
    public Choice[] getOptions(Samples learningSamples, Guidance guide,
            int perc) {
        DFA dfa = new DFA(learningSamples);
        return getOptions(dfa, guide, perc);
    }

    /**
     * Initializes from the specified dfa, and applies merges and promotions
     * as indicated by the guide until the guide is exhausted, and then computes
     * the possibilities at this point.
     * The possibilities are sorted according to their score, and the
     * specified percentage is kept. The resulting list of choices is returned.
     * @param dfa the DFA from which to initialize
     * @param guide decides between the possibilities at each step
     * @param perc the percentage that is to be kept.
     * @return the choices.
     */
    public Choice[] getOptions(DFA dfa, Guidance guide, int perc) {

        init(dfa);

        if (printInfo && logger.isDebugEnabled()) {
            logger.debug("Initial DFA has score " + getScore()
                    + ", #states = " + dfa.nStates
                    + ", DFA score = " + dfa.getDFAComplexity());
        }

        getCandidates();

        getOptions(guide, perc);
        Choice[] retval;
        if (pickBlueStrategy != null) {
            retval = applyBluePicker();
        } else {
            retval = new Choice[numCandidates];
            System.arraycopy(mergeCandidates, 0, retval, 0, numCandidates);
        }
        numCandidates = 0;      // prevent these choices from being released.
        return retval;
    }
}
