package april.util;

import java.io.*;
import java.util.*;

public class BinaryStructureWriter implements StructureWriter
{
    DataOutputStream outs;

    // similar to the ISLOG ISMAGIC number
    public static final long BRACKET_BEGIN = 0x18923349ab10ea9bL;
    public static final long BRACKET_END   = 0x18923349ab10ea9cL;

    static boolean warned = false;

    public BinaryStructureWriter(BufferedOutputStream bos)
    {
        this.outs = new DataOutputStream(bos);
    }

    public void writeComment(String s) throws IOException
    {
        if (!warned) {
            System.err.println("Warning: BinaryStructureWriter does not support comments");
            warned = true;
        }
    }

    // *allowed* to contain a newline
    public void writeString(String s) throws IOException
    {
        if (s == null) {
            outs.writeInt(-1);
            return;
        }

        outs.writeInt(s.length());
        byte strbuf[] = s.getBytes();
        outs.write(strbuf, 0, strbuf.length);
    }

    public void writeInt(int v) throws IOException
    {
        outs.writeInt(v);
    }

    public void writeInts(int v[]) throws IOException
    {
        if (v == null) {
            outs.writeInt(-1);
            return;
        }

        outs.writeInt(v.length);
        for (int i=0; i < v.length; i++)
            outs.writeInt(v[i]);
    }

    public void writeLong(long v) throws IOException
    {
        outs.writeLong(v);
    }

    public void writeFloat(float v) throws IOException
    {
        outs.writeFloat(v);
    }

    public void writeFloats(float v[]) throws IOException
    {
        if (v == null) {
            outs.writeInt(-1);
            return;
        }

        outs.writeInt(v.length);
        for (int i=0; i < v.length; i++)
            outs.writeFloat(v[i]);
    }

    public void writeDouble(double v) throws IOException
    {
        outs.writeDouble(v);
    }

    public void writeDoubles(double v[]) throws IOException
    {
        if (v == null) {
            outs.writeInt(-1);
            return;
        }

        outs.writeInt(v.length);
        for (int i=0; i < v.length; i++)
            outs.writeDouble(v[i]);
    }

    public void writeMatrix(double v[][]) throws IOException
    {
        if (v == null) {
            outs.writeInt(-1); // rows
            outs.writeInt(-1); // cols
            return;
        }

        int rows = v.length;
        int cols = v[0].length;

        outs.writeInt(rows);
        outs.writeInt(cols);

        for (int i=0; i < rows; i++) {
            for (int j=0; j < cols; j++)
                outs.writeDouble(v[i][j]);
        }
    }

    public void writeBytes(byte b[]) throws IOException
    {
        if (b == null) {
            outs.writeInt(-1);
            return;
        }

        outs.writeInt(b.length);
        for (int i=0; i < b.length; i++)
            outs.writeByte(b[i]);
    }

    public void blockBegin() throws IOException
    {
        outs.writeLong(BRACKET_BEGIN);
    }

    public void blockEnd() throws IOException
    {
        outs.writeLong(BRACKET_END);
    }

    public void close() throws IOException
    {
        outs.close();
    }
}
