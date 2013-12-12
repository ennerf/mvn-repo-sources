package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

public class VisVertexData implements VisAbstractVertexData, VisSerializable
{
    long id = -1; // allocate an id lazily
    ArrayList<Block> blocks = new ArrayList<Block>();

    static class Block
    {
        float vf[];
        double vd[];
        int nv;
        int dim;
    }

    public VisVertexData()
    {
    }

    public VisVertexData(FloatArray f, int dim)
    {
        add(f.getData(), f.size() / dim, dim);
    }

    public VisVertexData(DoubleArray f, int dim)
    {
        add(f.getData(), f.size() / dim, dim);
    }

    public VisVertexData(float vf[], int nv, int dim)
    {
        add(vf, nv, dim);
    }

    public VisVertexData(double[]  ... points)
    {
        if (points.length > 0)
            add(points);
    }

    public VisVertexData(float[]  ... points)
    {
        if (points.length > 0)
            add(points);
    }

    public VisVertexData(ArrayList<double[]> d)
    {
        if (d.size() > 0)
            add(d);
    }

    /** Add a single vertex: PERFORMANCE WARNING. **/
    public synchronized void add(float v[])
    {
        add(v, 1, v.length);
    }

    /** Add a single vertex: PERFORMANCE WARNING. **/
    public synchronized void add(double v[])
    {
        add(v, 1, v.length);
    }

    /** Add multiple vertices **/
    public synchronized void add(float v[], int nv, int dim)
    {
        Block b = new Block();
        b.vf = v;
        b.nv = nv;
        b.dim = dim;

        blocks.add(b);
    }

    /** Add multiple vertices **/
    public synchronized void add(double v[], int nv, int dim)
    {
        Block b = new Block();
        b.vd = v;
        b.nv = nv;
        b.dim = dim;

        blocks.add(b);
    }

    /** Add multiple vertices **/
    public synchronized void add(double[] ... points)
    {
        int dim = 2;
        for (double p[] : points) {
            if (p.length >= 3)
                dim = 3;
        }

        Block b = new Block();
        b.vd = new double[points.length*dim];

        for (int i = 0; i < points.length; i++) {
            double p[] = points[i];

            b.vd[i*dim+0] = points[i][0];
            b.vd[i*dim+1] = points[i][1];
            if (p.length > 2)
                b.vd[i*dim+2] = points[i][2];
        }

        b.nv = points.length;
        b.dim = dim;

        blocks.add(b);
    }

    public synchronized void add(float[] ... points)
    {
        int dim = 2;
        for (float p[] : points) {
            if (p.length >= 3)
                dim = 3;
        }

        Block b = new Block();
        b.vf = new float[points.length*dim];

        for (int i = 0; i < points.length; i++) {
            float p[] = points[i];

            b.vf[i*dim+0] = points[i][0];
            b.vf[i*dim+1] = points[i][1];
            if (p.length > 2)
                b.vf[i*dim+2] = points[i][2];
        }

        b.nv = points.length;
        b.dim = dim;

        blocks.add(b);
    }

    /** Add multiple vertices **/
    public synchronized void add(ArrayList<double[]> points)
    {
        int dim = 2;
        for (double p[] : points) {
            if (p.length >= 3)
                dim = 3;
        }

        Block b = new Block();
        b.vd = new double[points.size()*dim];

        for (int i = 0; i < points.size(); i++) {
            double p[] = points.get(i);

            b.vd[i*dim+0] = p[0];
            b.vd[i*dim+1] = p[1];
            if (p.length > 2)
                b.vd[i*dim+2] = p[2];
        }

        b.nv = points.size();
        b.dim = dim;

        blocks.add(b);
    }

    public synchronized void addFloats(ArrayList<float[]> points)
    {
        int dim = 2;
        for (float p[] : points) {
            if (p.length >= 3)
                dim = 3;
        }

        Block b = new Block();
        b.vf = new float[points.size()*dim];

        for (int i = 0; i < points.size(); i++) {
            float p[] = points.get(i);

            b.vf[i*dim+0] = p[0];
            b.vf[i*dim+1] = p[1];
            if (p.length > 2)
                b.vf[i*dim+2] = p[2];
        }

        b.nv = points.size();
        b.dim = dim;

        blocks.add(b);
    }

    public synchronized int size()
    {
        combine();

        if (blocks.size() == 0)
            return 0;

        return blocks.get(0).nv;
    }

