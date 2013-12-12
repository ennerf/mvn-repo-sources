package april.util;

import java.io.*;
import java.util.*;

/** Reads a structure from a human-readable text file. **/
public class TextStructureReader implements StructureReader
{
    CommentReader ins;

    static class CommentReader
    {
        BufferedReader ins;
        int lineNumber = 0;

        CommentReader(BufferedReader ins)
        {
            this.ins = ins;
        }

        public String readLine() throws IOException
        {
            while (true) {
                String line = ins.readLine();
                if (line == null)
                    return null;

                line = line.trim();
                lineNumber++;

                if (line.startsWith("#") || line.length()==0)
                    continue;

                return line;
            }
        }

        public void close() throws IOException
        {
            ins.close();
        }
    }

    public TextStructureReader(BufferedReader _ins)
    {
        ins = new CommentReader(_ins);
    }

    @Override
    public int readInt() throws IOException
    {
        return Integer.parseInt(ins.readLine());
    }

    @Override
    public int[] readInts() throws IOException
    {
        String line = ins.readLine();
        String toks[] = line.split("\\s+");
        assert(toks.length==2 && toks[0].equals("ivec"));
        int length = Integer.parseInt(toks[1]);

        if (length < 0)
            return null;

        if (length == 0)
            return new int[0];

        line = ins.readLine();
        toks = line.split("\\s+");

        assert(length == toks.length);

        int v[] = new int[toks.length];
        for (int i = 0; i < toks.length; i++)
            v[i] = Integer.parseInt(toks[i]);

        return v;
    }

    @Override
    public long readLong() throws IOException
    {
        return Long.parseLong(ins.readLine());
    }

    @Override
    public float readFloat() throws IOException
    {
        return Float.parseFloat(ins.readLine());
    }

    @Override
    public float[] readFloats() throws IOException
    {
        String line = ins.readLine();
        String toks[] = line.split("\\s+");
        assert(toks.length==2 && toks[0].equals("fvec"));
        int length = Integer.parseInt(toks[1]);

        if (length < 0)
            return null;

        if (length == 0)
            return new float[0];

        line = ins.readLine();
        toks = line.split("\\s+");

        assert(length == toks.length);

        float v[] = new float[toks.length];
        for (int i = 0; i < toks.length; i++)
            v[i] = Float.parseFloat(toks[i]);

        return v;
    }

    @Override
    public double readDouble() throws IOException
    {
        return Double.parseDouble(ins.readLine());
    }

    @Override
    public double[] readDoubles() throws IOException
    {
        String line = ins.readLine();
        String toks[] = line.split("\\s+");
        assert(toks.length==2 && toks[0].equals("vec"));
        int length = Integer.parseInt(toks[1]);

        if (length < 0)
            return null;

        if (length == 0)
            return new double[0];

        line = ins.readLine();
        toks = line.split("\\s+");

        assert(length == toks.length);

        double v[] = new double[toks.length];
        for (int i = 0; i < toks.length; i++)
            v[i] = Double.parseDouble(toks[i]);

        return v;
    }

    @Override
    public double[][] readMatrix() throws IOException
    {
        String line = ins.readLine();
        String toks[] = line.split("\\s+");
        assert(toks.length==3 && toks[0].equals("mat"));

        int rows = Integer.parseInt(toks[1]);
        int cols = Integer.parseInt(toks[2]);

        if (rows < 0)
            return null;

        double v[][] = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            line = ins.readLine();
            toks = line.split("\\s+");
            assert(toks.length == cols);
            for (int j = 0; j < cols; j++) {
                v[i][j] = Double.parseDouble(toks[j]);
            }
        }
        return v;
    }

    String unescapeString(String s)
    {
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c!='\\') {
                sb.append(c);
                continue;
            }

            i++;
            c = s.charAt(i);
            if (c=='n')
                sb.append('\n');
            else if (c=='r')
                sb.append('\r');
            else
                sb.append(c);
        }

        return sb.toString();
    }

    @Override
    public String readString() throws IOException
    {
        String s = ins.readLine();
        if (s == null)
            return null;

        if (s.length()==0 || s.charAt(0)!='\"')
            return s;

        return unescapeString(s.substring(1, s.length()-1));
    }

    @Override
    public byte[] readBytes() throws IOException
    {
        int nlines = readInt();
        String lines[] = new String[nlines];
        for (int i = 0; i < nlines; i++)
            lines[i] = ins.readLine();

        return Base64.decode(lines);
    }

    @Override
    public void blockBegin() throws IOException
    {
        String line = ins.readLine();
        assert(line.equals("{"));
    }

    @Override
    public void blockEnd() throws IOException
    {
        String line = ins.readLine();
        assert(line.equals("}"));
    }

    @Override
    public void close() throws IOException
    {
        ins.close();
    }

    public int getLineNumber()
    {
        return ins.lineNumber;
    }
}
