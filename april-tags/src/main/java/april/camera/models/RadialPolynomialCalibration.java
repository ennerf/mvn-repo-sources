package april.camera.models;

import april.camera.*;
import april.config.*;
import april.jmat.*;
import april.util.*;

public class RadialPolynomialCalibration implements Calibration, ParameterizableCalibration
{
    // constants for iteratively rectifying coordinates (e.g. max allowed error)
    private static final int max_iterations = 20;
    private static final double max_pixel_error = 0.01;
    private double max_sqerr;

    // required calibration parameter lengths
    static final public int LENGTH_FC = 2;
    static final public int LENGTH_CC = 2;
    public int LENGTH_KC;

    // Focal length, in pixels
    private double[]        fc;

    // Camera center
    private double[]        cc;

    // Distortion
    private double[]        kc;

    // Intrinsics matrix
    private double[][]      K;
    private double[][]      Kinv;

    // Other
    private int             width;
    private int             height;

    public RadialPolynomialCalibration(double fc[], double cc[], double kc[],
                                    int width, int height)
    {
        this.fc     = LinAlg.copy(fc);
        this.cc     = LinAlg.copy(cc);
        this.kc     = LinAlg.copy(kc);
        this.LENGTH_KC = this.kc.length;

        this.width  = width;
        this.height = height;

        createIntrinsicsMatrix();
    }

    public RadialPolynomialCalibration(Config config)
    {
        this.fc     = config.requireDoubles("intrinsics.fc");
        this.cc     = config.requireDoubles("intrinsics.cc");
        this.kc     = config.requireDoubles("intrinsics.kc");
        this.LENGTH_KC = this.kc.length;

        this.width  = config.requireInt("width");
        this.height = config.requireInt("height");

        createIntrinsicsMatrix();
    }

    public RadialPolynomialCalibration(int kclength, double params[], int width, int height)
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

        K = new double[][] { { fc[0],   0.0, cc[0] } ,
                             {   0.0, fc[1], cc[1] } ,
                             {   0.0,   0.0,   1.0 } };
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

        s = String.format("%s        }\n", s);

        return s;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Parameterizable interface methods
    public double[] getParameterization()
    {
        int len = LENGTH_FC + LENGTH_CC + LENGTH_KC;

        double params[] = new double[len];

        params[0] = fc[0];
        params[1] = fc[1];

        params[2] = cc[0];
        params[3] = cc[1];

        for (int i = 0; i < LENGTH_KC; i++)
            params[4+i] = kc[i];

        return params;
    }

    public void resetParameterization(double params[])
    {
        assert(params.length == (LENGTH_FC + LENGTH_CC + LENGTH_KC));

        fc = new double[LENGTH_FC];
        fc[0] = params[0];
        fc[1] = params[1];

        cc = new double[LENGTH_CC];
        cc[0] = params[2];
        cc[1] = params[3];

        kc = new double[LENGTH_KC];
        for (int i = 0; i < LENGTH_KC; i++)
            kc[i] = params[4+i];

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

        double multiplier = 1;

        double rpow = 1;
        for (int i = 0; i < LENGTH_KC; i++) {
            rpow *= r2;
            multiplier += kc[i]*rpow;
        }

        double xy_dn[] = new double[] { x*multiplier ,
                                        y*multiplier };
        return xy_dn;
    }

    // Perform iterative rectification and return a ray
    private double[] rectifyToRay(double xy_dn[])
    {
        double x_rn = xy_dn[0];
        double y_rn = xy_dn[1];

        for (int i=0; i < max_iterations; i++) {

            double r2 = x_rn*x_rn + y_rn*y_rn;

            double multiplier = 1;
            double rpow = 1;
            for (int j = 0; j < LENGTH_KC; j++) {
                rpow *= r2;
                multiplier += kc[j]*rpow;
            }

            double x_sqerr = xy_dn[0] - (x_rn*multiplier);
            double y_sqerr = xy_dn[1] - (y_rn*multiplier);
            double sqerr = x_sqerr*x_sqerr + y_sqerr*y_sqerr;

            x_rn = (xy_dn[0]) / multiplier;
            y_rn = (xy_dn[1]) / multiplier;

            if (sqerr < this.max_sqerr)
                break;
        }

        return new double[] { x_rn, y_rn, 1 };
    }
}
