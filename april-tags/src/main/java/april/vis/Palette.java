package april.vis;

import java.awt.Color;
import java.util.*;
import javax.swing.*;

import april.jmat.LinAlg;

public class Palette
{
    /** Color class where each entry should have a distinct intensity,
     *  even with colorblind perception.
     */
    public static class friendly
    {
        // dark to light
        public static Color black  = new Color(   0,   0,   0);
        public static Color purple = new Color( 116,  20, 114);
        public static Color green  = new Color(   0, 111,  69);
        public static Color blue   = new Color(   0, 143, 213);
        public static Color orange = new Color( 247, 148,  30);
        public static Color olive  = new Color( 171, 214, 156);
        public static Color yellow = new Color( 255, 242,   0);
        public static Color white  = new Color( 255, 255, 255);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(black);
            list.add(purple);
            list.add(green);
            list.add(blue);
            list.add(orange);
            list.add(olive);
            list.add(yellow);
            list.add(white);

            return list;
        }
    }

    /** Color class designed such that no individual color draws the eye
     *  more than any other. This palette is colorblind safe.
     */
    public static class diverging_brewer
    {
        // Reddish to bluish
        public static Color red          = new Color( 165,   0,  38);
        public static Color redorange    = new Color( 215,  48,  39);
        public static Color tangerine    = new Color( 244, 109,  67);
        public static Color yelloworange = new Color( 254, 174,  97);
        public static Color goldenrod    = new Color( 255, 224, 144);
        public static Color lightyellow  = new Color( 255, 255, 191);
        public static Color lightblue    = new Color( 224, 243, 248);
        public static Color skyblue      = new Color( 171, 217, 233);
        public static Color robinsegg    = new Color( 116, 173, 209);
        public static Color blue         = new Color(  69, 117, 180);
        public static Color darkblue     = new Color(  49,  54, 149);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(red);
            list.add(redorange);
            list.add(tangerine);
            list.add(yelloworange);
            list.add(goldenrod);
            list.add(lightyellow);
            list.add(lightblue);
            list.add(skyblue);
            list.add(robinsegg);
            list.add(blue);
            list.add(darkblue);

            return list;
        }

        /** A (supposedly) printer-friendly subset of the colors */
        public static List<Color> listPrintFriendly()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(redorange);
            list.add(tangerine);    // XXX Really should be between tangerine and yelloworange
            list.add(goldenrod);
            list.add(lightblue);
            list.add(robinsegg);    // XXX Really should be between sky and robinsegg
            list.add(blue);

            return list;
        }
    }

    /** Color class designed such that no individual color draws the eye more
     *  than any other. Not colorblind safe, but provides a wider variety of
     *  colors than divergent_brewer as well as one more color option. This
     *  is the largest Brewer set possible.
     */
    public static class qualitative_brewer
    {
        public static Color teal    = new Color( 141, 211, 199);
        public static Color yellow0 = new Color( 255, 255, 179);
        public static Color purple0 = new Color( 190, 186, 218);
        public static Color red     = new Color( 251, 128, 114);
        public static Color blue    = new Color( 128, 177, 211);
        public static Color orange  = new Color( 253, 180,  98);
        public static Color green0  = new Color( 179, 222, 105);
        public static Color pink    = new Color( 252, 205, 229);
        public static Color gray    = new Color( 217, 217, 217);
        public static Color purple1 = new Color( 188, 128, 189);
        public static Color green1  = new Color( 204, 235, 197);
        public static Color yellow1 = new Color( 255, 237, 111);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(teal);
            list.add(yellow0);
            list.add(purple0);
            list.add(red);
            list.add(blue);
            list.add(orange);
            list.add(green0);
            list.add(pink);
            list.add(gray);
            list.add(purple1);
            list.add(green1);
            list.add(yellow1);

            return list;
        }
    }

    /** Color class designed such that no invidual color draws the eye more
     *  than any other. Provides 6 colors as well as light versions of those
     *  colors for "paired" data. These colors are, in theory, colorblind and
     *  print friendly.
     */
    public static class paired_brewer
    {
        public static Color blue0   = new Color( 166, 206, 227);
        public static Color blue1   = new Color(  31, 120, 180);
        public static Color green0  = new Color( 178, 223, 138);
        public static Color green1  = new Color(  51, 160,  44);
        public static Color red0    = new Color( 251, 154, 153);
        public static Color red1    = new Color( 227,  26,  28);
        public static Color orange0 = new Color( 253, 191, 111);
        public static Color orange1 = new Color( 255, 127,   0);
        public static Color purple0 = new Color( 202, 178, 214);
        public static Color purple1 = new Color( 106,  61, 154);
        public static Color yellow0 = new Color( 255, 255, 153);
        public static Color yellow1 = new Color( 177,  89,  40);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.addAll(listLight());
            list.addAll(listDark());

            return list;
        }

        public static List<Color> listLight()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(blue0);
            list.add(green0);
            list.add(red0);
            list.add(orange0);
            list.add(purple0);
            list.add(yellow0);

            return list;
        }

        public static List<Color> listDark()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(blue1);
            list.add(green1);
            list.add(red1);
            list.add(orange1);
            list.add(purple1);
            list.add(yellow1);

            return list;
        }

    }

    public static class vibrant
    {
        public static Color pink    = new Color( 211,  61, 125);
        public static Color green   = new Color( 163, 221,  70);
        public static Color orange  = new Color( 255, 168,  69);
        public static Color blue    = new Color(  97, 223, 241);
        public static Color purple  = new Color( 186, 153, 251);
        public static Color tan     = new Color( 228, 224, 130);
        public static Color gray    = new Color( 238, 238, 238);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(pink);
            list.add(green);
            list.add(orange);
            list.add(blue);
            list.add(purple);
            list.add(tan);
            list.add(gray);

            return list;
        }
    }

    public static class vibrant_printable
    {
        public static Color orange  = new Color( 235, 148,  49);
        public static Color blue    = new Color(  77, 143, 201);
        public static Color green   = new Color( 173, 231,  80);
        public static Color pink    = new Color( 211,  61, 125);
        public static Color cyan    = new Color(  97, 223, 241);
        public static Color purple  = new Color( 186, 153, 251);
        public static Color tan     = new Color( 218, 204, 110);
        public static Color gray    = new Color( 238, 238, 238);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(orange);
            list.add(blue);
            list.add(green);
            list.add(pink);
            list.add(cyan);
            list.add(purple);
            list.add(tan);
            list.add(gray);

            return list;
        }
    }

    public static class web
    {
        public static Color pink1   = new Color( 185,  46, 102);
        public static Color pink2   = new Color( 239,  91, 147);
        public static Color orange1 = new Color( 255, 105,  48);
        public static Color orange2 = new Color( 247, 145,   0);
        public static Color green1  = new Color( 207, 222,  33);
        public static Color green2  = new Color(  59, 161,  91);
        public static Color green3  = new Color(  44, 178, 175);
        public static Color blue1   = new Color(  56, 183, 221);
        public static Color blue2   = new Color(  70, 153, 214);
        public static Color blue3   = new Color(  32, 116, 154);
        public static Color blue4   = new Color(  48,  81, 107);
        public static Color blue5   = new Color(  58,  86, 150);
        public static Color blue6   = new Color(  16, 182, 231);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(pink1);
            list.add(pink2);
            list.add(orange1);
            list.add(orange2);
            list.add(green1);
            list.add(green2);
            list.add(green3);
            list.add(blue1);
            list.add(blue2);
            list.add(blue3);
            list.add(blue4);
            list.add(blue5);
            list.add(blue6);

            return list;
        }
    }

    public static class code
    {
        public static Color dark_blue = new Color(0x04, 0x20, 0x29);
        public static Color gray      = new Color(0x83, 0x94, 0x8F);
        public static Color blue      = new Color(0x00, 0x88, 0xD9);
        public static Color teal      = new Color(0x00, 0xA6, 0x9A);
        public static Color orange    = new Color(0xBC, 0x88, 0x00);
        public static Color olive     = new Color(0x7C, 0x9F, 0x00);

        public static List<Color> listAll()
        {
            List<Color> list = new ArrayList<Color>();
            list.add(dark_blue);
            list.add(gray);
            list.add(blue);
            list.add(teal);
            list.add(orange);
            list.add(olive);

            return list;
        }
    }

    public static List<Color> listAll()
    {
        List<Color> list = new ArrayList<Color>();
        list.addAll(friendly.listAll());
        list.addAll(web.listAll());
        list.addAll(vibrant.listAll());
        list.addAll(vibrant_printable.listAll());
        list.addAll(diverging_brewer.listAll());
        list.addAll(qualitative_brewer.listAll());
        list.addAll(paired_brewer.listAll());

        return list;
    }

    /** Render all of the color palettes unlit for your viewing pleasure */
    public static void main(String[] args)
    {
        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);

        JFrame jf = new JFrame("VisPalette");
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setSize(800, 600);
        jf.add(vc);

        // Setup all color grids.
        {
            VisWorld.Buffer vb = vw.getBuffer("friendly");

            vb.addBack(new VisLighting(false,
                                       LinAlg.translate(0, 1.0, 0),
                                       LinAlg.scale(0.002),
                                       new VzText(VzText.ANCHOR.BOTTOM_LEFT,
                                                  "<<monospaced-128>>Friendly")));
            List<Color> list = friendly.listAll();
            for (int i = 0; i < list.size(); i++) {
                vb.addBack(new VisLighting(false,
                                           LinAlg.translate(.5 + i%4, .5-i/4, 0),
                                           new VzRectangle(1, 1,
                                                           new VzMesh.Style(list.get(i)))));
            }

            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("web");

            vb.addBack(new VisLighting(false,
                                       LinAlg.translate(7.0, 1.0, 0),
                                       LinAlg.scale(0.002),
                                       new VzText(VzText.ANCHOR.BOTTOM_LEFT,
                                                  "<<monospaced-128>>Web")));
            List<Color> list = web.listAll();
            for (int i = 0; i < list.size(); i++) {
                vb.addBack(new VisLighting(false,
                                           LinAlg.translate(7.5 + i%7, .5-i/7, 0),
                                           new VzRectangle(1, 1,
                                                           new VzMesh.Style(list.get(i)))));
            }

            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("vibrant");

            vb.addBack(new VisLighting(false,
                                       LinAlg.translate(0, -2.0, 0),
                                       LinAlg.scale(0.002),
                                       new VzText(VzText.ANCHOR.BOTTOM_LEFT,
                                                  "<<monospaced-128>>Vibrant")));
            List<Color> list = vibrant.listAll();
            for (int i = 0; i < list.size(); i++) {
                vb.addBack(new VisLighting(false,
                                           LinAlg.translate(.5 + i%4, -2.5-i/4, 0),
                                           new VzRectangle(1, 1,
                                                           new VzMesh.Style(list.get(i)))));
            }

            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("diverging_brewer");

            vb.addBack(new VisLighting(false,
                                       LinAlg.translate(7.0, -2.0, 0),
                                       LinAlg.scale(0.002),
                                       new VzText(VzText.ANCHOR.BOTTOM_LEFT,
                                                  "<<monospaced-128>>Diverging Brewer")));
            List<Color> list = diverging_brewer.listAll();
            for (int i = 0; i < list.size(); i++) {
                vb.addBack(new VisLighting(false,
                                           LinAlg.translate(7.5 + i%6, -2.5-i/6, 0),
                                           new VzRectangle(1, 1,
                                                           new VzMesh.Style(list.get(i)))));
            }

            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("qualitative_brewer");

            vb.addBack(new VisLighting(false,
                                       LinAlg.translate(0, -5.0, 0),
                                       LinAlg.scale(0.002),
                                       new VzText(VzText.ANCHOR.BOTTOM_LEFT,
                                                  "<<monospaced-128>>Qualitative Brewer")));
            List<Color> list = qualitative_brewer.listAll();
            for (int i = 0; i < list.size(); i++) {
                vb.addBack(new VisLighting(false,
                                           LinAlg.translate(.5 + i%6, -5.5-i/6, 0),
                                            new VzRectangle(1, 1,
                                                            new VzMesh.Style(list.get(i)))));
            }

            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("paired_brewer");

            vb.addBack(new VisLighting(false,
                                       LinAlg.translate(7.0, -5.0, 0),
                                       LinAlg.scale(0.002),
                                       new VzText(VzText.ANCHOR.BOTTOM_LEFT,
                                                  "<<monospaced-128>>Paired Brewer")));
            List<Color> list = paired_brewer.listAll();
            for (int i = 0; i < list.size(); i++) {
                vb.addBack(new VisLighting(false,
                                           LinAlg.translate(7.5 + i%6, -5.5-i/6, 0),
                                           new VzRectangle(1, 1,
                                                           new VzMesh.Style(list.get(i)))));
            }

            vb.swap();
        }

        {
            VisWorld.Buffer vb = vw.getBuffer("code");

            vb.addBack(new VisLighting(false,
                                       LinAlg.translate(0, -8.0, 0),
                                       LinAlg.scale(0.002),
                                       new VzText(VzText.ANCHOR.BOTTOM_LEFT,
                                                  "<<monospaced-128>>Code")));
            List<Color> list = code.listAll();
            for (int i = 0; i < list.size(); i++) {
                vb.addBack(new VisLighting(false,
                                           LinAlg.translate(.5 + i%6, -8.5-i/6, 0),
                                           new VzRectangle(1, 1,
                                                           new VzMesh.Style(list.get(i)))));
            }

            vb.swap();
        }

        // Setup viewport
        vl.cameraManager.fit2D(new double[] {0, 1},
                               new double[] {14, -8},
                               true);

        jf.setVisible(true);
    }
}
