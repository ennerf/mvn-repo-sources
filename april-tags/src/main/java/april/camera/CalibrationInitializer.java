package april.camera;

import java.util.*;

import april.tag.*;

public interface CalibrationInitializer
{
    // Note: must have a constructor which takes a single string (for reflection)

    /** Return the parameter string passed in via the required constructor.
      */
    public String getParameterString();

    /** Initialize the calibration using the estimation process specified by
      * the initializer. Returns null if initialization could not proceed.
      */
    public ParameterizableCalibration initializeWithObservations(int width, int height,
                                                                 List<List<TagDetection>> allDetections,
                                                                 TagMosaic tm);

    /** Initialize the calibration using the provided parameters. Essentially,
      * create the desired class (which implements ParameterizableCalibration)
      * and reset its parameters to those provided. Don't waste time estimating
      * the parameters
      */
    public ParameterizableCalibration initializeWithParameters(int width, int height,
                                                               double params[]);
}
