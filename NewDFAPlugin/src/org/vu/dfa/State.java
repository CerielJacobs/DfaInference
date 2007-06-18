package org.vu.dfa;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

/**
 * Representation of a state in a DFA.
 */
public final class State implements java.io.Serializable, Configuration,
        Comparable {

    private static final long serialVersionUID = 1L;

    /** The number of symbols used. */
    static int nsym = 0;

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
    int depth = 0;

    /** Index up until which the count is valid. */
    int maxLenComputed = Integer.MAX_VALUE;

    /** Set of parents. */
    ArrayList<State> parents;

    /** Parents, as an array. */
    State[] parentsArray;

    /**
     * Weight of this state. The weight of an accepting state is defined
     * as the number of samples that finish in this state.
     */
    int weight = 0;

    /**
     * Identifying number of this state.
     */
    int id = -1;

    /** States with which this state has a conflict. */
    BitSet conflicting = null;

    /** Index in array of saved states. */
    int savedIndex = 0;

    /**
     * Constructor that specifies the number of symbols.
     * @param nsym the number of symbols.
     */
    public State(int nsym) {
        if (State.nsym == 0) {
            State.nsym = nsym;
        }
        children = new State[nsym];
        if (USE_PARENT_SETS) {
            parents = new ArrayList<State>();
        }
    }

    /**
     * Constructor that creates a deep copy of the specified state.
     * In a general DFA, a state can have more than one parent. However,
     * we only use the parent field as long as the form is a tree.
     * @param s the state to copy
     * @param parent the parent of the newly created state
     * @param h maps states to copies, so that cycles can be dealt with.
     */
    private State(State s, State parent, HashMap<State, State> h) {
        productive = s.productive;
        accepting = s.accepting;
        depth = s.depth;
        id = s.id;
        weight = s.weight;
        if (USE_PARENT_SETS) {
            parents = new ArrayList<State>();
        }
        this.parent = parent;
        //        if (s.conflicting != null) {
        //            conflicting = (BitSet) s.conflicting.clone();
        //        }
        children = new State[nsym];
        h.put(s, this);
        for (int i = 0; i < children.length; i++) {
            if (s.children[i] != null) {
                State cp = (State) h.get(s.children[i]);
                if (cp == null) {
                    children[i] = new State(s.children[i], this, h);
                }
                children[i] = cp;
                if (USE_PARENT_SETS) {
                    if (! cp.parents.contains(this)) {
                        cp.parents.add(this);
                        cp.parentsArray = null;
                    }
                }
            }
        }
    }

    public int compareTo(Object o) {
        State s = (State) o;
        return s.depth - depth;
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
     * @return the new state.
     */
    public State addDestination(int symbol) {
        State dest = new State(nsym);
        dest.parent = this;
        if (USE_PARENT_SETS) {
            if (! dest.parents.contains(this)) {
                dest.parents.add(this);
                dest.parentsArray = null;
            }
        }
        dest.depth = depth + 1;
        children[symbol] = dest;
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
        if (USE_PARENT_SETS) {
            if (! dest.parents.contains(this)) {
                dest.parents.add(this);
                dest.parentsArray = null;
            }
        }
    }

    /**
     * Creates a deep copy of this state.
     * @return the copy.
     */
    public State copy() {
        return new State(this, null, new HashMap<State, State>());
    }

    /**
     * Recursively initializes the depth field to a large value.
     */
    private void killDepths() {
        if (depth < Integer.MAX_VALUE) {
            depth = Integer.MAX_VALUE;
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
        if (this.depth > depth) {
            this.depth = depth;
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
            if (chj != null && chj.depth > depth) {
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
        int idCounter = 0;
        int low = 0;
        int high = 1;
        if (id == -1) {
            id = 0;
            idCounter = 1;
        }
        a.add(this);
        clearMarks();
        mark = 1;
        while (low < high) {
            for (int i = low; i < high; i++) {
                State s = (State) a.get(i);
                for (int j = 0; j < s.children.length; j++) {
                    State schj = s.children[j];
                    if (schj != null && schj.mark == 0) {
                        schj.mark = 1;
                        a.add(schj);
                        if (idCounter > 0) {
                            schj.id = idCounter++;
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

    /**
     * Computes and returns the set of productive states.
     * @param mask either REJECTING or ACCEPTING.
     * @return the set of productive states.
     */
    public int computeProductive(byte mask) {
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
     * Initializes for another run of {@link #doCount(int, int[][])}.
     */
    public void initCount() {
        maxLenComputed = 0;
        for (int i = 0; i < children.length; i++) {
            State s = children[i];
            if (s != null && s.depth > depth) {
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
    public void doCount(int maxlen, int[][] counts) {
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
                int cnt = 0;
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
    public void computeUpdate(int maxlen, int[][] counts, int[][] temps, int mrk) {
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
                int cnt = 0;
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

    public void computeStateCountsUpdate(int maxlen, CountsMap[] counts, CountsMap[] temps, int mrk) {
        if (maxlen > maxLenComputed) {
            if (maxlen > 1) {
                for (int i = 0; i < children.length; i++) {
                    State s = children[i];
                    if (s != null && s.mark >= mrk) {
                        // Note: this may affect the value of maxLenComputed of the
                        // current state: we may recurse into the same state,
                        // but with a lower maxlen.
                        s.computeStateCountsUpdate(maxlen-1, counts, temps, mrk);
                    }
                }
            }

            CountsMap thisMap = temps[this.mark - mrk];
            for (int j = maxLenComputed; j < maxlen; j++) {
                for (int i = 0; i < children.length; i++) {
                    State s = children[i];
                    if (s != null) {
                        CountsMap m = null;
                        if (s.mark >= mrk) {
                            m = temps[s.mark - mrk];
                        } else {
                            m = counts[s.id];
                        }
                        for (int l = 0; l < m.size(); l++) {
                            int cnt = m.getCount(l, j);
                            if (cnt != 0) {
                                thisMap.add(m.getState(l), j+1, cnt);
                            }
                        }
                    }
                }
            }
            maxLenComputed = maxlen;
        }
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
     * Returns a string representation of this state.
     * @return a string representation.
     */
    public String toString() {
        return "State" + id;
    }

	public State[] getChildren() {
		return children;
	}

	public void setChildren(State[] children) {
		this.children = children;
	}

	public State getParent() {
		return parent;
	}

	public void setParent(State parent) {
		this.parent = parent;
	}

	public ArrayList<State> getParents() {
		return parents;
	}

	public void setParents(ArrayList<State> parents) {
		this.parents = parents;
	}

	public boolean isAccepting(){
		return accepting==ACCEPTING;
	}

	public static int getNsym() {
		return nsym;
	}
}
