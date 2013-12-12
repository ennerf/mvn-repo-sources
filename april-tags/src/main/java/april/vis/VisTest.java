package april.vis;

import java.awt.*;
import java.awt.image.*;
import javax.swing.*;
import java.util.*;
import java.io.*;
import javax.imageio.*;
import april.jmat.*;

public class VisTest
{
    JFrame jf;
    VisWorld vw = new VisWorld();
    VisLayer vl = new VisLayer(vw);
    VisCanvas vc = new VisCanvas(vl);

    VisFont vfont = VisFont.getFont(new Font("Sans Serif", Font.PLAIN, 128));

    static class MyLayer
    {
        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);

        MyLayer(double pos[], Color c)
        {
            VzGrid.addGrid(vw);

            vl.backgroundColor = c;
            vl.layerManager = new DefaultLayerManager(vl, pos);

            VisWorld.Buffer vb = vw.getBuffer("foo");
            vb.addBack(new VisChain(LinAlg.scale(10, 10, 10),
                                    new Square(Color.blue),
                                    LinAlg.translate(-0.1, -0.1, .1),
                                    new Square(Color.green)
                           ));
            vb.swap();
        }
    }

    static class PlotLayer
    {
        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);

        PlotLayer(double pos[])
        {
            vl.layerManager = new DefaultLayerManager(vl, pos);
//            ((DefaultCameraManager) vl.cameraManager).perspective_fovy_degrees = 30;
//            ((DefaultCameraManager) vl.cameraManager).perspectiveness = 0;
            ((DefaultCameraManager) vl.cameraManager).scaley1 = 10;
            ((DefaultCameraManager) vl.cameraManager).interfaceMode = 2.5;
            ((DefaultCameraManager) vl.cameraManager).fit2D(new double[] { 0, -1, 0},
                                                            new double[] { 30, 1, 0 },
                                                            true);
            vl.backgroundColor = Color.red;


            VzGrid vg = VzGrid.addGrid(vw);
            VisWorld.Buffer vb = vw.getBuffer("plot");

            VisColorData cd = new VisColorData();
            VisVertexData vd = new VisVertexData();
            for (double x = 0; x < 30; x += 0.01) {
                float p[] = new float[] { (float) x, (float) Math.sin(x) };
                vd.add(p);
                cd.add(0xff7f0000 + (int) (255*(.5+p[1]/2)));
            }
            vb.addBack(new VisLighting(false, new VzPoints(vd, new VzPoints.Style(cd, 2.0))));
//            vb.addBack(new VisLighting(false, new VzPoints(vd, new VisConstantColor(Color.yellow), 2.0)));
            vb.swap();
        }
    }

    public VisTest()
    {
        VzGrid.addGrid(vw);

        jf = new JFrame("VisTest");
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.setSize(600,400);
        jf.setVisible(true);

        if (true) {
            VisWorld.Buffer vb = vw.getBuffer("foo");
            vb.addBack(new VisChain(LinAlg.translate(0, 0, -1),
                                    new VzCircle(10.0, 33,
                                                 new VzLines.Style(Color.blue, 2),
                                                 new VzMesh.Style(Color.gray))));

            vb.addBack(new VisChain(LinAlg.scale(10, 10, 10),
                                    new Square(Color.blue),
                                    LinAlg.translate(-0.1, -0.1, 1),
                                    new Square(Color.green)
                           ));

            vb.swap();
        }

        if (true) {
            try {
                VisWorld.Buffer vb = vw.getBuffer("sphere");
                VzSphere vo = new VzSphere(10, new VisTexture(ImageIO.read(new File("/home/ebolson/earth.png"))));
//                VzSphere vo = new VzSphere(10, new VisFillStyle(Color.green));
                vb.addBack(new VisChain(LinAlg.translate(10, 0, 0),
                                        vo));
                vb.swap();
            } catch (Exception ex) {
                System.out.println("ex: "+ex);
            }
        }

        if (true) {
            VisWorld.Buffer vb = vw.getBuffer("vistext");

            vb.addBack(new VisChain(LinAlg.scale(.1, .1, 1),
                                    new VzText(VzText.ANCHOR.BOTTOM_LEFT,
                                                "<<dropshadow=#33330088>>"+
                                                "<<sansserif-bold-16,green,right>>right\n" +
                                                "<<center>><<red>>R<<green>>G<<blue>>B\n" +
                                                "<<blue,left>>left\n" +
                                                "<<white,serif-italic-16>>serif\n" +
                                                "<<monospaced-16>>monospaced\n" +
                                                "<<sansserif-32,left>>long line\n" +
                                                "<<cyan,serif-italic-bold-20>>serif-bold-italic")));

            vb.addBack(new VisDepthTest(false,
                                        new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_RIGHT,
                                                                new VzText(VzText.ANCHOR.BOTTOM_RIGHT,
                                                                            "<<margin=15>>" +
                                                                            "<<cyan,serif-italic-32>>cyan 32\n"))));


            vb.addBack(new VisDepthTest(false,
                                        new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                                                new VzText(VzText.ANCHOR.TOP_LEFT,
                                                                            "<<margin=15>>" +
                                                                            "<<white,sansserif-bold-16,width=100>>abc<<yellow>>42\n"+
                                                                            "<<cyan,sansserif-bold-16,width=100>>leftie<<yellow>>1.35\n"))));
            vb.swap();
        }

        if (true) {
            MyLayer ml1 = new MyLayer(new double[] { 0, 0, 0.25, 0.25 }, Color.blue);
            vc.addLayer(ml1.vl);

            MyLayer ml2 = new MyLayer(new double[] { 0.75, 0.75, 0.25, 0.25 }, Color.green);
            vc.addLayer(ml2.vl);

            PlotLayer pl = new PlotLayer(new double[] { .0, 0.6, 0.4, 0.4 });
            vc.addLayer(pl.vl);
        }
    }

    public static void main(String args[])
    {
        VisTest vt = new VisTest();
    }

    class DumbSquare implements VisObject
    {
        Color c;

        public DumbSquare(Color c)
        {
            this.c = c;
        }

        public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
        {
            gl.glColor(c);

            gl.glBegin(GL.GL_QUADS);
            gl.glVertex2d(-.5, -.5);
            gl.glVertex2d(.5, -.5);
            gl.glVertex2d(.5, .5);
            gl.glVertex2d(-.5, .5);
            gl.glEnd();
        }
    }

    static class Square implements VisObject, VisSerializable
    {
        static long  vid, cid;
        static float vertices[];
        static int colors[];

        static {
            vid = VisUtil.allocateID();
            cid = VisUtil.allocateID();

            vertices = new float[] { -.5f, -.5f,
                                     .5f, -.5f,
                                     .5f, .5f,
                                     -.5f, .5f };

            // AARRGGBB
            colors = new int[] { 0xffff0000,
                                 0xff00ff00,
                                 0xff0000ff,
                                 0xffffffff };
        }

        Color c;

        public Square(Color c)
        {
            this.c = c;
        }

        public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
        {
            gl.glColor(c);
            gl.gldBind(GL.VBO_TYPE_VERTEX, vid, 4, 2, vertices);
            gl.gldBind(GL.VBO_TYPE_COLOR, cid, 4, 4, colors);
            gl.glDrawArrays(GL.GL_QUADS, 0, 4);
            gl.gldUnbind(GL.VBO_TYPE_VERTEX, vid);
            gl.gldUnbind(GL.VBO_TYPE_COLOR, cid);
        }

        public Square(ObjectReader r)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeColor(c);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            c = ins.readColor();
        }

    }
}
