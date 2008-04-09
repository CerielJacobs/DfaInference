package DfaInference;


/**
 * Collects information that allows for the undoing of a state merge.
 */
public final class UndoInfo implements Configuration {

    /** Undo info per state. */
    private static class StateInfo {
        /** Free list of <code>StateInfo</code> objects. */
        static StateInfo freeList;

        /** Links <code>StateInfo</code> objects */
        StateInfo next;

        /** The original State object. */
        State orig;

        /** The accepting field of the object to be saved. */
        byte accepting;

        /** The productive field of the object to be saved. */
        byte productive;

        /** The depth field of the object to be saved. */
        int depth;

        /** The weight field of the object to be saved. */
        int weight;
        
        int[] edgeWeights;


        /**
         * Saves the state of the specified state.
         * @param s the state to save.
         */
        private void set(State s) {
            orig = s;
            accepting = s.accepting;
            productive = s.productive;
            depth = s.depth;
            weight = s.weight;
            edgeWeights = (int[]) s.edgeWeights.clone();
        }

        /**
         * Saves the specified state, inserts it in the indicated
         * list, and returns the new list.
         * @param s the state to save
         * @param u the list of states saved so far
         * @return the new list.
         */
        public static StateInfo saveState(State s, StateInfo u) {
            StateInfo si = freeList;
            if (si != null) {
                freeList = si.next;
            } else {
                si = new StateInfo();
            }
            si.set(s);
            si.next = u;
            return si;
        }

        /**
         * Restores the state saved in the list headed by the current
         * <code>StateInfo</code> object.
         */
        public void restore(DFA dfa) {
            StateInfo last;
            StateInfo n = this;

            do {
                last = n;
                State orig = last.orig;
                orig.accepting = last.accepting;

                if (! REFINED_MDL) {
                    if (dfa.counts != null) {
                        dfa.counts[0][orig.id]
                            = ((orig.accepting & ACCEPTING) != 0) ? 1 : 0;
                    }
                    if (dfa.xCounts != null) {
                        dfa.xCounts[0][orig.id]
                                = ((orig.accepting & REJECTING) != 0) ? 1 : 0;
                    }
                }

                orig.productive = last.productive;
                orig.depth = last.depth;
                orig.weight = last.weight;
                orig.edgeWeights = last.edgeWeights;
                last.edgeWeights = null;
                n = last.next;
            } while (n != null);

            last.next = freeList;
            freeList = this;
        }
    }

    /** Undo info per edge. */
    private static class EdgeInfo {
        /** Free list of <code>EdgeInfo</code> objects. */
        static EdgeInfo freeList;

        /** Links <code>EdgeInfo</code> objects */
        EdgeInfo next;

        /** The original State object. */
        State orig;

        /** The symbol labeling the saved edge. */
        int sym;

        /** The target state of the edge. */
        State target;

        /**
         * Constructor that saves an edge.
         */
        private EdgeInfo(State s, int sym, State t) {
            set(s, sym, t);
        }

        /**
         * Saves an edge.
         * @param s the source state.
         * @param sym the edge label.
         * @param t the target state.
         */
        private void set(State s, int sym, State t) {
            orig = s;
            this.sym = sym;
            target = t;
        }

        /**
         * Saves the specified edge, inserts it in the indicated
         * list, and returns the new list.
         * @param s the source state of the edge
         * @param sym the edge label
         * @param t the destination state
         * @param u the list of edges saved so far
         * @return the new list.
         */
        public static EdgeInfo saveEdge(State s, int sym, State t, EdgeInfo u) {
            EdgeInfo si = freeList;
            if (si != null) {
                freeList = si.next;
                si.set(s, sym, t);
            } else {
                si = new EdgeInfo(s, sym, t);
            }
            si.next = u;
            return si;
        }

        /**
         * Restores the edge saved in the list headed by the current
         * <code>EdgeInfo</code> object.
         */
        public void restore() {
            EdgeInfo last;
            EdgeInfo n = this;

            do {
                last = n;
                last.orig.children[last.sym] = last.target;
                n = last.next;
            } while (n != null);

            last.next = freeList;
            freeList = this;
        }
    }

