package april.camera.tools;

import java.io.*;
import java.awt.image.*;
import javax.imageio.*;

import april.camera.*;
import april.camera.models.*;
import april.config.*;
import april.jcam.*;
import april.jmat.*;
import april.util.*;

public class ExampleRectifier
{
    View input;
    View output;
    Rasterizer      rasterizer;

    public ExampleRectifier(Config config, String imagepath, String rectifierclass, int maxdimension) throws IOException
    {
        ////////////////////////////////////////
        // Get output image path
        String classtoks[] = rectifierclass.split("\\.");
        String pathtoks[] = imagepath.split("\\.");
        String newpath = pathtoks[0];
        for (int i=1; i+1 < pathtoks.length; i++)
            newpath = String.format("%s.%s", newpath, pathtoks[i]);
        newpath = String.format("%s.%s.%s",
                                newpath, classtoks[classtoks.length-1], pathtoks[pathtoks.length-1]);
        System.out.printf("Output image path: '%s'\n", newpath);

        ////////////////////////////////////////
        // Load image
        System.out.println("Reading input image");
        BufferedImage in = ImageIO.read(new File(imagepath));
        in = ImageConvert.convertImage(in, BufferedImage.TYPE_INT_RGB);

        ////////////////////////////////////////
        // Input view
        System.out.println("Creating camera calibration");
        String classname = config.requireString("class");

        Object obj = ReflectUtil.createObject(classname, config);
        assert(obj != null);
        assert(obj instanceof Calibration);

        input = (Calibration) obj;
        //input = new ScaledView(0.5, input);

        ////////////////////////////////////////
        // Output view
        System.out.println("Creating rectified view");
        Object robj = null;
        try {
            robj = Class.forName(rectifierclass).getConstructor(new Class[] { Class.forName("april.camera.View") }).newInstance(input);
        } catch (Exception ex) {
            System.out.println("Exception when creating rectifier via reflection: "+ex);
            ex.printStackTrace();
            System.exit(-1);
        }
        assert(robj != null);
        assert(robj instanceof View);
        output = (View) robj;

        int maxOutputDimension = Math.max(output.getWidth(), output.getHeight());
        if (maxdimension > 0 && maxOutputDimension > maxdimension) {
            output = new ScaledView(((double) maxdimension) / maxOutputDimension,
                                    output);
        }

        ////////////////////////////////////////
        // Make rasterizer
        System.out.println("Creating rasterizer");
        Tic tic = new Tic();
        rasterizer = new BilinearRasterizer(input, output);
        System.out.printf("Took %.3f seconds to build rasterizer\n", tic.toc());
        //rasterizer = new NearestNeighborRasterizer(input, output);

        ////////////////////////////////////////
        // Rasterize image
        System.out.println("Rasterizing image");
        BufferedImage out = rasterizer.rectifyImage(in);

        ////////////////////////////////////////
        // Write image
        System.out.println("Writing image to file");
        ImageIO.write(out, "png", new File(newpath));

        System.out.println("Done!");
    }

    public static void main(String args[])
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h',"help",false,"See the help screen");
        opts.addString('c',"config","","Config file path");
        opts.addString('s',"childstring","aprilCameraCalibration.camera0000","CameraSet child name (e.g. aprilCameraCalibration.camera0)");
        opts.addString('r',"rectifier","april.camera.MaxRectifiedView","Rectifier class to use");
        opts.addString('i',"image","","Image path");
        opts.addInt('m',"maxdimension",-1,"Maximum image dimension after rectifying");

        if (!opts.parse(args)) {
            System.out.println("Option error: " + opts.getReason());
        }

        String configpath = opts.getString("config");
        String childstring = opts.getString("childstring");
        String rectifierclass = opts.getString("rectifier");
        String imagepath = opts.getString("image");
        int maxdim = opts.getInt("maxdimension");

        if (opts.getBoolean("help") || configpath.isEmpty() || childstring.isEmpty() || imagepath.isEmpty()) {
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(-1);
        }

        try {
            Config config = new ConfigFile(configpath);
            Config child = config.getChild(childstring);

            new ExampleRectifier(child, imagepath, rectifierclass, maxdim);

        } catch (IOException ex) {
            System.err.println("Exception: " + ex);
            ex.printStackTrace();
            System.exit(-1);
        }
    }
}
