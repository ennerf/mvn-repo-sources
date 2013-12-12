package april.camera.calibrator;

import java.io.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.image.*;
import java.util.*;
import java.awt.event.*;

import javax.swing.*;
import april.camera.*;
import april.camera.tools.*;
import april.camera.models.*;
import april.jcam.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

import javax.imageio.*;

public class TagTriggerCal
{
    final int MODE_CALIBRATE = 0;
    final int MODE_RECTIFY = 1;
    int applicationMode = MODE_CALIBRATE;

    private class ProcessedFrame
    {
        public FrameData fd;
        public BufferedImage im;
        public List<TagDetection> detections;
    }

    // gui
    JFrame          jf;
    VisWorld        vw;
    VisLayer        vl;
    VisCanvas       vc;

    // camera
    String          url;
    ImageSource     isrc;
    BlockingSingleQueue<FrameData> imageQueue = new BlockingSingleQueue<FrameData>();
    BlockingSingleQueue<ProcessedFrame> processedImageQueue = new BlockingSingleQueue<ProcessedFrame>();

    CameraCalibrator calibrator;
    List<CameraCalibrator.GraphStats> lastGraphStats;
    Rasterizer rasterizer;
    double clickWidthFraction = 0.25, clickHeightFraction = 0.25;

    TagFamily tf;
    TagMosaic tm;
    TagDetector td;
    double PixelsToVis[][];
    boolean once = true;

    Integer imwidth, imheight;

    Integer minRow, minCol, maxRow, maxCol;
    int minRowUsed = -1, minColUsed = -1, maxRowUsed = -1, maxColUsed = -1;

    boolean captureNext = false;

    // save info for reinitialization
    double tagSpacingMeters;
    CalibrationInitializer initializer;

    List<Color> colorList = new ArrayList<Color>();

    public TagTriggerCal(CalibrationInitializer initializer, String url, double tagSpacingMeters)
    {
        this.tf = new Tag36h11();
        this.tm = new TagMosaic(tf, tagSpacingMeters);
        this.td = new TagDetector(tf);
        this.tagSpacingMeters = tagSpacingMeters;
        this.initializer = initializer;

        ////////////////////////////////////////
        // Calibrator setup

        calibrator = new CameraCalibrator(Arrays.asList(initializer), tf, tagSpacingMeters, true, false);

        ////////////////////////////////////////
        // GUI
        vw = new VisWorld();
        vl = new VisLayer("TagTriggerCal", vw);
        vc = new VisCanvas(vl);

        VisConsole vcon = new VisConsole(vw,vl,vc);
        vcon.drawOrder = 2000;
        VisHandler vlis = new VisHandler();
        vcon.addListener(vlis);
        vl.addEventHandler(vlis);

        jf = new JFrame("TagTriggerCal");

        JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, vc, calibrator.getVisCanvas());
        pane.setDividerLocation(0.6);
        pane.setResizeWeight(0.6);

        jf.setLayout(new BorderLayout());
        jf.add(pane, BorderLayout.CENTER);
        //jf.setSize(1200, 600);
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        device.setFullScreenWindow(jf);

        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        // colors
        while (colorList.size() < this.tf.codes.length)
        {
            List<Color> colors = Palette.friendly.listAll();
            colors.addAll(Palette.web.listAll());
            colors.addAll(Palette.vibrant.listAll());
            colors.remove(0);
            colorList.addAll(colors);
        }
        Collections.shuffle(colorList, new Random(283819));

        ////////////////////////////////////////
        // Camera setup
        try {
            isrc = ImageSource.make(url);

        } catch (IOException ex) {
            System.err.println("Exception caught while making image source: " + ex);
            ex.printStackTrace();
            System.exit(-1);
        }

