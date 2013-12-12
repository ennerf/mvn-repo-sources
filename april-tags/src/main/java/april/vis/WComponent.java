package april.vis;

import java.awt.*;
import java.awt.event.*;

public abstract class WComponent
{
    public Color backgroundColor;
    public WComponent parent;

    public boolean mouseClicked(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mouseEntered(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mouseExited(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mouseMoved(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mouseDragged(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mousePressed(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public boolean mouseReleased(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        return false;
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public Color getBackgroundColor()
    {
        if (backgroundColor != null)
            return backgroundColor;

        if (parent == null)
            return Color.white;

        return parent.getBackgroundColor();
    }

    public void paintBackground(Graphics2D g, int width, int height)
    {
        g.setColor(getBackgroundColor());
        g.fillRect(0, 0, width, height);
    }

    public abstract void paint(Graphics2D g, int width, int height);
}
