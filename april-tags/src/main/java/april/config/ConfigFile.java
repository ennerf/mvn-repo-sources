package april.config;

import java.io.*;
import java.util.*;

import april.util.*;

/** Concrete implementation of Config using a file. **/
public class ConfigFile extends Config
{
    public ConfigFile(String path) throws IOException
    {
        this(new File(path));
    }

    public ConfigFile(File f) throws IOException
    {
        prefix = "";
        if (f.getParent() != null)
            basePath = f.getParent() + File.separator;

        merge(f);
    }

    public void merge(File f) throws IOException
    {
        Tokenizer t = new Tokenizer(f);

        parse(t, "", 0);
    }

    void parseError(Tokenizer t, String msg)
    {
        System.out.println("Parse error: "+msg);

//        System.out.println("Near line "+t.lineNumber+": "+t.line);
    }

    void copyProperties(String srcNameSpace, String destNameSpace)
    {
        HashMap<String, String[]> newkeys = new HashMap<String, String[]>();

        for (String key : keys.keySet()) {

            if (key.startsWith(srcNameSpace)) {
                String propertyName = key.substring(srcNameSpace.length());
                String newKeyName = destNameSpace + propertyName;

                if (destNameSpace.startsWith(":")) {
                    newKeyName = ":" + newKeyName.replace(":","");
                } else {
                    newKeyName = newKeyName.replace(":","");
                }

                newkeys.put(newKeyName, keys.get(key));
            }
        }

        for (String key : newkeys.keySet()) {
            keys.put(key, newkeys.get(key));
        }
    }

    /**
     * @param keyroot The namespace of the key (i.e., everything
     * except the property), well formed so that keyroot+propertyname
     * has dots in the right spots.
     **/
    void parse(Tokenizer t, String keyroot, int depth) throws IOException
    {
        int instantiateId = 0;

        while (true) {

            if (!t.hasNext())
                return;

/*
// anonymous closures are useless and so disabled
            if (t.consume("{")) {
                // a non-inheriting anonymous closure.
                // an anonymous enclosure? Anonymous enclosures are
                // used for scoping inheritance without introducing
                // extra namespace segments into the path.
                parse(t, keyroot, depth+1);
                continue;
            }
*/

            // end of block?
            if (t.consume("}")) {
                if (depth == 0)
                    parseError(t, "Unmatched } in input");

                return;
            }

            if (!t.hasNext())
                return;

            String keypart = null;

            // parse a key block.
            if (t.consume(":")) {
                keypart = ":" + t.next();
            } else {
                keypart = t.next();
            }

            if (keypart.endsWith("#")) {
                // System.out.println("*********: "+keypart);
                keypart = keypart.substring(0, keypart.length()-1) + instantiateId;
                instantiateId++;
            }

            if (!t.hasNext()) {
                parseError(t, "Premature EOF");
                return;
            }

            // inheriting?
            if (t.consume(":")) {
                while (true) {
                    String superclass = t.next();
                    copyProperties(":"+superclass+".", keyroot+keypart+".");
                    copyProperties(superclass+".", keyroot+keypart+".");

                    if (!t.consume(","))
                        break;
                }
            }

            // we have a non-inheriting enclosure block?
            if (t.consume("{")) {
                parse(t, keyroot+keypart+".", depth + 1);
                continue;
            }

            if (t.consume("+{")) {
                copyProperties(keyroot, keyroot+keypart+".");
                parse(t, keyroot+keypart+".", depth + 1);
                continue;
            }

            // This is a key/value declaration.
            // keypart is the key.

            String tok = t.next();
            if (!tok.equals("=")) {
                parseError(t, "Expected = got "+tok + "\t["+keypart+"]");
                return;
            }

            ArrayList<String> values = new ArrayList<String>();

            if (t.consume("[")) {
                tok = t.peek();
                if (tok.equals("[")) {   // matrix
                    int r = 1;
                    int idx = 0;
                    while (t.consume("[")) {
                        values.add(null);
                        ArrayList<String> vect = parseVector(t);
                        values.addAll(vect);
                        values.set(idx, ""+vect.size());
                        if (!t.consume(","))
                            break;
                        idx = values.size();
                        r++;
                    }
                    if (!t.consume("]"))
                        parseError(t, "Expected ] (matrix end) got "+tok + "\t["+keypart+"]");
                    values.add(0, ""+r);  // allow jagged matrices
                } else {
                    values.addAll(parseVector(t));
                }
            } else {
                // read a single value
                values.add(t.next());
            }

            if (!t.consume(";"))
                parseError(t, "Expected ; got "+tok + "\t["+keypart+"]");

            String key = keyroot+keypart;

            if (keys.get(key) != null) {
                //parseError(t, "Duplicate key definition for: "+key);
            }

            keys.put(key, values.toArray(new String[0]));
        }
    }

    private ArrayList<String> parseVector(Tokenizer t) throws IOException
    {
        ArrayList<String> values = new ArrayList<String>();

        // read a list of values
        while (true) {
            String tok = t.next();
            if (tok.equals("]"))
                return values;
            values.add(tok);
            tok = t.peek();
            if (tok.equals(","))
                t.next();
        }
    }

    public static void main(String args[])
    {
        try {
            Config cf = new ConfigFile(new File(args[0]));

            System.out.println("Keys: ");
            for (String key : cf.getKeys()){
                String vs[] = cf.keys.get(key);

                for (int vidx = 0; vidx < vs.length; vidx++)
                    System.out.printf("  %-40s : %s\n", vidx == 0 ? key : "", vs[vidx]);
            }
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }
    }

}
