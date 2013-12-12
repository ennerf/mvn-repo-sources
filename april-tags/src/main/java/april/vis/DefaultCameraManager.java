package april.vis;

import april.jmat.*;

import java.awt.event.*;
import java.io.*;
import javax.swing.*;

public class DefaultCameraManager implements VisCameraManager, VisSerializable
{
    // we record two sets of positions to support
    double eye0[], lookat0[], up0[];
    double scalex0 = 1.0, scaley0 = 1.0;
    double perspectiveness0;
    long mtime0;

    double eye1[], lookat1[], up1[];
    double scalex1 = 1.0, scaley1 = 1.0;
    double perspectiveness1 = 1.0;
    long mtime1;

    double defaultEye[] = new double[] { 0, 0, 100 };
    double defaultLookat[] = new double[] { 0, 0, 0 };
    double defaultUp[] = new double[] { 0, 1, 0 };

    double follow_lastpos[], follow_lastquat[];

    // we don't interpolate between these values.
    public double interfaceMode = 2.5;
    public double perspective_fovy_degrees = 50;
    public double zclip_near = 0.025;
    public double zclip_far = 50000;

    public double popupInterfaceModes[] = new double[] { 1.5, 2.0, 2.5, 3.0 };
    public int popupAnimateDelays[] = new int[] { 0, 50, 100, 200, 400, 800 };

    public int UI_ANIMATE_MS = popupAnimateDelays[2];
    public int BOOKMARK_ANIMATE_MS = 500;

    public int FIT_ANIMATE_MS = 0;

    Fit fit;

    public DefaultCameraManager()
    {
        uiDefault();
    }

    public void uiDefault()
    {
        eye1 = LinAlg.copy(defaultEye);
        lookat1 = LinAlg.copy(defaultLookat);
        up1 = LinAlg.copy(defaultUp);
        mtime1 = System.currentTimeMillis() + UI_ANIMATE_MS;
    }

    public void setDefaultPosition(double eye[], double lookat[], double up[])
    {
        defaultEye = LinAlg.copy(eye);
        defaultLookat = LinAlg.copy(lookat);
        defaultUp = LinAlg.copy(up);
    }

    public CameraPosition getCameraTarget()
    {
        CameraPosition p = new CameraPosition();

        p.layerViewport = null;
        p.perspective_fovy_degrees = perspective_fovy_degrees;
        p.zclip_near = zclip_near;
        p.zclip_far = zclip_far;

        p.eye = LinAlg.copy(eye1);
        p.lookat = LinAlg.copy(lookat1);
        p.up = LinAlg.copy(up1);
        p.perspectiveness = perspectiveness1;
        p.scalex = scalex1;
        p.scaley = scaley1;

        return p;
    }

