package april.util;

import java.io.*;
import java.util.*;

/** Interface for writing common types (floats, strings, etc.) via keys **/
public interface KeyedStructureWriter
{
    public void writeComment(String s) throws IOException;

    // not allowed to contain a newline
    public void writeString(String key, String s) throws IOException;

    public void writeInt(String key, int v) throws IOException;
    public void writeInts(String key, int v[]) throws IOException;
    public void writeIntsMatrix(String key, int v[][]) throws IOException;

    public void writeLong(String key, long v) throws IOException;
    public void writeLongs(String key, long v[]) throws IOException;
    public void writeLongsMatrix(String key, long v[][]) throws IOException;

    public void writeFloat(String key, float v) throws IOException;
    public void writeFloats(String key, float v[]) throws IOException;
    public void writeFloatsMatrix(String key, float v[][]) throws IOException;

    public void writeDouble(String key, double v) throws IOException;
    public void writeDoubles(String key, double v[]) throws IOException;
    public void writeDoublesMatrix(String key, double v[][]) throws IOException;

    public void blockBegin(String name) throws IOException;
    public void blockEnd() throws IOException;

    public void close() throws IOException;
}
