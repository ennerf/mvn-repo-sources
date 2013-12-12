package april.vis;

import java.awt.Color;
import java.io.*;
import java.util.List;
import lcm.lcm.*;

// for legend
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.*;


/** Converts scalar values to RGB colors by interpolating from a
 *  user-provided look-up table.
 *  Creates a VisColorData VBO by taking a set of data, and an index in that data to map:
 *     ColorMapper.makeJet(ymin,ymax).makeColorData(data, 1);
 */
public class ColorMapper implements VisSerializable
{
    /** Minimum/maximum value for mapped range (will be drawn opaquely). **/
    double minval;
    double maxval;
    int[] colors;

    /** If these bounds are exceeded, use transparent color **/
    double opaqueMax = Double.MAX_VALUE, opaqueMin=-Double.MAX_VALUE;

    public ColorMapper(int[] colors, double minval, double maxval)
    {
        this.colors=colors;
        this.minval=minval;
        this.maxval=maxval;
    }

    private ColorMapper()
    {
    }

    public void setMinMax(double minval, double maxval)
    {
        this.minval = minval;
        this.maxval = maxval;
    }

    public double[] getMinMax()
    {
        return new double[]{minval,maxval};
    }

    public void setOpaqueMax(double opaqueMax)
    {
        this.opaqueMax = opaqueMax;
    }

    public void setOpaqueMin(double opaqueMin)
    {
        this.opaqueMin = opaqueMin;
    }

    public int[] getColors()
    {
        return colors;
    }

    public boolean isVisible(double v)
    {
        if (v > opaqueMax || v < opaqueMin)
            return false;
        return true;
    }

    // Returns a new colormapper which swaps R and B channels This is
    // useful for plotting VBOs in openGL, which use BGR by default
    public ColorMapper swapRedBlue()
    {
        ColorMapper other = new ColorMapper();
        other.colors = new int[this.colors.length];
        for (int i = 0; i < this.colors.length; i++)
            other.colors[i] = ColorUtil.swapRedBlue(this.colors[i]);

        other.minval = this.minval;
        other.maxval = this.maxval;
        other.opaqueMin = this.opaqueMin;
        other.opaqueMax = this.opaqueMax;
        return other;
    }

    public VisColorData makeColorData(List<double[]> data, int color_index)
    {
        int cols[] = new int[data.size()];

        int idx = 0;
        for (double[] d: data)
            cols[idx++]=map(d[color_index]);
        return new VisColorData(cols);
    }

    public static ColorMapper makeGray(double min, double max)
    {
        return new ColorMapper(new int[] {0x000000,
                                          0xffffff},
            min,
            max);
    }

    public static ColorMapper makeJet(double min, double max)
    {

        return new ColorMapper(new int[] {0x000000,
                                          0x0000ff,
                                          0x008080,
                                          0x00ff00,
                                          0x808000,
                                          0xff0000},
            min,
            max);
    }

    public static ColorMapper makeJetWhite(double min, double max)
    {

        return new ColorMapper(new int[] {0xffffff,
                                          0x0000ff,
                                          0x008080,
                                          0x00ff00,
                                          0x808000,
                                          0xff0000,
                                          0xffff00},
            min,
            max);
    }

    public static ColorMapper makeDivergent(double min, double max)
    {
        /* http://geography.uoregon.edu/datagraphics/color_scales.htm
         * ColorMap: blue to dark-red 18 steps */
        return new ColorMapper(new int[] {
                new Color( 36,   0, 216).getRGB(),
                new Color( 24,  28, 247).getRGB(),
                new Color( 40,  87, 255).getRGB(),
                new Color( 61, 135, 255).getRGB(),
                new Color( 86, 176, 255).getRGB(),
                new Color(117, 211, 255).getRGB(),
                new Color(153, 234, 255).getRGB(),
                new Color(188, 249, 255).getRGB(),
                new Color(234, 255, 255).getRGB(),
                new Color(255, 255, 234).getRGB(),
                new Color(255, 241, 188).getRGB(),
                new Color(255, 214, 153).getRGB(),
                new Color(255, 172, 117).getRGB(),
                new Color(255, 120,  86).getRGB(),
                new Color(255,  61,  61).getRGB(),
                new Color(247,  39,  53).getRGB(),
                new Color(216,  21,  47).getRGB(),
                new Color(165,   0,  33).getRGB()}, min, max);

    }

