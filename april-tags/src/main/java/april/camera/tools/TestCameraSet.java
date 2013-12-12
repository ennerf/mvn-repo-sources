package april.camera.tools;

import java.io.*;

import april.camera.*;
import april.config.*;
import april.jmat.*;
import april.util.*;

public class TestCameraSet
{
    public static void main(String args[])
    {
        GetOpt opts  = new GetOpt();

        opts.addBoolean('h',"help",false,"See this help screen");
        opts.addString('c',"config","","Config file path");
        opts.addString('s',"child","","Child string");

        if (!opts.parse(args)) {
            System.out.println("Option error: "+opts.getReason());
	    }

        String configstr = opts.getString("config");
        String childstr  = opts.getString("child");

        if (opts.getBoolean("help") || configstr.isEmpty() || childstr.isEmpty()){
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(1);
        }

        try {
            Config config = new ConfigFile(configstr);

            new TestCameraSet(config.getChild(childstr));

        } catch (IOException ex) {
            System.out.println(ex);
        }
    }

    public TestCameraSet(Config config)
    {
        CameraSet cs = new CameraSet(config);

        int size = cs.size();

        for (int i=0; i < size; i++) {

            String name = cs.getName(i);
            Calibration cal = cs.getCalibration(i);
            double extrinsics[][] = cs.getExtrinsicsL2C(i);

            System.out.printf("Camera %d of %d (name: '%s')\n",
                              i+1, size, name);
            System.out.println("Intrinsics matrix");
            LinAlg.print(cal.copyIntrinsics());
            System.out.println("Extrinsics matrix (Local To Camera)");
            LinAlg.print(extrinsics);
            System.out.println();
        }
    }
}
