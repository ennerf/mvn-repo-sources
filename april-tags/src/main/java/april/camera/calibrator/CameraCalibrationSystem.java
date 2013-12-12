package april.camera.calibrator;

import java.awt.image.*;
import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;

// TODO
//  - replace xyzrpy with xyzquat?

/** This class attempts to handle camera and mosaic initialization,
    as well as sub-graph extraction when not enough information is known
    to perform nonlinear optimization on the entire system. Values improved
    by optimization should be updated so they are present when other terms
    can be initialized. Preferred initial values can be set by the user, but
    they will not be used until the parameter should be properly constrained
    by the tag detections present.

    // XXX it should be possible to initialize intrinsics even when we can't compute them

    Initialization occurs when all of the conditions (dependencies in brackets)
    for any of the actions below are true. These naturally form a topological ordering,
    so we can just check for each condition sequentially

    initialize camera intrinsics
    {
        initializer returns non-null ParameterizableCalibration
    }

    initialize mosaic extrinsics w.r.t. cameraX's subsystem
    {
        cameraX's intrinsics have been initialized
        cameraX's extrinsics have been initialized w.r.t. a known root
        number of detections in cameraX image exceeds minimum
    }

    initialize camera extrinsics for cameraX w.r.t. cameraY
    {
        a mosiac exists with extrinsics w.r.t. cameraX
            \-- depends on: cameraX's intrinsics have been initialized

        a mosiac exists with extrinsics w.r.t. cameraY
            \-- depends on: cameraY's intrinsics have been initialized
    }
*/
public class CameraCalibrationSystem
{
    public int REQUIRED_TAGS_PER_IMAGE = 8;   // number of constraints needed per image
    public boolean verbose = false; // don't make static

    ////////////////////////////////////////////////////////////////////////////////
    // Data
    TagFamily           tf;
    TagMosaic           tm;
    double              metersPerTag;

    List<CameraWrapper> cameras;
    List<MosaicWrapper> mosaics;

    ////////////////////////////////////////////////////////////////////////////////
    // Classes
    public static class CameraWrapper
    {
        public int                      cameraNumber; // camera index zero-indexed
        public String                   name;         // safe to change at any time
        public CalibrationInitializer   initializer;

        public int width;
        public int height;

        // intrinsics
        public ParameterizableCalibration cal; // use get and set methods for parameter lists

        // extrinsics
        public double  CameraToRootXyzrpy[];
        public int     rootNumber; // the cameraNumber for the root camera
    }

    public static class MosaicWrapper
    {
        // one image and detection list per camera
        public List<BufferedImage>      imageSet;
        public List<List<TagDetection>> detectionSet;

        // One xyzrpy per subsystem that this mosaic has been connected to.
        // If a camera's intrinsics were just initialized, it is its own root,
        // and there will not be an entry in this map for it. If a camera was
        // already initialized, there may be an entry already from another
        // camera with the same root
        public HashMap<Integer,double[]> MosaicToRootXyzrpys;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Methods
    public CameraCalibrationSystem(List<CalibrationInitializer> initializers,
                                   TagFamily tf, double metersPerTag, boolean verbose)
    {
        this.verbose = verbose;
        this.tf = tf;
        this.tm = new TagMosaic(tf, metersPerTag);
        this.metersPerTag = metersPerTag;

        cameras = new ArrayList<CameraWrapper>();

        for (int cameraIndex = 0; cameraIndex < initializers.size(); cameraIndex++)
        {
            CameraWrapper cam = new CameraWrapper();
            cam.cameraNumber    = cameraIndex;
            cam.name            = String.format("camera%04d", cameraIndex);
            cam.initializer     = initializers.get(cameraIndex);

            // we don't know the dimensions until we see an image
            cam.width = -1;
            cam.height = -1;

            // we don't know the intrinsics
            cam.cal = null;

            // all cameras are initialized in separate frames at the origin
            // (they won't have "interesting" extrinsics at first). cameras
            // that are in their own coordinate system have rootNumber == cameraNumber
            cam.CameraToRootXyzrpy = new double[6];
            cam.rootNumber = cam.cameraNumber;

            cameras.add(cam);
        }

        // no frames yet
        mosaics = new ArrayList<MosaicWrapper>();
    }

