package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

public class VzMesh implements VisObject, VisSerializable
{
    VisAbstractVertexData vd, nd;
    VisAbstractIndexData id;

    Style styles[];
    int type;

    public static final int TRIANGLES = 1, TRIANGLE_STRIP = 2, TRIANGLE_FAN = 3, QUADS = 4, QUAD_STRIP = 5;

    // XXX add material properties
    public static class Style implements VisSerializable, april.vis.Style
    {
        VisAbstractColorData cd;

        public Style(Color c)
        {
            this(new VisConstantColor(c));
        }

        public Style(VisAbstractColorData cd)
        {
            this.cd = cd;
        }

        public Style(ObjectReader ins)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeObject(cd);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            cd = (VisAbstractColorData) ins.readObject();
        }
    }

    public VzMesh(VisAbstractVertexData vd, int type, Style ... styles)
    {
        this(vd, null, type, styles);
    }

    public VzMesh(VisAbstractVertexData vd, VisAbstractVertexData nd, int type, Style ... styles)
    {
        this(vd, nd, null, type, styles);
    }

    public VzMesh(VisAbstractVertexData vd, VisAbstractVertexData nd, VisAbstractIndexData id, int type, Style ... styles)
    {
        this.vd = vd;
        this.nd = nd;
        this.id = id;
        this.type = type;
        this.styles = styles;
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, Style style)
    {
        vd.bindVertex(gl);
        if (nd != null)
            nd.bindNormal(gl);
        if (id != null)
            id.bindIndex(gl);
        style.cd.bindColor(gl);

        switch (type) {
            case TRIANGLES:
                if (id != null)
                    gl.glDrawRangeElements(GL.GL_TRIANGLES, 0, vd.size(), id.size(), 0);
                else
                    gl.glDrawArrays(GL.GL_TRIANGLES, 0, vd.size());
                break;
            case TRIANGLE_STRIP:
                if (id != null)
                    gl.glDrawRangeElements(GL.GL_TRIANGLE_STRIP, 0, vd.size(), id.size(), 0);
                else
                    gl.glDrawArrays(GL.GL_TRIANGLE_STRIP, 0, vd.size());
                break;
            case TRIANGLE_FAN:
                if (id != null)
                    gl.glDrawRangeElements(GL.GL_TRIANGLE_FAN, 0, vd.size(), id.size(), 0);
                else
                    gl.glDrawArrays(GL.GL_TRIANGLE_FAN, 0, vd.size());
                break;
            case QUADS:
                if (id != null)
                    gl.glDrawRangeElements(GL.GL_QUADS, 0, vd.size(), id.size(), 0);
                else
                    gl.glDrawArrays(GL.GL_QUADS, 0, vd.size());
                break;
            case QUAD_STRIP:
                if (id != null)
                    gl.glDrawRangeElements(GL.GL_QUAD_STRIP, 0, vd.size(), id.size(), 0);
                else
                    gl.glDrawArrays(GL.GL_QUAD_STRIP, 0, vd.size());
                break;
            default:
                assert(false);
        }

        style.cd.unbindColor(gl);
        vd.unbindVertex(gl);
        if (nd != null)
            nd.unbindNormal(gl);
        if (id != null)
            id.unbindIndex(gl);
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            render(vc, layer, rinfo, gl, style);
    }

    public VzMesh(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeObject(vd);
        outs.writeObject(nd);
        outs.writeObject(id);
        outs.writeInt(type);

        outs.writeInt(styles.length);
        for (int sidx = 0; sidx < styles.length; sidx++)
            outs.writeObject(styles[sidx]);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        vd = (VisAbstractVertexData) ins.readObject();
        nd = (VisAbstractVertexData) ins.readObject();
        id = (VisAbstractIndexData) ins.readObject();
        type = ins.readInt();

        int nstyles = ins.readInt();
        styles = new Style[nstyles];
        for (int sidx = 0; sidx < styles.length; sidx++)
            styles[sidx] = (Style) ins.readObject();

    }
}
