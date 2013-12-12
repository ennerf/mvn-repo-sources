package april.config;

import java.io.*;
import java.util.*;

import april.jmat.LinAlg;
import april.util.KeyedStructureWriter;

/** Writes a config structure as a human-readable text file. **/
public class ConfigFileWriter implements KeyedStructureWriter
{
    BufferedWriter outs;
    int indent = 0;

    public ConfigFileWriter(BufferedWriter outs)
    {
        this.outs = outs;
    }

    private void writeKey(String key) throws IOException
    {
        doIndent();
        outs.write(key + " = ");
    }

    String escapeString(String s)
    {
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c=='\n')
                sb.append("\\n");
            else if (c=='\r')
                sb.append("\\r");
            else if (c=='\\')
                sb.append("\\\\");
            else if (c=='\"')
                sb.append("\\\"");
            else
                sb.append(c);
        }

        return sb.toString();
    }

    /** Write an arbitrary string that might contain newlines or other
        troublesome characters.  The string is preserved exactly,
        including white space.
    **/
    public void writeString(String key, String s) throws IOException
    {
        writeKey(key);
        String escapedString = s.replace("\\", "\\\\").replace("\"", "\\\\\"").replace("\n","\\n");
        outs.write("\""+escapeString(s)+"\";\n");
    }

    public void writeComment(String s) throws IOException
    {
        if (s==null || s.length()==0)
            outs.write("\n");
        else {
            doIndent();
            outs.write("# "+s+"\n");
        }
    }

    public void writeInt(String key, int v) throws IOException
    {
        writeKey(key);
        outs.write(String.format("%d;\n\n", v));
    }

    public void writeLong(String key, long v) throws IOException
    {
        writeKey(key);
        outs.write(String.format("%d;\n\n", v));
    }

    public void writeFloat(String key, float v) throws IOException
    {
        writeKey(key);
        outs.write(String.format("%.10g;\n\n", v));
    }

    public void writeDouble(String key, double v) throws IOException
    {
        writeKey(key);
        outs.write(String.format("%.15g;\n\n", v));
    }

    public void writeInts(String key, int v[]) throws IOException
    {
        writeKey(key);
        writeIntsRaw(v);
        outs.write(";\n\n");
    }

    public void writeLongs(String key, long v[]) throws IOException
    {
        writeKey(key);
        writeLongsRaw(v);
        outs.write(";\n\n");
    }

    public void writeFloats(String key, float v[]) throws IOException
    {
        writeKey(key);
        writeFloatsRaw(v);
        outs.write(";\n\n");
    }

    public void writeDoubles(String key, double v[]) throws IOException
    {
        writeKey(key);
        writeDoublesRaw(v);
        outs.write(";\n\n");
    }

    private void writeIntsRaw(int v[]) throws IOException
    {
        outs.write("[ ");
        for (int i = 0; i < v.length; i++) {
            if (i > 0)
                outs.write(", ");
            outs.write(String.format("%d", v[i]));
        }
        outs.write(" ]");
    }

    private void writeLongsRaw(long v[]) throws IOException
    {
        outs.write("[ ");
        for (int i = 0; i < v.length; i++){
            if (i > 0)
                outs.write(", ");
            outs.write(String.format("%d", v[i]));
        }
        outs.write(" ]");
    }

    private void writeFloatsRaw(float v[]) throws IOException
    {
        outs.write("[ ");
        for (int i = 0; i < v.length; i++){
            if (i > 0)
                outs.write(", ");
            outs.write(String.format("%.10g", v[i]));
        }
        outs.write(" ]");
    }

    private void writeDoublesRaw(double v[]) throws IOException
    {
        outs.write("[ ");
        for (int i = 0; i < v.length; i++){
            if (i > 0)
                outs.write(", ");
            outs.write(String.format("%.15g", v[i]));
        }
        outs.write(" ]");
    }

    public void writeIntsMatrix(String key, int v[][]) throws IOException
    {
        writeKey(key);
        matrixBlockBegin();

        for (int i = 0; i < v.length; i++) {
            if (i > 0)
                outs.write(",");
            outs.write("\n");
            doIndent();
            writeIntsRaw(v[i]);
        }
        matrixBlockEnd();
    }

    public void writeLongsMatrix(String key, long v[][]) throws IOException
    {
        writeKey(key);
        matrixBlockBegin();

        for (int i = 0; i < v.length; i++) {
            if (i > 0)
                outs.write(",");
            outs.write("\n");
            doIndent();
            writeLongsRaw(v[i]);
        }
        matrixBlockEnd();
    }

    public void writeFloatsMatrix(String key, float v[][]) throws IOException
    {
        writeKey(key);
        matrixBlockBegin();

        for (int i = 0; i < v.length; i++) {
            if (i > 0)
                outs.write(",");
            outs.write("\n");
            doIndent();
            writeFloatsRaw(v[i]);
        }
        matrixBlockEnd();
    }

    public void writeDoublesMatrix(String key, double v[][]) throws IOException
    {
        writeKey(key);
        matrixBlockBegin();

        for (int i = 0; i < v.length; i++) {
            if (i > 0)
                outs.write(",");
            outs.write("\n");
            doIndent();
            writeDoublesRaw(v[i]);
        }
        matrixBlockEnd();
    }

    void doIndent() throws IOException
    {
        for (int i = 0; i < indent; i++)
            outs.write("    ");
    }

    private void matrixBlockBegin() throws IOException
    {
        outs.write("[");
        indent += 2;
        doIndent();
    }

    private void matrixBlockEnd() throws IOException
    {
        outs.write("];\n\n");
        indent -= 2;
        assert(indent >= 0);
    }

    public void blockBegin(String name) throws IOException
    {
        doIndent();
        outs.write(name + " {\n");
        indent++;
    }

    public void blockEnd() throws IOException
    {
        indent--;
        assert(indent >= 0);
        doIndent();
        outs.write("}\n\n");
    }

    public void close() throws IOException
    {
        outs.flush();
        outs.close();
    }

    public static void main(String args[]) throws IOException
    {
        String path = "./test.config";
        FileWriter _outs = new FileWriter(path);
        ConfigFileWriter outs = new ConfigFileWriter(new BufferedWriter(_outs));

        outs.blockBegin("s");

        outs.writeInt("good_int", 99);
        outs.writeInt("bad_int", 2147483647 + 2147483647 + 2);
        outs.writeLong("good_long", 2147483647L + 2147483647L + 2);
        outs.writeFloat("good_float", 99.0f);
        outs.writeFloat("bad_float", (float)1.79769313486231E308);
        outs.writeDouble("good_double", 1.79769313486231E308);
        outs.writeString("str", "where's the beef?");

        outs.blockEnd();
        outs.blockBegin("v");

        outs.writeInts("ints",       new int[]{1,2,3,4,5});
        outs.writeLongs("longs",     new long[]{1,2,3,4,5});
        outs.writeFloats("floats",   new float[]{1,2,3,4,5});
        outs.writeDoubles("doubles", new double[]{1,2,3,4,5});

        outs.blockEnd();
        outs.blockBegin("m");

        outs.writeComment("These are 2D matrices, but a trivial change could make this N-dimensional");
        outs.writeComment("\tThe interface supports jagged matrices");

        outs.writeIntsMatrix("ints_matrix",       new int[][]{{1,2,3,4,5},
                                                                {6,7,8,9}});
        outs.writeLongsMatrix("longs_matrix",     new long[][]{{1,2,3,4,5},
                                                                {6,7,8,9}});
        outs.writeFloatsMatrix("floats_matrix",   new float[][]{{1,2,3,4,5,6,7,8},
                                                                  {6,7,8,9}});

        outs.writeComment("here");
        outs.writeDoublesMatrix("doubles_matrix", new double[][]{{1,2,3,4,5},
                                                                   {6,7,8,9}});
        outs.blockEnd();
        outs.close();
        _outs.close();

        System.out.println("Successfully written");

        Config config = new ConfigFile(path);

        System.out.printf("good_int\t%d\nbad_int \t%d\ngood_long\t%d\n" +
                          "good_float\t%f\nbad_float\t%f\ngood_double\t%g\n",
                          config.requireInt("s.good_int"),   config.requireInt("s.bad_int"),
                          config.requireLong("s.good_long"),
                          config.requireDouble("s.good_float"), config.requireDouble("s.bad_float"),
                          config.requireDouble("s.good_double"));

        System.out.println("v.ints:");
        LinAlg.printTranspose(config.requireInts("v.ints"));
        System.out.println("v.longs:");
        LinAlg.printTranspose(config.requireLongs("v.longs"));
        System.out.println("v.floats:");
        LinAlg.printTranspose(config.requireFloats("v.floats"));
        System.out.println("v.doubles:");
        LinAlg.printTranspose(config.requireDoubles("v.doubles"));

        System.out.println("m.ints_matrix:");
        LinAlg.print(config.requireIntsMatrix("m.ints_matrix"));
        System.out.println("m.longs_matrix:");
        LinAlg.print(config.requireLongsMatrix("m.longs_matrix"));
        System.out.println("m.floats_matrix:");
        LinAlg.print(config.requireFloatsMatrix("m.floats_matrix"));
        System.out.println("m.doubles_matrix:");
        LinAlg.print(config.requireDoublesMatrix("m.doubles_matrix"));
    }
}
