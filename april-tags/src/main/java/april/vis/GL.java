package april.vis;

import java.awt.*;
import java.awt.image.*;
import java.util.*;

// All methods should be called from the same thread!
//
// XXX Many of the commands take bitfield masks which we're casting to
// doubles. Should we use Double.intToDoubles instead?
//
// XXX Don't currently expose ability to specify different depth
// buffer/color buffer bit depths.
public class GL
{
    DoubleArray cmd = new DoubleArray();

    private static native int gl_initialize();
    private static native int gl_fbo_create(int width, int height);
    private static native int gl_fbo_destroy(int fboid);
    private static native int gl_fbo_bind(int fboid);
//    private static native int[] gl_read_pixels(int width, int height);
    private static native int gl_read_pixels2(int width, int height, int data[]);
    private static native int gl_read_pixels2(int width, int height, byte data[]);
    private static native int gl_ops(double toks[], int toklen);

    private static native int gl_get_double_v(int param, double d[]);

    public static final int VBO_TYPE_VERTEX=1, VBO_TYPE_NORMAL=2, VBO_TYPE_COLOR=3, VBO_TYPE_TEX_COORD=4, VBO_TYPE_ELEMENT_ARRAY=5;

    // bind and unbind vertex data
    private static native int gldata_bind(int vbo_type, long id, int nvertices, int vertdim, float data[]);
    private static native int gldata_bind(int vbo_type, long id, int nvertices, int vertdim, double data[]);
    private static native int gldata_bind(int vbo_type, long id, int nvertices, int vertdim, int data[]);
    private static native int gldata_bind(int vbo_type, long id, int nvertices, int vertdim, short data[]);
    private static native int gldata_unbind(int vbo_type, long id);

    private static native int gldata_tex_bind(long id, int internalfmt, int width, int height, int fmt, int type, int data[], int flags);
    private static native int gldata_tex_bind(long id, int internalfmt, int width, int height, int fmt, int type, byte data[], int flags);
    private static native int gldata_tex_unbind(long id);

    // call this function before rendering the objects that are part
    // of a single frame.  this will mark subsequently drawn objects
    // as having been drawn as part of the specified frame, which is
    // necessary for the garbage collector.
    private static native int gldata_begin_frame(long canvasid);

    // signals that all objects have been rendered from this
    // frame. Should be called *exactly once* between frames.  look
    // for data to be deallocated. We will retain all objects from the
    // last frame, plus older objects up to memsize bytes.
    private static native int gldata_end_frame_and_collect(long canvasid, int memsize);

    private static native int gl_get_error();

    static final int OP_TRANSLATED = 1, OP_PUSHMATRIX = 2, OP_POPMATRIX = 3, OP_MULTMATRIXD = 4,
        OP_LOADIDENTITY = 5, OP_MATRIXMODE = 6, OP_ENABLE = 7, OP_DISABLE = 8, OP_SCALED = 9,
        OP_CLEAR = 10, OP_COLOR = 11, OP_CLEARCOLOR = 12, OP_CLEARDEPTH = 13, OP_SCISSOR = 14,
        OP_LIGHTFV = 15, OP_LIGHTMODELI = 16, OP_COLORMATERIAL = 17, OP_MATERIALF = 18,
        OP_MATERIALFV = 19, OP_DEPTHFUNC = 20, OP_BLENDFUNC = 21, OP_POLYGONMODE = 22, OP_HINT = 23,
        OP_SHADEMODEL = 24, OP_BEGIN = 25, OP_END = 26, OP_VERTEX2D = 27, OP_VERTEX3D = 28,
        OP_VIEWPORT = 29, OP_DRAWARRAYS = 30, OP_POLYGONOFFSET = 31, OP_LINEWIDTH = 32,
        OP_PUSHATTRIB = 33, OP_POPATTRIB = 34, OP_POINTSIZE = 35, OP_TEXCOORD2D = 36, OP_NORMAL3F = 37,
        OP_ALPHA_FUNC = 38, OP_ROTATED = 39, OP_DRAWRANGEELEMENTS = 40;

    public static final int GL_TRUE = 1, GL_FALSE = 0;

    // matrix mode enums
    public static final int GL_MODELVIEW = 0x1700, GL_PROJECTION = 0x1701, GL_TEXTURE = 0x1702;

