package april.graph;

import java.io.*;
import java.util.*;

import april.jmat.*;
import april.util.*;

public class GEdgeMixture extends GEdge
{
    GEdge components[];
    double weights[];

    public GEdgeMixture()
    {
    }

    /** Components should all be of the same edge type. Weights should sum to 1. **/
    public GEdgeMixture(GEdge components[], double weights[])
    {
        this.components = components;
        this.weights = weights;

        double total = 0;
        assert(components.length == weights.length);
        for (int i = 0; i < components.length; i++) {
            total += weights[i];
            assert(components[i].getClass().equals(components[0].getClass()));

            assert(components[i].nodes.length == components[0].nodes.length);
            for (int j = 0; j < components[0].nodes.length; j++) {
                assert(components[i].nodes[j] == components[0].nodes[j]);
            }
        }

        assert(total >= 0.99 && total <= 1.01);

        this.nodes = LinAlg.copy(components[0].nodes);
    }

    public double getChi2(Graph g)
    {
        double chi2 = Double.MAX_VALUE;

        for (int i = 0; i < components.length; i++) {
            chi2 = Math.min(chi2, -2*Math.log(weights[i]) + components[i].getChi2(g));
        }

        return chi2;
    }

    public void write(StructureWriter outs) throws IOException
    {
        outs.writeComment("num components");
        outs.writeInt(components.length);
        for (int i = 0; i < components.length; i++) {
            outs.writeComment("component "+i);
            outs.writeDouble(weights[i]);
            outs.writeString(components[i].getClass().getName());
            outs.blockBegin();
            components[i].write(outs);
            outs.blockEnd();
        }

        Attributes.write(attributes, outs);
    }

    public void read(StructureReader ins) throws IOException
    {
        int ncomponents = ins.readInt();
        components = new GEdge[ncomponents];
        weights = new double[ncomponents];

        for (int i = 0; i < components.length; i++) {
            weights[i] = ins.readDouble();
            String cls = ins.readString();
            components[i] = (GEdge) ReflectUtil.createObject(cls);
            ins.blockBegin();
            components[i].read(ins);
            ins.blockEnd();
        }

        attributes = Attributes.read(ins);

        nodes = LinAlg.copy(components[0].nodes);
    }

    public int getDOF()
    {
        return components[0].getDOF();
    }

    public Linearization linearize(Graph g, Linearization lin)
    {
        double bestChi2 = Double.MAX_VALUE;
        GEdge bestEdge = null;
        int besti = -1;

        for (int i = 0; i < components.length; i++) {
            double thisChi2 = - 2 * Math.log(weights[i]) + components[i].getChi2(g);
            if (thisChi2 < bestChi2) {
                bestChi2 = thisChi2;
                bestEdge = components[i];
                besti = i;
            }
            System.out.printf("%d %15f\n", i, thisChi2);

        }

        System.out.println("besti: "+besti);
        return bestEdge.linearize(g, lin);
    }

    public GEdgeMixture copy()
    {
        GEdgeMixture ge = new GEdgeMixture();

        ge.components = new GEdge[components.length];
        ge.weights = new double[components.length];

        for (int i = 0; i < components.length; i++) {
            ge.components[i] = components[i].copy();
            ge.weights[i] = weights[i];
        }

        return ge;
    }

    public static void main(String args[])
    {
        mixture();
        nomixture();
    }

    public static void mixture()
    {
        Graph g = new Graph();

        int cnt = 5;

        for (int i = 0; i < cnt; i++) {
            GXYTNode gn = new GXYTNode();
            gn.init = new double[] { i, 0, 0 };
            gn.state = LinAlg.copy(gn.init);
            gn.truth = new double[3];
            g.nodes.add(gn);

            if (i > 0) {
                double sig = 0.06;

                // wheel slippage mode
                GXYTEdge ge1 = new GXYTEdge();
                ge1.z = new double[] { 0, 0, 0};
                ge1.P = LinAlg.diag(new double[] { sig, sig, sig });
                ge1.nodes = new int[] { i-1, i };

                // nominal odometry
                GXYTEdge ge2 = new GXYTEdge();
                ge2.z = new double[] { 1, 0, 0};
                ge2.P = LinAlg.diag(new double[] { sig, sig, sig });
                ge2.nodes = new int[] { i-1, i };

                GEdgeMixture ge = new GEdgeMixture(new GEdge[] { ge1, ge2 }, new double[] { 0.05, 0.95 });
                g.edges.add(ge);
            }
        }

        if (true) {
            GXYTEdge ge = new GXYTEdge();
            ge.z = new double[] { cnt, 0, 0 };
            ge.P = LinAlg.diag(new double[] { 0.1, 0.1, 0.1 });
            ge.nodes = new int[] { 0, g.nodes.size() - 1 };
            g.edges.add(ge);
        }

        try {
            g.write("mixture.graph");
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }
    }

    public static void nomixture()
    {
        Graph g = new Graph();

        int cnt = 5;

        double u = 0.95;
        double sig = 0.1075;

        for (int i = 0; i < cnt; i++) {
            GXYTNode gn = new GXYTNode();
            gn.init = new double[] { i, 0, 0 };
            gn.state = LinAlg.copy(gn.init);
            g.nodes.add(gn);

            if (i > 0) {
                GXYTEdge ge = new GXYTEdge();
                ge.z = new double[] { 0.95, 0, 0};
                ge.P = LinAlg.diag(new double[] { sig, sig, sig });
                ge.nodes = new int[] { i-1, i };
                g.edges.add(ge);
            }
        }

        if (true) {
            GXYTEdge ge = new GXYTEdge();
            ge.z = new double[] { 0, 0, 0 };
            ge.P = LinAlg.diag(new double[] { 0.1, 0.1, 0.1 });
            ge.nodes = new int[] { 0, g.nodes.size() - 1 };
            g.edges.add(ge);
        }

        try {
            g.write("nomixture.graph");
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }
    }

}

