package april.vis;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class WCheckbox extends WComponent
{
    // how big were we when we last painted?
    int paintWidth, paintHeight;

    boolean value;

    int sz = 20;

    ArrayList<Listener> listeners = new ArrayList<Listener>();

    public interface Listener
    {
        public void stateChanged(WCheckbox target, boolean v);
    }

    public WCheckbox(boolean value)
    {
        this.value = value;
    }

    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    public boolean mouseClicked(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        setSelected(!isSelected());
        wadapter.repaint();
        return true;
    }

    public int getWidth()
    {
        return sz;
    }

    public int getHeight()
    {
        return sz;
    }

    public void paint(Graphics2D g, int width, int height)
    {
        paintBackground(g, width, height);

        paintWidth = width;
        paintHeight = height;

        if (value) {
            g.setColor(Color.green);
            g.fillRect(0, 0, sz, sz);
        }

        g.setColor(Color.black);
        g.drawRect(0, 0, sz, sz);
    }

    public boolean isSelected()
    {
        return value;
    }

    public void setSelected(boolean v)
    {
        this.value = v;

        for (Listener listener : listeners)
            listener.stateChanged(this, v);
    }
}
