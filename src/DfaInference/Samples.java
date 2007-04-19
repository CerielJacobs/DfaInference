package DfaInference;

import java.util.BitSet;

public final class Samples extends ibis.satin.SharedObject
        implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    int [][] learningSamples;
    
    BitSet[] conflicts;

    Samples(int[][] learningSamples, BitSet[] conflicts) {
        this.learningSamples = learningSamples;
        this.conflicts = conflicts;
    }
}
