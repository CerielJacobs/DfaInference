package DfaInference;

import ibis.util.TypedProperties;

/**
 * Some configuration parameters.
 */
public interface Configuration {

    static final TypedProperties tp = new TypedProperties(System.getProperties());

    /** The complement language must participate in the MDL score. */   
    static final boolean MDL_COMPLEMENT
        = tp.booleanProperty("Complement", false);

    /**
     * The negative examples must participate in the MDL score.
     * This is not the same as MDL_COMPLEMENT. MDL_NEGATIVES
     * tries to build a rejecting DFA independently.
     */
    static final boolean MDL_NEGATIVES
        = tp.booleanProperty("Negatives", false);

    static final boolean NEW_IMPL
        = tp.booleanProperty("NewImpl", false);

    /** Parent sets are used for computing the (improved) MDL scores. */
    static final boolean USE_PARENT_SETS
        = tp.booleanProperty("UseParentSets", false);

    /** Use counts of productive states for DFA complexity. */
    static final boolean USE_PRODUCTIVE
        = tp.booleanProperty("UseProductive", false);

    static final boolean REFINED_MDL
        = tp.booleanProperty("RefinedMDL", false);

    /** Use incremental computation of counts. */
    static final boolean INCREMENTAL_COUNTS =
        tp.booleanProperty("IncrementalCounts", false);

    /** Include number of missing edges in DFA complexity computation. */
    static final boolean MISSING_EDGES =
        tp.booleanProperty("MissingEdges", false);

    /** Compensate for supposed redundancy in DFA complexity. */
    static final boolean COMPENSATE_REDUNDANCY =
        tp.booleanProperty("CompensateRedundancy", false);

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
