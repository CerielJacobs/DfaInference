package DfaInference;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Logger;

/**
 * This class represents a DFA and offers various operations on it.
 */
public final class DFA implements java.io.Serializable, Configuration {

    private static final long serialVersionUID = 1L;

    /** Precompute log(2). */
    private static final double LOG2 = Math.log(2);

    /** Precomputed sums of logs. */
    private static double[] sumLogs;

    /** Log4j logger. */
    static Logger logger = Logger.getLogger(DFA.class.getName());

    static {
        if (logger.isInfoEnabled()) {
            String str = "DFA inference configuration:";
            if (INCREMENTAL_COUNTS) {
                str += " Incremental";
            }
            if (USE_PARENT_SETS) {
                str += " ParentSets";
            }
            if (NEW_IMPL) {
                str += " NewImpl";
            }
            logger.info(str);

            str = "MDL score configuration:";
            if (MDL_COMPLEMENT) {
                str += " Complement";
            }
            if (MDL_NEGATIVES) {
                str += " Negatives";
            }
            if (REFINED_MDL) {
                str += " PerEndState";
            }
            logger.info(str);

            str = "DFA score configuration:";
            if (USE_PRODUCTIVE) {
                str += " ProductiveCounts";
            }
            logger.info(str);
        }
    };

    /** The learning samples. */
    int[][] samples;

    /**
     * Counts for number of strings recognized by each state, for
     * all lengths from 0 to maxlen.
     */
    int [][] counts;

    /**
     * Counts for number of strings recognized by each state of the rejecting
     * dfa, for all lengths from 0 to maxlen.
     */
    int [][] xCounts;

    /**
     * Temporary for counts, for computing updates.
     */
    private int [][] tempCounts;

    /**
     * Some more temporary storage, for refined MDL.
     */
    CountsMap[] stateCounts;

    /** Set when counts are initialized. Used for incremental computations. */
    private boolean counts_done = false;


    /** The start state of this DFA. */
    State startState;

    /** Maximum length of input string. */
    int maxlen = 0;

    /** Number of recognized input strings from the sample. */
    int numRecognized = 0;

    /** Number of rejected input strings from the sample. */
    int numRejected = 0;

    /** Number of states in this DFA. */
    int nStates;

    /** MDL Score of sample. */
    double MDLScore = 0;

    /** Score of this DFA without samples. */
    double DFAScore = 0;

    /** Label score of the last merge. */
    int labelScore = 0;

    /** Number of symbols. */
    int nsym;

    /** Number of productive states. */
    int nProductive = -1;

    /** Number of productive states in the complement DFA. */
    int nXProductive = -1;

    /** Conflict: an accepting state is merged with a rejecting state. */
    boolean conflict = false;

    /** Maps ids to states. */
    State[] idMap;

    /** Temporary storage. */
    private State[] saved;

    /** Index in <code>saved</code> array. */
    private int savedIndex;

    /** Conflicts of initial DFA. */
    private transient BitSet[] conflicts;

    boolean mustRecomputeProductive;

    int markCounter = 2;     // Used for state marker

    int[] accepts;
    int[] rejects;

    /**
     * Basic constructor, creates an empty DFA.
     * @param nsym the number of symbols used.
     */
    public DFA(int nsym) {
        nStates = 1;
        this.nsym = nsym;
        startState = new State(nsym);
    }

