package april.camera;

import java.util.*;

import april.config.*;
import april.jmat.*;
import april.util.*;

/** Container class for camera calibration results. Includes both the
  * intrinsics and extrinsics (local to camera) of the calibrated cameras.
  */
public class CameraSet
{
    HashMap<String,Integer> nameMap = new HashMap<String,Integer>();
    ArrayList<Camera>   cameras = new ArrayList<Camera>();

    private static class Camera
    {
        public String       name;
        public Calibration  cal;
        public double[][]   L2C;
    }

    public CameraSet()
    {
    }

    /** Create a CameraSet from a config file block.
      */
    public CameraSet(Config config)
    {
        String names[] = config.requireStrings("names");

        for (String name : names) {
            Camera cam = new Camera();
            cam.name = name;

            Config child = config.getChild(name);

            // Calibration object
            String classname = child.requireString("class");

            Object obj = ReflectUtil.createObject(classname, child);
            assert(obj != null);
            assert(obj instanceof Calibration);

            Calibration cal = (Calibration) obj;
            cam.cal = cal;

            // Extrinsics
            double xyz[] = child.requireDoubles("extrinsics.position");
            double rpy[] = child.requireDoubles("extrinsics.rollpitchyaw_degrees");
            double ext[][] = LinAlg.xyzrpyToMatrix(new double[] { xyz[0]             ,
                                                                  xyz[1]             ,
                                                                  xyz[2]             ,
                                                                  rpy[0]*Math.PI/180 ,
                                                                  rpy[1]*Math.PI/180 ,
                                                                  rpy[2]*Math.PI/180 });
            cam.L2C = ext;

            // Add to list
            cameras.add(cam);

            // Hashmap
            int idx = cameras.size() - 1;
            nameMap.put(name, idx);
        }
    }

    public void addCamera(Calibration cal, double ext[][])
    {
        addCamera(cal, ext, null);
    }

    public void addCamera(Calibration cal, double ext[][], String name)
    {
        Camera cam = new Camera();

        cam.name = name;
        cam.cal = cal;
        cam.L2C = ext;

        cameras.add(cam);

        int idx = cameras.size() - 1;

        if (name != null)
            nameMap.put(name, idx);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Get data

    /** Number of cameras.
      */
    public int size()
    {
        return cameras.size();
    }

    /** Find the index for this camera name.
      */
    public int getIndex(String name)
    {
        Integer idx = nameMap.get(name);
        if (idx == null)
            return -1;

        return idx;
    }

    /** Get the name of this camera.
      */
    public String getName(int idx)
    {
        return cameras.get(idx).name;
    }

    /** Get Calibration object for this camera.
      */
    public Calibration getCalibration(String name)
    {
        Integer idx = nameMap.get(name);
        if (idx == null)
            return null;

        return getCalibration(idx);
    }

    /** Get Calibration object for this camera.
      */
    public Calibration getCalibration(int idx)
    {
        return cameras.get(idx).cal;
    }

    /** Extrinsics matrix to transform from the local frame (shared between cameras)
      * to the coordinate frame of this camera.
      */
    public double[][] getExtrinsicsL2C(String name)
    {
        Integer idx = nameMap.get(name);
        if (idx == null)
            return null;

        return getExtrinsicsL2C(idx);
    }

    /** Extrinsics matrix to transform from the local frame (shared between cameras)
      * to the coordinate frame of this camera.
      */
    public double[][] getExtrinsicsL2C(int idx)
    {
        return cameras.get(idx).L2C;
    }
}
