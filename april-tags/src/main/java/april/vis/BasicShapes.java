package april.vis;

public class BasicShapes
{
    public int   mode; // e.g. GL_TRIANGLE_FAN

    public long  vid;
    public int   vsz;
    public int   vdim;
    public float v[]; // vertex data

    public long  nid;
    public int   ndim; // always 3
    public int   nsz;
    public float n[]; // normals

    public BasicShapes(int mode, int vdim, float v[], float n[])
    {
        this.mode = mode;

        if (n != null) {
            this.nid = VisUtil.allocateID();
            this.ndim = 3;
            this.nsz = n.length / 3;
            this.n = n;
        }

        this.vid = VisUtil.allocateID();
        this.vdim = vdim;
        this.vsz = v.length / vdim;
        this.v = v;
    }

    public void bind(GL gl)
    {
        gl.gldBind(GL.VBO_TYPE_VERTEX, vid, vsz, vdim, v);
        if (n != null)
            gl.gldBind(GL.VBO_TYPE_NORMAL, nid, nsz, ndim, n);
    }

    public void draw(GL gl, int mode)
    {
        gl.glDrawArrays(mode, 0, vsz);
    }

    public void unbind(GL gl)
    {
        gl.gldUnbind(GL.VBO_TYPE_VERTEX, vid);
        if (n != null)
            gl.gldUnbind(GL.VBO_TYPE_NORMAL, nid);
    }

    // Can use either GL_LINE_STRIP or GL_QUADS.
    public static BasicShapes square = new BasicShapes(GL.GL_QUADS,
                                                       2,
                                                       new float[] { -1, -1,
                                                                     1, -1,
                                                                     1, 1,
                                                                     -1, 1 },
                                                       null);


    public static BasicShapes circleOutline16 = new BasicShapes(GL.GL_LINE_LOOP,
                                                                2,
                                                                makeCircleOutline(16),
                                                                null);

    public static BasicShapes circleFill16 = new BasicShapes(GL.GL_TRIANGLE_FAN,
                                                             2,
                                                             makeCircleFill(16),
                                                             null);

    static float[] makeCircleOutline(int n)
    {
        float v[] = new float[n*2];

        for (int i = 0; i < n; i++) {
            double theta = 2*Math.PI * i / n;
            v[2*i+0] = (float) Math.cos(theta);
            v[2*i+1] = (float) Math.sin(theta);
        }

        return v;
    }

    static float[] makeCircleFill(int n)
    {
        float v[] = new float[(n+1)*2+2];

        v[0] = 0;
        v[1] = 0;

        for (int i = 0; i <= n; i++) {
            double theta = 2*Math.PI * i / n;
            v[2+2*i+0] = (float) Math.cos(theta);
            v[2+2*i+1] = (float) Math.sin(theta);
        }

        return v;
    }

}
