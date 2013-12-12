package april.vis;

import java.awt.*;
import java.io.*;
import javax.swing.*;

import april.jmat.*;

public class VzGridText implements VisObject, VisSerializable
{
    VzText vt;
    double displayTime = 1.5;
    double spacing[];

    VzGrid grid;

    VzText.ANCHOR anchor;
    String format;

    long lastUpdateTime;

    public VzGridText(VzGrid grid, VzText.ANCHOR anchor, String format)
    {
        this.grid = grid;
        this.anchor = anchor;
        this.format = format;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        if (grid.lastspacing == null)
            return;

        double lastspacing[] = grid.lastspacing;

        if (vt == null || grid.lastspacing == null || spacing == null ||
            grid.lastspacing[0] != spacing[0] || grid.lastspacing[1] != spacing[1] || vt == null) {

            spacing = LinAlg.copy(lastspacing);

            vt = new VzText(anchor, String.format(format, spacing[0], spacing[1]));
            lastUpdateTime = System.currentTimeMillis();
        }

        long now = System.currentTimeMillis();
        double dt = (now - lastUpdateTime) / 1000.0;

        if (dt < displayTime)
            vt.render(vc, layer, rinfo, gl);
    }

    public VzGridText(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeDouble(displayTime);
        outs.writeObject(grid);
        outs.writeUTF(anchor.name());
        outs.writeUTF(format);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        displayTime = ins.readDouble();
        grid = (VzGrid) ins.readObject();
        anchor = VzText.ANCHOR.valueOf(ins.readUTF());
        format = ins.readUTF();
    }
}