    /** Undo info per parent set. */
    private static class ParentSetInfo {
        /** Free list of <code>ParentSetInfo</code> objects. */
        static ParentSetInfo freeList;

        /** Links <code>ParentSetInfo</code> objects */
        ParentSetInfo next;

        /** the state from which the parent set to restore. */
        State dest;

        /** The state to be added/removed. */
        State state;

        /** Indicates whether the state should be added or removed. */
        boolean toAdd;

        /**
         * Constructor that stores undo information for parent sets.
         * @param dest the state from which the parent set to restore.
         * @param state the state to add/remove.
         * @param toAdd indicates whether to add or remove.
         */
        private ParentSetInfo(State dest, State state, boolean toAdd) {
            this.dest =dest;
            this.state = state;
            this.toAdd = toAdd;
        }

        /**
         * Initializes undo information for parent sets.
         * @param dest the state from which the parent set to restore.
         * @param state the state to add/remove.
         * @param toAdd indicates whether to add or remove.
         */
        private void set(State dest, State state, boolean toAdd) {
            this.dest = dest;
            this.state = state;
            this.toAdd = toAdd;
        }

        /**
         * Saves the specified parent set info, inserts it in the indicated
         * list, and returns the new list.
         * @param dest the state from which the parent set to restore.
         * @param state the state to add/remove.
         * @param toAdd indicates whether to add or remove.
         * @param u the original list.
         * @return the new list
         */
        public static ParentSetInfo saveParentSet(State dest, State state,
                boolean toAdd, ParentSetInfo u) {
            ParentSetInfo si = freeList;
            if (si != null) {
                freeList = si.next;
                si.set(dest, state, toAdd);
            } else {
                si = new ParentSetInfo(dest, state, toAdd);
            }
            si.next = u;
            return si;
        }

        /**
         * Restores the parent set saved in the list headed by the current
         * <code>ParentSetInfo</code> object.
         */
        public void restore() {
            ParentSetInfo n = this;
            ParentSetInfo last;

            do {
                last = n;
                if (n.toAdd) {
                    n.dest.parents.add(n.state);
                } else {
                    n.dest.parents.remove(n.state);
                }
                n = n.next;
            } while (n != null);

            last.next = freeList;
            freeList = this;
        }
    }

    /** Free list of <code>UndoInfo</code> objects. */
    private static UndoInfo freeList;

    /** Links <code>UndoInfo</code> objects together. */
    private UndoInfo next;

    /** Saved number of states. */
    private int nStates;
    
    /** Saved number of accepting states. */
    private int nAccepting;
    
    /** Saved number of rejecting states. */
    private int nRejecting;

    /** Saved number of edges. */
    private int nEdges;

    /** Saved MDL score. */
    private double MDLScore;

    /** Saved DFA score. */
    private double DFAScore;

    /** Saved number of productive states. */
    private int nProductiveStates;

    /** Saved number of productive states in rejecting DFA. */
    private int nXProductiveStates;

    /** Saved number of productive edges. */
    private int nProductiveEdges;

    /** Saved number of productive edges in rejecting DFA. */
    private int nXProductiveEdges;

    /** Missing edges in DFA. */
    private int missingEdges;

    /** Missing edges in rejecting DFA. */
    private int missingXEdges;

    /** Collects all Undo info. */
    private StateInfo savedStates;

    /** Collects all Edges to be undone. */
    private EdgeInfo savedEdges;

    /** Collects all parent set changes to be undone. */
    private ParentSetInfo savedSets;

    /** Storage for saving counts. */
    double[] counts;

    /** Set when counts field is initialized. */
    boolean countsInitialized;

    /** DFA for which stuff was saved. */
    DFA dfa;

