package april.vis;

public interface VisAbstractVertexData
{
    public void bindVertex(GL gl);
    public void unbindVertex(GL gl);

    public void bindNormal(GL gl);
    public void unbindNormal(GL gl);

    /** returns the number of vertices. **/
    public int size();
}
