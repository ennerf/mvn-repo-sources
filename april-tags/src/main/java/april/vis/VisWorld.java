package april.vis;

import java.util.*;
import java.awt.*;
import java.io.*;

import april.util.*;

/** A VisWorld represents a scene of objects. This scene could be
 * rendered by one or more VisLayers.
 **/
public class VisWorld implements VisSerializable
{
    static boolean debug = EnvUtil.getProperty("vis.debug", false);

    // synchrronize on 'buffers' before accessing.
    ArrayList<Buffer> buffers = new ArrayList<Buffer>();

    // 'bufferMap' is protected by synchronizing on 'buffers'.
    HashMap<String, Buffer> bufferMap = new HashMap<String, Buffer>();

    ArrayList<Listener> listeners = new ArrayList<Listener>();

    public interface Listener
    {
        public void bufferAdded(VisWorld vw, String name);
    }

    static class TemporaryObject implements VisSerializable
    {
        VisObject vo;
        long expireTime;

        TemporaryObject(VisObject vo, long expireTime)
        {
            this.vo = vo;
            this.expireTime = expireTime;
        }

        public void writeObject(ObjectWriter outs) throws IOException
        {
            outs.writeObject(vo);
            outs.writeLong(expireTime);
        }

        public void readObject(ObjectReader ins) throws IOException
        {
            this.vo = (VisObject) ins.readObject();
            this.expireTime = ins.readLong();
        }
    }

    public class Buffer implements Comparable<Buffer>
    {
        // contents of 'front' and 'back' are protected by synchronizing on the buffer.
        protected ArrayList<VisObject> back  = new ArrayList<VisObject>();
        protected ArrayList<VisObject> front = new ArrayList<VisObject>();

        protected ArrayList<TemporaryObject> temporaries = new ArrayList<TemporaryObject>();

        int drawOrder = -1;
        String name;

        Buffer(String name)
        {
            this.name = name;
        }

        public String getName()
        {
            return name;
        }

        public synchronized void addTemporary(VisObject vo, double dt)
        {
            if (vo == null)
                return;

            temporaries.add(new TemporaryObject(vo, (long)(System.currentTimeMillis() + 1000.0*dt)));
        }

        public void removeTemporary(VisObject vo)
        {
            if (vo == null)
                return;

            for (int idx = 0; idx < temporaries.size(); idx++) {
                TemporaryObject to = temporaries.get(idx);
                if (to.vo == vo) {
                    temporaries.remove(idx);
                    idx--;
                }
            }
        }

        public synchronized void addBack(VisObject vo)
                                 {
                                     back.add(vo);
                                 }

        public synchronized void clear()
                                 {
                                     back.clear();
                                     front.clear();
                                     temporaries.clear();
                                 }

        public synchronized void addFront(VisObject vo)
                                 {
                                     front.add(vo);
                                 }

        public synchronized void swap()
                                 {
                                     front = back;
                                     // don't recycle: a previous front buffer
                                     // could still have a reference somewhere.
                                     back = new ArrayList<VisObject>();
                                 }

        public int compareTo(Buffer b)
        {
            return drawOrder - b.drawOrder;
        }

        public void setDrawOrder(int order)
        {
            this.drawOrder = order;
        }
    }

    public VisWorld()
    {
    }

    public void addListener(Listener listener)
    {
        if (!listeners.contains(listener))
            listeners.add(listener);
    }

    public Buffer getBuffer(String name)
    {
        Buffer b = bufferMap.get(name);
        if (b == null) {
            b = new Buffer(name);
            synchronized(buffers) {
                bufferMap.put(name, b);
                buffers.add(b);
                Collections.sort(buffers);
            }
            for (Listener listener : listeners)
                listener.bufferAdded(this, name);
        }

        return b;
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        long now = System.currentTimeMillis();

        synchronized(buffers) {

            Collections.sort(buffers);

            for (Buffer b : buffers) {

                if (!layer.isBufferEnabled(b.name))
                    continue;

                synchronized(b) {
                    for (VisObject vo : b.front) {
                        if (vo != null)
                            vo.render(vc, layer, rinfo, gl);
                    }

                    for (int idx = 0; idx < b.temporaries.size(); idx++) {
                        TemporaryObject to = b.temporaries.get(idx);
                        if (to.expireTime < now) {
                            b.temporaries.remove(idx);
                            idx--;
                        } else {
                            to.vo.render(vc, layer, rinfo, gl);
                        }
                    }
                }
            }
        }
    }

    public VisWorld(ObjectReader r)
    {
    }

    public void writeObject(ObjectWriter outs) throws IOException
    {
        synchronized(buffers) {
            outs.writeInt(buffers.size());
            for (int bidx = 0; bidx < buffers.size(); bidx++) {
                VisWorld.Buffer vb = buffers.get(bidx);

                outs.writeUTF(vb.name);
                outs.writeInt(vb.drawOrder);

                outs.writeInt(vb.front.size());
                for (int i = 0; i < vb.front.size(); i++)
                    outs.writeObject(vb.front.get(i));

                outs.writeInt(vb.temporaries.size());
                for (int i = 0; i < vb.temporaries.size(); i++)
                    outs.writeObject(vb.temporaries.get(i));
            }
        }
    }

    public void readObject(ObjectReader ins) throws IOException
    {
        synchronized(buffers) {
            int bsz = ins.readInt();

            for (int bidx = 0; bidx < bsz; bidx++) {
                String name = ins.readUTF();

                VisWorld.Buffer vb = getBuffer(name);
                vb.drawOrder = ins.readInt();

                int n = ins.readInt();
                for (int i = 0; i < n; i++)
                    vb.front.add((VisObject) ins.readObject());

                n = ins.readInt();
                for (int i = 0; i < n; i++)
                    vb.temporaries.add((TemporaryObject) ins.readObject());
            }
        }
    }
}
