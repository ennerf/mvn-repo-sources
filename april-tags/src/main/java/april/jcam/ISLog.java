package april.jcam;

import java.io.*;

import april.util.*;

import lcm.util.BufferedRandomAccessFile;

public class ISLog
{
    // Depends on LCM...
    BufferedRandomAccessFile raf; //only used in reading mode

    DataOutputStream outs; // only used in writing mode
    long writeOffset = 0;

    public static final long ISMAGIC = 0x17923349ab10ea9aL;

    // 60MB buffer seems to be sufficient for 60fps@752x480. 120fps eventually overruns
    public static final int DEFAULT_BUFFER_SIZE = 60*1000*1000; //60 MB
    final String path, mode;

    // Mode is one of "r" or "w". "rw" is no longer supported
    public ISLog(File file, String mode, int bufferSize) throws IOException
    {
        this.path = file.getPath();
        this.mode = mode;

        if (mode.equals("w"))
            outs = new DataOutputStream(new ThreadedOutputStream(new FileOutputStream(file), bufferSize));
        else if (mode.equals("r"))
            raf = new BufferedRandomAccessFile(file, "rw");

        if (outs == null && raf == null)
            throw new IllegalArgumentException(String.format("Unsupported ISLog mode %s. Options are \"r\" and \"w\".", mode));
    }

    public ISLog(String path, String mode) throws IOException
    {
        this(new File(path), mode, DEFAULT_BUFFER_SIZE);
    }

    public ISLog(File file, String mode) throws IOException
    {
        this (file, mode, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Retrieves the path to the log file.
     * @return the path to the log file
     */
    public String getPath()
    {
        return path;
    }

    public static class ISEvent
    {
        /**
         * Byte offset in ISLog file for the start of this event
         **/
        public long                 byteOffset;
        /**
         * Time of message receipt, represented in microseconds since 00:00:00
         * UTC January 1, 1970.
         */
        public long                 utime;
        /**
         * Image format (width, height, encoding)
         **/
        public ImageSourceFormat    ifmt;
        /**
         * Image buffer
         **/
        public byte[]               buf;
    }

    public synchronized ISEvent readAtPosition(long position) throws IOException
    {
        raf.seek(position);
        return readNext();
    }

    public synchronized ISEvent readNext() throws IOException
    {
        long magic = 0;
        ISEvent e = new ISEvent();

        while (true)
        {
            int v = raf.readByte()&0xff;

            magic = (magic<<8) | v;

            if (magic != ISMAGIC)
                continue;

            // byte offset
            e.byteOffset = raf.getFilePointer() - (long) (Long.SIZE/8);

            // utime
            e.utime = raf.readLong();

            // image source format
            e.ifmt = new ImageSourceFormat();

            e.ifmt.width = raf.readInt();
            e.ifmt.height = raf.readInt();

            byte strbuf[] = new byte[raf.readInt()];
            raf.readFully(strbuf);
            e.ifmt.format = new String(strbuf);

            // image buffer
            e.buf = new byte[raf.readInt()];
            raf.readFully(e.buf);

            break;
        }

        return e;
    }

    /** Get position in percent
      **/
    public synchronized double getPositionFraction() throws IOException
    {
        return raf.getFilePointer()/((double) raf.length());
    }

    /** Jump to position in percent
      **/
    public synchronized void seekPositionFraction(double frac) throws IOException
    {
        raf.seek((long) (raf.length()*frac));
    }

    /** Get position as the RandomAccessFile offset
      **/
    public synchronized long getPosition() throws IOException
    {
        return raf.getFilePointer();
    }

    /** Set position with a RandomAccessFile offset
      **/
    public synchronized void seekPosition(long position) throws IOException
    {
        raf.seek(position);
    }

    public synchronized void close() throws IOException
    {
        if (raf != null)
            raf.close();
        if (outs != null)
            outs.close();
    }

    public synchronized long write(FrameData frmd) throws IOException
    {
        return write(frmd.ifmt, frmd.utime, frmd.data);
    }

    public synchronized long write(ImageSourceFormat ifmt, byte imbuf[]) throws IOException
    {
        return write(ifmt, TimeUtil.utime(), imbuf);
    }

    public synchronized long write(ImageSourceFormat ifmt, long utime, byte imbuf[]) throws IOException
    {
        if (outs == null)
            throw new IOException(String.format("Invalid mode %s for ISLog: write() prohibited\n", mode));
        assert(outs != null);

        // NOTE: If you add/remove anything, make sure to update the 'written' tally below
        outs.writeLong(ISMAGIC);
        outs.writeLong(utime);
        outs.writeInt(ifmt.width);
        outs.writeInt(ifmt.height);
        outs.writeInt(ifmt.format.length());
        byte strbuf[] = ifmt.format.getBytes();
        outs.write(strbuf, 0, strbuf.length);
        outs.writeInt(imbuf.length);
        outs.write(imbuf, 0, imbuf.length);

        long frameStartOffset = writeOffset;

        //NOTE: Be sure to update this!
        long written = 8 + 8 + 4 + 4 +4 + strbuf.length + 4 + imbuf.length;
        writeOffset += written;

        return frameStartOffset;
    }
}