    public CameraPosition getCameraPosition(VisCanvas vc, int viewport[], int layerViewport[], VisLayer vl, long mtime)
    {
        CameraPosition p = new CameraPosition();

        p.layerViewport = LinAlg.copy(layerViewport);
        p.perspective_fovy_degrees = perspective_fovy_degrees;
        p.zclip_near = zclip_near;
        p.zclip_far = zclip_far;

        double eye[] = null, lookat[] = null, up[] = null;
        double scalex, scaley;
        double perspectiveness;

        if (fit != null) {
            synchronized(this) {
                Fit f = fit;
                fit = null;

                // use ortho
                perspectiveness = 0;

                lookat1 = new double[] { (f.xy0[0] + f.xy1[0]) / 2,
                                         (f.xy0[1] + f.xy1[1]) / 2,
                                         0 };

                // width, height, & perspective of Fit
                double wF = f.xy1[0] - f.xy0[0];
                double hF = f.xy1[1] - f.xy0[1];
                double pF = wF / hF;

                // perspective of Viewport
                double pV = layerViewport[2] * 1.0 / layerViewport[3];

                double tAngle = Math.tan(Math.toRadians(perspective_fovy_degrees/2));

                // if viewport wider, set on fit height, else set on fit (adjusted)width
                double h = 0.5 * (pV > pF ? hF : wF / pV) / tAngle;
                eye1 = new double[] { (f.xy0[0] + f.xy1[0]) / 2,
                                      (f.xy0[1] + f.xy1[1]) / 2,
                                      Math.abs(h) };

                up1 = new double[] { 0, 1, 0 };
                mtime1 = f.mtime;

                if (f.setDefault) {
                    setDefaultPosition(eye1, lookat1, up1);
                }
            }
        }

        if (eye0 == null || mtime >= mtime1) {
            eye = LinAlg.copy(eye1);
            lookat = LinAlg.copy(lookat1);
            up = LinAlg.copy(up1);
            perspectiveness = perspectiveness1;
            scalex = scalex1;
            scaley = scaley1;
        } else if (mtime <= mtime0) {
            eye = LinAlg.copy(eye0);
            lookat = LinAlg.copy(lookat0);
            up = LinAlg.copy(up0);
            perspectiveness = perspectiveness0;
            scalex = scalex0;
            scaley = scaley0;
        } else {

            double alpha1 = ((double) mtime - mtime0) / (mtime1 - mtime0);
            double alpha0 = 1.0 - alpha1;

            // linearly interpolate
            eye = LinAlg.add(LinAlg.scale(eye0, alpha0), LinAlg.scale(eye1, alpha1));
            lookat = LinAlg.add(LinAlg.scale(lookat0, alpha0), LinAlg.scale(lookat1, alpha1));
            up = LinAlg.add(LinAlg.scale(up0, alpha0), LinAlg.scale(up1, alpha1));
            perspectiveness = perspectiveness0*alpha0 + perspectiveness1*alpha1;
            scalex = scalex0*alpha0 + scalex1*alpha1;
            scaley = scaley0*alpha0 + scaley1*alpha1;

            // tweak so eye-to-lookat position is the right distance
            double dist0 = LinAlg.distance(eye0, lookat0);
            double dist1 = LinAlg.distance(eye1, lookat1);

            double dist = dist0*alpha0 + dist1*alpha1;
            double eye2p[] = LinAlg.normalize(LinAlg.subtract(eye, lookat));
            eye = LinAlg.add(lookat, LinAlg.scale(eye2p, dist));
        }

        eye0 = eye;
        lookat0 = lookat;
        up0 = up;
        perspectiveness0 = perspectiveness;
        scalex0 = scalex;
        scaley0 = scaley;
        mtime0 = mtime;

        //////////////////////////////////////////////////
        // fix up position
        if (true) {
            if (interfaceMode <= 2.0) {
                eye[0] = lookat[0];
                eye[1] = lookat[1];
                eye[2] = Math.abs(eye[2]);
                up[2] = 0;
                if (LinAlg.magnitude(up) < 1E-10)
                    up = new double[] {0, 1, 0};
                else
                    up = LinAlg.normalize(up);
                lookat[2] = 0;

                if (interfaceMode < 2)
                    up = new double[] {0, 1, 0};

            } else if (interfaceMode == 2.5) {

                lookat[2] = 0;

                // step one: make sure horizon is level
                double left[] = LinAlg.crossProduct(up, LinAlg.normalize(LinAlg.subtract(lookat, eye)));
                left[2] = 0;
                left = LinAlg.normalize(left);

                // step two: don't allow them to get us "upside down". This has the nice effect
                // that it is easy for the user to "lock" into looking straight down.
                up[2] = Math.max(0.0, up[2]);
                up = LinAlg.makePerpendicular(up, left);
                up = LinAlg.normalize(up);

                // Now, recompute the eye position by computing the new lookat direction.
                double dir[] = LinAlg.crossProduct(up, left);

                // preserve the previous distance from camera to lookat
                double dist = LinAlg.distance(eye, lookat);
                eye = LinAlg.add(lookat, LinAlg.scale(dir, dist));

            } else if (interfaceMode == 3.0) {
                // do nothing
            }
        }

        //////////////////////////////////////////////////
        // prevent bad zooms that hit clipping
        if (true) {
            double lookdir[] = LinAlg.normalize(LinAlg.subtract(lookat, eye));
            double dist = LinAlg.distance(eye, lookat);
            dist = Math.min(zclip_far / 3.0, dist);
            dist = Math.max(zclip_near * 3.0, dist);
            eye = LinAlg.subtract(lookat, LinAlg.scale(lookdir, dist));
        }

        //////////////////////////////////////////////////
        p.eye = LinAlg.copy(eye);
        p.lookat = LinAlg.copy(lookat);
        p.up = LinAlg.copy(up);
        p.perspectiveness = perspectiveness;
        p.scalex = scalex;
        p.scaley = scaley;

        return p;
    }

