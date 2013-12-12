package april.vis;

import april.jmat.*;
import java.awt.*;
import java.io.*;

public class VzAxes implements VisObject, VisSerializable
{
    static VisObject vo;

    static {
        float _vd[] = new float[] { 0, 0, 0,
                                   1, 0, 0,

                                   0, 0, 0,
                                   0, 1, 0,

                                   0, 0, 0,
                                   0, 0, 1
        };

        int _cd[] = new int[] { 0xff0000ff,
                               0xff0000ff,
                               0xff00ff00,
                               0xff00ff00,
                               0xffff0000,
                               0xffff0000
        };

        VisVertexData vd = new VisVertexData(_vd, _vd.length / 3, 3);
        VisColorData cd = new VisColorData(_cd);

        double s = 0.1;

        vo = new VisChain(new VzLines(vd, VzLines.LINES, new VzLines.Style(cd, 1)),
                          new VisChain(LinAlg.translate(1, 0, 0),
                                       LinAlg.rotateY(Math.PI/2),
                                       LinAlg.scale(s/2, s/2, s),
                                       new VzSquarePyramid(VzSquarePyramid.BOTTOM,
                                                           new VzMesh.Style(Color.red))),
                          new VisChain(LinAlg.translate(0, 1, 0),
                                       LinAlg.rotateX(-Math.PI/2),
                                       LinAlg.scale(s/2, s/2, s),
                                       new VzSquarePyramid(VzSquarePyramid.BOTTOM,
                                                           new VzMesh.Style(Color.green))),
                          new VisChain(LinAlg.translate(0, 0, 1),
                                       LinAlg.scale(s/2, s/2, s),
                                       new VzSquarePyramid(VzSquarePyramid.BOTTOM,
                                                           new VzMesh.Style(Color.blue))));
    }

    public VzAxes()
    {
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        vo.render(vc, layer, rinfo, gl);
    }

    public VzAxes(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        // nothing to do
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        // nothing to do
    }
}