        ////////////////////////////////////////
        new AcquisitionThread().start();
        new DetectionThread().start();
        new CalibrationThread().start();
    }

    private String[] getConfigCommentLines()
    {
        if (lastGraphStats == null)
            return null;

        ArrayList<String> lines = new ArrayList();

        for (CameraCalibrator.GraphStats gs : lastGraphStats)
            lines.add(String.format("MRE: %10s MSE %10s",
                                    (gs == null) ? "n/a" : String.format("%7.3f px", gs.MRE),
                                    (gs == null) ? "n/a" : String.format("%7.3f px", gs.MSE)));

        return lines.toArray(new String[0]);
    }

    class VisHandler extends VisEventAdapter implements VisConsole.Listener
    {
        /** Return true if the command was valid. **/
        public boolean consoleCommand(VisConsole vc, PrintStream out, String command)
        {
            String toks[] = command.split("\\s+");
            if (toks.length == 1 && toks[0].equals("print-calibration")) {
                calibrator.printCalibrationBlock(getConfigCommentLines());
                return true;
            }

            if (toks[0].equals("save-calibration")) {
                if (toks.length == 2)
                    calibrator.saveCalibration(toks[1], getConfigCommentLines());
                else
                    calibrator.saveCalibration("/tmp/cameraCalibration", getConfigCommentLines());

                return true;
            }

            if (toks[0].equals("save-calibration-images")) {
                if (toks.length == 2)
                    calibrator.saveCalibrationAndImages(toks[1], getConfigCommentLines());
                else
                    calibrator.saveCalibrationAndImages("/tmp/cameraCalibration", getConfigCommentLines());

                return true;
            }

            if (toks.length == 2 && toks[0].equals("mode")) {
                if (toks[1].equals("calibrate")) {
                    applicationMode = MODE_CALIBRATE;
                    return true;
                }
                else if (toks[1].equals("rectify")) {
                    applicationMode = MODE_RECTIFY;
                    return true;
                }
            }

            return false;
        }

        /** Return commands that start with prefix. (You can return
         * non-matching completions; VisConsole will filter them
         * out.) You may return null. **/
        public ArrayList<String> consoleCompletions(VisConsole vc, String prefix)
        {
            return new ArrayList(Arrays.asList("print-calibration", "save-calibration /tmp/cameraCalibration", "save-calibration-images /tmp/cameraCalibration", "mode calibrate", "mode rectify"));
        }


        public boolean keyPressed(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, KeyEvent e)
        {
            char c = e.getKeyChar();
            int code = e.getKeyCode();

            int mods = e.getModifiersEx();
            boolean shift = (mods&KeyEvent.SHIFT_DOWN_MASK) > 0;
            boolean ctrl = (mods&KeyEvent.CTRL_DOWN_MASK) > 0;
            boolean alt = (mods&KeyEvent.ALT_DOWN_MASK) > 0;

            if (code == KeyEvent.VK_SPACE) {
                // Manual Capture
                captureNext = true;
                return true;
            }

            if (code == KeyEvent.VK_C) {
                applicationMode = MODE_CALIBRATE;
                return true;
            }

            if (code == KeyEvent.VK_R) {
                applicationMode = MODE_RECTIFY;
                return true;
            }

            return false;
        }

        public boolean mouseClicked(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
        {
            int x = e.getX();
            int y = e.getY();

            if (x >= 0 && x < vc.getWidth()*clickWidthFraction &&
                y >= 0 && y < vc.getHeight()*clickHeightFraction)
            {
                if (applicationMode == MODE_CALIBRATE) {
                    applicationMode = MODE_RECTIFY;
                    return true;
                }
                else if (applicationMode == MODE_RECTIFY) {
                    applicationMode = MODE_CALIBRATE;
                    return true;
                }
            }

            return false;
        }
    }

    class AcquisitionThread extends Thread
    {
        public AcquisitionThread()
        {
            this.setName("TagTriggerCal AcquisitionThread");
        }

        public void run()
        {
            try {
                this.setPriority(Thread.MAX_PRIORITY);
            } catch (IllegalArgumentException e) {
                System.err.println("Could not set thread priority. Priority out of range.");
            } catch (SecurityException e) {
                System.err.println("Could not set thread priority. Not permitted.");
            }

            isrc.start();
            while (true) {
                FrameData frmd = isrc.getFrame();

                if (frmd == null)
                    break;

                imageQueue.put(frmd);
            }

            System.out.println("Out of frames!");
        }
    }

    class DetectionThread extends Thread
    {
        public DetectionThread()
        {
            this.setName("TagTriggerCal DetectionThread");
        }

        public void run()
        {
            while (true) {

                FrameData frmd = imageQueue.get();

                BufferedImage im = ImageConvert.convertToImage(frmd);
                if (imwidth == null || imheight == null) {
                    imwidth = im.getWidth();
                    imheight = im.getHeight();
                }
                assert(imwidth == im.getWidth() && imheight == im.getHeight());

                ProcessedFrame pf = new ProcessedFrame();
                pf.fd = frmd;
                pf.im = im;

                pf.detections = td.process(im, new double[] {im.getWidth()/2.0, im.getHeight()/2.0});

                if (applicationMode == MODE_CALIBRATE)
                    draw(pf.im, pf.detections);

                processedImageQueue.put(pf);
            }
        }

        void draw(BufferedImage im, List<TagDetection> detections)
        {
            VisWorld.Buffer vb;

            PixelsToVis = getPlottingTransformation(im, true);

            ////////////////////////////////////////
            // camera image
            vb = vw.getBuffer("Camera");
            vb.setDrawOrder(0);
            vb.addBack(new VisLighting(false,
                                       new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                                        new VisChain(PixelsToVis,
                                                                     new VzImage(new VisTexture(im,
                                                                                                VisTexture.NO_MAG_FILTER |
                                                                                                VisTexture.NO_MIN_FILTER |
                                                                                                VisTexture.NO_REPEAT),
                                                                                 0)))));
            vb.swap();

            vb = vw.getBuffer("HUD");
            vb.setDrawOrder(1000);
            vb.addBack(new VisDepthTest(false,
                                        new VisPixCoords(VisPixCoords.ORIGIN.TOP,
                                                         new VzText(VzText.ANCHOR.TOP,
                                                                    "<<dropshadow=#FF000000,"+
                                                                    "monospaced-12-bold,white>>"+
                                                                    "Images are shown in grayscale and mirrored for display purposes"))));
            if (calibrator.getCalRef().getMosaics().size() < 1) {
                vb.addBack(new VisDepthTest(false,
                                            new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VzText(VzText.ANCHOR.CENTER,
                                                       "<<dropshadow=#AA000000>>"+
                                                       "<<monospaced-20-bold,green>>"+
                                                       "Cover and release first tag to trigger image acquisition"))));
            }
            vb.swap();

            vb = vw.getBuffer("Detections");
            vb.setDrawOrder(10);
            for (TagDetection d : detections) {
                Color color = colorList.get(d.id);

                ArrayList<double[]> quad = new ArrayList<double[]>();
                quad.add(d.interpolate(-1,-1));
                quad.add(d.interpolate( 1,-1));
                quad.add(d.interpolate( 1, 1));
                quad.add(d.interpolate(-1, 1));

                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VisChain(PixelsToVis,
                                                         new VzMesh(new VisVertexData(quad),
                                                                    VzMesh.QUADS,
                                                                    new VzMesh.Style(color)))));
            }
            vb.swap();
        }
    }

    class CalibrationThread extends Thread
    {
        boolean capturedLast = true;

        public CalibrationThread()
        {
            this.setName("TagTriggerCal CalibrationThread");
        }

        public void run()
        {
            long lastutime = 0;

            while (true) {

                ProcessedFrame pf = processedImageQueue.get();

                calibrationUpdate(pf);
                rectificationUpdate(pf.im);
            }
        }

        void calibrationUpdate(ProcessedFrame pf)
        {
            if (applicationMode != MODE_CALIBRATE) {
                return;
            }

            updateMosaic(pf.detections);

            if (maxCol == null || minCol == null || maxRow == null || minRow == null)
                return;

            if (maxCol - minCol + 1 < 5 || maxRow - minRow + 1 < 5)
                return;

            boolean captureThis = false;
            int captureid = tm.getID(minCol, minRow);
            for (TagDetection d : pf.detections)
                if (d.id == captureid)
                    captureThis = true;

            if (capturedLast || !captureThis) {
                if (!captureThis)
                    capturedLast = false;
                return;
            }

            addImage(pf.im, pf.detections);
            updateRectifiedBorder();

            new FlashThread().start();

            capturedLast = true;

            vw.getBuffer("HUD").swap();
        }

        void rectificationUpdate(BufferedImage im)
        {
            if (applicationMode != MODE_RECTIFY) {
                vw.getBuffer("Rectified").swap();
                vw.getBuffer("Distorted outline").swap();
                vw.getBuffer("Finished").swap();
                rasterizer = null;
                return;
            }

            ParameterizableCalibration curcal = null, cal = null;
            double params[] = null;
            if (calibrator != null) curcal = calibrator.getCalRef().getCameras().get(0).cal;
            if (curcal != null)     params = curcal.getParameterization();
            if (params != null)     cal    = initializer.initializeWithParameters(imwidth, imheight, params);

            if (cal == null) {
                vw.getBuffer("Rectified").swap();
                vw.getBuffer("Distorted outline").swap();
                applicationMode = MODE_CALIBRATE;
                return;
            }

            if (rasterizer == null) {
                View rectifiedView = new MaxRectifiedView(cal);

                // rescale if necessary
                int maxdimension = 800;
                int maxOutputDimension = Math.max(rectifiedView.getWidth(), rectifiedView.getHeight());
                if (maxOutputDimension > maxdimension)
                    rectifiedView = new ScaledView(((double) maxdimension) / maxOutputDimension,
                                                   rectifiedView);

                rasterizer = new BilinearRasterizer(cal, rectifiedView);

                vw.getBuffer("Camera").swap();
                vw.getBuffer("HUD").swap();
                vw.getBuffer("Shade").swap();
                vw.getBuffer("SuggestedTags").swap();
                vw.getBuffer("Detections").swap();
                vw.getBuffer("Suggestion HUD").swap();
                vw.getBuffer("Selected-best-color").swap();
                vw.getBuffer("Error meter").swap();
                vw.getBuffer("Rectified outline").swap();
                vw.getBuffer("Flash").swap();
            }

            BufferedImage rectified = rasterizer.rectifyImage(im);

            VisWorld.Buffer vb;
            vb = vw.getBuffer("Rectified");
            vb.setDrawOrder(1000);
            vb.addBack(new VisLighting(false,
                                       new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                                        new VisChain(getPlottingTransformation(rectified, false),
                                                                     new VzImage(new VisTexture(rectified,
                                                                                                VisTexture.NO_MAG_FILTER |
                                                                                                VisTexture.NO_MIN_FILTER |
                                                                                                VisTexture.NO_REPEAT),
                                                                                 0)))));
            vb.swap();

            vb = vw.getBuffer("Distorted outline");
            vb.setDrawOrder(1010);
            {
                double h = vc.getHeight()*0.25;
                double scale = h / imheight;

                clickHeightFraction = 0.25;
                clickWidthFraction  = 0.25*(imwidth/imheight)/(vc.getWidth()/vc.getHeight());

                //System.out.printf("im %4d %4d vc %4d %4d percent %5.2f %5.2f\n",
                //                  imwidth, imheight, vc.getWidth(), vc.getHeight(),
                //                  clickWidthFraction, clickHeightFraction);

                ArrayList<double[]> border = new ArrayList<double[]>();
                border.add(new double[] {       0,        0 });
                border.add(new double[] { imwidth,        0 });
                border.add(new double[] { imwidth, imheight });
                border.add(new double[] {       0, imheight });

                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                            new VisChain(LinAlg.scale(scale, -scale, 1),
                                                         new VzLines(new VisVertexData(border),
                                                                     VzLines.LINE_LOOP,
                                                                     new VzLines.Style(Color.white, 2)))));
            }
            vb.swap();
        }
    }

    private void updateRectifiedBorder()
    {
        VisWorld.Buffer vb;

        vb = vw.getBuffer("Rectified outline");
        vb.setDrawOrder(1000);

        ParameterizableCalibration curcal = null, cal = null;
        double params[] = null;
        if (calibrator != null) curcal = calibrator.getCalRef().getCameras().get(0).cal;
        if (curcal != null)     params = curcal.getParameterization();
        if (params != null)     cal    = initializer.initializeWithParameters(imwidth, imheight, params);

        if (cal != null) {
            ArrayList<double[]> border = MaxGrownInscribedRectifiedView.computeRectifiedBorder(cal);

            double minx = border.get(0)[0];
            double miny = border.get(0)[1];
            double maxx = minx, maxy = miny;
            for (double xy[] : border) {
                minx = Math.min(minx, xy[0]);
                maxx = Math.max(maxx, xy[0]);
                miny = Math.min(miny, xy[1]);
                maxy = Math.max(maxy, xy[1]);
            }

            double h = vc.getHeight()*0.25;
            double scale = h / (maxy - miny);

            clickHeightFraction = 0.25;
            clickWidthFraction  = 0.25*((maxx-minx)/(maxy-miny))/(vc.getWidth()/vc.getHeight());

            //System.out.printf("im %4d %4d vc %4d %4d percent %5.2f %5.2f :: %6.1f %6.1f %6.1f %6.1f\n",
            //                  imwidth, imheight, vc.getWidth(), vc.getHeight(),
            //                  clickWidthFraction, clickHeightFraction,
            //                  minx, maxx, miny, maxy);

            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                        new VisChain(LinAlg.scale(scale, -scale, 1),
                                                     LinAlg.translate(-minx, -miny, 0),
                                                     new VzLines(new VisVertexData(border),
                                                                 VzLines.LINE_LOOP,
                                                                 new VzLines.Style(Color.white, 2)))));
        }

        vb.swap();
    }

    private void addImage(BufferedImage im, List<TagDetection> detections)
    {
        calibrator.addOneImageSet(Arrays.asList(im),
                                  Arrays.asList(detections));

        List<CameraCalibrator.GraphStats> stats =
            calibrator.iterateUntilConvergenceWithReinitalization(1.0, 0.01, 3, 50);

        calibrator.draw(stats);

        lastGraphStats = stats;
    }

    private double[][] getPlottingTransformation(BufferedImage im, boolean mirror)
    {
        double imaspect = ((double) im.getWidth()) / im.getHeight();
        double visaspect = ((double) vc.getWidth()) / vc.getHeight();

        double h = 0;
        double w = 0;

        if (imaspect > visaspect) {
            w = vc.getWidth();
            h = w / imaspect;
        } else {
            h = vc.getHeight();
            w = h*imaspect;
        }

        double T[][] = LinAlg.multiplyMany(LinAlg.translate(-w/2, -h/2, 0),
                                           CameraMath.makeVisPlottingTransform(im.getWidth(), im.getHeight(),
                                                                               new double[] {   0,   0 },
                                                                               new double[] {   w,   h },
                                                                               true));

        if (mirror)
            T = LinAlg.multiplyMany(T,
                                    LinAlg.translate(im.getWidth(), 0, 0),
                                    LinAlg.scale(-1, 1, 1));

        return T;
    }

    private void updateMosaic(List<TagDetection> detections)
    {
        if (detections == null || detections.size() == 0)
            return;

        // update the min/max column/row
        if (minRow == null || maxRow == null || minCol == null || maxCol == null) {
            TagDetection d = detections.get(0);
            minRow = tm.getRow(d.id);
            maxRow = minRow;
            minCol = tm.getColumn(d.id);
            maxCol = minCol;
        }

        for (TagDetection d : detections) {
            int row = tm.getRow(d.id);
            int col = tm.getColumn(d.id);

            minRow = Math.min(minRow, row);
            minCol = Math.min(minCol, col);
            maxRow = Math.max(maxRow, row);
            maxCol = Math.max(maxCol, col);
        }
    }

    private class FlashThread extends Thread
    {
        private FlashThread()
        {
            this.setName("TagTriggerCal FlashThread");
        }

        public void run()
        {
            VisWorld.Buffer vb;

            vb = vw.getBuffer("Flash");
            vb.setDrawOrder(100);

            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                        new VzRectangle(vc.getWidth(), vc.getHeight(),
                                                        new VzMesh.Style(Color.white))));
            vb.swap();

            for (int i = 0; i < 18; i++) {
                int alpha = (int) (Math.exp(-Math.pow(i*0.15 - 0.35, 2)) * 255);
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VzRectangle(vc.getWidth(), vc.getHeight(),
                                                            new VzMesh.Style(new Color(255, 255, 255, alpha)))));
                vb.swap();

                TimeUtil.sleep(15);
            }

            vb.swap();
        }
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('u',"url","","Camera URL");
        opts.addString('c',"class","april.camera.models.AngularPolynomialInitializer","Calibration model initializer class name");
        opts.addString('p',"parameterString","kclength=4","Initializer parameter string (comma separated, key=value pairs)");
        opts.addDouble('m',"spacing",0.0381,"Spacing between tags (meters)");
        opts.addBoolean('\0',"debug-gui",false,"Display additional debugging information");

        if (!opts.parse(args)) {
            System.out.println("Option error: "+opts.getReason());
	    }

        String url             = opts.getString("url");
        String initclass       = opts.getString("class");
        String parameterString = opts.getString("parameterString");
        double spacing         = opts.getDouble("spacing");

        if (opts.getBoolean("help") || url.isEmpty()) {
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        Object obj = ReflectUtil.createObject(initclass, parameterString);
        assert(obj != null);
        assert(obj instanceof CalibrationInitializer);
        CalibrationInitializer initializer = (CalibrationInitializer) obj;

        new TagTriggerCal(initializer, url, spacing);
    }
}

