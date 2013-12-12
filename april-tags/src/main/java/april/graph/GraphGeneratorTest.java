package april.graph;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.imageio.*;
import java.util.*;

import april.jmat.*;
import april.vis.*;
import april.jmat.geom.*;
import april.util.*;

/** Test harness and user interface for GraphGenerator. **/
public class GraphGeneratorTest implements ParameterListener
{
    JFrame      frame = new JFrame(getClass().getName());

    VisWorld    world = new VisWorld();
    VisLayer    layer = new VisLayer(world);
    VisCanvas  canvas = new VisCanvas(layer);

    ParameterGUI   pg = new ParameterGUI();
    Graph           g = null;

    public static void main(String[] args)
    {
        new GraphGeneratorTest().run(args);
    }

    public void run(String args[])
    {
        pg.addIntSlider("seed", "Random seed", 1, 1000, 1);
        pg.addInt("numNodes", "Number of nodes", 1000);
        pg.addInt("numTrajectories", "Number of Trajectories", 1);
        pg.addInt("nodesPerLeg", "Nodes per leg", 10);
        pg.addInt("numLandmarks", "Number of landmarks", 0);
        pg.addIntSlider("maxWorldSize", "Max world size", 10, 1000, 30);
        pg.addString("topology", "Topology", "");
        pg.addInt("obsLandmarksRigid", "Rigid Landmark observations", 0);
        pg.addInt("obsLandmarksBearing", "Bearing Landmark observations", 0);
        pg.addInt("obsPosesRigid", "Rigid Pose observations", 0);

        pg.addDoubleSlider("odomNoise", "Odometry noise", 0.00, 0.05, 0.01);
        pg.addDoubleSlider("obsNoise", "Observation noise", 0.00, 0.05, 0.01);

        pg.addString("filename", "filename", "/tmp/out.graph");

        pg.addButtons("truth", "truth",
                      "reset", "reset",
                      "save", "save");

        pg.addListener(this);

        frame.setLayout(new BorderLayout());
        frame.add(canvas, BorderLayout.CENTER);
        frame.add(pg.getPanel(), BorderLayout.SOUTH);
        frame.setSize(600,400);
        frame.setVisible(true);

        regenerate();
    }

    void regenerate()
    {
        boolean nonoise = false;

        GraphGenerator gg = new GraphGenerator(pg.gi("maxWorldSize"), pg.gi("seed"));

        double EPS=0.0001;

        MultiGaussian odomNoise = new MultiGaussian(Matrix.identity(3,3).times(pg.gd("odomNoise")));
        MultiGaussian landmarkInitNoise = new MultiGaussian(Matrix.diag(new double[] {2,2,1}));
        MultiGaussian landmarkObsRigidNoise = new MultiGaussian(Matrix.identity(3,3).times(pg.gd("obsNoise")));
        MultiGaussian landmarkObsBearingNoise = new MultiGaussian(Matrix.diag(new double[] {0.01}));

        if (nonoise) {
            odomNoise = new MultiGaussian(new Matrix(3,3));
            landmarkInitNoise = new MultiGaussian(new Matrix(3,3));
            landmarkObsRigidNoise = new MultiGaussian(new Matrix(3,3));
            landmarkObsBearingNoise = new MultiGaussian(new Matrix(1,1));
        }

        for (int i = 0; i < pg.gi("numTrajectories"); i++)
            gg.addTrajectory(pg.gi("numNodes"), pg.gs("topology"), pg.gi("nodesPerLeg"), 2.0, odomNoise);

        gg.addObservedLandmarks(pg.gi("numLandmarks"), 10, landmarkInitNoise);

        gg.observeLandmarksRigid(pg.gi("obsLandmarksRigid"), 10, landmarkObsRigidNoise);
//        gg.observeLandmarksBearing(pg.gi("obsLandmarksBearing"), 10, landmarkObsBearingNoise);

        gg.observePosesRigid(pg.gi("obsPosesRigid"), 10, landmarkObsRigidNoise);

        g = gg.getGraph();
        update();
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("reset")) {
            g.reset();
            update();
            return;
        }

        if (name.equals("truth")) {
            g.truth();
            update();
            return;
        }

        if (name.equals("save")) {
            try {
                g.write(pg.gs("filename"));
            } catch (IOException ex) {
                System.out.println("Exception: "+ex);
            }
            return;
        }

        regenerate();
    }

    void update()
    {
        VisWorld.Buffer vb = world.getBuffer("graph");

        if (true) {
            VisVertexData vd = new VisVertexData();

            for (GEdge ge : g.edges) {
                if (ge.nodes.length != 2)
                    continue; // Can't draw other types of edges

                for (int i = 0; i < ge.nodes.length; i++) {
                    GNode gn = g.nodes.get(ge.nodes[i]);
                    vd.add(((SpatialNode) gn).toXyzRpy());
                }
            }

            vb.addBack(new VzLines(vd,
                                   VzLines.LINES,
                                   new VzLines.Style(Color.green, 1)));
        }

        for (GNode gn : g.nodes) {

            VisObject vo = new VzRobot();
            if (gn instanceof GXYNode)
                vo = new VzStar();
            vb.addBack(new VisChain(LinAlg.xyzrpyToMatrix(((SpatialNode) gn).toXyzRpy()), vo));
            ArrayList<double[]> points = (ArrayList<double[]>) gn.getAttribute("points");

            if (points != null)
                vb.addBack(new VisChain(LinAlg.xyzrpyToMatrix(((SpatialNode) gn).toXyzRpy()),
                                        new VzPoints(new VisVertexData(points),
                                                     new VzPoints.Style(Color.gray, 1))));
        }

        Graph.ErrorStats estats = g.getErrorStats();
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                           new VzText(VzText.ANCHOR.TOP_LEFT,
                                                       String.format("<<monospaced-12>>chi^2:   %15f\nchi^2/s: %15f\nMSE(xy): %15f",
                                                                     estats.chi2, estats.chi2normalized, estats.meanSquaredDistanceError))));
        vb.swap();
    }

}
