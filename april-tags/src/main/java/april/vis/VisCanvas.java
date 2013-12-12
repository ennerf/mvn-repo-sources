package april.vis;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.swing.*;
import javax.imageio.*;

import april.jmat.*;
import april.jmat.geom.*;
import april.util.*;

import lcm.lcm.*;

/** A VisCanvas coordinates the rendering and event handling for a
 * collection of VisLayers in a single JComponent.
 **/
public class VisCanvas extends JComponent implements VisSerializable
{
    GLManager glManager = GLManager.getSingleton();
    BufferedImage im = null;
    RedrawTask redrawTask = new RedrawTask();

    int frameCounter;

    long canvasId;

    // protected by synchronizing on 'layers'
    ArrayList<VisLayer> layers = new ArrayList<VisLayer>();
    public VisWorld privateWorld;
    public VisLayer privateLayer;

    EventHandler eh = new EventHandler();

    RenderInfo lastRenderInfo;

    public int popupFrameRates[] = new int[] { 1, 5, 10, 20, 30, 60, 100, 100000 };
    int targetFrameRate = 20;

    public boolean smoothPoints = false;
    public boolean smoothPolygons = false;

    // a list of open movies.
    ArrayList<Movie> movies = new ArrayList<Movie>();
    Movie popupMovie;

    // time-averaged frame rate (empirically measured), but
    // initialized with a good guess.
    double fpsDt = 1.000 / targetFrameRate;

    JFrame layerBufferManager;
    LayerBufferPanel layerBufferPanel;

    boolean showFPS = false;
    public boolean showSizeChanges = true;

    ArrayList<Listener> listeners = new ArrayList<Listener>();

    static {
        // There's a bug in the JDK that is tickled if two threads
        // both attempt to do graphics rendering at the same time;
        // they both invoke a static initializer and the JDK
        // deadlocks. VisCanvas, when used in headless mode, seems to
        // trigger this bug. Thus, we force static initalizers to run
        // with the code below; this will execute before any
        // VisCanvases are created, ensuring that the static
        // initializer is invoked only on one thread.
        //
        // See Sun bug #6995195.
        //  --- ebolson (11/2011)

        BufferedImage im = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics g = im.createGraphics();
    }

    public interface Listener
    {
        public void layerAdded(VisCanvas vc, VisLayer vl);
    }

    public class Movie
    {
        boolean autoframes;
        GZIPOutputStream outs;

        Movie(GZIPOutputStream outs, boolean autoframes)
        {
            this.autoframes = autoframes;
            this.outs = outs;
        }

        synchronized public void addFrame()
        {
            drawSync();
            addFrame(im);
        }

        synchronized public void close() throws IOException
        {
            synchronized(movies) {
                movies.remove(this);
            }

            outs.close();
        }

        synchronized void addFrame(BufferedImage upsideDownImage)
        {
            BufferedImage im = upsideDownImage;

            int width = im.getWidth(), height = im.getHeight();

            byte bdata[] = ((DataBufferByte) (im.getRaster().getDataBuffer())).getData();

            if (true) {
                // flip upside down and convert BGR to RGB

                byte bdata2[] = new byte[bdata.length];
                int pos = 0;

                for (int y = height-1; y >= 0; y--) {
                    for (int x = 0; x < width; x++) {
                        bdata2[pos++] = bdata[y*3*width + 3*x + 2];
                        bdata2[pos++] = bdata[y*3*width + 3*x + 1];
                        bdata2[pos++] = bdata[y*3*width + 3*x + 0];
                    }
                }

                bdata = bdata2;
            }

            try {
                String hdr = "";
                hdr += String.format("# mtime=%d\n", System.currentTimeMillis());
                hdr += String.format("P6 %d %d %d\n", width, height, 255);

                outs.write(hdr.getBytes());
                outs.write(bdata);
                outs.flush();
            } catch (IOException ex) {
                System.out.println("Error writing movie: "+ex);
            }
        }
    }

    public static class RenderInfo
    {
        // The layers, in the order that they were rendered.
        public ArrayList<VisLayer> layers = new ArrayList<VisLayer>();

        // The position of the layers when they were rendered.
        public HashMap<VisLayer, int[]> layerPositions = new HashMap<VisLayer, int[]>();

        public HashMap<VisLayer, VisCameraManager.CameraPosition> cameraPositions = new HashMap<VisLayer, VisCameraManager.CameraPosition>();
    }

