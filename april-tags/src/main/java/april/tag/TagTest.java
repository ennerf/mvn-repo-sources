package april.tag;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import april.jmat.*;
import april.jmat.geom.*;

import april.vis.*;
import april.jcam.*;

import april.util.*;

public class TagTest implements ParameterListener
{
    JFrame jf;
    VisWorld  vw = new VisWorld();
    VisLayer vl = new VisLayer(vw);
    VisCanvas vc = new VisCanvas(vl);

    VisWorld vw2 = new VisWorld();
    VisLayer vl2 = new VisLayer(vw2);
    VisCanvas vc2 = new VisCanvas(vl2);

    ImageSource is;

    ParameterGUI pg;

    TagFamily tf;
    TagDetector detector;

    // usage: TagTest <imagesource> [tagfamily class]
    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();
        opts.addBoolean('h',"help",false,"See this help screen");
//        opts.addString('u',"url","","Camera url");
        opts.addString('t',"tagfamily","april.tag.Tag36h11","Tag family");

        if (!opts.parse(args)) {
            System.out.println("option error: "+opts.getReason());
        }

        ArrayList<String> eargs = opts.getExtraArgs();

        String url;
        if (eargs.size() > 0)
            url = eargs.get(0);
        else
            url = ImageSource.getCameraURLs().get(0);

        String tagfamily = opts.getString("tagfamily");

        if (opts.getBoolean("help") || url.isEmpty()){
            System.out.println("Usage: TagTest [cameraurl]");
            opts.doHelp();
            System.exit(1);
        }

