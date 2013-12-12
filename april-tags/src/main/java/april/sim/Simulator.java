package april.sim;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.*;
import javax.swing.*;

import april.config.*;
import april.util.*;
import april.jmat.*;
import april.vis.*;
import april.jmat.geom.*;

import lcm.util.*;

public class Simulator implements VisConsole.Listener
{
    VisWorld vw;
    VisLayer vl;
    VisConsole console;

    SimWorld world;
    String worldFilePath = "/tmp/world.world";

    static final double MIN_SIZE = 0.25;

    String simObjectClass = "april.sim.SimBox";
    SimObject selectedObject = null;
    FindSimObjects finder = new FindSimObjects();

    KeyboardGamepad keygp = new KeyboardGamepad();

    public Simulator(VisWorld vw, VisLayer vl, VisConsole console, SimWorld sw)
    {
        this.vw = vw;
        this.vl = vl;
        this.console = console;
        this.world = sw;

        if (world.path != null)
            worldFilePath = world.path;

        keygp.running = false;
        new Thread(keygp).start();

        vl.addEventHandler(new MyEventHandler());

        VzGrid.addGrid(vw, new VzGrid(new VzLines.Style(Color.gray,1),
                                      new VzMesh.Style(Color.white)));// = Color.white;//new java.awt.Color(255,255,255,0);
        vl.backgroundColor = Color.white;
        if (true) {
            VisWorld.Buffer vb = vw.getBuffer("SimWorld");
            vb.addBack(new VisSimWorld());
            vb.swap();
        }

        // Set the window size correctly
        if (true) {
            double max[] = {-Double.MAX_VALUE, - Double.MAX_VALUE,- Double.MAX_VALUE};
            double min[] = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            for (SimObject so : world.objects) {
                double T[][] = so.getPose();
                Shape s = so.getShape();
                if (s instanceof BoxShape) {
                    BoxShape bs = (BoxShape) s;

                    ArrayList<double[]> vertices = bs.getVertices();

                    for (double vertex[] : vertices) {
                        double global_v[] = LinAlg.transform(T, vertex);

                        for (int l = 0; l < 3; l++) {
                            max[l] = Math.max(global_v[l],max[l]);
                            min[l] = Math.min(global_v[l],min[l]);
                        }
                    }

                } else if (s instanceof SphereShape){
                    SphereShape ss = (SphereShape) s;
                    double r = ss.getRadius();
                    for (int l = 0; l < 3; l++) {
                        max[l] = Math.max(T[l][3] + r, max[l]);
                        min[l] = Math.min(T[l][3] - r, min[l]);
                    }

                } else {
                    for (int l = 0; l < 3; l++) {
                        max[l] = Math.max(T[l][3],max[l]);
                        min[l] = Math.min(T[l][3],min[l]);
                    }
                    System.out.println("WRN: Unsupported shape type: "+s.getClass().getName());
                }


            }

            // XXX Might be good to add a bit of 'fudge' here, especially if we stick with perspective
            vl.cameraManager.fit2D(LinAlg.resize(min,2),
                                   LinAlg.resize(max,2),true);
        }


        //vis2
        console.addListener(this);
        console.addShortcut(VisConsole.Shortcut.makeCode("start", KeyEvent.VK_F1, 0));
        console.addShortcut(VisConsole.Shortcut.makeCode("stop", KeyEvent.VK_F2, 0));
        console.addShortcut(VisConsole.Shortcut.makeCode("toggle-keyboard-gamepad", KeyEvent.VK_F5, 0));

        draw();


        if (world.geoimage != null) {

            VisWorld.Buffer vb = vw.getBuffer("geoimage");
            vb.setDrawOrder(-100);
            BufferedImage im = ImageUtil.convertImage(world.geoimage.getImage(), BufferedImage.TYPE_INT_ARGB);

            if (true) {
                // make image grayscale and mostly transparent
                int d[] = ((DataBufferInt) (im.getRaster().getDataBuffer())).getData();
                for (int i = 0; i < d.length; i++) {
                    int rgb = d[i];
                    int r = (rgb >> 16) & 0xff;
                    int gr = (rgb >> 8) & 0xff;
                    int b = (rgb >> 0) & 0xff;
                    int m = (r + gr + b) / 3;
                    d[i] = (m<<16) + (m<<8) + (m<<0) + (90<<24);
                }
            }

            VisTexture tex = new VisTexture(im);
/*            tex.setMagFilter(true);
              vb.addBack(new VisChain(new VisDepthTest(false,
              new VzImage(tex,
              world.geoimage.image2xy(new double[] {0,0}),
              world.geoimage.image2xy(new double[] {im.getWidth()-1,
              im.getHeight()-1})))));
              vb.addBack(new VisData(new double[3], new VisDataPointStyle(Color.gray, 3)));
*/
            vb.swap();
        }
    }

