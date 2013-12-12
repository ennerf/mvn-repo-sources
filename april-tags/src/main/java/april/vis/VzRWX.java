package april.vis;

import java.awt.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

import april.jmat.*;

/** VisObject representing a RWX 3D model format, including quite a
 * few of the ActiveWorlds extensions. Textures are not supported. **/
public class VzRWX implements VisObject, VisSerializable
{
    public HashMap<String, Proto> protos = new HashMap<String, Proto>();
    public ArrayList<RenderOp> renderOps = new ArrayList<RenderOp>();

    FloatArray vertexArray = new FloatArray();
    FloatArray normalArray = new FloatArray();
    IntArray indexArray = new IntArray();

    long vid = VisUtil.allocateID(), nid = VisUtil.allocateID(), iid = VisUtil.allocateID();

    static RenderOp GL_PUSH = new GLPushOp(), GL_POP = new GLPopOp();

    public VzRWX(String path) throws IOException
    {
        BufferedReader ins = new BufferedReader(new FileReader(path));

        String toks[] = nextLine(ins);
        assert(toks[0].equals("MODELBEGIN"));

        while (true) {
            toks = nextLine(ins);
            if (toks[0].equals("MODELEND"))
                break;
            else if (toks[0].equals("PROTOBEGIN")) {
                Proto p = new Proto(this, ins, toks);
                protos.put(p.name, p);
            } else if (toks[0].equals("CLUMPBEGIN")) {
                renderOps.add(new Clump(this, ins, toks));
            } else if (toks[0].equals("SCALE") ||
                       toks[0].equals("COLOR") ||
                       toks[0].equals("SURFACE") ||
                       toks[0].equals("OPACITY")) {
                System.out.println("Model: got CLUMP command "+toks[0]+". Ignoring.");
            } else {
                System.out.println("Model: unknown line: "+glue(toks));
            }
        }
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        gl.gldBind(GL.VBO_TYPE_VERTEX, vid, vertexArray.size() / 3, 3, vertexArray.getData());
        gl.gldBind(GL.VBO_TYPE_NORMAL, nid, normalArray.size() / 3, 3, normalArray.getData());
        gl.gldBind(GL.VBO_TYPE_ELEMENT_ARRAY, iid, indexArray.size(), 1, indexArray.getData());

        gl.glEnable(GL.GL_CULL_FACE);
        gl.glPushMatrix();

        for (RenderOp rop: renderOps) {
            rop.renderGL(this, gl, 1);
        }

        gl.glPopMatrix();
        gl.glPushMatrix();

        for (RenderOp rop: renderOps) {
            rop.renderGL(this, gl, 2);
        }

        gl.glPopMatrix();
        gl.glDisable(GL.GL_CULL_FACE);

        gl.gldUnbind(GL.VBO_TYPE_VERTEX, vid);
        gl.gldUnbind(GL.VBO_TYPE_NORMAL, nid);
        gl.gldUnbind(GL.VBO_TYPE_ELEMENT_ARRAY, iid);
    }

    abstract static class RenderOp implements VisSerializable
    {
        abstract void renderGL(VzRWX rwx, GL gl, int pass);
    }

    static class GLPushOp extends RenderOp
    {
        GLPushOp()
        {
        }

        void renderGL(VzRWX rwx, GL gl, int pass)
        {
            gl.glPushMatrix();
        }

