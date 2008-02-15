package DfaInference;

import java.util.BitSet;

public final class Samples extends ibis.satin.SharedObject
        implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    Symbols symbols;
    
    int [][] learningSamples;
    
    BitSet[] conflicts;

    Samples(Symbols symbols, int[][] learningSamples, BitSet[] conflicts) {
        this.symbols = symbols;
        this.learningSamples = learningSamples;
        this.conflicts = conflicts;
    }
}