        try {
            ImageSource is = ImageSource.make(url);
            TagFamily tf = (TagFamily) ReflectUtil.createObject(tagfamily);

            TagTest tt = new TagTest(is, tf);

        } catch (IOException ex) {
            System.out.println("Ex: "+ex);
        }
    }

    public TagTest(ImageSource is, TagFamily tf)
    {
        this.is = is;
        this.tf = tf;

        detector = new TagDetector(tf);

        pg = new ParameterGUI();
        pg.addDoubleSlider("segsigma", "smoothing sigma (segmentation)", 0, 2, detector.segSigma);
        pg.addDoubleSlider("sigma", "smoothing sigma (sampling)", 0, 2, detector.sigma);
        pg.addDoubleSlider("minmag", "minimum magnitude", 0.0001, 0.01, detector.minMag);
        pg.addDoubleSlider("maxedgecost", "maximum edge cost (radians)", 0, Math.PI, detector.maxEdgeCost);
        pg.addDoubleSlider("magthresh", "magnitude threshold", 0, 5000, detector.magThresh);
        pg.addDoubleSlider("thetathresh", "theta threshold", 0, 5000, detector.thetaThresh);
        pg.addIntSlider("errorbits", "error recovery (bits)", 0, 5, 1);
        pg.addIntSlider("weightscale", "Weight scale", 1, 100, detector.WEIGHT_SCALE);

        pg.addCheckBoxes("segDecimate", "segmentation decimate", detector.segDecimate,
                         "debug", "debug", false);

        jf = new JFrame("TagTest");
        jf.setLayout(new BorderLayout());

        JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, vc, vc2);
        jsp.setDividerLocation(0.5);
        jsp.setResizeWeight(0.5);

        jf.add(jsp, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);

        vl2.cameraManager.uiLookAt(new double[] {0, -2, 1.65},
                                   new double[] {0, 2, 0},
                                   new double[] {0, .37, 0.927}, true);
        jf.setSize(800,600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        ImageSourceFormat ifmt = is.getCurrentFormat();
        vl.cameraManager.fit2D(new double[] {0,0}, new double[] { ifmt.width, ifmt.height}, true);
        ((DefaultCameraManager) vl.cameraManager).interfaceMode = 2.0;
        vl.backgroundColor = new Color(128,128,128);

        new RunThread().start();

        VzGrid.addGrid(vw2);
        pg.addListener(this);
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
    }

    class RunThread extends Thread
    {
        public void run()
        {
            is.start();
            ImageSourceFormat fmt = is.getCurrentFormat();

            detector = new TagDetector(tf);

            VisWorld.Buffer vbOriginal = vw.getBuffer("unprocessed image");
            VisWorld.Buffer vbSegmentation = vw.getBuffer("segmentation");
            VisWorld.Buffer vbInput = vw.getBuffer("input");
            VisWorld.Buffer vbThetas = vw.getBuffer("thetas");
            VisWorld.Buffer vbMag = vw.getBuffer("mag");
            VisWorld.Buffer vbDetections = vw.getBuffer("detections");
            VisWorld.Buffer vbClock = vw.getBuffer("clock");

            VisWorld.Buffer vbTag3D = vw2.getBuffer("taglocs");

            detector.debugSegments  = vw.getBuffer("segments");
            detector.debugQuads     = vw.getBuffer("quads");
            detector.debugSamples   = vw.getBuffer("samples");
            detector.debugLabels    = vw.getBuffer("labels");

            while (true) {
                FrameData frmd = is.getFrame();
                if (frmd == null)
                    continue;

                BufferedImage im = ImageConvert.convertToImage(frmd);

                tf.setErrorRecoveryBits(pg.gi("errorbits"));

                detector.debug = pg.gb("debug");
                detector.sigma = pg.gd("sigma");
                detector.segSigma = pg.gd("segsigma");
                detector.segDecimate = pg.gb("segDecimate");
                detector.minMag = pg.gd("minmag");
                detector.maxEdgeCost = pg.gd("maxedgecost");
                detector.magThresh = pg.gd("magthresh");
                detector.thetaThresh = pg.gd("thetathresh");
                detector.WEIGHT_SCALE = pg.gi("weightscale");

                Tic tic = new Tic();
                ArrayList<TagDetection> detections = detector.process(im, new double[] {im.getWidth()/2.0, im.getHeight()/2.0});
                double dt = tic.toc();

                if (detector.debugInput!=null)
                    vbInput.addBack(new VisDepthTest(false, new VisLighting(false, new VzImage(detector.debugInput, VzImage.FLIP))));
                vbInput.swap();

                if (detector.debugSegmentation!=null)
                    vbSegmentation.addBack(new VisDepthTest(false, new VisLighting(false, new VzImage(detector.debugSegmentation, VzImage.FLIP))));
                vbSegmentation.swap();


                vbOriginal.addBack(new VisDepthTest(false, new VisLighting(false, new VzImage(im, VzImage.FLIP))));
                vbOriginal.swap();

                if (detector.debugTheta != null)
                    vbThetas.addBack(new VisDepthTest(false, new VisLighting(false, new VzImage(detector.debugTheta, VzImage.FLIP))));
                vbThetas.swap();

                if (detector.debugMag != null)
                    vbMag.addBack(new VisDepthTest(false, new VisLighting(false, new VzImage(detector.debugMag, VzImage.FLIP))));
                vbMag.swap();

                vbClock.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_RIGHT,
                                                        new VzText(VzText.ANCHOR.BOTTOM_RIGHT,
                                                                    String.format("<<cyan>>%8.2f ms", dt*1000))));
                vbClock.swap();

                for (TagDetection d : detections) {
                    double p0[] = d.interpolate(-1,-1);
                    double p1[] = d.interpolate(1,-1);
                    double p2[] = d.interpolate(1,1);
                    double p3[] = d.interpolate(-1,1);

                    double ymax = Math.max(Math.max(p0[1], p1[1]), Math.max(p2[1], p3[1]));

                    vbDetections.addBack(new VisChain(LinAlg.translate(0, im.getHeight(), 0),
                                                      LinAlg.scale(1, -1, 1),
                                                      new VzLines(new VisVertexData(p0, p1, p2, p3, p0),
                                                                  VzLines.LINE_STRIP,
                                                                  new VzLines.Style(Color.blue, 4)),
                                                      new VzLines(new VisVertexData(p0,p1),
                                                                  VzLines.LINE_STRIP,
                                                                  new VzLines.Style(Color.green, 4)),
                                                      new VzLines(new VisVertexData(p0, p3),
                                                                  VzLines.LINE_STRIP,
                                                                  new VzLines.Style(Color.red, 4)),
                                                      new VisChain(LinAlg.translate(d.cxy[0], ymax + 20, 0), //LinAlg.translate(d.cxy[0],d.cxy[1],0),
                                                                   LinAlg.scale(1, -1, 1),
                                                                   LinAlg.scale(.25, .25, .25),
                                                                   new VzText(VzText.ANCHOR.CENTER,
                                                                              String.format("<<sansserif-48,center,yellow,dropshadow=#88000000>>id %3d\n(err=%d)\n", d.id, d.hammingDistance)))));

                    // You need to adjust the tag size (measured
                    // across the whole tag in meters and the focal
                    // length.
                    double tagsize_m = 0.216;
                    double f = 485.6;
                    double aspect = 752.0 / 480.0;
//                    double M[][] = CameraUtil.homographyToPose(f, f, tagsize_m, d.homography);
                    double M[][] = CameraUtil.homographyToPose(f, f, im.getWidth()/2, im.getHeight()/2, d.homography);
                    M = CameraUtil.scalePose(M, 2.0, tagsize_m);

                    BufferedImage tfimg = tf.makeImage(d.id);
                    double vertices[][] = {{ -tagsize_m/2, -tagsize_m/2, 0},
                                           { tagsize_m/2, -tagsize_m/2, 0},
                                           { tagsize_m/2,  tagsize_m/2, 0},
                                           { -tagsize_m/2,  tagsize_m/2, 0}};


                    // same order as in vertices, but remember y flip.
                    double texcoords [][] = { { 0, 1},
                                              { 1, 1},
                                              { 1, 0},
                                              { 0, 0 } };

                    vbTag3D.addBack(new VisChain(LinAlg.rotateX(Math.PI/2),
                                                 M,
                                                 new VzImage(new VisTexture(tfimg, VisTexture.NO_MIN_FILTER),
                                                             vertices, texcoords, null)));
                }

                vbTag3D.addBack(new VisChain(LinAlg.rotateX(Math.PI/2),
                                             new VzAxes()));
                vbTag3D.addBack(new VisChain(LinAlg.rotateZ(Math.PI/2),
                                             LinAlg.scale(.25, .25, .25),
                                             new VzCamera()));
                vbTag3D.swap();

                vbDetections.swap();
            }
        }
    }
}