    // glClear
    public static final int GL_COLOR_BUFFER_BIT = 0x4000, GL_DEPTH_BUFFER_BIT = 0x100, GL_STENCIL_BUFFER_BIT = 0x40;


    // glLight
    public static final int GL_LIGHTING = 0xb50;
    public static final int GL_LIGHT0 = 0x4000, GL_LIGHT1 = 0x4001, GL_LIGHT2 = 0x4002;
    public static final int GL_SPOT_EXPONENT=0x1205, GL_SPOT_CUTOFF=0x1206,GL_CONSTANT_ATTENUATION=0x1207,GL_LINEAR_ATTENUATION=0x1208,GL_QUADRATIC_ATTENUATION=0x1209,GL_AMBIENT=0x1200,GL_DIFFUSE=0x1201,GL_SPECULAR=0x1202,GL_SHININESS=0x1601,GL_EMISSION=0x1600,GL_POSITION=0x1203,GL_SPOT_DIRECTION=0x1204,GL_AMBIENT_AND_DIFFUSE=0x1602,GL_COLOR_INDEXES=0x1603,GL_LIGHT_MODEL_TWO_SIDE=0xb52,GL_LIGHT_MODEL_LOCAL_VIEWER=0xb51,GL_LIGHT_MODEL_AMBIENT=0x0b53,GL_FRONT_AND_BACK=0x0408,GL_SHADE_MODEL=0x0b54,GL_FLAT=0x1d00,GL_SMOOTH=0x1d01,GL_COLOR_MATERIAL=0x0b57,GL_COLOR_MATERIAL_FACE=0xB55,GL_COLOR_MATERIAL_PARAMETER=0x0b56,GL_NORMALIZE=0x0ba1;

    // depth buffer
    public static final int GL_DEPTH_TEST=0xb71, GL_LEQUAL=0x0203;
    public static final int GL_ENABLE_BIT = 0x2000;

    public static final int GL_SCISSOR_TEST = 0x0c11;

    // blending
    public static final int GL_BLEND=0x0be2, GL_SRC_ALPHA=0x0302, GL_ONE_MINUS_SRC_ALPHA=0x303, GL_ZERO = 0, GL_ONE=1;

    // polygon mode
    public static final int GL_POINT = 0x1b00, GL_LINE = 0x1b01, GL_FILL = 0x1b02, GL_FRONT=0x0404, GL_BACK=0x0405;

    public static final int GL_POLYGON_OFFSET_FILL = 0x8037, GL_POLYGON_OFFSET_LINE = 0x2a02;

    public static final int GL_POINT_SMOOTH = 0x0b10;

    // hints
    public static final int GL_PERSPECTIVE_CORRECTION_HINT = 0x0c50, GL_POINT_SMOOTH_HINT = 0x0c51, GL_LINE_SMOOTH_HINT = 0xc52, GL_POLYGON_SMOOTH_HINT=0xc53, GL_FOG_HINT=0xc54, GL_POLYGON_SMOOTH = 0x0b41;
    public static final int GL_DONT_CARE=0x1100, GL_FASTEST=0x1101, GL_NICEST=0x1102;

    public static final int GL_LINE_SMOOTH = 0x0b20, GL_LINE_STIPPLE = 0x0b24;

    public static final int GL_CULL_FACE = 0x0b44, GL_CULL_FACE_MODE = 0x0b45;

    public static final int GL_POINTS = 0x0000, GL_LINES = 0x0001, GL_LINE_LOOP = 0x0002, GL_LINE_STRIP = 0x0003, GL_TRIANGLES = 0x0004, GL_TRIANGLE_STRIP = 0x0005, GL_TRIANGLE_FAN = 0x0006, GL_QUADS=0x0007, GL_QUAD_STRIP = 0x0008, GL_POLYGON = 0x0009;

    public static final int GL_RGBA8 = 0x8058, GL_ALPHA8=0x803c, GL_LUMINANCE8 = 0x8040, GL_BGRA = 0x80e1, GL_UNSIGNED_INT_8_8_8_8_REV = 0x8367, GL_UNSIGNED_INT_8_8_8_8 = 0x8035, GL_ABGR_EXT = 0x8000, GL_RGB8=0x8051, GL_LUMINANCE = 0x1909, GL_ALPHA = 0x1906, GL_UNSIGNED_BYTE = 0x1401;

