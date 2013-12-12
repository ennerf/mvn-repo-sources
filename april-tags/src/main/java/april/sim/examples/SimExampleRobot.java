package april.sim.examples;


import java.io.*;
import java.awt.Color;
import java.util.*;
import java.awt.image.*;

import april.sim.*;
import april.lcm.*;
import april.vis.*;
import april.jmat.*;
import april.util.*;
import april.lcmtypes.*;

import lcm.lcm.*;

public class SimExampleRobot implements SimObject
{
    static final double robot_radius = .4;
    static final double laser_height = .087;
    static final double laser_width = .06;
    static final double camera_height = .035;

    DifferentialDrive drive;
    PeriodicTasks tasks = new PeriodicTasks(2);
    SimWorld sw;

    public SimExampleRobot(SimWorld _sw)
    {
        sw = _sw;

        drive = new DifferentialDrive(sw,this,new double[3]);
        drive.baseline = robot_radius*1.8; // wheels aren't quite at the edge of our platform
        drive.wheelDiameter = drive.baseline/3.0; // would take 3 wheels to span from one edge to the other

        tasks.addFixedRate(new ControlTask(), .02); // 50Hz
        tasks.addFixedRate(new LidarTask(), .025); // 40 Hz
        tasks.addFixedRate(new CameraTask(),.066); // 15 Hz
    }

    public void setRunning(boolean run)
    {
        tasks.setRunning(run);
        drive.setRunning(run);
    }


    /** Where is the object? (4x4 matrix). It is safe to return your internal representation. **/
    public double[][] getPose()
    {
        return LinAlg.quatPosToMatrix(drive.poseTruth.orientation,
                                      drive.poseTruth.pos);
    }

    public void setPose(double T[][])
    {
        drive.poseTruth.orientation = LinAlg.matrixToQuat(T);
        drive.poseTruth.pos = new double[] { T[0][3], T[1][3], 0 };
    }

    // Model the robot as a sphere for collision purposes
    public Shape getShape()
    {
        return new SphereShape(robot_radius);
    }

    // Display the robot as a simple wedge for display purposes
    public VisObject getVisObject()
    {
        return new VisChain(new VisChain(LinAlg.scale(robot_radius*2),new VzRobot()),
                            LinAlg.translate(laser_width/2 - robot_radius,0,laser_height/2),
                            new VisChain(LinAlg.scale(.06,.06,laser_height),new VzBox( new VzMesh.Style(new Color(255,65,5)))),
                            LinAlg.translate(-laser_width/2 + .01,0,laser_height/2 + camera_height/2),
                            new VisChain(LinAlg.scale(.02,camera_height,camera_height), new VzCamera(new VzMesh.Style(Color.black))));
    }

    // We expect the save file to contain two xyzrpy arrays,
    // one for our "true" position, and another for our odometry estimated one
    public void read(StructureReader ins) throws IOException
    {
        synchronized(drive) {
            double t_6dof[] = ins.readDoubles(); // truth
            double o_6dof[] = ins.readDoubles(); // odom

            drive.poseTruth.orientation = LinAlg.rollPitchYawToQuat(new double[]{t_6dof[3],t_6dof[4],t_6dof[5]});
            drive.poseTruth.pos = new double[]{t_6dof[0],t_6dof[1],t_6dof[2]};


            drive.poseOdom.orientation = LinAlg.rollPitchYawToQuat(new double[]{o_6dof[3],o_6dof[4],o_6dof[5]});
            drive.poseOdom.pos = new double[]{o_6dof[0],o_6dof[1],o_6dof[2]};
        }

    }

    public void write(StructureWriter outs) throws IOException
    {
        synchronized(drive) {
            outs.writeComment("XYZRPY Truth");
            outs.writeDoubles(LinAlg.quatPosToXyzrpy(drive.poseTruth.orientation,drive.poseTruth.pos));
            outs.writeComment("XYZRPY Odom");
            outs.writeDoubles(LinAlg.quatPosToXyzrpy(drive.poseOdom.orientation,drive.poseOdom.pos));
        }
    }

    public class ControlTask implements PeriodicTasks.Task, LCMSubscriber
    {
        ExpiringMessageCache<gamepad_t> gamepadCache = new ExpiringMessageCache<gamepad_t>(0.25);

        public ControlTask()
        {
            LCM.getSingleton().subscribe("GAMEPAD",this);
        }

