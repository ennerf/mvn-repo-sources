package april.viewer;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.imageio.*;
import java.util.*;
import java.net.*;

import april.config.*;
import april.util.*;

import april.jmat.*;
import april.vis.*;

/** Generic robot viewer. **/
public class Viewer
{
    JFrame jf;
    VisWorld   vw;
    VisLayer   vl;
    VisCanvas  vc;
    JPanel paramsPanel;

    Config     config;

    ArrayList<ViewObject> viewObjects = new ArrayList<ViewObject>();

    public static void main(String args[])
    {
        Config config = ConfigUtil.getDefaultConfig(args);

        Viewer v = new Viewer(config);
    }

    /**
     * Creates Viewer in an existing window.  This allows all of the viewer code to be used
     * inside another application (possibly in a seperate JPanel)
     *
     * @param _config     file containing configuration information
     * @param _jf     JFrame from parent application (window)
     public Viewer(Config _config, JFrame _jf)
     {
     vw = new VisWorld();
     VisLayer vl = new VisLayer(vw);nvc = new VisCanvas(vl);
     jf = _jf;

     initialize(_config);
     }
    */

    /**
     * Creates default Viewer in new window (frame)
     *
     * @param _config     file containing configuration information
     */
    public Viewer(Config _config)
    {
        vw = new VisWorld();
        vl = new VisLayer(vw);
        vc = new VisCanvas(vl);

        jf = new JFrame("Viewer");

        jf.setLayout(new BorderLayout());

        paramsPanel = new JPanel();
        paramsPanel.setLayout(new VFlowLayout());
        JSplitPane jsp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, vc, paramsPanel);
        jsp.setDividerLocation(1.0);
        jsp.setResizeWeight(1.0);

        jf.add(jsp, BorderLayout.CENTER);
        jf.setSize(600,400);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setSize(1000+2, 600+28);

        initialize(_config);
    }

    public void initialize(Config _config)
    {
        this.config = _config.getChild("viewer");

        VzGrid.addGrid(vw);

        String viewobjects[] = config.requireStrings("viewobjects");

        for (String viewobject : viewobjects) {

            Config childConfig = config.getChild(viewobject);
            String className = childConfig.requireString("class");

            try {
                Class cls = Class.forName(className);
                ViewObject o = (ViewObject) cls.getConstructor(Viewer.class, String.class, Config.class).newInstance(this, viewobject, childConfig);
                viewObjects.add(o);
            } catch (Exception ex) {
                System.out.println("Viewer: Unable to create "+viewobject+": "+ex);
                ex.printStackTrace();
                System.exit(0);
            }
        }
    }

    public VisWorld getVisWorld()
    {
        return vw;
    }

    public VisCanvas getVisCanvas()
    {
        return vc;
    }

    public VisLayer getVisLayer()
    {
        return vl;
    }

    public void addParamPanel(JComponent c)
    {
        paramsPanel.add(c);
        paramsPanel.invalidate();
    }
}
