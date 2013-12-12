package april.vis;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.nio.channels.*;
import java.nio.*;
import javax.swing.*;

import april.jmat.geom.*;
import april.jmat.*;

/** All formatting, with the exception of anchoring is specified using
 * markup. **/
public class VzText implements VisObject, VisSerializable
{
    /** The _ROUND variants are identical to the non-ROUND variants
        except that the final position is rounded to an integer
        value. This is useful when rendering text in pixel coordinates
        when the font is quite small, since pixels will line up
        "neatly".
    **/
    public enum ANCHOR {
        TOP_LEFT, TOP_LEFT_ROUND,
            TOP, TOP_ROUND,
            TOP_RIGHT, TOP_RIGHT_ROUND,
            LEFT, LEFT_ROUND,
            CENTER, CENTER_ROUND,
            RIGHT, RIGHT_ROUND,
            BOTTOM_LEFT, BOTTOM_LEFT_ROUND,
            BOTTOM, BOTTOM_ROUND,
            BOTTOM_RIGHT, BOTTOM_RIGHT_ROUND };
    ANCHOR anchor;

    // The text as passed in by user.
    String text = "";

    // Our internal data representation.
    ArrayList<Line> lines;

    /** Global state: these properties affect the rendering of the
     * whole VzText and can be specified by properties in text
     * markup. If specified multiple times, the last markup will
     * dictate the behavior. **/
    boolean dropShadow = true;
    double dropShadowMarginPixels = 3.0;
    Color dropShadowColor = null; // if not set, mirrors color of VisLayer background.

    double pixelMargin = 3.0; // around entire VzText

    double scale = 1.0;

    private enum JUSTIFICATION { LEFT, CENTER, RIGHT };

    int DEFAULT_FONT_SIZE = 12; // Default to good size for displaying in pixels

    /** A line of text is composed of multiple styled fragments, each
     * drawn contiguously (horizontally). The line additionally has an
     * alignment, which determines how the text is justified.
     **/
    static class Line
    {
        ArrayList<StyledFragment> fragments = new ArrayList<StyledFragment>();
        JUSTIFICATION justification;

        /** how much additional space below this line should we leave? **/
        int leading = 1;

        double getAdvance()
        {
            double advance = 0;

            for (StyledFragment frag : fragments) {
                advance += frag.getAdvance();
            }

            return advance;
        }

        double getWidth()
        {
            double width = 0;

            for (int idx = 0; idx < fragments.size(); idx++) {
                StyledFragment frag = fragments.get(idx);

                if (idx + 1 == fragments.size())
                    width += frag.getWidth();
                else
                    width += frag.getAdvance();
            }

            return width;
        }

        double getHeight()
        {
            double maxheight = 1;

            for (StyledFragment frag : fragments) {
                maxheight = Math.max(maxheight, frag.getHeight());
            }

            return maxheight + leading;
        }
    }

    /** Each fragment has a font, color, and the string itself. A line
     * can contain multiple styled fragments.
     **/
    static class StyledFragment
    {
        Color color;
        String s;

        // width of this fragment in pixels. (-1 means compute from
        // the font.) otherwise the width of the fragment can be
        // manually specified using markup.
        int    width = -1;

        VisFont vfont;
        VisFont.Text fontObject;

        double getAdvance()
        {
            if (width < 0)
                return fontObject.getAdvance();
            return width;
        }

        double getWidth()
        {
            if (width < 0)
                return fontObject.getWidth();

            return width;
        }

        /** Total line height in pixels. **/
        double getHeight()
        {
            return fontObject.getHeight();
        }

        /** What is the descent of the font? **/
        double getDescent()
        {
            return fontObject.getDescent();
        }
    }

    public VzText(String text)
    {
        this(ANCHOR.BOTTOM_LEFT, text);
    }

    public VzText(ANCHOR anchor, String text)
    {
        this.anchor = anchor;

        addText(text);
    }

