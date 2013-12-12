package april.tag;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import april.jmat.*;
import april.jmat.geom.*;
import april.jmat.ordering.*;

import april.vis.*;
import april.jcam.*;

import april.image.*;
import april.util.*;

import april.graph.*;


public class Calibrate implements ParameterListener
{
    JFrame jf;
    VisWorld vw = new VisWorld();
    VisLayer vl = new VisLayer(vw);
    VisCanvas vc = new VisCanvas(vl);

    VisWorld vw2 = new VisWorld();
    VisLayer vl2 = new VisLayer(vw2);
    VisCanvas vc2 = new VisCanvas(vl2);

    VisWorld vw3 = new VisWorld();
    VisLayer vl3 = new VisLayer(vw3);
    VisCanvas vc3 = new VisCanvas(vl3);

    ParameterGUI pg = new ParameterGUI();

    TagFamily tf = new Tag36h11();
    TagDetector td = new TagDetector(tf);

    CaptureThread captureThread;

    ArrayList<Capture> captures = new ArrayList<Capture>();

    // indexed by tag id
    HashMap<Integer, TagPosition> tagPositions = new HashMap<Integer, TagPosition>();

    Graph g;

    IterateThread iterateThread;

    static class TagPosition
    {
        int id;
        double cx, cy;
        double size;
    }

    static class Capture
    {
        BufferedImage im;
        ArrayList<TagDetection> detections;
    }

