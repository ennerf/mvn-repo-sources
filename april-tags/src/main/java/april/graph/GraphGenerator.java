package april.graph;

import april.jmat.*;
import april.util.*;

import java.util.*;



/** Generates graphs automatically in a "manhatten" world. **/
public class GraphGenerator
{
    GridMap2D poseMap      = new GridMap2D(10);
    GridMap2D landmarkMap  = new GridMap2D(10);
    double    maxWorldSize = 100;  // in meters
    Graph     g;
    Random    r;

    ArrayList<Integer> landmarks = new ArrayList<Integer>();
    ArrayList<Integer> poses = new ArrayList<Integer>();

    //////////////////////////////////////////////////////////////////
    static class GridMapCell
    {
        double   xyt[];
        int      nodeId;

        public GridMapCell(double xyt[], int nodeId)
        {
            this.xyt = xyt;
            this.nodeId = nodeId;
        }
    }

    //////////////////////////////////////////////////////////////////
    static class GridMap2D
    {
        HashMap<Integer, HashMap<Integer, ArrayList<GridMapCell>>> mapmap =
            new HashMap<Integer, HashMap<Integer, ArrayList<GridMapCell>>>();

        double bucketSize;
        int sz;

        public GridMap2D(double bucketSize)
        {
            this.bucketSize = bucketSize;
        }

        public int size()
        {
            return sz;
        }

        public void add(GridMapCell cell)
        {
            int ix = (int) Math.round(cell.xyt[0] / bucketSize);
            int iy = (int) Math.round(cell.xyt[1] / bucketSize);

            HashMap<Integer, ArrayList<GridMapCell>> map = mapmap.get(ix);
            if (map == null) {
                map = new HashMap<Integer, ArrayList<GridMapCell>>();
                mapmap.put(ix, map);
            }

            ArrayList<GridMapCell> cells = map.get(iy);

            if (cells == null) {
                cells = new ArrayList<GridMapCell>();
                map.put(iy, cells);
            }

            cells.add(cell);
            sz++;
        }

        public GridMapCell getRandomNeighbor(double xyt[], double maxRange, Random rand)
        {
            int ix = (int) Math.round(xyt[0] / bucketSize);
            int iy = (int) Math.round(xyt[1] / bucketSize);
            int ir = (int) (maxRange / bucketSize) + 1;

            ArrayList<GridMapCell> neighbors = new ArrayList<GridMapCell>();

            for (int i = ix - ir; i <= ix + ir; i++) {

                HashMap<Integer, ArrayList<GridMapCell>> map = mapmap.get(i);
                if (map == null)
                    continue;

                for (int j = iy - ir; j <= iy + ir; j++) {
                    ArrayList<GridMapCell> cells = map.get(j);
                    if (cells == null)
                        continue;

                    for (GridMapCell thiscell : cells) {
                        double dist = LinAlg.distance(thiscell.xyt, xyt, 2);
                        if (dist <= maxRange)
                            neighbors.add(thiscell);
                    }
                }
            }

            if (neighbors.size()==0) {
                //		System.out.println("Warning: no neighbors found");
                return null;
            }

            return neighbors.get(rand.nextInt(neighbors.size()));
        }
    }

    //////////////////////////////////////////////////////////////////
    public GraphGenerator(double maxWorldSize, long seed)
    {
        this.maxWorldSize = maxWorldSize;
        this.g = new Graph();
        this.r = new Random(seed);
    }

