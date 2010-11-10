package stamina;

import java.util.ArrayList;

import sample.SampleString;

public class StaminaString implements SampleString {

    private final ArrayList<String> sample = new ArrayList<String>(); 

    /**
     * Positive for positive string, 0 for negative string,
     * negative for unknown.
     */
    private char flag;
    
    public StaminaString(char flag) {
        this.flag = flag;
    }
    
    @Override
    public void addToken(String s) {
        sample.add(s);
    }

    @Override
    public String[] getString() {
        return sample.toArray(new String[sample.size()]);
    }

    @Override
    public boolean isAccepted() {
        return flag == '+';
    }

    @Override
    public boolean isNotAccepted() {
        return flag == '-';
    }

}
