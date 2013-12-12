package april.image.corner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFrame;

import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import april.image.Corner;
import april.jmat.LinAlg;
import april.lcmtypes.laser_t;
import april.lcmtypes.pose_t;
import april.vis.*;

public class LaserExample implements LCMSubscriber
{
    LCM          lcm;

    JFrame       jf;
    VisWorld     vw = new VisWorld();
    VisLayer     vl = new VisLayer(vw);
    VisCanvas    vc = new VisCanvas(vl);

    LaserHarris  lh=new LaserHarris();

    laser_t lastLaser;
    pose_t lastPose;
    boolean hasLaser=false, hasPose=false;

    String laserName = "LIDAR_FRONT";
    String poseName = "POSE_TRUTH";
    ArrayList<double[]>allFeature=new ArrayList<double[]>();
    ArrayList<ArrayList>allPoint=new ArrayList<ArrayList>();

    public LaserExample() throws IOException
    {
        jf = new JFrame("SLAMv1.FeatureDetector");
        jf.setLayout(new BorderLayout());

        jf.add(vc, BorderLayout.CENTER);

        jf.setSize(800,600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
        lcm=new LCM();
        lcm.subscribe(laserName, this);
        lcm.subscribe(poseName, this);

        setCamera();
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        if (channel.equals(laserName))
        {
            try {
                lastLaser = new laser_t(ins);
            } catch (IOException e) {}
            hasLaser=true;
        }

        if (channel.equals(poseName))
        {
            try {
                lastPose = new pose_t(ins);
            } catch (IOException e) {}
            hasPose=true;
        }

        if (hasLaser&&hasPose)
        {
            double timeDif=lastLaser.utime-lastPose.utime;
            if (Math.abs(timeDif)<1000)
            {
                messageReceivedEx(lastLaser,lastPose);
            }
            else
            {
                if(timeDif>0)
                    hasPose=false;
                else
                    hasLaser=false;
            }
        }
    }

    void messageReceivedEx(laser_t l,pose_t pose)
    {
        ArrayList<Corner>harrisResults=lh.extractFeatures(l);
        double[] currentPose=lastPose.pos;
        currentPose[2]=LinAlg.quatToRollPitchYaw(lastPose.orientation)[2];
        VisWorld.Buffer vbFeature = vw.getBuffer("features");
        VisWorld.Buffer vbMap = vw.getBuffer("map");
        for (Corner c : harrisResults)
        {
            allFeature.add(LinAlg.transform(currentPose, new double[]{c.x,c.y}));
        }
        vbMap.addBack(new VzPoints(new VisVertexData(allFeature),
                                   new VzPoints.Style(Color.red, 4)));
        ArrayList<double[]>points=LaserHarris.laserToPoints(l, 10000, 0);
        allPoint.add(LinAlg.transform(currentPose, points));
        for (ArrayList<double[]>pointSet:allPoint)
            vbMap.addBack(new VzPoints(new VisVertexData(pointSet),
                                       new VzPoints.Style(Color.yellow, 1)));
        vbMap.swap();
        vbFeature.swap();
    }

    static VisCameraManager.CameraPosition view = new VisCameraManager.CameraPosition();
    static {
        view.perspective_fovy_degrees = 45;
        view.eye =    new double[] { 0, 0, 100};
        view.lookat = new double[] { 0, 0, 0};
        view.up =     new double[] { 0, 1, 0};
    }

    private void setCamera()
    {
        vl.cameraManager.goBookmark(view);

    }

    public static void main(String arg[])
    {
        try {
            new LaserExample();
        } catch (IOException e){}
    }
}
