package april.camera.models;

import april.camera.*;
import april.config.*;
import april.jmat.*;
import april.util.*;

public class KannalaBrandtCalibration implements Calibration, ParameterizableCalibration
{
    // required calibration parameter lengths
    static final public int LENGTH_FC = 2;
    static final public int LENGTH_CC = 2;

    public int LENGTH_KC;

    static final public int LENGTH_LC = 3;
    static final public int LENGTH_IC = 4;
    static final public int LENGTH_MC = 3;
    static final public int LENGTH_JC = 4;

    // Focal length, in pixels
    private double[]        fc;

    // Camera center
    private double[]        cc;

    // Distortion
    private double[]        kc;

    private double[]        lc;
    private double[]        ic;
    private double[]        mc;
    private double[]        jc;

    // Intrinsics matrix
    private double[][]      K;
    private double[][]      Kinv;

    // Other
    private int             width;
    private int             height;

    public KannalaBrandtCalibration(double fc[], double cc[], double kc[],
                                    double lc[], double ic[], double mc[], double jc[],
                                    int width, int height)
    {
        this.fc     = LinAlg.copy(fc);
        this.cc     = LinAlg.copy(cc);
        this.kc     = LinAlg.copy(kc);
        this.LENGTH_KC = this.kc.length;

        this.lc     = LinAlg.copy(lc);
        this.ic     = LinAlg.copy(ic);
        this.mc     = LinAlg.copy(mc);
        this.jc     = LinAlg.copy(jc);

        this.width  = width;
        this.height = height;

        createIntrinsicsMatrix();
    }

    public KannalaBrandtCalibration(Config config)
    {
        this.fc     = config.requireDoubles("intrinsics.fc");
        this.cc     = config.requireDoubles("intrinsics.cc");
        this.kc     = config.requireDoubles("intrinsics.kc");
        this.LENGTH_KC = this.kc.length;

        this.lc     = config.requireDoubles("intrinsics.lc");
        this.ic     = config.requireDoubles("intrinsics.ic");
        this.mc     = config.requireDoubles("intrinsics.mc");
        this.jc     = config.requireDoubles("intrinsics.jc");

        this.width  = config.requireInt("width");
        this.height = config.requireInt("height");

        createIntrinsicsMatrix();
    }

    public KannalaBrandtCalibration(int kclen, double params[], int width, int height)
    {
        this.width = width;
        this.height = height;
        this.LENGTH_KC = kclen;

        resetParameterization(params);
    }

