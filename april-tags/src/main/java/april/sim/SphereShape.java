package april.sim;

import java.awt.*;
import java.io.*;

import april.vis.*;
import april.jmat.*;

public class SphereShape implements Shape
{
    protected double r;

    public SphereShape(double r)
    {
        this.r = r;
    }

    public double getRadius()
    {
        return r;
    }

    public double getBoundingRadius()
    {
        return r;
    }
}
