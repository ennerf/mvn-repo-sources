package april.camera.calibrator;

import java.io.*;
import java.util.*;

import april.camera.*;
import april.graph.*;
import april.jmat.*;
import april.util.*;

public class GTagEdge extends GEdge
{
    // inherited: nodes
    ArrayList<double[]> pixel_observations = new ArrayList<double[]>();
    ArrayList<double[]> mosaic_coordinates = new ArrayList<double[]>();

    // Set so that normalized cxhi^2 = 1 (as best as we can)
    public Double pixelVariance = null;// will be set by checking the with of the camera cal later

    // On the lens/camera combos we have observed, the typical
    // fit value for this is .000040, so this is an over estimate
    // based on 2012-12-02-tamron28-1px-wizard
    public double pixVarPerPixOfWidth = 0.000070; // = 0.057 / 752

    boolean hasCameraIntrinsics = true;
    boolean hasCameraExtrinsics = true;

    // indices in this.nodes[] for GNode objects
    int CI = 0;
    int CE = 1;
    int ME = 2;

    // Special mode: for fixed camera intrinsics, we can supply an intrinsics
    // node that is *not* in the graph
    GIntrinsicsNode fixedIntrinsics;

    public GTagEdge()
    {
    }

    // For the "privileged" camera (at the origin)
    public GTagEdge(int calNodeIndex, int mosaicExtrinsicsIndex,
                    ArrayList<double[]> xys_px,
                    ArrayList<double[]> xyzs_m)
    {
        this(calNodeIndex, -1, mosaicExtrinsicsIndex, xys_px, xyzs_m);
    }

    public GTagEdge(int calNodeIndex, int calExtrisicsIndex, int mosaicExtrinsicsIndex,
                    ArrayList<double[]> xys_px,
                    ArrayList<double[]> xyzs_m)
    {
        if (calExtrisicsIndex == -1) {
            hasCameraExtrinsics = false;
            CI = 0;
            CE = -1;
            ME = 1;
        }

        if (hasCameraExtrinsics)
            this.nodes = new int[] {calNodeIndex, calExtrisicsIndex, mosaicExtrinsicsIndex};
        else
            this.nodes = new int[] {calNodeIndex, mosaicExtrinsicsIndex};

        assert(xys_px.size() == xyzs_m.size());

        for (double[] xy_px : xys_px)
            this.pixel_observations.add(LinAlg.copy(xy_px));

        for (double[] xyz_m : xyzs_m)
            this.mosaic_coordinates.add(LinAlg.copy(xyz_m));
    }

    // Special constructor for using fixed intrinsics (which won't be optimized)
    // Camera extrinsics are optional as before
    public GTagEdge(GIntrinsicsNode fixedIntrinsics, int calExtrisicsIndex, int mosaicExtrinsicsIndex,
                    ArrayList<double[]> xys_px,
                    ArrayList<double[]> xyzs_m)
    {
        this.fixedIntrinsics = fixedIntrinsics;

        if (calExtrisicsIndex == -1) {
            hasCameraIntrinsics = false;
            hasCameraExtrinsics = false;
            this.nodes = new int[] {mosaicExtrinsicsIndex};
            CI = -1;
            CE = -1;
            ME = 0;
        }
        else {
            hasCameraIntrinsics = false;
            hasCameraExtrinsics = true;
            this.nodes = new int[] {calExtrisicsIndex, mosaicExtrinsicsIndex};
            CI = -1;
            CE = 0;
            ME = 1;
        }

        assert(xys_px.size() == xyzs_m.size());

        for (double[] xy_px : xys_px)
            this.pixel_observations.add(LinAlg.copy(xy_px));

        for (double[] xyz_m : xyzs_m)
            this.mosaic_coordinates.add(LinAlg.copy(xyz_m));

        pixelVariance = pixVarPerPixOfWidth * fixedIntrinsics.getWidth();

    }

    public int getDOF()
    {
        return pixel_observations.size()*2;
    }

    public double[] getResidualExternal(Graph g)
    {
        if (hasCameraIntrinsics)
            assert(g.nodes.get(this.nodes[CI]) instanceof GIntrinsicsNode);
        if (hasCameraExtrinsics)
            assert(g.nodes.get(this.nodes[CE]) instanceof GExtrinsicsNode);
        assert(g.nodes.get(this.nodes[ME]) instanceof GExtrinsicsNode);

        GIntrinsicsNode cameraIntrinsics = null;
        if (hasCameraIntrinsics)
            cameraIntrinsics = (GIntrinsicsNode) g.nodes.get(this.nodes[CI]);
        else
            cameraIntrinsics = fixedIntrinsics;

        GExtrinsicsNode cameraExtrinsics = null;
        if (hasCameraExtrinsics)
            cameraExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[CE]);

