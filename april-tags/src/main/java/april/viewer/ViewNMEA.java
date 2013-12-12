package april.viewer;

import lcm.lcm.*;
import april.lcmtypes.*;
import april.util.*;
import april.config.*;
import april.jmat.*;
import april.vis.*;
import april.util.*;

import java.io.*;
import java.awt.*;
import java.util.*;

public class ViewNMEA implements ViewObject, LCMSubscriber
{
    Viewer      viewer;
    String      name;
    Config      config;
    LCM         lcm = LCM.getSingleton();
    PoseTracker pt  = PoseTracker.getSingleton();

    long utime;
    double lat, lon, el;
    int fixtype;
    int nsats;

    public ViewNMEA(Viewer viewer, String name, Config config)
    {
        this.viewer = viewer;
        this.name = name;
        this.config = config;
        String channel = config.getString("channel", "NMEA");
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

    void messageReceivedEx(String channel, LCMDataInputStream ins) throws IOException
    {
        nmea_t nmea = new nmea_t(ins);

        if (nmea.nmea.startsWith("$GPGSV")) {
            String toks[] = nmea.nmea.split("[,*]");


        } else if (nmea.nmea.startsWith("$GPGGA")) {
            String toks[] = nmea.nmea.split("[,*]");
            try {
                lat = GPSUtil.decDegMin2DecDeg(Double.parseDouble(toks[2])*
                                               GPSUtil.getSign(toks[3].charAt(0)));
                lon = GPSUtil.decDegMin2DecDeg(Double.parseDouble(toks[4])*
                                               GPSUtil.getSign(toks[5].charAt(0)));
                el = Double.parseDouble(toks[9]);

                nsats = Integer.parseInt(toks[7]);
                fixtype = Integer.parseInt(toks[6]);

                utime = nmea.utime;

            } catch (Exception ex) {
                return;
            }
        }

        update();
    }

    String prettyFixType(int d)
    {
        switch(d) {
            case 0:
                return "No fix";
            case 1:
                return "2D/3D";
            case 2:
                return "DGPS";
            default:
                return ""+d;
        }
    }

    void update()
    {
        VisWorld.Buffer vb = viewer.getVisWorld().getBuffer("NMEA");

        String s = "<<sansserif-36,#ff6666>>No GPS Fix";

        if (fixtype != 0)
            s = String.format("lat: %f, lon: %f\n" +
                              "fix: %s, nsats: %d",
                              lat, lon,
                              prettyFixType(fixtype), nsats);

        vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.TOP_RIGHT,
                                    new VzText(VzText.ANCHOR.TOP_RIGHT,
                                               s)));
        vb.swap();
    }
}
