// File: $Id: Helpers.java,v 1.1 2005/07/06 16:29:03 ceriel Exp $

package DfaInference;

/** 
 * This class contains some miscellaneous helper methods.
 */
public final class Helpers {
    /**
     * Disallow constructor since it is a static only class.
     */
    private Helpers() {}

    /**
     * Returns a string describing the platform version that is used.
     * @return description string.
     */
    static String getPlatformVersion()
    {
        java.util.Properties p = System.getProperties();

        return "Java " + p.getProperty( "java.version" )
            + " (" + p.getProperty( "java.vendor" ) + ") on "
            + p.getProperty( "os.name" ) + " " + p.getProperty( "os.version" )
            + " (" + p.getProperty( "os.arch" ) + ")";
    }
}
