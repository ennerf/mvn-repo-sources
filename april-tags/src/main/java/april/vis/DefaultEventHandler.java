package april.vis;

import java.awt.event.*;
import java.util.*;
import java.io.*;

import april.jmat.*;
import april.jmat.geom.*;

public class DefaultEventHandler implements VisEventHandler, VisSerializable
{
    // the point that we try to keep under the cursor when doing mouse drags.
    double manipulationPoint[];

    public double SCROLL_ZOOM_FACTOR = Math.pow(2, 0.25);
    public double SHIFT_SCROLL_ZOOM_FACTOR = Math.pow(2, 1);

    HashMap<Integer, VisCameraManager.CameraPosition> bookmarks = new HashMap<Integer, VisCameraManager.CameraPosition>();

    public DefaultEventHandler()
    {
    }

    public int getDispatchOrder()
    {
        return 10;
    }

    /** Given a coordinate in scene coordinates dq, modify the camera
        such that the screen projection of point xyz moves to pixel
        coordinates (winx,winy). If preservez is used, panning is
        implemented differently so that the height of the camera does
        not change.

        winx, winy are in opengl screen coordinates (0,0) in bottom left
    **/
    double[] windowSpacePanTo(double xyz[], double winx, double winy, boolean preserveZ,
                              double P[][], int viewport[],
                              double eye[], double lookat[], double up[])
    {
        double mv[] = new double[3];

        for (int iter = 0; iter < 100; iter++) {

            double M[][] = VisUtil.lookAt(eye, lookat, up);

            // where does the point project to now?
            double winpos0[] = VisUtil.project(xyz, M, P, viewport);

            double err[] = LinAlg.subtract(new double[] { winx, winy }, new double[] { winpos0[0], winpos0[1] });

            double lookVec[] = LinAlg.normalize(LinAlg.subtract(lookat, eye));
            double left[] = LinAlg.crossProduct(up, lookVec);

            double dir1[] = LinAlg.copy(up);
            double dir2[] = LinAlg.copy(left);

            double J[][] = computePanJacobian(xyz, P, viewport, eye, lookat, up,
                                              dir1, dir2);
            if (preserveZ) {
                dir1[2] = 0;
                dir2[2] = 0;
            }

            double weights[] = LinAlg.matrixAB(LinAlg.inverse(J), err);
            double dx[] = LinAlg.add(LinAlg.scale(dir1, weights[0]),
                                     LinAlg.scale(dir2, weights[1]));
            eye = LinAlg.add(eye, dx);
            lookat = LinAlg.add(lookat, dx);
            mv = LinAlg.add(mv, dx);

            if (LinAlg.magnitude(dx) < 0.001)
                break;
        }

        return mv;
    }

    // How does the projected (opengl window coordinate) position of 3D point
    // xyz change with respect to moving the eye and lookat positions
    // by d, where d = lambda1 * dir1 + lambda2 * dir.
    //
    // J = [   d project(xyz)_x / d lambda1         d project(xyz)_x / d lambda2
    //     [   d project(xyz)_y / d lambda1         d project(xyz)_y / d lambda2
    static double[][] computePanJacobian(double xyz[], double P[][], int viewport[],
                                         double eye[], double c[], double up[],
                                         double dir1[], double dir2[])
    {
        // XXX do symmetric numerical jacobians?
        double eps = Math.max(0.00001, 0.00001*LinAlg.magnitude(eye));

        double M0[][] = VisUtil.lookAt(eye, c, up);
        double w0[] = VisUtil.project(xyz, M0, P, viewport);

        double M1[][] = VisUtil.lookAt(LinAlg.add(eye, LinAlg.scale(dir1, eps)),
                                       LinAlg.add(c, LinAlg.scale(dir1, eps)),
                                       up);
        double w1[] = VisUtil.project(xyz, M1, P, viewport);

        double M2[][] = VisUtil.lookAt(LinAlg.add(eye, LinAlg.scale(dir2, eps)),
                                       LinAlg.add(c, LinAlg.scale(dir2, eps)),
                                       up);
        double w2[] = VisUtil.project(xyz, M2, P, viewport);

        return new double[][] { { (w1[0] - w0[0]) / eps, (w2[0] - w0[0]) / eps },
                                { (w1[1] - w0[1]) / eps, (w2[1] - w0[1]) / eps } };
    }

