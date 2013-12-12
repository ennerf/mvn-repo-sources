package april.camera;

import java.util.*;

import april.jmat.*;

public class MaxRectifiedView implements View
{
    public static boolean verbose = false;

    double[][]  K;
    double[][]  Kinv;

    double      Rb, Rr, Rt, Rl;

    int         width;
    int         height;

    public MaxRectifiedView(View view)
    {
        computeMaxRectifiedRectangle(view);
    }

    private void computeMaxRectifiedRectangle(View view)
    {
        int x_dp, y_dp;

        K = view.copyIntrinsics();

        DistortionFunctionVerifier verifier = new DistortionFunctionVerifier(view);

        Rb = CameraMath.pinholeTransform(K, view.pixelsToRay(verifier.clampPixels(new double[] {                0,                  0})))[1];
        Rr = CameraMath.pinholeTransform(K, view.pixelsToRay(verifier.clampPixels(new double[] {view.getWidth()-1,                  0})))[0];
        Rt = CameraMath.pinholeTransform(K, view.pixelsToRay(verifier.clampPixels(new double[] {view.getWidth()-1, view.getHeight()-1})))[1];
        Rl = CameraMath.pinholeTransform(K, view.pixelsToRay(verifier.clampPixels(new double[] {                0, view.getHeight()-1})))[0];

        // TL -> TR
        y_dp = 0;
        for (x_dp = 0; x_dp < view.getWidth(); x_dp++) {

            double xy_rp[] = CameraMath.pinholeTransform(K, view.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp })));
            Rb = Math.min(Rb, xy_rp[1]);
            Rr = Math.max(Rr, xy_rp[0]);
            Rt = Math.max(Rt, xy_rp[1]);
            Rl = Math.min(Rl, xy_rp[0]);
        }

        // TR -> BR
        x_dp = view.getWidth()-1;
        for (y_dp = 0; y_dp < view.getHeight(); y_dp++) {

            double xy_rp[] = CameraMath.pinholeTransform(K, view.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp })));
            Rb = Math.min(Rb, xy_rp[1]);
            Rr = Math.max(Rr, xy_rp[0]);
            Rt = Math.max(Rt, xy_rp[1]);
            Rl = Math.min(Rl, xy_rp[0]);
        }

        // BR -> BL
        y_dp = view.getHeight()-1;
        for (x_dp = view.getWidth()-1; x_dp >= 0; x_dp--) {

            double xy_rp[] = CameraMath.pinholeTransform(K, view.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp })));
            Rb = Math.min(Rb, xy_rp[1]);
            Rr = Math.max(Rr, xy_rp[0]);
            Rt = Math.max(Rt, xy_rp[1]);
            Rl = Math.min(Rl, xy_rp[0]);
        }

        // BL -> TL
        x_dp = 0;
        for (y_dp = view.getHeight()-1; y_dp >= 0; y_dp--) {

            double xy_rp[] = CameraMath.pinholeTransform(K, view.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp })));
            Rb = Math.min(Rb, xy_rp[1]);
            Rr = Math.max(Rr, xy_rp[0]);
            Rt = Math.max(Rt, xy_rp[1]);
            Rl = Math.min(Rl, xy_rp[0]);
        }

        ////////////////////////////////////////
        // transformation matrix
        K[0][2] -= Rl;
        K[1][2] -= Rb;
        Kinv = LinAlg.inverse(K);

        width   = (int) Math.floor(Rr - Rl + 1);
        height  = (int) Math.floor(Rt - Rb + 1);

        if (verbose) System.out.printf("Bottom: %5.1f Right: %5.1f Top: %5.1f Left: %5.1f Width: %d Height: %d\n",
                                       Rb, Rr, Rt, Rl, width, height);

        if (verbose) LinAlg.print(K);
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

