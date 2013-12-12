package april.camera.models;

import april.camera.*;
import april.config.*;
import april.jmat.*;
import april.util.*;

/** The standard radial+tangential model as used in OpenCV and Caltech's MATLAB toolbox.
  * Note: The radial and tangential terms have been split for API convenience and do not
  * would require reordering for use with Caltech's toolbox. For example, if kclength==3,
  * use the following for Caltech { kc[0], kc[1], lc[0], lc[1], kc[2] }
  */
public class CaltechCalibration implements Calibration, ParameterizableCalibration
{
    // constants for iteratively rectifying coordinates (e.g. max allowed error)
    private static final int max_iterations = 20;
    private static final double max_pixel_error = 0.01;
    private double max_sqerr;

    // required calibration parameter lengths
    static final public int LENGTH_FC = 2;
    static final public int LENGTH_CC = 2;
    public int LENGTH_KC; // radial
    static final public int LENGTH_LC = 2; // tangential

    // Focal length, in pixels
    private double[]        fc;

    // Camera center
    private double[]        cc;

    // Distortion
    private double[]        kc;
    private double[]        lc;

    // Skew
    private double          skew;

    // Intrinsics matrix
    private double[][]      K;
    private double[][]      Kinv;

    // Other
    private int             width;
    private int             height;

    public CaltechCalibration(double fc[], double cc[], double kc[], double lc[], double skew,
                              int width, int height)
    {
        this.fc     = LinAlg.copy(fc);
        this.cc     = LinAlg.copy(cc);
        this.kc     = LinAlg.copy(kc);
        this.lc     = LinAlg.copy(lc);
        this.skew   = skew;

        this.LENGTH_KC = this.kc.length;

        this.width  = width;
        this.height = height;

        createIntrinsicsMatrix();
    }

    public CaltechCalibration(Config config)
    {
        this.fc     = config.requireDoubles("intrinsics.fc");
        this.cc     = config.requireDoubles("intrinsics.cc");
        this.kc     = config.requireDoubles("intrinsics.kc");
        this.lc     = config.requireDoubles("intrinsics.lc");
        this.skew   = config.requireDouble("intrinsics.skew");

        this.LENGTH_KC = this.kc.length;

        this.width  = config.requireInt("width");
        this.height = config.requireInt("height");

        createIntrinsicsMatrix();
    }

    public CaltechCalibration(int kclength, double params[], int width, int height)
    {
        this.width = width;
        this.height = height;
        this.LENGTH_KC = kclength;

        resetParameterization(params);
    }

    private void createIntrinsicsMatrix()
    {
        assert(fc.length == LENGTH_FC);
        assert(cc.length == LENGTH_CC);
        assert(kc.length == LENGTH_KC);
        assert(lc.length == LENGTH_LC);

        K = new double[][] { { fc[0],  skew*fc[0], cc[0] } ,
                             {   0.0,       fc[1], cc[1] } ,
                             {   0.0,         0.0,   1.0 } };
        Kinv = LinAlg.inverse(K);

        // compute the max square error for iterative rectification in normalized units
        max_sqerr = Math.pow(max_pixel_error / Math.max(fc[0], fc[1]), 2);
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

        s = String.format("%s            kc = [",s);
        for (int i = 0; i < LENGTH_KC; i++)
            s = String.format("%s%11.6f%s", s, kc[i], (i+1 < LENGTH_KC) ? "," : " ];\n");

        s = String.format("%s            lc = [%11.6f,%11.6f ];\n",
                          s, lc[0], lc[1]);

        s = String.format("%s            skew = %11.6f;\n", s, skew);
        s = String.format("%s        }\n", s);

        return s;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Parameterizable interface methods
    public double[] getParameterization()
    {
        int len = LENGTH_FC + LENGTH_CC + LENGTH_KC + LENGTH_LC + 1;

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
        base += LENGTH_LC;

        params[base+0] = skew;

        return params;
    }

    public void resetParameterization(double params[])
    {
        assert(params.length == (LENGTH_FC + LENGTH_CC + LENGTH_KC + LENGTH_LC + 1));
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
        for (int i = 0; i < LENGTH_KC; i++)
            kc[i] = params[base+i];
        base += LENGTH_KC;

        lc = new double[LENGTH_LC];
        lc[0] = params[base+0];
        lc[1] = params[base+1];
        base += LENGTH_LC;

        skew = params[base+0];

        createIntrinsicsMatrix();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Private methods

    // Distort a ray
    private double[] distortRay(double xyz_r[])
    {
        double x = xyz_r[0];
        double y = xyz_r[1];
        double z = xyz_r[2];
        // pinhole assumption
        x = x / z;
        y = y / z;

        double r2 = x*x + y*y;

        double rpow = 1;
        double multiplier = 1;

        for (int i = 0; i < LENGTH_KC; i++) {
            rpow *= r2;
            multiplier += kc[i]*rpow;
        }

        double dx[] = new double[] {2*lc[0]*x*y + lc[1]*(r2 + 2*x*x),
                                    lc[0]*(r2 + 2*y*y) + 2*lc[1]*x*y};

        double xy_dn[] = new double[] { x*multiplier + dx[0] ,
                                        y*multiplier + dx[1] };
        return xy_dn;
    }

    // Perform iterative rectification and return a ray
    private double[] rectifyToRay(double xy_dn[])
    {
        double x_rn = xy_dn[0];
        double y_rn = xy_dn[1];

        for (int i=0; i < max_iterations; i++) {
            double r2 = x_rn*x_rn + y_rn*y_rn;

            double rpow = 1;
            double multiplier = 1;
            for (int j = 0; j < LENGTH_KC; j++) {
                rpow *= r2;
                multiplier += kc[j]*rpow;
            }

            double dx[] = new double[] {2*lc[0]*x_rn*y_rn + lc[1]*(r2 + 2*x_rn*x_rn),
                                        lc[0]*(r2 + 2*y_rn*y_rn) + 2*lc[1]*x_rn*y_rn};

            double x_sqerr = xy_dn[0] - (x_rn*multiplier + dx[0]);
            double y_sqerr = xy_dn[1] - (y_rn*multiplier + dx[1]);
            double sqerr = x_sqerr*x_sqerr + y_sqerr*y_sqerr;

            x_rn = (xy_dn[0] - dx[0]) / multiplier;
            y_rn = (xy_dn[1] - dx[1]) / multiplier;

            if (sqerr < this.max_sqerr)
                break;
        }

        return new double[] { x_rn, y_rn, 1 };
    }
}
