package april.config;

import java.util.*;
import java.io.*;

import april.util.*;

/**
 * A set of key/value configuration data.
 **/
public class Config
{
    HashMap<String, String[]> keys = new VerboseHashMap<String, String[]>();

    String       prefix; // either empty or has a trailing "." so that
                         // prefix+key is always well-formed

    String       basePath;   // root directory for any paths that aren't fully specified

    Config       root;   // a config whose prefix is empty ("")

    public boolean verbose = EnvUtil.getProperty("april.config.debug", false);

    public Config()
    {
        this.prefix = "";
        this.basePath = "";
        this.root = this;
    }

    /** A config object built from provided hashmaps.
      */
    public Config(HashMap<String,String[]> inputKeys)
    {
        this.prefix = "";
        this.basePath = "";
        this.root = this;

        Set<Map.Entry<String,String[]>> entries = inputKeys.entrySet();

        for (Map.Entry<String,String[]> entry : entries)
            this.keys.put(entry.getKey(), entry.getValue());
    }

    class VerboseHashMap<K,V> extends HashMap<K,V>
    {
        public V get(Object k)
        {
            V v = super.get(k);
            if (verbose) {
                StringBuffer sb = new StringBuffer();
                if (v == null)
                    sb.append("null");
                else {
                    if (v != null) {
                        String vs[] = (String[]) v;
                        for (String s : vs) {
                            sb.append(s+" ");
                        }
                    }
                }

                System.out.printf("Config: %s = %s\n", k, sb.toString());
            }
            return v;
        }
    }

    public Config getChild(String childprefix)
    {
        Config child = new Config();
        child.keys = keys;

        child.prefix = this.prefix;
        if (child.prefix.length() > 0 && !child.prefix.endsWith("."))
            child.prefix = child.prefix+".";
        child.prefix = child.prefix+childprefix+".";

        child.basePath = basePath;
        child.root = root;

        return child;
    }

    public Config getRoot()
    {
        return root;
    }

    public String[] getKeys()
    {
        ArrayList<String> subkeys = new ArrayList<String>();

        for (String key : keys.keySet()) {
            if (key.length() <= prefix.length())
                continue;
            if (key.startsWith(prefix))
                subkeys.add(key.substring(prefix.length()));
        }

        return subkeys.toArray(new String[subkeys.size()]);
    }

    public boolean hasKey(String key)
    {
        return keys.containsKey(prefix + key);
    }

    void missingRequired(String key)
    {
        System.out.println("Config: Required key '"+key+"' missing.");
        assert(false);
    }

