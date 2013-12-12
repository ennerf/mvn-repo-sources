package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

public class VzPoints implements VisObject, VisSerializable
{
    VisAbstractVertexData vd;
    Style styles[];

    public static class Style implements VisSerializable, april.vis.Style
    {
        double pointSize;
        VisAbstractColorData cd;

        public Style(Color c, double pointSize)
        {
            this(new VisConstantColor(c), pointSize);
        }

        public Style(VisAbstractColorData cd, double pointSize)
        {
            this.cd = cd;
            this.pointSize = pointSize;
        }

        public Style(ObjectReader ins)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeDouble(pointSize);
            outs.writeObject(cd);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            pointSize = ins.readDouble();
            cd = (VisAbstractColorData) ins.readObject();
        }
    }

    public VzPoints(VisAbstractVertexData vd, Style ... styles)
    {
        this.vd = vd;
        this.styles = styles;
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, VzPoints.Style style)
    {
        vd.bindVertex(gl);
        style.cd.bindColor(gl);

        gl.glNormal3f(0, 0, 1);
        gl.glPointSize((float) style.pointSize);

        gl.glDrawArrays(GL.GL_POINTS, 0, vd.size());

        style.cd.unbindColor(gl);
        vd.unbindVertex(gl);
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            render(vc, layer, rinfo, gl, style);
    }

    public VzPoints(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeObject(vd);

        outs.writeInt(styles.length);
        for (int sidx = 0; sidx < styles.length; sidx++)
            outs.writeObject(styles[sidx]);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        vd = (VisAbstractVertexData) ins.readObject();

        int nstyles = ins.readInt();
        styles = new Style[nstyles];
        for (int sidx = 0; sidx < styles.length; sidx++)
            styles[sidx] = (Style) ins.readObject();

    }
}
