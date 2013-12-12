package april.camera.calibrator;

import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;

public class InitializationVarianceScorer implements FrameScorer
{
    public static int NSAMPLES = 20;

    CameraCalibrator currentCal;
    int imwidth, imheight;

    List<CalibrationInitializer> initializers;
    TagMosaic tm;

    Random r = new Random();

    public InitializationVarianceScorer(CameraCalibrator cal, int width, int height)
    {
        this.currentCal = cal;
        this.imwidth = width;
        this.imheight = height;

        initializers = cal.getCalRef().getInitializers();
        assert(initializers.size() == 1); // only support single-camera mode

        tm = cal.getTagMosaic();
    }

    public double scoreFrame(List<TagDetection> detections)
    {
        CalibrationInitializer initializer = initializers.get(0);

        // verify that we can initialize at all
        if (true) {
            ParameterizableCalibration cal = initializer.initializeWithObservations(imwidth, imheight,
                                                                                    Arrays.asList(detections), tm);
            if (cal == null)
                return Double.NaN;

            //double K[][] = cal.copyIntrinsics();
            //System.out.printf("InitializationVarianceScorer.scoreFrame: f is %f\n", K[0][0]);
        }

        List<Double> focallengths = new ArrayList<Double>();
        for (int iter=0; iter < this.NSAMPLES; iter++) {

            try {
                List<TagDetection> noisyDetections = new ArrayList<TagDetection>();
                for (TagDetection d : detections) {

                    TagDetection rd = copyDetection(d);
                    rd.cxy[0] += (r.nextDouble()*2 - 1) * 2.0;
                    rd.cxy[1] += (r.nextDouble()*2 - 1) * 2.0;

                    noisyDetections.add(rd);
                }

                ParameterizableCalibration cal = initializer.initializeWithObservations(imwidth, imheight,
                                                                                        Arrays.asList(noisyDetections),
                                                                                        tm);

                if (cal == null)
                    continue;

                double K[][] = cal.copyIntrinsics();
                assert(K != null);

                focallengths.add(K[0][0]);

            } catch (Exception ex) {
            }
        }

        if (focallengths.size() > 0) {
            double sum = 0, sumsquared = 0;
            int nfs = 0, nnans = 0;
            for (Double f : focallengths) {

                if (Double.isNaN(f)) {
                    nnans++;
                    continue;
                }

                sum += f;
                sumsquared += f*f;
                nfs++;
            }

            double Ef = sum / nfs;
            double Ef2 = sumsquared / nfs;
            double variance = Ef2 - Ef*Ef;
            double stddev = Math.sqrt(variance);

            return stddev;
        }

        return Double.POSITIVE_INFINITY;
    }

    private TagDetection copyDetection(TagDetection d)
    {
        TagDetection cd = new TagDetection();

        // not supported
        cd.homography           = null;
        cd.hxy                  = null;

        // easy stuff
        cd.good                 = d.good;
        cd.obsCode              = d.obsCode;
        cd.code                 = d.code;
        cd.id                   = d.id;
        cd.hammingDistance      = d.hammingDistance;
        cd.rotation             = d.rotation;
        cd.observedPerimeter    = d.observedPerimeter;

        // fix these for estimating the intrinsics
        cd.cxy                  = LinAlg.copy(d.cxy);
        cd.p                    = LinAlg.copy(d.p);

        return cd;
    }
}
