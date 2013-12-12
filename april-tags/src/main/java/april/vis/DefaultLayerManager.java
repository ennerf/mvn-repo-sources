package april.vis;

import java.util.*;
import java.io.*;

import april.jmat.*;

/** Layer positions are given in Java window coordinates, i.e., with
  * 0,0 in the bottom left corner.
  **/
public class DefaultLayerManager implements VisLayerManager, VisSerializable
{
    HashMap<VisLayer, LayerPosition> layerPositions = new HashMap<VisLayer, LayerPosition>();

    // default constructor
    public DefaultLayerManager()
    {
    }

    public DefaultLayerManager(VisLayer vl, double pos[])
    {
        LayerPosition lpos = new LayerPosition();
        lpos.dpos1 = LinAlg.copy(pos);

        layerPositions.put(vl, lpos);
    }

    static class LayerPosition implements VisSerializable
    {
        // The position of a layer is determined by four numbers:
        //
        // 0: the left most coordinate as a fraction of the viewport width
        // 1: the bottom most coordinate as a fraction of the viewport height
        // 2: the width as a fraction of the viewport width
        // 3: the height as a fraction of the viewport height

        double dpos0[];
        long mtime0;
        double dpos1[] = new double[] { 0, 0, 1, 1};
        long mtime1 = System.currentTimeMillis();

        LayerPosition()
        {
        }

        double[] getPosition(long mtime)
        {
            if (dpos0 == null || mtime >= mtime1)
                return dpos1;

            if (mtime <= mtime0 && dpos0 != null)
                return dpos0;

            double alpha1 = ((double) mtime - mtime0) / (mtime1 - mtime0);
            double alpha0 = 1.0 - alpha1;

            return new double[] { alpha0 * dpos0[0] + alpha1 * dpos1[0],
                                  alpha0 * dpos0[1] + alpha1 * dpos1[1],
                                  alpha0 * dpos0[2] + alpha1 * dpos1[2],
                                  alpha0 * dpos0[3] + alpha1 * dpos1[3] };
        }

        public LayerPosition(ObjectReader r)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeDoubles(dpos0);
            outs.writeLong(mtime0);
            outs.writeDoubles(dpos1);
            outs.writeLong(mtime1);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            dpos0 = ins.readDoubles();
            mtime0 = ins.readLong();
            dpos1 = ins.readDoubles();
            mtime1 = ins.readLong();
        }
    }

    public int[] getLayerPosition(VisCanvas vc, int viewport[], VisLayer vl, long mtime)
    {
        LayerPosition layerPosition = layerPositions.get(vl);
        if (layerPosition == null) {
            layerPosition = new LayerPosition();
            layerPositions.put(vl, layerPosition);
            // XXX memory leak if many layers are created and discarded.
        }

        double dpos[] = layerPosition.getPosition(mtime);

        return new int[] { (int) Math.round(viewport[2]*dpos[0]),
                           (int) Math.round(viewport[3]*dpos[1]),
                           (int) Math.round(viewport[2]*dpos[2]),
                           (int) Math.round(viewport[3]*dpos[3]) };
    }

    public DefaultLayerManager(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeInt(layerPositions.size());

        for (VisLayer vl : layerPositions.keySet()) {
            outs.writeObject(vl);
            outs.writeObject(layerPositions.get(vl));
        }
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        int n = ins.readInt();

        for (int i = 0; i < n; i++) {
            VisLayer vl = (VisLayer) ins.readObject();
            LayerPosition pos = (LayerPosition) ins.readObject();
            layerPositions.put(vl, pos);
        }
    }
}
