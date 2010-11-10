package sample;

import java.io.IOException;
import java.io.Reader;

/**
 * Interface for reading samples. Allows for multiple formats.
 */
public interface ReadSample {
    /**
     * Reads sentences from the specified file. 
     * @param r the reader to use.
     * @return the sentences read.
     * @exception IOException on IO error.
     */
    public SampleString[] readStrings(Reader r) throws IOException;
}
