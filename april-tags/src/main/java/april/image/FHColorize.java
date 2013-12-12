package april.image;

import java.awt.image.*;

import april.vis.ColorUtil;
import april.jcam.ImageConvert;

public class FHColorize
{
    public static BufferedImage SeededRandom(BufferedImage source, BufferedImage _reps)
    {
        int reps[] = ((DataBufferInt) (_reps.getRaster().getDataBuffer())).getData();
        return SeededRandom(source, reps);
    }

    public static BufferedImage SeededRandom(BufferedImage source, int reps[])
    {
        assert(source.getType() == BufferedImage.TYPE_INT_RGB);

        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        int _out[] = ((DataBufferInt) (out.getRaster().getDataBuffer())).getData();

        mapSeededRandom(reps, _out);

        return out;
    }

    public static BufferedImage RGBAverage(BufferedImage source, BufferedImage _reps)
    {
        int reps[] = ((DataBufferInt) (_reps.getRaster().getDataBuffer())).getData();
        return RGBAverage(source, reps);
    }

    public static BufferedImage RGBAverage(BufferedImage source, int reps[])
    {
        assert(source.getType() == BufferedImage.TYPE_INT_RGB);

        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        int _out[] = ((DataBufferInt) (out.getRaster().getDataBuffer())).getData();

        mapRGBAverage(source, reps, _out);

        return out;
    }

    public static BufferedImage WeightedHueAverage(BufferedImage source, BufferedImage _reps)
    {
        int reps[] = ((DataBufferInt) (_reps.getRaster().getDataBuffer())).getData();
        return WeightedHueAverage(source, reps);
    }

    public static BufferedImage WeightedHueAverage(BufferedImage source, int reps[])
    {
        assert(source.getType() == BufferedImage.TYPE_INT_RGB);

        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        int _out[] = ((DataBufferInt) (out.getRaster().getDataBuffer())).getData();

        mapWeightedHueAverage(source, reps, _out);

        return out;

    }

    public static void mapSeededRandom(int reps[], int out[])
    {
        // XXX why was this y+1 again?
        for (int i=0; i < reps.length; i++)
            out[i] = ColorUtil.seededColor(reps[i]).getRGB();
    }

    public static void mapRGBAverage(BufferedImage source, int reps[], int out[])
    {
        int width = source.getWidth();
        int height = source.getHeight();
        int rgbsums[] = new int[width * height * 3];
        int sizes[] = new int[width * height];

        int src[] = ((DataBufferInt) (source.getRaster().getDataBuffer())).getData();

        for (int i=0; i < width*height; i++) {
            int rep = reps[i];

            int rgb = src[i];
            rgbsums[3*rep + 0] += ((rgb >> 16) & 0xFF);
            rgbsums[3*rep + 1] += ((rgb >> 8) & 0xFF);
            rgbsums[3*rep + 2] += ((rgb) & 0xFF);

            sizes[rep]++;
        }

        for (int i=0; i < width*height; i++) {
            int rep  = reps[i];
            int size = sizes[rep];

            int r = (int) Math.min(((double) rgbsums[3*rep+0]) / size, 255);
            int g = (int) Math.min(((double) rgbsums[3*rep+1]) / size, 255);
            int b = (int) Math.min(((double) rgbsums[3*rep+2]) / size, 255);

            out[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    public static void mapWeightedHueAverage(BufferedImage source, int reps[], int out[])
    {
        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage _hsv = ImageConvert.RGBtoHSV(source);
        int hsv[] = ((DataBufferInt) (_hsv.getRaster().getDataBuffer())).getData();

        double Ms[] = new double[width * height];
        double Ns[] = new double[width * height];

        for (int i=0; i < width*height; i++) {
            int rep = reps[i];

            int h = (hsv[i] >> 16) & 0xFF;
            int s = (hsv[i] >> 8) & 0xFF;
            int v = (hsv[i]) & 0xFF;

            // scale from byte to radians
            double hue = Math.toRadians(h * 360.0 / 255.0);

            // conservative hue averaging
            Ms[rep] += Math.sin(hue);
            Ns[rep] += Math.cos(hue);
        }

        for (int i=0; i < width*height; i++) {
            int rep = reps[i];

            // theta valid from 0 to 2*Pi
            double theta = Math.atan2(Ms[rep], Ns[rep]);
            if (theta < 0)
                theta += 2*Math.PI;

            // hue valid from 0 to 360 degrees
            int hue = (int) Math.toDegrees(theta);

            // scale hue from degrees to a byte
            int h = ((hue * 255) / 360) & 0xFF;
            out[i] = 0xFF000000 | h << 16 | 255 << 8 | 255;
        }

        ImageConvert.HSVtoRGB(out);
    }
}
