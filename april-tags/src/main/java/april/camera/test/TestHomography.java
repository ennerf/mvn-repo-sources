package april.camera.test;

import java.util.*;

import april.camera.*;
import april.jmat.*;
import april.util.*;

public class TestHomography
{
    public static void main(String args[])
    {
        new TestHomography();
    }

    public TestHomography()
    {
        double K[][]  = new double[][] { { 1000,    0,  500 },
                                         {    0, 1000,  500 },
                                         {    0,    0,    1 } };
        System.out.println("K:"); LinAlg.print(K);
        double Rt[][] = LinAlg.multiplyMany(new double[][] { {  0, -1,  0,  0 },
                                                             {  0,  0, -1,  0 },
                                                             {  1,  0,  0,  0 },
                                                             {  0,  0,  0,  1 } },
                                            LinAlg.xyzrpyToMatrix(new double[] { 0.4, 0.05, 0.03,
                                                                                 0.25, 0.1, 0.3 }),
                                            LinAlg.rotateY(Math.PI/2),
                                            LinAlg.rotateZ(-Math.PI/2));
        System.out.println("Rt:"); LinAlg.print(Rt);

        ArrayList<double[]> xys         = new ArrayList<double[]>();
        ArrayList<double[]> xy_primes   = new ArrayList<double[]>();

        Random r = new Random(4612341L);

        int npoints = 20;
        for (int i=0; i < npoints; i++) {

            double xyz[] = new double[] {-0.2 + 0.4*r.nextDouble(),
                                         -0.2 + 0.4*r.nextDouble(),
                                          0 };

            double xy[]  = new double[] { xyz[0], xyz[1] };
            double xyp[] = CameraMath.project(K, Rt, xyz);

            xys.add(xy);
            xy_primes.add(xyp);
        }

        double H[][] = CameraMath.estimateHomography(xys, xy_primes);
        System.out.println("H:"); LinAlg.print(H);

        for (int i=0; i < npoints; i++) {
            double xy[] = xys.get(i);
            double xyp[] = xy_primes.get(i);

            double xy_est[] = CameraMath.pinholeTransform(H, xy);

            double dist = LinAlg.distance(xy_est, xyp);

            System.out.printf("Projected (%8.3f, %8.3f) to (%8.3f, %8.3f) with H, a distance of %8.3f from (%8.3f, %8.3f)\n",
                              xy[0], xy[1], xy_est[0], xy_est[1], dist, xyp[0], xyp[1]);
        }

        H = LinAlg.scale(H, 1.0/H[2][2]);
        System.out.println("H (normalized):"); LinAlg.print(H);

        double Hhat[][] = LinAlg.matrixAB(K, LinAlg.select(Rt, 0, 2, 0, 3));
        Hhat[0] = new double[] { Hhat[0][0], Hhat[0][1], Hhat[0][3]};
        Hhat[1] = new double[] { Hhat[1][0], Hhat[1][1], Hhat[1][3]};
        Hhat[2] = new double[] { Hhat[2][0], Hhat[2][1], Hhat[2][3]};
        Hhat = LinAlg.scale(Hhat, 1.0/Hhat[2][2]);

        System.out.println("Hhat (normalized):"); LinAlg.print(Hhat);

        for (int i=0; i < H.length; i++) {
            for (int j=0; j < H[i].length; j++) {
                if (Math.abs(H[i][j] - Hhat[i][j]) > 1.0E-6) {
                    System.out.println("H and Hhat differ (after normalization) by more than 1.0E-6.");
                    System.exit(-1);
                }
            }
        }

        System.out.println("H and Hhat match (after normalization) up to 1.0E-6.");

        double T[][] = CameraMath.decomposeHomography(H, K, xys.get(0));
        System.out.println("Homography-decomposition estimate of Rt, T:"); LinAlg.print(T);

        for (int i=0; i < T.length; i++) {
            for (int j=0; j < T[i].length; j++) {
                if (Math.abs(T[i][j] - Rt[i][j]) > 1.0E-6) {
                    System.out.println("Rt and T differ (after normalization) by more than 1.0E-6.");
                    System.exit(-1);
                }
            }
        }

        System.out.println("Rt and T match (after normalization) up to 1.0E-6.");
    }
}
