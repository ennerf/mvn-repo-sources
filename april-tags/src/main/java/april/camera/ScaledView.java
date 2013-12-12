package april.camera;

import java.util.*;

import april.jmat.*;

public class ScaledView implements View
{
    View view;

    double scale;

    double[][] S;
    double[][] Sinv;

    int width;
    int height;

    public ScaledView(double scale, View view)
    {
        this.view = view;
        this.scale = scale;

        double offs = (1.0 - scale) / 2;

        S = new double[][] { { scale,     0, -offs } ,
                             {     0, scale, -offs } ,
                             {     0,     0,     1 } };
        Sinv = LinAlg.inverse(S);

        width  = (int) Math.floor(view.getWidth() * scale);
        height = (int) Math.floor(view.getHeight() * scale);
    }

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
        return LinAlg.matrixAB(S, view.copyIntrinsics());
    }

    public double[] rayToPixels(double xyz_r[])
    {
        return CameraMath.pinholeTransform(S, view.rayToPixels(xyz_r));
    }

    public double[] pixelsToRay(double xy_p[])
    {
        return view.pixelsToRay(CameraMath.pinholeTransform(Sinv, xy_p));
    }
}
