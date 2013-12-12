package april.vis;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import java.io.*;

import april.util.*;

import lcm.lcm.*;

public class VzGeoImageSet implements VisObject, VisSerializable
{
    GPSLinearization gpslin;
    ArrayList<Tile> tiles = new ArrayList<Tile>();
    String dirpath;
    Color  modulateColor;

    static class Tile
    {
        VzImage vim;
        double M[][];
    }

    public VzGeoImageSet(String dirpath, GPSLinearization gpslin, boolean asyncLoad) throws IOException
    {
        init(dirpath, gpslin, asyncLoad);
    }

    void init(String dirpath, GPSLinearization gpslin, boolean asyncLoad) throws IOException
    {
        this.gpslin = gpslin;
        this.dirpath = dirpath;
        this.modulateColor = Color.white;

        if (!asyncLoad)
            load();
        else
            new LoadThread().start();
    }

    public void setModulateColor(Color c)
    {
        this.modulateColor = c;
    }

    public GPSLinearization getGPSLinearization()
    {
        return gpslin;
    }

    public void render(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.glPushAttrib(gl.GL_ENABLE_BIT);

        if (true)
            gl.glDisable(GL.GL_LIGHTING);
        else
            gl.glEnable(GL.GL_LIGHTING);

        synchronized(tiles) {
            for (Tile tile : tiles) {
                gl.glPushMatrix();
                gl.glMultMatrix(tile.M);
                tile.vim.modulateColor = modulateColor;
                tile.vim.render(vc, vl, rinfo, gl);
                gl.glPopMatrix();
            }
        }

        gl.glPopAttrib();


    }

    class LoadThread extends Thread
    {
        public void run()
        {
            try {
                load();
            } catch (IOException ex) {
                System.out.println("ex: "+ex);
            }
        }
    }

    void load() throws IOException
    {
        File dir = new File(dirpath);
        if (!dir.exists()) {
            System.out.println("VisGeoImageSet: path not found: "+dirpath);
            return;
        }

        File files[] = null;

        if (dir.isDirectory())
            files = dir.listFiles();
        else
            files = new File[] { dir };

        Arrays.sort(files);

        for (File file : files) {
            if (file.getName().endsWith(".png")) {

                GeoImage geoim = new GeoImage(file.getPath(), gpslin);

                BufferedImage im = geoim.getImage();
                VisTexture vt = new VisTexture(im,  VisTexture.NO_REPEAT);// | VisTexture.NO_MAG_FILTER | VisTexture.NO_MIN_FILTER);
                //vis2 vt.lock();
                // double xy12[][]={{0,0},{im.getWidth(), im.getHeight()}};
                VzImage vim = new VzImage(vt,0);

                Tile tile = new Tile();
                tile.vim = vim;
                tile.M = geoim.getMatrix();

                synchronized(tiles) {
                    tiles.add(tile);
                }
            }
        }
    }

    // only for VisSerializable
    public VzGeoImageSet(ObjectReader in)
    {
    }


    public void writeObject(ObjectWriter out) throws IOException
    {
        out.writeUTF(dirpath);
        out.writeDouble(gpslin.getOriginLL()[0]);
        out.writeDouble(gpslin.getOriginLL()[1]);
        out.writeInt(modulateColor.getRGB());
    }

    public void readObject(ObjectReader in) throws IOException
    {
        String _dirpath = in.readUTF();
        double ll[] = new double[] { in.readDouble(), in.readDouble() };
        GPSLinearization _gpslin = new GPSLinearization(ll);

        init(_dirpath, _gpslin, true);

        modulateColor = new Color(in.readInt(), true);
    }
}
