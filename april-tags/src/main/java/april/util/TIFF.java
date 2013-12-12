package april.util;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.awt.image.*;
import java.text.*;

import lcm.util.*; // BufferedRandomAccessFile
import april.jmat.*;

public class TIFF
{
    public ArrayList<SubFile> subfiles = new ArrayList<SubFile>();

    public static final int TYPE_BYTE = 1, TYPE_ASCII = 2, TYPE_SHORT = 3,
        TYPE_LONG = 4, TYPE_RATIONAL = 5, TYPE_SBYTE = 6, TYPE_UNDEFINED = 7,
        TYPE_SSHORT = 8, TYPE_SLONG = 9, TYPE_SRATIONAL = 10, TYPE_FLOAT = 11,
        TYPE_DOUBLE = 12;

    // how many bytes to encode each of the types, indexed by type
    // id. Undefined data is encoded as 1 byte per count.
    public static final int TYPE_SIZES[] = { 0, 1, 1, 2, 4, 8, 1, 1, 2, 4, 8, 4, 8 };

    public static class Rational
    {
        public int num, den;

        public Rational(int num, int den)
        {
            this.num = num;
            this.den = den;
        }
    }

    public static String tagString(int t)
    {
        switch(t) {
            case 0x00fe:
                return "NewSubfileType";
            case 0x0100:
                return "ImageWidth";
            case 0x0101:
                return "ImageLength";
            case 0x0102:
                return "BitsPerSample";
            case 0x0103:
                return "Compression";
            case 0x0106:
                return "PhotometricInterpretation"; // aka colorspace
            case 0x0111:
                return "StripOffsets";
            case 0x0112:
                return "Orientation";
            case 0x0115:
                return "SamplesPerPixel";
            case 0x0116:
                return "RowsPerStrip";
            case 0x0117:
                return "StripByteCounts";
            case 0x011a:
                return "XResolution";
            case 0x011b:
                return "YResolution";
            case 0x011c:
                return "PlanarConfiguration";
            case 0x0128:
                return "ResolutionUnit";
            case 0x0131:
                return "Software"; // a comment
            case 0x0132:
                return "DateTime";
            case 0x013d:
                return "Predictor";
            case 0x0140:
                return "ColorMap";
            case 0x830e:
                return "ModelPixelScaleTag";
            case 0x8482:
                return "ModelTiepointTag";
            case 0x87af:
                return "GeoKeyDirectoryTag";
            case 0x87b0:
                return "GeoDoubleParamsTag"; // XXX implement this
            case 0x87b1:
                return "GeoAsciiParamsTag";
            default:
                return "UnknownTag";
        }
    }

    public static String typeString(int t)
    {
        switch(t) {
            case TYPE_BYTE:
                return "byte";
            case TYPE_ASCII:
                return "ascii";
            case TYPE_SHORT:
                return "short";
            case TYPE_LONG:
                return "long";
            case TYPE_RATIONAL:
                return "rational";
            case TYPE_SBYTE:
                return "sbyte";
            case TYPE_UNDEFINED:
                return "undef";
            case TYPE_SSHORT:
                return "sshort";
            case TYPE_SLONG:
                return "slong";
            case TYPE_SRATIONAL:
                return "srational";
            case TYPE_FLOAT:
                return "float";
            case TYPE_DOUBLE:
                return "double";
            default:
                return "unknown";
        }
    }

    // for geodirectories
    public static String keyString(int t)
    {
        switch(t) {
            case 0x0400:
                return "GTModelTypeGeoKey";
            case 0x0401:
                return "RTRasterTypeGeoKey";
            case 0x0402:
                return "GTCitationGeoKey";
            case 0x0c00:
                return "ProjectedCSTypeGeoKey";
            case 0x0c01:
                return "PCSCitationGeoKey";
            case 0x0c04:
                return "ProjLinearUnitsGeoKey";
            default:
                return "Unknown";
        }
    }

    public static class SubFile
    {
        ArrayList<DirectoryEntry> dirents = new ArrayList<DirectoryEntry>();
        ArrayList<byte[]> strips = new ArrayList<byte[]>();
        ArrayList<GeoEntry> geoents = new ArrayList<GeoEntry>();

        public class GeoEntry implements Comparable<GeoEntry>
        {
            // like a tag number
            public int key;

            // one of Short, double[], String
            public Object data;

