package april.camera.tools;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;

import javax.swing.*;
import javax.imageio.*;

import april.config.*;
import april.camera.*;
import april.vis.*;
import april.util.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.jcam.*;

public class StereoEpipolarCurveViewer implements ParameterListener
{
    Config config;
    CameraSet cameras;
    View leftView;
    View rightView;

    JFrame jf;
    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;

    VisWorld.Buffer vbleft;
    VisWorld.Buffer vbright;

    ParameterGUI pg;

    BufferedImage leftIm, rightIm;

    public StereoEpipolarCurveViewer(Config config, String leftPath, String rightPath)
    {
        if (config == null) {
            System.err.println("Config object is null. Exiting.");
            System.exit(-1);
        }

        this.config = config;
        this.cameras = new CameraSet(config);

        leftView = cameras.getCalibration(0);
        rightView = cameras.getCalibration(1);

        setupGUI();

        showImages(leftPath, rightPath);
    }

    private void setupGUI()
    {
        // parametergui
        pg = new ParameterGUI();
        pg.addListener(this);

        // vis
        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);

        vl.addEventHandler(new EventAdapter());

        vl.cameraManager.getCameraTarget().perspectiveness = 0;
        vc.setBackground(Color.black);
        vl.cameraManager.fit2D(getXY(0, "left"), getXY(1, "right"), true);

        vbleft = vw.getBuffer("left");
        vbright = vw.getBuffer("right");

        // jframe
        jf = new JFrame("Stereo Epipolar Curve Viewer");
        jf.setLayout(new BorderLayout());

