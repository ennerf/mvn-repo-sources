package april.camera.tools;

import java.util.*;
import java.io.*;

import april.camera.*;
import april.camera.models.*;
import april.camera.tools.*;
import april.camera.calibrator.*;
import april.config.*;
import april.jmat.*;
import april.util.*;

public class ComputeFieldOfView
{
    private static double xyzToTheta(double xyz_ar[], double xyz_br[])
    {
        double thetaa = Math.atan2(Math.sqrt(xyz_ar[0]*xyz_ar[0]+xyz_ar[1]*xyz_ar[1]), xyz_ar[2]);
        double thetab = Math.atan2(Math.sqrt(xyz_br[0]*xyz_br[0]+xyz_br[1]*xyz_br[1]), xyz_br[2]);
        double theta  = Math.abs(thetaa) + Math.abs(thetab);

        return Math.toDegrees(theta);
    }

    private static void computeFOV(Calibration cal)
    {
        int width = cal.getWidth();
        int height = cal.getHeight();

        double HFOV = Double.NaN;
        double VFOV = Double.NaN;
        double DFOV = Double.NaN;

        // HFOV
        {
            double a_dp[] = new double[] { 0    , height/2 };
            double b_dp[] = new double[] { width, height/2 };

            double xyz_ar[] = cal.pixelsToRay(a_dp);
            double xyz_br[] = cal.pixelsToRay(b_dp);
            HFOV = xyzToTheta(xyz_ar, xyz_br);

            System.out.printf("   Horiz FOV ::"+
                              " xy (%4.0f, %4.0f)<->(%4.0f, %4.0f) ::"+
                              " %6.1f degrees\n",
                              a_dp[0], a_dp[1], b_dp[0], b_dp[1], HFOV);
        }

        // VFOV
        {
            double a_dp[] = new double[] { width/2, 0      };
            double b_dp[] = new double[] { width/2, height };

            double xyz_ar[] = cal.pixelsToRay(a_dp);
            double xyz_br[] = cal.pixelsToRay(b_dp);
            VFOV = xyzToTheta(xyz_ar, xyz_br);

            System.out.printf("   Vert  FOV ::"+
                              " xy (%4.0f, %4.0f)<->(%4.0f, %4.0f) ::"+
                              " %6.1f degrees\n",
                              a_dp[0], a_dp[1], b_dp[0], b_dp[1], VFOV);
        }

        // DFOV
        {
            double a_dp[] = new double[] { 0    , 0      };
            double b_dp[] = new double[] { width, height };

            double xyz_ar[] = cal.pixelsToRay(a_dp);
            double xyz_br[] = cal.pixelsToRay(b_dp);
            DFOV = xyzToTheta(xyz_ar, xyz_br);

            System.out.printf("   Diag  FOV ::"+
                              " xy (%4.0f, %4.0f)<->(%4.0f, %4.0f) ::"+
                              " %6.1f degrees\n",
                              a_dp[0], a_dp[1], b_dp[0], b_dp[1], DFOV);
        }

        System.out.printf("   Ratios DFOV/HFOV: %6.3f DFOV/VFOV: %6.3f\n", DFOV/HFOV, DFOV/VFOV);
    }

    public ComputeFieldOfView(String path, String childname) throws IOException
    {
        Config config = new ConfigFile(path);

        CameraSet cs = new CameraSet(config.getChild(childname));

        for (int i = 0; i < cs.size(); i++) {
            System.out.printf("Camera #%d ('%s'):\n", i, cs.getName(i));
            computeFOV(cs.getCalibration(i));
        }
    }

    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('c',"config","","Config file");
        opts.addString('s',"childstr","aprilCameraCalibration","Child block name");

        if (!opts.parse(args)) {
            System.out.println("Option error: "+opts.getReason());
            System.exit(1);
	    }

        String config   = opts.getString("config");
        String childstr = opts.getString("childstr");

        if (opts.getBoolean("help") || config.isEmpty() || childstr.isEmpty()) {
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        try {
            new ComputeFieldOfView(config, childstr);
        } catch (IOException ex) {
            System.out.println("Exception: " + ex);
            System.exit(1);
        }
    }
}
