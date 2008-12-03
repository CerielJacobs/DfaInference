package DfaInference;

import java.util.BitSet;

public final class Samples extends ibis.satin.SharedObject
        implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    Symbols symbols;
    
    int [][] learningSamples;
    
    BitSet[] conflicts;
    
    BitSet[] adjacencyComplement;
    
    BitSet[] xAdjacencyComplement;

    public Samples(Symbols symbols, int[][] learningSamples, BitSet[] conflicts) {

        this.learningSamples = learningSamples;
        this.conflicts = conflicts;
        this.symbols = symbols;
        adjacencyComplement = new BitSet[symbols.nSymbols()+1];
        xAdjacencyComplement = new BitSet[symbols.nSymbols()+1];
        for (int i = 0; i < adjacencyComplement.length; i++) {
            adjacencyComplement[i] = new BitSet();
            xAdjacencyComplement[i] = new BitSet();
        }
        computeAdjacencyComplement();
    }
    
    private void computeAdjacencyComplement() {
        int nsym = symbols.nSymbols();
        for (int i = 0; i < learningSamples.length; i++) {
            int[] sample = learningSamples[i];
            boolean reject = sample[0] != 1;
            int sym1 = symbols.nSymbols();
            for (int j = 1; j < sample.length; j++) {
                int sym2 = sample[j];
                setAdjacent(reject, sym2, sym1);
                sym1 = sym2;
            }
            setAdjacent(reject, nsym, sym1);
        }
        for (int i = 0; i < adjacencyComplement.length; i++) {
            adjacencyComplement[i].flip(0, symbols.nSymbols()+1);
            xAdjacencyComplement[i].flip(0, symbols.nSymbols()+1);
        }
    }
    
    private void setAdjacent(boolean reject, int sym1, int sym2) {
        if (reject) {
            xAdjacencyComplement[sym1].set(sym2);
        } else {
            adjacencyComplement[sym1].set(sym2);
        }
    }
}
