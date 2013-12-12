package april.vis;

import java.awt.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import april.jmat.*;

/** Loads and represents an object in OFF format **/
public class VzOFF implements VisObject
{
    String path;

    VzMesh mesh;
    VzMesh.Style style;

    double xyz_min[] = new double[] { Double.MAX_VALUE,
                                      Double.MAX_VALUE,
                                      Double.MAX_VALUE };

    double xyz_max[] = new double[] { -Double.MAX_VALUE,
                                      -Double.MAX_VALUE,
                                      -Double.MAX_VALUE };

    public VzOFF(String path, VzMesh.Style style) throws IOException
    {
        this.path = path;
        this.style = style;

        BufferedReader ins = new BufferedReader(new FileReader(new File(path)));

        FloatArray vertexArray = new FloatArray();
        IntArray indexArray = new IntArray();
        FloatArray normalArray;

        String header = ins.readLine();
        if (!header.equals("OFF"))
            throw new IOException("Not an OFF file");

        int nvertexArray, nfaces, nedges;
        if (true) {
            String sizes = ins.readLine();
            String toks[] = fastSplit(sizes);
            nvertexArray = Integer.parseInt(toks[0]);
            nfaces = Integer.parseInt(toks[1]);
            nedges = Integer.parseInt(toks[2]);
        }

        for (int i = 0; i < nvertexArray; i++) {
            String line = ins.readLine();
            String toks[] = fastSplit(line);

            float x = Float.parseFloat(toks[0]);
            float y = Float.parseFloat(toks[1]);
            float z = Float.parseFloat(toks[2]);

            xyz_min[0] = Math.min(xyz_min[0], x);
            xyz_min[1] = Math.min(xyz_min[1], y);
            xyz_min[2] = Math.min(xyz_min[2], z);

            xyz_max[0] = Math.max(xyz_max[0], x);
            xyz_max[1] = Math.max(xyz_max[1], y);
            xyz_max[2] = Math.max(xyz_max[2], z);

            vertexArray.add(x);
            vertexArray.add(y);
            vertexArray.add(z);
        }

        float vs[] = vertexArray.getData();
        float ns[] = new float[vs.length];
        normalArray = new FloatArray(ns);

        for (int i = 0; i < nfaces; i++) {
            String line = ins.readLine();
            String toks[] = fastSplit(line);

            int len = Integer.parseInt(toks[0]);
            assert(len+1 == toks.length);

            for (int j = 2; j+1 <= len; j++) {
                int a = Integer.parseInt(toks[1]);
                int b = Integer.parseInt(toks[j]);
                int c = Integer.parseInt(toks[j+1]);

                indexArray.add(a);
                indexArray.add(b);
                indexArray.add(c);

                float vba[] = new float[] { vs[b*3+0] - vs[a*3+0],
                                            vs[b*3+1] - vs[a*3+1],
                                            vs[b*3+2] - vs[a*3+2] };

                float vca[] = new float[] { vs[c*3+0] - vs[a*3+0],
                                            vs[c*3+1] - vs[a*3+1],
                                            vs[c*3+2] - vs[a*3+2] };

                float n[] = LinAlg.normalize(LinAlg.crossProduct(vba, vca));

                for (int k = 0; k < 3; k++) {
                    ns[3*a+k] += n[k];
                    ns[3*b+k] += n[k];
                    ns[3*c+k] += n[k];
                }
            }
        }

        for (int i = 0; i+2 < ns.length; i+=3) {
            double mag = Math.sqrt(ns[i+0]*ns[i+0] + ns[i+1]*ns[i+1] + ns[i+2]*ns[i+2]);
            ns[i+0] /= mag;
            ns[i+1] /= mag;
            ns[i+2] /= mag;
        }

        mesh = new VzMesh(new VisVertexData(vs, vs.length / 3, 3),
                          new VisVertexData(ns, ns.length / 3, 3),
                          new VisIndexData(indexArray),
                          VzMesh.TRIANGLES);
        ins.close();
    }

    // equivalent to s.split("\ +")
    static final String[] fastSplit(String s)
    {
        ArrayList<String> toks = new ArrayList<String>();

        StringBuilder sb = null;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c==' ') {  //  || c=='\n' || c=='\r' || c=='\t') {
                if (sb != null) {
                    toks.add(sb.toString());
                    sb = null;
                }
            } else {
                if (sb == null)
                    sb = new StringBuilder();
                sb.append(c);
            }
        }

        if (sb != null)
            toks.add(sb.toString());

        return toks.toArray(new String[toks.size()]);
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        mesh.render(vc, layer, rinfo, gl, style);
    }

    static String baseName(String path)
    {
        int idx = path.lastIndexOf("/");
        if (idx < 0)
            return path;
        return path.substring(idx+1);
    }

    /** Call with one or more OFF model paths **/
    public static void main(String args[])
    {
        JFrame f = new JFrame("VzOFF "+args[0]);
        f.setLayout(new BorderLayout());

        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);

        VzMesh.Style defaultMeshStyle = new VzMesh.Style(Color.cyan);

        ArrayList<VzOFF> models = new ArrayList<VzOFF>();

        for (int i = 0; i < args.length; i++) {
            if (args[i].endsWith(".off")) {
                try {
                    models.add(new VzOFF(args[i], defaultMeshStyle));
                    System.out.printf("Loaded: %20s (%5.2f%%)\n", args[i], i*100.0 / args.length);
                } catch (IOException ex) {
                    System.out.println("ex: "+ex);
                }
            } else {
                System.out.printf("Ignoring file with wrong suffix: "+args[i]);
            }
        }

        if (models.size() == 0) {
            System.out.println("No models specified\n");
            return;
        }

        int rows = (int) Math.sqrt(models.size());
        int cols = models.size() / rows + 1;

//        VzGrid.addGrid(vw);

        VisWorld.Buffer vb = vw.getBuffer("models");
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int idx = y*cols + x;
                if (idx >= models.size())
                    break;

                VzOFF model = models.get(idx);

                double mx = Math.max(model.xyz_max[2] - model.xyz_min[2],
                                     Math.max(model.xyz_max[1] - model.xyz_min[1],
                                              model.xyz_max[0] - model.xyz_min[0]));

                vb.addBack(new VisChain(LinAlg.translate(x+.5, rows - (y+.5), 0),
                                        new VzRectangle(1, 1, new VzLines.Style(Color.white, 3)),
                                        new VisChain(LinAlg.translate(0, .4, 0),
                                                     new VzText(VzText.ANCHOR.CENTER,
                                                                String.format("<<sansserif-20,scale=.003>>%s", baseName(model.path)))),
                                        LinAlg.scale(.5, .5, .5),
                                        LinAlg.scale(1.0 / mx, 1.0 / mx, 1.0 / mx),
                                        LinAlg.translate(-(model.xyz_max[0] + model.xyz_min[0]) / 2.0,
                                                         -(model.xyz_max[1] + model.xyz_min[1]) / 2.0,
                                                         -(model.xyz_max[2] + model.xyz_min[2]) / 2.0),
                                        model));


            }
        }

        vb.swap();

        vl.cameraManager.fit2D(new double[] { 0, 0, 0 }, new double[] { cols, rows, 0 }, true);
        ((DefaultCameraManager) vl.cameraManager).interfaceMode = 3.0;

        f.add(vc);
        f.setSize(600, 400);
        f.setVisible(true);

    }

}
