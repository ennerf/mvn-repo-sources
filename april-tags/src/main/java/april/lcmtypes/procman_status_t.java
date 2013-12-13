/* LCM type definition class file
 * This file was automatically generated by lcm-gen
 * DO NOT MODIFY BY HAND!!!!
 */

package april.lcmtypes;
 
import java.io.*;
import java.util.*;
import lcm.lcm.*;
 
public final class procman_status_t implements lcm.lcm.LCMEncodable
{
    public int procid;
    public boolean running;
    public int restarts;
    public int last_exit_code;
 
    public procman_status_t()
    {
    }
 
    public static final long LCM_FINGERPRINT;
    public static final long LCM_FINGERPRINT_BASE = 0xacd3cadc6933aee9L;
 
    static {
        LCM_FINGERPRINT = _hashRecursive(new ArrayList<Class<?>>());
    }
 
    public static long _hashRecursive(ArrayList<Class<?>> classes)
    {
        if (classes.contains(april.lcmtypes.procman_status_t.class))
            return 0L;
 
        classes.add(april.lcmtypes.procman_status_t.class);
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
        outs.writeInt(this.procid); 
 
        outs.writeByte( this.running ? 1 : 0); 
 
        outs.writeInt(this.restarts); 
 
        outs.writeInt(this.last_exit_code); 
 
    }
 
    public procman_status_t(byte[] data) throws IOException
    {
        this(new LCMDataInputStream(data));
    }
 
    public procman_status_t(DataInput ins) throws IOException
    {
        if (ins.readLong() != LCM_FINGERPRINT)
            throw new IOException("LCM Decode error: bad fingerprint");
 
        _decodeRecursive(ins);
    }
 
    public static april.lcmtypes.procman_status_t _decodeRecursiveFactory(DataInput ins) throws IOException
    {
        april.lcmtypes.procman_status_t o = new april.lcmtypes.procman_status_t();
        o._decodeRecursive(ins);
        return o;
    }
 
    public void _decodeRecursive(DataInput ins) throws IOException
    {
        this.procid = ins.readInt();
 
        this.running = ins.readByte()!=0;
 
        this.restarts = ins.readInt();
 
        this.last_exit_code = ins.readInt();
 
    }
 
    public april.lcmtypes.procman_status_t copy()
    {
        april.lcmtypes.procman_status_t outobj = new april.lcmtypes.procman_status_t();
        outobj.procid = this.procid;
 
        outobj.running = this.running;
 
        outobj.restarts = this.restarts;
 
        outobj.last_exit_code = this.last_exit_code;
 
        return outobj;
    }
 
}