            // sort in ascending tag order
            public int compareTo(GeoEntry e)
            {
                return key - e.key;
            }
        }

        public DirectoryEntry getDirectoryEntry(int tag)
        {
            for (DirectoryEntry dirent : dirents) {
                if (dirent.tag == tag)
                    return dirent;
            }

            return null;
        }

        public void removeDirectoryEntry(int tag)
        {
            for (int tidx = 0; tidx < dirents.size(); tidx++) {
                if (dirents.get(tidx).tag == tag) {
                    dirents.remove(tidx);
                    tidx--;
                }
            }
        }

        public void setDirectoryEntry(int tag, Object data)
        {
            DirectoryEntry dirent = getDirectoryEntry(tag);
            if (dirent == null) {
                dirent = new DirectoryEntry();
                dirents.add(dirent);
            }

            dirent.tag = tag;
            dirent.data = data;
        }

        public void setGeoEntry(int key, Object data)
        {
            GeoEntry geoent = getGeoEntry(key);
            if (geoent == null) {
                geoent = new GeoEntry();
                geoents.add(geoent);
            }

            geoent.key = key;
            geoent.data = data;
        }

        public GeoEntry getGeoEntry(int key)
        {
            for (GeoEntry geoent : geoents) {
                if (geoent.key == key)
                    return geoent;
            }

            return null;
        }


        public BufferedImage makeBufferedImage() throws IOException
        {
            int width = ((short[]) getDirectoryEntry(0x0100).data)[0];
            int height = ((short[]) getDirectoryEntry(0x0101).data)[0];

            int rowsPerStrip = ((short[]) getDirectoryEntry(0x0116).data)[0];
            int samplesPerPixel = ((short[]) getDirectoryEntry(0x0115).data)[0];

            BufferedImage im = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                byte data[] = strips.get(y / rowsPerStrip);

                int offset = (y % rowsPerStrip) * width * 3;

                for (int x = 0; x < width; x++) {
                    int r = data[offset + x*3+0] & 0xff;
                    int g = data[offset + x*3+1] & 0xff;
                    int b = data[offset + x*3+2] & 0xff;

                    im.setRGB(x,  y, (r<<16) + (g<<8) + b);
                }
            }

