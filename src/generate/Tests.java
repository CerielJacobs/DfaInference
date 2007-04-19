package generate;

import junit.framework.Test;
import junit.framework.TestSuite;

public class Tests extends TestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(abbadingo.Tests.suite());
        return suite;
    }
}
