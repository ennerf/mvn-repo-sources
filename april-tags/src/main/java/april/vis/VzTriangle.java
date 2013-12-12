package april.vis;

import java.awt.*;

public class VzTriangle implements VisObject
{
    double lenA, lenB, lenC;
    Style styles[];

    VzMesh mesh;
    VzLines lines;

    public VzTriangle(Style ... styles)
    {
        this(0.5, 1.0, 1.0, styles);
    }

    /**
     * Construct triangle with the specified side lengths drawn at centroid (+)
     *
     *                *
     *       B     *   *
     *          *       *  C
     *       *     +     *
     *    *               *
     *  *******************
     *           A
     **/
    public VzTriangle(double lenA, double lenB, double lenC, Style ... styles)
    {
        this.lenA = lenA;
        this.lenB = lenB;
        this.lenC = lenC;

        // verify triangle inequality
        assert (lenA <= lenB + lenC);
        assert (lenB <= lenA + lenC);
        assert (lenC <= lenA + lenB);
        assert (lenA*lenB*lenC > 0);   // force them all to be non-zero

        makeVertices(lenA, lenB, lenC);
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, Style style)
    {
        if (style instanceof VzLines.Style)
            lines.render(vc, layer, rinfo, gl, (VzLines.Style) style);

        if (style instanceof VzMesh.Style)
            mesh.render(vc, layer, rinfo, gl, (VzMesh.Style) style);
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles)
            render(vc, layer, rinfo, gl, style);
    }

    private void makeVertices(double A, double B, double C)
    {
        VisVertexData vd = new VisVertexData();

        float x = 0;
        float y = 0;

        double angle = Math.PI - Math.acos((A*A + C*C - B*B) / (2.0*A*C));

        // first create points w.r.t. midpoint of side A
        float [][]pts = new float[3][];
        pts[0] = new float[] {(float) (-0.5*A), 0};
        pts[1] = new float[] {(float)  (0.5*A), 0};
        pts[2] = new float[] {(float)  (0.5*A + lenC*Math.cos(angle)),
                              (float) (lenC*Math.sin(angle))};

        // transform points to centroid
        for (int i = 0; i < 3; i++) {
            x += pts[i][0];
            y += pts[i][1];
        }
        x /= 3;
        y /= 3;

        for (int i = 0; i < 3; i++) {
            pts[i][0] -= x;
            pts[i][1] -= y;
            vd.add(pts[i]);
        }

        lines = new VzLines(vd, VzLines.LINE_LOOP);
        mesh = new VzMesh(vd, VzMesh.TRIANGLES);
    }
}