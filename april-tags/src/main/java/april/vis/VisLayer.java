package april.vis;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

import lcm.lcm.*;

/** A VisLayer provides information about how to render a VisWorld. A
 * VisLayer is owned by a single VisCanvas.
 **/
public class VisLayer implements Comparable<VisLayer>, VisSerializable
{
    boolean enabled = true;

    public String name;

    public boolean clearDepth = true;

    // The objects in the world.
    public VisWorld world;

    // low numbers draw before big numbers
    public int drawOrder;

    // The color of the layer before any objects are drawn
    public Color backgroundColor = new Color(0, 0, 0, 255); //Color.black;

    // Where are the lights that illuminate the objects in the world?
    public ArrayList<VisLight> lights = new ArrayList<VisLight>();

    // Where is the camera?
    public VisCameraManager cameraManager = new DefaultCameraManager();

    // How is the layer positioned with respect to the viewport?
    public VisLayerManager layerManager = new DefaultLayerManager();

    // How do we determine what point to translate/rotate around in
    // response to a user interface click?
    public VisManipulationManager manipulationManager = new DefaultManipulationManager();

    // synchronize on list before accessing. Invariant: event handlers
    // are sorted by priority, higher priority handlers first. Use addEventHandler!
    public ArrayList<VisEventHandler> eventHandlers = new ArrayList<VisEventHandler>();

    VisEventHandler popupMenu = new DefaultPopupMenu(this);

    boolean showCameraPosition = false;

    public int popupBackgroundColors[] = new int[] { 0x000000, 0x808080, 0xffffff };

    HashSet<String> disabledBuffers = new HashSet<String>();

    public VisLayer(VisWorld vw)
    {
        this("Unnamed Layer", vw);
    }

    public VisLayer(String name, VisWorld vw)
    {
        this.world = vw;
        this.name = name;

        lights.add(new VisLight(new float[] { 100f, 150f, 120f, 1.0f },
                                new float[] { .4f, .4f, .4f, 1.0f},
                                new float[] { .8f, .8f, .8f, 1.0f },
                                new float[] { .5f, .5f, .5f, 1.0f}));


        lights.add(new VisLight(new float[] { -100f, -150f, 120f, 1.0f },
                                new float[] { .1f, .1f, .1f, 1.0f},
                                new float[] { .1f, .1f, .1f, 1.0f },
                                new float[] { .5f, .5f, .5f, 1.0f}));

        addEventHandler(new DefaultEventHandler());
        addEventHandler(popupMenu);
    }