    public static ColorMapper makeDivergentPinkGreen(double min, double max)
    {
        /* http://geography.uoregon.edu/datagraphics/color_scales.htm
         * ColorMap: blue to dark-red 18 steps */
        return new ColorMapper(new int[] {
                new Color(142,   1,  82).getRGB(),
                new Color(197,  27, 125).getRGB(),
                new Color(222, 119, 174).getRGB(),
                new Color(241, 182, 218).getRGB(),
                new Color(253, 224, 239).getRGB(),
                new Color(247, 247, 247).getRGB(),
                new Color(230, 245, 208).getRGB(),
                new Color(184, 225, 134).getRGB(),
                new Color(127, 188,  65).getRGB(),
                new Color( 77, 146,  33).getRGB(),
                new Color( 39, 100,  25).getRGB()}, min, max);
    }

    public Color mapColor(double vin)
    {
        int v = map(vin);
        return new Color((v>>16)&0xff, (v>>8)&0xff, (v>>0)&0xff, (v>>24)&0xff);
    }

    public int map(double v)
    {
        if (!isVisible(v))
            return 0x00000000; // transparent

        double normval = (colors.length-1)*(v-minval)/(maxval-minval);

        int a = (int) (normval);
        if (a<0)
            a=0;
        if (a>=colors.length)
            a=colors.length-1;

        int b = a + 1;
        if (b>=colors.length)
            b=colors.length-1;

        double frac = normval - a;
        if (frac<0)
            frac=0;
        if (frac>1)
            frac=1;

        int c=0;
        for (int i=0;i<4;i++)
	    {
            int r =i*8;
            int comp = (int) (((colors[a]>>r)&0xff)*(1-frac) + ((colors[b]>>r)&0xff)*frac);
            comp = comp & 0xff;
            c |= (comp<<r);
	    }

        // force opacity
        return c | 0xff000000;
    }

    // Returns an image with text labels
    // e.g. makeLegend(70, 480, 0.35, "%5.1f", new Font("Monospaced", Font.PLAIN, 12));
    public BufferedImage makeLegend(int width, int height, double barFraction, boolean light,
                                    String format, Font font)
    {
        BufferedImage legend = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int legenddata[] = ((DataBufferInt) (legend.getRaster().getDataBuffer())).getData();

        Graphics2D g = legend.createGraphics();
        if (light) g.setColor(Color.white);
        else       g.setColor(Color.black);
        g.fillRect(0, 0, width, height);

        int barwidth     = (int) (width * barFraction);
        int barheight    = height;

        for (int y = 0; y < barheight; y++) {
            for (int x = 0; x < barwidth; x++) {
                double val        = ((double) (barheight - y) * (maxval - minval)) / barheight + minval;
                legenddata[y*width + x] = map(val);
            }
        }

        if (light) g.setColor(Color.black);
        else       g.setColor(Color.white);
        g.setFont(font);

        int fontsize = font.getSize();

        int x = (int) (width * (barFraction + 0.1));
        for (int i = 0; i < colors.length; i++) {

            double percent = ((double) (i)) / (colors.length - 1);

            int y = (int) (percent * barheight);
            if (i == 0)               y += fontsize/2;
            if (i+1 == colors.length) y -= fontsize*3/2;
            y = barheight - y;

            String s = String.format(format, minval + percent * (maxval - minval));
            g.drawString(s, x, y);
        }

        return legend;
    }

    // Serialization
    public ColorMapper(ObjectReader none)
    {
    }

    public void writeObject(ObjectWriter out) throws IOException
    {
        out.writeDouble(minval);
        out.writeDouble(maxval);
        out.writeDouble(opaqueMin);
        out.writeDouble(opaqueMax);

        out.writeInt(colors.length);
        for (int c : colors)
            out.writeInt(c);

    }

    public void readObject(ObjectReader in) throws IOException
    {
        minval = in.readDouble();
        maxval = in.readDouble();
        opaqueMin = in.readDouble();
        opaqueMax = in.readDouble();


        colors = new int[in.readInt()];
        for( int i =0; i < colors.length; i++)
            colors[i] = in.readInt();
    }

}
