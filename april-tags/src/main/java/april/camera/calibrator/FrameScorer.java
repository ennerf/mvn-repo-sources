package april.camera.calibrator;

import java.awt.image.*;
import java.util.*;
import april.tag.*;

public interface FrameScorer
{
    // All FrameScorers should also have a constructor with signature
    // public  FrameScorer(CameraCalibrator cal, int width, int height)


    public double scoreFrame(List<TagDetection> dets);

}