    public static final int GL_ALPHA_TEST = 0x0BC0;
    public static final int GL_GREATER = 0x0204, GL_GEQUAL = 0x0206, GL_ALWAYS = 0x0207;

    public static final int TEX_FLAG_MIN_FILTER = 1, TEX_FLAG_MAG_FILTER = 2, TEX_FLAG_REPEAT = 4;

    public static final int GL_MODELVIEW_MATRIX = 0x0ba6, GL_PROJECTION_MATRIX = 0x0ba7;

    /** While "public", this method should only be called once and
     * only called on the thread that will do all subsequent GL
     * rendering. With vis, it is called by the GLManager.
     **/
    public static void initialize()
    {
        System.loadLibrary("jgl");

        gl_initialize();
    }

    HashMap<Integer, int[]> frameBufferSizes = new HashMap<Integer, int[]>();
    int currentFrameBufferId;

    public GL()
    {
    }

    public int frameBufferCreate(int width, int height)
    {
        int fboId = gl_fbo_create(width, height);
        frameBufferSizes.put(fboId, new int[] { width, height });

        return fboId;
    }

    public void frameBufferBind(int fboId)
    {
        currentFrameBufferId = fboId;
        gl_fbo_bind(fboId);
    }

    public void frameBufferDestroy(int fboId)
    {
        gl_fbo_destroy(fboId);
        frameBufferSizes.remove(fboId);
    }

    public int frameBufferWidth(int fboId)
    {
        return frameBufferSizes.get(fboId)[0];
    }

    public int frameBufferHeight(int fboId)
    {
        return frameBufferSizes.get(fboId)[1];
    }

    public BufferedImage getImage(BufferedImage im)
    {
        flush();

        int wh[] = frameBufferSizes.get(currentFrameBufferId);

        boolean use3byte = true; // false does not work

        if (im == null || im.getWidth() != wh[0] || im.getHeight() != wh[1]) {
            if (use3byte)
                im = new BufferedImage(wh[0], wh[1], BufferedImage.TYPE_3BYTE_BGR);
            else
                im = new BufferedImage(wh[0], wh[1], BufferedImage.TYPE_INT_RGB);
        }

        if (im.getType() == BufferedImage.TYPE_3BYTE_BGR) {

            byte data[] = ((DataBufferByte) (im.getRaster().getDataBuffer())).getData();
            gl_read_pixels2(wh[0], wh[1], data);

        } else if (im.getType() == BufferedImage.TYPE_INT_RGB) {
            // this code path does NOT work properly.
            int data[] = ((DataBufferInt) (im.getRaster().getDataBuffer())).getData();
            gl_read_pixels2(wh[0], wh[1], data);
        }

        return im;
    }

    public void flush()
    {
        if (cmd.size() == 0)
            return;

        gl_ops(cmd.getData(), cmd.size());

        cmd.clear();
    }

    public void glTranslated(double x, double y, double z)
    {
        cmd.add(OP_TRANSLATED);
        cmd.add(x);
        cmd.add(y);
        cmd.add(z);
    }

    public void glScaled(double x, double y, double z)
    {
        cmd.add(OP_SCALED);
        cmd.add(x);
        cmd.add(y);
        cmd.add(z);
    }

    public void glRotated(double angle, double x, double y, double z)
    {
        cmd.add(OP_ROTATED);
        cmd.add(angle);
        cmd.add(x);
        cmd.add(y);
        cmd.add(z);
    }

    /** Assumes that an ELEMENT_ARRAY has been bound.
     * @param start  The minimum vertex index referenced
     * @param end  The maximum vertex index referenced
     * @param count The number of vertices to render
     * @param indexOffsetBytes The offset in the index buffer object to fetch indices from
    **/
    public void glDrawRangeElements(int mode, int start, int end, int count, int indexOffsetBytes)
    {
        cmd.add(OP_DRAWRANGEELEMENTS);
        cmd.add(mode);
        cmd.add(start);
        cmd.add(end);
        cmd.add(count);
        cmd.add(indexOffsetBytes);
    }

    public void glPushMatrix()
    {
        cmd.add(OP_PUSHMATRIX);
    }

    public void glPopMatrix()
    {
        cmd.add(OP_POPMATRIX);
    }

