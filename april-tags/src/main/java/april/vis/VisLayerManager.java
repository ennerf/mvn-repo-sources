package april.vis;

public interface VisLayerManager
{
    /** Returns a viewport with v[0],v[1] representing the bottom left
     * coordinate of the view port (opengl style coordinates with y=0
     * at the bottom). v[2] and v[3] are the width and height ofthe
     * viewport.
     */
    public int[] getLayerPosition(VisCanvas vc, int viewport[], VisLayer vl, long mtime);
}