    public boolean preserveZWhenTranslating()
    {
        if (interfaceMode == 3.0)
            return false;

        return true;
    }

    public void setPerspectiveness(double p)
    {
        this.perspectiveness1 = p;
        this.mtime1 = System.currentTimeMillis() + UI_ANIMATE_MS;
    }

    public void uiLookAt(double eye[], double lookat[], double up[], boolean setDefault)
    {
        this.eye1 = LinAlg.copy(eye);
        this.lookat1 = LinAlg.copy(lookat);
        this.up1 = LinAlg.copy(up);
        this.mtime1 = System.currentTimeMillis() + UI_ANIMATE_MS;

        if (setDefault)
            setDefaultPosition(this.eye1, this.lookat1, this.up1);
    }

    public void uiRotate(double q[])
    {
        double toEyeVec[] = LinAlg.subtract(eye1, lookat1);
        double newToEyeVec[] = LinAlg.quatRotate(q, toEyeVec);
        double neweye[] = LinAlg.add(lookat1, newToEyeVec);
        double newup[] = LinAlg.quatRotate(q, up1);

        eye1 = neweye;
        up1 = newup;
        this.mtime1 = System.currentTimeMillis() + UI_ANIMATE_MS;
    }

    public void goBookmark(CameraPosition pos)
    {
        eye1 = pos.eye;
        up1 = pos.up;
        lookat1 = pos.lookat;
        perspectiveness1 = pos.perspectiveness;
        perspective_fovy_degrees = pos.perspective_fovy_degrees;
        scalex1 = pos.scalex;
        scaley1 = pos.scaley;
        this.mtime1 = System.currentTimeMillis() + BOOKMARK_ANIMATE_MS;
    }

    // Does UI animation instead of lengthy bookmark animation
    public void goUI(CameraPosition pos)
    {
        eye1 = pos.eye;
        up1 = pos.up;
        lookat1 = pos.lookat;
        perspectiveness1 = pos.perspectiveness;
        perspective_fovy_degrees = pos.perspective_fovy_degrees;
        scalex1 = pos.scalex;
        scaley1 = pos.scaley;
        this.mtime1 = System.currentTimeMillis();
    }

    class Fit
    {
        double xy0[], xy1[];
        long mtime;
        boolean setDefault;

        Fit(double xy0[], double xy1[], boolean setDefault)
        {
            this.xy0 = LinAlg.copy(xy0);
            this.xy1 = LinAlg.copy(xy1);
            this.mtime = System.currentTimeMillis() + FIT_ANIMATE_MS;
            this.setDefault = setDefault;
        }
    }

    public void fit2D(double xy0[], double xy1[], boolean setDefault)
    {
        // we can't compute the fit now because we don't know the
        // viewport dimensions. Thus we'll queue up the fit command
        // for the next time getCameraPosition is called.
        synchronized(this) {
            fit = new Fit(xy0, xy1, setDefault);
        }
    }

