package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;

import april.jmat.*;
import april.jmat.geom.*;

/** We re-create a jmenu with every right click; this makes it easier
 * for us to set checkbox menu items appropriately without requiring
 * every change of camera settings to fiddle with the popup menu. **/
public class DefaultPopupMenu extends VisEventAdapter implements VisSerializable
{
    VisLayer layer;

    String interfaceModes[] = { "1.5D", "2D", "2.5D", "3D" };

    public DefaultPopupMenu(VisLayer layer)
    {
        this.layer = layer;
    }

    public int getDistpatchOrder()
    {
        return 10;
    }

    public boolean mouseClicked(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
    {
        assert(vl == layer);

        int mods = e.getModifiersEx();
        boolean shift = (mods&MouseEvent.SHIFT_DOWN_MASK)>0;
        boolean ctrl = (mods&MouseEvent.CTRL_DOWN_MASK)>0;
        boolean alt = (mods&MouseEvent.ALT_DOWN_MASK)>0;

        if (e.getButton() == MouseEvent.BUTTON3 && ctrl) {
            JPopupMenu jmenu = populatePopupMenu(vc, vl);
            jmenu.show(vc, (int) e.getPoint().getX(), (int) e.getPoint().getY());
            return true;
        }

        return false;
    }

    JComponent makeSeparator(String name)
    {
        JLabel jl = new JLabel(name);
        jl.setForeground(Color.gray);
//        jl.setBorder(BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.LOWERED));
        return jl;
    }

    JPopupMenu populatePopupMenu(VisCanvas vc, VisLayer vl)
    {
        JPopupMenu jmenu = new JPopupMenu();

//        jmenu.addSeparator();
        jmenu.add(makeSeparator("Camera Options"));
        jmenu.addSeparator();

        jmenu.setLabel("Test");
        vl.cameraManager.populatePopupMenu(jmenu);

        jmenu.addSeparator();
        jmenu.add(makeSeparator("VisLayer Options"));
        jmenu.addSeparator();

        vl.populatePopupMenu(jmenu);

        jmenu.addSeparator();
        jmenu.add(makeSeparator("VisCanvas Options"));
        jmenu.addSeparator();

        vc.populatePopupMenu(jmenu);

        return jmenu;
    }

    public DefaultPopupMenu(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeObject(layer);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        layer = (VisLayer) ins.readObject();
    }
}
