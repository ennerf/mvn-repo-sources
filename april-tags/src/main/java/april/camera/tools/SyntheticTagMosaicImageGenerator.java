package april.camera.tools;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

import javax.imageio.*;
import javax.swing.*;

import april.camera.*;
import april.camera.models.*;
import april.jmat.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

public class SyntheticTagMosaicImageGenerator
{
    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;
    VisWorld.Buffer vb;

    TagFamily tf;
    TagDetector detector;

    int canvasWidth, canvasHeight;
    int imageWidth, imageHeight;

    TagMosaic mosaic;
    double tagSizeMeters;

    public ArrayList<Integer> tagsToDisplay;
    double mosaic_xmin, mosaic_xmax, mosaic_ymin, mosaic_ymax;

    double MosaicPixelsToVis[][];
    double MosaicToVis[][];

    double K[][];
    Calibration input;
    Calibration outputRectified;

    public class SyntheticImages
    {
        public double[] MosaicToGlobal; // xyzrpy

        public BufferedImage rectified;
        public BufferedImage distorted;

        public int[] tagids;
        public ArrayList<double[]> predictedTagCenters_rectified;
        public ArrayList<double[]> predictedTagCenters_distorted;
    }

    public SyntheticTagMosaicImageGenerator(TagFamily tf, int _width, int _height, double tagSizeMeters)
    {
        this(tf, _width, _height, tagSizeMeters, null);
    }

    private ArrayList<Integer> getAllTagIds(TagFamily tf)
    {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        for (int i=0; i < tf.codes.length; i++)
            ids.add(i);
        return ids;
    }

    public SyntheticTagMosaicImageGenerator(TagFamily tf, int _width, int _height, double tagSizeMeters,
                                            ArrayList<Integer> tagsToDisplay)
    {
        // parameters
        this.canvasWidth    = 2 * _width;
        this.canvasHeight   = 2 * _height;
        this.imageWidth     = _width;
        this.imageHeight    = _height;
        this.tf             = tf;
        this.detector       = new TagDetector(tf);
        this.tagSizeMeters  = tagSizeMeters;
        this.tagsToDisplay = tagsToDisplay;
        if (this.tagsToDisplay == null)
            this.tagsToDisplay = getAllTagIds(tf);
        this.mosaic = new TagMosaic(this.tf, this.tagSizeMeters);

        // gui
        vw = new VisWorld();
        vl = new VisLayer(vw);
        // standard caltech coordinate frame
        ((DefaultCameraManager) vl.cameraManager).interfaceMode = 3.0;
        vl.cameraManager.uiLookAt(new double[] {  0.0,  0.0,  0.0 }, // eye
                                  new double[] {  0.0,  0.0,  1.0 }, // lookAt
                                  new double[] {  0.0, -1.0,  0.0 }, // up
                                  true);

        vc = new VisCanvas(vl);
        vc.setSize(canvasWidth, canvasHeight);
        vc.privateLayer.setEnabled(false);

        // get the extreme edges for the specified tag mosaic
        mosaic_xmin = mosaic.getPositionPixels(this.tagsToDisplay.get(0))[0] - mosaic.getTagWidthPixels() /2.0;
        mosaic_xmax = mosaic.getPositionPixels(this.tagsToDisplay.get(0))[0] + mosaic.getTagWidthPixels() /2.0;
        mosaic_ymin = mosaic.getPositionPixels(this.tagsToDisplay.get(0))[1] - mosaic.getTagHeightPixels()/2.0;
        mosaic_ymax = mosaic.getPositionPixels(this.tagsToDisplay.get(0))[1] + mosaic.getTagHeightPixels()/2.0;
        for (Integer id : this.tagsToDisplay) {
            mosaic_xmin = Math.min(mosaic_xmin, mosaic.getPositionPixels(id)[0] - mosaic.getTagWidthPixels() /2.0);
            mosaic_xmax = Math.max(mosaic_xmax, mosaic.getPositionPixels(id)[0] + mosaic.getTagWidthPixels() /2.0);
            mosaic_ymin = Math.min(mosaic_ymin, mosaic.getPositionPixels(id)[1] - mosaic.getTagHeightPixels()/2.0);
            mosaic_ymax = Math.max(mosaic_ymax, mosaic.getPositionPixels(id)[1] + mosaic.getTagHeightPixels()/2.0);
        }

        // camera settings
        double fov_deg = vl.cameraManager.getCameraTarget().perspective_fovy_degrees;
        double f = (canvasHeight / 2.0) / Math.tan(Math.PI/180.0 * fov_deg/2);

        K = new double[][] { { f, 0, canvasWidth /2 - 0.5 },
                             { 0, f, canvasHeight/2 - 0.5 },
                             { 0, 0, 1                    } };

        input = new DistortionFreeCalibration(new double[] {K[0][0], K[1][1]},
                                              new double[] {K[0][2], K[1][2]},
                                              canvasWidth, canvasHeight);

        outputRectified = new DistortionFreeCalibration(new double[] {K[0][0], K[1][1]},
                                                        new double[] {K[0][2]/2, K[1][2]/2},
                                                        imageWidth, imageHeight);
    }

