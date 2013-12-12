package april.camera.calibrator;

import java.io.*;
import java.util.*;

import april.camera.*;
import april.graph.*;
import april.jmat.*;
import april.util.*;

public class GExtrinsicsNode extends GNode
{
    // inherited: init, state, truth, attributes

    // init, state, truth all represent the "camera to global" transformation

    public GExtrinsicsNode()
    {
    }

    public GExtrinsicsNode(double bodyToGlobal[][])
    {
        this(bodyToGlobal, null);
    }

    public GExtrinsicsNode(double bodyToGlobal[][], double trueBodyToGlobal[][])
    {
        this.init = LinAlg.matrixToXyzrpy(bodyToGlobal);
        this.state = LinAlg.copy(this.init);

        if (trueBodyToGlobal != null)
            this.truth = LinAlg.matrixToXyzrpy(trueBodyToGlobal);
    }

    public double[][] getMatrix()
    {
        return LinAlg.xyzrpyToMatrix(this.state);
    }

    public int getDOF()
    {
        return init.length;
    }

    public double[] toXyzRpy(double s[])
    {
        return LinAlg.copy(s);
    }

    public GExtrinsicsNode copy()
    {
        GExtrinsicsNode node = new GExtrinsicsNode();
        node.init = LinAlg.copy(this.init);
        node.state = LinAlg.copy(this.state);
        if (this.truth != null)
            node.truth = LinAlg.copy(this.truth);
        node.attributes = Attributes.copy(this.attributes);

        return node;
    }
}