    public void glMultMatrix(double m[][])
    {
        cmd.add(OP_MULTMATRIXD);
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                cmd.add(m[j][i]);
    }

    public void glMultMatrixd(double m[])
    {
        cmd.add(OP_MULTMATRIXD);
        for (int i = 0; i < 16; i++)
            cmd.add(m[i]);
    }

    public void glLoadIdentity()
    {
        cmd.add(OP_LOADIDENTITY);
    }

    public void glMatrixMode(int mode)
    {
        cmd.add(OP_MATRIXMODE);
        cmd.add(mode);
    }

    public void glEnable(int cap)
    {
        cmd.add(OP_ENABLE);
        cmd.add(cap);
    }

    public void glDisable(int cap)
    {
        cmd.add(OP_DISABLE);
        cmd.add(cap);
    }

    public void glClear(int mask)
    {
        cmd.add(OP_CLEAR);
        cmd.add(mask);
    }

    public void glColor(Color c)
    {
        glColor4ub(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    }

    public void glColor4ub(int r, int g, int b, int alpha)
    {
        cmd.add(OP_COLOR);
        long rgba = (r<<24) + (g<<16) + (b<<8) + alpha;
        cmd.add((double) rgba);
    }

    public void glClearColor(double r, double g, double b, double a)
    {
        cmd.add(OP_CLEARCOLOR);
        cmd.add(r);
        cmd.add(g);
        cmd.add(b);
        cmd.add(a);
    }

    public void glClearDepth(double v)
    {
        cmd.add(OP_CLEARDEPTH);
        cmd.add(v);
    }

    public void glScissor(int x, int y, int width, int height)
    {
        cmd.add(OP_SCISSOR);
        cmd.add(x);
        cmd.add(y);
        cmd.add(width);
        cmd.add(height);
    }

    public void glLightfv(int light, int name, float params[])
    {
        cmd.add(OP_LIGHTFV);
        cmd.add(light);
        cmd.add(name);
        cmd.add(params.length);
        for (int i = 0; i < params.length; i++)
            cmd.add(params[i]);
    }

    public void glLightModeli(int name, int val)
    {
        cmd.add(OP_LIGHTMODELI);
        cmd.add(name);
        cmd.add(val);
    }

    public void glColorMaterial(int face, int mode)
    {
        cmd.add(OP_COLORMATERIAL);
        cmd.add(face);
        cmd.add(mode);
    }

    public void glMaterialf(int face, int name, float val)
    {
        cmd.add(OP_MATERIALF);
        cmd.add(face);
        cmd.add(name);
        cmd.add(val);
    }

    public void glMaterialfv(int face, int name, float params[])
    {
        cmd.add(OP_MATERIALFV);
        cmd.add(face);
        cmd.add(name);
        cmd.add(params.length);
        for (int i = 0; i < params.length; i++)
            cmd.add(params[i]);
    }

    public void glDepthFunc(int n)
    {
        cmd.add(OP_DEPTHFUNC);
        cmd.add(n);
    }

    public void glBlendFunc(int sfactor, int dfactor)
    {
        cmd.add(OP_BLENDFUNC);
        cmd.add(sfactor);
        cmd.add(dfactor);
    }

    public void glPolygonMode(int face, int mode)
    {
        cmd.add(OP_POLYGONMODE);
        cmd.add(face);
        cmd.add(mode);
    }

    public void glHint(int target, int mode)
    {
        cmd.add(OP_HINT);
        cmd.add(target);
        cmd.add(mode);
    }

    public void glShadeModel(int mode)
    {
        cmd.add(OP_SHADEMODEL);
        cmd.add(mode);
    }

    public void glPolygonOffset(float factor, float units)
    {
        cmd.add(OP_POLYGONOFFSET);
        cmd.add(factor);
        cmd.add(units);
    }

    public void glBegin(int mode)
    {
        cmd.add(OP_BEGIN);
        cmd.add(mode);
    }

    public void glEnd()
    {
        cmd.add(OP_END);
    }

    public void glNormal3f(float x, float y, float z)
    {
        cmd.add(OP_NORMAL3F);
        cmd.add(x);
        cmd.add(y);
        cmd.add(z);
    }

    public void glVertex2d(double x, double y)
    {
        cmd.add(OP_VERTEX2D);
        cmd.add(x);
        cmd.add(y);
    }

    public void glVertex3d(double x, double y, double z)
    {
        cmd.add(OP_VERTEX3D);
        cmd.add(x);
        cmd.add(y);
        cmd.add(z);
    }

    public int glGetError()
    {
        flush();
        return gl_get_error();
    }

    public double[][] getModelViewMatrix()
    {
        double v[] = new double[16];
        glGetDoublev(GL_MODELVIEW_MATRIX, v);

        double m[][] = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m[j][i] = v[i*4+j];
            }
        }

