package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class DragPanel implements MouseListener, MouseMotionListener
{
    ArrayList<Item> items = new ArrayList<Item>();

    Item selectedItem = null;

    // where is the actual mouse pointer?
    int selectedMouseX = -1, selectedMouseY = -1;

    // where, with respect to the item's origin, is the mouse pointer?
    int selectedOffsetX = -1, selectedOffsetY = -1;

    public interface Item
    {
        // how many vertical pixels will the item draw? This should be
        // a constant value for the item.
        public int getHeight();

        // get preferred width (but you can draw to infinity)
        public int getWidth();

        // Draw the item at y=0, x=0, respecting the getHeight. Do not clear the background.
        public void paint(DragPanel dp, Graphics2D g, int width, int height, boolean selected);
    }

    public DragPanel()
    {
    }

    public void addItem(Item item)
    {
        items.add(item);
    }

    public void paint(Graphics2D g, int width, int height)
    {
        g.setColor(Color.white);
        g.fillRect(0, 0, width, height);

        int ys[] = getYs();

        g.setColor(new Color(200,200,200));
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);

            if (item == selectedItem)
                continue;

            g.translate(0, ys[i]);
            item.paint(this, g, width, item.getHeight(), false);
            g.translate(0, -ys[i]);
        }

        if (selectedItem != null) {
            g.translate(selectedMouseX - selectedOffsetX, selectedMouseY - selectedOffsetY);
            selectedItem.paint(this, g, width, selectedItem.getHeight(), true);
            g.translate(-(selectedMouseX - selectedOffsetX), -(selectedMouseY - selectedOffsetY));
        }
    }

    static class Pos implements Comparable<Pos>
    {
        Item item;
        int  ymid;

        public int compareTo(Pos p)
        {
            return ymid - p.ymid;
        }
    }

    int[] getYs()
    {
        ArrayList<Pos> poses = new ArrayList<Pos>();

        // what is the y coordinate of the origin of the selected item?
        int sy = selectedMouseY - selectedOffsetY;

        int y0 = 0;

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);

            int ymid = y0+item.getHeight() / 2;

            Pos p = new Pos();
            p.item = item;
            p.ymid = ymid;

            if (item == selectedItem)
                p.ymid = sy + selectedItem.getHeight() / 2;

            y0 += item.getHeight();

            poses.add(p);
        }

        Collections.sort(poses);

        int ys[] = new int[items.size()];
        y0 = 0;

        for (int i = 0; i < poses.size(); i++) {
            Pos p = poses.get(i);
            items.set(i, p.item);
            ys[i] = y0;
            y0 += p.item.getHeight();
        }

        return ys;
    }

    public void mouseClicked(MouseEvent e)
    {
    }

    public void mouseEntered(MouseEvent e)
    {
    }

    public void mouseExited(MouseEvent e)
    {
    }

    public void mouseMoved(MouseEvent e)
    {
    }

    public void mouseDragged(MouseEvent e)
    {
        if (selectedItem != null) {
            selectedMouseX = e.getX();
            selectedMouseY = e.getY();
        }
    }

    public void mousePressed(MouseEvent e)
    {
        // Find the Item that is being pressed (if any)
        int y0 = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int y1 = y0 + item.getHeight();

            if (e.getY() >= y0 && e.getY() <= y1) {
                selectedItem = item;
                selectedMouseX = e.getX();
                selectedMouseY = e.getY();
                selectedOffsetX = e.getX();
                selectedOffsetY = e.getY() - y0;
            }

            y0 = y1;
        }
    }

    public void mouseReleased(MouseEvent e)
    {
        selectedItem = null;
    }

    public Dimension getPreferredSize()
    {
        int y = 0;
        int x = 0;

        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            y += item.getHeight();
            x = Math.max(x, item.getWidth());
        }
        return new Dimension(x, y);
    }

    static class TextItem implements DragPanel.Item
    {
        String name;

        public TextItem(String name)
        {
            this.name = name;
        }

        public int getHeight()
        {
            return 25;
        }

        public int getWidth()
        {
            return 100;
        }

        public void paint(DragPanel dp, Graphics2D g, int width, int height, boolean selected)
        {
            if (selected)
                g.setColor(Color.blue);
            else
                g.setColor(Color.gray);
            g.fillRoundRect(0, 0, width, height, 8, 8);
            g.setColor(Color.lightGray);
            g.drawRoundRect(0, 0, width, height, 8, 8);

            g.setColor(Color.black);
            g.drawString(name, 10, 20);
        }
    }

    public static void main(String args[])
    {
        JFrame jf = new JFrame("DragPanel Test");
        jf.setLayout(new BorderLayout());

        DragPanel dp = new DragPanel();
        dp.addItem(new TextItem("abc"));
        dp.addItem(new TextItem("def"));
        dp.addItem(new TextItem("ghi"));
        dp.addItem(new TextItem("jkl"));
        dp.addItem(new TextItem("mno"));
        dp.addItem(new TextItem("pqr"));
        dp.addItem(new TextItem("stu"));
        dp.addItem(new TextItem("vwx"));

//        jf.add(new JScrollPane(dp), BorderLayout.CENTER);

        jf.setSize(600,400);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }
}
