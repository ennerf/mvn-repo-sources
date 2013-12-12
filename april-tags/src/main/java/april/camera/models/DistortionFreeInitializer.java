package april.camera.models;

import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;

public class DistortionFreeInitializer implements CalibrationInitializer
{
    public static boolean verbose = false;

    public DistortionFreeInitializer(String parameterString)
    {
    }

    /** Return the parameter string passed in via the required constructor.
      */
    public String getParameterString()
    {
        return "";
    }

    /** Initialize the calibration using the estimation process specified by
      * the initializer. Returns null if initialization could not proceed.
      */
    public ParameterizableCalibration initializeWithObservations(int width, int height,
                                                                 List<List<TagDetection>> allDetections,
                                                                 TagMosaic tm)
    {
        IntrinsicsEstimator estimator = new IntrinsicsEstimator(allDetections, tm,
                                                                width/2, height/2);
        double K[][] = estimator.getIntrinsics();
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

        return new DistortionFreeCalibration(fc, cc, width, height);
    }

    /** Initialize the calibration using the provided parameters. Essentially,
      * create the desired class (which implements ParameterizableCalibration)
      * and reset its parameters to those provided. Don't waste time estimating
      * the parameters
      */
    public ParameterizableCalibration initializeWithParameters(int width, int height,
                                                               double params[])
    {
        return new DistortionFreeCalibration(params, width, height);
    }
}

