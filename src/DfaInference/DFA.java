package DfaInference;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.apache.commons.math.MathException;
import org.apache.commons.math.special.Gamma;
import org.apache.commons.math.distribution.NormalDistributionImpl;
import org.apache.commons.math.distribution.NormalDistribution;

import sample.Samples;
import sample.Symbols;
import viz.TouchGraphDFAPanel;


/**
 * This class represents a DFA and offers various operations on it.
 */
public final class DFA implements java.io.Serializable, Configuration {

    private static final long serialVersionUID = 1L;

    /** Precompute log(2). */
    private static final double LOG2 = Math.log(2);

    /** Precomputed sums of logs. */
    private static double[] sumLogs;

    static {
        sumLogs = new double[65536];
        sumLogs[0] = 0.0;
        for (int i = 1; i < sumLogs.length; i++) {
            sumLogs[i] = Math.log(i) + sumLogs[i-1];
        }
    }

    /** Precomputed 2logs. */
    private static double[] logs;

    static {
        logs = new double[65536];
        for (int i = 1; i < logs.length; i++) {
            logs[i] = Math.log(i) / LOG2;
        }
    }

    /** Log4j logger. */
    static Logger logger = Logger.getLogger(DFA.class.getName());

    static NormalDistribution normal = new NormalDistributionImpl();

