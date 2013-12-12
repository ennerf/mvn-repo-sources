package april.camera;

import java.util.*;

import april.jmat.*;

public class CameraMath
{
    public static boolean verbose = false;

    /** Triangulation function for points in *rectified image planes*. Radial distortion
      * must be accounted for before calling triangulate()
      **/
    public static double[] triangulate(double[][] Pleft, double[][] Pright,
                                       double[] pxleft, double[] pxright)
    {
        double[] p1t_left = new double[] { Pleft[0][0], Pleft[0][1], Pleft[0][2], Pleft[0][3] };
        double[] p2t_left = new double[] { Pleft[1][0], Pleft[1][1], Pleft[1][2], Pleft[1][3] };
        double[] p3t_left = new double[] { Pleft[2][0], Pleft[2][1], Pleft[2][2], Pleft[2][3] };

        double[] p1t_right = new double[] { Pright[0][0], Pright[0][1], Pright[0][2], Pright[0][3] };
        double[] p2t_right = new double[] { Pright[1][0], Pright[1][1], Pright[1][2], Pright[1][3] };
        double[] p3t_right = new double[] { Pright[2][0], Pright[2][1], Pright[2][2], Pright[2][3] };

        double A[][] = new double[4][];
        A[0] = LinAlg.subtract(LinAlg.scale( p3t_left,  pxleft[0]),  p1t_left);
        A[1] = LinAlg.subtract(LinAlg.scale( p3t_left,  pxleft[1]),  p2t_left);
        A[2] = LinAlg.subtract(LinAlg.scale(p3t_right, pxright[0]), p1t_right);
        A[3] = LinAlg.subtract(LinAlg.scale(p3t_right, pxright[1]), p2t_right);

        Matrix Am = new Matrix(A);
        SingularValueDecomposition SVD = new SingularValueDecomposition(Am);
        Matrix U = SVD.getU();
        Matrix S = SVD.getS();
        Matrix V = SVD.getV();

        double X[] = V.getColumn(3).getDoubles();
        X = LinAlg.scale(X, 1.0/X[3]);

        return LinAlg.select(X, 0, 2);
    }

    public final static double[] pinholeTransform(double T[][], double p[])
    {
        if (T.length == 3) {
            if (p.length==2) {
                double r[] = new double[] { T[0][0]*p[0] + T[0][1]*p[1] + T[0][2],
                                            T[1][0]*p[0] + T[1][1]*p[1] + T[1][2],
                                            T[2][0]*p[0] + T[2][1]*p[1] + T[2][2] };

                return new double[] { r[0]/r[2], r[1]/r[2] };
            }
            if (p.length==3) {
                double r[] = new double[] { T[0][0]*p[0] + T[0][1]*p[1] + T[0][2]*p[2],
                                            T[1][0]*p[0] + T[1][1]*p[1] + T[1][2]*p[2],
                                            T[2][0]*p[0] + T[2][1]*p[1] + T[2][2]*p[2] };

                return new double[] { r[0]/r[2], r[1]/r[2] };
            }
            assert(false);
        }

        assert(false);
        return null;
    }

    public final static double[] pointTransform(double T[][], double p[])
    {
        if (T.length == 3) {
            if (p.length==3) {
                double r[] = new double[] { T[0][0]*p[0] + T[0][1]*p[1] + T[0][2]*p[2],
                                            T[1][0]*p[0] + T[1][1]*p[1] + T[1][2]*p[2],
                                            T[2][0]*p[0] + T[2][1]*p[1] + T[2][2]*p[2] };

                return r;
            }
            assert(false);
        }

        assert(false);
        return null;
    }

    public final static double[] rayToPlane(double p[])
    {
        if (p.length == 2) {
            return new double[] { p[0], p[1], 1 }; // x and y already normalized, z == 1 plane
        }
        if (p.length == 3) {
            return new double[] { p[0]/p[2], p[1]/p[2], 1 }; // normalize x and y, z == 1 plane
        }

        assert(false);
        return null;
    }