    public void render(VisCanvas vc, VisCanvas.RenderInfo rinfo, GL gl, int viewport[], long mtime)
    {
        if (!isEnabled())
            return;

        int layerPosition[] = layerManager.getLayerPosition(vc, viewport, this, mtime);
        rinfo.layerPositions.put(this, layerPosition);

        gl.glScissor(layerPosition[0], layerPosition[1], layerPosition[2], layerPosition[3]);
        gl.glViewport(layerPosition[0], layerPosition[1], layerPosition[2], layerPosition[3]);

        int clearflags = 0;

        if (clearDepth) {
            gl.glClearDepth(1.0);
            clearflags |= GL.GL_DEPTH_BUFFER_BIT;
        }

        if (backgroundColor.getAlpha() != 0) {
            gl.glClearColor(backgroundColor.getRed()/255f,
                            backgroundColor.getGreen()/255f,
                            backgroundColor.getBlue()/255f,
                            backgroundColor.getAlpha()/255f);
            clearflags |= GL.GL_COLOR_BUFFER_BIT;
        }

        if (clearflags != 0)
            gl.glClear(clearflags);

        ///////////////////////////////////////////////////////
        // set up lighting

        // The position of lights is transformed by the
        // current model view matrix, thus we load the
        // identity matrix before configuring the lights.
        gl.glMatrixMode(GL.GL_MODELVIEW);
        gl.glLoadIdentity();

        for (int i = 0; i < lights.size(); i++) {
            VisLight light = lights.get(i);
            gl.glLightfv(GL.GL_LIGHT0 + i, GL.GL_POSITION, light.position);
            gl.glLightfv(GL.GL_LIGHT0 + i, GL.GL_AMBIENT, light.ambient);
            gl.glLightfv(GL.GL_LIGHT0 + i, GL.GL_DIFFUSE, light.diffuse);
            gl.glLightfv(GL.GL_LIGHT0 + i, GL.GL_SPECULAR, light.specular);

            gl.glEnable(GL.GL_LIGHT0 + i);
        }

        // position the camera
        VisCameraManager.CameraPosition cameraPosition = cameraManager.getCameraPosition(vc,
                                                                                         viewport,
                                                                                         layerPosition,
                                                                                         this,
                                                                                         mtime);
        rinfo.cameraPositions.put(this, cameraPosition);

        gl.glMatrixMode(GL.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glMultMatrix(cameraPosition.getProjectionMatrix());

        gl.glMatrixMode(GL.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glMultMatrix(cameraPosition.getModelViewMatrix());

        // draw the objects
        world.render(vc, this, rinfo, gl);

        if (showCameraPosition) {

            VisObject vo = new VisDepthTest(false,
                                            new VisPixCoords(VisPixCoords.ORIGIN.BOTTOM_RIGHT,
                                                                    new VzText(VzText.ANCHOR.BOTTOM_RIGHT_ROUND,
                                                                               "<<white,monospaced-12>>"+
                                                                               String.format("eye:    %15.5f %15.5f %15.5f\n" +
                                                                                             "lookat: %15.5f %15.5f %15.5f\n" +
                                                                                             "up:     %15.5f %15.5f %15.5f\n",
                                                                                             cameraPosition.eye[0], cameraPosition.eye[1], cameraPosition.eye[2],
                                                                                             cameraPosition.lookat[0], cameraPosition.lookat[1], cameraPosition.lookat[2],
                                                                                             cameraPosition.up[0], cameraPosition.up[1], cameraPosition.up[2]))));

            System.out.printf("vl.cameraManager.uiLookAt(new double[] { %15.5f, %15.5f, %15.5f },\n"+
                              "                          new double[] { %15.5f, %15.5f, %15.5f },\n"+
                              "                          new double[] { %15.5f, %15.5f, %15.5f }, true);\n\n",
                              cameraPosition.eye[0], cameraPosition.eye[1], cameraPosition.eye[2],
                              cameraPosition.lookat[0], cameraPosition.lookat[1], cameraPosition.lookat[2],
                              cameraPosition.up[0], cameraPosition.up[1], cameraPosition.up[2]);

            vo.render(vc, this, rinfo, gl);
        }

        // undo our lighting
        for (int i = 0; i < lights.size(); i++) {
            gl.glDisable(GL.GL_LIGHT0 + i);
        }
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean v)
    {
        enabled = v;
    }

    public void addEventHandler(VisEventHandler eh)
    {
        synchronized(eventHandlers) {
            eventHandlers.add(eh);
            Collections.sort(eventHandlers, new EventHandlerComparator());
        }
    }

    class EventHandlerComparator implements Comparator<VisEventHandler>
    {
        public int compare(VisEventHandler a, VisEventHandler b)
        {
            return a.getDispatchOrder() - b.getDispatchOrder();
        }
    }

    public int compareTo(VisLayer vl)
    {
        return drawOrder - vl.drawOrder;
    }

    public void populatePopupMenu(JPopupMenu jmenu)
    {
        // background color
        if (true) {
            JMenu jm = new JMenu("Background Color");
            JRadioButtonMenuItem jmis[] = new JRadioButtonMenuItem[popupBackgroundColors.length];
            ButtonGroup group = new ButtonGroup();

            for (int i = 0; i < jmis.length; i++) {
                jmis[i] = new JRadioButtonMenuItem(String.format("%08x", popupBackgroundColors[i]));
                group.add(jmis[i]);
                jm.add(jmis[i]);

                jmis[i].addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        JRadioButtonMenuItem jmi = (JRadioButtonMenuItem) e.getSource();
                        backgroundColor = new Color(Integer.parseInt(jmi.getText(), 16));
                    }
                });
            }

            int bestIndex = 0;
            int v = backgroundColor.getRGB() & 0xffffff;
            for (int i = 0; i < popupBackgroundColors.length; i++) {
                if (Math.abs(popupBackgroundColors[i] - v) < Math.abs(popupBackgroundColors[bestIndex] - v))
                    bestIndex = i;
            }

            jmis[bestIndex].setSelected(true);

            jmenu.add(jm);
        }

