package sample;

import java.io.IOException;
import java.util.BitSet;


public final class Samples extends ibis.satin.SharedObject
        implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Symbols symbols;

    private int [][] learningSamples;
    
    private BitSet[] conflicts;
    
    private BitSet[] adjacencyComplement;
    
    private BitSet[] xAdjacencyComplement;
    
    private transient SampleIO sampleIO;
    
    public Symbols getSymbols() {
        return symbols;
    }

    public void setSymbols(Symbols symbols) {
        this.symbols = symbols;
    }

    public int[][] getLearningSamples() {
        return learningSamples;
    }

    public void setLearningSamples(int[][] learningSamples) {
        this.learningSamples = learningSamples;
    }

    public BitSet[] getConflicts() {
        return conflicts;
    }

    public void setConflicts(BitSet[] conflicts) {
        this.conflicts = conflicts;
    }

    public Samples(String sampleIOClass, String learningSetFile) throws IOException {
	this(new SampleIO(sampleIOClass), learningSetFile);
    }
    
    public Samples(SampleIO sampleReader, String learningSetFile) throws IOException {       
        SampleString[] samples = null;
        this.sampleIO = sampleReader;
        if (learningSetFile != null) {
            samples = sampleReader.getStrings(learningSetFile);
        } else {
            samples = sampleReader.getStrings(System.in);
        }

        symbols = new Symbols();
        learningSamples = symbols.convert2learn(samples);
        computeAdjacencyComplement();
    }
 
    public SampleIO getSampleIO() {
        return sampleIO;
    }

    public void setSampleIO(SampleIO sampleIO) {
        this.sampleIO = sampleIO;
    }

    public Samples(Symbols symbols, int[][] learningSamples, BitSet[] conflicts) {

        this.learningSamples = learningSamples;
        this.conflicts = conflicts;
        this.symbols = symbols;

        computeAdjacencyComplement();
    }

    public Samples(int nsym, int[][] learningSamples, BitSet[] conflicts) {

        this.learningSamples = learningSamples;
        this.conflicts = conflicts;
        symbols = new Symbols();
        for (int i = 0; i < nsym; i++) {
            symbols.addSymbol("" + i);
        }

        computeAdjacencyComplement();
    }
    
    private void computeAdjacencyComplement() {
        setAdjacencyComplement(new BitSet[symbols.nSymbols()+1]);
        xAdjacencyComplement = new BitSet[symbols.nSymbols()+1];
        for (int i = 0; i < getAdjacencyComplement().length; i++) {
            getAdjacencyComplement()[i] = new BitSet();
            xAdjacencyComplement[i] = new BitSet();
        }
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
        for (int i = 0; i < getAdjacencyComplement().length; i++) {
            getAdjacencyComplement()[i].flip(0, symbols.nSymbols()+1);
            xAdjacencyComplement[i].flip(0, symbols.nSymbols()+1);
        }
    }
    
    private void setAdjacent(boolean reject, int sym1, int sym2) {
        if (reject) {
            xAdjacencyComplement[sym1].set(sym2);
        } else {
            getAdjacencyComplement()[sym1].set(sym2);
        }
    }

    public void setAdjacencyComplement(BitSet[] adjacencyComplement) {
	this.adjacencyComplement = adjacencyComplement;
    }

    public BitSet[] getAdjacencyComplement() {
	return adjacencyComplement;
    }
    
    public SampleString[] getStrings() {
	SampleString[] result = new SampleString[learningSamples.length];
	for (int i = 0; i < learningSamples.length; i++) {
	    result[i] = sampleIO.getString(symbols, learningSamples[i]);
	}
	return result;
    }
}
