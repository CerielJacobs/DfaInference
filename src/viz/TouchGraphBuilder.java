package viz;

import com.touchgraph.graphlayout.TGException;
import com.touchgraph.graphlayout.TGPanel;

public interface TouchGraphBuilder {
    public void build(TGPanel tgPanel) throws TGException;
}
