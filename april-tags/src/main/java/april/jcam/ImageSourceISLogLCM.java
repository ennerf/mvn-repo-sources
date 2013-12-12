package april.jcam;

import java.io.*;
import java.util.*;

import april.util.*;

import lcm.lcm.*;
import lcm.util.*;
import april.lcmtypes.*;

/** EXPERIMENTAL LCM-based ISLog ImageSource. This ImageSource will read frames
  * when directed to by LCM via a message (a url_t) that specifies the name of the
  * ImageSourceLog (ISLog) and the location of the frame within the log.
  * <br>
  * <br>
  * <font color=red><b>CAVEATS</b>:</font>
  * <br>
  * The ImageSource API does not provide a mechanism to return a frame and an
  * ImageSourceFormat at the same time. However, ISLogs contain an
  * ImageSourceFormat for every image and this format could change over time.
  * Therefore, the user must call getFormat() after every successful getFrame()
  * call in order to know the format for image decoding. The ImageSource API
  * also does not provide a mechanism for returning timestamps, whereas they
  * are available in the ISLog event. The user may call getTimestamp() after
  * every successful getFrame() call, as well.
  */
public class ImageSourceISLogLCM extends ImageSource implements LCMSubscriber
{
    LCM lcm = LCM.getSingleton();

    String basepath = null;
    String channel = null;

    BlockingSingleQueue<url_t> queue = new BlockingSingleQueue<url_t>();

    ISLog log = null;
    ImageSourceFormat lastIfmt = null;

    boolean warned = false;

    public ImageSourceISLogLCM(String url)
    {
        URLParser up = new URLParser(url);

        // should be an 'islog-lcm'
        String protocol = up.get("protocol");
        assert(protocol.equals("islog-lcm"));

        // the directory of .islog files
        basepath = up.get("network");
        assert(new File(basepath).isDirectory());
        if (!basepath.endsWith("/"))
            basepath = basepath + "/";

        // the lcm channel for url_t messages
        channel = up.get("channel", "ISLOG");

        lcm.subscribe(channel, this);
    }

    public boolean isFeatureAvailable(int idx)
    {
        return false;
    }

    public String getFeatureType(int idx)
    {
        return "";
    }

    public synchronized void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try {
            if (channel.equals(this.channel))
                queue.put(new url_t(ins));

        } catch (IOException ex) {
            System.err.println("ImageSourceISLogLCM messageReceived IOException: " + ex);
        } catch (IllegalArgumentException ex) {
            System.err.println("ImageSourceISLogLCM messageReceived IllegalArgumentException: " + ex);
        }
    }

    public ISLog.ISEvent readImage(url_t url) throws IOException, IllegalArgumentException
    {
        URLParser parser = new URLParser(url.url);

        // verify that we're reading the correct log
        ensureLogOpen(parser);

        // get offsets
        String offsetStr = parser.get("offset");
        assert(offsetStr != null);

        // read the frame
        return log.readAtPosition(Long.valueOf(offsetStr));
    }

    private void ensureLogOpen(URLParser up) throws IOException
    {
        String protocol = up.get("protocol");
        if (protocol == null)
            throw new IOException();

        String location = up.get("network");
        if (location == null)
            throw new IOException();

        String path = basepath + location;
        String newCanonicalPath = (new File(path)).getCanonicalPath();

        // close old log if it's time for the new one
        if (log != null) {

            String oldCanonicalPath = (new File(log.getPath())).getCanonicalPath();

            if (!newCanonicalPath.equals(oldCanonicalPath)) {
                log.close();
                log = null;
            }
        }

        // we don't have a log! open one!
        if (log == null) {
            System.out.printf("Opening log at path '%s'\n", path);
            log = new ISLog(path, "r");
        }
    }

    /** Wait for a new image or use the last unused image if one exists. Return the
      * byte buffer and save ImageSourceFormat and timestamp for later.
      * <br>
      */
    public FrameData getFrame()
    {
        // get latest image url
        url_t url = queue.get();

        // actually read the image
        ISLog.ISEvent event = null;
        try {
            event = readImage(url);
        } catch (Exception ex) {
            System.err.println("ImageSourceISLogLCM exception while reading image: " + ex);
            return null;
        }

        lastIfmt = event.ifmt;

        FrameData frmd = new FrameData();
        frmd.ifmt = event.ifmt;
        frmd.data = event.buf;
        frmd.utime = event.utime;

        return frmd;
    }

    /** Returns the LAST image's format. Returns null if no image has been read.
      */
    public ImageSourceFormat getFormat(int idx)
    {
        assert(idx == 0);

        // if we don't have a format, we have to return null
        if (lastIfmt == null) {
            if (!warned) {
                warned = true;
                System.out.println("Warning: ImageSourceISLogLCM.getFormat() returning null because no frame has been received");
            }

            return null;
        }

        return lastIfmt;
    }

    ////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////

    public void start()
    {
    }

    public void stop()
    {
    }

    public int getNumFormats()
    {
        return 1;
    }

    public void setFormat(int idx)
    {
        assert(idx == 0);
    }

    public int getCurrentFormatIndex()
    {
        return 0;
    }

    public void printInfo()
    {
        System.out.printf("========================================\n");
        System.out.printf(" ImageSourceISLogLCM Info\n");
        System.out.printf("========================================\n");
        System.out.printf("\tBase path: %s\n", basepath);
        System.out.printf("\tChannel: %s\n", channel);
    }

    public int close()
    {
        if (log != null) {
            try {
                log.close();
                return 0;

            } catch (IOException ex) {
                return 1;
            }
        }

        return 0;
    }
}
