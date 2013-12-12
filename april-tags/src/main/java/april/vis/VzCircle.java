package april.vis;

import java.awt.*;
import java.io.*;
import java.util.HashMap;
/** A circle in the XY plane centered at zero. Statically caches lines
 * and mesh in hashmap for instantiated circles.**/

public class VzCircle implements VisObject, VisSerializable
{

    // Synchronized on lineMap to access both
    public static HashMap<Integer, VzLines> lineMap = new HashMap<Integer, VzLines>();
    public static HashMap<Integer, VzMesh> meshMap = new HashMap<Integer, VzMesh>();

    double r;
    Style styles[];

    private VzLines lines;
    private VzMesh  mesh;

    public VzCircle(Style ... styles)
    {
        this(1.0, styles);
    }

    public VzCircle(double r, Style ... styles)
    {
        this(r, 16, styles); // Default points per circle is 16
    }

    public VzCircle(double r, int npoints, Style ... styles)
    {
        this.r = r;
        this.styles = styles;

        synchronized(lineMap) {
            lines = lineMap.get(npoints);

            if (lines == null){
                lineMap.put(npoints,new VzLines(makeCircleOutline(npoints), VzLines.LINE_LOOP));
                meshMap.put(npoints,new VzMesh(makeCircleFill(npoints), VzMesh.TRIANGLE_FAN));

                lines = lineMap.get(npoints);
            }
            mesh = meshMap.get(npoints);
        }
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, Style style)
    {
        gl.glPushMatrix();
        gl.glScaled(r, r, r);

        if (style instanceof VzLines.Style)
            lines.render(vc, layer, rinfo, gl, (VzLines.Style) style);

        if (style instanceof VzMesh.Style)
            mesh.render(vc, layer, rinfo, gl, (VzMesh.Style) style);

        gl.glPopMatrix();
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            render(vc, layer, rinfo, gl, style);
    }

    public static VisVertexData makeCircleOutline(int n)
    {
        VisVertexData vd = new VisVertexData();

        for (int i = 0; i < n; i++) {
            double theta = 2*Math.PI * i / n;
            vd.add(new float[] { (float) Math.cos(theta),
                                 (float) Math.sin(theta) });
        }

        return vd;
    }

    public static VisVertexData makeCircleFill(int n)
    {
        VisVertexData vd = new VisVertexData();

        vd.add(new float[] { 0, 0 });

        for (int i = 0; i <= n; i++) {
            double theta = 2*Math.PI * i / n;
            vd.add(new float[] { (float) Math.cos(theta),
                                 (float) Math.sin(theta) });
        }

        return vd;
    }

    public VzCircle(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeDouble(r);

        outs.writeInt(styles.length);
        for (int sidx = 0; sidx < styles.length; sidx++)
            outs.writeObject(styles[sidx]);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        r = ins.readDouble();

        int nstyles = ins.readInt();
        styles = new Style[nstyles];
        for (int sidx = 0; sidx < styles.length; sidx++)
            styles[sidx] = (Style) ins.readObject();

    }
}