    public void clear()
    {
        text = "";
        lines = null;
    }

    public void addText(String s)
    {
        text = text + s;
        lines = null;
    }

    // AARRGGBB
    static Color stringToColor(String s)
    {
        if (s.length()==7)
            return new Color(Integer.parseInt(s.substring(1,3), 16),
                             Integer.parseInt(s.substring(3,5), 16),
                             Integer.parseInt(s.substring(5,7), 16));
        if (s.length()==9)
            return new Color(Integer.parseInt(s.substring(3,5), 16),
                             Integer.parseInt(s.substring(5,7), 16),
                             Integer.parseInt(s.substring(7,9), 16),
                             Integer.parseInt(s.substring(1,3), 16));
        System.out.println("VzText: Badly formatted color "+s);
        return null;

    }

    public double getTotalWidth()
    {
        double maxLineWidth = 0;

        if (lines == null)
            parse();

        for (Line line : lines)
            maxLineWidth = Math.max(line.getWidth(), maxLineWidth);

        double totalWidth = maxLineWidth + 2*pixelMargin;
        return scale * totalWidth;
    }

    public double getTotalHeight()
    {
        double totalHeight = 0;

        if (lines == null)
            parse();

        for (Line line : lines)
            totalHeight += line.getHeight();

        totalHeight += 2*pixelMargin;
        return scale * totalHeight;
    }

