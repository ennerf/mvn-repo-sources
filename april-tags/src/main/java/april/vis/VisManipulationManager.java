package april.vis;

import april.jmat.geom.*;

public interface VisManipulationManager
{
    public double[] pickManipulationPoint(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray);
}
