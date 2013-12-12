package april.vis;

import java.io.*;

/** Changes the model view and projection matrix to create a one unit
 * to one pixel projection. The origin of this projection can be
 * configured to be various parts of the screen, which are well-suited
 * to positioning text or images as overlayed data. The resulting
 * coordinate system will be an OpenGL-style coordinate system, where
 * y=0 is on the bottom (not the top). **/
public class VisPixCoords extends VisChain
{
    public enum ORIGIN { TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, CENTER_ROUND, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT };

    ORIGIN origin;

    public VisPixCoords(ORIGIN origin, Object... os)
    {
        super(os);
        this.origin = origin;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        int viewport[] = rinfo.layerPositions.get(layer);
        double width = viewport[2];
        double height = viewport[3];

        gl.glMatrixMode(GL.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glMultMatrix(VisUtil.glOrtho(0, width, 0, height, -1, 1));

        gl.glMatrixMode(GL.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();

        double tx = 0, ty = 0;

        switch (origin) {
            case BOTTOM_LEFT:
            case BOTTOM:
            case BOTTOM_RIGHT:
                ty = 0;
                break;

            case LEFT:
            case CENTER:
            case RIGHT:
                ty = height / 2;
                break;
            case CENTER_ROUND:
                ty = Math.round(height/2);
                break;

            case TOP_LEFT:
            case TOP:
            case TOP_RIGHT:
                ty = height;
                break;
        }

        switch (origin) {
            case BOTTOM_LEFT:
            case TOP_LEFT:
            case LEFT:
                tx = 0;
                break;

            case BOTTOM:
            case CENTER:
            case TOP:
                tx = width / 2;
                break;

            case CENTER_ROUND:
                tx = Math.round(width/2);
                break;

            case BOTTOM_RIGHT:
            case TOP_RIGHT:
            case RIGHT:
                tx = width;
                break;
        }

        gl.glTranslated(tx, ty, 0);

        super.render(vc, layer, rinfo, gl);

        gl.glMatrixMode(GL.GL_MODELVIEW);
        gl.glPopMatrix();

        gl.glMatrixMode(GL.GL_PROJECTION);
        gl.glPopMatrix();

        gl.glMatrixMode(GL.GL_MODELVIEW);
    }

    public VisPixCoords(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        super.writeObject(outs);
        outs.writeUTF(origin.name());
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        super.readObject(ins);
        origin = ORIGIN.valueOf(ins.readUTF());
    }

}