    synchronized void combine()
    {
        if (blocks.size() <= 1)
            return;

        boolean doubles = false;
        int dim = 2;
        int nv = 0;

        for (Block b : blocks) {
            if (b.vd != null)
                doubles = true;
            if (b.dim == 3)
                dim = 3;

            nv += b.nv;
        }

        if (doubles) {
            // must promote all to doubles

            double vd[] = new double[dim*nv];
            int pos = 0;

            for (Block b : blocks) {

                if (b.vf != null) {
                    for (int i = 0; i < b.nv; i++) {
                        for (int j = 0; j < Math.min(dim, b.dim); j++) {
                            vd[pos*dim+j] = b.vf[i*b.dim+j];
                        }
                        pos++;
                    }
                } else {
                    for (int i = 0; i < b.nv; i++) {
                        for (int j = 0; j < Math.min(dim, b.dim); j++) {
                            vd[pos*dim+j] = b.vd[i*b.dim+j];
                        }
                        pos++;
                    }
                }
            }

            Block b = new Block();
            b.vd = vd;
            b.nv = nv;
            b.dim = dim;
            blocks.clear();
            blocks.add(b);

        } else {
            // keep as floats
            float vf[] = new float[dim*nv];
            int pos = 0;

            for (Block b : blocks) {
                for (int i = 0; i < b.nv; i++) {
                    for (int j = 0; j < b.dim; j++) {
                        vf[pos*dim+j] = b.vf[i*b.dim+j];
                    }
                    pos++;
                }
            }

            Block b = new Block();
            b.vf = vf;
            b.nv = nv;
            b.dim = dim;
            blocks.clear();
            blocks.add(b);
        }

        id = -1;
    }

    public synchronized void bindVertex(GL gl)
    {
        combine();

        if (id < 0)
            id = VisUtil.allocateID();

        if (blocks.size() == 0) {
            gl.gldBind(GL.VBO_TYPE_VERTEX, id, 0, 2, new float[0]);
            return;
        }

        Block b = blocks.get(0);

        if (b.vf != null)
            gl.gldBind(GL.VBO_TYPE_VERTEX, id, b.nv, b.dim, b.vf);
        else
            gl.gldBind(GL.VBO_TYPE_VERTEX, id, b.nv, b.dim, b.vd);
    }

    public synchronized void unbindVertex(GL gl)
    {
        gl.gldUnbind(GL.VBO_TYPE_VERTEX, id);
    }

    public synchronized void bindNormal(GL gl)
    {
        combine();

        if (id < 0)
            id = VisUtil.allocateID();

        if (blocks.size() == 0) {
            gl.gldBind(GL.VBO_TYPE_NORMAL, id, 0, 2, new float[0]);
            return;
        }

        Block b = blocks.get(0);

        if (b.vf != null)
            gl.gldBind(GL.VBO_TYPE_NORMAL, id, b.nv, b.dim, b.vf);
        else
            gl.gldBind(GL.VBO_TYPE_NORMAL, id, b.nv, b.dim, b.vd);
    }

    public synchronized void unbindNormal(GL gl)
    {
        gl.gldUnbind(GL.VBO_TYPE_NORMAL, id);
    }

    public synchronized void bindTexCoord(GL gl)
    {
        combine();

        if (id < 0)
            id = VisUtil.allocateID();

        Block b = blocks.get(0);

        if (b.vf != null)
            gl.gldBind(GL.VBO_TYPE_TEX_COORD, id, b.nv, b.dim, b.vf);
        else
            gl.gldBind(GL.VBO_TYPE_TEX_COORD, id, b.nv, b.dim, b.vd);
    }

    public synchronized void unbindTexCoord(GL gl)
    {
        gl.gldUnbind(GL.VBO_TYPE_TEX_COORD, id);
    }

    public synchronized void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        if (blocks.size() == 0)
            return;

        if (blocks.size() > 1)
            combine();

        if (id < 0)
            id = VisUtil.allocateID();

        Block b = blocks.get(0);
        if (b.nv == 0)/*XXX: No vertices to render*/
            return;

        if (b.vf != null)
            gl.gldBind(GL.VBO_TYPE_VERTEX, id, b.nv, b.dim, b.vf);
        else
            gl.gldBind(GL.VBO_TYPE_VERTEX, id, b.nv, b.dim, b.vd);

        gl.glDrawArrays(GL.GL_POINTS, 0, b.nv);
    }

    public VisVertexData(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        combine();

        outs.writeInt(blocks.size());

        for (Block b : blocks) {
            outs.writeInt(b.nv);
            outs.writeInt(b.dim);
            int n = b.nv * b.dim;
            outs.writeInt(n);

            if (b.vf != null) {
                outs.writeByte(0);

                for (int i = 0; i < n; i++)
                    outs.writeFloat(b.vf[i]);
            } else {
                outs.writeByte(1);

                for (int i = 0; i < n; i++)
                    outs.writeDouble(b.vd[i]);
            }
        }
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        int nblocks = ins.readInt();

        for (int bidx = 0; bidx < nblocks; bidx++) {
            Block b = new Block();
            b.nv = ins.readInt();
            b.dim = ins.readInt();
            int n = ins.readInt();
            int type = ins.readByte();

            if (type == 0) {
                b.vf = new float[n];
                for (int i = 0; i < n; i++)
                    b.vf[i] = ins.readFloat();
            } else {
                b.vd = new double[n];
                for (int i = 0; i < n; i++)
                    b.vd[i] = ins.readDouble();
            }

            blocks.add(b);
        }
    }

}