    /**
     * Constructor, creates a DFA recognizing the specified samples.
     * @param samples the samples
     */
    public DFA(int[][] samples) {
        this(getNumSyms(samples));

        runSample(samples);
        idMap = startState.breadthFirst();
        saved = new State[nStates];

        if (logger.isDebugEnabled()) {
            if (! checkDFA()) {
                logger.error("From dfa constructor: exit");
                System.exit(1);
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Created initial DFA, #states " + nStates);
        }
    }

    /**
     * Copying constructor.
     * @param dfa The DFA to copy.
     */
    public DFA(DFA dfa) {

        nStates = dfa.nStates;
        samples = dfa.samples;
        maxlen = dfa.maxlen;
        numRecognized = dfa.numRecognized;
        numRejected = dfa.numRejected;
        nsym = dfa.nsym;
        MDLScore = dfa.MDLScore;
        DFAScore = dfa.DFAScore;
        nProductive = dfa.nProductive;
        nXProductive = dfa.nXProductive;

        startState = dfa.startState.copy();
        idMap = startState.breadthFirst();
        saved = new State[nStates];
        conflicts = dfa.conflicts;

        if (logger.isDebugEnabled()) {
            logger.debug("Creating a DFA copy");
            if (! checkDFA()) {
                logger.error("From dfa copy constructor: exit");
                System.exit(1);
            }
        }
    }

    /**
     * Read the dfa from the specified reader, interpreting the
     * input as described below.
     * <br>
     * The input consists of lines that start with a tag and arguments to the
     * tag. The tags are X for example, T for number of tokens, S for startnode,
     * N for node, A for accepting and E for edge.
     * Order is important. In general the order is:<ul>
     * <li> 1. One or more X lines.
     * <li> 2. A T line.
     * <li> 3. An S line.
     * <li> 4. One or more groups of:<ul>
     * <li> a. an N line, optionally followed by an A line.
     * <li> b. followed by zero or more E lines</ul></ul>
     * <br> 
     * Example:<br>
     *
     * <pre>
     * Xa
     * Xab
     * T2
     * S1
     * N1
     * E1:a:2
     * N2
     * A2
     * E2:b:3
     * N3
     * A3
     * </pre>
     *
     * @param r reader from which to read the dfa.
     */
    public DFA(Reader r) {
        HashMap<Integer, State> nodes = new HashMap<Integer, State>();

        try {
            String line;
            BufferedReader br = new BufferedReader(r);
            Integer startNodeId = null;
            while ((line=br.readLine()) != null) {
                if (line.length() == 0) {
                    continue;
                }

                switch (line.charAt(0)) {
                case 'T':
                    // Number of tokens
                    nsym = (new Integer(line.substring(1))).intValue();
                    break;

                case 'S':
                    // Treat start node indicator (creates the dfa)
                    if (startNodeId != null) {
                        throw new Error("File format error: multiple start states!");
                    }
                    startNodeId = new Integer(line.substring(1));
                    if (nsym == 0) {
                        throw new Error("File format error: number of tokens not specified before startnode defined");
                    }
                    startState = new State(nsym);
                    nodes.put(startNodeId, startState);
                    nStates = 1;
                    break;

                case 'N':
                    {
                        // Treat node definition
                        if (startNodeId == null) {
                            throw new Error("File format error: node defined before startnode defined!");
                        }
                        // add node only if it is not the start node (which is already
                        // created)
                        Integer id = new Integer(line.substring(1));
                        if (nodes.get(id) == null) {
                            State s = new State(nsym);
                            nodes.put(id, s);
                            nStates++;
                        }
                        break;
                    }

                case 'E':
                    {
                        // Treat edge definition
                        if (startNodeId == null) {
                            throw new Error("File format error: edge defined before startnode defined!");
                        }

                        int lastIndex = line.indexOf(':');
                        // first field defines from node 
                        if (lastIndex == -1) {
                            throw new  Error("File format error: Invalid E line (1)!");
                        }
                        String from = line.substring(1,lastIndex);

                        // next field defines label, and last field defines to node
                        int lastLastIndex = lastIndex;
                        lastIndex = line.indexOf(':',lastIndex+1);
                        if (lastIndex == -1) {
                            throw new Error("File format error: Invalid E line (2)!");
                        }
                        String label_s = line.substring(lastLastIndex+1,lastIndex);
                        int label = Symbols.addSymbol(label_s);
                        String to = line.substring(lastIndex+1);

                        Integer fromVal = new Integer(from);
                        Integer toVal = new Integer(to);

                        // only from node needs to exist; note that this is
                        // a bug, we should really check if the destination
                        // node exists at the end of the read operation
                        State s = (State) nodes.get(fromVal);
                        if (s == null) {
                            throw new Error("File format error: E with from "
                                    + "node for which no N line seen yet!");
                        }

                        State d = (State) nodes.get(toVal);
                        if (d == null) {
                            d = s.addDestination(label);
                            nodes.put(toVal, d);
                            nStates++;
                        } else {
                            s.addEdge(d, label);
                        }
                        break;
                    }

                case 'A':
                    {
                        // Treat accepting indicator (node must be defined)
                        State d = (State) nodes.get(
                                new Integer(line.substring(1)));
                        if (d == null) {
                            throw new Error("Trying to set Accept of node for which no N line seen!");
                        }
                        d.accepting = ACCEPTING;
                        break;
                    }

                case 'X':
                    // Treat example definition (ignore)
                    break;

                default:
                    // Ignore everything else.
                    break;
                }
            }
        } catch(IOException E) {
            throw new Error(E.toString());
        }

        idMap = startState.breadthFirst();
        startState.computeDepths();
        saved = new State[nStates];
        nProductive = startState.computeProductive(ACCEPTING);
        if (MDL_COMPLEMENT || MDL_NEGATIVES) {
            nXProductive = startState.computeProductive(REJECTING);
        }

        if (logger.isDebugEnabled()) {
            if (! checkDFA()) {
                logger.error("From dfa reader constructor: exit");
                System.exit(1);
            }
        }
    }

    /**
     * Sets the initial conflicts.
     * @param conflicts the initial conflict sets.
     */
    public void setConflicts(BitSet[] conflicts) {
        this.conflicts = conflicts;
    }

    /**
     * Returns the state identified by the specified id.
     * @param id the identification
     * @return the state.
     */
    public State getState(int id) {
        if (id < 0) {
            return null;
        }
        return idMap[id];
    }

    /**
     * Converts a string, of which each character is assumed to be a separate
     * symbol, to an array of integers.
     */
    public int[] stringToSymbols(String sentence) {
        int len = sentence.length();
        int [] symbols = new int[len+1];

        symbols[0] = len;
        for (int i = 0; i < len; i++) {
            String substr = sentence.substring(i, i+1);
            symbols[i+1] = Symbols.addSymbol(substr);
        }
        return symbols;
    }

    /**
     * Adds states and edges from the given symbol string.
     * symbols[0] tells whether this is an accept or reject.
     * @param symbols        the given symbol string.
     */
    public void addString(int[] symbols) {
        State n = startState;
        boolean reject = symbols[0] != 1;

        if (accepts == null) {
            accepts = new int[256];
            rejects = new int[256];
        }

        for (int i = 1; i < symbols.length; i++) {
            State target = n.traverseEdge(symbols[i]);
            if (target == null) {
                target = n.addDestination(symbols[i]);
                nStates++;
            }
            n = target;
        }

        if (reject) {
            numRejected++;
            rejects[symbols.length-1]++;
            n.accepting = REJECTING;
            if (MDL_COMPLEMENT || MDL_NEGATIVES) {
                n.weight = 1;
            }
        } else {
            numRecognized++;
            accepts[symbols.length-1]++;
            n.accepting = ACCEPTING;
            n.weight = 1;
        }
    }

    /**
     * Derives and returns the number of symbols used in the specified samples.
     * @param samples the samples.
     * @return the number of symbols used in the samples.
     */
    private static int getNumSyms(int[][] samples) {
        int ns = 0;
        for (int i = 0; i < samples.length; i++) {
            int[] sample = samples[i];
            for (int j = 1; j < sample.length; j++) {
                if (sample[j] >= ns) {
                    ns = sample[j] + 1;
                }
            }
        }
        return ns;
    }

    /**
     * Adds states and edges from the specified sample.
     * @param samples        the specified sample.
     */
    public void runSample(int[][] samples) {
        this.samples = samples;
        maxlen = 0;
        MDLScore = 0;
        DFAScore = 0;

        for (int i = 0; i < samples.length; i++) {
            if (samples[i].length > maxlen+1) {
                maxlen = samples[i].length-1;
            }
        }
        for (int i = 0; i < samples.length; i++) {
            addString(samples[i]);
        }
        nProductive = startState.computeProductive(ACCEPTING);
        if (MDL_COMPLEMENT || MDL_NEGATIVES) {
            nXProductive = startState.computeProductive(REJECTING);
        }
    }

    /**
     * Adds states and edges from the learning sample.
     * Each character in the strings is assumed to be separate symbol.
     * @param sentences        the given strings.
     */
    public void runSample(String[] sentences) {
        int[][] samples = new int[sentences.length][];
        for (int i = 0; i < sentences.length; i++) {
            samples[i] = stringToSymbols(sentences[i]);
        }
        runSample(samples);
    }

    /**
     * Attempts to recognize the given symbol string.
     * Returns <code>true</code> if accepted, <code>false</code> if not.
     *
     * @param symbols        the given string.
     */
    public boolean recognize(int[] symbols) {
        State n = startState;

        for (int i = 1; i < symbols.length; i++) {
            State target = n.traverseEdge(symbols[i]);
            if (target == null) {
                return false;
            }
            n = target;
        }
        return (n.accepting == ACCEPTING);
    }

    /**
     * Attempts to recognize the given string.
     * Each character in the string is assumed to be separate symbol.
     * Returns <code>true</code> if accepted, <code>false</code> if not.
     *
     * @param sentence        the given string.
     */
    public boolean recognize(String sentence) {
        return recognize(stringToSymbols(sentence));
    }

    /**
     * Obtains the number of states.
     *
     * @return number of states
     */
    public int getNumStates() {
        return nStates;
    }

    /**
     * Obtains the start state for this DFA.
     *
     * @return the start state of this DFA.
     */
    public State getStartState() {
        return startState;
    }

    private int getMark() {
        if (markCounter >= Integer.MAX_VALUE - idMap.length) {
            for (int i = 0; i < idMap.length; i++) {
                idMap[i].mark = 0;
            }
            markCounter = 0;
        }
        return ++markCounter;
    }

    private State[] addToSavedStates(State[] s, int ns) {
        int mark = getMark();
        int count = savedIndex;
        for (int i = 0; i < savedIndex; i++) {
            saved[i].mark = mark;
        }
        for (int i = 0; i < ns; i++) {
            if (s[i].mark != mark) {
                saved[count++] = s[i];
            }
        }
        State[] states = new State[count];
        System.arraycopy(saved, 0, states, 0, count);
        return states;
    }

    private State[] computeParentClosure(State[] states, int count) {
        ArrayList<State> mods = new ArrayList<State>();
        int mark = getMark();
        for (int i = 0; i < count; i++) {
            mods.add(states[i]);
            states[i].mark = mark;
        }

        int startIndex = 0;

        do {
            states = mods.toArray(new State[mods.size()]);
            for (int i = startIndex; i < states.length; i++) {
                State s = states[i];
                if (s.parentsArray == null) {
                    s.parentsArray = (State[]) s.parents.toArray(new State[0]);
                }
                for (int j = 0; j < s.parentsArray.length; j++) {
                    State ps = s.parentsArray[j];
                    if (ps.mark != mark) {
                        mods.add(ps);
                        ps.mark = mark;
                    }
                }
            }
            startIndex = states.length;
        } while (startIndex < mods.size());

        return states;
    }

    private void updateProductive(State[] states, UndoInfo undo) {
        boolean change = true;

        while (change) {
            change = false;
            for (int i = 0; i < states.length; i++) {
                State s = states[i];
                if (s.productive != ((MDL_COMPLEMENT || MDL_NEGATIVES)
                            ? MASK : ACCEPTING)) {
                    byte prod = s.accepting;
                    for (int j = 0; j < s.children.length; j++) {
                        State sj = s.children[j];
                        if (sj != null) {
                            prod |= sj.productive;
                        }
                    }
                    if ((prod & s.productive) != prod) {
                        saveState(s, undo);
                        change = true;
                        if ((prod & ACCEPTING) != 0) {
                            if ((s.productive & ACCEPTING) == 0) {
				s.productive |= ACCEPTING;
                                nProductive++;
                            }
                        }
                        if ((MDL_COMPLEMENT || MDL_NEGATIVES)
                                && (prod & REJECTING) != 0) {
                            if ((s.productive & REJECTING) == 0) {
				s.productive |= REJECTING;
                                nXProductive++;
                            }
                        }
			s.productive |= prod;
                    }
                }
            }
        }
    }

    /**
     * Merges the specified red and blue states, including the implied
     * merges to make the DFA deterministic again. If afterwards the
     * DFA has a conflict, it is marked as such.
     */
    public UndoInfo treeMerge(State red, State blue, boolean undoNeeded,
            State[] redStates, int numRedStates) {
        UndoInfo undo = null;

        if (conflicts != null && conflicts[red.id] != null) {
            if (conflicts[red.id].get(blue.id)) {
                conflict = true;
                return null;
            }
        }

        if (blue.hasConflict(red)) {
            conflict = true;
            return null;
        }

        State parent = blue.parent;

        if (USE_PRODUCTIVE) {
            mustRecomputeProductive =
                (parent.productive | red.productive) != parent.productive;
        }

        // Make the blue node's parent indicate the red node.
        savedIndex = 1;

        saved[0] = parent;
        parent.savedIndex = 0;

        if (undoNeeded) {
            if (INCREMENTAL_COUNTS && ! counts_done) {
                // if (stateCounts != null || counts != null) {
                    getMDLComplexity();
                // }
            }
            undo = UndoInfo.getUndoInfo(this);
            undo.addData(parent);
        }

        State[] pch = parent.children;
        for (int j = 0; j < nsym; j++) {
            if (pch[j] == blue) {
                addEdge(undo, parent, j, red);
            }
        }

        // Recurse while merging ...
        labelScore = walkTreeMerge(red, blue, undo);

        DFAScore = 0;

        if (! conflict) {
            MDLScore = 0;

            if (mustRecomputeProductive ||
                (INCREMENTAL_COUNTS && (counts != null || stateCounts != null))) {
                State[] states;

                if (USE_PARENT_SETS) {
                    states = computeParentClosure(saved, savedIndex);
                } else {
                    states = addToSavedStates(redStates, numRedStates);
                }

                if (INCREMENTAL_COUNTS && counts != null) {
                    if (undo != null) {
                        // Does not fully recompute the counts array.
                        // Only recomputes for as far as needed to obtain the
                        // counts for the start state.

                        int mark = getMark();
                        int startStateIndex = -1;

                        markCounter += states.length;

                        for (int i = 0; i < states.length; i++) {
                            states[i].maxLenComputed = 0;
                            states[i].mark = mark+i;
                            if (states[i] == startState) {
                                startStateIndex = i;
                            }
                        }

                        if (tempCounts == null || tempCounts.length < states.length) {
                            tempCounts = new int[states.length][maxlen+1];
                        }

                        undo.saveCounts();

                        startState.computeUpdate(maxlen, counts, tempCounts, mark);
                        for (int i = 1; i <= maxlen; i++) {
                            counts[i][startState.id] = tempCounts[startStateIndex][i];
                        }
                        if (MDL_NEGATIVES) {
                            for (int i = 0; i < states.length; i++) {
                                states[i].maxLenComputed = 0;
                            }
                            startState.computeUpdate(maxlen, xCounts, tempCounts, mark);
                            for (int i = 1; i <= maxlen; i++) {
                                xCounts[i][startState.id] = tempCounts[startStateIndex][i];
                            }
                        }
                    } else {
                        // Fully recompute the counts.
                        counts_done = false;
                    }

                } else if (INCREMENTAL_COUNTS && stateCounts != null) {
                    if (undo != null) {

                        int mark = getMark();
                        int startStateIndex = -1;

                        markCounter += states.length;

                        CountsMap[] tempStateCounts
                            = new CountsMap[states.length];

                        for (int i = 0; i < states.length; i++) {
                            states[i].maxLenComputed = 0;
                            states[i].mark = mark+i;
                            if (states[i] == startState) {
                                startStateIndex = i;
                            }
                            tempStateCounts[i] = new CountsMap();
                            if (states[i].weight != 0) {
                                tempStateCounts[i].put(states[i], 0, 1);
                            }
                        }

                        startState.computeStateCountsUpdate(maxlen, stateCounts,
                                tempStateCounts, mark);
                        stateCounts[startState.id]
                                = tempStateCounts[startStateIndex];
                    } else {
                        // Fully recompute the counts.
                        counts_done = false;
                    }
                }

                if (mustRecomputeProductive) {
                    updateProductive(states, undo);
                }
            }

            if (logger.isDebugEnabled()) {
                if (! checkDFA()) {
                    System.out.println("Problem after merge.");
                    if (undo != null) {
                        System.out.println("DFA before merge was: ");
                        undoMerge(undo);
                        System.out.println(dumpDFA());
                    }
                    System.exit(1);
                }
            }
        } else {
            blue.addConflict(red);
        }

        return undo;
    }

    private void saveState(State s, UndoInfo undo) {
        if (savedIndex <= s.savedIndex || saved[s.savedIndex] != s) {
            saved[savedIndex] = s;
            s.savedIndex = savedIndex;
            savedIndex++;
            if (undo != null) {
                undo.addData(s);
            }
        }
    }

    /**
     *   Merges two states. This version specifically performs merges for
     *   which one of the states is the top of a tree, so does not take
     *   into account states that point upwards. For some
     *   search techniques, this is good enough, for others, it is not.
     *   It computes the number of corresponding labels in the merge, where
     *   a corresponding label means: either both states are accepting or
     *   both states are rejecting.
     *
     *   @param n1  The first state
     *   @param n2  The second state (top of a tree)
     *   @param undo Will collect information for undoing this merge.
     *   @return the number of corresponding labels in this merge.
     */
    public int walkTreeMerge(State n1, State n2, UndoInfo undo) {

        int labelscore = 0;

        saveState(n1, undo);

        if ((n1.accepting & n2.accepting) != 0) {
            labelscore = 1;
        } else if ((n1.accepting | n2.accepting) == MASK) {
            conflict = true;
            return 0;
        } else {
            n1.accepting |= n2.accepting;
            if (! REFINED_MDL) {
                if (n1.accepting != 0) {
                    if (counts != null && (n1.accepting & ACCEPTING) != 0) {
                        counts[0][n1.id] = 1;
                    } 
                    if (xCounts != null && (n1.accepting & REJECTING) != 0) {
                        xCounts[0][n1.id] = 1;
                    }
                }
            }
        }

        n1.weight += n2.weight;

        if ((n2.productive & ACCEPTING) != 0) {
            if ((n1.productive & ACCEPTING) == 0) {
                n1.productive |= ACCEPTING;
                mustRecomputeProductive = true;
            } else {
                nProductive--;
            }
        }

        if ((MDL_COMPLEMENT || MDL_NEGATIVES)
                && (n2.productive & REJECTING) != 0) {
            if ((n1.productive & REJECTING) == 0) {
                n1.productive |= REJECTING;
                mustRecomputeProductive = true;
            } else {
                nXProductive--;
            }
        }

        // A merge may make the depth smaller.
        if (n1.depth > n2.depth) {
            n1.depth = n2.depth;
        }

        nStates--;

        // System.out.println("n1 = " + n1.id + ", n2 = " + n2.id);
        for (int i = 0; i < nsym; i++) {
            State v2 = n2.children[i];
            if (v2 != null) {
		State v1 = n1.children[i];
                if (v1 != null) {
                    // System.out.println("    v1 = " + v1.id + ", v2 = " + v2.id);
                    labelscore += walkTreeMerge(v1, v2, undo);
                    if (conflict) {
                        return 0;
                    }
                } else {
                    // Parent of v2 changes, but is recomputed before every
                    // merge.
                    // System.out.println("    v2 = " + v2.id);
                    addEdge(undo, n1, i, v2);
                    if (USE_PARENT_SETS) {
                        v2.parents.remove(n2);
                        v2.parentsArray = null;
                        if (undo != null) {
                            undo.addParentRemoval(v2, n2);
                        }
                    }
                }
            }
        }

        return labelscore;
    }

    private void addEdge(UndoInfo undo, State parent, int i, State dest) {
        if (undo != null) {
            undo.addChild(parent, i, parent.children[i]);
            if (USE_PARENT_SETS) {
                if (! dest.parents.contains(parent)) {
                    undo.addParentAddition(dest, parent);
                }
            }
        }
        parent.addEdge(dest, i);
    }

    /**
     * Restores the DFA to the state it was in earlier, as specified by
     * the parameter.
     * @param u specifies saved state.
     */
    public void undoMerge(UndoInfo u) {
        conflict = false;
        if (u != null) {
            u.undo();
        }

        if (logger.isDebugEnabled()) {
            if (! checkDFA()) {
                logger.error("From undoMerge: exit");
                System.exit(1);
            }
        }
    }

    /**
     * Computes the number of bits needed to encode the DFA.
     * We need a two-dimensional array of (nStates * nsym) size, where
     * each entry contains a destination state. We also need one bit per
     * state indicating if the state is an end state.
     * Each entry needs enough bits to encode a state number.
     * This assumes a fixed start state.
     * Note that any permutation of the states will do, so there is a lot
     * of redundancy in this representation. In fact, (nStates-1)! redundancy,
     * so we deduct log2((nStates-1)!) (the start state is fixed).
     *
     * @return  the actual sum for the DFA
     */
    public double getDFAComplexity() {
        if (conflict) {
            return Double.MAX_VALUE;
        }
        if (DFAScore == 0) {
            if (USE_PRODUCTIVE) {
                if ((MDL_NEGATIVES || MDL_COMPLEMENT) && nXProductive > 0) {
                    int nXs = nXProductive+1;
                    DFAScore = nXs * (1 + nsym * log2(nXs));
                    DFAScore -= sumLog(nXs-1)/LOG2;
                    // From a paper by Domaratzky, Kisman, Shallit
                    // DFAScore = nXs * (1.5 + log2(nXs));
                }
                int ns = nProductive+1;
                DFAScore += ns * (1 + nsym * log2(ns));
                DFAScore -= sumLog(ns-1)/LOG2;
                // DFAScore += ns * (1.5 + log2(ns));
            } else {
                int ns = nStates+1;
                DFAScore = ns * (1 + nsym * log2(ns));
                DFAScore -= sumLog(ns-1)/LOG2;
                // DFAScore = ns * (1.5 + log2(ns));
            }
        }

        return DFAScore;
    }

    /**
     * Precomputes the sum of logs. Approximates if c > 2^16
     * @param c logs up until this number are needed, but we may compute more.
     */
    private static final double sumLog(double c) {
        if (sumLogs == null) {
            sumLogs = new double[65536];
            sumLogs[0] = 0.0;
            for (int i = 1; i < sumLogs.length; i++) {
                sumLogs[i] = Math.log(i) + sumLogs[i-1];
            }
        }
        if (c >= sumLogs.length) {
            // Uses the approximation ln(n!) ~ n.ln(n) - n.
            return c * Math.log(c) - c;
        }
        return sumLogs[(int)c];
    }

    private double approximate2LogNoverK(double n, int k) {
        return (sumLog(n) - sumLog(k) - sumLog(n-k))/LOG2;
    }

    private void computeCounts(State[] states) {
        if (stateCounts == null) {
            stateCounts = new CountsMap[idMap.length];
            CountsMap.initHash(idMap.length, maxlen+1);
        }

        for (int i = 0; i < stateCounts.length; i++) {
            stateCounts[i] = null;
        }

        for (int i = 0; i < states.length; i++) {
            State s = states[i];
            stateCounts[s.id] = new CountsMap();
            if (s.weight != 0) {
                stateCounts[s.id].put(s, 0, 1);
            }
        }

        for (int k = 1; k <= maxlen; k++) {
            for (int i = 0; i < states.length; i++) {
                State s = states[i];
                CountsMap hs = stateCounts[s.id];
                for (int j = 0; j < s.children.length; j++) {
                    State sj = s.children[j];
                    if (sj != null) {
                        CountsMap hsj = stateCounts[sj.id];
                        int sz = hsj.size();
                        for (int l = 0; l < sz; l++) {
                            int cnt = hsj.getCount(l, k-1);
                            if (cnt != 0) {
                                hs.add(hsj.getState(l), k, cnt);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Calculates the Minimum Description Length complexity, relative to
     * the complete learning set.
     * @return The complexity
     */
    public double getMDLComplexity() {
        if (conflict) {
            return Double.MAX_VALUE;
        }
        if (MDLScore == 0) {
            if (counts == null && (! REFINED_MDL || ! NEW_IMPL)) {
                counts = new int[maxlen+1][];
                for (int i = 0; i < counts.length; i++) {
                    counts[i] = new int[idMap.length];
                }
            }
            if (MDL_NEGATIVES) {
                if (xCounts == null && (! REFINED_MDL || ! NEW_IMPL)) {
                    xCounts = new int[maxlen+1][];
                    for (int i = 0; i < xCounts.length; i++) {
                        xCounts[i] = new int[idMap.length];
                    }
                }
            }
            if (REFINED_MDL) {
                double score = 0;
                if (NEW_IMPL) {
                    if (! INCREMENTAL_COUNTS || ! counts_done) {
                        State[] myStates = startState.breadthFirst();
                        computeCounts(myStates);
                    }
                    /*
                    int mark = getMark();
                    for (int i = 0; i < myStates.length; i++) {
                        myStates[i].mark = mark;
                    }
                    */
                    CountsMap startStateMap = stateCounts[startState.id];
                    int sz = startStateMap.size();
                    for (int i = 0; i < sz; i++) {
                        State e = startStateMap.getState(i);
                        // if (e.mark == mark) {
                            int cnt = 0;
                            for (int j = 0; j <= maxlen; j++) {
                                cnt += startStateMap.getCount(i, j);
                            }
                            score += approximate2LogNoverK(cnt, e.weight);
                        // }
                    }
                } else {
                    /*
                    State[] myStates = startState.breadthFirst();
                    for (int i = 0; i < myStates.length; i++) {
                        if (myStates[i].weight != 0) {
                            int id = myStates[i].id;
                            for (int j = 0; j <= maxlen; j++) {
                                counts[j][id] = 0;
                            }
                        }
                    }
                    */
                    State[] myStates = reachCount();
                    int totalCount = 0;
                    for (int i = 0; i < myStates.length; i++) {
                        int cnt = 0;
                        int id = myStates[i].id;
                        for (int j = 0; j <= maxlen; j++) {
                            cnt += counts[j][id];
                        }
                        double sc = approximate2LogNoverK(cnt,
                                myStates[i].weight);
                        if (logger.isDebugEnabled()) {
                            totalCount += cnt;
                            logger.debug("State " + id + ", weight = "
                                    + myStates[i].weight + ", cnt = " + cnt
                                    + ", sc  = " + sc);
                        }
                        score += sc;
                    }
                    logger.debug("totalCount = " + totalCount);
                }
                MDLScore = score;
                counts_done = true;
                double DFAScore = getDFAComplexity();
                score += DFAScore;
                if (logger.isDebugEnabled()) {
                    logger.debug("getMDLComplexity: MDLscore = "
                            + MDLScore + ", DFAscore = " + DFAScore
                            + ", total = " + score);
                }
                return score;
            }

            int n = computeNStrings(maxlen, counts, ACCEPTING);
	    MDLScore = approximate2LogNoverK(n, numRecognized);

            if (MDL_NEGATIVES && numRejected > 0) {
                n = computeNStrings(maxlen, xCounts, REJECTING);
                MDLScore += approximate2LogNoverK(n, numRejected);
            }

            if (MDL_COMPLEMENT && numRejected > 0) {
                double cn = Math.pow(2, maxlen+1) - n - 1;
                double score = approximate2LogNoverK(cn, numRejected);
                MDLScore += score;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("getMDLComplexity: score = "
                        + MDLScore);
            }
            counts_done = true;
        }

        double score = MDLScore + getDFAComplexity();

        if (logger.isDebugEnabled()) {
            logger.debug("getMDLComplexity: total score = " + score);
        }

        return score;
    }

    private double log2(double d) {
        return Math.log(d)/LOG2;
    }

    /**
     * Consistency check for the DFA.
     */
    public boolean checkDFA() {
        boolean ok = true;

        // Check that it recognizes the samples! 
        if (samples != null) {
            for (int i = 0; i < samples.length; i++) {
                if (samples[i][0] == 1 && ! recognize(samples[i])) {
                    logger.error("Did not recognize sample " + i);
                    ok = false;
                }
                if (! conflict && samples[i][0] == 0 && recognize(samples[i])) {
                    logger.error("Did recognize sample " + i);
                    ok = false;
                }
            }
        }

        State[] l = startState.breadthFirst();

        int nprod = startState.computeProductive(ACCEPTING);
        if (nProductive != nprod) {
            logger.error("nProductive = " + nProductive + ", size = "
                    + nprod
                    + ", DFA = \n" + dumpDFA());
            ok = false;
        }

        // Check parents
        if (USE_PARENT_SETS) {
            for (int i = 0; i < l.length; i++) {
                State s = l[i];
                boolean present;

                if (s.parentsArray == null) {
                    s.parentsArray = (State[]) s.parents.toArray(new State[0]);
                }
                for (int iter = 0; iter < s.parentsArray.length; iter++) {
                    State s2 = s.parentsArray[iter];
                    present = false;
                    for (int j = 0; j < nsym; j++) {
                        if (s2.children[j] == s) {
                            present = true;
                            break;
                        }
                    }
                    if (! present) {
                        logger.error("State " + s2 + " is present in the parent "
                                + "set of state " + s + ", but should not be.");

                        ok = false;
                    }
                }
                for (int j = 0; j < nsym; j++) {
                    State s2 = s.children[j];
                    if (s2 != null && ! s2.parents.contains(s)) {
                        logger.error("State " + s + " is not present in the parent "
                                + "set of state " + s2 + ", but should be.");
                        ok = false;
                    }
                }
            }
        }

        return ok;
    }

    /**
     * Retrieves a String representation of this object.
     * 
     * @return a <code>String</code> representation of this object.
     * @see Object#toString()
     */
    public String toString() {
        StringWriter w = new StringWriter();
        try {
            write(w, false);
        } catch(IOException e) {
            throw new Error("Should not happen? " + e);
        }
        return w.toString();
    }

    public String dumpDFA() {
        StringWriter w = new StringWriter();
        try {
            write(w, true);
        } catch(IOException e) {
            throw new Error("Should not happen? " + e);
        }
        return w.toString();
    }



    /**
     * Writes a string representation of this object to the specified
     * writer.
     * @param w the writer.
     * @param allStates when set, print all states, not just productive ones.
     */
    public void write(Writer w, boolean allStates) throws IOException {

        startState.computeProductive(ACCEPTING);
        State[] l = startState.breadthFirst();
        HashMap<State, Integer> h = new HashMap<State, Integer>();
        int cnt = 0;
        for (int i = 0; i < l.length; i++) {
            State s = l[i];
            if (allStates || (s.productive & ACCEPTING) != 0) {
                h.put(s, new Integer(cnt));
                cnt++;
            }
        }

        // add the number of tokens indicator
        w.write("T" + nsym + "\n");

        // add the startstate indicator
        if (allStates) {
            w.write("S" + startState.id + "\n");
        } else {
            w.write("S0\n");
        }

        // add each state
        for (int i = 0; i < l.length; i++) {
            State s = l[i];
            if (allStates || (s.productive & ACCEPTING) != 0) {
                // Print the node definition
                int index = s.id;
                if (! allStates) {
                    index = h.get(s).intValue();
                }
                w.write("N" + index);
                if (allStates) {
                    w.write("(");
                    if ((s.productive & ACCEPTING) != 0) {
                        w.write("+");
                    }
                    if ((s.productive & REJECTING) != 0) {
                        w.write("-");
                    }
                    w.write(")");
                }
                w.write("\n");

                // Mark node as accepting if it is
                if ((s.accepting & ACCEPTING) != 0) {
                    w.write("A" + index + "\n");
                }

                // Mark node as rejecting if it is
                if (allStates && (s.accepting & REJECTING) != 0) {
                    w.write("R" + index + "\n");
                }

                for (int j = 0; j < nsym; j++) {
                    State e = s.traverseLink(j);
                    if (e != null
                            && (allStates || (e.productive & ACCEPTING) != 0)) {
                        w.write("E" + index + ":" + Symbols.getSymbol(j) + ":"
                                + (allStates ? e.id : ((Integer) h.get(e)).intValue()) + "\n");
                            }
                }
            }
        }
    }

    /**
     * Writes a string representation of this DFA to the specified file.
     * @param filename the specified file.
     */
    public void write(String filename) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
            write(bw, false);
            bw.close();
        } catch(Exception E) {
            throw new Error(E.toString(), E);
        }
    }

    private void computeCounts(State[] states, int[][] counts) {
        for (int j = 1; j <= maxlen; j++) {
            int[] countjm1 = counts[j-1];
            for (int i = 0; i < states.length; i++) {
                State s = states[i];
                int cnt = 0;
                for (int k = 0; k < nsym; k++) {
                    State sk = s.children[k];
                    if (sk != null) {
                        cnt += countjm1[sk.id];
                    }
                }
                counts[j][s.id] = cnt;
            }
        }
    }

    /**
     * Computes the number of strings with length less than or equal to
     * <code>l</code> that are recognized by the current DFA.
     * @param l the maximum string length
     * @param count the count array.
     * @param acceptOrReject count either from the rejecting DFA or
     * the accepting DFA.
     * @return the number of strings recognized.
     */
    private int computeNStrings(int l, int[][] count, int acceptOrReject) {
        BitSet h = null;
        if (! INCREMENTAL_COUNTS || ! counts_done) {
            State[] myStates = startState.breadthFirst();

            if (USE_PARENT_SETS) {
                h = new BitSet(idMap.length);
            }

            // Initialize
            for (int i = 0; i < myStates.length; i++) {
                State s = myStates[i];
                for (int j = 0; j < count.length; j++) {
                    count[j][s.id] = 0;
                }
                if ((s.accepting & acceptOrReject) != 0) {
                    if (USE_PARENT_SETS) {
                        h.set(s.id);
                    }
                    count[0][s.id] = 1;
                }
            }
        }
        if (USE_PARENT_SETS) {
            if (! INCREMENTAL_COUNTS || ! counts_done) {
                // Compute counts
                for (int k = 1; k <= l; k++) {
                    BitSet h2 = new BitSet(idMap.length);
                    for (int i = h.nextSetBit(0); i >= 0; i = h.nextSetBit(i+1)) {
                        State s = idMap[i];
                        if (s.parentsArray == null) {
                            s.parentsArray = (State[]) s.parents.toArray(new State[0]);
                        }
                        for (int iter = 0; iter < s.parentsArray.length; iter++) {
                            State si = s.parentsArray[iter];
                            for (int j = 0; j < nsym; j++) {
                                if (si.children[j] == s) {
                                    count[k][si.id] += count[k-1][s.id];
                                }
                            }
                            h2.set(si.id);
                        }
                    }
                    h = h2;
                }
            }
        } else if (INCREMENTAL_COUNTS) {
            if (! counts_done) {
                computeCounts(idMap, count);
            }
        } else {
            // This is faster, and does not need parent administration,
            // but we cannot do incremental stuff later on.
            startState.initCount();
            startState.doCount(l, count);
        }

        int n = 0;
        for (int i = 0; i <= l; i++) {
            n += count[i][startState.id];
        }

        if (logger.isDebugEnabled()) {
            logger.debug("DFA: Total number of recognized strings of length <= " + l
                    + " is " + n);
        }

        return n;
    }

    private static State[] l1;
    private static State[] l2;

    /**
     * Computes in how many ways each state can be reached from the startstate
     * with in input of length less than or equal to <code>maxlen</code>.
     * @return an array containing the endstates.
     */
    private State[] reachCount() {
        int c1, c2;
        HashSet<State> h = new HashSet<State>();
        if (l1 == null) {
            l1 = new State[idMap.length];
            l2 = new State[idMap.length];
        }

        // Initialize. Only initialize count fields that we are going to use.
        l1[0] = startState;
        c1 = 1;
        counts[0][startState.id] = 1;
        if (startState.weight > 0) {
            for (int i = 1; i <= maxlen; i++) {
                counts[i][startState.id] = 0;
            }
            h.add(startState);
        }

        // Compute counts
        for (int k = 1; k <= maxlen; k++) {
            int mark = getMark();
            c2 = 0;
            int[] countk = counts[k];
            int[] countkm1 = counts[k-1];
            for (int i = 0; i < c1; i++) {
                State s = l1[i];
                int km1 = countkm1[s.id];
                for (int j = 0; j < s.children.length; j++) {
                    State sj = s.children[j];
                    if (sj != null) {
                        if (sj.mark != mark) {
                            l2[c2++] = sj;
                            sj.mark = mark;
                            countk[sj.id] = 0;
                            if (sj.weight > 0) {
                                if (! h.contains(sj)) {
                                    h.add(sj);
                                    for (int l = 0; l <= maxlen; l++) {
                                        counts[l][sj.id] = 0;
                                    }
                                }
                            }
                        }
                        countk[sj.id] += km1;
                    }
                }
            }
            c1 = c2;
            State[] temp = l1;
            l1 = l2;
            l2 = temp;
        }
        return h.toArray(new State[h.size()]);
    }

    /**
     * Minimize the DFA. HopCrofts algorithm.
     */
    public void minimize() {
        BitSet[] partition = new BitSet[nStates];
        BitSet workList = new BitSet(nStates);
        BitSet partition1 = new BitSet(nStates);
        BitSet partition2 = new BitSet(nStates);
        BitSet partition3 = new BitSet(nStates);

        // Careful! You cannot rely on state ids anymore after this.
        startState.id = -1;
        idMap = startState.breadthFirst();

        counts_done = false;

        for (int i = 0; i < idMap.length; i++) {
            State s = idMap[i];
            if (! REFINED_MDL) {
                if (counts != null) {
                    counts[0][s.id] = 0;
                }
                if (xCounts != null) {
                    xCounts[0][s.id] = 0;
                }
            }
            if ((s.accepting & ACCEPTING) != 0) {
                if (! REFINED_MDL && counts != null) {
                    counts[0][s.id] = 1;
                }
                partition1.set(s.id);
            } else if ((s.accepting & REJECTING) != 0) {
                if (! REFINED_MDL && xCounts != null) {
                    xCounts[0][s.id] = 1;
                }
                partition2.set(s.id);
            } else {
                partition3.set(s.id);
            }
        }

        int npartitions = 0;
        if (partition1.cardinality() != 0) {
            partition[npartitions++] = partition1;
        }
        if (partition2.cardinality() != 0) {
            partition[npartitions++] = partition2;
        }
        if (partition3.cardinality() != 0) {
            partition[npartitions++] = partition3;
        }

        for (int i = 0; i < npartitions; i++) {
            workList.set(i);
        }

        int s = workList.nextSetBit(0);
        while (s != -1) {
            workList.set(s, false);
            BitSet S = partition[s];

            for (int i = 0; i < nsym; i++) {
                BitSet Ia = new BitSet(nStates);
                for (int j = 0; j < idMap.length; j++) {
                    State st = idMap[j].traverseLink(i);
                    if (st != null && S.get(st.id)) {
                        Ia.set(idMap[j].id);
                    }
                }

                // Ia is now the set of all states that can reach partition S
                // on symbol i.

                if (Ia.cardinality() == 0) {
                    continue;
                }

                for (int j = 0; j < npartitions; j++) {
                    BitSet R = partition[j];
                    if (R.intersects(Ia)) {
                        BitSet R1 = (BitSet) R.clone();
                        R1.and(Ia);
                        int c = R1.cardinality();
                        if (c != R.cardinality()) {
                            // c != R.size() means that R has elements that are
                            // not in Ia, i.e. R is not contained in Ia.
                            // R must be split.
                            R.andNot(R1);
                            partition[j] =  R1;
                            partition[npartitions++] = R;
                            // workList.set(j);
                            // workList.set(npartitions-1);
                            if (workList.get(j)) {
                                workList.set(npartitions-1);
                            } else if (c <= R.cardinality()) {
                                workList.set(j);
                            } else {
                                workList.set(npartitions-1);
                            }
                        }
                    }
                }
            }
            s = workList.nextSetBit(0);
        }

        State[] states = new State[npartitions];
        for (int i = 0; i < npartitions; i++) {
            BitSet p = partition[i];
            int ind = p.nextSetBit(0);
            states[i] = idMap[ind];
            ind = p.nextSetBit(ind+1);
            while (ind >= 0) {
                states[i].weight += idMap[ind].weight;
                ind = p.nextSetBit(ind+1);
            }
            if (p.get(startState.id)) {
                startState = states[i];
            }
        }
        for (int i = 0; i < npartitions; i++) {
            if (states[i].parents != null) {
                states[i].parents.clear();
                states[i].parentsArray = null;
            }
        }
        for (int i = 0; i < npartitions; i++) {
            for (int j = 0; j < nsym; j++) {
                State st = states[i].traverseLink(j);
                if (st != null) {
                    for (int k = 0; k < npartitions; k++) {
                        if (partition[k].get(st.id)) {
                            states[i].addEdge(states[k], j);
                            break;
                        }
                    }
                }
            }
        }

        nStates = npartitions;

        nProductive = startState.computeProductive(ACCEPTING);
        if (MDL_COMPLEMENT || MDL_NEGATIVES) {
            nXProductive = startState.computeProductive(REJECTING);
        }
        MDLScore = 0;
        DFAScore = 0;
    }

    private void addConflict(BitSet[] conflicts, int s1, int s2) {
        if (conflicts[s1] == null) {
            conflicts[s1] = new BitSet();
        }
        conflicts[s1].set(s2);
        if (conflicts[s2] == null) {
            conflicts[s2] = new BitSet();
        }
        conflicts[s2].set(s1);
    }

    private void propagateConflict(BitSet[] conflicts, State s1, State s2) {

        addConflict(conflicts, s1.id, s2.id);

        // Propagate to conflict to parents.
        State ps1 = s1.parent;
        State ps2 = s2.parent;
        if (ps1 != null && ps2 != null) {
            for (int i = 0; i < nsym; i++) {
                if (ps1.children[i] == s1 && ps2.children[i] == s2) {
                    // edge on the same symbol to conflicting states, so the
                    // parents are conflicting as well.
                    propagateConflict(conflicts, ps1, ps2);
                    return;
                }
            }
        }
    }

    /**
     * Precomputes conflicts for the initial DFA.
     * @return the conflict sets.
     */
    public BitSet[] computeConflicts() {
        BitSet[] conflicts = new BitSet[idMap.length];
        for (int i = 0; i < idMap.length; i++) {
            State s1 = idMap[i];
            if ((s1.accepting & REJECTING) != 0) {
                for (int j = 0; j < idMap.length; j++) {
                    State s2 = idMap[j];
                    if ((s2.accepting & ACCEPTING) != 0) {
                        propagateConflict(conflicts, s1, s2);
                    }
                }
            }
        }
        return conflicts;
    }
}
