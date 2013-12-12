package april.util;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import java.io.*;

import april.image.*;
import april.jmat.*;

/** Image utility functions. **/
public class ImageUtil
{
    public static boolean warn = true;

    /**
     * Ensure an image is of the right format, converting it to the
     * format if necessary.
     * @param in The input image, in any format.
     * @param type The desired type, e.g. BufferedImage.TYPE_3BYTE_BGR
     * @return An image with analagous content as in, but of the
     * requested type. Or, if the input image was already in the
     * requested format, the input image is returned.
     **/
    public static BufferedImage convertImage(BufferedImage in, int type)
    {
        if (in.getType()==type)
            return in;

        if (warn) {
            System.out.println("ImageUtil: Performing slow image type conversion");
            warn = false;
        }

        int w = in.getWidth();
        int h = in.getHeight();

        BufferedImage out=new BufferedImage(w,h,type);
        Graphics g = out.getGraphics();

        g.drawImage(in, 0, 0, null);

        g.dispose();

        return out;
    }

    public static BufferedImage conformImageToInt(BufferedImage in)
    {
        return convertImage(in, BufferedImage.TYPE_INT_RGB);
    }

    public static BufferedImage scale(BufferedImage in, double scale)
    {
        return scale(in, (int) (scale*in.getWidth()), (int) (scale*in.getHeight()));
    }

    public static BufferedImage scale(BufferedImage in, int newwidth, int newheight)
    {
        BufferedImage out = new BufferedImage(newwidth, newheight, in.getType());
        Graphics2D g = out.createGraphics();

        final Object interp = (newwidth < in.getWidth() && newheight < in.getHeight()) ?
            RenderingHints.VALUE_INTERPOLATION_BILINEAR :
            RenderingHints.VALUE_INTERPOLATION_BICUBIC;

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interp);
        g.drawImage(in, 0, 0, out.getWidth(), out.getHeight(), null);
        g.dispose();
        return out;
    }

    public static BufferedImage scale(BufferedImage in, int newwidth)
    {
        int newheight = in.getHeight()*newwidth / in.getWidth();

        return scale(in, newwidth, newheight);
    }

    public static BufferedImage smooth(BufferedImage in, double sigma, int kernelsize)
    {
        float k[] = SigProc.makeGaussianFilter(sigma, kernelsize);

        FloatImage fr = new FloatImage(in, 16);
        FloatImage fg = new FloatImage(in, 8);
        FloatImage fb = new FloatImage(in, 0);

        fr = fr.filterFactoredCentered(k, k);
        fg = fg.filterFactoredCentered(k, k);
        fb = fb.filterFactoredCentered(k, k);

        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < in.getHeight(); y++) {
            for (int x = 0; x < in.getWidth(); x++) {
                int r = (int) (255.0*fr.get(x, y) + .5);
                int g = (int) (255.0*fg.get(x, y) + .5);
                int b = (int) (255.0*fb.get(x, y) + .5);
                r = LinAlg.clamp(r, 0, 255);
                g = LinAlg.clamp(g, 0, 255);
                b = LinAlg.clamp(b, 0, 255);

                out.setRGB(x, y, (r<<16)|(g<<8)|b);
            }
        }

        return out;
    }

    public static BufferedImage flipVertical(BufferedImage im)
    {
        int height = im.getHeight();
        int width = im.getWidth();

        DataBuffer db = im.getRaster().getDataBuffer();
        if (db instanceof DataBufferInt) {
            int imdata[] = ((DataBufferInt) db).getData();

            BufferedImage im2 = new BufferedImage(width, height, im.getType());
            int imdata2[] = ((DataBufferInt) (im2.getRaster().getDataBuffer())).getData();

            int stride = width;
            for (int y = 0; y < height; y++)
                System.arraycopy(imdata, y*stride, imdata2, (height-1-y)*stride, stride);

            return im2;
        }

        if (db instanceof DataBufferByte) {
            byte imdata[] = ((DataBufferByte) db).getData();

            BufferedImage im2 = new BufferedImage(width, height, im.getType());
            byte imdata2[] = ((DataBufferByte) (im2.getRaster().getDataBuffer())).getData();

            int bytes_per_pixel = 0;
            switch(im.getType()) {
                case BufferedImage.TYPE_4BYTE_ABGR:
                case BufferedImage.TYPE_4BYTE_ABGR_PRE:
                    bytes_per_pixel = 4;
                    break;
                case BufferedImage.TYPE_3BYTE_BGR:
                    bytes_per_pixel = 3;
                    break;
                case BufferedImage.TYPE_BYTE_GRAY:
                    bytes_per_pixel = 1;
                    break;
                case BufferedImage.TYPE_BYTE_BINARY:
                case BufferedImage.TYPE_BYTE_INDEXED:
                    return null; //XXX These types are not supported by this method
            }
            int stride = bytes_per_pixel * width;
            for (int y = 0; y < height; y++)
                System.arraycopy(imdata, y*stride, imdata2, (height-1-y)*stride, stride);

            return im2;
        }

        assert(false);
        return null;
    }

}
