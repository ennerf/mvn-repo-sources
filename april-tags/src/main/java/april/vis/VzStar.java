package april.vis;

import java.awt.*;
import java.io.*;

public class VzStar implements VisObject, VisSerializable
{
    int npoints;
    double ratio;

    Style styles[];

    Implementation impl;

    static Implementation impls[];
    static final double DEFAULT_RATIO = 0.5;

    static class Implementation
    {
        VzMesh mesh;
        VzLines lines;

        public Implementation(int npoints, double ratio)
        {
            VisVertexData vd = new VisVertexData();
            VisVertexData ld = new VisVertexData();

            vd.add(new float[2]);

            for (int i = 0; i <= npoints*2; i++) {
                double r = ((i & 1) == 0) ? 1 : DEFAULT_RATIO;
                double theta = 2*Math.PI * i / (npoints * 2);
                vd.add(new float[] { (float) (r*Math.cos(theta)),
                                     (float) (r*Math.sin(theta)) });
                ld.add(new float[] { (float) (r*Math.cos(theta)),
                                     (float) (r*Math.sin(theta)) });
            }

            mesh = new VzMesh(vd, VzMesh.TRIANGLE_FAN);
            lines = new VzLines(ld, VzLines.LINE_STRIP);
        }
    }

    static {
        impls = new Implementation[15];

        for (int i = 3; i < impls.length; i++)
            impls[i] = new Implementation(i, DEFAULT_RATIO);
    }

    public VzStar()
    {
        this(new VzMesh.Style(Color.yellow));
    }

    public VzStar(Style ... styles)
    {
        this(5, DEFAULT_RATIO, styles);
    }

    static Implementation getImplementation(int npoints, double ratio)
    {
        if (ratio == DEFAULT_RATIO && npoints < impls.length) {
            return impls[npoints];
        } else {
            return new Implementation(npoints, ratio);
        }
    }

    public VzStar(int npoints, double ratio, Style ... styles)
    {
        assert(npoints >= 3);

        this.npoints = npoints;
        this.ratio = ratio;
        this.styles = styles;

        impl = getImplementation(npoints, ratio);
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles) {
            if (style instanceof VzLines.Style)
                impl.lines.render(vc, layer, rinfo, gl, (VzLines.Style) style);
            if (style instanceof VzMesh.Style)
                impl.mesh.render(vc, layer, rinfo, gl, (VzMesh.Style) style);
        }
    }

    public VzStar(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeInt(npoints);
        outs.writeDouble(ratio);

        outs.writeInt(styles.length);
        for (int sidx = 0; sidx < styles.length; sidx++)
            outs.writeObject(styles[sidx]);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        npoints = ins.readInt();
        ratio = ins.readDouble();

        impl = getImplementation(npoints, ratio);

        int nstyles = ins.readInt();
        styles = new Style[nstyles];
        for (int sidx = 0; sidx < styles.length; sidx++)
            styles[sidx] = (Style) ins.readObject();

    }
}
