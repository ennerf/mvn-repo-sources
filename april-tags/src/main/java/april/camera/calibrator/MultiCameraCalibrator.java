package april.camera.calibrator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.swing.*;

import april.camera.*;
import april.camera.models.*;
import april.jcam.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

public class MultiCameraCalibrator implements ParameterListener
{
    public boolean verbose = false; // don't make static
    boolean autocapture = false;

    JFrame          jf;
    JSplitPane      canvasPane;
    ParameterGUI    pg;

    List<CalibrationInitializer>    initializers;
    String                          urls[];
    ImageSource                     isrcs[];
    ImageSourceFormat               ifmts[];
    BlockingSingleQueue<FrameData>  imageQueues[];
    boolean                         imageQueueFlags[];

    CameraCalibrator calibrator;
    List<CameraCalibrator.GraphStats> lastGraphStats;
    TagFamily   tf;
    TagDetector td;
    double      metersPerTag;

    VisWorld vwImages;
    VisLayer vlImages;
    VisCanvas vcImages;
    HashMap<Integer,double[][]> PixelsToVisTransforms = new HashMap<Integer,double[][]>();
    List<Color> colorList = new ArrayList<Color>();

    boolean captureOnce = false;

    long start_utime;
    int imageCounter = 0;

    public MultiCameraCalibrator(List<CalibrationInitializer> initializers, String urls[],
                                 double metersPerTag, boolean verbose, boolean autocapture)
    {
        this.verbose = verbose;
        this.autocapture = autocapture;
        this.tf = new Tag36h11();
        this.td = new TagDetector(tf);
        this.initializers = initializers;
        this.metersPerTag = metersPerTag;

        this.urls           = urls;
        this.isrcs          = new ImageSource[urls.length];
        this.ifmts          = new ImageSourceFormat[urls.length];
        this.imageQueues    = new BlockingSingleQueue[urls.length];
        this.imageQueueFlags= new boolean[urls.length];

        this.start_utime = TimeUtil.utime();

        // Calibrator setup
        calibrator = new CameraCalibrator(initializers, tf, metersPerTag, true, verbose);

        pg = new ParameterGUI();
        pg.addCheckBoxes("screenshots","Automatically save screenshots to /tmp", false);
        pg.addButtons("captureOnce","Capture once",
                      "print", "Print calibration block",
                      "modelselect","Perform model selection and save",
                      "savecalibration","Save calibration",
                      "saveall","Save calibration and images");
        pg.addListener(this);

        vwImages = new VisWorld();
        vlImages = new VisLayer(vwImages);
        vcImages = new VisCanvas(vlImages);

        jf = new JFrame("Multi camera calibrator");
        jf.setLayout(new BorderLayout());

        canvasPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                    vcImages,
                                    calibrator.getVisCanvas());
        canvasPane.setDividerLocation(0.3);
        canvasPane.setResizeWeight(0.3);

