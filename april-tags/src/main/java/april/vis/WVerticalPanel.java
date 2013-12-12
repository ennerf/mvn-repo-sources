package april.vis;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class WVerticalPanel extends WComponent
{
    ArrayList<WComponent> items = new ArrayList<WComponent>();

    // how big were we when we last painted?
    int paintWidth, paintHeight;

    // which item is in the middle of a mouse pressed event? They will
    // get drag/release events. (This applies to areas outside the
    // grab bar.)
    WComponent mousePressedItem = null;

    int ys[] = new int[0];

    public WVerticalPanel()
    {
    }

    public void add(WComponent item)
    {
        item.parent = this;
        items.add(item);

        // recompute ys;
        ys = new int[items.size()];
        int y = 0;
        for (int i = 0; i < items.size(); i++) {
            ys[i] = y;
            y += items.get(i).getHeight();
        }
    }

    boolean dispatchMouseEvent(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        // direct most/all events to an item in the middle of a mouse-down event.
        if (mousePressedItem != null) {
            WComponent item = mousePressedItem;

            boolean handled = false;

            int tx = 0, ty = 0;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) == item) {
                    tx = mx;
                    ty = my - ys[i];
                }
            }

            switch (e.getID()) {
                case MouseEvent.MOUSE_PRESSED:
                    handled = item.mousePressed(wadapter, e, tx, ty);
                    break;
                case MouseEvent.MOUSE_RELEASED:
                    handled = item.mouseReleased(wadapter, e, tx, ty);
                    mousePressedItem = null;
                    break;
                case MouseEvent.MOUSE_CLICKED:
                    handled = item.mouseClicked(wadapter, e, tx, ty);
                    break;
                case MouseEvent.MOUSE_DRAGGED:
                    handled = item.mouseDragged(wadapter, e, tx, ty);
                    break;
                case MouseEvent.MOUSE_MOVED:
                    handled = item.mouseMoved(wadapter, e, tx, ty);
                    break;
                case MouseEvent.MOUSE_WHEEL:
//                        handled = item.mouseWheel(wadapter, e, tx, ty);
                    break;
                case MouseEvent.MOUSE_ENTERED:
                    handled = false;
                    break;
                case MouseEvent.MOUSE_EXITED:
                    handled = false;
                    break;
                default:
                    System.out.println("Unhandled mouse event id: "+e.getID());
                    handled = false;
                    break;
            }

            return handled;
        }

        // it could be any item. find it.
        for (int i = 0; i < items.size(); i++) {
            WComponent item = items.get(i);

            if (my >= ys[i] && my <= ys[i] + item.getHeight()) {

                boolean handled = false;

                int tx = mx;
                int ty = my - ys[i];

                switch (e.getID()) {
                    case MouseEvent.MOUSE_PRESSED:
                        handled = item.mousePressed(wadapter, e, tx, ty);
                        mousePressedItem = item;
                        break;
                    case MouseEvent.MOUSE_RELEASED:
                        handled = item.mouseReleased(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_CLICKED:
                        handled = item.mouseClicked(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_DRAGGED:
                        handled = item.mouseDragged(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_MOVED:
                        handled = item.mouseMoved(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_WHEEL:
//                        handled = item.mouseWheel(wadapter, e, tx, ty);
                        break;
                    case MouseEvent.MOUSE_ENTERED:
                        handled = false;
                        break;
                    case MouseEvent.MOUSE_EXITED:
                        handled = false;
                        break;
                    default:
                        System.out.println("Unhandled mouse event id: "+e.getID());
                        handled = false;
                        break;
                }

                if (handled)
                    return true;
            }
        }
        return false;
    }

    public boolean mouseClicked(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public boolean mouseEntered(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public boolean mouseExited(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public boolean mouseMoved(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public boolean mouseDragged(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public boolean mousePressed(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public boolean mouseReleased(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        return false;
    }

    public int getWidth()
    {
        int x = 0;
        for (int i = 0; i < items.size(); i++) {
            x = Math.max(x, items.get(i).getWidth());
        }
        return x;
    }

    public int getHeight()
    {
        int y = 0;
        for (int i = 0; i < items.size(); i++) {
            y += items.get(i).getHeight();
        }
        return y;
    }

    public void paint(Graphics2D g, int width, int height)
    {
        paintBackground(g, width, height);

        paintWidth = width;
        paintHeight = height;

        for (int i = 0; i < items.size(); i++) {
            WComponent item = items.get(i);

            g.translate(0, ys[i]);
            item.paint(g, width, item.getHeight());
            g.translate(0, -ys[i]);
        }
    }
}
