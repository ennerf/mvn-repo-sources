package april.tag;

import java.util.*;
import java.awt.image.*;

public interface AbstractTagDetector
{
    public ArrayList<TagDetection> process(BufferedImage im, double opticalCenter[]);
}
