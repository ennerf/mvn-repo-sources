package april.vis;

import java.awt.*;
import java.awt.image.*;
import java.io.*;

import april.jmat.*;

/**
    In the interest of simplicity, we only attempt to support systems with
    GL_ARB_texture_non_power_of_two. **/
public class VisTexture implements VisSerializable
{
    long id = VisUtil.allocateID();

    int glinternal, glformat, gltype;
    int width, height;
    int bytes_per_pixel;

    static boolean warnedSlowConversion[] = {false,false};

    boolean alphaMask;

    int idata[];
    byte bdata[];

    boolean minFilter = true;
    boolean magFilter = true;
    boolean repeat = false;

    public static final int
        NO_MIN_FILTER = 1,  MIN_FILTER = 2,
        NO_MAG_FILTER = 4,  MAG_FILTER = 8,
        NO_REPEAT     = 16, REPEAT = 32,
        NO_ALPHA_MASK = 64, ALPHA_MASK = 128;

    /** You may not subsequently modify im or behavior is undefined. **/
    public VisTexture(BufferedImage input)
    {
        this(input, 0);
    }

    public VisTexture(BufferedImage input, int flags)
    {
        setFlags(flags);

        BufferedImage im = null;

        this.width = input.getWidth();
        this.height = input.getHeight();

        if (alphaMask) {
            im = VisUtil.coerceImage(input, BufferedImage.TYPE_BYTE_GRAY);
            glinternal = GL.GL_ALPHA8;
            glformat = GL.GL_ALPHA;
            gltype = GL.GL_UNSIGNED_BYTE;
            bytes_per_pixel = 1;

        } else {

            int imtype = input.getType();

            if (imtype == BufferedImage.TYPE_INT_ARGB) {
                im = input;
                glinternal = GL.GL_RGBA8;
                glformat = GL.GL_BGRA;
                gltype = GL.GL_UNSIGNED_INT_8_8_8_8_REV;
                bytes_per_pixel = 4;
            } else if (imtype ==  BufferedImage.TYPE_BYTE_GRAY && width % 4 == 0) {
                im = input;
                glinternal = GL.GL_LUMINANCE8;
                glformat = GL.GL_LUMINANCE;
                gltype = GL.GL_UNSIGNED_BYTE;
                bytes_per_pixel = 1;
/* GL_ABGR_EXT isn't portable (fails on ed's ATI hardware.) Let this fall through to default.
            } else if (imtype ==  BufferedImage.TYPE_4BYTE_ABGR) {
                im = input;
                glinternal = GL.GL_RGBA8;
                glformat = GL.GL_ABGR_EXT;
                gltype = GL.GL_UNSIGNED_INT_8_8_8_8_REV;
                bytes_per_pixel = 4;
*/
            } else if (imtype ==  BufferedImage.TYPE_INT_RGB) {
                im = input;
                glinternal = GL.GL_RGB8;
                glformat = GL.GL_BGRA;
                gltype = GL.GL_UNSIGNED_INT_8_8_8_8_REV;
                bytes_per_pixel = 4;
            } else {
                if (imtype == BufferedImage.TYPE_BYTE_GRAY && !warnedSlowConversion[0]) {
                    System.out.println("Warning(once): VisTexture using slow image conversion due to bad stride in BYTE_GRAY image");
                    warnedSlowConversion[0] = true;
                }

                if (imtype == BufferedImage.TYPE_4BYTE_ABGR && !warnedSlowConversion[1]) {
                    System.out.println("Warning(once): VisTexture using slow image conversion due to lacking ATI support for 4BYTE_ABGR");
                    warnedSlowConversion[1] = true;
                }


                // coerce texture format to a type we know.
                im = VisUtil.coerceImage(input, BufferedImage.TYPE_INT_ARGB);

                glinternal = GL.GL_RGBA8;
                glformat = GL.GL_BGRA;
                gltype = GL.GL_UNSIGNED_INT_8_8_8_8_REV;
                bytes_per_pixel = 4;
            }
        }


        if (im.getRaster().getDataBuffer() instanceof DataBufferInt)
            this.idata = ((DataBufferInt) (im.getRaster().getDataBuffer())).getData();
        else
            this.bdata = ((DataBufferByte) (im.getRaster().getDataBuffer())).getData();
    }

    public void bind(GL gl)
    {
        int flags = 0;

        if (minFilter)
            flags |= GL.TEX_FLAG_MIN_FILTER;

        if (magFilter)
            flags |= GL.TEX_FLAG_MAG_FILTER;

        if (repeat)
            flags |= GL.TEX_FLAG_REPEAT;

        if (idata != null)
            gl.gldBindTexture(id, glinternal, width, height, glformat, gltype, idata, flags);
        else
            gl.gldBindTexture(id, glinternal, width, height, glformat, gltype, bdata, flags);
    }

    public void unbind(GL gl)
    {
        gl.gldUnbindTexture(id);
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    private void setFlags(int flags)
    {
        if ((flags & NO_ALPHA_MASK) != 0)
            alphaMask = false;
        if ((flags & ALPHA_MASK) != 0)
            alphaMask = true;

        if ((flags & NO_MIN_FILTER) != 0)
            minFilter = false;
        if ((flags & MIN_FILTER) != 0)
            minFilter = true;

        if ((flags & NO_MAG_FILTER) != 0)
            magFilter = false;
        if ((flags & MAG_FILTER) != 0)
            magFilter = true;

        if ((flags & NO_REPEAT) != 0)
            repeat = false;
        if ((flags & REPEAT) != 0)
            repeat = true;
    }

    public VisTexture(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeInt(glinternal);
        outs.writeInt(glformat);
        outs.writeInt(gltype);

        outs.writeInt(width);
        outs.writeInt(height);

        int flags = ((alphaMask ? ALPHA_MASK  : NO_ALPHA_MASK) |
                     (repeat    ? REPEAT      : NO_REPEAT    ) |
                     (magFilter ? MAG_FILTER  : NO_MAG_FILTER) |
                     (minFilter ? MIN_FILTER  : NO_MIN_FILTER));

        outs.writeInt(flags);
        outs.writeInts(idata);
        outs.writeBytes(bdata);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        glinternal = ins.readInt();
        glformat = ins.readInt();
        gltype = ins.readInt();

        width = ins.readInt();
        height = ins.readInt();

        setFlags(ins.readInt());
        idata = ins.readInts();
        bdata = ins.readBytes();
    }
}
