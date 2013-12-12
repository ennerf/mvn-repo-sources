package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class WAdapter extends JComponent implements MouseListener, MouseMotionListener
{
    WComponent comp;

    public WAdapter(WComponent comp)
    {
        this.comp = comp;

        addMouseListener(this);
        addMouseMotionListener(this);
    }

    public void mouseClicked(MouseEvent e)
    {
        comp.mouseClicked(this, e, e.getX(), e.getY());
    }

    public void mouseEntered(MouseEvent e)
    {
        comp.mouseEntered(this, e, e.getX(), e.getY());
    }

    public void mouseExited(MouseEvent e)
    {
        comp.mouseExited(this, e, e.getX(), e.getY());
    }

    public void mouseMoved(MouseEvent e)
    {
        comp.mouseMoved(this, e, e.getX(), e.getY());
    }

    public void mouseDragged(MouseEvent e)
    {
        comp.mouseDragged(this, e, e.getX(), e.getY());
    }

    public void mousePressed(MouseEvent e)
    {
        comp.mousePressed(this, e, e.getX(), e.getY());
    }

    public void mouseReleased(MouseEvent e)
    {
        comp.mouseReleased(this, e, e.getX(), e.getY());
    }

    public void paint(Graphics _g)
    {
        Graphics2D g = (Graphics2D) _g;

        comp.paint(g, getWidth(), getHeight());
    }

    public Dimension getPreferredSize()
    {
        return new Dimension(comp.getWidth(), comp.getHeight());
    }
}
