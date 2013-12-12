package april.camera.calibrator;

import java.io.*;
import java.awt.*;
import javax.swing.*;
import java.util.*;

import april.vis.*;
import april.camera.*;
import april.camera.tools.*;
import april.jmat.*;
import april.util.*;
import april.tag.*;
import java.awt.image.*;

public class DebugAprilCalDictionary extends Thread implements VisConsole.Listener
{
    VisWorld vw = new VisWorld();
    VisLayer vl = new VisLayer(vw);
    VisCanvas vc = new VisCanvas(vl);

    JFrame jf = new JFrame("Debug Frame Scorers");

    AprilCal ac;

    HashSet<String> bufferNames = new HashSet();

    public DebugAprilCalDictionary(AprilCal ac)
    {
        this.ac = ac;

        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.setSize(720, 480);
        jf.setLocation(0,600);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        new VisConsole(vw,vl,vc).addListener(this);
        start();
    }

    public boolean consoleCommand(VisConsole vc, PrintStream out, String command)
    {
        String toks[] = command.split("\\s+");

        if (toks.length == 2 && toks[0].equals("enable")) {
            boolean enable = toks[1].equals("all");
            for (String bufferName : bufferNames)
                vl.setBufferEnabled(bufferName, enable);

            out.printf((enable? "Enabled " : "Disabled ")+" %d buffers\n", bufferNames.size());
        }
        return false;
    }

    /** Return commands that start with prefix. (You can return
     * non-matching completions; VisConsole will filter them
     * out.) You may return null. **/
    public ArrayList<String> consoleCompletions(VisConsole vc, String prefix)
    {
        return new ArrayList(Arrays.asList("enable all","enable none"));
    }

    public void run()
    {

        ArrayList<AprilCal.SuggestedImage> ranked = ac.ranked;
        while(true){
            TimeUtil.sleep(100);
            if (ac.ranked == ranked)
                continue;

            ranked = ac.ranked;
            // Else, do some debug rendering

            drawDictionary(ranked);
        }
    }


    public void drawDictionary(ArrayList<AprilCal.SuggestedImage> ranked)
    {
        ranked = new ArrayList(ranked); // Thread saf(er)
        // Draw each ranked image on a separate buffer
        BufferedImage sampleIm = ac.calibrator.getCalRef().getAllImageSets().get(0).get(0);
        CalibrationInitializer initializer = ac.calibrator.getCalRef().getInitializers().get(0);
        double PixelsToVis[][] = getPlottingTransformation(sampleIm, true);

        for (int i = 0; i < ranked.size(); i++) {
            bufferNames.add("ranked-"+i);
            VisWorld.Buffer vb = vw.getBuffer("ranked-"+i);
            vb.setDrawOrder(i);

            // Draw all the suggestions in muted colors
            VisChain chain = new VisChain();
            for (TagDetection d : ranked.get(i).detections) {

                Color color = ColorUtil.seededColor(i); //colorList.get(d.id % colorList.size());
                chain.add(new VzLines(new VisVertexData(d.p),
                                      VzLines.LINE_LOOP,
                                      new VzLines.Style(color, 2)));
            }
            vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                        new VisChain(PixelsToVis, chain)));
            vb.swap();
        }

        ParameterizableCalibration curcal = null, cal = null;
        double params[] = null;
        if (ac.calibrator != null)
            curcal = ac.calibrator.getCalRef().getCameras().get(0).cal;
        if (curcal != null)
            params = curcal.getParameterization();
        if (params != null)
            cal = initializer.initializeWithParameters(sampleIm.getWidth(), sampleIm.getHeight(), params);

        if (cal == null)
            return;

        {

            // Plot the center of each extrinsics in the dictionary;

            VisWorld.Buffer vb = vw.getBuffer("centers-ext");
            vb.setDrawOrder(1000);
            for (int i = 0; i < ranked.size(); i++) {
                double ext[][] = LinAlg.xyzrpyToMatrix(ranked.get(i).xyzrpy_cen);
                assert(ext != null);
                double cxy[] = CameraMath.project(cal, ext, new double[3]);
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                            PixelsToVis,new VzPoints(new VisVertexData(cxy), new VzPoints.Style(Color.cyan, 4))));

            }
            vb.swap();
        }


        { // Draw the focal center
            double [][] intrin = cal.copyIntrinsics();
            double cc[] = {intrin[0][2], intrin[1][2], 0};
            vw.getBuffer("cc").addBack(new VisPixCoords(VisPixCoords.ORIGIN.CENTER,
                                                        PixelsToVis,
                                                        new VzPoints(new VisVertexData(cc),
                                                                     new VzPoints.Style(Color.yellow, 6))));
            vw.getBuffer("cc").swap();
        }

    }


    private double[][] getPlottingTransformation(BufferedImage im, boolean mirror)
    {
        double imaspect = ((double) im.getWidth()) / im.getHeight();
        double visaspect = ((double) vc.getWidth()) / vc.getHeight();

        double h = 0;
        double w = 0;

        if (imaspect > visaspect) {
            w = vc.getWidth();
            h = w / imaspect;
        } else {
            h = vc.getHeight();
            w = h*imaspect;
        }

        double T[][] = LinAlg.multiplyMany(LinAlg.translate(-w/2, -h/2, 0),
                                           CameraMath.makeVisPlottingTransform(im.getWidth(), im.getHeight(),
                                                                               new double[] {   0,   0 },
                                                                               new double[] {   w,   h },
                                                                               true));

        if (mirror)
            T = LinAlg.multiplyMany(T,
                                    LinAlg.translate(im.getWidth(), 0, 0),
                                    LinAlg.scale(-1, 1, 1));

        return T;
    }

}
