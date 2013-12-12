package april.vis.examples;

import april.vis.*;

import javax.imageio.*;
import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;
import april.jmat.*;
import april.jmat.geom.*;
import april.util.*;

import java.util.concurrent.*;

public class FOVTest implements ParameterListener
{
    VisWorld vw = new VisWorld();
    VisLayer vl = new VisLayer(vw);
    VisCanvas vc = new VisCanvas(vl);

    ParameterGUI pg = new ParameterGUI();

    public FOVTest()
    {
        // remove the default camera controls.
        vl.eventHandlers.clear();

        JFrame jf = new JFrame("FOVTest");

        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);

        pg.addDoubleSlider("fov", "fov (degrees)", 5, 180, 50);
        pg.addListener(this);

        jf.setSize(600,400);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        parameterChanged(pg, "fov");

        ((DefaultCameraManager) vl.cameraManager).interfaceMode = 3.0;
        vl.cameraManager.uiLookAt(new double[3], new double[] { 0, 0, 1 }, new double[] { 0, 1, 0 }, true);
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        double fov = pg.gd("fov");

        VisWorld.Buffer vb = vw.getBuffer("cone");

        vb.addBack(new VisChain(LinAlg.translate(0, 0, 1.0/Math.tan(Math.toRadians(fov/2))),
                                new VzCircle(1, new VzLines.Style(Color.white, 1))));

        vb.swap();
    }

    public static void main(String args[])
    {
        new FOVTest();
    }
}
