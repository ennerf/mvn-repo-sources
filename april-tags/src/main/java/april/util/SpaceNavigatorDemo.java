package april.util;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.io.*;

import april.vis.*;
import april.jmat.*;

public class SpaceNavigatorDemo implements SpaceNavigator.Listener
{
    JFrame jf;

    VisWorld.Buffer vb;
    VisWorld vw;
    VisLayer vl;
    VisCanvas vc;

    ParameterGUI pg;

    double translate_scale = 0.001;
    double rotate_scale = 0.0001;

    Color colors[] = new Color[] {Color.black, Color.red, Color.yellow,
                                  Color.green, Color.blue};
    int colorset = 0;

    long last = 0;

    public SpaceNavigatorDemo()
    {
        // init vis
        pg = new ParameterGUI();

        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);
        //vis2 defaults to 3.0 vc.getViewManager().setInterfaceMode(3);

        vb = vw.getBuffer("main");
        vw.getBuffer("grid").addFront(new VzGrid());
        vw.getBuffer("axes").addFront(new VzAxes());
        vl.setBufferEnabled("grid", false);

        jf = new JFrame("SpaceNavigator Demo");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);

        jf.setSize(1000, 600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        redraw();
    }

    public double[] translationScaled(SpaceNavigator.MotionEvent me)
    {
        return new double[] {scale_t(me.x),
                             scale_t(me.y),
                             scale_t(me.z)};
    }

    public double scale_t(double i)
    {
        return 5.0E-4 * Math.pow(i, 1) +
            1.0E-8 * Math.pow(i, 3);
    }

    public double[] rotationScaled(SpaceNavigator.MotionEvent me, double mag_t)
    {
        return new double[] {scale_r(me.roll, mag_t)  * 0.3,
                             scale_r(me.pitch, mag_t) * 0.3,
                             scale_r(me.yaw, mag_t)};
    }

    public double scale_r(double i, double mag_t)
    {
        double dampening = 0.5 / Math.pow(350, 2) * Math.pow(mag_t, 2);
        return 1.5E-4 * Math.pow(i, 1) * (1 - dampening);
    }

    @Override
    public void handleUpdate(SpaceNavigator.MotionEvent me)
    {
        long now = TimeUtil.utime();
        double dt = (now - last) / 1.0E6;
        last = now;
        VisWorld.Buffer vb = vw.getBuffer("FPS");
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_LEFT,
                                           new VzText(VzText.ANCHOR.TOP_LEFT, String.format("FPS: %3.1f", 1.0/dt))));
        vb.swap();


        String str = String.format("Last Event:\n<<monospaced-24>>" +
                                   "%4d :x\n%4d :y\n%4d :z\n%4d :r\n%4d :p\n%4d :y",
                                   me.x, me.y, me.z, me.roll, me.pitch, me.yaw);
        vb = vw.getBuffer("MOTION_EVENT");
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_RIGHT,
                                           new VzText(VzText.ANCHOR.TOP_RIGHT, str)));
        vb.swap();


        // display cross at center of rotation (eye) in 2 colors to ensure
        //vis2: These VzTexts originally specified a transparent background?? (alpha=0.0), not supported in vis2
        vb = vw.getBuffer("CENTER");
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                           new VzText(VzText.ANCHOR.LEFT,
                                                       "<<monospaced-24, blue>>|")));
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                           new VzText(VzText.ANCHOR.LEFT,
                                                       "<<monospaced-24, blue>>--")));
        vb.swap();

        vb = vw.getBuffer("CENTER2");
        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                           new VzText(VzText.ANCHOR.RIGHT,
                                                       "<<monospaced-24, black>>+")));
        vb.swap();

        //vis2  SpaceNavigatorDemo needs to be reimplemented as custom CameraManager
        DefaultCameraManager dcm = (DefaultCameraManager)vl.cameraManager;
        dcm.interfaceMode = 3.0;

        double eye[]    = null;
        double lookAt[] = null;
        double up[]     = null;

        VisCameraManager.CameraPosition position = dcm.getCameraTarget();
        eye     = position.eye;
        lookAt  = position.lookat;
        up      = position.up;

        // compute view_0 = lookAt - eye
        double view0[] = LinAlg.subtract(lookAt, eye);

        // compute unit vectors for in-view coordinate frame
        double x_norm[] = LinAlg.normalize(view0);
        double z_norm[] = LinAlg.normalize(up);
        double y_norm[] = LinAlg.crossProduct(z_norm, x_norm);

        // translate eye_0 to eye_1 with the scaled tx/ty/tz from the SpaceNavigator
        double A[][] = LinAlg.transpose(new double[][] { x_norm,
                                                         y_norm,
                                                         z_norm });
        double trans[] = LinAlg.matrixAB(A, translationScaled(me));

        double eye1[] = LinAlg.add(eye, trans);

        // rotate view_0 and up_0 with scaled r/p/y from the SpaceNavigator *in the
        // coordinate frame designated by view_0 and up_0
        double R[][] = LinAlg.rollPitchYawToMatrix(rotationScaled(me,
                                                                  LinAlg.magnitude(new double[] {me.x,
                                                                                                 me.y,
                                                                                                 me.z})));

        double B[][] = LinAlg.matrixAB(A, LinAlg.select(R, 0, 2, 0, 2));

        B = limitRotation(B);

        // update lookAt and up
        double Bt[][]   = LinAlg.transpose(B);
        double view1[]  = Bt[0];
        double up1[]    = Bt[2];

        double lookAt1[] = LinAlg.add(eye1, view1);

        // set view parameters
        vl.cameraManager.uiLookAt(eye1, lookAt1, up1, false);

        // draw lookat sphere

        if (me.left)
            colorset--;

        if (me.right)
            colorset++;

        redraw();
    }

    double[][] limitRotation(double[][] _B)
    {
        double B[][] = new double[4][4];
        for (int r=0; r < 3; r++) {
            for (int c=0; c < 3; c++) {
                B[r][c] = _B[r][c];
            }
        }
        B[3][3] = 1;

        double xyzrpy[] = LinAlg.matrixToXyzrpy(B);

        // no roll
        xyzrpy[3] = 0;

        // limit pitch
        double maxpitch = Math.PI/3;
        xyzrpy[4] = Math.min(Math.max(xyzrpy[4], -maxpitch), maxpitch);

        B = LinAlg.xyzrpyToMatrix(xyzrpy);
        return LinAlg.select(B, 0, 2, 0, 2);
    }

    public void redraw()
    {
        vb.addBack(new VisChain(LinAlg.translate(0, 0, -1),
                                new VzBox(100, 100, 2, new VzMesh.Style(Color.gray))));

        vb.addBack(new VisChain(LinAlg.translate(4, -5, 1),
                                new VzBox(3, 3, 2, new VzMesh.Style(getColor(0)))));

        vb.addBack(new VisChain(LinAlg.translate(4, 0, 1),
                                new VzBox(3, 3, 4, new VzMesh.Style(getColor(1)))));

        vb.addBack(new VisChain(LinAlg.translate(4, 5, 1),
                                new VzBox(3, 2, 2, new VzMesh.Style(getColor(2)))));

        vb.addBack(new VisChain(LinAlg.translate(4, 12, 1),
                                new VzBox(4, 4, 2, new VzMesh.Style(getColor(3)))));

        vb.addBack(new VisChain(LinAlg.translate(4, 17, 1),
                                new VzBox(4, 4, 2, new VzMesh.Style(getColor(4)))));

        vb.swap();
    }

    public Color getColor(int offset)
    {
        int idx = ((colorset + offset) % colors.length + colors.length) % colors.length;

        return colors[idx];
    }

    public static void main(String args[])
    {
        SpaceNavigator sn = new SpaceNavigator();
        sn.addListener(new SpaceNavigatorDemo());
    }
}
