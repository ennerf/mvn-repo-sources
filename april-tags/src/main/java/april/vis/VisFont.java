package april.vis;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.imageio.*;
import java.io.*;
import java.util.*;

/** Create a texture to support rendering of fonts. The fonts are
 * originally drawn using Java2D and are converted into a texture for
 * OpenGL.
 **/
public class VisFont
{
    VisTexture texture;
    Font f;
    FontMetrics fm;

    static final int ASCII_MIN = ' ', ASCII_MAX = '~';

    int tile_width, tile_height; // size of each letter's tile (in pixels)
    int ntiles; // how many tiles in total?
    int tile_dim; // how many tiles in each row?

    int width, height; // overall size of our texture

    // advance and width of each character in the font
    float advances[], widths[];

    // used to cache fonts (reduce texture usage)
    static ArrayList<VisFont> fonts = new ArrayList<VisFont>();

    /** Use the factory method VisFont.getFont(), which will recycle
     * VisFont objects.
     **/
    protected VisFont(Font f, boolean export)
    {
        this.f = f;
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics tmpg = tmp.createGraphics();
        fm = tmpg.getFontMetrics(f);

        Rectangle2D maxr = fm.getMaxCharBounds(tmpg);
        tile_width = (int) (maxr.getWidth() + 2);
        tile_height = (int) (maxr.getHeight() + 2);

        ntiles = ASCII_MAX - ASCII_MIN + 1;
        tile_dim = ((int) Math.sqrt(ntiles)) + 1; // how many rows and columns of tiles?

        width = tile_dim*tile_width;
        height = tile_dim*tile_height;

        width = width + (4 - (width&3));
        height = height + (4 - (height&3));

        BufferedImage tiles = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = tiles.createGraphics();

        g.setFont(f);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setColor(new Color(255,255,255,0));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(255,255,255,255));

        advances = new float[ntiles];
        widths = new float[ntiles];

        for (int idx = 0; idx < ntiles; idx++) {
            char c = (char) (idx + ASCII_MIN);
            String s = "" + c;

            Rectangle2D r = fm.getStringBounds(s, g);

            advances[idx] = fm.charWidth(c);
            widths[idx] = (float) r.getWidth();

            int tile_y = idx / tile_dim;
            int tile_x = idx - tile_y * tile_dim;

            g.drawString(s, tile_x*tile_width, tile_y*tile_height + fm.getMaxAscent());

            // XXX: our widths from above do not seem to be right. Let's just test
            // the actual render width of the font.
            int maxdx = 0;
            for (int dx = 0; dx < tile_width; dx++) {
                for (int dy = 0; dy < tile_height; dy++) {
                    int rgb = tiles.getRGB(tile_x*tile_width + dx, tile_y*tile_height + dy);
                    if ((rgb & 0xff) != 0)
                        maxdx = Math.max(maxdx, dx);
                }
            }
            widths[idx] = maxdx + 1; // not a fudge: width is dx + 1
        }

        texture = new VisTexture(tiles, VisTexture.ALPHA_MASK);

        g.dispose();
        tmpg.dispose();

