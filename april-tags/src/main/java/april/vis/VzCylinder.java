package april.vis;

import java.awt.*;
import java.util.*;
import java.io.*;

import april.jmat.*;

/** Cylinder's geometric center is at the origin; it has a circular
 * footprint in the XY plane and extends from z=-h/2 to z=h/2 **/
public class VzCylinder implements VisObject, VisSerializable
{
    Style styles[];
    double r, h;
    int flags;

    public static final int TOP = 1, BOTTOM = 2;

    static VzMesh barrel;
    static VzMesh circle;

    static {
        VisVertexData barrelData = new VisVertexData();
        VisVertexData barrelNormals = new VisVertexData();
        VisVertexData circleData = new VisVertexData();

        circleData.add(new float[2]);

        int n = 16;
        for (int i = 0; i <= n; i++) {
            double theta = 2*Math.PI * i / n;

            barrelData.add(new float[] { (float) Math.cos(theta),
                                         (float) Math.sin(theta),
                                         1 });
            barrelData.add(new float[] { (float) Math.cos(theta),
                                         (float) Math.sin(theta),
                                         -1 });
            barrelNormals.add(new float[] { (float) Math.cos(theta),
                                            (float) Math.sin(theta),
                                            0 });
            barrelNormals.add(new float[] { (float) Math.cos(theta),
                                            (float) Math.sin(theta),
                                            0 });

            circleData.add(new float[] { (float) Math.cos(theta),
                                         (float) Math.sin(theta) });

        }

        barrel = new VzMesh(barrelData, barrelNormals, VzMesh.TRIANGLE_STRIP);
        circle = new VzMesh(circleData, VzMesh.TRIANGLE_FAN);
    }

    public VzCylinder(Style ... styles)
    {
        this(1, 1, styles);
    }

    public VzCylinder(double r, double h, Style ... styles)
    {
        this(r, h, TOP | BOTTOM, styles);
    }

    public VzCylinder(double r, double h, int flags, Style ... styles)
    {
        this.r = r;
        this.h = h;
        this.flags = flags;
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glPushMatrix();
        gl.glScaled(r, r, h/2);

        for (Style style : styles) {
            if (style instanceof VzMesh.Style) {
                barrel.render(vc, layer, rinfo, gl, (VzMesh.Style) style);

                if ((flags & TOP) != 0) {
                    gl.glNormal3f(0, 0, 1);
                    gl.glTranslated(0, 0, 1);
                    circle.render(vc, layer, rinfo, gl, (VzMesh.Style) style);
                    gl.glTranslated(0, 0, -1);
                }

                if ((flags & BOTTOM) != 0) {
                    gl.glNormal3f(0, 0, -1);
                    gl.glTranslated(0, 0, -1);
                    circle.render(vc, layer, rinfo, gl, (VzMesh.Style) style);
                    gl.glTranslated(0, 0, 1);
                }
            }
        }

        gl.glPopMatrix();
    }

    public VzCylinder(ObjectReader ins)
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
