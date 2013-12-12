package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class WTest
{
    public static void main(String args[])
    {
        Color layerBorder = new Color(220, 220, 220);
        Color layerBackground = new Color(240, 240, 240);

        Color bufferBorder = new Color(200, 200, 200);
        Color bufferBackground = new Color(220, 220, 220);

        WDragPanel layerPanel = new WDragPanel();
        layerPanel.backgroundColor = Color.white;
        layerPanel.grabColor = layerBorder;

        for (int i = 0; i < 3; i++) {
            WVerticalPanel vp = new WVerticalPanel();
            vp.backgroundColor = layerBackground;
            vp.add(new WLabel("Test "+i));
            layerPanel.add(new WInset(vp, 2, 1, 2, 1, bufferBorder));
        }

        for (int layernum = 0; layernum < 3; layernum ++) {
            WDragPanel bufferPanel = new WDragPanel();

            bufferPanel.backgroundColor = bufferBackground;
            bufferPanel.grabColor = bufferBorder;

            for (int bufnum = 0; bufnum < 4; bufnum ++) {
                WHorizontalPanel whp = new WHorizontalPanel();
                whp.add(new WInset(new WCheckbox(true), 2, 2, 2, 2));
                whp.add(new WLabel("" + bufnum, Color.black, bufferPanel.backgroundColor));

                bufferPanel.add(new WInset(whp,
                                           1, 1, 1, 1,
                                           bufferPanel.grabColor));
            }

            WVerticalPanel vp = new WVerticalPanel();
            vp.backgroundColor = layerBackground;
            vp.add(new WLabel("Test "+layernum));
            vp.add(new WInset(bufferPanel, 4, 4, 4, 40));
            layerPanel.add(new WInset(vp, 2, 1, 2, 1, bufferBorder));
        }

        JFrame jf = new JFrame("WTest");
        jf.setLayout(new BorderLayout());
        jf.add(new JScrollPane(new WAdapter(layerPanel)), BorderLayout.CENTER);
        jf.setSize(400,600);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }
}
