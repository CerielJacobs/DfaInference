package sample;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Interface for reading or writing samples. Allows for multiple formats.
 */
public interface SampleIOInterface {
    /**
     * Reads sentences from the specified reader. 
     * @param r the reader to use.
     * @return the sentences read.
     * @exception IOException on IO error.
     */
    public SampleString[] readStrings(Reader r) throws IOException;
    
    /**
     * Writes sentences to the specified writer.
     * @param nsym the number of symbols.
     * @param s the samples to write.
     * @param w the writer. 
     * @exception IOException on IO error.
     */
    public void writeStrings(int nsym, SampleString[] s, Writer w) throws IOException;
    
    public SampleString convert2Sample(Symbols symbols, int[] sentence);
}
