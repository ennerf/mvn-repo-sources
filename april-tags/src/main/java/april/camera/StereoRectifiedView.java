package april.camera;

import java.util.*;

import april.jmat.*;

public class StereoRectifiedView implements View
{
    double[][]  K;
    double[][]  Kinv;

    double      Rb, Rr, Rt, Rl;

    int         width;
    int         height;

    public StereoRectifiedView(double _K[][], double XY01[][])
    {
        Rb = XY01[0][1];
        Rl = XY01[0][0];
        Rr = XY01[1][0];
        Rt = XY01[1][1];

        this.K      = LinAlg.copy(_K);
        this.K[0][2] -= Rl;
        this.K[1][2] -= Rb;
        this.Kinv   = LinAlg.inverse(this.K);

        width  = (int) Math.floor(Rr - Rl + 1);
        height = (int) Math.floor(Rt - Rb + 1);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Calibration interface methods

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    public double[][] copyIntrinsics()
    {
        return LinAlg.copy(K);
    }

    public double[] rayToPixels(double xyz_r[])
    {
        return CameraMath.pinholeTransform(K, xyz_r);
    }

    public double[] pixelsToRay(double xy_rp[])
    {
        return CameraMath.rayToPlane(CameraMath.pinholeTransform(Kinv, xy_rp));
    }
}
