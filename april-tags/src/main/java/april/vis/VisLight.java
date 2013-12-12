package april.vis;

import april.jmat.*;

import java.io.*;

public class VisLight implements VisSerializable
{
    public float position[];
    public float ambient[], diffuse[], specular[];

    public VisLight(float position[], float ambient[], float diffuse[], float specular[])
    {
        this.position = LinAlg.copy(position);
        this.ambient = LinAlg.copy(ambient);
        this.diffuse = LinAlg.copy(diffuse);
        this.specular = LinAlg.copy(specular);
    }

    public VisLight(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeFloats(position);
        outs.writeFloats(ambient);
        outs.writeFloats(diffuse);
        outs.writeFloats(specular);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        position = ins.readFloats();
        ambient = ins.readFloats();
        diffuse = ins.readFloats();
        specular = ins.readFloats();
    }
}