    public VisCanvas(VisLayer... layers)
    {
        privateWorld = new VisWorld();
        privateLayer = new VisLayer("VisCanvas Private Layer", privateWorld);
        privateLayer.drawOrder = Integer.MAX_VALUE;
        privateLayer.backgroundColor = new Color(0,0,0,0);
        privateLayer.clearDepth = false;
        privateLayer.eventHandlers.clear();
        addLayer(privateLayer);

        canvasId = VisUtil.allocateID();

        addComponentListener(new MyComponentListener());

        addMouseMotionListener(eh);
        addMouseListener(eh);
        addMouseWheelListener(eh);
        addKeyListener(eh);

        setFocusTraversalKeysEnabled(false);

        for (int i = 0; i < layers.length; i++)
            addLayer(layers[i]);

        new RepaintThread().start();
    }

    public void addListener(Listener listener)
    {
        if (!listeners.contains(listener))
            listeners.add(listener);
    }

    public void addLayer(VisLayer layer)
    {
        layers.add(layer);
        for (Listener listener : listeners)
            listener.layerAdded(this, layer);
    }

    class RepaintThread extends Thread
    {
        public RepaintThread()
        {
            setDaemon(true);
        }

        public void run()
        {
            while (true) {
                try {
                    Thread.sleep(1000 / targetFrameRate);
                } catch (InterruptedException ex) {
                    System.out.println("ex: "+ex);
                }

                if (VisCanvas.this.isVisible())
                    glManager.add(redrawTask);
            }
        }
    }

    class MyComponentListener extends ComponentAdapter
    {
        VisObject lastResizeObject = null;

        public void componentResized(ComponentEvent e)
        {
            VisWorld.Buffer vb = privateWorld.getBuffer("VisCanvas dimensions");
            vb.removeTemporary(lastResizeObject);
            if (showSizeChanges) {
                lastResizeObject = new VisPixCoords(VisPixCoords.ORIGIN.CENTER_ROUND,
                                                    new VisDepthTest(false,
                                                                     new VzText(VzText.ANCHOR.CENTER_ROUND,
                                                                                "<<sansserif-12>>"+
                                                                                String.format("%d x %d", getWidth(), getHeight()))));
                vb.addTemporary(lastResizeObject, 0.750);

                draw();
            }
        }

        public void componentShown(ComponentEvent e)
        {
            draw();
        }
    }

    // This task serves as a synchronization barrier, which we use
    // perform synchronous frame updates.
    class SyncTask implements GLManager.Task
    {
        Object o = new Object();

        public void run()
        {
            synchronized(o) {
                o.notifyAll();
            }
        }
    }

    class RedrawTask implements GLManager.Task
    {
        GL gl;
        int fboId;
        int fboWidth, fboHeight;

        long lastDrawTime = System.currentTimeMillis();

        public void run()
        {
            // GL object must be created from the GLManager thread.
            if (gl == null) {
                gl = new GL();
            }

            int width = getWidth();
            int height = getHeight();

            if (width==0 || height==0 )
                return;

            // XXX Should we only reallocate an FBO if our render size
            // has gotten bigger? If we do this, we should modify
            // getFrame() so that we don't read parts of the image
            // that we don't need.
            if (fboId <= 0 || fboWidth != width || fboHeight != height) {
                if (fboId > 0) {
                    gl.frameBufferDestroy(fboId);
                    fboId = 0;
                }

                fboId = gl.frameBufferCreate(width, height);
                if (fboId < 0) {
                    System.out.println("Failed to create frame buffer. "+width+"\n");
                    return;
                }
                fboWidth = width;
                fboHeight = height;
            }

            gl.frameBufferBind(fboId);

            gl.gldFrameBegin(canvasId);

            ///////////////////////////////////////////////
            // Begin GL Rendering
            int viewport[] = new int[] { 0, 0, getWidth(), getHeight() };

            gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

            gl.glEnable(GL.GL_NORMALIZE);
            gl.glEnable(GL.GL_LIGHTING);

            gl.glLightModeli(GL.GL_LIGHT_MODEL_TWO_SIDE, GL.GL_TRUE);
            gl.glEnable(GL.GL_COLOR_MATERIAL);
            gl.glColorMaterial(GL.GL_FRONT_AND_BACK, GL.GL_AMBIENT_AND_DIFFUSE);
            gl.glMaterialf(GL.GL_FRONT_AND_BACK, GL.GL_SHININESS, 0);
            gl.glMaterialfv(GL.GL_FRONT_AND_BACK, GL.GL_SPECULAR, new float[] {.1f, .1f, .1f, .1f});

            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(GL.GL_LEQUAL);

            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

            // don't do z-buffer updates for fully transparent
            // pixels. VisFont relies on this, because texture atlas
            // for individual characters tend to overlap when
            // rendering.
            gl.glEnable(GL.GL_ALPHA_TEST);
            gl.glAlphaFunc(GL.GL_GREATER, 0);

            gl.glPolygonMode(GL.GL_FRONT, GL.GL_FILL);
            gl.glPolygonMode(GL.GL_BACK, GL.GL_FILL);

            gl.glDisable(GL.GL_LINE_STIPPLE);

            gl.glShadeModel(GL.GL_SMOOTH);

            if (smoothPoints) {
                gl.glEnable(GL.GL_POINT_SMOOTH);
                gl.glHint(GL.GL_POINT_SMOOTH_HINT, GL.GL_NICEST);
            }

            // VzGrid benefits tremendously from this
            gl.glEnable(GL.GL_LINE_SMOOTH);
            gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);