    public Calibrate(String args[])
    {
        jf = new JFrame("Calibrate");
        jf.setLayout(new BorderLayout());

        pg.addButtons("capture", "capture",
                      "iterate", "iterate",
                      "run", "run/stop");

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new GridLayout(2,1));
        leftPanel.add(vc);
        leftPanel.add(vc2);
        JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, vc3);
        jsp.setDividerLocation(0.5);
        jsp.setResizeWeight(0.5);
        jf.add(jsp, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);
        jf.setSize(800,600);
        jf.setVisible(true);

        VzGrid.addGrid(vw2);

        vl2.cameraManager.uiLookAt(new double[] {         0.61048,        -0.56914,         0.34533 },
                                   new double[] {         0.20330,        -0.17063,         0.00000 },
                                   new double[] {        -0.37045,         0.36255,         0.85517 }, true);

        String url = null;
        if (args.length > 0)
            url = args[0];
        else
            url = ImageSource.getCameraURLs().get(0);

        if (url==null) {
            System.out.println("No camera found or specified.");
            return;
        }

        /////////////////////////
        // Create ground truth
        if (true) {
            int tagsPerRow = 24;
            for (int y = 0; y < 24; y++) {
                for (int x = 0; x < tagsPerRow; x++) {
                    TagPosition tp = new TagPosition();
                    tp.id = y*tagsPerRow + x;

                    if (tp.id >= tf.codes.length)
                        continue;

                    // XXX assumes one inch spacing between tags.
                    tp.cx = x * 0.0254;
                    tp.cy = -y * 0.0254;
                    tp.size = 0.0254 * 8.0 / 10.0;

                    tagPositions.put(tp.id, tp);
                }
            }
        }

        drawGroundTruth();

        captureThread = new CaptureThread(url);
        captureThread.start();

        pg.addListener(this);

        g = new Graph();
    }

    class CaptureThread extends Thread
    {
        String url;

        // protected by synchronizing on CaptureThread
        GCalibrateEdge lastEdge;
        GExtrinsicsNode lastNode;

        boolean firstFrame = true;

        CaptureThread(String url)
        {
            this.url = url;
        }

        public void run()
        {
            boolean first = true;

            try {
                ImageSource isrc = ImageSource.make(url);
                ImageSourceFormat ifmt = isrc.getCurrentFormat();

                isrc.start();

                while (true) {

                    FrameData fd = isrc.getFrame();
                    byte data[] = fd.data;
                    BufferedImage im = ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, data);

                    if (firstFrame) {
                        double f = 400;  // XXX HACK!
                        g.nodes.add(new GIntrinsicsNode(im.getWidth(), im.getHeight(),
                                                        f, f, im.getWidth() / 2.0, im.getHeight() / 2.0));
                        firstFrame = false;
                    }

                    synchronized(this) {
                        lastEdge = null;

                        ArrayList<TagDetection> detections = td.process(im, new double[] {im.getWidth()/2.0, im.getHeight()/2.0});

                        if (true) {
                            VisWorld.Buffer vb = vw.getBuffer("camera");
                            vb.addBack(new VisChain(LinAlg.translate(0, im.getHeight(), 0),
                                                        LinAlg.scale(1, -1, 1),
                                                        new VzImage(im)));

                            for (TagDetection d : detections) {
                                double p0[] = d.interpolate(-1,-1);
                                double p1[] = d.interpolate(1,-1);
                                double p2[] = d.interpolate(1,1);
                                double p3[] = d.interpolate(-1,1);

                                vb.addBack(new VisChain(LinAlg.translate(0, im.getHeight(), 0),
                                                        LinAlg.scale(1, -1, 1),
                                                        LinAlg.translate(d.cxy[0], d.cxy[1], 0),
                                                        new VzText(VzText.ANCHOR.CENTER,
                                                                   String.format("<<center,blue>>id %3d\n(err=%d)\n", d.id, d.hammingDistance))));
//                                                            new VisData(new VisDataLineStyle(Color.blue, 4), p0, p1, p2, p3, p0),
//                                                            new VisData(new VisDataLineStyle(Color.green, 4), p0, p1), // x axis
//                                                            new VisData(new VisDataLineStyle(Color.red, 4), p0, p3))); // y axis
                            }

                            vb.swap();
                        }

                        if (first) {
                            first = false;
                            vl.cameraManager.fit2D(new double[] {0,0}, new double[] { im.getWidth(), im.getHeight() }, true);
                        }

                        // every frame adds 6 unknowns, so we need at
                        // least 6 tags for it to be worth the
                        // trouble.
                        int minTags = 8;
                        ArrayList<double[]> correspondences = new ArrayList<double[]>();

                        if (detections.size() >= minTags) {

                            // compute a homography using the entire set of tags
                            Homography33b h = new Homography33b();
                            for (TagDetection d : detections) {
                                TagPosition tp = tagPositions.get(d.id);
                                if (tp == null) {
                                    System.out.println("Found tag that doesn't exist in model: "+d.id);
                                    continue;
                                }

                                h.addCorrespondence(tp.cx, tp.cy, d.cxy[0], d.cxy[1]);
                                correspondences.add(new double[] { tp.cx, tp.cy, d.cxy[0], d.cxy[1] });
                            }

                            double fx = ((GIntrinsicsNode) g.nodes.get(0)).state[0];
                            double fy = ((GIntrinsicsNode) g.nodes.get(0)).state[1];
                            double cx = ((GIntrinsicsNode) g.nodes.get(0)).state[2];
                            double cy = ((GIntrinsicsNode) g.nodes.get(0)).state[3];

                            double P[][] = CameraUtil.homographyToPose(-fx, fy, cx, cy, h.getH());

                            VisWorld.Buffer vb = vw2.getBuffer("camera");
                            vb.addBack(new VisChain(LinAlg.inverse(P),
                                                    LinAlg.scale(0.1, 0.1, 0.1),
                                                    LinAlg.rotateY(Math.PI/2),
                                                    new VzCamera(new VzMesh.Style(Color.blue))));
                            vb.swap();

                            lastEdge = new GCalibrateEdge(correspondences, im);
                            lastNode = new GExtrinsicsNode();
                            lastNode.state = LinAlg.matrixToXyzrpy(P);
                            lastNode.init = LinAlg.copy(lastNode.state);
                        }
                    }
                }
            } catch (Exception ex) {
                System.out.println("ex: "+ex);
            }
        }
    }

    public void drawGroundTruth()
    {
        VisWorld.Buffer vb = vw2.getBuffer("truth");
        for (TagPosition tp : tagPositions.values()) {
            BufferedImage im = tf.makeImage(tp.id);
            vb.addBack(new VisChain(LinAlg.translate(tp.cx, tp.cy, 0.001),
                                    LinAlg.scale(1, -1, 1),
                                    LinAlg.translate(-tp.size / 2, -tp.size / 2),
                                    LinAlg.scale(tp.size / im.getWidth(), tp.size / im.getHeight(), 1),
                                    new VzImage(new VisTexture(im, VisTexture.NO_MAG_FILTER))));
        }
        vb.swap();
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("capture")) {
            synchronized(captureThread) {
                if (captureThread.lastEdge == null) {
                    System.out.printf("Could not create a constraint.\n");
                    return;
                }

                int nidx = g.nodes.size();
                g.nodes.add(captureThread.lastNode);
                captureThread.lastEdge.nodes[0] = 0;
                captureThread.lastEdge.nodes[1] = nidx;
                g.edges.add(captureThread.lastEdge);

                System.out.printf("chi2: %15f\n", captureThread.lastEdge.getChi2(g));

                update();

                captureThread.lastEdge = null;
                captureThread.lastNode = null;

                System.out.println("Added frame");
            }
        }

        if (name.equals("run") || name.equals("iterate")) {
            int niters = Integer.MAX_VALUE;

            if (name.equals("iterate"))
                niters = 1;

            if (iterateThread == null) {
                iterateThread = new IterateThread(niters);
                iterateThread.start();
            } else {
                iterateThread.stop = true;
                iterateThread = null;
            }

            if (name.equals("iterate"))
                iterateThread = null;

        }
    }

    class IterateThread extends Thread
    {
        public boolean stop = false;
        int niters;

        IterateThread(int niters)
        {
            this.niters = niters;
        }

        public void run()
        {
//            GraphSolver gs = new GaussSeidelSolver(g);
            GraphSolver gs = new CholeskySolver(g, new MinimumDegreeOrdering());

            for ( ; niters > 0 && !stop ; niters--) {

                gs.iterate();

                for (int i = 0; i < g.nodes.get(0).state.length; i++) {
                    System.out.printf("%16.8f\n", g.nodes.get(0).state[i]);
                }

                update();

                int ncorr = 0;
                for (GEdge ge : g.edges) {
                    if (ge instanceof GCalibrateEdge) {
                        ncorr += ((GCalibrateEdge) ge).correspondences.size();
                    }
                }

                System.out.printf("Graph with %d correspondences chi2: %15.5f (%15.5f per correspondence)\n",
                                  ncorr,
                                  g.getErrorStats().chi2,
                                  g.getErrorStats().chi2 / ncorr);
            }
        }
    }

    void update()
    {
        int yoff = 0;

        VisWorld.Buffer vb = vw3.getBuffer("frame "+yoff);

        for (GEdge ge : g.edges) {
            if (ge instanceof GCalibrateEdge) {
                ((GCalibrateEdge) ge).draw(g, vb, 0, yoff, 1);
                yoff++;
            }
        }

        vb.swap();
    }

    public void updateCaptureDisplay()
    {
        VisWorld.Buffer vb = vw2.getBuffer("captures");

        vb.swap();
    }

    public static void main(String args[])
    {
        new Calibrate(args);
    }


    /////////////////////////////////////////////////////////////////////
    static class GIntrinsicsNode extends GNode
    {
//        public static final double eps[] = new double[] { 0.01, 0.01, 0.01, 0.01, 0.001, 0.001, 0.001 };
        int imwidth, imheight;

        public GIntrinsicsNode(int imwidth, int imheight, double fx, double fy, double cx, double cy)
        {
            this.imwidth = imwidth;
            this.imheight = imheight;
            state = new double[] { fx, fy, cx, cy, 0, 0 };
        }

        private GIntrinsicsNode()
        {
        }

        // state:
        // 0: fx
        // 1: fy
        // 2: cx
        // 3: cy
        // 4: distortion r coefficient
        // 5: distortion r^2 coefficient
        // 6: distortion r^4 coefficient
        public int getDOF()
        {
            return state.length;
        }

        public double[] project(double state[], double p[])
        {
            assert(p.length==4); // 3D homogeneous coordinates in, please.

            double M[][] = new double[][] { { -state[0], 0, state[2], 0 },
                                            { 0, state[1], state[3], 0 },
                                            { 0, 0, 1, 0 } };
            double q[] = LinAlg.matrixAB(M, p);
            q[0] /= q[2];
            q[1] /= q[2];
            q[2] = 1;

            // do lens distortion model
            double dx = q[0] - state[2];
            double dy = q[1] - state[3];

            double r = Math.sqrt(dx*dx + dy*dy) / (imwidth / 2.0);
            double theta = Math.atan2(dy, dx);

            double rp = (imwidth / 2.0)*(r + state[4]*r*r + state[5]*r*r*r*r);

            return new double[] { Math.cos(theta)*rp + state[2], Math.sin(theta)*rp + state[3] };
        }

        public GIntrinsicsNode copy()
        {
            GIntrinsicsNode gn= new GIntrinsicsNode();
            gn.imwidth = imwidth;
            gn.imheight = imheight;
            gn.state = LinAlg.copy(state);
            gn.init = LinAlg.copy(init);
            if (truth != null)
                gn.truth = LinAlg.copy(truth);
            gn.attributes = attributes.copy();
            return gn;
        }
    }

    static class GExtrinsicsNode extends GNode
    {
        // state:
        // 0-2: xyz
        // 3-5: rpy

//        public static final double eps[] = new double[] { 0.001, 0.001, 0.001, 0.001, 0.001, 0.001 };

        public int getDOF()
        {
            assert(state.length==6);
            return state.length; // should be 6
        }

        public static double[] project(double state[], double p[])
        {
            double M[][] = LinAlg.xyzrpyToMatrix(state);

            return LinAlg.matrixAB(M, new double[] { p[0], p[1], 0, 1 });
        }

        public GExtrinsicsNode copy()
        {
            GExtrinsicsNode gn = new GExtrinsicsNode();
            gn.state = LinAlg.copy(state);
            gn.init = LinAlg.copy(init);
            if (truth != null)
                gn.truth = LinAlg.copy(truth);
            gn.attributes = attributes.copy();
            return gn;
        }
    }

    static class GCalibrateEdge extends GEdge
    {
        // each correspondence is: worldx, worldy, imagex, imagey
        ArrayList<double[]> correspondences;

        BufferedImage im;

        public GCalibrateEdge(ArrayList<double[]> correspondences, BufferedImage im)
        {
            this.nodes = new int[] { -1, -1 }; // make sure someone sets us later.
            this.correspondences = correspondences;
            this.im = im;
        }

        public int getDOF()
        {
            return correspondences.size();
        }

        public void draw(Graph g, VisWorld.Buffer vb, double xoff, double yoff, double xsize)
        {
            ArrayList<double[]> projected = new ArrayList<double[]>();
            VisChain errs = new VisChain();

            for (double corr[] : correspondences) {
                double pp[] = project(g, new double[] { corr[0], corr[1] });
                projected.add(new double[] { pp[0], pp[1] });
                ArrayList<double[]> line = new ArrayList<double[]>();
                line.add(new double[] { corr[2], corr[3] });
                line.add(new double[] { pp[0], pp[1] });
                errs.add(new VzLines(new VisVertexData(line), VzLines.LINE_STRIP, new VzLines.Style(Color.orange, 2)));
            }

            vb.addBack(new VisChain(LinAlg.translate(xoff, yoff, 0),
                                    LinAlg.scale(xsize / im.getWidth(), xsize / im.getWidth(), 1),
                                    LinAlg.translate(0, im.getHeight(), 0),
                                    LinAlg.scale(1, -1, 1),
                                    new VzImage(im),
                                    errs,
                                    new VzPoints(new VisVertexData(projected), new VzPoints.Style(Color.cyan, 4))));
        }

        double[] project(Graph g, double worldxy[])
        {
            GIntrinsicsNode gin = (GIntrinsicsNode) g.nodes.get(nodes[0]);
            GExtrinsicsNode gex = (GExtrinsicsNode) g.nodes.get(nodes[1]);

            return gin.project(gin.state,
                               gex.project(gex.state, worldxy));
        }

        public double getChi2(Graph g)
        {
            double err2 = 0;
            for (double corr[] : correspondences) {

                err2 += LinAlg.sq(getResidual(g, corr));
            }

            return err2;
        }

        public double getResidual(Graph g, double corr[])
        {
            double p[] = project(g, new double[] { corr[0], corr[1] });
            return LinAlg.distance(p, new double[] { corr[2], corr[3] });
        }

        public Linearization linearize(Graph g, Linearization lin)
        {
            if (lin == null) {
                lin = new Linearization();

                for (int nidx = 0; nidx < nodes.length; nidx++) {
                    lin.J.add(new double[correspondences.size()][g.nodes.get(nodes[nidx]).state.length]);
                }

                lin.R = new double[correspondences.size()];
                lin.W = LinAlg.identity(correspondences.size());

                // chi2 is sum of error of each correspondence, so W
                // should just be 1.
            }

            for (int cidx = 0; cidx < correspondences.size(); cidx++) {
                lin.R[cidx] = getResidual(g, correspondences.get(cidx));

                for (int nidx = 0; nidx < nodes.length; nidx++) {
                    GNode gn = g.nodes.get(nodes[nidx]);

                    double s[] = LinAlg.copy(gn.state);
                    for (int i = 0; i < gn.state.length; i++) {

                        double eps = Math.max(0.001, Math.abs(gn.state[i]) / 1000);

                        gn.state[i] = s[i] + eps;
                        double chiplus = LinAlg.sq(getResidual(g, correspondences.get(cidx)));

                        gn.state[i] = s[i] - eps;
                        double chiminus = LinAlg.sq(getResidual(g, correspondences.get(cidx)));

                        lin.J.get(nidx)[cidx][i] = (chiplus - chiminus) / (2*eps);

                        gn.state[i] = s[i];
                    }
                }
            }

            return lin;
        }

        public GCalibrateEdge copy()
        {
            assert(false);
            return null;
        }

        public void write(StructureWriter outs) throws IOException
        {
            assert(false);
        }

        public void read(StructureReader ins) throws IOException
        {
            assert(false);
        }

    }
}