    public boolean consoleCommand(VisConsole vc, PrintStream out, String command)
    {
        String toks[] = command.trim().split("\\s+");
        if (toks.length==0)
            return false;

        if (toks[0].equals("save")) {
            if (toks.length > 1)
                worldFilePath = toks[1];
            try {
                world.write(worldFilePath);
                out.printf("Saved world to: "+worldFilePath+"\n");
            } catch (IOException ex) {
                out.println("ex: "+ex);
            }
            return true;
        }

        if (toks[0].equals("class")) {
            if (toks.length==2) {
                SimObject sobj = SimWorld.createObject(world, toks[1]);

                if (sobj != null) {
                    simObjectClass = toks[1];
                    out.printf("Class set\n");
                } else {
                    out.printf("Unknown or invalid class name: "+toks[1]+"\n");
                }
                return true;
            } else {
                out.printf("usage: class <classname>\n");
                return true;
            }
        }

        if (toks[0].equals("stop")) {
            world.setRunning(false);
            out.printf("Stopped\n");
            return true;
        }

        if (toks[0].equals("start")) {
            world.setRunning(true);
            out.printf("Started\n");
            return true;
        }

        if (toks[0].equals("toggle-keyboard-gamepad")) {
            keygp.running = !keygp.running;
            out.printf("Keyboard Gamepad "+(keygp.running? "running" : "off") + "\n");
            return true;
        }

        out.printf("Unknown command\n");
        return false;
    }

    public ArrayList<String> consoleCompletions(VisConsole vc, String prefix)
    {
        String cs[] = new String[] { "save", "start", "stop", "toggle-keyboard-gamepad"};

        ArrayList<String> as = new ArrayList<String>();
        for (String s: cs)
            as.add(s);

        for (String s : finder.classes)
            as.add("class "+s);
        return as;
    }

    public static void main(String args[])
    {
        GetOpt gopt = new GetOpt();
        gopt.addBoolean('h', "help", false, "Show this help");
        gopt.addString('w', "world", "", "World file");
        gopt.addString('c', "config", "", "Configuration file");
        gopt.addBoolean('\0', "start", false, "Start simulation automatically");
        gopt.addInt('\0', "fps", 10, "Maximum frame rate");

        if (!gopt.parse(args) || gopt.getBoolean("help") || gopt.getExtraArgs().size() > 0) {
            gopt.doHelp();
            return;
        }

        SimWorld world;
        try {
            Config config = new Config();
            if (gopt.wasSpecified("config"))
                config = new ConfigFile(EnvUtil.expandVariables(gopt.getString("config")));

            if (gopt.getString("world").length() > 0) {
                String worldFilePath = EnvUtil.expandVariables(gopt.getString("world"));
                world = new SimWorld(worldFilePath, config);
            } else {
                world = new SimWorld(config);
            }

        } catch (IOException ex) {
            System.out.println("ex: "+ex);
            ex.printStackTrace();
            return;
        }

        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);
        vc.setTargetFPS(gopt.getInt("fps"));

