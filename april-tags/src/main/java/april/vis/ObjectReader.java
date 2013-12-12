package april.vis;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class ObjectReader
{
    NestableInputStream nins;
    DataInputStream ins;

    static class ObjectCache
    {
        HashMap<Long, Object> objectIDs = new HashMap<Long, Object>();
    }

    static class NestableInputStream extends InputStream
    {
        byte b[];
        int offset;
        int length;
        int pos;     // relative to offset

        public NestableInputStream(byte b[], int offset, int length)
        {
            this.b = b;
            this.offset = offset;
            this.length = length;
            this.pos = 0;
        }

        public int available()
        {
            return (length - pos);
        }

        public int read()
        {
            if (pos + 1 <= length)
                return b[offset + pos++] & 0xff;
            return -1;
        }

        public int read(byte _b[], int _offset, int _len) throws IOException
        {
            if (pos + _len <= length) {
                System.arraycopy(b, offset + pos, _b, _offset, _len);
                pos += _len;
                return _len;
            }

            throw new EOFException();
        }

        public NestableInputStream getStream(int nbytes) throws IOException
        {
            if (pos + nbytes <= length) {
                NestableInputStream nins = new NestableInputStream(b, offset + pos, nbytes);
                pos += nbytes;
                return nins;
            }

            throw new EOFException("End of buffer "+nbytes+" "+pos+" "+length+" "+offset);
        };
    }

    ObjectCache cache = new ObjectCache();

    public ObjectReader(String path) throws IOException
    {
        File f = new File(path);

        DataInputStream dins = new DataInputStream(new GZIPInputStream(new FileInputStream(f)));
        byte b[] = new byte[16384];
        int bpos = 0;
        while (true) {
            int readlen = dins.read(b, bpos, b.length - bpos);

            if (readlen < 0)
                break;

            bpos += readlen;

            if (b.length == bpos) {
                byte nb[] = new byte[b.length*2];
                System.arraycopy(b, 0, nb, 0, b.length);
                b = nb;
            }
        }
        dins.close();

        this.nins = new NestableInputStream(b, 0, bpos);
        this.ins = new DataInputStream(nins);
    }

    protected ObjectReader(NestableInputStream nins, ObjectCache cache)
    {
        this.nins = nins;
        this.ins = new DataInputStream(nins);
        this.cache = cache;
    }

    public boolean readBoolean() throws IOException
    {
        return ins.readBoolean();
    }

    public int readInt() throws IOException
    {
        return ins.readInt();
    }

    public int readByte() throws IOException
    {
        return ins.readByte();
    }

    public long readLong() throws IOException
    {
        return ins.readLong();
    }

    public float readFloat() throws IOException
    {
        return ins.readFloat();
    }

    public double readDouble() throws IOException
    {
        return ins.readDouble();
    }

    public String readUTF() throws IOException
    {
        return ins.readUTF();
    }

    public Color readColor() throws IOException
    {
        int r = ins.readByte() & 0xff;
        int g = ins.readByte() & 0xff;
        int b = ins.readByte() & 0xff;
        int a = ins.readByte() & 0xff;

        return new Color(r, g, b, a);
    }

    public float[] readFloats() throws IOException
    {
        int len = readInt();
        if (len < 0)
            return null;

        float v[] = new float[len];
        for (int i = 0; i < v.length; i++)
            v[i] = readFloat();
        return v;
    }

    public double[] readDoubles() throws IOException
    {
        int len = readInt();
        if (len < 0)
            return null;

        double v[] = new double[len];
        for (int i = 0; i < v.length; i++)
            v[i] = readDouble();
        return v;
    }

    public int[] readInts() throws IOException
    {
        int len = readInt();
        if (len < 0)
            return null;

        int v[] = new int[len];
        for (int i = 0; i < v.length; i++)
            v[i] = readInt();
        return v;
    }

    public byte[] readBytes() throws IOException
    {
        int len = readInt();
        if (len < 0)
            return null;

        byte v[] = new byte[len];
        ins.readFully(v);
        return v;
    }

    public double[][] readDoubleMatrix() throws IOException
    {
        int a = readInt();
        if (a < 0)
            return null;

        int b = readInt();

        double v[][] = new double[a][b];
        for (int i = 0; i < v.length; i++)
            for (int j = 0; j < v[0].length; j++)
                v[i][j] = ins.readDouble();

        return v;
    }

    public Object readObject() throws IOException
    {
        String cls = ins.readUTF();

        if (cls.equals("null"))
            return null;

        long id = ins.readLong();

        int mode = ins.readByte()&0xff;

//        System.out.println("readObject: "+cls+" "+id+" "+mode);

        switch (mode) {
            case 0: {
                assert(cache.objectIDs.get(id) != null);
                return cache.objectIDs.get(id);
            }

            case 1: {
                Object obj = createObject(cls);
                cache.objectIDs.put(id, obj);

                int len = ins.readInt();

                NestableInputStream subnins = nins.getStream(len);
                ((VisSerializable) obj).readObject(new ObjectReader(subnins, cache));

                if (subnins.available() != 0)
                    System.out.printf("Warning: %d bytes remain after deserialization of %s\n", subnins.available(), cls);
                return obj;
            }

            case 2: {
                Object obj = createObjectEmpty(cls);
                cache.objectIDs.put(id, obj);
                return obj;
            }

            default: {
                assert(false);
                break;
            }
        }

        assert(false);
        return null;
    }

    public Object createObject(String className)
    {
        try {
            Class cls = Class.forName(className);
            Object o = cls.getConstructor(this.getClass()).newInstance(new Object[] { null } );
            return o;
        } catch (Exception ex) {
            System.out.println("ReflectUtil.createObject ex: "+ex);
            ex.printStackTrace();
            return null;
        }
    }

    public Object createObjectEmpty(String className)
    {
        try {
            Class cls = Class.forName(className);
            Object o = cls.getConstructor().newInstance();
            return o;
        } catch (Exception ex) {
            System.out.println("ReflectUtil.createObject ex: "+ex);
            ex.printStackTrace();
            return null;
        }
    }

}