    public void populatePopupMenu(JPopupMenu jmenu)
    {
        if (true) {
            JMenuItem jmi = new JMenuItem("Default Camera Position");
            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        uiDefault();
                    }
                });
            jmenu.add(jmi);
        }

        if (true) {
            JMenuItem jmi = new JMenuItem("Snap to nearest axis");
            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        double lookvec[] = LinAlg.subtract(lookat1, eye1);
                        double lookdist = LinAlg.distance(lookat1, eye1);

                        lookvec = vectorSnapTo(lookvec);

                        uiLookAt(LinAlg.subtract(lookat1, LinAlg.scale(lookvec, lookdist)),
                                 lookat1,
                                 vectorSnapTo(up1),
                                 false);
                    }
                });
            jmenu.add(jmi);
        }

        if (true) {
            JCheckBoxMenuItem jmi = new JCheckBoxMenuItem("Orthographic projection");
            if (perspectiveness1 == 0)
                jmi.setSelected(true);

            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        perspectiveness1 = 0;
                        mtime1 = System.currentTimeMillis() + UI_ANIMATE_MS;
                    }
                });

            jmenu.add(jmi);
        }

        /*
        if (true) {
            JCheckBoxMenuItem jmi = new JCheckBoxMenuItem("Perspectiveness = 0.25");
            if (perspectiveness1 == 0.25)
                jmi.setSelected(true);

            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        perspectiveness1 = 0.25;
                        mtime1 = System.currentTimeMillis() + UI_ANIMATE_MS;
                    }
                });

            jmenu.add(jmi);
        }

        if (true) {
            JCheckBoxMenuItem jmi = new JCheckBoxMenuItem("Perspectiveness = 0.5");
            if (perspectiveness1 == 0.5)
                jmi.setSelected(true);

            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        perspectiveness1 = 0.5;
                        mtime1 = System.currentTimeMillis() + UI_ANIMATE_MS;
                    }
                });

            jmenu.add(jmi);
        }

        if (true) {
            JCheckBoxMenuItem jmi = new JCheckBoxMenuItem("Perspectiveness = 0.75");
            if (perspectiveness1 == 0.75)
                jmi.setSelected(true);

            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        perspectiveness1 = 0.75;
                        mtime1 = System.currentTimeMillis() + UI_ANIMATE_MS;
                    }
                });

            jmenu.add(jmi);
        }
        */

        if (true) {
            JCheckBoxMenuItem jmi = new JCheckBoxMenuItem("Perspective projection");
            if (perspectiveness1 == 1)
                jmi.setSelected(true);

            jmi.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        perspectiveness1 = 1;
                        mtime1 = System.currentTimeMillis() + UI_ANIMATE_MS;
                    }
                });

            jmenu.add(jmi);
        }

        if (true) {
            JMenu jm = new JMenu("Camera Smoothing (ms)");
            JRadioButtonMenuItem jmis[] = new JRadioButtonMenuItem[popupAnimateDelays.length];
            ButtonGroup group = new ButtonGroup();

            for (int i = 0; i < jmis.length; i++) {
                jmis[i] = new JRadioButtonMenuItem(""+popupAnimateDelays[i]);
                group.add(jmis[i]);
                jm.add(jmis[i]);

                jmis[i].addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            JRadioButtonMenuItem jmi = (JRadioButtonMenuItem) e.getSource();
                            DefaultCameraManager.this.UI_ANIMATE_MS = Integer.parseInt(jmi.getText());
                        }
                    });
            }

            int bestIndex = 0;
            for (int i = 0; i < popupAnimateDelays.length; i++) {
                if (Math.abs(popupAnimateDelays[i] - UI_ANIMATE_MS) < Math.abs(popupAnimateDelays[bestIndex] - UI_ANIMATE_MS))
                    bestIndex = i;
            }

            jmis[bestIndex].setSelected(true);

            jmenu.add(jm);
        }

        if (true) {
            JMenu jm = new JMenu("Interface Mode");
            JRadioButtonMenuItem jmis[] = new JRadioButtonMenuItem[popupInterfaceModes.length];
            ButtonGroup group = new ButtonGroup();

            for (int i = 0; i < jmis.length; i++) {
                jmis[i] = new JRadioButtonMenuItem(String.format("%.1f", popupInterfaceModes[i]));
                group.add(jmis[i]);
                jm.add(jmis[i]);

                jmis[i].addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            JRadioButtonMenuItem jmi = (JRadioButtonMenuItem) e.getSource();
                            DefaultCameraManager.this.interfaceMode = Double.parseDouble(jmi.getText());
                        }
                    });
            }

            int bestIndex = 0;
            for (int i = 0; i < popupInterfaceModes.length; i++) {
                if (Math.abs(popupInterfaceModes[i] - interfaceMode) < Math.abs(popupInterfaceModes[bestIndex] - interfaceMode))
                    bestIndex = i;
            }

            jmis[bestIndex].setSelected(true);

            jmenu.add(jm);
        }
    }

    // Find the axis direction closest to v.
    static double[] vectorSnapTo(double v[])
    {
        int maxidx = 0;
        double maxvalue = 0;

        for (int i = 0; i < v.length; i++) {
            if (Math.abs(v[i]) > Math.abs(maxvalue)) {
                maxidx = i;
                maxvalue = v[i];
            }
            v[i] = 0;
        }

        double w[] = new double[v.length];
        w[maxidx] = maxvalue > 0 ? 1 : -1;
        return w;
    }

    public void follow(double pos[], double quat[], boolean followYaw)
    {
        if (follow_lastpos != null) {
            follow(follow_lastpos, follow_lastquat, pos, quat, followYaw);
        }

        follow_lastpos = LinAlg.copy(pos);
        follow_lastquat = LinAlg.copy(quat);
    }

    public void follow(double lastPos[], double lastQuat[], double newPos[], double newQuat[],
                       boolean followYaw)
    {
        if (followYaw) {
            // follow X,Y, and orientation.

            // our strategy is to compute the eye,lookAt relative to
            // the vehicle position, then use the new vehicle position
            // to recompute new eye/lookAt. We'll keep 'up' as it is.

            double v2eye[] = LinAlg.subtract(lastPos, eye1);
            double v2look[] = LinAlg.subtract(lastPos, lookat1);

            // this is the vector that the robot is newly pointing in
            double vxy[] = new double[] { 1, 0, 0};
            vxy = LinAlg.quatRotate(newQuat, vxy);
            vxy[2] = 0;

            // where was the car pointing last time?
            double theta = LinAlg.quatToRollPitchYaw(newQuat)[2] -
                LinAlg.quatToRollPitchYaw(lastQuat)[2];

            double zaxis[] = new double[] {0,0,1};
            double q[] = LinAlg.angleAxisToQuat(theta, zaxis);

            v2look = LinAlg.quatRotate(q, v2look);
            double newLookAt[] = LinAlg.subtract(newPos, v2look);

            v2eye = LinAlg.quatRotate(q, v2eye);
            double newEye[] = LinAlg.subtract(newPos, v2eye);
            double newUp[] = LinAlg.quatRotate(q, up1);

            uiLookAt(newEye, newLookAt, newUp, false);
        } else {
            // follow in X/Y (but not yaw)

            double dpos[] = LinAlg.subtract(newPos, lastPos);

            double newEye[] = LinAlg.add(eye1, dpos);
            double newLookAt[] = LinAlg.add(lookat1, dpos);

            uiLookAt(newEye, newLookAt, up1, false);
        }
    }

    public DefaultCameraManager(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeDoubles(eye0);
        outs.writeDoubles(lookat0);
        outs.writeDoubles(up0);
        outs.writeDouble(perspectiveness0);
        outs.writeDouble(scalex0);
        outs.writeDouble(scaley0);
        outs.writeLong(mtime0);

        outs.writeDoubles(eye1);
        outs.writeDoubles(lookat1);
        outs.writeDoubles(up1);
        outs.writeDouble(perspectiveness1);
        outs.writeDouble(scalex1);
        outs.writeDouble(scaley1);
        outs.writeLong(mtime1);

        outs.writeDoubles(defaultEye);
        outs.writeDoubles(defaultLookat);
        outs.writeDoubles(defaultUp);

        outs.writeDouble(interfaceMode);
        outs.writeDouble(perspective_fovy_degrees);
        outs.writeDouble(zclip_near);
        outs.writeDouble(zclip_far);

        outs.writeDoubles(popupInterfaceModes);
        outs.writeInts(popupAnimateDelays);
        outs.writeInt(UI_ANIMATE_MS);
        outs.writeInt(BOOKMARK_ANIMATE_MS);
        outs.writeInt(FIT_ANIMATE_MS);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        eye0 = ins.readDoubles();
        lookat0 = ins.readDoubles();
        up0 = ins.readDoubles();
        perspectiveness0 = ins.readDouble();
        scalex0 = ins.readDouble();
        scaley0 = ins.readDouble();
        mtime0 = ins.readLong();

        eye1 = ins.readDoubles();
        lookat1 = ins.readDoubles();
        up1 = ins.readDoubles();
        perspectiveness1 = ins.readDouble();
        scalex1 = ins.readDouble();
        scaley1 = ins.readDouble();
        mtime1 = ins.readLong();

        defaultEye = ins.readDoubles();
        defaultLookat = ins.readDoubles();
        defaultUp = ins.readDoubles();

        interfaceMode = ins.readDouble();
        perspective_fovy_degrees = ins.readDouble();
        zclip_near = ins.readDouble();
        zclip_far = ins.readDouble();

        popupInterfaceModes = ins.readDoubles();
        popupAnimateDelays = ins.readInts();
        UI_ANIMATE_MS = ins.readInt();
        BOOKMARK_ANIMATE_MS = ins.readInt();
        FIT_ANIMATE_MS = ins.readInt();
    }
}
