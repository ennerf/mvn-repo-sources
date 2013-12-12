package april.vis;

import java.awt.*;
import java.io.*;

public class VzRectangle implements VisObject, VisSerializable
{
    double sx, sy;

    Style styles[];

    static VzLines lines;
    static VzMesh  mesh;
    static VzPoints points;

    static {

        // vertex data for GL_QUADS and for LINE LOOP
        float vd[] = new float[] { -1, -1,
                                   1, -1,
                                   1, 1,
                                   -1, 1 };

        VisVertexData fillVertices = new VisVertexData(vd, vd.length / 2, 2);

        // normal data for GL_QUADS
        float nd[] = new float[] { 0, 0, 1,
                                   0, 0, 1,
                                   0, 0, 1,
                                   0, 0, 1 };

        VisVertexData fillNormals = new VisVertexData(nd, nd.length / 3, 3);

        mesh = new VzMesh(fillVertices, fillNormals, VzMesh.QUADS);

        lines = new VzLines(fillVertices, VzLines.LINE_LOOP);
        points = new VzPoints(fillVertices);

    }

    /** A box that extends from -1 to +1 along x, and y axis **/
    public VzRectangle(Style ... styles)
    {
        this(2, 2, styles);
    }

    /** A box that extends from -sx/2 to sx/2, -sy/2 to sy/2 **/
    public VzRectangle(double sx, double sy, Style ... styles)
    {
        this.sx = sx / 2;
        this.sy = sy / 2;
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, Style style)
    {
        gl.glPushMatrix();
        gl.glScaled(sx, sy, 1);

        if (style instanceof VzMesh.Style)
            mesh.render(vc, layer, rinfo, gl, (VzMesh.Style) style);

/*            fillStyle.bindFill(gl);
            gl.gldBind(GL.VBO_TYPE_VERTEX, vdid, vd.length / 3, 3, vd);
            gl.gldBind(GL.VBO_TYPE_NORMAL, ndid, nd.length / 3, 3, nd);

            gl.glDrawArrays(GL.GL_QUADS, 0, vd.length / 3);

            gl.gldUnbind(GL.VBO_TYPE_VERTEX, vdid);
            gl.gldUnbind(GL.VBO_TYPE_NORMAL, ndid);
            fillStyle.unbindFill(gl);
        }
*/
        if (style instanceof VzLines.Style)
            lines.render(vc, layer, rinfo, gl, (VzLines.Style) style);

        if (style instanceof VzPoints.Style)
            points.render(vc, layer, rinfo, gl, (VzPoints.Style) style);

        gl.glPopMatrix();
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            render(vc, layer, rinfo, gl, style);
    }

    public VzRectangle(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeDouble(sx);
        outs.writeDouble(sy);

        outs.writeInt(styles.length);
        for (int sidx = 0; sidx < styles.length; sidx++)
            outs.writeObject(styles[sidx]);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        sx = ins.readDouble();
        sy = ins.readDouble();

        int nstyles = ins.readInt();
        styles = new Style[nstyles];
        for (int sidx = 0; sidx < styles.length; sidx++)
            styles[sidx] = (Style) ins.readObject();

    }
}
