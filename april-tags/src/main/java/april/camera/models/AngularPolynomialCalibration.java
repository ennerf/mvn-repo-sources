package april.camera.models;

import april.camera.*;
import april.config.*;
import april.jmat.*;
import april.util.*;

/** A simple radial distortion model parameterized in angle instead of
  * rectified image radius. Usually results in a better model in the image corners.
  */
public class AngularPolynomialCalibration implements Calibration, ParameterizableCalibration
{
    static final int MAX_ITERATIONS = 10;

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

    public AngularPolynomialCalibration(double fc[], double cc[], double kc[],
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

    public AngularPolynomialCalibration(Config config)
    {
        this.fc     = config.requireDoubles("intrinsics.fc");
        this.cc     = config.requireDoubles("intrinsics.cc");
        this.kc     = config.requireDoubles("intrinsics.kc");
        this.LENGTH_KC = this.kc.length;

        this.width  = config.requireInt("width");
        this.height = config.requireInt("height");

        createIntrinsicsMatrix();
    }

    public AngularPolynomialCalibration(int kclength, double params[], int width, int height)
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
        return CameraMath.rayToSphere(rectifyToRay(xy_dn));
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

        params[ 0] = fc[0];
        params[ 1] = fc[1];

        params[ 2] = cc[0];
        params[ 3] = cc[1];

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
    public double[] distortRay(double xyz_r[])
    {
        assert(xyz_r.length == 3);

        double x = xyz_r[0];
        double y = xyz_r[1];
        double z = xyz_r[2];

        double r = Math.sqrt(x*x + y*y);

        double theta  = Math.atan2(r, z);
        double theta2 = theta*theta;

        double psi = Math.atan2(y, x);

        // polynomial mapping function. not to be mistaken as r*theta
        double rtheta   = theta;
        double thetapow = theta;
        for (int i = 0; i < LENGTH_KC; i++) {
            thetapow *= theta2;
            rtheta += kc[i] * thetapow;
        }

        double ur[] = new double[] { Math.cos(psi), Math.sin(psi) };

        double xy_dn[] = new double[2];

        xy_dn[0] += ur[0] * rtheta;
        xy_dn[1] += ur[1] * rtheta;

        return xy_dn;
    }

    // Perform iterative rectification and return a ray
    public double[] rectifyToRay(double xy_dn[])
    {
        if (LinAlg.magnitude(xy_dn) < 1e-12)
            return new double[] { 0, 0, 1 };

        double psi = 0;
        if (Math.abs(xy_dn[1]) > 1e-9)
            psi = Math.atan2(xy_dn[1], xy_dn[0]);

        double xrtheta = (xy_dn[0]/Math.cos(psi));
        double yrtheta = (xy_dn[1]/Math.sin(psi));

        // avoid NaNs
        double rtheta = 0;
        if (Double.isNaN(xrtheta))      rtheta = yrtheta;
        else if (Double.isNaN(yrtheta)) rtheta = xrtheta;
        else                            rtheta = (xrtheta + yrtheta) / 2.0;

        // xy_dn is on the z==1 plane. estimate initial theta
        double theta = Math.atan2(Math.sqrt(xy_dn[0]*xy_dn[0] + xy_dn[1]*xy_dn[1]), 1);

        // gradient descent
        for (int i = 0; i < MAX_ITERATIONS; i++) {

            double eps = 0.01; // half a degree

            double theta0 = theta;
            double theta1 = theta + eps;

            double rtheta0  = theta0;
            double theta0pow = theta0;
            double theta02   = theta0*theta0;
            for (int j=0; j < LENGTH_KC; j++) {
                theta0pow *= theta02;
                rtheta0   += kc[j]*theta0pow;
            }

            double rtheta1  = theta1;
            double theta1pow = theta1;
            double theta12   = theta1*theta1;
            for (int j=0; j < LENGTH_KC; j++) {
                theta1pow *= theta12;
                rtheta1   += kc[j]*theta1pow;
            }

            double J = (rtheta1 - rtheta0) / eps;

            double res = rtheta0 - rtheta;

            theta = theta - res/J;

            if (Math.abs(res) < 1.8e-5) // less than 0.001 degrees?
                break;
        }

        // Theta is the angle off of the z axis, which forms a triangle with sides
        // z (adjacent), r (opposite), and mag (hypotenuse). Theta is the hard mapping.
        // Psi, the rotation about the z axis, relates x, y, and r.

        // magnitude == 1
        double z = Math.cos(theta); // cos(theta) = A/H = z/1 = z
        double r = Math.sin(theta); // sin(theta) = O/H = r/1 = r

        double x = r*Math.cos(psi);
        double y = r*Math.sin(psi);

        return new double[] { x, y, z };
    }
}

