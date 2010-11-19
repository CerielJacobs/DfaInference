package DfaInference;

import ibis.util.TypedProperties;

/**
 * Some configuration parameters.
 */
public interface Configuration {

    static final TypedProperties tp = new TypedProperties(System.getProperties());

    /** The complement language must participate in the MDL score. */   
    static final boolean MDL_COMPLEMENT
        = tp.getBooleanProperty("Complement", false);

    /**
     * The negative examples must participate in the MDL score.
     * This is not the same as MDL_COMPLEMENT. MDL_NEGATIVES
     * tries to build a rejecting DFA independently.
     */
    static final boolean NEGATIVES
        = tp.getBooleanProperty("Negatives", false);

    /** Parent sets are used for computing the (improved) MDL scores. */
    static final boolean USE_PARENT_SETS
        = tp.getBooleanProperty("UseParentSets", false);

    /** Use counts of productive states for DFA complexity. */
    static final boolean USE_PRODUCTIVE
        = tp.getBooleanProperty("UseProductive", false);

    static final boolean REFINED_MDL
        = tp.getBooleanProperty("RefinedMDL", false);

    /** Use incremental computation of counts. */
    static final boolean INCREMENTAL_COUNTS =
        tp.getBooleanProperty("IncrementalCounts", false);
  
    static final boolean USE_CHISQUARE =
        tp.getBooleanProperty("ChiSquare", false);
    
    static final boolean USE_STAMINA =
        tp.getBooleanProperty("Stamina", false);
    
    static final int CHI_MIN =
        tp.getIntProperty("ChiMin", 5);

    static final boolean UNIQUE_SAMPLES = ! (USE_CHISQUARE || USE_STAMINA);

    /**
     * Which version of DFA scoring to use? The options are listed below.
     * 0: code DFA as two-dimensional array with state and sym index,
     *    state value.
     * 1: code DFA as list of edges.
     * 2: code DFA as two-dimensional array of bits indicating presence/absence
     *    of edge, code edges as just destination state.
     * 3: whichever gives the lowest score.
     * 4: whichever gives the highest score.
     */
    static final int DFA_SCORING =
        tp.getIntProperty("DFAScoring", 3);

    static final boolean USE_ADJACENCY =
        tp.getBooleanProperty("Adjacency", false);
    
    /** Limits adjacency checks to begin and end of sentence. */
    static final boolean ONLY_CHECK_BEGIN_AND_END_ADJACENCY =
        tp.getBooleanProperty("LimitedAdjacency", false);
    
    static final boolean NEEDS_EDGECOUNTS = USE_CHISQUARE || USE_STAMINA;
  
    /**
     * Bit that indicates accepting state or state of DFA that recognizes
     * positive samples.
     */
    static final byte ACCEPTING = 0x01;

    /**
     * Bit that indicates rejecting state or state of DFA that recognizes
     * negative samples.
     */
    static final byte REJECTING = 0x02;

    /** Mask for ACCEPTING and REJECTING. */
    static final byte MASK = ACCEPTING | REJECTING;
}
