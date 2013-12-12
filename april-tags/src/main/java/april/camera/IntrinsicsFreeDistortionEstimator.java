package april.camera;

import java.io.*;
import java.util.*;

import april.graph.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.jmat.ordering.*;
import april.tag.*;
import april.util.*;

public class IntrinsicsFreeDistortionEstimator
{
    public int MAX_ITERATIONS = 20; // number of iterations for undistortion

    List<List<TagDetection>> allDetections;
    int width, height;

    TagMosaic mosaic;

    // XXX not always intended to be public
    public Graph g;
    public GraphSolver gs;
    public int distortNodeIndex = -1;

    public IntrinsicsFreeDistortionEstimator(List<List<TagDetection>> allDetections,
                                             TagMosaic mosaic, int width, int height)
    {
        this.allDetections = allDetections;
        this.mosaic = mosaic;
        this.width = width;
        this.height = height;

        g = new Graph();

        CholeskySolver.verbose = false;
        LMSolver.verbose = false;
        IntrinsicsEstimator.verbose = false;

        gs = new LMSolver(g, new MinimumDegreeOrdering(), 1e-10, 1e10, 10);

        buildGraph();
        iterateFully();
    }

    public double[] getCC()
    {
        GDistort distort = (GDistort) g.nodes.get(distortNodeIndex);
        return new double[] {distort.state[0], distort.state[1]};
    }

    public double[] distort(double xy_rp[])
    {
        GDistort distort = (GDistort) g.nodes.get(distortNodeIndex);

        return distort.distort(xy_rp);
    }

    public double[] undistort(double xy_dp[])
    {
        GDistort distort = (GDistort) g.nodes.get(distortNodeIndex);

        return distort.undistort(xy_dp);
    }

    public void iterate()
    {
        if (g == null || gs == null || g.edges.size() == 0)
            return;

        gs.iterate();
    }

    public void iterateFully()
    {
        if (g == null || gs == null || g.edges.size() == 0)
            return;

        while (gs.canIterate())
            gs.iterate();
    }

    public double getMSE()
    {
        return g.getErrorStats().chi2;
    }

    public void buildGraph()
    {
        // add "camera" node
        GDistort distort = new GDistort();
        distort.init = new double[] { width/2.0, height/2.0, 0.0, 0.0 }; // cx, cy, k1, k2
        distort.state = LinAlg.copy(distort.init);
        g.nodes.add(distort);
        distortNodeIndex = g.nodes.size() - 1;

        // make edges that enforce collinearity
        for (int image=0; image < allDetections.size(); image++) {

            List<TagDetection> detections = allDetections.get(image);

            List<TagMosaic.GroupedDetections> rowDetections = mosaic.getRowDetections(detections);
            List<TagMosaic.GroupedDetections> colDetections = mosaic.getColumnDetections(detections);

            for (TagMosaic.GroupedDetections group : rowDetections) {
                assert(group.type == TagMosaic.GroupedDetections.ROW_GROUP);

                // two points define a line. we need three or more points to compute the fit of that line
                if (group.detections.size() < 3)
                    continue;

                ArrayList<double[]> pointPositions = new ArrayList<double[]>();
                for (TagDetection d : group.detections)
                    pointPositions.add(LinAlg.copy(d.cxy));

                GCollinearEdge edge = new GCollinearEdge(image,
                                                         distortNodeIndex,
                                                         pointPositions);
                g.edges.add(edge);
            }

            for (TagMosaic.GroupedDetections group : colDetections) {
                assert(group.type == TagMosaic.GroupedDetections.COL_GROUP);

                // two points define a line. we need three or more points to compute the fit of that line
                if (group.detections.size() < 3)
                    continue;

                ArrayList<double[]> pointPositions = new ArrayList<double[]>();
                for (TagDetection d : group.detections)
                    pointPositions.add(LinAlg.copy(d.cxy));

                GCollinearEdge edge = new GCollinearEdge(image,
                                                         distortNodeIndex,
                                                         pointPositions);
                g.edges.add(edge);
            }
        }
    }

    public class GDistort extends GNode
    {
        // init: cx, cy, k1, k2
        // state: cx, cy, k1, k2
        // truth

        public GDistort()
        {
        }

        public int getDOF()
        {
            return state.length;
        }

        public double[] distort(double xy_rp[])
        {
            double cx = this.state[0];
            double cy = this.state[1];
            double k1 = this.state[2];
            double k2 = this.state[3];

            double x_rpc = xy_rp[0] - cx;
            double y_rpc = xy_rp[1] - cy;

            // initial guess
            double x_dpc = x_rpc;
            double y_dpc = y_rpc;

            for (int i=0; i < MAX_ITERATIONS; i++) {
                double r2 = x_dpc*x_dpc + y_dpc*y_dpc;
                double r4 = r2*r2;

                double multiplier = 1 + k1*r2 + k2*r4;

                x_dpc = x_rpc / multiplier;
                y_dpc = y_rpc / multiplier;
            }

            double xy_dp[] = new double[] { cx + x_dpc ,
                                            cy + y_dpc };
            return xy_dp;
        }

