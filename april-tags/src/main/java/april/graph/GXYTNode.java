package april.graph;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;

import april.jmat.*;
import april.jmat.geom.*;
import april.util.*;

public class GXYTNode extends GNode implements SpatialNode
{
    public GXYTNode copy()
    {
        GXYTNode g = new GXYTNode();
        g.state = LinAlg.copy(state);
        g.init = LinAlg.copy(init);
        if (truth != null)
            g.truth = LinAlg.copy(truth);

        g.attributes = Attributes.copy(attributes);

        return g;
    }

    public int getDOF()
    {
        return 3;
    }

    public double[] toXyzRpy()
    {
        return new double[] { state[0], state[1], 0, 0, 0, state[2] };
    }
}
