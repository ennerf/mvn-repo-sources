package april.jcam;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import april.util.*;

/* EXPERIMENTAL ImageSource. Listens for images over a TCP connection using the format below.
   Intended for use as glue code to transfer images over localhost with arbitrary drivers.

   Example url to listen on port 7001: tcp://7001

   Format:
    - sync word     (8 bytes, 0x17923349ab10ea9aL )
    - utime         (8 bytes)
    - width         (4 bytes)
    - height        (4 bytes)
    - format length (4 bytes)
    - format string (format length bytes)
    - buffer length (4 bytes)
    - buffer        (buffer length bytes)
 */
public class ImageSourceTCP extends ImageSource
{
    public static final long MAGIC = 0x17923349ab10ea9aL;

    ReceiveThread rthread;

    final static int MAX_QUEUE_SIZE = 100;
    ArrayBlockingQueue<FrameData> queue = new ArrayBlockingQueue<FrameData>(MAX_QUEUE_SIZE*2);
    int numDropped = 0;

    ImageSourceFormat lastFmt;
    boolean warned = false;

    String url;

    public ImageSourceTCP(String _url)
    {
        this.url = _url;

        boolean isClientTCP = true;

        rthread = new ReceiveThread();

        rthread.start();

        try {
            rthread.blockUntilFrameReceived();
        } catch (InterruptedException ex) {
            System.out.println("Interrupted while waiting for a frame "+ex);
        }
    }

    ////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////

    public boolean isFeatureAvailable(int idx)
    {
        return false;
    }

    public String getFeatureType(int idx)
    {
        return "";
    }

    /** Wait for a new image or use the last unused image if one
      * exists. Return the byte buffer and save ImageSourceFormat and
      * timestamp for later.  <br>
      */
    public FrameData getFrame()
    {
        FrameData fd = null;

        try {
            fd = queue.take();
        } catch (Exception ex) {
            System.out.println("Exception during ArrayBlockingQueue.take(): " + ex);
        }

        return fd;
    }

    /** Returns the LAST image's format. Returns null if no image has been read.
      */
    public ImageSourceFormat getFormat(int idx)
    {
        assert(idx == 0);

        // if we don't have a format, we have to return null
        if (lastFmt == null) {
            try {
                rthread.blockUntilFrameReceived();
            } catch (InterruptedException ex) {
                System.out.println("interrupted while waiting for frame: "+ex);
            }
        }

        return lastFmt;
    }

    ////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////

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
        System.out.printf(" ImageSourceTCP Info\n");
        System.out.printf("========================================\n");
        System.out.printf("\tURL: %s\n", url);
    }

    public int close()
    {
        return 0;
    }

    ////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////
    private class ReceiveThread extends Thread
    {
        ReceiveThread()
        {
            this.setName("ImageSourceTCP ReceiveThread");
        }

        void reconnect()
        {
        }

        public void run()
        {
            Socket sock = null;
            ServerSocket serverSock = null;

            while (true) {
                // (Re)-connect
                System.out.println(url);

                try {
                    if (url.startsWith("tcp://")) {
                        // an outbound client connection.

                        String hostport = url.substring("tcp://".length());
                        int colon_pos = hostport.indexOf(":");
                        String host = (colon_pos >= 0) ? hostport.substring(0, colon_pos) : hostport;
                        if (host.length() == 0)
                            host = "localhost";

                        int port = (colon_pos >= 0) ? Integer.parseInt(hostport.substring(colon_pos+1)) : 7701;

                        sock = new Socket(host, port);
                    } else {
                        assert(url.startsWith("tcp-server://"));

                        if (serverSock == null) {
                            String port_string = url.substring("tcp-server://".length());
                            // an inbound server
                            int port = port_string.length() > 0 ? Integer.valueOf(port_string) : 7701;

                            serverSock = new ServerSocket(port);
                        }
                        System.out.println("Waiting for connection...");
                        sock = serverSock.accept();
                        System.out.println("... connected");
                    }
                } catch (IOException ex) {
                    System.out.println("Exception: "+ex);
                }

                if (sock != null)
                    readSock(sock);

                try {
                    Thread.sleep(250);
                } catch (InterruptedException ex) {
                }
            }
        }

        void blockUntilFrameReceived() throws InterruptedException
        {
            synchronized(this) {
                this.wait();
            }
        }

        void readSock(Socket sock)
        {
            try {
                DataInputStream ins = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
                long magic = 0;

                while (true) {

                    int v = ins.readByte() & 0xFF;
                    magic = (magic << 8) | v;

                    if (magic != MAGIC)
                        continue;

                    FrameData fd = new FrameData();

                    // utime
                    fd.utime = ins.readLong();

                    // format
                    fd.ifmt = new ImageSourceFormat();

                    fd.ifmt.width  = ins.readInt();
                    fd.ifmt.height = ins.readInt();

                    byte strbuf[] = new byte[ins.readInt()];
                    ins.readFully(strbuf);
                    fd.ifmt.format = new String(strbuf);

                    // image buffer
                    fd.data = new byte[ins.readInt()];
                    ins.readFully(fd.data);

                    lastFmt = fd.ifmt;

                    // add the queue
                    synchronized (queue) {

                        queue.add(fd);

                        while (queue.size() > MAX_QUEUE_SIZE) {
                            try {
                                queue.take();
                                numDropped++;
                            } catch (Exception ex) {
                                System.out.println("Exception while shrinking queue: " + ex);
                            }
                        }
                    }

                    synchronized(this) {
                        this.notifyAll();
                    }
                }

            } catch (EOFException ex) {
                System.out.println("Client disconnected");
            } catch (IOException ex) {
                System.out.println("ex: "+ex); ex.printStackTrace();
            }

            try {
                sock.close();
            } catch(IOException ex) {
                System.out.println("ex: "+ex);
            }
        }
    }
}
