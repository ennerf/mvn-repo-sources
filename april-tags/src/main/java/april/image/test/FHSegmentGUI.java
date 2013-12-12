package april.image.test;

import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.util.*;

import javax.imageio.*;
import javax.swing.*;

import april.util.*;
import april.image.*;
import april.jcam.*;

public class FHSegmentGUI
{

    // Testing code -- displays parameter GUI to explore parameters' effect on segmentation
    public static void main(String args[])
    {

        if (args.length != 4) {
            System.out.printf("Usage: java jhstrom.image.FHSegment sig k min input\n");
            System.exit(1);
        }

        double sig = Double.parseDouble(args[0]);
        double k = Double.parseDouble(args[1]);
        int minSz = Integer.parseInt(args[2]);


        try {
            final BufferedImage im = ImageConvert.convertImage(ImageIO.read(new File(args[3])), BufferedImage.TYPE_INT_RGB);

            // GUI Setup
            JFrame jf =  new JFrame("FH Segment");
            final JImage jim = new JImage();
            jim.setFit(true);

            ParameterGUI pg = new ParameterGUI();
            pg.addDoubleSlider("k","Noise Tolerance (k)",0,1500, k);
            pg.addIntSlider("minsz","Min Segment Size",0,10000, minSz);
            pg.addDoubleSlider("sig","Gauss. Blur Sigma",0,10, sig);
            pg.addCheckBoxes("segment","Segment",true,"avgc","Avg Color", false, "float", "Use FHFloatSegment", true);

            jf.setLayout(new BorderLayout());
            jf.add(jim, BorderLayout.CENTER);
            jf.add(pg, BorderLayout.SOUTH);

            jf.setSize(1024,768);
            jf.setVisible(true);
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


            pg.addListener(new ParameterListener() {
                    public void parameterChanged(ParameterGUI pg, String name)
                    {

                        double sig = pg.gd("sig");
                        double k = pg.gd("k");
                        int minSz = pg.gi("minsz");

                        // Mimic FH blur:
                        sig = Math.max(sig, .01);
                        int filtsz = ((int) (2*Math.ceil(4*sig))) | 1;
                        float filt[] = SigProc.makeGaussianFilter(sig, filtsz);


                        FloatImage fir = new FloatImage(im, 16);
                        FloatImage fig = new FloatImage(im, 8);
                        FloatImage fib = new FloatImage(im, 0);
                        fir = fir.filterFactoredCentered(filt,filt);
                        fig = fig.filterFactoredCentered(filt,filt);
                        fib = fib.filterFactoredCentered(filt,filt);

                        if (pg.gb("segment")) {
                            BufferedImage out = null;
                            if (pg.gb("float"))
                                out = FHFloatSegment.segment(fir.d, fig.d, fib.d, 1.0f,
                                                             im.getWidth(), im.getHeight(),
                                                             k, minSz);
                            else
                                out = FHSegment.segment(im, k, minSz);

                            if (pg.gb("avgc")) {
                                out = FHColorize.RGBAverage(im, out); //Randomly color
                            } else {
                                out = FHColorize.SeededRandom(out, out); //Randomly color
                            }
                            jim.setImage(out);
                        } else {
                            jim.setImage(FloatImage.makeImage(fir,fig,fib));
                        }

                    }
                });
            pg.notifyListeners("segment");

        } catch (IOException e) {

        }
    }

}