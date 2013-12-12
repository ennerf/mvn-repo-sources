package april.sim;

import java.util.*;

import april.jmat.*;

public class CompoundShape implements Shape
{
    ArrayList<Object> ops = new ArrayList<Object>();


    // bounding size variables;
    private double r;
    // must be class variable to allow multiple add() calls
    double scale = 1;
    double translation = 0;

    public CompoundShape(Object ... os)
    {
        add(os);
    }

    public void add(Object ... os)
    {
        int i = 0;

        while (i < os.length) {
            if (os[i] == null) {
                i++;
                continue;
            }

            if (os[i] instanceof double[][])
                add((double[][]) os[i]);
            else {
                ops.add(os[i]);

                // now update max radius and use ops (not os) because
                // last Object from previous add() call could have
                // been a double[][]
                int s = ops.size();
                if (s > 1) {
                    Object o = ops.get(s-2);
                    if (o instanceof double[][]) {
                        double[][]M = (double[][])o;
                        scale *= Math.sqrt(LinAlg.sq(M[0][0]) + LinAlg.sq(M[1][0]) + LinAlg.sq(M[2][0]));
                        assert(scale > 0);

                        // this is an upper bound on radius
                        translation += LinAlg.magnitude(new double[]{M[0][3], M[1][3], M[2][3]});
                    }
                }
                if (os[i] instanceof Shape) {
                    this.r = Math.max(this.r, translation + scale * ((Shape)os[i]).getBoundingRadius());
                }
            }
            i++;
        }
    }

    public double getBoundingRadius()
    {
        return r;
    }

    /** Allow users to manually set a compound shape's radius.  This
        is because the upper bound caclulated above may be too
        pessimistic **/
    public void setBoundingRadius(double r)
    {
        if (r > 0)
            this.r = r;
    }

    private void add(double M[][])
    {
        // if more than one rigid-body transformation in a row,
        // pre-multiply them together.
        if (ops.size() > 0) {
            Object o = ops.get(ops.size()-1);
            if (o instanceof double[][]) {
                ops.set(ops.size()-1, LinAlg.matrixAB((double[][]) o, M));
                return;
            }else
                ops.add(M);
        } else
            ops.add(M);
    }
}
