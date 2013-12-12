 package april.util;

import java.io.*;

// testing
import java.util.*;

// Specialized output stream designed to maximize throughput to disk for large writes.
// Stream guarantees to return quickly from write() calls as long as the buffer is not full. Buffer is written to disk by separate thread
// Caveats: 1) writes larger than the buffer size are not permitted. Consider wrapping this stream with a BufferedOutputStream
//          2) if the buffer is full, the write() call will block until there is sufficient free space in the buffer
public class ThreadedOutputStream extends FilterOutputStream
{

    final OutputStream parent;

    // This buffer can contain at most buffer.length -1 bytes
    // -- this solves the ambiguity where start=end, which could be either be a completely full or empty buffer

    final byte buffer[];
    int start, end; // the valid data in the buffer starts at 'start' and continues to end, potentially wrapping around the end of the buffer
    // the 'start' index can only be updated in the write thread, and the 'end' index only updated by the calling thread

    // signals sent to the write thread
    boolean closed, flush;

    boolean once = true;

    WriteThread writeThread = new WriteThread();

    IOException ex = null; // This exception may be thrown inside the writer thread, will be thrown on next write() call, etc

    public ThreadedOutputStream(OutputStream parent)
    {
        this(parent, 512);
    }

    public ThreadedOutputStream(OutputStream parent, int bufferSize)
    {
        super(parent);
        this.parent = parent;
        buffer = new byte[bufferSize];
        start = 0;
        end = 0;

        writeThread.start();
    }

    public void write(byte buf[]) throws IOException
    {
        write(buf,0, buf.length);
    }

    public void write (int b) throws IOException
    {
        write(new byte[]{(byte)b}, 0, 1); // Not very efficient
    }

    public void write(byte buf[], int offset, int length) throws IOException
    {
        // Handle the case where the write is longer than the allocated buffer
        int max_size = buffer.length -1;
        if (length > max_size) {
            for (int i = 0; i < length; i+=max_size) {
                int len = Math.min(max_size, length - i);
                this.write(buf, offset+i, len);
            }
            return;
        }

        if (ex != null)
            throw ex;


        ensureSpace(length);

        for (int i = 0; i < length; i++) {
            int idx = (end+i) % buffer.length;
            buffer[idx] = buf[offset+i];
        }


        // Signal there's been data written
        synchronized(this) {
            end = (end + length) % buffer.length;
            this.notifyAll();
        }
    }


    public void flush() throws IOException
    {
        if (ex != null)
            throw ex;

        // long desiredWriteCount = writeCount + writeable();

        flush = true; // will be set back to false once the write thread processes

        // cause the write thread to wake, and write whatever is available.
        synchronized(this) {
            this.notifyAll();
        }

        // Now wait until the write thread has processed our flush command
        synchronized(writeThread) {
            while(flush) {
                if (ex != null)
                    throw ex;
                try {
                    writeThread.wait();
                } catch(InterruptedException e){}
            }
        }

        parent.flush();
    }

    public void close() throws IOException
    {
        flush();

        closed = true;
        synchronized(this) {
            this.notifyAll();
        }

        try {
            writeThread.join();
        } catch(InterruptedException e) {
        }
        parent.close();
    }

    // How much space can be written -- only call from writer thread
    public int writeable()
    {
        return (buffer.length  + end - start) % buffer.length;
    }


    // Returns a lower bound on how much space is available
    // does not block, should only be called from the calling thread
    // Note: max available is buffer.length -1
    public int available()
    {
        return (buffer.length + start -1 - end) % buffer.length;
    }

    public void ensureSpace(int length) throws IOException
    {
        if (length > buffer.length)
            throw new IOException(String.format("Unsupported write size: %d exceeds buffer size of %d\n", length, buffer.length));

        // return without blocking if there's space
        if (available() >= length)
            return;

        if (once) {
            once = false;
            System.out.printf("WRN: One-time warning: buffer overfull on write of size %d. Blocking until sufficient space\n",
                              length);
        }


        // otherwise, we need to wait until the writer thread signals there's room
        synchronized(writeThread) {
            while(available() < length) {
                if (ex != null)
                    throw ex;
                try {
                    writeThread.wait();
                } catch(InterruptedException e){}
            }
        }
    }


    private class WriteThread extends Thread
    {
        public void run()
        {
            byte b2[] = new byte[buffer.length];

            while(!closed) {
                int cur_end = -1;
                boolean cur_flush = false;
                // Get signal everytime there's new data
                synchronized(ThreadedOutputStream.this) {
                    while(writeable() == 0 && !flush && !closed)
                        try {
                            ThreadedOutputStream.this.wait();
                        } catch(InterruptedException e){}
                    if (closed)
                        break;
                    cur_flush = flush;
                    cur_end = end;
                }


                try {

                    // copy the data we need to write to 'b2', then
                    // XXX could speed this up with two memcpys instead?
                    int cur_len = (buffer.length + cur_end - start) % buffer.length;
                    for (int i = 0; i < cur_len; i++) {
                        b2[i] = buffer[ (start+i) % buffer.length];
                    }

                    parent.write(b2, 0, cur_len);

                } catch(IOException e) {
                    ex = e;
                    break;
                }

                synchronized(WriteThread.this) {
                    start = cur_end;
                    if (cur_flush)
                        flush = false;
                    WriteThread.this.notifyAll();
                }

            }

            // ensure no one is blocking on us anymore
            synchronized(WriteThread.this) {
                WriteThread.this.notifyAll();
            }
        }
    }


    public static void main(String args[]) throws IOException
    {
        if (args.length != 2) {
            System.out.println("Usage: java ThreadedOutputStream <buffersize> <output-file>\n");
        }

        int bufferSize = (int)Double.parseDouble(args[0]);
        File f = new File(args[1]);
        // writes 2GB of randomly sized writes
        Tic t = new Tic();
        long sz = (long)4e9;
        long wrote = 0;
        long last_print_write = 0;
        FileOutputStream fos = new FileOutputStream(f);
        OutputStream out = new ThreadedOutputStream(fos, bufferSize);

        int flushCount = 0;
        Random r = new Random(-34573924312l);
        while (wrote < sz) {
            byte b[] = new byte[752*480];//r.nextInt(bufferSize)];
            for (int i =0; i < b.length; i++)
                b[i] = (byte)(wrote + i);

            out.write(b);
            wrote += b.length;

            if (wrote > last_print_write + 100e6) {

                System.out.printf(" -- writing %.2f MB, avg time = %.2f MB/s flush count %d\n", (wrote - last_print_write)/1e6, ((wrote - last_print_write)/1e6)/t.toctic(), flushCount);
                last_print_write = wrote;

                out.flush(); // not necessary, but just regression testing
            }
        }
        System.out.printf(" ... closing stream\n");
        out.flush();
        fos.getFD().sync();
        out.close();
        System.out.printf(" -- wrote %.2f MB, avg time = %.2f MB/s bufferSize: %d bytes\n", wrote/1e6, (wrote/1e6)/t.totalTime(), bufferSize);
        f.delete();
    }

}