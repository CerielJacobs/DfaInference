package iterativeDeepening;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

import DfaInference.ControlResultPair;

public class ControlResultPairTable extends ibis.satin.SharedObject 
        implements ControlResultPairTableInterface, Runnable,
        java.io.Serializable {
    
    private static final long serialVersionUID = -1L;

    /** The entries in this table, organized according to their first control-entry. */
    private final ArrayList<ControlResultPair> table = new ArrayList<ControlResultPair>();
    
    /** Fixed initial choice when iterative deepening. */
    private ControlResultPair fix = null;
    
    /** Index offset when iterative deepening. */
    private int fixOffset = 0;
    
    /** Filename to read from/write to. */
    private transient final File file;
    
    private transient TableManager manager;
    
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
    
    public void setManager(TableManager manager) {
        this.manager = manager;
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
        // System.out.print("GetResult {");
        for (int i = fixOffset; l != null && i < control.length; i++) {
            // System.out.print(" " + control[i]);
            if (control[i] < l.size()) {
                ControlResultPair p = l.get(control[i]);
                if (i == control.length - 1 && p.control != null) {
                    // System.out.println(" } found, score " + p.score);
                    return p;
                }
                l = p.getTable();
            } else {
                break;
            }
        }
        // System.out.println("} not found");
        return null;
    }
    
    /**
     * Adds the specified result to the table.
     * @param p the result to be added.
     */
    public synchronized void putResult(int off, int depth, ControlResultPair p) {
        if (off < fixOffset) {
            // This may happen because we may receive results that are from
            // before a fix.
            return;
        }

        ArrayList<ControlResultPair> l = table;

        // System.out.print("PutResult, depth = " + depth + ", control = {");

        for (int i = fixOffset; i < depth; i++) {
            // System.out.print(" " + p.control[i]);

           while (p.control[i] >= l.size()) {
                l.add(new ControlResultPair(0, null, 0, 0));
            }
        
            ControlResultPair v = l.get(p.control[i]);

            if (v.control != null) {
                // Already have a result higher up. Ignore this one.
                // This may happen if we get results out of order.
                // System.out.println("} ignored");
                return;
            }
            
            if (i == depth-1) {
                v.control = p.control.clone();
                v.setFromChoiceIndex(p.getFromChoiceIndex());
                v.setDepth(depth);
                v.score = p.score;
                v.setTable(null);
            } else {
                if (v.getTable() == null) {
                    v.setTable(new ArrayList<ControlResultPair>());
                }
                l = v.getTable();
            }
        }
        // System.out.println("}, score = " + p.score);
    }
    
    /**
     * Fixes a base for iterative deepening.
     * @param p the base.
     */
    public synchronized void fix(ControlResultPair p) {
        sharedWrite(p);
        doWrite();
    }

    public void sharedWrite(ControlResultPair p) {
        fix = p;
        table.clear();
        fixOffset = p.control.length;       
    }
    
    public void finish() {
        manager.done();
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
            p.write(w);
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
        line = r.readLine();
        int tableLength = (new Integer(line)).intValue();
        for (int i = 0; i < tableLength; i++) {          
            table.add(new ControlResultPair(r));
        }
    }

    public int getFixOffset() {
        return fixOffset;
    }
    
    /**
     * Initiates a dump, every 20 seconds.
     */
    public void run() {
        for (;;) {
            try {
                Thread.sleep(20000);
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