        public double[] undistort(double xy_dp[])
        {
            double cx = this.state[0];
            double cy = this.state[1];
            double k1 = this.state[2];
            double k2 = this.state[3];

            double x_dpc = xy_dp[0] - cx;
            double y_dpc = xy_dp[1] - cy;

            double r2 = x_dpc*x_dpc + y_dpc*y_dpc;
            double r4 = r2*r2;

            double multiplier = 1 + k1*r2 + k2*r4;

            double xy_rp[] = new double[] { cx + multiplier*x_dpc ,
                                            cy + multiplier*y_dpc };
            return xy_rp;
        }

        public double[] toXyzRpy(double s[])
        {
            assert(false);
            return null;
        }

        public GNode copy()
        {
            assert(false);
            return null;
        }
    }

    // XXX not intended to be public forever
    public class GCollinearEdge extends GEdge
    {
        // nodes
        public double P[][];
        double W[][]; // inverse(P)

        public int image;
        List<double[]> xy_dps;

        public GCollinearEdge(int image, // which image was this in?
                              int distortNodeIndex,
                              List<double[]> distortedPointPositions)
        {
            this.image = image;
            this.nodes = new int[] { distortNodeIndex };
            this.xy_dps = distortedPointPositions;

            this.P = LinAlg.identity(1);
        }

        private double[][] getW()
        {
            if (W == null)
                W = LinAlg.inverse(P);
            return W;
        }

        public int getDOF()
        {
            return 1;
        }

        public Linearization linearize(Graph g, Linearization lin)
        {
            GDistort distort = (GDistort) g.nodes.get(this.nodes[0]);

            if (lin == null) {
                lin = new Linearization();

                for (int nidx=0; nidx < nodes.length; nidx++)
                    lin.J.add(new double[getDOF()][g.nodes.get(nodes[nidx]).getDOF()]);

                lin.W = getW();
            }

            lin.R = getResidual(distort);

            computeJacobianNumerically(distort, lin.J.get(0));

            //System.out.printf("J: ");
            //double J[][] = lin.J.get(0);
            //for (int i=0; i < J[0].length; i++)
            //    System.out.printf("%24.6f, ", J[0][i]);
            //System.out.println();

            return lin;
        }

        private void computeJacobianNumerically(GDistort distort, double Jn[][])
        {
            final double s[] = LinAlg.copy(distort.state);
            double epses[] = new double[] { 0.001, 0.001, 0.0000000001, 0.0000000000001 };
            for (int i=0; i < s.length; i++) {

                double eps = epses[i];

                distort.state[i] = s[i] + eps;
                double res_plus[] = getResidual(distort);

                distort.state[i] = s[i] - eps;
                double res_minus[] = getResidual(distort);

                //System.out.printf("[%d] res_plus %24.6f res_minus %24.6f\n",
                //                  i, res_plus[0], res_minus[0]);

                Jn[0][i] = (res_plus[0] - res_minus[0]) / (2*eps);

                distort.state[i] = s[i];
            }

            //System.out.printf("J:");LinAlg.print(Jn);
            //double mag = Math.max(Math.abs(Jn[0][2]), 1000000.0);
            //Jn[0][2] = (Jn[0][2] >= 0) ? mag : -mag;
            //System.out.printf(" :: ");LinAlg.print(Jn);
        }

        public double[] getResidual(GDistort distort)
        {
            // undistort all the points with the current model
            ArrayList<double[]> xy_rps = new ArrayList<double[]>();
            for (double xy_dp[] : this.xy_dps)
                xy_rps.add(distort.undistort(xy_dp));

            GLine2D line = GLine2D.lsqFit(xy_rps);

            // compute the fit error
            double totalDistance = 0;
            for (double xy_rp[] : xy_rps)
                totalDistance += Math.abs(line.perpendicularDistanceTo(xy_rp));

            // return distance to the line
            return new double[] { totalDistance };
        }

        public double getChi2(Graph g)
        {
            GNode node = g.nodes.get(this.nodes[0]);
            assert(node instanceof GDistort);
            GDistort distort = (GDistort) node;

            double residual[] = getResidual(distort);

            return residual[0]*residual[0];
        }

        public double[][] getLine(Graph g)
        {
            GDistort distort = (GDistort) g.nodes.get(this.nodes[0]);

            // undistort all the points with the current model
            ArrayList<double[]> xy_rps = new ArrayList<double[]>();
            for (double xy_dp[] : this.xy_dps)
                xy_rps.add(distort.undistort(xy_dp));

            GLine2D line = GLine2D.lsqFit(xy_rps);

            // init
            double xy_rp1[] = xy_rps.get(0);
            double xy_rp2[] = xy_rps.get(0);

            // should we minimize and maximize x or y?
            int index = 0;
            double t = line.getTheta();
            t = MathUtil.mod2pi(t);
            if ((t < -3*Math.PI/4) || (t > -Math.PI/4 && t < Math.PI/4) || (t > 3*Math.PI/4))
                index = 0; // mostly horizontal
            else
                index = 1; // mostly vertical

            // find the min and max term
            for (double xy_rp[] : xy_rps) {

                if (xy_rp[index] < xy_rp1[index])
                    xy_rp1 = xy_rp;

                if (xy_rp[index] > xy_rp2[index])
                    xy_rp2 = xy_rp;
            }

            // return the line segment
            return new double[][] { line.pointOnLineClosestTo(xy_rp1),
                                    line.pointOnLineClosestTo(xy_rp2) };
        }


        public GEdge copy()
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
