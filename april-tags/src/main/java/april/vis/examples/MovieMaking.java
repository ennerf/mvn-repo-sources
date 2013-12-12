package april.vis.examples;

import april.vis.*;
import april.jmat.*;

import java.awt.*;
import java.awt.image.*;

import java.io.*;
import javax.swing.*;

/** Demonstrates manual creation of a movie in which frames are
 * triggered individually. The resulting movie can be converted by
 * using the ppms2mpeg script, like this:
 *
 * bash% ~/april/bin/ppms2mpeg4 20M 30 example_movie.ppms.gz
 *
 * (20M is the bitrate--- this is very high for "as high quality as
 * possible". 30 is the frame rate.)
 **/
public class MovieMaking
{
    public static void main(String args[])
    {
        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);

        VzGrid.addGrid(vw);
        vc.setSize(320,200);

        try {
            VisCanvas.Movie mov = vc.movieCreate("example_movie", false);

            for (int i = 0; i < 100; i++) {
                VisWorld.Buffer vb = vw.getBuffer("cube");
                vb.addBack(new VisChain(LinAlg.rotateZ(2 * Math.PI * i / 100.0),
                                        new VzBox(10, 10, 10, new VzMesh.Style(Color.cyan))));
                vb.swap();

                mov.addFrame();

                System.out.println(i);
            }

            mov.close();

        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }
    }
}