    public final static double[] rayToSphere(double p[])
    {
        if (p.length == 2) {
            return LinAlg.normalize(new double[] { p[0], p[1], 1 }); // assume point was on z == 1 plane
        }
        if (p.length == 3) {
            return LinAlg.normalize(p); // unit magnitude sphere
        }

        assert(false);
        return null;
    }

    public final static double[][] makeCameraMatrix(double K[][], double L2C[][])
    {
        return LinAlg.matrixAB(K, LinAlg.select(L2C, 0, 2, 0, 3));
    }

    /** Estimate the camera intrinsics matrix K with the focal center provided.
      * Needs at least one vanishing point pair, as there's only one unknown with
      * cx and cy given
      *
      * @param vanishingPointPairs - A list of vanishing point pairs {{u0, v0},
      * {u1, v1}, {u2, v2}, ...} specified as two vanishing points per plane in
      * the image (e.g. an AprilTag or tag calibration mosaic). Null
      * vanishing points are allowed for convenience and will be skipped.
      */
    public final static double[][] estimateIntrinsicsFromVanishingPointsWithGivenCxCy(List<double[][]> vanishingPointPairs,
                                                                                      double cx, double cy)
    {
        int numVanishingPoints = 0;
        for (double vp[][] : vanishingPointPairs)
            if (vp != null)
                numVanishingPoints++;

        if (numVanishingPoints < 1)
            return null;

        // the dimension of A matters because we'll be using the economical SVD
        double A[][] = new double[Math.max(3, numVanishingPoints+1)][];

        int i=0;
        for (int n=0; n < vanishingPointPairs.size(); n++) {
            double vp[][] = vanishingPointPairs.get(n);
            if (vp == null)
                continue;

            double u[] = new double[] { vp[0][0] - cx, vp[0][1] - cy, 1 };
            double v[] = new double[] { vp[1][0] - cx, vp[1][1] - cy, 1 };

            // normalization is essential for good estimates
            u = LinAlg.normalize(u);
            v = LinAlg.normalize(v);

            double u1 = u[0];
            double u2 = u[1];
            double u3 = u[2];

            double v1 = v[0];
            double v2 = v[1];
            double v3 = v[2];

            double row[] = new double[] { u1*v1,
                                          u2*v2,
                                          u3*v3 };

            A[i++] = row;
        }


        // similar focal lengths
        A[i++] = new double[] { 1, -1, 0 };

        // if we only have one vanishing point, we need a row of zeros to get the
        // correct result from the economical SVD
        if (numVanishingPoints == 1)
            A[i++] = new double[3];

        if (verbose) {
            System.out.println("A"); LinAlg.print(A);
        }

        SingularValueDecomposition SVD = new SingularValueDecomposition(new Matrix(A));
        Matrix U = SVD.getV();
        Matrix S = SVD.getS();
        Matrix V = SVD.getV();

        if (verbose) {
            System.out.println("U"); U.print();
            System.out.println("S"); S.print();
            System.out.println("V"); V.print();
        }

        double w[] = V.getColumn(V.getColumnDimension()-1).getDoubles();

        double omega[][] = new double[3][3];
        omega[0][0] = w[0];
        omega[0][1] = 0;
        omega[0][2] = 0;
        omega[1][0] = 0;
        omega[1][1] = w[1];
        omega[1][2] = 0;
        omega[2][0] = 0;
        omega[2][1] = 0;
        omega[2][2] = w[2];

        omega = LinAlg.scale(omega, 1.0/omega[2][2]);
        if (verbose) {
            System.out.println("Omega:"); LinAlg.print(omega);
        }

        CholeskyDecomposition cd = new CholeskyDecomposition(new Matrix(omega));
        if (!cd.isSPD())
            cd = new CholeskyDecomposition(new Matrix(LinAlg.scale(omega, -1)));
        // XXX if (!cd.isSPD())
        // XXX     return null;

        double Kinv[][] = cd.getL().transpose().copyArray();

        double K[][] = LinAlg.inverse(Kinv);
        K = LinAlg.scale(K, 1.0 / K[2][2]);

        K[0][2] = cx;
        K[1][2] = cy;
        if (verbose) {
            System.out.println("K:"); LinAlg.print(K);
        }

        return K;
    }

