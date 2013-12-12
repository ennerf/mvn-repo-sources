package april.camera;

import java.awt.*;
import java.util.*;
import javax.swing.*;

import april.jmat.*;
import april.vis.*;

public class StereoRectification
{
    // inputs
    View cal_A;
    View cal_B;
    double  G2C_A[][];
    double  G2C_B[][];

    // internal
    double  K[][];
    double  G2C_A_new[][];
    double  G2C_B_new[][];
    double  R_N2O_A[][];
    double  R_N2O_B[][];

    // outputs
    StereoRectifiedView viewA;
    StereoRectifiedView viewB;

    private StereoRectification(View cal_A, View cal_B,
                                double[][] GlobalToCam_A, double[][] GlobalToCam_B)
    {
        this.cal_A = cal_A;
        this.cal_B = cal_B;

        this.G2C_A = GlobalToCam_A;
        this.G2C_B = GlobalToCam_B;
    }

    public static StereoRectification getMaxGrownInscribedSR(View cal_A, View cal_B,
                                                             double[][] GlobalToCam_A, double[][] GlobalToCam_B)
    {
        StereoRectification sr = new StereoRectification(cal_A, cal_B, GlobalToCam_A, GlobalToCam_B);
        sr.computeTransformations();
        sr.createMaxGrownInscribedSRViews();

        return sr;
    }

    public static StereoRectification getMaxInscribedSR(View cal_A, View cal_B,
                                                        double[][] GlobalToCam_A, double[][] GlobalToCam_B)
    {
        StereoRectification sr = new StereoRectification(cal_A, cal_B, GlobalToCam_A, GlobalToCam_B);
        sr.computeTransformations();
        sr.createMaxInscribedSRViews();

        return sr;
    }

    public static StereoRectification getMaxSR(View cal_A, View cal_B,
                                               double[][] GlobalToCam_A, double[][] GlobalToCam_B)
    {
        StereoRectification sr = new StereoRectification(cal_A, cal_B, GlobalToCam_A, GlobalToCam_B);
        sr.computeTransformations();
        sr.createMaxSRViews();

        return sr;
    }

    void computeTransformations()
    {
        ////////////////////////////////////////////////////////////////////////////////
        // New intrinsics
        double K_A[][] = cal_A.copyIntrinsics();
        double K_B[][] = cal_B.copyIntrinsics();

        K = LinAlg.scale(LinAlg.add(K_A, K_B), 0.5);
        K[0][1] = 0; // no skew

        ////////////////////////////////////////////////////////////////////////////////
        // Compute rotation
        double C2G_A[][] = LinAlg.inverse(G2C_A);
        double C2G_B[][] = LinAlg.inverse(G2C_B);

        double c1[] = LinAlg.transform(C2G_A, new double[] { 0, 0, 0 });
        double c2[] = LinAlg.transform(C2G_B, new double[] { 0, 0, 0 });

        double vx[] = LinAlg.normalize(LinAlg.subtract(c2, c1));
        // vy might not be right. it isn't well tested, as the left camera is usually at the origin
        double vy[] = LinAlg.normalize(LinAlg.crossProduct(new double[] {C2G_A[0][2], C2G_A[1][2], C2G_A[2][2]},
                                                           vx));
        double vz[] = LinAlg.normalize(LinAlg.crossProduct(vx, vy));

        double R_N2O_A[][] = new double[][] { { vx[0], vy[0], vz[0], 0 } ,
                                              { vx[1], vy[1], vz[1], 0 } ,
                                              { vx[2], vy[2], vz[2], 0 } ,
                                              {     0,     0,     0, 1 } };

        ////////////////////////////////////////////////////////////////////////////////
        // New extrinsics
        double C2G_A_rot[][] = new double[][] { { C2G_A[0][0], C2G_A[0][1], C2G_A[0][2], 0 },
                                                { C2G_A[1][0], C2G_A[1][1], C2G_A[1][2], 0 },
                                                { C2G_A[2][0], C2G_A[2][1], C2G_A[2][2], 0 },
                                                {           0,           0,           0, 1 } };

        double C2G_B_rot[][] = new double[][] { { C2G_B[0][0], C2G_B[0][1], C2G_B[0][2], 0 },
                                                { C2G_B[1][0], C2G_B[1][1], C2G_B[1][2], 0 },
                                                { C2G_B[2][0], C2G_B[2][1], C2G_B[2][2], 0 },
                                                {           0,           0,           0, 1 } };

        double C2G_A_new[][] = LinAlg.multiplyMany(C2G_A,
                                                   R_N2O_A);

        double C2G_B_new[][] = LinAlg.multiplyMany(C2G_B,
                                                   LinAlg.inverse(C2G_B_rot),
                                                   C2G_A_rot,
                                                   R_N2O_A);

        this.G2C_A_new = LinAlg.inverse(C2G_A_new);
        this.G2C_B_new = LinAlg.inverse(C2G_B_new);

        this.R_N2O_A = LinAlg.select(LinAlg.matrixAB(G2C_A,
                                                     C2G_A_new),
                                     0, 2, 0, 2);

        this.R_N2O_B = LinAlg.select(LinAlg.matrixAB(G2C_B,
                                                     C2G_B_new),
                                     0, 2, 0, 2);
    }