        JFrame jf = new JFrame("Simulator");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);

        jf.setSize(800,600);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        Simulator editor = new Simulator(vw, vl, new VisConsole(vw,vl,vc), world);

        if (gopt.getBoolean("start")) {
            world.setRunning(true);
        }

    }

    class VisSimWorld implements VisObject
    {
        public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
        {
            synchronized(world) {
                for (SimObject obj : world.objects) {
                    VisChain v = new VisChain(obj.getPose(), obj.getVisObject());
                    v.render(vc, layer, rinfo, gl);
                }
            }
        }
    }

    void draw()
    {
        if (true) {
            VisWorld.Buffer vb = vw.getBuffer("collide-info");
            boolean collide = false;

            SimObject curSelectedObject = selectedObject;
            if (curSelectedObject != null) {
                // does this object now collide with anything else?
                synchronized(world) {
                    for (SimObject so : world.objects) {
                        if (so != curSelectedObject && Collisions.collision(so.getShape(),
                                                                            so.getPose(),
                                                                            curSelectedObject.getShape(),
                                                                            curSelectedObject.getPose())) {
                            collide = true;
                            break;
                        }
                    }
                }
            }
            if (collide)
                vb.addBack(new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_RIGHT,
                                                   new VzText(VzText.ANCHOR.BOTTOM_RIGHT, "<<blue,monospaced-12>>Collision")));

            vb.swap();
        }
    }

    class MyEventHandler extends VisEventAdapter
    {
        double sz = 1;
        Color color = new Color(50,50,50);
        double lastxy[] = null;

        public MyEventHandler()
        {
        }

        public int getDispatchOrder()
        {
            return -10;
        }

        //vis2
        // public void doHelp(HelpOutput houts)
        // {
        //     houts.beginMouseCommands(this);
        //     houts.addMouseCommand(this, HelpOutput.LEFT | HelpOutput.DRAG,
		// 						  "Move selected object (xy plane)");
        //     houts.addMouseCommand(this, HelpOutput.LEFT | HelpOutput.CTRL | HelpOutput.DRAG,
		// 						  "Create or resize selected SimBox");
        //     houts.addMouseCommand(this, HelpOutput.LEFT | HelpOutput.SHIFT | HelpOutput.DRAG,
		// 						  "Rotate selected object (xy plane)");
        //     houts.addMouseCommand(this, HelpOutput.LEFT | HelpOutput.SHIFT | HelpOutput.ALT | HelpOutput.DRAG,
		// 						  "Rotate selected object (yz plane)");
        //     houts.addMouseCommand(this, HelpOutput.RIGHT | HelpOutput.SHIFT | HelpOutput.DRAG,
		// 						  "Rotate selected object (zx plane)");

        //     houts.beginKeyboardCommands(this);
        //     houts.addKeyboardCommand(this, "r", 0, "Set color to Red");
        //     houts.addKeyboardCommand(this, "g", 0, "Set color to Gray");
        //     houts.addKeyboardCommand(this, "b", 0, "Set color to Blue");
        //     houts.addKeyboardCommand(this, "m", 0, "Set color to Magenta");
        //     houts.addKeyboardCommand(this, "c", 0, "Set color to Cyan");
        //     houts.addKeyboardCommand(this, "[1-9]", 0, "Set selected SimBox size");
        //     houts.addKeyboardCommand(this, "delete", 0, "Delete selected object");
        //     houts.addKeyboardCommand(this, "backspace", 0, "Delete selected object");
        // }

        public boolean mouseReleased(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
        {
            lastxy = null;
            if (selectedObject == null)
                return false;

            selectedObject = null;
            draw();

            return true;
        }

        public boolean mouseDragged(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
        {
            if (selectedObject == null)
                return false;

            int mods = e.getModifiersEx();
            boolean shift = (mods & MouseEvent.SHIFT_DOWN_MASK) > 0;
            boolean ctrl = (mods & MouseEvent.CTRL_DOWN_MASK) > 0;
            boolean alt = (mods & MouseEvent.ALT_DOWN_MASK) > 0;

            double xy[] = ray.intersectPlaneXY();
            if (ctrl) {
                if ((mods & InputEvent.BUTTON1_DOWN_MASK) == 0)
                    return false;
                double T[][] = selectedObject.getPose();

                if (selectedObject instanceof SimBox) {
                    // resize
                    SimBox sb = (SimBox) selectedObject;

                    // Generate the four corners
                    ArrayList<double[]> corners = new ArrayList<double[]>();
                    corners.add(new double[] { -sb.sxyz[0]/2, -sb.sxyz[1]/2 });
                    corners.add(new double[] { -sb.sxyz[0]/2, sb.sxyz[1]/2 });
                    corners.add(new double[] { sb.sxyz[0]/2, sb.sxyz[1]/2 });
                    corners.add(new double[] { sb.sxyz[0]/2, -sb.sxyz[1]/2 });
                    corners = LinAlg.transform(T, corners);

                    // which corner is farthest away? (this corner will remain stationary)
                    double furthest[] = null;
                    for (double cxy[] : corners) {
                        if (furthest==null || LinAlg.distance(cxy, xy) > LinAlg.distance(furthest, xy))
                            furthest = cxy;
                    }

                    double Tinv[][] = LinAlg.inverse(T);
                    ArrayList<double[]> newcorners = new ArrayList<double[]>();
                    newcorners.add(furthest);
                    newcorners.add(xy);
                    newcorners = LinAlg.transform(Tinv, newcorners);
                    double p0[] = newcorners.get(0);
                    double p1[] = newcorners.get(1);

                    sb.sxyz[0] = Math.abs(p1[0]-p0[0]);
                    sb.sxyz[1] = Math.abs(p1[1]-p0[1]);
                    sb.T[0][3] = (xy[0] + furthest[0])/2;
                    sb.T[1][3] = (xy[1] + furthest[1])/2;
//                    sb.sxyz[0] = Math.max(MIN_SIZE, 2*Math.abs(xy[0] - T[0][3]));
//                    sb.sxyz[1] = Math.max(MIN_SIZE, 2*Math.abs(xy[1] - T[1][3]));
                } else if (selectedObject instanceof SimSphere) {
                    SimSphere s = (SimSphere) selectedObject;

                    s.r = Math.max(MIN_SIZE, Math.sqrt(LinAlg.sq(xy[0] - T[0][3]) +
                                                       LinAlg.sq(xy[1] - T[1][3])));
                }
            } else if (shift) {
                // rotate
                double T[][] = selectedObject.getPose();
                double rpy[] = LinAlg.matrixToRollPitchYaw(T);
                double t = Math.atan2(xy[1] - T[1][3], xy[0] - T[0][3]);

				if ((mods & InputEvent.BUTTON1_DOWN_MASK) == InputEvent.BUTTON1_DOWN_MASK) {
                    if (alt)
                        rpy[0] = -t;
                    else
                        rpy[2] = t;
                } else if ((mods & InputEvent.BUTTON3_DOWN_MASK) == InputEvent.BUTTON3_DOWN_MASK) {
                    rpy[1] = t;
                } else
                    return false;

                T = LinAlg.xyzrpyToMatrix(new double[]{T[0][3], T[1][3], T[2][3],
                                                       rpy[0], rpy[1], rpy[2]});
                selectedObject.setPose(T);
            } else {
                // translate
                if ((mods & InputEvent.BUTTON1_DOWN_MASK) == 0)
                    return false;
                double T[][] = selectedObject.getPose();
                T[0][3] += xy[0] - lastxy[0];
                T[1][3] += xy[1] - lastxy[1];
                selectedObject.setPose(T);
            }

            draw();
            lastxy = xy;
            return true;
        }

        public boolean keyReleased(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, KeyEvent e)
        {
            keygp.keyReleased(e);
            return false;
        }
        public boolean keyPressed(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, KeyEvent e)
        {
            if (e.getKeyChar() >= '1' && e.getKeyChar() <= '9') {
                sz = Double.parseDouble(""+e.getKeyChar());

                if (selectedObject != null && selectedObject instanceof SimBox) {
                    SimBox sb = (SimBox) selectedObject;
                    sb.sxyz[2] = sz;
                    draw();
                }

                return true;
            }

            char c = e.getKeyChar();
            if (c >='a' && c <='z') {
                switch (e.getKeyChar()) {
                    case 'r':
                        color = Color.red; break;
                    case 'g':
                        color = new Color(50,50,50); break;
                    case 'b':
                        color = Color.blue; break;
                    case 'm':
                        color = Color.magenta; break;
                    case 'c':
                        color = Color.cyan; break;
                }

                if (selectedObject != null && selectedObject instanceof SimBox) {
                    ((SimBox) selectedObject).color = color;
                    draw();
                } else if (selectedObject != null && selectedObject instanceof SimSphere) {
                    ((SimSphere) selectedObject).color = color;
                    draw();
                }


                return true;
            }

            if (selectedObject != null && (e.getKeyCode()==KeyEvent.VK_DELETE || e.getKeyCode()==KeyEvent.VK_BACK_SPACE)) {
                selectedObject.setRunning(false);
                synchronized(world) {
                    world.objects.remove(selectedObject);
                }

                selectedObject = null;
                draw();
                return true;
            }

            keygp.keyPressed(e);
            return false;
        }

        public boolean mousePressed(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
        {
            int mods = e.getModifiersEx();
            boolean shift = (mods&MouseEvent.SHIFT_DOWN_MASK)>0;
            boolean ctrl = (mods&MouseEvent.CTRL_DOWN_MASK)>0;

            double xy[] = ray.intersectPlaneXY();

            if (ctrl) {
                if ((mods & InputEvent.BUTTON1_DOWN_MASK) == 0)
                    return false;
                // create a new object
                selectedObject = SimWorld.createObject(world, simObjectClass);

                double T[][] = LinAlg.identity(4);
                T[0][3] = xy[0];
                T[1][3] = xy[1];
                T[2][3] = sz/2;
                if (selectedObject.getShape().getBoundingRadius() < 0)
                    T[2][3] = 0;
                selectedObject.setPose(T);

                if (selectedObject instanceof SimBox) {
                    ((SimBox) selectedObject).sxyz = new double[] { 1, 1, sz };
                    ((SimBox) selectedObject).color = color;
                }

                synchronized(world) {
                    world.objects.add(selectedObject);
                }

            } else {
                // select an existing object
                double bestd = Double.MAX_VALUE;

                synchronized(world) {
                    for (SimObject obj : world.objects) {

                        double d = Collisions.collisionDistance(ray.getSource(), ray.getDir(), obj.getShape(), obj.getPose());

                        boolean b = Collisions.collision(obj.getShape(), obj.getPose(),
                                                         new SphereShape(0.1), LinAlg.translate(xy[0], xy[1], 0));

                        if (d < bestd) {
                            selectedObject = obj;
                            bestd = d;
                        }
                    }
                }

                if (selectedObject == null)
                    return false;
            }

            draw();
            lastxy = xy;
            return true;
        }
    }

    class FindSimObjects implements lcm.util.ClassDiscoverer.ClassVisitor
    {
        ArrayList<String> classes = new ArrayList<String>();

        public FindSimObjects()
        {
            ClassDiscoverer.findClasses(this);
        }

        public void classFound(String jarfile, Class cls)
        {
            boolean good = false;

            for (Class c : cls.getInterfaces()) {
                if (c.equals(SimObject.class))
                    good = true;
            }

            if (good)
                classes.add(cls.getName());
        }
    }
}
