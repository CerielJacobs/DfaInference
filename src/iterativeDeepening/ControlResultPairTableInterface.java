package iterativeDeepening;

import DfaInference.ControlResultPair;
import ibis.satin.WriteMethodsInterface;

public interface ControlResultPairTableInterface extends WriteMethodsInterface {
    public void sharedWrite(ControlResultPair p);
    public void finish();
}