    // completely reparse the text.
    void parse()
    {
        JUSTIFICATION justification = JUSTIFICATION.LEFT;
        Color color = Color.white;
        VisFont vfont = VisFont.getFont(new Font("Sans Serif", Font.PLAIN, DEFAULT_FONT_SIZE));
        int width = -1;

        lines = new ArrayList<Line>();

        String ss[] = text.split("\\n");
        for (String s : ss) {

            int pos = 0;

            Line line = new Line();
            line.justification = justification;
            lines.add(line);

            while (pos >= 0 && pos < s.length()) {

                // If there's not a format specifier first, consume
                // everything up until the format specifier.
                int fmtpos = s.indexOf("<<", pos);
                int endfmtpos = fmtpos >=0 ? s.indexOf(">>", fmtpos) : -1;

                if (fmtpos != pos || fmtpos < 0 || endfmtpos < 0) {
                    StyledFragment frag = new StyledFragment();
                    frag.vfont = vfont;
                    frag.color = color;
                    frag.width = width;
                    width = -1; // width isn't stateful.
                    if (fmtpos < 0)
                        frag.s = s.substring(pos);
                    else
                        frag.s = s.substring(pos, fmtpos);

                    frag.fontObject = frag.vfont.makeText(frag.s, frag.color);

                    line.fragments.add(frag);
                    pos = fmtpos;
                    continue;
                }

                // a format specifier begins at pos.
                String fmt = s.substring(fmtpos+2, endfmtpos);
                String toks[] = fmt.split(",");
                for (int i = 0; i < toks.length; i++) {
                    toks[i] = toks[i].toLowerCase().trim();

                    // #RRGGBB or #AARRGGBB
                    if (toks[i].startsWith("#") && (toks[i].length()==7 || toks[i].length()==9)) {
                        color = stringToColor(toks[i]);
                        continue;
                    }

                    if (toks[i].startsWith("scale")) {
                        if (toks[i].contains("=")) {
                            String arg = toks[i].substring(toks[i].indexOf("=")+1).trim().toLowerCase();
                            scale = Double.parseDouble(arg);
                        }
                        continue;
                    }

                    // dropshadow=#RRGGBB/#AARRGGBB, dropshadow=true/false
                    if (toks[i].startsWith("dropshadow")) {
                        if (toks[i].contains("=")) {
                            String arg = toks[i].substring(toks[i].indexOf("=")+1).trim().toLowerCase();
                            if (arg.equals("true") || arg.equals("yes") || arg.equals("1"))
                                dropShadow = true;
                            else if (arg.equals("false") || arg.equals("no") || arg.equals("0"))
                                dropShadow = false;
                            else if (arg.startsWith("#")) {
                                dropShadow = true;
                                dropShadowColor = stringToColor(arg);
                            } else {
                                System.out.println("VzText: Don't understand "+toks[i]);
                            }
                        } else {
                            dropShadow = true;
                        }
                        continue;
                    }

                    if (toks[i].equals("blue")) {
                        color = Color.blue;
                        continue;
                    }
                    if (toks[i].equals("red")) {
                        color = Color.red;
                        continue;
                    }
                    if (toks[i].equals("green")) {
                        color = Color.green;
                        continue;
                    }
                    if (toks[i].equals("black")) {
                        color = Color.black;
                        continue;
                    }
                    if (toks[i].equals("orange")) {
                        color = Color.orange;
                        continue;
                    }
                    if (toks[i].equals("yellow")) {
                        color = Color.yellow;
                        continue;
                    }
                    if (toks[i].equals("cyan")) {
                        color = Color.cyan;
                        continue;
                    }
                    if (toks[i].equals("magenta")) {
                        color = Color.magenta;
                        continue;
                    }
                    if (toks[i].equals("gray")) {
                        color = Color.gray;
                        continue;
                    }
                    if (toks[i].equals("white")) {
                        color = Color.white;
                        continue;
                    }
                    if (toks[i].equals("pink")) {
                        color = Color.pink;
                        continue;
                    }
                    if (toks[i].equals("darkgray")) {
                        color = Color.darkGray;
                        continue;
                    }

                    if (true) {
                        boolean good = false;
                        String fontData[] = new String[] { "serif", "Serif",
                                                           "sansserif", "SansSerif",
                                                           "monospaced", "Monospaced",
                                                           "fixed", "Monospaced" };

                        for (int k = 0; k < fontData.length; k+= 2) {
                            if (toks[i].startsWith(fontData[k])) {
                                int style = Font.PLAIN;
                                int sz = DEFAULT_FONT_SIZE;

                                String ts[] = toks[i].split("-");
                                for (int tsidx = 1; tsidx < ts.length; tsidx++) {
                                    if (ts[tsidx].equals("bold"))
                                        style |= Font.BOLD;
                                    if (ts[tsidx].equals("italic"))
                                        style |= Font.ITALIC;
                                    if (Character.isDigit(ts[tsidx].charAt(0)))
                                        sz = Integer.parseInt(ts[tsidx]);
                                }

                                vfont = VisFont.getFont(new Font(fontData[k+1], style, sz));
                                good = true;
                                break;
                            }
                        }

                        if (good)
                            continue;
                    }

                    if (toks[i].equals("left")) {
                        justification = JUSTIFICATION.LEFT;
                        line.justification = justification;
                        continue;
                    }
                    if (toks[i].equals("center")) {
                        justification = JUSTIFICATION.CENTER;
                        line.justification = justification;
                        continue;
                    }
                    if (toks[i].equals("right")) {
                        justification = JUSTIFICATION.RIGHT;
                        line.justification = justification;
                        continue;
                    }

                    // fixed-width. (manually specify width in
                    // pixels. Useful for making non-fixed width fonts
                    // line up.
                    if (toks[i].startsWith("width=")) {
                        width = Integer.parseInt(toks[i].substring(6));
                        continue;
                    }

                    if (toks[i].startsWith("margin=")) {
                        pixelMargin = Integer.parseInt(toks[i].substring(7));
                        continue;
                    }

                    System.out.println("VzText: Unknown format specifier: "+toks[i]);
                }

                // skip to the end of the format specifier.
                pos = endfmtpos + 2;
            }
        } // for (String s : ss)

    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        if (lines == null)
            parse();

        gl.glPushMatrix();
        gl.glScaled(scale, scale, scale);

        // determine overall width and height so we can draw a drop
        // shadow, and so that we can anchor.
        double maxLineWidth = 0;
        double totalHeight = 0;

        for (Line line : lines) {
            maxLineWidth = Math.max(line.getWidth(), maxLineWidth);
            totalHeight += line.getHeight();
        }

        totalHeight += 2*pixelMargin;
        double totalWidth = maxLineWidth + 2*pixelMargin;

        double anchorx = 0, anchory = 0;

        switch (anchor) {
            case TOP_LEFT:
            case LEFT:
            case BOTTOM_LEFT:
            case TOP_LEFT_ROUND:
            case LEFT_ROUND:
            case BOTTOM_LEFT_ROUND:
                anchorx = 0;
                break;

            case TOP_RIGHT:
            case RIGHT:
            case BOTTOM_RIGHT:
                anchorx = -totalWidth;
                break;

            case TOP_RIGHT_ROUND:
            case RIGHT_ROUND:
            case BOTTOM_RIGHT_ROUND:
                anchorx = Math.round(-totalWidth);
                break;

            case TOP:
            case CENTER:
            case BOTTOM:
                anchorx = -totalWidth/2;
                break;

            case TOP_ROUND:
            case CENTER_ROUND:
            case BOTTOM_ROUND:
                anchorx = Math.round(-totalWidth/2);
                break;
        }

        switch (anchor) {
            case TOP_LEFT:
            case TOP_RIGHT:
            case TOP:
                anchory = -totalHeight;
                break;

            case TOP_LEFT_ROUND:
            case TOP_RIGHT_ROUND:
            case TOP_ROUND:
                anchory = Math.round(-totalHeight);
                break;

            case BOTTOM_LEFT:
            case BOTTOM_RIGHT:
            case BOTTOM:
            case BOTTOM_LEFT_ROUND:
            case BOTTOM_RIGHT_ROUND:
            case BOTTOM_ROUND:
                anchory = 0;
                break;

            case RIGHT:
            case LEFT:
            case CENTER:
                anchory = -totalHeight / 2;
                break;

            case RIGHT_ROUND:
            case LEFT_ROUND:
            case CENTER_ROUND:
                anchory = Math.round(-totalHeight / 2);
                break;
        }

        // draw drop shadow
        if (dropShadow) {
            Color c = dropShadowColor;
            if (c == null)
                c = new Color(64,64,64,128);
            gl.glColor(c);

            gl.glPushMatrix();

            BasicShapes s = BasicShapes.square;
            gl.glTranslated(anchorx, anchory, 0);
            gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
            gl.glPolygonOffset(2,2);
            // scale BasicShapes.square to a [0,0]->[totalWidth,totalHeight] square
            gl.glScaled(totalWidth / 2.0, totalHeight / 2.0, 1);
            gl.glTranslated(1, 1, 0);
            s.bind(gl);
            s.draw(gl, GL.GL_QUADS);
            s.unbind(gl);
            gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);
            gl.glPopMatrix();
        }

        // now draw the actual text
        double y = totalHeight;

        for (Line line : lines) {

            double x = 0;

            switch (line.justification) {
                case LEFT:
                    break;
                case RIGHT:
                    x = maxLineWidth - line.getAdvance();
                    break;
                case CENTER:
                    x = (maxLineWidth - line.getAdvance()) / 2.0;
                    break;
            }

            y -= line.getHeight();

            for (StyledFragment frag : line.fragments) {

                gl.glPushMatrix();

                gl.glTranslated(anchorx + x + pixelMargin, anchory + y - pixelMargin, 0);

                frag.fontObject.render(vc, layer, rinfo, gl);
                x += frag.getAdvance();

                gl.glPopMatrix();
            }
        }

        gl.glPopMatrix();
    }

    public VzText(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeUTF(anchor.name());
        outs.writeUTF(text);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        this.anchor = ANCHOR.valueOf(ins.readUTF());
        this.text = ins.readUTF();
    }
}