        JSplitPane jspv = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vc, pg);
        jspv.setDividerLocation(1.0);
        jspv.setResizeWeight(1.0);

        jf.add(jspv, BorderLayout.CENTER);
        jf.setSize(1000,400);

        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }

    private BufferedImage loadImage(String path) throws IOException
    {
        BufferedImage im = ImageIO.read(new File(path));
        im = ImageConvert.convertImage(im, BufferedImage.TYPE_INT_RGB);

        return im;
    }

    private void showImages(String leftPath, String rightPath)
    {
        VisWorld.Buffer vb = vw.getBuffer("Images");
        vb.setDrawOrder(-100);

        try {
            double XY0[] = getXY(0, "left");
            double XY1[] = getXY(1, "left");
            BufferedImage im = loadImage(leftPath);
            vb.addBack(new VisChain(LinAlg.translate(XY0[0], XY0[1], 0),
                                    LinAlg.scale((XY1[0]-XY0[0])/im.getWidth(),
                                                 (XY1[1]-XY0[1])/im.getHeight(), 1),
                                    new VzImage(im, VzImage.FLIP)));
            leftIm = im;

        } catch (IOException ex) {
        }

        try {
            double XY0[] = getXY(0, "right");
            double XY1[] = getXY(1, "right");
            BufferedImage im = loadImage(rightPath);
            vb.addBack(new VisChain(LinAlg.translate(XY0[0], XY0[1], 0),
                                    LinAlg.scale((XY1[0]-XY0[0])/im.getWidth(),
                                                 (XY1[1]-XY0[1])/im.getHeight(), 1),
                                    new VzImage(im, VzImage.FLIP)));
            rightIm = im;

        } catch (IOException ex) {
        }

        vb.swap();
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
    }

    private final class EventAdapter extends VisEventAdapter
    {
        private final double leftXY0[];
        private final double leftXY1[];
        private final double rightXY0[];
        private final double rightXY1[];

        private EventAdapter()
        {
            this.leftXY0 = getXY(0, "left");
            this.leftXY1 = getXY(1, "left");
            this.rightXY0 = getXY(0, "right");
            this.rightXY1 = getXY(1, "right");
        }

        @Override
        public boolean mouseMoved(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
        {
            double vis_xy[] = ray.intersectPlaneXY(0);

            // left image?
            if (vis_xy[0] >= leftXY0[0] && vis_xy[0] < leftXY1[0] &&
                vis_xy[1] >= leftXY0[1] && vis_xy[1] < leftXY1[1])
            {
                double corrected_xy[] = new double[] { vis_xy[0] - leftXY0[0],
                                                       vis_xy[1] - leftXY0[1]};

                double im_xy[] = new double[] {  vis_xy[0] - leftXY0[0] ,
                                                leftXY1[1] -  vis_xy[1] };

                redraw(vbleft, vbright,
                       leftIm, rightIm,
                       leftXY0, leftXY1, rightXY0, rightXY1,
                       corrected_xy, im_xy,
                       leftView, cameras.getExtrinsicsL2C(0),
                       rightView, cameras.getExtrinsicsL2C(1));

                return true;
            }

            // right image?
            if (vis_xy[0] >= rightXY0[0] && vis_xy[0] < rightXY1[0] &&
                vis_xy[1] >= rightXY0[1] && vis_xy[1] < rightXY1[1])
            {
                double corrected_xy[] = new double[] { vis_xy[0] - rightXY0[0],
                                                       vis_xy[1] - rightXY0[1]};

                double im_xy[] = new double[] {   vis_xy[0] - rightXY0[0] ,
                                                rightXY1[1] -   vis_xy[1] };

                redraw(vbright, vbleft,
                       rightIm, leftIm,
                       rightXY0, rightXY1, leftXY0, leftXY1,
                       corrected_xy, im_xy,
                       rightView, cameras.getExtrinsicsL2C(1),
                       leftView, cameras.getExtrinsicsL2C(0));

                return true;
            }

            // it wasn't in either image, so we can't do anything
            return false;
        }
    }

    private double[] getXY(int zeroone, String leftright)
    {
        int leftWidth   = leftView.getWidth();
        int leftHeight  = leftView.getHeight();
        int rightWidth  = rightView.getWidth();
        int rightHeight = rightView.getHeight();

        switch(zeroone) {
            case 0:
                if (leftright.equals("left")) {
                    // left
                    return new double[] { 0                   , 0          };
                } else {
                    // right
                    return new double[] { leftWidth           , 0          };
                }
            case 1:
                if (leftright.equals("left")) {
                    // left
                    return new double[] { leftWidth           , leftHeight };
                } else {
                    // right
                    return new double[] { leftWidth+rightWidth, rightHeight};
                }
        }

        return null;
    }

    public void redraw(VisWorld.Buffer vbactive, VisWorld.Buffer vbpassive,
                       BufferedImage activeIm,   BufferedImage passiveIm,
                       double activeXY0[],       double activeXY1[],
                       double passiveXY0[],      double passiveXY1[],
                       double active_xy[],       double im_xy[],
                       View activeCal,           double activeG2C[][],
                       View passiveCal,          double passiveG2C[][])
    {
        drawBox(vbactive, activeXY0, activeXY1, Color.blue);
        drawBox(vbpassive, passiveXY0, passiveXY1, Color.red);

        vbactive.addBack(new VzPoints(new VisVertexData(LinAlg.add(activeXY0, active_xy)),
                                      new VzPoints.Style(Color.green, 4)));

        drawEpipolarCurve(vbpassive, passiveXY0, passiveXY1, im_xy,
                          activeCal, activeG2C,
                          passiveCal, passiveG2C,
                          activeIm, passiveIm);

        vbactive.swap();
        vbpassive.swap();
    }

    public void drawBox(VisWorld.Buffer vb, double XY0[], double XY1[], Color c)
    {
        ArrayList<double[]> points = new ArrayList<double[]>();
        points.add(new double[] {XY0[0], XY0[1]});
        points.add(new double[] {XY0[0], XY1[1]});
        points.add(new double[] {XY1[0], XY1[1]});
        points.add(new double[] {XY1[0], XY0[1]});

        vb.addBack(new VzLines(new VisVertexData(points),
                               VzLines.LINE_LOOP,
                               new VzLines.Style(c, 1)));
    }

    public void drawEpipolarCurve(VisWorld.Buffer vb, double XY0[], double XY1[], double xy_dp[],
                                  View activeCal,           double activeG2C[][],
                                  View passiveCal,          double passiveG2C[][],
                                  BufferedImage activeIm,   BufferedImage passiveIm)
    {
        double xyz_r[] = activeCal.pixelsToRay(xy_dp);

        ArrayList<double[]> points = new ArrayList<double[]>();
        ArrayList<double[]> depths = new ArrayList<double[]>();
        ArrayList<double[]> colorError = new ArrayList<double[]>();

        // sample depths from 0 to zmax meters
        double zmax = 10;
        double lastvisxy[] = null;
        for (double z=0.0; z < zmax; z += 0.01) {

            double xyz_cam[] = LinAlg.scale(xyz_r, z);

            double xyz_global[] = LinAlg.transform(LinAlg.inverse(activeG2C),
                                                   xyz_cam);

            double xy[] = CameraMath.project(passiveCal, passiveG2C, xyz_global);

            if (xy[0] >= 0 && xy[0] < passiveCal.getWidth() &&
                xy[1] >= 0 && xy[1] < passiveCal.getHeight())
            {
                double visxy[] = new double[] { xy[0],
                                                passiveCal.getHeight() - xy[1] };

                if ((lastvisxy != null) && (LinAlg.distance(visxy, lastvisxy) < 1))
                    continue;

                points.add(visxy);
                depths.add(new double[] {z});

                if (activeIm != null && passiveIm != null) {
                    colorError.add(getColorError(activeIm, passiveIm,
                                                 xy_dp, xy, z));
                }

                lastvisxy = visxy;
            }
        }


        VisColorData colors = ColorMapper.makeJet(0, zmax).makeColorData(depths, 0);
        vb.addBack(new VzPoints(new VisVertexData(LinAlg.transform(LinAlg.translate(XY0[0], XY0[1], 0),
                                                                  points)),
                                new VzPoints.Style(colors, 2)));

        if (activeIm != null && passiveIm != null) {
            double maxDepth = 0;
            double maxError = 0;

            for (double de[] : colorError) {
                maxDepth = Math.max(maxDepth, de[0]);
                maxError = Math.max(maxError, de[1]);
            }

            String str = "<<monospaced-12,white>>Color error (Y) as a function of depth (X)";
            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                        new VisChain(LinAlg.translate(200, 100, 0),
                                                     new VzRectangle(400, 200, new VzMesh.Style(new Color(10, 10, 10)))),
                                        new VisChain(LinAlg.scale(400 / maxDepth,
                                                                  200 / maxError,
                                                                  1),
                                                     new VzLines(new VisVertexData(colorError),
                                                                 VzLines.LINE_STRIP,
                                                                 new VzLines.Style(colors, 1))),
                                        new VisChain(LinAlg.translate(0, 200, 0),
                                                     new VzText(VzText.ANCHOR.BOTTOM_LEFT, str))));
        }
    }

    public double[] getColorError(BufferedImage activeIm, BufferedImage passiveIm,
                                  double xy_active[], double xy_passive[], double depth)
    {
        int activeBuf[] = ((DataBufferInt) (activeIm.getRaster().getDataBuffer())).getData();
        int activeWidth = activeIm.getWidth();
        int activeHeight = activeIm.getHeight();
        int xactive = (int) xy_active[0];
        int yactive = (int) xy_active[1];

        int passiveBuf[] = ((DataBufferInt) (passiveIm.getRaster().getDataBuffer())).getData();
        int passiveWidth = passiveIm.getWidth();
        int passiveHeight = passiveIm.getHeight();
        int xpassive = (int) xy_passive[0];
        int ypassive = (int) xy_passive[1];

        int vactive = activeBuf[yactive * activeWidth + xactive];
        int vpassive = passiveBuf[ypassive * passiveWidth + xpassive];

        int ractive = ((vactive >> 16) & 0xFF);
        int gactive = ((vactive >>  8) & 0xFF);
        int bactive = ((vactive      ) & 0xFF);

        int rpassive = ((vpassive >> 16) & 0xFF);
        int gpassive = ((vpassive >>  8) & 0xFF);
        int bpassive = ((vpassive      ) & 0xFF);

        double error = Math.sqrt(Math.pow(ractive - rpassive, 2) +
                                 Math.pow(gactive - gpassive, 2) +
                                 Math.pow(bactive - bpassive, 2) );

        return new double[] { depth, error };
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('c',"config","","StereoCamera config");
        opts.addString('s',"child","aprilCameraCalibration","Camera calibration child (e.g. aprilCameraCalibration)");
        opts.addString('l',"leftimage","","Left image path (optional)");
        opts.addString('r',"rightimage","","Right image path (optional)");

        if (!opts.parse(args)) {
            System.out.println("option error: "+opts.getReason());
	    }

        String configstr = opts.getString("config");
        String childstr = opts.getString("child");
        String leftimagepath = opts.getString("leftimage");
        String rightimagepath = opts.getString("rightimage");

        if (opts.getBoolean("help") || configstr.isEmpty()){
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        try {
            Config config = new ConfigFile(configstr);

            new StereoEpipolarCurveViewer(config.getChild(childstr),
                                          leftimagepath, rightimagepath);
        } catch (IOException e) {
            System.err.println("ERR: "+e);
            System.exit(-1);
        }
    }
}