    /** Estimate the camera intrinsics matrix K from a list of vanishing point
      * pairs (two vanishing points per image).
      *
      * @param vanishingPointPairs - A list of vanishing point pairs {{u0, v0},
      * {u1, v1}, {u2, v2}, ...} specified as two vanishing points per plane in
      * the image (e.g. an AprilTag or tag calibration mosaic). At least 3
      * planar observations are required to estimate the intrinsics. Null
      * vanishing points are allowed for convenience and will be skipped.
      */
    public final static double[][] estimateIntrinsicsFromVanishingPoints(List<double[][]> vanishingPointPairs)
    {
        int numVanishingPoints = 0;
        for (double vp[][] : vanishingPointPairs)
            if (vp != null)
                numVanishingPoints++;

        if (numVanishingPoints < 3)
            return null;

        // the dimension of A matters because we'll be using the economical SVD
        double A[][] = new double[Math.max(6, numVanishingPoints+2)][];

        // vanishing points
        int i=0;
        for (int n=0; n < vanishingPointPairs.size(); n++) {
            double vp[][] = vanishingPointPairs.get(n);
            if (vp == null)
                continue;

            double u[] = new double[] { vp[0][0], vp[0][1], 1 };
            double v[] = new double[] { vp[1][0], vp[1][1], 1 };

            // normalization is essential for good estimates
            u = LinAlg.normalize(u);
            v = LinAlg.normalize(v);

            double u1 = u[0];
            double u2 = u[1];
            double u3 = u[2];

            double v1 = v[0];
            double v2 = v[1];
            double v3 = v[2];

            double row[] = new double[] { u1*v1,
                                          u1*v2 + u2*v1,
                                          u2*v2,
                                          u1*v3 + u3*v1,
                                          u2*v3 + u3*v2,
                                          u3*v3 };

            A[i++] = row;
        }

        // zero skew
        A[i++] = new double[] { 0, 1, 0, 0, 0, 0 };

        // similar focal lengths
        A[i++] = new double[] { 1, 0,-1, 0, 0, 0 };

        // if we only have three vanishing points, we need this row of zeros to get
        // the right answer from the economical SVD
        if (numVanishingPoints == 3)
            A[i++] = new double[6];

        if (verbose) {
            System.out.println("A"); LinAlg.print(A);
        }

        SingularValueDecomposition SVD = new SingularValueDecomposition(new Matrix(A));
        Matrix U = SVD.getV();
        Matrix S = SVD.getS();
        Matrix V = SVD.getV();

        if (verbose) {
            System.out.println("U"); U.print();
            System.out.println("S"); S.print();
            System.out.println("V"); V.print();
        }

        double w[] = V.getColumn(V.getColumnDimension()-1).getDoubles();

        double omega[][] = new double[3][3];
        omega[0][0] = w[0];
        omega[0][1] = w[1];
        omega[0][2] = w[3];
        omega[1][0] = w[1];
        omega[1][1] = w[2];
        omega[1][2] = w[4];
        omega[2][0] = w[3];
        omega[2][1] = w[4];
        omega[2][2] = w[5];

        omega = LinAlg.scale(omega, 1.0/omega[2][2]);
        if (verbose) {
            System.out.println("Omega:"); LinAlg.print(omega);
        }

        CholeskyDecomposition cd = new CholeskyDecomposition(new Matrix(omega));
        if (!cd.isSPD())
            cd = new CholeskyDecomposition(new Matrix(LinAlg.scale(omega, -1)));
        // XXX if (!cd.isSPD())
        // XXX     return null;

        double Kinv[][] = cd.getL().transpose().copyArray();

        double K[][] = LinAlg.inverse(Kinv);
        K = LinAlg.scale(K, 1.0 / K[2][2]);
        if (verbose) {
            System.out.println("K:"); LinAlg.print(K);
        }

        return K;
    }