    public void addSingleImageSet(List<BufferedImage> imageSet,
                                  List<List<TagDetection>> detectionSet)
    {
        // call the multi-set function with single-entry lists
        addMultipleImageSets(Arrays.asList(imageSet),
                             Arrays.asList(detectionSet));
    }

    public void addMultipleImageSets(List<List<BufferedImage>> imageSets,
                                     List<List<List<TagDetection>>> detectionSets)
    {
        assert(imageSets.size() == detectionSets.size());

        for (int setindex = 0; setindex < imageSets.size(); setindex++)
        {
            List<BufferedImage> imageSet = imageSets.get(setindex);
            List<List<TagDetection>> detectionSet = detectionSets.get(setindex);

            assert(imageSet.size() == cameras.size());
            assert(detectionSet.size() == cameras.size());

            // update/check camera width/height fields
            for (int cameraIndex = 0; cameraIndex < cameras.size(); cameraIndex++)
            {
                CameraWrapper cam   = cameras.get(cameraIndex);
                BufferedImage image = imageSet.get(cameraIndex);

                // only reset the first time (so we assert every other time)
                if (cam.width == -1 || cam.height == -1) {
                    cam.width = image.getWidth();
                    cam.height = image.getHeight();
                }

                assert(cam.width == image.getWidth());
                assert(cam.height == image.getHeight());
            }

            // create a wrapper for the image data
            MosaicWrapper mosaic = new MosaicWrapper();
            mosaic.imageSet             = imageSet;
            mosaic.detectionSet         = detectionSet;
            mosaic.MosaicToRootXyzrpys  = new HashMap<Integer,double[]>();

            mosaics.add(mosaic);
        }

        // Update all of the system state. The order below (intrinsics,
        // mosaics, camera extrinsics) naturally forms a topological ordering,
        // which means that we can update them one-by-one and any changes that
        // would affect the camera extrinsics, for example, would already have
        // been made in the previous two calls
        updateIntrinsics();
        updateMosaicExtrinsics();
        updateSubsystemExtrinsics();
    }

    /** Estimate camera intrinsics.
      *
      * Depends on the number of images in the system
      */
    private void updateIntrinsics()
    {
        for (int cameraIndex = 0; cameraIndex < cameras.size(); cameraIndex++)
        {
            CameraWrapper cam = cameras.get(cameraIndex);

            // skip if previously initialized
            if (cam.cal != null)
                continue;

            List<List<TagDetection>> usableDetections = getCamerasUsableDetections(cameraIndex);

            ParameterizableCalibration cal =
                cam.initializer.initializeWithObservations(cam.width, cam.height,
                                                           usableDetections,
                                                           this.tm);

            // skip if invalid result returned
            if (cal == null)
                continue;

            if (verbose)
                System.out.printf("Initialized intrinsics for camera %d "+
                                  "(used %d of %d images)\n",
                                  cameraIndex, usableDetections.size(), mosaics.size());

            cam.cal = cal;
        }
    }