    // add new landmarks, but pretend to observe them from some
    // specific pose.  You must have already created the trajectories
    // for this to work...
    public void addObservedLandmarks(int numLandmarks, double obsRange, MultiGaussian obsNoise)
    {
        for (int i = 0; i < numLandmarks; i++) {

            int a = -1;

            // randomly select a pose from a trajectory.
            a = r.nextInt(g.nodes.size());

            GNode pose = g.nodes.get(a);

            // relative position of landmark WRT pose
            double obsRelativeTruth[] = new double[] { (2*r.nextDouble()-1)*obsRange,
                                                       (2*r.nextDouble()-1)*obsRange,
                                                       0 };

            double landmarkXytTruth[] = LinAlg.add(pose.truth, obsRelativeTruth);

            // simulate an observation to the landmark from the pose
            double obsTruth[] = LinAlg.xytInvMul31(pose.truth, landmarkXytTruth);
            double obsNoisy[] = LinAlg.add(obsTruth, obsNoise.sample(r));

            double landmarkXytNoisy[] = LinAlg.xytMultiply(pose.state, obsNoisy);

            // true position of landmark
            GNode landmark = new GXYTNode();
            landmark.state = landmarkXytNoisy;
            landmark.truth = landmarkXytTruth;
            landmark.init = landmarkXytNoisy;

            g.nodes.add(landmark);
            landmarks.add(g.nodes.size()-1);

            GXYTEdge gc = new GXYTEdge();
            gc.z = obsNoisy;
            gc.nodes = new int[] { a, g.nodes.size() - 1 };
            gc.P = obsNoise.getCovariance().copyArray();
            gc.truth = obsTruth;
            g.edges.add(gc);
        }
    }

    public void addTrajectory(int numPoses,
                              String topology, int nodesPerLeg, double distPerNode,
                              MultiGaussian odomNoise)
    {
        double xyt[] = new double[3];
        double noisyXyt[] = LinAlg.copy(xyt);

        if (true) {
            GXYTNode gn = new GXYTNode();
            gn.state = LinAlg.copy(noisyXyt);
            gn.truth = LinAlg.copy(xyt);
            gn.init = LinAlg.copy(noisyXyt);
            g.nodes.add(gn);
        }
        poses.add(g.nodes.size()-1);

        double dx = 0, dy = 0; // our direction of motion (truth)

        for (int i = 0; i < numPoses; i++) {

            ////////////////////////////////////////////////////////////////
            // Which direction should we go in?
            if (i % nodesPerLeg == 0) {

                while (true) {
                    if (topology == null || topology.length()==0) {

                        switch (r.nextInt(4))
                        {
                            case 0:
                                dx = 0;  dy = 1;  xyt[2] = Math.PI/2;  break;
                            case 1:
                                dx = 0;  dy = -1; xyt[2] = -Math.PI/2; break;
                            case 2:
                                dx = 1;  dy = 0;  xyt[2] = 0;          break;
                            case 3:
                                dx = -1; dy = 0;  xyt[2] = Math.PI;    break;
                        }

                    } else {

                        char c = topology.charAt((i/nodesPerLeg) % topology.length());
                        switch (Character.toLowerCase(c))
                        {
                            case 'n':
                                dx = 0;  dy = 1;  xyt[2] = Math.PI/2;  break;
                            case 's':
                                dx = 0;  dy = -1; xyt[2] = -Math.PI/2; break;
                            case 'e':
                                dx = 1;  dy = 0;  xyt[2] = 0;          break;
                            case 'w':
                                dx = -1; dy = 0;  xyt[2] = Math.PI;    break;
                            default:
                                System.out.println("GraphGenerator error: topology letters must be 'nsew'");
                                return;
                        }
                    }

                    // validate the new direction to make sure we
                    // won't go too far off the map.

                    double endx = xyt[0]+dx*nodesPerLeg,
                        endy = xyt[1]+dy*nodesPerLeg;

                    if (endx >= -maxWorldSize && endx <= maxWorldSize &&
                        endy >= -maxWorldSize && endy <= maxWorldSize)
                        break;

                    // if they specified a topology, ignore the world size limit
                    if (topology.length() > 0)
                        break;
                }
            }

            ////////////////////////////////////////////////////////////////
            // Move in that direction.

            xyt[0] = xyt[0] + distPerNode*dx;
            xyt[1] = xyt[1] + distPerNode*dy;

            int a = g.nodes.size()-1, b = a+1;

            double prevXyt[] = g.nodes.get(a).truth;
            // xyt = prevXyt * T;
            double Ttruth[] = LinAlg.xytMultiply(LinAlg.xytInverse(prevXyt), xyt);
            double Tnoisy[] = LinAlg.add(Ttruth, odomNoise.sample(r));

            noisyXyt = LinAlg.xytMultiply(noisyXyt, Tnoisy);

            {
                GXYTNode gn = new GXYTNode();
                gn.state = LinAlg.copy(noisyXyt);
                gn.truth = LinAlg.copy(xyt);
                gn.init = LinAlg.copy(noisyXyt);
                g.nodes.add(gn);
            }

            poses.add(g.nodes.size()-1);
            poseMap.add(new GridMapCell(LinAlg.copy(xyt), a));

            GXYTEdge ge = new GXYTEdge();
            ge.nodes = new int[] { a, b};
            ge.z = Tnoisy;
            ge.truth = Ttruth;
            ge.P = odomNoise.getCovariance().copyArray();

            g.edges.add(ge);
        }
    }