            if (smoothPolygons) {
                gl.glEnable(GL.GL_POLYGON_SMOOTH);
                gl.glHint(GL.GL_POLYGON_SMOOTH_HINT, GL.GL_NICEST);
            }

            gl.glEnable(GL.GL_SCISSOR_TEST);

            long mtime = System.currentTimeMillis();

            // rinfo records where we rendered everything, which we'll
            // need in order to properly handle events.
            RenderInfo rinfo = new RenderInfo();
            synchronized(layers) {
                rinfo.layers.addAll(layers);
            }

            if (true) {
                VisWorld.Buffer vb = privateWorld.getBuffer("FPS Rate");

                if (showFPS)
                    vb.addBack(new VisDepthTest(false,
                                                new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_LEFT,
                                                                        new VzText(VzText.ANCHOR.BOTTOM_LEFT_ROUND,
                                                                                   "<<white,monospaced-12>>"+
                                                                                   String.format("%5.1f fps %c",
                                                                                                 getMeasuredFPS(),
                                                                                                 (frameCounter&1) > 0 ? '.' : ' ')))));
                vb.swap();
            }

            Collections.sort(rinfo.layers);

            if (true) {
                gl.glClearDepth(1.0);
                gl.glClearColor(0, 0, 0, 0);

                gl.glClear(GL.GL_DEPTH_BUFFER_BIT | GL.GL_COLOR_BUFFER_BIT);
            }

            for (VisLayer layer : rinfo.layers) {

                layer.render(VisCanvas.this, rinfo, gl, viewport, mtime);

            }

            gl.gldFrameEnd(canvasId);

            int err = gl.glGetError();
            if (err != 0)
                System.out.printf("glGetError: %d\n", err);

            im = gl.getImage(im);

            repaint();
            lastRenderInfo = rinfo;

            synchronized(movies) {
                for (Movie m : movies) {
                    if (m.autoframes)
                        m.addFrame(im);
                }
            }

            if (true) {
                long now = System.currentTimeMillis();
                double dt = (now - lastDrawTime) / 1000.0;

                // clamp to [0,1]
                double dtclamp = Math.max(0, Math.min(1, dt));

                // larger alpha = favor existing estimate
                //
                // after T seconds of updates, we'd like the
                // effective weight of the existing estimate to be
                // w.  In one second, there where will be (T / dt)
                // updates. Solve for alpha.
                //
                // alpha^(T / dt) = w
                //
                //  (T / dt) * log(alpha) = log(w)
                // alpha = exp( log(w) / (T / dt) )

                double T = 1;
                double w = 0.2;
                double fpsAlpha = Math.exp( Math.log(w) / (T / dtclamp) );

                fpsDt = fpsDt * fpsAlpha + dt * (1.0 - fpsAlpha);
                lastDrawTime = now;
            }