    /** return the intrinsics for the rectified image.
      */
    public double[][] getIntrinsics()
    {
        return outputRectified.copyIntrinsics();
    }

    /** Generate a synthetic image using the MosaicToGlobal XYZRPY exactly as specified.
      */
    public SyntheticImages generateImageNotCentered(Calibration outputDistorted,
                                                    double xyzrpy[],
                                                    boolean drawTagCenters)
    {
        double scale = tagSizeMeters / mosaic.getTagWidthPixels();

        double MosaicPixelsToGlobal[][] = LinAlg.multiplyMany(LinAlg.xyzrpyToMatrix(xyzrpy),
                                                              LinAlg.scale(scale, scale, 1),
                                                              LinAlg.translate(-mosaic.getTagWidthPixels() /2.0,
                                                                               -mosaic.getTagHeightPixels()/2.0,
                                                                                0                              ));
        double MosaicToGlobal[][] = LinAlg.xyzrpyToMatrix(xyzrpy);

        return generateImage(outputDistorted, MosaicPixelsToGlobal, MosaicToGlobal, drawTagCenters);
    }

    /** Generate a synthetic image using the MosaicToGlobal XYZRPY specified
      * <b>for the center of the tag mosaic</b> (not the normal origin of the TagMosaic frame).
      * The final MosaicToGlobal transformation can be recovered from the returned SyntheticImages
      * object. This transformation incorporates a translation that puts the center of the rendered
      * tag mosaic at the point specified by the user.
      */
    public SyntheticImages generateImageCentered(Calibration outputDistorted,
                                                 double xyzrpy[],
                                                 boolean drawTagCenters)
    {
        double scale = tagSizeMeters / mosaic.getTagWidthPixels();

        double MosaicPixelsToGlobal[][] = LinAlg.multiplyMany(LinAlg.xyzrpyToMatrix(xyzrpy),
                                                              LinAlg.scale(scale, scale, 1),
                                                              LinAlg.translate(-(mosaic_xmin+mosaic_xmax)/2,
                                                                               -(mosaic_ymin+mosaic_ymax)/2,
                                                                                0                          ));
        double MosaicToGlobal[][] = LinAlg.multiplyMany(LinAlg.xyzrpyToMatrix(xyzrpy),
                                                        LinAlg.translate(-scale*(mosaic_xmin+mosaic_xmax)/2,
                                                                         -scale*(mosaic_ymin+mosaic_ymax)/2,
                                                                          0                                ),
                                                        LinAlg.translate(scale*mosaic.getTagWidthPixels() /2.0,
                                                                         scale*mosaic.getTagHeightPixels()/2.0,
                                                                         0                                    ));

        return generateImage(outputDistorted, MosaicPixelsToGlobal, MosaicToGlobal, drawTagCenters);
    }

