package april.camera.test;

import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.util.*;

public class TestFundamentalMatrix
{
    public static void main(String args[])
    {
        new TestFundamentalMatrix();
    }

    public TestFundamentalMatrix()
    {
        double K[][]  = new double[][] { { 1000,    0,  500 },
                                         {    0, 1000,  500 },
                                         {    0,    0,    1 } };

        double Kp[][] = LinAlg.copy(K);

        System.out.println("K:"); LinAlg.print(K);
        System.out.println("Kp:"); LinAlg.print(Kp);

        double Rt[][] = LinAlg.xyzrpyToMatrix(new double[] { -0.2, 0, 0, 0*Math.PI/180.0, 0*Math.PI/180.0, -30*Math.PI/180.0 });

        double R[][]  = LinAlg.select(Rt, 0, 2, 0, 2);

        double t[]    = new double[] { Rt[0][3], Rt[1][3], Rt[2][3] };
        System.out.println("Rt:"); LinAlg.print(Rt);
        System.out.println("R:"); LinAlg.print(R);
        System.out.println("t:"); LinAlg.print(t);

        double e[]  = LinAlg.matrixAB(LinAlg.matrixAB(K, LinAlg.transpose(R)), t);

        double ep[] = LinAlg.matrixAB(Kp, t);

        System.out.println("e:"); LinAlg.print(e);
        System.out.println("ep:"); LinAlg.print(ep);

        double ex[][]  = crossMatrix(e);
        double epx[][] = crossMatrix(ep);

        double F1[][] = LinAlg.multiplyMany(epx,
                                            Kp,
                                            R,
                                            LinAlg.inverse(K));

        double F2[][] = LinAlg.multiplyMany(LinAlg.inverse(LinAlg.transpose(Kp)),
                                            R,
                                            LinAlg.transpose(K),
                                            ex);

        System.out.println("Before normalization");
        System.out.println("F1:"); LinAlg.print(F1);
        System.out.println("F2:"); LinAlg.print(F2);

        System.out.println("After normalization");
        F1 = normalize(enforceRank2(F1));
        System.out.println("F1:"); LinAlg.print(F1);
        F2 = normalize(enforceRank2(F2));
        System.out.println("F2:"); LinAlg.print(F2);

        int npoints = 20;
        ArrayList<double[]> points      = new ArrayList<double[]>();
        ArrayList<double[]> xys         = new ArrayList<double[]>();
        ArrayList<double[]> xy_primes   = new ArrayList<double[]>();

        Random r = new Random(4612341L);

        for (int i=0; i < npoints; i++) {

            double xyz[] = new double[] {-0.2 + 0.4*r.nextDouble(),
                                         -0.2 + 0.4*r.nextDouble(),
                                          0.8 + 0.4*r.nextDouble() };
            //double xyz[] = new double[] { 0, 0, 1 };

            double xy[] = CameraMath.project(K, null, xyz);
            double xyp[] = CameraMath.project(Kp, Rt, xyz);

            double lp[] = new double[] { F1[0][0]*xy[0] + F1[0][1]*xy[1] + F1[0][2],
                                         F1[1][0]*xy[0] + F1[1][1]*xy[1] + F1[1][2],
                                         F1[2][0]*xy[0] + F1[2][1]*xy[1] + F1[2][2] };

            double err = xyp[0]*lp[0] + xyp[1]*lp[1] + lp[2];

            if (Math.abs(err) > 1.0E-8) {
                System.out.println("Distance to the epipolar line (xpT * F1 * x) exceeded 1.0E-8");

                System.out.println("xyz:"); LinAlg.print(xyz);
                System.out.println("xy:"); LinAlg.print(xy);
                System.out.println("xyp:"); LinAlg.print(xyp);

                System.out.println("err: " + err);
                System.exit(-1);
            }

            points.add(xyz);
            xys.add(xy);
            xy_primes.add(xyp);
        }

        double F3[][] = CameraMath.estimateFundamentalMatrix(xys, xy_primes);

        System.out.println("Before normalization");
        System.out.println("F3:"); LinAlg.print(F3);

        System.out.println("After normalization");
        F3 = normalize(enforceRank2(F3));
        System.out.println("F3:"); LinAlg.print(F3);

        for (int i=0; i < npoints; i++) {

            double xyz[] = points.get(i);
            double xy[]  = xys.get(i);
            double xyp[] = xy_primes.get(i);

            double lp[] = new double[] { F3[0][0]*xy[0] + F3[0][1]*xy[1] + F3[0][2],
                                         F3[1][0]*xy[0] + F3[1][1]*xy[1] + F3[1][2],
                                         F3[2][0]*xy[0] + F3[2][1]*xy[1] + F3[2][2] };

            double err = xyp[0]*lp[0] + xyp[1]*lp[1] + lp[2];

            if (Math.abs(err) > 1.0E-8) {
                System.out.println("Distance to the epipolar line (xpT * F3 * x) exceeded 1.0E-8");

                System.out.println("xyz:"); LinAlg.print(xyz);
                System.out.println("xy:"); LinAlg.print(xy);
                System.out.println("xyp:"); LinAlg.print(xyp);

                System.out.println("err: " + err);
                System.exit(-1);
            }
        }

        for (int i=0; i < F1.length; i++) {
            for (int j=0; j < F1[i].length; j++) {
                if (Math.abs(F1[i][j] - F3[i][j]) > 1.0E-6) {
                    System.out.println("F1 and F3 differ (after normalization) by more than 1.0E-6.");
                    System.exit(-1);
                }
            }
        }

        System.out.println("F1 and F3 match (after normalization) up to 1.0E-6.");
    }

    double[][] crossMatrix(double a[])
    {
        assert(a.length == 3);

        double ax[][] = new double[][] { {     0, -a[2],  a[1] },   //   0, -a3,  a2
                                         {  a[2],     0, -a[0] },   //  a3,   0, -a1
                                         { -a[1],  a[0],     0 } }; // -a2,  a1,   0

        return ax;
    }

    double[][] enforceRank2(double F[][])
    {
        assert(F.length == 3 && F[0].length == 3);

        SingularValueDecomposition F_SVD = new SingularValueDecomposition(new Matrix(F));
        double U[][] = F_SVD.getU().copyArray();
        double S[][] = F_SVD.getS().copyArray();
        double V[][] = F_SVD.getV().copyArray();

        S[2][2] = 0;

        // recreate F
        return LinAlg.matrixAB(U, LinAlg.matrixAB(S, LinAlg.transpose(V)));
    }

    double[][] normalize(double F[][])
    {
        double acc = 0;

        for (int i=0; i < F.length; i++)
            for (int j=0; j < F[i].length; j++)
                acc += F[i][j]*F[i][j];

        double mag = Math.sqrt(acc);

        double res[][] = new double[F.length][F[0].length];

        for (int i=0; i < F.length; i++)
            for (int j=0; j < F[i].length; j++)
                res[i][j] = F[i][j] / mag;

        return res;
    }
}
