package april.graph;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.io.IOException;

import april.graph.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.jmat.ordering.*;
import april.util.*;
import april.vis.*;

public class GraphXYTExample implements ParameterListener
{
    // GUI stuff
    JFrame jf;

    VisWorld.Buffer vbTrue;
    VisWorld.Buffer vbNoisy;
    VisWorld.Buffer vbGraphTrue;
    VisWorld.Buffer vbGraphOptimized;
    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;

    ParameterGUI pg;
    JPanel paramsPanel;

    // Simulation data
    ArrayList<SimPose> poses;
    Random r = new Random(12354);

    Graph g;
    GraphSolver  solver;

    private class SimPose
    {
        // meters and quaternions, all around

        double truePosition[];
        double trueOrientation[];

        double noisyPosition[];
        double noisyOrientation[];

        double trueTranslation[];
        double trueRotation[];

        double noisyTranslation[];
        double noisyRotation[];
    }

    @Override
    public synchronized void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("step")) {
            step(pg.gi("steps"));
            redraw();
        }

        if (name.equals("constraint")) {
            int index_b = g.nodes.size()-1;
            int index_a = index_b;
            while (index_a == index_b)
                index_a = r.nextInt(g.nodes.size());

            GXYTNode na = (GXYTNode) g.nodes.get(index_a);
            GXYTNode nb = (GXYTNode) g.nodes.get(index_b);

            double truthA[][] = LinAlg.xytToMatrix(na.truth);
            double truthB[][] = LinAlg.xytToMatrix(nb.truth);

            double z[] = LinAlg.matrixToXYT(LinAlg.matrixAB(LinAlg.inverse(truthA), truthB));

            // AT = B, where A and B are poses, and T is the constraint measured
            // by this edge.
            // Observation model is thus: T = inv(A)*B
            GXYTEdge e = new GXYTEdge();
            e.z     = new double[] {z[0] + pg.gd("loopnoise")*r.nextGaussian(),
                                    z[1] + pg.gd("loopnoise")*r.nextGaussian(),
                                    z[2] + pg.gd("loopnoise")*r.nextGaussian()};

            e.truth = new double[] {z[0],
                                    z[1],
                                    z[2]};

            double var = pg.gd("loopnoise")*pg.gd("loopnoise");
            e.P     = new double[][] { {var,   0,   0},
                                       {  0, var,   0},
                                       {  0,   0, var} };

            e.nodes = new int[] {index_a, index_b};

            g.edges.add(e);

            redraw();
        }

        if (name.equals("optimize")) {
            for (int i=0; i < pg.gi("optims"); i++)
                solver.iterate();

            redraw();
        }

        if (name.equals("method")) {
            switch (pg.gi("method"))
            {
                case 0:
                    solver = new CholeskySolver(g, new MinimumDegreeOrdering());
                    pg.si("optims", 1);
                    break;
                case 1:
                    solver = new GaussSeidelSolver(g);
                    pg.si("optims", 100);
                    break;
                default:
                    assert (false);
            }
        }

        if (name.equals("save")) {
            try {
                g.write("graph.graph");
            } catch (IOException ex) {
                System.err.println("Exception: "+ex);
            }
        }

        if (name.equals("reset")) {
            reset();
            redraw();
        }
    }

    public void step(int steps)
    {
        for (int i=0; i < steps; i++) {
            SimPose sp = new SimPose();

            double xyz[] = new double[] {1 + 0.1*r.nextGaussian(),
                                         0 + 0.2*r.nextGaussian(),
                                         0};

            double rpy[] = new double[] {0,
                                         0,
                                         0 + 0.5*r.nextGaussian()};

            double xyznoise[] = new double[] {0 + pg.gd("odomnoise")*r.nextGaussian(),
                                              0 + pg.gd("odomnoise")*r.nextGaussian(),
                                              0};

            double rpynoise[] = new double[] {0,
                                              0,
                                              0 + pg.gd("odomnoise")*r.nextGaussian()};

            // incremental RBTs
            sp.trueTranslation = xyz;
            sp.trueRotation = LinAlg.rollPitchYawToQuat(rpy);

            sp.noisyTranslation = LinAlg.add(xyz, xyznoise);
            sp.noisyRotation    = LinAlg.rollPitchYawToQuat(LinAlg.add(rpy, rpynoise));

            double trueTransformation[][] = LinAlg.quatPosToMatrix(sp.trueRotation, sp.trueTranslation);
            double noisyTransformation[][] = LinAlg.quatPosToMatrix(sp.noisyRotation, sp.noisyTranslation);

            // final RBTs
            double trueRobot[][] = null;
            double noisyRobot[][] = null;
            if (poses.size() > 0) {
                // get last true pose
                SimPose lastSP = poses.get(poses.size()-1);
                trueRobot = LinAlg.quatPosToMatrix(lastSP.trueOrientation, lastSP.truePosition);
                noisyRobot = LinAlg.quatPosToMatrix(lastSP.noisyOrientation, lastSP.noisyPosition);
            } else {
                trueRobot = LinAlg.quatPosToMatrix(LinAlg.rollPitchYawToQuat(new double[3]), new double[3]);
                noisyRobot = LinAlg.quatPosToMatrix(LinAlg.rollPitchYawToQuat(new double[3]), new double[3]);
            }

            // apply new transformation
            trueRobot = LinAlg.matrixAB(trueRobot, trueTransformation);
            noisyRobot = LinAlg.matrixAB(noisyRobot, noisyTransformation);

            sp.truePosition = new double[] {trueRobot[0][3], trueRobot[1][3], trueRobot[2][3]};
            sp.trueOrientation = LinAlg.matrixToQuat(trueRobot);

            sp.noisyPosition = new double[] {noisyRobot[0][3], noisyRobot[1][3], noisyRobot[2][3]};
            sp.noisyOrientation = LinAlg.matrixToQuat(noisyRobot);

            ////////////////////////////////////////
            // Poses
            ////////////////////////////////////////

            poses.add(sp);

            ////////////////////////////////////////
            // Graph
            ////////////////////////////////////////

            // the node we link to
            GXYTNode prevNode = (GXYTNode) g.nodes.get(g.nodes.size()-1);
            double prevRBT[][] = LinAlg.xytToMatrix(prevNode.state);
            double newRBT[][] = LinAlg.matrixAB(prevRBT,
                                                noisyTransformation);

            GXYTNode n = new GXYTNode();
            n.state = LinAlg.matrixToXYT(newRBT);
            n.init  = LinAlg.matrixToXYT(newRBT);
            n.truth = new double[] {sp.truePosition[0],
                                    sp.truePosition[1],
                                    LinAlg.quatToRollPitchYaw(sp.trueOrientation)[2]};

            GXYTEdge e = new GXYTEdge();
            e.z     = new double[] {sp.noisyTranslation[0],
                                    sp.noisyTranslation[1],
                                    LinAlg.quatToRollPitchYaw(sp.noisyRotation)[2]};
            e.truth = new double[] {sp.trueTranslation[0],
                                    sp.trueTranslation[1],
                                    LinAlg.quatToRollPitchYaw(sp.trueRotation)[2]};
            double var = pg.gd("odomnoise")*pg.gd("odomnoise");
            e.P     = new double[][] { {var,   0,   0},
                                       {  0, var,   0},
                                       {  0,   0, var} };
            e.nodes = new int[] {g.nodes.size()-1, g.nodes.size()};

            g.nodes.add(n);
            g.edges.add(e);
        }
    }

    public synchronized void redraw()
    {
        double pose[] = new double[3];
        double orientation[] = LinAlg.rollPitchYawToQuat(new double[3]);

        VisChain vcTrue = new VisChain();
        vcTrue.add(LinAlg.quatPosToMatrix(orientation, pose));
        vcTrue.add(new VzRobot(new VzLines.Style(new Color(20, 20, 255), 1),
                               new VzMesh.Style(new Color(20, 20, 100))));

        VisChain vcNoisy = new VisChain();
        vcNoisy.add(LinAlg.quatPosToMatrix(orientation, pose));
        vcNoisy.add(new VzRobot(new VzLines.Style(new Color(20, 255, 20), 1),
                                new VzMesh.Style(new Color(20, 100, 20))));

        ArrayList<double[]> pointsTrue = new ArrayList<double[]>();
        pointsTrue.add(pose);

        ArrayList<double[]> pointsNoisy = new ArrayList<double[]>();
        pointsNoisy.add(pose);

        for (SimPose sp : poses) {
            vcTrue.add(LinAlg.quatPosToMatrix(sp.trueRotation, sp.trueTranslation));
            vcTrue.add(new VzRobot(new VzLines.Style(new Color(20, 20, 255), 1),
                                   new VzMesh.Style(new Color(20, 20, 100))));// blue

            vcNoisy.add(LinAlg.quatPosToMatrix(sp.noisyRotation, sp.noisyTranslation));
            vcNoisy.add(new VzRobot(new VzLines.Style(new Color(20, 255, 20), 1),
                                    new VzMesh.Style(new Color(20, 100, 20))));// green

            pointsTrue.add(sp.truePosition);
            pointsNoisy.add(sp.noisyPosition);
        }

        // true buffer
        vbTrue.addBack(new VzLines(new VisVertexData(pointsTrue),
                                   VzLines.LINE_STRIP,
                                   new VzLines.Style(Color.blue, 1)));
        vbTrue.addBack(vcTrue);
        vbTrue.swap();

        // noisy buffer
        vbNoisy.addBack(new VzLines(new VisVertexData(pointsNoisy),
                                    VzLines.LINE_STRIP,
                                    new VzLines.Style(Color.green, 1)));
        vbNoisy.addBack(vcNoisy);
        vbNoisy.swap();

        // graph buffers
        for (GEdge o : g.edges) {
            if (o instanceof GXYTEdge) {
                GXYTEdge e = (GXYTEdge) o;

                GXYTNode a = (GXYTNode) g.nodes.get(e.nodes[0]);
                GXYTNode b = (GXYTNode) g.nodes.get(e.nodes[1]);

                vbGraphTrue.addBack(new VzLines(new VisVertexData(new double[][] { LinAlg.select(a.truth, 0, 1),
                                                                                   LinAlg.select(b.truth, 0, 1) }),
                                                VzLines.LINE_STRIP,
                                                new VzLines.Style(new Color(170, 100, 10, 164), 1)));
                vbGraphOptimized.addBack(new VzLines(new VisVertexData(new double[][] { LinAlg.select(a.state, 0, 1),
                                                                                        LinAlg.select(b.state, 0, 1) }),
                                                     VzLines.LINE_STRIP,
                                                     new VzLines.Style(new Color(10, 100, 170, 164), 1)));
            }
        }

        for (GNode o : g.nodes) {
            if (o instanceof GXYTNode) {
                GXYTNode n = (GXYTNode) o;

                vbGraphTrue.addBack(new VisChain(LinAlg.xytToMatrix(n.truth),
                                                 new VzRobot(new VzLines.Style(new Color(255, 180, 10), 1),
                                                             new VzMesh.Style(new Color(170, 100, 10)))));
                vbGraphOptimized.addBack(new VisChain(LinAlg.xytToMatrix(n.state),
                                                      new VzRobot(new VzLines.Style(new Color(10, 180, 255), 1),
                                                                  new VzMesh.Style(new Color(10, 100, 170)))));
            }
        }

        vbGraphTrue.swap();
        vbGraphOptimized.swap();
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Boring setup stuff
    ////////////////////////////////////////////////////////////////////////////////

    public GraphXYTExample()
    {
        setupPG();
        setupVis();
        setupGUI();

        reset();

        redraw();
    }

    private void reset()
    {
        poses = new ArrayList<SimPose>();

        g = new Graph();

        GXYTNode n = new GXYTNode();
        n.state = new double[3];
        n.init  = new double[3];
        n.truth = new double[3];

        g.nodes.add(n);

        GXYTPosEdge e = new GXYTPosEdge();
        e.z     = new double[3];
        e.truth = new double[3];
        e.P     = new double[][] { {1e-6,    0,    0},
                                   {   0, 1e-6,    0},
                                   {   0,    0, 1e-6} };
        e.nodes = new int[] {0};

        g.edges.add(e);

        // create solver
        parameterChanged(pg, "method");
    }

    private void setupPG()
    {
        pg = new ParameterGUI();
        pg.addChoice("method", "Method", new String[] { "Sparse Cholesky", "Gauss Seidel" }, 0);
        pg.addIntSlider("steps","Steps per click", 1, 100, 3);
        pg.addIntSlider("optims","Optimization iterations per click", 1, 1000, 1);
        pg.addDoubleSlider("odomnoise","Odometry noise (shared for x,y,t)",     0, 0.5*Math.PI, 0.1);
        pg.addDoubleSlider("loopnoise","Loop closure noise (shared for x,y,t)", 0, 0.5*Math.PI, 0.1);
        pg.addButtons("step","Step",
                      "constraint","Observe noisy XYT to a random pose",
                      "optimize", "Optimize",
                      "save", "Save graph",
                      "reset","Reset");
        pg.addListener(this);
    }

    private void setupVis()
    {
        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);

        vl.cameraManager.getCameraTarget().perspectiveness = 0;
        vc.setBackground(Color.black);

        VisCameraManager cameraManager = vl.cameraManager;
        VisCameraManager.CameraPosition camPos = cameraManager.getCameraTarget();
        camPos.perspectiveness = 0;
        camPos.eye    = new double[] {    0,    0,   20 }; // eye
        camPos.lookat = new double[] {    0,    0,    0 }; // lookat
        camPos.up     = new double[] {    0,    1,    0 }; // up
        cameraManager.goUI(camPos);
        cameraManager.uiLookAt(camPos.eye, camPos.lookat, camPos.up, true);

        ((DefaultCameraManager) vl.cameraManager).interfaceMode = 2.0;

        vc.setTargetFPS(5);

        vbTrue = vw.getBuffer("true (no graph)");
        vbNoisy = vw.getBuffer("noisy (no graph)");
        vbGraphTrue = vw.getBuffer("graph - true");
        vbGraphOptimized = vw.getBuffer("graph - optimized");

        vl.setBufferEnabled("true (no graph)", false);
        vl.setBufferEnabled("noisy (no graph)", false);
    }

    private void setupGUI()
    {
        paramsPanel = new LayerBufferPanel(vc);

        jf = new JFrame("XYT Graph Example");
        jf.setLayout(new BorderLayout());

        JSplitPane jspv = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vc, pg);
        jspv.setDividerLocation(1.0);
        jspv.setResizeWeight(1.0);

        JSplitPane jsph = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, jspv, paramsPanel);
        jsph.setDividerLocation(1.0);
        jsph.setResizeWeight(1.0);

        jf.add(jsph, BorderLayout.CENTER);
        //jf.setSize(1000, 600);
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        device.setFullScreenWindow(jf);

        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }

    public static void main(String args[])
    {
        new GraphXYTExample();
    }
}
