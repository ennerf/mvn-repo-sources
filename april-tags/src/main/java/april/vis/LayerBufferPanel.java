package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class LayerBufferPanel extends JPanel implements VisWorld.Listener, VisCanvas.Listener
{
    VisCanvas vc;
    WAdapter wadapter;
    WDragPanel layerPanel = new WDragPanel();

    HashMap<WComponent, VisLayer> layerMap;
    HashMap<WComponent, VisWorld.Buffer> bufferMap;

    public LayerBufferPanel(VisCanvas vc)
    {
        this.vc = vc;

        wadapter = new WAdapter(layerPanel);

        vc.addListener(this);

        setLayout(new BorderLayout());
        add(new JScrollPane(wadapter), BorderLayout.CENTER);
        add(Box.createHorizontalStrut(280), BorderLayout.SOUTH);

        rebuild();
    }

    public void bufferAdded(VisWorld vw, String name)
    {
        rebuild();
    }

    public void layerAdded(VisCanvas vc, VisLayer vl)
    {
        rebuild();
    }

    void rebuild()
    {
        layerPanel.clear();

        Color layerBorder = new Color(220, 220, 220);
        Color layerBackground = new Color(240, 240, 240);

        Color bufferBorder = new Color(200, 200, 200);
        Color bufferBackground = new Color(220, 220, 220);

        layerPanel.backgroundColor = Color.white;
        layerPanel.grabColor = layerBorder;

        layerMap = new HashMap<WComponent, VisLayer>();
        bufferMap = new HashMap<WComponent, VisWorld.Buffer>();

        synchronized(vc.layers) {

            Collections.sort(vc.layers);

            for (VisLayer layer : vc.layers) {

                layer.world.addListener(this);

                WVerticalPanel vp = new WVerticalPanel();
                vp.backgroundColor = layerBackground;

                if (true) {
                    WHorizontalPanel hp = new WHorizontalPanel();

                    WCheckbox cb = new WCheckbox(layer.isEnabled());
                    cb.addListener(new LayerCheckboxListener(layer));
                    hp.add(new WInset(cb, 2, 2, 2, 2, layerBackground));
                    hp.add(new WLabel(layer.name, Color.black, layerBackground));

                    vp.add(new WInset(hp, 1, 1, 1, 1, layerBackground));
                }

                WDragPanel bufferPanel = new WDragPanel();
                bufferPanel.backgroundColor = bufferBackground;
                bufferPanel.grabColor = bufferBorder;

                synchronized(layer.world.buffers) {

                    for (VisWorld.Buffer buffer : layer.world.buffers) {

                        WHorizontalPanel hp = new WHorizontalPanel();
                        WCheckbox cb = new WCheckbox(layer.isBufferEnabled(buffer.name));
                        cb.addListener(new BufferCheckboxListener(layer, buffer.name));
                        hp.add(new WInset(cb, 2, 2, 2, 2));
                        hp.add(new WLabel(buffer.name, Color.black, bufferPanel.backgroundColor));

                        WComponent bufferObject = new WInset(hp, 1, 1, 1, 1, bufferPanel.grabColor);
                        bufferMap.put(bufferObject, buffer);
                        bufferPanel.add(bufferObject);
                    }

                    bufferPanel.addListener(new BufferDragPanelListener());
                }

                vp.add(new WInset(bufferPanel, 4, 4, 4, 40));
                WComponent layerObject = new WInset(vp, 2, 1, 2, 1, bufferBorder);
                layerMap.put(layerObject, layer);
                layerPanel.addListener(new LayerDragPanelListener());

                layerPanel.add(layerObject);
            }
        }

        revalidate();
        wadapter.repaint();
    }

    static class BufferCheckboxListener implements WCheckbox.Listener
    {
        VisLayer layer;
        String name;

        BufferCheckboxListener(VisLayer layer, String name)
        {
            this.layer = layer;
            this.name = name;
        }

        public void stateChanged(WCheckbox wcb, boolean v)
        {
            layer.setBufferEnabled(name, v);
        }
    }

    static class LayerCheckboxListener implements WCheckbox.Listener
    {
        VisLayer layer;

        LayerCheckboxListener(VisLayer layer)
        {
            this.layer = layer;
        }

        public void stateChanged(WCheckbox wcb, boolean v)
        {
            layer.setEnabled(v);
        }
    }

    class LayerDragPanelListener implements WDragPanel.Listener
    {
        LayerDragPanelListener()
        {
        }

        public void orderChanged(WDragPanel dp, WComponent order[])
        {
            for (int i = 0; i < order.length; i++) {
                VisLayer layer = layerMap.get(order[i]);
                if (layer != null)
                    layer.drawOrder = i;
            }
        }
    }

    class BufferDragPanelListener implements WDragPanel.Listener
    {
        BufferDragPanelListener()
        {
        }

        public void orderChanged(WDragPanel dp, WComponent order[])
        {
            for (int i = 0; i < order.length; i++) {
                VisWorld.Buffer vb = bufferMap.get(order[i]);
                if (vb != null)
                    vb.setDrawOrder(i);
            }
        }
    }

}
