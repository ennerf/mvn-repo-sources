package april.camera.models;

import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;

public class RadialPolynomialInitializer implements CalibrationInitializer
{
    public static boolean verbose = false;

    String parameterString;
    int kclen;

    public RadialPolynomialInitializer(String parameterString)
    {
        this.parameterString = parameterString;
        this.kclen = InitializerUtil.getParameter(parameterString, "kclength");
    }

    /** Return the parameter string passed in via the required constructor.
      */
    public String getParameterString()
    {
        return this.parameterString;
    }

    /** Initialize the calibration using the estimation process specified by
      * the initializer. Returns null if initialization could not proceed.
      */
    public ParameterizableCalibration initializeWithObservations(int width, int height,
                                                                 List<List<TagDetection>> allDetections,
                                                                 TagMosaic tm)
    {
        IntrinsicsFreeDistortionEstimator distortionEstimator = null;
        try {
            distortionEstimator = new IntrinsicsFreeDistortionEstimator(allDetections, tm,
                                                                        width, height);
        } catch (Exception ex) {
            return null;
        }

        List<List<TagDetection>> allRectifiedDetections = new ArrayList<List<TagDetection>>();
        for (List<TagDetection> detections : allDetections) {

            List<TagDetection> rectifiedDetections = new ArrayList<TagDetection>();
            for (TagDetection d : detections) {

                TagDetection rd = new TagDetection();

                // not supported
                rd.homography           = null;
                rd.hxy                  = null;

                // easy stuff
                rd.good                 = d.good;
                rd.obsCode              = d.obsCode;
                rd.code                 = d.code;
                rd.id                   = d.id;
                rd.hammingDistance      = d.hammingDistance;
                rd.rotation             = d.rotation;
                rd.observedPerimeter    = d.observedPerimeter;

                // fix these for estimating the intrinsics
                rd.cxy                  = distortionEstimator.undistort(d.cxy);
                rd.p                    = new double[d.p.length][];
                for (int i=0; i < d.p.length; i++)
                    rd.p[i] = distortionEstimator.undistort(d.p[i]);

                rectifiedDetections.add(rd);
            }

            allRectifiedDetections.add(rectifiedDetections);
        }

        IntrinsicsEstimator intrinsicsEstimator = new IntrinsicsEstimator(allRectifiedDetections, tm,
                                                                          width/2, height/2);

        double K[][] = intrinsicsEstimator.getIntrinsics();
        if (K == null)
            return null;

        if (verbose) System.out.println("Estimated intrinsics:");
        if (verbose) LinAlg.print(K);

        for (int i=0; i < K.length; i++)
            for (int j=0; j < K[i].length; j++)
                if (Double.isNaN(K[i][j]))
                    return null;

        double fc[] = new double[] {  K[0][0],  K[1][1] };
        double cc[] = new double[] {  width/2, height/2 };
        double kc[] = new double[kclen];

        return new RadialPolynomialCalibration(fc, cc, kc, width, height);
    }

    /** Initialize the calibration using the provided parameters. Essentially,
      * create the desired class (which implements ParameterizableCalibration)
      * and reset its parameters to those provided. Don't waste time estimating
      * the parameters
      */
    public ParameterizableCalibration initializeWithParameters(int width, int height,
                                                               double params[])
    {
        return new RadialPolynomialCalibration(kclen, params, width, height);
    }
}
