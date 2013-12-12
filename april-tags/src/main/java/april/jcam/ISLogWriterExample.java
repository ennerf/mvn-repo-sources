package april.jcam;

import java.io.*;

import lcm.lcm.LCM;

import april.jcam.*;
import april.util.GetOpt;

import april.lcmtypes.url_t;

/**
 * Opens camera and saves images to ISLog and (may) publish url_t LCM messages
 *
 * Use ImageSourceISLogLCM for playback
 **/
public class ISLogWriterExample
{
    String logname;
    String channel;

    ISLog logWriter;
    ImageSource imgsrc;

    LCM lcm = null;

    public ISLogWriterExample(String url, String logname, String channel)
    {
        this(url, logname, channel, false);
    }

    public ISLogWriterExample(String url, String logname, String channel, boolean verbose)
    {
        assert(logname.endsWith(".islog"));  // policy

        this.logname = logname;
        this.channel = channel;

        if (channel != null)
            lcm = LCM.getSingleton();

        // open logwriter
        try {
            logWriter = new ISLog(logname, "w");
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
            return;
        }

        // open camera
        try {
            if (verbose)
                System.out.println("Attempting to open " + url);
            imgsrc = ImageSource.make(url);
            imgsrc.start();
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
            return;
        }
    }

    public void run()
    {
        long offset = 0;

        while (true) {
            FrameData fdata = imgsrc.getFrame();
            if (fdata == null) {
                System.out.println("WRN: Invalid frame");
                continue;
            }
            try {
                offset = logWriter.write(fdata);
            } catch (IOException ex) {
                System.out.println("Ex: "+ex);
                continue;
            }
            if (channel != null)
                publish(fdata.utime, offset);
        }
    }

    public void publish(long utime, long offset)
    {
        url_t url = new url_t();

        url.utime = utime;
        url.url = String.format("islog://%s?offset=%s", this.logname, Long.toString(offset));
        lcm.publish(channel, url);
    }

    public void close()
    {
        try {
            logWriter.close();
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }
    }

    public static void main(String args[])
    {
        GetOpt gopt = new GetOpt();

        gopt.addString('u', "url", "dc1394://", "ImageSource URL");
        gopt.addString('l', "logname", "tmp.islog", "Log filename");
        gopt.addString('c', "channel", null, "Channel name for url_t (null for no publish))");
        gopt.addBoolean('v', "verbose", false, "Be verbose");
        gopt.addBoolean('h', "help", false, "Show this help");

        if (!gopt.parse(args) || gopt.getBoolean("help")) {
            System.out.println("Usage: [options] [-u camera-url] [-l log-name]");
            gopt.doHelp();
            System.exit(0);
        }

        new ISLogWriterExample(gopt.getString("url"), gopt.getString("logname"),
                               gopt.getString("channel"), gopt.getBoolean("verbose")).run();
    }
}