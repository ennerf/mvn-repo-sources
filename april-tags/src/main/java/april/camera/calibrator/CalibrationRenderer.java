package april.camera.calibrator;

import java.awt.image.*;
import java.awt.Color;
import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.tag.*;
import april.vis.*;

public class CalibrationRenderer
{
    public boolean verbose = false;

    CameraCalibrationSystem cal;

    VisWorld worlds[];
    VisLayer layers[];
    VisCanvas vc;
    int numUsedLayers;
    boolean gui;

    TagFamily tf;
    TagMosaic tm;
    double metersPerTag;

    Integer minCol, maxCol, minRow, maxRow;

    double Tvis[][] = new double[][] { {  0,  0,  1,  0 },
                                       { -1,  0,  0,  0 } ,
                                       {  0, -1,  0,  0 } ,
                                       {  0,  0,  0,  1 } };

    public CalibrationRenderer(CameraCalibrationSystem cal,
                               TagFamily tf, double metersPerTag, boolean verbose)
    {
        this.verbose = verbose;
        this.tf = tf;
        this.tm = new TagMosaic(tf, metersPerTag);
        this.metersPerTag = metersPerTag;
        this.gui = gui;
        this.cal = cal;

        vc = new VisCanvas();
        worlds = new VisWorld[this.cal.getCameras().size()];
        layers = new VisLayer[this.cal.getCameras().size()];

        for (int i = 0; i < this.cal.getCameras().size(); i++) {
            String name = String.format("Subsystem %d", i);
            VisWorld vw = new VisWorld();
            VisLayer vl = new VisLayer(name, vw);

            vl.layerManager = new GridLayerManager(0, i, 1, this.cal.getCameras().size());

            int gray = 20 + 10*i;
            vl.backgroundColor = new Color(gray, gray, gray);

            DefaultCameraManager cameraManager = (DefaultCameraManager) vl.cameraManager;
            cameraManager.interfaceMode = 2.5;

            VisCameraManager.CameraPosition pos = cameraManager.getCameraTarget();
            pos.eye    = new double[] { 0.1, 0.0, 2.0 };
            pos.lookat = new double[] { 0.1, 0.0, 0.0 };
            pos.up     = new double[] { 1.0, 0.0, 0.0 };
            pos.perspectiveness = 0;
            //pos.eye    = new double[] { 1.2, 0.0, 0.5 };
            //pos.lookat = new double[] { 0.2, 0.0, 0.0 };
            //pos.up     = new double[] {-0.4, 0.0, 0.9 };
            cameraManager.goUI(pos);
            cameraManager.setDefaultPosition(pos.eye, pos.lookat, pos.up);

            VzGrid.addGrid(vw, new VzGrid(new VzLines.Style(new Color(128, 128, 128, 128), 1)));
            vw.getBuffer("grid").setDrawOrder(-10001);
            vw.getBuffer("grid-overlay").setDrawOrder(-10000);

            worlds[i] = vw;
            layers[i] = vl;

            vc.addLayer(vl);
        }

        numUsedLayers = this.cal.getCameras().size();
    }

    ////////////////////////////////////////////////////////////////////////////////
    public void updateMosaicDimensions(List<List<TagDetection>> newDetections)
    {
        for (List<TagDetection> detections : newDetections) {
            // update observed mosaic bounds
            for (TagDetection d : detections) {
                int col = this.tm.getColumn(d.id);
                int row = this.tm.getRow(d.id);

                if (minCol == null || col < minCol) minCol = col;
                if (maxCol == null || col > maxCol) maxCol = col;
                if (minRow == null || row < minRow) minRow = row;
                if (maxRow == null || row > maxRow) maxRow = row;
            }
        }
    }

