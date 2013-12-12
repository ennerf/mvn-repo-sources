package april.jcam;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;

/* Bare-bones example class for reading images using jcam and publishing over tcp.

   Example url to send to localhost over port 7001: tcp://localhost:7001
 */
public class JCamTCPExample
{
    ImageSource isrc;

    volatile boolean running = true;

    DataOutputStream outs;

    public JCamTCPExample(ImageSource _isrc, String url)
    {
        isrc = _isrc;

        connect(url);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run()
            {
                running = false;
            }
        });
    }

    void connect(String url)
    {
        if (!url.startsWith("tcp://"))
            throw new IllegalArgumentException("JCamTCPExample only accepts tcp:// urls");

        url = url.substring("tcp://".length());
        int argidx = url.indexOf("?");
        if (argidx >= 0) {
            String arg = url.substring(argidx+1);
            url = url.substring(0, argidx);
        }

        // bind a connection with specified address
        String url_parts[] = url.split(":");
        String host = url_parts[0];
        int port = Integer.parseInt(url_parts[1]);
        try {
            Socket sock = new Socket(host, port);
            outs = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
        } catch (IOException e) {
            System.out.println("ERR: Ex: "+e);
            System.exit(1);
        }
    }

    void writeFrame(FrameData fd) throws IOException
    {
        // magic sync word
        outs.writeLong(ImageSourceTCP.MAGIC);

        // utime
        outs.writeLong(fd.utime);

        // format
        outs.writeInt(fd.ifmt.width);
        outs.writeInt(fd.ifmt.height);

        outs.writeInt(fd.ifmt.format.length());
        byte strbuf[] = fd.ifmt.format.getBytes();
        outs.write(strbuf, 0, strbuf.length);

        // image buffer
        outs.writeInt(fd.data.length);
        outs.write(fd.data, 0, fd.data.length);

        outs.flush();
    }

    public void run()
    {
        isrc.start();

        while (running) {
            FrameData frmd = isrc.getFrame(); // wait for the next frame

            // If something goes wrong with the camera, the frame data can be null
            if (frmd == null) {
                System.out.println("ERR: Image stream interrupted!");
                break;
            }

            try {
                writeFrame(frmd);
            } catch (IOException ex) {
                System.out.println("Exception writing frame to socket: " + ex);
                break;
            }
        }

        isrc.stop();
    }

    public static void main(String args[])
    {
        if (args.length != 2) {
            System.out.println("Usage: JCamTCPExample <image source camera url> <server url>");
            System.exit(1);
        }

        try {
            new JCamTCPExample(ImageSource.make(args[0]), args[1]).run();
        } catch (IOException ex) {
            System.err.println("ERR: Failed to create image source: " + ex);
            System.err.println("     Usage: java april.jcam.JCamTCPExample <image source url>");
            System.exit(-1);
        }
    }
}