    public void observePosesRigid(int numObservations, double obsRange, MultiGaussian obsNoise)
    {
        assert(obsNoise.getDimension()==3);

        if (numObservations > 0 && poseMap.size() == 0) {
            System.out.println("NO POSES!");
            return;
        }

        for (int iter = 0; iter < numObservations; iter++) {
            int poseaId = poses.get(r.nextInt(poses.size()));
            GNode poseaNode = g.nodes.get(poseaId);

            GridMapCell gmc = poseMap.getRandomNeighbor(poseaNode.truth, obsRange, r);
            if (gmc == null)
                continue;

            int posebId = gmc.nodeId;

            if (poseaId == posebId || poseaId > posebId)
                continue;

            GNode posebNode = g.nodes.get(posebId);
            double obsTruth[] = LinAlg.xytInvMul31(poseaNode.truth, posebNode.truth);
            double obsNoisy[] = LinAlg.add(obsTruth, obsNoise.sample(r));

            GXYTEdge ge = new GXYTEdge();
            ge.nodes = new int[] { poseaId, posebId };
            ge.z = obsNoisy;
            ge.truth = obsTruth;
            ge.P = obsNoise.getCovariance().copyArray();

            g.edges.add(ge);
        }
    }

    public void observeLandmarksRigid(int numObservations, double obsRange, MultiGaussian obsNoise)
    {
        assert(obsNoise.getDimension()==3);

        if (numObservations > 0 && landmarks.size() == 0) {
            System.out.println("NO LANDMARKS!");
            return;
        }

        for (int iter = 0; iter < numObservations; iter++) {
            int landmarkId = landmarks.get(r.nextInt(landmarks.size()));
            GNode landmarkNode = g.nodes.get(landmarkId);

            GridMapCell gmc = poseMap.getRandomNeighbor(landmarkNode.truth, obsRange, r);
            if (gmc == null)
                continue;

            GNode poseNode = g.nodes.get(gmc.nodeId);
            double obsTruth[] = LinAlg.xytInvMul31(poseNode.truth, landmarkNode.truth);
            double obsNoisy[] = LinAlg.add(obsTruth, obsNoise.sample(r));

            GXYTEdge ge = new GXYTEdge();
            ge.nodes = new int[] { gmc.nodeId, landmarkId };
            ge.z = obsNoisy;
            ge.P = obsNoise.getCovariance().copyArray();
            ge.truth = obsTruth;
            g.edges.add(ge);
        }
    }

/*
    public void observeLandmarksBearing(int numObservations, double obsRange, MultiGaussian obsNoise)
    {
        assert(obsNoise.getDimension()==1);

        if (numObservations > 0 && landmarks.size() == 0) {
            System.out.println("NO LANDMARKS!");
            return;
        }

        for (int iter = 0; iter < numObservations; iter++) {
            int landmarkId = landmarks.get(r.nextInt(landmarks.size()));
            GNode landmarkNode = g.nodes.get(landmarkId);
            assert(landmarkNode.trajectoryId < 0);

            GridMapCell gmc = poseMap.getRandomNeighbor(landmarkNode.truth, obsRange, r);
            if (gmc == null)
                continue;

            GNode poseNode = g.nodes.get(gmc.nodeId);

            double ttruth = Math.atan2(landmarkNode.truth[1] - poseNode.truth[1],
                                       landmarkNode.truth[0] - poseNode.truth[0]) - poseNode.truth[2];
            double tnoisy = ttruth + obsNoise.sample()[0];

            GEdge gc = new BearingConstraint(gmc.nodeId, landmarkId,
                                             new double[] {0,0},
                                             tnoisy,
                                             obsNoise.getCovariance().get(0,0),
                                             ttruth);
            g.constraints.add(gc);
        }
    }
*/

    public Graph getGraph()
    {
        return g;
    }
}
