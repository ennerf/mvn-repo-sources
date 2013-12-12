package april.camera.calibrator;

import java.awt.image.*;
import java.awt.Color;
import java.io.*;
import java.util.*;
import javax.imageio.*;

import april.camera.*;
import april.graph.*;
import april.jmat.*;
import april.jmat.ordering.*;
import april.tag.*;
import april.vis.*;

public class CameraCalibrator
{
    public boolean verbose = false; // don't make static

    List<CalibrationInitializer> initializers;
    CameraCalibrationSystem cal;
    CalibrationRenderer renderer;

    TagFamily tf;
    TagMosaic tm;
    double metersPerTag;

    public CameraCalibrator(List<CalibrationInitializer> initializers,
                            TagFamily tf, double metersPerTag,
                            boolean gui, boolean verbose)
    {
        this.verbose = verbose;
        this.initializers = initializers;
        this.tf = tf;
        this.tm = new TagMosaic(tf, metersPerTag);
        this.metersPerTag = metersPerTag;

        cal = new CameraCalibrationSystem(initializers, tf, metersPerTag, verbose);

        if (gui)
            renderer = new CalibrationRenderer(cal, this.tf, this.metersPerTag, verbose);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // add imagery

    public void addOneImageSet(List<BufferedImage> newImages,
                               List<List<TagDetection>> newDetections)
    {
        cal.addSingleImageSet(newImages, newDetections);

        if (renderer != null)
            renderer.updateMosaicDimensions(newDetections);
    }

    public void addManyImageSets(List<List<BufferedImage>> imagesList,
                                 List<List<List<TagDetection>>> detectionsList)
    {
        cal.addMultipleImageSets(imagesList, detectionsList);

        if (renderer != null)
            for (List<List<TagDetection>> lists : detectionsList)
                renderer.updateMosaicDimensions(lists);
    }


    public void resetCalibrationSystem()
    {
        cal = new CameraCalibrationSystem(initializers, tf, metersPerTag, verbose);

        if (renderer != null)
            renderer.replaceCalibrationSystem(cal);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // graph optimization

    public static class GraphStats
    {
        public int rootNumber;   // the root camera number for this graph stats object

        public int numObs;       // number of tags used
        public double MRE;       // mean reprojection error
        public double MSE;       // mean-squared reprojection error
        public double MaxRE;     // max reprojection error
        public boolean SPDError; // did we catch an SPD error from the graph solver?

        public Double MaxERE;    // max expected reprojection error. optional, for display only
    }

    public static class GraphWrapper
    {
        // The graph that was created (all graph state can be changed without affecting the camera system)
        public Graph g;

        // The root camera number for this graph
        public int rootNumber;

        // Maps that connect a camera or mosaic wrapper to the GNode index in the graph
        // Note: The keys in these maps are from the original CameraCalibrationSystem
        //  -- changing their values will modify the original system
        public HashMap<CameraCalibrationSystem.CameraWrapper,Integer> cameraToIntrinsicsNodeIndex;
        public HashMap<CameraCalibrationSystem.CameraWrapper,Integer> cameraToExtrinsicsNodeIndex;
        public HashMap<CameraCalibrationSystem.MosaicWrapper,Integer> mosaicToExtrinsicsNodeIndex;
    }

    /** Compute MRE and MSE for a graph.
      */
    public static GraphStats getGraphStats(Graph g, int rootNumber)
    {
        GraphStats stats = new GraphStats();
        stats.rootNumber = rootNumber;
        stats.numObs = 0;
        stats.MRE = 0;
        stats.MSE = 0;
        stats.MaxRE = 0;
        stats.SPDError = false;

        for (GEdge e : g.edges) {
            if (e instanceof GTagEdge) {
                GTagEdge edge = (GTagEdge) e;

                double res[] = edge.getResidualExternal(g);
                assert((res.length & 0x1) == 0);

                for (int i=0; i < res.length; i+=2) {
                    double sqerr = res[i]*res[i] + res[i+1]*res[i+1];
                    double abserr = Math.sqrt(sqerr);
                    stats.MRE += abserr;
                    stats.MSE += sqerr;
                    stats.MaxRE = Math.max(stats.MaxRE, abserr);
                }

                stats.numObs += res.length / 2;
            }
        }

        stats.MRE /= stats.numObs;
        stats.MSE /= stats.numObs;

        return stats;
    }


    /** Iterate each graph until convergence. See the single-graph method for details.
      */
    public List<GraphStats> iterateUntilConvergence(List<GraphWrapper> graphs, double improvementThreshold,
                                                    int minConvergedIterations, int maxIterations)
    {
        return iterateUntilConvergence(graphs, improvementThreshold, minConvergedIterations,
                                       maxIterations, this.verbose);
    }

    /** Iterate each graph until convergence. See the single-graph method for details.
      */
    public static List<GraphStats> iterateUntilConvergence(List<GraphWrapper> graphs, double improvementThreshold,
                                                           int minConvergedIterations, int maxIterations,
                                                           boolean verbose)
    {
        List<GraphStats> stats = new ArrayList<GraphStats>();
        for (GraphWrapper gw : graphs) {
            GraphStats s = iterateUntilConvergence(gw, improvementThreshold,
                                                   minConvergedIterations, maxIterations, verbose);
            stats.add(s);
        }

        return stats;
    }

    /** Iterate graph until it converges. Catch SPD errors and set the field in the
      * GraphStats object that is returned.
      */
    public GraphStats iterateUntilConvergence(GraphWrapper gw, double improvementThreshold,
                                              int minConvergedIterations, int maxIterations)
    {
        return iterateUntilConvergence(gw, improvementThreshold, minConvergedIterations,
                                       maxIterations, this.verbose);
    }

    /** Iterate graph until it converges. Catch SPD errors and set the field in the
      * GraphStats object that is returned.
      */
    public static GraphStats iterateUntilConvergence(GraphWrapper gw, double improvementThreshold,
                                                     int minConvergedIterations, int maxIterations,
                                                     boolean verbose)
    {
        if (gw == null)
            return null;

        GraphSolver solver = new CholeskySolver(gw.g, new MinimumDegreeOrdering());
        //GraphSolver solver = new LMSolver(gw.g, new MinimumDegreeOrdering(), 10e-6, 10e6, 10);
        CholeskySolver.verbose = false;

        GraphStats lastStats = getGraphStats(gw.g, gw.rootNumber);
        int convergedCount = 0;
        int iterationCount = 0;

        try {
            while (iterationCount < maxIterations && convergedCount < minConvergedIterations) {

                solver.iterate();
                GraphStats stats = getGraphStats(gw.g, gw.rootNumber);

                double percentImprovement = (lastStats.MRE - stats.MRE) / lastStats.MRE;

                if (percentImprovement < improvementThreshold)
                    convergedCount++;
                else
                    convergedCount = 0;

                lastStats = stats;

                iterationCount++;
            }
        } catch (RuntimeException ex) {
            lastStats.SPDError = true;

            if (verbose)
                System.out.println("CameraCalibrator: Caught SPD error during optimization");
        }

        return lastStats;
    }

    private void addMaxERE(List<GraphStats> stats)
    {
        if (stats.size() != 1)
            return;

        GraphStats gs = stats.get(0);

        List<CameraCalibrationSystem.CameraWrapper> camWrappers = cal.getCameras();
        if (camWrappers.size() == 1) {
            CameraCalibrationSystem.CameraWrapper cam = camWrappers.get(0);
            if (cal.getAllImageSets().size() >= 3 && cam.cal != null)
                gs.MaxERE = MaxEREScorer.scoreCal(this, cam.width, cam.height);
        }
    }

    public List<GraphStats> iterateUntilConvergenceWithReinitalization(double reinitMREThreshold, double improvementThreshold,
                                                                       int minConvergedIterations, int maxIterations)
    {
        // iterate the usual way
        List<GraphWrapper> origGraphWrappers = this.buildCalibrationGraphs();
        List<GraphStats> origStats = this.iterateUntilConvergence(origGraphWrappers, improvementThreshold,
                                                                  minConvergedIterations, maxIterations);

        boolean origError = false;
        double origJointMRE = 0;
        int origJointNumObs = 0;
        for (GraphStats s : origStats) {
            if (s == null || s.SPDError == true) {
                origError = true;
                continue;
            }

            origJointMRE += s.MRE*s.numObs;
            origJointNumObs += s.numObs;
        }
        origJointMRE = origJointMRE / origJointNumObs;

        boolean origBadFocalLength = false;
        for (GraphWrapper gw : origGraphWrappers) {
            if (gw == null || gw.g == null)
                continue;

            for (GNode n : gw.g.nodes) {
                if (n instanceof GIntrinsicsNode) {
                    GIntrinsicsNode intrinsics = (GIntrinsicsNode) n;
                    double K[][] = intrinsics.copyIntrinsicsMatrix();

                    if (K[0][0] < 0 || K[1][1] < 0)
                        origBadFocalLength = true;
                }
            }
        }

        // is it acceptable?
        if (!origError && !origBadFocalLength && origJointMRE < reinitMREThreshold) {
            this.updateFromGraphs(origGraphWrappers, origStats);
            if (verbose)
                System.out.printf("Skipped reinitialization, using original (orig %b/%b/%8.3f)\n",
                                  origError, origBadFocalLength, origJointMRE);
            addMaxERE(origStats);
            return origStats;
        }

        // build and optimize the new system
        CameraCalibrationSystem copy = this.cal.copyWithBatchReinitialization(false);
        List<GraphWrapper> newGraphWrappers = this.buildCalibrationGraphs(copy, false);
        List<GraphStats> newStats = this.iterateUntilConvergence(newGraphWrappers, improvementThreshold,
                                                                 minConvergedIterations, maxIterations);
        copy.verbose = this.verbose; // quiet during initialization

        boolean newError = false;
        double newJointMRE = 0;
        int newJointNumObs = 0;
        for (GraphStats s : newStats) {
            if (s == null || s.SPDError == true) {
                newError = true;
                continue;
            }

            newJointMRE += s.MRE*s.numObs;
            newJointNumObs += s.numObs;
        }
        newJointMRE = newJointMRE / newJointNumObs;

        boolean newBadFocalLength = false;
        for (GraphWrapper gw : newGraphWrappers) {
            if (gw == null || gw.g == null)
                continue;

            for (GNode n : gw.g.nodes) {
                if (n instanceof GIntrinsicsNode) {
                    GIntrinsicsNode intrinsics = (GIntrinsicsNode) n;
                    double K[][] = intrinsics.copyIntrinsicsMatrix();

                    if (K[0][0] < 0 || K[1][1] < 0)
                        newBadFocalLength = true;
                }
            }
        }

        // decide which system to use
        boolean useNew = false;
        if (!origError && !origBadFocalLength &&
            !newError && !newBadFocalLength &&
            (newJointMRE + 0.001 < origJointMRE)) // both are good but new has lower error
        {
            useNew = true;
        }
        else if (origError || origBadFocalLength)
            useNew = true;

        if (!useNew) {
            this.updateFromGraphs(origGraphWrappers, origStats);
            if (verbose)
                System.out.printf("Attempted reinitialization, using original (orig %b/%b/%8.3f new %b/%b/%8.3f)\n",
                                  origError, origBadFocalLength, origJointMRE,
                                  newError, newBadFocalLength, newJointMRE);
            addMaxERE(origStats);
            return origStats;
        }

        this.updateFromGraphs(newGraphWrappers, newStats);
        this.cal = copy;
        if (this.renderer != null)
            this.renderer.replaceCalibrationSystem(copy);
        if (verbose)
            System.out.printf("Attempted reinitialization, using new (orig %b/%b/%8.3f new %b/%b/%8.3f)\n",
                              origError, origBadFocalLength, origJointMRE,
                              newError, newBadFocalLength, newJointMRE);
        addMaxERE(newStats);
        return newStats;
    }

    /** Convenience method to build graphs for all connected subsystems. Finds
      * all unique rootNumbers in the camera system and calls buildCalibrationGraph()
      * to build a graph for each subsystem. See buildCalibrationGraph() for details.
      */
    public List<GraphWrapper> buildCalibrationGraphs()
    {
        return buildCalibrationGraphs(this.cal, this.verbose);
    }

    public static List<GraphWrapper> buildCalibrationGraphs(CameraCalibrationSystem cal, boolean verbose)
    {
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        Set<Integer> uniqueRoots = new TreeSet<Integer>();
        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
            uniqueRoots.add(cam.rootNumber);

        List<GraphWrapper> graphWrappers = new ArrayList<GraphWrapper>();
        for (int root : uniqueRoots)
            graphWrappers.add(buildCalibrationGraph(cal, root, verbose));

        return graphWrappers;
    }

    /** Build a graph for the specified root camera. Returns null if the graph
      * cannot be built yet (e.g. intrinsics not initialized). All state in the
      * contained graph is safe to change, as it is copied or regenerated from
      * the underlying CameraCalibrationSystem. This means you can optimize the
      * graph safely and ensure that it has suceeded before updating the
      * underlying CameraCalibrationSystem. See updateFromGraph() and related
      * methods
      */
    public static GraphWrapper buildCalibrationGraph(CameraCalibrationSystem cal, int rootNumber,
                                                     boolean verbose)
    {
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        GraphWrapper gw = new GraphWrapper();
        gw.g = new Graph();
        gw.rootNumber = rootNumber;
        gw.cameraToIntrinsicsNodeIndex = new HashMap();
        gw.cameraToExtrinsicsNodeIndex = new HashMap();
        gw.mosaicToExtrinsicsNodeIndex = new HashMap();

        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
        {
            // skip cameras with different roots
            if (cam.rootNumber != rootNumber)
                continue;

            // if a camera in this subsystem doesn't have intrinsics, this
            // graph cannot be constructed
            if (cam.cal == null)
                return null;

            // make a new camera model
            double params[] = cam.cal.getParameterization();
            ParameterizableCalibration pcal =
                cam.initializer.initializeWithParameters(cam.width, cam.height, params);

            GIntrinsicsNode intrinsics = new GIntrinsicsNode(pcal);

            gw.g.nodes.add(intrinsics);
            int intrinsicsNodeIndex = gw.g.nodes.size()-1;
            gw.cameraToIntrinsicsNodeIndex.put(cam, intrinsicsNodeIndex);

            if (verbose)
                System.out.printf("[Root %d] Added intrinsics for camera '%s'\n",
                                  rootNumber, cam.name);

            // has extrinsics
            if (cam.cameraNumber != cam.rootNumber) {
                double CameraToRoot[][] = LinAlg.xyzrpyToMatrix(cam.CameraToRootXyzrpy);

                GExtrinsicsNode extrinsics = new GExtrinsicsNode(CameraToRoot);

                gw.g.nodes.add(extrinsics);
                int extrinsicsNodeIndex = gw.g.nodes.size()-1;
                gw.cameraToExtrinsicsNodeIndex.put(cam, extrinsicsNodeIndex);

                if (verbose)
                    System.out.printf("[Root %d] Added extrinsics for camera '%s'\n",
                                      rootNumber, cam.name);
            }
        }

        for (int mosaicIndex = 0; mosaicIndex < mosaics.size(); mosaicIndex++)
        {
            CameraCalibrationSystem.MosaicWrapper mosaic = mosaics.get(mosaicIndex);

            double MosaicToRootXyzrpy[] = mosaic.MosaicToRootXyzrpys.get(rootNumber);

            // skip mosaics not rooted in this subsystem
            if (MosaicToRootXyzrpy == null)
                continue;

            double MosaicToRoot[][] = LinAlg.xyzrpyToMatrix(MosaicToRootXyzrpy);

            GExtrinsicsNode mosaicExtrinsics = new GExtrinsicsNode(MosaicToRoot);

            gw.g.nodes.add(mosaicExtrinsics);
            int mosaicExtrinsicsIndex = gw.g.nodes.size()-1;
            gw.mosaicToExtrinsicsNodeIndex.put(mosaic, mosaicExtrinsicsIndex);

            if (verbose)
                System.out.printf("[Root %d] Added extrinsics for mosaic %d\n",
                                  rootNumber, mosaicIndex);

            for (CameraCalibrationSystem.CameraWrapper cam : cameras) {
                // skip cameras with different roots
                if (cam.rootNumber != rootNumber)
                    continue;

                // get the detections for this camera
                List<TagDetection> detections = mosaic.detectionSet.get(cam.cameraNumber);

                // only add edges if there are enough constraints
                if (cal.detectionsUsable(detections) == false)
                    continue;

                ArrayList<double[]> xys_px = new ArrayList<double[]>();
                ArrayList<double[]> xyzs_m = new ArrayList<double[]>();

                for (TagDetection d : detections) {
                    xys_px.add(LinAlg.copy(d.cxy));
                    xyzs_m.add(LinAlg.copy(cal.tm.getPositionMeters(d.id)));
                }

                // get the intrinsics and extrinsics indices for this camera
                Integer cameraIntrinsicsNodeIndex = gw.cameraToIntrinsicsNodeIndex.get(cam);
                Integer cameraExtrinsicsNodeIndex = gw.cameraToExtrinsicsNodeIndex.get(cam);

                GTagEdge edge;
                if (cameraExtrinsicsNodeIndex == null)
                    edge = new GTagEdge(cameraIntrinsicsNodeIndex,
                                        mosaicExtrinsicsIndex, xys_px, xyzs_m);
                else
                    edge = new GTagEdge(cameraIntrinsicsNodeIndex, cameraExtrinsicsNodeIndex,
                                        mosaicExtrinsicsIndex, xys_px, xyzs_m);

                gw.g.edges.add(edge);

                if (verbose)
                    System.out.printf("[Root %d] Added tag edge between mosaic %d and camera '%s'"+
                                      "(camera %s extrinsics)\n",
                                      rootNumber, mosaicIndex, cam.name,
                                      (cameraExtrinsicsNodeIndex == null) ? "doesn't have" : "has");
            }
        }

        return gw;
    }

    /** Update the CameraCalibrationSystem from the graphs provided. If an
      * entry in either argument is null or the GraphStats.SPDError field is
      * true, the camera system will <b>not</b> be updated from the
      * corresponding GraphWrapper.
      */
    public void updateFromGraphs(List<GraphWrapper> graphWrappers,
                                 List<GraphStats> stats)
    {
        updateFromGraphs(cal, graphWrappers, stats, this.verbose);
    }

    /** Update the CameraCalibrationSystem from the graphs provided. If an
      * entry in either argument is null or the GraphStats.SPDError field is
      * true, the camera system will <b>not</b> be updated from the
      * corresponding GraphWrapper.
      */
    public static void updateFromGraphs(CameraCalibrationSystem cal,
                                        List<GraphWrapper> graphWrappers,
                                        List<GraphStats> stats,
                                        boolean verbose)
    {
        assert(graphWrappers.size() == stats.size());

        for (int i = 0; i < graphWrappers.size(); i++)
        {
            GraphWrapper gw = graphWrappers.get(i);
            GraphStats s = stats.get(i);

            updateFromGraph(cal, gw, s);
        }

        if (verbose)
            cal.printSystem();
    }

    /** Update the CameraCalibrationSystem from the graph provided. If either
      * argument is null or the GraphStats.SPDError field is true, the camera
      * system will <b>not</b> be updated from the corresponding GraphWrapper.
      */
    public static void updateFromGraph(CameraCalibrationSystem cal, GraphWrapper gw, GraphStats s)
    {
        updateCameraIntrinsicsFromGraph(cal, gw, s);
        updateCameraExtrinsicsFromGraph(cal, gw, s);
        updateMosaicExtrinsicsFromGraph(cal, gw, s);
    }

    /** Update the CameraCalibrationSystem's camera intrinsics from the graph
      * provided. If either * argument is null or the GraphStats.SPDError field
      * is true, the camera * system will <b>not</b> be updated from the
      * corresponding GraphWrapper.
      */
    public static void updateCameraIntrinsicsFromGraph(CameraCalibrationSystem cal, GraphWrapper gw, GraphStats s)
    {
        if (gw == null || s == null || s.SPDError == true)
            return;

        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        Set<Map.Entry<CameraCalibrationSystem.CameraWrapper,Integer>> camera_intrinsics =
            gw.cameraToIntrinsicsNodeIndex.entrySet();

        for (Map.Entry<CameraCalibrationSystem.CameraWrapper,Integer> entry : camera_intrinsics)
        {
            CameraCalibrationSystem.CameraWrapper cam = entry.getKey();
            Integer cameraIntrinsicsNodeIndex = entry.getValue();
            assert(cam != null);
            assert(cameraIntrinsicsNodeIndex != null);

            GNode node = gw.g.nodes.get(cameraIntrinsicsNodeIndex);
            assert(node != null);
            assert(node instanceof GIntrinsicsNode);
            GIntrinsicsNode intrinsics = (GIntrinsicsNode) node;

            assert(cam.cal != null);
            cam.cal.resetParameterization(intrinsics.state);
        }
    }

    /** Update the CameraCalibrationSystem's camera extrinsics from the graph
      * provided. If either * argument is null or the GraphStats.SPDError field
      * is true, the camera * system will <b>not</b> be updated from the
      * corresponding GraphWrapper.
      */
    public static void updateCameraExtrinsicsFromGraph(CameraCalibrationSystem cal, GraphWrapper gw, GraphStats s)
    {
        if (gw == null || s == null || s.SPDError == true)
            return;

        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        Set<Map.Entry<CameraCalibrationSystem.CameraWrapper,Integer>> camera_extrinsics =
            gw.cameraToExtrinsicsNodeIndex.entrySet();

        for (Map.Entry<CameraCalibrationSystem.CameraWrapper,Integer> entry : camera_extrinsics)
        {
            CameraCalibrationSystem.CameraWrapper cam = entry.getKey();
            Integer cameraExtrinsicsNodeIndex = entry.getValue();
            assert(cam != null);
            assert(cameraExtrinsicsNodeIndex != null);

            GNode node = gw.g.nodes.get(cameraExtrinsicsNodeIndex);
            assert(node != null);
            assert(node instanceof GExtrinsicsNode);
            GExtrinsicsNode extrinsics = (GExtrinsicsNode) node;

            cam.CameraToRootXyzrpy = LinAlg.copy(extrinsics.state);
        }
    }

    /** Update the CameraCalibrationSystem's mosaic extrinsics from the graph
      * provided. If either * argument is null or the GraphStats.SPDError field
      * is true, the camera * system will <b>not</b> be updated from the
      * corresponding GraphWrapper.
      */
    public static void updateMosaicExtrinsicsFromGraph(CameraCalibrationSystem cal, GraphWrapper gw, GraphStats s)
    {
        if (gw == null || s == null || s.SPDError == true)
            return;

        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        Set<Map.Entry<CameraCalibrationSystem.MosaicWrapper,Integer>> mosaic_extrinsics =
            gw.mosaicToExtrinsicsNodeIndex.entrySet();

        for (Map.Entry<CameraCalibrationSystem.MosaicWrapper,Integer> entry : mosaic_extrinsics)
        {
            CameraCalibrationSystem.MosaicWrapper mosaic = entry.getKey();
            Integer mosaicExtrinsicsNodeIndex = entry.getValue();
            assert(mosaic != null);
            assert(mosaicExtrinsicsNodeIndex != null);

            GNode node = gw.g.nodes.get(mosaicExtrinsicsNodeIndex);
            assert(node != null);
            assert(node instanceof GExtrinsicsNode);
            GExtrinsicsNode extrinsics = (GExtrinsicsNode) node;

            double MosaicToRootXyzrpy[] = mosaic.MosaicToRootXyzrpys.get(gw.rootNumber);
            assert(MosaicToRootXyzrpy != null);

            mosaic.MosaicToRootXyzrpys.put(gw.rootNumber, LinAlg.copy(extrinsics.state));
        }
    }

    public List<CameraCalibrator> createModelSelectionCalibrators(List<List<CalibrationInitializer>> initializerSets)
    {
        List<CameraCalibrator> calibrators = new ArrayList();

        outer:
        for (List<CalibrationInitializer> initializerSet : initializerSets)
        {
            CameraCalibrator calCopy = this.copy(false);

            List<CameraCalibrationSystem.CameraWrapper> cameras = calCopy.getCalRef().getCameras();
            assert(initializerSet.size() == cameras.size());

            for (int cameraIndex = 0; cameraIndex < cameras.size(); cameraIndex++)
            {
                CameraCalibrationSystem.CameraWrapper cam = cameras.get(cameraIndex);
                CalibrationInitializer initializer = initializerSet.get(cameraIndex);

                List<List<TagDetection>> usableDetections =
                    calCopy.getCalRef().getCamerasUsableDetections(cameraIndex);

                ParameterizableCalibration cal =
                    initializer.initializeWithObservations(cam.width, cam.height,
                                                           usableDetections, tm);

                if (cal == null) {
                    calibrators.add(null);
                    continue outer;
                }

                cam.initializer = initializer;
                cam.cal = cal;
            }

            calibrators.add(calCopy);
        }

        return calibrators;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // rendering code

    public void createGUI()
    {
        if (renderer != null)
            return;

        renderer = new CalibrationRenderer(this.cal, this.tf, this.metersPerTag, this.verbose);

        List<CameraCalibrationSystem.MosaicWrapper> mosaics = this.cal.getMosaics();
        for (CameraCalibrationSystem.MosaicWrapper mosaic : mosaics)
            renderer.updateMosaicDimensions(mosaic.detectionSet);

        renderer.draw(null);
    }

    /** Return a reference to the CalibrationRenderer's VisCanvas, if it exists.
      */
    public VisCanvas getVisCanvas()
    {
        if (renderer == null)
            return null;

        return renderer.vc;
    }

    /** Tell the CalibrationRenderer to draw(), if it exists.
      */
    public void draw()
    {
        draw(null);
    }

    public void draw(List<CameraCalibrator.GraphStats> stats)
    {
        if (renderer == null)
            return;

        renderer.draw(stats);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // file io code

    /** Print the camera calibration string to the terminal. Uses the
      * getCalibrationBlockString() method.
      */
    public void printCalibrationBlock(String[] commentLines)
    {
        System.out.printf(getCalibrationBlockString(commentLines));
    }

    /** Get the camera calibration string. Intrinsics that aren't initialized
      * result in a comment (thus an invalid calibration block).
      */
    public String getCalibrationBlockString(String[] commentLines)
    {
        return getCalibrationBlockString(cal, commentLines);
    }

    public static String getCalibrationBlockString(CameraCalibrationSystem cal, String[] commentLines)
    {
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();

        String str = "";

        // start block
        str += "aprilCameraCalibration {\n";
        str += "\n";

        // add all comment lines
        if (commentLines != null) {
            for (String line : commentLines)
                str += String.format("    // %s\n", line.trim().replace("\n",""));
            str += "\n";
        }

        // print name list
        String names = "    names = [";
        for (int i=0; i+1 < cameras.size(); i++) {
            CameraCalibrationSystem.CameraWrapper cam = cameras.get(i);
            names = String.format("%s %s,", names, cam.name);
        }
        names = String.format("%s %s ];\n", names, cameras.get(cameras.size()-1).name);
        str += names;

        // print cameras
        for (int i=0; i < cameras.size(); i++) {

            CameraCalibrationSystem.CameraWrapper cam = cameras.get(i);

            str += "\n";
            str += String.format("    %s {\n", cam.name);

            // make sure ParameterizableCalibration is up to date and print it
            if (cam.cal != null)
                str += cam.cal.getCalibrationString();
            else
                str += "        // Error: intrinsics not initialized\n";

            // RootToCamera
            double state[] = LinAlg.xyzrpyInverse(cam.CameraToRootXyzrpy);

            String s;
            s = String.format(  "        extrinsics {\n");
            s = String.format("%s            // Global-To-Camera coordinate transformation\n", s);
            s = String.format("%s            position = [%11.6f,%11.6f,%11.6f ];\n", s, state[0], state[1], state[2]);
            s = String.format("%s            rollpitchyaw_degrees = [%11.6f,%11.6f,%11.6f ];\n",
                              s, state[3]*180/Math.PI, state[4]*180/Math.PI, state[5]*180/Math.PI);
            s = String.format("%s        }\n", s);

            str += s;
            str += "    }\n";
        }

        // end block
        str += "}\n";

        return str;
    }

    /** Save the calibration to a file.
      */
    public synchronized void saveCalibration(String basepath, String[] commentLines)
    {
        File dir = new File(basepath);
        if (!dir.exists()) {
            boolean success = dir.mkdirs();
            if (!success) {
                System.err.printf("CameraCalibrator: Failure to create directory '%s'\n", basepath);
                return;
            }
        }

        // find unused name
        int calNum = -1;
        String calName = null;
        File outputConfigFile = null;
        do {
            calNum++;
            calName = String.format("%s/calibration%04d.config/", basepath, calNum);
            outputConfigFile = new File(calName);
        } while (outputConfigFile.exists());

        try {
            BufferedWriter outs = new BufferedWriter(new FileWriter(outputConfigFile));
            outs.write(getCalibrationBlockString(commentLines));
            outs.flush();
            outs.close();
        } catch (Exception ex) {
            System.err.printf("CameraCalibrator: Failed to output calibration to '%s'\n", calName);
            return;
        }
    }

    /** Save the calibration to a file and all images.
      */
    public synchronized void saveCalibrationAndImages(String basepath, String[] commentLines)
    {
        // create directory for image dump
        int dirNum = -1;
        String dirName = null;
        File dir = null;
        do {
            dirNum++;
            dirName = String.format("%s/imageSet%d/", basepath, dirNum);
            dir = new File(dirName);
        } while (dir.exists());

        if (dir.mkdirs() != true) {
            System.err.printf("CameraCalibrator: Failure to create directory '%s'\n", dirName);
            return;
        }

        String configpath = String.format("%s/calibration.config", dirName);
        try {
            BufferedWriter outs = new BufferedWriter(new FileWriter(new File(configpath)));
            outs.write(getCalibrationBlockString(commentLines));
            outs.flush();
            outs.close();
        } catch (Exception ex) {
            System.err.printf("CameraCalibrator: Failed to output calibration to '%s'\n", configpath);
            return;
        }

        // save images
        List<List<BufferedImage>> imageSets = this.cal.getAllImageSets();
        for (int cameraIndex = 0; cameraIndex < this.initializers.size(); cameraIndex++) {

            String subDirName = dirName;

            // make a subdirectory if we have multiple cameras
            if (this.initializers.size() > 1) {
                subDirName = String.format("%scamera%d/", dirName, cameraIndex);
                File subDir = new File(subDirName);

                if (subDir.mkdirs() != true) {
                    System.err.printf("CameraCalibrator: Failure to create subdirectory '%s'\n", subDirName);
                    return;
                }
            }

            for (int imageSetIndex = 0; imageSetIndex < imageSets.size(); imageSetIndex++) {

                List<BufferedImage> images = imageSets.get(imageSetIndex);
                BufferedImage im = images.get(cameraIndex);

                String fileName = String.format("%simage%04d.png", subDirName, imageSetIndex);
                File imageFile = new File(fileName);

                System.out.printf("Filename '%s'\n", fileName);

                try {
                    ImageIO.write(im, "png", imageFile);

                } catch (IllegalArgumentException ex) {
                    System.err.printf("CameraCalibrator: Failed to output images to '%s'\n", subDirName);
                    return;
                } catch (IOException ex) {
                    System.err.printf("CameraCalibrator: Failed to output images to '%s'\n", subDirName);
                    return;
                }
            }
        }

        System.out.printf("Successfully saved calibration and images to '%s'\n", dirName);
    }

    /** Use at your own risk! Returns a reference to the CameraCalibrationSystem in use.
      */
    public CameraCalibrationSystem getCalRef()
    {
        return cal;
    }

    public TagMosaic getTagMosaic()
    {
        return tm;
    }

    public CameraCalibrator copy(boolean gui)
    {
        CameraCalibrator rocal = new CameraCalibrator(this.initializers,
                                                      this.tf,
                                                      this.metersPerTag,
                                                      gui,
                                                      this.verbose);
        rocal.cal = this.cal.copy();
        if (rocal.renderer != null)
            rocal.renderer.replaceCalibrationSystem(rocal.cal);

        return rocal;
    }
}
