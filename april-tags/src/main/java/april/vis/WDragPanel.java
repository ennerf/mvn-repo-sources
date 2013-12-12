package april.vis;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class WDragPanel extends WComponent
{
    ArrayList<WComponent> items = new ArrayList<WComponent>();
    int ys[];

    WComponent selectedItem;

    // where is the actual mouse pointer?
    int selectedMouseX = -1, selectedMouseY = -1;

    // where, with respect to the item's origin, is the mouse pointer?
    int selectedOffsetX = -1, selectedOffsetY = -1;

    // how many pixels wide is the grab region on the right?
    int grabWidth = 20;

    // how big were we when we last painted?
    int paintWidth, paintHeight;

    // which item is in the middle of a mouse pressed event? They will
    // get drag/release events. (This applies to areas outside the
    // grab bar.)
    WComponent mousePressedItem = null;

    int preferredWidth, preferredHeight;

    public Color grabColor = new Color(120, 220, 220);

    ArrayList<Listener> listeners = new ArrayList<Listener>();

    public interface Listener
    {
        public void orderChanged(WDragPanel target, WComponent order[]);
    }

    public WDragPanel()
    {
    }

    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    public synchronized void clear()
    {
        items.clear();
        recompute();
    }

    public synchronized void add(WComponent item)
    {
        item.parent = this;
        items.add(item);
        ys = null;
        recompute();
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
                    System.out.println("huh?");
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

                if (item == selectedItem) {
                    tx = mx - (selectedMouseX - selectedOffsetX);
                    ty = my - (selectedMouseY - selectedOffsetY);
                }

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

        if (selectedItem != null) {
            selectedMouseX = mx;
            selectedMouseY = my;
            recompute();
            wadapter.repaint();
            return true;
        }

        return false;
    }

    public boolean mousePressed(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        // Find the Item that is being pressed (if any)
        int y0 = 0;
        for (int i = 0; i < items.size(); i++) {
            WComponent item = items.get(i);
            int y1 = y0 + item.getHeight();

            if (my >= y0 && my <= y1 && mx > (paintWidth - grabWidth) && mx < paintWidth) {
                selectedItem = item;
                selectedMouseX = mx;
                selectedMouseY = my;
                selectedOffsetX = mx;
                selectedOffsetY = my - y0;
            }

            y0 = y1;
            wadapter.repaint();
        }

        return selectedItem != null;
    }

    public boolean mouseReleased(WAdapter wadapter, MouseEvent e, int mx, int my)
    {
        if (dispatchMouseEvent(wadapter, e, mx, my))
            return true;

        if (selectedItem != null) {
            selectedItem = null;
            wadapter.repaint();
            return true;
        }

        return false;
    }

    public int getWidth()
    {
        return preferredWidth;
    }

    public int getHeight()
    {
        return preferredHeight;
    }

    static class Pos implements Comparable<Pos>
    {
        WComponent item;
        int  ymid;

        public int compareTo(Pos p)
        {
            return ymid - p.ymid;
        }
    }

    // Compute the y coordinate for each item, reordering the items if necessary.
    synchronized void recompute()
    {
        if (selectedItem != null) {
            int sy = selectedMouseY - selectedOffsetY;
            ArrayList<WComponent> newitems = new ArrayList<WComponent>();

            int y = 0;
            for (int i = 0; i < items.size(); i++) {
                WComponent item = items.get(i);
                if (item == selectedItem)
                    continue;

                if (y+item.getHeight()/2 < sy) {
                    newitems.add(item);
                    y += item.getHeight();
                } else {
                    break;
                }
            }

            newitems.add(selectedItem);

            for (int i = 0; i < items.size(); i++) {
                WComponent item = items.get(i);

                if (!newitems.contains(item))
                    newitems.add(item);
            }

            items = newitems;
        }

        ys = new int[items.size()];
        preferredWidth = 0;

        int y = 0;
        for (int i = 0; i < items.size(); i++) {
            WComponent item = items.get(i);
            ys[i] = y;
            y += item.getHeight();

            preferredWidth = Math.max(preferredWidth, item.getWidth());
        }

        preferredHeight = y;

        WComponent order[] = new WComponent[items.size()];
        for (int i = 0; i < items.size(); i++)
            order[i] = items.get(i);

        for (Listener listener : listeners)
            listener.orderChanged(this, order);
    }

    public synchronized void paint(Graphics2D g, int width, int height)
    {
        paintBackground(g, width, height);

        paintWidth = width;
        paintHeight = height;

        // draw components
        for (int i = 0; i < items.size(); i++) {
            WComponent item = items.get(i);

            // selected item gets drawn "on top", so skip for now.
            if (item == selectedItem)
                continue;

            g.translate(0, ys[i]);
            item.paint(g, width - grabWidth, item.getHeight());

            drawGrabBar(g, paintWidth - grabWidth, 0, grabWidth, item.getHeight());

            g.translate(0, -ys[i]);
        }

        if (selectedItem != null) {
            // draw last/on top.
            int offsetx = selectedMouseX - selectedOffsetX;
            int offsety = selectedMouseY - selectedOffsetY;

            // keep the selected item on the screen
            offsety = Math.max(offsety, -selectedItem.getHeight()/2);
            offsety = Math.min(offsety, height - selectedItem.getHeight()/2);

            g.translate(offsetx, offsety);
            selectedItem.paint(g, width - grabWidth, selectedItem.getHeight());

            drawGrabBar(g, paintWidth - grabWidth, 0, grabWidth, selectedItem.getHeight());

            g.translate(-offsetx, -offsety);
        }
    }

    void drawGrabBar(Graphics2D g, int x, int y, int width, int height)
    {
        g.setColor(grabColor);
        g.fillRect(x+1, y+1, width-2, height-2);

        g.setColor(grabColor.darker());
        for (int y0 = y+2; y0 + 2 < y + height; y0 += 4)
            g.drawLine(x+3, y0, x+width-5, y0);
    }
}