    /** Decompose a Homography using the camera intrinsics matrix K. It is
      * important to note that the coordinate system convention assumed here
      * assumes that Z points INTO the scene (e.g. Caltech), so any points
      * observed in the image must have corresponding 3D points after
      * transforming by the resulting transformation matrix with positive z
      * coordinates. Point <b>p</b> is used to enforce this condition -- when
      * decomposing a homography from an AprilTag detection, p={0,0} is
      * sufficient (the center of the tag was observed in the image), but when
      * the origin is <b>not</b> observed in the image, this assumption results
      * in an incorrect estimate of the transformation.<br>
      * <br>
      * Note: It is assumed that the homography was computed between the desired
      * input and output units. If this is not the case, rescale the translation
      * component into the desired units.
      *
      * @param H - the 3x3 homography
      * @param K - the 3x3 camera intrinsics matrix
      * @param p - a point on the scene plane (e.g. tag mosaic) that must be in
      * front of the camera (any point that was observed in the image)
      */
    public final static double[][] decomposeHomography(double H[][], double K[][], double p[])
    {
        // H is typically equal to K*Rt (for a 3x3 matrix Rt). However, H is
        // only ever known up to scale, so we must expect H=S*K*Rt
        // Conveniently, it turns out that H = S*K*Rt = K*S*Rt, which means we
        // can premultiply by inv(K) to recover the scale rotation component
        double SRt[][] = LinAlg.matrixAB(LinAlg.inverse(K), H);

        // compute the scale, as the columns of R in Rt=[R_3x2 | t_3x1] should
        // have unit magnitude
        double scale0 = Math.sqrt(SRt[0][0]*SRt[0][0] + SRt[1][0]*SRt[1][0] + SRt[2][0]*SRt[2][0]);
        double scale1 = Math.sqrt(SRt[0][1]*SRt[0][1] + SRt[1][1]*SRt[1][1] + SRt[2][1]*SRt[2][1]);
        double scale  = Math.sqrt(scale0*scale1);

        // Remove the scale factor.
        double Rt[][] = LinAlg.scale(SRt, 1.0/scale);

        double T[][] = new double[][] { { Rt[0][0], Rt[0][1],        0, Rt[0][2] },
                                        { Rt[1][0], Rt[1][1],        0, Rt[1][2] },
                                        { Rt[2][0], Rt[2][1],        0, Rt[2][2] },
                                        {        0,        0,        0,        1 } };

        double pp[] = LinAlg.transform(T, p);

        // pp must be in front of the camera (positive z)
        if (pp[2] < 0)
            T = LinAlg.scale(T, -1);

        // ensure the bottom row is what we expect (rescaling can flip the sign)
        T[3] = new double[] { 0, 0, 0, 1 };

        // recover third rotation vector by crossproduct of the other two rotation vectors.
        double a[] = new double[] { T[0][0], T[1][0], T[2][0] };
        double b[] = new double[] { T[0][1], T[1][1], T[2][1] };
        double ab[] = LinAlg.crossProduct(a,b);

        T[0][2] = ab[0];
        T[1][2] = ab[1];
        T[2][2] = ab[2];

        // pull out just the rotation component so we can normalize it.
        double R[][] = LinAlg.select(T, 0, 2, 0, 2);

        SingularValueDecomposition svd = new SingularValueDecomposition(new Matrix(R));
        // polar decomposition, R = (UV')(VSV')
        Matrix MR = svd.getU().times(svd.getV().transpose());
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                T[i][j] = MR.get(i,j);

        return T;
    }