    public void showDebuggingGUI()
    {
        JFrame jf = new JFrame("Debug");
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setLayout(new BorderLayout());

        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);

        jf.add(vc, BorderLayout.CENTER);
        jf.setSize(800, 600);
        jf.setVisible(true);

        VisWorld.Buffer vb;

        vb = vw.getBuffer("initial axes");

        vb.addBack(new VisChain(LinAlg.inverse(G2C_A),
                                LinAlg.scale(0.05, 0.05, 0.05),
                                new VzAxes()));
        vb.addBack(new VisChain(LinAlg.inverse(G2C_B),
                                LinAlg.scale(0.05, 0.05, 0.05),
                                new VzAxes()));
        vb.swap();

        vb = vw.getBuffer("new axes");
        vb.addBack(new VisChain(LinAlg.inverse(G2C_A_new),
                                LinAlg.scale(0.05, 0.05, 0.05),
                                new VzAxes()));

        vb.addBack(new VisChain(LinAlg.inverse(G2C_B_new),
                                LinAlg.scale(0.05, 0.05, 0.05),
                                new VzAxes()));

        double c1[] = LinAlg.transform(LinAlg.inverse(G2C_A_new), new double[] { 0, 0, 0 });
        double c2[] = LinAlg.transform(LinAlg.inverse(G2C_B_new), new double[] { 0, 0, 0 });
        vb.addBack(new VzLines(new VisVertexData(new double[][] {c1, c2}),
                               VzLines.LINE_STRIP,
                               new VzLines.Style(Color.white, 1)));
        vb.swap();
    }

    public ArrayList<View> getViews()
    {
        assert(viewA != null);
        assert(viewB != null);

        ArrayList<View> views = new ArrayList<View>();
        views.add(viewA);
        views.add(viewB);
        return views;
    }

    public ArrayList<double[][]> getExtrinsicsL2C()
    {
        ArrayList<double[][]> extrinsics = new ArrayList<double[][]>();
        extrinsics.add(G2C_A_new);
        extrinsics.add(G2C_B_new);
        return extrinsics;
    }

    private void createMaxGrownInscribedSRViews()
    {
        assert(cal_A != null);
        assert(cal_B != null);
        assert(R_N2O_A != null);
        assert(R_N2O_B != null);
        assert(K != null);
        double XY01_A[][] = computeMaxGrownInscribedRectifiedRectangle(cal_A, R_N2O_A, K);
        double XY01_B[][] = computeMaxGrownInscribedRectifiedRectangle(cal_B, R_N2O_B, K);

        // make sure that the Y offsets are shared. this ensures that the y
        // indices into the image match
        XY01_A[0][1] = Math.max(XY01_A[0][1], XY01_B[0][1]);
        XY01_A[1][1] = Math.min(XY01_A[1][1], XY01_B[1][1]);
        XY01_B[0][1] = XY01_A[0][1];
        XY01_B[1][1] = XY01_A[1][1];

        viewA = new StereoRectifiedView(K, XY01_A);
        viewB = new StereoRectifiedView(K, XY01_B);
        assert(viewA.getHeight() == viewB.getHeight());
    }

    private static double[][] computeMaxGrownInscribedRectifiedRectangle(View cal,
                                                                         double R_N2O[][], double K[][])
    {
        assert(cal != null);
        assert(R_N2O != null);
        assert(K != null);
        double T[][] = LinAlg.matrixAB(K,
                                       LinAlg.inverse(R_N2O));

        double Rb, Rt, Rl, Rr;
        int x_dp, y_dp;

        DistortionFunctionVerifier verifier = new DistortionFunctionVerifier(cal);

        ////////////////////////////////////////
        // compute rectified border
        ArrayList<double[]> border = new ArrayList<double[]>();

        // TL -> TR
        y_dp = 0;
        for (x_dp = 0; x_dp < cal.getWidth(); x_dp++)
        {
            double xy_dp[] = new double[] { x_dp, y_dp };
            xy_dp = verifier.clampPixels(xy_dp);
            double xyz_r[] = cal.pixelsToRay(xy_dp);
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            border.add(xy_rp);
        }

        // TR -> BR
        x_dp = cal.getWidth()-1;
        for (y_dp = 0; y_dp < cal.getHeight(); y_dp++)
        {
            double xy_dp[] = new double[] { x_dp, y_dp };
            xy_dp = verifier.clampPixels(xy_dp);
            double xyz_r[] = cal.pixelsToRay(xy_dp);
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            border.add(xy_rp);
        }

        // BR -> BL
        y_dp = cal.getHeight()-1;
        for (x_dp = cal.getWidth()-1; x_dp >= 0; x_dp--)
        {
            double xy_dp[] = new double[] { x_dp, y_dp };
            xy_dp = verifier.clampPixels(xy_dp);
            double xyz_r[] = cal.pixelsToRay(xy_dp);
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            border.add(xy_rp);
        }

        // BL -> TL
        x_dp = 0;
        for (y_dp = cal.getHeight()-1; y_dp >= 0; y_dp--)
        {
            double xy_dp[] = new double[] { x_dp, y_dp };
            xy_dp = verifier.clampPixels(xy_dp);
            double xyz_r[] = cal.pixelsToRay(xy_dp);
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            border.add(xy_rp);
        }

        ////////////////////////////////////////
        // grow inscribed rectangle

        double centroid[] = new double[2];
        for (double xy[] : border) {
            centroid[0] += xy[0]/border.size();
            centroid[1] += xy[1]/border.size();
        }

        // corner case
        int xmin = (int) centroid[0];
        int xmax = (int) centroid[0];
        int ymin = (int) centroid[1];
        int ymax = (int) centroid[1];

        boolean changed = true;
        while (changed)
        {
            changed = false;

            if (acceptMove(border, xmin-1, xmax, ymin, ymax)) {
                changed = true;
                xmin--;
            }

            if (acceptMove(border, xmin, xmax+1, ymin, ymax)) {
                changed = true;
                xmax++;
            }

            if (acceptMove(border, xmin, xmax, ymin-1, ymax)) {
                changed = true;
                ymin--;
            }

            if (acceptMove(border, xmin, xmax, ymin, ymax+1)) {
                changed = true;
                ymax++;
            }

            double area = (xmax - xmin)*(ymax - ymin);
            if (area > 10*cal.getWidth()*cal.getHeight()) { // XXX
                break;
            }
        }

        Rb = ymin;
        Rt = ymax;
        Rl = xmin;
        Rr = xmax;

        System.out.printf("Bottom: %5.1f Right: %5.1f Top: %5.1f Left: %5.1f\n", Rb, Rr, Rt, Rl);

        return new double[][] { { Rl, Rb }, { Rr, Rt } };
    }

    private static boolean acceptMove(ArrayList<double[]> border,
                                      double xmin, double xmax,
                                      double ymin, double ymax)
    {
        for (double xy[] : border) {
            if (xy[0] > xmin && xy[0] < xmax && xy[1] > ymin && xy[1] < ymax)
                return false;
        }

        return true;
    }

    private void createMaxInscribedSRViews()
    {
        assert(cal_A != null);
        assert(cal_B != null);
        assert(R_N2O_A != null);
        assert(R_N2O_B != null);
        assert(K != null);
        double XY01_A[][] = computeMaxInscribedRectifiedRectangle(cal_A, R_N2O_A, K);
        double XY01_B[][] = computeMaxInscribedRectifiedRectangle(cal_B, R_N2O_B, K);

        if ((XY01_A[0][1] >= XY01_A[1][1]) || (XY01_A[0][0] >= XY01_A[1][0]) ||
            (XY01_B[0][1] >= XY01_B[1][1]) || (XY01_B[0][0] >= XY01_B[1][0]))
        {
            System.err.println("Error: image rotation appears too severe for a 'max inscribed rectangle'. Try a 'max rectangle'");
            assert(false);
        }

        // make sure that the Y offsets are shared. this ensures that the y
        // indices into the image match
        XY01_A[0][1] = Math.max(XY01_A[0][1], XY01_B[0][1]);
        XY01_A[1][1] = Math.min(XY01_A[1][1], XY01_B[1][1]);
        XY01_B[0][1] = XY01_A[0][1];
        XY01_B[1][1] = XY01_A[1][1];

        viewA = new StereoRectifiedView(K, XY01_A);
        viewB = new StereoRectifiedView(K, XY01_B);
        assert(viewA.getHeight() == viewB.getHeight());
    }

    private static double[][] computeMaxInscribedRectifiedRectangle(View cal, double R_N2O[][], double K[][])
    {
        assert(cal != null);
        assert(R_N2O != null);
        assert(K != null);
        double T[][] = LinAlg.matrixAB(K,
                                       LinAlg.inverse(R_N2O));

        double Rb, Rt, Rl, Rr;
        int x_dp, y_dp;

        DistortionFunctionVerifier verifier = new DistortionFunctionVerifier(cal);

        // initialize bounds
        {
            double xyz_r[];
            double xy_rp[];

            xyz_r = cal.pixelsToRay(verifier.clampPixels(new double[] {                0,                 0}));
            xy_rp = CameraMath.pinholeTransform(T, xyz_r);
            Rb = xy_rp[1];

            xyz_r = cal.pixelsToRay(verifier.clampPixels(new double[] { cal.getWidth()-1,                 0}));
            xy_rp = CameraMath.pinholeTransform(T, xyz_r);
            Rr = xy_rp[0];

            xyz_r = cal.pixelsToRay(verifier.clampPixels(new double[] { cal.getWidth()-1, cal.getHeight()-1}));
            xy_rp = CameraMath.pinholeTransform(T, xyz_r);
            Rt = xy_rp[1];

            xyz_r = cal.pixelsToRay(verifier.clampPixels(new double[] {                0, cal.getHeight()-1}));
            xy_rp = CameraMath.pinholeTransform(T, xyz_r);
            Rl = xy_rp[0];
        }

        // TL -> TR
        y_dp = 0;
        for (x_dp = 0; x_dp < cal.getWidth(); x_dp++) {

            double xyz_r[] = cal.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp }));
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            Rb = Math.max(Rb, xy_rp[1]);
        }

        // TR -> BR
        x_dp = cal.getWidth()-1;
        for (y_dp = 0; y_dp < cal.getHeight(); y_dp++) {

            double xyz_r[] = cal.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp }));
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            Rr = Math.min(Rr, xy_rp[0]);
        }

        // BR -> BL
        y_dp = cal.getHeight()-1;
        for (x_dp = cal.getWidth()-1; x_dp >= 0; x_dp--) {

            double xyz_r[] = cal.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp }));
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            Rt = Math.min(Rt, xy_rp[1]);
        }

        // BL -> TL
        x_dp = 0;
        for (y_dp = cal.getHeight()-1; y_dp >= 0; y_dp--) {

            double xyz_r[] = cal.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp }));
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            Rl = Math.max(Rl, xy_rp[0]);
        }

        System.out.printf("Bottom: %5.1f Right: %5.1f Top: %5.1f Left: %5.1f\n", Rb, Rr, Rt, Rl);

        return new double[][] { { Rl, Rb }, { Rr, Rt } };
    }

    private void createMaxSRViews()
    {
        assert(cal_A != null);
        assert(cal_B != null);
        assert(R_N2O_A != null);
        assert(R_N2O_B != null);
        assert(K != null);
        double XY01_A[][] = computeMaxRectifiedRectangle(cal_A, R_N2O_A, K);
        double XY01_B[][] = computeMaxRectifiedRectangle(cal_B, R_N2O_B, K);

        // make sure that the Y offsets are shared. this ensures that the y
        // indices into the image match
        XY01_A[0][1] = Math.min(XY01_A[0][1], XY01_B[0][1]);
        XY01_A[1][1] = Math.max(XY01_A[1][1], XY01_B[1][1]);
        XY01_B[0][1] = XY01_A[0][1];
        XY01_B[1][1] = XY01_A[1][1];

        viewA = new StereoRectifiedView(K, XY01_A);
        viewB = new StereoRectifiedView(K, XY01_B);
        assert(viewA.getHeight() == viewB.getHeight());
    }

    private static double[][] computeMaxRectifiedRectangle(View cal, double R_N2O[][], double K[][])
    {
        assert(cal != null);
        assert(R_N2O != null);
        assert(K != null);
        double T[][] = LinAlg.matrixAB(K,
                                       LinAlg.inverse(R_N2O));

        double Rb, Rt, Rl, Rr;
        int x_dp, y_dp;

        DistortionFunctionVerifier verifier = new DistortionFunctionVerifier(cal);

        // initialize bounds
        {
            double xyz_r[];
            double xy_rp[];

            xyz_r = cal.pixelsToRay(verifier.clampPixels(new double[] {                0,                 0}));
            xy_rp = CameraMath.pinholeTransform(T, xyz_r);
            Rb = xy_rp[1];

            xyz_r = cal.pixelsToRay(verifier.clampPixels(new double[] { cal.getWidth()-1,                 0}));
            xy_rp = CameraMath.pinholeTransform(T, xyz_r);
            Rr = xy_rp[0];

            xyz_r = cal.pixelsToRay(verifier.clampPixels(new double[] { cal.getWidth()-1, cal.getHeight()-1}));
            xy_rp = CameraMath.pinholeTransform(T, xyz_r);
            Rt = xy_rp[1];

            xyz_r = cal.pixelsToRay(verifier.clampPixels(new double[] {                0, cal.getHeight()-1}));
            xy_rp = CameraMath.pinholeTransform(T, xyz_r);
            Rl = xy_rp[0];
        }

        // TL -> TR
        y_dp = 0;
        for (x_dp = 0; x_dp < cal.getWidth(); x_dp++) {

            double xyz_r[] = cal.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp }));
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            Rb = Math.min(Rb, xy_rp[1]);
        }

        // TR -> BR
        x_dp = cal.getWidth()-1;
        for (y_dp = 0; y_dp < cal.getHeight(); y_dp++) {

            double xyz_r[] = cal.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp }));
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            Rr = Math.max(Rr, xy_rp[0]);
        }

        // BR -> BL
        y_dp = cal.getHeight()-1;
        for (x_dp = cal.getWidth()-1; x_dp >= 0; x_dp--) {

            double xyz_r[] = cal.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp }));
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            Rt = Math.max(Rt, xy_rp[1]);
        }

        // BL -> TL
        x_dp = 0;
        for (y_dp = cal.getHeight()-1; y_dp >= 0; y_dp--) {

            double xyz_r[] = cal.pixelsToRay(verifier.clampPixels(new double[] { x_dp, y_dp }));
            double xy_rp[] = CameraMath.pinholeTransform(T, xyz_r);
            Rl = Math.min(Rl, xy_rp[0]);
        }

        System.out.printf("Bottom: %5.1f Right: %5.1f Top: %5.1f Left: %5.1f\n", Rb, Rr, Rt, Rl);

        return new double[][] { { Rl, Rb }, { Rr, Rt } };
    }
}

