package april.camera;

public interface View
{
    /** Return max width. Valid pixel values for this
      * view range from 0 to width.
      */
    public int          getWidth();

    /** Return max height. Valid pixel values for this
      * view range from 0 to height.
      */
    public int          getHeight();

    /** Return intrinsics matrix.
      */
    public double[][]   copyIntrinsics();

    /** Convert a 3D ray to pixel coordinates in this view,
      * applying distortion if appropriate.
      */
    public double[]     rayToPixels(double xyz_r[]);

    /** Convert a 2D pixel coordinate in this view to a 3D ray,
      * removing distortion if appropriate.
      */
    public double[]     pixelsToRay(double xy_p[]);
}

