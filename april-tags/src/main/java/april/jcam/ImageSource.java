package april.jcam;

import java.io.*;
import java.util.*;

/** A provider of image data, such as a camera. **/
public abstract class ImageSource
{
    public static ImageSource make(String url) throws IOException
    {
       if (url.startsWith("file:") || url.startsWith("dir:"))
           return new ImageSourceFile(url);

        if (url.startsWith("islog-lcm:"))
            return new ImageSourceISLogLCM(url);

        if (url.startsWith("tcp:") || url.startsWith("tcp-server:"))
            return new ImageSourceTCP(url);

        if (url.startsWith("reflect:"))
            return new ImageSourceReflect(url);

        return new ImageSourceNative(url);
    }

    public static ArrayList<String> getCameraURLs()
    {
        return ImageSourceNative.getCameraURLs();
    }

    public abstract void start();

    public abstract void stop();

    public abstract FrameData getFrame();

    public abstract int getNumFormats();

    public abstract ImageSourceFormat getFormat(int idx);

    /** Try to find a format with the given format string. The first
        such format will be selected.  e.g., format = "GRAY8" or
        "BAYER_RGGB". Returns true on success. **/
    public boolean setFormat(String format)
    {
        for (int i = 0; i < getNumFormats(); i++) {
            ImageSourceFormat ifmt = getFormat(i);
            if (ifmt.format.equals(format)) {
                setFormat(i);
                return true;
            }
        }

        return false;
    }

    public abstract void setFormat(int idx);

    public abstract int getCurrentFormatIndex();

    public ImageSourceFormat getCurrentFormat()
    {
        return getFormat(getCurrentFormatIndex());
    }

    public abstract boolean isFeatureAvailable(int idx);
    public abstract String getFeatureType(int idx);

    public int getNumFeatures()
    {
        return 0;
    }

    public String getFeatureName(int idx)
    {
        return "";
    }

    public double getFeatureMin(int idx)
    {
        assert(false); //deprecated
        return 0;
    }

    public double getFeatureMax(int idx)
    {
        assert(false); //deprecated
        return 0;
    }

    public double getFeatureValue(int idx)
    {
        return 0;
    }

    /** returns non-zero on error. **/
    public int setFeatureValue(int idx, double v)
    {
        return -1;
    }

    public abstract void printInfo();

    public abstract int close();
}
