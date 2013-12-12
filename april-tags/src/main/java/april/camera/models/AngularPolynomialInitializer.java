package april.camera.models;

import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;

public class AngularPolynomialInitializer implements CalibrationInitializer
{
    public static boolean verbose = false;

    String parameterString;
    int kclen;

    public AngularPolynomialInitializer(String parameterString)
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
        double kc[] = new double[kclen];

        // initialize kc to the polynomial approximation to the tangent function
        double defaultkc[] = new double[] {  1.0/  3,
                                             2.0/ 15,
                                            17.0/315,
                                             0.0    };

        for (int i = 0; i < kc.length; i++) {
            if (i < defaultkc.length) kc[i] = defaultkc[i];
            else                      kc[i] = 0.0;
        }

        return new AngularPolynomialCalibration(fc, cc, kc, width, height);
    }

    /** Initialize the calibration using the provided parameters. Essentially,
      * create the desired class (which implements ParameterizableCalibration)
      * and reset its parameters to those provided. Don't waste time estimating
      * the parameters
      */
    public ParameterizableCalibration initializeWithParameters(int width, int height,
                                                               double params[])
    {
        return new AngularPolynomialCalibration(kclen, params, width, height);
    }
}