        if (export) {
            try {
                String style = "";
                if ((f.getStyle() & Font.BOLD) > 0)
                    style += "B";
                if ((f.getStyle() & Font.ITALIC) > 0)
                    style += "I";

                String basename = f.getName()+"_"+style+"_"+f.getSize();
                basename = basename.replace(" ", "-").toLowerCase();

                ImageIO.write(tiles, "png", new File("/tmp/"+basename+".png"));

                if (false) {
                    BufferedWriter outs = new BufferedWriter(new FileWriter("/tmp/"+basename+".param"));
                    outs.write(String.format("ascii_min %d\n", ASCII_MIN));
                    outs.write(String.format("ascii_max %d\n", ASCII_MAX));
                    outs.write(String.format("tile_width %d\n", tile_width));
                    outs.write(String.format("tile_height %d\n", tile_height));
                    outs.write(String.format("tile_dim %d\n", tile_dim));
                    outs.write(String.format("height %d\n", height));
                    outs.write(String.format("width %d\n", width));
                    outs.write(String.format("widths "));
                    for (int i = 0; i < widths.length; i++)
                        outs.write(String.format("%d ", (int) widths[i]));
                    outs.write("\n");

                    outs.write(String.format("advances "));
                    for (int i = 0; i < advances.length; i++)
                        outs.write(String.format("%d ", (int) advances[i]));
                    outs.write("\n");

                    outs.close();
                }

                if (true) {
                    DataOutputStream outs = new DataOutputStream(new FileOutputStream("/tmp/"+basename+".bparam"));
                    outs.writeInt(ASCII_MIN);
                    outs.writeInt(ASCII_MAX);
                    outs.writeInt(tile_width);
                    outs.writeInt(tile_height);
                    outs.writeInt(tile_dim);
                    outs.writeInt(width);
                    outs.writeInt(height);
                    outs.writeInt(widths.length);
                    for (int i = 0; i < widths.length; i++)
                        outs.writeInt((int) (100*widths[i]));
                    outs.writeInt(advances.length);
                    for (int i = 0; i < advances.length; i++)
                        outs.writeInt((int) (100*advances[i]));
                    outs.close();
                }

            } catch (IOException ex) {
                assert(false);
            }
        }
    }

    public static VisFont getFont(Font f)
    {
        for (VisFont vf : fonts) {
            if (vf.f.getFontName().equals(f.getFontName()) &&
                vf.f.getStyle()==f.getStyle() &&
                vf.f.getSize2D()==f.getSize2D())

                return vf;
        }

        VisFont vf = new VisFont(f, false);
        fonts.add(vf);
        return vf;
    }

    /** You probably want VzText instead. **/
    public VisFont.Text makeText(String s, Color color)
    {
        return new Text(s, color);
    }

    class Text implements VisObject
    {
        float verts[];
        float texcoords[];

        long vertsID = VisUtil.allocateID();
        long texCoordsID = VisUtil.allocateID();

        Color color;

        double totalAdvance = 0, totalWidth = 0, totalHeight = 0;

        public Text(String s, Color color)
        {
            this.color = color;

            verts = new float[s.length() * 8];
            texcoords = new float[s.length() * 8];

            float xpos = 0;

            totalHeight = tile_height;

            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c < ASCII_MIN || c > ASCII_MAX)
                    c = ' ';

                int idx = c - ASCII_MIN;
                int tile_y = idx / tile_dim;
                int tile_x = idx - (tile_y*tile_dim);

                float cwidth = widths[idx];
                double advance = advances[idx];

                verts[8*i+0]     = xpos;
                verts[8*i+1]     = 0;
                texcoords[8*i+0] = tile_x*tile_width;
                texcoords[8*i+1] = (tile_y+1)*tile_height;

                verts[8*i+2]     = xpos + cwidth;
                verts[8*i+3]     = 0;
                texcoords[8*i+2] = tile_x*tile_width + cwidth;
                texcoords[8*i+3] = (tile_y+1)*tile_height;

                verts[8*i+4]     = xpos + cwidth;
                verts[8*i+5]     = tile_height;
                texcoords[8*i+4] = tile_x*tile_width + cwidth;
                texcoords[8*i+5] = (tile_y+0)*tile_height;

                verts[8*i+6]     = xpos;
                verts[8*i+7]     = tile_height;
                texcoords[8*i+6] = tile_x*tile_width;
                texcoords[8*i+7] = (tile_y+0)*tile_height;

                xpos += advance;
                totalAdvance += advance;

                if (i+1 == s.length())
                    totalWidth += Math.max(advance, cwidth);
                else
                    totalWidth += advance;
            }

            for (int i = 0; i < texcoords.length/2; i++) {
                texcoords[2*i+0] /= width;
                texcoords[2*i+1] /= height;
            }
        }

        public double getAdvance()
        {
            return totalAdvance;
        }

        public double getWidth()
        {
            return totalWidth;
        }

        // overall height, including ascent and descent.
        public double getHeight()
        {
            return totalHeight;
        }

        public double getDescent()
        {
            return fm.getMaxAscent();
        }

        public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
        {
            gl.glColor(color);
            gl.glNormal3f(0,0,1);

            VisFont.this.texture.bind(gl);
            gl.gldBind(GL.VBO_TYPE_VERTEX,    vertsID,     verts.length / 2,     2, verts);
            gl.gldBind(GL.VBO_TYPE_TEX_COORD, texCoordsID, texcoords.length / 2, 2, texcoords);

            gl.glDrawArrays(GL.GL_QUADS, 0, verts.length / 2);

            gl.gldUnbind(GL.VBO_TYPE_VERTEX,    vertsID);
            gl.gldUnbind(GL.VBO_TYPE_TEX_COORD, texCoordsID);

            VisFont.this.texture.unbind(gl);
        }
    }

    public static void main(String args[])
    {
        int sizes[] = new int[] { 128 };
        int styles[] = new int[] { Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC };

        for (int style : styles) {
            for (int size : sizes) {
                new VisFont(new Font("Serif", style, size), true);
                new VisFont(new Font("SansSerif", style, size), true);
                new VisFont(new Font("Monospaced", style, size), true);
            }
        }
    }
}
