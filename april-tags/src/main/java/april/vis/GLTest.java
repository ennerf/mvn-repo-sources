package april.vis;

import java.awt.*;
import javax.swing.*;

public class GLTest
{
    JFrame jf;
    GL gl = new GL();
    MyJPanel panel = new MyJPanel();

    public GLTest()
    {
        jf = new JFrame("GLTest");
        jf.setLayout(new BorderLayout());
        jf.add(panel, BorderLayout.CENTER);
        jf.setSize(600, 400);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }

    class MyJPanel extends JPanel
    {
        Image im;

        public synchronized void setImage(Image im)
        {
            this.im = im;
        }

        public synchronized void paint(Graphics g)
        {
            if (im != null)
                g.drawImage(im, 0, 0, null);
        }
    }

    public static void main(String args[])
    {
        new GLTest();
    }
}