        public void messageReceived(LCM lcm, String channel, LCMDataInputStream dins)
        {
            try {
                if (channel.equals("GAMEPAD")) {
                    gamepad_t gp = new gamepad_t(dins);
                    gamepadCache.put(gp,gp.utime);

                }
            } catch(IOException e) {}
        }

        // Update & report position
        public void run(double dt)
        {
            gamepad_t gp = gamepadCache.get();
            double cmd[] = new double[2]; // default to 0 voltage
            if (gp != null) {

                final int RIGHT_VERT_AXIS = 3;
                final int RIGHT_HORZ_AXIS = 2;

                double speed = -gp.axes[RIGHT_VERT_AXIS];
                double turn = gp.axes[RIGHT_HORZ_AXIS];


                if (true) { // Use D-PAD when in use, also enables compatability with KeyboardGamepad
                    if (Math.abs(gp.axes[5]) > Math.abs(speed))
                        speed = -gp.axes[5];
                    if (Math.abs(gp.axes[4]) > Math.abs(turn))
                        turn = gp.axes[4]*.25; // Don't want to turn out of control!
                }


                if (gp.buttons != 0) { // check 'estop'
                    cmd = new double[] { speed + turn, speed - turn };
                }

                if ((gp.buttons&(16|64)) != 0) // turbo boost
                    cmd = LinAlg.scale(cmd,4);

            }
            drive.motorCommands = cmd; // set voltages

            pose_t pose = drive.poseOdom.copy();
            pose.utime = TimeUtil.utime();

            LCM.getSingleton().publish("POSE",pose);
        }

    }


    class LidarTask implements PeriodicTasks.Task
    {
        HashSet<SimObject> ignore = new HashSet<SimObject>();

        public LidarTask()
        {
            ignore.add(SimExampleRobot.this);
        }

        public void run(double dt)
        {
            laser_t laser = new laser_t();

            double T[][] = LinAlg.matrixAB(getPose(),LinAlg.translate(laser_width/2 - robot_radius,0,laser_height/2));
            laser.utime = TimeUtil.utime();
            laser.nranges = 1080;
            laser.rad0 = (float)(-3*Math.PI/4);
            laser.radstep = (float)((3*Math.PI/2)/laser.nranges);
            laser.ranges = LinAlg.copyFloats(Sensors.laser(sw, ignore, T,
                                                           laser.nranges,laser.rad0,laser.radstep, 31.0));

            for (int i = 0; i < laser.ranges.length; i++)
                if (laser.ranges[i] > 30.0)
                    laser.ranges[i] = 0.0f;

            LCM.getSingleton().publish("LIDAR",laser);
        }
    }

    class CameraTask implements PeriodicTasks.Task
    {
        HashSet<SimObject> ignore = new HashSet<SimObject>();

        public CameraTask()
        {
            ignore.add(SimExampleRobot.this);
        }

        public void run(double dt)
        {

            VisWorld vw = new VisWorld();
            VisWorld.Buffer vb = vw.getBuffer("to-draw");
            VzGrid.addGrid(vw, new VzGrid(new VzMesh.Style(Color.gray)));
            synchronized(sw) {
                for (SimObject so :  sw.objects)
                    if (!ignore.contains(so))
                        vb.addBack(new VisChain(so.getPose(),so.getVisObject()));
            }
            vb.swap();

            // Camera image is based on true position of robot
            double T[][] = LinAlg.quatPosToMatrix(drive.poseTruth.orientation,
                                                  drive.poseTruth.pos);

            // Compute camera position and look direction
            double eye[] = LinAlg.transform(T,new double[]{.01 - robot_radius,0,laser_height + camera_height/2});
            double lookAt[] = LinAlg.transform(T,new double[]{1,0,laser_height + camera_height/2});
            double up[] = {0,0,1};//LinAlg.subtract(LinAlg.transform(T,new double[]{0,0,1}), eye);

            // Retrieve the rendered image
            BufferedImage img = Sensors.camera(vw,eye,lookAt,up, 67, 480, 360);// small image
            img = ImageUtil.convertImage(img, BufferedImage.TYPE_INT_RGB); // 4BYTE_ABGR_PRE not supported by java JPEG

            try {
                image_t img_t = image_t_util.encodeJPEG(img, 0.5f);
                img_t.utime = TimeUtil.utime();

                LCM.getSingleton().publish("CAMERA",img_t);
            } catch(IOException e){}

        }
    }
}