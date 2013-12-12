package april.util;

import java.util.*;
import java.io.*;

/** Base64 encoding and decoding functions. **/
public class Base64
{
    static char encodeLut[];
    static int decodeLut[];

    static {
        String cs = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

        assert(cs.length()==64);

        encodeLut = new char[cs.length()];
        decodeLut = new int[256];

        for (int i = 0; i < cs.length(); i++) {
            encodeLut[i] = cs.charAt(i);
            decodeLut[(int) cs.charAt(i)] = i;
        }
    }

    public static String[] encode(byte in[])
    {
        return encode(in, in.length);
    }

    public static String[] encode(byte in[], int length) {
        StringBuilder sb = new StringBuilder();
        ArrayList<String> lines = new ArrayList<String>();

        for (int idx = 0; idx < length; idx+=3) {
            int v0 = in[idx]&0xff;
            int v1 = idx+1 < length ? in[idx+1]&0xff : 0;
            int v2 = idx+2 < length ? in[idx+2]&0xff : 0;
            int v = (v0<<16) | (v1<<8) | v2;

            int a = (v>>18)&63;
            int b = (v>>12)&63;
            int c = (v>>6)&63;
            int d = v&63;

            sb.append(encodeLut[a]);
            sb.append(encodeLut[b]);
            sb.append(encodeLut[c]);
            sb.append(encodeLut[d]);

            if (idx+3 >= length || sb.length()==72) {
                if (idx+2 >= length)
                    sb.setCharAt(sb.length()-1, '=');
                if (idx+1 >= length)
                    sb.setCharAt(sb.length()-2, '=');

                lines.add(sb.toString());
                sb = new StringBuilder();
            }
        }

        return lines.toArray(new String[lines.size()]);
    }

    public static byte[] decode(String lines[])
    {
        ByteArrayOutputStream outs = new ByteArrayOutputStream(54 * lines.length);

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx];

            for (int idx = 0; idx < line.length(); idx+=4) {
                int a = decodeLut[(int) line.charAt(idx)];
                int b = decodeLut[(int) line.charAt(idx+1)];
                int c = decodeLut[(int) line.charAt(idx+2)];
                int d = decodeLut[(int) line.charAt(idx+3)];

                int v = (a<<18) | (b<<12) | (c<<6) | d;

                int v0 = (v>>16)&0xff;
                int v1 = (v>>8)&0xff;
                int v2 = v&0xff;

                outs.write(v0);
                if (line.charAt(idx+2)!='=')
                    outs.write(v1);
                if (line.charAt(idx+3)!='=')
                    outs.write(v2);
            }
        }

        return outs.toByteArray();
    }

    /**
     * Provides a user-space mechanism for encoding and decoding data in base-64.
     *
     * Usage: Base64 [--encode* | --decode] [--file=<input>] [--help]
     */
    public static void main(String args[])
    {
        final int BINARY_BUFFER_LEN = 54;  // size of raw binary input/output buffer
        final int TEXT_BUFFER_LEN = 73;    // size of encoded text input/output buffer

        GetOpt opts  = new GetOpt();

        opts.addBoolean('h', "help", false, "Print usage information and exit");
        opts.addBoolean('e', "encode", true, "Encode binary input into base-64 text (default)");
        opts.addBoolean('d', "decode", false, "Decode base-64 text input into original binary");
        opts.addString( 'f', "file", "", "Read input from a file with the given filename");

        if (!opts.parse(args)) {
            System.err.println("Invalid argument: " + opts.getReason());
            System.exit(1);
        }

        if (opts.getBoolean("help")) {
            System.out.println("Usage:");
            opts.doHelp();
            System.exit(0);
        }

        if (opts.wasSpecified("encode") && opts.wasSpecified("decode")) {
            System.err.println("Only one of --encode and --decode may be specified");
            System.exit(1);
        }


        if (opts.getBoolean("decode")) {
            // decode base-64 text input
            try {
                BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                char[] data = new char[TEXT_BUFFER_LEN];
                ArrayList<String> lineArray = new ArrayList<String>();
                int count;

                if (opts.wasSpecified("file")) {
                    // open input file
                    input = new BufferedReader(new FileReader(opts.getString("file")));
                }

                while (true) {
                    // read input data
                    count = input.read(data, 0, TEXT_BUFFER_LEN);
                    if (count < 0)
                        break;
                    // trim trailing end-of-line
                    if (data[count-1] == '\n')
                        count--;
                    lineArray.add(new String(data, 0, count));
                }

                // decode and output
                byte[] output = Base64.decode(lineArray.toArray(new String[0]));
                System.out.write(output, 0, output.length);

                input.close();
            } catch (IOException ioe) {
                System.err.println(ioe);
                System.exit(2);
            }
        } else {
            // encode binary input
            try {
                BufferedInputStream input = new BufferedInputStream(System.in);
                byte[] data = new byte[BINARY_BUFFER_LEN];
                int count;

                if (opts.wasSpecified("file")) {
                    // open input file
                    input = new BufferedInputStream(new FileInputStream(opts.getString("file")));
                }

                while (true) {
                    // read input data
                    count = input.read(data, 0, BINARY_BUFFER_LEN);
                    if (count < 0)
                        break;

                    // encode and output data
                    String[] encoded = Base64.encode(data, count);
                    for (String line : encoded) {
                        System.out.println(line);
                    }
                }

                input.close();
            } catch (IOException ioe) {
                System.err.println(ioe);
                System.exit(2);
            }
        }
    }
}