        GExtrinsicsNode mosaicExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[ME]);

        return getResidual(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics);
    }

    private double[] getResidual(GIntrinsicsNode cameraIntrinsics,
                                 GExtrinsicsNode cameraExtrinsics,
                                 GExtrinsicsNode mosaicExtrinsics)
    {
        if (hasCameraIntrinsics)
            cameraIntrinsics.updateIntrinsics();

        double[][] cameraToGlobal = LinAlg.identity(4);
        if (hasCameraExtrinsics)
            cameraToGlobal = cameraExtrinsics.getMatrix();

        double[][] mosaicToGlobal = mosaicExtrinsics.getMatrix();

        double[][] mosaicToCamera = LinAlg.matrixAB(LinAlg.inverse(cameraToGlobal),
                                                    mosaicToGlobal);

        double residual[] = new double[this.getDOF()];
        assert(pixel_observations.size() == mosaic_coordinates.size());
        for (int i=0; i < pixel_observations.size(); i++) {

            double[] xy_px = pixel_observations.get(i);
            double[] xyz_m = mosaic_coordinates.get(i);

            double[] xyz_camera = LinAlg.transform(mosaicToCamera,
                                                   xyz_m);
            double[] xy_predicted = cameraIntrinsics.project(xyz_camera);

            residual[2*i+0] = xy_px[0] - xy_predicted[0];
            residual[2*i+1] = xy_px[1] - xy_predicted[1];
        }

        return residual;
    }

    private void computeJacobianNumerically(GIntrinsicsNode  cameraIntrinsics,
                                            GExtrinsicsNode  cameraExtrinsics,
                                            GExtrinsicsNode  mosaicExtrinsics,
                                            GNode            gn,
                                            double[][]       Jn)
    {
        final double s[] = LinAlg.copy(gn.state);
        for (int i=0; i < s.length; i++) {

            double eps = Math.max(1.0e-6, Math.abs(s[i])*1.0e-3);

            gn.state[i] = s[i] + eps;
            double res_plus[] = getResidual(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics);

            gn.state[i] = s[i] - eps;
            double res_minus[] = getResidual(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics);

            for (int j=0; j < this.getDOF(); j++)
                Jn[j][i] = (res_plus[j] - res_minus[j]) / (2*eps);

            gn.state[i] = s[i];
        }
    }

    public Linearization linearize(Graph g, Linearization lin)
    {
        if (hasCameraIntrinsics)
            assert(g.nodes.get(this.nodes[CI]) instanceof GIntrinsicsNode);
        if (hasCameraExtrinsics)
            assert(g.nodes.get(this.nodes[CE]) instanceof GExtrinsicsNode);
        assert(g.nodes.get(this.nodes[ME]) instanceof GExtrinsicsNode);

        GIntrinsicsNode cameraIntrinsics = null;
        if (hasCameraIntrinsics)
            cameraIntrinsics = (GIntrinsicsNode) g.nodes.get(this.nodes[CI]);
        else
            cameraIntrinsics = fixedIntrinsics;

        GExtrinsicsNode cameraExtrinsics = null;
        if (hasCameraExtrinsics)
            cameraExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[CE]);

        GExtrinsicsNode mosaicExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[ME]);

        if (lin == null) {
            lin = new Linearization();
            if (pixelVariance == null && hasCameraIntrinsics)
                pixelVariance = pixVarPerPixOfWidth * cameraIntrinsics.getWidth();

            if (hasCameraIntrinsics)
                lin.J.add(new double[this.getDOF()][g.nodes.get(nodes[CI]).getDOF()]);
            if (hasCameraExtrinsics)
                lin.J.add(new double[this.getDOF()][g.nodes.get(nodes[CE]).getDOF()]);
            lin.J.add(new double[this.getDOF()][g.nodes.get(nodes[ME]).getDOF()]);


            lin.W = LinAlg.scale(LinAlg.identity(this.getDOF()), 1/pixelVariance);
        }

        lin.R = getResidual(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics);

        if (hasCameraIntrinsics) {
            computeJacobianNumerically(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics,
                                       cameraIntrinsics, lin.J.get(CI));
        }

        if (hasCameraExtrinsics) {
            computeJacobianNumerically(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics,
                                       cameraExtrinsics, lin.J.get(CE));
        }

        computeJacobianNumerically(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics,
                                   mosaicExtrinsics, lin.J.get(ME));

        return lin;
    }

    public GTagEdge copy()
    {
        GTagEdge edge = new GTagEdge();
        edge.nodes = LinAlg.copy(this.nodes);
        edge.attributes = Attributes.copy(this.attributes);

        edge.hasCameraIntrinsics = this.hasCameraIntrinsics;
        edge.hasCameraExtrinsics = this.hasCameraExtrinsics;
        edge.CI = this.CI;
        edge.CE = this.CE;
        edge.ME = this.ME;
        edge.fixedIntrinsics = this.fixedIntrinsics;

        // inherited: nodes
        edge.pixel_observations = new ArrayList<double[]>(this.pixel_observations);
        edge.mosaic_coordinates = new ArrayList<double[]>(this.mosaic_coordinates);
        return edge;
    }


    public ArrayList<double[]> getPixelObs()
    {
        return pixel_observations;
    }

    public ArrayList<double[]> getMosaicCoords()
    {
        return mosaic_coordinates;
    }

    public double getChi2(Graph g)
    {
        if (hasCameraIntrinsics)
            assert(g.nodes.get(this.nodes[CI]) instanceof GIntrinsicsNode);
        if (hasCameraExtrinsics)
            assert(g.nodes.get(this.nodes[CE]) instanceof GExtrinsicsNode);
        assert(g.nodes.get(this.nodes[ME]) instanceof GExtrinsicsNode);

        GIntrinsicsNode cameraIntrinsics = null;
        if (hasCameraIntrinsics)
            cameraIntrinsics = (GIntrinsicsNode) g.nodes.get(this.nodes[CI]);
        else
            cameraIntrinsics = fixedIntrinsics;

        if (pixelVariance == null)
            pixelVariance = pixVarPerPixOfWidth * cameraIntrinsics.getWidth();

        GExtrinsicsNode cameraExtrinsics = null;
        if (hasCameraExtrinsics)
            cameraExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[CE]);

        GExtrinsicsNode mosaicExtrinsics = (GExtrinsicsNode) g.nodes.get(this.nodes[ME]);

        double r[] = getResidual(cameraIntrinsics, cameraExtrinsics, mosaicExtrinsics);

        double chi2 = 0;
        for (int i=0; i < r.length; i++)
            chi2 += r[i]*r[i];

        return chi2 / pixelVariance;
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
