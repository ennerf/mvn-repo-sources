package april.camera.tools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.swing.*;

import april.camera.*;
import april.config.*;
import april.jcam.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.tag.*;
import april.util.*;
import april.vis.*;

public class EstimateIntrinsics implements ParameterListener
{
    ArrayList<String>               paths           = new ArrayList<String>();
    ArrayList<BufferedImage>        images          = new ArrayList<BufferedImage>();
    ArrayList<List<TagDetection>>   allDetections   = new ArrayList<List<TagDetection>>();

    IntrinsicsEstimator ie;

    TagFamily tf;
    TagMosaic tm;
    TagDetector td;

    JFrame          jf;
    VisWorld        vw;
    VisLayer        vl;
    VisCanvas       vc;
    ParameterGUI    pg;
    VisWorld.Buffer vb;

    double XY0[];
    double XY1[];
    double PixelsToVis[][];

    int imwidth  = -1;
    int imheight = -1;

    public EstimateIntrinsics(String dirpath, TagFamily tf)
    {
        // check directory
        File dir = new File(dirpath);
        if (!dir.isDirectory()) {
            System.err.println("Not a directory: " + dirpath);
            System.exit(-1);
        }

        // get images
        for (String child : dir.list()) {
            String childpath = dirpath+"/"+child;
            String tmp = childpath.toLowerCase();
            if (tmp.endsWith("jpeg") || tmp.endsWith("jpg") || tmp.endsWith("png") ||
                tmp.endsWith("bmp") || tmp.endsWith("wbmp") || tmp.endsWith("gif"))
                paths.add(childpath);
        }
        Collections.sort(paths);

        try {
            for (String path : paths) {
                BufferedImage im = ImageIO.read(new File(path));

                if (imwidth == -1 || imheight == -1) {
                    imwidth = im.getWidth();
                    imheight = im.getHeight();
                }

                assert(imwidth == im.getWidth() && imheight == im.getHeight());

                images.add(im);
            }
        } catch (IOException ex) {
            System.err.println("Exception while loading images: " + ex);
            System.exit(-1);
        }

        // detect tags
        this.tf = tf;
        this.td = new TagDetector(tf);
        this.tm = new TagMosaic(tf, 0.0254);

        for (BufferedImage im : images)
            allDetections.add(td.process(im, new double[] { imwidth/2, imheight/2 }));

        // setup GUI
        setupGUI();

        // estimate the intrinsics
        ie = new IntrinsicsEstimator(allDetections, tm, imwidth/2, imheight/2);

        double K[][] = ie.getIntrinsics();

        if (K == null) {
            System.out.println("Could not estimate intrinsics - K returned from CameraMath is null");
        }
        else {
            System.out.println("Estimated intrinsics matrix K:");
            LinAlg.print(K);
        }

        // for render once
        parameterChanged(pg, "image");
    }

    private void setupGUI()
    {
        assert(images.size() == paths.size());
        assert(images.size() == allDetections.size());

        pg = new ParameterGUI();
        pg.addIntSlider("image", "Selected image", 0, images.size()-1, 0);
        pg.addListener(this);

        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);

        jf = new JFrame("Camera intrinsics estimator");
        jf.setLayout(new BorderLayout());

        JSplitPane jspane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vc, pg);
        jspane.setDividerLocation(1.0);
        jspane.setResizeWeight(1.0);

        jf.add(jspane, BorderLayout.CENTER);
        jf.setSize(1200, 600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        BufferedImage image = images.get(0);
        XY0 = new double[2];
        XY1 = new double[] { image.getWidth(), image.getHeight() };
        PixelsToVis = CameraMath.makeVisPlottingTransform(image.getWidth(), image.getHeight(),
                                                          XY0, XY1, true);
        vl.cameraManager.fit2D(XY0, XY1, true);
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("image"))
            draw();
    }

    private void draw()
    {
        int n = pg.gi("image");

        String path                         = paths.get(n);
        BufferedImage image                 = images.get(n);
        List<TagDetection> detections  = allDetections.get(n);

        assert(images.size() == ie.getVanishingPoints().size());
        double vp[][]                   = ie.getVanishingPoints().get(n);
        ArrayList<double[][]> fitLines  = ie.getFitLines(n);

        vb = vw.getBuffer("Image");
        vb.addBack(new VisChain(PixelsToVis,
                                new VzImage(image)));
        vb.swap();

        vb = vw.getBuffer("HUD");
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                    new VzText(VzText.ANCHOR.TOP_LEFT,
                                               String.format("<<monospaced-12>>%s\n%d detections",
                                                             path, detections.size()))));
        vb.swap();

        vb = vw.getBuffer("Fit lines");
        if (fitLines != null)
            for (double line[][] : fitLines)
                vb.addBack(new VisChain(PixelsToVis,
                                        new VzLines(new VisVertexData(line),
                                                    VzLines.LINE_STRIP,
                                                    new VzLines.Style(Color.yellow, 2))));
        vb.swap();

        vb = vw.getBuffer("Detections");
        for (TagDetection d : detections) {
            double p0[] = d.interpolate(-1,-1);
            double p1[] = d.interpolate( 1,-1);
            double p2[] = d.interpolate( 1, 1);
            double p3[] = d.interpolate(-1, 1);

            vb.addBack(new VisChain(PixelsToVis,
                                    new VzLines(new VisVertexData(p0, p1, p2, p3, p0),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(Color.blue, 4)),
                                    new VzLines(new VisVertexData(p0,p1),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(Color.green, 4)),
                                    new VzLines(new VisVertexData(p0, p3),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(Color.red, 4))));
        }
        vb.swap();

        vb = vw.getBuffer("Vanishing points");
        if (vp != null) {
            vb.addBack(new VisChain(PixelsToVis,
                                    new VzPoints(new VisVertexData(vp[0]),
                                                 new VzPoints.Style(Color.green, 8))));
            vb.addBack(new VisChain(PixelsToVis,
                                    new VzPoints(new VisVertexData(vp[1]),
                                                 new VzPoints.Style(Color.red, 8))));
        } else {
            VzText text = new VzText(VzText.ANCHOR.CENTER,
                                     "<<sansserif-20>>No vanishing points available for this frame");
            vb.addBack(new VisChain(LinAlg.translate((XY1[0]+XY0[0])/2,
                                                     (XY1[1]+XY0[1])/2, 0),
                                    new VzRectangle(XY1[0]-XY0[0], XY1[1]-XY0[1],
                                                    new VzMesh.Style(new Color(0, 0, 0, 200))),
                                    text));
        }
        vb.swap();
    }

    public static void main(String args[])
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h',"help",false,"See the help screen");
        opts.addString('d',"dir",".","Directory of images containing the AprilTag camera calibration mosaic or a single AprilTag. Accepted formats: jpeg, jpg, png, bmp, wbmp, gif");
        opts.addString('f',"tagfamily","april.tag.Tag36h11","AprilTag family");

        if (!opts.parse(args)) {
            System.out.println("Option error: " + opts.getReason());
        }

        String dir = opts.getString("dir");
        String tagfamily = opts.getString("tagfamily");

        if (opts.getBoolean("help")) {
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(-1);
        }

        TagFamily tf = (TagFamily) ReflectUtil.createObject(tagfamily);

        if (tf == null) {
            System.err.printf("Invalid tag family '%s'\n", tagfamily);
            System.exit(-1);
        }

        new EstimateIntrinsics(dir, tf);
    }
}
