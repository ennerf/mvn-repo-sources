package april.jmat.geom;

import java.util.*;
import april.jmat.*;

public class AlignPoints3D
{

    // returns T, an n+1 by n+1 homogeneous transform points of dimension n
    // from list a to list b using method described in
    // "Least Squares Estimation of Transformation Parameters Between Two Point Patterns"
    // by Shinji Umeyana
    //
    // Algorithm overiew:
    //   a. Compute centroids of both lists, and center a, b at origin
    //   b. compute M[n][n] = \Sum b_i * a_i^t
    //   c. given M = UDV^t via singular value decomposition, compute rotation
    //      via R = USV^t where S = diag(1,1 .. 1, det(U)*det(V));
    //   d. result computed by compounding differences in centroid and rotation matrix
    //
    //  For 2D points, using AlignPoints2D is about 3 times as fast for small datasets,
    //  but the cost of the SVD in this implementation is negligible for large datasets
    public static double[][] align(List<double[]> a, List<double[]> b)
    {
        assert(a.size() == b.size());
        int n = a.size(); // how many data points
        int m = a.get(0).length; // in what dimension does the data live

        // XXX This implementation is currently broken for points not in 2 or 3D
        //     uncomment, and run evaluation to see how
        assert(m == 2 || m ==3);

        // Compute centroids of each cluster
        double aCent[] = new double[m];
        double bCent[] = new double[m];
        for (int i = 0; i < n; i++) {
            LinAlg.plusEquals(aCent, a.get(i));
            LinAlg.plusEquals(bCent, b.get(i));
        }
        aCent = LinAlg.scale(aCent, 1.0/n);
        bCent = LinAlg.scale(bCent, 1.0/n);

        // Now compute M = \Sig (x'_b) (x'_a)^t
        double M[][] = new double[m][m];
        for (int p = 0; p < n; p++) {
            double xa[] = LinAlg.subtract(a.get(p), aCent);
            double xb[] = LinAlg.subtract(b.get(p), bCent);

            for (int  i =0; i < m; i++)
                for (int j = 0; j < m; j++)
                    M[i][j] += xb[i]*xa[j];
        }
        // Scale by 1/n for numerical precision in next step
        for (int  i = 0; i < m; i++)
            for (int j = 0; j < m; j++)
                M[i][j] /= n;

        // compute SVD of M to get rotation
        SingularValueDecomposition svd = new SingularValueDecomposition(new Matrix(M));
        double U[][] = svd.getU().copyArray();
        double V[][] = svd.getV().copyArray();
        double det = det(U)*det(V); // compute sign, if -1, we need to swap the sign of S

        double S[][] = LinAlg.identity(m);
        S[m-1][m-1] = Math.round(det); // swap sign if necessary

        double R[][] = LinAlg.matrixABCt(U,S,V);

        // Compute T = matrixABC(O2B, Rmm, A2O); = |R t| where t = bCent  + R*(-aCent)
        double t[] = LinAlg.add(bCent, LinAlg.matrixAB(R, LinAlg.scale(aCent,-1)));

        double T[][] = new double[m+1][m+1];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < m; j++)
                T[i][j] = R[i][j];
        for (int  i = 0; i < m; i++)
            T[i][m] = t[i];
        T[m][m] = 1;
        return T;
    }

    // Warning: This is not a terribly efficient way to compute determinants for anything other than 3x3 or 2x2
    public static double det(double A[][])
    {
        assert(A.length == A[0].length);

        if (A.length == 3)
            return LinAlg.det33(A);

        if (A.length == 2)
            return LinAlg.det22(A);

        // Otherwise compute the LU decomposition, multiply the diagonals
        LUDecomposition lu = new LUDecomposition(new Matrix(A));
        double det = 1;

        for (int i = 0; i < A.length; i++)
            det *= lu.getL().get(i,i);

        for (int i = 0; i < A.length; i++)
            det *= lu.getU().get(i,i);

        return det;
    }

    // Testing
    // Note that the result could be left handed!
    public static double[][] randomOrthogonalMatrix(int dim, Random r)
    {
        double M[][] = new double[dim][dim];
        for (int i = 0; i < dim; i ++)
            for (int j = 0; j < dim; j++)
                M[i][j] = r.nextGaussian();

        QRDecomposition qr = new QRDecomposition(new Matrix(M));

        return qr.getQ().copyArray();
    }

    public static double[] randomVector(int dim, double scale, Random r)
    {
        double t[] = new double[dim];
        for (int  i = 0; i < dim; i++)
            t[i] = r.nextDouble() * 2*scale - scale;
        return t;
    }


    // computes R p + t when T = |R t| and len(p) = len(t)
    //                           |0 1|
    public static double[] transform(double T[][], double p[])
    {
        assert(T.length == T[0].length &&
               p.length +1 == T.length);

        double o[] = new double[p.length];
        for (int i = 0; i < p.length; i++)
            for (int j = 0; j < p.length; j++)
                o[i] += T[i][j] * p[j];

        for (int i = 0; i < p.length; i++)
            o[i] += T[i][p.length];

        return o;
    }

    public static double[][] xytToMatrix33(double xyt[])
    {
        double c = Math.cos(xyt[2]), s = Math.sin(xyt[2]);

        return new double[][] {{c, -s, xyt[0]},
                               {s,  c, xyt[1]},
                               {0,  0, 1}};
    }

    public static void main(String args[])
    {
        Random r = new Random(3856);

        int dimensions[] = {2, 2, 3, 3, 4,5,6,7,8,9, 2};

        for (int dimIdx = 0; dimIdx < dimensions.length; dimIdx++) { // allow for burn in before we get to the cases we really care about (2,3 d)
            int dim = dimensions[dimIdx];

            double sumDimErr = 0;
            double maxDimErr = 0;
            double minDimErr = Double.MAX_VALUE;
            int npts = 0;
            double sumTime = 0;
            double maxTime = 0;
            april.util.Tic tic = new april.util.Tic();
            int ntrials = 1000;
            for (int trial  = 0; trial < ntrials; trial++) {
                // generate N random vectors, translate, rotate them and check that we solve correctly

                double R[][] = randomOrthogonalMatrix(dim, r);
                double t[] = randomVector(dim, trial < 10 ? 0.0 : 10.0, r); // do ten trials with no translation
                double rDet = det(R);
                if (rDet < 0) { // make R right handed
                    double S[][] = LinAlg.identity(dim);
                    S[dim-1][dim-1] = -1;
                    R = LinAlg.matrixAB(R,S);
                }

                double T[][] = LinAlg.identity(dim+1);
                for (int i = 0; i < dim; i++)
                    for (int j = 0; j < dim; j++)
                        T[i][j] = R[i][j];
                for (int j = 0; j < dim; j++)
                    T[j][dim] = t[j];


                ArrayList<double[]> start = new ArrayList<double[]>();
                ArrayList<double[]> end = new ArrayList<double[]>();
                for (int i = 0; i < dim + trial/4; i++) { // make 4 set of each size, starting with the minimum
                    double s[] = randomVector(dim, 1.0, r);
                    double e[] = transform(T, s);

                    start.add(s);
                    end.add(e);
                }

                tic.tic();
                double H[][] = null;
                if (dim == 2 && dimIdx == dimensions.length -1)
                    H = xytToMatrix33(AlignPoints2D.align(start,end));
                else
                    H = align(start,end);
                double time = tic.toc();

                double err = 0;
                for (int i = 0; i < start.size(); i++) {
                    double s[] = start.get(i);
                    double e[] = end.get(i);

                    double ee[] = transform(H, s);

                    err += LinAlg.distance(ee,e);
                }

                maxDimErr = Math.max(err/ start.size(), maxDimErr);
                minDimErr = Math.min(err/ start.size(), minDimErr);
                sumDimErr += err;
                npts += start.size();
                maxTime = Math.max(maxTime, time);
                sumTime += time;
            }

            System.out.printf("Finished dim %d:   Err min/max/avg  %.15f / %.15f / %.15f    time(avg)  = %.8f  time(max) = %.8f\n",
                              dim, minDimErr, maxDimErr, sumDimErr/npts, sumTime/ntrials, maxTime);
        }

    }
}