        // buffer menu
        if (true) {
            JMenu jm = new JMenu("Buffers");

            synchronized(world.buffers) {

                JCheckBoxMenuItem jmis[] = new JCheckBoxMenuItem[world.buffers.size()];

                for (int i = 0; i < world.buffers.size(); i++) {
                    VisWorld.Buffer vb = world.buffers.get(i);

                    jmis[i] = new JCheckBoxMenuItem(vb.getName());
                    jmis[i].setSelected(!disabledBuffers.contains(vb.name));

                    jmis[i].addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            JCheckBoxMenuItem jmi = (JCheckBoxMenuItem) e.getSource();
                            String bufferName = jmi.getText();

                            VisWorld.Buffer vb = world.getBuffer(bufferName);

                            setBufferEnabled(vb.name, !isBufferEnabled(vb.name));
                        }
                    });

                    jm.add(jmis[i]);
                }
            }

            if (true) {
                JCheckBoxMenuItem jmi = new JCheckBoxMenuItem("Show Camera Position");
                jmi.setSelected(showCameraPosition);

                jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        showCameraPosition ^= true;
                    }
                });

                jmenu.add(jmi);
            }

            jmenu.add(jm);
        }
    }

    public void setBufferEnabled(String name, boolean v)
    {
        if (v)
            disabledBuffers.remove(name);
        else
            disabledBuffers.add(name);
    }

    public boolean isBufferEnabled(String name)
    {
        return !disabledBuffers.contains(name);
    }

    /** For use only be serialization **/
    public VisLayer(ObjectReader obj)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeBoolean(enabled);
        outs.writeUTF(name);
        outs.writeBoolean(clearDepth);
        outs.writeObject(world);
        outs.writeInt(drawOrder);
        outs.writeColor(backgroundColor);

        outs.writeInt(lights.size());
        for (VisLight light : lights)
            outs.writeObject(light);

        outs.writeObject(cameraManager);
        outs.writeObject(layerManager);
        outs.writeObject(manipulationManager);

        outs.writeInt(disabledBuffers.size());
        for (String s : disabledBuffers)
            outs.writeUTF(s);

        outs.writeInt(eventHandlers.size());
        for (VisEventHandler eh : eventHandlers)
            outs.writeObject(eh);

        outs.writeObject(popupMenu);

        outs.writeInts(popupBackgroundColors);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        enabled = ins.readBoolean();
        name = ins.readUTF();
        clearDepth = ins.readBoolean();
        world = (VisWorld) ins.readObject();
        drawOrder = ins.readInt();
        backgroundColor = ins.readColor();

        int n = ins.readInt();
        for (int i = 0; i < n; i++)
            lights.add((VisLight) ins.readObject());

        cameraManager = (VisCameraManager) ins.readObject();
        layerManager = (VisLayerManager) ins.readObject();
        manipulationManager = (VisManipulationManager) ins.readObject();

        n = ins.readInt();
        for (int i = 0; i < n; i++)
            disabledBuffers.add(ins.readUTF());

        n = ins.readInt();
        for (int i = 0; i < n; i++)
            addEventHandler((VisEventHandler) ins.readObject());

        popupMenu = (VisEventHandler) ins.readObject();

        popupBackgroundColors = ins.readInts();
    }

}