    /** Estimate mosaic-to-camera extrinsics.
      *
      * Depends on camera intrinsics.
      */
    private void updateMosaicExtrinsics()
    {
        for (int mosaicIndex = 0; mosaicIndex < mosaics.size(); mosaicIndex++)
        {
            MosaicWrapper mosaic = mosaics.get(mosaicIndex);

            for (int cameraIndex = 0; cameraIndex < cameras.size(); cameraIndex++)
            {
                CameraWrapper cam = cameras.get(cameraIndex);

                // skip if this camera's intrinsics haven't been initialized
                if (cam.cal == null)
                    continue;

                // skip if we've already initialized this mosaic w.r.t. the
                // root camera in this subsystem
                if (mosaic.MosaicToRootXyzrpys.get(cam.rootNumber) != null)
                    continue;

                // skip if this mosaic wasn't observed in the current camera
                List<TagDetection> detections = mosaic.detectionSet.get(cameraIndex);
                if (detectionsUsable(detections) == false)
                    continue;

                double MosaicToCam[][] = estimateMosaicToCameraTransformation(cam.cal, detections);

                double CamToRoot[][] = LinAlg.xyzrpyToMatrix(cam.CameraToRootXyzrpy);

                double MosaicToRoot[][] = LinAlg.matrixAB(CamToRoot,
                                                          MosaicToCam);

                double xyzrpy[] = LinAlg.matrixToXyzrpy(MosaicToRoot);

                if (verbose)
                    System.out.printf("Initialized extrinsics for mosaic %d"+
                                      " using camera %d to root %d\n",
                                      mosaicIndex, cam.cameraNumber, cam.rootNumber);

                mosaic.MosaicToRootXyzrpys.put(cam.rootNumber, xyzrpy);
            }
        }
    }

    /** Try to perform a merge if any mosaics define rigid body transformations
      * between disconnected subsystems.
      *
      * Depends on camera intrinsics and mosaic extrinsics
      */
    private void updateSubsystemExtrinsics()
    {
        for (int mosaicIndex = 0; mosaicIndex < mosaics.size(); mosaicIndex++)
        {
            MosaicWrapper mosaic = mosaics.get(mosaicIndex);

            Integer rootNumbers[] = mosaic.MosaicToRootXyzrpys.keySet().toArray(new Integer[0]);

            // if this mosaic isn't currently defined in multiple subsystems,
            // we can't use it to merge subsystems
            if (rootNumbers.length < 2)
                continue;

            // get camera with lowest unique identifier. All other connected
            // subsystems will transform into this camera's coordinate frame
            int rootNumber = rootNumbers[0];
            for (int cameraNumber : rootNumbers)
                rootNumber = Math.min(rootNumber, cameraNumber);

            CameraWrapper root = cameras.get(rootNumber);
            assert(root != null);

            double MosaicToRootXyzrpy[] = mosaic.MosaicToRootXyzrpys.get(rootNumber);
            assert(MosaicToRootXyzrpy != null);

            double MosaicToRoot[][] = LinAlg.xyzrpyToMatrix(MosaicToRootXyzrpy);

            List<Integer> defunctRoots = new ArrayList<Integer>();

            // loop over every other camera connected to this mosaic and transform its subsystem
            for (int cameraNumber : rootNumbers) {
                // skip the new root
                if (cameraNumber == rootNumber)
                    continue;

                CameraWrapper cam = cameras.get(cameraNumber);
                assert(cam.cameraNumber == cam.rootNumber); // we expected to get a root camera

                double MosaicToCameraXyzrpy[] = mosaic.MosaicToRootXyzrpys.get(cameraNumber);
                assert(MosaicToCameraXyzrpy != null);

                double MosaicToCamera[][] = LinAlg.xyzrpyToMatrix(MosaicToCameraXyzrpy);

                double CameraToRoot[][] = LinAlg.matrixAB(MosaicToRoot,
                                                          LinAlg.inverse(MosaicToCamera));

                // apply the transformation
                transformMosaicsExcept(mosaicIndex, rootNumber, cameraNumber, CameraToRoot);
                transformCameras(rootNumber, cameraNumber, CameraToRoot);
                defunctRoots.add(cameraNumber);

                if (verbose)
                    System.out.printf("Connected subsystems for roots %d and %d"+
                                      "(transformed %d --> %d) using mosaic %d\n",
                                      rootNumber, cameraNumber,
                                      cameraNumber, rootNumber,
                                      mosaicIndex);
            }

            for (int defunctRoot : defunctRoots)
                mosaic.MosaicToRootXyzrpys.remove(defunctRoot);

            if (verbose)
                System.out.printf("Removed %d defunct roots from mosaic %d\n",
                                  defunctRoots.size(), mosaicIndex);
        }
    }

