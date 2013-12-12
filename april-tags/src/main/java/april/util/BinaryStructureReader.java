package april.util;

import java.io.*;
import java.util.*;

public class BinaryStructureReader implements StructureReader
{
    DataInputStream ins;

    public BinaryStructureReader(BufferedInputStream bis)
    {
        this.ins = new DataInputStream(bis);
    }

    public int readInt() throws IOException
    {
        return ins.readInt();
    }

    public int[] readInts() throws IOException
    {
        try {
            int length = ins.readInt();

            if (length < 0)
                return null;

            int v[] = new int[length];
            for (int i=0; i < length; i++)
                v[i] = ins.readInt();

            return v;
        } catch (EOFException ex) {
            System.out.println("BinaryStructureReader:readInts: EOFException: " + ex);
        }

        return null;
    }

    public long readLong() throws IOException
    {
        return ins.readLong();
    }

    public float readFloat() throws IOException
    {
        return ins.readFloat();
    }

    public float[] readFloats() throws IOException
    {
        try {
            int length = ins.readInt();

            if (length < 0)
                return null;

            float v[] = new float[length];
            for (int i=0; i < length; i++)
                v[i] = ins.readFloat();

            return v;
        } catch (EOFException ex) {
            System.out.println("BinaryStructureReader:readFloats: EOFException: " + ex);
        }

        return null;
    }

    public double readDouble() throws IOException
    {
        return ins.readDouble();
    }

    public double[] readDoubles() throws IOException
    {
        try {
            int length = ins.readInt();

            if (length < 0)
                return null;

            double v[] = new double[length];
            for (int i=0; i < length; i++)
                v[i] = ins.readDouble();

            return v;
        } catch (EOFException ex) {
            System.out.println("BinaryStructureReader:readDoubles: EOFException: " + ex);
        }

        return null;
    }

    public double[][] readMatrix() throws IOException
    {
        try {
            int rows = ins.readInt();
            int cols = ins.readInt();

            if (rows < 0 || cols < 0)
                return null;

            double v[][] = new double[rows][cols];

            for (int i=0; i < rows; i++) {
                for (int j=0; j < cols; j++) {
                    v[i][j] = ins.readDouble();
                }
            }

            return v;
        } catch (EOFException ex) {
            System.out.println("BinaryStructureReader:readMatrix: EOFException: " + ex);
        }

        return null;
    }

    public String readString() throws IOException
    {
        try {
            int length = ins.readInt();

            if (length < 0)
                return null;

            byte strbuf[] = new byte[length];
            ins.readFully(strbuf);

            return new String(strbuf);
        } catch (EOFException ex) {
            System.out.println("BinaryStructureReader:readString: EOFException: " + ex);
        }

        return null;
    }

    public byte[] readBytes() throws IOException
    {
        try {
            int length = ins.readInt();

            if (length < 0)
                return null;

            byte b[] = new byte[length];
            for (int i=0; i < length; i++)
                b[i] = ins.readByte();

            return b;
        } catch (EOFException ex) {
            System.out.println("BinaryStructureReader:readBytes: EOFException: " + ex);
        }

        return null;
    }

    public void blockBegin() throws IOException
    {
        long v = ins.readLong();
        assert(v == BinaryStructureWriter.BRACKET_BEGIN);
    }

    public void blockEnd() throws IOException
    {
        long v = ins.readLong();
        assert(v == BinaryStructureWriter.BRACKET_END);
    }

    public void close() throws IOException
    {
        ins.close();
    }
}
