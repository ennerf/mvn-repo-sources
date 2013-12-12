package april.graph;

import april.jmat.*;
import april.jmat.ordering.*;

public class GraphUtil
{
    public static boolean verbose = true;
    public static int matrixType = Matrix.SPARSE;


    // The SolveState infrastructure facilitates auxiliary computations
    // such as marginal or conditional covariance given a graph
    // Note: Most of this code is copied straight from CholeskySolver
    static class SolveState
    {
        Graph g;

        Matrix A, B;
        // Matrix PAP, PB;

        CholeskyDecomposition cd;

        int perm[];
        int invPerm[];
    }

    public static Matrix makeSymbolicA(Graph g)
    {
        Matrix A = new Matrix(g.nodes.size(), g.nodes.size(), matrixType);

        for (GEdge ge : g.edges) {
            for (int i = 0; i < ge.nodes.length; i++)
                for (int j = 0; j < ge.nodes.length; j++)
                    A.set(ge.nodes[i], ge.nodes[j], 1);
        }

        return A;
    }


    public static void linearizeGraph(SolveState ss)
    {
        int n = ss.g.getStateLength();
        if (ss.A == null)
            ss.A = new Matrix(n, n, matrixType);
        if (ss.B == null)
            ss.B = new Matrix(n, 1);

        // Computing A directly, rather than computing J'J, is hugely
        // faster. Each edge connects ge.nodes nodes, which will
        // create ge.nodes.length^2 contributions to A.
        for (GEdge ge : ss.g.edges) {

            Linearization lin = ge.linearize(ss.g, null);

            for (int i = 0; i < ge.nodes.length; i++) {

                int aidx = ss.g.getStateIndex(ge.nodes[i]);
                double JatW[][] = LinAlg.matrixAtB(lin.J.get(i), lin.W);

                for (int j = 0; j < ge.nodes.length; j++) {

                    int bidx = ss.g.getStateIndex(ge.nodes[j]);

                    double JatWJb[][] = LinAlg.matrixAB(JatW, lin.J.get(j));

                    ss.A.plusEquals(aidx, bidx, JatWJb);
                }

                double JatWr[] = LinAlg.matrixAB(JatW, lin.R);
                ss.B.plusEqualsColumnVector(aidx, 0, JatWr);
            }
        }
    }

    public static void computeCholesky(SolveState ss)
    {

        int n = ss.g.getStateLength();
        Matrix SA = makeSymbolicA(ss.g);

        int saPerm[] = new MinimumDegreeOrdering().getPermutation(SA);


        int perm[] = new int[n];
        int pos = 0;
        for (int saidx = 0; saidx < saPerm.length; saidx++) {
            int gnidx = saPerm[saidx];
            GNode gn = ss.g.nodes.get(gnidx);
            int gnpos = ss.g.getStateIndex(gnidx);
            for (int i = 0; i < gn.getDOF(); i++)
                perm[pos++] = gnpos + i;
        }
        ss.invPerm = inversePerm(perm);

        Matrix PAP = ss.A.copyPermuteRowsAndColumns(perm);
        Matrix PB = ss.B.copy();
        PB.permuteRows(perm);

        ss.cd = new CholeskyDecomposition(PAP, verbose);
    }

    public static int[] inversePerm(int perm[])
    {
        int inv[] = new int[perm.length];

        for (int i = 0; i < perm.length; i++)
            inv[perm[i]] = i;

        return inv;
    }

    // Computes the full covariance of the graph
    public static Matrix computeCovariance(SolveState ss)
    {
        assert(ss.cd != null);

        // Covariance computation
        int n = ss.g.getStateLength();
        Matrix PAinvP = ss.cd.solve(Matrix.identity(n,n));
        return PAinvP.copyPermuteRowsAndColumns(ss.invPerm);
    }

    // Warning this methods requires n^2 memory, and is potentially
    // much slower than getting the conditional
    public static Matrix getMarginalCovariance(Graph g, int nodeid)
    {
        int nodeDim = g.nodes.get(nodeid).getDOF();
        SolveState ess = new SolveState();
        ess.g = g;
        linearizeGraph(ess);
        computeCholesky(ess);

        // Compute the full covaraince (SLOW, lots of memory), and then select the part we want
        Matrix cov = computeCovariance(ess).copy(0,nodeDim-1,0,nodeDim-1);
        return cov;
    }

    public static Matrix getConditionalCovariance(Graph g, int nodeid)
    {
        int nodeDim = g.nodes.get(nodeid).getDOF();
        SolveState ess = new SolveState();
        ess.g = g;
        linearizeGraph(ess);

        Matrix nodeInf = ess.A.copy(0,nodeDim-1,0,nodeDim-1);
        Matrix cov = nodeInf.inverse();
        return cov;
    }
}