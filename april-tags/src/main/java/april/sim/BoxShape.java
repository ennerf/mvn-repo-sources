package april.sim;

import april.jmat.*;
import java.util.*;

public class BoxShape implements Shape
{
    double sxyz[];  // cartesian size
    private double r;       // bounding radius

    ArrayList<double[]> planes;
    ArrayList<double[]> vertices;
    ArrayList<Edge> edges;

    // need to store 1 edge per plane for collision check in 6DOF
    final static int[][] EDGE_PAIRS = {{0, 1},
                                       {0, 2},
                                       {0, 4},
                                       {1, 3},
                                       {1, 5},
                                       {2, 3},
                                       {2, 6},
                                       {3, 7},
                                       {4, 5},
                                       {4, 6},
                                       {5, 7},
                                       {6, 7}};

    public class Edge
    {
        double[] v1;
        double[] v2;

        public Edge(double[] v1, double[] v2)
        {
            this.v1 = v1;
            this.v2 = v2;
        }

        public double[] getVector()
        {
            return LinAlg.subtract(v1, v2);
        }
    }

    public ArrayList<Edge> getEdges()
    {
        return edges;
    }

    public double getBoundingRadius()
    {
        return r;
    }

    public BoxShape(double sx, double sy, double sz)
    {
        this(new double[] { sx, sy, sz } );
    }

    public BoxShape(double sxyz[])
    {
        boolean nonCollidable = (sxyz[2] <= 0);
        this.sxyz = LinAlg.copy(sxyz);
        planes = new ArrayList<double[]>();
        vertices = new ArrayList<double[]>();
        edges = new ArrayList<Edge>();

        // create planes and vertices
        planes.add(new double[] { -1, 0, 0, -sxyz[0]/2 });
        planes.add(new double[] {  1, 0, 0, -sxyz[0]/2 });

        planes.add(new double[] { 0, -1, 0, -sxyz[1]/2 });
        planes.add(new double[] { 0,  1, 0, -sxyz[1]/2 });

        planes.add(new double[] { 0, 0, -1, -sxyz[2]/2 });
        planes.add(new double[] { 0, 0,  1, -sxyz[2]/2 });

        for (int i = -1; i < 2; i+=2)
            for (int j = -1; j < 2; j+=2)
                for (int k = -1; k < 2; k+=2)
                    vertices.add(new double[] { i*sxyz[0]/2, j*sxyz[1]/2, k*sxyz[2]/2 });

        for (int i = 0; i < EDGE_PAIRS.length; i++)
            edges.add(new Edge(vertices.get(EDGE_PAIRS[i][0]),
                               vertices.get(EDGE_PAIRS[i][1])));

        r = Math.sqrt(sxyz[0]*sxyz[0] + sxyz[1]*sxyz[1] + sxyz[2]*sxyz[2]) / 2;  // calculate once
        if (nonCollidable)
            r *= -1;
    }

    protected BoxShape()
    {
    }

    public BoxShape transform(double T[][])
    {
        BoxShape bs = new BoxShape();
        bs.sxyz = LinAlg.copy(sxyz);
        bs.vertices = LinAlg.transform(T, vertices);
        bs.planes = LinAlg.transformPlanes(T, planes);
        bs.r = r;

        bs.edges = new ArrayList<Edge>();
        for (int i = 0; i < EDGE_PAIRS.length; i++)
            bs.edges.add(new Edge(bs.vertices.get(EDGE_PAIRS[i][0]),
                                  bs.vertices.get(EDGE_PAIRS[i][1])));
        return bs;
    }

    // Returns a deep copy of vertices
    public ArrayList<double[]> getVertices()
    {
        ArrayList<double[]> v = new ArrayList<double[]>();
        for (double [] p : vertices)
            v.add(LinAlg.copy(p));
        return v;
    }

    // Returns a deep copy of planes
    public ArrayList<double[]> getPlanes()
    {
        ArrayList<double[]> v = new ArrayList<double[]>();
        for (double [] p : planes)
            v.add(LinAlg.copy(p));
        return v;
    }

}
