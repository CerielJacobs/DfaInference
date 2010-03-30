package abbadingo;

import java.io.IOException;
import java.io.StringReader;

import sample.SampleString;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class Tests extends TestCase {
    final static String input = 
            "3 2\n" + "1 3 aap noot mies\n" + "0 2 0 1\n" + "-1 1 bla\n";

    public static Test suite() {
        return new TestSuite(Tests.class);
    }

    public void testReader() throws IOException {
        StringReader r = new StringReader(input);
        SampleString[] strings = AbbaDingoReader.getStrings(r);

        assertTrue(strings.length == 3);

        assertTrue(strings[0].isAccepted());
        assertFalse(strings[0].isNotAccepted());

        String[] words = strings[0].getString();
        assertTrue(words.length == 3);
        assertEquals(words[0], "aap");
        assertEquals(words[1], "noot");
        assertEquals(words[2], "mies");

        assertFalse(strings[1].isAccepted());
        assertTrue(strings[1].isNotAccepted());

        words = strings[1].getString();
        assertTrue(words.length == 2);
        assertEquals(words[0], "0");
        assertEquals(words[1], "1");

        assertFalse(strings[2].isAccepted());
        assertFalse(strings[2].isNotAccepted());

        words = strings[2].getString();
        assertTrue(words.length == 1);
        assertEquals(words[0], "bla");
    }
}
