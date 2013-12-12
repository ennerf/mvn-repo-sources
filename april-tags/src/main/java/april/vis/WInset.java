package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class WInset extends WComponent
{
    WComponent comp;
    int north, east, south, west;

    public WInset(WComponent comp, int north, int east, int south, int west, Color backgroundColor)
    {
        this.comp = comp;
        this.north = north;
        this.east = east;
        this.south = south;
        this.west = west;
        this.backgroundColor = backgroundColor;

        comp.parent = this;
    }

    public WInset(WComponent comp, int north, int east, int south, int west)
    {
        this(comp, north, east, south, west, null);
    }

    public boolean mouseClicked(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return comp.mouseClicked(wadapter, e, mx - west, my - north);
    }

    public boolean mouseEntered(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return comp.mouseEntered(wadapter, e, mx - west, my - north);
    }

    public boolean mouseExited(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return comp.mouseExited(wadapter, e, mx - west, my - north);
    }

    public boolean mouseMoved(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return comp.mouseMoved(wadapter, e, mx - west, my - north);
    }

    public boolean mouseDragged(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return comp.mouseDragged(wadapter, e, mx - west, my - north);
    }

    public boolean mousePressed(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return comp.mousePressed(wadapter, e, mx - west, my - north);
    }

    public boolean mouseReleased(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return comp.mouseReleased(wadapter, e, mx - west, my - north);
    }

    public int getWidth()
    {
        return comp.getWidth()+east+west;
    }

    public int getHeight()
    {
        return comp.getHeight()+north+south;
    }

    public void paint(Graphics2D g, int width, int height)
    {
        paintBackground(g, width, height);

        g.translate(west, north);

        comp.paint(g, width - east - west, height - north - south);

        g.translate(-west, -north);
    }
}
