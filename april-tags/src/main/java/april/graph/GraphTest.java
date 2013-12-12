package april.graph;

import lcm.logging.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import april.vis.*;
import april.jmat.*;
import april.jmat.ordering.*;
import april.jmat.geom.*;
import april.util.*;
import april.graph.*;
import april.lcmtypes.*;

public class GraphTest implements ParameterListener
{
    JFrame       jf;
    VisWorld     vw = new VisWorld();
    VisLayer     vl = new VisLayer(vw);
    VisCanvas    vc = new VisCanvas(vl);
    ParameterGUI pg = new ParameterGUI();
    Graph        g;
    GraphSolver  solver;
    RunThread    runThread;

    public static void main(String args[])
    {
        if (args.length == 0) {
            System.out.println("Usage: GraphTest mygraph.graph");
            System.exit(0);
        }

        try {
            Graph g = new Graph(args[0]);

            if (false) {
                ArrayList<Graph> components = g.getConnectedComponents();
                if (components.size() > 0) {
                    System.out.println("WARNING: Graph is disconnected. Picking the largest connected component.");

                    g = components.get(0);
                    for (Graph tg : components)
                        if (tg.nodes.size() > g.nodes.size())
                            g = tg;
                }
            }

            new GraphTest(g);
        } catch (IOException ex) {
            System.out.println("Ex: " + ex);
        }
    }

    public GraphTest(Graph g)
    {
        this.g = g;

        pg.addButtons("init", "init", "truth", "truth");
        pg.addChoice("method", "Method", new String[] { "Sparse Cholesky", "Gauss Seidel" }, 0);
        pg.addInt("drawevery", "Draw Every N iterations", 10);
        pg.addButtons("iterate", "iterate", "startstop", "start/stop");
        pg.addListener(this);
        parameterChanged(pg, "method");

        jf = new JFrame("GraphTest");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg.getPanel(), BorderLayout.SOUTH);
        jf.setSize(800, 600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        ArrayList<double[]> bounds = SpatialUtil.getBounds(g);
        double xyz0[] = bounds.get(0);
        double xyz1[] = bounds.get(1);
        double cxyz[] = new double[] { (xyz0[0] + xyz1[0]) / 2, (xyz0[1] + xyz1[1]) / 2, (xyz0[2] + xyz1[2]) / 2 };
        double dist = LinAlg.distance(xyz0, xyz1);
//        vc.getViewManager().viewGoal.lookAt(LinAlg.add(cxyz, new double[] { 0, 0, dist }), cxyz, new double[] { 0, 1, 0 });
        update();
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("init")) {
            for (GNode gn : g.nodes)
                if (gn.init != null)
                    gn.state = LinAlg.copy(gn.init);
            // recreate the solver
            parameterChanged(pg, "method");
        }

        if (name.equals("truth")) {
            for (GNode gn : g.nodes)
                if (gn.truth != null && gn.truth != null)
                    gn.state = LinAlg.copy(gn.truth);
        }

        if (name.equals("method")) {
            switch (pg.gi("method"))
            {
                case 0:
                    solver = new CholeskySolver(g, new MinimumDegreeOrdering());
                    break;
                case 1:
                    solver = new GaussSeidelSolver(g);
                    break;
                default:
                    assert (false);
            }
        }

        if (name.equals("startstop")) {

            if (runThread == null) {

                runThread = new RunThread(Integer.MAX_VALUE);
                runThread.start();

            } else {

                runThread.stop = true;

                try {
                    runThread.join();
                    runThread = null;
                } catch (InterruptedException ex) {
                    System.out.println("ex: " + ex);
                }
            }
        }

        if (name.equals("iterate")) {

            if (runThread == null) {

                runThread = new RunThread(1);
                runThread.start();

                try {
                    runThread.join();
                    runThread = null;
                } catch (InterruptedException ex) {
                    System.out.println("ex: " + ex);
                }
            } else {
                System.out.println("Can't iterate: busy");
            }
        }

        update();
    }

    void update()
    {
        VisWorld.Buffer vb = vw.getBuffer("graph");

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

    class RunThread extends Thread
    {
        int     iterations = 0;
        boolean stop       = false;

        public RunThread(int iterations)
        {
            this.iterations = iterations;
        }

        public void run()
        {
            for (int iter = 0; iter < iterations; iter++) {

                if (stop)
                    break;

                solver.iterate();

                if ((iter % pg.gi("drawevery")) == 0 || iter + 1 == iterations)
                    update();
            }
        }
    }
}
