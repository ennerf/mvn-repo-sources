package april.camera.tools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.swing.*;

import april.camera.*;
import april.config.*;
import april.jcam.*;
import april.jmat.*;
import april.util.*;
import april.vis.*;

public class SphericalProjection
{
    public SphericalProjection(Config config, String imagepath, boolean planar) throws IOException
    {
        ////////////////////////////////////////
        // GUI init
        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);

        VisCameraManager cameraManager = vl.cameraManager;
        VisCameraManager.CameraPosition camPos = cameraManager.getCameraTarget();
        camPos.perspectiveness = 0;
        camPos.eye    = new double[] {    0,    0,   -1 }; // eye
        camPos.lookat = new double[] {    0,    0,    0 }; // lookat
        camPos.up     = new double[] {    0,   -1,    0 }; // up
        cameraManager.goUI(camPos);
        cameraManager.uiLookAt(camPos.eye, camPos.lookat, camPos.up, true);

        ((DefaultCameraManager) vl.cameraManager).interfaceMode = 3.0;

        JFrame jf = new JFrame("SphericalProjection");
        jf.setLayout(new BorderLayout());
        jf.setSize(800, 600);
        jf.add(vc);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);

        ////////////////////////////////////////
        // Input view
        System.out.println("Creating camera calibration");
        String classname = config.requireString("class");

        Object obj = ReflectUtil.createObject(classname, config);
        assert(obj != null);
        assert(obj instanceof Calibration);

        Calibration cal = (Calibration) obj;

        ////////////////////////////////////////
        // Load image
        System.out.println("Reading input image");
        BufferedImage in = ImageIO.read(new File(imagepath));
        in = ImageConvert.convertImage(in, BufferedImage.TYPE_INT_RGB);

        int data[] = ((DataBufferInt) (in.getRaster().getDataBuffer())).getData();
        int width  = in.getWidth();
        int height = in.getHeight();

        ////////////////////////////////////////
        // Draw
        VisVertexData vertexData = new VisVertexData();
        VisColorData colorData = new VisColorData();

        // Create vertices and get colors
        for (int iy = 0; iy < height; iy++) {
            for (int ix = 0; ix < width; ix++) {
                int index = iy*width + ix;
                int rgb = 0xFF000000 | data[index];

                double xy_dp[] = new double[] { ix, iy };

                double xyz_r[] = cal.pixelsToRay(xy_dp);

                if (planar) xyz_r = CameraMath.rayToPlane(xyz_r);
                else        xyz_r = CameraMath.rayToSphere(xyz_r);

                vertexData.add(xyz_r);
                colorData.add(rgb);
            }
        }

        // Pick triangles
        VisIndexData indexData = new VisIndexData();
        for (int iy = 0; iy+1 < height; iy++) {
            for (int ix = 0; ix+1 < width; ix++) {
                int i00 = (iy+0)*width + (ix+0);
                int i10 = (iy+0)*width + (ix+1);
                int i01 = (iy+1)*width + (ix+0);
                int i11 = (iy+1)*width + (ix+1);

                indexData.add(i00);
                indexData.add(i10);
                indexData.add(i11);

                indexData.add(i00);
                indexData.add(i11);
                indexData.add(i01);
            }
        }

        // Render
        VisWorld.Buffer vb;

        vb = vw.getBuffer("Projection");
        vb.addBack(new VisLighting(false,
                                   new VzMesh(vertexData,
                                              vertexData,
                                              indexData,
                                              VzMesh.TRIANGLES,
                                              new VzMesh.Style(colorData))));
        vb.swap();

        vb = vw.getBuffer("Origin");
        vb.addBack(new VzPoints(new VisVertexData(new double[3]),
                                new VzPoints.Style(Color.white, 8)));
        vb.swap();
    }

    public static void main(String args[])
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h',"help",false,"See the help screen");
        opts.addString('c',"config","","Config file path");
        opts.addString('s',"childstring","aprilCameraCalibration.camera0000","CameraSet child name (e.g. aprilCameraCalibration.camera0)");
        opts.addString('i',"image","","Image path");
        opts.addBoolean('p',"planar",false,"Draw as a plane instead of a sphere");

        if (!opts.parse(args)) {
            System.out.println("Option error: " + opts.getReason());
        }

        String configpath  = opts.getString("config");
        String childstring = opts.getString("childstring");
        String imagepath   = opts.getString("image");
        boolean planar     = opts.getBoolean("planar");

        if (opts.getBoolean("help") || configpath.isEmpty() || childstring.isEmpty() || imagepath.isEmpty()) {
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(-1);
        }

        try {
            Config config = new ConfigFile(configpath);
            Config child = config.getChild(childstring);

            new SphericalProjection(child, imagepath, planar);

        } catch (IOException ex) {
            System.err.println("Exception: " + ex);
            ex.printStackTrace();
            System.exit(-1);
        }
    }
}
