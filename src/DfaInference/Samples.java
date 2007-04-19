package DfaInference;

import java.util.BitSet;

public final class Samples extends ibis.satin.SharedObject
        implements java.io.Serializable {
    int [][] learningSamples;
    BitSet[] conflicts;

    Samples(int[][] learningSamples, BitSet[] conflicts) {
        this.learningSamples = learningSamples;
        this.conflicts = conflicts;
    }
}