    /** Return true if you've consumed the event. **/
    public boolean mousePressed(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
    {
        int ex = e.getX();
        int ey = vc.getHeight() - e.getY();
        int mods = e.getModifiersEx();

        boolean shift = (mods & MouseEvent.SHIFT_DOWN_MASK)>0;
        boolean ctrl = (mods & MouseEvent.CTRL_DOWN_MASK)>0;

        if ((mods & InputEvent.BUTTON1_DOWN_MASK) != 0) {

            if (shift || ctrl)
                return false;

            // pick a point that we will try to keep beneath the cursor
            this.manipulationPoint = vl.manipulationManager.pickManipulationPoint(vc, vl, rinfo, ray);

            return true;
        }

        if ((mods & InputEvent.BUTTON3_DOWN_MASK) != 0) {
            lastRotateX = ex;
            lastRotateY = ey;

            VisCameraManager.CameraPosition cameraPosition = rinfo.cameraPositions.get(vl);
            this.manipulationPoint = vl.manipulationManager.pickManipulationPoint(vc, vl, rinfo,
                                                                                  cameraPosition.computeRay(cameraPosition.layerViewport[0] + cameraPosition.layerViewport[2]/2,
                                                                                                            cameraPosition.layerViewport[1] + cameraPosition.layerViewport[3]/2));

            return true;
        }

        return false;
    }

    int lastRotateX, lastRotateY;

    public boolean mouseDragged(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
    {
        int ex = e.getX();
        int ey = vc.getHeight() - e.getY();
        int mods = e.getModifiersEx();

        boolean shift = (mods & MouseEvent.SHIFT_DOWN_MASK) > 0;
        boolean ctrl = (mods & MouseEvent.CTRL_DOWN_MASK) > 0;

        VisCameraManager.CameraPosition cameraPosition = rinfo.cameraPositions.get(vl);

        if ((mods&InputEvent.BUTTON1_DOWN_MASK) > 0) {

            if (manipulationPoint == null)
                return false;

            double mv[] = windowSpacePanTo(manipulationPoint, ex, ey, vl.cameraManager.preserveZWhenTranslating(),
                                           cameraPosition.getProjectionMatrix(), cameraPosition.getViewport(),
                                           cameraPosition.eye, cameraPosition.lookat, cameraPosition.up);

            vl.cameraManager.uiLookAt(LinAlg.add(mv, cameraPosition.eye),
                                      LinAlg.add(mv, cameraPosition.lookat),
                                      cameraPosition.up,
                                      false);
            return true;
        }

        if ((mods&InputEvent.BUTTON3_DOWN_MASK) > 0) {

            double dx = ex - lastRotateX;
            double dy = ey - lastRotateY;

            boolean only_roll = shift && !ctrl;
            boolean only_pitch = shift && ctrl;
            boolean only_yaw = !shift && ctrl;
            boolean any_rotate = !shift && !ctrl;

            // for some rotations, we must convert a motion in pixels to a rotation.
            double pixelsToRadians = Math.PI / Math.max(cameraPosition.layerViewport[2], cameraPosition.layerViewport[3]);

            double interfaceMode = 3; // vc.getViewManager().getInterfaceMode();

            double qcum[] = new double[] {1, 0, 0, 0};

            if (interfaceMode < 2) {
                only_roll = false;
                only_pitch = false;
                only_yaw = false;
                any_rotate = false;
            }

            if (interfaceMode == 2.0)
                only_roll = true;

            if (only_roll) {
                double cx = cameraPosition.layerViewport[0] + cameraPosition.layerViewport[2]/2;
                double cy = cameraPosition.layerViewport[1] + cameraPosition.layerViewport[3]/2;

                double v1[] = new double[] { ex - cx,
                                             - (ey - cy),
                                             0};
                double v1mag = LinAlg.magnitude(v1);

                double v0[] = new double[] { lastRotateX - cx,
                                             - (lastRotateY - cy),
                                             0};
                double v0mag = LinAlg.magnitude(v0);

                v1 = LinAlg.normalize(v1);
                v0 = LinAlg.normalize(v0);

                if (dx != 0 || dy != 0) {
                    double theta = MathUtil.mod2pi(Math.atan2(v1[1], v1[0]) - Math.atan2(v0[1], v0[0]));
                    double p2eye[] = LinAlg.subtract(cameraPosition.eye, cameraPosition.lookat);
                    if (!Double.isNaN(theta))
                        qcum = LinAlg.quatMultiply(qcum, LinAlg.angleAxisToQuat(theta, p2eye));
                }
            } else if (only_yaw) {
                if (dx != 0 || dy != 0) {
                    double theta = dx * pixelsToRadians;
                    double p2eye[] = LinAlg.subtract(cameraPosition.eye, cameraPosition.lookat);

                    if (interfaceMode < 3) {
                        if (!Double.isNaN(theta))
                            qcum = LinAlg.quatMultiply(qcum, LinAlg.angleAxisToQuat(-theta, new double[] {0,0,1}));
                    } else {
                        if (!Double.isNaN(theta))
                            qcum = LinAlg.quatMultiply(qcum, LinAlg.angleAxisToQuat(-theta, cameraPosition.up));
                    }
                }
            } else if (only_pitch) {
                double p2eye[] = LinAlg.subtract(cameraPosition.eye, cameraPosition.lookat);
                double left[] = LinAlg.crossProduct(cameraPosition.up, p2eye);
                if (dx != 0 || dy != 0) {
                    double theta = dy * pixelsToRadians;
                    if (!Double.isNaN(theta))
                        qcum = LinAlg.quatMultiply(qcum, LinAlg.angleAxisToQuat(-theta, left));
                }
            } else if (any_rotate) {
                // unconstrained rotation
                qcum = LinAlg.quatMultiply(qcum, LinAlg.angleAxisToQuat(-dx*pixelsToRadians, cameraPosition.up));

                double p2eye[] = LinAlg.subtract(cameraPosition.eye, cameraPosition.lookat);
                double left[] = LinAlg.crossProduct(cameraPosition.up, p2eye);

                qcum = LinAlg.quatMultiply(qcum, LinAlg.angleAxisToQuat(dy*pixelsToRadians, left));
            }

            lastRotateX = ex;
            lastRotateY = ey;

            vl.cameraManager.uiRotate(qcum);

            return true;
        }

        return false;
    }

    public boolean mouseReleased(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
    {
        manipulationPoint = null;

        return false;
    }

    public boolean mouseClicked(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
    {
        return false;
    }

    public boolean mouseMoved(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseEvent e)
    {
        return false;
    }

    public boolean mouseWheel(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, GRay3D ray, MouseWheelEvent e)
    {
        int ex = e.getX();
        int ey = vc.getHeight() - e.getY();
        int mods = e.getModifiersEx();

        boolean shift = (mods & MouseEvent.SHIFT_DOWN_MASK)>0;
        boolean ctrl = (mods & MouseEvent.CTRL_DOWN_MASK)>0;

        VisCameraManager.CameraPosition cameraPosition = rinfo.cameraPositions.get(vl);

        double factor = 1.0;

        if (!shift)
            factor = SCROLL_ZOOM_FACTOR;
        else
            factor = SHIFT_SCROLL_ZOOM_FACTOR;

        if (e.getWheelRotation() > 0)
            factor = 1.0 / factor;

        double mp[] = vl.manipulationManager.pickManipulationPoint(vc, vl, rinfo, ray);

        /*
        double lookdir[] = LinAlg.normalize(LinAlg.subtract(cameraPosition.lookat, cameraPosition.eye));
        double dist = LinAlg.distance(cameraPosition.eye, cameraPosition.lookat);
        double newdist = dist * factor;
        //newdist = Math.min(cameraPosition.zclip_far / 5.0, newdist);
        //newdist = Math.max(cameraPosition.zclip_near * 5.0, newdist);

        double fakeEye[] = LinAlg.subtract(cameraPosition.lookat, LinAlg.scale(lookdir, newdist));
        double fakeView[] = LinAlg.subtract(cameraPosition.lookat, fakeEye);

        double movedir[] = LinAlg.normalize(LinAlg.subtract(mp, cameraPosition.eye));
        GRay3D moveray = new GRay3D(LinAlg.copy(cameraPosition.eye), movedir);

        cameraPosition.eye = moveray.intersectPlaneXY(fakeEye[2]); // XXX XY only?
        cameraPosition.lookat = LinAlg.add(cameraPosition.eye, fakeView);
        */

        // a simple triangle-scaling method
        double mp2eye[]    = LinAlg.subtract(cameraPosition.eye, mp);
        double mp2lookat[] = LinAlg.subtract(cameraPosition.lookat, mp);

        mp2eye    = LinAlg.scale(mp2eye, factor);
        mp2lookat = LinAlg.scale(mp2lookat, factor);

        double neweye[]    = LinAlg.add(mp, mp2eye);
        double newlookat[] = LinAlg.add(mp, mp2lookat);

        cameraPosition.eye = neweye;
        cameraPosition.lookat = newlookat;

        vl.cameraManager.uiLookAt(cameraPosition.eye,
                                  cameraPosition.lookat,
                                  cameraPosition.up,
                                  false);

        return true;
    }

    public boolean keyTyped(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, KeyEvent e)
    {
        return false;
    }

    public boolean keyPressed(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, KeyEvent e)
    {
        int code = e.getKeyCode();
        int mods = e.getModifiersEx();
        boolean shift = (mods&MouseEvent.SHIFT_DOWN_MASK) > 0;
        boolean ctrl = (mods&MouseEvent.CTRL_DOWN_MASK) > 0;
        boolean alt = shift & ctrl;

        if (ctrl) {
            switch (code) {
                case KeyEvent.VK_F1:
                case KeyEvent.VK_F2:
                case KeyEvent.VK_F3:
                case KeyEvent.VK_F4:
                case KeyEvent.VK_F5:
                case KeyEvent.VK_F6:
                case KeyEvent.VK_F7:
                case KeyEvent.VK_F8:
                case KeyEvent.VK_F9:
                case KeyEvent.VK_F10:
                    System.out.println("Set bookmark: "+code);
                    VisCameraManager.CameraPosition cameraPosition = rinfo.cameraPositions.get(vl);
                    bookmarks.put(code, cameraPosition);
                    return true;
            }

            return false;
        }

        // no modifiers
        switch (code) {
            case KeyEvent.VK_F1:
            case KeyEvent.VK_F2:
            case KeyEvent.VK_F3:
            case KeyEvent.VK_F4:
            case KeyEvent.VK_F5:
            case KeyEvent.VK_F6:
            case KeyEvent.VK_F7:
            case KeyEvent.VK_F8:
            case KeyEvent.VK_F9:
            case KeyEvent.VK_F10:
                System.out.println("Play bookmark: "+code);
                VisCameraManager.CameraPosition cameraPosition = bookmarks.get(code);
                if (cameraPosition != null) {
                    vl.cameraManager.goBookmark(cameraPosition);
                    return true;
                }
                break;
        }

        return false;
    }

    public boolean keyReleased(VisCanvas vc, VisLayer vl, VisCanvas.RenderInfo rinfo, KeyEvent e)
    {
        return false;
    }

    public DefaultEventHandler(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeDoubles(manipulationPoint);
        outs.writeDouble(SCROLL_ZOOM_FACTOR);
        outs.writeDouble(SHIFT_SCROLL_ZOOM_FACTOR);

        outs.writeInt(bookmarks.size());
        for (Integer i : bookmarks.keySet()) {
            VisCameraManager.CameraPosition pos = bookmarks.get(i);
            outs.writeInt(i);
            outs.writeObject(pos);
        }
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        manipulationPoint = ins.readDoubles();
        SCROLL_ZOOM_FACTOR = ins.readDouble();
        SHIFT_SCROLL_ZOOM_FACTOR = ins.readDouble();

        int n = ins.readInt();
        for (int i = 0; i < n; i++) {
            int id = ins.readInt();
            VisCameraManager.CameraPosition pos = (VisCameraManager.CameraPosition) ins.readObject();
            bookmarks.put(id, pos);
        }
    }
}
