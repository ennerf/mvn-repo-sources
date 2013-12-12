package april.vis;

import java.io.*;
import java.util.*;

public class VisChain implements VisObject, VisSerializable
{
    ArrayList<Object> ops = new ArrayList<Object>();

    int displayListId;
    GL displayListGL;
    boolean lock;

    public VisChain()
    {
    }

    public VisChain(Object ... os)
    {
        add(os);
    }

    // this method must be added to disabiguate between a
    // two-dimensional array being interpreted as a varargs call
    // consisting of several one-dimensional doubles.
    public void add(double M[][])
    {
        ops.add(M);
    }

    public void add(Object ... os)
    {
        int i = 0;

        while (i < os.length) {
            if (os[i] == null) {
                i++;
                continue;
            }

            if (os[i] instanceof double[][]) {
                ops.add(os[i]);
                i++;
                continue;
            }

            if (os[i] instanceof VisObject) {
                ops.add((VisObject) os[i]);
                i++;
                continue;
            }

            // unknown type!
            System.out.println("VisChain: Unknown object added to chain: "+os[i]);
            assert(false);
            i++;
        }
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glPushMatrix();

        for (Object o : ops) {

            if (o instanceof double[][]) {
                gl.glMultMatrix((double[][]) o);
                continue;
            }

            if (o instanceof VisObject) {
                VisObject vo = (VisObject) o;
                vo.render(vc, layer, rinfo, gl);
            }
        }

        gl.glPopMatrix();
    }

    /** for serialization only **/
    public VisChain(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeInt(ops.size());

        for (Object o : ops) {
            if (o == null) {
                outs.writeByte(0);
                outs.writeObject(null);
            } else if (o instanceof VisObject) {
                outs.writeByte(1);
                outs.writeObject(o);
            } else if (o instanceof double[][]) {
                outs.writeByte(2);
                outs.writeDoubleMatrix((double[][]) o);
            }
        }
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        int sz = ins.readInt();

        for (int i = 0; i < sz; i++) {
            int type = ins.readByte();

            if (type == 0)
                add(null);
            else if (type == 1)
                add(ins.readObject());
            else if (type == 2)
                add(ins.readDoubleMatrix());
            else
                assert(false);
        }
    }
}
