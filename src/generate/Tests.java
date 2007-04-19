package generate;

import junit.framework.TestSuite;
import junit.framework.Test;

public class Tests extends TestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(abbadingo.Tests.suite());
        return suite;
    }
}
