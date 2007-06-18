package org.vu.dfa;

import java.util.BitSet;

public final class Samples 
        implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    int [][] learningSamples;
    
    BitSet[] conflicts;

    public Samples(int[][] learningSamples, BitSet[] conflicts) {
        this.learningSamples = learningSamples;
        this.conflicts = conflicts;
    }
}