    public void printSystem()
    {
        // print cameras
        for (int cameraIndex = 0; cameraIndex < cameras.size(); cameraIndex++)
        {
            CameraCalibrationSystem.CameraWrapper cam = cameras.get(cameraIndex);
            System.out.printf("Camera %d '%s'", cameraIndex, cam.name);
            System.out.printf(" cameraNumber %d rootNumber %d", cam.cameraNumber, cam.rootNumber);
            System.out.printf(" width %4d height %4d\n", cam.width, cam.height);
            if (cam.cal != null) {
                System.out.printf("    intrinsics: ");
                LinAlg.printTranspose(cam.cal.getParameterization());
            }
            System.out.printf("    extrinsics: ");
            LinAlg.printTranspose(cam.CameraToRootXyzrpy);
        }

        // print mosaics
        for (int mosaicIndex = 0; mosaicIndex < mosaics.size(); mosaicIndex++)
        {
            CameraCalibrationSystem.MosaicWrapper mosaic = mosaics.get(mosaicIndex);
            System.out.printf("Mosaic %d", mosaicIndex);
            System.out.printf(" %d images, %d detection sets with [ ",
                              mosaic.imageSet.size(), mosaic.detectionSet.size());
            for (List<TagDetection> d : mosaic.detectionSet)
                System.out.printf("%d ", d.size());
            System.out.println("] detections");

            Set<Map.Entry<Integer,double[]>> xyzrpys = mosaic.MosaicToRootXyzrpys.entrySet();
            for (Map.Entry<Integer,double[]> xyzrpy : xyzrpys) {
                System.out.printf("    Extrinsics for root %d: ", xyzrpy.getKey());
                LinAlg.printTranspose(xyzrpy.getValue());
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Helper methods

    public List<List<TagDetection>> getCamerasUsableDetections(int cameraIndex)
    {
        List<List<TagDetection>> currentCameraDetections = new ArrayList<List<TagDetection>>();

        for (MosaicWrapper mosaic : mosaics) {
            List<TagDetection> detections = mosaic.detectionSet.get(cameraIndex);

            if (detectionsUsable(detections))
                currentCameraDetections.add(detections);
        }

        return currentCameraDetections;
    }

    public boolean detectionsUsable(List<TagDetection> detections)
    {
        if (detections.size() < REQUIRED_TAGS_PER_IMAGE)
            return false;

        // skip if the tags don't span multiple rows and columns
        int minRow = this.tm.getRow(detections.get(0).id);
        int maxRow = minRow;
        int minCol = this.tm.getColumn(detections.get(0).id);
        int maxCol = minCol;
        for (TagDetection d : detections) {
            minRow = Math.min(minRow, this.tm.getRow(d.id));
            maxRow = Math.max(maxRow, this.tm.getRow(d.id));
            minCol = Math.min(minCol, this.tm.getColumn(d.id));
            maxCol = Math.max(maxCol, this.tm.getColumn(d.id));
        }

        int rowSpan = maxRow - minRow + 1;
        int colSpan = maxCol - minCol + 1;
        if (rowSpan < 2 || colSpan < 2)
            return false;

        return true;
    }

    private double[][] estimateMosaicToCameraTransformation(ParameterizableCalibration cal,
                                                            List<TagDetection> detections)
    {
        double K[][] = cal.copyIntrinsics();

        ArrayList<double[]> points_mosaic_meters = new ArrayList<double[]>();
        ArrayList<double[]> points_image_pixels = new ArrayList<double[]>();

        for (TagDetection d : detections) {
            points_mosaic_meters.add(LinAlg.select(this.tm.getPositionMeters(d.id), 0, 1));
            points_image_pixels.add(LinAlg.select(d.cxy, 0, 1));
        }

        double H[][] = CameraMath.estimateHomography(points_mosaic_meters,
                                                     points_image_pixels);

        double Rt[][] = CameraMath.decomposeHomography(H, K, points_mosaic_meters.get(0));

        return Rt;
    }

    /** Transform all mosaics to the new coordinate system except the mosaic specified.
      */
    private void transformMosaicsExcept(int skipIndex,
                                        int newRootNumber, int oldRootNumber, double OldRootToNewRoot[][])
    {
        for (int mosaicIndex = 0; mosaicIndex < mosaics.size(); mosaicIndex++)
        {
            // skip the mosaic that the caller is operating on
            if (mosaicIndex == skipIndex)
                continue;

            MosaicWrapper mosaic = mosaics.get(mosaicIndex);

            double MosaicToOldRootXyzrpy[] = mosaic.MosaicToRootXyzrpys.remove(oldRootNumber);

            // skip if this mosaic isn't rooted against our old root
            if (MosaicToOldRootXyzrpy == null)
                continue;

            // if we're already connected to the new root, we're done since we removed the old one
            if (mosaic.MosaicToRootXyzrpys.get(newRootNumber) != null)
                continue;

            double MosaicToOldRoot[][] = LinAlg.xyzrpyToMatrix(MosaicToOldRootXyzrpy);

            double MosaicToNewRoot[][] = LinAlg.matrixAB(OldRootToNewRoot,
                                                         MosaicToOldRoot);

            double xyzrpy[] = LinAlg.matrixToXyzrpy(MosaicToNewRoot);

            mosaic.MosaicToRootXyzrpys.put(newRootNumber, xyzrpy);
        }
    }

    /** Transform all cameras with the old root specified to the new root with the
      * provided rigid-body transformation.
      */
    private void transformCameras(int newRootNumber, int oldRootNumber, double OldRootToNewRoot[][])
    {
        assert(oldRootNumber != newRootNumber);

        for (int cameraIndex = 0; cameraIndex < cameras.size(); cameraIndex++)
        {
            CameraWrapper cam = cameras.get(cameraIndex);

            if (cam.rootNumber != oldRootNumber)
                continue;

            double CameraToOldRoot[][] = LinAlg.xyzrpyToMatrix(cam.CameraToRootXyzrpy);

            double CameraToNewRoot[][] = LinAlg.matrixAB(OldRootToNewRoot,
                                                         CameraToOldRoot);

            double xyzrpy[] = LinAlg.matrixToXyzrpy(CameraToNewRoot);

            cam.CameraToRootXyzrpy = xyzrpy;
            cam.rootNumber = newRootNumber;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    /** Return reference to list of cameras in use by this class instance.
      * The user is only allowed to change the camera name (not needed internally)
      * or the parameters for the ParameterizableCalibration or CameraToRootXyzrpy.
      * The user *shall not* initialize anything that has not been initialized
      * or uninitialize variables that are null until initialization
      * (e.g. ParameterizableCalibration)
      */
    public List<CameraWrapper> getCameras()
    {
        return cameras;
    }

    /** Return a list of all CalibrationInitializer objects.
      */
    public List<CalibrationInitializer> getInitializers()
    {
        List<CalibrationInitializer> initializers = new ArrayList();
        for (CameraWrapper cam : this.cameras)
            initializers.add(cam.initializer);

        return initializers;
    }

    /** Return reference to list of mosaics in use by this class instance.
      * The user is only allowed to read images/tag detections or change the
      * values of entries in the MosaicToRootXyzrpys hashmap. The user is *not*
      * allowed to add or remove entries in the hashmap nor remove images or
      * tag detections.
      */
    public List<MosaicWrapper> getMosaics()
    {
        return mosaics;
    }

    public List<List<BufferedImage>> getAllImageSets()
    {
        List<List<BufferedImage>> allImageSets = new ArrayList<List<BufferedImage>>();

        for (MosaicWrapper mosaic : mosaics)
            allImageSets.add(mosaic.imageSet);

        return allImageSets;
    }

    public List<List<List<TagDetection>>> getAllDetectionSets()
    {
        List<List<List<TagDetection>>> allDetectionSets = new ArrayList<List<List<TagDetection>>>();

        for (MosaicWrapper mosaic : mosaics)
            allDetectionSets.add(mosaic.detectionSet);

        return allDetectionSets;
    }

    /** Return a copy of this CameraCalibrationSystem. All references except
      * camera initializers, mosaic image sets, and mosaic detection sets point
      * to new objects.
      */
    public CameraCalibrationSystem copy()
    {
        CameraCalibrationSystem sub = new CameraCalibrationSystem(this.getInitializers(),
                                                                  this.tf, this.metersPerTag,
                                                                  this.verbose);
        sub.REQUIRED_TAGS_PER_IMAGE = this.REQUIRED_TAGS_PER_IMAGE;
        copyCameras(this, sub);
        copyMosaics(this, sub);

        return sub;
    }

    /** Copy cameras from input system to output system. Only the calibration
      * initializer reference is reused.
      */
    public void copyCameras(CameraCalibrationSystem from, CameraCalibrationSystem to)
    {
        to.cameras = new ArrayList();
        for (int i = 0; i < from.cameras.size(); i++)
        {
            CameraWrapper cam = from.cameras.get(i);
            assert(cam.cameraNumber == i);

            CameraWrapper copy = new CameraWrapper();
            copy.cameraNumber = cam.cameraNumber;
            copy.name = new String(cam.name);
            copy.initializer = cam.initializer; // reference copy

            copy.width = cam.width;
            copy.height = cam.height;

            if (cam.cal != null)
                copy.cal = copy.initializer.initializeWithParameters(copy.width, copy.height,
                                                                     cam.cal.getParameterization());

            copy.CameraToRootXyzrpy = LinAlg.copy(cam.CameraToRootXyzrpy);
            copy.rootNumber = cam.rootNumber;

            to.cameras.add(copy);
        }
    }

    /** Copy mosaics from an input system to an output system. Only the image
      * and detection set references are reused.
      */
    public void copyMosaics(CameraCalibrationSystem from, CameraCalibrationSystem to)
    {
        to.mosaics = new ArrayList();
        for (MosaicWrapper mosaic : from.mosaics)
        {
            MosaicWrapper copy = new MosaicWrapper();
            copy.imageSet = mosaic.imageSet; // reference copy
            copy.detectionSet = mosaic.detectionSet; // reference copy

            copy.MosaicToRootXyzrpys = new HashMap();

            Set<Map.Entry<Integer,double[]>> xyzrpys = mosaic.MosaicToRootXyzrpys.entrySet();
            for (Map.Entry<Integer,double[]> xyzrpy : xyzrpys)
            {
                Integer key = new Integer(xyzrpy.getKey());
                double value[] = LinAlg.copy(xyzrpy.getValue());

                copy.MosaicToRootXyzrpys.put(key, value);
            }

            to.mosaics.add(copy);
        }
    }

    /** Create a new camera system with batch initialization from the observations
      * in this camera system.
      */
    public CameraCalibrationSystem copyWithBatchReinitialization()
    {
        return copyWithBatchReinitialization(this.verbose);
    }

    /** Create a new camera system with batch initialization from the observations
      * in this camera system.
      */
    public CameraCalibrationSystem copyWithBatchReinitialization(boolean verbose)
    {
        // Copy the system (but not the mosaics)
        CameraCalibrationSystem sub = new CameraCalibrationSystem(this.getInitializers(),
                                                                  this.tf, this.metersPerTag,
                                                                  verbose);
        sub.REQUIRED_TAGS_PER_IMAGE = this.REQUIRED_TAGS_PER_IMAGE;

        // copy camera data but skip fields set by adding images
        for (int i = 0; i < this.cameras.size(); i++)
        {
            CameraWrapper cam = this.cameras.get(i);
            CameraWrapper copy = sub.cameras.get(i);

            assert(copy.cameraNumber == cam.cameraNumber);
            copy.name = new String(cam.name);
        }

        // add all of the images in a batch operation
        sub.addMultipleImageSets(this.getAllImageSets(),
                                 this.getAllDetectionSets());

        return sub;
    }
}

