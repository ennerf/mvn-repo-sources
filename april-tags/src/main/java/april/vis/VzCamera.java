package april.vis;

import java.awt.*;
import java.io.*;

import april.jmat.*;

/** A camera formed by a box and a pyramid, with a focal point (the
 * apex of the pyramid) pointing down the +x axis.
 **/
public class VzCamera implements VisObject, VisSerializable
{
    Style styles[];

    static VzBox box = new VzBox();
    static VzSquarePyramid pyramid = new VzSquarePyramid();

    public VzCamera()
    {
        this(new VzMesh.Style(Color.gray));
    }

    public VzCamera(Style ... styles)
    {
        this.styles = styles;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl, Style style)
    {
        box.render(vc, layer, rinfo, gl, style);

        gl.glPushMatrix();
        gl.glMultMatrix(LinAlg.multiplyMany(LinAlg.scale(1, .5, .5),
                                            LinAlg.translate(1, 0, 0),
                                            LinAlg.rotateY(-Math.PI/2)));

        pyramid.render(vc, layer, rinfo, gl, style);

        gl.glPopMatrix();
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        for (Style style : styles) {
            render(vc, layer, rinfo, gl, style);
        }
    }

    public VzCamera(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeInt(styles.length);
        for (int sidx = 0; sidx < styles.length; sidx++)
            outs.writeObject(styles[sidx]);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        int nstyles = ins.readInt();
        styles = new Style[nstyles];
        for (int sidx = 0; sidx < styles.length; sidx++)
            styles[sidx] = (Style) ins.readObject();
    }
}
