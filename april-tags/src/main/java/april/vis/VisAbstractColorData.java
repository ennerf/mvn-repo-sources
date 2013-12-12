package april.vis;

/** Represents vertex color data. This is used to color points and
 * lines, but not materials (see VisAbstractFillStyle.
 **/
public interface VisAbstractColorData
{
    public void bindColor(GL gl);
    public void unbindColor(GL gl);

    /** returns the number of colors, or -1 if any number of vertices
        can be colored. **/
    public int size();
}
