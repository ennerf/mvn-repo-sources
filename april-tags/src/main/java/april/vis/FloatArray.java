package april.vis;

import java.io.*;

public final class FloatArray implements VisSerializable
{
    float data[] = new float[16];
    int  pos; // index of next index to write to.

    public FloatArray()
    {
    }

    public FloatArray(float data[])
    {
        this.data = data;
        this.pos = data.length;
    }

    public void ensureSpace(int additionalCapacity)
    {
        if (pos + additionalCapacity < data.length)
            return;

        int newsize = 2 * data.length;

        while (newsize < pos + additionalCapacity)
            newsize *= 2;

        float f[] = new float[newsize];
        System.arraycopy(data, 0, f, 0, pos);
        data = f;
    }

    public void add(float f)
    {
        ensureSpace(1);
        data[pos++] = f;
    }

    public float[] getData()
    {
        return data;
    }

    public int size()
    {
        return pos;
    }

    public FloatArray(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeFloats(data);
        outs.writeInt(pos);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        data = ins.readFloats();
        pos = ins.readInt();
    }
}