            frameCounter++;
        }
    }

    public double getMeasuredFPS()
    {
        return 1.0 / fpsDt;
    }

    /** Caution: can return null. **/
    public RenderInfo getLastRenderInfo()
    {
        return lastRenderInfo;
    }

    /** Schedule a repainting operation as soon as possible, even if
     * the target frame rate is exceeded.
     **/
    public void draw()
    {
        glManager.add(redrawTask);
    }

    public void paintComponent(Graphics _g)
    {
        Graphics2D g = (Graphics2D) _g;

        if (im != null) {
            g.translate(0, getHeight());
            g.scale(1, -1);
            g.drawImage(im, 0, 0, null);
        }
    }

    class EventHandler implements MouseMotionListener, MouseListener, MouseWheelListener, KeyListener
    {
        VisLayer mousePressedLayer;
        VisLayer keyboardFocusLayer;

        int lastex = -1, lastey = -1;

        public void keyPressed(KeyEvent e)
        {
            dispatchKeyEvent(e);
        }

        public void keyReleased(KeyEvent e)
        {
            dispatchKeyEvent(e);
        }

        public void keyTyped(KeyEvent e)
        {
            dispatchKeyEvent(e);
        }

        public void mouseWheelMoved(MouseWheelEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mouseDragged(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mouseMoved(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mousePressed(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mouseReleased(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mouseClicked(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        public void mouseEntered(MouseEvent e)
        {
            dispatchMouseEvent(e);
            requestFocus();
        }

        public void mouseExited(MouseEvent e)
        {
            dispatchMouseEvent(e);
        }

        // Find a layer that can consume this event.
        void dispatchMouseEvent(MouseEvent e)
        {
            RenderInfo rinfo = lastRenderInfo;
            if (rinfo == null)
                return;

            int ex = e.getX();
            int ey = getHeight() - e.getY();

            lastex = ex;
            lastey = ey;

            // these events go to the layer that got the MOUSE_PRESSED
            // event, not the layer under the event.
            if (e.getID() == MouseEvent.MOUSE_DRAGGED || e.getID() == MouseEvent.MOUSE_RELEASED) {
                if (mousePressedLayer != null && rinfo.cameraPositions.get(mousePressedLayer) != null)
                    dispatchMouseEventToLayer(VisCanvas.this, mousePressedLayer, rinfo,
                                              rinfo.cameraPositions.get(mousePressedLayer).computeRay(ex, ey), e);

                return;
            }

            for (int lidx = rinfo.layers.size()-1; lidx >= 0; lidx--) {
                VisLayer layer = rinfo.layers.get(lidx);
                if (!layer.enabled)
                    continue;

                int pos[] = rinfo.layerPositions.get(layer);

                GRay3D ray = rinfo.cameraPositions.get(layer).computeRay(ex, ey);

                if (ex >= pos[0] && ey >= pos[1] &&
                    ex < pos[0]+pos[2] && ey < pos[1]+pos[3]) {

                    boolean handled = dispatchMouseEventToLayer(VisCanvas.this, layer, rinfo, ray, e);

                    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                        if (handled)
                            mousePressedLayer = layer;
                        else
                            mousePressedLayer = null;
                    }

                    if (handled)
                        return;
                }
            }
        }

        // this is used by dispatchMouseEvent. It processes the event
        // handlers within the layer, returning true if one of them
        // consumed the event.
        boolean dispatchMouseEventToLayer(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
        {
            boolean handled = false;

            synchronized (layer.eventHandlers) {
                for (VisEventHandler eh : layer.eventHandlers) {

                    switch (e.getID()) {
                        case MouseEvent.MOUSE_PRESSED:
                            mousePressedLayer = layer;
                            handled = eh.mousePressed(VisCanvas.this, layer, rinfo, ray, e);
                            break;
                        case MouseEvent.MOUSE_RELEASED:
                            handled = eh.mouseReleased(VisCanvas.this, layer, rinfo, ray, e);
                            break;
                        case MouseEvent.MOUSE_CLICKED:
                            handled = eh.mouseClicked(VisCanvas.this, layer, rinfo, ray, e);
                            break;
                        case MouseEvent.MOUSE_DRAGGED:
                            handled = eh.mouseDragged(VisCanvas.this, layer, rinfo, ray, e);
                            break;
                        case MouseEvent.MOUSE_MOVED:
                            handled = eh.mouseMoved(VisCanvas.this, layer, rinfo, ray, e);
                            break;
                        case MouseEvent.MOUSE_WHEEL:
                            handled = eh.mouseWheel(VisCanvas.this, layer, rinfo, ray, (MouseWheelEvent) e);
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
                        break;
                }
            }

            return handled;
        }

        void dispatchKeyEvent(KeyEvent e)
        {
            RenderInfo rinfo = lastRenderInfo;
            if (rinfo == null)
                return;

            for (int lidx = rinfo.layers.size()-1; lidx >= 0; lidx--) {
                VisLayer layer = rinfo.layers.get(lidx);
                if (!layer.enabled)
                    continue;

                int pos[] = rinfo.layerPositions.get(layer);

                if (lastex >= pos[0] && lastey >= pos[1] &&
                    lastex < pos[0]+pos[2] && lastey < pos[1]+pos[3]) {

                    boolean handled = dispatchKeyEventToLayer(VisCanvas.this, layer, rinfo, e);

                    if (handled)
                        return;
                }
            }
        }

        boolean dispatchKeyEventToLayer(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, KeyEvent e)
        {
            boolean handled = false;

            synchronized (layer.eventHandlers) {
                for (VisEventHandler eh : layer.eventHandlers) {

                    switch (e.getID()) {
                        case KeyEvent.KEY_TYPED:
                            handled = eh.keyTyped(VisCanvas.this, layer, rinfo, e);
                            break;
                        case KeyEvent.KEY_PRESSED:
                            handled = eh.keyPressed(VisCanvas.this, layer, rinfo, e);
                            break;
                        case KeyEvent.KEY_RELEASED:
                            handled = eh.keyReleased(VisCanvas.this, layer, rinfo, e);
                            break;
                        default:
                            System.out.println("Unhandled key event id: "+e.getID());
                            break;
                    }

                    if (handled)
                        break;
                }

                return handled;
            }
        }
    }

    public void drawSync()
    {
        SyncTask st = new SyncTask();

        synchronized(st.o) {
            boolean success = false;
            int i = 0;
            while (!success) {
                if (i++ > 0)
                    TimeUtil.sleep(1);

                success = glManager.add(redrawTask); // ensure we're queued
                success &= glManager.add(st);
            }
            try {
                st.o.wait();
            } catch (InterruptedException ex) {
                System.out.println("VisCanvas: drawSync interrupted!");
            }
        }
    }

    // XXX trying to avoid an image copy, assuming (possibly erroneously) that BufferedImage will
    //     be ok with multiple threads reading its contents
    public BufferedImage getLatestFrame()
    {
        return im;
    }

    /** Forces a synchronous redraw and then draws. **/
    public void writeScreenShot(File file, String format)
    {
        drawSync();

        // Our image will be upside down. let's flip it.
        BufferedImage thisim = this.im;
        if (thisim == null) {
            System.out.println("WRN: Screenshot failed due to null image");
            return;
        }
        thisim = ImageUtil.flipVertical(im);
        try {
            ImageIO.write(thisim, format, file);
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
            return;
        }

        System.out.println("Screen shot written to "+file.getPath());
        return;
    }

    /** There are two ways to make movies which differ in when frames
     * are added to the movie. In 'autoframes' mode, every rendered
     * frame is added to the movie. In manual mode, frames are added
     * programmatically by calls to movieAddFrame **/
    public Movie movieCreate(String path, boolean autoframes) throws IOException
    {
        if (!path.endsWith(".ppms.gz"))
            path += ".ppms.gz";

        Movie m = new Movie(new GZIPOutputStream(new FileOutputStream(path)), autoframes);

        synchronized(movies) {
            movies.add(m);
        }

        return m;
    }

    public void populatePopupMenu(JPopupMenu jmenu)
    {
        if (true) {
            JMenuItem jmi = new JMenuItem("Save screenshot (.png)");
            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {

                        Calendar c = new GregorianCalendar();

                        String s = String.format("%4d%02d%02d_%02d%02d%02d_%03d", c.get(Calendar.YEAR),
                                                 c.get(Calendar.MONTH)+1,
                                                 c.get(Calendar.DAY_OF_MONTH),
                                                 c.get(Calendar.HOUR_OF_DAY),
                                                 c.get(Calendar.MINUTE),
                                                 c.get(Calendar.SECOND),
                                                 c.get(Calendar.MILLISECOND)
                            );

                        String path = "p"+s+".png";

                        writeScreenShot(new File(path), "png");
                    }
                });
            jmenu.add(jmi);
        }

        if (true) {
            JMenuItem jmi = new JMenuItem("Save scene (.vsc)");
            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {

                        Calendar c = new GregorianCalendar();

                        String path = String.format("v%4d%02d%02d_%02d%02d%02d_%03d.vsc", c.get(Calendar.YEAR),
                                                    c.get(Calendar.MONTH)+1,
                                                    c.get(Calendar.DAY_OF_MONTH),
                                                    c.get(Calendar.HOUR_OF_DAY),
                                                    c.get(Calendar.MINUTE),
                                                    c.get(Calendar.SECOND),
                                                    c.get(Calendar.MILLISECOND)
                            );

                        try {
                            DataOutputStream outs = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(path))));
                            ObjectWriter ow = new ObjectWriter(outs);
                            ow.writeInt(getWidth());
                            ow.writeInt(getHeight());
                            ow.writeObject(VisCanvas.this);
                            outs.flush();
                            outs.close();
                            System.out.println("wrote "+path);

                        } catch (IOException ex) {
                            System.out.println("ex: "+ex);
                        }
                    }
                });
            jmenu.add(jmi);
        }

        if (popupMovie == null) {
            JMenuItem jmi = new JMenuItem("Record Movie");
            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        Calendar c = new GregorianCalendar();
                        String path = String.format("m%4d%02d%02d_%02d%02d%02d_%03d.ppms.gz", c.get(Calendar.YEAR),
                                                    c.get(Calendar.MONTH)+1,
                                                    c.get(Calendar.DAY_OF_MONTH),
                                                    c.get(Calendar.HOUR_OF_DAY),
                                                    c.get(Calendar.MINUTE),
                                                    c.get(Calendar.SECOND),
                                                    c.get(Calendar.MILLISECOND)
                            );

                        try {
                            popupMovie = movieCreate(path, true);
                            System.out.println("beginning movie "+path);
                        } catch (IOException ex) {
                            System.out.println("ex: "+ex);
                        }
                    }
                });
            jmenu.add(jmi);
        } else {
            JMenuItem jmi = new JMenuItem("Stop Recording Movie");
            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        try {
                            popupMovie.close();
                            popupMovie = null;
                            System.out.println("movie ended");
                        } catch (IOException ex) {
                            System.out.println("ex: "+ex);
                        }
                    }
                });
            jmenu.add(jmi);
        }


        if (true) {
            JCheckBoxMenuItem jmi = new JCheckBoxMenuItem("Show FPS counter");
            jmi.setSelected(showFPS);

            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        showFPS ^= true;
                    }
                });

            jmenu.add(jmi);
        }

        if (true) {
            JMenu jm = new JMenu("Max Frame Rate");
            JRadioButtonMenuItem jmis[] = new JRadioButtonMenuItem[popupFrameRates.length];
            ButtonGroup group = new ButtonGroup();

            for (int i = 0; i < jmis.length; i++) {
                jmis[i] = new JRadioButtonMenuItem(""+popupFrameRates[i]);
                group.add(jmis[i]);
                jm.add(jmis[i]);

                jmis[i].addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            JRadioButtonMenuItem jmi = (JRadioButtonMenuItem) e.getSource();
                            VisCanvas.this.targetFrameRate = Integer.parseInt(jmi.getText());
                        }
                    });
            }

            int bestIndex = 0;
            for (int i = 0; i < popupFrameRates.length; i++) {
                if (Math.abs(popupFrameRates[i] - targetFrameRate) < Math.abs(popupFrameRates[bestIndex] - targetFrameRate))
                    bestIndex = i;
            }

            jmis[bestIndex].setSelected(true);

            jmenu.add(jm);
        }

        if (true) {
            JMenuItem jmi = new JMenuItem("Show Layer/Buffer Manager");
            jmi.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    if (layerBufferManager == null) {
                        layerBufferManager = new JFrame("Layer/Buffer Manager");
                        layerBufferManager.setLayout(new BorderLayout());
                        layerBufferPanel = new LayerBufferPanel(VisCanvas.this);
                        layerBufferManager.add(layerBufferPanel, BorderLayout.CENTER);
                        layerBufferManager.setSize(400, 600);
                    } else {
                        layerBufferPanel.rebuild();
                    }
                    layerBufferManager.setVisible(true);
                }
            });

            jmenu.add(jmi);
        }
    }

    public int getTargetFPS()
    {
        return targetFrameRate;
    }

    public void setTargetFPS(double fps)
    {
        targetFrameRate = (int) fps;
    }

    /** for serialization only **/
    public VisCanvas(ObjectReader r)
    {
        canvasId = VisUtil.allocateID();

        addComponentListener(new MyComponentListener());

        addMouseMotionListener(eh);
        addMouseListener(eh);
        addMouseWheelListener(eh);
        addKeyListener(eh);

        setFocusTraversalKeysEnabled(false);
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeInt(layers.size());
        for (VisLayer layer : layers)
            outs.writeObject(layer);
        outs.writeObject(privateWorld);
        outs.writeObject(privateLayer);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        int n = ins.readInt();
        for (int i = 0; i < n; i++)
            addLayer((VisLayer) ins.readObject());

        privateWorld = (VisWorld) ins.readObject();
        privateLayer = (VisLayer) ins.readObject();

        new RepaintThread().start();
    }
}