    private void createIntrinsicsMatrix()
    {
        assert(fc.length == LENGTH_FC);
        assert(cc.length == LENGTH_CC);
        assert(kc.length == LENGTH_KC);

        assert(lc.length == LENGTH_LC);
        assert(ic.length == LENGTH_IC);
        assert(mc.length == LENGTH_MC);
        assert(jc.length == LENGTH_JC);

        K = new double[][] { { fc[0],   0.0, cc[0] } ,
                             {   0.0, fc[1], cc[1] } ,
                             {   0.0,   0.0,   1.0 } };
        Kinv = LinAlg.inverse(K);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Calibration interface methods

    /** Return image width from calibration.
      */
    public int getWidth()
    {
        return width;
    }

    /** Return image height from calibration.
      */
    public int getHeight()
    {
        return height;
    }

    /** Return intrinsics matrix from calibration.
      */
    public double[][] copyIntrinsics()
    {
        return LinAlg.copy(K);
    }

    /** Convert a 3D ray to pixel coordinates in this view,
      * applying distortion if appropriate.
      */
    public double[] rayToPixels(double xyz_r[])
    {
        double xy_dn[] = distortRay(xyz_r);
        return CameraMath.pinholeTransform(K, xy_dn);
    }

    /** Convert a 2D pixel coordinate in this view to a 3D ray,
      * removing distortion if appropriate.
      */
    public double[] pixelsToRay(double xy_dp[])
    {
        double xy_dn[] = CameraMath.pinholeTransform(Kinv, xy_dp);
        return rectifyToRay(xy_dn);
    }

    /** Return a string of all critical parameters for caching data based
      * on a calibration (e.g. lookup tables).
      */
    public String getCalibrationString()
    {
        String s;

        s = String.format(  "        class = \"%s\";\n\n", this.getClass().getName());
        s = String.format("%s        width = %d;\n", s, width);
        s = String.format("%s        height = %d;\n\n", s, height);
        s = String.format("%s        intrinsics {\n", s);
        s = String.format("%s            fc = [%11.6f,%11.6f ];\n", s, fc[0], fc[1]);
        s = String.format("%s            cc = [%11.6f,%11.6f ];\n", s, cc[0], cc[1]);

        s = String.format("%s            kc = [", s);
        for (int i = 0; i < LENGTH_KC; i++)
            s = String.format("%s%11.6f%s", s, kc[i], (i+1<LENGTH_KC) ? "," : " ];\n");

        s = String.format("%s            lc = [%11.6f,%11.6f,%11.6f ];\n",
                          s, lc[0], lc[1], lc[2]);
        s = String.format("%s            ic = [%11.6f,%11.6f,%11.6f,%11.6f ];\n",
                          s, ic[0], ic[1], ic[2], ic[3]);
        s = String.format("%s            mc = [%11.6f,%11.6f,%11.6f ];\n",
                          s, mc[0], mc[1], mc[2]);
        s = String.format("%s            jc = [%11.6f,%11.6f,%11.6f,%11.6f ];\n",
                          s, jc[0], jc[1], jc[2], jc[3]);

        s = String.format("%s        }\n", s);

        return s;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Parameterizable interface methods
    public double[] getParameterization()
    {
        int len = LENGTH_FC + LENGTH_CC + LENGTH_KC +
                  LENGTH_LC + LENGTH_IC + LENGTH_MC + LENGTH_JC;

        double params[] = new double[len];
        int base = 0;

        params[base+0] = fc[0];
        params[base+1] = fc[1];
        base += LENGTH_FC;

        params[base+0] = cc[0];
        params[base+1] = cc[1];
        base += LENGTH_CC;

        for (int i = 0; i < LENGTH_KC; i++)
            params[base+i] = kc[i];
        base += LENGTH_KC;

        params[base+0] = lc[0];
        params[base+1] = lc[1];
        params[base+2] = lc[2];
        base += LENGTH_LC;

        params[base+0] = ic[0];
        params[base+1] = ic[1];
        params[base+2] = ic[2];
        params[base+3] = ic[3];
        base += LENGTH_IC;

        params[base+0] = mc[0];
        params[base+1] = mc[1];
        params[base+2] = mc[2];
        base += LENGTH_MC;

        params[base+0] = jc[0];
        params[base+1] = jc[1];
        params[base+2] = jc[2];
        params[base+3] = jc[3];
        base += LENGTH_JC;

        return params;
    }

    public void resetParameterization(double params[])
    {
        assert(params.length == (LENGTH_FC + LENGTH_CC + LENGTH_KC +
                                 LENGTH_LC + LENGTH_IC + LENGTH_MC + LENGTH_JC));
        int base = 0;

        fc = new double[LENGTH_FC];
        fc[0] = params[base+0];
        fc[1] = params[base+1];
        base += LENGTH_FC;

        cc = new double[LENGTH_CC];
        cc[0] = params[base+0];
        cc[1] = params[base+1];
        base += LENGTH_CC;

        kc = new double[LENGTH_KC];
        kc[0] = params[base+0];
        kc[1] = params[base+1];
        kc[2] = params[base+2];
        kc[3] = params[base+3];
        base += LENGTH_KC;

        lc = new double[LENGTH_LC];
        lc[0] = params[base+0];
        lc[1] = params[base+1];
        lc[2] = params[base+2];
        base += LENGTH_LC;

        ic = new double[LENGTH_IC];
        ic[0] = params[base+0];
        ic[1] = params[base+1];
        ic[2] = params[base+2];
        ic[3] = params[base+3];
        base += LENGTH_IC;

        mc = new double[LENGTH_MC];
        mc[0] = params[base+0];
        mc[1] = params[base+1];
        mc[2] = params[base+2];
        base += LENGTH_MC;

        jc = new double[LENGTH_JC];
        jc[0] = params[base+0];
        jc[1] = params[base+1];
        jc[2] = params[base+2];
        jc[3] = params[base+3];
        base += LENGTH_JC;

        createIntrinsicsMatrix();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Private methods

    // Distort a ray
    private double[] distortRay(double xyz_r[])
    {
        assert(xyz_r.length == 3);

        double x = xyz_r[0];
        double y = xyz_r[1];
        double z = xyz_r[2];

        // the three sides of the triangle
        double O = Math.sqrt(x*x + y*y);
        double H = Math.sqrt(x*x + y*y + z*z);
        double A = z;

        double theta  = Math.asin(O/H);
        double theta2 = theta*theta;
        double theta3 = theta*theta2;
        double theta5 = theta3*theta2;

        double psi = Math.atan2(y, x);

        double thetapow = theta;
        double rtheta = theta;
        for (int i = 0; i < LENGTH_KC; i++) {
            thetapow *= theta2;
            rtheta += kc[i]*thetapow;
        }

        double ur[] = new double[] { Math.cos(psi), Math.sin(psi) };
        double upsi[] = new double[] { -ur[1], ur[0] };

        double xy_dn[] = new double[2];

        xy_dn[0] += ur[0] * rtheta;
        xy_dn[1] += ur[1] * rtheta;

        // radial distortion
        double deltar = (lc[0]*theta + lc[1]*theta3 + lc[2]*theta5) *
                        (ic[0]*Math.cos(  psi) + ic[1]*Math.sin(  psi) +
                         ic[2]*Math.cos(2*psi) + ic[3]*Math.sin(2*psi));
        xy_dn[0] += ur[0] * deltar;
        xy_dn[1] += ur[1] * deltar;

        // tangential distortion
        double deltat = (mc[0]*theta + mc[1]*theta3 + mc[2]*theta5) *
                        (jc[0]*Math.cos(  psi) + jc[1]*Math.sin(  psi) +
                         jc[2]*Math.cos(2*psi) + jc[3]*Math.sin(2*psi));
        xy_dn[0] += upsi[0] * deltat;
        xy_dn[1] += upsi[1] * deltat;

        return xy_dn;
    }

    // Perform iterative rectification and return a ray
    private double[] rectifyToRay(double xy_dn[])
    {
        System.out.println("Error: KannalaBrandtCalibration cannot be used for rectification until tested on fisheye lens");
        assert(false);
        return null;
    }
}

