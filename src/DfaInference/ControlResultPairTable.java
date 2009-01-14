package DfaInference;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

public class ControlResultPairTable extends ibis.satin.SharedObject 
        implements Runnable, java.io.Serializable {
    
    private static final long serialVersionUID = 8456707383205600263L;

    /** The entries in this table, organized according to their first control-entry. */
    private final ArrayList<ControlResultPair> table = new ArrayList<ControlResultPair>();
    
    /** Fixed initial choice when iterative deepening. */
    private ControlResultPair fix = null;
    
    /** Index offset when iterative deepening. */
    private int fixOffset = 0;
    
    /** Filename to read from/write to. */
    private final File file;
    
    /**
     * Constructor.
     * @param fn file to read from/write to.
     */
    public ControlResultPairTable(String fn) {
        file = new File(fn);
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            read(br);
            br.close();
        } catch (Throwable e) {
            // ignored, does not matter.
        }
        Thread t = new Thread(this);
        t.setDaemon(true);
        t.start();
    }
    
    public ControlResultPairTable() {
        file = null;
    }
    
    public int[] getFixControl() {
        if (fix != null) {
            return fix.control.clone();
        }
        return null;
    }
    
    /**
     * Requests the result for the specified control.
     * @param control control for which result is requested.
     * @return the result, if available, or <code>null</code>.
     */
    public synchronized ControlResultPair getResult(int[] control) {
        ArrayList<ControlResultPair> l = table;
        for (int i = fixOffset; l != null && i < control.length; i++) {
            if (control[i] < l.size()) {
                ControlResultPair p = l.get(control[i]);
                if (i == control.length - 1 && p.control != null) {
                    return p;
                }
                l = p.table;
            } else {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Adds the specified result to the table.
     * @param p the result to be added.
     */
    public synchronized void putResult(ControlResultPair p) {
        ArrayList<ControlResultPair> l = table;
        for (int i = fixOffset; i < p.control.length; i++) {

           while (p.control[i] >= l.size()) {
                l.add(new ControlResultPair(0, null, 0, 0));
            }
        
            ControlResultPair v = l.get(p.control[i]);

            if (v.control != null) {
                // Already have a result higher up. Ignore this one.
                return;
            }
            if (i == p.control.length - 1) {
                v.control = p.control.clone();
                v.fromChoiceIndex = p.fromChoiceIndex;
                v.fromWindowIndex = p.fromWindowIndex;
                v.score = p.score;
                v.table = null;
            } else {
                if (v.table == null) {
                    v.table = new ArrayList<ControlResultPair>();
                }
                l = v.table;
            }
        }
    }
    
    /**
     * Fixes a base for iterative deepening.
     * @param p the base.
     */
    public synchronized void fix(ControlResultPair p) {
        fix = p;
        fixOffset = p.control.length;
        table.clear();
        doWrite();
    }
    
    /**
     * Writes a string representation of this object to the
     * specified writer.
     * @param w the writer.
     */
    private synchronized void write(Writer w) throws IOException {
        w.write("" + fixOffset + "\n");
        if (fixOffset > 0) {
            fix.write(w);
        }
        w.write("" + table.size() + "\n");
        for (int i = 0; i < table.size(); i++) {
            ControlResultPair p = table.get(i);
            if (p.control == null) {
                w.write("0\n");
            } else {
                w.write("1\n");
                p.write(w);
            }
        }
    }

    /**
     * Initializes from the specified reader.
     * @param r the reader.
     */
    private void read(BufferedReader r) throws IOException {
        String line = r.readLine();
        fixOffset = (new Integer(line)).intValue();
        if (fixOffset > 0) {
            fix = new ControlResultPair(r);
        }
        int tableLength = (new Integer(line)).intValue();
        for (int i = 0; i < tableLength; i++) {
            line = r.readLine();
            int hasEntry = (new Integer(line)).intValue();
            if (hasEntry != 0) {
                table.add(new ControlResultPair(r));
            } else {
                table.add(new ControlResultPair(0, null, 0, 0));
            }
        }
    }
    
    /**
     * Initiates a dump, every 60 seconds.
     */
    public void run() {
        for (;;) {
            try {
                Thread.sleep(60000);
            } catch (Throwable e) {
                // ignored
            }
            doWrite();
        }
    }
    
    public synchronized void doWrite() {
        try {
            File temp = File.createTempFile("dfa", "dmp", new File("."));
            BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
            write(bw);
            bw.close();
            if (! temp.renameTo(file)) {
                throw new IOException("rename failed");
            }
        } catch(IOException e) {
            System.err.println("Warning: could not write dump");
        }
    }

}
