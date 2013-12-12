package april.vis;

import java.io.*;

public class VisDepthTest extends VisChain
{
    boolean enabled;

    public VisDepthTest(boolean enabled, Object ... os)
    {
        super (os);

        this.enabled = enabled;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glPushAttrib(gl.GL_ENABLE_BIT);

        if (!enabled)
            gl.glDisable(GL.GL_DEPTH_TEST);
        else
            gl.glEnable(GL.GL_DEPTH_TEST);

        super.render(vc, layer, rinfo, gl);

        gl.glPopAttrib();
    }

    public VisDepthTest(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        super.writeObject(outs);
        outs.writeBoolean(enabled);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        super.readObject(ins);
        enabled = ins.readBoolean();
    }
}
