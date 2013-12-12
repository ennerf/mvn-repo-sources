package april.vis;

import java.awt.*;
import java.io.*;
import java.util.*;

public class ObjectWriter
{
    DataOutputStream outs;

    static class ObjectCache
    {
        HashMap<Object, Long> objectIDs = new HashMap<Object, Long>();
        long nextObjectID = 1;
    }

    ObjectCache cache = new ObjectCache();

    public ObjectWriter(DataOutputStream outs)
    {
        this.outs = outs;
    }

    // when recursively serializing an object, we encapsulate its data
    // in a private ByteArrayOutputStream in order to be more robust
    // if deserialization of that object fails. However, we want to
    // use the same cache for that recursive serialization.
    protected ObjectWriter(DataOutputStream outs, ObjectCache cache)
    {
        this.outs = outs;
        this.cache = cache;
    }

    public void writeBoolean(boolean v) throws IOException
    {
        outs.writeBoolean(v);
    }

    public void writeInt(int v) throws IOException
    {
        outs.writeInt(v);
    }

    public void writeByte(int v) throws IOException
    {
        outs.writeByte(v);
    }

    public void writeLong(long v) throws IOException
    {
        outs.writeLong(v);
    }

    public void writeFloat(float v) throws IOException
    {
        outs.writeFloat(v);
    }

    public void writeDouble(double v) throws IOException
    {
        outs.writeDouble(v);
    }

    public void writeFloats(float v[]) throws IOException
    {
        if (v == null) {
            outs.writeInt(-1);
            return;
        }

        outs.writeInt(v.length);
        for (int i = 0; i < v.length; i++)
            outs.writeFloat(v[i]);
    }

    public void writeDoubles(double v[]) throws IOException
    {
        if (v == null) {
            outs.writeInt(-1);
            return;
        }

        outs.writeInt(v.length);
        for (int i = 0; i < v.length; i++)
            outs.writeDouble(v[i]);
    }

    public void writeDoubleMatrix(double v[][]) throws IOException
    {
        if (v == null) {
            outs.writeInt(-1);
            return;
        }

        outs.writeInt(v.length);
        outs.writeInt(v[0].length);

        for (int i = 0; i < v.length; i++)
            for (int j = 0; j < v[0].length; j++)
                outs.writeDouble(v[i][j]);
    }

    public void writeInts(int v[]) throws IOException
    {
        if (v == null) {
            outs.writeInt(-1);
            return;
        }

        outs.writeInt(v.length);
        for (int i = 0; i < v.length; i++)
            outs.writeInt(v[i]);
    }

    public void writeBytes(byte v[]) throws IOException
    {
        if (v == null) {
            outs.writeInt(-1);
            return;
        }

        outs.writeInt(v.length);
        for (int i = 0; i < v.length; i++)
            outs.write(v[i]);
    }

    public void writeColor(Color c) throws IOException
    {
        outs.writeByte(c.getRed());
        outs.writeByte(c.getGreen());
        outs.writeByte(c.getBlue());
        outs.writeByte(c.getAlpha());
    }

    public void writeUTF(String s) throws IOException
    {
        outs.writeUTF(s);
    }

    public void writeObject(Object obj) throws IOException
    {
        if (obj == null) {
            outs.writeUTF("null");
            return;
        }

        // write the class name, e.g., april.vis.VzBox
        outs.writeUTF(obj.getClass().getName());

        // have we already written this object? (if so, provide a pointer)
        Long _id = cache.objectIDs.get(obj);

        if (_id != null) {
            outs.writeLong((long) _id);
            outs.writeByte(0); // no object follows (cached)
            // and we're done!
            return;
        }

        // write the object...
        long id = cache.nextObjectID++;
        cache.objectIDs.put(obj, id);

        outs.writeLong(id);

        if (obj instanceof VisSerializable) {
            outs.writeByte(1); // visserializable object follows
            ByteArrayOutputStream bouts = new ByteArrayOutputStream();
            DataOutputStream douts = new DataOutputStream(bouts);
            ((VisSerializable) obj).writeObject(new ObjectWriter(douts, cache));
            byte b[] = bouts.toByteArray();
            outs.writeInt(b.length);
            outs.write(b);

        } else {
            outs.writeByte(2); // no serialization follows
            System.out.println("no serialization for : "+obj+"; I hope a default constructor is okay.");
//            new Exception().printStackTrace();
        }
    }
}
