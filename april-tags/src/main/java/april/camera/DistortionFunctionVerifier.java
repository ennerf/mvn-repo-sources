package april.camera;

import java.util.*;

import april.camera.models.*;
import april.jmat.*;
import april.util.*;

/** A DistortionFunctionVerifier attempts to determine the limit of the
 *  distortion model in the supplied View object. Once this limit is known,
 *  the DistortionFunctionVerifier can be queried to determine if it is
 *  valid to use the distortion model for a given pixel. One example of this
 *  is a lens distortion model that could be used to rectify a distorted image,
 *  but eventually degenerates (e.g. maps all pixels to a radius of zero)
 *  -- if a user were to blindly project 3D point cloud data using this distortion
 *  model, he would get points that appeared to project into the image, but
 *  should not project at all. By calling the DistortionFunctionVerifier before
 *  computing the projection via the distortion model, the user can protect himself
 *  from such degenerate behaviors.<br>
 *  <br>
 *  It should be noted that the DistortionFunctionVerifier attempts to compute
 *  when the distortion function is no longer valid, but it is not perfect.
 *  Consider supplying corner-cases found in practice to the author, Andrew
 *  Richardson (chardson@umich.edu) so the DistortionFunctionVerifier can be
 *  improved.
 */
public class DistortionFunctionVerifier
{
    // How much further should we go beyond the furthest corner when looking
    // for the max radius? a value of 0.00 goes to the furthest corner exactly.
    // A value of 0.10 goes 10% beyond the corner radius
    public static double radiusBuffer = 0.10;

    double dTheta;

    double maxValidTheta; // angle off of principal (z) axis
    double maxValidPixelRadius;

    double cc[];

    public DistortionFunctionVerifier(View view)
    {
        this(view, Math.PI/1000); // 1000 steps over the theta range
    }

    public DistortionFunctionVerifier(View view, double dTheta)
    {
        double K[][] = view.copyIntrinsics();

        this.dTheta = dTheta;
        this.cc     = new double[] { K[0][2], K[1][2] };

        int width   = view.getWidth();
        int height  = view.getHeight();
        double maxObservedPixelRadius = getMaxObservedPixelRadius(cc, width, height);

        double maxTheta = Math.PI; // max angle from z axis
        double lastPixelRadius = 0;

        for (double theta = 0; theta < maxTheta; theta += dTheta) {

            double x = Math.sin(theta); // if y==0, x==r. sin(theta) = O/H = r/1 = r = x
            double y = 0;
            double z = Math.cos(theta); // cos(theta) = A/H = z/1 = z

            double xyz_r[] = new double[] { x, y, z };

            double xy_dp[] = view.rayToPixels(xyz_r);
            double pixelRadius = LinAlg.distance(xy_dp, cc);

            if (pixelRadius < lastPixelRadius)
                break;

            this.maxValidTheta = theta;
            this.maxValidPixelRadius = pixelRadius;

            lastPixelRadius = pixelRadius;

            // break if we're past the furthest corner in the distorted image.
            // we add a user-configurable buffer because projections just outside
            // of the image can be useful
            if (pixelRadius > (1.0 + this.radiusBuffer)*maxObservedPixelRadius)
                break;
        }
    }

    private double getMaxObservedPixelRadius(double cc[], int width, int height)
    {
        double max = 0;

        max = Math.max(max, LinAlg.distance(cc, new double[] {      0,        0}));
        max = Math.max(max, LinAlg.distance(cc, new double[] {width-1,        0}));
        max = Math.max(max, LinAlg.distance(cc, new double[] {      0, height-1}));
        max = Math.max(max, LinAlg.distance(cc, new double[] {width-1, height-1}));

        return max;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // public methods

    /** Will this ray be valid after distortion?
      */
    public boolean validRay(double xyz_r[])
    {
        double x = xyz_r[0];
        double y = xyz_r[1];
        double z = xyz_r[2];

        double r = Math.sqrt(x*x + y*y);
        double theta = Math.atan2(r, z);

        if (theta < this.maxValidTheta)
            return true;

        return false;
    }

    /** Should this pixel coordinate undistort correctly?
      */
    public boolean validPixelCoord(double xy_dp[])
    {
        double dx = xy_dp[0] - cc[0];
        double dy = xy_dp[1] - cc[1];
        double pixelRadius = Math.sqrt(dx*dx + dy*dy);

        if (pixelRadius < this.maxValidPixelRadius)
            return true;

        return false;
    }

    /** If this ray is valid, return it, otherwise, return a ray at the same
      * angle about the principal axis (psi) with the maximum valid angle off
      * of the principal axis (theta) for this calibration.
      */
    public double[] clampRay(double xyz_r[])
    {
        double x = xyz_r[0];
        double y = xyz_r[1];
        double z = xyz_r[2];

        double r = Math.sqrt(x*x + y*y);
        double theta = Math.atan2(r, z);
        double psi   = Math.atan2(y, x);

        if (theta < this.maxValidTheta)
            return xyz_r;

        theta = this.maxValidTheta;

        z = Math.cos(theta); // cos(theta) = A/H = z/1 = z
        r = Math.sin(theta); // sin(theta) = O/H = r/1 = r

        x = r*Math.cos(psi);
        y = r*Math.sin(psi);

        return new double[] { x, y, z };
    }

    /** If this pixel coordinate is valid, return it, otherwise, return a
      * coordinate at the same angle about the principal axis with the maximum
      * valid pixel radius for this calibration.
      */
    public double[] clampPixels(double xy_dp[])
    {
        double relative_dp[] = LinAlg.subtract(xy_dp, cc);
        double pixelRadius = LinAlg.magnitude(relative_dp);

        if (pixelRadius < this.maxValidPixelRadius)
            return xy_dp;

        return LinAlg.add(cc,
                          LinAlg.scale(relative_dp,
                                       maxValidPixelRadius/pixelRadius));
    }
}

