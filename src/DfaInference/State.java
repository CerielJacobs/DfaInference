package DfaInference;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

/**
 * Representation of a state in a DFA.
 */
public final class State implements java.io.Serializable, Configuration,
        Comparable<State> {

    private static final long serialVersionUID = 1L;
    
    /** The children of this state. Index is the symbol number. */
    State[] children = null;

    /** 
     * The parent of this state. Note that as long as the shape is a tree,
     * there is only one parent.
     */
    State   parent = null;

    /**
     * Accepting status of this state. 
     * Uses two bits: ACCEPTING and REJECTING.
     * When the ACCEPTING bit is set, the state is an accepting state 
     * of the ACCEPTING DFA. When the REJECTING bit is set, the state
     * an accepting state of the DFA that accepts the counter examples.
     */
    byte accepting;

    /**
     * Productivity status of this state. 
     * Uses two bits: ACCEPTING and REJECTING.
     * When the ACCEPTING bit is set, the state is part of the ACCEPTING
     * DFA. When the REJECTING bit is set, the state is part of the
     * DFA that accepts the counter examples.
     */
    byte productive;

    /** Marks. */
    int mark;

    /** Depth of this state in the DFA. */
    private int depth = 0;

    /** Index up until which the count is valid. */
    int maxLenComputed = Integer.MAX_VALUE;

    /** Set of parents. */
    ArrayList<State> parents;

    /**
     * Weight of this state. The weight of an accepting state is defined
     * as the number of samples that finish in this state.
     */
    int weight = 0;

    /**
     * The number of times we pass this state when processing the positive samples.
     */
    private int traffic = 0;

    /**
     * The number of times we pass this state when processing the negative samples.
     */
    int xTraffic = 0;

    /** Number of positive samples that pass through this edge. */
    int[] edgeWeights;
    
    /** Number of negative samples that pass through this edge. */
    int[] xEdgeWeights;
    
    /**
     * Identifying number of this state.
     */
    private int id = -1;

    public State[] getChildren() {
        return children;
    }

    public void setChildren(State[] children) {
        this.children = children;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    /** States with which this state has a conflict. */
    BitSet conflicting = null;

    /** Index in array of saved states. */
    int savedIndex = 0;
    
    BitSet entrySyms;

    /**
     * Constructor that specifies the number of symbols.
     * @param nsym the number of symbols.
     */
    public State(int nsym) {
        children = new State[nsym];
        if (USE_CHISQUARE) {
            edgeWeights = new int[nsym];
            xEdgeWeights = new int[nsym];
        }
        if (USE_PARENT_SETS) {
            parents = new ArrayList<State>();
        }
        if (USE_ADJACENCY) {
            entrySyms = new BitSet(nsym+1);
        }
    }

    /**
     * Constructor that creates a deep copy of the specified state.
     * @param s the state to copy
     * @param parent the parent of the newly created state
     * @param h maps states to copies, so that cycles can be dealt with.
     * @param map if non-null, re-orders the symbol numbering.
     * @param nsym the new number of symbols.
     */
    State(State s, State parent, HashMap<State, State> h, int[] map,
            Numberer numberer, int old_nsym, int nsym) {
        productive = s.productive;
        accepting = s.accepting;
        setDepth(s.getDepth());
        if (numberer != null) {
            id = numberer.next();
        } else {
            id = s.id;
        }
        weight = s.weight;
        if (USE_PARENT_SETS) {
            parents = new ArrayList<State>();
        }
        if (USE_ADJACENCY) {
            BitSet set = s.entrySyms;
            entrySyms = new BitSet(nsym+1);
            if (map == null) {
                // Just remap implicit start symbol.
                entrySyms.or(set);
                if (set.get(old_nsym)) {
                    entrySyms.clear(old_nsym);
                    entrySyms.set(nsym);
                }
            } else {
                // remap complete set.
                for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i+1)) {
                    entrySyms.set(map[i]);
                }
            }
        }
        this.parent = parent;
        //        if (s.conflicting != null) {
        //            conflicting = (BitSet) s.conflicting.clone();
        //        }
        children = new State[nsym];
        if (USE_CHISQUARE) {
            edgeWeights = new int[nsym];
            xEdgeWeights = new int[nsym];
        }
        h.put(s, this);
        for (int i = 0; i < s.children.length; i++) {
            if (s.children[i] != null) {
                State cp = h.get(s.children[i]);
                if (cp == null) {
                    cp = new State(s.children[i], this, h, map, numberer, old_nsym, nsym);
                }
                if (map == null) {
                    children[i] = cp;
                    if (USE_CHISQUARE) {
                        edgeWeights[i] = s.edgeWeights[i];
                        xEdgeWeights[i] = s.xEdgeWeights[i];
                    }
                } else {
                    children[map[i]] = cp;
                    if (USE_CHISQUARE) {
                        edgeWeights[map[i]] = s.edgeWeights[i];
                        xEdgeWeights[map[i]] = s.xEdgeWeights[i];
                    }
                }
                if (USE_PARENT_SETS) {
                    if (! cp.parents.contains(this)) {
                        cp.parents.add(this);
                    }
                }
            }
        }
    }
 
    /**
     * Constructs a deep copy of the specified state. However, some states are to
     * be merged together, as specified by the <code>mergeSets</code> array, which
     * is indexed by state numbers, and specifies which states are equivalent to this
     * state. A table is maintained which maps original states to copies, to avoid
     * infinite loops.  
     * @param s the state to deep-copy.
     * @param mergeSets specifies which states are equivalent.
     * @param map maps original states to copies.
     * @param oldStates the original state table.
     */
    State(State s, BitSet[] mergeSets, State[] map, State[] oldStates,
            Numberer numberer) {
        productive = 0;
        accepting = 0;
        id = numberer.next();
        weight = 0;
        children = new State[s.children.length];
        if (USE_CHISQUARE) {
            edgeWeights = new int[children.length];
            xEdgeWeights = new int[children.length];
        }
        if (USE_PARENT_SETS) {
            parents = new ArrayList<State>();
        }
        if (USE_ADJACENCY) {
            entrySyms =  (BitSet) s.entrySyms.clone();
        }
        
        if (mergeSets[s.id] == null) {
            // No states equivalent to this one. Just merge in the edges
            // and data.
            map[s.id] = this;
            mergeIn(s, mergeSets, map, oldStates, numberer);
        } else {
            // There are equivalent states. Merge everything in from
            // all states in this equivalence class.
            for (int n = mergeSets[s.id].nextSetBit(0); n != -1; n = mergeSets[s.id].nextSetBit(n+1)) {
                map[n] = this;
            }
            for (int n = mergeSets[s.id].nextSetBit(0); n != -1; n = mergeSets[s.id].nextSetBit(n+1)) {
                map[n] = this;
                mergeIn(oldStates[n], mergeSets, map, oldStates, numberer);
                if (USE_ADJACENCY) {
                    entrySyms.or(oldStates[n].entrySyms);
                }
            }            
        }
    }
    
    private void mergeIn(State s, BitSet[] mergeSets, State[] map,
            State[] oldStates, Numberer numberer) {
        productive |= s.productive;
        accepting |= s.accepting;
        weight += s.weight;

        if (USE_ADJACENCY) {
            entrySyms.or(s.entrySyms);
        }
        for (int i = 0; i < children.length; i++) {
            if (USE_CHISQUARE) {
                edgeWeights[i] += s.edgeWeights[i];
                xEdgeWeights[i] += s.xEdgeWeights[i];
            }
            State child = s.children[i];
            if (children[i] == null && child != null) {
                State dest = map[child.id];
                if (dest == null) {
                    // This destination is not copied yet. Deep-copy it.
                    dest = new State(child, mergeSets, map, oldStates, numberer);
                }
                children[i] = dest;
                if (USE_PARENT_SETS) {
                    if (! dest.parents.contains(this)) {
                        dest.parents.add(this);
                    }
                }
            }
        }
    }
    
    public int compareTo(State s) {
        if (s.getDepth() == getDepth()) {
            return s.id - id;
        }
        return s.getDepth() - getDepth();
    }

    /**
     * Decides if the current state has a conflict with the specified state.
     * @param red the state to compare labels with.
     * @return <code>true</code> if there is a conflict, <code>false</code>
     * if not.
     */
    public boolean hasConflict(State red) {
        if ((red.accepting | accepting) == MASK) {
            return true;
        }
        if (conflicting == null) {
            return false;
        }
        return conflicting.get(red.id);
    }

    public int depth() {
        return getDepth();
    }

    /**
     * Returns the number of missing edges for this state.
     * @param flag either <code>ACCEPTING</code> or <code>REJECTING</code>.
     * @return the number of missing edges.
     */
    public int missingEdges(byte flag) {
        if ((productive & flag) == 0) {
            return 0;
        }
        int cnt = 0;
        for (int i = 0; i < children.length; i++) {
            if (children[i] == null || (children[i].productive & flag) == 0) {
                cnt++;
            }
        }
        return cnt;
    }

    /**
     * Returns the number of productive edges for this state.
     * @param flag either <code>ACCEPTING</code> or <code>REJECTING</code>.
     * @return the number of productive edges.
     */
    public int productiveEdges(byte flag) {
        if ((productive & flag) == 0) {
            return 0;
        }
        int cnt = 0;
        for (int i = 0; i < children.length; i++) {
            if (children[i] != null && (children[i].productive & flag) != 0) {
                cnt++;
            }
        }
        return cnt;
    }

    /**
     * Adds a conflict for the current state with the specified state.
     * Note that this does not have to be a direct conflict, but could
     * be the result of a merge attempts that fails because it results
     * in a merge of conflicting states.
     * @param red the state that this state has a conflict with.
     */
    public void addConflict(State red) {
        if (conflicting == null) {
            conflicting = new BitSet();
        }
        conflicting.set(red.id);
    }

    /**
     * Determines in which state the DFA gets when the specified symbol is
     * given in the current state.
     * @param symbol the specified symbol
     * @return the state in which the DFA gets on this symbol,
     * or <code>null</code>.
     */
    public State traverseLink(int symbol) {
        return children[symbol];
    }

    /**
     * Adds a new state, with an edge from the current state
     * with the specified label.
     * @param symbol the edge label.
     * @param accept wether the input is a positive sample.
     * @return the new state.
     */
    public State addDestination(boolean accept, int symbol) {
        State dest = new State(children.length);
        dest.parent = this;
        if (USE_PARENT_SETS) {
            if (! dest.parents.contains(this)) {
                dest.parents.add(this);
            }
        }
        dest.setDepth(getDepth() + 1);
        children[symbol] = dest;
        if (USE_ADJACENCY && accept) {
            dest.entrySyms.set(symbol);
        }
        return dest;
    }

    /**
     * Traverses the edge with the specified symbol and returns the result.
     * @param symbol the edge label.
     * @return the target state, or <code>null</code>.
     */
    public State traverseEdge(int symbol) {
        return children[symbol];
    }

    /**
     * Adds a new edge from this state to the specified state,
     * with the specified label.
     * @param dest the destination of the edge.
     * @param symbol the edge label.
     */
    public void addEdge(State dest, int symbol) {
        children[symbol] = dest;
        dest.parent = this;
        if (USE_ADJACENCY) {
            dest.entrySyms.set(symbol);
        }
        if (USE_PARENT_SETS) {
            if (! dest.parents.contains(this)) {
                dest.parents.add(this);
            }
        }
    }

    /**
     * Creates a deep copy of this state.
     * @return the copy.
     */
    public State copy() {
        return new State(this, null, new HashMap<State, State>(), null, null, children.length, children.length);
    }

    /**
     * Recursively initializes the depth field to a large value.
     */
    private void killDepths() {
        if (getDepth() < Integer.MAX_VALUE) {
            setDepth(Integer.MAX_VALUE);
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null) {
                    children[i].killDepths();
                }
            }
        }
    }

    /**
     * Recursively computes the depth of this state and its children.
     * @param depth the specified depth of this state.
     */
    private void computeDepths(int depth) {
        if (this.getDepth() > depth) {
            this.setDepth(depth);
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null) {
                    children[i].computeDepths(depth+1);
                }
            }
        }
    }

    /**
     * Computes the depth of each state under the current one, which is
     * supposed to be the start state.
     */
    public void computeDepths() {
        killDepths();
        computeDepths(0);
    }

    /**
     * Recursively initializes the mark field.
     */
    private void clearMarks() {
        this.mark = 0;
        for (int j = 0; j < children.length; j++) {
            State chj = children[j];
            if (chj != null && chj.getDepth() > getDepth()) {
                chj.clearMarks();
            }
        }
    }

    /**
     * Computes the breadth-first order of the states and
     * possibly also gives each state an identification (if this
     * has not been done earlier).
     * @return an array containing the states in breadth-first order.
     */
    public State[] breadthFirst() {
        ArrayList<State> a = new ArrayList<State>();
        Numberer numberer = null;
        int low = 0;
        int high = 1;
        if (id == -1) {
            numberer = new Numberer();
            id = numberer.next();
        }
        a.add(this);
        clearMarks();
        mark = 1;
        while (low < high) {
            for (int i = low; i < high; i++) {
                State s = a.get(i);
                for (int j = 0; j < s.children.length; j++) {
                    State schj = s.children[j];
                    if (schj != null && schj.mark == 0) {
                        schj.mark = 1;
                        a.add(schj);
                        if (numberer != null) {
                            schj.id = numberer.next();
                        }
                    }
                }
            }
            low = high;
            high = a.size();
        }
        return a.toArray(new State[a.size()]);
    }

    public int getNumEdges(byte mask) {
        State[] l = breadthFirst();
        int cnt = 0;
        for (int i = l.length-1; i >= 0; i--) {
            State s = l[i];
            for (int j = 0; j < s.children.length; j++) {
                State schj = s.children[j];
                if (schj != null && (schj.productive & mask) != 0) {
                    cnt++;
                }
            }
        }
        return cnt;
    }
    
    public void fillIdMap(State[] idMap) {
        if (idMap[id] == null) {
            idMap[id] = this;
            for (int i = 0; i < children.length; i++) {
                State child = children[i];
                if (child != null) {
                    child.fillIdMap(idMap);
                }
            }
        }
    }

    /**
     * Computes and returns the set of productive states.
     * @param mask either REJECTING or ACCEPTING.
     * @return the set of productive states.
     */
    public int computeProductiveStates(byte mask) {
        State[] l = breadthFirst();
        boolean change = true;
        int cnt = 0;

        for (int i = l.length-1; i >= 0; i--) {
            State s = l[i];
            s.productive &= ~mask;
        }

        while (change) {
            change = false;
            for (int i = l.length-1; i >= 0; i--) {
                State s = l[i];
                if ((s.productive & mask) == 0) {
                    if ((s.accepting & mask) != 0) {
                        change = true;
                        s.productive |= mask;
                        cnt++;
                    } else {
                        for (int j = 0; j < s.children.length; j++) {
                            State schj = s.children[j];
                            if (schj != null && (schj.productive & mask) != 0) {
                                change = true;
                                s.productive |= mask;
                                cnt++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return cnt;
    }

    /**
     * Initializes for another run of {@link #doCount(int, double[][])}.
     */
    public void initCount() {
        maxLenComputed = 0;
        for (int i = 0; i < children.length; i++) {
            State s = children[i];
            if (s != null && s.getDepth() > getDepth()) {
                s.initCount();
            }
        }
    }

    /**
     * Computes the number of strings that are recognized by this state,
     * from lengths 0 .. <code>maxlen</code>.
     * The result is stored in the counts array.
     * @param maxlen the maximum length of the string.
     * @param counts the counts array, first index is length, second index is
     *   state number.
     */
    public void doCount(int maxlen, double[][] counts) {
        if (maxlen > maxLenComputed) {
            for (int i = 0; i < children.length; i++) {
                State s = children[i];
                if (s != null) {
                    // Note: this may affect the value of maxLenComputed of the
                    // current state!
                    s.doCount(maxlen-1, counts);
                }
            }

            for (int j = maxLenComputed; j < maxlen; j++) {
                double cnt = 0;
                for (int i = 0; i < children.length; i++) {
                    State s = children[i];
                    if (s != null) {
                        cnt += counts[j][s.id];
                    }
                }
                counts[j+1][id] = cnt;
            }
            maxLenComputed = maxlen;
        }
    }

    /**
     * Computes the number of strings that are recognized by this state,
     * from lengths 0 .. <code>maxlen</code>.
     * Only numbers for the states in the specified set are recomputed.
     * @param maxlen the maximum length of the string.
     * @param counts the counts array, first index is length, second index is
     *   state number.
     * @param mrk mark of the states for which we must recompute.
     */
    public void computeUpdate(int maxlen, double[][] counts, double[][] temps, int mrk) {
        if (maxlen > maxLenComputed) {
            if (maxlen > 1) {
                for (int i = 0; i < children.length; i++) {
                    State s = children[i];
                    if (s != null && s.mark >= mrk) {
                        // Note: this may affect the value of maxLenComputed of the
                        // current state: we may recurse into the same state,
                        // but with a lower maxlen.
                        s.computeUpdate(maxlen-1, counts, temps, mrk);
                    }
                }
            }

            for (int j = maxLenComputed; j < maxlen; j++) {
                double cnt = 0;
                for (int i = 0; i < children.length; i++) {
                    State s = children[i];
                    if (s != null) {
                        if (s.mark >= mrk && j != 0) {
                            cnt += temps[s.mark - mrk][j];
                        } else {
                            cnt += counts[j][s.id];
                        }
                    }
                }
                temps[this.mark - mrk][j+1] = cnt;
            }
            maxLenComputed = maxlen;
        }
    }
    
    public boolean isRejecting(){
        return accepting==REJECTING;
    }

    public boolean isAccepting(){
        return accepting==ACCEPTING;
    }
    
    public boolean isProductive() {
        return (productive & ACCEPTING) != 0;
    }

    /**
     * Returns a string representation of this state.
     * @return a string representation.
     */
    public String toString() {
        return "State" + id;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getDepth() {
        return depth;
    }

    public void setTraffic(int traffic) {
        this.traffic = traffic;
    }

    public int getTraffic() {
        return traffic;
    }
}