    public void replaceCalibrationSystem(CameraCalibrationSystem newcal)
    {
        assert(this.cal.getCameras().size() == newcal.getCameras().size());
        this.cal = newcal;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // rendering code

    public VisCanvas getVisCanvas()
    {
        return vc;
    }

    public void draw(List<CameraCalibrator.GraphStats> stats)
    {
        List<CameraCalibrationSystem.CameraWrapper> cameras = cal.getCameras();
        List<CameraCalibrationSystem.MosaicWrapper> mosaics = cal.getMosaics();

        drawSubsystems(cameras, mosaics);
        drawHUD(cameras, mosaics, stats);
        updateLayerManagers(cameras);
    }

    private void drawSubsystems(List<CameraCalibrationSystem.CameraWrapper> cameras,
                                List<CameraCalibrationSystem.MosaicWrapper> mosaics)
    {
        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
        {
            VisWorld vw = worlds[cam.rootNumber];
            VisWorld.Buffer vb = vw.getBuffer("Cameras");

            double CameraToRoot[][] = LinAlg.xyzrpyToMatrix(cam.CameraToRootXyzrpy);

            vb.addBack(new VisChain(Tvis,
                                    CameraToRoot,
                                    LinAlg.scale(0.05, 0.05, 0.05),
                                    new VzAxes()));
        }

        // compute mosaic border
        double XY0[] = new double[2];
        double XY1[] = new double[2];
        if (minRow != null && maxRow != null && minCol != null && maxCol != null) {
            XY0 = this.tm.getPositionMeters(minCol - 0.5, minRow - 0.5);
            XY1 = this.tm.getPositionMeters(maxCol + 0.5, maxRow + 0.5);
        }

        for (int mosaicIndex = 0; mosaicIndex < mosaics.size(); mosaicIndex++)
        {
            CameraCalibrationSystem.MosaicWrapper mosaic = mosaics.get(mosaicIndex);

            Integer rootNumbers[] = mosaic.MosaicToRootXyzrpys.keySet().toArray(new Integer[0]);

            Set<Integer> tagIDSet = new TreeSet();
            for (List<TagDetection> detections : mosaic.detectionSet)
                for (TagDetection d : detections)
                    tagIDSet.add(d.id);

            ArrayList<double[]> tagPoints_mosaic = new ArrayList();
            for (int id : tagIDSet)
                tagPoints_mosaic.add(this.tm.getPositionMeters(id));

            for (int root : rootNumbers)
            {
                VisWorld vw = worlds[root];
                VisWorld.Buffer vb = vw.getBuffer("Mosaics");

                double MosaicToRootXyzrpy[] = mosaic.MosaicToRootXyzrpys.get(root);
                assert(MosaicToRootXyzrpy != null);

                double MosaicToRoot[][] = LinAlg.xyzrpyToMatrix(MosaicToRootXyzrpy);

                Color c = ColorUtil.seededColor(mosaicIndex);
                vb.addBack(new VisChain(Tvis,
                                        MosaicToRoot,
                                        LinAlg.translate((XY0[0]+XY1[0])/2.0, (XY0[1]+XY1[1])/2.0, 0),
                                        new VzRectangle(XY1[0] - XY0[0],
                                                        XY1[1] - XY0[1],
                                                        new VzLines.Style(c, 2))));

                vb.addBack(new VisChain(Tvis,
                                        MosaicToRoot,
                                        new VzPoints(new VisVertexData(tagPoints_mosaic),
                                                     new VzPoints.Style(c, 1))));
            }
        }

        // swap now in case the buffer was used multiple times
        for (VisWorld vw : worlds) {
            vw.getBuffer("Cameras").swap();
            vw.getBuffer("Mosaics").swap();
        }
    }

    private void drawHUD(List<CameraCalibrationSystem.CameraWrapper> cameras,
                         List<CameraCalibrationSystem.MosaicWrapper> mosaics,
                         List<CameraCalibrator.GraphStats> stats)
    {
        drawDistortionCurves(cameras, mosaics);
        drawHUDText(cameras, mosaics, stats);
    }

    private void drawDistortionCurves(List<CameraCalibrationSystem.CameraWrapper> cameras,
                                      List<CameraCalibrationSystem.MosaicWrapper> mosaics)
    {
        HashMap<VisWorld.Buffer,Integer> bufToCameraCount = new HashMap<VisWorld.Buffer,Integer>();

        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
        {
            VisWorld vw = worlds[cam.rootNumber];
            VisWorld.Buffer vb = vw.getBuffer("Distortion");

            Integer idx = bufToCameraCount.get(vb);
            if (idx == null)
                idx = 0;
            bufToCameraCount.put(vb, idx+1);

            VisChain bgchain = new VisChain();
            VisChain mgchain = new VisChain();
            VisChain fgchain = new VisChain();

            View cal        = cam.cal;
            if (cal == null)
                continue;

            double K[][]    = cal.copyIntrinsics();
            double Kinv[][] = LinAlg.inverse(K);
            int width       = cal.getWidth();
            int height      = cal.getHeight();

            // theta, not radius
            ArrayList<double[]> points = new ArrayList<double[]>();
            for (double theta = 0; theta < Math.PI; theta += Math.toRadians(1)) {

                double r = Math.sin(theta);

                double x = r;
                double y = 0;
                double z = Math.cos(theta);

                double xyz_r[] = new double[] { x, y, z };

                double xy_dp[] = cal.rayToPixels(xyz_r);

                double xyz_d[] = CameraMath.rayToSphere(CameraMath.pinholeTransform(Kinv, xy_dp));

                double newr = xyz_d[0];
                double newtheta = Math.atan2(newr, xyz_d[2]);

                points.add(new double[] { theta, newtheta });
            }

            bgchain.add(new VisChain(LinAlg.translate(50, 50 + 100*idx, 0),
                                     new VzRectangle(100, 100,
                                                     new VzMesh.Style(new Color(10*idx, 10*idx, 10*idx)),
                                                     new VzLines.Style(Color.white, 1))),
                        new VisChain(LinAlg.translate(0, 100*idx, 0),
                                     new VzLines(new VisVertexData(new double[][] { {  0,   0}, {100, 100} }),
                                                 VzLines.LINE_STRIP,
                                                 new VzLines.Style(new Color(255, 255, 255, 128), 1))));
            mgchain.add(new VisChain(LinAlg.translate(0, 100*idx, 0),
                                     LinAlg.scale(100 / Math.PI, 100 / Math.PI, 1),
                                     new VzLines(new VisVertexData(points),
                                                 VzLines.LINE_STRIP,
                                                 new VzLines.Style(Color.red, 1))));
            fgchain.add(new VisChain(LinAlg.translate(35, 100*idx, 0),
                                     new VzText(VzText.ANCHOR.BOTTOM_LEFT, "<<monospaced-10>>theta rect")),
                        new VisChain(LinAlg.translate(0, 35+100*idx, 0),
                                     LinAlg.rotateZ(Math.PI/2),
                                     new VzText(VzText.ANCHOR.TOP_LEFT, "<<monospaced-10>>theta dist")));

            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT, bgchain, mgchain, fgchain));
        }

