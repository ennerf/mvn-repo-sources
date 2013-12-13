/* LCM type definition class file
 * This file was automatically generated by lcm-gen
 * DO NOT MODIFY BY HAND!!!!
 */

package april.lcmtypes;
 
import java.io.*;
import java.util.*;
import lcm.lcm.*;
 
public final class image_t implements lcm.lcm.LCMEncodable
{
    public long utime;
    public short width;
    public short height;
    public short stride;
    public int pixelformat;
    public int size;
    public byte image[];
 
    public image_t()
    {
    }
 
    public static final long LCM_FINGERPRINT;
    public static final long LCM_FINGERPRINT_BASE = 0xe613eff149a08dbcL;
 
    static {
        LCM_FINGERPRINT = _hashRecursive(new ArrayList<Class<?>>());
    }
 
    public static long _hashRecursive(ArrayList<Class<?>> classes)
    {
        if (classes.contains(april.lcmtypes.image_t.class))
            return 0L;
 
        classes.add(april.lcmtypes.image_t.class);
        long hash = LCM_FINGERPRINT_BASE
            ;
        classes.remove(classes.size() - 1);
        return (hash<<1) + ((hash>>63)&1);
    }
 
    public void encode(DataOutput outs) throws IOException
    {
        outs.writeLong(LCM_FINGERPRINT);
        _encodeRecursive(outs);
    }
 
    public void _encodeRecursive(DataOutput outs) throws IOException
    {
        outs.writeLong(this.utime); 
 
        outs.writeShort(this.width); 
 
        outs.writeShort(this.height); 
 
        outs.writeShort(this.stride); 
 
        outs.writeInt(this.pixelformat); 
 
        outs.writeInt(this.size); 
 
        if (this.size > 0)
            outs.write(this.image, 0, size);
 
    }
 
    public image_t(byte[] data) throws IOException
    {
        this(new LCMDataInputStream(data));
    }
 
    public image_t(DataInput ins) throws IOException
    {
        if (ins.readLong() != LCM_FINGERPRINT)
            throw new IOException("LCM Decode error: bad fingerprint");
 
        _decodeRecursive(ins);
    }
 
    public static april.lcmtypes.image_t _decodeRecursiveFactory(DataInput ins) throws IOException
    {
        april.lcmtypes.image_t o = new april.lcmtypes.image_t();
        o._decodeRecursive(ins);
        return o;
    }
 
    public void _decodeRecursive(DataInput ins) throws IOException
    {
        this.utime = ins.readLong();
 
        this.width = ins.readShort();
 
        this.height = ins.readShort();
 
        this.stride = ins.readShort();
 
        this.pixelformat = ins.readInt();
 
        this.size = ins.readInt();
 
        this.image = new byte[(int) size];
        ins.readFully(this.image, 0, size); 
    }
 
    public april.lcmtypes.image_t copy()
    {
        april.lcmtypes.image_t outobj = new april.lcmtypes.image_t();
        outobj.utime = this.utime;
 
        outobj.width = this.width;
 
        outobj.height = this.height;
 
        outobj.stride = this.stride;
 
        outobj.pixelformat = this.pixelformat;
 
        outobj.size = this.size;
 
        outobj.image = new byte[(int) size];
        if (this.size > 0)
            System.arraycopy(this.image, 0, outobj.image, 0, this.size); 
        return outobj;
    }
 
}
