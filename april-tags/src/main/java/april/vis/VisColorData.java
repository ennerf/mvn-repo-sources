package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

public class VisColorData implements VisAbstractColorData, VisSerializable
{
    long id = -1; // allocate an id lazily
    ArrayList<int[]> blocks = new ArrayList<int[]>();

    int tmp[];
    int tmpsz;

    public VisColorData()
    {
    }

    /** Note: color order is AABBGGRR **/
    public VisColorData(int c[])
    {
        add(c);
    }

    void tmpFlush()
    {
        if (tmpsz > 0) {
            blocks.add(tmp);
            tmpsz = 0;
            tmp = null;
        }
    }

    /** Add a single color, AABBGGRR. Sequential calls to this are optimized. **/
    public synchronized void add(int c)
    {
        if (tmp == null) {
            tmp = new int[256];
            tmpsz = 0;
        }
        tmp[tmpsz++] = c;

        if (tmpsz == tmp.length) {
            tmpFlush();
        }
    }

    public synchronized void add(int c[])
    {
        tmpFlush();

        blocks.add(c);
    }

    synchronized void combine()
    {
        tmpFlush();

        if (blocks.size() <= 1)
            return;

        int sz = 0;
        for (int b[] : blocks)
            sz += b.length;

        int idata[] = new int[sz];
        int pos = 0;
        for (int b[] : blocks) {
            for (int i = 0; i < b.length; i++)
                idata[pos++] = b[i];
        }

        blocks.clear();
        blocks.add(idata);
    }

    public synchronized int size()
    {
        combine();

        if (blocks.size() == 0)
            return 0;

        return blocks.get(0).length;
    }

    public synchronized void bindColor(GL gl)
    {
        combine();

        if (id < 0)
            id = VisUtil.allocateID();

        int b[] = blocks.get(0);

        // the back-end special cases VBO_TYPE_COLOR, and uses gltype = unsigned byte.
        gl.glColor(Color.white);

        gl.gldBind(GL.VBO_TYPE_COLOR, id, b.length, 4, b);
    }

    public synchronized void unbindColor(GL gl)
    {
        int b[] = blocks.get(0);

        gl.gldUnbind(GL.VBO_TYPE_COLOR, id);
    }

    public VisColorData(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        combine();

        outs.writeInt(blocks.size());

        for (int b[] : blocks) {
            outs.writeInt(b.length);
            for (int i = 0; i < b.length; i++) {
                outs.writeInt(b[i]);
            }
        }
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        int nblocks = ins.readInt();

        for (int bidx = 0; bidx < nblocks; bidx++) {
            int len = ins.readInt();
            int b[] = new int[len];
            for (int i = 0; i < b.length; i++) {
                b[i] = ins.readInt();
            }
            blocks.add(b);
        }
    }

}
