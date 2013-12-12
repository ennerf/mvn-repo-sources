package april.tag;

import java.io.*;

public class TagToC
{
    public static void main(String args[]) throws IOException
    {
        String cls = args[0];

        TagFamily tf = (TagFamily) april.util.ReflectUtil.createObject(cls);
        if (tf == null)
            return;

        String indent = "   ";

        String cname = String.format("tag%dh%d.c", tf.bits, tf.minimumHammingDistance);
        if (true) {
            BufferedWriter outs = new BufferedWriter(new FileWriter(cname));

            outs.write(String.format("#include <stdlib.h>\n"));
            outs.write(String.format("#include \"apriltag.h\"\n\n"));
            outs.write(String.format("april_tag_family_t *tag%dh%d_create()\n", tf.bits, tf.minimumHammingDistance));
            outs.write(String.format("{\n"));
            outs.write(String.format("%sapril_tag_family_t *tf = calloc(1, sizeof(april_tag_family_t));\n", indent));
            outs.write(String.format("%stf->black_border = 1;\n", indent));
            outs.write(String.format("%stf->d = %d;\n", indent, tf.d));
            outs.write(String.format("%stf->h = %d;\n", indent, tf.minimumHammingDistance));
            outs.write(String.format("%stf->ncodes = %d;\n", indent, tf.codes.length));
            outs.write(String.format("%stf->codes = calloc(%d, sizeof(uint64_t));\n", indent, tf.codes.length));
            for (int i = 0; i < tf.codes.length; i++) {
                outs.write(String.format("%stf->codes[%d] = 0x%016xUL;\n", indent, i, tf.codes[i]));
            }
            outs.write(String.format("%sreturn tf;\n", indent));
            outs.write(String.format("}\n"));
            outs.write(String.format("\n"));
            outs.write(String.format("void tag%dh%d_destroy(april_tag_family_t *tf)\n", tf.bits, tf.minimumHammingDistance));
            outs.write(String.format("{\n"));
            outs.write(String.format("%sfree(tf->codes);\n", indent));
            outs.write(String.format("%sfree(tf);\n", indent));
            outs.write(String.format("}\n"));
            outs.flush();
            outs.close();
        }

        if (true) {
            String hname = String.format("tag%dh%d.h", tf.bits, tf.minimumHammingDistance);

            BufferedWriter outs = new BufferedWriter(new FileWriter(hname));
            outs.write(String.format("#ifndef _TAG%dH%d\n", tf.bits, tf.minimumHammingDistance));
            outs.write(String.format("#define _TAG%dH%d\n\n", tf.bits, tf.minimumHammingDistance));
            outs.write(String.format("april_tag_family_t *tag%dh%d_create();\n", tf.bits, tf.minimumHammingDistance));
            outs.write(String.format("void tag%dh%d_destroy(april_tag_family_t *tf);\n", tf.bits, tf.minimumHammingDistance));
            outs.write(String.format("#endif"));
            outs.flush();
            outs.close();
        }
    }
}
