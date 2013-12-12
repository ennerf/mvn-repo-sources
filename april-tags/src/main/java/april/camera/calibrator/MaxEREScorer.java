package april.camera.calibrator;

import java.util.*;
import april.tag.*;
import april.graph.*;
import april.jmat.*;
import java.awt.image.*;

import april.camera.*;

/** Frame scorer which reports the Maximum Expected Reprojection Error (MaxERE)
  * for a coarse grid of samples spread across the image.
  *
  * You must instantiate a new FrameScorer each time the currentCal changes
  */
public class MaxEREScorer implements FrameScorer
{
    final static boolean useMarginal = true; // can either use marginal (slow) or conditional (faster, not "correct")

    // Do not change seed during a run!
    public static int seed = 14;
    public static int nsamples = 50;

    final CameraCalibrator currentCal;

    final int width, height;
    BufferedImage fakeIm;

    public MaxEREScorer(CameraCalibrator _currentCal, int _width, int _height)
    {
        currentCal = _currentCal;

        width = _width;
        height = _height;
        fakeIm = new BufferedImage(width,height, BufferedImage.TYPE_BYTE_BINARY); // cheapest
    }

    public double scoreFrame(List<TagDetection> dets)
    {
        CameraCalibrator cal = currentCal.copy(false);

        // XXX Passing null here
        cal.addOneImageSet(Arrays.asList(fakeIm), Arrays.asList(dets));

        // build graphs
        List<CameraCalibrator.GraphWrapper> graphWrappers = cal.buildCalibrationGraphs();
        assert(graphWrappers.size() == 1);

        // optimize
        List<CameraCalibrator.GraphStats> stats =
            cal.iterateUntilConvergence(graphWrappers, 0.01, 2, 1000);
        assert(stats.size() == 1);

        // update
        cal.updateFromGraphs(graphWrappers, stats);

        // there should only be one camera, so get its graph objects
        CameraCalibrator.GraphWrapper gw = graphWrappers.get(0);
        CameraCalibrator.GraphStats s = stats.get(0);
        assert(gw != null);
        assert(s != null);

        // get the node index for the camera intrinsics
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCalRef().getCameras();
        assert(cameras.size() == 1);
        Integer cameraIntrinsicsIndex = gw.cameraToIntrinsicsNodeIndex.get(cameras.get(0));
        assert(cameraIntrinsicsIndex != null);

        // return result on error
        if (s.SPDError)
            return Double.NaN;

        return scoreCal(cal.getCalRef().getInitializers(),
                        gw.g, cameraIntrinsicsIndex, width, height);
    }

    public static double scoreCal(CameraCalibrator cal, int width, int height)
    {
        // build graphs
        List<CameraCalibrator.GraphWrapper> graphWrappers = cal.buildCalibrationGraphs();
        assert(graphWrappers.size() == 1);

        CameraCalibrator.GraphWrapper gw = graphWrappers.get(0);
        assert(gw != null);

        // get the node index for the camera intrinsics
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCalRef().getCameras();
        assert(cameras.size() == 1);
        Integer cameraIntrinsicsIndex = gw.cameraToIntrinsicsNodeIndex.get(cameras.get(0));
        assert(cameraIntrinsicsIndex != null);

        return scoreCal(cal.getCalRef().getInitializers(),
                        gw.g, cameraIntrinsicsIndex, width, height);
    }

    public static double scoreCal(List<CalibrationInitializer> initializers,
                                  Graph gorig, int nodeIndex, int width, int height)
    {
        Graph g = gorig.copy();
        MultiGaussian mg = null;

        // Compute mean, covariance from which we will sample
        try {
            double mu[] = LinAlg.copy(g.nodes.get(nodeIndex).state);
            double P[][] = (useMarginal?
                            GraphUtil.getMarginalCovariance(g, nodeIndex).copyArray() :
                            GraphUtil.getConditionalCovariance(g, nodeIndex).copyArray());

            mg = new MultiGaussian(P, mu);

        } catch(RuntimeException e){
            return Double.NaN;
        }

        ArrayList<double[]> samples = mg.sampleMany(new Random(seed), nsamples);

        double errMeanVar[] = computeMaxErrorDist(mg.getMean(), samples, 5,
                                                  initializers.get(0), width, height);

        return errMeanVar[0];
    }


    // Returns the distribution corresponding to the worst point on the grid
    public static double[] computeMaxErrorDist(double mean[], List<double[]> samples, int gridSz,
                                               CalibrationInitializer init, int width, int height)
    {
        // Where do we want to sample? We need a way to determine which 3D points will project?

        ArrayList<ParameterizableCalibration> cals = new ArrayList();
        for (double p[] : samples)
            cals.add(init.initializeWithParameters(width, height, p));

        ParameterizableCalibration meanCal = init.initializeWithParameters(width, height, mean);

        ArrayList<MultiGaussian> errSamples = new ArrayList();
        for (int i = 0; i < gridSz; i++)
            for (int j = 0; j < gridSz; j++) {
                int x = (width-1) * j / (gridSz - 1);
                int y = (height-1) * i / (gridSz - 1);

                int idx = y*width + x;
                double meanPix [] = {x,y};
                double meanRay[] = meanCal.pixelsToRay(meanPix);

                MultiGaussianEstimator mge = new MultiGaussianEstimator(1);
                for (View cal : cals) {
                    double samplePix[] = cal.rayToPixels(meanRay);
                    mge.observe(new double[]{LinAlg.distance(samplePix, meanPix)});
                }
                errSamples.add(mge.getEstimate());
            }

        Collections.sort(errSamples, new Comparator<MultiGaussian>(){
                public int compare(MultiGaussian m1, MultiGaussian m2)
                {
                    assert(m1.getDimension() == m2.getDimension());
                    assert(m1.getDimension() == 1);
                    return Double.compare(m1.getMean()[0],
                                          m2.getMean()[0]);
                }
            });


        // These are actually single-variate gaussians...
        MultiGaussian mgWorst = errSamples.get(errSamples.size()-1);

        return new double[]{mgWorst.getMean()[0], mgWorst.getCovariance().get(0,0)};
    }

}
