package sample;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class SampleReader {
    
    private final ReadSample reader;
    
    public SampleReader(String className) {

        Class<?> cl;
        try {
            cl = Class.forName(className);
        } catch(ClassNotFoundException e) {
            throw new Error("Class " + className + " not found", e);
        }

        try {
            reader = (ReadSample) cl.newInstance();
        } catch(Exception e) {
            throw new Error("Could not instantiate " + className, e);
        }

    }
    
    /**
     * Reads the specified input stream.
     * @param s the input stream.
     * @return the sentences read.
     * @exception IOException on I/O error.
     */
    public SampleString[] getStrings(InputStream s)
            throws IOException {
        s = new BufferedInputStream(s);
        return getStrings(new InputStreamReader(s));
    }

    /**
     * Reads the specified reader, which should be in AbbaDingo format.
     * @param s the reader.
     * @return the sentences read.
     * @exception IOException on I/O error.
     */
    public SampleString[] getStrings(Reader s)
            throws java.io.IOException {
        return reader.readStrings(s);
    }

    /**
     * Reads the specified file, which should be in AbbaDingo format.
     * @param filename the name of the input file.
     * @return the sentences read.
     * @exception IOException on I/O error.
     */
    public SampleString[] getStrings(String filename)
            throws java.io.IOException {
        FileInputStream f = new FileInputStream(filename);
        SampleString[] strs = getStrings(f);
        f.close();
        return strs;
    }

}
