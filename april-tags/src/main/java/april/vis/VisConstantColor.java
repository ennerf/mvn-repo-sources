package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

public class VisConstantColor implements VisAbstractColorData, VisSerializable
{
    Color c;

    public VisConstantColor(Color c)
    {
        this.c = c;
    }

    public synchronized int size()
    {
        return -1;
    }

    public synchronized void bindColor(GL gl)
    {
        gl.glColor(c);
    }

    public synchronized void unbindColor(GL gl)
    {
        // nop
    }

    public synchronized void bindFill(GL gl)
    {
        gl.glColor(c);
    }

    public synchronized void unbindFill(GL gl)
    {
        // nop
    }

    public VisConstantColor(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeColor(c);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        c = ins.readColor();
    }


}
