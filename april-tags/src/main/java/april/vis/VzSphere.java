package april.vis;

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import java.io.*;

import javax.swing.*;
import javax.imageio.*;

import april.jmat.*;

public class VzSphere implements VisObject, VisSerializable
{
    static final SphereBuilder sphere4 = new SphereBuilder(4);
    static final VzMesh mesh = new VzMesh(new VisVertexData(sphere4.verts, sphere4.verts.length / 3, 3),
                                          new VisVertexData(sphere4.verts, sphere4.verts.length / 3, 3),
                                          new VisIndexData(sphere4.indices),
                                          VzMesh.TRIANGLES);
    VzMesh.Style meshStyle;
    VisTexture   texture;
    double       r;

    /** A helper class that computes vertex/normal/texture coordinates
     * for a sphere. Note that vertices are equal to normals.**/
    public static class SphereBuilder
    {
        // These arrays are temporary--- only exist during creation time.
        ArrayList<float[]> _vertices = new ArrayList<float[]>();
        ArrayList<float[]> _texcoords = new ArrayList<float[]>();
        ArrayList<int[]> _tris = new ArrayList<int[]>();
        HashMap<Float,IntArray> _vertexHash = new HashMap<Float,IntArray>();

        // these are the actual outputs
        public float  verts[];
        public float  texcoords[];
        public int    indices[];

        public long vid = VisUtil.allocateID(), tid = VisUtil.allocateID(), iid = VisUtil.allocateID();

        // return the index of a vertex having position v and texcoord
        // s, recycling an existing vertex if possible, else creating
        // a new vertex.
        int addVertexTexCoord(float v[], float s[])
        {
            IntArray ia = _vertexHash.get(10*s[0] + s[1]);

            if (ia != null) {

                // check all indices in ia.
                for (int j = 0; j < ia.size(); j++) {
                    int i = ia.get(j);

                    float w[] = _vertices.get(i);

                    if (v[0]==w[0] && v[1]==w[1] && v[2]==w[2]) {

                        float t[] = _texcoords.get(i);

                        if (s[0]==t[0] && s[1]==t[1]) {
                            return i;
                        }
                    }
                }
            } else {
                ia = new IntArray();
                _vertexHash.put(10*s[0] + s[1], ia);
            }

            int idx = _vertices.size();
            _vertices.add(v);
            _texcoords.add(s);

            ia.add(idx);

            return idx;
        }

        // Returns an array containing the indices of vertices that belong to the triangle.
        int[] makeTriangle(float va[], float vb[], float vc[])
        {
            // compute texture coordinates
            float sa[] = new float[] { (float) (Math.atan2(va[1], va[0])/(2*Math.PI)),
                                       (float) (Math.acos(va[2])/(Math.PI)) };

            float sb[] = new float[] { (float) mod1(sa[0], Math.atan2(vb[1], vb[0])/(2*Math.PI)),
                                       (float) (Math.acos(vb[2])/(Math.PI)) };

            float sc[] = new float[] { (float) mod1(sa[0], Math.atan2(vc[1], vc[0])/(2*Math.PI)),
                                       (float) (Math.acos(vc[2])/(Math.PI))};

            int a = addVertexTexCoord(va, sa);
            int b = addVertexTexCoord(vb, sb);
            int c = addVertexTexCoord(vc, sc);

            return new int[] { a, b, c };
        }

