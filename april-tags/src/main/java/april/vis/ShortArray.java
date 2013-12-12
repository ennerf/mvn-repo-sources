package april.vis;

public final class ShortArray
{
    short data[] = new short[16];
    int  pos; // index of next index to write to.

    public void ensureSpace(int additionalCapacity)
    {
        if (pos + additionalCapacity < data.length)
            return;

        int newsize = 2 * data.length;

        while (newsize < pos + additionalCapacity)
            newsize *= 2;

        short f[] = new short[newsize];
        System.arraycopy(data, 0, f, 0, pos);
        data = f;
    }

    public int bytesPerElement()
    {
        return 2;
    }

    public void add(short f)
    {
        ensureSpace(1);
        data[pos++] = f;
    }

    public short[] getData()
    {
        return data;
    }

    public int size()
    {
        return pos;
    }
}