    private SyntheticImages generateImage(Calibration outputDistorted,
                                          double MosaicPixelsToGlobal[][],
                                          double MosaicToGlobal[][],
                                          boolean drawTagCenters)
    {
        SyntheticImages images = new SyntheticImages();

        images.MosaicToGlobal = LinAlg.matrixToXyzrpy(MosaicToGlobal);

        ArrayList<double[]> tagPositionsGlobal = new ArrayList<double[]>();
        for (int id : tagsToDisplay)
            tagPositionsGlobal.add(LinAlg.transform(MosaicToGlobal,
                                                    mosaic.getPositionMeters(id)));

        vb = vw.getBuffer("Image");
        for (int i=0; i < tagsToDisplay.size(); i++) {

            int id              = tagsToDisplay.get(i);
            double p[]          = mosaic.getPositionPixels(id);
            BufferedImage tag   = mosaic.getImage(id);

            VisTexture vt = new VisTexture(tag, VisTexture.NO_MIN_FILTER |
                                                VisTexture.NO_MAG_FILTER |
                                                VisTexture.NO_REPEAT |
                                                VisTexture.NO_ALPHA_MASK);
            int flags = 0;
            VzImage vzim = new VzImage(vt, flags);
            vb.addBack(new VisChain(MosaicPixelsToGlobal,
                                    LinAlg.translate(p[0] - 0.5 * mosaic.getTagWidthPixels()  ,
                                                     p[1] - 0.5 * mosaic.getTagHeightPixels() ,
                                                     0                                        ),
                                    vzim));
        }
        vb.swap();

        vb = vw.getBuffer("Points");
        if (drawTagCenters)
            vb.addBack(new VisDepthTest(false,
                             new VzPoints(new VisVertexData(tagPositionsGlobal),
                                          new VzPoints.Style(Color.green, 10))));
        vb.swap();

        // render
        vc.drawSync();

        // get and save frame
        BufferedImage frame = new BufferedImage(canvasWidth, canvasHeight,
                                                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = (Graphics2D) frame.getGraphics();
        vc.paintComponent(g);

        if (true) {
            Rasterizer rasterizer = new BilinearRasterizer(input, outputRectified);
            images.rectified = rasterizer.rectifyImage(frame);

            images.predictedTagCenters_rectified = CameraMath.project(outputRectified,
                                                                      null,
                                                                      tagPositionsGlobal);
        }

        if (outputDistorted != null) {
            Rasterizer rasterizer = new BilinearRasterizer(input, outputDistorted);
            images.distorted = rasterizer.rectifyImage(frame);

            images.predictedTagCenters_distorted = CameraMath.project(outputDistorted,
                                                                      null,
                                                                      tagPositionsGlobal);
        }

        images.tagids = new int[tagsToDisplay.size()];
        for (int i=0; i < tagsToDisplay.size(); i++)
            images.tagids[i] = tagsToDisplay.get(i);

        return images;
    }

    private static class SIGenGUI implements ParameterListener
    {
        JFrame          jf;
        ParameterGUI    pg;
        JImage          jimr;
        JImage          jimd;
        JFileChooser    chooser;

        SyntheticTagMosaicImageGenerator gen;
        int width, height;

        Random r = new Random(1461234L);

        SyntheticImages lastImages;
        double lastXyzrpy[];

        long lastUtime = 0L;

        public SIGenGUI(SyntheticTagMosaicImageGenerator gen, int width, int height)
        {
            this.gen = gen;
            this.width = width;
            this.height = height;

            pg = new ParameterGUI();
            pg.addCheckBoxes("showpoints","Show predicted tag centers (vis)",false,
                             "showPredictedTagCenters","Show predicted tag centers (image coordinates)", false,
                             "usecentered","Center mosaic for rendering",true);
            pg.addDoubleSlider("k1", "Distortion k1", -2, 2, -0.4);
            pg.addDoubleSlider("k2", "Distortion k2", -2, 2,  0.2);
            pg.addButtons("norotation","Render without rotation","step","Sample new mosaic position","save","Save image pair");
            pg.addListener(this);

            jf = new JFrame("Synthetic image generator");
            jf.setLayout(new BorderLayout());

            jimr = new JImage();
            jimr.setFit(true);
            jimd = new JImage();
            jimd.setFit(true);

            chooser = new JFileChooser();
            javax.swing.filechooser.FileNameExtensionFilter filter =
                    new javax.swing.filechooser.FileNameExtensionFilter("PNG images", "png");
            chooser.setFileFilter(filter);

            JSplitPane imagePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jimr, jimd);
            //JSplitPane imagePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jimr, gen.vc); // debugging only - messes up the camera settings
            imagePane.setDividerLocation(0.5);
            imagePane.setResizeWeight(0.5);

            JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, imagePane, pg);
            pane.setDividerLocation(1.0);
            pane.setResizeWeight(1.0);

            jf.add(pane, BorderLayout.CENTER);
            jf.setSize(2*width + 100, height + 200);
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            jf.setVisible(true);

            // initial image
            double xyzrpy[] = new double[] { 0, 0, 0.3, -0.15, 0.8, 0.35 };
            generate(xyzrpy);
            lastXyzrpy = xyzrpy;
        }