        SphereBuilder(int refinesteps)
        {
            if (true) {
                float v = (float) (Math.sqrt(3)/3);

                float va[] = new float[] {  v,  v,  v };
                float vb[] = new float[] { -v, -v,  v };
                float vc[] = new float[] { -v,  v, -v };
                float vd[] = new float[] {  v, -v, -v };

                _tris.add(makeTriangle(va, vb, vc));
                _tris.add(makeTriangle(va, vd, vb));
                _tris.add(makeTriangle(va, vc, vd));
                _tris.add(makeTriangle(vb, vd, vc));
            }

            // refine it
            for (int refine = 0; refine < refinesteps; refine++) {

                ArrayList<int[]> newtris = new ArrayList<int[]>();

                // sub-divide every triangle into four new triangles.
                for (int tri[] : _tris) {
                    int a = tri[0], b = tri[1], c = tri[2];

                    int ab = _vertices.size(), bc = _vertices.size()+1, ac = _vertices.size() + 2;

                    float va[] = _vertices.get(a);
                    float vb[] = _vertices.get(b);
                    float vc[] = _vertices.get(c);

                    float vab[] = LinAlg.normalize(new float[] { va[0]+vb[0],
                                                                 va[1]+vb[1],
                                                                 va[2]+vb[2] });

                    float vbc[] = LinAlg.normalize(new float[] { vc[0]+vb[0],
                                                                 vc[1]+vb[1],
                                                                 vc[2]+vb[2] });

                    float vac[] = LinAlg.normalize(new float[] { vc[0]+va[0],
                                                                 vc[1]+va[1],
                                                                 vc[2]+va[2] });

                    newtris.add(makeTriangle(va, vab, vac));
                    newtris.add(makeTriangle(vab, vb, vbc));
                    newtris.add(makeTriangle(vac, vab, vbc));
                    newtris.add(makeTriangle(vc, vac, vbc));
                }

                _tris = newtris;
            }

            // create compact geometry structures.
            verts = new float[_vertices.size()*3];
            texcoords = new float[_texcoords.size()*2];
            indices = new int[_tris.size()*3];

            for (int i = 0; i < _vertices.size(); i++) {
                float v[] = _vertices.get(i);
                verts[3*i+0] = v[0];
                verts[3*i+1] = v[1];
                verts[3*i+2] = v[2];

                float s[] = _texcoords.get(i);
                texcoords[2*i+0] = s[0];
                texcoords[2*i+1] = s[1];
            }

            for (int i = 0; i < _tris.size(); i++) {
                int tri[] = _tris.get(i);
                indices[3*i+0] = tri[0];
                indices[3*i+1] = tri[1];
                indices[3*i+2] = tri[2];
            }

//        System.out.printf("%d vertices, %d triangles\n", geom.vertices.size(), geom.tris.size());

            // don't need these any more.
            _vertices = null;
            _texcoords = null;
            _tris = null;
            _vertexHash = null;
        }

        // XXX not a very efficient implementation, but it doesn't matter.
        static final double mod1(double ref, double v)
        {
            while (v - ref > .5)
                v -= 1;

            while (v - ref < -.5)
                v += 1;

            return v;
        }
    }

    public VzSphere(VzMesh.Style meshStyle)
    {
        this(1, meshStyle);
    }

    public VzSphere(double r, VzMesh.Style meshStyle)
    {
        this.r = r;
        this.meshStyle = meshStyle;
    }

    // Make sure VisTexture.REPEAT is enabled
    public VzSphere(VisTexture texture)
    {
        this(1, texture);
    }

    // Make sure VisTexture.REPEAT is enabled
    public VzSphere(double r, VisTexture texture)
    {
        this.r = r;
        this.texture = texture;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        SphereBuilder sb = sphere4;

        gl.glPushMatrix();

        gl.glScaled(r, r, r);

        if (meshStyle != null) {
            mesh.render(vc, layer, rinfo, gl, meshStyle);
        }

        if (texture != null) {

            gl.gldBind(GL.VBO_TYPE_VERTEX, sb.vid, sb.verts.length / 3, 3, sb.verts);
            gl.gldBind(GL.VBO_TYPE_NORMAL, sb.vid, sb.verts.length / 3, 3, sb.verts);
            gl.gldBind(GL.VBO_TYPE_ELEMENT_ARRAY, sb.iid, sb.indices.length, 1, sb.indices);

            gl.glColor(Color.white);

            texture.bind(gl);
            gl.gldBind(GL.VBO_TYPE_TEX_COORD, sb.tid, sb.texcoords.length / 2, 2, sb.texcoords);
            gl.glDrawRangeElements(GL.GL_TRIANGLES, 0, sb.verts.length / 3, sb.indices.length, 0);
            gl.gldUnbind(GL.VBO_TYPE_TEX_COORD, sb.tid);
            texture.unbind(gl);

            gl.gldUnbind(GL.VBO_TYPE_VERTEX, sb.vid);
            gl.gldUnbind(GL.VBO_TYPE_NORMAL, sb.vid);
        }

        gl.glPopMatrix();
    }

    public static void main(String args[])
    {
        JFrame f = new JFrame("Vis Zoo");
        f.setLayout(new BorderLayout());

        VisWorld vw = new VisWorld();
        VisLayer vl = new VisLayer(vw);
        VisCanvas vc = new VisCanvas(vl);

        BufferedImage im = null;
        try {
            im = ImageIO.read(new File("/home/ebolson/earth.png"));
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }

        VisWorld.Buffer vb = vw.getBuffer("zoo");
        vb.addBack(new VzSphere(new VisTexture(im)));
        vb.swap();

        f.add(vc, BorderLayout.CENTER);
        f.setSize(600, 400);
        f.setVisible(true);
    }

    public VzSphere(ObjectReader ins)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        outs.writeDouble(r);

        outs.writeObject(texture);
        outs.writeObject(meshStyle);
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        r = ins.readDouble();
        texture = (VisTexture) ins.readObject();
        meshStyle = (VzMesh.Style) ins.readObject();
    }
}

