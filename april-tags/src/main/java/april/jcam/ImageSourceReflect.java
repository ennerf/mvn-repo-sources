package april.jcam;

import april.util.*;
import java.io.*;

// Thin shell wrapper to enable loading an external image source class
// For example, using this url:
// reflect://jhstrom.blackfly.ImageSourceGVCP?gvcp://169.254.0.2
// creates a wrapper around the url 'gvcp://169.254.0.2'
// Note: external image sources must have a constructor with a single string argument (the url)
public class ImageSourceReflect extends ImageSource
{
    ImageSource is;

    // accepts urls like: reflect://java.class.name?gvcp://.....
    public ImageSourceReflect(String rurl) throws IOException
    {
        int qIdx = rurl.indexOf('?');

        if (!rurl.startsWith("reflect://") || qIdx < 0)
            throw new IOException("Invalid reflect url "+rurl);

        String isClass = rurl.substring("reflect://".length(), qIdx);
        String url = rurl.substring(qIdx+1);


        is = (ImageSource) ReflectUtil.createObject(isClass, url);
        if (is == null)
            throw new IOException(String.format("Unable to load class %s from url %s\n", isClass, rurl));
    }

    public void start()
    {
        is.start();
    }

    public void stop()
    {
        is.stop();
    }

    public FrameData getFrame()
    {
        return is.getFrame();
    }

    public int getNumFormats()
    {
        return is.getNumFormats();
    }

    public ImageSourceFormat getFormat(int idx)
    {
        return is.getFormat(idx);
    }

    public void setFormat(int idx)
    {
        is.setFormat(idx);
    }

    public int getCurrentFormatIndex()
    {
        return is.getCurrentFormatIndex();
    }

    public boolean isFeatureAvailable(int idx)
    {
        return is.isFeatureAvailable(idx);
    }

    public String getFeatureType(int idx)
    {
        return is.getFeatureType(idx);
    }


    public int getNumFeatures()
    {
        return is.getNumFeatures();
    }

    public String getFeatureName(int idx)
    {
        return is.getFeatureName(idx);
    }

    public double getFeatureValue(int idx)
    {
        return is.getFeatureValue(idx);
    }

    /** returns non-zero on error. **/
    public int setFeatureValue(int idx, double v)
    {
        return is.setFeatureValue(idx, v);
    }

    public void printInfo()
    {
        is.printInfo();
    }

    public int close()
    {
        return is.close();
    }



}