    static {
        if (logger.isInfoEnabled()) {
            String str = "DFA inference configuration:";
            if (INCREMENTAL_COUNTS) {
                str += " Incremental";
            }
            if (USE_PARENT_SETS) {
                str += " ParentSets";
            }
            logger.info(str);

            str = "MDL score configuration:";
            if (MDL_COMPLEMENT) {
                str += " Complement";
            }
            if (NEGATIVES) {
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

    public Symbols symbols;

    /** The learning samples. */
    // ** REMARK: For ' CheckDFA ' is it necessary to keep this. In production:
    // make samples TRANSIENT!!!

    private ArrayList<int[]> samples;

    /**
     * Counts for number of strings recognized by each state, for
     * all lengths from 0 to maxlen.
     */
    double[][] counts;

    /**
     * Counts for number of strings recognized by each state of the rejecting
     * dfa, for all lengths from 0 to maxlen.
     */
    double[][] xCounts;

    /**
     * Temporary for counts, for computing updates.
     */
    private double[][] tempCounts;

    /** Set when counts are initialized. Used for incremental computations. */
    private boolean counts_done = false;

    /** The start state of this DFA. */
    State startState;

    boolean printInfo = false;

    /** Maximum length of input string. */
    int maxlen = 0;

    /** Number of recognized input strings from the sample. */
    int numRecognized = 0;

    /** Number of rejected input strings from the sample. */
    int numRejected = 0;

    /** Number of states in this DFA. */
    int nStates;

    /** Number of edges in this DFA. */
    int nEdges;

    /** Number of accepting states in this DFA. */
    int nAccepting;

    /** Number of rejecting states in this DFA. */
    int nRejecting;

    /** MDL Score of sample. */
    double MDLScore = 0;

    /** Score of this DFA without samples. */
    double DFAScore = 0;

    /** Chi-square sum of the last merge for positive. */
    double chiSquareSum;

    /** Chi-square sum of the last merge for negative. */
    double xChiSquareSum;

    /** Z-transform sum for positive. */
    double zSum;

    /** Z-transform sum for negative. */
    double xZSum;

    int sumCount;

    int xSumCount;

    /** Label score of the last merge. */
    int labelScore = 0;

    /** Number of symbols. */
    int nsym;

    /** For Stamina: count similar states in a merge. */
    int similarStates;
    
    /** For Stamina: penalties for non-similar state merges. */
    int staminaPenalty;
    
    /** Number of productive states. */
    int nProductiveStates = -1;

    /** Number of productive states in the complement DFA. */
    int nXProductiveStates = -1;

    /** Number of productive edges. */
    int nProductiveEdges = -1;

    /** Number of productive edges in the complement DFA. */
    int nXProductiveEdges = -1;

    /** Edges missing in DFA. */
    int missingEdges = 0;

    /** Edges missing in complement DFA. */
    int missingXEdges = 0;

    boolean conflict = false;

    /** Maps ids to states. */
    private State[] idMap;

    /** Temporary storage. */
    private State[] saved;

    /** Index in <code>saved</code> array. */
    private int savedIndex;

    /** Conflicts of initial DFA. */
    private transient BitSet[] conflicts;

    /** Adjacency tables. */
    private BitSet[] adjacencyComplement = null;

    private boolean mustRecomputeProductive;

    private int markCounter = 2;     // Used for state marker

    private ArrayList<State> entranceStates;

    double chance;

    /**
     * Basic constructor, creates an empty DFA.
     * @param nsym the number of symbols used.
     */
    private DFA(int nsym) {
        nStates = 1;
        nEdges = 0;
        this.nsym = nsym;
        startState = new State(nsym);
        symbols = new Symbols();
        entranceStates = new ArrayList<State>();
        entranceStates.add(startState);
    }

    public DFA(Samples samples, boolean printInfo) {
        this(samples);
        this.printInfo = printInfo;
    }

    /**
     * Constructor, creates a DFA recognizing the specified samples.
     * @param samples the samples
     */
    public DFA(Samples samples) {
        this(samples.getSymbols().nSymbols());
        if (USE_ADJACENCY) {
            startState.entrySyms.set(nsym);
        }
        this.symbols = samples.getSymbols();
        if (USE_ADJACENCY) {
            this.adjacencyComplement = samples.getAdjacencyComplement();
        }

        addSample(samples.getLearningSamples());

        if (logger.isDebugEnabled()) {
            if (! checkDFA()) {
                logger.error("From dfa constructor: exit");
                System.exit(1);
            }
            logger.debug("Initial DFA:\n" + dumpDFA());
        }

        if (logger.isInfoEnabled()) {
            logger.info("Created initial DFA, #states " + nStates);
        }

        double nsentences;

        if (nsym <= 1) {
            nsentences = maxlen + 1;
        } else {
            nsentences = (Math.pow(nsym, maxlen + 1) - 1) / (nsym - 1);
        }

        if (logger.isInfoEnabled()) {
            logger.info("PTAScore = " + getMDLComplexity());
            logger.info("trivialScore = "
                    + approximate2LogNoverK(nsentences, this.samples.size()));
        }

        conflicts = samples.getConflicts();
    }

    private void staminaPreprocess(State s) {
        int outDegree1 = 0;

        for (int i = 0; i < nsym; i++) {
            State child = s.children[i];
            if (child != null && child.isProductive()) {
                outDegree1 += 2;
            }
        }

        if (s.isAccepting()) {
            outDegree1++;
        }

        if (! s.isAccepting() && ! s.isRejecting()) {
            // Non-accepting state.

            if (outDegree1 != 0) {
                int new_outgoing1 = outDegree1 + 1;
                double thisChance = Math.pow(outDegree1 / (double) new_outgoing1, s.getTraffic());
                // Now, thisChance is the chance that the sample could occur, if
                // s would be accepting. Make the state rejecting if this is
                // below a certain threshold.
                if (thisChance < THRESHOLD) {
                    s.accepting = REJECTING;
                }
            }
        }
        for (int i = 0; i < nsym; i++) {
            State child = s.children[i];
            if (child != null) {
                if (outDegree1 != 0 && ! child.isProductive()) {
                    int new_outgoing1 = outDegree1 + 1;
                    double thisChance = Math.pow(outDegree1 / (double) new_outgoing1, s.getTraffic());
                    // Now, thisChance is the chance that the sample could occur
                    // if s would actually have a positive edge on symbol i.
                    // Make the whole tree starting at s.edgeWeights[i]
                    // rejecting if this is below a certain threshold.
                    // Also. mark it so that it never will be merged into a productive tree.
                    if (thisChance < THRESHOLD) {
                        s.children[i].markRejectingTree();
                        continue;
                    }
                }
                if (child.isProductive()) {
                    staminaPreprocess(s.children[i]);
                }
            }
        }
    }

    private static class IntArrayComparator implements Comparator<int[]> {

        public int compare(int[] o1, int[] o2) {
            if (o1.length != o2.length) {
                return o2.length - o1.length;
            }
            for (int i = 0; i < o1.length; i++) {
                if (o1[i] != o2[i]) {
                    return o2[i] - o1[i];
                }
            }
            return 0;
        }
    }

    private int[][] sortAndUnique(int[][] samples2) {
        IntArrayComparator comparator = new IntArrayComparator();
        Arrays.sort(samples2, comparator);
        ArrayList<int[]> list = new ArrayList<int[]>();
        int[] prev = null;
        for (int[] element : samples2) {
            if (prev != null && comparator.compare(prev, element) == 0) {
                continue;
            }
            prev = element;
            list.add(element);
        }
        return list.toArray(new int[list.size()][]);
    }

    /**
     * Copying constructor.
     * @param dfa The DFA to copy.
     */
    public DFA(DFA dfa) {

        nStates = dfa.nStates;
        nEdges = dfa.nEdges;
        nAccepting = dfa.nAccepting;
        nRejecting = dfa.nRejecting;
        if (dfa.samples != null) {
            samples = new ArrayList<int[]>(dfa.samples);
        }
        symbols = new Symbols(dfa.symbols);
        maxlen = dfa.maxlen;
        numRecognized = dfa.numRecognized;
        numRejected = dfa.numRejected;
        nsym = dfa.nsym;
        MDLScore = dfa.MDLScore;
        DFAScore = dfa.DFAScore;
        nProductiveStates = dfa.nProductiveStates;
        nXProductiveStates = dfa.nXProductiveStates;
        nProductiveEdges = dfa.nProductiveEdges;
        nXProductiveEdges = dfa.nXProductiveEdges;
        symbols = new Symbols(symbols);

        startState = dfa.startState.copy();
        setIdMap(startState.breadthFirst());
        saved = new State[nStates];
        conflicts = dfa.conflicts;
        adjacencyComplement = dfa.adjacencyComplement;
        if (logger.isDebugEnabled()) {
            logger.debug("Creating a DFA copy");
            if (! checkDFA()) {
                logger.error("From dfa copy constructor: exit");
                System.exit(1);
            }
        }
        entranceStates = new ArrayList<State>();
        for (State s : dfa.entranceStates) {
            entranceStates.add(getState(s.getId()));
        }
    }

    /**
     * Read the dfa from the specified reader, interpreting the input as
     * described below. <br>
     * The input consists of lines that start with a tag and arguments to the
     * tag. The tags are X for example, T for number of tokens, S for startnode,
     * N for node, A for accepting and E for edge. Order is important. In
     * general the order is:
     * <ul>
     * <li> 1. One or more X lines.
     * <li> 2. A T line.
     * <li> 3. An S line.
     * <li> 4. One or more groups of:
     * <ul>
     * <li> a. an N line, optionally followed by an A line.
     * <li> b. followed by zero or more E lines
     * </ul>
     * </ul>
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
     * @param r
     *            reader from which to read the dfa.
     */
    public DFA(Reader r) {
        HashMap<Integer, State> nodes = new HashMap<Integer, State>();

        symbols = new Symbols();

        try {
            String line;
            BufferedReader br = new BufferedReader(r);
            Integer startNodeId = null;
            while ((line = br.readLine()) != null) {
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
                        throw new Error(
                                "File format error: multiple start states!");
                    }
                    startNodeId = new Integer(line.substring(1));
                    if (nsym == 0) {
                        throw new Error(
                                "File format error: number of tokens not specified before startnode defined");
                    }
                    startState = new State(nsym);
                    if (USE_ADJACENCY) {
                        startState.entrySyms.set(nsym);
                    }
                    nodes.put(startNodeId, startState);
                    nStates = 1;
                    nEdges = 0;
                    break;

                case 'N': {
                    // Treat node definition
                    if (startNodeId == null) {
                        throw new Error(
                                "File format error: node defined before startnode defined!");
                    }
                    // add node only if it is not the start node (which is
                    // already
                    // created)
                    Integer id = new Integer(line.substring(1));
                    if (nodes.get(id) == null) {
                        State s = new State(nsym);
                        nodes.put(id, s);
                        nStates++;
                    }
                    break;
                }

                case 'E': {
                    // Treat edge definition
                    if (startNodeId == null) {
                        throw new Error(
                                "File format error: edge defined before startnode defined!");
                    }

                    int lastIndex = line.indexOf(':');
                    // first field defines from node
                    if (lastIndex == -1) {
                        throw new Error(
                                "File format error: Invalid E line (1)!");
                    }
                    String from = line.substring(1, lastIndex);

                    // next field defines label, and last field defines to node
                    int lastLastIndex = lastIndex;
                    lastIndex = line.indexOf(':', lastIndex + 1);
                    if (lastIndex == -1) {
                        throw new Error(
                                "File format error: Invalid E line (2)!");
                    }
                    String label_s = line.substring(lastLastIndex + 1,
                            lastIndex);
                    int label = symbols.addSymbol(label_s);
                    String to = line.substring(lastIndex + 1);

                    Integer fromVal = new Integer(from);
                    Integer toVal = new Integer(to);

                    // only from node needs to exist; note that this is
                    // a bug, we should really check if the destination
                    // node exists at the end of the read operation
                    State s = nodes.get(fromVal);
                    if (s == null) {
                        throw new Error("File format error: E with from "
                                + "node for which no N line seen yet!");
                    }

                    State d = nodes.get(toVal);
                    if (d == null) {
                        d = s.addDestination(true, label);
                        nodes.put(toVal, d);
                        nStates++;
                    } else {
                        s.addEdge(d, label);
                    }
                    nEdges++;
                    break;
                }

                case 'A': {
                    // Treat accepting indicator (node must be defined)
                    State d = nodes.get(new Integer(line.substring(1)));
                    if (d == null) {
                        throw new Error(
                                "Trying to set Accept of node for which no N line seen!");
                    }
                    d.accepting = ACCEPTING;
                    nAccepting++;
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
        } catch (IOException E) {
            throw new Error(E.toString());
        }

        dfaComputations(true);
        entranceStates = new ArrayList<State>();
        entranceStates.add(startState);
        if (USE_ADJACENCY) {
            computeAdjacency();
        }

        if (logger.isDebugEnabled()) {
            if (!checkDFA()) {
                logger.error("From dfa reader constructor: exit");
                System.exit(1);
            }
        }
    }
    
    TouchGraphDFAPanel panel = null;

    private void dfaComputations(boolean reIndex) {
        State[] states;

        startState.computeDepths();

        if (reIndex) {
            startState.setId(-1);
            setIdMap(startState.breadthFirst());
            nStates = getIdMap().length;
            states = getIdMap();
            conflicts = null;
        } else {
            states = startState.breadthFirst();
        }
        nAccepting = 0;
        nRejecting = 0;
        nEdges = 0;
        for (State s : states) {
            if (PRINT_DFA) {
                /*
                    if (panel == null) {
                        panel = TouchGraphDFAPanel.createPanel();
                    }
                    panel.setDFA(this);
                    try {
                        Thread.sleep(100000000);
                    } catch (InterruptedException e) {
                        // ignored
                    }
                */

                System.out.print("State " + s.getId());
                if (s.isRejecting()) {
                    System.out.print(" is rejecting, weight = " + s.getWeight());
                } else if (s.isAccepting()) {
                    System.out.print(" is accepting, weight = " + s.getWeight());
                }
                System.out.println();
                for (int i = 0; i < nsym; i++) {
                    if (s.children[i] != null) {
                        System.out.println("    edge on symbol " + symbols.getSymbol(i) + " to state " + s.children[i].getId()
                                + ", traffic = " + s.edgeWeights[i] + ", xtraffic = " + s.xEdgeWeights[i]);
                    }
                }
            }
            if (s.getWeight() > 0) {
                if (s.isRejecting()) {
                    nRejecting++;
                } else if (s.isAccepting()) {
                    nAccepting++;
                }
            }
            for (int i = 0; i < nsym; i++) {
                if (s.children[i] != null) {
                    nEdges++;
                }
            }
        }
        saved = new State[nStates];
        nProductiveStates = startState.computeProductiveStates(ACCEPTING);
        nProductiveEdges = computeProductiveEdges(getIdMap(), ACCEPTING);
        if (NEGATIVES || MDL_COMPLEMENT) {
            nXProductiveStates = startState.computeProductiveStates(REJECTING);
            nXProductiveEdges = computeProductiveEdges(getIdMap(), REJECTING);
        }

        missingEdges = computeMissingEdges(getIdMap(), ACCEPTING);
        if (MDL_COMPLEMENT || NEGATIVES) {
            missingXEdges = computeMissingEdges(getIdMap(), REJECTING);
        }
        counts = null;
        xCounts = null;
        MDLScore = 0;
        DFAScore = 0;
        counts_done = false;
    }

    public DFA(DFA dfa1, DFA dfa2) throws ConflictingMerge {
        this(dfa1, dfa2, true);
    }

    /**
     * Creates a DFA that is the result of merging the specified DFAs. This may
     * or may not be an exact merge, depending on the <code>exact</code>
     * flag.
     *
     * @param dfa1
     *            the first dfa.
     * @param dfa2
     *            the second dfa.
     * @param exact
     *            determines wether the merge is exact or generalizing.
     * @throws ConflictingMerge
     *             when the merge results in a conflict.
     */
    public DFA(DFA dfa1, DFA dfa2, boolean exact) throws ConflictingMerge {
        // First merge symbol sets.
        symbols = new Symbols(dfa1.symbols);
        int[] dfa2SymbolMap = symbols.addSymbols(dfa2.symbols);
        nsym = symbols.nSymbols();

        if (dfa1.samples != null && dfa2.samples != null) {
            samples = new ArrayList<int[]>(dfa1.samples);
            for (int[] element : dfa2.samples) {
                int[] cp = new int[element.length];
                cp[0] = element[0]; // first byte of element contains flag
                                    // whether sample is positive or negative
                for (int i = 1; i < cp.length; i++) {
                    cp[i] = dfa2SymbolMap[element[i]];
                }
                samples.add(cp);
            }
        }
        // Then, copy and states and possibly re-index the children arrays.
        Numberer numberer = new Numberer();
        HashMap<State, State> map = new HashMap<State, State>();
        State state1 = new State(dfa1.startState, null,
                map, null, numberer, dfa1.symbols.nSymbols(), nsym);
        State state2 = new State(dfa2.startState, null,
                map, dfa2SymbolMap, numberer, 0, nsym);

        entranceStates = new ArrayList<State>();
        for (State s : dfa1.entranceStates) {
            entranceStates.add(map.get(s));
        }
        for (State s : dfa2.entranceStates) {
            entranceStates.add(map.get(s));
        }
        // compute a new idMap array.
        nStates = dfa1.nStates + dfa2.nStates;
        nAccepting = dfa1.nAccepting + dfa2.nAccepting;
        nRejecting = dfa1.nRejecting + dfa2.nRejecting;
        setIdMap(new State[nStates]);
        state1.fillIdMap(getIdMap());
        state2.fillIdMap(getIdMap());
        maxlen = dfa1.maxlen;
        if (dfa2.maxlen > maxlen) {
            maxlen = dfa2.maxlen;
        }

        // For now, kill the entrance sets. It is not clear how to deal with them.
        for (State s : entranceStates) {
            if (s == state1 || s == state2) {
                continue;
            }
            // warn for ignored entrance state.
            logger.warn("Ignored entrance state during DFA merging");
        }
        if (exact) {
            exactMerge(state1, state2);
        } else {
            // Compute equivalence sets. The resulting array of sets is
            // indexed by state id. Equivalent states have the same equivalence
            // set.
            BitSet[] merges = merge(state1, state2);
            startState = new State(state1, merges, new State[getIdMap().length],
                    getIdMap(), new Numberer());
        }
        entranceStates.clear();
        entranceStates.add(startState);

        dfaComputations(true);

        double d1Accepted = dfa1.computeTotalRecognized(maxlen, ACCEPTING);
        double d1Rejected = dfa1.computeTotalRecognized(maxlen, REJECTING);
        double d2Accepted = dfa2.computeTotalRecognized(maxlen, ACCEPTING);
        double d2Rejected = dfa2.computeTotalRecognized(maxlen, REJECTING);

        double newAccepted = computeTotalRecognized(maxlen, ACCEPTING);
        double newRejected = computeTotalRecognized(maxlen, REJECTING);

        double acceptedOverlap = d1Accepted + d2Accepted - newAccepted;
        double rejectedOverlap = d1Rejected + d2Rejected - newRejected;

/*
        double d1AcceptedOld = dfa1.computeTotalRecognized(dfa1.maxlen, ACCEPTING);
        double d1RejectedOld = dfa1.computeTotalRecognized(dfa1.maxlen, REJECTING);
        double d2AcceptedOld = dfa2.computeTotalRecognized(dfa2.maxlen, ACCEPTING);
        double d2RejectedOld = dfa2.computeTotalRecognized(dfa2.maxlen, REJECTING);

        double d1NumRecognized = dfa1.numRecognized * (d1Accepted / Math.max(1.0, d1AcceptedOld));
        double d1NumRejected = dfa1.numRejected * (d1Rejected / Math.max(1.0, d1RejectedOld));
        double d2NumRecognized = dfa2.numRecognized * (d2Accepted / Math.max(1.0, d2AcceptedOld));
        double d2NumRejected = dfa2.numRejected * (d2Rejected / Math.max(1.0, d2RejectedOld));
        numRecognized = (int) ((d1NumRecognized + d2NumRecognized)
                * (1 - acceptedOverlap/(d1Accepted + d2Accepted)));
        if (newRejected != 0) {
            numRejected = (int) ((d1NumRejected + d2NumRejected)
                    * (1 - rejectedOverlap/(d1Rejected + d2Rejected)));
        }
*/

        // Make a guess for numRecognized. We cannot just add the ones from
        // dfa1 and dfa2, because there may be overlap, and we have no way
        // of knowing. The best we can do is estimate, by incorporating
        // the overlap in recognized strings.
        numRecognized = (int) ((dfa1.numRecognized + dfa2.numRecognized)
                * (1 - acceptedOverlap/(d1Accepted + d2Accepted)));
        if (newRejected != 0) {
            numRejected = (int) ((dfa1.numRejected + dfa2.numRejected)
                    * (1 - rejectedOverlap/(d1Rejected + d2Rejected)));
        }
        System.out.println("numRecognized = " + numRecognized);
        System.out.println("totalRecognized = " + newAccepted);

        if (USE_ADJACENCY) {
            computeAdjacency();
        }

        if (logger.isDebugEnabled()) {
            if (!checkDFA()) {
                System.out.println(dumpDFA());
                logger.error("From dfa merging constructor: exit");
                System.exit(1);
            }
            logger.debug("Merged DFA: nstates = " + nStates + ", nProductiveStates = " + nProductiveStates);
            logger.debug("DFA1.nstates = " + dfa1.nStates + ", dfa2.nStates = " + dfa2.nStates);
            logger.debug("d1Accepted = " + d1Accepted + ", d2Accepted = " + d2Accepted);
            logger.debug("dfa1.numRecognized = " + dfa1.numRecognized);
            logger.debug("dfa2.numRecognized = " + dfa2.numRecognized);
        }
    }

    public void fullMerge(State s1, State s2) throws ConflictingMerge {
        BitSet[] merges = merge(s1, s2);
        if (USE_STAMINA) {
            chance = 1.0;
            similarStates = 0;
            BitSet[] newMerges = merges.clone();
            for (int j = 0; j < newMerges.length; j++) {
        	BitSet b = newMerges[j];
        	if (b == null) {
        	    continue;
        	}
        	int c1 = b.nextSetBit(0);
        	int i = c1;
        	State state1 = getState(c1);
        	for (i = b.nextSetBit(i+1); i != -1; i = b.nextSetBit(i+1)) {
        	    newMerges[i] = null;
        	    State state2 = getState(i);
        	    if ((state1.productiveForbidden && state2.isProductive())
        		    || (state2.productiveForbidden && state1.isProductive())) {
        		throw new ConflictingMerge();
        	    }
        	    // System.out.println("State " + c1 + ", State " + i);
        	    double chance2 = computeStaminaChance(state1, state2);
        	    if (chance > chance2) {
        		// System.out.println("Chance becomes " + chance2);
        		chance = chance2;
        		if (chance < THRESHOLD) {
        		    throw new ConflictingMerge();
        		}
        	    }
         	}
            }
        }
        startState = new State(startState, merges, new State[getIdMap().length],
                getIdMap(), new Numberer());
        dfaComputations(true);
        if (USE_ADJACENCY) {
            for (State s : getIdMap()) {
                for (int i = 0; i < nsym; i++) {
                    if (s.children[i] != null) {
                        if (s.entrySyms.intersects(adjacencyComplement[i])) {
                            throw new ConflictingMerge("Adjacency");
                        }
                    }
                }
                if (s.isAccepting()) {
                    if (s.entrySyms.intersects(adjacencyComplement[nsym])) {
                        throw new ConflictingMerge("Adjacency");
                    }
                }
            }
        }
    }

    /**
     * Actually merges two DFAs, making the result deterministic on the fly.
     * This method implements an exact merge, that is, the resulting DFA
     * accepts sentences that are accepted by either one (or both) of the
     * original DFAs, and rejects all others.
     * @param state1 startstate of the first DFA.
     * @param state2 startstate of the second DFA.
     * @throws ConflictingMerge is thrown when a conflict is detected,
     *     t.i., a state turns out to be accepting as well as rejecting.
     */
    private void exactMerge(State state1, State state2) throws ConflictingMerge {
        ArrayList<BitSet> workList = new ArrayList<BitSet>();
        HashMap<BitSet, State> map = new HashMap<BitSet, State>();
        BitSet initial = new BitSet(nStates);
        nEdges = 0;

        // Mimic a new startstate consisting of the union of the two startstates.
        initial.set(state1.getId());
        initial.set(state2.getId());
        workList.add(initial);

        startState = new State(nsym);
        startState.accepting = (byte)(state1.accepting | state2.accepting);
        startState.setWeight(state1.getWeight() + state2.getWeight());
        startState.setTraffic(state1.getTraffic() + state2.getTraffic());
        startState.setxTraffic(state1.getxTraffic() + state2.getxTraffic());

        if (NEEDS_EDGECOUNTS) {
            for (int i = 0; i < nsym; i++) {
                startState.edgeWeights[i] = state1.edgeWeights[i] + state2.edgeWeights[i];
                startState.xEdgeWeights[i] = state1.xEdgeWeights[i] + state2.xEdgeWeights[i];
            }
        }
        if (startState.accepting == (ACCEPTING|REJECTING)) {
            throw new ConflictingMerge("found conflict!");
        }
        map.put(initial, startState);
        nEdges = 0;

        // Each entry on the worklist represents a state in the new DFA that
        // is yet to be processed.
        while (workList.size() > 0) {
            BitSet current = workList.remove(0);
            State currentState = map.get(current);
            // For each symbol, determine the set of target states from the
            // original DFAs.
            for (int sym = 0; sym < nsym; sym++) {
                BitSet target = new BitSet(nStates);
                boolean toAdd = false;

                byte accepting = 0;
                int weight = 0;
                int traffic = 0;
                int xTraffic = 0;
                int[] edgeWeights = null;
                int[] xEdgeWeights = null;
                if (NEEDS_EDGECOUNTS) {
                    edgeWeights = new int[nsym];
                    xEdgeWeights = new int[nsym];
                }

                for (int i = current.nextSetBit(0); i != -1; i = current.nextSetBit(i + 1)) {
                    State si = getState(i);
                    State child = si.children[sym];
                    if (child != null) {
                        target.set(child.getId());
                        accepting |= child.accepting;
                        weight += child.getWeight();
                        traffic += child.getTraffic();
                        xTraffic += child.getxTraffic();
                        if (NEEDS_EDGECOUNTS) {
                            for (int j = 0; j < nsym; j++) {
                                edgeWeights[j] += child.edgeWeights[j];
                                xEdgeWeights[j] += child.xEdgeWeights[j];
                            }
                        }
                        toAdd = true;
                    }
                }
                // See if we found any transitions. If not, continue with
                // the next symbol.
                if (! toAdd) {
                    continue;
                }
                if (accepting == (ACCEPTING|REJECTING)) {
                    throw new ConflictingMerge("found conflict!");
                }
                // Now we have a set of target states, which is a state in the
                // new DFA. Maybe we already have it ...
                State newTarget = map.get(target);
                if (newTarget == null) {
                    // No we don't. Add it, and add it to the worklist as well.
                    newTarget = new State(nsym);
                    newTarget.accepting = accepting;
                    newTarget.setWeight(weight);
                    newTarget.setTraffic(traffic);
                    newTarget.setxTraffic(xTraffic);
                    newTarget.edgeWeights = edgeWeights;
                    newTarget.xEdgeWeights = xEdgeWeights;
                    workList.add(target);
                    map.put(target, newTarget);
                }
                currentState.addEdge(newTarget, sym);
                nEdges++;
            }
        }
    }

    /**
     * Determines the merge sets which are the result of merging the specified
     * states and making the DFA deterministic.
     *
     * @param s1
     *            the first state to merge.
     * @param s2
     *            the second state to merge.
     * @return the bitsets containing the merge sets.
     * @throws ConflictingMerge
     */
    private BitSet[] merge(State s1, State s2) throws ConflictingMerge {

        LinkedList<BitSet> l = new LinkedList<BitSet>();
        BitSet temp = new BitSet(nStates);
        BitSet newMerge = new BitSet(nStates);
        BitSet acceptingStates = new BitSet(nStates);
        BitSet rejectingStates = new BitSet(nStates);
        BitSet[] mergeSets = new BitSet[nStates];

        BitSet b = new BitSet();
        b.set(s1.getId());
        b.set(s2.getId());

        for (State s : getIdMap()) {
            if ((s.accepting & ACCEPTING) != 0) {
                acceptingStates.set(s.getId());
            } else if ((s.accepting & REJECTING) != 0) {
                rejectingStates.set(s.getId());
            }
        }

        // Add the set containing the two states to the list of sets to process.
        l.addFirst(b);

        // Process list of equivalence sets.
        while (l.size() != 0) {
            int n;
            b = l.removeFirst();

            // Add all states that are known to be equivalent to a state in
            // b to b.
            for (int i = b.nextSetBit(0); i != -1; i = b.nextSetBit(i + 1)) {
                if (mergeSets[i] != null) {
                    b.or(mergeSets[i]);
                }
            }

            // Mark all these states with the current equivalence set.
            for (n = b.nextSetBit(0); n != -1; n = b.nextSetBit(n + 1)) {
                mergeSets[n] = b;
            }

            if (logger.isInfoEnabled()) {
                logger.debug("Merging: " + b);
            }

            // Check for conflicts: an accepting state cannot be equivalent
            // with a rejecting state.
            if (b.intersects(acceptingStates) && b.intersects(rejectingStates)) {
                throw new ConflictingMerge("Conflict on merge set " + b);
            }

            // Find conflicts, i.e. new merge sets.
            for (int i = 0; i < symbols.nSymbols(); i++) {
                for (n = b.nextSetBit(0); n != -1; n = b.nextSetBit(n + 1)) {
                    State t = getIdMap()[n].traverseLink(i);
                    if (t != null) {
                        if (mergeSets[t.getId()] != null) {
                            newMerge.or(mergeSets[t.getId()]);
                        } else {
                            newMerge.set(t.getId());
                        }
                    }
                }

                // newMerge now contains either a proposed merge, or a
                // singleton.
                n = newMerge.nextSetBit(0);
                int n1 = newMerge.nextSetBit(n + 1);
                if (n1 != -1) {
                    // The set newMerge is not a singleton, so it contains a
                    // proposed merge. Find out if this proposed merge has
                    // not already been done.
                    // This is the case if the mergeSet of the first member
                    // does not exist yet or does not contain the proposed
                    // merge.
                    if (mergeSets[n] == null) {
                        // Does not exist yet. Add it to the list of sets to
                        // process.
                        addMerge(l, newMerge);
                    } else {
                        // Check if it contains the proposed merge.
                        // BitSet has no contains() or subset() operator, so
                        // create a copy, intersect it with the proposed
                        // merge, and compare with the proposed merge.
                        temp.or(mergeSets[n]);
                        temp.and(newMerge);
                        if (!temp.equals(newMerge)) {
                            addMerge(l, newMerge);
                        }
                        temp.clear();
                    }
                }
                newMerge.clear();
            }
        }
        return mergeSets;
    }

    private void addMerge(LinkedList<BitSet> l, BitSet s) {
        for (BitSet el : l) {
            if (el.intersects(s)) {
                el.or(s);
                return;
            }
        }
        l.addFirst((BitSet) s.clone());
    }

    private void computeAdjacency() {

        State[] list = startState.breadthFirst();

        // Allocate entry symbol sets for all states.
        for (State s : list) {
            s.entrySyms = new BitSet(nsym+1);
        }

        // And compute its contents.
        startState.entrySyms.set(nsym);
        for (State s : list) {
            State[] c = s.children;
            for (int i = 0; i < nsym; i++) {
                if (c[i] != null) {
                    c[i].entrySyms.set(i);
                }
            }
        }

        // Now compute adjacencies. First allocate sets.
        BitSet[] adjacency = new BitSet[nsym+1];
        for (int i = 0; i <= nsym; i++) {
            adjacency[i] = new BitSet(nsym+1);
        }

        // Then, compute.
        for (State s : list) {
            State[] c = s.children;
            for (int i = 0; i < nsym; i++) {
                if (c[i] != null) {
                    adjacency[i].or(s.entrySyms);
                }
            }
            if (s.isAccepting()) {
                adjacency[nsym].or(s.entrySyms);
            }
        }

        // And compute complements.
        adjacencyComplement = adjacency;
        for (BitSet b : adjacencyComplement) {
            b.flip(0, nsym+1);
        }
    }

    /**
     * Computes the number of edges that are needed to make the DFA functionally
     * complete (every state has an outgoing edge on all symbols). We don't
     * actually make the DFA functionally complete, but we could compute scores
     * as if it is. Every edge computed here would then account for one extra
     * state, with edges to itself on all symbols.
     *
     * @param flag
     *            either <code>ACCEPTING</code> or <code>REJECTING</code>,
     *            deciding if the accepting or rejecting DFA is used.
     * @return the number of additional edges needed.
     */
    private int computeMissingEdges(State[] map, byte flag) {

        int sum = 0;

        for (int i = 0; i < map.length; i++) {
            sum += map[i].missingEdges(flag);
        }
        return sum;
    }

    /**
     * Computes the number of productive edges in the DFA.
     * @param flag either <code>ACCEPTING</code> or <code>REJECTING</code>,
     * deciding if the accepting or rejecting DFA is used.
     * @return the number of productive edges.
     */
    private int computeProductiveEdges(State[] map, byte flag) {
        int sum = 0;

        for (int i = 0; i < map.length; i++) {
            int cnt = map[i].productiveEdges(flag);
            sum += cnt;
        }
        return sum;
    }

    /**
     * Returns the state identified by the specified id.
     *
     * @param id
     *            the identification
     * @return the state.
     */
    public State getState(int id) {
        if (id < 0) {
            return null;
        }
        return getIdMap()[id];
    }

    /**
     * Converts a string, of which each character is assumed to be a separate
     * symbol, to an array of integers.
     */
    public int[] stringToSymbols(String sentence) {
        int len = sentence.length();
        int [] symbol_array = new int[len+1];

        symbol_array[0] = len;
        for (int i = 0; i < len; i++) {
            String substr = sentence.substring(i, i+1);
            symbol_array[i+1] = symbols.addSymbol(substr);
        }
        return symbol_array;
    }

    /**
     * Adds states and edges from the given symbol string.
     * Symbols[0] tells whether this is an accept or reject.
     * Note: this method has the implicit assumption that this is a new sample,
     * not present before. (Ceriel)
     *
     * @param symbols        the given symbol string.
     */
    public void addString(int[] symbols) {
        State n = startState;
        boolean reject = symbols[0] != 1;
        if (reject) {
            n.setxTraffic(n.getxTraffic() + 1);
        } else {
            n.setTraffic(n.getTraffic() + 1);
        }

        for (int i = 1; i < symbols.length; i++) {
            State target = n.traverseEdge(symbols[i]);
            if (target == null) {
                target = n.addDestination(! reject, symbols[i]);
                nStates++;
                nEdges++;
            }
            if (reject) {
                target.setxTraffic(target.getxTraffic() + 1);
            } else {
                target.setTraffic(target.getTraffic() + 1);
            }

            if (NEEDS_EDGECOUNTS) {
                if (reject) {
                    n.xEdgeWeights[symbols[i]]++;
                } else {
                    n.edgeWeights[symbols[i]]++;
                }
            }
            n = target;
        }

        if (reject) {
            if (n.getWeight() == 0) {
        	numRejected++;
        	nRejecting++;
        	n.accepting = REJECTING;
            }
            n.setWeight(n.getWeight() + 1);
        } else {
            if (n.getWeight() == 0) {
        	numRecognized++;
        	nAccepting++;
        	n.accepting = ACCEPTING;
            }
            n.setWeight(n.getWeight() + 1);
        }
    }

    /**
     * Adds states and edges from the specified sample.
     * @param samples        the specified sample.
     */
    public void addSample(int[][] samples) {
        if (UNIQUE_SAMPLES) {
            samples = sortAndUnique(samples);
        }

        if (this.samples == null) {
            this.samples = new ArrayList<int[]>();
            maxlen = 0;
        }

        for (int[] el : samples) {
            this.samples.add(el);
        }

        MDLScore = 0;
        DFAScore = 0;

        for (int i = 0; i < samples.length; i++) {
            if (samples[i].length > maxlen + 1) {
                maxlen = samples[i].length - 1;
            }
        }
        for (int i = 0; i < samples.length; i++) {
            addString(samples[i]);
        }
        /*
        if (USE_STAMINA) {
            staminaPreprocess(startState);
        }
        */
        dfaComputations(true);
        if (USE_ADJACENCY) {
            startState.entrySyms.set(nsym);
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
        addSample(samples);
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
     * Attempts to recognize the given string. Each character in the string is
     * assumed to be separate symbol. Returns <code>true</code> if accepted,
     * <code>false</code> if not.
     *
     * @param sentence
     *            the given string.
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
     * Obtains the number of edges.
     *
     * @return number of edges
     */
    public int getNumEdges() {
        return nEdges;
    }

    /**
     * Obtains the number of productive states.
     *
     * @return number of productive states
     */
    public int getNumProductiveStates() {
        return nProductiveStates;
    }

    /**
     * Obtains the number of productive edges.
     *
     * @return number of productive edges
     */
    public int getNumProductiveEdges() {
        return nProductiveEdges;
    }

    /**
     * Obtains the number of accepting states.
     *
     * @return number of accepting states.
     */
    public int getNumAcceptingStates() {
        return nAccepting;
    }

    /**
     * Obtains the number of rejecting states.
     *
     * @return number of rejecting states.
     */
    public int getNumRejectingStates() {
        return nRejecting;
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
        if (markCounter >= Integer.MAX_VALUE - getIdMap().length) {
            for (int i = 0; i < getIdMap().length; i++) {
                getIdMap()[i].mark = 0;
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
                for (State ps : s.parents) {
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
                if (s.productive != ((NEGATIVES || MDL_COMPLEMENT)
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
                                if (USE_STAMINA) {
                                    double c1 = Math.pow(.5, s.getxTraffic());
                                    chance *= c1;
                                    staminaPenalty++;
                                }
                                nProductiveStates++;
                                missingEdges += s.missingEdges(ACCEPTING);
                                nProductiveEdges += s.productiveEdges(ACCEPTING);
                            }
                        }
                        if ((NEGATIVES || MDL_COMPLEMENT)
                            && (prod & REJECTING) != 0) {
                            if ((s.productive & REJECTING) == 0) {
                                s.productive |= REJECTING;
                                if (USE_STAMINA) {
                                    double c1 = Math.pow(.5, s.getTraffic());
                                    chance *= c1;
                                }
                                nXProductiveStates++;
                                missingXEdges += s.missingEdges(REJECTING);
                                nXProductiveEdges += s.productiveEdges(REJECTING);
                            }
                        }
                        s.productive |= prod;
                    }
                }
            }
        }
    }

    /**
     * Merges the specified red and blue states, including the implied merges to
     * make the DFA deterministic again. If afterwards the DFA has a conflict,
     * it is marked as such.
     */
    public UndoInfo treeMerge(State red, State blue, boolean undoNeeded,
            State[] redStates, int numRedStates) {
        UndoInfo undo = null;

        if (conflicts != null && conflicts[red.getId()] != null) {
            if (conflicts[red.getId()].get(blue.getId())) {
                conflict = true;
                return undo;
            }
        }

        if (blue.hasConflict(red)) {
            conflict = true;
            return undo;
        }

        State parent = blue.parent;

        mustRecomputeProductive = (parent.productive | red.productive) != parent.productive;

        // Make the blue node's parent indicate the red node.
        savedIndex = 1;

        saved[0] = parent;
        parent.savedIndex = 0;

        if (undoNeeded) {
            if (INCREMENTAL_COUNTS && !counts_done) {
                getMDLComplexity();
            }
            undo = UndoInfo.getUndoInfo(this);
            undo.addData(parent);
        }

        State[] pch = parent.children;
        for (int j = 0; j < nsym; j++) {
            if (pch[j] == blue) {
                if ((blue.productive & ACCEPTING) == 0
                        && (red.productive & ACCEPTING) != 0) {
                    nProductiveEdges++;
                }
                if ((blue.productive & REJECTING) == 0
                        && (red.productive & REJECTING) != 0) {
                    nXProductiveEdges++;
                }
                addEdge(undo, parent, j, red);
            }
        }

        // Recurse while merging ...
        labelScore = 0;
        chance = 1.0;
        chiSquareSum = 0.0;
        xChiSquareSum = 0.0;
        zSum = 0.0;
        xZSum = 0.0;
        sumCount = 0;
        xSumCount = 0;
        similarStates = 0;
        staminaPenalty = 0;
        // int savedNStates = nStates;
        walkTreeMerge(red, blue, undo);

        DFAScore = 0;
        if (conflict) {
            blue.addConflict(red);
            return undo;
        }

        // int diff = savedNStates - nStates;
        MDLScore = 0;

        if (mustRecomputeProductive || (INCREMENTAL_COUNTS && (counts != null))) {
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
                        states[i].mark = mark + i;
                        if (states[i] == startState) {
                            startStateIndex = i;
                        }
                    }

                    if (tempCounts == null || tempCounts.length < states.length) {
                        tempCounts = new double[states.length][maxlen + 1];
                    }

                    undo.saveCounts();

                    startState.computeUpdate(maxlen, counts, tempCounts, mark);
                    for (int i = 1; i <= maxlen; i++) {
                        counts[i][startState.getId()] = tempCounts[startStateIndex][i];
                    }
                    if (NEGATIVES) {
                        for (int i = 0; i < states.length; i++) {
                            states[i].maxLenComputed = 0;
                        }
                        startState.computeUpdate(maxlen, xCounts, tempCounts,
                                mark);
                        for (int i = 1; i <= maxlen; i++) {
                            xCounts[i][startState.getId()] = tempCounts[startStateIndex][i];
                        }
                    }
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
            if (!checkDFA()) {
                System.out.println("Problem after merge.");
                if (undo != null) {
                    System.out.println("DFA before merge was: ");
                    undoMerge(undo);
                    System.out.println(dumpDFA());
                }
                System.exit(1);
            }
        }
        return undo;
    }

    private boolean givesAdjacencyConflict(State s1, State s2) {
        for (int j = 0; j < nsym; j++) {
            if (s1.children[j] == null) {
                if (s2.children[j] != null) {
                    // Check if all entry symbols of state s1 can be
                    // adjacent to symbol j. The adjacencyComplement sets
                    // already contain the complement of the (in front of)
                    // adjacency set. This complement set must have no symbols
                    // in common with the entry symbols of s1, or the entry
                    // symbols of s1 are not a subset of the adjacency set!
                    if (ONLY_CHECK_BEGIN_AND_END_ADJACENCY) {
                        if (s1.entrySyms.get(nsym) && adjacencyComplement[j].get(nsym)) {
                            return true;
                        }
                    } else if (s1.entrySyms.intersects(adjacencyComplement[j])) {
                        return true;
                    }
                }
            } else if (s2.children[j] == null) {
                if (ONLY_CHECK_BEGIN_AND_END_ADJACENCY) {
                    if (s2.entrySyms.get(nsym) && adjacencyComplement[j].get(nsym)) {
                        return true;
                    }
                } else if (s2.entrySyms.intersects(adjacencyComplement[j])) {
                    return true;
                }
            }
        }
        if (! s1.isAccepting()) {
            if (s2.isAccepting()) {
                if (s1.entrySyms.intersects(adjacencyComplement[nsym])) {
                    return true;
                }
            }
        } else if (! s2.isAccepting()) {
            if (s2.entrySyms.intersects(adjacencyComplement[nsym])) {
                return true;
            }
        }
        return false;
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

    private double computeStaminaChance(State n1, State n2) {
        // According to the Stamina description (website), a transfer
        // to any state is twice as likely as stopping (if in an
        // accepting state).
        int outDegree1 = 0;
        int outDegree2 = 0;
        int xOutDegree1 = 0;
        int xOutDegree2 = 0;

        for (int i = 0; i < nsym; i++) {
            State child1 = n1.children[i];
            State child2 = n2.children[i];
            if (child1 != null) {
        	if (child1.isProductive()) {
        	    outDegree1 += 2;
        	}
        	if (child1.isXProductive()) {
        	    xOutDegree1 += 2;
        	}
            }
            if (child2 != null) {
        	if (child2.isProductive()) {
        	    outDegree2 += 2;
        	}
        	if (child2.isXProductive()) {
        	    xOutDegree2 += 2;
        	}
            }
        }
        if (n1.isAccepting()) {
            outDegree1++;
        }
        if (n2.isAccepting()) {
            outDegree2++;
        }
        if (n1.isRejecting()) {
            xOutDegree1++;
        }
        if (n2.isRejecting()) {
            xOutDegree2++;
        }

        int newOutDegree1 = outDegree1;
        int newOutDegree2 = outDegree2;

        // If the merged state is getting new edges or new accepting status, either
        // with respect to n1 or n2, compute the chance that the sample could have
        // been generated with this new state. Compare this with throwing a dice a number
        // of times: what is the chance that you don't throw a 6 in say 10 attempts?
        // That chance is (5/6) ^ 10.
        int n1Traffic = n1.getTraffic();
        int n2Traffic = n2.getTraffic();
        int n1XTraffic = n1.getxTraffic();
        int n2XTraffic = n2.getxTraffic();

        if (! n1.isAccepting() && n2.isAccepting()) {
            newOutDegree1++;
        }
        if (! n2.isAccepting() && n1.isAccepting()) {
            newOutDegree2++;
        }

        for (int i = 0; i < nsym; i++) {
            State child1 = n1.children[i];
            State child2 = n2.children[i];
            if (child2 != null && child2.isProductive()) {
        	if (child1 == null || ! child1.isProductive()) {
        	    newOutDegree1 += 2;
        	}    
            }
            if (child1 != null && child1.isProductive()) {
        	if (child2 == null || ! child2.isProductive()) {
        	    newOutDegree2 += 2;
        	}    
            }
        }
 
        if (newOutDegree1 == outDegree1 && newOutDegree2 == outDegree2) {
            similarStates++;
        } else if (newOutDegree1 != outDegree1) {
            staminaPenalty++;
        }

        double c = 1.0;

        if (newOutDegree1 != outDegree1) {
            double c1;
            if (outDegree1 != 0) {
        	c1 = Math.pow(outDegree1/(double)newOutDegree1, n1Traffic);
            } else {
                // This makes a state that previously only was part of the
                // rejecting automaton part of the accepting DFA as well.
        	c1 = Math.pow(.5, n1XTraffic);
            }
            c *= c1;
        }
        if (newOutDegree2 != outDegree2) {
            // This is a bit less serious, as this does not affect the
            // final automaton as much. However, there still may be some
            // quite unlikely issues.
            double c1;
            if (outDegree2 != 0) {
                c1 = Math.pow(outDegree2/(double)newOutDegree2, n2Traffic);
            } else {
        	c1 = Math.pow(0.5, n2XTraffic);
            }
            c *= c1;
        }

        if (NEGATIVES) {
            // We have to do something about negative sentences as well. Otherwise,
            // the learning process will favor generalization of the "negative" part,
            // which may, in turn, hamper the generalization of the "positive" part.
            //
            // Negative sentences are generated quite differently however: they are generated
            // by editing accepted sentences. So I am not at all convinced that the
            // code below is any good. On the other hand, most of the symbols in a rejected
            // sentence are generated by the same rules.

            int newXOutDegree1 = xOutDegree1;
            int newXOutDegree2 = xOutDegree2;

            double cx = 1.0;

            if (! n1.isRejecting() && n2.isRejecting()) {
                // This merge makes n1 rejecting. Compute the chance that it
                // actually could be accepting.
                if (outDegree1 != 0) {
                    newOutDegree1 = outDegree1 + 1;
                    double c1 = 1 - Math.pow(outDegree1 / (double) newOutDegree1, n1Traffic);
                    cx *= c1;
                } else {
                    newXOutDegree1++;
                }
            }
            if (! n2.isRejecting() && n1.isRejecting()) {
                if (outDegree2 != 0) {
                    newOutDegree2 = outDegree2 + 1;
                    double c1 = 1 - Math.pow(outDegree2 / (double) newOutDegree2, n1Traffic);
                    cx *= c1;
                } else {
                    newXOutDegree2++;
                }
            }
            
            for (int i = 0; i < nsym; i++) {
                State child1 = n1.children[i];
                State child2 = n2.children[i];
                if (child2 != null && child2.isXProductive()) {
                    if (child1 == null || ! child1.isXProductive()) {
                	newXOutDegree1 += 2;
                    }    
                }
                if (child1 != null && child1.isXProductive()) {
                    if (child2 == null || ! child2.isXProductive()) {
                	newXOutDegree2 += 2;
                    }    
                }
            }

            if (newXOutDegree1 == xOutDegree1 && newXOutDegree2 == xOutDegree2) {
                similarStates++;
            } else if (newXOutDegree1 != xOutDegree1) {
        	staminaPenalty++;
            }
            
            // How to compensate for the fact that there have been edit operations in the
            // sentences? These operations may cause edges that don't "obey" statistics.
            if (xOutDegree1 != 0 && newXOutDegree1 != xOutDegree1) {
                double c1 = Math.pow(xOutDegree1/(double)newXOutDegree1, n1XTraffic);
                cx *= c1;
            }
            if (xOutDegree2 != 0 && newXOutDegree2 != xOutDegree2) {
        	double c1 = Math.pow(xOutDegree2/(double)newXOutDegree2, n1XTraffic);
                cx *= c1;
            }

            if (cx < c) {
                c = cx;
            }
        } else if (n1Traffic != 0 && n2.isRejecting() && ! n1.isRejecting()) {
            // If n1 is rejecting, we reduce the chance with the chance that n1
            // could actually be accepting.
            newOutDegree1 = outDegree1 + 1;
            double thisChance = Math.pow(outDegree1 / (double) newOutDegree1, n1Traffic);
            // Now, thisChance is the chance that state n1 should actually be accepting.
            c *= (1 - thisChance);
        } else if (n2Traffic != 0 && n1.isRejecting() && ! n2.isRejecting()) {
            newOutDegree2 = outDegree2 + 1;
            double thisChance = Math.pow(outDegree2 / (double) newOutDegree2, n2Traffic);
            c *= (1 - thisChance);
        }
        /*
        System.out.println(n1.verboseString());
        System.out.println(n2.verboseString());
        System.out.println("Merge gives chance " + c);
        */
        return c;
    }
    
    private double computeInverse(double c) {
	boolean p = (c > .5);
	if (c > .5) {
	    c = 1 - c;
	}
	/*
	if (c < 1e-50) {
	    c = 1e-50;
	}
	*/
	double retval = -1000;
        try {
	    retval = normal.inverseCumulativeProbability(c);
	    if (retval < -1000) {
		retval = -1000;
	    }
	} catch (MathException e) {
	    // ignore
	}
	return p ? -retval : retval;
    }


    /**
     * Merges two states. This version specifically performs merges for which
     * one of the states is the top of a tree, so does not take into account
     * states that point upwards. For some search techniques, this is good
     * enough, for others, it is not. It computes the number of corresponding
     * labels in the merge, where a corresponding label means: either both
     * states are accepting or both states are rejecting.
     *
     * @param n1
     *            The first state
     * @param n2
     *            The second state (top of a tree)
     * @param undo
     *            Will collect information for undoing this merge.
     */
    private void walkTreeMerge(State n1, State n2, UndoInfo undo) {

        if (logger.isDebugEnabled()) {
            logger.debug("Merging " + n1 + " and " + n2);
        }

        if ((n1.accepting | n2.accepting) == MASK) {
            n2.addConflict(n1);
            conflict = true;
            /*
            if (undo != null) {
                System.out.println("CONFLICT!");
                System.out.println(n1.verboseString());
                System.out.println(n2.verboseString());
            }
            */
            return;
        }
        
        if ((n1.productiveForbidden && n2.isProductive()) || (n2.productiveForbidden && n1.isProductive())) {
            conflict = true;
            return;
        }

        if (USE_ADJACENCY) {
            if (givesAdjacencyConflict(n1, n2)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Merge of state " + n1 + ", " + n2
                            + " rejected because of adjacency");
                }
                conflict = true;
                return;
            }
        }

        if (USE_STAMINA) {
            double c;

            c = computeStaminaChance(n1, n2);
            
            // int n = n1.getTraffic() + n2.getTraffic() + n1.getxTraffic() + n2.getxTraffic();
            // double c1 = computeInverse(c);
            // zSum += n * c;
            // sumCount += n;
            /*
            if (undo == null) {
        	System.out.println("Merge of state " + n1 + ", " + n2 + ": c = " + c);
            }
            */

            if (c < chance) {
                chance = c;
            }
            
            if (undo == null && printInfo) {
                System.out.println("Merge of state " + n1.getId() + " and " + n2.getId() + " gives score " + c
                        + " and penalty " + staminaPenalty);
                System.out.println(n1.verboseString());
                System.out.println(n2.verboseString());
            }

            /*
            if (c < THRESHOLD) {
                return;
            }
            */
        }

        saveState(n1, undo);

        if (USE_CHISQUARE) {
            computeChiSquare(n1, n2);
            if (NEGATIVES) {
                computeXChiSquare(n1, n2);
            }
        }

        if ((n1.accepting & n2.accepting) != 0) {
            if (n1.getWeight() != 0 && n2.getWeight() != 0) {
        	labelScore++;
        	if (n1.isAccepting()) {
        	    nAccepting--;
        	} else {
        	    nRejecting--;
        	}
            }
        } else {
            n1.accepting |= n2.accepting;
            if (! REFINED_MDL) {
                if (n1.accepting != 0) {
                    if (counts != null && (n1.accepting & ACCEPTING) != 0) {
                        counts[0][n1.getId()] = 1;
                    }
                    if (xCounts != null && (n1.accepting & REJECTING) != 0 && n1.getWeight() + n2.getWeight() != 0) {
                        xCounts[0][n1.getId()] = 1;
                    }
                }
            }
        }

        n1.setWeight(n1.getWeight() + n2.getWeight());
        n1.setTraffic(n1.getTraffic() + n2.getTraffic());
        n1.setxTraffic(n1.getxTraffic() + n2.getxTraffic());
        if (NEEDS_EDGECOUNTS) {
            for (int j = 0; j < nsym; j++) {
                n1.edgeWeights[j] += n2.edgeWeights[j];
                n1.xEdgeWeights[j] += n2.xEdgeWeights[j];
            }
        }

        if ((n2.productive & ACCEPTING) != 0) {
            missingEdges -= n2.missingEdges(ACCEPTING);
            if ((n1.productive & ACCEPTING) == 0) {
                n1.productive |= ACCEPTING;
                mustRecomputeProductive = true;
                missingEdges += n1.missingEdges(ACCEPTING);
            } else {
                nProductiveStates--;
            }
        }

        if ((n2.productive & REJECTING) != 0) {
            missingXEdges -= n2.missingEdges(REJECTING);
            if ((n1.productive & REJECTING) == 0) {
                n1.productive |= REJECTING;
                missingXEdges += n1.missingEdges(REJECTING);
                mustRecomputeProductive = true;
            } else {
                nXProductiveStates--;
            }
        }

        // A merge may make the depth smaller.
        if (n1.getDepth() > n2.getDepth()) {
            n1.setDepth(n2.getDepth());
        }

        nStates--;

        // System.out.println("n1 = " + n1.id + ", n2 = " + n2.id);
        for (int i = 0; i < nsym; i++) {
            State v2 = n2.children[i];
            if (v2 != null) {
                State v1 = n1.children[i];
                if (v1 != null) {
                    if (v2.hasConflict(v1)) {
                	conflict = true;
                	return;
                    }
                    // System.out.println("    v1 = " + v1.id + ", v2 = " + v2.id);
                    nEdges--;
                    if ((v1.productive & ACCEPTING) != 0 &&
                        (v2.productive & ACCEPTING) != 0) {
                        nProductiveEdges--;
                    }
                    if ((v1.productive & REJECTING) != 0 &&
                        (v2.productive & REJECTING) != 0) {
                        nXProductiveEdges--;
                    }
                    if ((n1.productive & ACCEPTING) != 0 &&
                        (v1.productive & ACCEPTING) == 0 &&
                        (v2.productive & ACCEPTING) != 0) {
                        missingEdges--;
                    }
                    if ((n1.productive & REJECTING) != 0 &&
                        (v1.productive & REJECTING) == 0 &&
                        (v2.productive & REJECTING) != 0) {
                        missingXEdges--;
                    }
                    walkTreeMerge(v1, v2, undo);
                    if (conflict) {
                	v2.addConflict(v1);
                        return;
                    }
                } else {
                    // Parent of v2 changes, but is recomputed before every
                    // merge.
                    // System.out.println("    v2 = " + v2.id);
                    addEdge(undo, n1, i, v2);
                    if (USE_PARENT_SETS) {
                        v2.parents.remove(n2);
                        if (undo != null) {
                            undo.addParentRemoval(v2, n2);
                        }
                    }
                }
            }
        }
    }

    /**
     * Computes a chi-square sum element.
     * @return the chi-square sum element.
     */
    private static double symScore(int total1, int observed1,
            int total2, int observed2) {
        double retval;
        int totObserved = observed1 + observed2;
        int total = total1 + total2;

        double expected1 = ((double)total1) * totObserved / total;
        double expected2 = ((double)total2) * totObserved / total;
        if (false) {
            double diff1 = observed1 - expected1;
            double diff2 = observed2 - expected2;
            retval = (diff1 * diff1) / expected1 + (diff2 * diff2)/expected2;
        } else {
            // Sicco sais this one is better.
            retval = 2 * ( observed1 * Math.log(observed1/expected1)
                    + observed2 * Math.log(observed2/expected2));
        }
        if (logger.isDebugEnabled()) {
            logger.debug("expected1 = " + expected1
                    + ", observed1 = " + observed1
                    + ", expected2 = " + expected2
                    + ", observed2 = " + observed2
                    + ", sum element = " + retval);
        }
        return retval;
    }

    private void computeChiSquare(State n1, State n2) {
        double score = 0.0;
        int cnt = -1;

        int total1 = 0;
        int total2 = 0;
        int pool1 = 0;
        int pool2 = 0;

        if ((n1.accepting & ACCEPTING) != 0 ||
                (n2.accepting & ACCEPTING) != 0) {
            if (n1.getWeight() < CHI_MIN || n2.getWeight() < CHI_MIN) {
                pool1 += n1.getWeight();
                pool2 += n2.getWeight();
            }
            total1 += n1.getWeight();
            total2 += n2.getWeight();
        }
        for (int i = 0; i < nsym; i++) {
            if (n1.edgeWeights[i] < CHI_MIN || n2.edgeWeights[i] < CHI_MIN) {
                pool1 += n1.edgeWeights[i];
                pool2 += n2.edgeWeights[i];
            }
            total1 += n1.edgeWeights[i];
            total2 += n2.edgeWeights[i];
        }

        if (pool1 < CHI_MIN || pool2 < CHI_MIN) {
            total1 -= pool1;
            total2 -= pool2;
        }

        if (total1 < CHI_MIN || total2 < CHI_MIN) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Computing ChiSquare for merge of state " + n1 + " and " + n2);
        }

        if ((n1.accepting & ACCEPTING) != 0 ||
                (n2.accepting & ACCEPTING) != 0) {
            if (n1.getWeight() >= CHI_MIN && n2.getWeight() >= CHI_MIN) {
                if (logger.isDebugEnabled()) {
                    logger.debug("accepting states ...");
                }
                score += symScore(total1, n1.getWeight(), total2, n2.getWeight());
                cnt++;
            }
        }
        for (int i = 0; i < nsym; i++) {
            if (n1.edgeWeights[i] >= CHI_MIN && n2.edgeWeights[i] >= CHI_MIN) {
                if (logger.isDebugEnabled()) {
                    logger.debug("contribution for symbol " + i);
                }
                score += symScore(total1, n1.edgeWeights[i], total2, n2.edgeWeights[i]);
                cnt++;
            }
        }
        if (pool1 >= CHI_MIN && pool2 >= CHI_MIN) {
            if (logger.isDebugEnabled()) {
                logger.debug("contribution for pool");
            }
            score += symScore(total1, pool1, total2, pool2);
            cnt++;
        }
        if (cnt >= 1) {
            double p_value = 0.0;
            sumCount++;
            try {
                p_value = 1.0 - Gamma.regularizedGammaP(cnt/2.0, score/2.0);
            } catch (MathException e) {
                // Does not converge???
                p_value = 0.01;
            }
            chiSquareSum += Math.log(p_value);
            try {
                zSum += normal.inverseCumulativeProbability(p_value);
            } catch(Throwable e) {
                logger.debug("Oops? p_value = " + p_value, e);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("(" + n1.getId() + "," + n2.getId() + ") --> score = " + p_value);
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("No ChiSquare contribution from this merge");
            }
        }
    }

    private void computeXChiSquare(State n1, State n2) {
        double score = 0.0;
        int cnt = -1;

        int total1 = 0;
        int total2 = 0;
        int pool1 = 0;
        int pool2 = 0;

        if (logger.isDebugEnabled()) {
            logger.debug("Computing XChiSquare for merge of state " + n1 + " and " + n2);
        }

        if ((n1.accepting & REJECTING) != 0 ||
                (n2.accepting & REJECTING) != 0) {
            if (n1.getWeight() < CHI_MIN || n2.getWeight() < CHI_MIN) {
                pool1 += n1.getWeight();
                pool2 += n2.getWeight();
            }
            total1 += n1.getWeight();
            total2 += n2.getWeight();
        }
        for (int i = 0; i < nsym; i++) {
            if (n1.xEdgeWeights[i] < CHI_MIN || n2.xEdgeWeights[i] < CHI_MIN) {
                pool1 += n1.xEdgeWeights[i];
                pool2 += n2.xEdgeWeights[i];
            }
            total1 += n1.xEdgeWeights[i];
            total2 += n2.xEdgeWeights[i];
        }

        if (pool1 < CHI_MIN || pool2 < CHI_MIN) {
            total1 -= pool1;
            total2 -= pool2;
        }

        if (total1 < CHI_MIN || total2 < CHI_MIN) {
            return;
        }

        if ((n1.accepting & REJECTING) != 0 ||
                (n2.accepting & REJECTING) != 0) {
            if (n1.getWeight() >= CHI_MIN && n2.getWeight() >= CHI_MIN) {
                if (logger.isDebugEnabled()) {
                    logger.debug("rejecting states ...");
                }
                score += symScore(total1, n1.getWeight(), total2, n2.getWeight());
                cnt++;
            }
        }
        for (int i = 0; i < nsym; i++) {
            if (n1.xEdgeWeights[i] >= CHI_MIN && n2.xEdgeWeights[i] >= CHI_MIN) {
                if (logger.isDebugEnabled()) {
                    logger.debug("contribution for symbol " + i);
                }
                score += symScore(total1, n1.xEdgeWeights[i], total2, n2.xEdgeWeights[i]);
                cnt++;
            }
        }
        if (pool1 >= CHI_MIN && pool2 >= CHI_MIN) {
            if (logger.isDebugEnabled()) {
                logger.debug("contribution for pool");
            }
            score += symScore(total1, pool1, total2, pool2);
            cnt++;
        }
        if (cnt >= 1) {
            double p_value = 0.0;
            xSumCount++;
            try {
                p_value = 1.0 - Gamma.regularizedGammaP(cnt/2.0, score/2.0);
            } catch (MathException e) {
                // Does not converge???
                p_value = 0.01;
            }
            xChiSquareSum += Math.log(p_value);
            try {
                xZSum += normal.inverseCumulativeProbability(p_value);
            } catch(MathException e) {
                logger.debug("Oops: MathException? ", e);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("(" + n1.getId() + "," + n2.getId() + ") --> xScore = " + p_value);
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("No ChiSquare contribution from this merge");
            }
        }
    }

    private void addEdge(UndoInfo undo, State parent, int i, State dest) {
        if (undo != null) {
            undo.addChild(parent, i, parent.children[i]);
            if (USE_PARENT_SETS) {
                if (!dest.parents.contains(parent)) {
                    undo.addParentAddition(dest, parent);
                }
            }
        }
        State olddest = parent.children[i];
        if ((parent.productive & ACCEPTING & dest.productive) != 0) {
            if (olddest == null || (olddest.productive & ACCEPTING) == 0) {
                missingEdges--;
            }
        }
        if ((parent.productive & REJECTING & dest.productive) != 0) {
            if (olddest == null || (olddest.productive & REJECTING) == 0) {
                missingXEdges--;
            }
        }
        parent.addEdge(dest, i);
    }

    /**
     * Restores the DFA to the state it was in earlier, as specified by the
     * parameter.
     *
     * @param u
     *            specifies saved state.
     */
    public void undoMerge(UndoInfo u) {
        conflict = false;
        if (u != null) {
            u.undo();
        }

        if (logger.isDebugEnabled()) {
            if (!checkDFA()) {
                logger.error("From undoMerge: exit");
                System.exit(1);
            }
        }
    }

    /**
     * Computes the number of bits needed to encode the DFA. Assumed is the presence
     * of a special accepting state A, and a special rejecting state R, and
     * a special end-marker symbol.
     * There are three ways of encoding:
     * 1. a two-dimensional array of (nStates * nsym) size, where each entry
     * contains a destination state. Each entry needs enough bits to encode a state
     * number. This assumes a fixed start state. Note that any permutation of
     * the states will do, so there is a lot of redundancy in this
     * representation. In fact, (nStates-1)! redundancy, so we deduct
     * log2((nStates-1)!) (the start state is fixed).
     * 2. for each state: number of outgoing edges,
     * for each edge the symbol + the destination
     * state. With redundancy compensation. N*(1+2log(S+1)) +
     * E*(2log(S)+2log(N)) - 2log((N-1)!) This encoding is much much better for
     * sparse DFAs (like Prefix Tree Acceptors :-).
     * 3. a table of nsym * nstates consisting of single bits: either there is an
     *    edge or there is not. Then, for each edge the destination state.
     *
     * In all representations, end states are encoded as a transition on a special
     * symbol to a special state.
     *
     * @return the actual sum for the DFA.
     */
    public double getDFAComplexity() {
        if (DFAScore == 0) {
            double score1 = 0.0;
            double score2 = 0.0;
            double score3 = 0.0;
            if (USE_PRODUCTIVE) {
                double redundancy;
                if ((NEGATIVES || MDL_COMPLEMENT) && nXProductiveStates > 0) {
                    int nXs = nXProductiveStates + 1;   // number of states + special rejecting state.
                    redundancy = sumLog(nXs - 1) / LOG2;
                    score1 += nXs * (nsym+1) * log2(nXs) - redundancy;
                    score2 += nXs * log2(nsym + 2)
                                + (nXProductiveEdges + nRejecting) * (log2(nsym+1) + log2(nXs))
                                - redundancy;
                    score3 += nXs * (nsym+1)
                                + (nXProductiveEdges + nRejecting) * log2(nXs) - redundancy;
                    // From a paper by Domaratzky, Kisman, Shallit
                    // DFAScore = nXs * (1.5 + log2(nXs)); (if nsym = 2).
                }
                int ns = nProductiveStates + 1; // number of states + special accepting state.
                redundancy = sumLog(ns - 1) / LOG2;
                score1 += ns * (nsym+1) * log2(ns) - redundancy;
                score2 += ns * log2(nsym + 2)
                            + (nProductiveEdges + nAccepting) * (log2(nsym+1) + log2(ns))
                            - redundancy;
                score3 += ns * (nsym+1)
                            + (nProductiveEdges + nAccepting) * log2(ns) - redundancy;
                // DFAScore += ns * (1.5 + log2(ns));
            } else {
                int ns = nStates + (nRejecting > 0 ? 2 : 1);   // number of states + special accepting/rejecting states.
                double redundancy = sumLog(ns - 1) / LOG2;
                // nsym+1 because of the transitions to special accepting/rejecting states.
                // These transitions are transitions on a special, new, symbol.
                score1 += ns * (nsym+1) * log2(ns) - redundancy;
                // nsym+2 because the number of outgoing edges ranges from 0 to nsym+1.
                score2 += ns * log2(nsym + 2)
                            + (nEdges + nAccepting + nRejecting) * (log2(nsym+1) + log2(ns))
                            - redundancy;
                score3 += ns * (nsym+1)
                            + (nEdges + nAccepting + nRejecting) * log2(ns) - redundancy;
                // DFAScore = ns * (1.5 + log2(ns));
            }
            if (DFA_SCORING == 0) {
                DFAScore = score1;
            } else if (DFA_SCORING == 1) {
                DFAScore = score2;
            } else if (DFA_SCORING == 2) {
                DFAScore = score3;
            } else if (DFA_SCORING == 3) {
                if (score1 > score2) {
                    DFAScore = score2;
                } else {
                    DFAScore = score1;
                }
                if (score3 < DFAScore) {
                    DFAScore = score3;
                }
            } else if (DFA_SCORING == 4) {
                if (score1 < score2) {
                    DFAScore = score2;
                } else {
                    DFAScore = score1;
                }
                if (score3 > DFAScore) {
                    DFAScore = score3;
                }
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("DFAScore = " + DFAScore + ", nStates = " + nStates
                    + ", nRejecting = " + nRejecting + ", nAccepting = " + nAccepting);
        }
        return DFAScore;
    }

    /**
     * Computes the sum of logs. Approximates if c > 2^16
     * @param c logs up until this number are needed.
     */
    private static final double sumLog(double c) {
        double retval;

        if (c >= sumLogs.length) {
            // Uses the approximation ln(n!) ~ n.ln(n) - n.
            retval = c * Math.log(c) - c;
        } else {
            retval = sumLogs[(int)c];
        }
        if (logger.isDebugEnabled()) {
            logger.debug("sumlog(" + c + ") = " + retval);
        }
        return retval;
    }

    private static final double approximate2LogNoverK(double n, int k) {
        if (k > n) {
            return 0.0;
        }
        return (sumLog(n) - sumLog(k) - sumLog(n-k))/LOG2;
    }

    public double computeTotalRecognized(int maxlength, int acceptOrReject) {
        double[][] counts = new double[maxlength+1][getIdMap().length];
        return computeNStrings(maxlength, counts, acceptOrReject, false);
    }

    /**
     * Calculates the Minimum Description Length complexity, relative to
     * the complete learning set.
     * @return The complexity
     */
    public double getMDLComplexity() {

        double DFAScore = getDFAComplexity();

        if (MDLScore == 0) {
            if (counts == null) {
                counts = new double[maxlen+1][];
                for (int i = 0; i < counts.length; i++) {
                    counts[i] = new double[getIdMap().length];
                }
            }
            if (NEGATIVES) {
                if (xCounts == null) {
                    xCounts = new double[maxlen+1][];
                    for (int i = 0; i < xCounts.length; i++) {
                        xCounts[i] = new double[getIdMap().length];
                    }
                }
            }
            if (REFINED_MDL) {
                double score = 0;
                State[] myStates = reachCount();
                int totalCount = 0;
                for (State s : myStates) {
                    double cnt = 0;
                    int id = s.getId();
                    for (int j = 0; j <= maxlen; j++) {
                        cnt += counts[j][id];
                    }
                    int weight = s.getWeight();
                    if (!(MDL_COMPLEMENT || NEGATIVES) && s.isRejecting()) {
                        weight = 0;
                    }
                    double sc = approximate2LogNoverK(cnt, weight);
                    if (logger.isDebugEnabled()) {
                        totalCount += cnt;
                        logger.debug("State " + id + ", weight = "
                                + s.getWeight() + ", cnt = " + cnt
                                + ", sc  = " + sc);
                    }
                    score += sc;
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("totalCount = " + totalCount);
                }
                MDLScore = score;
            } else {
                double n = computeNStrings(maxlen, counts, ACCEPTING,
                        INCREMENTAL_COUNTS);
                MDLScore = approximate2LogNoverK(n, numRecognized);

                if (NEGATIVES && numRejected > 0) {
                    n = computeNStrings(maxlen, xCounts, REJECTING,
                            INCREMENTAL_COUNTS);
                    MDLScore += approximate2LogNoverK(n, numRejected);
                }

                if (MDL_COMPLEMENT && numRejected > 0) {
                    double cn = Math.pow(2, maxlen + 1) - n - 1;
                    double score = approximate2LogNoverK(cn, numRejected);
                    MDLScore += score;
                }
            }
            counts_done = true;
        }

        double score = DFAScore + MDLScore;
        if (logger.isDebugEnabled()) {
            logger.debug("getMDLComplexity: MDLscore = "
                    + MDLScore + ", DFAscore = " + DFAScore
                    + ", total = " + score);
        }
        return score;
    }

    private static double log2(int d) {
        if (d >= logs.length) {
            return Math.log(d)/LOG2;
        }
        return logs[d];
    }

    /**
     * Consistency check for the DFA.
     */
    public boolean checkDFA() {
        boolean ok = true;

        // Check that it recognizes the samples!
        if (samples != null) {
            for (int i = 0; i < samples.size(); i++) {
                int[] sample = samples.get(i);
                if (sample[0] == 1 && !recognize(sample)) {
                    logger.debug("Did not recognize sample " + i);
                    ok = false;
                }
                if (sample[0] == 0 && recognize(sample)) {
                    logger.debug("Did recognize sample " + i);
                    ok = false;
                }
            }
        }

        State[] l = startState.breadthFirst();

        int nprod = startState.computeProductiveStates(ACCEPTING);
        if (nProductiveStates != nprod) {
            logger.debug("nProductiveStates = " + nProductiveStates + ", size = "
                    + nprod + ", DFA = \n" + dumpDFA());
            ok = false;
        }


        int nedges = computeProductiveEdges(l, ACCEPTING);
        if (nProductiveEdges != nedges) {
            logger.debug("nProductiveEdges = " + nProductiveEdges + ", size = "
                    + nedges + ", DFA = \n" + dumpDFA());
            ok = false;
        }

        if (NEEDS_EDGECOUNTS) {
            for (State s : l) {
                int cnt = s.isAccepting() ? s.getWeight() : 0;
                int xcnt = s.isRejecting() ? s.getWeight() : 0;
                for (int i = 0; i < nsym; i++) {
                    cnt += s.edgeWeights[i];
                    xcnt += s.xEdgeWeights[i];
                }
                if (cnt != s.getTraffic()) {
                    logger.debug("traffic mismatch: count = " + s.getTraffic()
                            + ", cnt = " + cnt);
                    ok = false;
                }
                if (xcnt != s.getxTraffic()) {
                    logger.debug("xtraffic mismatch: count = " + s.getxTraffic()
                            + ", xcnt = " + xcnt);
                    ok = false;
                }
            }
        }

        // Check parents
        if (USE_PARENT_SETS) {
            for (int i = 0; i < l.length; i++) {
                State s = l[i];
                boolean present;

                for (State s2 : s.parents) {
                    present = false;
                    for (int j = 0; j < nsym; j++) {
                        if (s2.children[j] == s) {
                            present = true;
                            break;
                        }
                    }
                    if (!present) {
                        logger.debug("State " + s2
                                + " is present in the parent "
                                + "set of state " + s + ", but should not be.");

                        ok = false;
                    }
                }
                for (int j = 0; j < nsym; j++) {
                    State s2 = s.children[j];
                    if (s2 != null && !s2.parents.contains(s)) {
                        logger.debug("State " + s
                                + " is not present in the parent "
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
        } catch (IOException e) {
            throw new Error("Should not happen? " + e);
        }
        return w.toString();
    }

    public String dumpDFA() {
        StringWriter w = new StringWriter();
        try {
            write(w, true);
        } catch (IOException e) {
            throw new Error("Should not happen? " + e);
        }
        return w.toString();
    }

    /**
     * Writes a string representation of this object to the specified writer.
     *
     * @param w
     *            the writer.
     * @param allStates
     *            when set, print all states, not just productive ones.
     */
    public void write(Writer w, boolean allStates) throws IOException {

        startState.computeProductiveStates(ACCEPTING);
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
            w.write("S" + startState.getId() + "\n");
        } else {
            w.write("S0\n");
        }

        // add each state
        for (int i = 0; i < l.length; i++) {
            State s = l[i];
            if (allStates || (s.productive & ACCEPTING) != 0) {
                // Print the node definition
                int index = s.getId();
                if (!allStates) {
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
                        w.write("E"
                                + index
                                + ":"
                                + symbols.getSymbol(j)
                                + ":"
                                + (allStates ? e.getId() : h.get(e).intValue()) + "\n");
                    }
                }
            }
        }
    }

    /**
     * Writes a string representation of this DFA to the specified file.
     *
     * @param filename
     *            the specified file.
     */
    public void write(String filename) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
            write(bw, false);
            bw.close();
        } catch (Exception E) {
            throw new Error(E.toString(), E);
        }
    }

    private void computeCounts(State[] states, double[][] counts) {
        for (int j = 1; j <= maxlen; j++) {
            double[] countjm1 = counts[j - 1];
            for (int i = 0; i < states.length; i++) {
                State s = states[i];
                double cnt = 0;
                for (int k = 0; k < nsym; k++) {
                    State sk = s.children[k];
                    if (sk != null) {
                        cnt += countjm1[sk.getId()];
                    }
                }
                counts[j][s.getId()] = cnt;
            }
        }
    }

    /**
     * Computes the number of strings with length less than or equal to
     * <code>l</code> that are recognized by the current DFA.
     *
     * @param l
     *            the maximum string length
     * @param count
     *            the count array.
     * @param acceptOrReject
     *            count either from the rejecting DFA or the accepting DFA.
     * @return the number of strings recognized.
     */
    private double computeNStrings(int l, double[][] count, int acceptOrReject,
            boolean incremental) {
        BitSet h = null;
        if (!incremental || !counts_done) {
            State[] myStates = startState.breadthFirst();

            if (USE_PARENT_SETS) {
                h = new BitSet(getIdMap().length);
            }

            // Initialize
            for (int i = 0; i < myStates.length; i++) {
                State s = myStates[i];
                for (int j = 0; j < count.length; j++) {
                    count[j][s.getId()] = 0;
                }
                if ((s.accepting & acceptOrReject) != 0 && s.getWeight() != 0) {
                    if (USE_PARENT_SETS) {
                        h.set(s.getId());
                    }
                    count[0][s.getId()] = 1;
                }
            }
        }
        if (USE_PARENT_SETS) {
            if (!incremental || !counts_done) {
                // Compute counts
                for (int k = 1; k <= l; k++) {
                    BitSet h2 = new BitSet(getIdMap().length);
                    for (int i = h.nextSetBit(0); i >= 0; i = h.nextSetBit(i + 1)) {
                        State s = getIdMap()[i];
                        for (State si : s.parents) {
                            for (int j = 0; j < nsym; j++) {
                                if (si.children[j] == s) {
                                    count[k][si.getId()] += count[k - 1][s.getId()];
                                }
                            }
                            h2.set(si.getId());
                        }
                    }
                    h = h2;
                }
            }
        } else if (incremental) {
            if (!counts_done) {
                computeCounts(getIdMap(), count);
            }
        } else {
            // This is faster, and does not need parent administration,
            // but we cannot do incremental stuff later on.
            startState.initCount();
            startState.doCount(l, count);
        }

        double n = 0;
        for (int i = 0; i <= l; i++) {
            n += count[i][startState.getId()];
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
        int c1;
        HashSet<State> h = new HashSet<State>();
        if (l1 == null) {
            l1 = new State[getIdMap().length];
            l2 = new State[getIdMap().length];
        }

        // Initialize. Only initialize count fields that we are going to use.
        l1[0] = startState;
        c1 = 1;
        counts[0][startState.getId()] = 1;
        if (startState.getWeight() > 0) {
            for (int i = 1; i <= maxlen; i++) {
                counts[i][startState.getId()] = 0;
            }
            h.add(startState);
        }

        // Compute counts
        for (int k = 1; k <= maxlen; k++) {
            int mark = getMark();
            int c2 = 0;
            double[] countk = counts[k];
            double[] countkm1 = counts[k - 1];
            for (int i = 0; i < c1; i++) {
                State s = l1[i];
                double km1 = countkm1[s.getId()];
                for (int j = 0; j < s.children.length; j++) {
                    State sj = s.children[j];
                    if (sj != null) {
                        if (sj.mark != mark) {
                            l2[c2++] = sj;
                            sj.mark = mark;
                            countk[sj.getId()] = 0;
                            if (sj.getWeight() > 0) {
                                if (!h.contains(sj)) {
                                    h.add(sj);
                                    for (int l = 0; l <= maxlen; l++) {
                                        counts[l][sj.getId()] = 0;
                                    }
                                }
                            }
                        }
                        countk[sj.getId()] += km1;
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

        // Careful! You cannot rely on state ids anymore after this.
        startState.setId(-1);
        setIdMap(startState.breadthFirst());
        nStates = getIdMap().length;

        BitSet[] partition = new BitSet[nStates];
        BitSet workList = new BitSet(nStates);
        BitSet partition1 = new BitSet(nStates);
        BitSet partition2 = new BitSet(nStates);
        BitSet partition3 = new BitSet(nStates);

        counts_done = false;

        for (int i = 0; i < getIdMap().length; i++) {
            State s = getIdMap()[i];
            if (!REFINED_MDL) {
                if (counts != null) {
                    counts[0][s.getId()] = 0;
                }
                if (xCounts != null) {
                    xCounts[0][s.getId()] = 0;
                }
            }
            if ((s.accepting & ACCEPTING) != 0) {
                if (!REFINED_MDL && counts != null) {
                    counts[0][s.getId()] = 1;
                }
                partition1.set(s.getId());
            } else if ((s.accepting & REJECTING) != 0) {
                if (!REFINED_MDL && xCounts != null) {
                    xCounts[0][s.getId()] = 1;
                }
                partition2.set(s.getId());
            } else {
                partition3.set(s.getId());
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
                for (int j = 0; j < getIdMap().length; j++) {
                    State st = getIdMap()[j].traverseLink(i);
                    if (st != null && S.get(st.getId())) {
                        Ia.set(getIdMap()[j].getId());
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
                            partition[j] = R1;
                            partition[npartitions++] = R;
                            // workList.set(j);
                            // workList.set(npartitions-1);
                            if (workList.get(j)) {
                                workList.set(npartitions - 1);
                            } else if (c <= R.cardinality()) {
                                workList.set(j);
                            } else {
                                workList.set(npartitions - 1);
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
            states[i] = getIdMap()[ind];
            ind = p.nextSetBit(ind + 1);
            while (ind >= 0) {
                states[i].setWeight(states[i].getWeight()
                        + getIdMap()[ind].getWeight());
                states[i].setTraffic(states[i].getTraffic()
                        + getIdMap()[ind].getTraffic());
                states[i].setxTraffic(states[i].getxTraffic()
                        + getIdMap()[ind].getxTraffic());
                if (NEEDS_EDGECOUNTS) {
                    for (int j = 0; j < nsym; j++) {
                        states[i].edgeWeights[j] += getIdMap()[ind].edgeWeights[j];
                        states[i].xEdgeWeights[j] += getIdMap()[ind].xEdgeWeights[j];
                    }
                }
                ind = p.nextSetBit(ind + 1);
            }
            if (p.get(startState.getId())) {
                startState = states[i];
            }
        }
        for (int i = 0; i < npartitions; i++) {
            if (states[i].parents != null) {
                states[i].parents.clear();
            }
        }
        for (int i = 0; i < npartitions; i++) {
            for (int j = 0; j < nsym; j++) {
                State st = states[i].traverseLink(j);
                if (st != null) {
                    for (int k = 0; k < npartitions; k++) {
                        if (partition[k].get(st.getId())) {
                            states[i].addEdge(states[k], j);
                            break;
                        }
                    }
                }
            }
        }

        dfaComputations(true);

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

        addConflict(conflicts, s1.getId(), s2.getId());

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
     *
     * @return the conflict sets.
     */
    public BitSet[] computeConflicts() {
        BitSet[] conflicts = new BitSet[getIdMap().length];
        for (int i = 0; i < getIdMap().length; i++) {
            State s1 = getIdMap()[i];
            if ((s1.accepting & REJECTING) != 0) {
                for (int j = 0; j < getIdMap().length; j++) {
                    State s2 = getIdMap()[j];
                    if ((s2.accepting & ACCEPTING) != 0) {
                        propagateConflict(conflicts, s1, s2);
                    }
                }
            }
        }
        return conflicts;
    }

    public Symbols getSymbols() {
        return symbols;
    }

    public ArrayList<int[]> getSamples() {
        return samples;
    }

    public void setSamples(ArrayList<int[]> samples) {
        this.samples = samples;
    }

    /**
     * Ties up two DFAs and returns the result. This means: if dfa1 has
     * an edge to an endstate on symbol s, and dfa2 has an outgoing edge
     * from its start state on that same symbol s, the two edges are replaced
     * by a single edge from the source of the first edge to the destination
     * of the second edge.
     *
     * TODO: what to do with states from dfa2 that become unreachable?
     *
     * @param dfa1
     * @param dfa2
     * @return the resulting DFA.
     */
    public static DFA tieUp(DFA dfa1, DFA dfa2) {

        logger.info("Tie up dfas");

        DFA result = new DFA(dfa1);

        // First merge symbol sets.
        int[] dfa2SymbolMap = result.symbols.addSymbols(dfa2.symbols);
        result.nsym = result.symbols.nSymbols();

        // Kill samples.
        result.samples = null;

        // Then, copy and states and possibly re-index the children arrays.
        Numberer numberer = new Numberer();
        HashMap<State, State> map = new HashMap<State, State>();
        State state1 = new State(dfa1.startState, null,
                map, null, numberer, dfa1.symbols.nSymbols(), result.nsym);
        State state2 = new State(dfa2.startState, null,
                map, dfa2SymbolMap, numberer, 0, result.nsym);

        result.entranceStates = new ArrayList<State>();
        for (State s : dfa1.entranceStates) {
            result.entranceStates.add(map.get(s));
        }
        for (State s : dfa2.entranceStates) {
            result.entranceStates.add(map.get(s));
        }

        result.nStates = dfa1.nStates + dfa2.nStates;
        result.setIdMap(new State[result.nStates]);
        state1.fillIdMap(result.getIdMap());
        state2.fillIdMap(result.getIdMap());
        result.startState = state1;
        result.maxlen = dfa1.maxlen + dfa2.maxlen - 1;

        State[] list1 = state1.breadthFirst();
        boolean added = false;
        for (int i = 0; i < result.nsym; i++) {
            if (state2.children[i] != null) {
                State state2Target = state2.children[i];
                boolean done = false;
                for (State s : list1) {
                    if (s.children[i] != null && s.children[i].isAccepting()) {
                        State endState = s.children[i];
                        s.children[i] = state2Target;
                        // Deal with outgoing edges from endState ...
                        for (int j = 0; j < endState.children.length; j++) {
                            if (endState.children[j] != null) {
                                if (state2Target.children[j] == null) {
                                    state2Target.children[j] = endState.children[j];
                                } else {
                                    // What to do here???
                                    // TODO: Refuse this tieUp?
                                }
                            }
                        }
                        done = true;
                        state2.children[i] = null;
                    }
                }
                if (! done && ! added) {
                    // Startstate of DFA2 has outgoing edges that don't match any
                    // edge to an endstate of DFA1.
                    // For now, we leave them alone. But, the startstate of DFA2
                    // may become unreachable.
                    // Solution: add it to entranceStates!
                    result.entranceStates.add(state2);
                    added = true;
                }
            }
        }

        result.dfaComputations(! added);

        double dfa1Fraction = dfa1.numRecognized
                /  dfa1.computeTotalRecognized(dfa1.maxlen, ACCEPTING);
        double dfa2Fraction = dfa2.numRecognized
                /  dfa2.computeTotalRecognized(dfa2.maxlen, ACCEPTING);
        double resultTotal = result.computeTotalRecognized(result.maxlen, ACCEPTING);
        // Estimate numRecognized. After all, we don't have samples anymore.
        result.numRecognized = (int) (resultTotal * (dfa1Fraction + dfa2Fraction) / 2);

        if (USE_ADJACENCY) {
            result.computeAdjacency();
        }

        if (logger.isDebugEnabled()) {
            if (!result.checkDFA()) {
                System.out.println(result.dumpDFA());
                logger.error("From dfa merging constructor: exit");
                System.exit(1);
            }
            logger.debug("TiedUp DFA: nstates = " + result.nStates + ", nProductiveStates = " + result.nProductiveStates);
        }
        return result;
    }

    public State[] getEntranceStates() {
        return entranceStates.toArray(new State[entranceStates.size()]);
    }

    private void setIdMap(State[] idMap) {
        this.idMap = idMap;
    }

    public State[] getIdMap() {
        return idMap;
    }
}
