package april.camera.calibrator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.*;
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

public class AprilCal
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

    // debug gui
    VisLayer vl2 = null;

    // Debug state
    ArrayList<SuggestedImage> ranked;

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

    Random r = new Random();//(1461234L);
    SyntheticTagMosaicImageGenerator simgen;
    SuggestedImage suggestion = null;
    int suggestionNumber = 0;

    // Score FrameScorer
    double minFSScore = Double.MAX_VALUE;
    long progressTimeoutUtime = Long.MAX_VALUE;
    boolean clearScoreState = false;
    int debugCT = 0;

    // tracks frame count, frame score
    ArrayList<double[]> selectedScores = new ArrayList();

    // Score Pix
    List<ScoredImage> pixScoreImages = new ArrayList();
    List<ScoredImage> fsScoreImages = new ArrayList();
    boolean waitingForBest = false;
    long startedWaiting = 0;
    double waitTime = 3.5; // seconds
    Integer minRow, minCol, maxRow, maxCol;
    int minRowUsed = -1, minColUsed = -1, maxRowUsed = -1, maxColUsed = -1;

    boolean captureNext = false;

    // save info for reinitialization
    double tagSpacingMeters;
    CalibrationInitializer initializer;
    List<List<BufferedImage>> imagesSet = new ArrayList<List<BufferedImage>>();
    List<List<List<TagDetection>>> detsSet = new ArrayList<List<List<TagDetection>>>();

    final double stoppingAccuracy;

    double bestScore = Double.POSITIVE_INFINITY;
    double currentScore = Double.POSITIVE_INFINITY;
    BufferedImage bestInitImage;
    List<TagDetection> bestInitDetections;
    boolean bestInitUpdated = false;

    List<Color> colorList = new ArrayList<Color>();

    static volatile boolean running = true;
    final AcquisitionThread   acquisitionThread;
    final DetectionThread     detectionThread;
    final CalibrationThread   calibrationThread;

    static class SuggestedImage
    {
        // Assumed to be in the actual image
        ArrayList<TagDetection> detections;

        // could be null
        BufferedImage im;
        double xyzrpy[];
        double xyzrpy_cen[];

        double score;
    }

    class ScoredImage implements Comparable<ScoredImage>
    {
        public BufferedImage        im;
        public List<TagDetection>   detections;
        public double               meandistance;

        public ScoredImage(BufferedImage im, List<TagDetection> detections, double meandistance)
        {
            this.im           = im;
            this.detections   = detections;
            this.meandistance = meandistance;
        }

        public int compareTo(ScoredImage si)
        {
            return Double.compare(this.meandistance, si.meandistance);
        }
    }

    public AprilCal(CalibrationInitializer initializer, String url, double tagSpacingMeters, double stoppingAccuracy,
                    boolean debugGUI, boolean dictionaryGUI, ArrayList<BufferedImage> debugSeedImages)
    {
        this.tf = new Tag36h11();
        this.tm = new TagMosaic(tf, tagSpacingMeters);
        this.td = new TagDetector(tf);
        this.tagSpacingMeters  = tagSpacingMeters;
        this.initializer       = initializer;
        this.stoppingAccuracy  = stoppingAccuracy;

        ////////////////////////////////////////
        // GUI
        vw = new VisWorld();
        vl = new VisLayer("AprilCal", vw);
        vc = new VisCanvas(vl);

        VisConsole vcon = new VisConsole(vw,vl,vc);
        vcon.drawOrder = 2000;
        VisHandler vlis = new VisHandler();
        vcon.addListener(vlis);
        vl.addEventHandler(vlis);

        vl.setBufferEnabled("fs-error-score", false);
        vl.setBufferEnabled("fs-error-meter", false);

        jf = new JFrame("AprilCal");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
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
        // Calibrator setup

        calibrator = new CameraCalibrator(Arrays.asList(initializer), tf, tagSpacingMeters, debugGUI, false);

        if (debugGUI) {
            JFrame jf2 = new JFrame("Debug");
            jf2.setLayout(new BorderLayout());
            jf2.add(calibrator.getVisCanvas(), BorderLayout.CENTER);
            jf2.setSize(720, 480);
            jf2.setLocation(1200,0);
            jf2.setVisible(true);
        }

        if (dictionaryGUI)
            new DebugAprilCalDictionary(this);

        if (debugSeedImages != null && debugSeedImages.size() > 0 ) {
            for (BufferedImage im : debugSeedImages)
                addImage(im, td.process(im, new double[] {im.getWidth()/2.0, im.getHeight()/2.0}));
            System.out.printf("Loaded %d images from seed \n", debugSeedImages.size());
        }

        ////////////////////////////////////////
        acquisitionThread = new AcquisitionThread();
        detectionThread   = new DetectionThread();
        calibrationThread = new CalibrationThread();

        acquisitionThread.start();
        detectionThread.start();
        calibrationThread.start();

        ////////////////////////////////////////

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                this.setName("Shutdown hook");

                running = false;

                try {
                    acquisitionThread.join();
                } catch (InterruptedException ex) {
                }
            }
        });
    }

    private String[] getConfigCommentLines()
    {
        if (lastGraphStats == null)
            return null;

        ArrayList<String> lines = new ArrayList();

        for (CameraCalibrator.GraphStats gs : lastGraphStats)
            lines.add(String.format("MRE: %10s MSE %10s MaxRE %10s MaxERE %10s",
                                    (gs == null) ? "n/a" : String.format("%7.3f px", gs.MRE),
                                    (gs == null) ? "n/a" : String.format("%7.3f px", gs.MSE),
                                    (gs == null) ? "n/a" : String.format("%7.3f px", gs.MaxRE),
                                    (gs == null || gs.MaxERE == null) ? "n/a" : String.format("%7.3f px", gs.MaxERE)));

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

            if (toks[0].equals("show-debug-gui")) {
                calibrator.createGUI();

                JFrame jf2 = new JFrame("Debug");
                jf2.setLayout(new BorderLayout());
                jf2.add(calibrator.getVisCanvas(), BorderLayout.CENTER);
                jf2.setSize(720, 480);
                jf2.setLocation(1200,0);
                jf2.setVisible(true);

                return true;
            }

            if (toks[0].equals("model-selection")) {
                if (toks.length == 2)
                    selectModelAndSave(toks[1]);
                else
                    selectModelAndSave("/tmp/cameraCalibration");

                return true;
            }

            return false;
        }

        /** Return commands that start with prefix. (You can return
         * non-matching completions; VisConsole will filter them
         * out.) You may return null. **/
        public ArrayList<String> consoleCompletions(VisConsole vc, String prefix)
        {
            return new ArrayList(Arrays.asList("print-calibration", "save-calibration /tmp/cameraCalibration", "save-calibration-images /tmp/cameraCalibration", "mode calibrate", "mode rectify", "show-debug-gui", "model-selection"));
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
            this.setName("AprilCal AcquisitionThread");
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
            while (running) {
                FrameData frmd = isrc.getFrame();

                if (frmd == null)
                    break;

                imageQueue.put(frmd);
            }
            isrc.stop();
            isrc.close();
        }
    }

    private int getDynamicFontSize(double scale)
    {
        double heightSize = vc.getHeight() * 0.025 * scale;
        double widthSize  = vc.getWidth()  * 0.017 * scale;

        return (int) Math.min(heightSize, widthSize);
    }

    class DetectionThread extends Thread
    {
        public DetectionThread()
        {
            this.setName("AprilCal DetectionThread");
        }

        public void run()
        {
            long lastutime = 0;

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
            {
                BufferedImage gray = ImageConvert.convertImage(im, BufferedImage.TYPE_BYTE_GRAY);
                vb.addBack(new VisLighting(false,
                                           new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                                            new VisChain(PixelsToVis,
                                                                         new VzImage(new VisTexture(gray,
                                                                                                    VisTexture.NO_MAG_FILTER |
                                                                                                    VisTexture.NO_MIN_FILTER |
                                                                                                    VisTexture.NO_REPEAT),
                                                                                     0)))));
            }
            vb.swap();

            vb = vw.getBuffer("HUD");
            vb.setDrawOrder(1000);
            vb.addBack(new VisDepthTest(false,
                                        new VisPixCoords(VisPixCoords.ORIGIN.TOP,
                                                         new VzText(VzText.ANCHOR.TOP,
                                                                    "<<dropshadow=#FF000000,"+
                                                                    "monospaced-"+getDynamicFontSize(1)+"-bold,center,white>>"+
                                                                    "Images are shown in grayscale and mirrored for display purposes"))));
            if (suggestion != null) {
                double livetheta = Double.NaN, sugtheta = Double.NaN;

                // suggestion major angle
                {
                    List<TagMosaic.GroupedDetections> columns = tm.getColumnDetections(suggestion.detections);
                    TagMosaic.GroupedDetections most = null;
                    for (int idx = 0; idx < columns.size(); idx++) {
                        TagMosaic.GroupedDetections group = columns.get(idx);

                        if (most == null || most.detections.size() < group.detections.size())
                            most = group;
                    }
                    if (most != null && most.detections.size() >= 2) {
                        TagDetection mind = null, maxd = null;
                        for (TagDetection d : most.detections) {
                            if (mind == null || mind.id > d.id)
                                mind = d;
                            if (maxd == null || maxd.id < d.id)
                                maxd = d;
                        }
                        sugtheta = Math.atan2(maxd.cxy[1] - mind.cxy[1], maxd.cxy[0] - mind.cxy[0]);
                    }
                }

                // live detection major angle
                {
                    List<TagMosaic.GroupedDetections> columns = tm.getColumnDetections(detections);
                    TagMosaic.GroupedDetections most = null;
                    for (int idx = 0; idx < columns.size(); idx++) {
                        TagMosaic.GroupedDetections group = columns.get(idx);

                        if (most == null || most.detections.size() < group.detections.size())
                            most = group;
                    }
                    if (most != null && most.detections.size() >= 2) {
                        TagDetection mind = null, maxd = null;
                        for (TagDetection d : most.detections) {
                            if (mind == null || mind.id > d.id)
                                mind = d;
                            if (maxd == null || maxd.id < d.id)
                                maxd = d;
                        }
                        livetheta = Math.atan2(maxd.cxy[1] - mind.cxy[1], maxd.cxy[0] - mind.cxy[0]);
                    }
                }

                if (!Double.isNaN(livetheta) && !Double.isNaN(sugtheta)) {
                    double dtheta = MathUtil.mod2pi(livetheta - sugtheta);

                    int fontsize = getDynamicFontSize(1.5);

                    if (dtheta > Math.PI/3)
                        vb.addBack(new VisDepthTest(false,
                                   new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                   new VzText(VzText.ANCHOR.TOP,
                                              "<<dropshadow=#FF000000,"+
                                              "monospaced-"+fontsize+"-bold,center,red>>"+
                                              "Rotate right"))));

                    if (dtheta < -Math.PI/3)
                        vb.addBack(new VisDepthTest(false,
                                   new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                   new VzText(VzText.ANCHOR.TOP,
                                              "<<dropshadow=#FF000000,"+
                                              "monospaced-"+fontsize+"-bold,center,red>>"+
                                              "Rotate left"))));
                }
            }
            vb.swap();

            vb = vw.getBuffer("Shade");
            vb.setDrawOrder(1);
            if (suggestion != null) {
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VisChain(PixelsToVis,
                                                         LinAlg.translate(imwidth/2, imheight/2, 0),
                                                         new VzRectangle(imwidth, imheight,
                                                                         new VzMesh.Style(new Color(0, 0, 0, 128))))));
            }
            vb.swap();

            vb = vw.getBuffer("Detections");
            vb.setDrawOrder(10);
            for (TagDetection d : detections) {
                Color color = getTagColor(d.id);

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

    private Color getTagColor(int id)
    {
        int row = tm.getRow(id);
        int col = tm.getColumn(id);

        if (minRow != null && maxRow != null && minCol != null && maxCol != null) {
            if (row > minRow && row < maxRow && col > minCol && col < maxCol)
                return Color.white;
        }

        return colorList.get(id);
    }

    class CalibrationThread extends Thread
    {
        public CalibrationThread()
        {
            this.setName("AprilCal CalibrationThread");
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
                vw.getBuffer("remaining-frames").swap();
                return;
            }

            updateInitialization(pf.im, pf.detections);
            updateMosaic(pf.detections);
            drawSuggestions(pf.im);
            scorePix(pf.im, pf.detections);
            scoreFS(pf.im, pf.detections);

            if (clearScoreState) {
                fsScoreImages.clear();
                pixScoreImages.clear();
                minFSScore = Double.MAX_VALUE;
                progressTimeoutUtime = Long.MAX_VALUE;
                clearScoreState = false;
            }
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

        void updateInitialization(BufferedImage im, List<TagDetection> detections)
        {
            bestInitUpdated = false;

            // we don't need a standard deviation lower than 100
            // was 20 when we added the bestInitImage to the actual calibration
            if (bestScore < 40)
                return;

            if (calibrator.getCalRef().getAllImageSets().size() >= 2 || detections.size() < 4) // XXX
                return;

            InitializationVarianceScorer scorer = new InitializationVarianceScorer(calibrator,
                                                                                   imwidth, imheight);

            double score = scorer.scoreFrame(detections);

            // we can sometimes get unlucky with the random samples from the InitializationVarianceScorer
            if (score <= 1.0e-6 || Double.isNaN(score) || Double.isInfinite(score)) {
                System.out.printf("updateInitialization: returning early due to invalid score (%f)\n", score);
                return;
            }

            AprilCal.this.currentScore = score;

            if (bestInitImage == null || Double.isInfinite(bestScore) || (score < 0.75*bestScore)) {
                bestScore = score;
                bestInitImage = im;
                bestInitDetections = detections;
                bestInitUpdated = true;

                System.out.printf("Reinitialized with score %12.6f\n", score);

                calibrator.resetCalibrationSystem();
                calibrator.addOneImageSet(Arrays.asList(im), Arrays.asList(detections));
                calibrator.draw();
            }
        }

        void drawSuggestions(BufferedImage im)
        {
            VisWorld.Buffer vb;

            PixelsToVis = getPlottingTransformation(im, true);
            double maxdim = Math.max(vc.getHeight(), vc.getWidth());
            double dynamicLineWidth = Math.max(1, Math.ceil(maxdim/500));

            vb = vw.getBuffer("SuggestedTags");
            vb.setDrawOrder(20);
            if (suggestion != null) {
                // Draw all the suggestions in muted colors
                VisChain chain = new VisChain();
                for (TagDetection d : suggestion.detections) {

                    Color color = getTagColor(d.id);
                    chain.add(new VzLines(new VisVertexData(d.p),
                                          VzLines.LINE_LOOP,
                                          new VzLines.Style(color, dynamicLineWidth)));
                }
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VisChain(PixelsToVis, chain)));
            }
            vb.swap();
        }

        boolean isFinished()
        {
            return selectedScores.size() > 0 && selectedScores.get(selectedScores.size()-1)[1] < stoppingAccuracy;
        }

        void scoreFS(BufferedImage im, List<TagDetection> detections)
        {
            if (suggestion == null)
                return;

            List<CameraCalibrationSystem.CameraWrapper> camWrappers = calibrator.getCalRef().getCameras();
            assert(camWrappers.size() == 1);
            CameraCalibrationSystem.CameraWrapper cam = camWrappers.get(0);

            FrameScorer fs = null;
            if (calibrator.getCalRef().getAllImageSets().size() < 3 || cam.cal == null)
                fs = new InitializationVarianceScorer(calibrator, imwidth, imheight);
            else
                fs = new MaxEREScorer(calibrator, imwidth, imheight);

            double fsScore  = fs.scoreFrame(detections);
            double fsThresh = suggestion.score;

            if (fsScore <= .2)
                return;

            ScoredImage si = new ScoredImage(im, detections, fsScore);
            fsScoreImages.add(si);
            Collections.sort(fsScoreImages);
            if (fsScoreImages.size() > 10)
                fsScoreImages.remove(fsScoreImages.size()-1);

            if (fsScore < minFSScore) {

                minFSScore = fsScore;
                progressTimeoutUtime = TimeUtil.utime() + 20*1000000; // 5 second with no progress

                // System.out.printf("Got new min, with %d dections, value %f\n",detections.size(),fsScore);
                // try {
                //     String filename = String.format("/tmp/DEBUG%02d.png", debugCT++);
                //     javax.imageio.ImageIO.write(im, "png", new File(filename));
                //     System.out.printf("Saved debug to %s\n",filename);
                // } catch(IOException e) {
                // }


            }

            if (TimeUtil.utime() > progressTimeoutUtime) {
                VisWorld.Buffer vb = vw.getBuffer("stuck");
                vb.setDrawOrder(1000);
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                            new VisDepthTest(false,
                                            new VzText(VzText.ANCHOR.BOTTOM_LEFT,
                                                       "<<monospaced-"+getDynamicFontSize(1)+"-bold,right,red,dropshadow=#FFaaaaaa>>"+
                                                       "Stuck? Press 'space' to take manual frame.\n \n"))));
            }
            vw.getBuffer("stuck").swap();

            ////////////////////////////////////////
            // frame score heuristic
            {
                VisWorld.Buffer vb = vw.getBuffer("fs-error-score");
                vb.setDrawOrder(1000);

                // Estimate frames remaining
                int pred = 10;
                {
                    if (selectedScores.size() > 2) {
                        int last_idx = (int)selectedScores.get(selectedScores.size() -1)[0];
                        double last_error = selectedScores.get(selectedScores.size() -1)[1];
                        double second_to_last_error = selectedScores.get(selectedScores.size() -2)[1];
                        double slope = last_error - second_to_last_error;

                        // we can hope to improve the error by at most .5 pixels per frame
                        // and at the least .01 per frame
                        slope = MathUtil.clamp(slope, -.5, -.01);

                        int remaining = (int)Math.ceil((stoppingAccuracy - last_error) / slope);
                        pred = remaining + last_idx;
                    }
                }
                int remaining = Math.max(1, Math.min(12, pred - calibrator.getCalRef().getAllImageSets().size()));



                String name_toks[] = fs.getClass().getName().split("\\.");
                String format0 = "<<white,monospaced-12,left>>";
                String format1 = fsScore < fsThresh ? "<<green,monospaced-12,left>>" : "<<white,monospaced-12,left>>";
                String format2 = minFSScore < fsThresh ? "<<green,monospaced-12,left>>" : "<<white,monospaced-12,left>>";
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_RIGHT,
                                            new VzText(VzText.ANCHOR.TOP_RIGHT,
                                                       String.format("%s%s\n"+
                                                                     "%sFrameScore       %12.3f\n"+
                                                                     "%sThresh           %12.3f\n"+
                                                                     "%sMin              %12.3f\n"+
                                                                     "%sRemaining frames %d",
                                                                     format0, name_toks[name_toks.length -1],
                                                                     format1, fsScore,
                                                                     format0, fsThresh,
                                                                     format2, minFSScore,
                                                                     format0, remaining))));
                vb.swap();

                vb = vw.getBuffer("remaining-frames");
                vb.setDrawOrder(1000);
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_RIGHT,
                                            new VzText(VzText.ANCHOR.TOP_RIGHT,
                                                       "<<monospaced-"+getDynamicFontSize(1)+"-bold,right,white>>"+
                                                       remaining+" frame"+(remaining != 1 ? "s" : "")+" to go.")));
                vb.swap();

            }


            ////////////////////////////////////////
            // meter
            {
                VisWorld.Buffer vb = vw.getBuffer("fs-error-meter");
                vb.setDrawOrder(1000);
                double maxRectHeight = vc.getHeight()*0.3;
                double rectHeight = Math.min(maxRectHeight, 3 * maxRectHeight * fsScore / fsThresh);
                double perc = rectHeight/maxRectHeight;
                double rectWidth = 25;
                Color barcolor = Color.gray;

                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_RIGHT,
                                            LinAlg.translate(-rectWidth/2, vc.getHeight()/2-maxRectHeight/2, 0),
                                            new VisChain(LinAlg.translate(0, maxRectHeight/2, 0),
                                                         new VzRectangle(rectWidth, maxRectHeight, new VzMesh.Style(Color.black))),
                                            new VisChain(LinAlg.translate(0, rectHeight/2, 0),
                                                         new VzRectangle(rectWidth, rectHeight, new VzMesh.Style(barcolor))),
                                            new VisChain(LinAlg.translate(0, maxRectHeight/2, 0),
                                                         new VzRectangle(rectWidth, maxRectHeight, new VzLines.Style(new Color(150, 150, 150), 2))),

                                            LinAlg.translate(rectWidth/2, 0, 0),
                                            new VisDepthTest(false, new VzText(VzText.ANCHOR.RIGHT,
                                                                               "<<dropshadow=#FF000000,"+
                                                                               "monospaced-12-bold,white>>"+
                                                                               "Perfect")),
                                            LinAlg.translate(0, maxRectHeight*1/3, 0),
                                            new VisDepthTest(false, new VzText(VzText.ANCHOR.RIGHT,
                                                                               "<<dropshadow=#FF000000,"+
                                                                               "monospaced-12-bold,white>>"+
                                                                               "Good")),
                                            LinAlg.translate(0, maxRectHeight*2/3, 0),
                                            new VisDepthTest(false, new VzText(VzText.ANCHOR.RIGHT,
                                                                               "<<dropshadow=#FF000000,"+
                                                                               "monospaced-12-bold,white>>"+
                                                                               "Bad"))));
                vb.swap();
            }
        }

        void scorePix(BufferedImage im, List<TagDetection> detections)
        {
            VisWorld.Buffer vb;

            // if we haven't detected any tags yet...
            if (minRow == null || maxRow == null || minCol == null || maxCol == null) {
                vb = vw.getBuffer("Suggestion HUD");
                vb.setDrawOrder(1000);
                vb.addBack(new VisDepthTest(false,
                                            new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VzText(VzText.ANCHOR.BOTTOM,
                                                       "<<dropshadow=#AA000000>>"+
                                                       "<<monospaced-"+getDynamicFontSize(1.5)+"-bold,center,green>>"+
                                                       "Hold target in front of camera"))));
                vb.swap();
                return;
            }

            if (suggestion == null) {
                return;
            }

            // give a bit of user instruction
            vb = vw.getBuffer("Suggestion HUD");
            vb.setDrawOrder(1000);
            vb.addBack(new VisDepthTest(false,
                                        new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM,
                                        new VzText(VzText.ANCHOR.BOTTOM,
                                                   "<<dropshadow=#FF000000>>"+
                                                   "<<monospaced-"+getDynamicFontSize(1)+"-bold,center,white>>"+
                                                   "Align live detections with similarly-colored outlines"))));

            // nothing more to do without detections
            if (detections.size() == 0)
                return;

            // compute score for suggestion
            double meandist = 0;
            {
                double totaldist = 0;
                int nmatches = 0;

                for (TagDetection det1 : detections) {
                    for (TagDetection det2 : suggestion.detections) {
                        if (det1.id != det2.id)
                            continue;

                        totaldist += LinAlg.distance(det1.cxy, det2.cxy);
                        nmatches++;
                        break;
                    }
                }

                meandist = totaldist/nmatches;
            }

            // Compute the acceptance threshold based on the size of the target:
            // Compute maximum target dimension
            double maxDist = 0.0;
            for (TagDetection td1 : detections) {
                for (TagDetection td2 : detections) {
                    if(td1.id == td2.id)
                        continue;

                    // How many squares away are we?
                    double gridDist =  LinAlg.distance(tm.getPositionPixels(td2.id),
                                                    tm.getPositionPixels(td1.id));

                    // how far apart on the screen are they?
                    double pixDist = LinAlg.distance(td1.cxy, td2.cxy);
                    double distRatio = pixDist/gridDist; // pixels/square

                    if (distRatio > maxDist)
                        maxDist = distRatio;
                }
            }

            // in units of "squares" how close do we need to get on average?
            double meandistthreshold = maxDist;

            ////////////////////////////////////////
            // meter
            vb = vw.getBuffer("Error meter");
            vb.setDrawOrder(1000);
            double maxRectHeight = vc.getHeight()*0.3;
            double rectHeight = Math.min(maxRectHeight, maxRectHeight*meandist/meandistthreshold/3);
            double perc = rectHeight/maxRectHeight;
            double rectWidth = 25;
            Color barcolor = Color.white;

            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                        LinAlg.translate(rectWidth/2, vc.getHeight()/2-maxRectHeight/2, 0),
                                        new VisChain(LinAlg.translate(0, maxRectHeight/2, 0),
                                                     new VzRectangle(rectWidth, maxRectHeight, new VzMesh.Style(Color.black))),
                                        new VisChain(LinAlg.translate(0, rectHeight/2, 0),
                                                     new VzRectangle(rectWidth, rectHeight, new VzMesh.Style(barcolor))),
                                        new VisChain(LinAlg.translate(0, maxRectHeight/2, 0),
                                                     new VzRectangle(rectWidth, maxRectHeight, new VzLines.Style(new Color(150, 150, 150), 2))),

                                        LinAlg.translate(-rectWidth/2, 0, 0),
                                        new VisDepthTest(false, new VzText(VzText.ANCHOR.LEFT,
                                                                           "<<dropshadow=#FF000000,"+
                                                                           "monospaced-12-bold,white>>"+
                                                                           "Perfect")),
                                        LinAlg.translate(0, maxRectHeight*1/3, 0),
                                        new VisDepthTest(false, new VzText(VzText.ANCHOR.LEFT,
                                                                          "<<dropshadow=#FF000000,"+
                                                                          "monospaced-12-bold,white>>"+
                                                                          "Good")),
                                        LinAlg.translate(0, maxRectHeight*2/3, 0),
                                        new VisDepthTest(false, new VzText(VzText.ANCHOR.LEFT,
                                                                          "<<dropshadow=#FF000000,"+
                                                                          "monospaced-12-bold,white>>"+
                                                                          "Bad"))));
            vb.swap();

            ////////////////////////////////////////
            ScoredImage si = new ScoredImage(im, detections, meandist);

            if (!waitingForBest && si.meandistance < meandistthreshold) {
                waitingForBest = true;
                startedWaiting = TimeUtil.utime();

                vb = vw.getBuffer("Suggestion HUD");
                vb.setDrawOrder(1000);
                vb.addBack(new VisDepthTest(false,
                                            new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            new VzText(VzText.ANCHOR.BOTTOM,
                                                       "<<dropshadow=#AA000000>>"+
                                                       "<<monospaced-"+getDynamicFontSize(1.5)+"-bold,center,green>>"+
                                                       "Almost there..."))));
                vb.swap();
            }

            // keep N best candidate images
            pixScoreImages.add(si);
            Collections.sort(pixScoreImages);
            List<ScoredImage> newCandidates = new ArrayList<ScoredImage>();
            for (int i=0; i < Math.min(10, pixScoreImages.size()); i++)
                newCandidates.add(pixScoreImages.get(i));
            pixScoreImages = newCandidates;

            if (captureNext || waitingForBest) {
                double waited = (TimeUtil.utime() - startedWaiting)*1.0e-6;

                if (fsScoreImages.size() >= 1 && (captureNext || waited > waitTime)) {

                    ////////////////////////////////////////
                    // "flash"
                    vw.getBuffer("Suggestion HUD").swap();
                    vw.getBuffer("SuggestedTags").swap();
                    new FlashThread().start();

                    ////////////////////////////////////////
                    // use acquired image and suggest a new one
                    waitingForBest = false;
                    ScoredImage bestSI = fsScoreImages.get(0);//pixScoreImages.get(0);

                    //if (!captureNext)
                    //    draw(bestSI.im, bestSI.detections);

                    // Compute the frame score for this image
                    if (calibrator.getCalRef().getAllImageSets().size() >= 3) {
                        FrameScorer fs = new MaxEREScorer(calibrator, imwidth, imheight);
                        double score = fs.scoreFrame(detections);

                        // Only fire the first time we are "finished"
                        if (!isFinished() && score < stoppingAccuracy) {
                            // Swap mode
                            applicationMode = MODE_RECTIFY;

                            vb = vw.getBuffer("Finished");
                            vb.addBack(new VisDepthTest(false,
                                       new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                                        new VzText(VzText.ANCHOR.CENTER,
                                                                   "<<dropshadow=#AA000000>>"+
                                                                   "<<monospaced-"+getDynamicFontSize(2)+"-bold,center,green>>"+
                                                                   "Calibration complete."+
                                                                   "\n<<monospaced-"+getDynamicFontSize(1)+"-bold,center>>"+
                                                                   "type \":save-calibration-images\" to export data"))));
                            vb.setDrawOrder(1001);
                            vb.swap();
                        }

                        int nimages = calibrator.getCalRef().getAllImageSets().size();
                        selectedScores.add(new double[]{ nimages+1, score }); // Keep the history

                        System.out.printf("Chose image with score %f compared to best in dictionary %f \n",
                                          score, suggestion.score);
                    }

                    if (captureNext)
                        addImage(im, detections);
                    else
                        addImage(bestSI.im, bestSI.detections);

                    // make a new suggestion
                    suggestionNumber++;
                    generateNextSuggestion();

                    updateRectifiedBorder();

                    captureNext = false;

                    // XXX
                    clearScoreState = true;

                }
            }
            else {
                vb = vw.getBuffer("Suggestion HUD");
                vb.setDrawOrder(1000);


                if (detections.size() > 0 && suggestion != null && suggestion.detections.size() > 0) {
                    double detectionSizeError = getMeanDetectionSizeErrors(suggestion.detections, detections);

                    String str = null;
                    if (detectionSizeError < -5.0) str = "Move target away from camera";
                    if (detectionSizeError > 10.0) str = "Move target closer to camera";

                    if (str != null) {
                        str = "<<dropshadow=#AA000000>>"+
                              "<<monospaced-"+getDynamicFontSize(1.5)+"-bold,center,green>>"+
                              str;

                        vb.addBack(new VisDepthTest(false,
                                                    new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                                    new VzText(VzText.ANCHOR.BOTTOM, str))));
                    }
                }

                vb.swap();
            }
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

            double h = Math.min(vc.getHeight()*0.25, 150);
            double fraction = h / vc.getHeight();
            double scale = h / (maxy - miny);

            clickHeightFraction = fraction;
            clickWidthFraction  = fraction*((maxx-minx)/(maxy-miny))/(vc.getWidth()/vc.getHeight());

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

    private double getMeanDetectionSizeErrors(List<TagDetection> detections1,
                                              List<TagDetection> detections2)
    {
        double perimeter1 = 0;
        double perimeter2 = 0;
        int n = 0;

        for (TagDetection d1 : detections1) {

            for (TagDetection d2 : detections2) {
                if (d2.id != d1.id)
                    continue;

                perimeter1 += LinAlg.distance(d1.p[0], d1.p[1]);
                perimeter1 += LinAlg.distance(d1.p[1], d1.p[2]);
                perimeter1 += LinAlg.distance(d1.p[2], d1.p[3]);
                perimeter1 += LinAlg.distance(d1.p[3], d1.p[0]);

                perimeter2 += LinAlg.distance(d2.p[0], d2.p[1]);
                perimeter2 += LinAlg.distance(d2.p[1], d2.p[2]);
                perimeter2 += LinAlg.distance(d2.p[2], d2.p[3]);
                perimeter2 += LinAlg.distance(d2.p[3], d2.p[0]);

                n++;
            }
        }

        return (perimeter1 - perimeter2) / 4.0 / n;
    }

    double[] getObsMosaicCenter()
    {
        return LinAlg.scale(LinAlg.add(tm.getPositionMeters(minCol, minRow),
                                       tm.getPositionMeters(maxCol, maxRow)),
                            0.5);
    }

    private ArrayList<SuggestedImage> generateSuggestionsDict(Calibration cal)
    {
        int width = cal.getWidth();
        int height = cal.getHeight();

        // Compute single depth for the dictionary XXX
        double K[][] = cal.copyIntrinsics();
        double desiredDepths[] = { (K[1][1] / (height*0.6)) * (5*tagSpacingMeters),
                                   (K[1][1] / (height*1.2)) * (5*tagSpacingMeters) };

        double rpys[][] = {{ 0.0, 0.0, Math.PI/2 },
                           { 0.0, 0.8, Math.PI/2 },
                           { 0.8, 0.0, Math.PI/2 }};

        // Place the center of the target:
        double centerXy[] = getObsMosaicCenter();

        DistortionFunctionVerifier verifier = new DistortionFunctionVerifier(cal);

        // Generate ~100 images, 3 angles each, 5 x 5 grid

        // These extrinsics are centered, so we can ensure the observed mosaic is mostly in view
        // ArrayList<double[]> centeredExt = new ArrayList();
        ArrayList<SuggestedImage> candidates = new ArrayList();


        // inset x percent of the image, then divide the remaining by
        // by the grid spacing
        double inset = .15;
        int divisionsX = 3;
        int divisionsY =  3;

        int startX = (int)(inset*width);
        int startY = (int)(inset*height);

        // Debug:
        double rNextGaus = 0.0; // Find replace with r.nextGaussian() to restore
        double rpyNoise = 0.1;
        for (double desiredDepth : desiredDepths) {
            for (int gy = 0; gy < divisionsY; gy++) {
                for (int gx = 0; gx < divisionsX; gx++) {

                    double x = startX + width*(1-inset*2) * gx / (divisionsX -1) + r.nextGaussian()*2.0;
                    double y = startY + height*(1-inset*2) * gy / (divisionsY -1) + r.nextGaussian()*2.0;
                    double xy_dp[] = new double[] { x, y };

                    // if (!verifier.validPixelCoord(xy_dp))
                    //     continue;

                    double xyz[] = cal.pixelsToRay(xy_dp);
                    //LinAlg.scaleEquals(xyz, desiredDepth + r.nextGaussian() * 0.01 );
                    LinAlg.scaleEquals(xyz, desiredDepth );

                    // In case the target is in the center of the image, rotate both ways
                    ArrayList<double[]> rpy_adjusted = new ArrayList();
                    for (double rpy[] : rpys) {
                        // Choose which direction to rotate based on which image quadrant this is
                        double signs_rpy[] = {MathUtil.sign(x - width/2),
                                              MathUtil.sign(y - height/2), 1};

                        if (rpy[0] != 0.0 && Math.abs(x - width/2) < .05*width) {
                            rpy_adjusted.add(LinAlg.scale(rpy, signs_rpy));
                            // flip roll:
                            signs_rpy[0] *= -1;
                            rpy_adjusted.add(LinAlg.scale(rpy, signs_rpy));
                        } else if (rpy[1] != 0.0 && Math.abs(y - height/2) < .05*height) {
                            rpy_adjusted.add(LinAlg.scale(rpy, signs_rpy));
                            // flip pitch:
                            signs_rpy[1] *= -1;
                            rpy_adjusted.add(LinAlg.scale(rpy, signs_rpy));
                        } else {
                            rpy_adjusted.add(LinAlg.scale(rpy, signs_rpy));
                        }
                    }

                    for (double rpy[] : rpy_adjusted) {
                        rpy = LinAlg.add(rpy, new double[]{r.nextGaussian()*rpyNoise, r.nextGaussian()*rpyNoise, 0});

                        SuggestedImage si = new SuggestedImage();
                        si.xyzrpy_cen = concat(xyz,rpy);
                        si.xyzrpy = LinAlg.xyzrpyMultiply(si.xyzrpy_cen, LinAlg.xyzrpyInverse(LinAlg.resize(centerXy,6)));
                        candidates.add(si);
                    }
                }
            }
        }

        ArrayList<SuggestedImage> passed = new ArrayList();
        for (SuggestedImage si : candidates) {
            // Pass in non-centered extrinsics
            si.detections = makeDetectionsFromExt(verifier, cal, si.xyzrpy);
            if (si.detections.size() < 12)
                continue;
            passed.add(si);
        }
        return passed;
    }

    // Candidate to move to LinAlg?
    public static double[] concat(double [] ... args)
    {
        int len = 0;
        for (double v[] : args)
            len += v.length;
        double out[] = new double[len];

        int off = 0;
        for (double v[] : args) {

            for (int i = 0; i < v.length; i++)
                out[off+ i] = v[i];
            off += v.length;
        }
        return out;
    }

    private ArrayList<TagDetection> makeDetectionsFromExt(DistortionFunctionVerifier verifier,
                                                          Calibration cal, double mExtrinsics[])
    {
        return SuggestUtil.makeDetectionsFromExt(cal, verifier, mExtrinsics, tm.getID(minCol, minRow),
                                                 tm.getID(maxCol, maxRow),
                                                 tm);
    }

    static ArrayList<SuggestedImage> scoreSuggestions(List<SuggestedImage> suggestions, FrameScorer fs)
    {
        ArrayList<SuggestedImage> outputs = new ArrayList(suggestions);

        for (SuggestedImage si : outputs)
            si.score = fs.scoreFrame(si.detections);

        Collections.sort(outputs, new Comparator<SuggestedImage>()
                         {
                             public int compare(SuggestedImage s1, SuggestedImage s2)
                             {
                                 return Double.compare(s1.score, s2.score);
                             }
                         });

        return outputs;
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

    public static ArrayList<double[]> stripNAs(ArrayList<double[]> input)
    {
        ArrayList<double[]> screened = new ArrayList();
      outer:
        for (double [] pt : input) {
            for (double p : pt)
                if (Double.isNaN(p))
                    continue outer;
            screened.add(pt);
        }
        return screened;
    }

    public static double intercept(double line_params[], double intercept)
    {
        return (intercept - line_params[1])/line_params[0];
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

        // we only need a new synthetic image if the min/max row/col changed
        // or if the initialization changed
        if (minRow == minRowUsed && maxRow == maxRowUsed &&
            minCol == minColUsed && maxCol == maxColUsed &&
            bestInitUpdated == false)
        {
            return;
        }

        minRowUsed = minRow;
        maxRowUsed = maxRow;
        minColUsed = minCol;
        maxColUsed = maxCol;

        int mosaicWidth  = tm.getMosaicWidth();
        int mosaicHeight = tm.getMosaicHeight();

        ArrayList<Integer> tagsToDisplay = new ArrayList<Integer>();
        for (int col = minCol; col <= maxCol; col++) {
            for (int row = minRow; row <= maxRow; row++) {

                int id = row*mosaicWidth + col;
                if (id > tf.codes.length)
                    continue;

                tagsToDisplay.add(id);
            }
        }

        //simgen = new SyntheticTagMosaicImageGenerator(tf, imwidth, imheight, tagSpacingMeters, tagsToDisplay);
        pixScoreImages.clear();
        fsScoreImages.clear();

        generateNextSuggestion();
    }

    private void generateNextSuggestion()
    {
        ParameterizableCalibration curcal = null, cal = null;
        double params[] = null;
        if (calibrator != null) curcal = calibrator.getCalRef().getCameras().get(0).cal;
        if (curcal != null)     params = curcal.getParameterization();
        if (params != null)     cal    = initializer.initializeWithParameters(imwidth, imheight, params);

        if (cal == null)
            return;

        ArrayList<SuggestedImage> suggestDictionary = generateSuggestionsDict(cal);
        System.out.printf("Made new dictionary with %d valid poses\n",suggestDictionary.size());

        SuggestedImage newSuggestion = null;

        FrameScorer fs = null;
        if (calibrator.getCalRef().getAllImageSets().size() < 3)
            fs = new InitializationVarianceScorer(calibrator, imwidth, imheight);
        else
            fs = new MaxEREScorer(calibrator, imwidth, imheight);

        Tic t = new Tic();
        //ArrayList<SuggestedImage>
        ranked = scoreSuggestions(suggestDictionary, fs);
        System.out.printf("  Scored %d suggestions in %.3f seconds\n", suggestDictionary.size(), t.toctic());
        // Pick the single best suggestion
        if (true) {
            if (ranked.size() > 0) suggestion = ranked.get(0);
            else                   suggestion = null;
        }

        // Grab the current value of 'cal'
        {
            double curCalScore = MaxEREScorer.scoreCal(calibrator, imwidth, imheight);

            System.out.printf("cur score %f\n", curCalScore);
        }
        // Debug
        if (false) {
            System.out.println("Top ten suggestions:");
            for (int i = 0; i < Math.min(10, ranked.size()); i++) {
                System.out.printf(" %02d score %f\n", i, ranked.get(i).score);
            }
            int lastIdx = ranked.size()-1;
            if (lastIdx >= 0) System.out.printf(" %d score %f\n", lastIdx, ranked.get(lastIdx).score);

        }
    }

    private void selectModelAndSave(String basepath)
    {
        List<List<CalibrationInitializer>> initializerTypes = new ArrayList();
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new DistortionFreeInitializer("")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new AngularPolynomialInitializer("kclength=1")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new AngularPolynomialInitializer("kclength=2")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new AngularPolynomialInitializer("kclength=3")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new AngularPolynomialInitializer("kclength=4")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new AngularPolynomialInitializer("kclength=5")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new RadialPolynomialInitializer("kclength=1")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new RadialPolynomialInitializer("kclength=2")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new RadialPolynomialInitializer("kclength=3")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new RadialPolynomialInitializer("kclength=4")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new RadialPolynomialInitializer("kclength=5")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new CaltechInitializer("kclength=1")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new CaltechInitializer("kclength=2")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new CaltechInitializer("kclength=3")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new CaltechInitializer("kclength=4")));
        initializerTypes.add(Arrays.asList((CalibrationInitializer) new CaltechInitializer("kclength=5")));

        List<CameraCalibrator> calibrators = calibrator.createModelSelectionCalibrators(initializerTypes);
        assert(calibrators.size() == initializerTypes.size());

        // create directory
        int dirNum = -1;
        String dirName = null;
        File dir = null;
        do {
            dirNum++;
            dirName = String.format("%s/imageSet%d/", basepath, dirNum);
            dir = new File(dirName);
        } while (dir.exists());

        if (dir.mkdirs() != true) {
            System.err.printf("Failure to create directory '%s'\n", dirName);
            return;
        }

        // save all camera models
        for (int i = 0; i < initializerTypes.size(); i++) {

            CameraCalibrator rcc = calibrators.get(i);
            CalibrationInitializer initializer = initializerTypes.get(i).get(0);
            String shortclassname = initializer.getClass().getName().replace("april.camera.models.","");
            shortclassname = shortclassname.replace("Initializer","Calibration");
            shortclassname += ","+initializer.getParameterString();

            if (rcc == null) {
                System.out.printf("Calibration with model %-45s: failed to initialize\n", "'"+shortclassname+"'");
                continue;
            }

            List<CameraCalibrator.GraphStats> stats =
                rcc.iterateUntilConvergenceWithReinitalization(1.0, 0.01, 3, 50);

            System.out.printf("Calibration with model %-45s: ", "'"+shortclassname+"'");
            ArrayList<String> lines = new ArrayList();
            for (CameraCalibrator.GraphStats gs : stats) {
                String s = String.format("MRE: %10s MSE %10s MaxRE %10s",
                                        (gs == null) ? "n/a" : String.format("%7.3f px", gs.MRE),
                                        (gs == null) ? "n/a" : String.format("%7.3f px", gs.MSE),
                                        (gs == null) ? "n/a" : String.format("%7.3f px", gs.MaxRE));
                lines.add(s);
                System.out.printf(s);
            }
            System.out.println();

            String calName = dirName + shortclassname + ".config";

            try {
                BufferedWriter outs = new BufferedWriter(new FileWriter(new File(calName)));
                outs.write(rcc.getCalibrationBlockString(lines.toArray(new String[0])));
                outs.flush();
                outs.close();
            } catch (Exception ex) {
                System.err.printf("AprilCal: Failed to output calibration to '%s'\n", calName);
                return;
            }
        }

        // save images
        List<List<BufferedImage>> imageSets = calibrator.getCalRef().getAllImageSets();

        for (int imageSetIndex = 0; imageSetIndex < imageSets.size(); imageSetIndex++) {

            List<BufferedImage> images = imageSets.get(imageSetIndex);
            assert(images.size() == 1);
            BufferedImage im = images.get(0);

            String fileName = String.format("%simage%04d.png", dirName, imageSetIndex);
            File imageFile = new File(fileName);

            System.out.printf("Filename '%s'\n", fileName);

            try {
                ImageIO.write(im, "png", imageFile);

            } catch (IllegalArgumentException ex) {
                System.err.printf("Failed to output images to '%s'\n", dirName);
                return;
            } catch (IOException ex) {
                System.err.printf("Failed to output images to '%s'\n", dirName);
                return;
            }
        }

        System.out.printf("Saved all model calibrations and images to '%s'\n", dirName);
    }

    private class FlashThread extends Thread
    {
        private FlashThread()
        {
            this.setName("AprilCal FlashThread");
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
        opts.addDouble('\0',"stopping-accuracy",1.0,"Termination accuracy (px) which flags \"finished\"");
        opts.addString('\0',"debug-seed-images","","Optional parameter listing starting images for debugging");
        opts.addBoolean('\0',"dictionary-gui",false,"Display image dictionary");
        opts.addBoolean('\0',"debug-gui",false,"Display calibration extrinsics and other debugging info");

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

        ArrayList<BufferedImage> debugSeedImages = new ArrayList();
        try {
            // Load images alphabetically from the directory
            ImageSource isrc = new ImageSourceFile("dir://"+opts.getString("debug-seed-images")+"?loop=0");
            FrameData fdat = null;
            while( (fdat = isrc.getFrame()) != null)
                debugSeedImages.add(ImageConvert.convertToImage(fdat));
        } catch(IOException e){
            if (!opts.getString("debug-seed-images").isEmpty())
                System.out.println("Failed to load seed images: "+e);
        }

        new AprilCal(initializer, url, spacing,  opts.getDouble("stopping-accuracy"),
                     opts.getBoolean("debug-gui"), opts.getBoolean("dictionary-gui"),
                     debugSeedImages);
    }
}

