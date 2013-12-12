package april.vis;

/** Represents vertex color data. This is used to color points and
 * lines, but not materials (see VisAbstractFillStyle.
 **/
public interface VisAbstractIndexData
{
    public void bindIndex(GL gl);
    public void unbindIndex(GL gl);

    /** returns the number of indices. **/
    public int size();
}
