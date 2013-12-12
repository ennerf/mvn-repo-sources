package april.vis;

import april.jmat.*;
import april.jmat.geom.*;

import java.awt.event.*;
import javax.swing.*;
import java.io.*;

public interface VisCameraManager
{
    public static class CameraPosition implements VisSerializable
    {
        public double eye[], lookat[], up[];
        public int layerViewport[];

        public double perspectiveness = 1.0;

        // field of view is the *total* field of view, i.e., in both
        // the +y and -y direction. Thus, the maximum allowed value is
        // 90.
        public double perspective_fovy_degrees = 50;

        public double zclip_near = 0.1;
        public double zclip_far = 5000;

        public double scalex = 1.0, scaley = 1.0;

        public CameraPosition()
        {
        }

        public double[][] getProjectionMatrix()
        {
            int width = layerViewport[2];
            int height = layerViewport[3];

            double aspect = ((double) width) / height;
            double dist = LinAlg.distance(eye, lookat);

            double pM[][] = VisUtil.gluPerspective(perspective_fovy_degrees, aspect,
                                                   zclip_near, zclip_far);
            double oM[][] = VisUtil.glOrtho(-dist * aspect / 2, dist*aspect / 2, -dist/2, dist/2,
                                            -zclip_far, zclip_far);

            // Virtually all of the visual difference between
            // perspective and orthographic mode occurs when
            // perspectiveness is near zero. As a result, linear
            // interpolation is not very smooth. We rescale the scale
            // factor here to provide a more aesthetically pleasing
            // interpolation.
            double perspectiveness_scaled = Math.pow(perspectiveness, 3);

            double M[][] = LinAlg.add(LinAlg.scale(pM, perspectiveness_scaled), LinAlg.scale(oM, 1.0 - perspectiveness_scaled));
            return M;
        }

        public double[][] getModelViewMatrix()
        {
            double M[][] = VisUtil.lookAt(eye, lookat, up);

            double S[][] = new double[][] { { scalex, 0, 0, 0 },
                                            { 0, scaley, 0, 0 },
                                            { 0, 0, 1, 0 },
                                            { 0, 0, 0, 1 } };
            return LinAlg.matrixAB(M,S);
        }

        public int[] getViewport()
        {
            return LinAlg.copy(layerViewport);
        }

        public GRay3D computeRay(double winx, double winy)
        {
            double rayStart[] = VisUtil.unProject(new double[] { winx, winy, 0 },
                                                  getModelViewMatrix(), getProjectionMatrix(),
                                                  layerViewport);

            double rayEnd[] = VisUtil.unProject(new double[] { winx, winy, 1 },
                                                getModelViewMatrix(), getProjectionMatrix(),
                                                layerViewport);

            return new GRay3D(rayStart, LinAlg.subtract(rayEnd, rayStart));
        }

        public CameraPosition(ObjectReader r)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeDoubles(eye);
            outs.writeDoubles(lookat);
            outs.writeDoubles(up);
            outs.writeInts(layerViewport);

            outs.writeDouble(perspectiveness);
            outs.writeDouble(perspective_fovy_degrees);
            outs.writeDouble(zclip_near);
            outs.writeDouble(zclip_far);
            outs.writeDouble(scalex);
            outs.writeDouble(scaley);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            eye = ins.readDoubles();
            lookat = ins.readDoubles();
            up = ins.readDoubles();
            layerViewport = ins.readInts();

            perspectiveness = ins.readDouble();
            perspective_fovy_degrees = ins.readDouble();
            zclip_near = ins.readDouble();
            zclip_far = ins.readDouble();
            scalex = ins.readDouble();
            scaley = ins.readDouble();
        }
    }

    /** Should only be called by VisCanvas **/
    public CameraPosition getCameraPosition(VisCanvas vc, int viewport[], int layerViewport[], VisLayer vl, long mtime);

    boolean preserveZWhenTranslating();

    /** called in response to user interface actions that want to
     * change the camera position, for example DefaultEventHandler, to
     * handle pans. This is an "absolute" command, in that the camera
     * position requested is the ultimate goal position of the camera.
     **/
    public void uiLookAt(double eye[], double lookAt[], double up[], boolean setDefault);

    /** Called in response to user interface actions that result in
        rotations. In contrast to uiLookAt, this command is
        "cumulative"; the goal camera position should be adjusted by this amount.
    **/
    public void uiRotate(double q[]);

    public void goBookmark(CameraPosition pos);
    public void goUI(CameraPosition pos);

    public CameraPosition getCameraTarget();

    // Default the camera position to the default position
    public void uiDefault();

    public void setDefaultPosition(double eye[], double lookAt[], double up[]);

    public void fit2D(double xy0[], double xy1[], boolean setDefault);

    public void populatePopupMenu(JPopupMenu jmenu);


}
