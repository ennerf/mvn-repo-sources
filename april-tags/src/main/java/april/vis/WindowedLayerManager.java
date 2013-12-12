package april.vis;

// Simple layer manager to keep your layer aligned to a window-relative point
public class WindowedLayerManager implements VisLayerManager
{
    public enum ALIGN
    {
        TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT, COORDINATES
    };

    // These can be changed anytime
    public ALIGN align;
    public int winwidth, winheight;
    public int offx, offy; // Offsets
    public WindowedLayerManager(ALIGN align, int winwidth, int winheight)
    {
        this.align = align;
        this.winwidth = winwidth;
        this.winheight = winheight;
    }


    public WindowedLayerManager(ALIGN align, int winwidth, int winheight, int offx, int offy)
    {
        this.align = align;
        this.winwidth = winwidth;
        this.winheight = winheight;
        this.offx = offx;
        this.offy = offy;
    }

    //XXX This doesn't handle the case where the viewport doesn't start with 0 0
    public int[] getLayerPosition(VisCanvas vc, int viewport[], VisLayer vl, long mtime)
    {
        // cache these
        int ww = winwidth;
        int wh = winheight;

        double px0, px1, py0, py1;
        switch (align)
        {
            case TOP_LEFT:
            case LEFT:
            case BOTTOM_LEFT:
                px0 = 0;
                px1 = ww;
                break;
            default:
            case TOP:
            case CENTER:
            case BOTTOM:
                px0 = (viewport[0] + viewport[2]) / 2 - ww / 2;
                px1 = px0 + ww;
                break;
            case TOP_RIGHT:
            case RIGHT:
            case BOTTOM_RIGHT:
                px1 = viewport[2] - 1;
                px0 = px1 - ww;
                break;
        }
        switch (align)
        {
            case TOP_LEFT:
            case TOP:
            case TOP_RIGHT:
                // remember that y is inverted: y=0 is at bottom
                // left in GL
                py0 = viewport[3] - wh - 1;
                py1 = py0 + wh;
                break;
            default:
            case LEFT:
            case CENTER:
            case RIGHT:
                py0 = (viewport[1] + viewport[3]) / 2 - wh / 2;
                py1 = py0 + wh;
                break;
            case BOTTOM_LEFT:
            case BOTTOM:
            case BOTTOM_RIGHT:
                py0 = 0;
                py1 = py0 + wh;
                break;
        }
        return new int[]{(int)px0,(int)py0,(int)(px1-px0),(int)(py1-py0)};
    }
}