    public final static double[][] estimateHomography(List<double[]> xys, List<double[]> xy_primes)
    {
        assert(xys.size() == xy_primes.size());
        int n = xys.size();

        // compute the transformations to normalize each set of points
        double T[][]        = getPointNormalizationTransform(xys);
        double Tprime[][]   = getPointNormalizationTransform(xy_primes);

        double A[][] = new double[Math.max(2*n, 10)][];
        for (int i=0; i < n; i++) {

            double xy[]         = xys.get(i);
            double xy_prime[]   = xy_primes.get(i);

            // apply the normalization to both points
            xy = pinholeTransform(T, xy);
            xy_prime = pinholeTransform(Tprime, xy_prime);

            double x  = xy[0];
            double y  = xy[1];
            double w  = 1;
            double xp = xy_prime[0];
            double yp = xy_prime[1];
            double wp = 1;

            A[2*i+0] = new double[] {     0,     0,     0, -wp*x, -wp*y, -wp*w,  yp*x,  yp*y,  yp*w };
            A[2*i+1] = new double[] {  wp*x,  wp*y,  wp*w,     0,     0,     0, -xp*x, -xp*y, -xp*w };
        }

        // add a zero entry because of the economical SVD
        if (n == 4) {
            A[8] = new double[9];
            A[9] = new double[9];
        }

        SingularValueDecomposition A_SVD = new SingularValueDecomposition(new Matrix(A));

        int rank = A_SVD.rank();
        if (rank < 8) {
            System.out.printf("Warning: matrix for computing a homography had rank %d (rank of 8 required)\n", rank);
            return null;
        }

        Matrix U = A_SVD.getU();
        Matrix S = A_SVD.getS();
        Matrix V = A_SVD.getV();

        /*
        System.out.println("U:"); U.print();
        System.out.println("S:"); S.print();
        System.out.println("V:"); V.print();
        */

        double hvec[] = V.getColumn(V.getColumnDimension()-1).getDoubles();

        double Ht[][] = new double[][] { { hvec[0], hvec[1], hvec[2] },
                                         { hvec[3], hvec[4], hvec[5] },
                                         { hvec[6], hvec[7], hvec[8] } };

        // result H = inv(T') * Ht * T
        // de-normalize
        double H[][] = LinAlg.matrixAB(LinAlg.inverse(Tprime), LinAlg.matrixAB(Ht, T));
        return H;
    }

    /** Estimate the fundamental matrix from two sets of xy coordinates. Solves the equation:<br>
      * <center><i> transpose(x') * F * x = 0 </i></center><br>
      * using a linear formulation and the SVD. Then enforces the rank 2 constraint on the matrix F.
      *
      * @param xys - The list of xy coordinates corresponding to <i>x</i> in the formula above
      * @param xy_primes - The list of xy coordinates corresponding to <i>x'</i> in the formula above
      */
    public final static double[][] estimateFundamentalMatrix(List<double[]> xys, List<double[]> xy_primes)
    {
        assert(xys.size() == xy_primes.size());
        int n = xys.size();

        // compute the transformations to normalize each set of points
        double T[][]        = getPointNormalizationTransform(xys);
        double Tprime[][]   = getPointNormalizationTransform(xy_primes);

        double A[][] = new double[n][];
        for (int i=0; i < n; i++) {

            double xy[]         = xys.get(i);
            double xy_prime[]   = xy_primes.get(i);

            // apply the normalization to both points
            xy = pinholeTransform(T, xy);
            xy_prime = pinholeTransform(Tprime, xy_prime);

            double x  = xy[0];
            double y  = xy[1];
            double xp = xy_prime[0];
            double yp = xy_prime[1];

            A[i] = new double[] { xp * x  ,
                                  xp * y  ,
                                  xp      ,
                                  yp * x  ,
                                  yp * y  ,
                                  yp      ,
                                  x       ,
                                  y       ,
                                  1       };
        }

        SingularValueDecomposition A_SVD = new SingularValueDecomposition(new Matrix(A));

        int rank = A_SVD.rank();
        if (rank < 8) {
            System.out.printf("Warning: matrix for computing the Fundamental Matrix had rank %d (rank of 8 required)\n", rank);
            return null;
        }

        Matrix A_V    = A_SVD.getV();
        double fvec[] = A_V.getColumn(A_V.getColumnDimension()-1).getDoubles();

        // reshape the vector
        double F[][] = new double[][] { { fvec[0], fvec[1], fvec[2] } ,
                                        { fvec[3], fvec[4], fvec[5] } ,
                                        { fvec[6], fvec[7], fvec[8] } };

        // enforce rank constraint
        SingularValueDecomposition F_SVD = new SingularValueDecomposition(new Matrix(F));
        double U[][] = F_SVD.getU().copyArray();
        double S[][] = F_SVD.getS().copyArray();
        double V[][] = F_SVD.getV().copyArray();

        S[2][2] = 0;

        // recreate F
        F = LinAlg.matrixAB(U, LinAlg.matrixAB(S, LinAlg.transpose(V)));

        // de-normalize
        F = LinAlg.matrixAB(LinAlg.transpose(Tprime), LinAlg.matrixAB(F, T));

        return F;
    }

