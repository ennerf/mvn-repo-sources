package april.vis;

public interface VisObject
{
    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl);
}
