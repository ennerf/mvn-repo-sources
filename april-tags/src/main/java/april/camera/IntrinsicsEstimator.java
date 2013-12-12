package april.camera;

import java.util.*;

import april.jmat.*;
import april.jmat.geom.*;
import april.tag.*;
import april.util.*;

public class IntrinsicsEstimator
{
    public static boolean verbose = false;

    private double K[][];
    private ArrayList<double[][]> vanishingPoints = new ArrayList<double[][]>();
    private ArrayList<ArrayList<double[][]>> allFitLines = new ArrayList<ArrayList<double[][]>>();
    private int numGoodImages = 0;

    private TagMosaic mosaic;

    /** Estimate the intrinsics by computing vanishing points from the tag
     * detections.  If only one image is provided, the fallback focal center is
     * used to allow estimation of the focal length. Lacking more information,
     * width/2 and height/2 is a good guess for the focal center.
      */
    public IntrinsicsEstimator(List<List<TagDetection>> allDetections, TagMosaic mosaic,
                               double fallbackcx, double fallbackcy)
    {
        this.mosaic = mosaic;

        // compute all of the vanishing points
        for (List<TagDetection> detections : allDetections) {

            if (detections.size() == 1)
                computeSingleTagVanishingPoints(detections.get(0));
            else if (detections.size() > 1)
                computeTagMosaicVanishingPoints(detections);
            else {
                allFitLines.add(null);
                vanishingPoints.add(null);
            }
        }

        if (verbose) {
            for (int i=0; i < vanishingPoints.size(); i++) {
                double vp[][] = vanishingPoints.get(i);
                if (vp == null)
                    System.out.printf("Vanishing point %2d: null\n", i+1);
                else
                    System.out.printf("Vanishing point %2d: (%8.1f, %8.1f) and (%8.1f, %8.1f)\n",
                                      i+1, vp[0][0], vp[0][1], vp[1][0], vp[1][1]);
            }
        }

        if (numGoodImages >= 2) {
            // if we have enough points to estimate cx and cy properly, do so
            K = CameraMath.estimateIntrinsicsFromVanishingPoints(vanishingPoints);

        } else if (numGoodImages >= 1) {
            // estimate the focal length with the fallback cx, cy given
            K = CameraMath.estimateIntrinsicsFromVanishingPointsWithGivenCxCy(vanishingPoints, fallbackcx, fallbackcy);
        }
    }

    public ArrayList<double[][]> getFitLines(int n)
    {
        return allFitLines.get(n);
    }

    public ArrayList<double[][]> getVanishingPoints()
    {
        return vanishingPoints;
    }

    /** Get the number of unique and good images observed.
     */
    public int getNumberOfGoodImages()
    {
        return numGoodImages;
    }

    public double[][] getIntrinsics()
    {
        if (K == null)
            return null;

        return LinAlg.copy(K);
    }

    private void computeSingleTagVanishingPoints(TagDetection d)
    {
        // lines on tag
        GLine2D bottom = new GLine2D(d.p[0], d.p[1]);
        GLine2D right  = new GLine2D(d.p[1], d.p[2]);
        GLine2D top    = new GLine2D(d.p[2], d.p[3]);
        GLine2D left   = new GLine2D(d.p[3], d.p[0]);

        // intersect lines to compute vanishing points
        vanishingPoints.add(new double[][] { bottom.intersectionWith(top) ,
                                             left.intersectionWith(right) });
        numGoodImages++;

        ArrayList<double[][]> fitLines = new ArrayList<double[][]>();
        fitLines.add(getLine(bottom));
        fitLines.add(getLine(top));
        fitLines.add(getLine(left));
        fitLines.add(getLine(right));
        allFitLines.add(fitLines);
    }