        public void parameterChanged(ParameterGUI pg, String name)
        {
            if (name.equals("norotation")) {
                double xyzrpy[] = new double[] {0, 0, 0.3, 0, 0, 0};
                generate(xyzrpy);
                lastXyzrpy = xyzrpy;
                return;
            }

            if (name.equals("step")) {
                double xyzrpy[] = new double[] {-0.1 + 0.2*r.nextDouble(),
                                                -0.1 + 0.2*r.nextDouble(),
                                                 0.2 + 0.4*r.nextDouble(),
                                                -0.4 + 0.8*r.nextDouble(),
                                                -0.4 + 0.8*r.nextDouble(),
                                                -0.4 + 0.8*r.nextDouble() };

                generate(xyzrpy);

                lastXyzrpy = xyzrpy;

                return;
            }

            if (name.equals("save")) {

                if (lastImages != null) {

                    int returnVal = chooser.showSaveDialog(jf);

                    if(returnVal == JFileChooser.APPROVE_OPTION) {
                        File f = chooser.getSelectedFile();
                        String path = f.getPath();

                        String base = path;
                        if (path.endsWith(".png"))
                            base = path.substring(0, path.length()-4);

                        {
                            String toks[] = base.split("/");
                            String shortname = toks[toks.length-1];
                            System.out.printf("%s_truth_ext = [%12.6f, %12.6f, %12.6f, %12.6f, %12.6f, %12.6f];\n",
                                              shortname,
                                              lastImages.MosaicToGlobal[0],
                                              lastImages.MosaicToGlobal[1],
                                              lastImages.MosaicToGlobal[2],
                                              lastImages.MosaicToGlobal[3],
                                              lastImages.MosaicToGlobal[4],
                                              lastImages.MosaicToGlobal[5]);
                        }

                        try {
                            ImageIO.write(lastImages.rectified, "png", new File(String.format("%s.rectified.png", base)));
                            ImageIO.write(lastImages.distorted, "png", new File(String.format("%s.distorted.png", base)));

                        } catch (Exception ex) {
                            System.err.println("Failed to write images.");
                        }
                    }
                }

                return;
            }

            long utime = TimeUtil.utime();
            if ((utime - lastUtime) > 500000L) {
                lastUtime = utime;

                generate(lastXyzrpy);
            }
        }

        private void generate(double xyzrpy[])
        {
            double K[][] = gen.getIntrinsics();
            Calibration output = new RadialPolynomialCalibration(new double[] {K[0][0], K[1][1]},
                                                                 new double[] {K[0][2], K[1][2]},
                                                                 new double[] {pg.gd("k1"), pg.gd("k2")},
                                                                 width, height);

            SyntheticTagMosaicImageGenerator.SyntheticImages images = null;
            if (pg.gb("usecentered"))
                images = gen.generateImageCentered(output, xyzrpy, pg.gb("showpoints"));
            else
                images = gen.generateImageNotCentered(output, xyzrpy, pg.gb("showpoints"));

            if (pg.gb("showPredictedTagCenters")) {
                int buf[] = ((DataBufferInt) (images.rectified.getRaster().getDataBuffer())).getData();
                int w = images.rectified.getWidth();
                int h = images.rectified.getHeight();

                for (double p[] : images.predictedTagCenters_rectified) {
                    int x = (int) p[0];
                    int y = (int) p[1];

                    int s = 2;
                    for (int yy=y-s; yy<=y+s; yy++)
                        for (int xx=x-s; xx<=x+s; xx++)
                            if (xx >= 0 && xx < w && yy >= 0 && yy < h)
                                buf[yy*w+xx] = 0xFF0000FF;
                }
            }

            if (pg.gb("showPredictedTagCenters")) {
                int buf[] = ((DataBufferInt) (images.distorted.getRaster().getDataBuffer())).getData();
                int w = images.distorted.getWidth();
                int h = images.distorted.getHeight();

                for (double p[] : images.predictedTagCenters_distorted) {
                    int x = (int) p[0];
                    int y = (int) p[1];

                    int s = 2;
                    for (int yy=y-s; yy<=y+s; yy++)
                        for (int xx=x-s; xx<=x+s; xx++)
                            if (xx >= 0 && xx < w && yy >= 0 && yy < h)
                                buf[yy*w+xx] = 0xFF0000FF;
                }
            }

            jimr.setImage(images.rectified);
            jimd.setImage(images.distorted);

            lastImages = images;
        }
    }

    public static void main(String args[])
    {
        TagFamily tf = new Tag36h11();
        int width = 752;
        int height = 480;

        // tags on a standard 1-page print of the tag mosaic
        double pitch = 0.0254;
        TagMosaic mosaic = new TagMosaic(tf, pitch);
        ArrayList<Integer> tagsToDisplay = new ArrayList<Integer>();
        for (int row=0; row < 5; row++)
            for (int col=0; col < 7; col++)
                tagsToDisplay.add(mosaic.getID(col, row));

        SyntheticTagMosaicImageGenerator gen = new SyntheticTagMosaicImageGenerator(tf, width, height, pitch,
                                                                                    tagsToDisplay);

        SIGenGUI gui = new SIGenGUI(gen, width, height);
    }
}
