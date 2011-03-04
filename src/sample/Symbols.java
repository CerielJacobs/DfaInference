package sample;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;



/**
 * Class mapping symbols to integers, and vice versa.
 * A symbol here is just a string, usually consisting of just a single
 * character.
 */
public class Symbols implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(Symbols.class.getName());

    /** Hashmap to store the symbols, and map them to integers. */
    private final HashMap<String, Integer> sym2int;

    /** ArrayList to map the integers back onto the symbols. */
    private final ArrayList<String> int2sym;

    /**
     * Constructor for empty symbols set.
     */
    public Symbols() {
        sym2int = new HashMap<String, Integer>();
        int2sym = new ArrayList<String>();
    }

    /**
     * Copying constructor.
     * @param orig the symbol set to copy.
     */
    public Symbols(Symbols orig) {
        sym2int = new HashMap<String, Integer>(orig.sym2int);
        int2sym = new ArrayList<String>(orig.int2sym);
    }

    /**
     * Adds the specified symbol to the symbol table, and returns the
     * corresponding integer.
     * @param s the specified symbol
     * @return the corresponding integer.
     */
    public int addSymbol(String s) {
        Integer i = sym2int.get(s);
        if (i != null) {
            // Already added.
            return i.intValue();
        }
        int retval = int2sym.size();
        sym2int.put(s, new Integer(retval));
        int2sym.add(s);
        logger.debug("new Symbol added: " + s + " --> " + retval);
        return retval;
    }
    
    /**
     * Adds the symbols of the specified symbol set to the current one,
     * and computes a mapping from the symbol numbers in the specified set
     * to the (new) symbol numbers in the current set.
     * @param s the symbol set to be added.
     * @return the mapping.
     */
    public int[] addSymbols(Symbols s) {
        int ns = s.int2sym.size();
        for (String str : s.int2sym) {
            addSymbol(str);
        }
        int[] retval = new int[ns + 1];
        for (int i = 0; i < ns; i++) {
            retval[i] = sym2int.get(s.int2sym.get(i));
        }
        retval[ns] = int2sym.size();
        return retval;
    }

    /**
     * Returns the total number of different symbols.
     * @return the total number of symbols
     */
    public int nSymbols() {
        return int2sym.size();
    }

    /**
     * Returns the symbol corresponding to the specified integer.
     * @param n the specified integer.
     * @return the corresponding symbol.
     */
    public String getSymbol(int n) {
        return int2sym.get(n);
    }

    /**
     * Converts the samples to a suitable representation for DFA learning, which is
     * an array of strings which are represented as an array of
     * symbol numbers.
     * @param samples the samples as they are read.
     * @return the array of symbol number strings.
     */
    public int[][] convert2learn(SampleString[] samples) {
        int len = samples.length;

        int[][] result = new int[len][];
        len = 0;
        for (int i = 0; i < samples.length; i++) {
            result[len++] = samples[i].convert2Learn(this);
        }

        return result;
    }
}
