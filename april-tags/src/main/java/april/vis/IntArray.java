package april.vis;

import java.io.*;

public final class IntArray implements VisSerializable
{
    int data[] = new int[16];
    int  pos; // index of next index to write to.

    public IntArray()
    {
    }

    public void ensureSpace(int additionalCapacity)
    {
        if (pos + additionalCapacity < data.length)
            return;

        int newsize = 2 * data.length;

        while (newsize < pos + additionalCapacity)
            newsize *= 2;

        int f[] = new int[newsize];
        System.arraycopy(data, 0, f, 0, pos);
        data = f;
    }

    public int get(int idx)
    {
        return data[idx];
    }

    public void add(int f)
    {
        ensureSpace(1);
        data[pos++] = f;
    }

    public int bytesPerElement()
    {
        return 4;
    }

    public int[] getData()
    {
        return data;
    }

    public int size()
    {
        return pos;
    }

    public IntArray(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeInts(data);
        outs.writeInt(pos);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        data = ins.readInts();
        pos = ins.readInt();
    }
}
