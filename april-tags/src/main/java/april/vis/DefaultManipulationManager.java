package april.vis;

import java.io.*;

import april.jmat.geom.*;

/** Picks a manipulation point based on the intersection of the ray
 * with the XY plane (by default at z=0) **/
public class DefaultManipulationManager implements VisManipulationManager, VisSerializable
{
    public double z = 0;

    public DefaultManipulationManager()
    {
    }

    public double[] pickManipulationPoint(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray)
    {
        return ray.intersectPlaneXY(z);
    }

    public DefaultManipulationManager(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeDouble(z);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        z = ins.readDouble();
    }
}
