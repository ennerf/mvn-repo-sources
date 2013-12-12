package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

public class VzLines implements VisObject, VisSerializable
{
    VisAbstractVertexData vd;
    int type;
    Style styles[];

    public static final int LINES = 1, LINE_LOOP = 2, LINE_STRIP = 4;

    public static class Style implements VisSerializable, april.vis.Style
    {
        double lineWidth;
        VisAbstractColorData cd;

        public Style(Color c, double lineWidth)
        {
            this(new VisConstantColor(c), lineWidth);
        }

        public Style(VisAbstractColorData cd, double lineWidth)
        {
            this.cd = cd;
            this.lineWidth = lineWidth;
        }

        public Style(ObjectReader ins)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeDouble(lineWidth);
            outs.writeObject(cd);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            lineWidth = ins.readDouble();
            cd = (VisAbstractColorData) ins.readObject();
        }
    }

    public VzLines(VisAbstractVertexData vd, int type, Style ... styles)
    {
        this.vd = vd;
        this.type = type;
        this.styles = styles;
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, Style style)
    {
        vd.bindVertex(gl);
        style.cd.bindColor(gl);

        gl.glLineWidth((float) style.lineWidth);

        switch (type) {
            case LINES:
                gl.glDrawArrays(GL.GL_LINES, 0, vd.size());
                break;
            case LINE_STRIP:
                gl.glDrawArrays(GL.GL_LINE_STRIP, 0, vd.size());
                break;
            case LINE_LOOP:
                gl.glDrawArrays(GL.GL_LINE_LOOP, 0, vd.size());
                break;
            default:
                assert(false);
        }

        style.cd.unbindColor(gl);
        vd.unbindVertex(gl);
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            render(vc, layer, rinfo, gl, style);
    }


    // For serialization
    public VzLines(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeObject(vd);
        outs.writeInt(type);

        outs.writeInt(styles.length);
        for (int sidx = 0; sidx < styles.length; sidx++)
            outs.writeObject(styles[sidx]);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        vd = (VisAbstractVertexData) ins.readObject();
        this.type = ins.readInt();

        int nstyles = ins.readInt();
        styles = new Style[nstyles];
        for (int sidx = 0; sidx < styles.length; sidx++)
            styles[sidx] = (Style) ins.readObject();
    }
}
