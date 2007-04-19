package DfaInference;

import java.util.*;
import org.apache.log4j.Logger;

import abbadingo.AbbaDingoString;

/**
 * Class mapping symbols to integers, and vice versa.
 * A symbol here is just a string, usually consisting of just a single
 * character.
 */
public class Symbols {

    /** Log4j logger. */
    private static Logger logger = Logger.getLogger(Symbols.class.getName());

    /** Hashmap to store the symbols, and map them to integers. */
    private static HashMap sym2int = new HashMap();

    /** ArrayList to map the integers back onto the symbols. */
    private static ArrayList int2sym = new ArrayList();

    /** Constructor. Private so that it cannot be instantiated. */
    private Symbols() {
    }

    /**
     * Adds the specified symbol to the symbol table, and returns the
     * corresponding integer.
     * @param s the specified symbol
     * @return the corresponding integer.
     */
    public static int addSymbol(String s) {
        Integer i = (Integer) sym2int.get(s);
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
     * Returns the total number of different symbols.
     * @return the total number of symbols
     */
    public static int nSymbols() {
        return int2sym.size();
    }

    /**
     * Returns the symbol corresponding to the specified integer.
     * @param n the specified integer.
     * @return the corresponding symbol.
     */
    public static String getSymbol(int n) {
        return (String) int2sym.get(n);
    }

    /**
     * Extracts the tokens from the specified <code>AbbaDingoString</code>
     * and converts them to token numbers.
     * The first element of the resulting array is 1 for accept, 0 for reject,
     * and -1 for unknown. The other elements represent the symbols in the
     * string.
     * @param s the string to convert.
     * @return an array with token numbers.
     */
    public static int[] abbaToSym(AbbaDingoString s) {
        String[] str = s.getString();
        int[] tokens = new int[str.length+1];
        
        tokens[0] = s.isAccepted() ? 1 : s.isNotAccepted() ? 0 : -1;
        for (int i = 0; i < str.length; i++) {
            tokens[i+1] = addSymbol(str[i]);
        }

        return tokens;
    }

    /**
     * Converts the samples, which are in AbbaDingoString format,
     * to a suitable representation for DFA learning, which is
     * an array of strings which are represented as an array of
     * symbol numbers.
     * @param samples the samples as they are read.
     * @return the array of symbol number strings.
     */
    public static int[][] convert2learn(AbbaDingoString[] samples) {
        int len = samples.length;

        int[][] result = new int[len][];
        len = 0;
        for (int i = 0; i < samples.length; i++) {
            result[len++] = abbaToSym(samples[i]);
        }

        return result;
    }
}
