package april.vis;

import java.awt.*;
import java.io.*;
import javax.swing.*;

import april.jmat.*;

public class VzGrid implements VisObject, VisSerializable
{
    /** If non-null, specifies fixed spacing for gridlines in the x
     * and y directions.
     **/
    public double spacing[];

    /** The last spacing actually rendered. might be null. **/
    public double lastspacing[];

    Style styles[];

    static int sz = 100; // number of lines to use when rendering grid.
    static VzLines gridLines;
    static VzCircle groundCircle = new VzCircle(1);

    static {
        // build (2*sz+1) lines that form a grid covering [-sz,-sz] to
        // [sz, sz]. Two vertices (four points) per line.
        VisVertexData gridLinesData = new VisVertexData();

        for (int i = -sz; i <= sz; i++) {
            float r = (float) Math.sqrt(sz*sz - i*i);
            gridLinesData.add(new float[] { -r, i });
            gridLinesData.add(new float[] { r, i });
        }

        gridLines = new VzLines(gridLinesData, VzLines.LINES);
    }

    public VzGrid()
    {
        this(new VzMesh.Style(new Color(32,32,32,64)),
             new VzLines.Style(new Color(128,128,128), 1));

    }

    /** Better quality usually results from listing mesh style first, followed by line styles. **/
    public VzGrid(Style ... styles)
    {
        this.styles = styles;
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        VisCameraManager.CameraPosition cameraPosition = rinfo.cameraPositions.get(layer);
        double M[][] = cameraPosition.getModelViewMatrix();

        double eye_dist = LinAlg.distance(cameraPosition.eye, cameraPosition.lookat);
        double spacingDefault = round_to_125(eye_dist / 10 );

        double spacingx = spacingDefault, spacingy = spacingDefault;
        if (spacing != null) {
            spacingx = spacing[0];
            spacingy = spacing[1];
        }

        lastspacing = new double[] { spacingx, spacingy };

        gl.glPushMatrix();

        double tx = Math.ceil(cameraPosition.lookat[0] / spacingx)*spacingx;
        double ty = Math.ceil(cameraPosition.lookat[1] / spacingy)*spacingy;

        gl.glTranslated(tx, ty, 0);

        gl.glMultMatrix(LinAlg.scale(spacingx, spacingy, 1));

        for (Style style : styles) {
            if (style instanceof VzMesh.Style) {
                gl.glPushMatrix();
                gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
                gl.glPolygonOffset(5,5);
                gl.glMultMatrix(LinAlg.scale(sz, sz, sz));
                groundCircle.render(vc, layer, rinfo, gl, style);
                gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);
                gl.glPopMatrix();
            }

            if (style instanceof VzLines.Style) {
                gl.glPushMatrix();
                gridLines.render(vc, layer, rinfo, gl, (VzLines.Style) style);
                gl.glMultMatrix(LinAlg.rotateZ(Math.PI/2));
                gridLines.render(vc, layer, rinfo, gl, (VzLines.Style) style);
                gl.glPopMatrix();
            }
       }

        gl.glPopMatrix();
    }

    /** round the input number to the next number of the form 1*10^n,
     * 2*10^n, or 5*10^n. */
    static double round_to_125(double in)
    {
        double v = 0.1; // minimum allowable value. Must be of form 1*10^n.

        while (v < in) {
            if (v < in)
                v *= 2;
            if (v < in)
                v = v/2 * 5;
            if (v < in)
                v *= 2;
        }

        return v;
    }

    /** Create a VzGrid with the default properties and a text overlay. **/
    public static VzGrid addGrid(VisWorld vw)
    {
        return addGrid(vw, new VzGrid());
    }

    public static VzGrid addGrid(VisWorld vw, VzGrid vg)
    {
        if (true) {
            // for transparent grid surfaces, we need to draw
            // last. Otherwise we end up setting Z buffer values and
            // blocking any objects behind it.

            // however, alpha blending is ugly this way around VzText.
            VisWorld.Buffer vb = vw.getBuffer("grid");
            vb.setDrawOrder(10000);
            vb.addFront(vg);
        }

        if (true) {
            VisWorld.Buffer vb = vw.getBuffer("grid-overlay");
            vb.setDrawOrder(10001);

            vb.addFront(new VisPixCoords(VisPixCoords.ORIGIN.CENTER_ROUND,
                                                new VisDepthTest(false,
                                                                 // translate so we don't draw on top of canvas dimensions.
                                                                 LinAlg.translate(0, -30, 0),
                                                                 new VzGridText(vg, VzText.ANCHOR.CENTER_ROUND,
                                                                                "<<sansserif-12,white>>grid %.2f m"))));
        }

        return vg;
    }

    public VzGrid(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeDoubles(spacing);

        outs.writeInt(styles.length);
        for (int sidx = 0; sidx < styles.length; sidx++)
            outs.writeObject(styles[sidx]);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        spacing = ins.readDoubles();

        int nstyles = ins.readInt();
        styles = new Style[nstyles];
        for (int sidx = 0; sidx < styles.length; sidx++)
            styles[sidx] = (Style) ins.readObject();
    }
}