        JSplitPane jspane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, canvasPane, pg);
        jspane.setDividerLocation(1.0);
        jspane.setResizeWeight(1.0);

        jf.add(jspane, BorderLayout.CENTER);
        jf.setSize(1200, 600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        ////////////////////////////////////////////////////////////////////////////////
        // Camera setup
        for (int i=0; i < urls.length; i++)
        {
            imageQueues[i] = new BlockingSingleQueue<FrameData>();

            try {
                isrcs[i] = ImageSource.make(urls[i]);

                ifmts[i] = isrcs[i].getCurrentFormat();

            } catch (IOException ex) {
                System.err.printf("Exception caught while making image source '%s': %s\n",
                                  urls[i], ex);
                ex.printStackTrace();
                System.exit(-1);
            }

            new AcquisitionThread(i, urls[i], isrcs[i], imageQueues[i]).start();
        }

        double XY0[] = getXY0(initializers.size()-1);
        double XY1[] = getXY1(0);
        vlImages.cameraManager.fit2D(XY0, XY1, true);

        // get a shuffled set of nice tag colors
        while (colorList.size() < this.tf.codes.length)
        {
            List<Color> colors = Palette.friendly.listAll();
            colors.addAll(Palette.web.listAll());
            colors.addAll(Palette.vibrant.listAll());
            colors.remove(0);
            colorList.addAll(colors);
        }
        Collections.shuffle(colorList, new Random(283819));

        // Threads
        new ProcessingThread().start();

        calibrator.draw();
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

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("captureOnce")) {
            if (captureOnce) captureOnce = false;
            else             captureOnce = true;
        }

        if (name.equals("print"))
            calibrator.printCalibrationBlock(getConfigCommentLines());

        if (name.equals("modelselect"))
            selectModelAndSave("/tmp/cameraCalibration");

        if (name.equals("savecalibration"))
            calibrator.saveCalibration("/tmp/cameraCalibration", getConfigCommentLines());

        if (name.equals("saveall"))
            calibrator.saveCalibrationAndImages("/tmp/cameraCalibration", getConfigCommentLines());
    }

    class AcquisitionThread extends Thread
    {
        int id;
        String url;
        ImageSource isrc;
        BlockingSingleQueue<FrameData> imageQueue;
        Boolean imageQueueFlag;

        public AcquisitionThread(int id, String url, ImageSource isrc, BlockingSingleQueue<FrameData> imageQueue)
        {
            this.id = id;
            this.url = url;
            this.isrc = isrc;
            this.imageQueue = imageQueue;
            this.imageQueueFlag = imageQueueFlag;
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
            long counter = 0;
            while (true) {
                FrameData frmd = isrc.getFrame();
                if (frmd == null)
                    break;

                if (autocapture)
                    imageQueueFlags[id] = true;

                imageQueue.put(frmd);

                if (autocapture) {
                    counter++;
                    System.out.printf("AcquisitionThread %d got frame #%d\n", id, counter);
                    synchronized (imageQueue) {
                        while (imageQueueFlags[id]) {
                            try {
                                imageQueue.wait();
                            } catch (InterruptedException ex) {
                                continue;
                            }
                        }
                    }
                }
            }

            System.out.printf("'%s' Out of frames!\n", url);
        }
    }

    class ProcessingThread extends Thread
    {
        public void run()
        {
            long lastutime = 0;

            Tic tic = new Tic();

            while (true)
            {
                if (captureOnce || autocapture) {

                    tic.tic();
                    List<BufferedImage> imageSet = new ArrayList<BufferedImage>();
                    for (int i = 0; i < urls.length; i++) {
                        FrameData frmd = imageQueues[i].get();

                        if (autocapture) {
                            imageQueueFlags[i] = false;
                            synchronized(imageQueues[i]) {
                                imageQueues[i].notifyAll();
                            }
                        }

                        BufferedImage im = ImageConvert.convertToImage(frmd);
                        imageSet.add(im);
                    }
                    if (verbose)
                        System.out.printf("TIMING: %12.6f seconds to get image set\n", tic.toctic());

                    List<List<TagDetection>> detectionSet = new ArrayList<List<TagDetection>>();
                    for (int i = 0; i < urls.length; i++) {
                        BufferedImage im = imageSet.get(i);
                        List<TagDetection> detections = td.process(im, new double[] {im.getWidth()/2.0, im.getHeight()/2.0});
                        detectionSet.add(detections);
                    }
                    if (verbose)
                        System.out.printf("TIMING: %12.6f seconds to detect tags\n", tic.toctic());

                    drawSet(imageSet, detectionSet);
                    if (verbose)
                        System.out.printf("TIMING: %12.6f seconds to draw set\n", tic.toctic());

                    if (pg.gb("screenshots")) {
                        String path = String.format("/tmp/MultiCameraCalibrator-ScreenShot-%d-CameraPane%04d.png",
                                                    start_utime, imageCounter);
                        vcImages.writeScreenShot(new File(path), "png");
                    }

                    processSet(imageSet, detectionSet);
                    if (verbose)
                        System.out.printf("TIMING: %12.6f seconds to process set\n", tic.toctic());

                    if (pg.gb("screenshots")) {
                        String path = String.format("/tmp/MultiCameraCalibrator-ScreenShot-%d-CalibratorPane%04d.png",
                                                    start_utime, imageCounter);
                        calibrator.getVisCanvas().writeScreenShot(new File(path), "png");
                        imageCounter++;
                    }

                    captureOnce = false;
                }
                else {

                    for (int i = 0; i < urls.length; i++) {
                        if (imageQueues[i].isEmpty())
                            continue;

                        FrameData frmd = imageQueues[i].get();
                        BufferedImage im = ImageConvert.convertToImage(frmd);

                        drawImage(i, im);
                    }
                }

                // skip the rate limiting in this mode
                if (autocapture)
                    continue;

                // sleep a little if we're spinning too fast
                long utime = TimeUtil.utime();
                long desired = 30000;
                if ((utime - lastutime) < desired) {
                    int sleepms = (int) ((desired - (utime-lastutime))*1e-3);
                    TimeUtil.sleep(sleepms);
                }
                lastutime = utime;
            }
        }
    }

    void drawSet(List<BufferedImage> imageSet, List<List<TagDetection>> detectionSet)
    {
        for (int i=0; i < imageSet.size(); i++) {
            drawImage(i, imageSet.get(i));
            drawDetections(i, detectionSet.get(i));
        }
    }

    double[] getXY0(int index)
    {
        double XY0[] = new double[2];

        assert(index < ifmts.length);
        for (int i=0; i <= index; i++)
            XY0[1] -= ifmts[i].height;

        return XY0;
    }

    double[] getXY1(int index)
    {
        double XY0[] = getXY0(index);
        double XY1[] = new double[2];

        XY1[0] = XY0[0] + ifmts[index].width;
        XY1[1] = XY0[1] + ifmts[index].height;

        return XY1;
    }

    double[][] ensurePixelTransform(int index)
    {
        double PixelsToVis[][] = PixelsToVisTransforms.get(index);

        if (PixelsToVis == null) {
            double XY0[] = getXY0(index);
            double XY1[] = getXY1(index);

            PixelsToVis = CameraMath.makeVisPlottingTransform(ifmts[index].width, ifmts[index].height,
                                                              XY0, XY1, true);
            PixelsToVisTransforms.put(index, PixelsToVis);
        }

        return PixelsToVis;
    }

    void drawImage(int index, BufferedImage im)
    {
        VisWorld.Buffer vb;
        double PixelsToVis[][] = ensurePixelTransform(index);

        vb = vwImages.getBuffer(String.format("Camera%d", index));
        vb.addBack(new VisLighting(false,
                                   new VisChain(PixelsToVis,
                                                new VzImage(new VisTexture(im, VisTexture.NO_MAG_FILTER |
                                                                               VisTexture.NO_MIN_FILTER |
                                                                               VisTexture.NO_REPEAT),
                                                            0))));
        vb.swap();
    }

    void drawDetections(int index, List<TagDetection> detections)
    {
        VisWorld.Buffer vb;
        double PixelsToVis[][] = ensurePixelTransform(index);

        vb = vwImages.getBuffer(String.format("Detections%d", index));
        VisChain chain = new VisChain();
        chain.add(PixelsToVis);
        for (TagDetection d : detections) {
            Color color = colorList.get(d.id);

            ArrayList<double[]> quad = new ArrayList<double[]>();
            quad.add(d.interpolate(-1,-1));
            quad.add(d.interpolate( 1,-1));
            quad.add(d.interpolate( 1, 1));
            quad.add(d.interpolate(-1, 1));

            chain.add(new VzMesh(new VisVertexData(quad),
                                 VzMesh.QUADS,
                                 new VzMesh.Style(color)));
        }
        vb.addBack(chain);
        vb.swap();
    }

    void processSet(List<BufferedImage> imageSet, List<List<TagDetection>> detectionSet)
    {
        calibrator.addOneImageSet(imageSet, detectionSet);

        List<CameraCalibrator.GraphStats> stats =
            calibrator.iterateUntilConvergenceWithReinitalization(1.0, 0.01, 3, 50);

        lastGraphStats = stats;

        calibrator.draw(stats);
        if (verbose)
            calibrator.printCalibrationBlock(getConfigCommentLines());

        if (verbose) {
            for (CameraCalibrator.GraphStats s : stats) {
                if (s == null) {
                    System.out.printf("Graph is null\n");
                    continue;
                }
                System.out.printf("Graph with %d observations, MRE %12.6f pixels, MSE %12.6f pixels, SPD Error: %s\n",
                                  s.numObs, s.MRE, s.MSE, s.SPDError ? "true" : "false");
            }
        }
    }

    void selectModelAndSave(String basepath)
    {
        List<CalibrationInitializer> initializerTypes = new ArrayList();
        initializerTypes.add((CalibrationInitializer) new DistortionFreeInitializer(""));
        initializerTypes.add((CalibrationInitializer) new AngularPolynomialInitializer("kclength=1"));
        initializerTypes.add((CalibrationInitializer) new AngularPolynomialInitializer("kclength=2"));
        initializerTypes.add((CalibrationInitializer) new AngularPolynomialInitializer("kclength=3"));
        initializerTypes.add((CalibrationInitializer) new AngularPolynomialInitializer("kclength=4"));
        initializerTypes.add((CalibrationInitializer) new AngularPolynomialInitializer("kclength=5"));
        initializerTypes.add((CalibrationInitializer) new RadialPolynomialInitializer("kclength=1"));
        initializerTypes.add((CalibrationInitializer) new RadialPolynomialInitializer("kclength=2"));
        initializerTypes.add((CalibrationInitializer) new RadialPolynomialInitializer("kclength=3"));
        initializerTypes.add((CalibrationInitializer) new RadialPolynomialInitializer("kclength=4"));
        initializerTypes.add((CalibrationInitializer) new RadialPolynomialInitializer("kclength=5"));
        initializerTypes.add((CalibrationInitializer) new CaltechInitializer("kclength=1"));
        initializerTypes.add((CalibrationInitializer) new CaltechInitializer("kclength=2"));
        initializerTypes.add((CalibrationInitializer) new CaltechInitializer("kclength=3"));
        initializerTypes.add((CalibrationInitializer) new CaltechInitializer("kclength=4"));
        initializerTypes.add((CalibrationInitializer) new CaltechInitializer("kclength=5"));

        List<List<CalibrationInitializer>> initializerSets = new ArrayList();
        for (CalibrationInitializer initializer : initializerTypes) {
            List<CalibrationInitializer> initializerSet = new ArrayList();
            for (int cameraIndex = 0; cameraIndex < urls.length; cameraIndex++)
                initializerSet.add(initializer);
            initializerSets.add(initializerSet);
        }

        List<CameraCalibrator> calibrators = calibrator.createModelSelectionCalibrators(initializerSets);
        assert(calibrators.size() == initializerSets.size());

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
        for (int i = 0; i < initializerSets.size(); i++) {

            CameraCalibrator rcc = calibrators.get(i);
            CalibrationInitializer initializer = initializerTypes.get(i);
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
                System.err.printf("Failed to output calibration to '%s'\n", calName);
                return;
            }
        }

        // save images
        List<List<BufferedImage>> imageSets = calibrator.getCalRef().getAllImageSets();
        for (int cameraIndex = 0; cameraIndex < urls.length; cameraIndex++) {

            String subDirName = dirName;

            // make a subdirectory if we have multiple cameras
            if (urls.length > 1) {
                subDirName = String.format("%scamera%d/", dirName, cameraIndex);
                File subDir = new File(subDirName);

                if (subDir.mkdirs() != true) {
                    System.err.printf("Failure to create subdirectory '%s'\n", subDirName);
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
                    System.err.printf("Failed to output images to '%s'\n", subDirName);
                    return;
                } catch (IOException ex) {
                    System.err.printf("Failed to output images to '%s'\n", subDirName);
                    return;
                }
            }
        }

        System.out.printf("Saved all model calibrations and images to '%s'\n", dirName);
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addBoolean('v',"verbose",false,"Enable verbosity");
        opts.addString('u',"urls","","Camera URLs separated by semicolons");
        opts.addString('c',"class","april.camera.models.AngularPolynomialInitializer","Calibration model initializer class name");
        opts.addString('p',"parameterString","kclength=4","Initializer parameter string (comma separated, key=value pairs)");
        opts.addDouble('m',"spacing",0.0381,"Spacing between tags (meters)");
        opts.addBoolean('a',"autocapture",false,"Automatically capture every frame");

        if (!opts.parse(args)) {
            System.out.println("Option error: "+opts.getReason());
	    }

        boolean verbose        = opts.getBoolean("verbose");
        String urllist         = opts.getString("urls");
        String initclass       = opts.getString("class");
        String parameterString = opts.getString("parameterString");
        double spacing         = opts.getDouble("spacing");
        boolean autocapture    = opts.getBoolean("autocapture");

        if (opts.getBoolean("help") || urllist.isEmpty()){
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        String urls[] = urllist.split(";");

        if (autocapture && urls.length > 1) {
            System.out.println("ERROR: Due to the lack of explicit inter-camera synchronization,"+
                               "using autocapture mode and multiple cameras is not well defined");
            System.exit(1);
        }

        List<CalibrationInitializer> initializers = new ArrayList<CalibrationInitializer>();

        for (int i=0; i < urls.length; i++) {
            Object obj = ReflectUtil.createObject(initclass, parameterString);
            assert(obj != null);
            assert(obj instanceof CalibrationInitializer);
            CalibrationInitializer initializer = (CalibrationInitializer) obj;
            initializers.add(initializer);
        }

        new MultiCameraCalibrator(initializers, urls, spacing, verbose, autocapture);
    }
}

