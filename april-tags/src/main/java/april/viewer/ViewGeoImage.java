package april.viewer;

import lcm.lcm.*;
import april.lcmtypes.*;
import april.util.*;
import april.config.*;
import april.vis.*;
import april.jmat.*;
import april.jmat.geom.*;

import java.io.*;
import java.awt.*;
import java.util.*;

public class ViewGeoImage implements ViewObject, LCMSubscriber
{
    Viewer viewer;
    String name;
    Config config;
    LCM         lcm = LCM.getSingleton();
    String channel;

    ArrayList<VzImage> vimages = new ArrayList<VzImage>();
    ArrayList<GeoImage> gimages = new ArrayList<GeoImage>();
    ArrayList<String> imagePaths = new ArrayList<String>();

    public ViewGeoImage(Viewer viewer, String name, Config config)
    {
        this.viewer = viewer;
        this.name = name;
        this.config = config;
        this.channel = config.getString("channel", "GPS_TIEPOINT");

        try {
            String files[] = config.requireStrings("files");
            for (String file : files) {
                GeoImage gim = new GeoImage(file, null);
                VzImage vim = new VzImage(gim.getImage());
                vim.modulateColor = new Color(255, 255, 255, config.getInt("alpha", 12));

                gimages.add(gim);
                vimages.add(vim);
                imagePaths.add(file);
            }
        } catch (Exception ex) {
            System.out.println("ex: "+ex);
        }

        lcm.subscribe(channel, this);

        update();
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            messageReceivedEx(channel, ins);
        } catch (IOException ex) {
            System.out.println("Exception: " + ex);
        }
    }

    void update()
    {
        for (int i = 0; i < imagePaths.size(); i++) {
            VisWorld.Buffer vb = viewer.getVisWorld().getBuffer("GeoImage: "+new File(imagePaths.get(i)).getName());
            vb.setDrawOrder(9900); // a bit higher priority than the vzgrid
            vb.addBack(new VisChain(gimages.get(i).getMatrix(), vimages.get(i)));
            vb.swap();
        }
    }

    void messageReceivedEx(String channel, LCMDataInputStream ins) throws IOException
    {
        gps_tiepoint_t tiepoint = new gps_tiepoint_t(ins);

        GPSLinearization gpslin = new GPSLinearization(tiepoint.lle,
                                                       new double[] { tiepoint.xyzt[0],
                                                                      tiepoint.xyzt[1],
                                                                      tiepoint.xyzt[3] });


        for (int i = 0; i < imagePaths.size(); i++)
            gimages.get(i).setGPSLinearization(gpslin);

        update();
    }
}
