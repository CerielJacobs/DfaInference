package sample;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

public class SampleIO {
    
    private final SampleIOInterface sampleIO;
    
    public SampleIO(String className) {

        Class<?> cl;
        try {
            cl = Class.forName(className);
        } catch(ClassNotFoundException e) {
            throw new Error("Class " + className + " not found", e);
        }

        try {
            sampleIO = (SampleIOInterface) cl.newInstance();
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
     * Writes the specified samples to the specified output stream.
     * @param s the output stream.
     * @param sample the samples to write.
     * @exception IOException on I/O error.
     */
    public void putStrings(int nsym, SampleString[] sample, OutputStream s)
            throws IOException {
        s = new BufferedOutputStream(s);
        OutputStreamWriter b = new OutputStreamWriter(s);
        putStrings(nsym, sample, b);
        b.flush();
    }

    /**
     * Reads the specified reader, which should be in AbbaDingo format.
     * @param s the reader.
     * @return the sentences read.
     * @exception IOException on I/O error.
     */
    public SampleString[] getStrings(Reader s)
            throws java.io.IOException {
        return sampleIO.readStrings(s);
    }

    /**
     * Writes the specified samples to the specified writer.
     * @param sample the samples to write.
     * @param s the writer.
     * @exception IOException on I/O error.
     */
    public void putStrings(int nsym, SampleString[] sample, Writer s)
            throws java.io.IOException {
        sampleIO.writeStrings(nsym, sample, s);
    }
    
    /**
     * Reads samples from the specified file.
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

    /**
     * Writes the specified samples to the specified file.
     * @param sample the samples to write.
     * @param filename the filename to write to.
     * @throws java.io.IOException on I/O error.
     */
    public void putStrings(int nsym, SampleString[] sample, String filename) throws java.io.IOException {
	FileOutputStream f = new FileOutputStream(filename);
	putStrings(nsym, sample, f);
	f.close();
    }
    
    public SampleString getString(Symbols symbols, int[] sentence) {
	return sampleIO.convert2Sample(symbols, sentence);
    }

}