    private void computeTagMosaicVanishingPoints(List<TagDetection> detections)
    {
        // group all of the detections
        ArrayList<TagMosaic.GroupedDetections> colDetections = mosaic.getColumnDetections(detections);
        ArrayList<TagMosaic.GroupedDetections> rowDetections = mosaic.getRowDetections(detections);
        ArrayList<TagMosaic.GroupedDetections> posDiagDetections = mosaic.getPositiveDiagonalDetections(detections);
        ArrayList<TagMosaic.GroupedDetections> negDiagDetections = mosaic.getNegativeDiagonalDetections(detections);

        if (verbose)
            System.out.printf("Group sizes: (%3d, %3d, %3d, %3d)\n",
                              colDetections.size(), rowDetections.size(), posDiagDetections.size(), negDiagDetections.size());

        // discard groups with fewer than two detections
        colDetections     = keepUsefulGroups(colDetections);
        rowDetections     = keepUsefulGroups(rowDetections);
        posDiagDetections = keepUsefulGroups(posDiagDetections);
        negDiagDetections = keepUsefulGroups(negDiagDetections);

        if (verbose)
            System.out.printf("Group sizes after filtering: (%3d, %3d, %3d, %3d)\n",
                              colDetections.size(), rowDetections.size(), posDiagDetections.size(), negDiagDetections.size());

        // make all the least-squares fit lines
        ArrayList<double[][]> fitLines = new ArrayList<double[][]>();
        for (TagMosaic.GroupedDetections group : colDetections)
            fitLines.add(getLine(group.fitLine()));
        for (TagMosaic.GroupedDetections group : rowDetections)
            fitLines.add(getLine(group.fitLine()));
        for (TagMosaic.GroupedDetections group : posDiagDetections)
            fitLines.add(getLine(group.fitLine()));
        for (TagMosaic.GroupedDetections group : negDiagDetections)
            fitLines.add(getLine(group.fitLine()));

        if (verbose)
            System.out.printf("%3d fit lines\n", fitLines.size());

        // get vanishing points from columns and rows
        double u1[] = getNullSpaceVanishingPoint(colDetections);
        double v1[] = getNullSpaceVanishingPoint(rowDetections);

        // get vanishing points from positive and negative diagonals
        double u2[] = getNullSpaceVanishingPoint(posDiagDetections);
        double v2[] = getNullSpaceVanishingPoint(negDiagDetections);

        // if neither pair of vanishing points exists, we can't estimate the intrinsics
        if ((u1 == null || v1 == null) && (u2 == null || v2 == null))
        {
            vanishingPoints.add(null);
            allFitLines.add(fitLines);
            return;
        }

        if (u1 != null && v1 != null)
            vanishingPoints.add(new double[][] { u1, v1 });

        if (u2 != null && v2 != null)
            vanishingPoints.add(new double[][] { u2, v2 });

        allFitLines.add(fitLines);
        numGoodImages++;
    }

    private ArrayList<TagMosaic.GroupedDetections> keepUsefulGroups(ArrayList<TagMosaic.GroupedDetections> groups)
    {
        ArrayList<TagMosaic.GroupedDetections> usefulGroups = new ArrayList<TagMosaic.GroupedDetections>();

        for (TagMosaic.GroupedDetections group : groups) {
            // we need at least two detections in a group to fit a line
            if (group.detections.size() >= 2) {
                usefulGroups.add(group);
            }
        }

        return usefulGroups;
    }

    private double[] getNullSpaceVanishingPoint(List<TagMosaic.GroupedDetections> groups)
    {
        if (groups.size() < 2)
            return null;

        int i=0;
        double A[][] = new double[groups.size()][];
        for (TagMosaic.GroupedDetections group : groups) {

            GLine2D line = group.fitLine();

            A[i] = new double[] { -line.getM(), 1, -line.getB() };
            i++;
        }

        SingularValueDecomposition SVD = new SingularValueDecomposition(new Matrix(A));
        Matrix V = SVD.getV();

        double xy[] = V.getColumn(V.getColumnDimension()-1).getDoubles();
        xy = LinAlg.scale(xy, 1.0 / xy[2]);

        return new double[] { xy[0], xy[1] };
    }

    // rendering
    private double[][] getLine(GLine2D line)
    {
        double x0 = -10000;
        double x1 =  20000;
        double y0 = -10000;
        double y1 =  20000;

        double p0[] = null;
        double p1[] = null;

        double dx  = line.getDx();
        double dy  = line.getDy();
        double p[] = line.getPoint();
        double t   = line.getTheta();
        t = MathUtil.mod2pi(t);

        double pi = Math.PI;
        // mostly horizontal
        if ((t < -3*pi/4) || (t > -pi/4 && t < pi/4) || (t > 3*pi/4)) {
            p0 = new double[] { x0, p[1] + (x0 - p[0])*dy/dx };
            p1 = new double[] { x1, p[1] + (x1 - p[0])*dy/dx };
        }
        // mostly vertical
        else {
            p0 = new double[] { p[0] + (y0 - p[1])*dx/dy, y0 };
            p1 = new double[] { p[0] + (y1 - p[1])*dx/dy, y1 };
        }

        return new double[][] { p0, p1 };
    }
}