    ////////////////////////////
    // int
    public int[] getInts(String key, int defaults[])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null)
            return defaults;

        int v[] = new int[vs.length];
        for (int i = 0; i < vs.length; i++) {
            v[i] = Integer.parseInt(vs[i]);
        }

        return v;
    }

    public int[][] getIntsMatrix(String key, int defaults[][])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null || vs[0] == null)
            return defaults;

        int idx = 1;
        int v[][] = new int[Integer.parseInt(vs[0])][];
        for (int i = 0; i < v.length; i++) {
            v[i] = new int[Integer.parseInt(vs[idx++])];
            for (int j = 0; j < v[i].length; j++) {
                v[i][j] = Integer.parseInt(vs[idx++]);
            }
        }

        return v;
    }

    public int[] getInts(String key)
    {
        return getInts(key, null);
    }

    public int[] requireInts(String key)
    {
        int v[] = getInts(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public int[][] getIntsMatrix(String key)
    {
        return getIntsMatrix(key, null);
    }

    public int[][] requireIntsMatrix(String key)
    {
        int v[][] = getIntsMatrix(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public int getInt(String key, int def)
    {
        return getInts(key, new int[] { def})[0];
    }

    public int requireInt(String key)
    {
        int v[] = getInts(key, null);

        if (v == null)
            missingRequired(key);

        return v[0];
    }

    public void setInt(String key, int v)
    {
        assert(false);
//        source.setInts(key, new int[] {v});
    }

    public void setInts(String key, int v[])
    {
        assert(false);
//        source.setInts(key, v);
    }

    ////////////////////////////
    // long
    public long[] getLongs(String key, long defaults[])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null)
            return defaults;

        long v[] = new long[vs.length];
        for (int i = 0; i < vs.length; i++) {
            v[i] = Long.parseLong(vs[i]);
        }

        return v;
    }

    public long[][] getLongsMatrix(String key, long defaults[][])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null || vs[0] == null)
            return defaults;

        int idx = 1;
        long v[][] = new long[Integer.parseInt(vs[0])][];
        for (int i = 0; i < v.length; i++) {
            v[i] = new long[Integer.parseInt(vs[idx++])];
            for (int j = 0; j < v[i].length; j++) {
                v[i][j] = Long.parseLong(vs[idx++]);
            }
        }

        return v;
    }

    public long[] getLongs(String key)
    {
        return getLongs(key, null);
    }

    public long[] requireLongs(String key)
    {
        long v[] = getLongs(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public long[][] getLongsMatrix(String key)
    {
        return getLongsMatrix(key, null);
    }

    public long[][] requireLongsMatrix(String key)
    {
        long v[][] = getLongsMatrix(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public long getLong(String key, long def)
    {
        return getLongs(key, new long[] { def})[0];
    }

    public long requireLong(String key)
    {
        long v[] = getLongs(key, null);

        if (v == null)
            missingRequired(key);

        return v[0];
    }

    public void setLong(String key, long v)
    {
        assert(false);
//        source.setLongs(key, new long[] {v});
    }

    public void setLongs(String key, long v[])
    {
        assert(false);
//        source.setLongs(key, v);
    }

    ///////////////////////////
    // Paths
    public String getPath(String key, String def)
    {
        String path = getString(key, def);
        if (path == null)
            return def;

        path = path.trim(); // remove white space
        if (!path.startsWith("/"))
            return basePath + path;

        return path;
    }

    public String getPath(String key)
    {
        return getPath(key, null);
    }

    ///////////////////////////
    // String
    public String[] getStrings(String key, String defaults[])
    {
        String v[] = keys.get(prefix + key);
        return (v==null) ? defaults : v;
    }

    public String[][] getStringsMatrix(String key, String defaults[][])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null || vs[0] == null)
            return defaults;

        int idx = 1;
        String v[][] = new String[Integer.parseInt(vs[0])][];
        for (int i = 0; i < v.length; i++) {
            v[i] = new String[Integer.parseInt(vs[idx++])];
            for (int j = 0; j < v[i].length; j++) {
                v[i][j] = vs[idx++];
            }
        }

        return v;
    }

    public String[] getStrings(String key)
    {
        return getStrings(key, null);
    }

    public String[] requireStrings(String key)
    {
        String v[] = getStrings(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public String[][] getStringsMatrix(String key)
    {
        return getStringsMatrix(key, null);
    }

    public String[][] requireStringsMatrix(String key)
    {
        String v[][] = getStringsMatrix(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public String getString(String key)
    {
        return getString(key, null);
    }

    public String getString(String key, String def)
    {
        return getStrings(key, new String[] { def })[0];
    }

    public String requireString(String key)
    {
        String v[] = getStrings(key, null);
        if (v == null)
            missingRequired(key);

        return v[0];
    }

    public void setString(String key, String v)
    {
        assert(false);
    }

    public void setStrings(String key, String v[])
    {
        assert(false);
    }

    ////////////////////////////
    // boolean
    public boolean[] getBooleans(String key, boolean defaults[])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null)
            return defaults;

        boolean v[] = new boolean[vs.length];
        for (int i = 0; i < vs.length; i++) {
            v[i] = Boolean.parseBoolean(vs[i]);
        }

        return v;
    }

    public boolean[][] getBooleansMatrix(String key, boolean defaults[][])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null || vs[0] == null)
            return defaults;

        int idx = 1;
        boolean v[][] = new boolean[Integer.parseInt(vs[0])][];
        for (int i = 0; i < v.length; i++) {
            v[i] = new boolean[Integer.parseInt(vs[idx++])];
            for (int j = 0; j < v[i].length; j++) {
                v[i][j] = Boolean.parseBoolean(vs[idx++]);
            }
        }

        return v;
    }

    public boolean[] getBooleans(String key)
    {
        return getBooleans(key, null);
    }

    public boolean[] requireBooleans(String key)
    {
        boolean v[] = getBooleans(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public boolean[][] getBooleansMatrix(String key)
    {
        return getBooleansMatrix(key, null);
    }

    public boolean[][] requireBooleansMatrix(String key)
    {
        boolean v[][] = getBooleansMatrix(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public boolean getBoolean(String key, boolean def)
    {
        return getBooleans(key, new boolean[] { def })[0];
    }

    public boolean requireBoolean(String key)
    {
        boolean v[] = getBooleans(key);
        if (v == null)
            missingRequired(key);
        return v[0];
    }

    public void setBoolean(String key, boolean v)
    {
        keys.put(prefix+key, new String[] {""+v});
    }

    public void setBooleans(String key, boolean v[])
    {
        assert(false);
    }

    ////////////////////////////
    // float
    public float[] getFloats(String key, float defaults[])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null)
            return defaults;

        float v[] = new float[vs.length];
        for (int i = 0; i < vs.length; i++) {
            v[i] = Float.parseFloat(vs[i]);
        }

        return v;
    }

    public float[][] getFloatsMatrix(String key, float defaults[][])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null || vs[0] == null)
            return defaults;

        int idx = 1;
        float v[][] = new float[Integer.parseInt(vs[0])][];
        for (int i = 0; i < v.length; i++) {
            v[i] = new float[Integer.parseInt(vs[idx++])];
            for (int j = 0; j < v[i].length; j++) {
                v[i][j] = Float.parseFloat(vs[idx++]);
            }
        }

        return v;
    }

    public float[] getFloats(String key)
    {
        return getFloats(key, null);
    }

    public float[] requireFloats(String key)
    {
        float v[] = getFloats(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public float[][] getFloatsMatrix(String key)
    {
        return getFloatsMatrix(key, null);
    }

    public float[][] requireFloatsMatrix(String key)
    {
        float v[][] = getFloatsMatrix(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public float getFloat(String key, float def)
    {
        return getFloats(key, new float[] { def })[0];
    }

    public float requireFloat(String key)
    {
        float v[] = getFloats(key, null);
        if (v == null)
            missingRequired(key);
        return v[0];
    }

    public void setFloat(String key, float v)
    {
        assert(false);
    }

    public void setFloats(String key, float v[])
    {
        String vs[] = new String[v.length];
        for (int i = 0; i < vs.length; i++)
            vs[i] = ""+v[i];
        keys.put(prefix+key, vs);
    }

    ////////////////////////////
    // double
    public double[] getDoubles(String key, double defaults[])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null)
            return defaults;

        double v[] = new double[vs.length];
        for (int i = 0; i < vs.length; i++) {
            v[i] = Double.parseDouble(vs[i]);
        }

        return v;
    }

    public double[][] getDoublesMatrix(String key, double defaults[][])
    {
        String vs[] = keys.get(prefix + key);
        if (vs == null || vs[0] == null)
            return defaults;

        int idx = 1;
        double v[][] = new double[Integer.parseInt(vs[0])][];
        for (int i = 0; i < v.length; i++) {
            v[i] = new double[Integer.parseInt(vs[idx++])];
            for (int j = 0; j < v[i].length; j++) {
                v[i][j] = Double.parseDouble(vs[idx++]);
            }
        }

        return v;
    }

    public double[] getDoubles(String key)
    {
        return getDoubles(key, null);
    }

    public double[] requireDoubles(String key)
    {
        double v[] = getDoubles(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public double[][] getDoublesMatrix(String key)
    {
        return getDoublesMatrix(key, null);
    }

    public double[][] requireDoublesMatrix(String key)
    {
        double v[][] = getDoublesMatrix(key, null);
        if (v == null)
            missingRequired(key);
        return v;
    }

    public double getDouble(String key, double def)
    {
        return getDoubles(key, new double[] { def })[0];
    }

    public double requireDouble(String key)
    {
        double v[] = getDoubles(key, null);
        if (v == null)
            missingRequired(key);
        return v[0];
    }

    public void setDouble(String key, double v)
    {
        assert(false);
    }

    public void setDoubles(String key, double v[])
    {
        String vs[] = new String[v.length];
        for (int i = 0; i < vs.length; i++)
            vs[i] = ""+v[i];
        keys.put(prefix+key, vs);
    }

    ////////////////////////////
    // bytes
    public byte[] getBytes(String key, byte defaults[])
    {
        String lines[] = getStrings(key);
        if (lines == null)
            return defaults;

        return Base64.decode(lines);
    }

    public byte[] requireBytes(String key)
    {
        byte v[] = getBytes(key, null);
        if (v == null)
            missingRequired(prefix+key);
        return v;
    }

    public void setBytes(String key, byte v[])
    {
        assert(false);
    }

    public void merge(Config includedConfig)
    {
        for (String key : includedConfig.keys.keySet()) {
            keys.put(key, includedConfig.keys.get(key));
        }
    }

    ////////////////////////////////////////////////////
}