        for (VisWorld.Buffer vb : bufToCameraCount.keySet())
            vb.swap();
    }

    private void drawHUDText(List<CameraCalibrationSystem.CameraWrapper> cameras,
                             List<CameraCalibrationSystem.MosaicWrapper> mosaics,
                             List<CameraCalibrator.GraphStats> stats)
    {
        if (stats == null)
            return;

        for (CameraCalibrator.GraphStats gs : stats)
        {
            if (gs == null)
                continue;

            VisWorld vw = worlds[gs.rootNumber];
            VisWorld.Buffer vb = vw.getBuffer("HUD Text");

            String font = "<<monospaced-12,left,white>>";
            String str = String.format("%sMean reprojection error:         %6.3f\n" +
                                       "%sMean-squared reprojection error: %6.3f\n" +
                                       "%sMax reprojection error:          %6.3f",
                                       font, gs.MRE,
                                       font, gs.MSE,
                                       font, gs.MaxRE);
            if (gs.MaxERE != null)
                str = String.format("%s\nMax expected reprojection error: %6.3f", str, gs.MaxERE);

            if (gs.SPDError)
                str = "<<monospaced-12,left,red>>SPD error for graph";

            vb.addBack(new VisLighting(false, new VisPixCoords(VisPixCoords.ORIGIN.TOP_RIGHT,
                                                               new VzText(VzText.ANCHOR.TOP_RIGHT, str))));
            vb.swap();
        }
    }

    private void updateLayerManagers(List<CameraCalibrationSystem.CameraWrapper> cameras)
    {
        int usedLayers = 0;
        for (CameraCalibrationSystem.CameraWrapper cam : cameras)
            if (cam.cameraNumber == cam.rootNumber)
                usedLayers++;

        // did the number of layers in use change? if so, update layer managers
        if (usedLayers != numUsedLayers)
        {
            int usedSoFar = 0;
            for (CameraCalibrationSystem.CameraWrapper cam : cameras)
            {
                VisLayer vl = layers[cam.cameraNumber];

                // give in-use layers a real grid position
                if (cam.cameraNumber == cam.rootNumber) {
                    vl.layerManager = new GridLayerManager(0, usedSoFar,
                                                           1, usedLayers);
                    usedSoFar++;
                }
                // give unused layers a "hidden" layer position
                else {
                    double pos[] = new double[] { 1, 1, 0, 0 };
                    vl.layerManager = new DefaultLayerManager(vl, pos);
                }
            }

            numUsedLayers = usedLayers;
        }
    }
}