        public GLPushOp(ObjectReader ins)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            // nothing to do.
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            // nothing to do.
        }
    }

    static class GLPopOp extends RenderOp
    {
        GLPopOp()
        {
        }

        void renderGL(VzRWX rwx, GL gl, int pass)
        {
            gl.glPopMatrix();
        }

        public GLPopOp(ObjectReader ins)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            // nothing to do.
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            // nothing to do.
        }
    }

    static class Translate extends RenderOp
    {
        float x, y, z;

        Translate(String toks[])
        {
            x = Float.parseFloat(toks[1]);
            y = Float.parseFloat(toks[2]);
            z = Float.parseFloat(toks[3]);
        }

        void renderGL(VzRWX rwx, GL gl, int pass)
        {
            gl.glTranslated(x,y,z);
        }

        public Translate(ObjectReader ins)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeFloat(x);
            outs.writeFloat(y);
            outs.writeFloat(z);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            x = ins.readFloat();
            y = ins.readFloat();
            z = ins.readFloat();
        }

    }

    static class Transform extends RenderOp
    {
        double m[] = new double[16];

        Transform(String toks[])
        {
            for (int i = 0; i < 16; i++)
                m[i] = Double.parseDouble(toks[1+i]);
        }

        void renderGL(VzRWX rwx, GL gl, int pass)
        {
            // ActiveWorld spec says that Transform is relative to
            // global coordinate frame, but models look funny that
            // way. Also note that calling glLoadMatrix would mess up
            // display lists.

            gl.glMultMatrixd(m);
        }

        public Transform(ObjectReader ins)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeDoubles(m);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            m = ins.readDoubles();
        }
    }

    static class Scale extends RenderOp
    {
        float sx, sy, sz;

        Scale(String toks[])
        {
            sx = Float.parseFloat(toks[1]);
            sy = Float.parseFloat(toks[2]);
            sz = Float.parseFloat(toks[3]);
        }

        void renderGL(VzRWX rwx, GL gl, int pass)
        {
            gl.glScaled(sx,sy,sz);
        }

        public Scale(ObjectReader ins)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeFloat(sx);
            outs.writeFloat(sy);
            outs.writeFloat(sz);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            sx = ins.readFloat();
            sy = ins.readFloat();
            sz = ins.readFloat();
        }
    }

    static class ProtoInstance extends RenderOp
    {
        String name;

        ProtoInstance(BufferedReader ins, String toks[])
        {
            name = toks[1];
        }

        void renderGL(VzRWX rwx, GL gl, int pass)
        {
            Proto p = rwx.protos.get(name);
            p.renderGL(rwx, gl, pass);
        }

        public ProtoInstance(ObjectReader ins)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeUTF(name);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            name = ins.readUTF();
        }
    }

    static class Proto extends ClumpLike
    {
        String name;

        Proto(VzRWX rwx, BufferedReader ins, String toks[])
        {
            super(rwx, ins, toks, "PROTOBEGIN", "PROTOEND");
            this.name = toks[1];
        }

        public Proto(ObjectReader ins)
        {
            super(ins);
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeUTF(name);
            super.writeObject(outs);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            name = ins.readUTF();
            super.readObject(ins);
        }
    }

    static class Clump extends ClumpLike
    {
        Clump(VzRWX rwx, BufferedReader ins, String toks[])
        {
            super(rwx, ins, toks, "CLUMPBEGIN", "CLUMPEND");
        }

        public Clump(ObjectReader ins)
        {
            super(ins);
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            super.writeObject(outs);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            super.readObject(ins);
        }
    }

    // Protos and Clumps both have very similar capabilities. This
    // implements that.
    abstract static class ClumpLike extends RenderOp
    {
        ArrayList<RenderOp> renderOps = new ArrayList<RenderOp>();

        float color[] = new float[3];//Color color = Color.black;
        float opacity = 1.0f;

        float diffuse = 1.0f;
        float specular = 0.0f;
        float ambient = 1.0f;

        String lightSampling = "Vertex";

        String tag = "";

        int vertexOffset0; // what index is our 0th vertex?
        int vertexOffset1; // what index is our last vertex?
        int indexOffset0;  // what is our first index?
        int indexOffset1;  // what is the last index?

        class Vertex
        {
            float x, y, z;
            float nx, ny, nz;
            int nnorms;

            Vertex(String toks[])
            {
                assert(toks[0].equals("VERTEX"));
                x = Float.parseFloat(toks[1]);
                y = Float.parseFloat(toks[2]);
                z = Float.parseFloat(toks[3]);
            }

            void updateNormal(float _nx, float _ny, float _nz)
            {
                nx += _nx;
                ny += _ny;
                nz += _nz;
                nnorms++;
            }

            void normalizeNormal()
            {
                nx /= nnorms;
                ny /= nnorms;
                nz /= nnorms;
            }
        }

        class Triangle
        {
            int i0, i1, i2;

            Triangle(String tok0, String tok1, String tok2)
            {
                i0 = Integer.parseInt(tok0) - 1;
                i1 = Integer.parseInt(tok1) - 1;
                i2 = Integer.parseInt(tok2) - 1;
            }
        }

        public ClumpLike(VzRWX rwx, BufferedReader ins, String toks[], String starttok, String endtok)
        {
            assert(toks[0].equals(starttok));

            ArrayList<Vertex> vertices = new ArrayList<Vertex>();
            ArrayList<Triangle> triangles = new ArrayList<Triangle>();

            while (true) {
                toks = nextLine(ins);

                if (toks[0].equals(endtok)) {
                    break;
                } else if (toks[0].equals("VERTEX")) {
                    vertices.add(new Vertex(toks));
                } else if (toks[0].equals("TRIANGLE")) {
                    triangles.add(new Triangle(toks[1], toks[2], toks[3]));
                } else if (toks[0].equals("QUAD")) {
                    triangles.add(new Triangle(toks[1], toks[2], toks[3]));
                    triangles.add(new Triangle(toks[1], toks[3], toks[4]));
                } else if (toks[0].equals("POLYGON")) {
                    // toks[1] is the number of vertexes. First vertex
                    // index starts at toks[2].

                    if (false) {
                        // This code "properly" tesselates the
                        // polygon, but some polygons seem to be
                        // self-intersecting
                        ArrayList<double[]> points = new ArrayList<double[]>();
                        for (int i = 2; i < toks.length; i++) {
                            Vertex v = vertices.get(Integer.parseInt(toks[i])-1);
                            points.add(new double[] {v.x, v.y, v.z});
                        }
                        april.jmat.geom.Polygon3D poly3d = new april.jmat.geom.Polygon3D(points);
                        ArrayList<int[]> tris = poly3d.getTriangles();
                        for (int tri[]: tris)
                            triangles.add(new Triangle(toks[tri[0]+2], toks[tri[1]+2], toks[tri[2]+2]));
                    } else {
                        // This code just does radial triangles
                        for (int i = 3; i < toks.length - 1; i++) {
                            triangles.add(new Triangle(toks[2], toks[i], toks[i+1]));
                        }
                    }

                } else if (toks[0].equals("COLOR")) {
                    color = new float[] { Float.parseFloat(toks[1]),
                                          Float.parseFloat(toks[2]),
                                          Float.parseFloat(toks[3]) };
                } else if (toks[0].equals("AMBIENT")) {
                    ambient = Float.parseFloat(toks[1]);
                } else if (toks[0].equals("DIFFUSE")) {
                    diffuse = Float.parseFloat(toks[1]);
                } else if (toks[0].equals("SPECULAR")) {
                    specular = Float.parseFloat(toks[1]);
                } else if (toks[0].equals("LIGHTSAMPLING")) {
                    lightSampling = toks[1];
                } else if (toks[0].equals("SURFACE")) {
                    ambient = Float.parseFloat(toks[1]);
                    diffuse = Float.parseFloat(toks[2]);
                    specular = Float.parseFloat(toks[3]);
                } else if (toks[0].equals("TAG")) {
                    tag = toks[1];
                } else if (toks[0].equals("TRANSFORMBEGIN")) {
                    renderOps.add(GL_PUSH);
                } else if (toks[0].equals("TRANSFORMEND")) {
                    renderOps.add(GL_POP);
                } else if (toks[0].equals("OPACITY")) {
                    opacity = Float.parseFloat(toks[1]);
                } else if (toks[0].equals("CLUMPBEGIN")) {
                    renderOps.add(new Clump(rwx, ins, toks));
                } else if (toks[0].equals("TRANSLATE")) {
                    renderOps.add(new Translate(toks));
                } else if (toks[0].equals("SCALE")) {
                    renderOps.add(new Scale(toks));
                } else if (toks[0].equals("PROTOINSTANCE")) {
                    renderOps.add(new ProtoInstance(ins, toks));
                } else if (toks[0].equals("TEXTURE")) {
                    // not implemented. arg1 = texture name? NULL=no texture
                } else if (toks[0].equals("TEXTUREMODE") || toks[0].equals("TEXTUREMODES")) {
                    // not implemented
                } else if (toks[0].equals("HINTS")) {
                    // not implemented
                } else if (toks[0].equals("GEOMETRYSAMPLING")) {
                    // not implemented
                } else if (toks[0].equals("TRANSFORM")) {
                    renderOps.add(new Transform(toks));
                } else if (toks[0].equals("IDENTITY")) {
                    renderOps.add(new Transform(new String[] {"TRANSFORM",
                                                              "1", "0", "0", "0",
                                                              "0", "1", "0", "0",
                                                              "0", "0", "1", "0",
                                                              "0", "0", "0", "1"}));
                } else if (toks[0].equals("ROTATE")) {
                    // active worlds extension that implements an animation
                } else if (toks[0].equals("SEAMLESS")) {
                    // only applies to textures
                } else if (toks[0].equals("COLLISION")) {
                    // not supported; we don't do collision tests
                } else {
                    System.out.println("Clump Unknown line: " + glue(toks));
                }
            }

            // fix normals
            for (Triangle t : triangles) {

                // compute the normal
                Vertex v0 = vertices.get(t.i0);
                Vertex v1 = vertices.get(t.i1);
                Vertex v2 = vertices.get(t.i2);

                float x0 = v1.x - v0.x, y0 = v1.y-v0.y, z0 = v1.z - v0.z;
                float x1 = v2.x - v0.x, y1 = v2.y-v0.y, z1 = v2.z - v0.z;

                float nx = y0*z1 - z0*y1;
                float ny = z0*x1 - x0*z1;
                float nz = x0*y1 - y0*x1;

                v0.updateNormal(nx, ny, nz);
                v1.updateNormal(nx, ny, nz);
                v2.updateNormal(nx, ny, nz);
            }

            for (Vertex v : vertices) {
                v.normalizeNormal();
            }

            vertexOffset0 = rwx.vertexArray.size() / 3;

            for (int vidx = 0; vidx < vertices.size(); vidx++) {
                Vertex v = vertices.get(vidx);
                rwx.vertexArray.add(v.x);
                rwx.vertexArray.add(v.y);
                rwx.vertexArray.add(v.z);

                rwx.normalArray.add(v.nx);
                rwx.normalArray.add(v.ny);
                rwx.normalArray.add(v.nz);
            }

            vertexOffset1 = rwx.vertexArray.size() / 3;

            indexOffset0 = rwx.indexArray.size();

            for (int tidx = 0; tidx < triangles.size(); tidx++) {
                Triangle t = triangles.get(tidx);

                rwx.indexArray.add(vertexOffset0 + t.i0);
                rwx.indexArray.add(vertexOffset0 + t.i1);
                rwx.indexArray.add(vertexOffset0 + t.i2);
            }

            indexOffset1 = rwx.indexArray.size();

            vertices = null;
            triangles = null;
        }

        void renderGL(VzRWX rwx, GL gl, int pass)
        {
            for (RenderOp rop: renderOps)
                rop.renderGL(rwx,gl, pass);

            if (opacity < 1 && pass == 1)
                return;

            if (opacity == 1 && pass == 2)
                return;

            gl.glColor(new Color(color[0], color[1], color[2], opacity));

            gl.glDrawRangeElements(GL.GL_TRIANGLES,
                                   vertexOffset0, vertexOffset1, indexOffset1 - indexOffset0 + 1,
                                   rwx.indexArray.bytesPerElement()*indexOffset0);
        }

        public ClumpLike(ObjectReader ins)
        {
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeInt(renderOps.size());
            for (int idx = 0; idx < renderOps.size(); idx++)
                outs.writeObject(renderOps.get(idx));

            outs.writeFloats(color);
            outs.writeFloat(opacity);
            outs.writeFloat(diffuse);
            outs.writeFloat(specular);
            outs.writeFloat(ambient);
            outs.writeUTF(lightSampling);
            outs.writeUTF(tag);
            outs.writeInt(vertexOffset0);
            outs.writeInt(vertexOffset1);
            outs.writeInt(indexOffset0);
            outs.writeInt(indexOffset1);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            int nops = ins.readInt();
            for (int idx = 0; idx < nops; idx++) {
                renderOps.add((RenderOp) ins.readObject());
            }

            color = ins.readFloats();
            opacity = ins.readFloat();
            diffuse = ins.readFloat();
            specular = ins.readFloat();
            ambient = ins.readFloat();
            lightSampling = ins.readUTF();
            tag = ins.readUTF();
            vertexOffset0 = ins.readInt();
            vertexOffset1 = ins.readInt();
            indexOffset0 = ins.readInt();
            indexOffset1 = ins.readInt();
        }
    }

    static String[] nextLine(BufferedReader ins)
    {
        try {
            while (true) {
                String line = ins.readLine().trim().toUpperCase();

                int comment = line.indexOf("#");
                if (comment>=0)
                    line = line.substring(0, comment).trim();

                if (line.length()==0)
                    continue;
                if (line.startsWith("#"))
                    continue;
                return line.split("\\s+");
            }
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
            return null;
        }
    }

    static final String glue(String toks[])
    {
        String s = "";
        for (String tok : toks)
            s += tok + " ";
        return s;
    }

    public static void main(String args[])
    {
        JFrame f = new JFrame("VzRWX "+args[0]);
        f.setLayout(new BorderLayout());

        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);

        try {
            VzRWX rwx = new VzRWX(args[0]);

            System.out.printf("Loaded RWX model with %d vertices and %d triangles\n", rwx.vertexArray.size()/3, rwx.indexArray.size()/3);

            VisWorld.Buffer vb = vw.getBuffer("rwx");
            vb.addBack(rwx);

            if (true) {
                for (int x = 0; x < 10; x++) {
                    for (int y = 0; y < 10; y++) {
                        vb.addBack(new VisChain(LinAlg.translate(x*10, y*10, 0),
                                                rwx));
                    }
                }
            }

            vb.swap();
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }

//        vc.getViewManager().viewGoal.lookAt(new double[] {0,0,.2}, new double[3], new double[] {0,1,0});
        f.add(vc);
        f.setSize(600, 400);
        f.setVisible(true);

    }

    public VzRWX(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeObject(vertexArray);
        outs.writeObject(normalArray);
        outs.writeObject(indexArray);

        outs.writeInt(protos.size());
        for (String s : protos.keySet()) {
            outs.writeUTF(s);
            outs.writeObject(protos.get(s));
        }

        outs.writeInt(renderOps.size());
        for (int idx = 0; idx < renderOps.size(); idx++)
            outs.writeObject(renderOps.get(idx));
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        vertexArray = (FloatArray) ins.readObject();
        normalArray = (FloatArray) ins.readObject();
        indexArray = (IntArray) ins.readObject();

        int nprotos = ins.readInt();
        for (int idx = 0; idx < nprotos; idx++) {
            String s = ins.readUTF();
            Proto p = (Proto) ins.readObject();
            protos.put(s, p);
        }

        int nops = ins.readInt();
        for (int idx = 0; idx < nops; idx++) {
            renderOps.add((RenderOp) ins.readObject());
        }
    }

}
