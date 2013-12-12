package april.sim;

import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;

import lcm.lcm.*;

import april.lcmtypes.*;
import april.jmat.*;
import april.util.*;
import april.vis.*;
import april.lcm.*;
import april.vis.*;

public class Sensors
{
    static Object vocObj = new Object();
    static VisCanvas voc;
    static VisLayer vol;

    public static BufferedImage camera(VisWorld vw,
                                       double eye[], double lookAt[], double up[],
                                       double fovy_degrees,
                                       int width, int height)
    {
        synchronized(vocObj) {
            if (vol == null) {
                vol = new VisLayer(vw);
                vol.backgroundColor = Color.white;
                ((DefaultCameraManager)vol.cameraManager).interfaceMode = 3.0;
                // The following seems to result in a 2.5 cm backward translation of the camera.
                ((DefaultCameraManager)vol.cameraManager).zclip_near = 0.025;
                ((DefaultCameraManager)vol.cameraManager).UI_ANIMATE_MS = 0;
            }
            if (voc == null) {
                voc = new VisCanvas(vol);
                voc.showSizeChanges = false;
            }

            voc.setSize(width, height);
            vol.world = vw;
            ((DefaultCameraManager)vol.cameraManager).perspective_fovy_degrees = fovy_degrees;
            vol.cameraManager.uiLookAt(eye,lookAt,up, false);

            voc.drawSync();
            return ImageUtil.flipVertical(voc.getLatestFrame());
        }
    }

    public static double[] laser(SimWorld sw, HashSet<SimObject> ignore,
                                 double T[][],
                                 int nranges, double rad0, double radstep, double maxrange)
    {
        double ranges[] = new double[nranges];

        double eye[] = new double[] { T[0][3], T[1][3], T[2][3] };
        double R[][] = LinAlg.copy(T);
        R[0][3] = 0;
        R[1][3] = 0;
        R[2][3] = 0;

        synchronized(sw) {
            for (int i = 0; i < ranges.length; i++) {
                double dir[] = LinAlg.transform(R, new double[] { Math.cos(rad0 + i*radstep),
                                                                  Math.sin(rad0 + i*radstep),
                                                                  0 });

                ranges[i] = Math.min(maxrange, sw.collisionDistance(eye, dir, ignore));
            }
        }

        return ranges;
    }
}
