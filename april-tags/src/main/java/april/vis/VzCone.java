package april.vis;

import java.awt.*;
import java.util.*;
import java.io.*;

import april.jmat.*;

/** The cone's circular base is centered at the origin and in the XY
 * plane. The point extends in the +z direction. **/
public class VzCone implements VisObject, VisSerializable
{
    Style styles[];
    double r, h;
    int flags;

    public static final int BOTTOM = 1;

    static VzMesh barrel;
    static VzMesh circle;

    static {
        VisVertexData barrelData = new VisVertexData();
        VisVertexData barrelNormals = new VisVertexData();
        VisVertexData circleData = new VisVertexData();

        circleData.add(new float[2]);

        int n = 16;

        barrelData.add(new float[] { 0, 0, 1 });
        barrelNormals.add(new float[] { 0, 0, 1 });

        for (int i = 0; i <= n; i++) {
            double theta = 2*Math.PI * i / n;

            barrelData.add(new float[] { (float) Math.cos(theta),
                                         (float) Math.sin(theta) });

            barrelNormals.add(new float[] { (float) Math.cos(theta),
                                            (float) Math.sin(theta),
                                            0 });

            circleData.add(new float[] { (float) Math.cos(theta),
                                         (float) Math.sin(theta) });

        }

        barrel = new VzMesh(barrelData, barrelNormals, VzMesh.TRIANGLE_FAN);
        circle = new VzMesh(circleData, VzMesh.TRIANGLE_FAN);
    }

    public VzCone(Style ... styles)
    {
        this(1, 1, styles);
    }

    public VzCone(double r, double h, Style ... styles)
    {
        this(r, h, BOTTOM, styles);
    }

    public VzCone(double r, double h, int flags, Style ... styles)
    {
        this.r = r;
        this.h = h;
        this.flags = flags;
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glPushMatrix();
        gl.glScaled(r, r, h);

        for (Style style : styles) {
            if (style instanceof VzMesh.Style) {
                barrel.render(vc, layer, rinfo, gl, (VzMesh.Style) style);

                if ((flags & BOTTOM) != 0) {
                    gl.glNormal3f(0, 0, -1);

                    circle.render(vc, layer, rinfo, gl, (VzMesh.Style) style);
                }
            }
        }

        gl.glPopMatrix();
    }

    public VzCone(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeDouble(r);
        outs.writeDouble(h);
        outs.writeInt(flags);

        outs.writeInt(styles.length);
        for (int sidx = 0; sidx < styles.length; sidx++)
            outs.writeObject(styles[sidx]);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        r = ins.readDouble();
        h = ins.readDouble();
        flags = ins.readInt();

        int nstyles = ins.readInt();
        styles = new Style[nstyles];
        for (int sidx = 0; sidx < styles.length; sidx++)
            styles[sidx] = (Style) ins.readObject();

    }
}