            return im;
        }
    }

    public static String getDateString()
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
        return sdf.format(new Date());
    }

    public static class DirectoryEntry implements Comparable<DirectoryEntry>
    {
        public int tag;

        // one of:
        // byte[], short[], int[], Rational[], float[], double[] String
        public Object data;

        // sort in ascending tag order
        public int compareTo(DirectoryEntry e)
        {
            return tag - e.tag;
        }

        public int getDataSize()
        {
            if (data instanceof byte[])
                return ((byte[]) data).length;

            if (data instanceof short[])
                return 2*((short[]) data).length;

            if (data instanceof int[])
                return 4*((int[]) data).length;

            if (data instanceof Rational[])
                return 8*((Rational[]) data).length;

            if (data instanceof float[])
                return 4*((float[]) data).length;

            if (data instanceof double[])
                return 8*((double[]) data).length;

            if (data instanceof String)
                return ((String) data).length() + 1; // count the null byte (which we aren't storing)

            assert(false);
            return 0;
        }
    }

    public TIFF(BufferedImage im) throws IOException
    {
        SubFile sf = new SubFile();

        int width = im.getWidth();
        int height = im.getHeight();

        sf.setDirectoryEntry(0x00fe, new int[] { 0 }); // newSubfileType
        sf.setDirectoryEntry(0x0100, new short[] { (short) width }); // ImageWidth
        sf.setDirectoryEntry(0x0101, new short[] { (short) height }); // ImageLength
        sf.setDirectoryEntry(0x0102, new short[] { 8, 8, 8 }); // BitsPerSample
        sf.setDirectoryEntry(0x0103, new short[] { 1 }); // Compression
        sf.setDirectoryEntry(0x0106, new short[] { 2 }); // PhotometricInterpretation (2 = RGB)
        sf.setDirectoryEntry(0x0112, new short[] { 1 }); // Orientation
        sf.setDirectoryEntry(0x0115, new short[] { 3 }); // SamplesPerPixel
        sf.setDirectoryEntry(0x0116, new short[] { 1 }); // RowsPerStrip
        sf.setDirectoryEntry(0x011a, new Rational[] { new Rational(72, 1) }); // XResolution
        sf.setDirectoryEntry(0x011b, new Rational[] { new Rational(72, 1) }); // YResolution
        sf.setDirectoryEntry(0x011c, new short[] { 1 }); // PlanarConfiguration (1 = contiguous)

        sf.setDirectoryEntry(0x0128, new short[] { 2 }); // ResolutionUnit (2 = inch)

        sf.setDirectoryEntry(0x0131, "APRIL Robotics Toolkit"); // Software
        sf.setDirectoryEntry(0x0132, getDateString()); // DateTime

        for (int y = 0; y < height; y++) {
            byte strip[] = new byte[width*3];
            for (int x = 0; x < width; x++) {
                int rgb = im.getRGB(x,y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;

                strip[x*3 + 0] = (byte) r;
                strip[x*3 + 1] = (byte) g;
                strip[x*3 + 2] = (byte) b;
            }

            sf.strips.add(strip);
        }

        subfiles.add(sf);
    }

    public TIFF(RandomAccessFile raf) throws IOException
    {
        if (raf.length() < 11)
            throw new IOException("Not in TIFF format");

        int b0 = raf.read();
        int b1 = raf.read();
        if (b0 != b1 || (b0 != 'I' && b0 != 'M'))
            throw new IOException("Not in TIFF format");

        boolean littleEndian = (b0 == 'I');

        EndianDataInputStream ins = new EndianDataInputStream(raf, littleEndian);

        int b2 = ins.readShort();
        if (b2 != 42)
            throw new IOException(String.format("Not in TIFF format (%04x != %04x)", 42, b2));

        //////////////////////////////////////////////////////
        // read the TIFF directory
        int ifd_offset = ins.readInt();
        raf.seek(ifd_offset);

        do {
            SubFile subfile = new SubFile();

            int nentries = ins.readShort() & 0xffff;

            for (int idx = 0; idx < nentries; idx++) {
                DirectoryEntry dirent = new DirectoryEntry();

                dirent.tag = ins.readShort() & 0xffff;
                int type = ins.readShort() & 0xffff;
                int nvalues = ins.readInt();

                byte valueBytes[] = new byte[4];
                for (int j = 0; j < 4; j++)
                    valueBytes[j] = (byte) ins.read();

                int sz = TYPE_SIZES[type] * nvalues;
                byte bdata[] = new byte[sz];
                if (sz <= 4) {
                    for (int i = 0; i < bdata.length; i++)
                        bdata[i] = valueBytes[i];
                } else {
                    // read the file offset using the correct endianness
                    EndianDataInputStream bins = new EndianDataInputStream(new ByteArrayInputStream(valueBytes), littleEndian);
                    int offset = bins.readInt();

                    long fp = raf.getFilePointer();
                    raf.seek(offset);
                    // now read the data.
                    raf.readFully(bdata);

                    raf.seek(fp);
                }

                // bdata now contains the actual payload of this
                // field, but it may have the wrong endianness
                EndianDataInputStream dins = new EndianDataInputStream(bdata, littleEndian);
                ByteArrayOutputStream _douts = new ByteArrayOutputStream();
                DataOutputStream douts = new DataOutputStream(_douts);

                switch (type) {
                    case TYPE_BYTE: {
                        byte b[] = new byte[nvalues];
                        for (int i = 0; i < b.length; i++)
                            b[i] = (byte) dins.read();
                        dirent.data = b;
                        break;
                    }

                    case TYPE_ASCII: {
                        byte b[] = new byte[nvalues - 1];
                        for (int i = 0; i < b.length; i++)
                            b[i] = (byte) dins.read();

                        byte nul = (byte) dins.read();
                        if (nul != 0)
                            System.out.println("TIFF ASCII string is not null terminated");

                        dirent.data = new String(b);
                        break;
                    }

                    case TYPE_SHORT:
                    case TYPE_SSHORT: {
                        short b[] = new short[nvalues];
                        for (int i = 0; i < b.length; i++)
                            b[i] = dins.readShort();
                        dirent.data = b;
                        break;
                    }

                    case TYPE_LONG:
                    case TYPE_SLONG: {
                        int b[] = new int[nvalues];
                        for (int i = 0; i < b.length; i++)
                            b[i] = dins.readInt();
                        dirent.data = b;
                        break;
                    }

                    case TYPE_RATIONAL: {
                        Rational b[] = new Rational[nvalues];
                        for (int i = 0; i < b.length; i++)
                            b[i] = new Rational(dins.readInt(), dins.readInt());
                        dirent.data = b;
                        break;
                    }

                    case TYPE_FLOAT: {
                        float b[] = new float[nvalues];
                        for (int i = 0; i < b.length; i++)
                            b[i] = dins.readFloat();
                        dirent.data = b;
                        break;
                    }

                    case TYPE_DOUBLE: {
                        double b[] = new double[nvalues];
                        for (int i = 0; i < b.length; i++)
                            b[i] = dins.readDouble();
                        dirent.data = b;
                        break;
                    }
                }

                subfile.dirents.add(dirent);
            }

            // read the pixel data.
            if (true) {
                long fp = raf.getFilePointer();

                int offsets[] = (int[]) subfile.getDirectoryEntry(0x0111).data; // StripsOffsets
                int lengths[] = (int[]) subfile.getDirectoryEntry(0x0117).data; // StripByteCounts

                for (int i = 0; i < offsets.length; i++){
                    raf.seek(offsets[i]);
                    byte b[] = new byte[lengths[i]];
                    raf.readFully(b);
                    subfile.strips.add(b);
                }

                raf.seek(fp);
            }

            // now, try to read the geotiff section
            if (true) {

                short vals[] = (short[]) subfile.getDirectoryEntry(0x87af).data; // GeoKeyDirectoryTag

                int version = vals[0];
                int keyversion = vals[1];
                int minorversion = vals[2];
                int nkeys = vals[3];

                for (int i = 1; i < nkeys; i++) {
                    int key = vals[4*i+0] & 0xffff;
                    int location = vals[4*i+1] & 0xffff;
                    int count = vals[4*i+2] & 0xffff;
                    int value = vals[4*i+3] & 0xffff;

                    if (location == 0) {
                        subfile.setGeoEntry(key, (Short) (short) value);
                    } else if (location == 0x87b1) {
                        // string data
                        String s = (String) subfile.getDirectoryEntry(0x87b1).data;
                        subfile.setGeoEntry(key, s.substring(value, value + count)); // prune nul
                    } else {
                        assert(false);
                    }
                }
            }

            subfiles.add(subfile);

            // read the offset of the next subfile.
            ifd_offset = raf.readInt();

        } while (ifd_offset != 0);
    }

    public void print() throws IOException
    {
        System.out.printf("TIFF file with %d subfiles\n", subfiles.size());

        for (int sfidx = 0; sfidx < subfiles.size(); sfidx++) {
            SubFile subfile = subfiles.get(sfidx);

            System.out.printf("Subfile %d\n", sfidx);

            for (int diridx = 0; diridx < subfile.dirents.size(); diridx++) {
                DirectoryEntry dirent = subfile.dirents.get(diridx);

                System.out.printf("  %26s (%04x) ",
                                  tagString(dirent.tag), dirent.tag);
                int preview = 4;

                if (dirent.data instanceof byte[]) {
                    byte v[] = (byte[]) dirent.data;

                    System.out.printf("(%6d) [ ", v.length);

                    for (int i = 0; i < Math.min(preview, v.length); i++) {
                        System.out.printf("%02x ", v[i]);
                    }

                    if (v.length > preview)
                        System.out.printf("...");
                    System.out.printf(" ] \n");
                }

                if (dirent.data instanceof short[]) {
                    short v[] = (short[]) dirent.data;

                    System.out.printf("(%6d) [ ", v.length);

                    for (int i = 0; i < Math.min(preview, v.length); i++) {
                        System.out.printf("%04x ", v[i]);
                    }

                    if (v.length > preview)
                        System.out.printf("...");
                    System.out.printf(" ] \n");
                }

                if (dirent.data instanceof int[]) {
                    int v[] = (int[]) dirent.data;

                    System.out.printf("(%6d) [ ", v.length);

                    for (int i = 0; i < Math.min(preview, v.length); i++) {
                        System.out.printf("%08x ", v[i]);
                    }

                    if (v.length > preview)
                        System.out.printf("...");
                    System.out.printf(" ] \n");
                }

                if (dirent.data instanceof float[]) {
                    float v[] = (float[]) dirent.data;

                    System.out.printf("(%6d) [ ", v.length);

                    for (int i = 0; i < Math.min(preview, v.length); i++) {
                        System.out.printf("%10f ", v[i]);
                    }

                    if (v.length > preview)
                        System.out.printf("...");
                    System.out.printf(" ] \n");
                }

                if (dirent.data instanceof double[]) {
                    double v[] = (double[]) dirent.data;

                    System.out.printf("(%6d) [ ", v.length);

                    for (int i = 0; i < Math.min(preview, v.length); i++) {
                        System.out.printf("%10f ", v[i]);
                    }

                    if (v.length > preview)
                        System.out.printf("...");
                    System.out.printf(" ] \n");
                }

                if (dirent.data instanceof String) {
                    String s = (String) dirent.data;

                    System.out.printf("(%6d) [ ", s.length());

                    s = s.replace("\n", "\\n");
                    s = s.replace("\r", "\\r");
                    if (s.length() > 30)
                        s = s.substring(0, 30) + " ... ";
                    System.out.printf("%s ]\n", s);
                }

                if (dirent.data instanceof Rational[]) {
                    Rational v[] = (Rational[]) dirent.data;

                    System.out.printf("(%6d) [ ", v.length);

                    for (int i = 0; i < Math.min(preview, v.length); i++) {
                        System.out.printf("%d/%d ", v[i].num, v[i].den);
                    }

                    if (v.length > preview)
                        System.out.printf("...");
                    System.out.printf(" ]\n");
                }
            }

            System.out.println("\nGeoTIFF data:\n");

            for (SubFile.GeoEntry geoent : subfile.geoents) {
                System.out.printf("  %26s (%04x) ",
                                  keyString(geoent.key), geoent.key);

                if (geoent.data instanceof Short)
                    System.out.printf("[ %04x ]", ((Short) geoent.data) & 0xffff);
                if (geoent.data instanceof String)
                    System.out.printf("[ %s ]", ((String) geoent.data));

                System.out.println("");
            }
        }
    }

    public void write(File f) throws IOException
    {
        write(f, false);
    }

    public void write(File f, boolean gzip) throws IOException
    {
        FileOutputStream fouts = new FileOutputStream(f);
        DataOutputStream outs;

        if (gzip)
            outs = new DataOutputStream(new GZIPOutputStream(fouts));
        else
            outs = new DataOutputStream(fouts);

        // write big-endian TIFF header
        outs.write('M');
        outs.write('M');
        outs.writeShort(42);

        for (SubFile subfile : subfiles) {

            // prepare our strip data (so we know how big it all is)
            // we'll fill in the real values in a bit.
            int strip_offsets[] = new int[subfile.strips.size()];
            int strip_lengths[] = new int[subfile.strips.size()];

            // update tag fields
            subfile.setDirectoryEntry(0x0111, strip_offsets); // StripOffsets
            subfile.setDirectoryEntry(0x0117, strip_lengths); // StripByteCounts
            subfile.setDirectoryEntry(0x0132, getDateString()); // DateTime

            // strip random tags
            if (true) {
                for (int deidx = 0; deidx < subfile.dirents.size(); deidx++) {
                    DirectoryEntry de = subfile.dirents.get(deidx);
                    String type = tagString(de.tag);
                    if (type.equals("UnknownTag")) {
                        subfile.removeDirectoryEntry(de.tag);
                        deidx--;
                    }
                }
            }

            subfile.setGeoEntry(0x0402, "APRIL Robotics Toolkit");
            subfile.setGeoEntry(0x0c01, "APRIL Robotics Toolkit");

            if (true) {
                // rebuild GeoDirectory
                HashMap<SubFile.GeoEntry, Integer> geoOffsets = new HashMap<SubFile.GeoEntry, Integer>();
                HashMap<SubFile.GeoEntry, Integer> geoCounts = new HashMap<SubFile.GeoEntry, Integer>();

                // compact the ASCII (and eventually double) parameters
                StringBuffer asciiParams = new StringBuffer();

                for (SubFile.GeoEntry geoent : subfile.geoents) {
                    if (geoent.data instanceof Short)
                        continue;
                    else if (geoent.data instanceof String) {
                        String v = (String) geoent.data;
                        geoOffsets.put(geoent, asciiParams.length());
                        geoCounts.put(geoent, v.length());
                        asciiParams.append(v);
                        asciiParams.append("|");
                        continue;
                    } else {
                        assert(false);
                    }
                }

                if (true) {
                    String p = asciiParams.toString();
                    // strip the final |
                    p = p.substring(0, p.length());
                    subfile.setDirectoryEntry(0x87b1, p);
                }

                Collections.sort(subfile.geoents);

                // rebuild the index
                short v[] = new short[4*subfile.geoents.size()+4];
                v[0] = 1; // version
                v[1] = 1; // key version
                v[2] = 0; // minor version
                v[3] = (short) (1 + subfile.geoents.size());

                for (int i = 0; i < subfile.geoents.size(); i++) {
                    SubFile.GeoEntry geoent = subfile.geoents.get(i);
                    v[4+4*i+0] = (short) geoent.key;
                    if (geoent.data instanceof Short) {
                        v[4+4*i+1] = 0; // location
                        v[4+4*i+2] = 1; // count
                        v[4+4*i+3] = (Short) geoent.data; // value
                    } else if (geoent.data instanceof String) {
                        v[4+4*i+1] = (short) 0x87b1; // location
                        v[4+4*i+2] = (short) (int) (Integer) geoCounts.get(geoent);
                        v[4+4*i+3] = (short) (int) (Integer) geoOffsets.get(geoent);
                    } else {
                        assert(false);
                    }
                }
                subfile.setDirectoryEntry(0x87af, v); // GeoKeyDirectoryTag
            }

            /////////////////////////////////////////////////////////////
            // !!! at this point, size of DirectoryEntries cannot change.

            // how much space do we need for directory entries and strips?
            int bytesNeeded = 0;
            for (byte b[] : subfile.strips)
                bytesNeeded += b.length;

            for (DirectoryEntry dirent : subfile.dirents) {

                int sz = dirent.getDataSize();
                if (sz > 4)
                    bytesNeeded += sz;
            }

            // write a pointer to our directory index
            int predictedIndexLocation = 4 + outs.size() + bytesNeeded;
            outs.writeInt(predictedIndexLocation);

            // write the strip data, updating the offsets
            for (int sidx = 0; sidx < subfile.strips.size(); sidx++) {
                byte b[] = subfile.strips.get(sidx);
                strip_offsets[sidx] = outs.size();
                strip_lengths[sidx] = b.length;
                outs.write(b);
            }

            subfile.setDirectoryEntry(0x0111, strip_offsets); // StripOffsets
            subfile.setDirectoryEntry(0x0117, strip_lengths); // StripByteCounts

            // encode all of the buffers
            HashMap<DirectoryEntry, byte[]> tagData = new HashMap<DirectoryEntry, byte[]>();

            for (DirectoryEntry dirent : subfile.dirents) {

                if (dirent.data instanceof byte[]) {
                    System.out.println("byte: "+((byte[]) dirent.data).length);
                    tagData.put(dirent, (byte[]) dirent.data);
                }

                if (dirent.data instanceof short[]) {
                    ByteArrayOutputStream _bouts = new ByteArrayOutputStream();
                    DataOutputStream douts = new DataOutputStream(_bouts);

                    short v[] = (short[]) dirent.data;
                    for (int i = 0; i < v.length; i++)
                        douts.writeShort(v[i]);
                    douts.flush();
                    tagData.put(dirent, _bouts.toByteArray());
                }

                if (dirent.data instanceof int[]) {
                    ByteArrayOutputStream _bouts = new ByteArrayOutputStream();
                    DataOutputStream douts = new DataOutputStream(_bouts);

                    int v[] = (int[]) dirent.data;
                    for (int i = 0; i < v.length; i++)
                        douts.writeInt(v[i]);
                    douts.flush();
                    tagData.put(dirent, _bouts.toByteArray());
                }

                if (dirent.data instanceof float[]) {
                    ByteArrayOutputStream _bouts = new ByteArrayOutputStream();
                    DataOutputStream douts = new DataOutputStream(_bouts);

                    float v[] = (float[]) dirent.data;
                    for (int i = 0; i < v.length; i++)
                        douts.writeFloat(v[i]);
                    douts.flush();
                    tagData.put(dirent, _bouts.toByteArray());
                }

                if (dirent.data instanceof double[]) {
                    ByteArrayOutputStream _bouts = new ByteArrayOutputStream();
                    DataOutputStream douts = new DataOutputStream(_bouts);

                    double v[] = (double[]) dirent.data;
                    for (int i = 0; i < v.length; i++)
                        douts.writeDouble(v[i]);
                    douts.flush();
                    tagData.put(dirent, _bouts.toByteArray());
                }

                if (dirent.data instanceof String) {
                    ByteArrayOutputStream _bouts = new ByteArrayOutputStream();
                    DataOutputStream douts = new DataOutputStream(_bouts);

                    String v = (String) dirent.data;

                    for (int i = 0; i < v.length(); i++)
                        douts.write((byte) v.charAt(i));

                    douts.write((byte) 0); // write terminater
                    douts.flush();

                    tagData.put(dirent, _bouts.toByteArray());
                }

                if (dirent.data instanceof Rational[]) {
                    ByteArrayOutputStream _bouts = new ByteArrayOutputStream();
                    DataOutputStream douts = new DataOutputStream(_bouts);

                    Rational v[] = (Rational[]) dirent.data;
                    for (int i = 0; i < v.length; i++) {
                        douts.writeInt(v[i].num);
                        douts.writeInt(v[i].den);
                    }
                    douts.flush();
                    tagData.put(dirent, _bouts.toByteArray());
                }
            }

            Collections.sort(subfile.dirents);

            // write the TIFF tag data fields that require additional storage
            // (so we know the offset when we write the directory)
            HashMap<DirectoryEntry, Integer> tagDataOffsets = new HashMap<DirectoryEntry, Integer>();

            for (DirectoryEntry dirent : subfile.dirents) {
                byte b[] = tagData.get(dirent);

                if (b.length > 4) {
                    tagDataOffsets.put(dirent, outs.size());
                    outs.write(b);
                }
            }

            // write the TIFF directory
            assert(predictedIndexLocation == outs.size());

            outs.writeShort(subfile.dirents.size());

            for (DirectoryEntry dirent : subfile.dirents) {
                outs.writeShort(dirent.tag);

                if (dirent.data instanceof byte[]) {
                    outs.writeShort(TYPE_BYTE);
                    outs.writeInt(((byte[]) dirent.data).length);
                } else if (dirent.data instanceof String) {
                    outs.writeShort(TYPE_ASCII);
                    int v = ((String) dirent.data).length() + 1;
                    outs.writeInt(((String) dirent.data).length() + 1); // count null byte
                } else if (dirent.data instanceof short[]) {
                    outs.writeShort(TYPE_SHORT);
                    outs.writeInt(((short[]) dirent.data).length);
                } else if (dirent.data instanceof int[]) {
                    outs.writeShort(TYPE_LONG);
                    outs.writeInt(((int[]) dirent.data).length);
                } else if (dirent.data instanceof float[]) {
                    outs.writeShort(TYPE_FLOAT);
                    outs.writeInt(((float[]) dirent.data).length);
                } else if (dirent.data instanceof double[]) {
                    outs.writeShort(TYPE_DOUBLE);
                    outs.writeInt(((double[]) dirent.data).length);
                } else if (dirent.data instanceof Rational[]) {
                    outs.writeShort(TYPE_RATIONAL);
                    outs.writeInt(((Rational[]) dirent.data).length);
                } else {
                    assert(false);
                }

                byte b[] = tagData.get(dirent);

                if (b.length > 4) {
                    outs.writeInt(tagDataOffsets.get(dirent));
                } else {
                    outs.write(b);
                    for (int j = b.length; j < 4; j++)
                        outs.write(0);
                }
            }
        }

        // there are no more subfiles
        outs.writeInt(0);
        outs.flush();
        outs.close();
    }

    public static void main(String args[])
    {
        for (String arg : args) {
            System.out.printf("File: %s\n", arg);

            try {
                RandomAccessFile raf = new RandomAccessFile(new File(arg), "r");
                TIFF t = new TIFF(raf);
                t.print();

                t.write(new File("/tmp/foo.tif.gz"), true);


                TIFF.SubFile sf = t.subfiles.get(0);
                double d[] = (double[]) sf.getDirectoryEntry(0x8482).data;

                int n = d.length / 6;
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < 6; j++) {
                        System.out.printf("%20.15f ", d[6*i+j]);
                    }
                    System.out.printf("\n");
                }
            } catch (IOException ex) {
                System.out.println("ex: "+ex);
                ex.printStackTrace();
            }
        }
    }
}