    /**
     * Obtains an <code>UndoInfo</code> object,
     * initialized with the specified parameters.
     * @param dfa the DFA to save from.
     * @return the <code>UndoInfo</code> object.
     */
    public static UndoInfo getUndoInfo(DFA dfa) {
        UndoInfo si = freeList;
        if (si != null) {
            freeList = si.next;
        } else {
            si = new UndoInfo();
        }
        si.dfa = dfa;
        si.nStates = dfa.nStates;
        si.nEdges = dfa.nEdges;
        si.nAccepting = dfa.nAccepting;
        si.nRejecting = dfa.nRejecting;
        si.MDLScore = dfa.MDLScore;
        si.DFAScore = dfa.DFAScore;
        si.nProductiveStates = dfa.nProductiveStates;
        si.nXProductiveStates = dfa.nXProductiveStates;
        si.nProductiveEdges = dfa.nProductiveEdges;
        si.nXProductiveEdges = dfa.nXProductiveEdges;
        si.missingEdges = dfa.missingEdges;
        si.missingXEdges = dfa.missingXEdges;
        si.countsInitialized = false;
        si.savedStates = null;
        si.savedEdges = null;
        si.savedSets = null;
        return si;
    }

    public void saveCounts() {
        if (! REFINED_MDL) {
            // Only saves the counts from the start symbol, as these are
            // the only ones that are modified when there is Undo info.
            if (counts == null) {
                counts = new double[2*(dfa.maxlen + 1)];
            }
            if (dfa.counts != null) {
                for (int i = 0; i <= dfa.maxlen; i++) {
                    counts[i] = dfa.counts[i][dfa.startState.id];
                }
                countsInitialized = true;
            }
            if (dfa.xCounts != null) {
                for (int i = 0; i <= dfa.maxlen; i++) {
                    counts[i+dfa.maxlen+1] = dfa.xCounts[i][dfa.startState.id];
                }
                countsInitialized = true;
            }
        }
    }

    private void restoreCounts() {
        if (countsInitialized) {
            if (dfa.counts != null) {
                for (int i = 0; i <= dfa.maxlen; i++) {
                    dfa.counts[i][dfa.startState.id] = counts[i];
                }
            }
            if (dfa.xCounts != null) {
                for (int i = 0; i <= dfa.maxlen; i++) {
                    dfa.xCounts[i][dfa.startState.id] = counts[i+dfa.maxlen+1];
                }
            }
        }
    }

    /**
     * Adds the Undo info for the specified state.
     * @param s the state to save.
     */
    public void addData(State s) {
        savedStates = StateInfo.saveState(s, savedStates);
    }

    /**
     * Adds the Undo info to restore an edge.
     * @param s the state to save the edge from
     * @param sym the edge label
     * @param t the edge target
     */
    public void addChild(State s, int sym, State t) {
        savedEdges = EdgeInfo.saveEdge(s, sym, t, savedEdges);
    }

    /**
     * Applies all Undo information collected so far.
     * Note: this method is destructive.
     */
    public void undo() {
        // restore counts first. Values of counts[0] may be changed again
        // when restoring states.
        restoreCounts();

        if (savedStates != null) {
            savedStates.restore(dfa);
            savedStates = null;
        }
        if (savedEdges != null) {
            savedEdges.restore();
            savedEdges = null;
        }
        if (savedSets != null) {
            savedSets.restore();
            savedSets = null;
        }

        dfa.MDLScore = MDLScore;
        dfa.DFAScore = DFAScore;
        dfa.nStates = nStates;
        dfa.nEdges = nEdges;
        dfa.nAccepting = nAccepting;
        dfa.nRejecting = nRejecting;
        dfa.nProductiveStates = nProductiveStates;
        dfa.nXProductiveStates = nXProductiveStates;
        dfa.nProductiveEdges = nProductiveEdges;
        dfa.nXProductiveEdges = nXProductiveEdges;
        dfa.missingEdges = missingEdges;
        dfa.missingXEdges = missingXEdges;

        next = freeList;
        freeList = this;
    }

    public void addParentRemoval(State dest, State s) {
        savedSets = ParentSetInfo.saveParentSet(dest, s, true, savedSets);
    }

    public void addParentAddition(State dest, State s) {
        savedSets = ParentSetInfo.saveParentSet(dest, s, false, savedSets);
    }
}
