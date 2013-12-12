package april.vis;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class WLabel extends WComponent
{
    String value;
    Color textColor;

    public WLabel(String value)
    {
        this(value, Color.black, null);
    }

    public WLabel(String value, Color textColor, Color backgroundColor)
    {
        this.value = value;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
    }

    public int getHeight()
    {
        return 25;
    }

    public int getWidth()
    {
        return 100;
    }

    public void paint(Graphics2D g, int width, int height)
    {
        paintBackground(g, width, height);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(textColor);
        g.drawString(value, 10, 20);
    }
}
