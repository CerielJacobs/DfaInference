package stamina;

import sample.SampleString;

public class StaminaString implements SampleString {

    private final boolean accept;
    
    public StaminaString(boolean accept) {
        this.accept = accept;
    }
    
    @Override
    public void addToken(String s) {
        // TODO Auto-generated method stub
    }

    @Override
    public String[] getString() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isAccepted() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isNotAccepted() {
        // TODO Auto-generated method stub
        return false;
    }

}
