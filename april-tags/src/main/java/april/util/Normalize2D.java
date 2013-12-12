package april.util;

import java.util.*;

/** For a set of 2D points, compute a 3x3 transform that
 * will make the points have mean zero and unit variance.
 **/
public class Normalize2D
{
    double mX, mY, mXX, mYY;
    int N;

    public void add(double x, double y)
    {
        mX += x;
        mXX += x*x;
        mY += y;
        mYY += y*y;
        N++;
    }

    public void add(ArrayList<double[]> points)
    {
        for (double p[]: points)
            add(p[0], p[1]);
    }

    public double[][] getTransform()
    {
        double eX = mX / N;
        double eY = mY / N;
        double stddevX = 1; //Math.sqrt(mXX / N + eX*eX);
        double stddevY = 1; //Math.sqrt(mYY / N + eY*eY);

        double scaleX = 1.0 / stddevX;
        double scaleY = 1.0 / stddevY;

        return new double[][] { { scaleX, 0,      -eX*scaleX },
                                { 0,      scaleY, -eY*scaleY },
                                { 0,      0,      1 } };
    }
}