    /** Compute the transformation which translates a set of points to the origin
      * and scales their distance from the origin such that the RMS error is sqrt(2).
      * Used when computing homographies and the fundamental matrix.
      */
    public final static double[][] getPointNormalizationTransform(List<double[]> points)
    {
        double xyrms[] = getXmeanYmeanRMS(points);

        double scale = Math.sqrt(2) / xyrms[2];

        double T[][] = new double[3][3];
        T[0][0] = scale;
        T[1][1] = scale;
        T[0][2] = -xyrms[0] * scale;
        T[1][2] = -xyrms[1] * scale;
        T[2][2] = 1;

        return T;
    }

    /** Compute the centroid of a set of points and the RMS distance of the points to the centroid.
      * Returns a double[] of the form { xmean, ymean, rms distance }
      */
    public final static double[] getXmeanYmeanRMS(List<double[]> points)
    {
        int n = points.size();
        double xsum = 0, ysum = 0;

        for (double xy[] : points) {
            xsum += xy[0];
            ysum += xy[1];
        }

        double xmean = xsum / n;
        double ymean = ysum / n;

        double acc = 0;
        for (double xy[] : points) {

            double dx = xy[0] - xmean;
            double dy = xy[1] - ymean;
            double dist = dx*dx + dy*dy;

            acc += dist;
        }

        double dist_rms = Math.sqrt((1.0 / n) * acc);

        return new double[] { xmean, ymean, dist_rms };
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Projection methods for 3D points

    // Intrinsics-based projection

    public final static ArrayList<double[]> project(double K[][], double L2C[][], ArrayList<double[]> xyzs)
    {
        ArrayList<double[]> res = new ArrayList<double[]>();
        for (double xyz[] : xyzs)
            res.add(project(K, L2C, xyz));
        return res;
    }

    public final static double[] project(double K[][], double L2C[][], double xyz[])
    {
        double xyz_camera[] = xyz;
        if (L2C != null)
            xyz_camera = LinAlg.transform(L2C, xyz);

        return pinholeTransform(K, xyz_camera);
    }

    // View-based projection

    public final static ArrayList<double[]> project(View view, double L2C[][], ArrayList<double[]> xyzs)
    {
        ArrayList<double[]> res = new ArrayList<double[]>();
        for (double xyz[] : xyzs)
            res.add(project(view, L2C, xyz));
        return res;
    }

    public final static double[] project(View view, double L2C[][], double xyz[])
    {
        double xyz_camera[] = xyz;
        if (L2C != null)
            xyz_camera = LinAlg.transform(L2C, xyz);

        return view.rayToPixels(xyz_camera);
    }

    // View-based projection with a distortion function verifier

    public final static double[] project(View view, DistortionFunctionVerifier verifier,
                                         double L2C[][], double xyz[])
    {
        double xyz_camera[] = xyz;
        if (L2C != null)
            xyz_camera = LinAlg.transform(L2C, xyz);

        if (verifier.validRay(xyz_camera))
            return view.rayToPixels(xyz_camera);

        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////

    public final static double[][] makeVisPlottingTransform(View view, double XY0[], double XY1[], boolean flip)
    {
        return makeVisPlottingTransform(view.getWidth(), view.getHeight(), XY0, XY1, flip);
    }

    public final static double[][] makeVisPlottingTransform(int imwidth, int imheight,
                                                            double XY0[], double XY1[], boolean flip)
    {
        double viswidth = XY1[0] - XY0[0];
        double visheight = XY1[1] - XY0[1];

        double T[][] = LinAlg.matrixAB(LinAlg.translate(XY0[0], XY1[1], 0),
                                       LinAlg.scale(viswidth / imwidth,
                                                    visheight / imheight,
                                                    1));

        if (flip)
            return LinAlg.matrixAB(T,
                                   LinAlg.scale(1, -1, 1));
        else
            return T;
    }
}
