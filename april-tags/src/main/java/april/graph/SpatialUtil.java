package april.graph;

import java.util.*;

/** Utilities for graphs that are spatial in nature (i.e., maps) **/
public class SpatialUtil
{
    /** Returns { (xmin, ymin, zmin), (xmax, ymax, zmax) } **/
    public static ArrayList<double[]> getBounds(Graph g)
    {
        ArrayList<double[]> bounds = new ArrayList<double[]>();

        double xmin = Double.MAX_VALUE, xmax = -Double.MAX_VALUE;
        double ymin = Double.MAX_VALUE, ymax = -Double.MAX_VALUE;
        double zmin = Double.MAX_VALUE, zmax = -Double.MAX_VALUE;

        for (GNode _gn : g.nodes) {
            if (_gn instanceof SpatialNode) {
                SpatialNode gn = (SpatialNode) _gn;

                double xytrpy[] = gn.toXyzRpy();

                xmin = Math.min(xmin, xytrpy[0]);
                ymin = Math.min(ymin, xytrpy[1]);
                zmin = Math.min(zmin, xytrpy[2]);

                xmax = Math.max(xmax, xytrpy[0]);
                ymax = Math.max(ymax, xytrpy[1]);
                zmax = Math.max(zmax, xytrpy[2]);
            }
        }

        bounds.add(new double[] {xmin, ymin, zmin});
        bounds.add(new double[] {xmax, ymax, zmax});

        return bounds;
    }


}
