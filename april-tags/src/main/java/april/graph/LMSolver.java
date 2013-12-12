package april.graph;

import java.util.*;

import april.jmat.*;
import april.jmat.ordering.*;

public class LMSolver extends CholeskySolver
{
    double lambda = 1e-6;
    double min;
    double max;
    double scale;
    boolean iterationFailed = false;
    boolean setDampingFailed = false;

    // Consistency Constructor Solver(Graph g)
    public LMSolver(Graph g)
    {
        this(g, new MinimumDegreeOrdering(), 10e-6, 10e6, 10);
    }

    public LMSolver(Graph g, Ordering ordering, double minDamping, double maxDamping, double scale)
    {
        super(g, ordering);

        this.min = minDamping;
        this.max = maxDamping;
        this.lambda = min;
        this.scale = scale;
    }

    public double getDamping()
    {
        return lambda;
    }

    public void setDamping(double v)
    {
        if (v >= min && v <= max) {
            lambda = v;
            setDampingFailed = false;
        } else {
            setDampingFailed = true;
        }
    }

    public void scaleDamping(double s)
    {
        setDamping(lambda*s);
    }

    @Override
    public void iterate()
    {
        GraphState before = new GraphState(g);
        super.iterate();

        if (before.chi2 < g.getErrorStats().chi2) {
            before.restore();
            scaleDamping(scale);
            iterationFailed = true;
            //System.out.printf("Iteration failed:    %24.12f\n", lambda);
        } else {
            scaleDamping(1.0/scale);
            iterationFailed = false;
            //System.out.printf("Iteration succeeded: %24.12f\n", lambda);
        }
    }

    @Override
    public boolean canIterate()
    {
        if (iterationFailed && setDampingFailed)
            return false;

        return true;
    }

    @Override
    Matrix solveForX(Matrix PAP, Matrix PB)
    {
        Matrix lambdaI = Matrix.identity(PAP.getRowDimension(), PAP.getColumnDimension()).times(lambda);
        PAP.plusEquals(lambdaI);

        CholeskyDecomposition cd = new CholeskyDecomposition(PAP, verbose);
        L = cd.getL();
        return cd.solve(PB);
    }

    static class GraphState
    {
        HashMap<GNode, double[]> saved = new HashMap<GNode, double[]>();
        final double chi2;

        GraphState(Graph g)
        {
            for (GNode n : g.nodes)
                saved.put(n, LinAlg.copy(n.state));

            this.chi2 = g.getErrorStats().chi2;
        }

        void restore()
        {
            for (GNode n : saved.keySet())
                n.state = saved.get(n);
        }
    }
}