        return m;
    }

    public double[][] getProjectionMatrix()
    {
        double v[] = new double[16];
        glGetDoublev(GL_MODELVIEW_MATRIX, v);

        double m[][] = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m[j][i] = v[i*4+j];
            }
        }

        return m;
    }

    public int glGetDoublev(int param, double data[])
    {
        flush();

        return gl_get_double_v(param, data);
    }

    public void glViewport(int x, int y, int width, int height)
    {
        cmd.add(OP_VIEWPORT);
        cmd.add(x);
        cmd.add(y);
        cmd.add(width);
        cmd.add(height);
    }

    public void glDrawArrays(int mode, int offset, int count)
    {
        cmd.add(OP_DRAWARRAYS);
        cmd.add(mode);
        cmd.add(offset);
        cmd.add(count);
    }

    public void glLineWidth(double width)
    {
        cmd.add(OP_LINEWIDTH);
        cmd.add(width);
    }

    public void glPointSize(double sz)
    {
        cmd.add(OP_POINTSIZE);
        cmd.add(sz);
    }

    public void glPushAttrib(long v)
    {
        cmd.add(OP_PUSHATTRIB);
        cmd.add((double) v);
    }

    public void glPopAttrib()
    {
        cmd.add(OP_POPATTRIB);
    }

    public void glTexCoord2d(double x, double y)
    {
        cmd.add(OP_TEXCOORD2D);
        cmd.add(x);
        cmd.add(y);
    }

    public void glAlphaFunc(int func, double r)
    {
        cmd.add(OP_ALPHA_FUNC);
        cmd.add(func);
        cmd.add(r);
    }

    //////////////////////////////////////////
    // Functions below manage our C-side VBO cache

    /** Signal to the garbage collector that we're beginning to draw a
     * new frame. This is used to setup a new "mark" pass for the
     * GC. This should only be used by VisCanvas!
     **/
    public void gldFrameBegin(long canvasId)
    {
        flush();
        gldata_begin_frame(canvasId);
    }

    /** Call at conclusion of a frame. This will cause garbage from
     * earlier versions of this canvas to be collected. **/
    public void gldFrameEnd(long canvasId)
    {
        flush();

        // last parameter isn't implemented.
        gldata_end_frame_and_collect(canvasId, 0);
    }

    public void gldBind(int vbo_type, long id, int nverts, int vertdim, float data[])
    {
        flush();
        gldata_bind(vbo_type, id, nverts, vertdim, data);
    }

    public void gldBind(int vbo_type, long id, int nverts, int vertdim, double data[])
    {
        flush();
        gldata_bind(vbo_type, id, nverts, vertdim, data);
    }

    public void gldBind(int vbo_type, long id, int nverts, int vertdim, int data[])
    {
        flush();
        gldata_bind(vbo_type, id, nverts, vertdim, data);
    }

    public void gldBind(int vbo_type, long id, int nverts, int vertdim, short data[])
    {
        flush();
        gldata_bind(vbo_type, id, nverts, vertdim, data);
    }

    public void gldUnbind(int vbo_type, long id)
    {
        flush();
        gldata_unbind(vbo_type, id);
    }

    public void gldBindTexture(long id, int internalfmt, int width, int height, int fmt, int type, int data[], int flags)
    {
        flush();
        gldata_tex_bind(id, internalfmt, width, height, fmt, type, data, flags);
    }

    public void gldBindTexture(long id, int internalfmt, int width, int height, int fmt, int type, byte data[], int flags)
    {
        flush();
        gldata_tex_bind(id, internalfmt, width, height, fmt, type, data, flags);
    }

    public void gldUnbindTexture(long id)
    {
        flush();
        gldata_tex_unbind(id);
    }


}
